package com.example.bb

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class UserRole { STUDENT, TEACHER, ADMIN }
enum class ClassStatus { ACTIVE, COMPLETED }
enum class AttendanceStatus { PRESENT, LATE, ABSENT }
enum class AudienceType { ALL_STUDENTS, ALL_TEACHERS, CLASS, STUDENT }
enum class MessageType { TEXT_ONLY, FILE_UPLOAD, ASSIGNMENT }

data class AuthenticatedUser(val role: UserRole, val phone: String, val displayName: String)

data class AdminModel(
    var name: String,
    var phone: String,
    var nationalId: String,
    var password: String
)

data class StudentModel(
    val id: String,
    var firstName: String, // 🌟 نام به صورت جداگانه
    var lastName: String,  // 🌟 نام خانوادگی به صورت جداگانه
    var studentCode: String = "",
    var phone: String,
    var nationalId: String,
    var password: String,
    var classId: String? = null,
    var registrationDate: String = AppDatabase.today(),
    var isActive: Boolean = true,
    var avatarResId: Int = R.drawable.avatar_student_1
) : Serializable {
    // 🌟 ترفند اصلی: یک Property مجازی تعریف می‌کنیم که نام و فامیل را می‌چسباند.
    val name: String
        get() = "$firstName $lastName"
}

data class TeacherModel(
    var name: String,
    var username: String,
    var nationalId: String,
    var password: String,
    var classIds: String = "",
    var isActive: Boolean = true
) : Serializable

data class ClassModel(
    val id: String,
    var className: String,
    var startTime: String,
    var endTime: String,
    var daysOfWeek: String,
    var sessionCount: Int,
    var teacherPhone: String? = null,
    var status: ClassStatus = ClassStatus.ACTIVE,
    var createdAt: String = AppDatabase.today(),
    var completedAt: String? = null
) : Serializable {
    val classTime: String
        get() = "$daysOfWeek | $startTime تا $endTime | $sessionCount جلسه"
}

data class EnrollmentModel(
    val id: String,
    val studentId: String,
    val classId: String,
    val startedAt: String,
    var endedAt: String? = null
)

data class AttendanceItem(
    val studentId: String,
    var status: AttendanceStatus = AttendanceStatus.PRESENT,
    var delayMinutes: Int = 0
)

data class AttendanceSession(
    val id: String,
    val classId: String,
    val date: String,
    val teacherPhone: String,
    val items: MutableList<AttendanceItem>,
    val finalizedAt: String
)

data class Announcement(
    val id: String,
    val title: String,
    val body: String,
    val senderName: String,
    val senderPhone: String,
    val date: String,
    val type: MessageType = MessageType.TEXT_ONLY,
    val audienceType: AudienceType,
    val targetId: String? = null,
    val attachmentName: String? = null,
    val attachmentUrl: String? = null
)

data class ReportCardModel(
    val id: String,
    val classId: String,
    val studentId: String,
    val criteria: List<GradeComponent>,
    val scores: Map<String, Int>,
    val publishedAt: String
)

object AppDatabase {
    private const val PREFS_NAME = "AppDatabasePrefs"
    private const val DATA_KEY = "app_data_v2"

    private lateinit var appContext: Context
    private var admin = AdminModel("مدیر آموزشگاه", "09120000001", "0012345678", "0012345678")
    private val students = mutableListOf<StudentModel>()
    private val teachers = mutableListOf<TeacherModel>()
    private val classes = mutableListOf<ClassModel>()
    private val enrollments = mutableListOf<EnrollmentModel>()
    private val attendanceSessions = mutableListOf<AttendanceSession>()
    private val announcements = mutableListOf<Announcement>()
    private val reportCards = mutableListOf<ReportCardModel>()

    fun init(context: Context) {
        appContext = context.applicationContext
        val raw = prefs().getString(DATA_KEY, null)
        if (raw.isNullOrBlank()) {
            migrateLegacyData()
            if (teachers.isEmpty()) {
                teachers += TeacherModel("استاد علی علوی", "09120000002", "1234567890", "1234567890")
            }
            save()
        } else {
            load(JSONObject(raw))
        }
    }

    fun today(): String = SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date())
    private fun now(): String = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date())
    private fun prefs() = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun authenticate(phone: String, password: String): AuthenticatedUser? = when {
        admin.phone == phone && admin.password == password -> AuthenticatedUser(UserRole.ADMIN, phone, admin.name)
        teachers.any { it.username == phone && it.password == password && it.isActive } -> {
            val teacher = teachers.first { it.username == phone }
            AuthenticatedUser(UserRole.TEACHER, phone, teacher.name)
        }
        students.any { it.phone == phone && it.password == password && it.isActive } -> {
            val student = students.first { it.phone == phone }
            AuthenticatedUser(UserRole.STUDENT, phone, student.name)
        }
        else -> null
    }

    fun getDisplayName(role: UserRole, phone: String): String = when (role) {
        UserRole.ADMIN -> admin.name
        UserRole.TEACHER -> getTeacherByUsername(phone)?.name ?: phone
        UserRole.STUDENT -> getStudentByUsername(phone)?.name ?: phone
    }

    fun updatePassword(role: UserRole, phone: String, oldPassword: String, newPassword: String): Boolean {
        val updated = when (role) {
            UserRole.ADMIN -> if (admin.phone == phone && admin.password == oldPassword) {
                admin.password = newPassword; true
            } else false
            UserRole.TEACHER -> getTeacherByUsername(phone)?.takeIf { it.password == oldPassword }?.let {
                it.password = newPassword; true
            } ?: false
            UserRole.STUDENT -> getStudentByUsername(phone)?.takeIf { it.password == oldPassword }?.let {
                it.password = newPassword; true
            } ?: false
        }
        if (updated) save()
        return updated
    }

    fun getAllStudents(): List<StudentModel> = students.toList()
    fun getStudentById(id: String): StudentModel? = students.find { it.id == id }
    fun getStudentByUsername(phone: String): StudentModel? = students.find { it.phone == phone }
    fun searchStudents(query: String): List<StudentModel> = students.filter {
        it.name.contains(query, true) || it.studentCode.contains(query, true) || it.phone.contains(query)
    }
    fun getStudentsInClass(classId: String): List<StudentModel> = students.filter { it.classId == classId && it.isActive }
    fun getStudentsEverInClass(classId: String): List<StudentModel> {
        val ids = enrollments.filter { it.classId == classId }.map { it.studentId }.toSet()
        return students.filter { it.id in ids }
    }

    fun upsertStudent(student: StudentModel, originalPhone: String? = null): String? {
        if (students.any { it.phone == student.phone && it.id != student.id }) return "این شماره تلفن قبلاً ثبت شده است"
        if (students.any { it.nationalId == student.nationalId && it.id != student.id }) return "این کد ملی قبلاً ثبت شده است"
        if (students.any { it.studentCode == student.studentCode && it.id != student.id && student.studentCode.isNotBlank() }) return "این کد دانش‌آموزی قبلاً ثبت شده است"
        val index = students.indexOfFirst { it.id == student.id || (originalPhone != null && it.phone == originalPhone) }
        if (index >= 0) students[index] = student else students += student
        save()
        return null
    }

    fun setStudentActive(id: String, active: Boolean): Boolean {
        val student = getStudentById(id) ?: return false
        if (!active && student.classId != null) return false
        student.isActive = active
        save()
        return true
    }

    fun assignClassToStudent(phone: String, classId: String?, context: Context? = null) {
        val student = getStudentByUsername(phone) ?: return
        if (student.classId == classId) return
        enrollments.find { it.studentId == student.id && it.endedAt == null }?.endedAt = today()
        student.classId = classId
        if (classId != null) {
            enrollments += EnrollmentModel(UUID.randomUUID().toString(), student.id, classId, today())
        }
        save()
    }

    fun getEnrollmentHistory(studentId: String): List<EnrollmentModel> = enrollments.filter { it.studentId == studentId }

    fun getAllClasses(includeCompleted: Boolean = true): List<ClassModel> =
        classes.filter { includeCompleted || it.status == ClassStatus.ACTIVE }.toList()
    fun getAllCreatedClasses(): List<ClassModel> = getAllClasses()
    fun getClassById(id: String): ClassModel? = classes.find { it.id == id }
    fun getClassNameById(id: String?): String? = classes.find { it.id == id }?.className

    fun addClass(model: ClassModel, context: Context? = null) {
        classes += model
        save()
    }

    fun completeClass(classId: String): Boolean {
        val model = getClassById(classId) ?: return false
        model.status = ClassStatus.COMPLETED
        model.completedAt = today()
        getStudentsInClass(classId).forEach { assignClassToStudent(it.phone, null) }
        syncTeacherClassIds()
        save()
        return true
    }

    @Deprecated("کلاس تاریخی نباید حذف شود؛ از completeClass استفاده کنید")
    fun deleteClass(classId: String, context: Context? = null) { completeClass(classId) }

    fun getAllTeachers(): List<TeacherModel> = teachers.toList()
    fun getTeacherByUsername(phone: String): TeacherModel? = teachers.find { it.username == phone }

    fun upsertTeacher(teacher: TeacherModel, originalPhone: String? = null): String? {
        if (teachers.any { it.username == teacher.username && it.username != originalPhone }) return "این شماره تلفن قبلاً ثبت شده است"
        if (teachers.any { it.nationalId == teacher.nationalId && it.username != originalPhone }) return "این کد ملی قبلاً ثبت شده است"
        val index = teachers.indexOfFirst { it.username == originalPhone || it.username == teacher.username }
        if (index >= 0) {
            val oldPhone = teachers[index].username
            teachers[index] = teacher
            if (oldPhone != teacher.username) classes.filter { it.teacherPhone == oldPhone }.forEach { it.teacherPhone = teacher.username }
        } else teachers += teacher
        syncTeacherClassIds()
        save()
        return null
    }

    fun addTeacher(teacher: TeacherModel, context: Context? = null) { upsertTeacher(teacher) }

    fun getAvailableClasses(): List<ClassModel> = classes.filter {
        it.status == ClassStatus.ACTIVE && it.teacherPhone == null
    }

    fun getTeacherClasses(phone: String): List<ClassModel> = classes.filter {
        it.teacherPhone == phone && it.status == ClassStatus.ACTIVE
    }

    fun assignClassToTeacher(phone: String, classId: String, context: Context? = null) {
        val model = getClassById(classId) ?: return
        if (model.status != ClassStatus.ACTIVE || model.teacherPhone != null) return
        model.teacherPhone = phone
        syncTeacherClassIds()
        save()
    }

    fun removeClassFromTeacher(phone: String, classId: String, context: Context? = null) {
        getClassById(classId)?.takeIf { it.teacherPhone == phone }?.teacherPhone = null
        syncTeacherClassIds()
        save()
    }

    fun toggleTeacherArchiveStatus(phone: String, context: Context? = null): Boolean {
        val teacher = getTeacherByUsername(phone) ?: return false
        if (teacher.isActive && getTeacherClasses(phone).isNotEmpty()) return false
        teacher.isActive = !teacher.isActive
        save()
        return true
    }

    private fun syncTeacherClassIds() {
        teachers.forEach { teacher ->
            teacher.classIds = classes.filter { it.teacherPhone == teacher.username && it.status == ClassStatus.ACTIVE }
                .joinToString(",") { it.id }
        }
    }

    fun getAttendance(classId: String, date: String): AttendanceSession? =
        attendanceSessions.find { it.classId == classId && it.date == date }

    fun getAttendanceHistory(classId: String): List<AttendanceSession> =
        attendanceSessions.filter { it.classId == classId }.sortedByDescending { it.date }

    fun finalizeAttendance(classId: String, date: String, teacherPhone: String, items: List<AttendanceItem>): Boolean {
        if (getAttendance(classId, date) != null) return false
        val normalized = items.map {
            it.copy(delayMinutes = if (it.status == AttendanceStatus.LATE) it.delayMinutes.coerceAtLeast(1) else 0)
        }.toMutableList()
        attendanceSessions += AttendanceSession(UUID.randomUUID().toString(), classId, date, teacherPhone, normalized, now())
        val className = getClassNameById(classId) ?: "کلاس"
        normalized.filter { it.status != AttendanceStatus.PRESENT }.forEach { item ->
            val student = getStudentById(item.studentId) ?: return@forEach
            val isLate = item.status == AttendanceStatus.LATE
            announcements += Announcement(
                id = UUID.randomUUID().toString(),
                title = if (isLate) "ثبت تأخیر" else "ثبت غیبت",
                body = if (isLate) "برای ${student.name} در $className، ${item.delayMinutes} دقیقه تأخیر ثبت شد."
                else "برای ${student.name} در $className غیبت ثبت شد.",
                senderName = "سامانه حضور و غیاب",
                senderPhone = teacherPhone,
                date = now(),
                audienceType = AudienceType.STUDENT,
                targetId = student.id
            )
        }
        save()
        return true
    }

    fun addAnnouncement(announcement: Announcement) {
        announcements.add(0, announcement)
        save()
    }

    fun getAnnouncementsFor(role: UserRole, phone: String): List<Announcement> {
        val visible = when (role) {
            UserRole.ADMIN -> announcements
            UserRole.TEACHER -> {
                val classIds = getTeacherClasses(phone).map { it.id }.toSet()
                announcements.filter {
                    (it.audienceType == AudienceType.ALL_TEACHERS) ||
                            (it.audienceType == AudienceType.CLASS && it.targetId != null && it.targetId in classIds) || it.senderPhone == phone
                }
            }
            UserRole.STUDENT -> {
                val student = getStudentByUsername(phone)
                announcements.filter {
                    it.audienceType == AudienceType.ALL_STUDENTS ||
                            (it.audienceType == AudienceType.CLASS && it.targetId == student?.classId) ||
                            (it.audienceType == AudienceType.STUDENT && it.targetId == student?.id)
                }
            }
        }
        return visible.sortedByDescending { it.date }
    }

    fun publishReportCards(classId: String, criteria: List<GradeComponent>, grades: List<StudentGrade>) {
        val publishedAt = now()
        grades.forEach { grade ->
            reportCards += ReportCardModel(
                UUID.randomUUID().toString(), classId, grade.id,
                criteria.map { it.copy() }, grade.scores.mapValues { it.value ?: 0 }, publishedAt
            )
        }
        save()
    }

    fun getReportCardsForStudent(studentId: String): List<ReportCardModel> =
        reportCards.filter { it.studentId == studentId }.sortedByDescending { it.publishedAt }

    private fun migrateLegacyData() {
        val old = prefs()
        for (i in 0 until old.getInt("student_count", 0)) {
            val phone = old.getString("student_${i}_phone", "").orEmpty()
            if (phone.isNotBlank()) {
                val legacyName = old.getString("student_${i}_name", "").orEmpty()
                val parts = legacyName.trim().split(Regex("\\s+"), limit = 2)
                students += StudentModel(
                    id = UUID.randomUUID().toString(),
                    firstName = parts.firstOrNull().orEmpty(),
                    lastName = parts.getOrNull(1).orEmpty(),
                    studentCode = "S${i + 1}", phone = phone,
                    nationalId = old.getString("student_${i}_nationalId", "").orEmpty(),
                    password = old.getString("student_${i}_password", "").orEmpty(),
                    classId = old.getString("student_${i}_classId", null)
                )
            }
        }
        for (i in 0 until old.getInt("teacher_count", 0)) {
            val phone = old.getString("teacher_${i}_username", "").orEmpty()
            if (phone.isNotBlank()) teachers += TeacherModel(
                old.getString("teacher_${i}_name", "").orEmpty(), phone,
                old.getString("teacher_${i}_nationalId", "").orEmpty(),
                old.getString("teacher_${i}_password", "").orEmpty(),
                old.getString("teacher_${i}_classIds", "").orEmpty(),
                old.getBoolean("teacher_${i}_isActive", true)
            )
        }
        for (i in 0 until old.getInt("class_count", 0)) {
            val id = old.getString("class_${i}_id", "").orEmpty()
            if (id.isNotBlank()) {
                val legacyTime = old.getString("class_${i}_time", "").orEmpty()
                classes += ClassModel(id, old.getString("class_${i}_name", "").orEmpty(), legacyTime, "", "نامشخص", 0)
            }
        }
        teachers.forEach { teacher ->
            teacher.classIds.split(',').filter { it.isNotBlank() }.forEach { classId ->
                getClassById(classId)?.teacherPhone = teacher.username
            }
        }
        students.filter { it.classId != null }.forEach {
            enrollments += EnrollmentModel(UUID.randomUUID().toString(), it.id, it.classId!!, today())
        }
    }

    private fun save() {
        if (!::appContext.isInitialized) return
        val root = JSONObject()
        root.put("admin", JSONObject().put("name", admin.name).put("phone", admin.phone).put("nationalId", admin.nationalId).put("password", admin.password))
        root.put("students", JSONArray().apply { students.forEach { s -> put(JSONObject().put("id", s.id).put("firstName", s.firstName).put("lastName", s.lastName).put("studentCode", s.studentCode).put("phone", s.phone).put("nationalId", s.nationalId).put("password", s.password).put("classId", s.classId).put("registrationDate", s.registrationDate).put("isActive", s.isActive).put("avatarResId", s.avatarResId)) } })
        root.put("teachers", JSONArray().apply { teachers.forEach { t -> put(JSONObject().put("name", t.name).put("username", t.username).put("nationalId", t.nationalId).put("password", t.password).put("classIds", t.classIds).put("isActive", t.isActive)) } })
        root.put("classes", JSONArray().apply { classes.forEach { c -> put(JSONObject().put("id", c.id).put("className", c.className).put("startTime", c.startTime).put("endTime", c.endTime).put("daysOfWeek", c.daysOfWeek).put("sessionCount", c.sessionCount).put("teacherPhone", c.teacherPhone).put("status", c.status.name).put("createdAt", c.createdAt).put("completedAt", c.completedAt)) } })
        root.put("enrollments", JSONArray().apply { enrollments.forEach { e -> put(JSONObject().put("id", e.id).put("studentId", e.studentId).put("classId", e.classId).put("startedAt", e.startedAt).put("endedAt", e.endedAt)) } })
        root.put("attendance", JSONArray().apply { attendanceSessions.forEach { a -> put(JSONObject().put("id", a.id).put("classId", a.classId).put("date", a.date).put("teacherPhone", a.teacherPhone).put("finalizedAt", a.finalizedAt).put("items", JSONArray().apply { a.items.forEach { item -> put(JSONObject().put("studentId", item.studentId).put("status", item.status.name).put("delayMinutes", item.delayMinutes)) } })) } })
        root.put("announcements", JSONArray().apply { announcements.forEach { a -> put(JSONObject().put("id", a.id).put("title", a.title).put("body", a.body).put("senderName", a.senderName).put("senderPhone", a.senderPhone).put("date", a.date).put("type", a.type.name).put("audienceType", a.audienceType.name).put("targetId", a.targetId).put("attachmentName", a.attachmentName).put("attachmentUrl", a.attachmentUrl)) } })
        root.put("reportCards", JSONArray().apply { reportCards.forEach { r -> put(JSONObject().put("id", r.id).put("classId", r.classId).put("studentId", r.studentId).put("publishedAt", r.publishedAt).put("criteria", JSONArray().apply { r.criteria.forEach { c -> put(JSONObject().put("id", c.id).put("name", c.name).put("maxScore", c.maxScore).put("isSelected", c.isSelected)) } }).put("scores", JSONObject(r.scores))) } })
        prefs().edit().putString(DATA_KEY, root.toString()).apply()
    }

    private fun load(root: JSONObject) {
        students.clear(); teachers.clear(); classes.clear(); enrollments.clear(); attendanceSessions.clear(); announcements.clear(); reportCards.clear()
        root.optJSONObject("admin")?.let { admin = AdminModel(it.optString("name"), it.optString("phone"), it.optString("nationalId"), it.optString("password")) }

        root.optJSONArray("students").forEachObject { o ->
            val legacyName = o.optString("name")
            val parts = legacyName.trim().split(Regex("\\s+"), limit = 2)
            students += StudentModel(
                id = o.optString("id"),
                firstName = o.optString("firstName", parts.firstOrNull().orEmpty()),
                lastName = o.optString("lastName", parts.getOrNull(1).orEmpty()),
                studentCode = o.optString("studentCode"),
                phone = o.optString("phone"),
                nationalId = o.optString("nationalId"),
                password = o.optString("password"),
                classId = o.optNullableString("classId"),
                registrationDate = o.optString("registrationDate", AppDatabase.today()),
                isActive = o.optBoolean("isActive", true),
                avatarResId = o.optInt("avatarResId", R.drawable.avatar_student_1)
            )
        }

        root.optJSONArray("teachers").forEachObject { o -> teachers += TeacherModel(o.optString("name"), o.optString("username"), o.optString("nationalId"), o.optString("password"), o.optString("classIds"), o.optBoolean("isActive", true)) }
        root.optJSONArray("classes").forEachObject { o -> classes += ClassModel(o.optString("id"), o.optString("className"), o.optString("startTime"), o.optString("endTime"), o.optString("daysOfWeek"), o.optInt("sessionCount"), o.optNullableString("teacherPhone"), runCatching { ClassStatus.valueOf(o.optString("status")) }.getOrDefault(ClassStatus.ACTIVE), o.optString("createdAt"), o.optNullableString("completedAt")) }
        root.optJSONArray("enrollments").forEachObject { o -> enrollments += EnrollmentModel(o.optString("id"), o.optString("studentId"), o.optString("classId"), o.optString("startedAt"), o.optNullableString("endedAt")) }
        root.optJSONArray("attendance").forEachObject { o ->
            val items = mutableListOf<AttendanceItem>()
            o.optJSONArray("items").forEachObject { item -> items += AttendanceItem(item.optString("studentId"), AttendanceStatus.valueOf(item.optString("status")), item.optInt("delayMinutes")) }
            attendanceSessions += AttendanceSession(o.optString("id"), o.optString("classId"), o.optString("date"), o.optString("teacherPhone"), items, o.optString("finalizedAt"))
        }
        root.optJSONArray("announcements").forEachObject { o -> announcements += Announcement(o.optString("id"), o.optString("title"), o.optString("body"), o.optString("senderName"), o.optString("senderPhone"), o.optString("date"), MessageType.valueOf(o.optString("type")), AudienceType.valueOf(o.optString("audienceType")), o.optNullableString("targetId"), o.optNullableString("attachmentName"), o.optNullableString("attachmentUrl")) }
        root.optJSONArray("reportCards").forEachObject { o ->
            val criteria = mutableListOf<GradeComponent>()
            o.optJSONArray("criteria").forEachObject { c -> criteria += GradeComponent(c.optString("id"), c.optString("name"), c.optInt("maxScore"), c.optBoolean("isSelected", true)) }
            val scoresObject = o.optJSONObject("scores") ?: JSONObject()
            val scores = scoresObject.keys().asSequence().associateWith { scoresObject.optInt(it) }
            reportCards += ReportCardModel(o.optString("id"), o.optString("classId"), o.optString("studentId"), criteria, scores, o.optString("publishedAt"))
        }
        syncTeacherClassIds()
    }

    private inline fun JSONArray?.forEachObject(block: (JSONObject) -> Unit) {
        if (this == null) return
        for (i in 0 until length()) optJSONObject(i)?.let(block)
    }

    private fun JSONObject.optNullableString(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() && it != "null" }
}