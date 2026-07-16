package com.example.bb

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
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

        @Suppress("DEPRECATION")
        editing = intent.getSerializableExtra("STUDENT_DATA") as? StudentModel
        editing?.let { student ->
            title.text = "ویرایش اطلاعات دانش‌آموز"
            firstName.setText(student.firstName)
            lastName.setText(student.lastName)
            studentCode.setText(student.studentCode)
            phone.setText(student.phone)
            nationalId.setText(student.nationalId)
        }

        findViewById<Button>(R.id.btnSaveStudent).setOnClickListener {
            val fname = firstName.text.toString().trim()
            val lname = lastName.text.toString().trim()
            val codeValue = studentCode.text.toString().trim()
            val phoneValue = phone.text.toString().trim()
            val nationalIdValue = nationalId.text.toString().trim()

            if (fname.isBlank() || lname.isBlank() || phoneValue.isBlank() || nationalIdValue.isBlank()) {
                Toast.makeText(this, "لطفاً فیلدهای ضروری را پر کنید", Toast.LENGTH_SHORT).show(); return@setOnClickListener
            }

            // 🌟 شرط به ۱۰ رقم تغییر کرد
            if (phoneValue.length != 10 || !phoneValue.all(Char::isDigit)) {
                Toast.makeText(this, "شماره تلفن باید دقیقاً ۱۰ رقم باشد (بدون صفر)", Toast.LENGTH_SHORT).show(); return@setOnClickListener
            }
            if (nationalIdValue.length != 10 || !nationalIdValue.all(Char::isDigit)) {
                Toast.makeText(this, "کد ملی باید ۱۰ رقم باشد", Toast.LENGTH_SHORT).show(); return@setOnClickListener
            }

            val old = editing

            // 🌟 ساخت یک نام رندوم بین 1 تا 9 برای دانش‌آموز جدید
            val randomAvatar = "avatar_student_${(1..9).random()}"

            val model = StudentModel(
                id = old?.id ?: UUID.randomUUID().toString(),
                firstName = fname,
                lastName = lname,
                studentCode = codeValue,
                phone = phoneValue,
                nationalId = nationalIdValue,
                password = old?.password ?: nationalIdValue,
                classId = old?.classId,
                registrationDate = old?.registrationDate ?: AppDatabase.today(),
                isActive = old?.isActive ?: true,
                avatarName = old?.avatarName ?: randomAvatar // 🌟 اعمال عکس رندوم
            )

            // 🌐 ارسال آنلاین به سرور با Retrofit
            RetrofitClient.instance.addStudent(model).enqueue(object : Callback<ApiResponse> {
                override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                    if (response.isSuccessful && response.body()?.status == "success") {
                        Toast.makeText(this@AddEditStudentActivity, "دانش‌آموز با موفقیت ثبت شد", Toast.LENGTH_SHORT).show()
                        setResult(RESULT_OK)
                        finish()
                    } else {
                        Toast.makeText(this@AddEditStudentActivity, "خطا در ثبت اطلاعات در سرور", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                    Toast.makeText(this@AddEditStudentActivity, "خطا در اتصال به اینترنت", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }
}