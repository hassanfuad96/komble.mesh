package com.bitchat.android.komprint

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
        val sb = StringBuilder()

    sb.append("[C]--------- <b>E-RECEIPT</b> ---------\n")
    sb.append("Order#: $orderId\n")
    sb.append("Tarikh : $date\n")
    sb.append("Total  : RM${df.format(total)}\n")
    sb.append("Bayaran: $payment\n")
    sb.append("\n")

    sb.append("Items:\n")
    order.order.products.forEach { p ->
        val name = p.name.take(16).padEnd(16, ' ')          // fits 58mm
        val qty = ("x" + p.qty.toString()).padEnd(4, ' ')
        val price = df.format(p.price ?: 0.0).padStart(6, ' ')
        sb.append("$name $qty $price\n")
    }

    sb.append("--------------------------------\n")
    sb.append("[C]Imbas / Scan for full receipt\n")
    sb.append("[C]<qrcode size='20'>$url</qrcode>\n")
    sb.append("[C]\n")

    sb.append("[C]-- Kurangkan Penggunaan Kertas --\n")
    sb.append("[C]Claim cashback & loyalty via QR\n")
    sb.append("[C]\n")

    sb.append("[C]Powered by Komers.io\n")
    sb.append("--------------------------------\n")
        return sb.toString()
    }
}