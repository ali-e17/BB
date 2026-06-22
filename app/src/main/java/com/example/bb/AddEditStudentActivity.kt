package com.example.bb

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class AddEditStudentActivity : AppCompatActivity() {

    private var editingStudent: Student? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_edit_student)

        val txtFormTitle = findViewById<TextView>(R.id.txtFormTitle)
        val etFirstName = findViewById<EditText>(R.id.etFirstName)
        val etLastName = findViewById<EditText>(R.id.etLastName)
        val etStudentCode = findViewById<EditText>(R.id.etStudentCode)
        val etPhone = findViewById<EditText>(R.id.etPhone)
        val spinnerFormLevel = findViewById<AutoCompleteTextView>(R.id.spinnerFormLevel)
        val btnSaveStudent = findViewById<Button>(R.id.btnSaveStudent)
        findViewById<ImageView>(R.id.btnFormBack).setOnClickListener { finish() }

        // تنظیم گزینه‌های دراپ‌داون (رفع ارور val cannot be reassigned)
        val levels = listOf("FF1", "FF2", "FF3", "FF4")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, levels)
        spinnerFormLevel.setAdapter(adapter)

        // بررسی اینکه آیا برای ویرایش آمدیم یا ثبت‌نام جدید
        @Suppress("DEPRECATION")
        editingStudent = intent.getSerializableExtra("STUDENT_DATA") as? Student
        if (editingStudent != null) {
            txtFormTitle.text = "ویرایش اطلاعات دانش‌آموز"
            editingStudent?.let {
                etFirstName.setText(it.firstName)
                etLastName.setText(it.lastName)
                etStudentCode.setText(it.studentCode)
                etPhone.setText(it.phoneNumber)
                // روش درست مقداردهی به AutoCompleteTextView در حالت ویرایش
                spinnerFormLevel.setText(it.level, false)
            }
        }

        btnSaveStudent.setOnClickListener {
            val fName = etFirstName.text.toString().trim()
            val lName = etLastName.text.toString().trim()
            val code = etStudentCode.text.toString().trim()
            val phone = etPhone.text.toString().trim()
            // روش درست دریافت متن از AutoCompleteTextView
            val selectedLevel = spinnerFormLevel.text.toString()

            if (fName.isEmpty() || lName.isEmpty() || code.isEmpty() || phone.isEmpty() || selectedLevel.isEmpty()) {
                Toast.makeText(this, "لطفاً تمام فیلدها را پر کنید", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val resultIntent = Intent()
            if (editingStudent != null) {
                // آپدیت دانش‌آموز فعلی
                editingStudent?.apply {
                    firstName = fName
                    lastName = lName
                    studentCode = code
                    phoneNumber = phone
                    level = selectedLevel
                }
                resultIntent.putExtra("RESULT_STUDENT", editingStudent)
                resultIntent.putExtra("IS_EDIT", true)
            } else {
                // ساخت دانش‌آموز جدید
                val newStudent = Student(
                    id = System.currentTimeMillis().toString(),
                    firstName = fName,
                    lastName = lName,
                    studentCode = code,
                    level = selectedLevel,
                    phoneNumber = phone,
                    registrationDate = "1404/06/05",
                    isActive = true,
                    avatarResId = R.drawable.avatar_student_1
                )
                resultIntent.putExtra("RESULT_STUDENT", newStudent)
                resultIntent.putExtra("IS_EDIT", false)
            }

            setResult(RESULT_OK, resultIntent)
            finish()
        }
    }
}