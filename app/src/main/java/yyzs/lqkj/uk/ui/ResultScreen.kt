package yyzs.lqkj.uk.ui

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import yyzs.lqkj.uk.Languages
import kotlinx.coroutines.launch

@Composable
fun ResultScreen(controller: AssistController, result: ScreenResult, onBack: () -> Unit, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun current(): Bitmap = if (result.showOriginal) result.original else result.renderer?.output ?: result.original
    fun ready(): Boolean = result.status == null && result.renderer != null && result.translations.isNotEmpty()

    Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIcon(Icons.AutoMirrored.Filled.ArrowBack, "返回", onBack)
            Spacer(Modifier.weight(1f))
            Surface(shape = RoundedCornerShape(50), color = Color(0xFFE8E8EC)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                    LanguageDropdown(
                        current = Languages.source(controller.source),
                        options = Languages.sources,
                        onSelect = { controller.setLanguages(newSource = it.code) },
                        color = Color(0xFF222222),
                    )
                    IconButton(onClick = {
                        val swapped = Languages.swap(controller.source, controller.target)
                        if (swapped == null) Toast.makeText(context, "请先选择原文语言", Toast.LENGTH_SHORT).show()
                        else controller.setLanguages(swapped.first, swapped.second)
                    }) {
                        Icon(Icons.Filled.SwapHoriz, contentDescription = "交换语言", tint = Color(0xFF222222))
                    }
                    LanguageDropdown(
                        current = Languages.target(controller.target),
                        options = Languages.targets,
                        onSelect = { controller.setLanguages(newTarget = it.code) },
                        color = Color(0xFF222222),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            var menu by remember { mutableStateOf(false) }
            Box {
                RoundIcon(Icons.Filled.MoreVert, "更多") { menu = true }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(if (result.deep) "改用快速翻译" else "深度翻译（发送截图）") },
                        onClick = { menu = false; controller.translateScreen(!result.deep) },
                    )
                    DropdownMenuItem(text = { Text("重新翻译") }, onClick = { menu = false; controller.translateScreen(result.deep) })
                    DropdownMenuItem(text = { Text("设置") }, onClick = { menu = false; onOpenSettings() })
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 28.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            ZoomableBitmap(result, ::current)
            Column(Modifier.align(Alignment.TopCenter).padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                result.status?.let { StatusPill(it, loading = true) }
                result.error?.let { StatusPill(it, loading = false) }
            }
        }

        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            BottomAction(Icons.Filled.ContentCopy, "复制译文") {
                if (!ready()) return@BottomAction
                ImageActions.copy(context, result.translatedText())
                Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
            }
            BottomAction(Icons.Filled.Share, "分享") {
                if (ready()) scope.launch { ImageActions.share(context, current()) }
            }
            BottomAction(Icons.Filled.SaveAlt, "保存") {
                if (!ready()) return@BottomAction
                scope.launch {
                    val ok = ImageActions.save(context, current())
                    Toast.makeText(context, if (ok) "已保存到 图片/翻译助手" else "保存失败", Toast.LENGTH_SHORT).show()
                }
            }
            BottomAction(Icons.Filled.Compare, if (result.showOriginal) "切换译文" else "切换原文") {
                result.showOriginal = !result.showOriginal
            }
        }
    }
}

/** Draws the mutable rendered bitmap directly so each paint shows up without copying it. */
@Composable
private fun ZoomableBitmap(result: ScreenResult, bitmap: () -> Bitmap) {
    var scale by remember(result) { mutableFloatStateOf(1f) }
    var offset by remember(result) { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }
    val paint = remember { Paint(Paint.FILTER_BITMAP_FLAG) }
    val original = result.original
    Box(
        Modifier
            .aspectRatio(original.width.toFloat() / original.height)
            .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y }
            .transformable(state)
            .pointerInput(result) {
                detectTapGestures(onDoubleTap = { scale = if (scale > 1f) 1f else 2.5f; offset = Offset.Zero })
            }
            .clip(RoundedCornerShape(6.dp))
            .drawBehind {
                result.version // read so every paint triggers a redraw
                result.showOriginal
                val b = bitmap()
                drawIntoCanvas {
                    it.nativeCanvas.drawBitmap(b, null, Rect(0, 0, size.width.toInt(), size.height.toInt()), paint)
                }
            },
    )
}

@Composable
private fun StatusPill(text: String, loading: Boolean) {
    Surface(shape = RoundedCornerShape(50), color = Color(0xE6222222)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(text, color = Color.White, fontSize = 14.sp)
        }
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    Surface(shape = CircleShape, color = Color(0xFF2A2A2E), onClick = onClick, modifier = Modifier.size(48.dp)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = description, tint = Color.White) }
    }
}

@Composable
private fun BottomAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(color = Color.Transparent, onClick = onClick, shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, color = Color(0xFFCCCCCC), fontSize = 12.sp)
        }
    }
}
