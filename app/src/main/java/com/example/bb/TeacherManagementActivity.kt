package com.example.bb

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.Collator
import java.util.Locale

class TeacherManagementActivity : BaseActivity() {

    private lateinit var adapter: TeacherAdapter
    private lateinit var txtCount: TextView
    private lateinit var emptyState: View
    private lateinit var statusFilter: MaterialAutoCompleteTextView
    private val teachers = arrayListOf<TeacherModel>()
    private val classes = arrayListOf<ClassModel>()
    private var search = ""
    private var selectedStatus = "همه اساتید"
    private val collator = Collator.getInstance(Locale("fa", "IR")).apply { strength = Collator.PRIMARY }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_teacher_management)

        findViewById<ImageView>(R.id.btnTeacherMgmtBack).setOnClickListener { finish() }
        findViewById<FloatingActionButton>(R.id.fabAddTeacher).setOnClickListener {
            startActivity(Intent(this, AddEditTeacherActivity::class.java))
        }

        txtCount = findViewById(R.id.txtTeacherCount)
        emptyState = findViewById(R.id.teacherEmptyState)
        statusFilter = findViewById(R.id.spinnerTeacherStatusFilter)
        val options = listOf("همه اساتید", "اساتید فعال", "بایگانی‌شده‌ها")
        statusFilter.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, options))
        statusFilter.setText(options.first(), false)
        statusFilter.setOnClickListener { statusFilter.showDropDown() }
        statusFilter.setOnItemClickListener { _, _, position, _ ->
            selectedStatus = options[position]
            applyFilters()
        }

        findViewById<EditText>(R.id.etSearchTeacher).addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { search = s.toString().trim(); applyFilters() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        })

        val recycler = findViewById<RecyclerView>(R.id.rvTeachers)
        recycler.layoutManager = LinearLayoutManager(this)
        adapter = TeacherAdapter(emptyList(),
            onRowClick = { teacher ->
                if (!teacher.isActive) Toast.makeText(this, "ابتدا استاد را فعال کنید", Toast.LENGTH_SHORT).show()
                else startActivity(Intent(this, AssignClassActivity::class.java)
                    .putExtra(AssignClassActivity.EXTRA_TEACHER_ID, teacher.id))
            },
            onDetailsClick = ::showTeacherDetails
        )
        recycler.adapter = adapter
    }

    override fun onResume() { super.onResume(); loadData() }

    private fun loadData() {
        RetrofitClient.instance.getClasses().enqueue(object : Callback<List<ClassModel>> {
            override fun onResponse(call: Call<List<ClassModel>>, response: Response<List<ClassModel>>) {
                classes.clear()
                if (response.isSuccessful) {
                    classes.addAll(response.body().orEmpty())
                    AppDatabase.replaceClasses(classes)
                } else {
                    classes.addAll(AppDatabase.getAllClasses(false))
                }
                loadTeachers()
            }
            override fun onFailure(call: Call<List<ClassModel>>, t: Throwable) {
                classes.clear(); classes.addAll(AppDatabase.getAllClasses(false)); loadTeachers()
            }
        })
    }

    private fun loadTeachers() {
        RetrofitClient.instance.getTeachers().enqueue(object : Callback<List<TeacherModel>> {
            override fun onResponse(call: Call<List<TeacherModel>>, response: Response<List<TeacherModel>>) {
                if (response.isSuccessful) {
                    teachers.clear(); teachers.addAll(response.body().orEmpty()); AppDatabase.replaceTeachersLocally(teachers); applyFilters()
                } else showLocal()
            }
            override fun onFailure(call: Call<List<TeacherModel>>, t: Throwable) { showLocal() }
        })
    }

    private fun showLocal() {
        teachers.clear(); teachers.addAll(AppDatabase.getAllTeachers()); applyFilters()
        Toast.makeText(this, "فهرست ذخیره‌شده اساتید نمایش داده شد", Toast.LENGTH_SHORT).show()
    }

    private fun applyFilters() {
        val q = normalize(search)
        val result = teachers.filter { teacher ->
            val matchesText = q.isBlank() || normalize("${teacher.firstName} ${teacher.lastName} ${teacher.phone}").contains(q)
            val matchesStatus = when (selectedStatus) {
                "اساتید فعال" -> teacher.isActive
                "بایگانی‌شده‌ها" -> !teacher.isActive
                else -> true
            }
            matchesText && matchesStatus
        }.sortedWith { a, b ->
            val last = collator.compare(normalize(a.lastName), normalize(b.lastName))
            if (last != 0) last else collator.compare(normalize(a.firstName), normalize(b.firstName))
        }
        adapter.updateData(result)
        txtCount.text = "${result.size} استاد"
        emptyState.visibility = if (result.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showTeacherDetails(initial: TeacherModel) {
        var teacher = initial
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_teacher_details, null)
        dialog.setContentView(view)

        val avatar = view.findViewById<ImageView>(R.id.dialogTeacherAvatar)
        val name = view.findViewById<TextView>(R.id.dialogTeacherName)
        val phone = view.findViewById<TextView>(R.id.dialogTeacherUsername)
        val assigned = view.findViewById<TextView>(R.id.dialogTeacherClass)
        val status = view.findViewById<TextView>(R.id.dialogTeacherStatus)
        val hint = view.findViewById<TextView>(R.id.dialogTeacherArchiveHint)
        val archive = view.findViewById<MaterialButton>(R.id.btnDialogTeacherArchive)

        fun render() {
            // 🌟 جایگزینی پیش‌فرض
            val avatarName = teacher.avatarName?.takeIf { it.isNotBlank() } ?: "avatar_no_profile"
            val res = resources.getIdentifier(avatarName, "drawable", packageName)
            avatar.setImageResource(if (res != 0) res else resources.getIdentifier("avatar_no_profile", "drawable", packageName))
            name.text = teacher.name
            phone.text = teacher.phone
            val teacherClasses = classes.filter {
                it.status == ClassStatus.ACTIVE &&
                    (it.teacherId == teacher.id ||
                        (it.teacherId.isNullOrBlank() && normalizePhone(it.teacherPhone) == normalizePhone(teacher.phone)))
            }
            assigned.text = teacherClasses.takeIf { it.isNotEmpty() }
                ?.joinToString("، ") { it.className } ?: "بدون کلاس فعال"
            status.text = if (teacher.isActive) "فعال" else "بایگانی‌شده"
            status.setBackgroundResource(if (teacher.isActive) R.drawable.bg_unified_status_active else R.drawable.bg_unified_status_inactive)
            archive.text = if (teacher.isActive) "بایگانی کردن" else "فعال‌سازی مجدد"
            val blocked = teacher.isActive && teacherClasses.isNotEmpty()
            archive.isEnabled = !blocked
            archive.alpha = if (blocked) .5f else 1f
            hint.visibility = if (blocked) View.VISIBLE else View.GONE
        }
        render()

        archive.setOnClickListener {
            archive.isEnabled = false
            RetrofitClient.instance.toggleTeacherActive(
                ToggleTeacherActiveRequest(teacher.id, !teacher.isActive)
            ).enqueue(object : Callback<ApiResponse> {
                override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                    archive.isEnabled = true
                    if (response.isSuccessful && response.body()?.status == "success") {
                        teacher = teacher.copy(isActive = !teacher.isActive)
                        render(); dialog.dismiss(); loadTeachers()
                    } else Toast.makeText(this@TeacherManagementActivity, response.body()?.message?.takeIf { it.isNotBlank() } ?: ApiErrorParser.userMessage(response, "تغییر وضعیت استاد انجام نشد"), Toast.LENGTH_LONG).show()
                }
                override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                    archive.isEnabled = true
                    Toast.makeText(this@TeacherManagementActivity, ApiErrorParser.networkMessage(t, "تغییر وضعیت استاد"), Toast.LENGTH_LONG).show()
                }
            })
        }
        view.findViewById<MaterialButton>(R.id.btnDialogTeacherEdit).setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, AddEditTeacherActivity::class.java)
                .putExtra(AddEditTeacherActivity.EXTRA_TEACHER_USERNAME, teacher.username))
        }

        view.findViewById<MaterialButton>(R.id.btnDialogTrashTeacher).setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("حذف قطعی استاد")
                .setMessage("آیا از حذف قطعی این استاد مطمئن هستید؟ تمام سوابق تخصیص او به کلاس‌ها پاک خواهد شد.")
                .setNegativeButton("انصراف", null)
                .setPositiveButton("حذف قطعی") { _, _ ->
                    RetrofitClient.instance.hardDeleteTeacher(HardDeleteTeacherRequest(teacher.id))
                        .enqueue(object : Callback<ApiResponse> {
                            override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                                val body = response.body()
                                val message = if (response.isSuccessful && body?.status == "success") {
                                    body.message.ifBlank { "استاد با موفقیت حذف شد" }
                                } else {
                                    body?.message?.takeIf { it.isNotBlank() }
                                        ?: ApiErrorParser.userMessage(response, "حذف استاد انجام نشد")
                                }
                                Toast.makeText(this@TeacherManagementActivity, message, Toast.LENGTH_LONG).show()
                                if (response.isSuccessful && body?.status == "success") { dialog.dismiss(); loadTeachers() }
                            }
                            override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                                Toast.makeText(this@TeacherManagementActivity, ApiErrorParser.networkMessage(t, "حذف استاد"), Toast.LENGTH_LONG).show()
                            }
                        })
                }.show()
        }

        val resetPassword = view.findViewById<MaterialButton>(R.id.btnDialogTeacherResetPassword)
        resetPassword.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("ساخت رمز موقت")
                .setMessage("رمز فعلی قابل مشاهده نیست. یک رمز موقت جدید ساخته شود؟")
                .setNegativeButton("انصراف", null)
                .setPositiveButton("ساخت رمز") { _, _ ->
                    resetPassword.isEnabled = false
                    RetrofitClient.instance.adminResetPassword(
                        AdminResetPasswordRequest("TEACHER", teacher.id)
                    ).enqueue(object : Callback<AdminResetPasswordResponse> {
                        override fun onResponse(call: Call<AdminResetPasswordResponse>, response: Response<AdminResetPasswordResponse>) {
                            resetPassword.isEnabled = true
                            val body = response.body()
                            if (response.isSuccessful && body?.status == "success" && !body.temporaryPassword.isNullOrBlank()) {
                                MaterialAlertDialogBuilder(this@TeacherManagementActivity)
                                    .setTitle("رمز موقت ${teacher.name}")
                                    .setMessage("${body.temporaryPassword}\n\nاین رمز فقط همین یک‌بار نمایش داده می‌شود. کاربر بعد از ورود مجبور به تغییر آن است.")
                                    .setPositiveButton("متوجه شدم", null)
                                    .show()
                            } else {
                                Toast.makeText(this@TeacherManagementActivity, body?.message?.takeIf { it.isNotBlank() } ?: ApiErrorParser.userMessage(response, "ساخت رمز موقت انجام نشد"), Toast.LENGTH_LONG).show()
                            }
                        }
                        override fun onFailure(call: Call<AdminResetPasswordResponse>, t: Throwable) {
                            resetPassword.isEnabled = true
                            Toast.makeText(this@TeacherManagementActivity, ApiErrorParser.networkMessage(t, "ساخت رمز موقت"), Toast.LENGTH_LONG).show()
                        }
                    })
                }
                .show()
        }
        dialog.show()
    }

    private fun normalize(value: String): String = value.lowercase(Locale("fa"))
        .replace('ي','ی').replace('ى','ی').replace('ك','ک').replace('\u200c',' ').trim()
    private fun normalizePhone(value: String?): String = value.orEmpty().replace(" ", "").removePrefix("0")
}
