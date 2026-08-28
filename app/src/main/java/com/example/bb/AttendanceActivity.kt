package com.example.bb


import androidx.activity.result.contract.ActivityResultContracts
import java.io.IOException
import okhttp3.ResponseBody
import android.content.Context
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AttendanceActivity : BaseActivity() {

    private lateinit var role: UserRole
    private var currentUserId: String = ""

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
    private lateinit var txtSessionCounter: TextView
    private lateinit var txtSessionMeta: TextView
    private lateinit var btnAttendanceDate: MaterialButton
    private lateinit var txtLiveStats: TextView
    private lateinit var txtMarkingHint: TextView
    private lateinit var recycler: RecyclerView
    private lateinit var btnExportAttendance: MaterialButton
    private lateinit var saveButton: MaterialButton
    private lateinit var progress: View
    private lateinit var emptyState: TextView

    private var pendingAttendanceExportClass: ClassModel? = null

    private val createAttendanceExcelLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        )
    ) { uri: Uri? ->

        val model = pendingAttendanceExportClass
        pendingAttendanceExportClass = null

        if (uri != null && model != null) {
            downloadAttendanceExcel(model, uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_attendance)

        val prefs = getSharedPreferences("LocalAppPrefs", Context.MODE_PRIVATE)
        role = runCatching {
            UserRole.valueOf(prefs.getString("CURRENT_USER_ROLE", "TEACHER").orEmpty())
        }.getOrDefault(UserRole.TEACHER)
        currentUserId = prefs.getString("CURRENT_USER_ID", "").orEmpty()

        if (role == UserRole.STUDENT) {
            AppToast.makeText(this, "دسترسی به این بخش فقط برای مدیر و استاد امکان‌پذیر است", Toast.LENGTH_SHORT).show()
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
        txtSessionCounter = findViewById(R.id.txtSessionCounter)
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
            val session = currentSession
            if (session == null) {
                AppToast.info(this, "لطفاً ابتدا یک جلسه را انتخاب کنید")
                return@setOnClickListener
            }

            when {
                session.isFinalized && session.canEdit -> showDatePicker()
                !session.isFinalized -> AppToast.info(
                    this,
                    "تاریخ جلسه هنگام ثبت نهایی انتخاب می‌شود"
                )
                else -> AppToast.warning(
                    this,
                    "تاریخ این جلسه فقط توسط مدیر قابل ویرایش است"
                )
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
                    applyLocalClasses(
                        ApiErrorParser.userMessage(
                            response,
                            "دریافت کلاس‌های حضور و غیاب کامل نشد"
                        ) + "؛ فهرست ذخیره‌شده دستگاه نمایش داده شد"
                    )
                }
            }

            override fun onFailure(call: Call<List<ClassModel>>, t: Throwable) {
                setLoading(false)
                applyLocalClasses(
                    ApiErrorParser.networkMessage(t, "دریافت کلاس‌های حضور و غیاب") +
                        " فهرست ذخیره‌شده دستگاه نمایش داده شد."
                )
            }
        })
    }

    private fun applyLocalClasses(message: String) {
        applyClasses(AppDatabase.getAllClasses(includeCompleted = role == UserRole.ADMIN))
        AppToast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun applyClasses(source: List<ClassModel>) {
        availableClasses.clear()
        availableClasses.addAll(
            source.asSequence()
                .filter { role == UserRole.ADMIN || it.status == ClassStatus.ACTIVE }
                .filter {
                    role == UserRole.ADMIN || it.teacherId == currentUserId
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

        // فرمت‌دهی جدید ترم کلاس
        val classNames = availableClasses.map {
            val termInfo = listOf(it.termSeason, it.termYear).filter { s -> s.isNotBlank() }.joinToString(" - ")
            if (termInfo.isNotBlank()) "${it.className} - $termInfo" else it.className
        }

        // تنظیمات استایل آبی آسمانی و فونت جمع‌وجور
        spinnerClass.setTextColor(android.graphics.Color.parseColor("#9ad9f5"))
        spinnerClass.textSize = 14f

        spinnerClass.setAdapter(createSkyBlueAdapter(this, classNames))
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

    private fun createSkyBlueAdapter(context: Context, items: List<String>): ArrayAdapter<String> {
        return object : ArrayAdapter<String>(context, android.R.layout.simple_dropdown_item_1line, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                view.setTextColor(android.graphics.Color.parseColor("#9ad9f5"))
                view.textSize = 14f
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent) as TextView
                view.setTextColor(android.graphics.Color.parseColor("#0ea5e9"))
                view.textSize = 14f
                return view
            }
        }
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
                        val message = errorMessage(response, body?.message ?: "دریافت جلسات حضور و غیاب کامل نشد")
                        showEmpty(message)
                        AppToast.error(this@AttendanceActivity, message)
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
                        showEmpty("برای این کلاس جلسه‌ای برای نمایش وجود ندارد")
                    } else {
                        loadSession(target)
                    }
                }

                override fun onFailure(call: Call<AttendanceOverviewResponse>, t: Throwable) {
                    setLoading(false)
                    val message = ApiErrorParser.networkMessage(t, "دریافت اطلاعات حضور و غیاب")
                    showEmpty(message)
                    AppToast.error(this@AttendanceActivity, message)
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
                        val message = errorMessage(response, body?.message ?: "دریافت اطلاعات جلسه حضور و غیاب کامل نشد")
                        showEmpty(message)
                        AppToast.error(this@AttendanceActivity, message)
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
                    val message = ApiErrorParser.networkMessage(t, "دریافت اطلاعات جلسه حضور و غیاب")
                    showEmpty(message)
                    AppToast.error(this@AttendanceActivity, message)
                }
            })
    }

    private fun bindSession(session: AttendanceSessionResponse) {
        hideEmpty()
        txtSessionTitle.text = if (session.isFinalized) {
            "جزئیات حضور و غیاب"
        } else {
            "ثبت حضور و غیاب"
        }

        txtSessionCounter.text =
            "جلسه ${session.sessionNumber} از ${session.sessionCount}"

        if (session.isFinalized) {
            txtSessionMeta.visibility = View.VISIBLE
            txtSessionMeta.text = buildString {
                append("ثبت نهایی")
                session.finalizedByName?.takeIf { it.isNotBlank() }?.let { append(" توسط $it") }
                session.finalizedAt?.takeIf { it.isNotBlank() }?.let { append(" • ${displayDateTime(it)}") }
                if (session.revision > 1) append(" • ویرایش ${session.revision - 1} بار")
            }
        } else {
            // متن قدیمی «جلسه بعدی کلاس؛ تاریخ هنگام ثبت نهایی ذخیره می‌شود»
            // حذف شده تا کارت جلسه تمیزتر و مستقیم‌تر باشد.
            txtSessionMeta.text = ""
            txtSessionMeta.visibility = View.GONE
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
            saveButton.text = "ثبت حضور و غیاب"
            saveButton.isEnabled = true
            saveButton.alpha = 0.72f
            txtMarkingHint.text = "یک جلسه را انتخاب کنید"
            return
        }

        val allMarked = records.isNotEmpty() && unmarked == 0
        when {
            session.isFinalized && !session.canEdit -> {
                saveButton.text = "ثبت نهایی شده؛ فقط مدیر امکان ویرایش دارد"
                saveButton.isEnabled = true
                saveButton.alpha = 0.72f
                txtMarkingHint.text = "این جلسه قفل شده و برای استاد فقط قابل مشاهده است"
            }

            session.isFinalized && session.canEdit -> {
                saveButton.text = "ذخیره اصلاحات مدیر"
                saveButton.isEnabled = true
                saveButton.alpha = if (allMarked) 1f else 0.72f
                txtMarkingHint.text = if (allMarked) {
                    "اصلاحات با ثبت سابقه مدیریتی ذخیره می‌شوند"
                } else {
                    "وضعیت $unmarked دانش‌آموز هنوز بررسی نشده است"
                }
            }

            else -> {
                saveButton.text = "ثبت نهایی جلسه ${session.sessionNumber}"
                saveButton.isEnabled = true
                saveButton.alpha = if (allMarked) 1f else 0.72f
                txtMarkingHint.text = if (allMarked) {
                    "همه دانش‌آموزان بررسی شدند؛ جلسه آماده ثبت نهایی است"
                } else {
                    "وضعیت $unmarked دانش‌آموز هنوز بررسی نشده است"
                }
            }
        }
    }

    private fun handleSaveClick() {
        val session = currentSession
        if (session == null) {
            AppToast.info(
                this,
                if (selectedClass == null) {
                    "لطفاً ابتدا یک کلاس را انتخاب کنید"
                } else {
                    "لطفاً ابتدا جلسه موردنظر را انتخاب کنید"
                }
            )
            return
        }

        if (records.isEmpty()) {
            AppToast.warning(this, "دانش‌آموز فعالی برای ثبت حضور و غیاب در این جلسه وجود ندارد")
            return
        }

        val unmarked = records.count { it.status == AttendanceMarkStatus.UNMARKED }
        if (unmarked > 0) {
            AppToast.warning(
                this,
                "وضعیت $unmarked دانش‌آموز مشخص نشده است؛ لطفاً وضعیت همه دانش‌آموزان را تعیین کنید"
            )
            return
        }

        if (session.isFinalized) {
            if (session.canEdit && role == UserRole.ADMIN) {
                showAdminEditDialog(session)
            } else {
                AppToast.warning(this, "این جلسه ثبت نهایی شده است و فقط مدیر امکان ویرایش آن را دارد")
            }
        } else {
            showFinalizeDatePicker(session)
        }
    }



    private fun confirmAttendanceExport() {

        if (role != UserRole.ADMIN) return

        val model = selectedClass ?: run {

            AppToast.makeText(
                this,
                "لطفاً ابتدا یک کلاس را انتخاب کنید",
                Toast.LENGTH_SHORT
            ).show()

            return
        }


        if (overview?.sessions.isNullOrEmpty()) {

            AppToast.makeText(
                this,
                "برای این کلاس جلسه ثبت‌شده‌ای وجود ندارد",
                Toast.LENGTH_LONG
            ).show()

            return
        }


        MaterialAlertDialogBuilder(this)
            .setTitle("دریافت خروجی اکسل")
            .setMessage(
                "گزارش کامل کلاس «${model.className}» شامل وضعیت تمام دانش‌آموزان در کل جلسات، " +
                        "جمع غیبت و تأخیر و خلاصه هر جلسه ساخته می‌شود."
            )
            .setNegativeButton(
                "انصراف",
                null
            )
            .setPositiveButton(
                "دانلود اکسل"
            ) { _, _ ->

                chooseAttendanceExcelLocation(model)
            }
            .show()
    }

    private fun chooseAttendanceExcelLocation(model: ClassModel) {

        val safeClassName = model.className
            .replace(Regex("[^A-Za-z0-9\u0600-\u06FF_-]+"), "_")
            .trim('_')
            .take(60)
            .ifBlank { "class" }

        val timePart = SimpleDateFormat(
            "yyyyMMdd_HHmm",
            Locale.US
        ).format(Calendar.getInstance().time)

        val fileName =
            "attendance_${safeClassName}_$timePart.xlsx"

        pendingAttendanceExportClass = model

        createAttendanceExcelLauncher.launch(fileName)
    }


    private fun downloadAttendanceExcel(
        model: ClassModel,
        uri: Uri
    ) {

        val prefs = getSharedPreferences(
            "LocalAppPrefs",
            Context.MODE_PRIVATE
        )

        val token = prefs
            .getString("API_TOKEN", "")
            .orEmpty()

        if (token.isBlank()) {

            AppToast.makeText(
                this,
                "نشست کاربری معتبر نیست؛ لطفاً از حساب کاربری خارج شده و مجدداً وارد شوید",
                Toast.LENGTH_LONG
            ).show()

            return
        }


        setLoading(true)


        RetrofitClient.instance
            .downloadAttendanceExcel(
                RetrofitClient.attendanceExportUrl(model.id)
            )
            .enqueue(
                object : Callback<ResponseBody> {

                    override fun onResponse(
                        call: Call<ResponseBody>,
                        response: Response<ResponseBody>
                    ) {

                        val body = response.body()

                        if (!response.isSuccessful || body == null) {

                            setLoading(false)

                            AppToast.makeText(
                                this@AttendanceActivity,
                                ApiErrorParser.userMessage(
                                    response,
                                    "دریافت فایل اکسل حضور و غیاب کامل نشد"
                                ),
                                Toast.LENGTH_LONG
                            ).show()

                            return
                        }


                        try {

                            contentResolver
                                .openOutputStream(uri, "w")
                                ?.use { outputStream ->

                                    body.byteStream().use { inputStream ->

                                        inputStream.copyTo(
                                            outputStream,
                                            bufferSize = 8 * 1024
                                        )

                                        outputStream.flush()
                                    }
                                }
                                ?: throw IOException(
                                    "امکان باز کردن محل ذخیره فایل وجود ندارد"
                                )


                            setLoading(false)


                            AppToast.makeText(
                                this@AttendanceActivity,
                                "فایل اکسل با موفقیت ذخیره شد",
                                Toast.LENGTH_LONG
                            ).show()


                        } catch (e: Exception) {

                            setLoading(false)

                            AppToast.makeText(
                                this@AttendanceActivity,
                                when (e) {

                                    is SecurityException ->
                                        "مجوز ذخیره فایل صادر نشد؛ لطفاً محل دیگری را انتخاب کنید"

                                    is IOException ->
                                        "ذخیره فایل اکسل کامل نشد؛ لطفاً محل ذخیره و فضای ذخیره‌سازی دستگاه را بررسی کنید"

                                    else ->
                                        "ذخیره فایل اکسل کامل نشد؛ لطفاً محل ذخیره، مجوز دسترسی و فضای آزاد دستگاه را بررسی کنید"
                                },
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }


                    override fun onFailure(
                        call: Call<ResponseBody>,
                        t: Throwable
                    ) {

                        setLoading(false)

                        AppToast.makeText(
                            this@AttendanceActivity,
                            ApiErrorParser.networkMessage(t, "دانلود فایل اکسل حضور و غیاب"),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            )
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
                    AppToast.makeText(
                        this@AttendanceActivity,
                        if (body.createdAnnouncements > 0) {
                            "جلسه ${session.sessionNumber} ثبت نهایی شد؛ ${body.createdAnnouncements} اعلان غیبت یا تأخیر برای دانش‌آموزان ارسال شد"
                        } else {
                            "جلسه ${session.sessionNumber} ثبت نهایی شد؛ اعلان غیبت یا تأخیری برای این جلسه لازم نبود"
                        },
                        Toast.LENGTH_LONG
                    ).show()
                    loadOverview(preferredSessionNumber = session.sessionNumber)
                } else {
                    AppToast.makeText(
                        this@AttendanceActivity,
                        errorMessage(response, body?.message ?: "ثبت نهایی کامل نشد"),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            override fun onFailure(call: Call<AttendanceSaveResponse>, t: Throwable) {
                setSaving(false)
                AppToast.makeText(
                    this@AttendanceActivity,
                    ApiErrorParser.networkMessage(t, "ثبت نهایی حضور و غیاب"),
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
                expectedRevision = session.revision,
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
                    AppToast.makeText(
                        this@AttendanceActivity,
                        if (body.createdAnnouncements > 0) {
                            "اصلاحات جلسه ${session.sessionNumber} ذخیره شد؛ ${body.createdAnnouncements} اعلان مرتبط برای دانش‌آموزان به‌روزرسانی شد"
                        } else {
                            "اصلاحات جلسه ${session.sessionNumber} با موفقیت ذخیره شد"
                        },
                        Toast.LENGTH_LONG
                    ).show()
                    loadOverview(preferredSessionNumber = session.sessionNumber)
                } else {
                    AppToast.makeText(
                        this@AttendanceActivity,
                        errorMessage(response, body?.message ?: "ذخیره اصلاحات کامل نشد"),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            override fun onFailure(call: Call<AttendanceSaveResponse>, t: Throwable) {
                setSaving(false)
                AppToast.makeText(
                    this@AttendanceActivity,
                    ApiErrorParser.networkMessage(t, "ذخیره اصلاحات حضور و غیاب"),
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
        val initialGregorian =
            parseIsoDate(selectedHeldDate)
                ?: Calendar.getInstance()

        val initialPersian =
            PersianDateUtils.gregorianToPersian(
                initialGregorian.get(Calendar.YEAR),
                initialGregorian.get(Calendar.MONTH) + 1,
                initialGregorian.get(Calendar.DAY_OF_MONTH)
            )

        val now = Calendar.getInstance()
        val todayPersian =
            PersianDateUtils.gregorianToPersian(
                now.get(Calendar.YEAR),
                now.get(Calendar.MONTH) + 1,
                now.get(Calendar.DAY_OF_MONTH)
            )

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }

        val dayPicker = NumberPicker(this).apply {
            wrapSelectorWheel = false
        }

        val monthPicker = NumberPicker(this).apply {
            minValue = 1
            maxValue = 12
            displayedValues = PersianDateUtils.monthNames
            wrapSelectorWheel = false
        }

        val yearPicker = NumberPicker(this).apply {
            minValue = 1300
            maxValue = todayPersian.year
            wrapSelectorWheel = false
        }

        fun updateYearLabels() {
            yearPicker.displayedValues = null
            yearPicker.displayedValues =
                (yearPicker.minValue..yearPicker.maxValue)
                    .map {
                        PersianDateUtils.toPersianDigits(
                            it.toString()
                        )
                    }
                    .toTypedArray()
        }

        fun updateMonthBounds() {
            val selectedYear = yearPicker.value

            monthPicker.displayedValues = null
            monthPicker.minValue = 1
            monthPicker.maxValue =
                if (selectedYear == todayPersian.year) {
                    todayPersian.month
                } else {
                    12
                }

            monthPicker.displayedValues =
                PersianDateUtils.monthNames
                    .copyOfRange(
                        0,
                        monthPicker.maxValue
                    )

            if (monthPicker.value > monthPicker.maxValue) {
                monthPicker.value = monthPicker.maxValue
            }
        }

        fun updateDayBounds() {
            val selectedYear = yearPicker.value
            val selectedMonth = monthPicker.value

            val monthLength =
                PersianDateUtils.monthLength(
                    selectedYear,
                    selectedMonth
                )

            val maxDay =
                if (
                    selectedYear == todayPersian.year &&
                    selectedMonth == todayPersian.month
                ) {
                    minOf(
                        monthLength,
                        todayPersian.day
                    )
                } else {
                    monthLength
                }

            val currentDay =
                dayPicker.value
                    .takeIf { it > 0 }
                    ?: 1

            dayPicker.displayedValues = null
            dayPicker.minValue = 1
            dayPicker.maxValue = maxDay
            dayPicker.displayedValues =
                (1..maxDay)
                    .map {
                        PersianDateUtils.toPersianDigits(
                            it.toString()
                        )
                    }
                    .toTypedArray()

            dayPicker.value =
                currentDay.coerceIn(
                    1,
                    maxDay
                )
        }

        updateYearLabels()

        yearPicker.value =
            initialPersian.year.coerceIn(
                yearPicker.minValue,
                yearPicker.maxValue
            )

        updateMonthBounds()

        monthPicker.value =
            initialPersian.month.coerceIn(
                monthPicker.minValue,
                monthPicker.maxValue
            )

        updateDayBounds()

        dayPicker.value =
            initialPersian.day.coerceIn(
                dayPicker.minValue,
                dayPicker.maxValue
            )

        yearPicker.setOnValueChangedListener { _, _, _ ->
            updateMonthBounds()
            updateDayBounds()
        }

        monthPicker.setOnValueChangedListener { _, _, _ ->
            updateDayBounds()
        }

        container.addView(
            dayPicker,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                0.8f
            )
        )

        container.addView(
            monthPicker,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1.4f
            )
        )

        container.addView(
            yearPicker,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        val dialog =
            MaterialAlertDialogBuilder(this)
                .setTitle("انتخاب تاریخ برگزاری (شمسی)")
                .setView(container)
                .setNegativeButton("انصراف", null)
                .setPositiveButton("تأیید", null)
                .create()

        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {
                    val persianYear = yearPicker.value
                    val persianMonth = monthPicker.value
                    val persianDay = dayPicker.value

                    val gregorian =
                        PersianDateUtils.persianToGregorian(
                            persianYear,
                            persianMonth,
                            persianDay
                        )

                    val selected = Calendar.getInstance().apply {
                        set(Calendar.YEAR, gregorian.year)
                        set(Calendar.MONTH, gregorian.month - 1)
                        set(Calendar.DAY_OF_MONTH, gregorian.day)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }

                    val today = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, 23)
                        set(Calendar.MINUTE, 59)
                        set(Calendar.SECOND, 59)
                        set(Calendar.MILLISECOND, 999)
                    }

                    if (selected.after(today)) {
                        AppToast.makeText(
                            this,
                            "ثبت تاریخ آینده مجاز نیست",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@setOnClickListener
                    }

                    selectedHeldDate =
                        String.format(
                            Locale.US,
                            "%04d-%02d-%02d",
                            gregorian.year,
                            gregorian.month,
                            gregorian.day
                        )

                    btnAttendanceDate.text =
                        "تاریخ برگزاری: ${displayDate(selectedHeldDate)}"

                    dialog.dismiss()
                    onDateSelected?.invoke()
                }
        }

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

    private fun errorMessage(response: Response<*>, fallback: String): String =
        ApiErrorParser.userMessage(response, fallback)

    private fun normalizePhone(value: String): String {
        var normalized = value.trim().replace(" ", "")
        if (normalized.startsWith("+98")) normalized = normalized.removePrefix("+98")
        if (normalized.startsWith("0098")) normalized = normalized.removePrefix("0098")
        if (normalized.length == 11 && normalized.startsWith("0")) {
            normalized = normalized.drop(1)
        }
        return normalized
    }

    private fun displayDate(value: String): String {
        val calendar = parseIsoDate(value) ?: return value.replace('-', '/')

        val persian =
            PersianDateUtils.gregorianToPersian(
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH)
            )

        return PersianDateUtils.toPersianDigits(
            String.format(
                Locale.US,
                "%04d/%02d/%02d",
                persian.year,
                persian.month,
                persian.day
            )
        )
    }

    private fun displayDateTime(value: String): String {
        if (value.length < 10) {
            return value.replace('-', '/')
        }

        val datePart = value.substring(0, 10)
        val suffix = value.substring(10)

        return displayDate(datePart) +
                PersianDateUtils.toPersianDigits(suffix)
    }

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
