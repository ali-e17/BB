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
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class AttendanceAdapter(
    private val records: List<AttendanceRecord>,
    private val onStatusChanged: () -> Unit
) : RecyclerView.Adapter<AttendanceAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.cardAttendanceStudent)
        val avatar: ImageView = view.findViewById(R.id.ivStudentAvatar)
        val name: TextView = view.findViewById(R.id.tvStudentName)
        val code: TextView = view.findViewById(R.id.tvStudentId)
        val stateHint: TextView = view.findViewById(R.id.tvAttendanceStateHint)
        val present: TextView = view.findViewById(R.id.btnPresent)
        val late: TextView = view.findViewById(R.id.btnLate)
        val absent: TextView = view.findViewById(R.id.btnAbsent)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_attendance_row, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val record = records[position]
        val context = holder.itemView.context

        holder.name.text = record.studentName
        holder.code.text = record.studentCode
            .takeIf { it.isNotBlank() }
            ?.let { "کد: $it" }
            ?: "بدون کد دانش‌آموزی"

        val fallbackNumber = (kotlin.math.abs(record.studentId.hashCode()) % 9) + 1
        val fallback = "avatar_student_$fallbackNumber"
        val avatarName = record.avatarName?.takeIf { it.isNotBlank() } ?: fallback
        val avatarRes = context.resources.getIdentifier(avatarName, "drawable", context.packageName)
        holder.avatar.setImageResource(
            if (avatarRes != 0) avatarRes else R.drawable.avatar_student_1
        )

        fun selectedBackground(color: Int): GradientDrawable = GradientDrawable().apply {
            cornerRadius = context.resources.displayMetrics.density * 10f
            setColor(color)
        }

        fun render() {
            val inactiveText = ContextCompat.getColor(context, R.color.sub_text)
            arrayOf(holder.present, holder.late, holder.absent).forEach { button ->
                button.background = null
                button.setTextColor(inactiveText)
                button.alpha = if (record.isLocked) 0.65f else 1f
            }

            holder.late.text = if (
                record.status == AttendanceMarkStatus.LATE && record.delayMinutes > 0
            ) {
                "تأخیر ${record.delayMinutes}د"
            } else {
                "تأخیر"
            }

            when (record.status) {
                AttendanceMarkStatus.UNMARKED -> {
                    holder.stateHint.text = "هنوز بررسی نشده"
                    holder.stateHint.setTextColor(Color.parseColor("#B45309"))
                    holder.card.strokeColor = Color.parseColor("#F59E0B")
                    holder.card.strokeWidth = dp(context, 1)
                }

                AttendanceMarkStatus.PRESENT -> {
                    holder.present.background = selectedBackground(Color.parseColor("#10B981"))
                    holder.present.setTextColor(Color.WHITE)
                    holder.stateHint.text = if (record.isLocked) "ثبت‌شده: حاضر" else "وضعیت: حاضر"
                    holder.stateHint.setTextColor(Color.parseColor("#047857"))
                    holder.card.strokeColor = Color.parseColor("#D1FAE5")
                    holder.card.strokeWidth = dp(context, 1)
                }

                AttendanceMarkStatus.LATE -> {
                    holder.late.background = selectedBackground(Color.parseColor("#F59E0B"))
                    holder.late.setTextColor(Color.WHITE)
                    holder.stateHint.text = "${record.delayMinutes} دقیقه تأخیر"
                    holder.stateHint.setTextColor(Color.parseColor("#B45309"))
                    holder.card.strokeColor = Color.parseColor("#FDE68A")
                    holder.card.strokeWidth = dp(context, 1)
                }

                AttendanceMarkStatus.ABSENT -> {
                    holder.absent.background = selectedBackground(Color.parseColor("#EF4444"))
                    holder.absent.setTextColor(Color.WHITE)
                    holder.stateHint.text = if (record.isLocked) "ثبت‌شده: غایب" else "وضعیت: غایب"
                    holder.stateHint.setTextColor(Color.parseColor("#B91C1C"))
                    holder.card.strokeColor = Color.parseColor("#FECACA")
                    holder.card.strokeWidth = dp(context, 1)
                }
            }

            holder.itemView.alpha = if (record.isLocked) 0.82f else 1f
        }

        render()

        holder.present.setOnClickListener {
            if (record.isLocked) return@setOnClickListener
            record.status = AttendanceMarkStatus.PRESENT
            record.delayMinutes = 0
            render()
            onStatusChanged()
        }

        holder.absent.setOnClickListener {
            if (record.isLocked) return@setOnClickListener
            record.status = AttendanceMarkStatus.ABSENT
            record.delayMinutes = 0
            render()
            onStatusChanged()
        }

        holder.late.setOnClickListener {
            if (record.isLocked) return@setOnClickListener
            showLateDialog(holder, record, ::render)
        }
    }

    private fun showLateDialog(
        holder: ViewHolder,
        record: AttendanceRecord,
        render: () -> Unit
    ) {
        val context = holder.itemView.context
        val input = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "مثلاً ۱۰"
            textDirection = View.TEXT_DIRECTION_LTR
            gravity = android.view.Gravity.CENTER
            setPadding(dp(context, 20), dp(context, 12), dp(context, 20), dp(context, 12))
            if (record.delayMinutes > 0) {
                setText(record.delayMinutes.toString())
                setSelection(text.length)
            }
        }

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle("مدت تأخیر ${record.studentName}")
            .setMessage("مدت تأخیر را به دقیقه وارد کنید.")
            .setView(input)
            .setNegativeButton("انصراف", null)
            .setPositiveButton("ثبت", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {
                    val minutes = input.text?.toString()?.trim()?.toIntOrNull()
                    if (minutes == null || minutes !in 1..300) {
                        input.error = "عدد بین ۱ تا ۳۰۰ وارد کنید"
                        Toast.makeText(context, "مدت تأخیر نامعتبر است", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    record.status = AttendanceMarkStatus.LATE
                    record.delayMinutes = minutes
                    render()
                    onStatusChanged()
                    dialog.dismiss()
                }
        }
        dialog.show()
    }

    override fun getItemCount(): Int = records.size

    private fun dp(context: android.content.Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
