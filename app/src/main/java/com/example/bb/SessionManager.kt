package com.example.bb

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("UserSessionPrefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_IS_LOGGED_IN = "isLoggedIn"
        private const val KEY_USERNAME = "username"
        private const val KEY_USER_ROLE = "userRole"
    }

    // ذخیره وضعیت لاگین هنگام ورود موفق
    fun createLoginSession(username: String, role: String) {
        prefs.edit().apply {
            putBoolean(KEY_IS_LOGGED_IN, true)
            putString(KEY_USERNAME, username)
            putString(KEY_USER_ROLE, role)
            apply()
        }
    }

    // چک کردن اینکه آیا کاربر قبلاً لاگین کرده یا نه
    fun isLoggedIn(): Boolean {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false)
    }

    // دریافت نقش کاربر (مدیر یا دانش‌آموز) در صورت نیاز
    fun getUserRole(): String? {
        return prefs.getString(KEY_USER_ROLE, null)
    }

    // پاک کردن اطلاعات سشن هنگام خروج از حساب
    fun logoutUser() {
        prefs.edit().clear().apply()
    }
}