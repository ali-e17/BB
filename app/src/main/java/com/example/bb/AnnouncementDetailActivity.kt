package com.example.bb

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView

class AnnouncementDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_announcement_detail)

        // فعال‌سازی دکمه برگشت
        findViewById<ImageView>(R.id.btnDetailBack).setOnClickListener { finish() }

        val title = intent.getStringExtra("MSG_TITLE") ?: "بدون عنوان"
        val sender = intent.getStringExtra("MSG_SENDER") ?: "ناشناس"
        val body = intent.getStringExtra("MSG_BODY") ?: ""
        val date = intent.getStringExtra("MSG_DATE") ?: ""
        val typeString = intent.getStringExtra("MSG_TYPE") ?: "TEXT_ONLY"
        val fileName = intent.getStringExtra("MSG_FILE")

        findViewById<TextView>(R.id.txtDetailTitle).text = title
        findViewById<TextView>(R.id.txtDetailSender).text = sender
        findViewById<TextView>(R.id.txtDetailDate).text = date
        findViewById<TextView>(R.id.txtDetailBody).text = body
        findViewById<TextView>(R.id.txtDetailAvatar).text = sender.firstOrNull()?.toString() ?: "B"

        val cardAttachment = findViewById<MaterialCardView>(R.id.cardAttachment)
        val txtFileName = findViewById<TextView>(R.id.txtFileName)
        val txtFileTypeLabel = findViewById<TextView>(R.id.txtFileTypeLabel)

        if (typeString != "TEXT_ONLY" && fileName != null) {
            cardAttachment.visibility = View.VISIBLE
            txtFileName.text = fileName
            txtFileTypeLabel.text = if (typeString == "ASSIGNMENT") "تکلیف خانگی" else "جزوه آموزشی"

            cardAttachment.setOnClickListener {
                Toast.makeText(this, "در حال دانلود فایل: $fileName", Toast.LENGTH_SHORT).show()
            }
        } else {
            cardAttachment.visibility = View.GONE
        }
    }
}