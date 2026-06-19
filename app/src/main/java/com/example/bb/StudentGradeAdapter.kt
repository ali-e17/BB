package com.example.bb

import android.content.Intent
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.ArrayList

class StudentGradeAdapter(
    private val students: List<StudentGrade>,
    private val activeCriteria: List<GradeComponent>,
    private val onStatusChanged: () -> Unit
) : RecyclerView.Adapter<StudentGradeAdapter.ViewHolder>() {

    private var expandedPosition = -1

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
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_student_grade, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val student = students[position]
        holder.txtStudentName.text = student.name

        updateStatusUI(holder, student)

        val isExpanded = position == expandedPosition
        holder.expandableBody.visibility = if (isExpanded) View.VISIBLE else View.GONE
        holder.imgExpandArrow.rotation = if (isExpanded) 180f else 0f

        // هندل کردن باز و بسته شدن کشو
        holder.headerLayout.setOnClickListener {
            val previousExpandedPosition = expandedPosition

            if (isExpanded) {
                expandedPosition = -1
            } else {
                expandedPosition = holder.adapterPosition
            }

            notifyItemChanged(previousExpandedPosition)
            notifyItemChanged(expandedPosition)
        }

        // ====== منطق دکمه پیش‌نمایش (چشم) ======
        holder.btnPreviewCard.setOnClickListener {
            val context = holder.itemView.context
            val intent = Intent(context, ReportCardViewActivity::class.java)

            // Pass Student ID and Full Name
            intent.putExtra("STUDENT_ID", student.id)
            intent.putExtra("STUDENT_NAME", student.name)

            val criteriaNames = ArrayList<String>()
            val scoresList = ArrayList<Int>()
            val maxScoresList = ArrayList<Int>()

            for (criteria in activeCriteria) {
                criteriaNames.add(criteria.name)
                scoresList.add(student.scores[criteria.id] ?: 0)
                maxScoresList.add(criteria.maxScore) // Pass max value for "Out of" column
            }

            intent.putStringArrayListExtra("CRITERIA_NAMES", criteriaNames)
            intent.putIntegerArrayListExtra("SCORES_LIST", scoresList)
            intent.putIntegerArrayListExtra("MAX_SCORES_LIST", maxScoresList)

            context.startActivity(intent)
        }

        // پاکسازی و تزریق داینامیک فیلدهای نمره
        holder.containerGrades.removeAllViews()
        val inflater = LayoutInflater.from(holder.itemView.context)

        for (criteria in activeCriteria) {
            val gradeView = inflater.inflate(R.layout.item_grade_input, holder.containerGrades, false)

            val txtName = gradeView.findViewById<TextView>(R.id.txtCriteriaName)
            val etInput = gradeView.findViewById<TextInputEditText>(R.id.etScoreInput)
            val inputLayout = gradeView.findViewById<TextInputLayout>(R.id.textInputLayoutScore)
            val btnAbsent = gradeView.findViewById<Button>(R.id.btnAbsent)

            txtName.text = criteria.name
            inputLayout.hint = "از ${criteria.maxScore}"

            student.scores[criteria.id]?.let { etInput.setText(it.toString()) }

            btnAbsent.setOnClickListener {
                etInput.setText("0")
            }

            etInput.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val inputText = s.toString()
                    val score = inputText.toIntOrNull()

                    // اعتبارسنجی نمره
                    if (score != null && (score > criteria.maxScore || score < 0)) {
                        inputLayout.error = "حداکثر: ${criteria.maxScore}"
                        student.scores[criteria.id] = null
                    } else {
                        inputLayout.error = null
                        student.scores[criteria.id] = score
                    }

                    recalculateStudentStatus(student)

                    if (holder.adapterPosition != RecyclerView.NO_POSITION) {
                        updateStatusUI(holder, student)
                        onStatusChanged()
                    }
                }
            })

            holder.containerGrades.addView(gradeView)
        }
    }

    private fun recalculateStudentStatus(student: StudentGrade) {
        val filledCount = activeCriteria.count { student.scores[it.id] != null }
        student.status = when (filledCount) {
            0 -> EntryStatus.NOT_STARTED
            activeCriteria.size -> EntryStatus.COMPLETED
            else -> EntryStatus.IN_PROGRESS
        }
    }

    private fun updateStatusUI(holder: ViewHolder, student: StudentGrade) {
        when (student.status) {
            EntryStatus.NOT_STARTED -> {
                holder.txtStatusBadge.text = "شروع نشده"
                holder.txtStatusBadge.setTextColor(0xFFEF4444.toInt())
                holder.btnPreviewCard.visibility = View.GONE
            }
            EntryStatus.IN_PROGRESS -> {
                holder.txtStatusBadge.text = "در حال ثبت"
                holder.txtStatusBadge.setTextColor(0xFFF59E0B.toInt())
                holder.btnPreviewCard.visibility = View.GONE
            }
            EntryStatus.COMPLETED -> {
                holder.txtStatusBadge.text = "تکمیل شده"
                holder.txtStatusBadge.setTextColor(0xFF10B981.toInt())
                holder.btnPreviewCard.visibility = View.VISIBLE
            }
        }
    }

    override fun getItemCount() = students.size
}