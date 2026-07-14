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
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton

class StudentManagementActivity : AppCompatActivity() {
    private lateinit var adapter: StudentAdapter
    private lateinit var classFilter: AutoCompleteTextView
    private var search = ""
    private var selectedClassId: String? = null

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

        findViewById<EditText>(R.id.etSearchStudent).addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { search = s.toString().trim(); refresh() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        })
    }

    override fun onResume() {
        super.onResume()
        setupClassFilter()
        refresh()
    }

    private fun setupClassFilter() {
        val activeClasses = AppDatabase.getAllClasses(false)
        val labels = listOf("همه کلاس‌ها", "بدون کلاس") + activeClasses.map { it.className }
        classFilter.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, labels))
        classFilter.setOnItemClickListener { _, _, position, _ ->
            selectedClassId = when (position) {
                0 -> null
                1 -> NO_CLASS
                else -> activeClasses[position - 2].id
            }
            refresh()
        }
    }

    private fun refresh() {
        val filtered = AppDatabase.getAllStudents().filter { student ->
            val matchesSearch = search.isBlank() || student.name.contains(search, true) ||
                student.studentCode.contains(search, true) || student.phone.contains(search)
            val matchesClass = when (selectedClassId) {
                null -> true
                NO_CLASS -> student.classId == null
                else -> student.classId == selectedClassId
            }
            matchesSearch && matchesClass
        }
        adapter.updateList(filtered)
    }

    private fun showDetails(student: StudentModel) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_student_details, null)
        dialog.setContentView(view)
        view.findViewById<TextView>(R.id.dialogName).text = student.name
        view.findViewById<TextView>(R.id.dialogCode).text = "کد دانش‌آموزی: ${student.studentCode}"
        view.findViewById<TextView>(R.id.dialogLevel).text = AppDatabase.getClassNameById(student.classId) ?: "بدون کلاس فعال"
        view.findViewById<TextView>(R.id.dialogPhone).text = "${student.phone} | کد ملی: ${student.nationalId}"
        view.findViewById<TextView>(R.id.dialogRegDate).text = student.registrationDate
        view.findViewById<ImageView>(R.id.dialogAvatar).setImageResource(student.avatarResId)

        val status = view.findViewById<TextView>(R.id.dialogStatus)
        val archive = view.findViewById<MaterialButton>(R.id.btnDialogArchive)
        status.text = if (student.isActive) "فعال" else "بایگانی شده"
        archive.text = if (student.isActive) "بایگانی کردن" else "فعال‌سازی مجدد"
        archive.setOnClickListener {
            if (!AppDatabase.setStudentActive(student.id, !student.isActive)) {
                Toast.makeText(this, "ابتدا دانش‌آموز را از کلاس فعال خارج کنید", Toast.LENGTH_SHORT).show()
            } else {
                dialog.dismiss(); refresh()
            }
        }
        view.findViewById<MaterialButton>(R.id.btnDialogEdit).setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, AddEditStudentActivity::class.java).putExtra("STUDENT_DATA", student))
        }
        dialog.show()
    }

    companion object { private const val NO_CLASS = "__NO_CLASS__" }
}
