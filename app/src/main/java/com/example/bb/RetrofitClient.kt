package com.example.bb

import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

// ۱. مدلی برای خوندن پیام‌های موفقیت یا خطای سرور
data class ApiResponse(
    val status: String,
    val message: String
)

// ۲. تعریف مسیرهای سرور (به این میگن API Interface)
interface ApiService
{
    @POST("toggle_student_active.php")
    fun toggleStudentActive(@Body request: ToggleActiveRequest): Call<ApiResponse>

    @POST("login.php")
    fun login(@Body request: LoginRequest): Call<LoginResponse>

    @POST("assign_class.php")
    fun assignClass(@Body request: AssignClassRequest): Call<ApiResponse>

    // دریافت لیست دانش‌آموزان
    @GET("get_students.php")
    fun getStudents(): Call<List<StudentModel>>

    // ثبت دانش‌آموز جدید
    @POST("add_student.php")
    fun addStudent(@Body studentModel: StudentModel): Call<ApiResponse>


    @POST("complete_class.php")
    fun completeClass(@Body request: CompleteClassRequest): Call<ApiResponse>

    // درخواست گرفتن لیست کلاس‌ها
    @GET("get_classes.php")
    fun getClasses(): Call<List<ClassModel>>

    // درخواست اضافه کردن کلاس جدید
    @POST("add_class.php")
    fun addClass(@Body classModel: ClassModel): Call<ApiResponse>

    // درخواست حذف کلاس
    @POST("delete_class.php")
    fun deleteClass(@Body request: DeleteClassRequest): Call<ApiResponse>
}

// مدلی برای فرستادن فقط آی‌دی کلاس موقع حذف
data class DeleteClassRequest(val id: String)

// ۳. موتور اصلی رتروفیت که به هاست شما وصل میشه
object RetrofitClient {
    // 🌟 همون آدرس موقتی که با آی‌پی جواب داد (حتماً باید با اسلش / تموم بشه)
    private const val BASE_URL = "http://5.144.129.239/~bayaneba/api/"

    val instance: ApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        retrofit.create(ApiService::class.java)
    }
}
data class CompleteClassRequest(val id: String)
data class AssignClassRequest(val studentId: String, val classId: String?)

data class LoginRequest(val username: String, val password: String)
data class LoginResponse(val status: String, val role: String?, val displayName: String?, val message: String?)
data class ToggleActiveRequest(val studentId: String, val isActive: Boolean)