package com.example.bb

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

class StudentAdapter(
    private var studentList: List<StudentModel>,
    private val onDetailsClicked: (StudentModel) -> Unit
) : RecyclerView.Adapter<StudentAdapter.StudentViewHolder>() {

    class StudentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivAvatar: ImageView = itemView.findViewById(R.id.ivStudentAvatar)
        val tvName: TextView = itemView.findViewById(R.id.tvStudentName)
        val tvId: TextView = itemView.findViewById(R.id.tvStudentId)
        val tvLevel: TextView = itemView.findViewById(R.id.tvStudentLevel)
        val btnDetails: MaterialButton = itemView.findViewById(R.id.btnViewDetails)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StudentViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_student_list, parent, false)
        return StudentViewHolder(view)
    }

    override fun onBindViewHolder(holder: StudentViewHolder, position: Int) {
        val student = studentList[position]

        holder.tvName.text = student.name
        holder.tvId.text = "کد: ${student.studentCode}"
        holder.tvLevel.text = AppDatabase.getClassNameById(student.classId) ?: "بدون کلاس فعال"
        holder.ivAvatar.setImageResource(student.avatarResId)

        // اگر بایگانی شده بود، رنگش رو خاکستری کنیم (جلوه بصری خفن)
        if (!student.isActive) {
            holder.itemView.alpha = 0.5f
            holder.btnDetails.text = "بایگانی شده"
        } else {
            holder.itemView.alpha = 1.0f
            holder.btnDetails.text = "جزئیات"
        }

        holder.btnDetails.setOnClickListener {
            onDetailsClicked(student)
        }
    }

    override fun getItemCount(): Int = studentList.size

    // این تابع برای سرچ زنده استفاده میشه
    fun updateList(newList: List<StudentModel>) {
        studentList = newList
        notifyDataSetChanged()
    }
}
