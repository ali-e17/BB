package com.example.bb

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class DictionaryDatabaseHelper(context: Context) {

    companion object {
        private const val DATABASE_NAME = "dictionary.db"
    }

    private val db: SQLiteDatabase

    init {
        val dbFile = File(context.filesDir, DATABASE_NAME)
        if (!dbFile.exists()) {
            copyFromAssets(context, dbFile)
        }
        db = SQLiteDatabase.openDatabase(
            dbFile.path, null, SQLiteDatabase.OPEN_READONLY
        )
    }

    fun search(query: String): List<DictionaryEntry> {
        val results = mutableListOf<DictionaryEntry>()
        val cursor = db.rawQuery(
            "SELECT word, definition, wordtype FROM entries WHERE word_lower LIKE ? ORDER BY word_lower ASC LIMIT 30",
            arrayOf("${query.lowercase(Locale.ROOT)}%")
        )
        cursor.use {
            while (it.moveToNext()) {
                results.add(
                    DictionaryEntry(
                        word = it.getString(0),
                        definition = it.getString(1),
                        partOfSpeech = it.getString(2)
                    )
                )
            }
        }
        return results
    }

    fun close() {
        db.close()
    }

    private fun copyFromAssets(context: Context, destFile: File) {
        context.assets.open(DATABASE_NAME).use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }
    }
}
