package yyzs.lqkj.uk

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import yyzs.lqkj.uk.agent.InstalledApps
import yyzs.lqkj.uk.assist.AssistService
import yyzs.lqkj.uk.assist.ScreenAccessibilityService
import yyzs.lqkj.uk.assist.TriggerActivity
import yyzs.lqkj.uk.speech.Recognizers
import yyzs.lqkj.uk.translate.Translator
import yyzs.lqkj.uk.ui.AppTheme
import yyzs.lqkj.uk.ui.AssistantLogo

/** Settings: a list of categories, each opening its own page. */
class SettingsActivity : ComponentActivity() {
    enum class Page(val title: String) {
        Wake("唤醒方式"), Translate("翻译"), Voice("语音"), Look("助手形象"), Apps("应用列表"), Api("模型接口"),
    }

    private var isAssistant by mutableStateOf(false)
    private var accessibilityOn by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val initial = intent.getStringExtra(EXTRA_PAGE)?.let { name -> Page.entries.firstOrNull { it.name == name } }
        setContent {
            AppTheme {
                var page by rememberSaveable { mutableStateOf(initial) }
                BackHandler(enabled = page != null) { page = null }
                // The arrow goes through the dispatcher so a page with unsaved edits can intercept it.
                SettingsScaffold(page?.title ?: "设置", onBack = { onBackPressedDispatcher.onBackPressed() }) {
                    when (page) {
                        null -> SettingsList(isAssistant, accessibilityOn) { page = it }
                        Page.Wake -> WakePage(isAssistant, accessibilityOn, ::openAssistantSettings) {
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }
                        Page.Translate -> TranslatePage()
                        Page.Voice -> VoicePage()
                        Page.Look -> Section { LogoPicker() }
                        Page.Apps -> Section { AppListPermission() }
                        Page.Api -> ApiPage()
                    }
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

    companion object {
        private const val EXTRA_PAGE = "page"

        /** [page] opens straight into one category; null shows the list. */
        fun intent(context: Context, page: Page? = null): Intent =
            Intent(context, SettingsActivity::class.java).putExtra(EXTRA_PAGE, page?.name)
    }
}

@Composable
private fun SettingsScaffold(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = Color(0xFF111111))
            }
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun SettingsList(isAssistant: Boolean, accessibilityOn: Boolean, open: (SettingsActivity.Page) -> Unit) {
    val context = LocalContext.current
    val warn = MaterialTheme.colorScheme.error
    val target = Languages.targets.firstOrNull { it.code == Prefs.targetLang }?.label ?: Prefs.targetLang
    val speechLang = Languages.speech.firstOrNull { it.first == Prefs.speechLang }?.second ?: Prefs.speechLang
    val appsDefined = remember { InstalledApps.permissionDefined(context) }

    Group {
        Entry(
            "唤醒方式",
            when {
                accessibilityOn -> "无障碍截图已开启"
                isAssistant -> "默认数字助理已开启"
                else -> "未开启"
            },
            summaryColor = if (accessibilityOn || isAssistant) null else warn,
        ) { open(SettingsActivity.Page.Wake) }
        EntryDivider()
        Entry("翻译", "译成$target" + if (Prefs.deepByDefault) " · 深度翻译" else "") { open(SettingsActivity.Page.Translate) }
        EntryDivider()
        Entry("语音", speechLang) { open(SettingsActivity.Page.Voice) }
    }
    Group {
        Entry("助手形象", if (AssistantLogo.isCustom(context)) "自定义图片" else "默认") { open(SettingsActivity.Page.Look) }
        EntryDivider()
        Entry(
            "应用列表",
            when {
                !appsDefined -> "无需授权"
                InstalledApps.permissionGranted(context) -> "已授权"
                else -> "未授权"
            },
        ) { open(SettingsActivity.Page.Apps) }
    }
    Group {
        Entry(
            "模型接口",
            if (Prefs.apiKey.isBlank()) "未填写对话模型的 API Key" else Prefs.model,
            summaryColor = if (Prefs.apiKey.isBlank()) warn else null,
        ) { open(SettingsActivity.Page.Api) }
    }
    Group { UpdateEntry() }

    Text(
        "bug及技术问题反馈：support@lqstudio.uk",
        fontSize = 12.sp, color = Color(0xFF999999), textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}

@Composable
private fun UpdateEntry() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val version = remember { appVersion(context) }
    var status by remember { mutableStateOf<String?>(null) }
    var available by remember { mutableStateOf<UpdateChecker.Result.Available?>(null) }
    Entry("检查更新", status ?: "当前版本 $version") {
        available?.let { update ->
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.url))) }
            return@Entry
        }
        if (status == "检查中…") return@Entry
        status = "检查中…"
        scope.launch {
            status = runCatching { UpdateChecker.check(version) }.fold(
                onSuccess = { result ->
                    when (result) {
                        UpdateChecker.Result.NotConfigured -> "暂未开放在线更新 · 当前版本 $version"
                        UpdateChecker.Result.UpToDate -> "已是最新版本 $version"
                        is UpdateChecker.Result.Available -> {
                            available = result
                            "发现新版本 ${result.version}，点击下载"
                        }
                    }
                },
                onFailure = { "检查失败：${it.message ?: "网络错误"}" },
            )
        }
    }
}

@Composable
private fun WakePage(
    isAssistant: Boolean,
    accessibilityOn: Boolean,
    openAssistantSettings: () -> Unit,
    openAccessibilitySettings: () -> Unit,
) {
    Section {
        StatusLine("无障碍截图（推荐）", accessibilityOn)
        Hint(
            "开启“翻译助手 屏幕截图”无障碍服务后，可以用下拉菜单里的“屏幕翻译”快捷开关或无障碍按钮唤醒。" +
                "这条路不依赖默认助理，不会被 YOYO 抢走。",
        )
        Button(onClick = openAccessibilitySettings) { Text(if (accessibilityOn) "无障碍设置" else "开启无障碍服务") }
    }
    Section {
        StatusLine("默认数字助理", isAssistant)
        Hint(
            "设为默认数字助理后，系统的助理手势（如长按电源键）会打开翻译助手。" +
                "荣耀手机上 YOYO 会把语音服务改回自己，长按电源键随之失效；荣耀手机请用无障碍截图。",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = openAssistantSettings) { Text("设置默认数字助理") }
            if (isAssistant || accessibilityOn) OutlinedButton(onClick = { TriggerActivity.capture() }) { Text("试一试") }
        }
    }
}

@Composable
private fun TranslatePage() {
    var target by remember { mutableStateOf(Prefs.targetLang) }
    var secondary by remember { mutableStateOf(Prefs.secondaryLang) }
    var autoTranslate by remember { mutableStateOf(Prefs.autoTranslateOnWake) }
    var deepDefault by remember { mutableStateOf(Prefs.deepByDefault) }
    var streamTranslation by remember { mutableStateOf(Prefs.streamScreenTranslation) }
    Section {
        Picker("译成", Languages.targets.map { it.code to it.label }, target) { target = it; Prefs.targetLang = it }
        Picker("原文已是目标语言时译成", Languages.targets.map { it.code to it.label }, secondary) {
            secondary = it; Prefs.secondaryLang = it
        }
    }
    Section {
        SwitchRow("唤醒后自动翻译屏幕", autoTranslate) { autoTranslate = it; Prefs.autoTranslateOnWake = it }
        SwitchRow("自动翻译时使用深度翻译", deepDefault) { deepDefault = it; Prefs.deepByDefault = it }
        Hint("快速翻译：本机识别文字，只把文字发给模型。深度翻译：同时发送截图，模型能纠正识别错误、结合画面理解语境，耗时和费用更高。")
    }
    Section {
        SwitchRow("流式输出", streamTranslation) { streamTranslation = it; Prefs.streamScreenTranslation = it }
        Hint("开启时逐段显示屏幕译文；关闭时，等全部翻译完成后一次显示。更改将在下一次翻译生效。")
    }
}

@Composable
private fun VoicePage() {
    val context = LocalContext.current
    var speechLang by remember { mutableStateOf(Prefs.speechLang) }
    var speechService by remember { mutableStateOf(Prefs.speechService) }
    var speakReplies by remember { mutableStateOf(Prefs.speakReplies) }
    val recognizers = remember { Recognizers.options(context) }
    Section {
        Picker("说话语言", Languages.speech, speechLang) { speechLang = it; Prefs.speechLang = it }
        Picker("识别服务", recognizers.map { it.id to it.label }, speechService) {
            speechService = it; Prefs.speechService = it
        }
        SwitchRow("语音提问时朗读回复（系统语音）", speakReplies) { speakReplies = it; Prefs.speakReplies = it }
    }
}

/** Endpoint fields are edited as a draft and only written to [Prefs] by the save button. */
private data class ApiDraft(
    val apiKey: String, val baseUrl: String, val model: String,
    val liveKey: String, val liveUrl: String, val liveModel: String, val liveVoice: String,
) {
    fun save() {
        Prefs.apiKey = apiKey; Prefs.baseUrl = baseUrl; Prefs.model = model
        Prefs.openaiKey = liveKey; Prefs.liveUrl = liveUrl; Prefs.liveModel = liveModel; Prefs.liveVoice = liveVoice
    }

    companion object {
        fun load() = ApiDraft(Prefs.apiKey, Prefs.baseUrl, Prefs.model, Prefs.openaiKey, Prefs.liveUrl, Prefs.liveModel, Prefs.liveVoice)
    }
}

@Composable
private fun ApiPage() {
    val scope = rememberCoroutineScope()
    val back = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    var saved by remember { mutableStateOf(ApiDraft.load()) }
    var draft by remember { mutableStateOf(saved) }
    var justSaved by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var confirmLeave by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    val dirty = draft != saved

    fun edit(change: ApiDraft) { draft = change; justSaved = false }
    fun save() {
        draft.save()
        // Reload so blank fields show the defaults the app will actually use.
        saved = ApiDraft.load()
        draft = saved
        justSaved = true
    }

    BackHandler(enabled = dirty && !leaving) { confirmLeave = true }
    // Leave only after recomposition has disabled the handler above, or it would catch this back press too.
    LaunchedEffect(leaving) { if (leaving) back?.onBackPressed() }
    if (confirmLeave) AlertDialog(
        onDismissRequest = { confirmLeave = false },
        title = { Text("有未保存的修改") },
        text = { Text("离开前要保存吗？") },
        confirmButton = { TextButton(onClick = { confirmLeave = false; save(); leaving = true }) { Text("保存") } },
        dismissButton = { TextButton(onClick = { confirmLeave = false; draft = saved; leaving = true }) { Text("不保存") } },
    )

    Section("对话模型") {
        Hint("兼容 OpenAI Chat Completions 格式的接口都可以接入，默认预填 DeepSeek。深度翻译需要模型支持图片输入。")
        OutlinedTextField(
            value = draft.apiKey, onValueChange = { edit(draft.copy(apiKey = it)) },
            label = { Text("API Key") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.baseUrl, onValueChange = { edit(draft.copy(baseUrl = it)) },
            label = { Text("接口地址") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.model, onValueChange = { edit(draft.copy(model = it)) },
            label = { Text("模型") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = {
                if (dirty) save()
                testResult = "测试中…"
                scope.launch {
                    testResult = runCatching {
                        val sb = StringBuilder()
                        Translator.translateText("Hello, how are you today?", "zh-Hans").collect { sb.append(it) }
                        "连接正常：$sb"
                    }.getOrElse { it.message ?: "失败" }
                }
            }) { Text(if (dirty) "保存并测试" else "测试连接") }
            Spacer(Modifier.padding(4.dp))
            testResult?.let { Text(it, fontSize = 13.sp, modifier = Modifier.weight(1f)) }
        }
    }

    Section("实时语音模型") {
        Hint(
            "面板里点“语音对话”进入实时语音聊天：实时语音模型负责说话，打开应用、闹钟等操作交给对话模型执行。" +
                "与 GPT-Live 接口格式兼容的服务都可以接入，默认预填 GPT-Live。费用以服务商为准，GPT-Live 约 0.05 美元/分钟。",
        )
        OutlinedTextField(
            value = draft.liveKey, onValueChange = { edit(draft.copy(liveKey = it)) },
            label = { Text("API Key") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.liveUrl, onValueChange = { edit(draft.copy(liveUrl = it)) },
            label = { Text("接口地址（WebSocket）") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = draft.liveModel, onValueChange = { edit(draft.copy(liveModel = it)) },
                label = { Text("模型") }, singleLine = true, modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = draft.liveVoice, onValueChange = { edit(draft.copy(liveVoice = it)) },
                label = { Text("音色") }, singleLine = true, modifier = Modifier.weight(1f),
            )
        }
    }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = ::save, enabled = dirty) { Text("保存") }
        Spacer(Modifier.size(12.dp))
        when {
            dirty -> Text("有未保存的修改", fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
            justSaved -> Text("已保存", fontSize = 13.sp, color = Color(0xFF1B7F3B))
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
            Hint("唤醒时显示的图片。建议使用透明背景的 PNG 或 WebP，主体居中、接近正方形。")
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
    Hint(
        if (defined) "授权后，助手能看到你安装的应用（不含系统应用）的名称，说“打开B站”“开一下那个外卖软件”也能找到对应的应用。列表只在本机用于匹配，并随对话发给对话模型。"
        else "这台手机不需要单独授权，助手可以直接看到已安装的应用。",
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

/** A white card of [Entry] rows separated by hairlines. */
@Composable
private fun Group(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(content = content)
    }
}

@Composable
private fun EntryDivider() = HorizontalDivider(Modifier.padding(start = 16.dp), color = Color(0xFFEEEEEE))

@Composable
private fun Entry(title: String, summary: String, summaryColor: Color? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(summary, fontSize = 13.sp, color = summaryColor ?: Color(0xFF888888))
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color(0xFFBBBBBB))
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
private fun Hint(text: String) {
    Text(text, fontSize = 13.sp, color = Color(0xFF666666))
}

@Composable
private fun Section(title: String? = null, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            title?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
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
