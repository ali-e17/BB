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
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sharedPreferences = getSharedPreferences("LocalAppPrefs", Context.MODE_PRIVATE)

        // ورود خودکار فقط زمانی معتبر است که Token سرور نیز ذخیره شده باشد.
        val savedToken = sharedPreferences.getString("API_TOKEN", "").orEmpty()
        if (sharedPreferences.getBoolean("IS_LOGGED_IN", false) && savedToken.isNotBlank()) {
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

            // غیرفعال کردن دکمه برای جلوگیری از چندبار کلیک کردن
            btnLogin.isEnabled = false
            btnLogin.text = "در حال بررسی..."

            // 🌐 درخواست لاگین از سرور
            val request = LoginRequest(username, password)
            RetrofitClient.instance.login(request).enqueue(object : Callback<LoginResponse> {
                override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                    btnLogin.isEnabled = true
                    btnLogin.text = "ENTER"

                    val body = response.body()
                    if (response.isSuccessful && body?.status == "success") {
                        Toast.makeText(this@LoginActivity, "ورود موفق", Toast.LENGTH_SHORT).show()

                        sharedPreferences.edit().apply {
                            putBoolean("IS_LOGGED_IN", true)
                            putString("CURRENT_USER_ROLE", body.role ?: "STUDENT")
                            putString("CURRENT_USERNAME", body.username ?: username)
                            putString("CURRENT_USER_ID", body.userId ?: "")
                            putString("CURRENT_DISPLAY_NAME", body.displayName ?: "کاربر")
                            putString("API_TOKEN", body.token.orEmpty())
                            putString("API_TOKEN_EXPIRES_AT", body.tokenExpiresAt.orEmpty())
                            apply()
                        }

                        val intent = Intent(this@LoginActivity, MainActivity::class.java)
                        intent.putExtra("USER_ROLE", body.role)
                        intent.putExtra("USERNAME", body.username ?: username)
                        startActivity(intent)
                        finish()
                    } else {
                        Toast.makeText(this@LoginActivity, "شماره تلفن یا رمز عبور اشتباه است", Toast.LENGTH_SHORT).show()
                        etPassword.text?.clear()
                    }
                }

                override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                    btnLogin.isEnabled = true
                    btnLogin.text = "ENTER"
                    Toast.makeText(this@LoginActivity, "خطا در اتصال به سرور", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }
}