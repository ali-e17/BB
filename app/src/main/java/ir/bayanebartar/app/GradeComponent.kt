package ir.bayanebartar.app

import java.io.Serializable

data class GradeComponent(
    val id: String,
    val name: String,
    var maxScore: Int = 0,
    var isSelected: Boolean = false
) : Serializable
