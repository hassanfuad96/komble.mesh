package com.bitchat.android.komprint

import org.json.JSONObject

object KomPrintErrors {
    const val PRINT_ERR_INVALID_JSON = "PRINT_ERR_INVALID_JSON"
    const val PRINT_ERR_BAD_SCHEMA = "PRINT_ERR_BAD_SCHEMA"
    const val PRINT_ERR_MISSING_FIELD = "PRINT_ERR_MISSING_FIELD"
}

data class KomPrintResult(val success: Boolean, val error: String?, val message: String?)

data class KomPrintOrderProduct(val name: String, val qty: Int, val price: Double?, val categoryId: String?)

data class KomPrintOrder(
    val orderId: String,
    val tableNumber: String?,
    val status: String?,
    val products: List<KomPrintOrderProduct>
)

data class KomPrintPayload(
    val type: String,
    val merchantId: String,
    val ts: String,
    val order: KomPrintOrder,
    val copies: Int,
    val paperWidth: Int
)

object KomPrintValidator {
    fun parse(json: String): KomPrintPayload {
        val root = try { JSONObject(json) } catch (_: Exception) { throw IllegalArgumentException(KomPrintErrors.PRINT_ERR_INVALID_JSON) }
        if (!root.has("type") || !root.has("merchant_id") || !root.has("ts") || !root.has("order")) throw IllegalArgumentException(KomPrintErrors.PRINT_ERR_MISSING_FIELD)
        val type = root.optString("type")
        val merchantId = root.optString("merchant_id")
        val ts = root.optString("ts")
        val o = root.optJSONObject("order") ?: throw IllegalArgumentException(KomPrintErrors.PRINT_ERR_BAD_SCHEMA)
        val orderId = o.optString("order_id")
        if (orderId.isBlank()) throw IllegalArgumentException(KomPrintErrors.PRINT_ERR_MISSING_FIELD)
        val table = o.optString("table_number", null)
        val status = o.optString("status", null)
        val productsArr = o.optJSONArray("products") ?: throw IllegalArgumentException(KomPrintErrors.PRINT_ERR_MISSING_FIELD)
        val products = mutableListOf<KomPrintOrderProduct>()
        for (i in 0 until productsArr.length()) {
            val p = productsArr.optJSONObject(i) ?: throw IllegalArgumentException(KomPrintErrors.PRINT_ERR_BAD_SCHEMA)
            val name = p.optString("name")
            val qty = try { p.optInt("qty") } catch (_: Exception) { 0 }
            val price = if (p.has("price")) try { p.optDouble("price") } catch (_: Exception) { null } else null
            val cat = if (p.has("category_id")) p.optString("category_id") else null
            if (name.isBlank() || qty <= 0) throw IllegalArgumentException(KomPrintErrors.PRINT_ERR_BAD_SCHEMA)
            products.add(KomPrintOrderProduct(name, qty, price, cat))
        }
        val printOptions = root.optJSONObject("printOptions")
        val copies = printOptions?.optInt("copies") ?: 1
        val paperWidth = printOptions?.optInt("paperWidth") ?: 58
        return KomPrintPayload(type, merchantId, ts, KomPrintOrder(orderId, table, status, products), copies, paperWidth)
    }
}
