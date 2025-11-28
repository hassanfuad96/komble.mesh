package com.bitchat.android.printer

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

data class SavedPrinter(
    val name: String?,
    val host: String,
    val port: Int,
    val label: String? = null,
    // Advanced ESC/POS settings (optional)
    val paperWidthMm: Int? = null,
    val dotsPerMm: Int? = null, // 8 or 12
    val initHex: String? = null,
    val cutterHex: String? = null,
    val drawerHex: String? = null,
    // Per-printer category selection
    val selectedCategoryIds: List<Int>? = null, // include 0 for "All"
    val uncategorizedSelected: Boolean? = null,
    // Auto-print control per printer
    val autoPrintEnabled: Boolean? = null,
    // Multi-printer support
    val id: String = UUID.randomUUID().toString()
)

class PrinterSettingsManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("printer_settings", Context.MODE_PRIVATE)
    private val gson = Gson()

    // Legacy single-printer API now maps to default printer in list
    fun savePrinter(printer: SavedPrinter) {
        val updated = getPrinters().toMutableList().apply {
            // De-duplicate by host:port
            val idx = indexOfFirst { it.host == printer.host && it.port == printer.port }
            if (idx >= 0) set(idx, printer) else add(printer)
        }
        persistPrinters(updated)
        setDefaultPrinterId(printer.id)
    }

    fun getPrinter(): SavedPrinter? = getDefaultPrinter()

    fun clearPrinter() {
        val def = getDefaultPrinterId() ?: return
        removePrinter(def)
    }

    // NEW: Multi-printer API
    fun addPrinter(printer: SavedPrinter, setDefault: Boolean = false) {
        val updated = getPrinters().toMutableList().apply {
            val idx = indexOfFirst { it.host == printer.host && it.port == printer.port }
            if (idx >= 0) set(idx, printer) else add(printer)
        }
        persistPrinters(updated)
        if (setDefault) setDefaultPrinterId(printer.id)
    }

    fun getPrinters(): List<SavedPrinter> {
        val json = prefs.getString(KEY_PRINTERS_JSON, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<SavedPrinter>>() {}.type
            gson.fromJson<List<SavedPrinter>>(json, type) ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    fun getDefaultPrinter(): SavedPrinter? {
        val id = getDefaultPrinterId() ?: return null
        return getPrinters().firstOrNull { it.id == id }
    }

    fun setDefaultPrinterId(id: String?) {
        prefs.edit().putString(KEY_DEFAULT_PRINTER_ID, id).apply()
    }

    private fun getDefaultPrinterId(): String? = prefs.getString(KEY_DEFAULT_PRINTER_ID, null)

    fun removePrinter(id: String) {
        val updated = getPrinters().filter { it.id != id }
        persistPrinters(updated)
        if (getDefaultPrinterId() == id) setDefaultPrinterId(updated.firstOrNull()?.id)
    }

    fun setPrinterCategories(id: String, selectedIds: List<Int>, includeUncategorized: Boolean) {
        val updated = getPrinters().map { p ->
            if (p.id == id) p.copy(selectedCategoryIds = selectedIds, uncategorizedSelected = includeUncategorized)
            else p
        }
        persistPrinters(updated)
    }

    fun setPrinterAutoPrint(id: String, enabled: Boolean) {
        val updated = getPrinters().map { p ->
            if (p.id == id) p.copy(autoPrintEnabled = enabled) else p
        }
        persistPrinters(updated)
    }

    private fun persistPrinters(list: List<SavedPrinter>) {
        try {
            val json = gson.toJson(list)
            prefs.edit().putString(KEY_PRINTERS_JSON, json).apply()
        } catch (_: Exception) {}
    }

    companion object {
        private const val KEY_PRINTERS_JSON = "printers_json"
        private const val KEY_DEFAULT_PRINTER_ID = "default_printer_id"
        // Legacy keys retained for migration if needed (unused in new path)
        private const val KEY_NAME = "printer_name"
        private const val KEY_HOST = "printer_host"
        private const val KEY_PORT = "printer_port"
        private const val KEY_PAPER_WIDTH_MM = "printer_paper_mm"
        private const val KEY_DOTS_PER_MM = "printer_dots_per_mm"
        private const val KEY_INIT_HEX = "printer_init_hex"
        private const val KEY_CUTTER_HEX = "printer_cutter_hex"
        private const val KEY_DRAWER_HEX = "printer_drawer_hex"
        const val DEFAULT_PORT = 9100
        const val DEFAULT_PAPER_WIDTH_MM = 80
        const val DEFAULT_DOTS_PER_MM = 8
    }
}
