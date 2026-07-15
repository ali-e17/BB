package com.example.bb

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class ClassManagementActivity : AppCompatActivity() {

    private lateinit var rvLevels: RecyclerView
    private val classesList = ArrayList<ClassModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_class_management)

        // 🔙 دکمه بازگشت به صفحه قبل
        findViewById<ImageView>(R.id.btnClassMgmtBack).setOnClickListener { finish() }

        rvLevels = findViewById(R.id.rvLevels)
        rvLevels.layoutManager = LinearLayoutManager(this)

        setupAdapter()

        // ➕ کلیک روی دکمه افزودن کلاس (انتقال به اکتیویتی جدید)
        findViewById<FloatingActionButton>(R.id.fabAddClass).setOnClickListener {
            val intent = Intent(this, AddEditClassActivity::class.java)
            startActivity(intent)
        }
    }

    // این متد باعث میشه هربار که از صفحه ویرایش برمی‌گردیم، لیست کلاس‌ها اتوماتیک آپدیت بشه
    override fun onResume() {
        super.onResume()
        refreshClassesList()
    }

    private fun setupAdapter() {
        rvLevels.adapter = object : RecyclerView.Adapter<ClassViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClassViewHolder {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_class_manage, parent, false)
                return ClassViewHolder(view)
            }

            override fun onBindViewHolder(holder: ClassViewHolder, position: Int) {
                val classModel = classesList[position]

                holder.tvName.text = classModel.className
                holder.tvTime.text = "${classModel.daysOfWeek} | ساعت: ${classModel.classTime}"

                // ✏️ رفتن به صفحه ویرایش با کلیک روی مشخصات کلاس
                holder.layoutText.setOnClickListener {
                    val intent = Intent(this@ClassManagementActivity, AddEditClassActivity::class.java)
                    intent.putExtra("CLASS_ID", classModel.id)
                    startActivity(intent)
                }

                // 🗑️ حذف کلاس با کلیک روی دکمه ضربدر
                holder.btnDelete.setOnClickListener {
                    AlertDialog.Builder(this@ClassManagementActivity)
                        .setTitle("حذف کلاس")
                        .setMessage("آیا از حذف کلاس «${classModel.className}» مطمئن هستید؟ این عملیات دانش‌آموزان را از کلاس خارج می‌کند.")
                        .setPositiveButton("بله، حذف شود") { _, _ ->
                            // TODO: اگر از Retrofit استفاده می‌کنید، متد حذف آنلاین را اینجا فراخوانی کنید
                            // RetrofitClient.instance.deleteClass(DeleteClassRequest(classModel.id)).enqueue(...)

                            AppDatabase.deleteClass(classModel.id) // حذف از دیتابیس لوکال
                            Toast.makeText(this@ClassManagementActivity, "کلاس با موفقیت حذف شد", Toast.LENGTH_SHORT).show()
                            refreshClassesList()
                        }
                        .setNegativeButton("انصراف", null)
                        .show()
                }
            }

            override fun getItemCount() = classesList.size
        }
    }

    private fun refreshClassesList() {
        classesList.clear()

        // دریافت لیست کلاس‌ها از دیتابیس لوکال
        val localClasses = AppDatabase.getAllClasses(false)
        classesList.addAll(localClasses)

        rvLevels.adapter?.notifyDataSetChanged()

        // 🌐 TODO: در صورت نیاز به همگام‌سازی با سرور، کد RetrofitClient.instance.getClasses() را اینجا قرار دهید
    }

    class ClassViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.txtManageClassName)
        val tvTime: TextView = view.findViewById(R.id.txtManageClassTime)
        val btnDelete: Button = view.findViewById(R.id.btnDeleteClass)
        val layoutText: LinearLayout = view.findViewById(R.id.layoutClassText)
    }
}