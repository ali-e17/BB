package com.example.bb

import android.content.Context
import com.google.gson.Gson
import okhttp3.Interceptor
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

data class ApiResponse(val status: String = "", val message: String = "")
data class CreateAnnouncementResponse(val status: String = "", val message: String = "", val announcementId: String? = null)
data class UnreadAnnouncementCountResponse(val status: String = "", val count: Int = 0)
data class ProfileResponse(
    val status: String = "", val role: String = "", val userId: String = "",
    val phone: String = "", val displayName: String = "", val avatarName: String = "",
    val classId: String? = null, val className: String? = null
)

interface ApiService {
    @POST("update_password.php") fun updatePassword(@Body request: UpdatePasswordRequest): Call<ApiResponse>
    @POST("assign_teacher_to_class.php") fun assignTeacherToClass(@Body request: AssignTeacherRequest): Call<ApiResponse>
    @GET("get_teachers.php") fun getTeachers(): Call<List<TeacherModel>>
    @POST("add_teacher.php") fun addTeacher(@Body teacherModel: TeacherModel): Call<ApiResponse>
    @POST("toggle_teacher_active.php") fun toggleTeacherActive(@Body request: ToggleTeacherActiveRequest): Call<ApiResponse>
    @POST("update_avatar.php") fun updateAvatar(@Body request: UpdateAvatarRequest): Call<ApiResponse>
    @POST("toggle_student_active.php") fun toggleStudentActive(@Body request: ToggleActiveRequest): Call<ApiResponse>
    @POST("login.php") fun login(@Body request: LoginRequest): Call<LoginResponse>
    @POST("logout.php") fun logout(): Call<ApiResponse>
    @GET("get_profile.php") fun getProfile(): Call<ProfileResponse>
    @POST("assign_class.php") fun assignClass(@Body request: AssignClassRequest): Call<ApiResponse>
    @GET("get_students.php") fun getStudents(): Call<List<StudentModel>>
    @POST("add_student.php") fun addStudent(@Body studentModel: StudentModel): Call<ApiResponse>
    @GET("get_classes.php") fun getClasses(): Call<List<ClassModel>>
    @POST("add_class.php") fun addClass(@Body classModel: ClassModel): Call<ApiResponse>
    @POST("update_class.php") fun updateClass(@Body classModel: ClassModel): Call<ApiResponse>
    @POST("complete_class.php") fun completeClass(@Body request: CompleteClassRequest): Call<ApiResponse>
    @POST("delete_class.php") fun deleteClass(@Body request: DeleteClassRequest): Call<ApiResponse>
    @GET("get_announcements.php") fun getAnnouncements(): Call<List<Announcement>>
    @Multipart @POST("create_announcement.php") fun createAnnouncement(
        @Part("id") id: RequestBody, @Part("title") title: RequestBody,
        @Part("body") body: RequestBody, @Part("scope") scope: RequestBody,
        @Part("targetClassIds") targetClassIds: RequestBody, @Part attachment: MultipartBody.Part?
    ): Call<CreateAnnouncementResponse>
    @POST("mark_announcement_read.php") fun markAnnouncementRead(@Body request: MarkAnnouncementReadRequest): Call<ApiResponse>
    @GET("get_unread_announcement_count.php") fun getUnreadAnnouncementCount(): Call<UnreadAnnouncementCountResponse>
    @POST("create_attendance_announcements.php") fun createAttendanceAnnouncements(@Body request: AttendanceAnnouncementsRequest): Call<AttendanceAnnouncementsResponse>
    @GET("get_attendance_overview.php") fun getAttendanceOverview(@Query("class_id") classId: String): Call<AttendanceOverviewResponse>
    @GET("get_attendance_session.php") fun getAttendanceSession(@Query("class_id") classId: String, @Query("session_number") sessionNumber: Int): Call<AttendanceSessionResponse>
    @POST("finalize_attendance.php") fun finalizeAttendance(@Body request: FinalizeAttendanceRequest): Call<AttendanceSaveResponse>
    @POST("update_attendance.php") fun updateAttendance(@Body request: UpdateAttendanceRequest): Call<AttendanceSaveResponse>
}

data class DeleteClassRequest(val id: String)
data class CompleteClassRequest(val id: String)
data class AssignClassRequest(val studentId: String, val classId: String?)
data class LoginRequest(val username: String, val password: String)
data class LoginResponse(
    val status: String = "", val role: String? = null, val userId: String? = null,
    val username: String? = null, val displayName: String? = null, val token: String? = null,
    val tokenExpiresAt: String? = null, val message: String? = null, val avatarName: String? = null
)
data class ToggleActiveRequest(val studentId: String, val isActive: Boolean)
data class ToggleTeacherActiveRequest(val teacherId: String, val isActive: Boolean)
data class MarkAnnouncementReadRequest(val announcementId: String)
data class AttendanceAnnouncementItemRequest(val studentId: String, val status: String, val delayMinutes: Int)
data class AttendanceAnnouncementsRequest(val classId: String, val date: String, val items: List<AttendanceAnnouncementItemRequest>)
data class AttendanceAnnouncementsResponse(val status: String = "", val message: String = "", val createdCount: Int = 0)
data class UpdateAvatarRequest(val userId: String, val avatarName: String, val role: String)
data class AssignTeacherRequest(val classId: String, val teacherPhone: String?)

private class AuthInterceptor(context: Context) : Interceptor {
    private val prefs = context.applicationContext.getSharedPreferences("LocalAppPrefs", Context.MODE_PRIVATE)
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val token = prefs.getString("API_TOKEN", null).orEmpty()
        val builder = chain.request().newBuilder().header("Accept", "application/json")
        if (token.isNotBlank()) builder.header("Authorization", "Bearer $token")
        return chain.proceed(builder.build())
    }
}

object RetrofitClient {
    private const val BASE_URL = "http://5.144.129.239/~bayaneba/api/"
    private lateinit var appContext: Context
    fun attendanceExportUrl(classId: String): String =
        "${BASE_URL}export_attendance_excel.php?class_id=${java.net.URLEncoder.encode(classId, Charsets.UTF_8.name())}"
    fun init(context: Context) { appContext = context.applicationContext }
    private val gson by lazy { Gson() }
    val instance: ApiService by lazy {
        check(::appContext.isInitialized) { "RetrofitClient.init(context) must be called from Application.onCreate()" }
        val client = OkHttpClient.Builder().addInterceptor(AuthInterceptor(appContext)).build()
        Retrofit.Builder().baseUrl(BASE_URL).client(client)
            .addConverterFactory(GsonConverterFactory.create(gson)).build().create(ApiService::class.java)
    }
}
