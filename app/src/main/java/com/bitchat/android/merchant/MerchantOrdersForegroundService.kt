package com.bitchat.android.merchant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import android.app.Service
import android.os.IBinder

/**
 * Foreground service to keep 5s polling alive when app is backgrounded.
 * Starts MerchantOrdersPoller and shows a low-priority ongoing notification.
 */
class MerchantOrdersForegroundService : Service() {
    companion object {
        private const val CHANNEL_ID = "orders_polling"
        private const val CHANNEL_NAME = "Orders Polling"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, MerchantOrdersForegroundService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Throwable) { }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, MerchantOrdersForegroundService::class.java))
            } catch (_: Throwable) { }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = try { buildNotification() } catch (_: Throwable) { buildFallbackNotification() }
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, 0)
        } catch (_: Throwable) {
            // Best-effort: if startForeground fails due to notification restrictions, attempt classic API
            try { startForeground(NOTIFICATION_ID, notification) } catch (_: Throwable) {}
        }

        // Start polling
        try { MerchantOrdersPoller.start(applicationContext) } catch (_: Throwable) { }

        return START_STICKY
    }

    override fun onDestroy() {
        try { MerchantOrdersPoller.stop() } catch (_: Throwable) { }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_MIN)
            channel.description = "Keeps merchant order polling active in background"
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(this, com.bitchat.android.MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        val pending = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0) or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setOngoing(true)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("KomBLE.mesh Merchant")
            .setContentText("Polling orders every 5s")
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    private fun buildFallbackNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setOngoing(true)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("KomBLE.mesh Merchant")
            .setContentText("Polling orders")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }
}