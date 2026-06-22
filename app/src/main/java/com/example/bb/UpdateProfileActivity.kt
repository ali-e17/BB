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

        val etOldPassword = findViewById<TextInputEditText>(R.id.etOldPassword)
        val etNewPassword = findViewById<TextInputEditText>(R.id.etNewPassword)
        val etConfirmPassword = findViewById<TextInputEditText>(R.id.etConfirmPassword)
        val btnUpdatePassword = findViewById<Button>(R.id.btnUpdatePassword)

        val userRole = (intent.getStringExtra("USER_ROLE") ?: "STUDENT").uppercase()
        val sharedPreferences = getSharedPreferences("LocalAppPrefs", Context.MODE_PRIVATE)

        btnUpdatePassword.setOnClickListener {
            val oldPassword = etOldPassword.text.toString().trim()
            val newPassword = etNewPassword.text.toString().trim()
            val confirmPassword = etConfirmPassword.text.toString().trim()

            if (oldPassword.isEmpty() || newPassword.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, "لطفاً تمام فیلدهای مربوط به رمز عبور را پر کنید", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // خواندن رمز عبور فعلی کاربر
            val currentUsername = sharedPreferences.getString("CURRENT_USERNAME", "") ?: ""
            val currentSavedPassword = if (userRole == "STUDENT") {
                AppDatabase.getStudentByUsername(currentUsername)?.password ?: "1234"
            } else {
                sharedPreferences.getString("${userRole}_PASSWORD", "1234") ?: "1234"
            }

            if (oldPassword != currentSavedPassword) {
                Toast.makeText(this, "رمز عبور فعلی وارد شده اشتباه است!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (newPassword != confirmPassword) {
                Toast.makeText(this, "رمز عبور جدید و تکرار آن همخوانی ندارند!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // ذخیره رمز جدید
            if (userRole == "STUDENT") {
                AppDatabase.updateStudentPassword(currentUsername, newPassword, this)
            } else {
                sharedPreferences.edit().putString("${userRole}_PASSWORD", newPassword).apply()
            }

            Toast.makeText(this, "رمز عبور با موفقیت تغییر کرد", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}