package com.bitchat.android.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.net.Uri
import com.bitchat.android.db.AppDatabaseHelper

class OrdersProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.komble.mesh.orders"
        private const val PATH_ORDERS = "orders"
        private const val PATH_ORDER_ITEMS = "order_items"

        private const val CODE_ORDERS = 1
        private const val CODE_ORDER_ID = 2
        private const val CODE_ORDER_ITEMS = 3

        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, PATH_ORDERS, CODE_ORDERS)
            addURI(AUTHORITY, "$PATH_ORDERS/*", CODE_ORDER_ID)
            addURI(AUTHORITY, PATH_ORDER_ITEMS, CODE_ORDER_ITEMS)
        }
    }

    private lateinit var dbHelper: AppDatabaseHelper

    override fun onCreate(): Boolean {
        val ctx = context ?: return false
        dbHelper = com.bitchat.android.db.AppDatabaseHelper(ctx)
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val db = dbHelper.readableDatabase
        val code = uriMatcher.match(uri)
        val cursor = when (code) {
            CODE_ORDERS -> {
                db.query(
                    "orders",
                    projection,
                    selection,
                    selectionArgs,
                    null,
                    null,
                    sortOrder
                )
            }
            CODE_ORDER_ID -> {
                val orderId = uri.lastPathSegment
                db.query(
                    "orders",
                    projection,
                    "order_id = ?",
                    arrayOf(orderId),
                    null,
                    null,
                    sortOrder
                )
            }
            CODE_ORDER_ITEMS -> {
                // Expect order_id as query parameter
                val orderId = uri.getQueryParameter("order_id")
                val sel: String?
                val selArgs: Array<String>?
                if (orderId.isNullOrEmpty()) {
                    sel = selection
                    selArgs = selectionArgs as? Array<String>
                } else {
                    sel = "order_id = ?"
                    selArgs = arrayOf(orderId)
                }
                db.query(
                    "order_items",
                    projection,
                    sel,
                    selArgs,
                    null,
                    null,
                    sortOrder
                )
            }
            else -> null
        }
        cursor?.setNotificationUri(context?.contentResolver, uri)
        return cursor
    }

    override fun getType(uri: Uri): String? {
        return when (uriMatcher.match(uri)) {
            CODE_ORDERS -> "vnd.android.cursor.dir/vnd.mesh.order"
            CODE_ORDER_ID -> "vnd.android.cursor.item/vnd.mesh.order"
            CODE_ORDER_ITEMS -> "vnd.android.cursor.dir/vnd.mesh.order_item"
            else -> null
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        throw UnsupportedOperationException("Read-only provider")
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        throw UnsupportedOperationException("Read-only provider")
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        throw UnsupportedOperationException("Read-only provider")
    }
}
