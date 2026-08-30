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
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
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

        /*
         * ترتیب ثابت کیبورد:
         * نام کاربری -> رمز عبور -> ورود
         */
        etUsername.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                etPassword.requestFocus()
                ensureFocusedFieldVisible(etPassword)
                true
            } else {
                false
            }
        }

        etPassword.setOnEditorActionListener { view, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val imm =
                    getSystemService(
                        Context.INPUT_METHOD_SERVICE
                    ) as InputMethodManager

                imm.hideSoftInputFromWindow(
                    view.windowToken,
                    0
                )

                btnLogin.performClick()
                true
            } else {
                false
            }
        }

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
            etUsername.error = null
            etPassword.error = null

            val username = normalizeDigits(etUsername.text?.toString().orEmpty())
                .filter { it in '0'..'9' }
            val password = etPassword.text?.toString().orEmpty()

            when {
                username.isBlank() -> {
                    etUsername.error = "شناسه ورود را وارد کنید"
                    etUsername.requestFocus()
                    AppToast.warning(
                        this,
                        "برای ورود، کد ملی یا شناسه ۱۲ رقمی اتباع را وارد کنید"
                    )
                    return@setOnClickListener
                }

                username.length != 10 && username.length != 12 -> {
                    etUsername.error = "شناسه ورود باید ۱۰ یا ۱۲ رقم باشد"
                    etUsername.requestFocus()
                    AppToast.warning(
                        this,
                        "شناسه ورود باید ۱۰ رقم برای کد ملی یا ۱۲ رقم برای اتباع باشد"
                    )
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
            RetrofitClient.instance.login(LoginRequest(username, password))
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
                                putString("CURRENT_USERNAME", body.username ?: username)
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
                                this@LoginActivity.applicationContext,
                                body?.message?.takeIf { it.isNotBlank() }
                                    ?: ApiErrorParser.userMessage(response, "ورود به حساب کامل نشد؛ لطفاً شناسه ورود و رمز عبور را بررسی کنید."),
                                Toast.LENGTH_LONG
                            ).show()
                            etPassword.text?.clear()
                        }
                    }

                    override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                        btnLogin.isEnabled = true
                        btnLogin.text = "ورود"
                        AppToast.makeText(
                            this@LoginActivity.applicationContext,
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
    override fun onRemoteConfigChanged(config: AppRemoteConfig) {
        // Auto-login can finish this Activity before activity_login is inflated.
        // In that path there is no UI to update and touching footer views would crash.
        val welcome = findViewById<TextView?>(R.id.tvWelcome) ?: return
        welcome.text = config.schoolNameFa
        findViewById<ImageView>(R.id.imgLogo)?.let {
            RemoteConfigManager.applyCachedLogo(it, R.drawable.final50cm)
            it.contentDescription = "لوگوی ${config.schoolShortNameFa}"
        }
        setupContactFooter()
    }

    private fun setupContactFooter() {
        val config = RemoteConfigManager.current().contact

        val titleText = findViewById<TextView>(R.id.tvContactTitle)
        val phoneText = findViewById<TextView>(R.id.tvContactPhone)
        val eitaaText = findViewById<TextView>(R.id.tvContactEitaa)
        val addressText = findViewById<TextView>(R.id.tvContactAddress)

        val phoneLayout = findViewById<LinearLayout>(R.id.layoutContactPhone)
        val eitaaLayout = findViewById<LinearLayout>(R.id.layoutContactEitaa)
        val addressLayout = findViewById<LinearLayout>(R.id.layoutContactAddress)

        titleText.text = config.title
        val phoneDisplay = toPersianDigits(config.phoneDisplay.ifBlank { config.phone })
        // شماره داخل متن فارسی با LRM ایزوله می‌شود تا خط تیره و پیش‌شماره
        // تحت الگوریتم BiDi جابه‌جا نشوند. فقط خود شماره LTR می‌ماند.
        phoneText.text = "${config.phoneLabel} : \u200E${phoneDisplay}\u200E"
        eitaaText.text = "${config.eitaaLabel} : ${toPersianDigits(config.eitaaNumber)}"
        addressText.text = config.addressText.ifBlank { config.addressLabel }

        phoneLayout.visibility = if (config.phone.isBlank()) android.view.View.GONE else android.view.View.VISIBLE
        eitaaLayout.visibility = if (config.eitaaNumber.isBlank() && config.eitaaUrl.isBlank()) android.view.View.GONE else android.view.View.VISIBLE
        addressLayout.visibility = if (config.addressUrl.isBlank() && config.addressText.isBlank()) android.view.View.GONE else android.view.View.VISIBLE

        phoneLayout.setOnClickListener {
            val phone = RemoteConfigManager.current().contact.phone.trim()
            if (phone.isNotBlank()) {
                runCatching {
                    startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
                }.onFailure {
                    AppToast.warning(this, "برنامه تماس در دسترس نیست")
                }
            }
        }

        eitaaLayout.setOnClickListener { openEitaa() }
        addressLayout.setOnClickListener { openSchoolAddress() }
    }

    private fun openSchoolAddress() {
        val addressUrl = RemoteConfigManager.current().contact.addressUrl.trim()
        if (addressUrl.isBlank()) {
            AppToast.warning(this, "نشانی آموزشگاه ثبت نشده است")
            return
        }
        openNeshanOrBrowser(addressUrl)
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
        val contact = RemoteConfigManager.current().contact
        val number = contact.eitaaNumber.trim()

        if (number.isNotBlank()) {
            try {
                val eitaaIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("eitaa://chat/$number")
                ).apply {
                    setPackage("ir.eitaa.messenger")
                }
                startActivity(eitaaIntent)
                return
            } catch (_: Exception) {
                // Fall through to the server-controlled web URL.
            }
        }

        val fallbackUrl = contact.eitaaUrl.trim()
        if (fallbackUrl.isBlank()) {
            AppToast.warning(this, "لینک ایتا ثبت نشده است")
            return
        }

        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl)))
        }.onFailure {
            AppToast.warning(this, "باز کردن لینک ایتا انجام نشد")
        }
    }

}