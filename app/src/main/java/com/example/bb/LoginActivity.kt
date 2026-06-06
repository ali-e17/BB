package com.example.bb

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val etUsername = findViewById<TextInputEditText>(R.id.etUsername)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)

        // مدیریت متن راهنما موقع کلیک روی نام کاربری
        etUsername.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                etUsername.hint = "Enter your username"
            } else {
                etUsername.hint = ""
            }
        }

        // مدیریت متن راهنما موقع کلیک روی رمز عبور
        etPassword.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                etPassword.hint = "Enter your password"
            } else {
                etPassword.hint = ""
            }
        }

        btnLogin.setOnClickListener {
            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()

            // تابع پاک‌سازی فیلدها و رفرش فرم در زمان خطا
            fun clearFields() {
                etUsername.text?.clear()
                etPassword.text?.clear()
                etUsername.clearFocus()
                etPassword.clearFocus()
            }

            // خطا در صورت پر نبودن فیلدها
            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                clearFields()
                return@setOnClickListener
            }

            val role = when (username) {
                "admin" if password == "1234" -> "ADMIN"
                "teacher" if password == "1234" -> "TEACHER"
                "student" if password == "1234" -> "STUDENT"
                else -> null
            }

            // بررسی صحت اطلاعات ورود
            if (role != null) {
                Toast.makeText(this, "Login successful", Toast.LENGTH_SHORT).show()

                val intent = Intent(this, MainActivity::class.java)
                intent.putExtra("USER_ROLE", role)
                startActivity(intent)
                finish()
            } else {
                Toast.makeText(this, "Invalid username or password", Toast.LENGTH_SHORT).show()
                clearFields()
            }
        }
    }
}