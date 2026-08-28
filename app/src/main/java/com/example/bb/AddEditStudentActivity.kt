package com.example.bb

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.UUID

class AddEditStudentActivity : BaseActivity() {

    private var editing: StudentModel? = null
    private var initializingIdentityUi = true
    private var isGeneratingForeignCode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_edit_student)

        findViewById<ImageView>(R.id.btnFormBack).setOnClickListener { finish() }

        val title = findViewById<TextView>(R.id.txtFormTitle)
        val firstName = findViewById<EditText>(R.id.etFirstName)
        val lastName = findViewById<EditText>(R.id.etLastName)
        val studentCode = findViewById<EditText>(R.id.etStudentCode)
        val phone = findViewById<EditText>(R.id.etPhone)

        val layoutNationalId = findViewById<TextInputLayout>(R.id.layoutNationalId)
        val nationalId = findViewById<EditText>(R.id.etNationalId)
        val cbIsForeign = findViewById<MaterialCheckBox>(R.id.cbIsForeign)

        val foreignSection = findViewById<LinearLayout>(R.id.foreignIdentitySection)
        val layoutForeignCode = findViewById<TextInputLayout>(R.id.layoutForeignCode)
        val foreignCode = findViewById<EditText>(R.id.etForeignCode)
        val cbNoForeignCode = findViewById<MaterialCheckBox>(R.id.cbNoForeignCode)
        val foreignAutoHint = findViewById<TextView>(R.id.txtForeignAutoHint)

        val saveButton = findViewById<Button>(R.id.btnSaveStudent)

        @Suppress("DEPRECATION")
        editing = intent.getSerializableExtra("STUDENT_DATA") as? StudentModel

        fun updateIdentityUi() {
            val isForeign = cbIsForeign.isChecked
            val noForeignCode = cbNoForeignCode.isChecked

            layoutNationalId.isEnabled = !isForeign
            nationalId.isEnabled = !isForeign
            nationalId.alpha = if (isForeign) 0.55f else 1f

            foreignSection.visibility = if (isForeign) View.VISIBLE else View.GONE

            // وقتی «کد اتباع ندارد» فعال است فیلد قابل ویرایش نیست،
            // اما مقدار تولیدشده داخل همین فیلد کاملاً قابل مشاهده می‌ماند.
            layoutForeignCode.isEnabled = isForeign
            foreignCode.isEnabled = isForeign && !noForeignCode
            foreignCode.alpha = if (isForeign) 1f else 0.55f

            layoutForeignCode.hint = if (noForeignCode) {
                "شناسه اتباع تولیدشده (۱۲ رقم)"
            } else {
                "کد اتباع (۱۲ رقم)"
            }

            foreignAutoHint.visibility =
                if (isForeign && noForeignCode) View.VISIBLE else View.GONE
        }

        editing?.let { student ->
            title.text = "ویرایش اطلاعات دانش‌آموز"
            firstName.setText(student.firstName)
            lastName.setText(student.lastName)
            studentCode.setText(student.studentCode)
            phone.setText(student.phone)

            val isForeign = student.identityType.equals("FOREIGN", ignoreCase = true)
            cbIsForeign.isChecked = isForeign

            if (isForeign) {
                nationalId.setText("")
                foreignCode.setText(student.foreignCode)
                cbNoForeignCode.isChecked = student.foreignCodeGenerated
            } else {
                nationalId.setText(student.nationalId)
                foreignCode.setText("")
                cbNoForeignCode.isChecked = false
            }
        }

        updateIdentityUi()
        initializingIdentityUi = false

        fun requestGeneratedForeignCode() {
            if (isGeneratingForeignCode) return

            isGeneratingForeignCode = true
            saveButton.isEnabled = false
            cbNoForeignCode.isEnabled = false
            foreignCode.setText("")
            foreignAutoHint.text = "در حال ساخت شناسه ۱۲ رقمی یکتا..."

            RetrofitClient.instance.generateForeignCode()
                .enqueue(object : Callback<GenerateForeignCodeResponse> {
                    override fun onResponse(
                        call: Call<GenerateForeignCodeResponse>,
                        response: Response<GenerateForeignCodeResponse>
                    ) {
                        isGeneratingForeignCode = false
                        saveButton.isEnabled = true
                        cbNoForeignCode.isEnabled = true

                        val body = response.body()
                        val generated = normalizeDigits(body?.foreignCode.orEmpty())

                        if (
                            response.isSuccessful &&
                            body?.status == "success" &&
                            generated.matches(Regex("^[0-9]{12}$"))
                        ) {
                            foreignCode.setText(generated)
                            foreignCode.setSelection(foreignCode.text.length)
                            foreignAutoHint.text =
                                "این شناسه به‌عنوان نام کاربری دانش‌آموز استفاده می‌شود."

                            AppToast.success(
                                this@AddEditStudentActivity,
                                "شناسه ۱۲ رقمی اتباع ساخته شد و داخل فیلد نمایش داده شد"
                            )
                        } else {
                            initializingIdentityUi = true
                            cbNoForeignCode.isChecked = false
                            initializingIdentityUi = false
                            foreignCode.setText("")
                            foreignAutoHint.text =
                                "با فعال کردن این گزینه، شناسه ۱۲ رقمی یکتا داخل همین فیلد نمایش داده می‌شود."
                            updateIdentityUi()

                            AppToast.error(
                                this@AddEditStudentActivity,
                                body?.message?.takeIf { it.isNotBlank() }
                                    ?: ApiErrorParser.userMessage(
                                        response,
                                        "ساخت شناسه اتباع انجام نشد"
                                    )
                            )
                        }
                    }

                    override fun onFailure(
                        call: Call<GenerateForeignCodeResponse>,
                        t: Throwable
                    ) {
                        isGeneratingForeignCode = false
                        saveButton.isEnabled = true
                        cbNoForeignCode.isEnabled = true

                        initializingIdentityUi = true
                        cbNoForeignCode.isChecked = false
                        initializingIdentityUi = false
                        foreignCode.setText("")
                        foreignAutoHint.text =
                            "با فعال کردن این گزینه، شناسه ۱۲ رقمی یکتا داخل همین فیلد نمایش داده می‌شود."
                        updateIdentityUi()

                        AppToast.error(
                            this@AddEditStudentActivity,
                            ApiErrorParser.networkMessage(t, "ساخت شناسه اتباع")
                        )
                    }
                })
        }

        cbIsForeign.setOnCheckedChangeListener { _, isChecked ->
            nationalId.error = null
            foreignCode.error = null

            if (!isChecked) {
                initializingIdentityUi = true
                cbNoForeignCode.isChecked = false
                initializingIdentityUi = false
                foreignCode.setText("")
            }

            updateIdentityUi()
        }

        cbNoForeignCode.setOnCheckedChangeListener { _, isChecked ->
            foreignCode.error = null
            updateIdentityUi()

            if (initializingIdentityUi) return@setOnCheckedChangeListener

            if (isChecked) {
                val old = editing
                val existingGeneratedCode = normalizeDigits(
                    old?.foreignCode.orEmpty()
                )

                if (
                    old?.foreignCodeGenerated == true &&
                    existingGeneratedCode.matches(Regex("^[0-9]{12}$"))
                ) {
                    foreignCode.setText(existingGeneratedCode)
                    foreignAutoHint.text =
                        "این شناسه قبلاً توسط سامانه ساخته شده و همان مقدار حفظ می‌شود."
                } else {
                    requestGeneratedForeignCode()
                }
            } else {
                if (editing?.foreignCodeGenerated == true) {
                    // کد تولیدی سیستم «کد رسمی اتباع» نیست؛
                    // اگر گزینه برداشته شود مدیر باید کد واقعی اتباع را وارد کند.
                    foreignCode.setText("")
                }
                foreignAutoHint.text =
                    "با فعال کردن این گزینه، شناسه ۱۲ رقمی یکتا داخل همین فیلد نمایش داده می‌شود."
            }
        }

        saveButton.setOnClickListener {
            firstName.error = null
            lastName.error = null
            studentCode.error = null
            phone.error = null
            nationalId.error = null
            foreignCode.error = null

            if (isGeneratingForeignCode) {
                AppToast.info(this, "شناسه اتباع در حال ساخته شدن است؛ چند لحظه صبر کنید")
                return@setOnClickListener
            }

            val fname = firstName.text.toString().trim()
            val lname = lastName.text.toString().trim()
            val codeValue = normalizeDigits(studentCode.text.toString().trim())
            val phoneValue = normalizeDigits(phone.text.toString().trim())
            val nationalIdValue = normalizeDigits(nationalId.text.toString().trim())
            val foreignCodeValue = normalizeDigits(foreignCode.text.toString().trim())

            val isForeign = cbIsForeign.isChecked
            val foreignCodeMissing = isForeign && cbNoForeignCode.isChecked

            when {
                fname.isBlank() -> {
                    firstName.error = "نام دانش‌آموز را وارد کنید"
                    firstName.requestFocus()
                    AppToast.warning(this, "برای ذخیره دانش‌آموز، نام را وارد کنید")
                    return@setOnClickListener
                }

                lname.isBlank() -> {
                    lastName.error = "نام خانوادگی را وارد کنید"
                    lastName.requestFocus()
                    AppToast.warning(this, "برای ذخیره دانش‌آموز، نام خانوادگی را وارد کنید")
                    return@setOnClickListener
                }

                !codeValue.matches(Regex("^[0-9]{4}$")) -> {
                    studentCode.error = "کد دانش‌آموز باید دقیقاً ۴ رقم باشد"
                    studentCode.requestFocus()
                    AppToast.warning(this, "کد دانش‌آموز باید دقیقاً ۴ رقم باشد")
                    return@setOnClickListener
                }

                phoneValue.length != 10 || !phoneValue.all { it in '0'..'9' } -> {
                    phone.error = "شماره تماس باید دقیقاً ۱۰ رقم و بدون صفر اول باشد"
                    phone.requestFocus()
                    AppToast.warning(this, "شماره تماس باید دقیقاً ۱۰ رقم و بدون صفر اول باشد")
                    return@setOnClickListener
                }

                !isForeign &&
                    (nationalIdValue.length != 10 || !nationalIdValue.all { it in '0'..'9' }) -> {
                    nationalId.error = "کد ملی باید دقیقاً ۱۰ رقم باشد"
                    nationalId.requestFocus()
                    AppToast.warning(this, "کد ملی دانش‌آموز باید دقیقاً ۱۰ رقم باشد")
                    return@setOnClickListener
                }

                isForeign &&
                    (foreignCodeValue.length != 12 || !foreignCodeValue.all { it in '0'..'9' }) -> {
                    foreignCode.error = if (foreignCodeMissing) {
                        "شناسه ۱۲ رقمی هنوز ساخته نشده است"
                    } else {
                        "کد اتباع باید دقیقاً ۱۲ رقم باشد"
                    }
                    foreignCode.requestFocus()
                    AppToast.warning(
                        this,
                        if (foreignCodeMissing) {
                            "شناسه اتباع هنوز ساخته نشده است؛ گزینه «کد اتباع ندارد» را دوباره فعال کنید"
                        } else {
                            "کد اتباع باید دقیقاً ۱۲ رقم باشد"
                        }
                    )
                    return@setOnClickListener
                }
            }

            val old = editing
            val isEditing = old != null

            val request = StudentSaveRequest(
                id = old?.id ?: UUID.randomUUID().toString(),
                firstName = fname,
                lastName = lname,
                studentCode = codeValue,
                phone = phoneValue,
                nationalId = if (isForeign) "" else nationalIdValue,
                classId = old?.classId,
                registrationDate = old?.registrationDate ?: AppDatabase.today(),
                avatarName = old?.avatarName ?: "avatar_no_profile",
                identityType = if (isForeign) "FOREIGN" else "NATIONAL",
                // حتی در حالت «کد اتباع ندارد» مقدار تولیدشده‌ای که کاربر
                // همین الان داخل فیلد می‌بیند برای Backend ارسال می‌شود.
                foreignCode = if (isForeign) foreignCodeValue else "",
                foreignCodeMissing = foreignCodeMissing
            )

            saveButton.isEnabled = false

            RetrofitClient.instance.addStudent(request)
                .enqueue(object : Callback<ApiResponse> {
                    override fun onResponse(
                        call: Call<ApiResponse>,
                        response: Response<ApiResponse>
                    ) {
                        saveButton.isEnabled = true
                        val body = response.body()

                        if (response.isSuccessful && body?.status == "success") {
                            // اگر به‌دلیل Race Condition بسیار نادر، Backend مجبور شده باشد
                            // شناسه تازه‌ای بسازد، همان مقدار نهایی فوراً داخل فیلد نمایش داده می‌شود.
                            if (
                                isForeign &&
                                body.foreignCodeGenerated &&
                                !body.foreignCode.isNullOrBlank()
                            ) {
                                foreignCode.setText(body.foreignCode)
                            }

                            val fallback = if (isEditing) {
                                "اطلاعات دانش‌آموز با موفقیت ویرایش شد"
                            } else {
                                "دانش‌آموز با موفقیت افزوده شد"
                            }

                            AppToast.success(
                                this@AddEditStudentActivity,
                                body.message.ifBlank { fallback }
                            )

                            if (!isEditing) {
                                showInitialCredentialsIfAvailable(body)
                            } else {
                                setResult(RESULT_OK)
                                finish()
                            }
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

                        val action = if (editing != null) {
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

    private fun showInitialCredentialsIfAvailable(body: ApiResponse) {
        val username = body.username.orEmpty().trim()
        val password = body.initialPassword.orEmpty()

        if (username.isBlank() || password.isBlank()) {
            setResult(RESULT_OK)
            finish()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("اطلاعات ورود دانش‌آموز")
            .setMessage(
                "نام کاربری: $username\n" +
                    "رمز اولیه: $password\n\n" +
                    "این اطلاعات را در اختیار دانش‌آموز قرار دهید."
            )
            .setCancelable(false)
            .setPositiveButton("متوجه شدم") { _, _ ->
                setResult(RESULT_OK)
                finish()
            }
            .show()
    }

    private fun normalizeDigits(value: String): String {
        return buildString(value.length) {
            value.forEach { ch ->
                append(
                    when (ch) {
                        '۰', '٠' -> '0'
                        '۱', '١' -> '1'
                        '۲', '٢' -> '2'
                        '۳', '٣' -> '3'
                        '۴', '٤' -> '4'
                        '۵', '٥' -> '5'
                        '۶', '٦' -> '6'
                        '۷', '٧' -> '7'
                        '۸', '٨' -> '8'
                        '۹', '٩' -> '9'
                        else -> ch
                    }
                )
            }
        }
    }
}
