package yyzs.lqkj.uk.assist

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import yyzs.lqkj.uk.ui.AssistActivity

class AssistSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = AssistSession(this)
}

/**
 * Has no UI of its own. It waits briefly for the screenshot so the assistant UI never ends up in
 * it, then opens [AssistActivity] and hides itself.
 */
private class AssistSession(context: Context) : VoiceInteractionSession(context) {
    private val handler = Handler(Looper.getMainLooper())
    private var launched = false
    private val launchAnyway = Runnable { launch() }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        launched = false
        ScreenHolder.begin()
        if (showFlags and SHOW_WITH_SCREENSHOT == 0) ScreenHolder.deliver(null)
        handler.postDelayed(launchAnyway, SCREENSHOT_WAIT_MS)
        if (ScreenHolder.capture.value !is Capture.Pending) launch()
    }

    override fun onHandleScreenshot(screenshot: Bitmap?) {
        ScreenHolder.deliver(screenshot)
        launch()
    }

    private fun launch() {
        if (launched) return
        launched = true
        handler.removeCallbacks(launchAnyway)
        startAssistantActivity(
            Intent(context, AssistActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
        hide()
    }

    override fun onHide() {
        handler.removeCallbacks(launchAnyway)
        super.onHide()
    }

    companion object {
        private const val SCREENSHOT_WAIT_MS = 700L
    }
}
