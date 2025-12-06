package com.bitchat.android.merchant

import android.content.Context
import android.bluetooth.BluetoothAdapter
import android.util.Log
import com.bitchat.android.net.OkHttpProvider
import com.bitchat.android.printer.PrinterManager
import com.bitchat.android.printer.PrinterSettingsManager
import com.bitchat.android.identity.SecureIdentityStateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import com.google.gson.JsonParser
import com.google.gson.JsonObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min

/**
 * Merchant WebSocket listener that reacts to order status events.
 * - Listens for "paid" events
 * - Verifies event belongs to current merchant user_id
 * - Loads latest orders for the user and prints per-printer category selection
 */
object MerchantWebSocketManager {
    private const val TAG = "MerchantWebSocketManager"

    private const val DEFAULT_WS_URL = "wss://komers-realtime-hub.bekreatif2020-4d4.workers.dev/ws"
    private const val KEY_AUTO_PRINT_EVENTS = "merchant_auto_print_events"
    private val DEFAULT_AUTO_PRINT_EVENTS = setOf("paid", "order.paid")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var webSocket: WebSocket? = null
    @Volatile private var connected: Boolean = false
    private var isStarted = false
    private var shouldRun = false
    private var reconnectAttempts = 0
    private const val BASE_BACKOFF_MS = 1000L
    private const val MAX_BACKOFF_MS = 60_000L

    // Metrics
    private val messagesReceived = AtomicInteger(0)
    private val paidEventsReceived = AtomicInteger(0)
    private val paidEventsDeduped = AtomicInteger(0)
    private val printAttempts = AtomicInteger(0)
    private val printSuccesses = AtomicInteger(0)
    private val printFailures = AtomicInteger(0)
    private val reconnectsScheduled = AtomicInteger(0)
    private val reconnectsCompleted = AtomicInteger(0)

    // Small de-duplication window for order_id to avoid repeated prints
    private const val DEDUP_WINDOW_MS = 15_000L
    private val recentPaidEvents = ConcurrentHashMap<String, Long>()
    @Volatile private var autoPrintEvents: Set<String> = DEFAULT_AUTO_PRINT_EVENTS

    private fun normalizeEvents(events: Set<String>): Set<String> = events.mapNotNull { it.trim().lowercase().takeIf { s -> s.isNotBlank() } }.toSet()

    private fun getDeviceId(context: Context): String? {
        val bt = try { BluetoothAdapter.getDefaultAdapter()?.address } catch (_: Throwable) { null }
        val validBt = if (bt.isNullOrBlank() || bt == "02:00:00:00:00:00") null else bt
        if (validBt != null) return validBt
        val aid = try { android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID) } catch (_: Throwable) { null }
        return aid
    }

    fun setAutoPrintEvents(context: Context, events: Set<String>) {
        val normalized = normalizeEvents(events)
        autoPrintEvents = if (normalized.isEmpty()) DEFAULT_AUTO_PRINT_EVENTS else normalized
        try {
            SecureIdentityStateManager(context).storeSecureValue(KEY_AUTO_PRINT_EVENTS, autoPrintEvents.joinToString(","))
        } catch (_: Exception) {}
    }

    fun getAutoPrintEvents(context: Context): Set<String> {
        try {
            val stored = SecureIdentityStateManager(context).getSecureValue(KEY_AUTO_PRINT_EVENTS)
            if (!stored.isNullOrBlank()) {
                autoPrintEvents = normalizeEvents(stored.split(",").toSet())
            }
        } catch (_: Exception) {}
        return autoPrintEvents
    }

    private fun shouldAutoPrint(eventLower: String?, statusLower: String?): Boolean {
        val selected = autoPrintEvents
        if (selected.contains("*")) return true
        if (!statusLower.isNullOrBlank() && selected.contains(statusLower)) return true
        val ev = eventLower ?: return false
        val norm = ev.replace('_', '.')
        if (selected.contains(ev) || selected.contains(norm)) return true
        if ((norm.startsWith("order.") || ev.startsWith("order_")) && selected.contains("order.*")) return true
        return false
    }

    fun start(context: Context) {
        if (isStarted) return
        val appContext = context.applicationContext
        val auth = MerchantAuthManager.getInstance(appContext)
        val user = auth.getCurrentUser() ?: run {
            Log.w(TAG, "start: no current user; skipping websocket start")
            return
        }
        getAutoPrintEvents(appContext)
        shouldRun = true
        connect(appContext)
        isStarted = true
    }

    fun stop() {
        try {
            webSocket?.close(1000, "logout")
        } catch (_: Throwable) {}
        webSocket = null
        connected = false
        isStarted = false
        shouldRun = false
        reconnectAttempts = 0
        Log.d(TAG, "WebSocket stopped")
    }

    private fun connect(context: Context) {
        val auth = MerchantAuthManager.getInstance(context)
        val currentUser = auth.getCurrentUser() ?: run {
            Log.w(TAG, "connect: no current user; aborting")
            return
        }
        try {
            val sb = StringBuilder().append(DEFAULT_WS_URL).append("?merchant_id=").append(currentUser.id)
            getDeviceId(context)?.let { did -> sb.append("&device=").append(java.net.URLEncoder.encode(did, "UTF-8")) }
            val url = sb.toString()
            val builder = Request.Builder().url(url)
            // Include bearer when available
            auth.getAuthorizationHeader()?.let { header ->
                if (header.isNotBlank()) builder.header("Authorization", header)
            }
            val request = builder.build()
            val client = OkHttpProvider.webSocketClient()
            webSocket = client.newWebSocket(request, PaidEventsListener(context))
            reconnectAttempts = 0
            Log.d(TAG, "WebSocket connecting: $url")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to create Merchant WebSocket", t)
        }
    }

    private fun scheduleReconnect(context: Context) {
        if (!shouldRun) return
        val delayMs = min(BASE_BACKOFF_MS * (1L shl reconnectAttempts.coerceAtMost(10)), MAX_BACKOFF_MS)
        reconnectAttempts += 1
        reconnectsScheduled.incrementAndGet()
        Log.w(TAG, "Scheduling reconnect in ${delayMs}ms (attempt=$reconnectAttempts)")
        scope.launch {
            try {
                delay(delayMs)
                if (shouldRun) {
                    connect(context)
                    reconnectsCompleted.incrementAndGet()
                }
            } catch (_: Throwable) {}
        }
    }

    private class PaidEventsListener(private val context: Context) : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "✅ Merchant WebSocket connected")
            connected = true
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            messagesReceived.incrementAndGet()
            val obj: JsonObject = try {
                JsonParser.parseString(text).asJsonObject
            } catch (t: Throwable) {
                Log.w(TAG, "Ignoring non-JSON WebSocket message: ${t.message}")
                return
            }
            val eventType = obj.get("type")?.asString ?: obj.get("event")?.asString
            val eventLower = eventType?.lowercase()

            val auth = MerchantAuthManager.getInstance(context)
            val currentUserId = auth.getCurrentUser()?.id ?: run {
                Log.w(TAG, "onMessage: no current user; ignoring event")
                return
            }

            val payloadMerchant = obj.get("merchant_id")?.asString
            val dataObj = obj.get("data")?.let { if (it.isJsonObject) it.asJsonObject else null }
            val userFromData = dataObj?.get("user_id")?.let { el ->
                try { el.asString } catch (_: Throwable) { try { el.asInt.toString() } catch (_: Throwable) { null } }
            }
            val currentUserStr = currentUserId.toString()
            if (!payloadMerchant.isNullOrBlank() && payloadMerchant != currentUserStr) return
            if (!userFromData.isNullOrBlank() && userFromData != currentUserStr) return

            val orderId = when {
                dataObj?.get("order_id") != null -> try { dataObj.get("order_id").asString } catch (_: Throwable) { null }
                dataObj?.get("id") != null -> try { dataObj.get("id").asString } catch (_: Throwable) { null }
                obj.get("order_id") != null -> try { obj.get("order_id").asString } catch (_: Throwable) { null }
                obj.get("id") != null -> try { obj.get("id").asString } catch (_: Throwable) { null }
                else -> null
            }
            if (orderId.isNullOrBlank()) return

            val statusLower = dataObj?.get("status")?.let { el ->
                try { el.asString.lowercase() } catch (_: Throwable) { null }
            }
            val shouldPrint = shouldAutoPrint(eventLower, statusLower)

            val now = System.currentTimeMillis()
            val last = recentPaidEvents[orderId]
            if (last != null && (now - last) < DEDUP_WINDOW_MS && shouldPrint) {
                paidEventsDeduped.incrementAndGet()
                return
            }
            if (shouldPrint) recentPaidEvents[orderId] = now
            if (recentPaidEvents.size > 1000) {
                val cutoff = now - (10 * 60_000L)
                recentPaidEvents.entries.removeIf { it.value < cutoff }
            }

            scope.launch {
                try {
                    val db = com.bitchat.android.db.AppDatabaseHelper(context)
                    try {
                        db.insertPrintLog(
                            com.bitchat.android.db.PrintLog(
                                printerId = null,
                                host = "ws",
                                port = 0,
                                label = "event=" + (eventLower ?: "") + "|status=" + (statusLower ?: "") + "|order=" + orderId,
                                type = "ws_receive",
                                success = true
                            )
                        )
                    } catch (_: Exception) { }
                    var dto: OrdersSyncWorker.OrderDto? = null
                    if (dataObj != null) {
                        dto = OrdersSyncWorker.OrderDto(
                            id = dataObj.get("id")?.let { try { it.asString } catch (_: Throwable) { null } },
                            orderId = orderId,
                            globalNote = dataObj.get("global_note")?.let { try { it.asString } catch (_: Throwable) { null } },
                            customerName = dataObj.get("customer_name")?.let { try { it.asString } catch (_: Throwable) { null } },
                            customerPhone = dataObj.get("customer_phone")?.let { try { it.asString } catch (_: Throwable) { null } },
                            tableNumber = dataObj.get("table_number")?.let { try { it.asString } catch (_: Throwable) { null } },
                            createdAt = dataObj.get("created_at")?.let { try { it.asString } catch (_: Throwable) { null } },
                            deliveryMethod = dataObj.get("delivery_method")?.let { try { it.asString } catch (_: Throwable) { null } },
                            deviceId = dataObj.get("device_id")?.let { try { it.asString } catch (_: Throwable) { null } },
                            userId = dataObj.get("user_id")?.let { try { it.asString } catch (_: Throwable) { null } },
                            status = dataObj.get("status")?.let { try { it.asString } catch (_: Throwable) { null } },
                            updatedAtStatus = dataObj.get("updated_at_status")?.let { try { it.asString } catch (_: Throwable) { null } },
                            products = dataObj.get("products")?.let { pel ->
                                try {
                                    if (!pel.isJsonArray) emptyList() else pel.asJsonArray.mapNotNull { pEl ->
                                        val pObj = try { pEl.asJsonObject } catch (_: Throwable) { null } ?: return@mapNotNull null
                                        OrdersSyncWorker.ProductDto(
                                            id = pObj.get("id")?.let { try { it.asString } catch (_: Throwable) { null } },
                                            name = pObj.get("name")?.let { try { it.asString } catch (_: Throwable) { null } } ?: return@mapNotNull null,
                                            price = pObj.get("price")?.let { 
                                                try { if (it.isJsonPrimitive && it.asJsonPrimitive.isNumber) it.asNumber.toString() else it.asString } catch (_: Throwable) { null }
                                            },
                                            quantity = pObj.get("quantity")?.let { 
                                                try { it.asInt } catch (_: Throwable) { try { it.asString.toInt() } catch (_: Throwable) { null } }
                                            },
                                            variant = pObj.get("variant")?.let { try { it.asString } catch (_: Throwable) { null } },
                                            categoryId = pObj.get("category_id")?.let { try { it.asString } catch (_: Throwable) { null } },
                                            note = pObj.get("note")?.let { try { it.asString } catch (_: Throwable) { null } },
                                            prepared = pObj.get("prepared")?.let { 
                                                try { it.asBoolean } catch (_: Throwable) { try { it.asInt != 0 } catch (_: Throwable) { null } }
                                            },
                                            printed = pObj.get("printed")?.let {
                                                try { it.asBoolean } catch (_: Throwable) { try { it.asInt != 0 } catch (_: Throwable) { null } }
                                            }
                                        )
                                    }
                                } catch (_: Throwable) { emptyList() }
                            }
                        )
                        db.upsertOrder(
                            orderId = dto.orderId,
                            id = dto.id,
                            createdAt = dto.createdAt,
                            deliveryMethod = dto.deliveryMethod,
                            userId = dto.userId,
                            status = dto.status,
                            customerName = dto.customerName,
                            customerPhone = dto.customerPhone,
                            tableNumber = dto.tableNumber,
                            globalNote = dto.globalNote,
                            deviceId = dto.deviceId,
                            updatedAtStatus = dto.updatedAtStatus,
                            payload = try { dataObj?.toString() } catch (_: Exception) { null }
                        )
                        val items = dto.products?.map { p ->
                            com.bitchat.android.db.AppDatabaseHelper.OrderItem(
                                itemId = p.id,
                                name = p.name,
                                quantity = (p.quantity ?: 0),
                                variant = p.variant,
                                categoryId = p.categoryId,
                                note = p.note,
                                prepared = (p.prepared ?: false),
                                printed = (p.printed ?: false)
                            )
                        }.orEmpty()
                        db.replaceOrderItems(dto.orderId, items)
                        try {
                            db.insertPrintLog(
                                com.bitchat.android.db.PrintLog(
                                    printerId = null,
                                    host = "ws",
                                    port = 0,
                                    label = "event_store",
                                    type = "ws_event_store",
                                    success = true
                                )
                            )
                        } catch (_: Exception) { }
                    } else {
                        val authHeader = auth.getAuthorizationHeader()
                        OrdersStoreHelper.fetchAndStore(context, currentUserId, authHeader)
                    }

                    if (eventLower == "order.status.updated" && !statusLower.isNullOrBlank()) {
                        db.updateOrderStatus(orderId, statusLower)
                    }
                    if (eventLower == "order.cancelled") {
                        db.updateOrderStatus(orderId, "cancelled")
                    }
                    if (eventLower == "item.prepared") {
                        val productId = dataObj?.get("product_id")?.let { el ->
                            try { el.asString } catch (_: Throwable) { null }
                        }
                        val preparedFlag = dataObj?.get("prepared")?.let { el ->
                            try { el.asBoolean } catch (_: Throwable) { try { el.asInt != 0 } catch (_: Throwable) { null } }
                        } ?: false
                        if (!productId.isNullOrBlank()) {
                            db.updateProductPrepared(orderId, productId, preparedFlag)
                            if (db.areAllItemsPrepared(orderId)) {
                                db.updateOrderStatus(orderId, "ready")
                            }
                        }
                    }

                    if (eventLower == "order.printed") {
                        try {
                            db.insertPrintLog(
                                com.bitchat.android.db.PrintLog(
                                    printerId = null,
                                    host = "ws",
                                    port = 0,
                                    label = "notify_printed|order=" + orderId,
                                    type = "ws_notify_printed",
                                    success = true
                                )
                            )
                        } catch (_: Exception) { }
                    }
                    if (eventLower == "order.ready") {
                        try {
                            db.insertPrintLog(
                                com.bitchat.android.db.PrintLog(
                                    printerId = null,
                                    host = "ws",
                                    port = 0,
                                    label = "notify_ready|order=" + orderId,
                                    type = "ws_notify_ready",
                                    success = true
                                )
                            )
                        } catch (_: Exception) { }
                    }

                    if (shouldPrint) {
                        val printers = PrinterSettingsManager(context).getPrinters().filter { p ->
                            p.autoPrintEnabled != false && p.role == "station"
                        }
                        if (printers.isEmpty()) {
                            try {
                                db.insertPrintLog(
                                    com.bitchat.android.db.PrintLog(
                                        printerId = null,
                                        host = "ws",
                                        port = 0,
                                        label = "auto_print_skipped=printers|reason=no_station_printers",
                                        type = "ws_print",
                                        success = false
                                    )
                                )
                            } catch (_: Exception) { }
                            return@launch
                        }
                        val orderDto = dto ?: OrdersSyncWorker.OrderDto(
                            id = null,
                            orderId = orderId,
                            globalNote = null,
                            customerName = null,
                            customerPhone = null,
                            tableNumber = null,
                            createdAt = null,
                            deliveryMethod = null,
                            deviceId = null,
                            userId = null,
                            status = statusLower,
                            updatedAtStatus = null,
                            products = null
                        )
                        printers.forEach { printer ->
                            try {
                                printAttempts.incrementAndGet()
                                val content = PrinterManager.formatOrderForPrinter(context, orderDto, printer)
                                if (content.isNotBlank()) {
                                    val ok = PrinterManager.printOrder(context, printer, content, orderDto)
                                    if (ok) printSuccesses.incrementAndGet() else printFailures.incrementAndGet()
                                    try {
                                        db.insertPrintLog(
                                            com.bitchat.android.db.PrintLog(
                                                printerId = printer.id,
                                                host = printer.host,
                                                port = printer.port,
                                                label = printer.label,
                                                type = "ws_print",
                                                success = ok
                                            )
                                        )
                                    } catch (_: Exception) { }
                                    Log.d(TAG, "WS printed order ${orderId} to ${printer.host}:${printer.port} success=${ok}")
                                } else {
                                    try {
                                        db.insertPrintLog(
                                            com.bitchat.android.db.PrintLog(
                                                printerId = printer.id,
                                                host = printer.host,
                                                port = printer.port,
                                                label = "no_matching_items",
                                                type = "ws_print",
                                                success = false
                                            )
                                        )
                                    } catch (_: Exception) { }
                                }
                            } catch (e: Exception) {
                                printFailures.incrementAndGet()
                                Log.e(TAG, "WS print pipeline error for printer ${printer.id}", e)
                                try {
                                    db.insertPrintLog(
                                        com.bitchat.android.db.PrintLog(
                                            printerId = printer.id,
                                            host = printer.host,
                                            port = printer.port,
                                            label = printer.label,
                                            type = "ws_print",
                                            success = false
                                        )
                                    )
                                } catch (_: Exception) { }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed handling event", e)
                }
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "Merchant WebSocket closing: $code $reason")
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "Merchant WebSocket closed: $code $reason")
            connected = false
            // Attempt reconnect with backoff
            scheduleReconnect(context)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "❌ Merchant WebSocket failure: ${t.message}", t)
            connected = false
            // Attempt reconnect with backoff
            scheduleReconnect(context)
        }
    }

    fun getMetrics(): Map<String, Int> {
        return mapOf(
            "messagesReceived" to messagesReceived.get(),
            "paidEventsReceived" to paidEventsReceived.get(),
            "paidEventsDeduped" to paidEventsDeduped.get(),
            "printAttempts" to printAttempts.get(),
            "printSuccesses" to printSuccesses.get(),
            "printFailures" to printFailures.get(),
            "reconnectsScheduled" to reconnectsScheduled.get(),
            "reconnectsCompleted" to reconnectsCompleted.get()
        )
    }

    fun isConnected(): Boolean = connected
}
