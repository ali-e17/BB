package com.example.bb

import android.content.Intent
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.ArrayList

class StudentGradeAdapter(
    students: List<StudentGrade>,
    private val activeCriteria: List<GradeComponent>,
    private val className: String,
    private val onStatusChanged: () -> Unit
) : RecyclerView.Adapter<StudentGradeAdapter.ViewHolder>() {

    private val students = mutableListOf<StudentGrade>().apply { addAll(students) }
    private var expandedStudentId: String? = null

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val headerLayout: RelativeLayout = view.findViewById(R.id.headerLayout)
        val expandableBody: LinearLayout = view.findViewById(R.id.expandableBody)
        val txtStudentName: TextView = view.findViewById(R.id.txtStudentName)
        val txtStatusBadge: TextView = view.findViewById(R.id.txtStatusBadge)
        val imgExpandArrow: ImageView = view.findViewById(R.id.imgExpandArrow)
        val btnPreviewCard: ImageView = view.findViewById(R.id.btnPreviewCard)
        val containerGrades: LinearLayout = view.findViewById(R.id.containerGrades)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_student_grade, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val student = students[position]
        holder.txtStudentName.text = student.name
        recalculateStudentStatus(student)
        updateStatusUI(holder, student)

        val isExpanded = student.id == expandedStudentId
        holder.expandableBody.visibility = if (isExpanded) View.VISIBLE else View.GONE
        holder.imgExpandArrow.rotation = if (isExpanded) 180f else 0f

        holder.headerLayout.setOnClickListener {
            val previousId = expandedStudentId
            expandedStudentId = if (isExpanded) null else student.id
            previousId?.let { id ->
                val previousIndex = students.indexOfFirst { it.id == id }
                if (previousIndex >= 0) notifyItemChanged(previousIndex)
            }
            val newIndex = students.indexOfFirst { it.id == expandedStudentId }
            if (newIndex >= 0) notifyItemChanged(newIndex)
        }

        holder.btnPreviewCard.setOnClickListener {
            val context = holder.itemView.context
            val criteriaNames = ArrayList<String>()
            val scoresList = ArrayList<Int>()
            val maxScoresList = ArrayList<Int>()

            activeCriteria.forEach { criterion ->
                criteriaNames += criterion.name
                scoresList += student.scores[criterion.id] ?: 0
                maxScoresList += criterion.maxScore
            }

            context.startActivity(
                Intent(context, ReportCardViewActivity::class.java)
                    .putExtra("STUDENT_ID", student.studentCode)
                    .putExtra("STUDENT_NAME", student.name)
                    .putExtra("CLASS_NAME", className)
                    .putExtra("REPORT_DATE", AppDatabase.today())
                    .putStringArrayListExtra("CRITERIA_NAMES", criteriaNames)
                    .putIntegerArrayListExtra("SCORES_LIST", scoresList)
                    .putIntegerArrayListExtra("MAX_SCORES_LIST", maxScoresList)
            )
        }

        holder.containerGrades.removeAllViews()
        val inflater = LayoutInflater.from(holder.itemView.context)

        activeCriteria.forEach { criterion ->
            val gradeView = inflater.inflate(
                R.layout.item_grade_input,
                holder.containerGrades,
                false
            )

            val txtName = gradeView.findViewById<TextView>(R.id.txtCriteriaName)
            val etInput = gradeView.findViewById<TextInputEditText>(R.id.etScoreInput)
            val inputLayout = gradeView.findViewById<TextInputLayout>(R.id.textInputLayoutScore)
            val btnAbsent = gradeView.findViewById<Button>(R.id.btnAbsent)

            txtName.text = criterion.name
            inputLayout.hint = "از ${criterion.maxScore}"
            val currentScore = student.scores[criterion.id]
            etInput.setText(currentScore?.toString() ?: "")
            etInput.setSelectAllOnFocus(true)

            btnAbsent.setOnClickListener {
                etInput.setText("0")
                etInput.setSelection(etInput.text?.length ?: 0)
            }

            etInput.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    val score = s?.toString()?.toIntOrNull()
                    when {
                        score == null -> {
                            inputLayout.error = "نمره را وارد کنید"
                            student.scores[criterion.id] = null
                        }
                        score !in 0..criterion.maxScore -> {
                            inputLayout.error = "بین ۰ تا ${criterion.maxScore}"
                            student.scores[criterion.id] = null
                        }
                        else -> {
                            inputLayout.error = null
                            student.scores[criterion.id] = score
                        }
                    }

                    recalculateStudentStatus(student)
                    updateStatusUI(holder, student)
                    onStatusChanged()
                }
            })

            holder.containerGrades.addView(gradeView)
        }
    }

    fun updateStudents(newStudents: List<StudentGrade>) {
        students.clear()
        students.addAll(newStudents)
        if (expandedStudentId != null && students.none { it.id == expandedStudentId }) {
            expandedStudentId = null
        }
        notifyDataSetChanged()
    }

    private fun recalculateStudentStatus(student: StudentGrade) {
        val validCount = activeCriteria.count { student.scores[it.id] != null }
        student.status = when (validCount) {
            0 -> EntryStatus.NOT_STARTED
            activeCriteria.size -> EntryStatus.COMPLETED
            else -> EntryStatus.IN_PROGRESS
        }
    }

    private fun updateStatusUI(holder: ViewHolder, student: StudentGrade) {
        when (student.status) {
            EntryStatus.NOT_STARTED -> {
                holder.txtStatusBadge.text = "خالی"
                holder.txtStatusBadge.setTextColor(0xFFEF4444.toInt())
                holder.btnPreviewCard.visibility = View.GONE
            }
            EntryStatus.IN_PROGRESS -> {
                holder.txtStatusBadge.text = "نیازمند اصلاح"
                holder.txtStatusBadge.setTextColor(0xFFF59E0B.toInt())
                holder.btnPreviewCard.visibility = View.GONE
            }
            EntryStatus.COMPLETED -> {
                holder.txtStatusBadge.text = "آماده"
                holder.txtStatusBadge.setTextColor(0xFF10B981.toInt())
                holder.btnPreviewCard.visibility = View.VISIBLE
            }
        }
    }

    override fun getItemCount(): Int = students.size
}
