package com.example.bb

import android.content.Intent
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

class GradeEntryActivity : AppCompatActivity() {
    private lateinit var classId: String
    private lateinit var className: String
    private lateinit var rvStudents: RecyclerView
    private lateinit var draftButton: MaterialButton
    private lateinit var publishButton: MaterialButton
    private lateinit var progress: View

    private var config: ReportConfigDto? = null
    private val students = mutableListOf<EditableStudent>()
    private lateinit var adapter: RosterAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_grade_entry)

        classId = intent.getStringExtra(EXTRA_CLASS_ID).orEmpty()
        className = intent.getStringExtra(EXTRA_CLASS_NAME).orEmpty()
        if (classId.isBlank()) { finish(); return }

        findViewById<ImageView>(R.id.btnGradeBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.txtGradeClassName).text = "لیست نمرات: $className"

        rvStudents = findViewById(R.id.rvStudents)
        draftButton = findViewById(R.id.btnSaveDraft)
        publishButton = findViewById(R.id.btnPublishReports)
        progress = findViewById(R.id.progressGradeEntry)

        rvStudents.layoutManager = LinearLayoutManager(this)
        adapter = RosterAdapter()
        rvStudents.adapter = adapter

        draftButton.setOnClickListener { save(false, "") }
        publishButton.setOnClickListener { requestPublish() }

        loadRoster()
    }

    private fun loadRoster() {
        setLoading(true)
        RetrofitClient.instance.getReportRoster(classId).enqueue(object : Callback<ReportRosterResponse> {
            override fun onResponse(call: Call<ReportRosterResponse>, response: Response<ReportRosterResponse>) {
                setLoading(false)
                val body = response.body()
                if (!response.isSuccessful || body?.status != "success" || body.config == null) {
                    toast(body?.message ?: "دریافت فهرست نمرات انجام نشد"); return
                }
                config = body.config
                students.clear()
                students.addAll(body.students.map { s ->
                    EditableStudent(s.id, s.name, s.studentCode, s.cardId, s.status, s.revision,
                        body.config.components.associate { c -> c.id to s.scores[c.id] }.toMutableMap())
                })
                adapter.notifyDataSetChanged()
                if (students.isEmpty()) toast("دانش‌آموزی در سابقه این کلاس ثبت نشده است")
            }
            override fun onFailure(call: Call<ReportRosterResponse>, t: Throwable) {
                setLoading(false); toast("ارتباط با سرور برقرار نشد")
            }
        })
    }

    private fun requestPublish() {
        rvStudents.clearFocus()
        val incomplete = students.any { s -> config?.components.orEmpty().any { s.scores[it.id] == null } }
        if (incomplete) { toast("برای انتشار نهایی، نمرات تمامی دانش‌آموزان باید کامل باشد"); return }

        val hasPublished = students.any { it.status == "PUBLISHED" }
        if (!hasPublished) {
            MaterialAlertDialogBuilder(this)
                .setTitle("انتشار کارنامه‌ها")
                .setMessage("آیا از انتشار کارنامه‌ها اطمینان دارید؟ پس از انتشار، دانش‌آموزان قادر به مشاهده نتایج خود خواهند بود.")
                .setNegativeButton("انصراف", null)
                .setPositiveButton("انتشار نهایی") { _, _ -> save(true, "") }
                .show()
        } else {
            val input = android.widget.EditText(this).apply { hint = "علت ویرایش کارنامه منتشرشده" }
            MaterialAlertDialogBuilder(this)
                .setTitle("ویرایش نسخه منتشرشده")
                .setMessage("شما در حال ویرایش کارنامه‌ای هستید که قبلاً منتشر شده است. ذکر علت ویرایش الزامی است.")
                .setView(input)
                .setNegativeButton("انصراف", null)
                .setPositiveButton("انتشار مجدد") { _, _ ->
                    val reason = input.text.toString().trim()
                    if (reason.isBlank()) toast("علت ویرایش الزامی است") else save(true, reason)
                }.show()
        }
    }

    private fun save(publish: Boolean, reason: String) {
        rvStudents.clearFocus()
        val payload = students.map { s -> SaveReportStudentRequest(s.id, s.revision, s.scores.toMap()) }
        setLoading(true)
        RetrofitClient.instance.saveReportCards(SaveReportCardsRequest(classId, publish, reason, payload)).enqueue(object : Callback<ApiResponse> {
            override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                setLoading(false)
                val body = response.body()
                if (response.isSuccessful && body?.status == "success") {
                    toast(if (publish) "کارنامه‌ها با موفقیت منتشر شدند" else "پیش‌نویس با موفقیت ذخیره شد")
                    loadRoster()
                } else {
                    toast(body?.message ?: "ذخیره نمرات انجام نشد")
                }
            }
            override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                setLoading(false); toast("ارتباط با سرور برقرار نشد")
            }
        })
    }

    private fun setLoading(v: Boolean) {
        progress.visibility = if (v) View.VISIBLE else View.GONE
        draftButton.isEnabled = !v
        publishButton.isEnabled = !v
    }

    private fun format(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else "%.2f".format(v)
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    private data class EditableStudent(val id:String, val name:String, val studentCode:String, val cardId:String?, val status:String, var revision:Int, val scores:MutableMap<String,Double?>)

    // آداپتر اختصاصی برای لیست کشویی هر دانش‌آموز
    private inner class RosterAdapter : RecyclerView.Adapter<RosterAdapter.ViewHolder>() {
        private var expandedStudentId: String? = null

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val headerLayout: View = view.findViewById(R.id.headerLayout)
            val expandableBody: View = view.findViewById(R.id.expandableBody)
            val txtStudentName: TextView = view.findViewById(R.id.txtStudentName)
            val txtStatusBadge: TextView = view.findViewById(R.id.txtStatusBadge)
            val imgExpandArrow: ImageView = view.findViewById(R.id.imgExpandArrow)
            val btnPreviewCard: ImageView = view.findViewById(R.id.btnPreviewCard)
            val containerGrades: LinearLayout = view.findViewById(R.id.containerGrades)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_student_grade, parent, false))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val student = students[position]
            holder.txtStudentName.text = student.name

            val components = config?.components.orEmpty().sortedBy { it.sortOrder }
            val validCount = components.count { student.scores[it.id] != null }

            when {
                validCount == 0 -> {
                    holder.txtStatusBadge.text = "خالی"
                    holder.txtStatusBadge.setTextColor(0xFFEF4444.toInt())
                }
                validCount < components.size -> {
                    holder.txtStatusBadge.text = "ناقص"
                    holder.txtStatusBadge.setTextColor(0xFFF59E0B.toInt())
                }
                else -> {
                    holder.txtStatusBadge.text = "آماده"
                    holder.txtStatusBadge.setTextColor(0xFF10B981.toInt())
                }
            }

            val isExpanded = student.id == expandedStudentId
            holder.expandableBody.visibility = if (isExpanded) View.VISIBLE else View.GONE
            holder.imgExpandArrow.rotation = if (isExpanded) 180f else 0f

            holder.headerLayout.setOnClickListener {
                val prevId = expandedStudentId
                expandedStudentId = if (isExpanded) null else student.id
                prevId?.let { id -> notifyItemChanged(students.indexOfFirst { it.id == id }) }
                expandedStudentId?.let { id -> notifyItemChanged(students.indexOfFirst { it.id == id }) }
            }

            holder.btnPreviewCard.setOnClickListener {
                if (student.cardId.isNullOrBlank()) {
                    toast("ابتدا دکمه «ذخیره پیش‌نویس» در پایین صفحه را بزنید تا امکان مشاهده کارنامه فراهم شود")
                } else {
                    startActivity(Intent(this@GradeEntryActivity, ReportCardViewActivity::class.java)
                        .putExtra(ReportCardViewActivity.EXTRA_REPORT_CARD_ID, student.cardId))
                }
            }

            holder.containerGrades.removeAllViews()
            val inflater = LayoutInflater.from(holder.itemView.context)

            components.forEach { component ->
                val row = inflater.inflate(R.layout.item_grade_input_compact, holder.containerGrades, false)
                row.findViewById<TextView>(R.id.txtCompactCriterion).text = component.title
                row.findViewById<TextView>(R.id.txtCompactMax).text = "از ${format(component.maxScore)}"
                val input = row.findViewById<TextInputEditText>(R.id.etCompactScore)

                student.scores[component.id]?.let { input.setText(format(it)) }

                row.findViewById<View>(R.id.btnCompactZero).setOnClickListener {
                    input.setText("0")
                    input.clearFocus()
                }

                input.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    override fun afterTextChanged(s: Editable?) {
                        val raw = s?.toString()?.trim().orEmpty()
                        val value = raw.toDoubleOrNull()
                        if (raw.isBlank()) {
                            student.scores[component.id] = null
                            input.error = null
                        } else if (value == null || value < 0 || value > component.maxScore) {
                            student.scores[component.id] = null
                            input.error = "۰ تا ${format(component.maxScore)}"
                        } else {
                            student.scores[component.id] = value
                            input.error = null
                        }

                        // بروزرسانی وضعیت لیبل در لحظه
                        val newValidCount = components.count { student.scores[it.id] != null }
                        when {
                            newValidCount == 0 -> { holder.txtStatusBadge.text = "خالی"; holder.txtStatusBadge.setTextColor(0xFFEF4444.toInt()) }
                            newValidCount < components.size -> { holder.txtStatusBadge.text = "ناقص"; holder.txtStatusBadge.setTextColor(0xFFF59E0B.toInt()) }
                            else -> { holder.txtStatusBadge.text = "آماده"; holder.txtStatusBadge.setTextColor(0xFF10B981.toInt()) }
                        }
                    }
                })
                holder.containerGrades.addView(row)
            }
        }
        override fun getItemCount() = students.size
    }

    companion object { const val EXTRA_CLASS_ID = "REPORT_CLASS_ID"; const val EXTRA_CLASS_NAME = "REPORT_CLASS_NAME" }
}