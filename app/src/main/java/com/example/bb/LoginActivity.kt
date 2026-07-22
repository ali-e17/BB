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
import com.google.android.material.textfield.TextInputEditText
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("LocalAppPrefs", Context.MODE_PRIVATE)

        val savedToken = prefs.getString("API_TOKEN", "").orEmpty()
        if (prefs.getBoolean("IS_LOGGED_IN", false) && savedToken.isNotBlank()) {
            startActivity(Intent(this, MainActivity::class.java).apply {
                putExtra("USER_ROLE", prefs.getString("CURRENT_USER_ROLE", "STUDENT"))
                putExtra("USERNAME", prefs.getString("CURRENT_USERNAME", ""))
            })
            finish()
            return
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
            AppCompatDelegate.setDefaultNightMode(
                if (dark) AppCompatDelegate.MODE_NIGHT_NO else AppCompatDelegate.MODE_NIGHT_YES
            )
            getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)
                .edit().putBoolean("IS_DARK_MODE", !dark).apply()
        }
        btnLanguageToggle.setOnClickListener {
            language = if (language == "fa") "en" else "fa"
            prefs.edit().putString("APP_LANGUAGE", language).apply()
            btnLanguageToggle.text = if (language == "fa") "EN" else "فا"
        }

        btnLogin.setOnClickListener {
            val username = etUsername.text?.toString()?.trim().orEmpty()
            val password = etPassword.text?.toString().orEmpty()
            if (username.isBlank() || password.isBlank()) {
                Toast.makeText(this, "شماره تماس و رمز عبور را وارد کنید", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnLogin.isEnabled = false
            btnLogin.text = "در حال بررسی..."
            RetrofitClient.instance.login(LoginRequest(username, password))
                .enqueue(object : Callback<LoginResponse> {
                    override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                        btnLogin.isEnabled = true
                        btnLogin.text = "ورود"
                        val body = response.body()
                        if (response.isSuccessful && body?.status == "success") {
                            prefs.edit().apply {
                                putBoolean("IS_LOGGED_IN", true)
                                putString("CURRENT_USER_ROLE", body.role ?: "STUDENT")
                                putString("CURRENT_USERNAME", body.username ?: username)
                                putString("CURRENT_USER_ID", body.userId.orEmpty())
                                putString("CURRENT_DISPLAY_NAME", body.displayName ?: "کاربر")
                                putString("CURRENT_AVATAR_NAME", body.avatarName.orEmpty())
                                putString("API_TOKEN", body.token.orEmpty())
                                putString("API_TOKEN_EXPIRES_AT", body.tokenExpiresAt.orEmpty())
                                apply()
                            }
                            startActivity(Intent(this@LoginActivity, MainActivity::class.java).apply {
                                putExtra("USER_ROLE", body.role)
                                putExtra("USERNAME", body.username ?: username)
                            })
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

    private fun isDarkMode(): Boolean =
        resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES

    private fun updateThemeIcon(view: ImageView) {
        view.setImageResource(if (isDarkMode()) R.drawable.ic_sun else R.drawable.ic_moon)
    }
}
