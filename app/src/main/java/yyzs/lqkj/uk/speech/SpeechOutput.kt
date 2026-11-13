package yyzs.lqkj.uk.speech

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/** Reads short replies aloud with the system text-to-speech engine. */
class SpeechOutput(context: Context) {
    private val speaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = speaking

    private var ready = false
    private var pending: String? = null
    private val tts = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) pending?.let { speak(it) }
        pending = null
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { speaking.value = true }
            override fun onDone(utteranceId: String?) { speaking.value = false }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { speaking.value = false }
            override fun onStop(utteranceId: String?, interrupted: Boolean) { speaking.value = false }
        })
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        if (!ready) { pending = text; return }
        tts.language = guessLocale(text)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), "reply")
    }

    fun stop() {
        pending = null
        tts.stop()
        speaking.value = false
    }

    fun shutdown() = tts.shutdown()

    private fun guessLocale(text: String): Locale {
        var han = 0; var kana = 0; var hangul = 0; var latin = 0
        for (c in text) when (Character.UnicodeScript.of(c.code)) {
            Character.UnicodeScript.HAN -> han++
            Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA -> kana++
            Character.UnicodeScript.HANGUL -> hangul++
            Character.UnicodeScript.LATIN -> latin++
            else -> Unit
        }
        return when {
            kana > 0 -> Locale.JAPANESE
            hangul > han -> Locale.KOREAN
            han > 0 -> Locale.SIMPLIFIED_CHINESE
            latin > 0 -> Locale.US
            else -> Locale.getDefault()
        }
    }
}
