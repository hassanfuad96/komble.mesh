package com.bitchat.android.komprint

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

class KomPrintValidatorTest {
    @Test
    fun validPayloadParses() {
        val obj = JSONObject()
            .put("type", "order.printed")
            .put("merchant_id", "1")
            .put("ts", "2025-11-28T20:22:12Z")
            .put("order", JSONObject()
                .put("order_id", "187786685697457")
                .put("table_number", "T10")
                .put("status", "printed")
                .put("products", org.json.JSONArray()
                    .put(JSONObject().put("name", "Nasi Goreng Cina").put("qty", 1).put("price", 30))
                )
            )
            .put("printOptions", JSONObject().put("copies", 1).put("paperWidth", 58))
        val payload = KomPrintValidator.parse(obj.toString())
        assertEquals("order.printed", payload.type)
        assertEquals("1", payload.merchantId)
        assertEquals("187786685697457", payload.order.orderId)
        assertTrue(payload.order.products.isNotEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun missingOrderFails() {
        val obj = JSONObject()
            .put("type", "order.printed")
            .put("merchant_id", "1")
            .put("ts", "2025-11-28T20:22:12Z")
        KomPrintValidator.parse(obj.toString())
    }
}
