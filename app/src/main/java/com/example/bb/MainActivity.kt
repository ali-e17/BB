package com.example.bb

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

enum class UserRole { STUDENT, TEACHER, ADMIN }

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var currentUserRole: UserRole

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

        // ================= اتصال دکمه پروفایل =================
        val btnProfile = findViewById<ImageView>(R.id.btnProfile)
        btnProfile.setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            intent.putExtra("USER_ROLE", currentUserRole.name.lowercase())
            startActivity(intent)
        }

        // ... (بقیه کدهای مربوط به RecyclerView و کارت‌ها همون قبلی‌ها باشن)
        // ========================================================================

        // ۲. تنظیمات مربوط به لیست صفحه هوم
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
                items.add(DashboardItem("تعریف کلاس‌ها", "مدیریت زمان‌بندی و ظرفیت کلاس", android.R.drawable.ic_input_add))
                items.add(DashboardItem("اطلاع‌رسانی کل", "ارسال اخبار سراسری آموزشگاه", android.R.drawable.ic_menu_send))
                items.add(DashboardItem("مدیریت دانش‌آموزان", "ثبت‌نام و ویرایش پروفایل زبان‌آموز", android.R.drawable.ic_menu_myplaces))
                items.add(DashboardItem("پنل اساتید", "مدیریت و تخصیص کلاس به اساتید", android.R.drawable.ic_menu_recent_history))
            }
        }

        recyclerView.adapter = DashboardAdapter(items) { clickedItem ->
            // اگر کاربر روی آیتم‌های مربوط به پیام‌ها و اطلاعیه‌ها کلیک کرد
            if (clickedItem.title.contains("پیام") || clickedItem.title.contains("اطلاعیه")) {
                val intent = Intent(this, AnnouncementsActivity::class.java)
                startActivity(intent)
            } else {
                Toast.makeText(this, "ورود به: ${clickedItem.title}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}