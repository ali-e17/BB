package com.example.bb

import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import android.content.Intent
import android.os.Bundle
import android.view.View
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

    private lateinit var teacherAdapter: TeacherAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_teacher_management)

        findViewById<ImageView>(R.id.btnTeacherMgmtBack).setOnClickListener { finish() }
        findViewById<FloatingActionButton>(R.id.fabAddTeacher).setOnClickListener {
            startActivity(Intent(this, AddEditTeacherActivity::class.java))
        }

        val recycler = findViewById<RecyclerView>(R.id.rvTeachers)
        recycler.layoutManager = LinearLayoutManager(this)

        teacherAdapter = TeacherAdapter(
            teachers = emptyList(),
            onRowClick = { teacher ->
                if (!teacher.isActive) {
                    Toast.makeText(this, "ابتدا استاد را فعال کنید", Toast.LENGTH_SHORT).show()
                } else {
                    startActivity(
                        Intent(this, AssignClassActivity::class.java)
                            .putExtra(AssignClassActivity.EXTRA_TEACHER_USERNAME, teacher.username)
                    )
                }
            },
            onDetailsClick = ::showTeacherDetailsBottomSheet
        )
        recycler.adapter = teacherAdapter
    }

    override fun onResume() {
        super.onResume()
        // 🌐 دریافت زنده لیست اساتید از سرور
        RetrofitClient.instance.getTeachers().enqueue(object : Callback<List<TeacherModel>> {
            override fun onResponse(call: Call<List<TeacherModel>>, response: Response<List<TeacherModel>>) {
                if (response.isSuccessful) {
                    val teachers = response.body().orEmpty()
                    // در صورت نیاز میتونی متد replaceTeachers هم تو AppDatabase بسازی
                    teacherAdapter.updateData(teachers)
                } else {
                    teacherAdapter.updateData(AppDatabase.getAllTeachers())
                }
            }

            override fun onFailure(call: Call<List<TeacherModel>>, t: Throwable) {
                teacherAdapter.updateData(AppDatabase.getAllTeachers())
                Toast.makeText(this@TeacherManagementActivity, "دریافت آنلاین اساتید ناموفق بود", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showTeacherDetailsBottomSheet(initialTeacher: TeacherModel) {
        var teacher = initialTeacher
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_teacher_details, null)
        dialog.setContentView(view)

        val avatar = view.findViewById<TextView>(R.id.dialogTeacherAvatar)
        val name = view.findViewById<TextView>(R.id.dialogTeacherName)
        val username = view.findViewById<TextView>(R.id.dialogTeacherUsername)
        val classes = view.findViewById<TextView>(R.id.dialogTeacherClass)
        val status = view.findViewById<TextView>(R.id.dialogTeacherStatus)
        val archiveHint = view.findViewById<TextView>(R.id.dialogTeacherArchiveHint)
        val btnArchive = view.findViewById<MaterialButton>(R.id.btnDialogTeacherArchive)
        val btnEdit = view.findViewById<MaterialButton>(R.id.btnDialogTeacherEdit)

        fun render() {
            val assignedClasses = AppDatabase.getTeacherClasses(teacher.username)

            avatar.text = teacher.name.firstOrNull()?.toString() ?: "A"
            name.text = teacher.name
            username.text = "کد ملی: ${teacher.nationalId} | تماس: ${teacher.username}"
            classes.text = if (assignedClasses.isEmpty()) {
                "بدون کلاس فعال"
            } else {
                assignedClasses.joinToString("، ") { it.className }
            }

            status.text = if (teacher.isActive) "فعال" else "بایگانی‌شده"
            status.setTextColor(
                android.graphics.Color.parseColor(if (teacher.isActive) "#10B981" else "#94A3B8")
            )
            btnArchive.text = if (teacher.isActive) "بایگانی کردن" else "فعال‌سازی مجدد"

            val archiveBlocked = teacher.isActive && assignedClasses.isNotEmpty()
            btnArchive.isEnabled = !archiveBlocked
            btnArchive.alpha = if (archiveBlocked) 0.5f else 1f
            archiveHint.visibility = if (archiveBlocked) View.VISIBLE else View.GONE
        }

        render()

        btnArchive.setOnClickListener {
            val success = AppDatabase.toggleTeacherArchiveStatus(teacher.username, this)
            if (!success) {
                Toast.makeText(this, "ابتدا کلاس‌های فعال استاد را حذف کنید", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            teacher = AppDatabase.getTeacherByUsername(teacher.username) ?: teacher
            render()
            teacherAdapter.updateData(AppDatabase.getAllTeachers())
            Toast.makeText(
                this,
                if (teacher.isActive) "استاد فعال شد" else "استاد بایگانی شد",
                Toast.LENGTH_SHORT
            ).show()
        }

        btnEdit.setOnClickListener {
            dialog.dismiss()
            startActivity(
                Intent(this, AddEditTeacherActivity::class.java)
                    .putExtra(AddEditTeacherActivity.EXTRA_TEACHER_USERNAME, teacher.username)
            )
        }

        dialog.show()
    }
}
