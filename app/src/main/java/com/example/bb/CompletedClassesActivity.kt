package com.example.bb

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class CompletedClassesActivity : BaseActivity() {

    private val completedClasses = arrayListOf<ClassModel>()
    private lateinit var rvClasses: RecyclerView
    private lateinit var progressLoading: View
    private lateinit var tvEmpty: TextView
    private lateinit var adapter: CompletedClassAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_completed_classes)

        findViewById<ImageView>(R.id.btnCompletedMgmtBack).setOnClickListener { finish() }

        rvClasses = findViewById(R.id.rvCompletedClasses)
        progressLoading = findViewById(R.id.progressCompletedClasses)
        tvEmpty = findViewById(R.id.tvCompletedClassesEmpty)

        rvClasses.layoutManager = LinearLayoutManager(this)
        adapter = CompletedClassAdapter()
        rvClasses.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        fetchCompletedClasses()
    }

    private fun fetchCompletedClasses() {
        setLoading(true)
        RetrofitClient.instance.getCompletedClasses().enqueue(object : Callback<List<ClassModel>> {
            override fun onResponse(
                call: Call<List<ClassModel>>,
                response: Response<List<ClassModel>>
            ) {
                setLoading(false)
                if (response.isSuccessful) {
                    val serverClasses = response.body().orEmpty()
                    renderClasses(serverClasses)
                } else {
                    AppToast.makeText(
                        this@CompletedClassesActivity,
                        ApiErrorParser.userMessage(response, "دریافت کلاس‌های پایان‌یافته کامل نشد"),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            override fun onFailure(call: Call<List<ClassModel>>, t: Throwable) {
                setLoading(false)
                AppToast.makeText(
                    this@CompletedClassesActivity,
                    ApiErrorParser.networkMessage(t, "دریافت کلاس‌های پایان‌یافته"),
                    Toast.LENGTH_LONG
                ).show()
            }
        })
    }

    private fun renderClasses(classes: List<ClassModel>) {
        completedClasses.clear()
        completedClasses.addAll(classes.sortedWith(compareBy<ClassModel> { it.className }.thenBy { it.startTime }))
        adapter.notifyDataSetChanged()
        tvEmpty.visibility = if (completedClasses.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openClassMembers(model: ClassModel) {
        startActivity(
            Intent(this, ClassDetailsActivity::class.java)
                .putExtra(ClassDetailsActivity.EXTRA_CLASS_ID, model.id)
                .putExtra(ClassDetailsActivity.EXTRA_CLASS_NAME, model.className)
                .putExtra(ClassDetailsActivity.EXTRA_IS_EDITABLE, false) // 🌟 جادوی قفل کردن صفحه
        )
    }

    private fun confirmTrashClass(model: ClassModel) {
        MaterialAlertDialogBuilder(this)
            .setTitle("حذف قطعی کلاس")
            .setMessage("آیا از حذف قطعی کلاس «${model.className}» مطمئن هستید؟ با این کار تمام سوابق، حضور و غیاب‌ها و کارنامه‌های این کلاس از دیتابیس پاک شده و دیگر قابل بازگشت نخواهد بود.")
            .setPositiveButton("حذف قطعی") { _, _ ->
                setLoading(true)
                RetrofitClient.instance.hardDeleteClass(HardDeleteClassRequest(model.id))
                    .enqueue(object : Callback<ApiResponse> {
                        override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                            setLoading(false)
                            val body = response.body()
                            AppToast.makeText(
                                this@CompletedClassesActivity,
                                if (response.isSuccessful && body?.status == "success") {
                                    body.message.ifBlank { "کلاس با موفقیت حذف شد" }
                                } else {
                                    body?.message?.takeIf { it.isNotBlank() }
                                        ?: ApiErrorParser.userMessage(response, "حذف کلاس کامل نشد")
                                },
                                Toast.LENGTH_LONG
                            ).show()
                            if (response.isSuccessful && body?.status == "success") fetchCompletedClasses()
                        }
                        override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                            setLoading(false)
                            AppToast.makeText(
                                this@CompletedClassesActivity,
                                ApiErrorParser.networkMessage(t, "حذف کلاس"),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    })
            }.setNegativeButton("انصراف", null).show()
    }

    private fun setLoading(loading: Boolean) {
        progressLoading.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private inner class CompletedClassAdapter : RecyclerView.Adapter<CompletedClassViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CompletedClassViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_class_manage, parent, false)
            return CompletedClassViewHolder(view)
        }

        override fun onBindViewHolder(holder: CompletedClassViewHolder, position: Int) {
            val model = completedClasses[position]
            holder.tvName.text = model.className
            holder.tvSchedule.text = model.classTime
            holder.tvTeacher.text = when {
                model.teacherId.isNullOrBlank() -> "استاد: تعیین نشده"
                model.teacherName.isNotBlank() -> "استاد: ${model.teacherName}"
                else -> "استاد: ${model.teacherPhone.orEmpty().ifBlank { "تعیین نشده" }}"
            }

            // 🌟 این دو خط جادویی، دکمه‌های ویرایش و پایان ترم رو محو میکنن و فضا رو آزاد می‌کنن
            holder.btnEdit.visibility = View.GONE
            holder.btnComplete.visibility = View.GONE

            holder.btnMembers.setOnClickListener { openClassMembers(model) }
            holder.btnTrash.setOnClickListener { confirmTrashClass(model) }
        }

        override fun getItemCount(): Int = completedClasses.size
    }

    private class CompletedClassViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.txtManageClassName)
        val tvSchedule: TextView = view.findViewById(R.id.txtManageClassTime)
        val tvTeacher: TextView = view.findViewById(R.id.txtManageClassTeacher)
        val btnMembers: MaterialButton = view.findViewById(R.id.btnManageMembers)
        val btnEdit: MaterialButton = view.findViewById(R.id.btnEditClass)
        val btnComplete: MaterialButton = view.findViewById(R.id.btnCompleteClass)
        val btnTrash: MaterialButton = view.findViewById(R.id.btnTrashClass)
    }
}
