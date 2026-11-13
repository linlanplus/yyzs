package yyzs.lqkj.uk

import android.Manifest
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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import yyzs.lqkj.uk.agent.AssistantAgent
import yyzs.lqkj.uk.agent.PhoneActions
import yyzs.lqkj.uk.assist.AssistService
import yyzs.lqkj.uk.assist.ScreenAccessibilityService
import yyzs.lqkj.uk.speech.SpeechOutput
import yyzs.lqkj.uk.speech.VoiceInput
import yyzs.lqkj.uk.ui.Accent
import yyzs.lqkj.uk.ui.AppTheme
import yyzs.lqkj.uk.ui.AssistController
import yyzs.lqkj.uk.ui.AssistOrb
import yyzs.lqkj.uk.ui.ChatList
import yyzs.lqkj.uk.ui.ChatStore
import yyzs.lqkj.uk.ui.Chip
import yyzs.lqkj.uk.ui.HistoryDrawer
import yyzs.lqkj.uk.ui.InputBar
import yyzs.lqkj.uk.ui.Notice
import yyzs.lqkj.uk.ui.OrbMood
import yyzs.lqkj.uk.ui.SheetColor

/** Something the user still has to set up, with the settings page that fixes it. */
private class SetupHint(val text: String, val page: SettingsActivity.Page)

/** Home screen: the assistant as a full-screen chat, with the same saved conversations as the panel. */
class MainActivity : ComponentActivity() {
    private lateinit var controller: AssistController
    private lateinit var voice: VoiceInput
    private lateinit var speech: SpeechOutput
    private var listening by mutableStateOf(false)
    private var partial by mutableStateOf("")
    private var micLevel by mutableFloatStateOf(0f)
    private var drawerOpen by mutableStateOf(false)
    private var setupHint by mutableStateOf<SetupHint?>(null)

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
                    setupHint = setupHint,
                    onMicDown = ::startVoice,
                    onMicUp = { if (listening) voice.stop() },
                    onSettings = { page -> startActivity(SettingsActivity.intent(this, page)) },
                    onSpeak = { speech.speak(it) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        controller.refreshHistory()
        setupHint = when {
            Prefs.apiKey.isBlank() -> SetupHint("还没有填写对话模型的 API Key，助手暂时无法回答", SettingsActivity.Page.Api)
            !AssistService.isActive(this) && !ScreenAccessibilityService.isEnabled(this) ->
                SetupHint("还没有开启唤醒方式，无法在其他应用里翻译屏幕", SettingsActivity.Page.Wake)
            else -> null
        }
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
}

@Composable
private fun ChatScreen(
    controller: AssistController,
    drawerOpen: Boolean,
    onDrawer: (Boolean) -> Unit,
    listening: Boolean,
    voiceLevel: Float,
    partial: String,
    setupHint: SetupHint?,
    onMicDown: () -> Unit,
    onMicUp: () -> Unit,
    onSettings: (SettingsActivity.Page?) -> Unit,
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
                IconButton(onClick = { onDrawer(true) }) { Icon(Icons.Filled.History, contentDescription = "历史对话") }
                Text(
                    title ?: "翻译助手",
                    fontSize = 17.sp, fontWeight = FontWeight.Medium, color = Color(0xFF111111),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                )
                IconButton(onClick = { onSettings(null) }) { Icon(Icons.Outlined.Settings, contentDescription = "设置") }
            }

            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp)) {
                if (controller.messages.isEmpty()) {
                    EmptyChat(setupHint, onSettings, Modifier.align(Alignment.Center))
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
private fun EmptyChat(setupHint: SetupHint?, onSettings: (SettingsActivity.Page?) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        AssistOrb(OrbMood.Idle, 0f, Modifier.size(96.dp))
        Spacer(Modifier.size(12.dp))
        Text("有什么可以帮你？", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Color(0xFF222222))
        Spacer(Modifier.size(6.dp))
        Text(
            "可以问问题、让我联网查资料，也可以让我打开应用、定闹钟。左上角能找到以前的对话。",
            fontSize = 13.sp, lineHeight = 19.sp, color = Color(0xFF888888), textAlign = TextAlign.Center,
        )
        if (setupHint != null) {
            Spacer(Modifier.size(20.dp))
            Surface(shape = RoundedCornerShape(14.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(setupHint.text, fontSize = 13.sp, lineHeight = 19.sp, color = Color(0xFF444444), modifier = Modifier.weight(1f))
                    TextButton(onClick = { onSettings(setupHint.page) }) { Text("去设置", color = Accent) }
                }
            }
        }
    }
}
