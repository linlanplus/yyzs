package yyzs.lqkj.uk.assist

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import yyzs.lqkj.uk.speech.Recognizers

/**
 * An assistant app must declare a recognition service, and Android makes it the system default
 * recognizer while the app holds the assistant role. Forward everything to a real recognizer so
 * voice input in other apps keeps working.
 */
class ProxyRecognitionService : RecognitionService() {
    private var delegate: SpeechRecognizer? = null

    override fun onStartListening(recognizerIntent: Intent, listener: Callback) {
        delegate?.destroy()
        val recognizer = Recognizers.create(this)
        if (recognizer == null) {
            listener.error(SpeechRecognizer.ERROR_CLIENT)
            return
        }
        delegate = recognizer
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = listener.readyForSpeech(params ?: Bundle())
            override fun onBeginningOfSpeech() = listener.beginningOfSpeech()
            override fun onRmsChanged(rmsdB: Float) = listener.rmsChanged(rmsdB)
            override fun onBufferReceived(buffer: ByteArray?) { buffer?.let(listener::bufferReceived) }
            override fun onEndOfSpeech() = listener.endOfSpeech()
            override fun onError(error: Int) = listener.error(error)
            override fun onResults(results: Bundle?) = listener.results(results ?: Bundle())
            override fun onPartialResults(partialResults: Bundle?) = listener.partialResults(partialResults ?: Bundle())
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        recognizer.startListening(recognizerIntent)
    }

    override fun onStopListening(listener: Callback) {
        delegate?.stopListening()
    }

    override fun onCancel(listener: Callback) {
        delegate?.cancel()
    }

    override fun onDestroy() {
        delegate?.destroy()
        delegate = null
        super.onDestroy()
    }
}
