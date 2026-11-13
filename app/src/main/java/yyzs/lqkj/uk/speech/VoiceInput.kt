package yyzs.lqkj.uk.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** Hold-to-talk wrapper around [SpeechRecognizer]. Main thread only. */
class VoiceInput(
    private val context: Context,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onError: (String) -> Unit,
    /** Loudness 0..1, roughly every 50-100 ms while listening. */
    private val onLevel: (Float) -> Unit = {},
) {
    private var recognizer: SpeechRecognizer? = null
    private var lastPartial = ""

    fun start(language: String) {
        recognizer?.destroy()
        val r = Recognizers.create(context)
        if (r == null) {
            onError("没有可用的语音识别服务，请在设置里选择")
            return
        }
        recognizer = r
        lastPartial = ""
        r.setRecognitionListener(object : RecognitionListener {
            override fun onPartialResults(partialResults: Bundle?) {
                best(partialResults)?.let { lastPartial = it; onPartial(it) }
            }

            override fun onResults(results: Bundle?) {
                val text = best(results) ?: lastPartial
                if (text.isBlank()) onError("没有听清，请再说一次") else onFinal(text)
            }

            override fun onError(error: Int) {
                // Releasing early often reports "no match" although partial text arrived.
                if (lastPartial.isNotBlank() && error in RECOVERABLE) onFinal(lastPartial) else onError(describe(error))
            }

            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            // Recognizers report roughly -2..10 dB.
            override fun onRmsChanged(rmsdB: Float) = onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = onLevel(0f)
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        r.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                // Hold-to-talk: the user decides when speech ends.
                .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 10_000L)
                .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 10_000L),
        )
    }

    fun stop() { recognizer?.stopListening() }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun best(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }

    private fun describe(error: Int) = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没有听清，请再说一次"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER -> "语音识别网络错误，可在设置里换一个识别服务"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "需要麦克风权限"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别服务忙，请稍后再试"
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "当前识别服务不支持这种语言"
        else -> "语音识别失败（$error），可在设置里换一个识别服务"
    }

    private companion object {
        val RECOVERABLE = setOf(SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT, SpeechRecognizer.ERROR_CLIENT)
    }
}
