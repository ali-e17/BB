package com.example.bb

import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Context
import java.util.concurrent.Executors

class AppNotificationJobService : JobService() {

    private val executor = Executors.newSingleThreadExecutor()

    override fun onStartJob(params: JobParameters): Boolean {
        executor.execute {
            try {
                val prefs = getSharedPreferences("LocalAppPrefs", Context.MODE_PRIVATE)
                val token = SecureSessionStore.getToken(this)
                val userId = prefs.getString("CURRENT_USER_ID", "").orEmpty()
                val role = runCatching {
                    UserRole.valueOf(
                        prefs.getString("CURRENT_USER_ROLE", UserRole.STUDENT.name).orEmpty()
                    )
                }.getOrDefault(UserRole.STUDENT)

                if (token.isBlank() || userId.isBlank() || role == UserRole.ADMIN) {
                    return@execute
                }

                val response = RetrofitClient.instance.getDashboardBadges().execute()
                val body = response.body()
                if (response.isSuccessful && body?.status == "success") {
                    AppNotificationCenter.processSnapshot(this, role, userId, body)
                }
            } catch (_: Throwable) {
                // قطع اینترنت یا خطای موقت نباید باعث Crash یا حذف زمان‌بندی بعدی شود.
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
