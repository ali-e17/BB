package com.example.bb

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.UUID

class AddEditClassActivity : BaseActivity() {

    private var classId: String = ""
    private var existingClass: ClassModel? = null

    private lateinit var tvTitle: TextView
    private lateinit var etClassCode: TextInputEditText
    private lateinit var spinnerClassName: MaterialAutoCompleteTextView
    private lateinit var etClassLevel: TextInputEditText
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

    private val classNameOptions = mutableListOf<ClassNameOption>()
    private var classNameOptionsLoaded = false
    private var lastValidClassName = ""
    private val manageClassNamesLabel = "＋ مدیریت نام کلاس‌ها"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_edit_class)

        findViewById<ImageView>(R.id.btnClassEditBack).setOnClickListener { finish() }

        tvTitle = findViewById(R.id.tvClassEditTitle)
        etClassCode = findViewById(R.id.etClassCode)
        spinnerClassName = findViewById(R.id.spinnerClassName)
        etClassLevel = findViewById(R.id.etClassLevel)
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

        spinnerClassName.isEnabled = false
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

        loadClassNameOptions()
        btnSaveClass.setOnClickListener { validateAndSave() }
    }

    private fun loadClassNameOptions(onFinished: (() -> Unit)? = null) {
        RetrofitClient.instance.getClassNameOptions().enqueue(object : Callback<List<ClassNameOption>> {
            override fun onResponse(call: Call<List<ClassNameOption>>, response: Response<List<ClassNameOption>>) {
                if (response.isSuccessful) {
                    classNameOptions.clear()
                    classNameOptions.addAll(response.body().orEmpty().filter { it.name.isNotBlank() })
                    classNameOptionsLoaded = true
                    refreshClassNameDropdown()
                    spinnerClassName.isEnabled = true
                    onFinished?.invoke()
                } else {
                    classNameOptionsLoaded = false
                    refreshClassNameDropdown()
                    spinnerClassName.isEnabled = existingClass != null
                    Toast.makeText(
                        this@AddEditClassActivity,
                        ApiErrorParser.userMessage(response, "دریافت فهرست نام کلاس‌ها انجام نشد"),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            override fun onFailure(call: Call<List<ClassNameOption>>, t: Throwable) {
                classNameOptionsLoaded = false
                refreshClassNameDropdown()
                spinnerClassName.isEnabled = existingClass != null
                Toast.makeText(
                    this@AddEditClassActivity,
                    ApiErrorParser.networkMessage(t, "دریافت فهرست نام کلاس‌ها"),
                    Toast.LENGTH_LONG
                ).show()
            }
        })
    }

    private fun refreshClassNameDropdown() {
        val names = classNameOptions.map { it.name }.toMutableList()
        val existingName = existingClass?.className?.trim().orEmpty()
        if (existingName.isNotBlank() && existingName !in names) {
            names.add(0, existingName)
        }

        val currentText = spinnerClassName.text?.toString()?.trim().orEmpty()
        if (
            currentText.isNotBlank() &&
            currentText != existingName &&
            currentText !in names &&
            currentText != manageClassNamesLabel
        ) {
            spinnerClassName.setText("", false)
            lastValidClassName = ""
        }

        val items = names + manageClassNamesLabel
        spinnerClassName.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, items)
        )
        spinnerClassName.setOnItemClickListener { _, _, position, _ ->
            val selected = items.getOrNull(position).orEmpty()
            if (selected == manageClassNamesLabel) {
                spinnerClassName.setText(lastValidClassName, false)
                showClassNameManager()
            } else {
                lastValidClassName = selected
                spinnerClassName.error = null
            }
        }
    }

    private fun showClassNameManager() {
        if (!classNameOptionsLoaded) {
            loadClassNameOptions { showClassNameManager() }
            return
        }

        val content = LayoutInflater.from(this)
            .inflate(R.layout.dialog_manage_class_names, null, false)
        val inputLayout = content.findViewById<TextInputLayout>(R.id.layoutNewClassName)
        val input = content.findViewById<TextInputEditText>(R.id.etNewClassName)
        val addButton = content.findViewById<MaterialButton>(R.id.btnAddClassName)
        val progress = content.findViewById<ProgressBar>(R.id.progressClassNames)
        val listContainer = content.findViewById<LinearLayout>(R.id.classNameListContainer)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("مدیریت نام کلاس‌ها")
            .setView(content)
            .setNegativeButton("بستن", null)
            .create()

        fun setBusy(busy: Boolean) {
            progress.visibility = if (busy) View.VISIBLE else View.GONE
            addButton.isEnabled = !busy
            input.isEnabled = !busy
            for (i in 0 until listContainer.childCount) {
                listContainer.getChildAt(i)
                    .findViewById<ImageButton>(R.id.btnDeleteClassNameOption)
                    ?.isEnabled = !busy
            }
        }

        fun renderRows() {
            listContainer.removeAllViews()
            classNameOptions.forEach { option ->
                val row = LayoutInflater.from(this)
                    .inflate(R.layout.item_class_name_option, listContainer, false)
                row.findViewById<TextView>(R.id.txtClassNameOption).text = option.name
                row.findViewById<ImageButton>(R.id.btnDeleteClassNameOption).setOnClickListener {
                    MaterialAlertDialogBuilder(this)
                        .setTitle("حذف نام کلاس")
                        .setMessage(
                            "آیا از حذف «${option.name}» از فهرست انتخاب مطمئن هستید؟\n\n" +
                                "کلاس‌های قبلی با این نام حذف یا تغییر نمی‌کنند."
                        )
                        .setNegativeButton("انصراف", null)
                        .setPositiveButton("حذف") { _, _ ->
                            setBusy(true)
                            RetrofitClient.instance.deleteClassNameOption(
                                DeleteClassNameOptionRequest(option.id)
                            ).enqueue(object : Callback<ApiResponse> {
                                override fun onResponse(
                                    call: Call<ApiResponse>,
                                    response: Response<ApiResponse>
                                ) {
                                    setBusy(false)
                                    val body = response.body()
                                    if (response.isSuccessful && body?.status == "success") {
                                        Toast.makeText(
                                            this@AddEditClassActivity,
                                            body.message.ifBlank { "نام کلاس از فهرست حذف شد" },
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        loadClassNameOptions { renderRows() }
                                    } else {
                                        Toast.makeText(
                                            this@AddEditClassActivity,
                                            body?.message?.takeIf { it.isNotBlank() }
                                                ?: ApiErrorParser.userMessage(
                                                    response,
                                                    "حذف نام کلاس انجام نشد"
                                                ),
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }

                                override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                                    setBusy(false)
                                    Toast.makeText(
                                        this@AddEditClassActivity,
                                        ApiErrorParser.networkMessage(t, "حذف نام کلاس"),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            })
                        }
                        .show()
                }
                listContainer.addView(row)
            }
        }

        addButton.setOnClickListener {
            inputLayout.error = null
            val name = input.text?.toString()?.trim().orEmpty()
            when {
                name.isBlank() -> {
                    inputLayout.error = "نام کلاس را وارد کنید"
                    input.requestFocus()
                }
                name.length > 100 -> {
                    inputLayout.error = "نام کلاس حداکثر ۱۰۰ کاراکتر است"
                    input.requestFocus()
                }
                else -> {
                    setBusy(true)
                    RetrofitClient.instance.addClassNameOption(
                        AddClassNameOptionRequest(name)
                    ).enqueue(object : Callback<ApiResponse> {
                        override fun onResponse(
                            call: Call<ApiResponse>,
                            response: Response<ApiResponse>
                        ) {
                            setBusy(false)
                            val body = response.body()
                            if (response.isSuccessful && body?.status == "success") {
                                input.setText("")
                                Toast.makeText(
                                    this@AddEditClassActivity,
                                    body.message.ifBlank { "نام کلاس به فهرست اضافه شد" },
                                    Toast.LENGTH_SHORT
                                ).show()
                                loadClassNameOptions { renderRows() }
                            } else {
                                inputLayout.error = body?.message?.takeIf { it.isNotBlank() }
                                    ?: ApiErrorParser.userMessage(
                                        response,
                                        "افزودن نام کلاس انجام نشد"
                                    )
                            }
                        }

                        override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                            setBusy(false)
                            Toast.makeText(
                                this@AddEditClassActivity,
                                ApiErrorParser.networkMessage(t, "افزودن نام کلاس"),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    })
                }
            }
        }

        dialog.setOnShowListener { renderRows() }
        dialog.show()
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
        lastValidClassName = model.className
        refreshClassNameDropdown()
        etClassLevel.setText(model.classLevel)
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
        etClassLevel.error = null
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
        val oldClassName = existingClass?.className.orEmpty()
        val activeNames = classNameOptions.map { it.name }.toSet()
        if (className.isBlank()) {
            spinnerClassName.error = "نام کلاس را انتخاب کنید"
            spinnerClassName.requestFocus()
            return
        }
        if (className != oldClassName && (!classNameOptionsLoaded || className !in activeNames)) {
            spinnerClassName.error = if (!classNameOptionsLoaded) {
                "فهرست نام کلاس‌ها هنوز دریافت نشده است"
            } else {
                "نام کلاس را از فهرست انتخاب کنید"
            }
            spinnerClassName.requestFocus()
            return
        }

        val classLevel = etClassLevel.text?.toString()?.trim().orEmpty()
        if (classLevel.isBlank()) {
            etClassLevel.error = "سطح کلاس الزامی است"
            etClassLevel.requestFocus()
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

        val sessionCount = etSessionCount.text?.toString()?.trim()?.toIntOrNull()
        if (sessionCount == null || sessionCount < 1) {
            etSessionCount.error = "تعداد جلسات باید حداقل ۱ باشد"
            etSessionCount.requestFocus()
            return
        }

        val minPassRaw = etMinPassingScore.text?.toString()?.trim().orEmpty()
        val minCondRaw = etMinConditionalScore.text?.toString()?.trim().orEmpty()
        val minPass = minPassRaw.toDoubleOrNull()
        val minCond = minCondRaw.toDoubleOrNull()

        if (minPass == null) {
            etMinPassingScore.error = "حد قبولی را به‌صورت عدد وارد کنید"
            etMinPassingScore.requestFocus()
            return
        }
        if (minCond == null) {
            etMinConditionalScore.error = "حد مشروطی را به‌صورت عدد وارد کنید"
            etMinConditionalScore.requestFocus()
            return
        }
        if (minCond < 0.0 || minPass <= 0.0) {
            etMinConditionalScore.error = "مرزهای نمره نمی‌توانند منفی باشند"
            etMinConditionalScore.requestFocus()
            return
        }
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

        if (selectedDays.isEmpty()) {
            Toast.makeText(this, "حداقل یک روز برگزاری را انتخاب کنید", Toast.LENGTH_LONG).show()
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
            classLevel = classLevel,
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
                    Toast.makeText(this@AddEditClassActivity, response.body()?.message?.takeIf { it.isNotBlank() } ?: ApiErrorParser.userMessage(response, "ثبت کلاس انجام نشد"), Toast.LENGTH_LONG).show()
                }
            }
            override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                setSavingState(false)
                Toast.makeText(this@AddEditClassActivity, ApiErrorParser.networkMessage(t, "ثبت اطلاعات کلاس"), Toast.LENGTH_LONG).show()
            }
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
                } else {
                    Toast.makeText(
                        this@AddEditClassActivity,
                        response.body()?.message?.takeIf { it.isNotBlank() } ?: ApiErrorParser.userMessage(response, "ویرایش کلاس انجام نشد"),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                setSavingState(false)
                Toast.makeText(this@AddEditClassActivity, ApiErrorParser.networkMessage(t, "ویرایش اطلاعات کلاس"), Toast.LENGTH_LONG).show()
            }
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
