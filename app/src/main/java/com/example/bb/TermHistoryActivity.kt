package com.example.bb

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class TermHistoryActivity : AppCompatActivity() {
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
                    empty.text = body?.let { "سابقه‌ای پیدا نشد" } ?: "دریافت سابقه انجام نشد"
                }
            }
            override fun onFailure(call: Call<TermHistoryResponse>, t: Throwable) {
                progress.visibility = View.GONE
                empty.visibility = View.VISIBLE
                empty.text = "ارتباط با سرور برقرار نشد"
            }
        })
    }

    private class HistoryAdapter(
        private val items: List<TermHistoryItem>,
        private val onReport: (TermHistoryItem) -> Unit
    ) : RecyclerView.Adapter<HistoryAdapter.Holder>() {
        class Holder(v: View) : RecyclerView.ViewHolder(v) {
            val title: TextView = v.findViewById(R.id.txtHistoryTitle)
            val status: TextView = v.findViewById(R.id.txtHistoryStatus) // 🌟 اضافه شدن آیدی وضعیت
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

            // 🌟 اعمال فونت قرمز برای کلاس‌های جاری و سبز برای پایان‌یافته
            if (item.status.equals("ACTIVE", ignoreCase = true)) {
                h.status.text = "جاری"
                h.status.setTextColor(Color.parseColor("#EF4444")) // قرمز
            } else if (item.status.equals("COMPLETED", ignoreCase = true)) {
                h.status.text = "پایان‌یافته"
                h.status.setTextColor(Color.parseColor("#10B981")) // سبز
            } else {
                h.status.text = "نامشخص"
                h.status.setTextColor(Color.GRAY)
            }

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

            h.result.visibility = if (item.reportCardId.isNullOrBlank()) View.GONE else View.VISIBLE
            h.result.text = buildString {
                append("مشاهده کارنامه")
                item.totalScore?.let { append("  •  نمره ${formatScore(it)}") }
                if (item.starCount > 0) append("  •  ${"★".repeat(item.starCount)}")
            }
            h.result.setOnClickListener { onReport(item) }
        }

        override fun getItemCount() = items.size
        private fun formatScore(value: Double) = if (value % 1.0 == 0.0) value.toInt().toString() else "%.2f".format(value)
    }

    companion object {
        const val EXTRA_ROLE = "HISTORY_ROLE"
        const val EXTRA_ID = "HISTORY_ID"
    }
}