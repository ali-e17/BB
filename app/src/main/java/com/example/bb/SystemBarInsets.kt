package com.example.bb

import android.app.Activity
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ScrollView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.widget.NestedScrollView
import java.util.WeakHashMap
import kotlin.math.max

/**
 * مدیریت واحد Status Bar / Navigation Bar / IME برای کل اپ.
 *
 * هیچ ارتفاع ثابتی برای کیبورد فرض نمی‌شود؛ مقدار واقعی Insets خود دستگاه
 * در هر بار باز و بسته شدن کیبورد دریافت می‌شود. بنابراین روی Samsung،
 * Nothing، Pixel و اندازه‌های مختلف نمایشگر یک منطق مشترک داریم.
 */
object SystemBarInsets {

    private val appliedViews =
        WeakHashMap<View, Unit>()

    fun apply(
        activity: Activity,
        root: View
    ) {
        if (appliedViews.containsKey(root)) {
            return
        }

        appliedViews[root] = Unit

        /*
         * Edge-to-edge را خودمان مدیریت می‌کنیم تا Android 15 و دستگاه‌هایی
         * که رفتار متفاوتی دارند نیز همیشه Insets واقعی را تحویل بدهند.
         */
        WindowCompat.setDecorFitsSystemWindows(
            activity.window,
            false
        )

        /*
         * adjustResize برای سازگاری IME در نسخه‌های قدیمی‌تر Android نیز
         * حفظ می‌شود؛ روی نسخه‌های جدید، ارتفاع واقعی IME از Insets می‌آید.
         */
        activity.window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )

        val initialLeft =
            root.paddingLeft
        val initialTop =
            root.paddingTop
        val initialRight =
            root.paddingRight
        val initialBottom =
            root.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(
            root
        ) { view, insets ->

            val bars =
                insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                )

            val ime =
                insets.getInsets(
                    WindowInsetsCompat.Type.ime()
                )

            val imeVisible =
                insets.isVisible(
                    WindowInsetsCompat.Type.ime()
                )

            /*
             * بالا همیشه به اندازه Status Bar امن می‌ماند.
             * پایین در حالت کیبورد از ارتفاع واقعی IME استفاده می‌کند.
             */
            val bottomInset =
                if (imeVisible) {
                    max(
                        bars.bottom,
                        ime.bottom
                    )
                } else {
                    bars.bottom
                }

            view.setPadding(
                initialLeft + bars.left,
                initialTop + bars.top,
                initialRight + bars.right,
                initialBottom + bottomInset
            )

            /*
             * Viewهای ثابت مثل Footer لاگین هنگام باز بودن کیبورد نباید
             * روی فرم شناور شوند. هر View با tag=hide_on_ime در زمان IME
             * موقتاً مخفی می‌شود و بعد از بسته شدن کیبورد برمی‌گردد.
             */
            updateImeTaggedViews(
                root = view,
                imeVisible = imeVisible
            )

            if (imeVisible) {
                activity.currentFocus
                    ?.takeIf {
                        it.isAttachedToWindow
                    }
                    ?.let {
                        revealFocusedView(it)
                    }
            }

            insets
        }

        /*
         * بعضی IMEها (Nothing / Pixel / Gboard variants) ارتفاع را طی
         * انیمیشن چند مرحله‌ای تغییر می‌دهند. در طول انیمیشن نیز فیلد
         * فعال را دوباره داخل viewport واقعی نگه می‌داریم.
         */
        ViewCompat.setWindowInsetsAnimationCallback(
            root,
            object : WindowInsetsAnimationCompat.Callback(
                WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_CONTINUE_ON_SUBTREE
            ) {
                override fun onProgress(
                    insets: WindowInsetsCompat,
                    runningAnimations: MutableList<WindowInsetsAnimationCompat>
                ): WindowInsetsCompat {
                    if (
                        insets.isVisible(
                            WindowInsetsCompat.Type.ime()
                        )
                    ) {
                        activity.currentFocus
                            ?.takeIf {
                                it.isAttachedToWindow
                            }
                            ?.let {
                                revealFocusedView(
                                    it,
                                    smooth = false
                                )
                            }
                    }

                    return insets
                }
            }
        )

        ViewCompat.requestApplyInsets(root)
    }

    private fun revealFocusedView(
        focused: View,
        smooth: Boolean = true
    ) {
        if (!focused.isAttachedToWindow) {
            return
        }

        fun revealNow(
            animate: Boolean
        ) {
            if (!focused.isAttachedToWindow) {
                return
            }

            revealInsideScrollableParent(
                focused = focused,
                smooth = animate
            )
        }

        focused.post {
            revealNow(false)
        }

        focused.postDelayed(
            {
                revealNow(false)
            },
            140L
        )

        focused.postDelayed(
            {
                revealNow(smooth)
            },
            360L
        )

        focused.postDelayed(
            {
                revealNow(false)
            },
            560L
        )
    }

    /**
     * requestRectangleOnScreen روی بعضی ROMها فقط یک درخواست کلی می‌دهد
     * و ScrollView را به اندازه کافی حرکت نمی‌دهد. این متد نزدیک‌ترین
     * ScrollView/NestedScrollView را پیدا می‌کند و مقدار Scroll واقعی را
     * بر اساس viewport بعد از IME محاسبه می‌کند.
     */
    private fun revealInsideScrollableParent(
        focused: View,
        smooth: Boolean
    ) {
        val density =
            focused.resources.displayMetrics.density

        val topGap =
            (12f * density).toInt()

        val bottomGap =
            (28f * density).toInt()

        var ancestor =
            focused.parent

        while (ancestor != null) {
            when (ancestor) {
                is NestedScrollView -> {
                    revealInScrollContainer(
                        container = ancestor,
                        focused = focused,
                        topGap = topGap,
                        bottomGap = bottomGap,
                        smooth = smooth
                    )
                    return
                }

                is ScrollView -> {
                    revealInScrollContainer(
                        container = ancestor,
                        focused = focused,
                        topGap = topGap,
                        bottomGap = bottomGap,
                        smooth = smooth
                    )
                    return
                }
            }

            ancestor =
                ancestor.parent
        }

        // Fallback برای RecyclerView، ConstraintLayout و سایر والدها.
        focused.requestRectangleOnScreen(
            Rect(
                0,
                -topGap,
                focused.width,
                focused.height + bottomGap
            ),
            !smooth
        )
    }

    private fun revealInScrollContainer(
        container: ViewGroup,
        focused: View,
        topGap: Int,
        bottomGap: Int,
        smooth: Boolean
    ) {
        val rect =
            Rect()

        focused.getDrawingRect(
            rect
        )

        runCatching {
            container.offsetDescendantRectToMyCoords(
                focused,
                rect
            )
        }.onFailure {
            focused.requestRectangleOnScreen(
                Rect(
                    0,
                    -topGap,
                    focused.width,
                    focused.height + bottomGap
                ),
                true
            )
            return
        }

        val scrollY =
            when (container) {
                is NestedScrollView -> container.scrollY
                is ScrollView -> container.scrollY
                else -> 0
            }

        val viewportTop =
            scrollY +
                container.paddingTop +
                topGap

        val viewportBottom =
            scrollY +
                container.height -
                container.paddingBottom -
                bottomGap

        val delta =
            when {
                rect.bottom > viewportBottom ->
                    rect.bottom -
                        viewportBottom

                rect.top < viewportTop ->
                    rect.top -
                        viewportTop

                else -> 0
            }

        if (delta == 0) {
            return
        }

        when (container) {
            is NestedScrollView -> {
                if (smooth) {
                    container.smoothScrollBy(
                        0,
                        delta
                    )
                } else {
                    container.scrollBy(
                        0,
                        delta
                    )
                }
            }

            is ScrollView -> {
                if (smooth) {
                    container.smoothScrollBy(
                        0,
                        delta
                    )
                } else {
                    container.scrollBy(
                        0,
                        delta
                    )
                }
            }
        }
    }

    private fun updateImeTaggedViews(
        root: View,
        imeVisible: Boolean
    ) {
        if (
            root.tag?.toString() ==
                TAG_HIDE_ON_IME
        ) {
            root.visibility =
                if (imeVisible) {
                    View.GONE
                } else {
                    View.VISIBLE
                }
        }

        if (root is ViewGroup) {
            for (
                index in
                0 until root.childCount
            ) {
                updateImeTaggedViews(
                    root = root.getChildAt(index),
                    imeVisible = imeVisible
                )
            }
        }
    }

    private const val TAG_HIDE_ON_IME =
        "hide_on_ime"
}
