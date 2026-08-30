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

class InitialPaymentActivity : BaseActivity() {

    private lateinit var amountText: TextView
    private lateinit var statusText: TextView
    private lateinit var environmentText: TextView

    private lateinit var toolbarTitle: TextView
    private lateinit var pageTitle: TextView
    private lateinit var descriptionText: TextView
    private lateinit var amountLabel: TextView
    private lateinit var footerText: TextView

    private lateinit var startButton: MaterialButton
    private lateinit var checkButton: MaterialButton
    private lateinit var progress: View

    private var gatewayOpened = false
    private var returnCheckScheduled = false
    private var navigatedAway = false

    private var currentPaymentReason = "FIRST_ACCESS"


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_initial_payment
        )


        amountText =
            findViewById(
                R.id.tvInitialPaymentAmount
            )

        statusText =
            findViewById(
                R.id.tvInitialPaymentStatus
            )

        environmentText =
            findViewById(
                R.id.tvInitialPaymentEnvironment
            )

        toolbarTitle =
            findViewById(
                R.id.tvInitialPaymentToolbarTitle
            )

        pageTitle =
            findViewById(
                R.id.tvInitialPaymentTitle
            )

        descriptionText =
            findViewById(
                R.id.tvInitialPaymentDescription
            )

        amountLabel =
            findViewById(
                R.id.tvInitialPaymentAmountLabel
            )

        footerText =
            findViewById(
                R.id.tvInitialPaymentFooter
            )

        startButton =
            findViewById(
                R.id.btnStartInitialPayment
            )

        checkButton =
            findViewById(
                R.id.btnCheckInitialPayment
            )

        progress =
            findViewById(
                R.id.progressInitialPayment
            )


        /*
         * در شروع صفحه دکمه بررسی وضعیت
         * نمایش داده نمی‌شود.
         *
         * فقط اگر تراکنش PENDING داشته باشیم
         * نمایش داده خواهد شد.
         */
        checkButton.visibility =
            View.GONE


        /*
         * متن Sandbox یا Production
         * در نسخه نهایی به کاربر نشان داده نمی‌شود.
         */
        environmentText.visibility =
            View.GONE

        environmentText.text =
            ""


        findViewById<ImageView>(
            R.id.btnInitialPaymentBack
        ).setOnClickListener {

            handleBack()
        }


        startButton.setOnClickListener {

            requestPayment()
        }


        checkButton.setOnClickListener {

            reconcilePayment(
                showLoading = true,
                manualCheck = true
            )
        }


        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {

                override fun handleOnBackPressed() {

                    handleBack()
                }
            }
        )


        /*
         * اگر از Callback برنامه باز شده باشد.
         */
        if (
            intent?.data?.scheme.equals(
                "bbapp",
                ignoreCase = true
            )
        ) {

            gatewayOpened = true
        }


        loadStatus(
            showLoading = true
        )
    }


    override fun onNewIntent(
        intent: Intent
    ) {

        super.onNewIntent(intent)

        setIntent(intent)


        /*
         * نتیجه داخل Deep Link قابل اعتماد نیست.
         *
         * فقط به عنوان علامت برگشت از درگاه
         * از آن استفاده می‌کنیم و وضعیت واقعی
         * را از Backend می‌گیریم.
         */
        gatewayOpened = true


        reconcilePayment(
            showLoading = true,
            manualCheck = false
        )
    }


    override fun onResume() {

        super.onResume()


        /*
         * اگر کاربر از مرورگر به برنامه برگشته،
         * کمی صبر می‌کنیم تا Callback زرین‌پال
         * فرصت ثبت وضعیت را داشته باشد.
         */
        if (
            gatewayOpened
            &&
            !returnCheckScheduled
            &&
            !navigatedAway
        ) {

            returnCheckScheduled = true


            Handler(
                Looper.getMainLooper()
            ).postDelayed({

                returnCheckScheduled = false


                if (
                    !isFinishing
                    &&
                    !navigatedAway
                ) {

                    reconcilePayment(
                        showLoading = false,
                        manualCheck = false
                    )
                }

            }, 1200)
        }
    }


    override fun onRemoteConfigChanged(config: AppRemoteConfig) {
        findViewById<ImageView>(R.id.imgInitialPaymentLogo)?.let {
            RemoteConfigManager.applyCachedLogo(it, R.drawable.final50cm)
            it.contentDescription = "لوگوی ${config.schoolShortNameFa}"
        }
        if (::toolbarTitle.isInitialized) {
            renderPaymentReason(currentPaymentReason)
        }
    }

    private fun loadStatus(
        showLoading: Boolean
    ) {

        if (showLoading) {

            setLoading(true)

            statusText.text =
                "در حال بررسی وضعیت عضویت..."
        }


        RetrofitClient.instance
            .getInitialPaymentStatus()
            .enqueue(
                object :
                    Callback<InitialPaymentStatusResponse> {

                    override fun onResponse(
                        call:
                        Call<InitialPaymentStatusResponse>,
                        response:
                        Response<InitialPaymentStatusResponse>
                    ) {

                        if (showLoading) {

                            setLoading(false)
                        }


                        val body =
                            response.body()


                        if (
                            response.isSuccessful
                            &&
                            body?.status == "success"
                        ) {

                            renderStatus(
                                body
                            )

                        } else {

                            showError(
                                body?.message
                                    ?.takeIf {
                                        it.isNotBlank()
                                    }
                                    ?: ApiErrorParser.userMessage(
                                        response,
                                        "وضعیت عضویت دریافت نشد"
                                    )
                            )
                        }
                    }


                    override fun onFailure(
                        call:
                        Call<InitialPaymentStatusResponse>,
                        t: Throwable
                    ) {

                        if (showLoading) {

                            setLoading(false)
                        }


                        showError(
                            ApiErrorParser.networkMessage(
                                t,
                                "بررسی وضعیت پرداخت"
                            )
                        )
                    }
                }
            )
    }


    private fun requestPayment() {

        setLoading(true)


        /*
         * هنگام ساخت تراکنش جدید
         * دکمه بررسی فعلاً مخفی باشد.
         */
        checkButton.visibility =
            View.GONE


        statusText.text =
            "در حال آماده‌سازی درگاه پرداخت..."


        RetrofitClient.instance
            .requestInitialPayment()
            .enqueue(
                object :
                    Callback<InitialPaymentRequestResponse> {

                    override fun onResponse(
                        call:
                        Call<InitialPaymentRequestResponse>,
                        response:
                        Response<InitialPaymentRequestResponse>
                    ) {

                        setLoading(false)


                        val body =
                            response.body()


                        if (
                            !response.isSuccessful
                            ||
                            body?.status != "success"
                        ) {

                            showError(
                                body?.message
                                    ?.takeIf {
                                        it.isNotBlank()
                                    }
                                    ?: ApiErrorParser.userMessage(
                                        response,
                                        "درگاه پرداخت آماده نشد"
                                    )
                            )

                            return
                        }


                        currentPaymentReason =
                            body.paymentReason
                                .ifBlank {
                                    currentPaymentReason
                                }


                        savePaymentMeta(
                            accessValidUntil =
                                body.accessValidUntil,

                            paymentReason =
                                body.paymentReason
                        )


                        renderPaymentReason(
                            currentPaymentReason
                        )


                        if (
                            !body.paymentRequired
                        ) {

                            completeActivation(
                                body.initialAccessStatus
                                    ?: "PAID"
                            )

                            return
                        }


                        renderAmount(
                            body.amount,
                            body.currency
                        )


                        renderEnvironment(
                            body.environment
                        )


                        val paymentUrl =
                            body.paymentUrl
                                .orEmpty()


                        if (
                            paymentUrl.isBlank()
                        ) {

                            showError(
                                "نشانی درگاه پرداخت دریافت نشد. لطفاً مجدداً تلاش کنید."
                            )

                            return
                        }


                        /*
                         * از این لحظه یک تراکنش
                         * PENDING داریم؛ بنابراین
                         * اگر کاربر از درگاه برگشت،
                         * امکان بررسی دستی هم دارد.
                         */
                        checkButton.visibility =
                            View.VISIBLE


                        openGateway(
                            paymentUrl
                        )
                    }


                    override fun onFailure(
                        call:
                        Call<InitialPaymentRequestResponse>,
                        t: Throwable
                    ) {

                        setLoading(false)


                        showError(
                            ApiErrorParser.networkMessage(
                                t,
                                "ایجاد درخواست پرداخت"
                            )
                        )
                    }
                }
            )
    }


    private fun reconcilePayment(
        showLoading: Boolean,
        manualCheck: Boolean
    ) {

        if (showLoading) {

            setLoading(true)
        }


        statusText.text =
            "در حال بررسی وضعیت پرداخت..."


        RetrofitClient.instance
            .reconcileInitialPayment()
            .enqueue(
                object :
                    Callback<InitialPaymentStatusResponse> {

                    override fun onResponse(
                        call:
                        Call<InitialPaymentStatusResponse>,
                        response:
                        Response<InitialPaymentStatusResponse>
                    ) {

                        if (showLoading) {

                            setLoading(false)
                        }


                        val body =
                            response.body()


                        if (
                            response.isSuccessful
                            &&
                            body?.status == "success"
                        ) {

                            gatewayOpened =
                                false


                            renderStatus(
                                body
                            )


                            /*
                             * اگر پرداخت تأیید شده باشد،
                             * renderStatus خودش وارد مرحله
                             * بعدی می‌شود.
                             */
                            if (
                                !body.paymentRequired
                            ) {

                                return
                            }


                            /*
                             * بازخورد واضح برای کاربری که
                             * خودش دکمه بررسی را زده.
                             */
                            if (manualCheck) {

                                when (
                                    body.transaction
                                        ?.status
                                        ?.uppercase(
                                            Locale.US
                                        )
                                ) {

                                    "PENDING" -> {

                                        statusText.text =
                                            "پرداخت تاکنون تأیید نشده است. اگر پرداخت را انجام داده‌اید، چند لحظه دیگر مجدداً بررسی کنید."


                                        AppToast.makeText(
                                            this@InitialPaymentActivity,
                                            "پرداخت تاکنون تأیید نشده است",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }


                                    "CREATED" -> {

                                        statusText.text =
                                            "درخواست پرداخت در حال آماده‌سازی است. لطفاً چند لحظه بعد مجدداً تلاش کنید."
                                    }


                                    "CANCELED" -> {

                                        statusText.text =
                                            "پرداخت قبلی لغو شده است. برای ادامه، پرداخت را مجدداً انجام دهید."
                                    }


                                    "FAILED" -> {

                                        statusText.text =
                                            "پرداخت قبلی ناموفق بوده است. لطفاً مجدداً تلاش کنید."
                                    }


                                    "REVERSED" -> {

                                        statusText.text =
                                            "مبلغ پرداخت بازگشت داده شده است. برای ادامه، پرداخت را مجدداً انجام دهید."
                                    }


                                    else -> {

                                        statusText.text =
                                            "پرداخت موفقی برای این حساب ثبت نشده است."
                                    }
                                }
                            }

                        } else {

                            showError(
                                body?.message
                                    ?.takeIf {
                                        it.isNotBlank()
                                    }
                                    ?: ApiErrorParser.userMessage(
                                        response,
                                        "وضعیت پرداخت قابل بررسی نیست"
                                    )
                            )
                        }
                    }


                    override fun onFailure(
                        call:
                        Call<InitialPaymentStatusResponse>,
                        t: Throwable
                    ) {

                        if (showLoading) {

                            setLoading(false)
                        }


                        showError(
                            ApiErrorParser.networkMessage(
                                t,
                                "بررسی نتیجه پرداخت"
                            )
                        )
                    }
                }
            )
    }


    private fun renderStatus(
        body: InitialPaymentStatusResponse
    ) {

        val tx =
            body.transaction


        currentPaymentReason =
            body.paymentReason
                .ifBlank {
                    "FIRST_ACCESS"
                }


        savePaymentMeta(
            accessValidUntil =
                body.accessValidUntil,

            paymentReason =
                currentPaymentReason
        )


        renderPaymentReason(
            currentPaymentReason
        )


        val txStatus =
            tx?.status
                ?.uppercase(
                    Locale.US
                )


        val txIsActive =
            txStatus == "PENDING"
                    ||
                    txStatus == "CREATED"


        if (
            body.paymentRequired
            &&
            txIsActive
            &&
            tx != null
        ) {

            /*
             * تراکنش جاری مبلغ خودش را دارد.
             */
            renderAmount(
                tx.amount,
                tx.currency
            )

            renderEnvironment(
                tx.environment
            )

        } else {

            renderAmount(
                body.amount,
                body.currency
            )

            renderEnvironment(
                body.environment
            )
        }


        val prefs =
            getSharedPreferences(
                "LocalAppPrefs",
                Context.MODE_PRIVATE
            )


        prefs.edit()
            .putString(
                "INITIAL_ACCESS_STATUS",
                body.initialAccessStatus
            )
            .putBoolean(
                "PAYMENT_REQUIRED",
                body.paymentRequired
            )
            .putString(
                "ACCESS_VALID_UNTIL",
                body.accessValidUntil
            )
            .putString(
                "PAYMENT_REASON",
                currentPaymentReason
            )
            .apply()


        /*
         * پرداخت تأیید شده؛
         * وارد مرحله بعد شو.
         */
        if (
            !body.paymentRequired
        ) {

            checkButton.visibility =
                View.GONE


            completeActivation(
                body.initialAccessStatus
            )

            return
        }


        startButton.isEnabled =
            true


        /*
         * دکمه بررسی وضعیت فقط وقتی
         * واقعاً تراکنش PENDING داریم
         * نشان داده می‌شود.
         */
        checkButton.visibility =
            if (
                txStatus == "PENDING"
            ) {
                View.VISIBLE
            } else {
                View.GONE
            }


        checkButton.isEnabled =
            txStatus == "PENDING"


        statusText.text =
            when (txStatus) {

                "PENDING" ->

                    "درخواست پرداخت ایجاد شده است. لطفاً پرداخت را تکمیل کنید. اگر پرداخت را انجام داده‌اید، می‌توانید وضعیت آن را بررسی کنید."


                "CREATED" ->

                    "درخواست پرداخت در حال آماده‌سازی است."


                "CANCELED" ->

                    "پرداخت قبلی لغو شده است. برای ادامه، پرداخت را مجدداً انجام دهید."


                "FAILED" ->

                    "پرداخت قبلی ناموفق بوده است. لطفاً مجدداً تلاش کنید."


                "REVERSED" ->

                    "پرداخت بازگشت داده شده است. برای ادامه، پرداخت را مجدداً انجام دهید."


                else ->

                    if (
                        currentPaymentReason ==
                        "ANNUAL_RENEWAL"
                    ) {

                        RemoteConfigManager.current().membership.renewalDescription

                    } else {

                        RemoteConfigManager.current().membership.firstDescription
                    }
            }


        startButton.text =
            when {

                txStatus == "PENDING" ->

                    "ادامه پرداخت"


                currentPaymentReason ==
                        "ANNUAL_RENEWAL" ->

                    RemoteConfigManager.current().membership.renewalButton


                else ->

                    RemoteConfigManager.current().membership.firstButton
            }
    }


    /*
     * نمایش مبلغ با:
     *
     * اعداد فارسی
     * ولی کامای معمولی پایین (,)
     *
     * مثال:
     * ۱۵۰,۰۰۰ تومان
     */
    private fun renderAmount(
        amount: Long,
        currency: String
    ) {

        val unit =
            if (
                currency.equals(
                    "IRR",
                    ignoreCase = true
                )
            ) {
                "ریال"
            } else {
                "تومان"
            }


        val englishGrouped =
            NumberFormat
                .getIntegerInstance(
                    Locale.US
                )
                .format(
                    amount
                )


        val persianGrouped =
            convertDigitsToPersian(
                englishGrouped
            )


        /*
         * NumberFormat در Locale.US
         * از U+002C یعنی کامای پایین استفاده می‌کند.
         *
         * فقط اعداد را فارسی می‌کنیم و
         * خود کاما را تغییر نمی‌دهیم.
         */
        amountText.text =
            "$persianGrouped $unit"
    }


    private fun convertDigitsToPersian(
        value: String
    ): String {

        val persianDigits =
            charArrayOf(
                '۰',
                '۱',
                '۲',
                '۳',
                '۴',
                '۵',
                '۶',
                '۷',
                '۸',
                '۹'
            )


        return buildString {

            value.forEach { character ->

                if (
                    character in '0'..'9'
                ) {

                    append(
                        persianDigits[
                            character - '0'
                        ]
                    )

                } else {

                    /*
                     * کامای معمولی "," همینجا
                     * بدون تغییر باقی می‌ماند.
                     */
                    append(
                        character
                    )
                }
            }
        }
    }


    private fun renderPaymentReason(
        paymentReason: String
    ) {
        val membership = RemoteConfigManager.current().membership

        if (paymentReason.equals("ANNUAL_RENEWAL", ignoreCase = true)) {
            toolbarTitle.text = membership.renewalToolbarTitle
            pageTitle.text = membership.renewalPageTitle
            descriptionText.text = membership.renewalDescription
            amountLabel.text = membership.renewalAmountLabel
            footerText.text = membership.renewalFooter
            startButton.text = membership.renewalButton
        } else {
            toolbarTitle.text = membership.firstToolbarTitle
            pageTitle.text = membership.firstPageTitle
            descriptionText.text = membership.firstDescription
            amountLabel.text = membership.firstAmountLabel
            footerText.text = membership.firstFooter
            startButton.text = membership.firstButton
        }
    }


    private fun renderEnvironment(
        environment: String
    ) {

        /*
         * عمداً چیزی درباره
         * SANDBOX / PRODUCTION
         * در UI نمایش داده نمی‌شود.
         */
        environmentText.visibility =
            View.GONE

        environmentText.text =
            ""
    }


    private fun savePaymentMeta(
        accessValidUntil: String?,
        paymentReason: String
    ) {

        getSharedPreferences(
            "LocalAppPrefs",
            Context.MODE_PRIVATE
        )
            .edit()
            .putString(
                "ACCESS_VALID_UNTIL",
                accessValidUntil
            )
            .putString(
                "PAYMENT_REASON",
                paymentReason
            )
            .apply()
    }


    private fun openGateway(
        url: String
    ) {

        val uri =
            runCatching {

                Uri.parse(
                    url
                )

            }.getOrNull()


        if (
            uri == null
            ||
            uri.scheme !in
            listOf(
                "https",
                "http"
            )
        ) {

            showError(
                "نشانی درگاه پرداخت معتبر نیست"
            )

            return
        }


        gatewayOpened =
            true


        /*
         * از حالا دکمه بررسی وضعیت
         * کاربرد دارد.
         */
        checkButton.visibility =
            View.VISIBLE


        statusText.text =
            "درگاه پرداخت باز شد. پس از تکمیل پرداخت، به برنامه بازگردید."


        try {

            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    uri
                )
            )

        } catch (_: Exception) {

            gatewayOpened =
                false


            showError(
                "مرورگر مناسبی برای باز کردن درگاه پرداخت در دسترس نیست"
            )
        }
    }


    private fun completeActivation(
        accessStatus: String
    ) {

        if (navigatedAway) {

            return
        }


        navigatedAway =
            true


        val prefs =
            getSharedPreferences(
                "LocalAppPrefs",
                Context.MODE_PRIVATE
            )


        prefs.edit()
            .putBoolean(
                "PAYMENT_REQUIRED",
                false
            )
            .putString(
                "INITIAL_ACCESS_STATUS",
                accessStatus
            )
            .apply()


        val mustChangePassword =
            prefs.getBoolean(
                "MUST_CHANGE_PASSWORD",
                false
            )


        if (mustChangePassword) {

            startActivity(
                Intent(
                    this,
                    UpdateProfileActivity::class.java
                ).apply {

                    flags =
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TASK


                    putExtra(
                        UpdateProfileActivity.EXTRA_FORCE_PASSWORD_CHANGE,
                        true
                    )


                    putExtra(
                        UpdateProfileActivity.EXTRA_OPEN_MAIN_AFTER_CHANGE,
                        true
                    )
                }
            )

        } else {

            startActivity(
                Intent(
                    this,
                    MainActivity::class.java
                ).apply {

                    flags =
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TASK


                    putExtra(
                        "USER_ROLE",
                        prefs.getString(
                            "CURRENT_USER_ROLE",
                            "STUDENT"
                        )
                    )
                }
            )
        }


        finish()
    }


    private fun setLoading(
        loading: Boolean
    ) {

        progress.visibility =
            if (loading) {
                View.VISIBLE
            } else {
                View.GONE
            }


        startButton.isEnabled =
            !loading


        /*
         * اگر دکمه مخفی باشد
         * وضعیت Enabled مهم نیست.
         */
        checkButton.isEnabled =
            !loading


        startButton.alpha =
            if (loading) {
                0.65f
            } else {
                1f
            }


        checkButton.alpha =
            if (loading) {
                0.65f
            } else {
                1f
            }
    }


    private fun showError(
        message: String
    ) {

        statusText.text =
            message


        AppToast.makeText(
            this,
            message,
            Toast.LENGTH_LONG
        ).show()
    }


    private fun handleBack() {

        val message =
            if (
                currentPaymentReason ==
                "ANNUAL_RENEWAL"
            ) {

                RemoteConfigManager.current().membership.renewalLogoutMessage

            } else {

                RemoteConfigManager.current().membership.firstLogoutMessage
            }


        MaterialAlertDialogBuilder(
            this
        )
            .setTitle(
                "خروج از حساب"
            )
            .setMessage(
                message
            )
            .setPositiveButton(
                "خروج"
            ) { _, _ ->

                logoutAndReturnToLogin()
            }
            .setNegativeButton(
                "ادامه",
                null
            )
            .show()
    }


    private fun logoutAndReturnToLogin() {

        setLoading(
            true
        )


        RetrofitClient.instance
            .logout()
            .enqueue(
                object :
                    Callback<ApiResponse> {

                    override fun onResponse(
                        call: Call<ApiResponse>,
                        response: Response<ApiResponse>
                    ) {

                        clearSessionAndOpenLogin()
                    }


                    override fun onFailure(
                        call: Call<ApiResponse>,
                        t: Throwable
                    ) {

                        clearSessionAndOpenLogin()
                    }
                }
            )
    }


    private fun clearSessionAndOpenLogin() {

        getSharedPreferences(
            "LocalAppPrefs",
            Context.MODE_PRIVATE
        )
            .edit()
            .clear()
            .apply()


        startActivity(
            Intent(
                this,
                LoginActivity::class.java
            ).apply {

                flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )


        finish()
    }
}