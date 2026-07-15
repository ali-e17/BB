package com.example.bb

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
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
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class StudentManagementActivity : AppCompatActivity() {
    private lateinit var adapter: StudentAdapter
    private lateinit var classFilter: AutoCompleteTextView
    private var search = ""
    private var selectedClassId: String? = null
    private val activeClasses = ArrayList<ClassModel>()

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

        // آداپتر با لیست خالی ساخته میشه تا بعداً از سرور پر بشه
        adapter = StudentAdapter(emptyList()) { showDetails(it) }
        recycler.adapter = adapter

        findViewById<EditText>(R.id.etSearchStudent).addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { search = s.toString().trim(); refresh() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        })
    }

    override fun onResume() {
        super.onResume()
        setupClassFilter() // دریافت کلاس‌ها و تنظیم فیلتر
    }

    // 🌐 ۱. تنظیم فیلتر بالای صفحه (همه، بدون کلاس، کلاس‌دارها، و لیست کلاس‌ها)
    private fun setupClassFilter() {
        RetrofitClient.instance.getClasses().enqueue(object : Callback<List<ClassModel>> {
            override fun onResponse(call: Call<List<ClassModel>>, response: Response<List<ClassModel>>) {
                if (response.isSuccessful) {
                    activeClasses.clear()
                    val classes = response.body()?.filter { it.status == ClassStatus.ACTIVE } ?: emptyList()
                    activeClasses.addAll(classes)

                    val labels = listOf("همه", "بدون کلاس", "کلاس‌دارها") + activeClasses.map { it.className }

                    val spinnerAdapter = ArrayAdapter(this@StudentManagementActivity, android.R.layout.simple_dropdown_item_1line, labels)
                    classFilter.setAdapter(spinnerAdapter)

                    classFilter.setOnItemClickListener { _, _, position, _ ->
                        selectedClassId = when (position) {
                            0 -> null             // همه
                            1 -> NO_CLASS         // بدون کلاس
                            2 -> HAS_CLASS        // کلاس‌دارها
                            else -> activeClasses[position - 3].id // کلاس خاص
                        }
                        refresh()
                    }

                    refresh() // بعد از تنظیم فیلتر، لیست دانش‌آموزان رو دریافت می‌کنیم
                }
            }

            override fun onFailure(call: Call<List<ClassModel>>, t: Throwable) {
                Toast.makeText(this@StudentManagementActivity, "خطا در دریافت لیست کلاس‌ها", Toast.LENGTH_SHORT).show()
                refresh()
            }
        })
    }

    // 🌐 ۲. دریافت دانش‌آموزان و اعمال فیلتر سرچ و کلاس
    private fun refresh() {
        RetrofitClient.instance.getStudents().enqueue(object : Callback<List<StudentModel>> {
            override fun onResponse(call: Call<List<StudentModel>>, response: Response<List<StudentModel>>) {
                if (response.isSuccessful) {
                    val allStudents = response.body() ?: emptyList<StudentModel>()

                    val filtered = allStudents.filter { student ->
                        val matchesSearch = search.isBlank() || student.name.contains(search, true) ||
                                student.studentCode.contains(search, true) || student.phone.contains(search)

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
            }

            override fun onFailure(call: Call<List<StudentModel>>, t: Throwable) {
                Toast.makeText(this@StudentManagementActivity, "خطا در اتصال به سرور", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showDetails(student: StudentModel) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_student_details, null)
        dialog.setContentView(view)

        view.findViewById<TextView>(R.id.dialogName).text = student.name
        view.findViewById<TextView>(R.id.dialogCode).text = "کد دانش‌آموزی: ${student.studentCode}"

        // پیدا کردن نام کلاس از روی لیست activeClasses
        val className = activeClasses.find { it.id == student.classId }?.className ?: "بدون کلاس فعال"
        view.findViewById<TextView>(R.id.dialogLevel).text = className

        view.findViewById<TextView>(R.id.dialogPhone).text = "${student.phone} | کد ملی: ${student.nationalId}"
        view.findViewById<TextView>(R.id.dialogRegDate).text = student.registrationDate
        view.findViewById<ImageView>(R.id.dialogAvatar).setImageResource(student.avatarResId)

        val status = view.findViewById<TextView>(R.id.dialogStatus)
        val archive = view.findViewById<MaterialButton>(R.id.btnDialogArchive)

        status.text = if (student.isActive) "فعال" else "بایگانی شده"
        archive.text = if (student.isActive) "بایگانی کردن" else "فعال‌سازی مجدد"

        archive.setOnClickListener {
            val nextStatus = !student.isActive

            // 🌟 شرط مهم: اگر می‌خواهیم بایگانی کنیم (nextStatus = false) و طرف کلاس دارد، اجازه نده!
            if (!nextStatus && !student.classId.isNullOrBlank()) {
                Toast.makeText(this, "ابتدا دانش‌آموز را از کلاس فعال خارج کنید", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            archive.isEnabled = false
            val request = ToggleActiveRequest(student.id, nextStatus)

            // 🌐 ارسال وضعیت جدید به سرور
            RetrofitClient.instance.toggleStudentActive(request).enqueue(object : Callback<ApiResponse> {
                override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                    archive.isEnabled = true
                    if (response.isSuccessful && response.body()?.status == "success") {
                        val msg = if (nextStatus) "دانش‌آموز فعال شد" else "دانش‌آموز بایگانی شد"
                        Toast.makeText(this@StudentManagementActivity, msg, Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                        refresh()
                    } else {
                        Toast.makeText(this@StudentManagementActivity, "خطا در تغییر وضعیت در سرور", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                    archive.isEnabled = true
                    Toast.makeText(this@StudentManagementActivity, "خطا در اتصال به اینترنت", Toast.LENGTH_SHORT).show()
                }
            })
        }

        view.findViewById<MaterialButton>(R.id.btnDialogEdit).setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, AddEditStudentActivity::class.java).putExtra("STUDENT_DATA", student))
        }
        dialog.show()
    }

    companion object {
        private const val NO_CLASS = "__NO_CLASS__"
        private const val HAS_CLASS = "__HAS_CLASS__"
    }
}