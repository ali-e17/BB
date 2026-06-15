package com.example.bb

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText

class UpdateProfileActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_update_profile)

        val etNewUsername = findViewById<TextInputEditText>(R.id.etNewUsername)
        val etOldPassword = findViewById<TextInputEditText>(R.id.etOldPassword)
        val etNewPassword = findViewById<TextInputEditText>(R.id.etNewPassword)
        val etConfirmPassword = findViewById<TextInputEditText>(R.id.etConfirmPassword)

        val btnUpdateUsername = findViewById<Button>(R.id.btnUpdateUsername)
        val btnUpdatePassword = findViewById<Button>(R.id.btnUpdatePassword)

        val userRole = (intent.getStringExtra("USER_ROLE") ?: "STUDENT").uppercase()
        val sharedPreferences = getSharedPreferences("LocalAppPrefs", Context.MODE_PRIVATE)

        // ۱. مدیریت کلیک دکمه تغییر نام کاربری
        btnUpdateUsername.setOnClickListener {
            val newUsername = etNewUsername.text.toString().trim()

            if (newUsername.isEmpty()) {
                Toast.makeText(this, "لطفاً نام کاربری جدید را وارد کنید", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val editor = sharedPreferences.edit()
            editor.putString("${userRole}_USERNAME", newUsername)
            editor.apply()

            Toast.makeText(this, "نام کاربری با موفقیت تغییر کرد", Toast.LENGTH_SHORT).show()
            etNewUsername.text?.clear()
            etNewUsername.clearFocus()
        }

        // ۲. مدیریت کلیک دکمه تغییر رمز عبور (همراه با تاییدیه رمز قدیمی)
        btnUpdatePassword.setOnClickListener {
            val oldPassword = etOldPassword.text.toString().trim()
            val newPassword = etNewPassword.text.toString().trim()
            val confirmPassword = etConfirmPassword.text.toString().trim()

            // بررسی خالی نبودن فیلدها
            if (oldPassword.isEmpty() || newPassword.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, "لطفاً تمام فیلدهای مربوط به رمز عبور را پر کنید", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // گرفتن رمز عبور فعلی ذخیره شده در سیستم (با مقادیر پیش‌فرض در صورت عدم تغییر قبلی)
            val defaultPassword = "1234"
            val currentSavedPassword = sharedPreferences.getString("${userRole}_PASSWORD", defaultPassword)

            // الف) بررسی صحت رمز عبور قدیمی
            if (oldPassword != currentSavedPassword) {
                Toast.makeText(this, "رمز عبور فعلی وارد شده اشتباه است!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // ب) بررسی همخوانی رمز جدید و تکرار آن
            if (newPassword != confirmPassword) {
                Toast.makeText(this, "رمز عبور جدید و تکرار آن همخوانی ندارند!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // ذخیره‌سازی رمز عبور جدید
            val editor = sharedPreferences.edit()
            editor.putString("${userRole}_PASSWORD", newPassword)
            editor.apply()

            Toast.makeText(this, "رمز عبور با موفقیت تغییر کرد", Toast.LENGTH_SHORT).show()

            // پاک کردن فیلدها پس از موفقیت
            etOldPassword.text?.clear()
            etNewPassword.text?.clear()
            etConfirmPassword.text?.clear()
            etOldPassword.clearFocus()
            etNewPassword.clearFocus()
            etConfirmPassword.clearFocus()
        }
    }
}