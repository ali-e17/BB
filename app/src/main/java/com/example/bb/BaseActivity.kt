package com.example.bb

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.LocaleList
import android.text.InputFilter
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.text.method.TransformationMethod
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Locale

/**
 * Base مشترک تمام صفحات اپ.
 *
 * علاوه بر Insets و بررسی اعتبار سالانه،
 * قواعد یکپارچه نمایش رقم‌ها را نیز اعمال می‌کند:
 *
 * - متن دارای حرف انگلیسی -> رقم انگلیسی
 * - سایر متن‌ها -> رقم فارسی
 * - فیلدهای رمز عبور همیشه ASCII/انگلیسی باقی می‌مانند
 *
 * مقدار واقعی TextView/EditText تغییر نمی‌کند؛
 * تبدیل رقم فقط در لایه نمایش انجام می‌شود.
 */
open class BaseActivity : AppCompatActivity() {

    private val membershipHandler =
        Handler(Looper.getMainLooper())

    private var membershipCheckRunning =
        false

    private var smartDigitsRoot: View? =
        null

    private var smartDigitsLayoutListener:
        ViewTreeObserver.OnGlobalLayoutListener? = null

    private val membershipCheckRunnable =
        object : Runnable {

            override fun run() {

                checkStudentMembership()

                membershipHandler.postDelayed(
                    this,
                    MEMBERSHIP_CHECK_INTERVAL
                )
            }
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        startMembershipWatcher()
        applySmartDigitsToCurrentScreen()
    }

    override fun onPause() {
        stopMembershipWatcher()
        super.onPause()
    }

    override fun onDestroy() {
        stopMembershipWatcher()
        removeSmartDigitsObserver()
        super.onDestroy()
    }

    override fun setContentView(
        @LayoutRes layoutResID: Int
    ) {
        super.setContentView(layoutResID)
        afterContentViewSet()
    }

    override fun setContentView(
        view: View
    ) {
        super.setContentView(view)
        afterContentViewSet()
    }

    override fun setContentView(
        view: View,
        params: ViewGroup.LayoutParams
    ) {
        super.setContentView(view, params)
        afterContentViewSet()
    }

    private fun afterContentViewSet() {
        applyRootInsets()
        setupSmartDigitsRendering()
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

    // =========================================================
    // Smart digits
    // =========================================================

    private fun setupSmartDigitsRendering() {

        removeSmartDigitsObserver()

        val content =
            findViewById<ViewGroup>(
                android.R.id.content
            )

        val root =
            content.getChildAt(0)
                ?: return

        smartDigitsRoot = root
        applySmartDigitsRecursively(root)

        val listener =
            ViewTreeObserver.OnGlobalLayoutListener {
                applySmartDigitsRecursively(root)
            }

        smartDigitsLayoutListener = listener
        root.viewTreeObserver.addOnGlobalLayoutListener(listener)
    }

    private fun applySmartDigitsToCurrentScreen() {
        smartDigitsRoot?.let(::applySmartDigitsRecursively)
    }

    private fun applySmartDigitsRecursively(
        view: View
    ) {
        if (view is TextView) {
            if (isPasswordField(view)) {
                configurePasswordField(view)
            } else {
                if (isForceEnglishDigitsField(view)) {
                    configureForceEnglishDigitsField(view)
                }
                applySmartDigitsTransformation(view)
            }
        }

        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                applySmartDigitsRecursively(
                    view.getChildAt(index)
                )
            }
        }
    }

    private fun applySmartDigitsTransformation(
        textView: TextView
    ) {
        val current = textView.transformationMethod

        if (current is SmartDigitsTransformationMethod) {
            return
        }

        textView.transformationMethod =
            SmartDigitsTransformationMethod(current)
    }


    private fun isForceEnglishDigitsField(
        textView: TextView
    ): Boolean =
        textView.tag?.toString() == FORCE_ENGLISH_DIGITS_TAG

    /**
     * فیلدهای کدی مثل کد اتباع:
     * حتی اگر کاربر با کیبورد فارسی/عربی عدد وارد کند،
     * مقدار واقعی EditText به رقم انگلیسی تبدیل می‌شود.
     */
    private fun configureForceEnglishDigitsField(
        textView: TextView
    ) {
        if (textView !is EditText) return

        if (textView.filters.none { it is AsciiDigitsInputFilter }) {
            textView.filters =
                textView.filters + AsciiDigitsInputFilter()
        }

        textView.textLocale = Locale.US

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            textView.imeHintLocales = LocaleList(Locale.US)
        }
    }

    /**
     * رمز عبور باید مستقل از زبان UI به‌صورت ASCII وارد شود.
     * رقم فارسی/عربی هنگام تایپ به رقم انگلیسی تبدیل می‌شود
     * و نویسه غیر ASCII در فیلد رمز پذیرفته نمی‌شود.
     */
    private fun configurePasswordField(
        textView: TextView
    ) {
        if (textView !is EditText) return

        if (textView.filters.none { it is AsciiPasswordInputFilter }) {
            textView.filters =
                textView.filters + AsciiPasswordInputFilter()
        }

        textView.textLocale = Locale.US

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            textView.imeHintLocales = LocaleList(Locale.US)
        }
    }

    private fun isPasswordField(
        textView: TextView
    ): Boolean {
        if (textView !is EditText) return false

        if (textView.transformationMethod is PasswordTransformationMethod) {
            return true
        }

        val variation =
            textView.inputType and
                InputType.TYPE_MASK_VARIATION

        return variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
    }

    private fun removeSmartDigitsObserver() {
        val root = smartDigitsRoot
        val listener = smartDigitsLayoutListener

        if (
            root != null &&
            listener != null &&
            root.viewTreeObserver.isAlive
        ) {
            root.viewTreeObserver.removeOnGlobalLayoutListener(listener)
        }

        smartDigitsRoot = null
        smartDigitsLayoutListener = null
    }

    private class SmartDigitsTransformationMethod(
        private val original: TransformationMethod?
    ) : TransformationMethod {

        override fun getTransformation(
            source: CharSequence,
            view: View
        ): CharSequence {
            val transformed =
                original?.getTransformation(source, view)
                    ?: source

            return if (
                view.tag?.toString() == FORCE_ENGLISH_DIGITS_TAG
            ) {
                UiTextFormatter.normalizeEnglishDigits(transformed)
            } else {
                UiTextFormatter.smartDigits(transformed)
            }
        }

        override fun onFocusChanged(
            view: View,
            sourceText: CharSequence,
            focused: Boolean,
            direction: Int,
            previouslyFocusedRect: Rect?
        ) {
            original?.onFocusChanged(
                view,
                sourceText,
                focused,
                direction,
                previouslyFocusedRect
            )
        }
    }

    private class AsciiDigitsInputFilter : InputFilter {

        override fun filter(
            source: CharSequence,
            start: Int,
            end: Int,
            dest: android.text.Spanned,
            dstart: Int,
            dend: Int
        ): CharSequence? {
            if (start >= end) return null

            val result = StringBuilder(end - start)
            var changed = false

            for (index in start until end) {
                val original = source[index]
                val normalized =
                    UiTextFormatter.toEnglishDigit(original)

                if (normalized in '0'..'9') {
                    result.append(normalized)
                } else {
                    changed = true
                }

                if (normalized != original) {
                    changed = true
                }
            }

            return if (changed) result.toString() else null
        }
    }

    private class AsciiPasswordInputFilter : InputFilter {

        override fun filter(
            source: CharSequence,
            start: Int,
            end: Int,
            dest: android.text.Spanned,
            dstart: Int,
            dend: Int
        ): CharSequence? {
            if (start >= end) return null

            val result = StringBuilder(end - start)
            var changed = false

            for (index in start until end) {
                val original = source[index]
                val normalized =
                    UiTextFormatter.toEnglishDigit(original)

                val isAllowedAscii =
                    normalized.code in 33..126

                if (isAllowedAscii) {
                    result.append(normalized)
                } else {
                    changed = true
                }

                if (normalized != original) {
                    changed = true
                }
            }

            return if (changed) result.toString() else null
        }
    }

    // =========================================================
    // Membership watcher
    // =========================================================

    private fun startMembershipWatcher() {

        if (!shouldWatchMembership()) {
            return
        }

        membershipHandler.removeCallbacks(
            membershipCheckRunnable
        )

        membershipHandler.post(
            membershipCheckRunnable
        )
    }

    private fun stopMembershipWatcher() {
        membershipHandler.removeCallbacks(
            membershipCheckRunnable
        )
    }

    private fun shouldWatchMembership(): Boolean {

        if (
            this is LoginActivity ||
            this is InitialPaymentActivity ||
            this is UpdateProfileActivity ||
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

        if (token.isBlank()) {
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

    private fun checkStudentMembership() {

        if (
            membershipCheckRunning ||
            !shouldWatchMembership()
        ) {
            return
        }

        membershipCheckRunning = true

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

                        membershipCheckRunning = false

                        if (
                            isFinishing ||
                            isDestroyed
                        ) {
                            return
                        }

                        if (!response.isSuccessful) {
                            return
                        }

                        val body =
                            response.body()
                                ?: return

                        if (body.status != "success") {
                            return
                        }

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

                        if (body.paymentRequired) {
                            openRenewalScreen()
                        }
                    }

                    override fun onFailure(
                        call: Call<InitialPaymentStatusResponse>,
                        t: Throwable
                    ) {
                        membershipCheckRunning = false
                    }
                }
            )
    }

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

        private const val FORCE_ENGLISH_DIGITS_TAG =
            "force_english_digits"


        private const val PREFS_NAME =
            "LocalAppPrefs"

        private const val MEMBERSHIP_CHECK_INTERVAL =
            60_000L
    }
}
