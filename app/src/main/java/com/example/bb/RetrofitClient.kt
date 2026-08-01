package com.example.bb

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.google.gson.Gson
import okhttp3.Interceptor
import okhttp3.Dns
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.net.InetAddress

data class HardDeleteClassRequest(val id: String)

data class ApiResponse(
    val status: String = "",
    val message: String = "",
    val code: String? = null,
    val id: String? = null,
    val temporaryPassword: String? = null,
    val revision: Int = 0
)
data class AdminResetPasswordResponse(
    val status: String = "", val message: String = "", val temporaryPassword: String? = null
)
data class CreateAnnouncementResponse(val status: String="", val message:String="", val announcementId:String?=null, val recipientCount:Int=0)
data class UnreadAnnouncementCountResponse(val status:String="", val count:Int=0)
data class ProfileResponse(
    val status:String="", val role:String="", val userId:String="", val username:String="",
    val phone:String="", val displayName:String="", val avatarName:String="",
    val mustChangePassword:Boolean=false, val classId:String?=null, val className:String?=null
)
data class LoginRequest(val username:String,val password:String)
data class LoginResponse(
    val status:String="", val role:String?=null, val userId:String?=null, val username:String?=null,
    val phone:String?=null, val displayName:String?=null, val token:String?=null,
    val tokenExpiresAt:String?=null, val message:String?=null, val avatarName:String?=null,
    val mustChangePassword:Boolean=false
)
data class CompleteClassRequest(val id:String)
data class AssignClassRequest(val studentId:String,val classId:String?)
data class ToggleActiveRequest(val studentId:String,val isActive:Boolean)
data class ToggleTeacherActiveRequest(val teacherId:String,val isActive:Boolean)
data class AssignTeacherRequest(val classId:String,val teacherId:String?)
data class DeleteClassRequest(val id:String)
data class MarkAnnouncementReadRequest(val announcementId:String)
data class UpdateAvatarRequest(val userId:String="",val avatarName:String,val role:String="")
data class AdminResetPasswordRequest(val role:String,val profileId:String)
data class TrashRequest(val entity:String,val id:String,val reason:String="")
data class PermanentDeleteRequest(val entity:String,val id:String,val confirmation:String)
data class TrashItem(val id:String="",val name:String="",val code:String="",val deletedAt:String="",val reason:String="")

data class ReportClassInfo(val id:String="",val classCode:String="",val className:String="",val bookName:String="",val classLevel:String="",val termYear:String="",val termSeason:String="")
data class ReportComponentDto(val id:String="",val title:String="",val maxScore:Double=0.0,val sortOrder:Int=0)
data class ReportConfigDto(val id:String="",val passWithoutStarMin:Double=0.0,val conditionalMin:Double=0.0,val status:String="DRAFT",val revision:Int=0,val components:List<ReportComponentDto> = emptyList())
data class ReportConfigResponse(val status:String="",val message:String?=null,val `class`:ReportClassInfo?=null,val config:ReportConfigDto?=null)
data class SaveReportConfigRequest(val classId:String,val passWithoutStarMin:Double,val conditionalMin:Double,val expectedRevision:Int,val components:List<ReportComponentDto>)
data class SaveReportConfigResponse(val status:String="",val message:String="",val configId:String?=null,val revision:Int=0)
data class ReportRosterStudent(val id:String="",val name:String="",val studentCode:String="",val cardId:String?=null,val status:String="EMPTY",val revision:Int=0,val totalScore:Double=0.0,val resultCode:String?=null,val starCount:Int=0,val scores:Map<String,Double> = emptyMap())
data class ReportRosterResponse(val status:String="",val message:String?=null,val config:ReportConfigDto?=null,val students:List<ReportRosterStudent> = emptyList())
data class SaveReportStudentRequest(val studentId:String,val expectedRevision:Int,val scores:Map<String,Double?>)
data class SaveReportCardsRequest(val classId:String,val publish:Boolean,val editReason:String,val students:List<SaveReportStudentRequest>)
data class ReportScoreDto(val componentId:String="",val title:String="",val maxScore:Double=0.0,val score:Double=0.0)
data class ReportCardDto(
    val id:String="",val classId:String="",val studentId:String="",val studentCode:String="",val studentName:String="",
    val classCode:String="",val className:String="",val bookName:String="",val classLevel:String="",val termYear:String="",val termSeason:String="",
    val totalScore:Double=0.0,val resultCode:String="",val starCount:Int=0,val resultMessage:String="",val status:String="",
    val revision:Int=0,val publishedAt:String?=null,val updatedAt:String?=null,val scores:List<ReportScoreDto> = emptyList()
)
data class ReportCardResponse(val status:String="",val message:String?=null,val card:ReportCardDto?=null)
data class ResultMessagesResponse(val status:String="", val messages:Map<String,String> = emptyMap())
data class SaveResultMessagesRequest(val messages:Map<String,String>)
data class TermHistoryItem(
    val classId:String="",val classCode:String?=null,val className:String="",val bookName:String?=null,val classLevel:String?=null,
    val termYear:String?=null,val termSeason:String?=null,val teacherName:String?=null,val status:String="",
    val enrolledAt:String?=null,val leftAt:String?=null,val assignedAt:String?=null,val endedAt:String?=null,
    val absentCount:Int=0,val lateCount:Int=0,val reportCardId:String?=null,val totalScore:Double?=null,
    val resultCode:String?=null,val starCount:Int=0,val studentCount:Int=0,val publishedReportCount:Int=0
)
data class TermHistoryResponse(val status:String="",val role:String="",val items:List<TermHistoryItem> = emptyList())

interface ApiService {
    @POST("delete_class.php")
    fun hardDeleteClass(@Body request: HardDeleteClassRequest): Call<ApiResponse>
    @POST("login.php") fun login(@Body request:LoginRequest):Call<LoginResponse>
    @POST("logout.php") fun logout():Call<ApiResponse>
    @GET("get_profile.php") fun getProfile():Call<ProfileResponse>
    @POST("update_password.php") fun updatePassword(@Body request:UpdatePasswordRequest):Call<ApiResponse>
    @POST("admin_reset_password.php") fun adminResetPassword(@Body request:AdminResetPasswordRequest):Call<AdminResetPasswordResponse>
    @POST("update_avatar.php") fun updateAvatar(@Body request:UpdateAvatarRequest):Call<ApiResponse>

    @GET("get_students.php") fun getStudents():Call<List<StudentModel>>
    @POST("add_student.php") fun addStudent(@Body model:StudentModel):Call<ApiResponse>
    @POST("assign_class.php") fun assignClass(@Body request:AssignClassRequest):Call<ApiResponse>
    @POST("toggle_student_active.php") fun toggleStudentActive(@Body request:ToggleActiveRequest):Call<ApiResponse>

    @GET("get_teachers.php") fun getTeachers():Call<List<TeacherModel>>
    @POST("add_teacher.php") fun addTeacher(@Body model:TeacherModel):Call<ApiResponse>
    @POST("assign_teacher_to_class.php") fun assignTeacherToClass(@Body request:AssignTeacherRequest):Call<ApiResponse>
    @POST("toggle_teacher_active.php") fun toggleTeacherActive(@Body request:ToggleTeacherActiveRequest):Call<ApiResponse>

    @GET("get_classes.php") fun getClasses():Call<List<ClassModel>>
    @POST("add_class.php") fun addClass(@Body model:ClassModel):Call<ApiResponse>
    @POST("update_class.php") fun updateClass(@Body model:ClassModel):Call<ApiResponse>
    @POST("complete_class.php") fun completeClass(@Body request:CompleteClassRequest):Call<ApiResponse>

    @GET("get_trash.php") fun getTrash(@Query("entity") entity:String):Call<List<TrashItem>>
    @POST("trash_entity.php") fun trashEntity(@Body request:TrashRequest):Call<ApiResponse>
    @POST("restore_entity.php") fun restoreEntity(@Body request:TrashRequest):Call<ApiResponse>
    @POST("permanent_delete_entity.php") fun permanentDelete(@Body request:PermanentDeleteRequest):Call<ApiResponse>

    @GET("get_announcements.php") fun getAnnouncements(@Query("page") page:Int=1,@Query("limit") limit:Int=50):Call<List<Announcement>>
    @Multipart @POST("create_announcement.php") fun createAnnouncement(
        @Part("id") id:RequestBody,@Part("title") title:RequestBody,@Part("body") body:RequestBody,
        @Part("scope") scope:RequestBody,@Part("targetClassIds") targetClassIds:RequestBody,
        @Part attachment:MultipartBody.Part?
    ):Call<CreateAnnouncementResponse>
    @POST("mark_announcement_read.php") fun markAnnouncementRead(@Body request:MarkAnnouncementReadRequest):Call<ApiResponse>
    @GET("get_unread_announcement_count.php") fun getUnreadAnnouncementCount():Call<UnreadAnnouncementCountResponse>

    @GET("get_attendance_overview.php") fun getAttendanceOverview(@Query("class_id") classId:String):Call<AttendanceOverviewResponse>
    @GET("get_attendance_session.php") fun getAttendanceSession(@Query("class_id") classId:String,@Query("session_number") sessionNumber:Int):Call<AttendanceSessionResponse>
    @POST("finalize_attendance.php") fun finalizeAttendance(@Body request:FinalizeAttendanceRequest):Call<AttendanceSaveResponse>
    @POST("update_attendance.php") fun updateAttendance(@Body request:UpdateAttendanceRequest):Call<AttendanceSaveResponse>

    @GET("get_report_config.php") fun getReportConfig(@Query("class_id") classId:String):Call<ReportConfigResponse>
    @POST("save_report_config.php") fun saveReportConfig(@Body request:SaveReportConfigRequest):Call<SaveReportConfigResponse>
    @GET("get_report_roster.php") fun getReportRoster(@Query("class_id") classId:String):Call<ReportRosterResponse>
    @POST("save_report_cards.php") fun saveReportCards(@Body request:SaveReportCardsRequest):Call<ApiResponse>
    @GET("get_report_cards.php") fun getReportCards(@Query("class_id") classId:String?=null,@Query("student_id") studentId:String?=null):Call<List<ReportCardDto>>
    @GET("get_report_card.php") fun getReportCard(@Query("id") id:String):Call<ReportCardResponse>
    @GET("get_term_history.php") fun getTermHistory(@Query("role") role:String,@Query("id") id:String):Call<TermHistoryResponse>
    @GET("get_result_messages.php") fun getResultMessages():Call<ResultMessagesResponse>
    @POST("save_result_messages.php") fun saveResultMessages(@Body request:SaveResultMessagesRequest):Call<ApiResponse>
}

private class SessionInterceptor(private val context:Context):Interceptor {
    private val prefs=context.getSharedPreferences("LocalAppPrefs",Context.MODE_PRIVATE)
    override fun intercept(chain:Interceptor.Chain):okhttp3.Response {
        val request=chain.request(); val token=prefs.getString("API_TOKEN",null).orEmpty()
        val builder=request.newBuilder().header("Accept","application/json")
        if(token.isNotBlank()) builder.header("Authorization","Bearer $token")
        val response=chain.proceed(builder.build())
        if(response.code==401 && !request.url.encodedPath.endsWith("login.php")) {
            prefs.edit().clear().apply()
            Handler(Looper.getMainLooper()).post {
                context.startActivity(Intent(context,LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            }
        } else if(response.code==428) {
            Handler(Looper.getMainLooper()).post {
                context.startActivity(Intent(context,ForceChangePasswordActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        return response
    }
}

object RetrofitClient {
    private lateinit var appContext:Context
    fun init(context:Context){appContext=context.applicationContext}
    fun attendanceExportUrl(classId:String)=ApiConfig.BASE_URL+"export_attendance_excel.php?class_id="+java.net.URLEncoder.encode(classId,Charsets.UTF_8.name())
    val instance:ApiService by lazy {
        check(::appContext.isInitialized)
        val client = OkHttpClient.Builder()
            .dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> {
                    return if (hostname.equals(ApiConfig.API_HOST, ignoreCase = true)) {
                        listOf(InetAddress.getByName(ApiConfig.API_IP))
                    } else {
                        Dns.SYSTEM.lookup(hostname)
                    }
                }
            })
            .addInterceptor(SessionInterceptor(appContext))
            .build()

        Retrofit.Builder()
            .baseUrl(ApiConfig.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(Gson()))
            .build()
            .create(ApiService::class.java)
    }
}
