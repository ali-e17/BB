package com.example.bb

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class AnnouncementsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_announcements)

        // فعال‌سازی دکمه برگشت
        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewAnnouncements)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val mockData = listOf(
            Announcement("1", "تغییر ساعت کلاس ترم ۶", "کلاس فردا به جای ساعت ۱۰، ساعت ۱۳ برگزار می‌شود.", "آموزشگاه", "امروز", MessageType.TEXT_ONLY),
            Announcement("2", "جزوه فصل سوم زبان عمومی", "جزوه گرامر پیشرفته آپلود شد.", "استاد کمالی", "دیروز", MessageType.FILE_UPLOAD, "Grammar_Session3.pdf"),
            Announcement("3", "تکلیف Essay Writing", "تا پایان هفته یک متن ۲۰۰ کلمه‌ای ارسال کنید.", "استاد ريحان", "۳ روز پیش", MessageType.ASSIGNMENT, "Guide.docx")
        )

        recyclerView.adapter = AnnouncementAdapter(mockData) { clickedMsg ->
            val intent = Intent(this, AnnouncementDetailActivity::class.java).apply {
                putExtra("MSG_TITLE", clickedMsg.title)
                putExtra("MSG_SENDER", clickedMsg.senderName)
                putExtra("MSG_BODY", clickedMsg.body)
                putExtra("MSG_DATE", clickedMsg.date)
                putExtra("MSG_TYPE", clickedMsg.type.name)
                putExtra("MSG_FILE", clickedMsg.attachmentName)
            }
            startActivity(intent)
        }
    }
}