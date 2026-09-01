package ir.bayanebartar.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class TermHistoryActivity : BaseActivity() {
    private lateinit var recycler: RecyclerView
    private lateinit var empty: View
    private lateinit var emptyText: TextView
    private lateinit var progress: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_term_history)

        findViewById<ImageView>(R.id.btnHistoryBack).setOnClickListener { finish() }
        recycler = findViewById(R.id.rvTermHistory)
        empty = findViewById(R.id.historyEmptyState)
        emptyText = findViewById(R.id.txtHistoryEmpty)
        progress = findViewById(R.id.progressTermHistory)

        recycler.layoutManager = LinearLayoutManager(this)
        load()
    }

    private fun load() {
        progress.visibility = View.VISIBLE
        recycler.visibility = View.INVISIBLE
        empty.visibility = View.GONE

        val prefs = getSharedPreferences("LocalAppPrefs", Context.MODE_PRIVATE)
        val role = intent.getStringExtra(EXTRA_ROLE)
            ?: prefs.getString("CURRENT_USER_ROLE", "STUDENT").orEmpty()
        val id = intent.getStringExtra(EXTRA_ID)
            ?: prefs.getString("CURRENT_USER_ID", "").orEmpty()

        RetrofitClient.instance.getTermHistory(role, id)
            .enqueue(object : Callback<TermHistoryResponse> {
                override fun onResponse(
                    call: Call<TermHistoryResponse>,
                    response: Response<TermHistoryResponse>
                ) {
                    progress.visibility = View.GONE
                    val body = response.body()
                    val apiError = ApiErrorParser.parse(response)

                    if (response.isSuccessful && body?.status == "success") {
                        val orderedItems = body.items.sortedWith(
                            compareByDescending<TermHistoryItem> { it.isCurrent }
                                .thenByDescending { parseTermYear(it.termYear) }
                                .thenByDescending { seasonRank(it.termSeason) }
                                .thenByDescending { it.enrolledAt.orEmpty() }
                        )

                        recycler.adapter = HistoryAdapter(orderedItems) { item ->
                            item.reportCardId
                                ?.takeIf { it.isNotBlank() }
                                ?.let { cardId ->
                                    startActivity(
                                        Intent(
                                            this@TermHistoryActivity,
                                            ReportCardViewActivity::class.java
                                        ).putExtra(
                                            ReportCardViewActivity.EXTRA_REPORT_CARD_ID,
                                            cardId
                                        )
                                    )
                                }
                        }

                        val hasItems = orderedItems.isNotEmpty()
                        recycler.visibility = if (hasItems) View.VISIBLE else View.INVISIBLE
                        empty.visibility = if (hasItems) View.GONE else View.VISIBLE
                        if (!hasItems) {
                            emptyText.text = "هنوز کلاس یا ترمی در سوابق شما ثبت نشده است."
                        }
                    } else {
                        recycler.visibility = View.INVISIBLE
                        empty.visibility = View.VISIBLE
                        val message = ApiErrorParser.userMessage(
                            response,
                            apiError,
                            "دریافت کارنامه‌ها و سوابق تحصیلی کامل نشد"
                        )
                        emptyText.text = message
                        AppToast.error(this@TermHistoryActivity, message)
                    }
                }

                override fun onFailure(call: Call<TermHistoryResponse>, t: Throwable) {
                    progress.visibility = View.GONE
                    recycler.visibility = View.INVISIBLE
                    empty.visibility = View.VISIBLE
                    val message = ApiErrorParser.networkMessage(
                        t,
                        "دریافت کارنامه‌ها و سوابق تحصیلی"
                    )
                    emptyText.text = message
                    AppToast.error(this@TermHistoryActivity, message)
                }
            })
    }

    private class HistoryAdapter(
        private val items: List<TermHistoryItem>,
        private val onReport: (TermHistoryItem) -> Unit
    ) : RecyclerView.Adapter<HistoryAdapter.Holder>() {

        class Holder(v: View) : RecyclerView.ViewHolder(v) {
            val title: TextView = v.findViewById(R.id.txtHistoryTitle)
            val status: TextView = v.findViewById(R.id.txtHistoryStatus)
            val meta: TextView = v.findViewById(R.id.txtHistoryMeta)
            val stats: TextView = v.findViewById(R.id.txtHistoryStats)
            val result: TextView = v.findViewById(R.id.txtHistoryResult)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            return Holder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_term_history, parent, false)
            )
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = items[position]
            holder.title.text = item.className.ifBlank { "کلاس" }

            holder.status.text = if (item.isCurrent) "ترم جاری" else "ترم گذشته"
            holder.status.setBackgroundResource(
                if (item.isCurrent) {
                    R.drawable.bg_unified_status_active
                } else {
                    R.drawable.bg_unified_status_inactive
                }
            )

            val term = listOfNotNull(
                item.termSeason?.takeIf(String::isNotBlank),
                item.termYear?.takeIf(String::isNotBlank)
            ).joinToString(" ")

            holder.meta.text = listOfNotNull(
                term.takeIf(String::isNotBlank)?.let { "ترم: $it" },
                item.classCode?.takeIf(String::isNotBlank)?.let { "کد کلاس: $it" },
                item.classLevel?.takeIf(String::isNotBlank)?.let { "سطح: $it" },
                item.bookName?.takeIf(String::isNotBlank)?.let { "کتاب: $it" },
                item.teacherName?.takeIf(String::isNotBlank)?.let { "استاد: $it" }
            ).joinToString("  •  ").ifBlank { "اطلاعات تکمیلی این ترم ثبت نشده است" }

            holder.stats.text = "غیبت: ${item.absentCount}  •  تأخیر: ${item.lateCount}"

            val hasPublishedCard = !item.reportCardId.isNullOrBlank()
            holder.result.visibility = View.VISIBLE
            holder.result.isEnabled = true
            holder.result.alpha = if (hasPublishedCard) 1f else 0.72f
            holder.result.text = if (hasPublishedCard) {
                buildString {
                    append("مشاهده کارنامه")
                    item.totalScore?.let { append("  •  نمره ${formatScore(it)}") }
                    if (item.starCount > 0) {
                        append("  •  ${"★".repeat(item.starCount.coerceIn(1, 5))}")
                    }
                }
            } else if (item.isCurrent) {
                "کارنامه این ترم تاکنون منتشر نشده است"
            } else {
                "برای این ترم کارنامه‌ای منتشر نشده است"
            }

            holder.result.setOnClickListener {
                if (hasPublishedCard) {
                    onReport(item)
                } else {
                    AppToast.info(
                        holder.itemView.context,
                        if (item.isCurrent) {
                            "کارنامه این ترم تاکنون منتشر نشده است"
                        } else {
                            "برای این ترم کارنامه‌ای منتشر نشده است"
                        }
                    )
                }
            }
        }

        override fun getItemCount(): Int = items.size

        private fun formatScore(value: Double): String =
            UiTextFormatter.formatPersianDecimalSlash(value)
    }

    companion object {
        const val EXTRA_ROLE = "HISTORY_ROLE"
        const val EXTRA_ID = "HISTORY_ID"

        private fun parseTermYear(value: String?): Int {
            if (value.isNullOrBlank()) return Int.MIN_VALUE
            val normalized = buildString {
                value.forEach { ch ->
                    append(
                        when (ch) {
                            '۰', '٠' -> '0'
                            '۱', '١' -> '1'
                            '۲', '٢' -> '2'
                            '۳', '٣' -> '3'
                            '۴', '٤' -> '4'
                            '۵', '٥' -> '5'
                            '۶', '٦' -> '6'
                            '۷', '٧' -> '7'
                            '۸', '٨' -> '8'
                            '۹', '٩' -> '9'
                            else -> ch
                        }
                    )
                }
            }
            return normalized.filter(Char::isDigit).toIntOrNull() ?: Int.MIN_VALUE
        }

        private fun seasonRank(value: String?): Int {
            return when (value?.trim()) {
                "زمستان" -> 4
                "پاییز" -> 3
                "تابستان" -> 2
                "بهار" -> 1
                else -> 0
            }
        }
    }
}
