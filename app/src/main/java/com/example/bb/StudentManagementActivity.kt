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
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class StudentManagementActivity : AppCompatActivity() {

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
                    Toast.makeText(
                        this@StudentManagementActivity,
                        "لیست کلاس‌ها از اطلاعات محلی نمایش داده شد",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onFailure(call: Call<List<ClassModel>>, t: Throwable) {
                applyClasses(AppDatabase.getAllClasses(false))
                Toast.makeText(
                    this@StudentManagementActivity,
                    "دریافت آنلاین کلاس‌ها ناموفق بود",
                    Toast.LENGTH_SHORT
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
                .sortedBy { it.className.lowercase() }
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
                    Toast.makeText(
                        this@StudentManagementActivity,
                        "سرور لیست دانش‌آموزان را برنگرداند",
                        Toast.LENGTH_SHORT
                    ).show()
                    return
                }

                val allStudents = response.body().orEmpty()
                AppDatabase.replaceStudents(allStudents)

                val filtered = allStudents.filter { student ->
                    val matchesSearch = search.isBlank() ||
                            student.name.contains(search, true) ||
                            student.studentCode.contains(search, true) ||
                            student.phone.contains(search)

                    val matchesClass = when (selectedClassId) {
                        null -> true
                        NO_CLASS -> student.classId.isNullOrBlank()
                        HAS_CLASS -> !student.classId.isNullOrBlank()
                        else -> student.classId == selectedClassId
                    }
                    matchesSearch && matchesClass
                }
                adapter.updateList(filtered)
            }

            override fun onFailure(call: Call<List<StudentModel>>, t: Throwable) {
                Toast.makeText(
                    this@StudentManagementActivity,
                    "خطا در اتصال به سرور",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    private fun showDetails(student: StudentModel) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_student_details, null)
        dialog.setContentView(view)

        view.findViewById<TextView>(R.id.dialogName).text = student.name
        view.findViewById<TextView>(R.id.dialogCode).text =
            "کد دانش‌آموزی: ${student.studentCode.ifBlank { "ندارد" }}"

        val className = activeClasses.find { it.id == student.classId }?.className
            ?: AppDatabase.getClassNameById(student.classId)
            ?: "بدون کلاس فعال"
        view.findViewById<TextView>(R.id.dialogLevel).text = className
        view.findViewById<TextView>(R.id.dialogPhone).text =
            "${student.phone} | کد ملی: ${student.nationalId}"
        view.findViewById<TextView>(R.id.dialogRegDate).text = student.registrationDate

        // 🌟 اختصاص عکس رندوم ثابت برای دیالوگ جزئیات
        val avatarView = view.findViewById<ImageView>(R.id.dialogAvatar)
        val randomNum = (Math.abs(student.id.hashCode()) % 9) + 1
        val fallback = "avatar_student_$randomNum"

        val avatar = student.avatarName?.takeIf { it.isNotBlank() } ?: fallback
        val resId = resources.getIdentifier(avatar, "drawable", packageName)
        if (resId != 0) {
            avatarView.setImageResource(resId)
        } else {
            avatarView.setImageResource(R.drawable.avatar_student_1)
        }

        val status = view.findViewById<TextView>(R.id.dialogStatus)
        val archive = view.findViewById<MaterialButton>(R.id.btnDialogArchive)
        status.text = if (student.isActive) "فعال" else "بایگانی شده"
        archive.text = if (student.isActive) "بایگانی کردن" else "فعال‌سازی مجدد"

        archive.setOnClickListener {
            val nextStatus = !student.isActive
            if (!nextStatus && !student.classId.isNullOrBlank()) {
                Toast.makeText(
                    this,
                    "ابتدا دانش‌آموز را از کلاس فعال خارج کنید",
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
                        Toast.makeText(
                            this@StudentManagementActivity,
                            if (nextStatus) "دانش‌آموز فعال شد" else "دانش‌آموز بایگانی شد",
                            Toast.LENGTH_SHORT
                        ).show()
                        dialog.dismiss()
                        refreshStudents()
                    } else {
                        Toast.makeText(
                            this@StudentManagementActivity,
                            response.body()?.message ?: "تغییر وضعیت در سرور ثبت نشد",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                    archive.isEnabled = true
                    Toast.makeText(
                        this@StudentManagementActivity,
                        "خطا در اتصال به اینترنت",
                        Toast.LENGTH_SHORT
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