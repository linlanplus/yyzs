package yyzs.lqkj.uk.ocr

import android.graphics.Rect
import kotlin.math.abs

/** Rejoins OCR fragments in reading order without treating every block as a paragraph. */
object ParagraphAssembler {
    private val listStart = Regex("""^(?:[>•●◆→➜▪‣]|[-–—]\s|\d{1,3}[.)]\s)""")
    fun startsListItem(text: String) = listStart.containsMatchIn(text.trimStart())
    fun assemble(fragments: List<TextRegion>): List<TextRegion> {
        val result = mutableListOf<TextRegion>()
        for (next in fragments.sortedWith(compareBy({ it.box.top }, { it.box.left }))) {
            val previous = result.indices.filter { continues(result[it], next) }
                .maxByOrNull { result[it].box.bottom }
            if (previous == null) {
                result += next
            } else {
                val first = result[previous]
                val lines = first.lines + next.lines
                result[previous] = TextRegion(first.id, Rect(first.box).apply { union(next.box) }, lines,
                    joinLines(lines.map { it.text }))
            }
        }
        return result.sortedWith(compareBy({ it.box.top }, { it.box.left }))
            .mapIndexed { id, region -> region.copy(id = id) }
    }

    private fun continues(first: TextRegion, next: TextRegion): Boolean {
        val height = first.lines.map { it.box.height() }.sorted()[first.lines.size / 2].toFloat()
        val nextHeight = next.lines.map { it.box.height() }.sorted()[next.lines.size / 2].toFloat()
        val gap = next.box.top - first.box.bottom
        if (gap < -height * .1f || gap > height * .65f) return false
        if (abs(first.box.left - next.box.left) > height * .8f) return false
        if (next.box.right > first.box.right + height * 2 && next.lines.size == 1) return false

        // Lowercase tail lines have no capitals/ascenders and therefore a shorter glyph box.
        val shortTail = next.lines.size == 1 && next.text.length <= 24 &&
            next.text.firstOrNull()?.isLowerCase() == true
        val ratio = maxOf(height, nextHeight) / minOf(height, nextHeight).coerceAtLeast(1f)
        if (ratio > if (shortTail) 1.9f else 1.45f) return false

        val end = first.text.trimEnd().trimEnd('"', '\'', '”', '’', ')', '）')
        if (end.lastOrNull() in listOf('.', '!', '?', '。', '！', '？', ':', '：', ';', '；')) return false
        if (first.text.startsWith('@') || next.text.startsWith('@') || first.text.contains("://")) return false
        if (startsListItem(next.text)) return false
        if (next.lines.size == 1 && next.box.width() < first.box.width() * .4f &&
            next.text.firstOrNull()?.isUpperCase() == true) return false

        // Two compact labels are usually separate controls, even when aligned vertically.
        return first.lines.size >= 2 || next.lines.size >= 2 ||
            (first.text.length >= 32 && next.text.firstOrNull()?.isLowerCase() == true)
    }

    fun joinLines(texts: List<String>): String = buildString {
        for (text in texts.filter { it.isNotBlank() }) {
            val t = text.trim()
            if (isNotEmpty()) {
                // Preserve lexical hyphens (e.g. video-like); leave ambiguous spelling to translation.
                if (last() == '-' && length >= 2 && this[length - 2].isLetter() && t.first().isLowerCase()) {
                    // No space after the hyphen.
                } else if (!isCjk(last()) && !isCjk(t.first())) append(' ')
            }
            append(t)
        }
    }

    private fun isCjk(c: Char): Boolean = when (Character.UnicodeScript.of(c.code)) {
        Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA -> true
        else -> c in '\u3000'..'\u303F' || c in '\uFF00'..'\uFFEF'
    }
}
