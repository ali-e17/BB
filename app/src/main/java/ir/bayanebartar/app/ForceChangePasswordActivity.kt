package ir.bayanebartar.app

import android.content.Intent
import android.os.Bundle

/**
 * Compatibility entry point for older app flows.
 * The forced and optional password-change flows now share UpdateProfileActivity.
 */
class ForceChangePasswordActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, UpdateProfileActivity::class.java).apply {
            putExtra(UpdateProfileActivity.EXTRA_FORCE_PASSWORD_CHANGE, true)
            putExtra(UpdateProfileActivity.EXTRA_OPEN_MAIN_AFTER_CHANGE, true)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        })
        finish()
    }
}
