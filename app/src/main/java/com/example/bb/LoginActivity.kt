package com.example.bb

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val etUsername = findViewById<EditText>(R.id.etUsername)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)

        btnLogin.setOnClickListener {
            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "لطفاً نام کاربری و رمز عبور را وارد کنید", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // تعیین نقش بر اساس یوزرنیمی که وارد میشه
            val role = when {
                username == "admin" && password == "1234" -> "ADMIN"
                username == "teacher" && password == "1234" -> "TEACHER"
                username == "student" && password == "1234" -> "STUDENT"
                else -> null
            }

            if (role != null) {
                Toast.makeText(this, "ورود موفقیت‌آمیز", Toast.LENGTH_SHORT).show()

                // ارسال اطلاعات به صفحه اصلی (کدهای تو)
                val intent = Intent(this, MainActivity::class.java)
                intent.putExtra("USER_ROLE", role)
                startActivity(intent)

                // بستن صفحه لاگین تا با دکمه بک (Back) برنگرده اینجا
                finish()
            } else {
                Toast.makeText(this, "نام کاربری یا رمز عبور اشتباه است", Toast.LENGTH_SHORT).show()
            }
        }
    }
}