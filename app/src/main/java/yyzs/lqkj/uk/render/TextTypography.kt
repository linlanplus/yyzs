package yyzs.lqkj.uk.render

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import yyzs.lqkj.uk.ocr.TextRegion
import kotlin.math.abs

/** OCR rectangles enclose ink, not the font's ascent/descent or the line's leading. */
internal class TextTypography(private val regions: List<TextRegion>) {
    private val measured = regions.associate { it.id to measure(it) }

    fun size(region: TextRegion): Float {
        val own = measured[region.id] ?: measure(region)
        val compatible = regions.filter {
            val other = measured.getValue(it.id)
            other in own * .86f..own * 1.14f && abs(it.box.left - region.box.left) < own * 1.5f &&
                abs(it.box.centerY() - region.box.centerY()) < own * 40f
        }.map { measured.getValue(it.id) }
        return if (compatible.size >= 3) compatible.sorted()[compatible.size / 2] else own
    }

    fun slot(region: TextRegion, ink: Rect, fontSize: Float, source: Bitmap, background: Int): Rect {
        val padding = (fontSize * .22f).toInt().coerceAtLeast(2)
        val box = Rect(ink.left, ink.top - padding, ink.right, ink.bottom + padding)
        for (other in regions) {
            if (other.id == region.id || other.box.right <= box.left || other.box.left >= box.right) continue
            if (other.box.bottom <= ink.top) box.top = maxOf(box.top, (other.box.bottom + ink.top) / 2)
            if (other.box.top >= ink.bottom) box.bottom = minOf(box.bottom, (other.box.top + ink.bottom) / 2)
        }
        if (!box.intersect(0, 0, source.width, source.height)) { box.setEmpty(); return box }
        // The longest OCR line is not necessarily the column's right margin. Reuse genuinely
        // empty space in the same column instead of shrinking text or orphaning a few characters.
        val sameColumn = regions.filter {
            val other = measured.getValue(it.id)
            abs(it.box.left - region.box.left) < fontSize * 1.5f &&
                other in fontSize * .86f..fontSize * 1.14f && it.box.width() >= fontSize * 12f
        }
        if (sameColumn.size >= 3) {
            var right = sameColumn.maxOf { it.box.right }.coerceAtMost(source.width)
            for (other in regions) {
                if (other.id != region.id && other.box.left >= box.right && other.box.top < box.bottom && other.box.bottom > box.top) {
                    right = minOf(right, other.box.left - padding)
                }
            }
            if (right > box.right) {
                val width = right - box.right
                val pixels = IntArray(width * box.height())
                source.getPixels(pixels, 0, width, box.right, box.top, width, box.height())
                var free = width
                for (x in 0 until width) {
                    var inkPixels = 0
                    for (y in 0 until box.height()) {
                        val c = pixels[y * width + x]
                        val distance = abs(Color.red(c) - Color.red(background)) + abs(Color.green(c) - Color.green(background)) +
                            abs(Color.blue(c) - Color.blue(background))
                        if (distance > 65 && ++inkPixels >= 2) break
                    }
                    if (inkPixels >= 2) { free = maxOf(0, x - padding); break }
                }
                box.right += free
            }
        }
        return box
    }

    private fun measure(region: TextRegion): Float {
        val paint = Paint().apply { textSize = 100f }
        val estimates = region.lines.filter { it.text.count(Char::isLetter) >= 4 }.mapNotNull { line ->
            val bounds = Rect()
            paint.getTextBounds(line.text, 0, line.text.length, bounds)
            if (bounds.height() <= 0) null else {
                val observed = line.box.height().toFloat()
                (observed * 100f / bounds.height()).coerceIn(observed * .85f, observed * 1.5f)
            }
        }.sorted()
        return if (estimates.isNotEmpty()) estimates[estimates.size / 2]
        else region.lines.map { it.box.height().toFloat() }.sorted().let { it.getOrElse(it.size / 2) { 16f } }
    }
}
