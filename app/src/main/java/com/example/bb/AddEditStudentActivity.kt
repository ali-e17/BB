package com.example.bb

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.UUID

class AddEditStudentActivity : AppCompatActivity() {
    private var editing: StudentModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_edit_student)
        findViewById<ImageView>(R.id.btnFormBack).setOnClickListener { finish() }

        val title = findViewById<TextView>(R.id.txtFormTitle)
        val firstName = findViewById<EditText>(R.id.etFirstName)
        val lastName = findViewById<EditText>(R.id.etLastName)
        val studentCode = findViewById<EditText>(R.id.etStudentCode)
        val phone = findViewById<EditText>(R.id.etPhone)
        val nationalId = findViewById<EditText>(R.id.etNationalId)
        val classDropdown = findViewById<AutoCompleteTextView>(R.id.spinnerFormLevel)
        val activeClasses = AppDatabase.getAllClasses(false)
        val classLabels = listOf("بدون کلاس") + activeClasses.map { it.className }
        classDropdown.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, classLabels))

        @Suppress("DEPRECATION")
        editing = intent.getSerializableExtra("STUDENT_DATA") as? StudentModel
        editing?.let { student ->
            title.text = "ویرایش اطلاعات دانش‌آموز"
            val parts = student.name.trim().split(Regex("\\s+"), limit = 2)
            firstName.setText(parts.firstOrNull().orEmpty())
            lastName.setText(parts.getOrNull(1).orEmpty())
            studentCode.setText(student.studentCode)
            phone.setText(student.phone)
            nationalId.setText(student.nationalId)
            classDropdown.setText(AppDatabase.getClassNameById(student.classId) ?: "بدون کلاس", false)
        } ?: classDropdown.setText("بدون کلاس", false)

        findViewById<Button>(R.id.btnSaveStudent).setOnClickListener {
            val fullName = "${firstName.text.toString().trim()} ${lastName.text.toString().trim()}".trim()
            val codeValue = studentCode.text.toString().trim()
            val phoneValue = phone.text.toString().trim()
            val nationalIdValue = nationalId.text.toString().trim()
            if (fullName.isBlank() || codeValue.isBlank() || phoneValue.isBlank() || nationalIdValue.isBlank()) {
                Toast.makeText(this, "لطفاً تمام فیلدها را پر کنید", Toast.LENGTH_SHORT).show(); return@setOnClickListener
            }
            if (phoneValue.length != 11 || !phoneValue.all(Char::isDigit)) {
                Toast.makeText(this, "شماره تلفن باید ۱۱ رقم باشد", Toast.LENGTH_SHORT).show(); return@setOnClickListener
            }
            if (nationalIdValue.length != 10 || !nationalIdValue.all(Char::isDigit)) {
                Toast.makeText(this, "کد ملی باید ۱۰ رقم باشد", Toast.LENGTH_SHORT).show(); return@setOnClickListener
            }
            val classId = activeClasses.find { it.className == classDropdown.text.toString() }?.id
            val old = editing
            val model = StudentModel(
                id = old?.id ?: UUID.randomUUID().toString(), name = fullName, studentCode = codeValue,
                phone = phoneValue, nationalId = nationalIdValue,
                password = old?.password ?: nationalIdValue, classId = old?.classId,
                registrationDate = old?.registrationDate ?: AppDatabase.today(),
                isActive = old?.isActive ?: true, avatarResId = old?.avatarResId ?: R.drawable.avatar_student_1
            )
            val error = AppDatabase.upsertStudent(model, old?.phone)
            if (error != null) { Toast.makeText(this, error, Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            if (model.classId != classId) AppDatabase.assignClassToStudent(model.phone, classId)
            Toast.makeText(this, if (old == null) "دانش‌آموز ثبت شد؛ رمز اولیه کد ملی است" else "اطلاعات بروزرسانی شد", Toast.LENGTH_LONG).show()
            setResult(RESULT_OK); finish()
        }
    }
}
