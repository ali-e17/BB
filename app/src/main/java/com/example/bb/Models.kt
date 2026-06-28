package com.example.bb

import android.content.Context

data class StudentModel(
    val name: String,
    val phone: String,
    val nationalId: String,
    var password: String,
    var classId: String? = null
)

// مدل استاد آپدیت شده (کد ملی و چند کلاس)
data class TeacherModel(
    var name: String,
    var username: String,      // همون شماره تماس
    var nationalId: String,    // کد ملی
    var password: String,
    var classIds: String = "", // لیست کلاس‌ها (با کاما جدا میشن)
    var isActive: Boolean = true
)

data class ClassModel(
    val id: String,
    val className: String,
    val classTime: String
)

object AppDatabase {
    private const val PREFS_NAME = "AppDatabasePrefs"
    private val studentsList = mutableListOf<StudentModel>()
    private val classesList = mutableListOf<ClassModel>()
    private val teachersList = mutableListOf<TeacherModel>()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains("initialized")) {
            studentsList.add(StudentModel("دانش‌آموز تست", "student", "1111111111", "1234", null))
            // اساتید تستی
            teachersList.add(TeacherModel("استاد علی علوی", "09120000000", "1234567890", "1234567890", "", true))

            saveAllStudents(context)
            saveAllClasses(context)
            saveAllTeachers(context)
            prefs.edit().putBoolean("initialized", true).apply()
        } else {
            loadAllStudents(context)
            loadAllClasses(context)
            loadAllTeachers(context)
        }
    }

    // --- دانش آموزان ---
    fun getAllStudents(): List<StudentModel> = studentsList
    fun searchStudents(query: String): List<StudentModel> = studentsList.filter { it.name.contains(query, ignoreCase = true) }
    fun getStudentByUsername(username: String): StudentModel? = studentsList.find { it.phone == username }
    fun updateStudentPassword(username: String, newPass: String, context: Context) {
        getStudentByUsername(username)?.let { it.password = newPass; saveAllStudents(context) }
    }
    fun assignClassToStudent(phone: String, classId: String?, context: Context) {
        getStudentByUsername(phone)?.let { it.classId = classId; saveAllStudents(context) }
    }
    fun getStudentsInClass(classId: String): List<StudentModel> = studentsList.filter { it.classId == classId }

    // --- کلاس‌ها ---
    fun getAllClasses(): List<ClassModel> = classesList
    fun getAllCreatedClasses(): List<ClassModel> = classesList
    fun addClass(classModel: ClassModel, context: Context) { classesList.add(classModel); saveAllClasses(context) }
    fun deleteClass(classId: String, context: Context) { classesList.removeAll { it.id == classId }; saveAllClasses(context) }
    fun getClassNameById(classId: String?): String? = classesList.find { it.id == classId }?.className

    // --- اساتید ---
    fun getAllTeachers(): List<TeacherModel> = teachersList
    fun getTeacherByUsername(username: String): TeacherModel? = teachersList.find { it.username == username }

    fun addTeacher(teacher: TeacherModel, context: Context) {
        // اگر استاد قبلا بود آپدیت میکنه، اگر نبود اضافه میکنه
        val index = teachersList.indexOfFirst { it.username == teacher.username }
        if (index != -1) teachersList[index] = teacher else teachersList.add(teacher)
        saveAllTeachers(context)
    }

    // گرفتن کلاس‌های آزاد (تخصیص نیافته به هیچ استادی)
    fun getAvailableClasses(): List<ClassModel> {
        val assignedIds = teachersList.flatMap { it.classIds.split(",") }.filter { it.isNotEmpty() }
        return classesList.filter { it.id !in assignedIds }
    }

    // گرفتن کلاس‌های یک استاد خاص
    fun getTeacherClasses(username: String): List<ClassModel> {
        val teacher = getTeacherByUsername(username) ?: return emptyList()
        val ids = teacher.classIds.split(",").filter { it.isNotEmpty() }
        return classesList.filter { it.id in ids }
    }

    fun assignClassToTeacher(username: String, classId: String, context: Context) {
        getTeacherByUsername(username)?.let {
            val currentIds = it.classIds.split(",").filter { id -> id.isNotEmpty() }.toMutableList()
            if (!currentIds.contains(classId)) {
                currentIds.add(classId)
                it.classIds = currentIds.joinToString(",")
                saveAllTeachers(context)
            }
        }
    }

    fun removeClassFromTeacher(username: String, classId: String, context: Context) {
        getTeacherByUsername(username)?.let {
            val currentIds = it.classIds.split(",").filter { id -> id.isNotEmpty() }.toMutableList()
            currentIds.remove(classId)
            it.classIds = currentIds.joinToString(",")
            saveAllTeachers(context)
        }
    }

    fun toggleTeacherArchiveStatus(username: String, context: Context): Boolean {
        val teacher = getTeacherByUsername(username) ?: return false
        // اگر کلاس داره، اجازه بایگانی نده
        if (teacher.isActive && teacher.classIds.isNotEmpty()) return false
        teacher.isActive = !teacher.isActive
        saveAllTeachers(context)
        return true
    }

    // --- ذخیره و لود ---
    private fun saveAllStudents(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        prefs.putInt("student_count", studentsList.size)
        studentsList.forEachIndexed { i, s ->
            prefs.putString("student_${i}_name", s.name)
            prefs.putString("student_${i}_phone", s.phone)
            prefs.putString("student_${i}_nationalId", s.nationalId)
            prefs.putString("student_${i}_password", s.password)
            prefs.putString("student_${i}_classId", s.classId)
        }
        prefs.apply()
    }
    private fun loadAllStudents(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        studentsList.clear()
        for (i in 0 until prefs.getInt("student_count", 0)) {
            val phone = prefs.getString("student_${i}_phone", "") ?: ""
            if (phone.isNotEmpty()) studentsList.add(StudentModel(
                prefs.getString("student_${i}_name", "") ?: "", phone,
                prefs.getString("student_${i}_nationalId", "") ?: "",
                prefs.getString("student_${i}_password", "") ?: "",
                prefs.getString("student_${i}_classId", null)
            ))
        }
    }
    private fun saveAllClasses(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        prefs.putInt("class_count", classesList.size)
        classesList.forEachIndexed { i, c ->
            prefs.putString("class_${i}_id", c.id)
            prefs.putString("class_${i}_name", c.className)
            prefs.putString("class_${i}_time", c.classTime)
        }
        prefs.apply()
    }
    private fun loadAllClasses(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        classesList.clear()
        for (i in 0 until prefs.getInt("class_count", 0)) {
            val id = prefs.getString("class_${i}_id", "") ?: ""
            if (id.isNotEmpty()) classesList.add(ClassModel(id,
                prefs.getString("class_${i}_name", "") ?: "",
                prefs.getString("class_${i}_time", "") ?: ""
            ))
        }
    }
    private fun saveAllTeachers(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        prefs.putInt("teacher_count", teachersList.size)
        teachersList.forEachIndexed { i, t ->
            prefs.putString("teacher_${i}_name", t.name)
            prefs.putString("teacher_${i}_username", t.username)
            prefs.putString("teacher_${i}_nationalId", t.nationalId)
            prefs.putString("teacher_${i}_password", t.password)
            prefs.putString("teacher_${i}_classIds", t.classIds)
            prefs.putBoolean("teacher_${i}_isActive", t.isActive)
        }
        prefs.apply()
    }
    private fun loadAllTeachers(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        teachersList.clear()
        for (i in 0 until prefs.getInt("teacher_count", 0)) {
            val username = prefs.getString("teacher_${i}_username", "") ?: ""
            if (username.isNotEmpty()) teachersList.add(TeacherModel(
                prefs.getString("teacher_${i}_name", "") ?: "", username,
                prefs.getString("teacher_${i}_nationalId", "") ?: "",
                prefs.getString("teacher_${i}_password", "") ?: "",
                prefs.getString("teacher_${i}_classIds", "") ?: "",
                prefs.getBoolean("teacher_${i}_isActive", true)
            ))
        }
    }
}