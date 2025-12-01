package com.bitchat.android.merchant

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bitchat.android.db.AppDatabaseHelper
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken

/**
 * Periodically polls the orders API, stores results into SQLite, and queues
 * compact payloads for @phone when a brand-new order appears.
 */
class OrdersSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    private val gson = Gson()

    companion object {
        private const val TAG = "OrdersSyncWorker"
        const val KEY_USER_ID = "user_id"
        const val KEY_AUTH_HEADER = "authorization_header"
    }

    data class ApiResponse<T>(val success: Boolean, val message: String?, val data: T?)

    data class OrderDto(
        val id: String?,
        @SerializedName("order_id") val orderId: String,
        @SerializedName("global_note") val globalNote: String?,
        @SerializedName("customer_name") val customerName: String?,
        @SerializedName("customer_phone") val customerPhone: String?,
        @SerializedName("table_number") val tableNumber: String?,
        @SerializedName("created_at") val createdAt: String?,
        @SerializedName("delivery_method") val deliveryMethod: String?,
        @SerializedName("device_id") val deviceId: String?,
        @SerializedName("user_id") val userId: String?,
        val status: String?,
        @SerializedName("updated_at_status") val updatedAtStatus: String?,
        val products: List<ProductDto>?
    )

    data class ProductDto(
        val id: String?,
        val name: String,
        val price: String?,
        val quantity: Int?,
        val variant: String?,
        @SerializedName("category_id") val categoryId: String?,
        val note: String?,
        val prepared: Boolean?
    )

    override suspend fun doWork(): Result {
        val userId = inputData.getInt(KEY_USER_ID, 1)
        val auth = inputData.getString(KEY_AUTH_HEADER)
        val raw = try { MerchantOrdersApi.fetchOrdersForUser(userId, auth) } catch (e: Exception) {
            Log.e(TAG, "Fetch failed", e)
            null
        }
        if (raw.isNullOrEmpty()) return Result.success()

        val parsed = try {
            val type = object : TypeToken<ApiResponse<Array<OrderDto>>>() {}.type
            gson.fromJson<ApiResponse<Array<OrderDto>>>(raw, type)
        } catch (e: Exception) {
            Log.e(TAG, "Parse failed", e)
            return Result.success()
        }
        val orders = parsed.data?.toList().orEmpty()
        if (orders.isEmpty()) return Result.success()

        val db = AppDatabaseHelper(applicationContext)
        var newCount = 0
        orders.forEach { o ->
            val isNew = !db.orderExists(o.orderId)
            // Upsert header
            db.upsertOrder(
                orderId = o.orderId,
                id = o.id,
                createdAt = o.createdAt,
                deliveryMethod = o.deliveryMethod,
                userId = o.userId,
                status = o.status
            )
            // Replace items
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

            if (isNew) {
                newCount++
                val compact = buildCompactPayload(o, items)
                db.enqueueOrderOutbox(o.orderId, compact)
            }
        }
        Log.d(TAG, "Synced ${orders.size} orders (${newCount} new)")
        return Result.success()
    }

    private fun buildCompactPayload(order: OrderDto, items: List<AppDatabaseHelper.OrderItem>): String {
        // Compact JSON for mesh send: keep keys short
        val payload = mapOf(
            "o" to order.orderId,
            "s" to (order.status ?: ""),
            "t" to (order.tableNumber ?: ""),
            "n" to (order.customerName ?: ""),
            "p" to (order.customerPhone ?: ""),
            "c" to (order.createdAt ?: ""),
            "i" to items.map { mapOf(
                "n" to it.name,
                "q" to it.quantity,
                "v" to (it.variant ?: ""),
                "c" to (it.categoryId ?: "")
            ) }
        )
        return gson.toJson(payload)
    }
}
