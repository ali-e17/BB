package com.example.bb

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen // 🌟 اینپورت لایبرری اسپلش
import com.google.android.material.textfield.TextInputEditText
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // 🌟 مرحله ۱: خواندن تم ذخیره شده در اپلیکیشن و اعمال آن قبل از هر چیز
        val themePrefs = getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)
        if (themePrefs.contains("IS_DARK_MODE")) {
            val isDarkModeSaved = themePrefs.getBoolean("IS_DARK_MODE", false)
            AppCompatDelegate.setDefaultNightMode(
                if (isDarkModeSaved) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
        }

        // 🌟 مرحله ۲: فعال‌سازی اسپلش اسکرین (حالا با تم تنظیم شده در بالا هماهنگ است)
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("LocalAppPrefs", Context.MODE_PRIVATE)

        // Phase-1 auth changed the login identifier from phone to national ID.
        // Old phone-based tokens are intentionally discarded once after app update.
        if (prefs.getInt("AUTH_SCHEMA_VERSION", 0) < 2) {
            clearExpiredSession()
            prefs.edit().putInt("AUTH_SCHEMA_VERSION", 2).apply()
        }

        val savedToken = prefs.getString("API_TOKEN", "").orEmpty()
        val hasSession = prefs.getBoolean("IS_LOGGED_IN", false) &&
                savedToken.isNotBlank() && !isTokenExpired(prefs.getString("API_TOKEN_EXPIRES_AT", null))

        if (hasSession) {
            if (prefs.getBoolean("MUST_CHANGE_PASSWORD", false)) {
                openForcedPasswordChange()
            } else {
                openMain()
            }
            finish()
            return
        } else if (savedToken.isNotBlank()) {
            clearExpiredSession()
        }

        setContentView(R.layout.activity_login)
        val etUsername = findViewById<TextInputEditText>(R.id.etUsername)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnThemeToggle = findViewById<ImageView>(R.id.btnThemeToggle)
        val btnLanguageToggle = findViewById<TextView>(R.id.btnLanguageToggle)

        updateThemeIcon(btnThemeToggle)
        var language = prefs.getString("APP_LANGUAGE", "fa") ?: "fa"
        btnLanguageToggle.text = if (language == "fa") "EN" else "فا"

        btnThemeToggle.setOnClickListener {
            val dark = isDarkMode()
            getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("IS_DARK_MODE", !dark)
                .apply()
            AppCompatDelegate.setDefaultNightMode(
                if (dark) AppCompatDelegate.MODE_NIGHT_NO else AppCompatDelegate.MODE_NIGHT_YES
            )
        }

        btnLanguageToggle.setOnClickListener {
            language = if (language == "fa") "en" else "fa"
            prefs.edit().putString("APP_LANGUAGE", language).apply()
            btnLanguageToggle.text = if (language == "fa") "EN" else "فا"
        }

        btnLogin.setOnClickListener {
            val nationalId = normalizeDigits(etUsername.text?.toString().orEmpty())
                .filter(Char::isDigit)
            val password = etPassword.text?.toString().orEmpty()

            if (nationalId.length != 10 || password.isBlank()) {
                Toast.makeText(this, "کد ملی ۱۰ رقمی و رمز عبور را وارد کنید", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnLogin.isEnabled = false
            btnLogin.text = "در حال بررسی..."
            RetrofitClient.instance.login(LoginRequest(nationalId, password))
                .enqueue(object : Callback<LoginResponse> {
                    override fun onResponse(
                        call: Call<LoginResponse>,
                        response: Response<LoginResponse>
                    ) {
                        btnLogin.isEnabled = true
                        btnLogin.text = "ورود"
                        val body = response.body()

                        if (response.isSuccessful && body?.status == "success") {
                            prefs.edit().apply {
                                putBoolean("IS_LOGGED_IN", true)
                                putString("CURRENT_USER_ROLE", body.role ?: "STUDENT")
                                putString("CURRENT_USERNAME", body.username ?: nationalId)
                                putString("CURRENT_PHONE", body.phone.orEmpty())
                                putString("CURRENT_USER_ID", body.userId.orEmpty())
                                putString("CURRENT_DISPLAY_NAME", body.displayName ?: "کاربر")
                                putString("CURRENT_AVATAR_NAME", body.avatarName.orEmpty())
                                putString("API_TOKEN", body.token.orEmpty())
                                putString("API_TOKEN_EXPIRES_AT", body.tokenExpiresAt.orEmpty())
                                putBoolean("MUST_CHANGE_PASSWORD", body.mustChangePassword)
                                putInt("AUTH_SCHEMA_VERSION", 2)
                                apply()
                            }

                            if (body.mustChangePassword) {
                                openForcedPasswordChange()
                            } else {
                                openMain()
                            }
                            finish()
                        } else {
                            Toast.makeText(
                                this@LoginActivity,
                                body?.message ?: "ورود انجام نشد (کد ${response.code()})",
                                Toast.LENGTH_LONG
                            ).show()
                            etPassword.text?.clear()
                        }
                    }

                    override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                        btnLogin.isEnabled = true
                        btnLogin.text = "ورود"
                        Toast.makeText(this@LoginActivity, "خطا در اتصال به سرور", Toast.LENGTH_LONG).show()
                    }
                })
        }
    }

    private fun openMain() {
        val prefs = getSharedPreferences("LocalAppPrefs", Context.MODE_PRIVATE)
        startActivity(Intent(this, MainActivity::class.java).apply {
            putExtra("USER_ROLE", prefs.getString("CURRENT_USER_ROLE", "STUDENT"))
        })
    }

    private fun openForcedPasswordChange() {
        startActivity(Intent(this, ForceChangePasswordActivity::class.java))
    }

    private fun clearExpiredSession() {
        getSharedPreferences("LocalAppPrefs", Context.MODE_PRIVATE).edit().apply {
            remove("IS_LOGGED_IN")
            remove("API_TOKEN")
            remove("API_TOKEN_EXPIRES_AT")
            remove("MUST_CHANGE_PASSWORD")
            apply()
        }
    }

    private fun isTokenExpired(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return true
        return runCatching {
            val parsed = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(raw)
            parsed == null || parsed.before(Date())
        }.getOrDefault(true)
    }

    private fun normalizeDigits(value: String): String = value
        .replace('۰', '0').replace('۱', '1').replace('۲', '2').replace('۳', '3')
        .replace('۴', '4').replace('۵', '5').replace('۶', '6').replace('۷', '7')
        .replace('۸', '8').replace('۹', '9')
        .replace('٠', '0').replace('١', '1').replace('٢', '2').replace('٣', '3')
        .replace('٤', '4').replace('٥', '5').replace('٦', '6').replace('٧', '7')
        .replace('٨', '8').replace('٩', '9')

    private fun isDarkMode(): Boolean =
        resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES

    private fun updateThemeIcon(view: ImageView) {
        view.setImageResource(if (isDarkMode()) R.drawable.ic_sun else R.drawable.ic_moon)
    }
}