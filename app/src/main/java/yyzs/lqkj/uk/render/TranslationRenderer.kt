package yyzs.lqkj.uk.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import yyzs.lqkj.uk.ocr.TextRegion
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Paints translations over a copy of the screenshot: each region's original text is covered with
 * its sampled background colour, then the translation is laid out in the same box, in a colour
 * sampled from the original glyphs.
 */
class TranslationRenderer(private val source: Bitmap) {
    val output: Bitmap = source.copy(Bitmap.Config.ARGB_8888, true)
    private val canvas = Canvas(output)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)

    fun paint(region: TextRegion, translation: String) {
        if (translation.isBlank() || normalize(translation) == normalize(region.text)) return
        val lineHeight = region.lines.map { it.box.height() }.sorted()[region.lines.size / 2]
        val pad = max(2, (lineHeight * 0.18f).roundToInt())
        val box = Rect(region.box).apply { inset(-pad, -pad); intersect(0, 0, source.width, source.height) }

        val bg = backgroundColor(box)
        val fg = textColor(region.box, bg)

        // Cover each original line rather than the whole union so nearby images stay visible.
        fill.color = bg
        for (line in region.lines) {
            val r = Rect(line.box).apply { inset(-pad, -pad) }
            canvas.drawRoundRect(RectF(r), pad.toFloat(), pad.toFloat(), fill)
        }

        textPaint.color = fg
        val width = max(region.box.width(), lineHeight)
        val available = region.box.height()
        // ML Kit line boxes hug the glyphs, so the box height is close to the original font size.
        val start = lineHeight * 0.92f
        var size = start
        var layout = layout(translation, size, width, region)
        while (layout.height > available && size > start * 0.6f) {
            size *= 0.92f
            layout = layout(translation, size, width, region)
        }

        // Text that still overflows gets its own background so it stays readable.
        if (layout.height > available) {
            val extra = RectF(
                region.box.left - pad.toFloat(), region.box.top - pad.toFloat(),
                region.box.left + width + pad.toFloat(), region.box.top + layout.height + pad.toFloat(),
            )
            canvas.drawRoundRect(extra, pad.toFloat(), pad.toFloat(), fill)
        }

        canvas.save()
        val dy = if (layout.height < available) (available - layout.height) / 2f else 0f
        canvas.translate(region.box.left.toFloat(), region.box.top + dy)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun normalize(text: String) = text.filterNot { it.isWhitespace() }.lowercase()

    private fun layout(text: String, size: Float, width: Int, region: TextRegion): StaticLayout {
        textPaint.textSize = size
        return StaticLayout.Builder.obtain(text, 0, text.length, textPaint, width)
            .setAlignment(if (isCentered(region)) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.08f)
            .setIncludePad(false)
            .build()
    }

    /** Multi-line text whose line centres line up but whose left edges don't. */
    private fun isCentered(region: TextRegion): Boolean {
        if (region.lines.size < 2) return false
        val tolerance = region.lines[0].box.height() * 0.6f
        val lefts = region.lines.map { it.box.left }
        val centers = region.lines.map { it.box.centerX() }
        return (lefts.max() - lefts.min()) > tolerance * 2 && (centers.max() - centers.min()) < tolerance
    }

    /** Most common colour on the ring just outside [box], quantised to tolerate JPEG-like noise. */
    private fun backgroundColor(box: Rect): Int {
        val counts = HashMap<Int, Int>()
        val sums = HashMap<Int, IntArray>()
        fun sample(x: Int, y: Int) {
            if (x !in 0 until source.width || y !in 0 until source.height) return
            val c = source.getPixel(x, y)
            val key = (Color.red(c) shr 3 shl 10) or (Color.green(c) shr 3 shl 5) or (Color.blue(c) shr 3)
            counts[key] = (counts[key] ?: 0) + 1
            val s = sums.getOrPut(key) { IntArray(3) }
            s[0] += Color.red(c); s[1] += Color.green(c); s[2] += Color.blue(c)
        }
        val step = max(1, min(box.width(), box.height()) / 12)
        for (x in box.left..box.right step step) { sample(x, box.top); sample(x, box.bottom) }
        for (y in box.top..box.bottom step step) { sample(box.left, y); sample(box.right, y) }
        val best = counts.maxByOrNull { it.value }?.key ?: return Color.WHITE
        val n = counts[best]!!
        val s = sums[best]!!
        return Color.rgb(s[0] / n, s[1] / n, s[2] / n)
    }

    /** Average of the pixels inside [box] that differ most from the background. */
    private fun textColor(box: Rect, bg: Int): Int {
        val r = Rect(box).apply { intersect(0, 0, source.width, source.height) }
        if (r.isEmpty) return contrastFallback(bg)
        val step = max(1, min(r.width(), r.height()) / 16)
        val samples = ArrayList<Int>()
        for (y in r.top until r.bottom step step) for (x in r.left until r.right step step) samples += source.getPixel(x, y)
        val far = samples.map { it to distance(it, bg) }.sortedByDescending { it.second }
        val top = far.take(max(1, far.size / 12)).filter { it.second > 60 }
        if (top.isEmpty()) return contrastFallback(bg)
        val color = Color.rgb(
            top.sumOf { Color.red(it.first) } / top.size,
            top.sumOf { Color.green(it.first) } / top.size,
            top.sumOf { Color.blue(it.first) } / top.size,
        )
        return if (distance(color, bg) < 90) contrastFallback(bg) else color
    }

    private fun distance(a: Int, b: Int) =
        abs(Color.red(a) - Color.red(b)) + abs(Color.green(a) - Color.green(b)) + abs(Color.blue(a) - Color.blue(b))

    private fun contrastFallback(bg: Int): Int {
        val luminance = 0.299 * Color.red(bg) + 0.587 * Color.green(bg) + 0.114 * Color.blue(bg)
        return if (luminance > 140) Color.rgb(20, 20, 20) else Color.WHITE
    }
}
