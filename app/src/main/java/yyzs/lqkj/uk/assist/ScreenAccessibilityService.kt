package yyzs.lqkj.uk.assist

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.provider.Settings
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.annotation.RequiresApi
import yyzs.lqkj.uk.ui.AssistActivity

/**
 * Second way to get the screen: an accessibility service may take screenshots (Android 11+).
 * It doesn't depend on being the default assistant, which vendor assistants such as YOYO keep
 * taking back. Triggered by the accessibility button/shortcut or the quick-settings tile.
 */
class ScreenAccessibilityService : AccessibilityService() {
    private val buttonCallback = object : AccessibilityButtonController.AccessibilityButtonCallback() {
        override fun onClicked(controller: AccessibilityButtonController) { capture() }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        accessibilityButtonController.registerAccessibilityButtonCallback(buttonCallback)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        accessibilityButtonController.unregisterAccessibilityButtonCallback(buttonCallback)
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    fun capture() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Toast.makeText(this, "无障碍截图需要 Android 11 以上", Toast.LENGTH_SHORT).show()
            return
        }
        ScreenHolder.begin()
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(result: ScreenshotResult) {
                ScreenHolder.deliver(toSoftwareBitmap(result))
                open()
            }

            override fun onFailure(errorCode: Int) {
                // Protected windows (FLAG_SECURE) and rate limiting end up here.
                ScreenHolder.deliver(null)
                open()
            }
        })
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun toSoftwareBitmap(result: ScreenshotResult): Bitmap? {
        val buffer = result.hardwareBuffer
        val hardware = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
        val software = hardware?.copy(Bitmap.Config.ARGB_8888, false)
        hardware?.recycle()
        buffer.close()
        return software
    }

    private fun open() {
        startActivity(
            Intent(this, AssistActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
    }

    companion object {
        @Volatile
        var instance: ScreenAccessibilityService? = null
            private set

        fun isEnabled(context: Context): Boolean {
            val me = ComponentName(context, ScreenAccessibilityService::class.java)
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?: return false
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
