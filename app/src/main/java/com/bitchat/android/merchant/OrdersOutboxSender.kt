package com.bitchat.android.merchant

import android.content.Context
import android.util.Log
import com.bitchat.android.db.AppDatabaseHelper
import com.bitchat.android.mesh.BluetoothMeshService

/**
 * Flush orders outbox rows via MessageRouter to the @phone peer when available.
 */
object OrdersOutboxSender {
    private const val TAG = "OrdersOutboxSender"

    fun flush(context: Context, mesh: BluetoothMeshService, targetNickname: String = "@phone") {
        val db = AppDatabaseHelper(context)
        val pending = db.getOrdersOutbox(limit = 100)
        if (pending.isEmpty()) return
        val peerId = resolvePeerIdByNickname(mesh, targetNickname)
        if (peerId == null) {
            Log.d(TAG, "No peerID for $targetNickname; skipping outbox flush")
            return
        }
        val router = try { com.bitchat.android.services.MessageRouter.getInstance(context, mesh) } catch (e: Exception) {
            Log.e(TAG, "MessageRouter unavailable: ${e.message}")
            return
        }
        pending.forEach { (orderId, payload) ->
            try {
                // messageID tags the order so duplicates are ignored upstream
                router.sendPrivate(payload, peerId, targetNickname, messageID = "order:$orderId")
                // Once delegated to router, remove from outbox to avoid re-queuing
                db.deleteOrdersOutbox(orderId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send outbox order $orderId", e)
            }
        }
    }

    private fun resolvePeerIdByNickname(mesh: BluetoothMeshService, nickname: String): String? {
        return try {
            mesh.getPeerNicknames().entries.firstOrNull { it.value == nickname }?.key
        } catch (_: Exception) { null }
    }
}
