package com.bitchat.android.merchant

import android.util.Log
import com.bitchat.android.net.OkHttpProvider
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Fetches product categories from API and provides helper methods.
 * Mirrors the behavior described in PRINT_BY_CATEGORIES.md.
 */
object CategoriesApiService {
    private const val TAG = "CategoriesApiService"
    private const val CATEGORIES_URL = "https://node-client.realm.chat/api/product/categories"
    private val gson = Gson()
    @Volatile private var cachedCategories: List<Category>? = null

    data class Category(
        val id: Int?, // null used for "Uncategorized"
        val name: String
    )

    data class CategoriesResponse(
        val success: Boolean,
        val message: String?,
        val data: List<CategoryDto>?
    )

    data class CategoryDto(
        val id: Int,
        val name: String
    )

    /**
     * Fetch categories and prepend an Uncategorized entry (id = null).
     */
    suspend fun fetchCategories(authorizationHeader: String? = null): List<Category> = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpProvider.httpClient()
            val requestBuilder = Request.Builder().url(CATEGORIES_URL).get()
            if (!authorizationHeader.isNullOrBlank()) requestBuilder.addHeader("Authorization", authorizationHeader)
            val response = client.newCall(requestBuilder.build()).execute()
            val body = response.body?.string()
            if (!response.isSuccessful || body.isNullOrEmpty()) {
                Log.w(TAG, "Categories request failed: code=${response.code}")
                val fallback = cachedCategories ?: emptyList()
                return@withContext listOf(Category(id = null, name = "Uncategorized")) + fallback.filter { it.id != null }
            }
            val parsed = gson.fromJson(body, CategoriesResponse::class.java)
            val list = parsed.data?.map { Category(id = it.id, name = it.name) } ?: emptyList()
            cachedCategories = list
            return@withContext listOf(Category(id = null, name = "Uncategorized")) + list
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch categories", e)
            val fallback = cachedCategories ?: emptyList()
            return@withContext listOf(Category(id = null, name = "Uncategorized")) + fallback.filter { it.id != null }
        }
    }

    /**
     * Convenience to extract a display name for a category ID.
     */
    fun getCategoryName(categories: List<Category>, id: Int?): String {
        return categories.firstOrNull { it.id == id }?.name ?: (if (id == null) "Uncategorized" else "Unknown")
    }

    fun getCached(): List<Category> {
        val list = cachedCategories ?: emptyList()
        return listOf(Category(id = null, name = "Uncategorized")) + list
    }
}
