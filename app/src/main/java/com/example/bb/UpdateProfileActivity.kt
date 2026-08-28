package com.example.bb

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/**
 * The optional and forced password-change flows intentionally use the same screen.
 * In forced mode the user may only change the password or sign out.
 */
class UpdateProfileActivity : BaseActivity() {

    private lateinit var oldLayout: TextInputLayout
    private lateinit var newLayout: TextInputLayout
    private lateinit var confirmLayout: TextInputLayout
    private lateinit var oldPassword: TextInputEditText
    private lateinit var newPassword: TextInputEditText
    private lateinit var confirmPassword: TextInputEditText
    private lateinit var saveButton: MaterialButton
    private lateinit var progress: View

    private var forced = false
    private var openMainAfterChange = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_update_profile)

        applyMode(intent)

        oldLayout = findViewById(R.id.layoutOldPassword)
        newLayout = findViewById(R.id.layoutNewPassword)
        confirmLayout = findViewById(R.id.layoutConfirmPassword)
        oldPassword = findViewById(R.id.etOldPassword)
        newPassword = findViewById(R.id.etNewPassword)
        confirmPassword = findViewById(R.id.etConfirmPassword)
        saveButton = findViewById(R.id.btnUpdatePassword)
        progress = findViewById(R.id.progressUpdatePassword)

        val backButton = findViewById<ImageView>(R.id.btnUpdateProfileBack)
        backButton.setOnClickListener { handleBack() }
        renderMode()
        saveButton.setOnClickListener { submitPasswordChange() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = handleBack()
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyMode(intent)
        if (::saveButton.isInitialized) renderMode()
    }

    private fun applyMode(source: Intent) {
        forced = forced || source.getBooleanExtra(EXTRA_FORCE_PASSWORD_CHANGE, false)
        openMainAfterChange = openMainAfterChange || forced ||
            source.getBooleanExtra(EXTRA_OPEN_MAIN_AFTER_CHANGE, false)
    }

    private fun renderMode() {
        findViewById<TextView>(R.id.tvPasswordChangeNotice).visibility =
            if (forced) View.VISIBLE else View.GONE
        saveButton.text = if (forced) "ذخیره و ورود به برنامه" else "تغییر رمز عبور"
    }

    private fun submitPasswordChange() {
        clearErrors()

        val oldValue = oldPassword.text?.toString().orEmpty()
        val newValue = newPassword.text?.toString().orEmpty()
        val confirmValue = confirmPassword.text?.toString().orEmpty()
        val prefs = getSharedPreferences("LocalAppPrefs", Context.MODE_PRIVATE)
        val nationalId = prefs.getString("CURRENT_USERNAME", "").orEmpty()

        when {
            oldValue.isBlank() -> showFieldError(oldLayout, oldPassword, "رمز عبور فعلی را وارد کنید")
            newValue.isBlank() -> showFieldError(newLayout, newPassword, "رمز عبور جدید را وارد کنید")
            newValue.length < 8 -> showFieldError(newLayout, newPassword, "رمز جدید باید حداقل ۸ کاراکتر باشد")
            newValue.any(Char::isWhitespace) -> showFieldError(newLayout, newPassword, "رمز عبور نباید شامل فاصله باشد")
            newValue == oldValue -> showFieldError(newLayout, newPassword, "رمز جدید نباید با رمز فعلی یکسان باشد")
            nationalId.isNotBlank() && newValue == nationalId ->
                showFieldError(newLayout, newPassword, "رمز جدید نباید با کد ملی شما یکسان باشد")
            confirmValue.isBlank() -> showFieldError(confirmLayout, confirmPassword, "تکرار رمز عبور جدید را وارد کنید")
            newValue != confirmValue ->
                showFieldError(confirmLayout, confirmPassword, "تکرار رمز عبور با رمز جدید یکسان نیست")
            else -> sendPasswordChange(oldValue, newValue)
        }
    }

    private fun sendPasswordChange(oldValue: String, newValue: String) {
        setLoading(true)
        RetrofitClient.instance.updatePassword(
            UpdatePasswordRequest(oldPassword = oldValue, newPassword = newValue)
        ).enqueue(object : Callback<ApiResponse> {
            override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                setLoading(false)
                val body = response.body()
                if (response.isSuccessful && body?.status == "success") {
                    val prefs = getSharedPreferences("LocalAppPrefs", Context.MODE_PRIVATE)
                    prefs.edit().putBoolean("MUST_CHANGE_PASSWORD", false).apply()
                    AppToast.makeText(
                        this@UpdateProfileActivity,
                        body.message.ifBlank { "رمز عبور با موفقیت تغییر کرد" },
                        Toast.LENGTH_SHORT
                    ).show()

                    if (openMainAfterChange) {
                        startActivity(Intent(this@UpdateProfileActivity, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            putExtra("USER_ROLE", prefs.getString("CURRENT_USER_ROLE", "STUDENT"))
                        })
                        finish()
                    } else {
                        finish()
                    }
                    return
                }

                val apiError = if (response.isSuccessful) body else ApiErrorParser.parse(response)
                val message = ApiErrorParser.userMessage(
                    response,
                    apiError,
                    "تغییر رمز عبور کامل نشد"
                )
                showServerPasswordError(apiError?.code, message)
            }

            override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                setLoading(false)
                AppToast.makeText(
                    this@UpdateProfileActivity,
                    ApiErrorParser.networkMessage(t, "تغییر رمز عبور"),
                    Toast.LENGTH_LONG
                ).show()
            }
        })
    }

    private fun showServerPasswordError(code: String?, message: String) {
        when (code) {
            "CURRENT_PASSWORD_REQUIRED", "CURRENT_PASSWORD_INCORRECT" ->
                showFieldError(oldLayout, oldPassword, message)
            "PASSWORD_REQUIRED", "PASSWORD_TOO_SHORT", "PASSWORD_CONTAINS_SPACE",
            "PASSWORD_SAME_AS_USERNAME", "PASSWORD_UNCHANGED" ->
                showFieldError(newLayout, newPassword, message)
            else -> AppToast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun showFieldError(layout: TextInputLayout, field: TextInputEditText, message: String) {
        layout.error = message
        field.requestFocus()
        AppToast.warning(this, message)
    }

    private fun clearErrors() {
        oldLayout.error = null
        newLayout.error = null
        confirmLayout.error = null
    }

    private fun setLoading(loading: Boolean) {
        saveButton.isEnabled = !loading
        saveButton.alpha = if (loading) 0.65f else 1f
        oldPassword.isEnabled = !loading
        newPassword.isEnabled = !loading
        confirmPassword.isEnabled = !loading
        progress.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun handleBack() {
        if (!forced) {
            finish()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("خروج از حساب")
            .setMessage("برای ورود به برنامه باید رمز اولیه را تغییر دهید. آیا می‌خواهید از حساب خارج شوید؟")
            .setPositiveButton("خروج") { _, _ -> logoutAndReturnToLogin() }
            .setNegativeButton("ادامه تغییر رمز", null)
            .show()
    }

    private fun logoutAndReturnToLogin() {
        RetrofitClient.instance.logout().enqueue(object : Callback<ApiResponse> {
            override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) = clearSessionAndOpenLogin()
            override fun onFailure(call: Call<ApiResponse>, t: Throwable) = clearSessionAndOpenLogin()
        })
    }

    private fun clearSessionAndOpenLogin() {
        getSharedPreferences("LocalAppPrefs", Context.MODE_PRIVATE).edit().clear().apply()
        AppToast.success(applicationContext, "با موفقیت از حساب کاربری خارج شدید")
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    companion object {
        const val EXTRA_FORCE_PASSWORD_CHANGE = "FORCE_PASSWORD_CHANGE"
        const val EXTRA_OPEN_MAIN_AFTER_CHANGE = "OPEN_MAIN_AFTER_CHANGE"
    }
}
