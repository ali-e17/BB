package com.example.bb

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TeacherAdapter(
    private var teachers: List<TeacherModel>,
    private val onItemClick: (TeacherModel) -> Unit,
    private val onItemLongClick: (TeacherModel) -> Unit
) : RecyclerView.Adapter<TeacherAdapter.TeacherViewHolder>() {

    class TeacherViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTeacherName: TextView = itemView.findViewById(R.id.tvTeacherName)
        val tvTeacherUsername: TextView = itemView.findViewById(R.id.tvTeacherUsername)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TeacherViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_teacher, parent, false)
        return TeacherViewHolder(view)
    }

    override fun onBindViewHolder(holder: TeacherViewHolder, position: Int) {
        val teacher = teachers[position]

        // نمایش اطلاعات استاد همراه با وضعیت کلاسش (اگر کلاسی داشته باشه)
        holder.tvTeacherName.text = teacher.name

        if (teacher.classId != null) {
            holder.tvTeacherUsername.text = "نام کاربری: ${teacher.username} | کلاس: ${teacher.classId}"
        } else {
            holder.tvTeacherUsername.text = "نام کاربری: ${teacher.username} | (بدون کلاس)"
        }

        // لیسنر کلیک معمولی برای رفتن به صفحه تخصیص کلاس
        holder.itemView.setOnClickListener {
            onItemClick(teacher)
        }

        // لیسنر لانگ‌کلیک برای حذف استاد
        holder.itemView.setOnLongClickListener {
            onItemLongClick(teacher)
            true
        }
    }

    override fun getItemCount(): Int = teachers.size

    fun updateData(newTeachers: List<TeacherModel>) {
        this.teachers = newTeachers
        notifyDataSetChanged()
    }
}