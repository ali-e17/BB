package com.example.bb

import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity

/**
 * Shared activity base that keeps every screen clear of the status and navigation bars.
 */
open class BaseActivity : AppCompatActivity() {

    override fun setContentView(@LayoutRes layoutResID: Int) {
        super.setContentView(layoutResID)
        applyRootInsets()
    }

    override fun setContentView(view: View) {
        super.setContentView(view)
        applyRootInsets()
    }

    override fun setContentView(view: View, params: ViewGroup.LayoutParams) {
        super.setContentView(view, params)
        applyRootInsets()
    }

    private fun applyRootInsets() {
        val content = findViewById<ViewGroup>(android.R.id.content)
        val root = content.getChildAt(0) ?: return
        SystemBarInsets.apply(this, root)
    }
}
