package com.example.bb

enum class EntryStatus { NOT_STARTED, IN_PROGRESS, COMPLETED }

data class StudentGrade(
    val id: String,
    val name: String,
    var status: EntryStatus = EntryStatus.NOT_STARTED,
    // نقشه‌ای (Map) که آیدیِ معیار رو به نمره‌اش وصل می‌کنه (مثلاً "Midterm" -> 18)
    val scores: MutableMap<String, Int?> = mutableMapOf()
)