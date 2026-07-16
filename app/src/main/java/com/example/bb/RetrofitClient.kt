package com.example.bb

import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

data class ApiResponse(
    val status: String = "",
    val message: String = ""
)

interface ApiService {

    @POST("update_avatar.php")
    fun updateAvatar(@Body request: UpdateAvatarRequest): Call<ApiResponse>

    @POST("toggle_student_active.php")
    fun toggleStudentActive(@Body request: ToggleActiveRequest): Call<ApiResponse>

    @POST("login.php")
    fun login(@Body request: LoginRequest): Call<LoginResponse>

    /**
     * classId = null یعنی دانش‌آموز از کلاس فعلی خارج شود.
     * classId دارای مقدار یعنی به کلاس جدید منتقل/اضافه شود.
     */
    @POST("assign_class.php")
    fun assignClass(@Body request: AssignClassRequest): Call<ApiResponse>

    @GET("get_students.php")
    fun getStudents(): Call<List<StudentModel>>

    @POST("add_student.php")
    fun addStudent(@Body studentModel: StudentModel): Call<ApiResponse>

    @GET("get_classes.php")
    fun getClasses(): Call<List<ClassModel>>

    @POST("add_class.php")
    fun addClass(@Body classModel: ClassModel): Call<ApiResponse>

    /**
     * این endpoint باید در هاست وجود داشته باشد و کلاس را با id ویرایش کند.
     */
    @POST("update_class.php")
    fun updateClass(@Body classModel: ClassModel): Call<ApiResponse>

    @POST("complete_class.php")
    fun completeClass(@Body request: CompleteClassRequest): Call<ApiResponse>

    /**
     * teacherPhone دارای مقدار: تخصیص کلاس به استاد
     * teacherPhone = null: حذف استاد از کلاس
     */
    @POST("assign_teacher_to_class.php")
    fun assignTeacherToClass(@Body request: AssignTeacherRequest): Call<ApiResponse>

    // برای سازگاری با نسخه‌های قبلی نگه داشته شده؛ در رابط جدید حذف واقعی نداریم.
    @POST("delete_class.php")
    fun deleteClass(@Body request: DeleteClassRequest): Call<ApiResponse>
}

data class DeleteClassRequest(val id: String)
data class CompleteClassRequest(val id: String)
data class AssignTeacherRequest(val classId: String, val teacherPhone: String?)
data class AssignClassRequest(val studentId: String, val classId: String?)
data class LoginRequest(val username: String, val password: String)
data class LoginResponse(
    val status: String = "",
    val role: String? = null,
    val userId: String? = null, // 🌟 این خط اضافه شد
    val displayName: String? = null,
    val message: String? = null
)
data class ToggleActiveRequest(val studentId: String, val isActive: Boolean)

object RetrofitClient {
    private const val BASE_URL = "http://5.144.129.239/~bayaneba/api/"

    val instance: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}

data class UpdateAvatarRequest(val studentId: String, val avatarName: String)
