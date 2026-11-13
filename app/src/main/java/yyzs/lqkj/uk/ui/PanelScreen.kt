package yyzs.lqkj.uk.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import yyzs.lqkj.uk.Languages
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.lerp as dpLerp
import kotlin.math.roundToInt
import androidx.activity.compose.BackHandler
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Logo size once docked beside the chips, and how much bigger it is at the centre of the screen. */
private val LogoSize = 44.dp
private const val LogoCenterScale = 2.2f
/** The panel's backing sheet; bubbles, chips and the input bar are white on it. */
internal val SheetColor = Color(0xFFF4F3FA)
private val SheetShape = RoundedCornerShape(28.dp)

/** How far above its docking slot the big logo first appears, i.e. just above the middle of the dialog. */
private val LogoLift = 64.dp

/** Entrance and exit progress of the panel's parts, all 0..1. */
class PanelMotion {
    val scrim = Animatable(0f)
    /** Logo pops up just above the middle of the dialog. */
    val pop = Animatable(0f)
    /** Logo glides from there to its place left of the chips. */
    val move = Animatable(0f)
    val ripple = Animatable(0f)
    val card = Animatable(0f)

    /** Logo pops up above the dialog, glides to the left, a wave spreads from it, then the dialog springs up. */
    suspend fun enter() = coroutineScope {
        launch { scrim.animateTo(1f, tween(300)) }
        launch { pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 320f)) }
        launch { delay(340); move.animateTo(1f, tween(460, easing = FastOutSlowInEasing)) }
        launch { delay(700); ripple.animateTo(1f, tween(720, easing = FastOutSlowInEasing)) }
        launch { delay(660); card.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 260f)) }
    }

    /** The reverse, but quicker: the dialog drops, the logo heads back to the middle and shrinks away. */
    suspend fun exit() = coroutineScope {
        launch { card.animateTo(0f, tween(150)) }
        launch { delay(60); move.animateTo(0f, tween(260, easing = FastOutSlowInEasing)) }
        launch { delay(170); pop.animateTo(0f, tween(180)) }
        launch { delay(80); scrim.animateTo(0f, tween(270)) }
    }
}

@Composable
fun PanelScreen(
    controller: AssistController,
    motion: PanelMotion,
    mood: OrbMood,
    listening: Boolean,
    voiceLevel: Float,
    partial: String,
    onMicDown: () -> Unit,
    onMicUp: () -> Unit,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    onStartLive: () -> Unit,
    onSpeak: (String) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    // Where the logo docks; unknown until the chips row has been laid out.
    var slot by remember { mutableStateOf<Offset?>(null) }
    var drawerOpen by remember { mutableStateOf(false) }
    // The panel is see-through when summoned; the sheet only comes in once there is a conversation.
    val sheet by animateFloatAsState(if (controller.messages.isNotEmpty()) 1f else 0f, tween(320, easing = FastOutSlowInEasing), label = "sheet")
    val density = LocalDensity.current
    val maxChat = (LocalConfiguration.current.screenHeightDp * 0.5f).dp

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { rootSize = it }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = motion.scrim.value }
                .background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color(0xB36A5AE0))),
        )
        slot?.let { DiffusionRipple(motion.ripple.value, it, Modifier.fillMaxSize()) }

        // Fills the screen below the history button so a long chat shrinks instead of running off the top.
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 64.dp)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
        Box(
            Modifier
                .weight(1f, fill = false)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
        // One solid sheet behind the chat, chips and input, so they read as a single layer above the app.
        // Only the sheet animates here; the logo slot inside must not move while the logo flies to it.
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    val p = motion.card.value.coerceIn(0f, 1f) * sheet
                    alpha = p
                    scaleY = 0.9f + 0.1f * p
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }
                .shadow(16.dp, SheetShape, ambientColor = Color(0x556A5AE0), spotColor = Color(0x556A5AE0))
                .background(SheetColor, SheetShape),
        )
        Column(
            Modifier.padding(dpLerp(4.dp, 10.dp, sheet)),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Dialog: messages, notices and the live transcript of hold-to-talk.
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .graphicsLayer {
                        val p = motion.card.value
                        alpha = p.coerceIn(0f, 1f)
                        translationY = (1f - p) * with(density) { 80.dp.toPx() }
                        scaleX = 0.92f + 0.08f * p
                        scaleY = 0.92f + 0.08f * p
                        transformOrigin = TransformOrigin(0.1f, 1f)
                    },
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AnimatedVisibility(controller.message != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    Notice(controller.message.orEmpty())
                }
                if (controller.messages.isNotEmpty()) ChatList(controller, maxChat, onSpeak, Modifier.weight(1f, fill = false))
                AnimatedVisibility(listening, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    Surface(shape = RoundedCornerShape(16.dp), color = Color.White) {
                        Text(
                            partial.ifEmpty { "我在听，请说…" },
                            color = if (partial.isEmpty()) Color(0xFF888888) else Color(0xFF222222),
                            fontSize = 15.sp,
                            lineHeight = 21.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp).animateContentSize(),
                        )
                    }
                }
            }

            // Logo slot + quick actions.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.size(LogoSize).onGloballyPositioned { slot = it.boundsInRoot().center })
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(start = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StaggeredChip(motion, 0) {
                        Chip(Icons.Filled.Psychology, "深度思考", selected = controller.thinking) { controller.toggleThinking() }
                    }
                    val chips = listOf<Triple<ImageVector, String, () -> Unit>>(
                        Triple(Icons.Filled.Translate, "翻译屏幕") { controller.translateScreen(deep = false) },
                        Triple(Icons.Filled.AutoAwesome, "深度翻译") { controller.translateScreen(deep = true) },
                        Triple(Icons.Filled.GraphicEq, "语音对话", onStartLive),
                        Triple(Icons.Filled.Settings, "设置", onOpenSettings),
                    )
                    chips.forEachIndexed { i, (icon, label, action) ->
                        val index = if (i < 2) i + 1 else i + 2
                        StaggeredChip(motion, index) { Chip(icon, label, onClick = action) }
                        if (i == 1) StaggeredChip(motion, 3) { TargetChip(controller) }
                    }
                }
            }

            // Input bar; turns into a live waveform while the mic is held.
            Box(
                Modifier.graphicsLayer {
                    val p = motion.card.value
                    alpha = p.coerceIn(0f, 1f)
                    translationY = (1f - p) * 36.dp.toPx()
                },
            ) {
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
                    onScan = { controller.translateScreen(deep = false) },
                )
            }
        }
        }
        }

        // The logo, drawn above everything so it can travel from the centre to its slot.
        // It is laid out at its large size and scaled down when docked, which keeps the picture sharp.
        val bigPx = with(density) { (LogoSize * LogoCenterScale).toPx() }
        AssistOrb(
            mood, voiceLevel,
            Modifier
                .size(LogoSize * LogoCenterScale)
                .offset {
                    val start = slot?.let { Offset(rootSize.width / 2f, it.y - LogoLift.toPx()) }
                        ?: Offset(rootSize.width / 2f, rootSize.height * 0.8f)
                    val pos = lerp(start, slot ?: start, motion.move.value)
                    IntOffset((pos.x - bigPx / 2).roundToInt(), (pos.y - bigPx / 2).roundToInt())
                }
                .graphicsLayer {
                    val s = motion.pop.value * (1f + (1f / LogoCenterScale - 1f) * motion.move.value)
                    scaleX = s; scaleY = s
                    alpha = motion.pop.value.coerceIn(0f, 1f)
                }
                // Tapping the logo should not count as tapping outside the panel.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        )

        // Past conversations, top left.
        Surface(
            shape = CircleShape,
            color = Color.White,
            shadowElevation = 2.dp,
            onClick = {
                controller.refreshHistory()
                drawerOpen = true
            },
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(12.dp)
                .size(40.dp)
                .graphicsLayer {
                    val p = motion.card.value.coerceIn(0f, 1f)
                    alpha = p
                    translationX = (1f - p) * -24.dp.toPx()
                },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.History, contentDescription = "历史对话", tint = Color(0xFF222222), modifier = Modifier.size(20.dp))
            }
        }

        BackHandler(enabled = drawerOpen) { drawerOpen = false }
        AnimatedVisibility(drawerOpen, enter = fadeIn(tween(220)), exit = fadeOut(tween(200))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x55000000))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { drawerOpen = false },
            )
        }
        AnimatedVisibility(
            drawerOpen,
            enter = slideInHorizontally(spring(dampingRatio = 0.86f, stiffness = 420f)) { -it } + fadeIn(tween(160)),
            exit = slideOutHorizontally(tween(220, easing = FastOutSlowInEasing)) { -it } + fadeOut(tween(200)),
        ) {
            HistoryDrawer(
                controller,
                onOpen = { controller.open(it); drawerOpen = false },
                onNew = { controller.newConversation(); drawerOpen = false },
            )
        }
    }
}

@Composable
internal fun HistoryDrawer(controller: AssistController, onOpen: (String) -> Unit, onNew: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
        color = Color.White,
        shadowElevation = 8.dp,
        modifier = Modifier
            .width(300.dp)
            .fillMaxHeight()
            // Taps inside the drawer must not reach the panel behind it.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        Column(Modifier.statusBarsPadding().navigationBarsPadding().padding(top = 12.dp)) {
            Text(
                "历史对话", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF111111),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFFF0EEFB),
                onClick = onNew,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Row(Modifier.height(44.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = Accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("新对话", fontSize = 14.sp, color = Accent)
                }
            }
            if (controller.history.isEmpty()) {
                Text("还没有对话记录", fontSize = 13.sp, color = Color(0xFF999999), modifier = Modifier.padding(20.dp))
            }
            LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp)) {
                items(controller.history, key = { it.id }) { chat ->
                    val current = chat.id == controller.conversationId
                    Row(
                        Modifier
                            .animateItem()
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 2.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (current) Color(0xFFF0EEFB) else Color.Transparent)
                            .clickable { onOpen(chat.id) }
                            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                chat.title.ifBlank { "（无标题）" }, fontSize = 14.sp, color = Color(0xFF222222),
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(relativeTime(chat.updatedAt), fontSize = 11.sp, color = Color(0xFF999999))
                        }
                        IconButton(onClick = { controller.delete(chat.id) }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "删除", tint = Color(0xFFAAAAAA), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun relativeTime(millis: Long): String {
    val diff = System.currentTimeMillis() - millis
    if (diff < 60_000) return "刚刚"
    if (diff < 3_600_000) return "${diff / 60_000} 分钟前"
    val then = Calendar.getInstance().apply { timeInMillis = millis }
    val today = Calendar.getInstance()
    val days = today.get(Calendar.DAY_OF_YEAR) - then.get(Calendar.DAY_OF_YEAR)
    val sameYear = today.get(Calendar.YEAR) == then.get(Calendar.YEAR)
    val time = SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(millis))
    return when {
        sameYear && days == 0 -> time
        sameYear && days == 1 -> "昨天 $time"
        sameYear -> SimpleDateFormat("M月d日", Locale.CHINA).format(Date(millis))
        else -> SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(Date(millis))
    }
}

@Composable
private fun StaggeredChip(motion: PanelMotion, index: Int, content: @Composable () -> Unit) {
    Box(
        Modifier.graphicsLayer {
            val p = ((motion.card.value - index * 0.08f) / 0.64f).coerceIn(0f, 1f)
            alpha = p
            translationX = (1f - p) * 30.dp.toPx()
            scaleX = 0.85f + 0.15f * p
            scaleY = 0.85f + 0.15f * p
        },
    ) { content() }
}

@Composable
internal fun InputBar(
    input: String,
    onInput: (String) -> Unit,
    listening: Boolean,
    voiceLevel: Float,
    busy: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onMicDown: () -> Unit,
    onMicUp: () -> Unit,
    /** The screen-translate button; left out where there is no screen to translate. */
    onScan: (() -> Unit)?,
) {
    val bg by animateColorAsState(if (listening) Accent else Color.White, tween(220), label = "bar")

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = bg,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedContent(listening, transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) }, label = "bar", modifier = Modifier.weight(1f)) { isListening ->
                if (isListening) {
                    Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        VoiceBars(voiceLevel, Modifier.weight(1f).height(24.dp), barWidth = 2.5.dp)
                        Spacer(Modifier.size(12.dp))
                        Text("松开发送", color = Color.White, fontSize = 13.sp)
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (onScan != null) {
                            IconButton(onClick = onScan, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Filled.DocumentScanner, contentDescription = "翻译屏幕", tint = Color(0xFF222222), modifier = Modifier.size(20.dp))
                            }
                        }
                        Box(Modifier.weight(1f).padding(start = if (onScan != null) 4.dp else 14.dp, end = 4.dp)) {
                            if (input.isEmpty()) Text("说点什么，或按住麦克风", color = Color(0xFF999999), fontSize = 14.sp)
                            BasicTextField(
                                value = input,
                                onValueChange = onInput,
                                textStyle = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, color = Color(0xFF222222)),
                                maxLines = 5,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = { onSend() }),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
            val action = when {
                busy && !listening -> 2
                input.isNotBlank() && !listening -> 1
                else -> 0
            }
            AnimatedContent(action, label = "action") { a ->
                if (a == 2) {
                    // Stops the reply being generated.
                    Box(
                        Modifier.size(38.dp).clip(CircleShape).background(Accent).clickable(onClick = onStop),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Stop, contentDescription = "停止", tint = Color.White, modifier = Modifier.size(18.dp)) }
                } else if (a == 1) {
                    IconButton(onClick = onSend, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送", tint = Accent, modifier = Modifier.size(20.dp))
                    }
                } else {
                    val scale by animateFloatAsState(if (listening) 1.18f else 1f, spring(dampingRatio = 0.5f, stiffness = 400f), label = "mic")
                    Box(
                        Modifier
                            .size(38.dp)
                            .graphicsLayer { scaleX = scale; scaleY = scale }
                            .clip(CircleShape)
                            .background(if (listening) Color.White.copy(alpha = 0.25f) else Color(0xFFF0EEFB))
                            .pointerInput(Unit) {
                                detectTapGestures(onPress = {
                                    onMicDown()
                                    tryAwaitRelease()
                                    onMicUp()
                                })
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Mic, contentDescription = "按住说话", tint = if (listening) Color.White else Accent, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
internal fun ChatList(controller: AssistController, maxHeight: Dp, onSpeak: (String) -> Unit, modifier: Modifier = Modifier) {
    val state = rememberLazyListState()
    val last = controller.messages.lastOrNull()
    LaunchedEffect(controller.messages.size, last?.text?.length, last?.actions?.size, last?.steps?.size, last?.status, last?.reasoning?.length) {
        if (controller.messages.isNotEmpty()) state.animateScrollToItem(controller.messages.lastIndex)
    }
    LazyColumn(
        state = state,
        modifier = modifier.fillMaxWidth().heightIn(max = maxHeight),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(controller.messages, key = { it.id }) { m ->
            Box(Modifier.fillMaxWidth().animateItem()) {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(220)) + scaleIn(
                        spring(dampingRatio = 0.7f, stiffness = 420f), initialScale = 0.85f,
                        transformOrigin = TransformOrigin(if (m.fromUser) 1f else 0f, 1f),
                    ),
                    modifier = Modifier.align(if (m.fromUser) Alignment.CenterEnd else Alignment.CenterStart),
                ) {
                    if (m.fromUser) UserBubble(m.text) else ReplyBubble(m, onSpeak)
                }
            }
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    Box(
        Modifier
            .widthIn(max = 280.dp)
            .clip(RoundedCornerShape(16.dp, 16.dp, 5.dp, 16.dp))
            .background(Brush.linearGradient(listOf(OrbBlue, OrbViolet)))
            .padding(horizontal = 13.dp, vertical = 8.dp),
    ) { Text(text, color = Color.White, fontSize = 14.sp, lineHeight = 20.sp) }
}

@Composable
private fun ReplyBubble(m: ChatMessage, onSpeak: (String) -> Unit) {
    val context = LocalContext.current
    Surface(shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 5.dp), color = Color.White, modifier = Modifier.widthIn(max = 310.dp)) {
        Column(Modifier.padding(horizontal = 13.dp, vertical = 9.dp).animateContentSize(spring(stiffness = 500f))) {
            if (m.reasoning.isNotEmpty()) Thought(m)
            for (step in m.steps) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                    Icon(
                        if (step.startsWith("搜索")) Icons.Filled.Search else Icons.AutoMirrored.Filled.Article, null,
                        tint = Color(0xFF999999), modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(step, fontSize = 12.sp, color = Color(0xFF888888), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            m.status?.let { status ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                    CircularProgressIndicator(Modifier.size(12.dp), color = Accent, strokeWidth = 1.5.dp)
                    Spacer(Modifier.size(6.dp))
                    Text(status, fontSize = 12.sp, color = Color(0xFF666666), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            for (action in m.actions) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                    Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF2EAD6B), modifier = Modifier.size(14.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(action, fontSize = 12.sp, color = Color(0xFF2E7D57))
                }
            }
            when {
                m.error != null -> Text(m.error!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                m.text.isEmpty() && m.loading && m.reasoning.isEmpty() && m.status == null -> TypingDots()
                m.text.isNotEmpty() -> SelectionContainer {
                    Text(m.text, color = Color(0xFF111111), fontSize = 15.sp, lineHeight = 22.sp)
                }
            }
            AnimatedVisibility(!m.loading && m.error == null && m.text.isNotBlank(), enter = fadeIn(tween(300))) {
                Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SmallAction(Icons.Filled.ContentCopy, "复制") { ImageActions.copy(context, m.text) }
                    SmallAction(Icons.AutoMirrored.Filled.VolumeUp, "朗读") { onSpeak(m.text) }
                }
            }
        }
    }
}

/** Deep-thinking text: open while the model thinks, folded once the answer arrives; tap the header to toggle. */
@Composable
private fun Thought(m: ChatMessage) {
    var userChoice by remember(m.id) { mutableStateOf<Boolean?>(null) }
    val thinkingNow = m.loading && m.thoughtMillis == null
    val expanded = userChoice ?: thinkingNow
    Column(Modifier.padding(bottom = 6.dp)) {
        Row(
            Modifier.clip(RoundedCornerShape(8.dp)).clickable { userChoice = !expanded }.padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Psychology, null, tint = Color(0xFF888888), modifier = Modifier.size(14.dp))
            Spacer(Modifier.size(5.dp))
            val seconds = ((m.thoughtMillis ?: 0L) / 1000).coerceAtLeast(1)
            Text(if (thinkingNow) "思考中…" else "已深度思考（$seconds 秒）", fontSize = 12.sp, color = Color(0xFF777777))
            Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = Color(0xFF999999), modifier = Modifier.size(16.dp))
        }
        AnimatedVisibility(expanded, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            // While streaming only the tail is shown, so the newest thoughts stay in view.
            val text = if (thinkingNow && m.reasoning.length > 280) "…" + m.reasoning.takeLast(280) else m.reasoning
            Text(
                text.trim(), fontSize = 12.sp, lineHeight = 18.sp, color = Color(0xFF8A8A8A),
                modifier = Modifier
                    .padding(top = 4.dp)
                    .heightIn(max = 220.dp)
                    .verticalScroll(rememberScrollState())
                    .drawBehind { drawRect(Color(0xFFE2E0EA), size = androidx.compose.ui.geometry.Size(2.dp.toPx(), size.height)) }
                    .padding(start = 9.dp),
            )
        }
    }
}

@Composable
private fun SmallAction(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(28.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = description, tint = Color(0xFF888888), modifier = Modifier.size(15.dp)) }
}

@Composable
private fun TargetChip(controller: AssistController) {
    Surface(shape = RoundedCornerShape(50), color = Color.White) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 11.dp)) {
            Text("译成", fontSize = 13.sp, color = Color(0xFF555555))
            LanguageDropdown(
                current = Languages.target(controller.target),
                options = Languages.targets,
                onSelect = { controller.setLanguages(newTarget = it.code) },
                color = Color(0xFF222222),
                compact = true,
            )
        }
    }
}

@Composable
internal fun Chip(icon: ImageVector, label: String, selected: Boolean = false, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) Accent else Color.White, tween(200), label = "chip")
    val fg by animateColorAsState(if (selected) Color.White else Color(0xFF222222), tween(200), label = "chipText")
    Surface(shape = RoundedCornerShape(50), color = bg, onClick = onClick) {
        Row(Modifier.height(32.dp).padding(horizontal = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp), tint = fg)
            Spacer(Modifier.size(5.dp))
            Text(label, fontSize = 13.sp, color = fg)
        }
    }
}

@Composable
internal fun Notice(text: String) {
    Surface(shape = RoundedCornerShape(14.dp), color = Color.White) {
        Text(text, color = Color(0xFFB3261E), modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp), fontSize = 13.sp, lineHeight = 18.sp)
    }
}
