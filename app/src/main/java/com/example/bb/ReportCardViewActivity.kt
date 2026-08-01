package com.example.bb

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ReportCardViewActivity : AppCompatActivity() {
    private lateinit var scoreContainer: LinearLayout
    private lateinit var progress: View
    private var card: ReportCardDto? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report_card_view)

        findViewById<ImageView>(R.id.btnReportBack).setOnClickListener { finish() }
        scoreContainer = findViewById(R.id.containerReportScores)
        progress = findViewById(R.id.progressReportView)
        findViewById<MaterialButton>(R.id.btnSaveReportPdf).setOnClickListener {
            Toast.makeText(this, "بخش PDF در آپدیت بعدی هماهنگ می‌شود", Toast.LENGTH_SHORT).show()
        }

        val id = intent.getStringExtra(EXTRA_REPORT_CARD_ID).orEmpty()
        if (id.isBlank()) { Toast.makeText(this, "شناسه کارنامه موجود نیست", Toast.LENGTH_LONG).show(); finish() }
        else load(id)
    }

    private fun load(id: String) {
        progress.visibility = View.VISIBLE
        RetrofitClient.instance.getReportCard(id).enqueue(object : Callback<ReportCardResponse> {
            override fun onResponse(call: Call<ReportCardResponse>, response: Response<ReportCardResponse>) {
                progress.visibility = View.GONE
                val c = response.body()?.card
                if (response.isSuccessful && c != null) { card = c; bind(c) }
                else { Toast.makeText(this@ReportCardViewActivity, "کارنامه دریافت نشد", Toast.LENGTH_LONG).show(); finish() }
            }
            override fun onFailure(call: Call<ReportCardResponse>, t: Throwable) {
                progress.visibility = View.GONE; Toast.makeText(this@ReportCardViewActivity, "خطای ارتباط", Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun bind(c: ReportCardDto) {
        // پر کردن اطلاعات هدر (Safe Call برای جلوگیری از کرش)
        findViewById<TextView>(R.id.txtStudentId)?.text = "Student ID: ${c.studentCode.ifBlank { c.studentId.take(5) }}"
        findViewById<TextView>(R.id.txtStudentName)?.text = "Name: ${c.studentName}"

        findViewById<TextView>(R.id.txtTerm)?.text = listOfNotNull(c.termSeason, c.termYear).filter { it.isNotBlank() }.joinToString("-").ifBlank { "—" }
        findViewById<TextView>(R.id.txtClassCode)?.text = c.classCode.ifBlank { "—" }
        findViewById<TextView>(R.id.txtBook)?.text = c.bookName.ifBlank { "—" }

        val dateText = c.publishedAt?.split(" ")?.firstOrNull() ?: "Draft"
        findViewById<TextView>(R.id.txtReportDate)?.text = "Date: $dateText"
        findViewById<TextView>(R.id.txtReportMessage)?.text = c.resultMessage.ifBlank { "VERY GOOD! This is to certify that you have participated in the term and level above." }

        // آماده سازی جدول
        scoreContainer.removeAllViews()
        val inflater = LayoutInflater.from(this)

        // 1. ساخت هدر جدول
        val headerRow = inflater.inflate(R.layout.item_report_card_table_row, scoreContainer, false)
        headerRow.setBackgroundColor(Color.parseColor("#EBF5FF")) // آبی خیلی روشن
        headerRow.findViewById<TextView>(R.id.txtTableSubject).apply { text = "Subject"; setTextColor(Color.parseColor("#1E3A8A")) }
        headerRow.findViewById<TextView>(R.id.txtTableScore).apply { text = "Score"; setTextColor(Color.parseColor("#1E3A8A")) }
        headerRow.findViewById<TextView>(R.id.txtTableOutOf).apply { text = "Out of"; setTextColor(Color.parseColor("#1E3A8A")) }
        scoreContainer.addView(headerRow)

        // 2. ساخت ردیف‌های نمرات
        c.scores.forEach { score ->
            addDivider()
            val row = inflater.inflate(R.layout.item_report_card_table_row, scoreContainer, false)
            row.findViewById<TextView>(R.id.txtTableSubject)?.text = score.title
            row.findViewById<TextView>(R.id.txtTableScore)?.text = format(score.score)
            row.findViewById<TextView>(R.id.txtTableOutOf)?.text = format(score.maxScore)
            scoreContainer.addView(row)
        }

        // 3. ردیف Total
        addDivider()
        val rowTotal = inflater.inflate(R.layout.item_report_card_table_row, scoreContainer, false)
        rowTotal.setBackgroundColor(Color.parseColor("#F9FAFB"))
        rowTotal.findViewById<TextView>(R.id.txtTableSubject)?.apply { text = "Total:"; setTextColor(Color.parseColor("#1E3A8A")) }
        rowTotal.findViewById<TextView>(R.id.txtTableScore)?.text = format(c.totalScore)
        rowTotal.findViewById<TextView>(R.id.txtTableOutOf)?.text = "100"
        scoreContainer.addView(rowTotal)

        // 4. ردیف Status
        addDivider()
        val rowStatus = inflater.inflate(R.layout.item_report_card_table_row, scoreContainer, false)
        rowStatus.findViewById<TextView>(R.id.txtTableSubject)?.text = "Status:"
        val statusLabel = resultLabelEn(c.resultCode)
        rowStatus.findViewById<TextView>(R.id.txtTableScore)?.text = statusLabel
        rowStatus.findViewById<TextView>(R.id.txtTableOutOf)?.text = "" // خالی
        scoreContainer.addView(rowStatus)

        // 5. ردیف Rank (Stars)
        addDivider()
        val rowRank = inflater.inflate(R.layout.item_report_card_table_row, scoreContainer, false)
        rowRank.setBackgroundColor(Color.parseColor("#FEFCE8")) // زرد خیلی روشن
        rowRank.findViewById<TextView>(R.id.txtTableSubject)?.text = "Rank:"

        val stars = when {
            c.totalScore >= 100.0 -> 5
            c.totalScore >= 95.0 -> 4
            c.totalScore >= 90.0 -> 3
            c.totalScore >= 87.0 -> 2
            c.totalScore >= 83.0 -> 1
            else -> 0
        }
        val starText = if (stars > 0) "★".repeat(stars) else "Failed"
        rowRank.findViewById<TextView>(R.id.txtTableScore)?.apply { text = starText; setTextColor(Color.parseColor("#D97706")) }
        rowRank.findViewById<TextView>(R.id.txtTableOutOf)?.text = ""
        scoreContainer.addView(rowRank)
    }

    private fun addDivider() {
        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2)
            setBackgroundColor(Color.parseColor("#E5E7EB"))
        }
        scoreContainer.addView(divider)
    }

    private fun resultLabelEn(code: String) = when(code) { "FIVE_STAR"->"Pass"; "FOUR_STAR"->"Pass"; "THREE_STAR"->"Pass"; "TWO_STAR"->"Pass"; "ONE_STAR"->"Pass"; "PASS_NO_STAR"->"Pass"; "CONDITIONAL"->"Cond."; "FAILED"->"Fail"; "INCOMPLETE"->"Inc."; else->"Unknown" }
    private fun format(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else "%.2f".format(v)

    companion object { const val EXTRA_REPORT_CARD_ID = "REPORT_CARD_ID"; private const val REQUEST_PDF = 5401 }
}