package com.example.bb

import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class GradeEntryActivity : AppCompatActivity() {

    private lateinit var btnPublish: Button
    private lateinit var students: List<StudentGrade>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_grade_entry)

        findViewById<ImageView>(R.id.btnEntryBack).setOnClickListener { finish() }
        btnPublish = findViewById(R.id.btnPublishLayout)

        // دریافت اسم کلاس و قرار دادن در تیتر صفحه
        val selectedClass = intent.getStringExtra("SELECTED_CLASS") ?: "نامشخص"
        val selectedClassId = intent.getStringExtra("SELECTED_CLASS_ID") ?: ""
        val txtClassTitle = findViewById<TextView>(R.id.txtClassTitle)
        txtClassTitle.text = "ورود نمرات: $selectedClass"

        // دریافت فیلدهایی که در صفحه قبل تیک خورده بودند
        val activeCriteria = intent.getSerializableExtra("ACTIVE_CRITERIA") as? ArrayList<GradeComponent> ?: emptyList()

        students = AppDatabase.getStudentsInClass(selectedClassId).map { StudentGrade(it.id, it.name) }

        val recycler = findViewById<RecyclerView>(R.id.recyclerStudents)
        recycler.layoutManager = LinearLayoutManager(this)

        recycler.adapter = StudentGradeAdapter(students, activeCriteria) {
            checkPublishEligibility()
        }

        btnPublish.setOnClickListener {
            AppDatabase.publishReportCards(selectedClassId, activeCriteria, students)
            Toast.makeText(this, "کارنامه‌ها با موفقیت برای کلاس $selectedClass منتشر شد!", Toast.LENGTH_LONG).show()
            finish()
        }
        checkPublishEligibility()
    }

    private fun checkPublishEligibility() {
        val allCompleted = students.isNotEmpty() && students.all { it.status == EntryStatus.COMPLETED }

        btnPublish.isEnabled = allCompleted
        btnPublish.alpha = if (allCompleted) 1.0f else 0.5f
    }
}
