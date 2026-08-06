package com.example.bb

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Locale

class TermHistoryActivity : BaseActivity() {
    private lateinit var recycler: RecyclerView
    private lateinit var empty: TextView
    private lateinit var progress: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_term_history)
        findViewById<ImageView>(R.id.btnHistoryBack).setOnClickListener { finish() }
        recycler = findViewById(R.id.rvTermHistory)
        empty = findViewById(R.id.txtHistoryEmpty)
        progress = findViewById(R.id.progressTermHistory)
        recycler.layoutManager = LinearLayoutManager(this)
        load()
    }

    private fun load() {
        progress.visibility = View.VISIBLE
        val prefs = getSharedPreferences("LocalAppPrefs", Context.MODE_PRIVATE)
        val role = intent.getStringExtra(EXTRA_ROLE) ?: prefs.getString("CURRENT_USER_ROLE", "STUDENT").orEmpty()
        val id = intent.getStringExtra(EXTRA_ID) ?: prefs.getString("CURRENT_USER_ID", "").orEmpty()
        RetrofitClient.instance.getTermHistory(role, id).enqueue(object : Callback<TermHistoryResponse> {
            override fun onResponse(call: Call<TermHistoryResponse>, response: Response<TermHistoryResponse>) {
                progress.visibility = View.GONE
                val body = response.body()
                val apiError = ApiErrorParser.parse(response)
                if (response.isSuccessful && body?.status == "success") {
                    recycler.adapter = HistoryAdapter(body.items) { item ->
                        item.reportCardId?.takeIf { it.isNotBlank() }?.let { cardId ->
                            startActivity(Intent(this@TermHistoryActivity, ReportCardViewActivity::class.java)
                                .putExtra(ReportCardViewActivity.EXTRA_REPORT_CARD_ID, cardId))
                        }
                    }
                    empty.visibility = if (body.items.isEmpty()) View.VISIBLE else View.GONE
                } else {
                    empty.visibility = View.VISIBLE
                    empty.text = ApiErrorParser.userMessage(response, apiError, "دریافت سابقه تحصیلی انجام نشد")
                }
            }
            override fun onFailure(call: Call<TermHistoryResponse>, t: Throwable) {
                progress.visibility = View.GONE
                empty.visibility = View.VISIBLE
                empty.text = ApiErrorParser.networkMessage(t, "دریافت سابقه تحصیلی")
            }
        })
    }

    private class HistoryAdapter(
        private val items: List<TermHistoryItem>,
        private val onReport: (TermHistoryItem) -> Unit
    ) : RecyclerView.Adapter<HistoryAdapter.Holder>() {
        class Holder(v: View) : RecyclerView.ViewHolder(v) {
            val title: TextView = v.findViewById(R.id.txtHistoryTitle)
            val meta: TextView = v.findViewById(R.id.txtHistoryMeta)
            val stats: TextView = v.findViewById(R.id.txtHistoryStats)
            val result: TextView = v.findViewById(R.id.txtHistoryResult)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_term_history, parent, false)
        )
        override fun onBindViewHolder(h: Holder, position: Int) {
            val item = items[position]
            h.title.text = item.className.ifBlank { "کلاس" }
            val term = listOfNotNull(item.termSeason?.takeIf(String::isNotBlank), item.termYear?.takeIf(String::isNotBlank)).joinToString(" ")
            h.meta.text = listOfNotNull(
                item.classCode?.takeIf(String::isNotBlank)?.let { "کد: $it" },
                item.bookName?.takeIf(String::isNotBlank)?.let { "کتاب: $it" },
                item.classLevel?.takeIf(String::isNotBlank)?.let { "سطح: $it" },
                term.takeIf(String::isNotBlank),
                item.teacherName?.takeIf(String::isNotBlank)?.let { "استاد: $it" }
            ).joinToString("  •  ").ifBlank { "اطلاعات تکمیلی ثبت نشده" }
            h.stats.text = if (item.studentCount > 0 || item.publishedReportCount > 0) {
                "${item.studentCount} زبان‌آموز  •  ${item.publishedReportCount} کارنامه منتشرشده"
            } else {
                "غیبت: ${item.absentCount}  •  تأخیر: ${item.lateCount}"
            }
            val hasPublishedCard = !item.reportCardId.isNullOrBlank()
            h.result.visibility = View.VISIBLE
            h.result.isEnabled = hasPublishedCard
            h.result.alpha = if (hasPublishedCard) 1f else 0.65f
            h.result.text = if (hasPublishedCard) {
                buildString {
                    append("مشاهده کارنامه")
                    item.totalScore?.let { append("  •  نمره ${formatScore(it)}") }
                    if (item.starCount > 0) append("  •  ${"★".repeat(item.starCount)}")
                }
            } else {
                "کارنامه این ترم منتشر نشده است"
            }
            h.result.setOnClickListener {
                if (hasPublishedCard) onReport(item)
            }
        }
        override fun getItemCount() = items.size
        private fun formatScore(value: Double) = if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.2f", value)
    }

    companion object {
        const val EXTRA_ROLE = "HISTORY_ROLE"
        const val EXTRA_ID = "HISTORY_ID"
    }
}
