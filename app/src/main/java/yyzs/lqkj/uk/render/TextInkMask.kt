package yyzs.lqkj.uk.render

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import yyzs.lqkj.uk.ocr.OcrLine
import yyzs.lqkj.uk.ocr.TextRegion
import kotlin.math.abs
import kotlin.math.roundToInt

/** Bounded connected components recover glyph edges outside inaccurate OCR boxes on UI backgrounds. */
object TextInkMask {
    data class Mask(val patches: List<Rect>, val inkBounds: Rect, val lineBounds: List<Rect>)

    fun find(source: Bitmap, region: TextRegion, background: Int, neighbors: List<TextRegion>): Mask {
        val patches = mutableListOf<Rect>()
        val ink = Rect(region.box)
        val lineBounds = mutableListOf<Rect>()
        val allLines = neighbors.flatMap { it.lines }
        for (line in region.lines) {
            val rowInk = Rect()
            lineBounds += rowInk
            val h = line.box.height()
            val search = Rect(line.box).apply {
                inset(-(h * .85f).roundToInt(), -(h * .22f).roundToInt())
                left -= (h * .4f).roundToInt()
                if (!intersect(0, 0, source.width, source.height)) setEmpty()
            }
            // Stop halfway to adjacent rows; a nearby descender belongs to the adjacent row.
            for (other in allLines) {
                if (other === line || other.box.right <= line.box.left || other.box.left >= line.box.right) continue
                if (other.box.bottom <= line.box.top) search.top = maxOf(search.top, (other.box.bottom + line.box.top) / 2)
                if (other.box.top >= line.box.bottom) search.bottom = minOf(search.bottom, (line.box.bottom + other.box.top) / 2)
            }
            if (search.isEmpty) { rowInk.set(line.box); continue }
            val w = search.width()
            val height = search.height()
            val pixels = IntArray(w * height)
            source.getPixels(pixels, 0, w, search.left, search.top, w, height)
            val foreground = BooleanArray(pixels.size) { distance(pixels[it], background) > 65 }
            val visited = BooleanArray(pixels.size)
            val queue = IntArray(pixels.size)
            for (seed in pixels.indices) {
                if (visited[seed] || !foreground[seed]) continue
                var head = 0
                var tail = 1
                queue[0] = seed
                visited[seed] = true
                val component = Rect()
                var touchesText = false
                while (head < tail) {
                    val index = queue[head++]
                    val x = index % w
                    val y = index / w
                    val sx = x + search.left
                    val sy = y + search.top
                    component.union(sx, sy, sx + 1, sy + 1)
                    if (line.box.contains(sx, sy)) touchesText = true
                    for (ny in maxOf(0, y - 1)..minOf(height - 1, y + 1)) {
                        for (nx in maxOf(0, x - 1)..minOf(w - 1, x + 1)) {
                            val next = ny * w + nx
                            if (!visited[next] && foreground[next]) {
                                visited[next] = true
                                queue[tail++] = next
                            }
                        }
                    }
                }
                val detachedLetter = !touchesText && component.right <= line.box.left &&
                    line.box.left - component.right <= h * .55f &&
                    component.height() >= h * .5f && component.height() <= h * 1.25f && component.width() <= h * 1.2f &&
                    abs(component.centerY() - line.box.centerY()) <= h * .3f &&
                    LeadingGlyph.matches(line.text.first(), source, component, background)
                if (!touchesText && !isLeadingMark(line, component) && !detachedLetter) continue
                if (component.height() > h * 1.5f) continue
                ink.union(component)
                rowInk.union(component)
                val fringe = maxOf(1, minOf(3, (h * .045f).roundToInt()))
                patches += Rect(component).apply {
                    inset(-fringe, -fringe)
                    if (!intersect(search)) setEmpty()
                }
            }
            if (rowInk.isEmpty) rowInk.set(line.box)
        }
        return Mask(patches, ink, lineBounds)
    }

    private fun isLeadingMark(line: OcrLine, component: Rect): Boolean {
        // Quote/list markers may be present in the OCR string but entirely outside its rectangle.
        if (line.text.firstOrNull() !in listOf('>', '(', '（', '“', '"', '•')) return false
        val h = line.box.height()
        return component.right <= line.box.left && line.box.left - component.right <= h * .35f &&
            component.width() < h * .85f && component.height() <= h * 1.15f &&
            abs(component.centerY() - line.box.centerY()) <= h * .35f
    }

    private fun distance(a: Int, b: Int) = abs(Color.red(a) - Color.red(b)) +
        abs(Color.green(a) - Color.green(b)) + abs(Color.blue(a) - Color.blue(b))
}
