package com.example.bb

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

class BBApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val sharedPrefs = getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)

        // اگر کاربر هنوز هیچ دکمه‌ای برای تغییر تم نزده است (اولین ورود)
        if (!sharedPrefs.contains("IS_DARK_MODE")) {
            // دقیقاً از حالت فعلی گوشی (هر چه که هست) پیروی کن
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        } else {
            // اگر قبلاً دکمه را زده بود، بر اساس سلیقه ذخیره شده‌اش عمل کن
            val isDarkMode = sharedPrefs.getBoolean("IS_DARK_MODE", false)
            if (isDarkMode) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        }
    }
}