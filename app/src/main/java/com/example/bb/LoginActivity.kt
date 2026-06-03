package com.example.BB

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.bb.R

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // اتصال به لایوت طراحی شده (activity_login.xml)
        setContentView(R.layout.activity_login)

        // پیدا کردن المان‌ها از داخل XML بر اساس ID
        val etUsername = findViewById<EditText>(R.id.etUsername)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)

        // تعریف رویداد کلیک برای دکمه
        btnLogin.setOnClickListener {
            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()

            // بررسی خالی بودن فیلدها
            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "لطفاً نام کاربری و رمز عبور را وارد کنید", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // منطق موقت برای بررسی ورود
            if (username == "admin" && password == "1234") {
                Toast.makeText(this, "خوش آمدید مدیر گرامی", Toast.LENGTH_SHORT).show()
                // در آینده: اینجا کد Intent برای رفتن به صفحه هوم اضافه میشه
            } else {
                Toast.makeText(this, "نام کاربری یا رمز عبور اشتباه است", Toast.LENGTH_SHORT).show()
            }
        }
    }
}