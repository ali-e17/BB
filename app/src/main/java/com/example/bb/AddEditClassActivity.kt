package com.example.bb

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.UUID

class AddEditClassActivity : AppCompatActivity() {

    private var classId: String = ""
    private var existingClass: ClassModel? = null

    private lateinit var tvTitle: TextView
    private lateinit var spinnerClassName: MaterialAutoCompleteTextView
    private lateinit var etStartTime: TextInputEditText
    private lateinit var etEndTime: TextInputEditText
    private lateinit var etSessionCount: TextInputEditText
    private lateinit var chipGroupDays: ChipGroup
    private lateinit var btnSaveClass: Button
    private lateinit var progressSaving: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_edit_class)

        findViewById<ImageView>(R.id.btnClassEditBack).setOnClickListener { finish() }

        tvTitle = findViewById(R.id.tvClassEditTitle)
        spinnerClassName = findViewById(R.id.spinnerClassName)
        etStartTime = findViewById(R.id.etStartTime)
        etEndTime = findViewById(R.id.etEndTime)
        etSessionCount = findViewById(R.id.etSessionCount)
        chipGroupDays = findViewById(R.id.chipGroupDays)
        btnSaveClass = findViewById(R.id.btnSaveClass)
        progressSaving = findViewById(R.id.progressSavingClass)

        spinnerClassName.setAdapter(
            ArrayAdapter(
                this,
                android.R.layout.simple_dropdown_item_1line,
                SchoolClassCatalog.classNames
            )
        )
        spinnerClassName.setOnClickListener { spinnerClassName.showDropDown() }

        setTimeFormatter(etStartTime)
        setTimeFormatter(etEndTime)

        classId = intent.getStringExtra(EXTRA_CLASS_ID).orEmpty()
        if (classId.isBlank()) {
            tvTitle.text = "ایجاد کلاس جدید"
        } else {
            tvTitle.text = "ویرایش اطلاعات کلاس"
            loadClassForEdit()
        }

        btnSaveClass.setOnClickListener { validateAndSave() }
    }

    private fun loadClassForEdit() {
        val localClass = AppDatabase.getClassById(classId)
        if (localClass != null) {
            bindClass(localClass)
            return
        }

        setSavingState(true)
        RetrofitClient.instance.getClasses().enqueue(object : Callback<List<ClassModel>> {
            override fun onResponse(
                call: Call<List<ClassModel>>,
                response: Response<List<ClassModel>>
            ) {
                setSavingState(false)
                val classes = response.body().orEmpty()
                if (response.isSuccessful) {
                    AppDatabase.replaceClasses(classes)
                }

                val model = classes.firstOrNull { it.id == classId }
                if (model == null) {
                    Toast.makeText(
                        this@AddEditClassActivity,
                        "اطلاعات کلاس پیدا نشد",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                    return
                }
                bindClass(model)
            }

            override fun onFailure(call: Call<List<ClassModel>>, t: Throwable) {
                setSavingState(false)
                Toast.makeText(
                    this@AddEditClassActivity,
                    "دریافت اطلاعات کلاس از سرور ناموفق بود",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        })
    }

    private fun bindClass(model: ClassModel) {
        existingClass = model

        if (model.status != ClassStatus.ACTIVE) {
            Toast.makeText(this, "کلاس پایان‌یافته قابل ویرایش نیست", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        spinnerClassName.setText(model.className, false)
        etStartTime.setText(model.startTime)
        etEndTime.setText(model.endTime)
        etSessionCount.setText(model.sessionCount.toString())

        val savedDays = model.daysOfWeek
            .split("،", ",")
            .map(::normalizeDayLabel)
            .toSet()

        for (index in 0 until chipGroupDays.childCount) {
            val chip = chipGroupDays.getChildAt(index) as? Chip ?: continue
            chip.isChecked = normalizeDayLabel(chip.text.toString()) in savedDays
        }
    }

    private fun validateAndSave() {
        spinnerClassName.error = null
        etStartTime.error = null
        etEndTime.error = null
        etSessionCount.error = null

        val className = spinnerClassName.text?.toString()?.trim().orEmpty()
        if (className !in SchoolClassCatalog.classNames && className != existingClass?.className) {
            spinnerClassName.error = "نام کلاس را از فهرست انتخاب کنید"
            spinnerClassName.requestFocus()
            return
        }

        val startTime = ClassTimeUtils.parse(etStartTime.text?.toString().orEmpty())
        if (startTime == null) {
            etStartTime.error = "ساعت شروع نامعتبر است"
            etStartTime.requestFocus()
            return
        }

        val endTime = ClassTimeUtils.parse(etEndTime.text?.toString().orEmpty())
        if (endTime == null) {
            etEndTime.error = "ساعت پایان نامعتبر است"
            etEndTime.requestFocus()
            return
        }

        etStartTime.setText(startTime.formatted)
        etEndTime.setText(endTime.formatted)

        if (endTime.minutesFromMidnight <= startTime.minutesFromMidnight) {
            etEndTime.error = "ساعت پایان باید بعد از ساعت شروع باشد"
            etEndTime.requestFocus()
            return
        }

        val sessionCount = etSessionCount.text?.toString()?.trim()?.toIntOrNull()
        if (sessionCount == null || sessionCount !in 1..365) {
            etSessionCount.error = "تعداد جلسات باید بین ۱ تا ۳۶۵ باشد"
            etSessionCount.requestFocus()
            return
        }

        val selectedDays = mutableListOf<String>()
        for (index in 0 until chipGroupDays.childCount) {
            val chip = chipGroupDays.getChildAt(index) as? Chip ?: continue
            if (chip.isChecked) selectedDays += chip.text.toString()
        }
        if (selectedDays.isEmpty()) {
            Toast.makeText(this, "حداقل یک روز برگزاری را انتخاب کنید", Toast.LENGTH_SHORT).show()
            return
        }

        val old = existingClass
        val model = ClassModel(
            id = old?.id ?: UUID.randomUUID().toString(),
            className = className,
            startTime = startTime.formatted,
            endTime = endTime.formatted,
            daysOfWeek = selectedDays.joinToString("، "),
            sessionCount = sessionCount,
            teacherPhone = old?.teacherPhone,
            status = old?.status ?: ClassStatus.ACTIVE,
            createdAt = old?.createdAt ?: AppDatabase.today(),
            completedAt = old?.completedAt
        )

        val duplicate = AppDatabase.getAllClasses(false).any {
            it.id != model.id &&
                it.className.equals(model.className, ignoreCase = true) &&
                it.daysOfWeek == model.daysOfWeek &&
                it.startTime == model.startTime &&
                it.endTime == model.endTime
        }
        if (duplicate) {
            Toast.makeText(
                this,
                "یک کلاس فعال با همین نام، روز و ساعت وجود دارد",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (old == null) addClass(model) else updateClass(model)
    }

    private fun addClass(model: ClassModel) {
        setSavingState(true)
        RetrofitClient.instance.addClass(model).enqueue(object : Callback<ApiResponse> {
            override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                setSavingState(false)
                val result = response.body()
                if (response.isSuccessful && result?.status == "success") {
                    AppDatabase.upsertClass(model)
                    Toast.makeText(
                        this@AddEditClassActivity,
                        result.message.ifBlank { "کلاس با موفقیت ایجاد شد" },
                        Toast.LENGTH_SHORT
                    ).show()
                    setResult(RESULT_OK)
                    finish()
                } else {
                    Toast.makeText(
                        this@AddEditClassActivity,
                        serverMessage(response, result, "سرور کلاس را ثبت نکرد"),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                setSavingState(false)
                Toast.makeText(
                    this@AddEditClassActivity,
                    "اتصال به سرور برقرار نشد: ${t.localizedMessage ?: "خطای نامشخص"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        })
    }

    private fun updateClass(model: ClassModel) {
        setSavingState(true)
        RetrofitClient.instance.updateClass(model).enqueue(object : Callback<ApiResponse> {
            override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                setSavingState(false)
                val result = response.body()

                if (response.isSuccessful && result?.status == "success") {
                    AppDatabase.upsertClass(model)
                    Toast.makeText(
                        this@AddEditClassActivity,
                        result.message.ifBlank { "اطلاعات کلاس ویرایش شد" },
                        Toast.LENGTH_SHORT
                    ).show()
                    setResult(RESULT_OK)
                    finish()
                } else {
                    Toast.makeText(
                        this@AddEditClassActivity,
                        serverMessage(response, result, "ویرایش کلاس در سرور انجام نشد"),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                setSavingState(false)
                Toast.makeText(
                    this@AddEditClassActivity,
                    "اتصال به سرور برقرار نشد: ${t.localizedMessage ?: "خطای نامشخص"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        })
    }

    private fun serverMessage(
        response: Response<ApiResponse>,
        result: ApiResponse?,
        fallback: String
    ): String {
        result?.message?.takeIf { it.isNotBlank() }?.let { return it }

        val rawError = runCatching { response.errorBody()?.string() }
            .getOrNull()
            ?.trim()
            .orEmpty()

        return when {
            rawError.isNotBlank() -> "خطای سرور (${response.code()}): $rawError"
            response.code() == 404 -> "فایل update_class.php روی هاست پیدا نشد"
            else -> "$fallback (کد ${response.code()})"
        }
    }

    private fun setTimeFormatter(field: TextInputEditText) {
        field.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                ClassTimeUtils.parse(field.text?.toString().orEmpty())?.let {
                    field.setText(it.formatted)
                }
            }
        }
    }

    private fun setSavingState(saving: Boolean) {
        btnSaveClass.isEnabled = !saving
        btnSaveClass.alpha = if (saving) 0.55f else 1f
        progressSaving.visibility = if (saving) View.VISIBLE else View.GONE
    }

    private fun normalizeDayLabel(value: String): String = value
        .replace("\u200C", "")
        .replace(" ", "")
        .trim()

    companion object {
        const val EXTRA_CLASS_ID = "CLASS_ID"
    }
}
