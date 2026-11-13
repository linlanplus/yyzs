package yyzs.lqkj.uk.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

enum class OrbMood { Idle, Listening, Thinking, Speaking }

val OrbBlue = Color(0xFF4F8DF7)
val OrbViolet = Color(0xFF8B6CF0)
val OrbPink = Color(0xFFF07CC0)
val OrbCyan = Color(0xFF5EE6E0)

/**
 * The assistant's face: the logo picture, which breathes when idle, swells with the voice level
 * while listening or speaking, and floats gently in a pulsing halo while thinking.
 */
@Composable
fun AssistOrb(mood: OrbMood, level: Float, modifier: Modifier = Modifier) {
    val logo = AssistantLogo.bitmap()
    val t = rememberInfiniteTransition(label = "orb")
    val breath by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "breath")
    val ring by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "ring")
    val bob by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "bob")
    val voice by animateFloatAsState(
        if (mood == OrbMood.Listening || mood == OrbMood.Speaking) level else 0f,
        spring(dampingRatio = 0.55f, stiffness = 380f), label = "voice",
    )
    val thinking by animateFloatAsState(if (mood == OrbMood.Thinking) 1f else 0f, tween(300), label = "thinking")

    Canvas(modifier) {
        val base = size.minDimension / 2f
        val r = base * (0.84f + 0.03f * breath + 0.12f * voice)
        val c = center

        // Expanding rings while listening/speaking.
        if (mood == OrbMood.Listening || mood == OrbMood.Speaking) {
            for (k in 0..1) {
                val p = (ring + k * 0.5f) % 1f
                drawCircle(
                    color = OrbViolet.copy(alpha = (1f - p) * 0.35f),
                    radius = r * 0.85f + base * 0.3f * p,
                    center = c,
                    style = Stroke(width = base * 0.05f * (1f - p)),
                )
            }
        }
        // Soft halo; while thinking it grows a little brighter and pulses with the float.
        val pulse = (bob + 1f) / 2f * thinking
        val haloR = r * (1.25f + 0.12f * pulse)
        drawCircle(
            Brush.radialGradient(listOf(OrbViolet.copy(alpha = 0.28f + 0.14f * pulse), Color.Transparent), c, haloR),
            radius = haloR, center = c,
        )
        // The picture, letterboxed into a square so any aspect ratio fits.
        val scale = 2f * r / maxOf(logo.width, logo.height)
        val w = logo.width * scale
        val h = logo.height * scale
        val dy = bob * base * 0.05f * thinking
        drawImage(
            logo,
            dstOffset = IntOffset((c.x - w / 2).roundToInt(), (c.y - h / 2 + dy).roundToInt()),
            dstSize = IntSize(w.roundToInt(), h.roundToInt()),
            filterQuality = FilterQuality.High,
        )
    }
}

/** Expanding waves that spread out from the orb when the assistant appears. [progress] runs 0..1. */
@Composable
fun DiffusionRipple(progress: Float, origin: Offset, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (progress <= 0f || progress >= 1f) return@Canvas
        val maxR = size.maxDimension * 1.1f
        // A main wave and a smaller one trailing behind it.
        for ((lag, strength) in listOf(0f to 1f, 0.22f to 0.6f)) {
            val p = ((progress - lag) / (1f - lag)).coerceIn(0f, 1f)
            if (p <= 0f) continue
            val r = maxR * p
            val fade = (1f - p) * strength
            drawCircle(
                Brush.radialGradient(
                    0f to Color.Transparent,
                    0.6f to OrbViolet.copy(alpha = 0.22f * fade),
                    0.9f to OrbBlue.copy(alpha = 0.5f * fade),
                    1f to Color.Transparent,
                    center = origin, radius = r,
                ),
                radius = r, center = origin,
            )
            drawCircle(OrbPink.copy(alpha = 0.8f * fade), radius = r * 0.97f, center = origin, style = Stroke(width = 4.dp.toPx() * fade))
        }
    }
}

/** Voice bars for the hold-to-talk input; each bar follows [level] with its own phase. */
@Composable
fun VoiceBars(level: Float, modifier: Modifier = Modifier, bars: Int = 24, barWidth: Dp = 3.dp, color: Color = Color.White) {
    val t = rememberInfiniteTransition(label = "bars")
    val phase by t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(900, easing = LinearEasing)), label = "phase")
    val smooth by animateFloatAsState(level, spring(dampingRatio = 0.7f, stiffness = 500f), label = "level")
    val weights = remember(bars) { List(bars) { i -> 0.45f + 0.55f * sin(PI * (i + 0.5) / bars).toFloat() } }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(barWidth), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until bars) {
            val wobble = 0.5f + 0.5f * sin(phase + i * 0.7f)
            val h = (0.12f + weights[i] * (0.15f + 0.85f * smooth) * (0.6f + 0.4f * wobble)).coerceIn(0.08f, 1f)
            Canvas(Modifier.width(barWidth).fillMaxHeight()) {
                val bh = size.height * h
                drawRoundRect(
                    color,
                    topLeft = Offset(0f, (size.height - bh) / 2),
                    size = Size(size.width, bh),
                    cornerRadius = CornerRadius(size.width / 2),
                )
            }
        }
    }
}

/** Three dots that bounce in turn, shown while waiting for the first token. */
@Composable
fun TypingDots(modifier: Modifier = Modifier, color: Color = OrbViolet) {
    val t = rememberInfiniteTransition(label = "dots")
    val p by t.animateFloat(0f, 3f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "p")
    Row(modifier.height(18.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0..2) {
            val d = ((p - i + 3f) % 3f)
            val lift = if (d < 1f) sin(PI * d).toFloat() else 0f
            Canvas(Modifier.width(7.dp).height(18.dp)) {
                drawCircle(color.copy(alpha = 0.45f + 0.55f * lift), radius = size.width / 2, center = Offset(size.width / 2, size.height / 2 - lift * 5.dp.toPx()))
            }
        }
    }
}
