package com.example.bb

import android.app.Activity
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import java.util.WeakHashMap

/** Applies status/navigation-bar padding consistently to one screen root. */
object SystemBarInsets {
    private val appliedViews = WeakHashMap<View, Unit>()

    fun apply(activity: Activity, root: View) {
        if (appliedViews.containsKey(root)) return
        appliedViews[root] = Unit

        WindowCompat.setDecorFitsSystemWindows(activity.window, false)

        val initialLeft = root.paddingLeft
        val initialTop = root.paddingTop
        val initialRight = root.paddingRight
        val initialBottom = root.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                initialLeft + bars.left,
                initialTop + bars.top,
                initialRight + bars.right,
                initialBottom + bars.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }
}
