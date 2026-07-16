package com.example.bb

import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText

class AddEditTeacherActivity : AppCompatActivity() {

    private var originalUsername: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_edit_teacher)

        findViewById<ImageView>(R.id.btnTeacherBack).setOnClickListener { finish() }

        val etName = findViewById<TextInputEditText>(R.id.etTeacherName)
        val etPhone = findViewById<TextInputEditText>(R.id.etTeacherPhone)
        val etNationalId = findViewById<TextInputEditText>(R.id.etTeacherNationalId)
        val btnSave = findViewById<Button>(R.id.btnSaveTeacher)
        val tvTitle = findViewById<TextView>(R.id.tvTitleAddEdit)

        originalUsername = intent.getStringExtra(EXTRA_TEACHER_USERNAME).orEmpty()
        val editingTeacher = originalUsername
            .takeIf { it.isNotBlank() }
            ?.let(AppDatabase::getTeacherByUsername)

        if (editingTeacher != null) {
            tvTitle.text = "ویرایش اطلاعات استاد"
            etName.setText(editingTeacher.name)
            etPhone.setText(editingTeacher.username)
            etNationalId.setText(editingTeacher.nationalId)
        } else {
            tvTitle.text = "افزودن استاد جدید"
        }

        btnSave.setOnClickListener {
            val name = etName.text?.toString()?.trim().orEmpty()
            val phone = ClassTimeUtils.normalizeDigits(etPhone.text?.toString().orEmpty()).trim()
            val nationalId = ClassTimeUtils.normalizeDigits(etNationalId.text?.toString().orEmpty()).trim()

            if (name.isBlank() || phone.isBlank() || nationalId.isBlank()) {
                Toast.makeText(this, "لطفاً تمام فیلدها را پر کنید", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (phone.length != 11 || !phone.all(Char::isDigit)) {
                etPhone.error = "شماره تلفن باید ۱۱ رقم باشد"
                return@setOnClickListener
            }
            if (nationalId.length != 10 || !nationalId.all(Char::isDigit)) {
                etNationalId.error = "کد ملی باید ۱۰ رقم باشد"
                return@setOnClickListener
            }

            val model = TeacherModel(
                name = name,
                username = phone,
                nationalId = nationalId,
                password = editingTeacher?.password ?: nationalId,
                classIds = editingTeacher?.classIds.orEmpty(),
                isActive = editingTeacher?.isActive ?: true
            )

            val error = AppDatabase.upsertTeacher(
                teacher = model,
                originalPhone = editingTeacher?.username
            )
            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            Toast.makeText(
                this,
                if (editingTeacher == null) "استاد ثبت شد" else "اطلاعات استاد ویرایش شد",
                Toast.LENGTH_SHORT
            ).show()
            setResult(RESULT_OK)
            finish()
        }
    }

    companion object {
        const val EXTRA_TEACHER_USERNAME = "TEACHER_USERNAME"
    }
}
