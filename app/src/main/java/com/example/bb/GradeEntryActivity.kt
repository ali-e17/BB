package com.example.bb

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class GradeEntryActivity : AppCompatActivity() {
    private lateinit var classId: String
    private lateinit var className: String
    private lateinit var studentDropdown: MaterialAutoCompleteTextView
    private lateinit var studentMeta: TextView
    private lateinit var scoreContainer: LinearLayout
    private lateinit var totalText: TextView
    private lateinit var draftButton: MaterialButton
    private lateinit var publishButton: MaterialButton
    private lateinit var progress: View
    private var config: ReportConfigDto? = null
    private val students = mutableListOf<EditableStudent>()
    private var selectedIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_grade_entry)
        classId = intent.getStringExtra(EXTRA_CLASS_ID).orEmpty()
        className = intent.getStringExtra(EXTRA_CLASS_NAME).orEmpty()
        if (classId.isBlank()) { finish(); return }
        findViewById<ImageView>(R.id.btnGradeBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.txtGradeClassName).text = className
        studentDropdown = findViewById(R.id.dropdownGradeStudent)
        studentMeta = findViewById(R.id.txtGradeStudentMeta)
        scoreContainer = findViewById(R.id.containerGradeInputs)
        totalText = findViewById(R.id.txtGradeTotal)
        draftButton = findViewById(R.id.btnSaveDraft)
        publishButton = findViewById(R.id.btnPublishReports)
        progress = findViewById(R.id.progressGradeEntry)
        findViewById<View>(R.id.btnPreviousStudent).setOnClickListener { move(-1) }
        findViewById<View>(R.id.btnNextStudent).setOnClickListener { move(1) }
        draftButton.setOnClickListener { save(false, "") }
        publishButton.setOnClickListener { requestPublish() }
        loadRoster()
    }

    private fun loadRoster() {
        setLoading(true)
        RetrofitClient.instance.getReportRoster(classId).enqueue(object : Callback<ReportRosterResponse> {
            override fun onResponse(call: Call<ReportRosterResponse>, response: Response<ReportRosterResponse>) {
                setLoading(false); val body = response.body()
                if (!response.isSuccessful || body?.status != "success" || body.config == null) {
                    toast(body?.message ?: "دریافت فهرست نمرات انجام نشد"); return
                }
                config = body.config
                students.clear(); students += body.students.map { s ->
                    EditableStudent(s.id, s.name, s.studentCode, s.cardId, s.status, s.revision,
                        body.config.components.associate { c -> c.id to s.scores[c.id] }.toMutableMap())
                }
                studentDropdown.setAdapter(ArrayAdapter(this@GradeEntryActivity, android.R.layout.simple_dropdown_item_1line, students.map { it.name }))
                studentDropdown.setOnItemClickListener { _, _, position, _ -> syncCurrent(); selectedIndex = position; renderCurrent() }
                if (students.isEmpty()) toast("دانش‌آموزی در سابقه این کلاس ثبت نشده") else renderCurrent()
            }
            override fun onFailure(call: Call<ReportRosterResponse>, t: Throwable) { setLoading(false); toast("ارتباط با سرور برقرار نشد") }
        })
    }

    private fun renderCurrent() {
        val student = students.getOrNull(selectedIndex) ?: return
        val components = config?.components.orEmpty().sortedBy { it.sortOrder }
        studentDropdown.setText(student.name, false)
        studentMeta.text = "کد دانش‌آموزی: ${student.studentCode.ifBlank { "تعیین نشده" }}  •  ${selectedIndex + 1} از ${students.size}"
        scoreContainer.removeAllViews()
        components.forEach { component ->
            val row = LayoutInflater.from(this).inflate(R.layout.item_grade_input_compact, scoreContainer, false)
            row.findViewById<TextView>(R.id.txtCompactCriterion).text = component.title
            row.findViewById<TextView>(R.id.txtCompactMax).text = "از ${format(component.maxScore)}"
            val input = row.findViewById<TextInputEditText>(R.id.etCompactScore)
            student.scores[component.id]?.let { input.setText(format(it)) }
            input.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) { syncCurrent(); updateTotal() } }
            row.findViewById<View>(R.id.btnCompactZero).setOnClickListener { input.setText("0"); syncCurrent(); updateTotal() }
            scoreContainer.addView(row)
        }
        updateTotal()
    }

    private fun syncCurrent() {
        val student = students.getOrNull(selectedIndex) ?: return
        val components = config?.components.orEmpty().sortedBy { it.sortOrder }
        for (i in 0 until scoreContainer.childCount) {
            val component = components.getOrNull(i) ?: continue
            val row = scoreContainer.getChildAt(i)
            val raw = row.findViewById<TextInputEditText>(R.id.etCompactScore).text?.toString()?.trim().orEmpty()
            val value = raw.toDoubleOrNull()
            val input = row.findViewById<TextInputEditText>(R.id.etCompactScore)
            when {
                raw.isBlank() -> { student.scores[component.id] = null; input.error = null }
                value == null || value < 0 || value > component.maxScore -> { student.scores[component.id] = null; input.error = "۰ تا ${format(component.maxScore)}" }
                else -> { student.scores[component.id] = value; input.error = null }
            }
        }
    }

    private fun updateTotal() {
        syncCurrent()
        val student = students.getOrNull(selectedIndex) ?: return
        val entered = student.scores.values.count { it != null }
        val total = student.scores.values.filterNotNull().sum()
        totalText.text = "جمع فعلی: ${format(total)} از ۱۰۰  •  $entered/${config?.components?.size ?: 0} معیار"
    }

    private fun move(delta: Int) {
        if (students.isEmpty()) return
        syncCurrent(); selectedIndex = (selectedIndex + delta).coerceIn(0, students.lastIndex); renderCurrent()
    }

    private fun requestPublish() {
        syncCurrent()
        val incomplete = students.any { s -> config.orEmptyComponents().any { s.scores[it.id] == null } }
        if (incomplete) { toast("برای انتشار، نمرات همه دانش‌آموزان باید کامل باشد"); return }
        val hasPublished = students.any { it.status == "PUBLISHED" }
        if (!hasPublished) {
            MaterialAlertDialogBuilder(this).setTitle("انتشار کارنامه‌ها").setMessage("بعد از انتشار، دانش‌آموزان کارنامه را می‌بینند. ادامه می‌دهید؟")
                .setNegativeButton("انصراف", null).setPositiveButton("انتشار") { _, _ -> save(true, "") }.show()
        } else {
            val input = android.widget.EditText(this).apply { hint = "علت ویرایش کارنامه منتشرشده" }
            MaterialAlertDialogBuilder(this).setTitle("ویرایش نسخه منتشرشده").setMessage("علت ویرایش در سابقه ثبت می‌شود.")
                .setView(input).setNegativeButton("انصراف", null).setPositiveButton("انتشار مجدد") { _, _ ->
                    val reason = input.text.toString().trim(); if (reason.isBlank()) toast("علت ویرایش الزامی است") else save(true, reason)
                }.show()
        }
    }

    private fun save(publish: Boolean, reason: String) {
        syncCurrent()
        val payload = students.map { s -> SaveReportStudentRequest(s.id, s.revision, s.scores.toMap()) }
        setLoading(true)
        RetrofitClient.instance.saveReportCards(SaveReportCardsRequest(classId, publish, reason, payload)).enqueue(object : Callback<ApiResponse> {
            override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                setLoading(false); val body = response.body()
                if (response.isSuccessful && body?.status == "success") { toast(body.message); loadRoster() }
                else toast(body?.message ?: "ذخیره نمرات انجام نشد")
            }
            override fun onFailure(call: Call<ApiResponse>, t: Throwable) { setLoading(false); toast("ارتباط با سرور برقرار نشد") }
        })
    }

    private fun ReportConfigDto?.orEmptyComponents() = this?.components.orEmpty()
    private fun setLoading(v: Boolean) { progress.visibility = if (v) View.VISIBLE else View.GONE; draftButton.isEnabled = !v; publishButton.isEnabled = !v }
    private fun format(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else "%.2f".format(v)
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
    private data class EditableStudent(val id:String,val name:String,val studentCode:String,val cardId:String?,val status:String,var revision:Int,val scores:MutableMap<String,Double?>)

    companion object { const val EXTRA_CLASS_ID = "REPORT_CLASS_ID"; const val EXTRA_CLASS_NAME = "REPORT_CLASS_NAME" }
}
