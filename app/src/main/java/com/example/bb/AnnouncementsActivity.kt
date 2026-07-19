package com.example.bb

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class AnnouncementsActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var adapter: AnnouncementAdapter
    private lateinit var role: UserRole
    private var phone: String = ""
    private lateinit var emptyState: View
    private lateinit var loading: View

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
        loading = findViewById(R.id.progressAnnouncements)

        recycler = findViewById(R.id.recyclerViewAnnouncements)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.setHasFixedSize(true)

        adapter = AnnouncementAdapter { announcement ->
            adapter.markRead(announcement.id)
            startActivity(
                Intent(this, AnnouncementDetailActivity::class.java)
                    .putExtra("ANNOUNCEMENT_DATA", announcement.copy(isRead = true))
            )
        }
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
        loadAnnouncements()
    }

    private fun loadAnnouncements() {
        setLoading(true)
        RetrofitClient.instance.getAnnouncements().enqueue(object : Callback<List<Announcement>> {
            override fun onResponse(
                call: Call<List<Announcement>>,
                response: Response<List<Announcement>>
            ) {
                setLoading(false)
                if (response.isSuccessful) {
                    render(response.body().orEmpty())
                } else {
                    showLocalFallback("دریافت اعلانات آنلاین انجام نشد")
                }
            }

            override fun onFailure(call: Call<List<Announcement>>, t: Throwable) {
                setLoading(false)
                showLocalFallback("اتصال به سرور برقرار نشد؛ اعلانات ذخیره‌شده دستگاه نمایش داده شدند")
            }
        })
    }

    private fun showLocalFallback(message: String) {
        val local = AppDatabase.getAnnouncementsFor(role, phone).map {
            it.copy(isRead = AppDatabase.isAnnouncementRead(it.id, role, phone))
        }
        render(local)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun render(items: List<Announcement>) {
        adapter.updateData(items)
        recycler.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        emptyState.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun setLoading(value: Boolean) {
        loading.visibility = if (value) View.VISIBLE else View.GONE
        if (value) {
            emptyState.visibility = View.GONE
            recycler.visibility = View.INVISIBLE
        } else {
            recycler.visibility = View.VISIBLE
        }
    }
}
