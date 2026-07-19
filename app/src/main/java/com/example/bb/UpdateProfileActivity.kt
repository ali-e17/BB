package com.example.bb

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class UpdateProfileActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_update_profile)

        findViewById<ImageView>(R.id.btnUpdateProfileBack).setOnClickListener { finish() }

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

            // غیرفعال کردن دکمه حین ارتباط با سرور
            btnUpdatePassword.isEnabled = false
            btnUpdatePassword.text = "در حال ارتباط با سرور..."

            val request = UpdatePasswordRequest(
                role = userRole,
                phone = currentUsername,
                oldPassword = oldPassword,
                newPassword = newPassword
            )

            // 🌐 ارسال درخواست آنلاین به سرور
            RetrofitClient.instance.updatePassword(request).enqueue(object : Callback<ApiResponse> {
                override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                    btnUpdatePassword.isEnabled = true
                    btnUpdatePassword.text = "تغییر رمز عبور"

                    val body = response.body()
                    if (response.isSuccessful && body?.status == "success") {
                        // آپدیت همزمان در حافظه لوکال گوشی برای لاگین‌های آفلاین احتمالی
                        val roleEnum = runCatching { UserRole.valueOf(userRole) }.getOrDefault(UserRole.STUDENT)
                        AppDatabase.updatePassword(roleEnum, currentUsername, oldPassword, newPassword)

                        Toast.makeText(this@UpdateProfileActivity, "رمز عبور با موفقیت تغییر کرد", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        // نمایش پیام خطای سرور (مثلاً رمز قبلی اشتباه است)
                        Toast.makeText(this@UpdateProfileActivity, body?.message ?: "خطا در تغییر رمز عبور", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                    btnUpdatePassword.isEnabled = true
                    btnUpdatePassword.text = "تغییر رمز عبور"
                    Toast.makeText(this@UpdateProfileActivity, "خطا در اتصال به اینترنت", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }
}