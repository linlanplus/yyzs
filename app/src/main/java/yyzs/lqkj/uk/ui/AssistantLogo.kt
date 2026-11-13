package yyzs.lqkj.uk.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import yyzs.lqkj.uk.R
import java.io.File
import kotlin.math.max

/**
 * The assistant's picture: the bundled logo, or one the user picked in settings.
 * Custom pictures are stored as PNG so a transparent background survives.
 */
object AssistantLogo {
    private const val MAX_SIZE = 512

    /** Bumped on every change so open screens reload the picture. */
    var version by mutableIntStateOf(0)
        private set

    private fun file(context: Context) = File(context.filesDir, "assistant_logo.png")

    fun isCustom(context: Context) = file(context).exists()

    fun set(context: Context, uri: Uri) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            // Software ARGB_8888 keeps the alpha channel and can be compressed.
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = max(info.size.width, info.size.height)
            if (longest > MAX_SIZE) {
                val scale = MAX_SIZE.toFloat() / longest
                decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
            }
        }
        val tmp = File(context.filesDir, "assistant_logo.tmp")
        tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        tmp.renameTo(file(context))
        version++
    }

    fun reset(context: Context) {
        file(context).delete()
        version++
    }

    @Composable
    fun bitmap(): ImageBitmap {
        val context = LocalContext.current
        val bundled = ImageBitmap.imageResource(R.drawable.assistant_logo)
        val v = version
        return remember(v) {
            val f = file(context)
            if (f.exists()) BitmapFactory.decodeFile(f.path)?.asImageBitmap() else null
        } ?: bundled
    }
}
