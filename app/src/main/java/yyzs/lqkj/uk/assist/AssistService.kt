package yyzs.lqkj.uk.assist

import android.content.Context
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession

/** Bound by the system while this app is the default digital assistant. */
class AssistService : VoiceInteractionService() {
    override fun onReady() {
        super.onReady()
        instance = this
    }

    override fun onShutdown() {
        instance = null
        super.onShutdown()
    }

    companion object {
        @Volatile
        var instance: AssistService? = null
            private set

        fun isActive(context: Context) = isActiveService(context, android.content.ComponentName(context, AssistService::class.java))

        /** Same as long-pressing the power key: the system screenshots the screen and opens a session. */
        fun trigger(): Boolean {
            val service = instance ?: return false
            service.showSession(
                Bundle(),
                VoiceInteractionSession.SHOW_WITH_ASSIST or VoiceInteractionSession.SHOW_WITH_SCREENSHOT,
            )
            return true
        }
    }
}
