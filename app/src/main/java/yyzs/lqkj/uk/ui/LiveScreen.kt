package yyzs.lqkj.uk.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import yyzs.lqkj.uk.live.LivePhase
import yyzs.lqkj.uk.live.LiveSession

@Composable
fun LiveScreen(session: LiveSession, onHangUp: () -> Unit) {
    val phase by session.phase.collectAsState()
    val level by session.level.collectAsState()
    val lines by session.lines.collectAsState()
    val error by session.error.collectAsState()

    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 200f)) }

    val mood = when (phase) {
        LivePhase.Listening -> OrbMood.Listening
        LivePhase.Speaking -> OrbMood.Speaking
        LivePhase.Connecting, LivePhase.Working -> OrbMood.Thinking
        else -> OrbMood.Idle
    }
    val status = when (phase) {
        LivePhase.Connecting -> "正在连接…"
        LivePhase.Listening -> "我在听"
        LivePhase.Speaking -> "正在说…可以随时打断"
        LivePhase.Working -> "正在处理…"
        LivePhase.Closed -> "对话已结束"
        LivePhase.Failed -> error ?: "连接失败"
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = enter.value.coerceIn(0f, 1f) }
            .background(Brush.verticalGradient(listOf(Color(0xF2140F2E), Color(0xF22A1F5C), Color(0xF24B2C7A)))),
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(0.8f))
            AssistOrb(
                mood, level,
                Modifier
                    .size(180.dp)
                    .graphicsLayer { val p = enter.value; scaleX = 0.4f + 0.6f * p; scaleY = 0.4f + 0.6f * p },
            )
            Spacer(Modifier.height(24.dp))
            AnimatedContent(status, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) }, label = "status") {
                Text(it, color = if (phase == LivePhase.Failed) Color(0xFFFFB4AB) else Color.White.copy(alpha = 0.85f), fontSize = 16.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.weight(0.4f))

            // The last few lines of the conversation, newest brightest.
            Column(Modifier.fillMaxWidth().animateContentSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val recent = lines.filter { it.text.isNotBlank() }.takeLast(3)
                recent.forEachIndexed { i, line ->
                    val fade = 0.45f + 0.55f * (i + 1) / recent.size
                    AnimatedContent(
                        line.text,
                        transitionSpec = { (fadeIn(tween(180)) + slideInVertically { it / 3 }) togetherWith fadeOut(tween(90)) },
                        label = "line",
                    ) { text ->
                        Text(
                            text,
                            color = (if (line.fromUser) Color(0xFFBFD4FF) else Color.White).copy(alpha = fade),
                            fontSize = if (line.fromUser) 16.sp else 19.sp,
                            lineHeight = 27.sp,
                            textAlign = if (line.fromUser) TextAlign.End else TextAlign.Start,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            Spacer(Modifier.weight(0.6f))
            Surface(shape = CircleShape, color = Color(0xFFE5484D), onClick = onHangUp, modifier = Modifier.size(72.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.CallEnd, contentDescription = "结束对话", tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
