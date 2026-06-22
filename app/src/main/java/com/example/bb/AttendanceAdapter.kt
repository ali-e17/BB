package com.example.bb

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

enum class AttendanceStatus { PRESENT, LATE, ABSENT }
data class AttendanceRecord(val student: Student, var status: AttendanceStatus = AttendanceStatus.ABSENT)

class AttendanceAdapter(
    private val records: List<AttendanceRecord>,
    private val onStatusChanged: () -> Unit
) : RecyclerView.Adapter<AttendanceAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivAvatar: ImageView = view.findViewById(R.id.ivStudentAvatar)
        val tvName: TextView = view.findViewById(R.id.tvStudentName)
        val tvId: TextView = view.findViewById(R.id.tvStudentId)
        val btnPresent: TextView = view.findViewById(R.id.btnPresent)
        val btnLate: TextView = view.findViewById(R.id.btnLate)
        val btnAbsent: TextView = view.findViewById(R.id.btnAbsent)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_attendance_row, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val record = records[position]

        holder.tvName.text = record.student.fullName
        holder.tvId.text = "کد: ${record.student.studentCode}"
        holder.ivAvatar.setImageResource(record.student.avatarResId)

        // تابع کمکی برای استایل دادن به دکمه انتخاب شده
        fun updateButtonsUI() {
            // ریست کردن همه دکمه‌ها به حالت خاکستری و ترنسپرنت
            arrayOf(holder.btnPresent, holder.btnLate, holder.btnAbsent).forEach {
                it.background = null
                it.setTextColor(Color.parseColor("#94A3B8")) // رنگ sub_text
            }

            // ساخت پس‌زمینه گوشه‌گرد برای آیتم انتخاب شده
            val selectedBg = GradientDrawable().apply {
                cornerRadius = 24f
            }

            when (record.status) {
                AttendanceStatus.PRESENT -> {
                    selectedBg.setColor(Color.parseColor("#10B981")) // سبز
                    holder.btnPresent.background = selectedBg
                    holder.btnPresent.setTextColor(Color.WHITE)
                }
                AttendanceStatus.LATE -> {
                    selectedBg.setColor(Color.parseColor("#F59E0B")) // زرد/نارنجی
                    holder.btnLate.background = selectedBg
                    holder.btnLate.setTextColor(Color.WHITE)
                }
                AttendanceStatus.ABSENT -> {
                    selectedBg.setColor(Color.parseColor("#EF4444")) // قرمز
                    holder.btnAbsent.background = selectedBg
                    holder.btnAbsent.setTextColor(Color.WHITE)
                }
            }
            onStatusChanged()
        }

        // مقداردهی اولیه
        updateButtonsUI()

        // کلیک لیسنرها
        holder.btnPresent.setOnClickListener {
            record.status = AttendanceStatus.PRESENT
            updateButtonsUI()
        }
        holder.btnLate.setOnClickListener {
            record.status = AttendanceStatus.LATE
            updateButtonsUI()
        }
        holder.btnAbsent.setOnClickListener {
            record.status = AttendanceStatus.ABSENT
            updateButtonsUI()
        }
    }

    override fun getItemCount() = records.size
}