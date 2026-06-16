package com.example.bb

import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream

class ReportCardViewActivity : AppCompatActivity() {

    private lateinit var studentId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report_card_view)

        // ۱. اتصال ویوهای اصلی
        findViewById<ImageView>(R.id.btnViewBack).setOnClickListener { finish() }

        val txtReportID = findViewById<TextView>(R.id.txtReportID)
        val txtReportFirstName = findViewById<TextView>(R.id.txtReportFirstName)
        val txtReportLastName = findViewById<TextView>(R.id.txtReportLastName)
        val txtReportTotalScore = findViewById<TextView>(R.id.txtReportTotalScore)
        val rowsContainer = findViewById<LinearLayout>(R.id.rowsContainer)
        val btnDownloadPdf = findViewById<Button>(R.id.btnDownloadPdf)
        val reportCardContainer = findViewById<LinearLayout>(R.id.reportCardContainer)

        // ۲. دریافت دیتا
        studentId = intent.getStringExtra("STUDENT_ID") ?: "0000"
        val fullName = intent.getStringExtra("STUDENT_NAME") ?: "Unknown"
        val criteriaNames = intent.getStringArrayListExtra("CRITERIA_NAMES") ?: arrayListOf()
        val scoresList = intent.getIntegerArrayListExtra("SCORES_LIST") ?: arrayListOf()
        val maxScoresList = intent.getIntegerArrayListExtra("MAX_SCORES_LIST") ?: arrayListOf()

        // ۳. پر کردن اطلاعات دانش‌آموز
        val nameParts = fullName.split(" ")
        txtReportFirstName.text = nameParts.firstOrNull() ?: fullName
        txtReportLastName.text = if (nameParts.size > 1) nameParts.drop(1).joinToString(" ") else ""
        txtReportID.text = studentId

        // ۴. پر کردن داینامیک جدول نمرات
        rowsContainer.removeAllViews()
        val inflater = LayoutInflater.from(this)
        var totalStudentScore = 0.0

        for (i in criteriaNames.indices) {
            val rowView = inflater.inflate(R.layout.item_report_card_table_row, rowsContainer, false)
            rowView.findViewById<TextView>(R.id.txtTableSubject).text = criteriaNames[i]
            rowView.findViewById<TextView>(R.id.txtTableScore).text = scoresList[i].toString()
            rowView.findViewById<TextView>(R.id.txtTableOutOf).text = maxScoresList[i].toString()

            totalStudentScore += scoresList[i]
            rowsContainer.addView(rowView)
        }
        txtReportTotalScore.text = totalStudentScore.toString()

        // ۵. لاجیک PDF
        btnDownloadPdf.setOnClickListener {
            createPdfFromView(reportCardContainer)
        }
    }

    private fun createPdfFromView(view: LinearLayout) {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(view.width, view.height, 1).create()
        val page = pdfDocument.startPage(pageInfo)

        val canvas: Canvas = page.canvas
        view.draw(canvas)

        pdfDocument.finishPage(page)

        // ذخیره در مسیر فایل‌های اپلیکیشن
        val file = File(getExternalFilesDir(null), "ReportCard_${studentId}.pdf")
        try {
            pdfDocument.writeTo(FileOutputStream(file))
            Toast.makeText(this, "فایل PDF ساخته شد:\n${file.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "خطا در ساخت PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        }
        pdfDocument.close()
    }
}