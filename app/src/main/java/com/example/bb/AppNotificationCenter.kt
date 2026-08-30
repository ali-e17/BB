package com.example.bb

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build

object AppNotificationScheduler {
    private const val JOB_ID = 2608301
    private const val PERIOD_MS = 15L * 60L * 1000L

    fun schedule(context: Context) {
        if (Build.VERSION.SDK_INT < 21) return
        val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        val component = ComponentName(context, AppNotificationJobService::class.java)
        val info = JobInfo.Builder(JOB_ID, component)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPersisted(true)
            .setPeriodic(PERIOD_MS)
            .build()
        scheduler.schedule(info)
    }
}

object AppNotificationCenter {
    private const val CHANNEL_ANNOUNCEMENTS = "bb_announcements"
    private const val CHANNEL_REPORTS = "bb_report_cards"
    private const val PREFS = "BackgroundNotificationPrefs"
    private const val NOTIFICATION_ANNOUNCEMENT = 31001
    private const val NOTIFICATION_REPORT = 31002

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val announcement = NotificationChannel(
            CHANNEL_ANNOUNCEMENTS,
            "اعلان‌های جدید",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "اعلان‌های دریافتی آموزشگاه"
            enableLights(true)
            lightColor = Color.parseColor("#FF6E14")
        }
        val report = NotificationChannel(
            CHANNEL_REPORTS,
            "کارنامه",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "انتشار یا بروزرسانی کارنامه"
            enableLights(true)
            lightColor = Color.parseColor("#2B4E78")
        }
        manager.createNotificationChannel(announcement)
        manager.createNotificationChannel(report)
    }

    fun processSnapshot(
        context: Context,
        role: UserRole,
        userId: String,
        snapshot: DashboardBadgesResponse
    ) {
        if (role == UserRole.ADMIN || userId.isBlank()) return

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val suffix = "_${role.name}_$userId"

        val announcementKey = "LATEST_ANNOUNCEMENT$suffix"
        val newAnnouncementSignature = snapshot.latestAnnouncementId.trim()
        val oldAnnouncementSignature = prefs.getString(announcementKey, null)
        if (oldAnnouncementSignature == null) {
            prefs.edit().putString(announcementKey, newAnnouncementSignature).apply()
        } else if (
            newAnnouncementSignature.isNotBlank() &&
            newAnnouncementSignature != oldAnnouncementSignature
        ) {
            if (snapshot.announcementUnreadCount > 0) {
                showAnnouncementNotification(
                    context,
                    role,
                    snapshot.latestAnnouncementTitle.trim()
                )
            }
            prefs.edit().putString(announcementKey, newAnnouncementSignature).apply()
        } else if (newAnnouncementSignature.isBlank() && oldAnnouncementSignature.isNotBlank()) {
            prefs.edit().putString(announcementKey, "").apply()
        }

        if (role != UserRole.STUDENT) return

        val reportKey = "LATEST_REPORT$suffix"
        val newReportSignature = snapshot.latestReportCardId.trim().let { id ->
            if (id.isBlank()) "" else "$id:${snapshot.latestReportCardRevision}"
        }
        val oldReportSignature = prefs.getString(reportKey, null)
        if (oldReportSignature == null) {
            prefs.edit().putString(reportKey, newReportSignature).apply()
        } else if (newReportSignature.isNotBlank() && newReportSignature != oldReportSignature) {
            if (snapshot.reportCardUpdateCount > 0) {
                showReportNotification(context, role, snapshot.latestReportCardRevision)
            }
            prefs.edit().putString(reportKey, newReportSignature).apply()
        } else if (newReportSignature.isBlank() && oldReportSignature.isNotBlank()) {
            prefs.edit().putString(reportKey, "").apply()
        }
    }

    private fun showAnnouncementNotification(context: Context, role: UserRole, title: String) {
        val message = title.ifBlank { "یک اعلان جدید برای شما ارسال شده است" }
        notify(
            context = context,
            notificationId = NOTIFICATION_ANNOUNCEMENT,
            channelId = CHANNEL_ANNOUNCEMENTS,
            role = role,
            title = "پیام جدید",
            message = message,
            accent = Color.parseColor("#FF6E14")
        )
    }

    private fun showReportNotification(context: Context, role: UserRole, revision: Int) {
        val message = if (revision > 1) {
            "کارنامه شما بروزرسانی و دوباره منتشر شد."
        } else {
            "کارنامه جدید شما منتشر شد."
        }
        notify(
            context = context,
            notificationId = NOTIFICATION_REPORT,
            channelId = CHANNEL_REPORTS,
            role = role,
            title = if (revision > 1) "کارنامه بروزرسانی شد" else "کارنامه منتشر شد",
            message = message,
            accent = Color.parseColor("#2B4E78")
        )
    }

    private fun notify(
        context: Context,
        notificationId: Int,
        channelId: String,
        role: UserRole,
        title: String,
        message: String,
        accent: Int
    ) {
        ensureChannels(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("USER_ROLE", role.name)
        }
        val pending = PendingIntent.getActivity(
            context,
            notificationId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(context, channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }

        builder
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(Notification.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setColor(accent)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)

        if (Build.VERSION.SDK_INT >= 21) {
            builder.setCategory(Notification.CATEGORY_MESSAGE)
            builder.setVisibility(Notification.VISIBILITY_PRIVATE)
        }

        manager.notify(notificationId, builder.build())
    }
}
