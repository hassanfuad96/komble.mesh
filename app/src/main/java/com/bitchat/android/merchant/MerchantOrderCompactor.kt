package com.bitchat.android.merchant

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.time.Instant

/**
 * Utility to compact merchant orders JSON for BLE-friendly transmission.
 * - Uses short keys: id, oid, s, u, m, st, ts, p
 * - Strips empty fields
 * - Converts ISO8601 times to Unix ms
 */
object MerchantOrderCompactor {
    private val gson = Gson()

    /**
     * Converts API response JSON string into a list of compact per-order JSON strings.
     */
    fun compactOrders(rawJson: String?): List<String> {
        if (rawJson.isNullOrBlank()) return emptyList()
        return try {
            val root = JsonParser.parseString(rawJson)
            if (!root.isJsonObject) return emptyList()
            val obj = root.asJsonObject
            val data = obj.get("data")
            if (data == null || !data.isJsonArray) return emptyList()

            data.asJsonArray.mapNotNull { orderEl ->
                compactSingleOrder(orderEl)?.let { gson.toJson(it) }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun compactSingleOrder(orderEl: JsonElement): JsonObject? {
        if (!orderEl.isJsonObject) return null
        val o = orderEl.asJsonObject

        val out = JsonObject()

        // Required/primary fields
        putIfNotBlank(out, "id", o.get("id")?.asString)
        putIfNotBlank(out, "oid", o.get("order_id")?.asString)
        putIfNotBlank(out, "s", o.get("device_id")?.asString)
        putIfNotBlank(out, "u", o.get("user_id")?.asString)

        // Delivery method short code: TA, DI, DL
        val dm = o.get("delivery_method")?.asString
        val dmShort = when (dm?.lowercase()) {
            "take away" -> "TA"
            "dine in" -> "DI"
            "delivery" -> "DL"
            else -> null
        }
        putIfNotBlank(out, "m", dmShort ?: dm)

        // Status
        putIfNotBlank(out, "st", o.get("status")?.asString)

        // Timestamps -> Unix ms (ISO8601)
        val createdAtMs = isoToMs(o.get("created_at")?.asString)
        if (createdAtMs != null) out.addProperty("ts", createdAtMs)
        val updatedStatusMs = isoToMs(o.get("updated_at_status")?.asString)
        if (updatedStatusMs != null) out.addProperty("tsu", updatedStatusMs)

        // Products array
        val productsEl = o.get("products")
        if (productsEl != null && productsEl.isJsonArray) {
            val compactProducts = com.google.gson.JsonArray()
            productsEl.asJsonArray.forEach { pEl ->
                val pObj = pEl.asJsonObject
                val cp = JsonObject()
                putIfNotBlank(cp, "n", pObj.get("name")?.asString)
                // price numeric
                pObj.get("price")?.let { priceEl ->
                    if (priceEl.isJsonPrimitive) {
                        val prim = priceEl.asJsonPrimitive
                        if (prim.isNumber) cp.addProperty("pr", prim.asNumber)
                    }
                }
                // quantity numeric
                pObj.get("quantity")?.let { qEl ->
                    if (qEl.isJsonPrimitive) {
                        val prim = qEl.asJsonPrimitive
                        if (prim.isNumber) cp.addProperty("q", prim.asNumber)
                    }
                }
                // category id -> number if possible
                pObj.get("category_id")?.let { cEl ->
                    val s = cEl.asString
                    val n = s.toLongOrNull()
                    if (n != null) cp.addProperty("c", n) else putIfNotBlank(cp, "c", s)
                }
                // optional variant
                putIfNotBlank(cp, "v", pObj.get("variant")?.asString)
                // optional note (short key "no")
                putIfNotBlank(cp, "no", pObj.get("note")?.asString)

                // Only add if at least one field present
                if (cp.entrySet().isNotEmpty()) compactProducts.add(cp)
            }
            if (compactProducts.size() > 0) out.add("p", compactProducts)
        }

        // Only return object if it has core fields
        return if (out.entrySet().isNotEmpty()) out else null
    }

    private fun isoToMs(iso: String?): Long? {
        if (iso.isNullOrBlank()) return null
        return try { Instant.parse(iso).toEpochMilli() } catch (_: Exception) { null }
    }

    private fun putIfNotBlank(obj: JsonObject, key: String, value: String?) {
        if (!value.isNullOrBlank()) obj.addProperty(key, value)
    }
}