package com.example.bb

import android.content.Context
import android.widget.Toast

/**
 * Toast یکپارچه برنامه.
 *
 * - همه پیام‌ها از یک مسیر واحد نمایش داده می‌شوند.
 * - پیام قبلی پیش از نمایش پیام جدید لغو می‌شود تا Toastها روی هم انباشته نشوند.
 * - نوع پیام با یک نشان کوتاه مشخص می‌شود.
 * - متن پیام پیش از نمایش، از نظر نگارشی و لحن رسمی یکدست می‌شود.
 */
object AppToast {

    private var activeToast: Toast? = null

    fun makeText(
        context: Context,
        text: CharSequence?,
        duration: Int
    ): Toast {
        activeToast?.cancel()

        val message = normalizeMessage(
            text?.toString().orEmpty()
        )

        val toast = Toast.makeText(
            context.applicationContext,
            decorate(message),
            duration
        )

        activeToast = toast
        return toast
    }

    fun success(
        context: Context,
        message: CharSequence,
        duration: Int = Toast.LENGTH_SHORT
    ) {
        showTyped(context, message, duration, "✓")
    }

    fun warning(
        context: Context,
        message: CharSequence,
        duration: Int = Toast.LENGTH_LONG
    ) {
        showTyped(context, message, duration, "⚠")
    }

    fun error(
        context: Context,
        message: CharSequence,
        duration: Int = Toast.LENGTH_LONG
    ) {
        showTyped(context, message, duration, "✕")
    }

    fun info(
        context: Context,
        message: CharSequence,
        duration: Int = Toast.LENGTH_SHORT
    ) {
        showTyped(context, message, duration, "ℹ")
    }

    private fun showTyped(
        context: Context,
        message: CharSequence,
        duration: Int,
        icon: String
    ) {
        activeToast?.cancel()

        activeToast = Toast.makeText(
            context.applicationContext,
            "$icon ${normalizeMessage(message.toString())}",
            duration
        ).also { it.show() }
    }

    private fun normalizeMessage(raw: String): String {
        var message = raw
            .trim()
            .replace(Regex("\\s+"), " ")

        if (message.isBlank()) {
            message = "امکان انجام این عملیات وجود ندارد"
        }

        // یکدست‌سازی عبارت‌های محاوره‌ای یا کم‌رسمی که ممکن است از پاسخ API نیز برسند.
        message = message
            .replace("دوباره بازش کنید", "مجدداً باز کنید")
            .replace("دوباره باز کنید", "مجدداً باز کنید")
            .replace("دوباره تلاش کنید", "مجدداً تلاش کنید")
            .replace("دوباره امتحان کنید", "مجدداً تلاش کنید")
            .replace("یک بار", "یک‌بار")

        return ensureTerminalPunctuation(message)
    }

    private fun ensureTerminalPunctuation(message: String): String {
        if (message.isBlank()) return "امکان انجام این عملیات وجود ندارد."

        val last = message.last()
        return if (
            last == '.' ||
            last == '!' ||
            last == '?' ||
            last == '؟' ||
            last == '؛' ||
            last == '…'
        ) {
            message
        } else {
            "$message."
        }
    }

    private fun decorate(message: String): String {
        if (
            message.startsWith("✓ ") ||
            message.startsWith("⚠ ") ||
            message.startsWith("✕ ") ||
            message.startsWith("ℹ ")
        ) {
            return message
        }

        val icon = when {
            looksLikeSuccess(message) -> "✓"
            looksLikeError(message) -> "✕"
            looksLikeWarning(message) -> "⚠"
            else -> "ℹ"
        }

        return "$icon $message"
    }

    private fun looksLikeSuccess(message: String): Boolean {
        val words = listOf(
            "با موفقیت",
            "ذخیره شد",
            "ثبت شد",
            "ثبت نهایی شد",
            "ارسال شد",
            "منتشر شد",
            "بازیابی شد",
            "حذف شد",
            "فعال شد",
            "غیرفعال شد",
            "بایگانی شد",
            "تخصیص داده شد",
            "منتقل شد",
            "خارج شد",
            "شروع شد",
            "تغییر کرد",
            "آماده ارسال است"
        )
        return words.any(message::contains)
    }

    private fun looksLikeError(message: String): Boolean {
        val words = listOf(
            "خطا",
            "انجام نشد",
            "کامل نشد",
            "ناموفق",
            "برقرار نشد",
            "قطع شد",
            "امکان‌پذیر نبود",
            "امکان پذیر نبود",
            "معتبر نیست",
            "در دسترس نیست",
            "لغو شد",
            "رد شد"
        )
        return words.any(message::contains)
    }

    private fun looksLikeWarning(message: String): Boolean {
        val words = listOf(
            "لطفاً",
            "ابتدا",
            "حداقل",
            "حداکثر",
            "الزامی است",
            "باید",
            "مجاز نیست",
            "قابل ویرایش نیست",
            "قابل انجام نیست",
            "امکان انجام",
            "انتخاب کنید",
            "وارد کنید",
            "کامل کنید",
            "منتشر نشده",
            "وجود ندارد",
            "مشخص نشده",
            "تأیید نشده"
        )
        return words.any(message::contains)
    }
}
