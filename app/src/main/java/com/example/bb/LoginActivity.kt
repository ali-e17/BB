package com.example.bb

import android.widget.TextView
import android.widget.LinearLayout
import android.net.Uri
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen // 🌟 اینپورت لایبرری اسپلش
import com.google.android.material.textfield.TextInputEditText
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LoginActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // 🌟 مرحله ۱: خواندن تم ذخیره شده در اپلیکیشن و اعمال آن قبل از هر چیز
        val themePrefs = getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)
        if (themePrefs.contains("IS_DARK_MODE")) {
            val isDarkModeSaved = themePrefs.getBoolean("IS_DARK_MODE", false)
            AppCompatDelegate.setDefaultNightMode(
                if (isDarkModeSaved) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
        }

        // 🌟 مرحله ۲: فعال‌سازی اسپلش اسکرین
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("LocalAppPrefs", Context.MODE_PRIVATE)

        // V3 adds a server-controlled initial-payment gate. Requiring one fresh
        // login prevents a stale V2 local session from bypassing the new route.
        if (prefs.getInt("AUTH_SCHEMA_VERSION", 0) < 3) {
            clearExpiredSession()
            prefs.edit().putInt("AUTH_SCHEMA_VERSION", 3).apply()
        }

        val savedToken = prefs.getString("API_TOKEN", "").orEmpty()
        val hasSession = prefs.getBoolean("IS_LOGGED_IN", false) &&
                savedToken.isNotBlank() && !isTokenExpired(prefs.getString("API_TOKEN_EXPIRES_AT", null))

        if (hasSession) {
            when {
                prefs.getBoolean("PAYMENT_REQUIRED", false) -> openInitialPayment()
                prefs.getBoolean("MUST_CHANGE_PASSWORD", false) -> openForcedPasswordChange()
                else -> openMain()
            }
            finish()
            return
        } else if (savedToken.isNotBlank()) {
            clearExpiredSession()
        }

        setContentView(R.layout.activity_login)
        setupContactFooter()
        val etUsername = findViewById<TextInputEditText>(R.id.etUsername)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnThemeToggle = findViewById<ImageView>(R.id.btnThemeToggle)

        updateThemeIcon(btnThemeToggle)

        btnThemeToggle.setOnClickListener {
            val dark = isDarkMode()
            getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("IS_DARK_MODE", !dark)
                .apply()
            AppToast.info(
                applicationContext,
                if (dark) "حالت روشن فعال شد" else "حالت تاریک فعال شد"
            )
            AppCompatDelegate.setDefaultNightMode(
                if (dark) AppCompatDelegate.MODE_NIGHT_NO else AppCompatDelegate.MODE_NIGHT_YES
            )
        }

        btnLogin.setOnClickListener {
            val nationalId = normalizeDigits(etUsername.text?.toString().orEmpty())
                .filter(Char::isDigit)
            val password = etPassword.text?.toString().orEmpty()

            when {
                nationalId.isBlank() -> {
                    etUsername.error = "کد ملی را وارد کنید"
                    etUsername.requestFocus()
                    AppToast.warning(this, "برای ورود، کد ملی خود را وارد کنید")
                    return@setOnClickListener
                }
                nationalId.length != 10 -> {
                    etUsername.error = "کد ملی باید ۱۰ رقم باشد"
                    etUsername.requestFocus()
                    AppToast.warning(this, "کد ملی باید دقیقاً ۱۰ رقم باشد")
                    return@setOnClickListener
                }
                password.isBlank() -> {
                    etPassword.error = "رمز عبور را وارد کنید"
                    etPassword.requestFocus()
                    AppToast.warning(this, "برای ورود، رمز عبور را وارد کنید")
                    return@setOnClickListener
                }
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
                                putString("INITIAL_ACCESS_STATUS", body.initialAccessStatus)
                                putBoolean("PAYMENT_REQUIRED", body.paymentRequired)
                                putInt("AUTH_SCHEMA_VERSION", 3)
                                apply()
                            }

                            when {
                                body.paymentRequired -> openInitialPayment()
                                body.mustChangePassword -> openForcedPasswordChange()
                                else -> openMain()
                            }
                            finish()
                        } else {
                            AppToast.makeText(
                                this@LoginActivity,
                                body?.message?.takeIf { it.isNotBlank() }
                                    ?: ApiErrorParser.userMessage(response, "ورود به حساب کامل نشد؛ لطفاً کد ملی و رمز عبور را بررسی کنید."),
                                Toast.LENGTH_LONG
                            ).show()
                            etPassword.text?.clear()
                        }
                    }

                    override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                        btnLogin.isEnabled = true
                        btnLogin.text = "ورود"
                        AppToast.makeText(
                            this@LoginActivity,
                            ApiErrorParser.networkMessage(t, "ورود به حساب"),
                            Toast.LENGTH_LONG
                        ).show()
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
        startActivity(Intent(this, UpdateProfileActivity::class.java).apply {
            putExtra(UpdateProfileActivity.EXTRA_FORCE_PASSWORD_CHANGE, true)
            putExtra(UpdateProfileActivity.EXTRA_OPEN_MAIN_AFTER_CHANGE, true)
        })
    }

    private fun openInitialPayment() {
        startActivity(Intent(this, InitialPaymentActivity::class.java))
    }

    private fun clearExpiredSession() {
        getSharedPreferences("LocalAppPrefs", Context.MODE_PRIVATE).edit().apply {
            remove("IS_LOGGED_IN")
            remove("API_TOKEN")
            remove("API_TOKEN_EXPIRES_AT")
            remove("MUST_CHANGE_PASSWORD")
            remove("PAYMENT_REQUIRED")
            remove("INITIAL_ACCESS_STATUS")
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
    private fun setupContactFooter() {

        val phoneText = findViewById<TextView>(R.id.tvContactPhone)
        val eitaaText = findViewById<TextView>(R.id.tvContactEitaa)

        val phoneLayout = findViewById<LinearLayout>(R.id.layoutContactPhone)
        val eitaaLayout = findViewById<LinearLayout>(R.id.layoutContactEitaa)
        val addressLayout = findViewById<LinearLayout>(R.id.layoutContactAddress)

        phoneText.text =
            "تماس با آموزشگاه : ${toPersianDigits(ContactConfig.PHONE_NUMBER)}"

        eitaaText.text =
            "ارتباط در ایتا : ${toPersianDigits(ContactConfig.EITAA_NUMBER)}"

        phoneLayout.setOnClickListener {
            val intent = Intent(
                Intent.ACTION_DIAL,
                Uri.parse("tel:${ContactConfig.PHONE_NUMBER}")
            )
            startActivity(intent)
        }

        eitaaLayout.setOnClickListener {
            openEitaa()
        }

        addressLayout.setOnClickListener {
            openSchoolAddress()
        }
    }

    private fun openSchoolAddress() {
        RetrofitClient.instance.getContactInfo()
            .enqueue(object : Callback<ContactInfoResponse> {

                override fun onResponse(
                    call: Call<ContactInfoResponse>,
                    response: Response<ContactInfoResponse>
                ) {
                    val body = response.body()
                    val addressUrl = body?.addressUrl?.trim().orEmpty()

                    if (!response.isSuccessful ||
                        body == null ||
                        (body.status.isNotBlank() && body.status != "success") ||
                        addressUrl.isBlank()
                    ) {
                        AppToast.makeText(
                            this@LoginActivity,
                            body?.message?.takeIf { it.isNotBlank() }
                                ?: ApiErrorParser.userMessage(response, "دریافت نشانی آموزشگاه کامل نشد"),
                            Toast.LENGTH_LONG
                        ).show()
                        return
                    }

                    openNeshanOrBrowser(addressUrl)
                }

                override fun onFailure(
                    call: Call<ContactInfoResponse>,
                    t: Throwable
                ) {
                    AppToast.makeText(
                        this@LoginActivity,
                        ApiErrorParser.networkMessage(t, "دریافت نشانی آموزشگاه"),
                        Toast.LENGTH_LONG
                    ).show()
                }
            })
    }

    private fun openNeshanOrBrowser(url: String) {
        val uri = runCatching { Uri.parse(url) }.getOrNull()

        if (uri == null || (uri.scheme != "http" && uri.scheme != "https")) {
            AppToast.makeText(
                this,
                "نشانی ثبت‌شده آموزشگاه معتبر نیست",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        try {
            val neshanIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("org.rajman.neshan.traffic.tehran.navigator")
            }
            startActivity(neshanIntent)
        } catch (_: Exception) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            } catch (_: Exception) {
                AppToast.makeText(
                    this,
                    "برنامه مناسبی برای باز کردن نشانی آموزشگاه در دسترس نیست؛ لطفاً مرورگر یا برنامه نقشه را بررسی کنید",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun toPersianDigits(value: String): String {
        return value
            .replace('0', '۰')
            .replace('1', '۱')
            .replace('2', '۲')
            .replace('3', '۳')
            .replace('4', '۴')
            .replace('5', '۵')
            .replace('6', '۶')
            .replace('7', '۷')
            .replace('8', '۸')
            .replace('9', '۹')
    }

    private fun openEitaa() {

        val eitaaNumber = ContactConfig.EITAA_NUMBER

        try {

            val eitaaIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("eitaa://chat/$eitaaNumber")
            )

            eitaaIntent.setPackage("ir.eitaa.messenger")

            startActivity(eitaaIntent)

        } catch (e: Exception) {

            val browserIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://eitaa.com/$eitaaNumber")
            )

            startActivity(browserIntent)
        }
    }
}