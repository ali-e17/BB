package com.example.bb

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText

class CreateAnnouncementActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_announcement)

        findViewById<ImageView>(R.id.btnCreateBack).setOnClickListener { finish() }

        val userRole = intent.getStringExtra("USER_ROLE") ?: "TEACHER"
        val dropdownAudience = findViewById<AutoCompleteTextView>(R.id.dropdownAudience)

        // تنظیم لیست مخاطبان بر اساس نقش
        val audiences = if (userRole == "ADMIN") {
            arrayOf("تمامی دانشجویان آموزشگاه", "تمامی اساتید", "کلاس ترم ۶", "کلاس آیلتس فشرده")
        } else {
            arrayOf("کلاس ترم ۶ (استاد کمالی)", "کلاس آیلتس فشرده (استاد کمالی)")
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, audiences)
        dropdownAudience.setAdapter(adapter)

        // مدیریت دکمه ارسال
        val btnSend = findViewById<Button>(R.id.btnSendMessage)
        val etTitle = findViewById<TextInputEditText>(R.id.etMessageTitle)
        val etBody = findViewById<TextInputEditText>(R.id.etMessageBody)

        btnSend.setOnClickListener {
            val title = etTitle.text.toString().trim()
            val body = etBody.text.toString().trim()
            val target = dropdownAudience.text.toString()

            if (title.isEmpty() || body.isEmpty() || target.isEmpty()) {
                Toast.makeText(this, "لطفاً تمامی فیلدها را پر کنید", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            Toast.makeText(this, "پیام شما با موفقیت برای «$target» ارسال شد", Toast.LENGTH_LONG).show()
            finish() // بستن صفحه و برگشت به صندوق ورودی
        }
    }
}