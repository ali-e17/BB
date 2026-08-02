package com.example.bb

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

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
        setupThemeButton()
        setupLanguageButton(prefs)
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

        txtGreeting.text = greetingForHour(
            Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        )

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
     * دکمه تغییر تم روشن و تاریک
     */
    private fun setupThemeButton() {
        val btnThemeToggle = findViewById<ImageView>(R.id.btnThemeToggle)

        updateThemeIcon(btnThemeToggle)

        btnThemeToggle.setOnClickListener {
            val darkModeEnabled = isDarkMode()

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
    private fun setupLanguageButton(
        prefs: android.content.SharedPreferences
    ) {
        val btnLanguageToggle = findViewById<TextView>(
            R.id.btnLanguageToggle
        )

        var currentLanguage = prefs
            .getString(PREF_APP_LANGUAGE, LANGUAGE_FA)
            ?: LANGUAGE_FA

        btnLanguageToggle.text =
            if (currentLanguage == LANGUAGE_FA) "EN" else "فا"

        btnLanguageToggle.setOnClickListener {
            currentLanguage =
                if (currentLanguage == LANGUAGE_FA) {
                    LANGUAGE_EN
                } else {
                    LANGUAGE_FA
                }

            prefs.edit()
                .putString(PREF_APP_LANGUAGE, currentLanguage)
                .apply()

            btnLanguageToggle.text =
                if (currentLanguage == LANGUAGE_FA) "EN" else "فا"

            Toast.makeText(
                this,
                if (currentLanguage == LANGUAGE_FA) {
                    "زبان فارسی انتخاب شد"
                } else {
                    "English selected"
                },
                Toast.LENGTH_SHORT
            ).show()
        }
    }

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
                        showStudentReportCards()
                    }
                }
            }

            TITLE_VIEW_REPORT -> {
                showStudentReportCards()
            }

            TITLE_STUDENT_HISTORY,
            TITLE_CLASS_HISTORY -> {
                startActivity(
                    Intent(this, TermHistoryActivity::class.java)
                        .putExtra(
                            TermHistoryActivity.EXTRA_ROLE,
                            currentUserRole.name
                        )
                        .putExtra(
                            TermHistoryActivity.EXTRA_ID,
                            currentUserId
                        )
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
                Toast.makeText(
                    this,
                    "این بخش هنوز آماده نشده است",
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
                    "مشاهده کارنامه‌های منتشرشده",
                    R.drawable.home_view_report
                ),
                DashboardItem(
                    TITLE_STUDENT_HISTORY,
                    "مشاهده کلاس‌ها و ترم‌های قبلی",
                    R.drawable.home_classes
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
            greetingForHour(
                Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            )

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
     * متن خوش‌آمدگویی براساس ساعت
     */
    private fun greetingForHour(hour: Int): String {
        return when (hour) {
            in 0..4 -> "شب بخیر"
            in 5..7 -> "صبح زود بخیر"
            in 8..11 -> "صبح بخیر"
            in 12..13 -> "ظهر بخیر"
            in 14..16 -> "بعدازظهر بخیر"
            in 17..19 -> "عصر بخیر"
            else -> "شب بخیر"
        }
    }

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

    /**
     * دریافت و نمایش کارنامه‌های دانش‌آموز
     */
    private fun showStudentReportCards() {
        RetrofitClient.instance
            .getReportCards()
            .enqueue(object : Callback<List<ReportCardDto>> {

                override fun onResponse(
                    call: Call<List<ReportCardDto>>,
                    response: Response<List<ReportCardDto>>
                ) {
                    val reports =
                        if (response.isSuccessful) {
                            response.body().orEmpty()
                        } else {
                            emptyList()
                        }

                    if (reports.isEmpty()) {
                        Toast.makeText(
                            this@MainActivity,
                            "هنوز کارنامه‌ای برای شما منتشر نشده است",
                            Toast.LENGTH_SHORT
                        ).show()
                        return
                    }

                    val labels = reports.map { report ->
                        val term = listOf(
                            report.termSeason,
                            report.termYear
                        )
                            .filter { it.isNotBlank() }
                            .joinToString(" ")

                        listOf(
                            report.className,
                            term,
                            "نمره ${formatScore(report.totalScore)}"
                        )
                            .filter { it.isNotBlank() }
                            .joinToString(" • ")
                    }.toTypedArray()

                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("کارنامه‌های منتشرشده")
                        .setItems(labels) { _, position ->
                            startActivity(
                                Intent(
                                    this@MainActivity,
                                    ReportCardViewActivity::class.java
                                ).putExtra(
                                    ReportCardViewActivity
                                        .EXTRA_REPORT_CARD_ID,
                                    reports[position].id
                                )
                            )
                        }
                        .setNegativeButton("بستن", null)
                        .show()
                }

                override fun onFailure(
                    call: Call<List<ReportCardDto>>,
                    throwable: Throwable
                ) {
                    Toast.makeText(
                        this@MainActivity,
                        "دریافت کارنامه‌ها انجام نشد",
                        Toast.LENGTH_LONG
                    ).show()
                }
            })
    }

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