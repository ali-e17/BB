package com.example.bb

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class AttendanceActivity : AppCompatActivity() {
    private lateinit var recycler: RecyclerView
    private lateinit var stats: TextView
    private lateinit var dates: LinearLayout
    private lateinit var saveButton: Button
    private var records = mutableListOf<AttendanceRecord>()
    private var selectedClass: ClassModel? = null
    private var selectedDate = ""
    private var teacherPhone = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_attendance)
        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }
        recycler = findViewById(R.id.rvAttendance)
        stats = findViewById(R.id.txtLiveStats)
        dates = findViewById(R.id.containerDates)
        saveButton = findViewById(R.id.btnSaveAttendance)
        recycler.layoutManager = LinearLayoutManager(this)

        val prefs = getSharedPreferences("LocalAppPrefs", Context.MODE_PRIVATE)
        teacherPhone = prefs.getString("CURRENT_USERNAME", "").orEmpty()
        val role = prefs.getString("CURRENT_USER_ROLE", "TEACHER")
        val availableClasses = if (role == UserRole.ADMIN.name) AppDatabase.getAllClasses(false)
        else AppDatabase.getTeacherClasses(teacherPhone)
        val spinner = findViewById<AutoCompleteTextView>(R.id.spinnerClass)
        spinner.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, availableClasses.map { it.className }))
        spinner.setOnItemClickListener { _, _, position, _ -> selectClass(availableClasses[position]) }
        if (availableClasses.isNotEmpty()) {
            spinner.setText(availableClasses.first().className, false)
            selectClass(availableClasses.first())
        } else {
            stats.text = "هیچ کلاس فعالی به شما تخصیص داده نشده است"
            saveButton.isEnabled = false
        }

        saveButton.setOnClickListener {
            val model = selectedClass ?: return@setOnClickListener
            AlertDialog.Builder(this)
                .setTitle("ثبت نهایی حضور و غیاب")
                .setMessage("پس از ثبت، این لیست قابل ویرایش نیست. مطمئن هستید؟")
                .setPositiveButton("ثبت نهایی") { _, _ ->
                    val saved = AppDatabase.finalizeAttendance(model.id, selectedDate, teacherPhone,
                        records.map { AttendanceItem(it.student.id, it.status, it.delayMinutes) })
                    if (saved) {
                        Toast.makeText(this, "ثبت شد؛ اعلان غیبت و تأخیر به‌صورت خودکار ساخته شد", Toast.LENGTH_LONG).show()
                        loadDate(selectedDate)
                    } else Toast.makeText(this, "این جلسه قبلاً ثبت نهایی شده است", Toast.LENGTH_SHORT).show()
                }.setNegativeButton("انصراف", null).show()
        }
    }

    private fun selectClass(model: ClassModel) {
        selectedClass = model
        selectedDate = AppDatabase.today()
        renderDates(model)
        loadDate(selectedDate)
    }

    private fun renderDates(model: ClassModel) {
        dates.removeAllViews()
        val allDates = (listOf(AppDatabase.today()) + AppDatabase.getAttendanceHistory(model.id).map { it.date }).distinct()
        allDates.forEach { date ->
            dates.addView(TextView(this).apply {
                text = if (date == AppDatabase.today()) "$date (امروز)" else date
                textSize = 13f; setPadding(32, 18, 32, 18)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(8, 0, 8, 0) }
                background = GradientDrawable().apply { cornerRadius = 28f; setColor(if (date == selectedDate) Color.parseColor("#FF6E14") else Color.parseColor("#E5E7EB")) }
                setTextColor(if (date == selectedDate) Color.WHITE else Color.parseColor("#1D2939"))
                setOnClickListener { selectedDate = date; renderDates(model); loadDate(date) }
            })
        }
    }

    private fun loadDate(date: String) {
        val model = selectedClass ?: return
        val saved = AppDatabase.getAttendance(model.id, date)
        records = AppDatabase.getStudentsInClass(model.id).map { student ->
            val item = saved?.items?.find { it.studentId == student.id }
            AttendanceRecord(student, item?.status ?: AttendanceStatus.PRESENT, item?.delayMinutes ?: 0, saved != null)
        }.toMutableList()
        recycler.adapter = AttendanceAdapter(records) { updateStats() }
        saveButton.isEnabled = saved == null && records.isNotEmpty() && date == AppDatabase.today()
        saveButton.text = if (saved == null) "ثبت نهایی حضور و غیاب" else "ثبت نهایی شده (غیرقابل ویرایش)"
        updateStats()
    }

    private fun updateStats() {
        stats.text = "حاضر: ${records.count { it.status == AttendanceStatus.PRESENT }} | غایب: ${records.count { it.status == AttendanceStatus.ABSENT }} | تأخیر: ${records.count { it.status == AttendanceStatus.LATE }}"
    }
}
