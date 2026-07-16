package com.example.bb

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputLayout
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ReportCardSetupActivity : AppCompatActivity() {

    private val componentTemplates = listOf(
        GradeComponent("1", "Work Book", 0, false),
        GradeComponent("2", "Class Activity", 0, false),
        GradeComponent("3", "Attendance", 0, false),
        GradeComponent("4", "Midterm", 0, false),
        GradeComponent("5", "Oral", 0, false),
        GradeComponent("6", "Final", 0, false)
    )

    private val components = mutableListOf<GradeComponent>()
    private val classes = mutableListOf<ClassModel>()

    private lateinit var stepClassPanel: View
    private lateinit var stepCriteriaPanel: View
    private lateinit var txtStepTitle: TextView
    private lateinit var stepProgress: LinearProgressIndicator

    private lateinit var dropdownClassTarget: AutoCompleteTextView
    private lateinit var btnChooseClassContinue: MaterialButton
    private lateinit var txtClassSelectionHint: TextView

    private lateinit var txtSelectedClassName: TextView
    private lateinit var txtSelectedClassTime: TextView
    private lateinit var containerFields: LinearLayout
    private lateinit var txtTotalSumValue: TextView
    private lateinit var txtSelectedCount: TextView
    private lateinit var txtValidationHint: TextView
    private lateinit var btnSaveLayout: MaterialButton

    private var selectedClass: ClassModel? = null
    private var currentStep = STEP_CLASS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report_card_setup)

        bindViews()
        resetComponents()
        showClassStep()
        loadClasses()

        findViewById<ImageView>(R.id.btnSetupBack).setOnClickListener { handleBack() }

        btnChooseClassContinue.setOnClickListener {
            val selected = selectedClass
            if (selected == null) {
                Toast.makeText(this, "ابتدا یک کلاس را انتخاب کنید", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            hideKeyboard()
            restoreSavedCriteria(selected.id)
            showCriteriaStep()
        }

        btnSaveLayout.setOnClickListener {
            val selected = selectedClass ?: return@setOnClickListener
            val activeFields = components.filter { it.isSelected }.map { it.copy() }

            if (!isCriteriaValid(activeFields)) {
                Toast.makeText(this, "بارم‌بندی را کامل و مجموع را ۱۰۰ کنید", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            hideKeyboard()
            startActivity(
                Intent(this, GradeEntryActivity::class.java)
                    .putExtra("ACTIVE_CRITERIA", ArrayList(activeFields))
                    .putExtra("SELECTED_CLASS", selected.className)
                    .putExtra("SELECTED_CLASS_ID", selected.id)
            )
        }
    }

    override fun onResume() {
        super.onResume()
        if (currentStep == STEP_CRITERIA) {
            selectedClass?.let { restoreSavedCriteria(it.id) }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        handleBack()
    }

    private fun bindViews() {
        stepClassPanel = findViewById(R.id.stepClassPanel)
        stepCriteriaPanel = findViewById(R.id.stepCriteriaPanel)
        txtStepTitle = findViewById(R.id.txtStepTitle)
        stepProgress = findViewById(R.id.stepProgress)

        dropdownClassTarget = findViewById(R.id.dropdownClassTarget)
        btnChooseClassContinue = findViewById(R.id.btnChooseClassContinue)
        txtClassSelectionHint = findViewById(R.id.txtClassSelectionHint)

        txtSelectedClassName = findViewById(R.id.txtSelectedClassName)
        txtSelectedClassTime = findViewById(R.id.txtSelectedClassTime)
        containerFields = findViewById(R.id.containerFields)
        txtTotalSumValue = findViewById(R.id.txtTotalSumValue)
        txtSelectedCount = findViewById(R.id.txtSelectedCount)
        txtValidationHint = findViewById(R.id.txtValidationHint)
        btnSaveLayout = findViewById(R.id.btnSaveLayout)
    }

    private fun handleBack() {
        if (currentStep == STEP_CRITERIA) {
            hideKeyboard()
            showClassStep()
        } else {
            finish()
        }
    }

    private fun showClassStep() {
        currentStep = STEP_CLASS
        stepClassPanel.visibility = View.VISIBLE
        stepCriteriaPanel.visibility = View.GONE
        txtStepTitle.text = "مرحله ۱ از ۲ · انتخاب کلاس"
        stepProgress.setProgressCompat(50, true)
        updateClassContinueState()
    }

    private fun showCriteriaStep() {
        currentStep = STEP_CRITERIA
        stepClassPanel.visibility = View.GONE
        stepCriteriaPanel.visibility = View.VISIBLE
        txtStepTitle.text = "مرحله ۲ از ۲ · تنظیم معیارها"
        stepProgress.setProgressCompat(100, true)

        selectedClass?.let {
            txtSelectedClassName.text = it.className
            txtSelectedClassTime.text = it.classTime.ifBlank { "برنامه زمانی ثبت نشده" }
        }

        buildDynamicFields()
        calculateLiveTotal()
    }

    private fun loadClasses() {
        setClasses(AppDatabase.getAllClasses(false))

        RetrofitClient.instance.getClasses().enqueue(object : Callback<List<ClassModel>> {
            override fun onResponse(
                call: Call<List<ClassModel>>,
                response: Response<List<ClassModel>>
            ) {
                val result = response.body()
                if (response.isSuccessful && result != null) {
                    AppDatabase.replaceClasses(result)
                    setClasses(result)
                }
            }

            override fun onFailure(call: Call<List<ClassModel>>, t: Throwable) {
                if (classes.isEmpty()) {
                    txtClassSelectionHint.text = "دریافت کلاس‌ها ممکن نشد؛ اتصال اینترنت را بررسی کنید."
                    txtClassSelectionHint.setTextColor(
                        ContextCompat.getColor(this@ReportCardSetupActivity, android.R.color.holo_red_dark)
                    )
                }
            }
        })
    }

    private fun setClasses(newClasses: List<ClassModel>) {
        val previousId = selectedClass?.id

        classes.clear()
        classes.addAll(
            newClasses
                .filter { it.status == ClassStatus.ACTIVE }
                .distinctBy { it.id }
        )

        dropdownClassTarget.setAdapter(ClassDropdownAdapter(this, classes))
        dropdownClassTarget.setOnItemClickListener { _, _, position, _ ->
            selectedClass = classes.getOrNull(position)
            selectedClass?.let {
                dropdownClassTarget.setText(it.className, false)
                txtClassSelectionHint.text = it.classTime.ifBlank { "برنامه زمانی ثبت نشده" }
                txtClassSelectionHint.setTextColor(ContextCompat.getColor(this, R.color.sub_text))
            }
            updateClassContinueState()
        }

        if (previousId != null) {
            classes.find { it.id == previousId }?.let {
                selectedClass = it
                dropdownClassTarget.setText(it.className, false)
                txtClassSelectionHint.text = it.classTime.ifBlank { "برنامه زمانی ثبت نشده" }
            }
        }

        if (classes.isEmpty()) {
            txtClassSelectionHint.text = "کلاس فعالی برای ثبت کارنامه وجود ندارد."
            btnChooseClassContinue.isEnabled = false
            btnChooseClassContinue.alpha = 0.5f
        } else {
            updateClassContinueState()
        }
    }

    private fun updateClassContinueState() {
        val enabled = selectedClass != null
        btnChooseClassContinue.isEnabled = enabled
        btnChooseClassContinue.alpha = if (enabled) 1f else 0.5f
    }

    private fun resetComponents() {
        components.clear()
        components.addAll(componentTemplates.map { it.copy() })
    }

    private fun restoreSavedCriteria(classId: String) {
        val saved = AppDatabase.getSavedReportCardCriteria(classId)

        components.clear()
        componentTemplates.forEach { template ->
            val previous = saved?.find { it.id == template.id }
            components += previous?.copy() ?: template.copy()
        }

        btnSaveLayout.text = if (AppDatabase.hasPublishedReportCards(classId)) {
            "ادامه و ویرایش نمرات"
        } else {
            "تأیید و ورود به ثبت نمرات"
        }

        if (currentStep == STEP_CRITERIA) {
            buildDynamicFields()
            calculateLiveTotal()
        }
    }

    private fun buildDynamicFields() {
        containerFields.removeAllViews()
        val inflater = LayoutInflater.from(this)

        components.forEach { component ->
            val row = inflater.inflate(R.layout.item_setup_field, containerFields, false)
            val card = row.findViewById<MaterialCardView>(R.id.cardCriterion)
            val selectionArea = row.findViewById<View>(R.id.selectionArea)
            val checkbox = row.findViewById<MaterialCheckBox>(R.id.checkboxField)
            val name = row.findViewById<TextView>(R.id.txtFieldName)
            val scoreLayout = row.findViewById<TextInputLayout>(R.id.scoreInputLayout)
            val scoreInput = row.findViewById<EditText>(R.id.etMaxScore)

            name.text = component.name
            checkbox.isChecked = component.isSelected
            scoreInput.setText(component.maxScore.toString())

            fun renderRow() {
                val selected = component.isSelected
                val activeColor = ContextCompat.getColor(this, R.color.title_blue)

                card.strokeWidth = if (selected) dpToPx(2) else dpToPx(1)
                card.setStrokeColor(if (selected) activeColor else 0x1A2B4E78)
                scoreLayout.isEnabled = selected
                scoreInput.isEnabled = selected
                scoreInput.alpha = if (selected) 1f else 0.55f
                name.alpha = 1f
            }

            checkbox.setOnCheckedChangeListener(null)
            checkbox.isChecked = component.isSelected
            checkbox.setOnCheckedChangeListener { _, checked ->
                component.isSelected = checked
                if (!checked) {
                    component.maxScore = 0
                    scoreInput.setText("0")
                    scoreLayout.error = null
                }
                renderRow()
                calculateLiveTotal()
            }

            selectionArea.setOnClickListener {
                checkbox.isChecked = !checkbox.isChecked
            }

            scoreInput.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

                override fun afterTextChanged(s: Editable?) {
                    val value = s?.toString()?.trim()?.toIntOrNull() ?: 0
                    component.maxScore = value
                    scoreLayout.error = if (value > 100) "حداکثر ۱۰۰" else null
                    calculateLiveTotal()
                }
            })

            scoreInput.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus && scoreInput.text.toString() == "0") {
                    scoreInput.selectAll()
                } else if (!hasFocus && scoreInput.text.isNullOrBlank()) {
                    scoreInput.setText("0")
                }
            }

            renderRow()
            containerFields.addView(row)
        }
    }

    private fun calculateLiveTotal() {
        val active = components.filter { it.isSelected }
        val total = active.sumOf { it.maxScore }
        val invalidScore = active.any { it.maxScore !in 1..100 }
        val valid = selectedClass != null && active.isNotEmpty() && !invalidScore && total == 100

        txtTotalSumValue.text = "$total / 100"
        txtSelectedCount.text = "${active.size} معیار انتخاب شده"

        val color = ContextCompat.getColor(
            this,
            if (valid) android.R.color.holo_green_dark else android.R.color.holo_red_dark
        )
        txtTotalSumValue.setTextColor(color)
        txtValidationHint.setTextColor(color)

        txtValidationHint.text = when {
            active.isEmpty() -> "حداقل یک معیار را فعال کنید."
            invalidScore -> "بارم هر معیار فعال باید بین ۱ تا ۱۰۰ باشد."
            total < 100 -> "${100 - total} نمره تا تکمیل بارم‌بندی باقی مانده است."
            total > 100 -> "مجموع بارم ${total - 100} نمره بیشتر از حد مجاز است."
            else -> "بارم‌بندی کامل است و می‌توانید ادامه دهید."
        }

        btnSaveLayout.isEnabled = valid
        btnSaveLayout.alpha = if (valid) 1f else 0.5f
    }

    private fun isCriteriaValid(items: List<GradeComponent>): Boolean =
        items.isNotEmpty() &&
            items.all { it.maxScore in 1..100 } &&
            items.sumOf { it.maxScore } == 100

    private fun hideKeyboard() {
        currentFocus?.let { view ->
            val manager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            manager.hideSoftInputFromWindow(view.windowToken, 0)
            view.clearFocus()
        }
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

    private class ClassDropdownAdapter(
        private val adapterContext: Context,
        private val items: List<ClassModel>
    ) : ArrayAdapter<ClassModel>(adapterContext, android.R.layout.simple_list_item_2, items) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
            createView(position, convertView, parent)

        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
            createView(position, convertView, parent)

        private fun createView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(adapterContext)
                .inflate(android.R.layout.simple_list_item_2, parent, false)
            val item = items[position]

            view.findViewById<TextView>(android.R.id.text1).apply {
                text = item.className
                setTextColor(ContextCompat.getColor(adapterContext, R.color.main_text))
                setTypeface(typeface, Typeface.BOLD)
                textSize = 15f
                maxLines = 2
            }

            view.findViewById<TextView>(android.R.id.text2).apply {
                text = item.classTime.ifBlank { "برنامه زمانی ثبت نشده" }
                setTextColor(ContextCompat.getColor(adapterContext, R.color.sub_text))
                textSize = 12f
                maxLines = 2
            }

            view.setPadding(24, 16, 24, 16)
            return view
        }
    }

    companion object {
        private const val STEP_CLASS = 1
        private const val STEP_CRITERIA = 2
    }
}
