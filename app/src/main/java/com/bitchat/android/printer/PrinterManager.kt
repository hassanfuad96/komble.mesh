package com.bitchat.android.printer

import android.content.Context
import android.util.Log
import com.bitchat.android.db.AppDatabaseHelper
import com.bitchat.android.db.PrintLog
import com.bitchat.android.db.AppDatabaseHelper.OrderItem
import com.bitchat.android.merchant.CategoriesApiService
import com.bitchat.android.merchant.CategoriesSelectionStore
import com.bitchat.android.merchant.MerchantAuthManager
import com.bitchat.android.merchant.OrdersSyncWorker
import com.bitchat.android.merchant.MerchantOrderStatusApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.bitchat.android.mesh.BluetoothPermissionManager
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections
import com.dantsu.escposprinter.EscPosPrinter
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Orchestrates category-aware printing pipeline.
 * Mirrors PRINT_BY_CATEGORIES.md behavior: selection, filtering, formatting, and post-print updates.
 */
object PrinterManager {
    private const val TAG = "PrinterManager"

    // Append a line only when it has non-blank content; trims trailing spaces
    private fun appendLineIfNotBlank(sb: StringBuilder, value: String?) {
        val v = value?.trim()
        if (v.isNullOrEmpty()) return
        val l = v.lowercase()
        if (l == "null" || l == "none") return
        sb.append(v).append("\n")
    }

    suspend fun loadPrinters(context: Context): List<SavedPrinter> = withContext(Dispatchers.IO) {
        PrinterSettingsManager(context).getPrinters()
    }

    fun getDefaultPrinter(context: Context): SavedPrinter? = PrinterSettingsManager(context).getDefaultPrinter()

    /**
     * Format order content for printing using a specific printer's category selection.
     * Falls back to global selection store if printer has no selection.
     * Returns empty string if no items match selection.
     */
    suspend fun formatOrderForPrinter(context: Context, order: OrdersSyncWorker.OrderDto, printer: SavedPrinter): String {
        val auth = MerchantAuthManager.getInstance(context).getAuthorizationHeader()
        val categories = CategoriesApiService.fetchCategories(auth)
        val db = AppDatabaseHelper(context)
        // Load order items from DB for filtering/formatting
        val items: List<OrderItem> = db.getOrderItems(order.orderId)

        // Resolve selection: prefer printer-specific, else global
        val printerSelected = printer.selectedCategoryIds ?: emptyList()
        val includeAll = printerSelected.contains(0)
        val includeUncategorized = printer.uncategorizedSelected == true
        val filtered = if (includeAll) {
            items
        } else if (printerSelected.isEmpty() && !includeUncategorized) {
            // fall back to global store
            val globalFiltered = CategoriesSelectionStore(context).filterOrderItems(items)
            globalFiltered
        } else {
            val selectedInts = printerSelected.toSet()
            items.filter { item ->
                val idInt = item.categoryId?.toIntOrNull()
                (idInt != null && selectedInts.contains(idInt)) || (item.categoryId == null && includeUncategorized)
            }
        }
        if (filtered.isEmpty()) return ""

        val sb = StringBuilder()
        if (printer.role == "station") {
            val dt = formatDisplayDate(order.createdAt)
            appendLineIfNotBlank(sb, "Order #${order.orderId}")
            appendLineIfNotBlank(sb, "Note: ${order.globalNote ?: ""}")
            appendLineIfNotBlank(sb, "Table: ${order.tableNumber ?: "N/A"}")
            appendLineIfNotBlank(sb, "Delivery: ${order.deliveryMethod}")
            appendLineIfNotBlank(sb, dt?.let { "Printed at: $it" })
            val widthMm = (printer.paperWidthMm ?: PrinterSettingsManager.DEFAULT_PAPER_WIDTH_MM)
            val is80 = widthMm >= 72
            val line = "-".repeat(if (is80) 42 else 32)
            sb.append("[C]$line\n")

            val grouped = filtered.groupBy { it.categoryId?.toIntOrNull() }
            grouped.forEach { (idInt, groupItems) ->
                val catName = CategoriesApiService.getCategoryName(categories, idInt)
                sb.append("[C]<b>${catName}</b>\n")
                sb.append("[C]$line\n")
                groupItems.forEach { item ->
                    val variant = item.variant?.let { " ($it)" } ?: ""
                    sb.append("[L]${item.name}$variant[R]x${item.quantity}\n")
                    if (!item.note.isNullOrBlank()) {
                        sb.append("  Note: ${item.note}\n")
                    }
                }
                sb.append("[C]$line\n")
            }
            return sb.toString().trimEnd()
        } else {
            appendLineIfNotBlank(sb, "Komers")
            appendLineIfNotBlank(sb, "Order #${order.orderId}")
            appendLineIfNotBlank(sb, formatDisplayDate(order.createdAt))
            appendLineIfNotBlank(sb, "Status: ${order.status}")
            appendLineIfNotBlank(sb, "Note: ${order.globalNote ?: ""}")
            val customer = order.customerName?.trim()
            if (!customer.isNullOrBlank() && customer.lowercase() != "none") {
                appendLineIfNotBlank(sb, "Customer: ${customer}")
            }
            val phone = order.customerPhone?.trim()
            if (!phone.isNullOrBlank() && phone.lowercase() != "none") {
                appendLineIfNotBlank(sb, "Phone: ${phone}")
            }
            appendLineIfNotBlank(sb, "Table: ${order.tableNumber ?: "N/A"}")
            appendLineIfNotBlank(sb, "Delivery: ${order.deliveryMethod}")
            sb.append("------------------------------\n")
            filtered.forEach { item ->
                val idInt = item.categoryId?.toIntOrNull()
                val catName = CategoriesApiService.getCategoryName(categories, idInt)
                val qty = item.quantity
                val variant = item.variant?.let { " ($it)" } ?: ""
                sb.append("${item.name}$variant x$qty\n")
                if (!item.note.isNullOrBlank()) {
                    sb.append("  Note: ${item.note}\n")
                }
                sb.append("  [${catName}]\n")
            }
            sb.append("------------------------------\n")
            sb.append("Thank you!\n")
            return sb.toString().trimEnd()
        }
    }
    private fun formatDisplayDate(createdAt: String?): String? {
        if (createdAt.isNullOrBlank()) return null
        val desired = DateTimeFormatter.ofPattern("dd/MM/yyyy - hh:mm a")
        val zone = try { ZoneId.systemDefault() } catch (_: Exception) { ZoneId.of("UTC") }
        try { val zdt = ZonedDateTime.ofInstant(Instant.parse(createdAt), zone); return desired.format(zdt) } catch (_: Exception) { }
        try { val odt = OffsetDateTime.parse(createdAt); return desired.format(odt.atZoneSameInstant(zone)) } catch (_: Exception) { }
        val patterns = listOf("yyyy-MM-dd HH:mm:ss","yyyy-MM-dd HH:mm","yyyy/MM/dd HH:mm:ss","yyyy/MM/dd HH:mm","yyyy-MM-dd'T'HH:mm:ss","yyyy-MM-dd'T'HH:mm")
        for (p in patterns) { try { val ldt = LocalDateTime.parse(createdAt, DateTimeFormatter.ofPattern(p)); return desired.format(ldt.atZone(zone)) } catch (_: Exception) { } }
        return createdAt
    }

    /**
     * Print the content to the given printer and apply status updates after successful print.
     */
    suspend fun printOrder(context: Context, printer: SavedPrinter, content: String, order: OrdersSyncWorker.OrderDto): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val ok = if (printer.port == 0) {
                    // Bluetooth ESC/POS printing path (host is MAC address)
                    if (!BluetoothPermissionManager(context).hasBluetoothPermissions()) {
                        Log.w(TAG, "Bluetooth permissions missing; cannot print to ${printer.host}")
                        false
                    } else {
                        try {
                            val list = BluetoothPrintersConnections().getList()?.toList() ?: emptyList()
                            val target = list.firstOrNull { conn ->
                                try { conn.device?.address == printer.host } catch (_: Exception) { false }
                            } ?: list.firstOrNull()
                            if (target == null) {
                                Log.w(TAG, "No paired Bluetooth printers found")
                                false
                            } else {
                                val connected = target.connect()
                                // Heuristic for paper width and columns
                                val widthMm = (printer.paperWidthMm ?: PrinterSettingsManager.DEFAULT_PAPER_WIDTH_MM)
                                val is80 = widthMm >= 72
                                val chars = if (is80) 42 else 32
                                val mm = if (is80) 72f else 48f
                                val dpi = when (printer.dotsPerMm ?: PrinterSettingsManager.DEFAULT_DOTS_PER_MM) {
                                    12 -> 300
                                    else -> 203
                                }
                                val escpos = EscPosPrinter(connected, dpi, mm, chars)
                                try {
                                    val initBytes = EscPosUtils.parseHexCsv(printer.initHex)
                                    if (initBytes != null) connected.write(initBytes)
                                } catch (_: Exception) { }
                                escpos.printFormattedText(content)
                                // Add a couple of newlines to advance paper slightly
                                escpos.printFormattedText("[C]\n[C]\n")
                                // Attempt to cut paper immediately after content
                                try {
                                    val cutterBytes = EscPosUtils.parseHexCsv(printer.cutterHex)
                                    if (cutterBytes != null) {
                                        connected.write(cutterBytes)
                                    } else {
                                        // Fallback: GS V 0 (partial cut)
                                        connected.write(byteArrayOf(0x1D, 0x56, 0x00))
                                    }
                                } catch (_: Exception) {
                                    // If cut command fails, printing is still considered ok
                                }
                                true
                            }
                        } catch (t: Throwable) {
                            Log.e(TAG, "Bluetooth print failed", t)
                            false
                        }
                    }
                } else {
                    val connection = com.dantsu.escposprinter.connection.tcp.TcpConnection(printer.host, printer.port)
                    val widthMm = (printer.paperWidthMm ?: PrinterSettingsManager.DEFAULT_PAPER_WIDTH_MM)
                    val is80 = widthMm >= 72
                    val chars = if (is80) 42 else 32
                    val mm = if (is80) 72f else 48f
                    val dpi = when (printer.dotsPerMm ?: PrinterSettingsManager.DEFAULT_DOTS_PER_MM) { 12 -> 300; else -> 203 }
                    val escpos = EscPosPrinter(connection, dpi, mm, chars)
                    try {
                        val initBytes = EscPosUtils.parseHexCsv(printer.initHex)
                        if (initBytes != null) connection.write(initBytes)
                    } catch (_: Exception) { }
                    escpos.printFormattedText(content)
                    try {
                        val cutterBytes = EscPosUtils.parseHexCsv(printer.cutterHex)
                        if (cutterBytes != null) connection.write(cutterBytes)
                    } catch (_: Exception) { }
                    escpos.printFormattedText("[C]\n[C]\n")
                    true
                }
                if (ok) {
                    updateStatusesAfterPrint(context, order, printer)
                } else {
                    try { AppDatabaseHelper(context).updateOrderStatus(order.orderId, "print_failed") } catch (_: Exception) { }
                }
                ok
            } catch (t: Throwable) {
                Log.e(TAG, "Printing failed", t)
                try { AppDatabaseHelper(context).updateOrderStatus(order.orderId, "print_failed") } catch (_: Exception) { }
                false
            }
        }
    }

    /**
     * Apply category-aware status updates after a successful print.
     * - If selection includes 0 → set order status to 'printed'
     * - Else → mark prepared=true for matching items and if all prepared, set order status to 'ready'
     * Note: HTTP endpoints for updates may vary; this uses DB flags and placeholders for remote sync.
     */
    private fun updateStatusesAfterPrint(context: Context, order: OrdersSyncWorker.OrderDto, printer: SavedPrinter) {
        val db = AppDatabaseHelper(context)
        val items = db.getOrderItems(order.orderId)
        val selected = printer.selectedCategoryIds ?: emptyList()
        val includeAll = selected.contains(0)
        val includeUncategorized = printer.uncategorizedSelected == true
        val categoryIds: Set<Int> = if (includeAll) {
            items.mapNotNull { it.categoryId?.toIntOrNull() }.toSet()
        } else if (selected.isEmpty() && !includeUncategorized) {
            val global = try { CategoriesSelectionStore(context).getSelectedCategories() } catch (_: Exception) { emptyList<Int?>() }
            global.mapNotNull { it }.toSet()
        } else {
            selected.toSet()
        }.filter { it != 0 }.toSet()

        try { db.updatePrintedForCategories(order.orderId, categoryIds, includeUncategorized) } catch (_: Exception) { }

        try {
            val authMgr = MerchantAuthManager.getInstance(context)
            val auth = authMgr.getAuthorizationHeader()
            val userId = authMgr.getCurrentUser()?.id
            kotlinx.coroutines.runBlocking {
                MerchantOrderStatusApi.markPrintedByCategory(order.orderId, categoryIds.toList(), auth, userId)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Remote printed-by-category update failed for ${order.orderId}", t)
        }
    }

    /**
     * Print a specific order by its ID to all eligible saved printers.
     * Eligible printers have either category selections, include uncategorized, or include All (0).
     * Returns the number of printers that successfully printed.
     */
    suspend fun printOrderById(context: Context, orderId: String): Int = withContext(Dispatchers.IO) {
        val db = AppDatabaseHelper(context)
        val items = db.getOrderItems(orderId)
        val printers = PrinterSettingsManager(context).getPrinters().filter { sp ->
            val sel = sp.selectedCategoryIds
            val unc = sp.uncategorizedSelected == true
            (sel != null && (sel.isNotEmpty() || sel.contains(0))) || unc
        }

        if (items.isEmpty() || printers.isEmpty()) {
            db.insertPrintLog(
                PrintLog(
                    printerId = null,
                    host = orderId,
                    port = 0,
                    label = if (items.isEmpty()) "no_items" else "no_eligible_printers",
                    type = "manual_print_id",
                    success = false
                )
            )
            return@withContext 0
        }

        val header = com.bitchat.android.db.AppDatabaseHelper.fetchOrderHeader(context, orderId)
        val orderDto = OrdersSyncWorker.OrderDto(
            id = header?.id,
            orderId = orderId,
            globalNote = header?.globalNote,
            customerName = header?.customerName,
            customerPhone = header?.customerPhone,
            tableNumber = header?.tableNumber,
            createdAt = header?.createdAt,
            deliveryMethod = header?.deliveryMethod,
            deviceId = header?.deviceId,
            userId = header?.userId,
            status = header?.status,
            updatedAtStatus = header?.updatedAtStatus,
            products = null
        )

        var successCount = 0
        printers.forEach { printer ->
            try {
                val content = formatOrderForPrinter(context, orderDto, printer)
                if (content.isBlank()) {
                    db.insertPrintLog(
                        PrintLog(
                            printerId = printer.id,
                            host = printer.host,
                            port = printer.port,
                            label = "empty_content",
                            type = "manual_print_id",
                            success = false
                        )
                    )
                } else {
                    val ok = printOrder(context, printer, content, orderDto)
                    db.insertPrintLog(
                        PrintLog(
                            printerId = printer.id,
                            host = printer.host,
                            port = printer.port,
                            label = null,
                            type = "manual_print_id",
                            success = ok
                        )
                    )
                    if (ok) successCount++
                }
            } catch (t: Throwable) {
                db.insertPrintLog(
                    PrintLog(
                        printerId = printer.id,
                        host = printer.host,
                        port = printer.port,
                        label = t.message,
                        type = "manual_print_id",
                        success = false
                    )
                )
            }
        }
        if (successCount > 0) {
            try { db.updateOrderStatus(orderId, "printed") } catch (_: Exception) { }
        }
        successCount
    }

    /**
     * Print the latest N orders by insertion time to eligible printers.
     * Returns the number of orders that printed to at least one printer.
     */
    suspend fun printLatestOrders(context: Context, limit: Int = 5): Int = withContext(Dispatchers.IO) {
        val db = AppDatabaseHelper(context)
        val ids = db.getLatestOrderIds(limit)
        var ordersPrinted = 0
        ids.forEach { id ->
            val printersPrinted = printOrderById(context, id)
            if (printersPrinted > 0) ordersPrinted++
        }
        db.insertPrintLog(
            PrintLog(
                printerId = null,
                host = "latest",
                port = limit,
                label = "printed_orders=$ordersPrinted",
                type = "manual_print_latest",
                success = true
            )
        )
        ordersPrinted
    }
}
