package com.example.forwarder

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class PendingMessage(
    val id: Long,
    val source: String,
    val sender: String,
    val content: String,
    val timestamp: Long
)

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "forwarder_queue.db"
        private const val DATABASE_VERSION = 1

        private const val TABLE_MESSAGES = "pending_messages"
        private const val COLUMN_ID = "id"
        private const val COLUMN_SOURCE = "source"
        private const val COLUMN_SENDER = "sender"
        private const val COLUMN_CONTENT = "content"
        private const val COLUMN_TIMESTAMP = "timestamp"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = ("CREATE TABLE " + TABLE_MESSAGES + "("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COLUMN_SOURCE + " TEXT,"
                + COLUMN_SENDER + " TEXT,"
                + COLUMN_CONTENT + " TEXT,"
                + COLUMN_TIMESTAMP + " INTEGER" + ")")
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_MESSAGES")
        onCreate(db)
    }

    fun insertPendingMessage(source: String, sender: String, content: String): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_SOURCE, source)
            put(COLUMN_SENDER, sender)
            put(COLUMN_CONTENT, content)
            put(COLUMN_TIMESTAMP, System.currentTimeMillis())
        }
        val id = db.insert(TABLE_MESSAGES, null, values)
        db.close()
        return id
    }

    fun getAllPendingMessages(): List<PendingMessage> {
        val list = mutableListOf<PendingMessage>()
        val selectQuery = "SELECT * FROM $TABLE_MESSAGES ORDER BY $COLUMN_ID ASC"
        val db = readableDatabase
        val cursor = db.rawQuery(selectQuery, null)

        if (cursor.moveToFirst()) {
            do {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID))
                val source = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SOURCE))
                val sender = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SENDER))
                val content = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CONTENT))
                val timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP))

                list.add(PendingMessage(id, source, sender, content, timestamp))
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return list
    }

    fun deletePendingMessage(id: Long) {
        val db = writableDatabase
        db.delete(TABLE_MESSAGES, "$COLUMN_ID = ?", arrayOf(id.toString()))
        db.close()
    }

    fun getPendingCount(): Int {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_MESSAGES", null)
        var count = 0
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0)
        }
        cursor.close()
        db.close()
        return count
    }
}
