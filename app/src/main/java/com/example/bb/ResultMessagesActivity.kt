package com.example.bb

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ResultMessagesActivity : AppCompatActivity() {

    private val labels = linkedMapOf(
        "FIVE_STAR" to "★★★★★  پنج ستاره",
        "FOUR_STAR" to "★★★★☆  چهار ستاره",
        "THREE_STAR" to "★★★☆☆  سه ستاره",
        "TWO_STAR" to "★★☆☆☆  دو ستاره",
        "ONE_STAR" to "★☆☆☆☆  یک ستاره",
        "PASS_NO_STAR" to "☆☆☆☆☆  قبول بدون ستاره",
        "CONDITIONAL" to "مشروط (Conditional)",
        "FAILED" to "مردود (Fail)"
    )

    private lateinit var container: LinearLayout
    private lateinit var saveButton: MaterialButton
    private lateinit var restoreButton: MaterialButton
    private lateinit var progress: View
    private val inputs = linkedMapOf<String, TextInputEditText>()

    private val prefs by lazy {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result_messages)

        findViewById<ImageView>(R.id.btnResultMessagesBack)
            .setOnClickListener { finish() }

        container = findViewById(R.id.containerResultMessages)
        saveButton = findViewById(R.id.btnSaveResultMessages)
        restoreButton = findViewById(R.id.btnRestoreDefaultMessages)
        progress = findViewById(R.id.progressResultMessages)

        saveButton.setOnClickListener { submit() }
        restoreButton.setOnClickListener {
            render(ReportCardViewActivity.DEFAULT_RESULT_MESSAGES)
            toast("متن‌های اصلی جای‌گذاری شدند؛ برای ثبت نهایی، ذخیره را بزنید")
        }

        load()
    }

    private fun load() {
        setLoading(true)

        RetrofitClient.instance.getResultMessages()
            .enqueue(object : Callback<ResultMessagesResponse> {
                override fun onResponse(
                    call: Call<ResultMessagesResponse>,
                    response: Response<ResultMessagesResponse>
                ) {
                    setLoading(false)
                    val serverValues = response.body()?.messages.orEmpty()
                    val appliedVersion = prefs.getInt(KEY_MESSAGES_VERSION, 0)

                    if (appliedVersion < APPROVED_MESSAGES_VERSION) {
                        render(ReportCardViewActivity.DEFAULT_RESULT_MESSAGES)
                        applyApprovedMessagesOnce()
                    } else {
                        render(
                            ReportCardViewActivity.DEFAULT_RESULT_MESSAGES +
                                serverValues
                        )
                    }
                }

                override fun onFailure(
                    call: Call<ResultMessagesResponse>,
                    t: Throwable
                ) {
                    setLoading(false)
                    render(ReportCardViewActivity.DEFAULT_RESULT_MESSAGES)
                    toast(
                        "متن‌های اصلی نمایش داده شدند؛ برای ثبت روی سرور، ذخیره را بزنید"
                    )
                }
            })
    }

    /**
     * در اولین ورود پس از این نسخه، هشت متن تاییدشده کاربر را یک بار روی سرور
     * ذخیره می‌کند. بعد از آن، ویرایش‌های بعدی مدیر محفوظ می‌مانند.
     */
    private fun applyApprovedMessagesOnce() {
        setLoading(true)

        RetrofitClient.instance.saveResultMessages(
            SaveResultMessagesRequest(
                ReportCardViewActivity.DEFAULT_RESULT_MESSAGES
            )
        ).enqueue(object : Callback<ApiResponse> {
            override fun onResponse(
                call: Call<ApiResponse>,
                response: Response<ApiResponse>
            ) {
                setLoading(false)
                val body = response.body()

                if (response.isSuccessful && body?.status == "success") {
                    prefs.edit()
                        .putInt(
                            KEY_MESSAGES_VERSION,
                            APPROVED_MESSAGES_VERSION
                        )
                        .apply()
                    toast("هشت متن تاییدشده با موفقیت اعمال شدند")
                } else {
                    toast(
                        body?.message
                            ?: "ثبت خودکار متن‌ها انجام نشد؛ دکمه ذخیره را بزنید"
                    )
                }
            }

            override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                setLoading(false)
                toast("ثبت خودکار انجام نشد؛ متن‌ها آماده‌اند، ذخیره را بزنید")
            }
        })
    }

    private fun render(values: Map<String, String>) {
        container.removeAllViews()
        inputs.clear()

        labels.forEach { (code, label) ->
            val row = LayoutInflater.from(this).inflate(
                R.layout.item_result_message_edit,
                container,
                false
            )

            row.findViewById<TextView>(
                R.id.txtResultMessageLabel
            ).text = label

            row.findViewById<TextView>(
                R.id.txtResultMessageHint
            ).text = when (code) {
                "CONDITIONAL" ->
                    "برای نمره بین حد مشروطی و حد قبولی کلاس"

                "FAILED" ->
                    "برای نمره پایین‌تر از حد مشروطی کلاس"

                "PASS_NO_STAR" ->
                    "برای قبولی بدون ستاره و بالاتر از حد قبولی کلاس"

                else ->
                    "پیام نمایش‌داده‌شده در کارنامه"
            }

            val input = row.findViewById<TextInputEditText>(
                R.id.etResultMessage
            )

            input.setText(
                values[code]
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: ReportCardViewActivity.DEFAULT_RESULT_MESSAGES
                        .getValue(code)
            )

            inputs[code] = input
            container.addView(row)
        }
    }

    private fun submit() {
        val values = inputs.mapValues { entry ->
            entry.value.text
                ?.toString()
                ?.trim()
                .orEmpty()
        }

        if (values.values.any { it.isBlank() }) {
            toast("متن هر هشت وضعیت را کامل کنید")
            return
        }

        setLoading(true)

        RetrofitClient.instance.saveResultMessages(
            SaveResultMessagesRequest(values)
        ).enqueue(object : Callback<ApiResponse> {
            override fun onResponse(
                call: Call<ApiResponse>,
                response: Response<ApiResponse>
            ) {
                setLoading(false)
                val body = response.body()

                if (response.isSuccessful && body?.status == "success") {
                    prefs.edit()
                        .putInt(
                            KEY_MESSAGES_VERSION,
                            APPROVED_MESSAGES_VERSION
                        )
                        .apply()
                    toast(body.message.ifBlank { "متن‌ها ذخیره شدند" })
                    finish()
                } else {
                    toast(body?.message ?: "ذخیره متن‌ها انجام نشد")
                }
            }

            override fun onFailure(
                call: Call<ApiResponse>,
                t: Throwable
            ) {
                setLoading(false)
                toast("ارتباط با سرور برقرار نشد")
            }
        })
    }

    private fun setLoading(value: Boolean) {
        progress.visibility = if (value) View.VISIBLE else View.GONE
        saveButton.isEnabled = !value
        restoreButton.isEnabled = !value
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    companion object {
        private const val PREFS_NAME = "ReportCardMessagePrefs"
        private const val KEY_MESSAGES_VERSION = "APPROVED_MESSAGES_VERSION"
        private const val APPROVED_MESSAGES_VERSION = 1
    }
}
