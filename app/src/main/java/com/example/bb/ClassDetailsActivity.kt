package com.example.bb

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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

class ClassDetailsActivity : BaseActivity() {

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
        // 🌟 وضعیت قفل یا باز بودن کلاس از اکتیویتی قبلی دریافت میشه
        isEditable = intent.getBooleanExtra(EXTRA_IS_EDITABLE, true)

        if (classId.isBlank()) {
            AppToast.error(this, "امکان نمایش اعضای کلاس وجود ندارد؛ شناسه کلاس ارسال نشده است")
            finish()
            return
        }

        findViewById<ImageView>(R.id.btnDetailsBack).setOnClickListener { finish() }

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

        // 🌟 به محض ورود، رابط کاربری رو بر اساس باز یا قفل بودن آپدیت می‌کنیم
        updateClassState()
        loadClassesThenStudents()
    }

    private fun loadClassesThenStudents() {
        setLoading(true)
        RetrofitClient.instance.getClasses().enqueue(object : Callback<List<ClassModel>> {
            override fun onResponse(call: Call<List<ClassModel>>, response: Response<List<ClassModel>>) {
                if (response.isSuccessful) {
                    val serverClasses = response.body().orEmpty()
                    classesById.clear()
                    serverClasses.forEach { classesById[it.id] = it }
                    AppDatabase.replaceClasses(serverClasses)
                } else {
                    useLocalClasses()
                    AppToast.error(
                        this@ClassDetailsActivity,
                        ApiErrorParser.userMessage(response, "دریافت اطلاعات کلاس کامل نشد") +
                            "؛ اطلاعات ذخیره‌شده دستگاه استفاده می‌شود"
                    )
                }
                updateClassState()
                fetchStudents()
            }

            override fun onFailure(call: Call<List<ClassModel>>, t: Throwable) {
                useLocalClasses()
                AppToast.error(
                    this@ClassDetailsActivity,
                    ApiErrorParser.networkMessage(t, "دریافت اطلاعات کلاس") +
                        " اطلاعات ذخیره‌شده دستگاه استفاده می‌شود."
                )
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
        val tvClassName = findViewById<TextView>(R.id.txtClassName)

        if (model != null) {
            className = model.className

            tvClassTeacherName.visibility = View.VISIBLE
            if (!model.teacherPhone.isNullOrBlank()) {
                val teacher = AppDatabase.getAllTeachers().find { it.phone == model.teacherPhone }
                tvClassTeacherName.text = "استاد: ${teacher?.name ?: model.teacherPhone}"
            } else {
                tvClassTeacherName.text = "استاد: تعیین نشده"
            }
        }

        tvClassName.text = if (isEditable) className else "$className (پایان‌یافته)"

        // 🌟 اگر قفل باشد (پایان یافته)، تمام تب‌ها مخفی می‌شوند
        if (!isEditable) {
            toggleGroup.visibility = View.GONE
            screenMode = ScreenMode.MEMBERS
        } else {
            toggleGroup.visibility = View.VISIBLE
            btnAddStudentsTab.isEnabled = true
        }

        updateModeUi()
    }

    private fun fetchStudents() {
        /*
         * کلاس فعال:
         * همان رفتار قبلی؛ فهرست جاری دانش‌آموزان از get_students.php می‌آید.
         *
         * کلاس پایان‌یافته:
         * students.class_id هنگام پایان ترم NULL می‌شود، بنابراین برای تاریخچه
         * باید اعضا را از class_enrollments همان کلاس بخوانیم.
         */
        val call = if (isEditable) {
            RetrofitClient.instance.getStudents()
        } else {
            RetrofitClient.instance.getClassMembers(classId)
        }

        call.enqueue(object : Callback<List<StudentModel>> {
            override fun onResponse(call: Call<List<StudentModel>>, response: Response<List<StudentModel>>) {
                setLoading(false)
                if (response.isSuccessful) {
                    allStudents.clear()
                    allStudents.addAll(response.body().orEmpty())

                    /*
                     * فقط فهرست جاری را در Cache عمومی دانش‌آموزها جایگزین می‌کنیم.
                     * اعضای تاریخی کلاس نباید classId فعلی دانش‌آموز را در Cache
                     * دستگاه بازنویسی کنند.
                     */
                    if (isEditable) {
                        AppDatabase.replaceStudents(allStudents)
                    }

                    renderCurrentList()
                } else {
                    if (isEditable) {
                        useLocalStudents(
                            ApiErrorParser.userMessage(
                                response,
                                "دریافت فهرست دانش‌آموزان کلاس کامل نشد"
                            ) + "؛ اطلاعات ذخیره‌شده دستگاه نمایش داده شدند"
                        )
                    } else {
                        allStudents.clear()
                        renderCurrentList()
                        AppToast.makeText(
                            this@ClassDetailsActivity,
                            ApiErrorParser.userMessage(
                                response,
                                "دریافت اعضای تاریخی کلاس کامل نشد"
                            ),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }

            override fun onFailure(call: Call<List<StudentModel>>, t: Throwable) {
                setLoading(false)
                if (isEditable) {
                    useLocalStudents(
                        ApiErrorParser.networkMessage(t, "دریافت فهرست دانش‌آموزان کلاس") +
                            " اطلاعات ذخیره‌شده دستگاه نمایش داده شدند."
                    )
                } else {
                    allStudents.clear()
                    renderCurrentList()
                    AppToast.makeText(
                        this@ClassDetailsActivity,
                        ApiErrorParser.networkMessage(t, "دریافت اعضای تاریخی کلاس"),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        })
    }

    private fun useLocalStudents(message: String) {
        allStudents.clear()
        allStudents.addAll(AppDatabase.getAllStudents())
        renderCurrentList()
        AppToast.makeText(this, message, Toast.LENGTH_LONG).show()
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
                        ScreenMode.ADD_STUDENT -> compareBy<StudentModel> { !it.classId.isNullOrBlank() }.thenBy { it.name.lowercase() }
                    }
                )
                .toList()
        )

        rvStudents.adapter?.notifyDataSetChanged()
        tvEmptyState.text = when (screenMode) {
            ScreenMode.MEMBERS -> if (query.isBlank()) "در حال حاضر دانش‌آموزی در این کلاس ثبت نشده است" else "عضوی مطابق جست‌وجوی شما پیدا نشد"
            ScreenMode.ADD_STUDENT -> if (query.isBlank()) "دانش‌آموز فعالی برای افزودن وجود ندارد" else "دانش‌آموزی مطابق جست‌وجوی شما پیدا نشد"
        }
        tvEmptyState.visibility = if (visibleStudents.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun createStudentsAdapter(): RecyclerView.Adapter<StudentViewHolder> =
        object : RecyclerView.Adapter<StudentViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StudentViewHolder {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_student_manage, parent, false)
                return StudentViewHolder(view)
            }

            override fun onBindViewHolder(holder: StudentViewHolder, position: Int) {
                val student = visibleStudents[position]
                val context = holder.itemView.context

                holder.tvName.text = student.name

                // avatar_no_profile خودش یک آواتار واقعی و قابل انتخاب است.
                // هیچ آواتار عددداری به عنوان fallback تحمیل نمی‌شود.
                val fallback = "avatar_no_profile"
                val avatarName = student.avatarName?.takeIf { it.isNotBlank() } ?: fallback
                val avatarResId = context.resources.getIdentifier(
                    avatarName,
                    "drawable",
                    context.packageName
                )
                val fallbackResId = context.resources.getIdentifier(
                    fallback,
                    "drawable",
                    context.packageName
                )

                holder.ivAvatar.setImageResource(
                    if (avatarResId != 0) avatarResId else fallbackResId
                )

                when (screenMode) {
                    ScreenMode.MEMBERS -> {
                        holder.tvDescription.text = buildString {
                            append("کد: ${student.studentCode.ifBlank { "ندارد" }}")
                            append(" | شماره: ${student.phone}")
                        }

                        // 🌟 قفل کردن دکمه حذف برای کلاس‌های پایان‌یافته
                        if (isEditable) {
                            holder.btnAction.visibility = View.VISIBLE
                            holder.btnAction.text = "حذف"
                            holder.btnAction.isEnabled = !requestInFlight
                            holder.btnAction.setOnClickListener { confirmRemoveStudent(student) }
                        } else {
                            holder.btnAction.visibility = View.GONE
                        }
                    }

                    ScreenMode.ADD_STUDENT -> {
                        val currentClassName = student.classId?.let { classesById[it]?.className ?: AppDatabase.getClassNameById(it) }
                        holder.tvDescription.text = buildString {
                            append("کد: ${student.studentCode.ifBlank { "ندارد" }}")
                            append(" | ")
                            if (student.classId.isNullOrBlank()) append("بدون کلاس") else append("کلاس فعلی: ${currentClassName ?: "نامشخص"}")
                        }
                        holder.btnAction.visibility = View.VISIBLE
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
        val currentClassName = classesById[currentClassId]?.className ?: AppDatabase.getClassNameById(currentClassId) ?: "کلاس قبلی"
        MaterialAlertDialogBuilder(this)
            .setTitle("انتقال دانش‌آموز")
            .setMessage("${student.name} اکنون عضو «$currentClassName» است. با ادامه، از کلاس قبلی خارج و به «$className» منتقل می‌شود.")
            .setPositiveButton("انتقال") { _, _ -> updateStudentClass(student, classId, "دانش‌آموز به کلاس جدید منتقل شد") }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun confirmRemoveStudent(student: StudentModel) {
        MaterialAlertDialogBuilder(this)
            .setTitle("حذف از کلاس")
            .setMessage("${student.name} از کلاس «$className» خارج شود؟")
            .setPositiveButton("خارج کردن") { _, _ -> updateStudentClass(student, null, "دانش‌آموز از کلاس خارج شد") }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun updateStudentClass(student: StudentModel, targetClassId: String?, successMessage: String) {
        if (requestInFlight) return
        requestInFlight = true
        renderCurrentList()

        RetrofitClient.instance.assignClass(AssignClassRequest(studentId = student.id, classId = targetClassId))
            .enqueue(object : Callback<ApiResponse> {
                override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                    requestInFlight = false
                    val body = response.body()
                    if (response.isSuccessful && body?.status == "success") {
                        AppToast.makeText(this@ClassDetailsActivity, body.message.ifBlank { successMessage }, Toast.LENGTH_SHORT).show()
                        fetchStudents()
                    } else {
                        renderCurrentList()
                        val action = when {
                            targetClassId == null -> "خارج کردن دانش‌آموز از کلاس"
                            !student.classId.isNullOrBlank() -> "انتقال دانش‌آموز به کلاس جدید"
                            else -> "افزودن دانش‌آموز به کلاس"
                        }
                        AppToast.makeText(
                            this@ClassDetailsActivity,
                            body?.message?.takeIf { it.isNotBlank() }
                                ?: ApiErrorParser.userMessage(response, "$action کامل نشد"),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                    requestInFlight = false
                    renderCurrentList()
                    val action = when {
                        targetClassId == null -> "خارج کردن دانش‌آموز از کلاس"
                        !student.classId.isNullOrBlank() -> "انتقال دانش‌آموز به کلاس جدید"
                        else -> "افزودن دانش‌آموز به کلاس"
                    }
                    AppToast.error(
                        this@ClassDetailsActivity,
                        ApiErrorParser.networkMessage(t, action)
                    )
                }
            })
    }

    private fun setLoading(loading: Boolean) {
        progressLoading.visibility = if (loading) View.VISIBLE else View.GONE
        rvStudents.visibility = if (loading) View.INVISIBLE else View.VISIBLE
    }

    class StudentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivAvatar: ImageView = view.findViewById(R.id.ivStudentAvatar)
        val tvName: TextView = view.findViewById(R.id.tvStudentName)
        val tvDescription: TextView = view.findViewById(R.id.tvStudentPhone)
        val btnAction: MaterialButton = view.findViewById(R.id.btnAction)
    }

    companion object {
        const val EXTRA_CLASS_ID = "CLASS_ID"
        const val EXTRA_CLASS_NAME = "CLASS_NAME"
        const val EXTRA_IS_EDITABLE = "IS_EDITABLE" // 🌟 ثابت جدید برای اینتنت
    }
}
