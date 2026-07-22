package com.example.bb

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

class TeacherAdapter(
    private var teachers: List<TeacherModel>,
    private val onRowClick: (TeacherModel) -> Unit,
    private val onDetailsClick: (TeacherModel) -> Unit
) : RecyclerView.Adapter<TeacherAdapter.TeacherViewHolder>() {

    class TeacherViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val itemRoot: LinearLayout = itemView.findViewById(R.id.itemTeacherRoot)
        val avatar: ImageView = itemView.findViewById(R.id.ivTeacherAvatar)
        val name: TextView = itemView.findViewById(R.id.tvTeacherName)
        val phone: TextView = itemView.findViewById(R.id.tvTeacherUsername)
        val classBadge: TextView = itemView.findViewById(R.id.tvTeacherClassCount)
        val archivedBadge: TextView = itemView.findViewById(R.id.txtArchivedBadge)
        val details: MaterialButton = itemView.findViewById(R.id.btnViewTeacherDetails)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TeacherViewHolder =
        TeacherViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_teacher, parent, false))

    override fun onBindViewHolder(holder: TeacherViewHolder, position: Int) {
        val teacher = teachers[position]
        val context = holder.itemView.context
        val avatarName = teacher.avatarName?.takeIf { it.isNotBlank() } ?: "avatar_teacher_1"
        val res = context.resources.getIdentifier(avatarName, "drawable", context.packageName)
        holder.avatar.setImageResource(if (res != 0) res else R.drawable.avatar_teacher_1)
        holder.name.text = teacher.name
        holder.phone.text = "شماره تماس: ${teacher.phone}"
        val count = AppDatabase.getTeacherClasses(teacher.phone).size
        holder.classBadge.text = if (count == 0) "بدون کلاس" else "$count کلاس فعال"
        holder.archivedBadge.visibility = if (teacher.isActive) View.GONE else View.VISIBLE
        holder.itemRoot.alpha = if (teacher.isActive) 1f else .7f
        holder.itemRoot.setOnClickListener { onRowClick(teacher) }
        holder.details.setOnClickListener { onDetailsClick(teacher) }
    }

    override fun getItemCount(): Int = teachers.size
    fun updateData(newTeachers: List<TeacherModel>) { teachers = newTeachers; notifyDataSetChanged() }
}
