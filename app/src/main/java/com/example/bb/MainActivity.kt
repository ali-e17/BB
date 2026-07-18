package com.example.bb

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var currentUserRole: UserRole

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val roleString = intent.getStringExtra("USER_ROLE") ?: "STUDENT"
        val usernameString = intent.getStringExtra("USERNAME") ?: "کاربر"

        currentUserRole = try {
            UserRole.valueOf(roleString)
        } catch (e: Exception) {
            UserRole.STUDENT
        }

        val txtGreeting = findViewById<TextView>(R.id.txtGreeting)
        val txtUserName = findViewById<TextView>(R.id.txtUserName)
        val txtRoleBadge = findViewById<TextView>(R.id.txtRoleBadge)

        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        txtGreeting.text = when (hour) {
            in 0..11 -> "صبح بخیر،"
            in 12..16 -> "ظهر بخیر،"
            in 17..19 -> "عصر بخیر،"
            else -> "شب بخیر،"
        }

        // 🌟 تغییر مهم: دریافت نام کامل کاربر که در زمان ورود از سرور گرفته شده بود
        val sharedPrefs = getSharedPreferences("LocalAppPrefs", Context.MODE_PRIVATE)
        val currentDisplayName = sharedPrefs.getString("CURRENT_DISPLAY_NAME", usernameString) ?: usernameString
        txtUserName.text = currentDisplayName

        txtRoleBadge.text = when (currentUserRole) {
            UserRole.STUDENT -> "دانش‌آموز"
            UserRole.TEACHER -> "استاد"
            UserRole.ADMIN -> "مدیر کل"
        }

        val btnThemeToggle = findViewById<ImageView>(R.id.btnThemeToggle)
        val btnLanguageToggle = findViewById<TextView>(R.id.btnLanguageToggle)

        val currentMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        if (currentMode == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
            btnThemeToggle.setImageResource(R.drawable.ic_sun)
        } else {
            btnThemeToggle.setImageResource(R.drawable.ic_moon)
        }

        var currentLanguage = sharedPrefs.getString("APP_LANGUAGE", "fa") ?: "fa"

        btnLanguageToggle.text = if (currentLanguage == "fa") "EN" else "FA"

        btnThemeToggle.setOnClickListener {
            val themePrefs = getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)
            val editor = themePrefs.edit()
            val checkMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK

            if (checkMode == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                editor.putBoolean("IS_DARK_MODE", false)
                Toast.makeText(this, "تم روشن شد", Toast.LENGTH_SHORT).show()
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                editor.putBoolean("IS_DARK_MODE", true)
                Toast.makeText(this, "تم تاریک شد", Toast.LENGTH_SHORT).show()
            }
            editor.apply()
        }

        btnLanguageToggle.setOnClickListener {
            val editor = sharedPrefs.edit()
            if (currentLanguage == "fa") {
                currentLanguage = "en"
                btnLanguageToggle.text = "FA"
                editor.putString("APP_LANGUAGE", "en")
                Toast.makeText(this, "Language changed to English", Toast.LENGTH_SHORT).show()
            } else {
                currentLanguage = "fa"
                btnLanguageToggle.text = "EN"
                editor.putString("APP_LANGUAGE", "fa")
                Toast.makeText(this, "زبان به فارسی تغییر یافت", Toast.LENGTH_SHORT).show()
            }
            editor.apply()
        }

        val btnProfile = findViewById<ImageView>(R.id.btnProfile)
        btnProfile.setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            intent.putExtra("USER_ROLE", currentUserRole.name.lowercase())
            startActivity(intent)
        }

        recyclerView = findViewById(R.id.recyclerViewDashboard)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val items = mutableListOf<DashboardItem>()

        when (currentUserRole) {
            UserRole.STUDENT -> {
                items.add(DashboardItem("کارنامه و نمرات", "مشاهده کارنامه‌های صادر شده", android.R.drawable.ic_menu_sort_by_size))
                items.add(DashboardItem("دیکشنری آفلاین", "جستجوی لغات بدون نیاز به نت", android.R.drawable.ic_menu_search))
                items.add(DashboardItem("اعلانات", "مشاهده پیام‌ها و تکالیف جدید", android.R.drawable.ic_menu_agenda))
            }
            UserRole.TEACHER -> {
                items.add(DashboardItem("حضور و غیاب", "ثبت لیست حضور و غیاب کلاس", android.R.drawable.ic_menu_recent_history))
                items.add(DashboardItem("اعلانات", "ارسال پیام برای کلاس‌ها", android.R.drawable.ic_dialog_email))
            }
            UserRole.ADMIN -> {
                items.add(DashboardItem("مدیریت دانش‌آموزان", "ثبت‌نام و ویرایش اطلاعات دانش‌آموزان", android.R.drawable.ic_menu_myplaces))
                items.add(DashboardItem("صدور کارنامه", "ثبت نمره و چاپ کارنامه", android.R.drawable.ic_menu_edit))
                items.add(DashboardItem("مدیریت کلاس‌ها", "تعریف کلاس جدید و زمان‌بندی", android.R.drawable.ic_input_add))
                items.add(DashboardItem("اعلانات", "ارسال پیام به اساتید و دانش‌آموزان", android.R.drawable.ic_menu_send))
                items.add(DashboardItem("مدیریت اساتید", "افزودن استاد جدید و مدیریت دسترسی", android.R.drawable.ic_menu_save))
            }
        }

        recyclerView.adapter = DashboardAdapter(items) { clickedItem ->
            when {
                clickedItem.title.contains("اعلان") -> {
                    val intent = Intent(this, AnnouncementsActivity::class.java)
                    intent.putExtra("USER_ROLE", currentUserRole.name)
                    startActivity(intent)
                }
                clickedItem.title.contains("کارنامه") || clickedItem.title.contains("نمر") -> {
                    when (currentUserRole) {
                        UserRole.ADMIN -> {
                            startActivity(Intent(this, ReportCardSetupActivity::class.java))
                        }
                        UserRole.STUDENT -> {
                            showStudentReportCards(usernameString)
                        }
                        else -> {}
                    }
                }
                clickedItem.title.contains("دانش‌آموزان") -> {
                    startActivity(Intent(this, StudentManagementActivity::class.java))
                }
                clickedItem.title.contains("حضور") -> {
                    startActivity(Intent(this, AttendanceActivity::class.java))
                }
                clickedItem.title.contains("کلاس‌ها") -> {
                    startActivity(Intent(this, ClassManagementActivity::class.java))
                }
                clickedItem.title.contains("اساتید") -> {
                    startActivity(Intent(this, TeacherManagementActivity::class.java))
                }
                clickedItem.title.contains("دیکشنری") -> {
                    startActivity(Intent(this, DictionaryActivity::class.java))
                }
                else -> {
                    Toast.makeText(this, "این بخش در حال توسعه است: ${clickedItem.title}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showStudentReportCards(phone: String) {
        val student = AppDatabase.getStudentByUsername(phone) ?: return
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
            }.show()
    }
}