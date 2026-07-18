package com.example.bb

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ClassDetailsActivity : AppCompatActivity() {

    private enum class ScreenMode { MEMBERS, ADD_STUDENT }

    private lateinit var classId: String
    private var className: String = "کلاس"
    private var isEditable = true
    private var requestInFlight = false
    private var screenMode = ScreenMode.MEMBERS

    private val allStudents = arrayListOf<StudentModel>()
    private val visibleStudents = arrayListOf<StudentModel>()
    private val classesById = linkedMapOf<String, ClassModel>()

    private lateinit var toggleGroup: MaterialButtonToggleGroup
    private lateinit var btnMembersTab: MaterialButton
    private lateinit var btnAddStudentsTab: MaterialButton
    private lateinit var layoutSearch: TextInputLayout
    private lateinit var etSearchStudent: TextInputEditText
    private lateinit var rvStudents: RecyclerView
    private lateinit var tvEmptyState: TextView
    private lateinit var progressLoading: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_class_details)

        classId = intent.getStringExtra(EXTRA_CLASS_ID).orEmpty()
        className = intent.getStringExtra(EXTRA_CLASS_NAME).orEmpty().ifBlank { "اعضای کلاس" }

        if (classId.isBlank()) {
            Toast.makeText(this, "شناسه کلاس مشخص نشده است", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        findViewById<ImageView>(R.id.btnDetailsBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.txtClassName).text = className

        toggleGroup = findViewById(R.id.toggleClassStudents)
        btnMembersTab = findViewById(R.id.btnMembersTab)
        btnAddStudentsTab = findViewById(R.id.btnAddStudentsTab)
        layoutSearch = findViewById(R.id.layoutSearch)
        etSearchStudent = findViewById(R.id.etSearchStudent)
        rvStudents = findViewById(R.id.rvClassStudents)
        tvEmptyState = findViewById(R.id.tvStudentsEmpty)
        progressLoading = findViewById(R.id.progressClassMembers)

        rvStudents.layoutManager = LinearLayoutManager(this)
        rvStudents.adapter = createStudentsAdapter()

        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            screenMode = when (checkedId) {
                R.id.btnAddStudentsTab -> ScreenMode.ADD_STUDENT
                else -> ScreenMode.MEMBERS
            }
            etSearchStudent.text?.clear()
            updateModeUi()
            renderCurrentList()
        }

        etSearchStudent.doAfterTextChanged { renderCurrentList() }

        toggleGroup.check(R.id.btnMembersTab)
        loadClassesThenStudents()
    }

    private fun loadClassesThenStudents() {
        setLoading(true)
        RetrofitClient.instance.getClasses().enqueue(object : Callback<List<ClassModel>> {
            override fun onResponse(
                call: Call<List<ClassModel>>,
                response: Response<List<ClassModel>>
            ) {
                if (response.isSuccessful) {
                    val serverClasses = response.body().orEmpty()
                    classesById.clear()
                    serverClasses.forEach { classesById[it.id] = it }
                    AppDatabase.replaceClasses(serverClasses)
                } else {
                    useLocalClasses()
                }
                updateClassState()
                fetchStudents()
            }

            override fun onFailure(call: Call<List<ClassModel>>, t: Throwable) {
                useLocalClasses()
                updateClassState()
                fetchStudents()
            }
        })
    }

    private fun useLocalClasses() {
        classesById.clear()
        AppDatabase.getAllClasses().forEach { classesById[it.id] = it }
    }

    private fun updateClassState() {
        val model = classesById[classId] ?: AppDatabase.getClassById(classId)
        val tvClassTeacherName = findViewById<TextView>(R.id.tvClassTeacherName)

        if (model != null) {
            className = model.className
            isEditable = model.status == ClassStatus.ACTIVE
            findViewById<TextView>(R.id.txtClassName).text = className

            // 🌟 فقط نمایش نام استاد (بدون قابلیت تغییر از اینجا)
            tvClassTeacherName.visibility = View.VISIBLE
            if (!model.teacherPhone.isNullOrBlank()) {
                val teacher = AppDatabase.getAllTeachers().find { it.phone == model.teacherPhone }
                tvClassTeacherName.text = "استاد: ${teacher?.name ?: model.teacherPhone}"
            } else {
                tvClassTeacherName.text = "استاد: تعیین نشده"
            }
        }

        btnAddStudentsTab.isEnabled = isEditable
        btnAddStudentsTab.alpha = if (isEditable) 1f else 0.45f

        if (!isEditable && screenMode == ScreenMode.ADD_STUDENT) {
            toggleGroup.check(R.id.btnMembersTab)
        }

        updateModeUi()
    }

    private fun fetchStudents() {
        RetrofitClient.instance.getStudents().enqueue(object : Callback<List<StudentModel>> {
            override fun onResponse(
                call: Call<List<StudentModel>>,
                response: Response<List<StudentModel>>
            ) {
                setLoading(false)
                if (!response.isSuccessful) {
                    useLocalStudents("سرور لیست دانش‌آموزان را برنگرداند")
                    return
                }

                allStudents.clear()
                allStudents.addAll(response.body().orEmpty())
                AppDatabase.replaceStudents(allStudents)
                renderCurrentList()
            }

            override fun onFailure(call: Call<List<StudentModel>>, t: Throwable) {
                setLoading(false)
                useLocalStudents("دریافت دانش‌آموزان از سرور ناموفق بود؛ اطلاعات محلی نمایش داده شد")
            }
        })
    }

    private fun useLocalStudents(message: String) {
        allStudents.clear()
        allStudents.addAll(AppDatabase.getAllStudents())
        renderCurrentList()
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun updateModeUi() {
        val queryHint = when (screenMode) {
            ScreenMode.MEMBERS -> "جست‌وجو بین اعضای کلاس"
            ScreenMode.ADD_STUDENT -> "جست‌وجوی نام، کد یا شماره تماس"
        }
        layoutSearch.hint = queryHint
        layoutSearch.visibility = View.VISIBLE
    }

    private fun renderCurrentList() {
        val query = etSearchStudent.text?.toString().orEmpty().trim()
        val membersCount = allStudents.count { it.classId == classId }
        val availableCount = allStudents.count { it.isActive && it.classId != classId }

        btnMembersTab.text = "اعضای کلاس ($membersCount)"
        btnAddStudentsTab.text = "افزودن دانش‌آموز ($availableCount)"

        visibleStudents.clear()
        val source = when (screenMode) {
            ScreenMode.MEMBERS -> allStudents.asSequence().filter { it.classId == classId }
            ScreenMode.ADD_STUDENT -> allStudents.asSequence().filter {
                isEditable && it.isActive && it.classId != classId
            }
        }

        visibleStudents.addAll(
            source
                .filter {
                    query.isBlank() ||
                        it.name.contains(query, ignoreCase = true) ||
                        it.studentCode.contains(query, ignoreCase = true) ||
                        it.phone.contains(query)
                }
                .sortedWith(
                    when (screenMode) {
                        ScreenMode.MEMBERS -> compareBy<StudentModel> { it.name.lowercase() }
                        ScreenMode.ADD_STUDENT -> compareBy<StudentModel> { !it.classId.isNullOrBlank() }
                            .thenBy { it.name.lowercase() }
                    }
                )
                .toList()
        )

        rvStudents.adapter?.notifyDataSetChanged()
        tvEmptyState.text = when (screenMode) {
            ScreenMode.MEMBERS -> if (query.isBlank()) {
                "هنوز دانش‌آموزی در این کلاس ثبت نشده است"
            } else {
                "عضوی مطابق جست‌وجوی شما پیدا نشد"
            }
            ScreenMode.ADD_STUDENT -> if (query.isBlank()) {
                "دانش‌آموز فعالی برای افزودن وجود ندارد"
            } else {
                "دانش‌آموزی مطابق جست‌وجوی شما پیدا نشد"
            }
        }
        tvEmptyState.visibility = if (visibleStudents.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun createStudentsAdapter(): RecyclerView.Adapter<StudentViewHolder> =
        object : RecyclerView.Adapter<StudentViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StudentViewHolder {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_student_manage, parent, false)
                return StudentViewHolder(view)
            }

            override fun onBindViewHolder(holder: StudentViewHolder, position: Int) {
                val student = visibleStudents[position]
                holder.tvName.text = student.name

                when (screenMode) {
                    ScreenMode.MEMBERS -> {
                        holder.tvDescription.text = buildString {
                            append("کد: ${student.studentCode.ifBlank { "ندارد" }}")
                            append(" | شماره: ${student.phone}")
                        }
                        holder.btnAction.text = "حذف"
                        holder.btnAction.isEnabled = isEditable && !requestInFlight
                        holder.btnAction.setOnClickListener { confirmRemoveStudent(student) }
                    }

                    ScreenMode.ADD_STUDENT -> {
                        val currentClassName = student.classId?.let {
                            classesById[it]?.className ?: AppDatabase.getClassNameById(it)
                        }
                        holder.tvDescription.text = buildString {
                            append("کد: ${student.studentCode.ifBlank { "ندارد" }}")
                            append(" | ")
                            if (student.classId.isNullOrBlank()) {
                                append("بدون کلاس")
                            } else {
                                append("کلاس فعلی: ${currentClassName ?: "نامشخص"}")
                            }
                        }
                        holder.btnAction.text = if (student.classId.isNullOrBlank()) "افزودن" else "انتقال"
                        holder.btnAction.isEnabled = isEditable && !requestInFlight
                        holder.btnAction.setOnClickListener { confirmAddOrTransfer(student) }
                    }
                }
            }

            override fun getItemCount(): Int = visibleStudents.size
        }

    private fun confirmAddOrTransfer(student: StudentModel) {
        val currentClassId = student.classId
        if (currentClassId.isNullOrBlank()) {
            updateStudentClass(student, classId, "دانش‌آموز به کلاس اضافه شد")
            return
        }

        val currentClassName = classesById[currentClassId]?.className
            ?: AppDatabase.getClassNameById(currentClassId)
            ?: "کلاس قبلی"

        AlertDialog.Builder(this)
            .setTitle("انتقال دانش‌آموز")
            .setMessage(
                "${student.name} اکنون عضو «$currentClassName» است. " +
                    "با ادامه، از کلاس قبلی خارج و به «$className» منتقل می‌شود."
            )
            .setPositiveButton("انتقال") { _, _ ->
                updateStudentClass(student, classId, "دانش‌آموز به کلاس جدید منتقل شد")
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun confirmRemoveStudent(student: StudentModel) {
        AlertDialog.Builder(this)
            .setTitle("حذف از کلاس")
            .setMessage("${student.name} از کلاس «$className» خارج شود؟")
            .setPositiveButton("خارج کردن") { _, _ ->
                updateStudentClass(student, null, "دانش‌آموز از کلاس خارج شد")
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun updateStudentClass(
        student: StudentModel,
        targetClassId: String?,
        successMessage: String
    ) {
        if (requestInFlight) return
        requestInFlight = true
        renderCurrentList()

        RetrofitClient.instance.assignClass(
            AssignClassRequest(studentId = student.id, classId = targetClassId)
        ).enqueue(object : Callback<ApiResponse> {
            override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                requestInFlight = false
                val body = response.body()
                if (response.isSuccessful && body?.status == "success") {
                    Toast.makeText(
                        this@ClassDetailsActivity,
                        body.message.ifBlank { successMessage },
                        Toast.LENGTH_SHORT
                    ).show()
                    fetchStudents()
                } else {
                    renderCurrentList()
                    Toast.makeText(
                        this@ClassDetailsActivity,
                        body?.message?.takeIf { it.isNotBlank() }
                            ?: "تغییر عضویت در سرور ثبت نشد",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                requestInFlight = false
                renderCurrentList()
                Toast.makeText(
                    this@ClassDetailsActivity,
                    "ارتباط با سرور برای تغییر عضویت برقرار نشد",
                    Toast.LENGTH_LONG
                ).show()
            }
        })
    }

    private fun setLoading(loading: Boolean) {
        progressLoading.visibility = if (loading) View.VISIBLE else View.GONE
        rvStudents.visibility = if (loading) View.INVISIBLE else View.VISIBLE
    }

    class StudentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvStudentName)
        val tvDescription: TextView = view.findViewById(R.id.tvStudentPhone)
        val btnAction: MaterialButton = view.findViewById(R.id.btnAction)
    }

    companion object {
        const val EXTRA_CLASS_ID = "CLASS_ID"
        const val EXTRA_CLASS_NAME = "CLASS_NAME"
    }
}
