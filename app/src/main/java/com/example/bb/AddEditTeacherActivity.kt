package com.example.bb

import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText

class AddEditTeacherActivity : AppCompatActivity() {

    private var isEditMode = false
    private var originalUsername = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        findViewById<ImageView>(R.id.btnTeacherBack).setOnClickListener { finish() }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_edit_teacher) // لایوتی که ساختی رو بده

        // (فرض بر اینه که آیدی‌های فیلدها اینا هستن، خودت تو XML مچشون کن)
        val etName = findViewById<TextInputEditText>(R.id.etTeacherName)
        val etPhone = findViewById<TextInputEditText>(R.id.etTeacherPhone)
        val etNationalId = findViewById<TextInputEditText>(R.id.etTeacherNationalId)
        val btnSave = findViewById<Button>(R.id.btnSaveTeacher)
        val tvTitle = findViewById<TextView>(R.id.tvTitleAddEdit)

        originalUsername = intent.getStringExtra("TEACHER_USERNAME") ?: ""
        isEditMode = originalUsername.isNotEmpty()

        if (isEditMode) {
            tvTitle.text = "ویرایش اطلاعات استاد"
            val teacher = AppDatabase.getTeacherByUsername(originalUsername)
            teacher?.let {
                etName.setText(it.name)
                etPhone.setText(it.username)
                etNationalId.setText(it.nationalId)
            }
        } else {
            tvTitle.text = "افزودن استاد جدید"
        }

        btnSave.setOnClickListener {
            val name = etName.text.toString().trim()
            val phone = etPhone.text.toString().trim()
            val nationalId = etNationalId.text.toString().trim()

            if (name.isEmpty() || phone.isEmpty() || nationalId.isEmpty()) {
                Toast.makeText(this, "لطفاً تمام فیلدها را پر کنید", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (phone.length != 11 || !phone.all(Char::isDigit) || nationalId.length != 10 || !nationalId.all(Char::isDigit)) {
                Toast.makeText(this, "شماره تلفن باید ۱۱ رقم و کد ملی ۱۰ رقم باشد", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (isEditMode) {
                val teacher = AppDatabase.getTeacherByUsername(originalUsername)
                if (teacher != null) {
                    val updatedTeacher = teacher.copy(name = name, username = phone, nationalId = nationalId)
                    val error = AppDatabase.upsertTeacher(updatedTeacher, originalUsername)
                    if (error != null) { Toast.makeText(this, error, Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                    Toast.makeText(this, "ویرایش انجام شد", Toast.LENGTH_SHORT).show()
                }
            } else {
                val newTeacher = TeacherModel(name, phone, nationalId, password = nationalId)
                val error = AppDatabase.upsertTeacher(newTeacher)
                if (error != null) { Toast.makeText(this, error, Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                Toast.makeText(this, "استاد با موفقیت ثبت شد", Toast.LENGTH_SHORT).show()
            }
            finish()
        }
    }
}
