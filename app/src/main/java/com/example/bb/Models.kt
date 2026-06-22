package com.example.bb

import android.content.Context

// مدل دانش‌آموز
data class StudentModel(
    val name: String,          // نام و نام خانوادگی
    val phone: String,         // شماره تماس (نام کاربری - غیرقابل تغییر)
    val nationalId: String,    // کد ملی
    var password: String,      // پسورد فعلی (پیش‌فرض همان کد ملی است)
    var classId: String? = null // آی‌دی کلاسی که دانش‌آموز در آن عضو است (اگر null باشد یعنی کلاسی ندارد)
)

// مدل استاد (جدید)
data class TeacherModel(
    val name: String,          // نام و نام خانوادگی استاد
    val username: String,      // نام کاربری (شماره تماس یا شناسه یکتا)
    var password: String,      // کلمه عبور
    var classId: String? = null // آی‌دی کلاسی که به این استاد اختصاص داده شده (فقط یک کلاس)
)

// مدل کلاس (بروزرسانی شده برای پشتیبانی از ساعت کلاس)
data class ClassModel(
    val id: String,            // یک آی‌دی منحصربه‌فرد (مثلاً level_1 یا یک UUID پویای متغیر)
    val className: String,     // نام سطح کلاس (مثلاً "سطح ۱ بیان برتر")
    val classTime: String      // ساعت و روز کلاس (مثلاً "زوج ساعت ۱۷")
)

// دیتابیس لوکال مرکزی اپلیکیشن برای مدیریت پایدار اطلاعات
object AppDatabase {
    private const val PREFS_NAME = "AppDatabasePrefs"
    private val studentsList = mutableListOf<StudentModel>()
    private val classesList = mutableListOf<ClassModel>()
    private val teachersList = mutableListOf<TeacherModel>() // لیست داینامیک اساتید (جدید)

    // مقداردهی اولیه دیتابیس و تعریف دانش‌آموزان، کلاس‌ها و اساتید تست
    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains("initialized")) {
            // اضافه کردن دانش‌آموزان تست
            studentsList.add(StudentModel("دانش‌آموز تست (اصلی)", "student", "1111111111", "1234", null))
            studentsList.add(StudentModel("علی رضایی", "09123456789", "0021456789", "0021456789", null))
            studentsList.add(StudentModel("محسن کمالی", "09351112233", "1289654321", "1289654321", null))
            studentsList.add(StudentModel("نازنین بابلخانی", "09117778899", "2051234567", "2051234567", null))

            // اضافه کردن اساتید تست اولیه (جدید)
            teachersList.add(TeacherModel("استاد علی علوی", "teacher", "1234", null))
            teachersList.add(TeacherModel("دکتر مریم محمدی", "teacher2", "1234", null))

            saveAllStudents(context)
            saveAllClasses(context)
            saveAllTeachers(context) // ذخیره اساتید اولیه
            prefs.edit().putBoolean("initialized", true).apply()
        } else {
            loadAllStudents(context)
            loadAllClasses(context)
            loadAllTeachers(context) // لود اساتید از حافظه
        }
    }

    // --- مدیریت دانش‌آموزان ---
    fun getAllStudents(): List<StudentModel> = studentsList

    fun searchStudents(query: String): List<StudentModel> {
        if (query.isEmpty()) return emptyList()
        return studentsList.filter { it.name.contains(query, ignoreCase = true) }
    }

    fun getStudentByUsername(username: String): StudentModel? {
        return studentsList.find { it.phone == username }
    }

    fun updateStudentPassword(username: String, newPass: String, context: Context) {
        getStudentByUsername(username)?.let {
            it.password = newPass
            saveAllStudents(context)
        }
    }

    fun assignClassToStudent(phone: String, classId: String?, context: Context) {
        getStudentByUsername(phone)?.let {
            it.classId = classId
            saveAllStudents(context)
        }
    }

    fun getStudentsInClass(classId: String): List<StudentModel> {
        return studentsList.filter { it.classId == classId }
    }

    // --- مدیریت کلاس‌ها ---
    fun getAllClasses(): List<ClassModel> = classesList

    // همگام‌سازی نام متد با اکتیویتی تخصیص کلاس پنل اساتید
    fun getAllCreatedClasses(): List<ClassModel> = classesList

    fun addClass(classModel: ClassModel, context: Context) {
        classesList.add(classModel)
        saveAllClasses(context)
    }

    fun deleteClass(classId: String, context: Context) {
        classesList.removeAll { it.id == classId }
        saveAllClasses(context)
    }

    // --- مدیریت اساتید (بخش جدید اضافه شده) ---
    fun getAllTeachers(): List<TeacherModel> = teachersList

    fun getTeacherByUsername(username: String): TeacherModel? {
        return teachersList.find { it.username == username }
    }

    fun addTeacher(teacher: TeacherModel, context: Context) {
        teachersList.add(teacher)
        saveAllTeachers(context)
    }

    fun deleteTeacher(username: String, context: Context) {
        teachersList.removeAll { it.username == username }
        saveAllTeachers(context)
    }

    fun assignClassToTeacher(username: String, classId: String, context: Context) {
        getTeacherByUsername(username)?.let {
            it.classId = classId
            saveAllTeachers(context)
        }
    }

    // --- ذخیره و لود در SharedPreferences ---
    private fun saveAllStudents(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putInt("student_count", studentsList.size)
        studentsList.forEachIndexed { index, student ->
            editor.putString("student_${index}_name", student.name)
            editor.putString("student_${index}_phone", student.phone)
            editor.putString("student_${index}_nationalId", student.nationalId)
            editor.putString("student_${index}_password", student.password)
            editor.putString("student_${index}_classId", student.classId)
        }
        editor.apply()
    }

    private fun loadAllStudents(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val count = prefs.getInt("student_count", 0)
        studentsList.clear()
        for (i in 0 until count) {
            val name = prefs.getString("student_${i}_name", "") ?: ""
            val phone = prefs.getString("student_${i}_phone", "") ?: ""
            val nationalId = prefs.getString("student_${i}_nationalId", "") ?: ""
            val password = prefs.getString("student_${i}_password", "") ?: ""
            val classId = prefs.getString("student_${i}_classId", null)
            if (phone.isNotEmpty()) {
                studentsList.add(StudentModel(name, phone, nationalId, password, classId))
            }
        }
    }

    private fun saveAllClasses(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putInt("class_count", classesList.size)
        classesList.forEachIndexed { index, clazz ->
            editor.putString("class_${index}_id", clazz.id)
            editor.putString("class_${index}_name", clazz.className)
            editor.putString("class_${index}_time", clazz.classTime)
        }
        editor.apply()
    }

    private fun loadAllClasses(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val count = prefs.getInt("class_count", 0)
        classesList.clear()
        for (i in 0 until count) {
            val id = prefs.getString("class_${i}_id", "") ?: ""
            val name = prefs.getString("class_${i}_name", "") ?: ""
            val time = prefs.getString("class_${i}_time", "") ?: ""
            if (id.isNotEmpty()) {
                classesList.add(ClassModel(id, name, time))
            }
        }
    }

    // متدهای ذخیره و لود اساتید (جدید)
    private fun saveAllTeachers(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putInt("teacher_count", teachersList.size)
        teachersList.forEachIndexed { index, teacher ->
            editor.putString("teacher_${index}_name", teacher.name)
            editor.putString("teacher_${index}_username", teacher.username)
            editor.putString("teacher_${index}_password", teacher.password)
            editor.putString("teacher_${index}_classId", teacher.classId)
        }
        editor.apply()
    }

    private fun loadAllTeachers(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val count = prefs.getInt("teacher_count", 0)
        teachersList.clear()
        for (i in 0 until count) {
            val name = prefs.getString("teacher_${i}_name", "") ?: ""
            val username = prefs.getString("teacher_${i}_username", "") ?: ""
            val password = prefs.getString("teacher_${i}_password", "") ?: ""
            val classId = prefs.getString("teacher_${i}_classId", null)
            if (username.isNotEmpty()) {
                teachersList.add(TeacherModel(name, username, password, classId))
            }
        }
    }
}