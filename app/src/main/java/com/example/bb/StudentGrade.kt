package com.example.bb

enum class EntryStatus { NOT_STARTED, IN_PROGRESS, COMPLETED }

data class StudentGrade(
    val id: String,
    val name: String,
    val studentCode: String = id,
    var status: EntryStatus = EntryStatus.COMPLETED,
    val scores: MutableMap<String, Int?> = mutableMapOf()
)
