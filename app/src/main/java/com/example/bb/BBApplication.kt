package com.example.bb

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class BBApplication : Application() {
    override fun onCreate() {
        // Light is the application default. A user's explicit choice is restored
        // before the first Activity is drawn, so cold starts do not reset the theme.
        val darkMode = getSharedPreferences("ThemePrefs", MODE_PRIVATE)
            .getBoolean("IS_DARK_MODE", false)
        AppCompatDelegate.setDefaultNightMode(
            if (darkMode) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )

        super.onCreate()
        RetrofitClient.init(this)
        AppDatabase.init(this)
    }
}
