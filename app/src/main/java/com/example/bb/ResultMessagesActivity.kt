package com.example.bb

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
    private val codes = linkedMapOf(
        "FIVE_STAR" to "پنج ستاره", "FOUR_STAR" to "چهار ستاره", "THREE_STAR" to "سه ستاره",
        "TWO_STAR" to "دو ستاره", "ONE_STAR" to "یک ستاره", "PASS_NO_STAR" to "قبول بدون ستاره",
        "CONDITIONAL" to "مشروط", "FAILED" to "مردود"
    )
    private lateinit var container: LinearLayout
    private lateinit var save: MaterialButton
    private lateinit var progress: View
    private val inputs = linkedMapOf<String, TextInputEditText>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result_messages)
        findViewById<ImageView>(R.id.btnResultMessagesBack).setOnClickListener { finish() }
        container = findViewById(R.id.containerResultMessages)
        save = findViewById(R.id.btnSaveResultMessages)
        progress = findViewById(R.id.progressResultMessages)
        save.setOnClickListener { submit() }
        load()
    }

    private fun load() {
        setLoading(true)
        RetrofitClient.instance.getResultMessages().enqueue(object : Callback<ResultMessagesResponse> {
            override fun onResponse(call: Call<ResultMessagesResponse>, response: Response<ResultMessagesResponse>) {
                setLoading(false); render(response.body()?.messages.orEmpty())
            }
            override fun onFailure(call: Call<ResultMessagesResponse>, t: Throwable) { setLoading(false); render(emptyMap()); toast("دریافت متن‌ها انجام نشد") }
        })
    }

    private fun render(values: Map<String,String>) {
        container.removeAllViews(); inputs.clear()
        codes.forEach { (code,label) ->
            val row = LayoutInflater.from(this).inflate(R.layout.item_result_message_edit, container, false)
            row.findViewById<TextView>(R.id.txtResultMessageLabel).text = label
            val input = row.findViewById<TextInputEditText>(R.id.etResultMessage)
            input.setText(values[code].orEmpty()); inputs[code] = input; container.addView(row)
        }
    }

    private fun submit() {
        val values = inputs.mapValues { it.value.text?.toString()?.trim().orEmpty() }
        if (values.values.any { it.isBlank() }) return toast("متن هر هشت وضعیت را کامل کنید")
        setLoading(true)
        RetrofitClient.instance.saveResultMessages(SaveResultMessagesRequest(values)).enqueue(object : Callback<ApiResponse> {
            override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                setLoading(false); val body=response.body(); toast(body?.message ?: "ذخیره انجام نشد"); if(response.isSuccessful&&body?.status=="success") finish()
            }
            override fun onFailure(call: Call<ApiResponse>, t: Throwable) { setLoading(false); toast("ارتباط با سرور برقرار نشد") }
        })
    }
    private fun setLoading(v:Boolean){progress.visibility=if(v)View.VISIBLE else View.GONE;save.isEnabled=!v}
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()
}
