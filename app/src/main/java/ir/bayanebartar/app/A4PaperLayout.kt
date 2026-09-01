package ir.bayanebartar.app

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import kotlin.math.roundToInt

/**
 * Keeps the report-card preview in the portrait A4 aspect ratio (210 × 297).
 * The content inside the view is laid out against the same single-page canvas.
 */
class A4PaperLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val availableWidth = View.MeasureSpec.getSize(widthMeasureSpec)
        val measuredWidth = if (availableWidth > 0) {
            availableWidth
        } else {
            suggestedMinimumWidth.coerceAtLeast(1)
        }

        val a4Height = (measuredWidth * A4_HEIGHT / A4_WIDTH).roundToInt()

        super.onMeasure(
            View.MeasureSpec.makeMeasureSpec(measuredWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(a4Height, View.MeasureSpec.EXACTLY)
        )
    }

    private companion object {
        const val A4_WIDTH = 210f
        const val A4_HEIGHT = 297f
    }
}
