package com.example.bb

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ClassDetailsActivity : AppCompatActivity() {

    private lateinit var classId: String
    private lateinit var rvSearchResults: RecyclerView
    private lateinit var rvClassMembers: RecyclerView

    private var allStudentsList = ArrayList<StudentModel>() // کل دانش‌آموزان دانلود شده از سرور
    private val searchResultsList = ArrayList<StudentModel>()
    private val classMembersList = ArrayList<StudentModel>()
    private var isEditable = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_class_details)

        classId = intent.getStringExtra("CLASS_ID") ?: ""

        // اگر وضعیت کلاس از دیتابیس محلی در دسترس نبود، پیش‌فرض کلاس رو فعال (قابل ویرایش) در نظر می‌گیریم
        isEditable = AppDatabase.getClassById(classId)?.status ?: ClassStatus.ACTIVE == ClassStatus.ACTIVE

        findViewById<TextView>(R.id.txtClassName).text = intent.getStringExtra("CLASS_NAME")

        rvSearchResults = findViewById(R.id.rvSearchResults)
        rvClassMembers = findViewById(R.id.rvClassMembers)
        val etSearch = findViewById<EditText>(R.id.etSearchStudent)

        rvSearchResults.layoutManager = LinearLayoutManager(this)
        rvClassMembers.layoutManager = LinearLayoutManager(this)

        fetchStudentsFromServer() // 🌐 دریافت اطلاعات آنلاین

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                performSearch(s.toString().trim())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    // 🌐 دریافت کل دانش‌آموزان از سرور
    private fun fetchStudentsFromServer() {
        RetrofitClient.instance.getStudents().enqueue(object : Callback<List<StudentModel>> {
            override fun onResponse(call: Call<List<StudentModel>>, response: Response<List<StudentModel>>) {
                if (response.isSuccessful) {
                    allStudentsList.clear()
                    allStudentsList.addAll(response.body() ?: emptyList())

                    updateClassMembers()

                    // اگر کلمه‌ای در باکس سرچ نوشته شده بود، لیست سرچ هم آپدیت شود
                    val currentQuery = findViewById<EditText>(R.id.etSearchStudent).text.toString().trim()
                    performSearch(currentQuery)
                }
            }

            override fun onFailure(call: Call<List<StudentModel>>, t: Throwable) {
                Toast.makeText(this@ClassDetailsActivity, "خطا در دریافت اطلاعات", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun performSearch(query: String) {
        if (query.isNotEmpty() && isEditable) {
            searchResultsList.clear()
            // جستجو در نام، فامیل، کد دانشجویی یا تلفن
            searchResultsList.addAll(allStudentsList.filter {
                it.classId != classId && (it.name.contains(query, true) || it.studentCode.contains(query, true) || it.phone.contains(query))
            })
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

    private fun updateClassMembers() {
        classMembersList.clear()
        classMembersList.addAll(allStudentsList.filter { it.classId == classId })

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
                    holder.btnAction.isEnabled = false // غیرفعال کردن دکمه تا زمان پاسخ سرور
                    assignStudentOnline(student.id, null) // ارسال نال برای خروج از کلاس
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
                    holder.btnAction.isEnabled = false // جلوگیری از کلیک اسپم
                    assignStudentOnline(student.id, classId) // ارسال آی‌دی کلاس برای عضویت
                }
            }
            override fun getItemCount() = searchResultsList.size
        }
    }

    // 🌐 متد شلیک تغییرات به سمت سرور
    private fun assignStudentOnline(studentId: String, newClassId: String?) {
        val request = AssignClassRequest(studentId, newClassId)
        RetrofitClient.instance.assignClass(request).enqueue(object : Callback<ApiResponse> {
            override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                if (response.isSuccessful && response.body()?.status == "success") {
                    findViewById<EditText>(R.id.etSearchStudent).text.clear() // پاک کردن باکس سرچ
                    fetchStudentsFromServer() // دریافت مجدد و بروزرسانی رابط کاربری
                } else {
                    Toast.makeText(this@ClassDetailsActivity, "خطا در ثبت تغییرات", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                Toast.makeText(this@ClassDetailsActivity, "خطا در اتصال به اینترنت", Toast.LENGTH_SHORT).show()
            }
        })
    }

    class StudentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvStudentName)
        val tvPhone: TextView = view.findViewById(R.id.tvStudentPhone)
        val btnAction: Button = view.findViewById(R.id.btnAction)
    }
}