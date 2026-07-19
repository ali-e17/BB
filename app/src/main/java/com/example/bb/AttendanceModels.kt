package com.example.bb

enum class AttendanceMarkStatus {
    UNMARKED,
    PRESENT,
    LATE,
    ABSENT
}

data class AttendanceClassInfo(
    val id: String = "",
    val className: String = "",
    val sessionCount: Int = 0,
    val classStatus: String = "ACTIVE"
)

data class AttendanceSessionSummary(
    val id: String = "",
    val sessionNumber: Int = 0,
    val heldDate: String = "",
    val finalizedAt: String = "",
    val finalizedByName: String = "",
    val updatedAt: String? = null,
    val presentCount: Int = 0,
    val lateCount: Int = 0,
    val absentCount: Int = 0,
    val revision: Int = 1
)

data class AttendanceOverviewResponse(
    val status: String = "",
    val message: String? = null,
    val classInfo: AttendanceClassInfo? = null,
    val nextSessionNumber: Int? = null,
    val isComplete: Boolean = false,
    val sessions: List<AttendanceSessionSummary> = emptyList()
)

data class AttendanceStudentResponse(
    val studentId: String = "",
    val name: String = "",
    val studentCode: String = "",
    val avatarName: String? = null,
    val status: String = "UNMARKED",
    val delayMinutes: Int = 0
)

data class AttendanceSessionResponse(
    val status: String = "",
    val message: String? = null,
    val sessionId: String? = null,
    val classId: String = "",
    val className: String = "",
    val sessionNumber: Int = 0,
    val sessionCount: Int = 0,
    val heldDate: String = "",
    val isFinalized: Boolean = false,
    val canEdit: Boolean = false,
    val finalizedAt: String? = null,
    val finalizedByName: String? = null,
    val revision: Int = 0,
    val students: List<AttendanceStudentResponse> = emptyList()
)

data class AttendanceSaveItemRequest(
    val studentId: String,
    val status: String,
    val delayMinutes: Int
)

data class FinalizeAttendanceRequest(
    val classId: String,
    val sessionNumber: Int,
    val heldDate: String,
    val items: List<AttendanceSaveItemRequest>
)

data class UpdateAttendanceRequest(
    val sessionId: String,
    val heldDate: String,
    val editReason: String,
    val items: List<AttendanceSaveItemRequest>
)

data class AttendanceSaveResponse(
    val status: String = "",
    val message: String = "",
    val sessionId: String? = null,
    val revision: Int = 0,
    val createdAnnouncements: Int = 0
)

data class AttendanceRecord(
    val studentId: String,
    val studentName: String,
    val studentCode: String,
    val avatarName: String?,
    var status: AttendanceMarkStatus = AttendanceMarkStatus.UNMARKED,
    var delayMinutes: Int = 0,
    var isLocked: Boolean = false
)
