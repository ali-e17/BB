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
interface ApiService {
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