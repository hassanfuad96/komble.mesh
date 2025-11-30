package com.bitchat.android.komprint

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.bitchat.android.db.AppDatabaseHelper

class KomPrintProgressActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private var jobId: Long = -1L
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val payload = intent?.getStringExtra("payload")
        if (payload.isNullOrBlank()) {
            finish()
            return
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }
        val progress = ProgressBar(this)
        statusText = TextView(this).apply {
            text = "Printing in progress…"
            textSize = 18f
            gravity = Gravity.CENTER
        }
        layout.addView(progress)
        layout.addView(statusText)
        setContentView(layout)

        val printers = com.bitchat.android.printer.PrinterSettingsManager(this).getPrinters()
        val mains = printers.filter { it.role == "main" }
        if (mains.isEmpty()) {
            statusText.text = "No Main Printer was setup"
            try {
                val intent = android.content.Intent("komprint.PRINT_RESULT").apply {
                    putExtra("success", false)
                    putExtra("error", "PRINT_ERR_MISSING_MAIN_PRINTER")
                    putExtra("message", "No Main Printer was setup")
                }
                sendBroadcast(intent)
            } catch (_: Throwable) {}
            handler.postDelayed({ finish() }, 1500)
            return
        }

        jobId = AppDatabaseHelper(this).enqueuePrintJobWithId(payload)
        LocalPrintQueue.process(this)
        pollUntilDone()
    }

    private fun pollUntilDone() {
        handler.postDelayed({
            val done = AppDatabaseHelper(this).isPrintJobDone(jobId)
            if (done) {
                statusText.text = "Done"
                finish()
            } else {
                pollUntilDone()
            }
        }, 500)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}