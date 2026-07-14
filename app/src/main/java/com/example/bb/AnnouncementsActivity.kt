package com.example.bb

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class AnnouncementsActivity : AppCompatActivity() {
    private lateinit var recycler: RecyclerView
    private lateinit var role: UserRole
    private var phone: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_announcements)
        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }
        recycler = findViewById(R.id.recyclerViewAnnouncements)
        recycler.layoutManager = LinearLayoutManager(this)

        val prefs = getSharedPreferences("LocalAppPrefs", Context.MODE_PRIVATE)
        role = runCatching { UserRole.valueOf(intent.getStringExtra("USER_ROLE") ?: prefs.getString("CURRENT_USER_ROLE", "STUDENT")!!) }.getOrDefault(UserRole.STUDENT)
        phone = prefs.getString("CURRENT_USERNAME", "").orEmpty()

        findViewById<FloatingActionButton>(R.id.fabCreateMessage).apply {
            visibility = if (role == UserRole.STUDENT) View.GONE else View.VISIBLE
            setOnClickListener {
                startActivity(Intent(this@AnnouncementsActivity, CreateAnnouncementActivity::class.java).putExtra("USER_ROLE", role.name))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        recycler.adapter = AnnouncementAdapter(AppDatabase.getAnnouncementsFor(role, phone)) { message ->
            startActivity(Intent(this, AnnouncementDetailActivity::class.java).apply {
                putExtra("MSG_TITLE", message.title); putExtra("MSG_SENDER", message.senderName)
                putExtra("MSG_BODY", message.body); putExtra("MSG_DATE", message.date)
                putExtra("MSG_TYPE", message.type.name); putExtra("MSG_FILE", message.attachmentName)
            })
        }
    }
}
