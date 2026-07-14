package com.example.bb

import android.content.Context
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import java.util.UUID

class CreateAnnouncementActivity : AppCompatActivity() {
    private data class AudienceOption(val label: String, val type: AudienceType, val targetId: String? = null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_announcement)
        findViewById<ImageView>(R.id.btnCreateBack).setOnClickListener { finish() }

        val prefs = getSharedPreferences("LocalAppPrefs", Context.MODE_PRIVATE)
        val phone = prefs.getString("CURRENT_USERNAME", "").orEmpty()
        val role = runCatching { UserRole.valueOf(intent.getStringExtra("USER_ROLE") ?: "TEACHER") }.getOrDefault(UserRole.TEACHER)
        val options = mutableListOf<AudienceOption>()
        if (role == UserRole.ADMIN) {
            options += AudienceOption("تمام دانش‌آموزان", AudienceType.ALL_STUDENTS)
            options += AudienceOption("تمام استادان", AudienceType.ALL_TEACHERS)
            options += AppDatabase.getAllClasses(false).map { AudienceOption("کلاس ${it.className}", AudienceType.CLASS, it.id) }
        } else {
            options += AppDatabase.getTeacherClasses(phone).map { AudienceOption("کلاس ${it.className}", AudienceType.CLASS, it.id) }
        }

        val audience = findViewById<AutoCompleteTextView>(R.id.dropdownAudience)
        audience.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, options.map { it.label }))
        val title = findViewById<TextInputEditText>(R.id.etMessageTitle)
        val body = findViewById<TextInputEditText>(R.id.etMessageBody)

        // بخش پیوست طبق تصمیم پروژه حفظ شده و هنگام اتصال API تکمیل می‌شود.
        findViewById<Button>(R.id.btnAttachFile).setOnClickListener {
            Toast.makeText(this, "پیوست در مرحله اتصال سرور فعال می‌شود", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnSendMessage).setOnClickListener {
            val selected = options.find { it.label == audience.text.toString() }
            val titleText = title.text.toString().trim()
            val bodyText = body.text.toString().trim()
            if (selected == null || titleText.isBlank() || bodyText.isBlank()) {
                Toast.makeText(this, "لطفاً مخاطب، عنوان و متن را کامل کنید", Toast.LENGTH_SHORT).show(); return@setOnClickListener
            }
            AppDatabase.addAnnouncement(Announcement(
                id = UUID.randomUUID().toString(), title = titleText, body = bodyText,
                senderName = AppDatabase.getDisplayName(role, phone), senderPhone = phone,
                date = AppDatabase.today(), audienceType = selected.type, targetId = selected.targetId
            ))
            Toast.makeText(this, "اعلان ارسال شد", Toast.LENGTH_SHORT).show(); finish()
        }
    }
}
