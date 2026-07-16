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
        val classCount = AppDatabase.getTeacherClasses(teacher.username).size

        holder.txtAvatar.text = teacher.name.firstOrNull()?.toString() ?: "A"
        holder.tvTeacherName.text = teacher.name
        holder.tvTeacherUsername.text = "شماره: ${teacher.username} | کلاس فعال: $classCount"

        holder.txtArchivedBadge.visibility = if (teacher.isActive) View.GONE else View.VISIBLE
        holder.itemRoot.alpha = if (teacher.isActive) 1f else 0.55f

        holder.itemView.setOnClickListener { onRowClick(teacher) }
        holder.btnViewDetails.setOnClickListener { onDetailsClick(teacher) }
    }

    override fun getItemCount(): Int = teachers.size

    fun updateData(newTeachers: List<TeacherModel>) {
        teachers = newTeachers
        notifyDataSetChanged()
    }
}
