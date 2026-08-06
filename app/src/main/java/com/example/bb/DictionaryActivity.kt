package com.example.bb

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class DictionaryActivity : BaseActivity() {

    private var dbHelper: DictionaryDatabaseHelper? = null
    private lateinit var adapter: DictionaryAdapter
    private lateinit var etSearch: EditText
    private lateinit var rvResults: RecyclerView
    private lateinit var txtEmptyState: TextView

    private val databaseExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val searchGeneration = AtomicInteger(0)
    private var pendingSearch: Runnable? = null
    @Volatile private var destroyed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dictionary)

        findViewById<ImageView>(R.id.btnDictionaryBack).setOnClickListener { finish() }

        etSearch = findViewById(R.id.etSearchWord)
        rvResults = findViewById(R.id.rvResults)
        txtEmptyState = findViewById(R.id.txtEmptyState)

        adapter = DictionaryAdapter(emptyList())
        rvResults.layoutManager = LinearLayoutManager(this)
        rvResults.adapter = adapter
        rvResults.setHasFixedSize(true)

        etSearch.isEnabled = false
        txtEmptyState.text = "در حال آماده‌سازی دیکشنری..."
        txtEmptyState.visibility = View.VISIBLE
        rvResults.visibility = View.GONE

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                scheduleSearch(s?.toString().orEmpty())
            }

            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) = Unit

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) = Unit
        })

        initializeDictionary()
    }

    private fun initializeDictionary() {
        databaseExecutor.execute {
            val result = runCatching {
                DictionaryDatabaseHelper(applicationContext)
            }

            mainHandler.post {
                if (destroyed) {
                    result.getOrNull()?.close()
                    return@post
                }

                result.onSuccess { helper ->
                    dbHelper = helper
                    etSearch.isEnabled = true
                    txtEmptyState.text = "برای جستجو، لغت مورد نظر را تایپ کنید"
                    txtEmptyState.visibility = View.VISIBLE
                }.onFailure {
                    etSearch.isEnabled = false
                    txtEmptyState.text =
                        "دیکشنری آماده نشد. برنامه را یک‌بار ببندید و دوباره باز کنید."
                    txtEmptyState.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun scheduleSearch(rawQuery: String) {
        pendingSearch?.let(mainHandler::removeCallbacks)
        val query = rawQuery.trim()
        val generation = searchGeneration.incrementAndGet()

        if (query.isBlank()) {
            showInitialState()
            return
        }

        if (dbHelper == null) {
            txtEmptyState.text = "دیکشنری هنوز در حال آماده‌سازی است..."
            txtEmptyState.visibility = View.VISIBLE
            rvResults.visibility = View.GONE
            return
        }

        val task = Runnable {
            txtEmptyState.text = "در حال جستجو..."
            txtEmptyState.visibility = View.VISIBLE

            databaseExecutor.execute {
                val result = runCatching {
                    dbHelper?.search(query).orEmpty()
                }

                mainHandler.post {
                    if (destroyed || generation != searchGeneration.get()) {
                        return@post
                    }

                    result.onSuccess(::showResults)
                        .onFailure {
                            adapter.updateData(emptyList())
                            rvResults.visibility = View.GONE
                            txtEmptyState.text = "جستجو انجام نشد. دوباره تلاش کنید."
                            txtEmptyState.visibility = View.VISIBLE
                        }
                }
            }
        }

        pendingSearch = task
        mainHandler.postDelayed(task, SEARCH_DEBOUNCE_MS)
    }

    private fun showInitialState() {
        adapter.updateData(emptyList())
        rvResults.visibility = View.GONE
        txtEmptyState.text = "برای جستجو، لغت مورد نظر را تایپ کنید"
        txtEmptyState.visibility = View.VISIBLE
    }

    private fun showResults(results: List<DictionaryEntry>) {
        adapter.updateData(results)
        val empty = results.isEmpty()
        rvResults.visibility = if (empty) View.GONE else View.VISIBLE
        txtEmptyState.visibility = if (empty) View.VISIBLE else View.GONE
        if (empty) {
            txtEmptyState.text = "نتیجه‌ای یافت نشد"
        }
    }

    override fun onDestroy() {
        destroyed = true
        searchGeneration.incrementAndGet()
        pendingSearch?.let(mainHandler::removeCallbacks)
        mainHandler.removeCallbacksAndMessages(null)

        databaseExecutor.execute {
            dbHelper?.close()
            dbHelper = null
        }
        databaseExecutor.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 280L
    }
}
