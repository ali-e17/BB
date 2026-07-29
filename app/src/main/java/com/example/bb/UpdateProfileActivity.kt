package com.example.bb

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class UpdateProfileActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_update_profile)

        val forced = intent.getBooleanExtra(EXTRA_FORCE_PASSWORD_CHANGE, false)
        val openMainAfterChange = intent.getBooleanExtra(EXTRA_OPEN_MAIN_AFTER_CHANGE, false)
        val backButton = findViewById<ImageView>(R.id.btnUpdateProfileBack)
        val notice = findViewById<TextView>(R.id.tvPasswordChangeNotice)

        backButton.visibility = if (forced) View.INVISIBLE else View.VISIBLE
        notice.visibility = if (forced) View.VISIBLE else View.GONE
        backButton.setOnClickListener { finish() }

        if (forced) {
            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    Toast.makeText(
                        this@UpdateProfileActivity,
                        "برای ادامه باید رمز اولیه را تغییر دهید",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
        }

        val oldPassword = findViewById<TextInputEditText>(R.id.etOldPassword)
        val newPassword = findViewById<TextInputEditText>(R.id.etNewPassword)
        val confirmPassword = findViewById<TextInputEditText>(R.id.etConfirmPassword)
        val save = findViewById<Button>(R.id.btnUpdatePassword)
        val prefs = getSharedPreferences("LocalAppPrefs", Context.MODE_PRIVATE)

        save.setOnClickListener {
            oldPassword.error = null
            newPassword.error = null
            confirmPassword.error = null

            val oldValue = oldPassword.text?.toString().orEmpty()
            val newValue = newPassword.text?.toString().orEmpty()
            val confirmValue = confirmPassword.text?.toString().orEmpty()
            val nationalId = prefs.getString("CURRENT_USERNAME", "").orEmpty()

            when {
                oldValue.isBlank() -> {
                    oldPassword.error = "رمز عبور فعلی را وارد کنید"
                    oldPassword.requestFocus()
                }
                newValue.length < 6 -> {
                    newPassword.error = "رمز جدید باید حداقل ۶ نویسه باشد"
                    newPassword.requestFocus()
                }
                newValue == oldValue -> {
                    newPassword.error = "رمز جدید نباید با رمز فعلی یکسان باشد"
                    newPassword.requestFocus()
                }
                newValue == nationalId -> {
                    newPassword.error = "رمز جدید نباید کد ملی شما باشد"
                    newPassword.requestFocus()
                }
                newValue != confirmValue -> {
                    confirmPassword.error = "تکرار رمز عبور با رمز جدید یکسان نیست"
                    confirmPassword.requestFocus()
                }
                else -> {
                    save.isEnabled = false
                    save.text = "در حال ذخیره..."
                    RetrofitClient.instance.updatePassword(
                        UpdatePasswordRequest(oldPassword = oldValue, newPassword = newValue)
                    ).enqueue(object : Callback<ApiResponse> {
                        override fun onResponse(
                            call: Call<ApiResponse>,
                            response: Response<ApiResponse>
                        ) {
                            save.isEnabled = true
                            save.text = "تغییر رمز عبور"
                            val body = response.body()
                            if (response.isSuccessful && body?.status == "success") {
                                prefs.edit().putBoolean("MUST_CHANGE_PASSWORD", false).apply()
                                Toast.makeText(
                                    this@UpdateProfileActivity,
                                    body.message.ifBlank { "رمز عبور تغییر کرد" },
                                    Toast.LENGTH_SHORT
                                ).show()

                                if (openMainAfterChange) {
                                    startActivity(Intent(this@UpdateProfileActivity, MainActivity::class.java).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                        putExtra("USER_ROLE", prefs.getString("CURRENT_USER_ROLE", "STUDENT"))
                                    })
                                } else {
                                    finish()
                                }
                            } else {
                                Toast.makeText(
                                    this@UpdateProfileActivity,
                                    body?.message ?: "تغییر رمز عبور انجام نشد",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }

                        override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                            save.isEnabled = true
                            save.text = "تغییر رمز عبور"
                            Toast.makeText(
                                this@UpdateProfileActivity,
                                "خطا در اتصال به سرور",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    })
                }
            }
        }
    }

    companion object {
        const val EXTRA_FORCE_PASSWORD_CHANGE = "FORCE_PASSWORD_CHANGE"
        const val EXTRA_OPEN_MAIN_AFTER_CHANGE = "OPEN_MAIN_AFTER_CHANGE"
    }
}
