package com.example.bb

import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.UUID

class AddEditTeacherActivity : AppCompatActivity() {

    private var originalUsername: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_edit_teacher)

        findViewById<ImageView>(R.id.btnTeacherBack).setOnClickListener { finish() }

        // 🌟 اتصال به فیلدهای جدید نام و فامیل
        val etFirstName = findViewById<TextInputEditText>(R.id.etTeacherFirstName)
        val etLastName = findViewById<TextInputEditText>(R.id.etTeacherLastName)
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
            etFirstName.setText(editingTeacher.firstName)
            etLastName.setText(editingTeacher.lastName)
            etPhone.setText(editingTeacher.phone)
            etNationalId.setText(editingTeacher.nationalId)
        } else {
            tvTitle.text = "افزودن استاد جدید"
        }

        btnSave.setOnClickListener {
            val fname = etFirstName.text?.toString()?.trim().orEmpty()
            val lname = etLastName.text?.toString()?.trim().orEmpty()
            val phone = etPhone.text?.toString()?.trim()?.replace(" ", "") ?: ""
            val nationalId = etNationalId.text?.toString()?.trim()?.replace(" ", "") ?: ""

            if (fname.isBlank() || lname.isBlank() || phone.isBlank() || nationalId.isBlank()) {
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

            val formattedPhone = if (phone.startsWith("0")) phone.substring(1) else phone

            val model = TeacherModel(
                id = editingTeacher?.id ?: UUID.randomUUID().toString(),
                firstName = fname,
                lastName = lname,
                phone = formattedPhone, // 🌟 استفاده از شماره تلفن بدون صفر
                nationalId = nationalId,
                password = editingTeacher?.password ?: nationalId,
                classIds = editingTeacher?.classIds.orEmpty(),
                isActive = editingTeacher?.isActive ?: true
            )

            // 🌐 ارسال اطلاعات استاد به سرور
            btnSave.isEnabled = false
            RetrofitClient.instance.addTeacher(model).enqueue(object : Callback<ApiResponse> {
                override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                    btnSave.isEnabled = true
                    if (response.isSuccessful && response.body()?.status == "success") {
                        // آپدیت موقت لوکال برای سرعت بیشتر
                        AppDatabase.addTeacher(model)
                        Toast.makeText(this@AddEditTeacherActivity, if (editingTeacher == null) "استاد ثبت شد" else "اطلاعات استاد ویرایش شد", Toast.LENGTH_SHORT).show()
                        setResult(RESULT_OK)
                        finish()
                    } else {
                        Toast.makeText(this@AddEditTeacherActivity, "خطا در ثبت اطلاعات در سرور", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                    btnSave.isEnabled = true
                    Toast.makeText(this@AddEditTeacherActivity, "خطا در اتصال به اینترنت", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    companion object {
        const val EXTRA_TEACHER_USERNAME = "TEACHER_USERNAME"
    }
}