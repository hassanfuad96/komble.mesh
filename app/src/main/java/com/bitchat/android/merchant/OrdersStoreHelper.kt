package com.bitchat.android.merchant

import android.content.Context
import android.util.Log
import com.bitchat.android.db.AppDatabaseHelper
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken

/**
 * Fetches orders from go.realm.chat and stores them into SQLite
 * so the ContentProvider can immediately serve data.
 */
object OrdersStoreHelper {
    private const val TAG = "OrdersStoreHelper"
    private val gson = Gson()

    data class ApiResponse<T>(
        val success: Boolean,
        val message: String?,
        val data: T?
    )

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
        val prepared: Boolean?
    )

    /**
     * Fetch orders for [userId] and store them into the local DB.
     * @return number of orders stored (upserted)
     */
    suspend fun fetchAndStore(context: Context, userId: Int, authorizationHeader: String? = null): Int {
        val raw = MerchantOrdersApi.fetchOrdersForUser(userId, authorizationHeader)
        if (raw.isNullOrEmpty()) {
            Log.w(TAG, "No response or empty body when fetching orders")
            return 0
        }
        return try {
            val type = object : TypeToken<ApiResponse<Array<OrderDto>>>() {}.type
            val parsed: ApiResponse<Array<OrderDto>> = gson.fromJson(raw, type)
            val orders = parsed.data?.toList() ?: emptyList()
            if (orders.isEmpty()) {
                Log.i(TAG, "No orders in response")
                return 0
            }

        val db = AppDatabaseHelper(context)
            var count = 0
            for (o in orders) {
                // Upsert order header
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
                        prepared = (p.prepared ?: false)
                    )
                }.orEmpty()
                db.replaceOrderItems(o.orderId, items)
                count++
            }
            Log.d(TAG, "Stored $count orders into SQLite")
            count
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse/store orders", e)
            0
        }
    }
}