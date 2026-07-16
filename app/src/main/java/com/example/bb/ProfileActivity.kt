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
        val currentUsername = sharedPreferences.getString("CURRENT_USERNAME", "") ?: ""
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

        val savedAvatarName = sharedPreferences.getString("AVATAR_NAME_${currentUsername}", "avatar_student_1")
        val resId = resources.getIdentifier(savedAvatarName, "drawable", packageName)
        if (resId != 0) {
            ivAvatar.setImageResource(resId)
        } else {
            ivAvatar.setImageResource(android.R.drawable.sym_def_app_icon)
        }

        when (userRole.lowercase()) {
            "student" -> {
                tvUserRole.text = "دانش‌آموز آموزشگاه"
                layoutStudentOptions.visibility = View.VISIBLE
                layoutTeacherOptions.visibility = View.GONE
                tvStudentClassStatus.visibility = View.VISIBLE

                tvStudentClassStatus.text = "در حال بررسی وضعیت کلاس..."

                // 🌟 دریافت آیدی منحصر به فرد کاربر به جای شماره تلفن مشترک
                val currentUserId = sharedPreferences.getString("CURRENT_USER_ID", "") ?: ""

                RetrofitClient.instance.getStudents().enqueue(object : Callback<List<StudentModel>> {
                    override fun onResponse(call: Call<List<StudentModel>>, response: Response<List<StudentModel>>) {
                        if (response.isSuccessful) {
                            val allStudents = response.body().orEmpty()

                            // 🌟 جستجوی ۱۰۰٪ دقیق بر اساس آیدی دیتابیس
                            val myStudent = allStudents.find { it.id == currentUserId }

                            if (!myStudent?.classId.isNullOrBlank()) {
                                RetrofitClient.instance.getClasses().enqueue(object : Callback<List<ClassModel>> {
                                    override fun onResponse(call: Call<List<ClassModel>>, response2: Response<List<ClassModel>>) {
                                        val classes = response2.body().orEmpty()
                                        val myClass = classes.find { it.id == myStudent?.classId }
                                        tvStudentClassStatus.text = " ${myClass?.className ?: "نامشخص"}"
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
            }
            "admin" -> {
                tvUserRole.text = "دسترسی کامل (مدیر کل)"
                layoutStudentOptions.visibility = View.GONE
                layoutTeacherOptions.visibility = View.GONE
                tvStudentClassStatus.visibility = View.GONE
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
            resources.getIdentifier("avatar_student_6", "drawable", packageName)
        ).filter { it != 0 }

        rvAvatars.adapter = AvatarAdapter(avatars) { selectedResId ->
            ivAvatar.setImageResource(selectedResId)
            val avatarName = resources.getResourceEntryName(selectedResId)

            val sharedPreferences = getSharedPreferences("LocalAppPrefs", Context.MODE_PRIVATE)
            val currentUsername = sharedPreferences.getString("CURRENT_USERNAME", "") ?: ""
            sharedPreferences.edit().putString("AVATAR_NAME_${currentUsername}", avatarName).apply()

            if (userRole.lowercase() == "student") {
                val student = AppDatabase.getStudentByUsername(currentUsername)
                student?.let {
                    val updated = it.copy(avatarResId = selectedResId)
                    AppDatabase.upsertStudent(updated, currentUsername)
                }
            }

            Toast.makeText(this, "عکس پروفایل به‌روزرسانی شد", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
        dialog.setContentView(view)
        dialog.show()
    }
}