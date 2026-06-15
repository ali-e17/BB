package com.example.bb

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.textfield.TextInputEditText

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // ۱. اتصال المان‌های قدیمی لایوت
        val etUsername = findViewById<TextInputEditText>(R.id.etUsername)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)

        // ۲. اتصال دکمه‌های جدید تنظیمات (تم و زبان)
        val btnThemeToggle = findViewById<ImageView>(R.id.btnThemeToggle)
        val btnLanguageToggle = findViewById<TextView>(R.id.btnLanguageToggle)

        // الف) چک کردن حالتِ فعلی سیستم جهت قرار دادن آیکون صحیح تم (خورشید یا ماه)
        val currentMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        if (currentMode == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
            btnThemeToggle.setImageResource(R.drawable.ic_sun)
        } else {
            btnThemeToggle.setImageResource(R.drawable.ic_moon)
        }

        // ب) خواندن وضعیت پایدار زبان از SharedPrefs و تنظیم متن دکمه
        val sharedPreferences = getSharedPreferences("LocalAppPrefs", Context.MODE_PRIVATE)
        var currentLanguage = sharedPreferences.getString("APP_LANGUAGE", "fa") ?: "fa"

        if (currentLanguage == "fa") {
            btnLanguageToggle.text = "EN"
        } else {
            btnLanguageToggle.text = "فا"
        }

        // ۳. منطق کلیک دکمه تغییر تم سراسری
        btnThemeToggle.setOnClickListener {
            val themePrefs = getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)
            val editor = themePrefs.edit()
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

        // ۴. منطق کلیک دکمه تغییر زبان متنی کاملاً همسان
        btnLanguageToggle.setOnClickListener {
            val editor = sharedPreferences.edit()

            if (currentLanguage == "fa") {
                currentLanguage = "en"
                btnLanguageToggle.text = "فا"
                editor.putString("APP_LANGUAGE", "en")
                Toast.makeText(this, "Language changed to English (Simulation)", Toast.LENGTH_SHORT).show()
            } else {
                currentLanguage = "fa"
                btnLanguageToggle.text = "EN"
                editor.putString("APP_LANGUAGE", "fa")
                Toast.makeText(this, "زبان به فارسی تغییر یافت (شبیه‌سازی)", Toast.LENGTH_SHORT).show()
            }
            editor.apply()
        }

        // مدیریت متن راهنمای فارسی موقع کلیک روی نام کاربری
        etUsername.setOnFocusChangeListener { _, hasFocus ->
            etUsername.hint = if (hasFocus) "Enter Username" else ""
        }

        // مدیریت متن راهنمای فارسی موقع کلیک روی رمز عبور
        etPassword.setOnFocusChangeListener { _, hasFocus ->
            etPassword.hint = if (hasFocus) "Enter Password" else ""
        }

        // منطق دکمه ورود (ثابت و بدون تغییر)
        btnLogin.setOnClickListener {
            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()

            fun clearFields() {
                etUsername.text?.clear()
                etPassword.text?.clear()
                etUsername.clearFocus()
                etPassword.clearFocus()
            }

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "لطفاً تمام فیلدها را پر کنید", Toast.LENGTH_SHORT).show()
                clearFields()
                return@setOnClickListener
            }

            val adminUser = sharedPreferences.getString("ADMIN_USERNAME", "admin")
            val adminPass = sharedPreferences.getString("ADMIN_PASSWORD", "1234")

            val teacherUser = sharedPreferences.getString("TEACHER_USERNAME", "teacher")
            val teacherPass = sharedPreferences.getString("TEACHER_PASSWORD", "1234")

            val studentUser = sharedPreferences.getString("STUDENT_USERNAME", "student")
            val studentPass = sharedPreferences.getString("STUDENT_PASSWORD", "1234")

            val role = when {
                username == adminUser && password == adminPass -> "ADMIN"
                username == teacherUser && password == teacherPass -> "TEACHER"
                username == studentUser && password == studentPass -> "STUDENT"
                else -> null
            }

            if (role != null) {
                Toast.makeText(this, "ورود با موفقیت انجام شد", Toast.LENGTH_SHORT).show()

                val intent = Intent(this, MainActivity::class.java)
                intent.putExtra("USER_ROLE", role)
                intent.putExtra("USERNAME", username)
                startActivity(intent)
                finish()
            } else {
                Toast.makeText(this, "نام کاربری یا رمز عبور اشتباه است", Toast.LENGTH_SHORT).show()
                clearFields()
            }
        }
    }
}