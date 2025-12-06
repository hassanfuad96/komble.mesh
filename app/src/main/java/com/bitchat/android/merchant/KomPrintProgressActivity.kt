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
    /**
     * Shows a simple progress UI while KomPrint processes the queued job.
     * Automatically returns to the originating app package if provided via
     * the "return_pkg" extra (from the deeplink referrer) once printing is done
     * or when a terminal error occurs (e.g., missing main printer).
     */
    private val handler = Handler(Looper.getMainLooper())
    private var jobId: Long = -1L
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val payload = intent?.getStringExtra("payload")
        val returnPkg = intent?.getStringExtra("return_pkg")
        if (payload.isNullOrBlank()) {
            finish()
            maybeReturnToCaller(returnPkg)
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
            handler.postDelayed({
                finish()
                maybeReturnToCaller(returnPkg)
            }, 800)
            return
        }

        jobId = AppDatabaseHelper(this).enqueuePrintJobWithId(payload)
        LocalPrintQueue.process(this)
        pollUntilDone(returnPkg)
    }

    /**
     * Polls the local DB for job completion, then auto-finishes and returns
     * to the originating app if available.
     */
    private fun pollUntilDone(returnPkg: String?) {
        handler.postDelayed({
            val done = AppDatabaseHelper(this).isPrintJobDone(jobId)
            if (done) {
                statusText.text = "Done"
                finish()
                maybeReturnToCaller(returnPkg)
            } else {
                pollUntilDone(returnPkg)
            }
        }, 500)
    }

    /**
     * Attempts to return to the originating app if the package name is known.
     * Falls back to simply finishing the activity if launch fails.
     */
    private fun maybeReturnToCaller(returnPkg: String?) {
        if (returnPkg.isNullOrBlank()) return
        try {
            val launch = packageManager.getLaunchIntentForPackage(returnPkg)
            if (launch != null) {
                launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launch)
            }
        } catch (_: Throwable) {
            // ignore; best-effort return
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}