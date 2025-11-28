package com.bitchat.android.merchant

import android.content.Context
import android.util.Log
import com.bitchat.android.net.OkHttpProvider
import com.bitchat.android.printer.PrinterManager
import com.bitchat.android.printer.PrinterSettingsManager
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

    // Endpoint can be adjusted as per WEBSOCKET.md; default to go.realm.chat
    private const val DEFAULT_WS_URL = "wss://go.realm.chat/api/v1/ws"

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

    fun start(context: Context) {
        if (isStarted) return
        val appContext = context.applicationContext
        val auth = MerchantAuthManager.getInstance(appContext)
        val user = auth.getCurrentUser() ?: run {
            Log.w(TAG, "start: no current user; skipping websocket start")
            return
        }
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
            val url = "$DEFAULT_WS_URL?user_id=${currentUser.id}"
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
            // Parse message; expect JSON with fields including event and user/order identification
            val obj: JsonObject = try {
                JsonParser.parseString(text).asJsonObject
            } catch (t: Throwable) {
                Log.w(TAG, "Ignoring non-JSON WebSocket message: ${t.message}")
                return
            }

            val event = obj.get("event")?.asString ?: obj.get("type")?.asString
            if (event != null && event.lowercase() != "paid") {
                // Not a paid event; ignore
                return
            }
            paidEventsReceived.incrementAndGet()

            val auth = MerchantAuthManager.getInstance(context)
            val currentUserId = auth.getCurrentUser()?.id
            if (currentUserId == null) {
                Log.w(TAG, "onMessage: no current user; ignoring event")
                return
            }

            // Verify ownership: compare user_id in payload to current user
            val payloadUserId = obj.get("user_id")?.let { el ->
                try { el.asInt } catch (_: Throwable) {
                    try { el.asString.toInt() } catch (_: Throwable) { null }
                }
            }
            if (payloadUserId != null && payloadUserId != currentUserId) {
                Log.d(TAG, "Event user_id=$payloadUserId does not match current=$currentUserId; skipping")
                return
            }

            // Identify order ID field variants
            val orderId = obj.get("order_id")?.asString
                ?: obj.get("id")?.asString
                ?: obj.get("orderId")?.asString
            if (orderId.isNullOrBlank()) {
                Log.w(TAG, "Paid event missing order_id; skipping")
                return
            }

            // De-duplication window: skip if recently processed
            val now = System.currentTimeMillis()
            val last = recentPaidEvents[orderId]
            if (last != null && (now - last) < DEDUP_WINDOW_MS) {
                paidEventsDeduped.incrementAndGet()
                Log.v(TAG, "Skipping duplicate paid event for order $orderId within ${DEDUP_WINDOW_MS}ms window")
                return
            }
            recentPaidEvents[orderId] = now
            // Light pruning to bound map size
            if (recentPaidEvents.size > 1000) {
                val cutoff = now - (10 * 60_000L)
                recentPaidEvents.entries.removeIf { it.value < cutoff }
            }

            // Handle printing asynchronously
            scope.launch {
                try {
                    val authHeader = auth.getAuthorizationHeader()
                    // Refresh orders to ensure the DB has latest items/status for this user
                    OrdersStoreHelper.fetchAndStore(context, currentUserId, authHeader)

                    // Prepare minimal OrderDto for formatting/updates
                    val orderDto = OrdersSyncWorker.OrderDto(
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
                        status = null,
                        updatedAtStatus = null,
                        products = null
                    )

                    val printers = PrinterSettingsManager(context).getPrinters()
                    if (printers.isEmpty()) {
                        Log.d(TAG, "No saved printers; skipping print for order $orderId")
                        return@launch
                    }

                    val db = com.bitchat.android.db.AppDatabaseHelper(context)
                    printers.forEach { printer ->
                        try {
                            printAttempts.incrementAndGet()
                            val content = PrinterManager.formatOrderForPrinter(context, orderDto, printer)
                            if (content.isNotBlank()) {
                                val ok = PrinterManager.printOrder(context, printer, content, orderDto)
                                Log.d(TAG, "Printed order $orderId to ${printer.host}:${printer.port} success=$ok")
                                if (ok) printSuccesses.incrementAndGet() else printFailures.incrementAndGet()
                                // Log websocket-driven print attempt
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
                            } else {
                                Log.v(TAG, "Printer ${printer.id} had no matching items for order $orderId")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Print pipeline error for printer ${printer.id}", e)
                            printFailures.incrementAndGet()
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
                } catch (e: Exception) {
                    Log.e(TAG, "Failed handling paid event for order $orderId", e)
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