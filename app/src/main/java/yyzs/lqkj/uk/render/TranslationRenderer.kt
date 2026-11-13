package yyzs.lqkj.uk.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
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
class TranslationRenderer(private val source: Bitmap, private val regions: List<TextRegion> = emptyList()) {
    val output: Bitmap = source.copy(Bitmap.Config.ARGB_8888, true)
    private val canvas = Canvas(output)
    private val fill = Paint()
    private val typography = TextTypography(regions)
    internal data class Drawing(val mask: TextInkMask.Mask, val bg: Int, val box: Rect, val layout: StaticLayout)
    internal data class Prepared(val id: Int, val drawing: Drawing?, val truncated: Boolean = false)
    private val drawings = sortedMapOf<Int, Drawing>()
    val truncatedIds = mutableSetOf<Int>()

    fun paint(region: TextRegion, translation: String) = apply(prepare(region, translation))

    /** Read-only analysis of the source bitmap; safe to do on a worker thread. */
    internal fun prepare(region: TextRegion, translation: String): Prepared {
        if (region.lines.isEmpty() || region.box.isEmpty || translation.isBlank() || normalize(translation) == normalize(region.text)) {
            return Prepared(region.id, null)
        }
        val lineHeight = region.lines.map { it.box.height() }.sorted()[region.lines.size / 2]
        val pad = max(2, (lineHeight * 0.18f).roundToInt())
        val ring = Rect(region.box).apply { inset(-pad, -pad) }
        if (!ring.intersect(0, 0, source.width, source.height)) return Prepared(region.id, null)
        val bg = backgroundColor(ring)
        val fg = textColor(region.box, bg)
        val mask = TextInkMask.find(source, region, bg, regions.ifEmpty { listOf(region) })
        val start = typography.size(region)
        val box = typography.slot(region, mask.inkBounds, start, source, bg)
        if (box.isEmpty) return Prepared(region.id, null)
        val width = max(1, box.width())
        val available = max(1, box.height())
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = fg }
        val minimum = max(8f, start * .62f)
        // A multi-line heading can wrap back to the left margin below an emoji. Its union box
        // is suitable for erasure, but the first translated line must retain that indentation.
        val first = mask.lineBounds.first()
        val indent = (first.left - box.left).takeIf { region.lines.size > 1 && it > start * .6f } ?: 0
        var size = start
        var layout = layout(translation, size, width, region, textPaint, indent, first.bottom - box.top)
        while (layout.height > available && size > minimum) {
            size = max(minimum, size * .94f)
            layout = layout(translation, size, width, region, textPaint, indent, first.bottom - box.top)
        }
        var truncated = false
        if (layout.height > available) {
            var lines = layout.lineCount
            while (lines > 1 && layout.getLineBottom(lines - 1) > available) lines--
            layout = layout(translation, size, width, region, textPaint, indent, first.bottom - box.top, lines)
            truncated = true
        }
        return Prepared(region.id, Drawing(mask, bg, box, layout), truncated)
    }

    /** Apply on the UI thread so Compose never reads a bitmap while another thread changes it. */
    internal fun apply(prepared: Prepared) = applyBatch(listOf(prepared))

    /** Commit a completed translation with one composite pass. */
    internal fun applyBatch(prepared: List<Prepared>) {
        var changed = false
        for (item in prepared) {
            if (item.truncated) truncatedIds += item.id else truncatedIds.remove(item.id)
            if (item.drawing == null) {
                changed = (drawings.remove(item.id) != null) || changed
            } else {
                drawings[item.id] = item.drawing
                changed = true
            }
        }
        if (changed) redraw()
    }

    private fun redraw() {
        // Erase all source glyphs before drawing any translations. Stream order and retries
        // cannot erase a neighboring translation or accumulate text from a previous result.
        canvas.drawBitmap(source, 0f, 0f, null)
        for (drawing in drawings.values) {
            fill.color = drawing.bg
            drawing.mask.patches.forEach { canvas.drawRect(it, fill) }
        }
        for (drawing in drawings.values) {
            canvas.save()
            canvas.clipRect(drawing.box)
            canvas.translate(drawing.box.left.toFloat(), drawing.box.top.toFloat())
            drawing.layout.draw(canvas)
            canvas.restore()
        }
    }

    private fun normalize(text: String) = text.filterNot { it.isWhitespace() }.lowercase()

    private fun layout(text: String, size: Float, width: Int, region: TextRegion, textPaint: TextPaint,
                       indent: Int, indentHeight: Int, maxLines: Int = Int.MAX_VALUE): StaticLayout {
        textPaint.textSize = size
        val rowHeight = (textPaint.fontMetrics.descent - textPaint.fontMetrics.ascent) * 1.08f
        val indentedRows = kotlin.math.ceil(indentHeight / rowHeight).toInt().coerceAtLeast(1)
        val indents = if (indent > 0) IntArray(indentedRows + 1) { if (it < indentedRows) indent.coerceAtMost(width / 2) else 0 } else null
        return StaticLayout.Builder.obtain(text, 0, text.length, textPaint, width)
            .setAlignment(if (isCentered(region)) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.08f)
            .setIncludePad(false)
            .setIndents(indents, null)
            .setMaxLines(maxLines)
            .setEllipsize(if (maxLines < Int.MAX_VALUE) TextUtils.TruncateAt.END else null)
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
        val r = Rect(box)
        if (!r.intersect(0, 0, source.width, source.height)) return contrastFallback(bg)
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
