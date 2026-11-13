package yyzs.lqkj.uk.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface

/** A complete first letter can sit outside the OCR box. Match its shape before erasing it. */
internal object LeadingGlyph {
    private const val W = 16
    private const val H = 20
    private val templates = mutableMapOf<Pair<Char, Boolean>, BooleanArray>()

    fun matches(letter: Char, source: Bitmap, bounds: Rect, background: Int): Boolean {
        // Conservative repair for detached Latin/digit glyphs. Arbitrary icons are not text seeds.
        if (letter !in 'A'..'Z' && letter !in 'a'..'z' && letter !in '0'..'9') return false
        val actual = sample(source, bounds) { c ->
            kotlin.math.abs(Color.red(c) - Color.red(background)) +
                kotlin.math.abs(Color.green(c) - Color.green(background)) +
                kotlin.math.abs(Color.blue(c) - Color.blue(background)) > 65
        }
        val actualExpanded = expand(actual)
        return listOf(false, true).any { bold ->
            val expected = template(letter, bold)
            val expectedExpanded = expand(expected)
            val precision = actual.indices.count { actual[it] && expectedExpanded[it] }.toFloat() / actual.count { it }.coerceAtLeast(1)
            val recall = expected.indices.count { expected[it] && actualExpanded[it] }.toFloat() / expected.count { it }.coerceAtLeast(1)
            val overlap = actual.indices.count { actual[it] && expected[it] }.toFloat() /
                actual.indices.count { actual[it] || expected[it] }.coerceAtLeast(1)
            precision >= .86f && recall >= .78f && overlap >= .42f
        }
    }

    @Synchronized
    private fun template(letter: Char, bold: Boolean): BooleanArray = templates.getOrPut(letter to bold) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 100f
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        val text = letter.toString()
        val bounds = Rect().also { paint.getTextBounds(text, 0, 1, it) }
        val bitmap = Bitmap.createBitmap(bounds.width() + 4, bounds.height() + 4, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawText(text, 2f - bounds.left, 2f - bounds.top, paint)
        val shape = sample(bitmap, Rect(2, 2, bitmap.width - 2, bitmap.height - 2)) { Color.alpha(it) > 80 }
        bitmap.recycle()
        shape
    }

    private fun sample(bitmap: Bitmap, box: Rect, ink: (Int) -> Boolean): BooleanArray = BooleanArray(W * H) { i ->
        val x = box.left + ((i % W + .5f) * box.width() / W).toInt().coerceAtMost(box.width() - 1)
        val y = box.top + ((i / W + .5f) * box.height() / H).toInt().coerceAtMost(box.height() - 1)
        ink(bitmap.getPixel(x, y))
    }

    private fun expand(shape: BooleanArray) = BooleanArray(shape.size) { i ->
        val x = i % W
        val y = i / W
        (maxOf(0, y - 1)..minOf(H - 1, y + 1)).any { yy ->
            (maxOf(0, x - 1)..minOf(W - 1, x + 1)).any { xx -> shape[yy * W + xx] }
        }
    }
}
