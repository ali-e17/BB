package com.example.bb

data class CourseClass(
    val classId: String,
    var className: String,
    var termLevel: Int,
    var classTime: String,
    var teacherMobile: String?,
    val studentMobiles: MutableList<String>
)