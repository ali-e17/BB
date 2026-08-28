package com.example.bb

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

class ReportCardSetupActivity : BaseActivity() {

    private val classes = mutableListOf<ClassModel>()
    private val components = mutableListOf<EditableComponent>()
    private var selectedClass: ClassModel? = null
    private var configRevision = 0
    private var loading = false
    private var configCall: Call<ReportConfigResponse>? = null

    private lateinit var classDropdown: MaterialAutoCompleteTextView
    private lateinit var selectedInfo: TextView
    private lateinit var passRuleText: TextView
    private lateinit var conditionalRuleText: TextView
    private lateinit var componentContainer: LinearLayout
    private lateinit var totalText: TextView
    private lateinit var totalHint: TextView
    private lateinit var totalProgress: LinearProgressIndicator
    private lateinit var addButton: MaterialButton
    private lateinit var continueButton: MaterialButton
    private lateinit var progress: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report_card_setup)

        findViewById<ImageView>(R.id.btnSetupBack).setOnClickListener { finish() }

        classDropdown = findViewById(R.id.dropdownClassTarget)
        selectedInfo = findViewById(R.id.txtSelectedClassInfo)
        passRuleText = findViewById(R.id.txtPassRule)
        conditionalRuleText = findViewById(R.id.txtConditionalRule)
        componentContainer = findViewById(R.id.containerFields)
        totalText = findViewById(R.id.txtTotalSumValue)
        totalHint = findViewById(R.id.txtTotalHint)
        totalProgress = findViewById(R.id.progressTotalScore)
        addButton = findViewById(R.id.btnAddCriterion)
        continueButton = findViewById(R.id.btnSaveLayout)
        progress = findViewById(R.id.progressReportSetup)

        val role = getSharedPreferences("LocalAppPrefs", MODE_PRIVATE)
            .getString("CURRENT_USER_ROLE", "STUDENT")
            .orEmpty()
            .uppercase(Locale.ROOT)

        findViewById<MaterialButton>(R.id.btnEditResultMessages).apply {
            visibility = if (role == "ADMIN") View.VISIBLE else View.GONE
            setOnClickListener {
                startActivity(
                    Intent(
                        this@ReportCardSetupActivity,
                        ResultMessagesActivity::class.java
                    )
                )
            }
        }

        addButton.setOnClickListener {
            if (components.size >= 8) {
                AppToast.warning(this, "حداکثر ۸ معیار برای کارنامه قابل تعریف است")
            } else {
                components += EditableComponent(
                    UUID.randomUUID().toString(),
                    "",
                    0.0
                )
                renderComponents()
            }
        }

        continueButton.setOnClickListener { saveAndContinue() }
        loadClasses()
    }

    private fun loadClasses() {
        setLoading(true)

        RetrofitClient.instance.getClasses()
            .enqueue(object : Callback<List<ClassModel>> {
                override fun onResponse(
                    call: Call<List<ClassModel>>,
                    response: Response<List<ClassModel>>
                ) {
                    setLoading(false)
                    classes.clear()

                    val classesLoadedSuccessfully = response.isSuccessful
                    if (classesLoadedSuccessfully) {
                        val prefs = getSharedPreferences("LocalAppPrefs", MODE_PRIVATE)
                        val role = prefs
                            .getString("CURRENT_USER_ROLE", "STUDENT")
                            .orEmpty()
                            .uppercase(Locale.ROOT)
                        val userId = prefs
                            .getString("CURRENT_USER_ID", "")
                            .orEmpty()

                        classes += response.body().orEmpty().filter {
                            it.status == ClassStatus.ACTIVE &&
                                (role == "ADMIN" ||
                                    (role == "TEACHER" && it.teacherId == userId))
                        }
                    } else {
                        AppToast.error(
                            this@ReportCardSetupActivity,
                            ApiErrorParser.userMessage(
                                response,
                                "دریافت کلاس‌های قابل صدور کارنامه کامل نشد"
                            )
                        )
                    }

                    classDropdown.setAdapter(
                        ArrayAdapter(
                            this@ReportCardSetupActivity,
                            android.R.layout.simple_dropdown_item_1line,
                            classes.map { it.className }
                        )
                    )

                    classDropdown.setOnClickListener {
                        if (!loading) classDropdown.showDropDown()
                    }
                    classDropdown.setOnFocusChangeListener { _, hasFocus ->
                        if (hasFocus && !loading) classDropdown.showDropDown()
                    }

                    classDropdown.setOnItemClickListener { _, _, position, _ ->
                        selectedClass = classes.getOrNull(position)
                        selectedClass?.let { selected ->
                            classDropdown.setText(selected.className, false)
                            bindSelectedClass(selected)
                            loadConfig(selected.id)
                        }
                    }

                    if (classes.isEmpty()) {
                        selectedInfo.text = if (classesLoadedSuccessfully) {
                            "کلاس فعالی برای صدور کارنامه وجود ندارد"
                        } else {
                            "فهرست کلاس‌ها دریافت نشد"
                        }
                        passRuleText.text = "حد قبولی: —"
                        conditionalRuleText.text = "حد مشروطی: —"
                        if (classesLoadedSuccessfully) {
                            AppToast.info(this@ReportCardSetupActivity, "کلاس فعالی برای صدور کارنامه وجود ندارد")
                        }
                    }

                    updateTotal()
                }

                override fun onFailure(call: Call<List<ClassModel>>, t: Throwable) {
                    setLoading(false)
                    val message = ApiErrorParser.networkMessage(t, "دریافت کلاس‌های قابل صدور کارنامه")
                    selectedInfo.text = message
                    AppToast.error(this@ReportCardSetupActivity, message)
                    updateTotal()
                }
            })
    }

    private fun bindSelectedClass(selected: ClassModel) {
        selectedInfo.text = buildClassInfo(selected)
        passRuleText.text =
            "حد قبولی بدون ستاره: ${format(selected.minPassingScore)}"
        conditionalRuleText.text =
            "حد مشروطی: ${format(selected.minConditionalScore)}"
        updateTotal()
    }

    private fun loadConfig(classId: String) {
        configCall?.cancel()
        setLoading(true)

        val request = RetrofitClient.instance.getReportConfig(classId)
        configCall = request
        request.enqueue(object : Callback<ReportConfigResponse> {
            override fun onResponse(
                call: Call<ReportConfigResponse>,
                response: Response<ReportConfigResponse>
            ) {
                if (selectedClass?.id != classId) return
                setLoading(false)

                val body = response.body()
                if (!response.isSuccessful || body?.status != "success") {
                    toast(
                        body?.message?.takeIf { it.isNotBlank() }
                            ?: ApiErrorParser.userMessage(response, "دریافت تنظیمات کارنامه کامل نشد")
                    )
                    return
                }

                components.clear()
                val config = body.config
                if (config != null) {
                    configRevision = config.revision
                    components += config.components
                        .sortedBy { it.sortOrder }
                        .map { EditableComponent(it.id, it.title, it.maxScore) }
                } else {
                    configRevision = 0
                    components += defaultComponents()
                }
                renderComponents()
            }

            override fun onFailure(call: Call<ReportConfigResponse>, t: Throwable) {
                if (call.isCanceled || selectedClass?.id != classId) return
                setLoading(false)
                toast(ApiErrorParser.networkMessage(t, "دریافت تنظیمات کارنامه"))
            }
        })
    }

    private fun defaultComponents(): List<EditableComponent> = listOf(
        EditableComponent(UUID.randomUUID().toString(), "Work Book", 15.0),
        EditableComponent(UUID.randomUUID().toString(), "Class Activity", 15.0),
        EditableComponent(UUID.randomUUID().toString(), "Attendance", 10.0),
        EditableComponent(UUID.randomUUID().toString(), "Midterm", 20.0),
        EditableComponent(UUID.randomUUID().toString(), "Oral", 15.0),
        EditableComponent(UUID.randomUUID().toString(), "Final", 25.0)
    )

    private fun renderComponents() {
        componentContainer.removeAllViews()

        components.forEachIndexed { index, component ->
            val row = LayoutInflater.from(this).inflate(
                R.layout.item_report_component_edit,
                componentContainer,
                false
            )

            val number = row.findViewById<TextView>(R.id.txtComponentNumber)
            val titleInput =
                row.findViewById<TextInputEditText>(R.id.etComponentTitle)
            val maxInput =
                row.findViewById<TextInputEditText>(R.id.etComponentMax)

            number.text = (index + 1).toString()
            titleInput.setText(component.title)
            maxInput.setText(
                if (component.maxScore > 0.0) format(component.maxScore) else ""
            )

            titleInput.addTextChangedListener(simpleTextWatcher {
                component.title = it.trim()
            })

            maxInput.addTextChangedListener(simpleTextWatcher { raw ->
                component.maxScore = raw.trim().toDoubleOrNull() ?: 0.0
                updateTotalFromModels()
            })

            row.findViewById<View>(R.id.btnRemoveComponent).setOnClickListener {
                if (components.size == 1) {
                    AppToast.makeText(
                        this,
                        "برای ادامه، تعریف حداقل یک معیار الزامی است",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    syncRows()
                    confirmComponentRemoval(component)
                }
            }

            componentContainer.addView(row)
        }

        addButton.isEnabled = !loading
        addButton.alpha = if (!loading && components.size < 8) 1.0f else 0.72f
        updateTotalFromModels()
    }


    private fun confirmComponentRemoval(component: EditableComponent) {
        val displayTitle = component.title.trim().ifBlank { "این معیار" }

        MaterialAlertDialogBuilder(this)
            .setTitle("حذف معیار")
            .setMessage("آیا از حذف «$displayTitle» مطمئن هستید؟")
            .setNegativeButton("انصراف", null)
            .setPositiveButton("حذف معیار") { _, _ ->
                components.remove(component)
                renderComponents()
            }
            .show()
    }

    private fun simpleTextWatcher(afterChange: (String) -> Unit): TextWatcher =
        object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) = Unit

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) = Unit

            override fun afterTextChanged(s: Editable?) {
                afterChange(s?.toString().orEmpty())
            }
        }

    private fun syncRows() {
        for (index in 0 until componentContainer.childCount) {
            val row = componentContainer.getChildAt(index)
            components.getOrNull(index)?.apply {
                title = row.findViewById<TextInputEditText>(R.id.etComponentTitle)
                    .text
                    ?.toString()
                    ?.trim()
                    .orEmpty()

                maxScore = row.findViewById<TextInputEditText>(R.id.etComponentMax)
                    .text
                    ?.toString()
                    ?.trim()
                    ?.toDoubleOrNull()
                    ?: 0.0
            }
        }
    }

    private fun updateTotal() {
        syncRows()
        updateTotalFromModels()
    }

    private fun updateTotalFromModels() {
        val total = components.sumOf { it.maxScore }
        val validTotal = abs(total - 100.0) < 0.001

        totalText.text = "${format(total)} / 100"
        totalProgress.progress = total.roundToInt().coerceIn(0, 100)

        when {
            validTotal -> {
                totalText.setTextColor(0xFF10B981.toInt())
                totalHint.text = "مجموع بارم‌ها کامل است"
                totalHint.setTextColor(0xFF10B981.toInt())
                totalProgress.setIndicatorColor(0xFF10B981.toInt())
            }

            total < 100.0 -> {
                totalText.setTextColor(0xFFFF6E14.toInt())
                totalHint.text =
                    "${format(100.0 - total)} نمره تا تکمیل بارم باقی مانده است"
                totalHint.setTextColor(0xFFFF6E14.toInt())
                totalProgress.setIndicatorColor(0xFFFF6E14.toInt())
            }

            else -> {
                totalText.setTextColor(0xFFEF4444.toInt())
                totalHint.text =
                    "مجموع بارم‌ها ${format(total - 100.0)} نمره بیشتر از ۱۰۰ است"
                totalHint.setTextColor(0xFFEF4444.toInt())
                totalProgress.setIndicatorColor(0xFFEF4444.toInt())
            }
        }

        updateContinueState()
    }

    private fun updateContinueState() {
        val validTotal =
            abs(components.sumOf { it.maxScore } - 100.0) < 0.001
        val ready = selectedClass != null && validTotal

        continueButton.isEnabled = !loading
        continueButton.alpha = if (!loading && ready) 1.0f else 0.72f
        addButton.isEnabled = !loading
        addButton.alpha = if (!loading && components.size < 8) 1.0f else 0.72f
        classDropdown.isEnabled = !loading
    }

    private fun saveAndContinue() {
        val selected = selectedClass
            ?: return toast("ابتدا کلاس موردنظر برای صدور کارنامه را انتخاب کنید")

        syncRows()

        when {
            components.isEmpty() || components.size > 8 ->
                return toast("تعداد معیارها باید بین ۱ تا ۸ باشد")

            components.any { it.title.isBlank() || it.maxScore <= 0.0 } ->
                return toast("نام و بارم همه معیارها را کامل کنید")

            components.map { it.title.lowercase(Locale.ROOT) }
                .distinct().size != components.size ->
                return toast("نام معیار تکراری است")

            abs(components.sumOf { it.maxScore } - 100.0) > 0.001 ->
                return toast("مجموع بارم‌ها باید دقیقاً ۱۰۰ باشد")
        }

        val request = SaveReportConfigRequest(
            classId = selected.id,
            passWithoutStarMin = selected.minPassingScore,
            conditionalMin = selected.minConditionalScore,
            expectedRevision = configRevision,
            components = components.mapIndexed { index, component ->
                ReportComponentDto(
                    id = component.id,
                    title = component.title,
                    maxScore = component.maxScore,
                    sortOrder = index + 1
                )
            }
        )

        setLoading(true)

        RetrofitClient.instance.saveReportConfig(request)
            .enqueue(object : Callback<SaveReportConfigResponse> {
                override fun onResponse(
                    call: Call<SaveReportConfigResponse>,
                    response: Response<SaveReportConfigResponse>
                ) {
                    setLoading(false)
                    val body = response.body()

                    if (response.isSuccessful && body?.status == "success") {
                        configRevision = body.revision
                        toast(body.message.ifBlank { "تنظیمات کارنامه ذخیره شد" })
                        startActivity(
                            Intent(
                                this@ReportCardSetupActivity,
                                GradeEntryActivity::class.java
                            )
                                .putExtra(
                                    GradeEntryActivity.EXTRA_CLASS_ID,
                                    selected.id
                                )
                                .putExtra(
                                    GradeEntryActivity.EXTRA_CLASS_NAME,
                                    selected.className
                                )
                        )
                        finish()
                    } else {
                        toast(
                            body?.message?.takeIf { it.isNotBlank() }
                                ?: ApiErrorParser.userMessage(response, "ذخیره تنظیمات کارنامه کامل نشد")
                        )
                    }
                }

                override fun onFailure(
                    call: Call<SaveReportConfigResponse>,
                    t: Throwable
                ) {
                    setLoading(false)
                    toast(ApiErrorParser.networkMessage(t, "ذخیره تنظیمات کارنامه"))
                }
            })
    }

    private fun buildClassInfo(selected: ClassModel): String {
        val term = listOf(selected.termSeason, selected.termYear)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { "—" }

        return buildString {
            append("نام کلاس: ")
            append(selected.className.ifBlank { "—" })
            append('\n')
            append("کد کلاس: ")
            append(selected.classCode.ifBlank { "—" })
            append('\n')
            append("سطح: ")
            append(selected.classLevel.ifBlank { "—" })
            append('\n')
            append("کتاب: ")
            append(selected.bookName.ifBlank { "—" })
            append('\n')
            append("ترم: ")
            append(term)
        }
    }

    private fun setLoading(value: Boolean) {
        loading = value
        progress.visibility = if (value) View.VISIBLE else View.GONE
        updateContinueState()
    }

    private fun format(value: Double): String =
        if (value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            String.format(Locale.US, "%.2f", value)
        }

    private fun toast(message: String) {
        AppToast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        configCall?.cancel()
        super.onDestroy()
    }

    private data class EditableComponent(
        val id: String,
        var title: String,
        var maxScore: Double
    )
}
