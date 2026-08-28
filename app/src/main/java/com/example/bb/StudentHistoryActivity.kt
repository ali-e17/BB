package com.example.bb

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class StudentHistoryActivity : BaseActivity() {

    private lateinit var rvHistory: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var progressLoading: View

    private val historyClasses = arrayListOf<ClassModel>()
    private lateinit var adapter: HistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_history)

        findViewById<ImageView>(R.id.btnHistoryBack).setOnClickListener { finish() }

        rvHistory = findViewById(R.id.rvStudentHistory)
        tvEmpty = findViewById(R.id.tvHistoryEmpty)
        progressLoading = findViewById(R.id.progressHistory)

        rvHistory.layoutManager = LinearLayoutManager(this)
        adapter = HistoryAdapter()
        rvHistory.adapter = adapter

        fetchHistory()
    }

    private fun fetchHistory() {
        progressLoading.visibility = View.VISIBLE

        // 🌟 در اینجا فرض بر این است که متد getClasses تمام کلاس‌های دانش‌آموز (اعم از جاری و پایان‌یافته) را برمی‌گرداند.
        // اگر API جداگانه‌ای برای سوابق داری، آن را در رتروفیت جایگزین کن.
        RetrofitClient.instance.getClasses().enqueue(object : Callback<List<ClassModel>> {
            override fun onResponse(call: Call<List<ClassModel>>, response: Response<List<ClassModel>>) {
                progressLoading.visibility = View.GONE
                if (response.isSuccessful) {
                    val serverClasses = response.body().orEmpty()
                    renderClasses(serverClasses)
                } else {
                    AppToast.error(
                        this@StudentHistoryActivity,
                        ApiErrorParser.userMessage(
                            response,
                            "دریافت سوابق کلاس‌های دانش‌آموز کامل نشد"
                        )
                    )
                }
            }

            override fun onFailure(call: Call<List<ClassModel>>, t: Throwable) {
                progressLoading.visibility = View.GONE
                AppToast.error(
                    this@StudentHistoryActivity,
                    ApiErrorParser.networkMessage(t, "دریافت سوابق کلاس‌های دانش‌آموز")
                )
            }
        })
    }

    private fun renderClasses(classes: List<ClassModel>) {
        historyClasses.clear()
        // مرتب‌سازی به این شکل که اول کلاس‌های جاری نشان داده شوند، سپس پایان‌یافته‌ها
        historyClasses.addAll(classes.sortedByDescending { it.status == ClassStatus.ACTIVE })

        adapter.notifyDataSetChanged()
        tvEmpty.visibility = if (historyClasses.isEmpty()) View.VISIBLE else View.GONE
    }

    // ==========================================
    // آداپتور برای نمایش لیست و تنظیم رنگ‌ها
    // ==========================================
    private inner class HistoryAdapter : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvHistoryClassName)
            val tvStatus: TextView = view.findViewById(R.id.tvHistoryStatus)
            val tvBook: TextView = view.findViewById(R.id.tvHistoryBook)
            val tvTerm: TextView = view.findViewById(R.id.tvHistoryTerm)
            val tvSchedule: TextView = view.findViewById(R.id.tvHistorySchedule)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_student_history, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val model = historyClasses[position]

            holder.tvName.text = model.className
            holder.tvBook.text = "کتاب: ${model.bookName.ifBlank { "نامشخص" }}"

            val termText = listOf(model.termSeason, model.termYear).filter { it.isNotBlank() }.joinToString(" ")
            holder.tvTerm.text = "ترم: ${termText.ifBlank { "نامشخص" }}"

            holder.tvSchedule.text = "برگزاری: ${model.classTime}"

            // 🌟 منطق رنگ‌آمیزی وضعیت کلاس
            if (model.status == ClassStatus.ACTIVE || model.status.toString() == "ACTIVE") {
                holder.tvStatus.text = "جاری"
                // رنگ قرمز جذاب برای کلاس‌های جاری
                holder.tvStatus.setTextColor(Color.parseColor("#EF4444"))
            } else if (model.status == ClassStatus.COMPLETED || model.status.toString() == "COMPLETED") {
                holder.tvStatus.text = "پایان یافته"
                // رنگ سبز برای کلاس‌های پایان‌یافته
                holder.tvStatus.setTextColor(Color.parseColor("#10B981"))
            } else {
                holder.tvStatus.text = "نامشخص"
                holder.tvStatus.setTextColor(Color.GRAY)
            }
        }

        override fun getItemCount(): Int = historyClasses.size
    }
}