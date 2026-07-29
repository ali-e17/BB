package com.example.bb

object ApiConfig {
    // دامنه به‌عنوان Host ارسال می‌شود تا Apache پوشه public_html همین اکانت را انتخاب کند.
    // تا زمان اصلاح DNS عمومی، OkHttp این دامنه را مستقیماً به IP هاست متصل می‌کند.
    const val BASE_URL = "http://bayanebartar95.ir/api/"
    const val API_HOST = "bayanebartar95.ir"
    const val API_IP = "5.144.129.239"
}
