package yyzs.lqkj.uk.assist

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import yyzs.lqkj.uk.SettingsActivity

/**
 * Started by the quick-settings tile (starting an activity collapses the notification shade) or by
 * the system assist gesture via ACTION_ASSIST. Once this window is gone, capture the screen through
 * whichever route is available.
 */
class TriggerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (AssistService.instance == null && ScreenAccessibilityService.instance == null) {
            Toast.makeText(this, "请先开启“翻译助手 屏幕截图”无障碍服务", Toast.LENGTH_LONG).show()
            startActivity(SettingsActivity.intent(this, SettingsActivity.Page.Wake).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } else {
            val delay = if (intent?.action == Intent.ACTION_ASSIST) WINDOW_GONE_MS else SHADE_COLLAPSE_MS
            Handler(Looper.getMainLooper()).postDelayed(::capture, delay)
        }
        finish()
    }

    companion object {
        private const val SHADE_COLLAPSE_MS = 450L
        private const val WINDOW_GONE_MS = 200L

        /** Prefers the accessibility screenshot: vendor assistants can take the assistant slot back at any time. */
        fun capture() {
            ScreenAccessibilityService.instance?.let { it.capture(); return }
            AssistService.trigger()
        }
    }
}
