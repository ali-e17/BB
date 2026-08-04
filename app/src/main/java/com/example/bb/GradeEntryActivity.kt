package com.example.bb

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Locale

class GradeEntryActivity : AppCompatActivity() {

    private lateinit var classId: String
    private lateinit var className: String
    private lateinit var rvStudents: RecyclerView
    private lateinit var draftButton: MaterialButton
    private lateinit var publishButton: MaterialButton
    private lateinit var progress: View
    private lateinit var studentsCountText: TextView
    private lateinit var readyCountText: TextView
    private lateinit var incompleteCountText: TextView

    private var config: ReportConfigDto? = null
    private val students = mutableListOf<EditableStudent>()
    private lateinit var adapter: RosterAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_grade_entry)

        classId = intent.getStringExtra(EXTRA_CLASS_ID).orEmpty()
        className = intent.getStringExtra(EXTRA_CLASS_NAME).orEmpty()

        if (classId.isBlank()) {
            finish()
            return
        }

        findViewById<ImageView>(R.id.btnGradeBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.txtGradeClassName).text =
            className.ifBlank { "ورود نمرات" }
        findViewById<TextView>(R.id.txtGradeSubtitle).text =
            "مرحله ۳ از ۳ • ثبت نمرات کارنامه"

        rvStudents = findViewById(R.id.rvStudents)
        draftButton = findViewById(R.id.btnSaveDraft)
        publishButton = findViewById(R.id.btnPublishReports)
        progress = findViewById(R.id.progressGradeEntry)
        studentsCountText = findViewById(R.id.txtStudentsCount)
        readyCountText = findViewById(R.id.txtReadyCount)
        incompleteCountText = findViewById(R.id.txtIncompleteCount)

        rvStudents.layoutManager = LinearLayoutManager(this)
        adapter = RosterAdapter()
        rvStudents.adapter = adapter

        draftButton.setOnClickListener { save(false, "") }
        publishButton.setOnClickListener { requestPublish() }

        updateSummary()
        loadRoster()
    }

    private fun loadRoster() {
        setLoading(true)

        RetrofitClient.instance.getReportRoster(classId)
            .enqueue(object : Callback<ReportRosterResponse> {
                override fun onResponse(
                    call: Call<ReportRosterResponse>,
                    response: Response<ReportRosterResponse>
                ) {
                    setLoading(false)
                    val body = response.body()

                    if (!response.isSuccessful ||
                        body?.status != "success" ||
                        body.config == null
                    ) {
                        toast(body?.message ?: "دریافت فهرست نمرات انجام نشد")
                        return
                    }

                    config = body.config
                    students.clear()
                    students.addAll(
                        body.students.map { student ->
                            EditableStudent(
                                id = student.id,
                                name = student.name,
                                studentCode = student.studentCode,
                                cardId = student.cardId,
                                status = student.status,
                                revision = student.revision,
                                scores = body.config.components.associate { component ->
                                    component.id to student.scores[component.id]
                                }.toMutableMap()
                            )
                        }
                    )

                    adapter.notifyDataSetChanged()
                    updateSummary()

                    if (students.isEmpty()) {
                        toast("دانش‌آموزی در سابقه این کلاس ثبت نشده است")
                    }
                }

                override fun onFailure(
                    call: Call<ReportRosterResponse>,
                    t: Throwable
                ) {
                    setLoading(false)
                    toast("ارتباط با سرور برقرار نشد")
                }
            })
    }

    private fun requestPublish() {
        rvStudents.clearFocus()

        val components = config?.components.orEmpty()
        val incomplete = students.any { student ->
            components.any { component ->
                student.scores[component.id] == null
            }
        }

        if (incomplete) {
            toast("برای انتشار نهایی، نمرات تمامی دانش‌آموزان باید کامل باشد")
            return
        }

        val hasPublished = students.any { it.status == "PUBLISHED" }

        if (!hasPublished) {
            MaterialAlertDialogBuilder(this)
                .setTitle("انتشار کارنامه‌ها")
                .setMessage(
                    "آیا از انتشار کارنامه‌ها اطمینان دارید؟ پس از انتشار، " +
                        "دانش‌آموزان قادر به مشاهده نتایج خود خواهند بود."
                )
                .setNegativeButton("انصراف", null)
                .setPositiveButton("انتشار نهایی") { _, _ ->
                    save(true, "")
                }
                .show()
        } else {
            val input = android.widget.EditText(this).apply {
                hint = "علت ویرایش کارنامه منتشرشده"
            }

            MaterialAlertDialogBuilder(this)
                .setTitle("ویرایش نسخه منتشرشده")
                .setMessage(
                    "شما در حال ویرایش کارنامه‌ای هستید که قبلاً منتشر شده است. " +
                        "ذکر علت ویرایش الزامی است."
                )
                .setView(input)
                .setNegativeButton("انصراف", null)
                .setPositiveButton("انتشار مجدد") { _, _ ->
                    val reason = input.text.toString().trim()
                    if (reason.isBlank()) {
                        toast("علت ویرایش الزامی است")
                    } else {
                        save(true, reason)
                    }
                }
                .show()
        }
    }

    private fun save(publish: Boolean, reason: String) {
        rvStudents.clearFocus()

        val payload = students.map { student ->
            SaveReportStudentRequest(
                student.id,
                student.revision,
                student.scores.toMap()
            )
        }

        setLoading(true)

        RetrofitClient.instance.saveReportCards(
            SaveReportCardsRequest(
                classId,
                publish,
                reason,
                payload
            )
        ).enqueue(object : Callback<ApiResponse> {
            override fun onResponse(
                call: Call<ApiResponse>,
                response: Response<ApiResponse>
            ) {
                setLoading(false)
                val body = response.body()

                if (response.isSuccessful && body?.status == "success") {
                    toast(
                        if (publish) {
                            "کارنامه‌ها با موفقیت منتشر شدند"
                        } else {
                            "پیش‌نویس با موفقیت ذخیره شد"
                        }
                    )
                    loadRoster()
                } else {
                    toast(body?.message ?: "ذخیره نمرات انجام نشد")
                }
            }

            override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                setLoading(false)
                toast("ارتباط با سرور برقرار نشد")
            }
        })
    }

    private fun updateSummary() {
        val components = config?.components.orEmpty()
        val ready = if (components.isEmpty()) {
            0
        } else {
            students.count { student ->
                components.all { component ->
                    student.scores[component.id] != null
                }
            }
        }

        studentsCountText.text = students.size.toString()
        readyCountText.text = ready.toString()
        incompleteCountText.text = (students.size - ready).toString()
    }

    private fun setLoading(value: Boolean) {
        progress.visibility = if (value) View.VISIBLE else View.GONE
        draftButton.isEnabled = !value
        publishButton.isEnabled = !value
    }

    private fun format(value: Double): String =
        if (value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            String.format(Locale.US, "%.2f", value)
        }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun roundedBadge(
        fillColor: Int,
        borderColor: Int
    ): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = 14f * resources.displayMetrics.density
        setColor(fillColor)
        setStroke(dp(1), borderColor)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private data class EditableStudent(
        val id: String,
        val name: String,
        val studentCode: String,
        val cardId: String?,
        val status: String,
        var revision: Int,
        val scores: MutableMap<String, Double?>
    )

    private inner class RosterAdapter :
        RecyclerView.Adapter<RosterAdapter.ViewHolder>() {

        private var expandedStudentId: String? = null

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val headerLayout: View = view.findViewById(R.id.headerLayout)
            val expandableBody: View = view.findViewById(R.id.expandableBody)
            val txtStudentName: TextView = view.findViewById(R.id.txtStudentName)
            val txtStudentCode: TextView = view.findViewById(R.id.txtStudentCode)
            val txtStudentTotal: TextView = view.findViewById(R.id.txtStudentTotal)
            val txtStatusBadge: TextView = view.findViewById(R.id.txtStatusBadge)
            val imgExpandArrow: ImageView = view.findViewById(R.id.imgExpandArrow)
            val btnPreviewCard: ImageView = view.findViewById(R.id.btnPreviewCard)
            val containerGrades: LinearLayout = view.findViewById(R.id.containerGrades)
            val txtExpandedTotal: TextView = view.findViewById(R.id.txtExpandedTotal)
        }

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): ViewHolder = ViewHolder(
            LayoutInflater.from(parent.context).inflate(
                R.layout.item_student_grade,
                parent,
                false
            )
        )

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val student = students[position]
            val components = config?.components.orEmpty()
                .sortedBy { it.sortOrder }

            bindStudentHeader(holder, student, components)

            val isExpanded = student.id == expandedStudentId
            holder.expandableBody.visibility =
                if (isExpanded) View.VISIBLE else View.GONE
            holder.imgExpandArrow.rotation = if (isExpanded) 180f else 0f

            holder.headerLayout.setOnClickListener {
                val previousId = expandedStudentId
                expandedStudentId = if (isExpanded) null else student.id

                val previousIndex =
                    students.indexOfFirst { it.id == previousId }
                if (previousIndex >= 0) {
                    notifyItemChanged(previousIndex)
                }

                val newIndex =
                    students.indexOfFirst { it.id == expandedStudentId }
                if (newIndex >= 0 && newIndex != previousIndex) {
                    notifyItemChanged(newIndex)
                }
            }

            holder.btnPreviewCard.alpha =
                if (student.cardId.isNullOrBlank()) 0.45f else 1.0f

            holder.btnPreviewCard.setOnClickListener {
                if (student.cardId.isNullOrBlank()) {
                    toast(
                        "ابتدا «ذخیره پیش‌نویس» را بزنید تا امکان مشاهده کارنامه فراهم شود"
                    )
                } else {
                    startActivity(
                        Intent(
                            this@GradeEntryActivity,
                            ReportCardViewActivity::class.java
                        ).putExtra(
                            ReportCardViewActivity.EXTRA_REPORT_CARD_ID,
                            student.cardId
                        )
                    )
                }
            }

            holder.containerGrades.removeAllViews()
            val inflater = LayoutInflater.from(holder.itemView.context)

            components.forEach { component ->
                val row = inflater.inflate(
                    R.layout.item_grade_input_compact,
                    holder.containerGrades,
                    false
                )

                row.findViewById<TextView>(R.id.txtCompactCriterion).text =
                    component.title
                row.findViewById<TextView>(R.id.txtCompactMax).text =
                    "حداکثر نمره: ${format(component.maxScore)}"

                val input =
                    row.findViewById<TextInputEditText>(R.id.etCompactScore)

                student.scores[component.id]?.let {
                    input.setText(format(it))
                }

                row.findViewById<View>(R.id.btnCompactZero)
                    .setOnClickListener {
                        input.setText("0")
                        input.clearFocus()
                    }

                input.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(
                        s: CharSequence?,
                        start: Int,
                        count: Int,
                        after: Int
                    ) = Unit

                    override fun onTextChanged(
                        s: CharSequence?,
                        start: Int,
                        before: Int,
                        count: Int
                    ) = Unit

                    override fun afterTextChanged(s: Editable?) {
                        val raw = s?.toString()?.trim().orEmpty()
                        val value = raw.toDoubleOrNull()

                        when {
                            raw.isBlank() -> {
                                student.scores[component.id] = null
                                input.error = null
                            }

                            value == null ||
                                value < 0.0 ||
                                value > component.maxScore -> {
                                student.scores[component.id] = null
                                input.error =
                                    "۰ تا ${format(component.maxScore)}"
                            }

                            else -> {
                                student.scores[component.id] = value
                                input.error = null
                            }
                        }

                        bindStudentHeader(holder, student, components)
                        updateSummary()
                    }
                })

                holder.containerGrades.addView(row)
            }
        }

        private fun bindStudentHeader(
            holder: ViewHolder,
            student: EditableStudent,
            components: List<ReportComponentDto>
        ) {
            holder.txtStudentName.text =
                student.name.ifBlank { "بدون نام" }
            holder.txtStudentCode.text =
                "کد: ${student.studentCode.ifBlank { "—" }}"

            val currentTotal = components.sumOf { component ->
                student.scores[component.id] ?: 0.0
            }
            holder.txtStudentTotal.text =
                "نمره فعلی: ${format(currentTotal)}"
            holder.txtExpandedTotal.text =
                "جمع نمره‌های واردشده: ${format(currentTotal)} از 100"

            val validCount = components.count { component ->
                student.scores[component.id] != null
            }

            when {
                validCount == 0 -> applyBadge(
                    holder.txtStatusBadge,
                    "خالی",
                    Color.parseColor("#B42318"),
                    Color.parseColor("#FEF3F2"),
                    Color.parseColor("#FECDCA")
                )

                validCount < components.size -> applyBadge(
                    holder.txtStatusBadge,
                    "ناقص",
                    Color.parseColor("#B54708"),
                    Color.parseColor("#FFFAEB"),
                    Color.parseColor("#FEDF89")
                )

                student.status == "PUBLISHED" -> applyBadge(
                    holder.txtStatusBadge,
                    "منتشر شده",
                    Color.parseColor("#175CD3"),
                    Color.parseColor("#EFF8FF"),
                    Color.parseColor("#B2DDFF")
                )

                else -> applyBadge(
                    holder.txtStatusBadge,
                    "آماده",
                    Color.parseColor("#067647"),
                    Color.parseColor("#ECFDF3"),
                    Color.parseColor("#ABEFC6")
                )
            }
        }

        private fun applyBadge(
            view: TextView,
            text: String,
            textColor: Int,
            fillColor: Int,
            borderColor: Int
        ) {
            view.text = text
            view.setTextColor(textColor)
            view.background = roundedBadge(fillColor, borderColor)
        }

        override fun getItemCount(): Int = students.size
    }

    companion object {
        const val EXTRA_CLASS_ID = "REPORT_CLASS_ID"
        const val EXTRA_CLASS_NAME = "REPORT_CLASS_NAME"
    }
}
