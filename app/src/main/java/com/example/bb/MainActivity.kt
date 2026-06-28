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

enum class UserRole { STUDENT, TEACHER, ADMIN }

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

        txtUserName.text = usernameString.replaceFirstChar { it.uppercase() }

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

        val sharedPrefs = getSharedPreferences("LocalAppPrefs", Context.MODE_PRIVATE)
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
                // حذف شد: برنامه کلاسی
                items.add(DashboardItem("کارنامه و نمرات", "مشاهده کارنامه‌های صادر شده", android.R.drawable.ic_menu_sort_by_size))
                items.add(DashboardItem("دیکشنری آفلاین", "جستجوی لغات بدون نیاز به نت", android.R.drawable.ic_menu_search))
                items.add(DashboardItem("اعلانات", "مشاهده پیام‌ها و تکالیف جدید", android.R.drawable.ic_menu_agenda))
            }
            UserRole.TEACHER -> {
                // حذف شد: ورود نمرات
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
                            val intent = Intent(this, ReportCardSetupActivity::class.java)
                            startActivity(intent)
                        }
                        UserRole.STUDENT -> {
                            // TODO: StudentReportCardActivity - باید ساخته بشه
                            Toast.makeText(this, "این بخش در حال توسعه است", Toast.LENGTH_SHORT).show()
                        }
                        else -> {}
                    }
                }
                clickedItem.title.contains("دانش‌آموزان") -> {
                    val intent = Intent(this, StudentManagementActivity::class.java)
                    startActivity(intent)
                }
                clickedItem.title.contains("حضور") -> {
                    val intent = Intent(this, AttendanceActivity::class.java)
                    startActivity(intent)
                }
                clickedItem.title.contains("کلاس‌ها") -> {
                    val intent = Intent(this, ClassManagementActivity::class.java)
                    startActivity(intent)
                }
                clickedItem.title.contains("اساتید") -> {
                    val intent = Intent(this, TeacherManagementActivity::class.java)
                    startActivity(intent)
                }
                clickedItem.title.contains("دیکشنری") -> {
                    // TODO: DictionaryActivity - باید ساخته بشه
                    Toast.makeText(this, "این بخش در حال توسعه است", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    Toast.makeText(this, "این بخش در حال توسعه است: ${clickedItem.title}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}