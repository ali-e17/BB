package ir.bayanebartar.app

data class ClassNameOption(
    val id: Long = 0,
    val name: String = ""
)

data class AddClassNameOptionRequest(
    val name: String
)

data class DeleteClassNameOptionRequest(
    val id: Long
)
