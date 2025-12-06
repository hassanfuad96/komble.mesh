package com.bitchat.android.komprint

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import com.bitchat.android.db.AppDatabaseHelper

class KomPrintActivity : Activity() {
    /**
     * Deeplink entry activity for KomPrint. Validates the payload and dispatches
     * to KomPrintProgressActivity. Also captures the originating app package from
     * the referrer (android-app://<package>) and passes it along so the progress
     * screen can automatically return to the originating app when done.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val data: Uri? = intent?.data
        val payloadParam = data?.getQueryParameter("payload")
        if (payloadParam.isNullOrBlank()) {
            sendResult(false, KomPrintErrors.PRINT_ERR_MISSING_FIELD, "missing payload")
            finish()
            return
        }
        val decoded = try { Uri.decode(payloadParam) } catch (_: Exception) { payloadParam }
        try {
            KomPrintValidator.parse(decoded)
        } catch (e: IllegalArgumentException) {
            sendResult(false, e.message ?: KomPrintErrors.PRINT_ERR_BAD_SCHEMA, "validation failed")
            finish()
            return
        } catch (t: Throwable) {
            sendResult(false, KomPrintErrors.PRINT_ERR_INVALID_JSON, "json error")
            finish()
            return
        }
        try {
            AppDatabaseHelper(this).insertPrintLog(
                com.bitchat.android.db.PrintLog(
                    printerId = null,
                    host = "deeplink",
                    port = 0,
                    label = "queued",
                    type = "komprint_in",
                    success = true
                )
            )
        } catch (_: Exception) {}

        // Try to capture the originating app package from the referrer (android-app://<package>)
        val returnPkg: String? = try {
            val ref = referrer
            if (ref != null && ref.scheme == "android-app") ref.host else null
        } catch (_: Throwable) { null }

        val intent = Intent(this, KomPrintProgressActivity::class.java).apply {
            putExtra("payload", decoded)
            if (!returnPkg.isNullOrBlank()) putExtra("return_pkg", returnPkg)
        }
        startActivity(intent)
        finish()
    }

    /**
     * Broadcasts the deeplink handling result for listening apps.
     */
    private fun sendResult(success: Boolean, error: String?, message: String?) {
        val intent = Intent("komprint.PRINT_RESULT").apply {
            putExtra("success", success)
            putExtra("error", error)
            putExtra("message", message)
        }
        try {
            sendBroadcast(intent)
        } catch (t: Throwable) {
            Log.e("KomPrintActivity", "broadcast failed", t)
        }
    }
}
