package com.example.bb

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.UUID

class AddEditClassActivity : AppCompatActivity() {

    private var isEditMode = false
    private var classId = ""

    private val enrolledStudents = ArrayList<StudentModel>()
    private val searchResultsList = ArrayList<StudentModel>()

    private lateinit var rvEnrolledStudents: RecyclerView
    private lateinit var rvStudentSearchResults: RecyclerView
    private lateinit var etSearchNewStudent: TextInputEditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_edit_class)

        findViewById<ImageView>(R.id.btnClassEditBack).setOnClickListener { finish() }

        val tvTitle = findViewById<TextView>(R.id.tvClassEditTitle)
        val spinnerClassName = findViewById<MaterialAutoCompleteTextView>(R.id.spinnerClassName)
        val spinnerTeacherName = findViewById<MaterialAutoCompleteTextView>(R.id.spinnerTeacherName)
        val etStartTime = findViewById<TextInputEditText>(R.id.etStartTime)
        val etEndTime = findViewById<TextInputEditText>(R.id.etEndTime)
        val etSessionCount = findViewById<TextInputEditText>(R.id.etSessionCount)
        val chipGroupDays = findViewById<ChipGroup>(R.id.chipGroupDays)
        val layoutStudentManagement = findViewById<LinearLayout>(R.id.layoutStudentManagement)

        val predefinedClasses = listOf(
            "Kids: Pockets 1A", "Kids: Pockets 1B", "Kids: Pockets 1C", "Kids: Pockets 2A", "Kids: Pockets 2B", "Kids: Pockets 2C", "Kids: Pockets 3A", "Kids: Pockets 3B", "Kids: Pockets 3C",
            "Elementary: FF1", "Elementary: FF2", "Elementary: FF3", "Elementary: F&F 1A", "Elementary: F&F 1B", "Elementary: F&F 1C", "Elementary: F&F 2A", "Elementary: F&F 2B", "Elementary: F&F 2C", "Elementary: F&F 3A", "Elementary: F&F 3B", "Elementary: F&F 3C",
            "Intermediate: F&F 4A", "Intermediate: F&F 4B", "Intermediate: F&F 4C", "Intermediate: Got it 1A", "Intermediate: Got it 1B", "Intermediate: Got it 1C", "Intermediate: Got it 2A", "Intermediate: Got it 2B", "Intermediate: Got it 2C", "Intermediate: Got it 3A", "Intermediate: Got it 3B", "Intermediate: Got it 3C",
            "Advanced: AEF 1A", "Advanced: AEF 1B", "Advanced: AEF 1C", "Advanced: AEF 2A", "Advanced: AEF 2B", "Advanced: AEF 2C", "Advanced: AEF 3A", "Advanced: AEF 3B", "Advanced: AEF 3C", "Advanced: AEF 4A", "Advanced: AEF 4B", "Advanced: AEF 4C", "Advanced: AEF 5A", "Advanced: AEF 5B", "Advanced: AEF 5C"
        )
        spinnerClassName.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, predefinedClasses))

        // اضافه شدن هوشمندانه گزینه "بدون استاد" به اول لیست
        val activeTeachers = AppDatabase.getAllTeachers().filter { it.isActive }
        val teacherNames = mutableListOf("بدون استاد (تخصیص بعداً)")
        teacherNames.addAll(activeTeachers.map { "${it.name} (${it.username})" })
        spinnerTeacherName.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, teacherNames))

        classId = intent.getStringExtra("CLASS_ID") ?: ""
        isEditMode = classId.isNotEmpty()

        if (isEditMode) {
            tvTitle.text = "ویرایش مشخصات کلاس"
            layoutStudentManagement.visibility = View.VISIBLE
            val existingClass = AppDatabase.getClassById(classId)

            existingClass?.let {
                spinnerClassName.setText(it.className, false)

                val savedDays = it.daysOfWeek.split("،").map { d -> d.trim() }
                for (i in 0 until chipGroupDays.childCount) {
                    val chip = chipGroupDays.getChildAt(i) as Chip
                    if (savedDays.contains(chip.text.toString())) {
                        chip.isChecked = true
                    }
                }

                etStartTime.setText(it.startTime)
                etEndTime.setText(it.endTime)
                etSessionCount.setText(it.sessionCount.toString())

                // بررسی استاد قبلی
                if (it.teacherPhone != null) {
                    val teacher = activeTeachers.find { t -> t.username == it.teacherPhone }
                    if (teacher != null) {
                        spinnerTeacherName.setText("${teacher.name} (${teacher.username})", false)
                    } else {
                        spinnerTeacherName.setText("بدون استاد (تخصیص بعداً)", false)
                    }
                } else {
                    spinnerTeacherName.setText("بدون استاد (تخصیص بعداً)", false)
                }

                setupStudentManagement()
            }
        } else {
            // در حالت کلاس جدید، به صورت پیش‌فرض روی بدون استاد تنظیم شود
            spinnerTeacherName.setText("بدون استاد (تخصیص بعداً)", false)
        }

        findViewById<Button>(R.id.btnSaveClass).setOnClickListener {
            val name = spinnerClassName.text.toString().trim()
            val teacherSelection = spinnerTeacherName.text.toString().trim()
            val start = etStartTime.text.toString().trim()
            val end = etEndTime.text.toString().trim()
            val sessionsStr = etSessionCount.text.toString().trim()

            val selectedDays = mutableListOf<String>()
            for (i in 0 until chipGroupDays.childCount) {
                val chip = chipGroupDays.getChildAt(i) as Chip
                if (chip.isChecked) selectedDays.add(chip.text.toString())
            }

            // فیلد استاد دیگر در این بررسی اجباری نیست
            if (name.isEmpty() || start.isEmpty() || end.isEmpty() || sessionsStr.isEmpty() || selectedDays.isEmpty()) {
                Toast.makeText(this, "لطفاً تمام فیلدهای اصلی و روزهای برگزاری را مشخص کنید", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val timeRegex = Regex("^([01]?[0-9]|2[0-3]):[0-5][0-9]$")
            if (!timeRegex.matches(start) || !timeRegex.matches(end)) {
                Toast.makeText(this, "فرمت ساعت صحیح نیست! مثال معتبر: 16:30", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (timeToMinutes(end) <= timeToMinutes(start)) {
                Toast.makeText(this, "ساعت پایان کلاس باید بعد از ساعت شروع باشد!", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // مدیریت منطق نال‌پذیر بودن استاد
            var extractedTeacherPhone: String? = null
            if (teacherSelection.isNotEmpty() && teacherSelection != "بدون استاد (تخصیص بعداً)" && teacherSelection.contains("(")) {
                extractedTeacherPhone = teacherSelection.substringAfter("(").substringBefore(")")
            }

            val daysFormatted = selectedDays.joinToString("، ")

            val finalClass = ClassModel(
                id = if (isEditMode) classId else UUID.randomUUID().toString(),
                className = name,
                startTime = start,
                endTime = end,
                daysOfWeek = daysFormatted,
                sessionCount = sessionsStr.toInt(),
                teacherPhone = extractedTeacherPhone,
                status = ClassStatus.ACTIVE,
                createdAt = if (isEditMode) AppDatabase.getClassById(classId)?.createdAt ?: AppDatabase.today() else AppDatabase.today()
            )

            if (isEditMode) {
                val existing = AppDatabase.getClassById(classId)
                existing?.apply {
                    className = finalClass.className
                    startTime = finalClass.startTime
                    endTime = finalClass.endTime
                    daysOfWeek = finalClass.daysOfWeek
                    sessionCount = finalClass.sessionCount
                    teacherPhone = finalClass.teacherPhone
                }
                Toast.makeText(this@AddEditClassActivity, "اطلاعات کلاس ویرایش شد", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                RetrofitClient.instance.addClass(finalClass).enqueue(object : Callback<ApiResponse> {
                    override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                        if (response.isSuccessful && response.body()?.status == "success") {
                            AppDatabase.addClass(finalClass)
                            Toast.makeText(this@AddEditClassActivity, "کلاس جدید با موفقیت ثبت شد", Toast.LENGTH_SHORT).show()
                            finish()
                        } else {
                            Toast.makeText(this@AddEditClassActivity, "خطا در ثبت کلاس (پاسخ سرور)", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                        Toast.makeText(this@AddEditClassActivity, "خطا در اتصال به سرور! اینترنت را بررسی کنید", Toast.LENGTH_SHORT).show()
                    }
                })
            }
        }
    }

    private fun timeToMinutes(time: String): Int {
        val parts = time.split(":")
        return parts[0].toInt() * 60 + parts[1].toInt()
    }

    private fun setupStudentManagement() {
        rvEnrolledStudents = findViewById(R.id.rvEnrolledStudents)
        rvStudentSearchResults = findViewById(R.id.rvStudentSearchResults)
        etSearchNewStudent = findViewById(R.id.etSearchNewStudent)

        rvEnrolledStudents.layoutManager = LinearLayoutManager(this)
        rvStudentSearchResults.layoutManager = LinearLayoutManager(this)

        updateEnrolledList()
        setupSearchAdapter()

        etSearchNewStudent.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()
                if (query.isNotEmpty()) {
                    searchResultsList.clear()
                    val activeNotInClass = AppDatabase.getAllStudents().filter { it.isActive && it.classId != classId }
                    searchResultsList.addAll(activeNotInClass.filter { it.name.contains(query) || it.studentCode.contains(query) })
                    rvStudentSearchResults.adapter?.notifyDataSetChanged()
                    rvStudentSearchResults.visibility = View.VISIBLE
                } else {
                    rvStudentSearchResults.visibility = View.GONE
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun updateEnrolledList() {
        enrolledStudents.clear()
        enrolledStudents.addAll(AppDatabase.getStudentsInClass(classId))

        rvEnrolledStudents.adapter = object : RecyclerView.Adapter<StudentViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StudentViewHolder {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_class_manage, parent, false)
                return StudentViewHolder(view)
            }
            override fun onBindViewHolder(holder: StudentViewHolder, position: Int) {
                val student = enrolledStudents[position]
                holder.tvName.text = student.name
                holder.tvSub.text = "کد: ${student.studentCode}"
                holder.btnAction.text = "✖"
                holder.btnAction.setTextColor(0xFFEF4444.toInt())

                holder.btnAction.setOnClickListener {
                    AppDatabase.assignClassToStudent(student.phone, null)
                    updateEnrolledList()
                }
            }
            override fun getItemCount() = enrolledStudents.size
        }
    }

    private fun setupSearchAdapter() {
        rvStudentSearchResults.adapter = object : RecyclerView.Adapter<StudentViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StudentViewHolder {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_class_manage, parent, false)
                return StudentViewHolder(view)
            }
            override fun onBindViewHolder(holder: StudentViewHolder, position: Int) {
                val student = searchResultsList[position]
                holder.tvName.text = student.name
                holder.tvSub.text = student.phone
                holder.btnAction.text = "➕"
                holder.btnAction.setTextColor(0xFF10B981.toInt())

                holder.btnAction.setOnClickListener {
                    AppDatabase.assignClassToStudent(student.phone, classId)
                    etSearchNewStudent.text?.clear()
                    rvStudentSearchResults.visibility = View.GONE
                    updateEnrolledList()
                }
            }
            override fun getItemCount() = searchResultsList.size
        }
    }

    class StudentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.txtManageClassName)
        val tvSub: TextView = view.findViewById(R.id.txtManageClassTime)
        val btnAction: Button = view.findViewById(R.id.btnDeleteClass)
    }
}