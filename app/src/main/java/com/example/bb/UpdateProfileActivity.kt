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

            val currentUsername = sharedPreferences.getString("CURRENT_USERNAME", "") ?: ""
            if (newPassword != confirmPassword) {
                Toast.makeText(this, "رمز عبور جدید و تکرار آن همخوانی ندارند!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (newPassword.length < 4) {
                Toast.makeText(this, "رمز جدید باید حداقل ۴ کاراکتر باشد", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val role = runCatching { UserRole.valueOf(userRole) }.getOrDefault(UserRole.STUDENT)
            if (!AppDatabase.updatePassword(role, currentUsername, oldPassword, newPassword)) {
                Toast.makeText(this, "رمز عبور فعلی اشتباه است", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            Toast.makeText(this, "رمز عبور با موفقیت تغییر کرد", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
