package com.example.bb

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
        val txtAvatar: TextView = itemView.findViewById(R.id.txtTeacherAvatar)
        val tvTeacherName: TextView = itemView.findViewById(R.id.tvTeacherName)
        val tvTeacherUsername: TextView = itemView.findViewById(R.id.tvTeacherUsername)
        val txtArchivedBadge: TextView = itemView.findViewById(R.id.txtArchivedBadge)
        val btnViewDetails: MaterialButton = itemView.findViewById(R.id.btnViewTeacherDetails)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TeacherViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_teacher, parent, false)
        return TeacherViewHolder(view)
    }

    override fun onBindViewHolder(holder: TeacherViewHolder, position: Int) {
        val teacher = teachers[position]

        holder.txtAvatar.text = teacher.name.firstOrNull()?.toString() ?: "A"
        holder.tvTeacherName.text = teacher.name

        // نمایش اینکه چندتا کلاس داره
        val classCount = if (teacher.classIds.isEmpty()) 0 else teacher.classIds.split(",").size
        holder.tvTeacherUsername.text = "شماره: ${teacher.username} | کلاس‌ها: $classCount"

        if (!teacher.isActive) {
            holder.txtArchivedBadge.visibility = View.VISIBLE
            holder.itemRoot.alpha = 0.5f
        } else {
            holder.txtArchivedBadge.visibility = View.GONE
            holder.itemRoot.alpha = 1.0f
        }

        holder.itemView.setOnClickListener { onRowClick(teacher) }
        holder.btnViewDetails.setOnClickListener { onDetailsClick(teacher) }
    }

    override fun getItemCount(): Int = teachers.size

    fun updateData(newTeachers: List<TeacherModel>) {
        this.teachers = newTeachers
        notifyDataSetChanged()
    }
}