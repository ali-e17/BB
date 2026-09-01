package ir.bayanebartar.app

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

        // Load the last successful config before Retrofit or any Activity needs it.
        // A fresh server copy is fetched asynchronously; offline launches continue
        // using the persisted device cache (or built-in defaults on first install).
        RemoteConfigManager.init(this)
        RetrofitClient.init(this)
        RemoteConfigManager.refreshIfStale(force = true)

        AppDatabase.init(this)

        // کانال‌ها و بررسی دوره‌ای اعلان/کارنامه بدون وابستگی به Firebase.
        AppNotificationCenter.ensureChannels(this)
        AppNotificationScheduler.schedule(this)
    }
}
