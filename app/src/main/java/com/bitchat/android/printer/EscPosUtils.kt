package com.bitchat.android.printer

object EscPosUtils {
    /** Parses hex values separated by commas or spaces into a ByteArray.
     * Example: "1D,56,42,00" or "1B 40" → corresponding bytes.
     */
    fun parseHexCsv(hex: String?): ByteArray? {
        if (hex.isNullOrBlank()) return null
        val cleaned = hex.trim()
        val parts = cleaned.split(',', ' ', '\n', '\t').filter { it.isNotBlank() }
        return try {
            parts.map { it.trim() }
                .map { it.removePrefix("0x").removePrefix("0X") }
                .map { it.toInt(16).toByte() }
                .toByteArray()
        } catch (_: Exception) {
            null
        }
    }
}