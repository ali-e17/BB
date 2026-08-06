package com.example.bb

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ResultMessagesActivity : BaseActivity() {

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
                    val body = response.body()
                    val apiError = ApiErrorParser.parse(response)
                    if (response.isSuccessful && body?.status == "success") {
                        render(
                            ReportCardViewActivity.DEFAULT_RESULT_MESSAGES +
                                body.messages
                        )
                    } else {
                        render(ReportCardViewActivity.DEFAULT_RESULT_MESSAGES)
                        toast(
                            ApiErrorParser.userMessage(
                                response,
                                apiError,
                                "دریافت متن‌ها انجام نشد؛ متن‌های اصلی نمایش داده شدند"
                            )
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
                        ApiErrorParser.networkMessage(t, "دریافت متن‌های کارنامه") +
                            " متن‌های اصلی نمایش داده شدند."
                    )
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
                val apiError = ApiErrorParser.parse(response)

                if (response.isSuccessful && body?.status == "success") {
                    toast(body.message.ifBlank { "متن‌ها ذخیره شدند" })
                    finish()
                } else {
                    toast(
                        body?.message?.takeIf { it.isNotBlank() }
                            ?: ApiErrorParser.userMessage(response, apiError, "ذخیره متن‌ها انجام نشد")
                    )
                }
            }

            override fun onFailure(
                call: Call<ApiResponse>,
                t: Throwable
            ) {
                setLoading(false)
                toast(ApiErrorParser.networkMessage(t, "ذخیره متن‌های کارنامه"))
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
}
