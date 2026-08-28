package com.example.bb

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.Collator
import java.util.Locale

class StudentManagementActivity : BaseActivity() {

    private data class ClassFilterOption(
        val id: String?,
        val title: String,
        val subtitle: String = ""
    )

    private lateinit var adapter: StudentAdapter
    private lateinit var classFilter: MaterialAutoCompleteTextView
    private var search = ""
    private var selectedClassId: String? = null
    private val activeClasses = arrayListOf<ClassModel>()
    private val filterOptions = arrayListOf<ClassFilterOption>()

    private val persianCollator: Collator by lazy {
        Collator.getInstance(Locale("fa", "IR")).apply {
            strength = Collator.PRIMARY
            decomposition = Collator.CANONICAL_DECOMPOSITION
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_management)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<FloatingActionButton>(R.id.fabAddStudent).setOnClickListener {
            startActivity(Intent(this, AddEditStudentActivity::class.java))
        }

        classFilter = findViewById(R.id.spinnerClassFilter)
        val recycler = findViewById<RecyclerView>(R.id.rvStudents)
        recycler.layoutManager = LinearLayoutManager(this)
        adapter = StudentAdapter(emptyList()) { showDetails(it) }
        recycler.adapter = adapter

        classFilter.dropDownWidth = resources.displayMetrics.widthPixels - dpToPx(40)
        classFilter.setOnClickListener { classFilter.showDropDown() }

        findViewById<EditText>(R.id.etSearchStudent)
            .addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    search = s.toString().trim()
                    refreshStudents()
                }

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
            })
    }

    override fun onResume() {
        super.onResume()
        setupClassFilter()
    }

    private fun setupClassFilter() {
        RetrofitClient.instance.getClasses().enqueue(object : Callback<List<ClassModel>> {
            override fun onResponse(
                call: Call<List<ClassModel>>,
                response: Response<List<ClassModel>>
            ) {
                if (response.isSuccessful) {
                    applyClasses(response.body().orEmpty())
                } else {
                    applyClasses(AppDatabase.getAllClasses(false))
                    AppToast.makeText(
                        this@StudentManagementActivity,
                        ApiErrorParser.userMessage(response, "دریافت فهرست کلاس‌ها کامل نشد") +
                                "؛ فهرست ذخیره‌شده دستگاه نمایش داده شد",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            override fun onFailure(call: Call<List<ClassModel>>, t: Throwable) {
                applyClasses(AppDatabase.getAllClasses(false))
                AppToast.makeText(
                    this@StudentManagementActivity,
                    ApiErrorParser.networkMessage(t, "دریافت فهرست کلاس‌ها") +
                            " فهرست ذخیره‌شده دستگاه نمایش داده شد.",
                    Toast.LENGTH_LONG
                ).show()
            }
        })
    }

    private fun applyClasses(serverClasses: List<ClassModel>) {
        AppDatabase.replaceClasses(serverClasses)

        activeClasses.clear()
        activeClasses.addAll(
            serverClasses
                .filter { it.status == ClassStatus.ACTIVE }
                .sortedWith { a, b ->
                    persianCollator.compare(
                        normalizePersian(a.className),
                        normalizePersian(b.className)
                    )
                }
        )

        filterOptions.clear()
        filterOptions += ClassFilterOption(null, "همه دانش‌آموزان", "بدون محدودیت کلاس")
        filterOptions += ClassFilterOption(NO_CLASS, "دانش‌آموزان بدون کلاس", "فقط افراد تخصیص‌داده‌نشده")
        filterOptions += ClassFilterOption(HAS_CLASS, "دانش‌آموزان کلاس‌دار", "همه افراد عضو کلاس فعال")
        filterOptions += activeClasses.map { model ->
            ClassFilterOption(
                id = model.id,
                title = model.className,
                subtitle = "${model.daysOfWeek} | ${model.startTime} تا ${model.endTime}"
            )
        }

        val filterAdapter = ClassFilterAdapter(this@StudentManagementActivity, filterOptions)
        classFilter.setAdapter(filterAdapter)
        classFilter.setOnItemClickListener { _, _, position, _ ->
            selectedClassId = filterOptions[position].id
            classFilter.setText(filterOptions[position].title, false)
            refreshStudents()
        }

        val selectedOption = filterOptions.firstOrNull { it.id == selectedClassId }
            ?: filterOptions.first()
        selectedClassId = selectedOption.id
        classFilter.setText(selectedOption.title, false)
        refreshStudents()
    }

    private fun refreshStudents() {
        RetrofitClient.instance.getStudents().enqueue(object : Callback<List<StudentModel>> {
            override fun onResponse(
                call: Call<List<StudentModel>>,
                response: Response<List<StudentModel>>
            ) {
                if (!response.isSuccessful) {
                    AppToast.makeText(
                        this@StudentManagementActivity,
                        ApiErrorParser.userMessage(response, "دریافت فهرست دانش‌آموزان کامل نشد"),
                        Toast.LENGTH_LONG
                    ).show()
                    return
                }

                val allStudents = response.body().orEmpty()
                AppDatabase.replaceStudents(allStudents)

                // فقط شناسه کلاس‌های فعال را مبنای فیلتر قرار می‌دهیم.
                // ممکن است classId یک دانش‌آموز هنوز به یک کلاس قدیمی/تکمیل‌شده اشاره کند؛
                // چنین دانش‌آموزی نباید در «دانش‌آموزان کلاس‌دار» دیده شود.
                val activeClassIds = activeClasses
                    .asSequence()
                    .map { it.id.trim() }
                    .filter { it.isNotEmpty() }
                    .toHashSet()

                val filtered = allStudents.filter { student ->
                    val normalizedSearch = normalizePersian(search)
                    val normalizedStudent = normalizePersian(
                        "${student.firstName} ${student.lastName} ${student.name} " +
                                "${student.studentCode} ${student.phone}"
                    )

                    val matchesSearch =
                        normalizedSearch.isBlank() || normalizedStudent.contains(normalizedSearch)

                    val studentClassId = student.classId?.trim().orEmpty()
                    val hasActiveClass =
                        studentClassId.isNotEmpty() && studentClassId in activeClassIds

                    val matchesClass = when (selectedClassId) {
                        null -> true
                        NO_CLASS -> !hasActiveClass
                        HAS_CLASS -> hasActiveClass
                        else -> studentClassId == selectedClassId
                    }

                    matchesSearch && matchesClass
                }.sortedWith(Comparator { first, second ->
                    compareStudents(first, second)
                })

                adapter.updateList(filtered)
            }

            override fun onFailure(call: Call<List<StudentModel>>, t: Throwable) {
                AppToast.makeText(
                    this@StudentManagementActivity,
                    ApiErrorParser.networkMessage(t, "دریافت فهرست دانش‌آموزان"),
                    Toast.LENGTH_LONG
                ).show()
            }
        })
    }

    private fun compareStudents(first: StudentModel, second: StudentModel): Int {
        val familyCompare = persianCollator.compare(
            sortableLastName(first),
            sortableLastName(second)
        )
        if (familyCompare != 0) return familyCompare

        val firstNameCompare = persianCollator.compare(
            normalizePersian(first.firstName),
            normalizePersian(second.firstName)
        )
        if (firstNameCompare != 0) return firstNameCompare

        return persianCollator.compare(
            normalizePersian(first.studentCode),
            normalizePersian(second.studentCode)
        )
    }

    private fun sortableLastName(student: StudentModel): String {
        val directLastName = normalizePersian(student.lastName)
        if (directLastName.isNotBlank()) return directLastName

        val normalizedFullName = normalizePersian(student.name)
        val parts = normalizedFullName.split(" ").filter { it.isNotBlank() }
        return when {
            parts.size > 1 -> parts.drop(1).joinToString(" ")
            else -> normalizedFullName
        }
    }

    private fun normalizePersian(value: String): String = value
        .lowercase(Locale("fa", "IR"))
        .replace('ي', 'ی')
        .replace('ى', 'ی')
        .replace('ك', 'ک')
        .replace('ة', 'ه')
        .replace('\u200C', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun showDetails(student: StudentModel) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_student_details, null)
        dialog.setContentView(view)

        view.findViewById<TextView>(R.id.dialogName).text = student.name
        view.findViewById<TextView>(R.id.dialogCode).text =
            UiTextFormatter.smartDigits(
                "کد دانش‌آموزی: ${student.studentCode.ifBlank { "ندارد" }}"
            )

        val className = activeClasses.find { it.id == student.classId }?.className
            ?: AppDatabase.getClassNameById(student.classId)
            ?: "بدون کلاس فعال"

        view.findViewById<TextView>(R.id.dialogLevel).text = className
        view.findViewById<TextView>(R.id.dialogPhone).text =
            UiTextFormatter.smartDigits(
                student.phone.ifBlank { "ثبت نشده" }
            )

        view.findViewById<TextView>(R.id.dialogIdentityLabel).text =
            if (student.isForeign) "کد اتباع" else "کد ملی"

        view.findViewById<TextView>(R.id.dialogNationalId).text =
            UiTextFormatter.smartDigits(
                student.displayIdentityCode.ifBlank { "ثبت نشده" }
            )

        view.findViewById<TextView>(R.id.dialogRegDate).text =
            UiTextFormatter.smartDigits(
                if (student.registrationDate.isBlank()) {
                    "ثبت نشده"
                } else {
                    PersianDateUtils.formatDate(student.registrationDate)
                }
            )

        val avatarView = view.findViewById<ImageView>(R.id.dialogAvatar)
        val fallback = "avatar_no_profile"
        val avatar = student.avatarName?.takeIf { it.isNotBlank() } ?: fallback
        val resId = resources.getIdentifier(avatar, "drawable", packageName)
        avatarView.setImageResource(
            if (resId != 0) resId else resources.getIdentifier(fallback, "drawable", packageName)
        )
        val status = view.findViewById<TextView>(R.id.dialogStatus)
        val archive = view.findViewById<MaterialButton>(R.id.btnDialogArchive)
        status.text = when (student.accountStatus.uppercase(Locale.ROOT)) {
            "ARCHIVED" -> "بایگانی‌شده"
            "PAYMENT_REQUIRED" -> "نیازمند پرداخت"
            "RENEWAL_REQUIRED" -> "نیازمند تمدید"
            else -> "فعال"
        }
        archive.text = if (student.isActive) "بایگانی کردن" else "فعال‌سازی مجدد"

        archive.setOnClickListener {
            val nextStatus = !student.isActive
            if (!nextStatus && !student.classId.isNullOrBlank()) {
                AppToast.makeText(
                    this,
                    "امکان بایگانی دانش‌آموز وجود ندارد؛ ابتدا عضویت او را در کلاس فعال پایان دهید",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            archive.isEnabled = false
            RetrofitClient.instance.toggleStudentActive(
                ToggleActiveRequest(student.id, nextStatus)
            ).enqueue(object : Callback<ApiResponse> {
                override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                    archive.isEnabled = true
                    if (response.isSuccessful && response.body()?.status == "success") {
                        AppToast.makeText(
                            this@StudentManagementActivity,
                            if (nextStatus) "دانش‌آموز فعال شد" else "دانش‌آموز بایگانی شد",
                            Toast.LENGTH_SHORT
                        ).show()
                        dialog.dismiss()
                        refreshStudents()
                    } else {
                        AppToast.makeText(
                            this@StudentManagementActivity,
                            response.body()?.message?.takeIf { it.isNotBlank() } ?: ApiErrorParser.userMessage(response, "تغییر وضعیت دانش‌آموز کامل نشد"),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                    archive.isEnabled = true
                    AppToast.makeText(
                        this@StudentManagementActivity,
                        ApiErrorParser.networkMessage(t, "تغییر وضعیت دانش‌آموز"),
                        Toast.LENGTH_LONG
                    ).show()
                }
            })
        }

        view.findViewById<MaterialButton>(R.id.btnDialogEdit).setOnClickListener {
            dialog.dismiss()
            startActivity(
                Intent(this, AddEditStudentActivity::class.java)
                    .putExtra("STUDENT_DATA", student)
            )
        }

        view.findViewById<MaterialButton>(R.id.btnDialogTrashStudent).setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("حذف قطعی دانش‌آموز")
                .setMessage("آیا از حذف قطعی این دانش‌آموز مطمئن هستید؟ تمام سوابق، حضور و غیاب و کارنامه‌های او برای همیشه پاک خواهند شد.")
                .setNegativeButton("انصراف", null)
                .setPositiveButton("حذف قطعی") { _, _ ->
                    RetrofitClient.instance.hardDeleteStudent(HardDeleteStudentRequest(student.id))
                        .enqueue(object : Callback<ApiResponse> {
                            override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                                val body = response.body()
                                val message = if (response.isSuccessful && body?.status == "success") {
                                    body.message.ifBlank { "دانش‌آموز با موفقیت حذف شد" }
                                } else {
                                    body?.message?.takeIf { it.isNotBlank() }
                                        ?: ApiErrorParser.userMessage(response, "حذف دانش‌آموز کامل نشد")
                                }
                                AppToast.makeText(this@StudentManagementActivity, message, Toast.LENGTH_LONG).show()
                                if (response.isSuccessful && body?.status == "success") { dialog.dismiss(); refreshStudents() }
                            }
                            override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                                AppToast.makeText(this@StudentManagementActivity, ApiErrorParser.networkMessage(t, "حذف دانش‌آموز"), Toast.LENGTH_LONG).show()
                            }
                        })
                }.show()
        }

        val resetPassword = view.findViewById<MaterialButton>(R.id.btnDialogResetPassword)
        resetPassword.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("ساخت رمز موقت")
                .setMessage("رمز فعلی قابل مشاهده نیست. یک رمز موقت جدید ساخته شود؟")
                .setNegativeButton("انصراف", null)
                .setPositiveButton("ساخت رمز") { _, _ ->
                    resetPassword.isEnabled = false
                    RetrofitClient.instance.adminResetPassword(
                        AdminResetPasswordRequest("STUDENT", student.id)
                    ).enqueue(object : Callback<AdminResetPasswordResponse> {
                        override fun onResponse(call: Call<AdminResetPasswordResponse>, response: Response<AdminResetPasswordResponse>) {
                            resetPassword.isEnabled = true
                            val body = response.body()
                            if (response.isSuccessful && body?.status == "success" && !body.temporaryPassword.isNullOrBlank()) {
                                MaterialAlertDialogBuilder(this@StudentManagementActivity)
                                    .setTitle("رمز موقت ${student.name}")
                                    .setMessage("${body.temporaryPassword}\n\nاین رمز فقط همین یک‌بار نمایش داده می‌شود. کاربر بعد از ورود مجبور به تغییر آن است.")
                                    .setPositiveButton("متوجه شدم", null)
                                    .show()
                            } else {
                                AppToast.makeText(this@StudentManagementActivity, body?.message?.takeIf { it.isNotBlank() } ?: ApiErrorParser.userMessage(response, "ساخت رمز موقت کامل نشد"), Toast.LENGTH_LONG).show()
                            }
                        }
                        override fun onFailure(call: Call<AdminResetPasswordResponse>, t: Throwable) {
                            resetPassword.isEnabled = true
                            AppToast.makeText(this@StudentManagementActivity, ApiErrorParser.networkMessage(t, "ساخت رمز موقت"), Toast.LENGTH_LONG).show()
                        }
                    })
                }
                .show()
        }

        dialog.show()
    }

    private fun dpToPx(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private class ClassFilterAdapter(
        context: Context,
        private val options: List<ClassFilterOption>
    ) : ArrayAdapter<ClassFilterOption>(context, R.layout.item_class_filter_option, options) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
            bindView(position, convertView, parent)

        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
            bindView(position, convertView, parent)

        private fun bindView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(context)
                .inflate(R.layout.item_class_filter_option, parent, false)

            val item = options[position]
            view.findViewById<TextView>(R.id.tvFilterClassName).text = item.title
            view.findViewById<TextView>(R.id.tvFilterClassSchedule).apply {
                text = item.subtitle
                visibility = if (item.subtitle.isBlank()) View.GONE else View.VISIBLE
            }
            return view
        }
    }

    companion object {
        private const val NO_CLASS = "__NO_CLASS__"
        private const val HAS_CLASS = "__HAS_CLASS__"
    }
}
