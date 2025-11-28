package com.bitchat.android.printer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Minimal raw JetDirect (port 9100) text printing.
 * Uses HP PJL wrapper to ensure page eject on office printers (e.g., DeskJet/LaserJet).
 * Not all printers require PJL; this path is for non‑ESC/POS devices.
 */
class JetDirectTextPrinterClient {
    suspend fun sendTextPage(host: String, port: Int = DEFAULT_PORT, text: String): Boolean = withContext(Dispatchers.IO) {
        // Try PJL-wrapped PCL job first, then plain raw as fallback.
        if (tryPjlPclJob(host, port, text)) return@withContext true
        return@withContext tryRawText(host, port, text)
    }

    private fun writePjlJobStart(out: OutputStream) {
        // UEL begin, PJL enter PCL (common default)
        out.write(byteArrayOf(0x1B))
        out.write("%-12345X".toByteArray(Charsets.US_ASCII))
        out.write("@PJL JOB NAME=BITCHAT\r\n".toByteArray(Charsets.US_ASCII))
        out.write("@PJL ENTER LANGUAGE = PCL\r\n".toByteArray(Charsets.US_ASCII))
    }

    private fun writePjlJobEnd(out: OutputStream) {
        out.write("\r\n@PJL EOJ\r\n".toByteArray(Charsets.US_ASCII))
        out.write(byteArrayOf(0x1B))
        out.write("%-12345X".toByteArray(Charsets.US_ASCII))
    }

    private fun tryPjlPclJob(host: String, port: Int, text: String): Boolean {
        return try {
            Socket().use { socket ->
                socket.soTimeout = 5000
                socket.connect(InetSocketAddress(host, port), 5000)
                socket.getOutputStream().use { out ->
                    // PJL header
                    writePjlJobStart(out)
                    // PCL reset to ensure text mode
                    out.write(byteArrayOf(0x1B, 0x45)) // ESC E (reset)
                    // Send text with CRLF and form feed
                    out.write((text.replace("\n", "\r\n") + "\r\n").toByteArray(Charsets.US_ASCII))
                    out.write(byteArrayOf(0x0C)) // FF
                    // PJL end
                    writePjlJobEnd(out)
                    out.flush()
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun tryRawText(host: String, port: Int, text: String): Boolean {
        return try {
            Socket().use { socket ->
                socket.soTimeout = 5000
                socket.connect(InetSocketAddress(host, port), 5000)
                socket.getOutputStream().use { out ->
                    out.write((text.replace("\n", "\r\n") + "\r\n").toByteArray(Charsets.US_ASCII))
                    out.write(byteArrayOf(0x0C))
                    out.flush()
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    companion object { const val DEFAULT_PORT = 9100 }
}