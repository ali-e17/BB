package com.example.bb

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var currentUserRole: UserRole

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefs = getSharedPreferences("LocalAppPrefs", Context.MODE_PRIVATE)
        val roleString = intent.getStringExtra("USER_ROLE")
            ?: prefs.getString("CURRENT_USER_ROLE", "STUDENT")
            ?: "STUDENT"
        val username = intent.getStringExtra("USERNAME")
            ?: prefs.getString("CURRENT_USERNAME", "")
            .orEmpty()

        currentUserRole = runCatching { UserRole.valueOf(roleString.uppercase()) }
            .getOrDefault(UserRole.STUDENT)

        val txtGreeting = findViewById<TextView>(R.id.txtGreeting)
        val txtUserName = findViewById<TextView>(R.id.txtUserName)
        val txtRoleBadge = findViewById<TextView>(R.id.txtRoleBadge)

        txtGreeting.text = greetingForHour(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
        txtUserName.text = prefs.getString("CURRENT_DISPLAY_NAME", "")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "کاربر عزیز"

        txtRoleBadge.text = when (currentUserRole) {
            UserRole.ADMIN -> "مدیر"
            UserRole.TEACHER -> "استاد"
            UserRole.STUDENT -> "دانش‌آموز"
        }
        txtRoleBadge.visibility = View.VISIBLE

        val btnThemeToggle = findViewById<ImageView>(R.id.btnThemeToggle)
        val btnLanguageToggle = findViewById<TextView>(R.id.btnLanguageToggle)
        updateThemeIcon(btnThemeToggle)

        var currentLanguage = prefs.getString("APP_LANGUAGE", "fa") ?: "fa"
        btnLanguageToggle.text = if (currentLanguage == "fa") "EN" else "فا"

        btnThemeToggle.setOnClickListener {
            val darkNow = isDarkMode()
            AppCompatDelegate.setDefaultNightMode(
                if (darkNow) AppCompatDelegate.MODE_NIGHT_NO
                else AppCompatDelegate.MODE_NIGHT_YES
            )
            getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("IS_DARK_MODE", !darkNow)
                .apply()
        }

        btnLanguageToggle.setOnClickListener {
            currentLanguage = if (currentLanguage == "fa") "en" else "fa"
            prefs.edit().putString("APP_LANGUAGE", currentLanguage).apply()
            btnLanguageToggle.text = if (currentLanguage == "fa") "EN" else "فا"
            Toast.makeText(
                this,
                if (currentLanguage == "fa") "زبان فارسی انتخاب شد" else "English selected",
                Toast.LENGTH_SHORT
            ).show()
        }

        findViewById<ImageView>(R.id.btnProfile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        recyclerView = findViewById(R.id.recyclerViewDashboard)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = DashboardAdapter(buildDashboardItems()) { clickedItem ->
            when {
                clickedItem.title.contains("اعلان") -> startActivity(
                    Intent(this, AnnouncementsActivity::class.java)
                        .putExtra("USER_ROLE", currentUserRole.name)
                )
                clickedItem.title.contains("کارنامه") || clickedItem.title.contains("نمر") -> {
                    when (currentUserRole) {
                        UserRole.ADMIN -> startActivity(Intent(this, ReportCardSetupActivity::class.java))
                        UserRole.STUDENT -> showStudentReportCards(username)
                        else -> Unit
                    }
                }
                clickedItem.title.contains("دانش‌آموزان") -> startActivity(Intent(this, StudentManagementActivity::class.java))
                clickedItem.title.contains("حضور") -> startActivity(Intent(this, AttendanceActivity::class.java))
                clickedItem.title.contains("کلاس‌ها") -> startActivity(Intent(this, ClassManagementActivity::class.java))
                clickedItem.title.contains("اساتید") -> startActivity(Intent(this, TeacherManagementActivity::class.java))
                clickedItem.title.contains("دیکشنری") -> startActivity(Intent(this, DictionaryActivity::class.java))
                else -> Toast.makeText(this, "این بخش در حال توسعه است", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        findViewById<TextView>(R.id.txtGreeting)?.text =
            greetingForHour(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
        val prefs = getSharedPreferences("LocalAppPrefs", Context.MODE_PRIVATE)
        findViewById<TextView>(R.id.txtUserName)?.text =
            prefs.getString("CURRENT_DISPLAY_NAME", "")?.takeIf { it.isNotBlank() } ?: "کاربر عزیز"
    }

    private fun greetingForHour(hour: Int): String = when (hour) {
        in 0..4 -> "شب بخیر،"
        in 5..7 -> "صبح زود بخیر،"
        in 8..11 -> "صبح بخیر،"
        in 12..13 -> "ظهر بخیر،"
        in 14..16 -> "بعدازظهر بخیر،"
        in 17..19 -> "عصر بخیر،"
        else -> "شب بخیر،"
    }

    private fun isDarkMode(): Boolean =
        resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES

    private fun updateThemeIcon(view: ImageView) {
        view.setImageResource(if (isDarkMode()) R.drawable.ic_sun else R.drawable.ic_moon)
    }

    private fun buildDashboardItems(): List<DashboardItem> = when (currentUserRole) {
        UserRole.STUDENT -> listOf(
            DashboardItem("کارنامه و نمرات", "مشاهده کارنامه‌های صادرشده", android.R.drawable.ic_menu_sort_by_size),
            DashboardItem("دیکشنری آفلاین", "جست‌وجوی لغات بدون اینترنت", android.R.drawable.ic_menu_search),
            DashboardItem("اعلانات", "پیام‌ها و تکالیف جدید", android.R.drawable.ic_menu_agenda)
        )
        UserRole.TEACHER -> listOf(
            DashboardItem("حضور و غیاب", "ثبت وضعیت جلسه‌های کلاس", android.R.drawable.ic_menu_recent_history),
            DashboardItem("اعلانات", "ارسال پیام برای کلاس‌ها", android.R.drawable.ic_dialog_email)
        )
        UserRole.ADMIN -> listOf(
            DashboardItem("مدیریت دانش‌آموزان", "ثبت‌نام و ویرایش اطلاعات", android.R.drawable.ic_menu_myplaces),
            DashboardItem("صدور کارنامه", "ثبت نمره و انتشار کارنامه", android.R.drawable.ic_menu_edit),
            DashboardItem("مدیریت کلاس‌ها", "تعریف کلاس و زمان‌بندی", android.R.drawable.ic_input_add),
            DashboardItem("مدیریت حضور و غیاب", "بازبینی جلسات و خروجی اکسل", android.R.drawable.ic_menu_recent_history),
            DashboardItem("اعلانات", "ارسال پیام به کلاس‌ها", android.R.drawable.ic_menu_send),
            DashboardItem("مدیریت اساتید", "افزودن و مدیریت دسترسی استاد", android.R.drawable.ic_menu_myplaces)
        )
    }

    private fun showStudentReportCards(phone: String) {
        val student = AppDatabase.getStudentByUsername(phone) ?: run {
            Toast.makeText(this, "اطلاعات دانش‌آموز هنوز همگام نشده است", Toast.LENGTH_SHORT).show()
            return
        }
        val reports = AppDatabase.getReportCardsForStudent(student.id)
        if (reports.isEmpty()) {
            Toast.makeText(this, "هنوز کارنامه‌ای برای شما منتشر نشده است", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = reports.map { report ->
            "${AppDatabase.getClassNameById(report.classId) ?: "کلاس"} - ${report.updatedAt}"
        }.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("کارنامه‌های منتشرشده")
            .setItems(labels) { _, position ->
                val report = reports[position]
                startActivity(Intent(this, ReportCardViewActivity::class.java).apply {
                    putExtra("STUDENT_ID", student.studentCode)
                    putExtra("STUDENT_NAME", student.name)
                    putExtra("CLASS_NAME", AppDatabase.getClassNameById(report.classId) ?: "کلاس")
                    putExtra("REPORT_DATE", report.updatedAt)
                    putStringArrayListExtra("CRITERIA_NAMES", ArrayList(report.criteria.map { it.name }))
                    putIntegerArrayListExtra("SCORES_LIST", ArrayList(report.criteria.map { report.scores[it.id] ?: 0 }))
                    putIntegerArrayListExtra("MAX_SCORES_LIST", ArrayList(report.criteria.map { it.maxScore }))
                })
            }
            .show()
    }
}
