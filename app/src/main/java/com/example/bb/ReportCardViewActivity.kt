package com.example.bb

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.IOException
import java.util.Locale
import kotlin.math.min

class ReportCardViewActivity : AppCompatActivity() {

    private lateinit var scoreContainer: LinearLayout
    private lateinit var progress: View
    private lateinit var printableArea: View
    private lateinit var pdfButton: MaterialButton
    private var currentCard: ReportCardDto? = null

    private val createPdfLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        if (uri != null) {
            writePdf(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report_card_view)
        SystemBarInsets.apply(this, findViewById(R.id.rootReportCardView))

        findViewById<ImageView>(R.id.btnReportBack).setOnClickListener { finish() }
        scoreContainer = findViewById(R.id.containerReportScores)
        progress = findViewById(R.id.progressReportView)
        printableArea = findViewById(R.id.reportPrintableArea)
        pdfButton = findViewById(R.id.btnSaveReportPdf)
        pdfButton.isEnabled = false
        pdfButton.alpha = 0.55f
        pdfButton.setOnClickListener {
            val card = currentCard ?: return@setOnClickListener
            createPdfLauncher.launch(buildPdfFileName(card))
        }

        val reportCardId = intent.getStringExtra(EXTRA_REPORT_CARD_ID).orEmpty()
        if (reportCardId.isBlank()) {
            toast("شناسه کارنامه موجود نیست")
            finish()
            return
        }

        load(reportCardId)
    }

    private fun load(id: String) {
        progress.visibility = View.VISIBLE
        pdfButton.isEnabled = false

        RetrofitClient.instance.getReportCard(id)
            .enqueue(object : Callback<ReportCardResponse> {
                override fun onResponse(
                    call: Call<ReportCardResponse>,
                    response: Response<ReportCardResponse>
                ) {
                    progress.visibility = View.GONE
                    val card = response.body()?.card
                    val apiError = ApiErrorParser.parse(response)

                    if (response.isSuccessful && card != null) {
                        currentCard = card
                        bind(card)
                        printableArea.post {
                            pdfButton.isEnabled = true
                            pdfButton.alpha = 1f
                        }
                    } else {
                        toast(response.body()?.message ?: apiError?.message ?: "کارنامه دریافت نشد")
                        finish()
                    }
                }

                override fun onFailure(
                    call: Call<ReportCardResponse>,
                    t: Throwable
                ) {
                    progress.visibility = View.GONE
                    toast("خطا در ارتباط با سرور")
                }
            })
    }

    private fun bind(card: ReportCardDto) {
        val manualStudentId = card.studentCode.ifBlank {
            card.studentId.take(8).ifBlank { "—" }
        }
        val term = listOf(card.termSeason, card.termYear)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { "—" }

        findViewById<TextView>(R.id.txtStudentName).text =
            card.studentName.ifBlank { "—" }
        findViewById<TextView>(R.id.txtStudentId).text = manualStudentId
        findViewById<TextView>(R.id.txtTerm).text = term
        findViewById<TextView>(R.id.txtClassCode).text =
            card.classCode.ifBlank { "—" }
        findViewById<TextView>(R.id.txtClassName).text =
            card.className.ifBlank { "—" }
        findViewById<TextView>(R.id.txtClassLevel).text =
            card.classLevel.ifBlank { "—" }
        findViewById<TextView>(R.id.txtBook).text =
            card.bookName.ifBlank { "—" }
        findViewById<TextView>(R.id.txtReportDate).text =
            "Date: ${formatPublishedDate(card.publishedAt)}"
        findViewById<TextView>(R.id.txtReportMessage).text =
            card.resultMessage.trim().ifBlank {
                defaultMessageFor(card.resultCode)
            }

        renderScoreTable(card)
        renderResultSummary(card)
    }

    private fun renderScoreTable(card: ReportCardDto) {
        scoreContainer.removeAllViews()
        val inflater = LayoutInflater.from(this)
        val rowHeightDp = when {
            card.scores.size <= 4 -> 30
            card.scores.size <= 6 -> 27
            else -> 24
        }

        addScoreRow(
            inflater, "Subject", "Score", "Out of",
            Color.parseColor("#2B4E78"), Color.WHITE, Color.WHITE, Color.WHITE,
            isHeader = true,
            heightDp = rowHeightDp
        )

        card.scores.sortedBy { it.sortOrder }.forEachIndexed { index, item ->
            addScoreRow(
                inflater,
                item.title.ifBlank { "—" },
                format(item.score),
                format(item.maxScore),
                Color.parseColor(if (index % 2 == 0) "#FFFFFF" else "#F5F8FD"),
                Color.parseColor("#111827"),
                Color.parseColor("#111827"),
                Color.parseColor("#475467"),
                heightDp = rowHeightDp
            )
        }

        scoreContainer.addView(
            View(this),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        val totalOutOf = card.scores.sumOf { it.maxScore }
            .takeIf { it > 0.0 } ?: 100.0
        addScoreRow(
            inflater, "TOTAL", format(card.totalScore), format(totalOutOf),
            Color.parseColor("#FFF1E8"),
            Color.parseColor("#2B4E78"),
            Color.parseColor("#FF6E14"),
            Color.parseColor("#2B4E78"),
            isHeader = true,
            heightDp = rowHeightDp
        )
    }

    private fun addScoreRow(
        inflater: LayoutInflater,
        subject: String,
        score: String,
        outOf: String,
        backgroundColor: Int,
        subjectColor: Int,
        scoreColor: Int,
        outOfColor: Int,
        isHeader: Boolean = false,
        heightDp: Int = 24
    ) {
        val row = inflater.inflate(
            R.layout.item_report_card_table_row,
            scoreContainer,
            false
        )
        row.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(heightDp)
        )
        row.setBackgroundColor(backgroundColor)
        row.findViewById<TextView>(R.id.txtTableSubject).apply {
            text = subject
            setTextColor(subjectColor)
        }
        row.findViewById<TextView>(R.id.txtTableScore).apply {
            text = score
            setTextColor(scoreColor)
        }
        row.findViewById<TextView>(R.id.txtTableOutOf).apply {
            text = outOf
            setTextColor(outOfColor)
        }
        scoreContainer.addView(row)
    }

    private fun renderResultSummary(card: ReportCardDto) {
        val totalOutOf = card.scores.sumOf { it.maxScore }
            .takeIf { it > 0.0 } ?: 100.0
        findViewById<TextView>(R.id.txtTotalScore).text =
            "${format(card.totalScore)} / ${format(totalOutOf)}"

        val presentation = statusPresentation(card.resultCode)
        findViewById<TextView>(R.id.txtResultStatus).apply {
            text = presentation.label
            setTextColor(presentation.textColor)
            background = roundedBackground(
                presentation.backgroundColor,
                presentation.borderColor,
                12f
            )
        }

        val starCount = resolvedStarCount(card)
        findViewById<TextView>(R.id.txtStars).apply {
            text = buildString {
                repeat(starCount) { append('★') }
                repeat(5 - starCount) { append('☆') }
            }
            setTextColor(
                if (starCount > 0) Color.parseColor("#FF6E14")
                else Color.parseColor("#98A2B3")
            )
        }
    }

    private fun writePdf(uri: Uri) {
        if (printableArea.width <= 0 || printableArea.height <= 0) {
            toast("پیش‌نمایش کارنامه هنوز آماده نشده است")
            return
        }

        progress.visibility = View.VISIBLE
        pdfButton.isEnabled = false

        val document = PdfDocument()
        try {
            val pageWidth = 595
            val pageHeight = 842
            val margin = 18f
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas
            canvas.drawColor(Color.WHITE)

            val availableWidth = pageWidth - (2f * margin)
            val availableHeight = pageHeight - (2f * margin)
            val scale = min(
                availableWidth / printableArea.width.toFloat(),
                availableHeight / printableArea.height.toFloat()
            )
            val drawnWidth = printableArea.width * scale
            val drawnHeight = printableArea.height * scale
            val left = (pageWidth - drawnWidth) / 2f
            val top = (pageHeight - drawnHeight) / 2f

            canvas.save()
            canvas.translate(left, top)
            canvas.scale(scale, scale)
            printableArea.draw(canvas)
            canvas.restore()
            document.finishPage(page)

            contentResolver.openOutputStream(uri, "w")?.use { stream ->
                document.writeTo(stream)
            } ?: throw IOException("خروجی فایل باز نشد")

            toast("فایل PDF با موفقیت ذخیره شد")
        } catch (error: Exception) {
            toast("ذخیره PDF انجام نشد: ${error.message ?: "خطای نامشخص"}")
        } finally {
            document.close()
            progress.visibility = View.GONE
            pdfButton.isEnabled = currentCard != null
        }
    }

    private fun buildPdfFileName(card: ReportCardDto): String {
        val term = listOf(card.termSeason, card.termYear)
            .filter { it.isNotBlank() }
            .joinToString("_")
        val identity = card.studentCode.ifBlank { card.studentName }
        val raw = listOf("ReportCard", identity, term)
            .filter { it.isNotBlank() }
            .joinToString("_")
        return sanitizeFilePart(raw).ifBlank { "ReportCard" } + ".pdf"
    }

    private fun sanitizeFilePart(value: String): String = value
        .replace(Regex("[\\\\/:*?\"<>|]+"), "_")
        .replace(Regex("\\s+"), "_")
        .trim('_', '.')
        .take(100)

    private fun resolvedStarCount(card: ReportCardDto): Int {
        if (card.starCount in 1..5) return card.starCount
        return when (card.resultCode) {
            "FIVE_STAR" -> 5
            "FOUR_STAR" -> 4
            "THREE_STAR" -> 3
            "TWO_STAR" -> 2
            "ONE_STAR" -> 1
            else -> 0
        }
    }

    private fun statusPresentation(code: String): StatusPresentation = when (code) {
        "FIVE_STAR", "FOUR_STAR", "THREE_STAR", "TWO_STAR", "ONE_STAR", "PASS_NO_STAR" ->
            StatusPresentation("PASS", Color.parseColor("#067647"), Color.parseColor("#ECFDF3"), Color.parseColor("#ABEFC6"))
        "CONDITIONAL" ->
            StatusPresentation("CONDITIONAL", Color.parseColor("#B54708"), Color.parseColor("#FFFAEB"), Color.parseColor("#FEDF89"))
        "FAILED" ->
            StatusPresentation("FAIL", Color.parseColor("#B42318"), Color.parseColor("#FEF3F2"), Color.parseColor("#FECDCA"))
        "INCOMPLETE" ->
            StatusPresentation("INCOMPLETE", Color.parseColor("#475467"), Color.parseColor("#F2F4F7"), Color.parseColor("#D0D5DD"))
        else ->
            StatusPresentation("UNKNOWN", Color.parseColor("#475467"), Color.parseColor("#F2F4F7"), Color.parseColor("#D0D5DD"))
    }

    private fun defaultMessageFor(code: String): String =
        DEFAULT_RESULT_MESSAGES[code] ?: "This report card is not complete yet."

    private fun roundedBackground(
        fillColor: Int,
        strokeColor: Int,
        radiusDp: Float
    ): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radiusDp * resources.displayMetrics.density
        setColor(fillColor)
        setStroke(dp(1), strokeColor)
    }

    private fun formatPublishedDate(value: String?): String {
        val normalized = value.orEmpty().trim()
        if (normalized.isBlank()) return "—"
        return normalized.substringBefore('T').substringBefore(' ').ifBlank { "—" }
    }

    private fun format(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString()
        else String.format(Locale.US, "%.2f", value)

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private data class StatusPresentation(
        val label: String,
        val textColor: Int,
        val backgroundColor: Int,
        val borderColor: Int
    )

    companion object {
        const val EXTRA_REPORT_CARD_ID = "REPORT_CARD_ID"

        val DEFAULT_RESULT_MESSAGES: Map<String, String> = linkedMapOf(
            "FIVE_STAR" to "FABULOUS! Perfect score! This is to certify that you have participated in the term and level above. Your success in learning a new language is our strong desire.",
            "FOUR_STAR" to "GREAT WORK! This is to certify that you have participated in the term and level above. Your success in learning a new language is our strong desire.",
            "THREE_STAR" to "VERY GOOD! This is to certify that you have participated in the term and level above. Your success in learning a new language is our strong desire.",
            "TWO_STAR" to "KEEP IT UP! This is to certify that you have participated in the term and level above. Your success in learning a new language is our strong desire.",
            "ONE_STAR" to "OK! This is to certify that you have participated in the term and level above. Your success in learning a new language is our strong desire.",
            "PASS_NO_STAR" to "This is to certify that you have participated in the term and level above. Your success in learning a new language is our strong desire.",
            "CONDITIONAL" to "Your score is incomplete. You need to either repeat the above level or ask for the supervisor's permission to see if you can go to a higher level.",
            "FAILED" to "So Sorry. It looks like you have to repeat the term and level above. Never mind, try again. Don't worry: ask for help, you deserve more."
        )
    }
}
