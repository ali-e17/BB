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

            if (isEditMode) {
                // آپدیت استاد (باید متد آپدیت رو تو دیتابیس هماهنگ کنی)
                val teacher = AppDatabase.getTeacherByUsername(originalUsername)
                if (teacher != null) {
                    teacher.name = name
                    teacher.username = phone
                    teacher.nationalId = nationalId
                    // اگر شماره عوض شده، باید حواست باشه تو دیتابیس کلیدش عوض بشه
                    // (ساده‌ترین راه: متد اختصاصی آپدیت بنویسی)
                    AppDatabase.addTeacher(teacher, this) // در حالت ساده اوررایت میشه
                    Toast.makeText(this, "ویرایش انجام شد", Toast.LENGTH_SHORT).show()
                }
            } else {
                // ساخت استاد جدید: کدملی میشه رمز عبورش
                val newTeacher = TeacherModel(name, phone, nationalId, password = nationalId)
                AppDatabase.addTeacher(newTeacher, this)
                Toast.makeText(this, "استاد با موفقیت ثبت شد", Toast.LENGTH_SHORT).show()
            }
            finish()
        }
    }
}