package com.example.bb

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class TeacherManagementActivity : AppCompatActivity() {

    private lateinit var rvTeachers: RecyclerView
    private lateinit var btnAddTeacher: Button
    private lateinit var teacherAdapter: TeacherAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_teacher_management)

        rvTeachers = findViewById(R.id.rvTeachers)
        btnAddTeacher = findViewById(R.id.btnAddTeacher)

        rvTeachers.layoutManager = LinearLayoutManager(this)

        setupTeacherList()

        // ＋ منطق افزودن استاد جدید با دیالوگ ورودی
        btnAddTeacher.setOnClickListener {
            showAddTeacherDialog()
        }
    }

    private fun setupTeacherList() {
        val teachersList = AppDatabase.getAllTeachers()

        // تنظیم آداپتر با دو لیسنر: یکی برای کلیک معمولی و یکی برای لانگ‌کلیک (حذف)
        teacherAdapter = TeacherAdapter(
            teachersList,
            onItemClick = { clickedTeacher ->
                // کلیک معمولی: هدایت به صفحه تخصیص کلاس
                val intent = Intent(this, AssignClassActivity::class.java)
                intent.putExtra("TEACHER_USERNAME", clickedTeacher.username)
                startActivity(intent)
            },
            onItemLongClick = { teacherToDelete ->
                // لانگ کلیک: نمایش دیالوگ تایید حذف استاد
                showDeleteConfirmationDialog(teacherToDelete)
            }
        )

        rvTeachers.adapter = teacherAdapter
    }

    // متد بروزرسانی سریع لیست بعد از تغییرات
    private fun refreshList() {
        teacherAdapter.updateData(AppDatabase.getAllTeachers())
    }

    // دیالوگ دریافت اطلاعات استاد جدید
    private fun showAddTeacherDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("افزودن استاد جدید")

        // ساخت یک لایه پویای ساده برای گرفتن نام و نام کاربری
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }

        val etName = EditText(this).apply { hint = "نام و نام خانوادگی استاد" }
        val etUsername = EditText(this).apply { hint = "نام کاربری (شماره تماس)" }

        layout.addView(etName)
        layout.addView(etUsername)
        builder.setView(layout)

        builder.setPositiveButton("ذخیره") { _, _ ->
            val name = etName.text.toString().trim()
            val username = etUsername.text.toString().trim()

            if (name.isNotEmpty() && username.isNotEmpty()) {
                // اضافه کردن به دیتابیس لوکال Models.kt
                val newTeacher = TeacherModel(name, username, "1234", null)
                AppDatabase.addTeacher(newTeacher, this)
                refreshList() // بروزرسانی لیست در صفحه
                Toast.makeText(this, "استاد با موفقیت اضافه شد", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "لطفاً تمام فیلدها را پر کنید", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("انصراف", null)
        builder.show()
    }

    // دیالوگ تایید حذف استاد
    private fun showDeleteConfirmationDialog(teacher: TeacherModel) {
        AlertDialog.Builder(this)
            .setTitle("حذف استاد")
            .setMessage("آیا از حذف استاد «${teacher.name}» مطمئن هستید؟")
            .setPositiveButton("بله، حذف شود") { _, _ ->
                AppDatabase.deleteTeacher(teacher.username, this)
                refreshList()
                Toast.makeText(this, "استاد حذف شد", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("خیر", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        // وقتی از صفحه تخصیص کلاس برمی‌گردیم لیست دوباره لود شود تا تغییرات احتمالی اعمال شده باشند
        refreshList()
    }
}