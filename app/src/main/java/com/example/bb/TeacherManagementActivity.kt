package com.example.bb

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton

class TeacherManagementActivity : AppCompatActivity() {

    private lateinit var rvTeachers: RecyclerView
    private lateinit var fabAddTeacher: FloatingActionButton
    private lateinit var teacherAdapter: TeacherAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_teacher_management)

        findViewById<ImageView>(R.id.btnTeacherMgmtBack).setOnClickListener { finish() }

        rvTeachers = findViewById(R.id.rvTeachers)
        fabAddTeacher = findViewById(R.id.fabAddTeacher)
        rvTeachers.layoutManager = LinearLayoutManager(this)

        setupTeacherList()

        // ارجاع به اکتیویتی جدید برای افزودن استاد
        fabAddTeacher.setOnClickListener {
            startActivity(Intent(this, AddEditTeacherActivity::class.java))
        }
    }

    private fun setupTeacherList() {
        teacherAdapter = TeacherAdapter(
            AppDatabase.getAllTeachers(),
            onRowClick = { teacher ->
                // کلیک روی ردیف -> رفتن به صفحه تخصیص کلاس جدید
                val intent = Intent(this, AssignClassActivity::class.java)
                intent.putExtra("TEACHER_USERNAME", teacher.username)
                startActivity(intent)
            },
            onDetailsClick = { teacher ->
                showTeacherDetailsBottomSheet(teacher)
            }
        )
        rvTeachers.adapter = teacherAdapter
    }

    private fun refreshList() {
        teacherAdapter.updateData(AppDatabase.getAllTeachers())
    }

    private fun showTeacherDetailsBottomSheet(teacherArg: TeacherModel) {
        var teacher = teacherArg
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_teacher_details, null)
        dialog.setContentView(view)

        val dialogTeacherAvatar = view.findViewById<TextView>(R.id.dialogTeacherAvatar)
        val dialogTeacherName = view.findViewById<TextView>(R.id.dialogTeacherName)
        val dialogTeacherUsername = view.findViewById<TextView>(R.id.dialogTeacherUsername)
        val dialogTeacherClass = view.findViewById<TextView>(R.id.dialogTeacherClass)
        val tvStatus = view.findViewById<TextView>(R.id.dialogTeacherStatus)
        val archiveHint = view.findViewById<TextView>(R.id.dialogTeacherArchiveHint)
        val btnArchive = view.findViewById<MaterialButton>(R.id.btnDialogTeacherArchive)
        val btnEdit = view.findViewById<MaterialButton>(R.id.btnDialogTeacherEdit)

        fun renderTeacher() {
            dialogTeacherAvatar.text = teacher.name.firstOrNull()?.toString() ?: "A"
            dialogTeacherName.text = teacher.name
            dialogTeacherUsername.text = "کدملی: ${teacher.nationalId} | تماس: ${teacher.username}"

            // پیدا کردن اسامی کلاس‌های استاد
            val assignedClasses = AppDatabase.getTeacherClasses(teacher.username)
            if (assignedClasses.isNotEmpty()) {
                dialogTeacherClass.text = assignedClasses.joinToString("، ") { it.className }
            } else {
                dialogTeacherClass.text = "بدون کلاس تخصیص یافته"
            }

            if (teacher.isActive) {
                tvStatus.text = "فعال"
                tvStatus.setTextColor(android.graphics.Color.parseColor("#10B981"))
                btnArchive.text = "بایگانی کردن"
            } else {
                tvStatus.text = "بایگانی شده"
                tvStatus.setTextColor(android.graphics.Color.GRAY)
                btnArchive.text = "فعال‌سازی مجدد"
            }

            // اگر کلاس داره اجازه بایگانی نده
            if (teacher.isActive && teacher.classIds.isNotEmpty()) {
                btnArchive.isEnabled = false
                btnArchive.alpha = 0.5f
                archiveHint.visibility = android.view.View.VISIBLE
            } else {
                btnArchive.isEnabled = true
                btnArchive.alpha = 1.0f
                archiveHint.visibility = android.view.View.GONE
            }
        }

        renderTeacher()

        btnArchive.setOnClickListener {
            val success = AppDatabase.toggleTeacherArchiveStatus(teacher.username, this)
            if (success) {
                teacher = AppDatabase.getTeacherByUsername(teacher.username) ?: teacher
                renderTeacher()
                refreshList()
                Toast.makeText(this, if (teacher.isActive) "استاد فعال شد" else "استاد بایگانی شد", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "ابتدا باید کلاس‌های این استاد را بگیرید", Toast.LENGTH_SHORT).show()
            }
        }

        btnEdit.setOnClickListener {
            dialog.dismiss()
            // ارجاع به صفحه اکتیویتی ادیت
            val intent = Intent(this, AddEditTeacherActivity::class.java)
            intent.putExtra("TEACHER_USERNAME", teacher.username)
            startActivity(intent)
        }

        dialog.show()
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }
}