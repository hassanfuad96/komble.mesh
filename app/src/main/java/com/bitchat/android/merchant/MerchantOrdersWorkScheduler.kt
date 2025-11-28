package com.bitchat.android.merchant

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Schedules/cancels OrdersSyncWorker based on merchant authentication state at runtime.
 * - Schedules on login with network constraints and user/token input
 * - Cancels on logout
 */
object MerchantOrdersWorkScheduler {
    private const val UNIQUE_NAME = "merchant_orders_sync"
    private var initialized = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun init(context: Context) {
        if (initialized) return
        initialized = true

        val appContext = context.applicationContext
        val authManager = MerchantAuthManager.getInstance(appContext)

        // Initial scheduling based on current state
        if (authManager.hasValidToken() && authManager.getCurrentUser() != null) {
            schedule(appContext, authManager)
            // Ensure foreground service keeps polling active
            try { MerchantOrdersForegroundService.start(appContext) } catch (_: Throwable) { }
        } else {
            cancel(appContext)
            try { MerchantOrdersForegroundService.stop(appContext) } catch (_: Throwable) { }
        }

        // Observe login state changes and react accordingly
        scope.launch {
            authManager.isLoggedIn.collect { loggedIn ->
                if (loggedIn && authManager.hasValidToken() && authManager.getCurrentUser() != null) {
                    schedule(appContext, authManager)
                    try { MerchantOrdersForegroundService.start(appContext) } catch (_: Throwable) { }
                } else {
                    cancel(appContext)
                    try { MerchantOrdersForegroundService.stop(appContext) } catch (_: Throwable) { }
                }
            }
        }
    }

    private fun schedule(context: Context, authManager: MerchantAuthManager) {
        val user = authManager.getCurrentUser() ?: return
        val authHeader = authManager.getAuthorizationHeader()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val input = Data.Builder()
            .putInt(OrdersSyncWorker.KEY_USER_ID, user.id)
            .putString(OrdersSyncWorker.KEY_AUTH_HEADER, authHeader)
            .build()

        val request = PeriodicWorkRequestBuilder<OrdersSyncWorker>(
            15, java.util.concurrent.TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setInputData(input)
            .addTag(UNIQUE_NAME)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    private fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
    }
}