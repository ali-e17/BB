package com.example.bb

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.NumberFormat
import java.util.Locale

/**
 * Mandatory first-access payment screen for STUDENT accounts.
 *
 * Important: this Activity never decides that a payment is successful from a
 * deep-link parameter. The server is always queried and remains the source of
 * truth for the payment state.
 */
class InitialPaymentActivity : BaseActivity() {

    private lateinit var amountText: TextView
    private lateinit var statusText: TextView
    private lateinit var environmentText: TextView
    private lateinit var startButton: MaterialButton
    private lateinit var checkButton: MaterialButton
    private lateinit var progress: View

    private var gatewayOpened = false
    private var returnCheckScheduled = false
    private var navigatedAway = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_initial_payment)

        amountText = findViewById(R.id.tvInitialPaymentAmount)
        statusText = findViewById(R.id.tvInitialPaymentStatus)
        startButton = findViewById(R.id.btnStartInitialPayment)
        checkButton = findViewById(R.id.btnCheckInitialPayment)
        progress = findViewById(R.id.progressInitialPayment)

        findViewById<ImageView>(R.id.btnInitialPaymentBack).setOnClickListener { handleBack() }
        startButton.setOnClickListener { requestPayment() }
        checkButton.setOnClickListener { reconcilePayment(showLoading = true) }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = handleBack()
        })

        if (intent?.data?.scheme.equals("bbapp", ignoreCase = true)) {
            gatewayOpened = true
        }
        loadStatus(showLoading = true)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Never trust result/status fields from the URI. We only use the URI as
        // a signal to re-check the transaction on our backend.
        gatewayOpened = true
        reconcilePayment(showLoading = true)
    }

    override fun onResume() {
        super.onResume()
        if (gatewayOpened && !returnCheckScheduled && !navigatedAway) {
            returnCheckScheduled = true
            Handler(Looper.getMainLooper()).postDelayed({
                returnCheckScheduled = false
                if (!isFinishing && !navigatedAway) {
                    reconcilePayment(showLoading = false)
                }
            }, 900)
        }
    }

    private fun loadStatus(showLoading: Boolean) {
        if (showLoading) setLoading(true)
        RetrofitClient.instance.getInitialPaymentStatus()
            .enqueue(object : Callback<InitialPaymentStatusResponse> {
                override fun onResponse(
                    call: Call<InitialPaymentStatusResponse>,
                    response: Response<InitialPaymentStatusResponse>
                ) {
                    if (showLoading) setLoading(false)
                    val body = response.body()
                    if (response.isSuccessful && body?.status == "success") {
                        renderStatus(body)
                    } else {
                        showError(
                            body?.message?.takeIf { it.isNotBlank() }
                                ?: ApiErrorParser.userMessage(response, "وضعیت فعال‌سازی دریافت نشد")
                        )
                    }
                }

                override fun onFailure(call: Call<InitialPaymentStatusResponse>, t: Throwable) {
                    if (showLoading) setLoading(false)
                    showError(ApiErrorParser.networkMessage(t, "بررسی وضعیت پرداخت"))
                }
            })
    }

    private fun requestPayment() {
        setLoading(true)
        statusText.text = "در حال آماده‌سازی درگاه پرداخت..."
        RetrofitClient.instance.requestInitialPayment()
            .enqueue(object : Callback<InitialPaymentRequestResponse> {
                override fun onResponse(
                    call: Call<InitialPaymentRequestResponse>,
                    response: Response<InitialPaymentRequestResponse>
                ) {
                    setLoading(false)
                    val body = response.body()
                    if (!response.isSuccessful || body?.status != "success") {
                        showError(
                            body?.message?.takeIf { it.isNotBlank() }
                                ?: ApiErrorParser.userMessage(response, "درگاه پرداخت آماده نشد")
                        )
                        return
                    }

                    if (!body.paymentRequired) {
                        completeActivation(body.initialAccessStatus ?: "PAID")
                        return
                    }

                    renderAmount(body.amount, body.currency)
                    renderEnvironment(body.environment)
                    val paymentUrl = body.paymentUrl.orEmpty()
                    if (paymentUrl.isBlank()) {
                        showError("آدرس درگاه پرداخت دریافت نشد. دوباره تلاش کنید.")
                        return
                    }
                    openGateway(paymentUrl)
                }

                override fun onFailure(call: Call<InitialPaymentRequestResponse>, t: Throwable) {
                    setLoading(false)
                    showError(ApiErrorParser.networkMessage(t, "ایجاد درخواست پرداخت"))
                }
            })
    }

    private fun reconcilePayment(showLoading: Boolean) {
        if (showLoading) setLoading(true)
        if (!showLoading) statusText.text = "در حال بررسی نتیجه پرداخت..."

        RetrofitClient.instance.reconcileInitialPayment()
            .enqueue(object : Callback<InitialPaymentStatusResponse> {
                override fun onResponse(
                    call: Call<InitialPaymentStatusResponse>,
                    response: Response<InitialPaymentStatusResponse>
                ) {
                    if (showLoading) setLoading(false)
                    val body = response.body()
                    if (response.isSuccessful && body?.status == "success") {
                        gatewayOpened = false
                        renderStatus(body)
                    } else {
                        showError(
                            body?.message?.takeIf { it.isNotBlank() }
                                ?: ApiErrorParser.userMessage(response, "وضعیت پرداخت قابل بررسی نیست")
                        )
                    }
                }

                override fun onFailure(call: Call<InitialPaymentStatusResponse>, t: Throwable) {
                    if (showLoading) setLoading(false)
                    showError(ApiErrorParser.networkMessage(t, "بررسی نتیجه پرداخت"))
                }
            })
    }

    private fun renderStatus(body: InitialPaymentStatusResponse) {
        val tx = body.transaction
        val txIsActive = tx?.status.equals("PENDING", ignoreCase = true) ||
                tx?.status.equals("CREATED", ignoreCase = true)
        if (body.paymentRequired && txIsActive && tx != null) {
            // A pending transaction keeps its own amount snapshot even if the
            // administrator changes the configured fee while it is in progress.
            renderAmount(tx.amount, tx.currency)
            renderEnvironment(tx.environment)
        } else {
            renderAmount(body.amount, body.currency)
            renderEnvironment(body.environment)
        }

        val prefs = getSharedPreferences("LocalAppPrefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("INITIAL_ACCESS_STATUS", body.initialAccessStatus)
            .putBoolean("PAYMENT_REQUIRED", body.paymentRequired)
            .apply()

        if (!body.paymentRequired) {
            completeActivation(body.initialAccessStatus)
            return
        }

        startButton.isEnabled = true
        checkButton.isEnabled = true

        statusText.text = when (tx?.status?.uppercase(Locale.US)) {
            "PENDING" -> "درخواست پرداخت ایجاد شده است. پرداخت را کامل کنید؛ اگر برگشته‌اید، «بررسی وضعیت پرداخت» را بزنید."
            "CANCELED" -> "پرداخت قبلی لغو شده است. برای فعال‌سازی حساب می‌توانید دوباره تلاش کنید."
            "FAILED" -> "پرداخت قبلی ناموفق بود. دوباره تلاش کنید."
            "REVERSED" -> "پرداخت برگشت خورده است. برای فعال‌سازی حساب دوباره پرداخت کنید."
            else -> "برای اولین استفاده یا تمدید استفاده از حساب دانش‌آموز، فعال‌سازی اولیه لازم است."
        }
        startButton.text = if (tx?.status.equals("PENDING", ignoreCase = true)) {
            "ادامه پرداخت"
        } else {
            "پرداخت و فعال‌سازی"
        }
    }

    private fun renderAmount(amount: Long, currency: String) {
        val unit = if (currency.equals("IRR", ignoreCase = true)) "ریال" else "تومان"
        amountText.text = NumberFormat.getNumberInstance(Locale.US).format(amount) + " " + unit
    }

    private fun renderEnvironment(environment: String) {
        val sandbox = environment.equals("SANDBOX", ignoreCase = true)
        environmentText.visibility = if (sandbox) View.VISIBLE else View.GONE
        if (sandbox) {
            environmentText.text = "محیط آزمایشی زرین‌پال (Sandbox) — پرداخت واقعی انجام نمی‌شود"
        }
    }

    private fun openGateway(url: String) {
        val uri = runCatching { Uri.parse(url) }.getOrNull()
        if (uri == null || uri.scheme !in listOf("https", "http")) {
            showError("آدرس درگاه پرداخت معتبر نیست")
            return
        }
        gatewayOpened = true
        statusText.text = "درگاه پرداخت باز شد. پس از پایان پرداخت به برنامه برگردید."
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: Exception) {
            gatewayOpened = false
            showError("مرورگری برای باز کردن درگاه پرداخت پیدا نشد")
        }
    }

    private fun completeActivation(accessStatus: String) {
        if (navigatedAway) return
        navigatedAway = true

        val prefs = getSharedPreferences("LocalAppPrefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("PAYMENT_REQUIRED", false)
            .putString("INITIAL_ACCESS_STATUS", accessStatus)
            .apply()

        val mustChangePassword = prefs.getBoolean("MUST_CHANGE_PASSWORD", false)
        if (mustChangePassword) {
            startActivity(Intent(this, UpdateProfileActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra(UpdateProfileActivity.EXTRA_FORCE_PASSWORD_CHANGE, true)
                putExtra(UpdateProfileActivity.EXTRA_OPEN_MAIN_AFTER_CHANGE, true)
            })
        } else {
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("USER_ROLE", prefs.getString("CURRENT_USER_ROLE", "STUDENT"))
            })
        }
        finish()
    }

    private fun setLoading(loading: Boolean) {
        progress.visibility = if (loading) View.VISIBLE else View.GONE
        startButton.isEnabled = !loading
        checkButton.isEnabled = !loading
        startButton.alpha = if (loading) 0.65f else 1f
        checkButton.alpha = if (loading) 0.65f else 1f
    }

    private fun showError(message: String) {
        statusText.text = message
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun handleBack() {
        MaterialAlertDialogBuilder(this)
            .setTitle("خروج از حساب")
            .setMessage("برای ورود به برنامه باید فعال‌سازی اولیه حساب تکمیل شود. آیا می‌خواهید از حساب خارج شوید؟")
            .setPositiveButton("خروج") { _, _ -> logoutAndReturnToLogin() }
            .setNegativeButton("ادامه فعال‌سازی", null)
            .show()
    }

    private fun logoutAndReturnToLogin() {
        setLoading(true)
        RetrofitClient.instance.logout().enqueue(object : Callback<ApiResponse> {
            override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) = clearSessionAndOpenLogin()
            override fun onFailure(call: Call<ApiResponse>, t: Throwable) = clearSessionAndOpenLogin()
        })
    }

    private fun clearSessionAndOpenLogin() {
        getSharedPreferences("LocalAppPrefs", Context.MODE_PRIVATE).edit().clear().apply()
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}
