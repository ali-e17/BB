package com.example.bb

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.UUID

class ReportCardSetupActivity : AppCompatActivity() {
    private val classes = mutableListOf<ClassModel>()
    private val components = mutableListOf<EditableComponent>()
    private var selectedClass: ClassModel? = null
    private var configRevision = 0

    private lateinit var classDropdown: MaterialAutoCompleteTextView
    private lateinit var selectedInfo: TextView
    private lateinit var componentContainer: LinearLayout
    private lateinit var totalText: TextView
    private lateinit var passInput: TextInputEditText
    private lateinit var conditionalInput: TextInputEditText
    private lateinit var addButton: MaterialButton
    private lateinit var continueButton: MaterialButton
    private lateinit var progress: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report_card_setup)
        findViewById<ImageView>(R.id.btnSetupBack).setOnClickListener { finish() }
        classDropdown = findViewById(R.id.dropdownClassTarget)
        selectedInfo = findViewById(R.id.txtSelectedClassInfo)
        componentContainer = findViewById(R.id.containerFields)
        totalText = findViewById(R.id.txtTotalSumValue)
        passInput = findViewById(R.id.etPassNoStarMin)
        conditionalInput = findViewById(R.id.etConditionalMin)
        addButton = findViewById(R.id.btnAddCriterion)
        continueButton = findViewById(R.id.btnSaveLayout)
        progress = findViewById(R.id.progressReportSetup)
        val role = getSharedPreferences("LocalAppPrefs", MODE_PRIVATE).getString("CURRENT_USER_ROLE", "STUDENT")
        findViewById<MaterialButton>(R.id.btnEditResultMessages).apply {
            visibility = if (role == "ADMIN") View.VISIBLE else View.GONE
            setOnClickListener { startActivity(Intent(this@ReportCardSetupActivity, ResultMessagesActivity::class.java)) }
        }

        addButton.setOnClickListener {
            if (components.size >= 8) Toast.makeText(this, "حداکثر ۸ معیار مجاز است", Toast.LENGTH_SHORT).show()
            else { components += EditableComponent(UUID.randomUUID().toString(), "", 0.0); renderComponents() }
        }
        continueButton.setOnClickListener { saveAndContinue() }
        loadClasses()
    }

    private fun loadClasses() {
        setLoading(true)
        RetrofitClient.instance.getClasses().enqueue(object : Callback<List<ClassModel>> {
            override fun onResponse(call: Call<List<ClassModel>>, response: Response<List<ClassModel>>) {
                setLoading(false)
                classes.clear()
                if (response.isSuccessful) {
                    val prefs = getSharedPreferences("LocalAppPrefs", MODE_PRIVATE)
                    val role = prefs.getString("CURRENT_USER_ROLE", "STUDENT").orEmpty().uppercase()
                    val userId = prefs.getString("CURRENT_USER_ID", "").orEmpty()
                    classes += response.body().orEmpty().filter {
                        it.status == ClassStatus.ACTIVE &&
                            (role == "ADMIN" || (role == "TEACHER" && it.teacherId == userId))
                    }
                }
                classDropdown.setAdapter(ArrayAdapter(this@ReportCardSetupActivity, android.R.layout.simple_dropdown_item_1line, classes.map { it.className }))
                classDropdown.setOnItemClickListener { _, _, position, _ ->
                    selectedClass = classes.getOrNull(position)
                    selectedClass?.let { c ->
                        classDropdown.setText(c.className, false)
                        selectedInfo.text = buildClassInfo(c)
                        loadConfig(c.id)
                    }
                }
                if (classes.isEmpty()) selectedInfo.text = "کلاس فعالی برای صدور کارنامه وجود ندارد"
            }
            override fun onFailure(call: Call<List<ClassModel>>, t: Throwable) {
                setLoading(false); selectedInfo.text = "دریافت کلاس‌ها انجام نشد"
            }
        })
    }

    private fun loadConfig(classId: String) {
        setLoading(true)
        RetrofitClient.instance.getReportConfig(classId).enqueue(object : Callback<ReportConfigResponse> {
            override fun onResponse(call: Call<ReportConfigResponse>, response: Response<ReportConfigResponse>) {
                setLoading(false)
                val config = response.body()?.config
                components.clear()
                if (response.isSuccessful && config != null) {
                    configRevision = config.revision
                    passInput.setText(format(config.passWithoutStarMin))
                    conditionalInput.setText(format(config.conditionalMin))
                    components += config.components.sortedBy { it.sortOrder }.map { EditableComponent(it.id, it.title, it.maxScore) }
                } else {
                    configRevision = 0
                    passInput.setText("78")
                    conditionalInput.setText("70")
                    components += listOf(
                        EditableComponent(UUID.randomUUID().toString(), "Work Book", 15.0),
                        EditableComponent(UUID.randomUUID().toString(), "Class Activity", 15.0),
                        EditableComponent(UUID.randomUUID().toString(), "Attendance", 10.0),
                        EditableComponent(UUID.randomUUID().toString(), "Midterm", 20.0),
                        EditableComponent(UUID.randomUUID().toString(), "Oral", 15.0),
                        EditableComponent(UUID.randomUUID().toString(), "Final", 25.0)
                    )
                }
                renderComponents()
            }
            override fun onFailure(call: Call<ReportConfigResponse>, t: Throwable) {
                setLoading(false); Toast.makeText(this@ReportCardSetupActivity, "دریافت تنظیمات کارنامه انجام نشد", Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun renderComponents() {
        componentContainer.removeAllViews()
        components.forEachIndexed { index, component ->
            val row = LayoutInflater.from(this).inflate(R.layout.item_report_component_edit, componentContainer, false)
            val title = row.findViewById<TextInputEditText>(R.id.etComponentTitle)
            val max = row.findViewById<TextInputEditText>(R.id.etComponentMax)
            title.setText(component.title); max.setText(if (component.maxScore > 0) format(component.maxScore) else "")
            title.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) component.title = title.text?.toString()?.trim().orEmpty() }
            max.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) { component.maxScore = max.text?.toString()?.toDoubleOrNull() ?: 0.0; updateTotal() } }
            row.findViewById<View>(R.id.btnRemoveComponent).setOnClickListener {
                if (components.size == 1) Toast.makeText(this, "حداقل یک معیار لازم است", Toast.LENGTH_SHORT).show()
                else { syncRows(); components.removeAt(index); renderComponents() }
            }
            componentContainer.addView(row)
        }
        addButton.isEnabled = components.size < 8
        updateTotal()
    }

    private fun syncRows() {
        for (i in 0 until componentContainer.childCount) {
            val row = componentContainer.getChildAt(i)
            components.getOrNull(i)?.apply {
                title = row.findViewById<TextInputEditText>(R.id.etComponentTitle).text?.toString()?.trim().orEmpty()
                maxScore = row.findViewById<TextInputEditText>(R.id.etComponentMax).text?.toString()?.toDoubleOrNull() ?: 0.0
            }
        }
    }

    private fun updateTotal() {
        syncRowsSafe()
        val total = components.sumOf { it.maxScore }
        totalText.text = "مجموع بارم: ${format(total)} از ۱۰۰"
        totalText.setTextColor(if (kotlin.math.abs(total - 100.0) < 0.001) 0xFF027A48.toInt() else 0xFFB42318.toInt())
    }

    private fun syncRowsSafe() {
        for (i in 0 until componentContainer.childCount) {
            val row = componentContainer.getChildAt(i)
            components.getOrNull(i)?.let { c ->
                c.title = row.findViewById<TextInputEditText>(R.id.etComponentTitle).text?.toString()?.trim().orEmpty()
                c.maxScore = row.findViewById<TextInputEditText>(R.id.etComponentMax).text?.toString()?.toDoubleOrNull() ?: 0.0
            }
        }
    }

    private fun saveAndContinue() {
        val c = selectedClass ?: return Toast.makeText(this, "کلاس را انتخاب کنید", Toast.LENGTH_SHORT).show()
        syncRows()
        val pass = passInput.text?.toString()?.toDoubleOrNull()
        val conditional = conditionalInput.text?.toString()?.toDoubleOrNull()
        when {
            components.isEmpty() || components.size > 8 -> return toast("تعداد معیارها باید بین ۱ تا ۸ باشد")
            components.any { it.title.isBlank() || it.maxScore <= 0 } -> return toast("نام و بارم همه معیارها را کامل کنید")
            components.map { it.title.lowercase() }.distinct().size != components.size -> return toast("نام معیار تکراری است")
            kotlin.math.abs(components.sumOf { it.maxScore } - 100.0) > 0.001 -> return toast("مجموع بارم‌ها باید دقیقاً ۱۰۰ باشد")
            pass == null || conditional == null || conditional < 0 || conditional >= pass || pass >= 83 -> return toast("مرزها باید ۰ ≤ مشروطی < قبولی بدون ستاره < ۸۳ باشند")
        }
        val request = SaveReportConfigRequest(c.id, pass!!, conditional!!, configRevision, components.mapIndexed { index, x -> ReportComponentDto(x.id, x.title, x.maxScore, index + 1) })
        setLoading(true)
        RetrofitClient.instance.saveReportConfig(request).enqueue(object : Callback<SaveReportConfigResponse> {
            override fun onResponse(call: Call<SaveReportConfigResponse>, response: Response<SaveReportConfigResponse>) {
                setLoading(false); val body = response.body()
                if (response.isSuccessful && body?.status == "success") {
                    startActivity(Intent(this@ReportCardSetupActivity, GradeEntryActivity::class.java)
                        .putExtra(GradeEntryActivity.EXTRA_CLASS_ID, c.id)
                        .putExtra(GradeEntryActivity.EXTRA_CLASS_NAME, c.className))
                } else toast(body?.message ?: "ذخیره تنظیمات انجام نشد")
            }
            override fun onFailure(call: Call<SaveReportConfigResponse>, t: Throwable) { setLoading(false); toast("ارتباط با سرور برقرار نشد") }
        })
    }

    private fun buildClassInfo(c: ClassModel): String = listOf(
        c.classCode.takeIf(String::isNotBlank)?.let { "کد $it" },
        c.bookName.takeIf(String::isNotBlank)?.let { "کتاب $it" },
        c.classLevel.takeIf(String::isNotBlank)?.let { "سطح $it" },
        listOf(c.termSeason, c.termYear).filter(String::isNotBlank).joinToString(" ").takeIf(String::isNotBlank)
    ).filterNotNull().joinToString("  •  ").ifBlank { "اطلاعات تکمیلی این کلاس هنوز کامل نشده" }

    private fun setLoading(value: Boolean) { progress.visibility = if (value) View.VISIBLE else View.GONE; continueButton.isEnabled = !value }
    private fun format(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else "%.2f".format(v)
    private fun toast(s: String) { Toast.makeText(this, s, Toast.LENGTH_LONG).show() }
    private data class EditableComponent(val id: String, var title: String, var maxScore: Double)
}
