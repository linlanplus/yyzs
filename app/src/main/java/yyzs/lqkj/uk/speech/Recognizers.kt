package yyzs.lqkj.uk.speech

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import yyzs.lqkj.uk.Prefs

data class RecognizerOption(val id: String, val label: String)

object Recognizers {
    /** Pseudo-id for [SpeechRecognizer.createOnDeviceSpeechRecognizer]. */
    const val ON_DEVICE = "on_device"

    /** Installed recognition services except this app's own proxy. */
    fun services(context: Context): List<ComponentName> =
        context.packageManager.queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0)
            .map { ComponentName(it.serviceInfo.packageName, it.serviceInfo.name) }
            .filter { it.packageName != context.packageName }

    fun options(context: Context): List<RecognizerOption> {
        val pm = context.packageManager
        val list = mutableListOf(RecognizerOption("", "自动选择"))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            list += RecognizerOption(ON_DEVICE, "系统离线识别")
        }
        for (c in services(context)) {
            val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(c.packageName, 0)).toString() }
                .getOrDefault(c.packageName)
            list += RecognizerOption(c.flattenToString(), label)
        }
        return list
    }

    /** The configured service if it is still installed, else Google's, else any other. */
    fun preferredService(context: Context): ComponentName? {
        val all = services(context)
        ComponentName.unflattenFromString(Prefs.speechService)?.let { if (it in all) return it }
        return all.firstOrNull { it.packageName.startsWith("com.google.") } ?: all.firstOrNull()
    }

    fun create(context: Context): SpeechRecognizer? {
        if (Prefs.speechService == ON_DEVICE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        ) {
            return SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        }
        val service = preferredService(context) ?: return null
        return SpeechRecognizer.createSpeechRecognizer(context, service)
    }
}
