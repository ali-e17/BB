package com.example.bb

import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.text.method.ReplacementTransformationMethod
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
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
    private lateinit var scrollClassForm: NestedScrollView
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
        // این صفحه Edge-to-Edge است؛ بنابراین resize سنتی به‌تنهایی قابل اتکا نیست.
        // فضای IME را با WindowInsets به‌صورت صریح مدیریت می‌کنیم.
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        setContentView(R.layout.activity_add_edit_class)

        findViewById<ImageView>(R.id.btnClassEditBack).setOnClickListener { finish() }

        tvTitle = findViewById(R.id.tvClassEditTitle)
        scrollClassForm = findViewById(R.id.scrollClassForm)
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

        // همان متد پایدار صفحه نمرات:
        // مقدار واقعی تغییر نمی‌کند، فقط نمایش اعداد فارسی می‌شود.
        etMinPassingScore.transformationMethod =
            PersianNumericTransformationMethod()
        etMinConditionalScore.transformationMethod =
            PersianNumericTransformationMethod()

        // عدد پیش‌فرض/قبلی با اولین تایپ کامل جایگزین شود؛
        // دیگر 80 یا 70 به عدد جدید نچسبد.
        etMinPassingScore.setSelectAllOnFocus(true)
        etMinConditionalScore.setSelectAllOnFocus(true)

        chipGroupDays = findViewById(R.id.chipGroupDays)
        btnSaveClass = findViewById(R.id.btnSaveClass)
        progressSaving = findViewById(R.id.progressSavingClass)

        setupImeAwareLayout()

        spinnerClassName.isEnabled = false
        spinnerClassName.setOnClickListener { openDropdown(spinnerClassName) }

        val seasons = listOf("بهار", "تابستان", "پاییز", "زمستان")
        spinnerTermSeason.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, seasons)
        )
        spinnerTermSeason.setOnClickListener { openDropdown(spinnerTermSeason) }
        spinnerTermSeason.setOnItemClickListener { _, _, _, _ ->
            spinnerTermSeason.error = null
            focusField(etStartTime)
        }

        setTimeFormatter(etStartTime)
        setTimeFormatter(etEndTime)
        setupKeyboardFlow()

        classId = intent.getStringExtra(EXTRA_CLASS_ID).orEmpty()
        if (classId.isBlank()) {
            tvTitle.text = "ایجاد کلاس جدید"

            // فقط برای «کلاس جدید» مقدار پیشنهادی اولیه می‌گذاریم.
            // کاربر آزاد است هر مقدار معتبر دیگری وارد کند.
            etMinPassingScore.setText("80")
            etMinConditionalScore.setText("70")
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
                    AppToast.makeText(
                        this@AddEditClassActivity,
                        ApiErrorParser.userMessage(response, "دریافت فهرست نام کلاس‌ها کامل نشد"),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            override fun onFailure(call: Call<List<ClassNameOption>>, t: Throwable) {
                classNameOptionsLoaded = false
                refreshClassNameDropdown()
                spinnerClassName.isEnabled = existingClass != null
                AppToast.makeText(
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
                focusField(etClassLevel)
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
                                        AppToast.makeText(
                                            this@AddEditClassActivity,
                                            body.message.ifBlank { "نام کلاس از فهرست حذف شد" },
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        loadClassNameOptions { renderRows() }
                                    } else {
                                        AppToast.makeText(
                                            this@AddEditClassActivity,
                                            body?.message?.takeIf { it.isNotBlank() }
                                                ?: ApiErrorParser.userMessage(
                                                    response,
                                                    "حذف نام کلاس کامل نشد"
                                                ),
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }

                                override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                                    setBusy(false)
                                    AppToast.makeText(
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
                    AppToast.warning(this, "برای افزودن به فهرست، نام کلاس را وارد کنید")
                }
                name.length > 100 -> {
                    inputLayout.error = "نام کلاس حداکثر ۱۰۰ کاراکتر است"
                    input.requestFocus()
                    AppToast.warning(this, "نام کلاس نمی‌تواند بیشتر از ۱۰۰ کاراکتر باشد")
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
                                AppToast.makeText(
                                    this@AddEditClassActivity,
                                    body.message.ifBlank { "نام کلاس به فهرست اضافه شد" },
                                    Toast.LENGTH_SHORT
                                ).show()
                                loadClassNameOptions { renderRows() }
                            } else {
                                val message = body?.message?.takeIf { it.isNotBlank() }
                                    ?: ApiErrorParser.userMessage(
                                        response,
                                        "افزودن نام کلاس کامل نشد"
                                    )
                                inputLayout.error = message
                                AppToast.error(this@AddEditClassActivity, message)
                            }
                        }

                        override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                            setBusy(false)
                            AppToast.makeText(
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
        val localFallback =
            AppDatabase.getClassById(classId)

        // برای ویرایش، مقدار مرزهای نمره را همیشه از سرور تازه می‌گیریم.
        // Cacheهای قدیمی ممکن است هنوز 80/70 پیش‌فرض داشته باشند.
        setSavingState(true)

        RetrofitClient.instance.getClasses()
            .enqueue(
                object : Callback<List<ClassModel>> {

                    override fun onResponse(
                        call: Call<List<ClassModel>>,
                        response: Response<List<ClassModel>>
                    ) {
                        setSavingState(false)

                        val classes =
                            response.body().orEmpty()

                        val serverModel =
                            if (response.isSuccessful) {
                                classes.firstOrNull {
                                    it.id == classId
                                }
                            } else {
                                null
                            }

                        if (
                            response.isSuccessful &&
                            classes.isNotEmpty()
                        ) {
                            AppDatabase.replaceClasses(
                                classes
                            )
                        }

                        val model =
                            serverModel ?: localFallback

                        if (model == null) {
                            AppToast.error(
                                this@AddEditClassActivity,
                                "اطلاعات این کلاس در دسترس نیست یا کلاس از سرور حذف شده است"
                            )
                            finish()
                            return
                        }

                        bindClass(model)
                    }

                    override fun onFailure(
                        call: Call<List<ClassModel>>,
                        t: Throwable
                    ) {
                        setSavingState(false)

                        if (localFallback != null) {
                            bindClass(localFallback)

                            AppToast.info(
                                this@AddEditClassActivity,
                                "اطلاعات کلاس از حافظه محلی باز شد؛ اتصال سرور در دسترس نبود"
                            )
                        } else {
                            AppToast.error(
                                this@AddEditClassActivity,
                                ApiErrorParser.networkMessage(
                                    t,
                                    "دریافت اطلاعات کلاس برای ویرایش"
                                )
                            )
                            finish()
                        }
                    }
                }
            )
    }

    private fun bindClass(model: ClassModel) {
        existingClass = model
        if (model.status != ClassStatus.ACTIVE) {
            AppToast.makeText(this, "کلاس پایان‌یافته قابل ویرایش نیست", Toast.LENGTH_LONG).show()
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
        etMinPassingScore.setText(formatBoundary(model.minPassingScore))
        etMinConditionalScore.setText(formatBoundary(model.minConditionalScore))

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
            AppToast.warning(this, "کد کلاس را وارد کنید")
            return
        }

        val className = spinnerClassName.text?.toString()?.trim().orEmpty()
        val oldClassName = existingClass?.className.orEmpty()
        val activeNames = classNameOptions.map { it.name }.toSet()
        if (className.isBlank()) {
            spinnerClassName.error = "نام کلاس را انتخاب کنید"
            spinnerClassName.requestFocus()
            AppToast.warning(this, "نام کلاس را از فهرست انتخاب کنید")
            return
        }
        if (className != oldClassName && (!classNameOptionsLoaded || className !in activeNames)) {
            spinnerClassName.error = if (!classNameOptionsLoaded) {
                "فهرست نام کلاس‌ها دریافت نشده است؛ لطفاً اتصال اینترنت را بررسی کرده و مجدداً تلاش کنید"
            } else {
                "نام کلاس را از فهرست انتخاب کنید"
            }
            spinnerClassName.requestFocus()
            AppToast.warning(this, spinnerClassName.error?.toString() ?: "نام کلاس معتبر نیست")
            return
        }

        val classLevel = etClassLevel.text?.toString()?.trim().orEmpty()
        if (classLevel.isBlank()) {
            etClassLevel.error = "سطح کلاس الزامی است"
            etClassLevel.requestFocus()
            AppToast.warning(this, "سطح کلاس را وارد کنید")
            return
        }

        val bookName = etBookName.text?.toString()?.trim().orEmpty()
        if (bookName.isBlank()) {
            etBookName.error = "نام کتاب الزامی است"
            etBookName.requestFocus()
            AppToast.warning(this, "نام کتاب کلاس را وارد کنید")
            return
        }

        val termYear = etTermYear.text?.toString()?.trim().orEmpty()
        if (termYear.isBlank()) {
            etTermYear.error = "سال ترم الزامی است"
            etTermYear.requestFocus()
            AppToast.warning(this, "سال ترم را وارد کنید")
            return
        }

        val termSeason = spinnerTermSeason.text?.toString()?.trim().orEmpty()
        if (termSeason !in listOf("بهار", "تابستان", "پاییز", "زمستان")) {
            spinnerTermSeason.error = "فصل ترم را انتخاب کنید"
            spinnerTermSeason.requestFocus()
            AppToast.warning(this, "فصل ترم را انتخاب کنید")
            return
        }

        val startTime = ClassTimeUtils.parse(etStartTime.text?.toString().orEmpty())
        if (startTime == null) {
            etStartTime.error = "ساعت شروع معتبر وارد کنید"
            etStartTime.requestFocus()
            AppToast.warning(this, "ساعت شروع کلاس را با فرمت صحیح وارد کنید؛ مثل 16:30")
            return
        }

        val endTime = ClassTimeUtils.parse(etEndTime.text?.toString().orEmpty())
        if (endTime == null) {
            etEndTime.error = "ساعت پایان معتبر وارد کنید"
            etEndTime.requestFocus()
            AppToast.warning(this, "ساعت پایان کلاس را با فرمت صحیح وارد کنید؛ مثل 18:00")
            return
        }

        if (endTime.minutesFromMidnight <= startTime.minutesFromMidnight) {
            etEndTime.error = "ساعت پایان باید بعد از ساعت شروع باشد"
            etEndTime.requestFocus()
            AppToast.warning(this, "ساعت پایان کلاس باید بعد از ساعت شروع باشد")
            return
        }

        val sessionCount = etSessionCount.text?.toString()?.trim()?.toIntOrNull()
        if (sessionCount == null || sessionCount < 1) {
            etSessionCount.error = "تعداد جلسات باید حداقل ۱ باشد"
            etSessionCount.requestFocus()
            AppToast.warning(this, "تعداد جلسات را به‌صورت عدد و حداقل ۱ وارد کنید")
            return
        }

        val minPassRaw =
            normalizeBoundaryInput(
                etMinPassingScore.text
                    ?.toString()
                    ?.trim()
                    .orEmpty()
            )

        val minCondRaw =
            normalizeBoundaryInput(
                etMinConditionalScore.text
                    ?.toString()
                    ?.trim()
                    .orEmpty()
            )

        val minPass =
            minPassRaw.toDoubleOrNull()

        val minCond =
            minCondRaw.toDoubleOrNull()

        if (minPass == null) {
            etMinPassingScore.error = "حد قبولی را به‌صورت عدد وارد کنید"
            etMinPassingScore.requestFocus()
            AppToast.warning(this, "حد قبولی را به‌صورت عدد وارد کنید")
            return
        }
        if (minCond == null) {
            etMinConditionalScore.error = "حد مشروطی را به‌صورت عدد وارد کنید"
            etMinConditionalScore.requestFocus()
            AppToast.warning(this, "حد مشروطی را به‌صورت عدد وارد کنید")
            return
        }
        if (minCond < 0.0 || minPass <= 0.0) {
            etMinConditionalScore.error = "مرزهای نمره نمی‌توانند منفی باشند"
            etMinConditionalScore.requestFocus()
            AppToast.warning(this, "حد قبولی و مشروطی باید مقادیر معتبر و غیرمنفی باشند")
            return
        }
        if (minPass >= 83.0) {
            etMinPassingScore.error = "حد پایین قبولی باید حتماً کمتر از ۸۳ باشد"
            etMinPassingScore.requestFocus()
            AppToast.warning(this, "حد قبولی باید کمتر از ۸۳ باشد")
            return
        }
        if (minCond >= minPass) {
            etMinConditionalScore.error = "حد مشروطی باید کمتر از حد قبولی باشد"
            etMinConditionalScore.requestFocus()
            AppToast.warning(this, "حد مشروطی باید کمتر از حد قبولی باشد")
            return
        }

        val selectedDays = mutableListOf<String>()
        for (index in 0 until chipGroupDays.childCount) {
            val chip = chipGroupDays.getChildAt(index) as? Chip ?: continue
            if (chip.isChecked) selectedDays += chip.text.toString()
        }

        if (selectedDays.isEmpty()) {
            AppToast.makeText(this, "حداقل یک روز برگزاری را انتخاب کنید", Toast.LENGTH_LONG).show()
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
                    AppToast.success(this@AddEditClassActivity, "کلاس با موفقیت ساخته شد")
                    setResult(RESULT_OK); finish()
                } else {
                    AppToast.makeText(this@AddEditClassActivity, response.body()?.message?.takeIf { it.isNotBlank() } ?: ApiErrorParser.userMessage(response, "ثبت کلاس کامل نشد"), Toast.LENGTH_LONG).show()
                }
            }
            override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                setSavingState(false)
                AppToast.makeText(this@AddEditClassActivity, ApiErrorParser.networkMessage(t, "ثبت اطلاعات کلاس"), Toast.LENGTH_LONG).show()
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
                    AppToast.success(this@AddEditClassActivity, "اطلاعات کلاس با موفقیت ویرایش شد")
                    setResult(RESULT_OK); finish()
                } else {
                    AppToast.makeText(
                        this@AddEditClassActivity,
                        response.body()?.message?.takeIf { it.isNotBlank() } ?: ApiErrorParser.userMessage(response, "ویرایش کلاس کامل نشد"),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                setSavingState(false)
                AppToast.makeText(this@AddEditClassActivity, ApiErrorParser.networkMessage(t, "ویرایش اطلاعات کلاس"), Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun setupKeyboardFlow() {
        val normalFields = listOf(
            etClassCode,
            etClassLevel,
            etBookName,
            etTermYear,
            etSessionCount,
            etMinPassingScore,
            etMinConditionalScore
        )

        normalFields.forEach { field ->
            field.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) ensureFieldVisible(field)
            }
        }

        spinnerClassName.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) ensureFieldVisible(spinnerClassName)
        }
        spinnerTermSeason.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) ensureFieldVisible(spinnerTermSeason)
        }

        setNextAction(etClassCode) {
            openDropdown(spinnerClassName)
        }
        setNextAction(etClassLevel) {
            focusField(etBookName)
        }
        setNextAction(etBookName) {
            focusField(etTermYear)
        }
        setNextAction(etTermYear) {
            openDropdown(spinnerTermSeason)
        }
        setNextAction(etStartTime) {
            focusField(etEndTime)
        }
        setNextAction(etEndTime) {
            focusField(etSessionCount)
        }
        setNextAction(etSessionCount) {
            focusField(etMinPassingScore)
        }
        setNextAction(etMinPassingScore) {
            focusField(etMinConditionalScore)
        }

        etMinConditionalScore.setOnEditorActionListener { view, actionId, event ->
            val donePressed =
                actionId == EditorInfo.IME_ACTION_DONE ||
                        (event?.keyCode == KeyEvent.KEYCODE_ENTER &&
                                event.action == KeyEvent.ACTION_DOWN)

            if (donePressed) {
                hideKeyboard(view)
                view.clearFocus()
                btnSaveClass.postDelayed({
                    ensureFieldVisible(btnSaveClass, extraBottomDp = 16)
                }, 120)
                true
            } else {
                false
            }
        }
    }

    private fun setNextAction(
        field: TextInputEditText,
        action: () -> Unit
    ) {
        field.setOnEditorActionListener { _, actionId, event ->
            val nextPressed =
                actionId == EditorInfo.IME_ACTION_NEXT ||
                        (event?.keyCode == KeyEvent.KEYCODE_ENTER &&
                                event.action == KeyEvent.ACTION_DOWN)

            if (nextPressed) {
                action()
                true
            } else {
                false
            }
        }
    }

    private fun focusField(field: TextInputEditText) {
        field.requestFocus()
        ensureFieldVisible(field)

        field.postDelayed({
            val inputMethodManager =
                getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

            inputMethodManager.showSoftInput(
                field,
                InputMethodManager.SHOW_IMPLICIT
            )

            // یک بار بعد از باز شدن/انیمیشن کیبورد نیز موقعیت را اصلاح می‌کنیم.
            field.postDelayed({
                ensureFieldVisible(
                    field,
                    extraBottomDp = 24,
                    smooth = false
                )
            }, 280)
        }, 80)
    }

    private fun openDropdown(dropdown: MaterialAutoCompleteTextView) {
        hideKeyboard(currentFocus)
        dropdown.requestFocus()
        ensureFieldVisible(dropdown)
        dropdown.postDelayed({
            if (dropdown.isEnabled) {
                dropdown.showDropDown()
            }
        }, 120)
    }

    private fun hideKeyboard(view: View?) {
        val inputMethodManager =
            getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val token = view?.windowToken ?: currentFocus?.windowToken
        if (token != null) {
            inputMethodManager.hideSoftInputFromWindow(token, 0)
        }
    }

    /**
     * این صفحه به‌صورت Edge-to-Edge اجرا می‌شود. در این حالت روی برخی نسخه‌های
     * Android، adjustResize به‌تنهایی ارتفاع قابل مشاهده را کم نمی‌کند و کیبورد
     * روی فیلدهای انتهایی می‌افتد.
     *
     * این listener ارتفاع واقعی IME را می‌گیرد و همان مقدار را به padding پایین
     * ریشه صفحه اضافه می‌کند؛ در نتیجه NestedScrollView واقعاً فقط فضای بالای
     * کیبورد را در اختیار دارد.
     */
    private fun setupImeAwareLayout() {
        val root = findViewById<View>(R.id.rootAddEditClass)

        val initialLeft = root.paddingLeft
        val initialTop = root.paddingTop
        val initialRight = root.paddingRight
        val initialBottom = root.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())

            val bottomInset = if (imeVisible) {
                maxOf(bars.bottom, ime.bottom)
            } else {
                bars.bottom
            }

            view.setPadding(
                initialLeft + bars.left,
                initialTop + bars.top,
                initialRight + bars.right,
                initialBottom + bottomInset
            )

            if (imeVisible) {
                scrollClassForm.post {
                    currentFocus?.let { focused ->
                        if (isClassFormField(focused)) {
                            ensureFieldVisible(focused, extraBottomDp = 24)
                        }
                    }
                }
            }

            insets
        }

        ViewCompat.setWindowInsetsAnimationCallback(
            root,
            object : WindowInsetsAnimationCompat.Callback(
                WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_CONTINUE_ON_SUBTREE
            ) {
                override fun onProgress(
                    insets: WindowInsetsCompat,
                    runningAnimations: MutableList<WindowInsetsAnimationCompat>
                ): WindowInsetsCompat {
                    return insets
                }

                override fun onEnd(animation: WindowInsetsAnimationCompat) {
                    super.onEnd(animation)

                    val insets = ViewCompat.getRootWindowInsets(root)
                    val imeVisible =
                        insets?.isVisible(WindowInsetsCompat.Type.ime()) == true

                    if (imeVisible) {
                        scrollClassForm.post {
                            currentFocus?.let { focused ->
                                if (isClassFormField(focused)) {
                                    ensureFieldVisible(
                                        focused,
                                        extraBottomDp = 24,
                                        smooth = false
                                    )
                                }
                            }
                        }
                    }
                }
            }
        )

        ViewCompat.requestApplyInsets(root)
    }

    private fun isClassFormField(view: View): Boolean =
        view === etClassCode ||
                view === spinnerClassName ||
                view === etClassLevel ||
                view === etBookName ||
                view === etTermYear ||
                view === spinnerTermSeason ||
                view === etStartTime ||
                view === etEndTime ||
                view === etSessionCount ||
                view === etMinPassingScore ||
                view === etMinConditionalScore ||
                view === btnSaveClass

    /**
     * فیلد فوکوس‌شده را داخل محدوده واقعی NestedScrollView نگه می‌دارد.
     * برخلاف requestRectangleOnScreen، این محاسبه مستقیماً بر اساس viewport
     * کوچک‌شده بعد از باز شدن IME انجام می‌شود.
     */
    private fun ensureFieldVisible(
        view: View,
        extraBottomDp: Int = 24,
        smooth: Boolean = true
    ) {
        scrollClassForm.post {
            if (!view.isShown) return@post

            val rect = Rect()
            view.getDrawingRect(rect)
            scrollClassForm.offsetDescendantRectToMyCoords(view, rect)

            val density = resources.displayMetrics.density
            val topGap = (12f * density).toInt()
            val bottomGap = (extraBottomDp * density).toInt()

            val viewportTop =
                scrollClassForm.scrollY + topGap

            val viewportBottom =
                scrollClassForm.scrollY +
                        scrollClassForm.height -
                        scrollClassForm.paddingBottom -
                        bottomGap

            val delta = when {
                rect.bottom > viewportBottom ->
                    rect.bottom - viewportBottom

                rect.top < viewportTop ->
                    rect.top - viewportTop

                else -> 0
            }

            if (delta != 0) {
                if (smooth) {
                    scrollClassForm.smoothScrollBy(0, delta)
                } else {
                    scrollClassForm.scrollBy(0, delta)
                }
            }
        }
    }

    private fun setTimeFormatter(field: TextInputEditText) {
        field.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                ensureFieldVisible(field)
            } else {
                ClassTimeUtils.parse(field.text?.toString().orEmpty())
                    ?.let { field.setText(it.formatted) }
            }
        }
    }

    private fun setSavingState(saving: Boolean) {
        btnSaveClass.isEnabled = !saving
        progressSaving.visibility = if (saving) View.VISIBLE else View.GONE
    }

    private fun normalizeBoundaryInput(
        value: String
    ): String =
        UiTextFormatter.normalizeEnglishDigits(value)
            .replace('٫', '.')
            .replace(',', '.')
            .replace('،', '.')
            .replace('/', '.')

    private fun formatBoundary(
        value: Double
    ): String =
        if (value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            value.toString()
        }

    /**
     * فقط نمایش رقم‌ها را فارسی می‌کند؛ مقدار واقعی فیلد دست‌نخورده می‌ماند.
     */
    private class PersianNumericTransformationMethod :
        ReplacementTransformationMethod() {

        override fun getOriginal(): CharArray =
            charArrayOf(
                '0', '1', '2', '3', '4',
                '5', '6', '7', '8', '9',
                '.'
            )

        override fun getReplacement(): CharArray =
            charArrayOf(
                '۰', '۱', '۲', '۳', '۴',
                '۵', '۶', '۷', '۸', '۹',
                '/'
            )
    }

    private fun normalizeDayLabel(value: String): String = value.replace("\u200C", "").replace(" ", "").trim()

    companion object { const val EXTRA_CLASS_ID = "CLASS_ID" }
}
