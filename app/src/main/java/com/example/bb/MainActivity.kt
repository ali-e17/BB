package com.example.bb
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

enum class UserRole { STUDENT, TEACHER, ADMIN }

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private var currentUserRole: UserRole = UserRole.STUDENT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.recyclerViewDashboard)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val items = mutableListOf<DashboardItem>()

        when (currentUserRole) {
            UserRole.STUDENT -> {
                items.add(DashboardItem("My Progress", "Check your latest grades", android.R.drawable.ic_menu_sort_by_size))
                items.add(DashboardItem("Dictionary", "Translate global phrases", android.R.drawable.ic_menu_search))
                items.add(DashboardItem("Assignments", "New coursework is ready", android.R.drawable.ic_menu_agenda))
                items.add(DashboardItem("Messages", "Latest academy updates", android.R.drawable.ic_dialog_email))
            }
            // بقیه نقش‌ها رو می‌تونی خودت اضافه کنی...
            else -> {}
        }

        recyclerView.adapter = DashboardAdapter(items) { clickedItem ->
            Toast.makeText(this, "ورود به: ${clickedItem.title}", Toast.LENGTH_SHORT).show()
        }
    }
}