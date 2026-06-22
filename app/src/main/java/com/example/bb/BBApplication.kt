package com.example.bb

import android.app.Application

class BBApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // راه‌اندازی دیتابیس کاربران
        AppDatabase.init(this)
    }
}