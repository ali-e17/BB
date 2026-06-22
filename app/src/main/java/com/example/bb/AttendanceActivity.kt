package com.example.bb

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class AttendanceActivity : AppCompatActivity() {

    private lateinit var rvAttendance: RecyclerView
    private lateinit var txtLiveStats: TextView
    private lateinit var containerDates: LinearLayout
    private lateinit var adapter: AttendanceAdapter
    private var records = mutableListOf<AttendanceRecord>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_attendance)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        rvAttendance = findViewById(R.id.rvAttendance)
        txtLiveStats = findViewById(R.id.txtLiveStats)
        containerDates = findViewById(R.id.containerDates)
        val spinnerClass = findViewById<AutoCompleteTextView>(R.id.spinnerClass)
        val btnSave = findViewById<Button>(R.id.btnSaveAttendance)

        // تنظیم دراپ‌داون کلاس‌ها
        val classes = arrayOf("کلاس FF1 (گرامر)", "کلاس FF2 (مکالمه)")
        spinnerClass.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, classes))
        spinnerClass.setText(classes[0], false)

        setupMockDates()
        generateMockStudents()

        rvAttendance.layoutManager = LinearLayoutManager(this)
        adapter = AttendanceAdapter(records) {
            updateLiveStats()
        }
        rvAttendance.adapter = adapter

        updateLiveStats()

        btnSave.setOnClickListener {
            Toast.makeText(this, "اطلاعات با موفقیت در سرور ثبت شد", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun updateLiveStats() {
        val present = records.count { it.status == AttendanceStatus.PRESENT }
        val late = records.count { it.status == AttendanceStatus.LATE }
        val absent = records.count { it.status == AttendanceStatus.ABSENT }

        txtLiveStats.text = "حاضر: $present | غایب: $absent | تأخیر: $late"
    }

    private fun setupMockDates() {
        val dates = listOf("۵ شهریور (امروز)", "۳ شهریور", "۱ شهریور", "۲۸ مرداد")

        dates.forEachIndexed { index, dateText ->
            val tvDate = TextView(this).apply {
                text = dateText
                textSize = 14f
                setPadding(40, 20, 40, 20)

                val marginParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                marginParams.setMargins(8, 0, 8, 0)
                layoutParams = marginParams

                val bg = GradientDrawable().apply {
                    cornerRadius = 30f
                    // تاریخ امروز نارنجی بشه، بقیه خاکستری
                    setColor(if (index == 0) Color.parseColor("#FF6E14") else Color.parseColor("#E5E7EB"))
                }
                background = bg
                setTextColor(if (index == 0) Color.WHITE else Color.parseColor("#1D2939"))

                setOnClickListener {
                    Toast.makeText(this@AttendanceActivity, "نمایش اطلاعات: $dateText", Toast.LENGTH_SHORT).show()
                }
            }
            containerDates.addView(tvDate)
        }
    }

    private fun generateMockStudents() {
        val s1 = Student("1", "کوثر", "شریفی", "2601", "FF1", "", "", true, R.drawable.avatar_student_1)
        val s2 = Student("2", "ریحانه", "محمدی", "2602", "FF1", "", "", true, R.drawable.avatar_student_2)
        val s3 = Student("3", "نازنین", "بابلخانی", "2603", "FF1", "", "", true, R.drawable.avatar_student_3)
        val s4 = Student("4", "علی", "رضایی", "2580", "FF1", "", "", true, R.drawable.avatar_student_4)

        records.add(AttendanceRecord(s1, AttendanceStatus.ABSENT))
        records.add(AttendanceRecord(s2, AttendanceStatus.ABSENT))
        records.add(AttendanceRecord(s3, AttendanceStatus.ABSENT))
        records.add(AttendanceRecord(s4, AttendanceStatus.ABSENT))
    }
}