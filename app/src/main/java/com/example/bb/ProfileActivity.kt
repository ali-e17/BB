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

class ProfileActivity : AppCompatActivity() {

    private lateinit var tvUserName: TextView
    private lateinit var tvUserRole: TextView
    private lateinit var tvStudentClassStatus: TextView // به متغیرهای کلاس اضافه شد
    private lateinit var layoutStudentOptions: LinearLayout
    private lateinit var layoutTeacherOptions: LinearLayout
    private lateinit var userRole: String

    private lateinit var ivAvatar: ImageView
    private lateinit var btnChangeAvatar: TextView
    private lateinit var btnLogout: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        val sharedPreferences = getSharedPreferences("LocalAppPrefs", Context.MODE_PRIVATE)

        tvUserName = findViewById(R.id.tvUserName)
        tvUserRole = findViewById(R.id.tvUserRole)
        tvStudentClassStatus = findViewById(R.id.tvStudentClassStatus) // مقداردهی اولیه
        layoutStudentOptions = findViewById(R.id.layoutStudentOptions)
        layoutTeacherOptions = findViewById(R.id.layoutTeacherOptions)

        ivAvatar = findViewById(R.id.ivAvatar)
        btnChangeAvatar = findViewById(R.id.btnChangeAvatar)
        btnLogout = findViewById(R.id.btnLogout)

        val btnChangeCredentials = findViewById<LinearLayout>(R.id.btnChangeCredentials)

        val savedRoleFallback = sharedPreferences.getString("CURRENT_USER_ROLE", "student") ?: "student"
        userRole = intent.getStringExtra("USER_ROLE") ?: savedRoleFallback

        btnChangeCredentials.setOnClickListener {
            val intent = Intent(this, UpdateProfileActivity::class.java)
            intent.putExtra("USER_ROLE", userRole.uppercase())
            startActivity(intent)
        }

        btnChangeAvatar.setOnClickListener {
            showAvatarSelectionDialog()
        }

        btnLogout.setOnClickListener {
            showLogoutConfirmationDialog()
        }
    }

    private fun showLogoutConfirmationDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("خروج از حساب")
        builder.setMessage("آیا مطمئن هستید که می‌خواهید از حساب کاربری خود خارج شوید؟")

        builder.setPositiveButton("بله") { dialog, _ ->
            val sharedPreferences = getSharedPreferences("LocalAppPrefs", Context.MODE_PRIVATE)

            sharedPreferences.edit().apply {
                putBoolean("IS_LOGGED_IN", false)
                putString("CURRENT_USER_ROLE", null)
                putString("CURRENT_USERNAME", null)
                apply()
            }

            Toast.makeText(this, "از حساب خود خارج شدید", Toast.LENGTH_SHORT).show()

            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        builder.setNegativeButton("خیر") { dialog, _ ->
            dialog.dismiss()
        }

        val alertDialog = builder.create()
        alertDialog.show()
    }

    private fun showAvatarSelectionDialog() {
        val sharedPreferences = getSharedPreferences("LocalAppPrefs", Context.MODE_PRIVATE)
        val roleUpper = userRole.uppercase()

        val filteredAvatars = when (roleUpper) {
            "ADMIN" -> listOf(R.drawable.avatar_admin_1, R.drawable.avatar_admin_2, R.drawable.avatar_admin_3, R.drawable.avatar_admin_4, R.drawable.avatar_admin_5)
            "TEACHER" -> listOf(R.drawable.avatar_teacher_1, R.drawable.avatar_teacher_2, R.drawable.avatar_teacher_3, R.drawable.avatar_teacher_4, R.drawable.avatar_teacher_5, R.drawable.avatar_teacher_6)
            else -> listOf(R.drawable.avatar_student_1, R.drawable.avatar_student_2, R.drawable.avatar_student_3, R.drawable.avatar_student_4, R.drawable.avatar_student_5, R.drawable.avatar_student_6, R.drawable.avatar_student_7, R.drawable.avatar_student_8, R.drawable.avatar_student_9)
        }

        val dialog = BottomSheetDialog(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_avatar_selector, null)
        dialog.setContentView(dialogView)

        val rvAvatarGrid = dialogView.findViewById<RecyclerView>(R.id.rvAvatarGrid)

        rvAvatarGrid.adapter = AvatarAdapter(filteredAvatars) { clickedResId ->
            val clickedAvatarName = resources.getResourceEntryName(clickedResId)
            sharedPreferences.edit().putString("${roleUpper}_AVATAR", clickedAvatarName).apply()
            ivAvatar.setImageResource(clickedResId)
            Toast.makeText(this, "آواتار با موفقیت تغییر یافت", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    override fun onResume() {
        super.onResume()

        val sharedPreferences = getSharedPreferences("LocalAppPrefs", Context.MODE_PRIVATE)
        val roleUpper = userRole.uppercase()
        val currentUsername = sharedPreferences.getString("CURRENT_USERNAME", "") ?: ""

        val intentUsername = intent.getStringExtra("USERNAME")
        if (!intentUsername.isNullOrEmpty()) {
            sharedPreferences.edit().putString("${roleUpper}_USERNAME", intentUsername).apply()
            intent.removeExtra("USERNAME")
        }

        val role = runCatching { UserRole.valueOf(roleUpper) }.getOrDefault(UserRole.STUDENT)
        tvUserName.text = AppDatabase.getDisplayName(role, currentUsername)

        var savedAvatarName = sharedPreferences.getString("${roleUpper}_AVATAR", null)
        var resId = 0

        if (!savedAvatarName.isNullOrEmpty()) {
            resId = resources.getIdentifier(savedAvatarName, "drawable", packageName)
        }

        if (resId == 0) {
            savedAvatarName = when (roleUpper) {
                "ADMIN" -> "avatar_admin"
                "TEACHER" -> listOf("avatar_teacher_1", "avatar_teacher_2", "avatar_teacher_3").random()
                else -> listOf("avatar_student_1", "avatar_student_2", "student_avatar_2", "avatar_student_4").random()
            }
            sharedPreferences.edit().putString("${roleUpper}_AVATAR", savedAvatarName).apply()
            resId = resources.getIdentifier(savedAvatarName, "drawable", packageName)
        }

        if (resId != 0) {
            ivAvatar.setImageResource(resId)
        } else {
            ivAvatar.setImageResource(android.R.drawable.sym_def_app_icon)
        }

        // مدیریت هوشمند نمایش المان‌ها بر اساس نقش
        when (userRole.lowercase()) {
            "student" -> {
                tvUserRole.text = "دانش‌آموز آموزشگاه"
                layoutStudentOptions.visibility = View.VISIBLE
                layoutTeacherOptions.visibility = View.GONE
                tvStudentClassStatus.visibility = View.VISIBLE // فقط برای دانش‌آموز فعال می‌شود

                val student = AppDatabase.getStudentByUsername(currentUsername)
                if (student?.classId != null) {
                    tvStudentClassStatus.text = "کلاس فعلی شما: ${AppDatabase.getClassNameById(student.classId)}"
                } else {
                    tvStudentClassStatus.text = "شما هنوز در هیچ کلاسی ثبت‌نام نشده‌اید."
                }
            }
            "teacher" -> {
                tvUserRole.text = "مدرس رسمی بیان برتر"
                layoutStudentOptions.visibility = View.GONE
                layoutTeacherOptions.visibility = View.VISIBLE
                tvStudentClassStatus.visibility = View.GONE // برای مدرس مخفی می‌شود
            }
            "admin" -> {
                tvUserRole.text = "دسترسی کامل (مدیر کل)"
                layoutStudentOptions.visibility = View.GONE
                layoutTeacherOptions.visibility = View.GONE
                tvStudentClassStatus.visibility = View.GONE // برای مدیر مخفی می‌شود
            }
        }
    }
}
