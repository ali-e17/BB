package com.example.bb

import android.net.Uri
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
        setupContactFooter()

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
        val fallback = "avatar_no_profile" // 🌟 تغییر به پیش‌فرض جدید برای همه
        val requested = resources.getIdentifier(name.ifBlank { fallback }, "drawable", packageName)
        val fallbackRes = resources.getIdentifier(fallback, "drawable", packageName)
        ivAvatar.setImageResource(if (requested != 0) requested else fallbackRes)
    }
    private fun showAvatarSelectionDialog() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_avatar_selector, null)

        // 🌟 اضافه شدن "avatar_no_profile" به اول لیستِ تمام نقش‌ها
        val names = listOf("avatar_no_profile") + when (userRole) {
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
    private fun setupContactFooter() {

        val phoneText = findViewById<TextView>(R.id.tvContactPhone)
        val eitaaText = findViewById<TextView>(R.id.tvContactEitaa)

        val phoneLayout = findViewById<LinearLayout>(R.id.layoutContactPhone)
        val eitaaLayout = findViewById<LinearLayout>(R.id.layoutContactEitaa)
        val addressLayout = findViewById<LinearLayout>(R.id.layoutContactAddress)

        phoneText.text =
            "تماس با آموزشگاه : ${toPersianDigits(ContactConfig.PHONE_NUMBER)}"

        eitaaText.text =
            "ارتباط در ایتا : ${toPersianDigits(ContactConfig.EITAA_NUMBER)}"

        phoneLayout.setOnClickListener {
            val intent = Intent(
                Intent.ACTION_DIAL,
                Uri.parse("tel:${ContactConfig.PHONE_NUMBER}")
            )
            startActivity(intent)
        }

        eitaaLayout.setOnClickListener {
            openEitaa()
        }

        addressLayout.setOnClickListener {
            openSchoolAddress()
        }
    }

    private fun openSchoolAddress() {
        RetrofitClient.instance.getContactInfo()
            .enqueue(object : Callback<ContactInfoResponse> {

                override fun onResponse(
                    call: Call<ContactInfoResponse>,
                    response: Response<ContactInfoResponse>
                ) {
                    val body = response.body()
                    val addressUrl = body?.addressUrl?.trim().orEmpty()

                    if (!response.isSuccessful ||
                        body == null ||
                        (body.status.isNotBlank() && body.status != "success") ||
                        addressUrl.isBlank()
                    ) {
                        Toast.makeText(
                            this@ProfileActivity,
                            "نشانی آموزشگاه در حال حاضر در دسترس نیست",
                            Toast.LENGTH_SHORT
                        ).show()
                        return
                    }

                    openNeshanOrBrowser(addressUrl)
                }

                override fun onFailure(
                    call: Call<ContactInfoResponse>,
                    t: Throwable
                ) {
                    Toast.makeText(
                        this@ProfileActivity,
                        "دریافت نشانی آموزشگاه انجام نشد. اتصال اینترنت را بررسی کنید.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }

    private fun openNeshanOrBrowser(url: String) {
        val uri = runCatching { Uri.parse(url) }.getOrNull()

        if (uri == null || (uri.scheme != "http" && uri.scheme != "https")) {
            Toast.makeText(
                this,
                "لینک نشانی آموزشگاه معتبر نیست",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        try {
            val neshanIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("org.rajman.neshan.traffic.tehran.navigator")
            }
            startActivity(neshanIntent)
        } catch (_: Exception) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            } catch (_: Exception) {
                Toast.makeText(
                    this,
                    "برنامه‌ای برای باز کردن نشانی پیدا نشد",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun toPersianDigits(value: String): String {
        return value
            .replace('0', '۰')
            .replace('1', '۱')
            .replace('2', '۲')
            .replace('3', '۳')
            .replace('4', '۴')
            .replace('5', '۵')
            .replace('6', '۶')
            .replace('7', '۷')
            .replace('8', '۸')
            .replace('9', '۹')
    }
    private fun openEitaa() {

        val eitaaNumber = ContactConfig.EITAA_NUMBER

        try {

            val eitaaIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("eitaa://chat/$eitaaNumber")
            )

            eitaaIntent.setPackage("ir.eitaa.messenger")

            startActivity(eitaaIntent)

        } catch (e: Exception) {

            val browserIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://eitaa.com/$eitaaNumber")
            )

            startActivity(browserIntent)
        }
    }
}
