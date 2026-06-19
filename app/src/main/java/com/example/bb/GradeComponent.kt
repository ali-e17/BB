package com.example.bb
import java.io.Serializable

data class GradeComponent(
    val id: String,
    val name: String,
    var maxScore: Int,
    var isSelected: Boolean = true
) : Serializable