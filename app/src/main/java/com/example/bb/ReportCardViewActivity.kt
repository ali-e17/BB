package com.example.bb

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
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
import java.util.Locale

class ReportCardViewActivity : AppCompatActivity() {

    private lateinit var scoreContainer: LinearLayout
    private lateinit var progress: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report_card_view)

        findViewById<ImageView>(R.id.btnReportBack).setOnClickListener { finish() }
        scoreContainer = findViewById(R.id.containerReportScores)
        progress = findViewById(R.id.progressReportView)

        findViewById<MaterialButton>(R.id.btnSaveReportPdf).setOnClickListener {
            Toast.makeText(
                this,
                "بخش PDF در آپدیت بعدی هماهنگ می‌شود",
                Toast.LENGTH_SHORT
            ).show()
        }

        val reportCardId = intent.getStringExtra(EXTRA_REPORT_CARD_ID).orEmpty()
        if (reportCardId.isBlank()) {
            Toast.makeText(this, "شناسه کارنامه موجود نیست", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        load(reportCardId)
    }

    private fun load(id: String) {
        progress.visibility = View.VISIBLE

        RetrofitClient.instance.getReportCard(id)
            .enqueue(object : Callback<ReportCardResponse> {
                override fun onResponse(
                    call: Call<ReportCardResponse>,
                    response: Response<ReportCardResponse>
                ) {
                    progress.visibility = View.GONE
                    val card = response.body()?.card

                    if (response.isSuccessful && card != null) {
                        bind(card)
                    } else {
                        Toast.makeText(
                            this@ReportCardViewActivity,
                            response.body()?.message ?: "کارنامه دریافت نشد",
                            Toast.LENGTH_LONG
                        ).show()
                        finish()
                    }
                }

                override fun onFailure(
                    call: Call<ReportCardResponse>,
                    t: Throwable
                ) {
                    progress.visibility = View.GONE
                    Toast.makeText(
                        this@ReportCardViewActivity,
                        "خطا در ارتباط با سرور",
                        Toast.LENGTH_LONG
                    ).show()
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

        addScoreRow(
            inflater = inflater,
            subject = "Subject",
            score = "Score",
            outOf = "Out of",
            backgroundColor = Color.parseColor("#2B4E78"),
            subjectColor = Color.WHITE,
            scoreColor = Color.WHITE,
            outOfColor = Color.WHITE
        )

        card.scores.forEachIndexed { index, item ->
            addScoreRow(
                inflater = inflater,
                subject = item.title.ifBlank { "—" },
                score = format(item.score),
                outOf = format(item.maxScore),
                backgroundColor = Color.parseColor(
                    if (index % 2 == 0) "#FFFFFF" else "#F5F8FD"
                ),
                subjectColor = Color.parseColor("#111827"),
                scoreColor = Color.parseColor("#111827"),
                outOfColor = Color.parseColor("#475467")
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
            .takeIf { it > 0.0 }
            ?: 100.0

        addScoreRow(
            inflater = inflater,
            subject = "TOTAL",
            score = format(card.totalScore),
            outOf = format(totalOutOf),
            backgroundColor = Color.parseColor("#FFF1E8"),
            subjectColor = Color.parseColor("#2B4E78"),
            scoreColor = Color.parseColor("#FF6E14"),
            outOfColor = Color.parseColor("#2B4E78")
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
        outOfColor: Int
    ) {
        val row = inflater.inflate(
            R.layout.item_report_card_table_row,
            scoreContainer,
            false
        )
        row.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(24)
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
            .takeIf { it > 0.0 }
            ?: 100.0

        findViewById<TextView>(R.id.txtTotalScore).text =
            "${format(card.totalScore)} / ${format(totalOutOf)}"

        val presentation = statusPresentation(card.resultCode)
        findViewById<TextView>(R.id.txtResultStatus).apply {
            text = presentation.label
            setTextColor(presentation.textColor)
            background = roundedBackground(
                fillColor = presentation.backgroundColor,
                strokeColor = presentation.borderColor,
                radiusDp = 12f
            )
        }

        val starCount = resolvedStarCount(card)
        findViewById<TextView>(R.id.txtStars).apply {
            text = buildString {
                repeat(starCount) { append('★') }
                repeat(5 - starCount) { append('☆') }
            }
            setTextColor(
                if (starCount > 0) {
                    Color.parseColor("#FF6E14")
                } else {
                    Color.parseColor("#98A2B3")
                }
            )
        }

        findViewById<TextView>(R.id.txtStarCaption).text = when {
            starCount == 5 -> "5 Stars"
            starCount in 1..4 -> "$starCount Stars"
            card.resultCode == "CONDITIONAL" -> "Conditional"
            card.resultCode == "FAILED" -> "Fail"
            card.resultCode == "INCOMPLETE" -> "Incomplete"
            else -> "No Star"
        }
    }

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

    private fun statusPresentation(code: String): StatusPresentation {
        return when (code) {
            "FIVE_STAR",
            "FOUR_STAR",
            "THREE_STAR",
            "TWO_STAR",
            "ONE_STAR",
            "PASS_NO_STAR" -> StatusPresentation(
                label = "PASS",
                textColor = Color.parseColor("#067647"),
                backgroundColor = Color.parseColor("#ECFDF3"),
                borderColor = Color.parseColor("#ABEFC6")
            )

            "CONDITIONAL" -> StatusPresentation(
                label = "CONDITIONAL",
                textColor = Color.parseColor("#B54708"),
                backgroundColor = Color.parseColor("#FFFAEB"),
                borderColor = Color.parseColor("#FEDF89")
            )

            "FAILED" -> StatusPresentation(
                label = "FAIL",
                textColor = Color.parseColor("#B42318"),
                backgroundColor = Color.parseColor("#FEF3F2"),
                borderColor = Color.parseColor("#FECDCA")
            )

            "INCOMPLETE" -> StatusPresentation(
                label = "INCOMPLETE",
                textColor = Color.parseColor("#475467"),
                backgroundColor = Color.parseColor("#F2F4F7"),
                borderColor = Color.parseColor("#D0D5DD")
            )

            else -> StatusPresentation(
                label = "UNKNOWN",
                textColor = Color.parseColor("#475467"),
                backgroundColor = Color.parseColor("#F2F4F7"),
                borderColor = Color.parseColor("#D0D5DD")
            )
        }
    }

    private fun defaultMessageFor(code: String): String {
        return DEFAULT_RESULT_MESSAGES[code]
            ?: "This report card is not complete yet."
    }

    private fun roundedBackground(
        fillColor: Int,
        strokeColor: Int,
        radiusDp: Float
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusDp * resources.displayMetrics.density
            setColor(fillColor)
            setStroke(dp(1), strokeColor)
        }
    }

    private fun formatPublishedDate(value: String?): String {
        val normalized = value.orEmpty().trim()
        if (normalized.isBlank()) return "—"

        return normalized
            .substringBefore('T')
            .substringBefore(' ')
            .ifBlank { "—" }
    }

    private fun format(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            String.format(Locale.US, "%.2f", value)
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
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
            "FIVE_STAR" to
                "FABULOUS! Perfect score! This is to certify that you have participated in the term and level above. Your success in learning a new language is our strong desire.",
            "FOUR_STAR" to
                "GREAT WORK! This is to certify that you have participated in the term and level above. Your success in learning a new language is our strong desire.",
            "THREE_STAR" to
                "VERY GOOD! This is to certify that you have participated in the term and level above. Your success in learning a new language is our strong desire.",
            "TWO_STAR" to
                "KEEP IT UP! This is to certify that you have participated in the term and level above. Your success in learning a new language is our strong desire.",
            "ONE_STAR" to
                "OK! This is to certify that you have participated in the term and level above. Your success in learning a new language is our strong desire.",
            "PASS_NO_STAR" to
                "This is to certify that you have participated in the term and level above. Your success in learning a new language is our strong desire.",
            "CONDITIONAL" to
                "Your score is incomplete. You need to either repeat the above level or ask for the supervisor's permission to see if you can go to a higher level.",
            "FAILED" to
                "So Sorry. It looks like you have to repeat the term and level above. Never mind, try again. Don't worry: ask for help, you deserve more."
        )
    }
}
