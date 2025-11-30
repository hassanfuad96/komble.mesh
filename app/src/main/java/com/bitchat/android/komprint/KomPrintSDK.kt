package com.bitchat.android.komprint

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONObject
import java.net.URLEncoder

object KomPrintSDK {
    fun triggerPrint(context: Context, payload: JSONObject) {
        val encoded = URLEncoder.encode(payload.toString(), "UTF-8")
        val url = "komprint://print?payload=${encoded}"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
