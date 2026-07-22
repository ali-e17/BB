package com.example.bb

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class UpdateProfileActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_update_profile)

        findViewById<ImageView>(R.id.btnUpdateProfileBack).setOnClickListener { finish() }

        val oldPassword = findViewById<TextInputEditText>(R.id.etOldPassword)
        val newPassword = findViewById<TextInputEditText>(R.id.etNewPassword)
        val confirmPassword = findViewById<TextInputEditText>(R.id.etConfirmPassword)
        val save = findViewById<Button>(R.id.btnUpdatePassword)
        val prefs = getSharedPreferences("LocalAppPrefs", Context.MODE_PRIVATE)
        val role = (intent.getStringExtra("USER_ROLE")
            ?: prefs.getString("CURRENT_USER_ROLE", "STUDENT")
            ?: "STUDENT").uppercase()

        save.setOnClickListener {
            oldPassword.error = null
            newPassword.error = null
            confirmPassword.error = null

            val oldValue = oldPassword.text?.toString().orEmpty()
            val newValue = newPassword.text?.toString().orEmpty()
            val confirmValue = confirmPassword.text?.toString().orEmpty()

            when {
                oldValue.isBlank() -> {
                    oldPassword.error = "رمز عبور فعلی را وارد کنید"
                    oldPassword.requestFocus()
                }
                newValue.length < 6 -> {
                    newPassword.error = "رمز جدید باید حداقل ۶ نویسه باشد"
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
                        UpdatePasswordRequest(
                            role = role,
                            phone = prefs.getString("CURRENT_USERNAME", "").orEmpty(),
                            oldPassword = oldValue,
                            newPassword = newValue
                        )
                    ).enqueue(object : Callback<ApiResponse> {
                        override fun onResponse(
                            call: Call<ApiResponse>,
                            response: Response<ApiResponse>
                        ) {
                            save.isEnabled = true
                            save.text = "تغییر رمز عبور"
                            val body = response.body()
                            if (response.isSuccessful && body?.status == "success") {
                                Toast.makeText(
                                    this@UpdateProfileActivity,
                                    body.message.ifBlank { "رمز عبور تغییر کرد" },
                                    Toast.LENGTH_SHORT
                                ).show()
                                finish()
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
}
