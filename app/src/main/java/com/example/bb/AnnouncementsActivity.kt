package com.example.bb

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class AnnouncementsActivity : BaseActivity() {

    private enum class Mailbox { INBOX, SENT }

    private lateinit var recycler: RecyclerView
    private lateinit var adapter: AnnouncementAdapter
    private lateinit var role: UserRole
    private var identityKey: String = ""
    private lateinit var emptyState: View
    private lateinit var emptyTitle: TextView
    private lateinit var emptyMessage: TextView
    private lateinit var loading: View
    private lateinit var hint: TextView
    private lateinit var toggleGroup: MaterialButtonToggleGroup
    private lateinit var btnInbox: MaterialButton
    private lateinit var btnSent: MaterialButton
    private var mailbox: Mailbox = Mailbox.INBOX

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
        identityKey = prefs.getString("CURRENT_USER_ID", "").orEmpty()

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }
        emptyState = findViewById(R.id.announcementEmptyState)
        emptyTitle = findViewById(R.id.txtAnnouncementEmptyTitle)
        emptyMessage = findViewById(R.id.txtAnnouncementEmptyMessage)
        loading = findViewById(R.id.progressAnnouncements)
        hint = findViewById(R.id.txtAnnouncementsHint)
        toggleGroup = findViewById(R.id.toggleAnnouncementBox)
        btnInbox = findViewById(R.id.btnAnnouncementInbox)
        btnSent = findViewById(R.id.btnAnnouncementSent)

        recycler = findViewById(R.id.recyclerViewAnnouncements)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.setHasFixedSize(true)

        adapter = AnnouncementAdapter { announcement ->
            if (mailbox == Mailbox.INBOX) {
                adapter.markRead(announcement.id)
                AppDatabase.markAnnouncementRead(announcement.id, role, identityKey)
                if (!announcement.isRead) decrementCachedUnreadBadge()
            }
            startActivity(
                Intent(this, AnnouncementDetailActivity::class.java)
                    .putExtra(
                        "ANNOUNCEMENT_DATA",
                        if (mailbox == Mailbox.INBOX) announcement.copy(isRead = true) else announcement
                    )
            )
        }
        recycler.adapter = adapter

        setupMailboxUi()

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

    private fun setupMailboxUi() {
        when (role) {
            UserRole.TEACHER -> {
                toggleGroup.visibility = View.VISIBLE
                mailbox = Mailbox.INBOX
                toggleGroup.check(R.id.btnAnnouncementInbox)
                toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
                    if (!isChecked) return@addOnButtonCheckedListener
                    val newMailbox = if (checkedId == R.id.btnAnnouncementSent) {
                        Mailbox.SENT
                    } else {
                        Mailbox.INBOX
                    }
                    if (mailbox != newMailbox) {
                        mailbox = newMailbox
                        renderMailboxLabels()
                        loadAnnouncements()
                    }
                }
            }

            UserRole.ADMIN -> {
                toggleGroup.visibility = View.GONE
                mailbox = Mailbox.SENT
            }

            UserRole.STUDENT -> {
                toggleGroup.visibility = View.GONE
                mailbox = Mailbox.INBOX
            }
        }
        renderMailboxLabels()
    }

    private fun renderMailboxLabels() {
        if (mailbox == Mailbox.SENT) {
            hint.text = "اعلان‌هایی که شما ارسال کرده‌اید"
            emptyTitle.text = "هنوز اعلانی ارسال نکرده‌اید"
            emptyMessage.text = "اعلان‌های ارسالی شما در این بخش نمایش داده می‌شوند."
        } else {
            hint.text = "جدیدترین اعلان‌های دریافتی در ابتدای فهرست قرار دارند"
            emptyTitle.text = "اعلان دریافتی وجود ندارد"
            emptyMessage.text = "اعلان‌های جدیدی که برای شما ارسال شوند در این بخش نمایش داده می‌شوند."
        }
    }

    override fun onResume() {
        super.onResume()
        loadAnnouncements()
    }

    private fun loadAnnouncements() {
        setLoading(true)
        val box = if (mailbox == Mailbox.SENT) "sent" else "inbox"
        RetrofitClient.instance.getAnnouncements(box = box)
            .enqueue(object : Callback<List<Announcement>> {
                override fun onResponse(
                    call: Call<List<Announcement>>,
                    response: Response<List<Announcement>>
                ) {
                    setLoading(false)
                    if (response.isSuccessful) {
                        val items = response.body().orEmpty()
                        AppDatabase.mergeAnnouncements(items)
                        if (mailbox == Mailbox.INBOX) {
                            items.filter { it.isRead }.forEach { item ->
                                AppDatabase.markAnnouncementRead(item.id, role, identityKey)
                            }
                        }
                        render(items)
                    } else {
                        showLocalFallback(
                            ApiErrorParser.userMessage(response, "دریافت اعلانات آنلاین کامل نشد") +
                                "؛ اعلانات ذخیره‌شده دستگاه نمایش داده شدند"
                        )
                    }
                }

                override fun onFailure(call: Call<List<Announcement>>, t: Throwable) {
                    setLoading(false)
                    showLocalFallback(
                        ApiErrorParser.networkMessage(t, "دریافت اعلانات") +
                            " اعلانات ذخیره‌شده دستگاه نمایش داده شدند."
                    )
                }
            })
    }

    private fun decrementCachedUnreadBadge() {
        if (mailbox != Mailbox.INBOX) return
        val prefs = getSharedPreferences("DashboardBadgePrefs", Context.MODE_PRIVATE)
        val cacheKey = "CACHED_ANNOUNCEMENT_UNREAD_${role.name}_${identityKey}"
        val current = prefs.getInt(cacheKey, 0)
        if (current <= 0) return
        prefs.edit()
            .putInt(cacheKey, (current - 1).coerceAtLeast(0))
            .apply()
    }

    private fun showLocalFallback(message: String) {
        val allLocal = AppDatabase.getAnnouncementsFor(role, identityKey)
        val local = when (mailbox) {
            Mailbox.SENT -> allLocal.filter { it.senderId == identityKey }
            Mailbox.INBOX -> when (role) {
                UserRole.TEACHER -> allLocal.filter {
                    it.senderId != identityKey && it.scope == AnnouncementScope.ALL_TEACHERS
                }
                else -> allLocal.filter { it.senderId != identityKey }
            }
        }.map {
            if (mailbox == Mailbox.INBOX) {
                it.copy(isRead = AppDatabase.isAnnouncementRead(it.id, role, identityKey))
            } else {
                it.copy(isRead = true)
            }
        }
        render(local)
        AppToast.makeText(this, message, Toast.LENGTH_LONG).show()
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
