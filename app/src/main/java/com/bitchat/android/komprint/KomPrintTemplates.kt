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
        sb.append("[C]Total: ${df.format(total)}\n")
        sb.append("[C]$line\n")
        sb.append("[C]Items:\n")
        order.order.products.forEach { p ->
            val left = p.name
            val right = df.format(p.price ?: 0.0)
            sb.append("[L]$left x${p.qty}[R]$right\n")
        }
        sb.append("[C]$line\n")
        sb.append("[C]Scan for full receipt\n")
        sb.append("[C]$line\n")
        sb.append("[C]<qrcode size='20'>$url</qrcode>\n")
        sb.append("[C]\n")
        sb.append("[C]Or visit:\n")
        sb.append("[C]$url\n")
        sb.append("[C]\n")
        sb.append("[C]Thank you!\n")
        sb.append("[C]Powered by Komers.io\n")
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
