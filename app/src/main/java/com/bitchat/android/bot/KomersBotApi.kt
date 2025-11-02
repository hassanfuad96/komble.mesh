package com.bitchat.android.bot

import android.util.Log
import com.bitchat.android.model.BitchatMessage
import com.bitchat.android.net.OkHttpProvider
import com.bitchat.android.util.AppConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Lightweight API helper for Komers bot.
 * Uses the shared OkHttpProvider so traffic honors Tor proxy settings.
 */
object KomersBotApi {
    private const val TAG = "KomersBotApi"

    /**
     * Fetch bot data using the incoming message content as the query.
     * Current API is a static order list endpoint; ignore message content and call static.
     */
    suspend fun fetchForMessage(message: BitchatMessage): String? = fetchStatic()

    /**
     * Core fetch with query parameter. Retained for future endpoints that accept message param.
     */
    suspend fun fetch(query: String): String? {
        val baseUrl = AppConstants.Bot.KOMERS_BOT_API_URL
        if (baseUrl.isBlank()) {
            Log.w(TAG, "Komers bot API base URL not configured. Skipping fetch.")
            return null
        }

        return withContext(Dispatchers.IO) {
            try {
                val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
                val url = if (baseUrl.contains("?")) "$baseUrl&message=$encoded" else "$baseUrl?message=$encoded"
                val builder = Request.Builder().url(url).get()
                val token = AppConstants.Bot.KOMERS_BOT_AUTH_TOKEN
                if (token.isNotBlank()) builder.header("Authorization", "Bearer $token")
                OkHttpProvider.httpClient().newCall(builder.build()).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        val msg = "HTTP ${resp.code}"
                        Log.w(TAG, "Fetch failed: $msg")
                        return@use "error: $msg"
                    }
                    return@use resp.body?.string()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fetch error: ${e.message}", e)
                "error: ${e.message}"
            }
        }
    }

    /**
     * Static fetch that calls the URL exactly as provided in constants,
     * adding Authorization bearer header when available.
     */
    suspend fun fetchStatic(): String? {
        val url = AppConstants.Bot.KOMERS_BOT_API_URL
        if (url.isBlank()) {
            Log.w(TAG, "Komers bot API base URL not configured. Skipping fetch.")
            return null
        }
        return withContext(Dispatchers.IO) {
            try {
                val builder = Request.Builder().url(url).get()
                val token = AppConstants.Bot.KOMERS_BOT_AUTH_TOKEN
                if (token.isNotBlank()) builder.header("Authorization", "Bearer $token")
                OkHttpProvider.httpClient().newCall(builder.build()).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        val msg = "HTTP ${resp.code}"
                        Log.w(TAG, "Fetch failed: $msg")
                        return@use "error: $msg"
                    }
                    return@use resp.body?.string()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fetch error: ${e.message}", e)
                "error: ${e.message}"
            }
        }
    }
}