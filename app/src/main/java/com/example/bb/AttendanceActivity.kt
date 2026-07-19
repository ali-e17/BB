package com.example.bb

import android.app.DatePickerDialog
import android.app.DownloadManager
import android.content.Context
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AttendanceActivity : AppCompatActivity() {

    private lateinit var role: UserRole
    private var username: String = ""

    private val availableClasses = mutableListOf<ClassModel>()
    private var selectedClass: ClassModel? = null
    private var overview: AttendanceOverviewResponse? = null
    private var currentSession: AttendanceSessionResponse? = null
    private var selectedSessionNumber: Int = 0
    private var selectedHeldDate: String = todayIso()
    private var records = mutableListOf<AttendanceRecord>()

    private lateinit var spinnerClass: MaterialAutoCompleteTextView
    private lateinit var containerSessions: LinearLayout
    private lateinit var txtSessionTitle: TextView
    private lateinit var txtSessionMeta: TextView
    private lateinit var btnAttendanceDate: MaterialButton
    private lateinit var txtLiveStats: TextView
    private lateinit var txtMarkingHint: TextView
    private lateinit var recycler: RecyclerView
    private lateinit var btnExportAttendance: MaterialButton
    private lateinit var saveButton: MaterialButton
    private lateinit var progress: View
    private lateinit var emptyState: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_attendance)

        val prefs = getSharedPreferences("LocalAppPrefs", Context.MODE_PRIVATE)
        role = runCatching {
            UserRole.valueOf(prefs.getString("CURRENT_USER_ROLE", "TEACHER").orEmpty())
        }.getOrDefault(UserRole.TEACHER)
        username = prefs.getString("CURRENT_USERNAME", "").orEmpty()

        if (role == UserRole.STUDENT) {
            Toast.makeText(this, "این بخش مخصوص استاد و مدیر است", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        bindViews()
        setupRecycler()
        setupActions()
        fetchClasses()
    }

    private fun bindViews() {
        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }
        spinnerClass = findViewById(R.id.spinnerClass)
        containerSessions = findViewById(R.id.containerSessions)
        txtSessionTitle = findViewById(R.id.txtSessionTitle)
        txtSessionMeta = findViewById(R.id.txtSessionMeta)
        btnAttendanceDate = findViewById(R.id.btnAttendanceDate)
        txtLiveStats = findViewById(R.id.txtLiveStats)
        txtMarkingHint = findViewById(R.id.txtMarkingHint)
        recycler = findViewById(R.id.rvAttendance)
        btnExportAttendance = findViewById(R.id.btnExportAttendance)
        saveButton = findViewById(R.id.btnSaveAttendance)
        btnExportAttendance.visibility = if (role == UserRole.ADMIN) View.VISIBLE else View.GONE
        progress = findViewById(R.id.progressAttendance)
        emptyState = findViewById(R.id.attendanceEmptyState)
    }

    private fun setupRecycler() {
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.setHasFixedSize(false)
    }

    private fun setupActions() {
        spinnerClass.setOnClickListener { spinnerClass.showDropDown() }
        btnAttendanceDate.setOnClickListener {
            val session = currentSession ?: return@setOnClickListener
            if (session.isFinalized && session.canEdit) {
                showDatePicker()
            }
        }
        btnExportAttendance.setOnClickListener { confirmAttendanceExport() }
        saveButton.setOnClickListener { handleSaveClick() }
    }

    private fun fetchClasses() {
        setLoading(true)
        RetrofitClient.instance.getClasses().enqueue(object : Callback<List<ClassModel>> {
            override fun onResponse(
                call: Call<List<ClassModel>>,
                response: Response<List<ClassModel>>
            ) {
                setLoading(false)
                if (response.isSuccessful) {
                    val classes = response.body().orEmpty()
                    AppDatabase.replaceClasses(classes)
                    applyClasses(classes)
                } else {
                    applyLocalClasses("دریافت کلاس‌ها از سرور انجام نشد")
                }
            }

            override fun onFailure(call: Call<List<ClassModel>>, t: Throwable) {
                setLoading(false)
                applyLocalClasses("اتصال به سرور برقرار نشد؛ فهرست ذخیره‌شده نمایش داده شد")
            }
        })
    }

    private fun applyLocalClasses(message: String) {
        applyClasses(AppDatabase.getAllClasses(includeCompleted = role == UserRole.ADMIN))
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun applyClasses(source: List<ClassModel>) {
        availableClasses.clear()
        availableClasses.addAll(
            source.asSequence()
                .filter { role == UserRole.ADMIN || it.status == ClassStatus.ACTIVE }
                .filter {
                    role == UserRole.ADMIN || normalizePhone(it.teacherPhone.orEmpty()) == normalizePhone(username)
                }
                .distinctBy { it.id }
                .sortedWith(compareBy<ClassModel> { it.className.lowercase() }.thenBy { it.startTime })
                .toList()
        )

        if (availableClasses.isEmpty()) {
            showEmpty(
                if (role == UserRole.ADMIN) {
                    "هیچ کلاسی برای مدیریت حضور و غیاب وجود ندارد"
                } else {
                    "هیچ کلاس فعالی به شما تخصیص داده نشده است"
                }
            )
            return
        }

        val classNames = availableClasses.map {
            val statusLabel = if (it.status == ClassStatus.COMPLETED) "  •  پایان‌یافته" else ""
            "${it.className}  •  ${it.sessionCount} جلسه$statusLabel"
        }
        spinnerClass.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, classNames)
        )
        spinnerClass.setOnItemClickListener { _, _, position, _ ->
            selectClass(availableClasses[position])
        }

        spinnerClass.setText(classNames.first(), false)
        selectClass(availableClasses.first())
    }

    private fun selectClass(model: ClassModel) {
        selectedClass = model
        btnExportAttendance.isEnabled = role == UserRole.ADMIN
        selectedSessionNumber = 0
        currentSession = null
        records.clear()
        recycler.adapter = null
        hideEmpty()
        loadOverview()
    }

    private fun loadOverview(preferredSessionNumber: Int? = null) {
        val model = selectedClass ?: return
        setLoading(true)
        RetrofitClient.instance.getAttendanceOverview(model.id)
            .enqueue(object : Callback<AttendanceOverviewResponse> {
                override fun onResponse(
                    call: Call<AttendanceOverviewResponse>,
                    response: Response<AttendanceOverviewResponse>
                ) {
                    setLoading(false)
                    val body = response.body()
                    if (!response.isSuccessful || body?.status != "success") {
                        showEmpty(errorMessage(response, body?.message ?: "دریافت جلسات انجام نشد"))
                        return
                    }

                    overview = body
                    renderSessionButtons()

                    val finalizedNumbers = body.sessions.map { it.sessionNumber }.toSet()
                    val classIsActive = body.classInfo?.classStatus.equals("ACTIVE", ignoreCase = true)
                    val target = when {
                        preferredSessionNumber != null && preferredSessionNumber in finalizedNumbers -> {
                            preferredSessionNumber
                        }
                        preferredSessionNumber != null && classIsActive &&
                            preferredSessionNumber == body.nextSessionNumber -> {
                            preferredSessionNumber
                        }
                        classIsActive && body.nextSessionNumber != null -> body.nextSessionNumber
                        body.sessions.isNotEmpty() -> body.sessions.maxOf { it.sessionNumber }
                        else -> null
                    }

                    if (target == null) {
                        showEmpty("برای این کلاس هنوز جلسه‌ای قابل نمایش نیست")
                    } else {
                        loadSession(target)
                    }
                }

                override fun onFailure(call: Call<AttendanceOverviewResponse>, t: Throwable) {
                    setLoading(false)
                    showEmpty("دریافت اطلاعات حضور و غیاب ناموفق بود")
                }
            })
    }

    private fun renderSessionButtons() {
        val data = overview ?: return
        val classInfo = data.classInfo ?: return
        val finalizedByNumber = data.sessions.associateBy { it.sessionNumber }

        containerSessions.removeAllViews()
        for (number in 1..classInfo.sessionCount) {
            val finalized = finalizedByNumber[number]
            val classIsActive = classInfo.classStatus.equals("ACTIVE", ignoreCase = true)
            val isNext = classIsActive && number == data.nextSessionNumber
            val isSelected = number == selectedSessionNumber
            val enabled = finalized != null || isNext

            val button = MaterialButton(this).apply {
                text = when {
                    finalized != null -> "✓ جلسه $number"
                    isNext -> "جلسه $number"
                    else -> "جلسه $number"
                }
                textSize = 12f
                isAllCaps = false
                gravity = Gravity.CENTER
                minWidth = 0
                minimumWidth = 0
                insetTop = 0
                insetBottom = 0
                cornerRadius = dp(12)
                isEnabled = enabled
                alpha = if (enabled) 1f else 0.45f
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dp(42)
                ).apply {
                    setMargins(dp(5), 0, dp(5), 0)
                }
                setPadding(dp(15), 0, dp(15), 0)

                val selectedColor = ContextCompat.getColor(this@AttendanceActivity, R.color.brand_orange)
                val completedColor = ColorStateList.valueOf(android.graphics.Color.parseColor("#E8F7F0"))
                val nextColor = ColorStateList.valueOf(android.graphics.Color.parseColor("#FFF4E8"))
                val futureColor = ColorStateList.valueOf(android.graphics.Color.parseColor("#F3F4F6"))

                backgroundTintList = when {
                    isSelected -> ColorStateList.valueOf(selectedColor)
                    finalized != null -> completedColor
                    isNext -> nextColor
                    else -> futureColor
                }
                setTextColor(
                    when {
                        isSelected -> android.graphics.Color.WHITE
                        finalized != null -> android.graphics.Color.parseColor("#047857")
                        isNext -> android.graphics.Color.parseColor("#B45309")
                        else -> ContextCompat.getColor(this@AttendanceActivity, R.color.sub_text)
                    }
                )
                strokeWidth = if (isSelected) 0 else dp(1)
                strokeColor = ColorStateList.valueOf(
                    when {
                        finalized != null -> android.graphics.Color.parseColor("#A7F3D0")
                        isNext -> android.graphics.Color.parseColor("#FED7AA")
                        else -> android.graphics.Color.parseColor("#E5E7EB")
                    }
                )

                if (enabled) {
                    setOnClickListener { loadSession(number) }
                }
            }
            containerSessions.addView(button)
        }
    }

    private fun loadSession(sessionNumber: Int) {
        val model = selectedClass ?: return
        selectedSessionNumber = sessionNumber
        currentSession = null
        records.clear()
        recycler.adapter = null
        renderSessionButtons()
        setLoading(true)

        RetrofitClient.instance.getAttendanceSession(model.id, sessionNumber)
            .enqueue(object : Callback<AttendanceSessionResponse> {
                override fun onResponse(
                    call: Call<AttendanceSessionResponse>,
                    response: Response<AttendanceSessionResponse>
                ) {
                    setLoading(false)
                    val body = response.body()
                    if (!response.isSuccessful || body?.status != "success") {
                        showEmpty(errorMessage(response, body?.message ?: "دریافت جلسه انجام نشد"))
                        return
                    }
                    currentSession = body
                    selectedHeldDate = if (body.isFinalized) {
                        body.heldDate.ifBlank { todayIso() }
                    } else {
                        todayIso()
                    }
                    bindSession(body)
                }

                override fun onFailure(call: Call<AttendanceSessionResponse>, t: Throwable) {
                    setLoading(false)
                    showEmpty("اتصال برای دریافت جلسه برقرار نشد")
                }
            })
    }

    private fun bindSession(session: AttendanceSessionResponse) {
        hideEmpty()
        txtSessionTitle.text = "جلسه ${session.sessionNumber} از ${session.sessionCount}"
        txtSessionMeta.text = if (session.isFinalized) {
            buildString {
                append("ثبت نهایی")
                session.finalizedByName?.takeIf { it.isNotBlank() }?.let { append(" توسط $it") }
                session.finalizedAt?.takeIf { it.isNotBlank() }?.let { append(" • ${displayDateTime(it)}") }
                if (session.revision > 1) append(" • ویرایش ${session.revision - 1} بار")
            }
        } else {
            "جلسه بعدی کلاس؛ تاریخ هنگام ثبت نهایی ذخیره می‌شود"
        }

        if (session.isFinalized) {
            btnAttendanceDate.visibility = View.VISIBLE
            btnAttendanceDate.text = "تاریخ برگزاری: ${displayDate(selectedHeldDate)}"
            btnAttendanceDate.isEnabled = session.canEdit
            btnAttendanceDate.alpha = if (session.canEdit) 1f else 0.72f
        } else {
            btnAttendanceDate.visibility = View.GONE
        }

        val locked = session.isFinalized && !session.canEdit
        records = session.students.map { student ->
            AttendanceRecord(
                studentId = student.studentId,
                studentName = student.name,
                studentCode = student.studentCode,
                avatarName = student.avatarName,
                status = runCatching {
                    AttendanceMarkStatus.valueOf(student.status.uppercase())
                }.getOrDefault(AttendanceMarkStatus.UNMARKED),
                delayMinutes = student.delayMinutes,
                isLocked = locked
            )
        }.toMutableList()

        recycler.adapter = AttendanceAdapter(records) { updateStatsAndSaveState() }
        recycler.visibility = if (records.isEmpty()) View.GONE else View.VISIBLE

        if (records.isEmpty()) {
            showEmpty("دانش‌آموز فعالی برای این جلسه وجود ندارد")
        }

        updateStatsAndSaveState()
    }

    private fun updateStatsAndSaveState() {
        val unmarked = records.count { it.status == AttendanceMarkStatus.UNMARKED }
        val present = records.count { it.status == AttendanceMarkStatus.PRESENT }
        val late = records.count { it.status == AttendanceMarkStatus.LATE }
        val absent = records.count { it.status == AttendanceMarkStatus.ABSENT }

        txtLiveStats.text = "بررسی‌نشده: $unmarked   |   حاضر: $present   |   تأخیر: $late   |   غایب: $absent"

        val session = currentSession
        if (session == null) {
            saveButton.isEnabled = false
            txtMarkingHint.text = "یک جلسه را انتخاب کنید"
            return
        }

        val allMarked = records.isNotEmpty() && unmarked == 0
        when {
            session.isFinalized && !session.canEdit -> {
                saveButton.text = "ثبت نهایی شده؛ فقط مدیر امکان ویرایش دارد"
                saveButton.isEnabled = false
                txtMarkingHint.text = "این جلسه قفل شده و برای استاد فقط قابل مشاهده است"
            }

            session.isFinalized && session.canEdit -> {
                saveButton.text = "ذخیره اصلاحات مدیر"
                saveButton.isEnabled = allMarked
                txtMarkingHint.text = if (allMarked) {
                    "اصلاحات با ثبت سابقه مدیریتی ذخیره می‌شوند"
                } else {
                    "$unmarked دانش‌آموز هنوز بررسی نشده است"
                }
            }

            else -> {
                saveButton.text = "ثبت نهایی جلسه ${session.sessionNumber}"
                saveButton.isEnabled = allMarked
                txtMarkingHint.text = if (allMarked) {
                    "همه دانش‌آموزان بررسی شدند؛ جلسه آماده ثبت نهایی است"
                } else {
                    "$unmarked دانش‌آموز هنوز بررسی نشده است"
                }
            }
        }
    }

    private fun handleSaveClick() {
        val session = currentSession ?: return
        val unmarked = records.count { it.status == AttendanceMarkStatus.UNMARKED }
        if (records.isEmpty() || unmarked > 0) {
            Toast.makeText(this, "ابتدا وضعیت همه دانش‌آموزان را مشخص کنید", Toast.LENGTH_SHORT).show()
            return
        }

        if (session.isFinalized) {
            if (session.canEdit && role == UserRole.ADMIN) {
                showAdminEditDialog(session)
            }
        } else {
            showFinalizeDatePicker(session)
        }
    }



    private fun confirmAttendanceExport() {
        if (role != UserRole.ADMIN) return
        val model = selectedClass ?: run {
            Toast.makeText(this, "ابتدا یک کلاس را انتخاب کنید", Toast.LENGTH_SHORT).show()
            return
        }
        if (overview?.sessions.isNullOrEmpty()) {
            Toast.makeText(this, "برای این کلاس هنوز جلسه ثبت‌شده‌ای وجود ندارد", Toast.LENGTH_LONG).show()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("دریافت خروجی اکسل")
            .setMessage(
                "گزارش کامل کلاس «${model.className}» شامل وضعیت تمام دانش‌آموزان در کل جلسات، " +
                    "جمع غیبت و تأخیر و خلاصه هر جلسه ساخته می‌شود."
            )
            .setNegativeButton("انصراف", null)
            .setPositiveButton("دانلود اکسل") { _, _ -> downloadAttendanceExcel(model) }
            .show()
    }

    private fun downloadAttendanceExcel(model: ClassModel) {
        val prefs = getSharedPreferences("LocalAppPrefs", Context.MODE_PRIVATE)
        val token = prefs.getString("API_TOKEN", "").orEmpty()
        if (token.isBlank()) {
            Toast.makeText(this, "نشست ورود معتبر نیست؛ یک‌بار خارج و دوباره وارد شوید", Toast.LENGTH_LONG).show()
            return
        }

        val safeClassName = model.className
            .replace(Regex("[^A-Za-z0-9\u0600-\u06FF_-]+"), "_")
            .trim('_')
            .take(60)
            .ifBlank { "class" }
        val timePart = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US)
            .format(Calendar.getInstance().time)
        val fileName = "attendance_${safeClassName}_$timePart.xlsx"

        val request = DownloadManager.Request(
            Uri.parse(RetrofitClient.attendanceExportUrl(model.id))
        ).apply {
            addRequestHeader("Authorization", "Bearer $token")
            setMimeType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            setTitle("خروجی حضور و غیاب ${model.className}")
            setDescription("در حال ساخت و دانلود گزارش اکسل")
            setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        }

        val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(request)
        Toast.makeText(
            this,
            "دانلود گزارش شروع شد؛ فایل در پوشه Downloads ذخیره می‌شود",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun showFinalizeDatePicker(session: AttendanceSessionResponse) {
        selectedHeldDate = todayIso()
        showDatePicker {
            showFinalizeConfirmation(session)
        }
    }

    private fun showFinalizeConfirmation(session: AttendanceSessionResponse) {
        val summary = attendanceSummary()
        MaterialAlertDialogBuilder(this)
            .setTitle("ثبت نهایی جلسه ${session.sessionNumber}")
            .setMessage(
                "تاریخ برگزاری: ${displayDate(selectedHeldDate)}\n\n" +
                    "$summary\n\n" +
                    "پس از ثبت، استاد امکان ویرایش ندارد و اعلان غیبت یا تأخیر به‌صورت خودکار برای دانش‌آموز مربوطه ساخته می‌شود."
            )
            .setNegativeButton("بازبینی", null)
            .setPositiveButton("ثبت نهایی") { _, _ -> submitFinalize(session) }
            .show()
    }

    private fun showAdminEditDialog(session: AttendanceSessionResponse) {
        val reasonInput = EditText(this).apply {
            hint = "علت اصلاح (اختیاری)"
            textDirection = View.TEXT_DIRECTION_RTL
            gravity = Gravity.RIGHT
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("ذخیره اصلاحات جلسه ${session.sessionNumber}")
            .setMessage(
                "تاریخ برگزاری: ${displayDate(selectedHeldDate)}\n\n" +
                    "${attendanceSummary()}\n\n" +
                    "این تغییر در سابقه مدیریتی ثبت می‌شود و اعلان اصلاح‌شده برای افراد مرتبط دوباره خوانده‌نشده خواهد شد."
            )
            .setView(reasonInput)
            .setNegativeButton("انصراف", null)
            .setPositiveButton("ذخیره اصلاحات") { _, _ ->
                submitAdminUpdate(
                    session,
                    reasonInput.text?.toString()?.trim().orEmpty().ifBlank { "اصلاح توسط مدیر" }
                )
            }
            .show()
    }

    private fun submitFinalize(session: AttendanceSessionResponse) {
        val model = selectedClass ?: return
        setSaving(true)
        RetrofitClient.instance.finalizeAttendance(
            FinalizeAttendanceRequest(
                classId = model.id,
                sessionNumber = session.sessionNumber,
                heldDate = selectedHeldDate,
                items = buildSaveItems()
            )
        ).enqueue(object : Callback<AttendanceSaveResponse> {
            override fun onResponse(
                call: Call<AttendanceSaveResponse>,
                response: Response<AttendanceSaveResponse>
            ) {
                setSaving(false)
                val body = response.body()
                if (response.isSuccessful && body?.status == "success") {
                    Toast.makeText(
                        this@AttendanceActivity,
                        "جلسه ثبت نهایی شد و اعلان‌ها ساخته شدند",
                        Toast.LENGTH_LONG
                    ).show()
                    loadOverview(preferredSessionNumber = session.sessionNumber)
                } else {
                    Toast.makeText(
                        this@AttendanceActivity,
                        errorMessage(response, body?.message ?: "ثبت نهایی انجام نشد"),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            override fun onFailure(call: Call<AttendanceSaveResponse>, t: Throwable) {
                setSaving(false)
                Toast.makeText(
                    this@AttendanceActivity,
                    "اتصال هنگام ثبت نهایی برقرار نشد؛ دوباره تلاش کنید",
                    Toast.LENGTH_LONG
                ).show()
            }
        })
    }

    private fun submitAdminUpdate(session: AttendanceSessionResponse, reason: String) {
        val sessionId = session.sessionId ?: return
        setSaving(true)
        RetrofitClient.instance.updateAttendance(
            UpdateAttendanceRequest(
                sessionId = sessionId,
                heldDate = selectedHeldDate,
                editReason = reason,
                items = buildSaveItems()
            )
        ).enqueue(object : Callback<AttendanceSaveResponse> {
            override fun onResponse(
                call: Call<AttendanceSaveResponse>,
                response: Response<AttendanceSaveResponse>
            ) {
                setSaving(false)
                val body = response.body()
                if (response.isSuccessful && body?.status == "success") {
                    Toast.makeText(
                        this@AttendanceActivity,
                        "اصلاحات مدیر ذخیره شد",
                        Toast.LENGTH_LONG
                    ).show()
                    loadOverview(preferredSessionNumber = session.sessionNumber)
                } else {
                    Toast.makeText(
                        this@AttendanceActivity,
                        errorMessage(response, body?.message ?: "ذخیره اصلاحات انجام نشد"),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            override fun onFailure(call: Call<AttendanceSaveResponse>, t: Throwable) {
                setSaving(false)
                Toast.makeText(
                    this@AttendanceActivity,
                    "اتصال هنگام ذخیره اصلاحات برقرار نشد",
                    Toast.LENGTH_LONG
                ).show()
            }
        })
    }

    private fun buildSaveItems(): List<AttendanceSaveItemRequest> = records.map { record ->
        AttendanceSaveItemRequest(
            studentId = record.studentId,
            status = record.status.name,
            delayMinutes = if (record.status == AttendanceMarkStatus.LATE) {
                record.delayMinutes
            } else {
                0
            }
        )
    }

    private fun attendanceSummary(): String {
        val present = records.count { it.status == AttendanceMarkStatus.PRESENT }
        val late = records.count { it.status == AttendanceMarkStatus.LATE }
        val absent = records.count { it.status == AttendanceMarkStatus.ABSENT }
        return "حاضر: $present   |   تأخیر: $late   |   غایب: $absent"
    }

    private fun showDatePicker(onDateSelected: (() -> Unit)? = null) {
        val calendar = parseIsoDate(selectedHeldDate) ?: Calendar.getInstance()
        val dialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val selected = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                }
                if (selected.after(Calendar.getInstance())) {
                    Toast.makeText(this, "تاریخ آینده قابل ثبت نیست", Toast.LENGTH_SHORT).show()
                } else {
                    selectedHeldDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(selected.time)
                    btnAttendanceDate.text = "تاریخ برگزاری: ${displayDate(selectedHeldDate)}"
                    onDateSelected?.invoke()
                }
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        dialog.datePicker.maxDate = System.currentTimeMillis()
        dialog.show()
    }

    private fun setLoading(loading: Boolean) {
        progress.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun setSaving(saving: Boolean) {
        saveButton.isEnabled = !saving
        saveButton.text = if (saving) "در حال ذخیره..." else saveButton.text
        progress.visibility = if (saving) View.VISIBLE else View.GONE
        if (!saving) updateStatsAndSaveState()
    }

    private fun showEmpty(message: String) {
        emptyState.text = message
        emptyState.visibility = View.VISIBLE
        if (records.isEmpty()) recycler.visibility = View.GONE
    }

    private fun hideEmpty() {
        emptyState.visibility = View.GONE
        recycler.visibility = View.VISIBLE
    }

    private fun errorMessage(response: Response<*>, fallback: String): String {
        val raw = runCatching { response.errorBody()?.string() }.getOrNull().orEmpty()
        if (raw.isBlank()) return fallback
        return runCatching { JSONObject(raw).optString("message", fallback) }
            .getOrDefault(fallback)
    }

    private fun normalizePhone(value: String): String {
        var normalized = value.trim().replace(" ", "")
        if (normalized.startsWith("+98")) normalized = normalized.removePrefix("+98")
        if (normalized.startsWith("0098")) normalized = normalized.removePrefix("0098")
        if (normalized.length == 11 && normalized.startsWith("0")) {
            normalized = normalized.drop(1)
        }
        return normalized
    }

    private fun displayDate(value: String): String = value.replace('-', '/')

    private fun displayDateTime(value: String): String = value.replace('-', '/')

    private fun parseIsoDate(value: String): Calendar? = runCatching {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(value)
            ?: return@runCatching null
        Calendar.getInstance().apply { time = date }
    }.getOrNull()

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        private fun todayIso(): String =
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().time)
    }
}
