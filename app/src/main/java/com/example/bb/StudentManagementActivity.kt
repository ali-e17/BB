package com.example.bb

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton

class StudentManagementActivity : AppCompatActivity() {

    private lateinit var rvStudents: RecyclerView
    private lateinit var studentAdapter: StudentAdapter
    private lateinit var etSearchStudent: EditText
    private lateinit var spinnerClassFilter: AutoCompleteTextView

    private var allStudents = mutableListOf<Student>()
    private var currentSearchQuery = ""
    private var currentSelectedLevel = "همه کلاس‌ها"

    // دریافت خروجی هوشمند از صفحه ثبت نام و ویرایش
    private val formLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            @Suppress("DEPRECATION")
            val student = data?.getSerializableExtra("RESULT_STUDENT") as? Student
            val isEdit = data?.getBooleanExtra("IS_EDIT", false) ?: false

            if (student != null) {
                if (isEdit) {
                    val index = allStudents.indexOfFirst { it.id == student.id }
                    if (index != -1) allStudents[index] = student
                    Toast.makeText(this, "اطلاعات بروزرسانی شد", Toast.LENGTH_SHORT).show()
                } else {
                    allStudents.add(student)
                    Toast.makeText(this, "دانش‌آموز جدید اضافه شد", Toast.LENGTH_SHORT).show()
                }
                applyFilters()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_management)

        rvStudents = findViewById(R.id.rvStudents)
        etSearchStudent = findViewById(R.id.etSearchStudent)
        spinnerClassFilter = findViewById(R.id.spinnerClassFilter)
        val btnBack = findViewById<ImageView>(R.id.btnBack)
        val fabAddStudent = findViewById<FloatingActionButton>(R.id.fabAddStudent)

        btnBack.setOnClickListener { finish() }

        generateMockData()

        // راه‌اندازی فیلتر کلاس‌ها با متد جدید
        val filterOptions = listOf("همه کلاس‌ها", "FF1", "FF2", "FF3", "FF4")
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, filterOptions)
        spinnerClassFilter.setAdapter(spinnerAdapter)

        // تنظیم مدیریت لیست
        rvStudents.layoutManager = LinearLayoutManager(this)
        studentAdapter = StudentAdapter(allStudents) { student ->
            showStudentDetailsBottomSheet(student)
        }
        rvStudents.adapter = studentAdapter

        // لیسنر سرچ زنده
        etSearchStudent.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s.toString()
                applyFilters()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // لیسنر صحیح برای دراپ‌داون در متریال دیزاین
        spinnerClassFilter.setOnItemClickListener { _, _, position, _ ->
            currentSelectedLevel = filterOptions[position]
            applyFilters()
        }

        fabAddStudent.setOnClickListener {
            val intent = Intent(this, AddEditStudentActivity::class.java)
            formLauncher.launch(intent)
        }
    }

    private fun applyFilters() {
        val filteredList = allStudents.filter { student ->
            val matchesSearch = student.fullName.contains(currentSearchQuery, ignoreCase = true) ||
                    student.studentCode.contains(currentSearchQuery, ignoreCase = true)

            val matchesClass = currentSelectedLevel == "همه کلاس‌ها" || student.level == currentSelectedLevel

            matchesSearch && matchesClass
        }
        studentAdapter.updateList(filteredList)
    }

    private fun showStudentDetailsBottomSheet(student: Student) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_student_details, null)
        dialog.setContentView(view)

        view.findViewById<TextView>(R.id.dialogName).text = student.fullName
        view.findViewById<TextView>(R.id.dialogCode).text = "کد دانشجویی: ${student.studentCode}"
        view.findViewById<TextView>(R.id.dialogLevel).text = student.level
        view.findViewById<TextView>(R.id.dialogPhone).text = student.phoneNumber
        view.findViewById<TextView>(R.id.dialogRegDate).text = student.registrationDate
        view.findViewById<ImageView>(R.id.dialogAvatar).setImageResource(student.avatarResId)

        val tvStatus = view.findViewById<TextView>(R.id.dialogStatus)
        val btnArchive = view.findViewById<MaterialButton>(R.id.btnDialogArchive)

        if (student.isActive) {
            tvStatus.text = "فعال"
            tvStatus.setTextColor(android.graphics.Color.parseColor("#10B981"))
            btnArchive.text = "بایگانی کردن"
        } else {
            tvStatus.text = "بایگانی شده"
            val typedValue = android.util.TypedValue()
            theme.resolveAttribute(android.R.attr.textColorSecondary, typedValue, true)
            tvStatus.setTextColor(typedValue.data)
            btnArchive.text = "فعال‌سازی مجدد"
        }

        // لاجیک دکمه بایگانی (Soft Delete)
        btnArchive.setOnClickListener {
            student.isActive = !student.isActive
            val index = allStudents.indexOfFirst { it.id == student.id }
            if (index != -1) allStudents[index] = student
            applyFilters()
            dialog.dismiss()
            Toast.makeText(this, if (student.isActive) "دانش‌آموز فعال شد" else "دانش‌آموز بایگانی شد", Toast.LENGTH_SHORT).show()
        }

        // لاجیک دکمه ویرایش اطلاعات
        view.findViewById<MaterialButton>(R.id.btnDialogEdit).setOnClickListener {
            dialog.dismiss()
            val intent = Intent(this, AddEditStudentActivity::class.java)
            intent.putExtra("STUDENT_DATA", student)
            formLauncher.launch(intent)
        }

        dialog.show()
    }

    private fun generateMockData() {
        if (allStudents.isEmpty()) {
            allStudents.add(Student("1", "کوثر", "شریفی", "2601", "FF1", "09131111111", "1404/01/15", true, R.drawable.avatar_student_1))
            allStudents.add(Student("2", "ریحانه", "محمدی", "2602", "FF1", "09132222222", "1404/02/20", true, R.drawable.avatar_student_2))
            allStudents.add(Student("3", "نازنین", "بابلخانی", "2603", "FF2", "09133333333", "1403/11/01", true, R.drawable.avatar_student_3))
            allStudents.add(Student("4", "علی", "رضایی", "2580", "FF3", "09134444444", "1403/05/12", false, R.drawable.avatar_student_4))
            allStudents.add(Student("5", "محمد", "حسینی", "2599", "FF4", "09135455555", "1404/04/10", true, R.drawable.avatar_student_5))
        }
    }
}