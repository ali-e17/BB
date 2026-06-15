package com.example.bb

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class StudentGradeAdapter(
    private val students: List<StudentGrade>,
    private val activeCriteria: List<GradeComponent>,
    private val onStatusChanged: () -> Unit
) : RecyclerView.Adapter<StudentGradeAdapter.ViewHolder>() {

    // متغیری برای ذخیره موقعیت آیتمی که الان باز است. 1- یعنی پیش‌فرض هیچکدام باز نیست.
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

        // آپدیت وضعیت ظاهری (متن و رنگِ لیبل وضعیت)
        updateStatusUI(holder, student)

        // --- منطق باز و بسته شدن هوشمند (آکاردئون تکی) ---
        val isExpanded = position == expandedPosition
        holder.expandableBody.visibility = if (isExpanded) View.VISIBLE else View.GONE
        holder.imgExpandArrow.rotation = if (isExpanded) 180f else 0f

        holder.headerLayout.setOnClickListener {
            val previousExpandedPosition = expandedPosition

            if (isExpanded) {
                // اگر روی همین آیتمی که بازه کلیک کرد، ببندش
                expandedPosition = -1
            } else {
                // اگر روی یه آیتم دیگه کلیک کرد، قبلی رو ببند و این یکی رو باز کن
                expandedPosition = holder.adapterPosition
            }

            // به آداپتور میگیم فقط همین دو تا آیتم رو دوباره رفرش کن تا انیمیشن باز و بسته شدن اجرا بشه
            notifyItemChanged(previousExpandedPosition)
            notifyItemChanged(expandedPosition)
        }

        // دکمه پیش‌نمایش
        holder.btnPreviewCard.setOnClickListener {
            Toast.makeText(holder.itemView.context, "نمایش پیش‌نمایش کارنامه ${student.name}", Toast.LENGTH_SHORT).show()
        }

        // --- تزریق داینامیک فیلدهای نمره ---
        holder.containerGrades.removeAllViews() // پاکسازی قبلی‌ها برای جلوگیری از تکرار روی هم
        val inflater = LayoutInflater.from(holder.itemView.context)

        for (criteria in activeCriteria) {
            val gradeView = inflater.inflate(R.layout.item_grade_input, holder.containerGrades, false)

            val txtName = gradeView.findViewById<TextView>(R.id.txtCriteriaName)
            val etInput = gradeView.findViewById<TextInputEditText>(R.id.etScoreInput)
            // از آیدی textInputLayoutScore که تو مرحله قبل ساختی استفاده می‌کنیم تا کرش نکنه
            val inputLayout = gradeView.findViewById<TextInputLayout>(R.id.textInputLayoutScore)
            val btnAbsent = gradeView.findViewById<Button>(R.id.btnAbsent)

            txtName.text = criteria.name
            inputLayout.hint = "از ${criteria.maxScore}"

            // اگر قبلاً نمره‌ای برای این فیلد وارد شده بود، روی صفحه نشون بده
            student.scores[criteria.id]?.let { etInput.setText(it.toString()) }

            // دکمه غایب (صفر دادن سریع)
            btnAbsent.setOnClickListener {
                etInput.setText("0")
            }

            // گوش دادن به تایپ شدن نمره

            // گوش دادن به تایپ شدن نمره و اعتبارسنجی
            etInput.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val inputText = s.toString()
                    val score = inputText.toIntOrNull()

                    // چک کردن قانون نمره (نباید بیشتر از سقف یا کمتر از صفر باشه)
                    if (score != null && (score > criteria.maxScore || score < 0)) {
                        inputLayout.error = "حداکثر: ${criteria.maxScore}" // متن ارور رو کوتاه و شیک کردیم
                        student.scores[criteria.id] = null
                    } else {
                        inputLayout.error = null
                        student.scores[criteria.id] = score
                    }

                    recalculateStudentStatus(student) // محاسبه مجدد وضعیت دانشجو

                    // آپدیت کردن ظاهر و چک کردن دکمه پابلیش
                    if (holder.adapterPosition != RecyclerView.NO_POSITION) {
                        updateStatusUI(holder, student)
                        onStatusChanged()
                    }
                }
            })

            holder.containerGrades.addView(gradeView)
        }
    }

    // متدی برای محاسبه وضعیت دانشجو (شروع نشده، در حال ثبت، تکمیل شده)
    private fun recalculateStudentStatus(student: StudentGrade) {
        val filledCount = activeCriteria.count { student.scores[it.id] != null }
        student.status = when (filledCount) {
            0 -> EntryStatus.NOT_STARTED
            activeCriteria.size -> EntryStatus.COMPLETED
            else -> EntryStatus.IN_PROGRESS
        }
    }

    // متدی برای آپدیت رنگ و متنِ لیبلِ وضعیتِ دانشجو
    private fun updateStatusUI(holder: ViewHolder, student: StudentGrade) {
        when (student.status) {
            EntryStatus.NOT_STARTED -> {
                holder.txtStatusBadge.text = "شروع نشده"
                holder.txtStatusBadge.setTextColor(0xFFEF4444.toInt()) // قرمز
                holder.btnPreviewCard.visibility = View.GONE
            }
            EntryStatus.IN_PROGRESS -> {
                holder.txtStatusBadge.text = "در حال ثبت"
                holder.txtStatusBadge.setTextColor(0xFFF59E0B.toInt()) // زرد
                holder.btnPreviewCard.visibility = View.GONE
            }
            EntryStatus.COMPLETED -> {
                holder.txtStatusBadge.text = "تکمیل شده"
                holder.txtStatusBadge.setTextColor(0xFF10B981.toInt()) // سبز
                holder.btnPreviewCard.visibility = View.VISIBLE // چشمِ پیش‌نمایش فعال میشه
            }
        }
    }

    override fun getItemCount() = students.size
}