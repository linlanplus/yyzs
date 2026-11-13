package yyzs.lqkj.uk.assist

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

sealed interface Capture {
    data object Pending : Capture
    /** The system gave no screenshot: protected app (FLAG_SECURE) or "use screenshot" turned off. */
    data object Unavailable : Capture
    data class Ready(val bitmap: Bitmap) : Capture
}

/** Hands the assist screenshot from the session to [AssistActivity]; both run in the app process. */
object ScreenHolder {
    private val state = MutableStateFlow<Capture>(Capture.Unavailable)
    val capture: StateFlow<Capture> = state

    fun begin() { state.value = Capture.Pending }

    fun deliver(bitmap: Bitmap?) {
        state.value = if (bitmap == null) Capture.Unavailable else Capture.Ready(bitmap)
    }
}
