package com.example.bb

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ProfileActivity : AppCompatActivity() {

    private lateinit var tvUserName: TextView
    private lateinit var tvUserRole: TextView
    private lateinit var layoutStudentOptions: LinearLayout
    private lateinit var layoutTeacherOptions: LinearLayout
    private lateinit var userRole: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        tvUserName = findViewById(R.id.tvUserName)
        tvUserRole = findViewById(R.id.tvUserRole)
        layoutStudentOptions = findViewById(R.id.layoutStudentOptions)
        layoutTeacherOptions = findViewById(R.id.layoutTeacherOptions)
        val btnChangeCredentials = findViewById<LinearLayout>(R.id.btnChangeCredentials)

        userRole = intent.getStringExtra("USER_ROLE") ?: "student"

        btnChangeCredentials.setOnClickListener {
            val intent = Intent(this, UpdateProfileActivity::class.java)
            intent.putExtra("USER_ROLE", userRole.uppercase())
            startActivity(intent)
        }
    }

    // استفاده از onResume برای بروزرسانی آنی نام کاربری پس از بازگشت از صفحه ویرایش
    override fun onResume() {
        super.onResume()

        val sharedPreferences = getSharedPreferences("LocalAppPrefs", Context.MODE_PRIVATE)
        val roleUpper = userRole.uppercase()

        // خواندن نام کاربری پویا از SharedPreferences (اگر تنظیم نشده باشد، نام نقش پیش‌فرض قرار می‌گیرد)
        val savedUsername = sharedPreferences.getString("${roleUpper}_USERNAME", userRole)

        // نمایش نام کاربری وارد شده در فیلد اصلی زیر آواتار
        tvUserName.text = savedUsername

        // تنظیم عنوان و دسترسی لایه‌ها بر اساس نقش ساختاری کاربر
        when (userRole.lowercase()) {
            "student" -> {
                tvUserRole.text = "دانش‌آموز آموزشگاه"
                layoutStudentOptions.visibility = View.VISIBLE
                layoutTeacherOptions.visibility = View.GONE
            }
            "teacher" -> {
                tvUserRole.text = "مدرس رسمی بیان برتر"
                layoutStudentOptions.visibility = View.GONE
                layoutTeacherOptions.visibility = View.VISIBLE
            }
            "admin" -> {
                tvUserRole.text = "دسترسی کامل (مدیر کل)"
                layoutStudentOptions.visibility = View.GONE
                layoutTeacherOptions.visibility = View.GONE
            }
        }
    }
}