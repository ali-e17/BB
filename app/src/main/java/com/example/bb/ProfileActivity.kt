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
    private lateinit var ivAvatar: ImageView
    private lateinit var userRole: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        val prefs = getSharedPreferences("LocalAppPrefs", Context.MODE_PRIVATE)
        userRole = prefs.getString("CURRENT_USER_ROLE", "STUDENT").orEmpty().uppercase()

        findViewById<ImageView>(R.id.btnProfileBack).setOnClickListener { finish() }
        tvUserName = findViewById(R.id.tvUserName)
        tvUserRole = findViewById(R.id.tvUserRole)
        tvStudentClassStatus = findViewById(R.id.tvStudentClassStatus)
        layoutStudentOptions = findViewById(R.id.layoutStudentOptions)
        layoutTeacherOptions = findViewById(R.id.layoutTeacherOptions)
        ivAvatar = findViewById(R.id.ivAvatar)

        findViewById<LinearLayout>(R.id.btnChangePassword).setOnClickListener {
            startActivity(Intent(this, UpdateProfileActivity::class.java).putExtra("USER_ROLE", userRole))
        }
        findViewById<LinearLayout>(R.id.btnLogout).setOnClickListener { confirmLogout() }
        findViewById<TextView>(R.id.btnChangeAvatar).setOnClickListener { showAvatarSelectionDialog() }
        findViewById<LinearLayout>(R.id.btnViewTeacherClasses).setOnClickListener {
            startActivity(Intent(this, TeacherHistoryActivity::class.java))
        }

        tvUserName.text = prefs.getString("CURRENT_DISPLAY_NAME", "کاربر عزیز")
        applyAvatar(prefs.getString("CURRENT_AVATAR_NAME", "").orEmpty())
        renderRoleState()
        loadProfile()
    }

    override fun onResume() {
        super.onResume()
        loadProfile()
    }

    private fun renderRoleState() {
        tvUserRole.text = when (userRole) {
            "ADMIN" -> "مدیر آموزشگاه"
            "TEACHER" -> "استاد آموزشگاه"
            else -> "دانش‌آموز آموزشگاه"
        }
        layoutStudentOptions.visibility = if (userRole == "STUDENT") View.VISIBLE else View.GONE
        layoutTeacherOptions.visibility = if (userRole == "TEACHER") View.VISIBLE else View.GONE
        tvStudentClassStatus.visibility = if (userRole == "STUDENT") View.VISIBLE else View.GONE
    }

    private fun loadProfile() {
        RetrofitClient.instance.getProfile().enqueue(object : Callback<ProfileResponse> {
            override fun onResponse(call: Call<ProfileResponse>, response: Response<ProfileResponse>) {
                val body = response.body()
                if (!response.isSuccessful || body?.status != "success") return
                val prefs = getSharedPreferences("LocalAppPrefs", Context.MODE_PRIVATE)
                prefs.edit()
                    .putString("CURRENT_DISPLAY_NAME", body.displayName)
                    .putString("CURRENT_AVATAR_NAME", body.avatarName)
                    .apply()
                tvUserName.text = body.displayName.ifBlank { "کاربر عزیز" }
                applyAvatar(body.avatarName)
                if (userRole == "STUDENT") {
                    tvStudentClassStatus.text = body.className
                        ?.takeIf { it.isNotBlank() }
                        ?.let { "کلاس فعلی شما: $it" }
                        ?: "هنوز در کلاس فعالی ثبت‌نام نشده‌اید"
                }
            }
            override fun onFailure(call: Call<ProfileResponse>, t: Throwable) {
                if (userRole == "STUDENT") tvStudentClassStatus.text = "وضعیت کلاس در حال حاضر در دسترس نیست"
            }
        })
    }

    private fun applyAvatar(name: String) {
        val fallback = when (userRole) {
            "ADMIN" -> "avatar_admin_1"
            "TEACHER" -> "avatar_teacher_1"
            else -> "avatar_student_1"
        }
        val res = resources.getIdentifier(name.ifBlank { fallback }, "drawable", packageName)
        ivAvatar.setImageResource(if (res != 0) res else resources.getIdentifier(fallback, "drawable", packageName))
    }

    private fun showAvatarSelectionDialog() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_avatar_selector, null)
        val names = when (userRole) {
            "ADMIN" -> (1..4).map { "avatar_admin_$it" }
            "TEACHER" -> (1..6).map { "avatar_teacher_$it" }
            else -> (1..9).map { "avatar_student_$it" }
        }
        val resourcesList = names.map { resources.getIdentifier(it, "drawable", packageName) }.filter { it != 0 }
        view.findViewById<RecyclerView>(R.id.rvAvatarGrid).adapter = AvatarAdapter(resourcesList) { selected ->
            val avatarName = resources.getResourceEntryName(selected)
            RetrofitClient.instance.updateAvatar(
                UpdateAvatarRequest(
                    userId = getSharedPreferences("LocalAppPrefs", Context.MODE_PRIVATE)
                        .getString("CURRENT_USER_ID", "").orEmpty(),
                    avatarName = avatarName,
                    role = userRole
                )
            ).enqueue(object : Callback<ApiResponse> {
                override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                    if (response.isSuccessful && response.body()?.status == "success") {
                        getSharedPreferences("LocalAppPrefs", Context.MODE_PRIVATE)
                            .edit().putString("CURRENT_AVATAR_NAME", avatarName).apply()
                        ivAvatar.setImageResource(selected)
                        Toast.makeText(this@ProfileActivity, "عکس پروفایل ذخیره شد", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@ProfileActivity, response.body()?.message ?: "ذخیره عکس انجام نشد", Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                    Toast.makeText(this@ProfileActivity, "خطا در اتصال به سرور", Toast.LENGTH_SHORT).show()
                }
            })
            dialog.dismiss()
        }
        dialog.setContentView(view)
        dialog.show()
    }

    private fun confirmLogout() {
        AlertDialog.Builder(this)
            .setTitle("خروج از حساب")
            .setMessage("از حساب کاربری خارج می‌شوید؟")
            .setPositiveButton("خروج") { _, _ -> logout() }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun logout() {
        RetrofitClient.instance.logout().enqueue(object : Callback<ApiResponse> {
            override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) = clearSession()
            override fun onFailure(call: Call<ApiResponse>, t: Throwable) = clearSession()
        })
    }

    private fun clearSession() {
        getSharedPreferences("LocalAppPrefs", Context.MODE_PRIVATE).edit().apply {
            remove("IS_LOGGED_IN")
            remove("CURRENT_USER_ROLE")
            remove("CURRENT_USERNAME")
            remove("CURRENT_USER_ID")
            remove("CURRENT_DISPLAY_NAME")
            remove("CURRENT_AVATAR_NAME")
            remove("API_TOKEN")
            remove("API_TOKEN_EXPIRES_AT")
            apply()
        }
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}
