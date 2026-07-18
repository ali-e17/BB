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
    private lateinit var adapter: AnnouncementAdapter
    private lateinit var role: UserRole
    private var phone: String = ""
    private lateinit var emptyState: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_announcements)

        val prefs = getSharedPreferences("LocalAppPrefs", Context.MODE_PRIVATE)
        role = runCatching {
            UserRole.valueOf(
                intent.getStringExtra("USER_ROLE")
                    ?: prefs.getString("CURRENT_USER_ROLE", "STUDENT").orEmpty()
            )
        }.getOrDefault(UserRole.STUDENT)
        phone = prefs.getString("CURRENT_USERNAME", "").orEmpty()

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }
        emptyState = findViewById(R.id.announcementEmptyState)

        recycler = findViewById(R.id.recyclerViewAnnouncements)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.setHasFixedSize(true)

        adapter = AnnouncementAdapter(
            role = role,
            phone = phone,
            onItemClick = { announcement ->
                AppDatabase.markAnnouncementRead(announcement.id, role, phone)
                startActivity(
                    Intent(this, AnnouncementDetailActivity::class.java)
                        .putExtra("ANNOUNCEMENT_ID", announcement.id)
                        .putExtra("USER_ROLE", role.name)
                )
            }
        )
        recycler.adapter = adapter

        findViewById<FloatingActionButton>(R.id.fabCreateMessage).apply {
            visibility = if (role == UserRole.STUDENT) View.GONE else View.VISIBLE
            setOnClickListener {
                startActivity(
                    Intent(this@AnnouncementsActivity, CreateAnnouncementActivity::class.java)
                        .putExtra("USER_ROLE", role.name)
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val items = AppDatabase.getAnnouncementsFor(role, phone)
        adapter.updateData(items)
        recycler.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        emptyState.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }
}
