package com.example.bb

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

enum class UserRole {
    STUDENT, TEACHER, ADMIN
}

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var dashboardAdapter: DashboardAdapter

    // فرض می‌کنیم نقش کاربر در هنگام لاگین مشخص شده است (اینجا دانش‌آموز را به صورت پیش‌فرض دادیم)
    private var currentUserRole: UserRole = UserRole.STUDENT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupRecyclerView()
        loadDashboardItems()
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.recyclerViewDashboard)
        // ایجاد یک گرید ۲ ستونه
        recyclerView.layoutManager = GridLayoutManager(this, 2)
    }

    private fun loadDashboardItems() {
        val items = mutableListOf<DashboardItem>()

        when (currentUserRole) {
            UserRole.STUDENT -> {
                items.add(DashboardItem("صندوق پیام", android.R.drawable.ic_dialog_email))
                items.add(DashboardItem("کارنامه", android.R.drawable.ic_menu_info_details))
                items.add(DashboardItem("دیکشنری", android.R.drawable.ic_menu_sort_alphabetically))
                items.add(DashboardItem("برنامه کلاسی", android.R.drawable.ic_menu_today))
            }
            UserRole.TEACHER -> {
                items.add(DashboardItem("صندوق پیام", android.R.drawable.ic_dialog_email))
                items.add(DashboardItem("مدیریت کلاس‌های من", android.R.drawable.ic_menu_manage))
                items.add(DashboardItem("ثبت نمرات", android.R.drawable.ic_menu_edit))
            }
            UserRole.ADMIN -> {
                items.add(DashboardItem("صندوق پیام (کل)", android.R.drawable.ic_dialog_email))
                items.add(DashboardItem("مدیریت دانش‌آموزان", android.R.drawable.ic_menu_myplaces))
                items.add(DashboardItem("مدیریت اساتید", android.R.drawable.ic_menu_recent_history))
                items.add(DashboardItem("مدیریت کلاس‌ها", android.R.drawable.ic_menu_manage))
                items.add(DashboardItem("ثبت نمرات نهایی", android.R.drawable.ic_menu_edit))
            }
        }

        // وصل کردن آداپتور به لیست
        dashboardAdapter = DashboardAdapter(items) { clickedItem ->
            // عملیاتی که هنگام کلیک روی هر کارت باید انجام شود
            Toast.makeText(this, "کلیک روی: ${clickedItem.title}", Toast.LENGTH_SHORT).show()
        }

        recyclerView.adapter = dashboardAdapter
    }
}