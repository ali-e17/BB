package com.example.bb

import android.app.Application

class BBApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        RetrofitClient.init(this)
        AppDatabase.init(this)
    }
}
