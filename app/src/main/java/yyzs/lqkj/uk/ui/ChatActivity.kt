package yyzs.lqkj.uk.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import yyzs.lqkj.uk.Prefs
import yyzs.lqkj.uk.agent.AssistantAgent
import yyzs.lqkj.uk.agent.PhoneActions
import yyzs.lqkj.uk.speech.SpeechOutput
import yyzs.lqkj.uk.speech.VoiceInput

/** The assistant as a normal full-screen chat inside the app, with the same saved conversations as the panel. */
class ChatActivity : ComponentActivity() {
    private lateinit var controller: AssistController
    private lateinit var voice: VoiceInput
    private lateinit var speech: SpeechOutput
    private var listening by mutableStateOf(false)
    private var partial by mutableStateOf("")
    private var micLevel by mutableFloatStateOf(0f)
    private var drawerOpen by mutableStateOf(false)

    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) controller.message = "需要麦克风权限才能语音输入"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        speech = SpeechOutput(this)
        val actions = PhoneActions(applicationContext) {
            runOnUiThread { controller.message = "翻译屏幕要在别的应用里唤醒助手（长按电源键或快捷开关）" }
        }
        controller = AssistController(
            lifecycleScope, 0, AssistantAgent(actions),
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
        if (intent.getBooleanExtra(EXTRA_HISTORY, false)) drawerOpen = true

        setContent {
            AppTheme {
                ChatScreen(
                    controller,
                    drawerOpen = drawerOpen,
                    onDrawer = { open ->
                        if (open) controller.refreshHistory()
                        drawerOpen = open
                    },
                    listening = listening,
                    voiceLevel = micLevel,
                    partial = partial,
                    onMicDown = ::startVoice,
                    onMicUp = { if (listening) voice.stop() },
                    onBack = ::finish,
                    onSpeak = { speech.speak(it) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        controller.refreshHistory()
    }

    override fun onStop() {
        super.onStop()
        speech.stop()
    }

    override fun onDestroy() {
        voice.destroy()
        speech.shutdown()
        super.onDestroy()
    }

    private fun startVoice() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        speech.stop()
        controller.message = null
        partial = ""
        listening = true
        voice.start(Prefs.speechLang)
    }

    companion object {
        private const val EXTRA_HISTORY = "history"

        /** [history] opens straight into the list of past conversations. */
        fun intent(context: Context, history: Boolean = false): Intent =
            Intent(context, ChatActivity::class.java).putExtra(EXTRA_HISTORY, history)
    }
}

@Composable
private fun ChatScreen(
    controller: AssistController,
    drawerOpen: Boolean,
    onDrawer: (Boolean) -> Unit,
    listening: Boolean,
    voiceLevel: Float,
    partial: String,
    onMicDown: () -> Unit,
    onMicUp: () -> Unit,
    onBack: () -> Unit,
    onSpeak: (String) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    val title = controller.history.firstOrNull { it.id == controller.conversationId }?.title
        ?: controller.messages.firstOrNull { it.fromUser }?.text

    Box(Modifier.fillMaxSize().background(SheetColor)) {
        Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                IconButton(onClick = { onDrawer(true) }) { Icon(Icons.Filled.History, contentDescription = "历史对话") }
                Text(
                    title ?: "新对话",
                    fontSize = 17.sp, fontWeight = FontWeight.Medium, color = Color(0xFF111111),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                )
                IconButton(onClick = { controller.newConversation() }) { Icon(Icons.Filled.Add, contentDescription = "新对话") }
            }

            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp)) {
                if (controller.messages.isEmpty()) {
                    EmptyChat(Modifier.align(Alignment.Center))
                } else {
                    ChatList(controller, Dp.Infinity, onSpeak, Modifier.fillMaxSize())
                }
            }

            Column(
                Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AnimatedVisibility(controller.message != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    Notice(controller.message.orEmpty())
                }
                AnimatedVisibility(listening, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    Surface(shape = RoundedCornerShape(16.dp), color = Color.White) {
                        Text(
                            partial.ifEmpty { "我在听，请说…" },
                            color = if (partial.isEmpty()) Color(0xFF888888) else Color(0xFF222222),
                            fontSize = 15.sp, lineHeight = 21.sp,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp).animateContentSize(),
                        )
                    }
                }
                Row(Modifier.padding(start = 2.dp)) {
                    Chip(Icons.Filled.Psychology, "深度思考", selected = controller.thinking) { controller.toggleThinking() }
                }
                InputBar(
                    input = input,
                    onInput = { input = it },
                    listening = listening,
                    voiceLevel = voiceLevel,
                    busy = controller.busy,
                    onSend = { controller.send(input); input = "" },
                    onStop = { controller.stop() },
                    onMicDown = onMicDown,
                    onMicUp = onMicUp,
                    onScan = null,
                )
            }
        }

        BackHandler(enabled = drawerOpen) { onDrawer(false) }
        AnimatedVisibility(drawerOpen, enter = fadeIn(tween(220)), exit = fadeOut(tween(200))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x55000000))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDrawer(false) },
            )
        }
        AnimatedVisibility(
            drawerOpen,
            enter = slideInHorizontally(spring(dampingRatio = 0.86f, stiffness = 420f)) { -it } + fadeIn(tween(160)),
            exit = slideOutHorizontally(tween(220, easing = FastOutSlowInEasing)) { -it } + fadeOut(tween(200)),
        ) {
            HistoryDrawer(
                controller,
                onOpen = { controller.open(it); onDrawer(false) },
                onNew = { controller.newConversation(); onDrawer(false) },
            )
        }
    }
}

@Composable
private fun EmptyChat(modifier: Modifier = Modifier) {
    Column(modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        AssistOrb(OrbMood.Idle, 0f, Modifier.size(96.dp))
        Spacer(Modifier.size(12.dp))
        Text("有什么可以帮你？", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Color(0xFF222222))
        Spacer(Modifier.size(6.dp))
        Text(
            "可以问问题、让我联网查资料，也可以让我打开应用、定闹钟。左上角能找到以前的对话。",
            fontSize = 13.sp, lineHeight = 19.sp, color = Color(0xFF888888), textAlign = TextAlign.Center,
        )
    }
}
