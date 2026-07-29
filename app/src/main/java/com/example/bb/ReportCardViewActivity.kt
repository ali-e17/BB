package com.example.bb

import android.app.Activity
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
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
import java.io.OutputStream

class ReportCardViewActivity : AppCompatActivity() {
    private lateinit var rootCard: View
    private lateinit var scoreContainer: LinearLayout
    private lateinit var progress: View
    private var card: ReportCardDto? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report_card_view)
        findViewById<ImageView>(R.id.btnReportBack).setOnClickListener { finish() }
        rootCard = findViewById(R.id.reportPrintableArea)
        scoreContainer = findViewById(R.id.containerReportScores)
        progress = findViewById(R.id.progressReportView)
        findViewById<MaterialButton>(R.id.btnSaveReportPdf).setOnClickListener { choosePdfDestination() }
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
                if (response.isSuccessful && response.body()?.status == "success" && c != null) { card = c; bind(c) }
                else { Toast.makeText(this@ReportCardViewActivity, response.body()?.message ?: "کارنامه دریافت نشد", Toast.LENGTH_LONG).show(); finish() }
            }
            override fun onFailure(call: Call<ReportCardResponse>, t: Throwable) { progress.visibility = View.GONE; Toast.makeText(this@ReportCardViewActivity, "ارتباط با سرور برقرار نشد", Toast.LENGTH_LONG).show() }
        })
    }

    private fun bind(c: ReportCardDto) {
        findViewById<TextView>(R.id.txtReportStudentName).text = c.studentName
        findViewById<TextView>(R.id.txtReportStudentCode).text = "کد دانش‌آموزی: ${c.studentCode.ifBlank { "—" }}"
        findViewById<TextView>(R.id.txtReportClassName).text = c.className
        findViewById<TextView>(R.id.txtReportClassMeta).text = listOfNotNull(
            c.classCode.takeIf(String::isNotBlank)?.let { "کد کلاس: $it" },
            c.bookName.takeIf(String::isNotBlank)?.let { "کتاب: $it" },
            c.classLevel.takeIf(String::isNotBlank)?.let { "سطح: $it" },
            listOf(c.termSeason, c.termYear).filter(String::isNotBlank).joinToString(" ").takeIf(String::isNotBlank)?.let { "ترم: $it" }
        ).joinToString("  •  ")
        findViewById<TextView>(R.id.txtReportTotal).text = "نمره کل: ${format(c.totalScore)} از ۱۰۰"
        findViewById<TextView>(R.id.txtReportStars).text = if (c.starCount > 0) "★".repeat(c.starCount) else "بدون ستاره"
        findViewById<TextView>(R.id.txtReportStatus).text = resultLabel(c.resultCode)
        findViewById<TextView>(R.id.txtReportMessage).text = c.resultMessage.ifBlank { "متن نتیجه هنوز تنظیم نشده است." }
        findViewById<TextView>(R.id.txtReportPublishedAt).text = c.publishedAt?.let { "تاریخ انتشار: $it" } ?: "پیش‌نویس"
        scoreContainer.removeAllViews()
        c.scores.forEach { score ->
            val row = LayoutInflater.from(this).inflate(R.layout.item_report_score_row, scoreContainer, false)
            row.findViewById<TextView>(R.id.txtReportScoreTitle).text = score.title
            row.findViewById<TextView>(R.id.txtReportScoreValue).text = "${format(score.score)} / ${format(score.maxScore)}"
            scoreContainer.addView(row)
        }
    }

    private fun choosePdfDestination() {
        val c = card ?: return
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "application/pdf"
            putExtra(Intent.EXTRA_TITLE, "report_${c.studentCode.ifBlank { c.studentId }}_${c.classCode.ifBlank { c.classId }}.pdf")
        }, REQUEST_PDF)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PDF && resultCode == Activity.RESULT_OK) data?.data?.let(::writePdf)
    }

    private fun writePdf(uri: Uri) {
        val c = card ?: return
        runCatching {
            contentResolver.openOutputStream(uri)?.use { out -> renderA4Pdf(c, out) } ?: error("output")
        }.onSuccess { Toast.makeText(this, "PDF ذخیره شد", Toast.LENGTH_LONG).show() }
            .onFailure { Toast.makeText(this, "ذخیره PDF انجام نشد", Toast.LENGTH_LONG).show() }
    }

    private fun renderA4Pdf(c: ReportCardDto, out: OutputStream) {
        val pdf = PdfDocument(); val pageWidth = 595; val pageHeight = 842
        val page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create())
        val canvas = page.canvas; canvas.drawColor(Color.WHITE)
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(16,64,120); textSize = 22f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
        val heading = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(25,35,50); textSize = 13f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.RIGHT }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(45,55,70); textSize = 11f; textAlign = Paint.Align.RIGHT }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(220,225,232); strokeWidth = 1f }
        canvas.drawText("کارنامه آموزشگاه زبان", pageWidth/2f, 55f, title)
        var y = 92f
        fun info(label:String, value:String) { canvas.drawText("$label: $value", 545f, y, body); y += 22f }
        info("نام زبان‌آموز", c.studentName); info("کد زبان‌آموز", c.studentCode.ifBlank { "—" }); info("کلاس", c.className); info("کد کلاس", c.classCode.ifBlank { "—" }); info("کتاب", c.bookName.ifBlank { "—" }); info("سطح", c.classLevel.ifBlank { "—" }); info("ترم", listOf(c.termSeason,c.termYear).filter(String::isNotBlank).joinToString(" ").ifBlank { "—" })
        y += 5f; canvas.drawLine(50f,y,545f,y,line); y += 25f
        canvas.drawText("معیار", 430f, y, heading); canvas.drawText("نمره", 245f, y, heading); canvas.drawText("بارم", 105f, y, heading); y += 15f
        c.scores.forEach { s ->
            canvas.drawLine(50f,y,545f,y,line); y += 20f
            canvas.drawText(shorten(s.title, 34), 430f, y, body); canvas.drawText(format(s.score), 245f, y, body); canvas.drawText(format(s.maxScore), 105f, y, body); y += 10f
        }
        y += 15f; canvas.drawText("نمره کل: ${format(c.totalScore)}", 545f, y, heading); y += 25f
        canvas.drawText("وضعیت: ${resultLabel(c.resultCode)}", 545f, y, heading); y += 25f
        canvas.drawText("ستاره: ${if(c.starCount>0) c.starCount.toString() else "بدون ستاره"}",545f,y,heading); y += 28f
        drawWrappedRtl(canvas, c.resultMessage, 545f, y, 480f, body, 17f)
        pdf.finishPage(page); pdf.writeTo(out); pdf.close()
    }

    private fun drawWrappedRtl(canvas: Canvas, text: String, x: Float, startY: Float, maxWidth: Float, paint: Paint, lineHeight: Float) {
        var y = startY; val words = text.split(Regex("\\s+")); var line = ""
        words.forEach { word ->
            val test = if (line.isBlank()) word else "$line $word"
            if (paint.measureText(test) > maxWidth && line.isNotBlank()) { canvas.drawText(line, x, y, paint); y += lineHeight; line = word }
            else line = test
        }
        if (line.isNotBlank()) canvas.drawText(line, x, y, paint)
    }

    private fun resultLabel(code: String) = when(code) { "FIVE_STAR"->"پنج ستاره"; "FOUR_STAR"->"چهار ستاره"; "THREE_STAR"->"سه ستاره"; "TWO_STAR"->"دو ستاره"; "ONE_STAR"->"یک ستاره"; "PASS_NO_STAR"->"قبول بدون ستاره"; "CONDITIONAL"->"مشروط"; "FAILED"->"مردود"; "INCOMPLETE"->"ناقص"; else->code }
    private fun format(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else "%.2f".format(v)
    private fun shorten(s:String,max:Int)=if(s.length<=max)s else s.take(max-1)+"…"

    companion object { const val EXTRA_REPORT_CARD_ID = "REPORT_CARD_ID"; private const val REQUEST_PDF = 5401 }
}
