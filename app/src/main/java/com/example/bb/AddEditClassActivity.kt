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
    private lateinit var etClassCode: TextInputEditText
    private lateinit var spinnerClassName: MaterialAutoCompleteTextView
    private lateinit var etBookName: TextInputEditText
    private lateinit var etTermYear: TextInputEditText
    private lateinit var spinnerTermSeason: MaterialAutoCompleteTextView
    private lateinit var etStartTime: TextInputEditText
    private lateinit var etEndTime: TextInputEditText
    private lateinit var etSessionCount: TextInputEditText
    private lateinit var etMinPassingScore: TextInputEditText       // 🌟 اضافه شد
    private lateinit var etMinConditionalScore: TextInputEditText   // 🌟 اضافه شد
    private lateinit var chipGroupDays: ChipGroup
    private lateinit var btnSaveClass: Button
    private lateinit var progressSaving: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_edit_class)

        findViewById<ImageView>(R.id.btnClassEditBack).setOnClickListener { finish() }

        tvTitle = findViewById(R.id.tvClassEditTitle)
        etClassCode = findViewById(R.id.etClassCode)
        spinnerClassName = findViewById(R.id.spinnerClassName)
        etBookName = findViewById(R.id.etBookName)
        etTermYear = findViewById(R.id.etTermYear)
        spinnerTermSeason = findViewById(R.id.spinnerTermSeason)
        etStartTime = findViewById(R.id.etStartTime)
        etEndTime = findViewById(R.id.etEndTime)
        etSessionCount = findViewById(R.id.etSessionCount)
        etMinPassingScore = findViewById(R.id.etMinPassingScore)               // 🌟
        etMinConditionalScore = findViewById(R.id.etMinConditionalScore)       // 🌟
        chipGroupDays = findViewById(R.id.chipGroupDays)
        btnSaveClass = findViewById(R.id.btnSaveClass)
        progressSaving = findViewById(R.id.progressSavingClass)

        spinnerClassName.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, SchoolClassCatalog.classNames))
        spinnerClassName.setOnClickListener { spinnerClassName.showDropDown() }

        val seasons = listOf("بهار", "تابستان", "پاییز", "زمستان")
        spinnerTermSeason.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, seasons))
        spinnerTermSeason.setOnClickListener { spinnerTermSeason.showDropDown() }

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
            override fun onResponse(call: Call<List<ClassModel>>, response: Response<List<ClassModel>>) {
                setSavingState(false)
                val classes = response.body().orEmpty()
                if (response.isSuccessful) AppDatabase.replaceClasses(classes)
                val model = classes.firstOrNull { it.id == classId }
                if (model == null) {
                    Toast.makeText(this@AddEditClassActivity, "اطلاعات کلاس پیدا نشد", Toast.LENGTH_LONG).show()
                    finish()
                    return
                }
                bindClass(model)
            }
            override fun onFailure(call: Call<List<ClassModel>>, t: Throwable) {
                setSavingState(false)
                Toast.makeText(this@AddEditClassActivity, "دریافت اطلاعات کلاس از سرور ناموفق بود", Toast.LENGTH_LONG).show()
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

        etClassCode.setText(model.classCode)
        spinnerClassName.setText(model.className, false)
        etBookName.setText(model.bookName)
        etTermYear.setText(model.termYear)
        spinnerTermSeason.setText(model.termSeason, false)
        etStartTime.setText(model.startTime)
        etEndTime.setText(model.endTime)
        etSessionCount.setText(model.sessionCount.toString())
        etMinPassingScore.setText(model.minPassingScore.toString())           // 🌟
        etMinConditionalScore.setText(model.minConditionalScore.toString())   // 🌟

        val savedDays = model.daysOfWeek.split("،", ",").map(::normalizeDayLabel).toSet()
        for (index in 0 until chipGroupDays.childCount) {
            val chip = chipGroupDays.getChildAt(index) as? Chip ?: continue
            chip.isChecked = normalizeDayLabel(chip.text.toString()) in savedDays
        }
    }

    private fun validateAndSave() {
        etClassCode.error = null
        spinnerClassName.error = null
        etBookName.error = null
        etTermYear.error = null
        spinnerTermSeason.error = null
        etStartTime.error = null
        etEndTime.error = null
        etSessionCount.error = null
        etMinPassingScore.error = null
        etMinConditionalScore.error = null

        val classCode = etClassCode.text?.toString()?.trim().orEmpty()
        if (classCode.isBlank()) {
            etClassCode.error = "کد دستی کلاس الزامی است"
            etClassCode.requestFocus()
            return
        }

        val className = spinnerClassName.text?.toString()?.trim().orEmpty()
        if (className !in SchoolClassCatalog.classNames && className != existingClass?.className) {
            spinnerClassName.error = "نام کلاس را از فهرست انتخاب کنید"
            spinnerClassName.requestFocus()
            return
        }

        val bookName = etBookName.text?.toString()?.trim().orEmpty()
        if (bookName.isBlank()) {
            etBookName.error = "نام کتاب الزامی است"
            etBookName.requestFocus()
            return
        }

        val termYear = etTermYear.text?.toString()?.trim().orEmpty()
        if (termYear.isBlank()) {
            etTermYear.error = "سال ترم الزامی است"
            etTermYear.requestFocus()
            return
        }

        val termSeason = spinnerTermSeason.text?.toString()?.trim().orEmpty()
        if (termSeason !in listOf("بهار", "تابستان", "پاییز", "زمستان")) {
            spinnerTermSeason.error = "فصل ترم را انتخاب کنید"
            spinnerTermSeason.requestFocus()
            return
        }

        val startTime = ClassTimeUtils.parse(etStartTime.text?.toString().orEmpty()) ?: return
        val endTime = ClassTimeUtils.parse(etEndTime.text?.toString().orEmpty()) ?: return

        if (endTime.minutesFromMidnight <= startTime.minutesFromMidnight) {
            etEndTime.error = "ساعت پایان باید بعد از ساعت شروع باشد"
            return
        }

        val sessionCount = etSessionCount.text?.toString()?.trim()?.toIntOrNull() ?: 1

        // 🌟 اعتبارسنجی محدوده‌های نمراتی جدید شما
        val minPass = etMinPassingScore.text?.toString()?.toDoubleOrNull() ?: 80.0
        val minCond = etMinConditionalScore.text?.toString()?.toDoubleOrNull() ?: 70.0

        if (minPass >= 83.0) {
            etMinPassingScore.error = "حد پایین قبولی باید حتماً کمتر از ۸۳ باشد"
            etMinPassingScore.requestFocus()
            return
        }
        if (minCond >= minPass) {
            etMinConditionalScore.error = "حد مشروطی باید کمتر از حد قبولی باشد"
            etMinConditionalScore.requestFocus()
            return
        }

        val selectedDays = mutableListOf<String>()
        for (index in 0 until chipGroupDays.childCount) {
            val chip = chipGroupDays.getChildAt(index) as? Chip ?: continue
            if (chip.isChecked) selectedDays += chip.text.toString()
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
            teacherId = old?.teacherId,
            teacherName = old?.teacherName.orEmpty(),
            status = old?.status ?: ClassStatus.ACTIVE,
            createdAt = old?.createdAt ?: AppDatabase.today(),
            classCode = classCode,
            bookName = bookName,
            termYear = termYear,
            termSeason = termSeason,
            minPassingScore = minPass,       // 🌟
            minConditionalScore = minCond    // 🌟
        )

        if (old == null) addClass(model) else updateClass(model)
    }

    private fun addClass(model: ClassModel) {
        setSavingState(true)
        RetrofitClient.instance.addClass(model).enqueue(object : Callback<ApiResponse> {
            override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                setSavingState(false)
                if (response.isSuccessful && response.body()?.status == "success") {
                    AppDatabase.upsertClass(model)
                    setResult(RESULT_OK); finish()
                } else {
                    Toast.makeText(this@AddEditClassActivity, response.body()?.message ?: "خطا در ثبت", Toast.LENGTH_LONG).show()
                }
            }
            override fun onFailure(call: Call<ApiResponse>, t: Throwable) { setSavingState(false) }
        })
    }

    private fun updateClass(model: ClassModel) {
        setSavingState(true)
        RetrofitClient.instance.updateClass(model).enqueue(object : Callback<ApiResponse> {
            override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                setSavingState(false)
                if (response.isSuccessful && response.body()?.status == "success") {
                    AppDatabase.upsertClass(model)
                    setResult(RESULT_OK); finish()
                }
            }
            override fun onFailure(call: Call<ApiResponse>, t: Throwable) { setSavingState(false) }
        })
    }

    private fun setTimeFormatter(field: TextInputEditText) {
        field.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) ClassTimeUtils.parse(field.text?.toString().orEmpty())?.let { field.setText(it.formatted) }
        }
    }

    private fun setSavingState(saving: Boolean) {
        btnSaveClass.isEnabled = !saving
        progressSaving.visibility = if (saving) View.VISIBLE else View.GONE
    }

    private fun normalizeDayLabel(value: String): String = value.replace("\u200C", "").replace(" ", "").trim()

    companion object { const val EXTRA_CLASS_ID = "CLASS_ID" }
}