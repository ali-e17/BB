package com.example.bb

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.textfield.TextInputEditText
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.UUID

class AddEditTeacherActivity : BaseActivity() {

    private var originalUsername = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_edit_teacher)

        findViewById<ImageView>(R.id.btnTeacherBack).setOnClickListener { finish() }

        val first = findViewById<TextInputEditText>(R.id.etTeacherFirstName)
        val last = findViewById<TextInputEditText>(R.id.etTeacherLastName)
        val phone = findViewById<TextInputEditText>(R.id.etTeacherPhone)
        val national = findViewById<TextInputEditText>(R.id.etTeacherNationalId)
        val save = findViewById<Button>(R.id.btnSaveTeacher)
        val progress = findViewById<View>(R.id.progressSavingTeacher)
        val title = findViewById<TextView>(R.id.tvTitleAddEdit)

        originalUsername = intent.getStringExtra(EXTRA_TEACHER_USERNAME).orEmpty()
        val editing = originalUsername
            .takeIf { it.isNotBlank() }
            ?.let(AppDatabase::getTeacherByUsername)

        if (editing != null) {
            title.text = "ویرایش اطلاعات استاد"
            first.setText(editing.firstName)
            last.setText(editing.lastName)
            phone.setText("0${editing.phone.removePrefix("0")}")
            national.setText(editing.nationalId)
            save.text = "ذخیره تغییرات"
        }

        save.setOnClickListener {
            first.error = null
            last.error = null
            phone.error = null
            national.error = null

            val firstValue = first.text?.toString()?.trim().orEmpty()
            val lastValue = last.text?.toString()?.trim().orEmpty()
            val phoneValue = phone.text?.toString()?.replace(" ", "")?.trim().orEmpty()
            val nationalValue = national.text?.toString()?.trim().orEmpty()

            when {
                firstValue.isBlank() -> {
                    first.error = "نام را وارد کنید"
                    first.requestFocus()
                    AppToast.warning(this, "برای ذخیره استاد، نام را وارد کنید.")
                }

                lastValue.isBlank() -> {
                    last.error = "نام خانوادگی را وارد کنید"
                    last.requestFocus()
                    AppToast.warning(this, "برای ذخیره استاد، نام خانوادگی را وارد کنید.")
                }

                phoneValue.isBlank() -> {
                    phone.error = "شماره تماس را وارد کنید"
                    phone.requestFocus()
                    AppToast.warning(this, "شماره تماس استاد را وارد کنید.")
                }

                phoneValue.length !in 10..11 || !phoneValue.all(Char::isDigit) -> {
                    phone.error = "شماره تماس معتبر نیست"
                    phone.requestFocus()
                    AppToast.warning(
                        this,
                        "شماره تماس باید ۱۰ رقم بدون صفر اول یا ۱۱ رقم با صفر اول باشد."
                    )
                }

                nationalValue.isBlank() -> {
                    national.error = "کد ملی را وارد کنید"
                    national.requestFocus()
                    AppToast.warning(this, "کد ملی استاد را وارد کنید.")
                }

                nationalValue.length != 10 || !nationalValue.all(Char::isDigit) -> {
                    national.error = "کد ملی باید ۱۰ رقم باشد"
                    national.requestFocus()
                    AppToast.warning(this, "کد ملی باید دقیقاً ۱۰ رقم باشد.")
                }

                else -> {
                    val normalizedPhone = phoneValue.removePrefix("0")
                    val isEditing = editing != null

                    val model = TeacherModel(
                        id = editing?.id ?: UUID.randomUUID().toString(),
                        firstName = firstValue,
                        lastName = lastValue,
                        phone = normalizedPhone,
                        nationalId = nationalValue,
                        password = "",
                        isActive = editing?.isActive ?: true,
                        classIds = editing?.classIds.orEmpty(),
                        avatarName = editing?.avatarName ?: "avatar_no_profile"
                    )

                    setSaving(save, progress, true)

                    RetrofitClient.instance.addTeacher(model)
                        .enqueue(object : Callback<ApiResponse> {
                            override fun onResponse(
                                call: Call<ApiResponse>,
                                response: Response<ApiResponse>
                            ) {
                                setSaving(save, progress, false)
                                val body = response.body()

                                if (response.isSuccessful && body?.status == "success") {
                                    AppDatabase.upsertTeacher(
                                        model,
                                        originalPhone = originalUsername.takeIf { it.isNotBlank() }
                                    )

                                    val fallback = if (isEditing) {
                                        "اطلاعات استاد با موفقیت ویرایش شد"
                                    } else {
                                        "استاد با موفقیت افزوده شد"
                                    }

                                    AppToast.success(
                                        this@AddEditTeacherActivity,
                                        body.message.ifBlank { fallback }
                                    )
                                    finish()
                                } else {
                                    val action = if (isEditing) {
                                        "ویرایش اطلاعات استاد"
                                    } else {
                                        "افزودن استاد"
                                    }

                                    AppToast.error(
                                        this@AddEditTeacherActivity,
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
                                setSaving(save, progress, false)
                                val action = if (isEditing) {
                                    "ویرایش اطلاعات استاد"
                                } else {
                                    "افزودن استاد"
                                }
                                AppToast.error(
                                    this@AddEditTeacherActivity,
                                    ApiErrorParser.networkMessage(t, action)
                                )
                            }
                        })
                }
            }
        }
    }

    private fun setSaving(
        button: Button,
        progress: View,
        saving: Boolean
    ) {
        button.isEnabled = !saving
        button.alpha = if (saving) 0.55f else 1f
        progress.visibility = if (saving) View.VISIBLE else View.GONE
    }

    companion object {
        const val EXTRA_TEACHER_USERNAME = "TEACHER_USERNAME"
    }
}
