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

    // متغیر موقتی برای نگهداری وضعیت زبان
    private var isEnglish: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // ۱. خواندن نقش و یوزرنیم فرستاده شده از لاگین
        val roleString = intent.getStringExtra("USER_ROLE") ?: "STUDENT"
        val usernameString = intent.getStringExtra("USERNAME") ?: "کاربر"

        currentUserRole = try {
            UserRole.valueOf(roleString)
        } catch (e: Exception) {
            UserRole.STUDENT
        }

        // ================= تنظیم هدر (خوش‌آمدگویی، نام و نقش) =================
        val txtGreeting = findViewById<TextView>(R.id.txtGreeting)
        val txtUserName = findViewById<TextView>(R.id.txtUserName)
        val txtRoleBadge = findViewById<TextView>(R.id.txtRoleBadge)

        // دریافت ساعت سیستم برای خوش‌آمدگویی هوشمند
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        txtGreeting.text = when (hour) {
            in 0..11 -> "صبح بخیر 👋"
            in 12..16 -> "ظهر بخیر ☀️"
            in 17..19 -> "عصر بخیر 🌇"
            else -> "شب بخیر 🌙"
        }

        // قرار دادن یوزرنیم (با حرف اول بزرگ)
        txtUserName.text = usernameString.replaceFirstChar { it.uppercase() }

        // تنظیم لیبل نقش با اسم‌های فارسی
        txtRoleBadge.text = when (currentUserRole) {
            UserRole.STUDENT -> "دانش‌آموز"
            UserRole.TEACHER -> "استاد"
            UserRole.ADMIN -> "مدیریت"
        }
        // ========================================================================

        // ================= اتصال دکمه‌های جدید (تم و زبان) =================
        val btnThemeToggle = findViewById<ImageView>(R.id.btnThemeToggle)
        val btnLanguageToggle = findViewById<ImageView>(R.id.btnLanguageToggle)

        // چک کردن حالتِ فعلیِ سیستم جهت قرار دادن آیکون صحیح (خورشید یا ماه)
        val currentMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        if (currentMode == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
            btnThemeToggle.setImageResource(R.drawable.ic_sun) // اگر تم تاریک است، آیکون خورشید را نشان بده
        } else {
            btnThemeToggle.setImageResource(R.drawable.ic_moon) // اگر تم روشن است، آیکون ماه را نشان بده
        }

        // تنظیم آیکون زبان موقع بالا آمدن برنامه (فعلاً روی EN)
        btnLanguageToggle.setImageResource(if (isEnglish) R.drawable.ic_lang_en else R.drawable.ic_lang_fa)

        // ۳. منطق دکمه تم (به‌روزرسانی شده با SharedPreferences برای ذخیره سراسری)
        // منطق دکمه تم (نسخه اصلاح‌شده و بدون خطا)
        btnThemeToggle.setOnClickListener {
            val sharedPrefs = getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)
            val editor = sharedPrefs.edit()

            // چک کردن وضعیت لایو و لحظه‌ای تم گوشی در زمان کلیک
            val checkMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK

            if (checkMode == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                editor.putBoolean("IS_DARK_MODE", false)
                Toast.makeText(this, "حالت روشن فعال شد", Toast.LENGTH_SHORT).show()
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                editor.putBoolean("IS_DARK_MODE", true)
                Toast.makeText(this, "حالت تاریک فعال شد", Toast.LENGTH_SHORT).show()
            }
            editor.apply()
        }

        // ۴. منطق دکمه تغییر زبان
        btnLanguageToggle.setOnClickListener {
            if (isEnglish) {
                isEnglish = false
                btnLanguageToggle.setImageResource(R.drawable.ic_lang_fa)
                Toast.makeText(this, "English language activated (Simulation)", Toast.LENGTH_SHORT).show()
            } else {
                isEnglish = true
                btnLanguageToggle.setImageResource(R.drawable.ic_lang_en)
                Toast.makeText(this, "زبان فارسی فعال شد (شبیه‌سازی)", Toast.LENGTH_SHORT).show()
            }
        }
        // ========================================================================

        // ================= اتصال دکمه پروفایل =================
        val btnProfile = findViewById<ImageView>(R.id.btnProfile)
        btnProfile.setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            intent.putExtra("USER_ROLE", currentUserRole.name.lowercase())
            startActivity(intent)
        }
        // ========================================================================

        // ۲. تنظیمات مربوط به لیست صفحه هوم (RecyclerView)
        recyclerView = findViewById(R.id.recyclerViewDashboard)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val items = mutableListOf<DashboardItem>()

        when (currentUserRole) {
            UserRole.STUDENT -> {
                items.add(DashboardItem("کارنامه ترم", "مشاهده نمرات و پیشرفت تحصیلی", android.R.drawable.ic_menu_sort_by_size))
                items.add(DashboardItem("دیکشنری هوشمند", "ترجمه کلمات و جملات کاربردی", android.R.drawable.ic_menu_search))
                items.add(DashboardItem("پیام‌ها و اطلاعیه‌ها", "تکالیف جدید و اخبار آموزشگاه", android.R.drawable.ic_dialog_email))
            }
            UserRole.TEACHER -> {
                items.add(DashboardItem("اطلاعیه‌های کلاس", "ارسال پیام به دانش‌آموزان", android.R.drawable.ic_dialog_email))
                items.add(DashboardItem("مدیریت کلاس‌ها", "لیست حضور غیاب و برنامه کلاسی", android.R.drawable.ic_menu_today))
            }
            UserRole.ADMIN -> {
                items.add(DashboardItem("مدیریت نمرات", "ثبت و تایید نمرات نهایی ترم", android.R.drawable.ic_menu_edit))
                items.add(DashboardItem("تعریف کلاس‌ها", "مدیریت زمان‌ب بندی و ظرفیت کلاس", android.R.drawable.ic_input_add))
                items.add(DashboardItem("اطلاع‌رسانی کل", "ارسال اخبار سراسری آموزشگاه", android.R.drawable.ic_menu_send))
                items.add(DashboardItem("مدیریت دانش‌آموزان", "ثبت‌نام و ویرایش پروفایل زبان‌آموز", android.R.drawable.ic_menu_myplaces))
                items.add(DashboardItem("پنل اساتید", "مدیریت و تخصیص کلاس به اساتید", android.R.drawable.ic_menu_recent_history))
            }
        }

        recyclerView.adapter = DashboardAdapter(items) { clickedItem ->
            if (clickedItem.title.contains("پیام") || clickedItem.title.contains("اطلاع")) {
                val intent = Intent(this, AnnouncementsActivity::class.java)
                // این یک خط رو اضافه کن تا نقش به صفحه پیام‌ها فرستاده بشه
                intent.putExtra("USER_ROLE", currentUserRole.name)
                startActivity(intent)
            } else if (clickedItem.title.contains("نمرات")) {
                val intent = Intent(this, ReportCardSetupActivity::class.java)
                startActivity(intent)
            }
            else {
                Toast.makeText(this, "ورود به: ${clickedItem.title}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}