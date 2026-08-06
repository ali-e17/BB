package com.example.bb

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ProfileActivity : BaseActivity() {

    private lateinit var tvUserName: TextView
    private lateinit var tvUserRole: TextView
    private lateinit var ivAvatar: ImageView
    private lateinit var userRole: String
    private var avatarRequestInProgress = false
    private var profileErrorShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        val prefs = getSharedPreferences("LocalAppPrefs", Context.MODE_PRIVATE)
        userRole = prefs.getString("CURRENT_USER_ROLE", "STUDENT").orEmpty().uppercase()

        findViewById<ImageView>(R.id.btnProfileBack).setOnClickListener { finish() }
        tvUserName = findViewById(R.id.tvUserName)
        tvUserRole = findViewById(R.id.tvUserRole)
        ivAvatar = findViewById(R.id.ivAvatar)

        findViewById<LinearLayout>(R.id.btnChangePassword).setOnClickListener {
            startActivity(Intent(this, UpdateProfileActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.btnLogout).setOnClickListener { confirmLogout() }
        findViewById<TextView>(R.id.btnChangeAvatar).setOnClickListener { showAvatarSelectionDialog() }

        tvUserName.text = prefs.getString("CURRENT_DISPLAY_NAME", "کاربر عزیز")
        tvUserRole.text = roleLabel(userRole)
        applyAvatar(prefs.getString("CURRENT_AVATAR_NAME", "").orEmpty())
    }

    override fun onResume() {
        super.onResume()
        profileErrorShown = false
        loadProfile()
    }

    private fun loadProfile() {
        RetrofitClient.instance.getProfile().enqueue(object : Callback<ProfileResponse> {
            override fun onResponse(call: Call<ProfileResponse>, response: Response<ProfileResponse>) {
                val body = response.body()
                if (response.isSuccessful && body?.status == "success") {
                    profileErrorShown = false
                    val prefs = getSharedPreferences("LocalAppPrefs", Context.MODE_PRIVATE)
                    prefs.edit()
                        .putString("CURRENT_DISPLAY_NAME", body.displayName)
                        .putString("CURRENT_AVATAR_NAME", body.avatarName)
                        .apply()
                    tvUserName.text = body.displayName.ifBlank { "کاربر عزیز" }
                    applyAvatar(body.avatarName)
                    return
                }

                if (!profileErrorShown) {
                    profileErrorShown = true
                    Toast.makeText(
                        this@ProfileActivity,
                        ApiErrorParser.userMessage(response, "اطلاعات پروفایل دریافت نشد"),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            override fun onFailure(call: Call<ProfileResponse>, t: Throwable) {
                if (!profileErrorShown) {
                    profileErrorShown = true
                    Toast.makeText(
                        this@ProfileActivity,
                        ApiErrorParser.networkMessage(t, "دریافت اطلاعات پروفایل"),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        })
    }

    private fun roleLabel(role: String): String = when (role) {
        "ADMIN" -> "مدیر آموزشگاه"
        "TEACHER" -> "استاد آموزشگاه"
        else -> "دانش‌آموز آموزشگاه"
    }

    private fun applyAvatar(name: String) {
        val fallback = when (userRole) {
            "ADMIN" -> "avatar_admin_1"
            "TEACHER" -> "avatar_teacher_1"
            else -> "avatar_student_1"
        }
        val requested = resources.getIdentifier(name.ifBlank { fallback }, "drawable", packageName)
        val fallbackRes = resources.getIdentifier(fallback, "drawable", packageName)
        ivAvatar.setImageResource(if (requested != 0) requested else fallbackRes)
    }

    private fun showAvatarSelectionDialog() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_avatar_selector, null)
        val names = when (userRole) {
            "ADMIN" -> (1..4).map { "avatar_admin_$it" }
            "TEACHER" -> (1..6).map { "avatar_teacher_$it" }
            else -> (1..9).map { "avatar_student_$it" }
        }
        val resourcesList = names
            .map { resources.getIdentifier(it, "drawable", packageName) }
            .filter { it != 0 }

        view.findViewById<RecyclerView>(R.id.rvAvatarGrid).adapter = AvatarAdapter(resourcesList, avatarClick@{ selected ->
            if (avatarRequestInProgress) return@avatarClick
            val avatarName = resources.getResourceEntryName(selected)
            avatarRequestInProgress = true

            RetrofitClient.instance.updateAvatar(
                UpdateAvatarRequest(
                    userId = getSharedPreferences("LocalAppPrefs", Context.MODE_PRIVATE)
                        .getString("CURRENT_USER_ID", "").orEmpty(),
                    avatarName = avatarName,
                    role = userRole
                )
            ).enqueue(object : Callback<ApiResponse> {
                override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                    avatarRequestInProgress = false
                    val body = response.body()
                    if (response.isSuccessful && body?.status == "success") {
                        getSharedPreferences("LocalAppPrefs", Context.MODE_PRIVATE)
                            .edit().putString("CURRENT_AVATAR_NAME", avatarName).apply()
                        ivAvatar.setImageResource(selected)
                        dialog.dismiss()
                        Toast.makeText(this@ProfileActivity, "عکس پروفایل ذخیره شد", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(
                            this@ProfileActivity,
                            body?.message?.takeIf { it.isNotBlank() }
                                ?: ApiErrorParser.userMessage(response, "ذخیره عکس پروفایل انجام نشد"),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                    avatarRequestInProgress = false
                    Toast.makeText(
                        this@ProfileActivity,
                        ApiErrorParser.networkMessage(t, "ذخیره عکس پروفایل"),
                        Toast.LENGTH_LONG
                    ).show()
                }
            })
        })
        dialog.setContentView(view)
        dialog.show()
    }

    private fun confirmLogout() {
        MaterialAlertDialogBuilder(this)
            .setTitle("خروج از حساب")
            .setMessage("آیا می‌خواهید از حساب کاربری خارج شوید؟")
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
            remove("CURRENT_PHONE")
            remove("CURRENT_USER_ID")
            remove("CURRENT_DISPLAY_NAME")
            remove("CURRENT_AVATAR_NAME")
            remove("API_TOKEN")
            remove("API_TOKEN_EXPIRES_AT")
            remove("MUST_CHANGE_PASSWORD")
            apply()
        }
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}
