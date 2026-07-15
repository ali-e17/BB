package com.example.bb

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ClassDetailsActivity : AppCompatActivity() {

    private lateinit var classId: String
    private lateinit var rvSearchResults: RecyclerView
    private lateinit var rvClassMembers: RecyclerView

    private val searchResultsList = ArrayList<StudentModel>()
    private val classMembersList = ArrayList<StudentModel>()
    private var isEditable = true

    override fun onCreate(savedInstanceState: Bundle?) {
        findViewById<ImageView>(R.id.btnDetailsBack).setOnClickListener { finish() }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_class_details)

        classId = intent.getStringExtra("CLASS_ID") ?: ""
        isEditable = AppDatabase.getClassById(classId)?.status == ClassStatus.ACTIVE
        findViewById<TextView>(R.id.txtClassName).text = intent.getStringExtra("CLASS_NAME")

        rvSearchResults = findViewById(R.id.rvSearchResults)
        rvClassMembers = findViewById(R.id.rvClassMembers)
        val etSearch = findViewById<EditText>(R.id.etSearchStudent)

        rvSearchResults.layoutManager = LinearLayoutManager(this)
        rvClassMembers.layoutManager = LinearLayoutManager(this)

        updateClassMembers()

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()
                if (query.isNotEmpty() && isEditable) {
                    searchResultsList.clear()
                    // جستجوی دانش‌آموزانی که در این کلاس نیستند
                    searchResultsList.addAll(AppDatabase.searchStudents(query).filter { it.classId != classId })
                    if (searchResultsList.isNotEmpty()) {
                        rvSearchResults.visibility = View.VISIBLE
                        setupSearchAdapter()
                    } else {
                        rvSearchResults.visibility = View.GONE
                    }
                } else {
                    rvSearchResults.visibility = View.GONE
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun updateClassMembers() {
        classMembersList.clear()
        classMembersList.addAll(if (isEditable) AppDatabase.getStudentsInClass(classId) else AppDatabase.getStudentsEverInClass(classId))

        rvClassMembers.adapter = object : RecyclerView.Adapter<StudentViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
                StudentViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_student_manage, parent, false))

            override fun onBindViewHolder(holder: StudentViewHolder, position: Int) {
                val student = classMembersList[position]
                holder.tvName.text = student.name
                holder.tvPhone.text = student.phone
                holder.btnAction.text = "✖"
                holder.btnAction.isEnabled = isEditable

                holder.btnAction.setOnClickListener {
                    AppDatabase.assignClassToStudent(student.phone, null, this@ClassDetailsActivity)
                    updateClassMembers()
                }
            }
            override fun getItemCount() = classMembersList.size
        }
    }

    private fun setupSearchAdapter() {
        rvSearchResults.adapter = object : RecyclerView.Adapter<StudentViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
                StudentViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_student_manage, parent, false))

            override fun onBindViewHolder(holder: StudentViewHolder, position: Int) {
                val student = searchResultsList[position]
                holder.tvName.text = student.name
                holder.tvPhone.text = student.phone
                holder.btnAction.text = "+"
                holder.btnAction.isEnabled = isEditable

                holder.btnAction.setOnClickListener {
                    AppDatabase.assignClassToStudent(student.phone, classId, this@ClassDetailsActivity)
                    findViewById<EditText>(R.id.etSearchStudent).text.clear()
                    rvSearchResults.visibility = View.GONE
                    updateClassMembers()
                }
            }
            override fun getItemCount() = searchResultsList.size
        }
    }

    class StudentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvStudentName)
        val tvPhone: TextView = view.findViewById(R.id.tvStudentPhone)
        val btnAction: Button = view.findViewById(R.id.btnAction)
    }
}
