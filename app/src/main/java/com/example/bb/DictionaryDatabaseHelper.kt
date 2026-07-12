package com.example.bb

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.FileOutputStream

class DictionaryDatabaseHelper(context: Context) {

    private val db: SQLiteDatabase

    init {
        val dbFile = File(context.filesDir, "dictionary.db")
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
            "SELECT word, definition, part_of_speech, example FROM entries WHERE word LIKE ? ORDER BY word ASC LIMIT 30",
            arrayOf("$query%")
        )
        cursor.use {
            while (it.moveToNext()) {
                results.add(
                    DictionaryEntry(
                        word = it.getString(0),
                        definition = it.getString(1),
                        partOfSpeech = it.getString(2),
                        example = it.getString(3)
                    )
                )
            }
        }
        return results
    }

    private fun copyFromAssets(context: Context, destFile: File) {
        context.assets.open("dictionary.db").use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }
    }
}