package com.bitchat.android.komprint

import android.graphics.BitmapFactory
import android.util.Base64
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.textparser.PrinterTextParserImg
import java.text.DecimalFormat

object KomPrintTemplates {
    fun format58(order: KomPrintPayload): String {
        val df = DecimalFormat("0.00")
        val total = order.order.products.fold(0.0) { acc, p -> acc + ((p.price ?: 0.0) * p.qty) }
        val sb = StringBuilder()
        sb.append(order.order.tableNumber ?: "").append("\n")
        sb.append("--------------------------------\n")
        order.order.products.forEach { p ->
            val name = p.name.take(24).padEnd(24, ' ')
            val qty = "x" + p.qty.toString()
            val price = df.format(p.price ?: 0.0).padStart(6, ' ')
            sb.append(name).append(" ").append(qty).append(" ").append(price).append("\n")
        }
        sb.append("--------------------------------\n")
        sb.append("Total").append(" ").append(df.format(total).padStart(23, ' ')).append("\n")
        sb.append("Printed at: ").append(order.ts).append("\n")
        sb.append("--------------------------------\n")
        return sb.toString()
    }
    fun format80(order: KomPrintPayload): String = format58(order)
    fun formatKitchen(order: KomPrintPayload): String = format58(order)
    fun formatReceipt(order: KomPrintPayload): String = format58(order)
    /**
     * Formats the main E-Receipt with rich-text tags for alignment, bold and larger font.
     * - Item lines: bold with increased font size.
     * - Total line: bold with increased font size.
     */
    fun formatMainEReceipt(order: KomPrintPayload): String {
        val df = java.text.DecimalFormat("0.00")
        val orderId = order.order.orderId
        val url = "https://wa.komers.io/order/$orderId"
        val total = order.order.products.fold(0.0) { acc, p -> acc + ((p.price ?: 0.0) * p.qty) }
        val is80 = order.paperWidth >= 72
        val cols = if (is80) 42 else 32
        val line = "-".repeat(cols)
        val sb = StringBuilder()
        sb.append("[C]<b>E-RECEIPT</b>\n")
        sb.append("[C]Order#: $orderId\n")
        sb.append("[C]$line\n")
        sb.append("[C]Items:\n")
        order.order.products.forEach { p ->
            val left = p.name
            val right = df.format(p.price ?: 0.0)
            // Bold and increased font size for item details
            sb.append("[L]<font size='big'><b>$left x${p.qty}</b></font>[R]<font size='big'><b>$right</b></font>\n")
        }
        sb.append("[C]$line\n")
        // Bold and increased font size for total
        sb.append("[C]<font size='big'><b>Total: ${df.format(total)}</b></font>\n")
        sb.append("[C]$line\n")
        sb.append("[C]Scan for full receipt\n")
        sb.append("$line\n")
        // Use smaller QR code module size for compact display
        sb.append("[C]<qrcode size='10'>$url</qrcode>\n")
        sb.append("\n")
        sb.append("[C]Or visit:\n")
        sb.append("[C]$url\n")
        sb.append("\n")
        sb.append("[C]Thank you!\n")
        sb.append("[C]Powered by Komers.io\n")
        return sb.toString()
    }

    fun formatMainEReceiptForPrinter(order: KomPrintPayload, printer: com.bitchat.android.printer.SavedPrinter): String {
        val df = java.text.DecimalFormat("0.00")
        val orderId = order.order.orderId
        val url = "https://wa.komers.io/order/$orderId"
        val total = order.order.products.fold(0.0) { acc, p -> acc + ((p.price ?: 0.0) * p.qty) }
        val is80 = order.paperWidth >= 72
        val cols = if (is80) 42 else 32
        val line = "-".repeat(cols)
        val itemSize = printer.eReceiptItemSize?.ifBlank { null } ?: "big"
        val totalSize = printer.eReceiptTotalSize?.ifBlank { null } ?: itemSize
        val headerSize = printer.eReceiptHeaderSize?.ifBlank { null } ?: "big"
        val bodySize = printer.eReceiptBodySize?.ifBlank { null } ?: "normal"
        val qrSize = (printer.qrModuleSize ?: 10).coerceIn(1,16)
        val sb = StringBuilder()
        sb.append("[C]<font size='${headerSize}'><b>E-RECEIPT</b></font>\n")
        sb.append("[C]<font size='${bodySize}'>Order#: $orderId</font>\n")
        sb.append("[C]$line\n")
        sb.append("[C]Items:\n")
        order.order.products.forEach { p ->
            val left = p.name
            val right = df.format(p.price ?: 0.0)
            sb.append("[L]<font size='${itemSize}'><b>$left x${p.qty}</b></font>[R]<font size='${itemSize}'><b>$right</b></font>\n")
        }
        sb.append("[C]$line\n")
        sb.append("[C]<font size='${totalSize}'><b>Total: ${df.format(total)}</b></font>\n")
        sb.append("[C]$line\n")
        sb.append("[C]<font size='${bodySize}'>Scan for full receipt</font>\n")
        sb.append("$line\n")
        sb.append("[C]<qrcode size='${qrSize}'>$url</qrcode>\n")
        sb.append("\n")
        sb.append("[C]<font size='${bodySize}'>Or visit:</font>\n")
        sb.append("[C]<font size='${bodySize}'>$url</font>\n")
        sb.append("\n")
        sb.append("[C]<font size='${bodySize}'>Thank you!</font>\n")
        sb.append("[C]<font size='${bodySize}'>Powered by Komers.io</font>\n")
        return sb.toString()
    }

    fun composeWithHeaderFooter(
        printer: EscPosPrinter?,
        headerBase64: String?,
        headerText: String?,
        content: String,
        footerText: String?
    ): String {
        val sb = StringBuilder()
        if (!headerBase64.isNullOrBlank() && printer != null) {
            val bytes = try { Base64.decode(headerBase64, Base64.DEFAULT) } catch (_: Exception) { null }
            if (bytes != null) {
                val bmp = try { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) } catch (_: Exception) { null }
                if (bmp != null) {
                    val hex = PrinterTextParserImg.bitmapToHexadecimalString(printer, bmp)
                    sb.append("[C]<img>").append(hex).append("</img>\n")
                }
            }
        }
        if (!headerText.isNullOrBlank()) sb.append("[C]").append(headerText.trim()).append("\n")
        sb.append(content)
        if (!footerText.isNullOrBlank()) sb.append("\n[C]").append(footerText.trim()).append("\n")
        return sb.toString()
    }
}
