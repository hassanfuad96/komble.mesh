package com.bitchat.android.merchant

import android.content.Context
import android.util.Log
import com.bitchat.android.db.AppDatabaseHelper
import com.bitchat.android.printer.PrinterManager
import com.bitchat.android.printer.PrinterSettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken

/**
 * Lightweight poller that fetches orders every 5 seconds,
 * stores them into SQLite, prints only brand-new orders (dedupe by order_id),
 * and logs as poll_print.
 */
object MerchantOrdersPoller {
    private const val TAG = "MerchantOrdersPoller"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false
    private var job: kotlinx.coroutines.Job? = null

    private data class ApiResponse<T>(val success: Boolean, val message: String?, val data: T?)

    fun start(context: Context) {
        if (started) return
        started = true
        val appCtx = context.applicationContext
        val auth = MerchantAuthManager.getInstance(appCtx)
        job = scope.launch {
            val gson = Gson()
            while (isActive) {
                try {
                    val user = auth.getCurrentUser()
                    val authHeader = auth.getAuthorizationHeader()
                    if (user == null || !auth.hasValidToken()) {
                        // Log unauthenticated state occasionally
                        try {
                            AppDatabaseHelper(appCtx).insertPrintLog(
                                com.bitchat.android.db.PrintLog(
                                    printerId = null,
                                    host = "api",
                                    port = 0,
                                    label = "unauthenticated",
                                    type = "poll_fetch",
                                    success = false
                                )
                            )
                        } catch (_: Exception) { }
                        delay(5000)
                        continue
                    }

                    val raw = MerchantOrdersApi.fetchOrdersForUser(user.id, authHeader)
                    if (raw.isNullOrEmpty()) {
                        try {
                            AppDatabaseHelper(appCtx).insertPrintLog(
                                com.bitchat.android.db.PrintLog(
                                    printerId = null,
                                    host = "api",
                                    port = 0,
                                    label = "count=0",
                                    type = "poll_fetch",
                                    success = true
                                )
                            )
                        } catch (_: Exception) { }
                        delay(5000)
                        continue
                    }

                    // If API indicates an error (e.g., after logout or token mismatch), log clearly and skip parsing
                    try {
                        val rootEl = JsonParser.parseString(raw)
                        if (rootEl.isJsonObject) {
                            val obj = rootEl.asJsonObject
                            val successEl = obj.get("success")
                            val resultEl = obj.get("result")
                            val isError = try {
                                (successEl != null && !successEl.isJsonNull && successEl.asBoolean == false) ||
                                (resultEl != null && !resultEl.isJsonNull && resultEl.asBoolean == false)
                            } catch (_: Exception) { false }
                            if (isError) {
                                val msg = try {
                                    val m = obj.get("message")
                                    if (m != null && !m.isJsonNull) m.asString else null
                                } catch (_: Exception) { null }
                                try {
                                    AppDatabaseHelper(appCtx).insertPrintLog(
                                        com.bitchat.android.db.PrintLog(
                                            printerId = null,
                                            host = "api",
                                            port = 0,
                                            label = "auth_error:${msg ?: "unauthorized"}",
                                            type = "poll_fetch",
                                            success = false
                                        )
                                    )
                                } catch (_: Exception) { }
                                delay(5000)
                                continue
                            } else {
                                // Handle success=true but empty/missing data -> "no new order"
                                val msg = try { obj.get("message")?.asStringOrNull() } catch (_: Exception) { null }
                                val dataEl = obj.get("data")
                                val isEmpty = try {
                                    dataEl == null || dataEl.isJsonNull || (dataEl.isJsonArray && dataEl.asJsonArray.size() == 0)
                                } catch (_: Exception) { false }
                                if (isEmpty) {
                                    try {
                                        AppDatabaseHelper(appCtx).insertPrintLog(
                                            com.bitchat.android.db.PrintLog(
                                                printerId = null,
                                                host = "api",
                                                port = 0,
                                                label = "no_new_order:${msg ?: "Orders retrieved successfully"}",
                                                type = "poll_fetch",
                                                success = true
                                            )
                                        )
                                    } catch (_: Exception) { }
                                    delay(5000)
                                    continue
                                }
                            }
                        }
                    } catch (_: Exception) {
                        // If parsing the error envelope itself fails, fall back to the normal path below
                    }

                    // Prefer tolerant parser first to avoid strict type mismatches during account switches
                    val preOrders = try { tolerantParseOrders(raw) } catch (_: Exception) { emptyList() }
                    if (preOrders.isNotEmpty()) {
                        val db = AppDatabaseHelper(appCtx)
                        val printersAll = PrinterSettingsManager(appCtx).getPrinters()
                        val globalSelected = try { CategoriesSelectionStore(appCtx).getSelectedCategories() } catch (_: Exception) { emptyList<Int?>() }
                        val printers = printersAll.filter { p ->
                            val sel = p.selectedCategoryIds ?: emptyList()
                            val includeUncat = p.uncategorizedSelected == true
                            sel.contains(0) || sel.isNotEmpty() || includeUncat || globalSelected.isNotEmpty()
                        }
                        if (printers.isEmpty()) {
                            preOrders.forEach { o -> upsertOrder(db, o) }
                            try {
                                AppDatabaseHelper(appCtx).insertPrintLog(
                                    com.bitchat.android.db.PrintLog(
                                        printerId = null,
                                        host = "api",
                                        port = 0,
                                        label = "auto_print_skipped=categories",
                                        type = "poll_fetch",
                                        success = true
                                    )
                                )
                            } catch (_: Exception) { }
                            delay(5000)
                            continue
                        }
                        preOrders.forEach { o ->
                            val existed = db.orderExists(o.orderId)
                            upsertOrder(db, o)
                            if (!existed) {
                                printers.forEach { printer ->
                                    try {
                                        val content = PrinterManager.formatOrderForPrinter(appCtx, o, printer)
                                        if (content.isNotBlank()) {
                                            val ok = PrinterManager.printOrder(appCtx, printer, content, o)
                                            try {
                                                db.insertPrintLog(
                                                    com.bitchat.android.db.PrintLog(
                                                        printerId = printer.id,
                                                        host = printer.host,
                                                        port = printer.port,
                                                        label = printer.label,
                                                        type = "poll_print",
                                                        success = ok
                                                    )
                                                )
                                            } catch (_: Exception) { }
                                        } else {
                                            try {
                                                db.insertPrintLog(
                                                    com.bitchat.android.db.PrintLog(
                                                        printerId = printer.id,
                                                        host = printer.host,
                                                        port = printer.port,
                                                        label = "no_matching_items",
                                                        type = "poll_print",
                                                        success = false
                                                    )
                                                )
                                            } catch (_: Exception) { }
                                        }
                                    } catch (_: Exception) { }
                                }
                            }
                        }
                        try {
                            AppDatabaseHelper(appCtx).insertPrintLog(
                                com.bitchat.android.db.PrintLog(
                                    printerId = null,
                                    host = "api",
                                    port = 0,
                                    label = "fallback_count=${preOrders.size}",
                                    type = "poll_fetch",
                                    success = true
                                )
                            )
                        } catch (_: Exception) { }
                        delay(5000)
                        continue
                    }

                    val type = object : TypeToken<ApiResponse<Array<OrdersSyncWorker.OrderDto>>>() {}.type
                    val parsed: ApiResponse<Array<OrdersSyncWorker.OrderDto>>? = try {
                        gson.fromJson(raw, type)
                    } catch (e: Exception) {
                        Log.e(TAG, "Parse failed", e)
                        // Classify response for clearer diagnostics
                        val label = try {
                            val trimmed = raw.trim()
                            // Text patterns indicating auth issues
                            val t = trimmed.lowercase()
                            if (t.contains("unauthorized") || t.contains("forbidden") || t.contains("invalid token") || t.contains("not logged in")) {
                                "auth_error:text"
                            } else if (trimmed.startsWith("<")) {
                                "unexpected_html"
                            } else {
                                // Try to parse minimal JSON for error/message/code indicators
                                val el = JsonParser.parseString(trimmed)
                                if (el.isJsonObject) {
                                    val obj = el.asJsonObject
                                    val msg = obj.get("message")?.asStringOrNull()
                                    val err = obj.get("error")?.asStringOrNull()
                                    val code = obj.get("code")?.asIntOrNull()
                                    when {
                                        (code == 401 || code == 403) -> "auth_error:${msg ?: err ?: code}"
                                        !msg.isNullOrBlank() -> "unrecognized:${msg}"
                                        !err.isNullOrBlank() -> "unrecognized:${err}"
                                        else -> {
                                            val preview = trimmed.replace("\n"," ").take(160)
                                            "unrecognized_response:${preview}"
                                        }
                                    }
                                } else {
                                    val preview = trimmed.replace("\n"," ").take(160)
                                    "unrecognized_response:${preview}"
                                }
                            }
                        } catch (_: Exception) {
                            "unrecognized_response"
                        }
                        try {
                            AppDatabaseHelper(appCtx).insertPrintLog(
                                com.bitchat.android.db.PrintLog(
                                    printerId = null,
                                    host = "api",
                                    port = 0,
                                    label = label,
                                    type = "poll_fetch",
                                    success = label.startsWith("auth_error").not()
                                )
                            )
                        } catch (_: Exception) { }
                        delay(5000)
                        continue
                    }

                    val orders = parsed?.data?.toList().orEmpty()
                    try {
                        AppDatabaseHelper(appCtx).insertPrintLog(
                            com.bitchat.android.db.PrintLog(
                                printerId = null,
                                host = "api",
                                port = 0,
                                label = "count=${orders.size}",
                                type = "poll_fetch",
                                success = true
                            )
                        )
                    } catch (_: Exception) { }
                    if (orders.isEmpty()) {
                        delay(5000)
                        continue
                    }

                    val db = AppDatabaseHelper(appCtx)
                    val printersAll = PrinterSettingsManager(appCtx).getPrinters()
                    // Treat printers as eligible if they have printer-specific categories OR
                    // the global selection store has categories configured (fallback path).
                    val globalSelected = try { CategoriesSelectionStore(appCtx).getSelectedCategories() } catch (_: Exception) { emptyList<Int?>() }
                    val printers = printersAll.filter { p ->
                        val sel = p.selectedCategoryIds ?: emptyList()
                        val includeUncat = p.uncategorizedSelected == true
                        sel.contains(0) || sel.isNotEmpty() || includeUncat || globalSelected.isNotEmpty()
                    }
                    if (printers.isEmpty()) {
                        // Still upsert so UI has latest
                        orders.forEach { o ->
                            upsertOrder(db, o)
                        }
                        // Signal that auto print was skipped due to no eligible printers
                        try {
                            AppDatabaseHelper(appCtx).insertPrintLog(
                                com.bitchat.android.db.PrintLog(
                                    printerId = null,
                                    host = "api",
                                    port = 0,
                                    label = "auto_print_skipped=categories",
                                    type = "poll_fetch",
                                    success = true
                                )
                            )
                        } catch (_: Exception) { }
                        // Log stored count for verification
                        try {
                            AppDatabaseHelper(appCtx).insertPrintLog(
                                com.bitchat.android.db.PrintLog(
                                    printerId = null,
                                    host = "api",
                                    port = 0,
                                    label = "stored=${orders.size}",
                                    type = "poll_fetch",
                                    success = true
                                )
                            )
                        } catch (_: Exception) { }
                        delay(5000)
                        continue
                    }

                    orders.forEach { o ->
                        val existed = db.orderExists(o.orderId)
                        // Upsert header and items
                        upsertOrder(db, o)

                        if (!existed) {
                            // Print to all saved printers using category-aware formatting
                            printers.forEach { printer ->
                                try {
                                    val content = PrinterManager.formatOrderForPrinter(appCtx, o, printer)
                                    if (content.isNotBlank()) {
                                        val ok = PrinterManager.printOrder(appCtx, printer, content, o)
                                        try {
                                            db.insertPrintLog(
                                                com.bitchat.android.db.PrintLog(
                                                    printerId = printer.id,
                                                    host = printer.host,
                                                    port = printer.port,
                                                    label = printer.label,
                                                    type = "poll_print",
                                                    success = ok
                                                )
                                            )
                                        } catch (_: Exception) { }
                                        
                                    } else {
                                        try {
                                            db.insertPrintLog(
                                                com.bitchat.android.db.PrintLog(
                                                    printerId = printer.id,
                                                    host = printer.host,
                                                    port = printer.port,
                                                    label = "no_matching_items",
                                                    type = "poll_print",
                                                    success = false
                                                )
                                            )
                                        } catch (_: Exception) { }
                                    }
                                } catch (e: Exception) {
                                    
                                    try {
                                        db.insertPrintLog(
                                            com.bitchat.android.db.PrintLog(
                                                printerId = printer.id,
                                                host = printer.host,
                                                port = printer.port,
                                                label = printer.label,
                                                type = "poll_print",
                                                success = false
                                            )
                                        )
                                    } catch (_: Exception) { }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Polling loop error", e)
                }
                delay(5000)
            }
        }
    }

    // Tolerant manual parse for orders when strict Gson model fails
    private fun tolerantParseOrders(raw: String): List<OrdersSyncWorker.OrderDto> {
        fun parseOrderObject(o: JsonObject): OrdersSyncWorker.OrderDto? {
            return OrdersSyncWorker.OrderDto(
                id = o.get("id")?.asStringOrNull(),
                orderId = o.get("order_id")?.asStringOrNull() ?: return null,
                globalNote = o.get("global_note")?.asStringOrNull(),
                customerName = o.get("customer_name")?.asStringOrNull(),
                customerPhone = o.get("customer_phone")?.asStringOrNull(),
                tableNumber = o.get("table_number")?.asStringOrNull(),
                createdAt = o.get("created_at")?.asStringOrNull(),
                deliveryMethod = o.get("delivery_method")?.asStringOrNull(),
                deviceId = o.get("device_id")?.asStringOrNull(),
                userId = o.get("user_id")?.asStringOrNull(),
                status = o.get("status")?.asStringOrNull(),
                updatedAtStatus = o.get("updated_at_status")?.asStringOrNull(),
                products = o.get("products")?.let { productsEl ->
                    if (!productsEl.isJsonArray) emptyList() else productsEl.asJsonArray.mapNotNull { pEl ->
                        val pObj = pEl.asJsonObject
                        OrdersSyncWorker.ProductDto(
                            id = pObj.get("id")?.asStringOrNull(),
                            name = pObj.get("name")?.asStringOrNull() ?: return@mapNotNull null,
                            price = pObj.get("price")?.asStringOrNull(),
                            quantity = pObj.get("quantity")?.asIntOrNull(),
                            variant = pObj.get("variant")?.asStringOrNull(),
                            categoryId = pObj.get("category_id")?.asStringOrNull(),
                            note = pObj.get("note")?.asStringOrNull(),
                            prepared = pObj.get("prepared")?.asBoolOrNull()
                        )
                    }
                }
            )
        }
        return try {
            val root = JsonParser.parseString(raw)
            // Some APIs may return the array directly
            if (root.isJsonArray) {
                return root.asJsonArray.mapNotNull { el ->
                    if (!el.isJsonObject) null else parseOrderObject(el.asJsonObject)
                }
            }
            if (!root.isJsonObject) return emptyList()
            val obj = root.asJsonObject
            // Support various nested keys: data, orders, results, list, items
            var dataEl: JsonElement? = obj.get("data")
            if (dataEl == null || dataEl.isJsonNull) dataEl = obj.get("orders")
            if (dataEl == null || dataEl.isJsonNull) dataEl = obj.get("results")
            if (dataEl == null || dataEl.isJsonNull) dataEl = obj.get("list")
            if (dataEl == null || dataEl.isJsonNull) dataEl = obj.get("items")
            if (dataEl == null || dataEl.isJsonNull) return emptyList()
            if (dataEl.isJsonArray) {
                return dataEl.asJsonArray.mapNotNull { orderEl ->
                    if (!orderEl.isJsonObject) null else parseOrderObject(orderEl.asJsonObject)
                }
            }
            if (dataEl.isJsonObject) {
                val dataObj = dataEl.asJsonObject
                // Try nested array inside data object
                val arr = dataObj.get("orders") ?: dataObj.get("results") ?: dataObj.get("list") ?: dataObj.get("items") ?: dataObj.get("data")
                if (arr != null && arr.isJsonArray) {
                    return arr.asJsonArray.mapNotNull { orderEl ->
                        if (!orderEl.isJsonObject) null else parseOrderObject(orderEl.asJsonObject)
                    }
                }
                // Or single order object
                return listOfNotNull(parseOrderObject(dataObj))
            }
            emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun JsonElement.asStringOrNull(): String? = try {
        when {
            this.isJsonNull -> null
            this.isJsonPrimitive -> this.asJsonPrimitive.toStringValueOrNull()
            else -> this.toString()
        }
    } catch (_: Exception) { null }

    private fun JsonElement.asIntOrNull(): Int? = try {
        if (this.isJsonNull) null else if (this.isJsonPrimitive) {
            val prim = this.asJsonPrimitive
            when {
                prim.isNumber -> prim.asInt
                prim.isString -> prim.asString.toIntOrNull()
                prim.isBoolean -> if (prim.asBoolean) 1 else 0
                else -> null
            }
        } else null
    } catch (_: Exception) { null }

    private fun JsonElement.asBoolOrNull(): Boolean? = try {
        if (this.isJsonNull) null else if (this.isJsonPrimitive) {
            val prim = this.asJsonPrimitive
            when {
                prim.isBoolean -> prim.asBoolean
                prim.isNumber -> prim.asInt != 0
                prim.isString -> prim.asString.lowercase() in setOf("true","1")
                else -> null
            }
        } else null
    } catch (_: Exception) { null }

    private fun com.google.gson.JsonPrimitive.toStringValueOrNull(): String? = try {
        when {
            this.isString -> this.asString
            this.isNumber -> this.asNumber.toString()
            this.isBoolean -> this.asBoolean.toString()
            else -> null
        }
    } catch (_: Exception) { null }

    fun stop() {
        started = false
        try { job?.cancel() } catch (_: Exception) {}
        job = null
    }

    private fun upsertOrder(db: AppDatabaseHelper, o: OrdersSyncWorker.OrderDto) {
        db.upsertOrder(
            orderId = o.orderId,
            id = o.id,
            createdAt = o.createdAt,
            deliveryMethod = o.deliveryMethod,
            userId = o.userId,
            status = o.status,
            customerName = o.customerName,
            customerPhone = o.customerPhone,
            tableNumber = o.tableNumber,
            globalNote = o.globalNote,
            deviceId = o.deviceId,
            updatedAtStatus = o.updatedAtStatus
        )
        val items = o.products?.map { p ->
            AppDatabaseHelper.OrderItem(
                itemId = p.id,
                name = p.name,
                quantity = (p.quantity ?: 0),
                variant = p.variant,
                categoryId = p.categoryId,
                note = p.note,
                prepared = (p.prepared ?: false)
            )
        }.orEmpty()
        db.replaceOrderItems(o.orderId, items)
    }
}
