package com.bitchat.android.merchant

import android.util.Log
import com.bitchat.android.net.OkHttpProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant

/**
 * API helper to update order status after printing.
 */
object MerchantOrderStatusApi {
    private const val TAG = "MerchantOrderStatusApi"

    /**
     * Update an order's status to "printed".
     * Tries flexible endpoint patterns:
     * - orders/{userId}{orderId}/status when userIdPrefix provided
     * - orders/{orderId}/status as fallback
     * @param orderId The external order id
     * @param authorizationHeader Bearer token header from MerchantAuthManager
     * @param userIdPrefix Optional merchant user id to prefix orderId if backend expects it
     * @return true if request succeeded
     */
    suspend fun markPrinted(orderId: String, authorizationHeader: String?, userIdPrefix: Int? = null): Boolean {
        if (orderId.isBlank()) return false
        return withContext(Dispatchers.IO) {
            try {
                val urls = mutableListOf<String>()
                if (userIdPrefix != null) {
                    urls.add("https://go.realm.chat/api/v1/orders/${userIdPrefix}${orderId}/status")
                }
                urls.add("https://go.realm.chat/api/v1/orders/${orderId}/status")
                val nowIso = Instant.now().toString()
                val json = "{" +
                    "\"status\":\"printed\"," +
                    "\"updated_at_status\":\"${nowIso}\"" +
                    "}"
                val mediaType = "application/json".toMediaType()
                val body = json.toRequestBody(mediaType)

                val client = OkHttpProvider.httpClient()
                for (url in urls) {
                    val builder = Request.Builder()
                        .url(url)
                        .put(body)
                        .addHeader("Content-Type", "application/json")

                    if (!authorizationHeader.isNullOrBlank()) {
                        builder.addHeader("Authorization", authorizationHeader)
                    }

                    val request = builder.build()
                    val response = client.newCall(request).execute()
                    val ok = response.isSuccessful
                    if (ok) {
                        return@withContext true
                    } else {
                        Log.w(TAG, "markPrinted failed: url=${url} code=${response.code} message=${response.message}")
                    }
                }
                false
            } catch (t: Throwable) {
                Log.e(TAG, "markPrinted request error", t)
                false
            }
        }
    }
}