package com.example.bb

import android.widget.Toast
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import java.util.Locale

class DictionaryActivity : AppCompatActivity() {

    private lateinit var dbHelper: DictionaryDatabaseHelper
    private lateinit var adapter: DictionaryAdapter
    private lateinit var etSearch: EditText
    private lateinit var rvResults: RecyclerView
    private lateinit var txtEmptyState: TextView
    private lateinit var btnRandomWord: MaterialButton

    // 🌟 لیست سیاه محلی جهت مچ‌گیری از کلمات نامناسب احتمالی دیتابیس
    private val blacklistedWords = setOf(
        "fuck", "shit", "bitch", "asshole", "crap", "dick", "pussy", "bastard", "slut"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dictionary)

        findViewById<ImageView>(R.id.btnDictionaryBack).setOnClickListener { finish() }

        dbHelper = DictionaryDatabaseHelper(this)

        etSearch = findViewById(R.id.etSearchWord)
        rvResults = findViewById(R.id.rvResults)
        txtEmptyState = findViewById(R.id.txtEmptyState)
        btnRandomWord = findViewById(R.id.btnRandomWord)

        adapter = DictionaryAdapter(emptyList())
        rvResults.layoutManager = LinearLayoutManager(this)
        rvResults.adapter = adapter

        // 🌟 منطق دکمه کلمه تصادفی
        btnRandomWord.setOnClickListener {
            var foundSafeWord = false
            var attempts = 0
            var randomEntry: DictionaryEntry? = null

            // تلاش مجدد در پس‌زمینه در صورت برخورد با کلمات لیست سیاه (حداکثر 10 بار)
            while (!foundSafeWord && attempts < 10) {
                randomEntry = dbHelper.getRandomWord()
                val wordLower = randomEntry?.word?.lowercase(Locale.ROOT).orEmpty().trim()

                if (randomEntry != null && !blacklistedWords.contains(wordLower)) {
                    foundSafeWord = true
                }
                attempts++
            }

            if (randomEntry != null && foundSafeWord) {
                // پاک کردن متن باکس سرچ برای جلوگیری از فعال شدن ناخواسته TextWatcher
                etSearch.text = null

                // نمایش اطلاعات کلمه رندوم در همان کارت نمایش سرچ عادی
                adapter.updateData(listOf(randomEntry))
                txtEmptyState.visibility = View.GONE
                rvResults.visibility = View.VISIBLE
            } else {
                Toast.makeText(this, "لغتی پیدا نشد، دوباره تلاش کنید", Toast.LENGTH_SHORT).show()
            }
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()
                if (query.isNotEmpty()) {
                    val results = dbHelper.search(query)
                    adapter.updateData(results)
                    txtEmptyState.visibility = if (results.isEmpty()) View.VISIBLE else View.GONE
                    if (results.isEmpty()) {
                        txtEmptyState.text = "نتیجه‌ای یافت نشد"
                    }
                    rvResults.visibility = if (results.isEmpty()) View.GONE else View.VISIBLE
                } else {
                    adapter.updateData(emptyList())
                    rvResults.visibility = View.GONE
                    txtEmptyState.text = "برای جستجو، لغت مورد نظر را تایپ کنید"
                    txtEmptyState.visibility = View.VISIBLE
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    override fun onDestroy() {
        if (::dbHelper.isInitialized) {
            dbHelper.close()
        }
        super.onDestroy()
    }
}