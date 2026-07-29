package com.example.bb

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ForceChangePasswordActivity : AppCompatActivity() {
    private lateinit var oldPassword: TextInputEditText
    private lateinit var newPassword: TextInputEditText
    private lateinit var confirmPassword: TextInputEditText
    private lateinit var saveButton: MaterialButton
    private lateinit var progress: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_force_change_password)
        oldPassword = findViewById(R.id.etForcedOldPassword)
        newPassword = findViewById(R.id.etForcedNewPassword)
        confirmPassword = findViewById(R.id.etForcedConfirmPassword)
        saveButton = findViewById(R.id.btnForcedSavePassword)
        progress = findViewById(R.id.progressForcedPassword)
        findViewById<ImageView>(R.id.btnForcedPasswordBack).setOnClickListener { logoutAndReturn() }
        saveButton.setOnClickListener { submit() }
    }

    override fun onBackPressed() = logoutAndReturn()

    private fun submit() {
        clearErrors()
        val old = oldPassword.text?.toString().orEmpty()
        val next = newPassword.text?.toString().orEmpty()
        val confirm = confirmPassword.text?.toString().orEmpty()
        when {
            old.isBlank() -> oldPassword.error = "رمز اولیه یا موقت را وارد کنید"
            next.length < 8 -> newPassword.error = "رمز جدید حداقل ۸ کاراکتر باشد"
            next == old -> newPassword.error = "رمز جدید با رمز قبلی متفاوت باشد"
            next != confirm -> confirmPassword.error = "تکرار رمز یکسان نیست"
            else -> {
                setLoading(true)
                RetrofitClient.instance.updatePassword(UpdatePasswordRequest(old, next))
                    .enqueue(object : Callback<ApiResponse> {
                        override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                            setLoading(false)
                            val body = response.body()
                            if (response.isSuccessful && body?.status == "success") {
                                getSharedPreferences("LocalAppPrefs", MODE_PRIVATE).edit()
                                    .putBoolean("MUST_CHANGE_PASSWORD", false).apply()
                                Toast.makeText(this@ForceChangePasswordActivity, "رمز عبور تغییر کرد", Toast.LENGTH_SHORT).show()
                                startActivity(Intent(this@ForceChangePasswordActivity, MainActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                                finish()
                            } else {
                                Toast.makeText(this@ForceChangePasswordActivity, body?.message ?: "تغییر رمز انجام نشد", Toast.LENGTH_LONG).show()
                            }
                        }
                        override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                            setLoading(false)
                            Toast.makeText(this@ForceChangePasswordActivity, "ارتباط با سرور برقرار نشد", Toast.LENGTH_LONG).show()
                        }
                    })
            }
        }
    }

    private fun clearErrors() {
        oldPassword.error = null; newPassword.error = null; confirmPassword.error = null
    }

    private fun setLoading(value: Boolean) {
        saveButton.isEnabled = !value
        progress.visibility = if (value) View.VISIBLE else View.GONE
    }

    private fun logoutAndReturn() {
        getSharedPreferences("LocalAppPrefs", MODE_PRIVATE).edit().clear().apply()
        startActivity(Intent(this, LoginActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }
}
