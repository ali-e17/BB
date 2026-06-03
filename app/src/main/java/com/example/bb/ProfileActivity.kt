package com.example.bayanebartar

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.bb.R

class ProfileActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        // ۱. اتصال المان‌های متنی هدر
        val tvUserName = findViewById<TextView>(R.id.tvUserName)
        val tvUserRole = findViewById<TextView>(R.id.tvUserRole)

        // ۲. اتصال لایه‌های اختصاصی نقش‌ها
        val layoutStudentOptions = findViewById<LinearLayout>(R.id.layoutStudentOptions)
        val layoutTeacherOptions = findViewById<LinearLayout>(R.id.layoutTeacherOptions)

        // ۳. گرفتن سطح کاربر فرستاده شده از صفحه هوم (اگر خالی بود پیش‌فرض student تست شود)
        val userRole = intent.getStringExtra("USER_ROLE") ?: "student"

        // ۴. شخصی‌سازی صفحه بر اساس سطح دسترسی کاربر
        when (userRole) {
            "student" -> {
                tvUserName.text = "علی علوی" // نام فرضی برای دانش‌آموز
                tvUserRole.text = "دانش‌آموز آموزشگاه"

                layoutStudentOptions.visibility = View.VISIBLE
                layoutTeacherOptions.visibility = View.GONE
            }
            "teacher" -> {
                tvUserName.text = "استاد کمالی" // نام فرضی برای استاد
                tvUserRole.text = "مدرس رسمی بیان برتر"

                layoutStudentOptions.visibility = View.GONE
                layoutTeacherOptions.visibility = View.VISIBLE
            }
            "admin" -> {
                tvUserName.text = "مدیریت سیستم" // نام فرضی برای مدیر
                tvUserRole.text = "دسترسی کامل (مدیر کل)"

                // مدیر فقط دکمه عمومی تغییر یوزر/پس را دارد، پس بقیه لایه‌ها مخفی می‌مانند
                layoutStudentOptions.visibility = View.GONE
                layoutTeacherOptions.visibility = View.GONE
            }
        }
    }
}