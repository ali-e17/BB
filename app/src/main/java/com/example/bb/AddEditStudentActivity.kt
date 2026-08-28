package com.example.bb

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.UUID

class AddEditStudentActivity : BaseActivity() {

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
        val saveButton = findViewById<Button>(R.id.btnSaveStudent)

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

        saveButton.setOnClickListener {
            firstName.error = null
            lastName.error = null
            studentCode.error = null
            phone.error = null
            nationalId.error = null

            val fname = firstName.text.toString().trim()
            val lname = lastName.text.toString().trim()
            val codeValue = studentCode.text.toString().trim()
            val phoneValue = phone.text.toString().trim()
            val nationalIdValue = nationalId.text.toString().trim()

            when {
                fname.isBlank() -> {
                    firstName.error = "نام دانش‌آموز را وارد کنید"
                    firstName.requestFocus()
                    AppToast.warning(this, "برای ذخیره دانش‌آموز، نام را وارد کنید.")
                    return@setOnClickListener
                }

                lname.isBlank() -> {
                    lastName.error = "نام خانوادگی را وارد کنید"
                    lastName.requestFocus()
                    AppToast.warning(this, "برای ذخیره دانش‌آموز، نام خانوادگی را وارد کنید.")
                    return@setOnClickListener
                }

                codeValue.isBlank() -> {
                    studentCode.error = "کد دانش‌آموز را وارد کنید"
                    studentCode.requestFocus()
                    AppToast.warning(this, "کد دانش‌آموز را وارد کنید.")
                    return@setOnClickListener
                }

                !codeValue.matches(Regex("^[0-9]{4}$")) -> {
                    studentCode.error = "کد دانش‌آموز باید دقیقاً ۴ رقم باشد"
                    studentCode.requestFocus()
                    AppToast.warning(this, "کد دانش‌آموز باید دقیقاً ۴ رقم باشد.")
                    return@setOnClickListener
                }

                phoneValue.isBlank() -> {
                    phone.error = "شماره تماس را وارد کنید"
                    phone.requestFocus()
                    AppToast.warning(this, "شماره تماس دانش‌آموز را وارد کنید.")
                    return@setOnClickListener
                }

                phoneValue.length != 10 || !phoneValue.all(Char::isDigit) -> {
                    phone.error = "شماره تماس باید دقیقاً ۱۰ رقم و بدون صفر اول باشد"
                    phone.requestFocus()
                    AppToast.warning(this, "شماره تماس باید دقیقاً ۱۰ رقم و بدون صفر اول باشد.")
                    return@setOnClickListener
                }

                nationalIdValue.isBlank() -> {
                    nationalId.error = "کد ملی را وارد کنید"
                    nationalId.requestFocus()
                    AppToast.warning(this, "کد ملی دانش‌آموز را وارد کنید.")
                    return@setOnClickListener
                }

                nationalIdValue.length != 10 || !nationalIdValue.all(Char::isDigit) -> {
                    nationalId.error = "کد ملی باید دقیقاً ۱۰ رقم باشد"
                    nationalId.requestFocus()
                    AppToast.warning(this, "کد ملی باید دقیقاً ۱۰ رقم باشد.")
                    return@setOnClickListener
                }
            }

            val old = editing
            val isEditing = old != null

            val model = StudentModel(
                id = old?.id ?: UUID.randomUUID().toString(),
                firstName = fname,
                lastName = lname,
                studentCode = codeValue,
                phone = phoneValue,
                nationalId = nationalIdValue,
                password = "",
                classId = old?.classId,
                registrationDate = old?.registrationDate ?: AppDatabase.today(),
                isActive = old?.isActive ?: true,
                avatarName = old?.avatarName ?: "avatar_no_profile"
            )

            saveButton.isEnabled = false

            RetrofitClient.instance.addStudent(model)
                .enqueue(object : Callback<ApiResponse> {
                    override fun onResponse(
                        call: Call<ApiResponse>,
                        response: Response<ApiResponse>
                    ) {
                        saveButton.isEnabled = true
                        val body = response.body()

                        if (response.isSuccessful && body?.status == "success") {
                            val fallback = if (isEditing) {
                                "اطلاعات دانش‌آموز با موفقیت ویرایش شد"
                            } else {
                                "دانش‌آموز با موفقیت افزوده شد"
                            }

                            AppToast.success(
                                this@AddEditStudentActivity,
                                body.message.ifBlank { fallback }
                            )
                            setResult(RESULT_OK)
                            finish()
                        } else {
                            val action = if (isEditing) {
                                "ویرایش اطلاعات دانش‌آموز"
                            } else {
                                "افزودن دانش‌آموز"
                            }

                            AppToast.error(
                                this@AddEditStudentActivity,
                                body?.message?.takeIf { it.isNotBlank() }
                                    ?: ApiErrorParser.userMessage(
                                        response,
                                        "$action کامل نشد"
                                    )
                            )
                        }
                    }

                    override fun onFailure(
                        call: Call<ApiResponse>,
                        t: Throwable
                    ) {
                        saveButton.isEnabled = true
                        val action = if (isEditing) {
                            "ویرایش اطلاعات دانش‌آموز"
                        } else {
                            "افزودن دانش‌آموز"
                        }
                        AppToast.error(
                            this@AddEditStudentActivity,
                            ApiErrorParser.networkMessage(t, action)
                        )
                    }
                })
        }
    }
}
