package com.example.bb

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ClassManagementActivity : AppCompatActivity() {

    private val activeClasses = arrayListOf<ClassModel>()
    private lateinit var rvClasses: RecyclerView
    private lateinit var progressLoading: View
    private lateinit var tvEmpty: TextView
    private lateinit var adapter: ClassAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_class_management)

        findViewById<ImageView>(R.id.btnClassMgmtBack).setOnClickListener { finish() }
        findViewById<FloatingActionButton>(R.id.fabAddClass).setOnClickListener {
            startActivity(Intent(this, AddEditClassActivity::class.java))
        }

        rvClasses = findViewById(R.id.rvLevels)
        progressLoading = findViewById(R.id.progressClasses)
        tvEmpty = findViewById(R.id.tvClassesEmpty)

        rvClasses.layoutManager = LinearLayoutManager(this)
        adapter = ClassAdapter()
        rvClasses.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        fetchClasses()
    }

    private fun fetchClasses() {
        setLoading(true)
        RetrofitClient.instance.getClasses().enqueue(object : Callback<List<ClassModel>> {
            override fun onResponse(
                call: Call<List<ClassModel>>,
                response: Response<List<ClassModel>>
            ) {
                setLoading(false)
                if (!response.isSuccessful) {
                    showLocalClasses("سرور لیست کلاس‌ها را برنگرداند")
                    return
                }

                val serverClasses = response.body().orEmpty()
                AppDatabase.replaceClasses(serverClasses)
                renderClasses(serverClasses)
            }

            override fun onFailure(call: Call<List<ClassModel>>, t: Throwable) {
                setLoading(false)
                showLocalClasses("اتصال به سرور برقرار نشد؛ اطلاعات محلی نمایش داده شد")
            }
        })
    }

    private fun showLocalClasses(message: String) {
        renderClasses(AppDatabase.getAllClasses(false))
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun renderClasses(classes: List<ClassModel>) {
        activeClasses.clear()
        activeClasses.addAll(
            classes
                .filter { it.status == ClassStatus.ACTIVE }
                .sortedWith(compareBy<ClassModel> { it.className }.thenBy { it.startTime })
        )
        adapter.notifyDataSetChanged()
        tvEmpty.visibility = if (activeClasses.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openEditClass(model: ClassModel) {
        startActivity(
            Intent(this, AddEditClassActivity::class.java)
                .putExtra(AddEditClassActivity.EXTRA_CLASS_ID, model.id)
        )
    }

    private fun openClassMembers(model: ClassModel) {
        startActivity(
            Intent(this, ClassDetailsActivity::class.java)
                .putExtra(ClassDetailsActivity.EXTRA_CLASS_ID, model.id)
                .putExtra(ClassDetailsActivity.EXTRA_CLASS_NAME, model.className)
        )
    }

    private fun confirmCompleteClass(model: ClassModel) {
        AlertDialog.Builder(this)
            .setTitle("پایان ترم")
            .setMessage(
                "ترم «${model.className}» پایان یابد؟\n\n" +
                    "سوابق دانش‌آموزان، حضور و غیاب و کارنامه‌ها حذف نمی‌شوند؛ " +
                    "کلاس فقط از فهرست کلاس‌های فعال خارج می‌شود."
            )
            .setPositiveButton("پایان ترم") { _, _ -> completeClass(model) }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun completeClass(model: ClassModel) {
        setLoading(true)
        RetrofitClient.instance.completeClass(CompleteClassRequest(model.id))
            .enqueue(object : Callback<ApiResponse> {
                override fun onResponse(
                    call: Call<ApiResponse>,
                    response: Response<ApiResponse>
                ) {
                    setLoading(false)
                    val result = response.body()
                    if (response.isSuccessful && result?.status == "success") {
                        AppDatabase.completeClass(model.id)
                        Toast.makeText(
                            this@ClassManagementActivity,
                            result.message.ifBlank { "ترم کلاس پایان یافت" },
                            Toast.LENGTH_SHORT
                        ).show()
                        fetchClasses()
                    } else {
                        Toast.makeText(
                            this@ClassManagementActivity,
                            result?.message?.takeIf { it.isNotBlank() } ?: "پایان ترم در سرور ثبت نشد",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                    setLoading(false)
                    Toast.makeText(
                        this@ClassManagementActivity,
                        "اتصال به complete_class.php برقرار نشد",
                        Toast.LENGTH_LONG
                    ).show()
                }
            })
    }

    private fun setLoading(loading: Boolean) {
        progressLoading.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private inner class ClassAdapter : RecyclerView.Adapter<ClassViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClassViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_class_manage, parent, false)
            return ClassViewHolder(view)
        }

        override fun onBindViewHolder(holder: ClassViewHolder, position: Int) {
            val model = activeClasses[position]
            holder.tvName.text = model.className
            holder.tvSchedule.text = model.classTime
            holder.tvTeacher.text = if (model.teacherPhone.isNullOrBlank()) {
                "استاد: تعیین نشده"
            } else {
                val teacherName = AppDatabase.getTeacherByUsername(model.teacherPhone.orEmpty())?.name
                "استاد: ${teacherName ?: model.teacherPhone}"
            }

            holder.btnMembers.setOnClickListener { openClassMembers(model) }
            holder.btnEdit.setOnClickListener { openEditClass(model) }
            holder.btnComplete.setOnClickListener { confirmCompleteClass(model) }
        }

        override fun getItemCount(): Int = activeClasses.size
    }

    private class ClassViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.txtManageClassName)
        val tvSchedule: TextView = view.findViewById(R.id.txtManageClassTime)
        val tvTeacher: TextView = view.findViewById(R.id.txtManageClassTeacher)
        val btnMembers: MaterialButton = view.findViewById(R.id.btnManageMembers)
        val btnEdit: MaterialButton = view.findViewById(R.id.btnEditClass)
        val btnComplete: MaterialButton = view.findViewById(R.id.btnCompleteClass)
    }
}
