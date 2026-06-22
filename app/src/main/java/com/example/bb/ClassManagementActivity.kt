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
import com.google.android.material.textfield.TextInputEditText
import java.util.UUID

class ClassManagementActivity : AppCompatActivity() {

    private lateinit var rvLevels: RecyclerView
    private val classesAdapterList = ArrayList<ClassModel>()
    private lateinit var classAdapter: RecyclerView.Adapter<ClassViewHolder>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_class_management)

        // دکمه بازگشت هدر
        findViewById<ImageView>(R.id.btnClassMgmtBack).setOnClickListener { finish() }

        rvLevels = findViewById(R.id.rvLevels)
        rvLevels.layoutManager = LinearLayoutManager(this)

        setupAdapter()
        refreshClassesList()

        // دکمه شناور پلاس نارنجی برای اد کردن کلاس جدید
        findViewById<FloatingActionButton>(R.id.fabAddClass).setOnClickListener {
            showAddClassDialog()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshClassesList()
    }

    private fun refreshClassesList() {
        classesAdapterList.clear()
        classesAdapterList.addAll(AppDatabase.getAllClasses())
        classAdapter.notifyDataSetChanged()
    }

    private fun setupAdapter() {
        classAdapter = object : RecyclerView.Adapter<ClassViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClassViewHolder {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_class_manage, parent, false)
                return ClassViewHolder(view)
            }

            override fun onBindViewHolder(holder: ClassViewHolder, position: Int) {
                val item = classesAdapterList[position]
                holder.tvName.text = item.className
                holder.tvTime.text = item.classTime

                // دکمه حذف کلاس
                holder.btnDelete.setOnClickListener {
                    AppDatabase.deleteClass(item.id, this@ClassManagementActivity)
                    refreshClassesList()
                    Toast.makeText(this@ClassManagementActivity, "کلاس با موفقیت حذف شد", Toast.LENGTH_SHORT).show()
                }

                // کلیک روی نام کلاس برای ورود به صفحه جزئیات
                holder.layoutText.setOnClickListener {
                    val intent = Intent(this@ClassManagementActivity, ClassDetailsActivity::class.java)
                    intent.putExtra("CLASS_ID", item.id)
                    intent.putExtra("CLASS_NAME", item.className)
                    startActivity(intent)
                }
            }

            override fun getItemCount() = classesAdapterList.size
        }
        rvLevels.adapter = classAdapter
    }

    private fun showAddClassDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_class, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        val etName = dialogView.findViewById<TextInputEditText>(R.id.etDialogClassName)
        val etTime = dialogView.findViewById<TextInputEditText>(R.id.etDialogClassTime)
        val btnAdd = dialogView.findViewById<Button>(R.id.btnDialogAdd)

        btnAdd.setOnClickListener {
            val name = etName.text.toString().trim()
            val time = etTime.text.toString().trim()

            if (name.isEmpty() || time.isEmpty()) {
                Toast.makeText(this, "لطفاً تمام فیلدها را پر کنید", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // ساخت شیء بر اساس مدل واقعی ClassModel شما
            val newClass = ClassModel(
                id = UUID.randomUUID().toString(),
                className = name,
                classTime = time
            )

            AppDatabase.addClass(newClass, this@ClassManagementActivity)
            refreshClassesList()
            dialog.dismiss()
            Toast.makeText(this, "کلاس جدید با موفقیت اضافه شد", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    class ClassViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.txtManageClassName)
        val tvTime: TextView = view.findViewById(R.id.txtManageClassTime)
        val btnDelete: Button = view.findViewById(R.id.btnDeleteClass)
        val layoutText: LinearLayout = view.findViewById(R.id.layoutClassText)
    }
}