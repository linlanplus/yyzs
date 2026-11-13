package yyzs.lqkj.uk.live

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.util.Base64
import yyzs.lqkj.uk.Prefs
import yyzs.lqkj.uk.agent.AgentEvent
import yyzs.lqkj.uk.agent.AssistantAgent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

enum class LivePhase { Connecting, Listening, Speaking, Working, Closed, Failed }

data class LiveLine(val fromUser: Boolean, val text: String)

/**
 * A full-duplex GPT-Live voice conversation over WebSocket (`/v1/live/sessions`).
 * GPT-Live talks; when it delegates a task (client delegation) the request goes to [agent],
 * i.e. DeepSeek with the phone tools, and the result is handed back as spoken commentary.
 */
class LiveSession(
    private val context: Context,
    private val agent: AssistantAgent,
    private val onAppLaunched: () -> Unit,
) {
    private val _phase = MutableStateFlow(LivePhase.Connecting)
    val phase: StateFlow<LivePhase> = _phase
    private val _level = MutableStateFlow(0f)
    /** 0..1 loudness of whoever is talking, for the orb animation. */
    val level: StateFlow<Float> = _level
    private val _lines = MutableStateFlow<List<LiveLine>>(emptyList())
    val lines: StateFlow<List<LiveLine>> = _lines
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var socket: WebSocket? = null
    private var micJob: Job? = null
    private var playJob: Job? = null
    private val playback = LinkedBlockingQueue<ByteArray>()
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var previousMode = AudioManager.MODE_NORMAL
    @Volatile private var closing = false

    // Turn tracking: the delegation event carries no task text, so keep the latest user utterance.
    private val userTurn = StringBuilder()
    private var userTurnOpen = false
    private var lastUserUtterance = ""

    fun start() {
        val key = Prefs.openaiKey
        if (key.isBlank()) return fail("请先在设置里填写实时语音模型的 API Key")
        val request = Request.Builder()
            .url(Prefs.liveUrl)
            .header("Authorization", "Bearer $key")
            .build()
        socket = HTTP.newWebSocket(request, listener)
    }

    fun stop() {
        if (closing) return
        closing = true
        stopAudio()
        val ws = socket
        if (ws != null && _phase.value != LivePhase.Failed) {
            ws.send(JSONObject().put("type", "session.close").toString())
            // Give the server a moment to report final usage, then drop the connection.
            scope.launch { delay(3000); ws.cancel() }
        }
        if (_phase.value != LivePhase.Failed) _phase.value = LivePhase.Closed
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            val session = JSONObject()
                .put("model", Prefs.liveModel)
                .put("instructions", INSTRUCTIONS)
                .put(
                    "audio", JSONObject()
                        .put("format", JSONObject().put("type", "audio/pcm").put("rate", SAMPLE_RATE))
                        .put("output", JSONObject().put("voice", Prefs.liveVoice)),
                )
                .put("delegation", JSONObject().put("type", "client").put("acknowledgement_delay_ms", 800))
            webSocket.send(JSONObject().put("type", "session.start").put("event_id", newId()).put("session", session).toString())
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val event = runCatching { JSONObject(text) }.getOrNull() ?: return
            when (event.optString("type")) {
                "session.started" -> {
                    startAudio()
                    _phase.value = LivePhase.Listening
                }
                "session.output_audio.delta" -> {
                    val pcm = Base64.decode(event.optString("delta"), Base64.DEFAULT)
                    playback.offer(pcm)
                    if (_phase.value == LivePhase.Listening) _phase.value = LivePhase.Speaking
                }
                "session.input_transcript.delta" -> onUserText(event.optString("delta"))
                "session.output_transcript.delta" -> onAssistantText(event.optString("delta"))
                "session.delegation.created" -> event.optJSONObject("delegation")?.optString("id")?.let(::delegate)
                "session.closed" -> webSocket.close(1000, null)
                "error" -> {
                    val err = event.optJSONObject("error")
                    val message = err?.optString("message")?.takeIf { it.isNotBlank() } ?: err?.optString("code") ?: "未知错误"
                    // Errors about a single command don't end the session; ones before start do.
                    if (_phase.value == LivePhase.Connecting) fail("语音会话启动失败：$message") else _error.value = message
                }
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (closing) return
            val reason = when (response?.code) {
                401 -> "实时语音模型的 API Key 无效"
                403 -> "没有该实时语音模型的使用权限（部分服务商的免费账户不支持）"
                404 -> "接口地址或模型不存在"
                429 -> "并发会话数或额度已用完"
                null -> "网络连接失败：${t.message}"
                else -> "连接失败（${response.code}）"
            }
            fail(reason)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            stopAudio()
            if (_phase.value != LivePhase.Failed) _phase.value = LivePhase.Closed
        }
    }

    private fun onUserText(delta: String) {
        if (delta.isEmpty()) return
        if (!userTurnOpen) { userTurn.clear(); userTurnOpen = true; appendLine(true, "") }
        userTurn.append(delta)
        lastUserUtterance = userTurn.toString()
        updateLastLine(true, userTurn.toString())
        // Barge-in: the user started talking over the reply, so drop what is still queued.
        if (_phase.value == LivePhase.Speaking) {
            playback.clear()
            _phase.value = LivePhase.Listening
        }
    }

    private fun onAssistantText(delta: String) {
        if (delta.isEmpty()) return
        val lines = _lines.value
        if (userTurnOpen || lines.isEmpty() || lines.last().fromUser) {
            userTurnOpen = false
            appendLine(false, delta)
        } else {
            updateLastLine(false, lines.last().text + delta)
        }
    }

    private fun appendLine(fromUser: Boolean, text: String) {
        _lines.value = (_lines.value + LiveLine(fromUser, text)).takeLast(MAX_LINES)
    }

    private fun updateLastLine(fromUser: Boolean, text: String) {
        val lines = _lines.value
        if (lines.isEmpty() || lines.last().fromUser != fromUser) appendLine(fromUser, text)
        else _lines.value = lines.dropLast(1) + LiveLine(fromUser, text)
    }

    /** Runs the delegated task through DeepSeek + phone tools and hands the result back to be spoken. */
    private fun delegate(delegationId: String) {
        val task = lastUserUtterance.ifBlank { return }
        scope.launch {
            _phase.value = LivePhase.Working
            val reply = StringBuilder()
            var launched = false
            val outcome = runCatching {
                agent.send(task, spoken = true).collect { e ->
                    when (e) {
                        is AgentEvent.Text -> reply.append(e.delta)
                        // Search results and page text are for the model, not to be read out.
                        is AgentEvent.Action -> if (e.result.step == null) {
                            if (reply.isNotEmpty()) reply.append('\n')
                            reply.append(e.result.output)
                            launched = launched || e.result.launches
                        }
                        else -> Unit
                    }
                }
            }
            val content = outcome.fold(
                onSuccess = { reply.toString().ifBlank { "已完成" } },
                onFailure = { "处理失败：${it.message}" },
            ).take(MAX_COMMENTARY_CHARS)
            socket?.send(
                JSONObject()
                    .put("type", "session.commentary.append")
                    .put("event_id", newId())
                    .put("delegation_id", delegationId)
                    .put("content", content)
                    .toString(),
            )
            _phase.value = LivePhase.Listening
            if (launched) {
                // Let the confirmation start playing before the other app covers the panel.
                delay(1500)
                onAppLaunched()
            }
        }
    }

    @SuppressLint("MissingPermission") // Checked by the caller before starting a session.
    private fun startAudio() {
        previousMode = audioManager.mode
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        routeToSpeaker()

        val minIn = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION, SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minIn, CHUNK_BYTES * 4),
        )
        // Full duplex: without echo cancellation the model hears itself through the speaker.
        if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(record.audioSessionId)?.enabled = true
        if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(record.audioSessionId)?.enabled = true

        val minOut = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build(),
            )
            .setBufferSizeInBytes(maxOf(minOut, CHUNK_BYTES * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setSessionId(record.audioSessionId)
            .build()

        micJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(CHUNK_BYTES)
            record.startRecording()
            try {
                while (isActive) {
                    val n = record.read(buffer, 0, buffer.size)
                    if (n <= 0) continue
                    val even = n and 1.inv()
                    if (_phase.value != LivePhase.Speaking) _level.value = rms(buffer, even)
                    socket?.send(
                        JSONObject()
                            .put("type", "session.input_audio.append")
                            .put("audio", Base64.encodeToString(buffer, 0, even, Base64.NO_WRAP))
                            .toString(),
                    )
                }
            } finally {
                record.stop()
                record.release()
            }
        }
        playJob = scope.launch(Dispatchers.IO) {
            track.play()
            try {
                while (isActive) {
                    val pcm = playback.poll(200, TimeUnit.MILLISECONDS)
                    if (pcm == null) {
                        if (_phase.value == LivePhase.Speaking) _phase.value = LivePhase.Listening
                        continue
                    }
                    _level.value = rms(pcm, pcm.size and 1.inv())
                    track.write(pcm, 0, pcm.size and 1.inv())
                }
            } finally {
                track.stop()
                track.release()
            }
        }
    }

    private fun routeToSpeaker() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.availableCommunicationDevices
                .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                ?.let { audioManager.setCommunicationDevice(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = true
        }
    }

    private fun stopAudio() {
        micJob?.cancel(); micJob = null
        playJob?.cancel(); playJob = null
        playback.clear()
        _level.value = 0f
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) audioManager.clearCommunicationDevice()
        audioManager.mode = previousMode
    }

    private fun fail(message: String) {
        _error.value = message
        _phase.value = LivePhase.Failed
        stopAudio()
        socket?.cancel()
    }

    fun release() {
        stop()
        scope.launch { delay(3500); scope.cancel() }
    }

    private fun rms(pcm: ByteArray, length: Int): Float {
        var sum = 0.0
        var i = 0
        while (i + 1 < length) {
            val s = (pcm[i].toInt() and 0xFF) or (pcm[i + 1].toInt() shl 8)
            sum += s.toDouble() * s
            i += 2
        }
        val samples = length / 2
        if (samples == 0) return 0f
        return (sqrt(sum / samples) / 6000.0).toFloat().coerceIn(0f, 1f)
    }

    private fun newId() = "evt_" + UUID.randomUUID().toString().replace("-", "").take(16)

    private companion object {
        const val SAMPLE_RATE = 24000
        /** 40 ms of 16-bit mono audio. */
        const val CHUNK_BYTES = SAMPLE_RATE * 2 / 25
        const val MAX_LINES = 30
        /** Commentary is capped at 500 tokens; Chinese runs roughly one token per character. */
        const val MAX_COMMENTARY_CHARS = 450

        val HTTP = yyzs.lqkj.uk.translate.DeepSeekClient.http.newBuilder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()

        const val INSTRUCTIONS = "你是手机上的语音助手“小克”，用自然、简短的口语交谈，默认说普通话；用户说其他语言时用同样的语言回答。" +
            "用户要你翻译时，直接说出译文。" +
            "需要操作手机（打开应用、闹钟、计时、手电筒、导航、搜索、调音量、打开设置、翻译屏幕）或需要准确信息时，交给后台处理，" +
            "等后台结果出来再告诉用户，不要自己假装已经完成。"
    }
}
