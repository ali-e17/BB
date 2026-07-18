package com.example.bb

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView

data class AttendanceRecord(
    val student: StudentModel,
    var status: AttendanceStatus = AttendanceStatus.PRESENT,
    var delayMinutes: Int = 0,
    val isLocked: Boolean = false
)

class AttendanceAdapter(
    private val records: List<AttendanceRecord>,
    private val onStatusChanged: () -> Unit
) : RecyclerView.Adapter<AttendanceAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val avatar: ImageView = view.findViewById(R.id.ivStudentAvatar)
        val name: TextView = view.findViewById(R.id.tvStudentName)
        val code: TextView = view.findViewById(R.id.tvStudentId)
        val present: TextView = view.findViewById(R.id.btnPresent)
        val late: TextView = view.findViewById(R.id.btnLate)
        val absent: TextView = view.findViewById(R.id.btnAbsent)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_attendance_row, parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val record = records[position]
        holder.name.text = record.student.name
        holder.code.text = "کد: ${record.student.studentCode}"

        // 🌟 اختصاص عکس رندوم ثابت برای لیست حضور و غیاب
        val context = holder.itemView.context
        val randomNum = (Math.abs(record.student.id.hashCode()) % 9) + 1
        val fallback = "avatar_student_$randomNum"

        val avatar = record.student.avatarName?.takeIf { it.isNotBlank() } ?: fallback
        val resId = context.resources.getIdentifier(avatar, "drawable", context.packageName)
        if (resId != 0) {
            holder.avatar.setImageResource(resId)
        } else {
            holder.avatar.setImageResource(R.drawable.avatar_student_1)
        }

        fun render() {
            arrayOf(holder.present, holder.late, holder.absent).forEach {
                it.background = null
                it.setTextColor(Color.parseColor("#94A3B8"))
                it.alpha = if (record.isLocked) 0.7f else 1f
            }
            holder.late.text = if (record.status == AttendanceStatus.LATE && record.delayMinutes > 0) "تأخیر ${record.delayMinutes}د" else "تأخیر"
            val selected = GradientDrawable().apply { cornerRadius = 24f }
            when (record.status) {
                AttendanceStatus.PRESENT -> { selected.setColor(Color.parseColor("#10B981")); holder.present.background = selected; holder.present.setTextColor(Color.WHITE) }
                AttendanceStatus.LATE -> { selected.setColor(Color.parseColor("#F59E0B")); holder.late.background = selected; holder.late.setTextColor(Color.WHITE) }
                AttendanceStatus.ABSENT -> { selected.setColor(Color.parseColor("#EF4444")); holder.absent.background = selected; holder.absent.setTextColor(Color.WHITE) }
            }
        }

        render()
        holder.present.setOnClickListener {
            if (record.isLocked) return@setOnClickListener
            record.status = AttendanceStatus.PRESENT; record.delayMinutes = 0; render(); onStatusChanged()
        }
        holder.absent.setOnClickListener {
            if (record.isLocked) return@setOnClickListener
            record.status = AttendanceStatus.ABSENT; record.delayMinutes = 0; render(); onStatusChanged()
        }
        holder.late.setOnClickListener {
            if (record.isLocked) return@setOnClickListener
            val input = EditText(holder.itemView.context).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                hint = "دقیقه تأخیر"
                if (record.delayMinutes > 0) setText(record.delayMinutes.toString())
            }
            AlertDialog.Builder(holder.itemView.context)
                .setTitle("مدت تأخیر ${record.student.name}")
                .setView(input)
                .setPositiveButton("ثبت") { _, _ ->
                    val minutes = input.text.toString().toIntOrNull() ?: 0
                    if (minutes > 0) {
                        record.status = AttendanceStatus.LATE
                        record.delayMinutes = minutes
                        render(); onStatusChanged()
                    }
                }.setNegativeButton("انصراف", null).show()
        }
    }

    override fun getItemCount() = records.size
}