package com.bitchat.android.merchant

import android.content.Context
import android.content.SharedPreferences
import com.bitchat.android.db.AppDatabaseHelper
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Persists and exposes selected categories and provides item filtering.
 * Keys and logic mirror PRINT_BY_CATEGORIES.md.
 */
class CategoriesSelectionStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("categories_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getSelectedCategories(): List<Int?> {
        val selectedJson = prefs.getString(KEY_SELECTED_CATEGORIES_JSON, null)
        val uncategorized = prefs.getString(KEY_UNCATEGORIZED_SELECTED, "false") == "true"
        val type = object : TypeToken<List<Int>>() {}.type
        val numeric = try {
            if (selectedJson.isNullOrBlank()) emptyList<Int>() else gson.fromJson<List<Int>>(selectedJson, type)
        } catch (_: Exception) {
            emptyList()
        }
        val list = numeric.map { it }
        return if (uncategorized) list + listOf(null) else list
    }

    fun setSelectedCategories(ids: List<Int?>) {
        // Persist numeric IDs only in selected_categories
        val numericIds = ids.filterNotNull()
        val json = gson.toJson(numericIds)
        prefs.edit().putString(KEY_SELECTED_CATEGORIES_JSON, json).apply()
        // Persist uncategorized flag when null is present
        val uncategorizedSelected = ids.any { it == null }
        prefs.edit().putString(KEY_UNCATEGORIZED_SELECTED, if (uncategorizedSelected) "true" else "false").apply()
    }

    /**
     * Filter items by selected categories according to rules:
     * - If selection includes 0 → return all items (All Categories)
     * - If selection is empty → return [] (skip printing)
     * - Else → include items whose category_id matches one of selected IDs
     */
    fun filterOrderItems(items: List<AppDatabaseHelper.OrderItem>): List<AppDatabaseHelper.OrderItem> {
        val selected = getSelectedCategories()
        if (selected.contains(0)) return items
        if (selected.isEmpty()) return emptyList()
        val selectedInts = selected.filterNotNull().toSet()
        return items.filter { item ->
            val idInt = item.categoryId?.toIntOrNull()
            (idInt != null && selectedInts.contains(idInt)) || (item.categoryId == null && selected.contains(null))
        }
    }

    companion object {
        private const val KEY_SELECTED_CATEGORIES_JSON = "selected_categories"
        private const val KEY_UNCATEGORIZED_SELECTED = "uncategorized_selected"
    }
}