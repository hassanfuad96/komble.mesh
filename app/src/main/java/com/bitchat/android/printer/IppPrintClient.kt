package com.bitchat.android.printer

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** Minimal IPP client to send a single-page PDF via Print-Job */
class IppPrintClient {
    suspend fun sendTextPage(
        host: String,
        port: Int = 631,
        queuePath: String = "/ipp/print",
        title: String = "Bitchat Test Page",
        lines: List<String> = listOf(
            "Merchant Connected",
            "Printer: IPP",
            "Time: ${System.currentTimeMillis()}"
        ),
        requestingUser: String = "bitchat"
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // 1) Create a small PDF document in-memory
            val pdfBytes = createSimplePdf(title, lines)

            // 2) Build IPP Print-Job request (2.0) with operation attributes
            val printerUri = "ipp://$host:$port$queuePath"
            val ippRequest = buildIppPrintJobRequest(printerUri, requestingUser, "application/pdf")

            // 3) POST IPP + PDF body to printer
            val totalBytes = ByteArrayOutputStream().apply {
                write(ippRequest)
                write(pdfBytes)
            }.toByteArray()

            val url = URL("http://$host:$port$queuePath")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/ipp")
                setRequestProperty("Accept", "application/ipp")
                doOutput = true
                // Some printers dislike chunked; set fixed content length
                setFixedLengthStreamingMode(totalBytes.size)
                connectTimeout = 7000
                readTimeout = 7000
            }

            conn.outputStream.use { it.write(totalBytes) }

            val code = conn.responseCode
            conn.disconnect()

            // Consider 200 as success; deeper IPP status parsing can be added later
            code == 200
        } catch (_: Exception) {
            false
        }
    }

    private fun createSimplePdf(title: String, lines: List<String>): ByteArray {
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 @ 72dpi
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint().apply {
            isAntiAlias = true
            textSize = 14f
        }
        var y = 40f
        paint.textSize = 18f
        canvas.drawText(title, 40f, y, paint)
        paint.textSize = 14f
        y += 24f
        lines.forEach { line ->
            canvas.drawText(line, 40f, y, paint)
            y += 20f
        }
        doc.finishPage(page)
        val baos = ByteArrayOutputStream()
        doc.writeTo(baos)
        doc.close()
        return baos.toByteArray()
    }

    private fun buildIppPrintJobRequest(printerUri: String, user: String, documentFormat: String): ByteArray {
        val baos = ByteArrayOutputStream()
        val out = DataOutputStream(baos)

        // IPP header
        out.writeByte(0x02) // version major 2
        out.writeByte(0x00) // version minor 0
        out.writeShort(0x0002) // operation: Print-Job
        out.writeInt(0x00000001) // request-id

        // Operation attributes tag
        out.writeByte(0x01)

        // attributes-charset (charset)
        writeAttribute(out, 0x47, "attributes-charset", "utf-8")

        // attributes-natural-language (naturalLanguage)
        writeAttribute(out, 0x48, "attributes-natural-language", "en")

        // printer-uri (uri)
        writeAttribute(out, 0x45, "printer-uri", printerUri)

        // requesting-user-name (nameWithoutLanguage)
        writeAttribute(out, 0x42, "requesting-user-name", user)

        // document-format (mimeMediaType)
        writeAttribute(out, 0x49, "document-format", documentFormat)

        // end-of-attributes tag
        out.writeByte(0x03)

        out.flush()
        return baos.toByteArray()
    }

    private fun writeAttribute(out: DataOutputStream, tag: Int, name: String, value: String) {
        out.writeByte(tag)
        val nameBytes = name.toByteArray()
        out.writeShort(nameBytes.size)
        out.write(nameBytes)
        val valBytes = value.toByteArray()
        out.writeShort(valBytes.size)
        out.write(valBytes)
    }
}