package yyzs.lqkj.uk

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import yyzs.lqkj.uk.ui.AssistantLogo
import yyzs.lqkj.uk.ui.ChatActivity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.net.Uri
import yyzs.lqkj.uk.agent.InstalledApps
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import yyzs.lqkj.uk.assist.AssistService
import yyzs.lqkj.uk.assist.ScreenAccessibilityService
import yyzs.lqkj.uk.assist.TriggerActivity
import yyzs.lqkj.uk.speech.Recognizers
import yyzs.lqkj.uk.translate.Translator
import yyzs.lqkj.uk.ui.AppTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var isAssistant by mutableStateOf(false)
    private var accessibilityOn by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                SettingsScreen(isAssistant, accessibilityOn, ::openAssistantSettings) {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isAssistant = AssistService.isActive(this)
        accessibilityOn = ScreenAccessibilityService.isEnabled(this)
    }

    private fun openAssistantSettings() {
        val candidates = listOf(
            Intent(Settings.ACTION_VOICE_INPUT_SETTINGS),
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
        )
        for (intent in candidates) {
            if (runCatching { startActivity(intent) }.isSuccess) return
        }
    }
}

@Composable
private fun SettingsScreen(
    isAssistant: Boolean,
    accessibilityOn: Boolean,
    openAssistantSettings: () -> Unit,
    openAccessibilitySettings: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var apiKey by remember { mutableStateOf(Prefs.apiKey) }
    var baseUrl by remember { mutableStateOf(Prefs.baseUrl) }
    var model by remember { mutableStateOf(Prefs.model) }
    var target by remember { mutableStateOf(Prefs.targetLang) }
    var secondary by remember { mutableStateOf(Prefs.secondaryLang) }
    var speechLang by remember { mutableStateOf(Prefs.speechLang) }
    var speechService by remember { mutableStateOf(Prefs.speechService) }
    var autoTranslate by remember { mutableStateOf(Prefs.autoTranslateOnWake) }
    var deepDefault by remember { mutableStateOf(Prefs.deepByDefault) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var openaiKey by remember { mutableStateOf(Prefs.openaiKey) }
    var liveUrl by remember { mutableStateOf(Prefs.liveUrl) }
    var liveModel by remember { mutableStateOf(Prefs.liveModel) }
    var liveVoice by remember { mutableStateOf(Prefs.liveVoice) }
    var speakReplies by remember { mutableStateOf(Prefs.speakReplies) }
    val recognizers = remember { Recognizers.options(context) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("翻译助手", style = MaterialTheme.typography.headlineSmall)

            Section("对话") {
                Text(
                    "在应用里直接和助手聊天，或者翻看、接着之前的对话。唤醒面板里的对话也会保存在这里。",
                    fontSize = 13.sp, color = Color(0xFF666666),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { context.startActivity(ChatActivity.intent(context)) }) { Text("开始对话") }
                    OutlinedButton(onClick = { context.startActivity(ChatActivity.intent(context, history = true)) }) { Text("历史对话") }
                }
            }

            Section("唤醒方式") {
                StatusLine("无障碍截图（推荐）", accessibilityOn)
                Text(
                    "开启“翻译助手 屏幕截图”无障碍服务后，可以用下拉菜单里的“屏幕翻译”快捷开关或无障碍按钮唤醒。" +
                        "这条路不依赖默认助理，不会被 YOYO 抢走。",
                    fontSize = 13.sp, color = Color(0xFF666666),
                )
                Button(onClick = openAccessibilitySettings) { Text(if (accessibilityOn) "无障碍设置" else "开启无障碍服务") }

                StatusLine("默认数字助理", isAssistant)
                Text(
                    "设为默认数字助理后，系统的助理手势（如长按电源键）会打开翻译助手。" +
                        "荣耀手机上 YOYO 会把语音服务改回自己，长按电源键随之失效；荣耀手机请用无障碍截图。",
                    fontSize = 13.sp, color = Color(0xFF666666),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = openAssistantSettings) { Text("设置默认数字助理") }
                    if (isAssistant || accessibilityOn) OutlinedButton(onClick = { TriggerActivity.capture() }) { Text("试一试") }
                }
            }

            Section("应用列表") { AppListPermission() }

            Section("助手形象") { LogoPicker() }

            Section("DeepSeek 接口") {
                OutlinedTextField(
                    value = apiKey, onValueChange = { apiKey = it; Prefs.apiKey = it },
                    label = { Text("API Key") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = baseUrl, onValueChange = { baseUrl = it; Prefs.baseUrl = it },
                    label = { Text("接口地址") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = model, onValueChange = { model = it; Prefs.model = it },
                    label = { Text("模型（deepseek-flash 即 V4.1，支持图片）") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = {
                        testResult = "测试中…"
                        scope.launch {
                            testResult = runCatching {
                                val sb = StringBuilder()
                                Translator.translateText("Hello, how are you today?", "zh-Hans").collect { sb.append(it) }
                                "连接正常：$sb"
                            }.getOrElse { it.message ?: "失败" }
                        }
                    }) { Text("测试连接") }
                    Spacer(Modifier.padding(4.dp))
                    testResult?.let { Text(it, fontSize = 13.sp, modifier = Modifier.weight(1f)) }
                }
            }

            Section("语音对话（GPT-Live）") {
                Text(
                    "面板里点“语音对话”进入实时语音聊天：GPT-Live 负责说话，打开应用、闹钟等操作交给 DeepSeek 执行。按分钟计费，约 0.05 美元/分钟。",
                    fontSize = 13.sp, color = Color(0xFF666666),
                )
                OutlinedTextField(
                    value = openaiKey, onValueChange = { openaiKey = it; Prefs.openaiKey = it },
                    label = { Text("OpenAI API Key") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = liveUrl, onValueChange = { liveUrl = it; Prefs.liveUrl = it },
                    label = { Text("接口地址（WebSocket）") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = liveModel, onValueChange = { liveModel = it; Prefs.liveModel = it },
                        label = { Text("模型") }, singleLine = true, modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = liveVoice, onValueChange = { liveVoice = it; Prefs.liveVoice = it },
                        label = { Text("音色") }, singleLine = true, modifier = Modifier.weight(1f),
                    )
                }
                SwitchRow("语音提问时朗读回复（系统语音）", speakReplies) { speakReplies = it; Prefs.speakReplies = it }
            }

            Section("翻译") {
                Picker("译成", Languages.targets.map { it.code to it.label }, target) { target = it; Prefs.targetLang = it }
                Picker("原文已是目标语言时译成", Languages.targets.map { it.code to it.label }, secondary) {
                    secondary = it; Prefs.secondaryLang = it
                }
                SwitchRow("唤醒后自动翻译屏幕", autoTranslate) { autoTranslate = it; Prefs.autoTranslateOnWake = it }
                SwitchRow("自动翻译时使用深度翻译", deepDefault) { deepDefault = it; Prefs.deepByDefault = it }
                Text(
                    "快速翻译：本机识别文字，只把文字发给模型。深度翻译：同时发送截图，模型能纠正识别错误、结合画面理解语境，耗时和费用更高。",
                    fontSize = 13.sp, color = Color(0xFF666666),
                )
            }

            Section("语音输入") {
                Picker("说话语言", Languages.speech, speechLang) { speechLang = it; Prefs.speechLang = it }
                Picker("识别服务", recognizers.map { it.id to it.label }, speechService) {
                    speechService = it; Prefs.speechService = it
                }
            }

            Text(
                "版本 ${appVersion(context)} · ${context.packageName}",
                fontSize = 12.sp, color = Color(0xFF999999),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun appVersion(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"

/** Preview of the assistant picture with buttons to replace it or go back to the built-in one. */
@Composable
private fun LogoPicker() {
    val context = LocalContext.current
    val logo = AssistantLogo.bitmap()
    var custom by remember { mutableStateOf(AssistantLogo.isCustom(context)) }
    var error by remember { mutableStateOf<String?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        error = runCatching { AssistantLogo.set(context, uri) }.exceptionOrNull()?.let { "无法读取这张图片：${it.message}" }
        custom = AssistantLogo.isCustom(context)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFFEDEBF7)),
            contentAlignment = Alignment.Center,
        ) {
            Image(logo, contentDescription = "助手形象", modifier = Modifier.size(60.dp), contentScale = ContentScale.Fit)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
            Text(
                "唤醒时显示的图片。建议使用透明背景的 PNG 或 WebP，主体居中、接近正方形。",
                fontSize = 13.sp, color = Color(0xFF666666),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text("选择图片") }
                if (custom) TextButton(onClick = { AssistantLogo.reset(context); custom = false }) { Text("恢复默认") }
            }
        }
    }
    error?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.error) }
}

/** The ROM's app-list permission, so the assistant can find apps by nickname or a vague name. */
@Composable
private fun AppListPermission() {
    val context = LocalContext.current
    val defined = remember { InstalledApps.permissionDefined(context) }
    var granted by remember { mutableStateOf(InstalledApps.permissionGranted(context)) }
    var denied by remember { mutableStateOf(false) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        denied = !it
    }
    StatusLine("读取已安装应用", granted)
    Text(
        if (defined) "授权后，助手能看到你安装的应用（不含系统应用）的名称，说“打开B站”“开一下那个外卖软件”也能找到对应的应用。列表只在本机用于匹配，并随对话发给 DeepSeek。"
        else "这台手机不需要单独授权，助手可以直接看到已安装的应用。",
        fontSize = 13.sp, color = Color(0xFF666666),
    )
    if (defined && !granted) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { request.launch(InstalledApps.PERMISSION) }) { Text("授权") }
            // After a refusal the ROM may stop showing the dialog; the switch is in the app's permission page.
            if (denied) OutlinedButton(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                )
            }) { Text("去系统设置") }
        }
    }
}

@Composable
private fun StatusLine(label: String, on: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(if (on) "已开启" else "未开启", color = if (on) Color(0xFF1B7F3B) else MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun Picker(label: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Column {
            TextButton(onClick = { open = true }) {
                Text(options.firstOrNull { it.first == selected }?.second ?: options.first().second)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                for ((id, name) in options) DropdownMenuItem(text = { Text(name) }, onClick = { open = false; onSelect(id) })
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
