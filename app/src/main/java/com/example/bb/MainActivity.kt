package com.example.bb

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
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

        // ۱. خواندن نقش فرستاده شده از لاگین
        val roleString = intent.getStringExtra("USER_ROLE") ?: "STUDENT"
        currentUserRole = try {
            UserRole.valueOf(roleString)
        } catch (e: Exception) {
            UserRole.STUDENT
        }

        // ================= اتصال دکمه پروفایل (اصلاح آی‌دی به btnProfile) =================
        val btnProfile = findViewById<ImageView>(R.id.btnProfile)
        btnProfile.setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            // فرستادن نقش به صورت حروف کوچک به صفحه پروفایل شما
            intent.putExtra("USER_ROLE", currentUserRole.name.lowercase())
            startActivity(intent)
        }
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
            Toast.makeText(this, "ورود به: ${clickedItem.title}", Toast.LENGTH_SHORT).show()
        }
    }
}