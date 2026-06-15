package com.example.bb

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class ReportCardSetupActivity : AppCompatActivity() {

    private val defaultComponents = mutableListOf(
        GradeComponent("1", "Work Book", 18),
        GradeComponent("2", "Class Activity", 13),
        GradeComponent("3", "Attendance", 5),
        GradeComponent("4", "Midterm", 20),
        GradeComponent("5", "Oral", 20),
        GradeComponent("6", "Final", 20)
    )

    private lateinit var containerFields: LinearLayout
    private lateinit var txtTotalSumValue: TextView
    private lateinit var txtValidationHint: TextView
    private lateinit var btnSaveLayout: Button
    private lateinit var dropdownClassTarget: AutoCompleteTextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report_card_setup)

        findViewById<ImageView>(R.id.btnSetupBack).setOnClickListener { finish() }

        containerFields = findViewById(R.id.containerFields)
        txtTotalSumValue = findViewById(R.id.txtTotalSumValue)
        txtValidationHint = findViewById(R.id.txtValidationHint)
        btnSaveLayout = findViewById(R.id.btnSaveLayout)
        dropdownClassTarget = findViewById(R.id.dropdownClassTarget)

        // راه‌اندازی منوی کشویی کلاس‌ها
        val classes = arrayOf("کلاس ترم ۶", "کلاس آیلتس فشرده", "کلاس مکالمه مبتدی")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, classes)
        dropdownClassTarget.setAdapter(adapter)

        buildDynamicFields()
        calculateLiveTotal()

        btnSaveLayout.setOnClickListener {
            val selectedClass = dropdownClassTarget.text.toString()

            // جلوگیری از عبور در صورت انتخاب نشدن کلاس
            if (selectedClass.isEmpty()) {
                Toast.makeText(this, "لطفاً ابتدا یک کلاس را انتخاب کنید", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val intent = Intent(this, GradeEntryActivity::class.java)

            // فیلدهای فعال و اسم کلاس رو به صفحه بعد می‌فرستیم
            val activeFields = defaultComponents.filter { it.isSelected }
            intent.putExtra("ACTIVE_CRITERIA", ArrayList(activeFields))
            intent.putExtra("SELECTED_CLASS", selectedClass)

            startActivity(intent)
            // دستور finish() رو ننوشتیم تا ادمین بتونه با دکمه بک برگرده اینجا
        }
    }

    private fun buildDynamicFields() {
        val inflater = LayoutInflater.from(this)

        for (component in defaultComponents) {
            val rowView = inflater.inflate(R.layout.item_setup_field, containerFields, false)

            val rowMainLayout = rowView.findViewById<View>(R.id.rowMainLayout)
            val checkBox = rowView.findViewById<CheckBox>(R.id.checkboxField)
            val txtName = rowView.findViewById<TextView>(R.id.txtFieldName)
            val etMax = rowView.findViewById<EditText>(R.id.etMaxScore)
            val btnIncrease = rowView.findViewById<TextView>(R.id.btnIncrease)
            val btnDecrease = rowView.findViewById<TextView>(R.id.btnDecrease)

            txtName.text = component.name
            etMax.setText(component.maxScore.toString())
            checkBox.isChecked = component.isSelected

            rowMainLayout.alpha = if (component.isSelected) 1.0f else 0.5f
            etMax.isEnabled = component.isSelected
            btnIncrease.isEnabled = component.isSelected
            btnDecrease.isEnabled = component.isSelected

            checkBox.setOnCheckedChangeListener { _, isChecked ->
                component.isSelected = isChecked
                etMax.isEnabled = isChecked
                btnIncrease.isEnabled = isChecked
                btnDecrease.isEnabled = isChecked
                rowMainLayout.alpha = if (isChecked) 1.0f else 0.5f
                calculateLiveTotal()
            }

            btnIncrease.setOnClickListener {
                val current = etMax.text.toString().toIntOrNull() ?: 0
                etMax.setText((current + 1).toString())
            }

            btnDecrease.setOnClickListener {
                val current = etMax.text.toString().toIntOrNull() ?: 0
                if (current > 0) {
                    etMax.setText((current - 1).toString())
                }
            }

            etMax.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val enteredValue = s.toString().toIntOrNull() ?: 0
                    component.maxScore = enteredValue
                    calculateLiveTotal()
                }
            })

            containerFields.addView(rowView)
        }
    }

    private fun calculateLiveTotal() {
        var currentSum = 0
        var activeFieldsCount = 0

        for (component in defaultComponents) {
            if (component.isSelected) {
                currentSum += component.maxScore
                activeFieldsCount++
            }
        }

        txtTotalSumValue.text = "$currentSum / 100"

        if (currentSum == 100 && activeFieldsCount <= 8) {
            txtTotalSumValue.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            txtValidationHint.text = "بارم‌بندی معتبر است. آماده ثبت نمرات."
            txtValidationHint.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            btnSaveLayout.isEnabled = true
            btnSaveLayout.alpha = 1.0f
        } else {
            txtTotalSumValue.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            txtValidationHint.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            btnSaveLayout.isEnabled = false
            btnSaveLayout.alpha = 0.5f

            if (currentSum != 100) {
                txtValidationHint.text = "خطا: مجموع بارم‌بندی باید دقیقاً ۱۰۰ باشد."
            } else {
                txtValidationHint.text = "خطا: تعداد معیارهای فعال نمی‌تواند بیش از ۸ باشد."
            }
        }
    }
}