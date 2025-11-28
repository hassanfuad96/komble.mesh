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

    private fun writeInit(out: OutputStream) {
        out.write(byteArrayOf(0x1B, 0x40)) // ESC @ initialize
    }

    private fun writeCenter(out: OutputStream) {
        out.write(byteArrayOf(0x1B, 0x61, 0x01)) // ESC a 1 center
    }

    private fun writeLeft(out: OutputStream) {
        out.write(byteArrayOf(0x1B, 0x61, 0x00)) // ESC a 0 left
    }

    private fun writeCut(out: OutputStream) {
        out.write(byteArrayOf(0x1D, 0x56, 0x00)) // GS V 0 partial cut
    }

    private fun writeText(out: OutputStream, text: String) {
        out.write(text.toByteArray(Charsets.UTF_8))
    }

    companion object { const val DEFAULT_PORT = 9100 }
}