package com.example.bb

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ProfileActivity : AppCompatActivity() {

    private lateinit var tvUserName: TextView
    private lateinit var tvUserRole: TextView
    private lateinit var tvStudentClassStatus: TextView
    private lateinit var layoutStudentOptions: LinearLayout
    private lateinit var layoutTeacherOptions: LinearLayout
    private lateinit var userRole: String

    private lateinit var ivAvatar: ImageView
    private lateinit var btnChangeAvatar: TextView
    private lateinit var btnLogout: LinearLayout
    private lateinit var btnChangePassword: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        findViewById<ImageView>(R.id.btnProfileBack).setOnClickListener { finish() }

        val sharedPreferences = getSharedPreferences("LocalAppPrefs", Context.MODE_PRIVATE)
        userRole = sharedPreferences.getString("CURRENT_USER_ROLE", "STUDENT") ?: "STUDENT"

        tvUserName = findViewById(R.id.tvUserName)
        tvUserRole = findViewById(R.id.tvUserRole)
        tvStudentClassStatus = findViewById(R.id.tvStudentClassStatus)
        layoutStudentOptions = findViewById(R.id.layoutStudentOptions)
        layoutTeacherOptions = findViewById(R.id.layoutTeacherOptions)

        ivAvatar = findViewById(R.id.ivAvatar)
        btnChangeAvatar = findViewById(R.id.btnChangeAvatar)
        btnLogout = findViewById(R.id.btnLogout)
        btnChangePassword = findViewById(R.id.btnChangePassword)

        btnChangePassword.setOnClickListener {
            val intent = Intent(this, UpdateProfileActivity::class.java)
            intent.putExtra("USER_ROLE", userRole)
            startActivity(intent)
        }

        btnLogout.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("خروج از حساب")
                .setMessage("آیا می‌خواهید از حساب کاربری خود خارج شوید؟")
                .setPositiveButton("بله") { _, _ ->
                    sharedPreferences.edit().apply {
                        putBoolean("IS_LOGGED_IN", false)
                        putString("CURRENT_USER_ROLE", "STUDENT")
                        putString("CURRENT_USERNAME", "")
                        putString("CURRENT_DISPLAY_NAME", "")
                        putString("CURRENT_USER_ID", "")
                        putString("API_TOKEN", "")
                        putString("API_TOKEN_EXPIRES_AT", "")
                        apply()
                    }
                    val intent = Intent(this, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
                .setNegativeButton("خیر", null)
                .show()
        }

        btnChangeAvatar.setOnClickListener {
            showAvatarSelectionDialog()
        }

        val displayName = sharedPreferences.getString("CURRENT_DISPLAY_NAME", "")
        tvUserName.text = if (!displayName.isNullOrEmpty()) displayName else "کاربر عزیز"

        // تنظیم اولیه نقش‌ها
        setupProfileData()
    }

    override fun onResume() {
        super.onResume()
        setupProfileData()
    }

    private fun setupProfileData() {
        val sharedPreferences = getSharedPreferences("LocalAppPrefs", Context.MODE_PRIVATE)
        val currentUserId = sharedPreferences.getString("CURRENT_USER_ID", "") ?: ""

        when (userRole.lowercase()) {
            "student" -> {
                tvUserRole.text = "دانش‌آموز آموزشگاه"
                layoutStudentOptions.visibility = View.VISIBLE
                layoutTeacherOptions.visibility = View.GONE
                tvStudentClassStatus.visibility = View.VISIBLE

                tvStudentClassStatus.text = "در حال بررسی وضعیت کلاس..."

                // 🌐 دریافت زنده اطلاعات دانش‌آموز از سرور
                RetrofitClient.instance.getStudents().enqueue(object : Callback<List<StudentModel>> {
                    override fun onResponse(call: Call<List<StudentModel>>, response: Response<List<StudentModel>>) {
                        if (response.isSuccessful) {
                            val allStudents = response.body().orEmpty()
                            val myStudent = allStudents.find { it.id == currentUserId }

                            if (myStudent != null) {
                                // 🌟 هماهنگ‌سازی کامل آواتار با فرمول رندوم ثابت پنل مدیریت
                                val randomNum = (Math.abs(myStudent.id.hashCode()) % 9) + 1
                                val fallback = "avatar_student_$randomNum"
                                val avatar = myStudent.avatarName?.takeIf { it.isNotBlank() } ?: fallback

                                val resId = resources.getIdentifier(avatar, "drawable", packageName)
                                if (resId != 0) {
                                    ivAvatar.setImageResource(resId)
                                } else {
                                    ivAvatar.setImageResource(R.drawable.avatar_student_1)
                                }

                                // لود وضعیت کلاس
                                if (!myStudent.classId.isNullOrBlank()) {
                                    RetrofitClient.instance.getClasses().enqueue(object : Callback<List<ClassModel>> {
                                        override fun onResponse(call: Call<List<ClassModel>>, response2: Response<List<ClassModel>>) {
                                            val classes = response2.body().orEmpty()
                                            val myClass = classes.find { it.id == myStudent.classId }
                                            tvStudentClassStatus.text = "کلاس فعلی شما: ${myClass?.className ?: "نامشخص"}"
                                        }
                                        override fun onFailure(call: Call<List<ClassModel>>, t: Throwable) {
                                            tvStudentClassStatus.text = "خطا در دریافت نام کلاس"
                                        }
                                    })
                                } else {
                                    tvStudentClassStatus.text = "شما هنوز در هیچ کلاسی ثبت‌نام نشده‌اید."
                                }
                            }
                        }
                    }
                    override fun onFailure(call: Call<List<StudentModel>>, t: Throwable) {
                        tvStudentClassStatus.text = "عدم اتصال به سرور"
                    }
                })
            }
            "teacher" -> {
                tvUserRole.text = "مدرس رسمی بیان برتر"
                layoutStudentOptions.visibility = View.GONE
                layoutTeacherOptions.visibility = View.VISIBLE
                tvStudentClassStatus.visibility = View.GONE
                ivAvatar.setImageResource(R.drawable.avatar_teacher_1)

                // 🌟 اضافه شدن عملکرد دکمه سوابق کلاس
                val btnViewTeacherClasses = findViewById<LinearLayout>(R.id.btnViewTeacherClasses)
                btnViewTeacherClasses.setOnClickListener {
                    startActivity(Intent(this@ProfileActivity, TeacherHistoryActivity::class.java))
                }
            }
            "admin" -> {
                tvUserRole.text = "دسترسی کامل (مدیر کل)"
                layoutStudentOptions.visibility = View.GONE
                layoutTeacherOptions.visibility = View.GONE
                tvStudentClassStatus.visibility = View.GONE
                ivAvatar.setImageResource(R.drawable.avatar_admin_1)
            }
        }
    }

    private fun showAvatarSelectionDialog() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_avatar_selector, null)
        val rvAvatars = view.findViewById<RecyclerView>(R.id.rvAvatarGrid)

        val avatars = listOf(
            resources.getIdentifier("avatar_student_1", "drawable", packageName),
            resources.getIdentifier("avatar_student_2", "drawable", packageName),
            resources.getIdentifier("avatar_student_3", "drawable", packageName),
            resources.getIdentifier("avatar_student_4", "drawable", packageName),
            resources.getIdentifier("avatar_student_5", "drawable", packageName),
            resources.getIdentifier("avatar_student_6", "drawable", packageName),
            resources.getIdentifier("avatar_student_7", "drawable", packageName),
            resources.getIdentifier("avatar_student_8", "drawable", packageName),
            resources.getIdentifier("avatar_student_9", "drawable", packageName)
        ).filter { it != 0 }

        rvAvatars.adapter = AvatarAdapter(avatars) { selectedResId ->
            ivAvatar.setImageResource(selectedResId)
            val avatarName = resources.getResourceEntryName(selectedResId)

            val sharedPreferences = getSharedPreferences("LocalAppPrefs", Context.MODE_PRIVATE)
            val currentUserId = sharedPreferences.getString("CURRENT_USER_ID", "") ?: ""

            if (userRole.lowercase() == "student" && currentUserId.isNotEmpty()) {
                // 🌐 شلیک و ذخیره قطعی تغییر آواتار روی دیتابیس آنلاین سرور
                val request = UpdateAvatarRequest(currentUserId, avatarName)
                RetrofitClient.instance.updateAvatar(request).enqueue(object : Callback<ApiResponse> {
                    override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                        if (response.isSuccessful && response.body()?.status == "success") {
                            Toast.makeText(this@ProfileActivity, "عکس پروفایل شما در سرور ذخیره شد", Toast.LENGTH_SHORT).show()
                            setupProfileData() // بازخوانی دیتا برای تثبیت
                        } else {
                            Toast.makeText(this@ProfileActivity, "خطا در ثبت عکس در سرور", Toast.LENGTH_SHORT).show()
                        }
                    }
                    override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                        Toast.makeText(this@ProfileActivity, "خطا در اتصال به اینترنت", Toast.LENGTH_SHORT).show()
                    }
                })
            }
            dialog.dismiss()
        }
        dialog.setContentView(view)
        dialog.show()
    }
}