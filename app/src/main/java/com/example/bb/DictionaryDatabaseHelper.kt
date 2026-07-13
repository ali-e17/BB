package com.example.bb

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class DictionaryDatabaseHelper(context: Context) {

    companion object {
        private const val DATABASE_NAME = "dictionary.db"
        private const val TEMP_DATABASE_NAME = "dictionary.db.tmp"
        private val REQUIRED_COLUMNS = setOf("word", "word_lower", "wordtype", "definition")
    }

    private val db: SQLiteDatabase

    init {
        val dbFile = prepareDatabase(context)
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

    private fun prepareDatabase(context: Context): File {
        val dbFile = File(context.filesDir, DATABASE_NAME)
        if (dbFile.exists() && hasCurrentSchema(dbFile)) {
            return dbFile
        }

        val tempFile = File(context.filesDir, TEMP_DATABASE_NAME)
        if (tempFile.exists() && !tempFile.delete()) {
            error("Could not remove the temporary dictionary database")
        }

        copyFromAssets(context, tempFile)
        if (!hasCurrentSchema(tempFile)) {
            tempFile.delete()
            error("The bundled dictionary database has an invalid schema")
        }

        if (dbFile.exists() && !dbFile.delete()) {
            tempFile.delete()
            error("Could not replace the old dictionary database")
        }
        if (!tempFile.renameTo(dbFile)) {
            tempFile.delete()
            error("Could not install the dictionary database")
        }

        return dbFile
    }

    private fun hasCurrentSchema(file: File): Boolean {
        return runCatching {
            val database = SQLiteDatabase.openDatabase(
                file.path, null, SQLiteDatabase.OPEN_READONLY
            )
            try {
                database.rawQuery("PRAGMA table_info(entries)", null).use { cursor ->
                    val nameColumnIndex = cursor.getColumnIndexOrThrow("name")
                    val columns = mutableSetOf<String>()
                    while (cursor.moveToNext()) {
                        columns.add(cursor.getString(nameColumnIndex))
                    }
                    columns.containsAll(REQUIRED_COLUMNS)
                }
            } finally {
                database.close()
            }
        }.getOrDefault(false)
    }

    private fun copyFromAssets(context: Context, destFile: File) {
        context.assets.open(DATABASE_NAME).use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }
    }
}
