package com.example.bb

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class AssignClassActivity : AppCompatActivity() {

    private lateinit var rvSearchResults: RecyclerView
    private lateinit var rvAssignedClasses: RecyclerView
    private lateinit var etSearchClass: TextInputEditText
    private var teacherUsername: String = ""

    private val searchResultsList = ArrayList<ClassModel>()
    private val assignedClassesList = ArrayList<ClassModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_assign_class)

        findViewById<ImageView>(R.id.btnAssignBack).setOnClickListener { finish() }

        teacherUsername = intent.getStringExtra("TEACHER_USERNAME") ?: ""

        rvSearchResults = findViewById(R.id.rvSearchResults)
        rvAssignedClasses = findViewById(R.id.rvAssignedClasses)
        etSearchClass = findViewById(R.id.etSearchClass)

        rvSearchResults.layoutManager = LinearLayoutManager(this)
        rvAssignedClasses.layoutManager = LinearLayoutManager(this)

        updateAssignedClasses()

        etSearchClass.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()
                if (query.isNotEmpty()) {
                    searchResultsList.clear()
                    // جستجو فقط در کلاس‌هایی که به هیچ استادی داده نشدن
                    searchResultsList.addAll(AppDatabase.getAvailableClasses().filter { it.className.contains(query, ignoreCase = true) })

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

    private fun updateAssignedClasses() {
        assignedClassesList.clear()
        assignedClassesList.addAll(AppDatabase.getTeacherClasses(teacherUsername))

        rvAssignedClasses.adapter = object : RecyclerView.Adapter<ClassActionViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
                ClassActionViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_class_action, parent, false))

            override fun onBindViewHolder(holder: ClassActionViewHolder, position: Int) {
                val classModel = assignedClassesList[position]
                holder.tvClassName.text = classModel.className
                holder.tvClassTime.text = classModel.classTime

                // دکمه قرمز برای حذف تخصیص
                holder.btnAction.text = "✖"
                holder.btnAction.setTextColor(android.graphics.Color.RED)
                holder.btnAction.setStrokeColorResource(android.R.color.holo_red_dark)

                holder.btnAction.setOnClickListener {
                    AppDatabase.removeClassFromTeacher(teacherUsername, classModel.id, this@AssignClassActivity)
                    updateAssignedClasses()
                    Toast.makeText(this@AssignClassActivity, "کلاس از استاد گرفته شد", Toast.LENGTH_SHORT).show()
                }
            }
            override fun getItemCount() = assignedClassesList.size
        }
    }

    private fun setupSearchAdapter() {
        rvSearchResults.adapter = object : RecyclerView.Adapter<ClassActionViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
                ClassActionViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_class_action, parent, false))

            override fun onBindViewHolder(holder: ClassActionViewHolder, position: Int) {
                val classModel = searchResultsList[position]
                holder.tvClassName.text = classModel.className
                holder.tvClassTime.text = classModel.classTime

                // دکمه سبز/آبی برای افزودن
                holder.btnAction.text = "+"
                holder.btnAction.setTextColor(android.graphics.Color.parseColor("#FF6B00"))

                holder.btnAction.setOnClickListener {
                    AppDatabase.assignClassToTeacher(teacherUsername, classModel.id, this@AssignClassActivity)
                    etSearchClass.text?.clear()
                    rvSearchResults.visibility = View.GONE
                    updateAssignedClasses()
                }
            }
            override fun getItemCount() = searchResultsList.size
        }
    }

    class ClassActionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvClassName: TextView = view.findViewById(R.id.tvClassName)
        val tvClassTime: TextView = view.findViewById(R.id.tvClassTime)
        val btnAction: MaterialButton = view.findViewById(R.id.btnAction)
    }
}