package com.example.bb

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.UUID

class AddEditTeacherActivity : AppCompatActivity() {

    private var originalUsername = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_edit_teacher)
        findViewById<ImageView>(R.id.btnTeacherBack).setOnClickListener { finish() }

        val first = findViewById<TextInputEditText>(R.id.etTeacherFirstName)
        val last = findViewById<TextInputEditText>(R.id.etTeacherLastName)
        val phone = findViewById<TextInputEditText>(R.id.etTeacherPhone)
        val national = findViewById<TextInputEditText>(R.id.etTeacherNationalId)
        val save = findViewById<Button>(R.id.btnSaveTeacher)
        val progress = findViewById<View>(R.id.progressSavingTeacher)
        val title = findViewById<TextView>(R.id.tvTitleAddEdit)

        originalUsername = intent.getStringExtra(EXTRA_TEACHER_USERNAME).orEmpty()
        val editing = originalUsername.takeIf { it.isNotBlank() }?.let(AppDatabase::getTeacherByUsername)
        if (editing != null) {
            title.text = "ویرایش اطلاعات استاد"
            first.setText(editing.firstName); last.setText(editing.lastName)
            phone.setText("0${editing.phone.removePrefix("0")}"); national.setText(editing.nationalId)
            save.text = "ذخیره تغییرات"
        }

        save.setOnClickListener {
            first.error=null; last.error=null; phone.error=null; national.error=null
            val f=first.text?.toString()?.trim().orEmpty(); val l=last.text?.toString()?.trim().orEmpty()
            val p=phone.text?.toString()?.replace(" ","")?.trim().orEmpty(); val n=national.text?.toString()?.trim().orEmpty()
            when {
                f.isBlank() -> { first.error="نام را وارد کنید"; first.requestFocus() }
                l.isBlank() -> { last.error="نام خانوادگی را وارد کنید"; last.requestFocus() }
                p.length !in 10..11 || !p.all(Char::isDigit) -> { phone.error="شماره تماس معتبر نیست"; phone.requestFocus() }
                n.length != 10 || !n.all(Char::isDigit) -> { national.error="کد ملی باید ۱۰ رقم باشد"; national.requestFocus() }
                else -> {
                    val normalized=p.removePrefix("0")
                    val model=TeacherModel(
                        id=editing?.id?:UUID.randomUUID().toString(), firstName=f,lastName=l,phone=normalized,
                        nationalId=n,password=if(editing==null)n else "",isActive=editing?.isActive?:true,classIds=editing?.classIds.orEmpty(),
                        avatarName=editing?.avatarName?:"avatar_teacher_${(1..6).random()}"
                    )
                    setSaving(save,progress,true)
                    RetrofitClient.instance.addTeacher(model).enqueue(object:Callback<ApiResponse>{
                        override fun onResponse(call:Call<ApiResponse>,response:Response<ApiResponse>){
                            setSaving(save,progress,false);val body=response.body()
                            if(response.isSuccessful&&body?.status=="success"){
                                AppDatabase.upsertTeacher(model,originalPhone=originalUsername.takeIf{it.isNotBlank()})
                                Toast.makeText(this@AddEditTeacherActivity,body.message,Toast.LENGTH_SHORT).show();finish()
                            } else Toast.makeText(this@AddEditTeacherActivity,body?.message?:"ثبت اطلاعات انجام نشد",Toast.LENGTH_LONG).show()
                        }
                        override fun onFailure(call:Call<ApiResponse>,t:Throwable){setSaving(save,progress,false);Toast.makeText(this@AddEditTeacherActivity,"خطا در اتصال به سرور",Toast.LENGTH_LONG).show()}
                    })
                }
            }
        }
    }

    private fun setSaving(button:Button,progress:View,saving:Boolean){button.isEnabled=!saving;button.alpha=if(saving).55f else 1f;progress.visibility=if(saving)View.VISIBLE else View.GONE}
    companion object { const val EXTRA_TEACHER_USERNAME="TEACHER_USERNAME" }
}
