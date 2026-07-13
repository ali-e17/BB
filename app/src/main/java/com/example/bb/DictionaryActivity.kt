package com.example.bb

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

class DictionaryActivity : AppCompatActivity() {

    private lateinit var dbHelper: DictionaryDatabaseHelper
    private lateinit var adapter: DictionaryAdapter
    private lateinit var etSearch: EditText
    private lateinit var rvResults: RecyclerView
    private lateinit var txtEmptyState: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dictionary)

        findViewById<ImageView>(R.id.btnDictionaryBack).setOnClickListener { finish() }

        dbHelper = DictionaryDatabaseHelper(this)

        etSearch = findViewById(R.id.etSearchWord)
        rvResults = findViewById(R.id.rvResults)
        txtEmptyState = findViewById(R.id.txtEmptyState)

        adapter = DictionaryAdapter(emptyList())
        rvResults.layoutManager = LinearLayoutManager(this)
        rvResults.adapter = adapter

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
