package com.example.bb

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response


/**
 * Base مشترک تمام صفحات اپ.
 *
 * علاوه بر Insets،
 * اعتبار سالانه دانش‌آموز را هم
 * وقتی اپ در حال استفاده است بررسی می‌کند.
 */
open class BaseActivity : AppCompatActivity() {

    private val membershipHandler =
        Handler(Looper.getMainLooper())


    private var membershipCheckRunning =
        false


    private val membershipCheckRunnable =
        object : Runnable {

            override fun run() {

                checkStudentMembership()

                /*
                 * هر یک دقیقه دوباره بررسی می‌کنیم.
                 *
                 * فقط وقتی Activity در حالت Resume باشد.
                 */
                membershipHandler.postDelayed(
                    this,
                    MEMBERSHIP_CHECK_INTERVAL
                )
            }
        }


    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )
    }


    override fun onResume() {

        super.onResume()

        startMembershipWatcher()
    }


    override fun onPause() {

        stopMembershipWatcher()

        super.onPause()
    }


    override fun setContentView(
        @LayoutRes layoutResID: Int
    ) {

        super.setContentView(
            layoutResID
        )

        applyRootInsets()
    }


    override fun setContentView(
        view: View
    ) {

        super.setContentView(
            view
        )

        applyRootInsets()
    }


    override fun setContentView(
        view: View,
        params: ViewGroup.LayoutParams
    ) {

        super.setContentView(
            view,
            params
        )

        applyRootInsets()
    }


    private fun applyRootInsets() {

        val content =
            findViewById<ViewGroup>(
                android.R.id.content
            )


        val root =
            content.getChildAt(0)
                ?: return


        SystemBarInsets.apply(
            this,
            root
        )
    }


    /**
     * شروع بررسی دوره‌ای اعتبار.
     */
    private fun startMembershipWatcher() {

        if (
            !shouldWatchMembership()
        ) {
            return
        }


        membershipHandler.removeCallbacks(
            membershipCheckRunnable
        )


        /*
         * همان لحظه ورود/برگشت به صفحه
         * یک بار بررسی می‌کنیم.
         */
        membershipHandler.post(
            membershipCheckRunnable
        )
    }


    /**
     * توقف بررسی وقتی Activity دیگر
     * روی صفحه نیست.
     */
    private fun stopMembershipWatcher() {

        membershipHandler.removeCallbacks(
            membershipCheckRunnable
        )
    }


    /**
     * فقط دانش‌آموز Login شده باید
     * بررسی اعتبار سالانه داشته باشد.
     */
    private fun shouldWatchMembership(): Boolean {

        /*
         * در این صفحات نباید Watcher اجرا شود.
         */
        if (
            this is LoginActivity
            ||
            this is InitialPaymentActivity
            ||
            this is UpdateProfileActivity
            ||
            this is ForceChangePasswordActivity
        ) {
            return false
        }


        val prefs =
            getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )


        val token =
            prefs
                .getString(
                    "API_TOKEN",
                    ""
                )
                .orEmpty()


        if (
            token.isBlank()
        ) {
            return false
        }


        val role =
            prefs
                .getString(
                    "CURRENT_USER_ROLE",
                    ""
                )
                .orEmpty()
                .uppercase()


        return role == "STUDENT"
    }


    /**
     * بررسی وضعیت عضویت از Backend.
     *
     * Backend منبع اصلی تشخیص اعتبار است.
     */
    private fun checkStudentMembership() {

        if (
            membershipCheckRunning
            ||
            !shouldWatchMembership()
        ) {
            return
        }


        membershipCheckRunning =
            true


        RetrofitClient
            .instance
            .getInitialPaymentStatus()
            .enqueue(
                object :
                    Callback<InitialPaymentStatusResponse> {


                    override fun onResponse(
                        call: Call<InitialPaymentStatusResponse>,
                        response: Response<InitialPaymentStatusResponse>
                    ) {

                        membershipCheckRunning =
                            false


                        if (
                            isFinishing
                            ||
                            isDestroyed
                        ) {
                            return
                        }


                        /*
                         * اگر سرور خودش 401/402 بدهد،
                         * SessionInterceptor آن را مدیریت می‌کند.
                         */
                        if (
                            !response.isSuccessful
                        ) {
                            return
                        }


                        val body =
                            response.body()
                                ?: return


                        if (
                            body.status != "success"
                        ) {
                            return
                        }


                        /*
                         * اطلاعات وضعیت را Cache می‌کنیم.
                         */
                        getSharedPreferences(
                            PREFS_NAME,
                            Context.MODE_PRIVATE
                        )
                            .edit()

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
                                body.paymentReason
                            )

                            .apply()


                        /*
                         * اعتبار منقضی شده.
                         */
                        if (
                            body.paymentRequired
                        ) {

                            openRenewalScreen()
                        }
                    }


                    override fun onFailure(
                        call: Call<InitialPaymentStatusResponse>,
                        t: Throwable
                    ) {

                        membershipCheckRunning =
                            false


                        /*
                         * اگر اینترنت قطع باشد،
                         * کاربر را بی‌دلیل Logout
                         * یا Block نمی‌کنیم.
                         *
                         * یک دقیقه بعد دوباره چک می‌شود.
                         */
                    }
                }
            )
    }


    /**
     * انتقال اجباری به صفحه تمدید.
     *
     * Token پاک نمی‌شود چون برای
     * انجام پرداخت لازم است.
     */
    private fun openRenewalScreen() {

        stopMembershipWatcher()


        getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )
            .edit()

            .putBoolean(
                "PAYMENT_REQUIRED",
                true
            )

            .putString(
                "INITIAL_ACCESS_STATUS",
                "PENDING"
            )

            .putString(
                "PAYMENT_REASON",
                "ANNUAL_RENEWAL"
            )

            .apply()


        startActivity(
            Intent(
                this,
                InitialPaymentActivity::class.java
            ).apply {

                flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )


        finish()
    }


    companion object {

        private const val PREFS_NAME =
            "LocalAppPrefs"


        /*
         * یک دقیقه
         */
        private const val MEMBERSHIP_CHECK_INTERVAL =
            60_000L
    }
}