package com.bitchat.android.merchant

import android.util.Log
import com.bitchat.android.net.OkHttpProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * API helper to fetch merchant orders for a user.
 * Uses OkHttpProvider to honor Tor proxy settings.
 */
object MerchantOrdersApi {
    private const val TAG = "MerchantOrdersApi"

    /**
     * Fetch raw orders JSON/text for a given user ID.
     * @param userId Merchant user id
     * @param authorizationHeader Optional auth header from MerchantAuthManager (e.g. "Bearer <token>")
     * @return Raw response body as String or null on failure
     */
    suspend fun fetchOrdersForUser(userId: Int, authorizationHeader: String?): String? {
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://go.realm.chat/api/v1/orders/user/$userId"
                Log.d(TAG, "Fetching orders for userId=$userId from $url")

                val client = OkHttpProvider.httpClient()
                val requestBuilder = Request.Builder()
                    .url(url)
                    .get()

                if (!authorizationHeader.isNullOrBlank()) {
                    requestBuilder.addHeader("Authorization", authorizationHeader)
                }

                val request = requestBuilder.build()
                val response = client.newCall(request).execute()
                val body = response.body?.string()

                if (response.isSuccessful && body != null) {
                    Log.d(TAG, "Orders request successful, ${body.length} bytes")
                    return@withContext body
                } else {
                    Log.w(TAG, "Orders request failed: code=${response.code} message=${response.message}")
                    return@withContext null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Orders request failed", e)
                return@withContext null
            }
        }
    }
}