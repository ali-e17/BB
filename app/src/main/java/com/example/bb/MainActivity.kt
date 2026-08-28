package com.example.bb

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

class MainActivity : BaseActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var currentUserRole: UserRole

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefs = getSharedPreferences(
            LOCAL_PREFS_NAME,
            Context.MODE_PRIVATE
        )

        val roleString = intent.getStringExtra(EXTRA_USER_ROLE)
            ?: prefs.getString(PREF_CURRENT_USER_ROLE, UserRole.STUDENT.name)
            ?: UserRole.STUDENT.name

        val currentUserId = prefs
            .getString(PREF_CURRENT_USER_ID, "")
            .orEmpty()

        currentUserRole = runCatching {
            UserRole.valueOf(roleString.uppercase(Locale.ROOT))
        }.getOrDefault(UserRole.STUDENT)

        setupHeader(prefs)
        setupTopBarWebsiteLink()
        setupThemeButton()
        setupProfileButton()
        setupDashboard(currentUserId)
    }

    /**
     * تنظیم متن خوش‌آمدگویی، نام کاربر و نقش
     */
    private fun setupHeader(prefs: android.content.SharedPreferences) {
        val txtGreeting = findViewById<TextView>(R.id.txtGreeting)
        val txtUserName = findViewById<TextView>(R.id.txtUserName)
        val txtRoleBadge = findViewById<TextView>(R.id.txtRoleBadge)

        txtGreeting.text = getSeasonalGreeting()

        txtUserName.text = prefs
            .getString(PREF_CURRENT_DISPLAY_NAME, "")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "کاربر محترم"

        txtRoleBadge.text = when (currentUserRole) {
            UserRole.ADMIN -> "مدیر"
            UserRole.TEACHER -> "استاد"
            UserRole.STUDENT -> "دانش‌آموز"
        }

        txtRoleBadge.visibility = View.VISIBLE
    }

    /**
     * باز کردن سایت آموزشگاه با لمس عنوان وسط Top Bar
     */
    private fun setupTopBarWebsiteLink() {
        findViewById<TextView>(R.id.txtTopBarSchoolName).setOnClickListener {
            runCatching {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(SCHOOL_WEBSITE_URL)
                    )
                )
            }.onFailure {
                AppToast.warning(
                    this,
                    "مرورگر مناسبی برای باز کردن سایت آموزشگاه در دسترس نیست"
                )
            }
        }
    }

    /**
     * دکمه تغییر تم روشن و تاریک
     */
    private fun setupThemeButton() {
        val btnThemeToggle = findViewById<ImageView>(R.id.btnThemeToggle)

        updateThemeIcon(btnThemeToggle)

        btnThemeToggle.setOnClickListener {
            val darkModeEnabled = isDarkMode()

            AppToast.info(
                applicationContext,
                if (darkModeEnabled) "حالت روشن فعال شد" else "حالت تاریک فعال شد"
            )

            AppCompatDelegate.setDefaultNightMode(
                if (darkModeEnabled) {
                    AppCompatDelegate.MODE_NIGHT_NO
                } else {
                    AppCompatDelegate.MODE_NIGHT_YES
                }
            )

            getSharedPreferences(THEME_PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(PREF_IS_DARK_MODE, !darkModeEnabled)
                .apply()
        }
    }

    /**
     * دکمه تغییر زبان
     */


    /**
     * دکمه پروفایل
     */
    private fun setupProfileButton() {
        findViewById<ImageView>(R.id.btnProfile).setOnClickListener {
            startActivity(
                Intent(this, ProfileActivity::class.java)
            )
        }
    }

    /**
     * ساخت لیست داشبورد و مدیریت کلیک کارت‌ها
     */
    private fun setupDashboard(currentUserId: String) {
        recyclerView = findViewById(R.id.recyclerViewDashboard)
        recyclerView.layoutManager = LinearLayoutManager(this)

        recyclerView.adapter = DashboardAdapter(
            buildDashboardItems()
        ) { clickedItem ->
            openDashboardItem(
                title = clickedItem.title,
                currentUserId = currentUserId
            )
        }
    }

    /**
     * باز کردن صفحه مربوط به هر کارت
     */
    private fun openDashboardItem(
        title: String,
        currentUserId: String
    ) {
        when (title) {

            TITLE_ANNOUNCEMENTS -> {
                startActivity(
                    Intent(this, AnnouncementsActivity::class.java)
                        .putExtra(
                            EXTRA_USER_ROLE,
                            currentUserRole.name
                        )
                )
            }

            TITLE_ISSUE_REPORT -> {
                when (currentUserRole) {
                    UserRole.ADMIN,
                    UserRole.TEACHER -> {
                        startActivity(
                            Intent(
                                this,
                                ReportCardSetupActivity::class.java
                            )
                        )
                    }

                    UserRole.STUDENT -> {
                        openStudentReportHistory(currentUserId)
                    }
                }
            }

            TITLE_VIEW_REPORT,
            TITLE_STUDENT_HISTORY -> {
                openStudentReportHistory(currentUserId)
            }


            TITLE_CLASS_HISTORY -> {
                startActivity(
                    Intent(this, TeacherHistoryActivity::class.java)
                )
            }


            TITLE_STUDENT_MANAGEMENT -> {
                startActivity(
                    Intent(
                        this,
                        StudentManagementActivity::class.java
                    )
                )
            }

            TITLE_ATTENDANCE,
            TITLE_ATTENDANCE_MANAGEMENT -> {
                startActivity(
                    Intent(this, AttendanceActivity::class.java)
                )
            }

            TITLE_CLASS_MANAGEMENT -> {
                startActivity(
                    Intent(
                        this,
                        ClassManagementActivity::class.java
                    )
                )
            }

            TITLE_TEACHER_MANAGEMENT -> {
                startActivity(
                    Intent(
                        this,
                        TeacherManagementActivity::class.java
                    )
                )
            }

            TITLE_DICTIONARY -> {
                startActivity(
                    Intent(this, DictionaryActivity::class.java)
                )
            }

            else -> {
                AppToast.makeText(
                    this,
                    "این بخش در نسخه فعلی برنامه فعال نیست",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * آیتم‌های داشبورد براساس نقش کاربر
     */
    private fun buildDashboardItems(): List<DashboardItem> {
        return when (currentUserRole) {

            UserRole.STUDENT -> listOf(
                DashboardItem(
                    TITLE_ANNOUNCEMENTS,
                    "مشاهده اطلاعیه‌ها و اخبار آموزشگاه",
                    R.drawable.home_announcements
                ),
                DashboardItem(
                    TITLE_VIEW_REPORT,
                    "مشاهده کارنامه ترم فعلی و ترم‌های گذشته",
                    R.drawable.home_view_report
                ),
                DashboardItem(
                    TITLE_DICTIONARY,
                    "جست‌وجوی معنی واژگان انگلیسی",
                    R.drawable.home_dictionary
                )
            )

            UserRole.TEACHER -> listOf(
                DashboardItem(
                    TITLE_ATTENDANCE,
                    "ثبت حضور و غیاب کلاس‌های شما",
                    R.drawable.home_attendance
                ),
                DashboardItem(
                    TITLE_ISSUE_REPORT,
                    "ثبت نمرات و صدور کارنامه",
                    R.drawable.home_issue_report
                ),
                DashboardItem(
                    TITLE_ANNOUNCEMENTS,
                    "مشاهده و ارسال اعلانات",
                    R.drawable.home_announcements
                ),
                DashboardItem(
                    TITLE_CLASS_HISTORY,
                    "مشاهده سوابق و کلاس‌های پایان‌یافته",
                    R.drawable.home_teacher_history
                )
            )

            UserRole.ADMIN -> listOf(
                DashboardItem(
                    TITLE_STUDENT_MANAGEMENT,
                    "افزودن و مدیریت دانش‌آموزان",
                    R.drawable.home_students
                ),
                DashboardItem(
                    TITLE_ISSUE_REPORT,
                    "ثبت نمرات و صدور کارنامه",
                    R.drawable.home_issue_report
                ),
                DashboardItem(
                    TITLE_CLASS_MANAGEMENT,
                    "ساخت و مدیریت کلاس‌ها",
                    R.drawable.home_classes
                ),
                DashboardItem(
                    TITLE_ATTENDANCE_MANAGEMENT,
                    "مدیریت جلسات و حضور و غیاب",
                    R.drawable.home_attendance
                ),
                DashboardItem(
                    TITLE_ANNOUNCEMENTS,
                    "ارسال و مشاهده اعلانات",
                    R.drawable.home_announcements
                ),
                DashboardItem(
                    TITLE_TEACHER_MANAGEMENT,
                    "افزودن و مدیریت اساتید",
                    R.drawable.home_teachers
                )
            )
        }
    }

    override fun onResume() {
        super.onResume()

        findViewById<TextView>(R.id.txtGreeting)?.text =
            getSeasonalGreeting()

        val prefs = getSharedPreferences(
            LOCAL_PREFS_NAME,
            Context.MODE_PRIVATE
        )

        findViewById<TextView>(R.id.txtUserName)?.text =
            prefs.getString(PREF_CURRENT_DISPLAY_NAME, "")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: "کاربر محترم"
    }

    /**
     * پیام خوش‌آمدگویی براساس ساعت رسمی ایران و فصل شمسی.
     */
    private fun getSeasonalGreeting(): String {
        val iranNow = ZonedDateTime.now(ZoneId.of(IRAN_TIME_ZONE))
        val persianDate = PersianDateUtils.gregorianToPersian(
            iranNow.year,
            iranNow.monthValue,
            iranNow.dayOfMonth
        )
        val minutes = iranNow.hour * 60 + iranNow.minute

        return when (persianDate.month) {
            in 1..3 -> greetingForSpring(minutes)
            in 4..6 -> greetingForSummer(minutes)
            in 7..9 -> greetingForAutumn(minutes)
            else -> greetingForWinter(minutes)
        }
    }

    private fun greetingForSpring(minutes: Int): String = when {
        minutes < timeInMinutes(5, 0) -> "شب بخیر 🌙"
        minutes < timeInMinutes(12, 0) -> "صبح بخیر ☕"
        minutes < timeInMinutes(14, 0) -> "ظهر بخیر ☀️"
        minutes < timeInMinutes(17, 0) -> "بعدازظهر بخیر 🕑"
        minutes < timeInMinutes(19, 30) -> "عصر بخیر 🌆"
        else -> "شب بخیر 🌃"
    }

    private fun greetingForSummer(minutes: Int): String = when {
        minutes < timeInMinutes(5, 0) -> "شب بخیر 🌙"
        minutes < timeInMinutes(12, 0) -> "صبح بخیر ☕"
        minutes < timeInMinutes(14, 0) -> "ظهر بخیر ☀️"
        minutes < timeInMinutes(17, 0) -> "بعدازظهر بخیر 🕑"
        minutes < timeInMinutes(20, 0) -> "عصر بخیر 🌆"
        else -> "شب بخیر 🌃"
    }

    private fun greetingForAutumn(minutes: Int): String = when {
        minutes < timeInMinutes(5, 30) -> "شب بخیر 🌙"
        minutes < timeInMinutes(12, 0) -> "صبح بخیر ☕"
        minutes < timeInMinutes(14, 0) -> "ظهر بخیر ☀️"
        minutes < timeInMinutes(16, 30) -> "بعدازظهر بخیر 🕑"
        minutes < timeInMinutes(18, 0) -> "عصر بخیر 🌆"
        else -> "شب بخیر 🌃"
    }

    private fun greetingForWinter(minutes: Int): String = when {
        minutes < timeInMinutes(6, 0) -> "شب بخیر 🌙"
        minutes < timeInMinutes(12, 0) -> "صبح بخیر ☕"
        minutes < timeInMinutes(13, 30) -> "ظهر بخیر ☀️"
        minutes < timeInMinutes(15, 30) -> "بعدازظهر بخیر 🕑"
        minutes < timeInMinutes(17, 30) -> "عصر بخیر 🌆"
        else -> "شب بخیر 🌃"
    }

    private fun timeInMinutes(hour: Int, minute: Int): Int = hour * 60 + minute

    private fun isDarkMode(): Boolean {
        return resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
    }

    private fun updateThemeIcon(view: ImageView) {
        view.setImageResource(
            if (isDarkMode()) {
                R.drawable.ic_sun
            } else {
                R.drawable.ic_moon
            }
        )
    }

    private fun openStudentReportHistory(currentUserId: String) {
        startActivity(
            Intent(this, TermHistoryActivity::class.java)
                .putExtra(TermHistoryActivity.EXTRA_ROLE, UserRole.STUDENT.name)
                .putExtra(TermHistoryActivity.EXTRA_ID, currentUserId)
        )
    }

    /**
     * مسیر قدیمی وضعیت کارنامه ترم فعلی نگه داشته شده است تا هیچ بخش دیگری از برنامه
     * دچار ناسازگاری نشود؛ ورودی اصلی دانش‌آموز اکنون TermHistoryActivity است.
     */
    private fun showStudentReportCards() {
        RetrofitClient.instance
            .getCurrentReportCards()
            .enqueue(object : Callback<CurrentReportsResponse> {

                override fun onResponse(
                    call: Call<CurrentReportsResponse>,
                    response: Response<CurrentReportsResponse>
                ) {
                    val body = response.body()
                    val apiError = ApiErrorParser.parse(response)
                    if (!response.isSuccessful || body?.status != "success") {
                        AppToast.makeText(
                            this@MainActivity,
                            body?.message?.takeIf { it.isNotBlank() }
                                ?: ApiErrorParser.userMessage(response, apiError, "دریافت وضعیت کارنامه کامل نشد"),
                            Toast.LENGTH_LONG
                        ).show()
                        return
                    }

                    val items = body.items
                    if (items.isEmpty()) {
                        AppToast.makeText(
                            this@MainActivity,
                            body.message.ifBlank { "کلاس فعالی برای شما ثبت نشده است" },
                            Toast.LENGTH_LONG
                        ).show()
                        return
                    }

                    if (items.size == 1) {
                        openCurrentReportItem(items.first())
                        return
                    }

                    val labels = items.map { item ->
                        val term = listOf(item.termSeason, item.termYear)
                            .filter { it.isNotBlank() }
                            .joinToString(" ")
                        val stateLabel = if (item.state == "PUBLISHED") {
                            item.card?.let { "منتشرشده • نمره ${formatScore(it.totalScore)}" }
                                ?: "منتشرشده"
                        } else {
                            "در انتظار انتشار"
                        }
                        listOf(item.className, item.classLevel, term, stateLabel)
                            .filter { it.isNotBlank() }
                            .joinToString(" • ")
                    }.toTypedArray()

                    MaterialAlertDialogBuilder(this@MainActivity)
                        .setTitle("کارنامه ترم فعلی")
                        .setItems(labels) { _, position ->
                            openCurrentReportItem(items[position])
                        }
                        .setNegativeButton("بستن", null)
                        .show()
                }

                override fun onFailure(
                    call: Call<CurrentReportsResponse>,
                    throwable: Throwable
                ) {
                    AppToast.makeText(
                        this@MainActivity,
                        ApiErrorParser.networkMessage(throwable, "دریافت وضعیت کارنامه"),
                        Toast.LENGTH_LONG
                    ).show()
                }
            })
    }

    private fun openCurrentReportItem(item: CurrentReportItem) {
        val card = item.card
        if (item.state == "PUBLISHED" && card != null && card.id.isNotBlank()) {
            startActivity(
                Intent(this, ReportCardViewActivity::class.java)
                    .putExtra(ReportCardViewActivity.EXTRA_REPORT_CARD_ID, card.id)
            )
        } else {
            AppToast.makeText(
                this,
                item.message.ifBlank { "کارنامه این ترم تاکنون منتشر نشده است" },
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun String?.ifNullOrBlank(fallback: String): String =
        this?.takeIf { it.isNotBlank() } ?: fallback

    private fun formatScore(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            String.format(Locale.US, "%.2f", value)
        }
    }

    companion object {
        private const val LOCAL_PREFS_NAME = "LocalAppPrefs"
        private const val THEME_PREFS_NAME = "ThemePrefs"

        private const val EXTRA_USER_ROLE = "USER_ROLE"

        private const val PREF_CURRENT_USER_ROLE =
            "CURRENT_USER_ROLE"

        private const val PREF_CURRENT_USER_ID =
            "CURRENT_USER_ID"

        private const val PREF_CURRENT_DISPLAY_NAME =
            "CURRENT_DISPLAY_NAME"

        private const val PREF_APP_LANGUAGE =
            "APP_LANGUAGE"

        private const val PREF_IS_DARK_MODE =
            "IS_DARK_MODE"

        private const val SCHOOL_WEBSITE_URL =
            "https://bayan-e-bartar.ir/"

        private const val IRAN_TIME_ZONE =
            "Asia/Tehran"

        private const val LANGUAGE_FA = "fa"
        private const val LANGUAGE_EN = "en"

        private const val TITLE_STUDENT_MANAGEMENT =
            "مدیریت دانش‌آموزان"

        private const val TITLE_ISSUE_REPORT =
            "صدور کارنامه"

        private const val TITLE_CLASS_MANAGEMENT =
            "مدیریت کلاس‌ها"

        private const val TITLE_ATTENDANCE_MANAGEMENT =
            "مدیریت حضور و غیاب"

        private const val TITLE_ATTENDANCE =
            "حضور و غیاب"

        private const val TITLE_ANNOUNCEMENTS =
            "اعلانات"

        private const val TITLE_TEACHER_MANAGEMENT =
            "مدیریت اساتید"

        private const val TITLE_DICTIONARY =
            "دیکشنری"

        private const val TITLE_VIEW_REPORT =
            "مشاهده کارنامه"

        private const val TITLE_STUDENT_HISTORY =
            "سوابق تحصیلی"

        private const val TITLE_CLASS_HISTORY =
            "سوابق کلاس‌ها"

        private const val TITLE_TRASH =
            "سطل زباله"
    }
}
