package com.bitchat.android.merchant

import android.util.Log
import com.bitchat.android.net.OkHttpProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * API helper for merchant authentication
 * Uses OkHttpProvider to respect Tor proxy settings
 */
object MerchantAuthApi {
    
    private const val TAG = "MerchantAuthApi"
    private const val LOGIN_URL = "https://node-client.realm.chat/api/auth/login"
    
    private val gson = Gson()
    
    /**
     * Perform merchant login
     * @param email Merchant email
     * @param password Merchant password
     * @return MerchantLoginResponse or null if failed
     */
    suspend fun login(email: String, password: String): MerchantLoginResponse? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Attempting merchant login for email: $email")
                
                val client = OkHttpProvider.httpClient()
                
                // Create request body
                val loginRequest = MerchantLoginRequest(email = email, password = password)
                val requestJson = gson.toJson(loginRequest)
                val mediaType = "application/json".toMediaType()
                val requestBody = requestJson.toRequestBody(mediaType)
                
                // Build request
                val request = Request.Builder()
                    .url(LOGIN_URL)
                    .post(requestBody)
                    .addHeader("Content-Type", "application/json")
                    .build()
                
                // Execute request
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                
                if (response.isSuccessful && responseBody != null) {
                    Log.d(TAG, "Login request successful")
                    val loginResponse = try {
                        gson.fromJson(responseBody, MerchantLoginResponse::class.java)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse login response: ${e.message}")
                        null
                    }
                    
                    if (loginResponse != null && loginResponse.result) {
                        Log.d(TAG, "Login successful for user: ${loginResponse.data?.user?.name}")
                        return@withContext loginResponse
                    } else {
                        Log.w(TAG, "Login failed: ${loginResponse?.message}")
                        return@withContext loginResponse ?: MerchantLoginResponse(
                            result = false,
                            message = "Invalid response",
                            data = null
                        )
                    }
                } else {
                    Log.e(TAG, "HTTP request failed: ${response.code} - ${response.message}")
                    return@withContext MerchantLoginResponse(
                        result = false,
                        message = "Network error: ${response.message}",
                        data = null
                    )
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Login request failed", e)
                return@withContext MerchantLoginResponse(
                    result = false,
                    message = "Request failed: ${e.message}",
                    data = null
                )
            }
        }
    }
}