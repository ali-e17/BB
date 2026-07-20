package com.example.bb

import android.content.Context
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

class TeacherHistoryActivity : AppCompatActivity() {

    private val historyClasses = arrayListOf<ClassModel>()
    private lateinit var rvHistory: RecyclerView
    private lateinit var tvEmptyState: TextView
    private lateinit var progressLoading: View
    private lateinit var currentTeacherPhone: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_teacher_history)

        findViewById<ImageView>(R.id.btnHistoryBack).setOnClickListener { finish() }

        rvHistory = findViewById(R.id.rvTeacherHistory)
        tvEmptyState = findViewById(R.id.tvHistoryEmpty)
        progressLoading = findViewById(R.id.progressHistory)

        rvHistory.layoutManager = LinearLayoutManager(this)
        rvHistory.adapter = HistoryAdapter()

        val prefs = getSharedPreferences("LocalAppPrefs", Context.MODE_PRIVATE)
        currentTeacherPhone = prefs.getString("CURRENT_USERNAME", "").orEmpty()

        fetchHistory()
    }

    private fun fetchHistory() {
        progressLoading.visibility = View.VISIBLE
        RetrofitClient.instance.getClasses().enqueue(object : Callback<List<ClassModel>> {
            override fun onResponse(call: Call<List<ClassModel>>, response: Response<List<ClassModel>>) {
                progressLoading.visibility = View.GONE
                if (response.isSuccessful) {
                    val allClasses = response.body().orEmpty()
                    historyClasses.clear()

                    // 🌟 جلوگیری از کرش: فیلتر امن و جاگذاری رشته خالی به جای مقادیر نال هنگام سورت
                    historyClasses.addAll(
                        allClasses.filter {
                            it.teacherPhone == currentTeacherPhone &&
                                    (it.status == ClassStatus.COMPLETED || it.status?.name == "COMPLETED")
                        }.sortedByDescending { it.completedAt ?: "" }
                    )

                    rvHistory.adapter?.notifyDataSetChanged()
                    tvEmptyState.visibility = if (historyClasses.isEmpty()) View.VISIBLE else View.GONE
                }
            }

            override fun onFailure(call: Call<List<ClassModel>>, t: Throwable) {
                progressLoading.visibility = View.GONE
                historyClasses.clear()

                // 🌟 ایمن‌سازی بخش آفلاین دیتابیس
                historyClasses.addAll(
                    AppDatabase.getAllClasses(true).filter {
                        it.teacherPhone == currentTeacherPhone &&
                                (it.status == ClassStatus.COMPLETED || it.status?.name == "COMPLETED")
                    }.sortedByDescending { it.completedAt ?: "" }
                )

                rvHistory.adapter?.notifyDataSetChanged()
                tvEmptyState.visibility = if (historyClasses.isEmpty()) View.VISIBLE else View.GONE
            }
        })
    }

    private inner class HistoryAdapter : RecyclerView.Adapter<HistoryViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_class_history, parent, false)
            return HistoryViewHolder(view)
        }

        override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
            val model = historyClasses[position]
            holder.tvName.text = model.className ?: "بدون نام"
            holder.tvSchedule.text = model.classTime ?: "بدون زمان"

            // 🌟 مدیریت امن نمایش تاریخ‌ها برای جلوگیری از خطای ناگهانی Null
            val startDate = model.createdAt ?: "نامشخص"
            val endDate = model.completedAt ?: "نامشخص"
            holder.tvDates.text = "شروع: $startDate | پایان: $endDate"
        }

        override fun getItemCount(): Int = historyClasses.size
    }

    private class HistoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.txtHistoryClassName)
        val tvSchedule: TextView = view.findViewById(R.id.txtHistoryClassTime)
        val tvDates: TextView = view.findViewById(R.id.txtHistoryClassDates)
    }
}