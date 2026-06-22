package com.example.bb

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class AssignClassActivity : AppCompatActivity() {

    private lateinit var tvTeacherName: TextView
    private lateinit var spinnerClasses: Spinner
    private lateinit var btnSubmitAssignment: Button
    private var teacherUsername: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_assign_class)

        tvTeacherName = findViewById(R.id.tvTeacherName)
        spinnerClasses = findViewById(R.id.spinnerClasses)
        btnSubmitAssignment = findViewById(R.id.btnSubmitAssignment)

        teacherUsername = intent.getStringExtra("TEACHER_USERNAME") ?: ""
        tvTeacherName.text = "تخصیص کلاس به استاد: $teacherUsername"

        loadAvailableClasses()

        btnSubmitAssignment.setOnClickListener {
            val addedClasses = AppDatabase.getAllCreatedClasses()
            val selectedPosition = spinnerClasses.selectedItemPosition

            // مطمئن می‌شویم کلاسی انتخاب شده و لیست خالی نیست
            if (selectedPosition != -1 && addedClasses.isNotEmpty()) {
                // ۱. استخراج آی‌دی واقعی کلاس بر اساس ردیف انتخاب شده در اسپینر
                val selectedClassId = addedClasses[selectedPosition].id
                val selectedClassName = addedClasses[selectedPosition].className

                // ۲. پاس دادن پارامترها همراه با Context (همان this) برای ذخیره پایدار
                AppDatabase.assignClassToTeacher(teacherUsername, selectedClassId, this)

                Toast.makeText(this, "کلاس $selectedClassName با موفقیت به استاد اختصاص یافت", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this, "کلاسی برای تخصیص وجود ندارد!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadAvailableClasses() {
        // دریافت لیست کلاس‌ها از دیتابیس
        val addedClasses = AppDatabase.getAllCreatedClasses()

        if (addedClasses.isEmpty()) {
            Toast.makeText(this, "هیچ کلاسی در سیستم تعریف نشده است. ابتدا از بخش مدیریت کلاس‌ها اقدام کنید.", Toast.LENGTH_LONG).show()
            btnSubmitAssignment.isEnabled = false
            return
        }

        // 🌟 اصلاح شد: هماهنگ‌سازی با فیلدهای واقعی ClassModel (یعنی className و classTime)
        val classNames = addedClasses.map { "${it.className} (${it.classTime})" }

        // تنظیم آداپتر برای نمایش لیست در Spinner
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, classNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerClasses.adapter = adapter
    }
}