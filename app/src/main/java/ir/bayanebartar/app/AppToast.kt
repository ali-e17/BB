package ir.bayanebartar.app

import android.content.Context
import android.widget.Toast

/**
 * Toast یکپارچه برنامه.
 *
 * نکته مهم:
 * Toast یک پیام کوتاه رابط کاربری است؛ برای جلوگیری از جابه‌جایی بصری نقطه
 * در متن‌های RTL، در انتهای پیام‌های خبری نقطه قرار نمی‌دهیم.
 *
 * همه پیام‌ها قبل از نمایش:
 * - از کنترل‌های جهت‌دهی قدیمی پاک می‌شوند
 * - حروف «ي/ى/ك» به «ی/ی/ک» فارسی تبدیل می‌شوند
 * - فاصله و نیم‌فاصله‌های رایج یکدست می‌شوند
 * - عبارت‌های محاوره‌ای رایج رسمی‌تر می‌شوند
 *
 * API قبلی AppToast حفظ شده و نیازی به تغییر Activityها نیست.
 */
object AppToast {

    private var activeToast: Toast? = null

    fun makeText(
        context: Context,
        text: CharSequence?,
        duration: Int
    ): Toast {
        activeToast?.cancel()

        val toast = Toast.makeText(
            context.applicationContext,
            normalizeMessage(text?.toString().orEmpty()),
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
        show(context, message, duration)
    }

    fun warning(
        context: Context,
        message: CharSequence,
        duration: Int = Toast.LENGTH_LONG
    ) {
        show(context, message, duration)
    }

    fun error(
        context: Context,
        message: CharSequence,
        duration: Int = Toast.LENGTH_LONG
    ) {
        show(context, message, duration)
    }

    fun info(
        context: Context,
        message: CharSequence,
        duration: Int = Toast.LENGTH_SHORT
    ) {
        show(context, message, duration)
    }

    private fun show(
        context: Context,
        message: CharSequence,
        duration: Int
    ) {
        activeToast?.cancel()

        activeToast = Toast.makeText(
            context.applicationContext,
            normalizeMessage(message.toString()),
            duration
        ).also { it.show() }
    }

    private fun normalizeMessage(raw: String): String {
        var message = raw
            // حذف LRM/RLM/embedding/isolateهای احتمالیِ رسیده از API یا نسخه‌های قبلی
            .replace(
                Regex("[\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069]"),
                ""
            )
            .trim()
            .replace(Regex("\\s+"), " ")
            // حروف استاندارد فارسی
            .replace('ي', 'ی')
            .replace('ى', 'ی')
            .replace('ك', 'ک')

        if (message.isBlank()) {
            return "امکان انجام این عملیات وجود ندارد"
        }

        // اگر نسخه قدیمی یک علامت را ابتدای رشته گذاشته باشد، پاکش می‌کنیم.
        message = message.replace(
            Regex("^[\\s.،؛!?؟…]+"),
            ""
        )

        // لحن رسمی و نگارش یکدست
        message = message
            .replace("دوباره بازش کنید", "مجدداً باز کنید")
            .replace("دوباره باز کنید", "مجدداً باز کنید")
            .replace("دوباره تلاش کنید", "مجدداً تلاش کنید")
            .replace("دوباره امتحان کنید", "مجدداً تلاش کنید")
            .replace("لطفا", "لطفاً")
            .replace("مجددا", "مجدداً")
            .replace("یک بار", "یک‌بار")
            .replace("به صورت", "به‌صورت")
            .replace("به عنوان", "به‌عنوان")
            .replace("می شود", "می‌شود")
            .replace("نمی شود", "نمی‌شود")
            .replace("می گردد", "می‌گردد")
            .replace("نمی گردد", "نمی‌گردد")
            .replace(
                "تنظیمات بدون تغییر است",
                "تغییری در تنظیمات ایجاد نشده است"
            )

        // فاصله قبل/بعد از علائم فارسی
        message = message
            .replace(Regex("\\s+([،؛؟!…])"), "$1")
            .replace(Regex("([،؛])(?=\\S)"), "$1 ")
            .replace(Regex("\\.{3,}"), "…")

        // سؤال فارسی با علامت سؤال فارسی تمام شود.
        if (containsPersian(message) && message.endsWith("?")) {
            message = message.dropLast(1) + "؟"
        }

        /*
         * مهم: نقطه انتهای Toast را حذف می‌کنیم.
         * در پیام کوتاه رابط کاربری از نظر نگارشی الزامی نیست و در بعضی نسخه‌های
         * Android/فونت‌های RTL همان نقطه به ابتدای جمله رندر می‌شود.
         * نقطه‌های داخل جمله دست‌نخورده می‌مانند.
         */
        message = message.trimEnd()
        while (message.endsWith(".")) {
            message = message.dropLast(1).trimEnd()
        }

        return message.ifBlank {
            "امکان انجام این عملیات وجود ندارد"
        }
    }

    private fun containsPersian(value: String): Boolean {
        return value.any { ch ->
            ch in '\u0600'..'\u06FF' ||
                    ch in '\u0750'..'\u077F' ||
                    ch in '\u08A0'..'\u08FF'
        }
    }
}
