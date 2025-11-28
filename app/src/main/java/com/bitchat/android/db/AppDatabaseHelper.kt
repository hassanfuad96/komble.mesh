package com.bitchat.android.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class PrintLog(
    val printerId: String?,
    val host: String,
    val port: Int,
    val label: String?,
    val type: String, // escpos_test | text_test | ipp_text
    val success: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

class AppDatabaseHelper constructor(context: Context) : SQLiteOpenHelper(
    context.applicationContext, DB_NAME, null, DB_VERSION
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS print_logs (
                _id INTEGER PRIMARY KEY AUTOINCREMENT,
                printer_id TEXT,
                host TEXT NOT NULL,
                port INTEGER NOT NULL,
                label TEXT,
                type TEXT NOT NULL,
                success INTEGER NOT NULL,
                timestamp INTEGER NOT NULL
            );
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_print_logs_time ON print_logs(timestamp DESC)")

        // Orders table for exposing via ContentProvider
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS orders (
                _id INTEGER PRIMARY KEY AUTOINCREMENT,
                order_id TEXT NOT NULL UNIQUE,
                id TEXT,
                created_at TEXT,
                delivery_method TEXT,
                user_id TEXT,
                status TEXT
            );
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_orders_created ON orders(created_at DESC)")

        // Order items table
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS order_items (
                _id INTEGER PRIMARY KEY AUTOINCREMENT,
                order_id TEXT NOT NULL,
                item_id TEXT,
                name TEXT,
                quantity INTEGER,
                variant TEXT,
                category_id TEXT,
                prepared INTEGER DEFAULT 0
            );
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_order_items_oid ON order_items(order_id)")

        // Outbox for orders to be sent to @phone
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS orders_outbox (
                order_id TEXT PRIMARY KEY,
                payload TEXT NOT NULL,
                created_at INTEGER NOT NULL
            );
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_orders_outbox_time ON orders_outbox(created_at DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 1) {
            onCreate(db)
        }
        if (oldVersion < 2) {
            // Ensure new tables exist when upgrading from older versions
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS orders (
                    _id INTEGER PRIMARY KEY AUTOINCREMENT,
                    order_id TEXT NOT NULL UNIQUE,
                    id TEXT,
                    created_at TEXT,
                    delivery_method TEXT,
                    user_id TEXT,
                    status TEXT
                );
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_orders_created ON orders(created_at DESC)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS order_items (
                    _id INTEGER PRIMARY KEY AUTOINCREMENT,
                    order_id TEXT NOT NULL,
                    item_id TEXT,
                    name TEXT,
                    quantity INTEGER,
                    variant TEXT,
                    category_id TEXT
                );
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_order_items_oid ON order_items(order_id)")
        }
        if (oldVersion < 3) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS orders_outbox (
                    order_id TEXT PRIMARY KEY,
                    payload TEXT NOT NULL,
                    created_at INTEGER NOT NULL
                );
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_orders_outbox_time ON orders_outbox(created_at DESC)")
        }
        if (oldVersion < 4) {
            // Add prepared flag to order_items
            try {
                db.execSQL("ALTER TABLE order_items ADD COLUMN prepared INTEGER DEFAULT 0")
            } catch (_: Exception) {
                // Column may already exist; ignore
            }
        }
    }

    fun insertPrintLog(log: PrintLog) {
        val values = ContentValues().apply {
            put("printer_id", log.printerId)
            put("host", log.host)
            put("port", log.port)
            put("label", log.label)
            put("type", log.type)
            put("success", if (log.success) 1 else 0)
            put("timestamp", log.timestamp)
        }
        writableDatabase.insert("print_logs", null, values)
    }

    fun queryRecentLogs(limit: Int = 50): List<PrintLog> {
        val result = mutableListOf<PrintLog>()
        val c: Cursor = readableDatabase.query(
            "print_logs",
            arrayOf("printer_id", "host", "port", "label", "type", "success", "timestamp"),
            null,
            null,
            null,
            null,
            "timestamp DESC",
            limit.toString()
        )
        c.use {
            val idxPrinterId = it.getColumnIndex("printer_id")
            val idxHost = it.getColumnIndex("host")
            val idxPort = it.getColumnIndex("port")
            val idxLabel = it.getColumnIndex("label")
            val idxType = it.getColumnIndex("type")
            val idxSuccess = it.getColumnIndex("success")
            val idxTimestamp = it.getColumnIndex("timestamp")
            while (it.moveToNext()) {
                result.add(
                    PrintLog(
                        printerId = if (idxPrinterId >= 0) it.getString(idxPrinterId) else null,
                        host = it.getString(idxHost),
                        port = it.getInt(idxPort),
                        label = if (idxLabel >= 0) it.getString(idxLabel) else null,
                        type = it.getString(idxType),
                        success = it.getInt(idxSuccess) == 1,
                        timestamp = it.getLong(idxTimestamp)
                    )
                )
            }
        }
        return result
    }

    fun clearLogs() {
        writableDatabase.delete("print_logs", null, null)
    }

    // Minimal helpers to upsert orders and replace items
    fun upsertOrder(
        orderId: String,
        id: String?,
        createdAt: String?,
        deliveryMethod: String?,
        userId: String?,
        status: String?
    ) {
        val values = ContentValues().apply {
            put("order_id", orderId)
            put("id", id)
            put("created_at", createdAt)
            put("delivery_method", deliveryMethod)
            put("user_id", userId)
            put("status", status)
        }
        writableDatabase.insertWithOnConflict(
            "orders",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun orderExists(orderId: String): Boolean {
        val c = readableDatabase.rawQuery(
            "SELECT 1 FROM orders WHERE order_id = ? LIMIT 1",
            arrayOf(orderId)
        )
        c.use { return it.moveToFirst() }
    }

    fun replaceOrderItems(
        orderId: String,
        items: List<OrderItem>
    ) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("order_items", "order_id=?", arrayOf(orderId))
            for (item in items) {
                val v = ContentValues().apply {
                    put("order_id", orderId)
                    put("item_id", item.itemId)
                    put("name", item.name)
                    put("quantity", item.quantity)
                    put("variant", item.variant)
                    put("category_id", item.categoryId)
                    put("prepared", if (item.prepared) 1 else 0)
                }
                db.insert("order_items", null, v)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun enqueueOrderOutbox(orderId: String, payload: String) {
        val values = ContentValues().apply {
            put("order_id", orderId)
            put("payload", payload)
            put("created_at", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict(
            "orders_outbox",
            null,
            values,
            SQLiteDatabase.CONFLICT_IGNORE
        )
    }

    fun getOrdersOutbox(limit: Int = 50): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        val c = readableDatabase.query(
            "orders_outbox",
            arrayOf("order_id", "payload"),
            null,
            null,
            null,
            null,
            "created_at DESC",
            limit.toString()
        )
        c.use {
            val idxOrderId = it.getColumnIndex("order_id")
            val idxPayload = it.getColumnIndex("payload")
            while (it.moveToNext()) {
                result.add(it.getString(idxOrderId) to it.getString(idxPayload))
            }
        }
        return result
    }

    fun deleteOrdersOutbox(orderId: String) {
        writableDatabase.delete("orders_outbox", "order_id=?", arrayOf(orderId))
    }

    data class OrderItem(
        val itemId: String?,
        val name: String,
        val quantity: Int,
        val variant: String?,
        val categoryId: String?,
        val prepared: Boolean = false
    )

    companion object {
        private const val DB_NAME = "komble.db"
        private const val DB_VERSION = 4
        @Volatile private var instance: AppDatabaseHelper? = null
        @JvmStatic
        fun getInstance(context: Context): AppDatabaseHelper = instance ?: synchronized(this) {
            instance ?: AppDatabaseHelper(context).also { instance = it }
        }
    }
    fun getOrderItems(orderId: String): List<OrderItem> {
        val result = mutableListOf<OrderItem>()
        val c = readableDatabase.query(
            "order_items",
            arrayOf("item_id", "name", "quantity", "variant", "category_id", "prepared"),
            "order_id=?",
            arrayOf(orderId),
            null,
            null,
            null
        )
        c.use {
            val idxItemId = it.getColumnIndex("item_id")
            val idxName = it.getColumnIndex("name")
            val idxQty = it.getColumnIndex("quantity")
            val idxVariant = it.getColumnIndex("variant")
            val idxCat = it.getColumnIndex("category_id")
            val idxPrepared = it.getColumnIndex("prepared")
            while (it.moveToNext()) {
                result.add(
                    OrderItem(
                        itemId = if (idxItemId >= 0) it.getString(idxItemId) else null,
                        name = it.getString(idxName),
                        quantity = it.getInt(idxQty),
                        variant = if (idxVariant >= 0) it.getString(idxVariant) else null,
                        categoryId = if (idxCat >= 0) it.getString(idxCat) else null,
                        prepared = (if (idxPrepared >= 0) it.getInt(idxPrepared) else 0) == 1
                    )
                )
            }
        }
        return result
    }

    fun getLatestOrderIds(limit: Int = 5): List<String> {
        val ids = mutableListOf<String>()
        val c = readableDatabase.query(
            "orders",
            arrayOf("order_id"),
            null,
            null,
            null,
            null,
            "_id DESC",
            limit.toString()
        )
        c.use {
            val idx = it.getColumnIndex("order_id")
            while (it.moveToNext()) {
                val id = if (idx >= 0) it.getString(idx) else null
                if (!id.isNullOrBlank()) ids.add(id)
            }
        }
        return ids
    }

    fun updateOrderStatus(orderId: String, status: String) {
        val db = writableDatabase
        val v = ContentValues().apply { put("status", status) }
        // Try to update existing row; if none affected, insert minimal header
        val affected = db.update("orders", v, "order_id=?", arrayOf(orderId))
        if (affected == 0) {
            val insertVals = ContentValues().apply {
                put("order_id", orderId)
                put("status", status)
            }
            db.insertWithOnConflict("orders", null, insertVals, SQLiteDatabase.CONFLICT_IGNORE)
        }
    }

    data class OrderRow(
        val orderId: String,
        val createdAt: String?,
        val status: String?
    )

    fun getOrdersByStatus(status: String, limit: Int = 100): List<OrderRow> {
        val result = mutableListOf<OrderRow>()
        val c = readableDatabase.query(
            "orders",
            arrayOf("order_id", "created_at", "status"),
            "status = ?",
            arrayOf(status),
            null,
            null,
            "_id DESC",
            limit.toString()
        )
        c.use {
            val idxId = it.getColumnIndex("order_id")
            val idxCreated = it.getColumnIndex("created_at")
            val idxStatus = it.getColumnIndex("status")
            while (it.moveToNext()) {
                val id = if (idxId >= 0) it.getString(idxId) else null
                if (!id.isNullOrBlank()) {
                    result.add(
                        OrderRow(
                            orderId = id,
                            createdAt = if (idxCreated >= 0) it.getString(idxCreated) else null,
                            status = if (idxStatus >= 0) it.getString(idxStatus) else null
                        )
                    )
                }
            }
        }
        return result
    }

    fun getOrdersByStatuses(statuses: List<String>, limit: Int = 100): List<OrderRow> {
        if (statuses.isEmpty()) return emptyList()
        val placeholders = statuses.joinToString(",") { "?" }
        val result = mutableListOf<OrderRow>()
        val c = readableDatabase.query(
            "orders",
            arrayOf("order_id", "created_at", "status"),
            "status IN ($placeholders)",
            statuses.toTypedArray(),
            null,
            null,
            "_id DESC",
            limit.toString()
        )
        c.use {
            val idxId = it.getColumnIndex("order_id")
            val idxCreated = it.getColumnIndex("created_at")
            val idxStatus = it.getColumnIndex("status")
            while (it.moveToNext()) {
                val id = if (idxId >= 0) it.getString(idxId) else null
                if (!id.isNullOrBlank()) {
                    result.add(
                        OrderRow(
                            orderId = id,
                            createdAt = if (idxCreated >= 0) it.getString(idxCreated) else null,
                            status = if (idxStatus >= 0) it.getString(idxStatus) else null
                        )
                    )
                }
            }
        }
        return result
    }

    fun updateProductPrepared(orderId: String, itemId: String?, prepared: Boolean) {
        if (itemId.isNullOrEmpty()) return
        val v = ContentValues().apply { put("prepared", if (prepared) 1 else 0) }
        writableDatabase.update(
            "order_items",
            v,
            "order_id=? AND item_id=?",
            arrayOf(orderId, itemId)
        )
    }

    fun updatePreparedForCategories(orderId: String, categoryIds: Set<Int>, includeUncategorized: Boolean) {
        val conditions = mutableListOf<String>()
        val args = mutableListOf<String>()
        // Base condition: order_id matches
        val baseCond = "order_id=?"
        args.add(orderId)
        // Category-based conditions
        if (categoryIds.isNotEmpty()) {
            val placeholders = categoryIds.joinToString(",") { "?" }
            conditions.add("category_id IN ($placeholders)")
            args.addAll(categoryIds.map { it.toString() })
        }
        if (includeUncategorized) {
            conditions.add("category_id IS NULL")
        }
        if (conditions.isEmpty()) return
        val where = "$baseCond AND (" + conditions.joinToString(" OR ") + ")"
        val v = ContentValues().apply { put("prepared", 1) }
        writableDatabase.update("order_items", v, where, args.toTypedArray())
    }

    fun areAllItemsPrepared(orderId: String): Boolean {
        val c = readableDatabase.rawQuery(
            "SELECT COUNT(1) FROM order_items WHERE order_id = ? AND (prepared IS NULL OR prepared = 0)",
            arrayOf(orderId)
        )
        c.use {
            return if (it.moveToFirst()) it.getInt(0) == 0 else false
        }
    }
}