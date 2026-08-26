package com.example.bb

object ApiConfig {
    // دامنه به‌عنوان Host ارسال می‌شود تا Apache پوشه public_html همین اکانت را انتخاب کند.
    // تا زمان اصلاح DNS عمومی، OkHttp این دامنه را مستقیماً به IP هاست متصل می‌کند.
    const val BASE_URL = "https://bayan-e-bartar.ir/api/"
    const val API_HOST = "bayan-e-bartar.ir     "
    const val API_IP = "5.144.129.239"
}
