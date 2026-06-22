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
        val sharedPreferences = getSharedPreferences("LocalAppPrefs", Context.MODE_PRIVATE)

        if (sharedPreferences.getBoolean("IS_LOGGED_IN", false)) {
            val savedRole = sharedPreferences.getString("CURRENT_USER_ROLE", "STUDENT") ?: "STUDENT"
            val savedUsername = sharedPreferences.getString("CURRENT_USERNAME", "student") ?: "student"

            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("USER_ROLE", savedRole)
            intent.putExtra("USERNAME", savedUsername)
            startActivity(intent)
            finish()
            return
        }

        setContentView(R.layout.activity_login)

        val etUsername = findViewById<TextInputEditText>(R.id.etUsername)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnThemeToggle = findViewById<ImageView>(R.id.btnThemeToggle)
        val btnLanguageToggle = findViewById<TextView>(R.id.btnLanguageToggle)

        val currentMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        if (currentMode == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
            btnThemeToggle.setImageResource(R.drawable.ic_sun)
        } else {
            btnThemeToggle.setImageResource(R.drawable.ic_moon)
        }

        var currentLanguage = sharedPreferences.getString("APP_LANGUAGE", "fa") ?: "fa"
        btnLanguageToggle.text = if (currentLanguage == "fa") "EN" else "فا"

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

        btnLanguageToggle.setOnClickListener {
            val editor = sharedPreferences.edit()
            if (currentLanguage == "fa") {
                currentLanguage = "en"
                btnLanguageToggle.text = "فا"
                editor.putString("APP_LANGUAGE", "en")
            } else {
                currentLanguage = "fa"
                btnLanguageToggle.text = "EN"
                editor.putString("APP_LANGUAGE", "fa")
            }
            editor.apply()
        }

        btnLogin.setOnClickListener {
            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "لطفاً تمام فیلدها را پر کنید", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val adminUser = sharedPreferences.getString("ADMIN_USERNAME", "admin")
            val adminPass = sharedPreferences.getString("ADMIN_PASSWORD", "1234")
            val teacherUser = sharedPreferences.getString("TEACHER_USERNAME", "teacher")
            val teacherPass = sharedPreferences.getString("TEACHER_PASSWORD", "1234")

            // دریافت کاربر از دیتابیس پویا
            val student = AppDatabase.getStudentByUsername(username)

            val role = when {
                username == adminUser && password == adminPass -> "ADMIN"
                username == teacherUser && password == teacherPass -> "TEACHER"
                student != null && student.password == password -> "STUDENT"
                else -> null
            }

            if (role != null) {
                Toast.makeText(this, "ورود موفق", Toast.LENGTH_SHORT).show()
                sharedPreferences.edit().apply {
                    putBoolean("IS_LOGGED_IN", true)
                    putString("CURRENT_USER_ROLE", role)
                    putString("CURRENT_USERNAME", username)
                    apply()
                }

                val intent = Intent(this, MainActivity::class.java)
                intent.putExtra("USER_ROLE", role)
                intent.putExtra("USERNAME", username)
                startActivity(intent)
                finish()
            } else {
                Toast.makeText(this, "نام کاربری یا رمز عبور اشتباه است", Toast.LENGTH_SHORT).show()
                etUsername.text?.clear()
                etPassword.text?.clear()
            }
        }
    }
}