package com.example.bb

import com.google.gson.Gson
import retrofit2.Response
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Converts API and network failures into messages that are suitable for end users.
 * Internal HTTP codes and exception details must never be shown directly in the UI.
 */
object ApiErrorParser {
    private val gson = Gson()

    fun parse(response: Response<*>): ApiResponse? {
        if (response.isSuccessful) return null
        val raw = runCatching { response.errorBody()?.string() }
            .getOrNull()
            .orEmpty()
        if (raw.isBlank()) return null
        return runCatching { gson.fromJson(raw, ApiResponse::class.java) }.getOrNull()
    }

    fun userMessage(response: Response<*>, fallback: String): String =
        userMessage(response, parse(response), fallback)

    fun userMessage(response: Response<*>, parsed: ApiResponse?, fallback: String): String {
        val apiMessage = parsed?.message?.trim().orEmpty()
        if (apiMessage.isNotBlank()) return apiMessage

        return when (response.code()) {
            400 -> "اطلاعات ارسال‌شده قابل پردازش نیست. موارد واردشده را بررسی کنید."
            401 -> "نشست شما پایان یافته است. لطفاً دوباره وارد حساب شوید."
            403 -> "شما اجازه انجام این عملیات را ندارید."
            404 -> "اطلاعات موردنظر پیدا نشد یا دیگر در دسترس نیست."
            408 -> "زمان دریافت پاسخ تمام شد. دوباره تلاش کنید."
            409 -> "اطلاعات تغییر کرده است. صفحه را تازه‌سازی کنید و دوباره تلاش کنید."
            422 -> "بعضی از اطلاعات واردشده معتبر نیست. موارد مشخص‌شده را اصلاح کنید."
            428 -> "برای ادامه ابتدا باید رمز عبور اولیه را تغییر دهید."
            429 -> "تعداد درخواست‌ها زیاد بوده است. کمی بعد دوباره تلاش کنید."
            in 500..599 -> "سرور موقتاً قادر به انجام درخواست نیست. کمی بعد دوباره تلاش کنید."
            else -> fallback
        }
    }


    fun messageFromRawJson(raw: String, fallback: String): String {
        if (raw.isBlank()) return fallback
        return runCatching { gson.fromJson(raw, ApiResponse::class.java) }
            .getOrNull()
            ?.message
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: fallback
    }

    fun networkMessage(error: Throwable, action: String = "انجام عملیات"): String = when (error) {
        is UnknownHostException, is ConnectException ->
            "اتصال به اینترنت یا سرور برقرار نیست. اینترنت خود را بررسی کنید."
        is SocketTimeoutException ->
            "پاسخ سرور بیش از حد طول کشید. دوباره تلاش کنید."
        is SSLException ->
            "اتصال امن با سرور برقرار نشد. تاریخ و ساعت گوشی و اینترنت را بررسی کنید."
        is java.io.IOException ->
            "ارتباط با سرور قطع شد. دوباره تلاش کنید."
        else ->
            "$action انجام نشد. دوباره تلاش کنید."
    }
}
