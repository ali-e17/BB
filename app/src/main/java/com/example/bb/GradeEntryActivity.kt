package com.example.bb

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class GradeEntryActivity : AppCompatActivity() {

    private lateinit var selectedClassId: String
    private lateinit var selectedClassName: String
    private lateinit var activeCriteria: List<GradeComponent>

    private val allStudents = mutableListOf<StudentGrade>()
    private val visibleStudents = mutableListOf<StudentGrade>()

    private lateinit var recycler: RecyclerView
    private lateinit var adapter: StudentGradeAdapter
    private lateinit var txtProgress: TextView
    private lateinit var txtStorageHint: TextView
    private lateinit var btnSaveDraft: Button
    private lateinit var btnPublish: Button
    private lateinit var searchInput: TextInputEditText

    private var suppressAutoSave = false
    private var dataLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_grade_entry)

        selectedClassName = intent.getStringExtra("SELECTED_CLASS") ?: "نامشخص"
        selectedClassId = intent.getStringExtra("SELECTED_CLASS_ID") ?: ""
        @Suppress("DEPRECATION")
        activeCriteria = intent.getSerializableExtra("ACTIVE_CRITERIA") as? ArrayList<GradeComponent>
            ?: emptyList()

        findViewById<ImageView>(R.id.btnEntryBack).setOnClickListener { closeAndSaveDraft() }
        findViewById<TextView>(R.id.txtClassTitle).text = "نمرات $selectedClassName"

        recycler = findViewById(R.id.recyclerStudents)
        txtProgress = findViewById(R.id.txtGradeProgress)
        txtStorageHint = findViewById(R.id.txtGradeStorageHint)
        btnSaveDraft = findViewById(R.id.btnSaveDraft)
        btnPublish = findViewById(R.id.btnPublishLayout)
        searchInput = findViewById(R.id.etSearchGradeStudent)

        recycler.layoutManager = LinearLayoutManager(this)
        adapter = StudentGradeAdapter(
            students = visibleStudents,
            activeCriteria = activeCriteria,
            className = selectedClassName,
            onStatusChanged = {
                updateProgress()
            }
        )
        recycler.adapter = adapter

        btnSaveDraft.setOnClickListener { saveDraft(showToast = true) }
        btnPublish.setOnClickListener { requestPublish() }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                applySearch(s?.toString().orEmpty())
            }
        })

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = closeAndSaveDraft()
        })

        renderStorageState()
        loadStudents()
    }

    override fun onPause() {
        super.onPause()
        if (!suppressAutoSave && dataLoaded && allStudents.isNotEmpty()) {
            saveDraft(showToast = false)
        }
    }

    private fun loadStudents() {
        setLoading(true)
        RetrofitClient.instance.getStudents().enqueue(object : Callback<List<StudentModel>> {
            override fun onResponse(
                call: Call<List<StudentModel>>,
                response: Response<List<StudentModel>>
            ) {
                val serverStudents = response.body()
                if (response.isSuccessful && serverStudents != null) {
                    AppDatabase.replaceStudents(serverStudents)
                    buildGradeList(serverStudents)
                } else {
                    buildGradeList(AppDatabase.getAllStudents())
                    Toast.makeText(
                        this@GradeEntryActivity,
                        "پاسخ سرور معتبر نبود؛ اطلاعات ذخیره‌شده دستگاه نمایش داده شد",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onFailure(call: Call<List<StudentModel>>, t: Throwable) {
                buildGradeList(AppDatabase.getAllStudents())
                Toast.makeText(
                    this@GradeEntryActivity,
                    "لیست دانش‌آموزان از اطلاعات ذخیره‌شده دستگاه نمایش داده شد",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    private fun buildGradeList(source: List<StudentModel>) {
        val classStudents = source.filter {
            it.isActive && it.classId == selectedClassId
        }

        val draft = AppDatabase.getReportCardDraft(selectedClassId)
        val publishedByStudent = AppDatabase.getPublishedReportCardsForClass(selectedClassId)
            .associateBy { it.studentId }

        allStudents.clear()
        classStudents.forEach { student ->
            val draftScores = draft?.scoresByStudent?.get(student.id)
            val publishedScores = publishedByStudent[student.id]?.scores

            val scores = activeCriteria.associate { criterion ->
                val value: Int? = when {
                    draftScores?.containsKey(criterion.id) == true -> draftScores[criterion.id]
                    publishedScores?.containsKey(criterion.id) == true -> publishedScores[criterion.id]
                    else -> 0
                }
                criterion.id to value
            }.toMutableMap()

            allStudents += StudentGrade(
                id = student.id,
                name = student.name,
                studentCode = student.studentCode.ifBlank { student.id },
                status = EntryStatus.COMPLETED,
                scores = scores
            )
        }

        dataLoaded = true
        setLoading(false)
        applySearch(searchInput.text?.toString().orEmpty())
        updateProgress()

        if (allStudents.isEmpty()) {
            Toast.makeText(this, "این کلاس دانش‌آموز فعالی ندارد", Toast.LENGTH_LONG).show()
        }
    }

    private fun applySearch(rawQuery: String) {
        val query = rawQuery.trim()
        visibleStudents.clear()
        visibleStudents += if (query.isBlank()) {
            allStudents
        } else {
            allStudents.filter { it.name.contains(query, ignoreCase = true) }
        }
        adapter.updateStudents(visibleStudents)
        findViewById<TextView>(R.id.txtEmptyGrades).visibility =
            if (dataLoaded && visibleStudents.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun updateProgress() {
        val completed = allStudents.count { it.status == EntryStatus.COMPLETED }
        txtProgress.text = "$completed از ${allStudents.size} کارنامه آماده انتشار"

        val canPublish = allStudents.isNotEmpty() &&
            activeCriteria.isNotEmpty() &&
            allStudents.all { it.status == EntryStatus.COMPLETED }

        btnSaveDraft.isEnabled = allStudents.isNotEmpty()
        btnSaveDraft.alpha = if (allStudents.isNotEmpty()) 1f else 0.5f
        btnPublish.isEnabled = canPublish
        btnPublish.alpha = if (canPublish) 1f else 0.5f

        btnPublish.text = if (AppDatabase.hasPublishedReportCards(selectedClassId)) {
            "ذخیره و بروزرسانی کارنامه‌ها"
        } else {
            "انتشار نهایی کارنامه‌ها"
        }
    }

    private fun saveDraft(showToast: Boolean) {
        if (!dataLoaded || allStudents.isEmpty() || activeCriteria.isEmpty()) return
        AppDatabase.saveReportCardDraft(selectedClassId, activeCriteria, allStudents)
        renderStorageState()
        if (showToast) {
            Toast.makeText(this, "پیش‌نویس نمرات روی این دستگاه ذخیره شد", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestPublish() {
        if (allStudents.isEmpty()) return
        if (allStudents.any { it.status != EntryStatus.COMPLETED }) {
            Toast.makeText(this, "ابتدا نمره‌های نامعتبر یا خالی را اصلاح کنید", Toast.LENGTH_SHORT).show()
            return
        }

        val allZeroCount = allStudents.count { student ->
            activeCriteria.all { criterion -> (student.scores[criterion.id] ?: 0) == 0 }
        }

        val message = buildString {
            append("کارنامه‌های ${allStudents.size} دانش‌آموز منتشر می‌شود.")
            if (allZeroCount > 0) {
                append("\n\nنمرات $allZeroCount دانش‌آموز در تمام معیارها صفر است. از درست بودن آن مطمئن شوید.")
            }
            if (AppDatabase.hasPublishedReportCards(selectedClassId)) {
                append("\n\nنسخه منتشرشده قبلی بروزرسانی می‌شود و رکورد تکراری ساخته نخواهد شد.")
            }
        }

        AlertDialog.Builder(this)
            .setTitle(if (AppDatabase.hasPublishedReportCards(selectedClassId)) "بروزرسانی کارنامه‌ها" else "انتشار کارنامه‌ها")
            .setMessage(message)
            .setNegativeButton("بررسی دوباره", null)
            .setPositiveButton("تأیید و انتشار") { _, _ -> publish() }
            .show()
    }

    private fun publish() {
        AppDatabase.publishReportCards(selectedClassId, activeCriteria, allStudents)
        suppressAutoSave = true
        Toast.makeText(
            this,
            "کارنامه‌های کلاس $selectedClassName با موفقیت منتشر شد",
            Toast.LENGTH_LONG
        ).show()
        finish()
    }

    private fun closeAndSaveDraft() {
        if (dataLoaded && allStudents.isNotEmpty()) saveDraft(showToast = false)
        suppressAutoSave = true
        finish()
    }

    private fun setLoading(loading: Boolean) {
        findViewById<View>(R.id.progressGrades).visibility = if (loading) View.VISIBLE else View.GONE
        recycler.visibility = if (loading) View.INVISIBLE else View.VISIBLE
    }

    private fun renderStorageState() {
        txtStorageHint.text = when {
            AppDatabase.getReportCardDraft(selectedClassId) != null ->
                "پیش‌نویس ذخیره شده است؛ تا انتشار نهایی فقط روی همین دستگاه دیده می‌شود."
            AppDatabase.hasPublishedReportCards(selectedClassId) ->
                "کارنامه قبلاً منتشر شده است؛ تغییرات جدید را دوباره منتشر کنید."
            else ->
                "نمره‌ها فعلاً محلی ذخیره می‌شوند و هنوز به cPanel ارسال نمی‌شوند."
        }
    }
}
