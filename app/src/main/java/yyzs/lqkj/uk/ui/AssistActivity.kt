package yyzs.lqkj.uk.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import yyzs.lqkj.uk.MainActivity
import yyzs.lqkj.uk.Prefs
import yyzs.lqkj.uk.agent.AssistantAgent
import yyzs.lqkj.uk.agent.InstalledApps
import yyzs.lqkj.uk.agent.PhoneActions
import yyzs.lqkj.uk.assist.Capture
import yyzs.lqkj.uk.assist.ScreenHolder
import yyzs.lqkj.uk.live.LiveSession
import yyzs.lqkj.uk.speech.SpeechOutput
import yyzs.lqkj.uk.speech.VoiceInput
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Opened on top of whatever app was in front, by the assist gesture, the tile or the accessibility button. */
class AssistActivity : ComponentActivity() {
    private lateinit var controller: AssistController
    private lateinit var voice: VoiceInput
    private lateinit var speech: SpeechOutput
    private lateinit var agent: AssistantAgent
    private var motion by mutableStateOf(PanelMotion())
    private var listening by mutableStateOf(false)
    private var partial by mutableStateOf("")
    private var micLevel by mutableFloatStateOf(0f)
    private var live by mutableStateOf<LiveSession?>(null)
    private var closing by mutableStateOf(false)

    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) controller.message = "需要麦克风权限才能语音输入"
    }
    private val appListPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) controller.message = "没有应用列表权限时，只能按准确的应用名打开应用。可以在设置里重新授权"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        speech = SpeechOutput(this)
        val actions = PhoneActions(applicationContext) { deep -> runOnUiThread { controller.translateScreen(deep) } }
        agent = AssistantAgent(actions)
        controller = AssistController(
            lifecycleScope, statusBarHeight(), agent,
            onReply = { text, spoken -> if (spoken && Prefs.speakReplies) speech.speak(text) },
            store = ChatStore(applicationContext),
        )
        voice = VoiceInput(
            this,
            onPartial = { partial = it },
            onFinal = { listening = false; micLevel = 0f; controller.send(it, spoken = true) },
            onError = { listening = false; micLevel = 0f; controller.message = it },
            onLevel = { micLevel = it },
        )
        onWake()
        // Asked once here; the settings screen has a button for later.
        if (!Prefs.askedAppList && !InstalledApps.permissionGranted(this)) {
            Prefs.askedAppList = true
            appListPermission.launch(InstalledApps.PERMISSION)
        }

        setContent {
            AppTheme {
                val speaking by speech.isSpeaking.collectAsState()
                val mood = when {
                    listening -> OrbMood.Listening
                    controller.busy && controller.messages.lastOrNull()?.text.isNullOrEmpty() -> OrbMood.Thinking
                    speaking || controller.busy -> OrbMood.Speaking
                    else -> OrbMood.Idle
                }
                // While speaking, give the orb a gentle pulse instead of a mic level.
                val level = if (listening) micLevel else if (mood == OrbMood.Speaking) 0.35f else 0f

                LaunchedEffect(motion) { motion.enter() }
                LaunchedEffect(closing) {
                    if (closing) { motion.exit(); finish(); overrideTransitionNone() }
                }

                val screen = controller.screen
                Box {
                    if (controller.showingScreen && screen != null) {
                        BackHandler { controller.showingScreen = false }
                        ResultScreen(controller, screen, onBack = { controller.showingScreen = false }, onOpenSettings = ::openSettings)
                    } else {
                        BackHandler(enabled = live == null) { close() }
                        PanelScreen(
                            controller, motion, mood,
                            listening = listening,
                            voiceLevel = level,
                            partial = partial,
                            onMicDown = ::startVoice,
                            onMicUp = { if (listening) voice.stop() },
                            onDismiss = ::close,
                            onOpenSettings = ::openSettings,
                            onStartLive = ::startLive,
                            onSpeak = { speech.speak(it) },
                        )
                    }
                    AnimatedVisibility(live != null, enter = fadeIn(tween(250)), exit = fadeOut(tween(200))) {
                        live?.let { session ->
                            BackHandler { endLive() }
                            LiveScreen(session, onHangUp = ::endLive)
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        closing = false
        motion = PanelMotion()
        onWake()
    }

    /** Each invocation starts fresh with the new screenshot. */
    private fun onWake() {
        endLive()
        speech.stop()
        controller.reset()
        controller.screenshot = null
        lifecycleScope.launch {
            val capture = withTimeoutOrNull(1500) { ScreenHolder.capture.first { it !is Capture.Pending } }
            controller.screenshot = (capture as? Capture.Ready)?.bitmap
            if (Prefs.autoTranslateOnWake && controller.screenshot != null) controller.translateScreen(Prefs.deepByDefault)
        }
    }

    private fun close() {
        if (!closing) closing = true
    }

    private fun hasMic(): Boolean {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) return true
        micPermission.launch(Manifest.permission.RECORD_AUDIO)
        return false
    }

    private fun startVoice() {
        if (!hasMic()) return
        speech.stop()
        controller.message = null
        partial = ""
        listening = true
        voice.start(Prefs.speechLang)
    }

    private fun startLive() {
        if (live != null || !hasMic()) return
        speech.stop()
        live = LiveSession(applicationContext, agent, onAppLaunched = { runOnUiThread(::endLive) }).also { it.start() }
    }

    private fun endLive() {
        live?.release()
        live = null
    }

    private fun openSettings() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    @Suppress("DEPRECATION")
    private fun overrideTransitionNone() = overridePendingTransition(0, 0)

    @SuppressLint("DiscouragedApi", "InternalInsetResource")
    private fun statusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }

    override fun onStop() {
        super.onStop()
        // Behaves like a system panel: leaving it closes it.
        if (!isChangingConfigurations) {
            endLive()
            speech.stop()
            finish()
        }
    }

    override fun onDestroy() {
        voice.destroy()
        speech.shutdown()
        endLive()
        super.onDestroy()
    }
}
