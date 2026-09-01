package ir.bayanebartar.app

import com.google.gson.Gson
import retrofit2.Response
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * تبدیل خطاهای API و شبکه به پیام‌های رسمی و قابل فهم برای کاربر.
 * جزئیات فنی، Exception خام یا کد HTTP مستقیماً در رابط کاربری نمایش داده نمی‌شود.
 */
object ApiErrorParser {
    private val gson = Gson()

    fun parse(response: Response<*>): ApiResponse? {
        if (response.isSuccessful) return null

        val raw = runCatching {
            response.errorBody()?.string()
        }.getOrNull().orEmpty()

        if (raw.isBlank()) return null

        return runCatching {
            gson.fromJson(raw, ApiResponse::class.java)
        }.getOrNull()
    }

    fun userMessage(
        response: Response<*>,
        fallback: String
    ): String = userMessage(
        response = response,
        parsed = parse(response),
        fallback = fallback
    )

    fun userMessage(
        response: Response<*>,
        parsed: ApiResponse?,
        fallback: String
    ): String {
        val apiMessage = parsed?.message?.trim().orEmpty()
        if (
            apiMessage.isNotBlank() &&
            apiMessage != "خطای داخلی سرور"
        ) {
            // پیام معتبر Backend معمولاً دقیق‌ترین علت خطاست؛ همان پیام در اولویت است.
            return apiMessage
        }

        messageForCode(parsed?.code)?.let { return it }

        val action = cleanFallback(fallback)

        return when (response.code()) {
            400 -> "$action؛ اطلاعات ارسال‌شده قابل پردازش نیست. لطفاً مقادیر واردشده را بررسی کنید."
            401 -> "نشست کاربری شما پایان یافته است. لطفاً مجدداً وارد حساب کاربری شوید."
            402 -> "اعتبار عضویت شما فعال نیست یا به پایان رسیده است. برای ادامه، وضعیت پرداخت و اعتبار عضویت را بررسی کنید."
            403 -> "$action؛ حساب کاربری شما مجوز انجام این عملیات را ندارد."
            404 -> "$action؛ اطلاعات موردنظر در دسترس نیست یا حذف شده است."
            408 -> "$action؛ زمان انتظار برای پاسخ سرور به پایان رسید. لطفاً مجدداً تلاش کنید."
            409 -> "$action؛ اطلاعات روی سرور تغییر کرده است. لطفاً صفحه را مجدداً باز کرده و عملیات را تکرار کنید."
            410 -> "$action؛ این قابلیت یا مسیر در نسخه فعلی سرور فعال نیست."
            413 -> "$action؛ حجم اطلاعات یا فایل ارسالی بیشتر از حد مجاز است."
            422 -> "$action؛ بخشی از اطلاعات واردشده ناقص یا نامعتبر است. لطفاً موارد فرم را بررسی کنید."
            428 -> "برای ادامه استفاده از برنامه، تغییر رمز عبور اولیه الزامی است."
            429 -> "$action؛ تعداد درخواست‌ها بیش از حد مجاز بوده است. لطفاً کمی بعد مجدداً تلاش کنید."
            in 500..599 -> "$action؛ سرور در حال حاضر قادر به تکمیل درخواست نیست. لطفاً کمی بعد مجدداً تلاش کنید."
            else -> fallback
        }
    }

    fun messageFromRawJson(
        raw: String,
        fallback: String
    ): String {
        if (raw.isBlank()) return fallback

        val parsed = runCatching {
            gson.fromJson(raw, ApiResponse::class.java)
        }.getOrNull()

        val apiMessage = parsed
            ?.message
            ?.trim()
            ?.takeIf { it.isNotBlank() && it != "خطای داخلی سرور" }

        if (apiMessage != null) return apiMessage

        messageForCode(parsed?.code)?.let { return it }
        return fallback
    }

    fun networkMessage(
        error: Throwable,
        action: String = "انجام عملیات"
    ): String = when (error) {
        is UnknownHostException,
        is ConnectException ->
            "$action کامل نشد؛ اتصال به اینترنت یا سرور برقرار نیست. لطفاً اتصال اینترنت را بررسی کنید."

        is SocketTimeoutException ->
            "$action کامل نشد؛ پاسخ سرور در زمان مقرر دریافت نشد. لطفاً چند لحظه بعد مجدداً تلاش کنید."

        is SSLException ->
            "$action کامل نشد؛ اتصال امن با سرور برقرار نشد. لطفاً تاریخ و ساعت دستگاه و اتصال اینترنت را بررسی کنید."

        is java.io.IOException ->
            "$action کامل نشد؛ ارتباط با سرور قطع شد. لطفاً اتصال اینترنت را بررسی کرده و مجدداً تلاش کنید."

        else ->
            "$action به دلیل بروز خطای ارتباطی کامل نشد. لطفاً مجدداً تلاش کنید."
    }

    private fun messageForCode(code: String?): String? {
        return when (code?.trim()?.uppercase()) {
            "UNAUTHENTICATED" ->
                "نشست کاربری شما معتبر نیست. لطفاً مجدداً وارد حساب کاربری شوید."

            "ACCOUNT_ARCHIVED" ->
                "حساب شما توسط مدیریت بایگانی شده است. برای فعال‌سازی مجدد با آموزشگاه تماس بگیرید."

            "INVALID_CREDENTIALS" ->
                "شناسه ورود یا رمز عبور اشتباه است."

            "CURRENT_PASSWORD_REQUIRED" ->
                "برای تغییر رمز عبور، وارد کردن رمز عبور فعلی الزامی است."

            "CURRENT_PASSWORD_INCORRECT" ->
                "رمز عبور فعلی صحیح نیست."

            "PASSWORD_REQUIRED" ->
                "وارد کردن رمز عبور جدید الزامی است."

            "PASSWORD_TOO_SHORT" ->
                "رمز عبور جدید باید حداقل ۸ کاراکتر باشد."

            "PASSWORD_CONTAINS_SPACE" ->
                "رمز عبور جدید نباید شامل فاصله باشد."

            "PASSWORD_SAME_AS_USERNAME" ->
                "رمز عبور جدید نباید با کد ملی شما یکسان باشد."

            "PASSWORD_UNCHANGED" ->
                "رمز عبور جدید باید با رمز عبور فعلی متفاوت باشد."

            "REVISION_CONFLICT" ->
                "اطلاعات توسط کاربر دیگری تغییر کرده است. لطفاً صفحه را مجدداً باز کرده و تغییرات را مجدداً اعمال کنید."

            "CONFIG_REVISION_CONFLICT" ->
                "معیارهای کارنامه تغییر کرده‌اند. لطفاً صفحه نمرات را مجدداً باز کنید."

            "PUBLISHED_CARD_REQUIRES_REPUBLISH" ->
                "این کارنامه قبلاً منتشر شده است. برای ذخیره تغییرات، از گزینه «انتشار مجدد» استفاده کنید."

            "ENDPOINT_RETIRED",
            "PAYMENT_API_DEPRECATED" ->
                "این قابلیت در نسخه فعلی برنامه قابل استفاده نیست. لطفاً برنامه را به‌روزرسانی کنید."

            else -> null
        }
    }

    private fun cleanFallback(fallback: String): String {
        val cleaned = fallback
            .trim()
            .trimEnd('.', '؛', '،')
            .replace(Regex("\\s+انجام نشد$"), "")
            .replace(Regex("\\s+ناموفق بود$"), "")
            .replace(Regex("\\s+کامل نشد$"), "")
            .trim()

        return cleaned.ifBlank { "انجام عملیات" }
    }
}
