package com.bitchat.android.komprint

import android.content.Context
import com.bitchat.android.db.AppDatabaseHelper
import com.bitchat.android.merchant.OrdersSyncWorker
import com.bitchat.android.printer.PrinterManager
import com.bitchat.android.printer.PrinterSettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object LocalPrintQueue {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var running = false

    fun addJob(context: Context, payloadJson: String) {
        AppDatabaseHelper(context).enqueuePrintJob(payloadJson)
    }

    fun process(context: Context) {
        if (running) return
        running = true
        scope.launch {
            val db = AppDatabaseHelper(context)
            while (true) {
                val item = db.nextPendingPrintJob() ?: break
                db.markPrintJobProcessing(item.id)
                val ok = try {
                    val parsed = KomPrintValidator.parse(item.payload)
                    // Persist to DB for category-aware formatting
                    val dto = OrdersSyncWorker.OrderDto(
                            id = null,
                            orderId = parsed.order.orderId,
                            globalNote = null,
                            customerName = null,
                            customerPhone = null,
                            tableNumber = parsed.order.tableNumber,
                            createdAt = parsed.ts,
                            deliveryMethod = null,
                            deviceId = null,
                            userId = parsed.merchantId,
                            status = parsed.order.status,
                            updatedAtStatus = null,
                            products = parsed.order.products.map {
                                OrdersSyncWorker.ProductDto(
                                    id = null,
                                    name = it.name,
                                    price = it.price?.toString(),
                                    quantity = it.qty,
                                    variant = null,
                                    categoryId = it.categoryId,
                                    note = null,
                                    prepared = null,
                                    printed = null
                                )
                            }
                        )
                    db.upsertOrder(dto.orderId, dto.id, dto.createdAt, dto.deliveryMethod, dto.userId, dto.status, payload = try { com.google.gson.Gson().toJson(dto) } catch (_: Exception) { null })
                    val items = dto.products?.map { p ->
                        AppDatabaseHelper.OrderItem(
                            itemId = p.id,
                            name = p.name,
                            quantity = (p.quantity ?: 0),
                            variant = p.variant,
                            categoryId = p.categoryId,
                            note = p.note,
                            prepared = false,
                            printed = false
                        )
                    }.orEmpty()
                    db.replaceOrderItems(dto.orderId, items)

                    val printers = PrinterSettingsManager(context).getPrinters()
                    val mains = printers.filter { it.role == "main" }
                    val stations = printers.filter { it.role == "station" }
                    if (mains.isEmpty()) {
                        try {
                            db.insertPrintLog(
                                com.bitchat.android.db.PrintLog(
                                    printerId = null,
                                    host = "deeplink",
                                    port = 0,
                                    label = "no_main_printers",
                                    type = "komprint_main",
                                    success = false
                                )
                            )
                        } catch (_: Exception) { }
                    }
                    var anyOk = false
                    // Main printers: print E-RECEIPT per printer settings
                    mains.forEach { p ->
                        try {
                            val content = KomPrintTemplates.formatMainEReceiptForPrinter(parsed, p)
                            val okMain = PrinterManager.printOrder(context, p, content, dto)
                            db.insertPrintLog(com.bitchat.android.db.PrintLog(p.id, p.host, p.port, "order=" + dto.orderId + "|printer=" + (p.label ?: ""), "komprint_main", okMain))
                            if (okMain) anyOk = true
                        } catch (_: Exception) { db.insertPrintLog(com.bitchat.android.db.PrintLog(p.id, p.host, p.port, "order=" + dto.orderId + "|printer=" + (p.label ?: ""), "komprint_main", false)) }
                    }
                    // Station printers: category-aware content
                    stations.forEach { p ->
                        try {
                            val content = PrinterManager.formatOrderForPrinter(context, dto, p)
                            if (content.isNotBlank()) {
                                val okSt = PrinterManager.printOrder(context, p, content, dto)
                                db.insertPrintLog(com.bitchat.android.db.PrintLog(p.id, p.host, p.port, "order=" + dto.orderId + "|printer=" + (p.label ?: ""), "komprint_station", okSt))
                                if (okSt) anyOk = true
                            } else {
                                db.insertPrintLog(com.bitchat.android.db.PrintLog(p.id, p.host, p.port, "empty|order=" + dto.orderId + "|printer=" + (p.label ?: ""), "komprint_station", false))
                            }
                        } catch (_: Exception) { db.insertPrintLog(com.bitchat.android.db.PrintLog(p.id, p.host, p.port, "order=" + dto.orderId + "|printer=" + (p.label ?: ""), "komprint_station", false)) }
                    }
                    anyOk
                } catch (_: Exception) { false }
                if (ok) {
                    db.markPrintJobDone(item.id)
                } else {
                    db.markPrintJobFailed(item.id, item.retries + 1)
                }
            }
            running = false
        }
    }
}
