package com.bitchat.android.printer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

class EscPosPrinterClient {
    suspend fun printText(
        host: String,
        port: Int = DEFAULT_PORT,
        content: String,
        initBytes: ByteArray? = null,
        cutterBytes: ByteArray? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
                socket.soTimeout = 5000
                socket.connect(InetSocketAddress(host, port), 5000)
                socket.getOutputStream().use { out ->
                    if (initBytes != null) out.write(initBytes) else writeInit(out)
                    writeLeft(out)
                    writeText(out, content)
                    if (cutterBytes != null) out.write(cutterBytes) else writeCut(out)
                    out.flush()
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }
    suspend fun sendTestReceipt(
        host: String,
        port: Int = DEFAULT_PORT,
        initBytes: ByteArray? = null,
        cutterBytes: ByteArray? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
                socket.soTimeout = 5000
                socket.connect(InetSocketAddress(host, port), 5000)
                socket.getOutputStream().use { out ->
                    if (initBytes != null) out.write(initBytes) else writeInit(out)
                    writeCenter(out)
                    writeText(out, "Bitchat Test Print\n")
                    writeLeft(out)
                    writeText(out, "-----------------------------\n")
                    writeText(out, "Merchant: Connected\n")
                    writeText(out, "Printer: ESC/POS Raw 9100\n")
                    writeText(out, "Time: ${System.currentTimeMillis()}\n")
                    writeText(out, "-----------------------------\n")
                    if (cutterBytes != null) out.write(cutterBytes) else writeCut(out)
                    out.flush()
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Print formatted content using DantSu-style tags, translating them into ESC/POS commands.
     * Supports:
     * - Alignment: [L], [C], [R]
     * - Bold: <b>...</b>
     * - Font size: <font size='normal|wide|tall|big|big-N'>...</font>
     * Other tags (u, img, barcode, qrcode) are ignored/stripped in the raw path.
     */
    suspend fun printRichText(
        host: String,
        port: Int = DEFAULT_PORT,
        content: String,
        initBytes: ByteArray? = null,
        cutterBytes: ByteArray? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
                socket.soTimeout = 5000
                socket.connect(InetSocketAddress(host, port), 5000)
                socket.getOutputStream().use { out ->
                    if (initBytes != null) out.write(initBytes) else writeInit(out)
                    processRichContent(out, content)
                    if (cutterBytes != null) out.write(cutterBytes) else writeCut(out)
                    out.flush()
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun writeInit(out: OutputStream) {
        out.write(byteArrayOf(0x1B, 0x40)) // ESC @ initialize
    }

    private fun writeCenter(out: OutputStream) {
        out.write(byteArrayOf(0x1B, 0x61, 0x01)) // ESC a 1 center
    }

    private fun writeLeft(out: OutputStream) {
        out.write(byteArrayOf(0x1B, 0x61, 0x00)) // ESC a 0 left
    }

    private fun writeRight(out: OutputStream) {
        out.write(byteArrayOf(0x1B, 0x61, 0x02)) // ESC a 2 right
    }

    private fun writeBold(out: OutputStream, enabled: Boolean) {
        out.write(byteArrayOf(0x1B, 0x45, if (enabled) 0x01 else 0x00)) // ESC E n bold on/off
    }

    private fun writeSize(out: OutputStream, value: Int) {
        out.write(byteArrayOf(0x1D, 0x21, value.toByte())) // GS ! n character size
    }

    /**
     * Writes a QR code using ESC/POS GS ( k sequence.
     * - Model 2
     * - Module size clamped to 1..16 (default 6)
     * - Error correction level: 'M' (value 49) by default
     */
    private fun writeQrCode(out: OutputStream, data: String, moduleSize: Int = 6, ecLevel: Int = 49) {
        val mod = moduleSize.coerceIn(1, 16).toByte()
        // Select model: 1D 28 6B 04 00 31 41 02 00 (Model 2)
        out.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x04, 0x00, 0x31, 0x41, 0x02, 0x00))
        // Set module size: 1D 28 6B 03 00 31 43 n
        out.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x43, mod))
        // Set error correction level: 1D 28 6B 03 00 31 45 n (48=L,49=M,50=Q,51=H)
        out.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x45, ecLevel.toByte()))
        val bytes = data.toByteArray(Charsets.UTF_8)
        val len = bytes.size + 3
        val pL = (len and 0xFF).toByte()
        val pH = ((len shr 8) and 0xFF).toByte()
        // Store data: 1D 28 6B pL pH 31 50 30 data
        out.write(byteArrayOf(0x1D, 0x28, 0x6B, pL, pH, 0x31, 0x50, 0x30))
        out.write(bytes)
        // Print QR: 1D 28 6B 03 00 31 51 30
        out.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x51, 0x30))
    }

    private fun writeCut(out: OutputStream) {
        out.write(byteArrayOf(0x1D, 0x56, 0x00)) // GS V 0 partial cut
    }

    private fun writeText(out: OutputStream, text: String) {
        out.write(text.toByteArray(Charsets.UTF_8))
    }

    /**
     * Parses DantSu-style markup and writes corresponding ESC/POS commands and text.
     * Lines containing multiple alignment tags (e.g., [L]..[R]..) will be split into separate lines
     * per segment to approximate the layout while preserving bold/size styling.
     */
    private fun processRichContent(out: OutputStream, content: String) {
        val lines = content.split("\n")
        for (line in lines) {
            if (line.isEmpty()) {
                writeText(out, "\n")
                continue
            }
            var i = 0
            var currentAlign: Char = 'L'
            var wroteAnySegment = false
            while (i < line.length) {
                // Detect alignment tags [L], [C], [R]
                if (line[i] == '[' && i + 2 < line.length && line[i + 2] == ']') {
                    val a = line[i + 1]
                    if (a == 'L' || a == 'C' || a == 'R') {
                        currentAlign = a
                        i += 3
                        continue
                    }
                }
                val start = i
                while (i < line.length && line[i] != '[') i++
                val segment = line.substring(start, i)
                val seg = segment.trim()
                if (seg.isNotEmpty()) {
                    when (currentAlign) {
                        'L' -> writeLeft(out)
                        'C' -> writeCenter(out)
                        'R' -> writeRight(out)
                    }
                    writeSegmentWithStyle(out, seg)
                    // End the segment with a newline to avoid layout conflicts
                    writeText(out, "\n")
                    wroteAnySegment = true
                }
            }
            if (!wroteAnySegment) {
                // No alignment tags found; default to left
                writeLeft(out)
                writeSegmentWithStyle(out, line)
                writeText(out, "\n")
            }
        }
    }

    /**
     * Writes a text segment handling <b> and <font size='...'> tags.
     * Unknown tags are stripped. Styles reset at the end of the segment.
     */
    private fun writeSegmentWithStyle(out: OutputStream, text: String) {
        var j = 0
        var bold = false
        var sizeVal = 0
        while (j < text.length) {
            if (text[j] == '<') {
                // Handle tags
                if (text.startsWith("<b>", j)) {
                    if (!bold) { writeBold(out, true); bold = true }
                    j += 3
                    continue
                }
                if (text.startsWith("</b>", j)) {
                    if (bold) { writeBold(out, false); bold = false }
                    j += 4
                    continue
                }
                if (text.startsWith("<font", j)) {
                    val end = text.indexOf('>', j)
                    if (end != -1) {
                        val tag = text.substring(j, end + 1)
                        val sizeNameMatch = Regex("size=['\\\"]([^'\\\"]+)['\\\"]").find(tag)
                        val sizeName = sizeNameMatch?.groupValues?.get(1)
                        val v = parseFontSizeValue(sizeName)
                        if (v != sizeVal) { writeSize(out, v); sizeVal = v }
                        j = end + 1
                        continue
                    }
                }
                // QR Code tag: <qrcode size='N'>data</qrcode>
                if (text.startsWith("<qrcode", j)) {
                    val end = text.indexOf('>', j)
                    if (end != -1) {
                        val tag = text.substring(j, end + 1)
                        val sizeMatch = Regex("size=['\\\"](\\d+)['\\\"]").find(tag)
                        val module = sizeMatch?.groupValues?.get(1)?.toIntOrNull() ?: 6
                        val close = text.indexOf("</qrcode>", end + 1)
                        val data = if (close != -1) text.substring(end + 1, close) else ""
                        writeQrCode(out, data, moduleSize = module)
                        j = if (close != -1) close + 9 else end + 1
                        continue
                    }
                }
                if (text.startsWith("</font>", j)) {
                    if (sizeVal != 0) { writeSize(out, 0); sizeVal = 0 }
                    j += 7
                    continue
                }
                // Strip underline and image/barcode tags in raw path
                if (text.startsWith("<u", j)) {
                    val end = text.indexOf('>', j)
                    j = if (end != -1) end + 1 else text.length
                    continue
                }
                if (text.startsWith("</u>", j)) { j += 4; continue }
                if (text.startsWith("<img", j)) {
                    val close = text.indexOf("</img>", j)
                    j = if (close != -1) close + 6 else text.length
                    continue
                }
                if (text.startsWith("<barcode", j)) {
                    val close = text.indexOf("</barcode>", j)
                    j = if (close != -1) close + 10 else text.length
                    continue
                }
                // Note: <qrcode> is handled above and thus not stripped here
                // Unrecognized tag: skip '<'
                j++
                continue
            }
            // Emit plain text until next tag
            val startPlain = j
            while (j < text.length && text[j] != '<') j++
            val plain = text.substring(startPlain, j)
            if (plain.isNotEmpty()) writeText(out, plain)
        }
        // Reset styles at end of segment to avoid bleed
        if (bold) writeBold(out, false)
        if (sizeVal != 0) writeSize(out, 0)
    }

    /**
     * Map human-friendly size names to GS ! values.
     * big => 0x11 (2x width, 2x height)
     * tall => 0x01 (1x width, 2x height)
     * wide => 0x10 (2x width, 1x height)
     * big-N => (N+1)x width/height using value ((N) + (N << 4)).
     */
    private fun parseFontSizeValue(sizeName: String?): Int {
        if (sizeName == null) return 0
        return when {
            sizeName.equals("normal", true) -> 0x00
            sizeName.equals("big", true) -> 0x11
            sizeName.equals("tall", true) -> 0x01
            sizeName.equals("wide", true) -> 0x10
            sizeName.startsWith("big-", true) -> {
                val nStr = sizeName.substringAfter("big-", "")
                val n = nStr.toIntOrNull() ?: 1
                val factor = (n + 1).coerceAtMost(8) // ESC/POS supports up to 8
                val h = (factor - 1)
                val w = (factor - 1) shl 4
                w + h
            }
            else -> 0x00
        }
    }

    companion object { const val DEFAULT_PORT = 9100 }
}