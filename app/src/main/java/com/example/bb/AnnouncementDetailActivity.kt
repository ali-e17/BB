package com.example.bb

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

class AnnouncementDetailActivity : AppCompatActivity() {

    private lateinit var announcement: Announcement
    private var pendingLocalAttachment: Uri? = null

    private val saveLocalAttachment =
        registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { destination ->
            val source = pendingLocalAttachment ?: return@registerForActivityResult
            if (destination == null) return@registerForActivityResult

            runCatching {
                openSourceStream(source)?.use { input ->
                    contentResolver.openOutputStream(destination)?.use { output ->
                        input.copyTo(output)
                    } ?: error("Cannot open destination")
                } ?: error("Cannot open attachment")
            }.onSuccess {
                Toast.makeText(this, "پیوست ذخیره شد", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(this, "ذخیره پیوست انجام نشد", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_announcement_detail)

        findViewById<ImageView>(R.id.btnDetailBack).setOnClickListener { finish() }

        @Suppress("DEPRECATION")
        val passed = intent.getSerializableExtra("ANNOUNCEMENT_DATA") as? Announcement
        val fallbackId = intent.getStringExtra("ANNOUNCEMENT_ID").orEmpty()
        val loaded = passed ?: AppDatabase.getAnnouncementById(fallbackId)
        if (loaded == null) {
            Toast.makeText(this, "این اعلان دیگر در دسترس نیست", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        announcement = loaded
        render()
        markReadOnline()
    }

    private fun markReadOnline() {
        RetrofitClient.instance
            .markAnnouncementRead(MarkAnnouncementReadRequest(announcement.id))
            .enqueue(object : Callback<ApiResponse> {
                override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) = Unit
                override fun onFailure(call: Call<ApiResponse>, t: Throwable) = Unit
            })
    }

    private fun render() {
        val titleView = findViewById<TextView>(R.id.txtDetailToolbarTitle)
        titleView.text = announcement.title
        applyCenteredDirection(titleView, announcement.title)

        findViewById<TextView>(R.id.txtDetailSender).text = announcement.senderName
        findViewById<TextView>(R.id.txtDetailRole).text = when (announcement.senderRole) {
            AnnouncementSenderRole.ADMIN -> "مدیر"
            AnnouncementSenderRole.TEACHER -> "استاد"
            AnnouncementSenderRole.SYSTEM -> "سامانه"
        }
        findViewById<TextView>(R.id.txtDetailDate).text = announcement.createdAt
        findViewById<TextView>(R.id.txtDetailTarget).text = targetSummary(announcement)

        val bodyView = findViewById<TextView>(R.id.txtDetailBody)
        bodyView.text = announcement.body
        applyDynamicAlignment(bodyView, announcement.body, Gravity.TOP)

        renderAttachment()
    }

    private fun targetSummary(item: Announcement): String = when (item.scope) {
        AnnouncementScope.ALL_CLASSES -> "همه کلاس‌ها"
        AnnouncementScope.DIRECT_STUDENT -> "پیام شخصی سامانه"
        AnnouncementScope.SELECTED_CLASSES -> {
            val names = item.targetClassIds.mapNotNull(AppDatabase::getClassNameById)
            when {
                names.isNotEmpty() -> names.joinToString("، ")
                item.targetClassIds.isNotEmpty() -> "${item.targetClassIds.size} کلاس انتخابی"
                else -> "کلاس‌های انتخابی"
            }
        }
    }

    private fun applyCenteredDirection(view: TextView, text: CharSequence?) {
        view.textDirection = if (isRtlText(text)) View.TEXT_DIRECTION_RTL else View.TEXT_DIRECTION_LTR
        view.gravity = Gravity.CENTER
    }

    private fun applyDynamicAlignment(
        view: TextView,
        text: CharSequence?,
        verticalGravity: Int = Gravity.CENTER_VERTICAL
    ) {
        val rtl = isRtlText(text)
        view.textDirection = if (rtl) View.TEXT_DIRECTION_RTL else View.TEXT_DIRECTION_LTR
        view.gravity = (if (rtl) Gravity.RIGHT else Gravity.LEFT) or verticalGravity
    }

    private fun isRtlText(text: CharSequence?): Boolean {
        if (text.isNullOrBlank()) return true
        for (char in text) {
            when (Character.getDirectionality(char)) {
                Character.DIRECTIONALITY_RIGHT_TO_LEFT,
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC,
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING,
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE -> return true

                Character.DIRECTIONALITY_LEFT_TO_RIGHT,
                Character.DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING,
                Character.DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE -> return false
            }
        }
        return true
    }

    private fun renderAttachment() {
        val card = findViewById<MaterialCardView>(R.id.cardAttachment)
        if (!announcement.hasAttachment) {
            card.visibility = View.GONE
            return
        }

        card.visibility = View.VISIBLE
        findViewById<TextView>(R.id.txtFileName).text = announcement.attachmentName ?: "فایل پیوست"

        val meta = mutableListOf<String>()
        announcement.attachmentMimeType?.takeIf { it.isNotBlank() }?.let(meta::add)
        announcement.attachmentSizeBytes?.let { meta += formatFileSize(it) }
        findViewById<TextView>(R.id.txtFileTypeLabel).text =
            meta.takeIf { it.isNotEmpty() }?.joinToString(" • ") ?: "پیوست اعلان"

        findViewById<MaterialButton>(R.id.btnDownloadAttachment).setOnClickListener {
            downloadAttachment()
        }
    }

    private fun downloadAttachment() {
        val rawUrl = announcement.attachmentUrl
        if (rawUrl.isNullOrBlank()) {
            Toast.makeText(this, "آدرس پیوست در دسترس نیست", Toast.LENGTH_SHORT).show()
            return
        }

        val uri = Uri.parse(rawUrl)
        when (uri.scheme?.lowercase()) {
            "http", "https" -> downloadRemoteFile(uri)
            "content", "file" -> {
                pendingLocalAttachment = uri
                saveLocalAttachment.launch(announcement.attachmentName ?: "attachment")
            }
            else -> openAttachment(uri)
        }
    }

    private fun downloadRemoteFile(uri: Uri) {
        runCatching {
            val fileName = sanitizeFileName(announcement.attachmentName ?: "announcement_attachment")
            val request = DownloadManager.Request(uri)
                .setTitle(fileName)
                .setDescription("دانلود پیوست اعلان")
                .setMimeType(announcement.attachmentMimeType ?: "application/octet-stream")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)

            val token = getSharedPreferences("LocalAppPrefs", Context.MODE_PRIVATE)
                .getString("API_TOKEN", "").orEmpty()
            if (token.isNotBlank()) {
                request.addRequestHeader("Authorization", "Bearer $token")
            }

            val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)
        }.onSuccess {
            Toast.makeText(this, "دانلود پیوست شروع شد", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(this, "شروع دانلود امکان‌پذیر نبود", Toast.LENGTH_LONG).show()
        }
    }

    private fun openAttachment(uri: Uri) {
        runCatching {
            startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, announcement.attachmentMimeType ?: "*/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            )
        }.onFailure {
            Toast.makeText(this, "امکان بازکردن پیوست وجود ندارد", Toast.LENGTH_LONG).show()
        }
    }

    private fun openSourceStream(uri: Uri): InputStream? = when (uri.scheme?.lowercase()) {
        "file" -> uri.path?.let { FileInputStream(File(it)) }
        else -> contentResolver.openInputStream(uri)
    }

    private fun sanitizeFileName(value: String): String =
        value.replace(Regex("[\\/:*?\"<>|]"), "_")

    private fun formatFileSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes بایت"
        bytes < 1024 * 1024 -> String.format("%.1f کیلوبایت", bytes / 1024.0)
        else -> String.format("%.1f مگابایت", bytes / (1024.0 * 1024.0))
    }
}
