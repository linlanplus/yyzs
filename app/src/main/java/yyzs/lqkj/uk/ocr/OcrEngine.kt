package yyzs.lqkj.uk.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class OcrLine(val text: String, val box: Rect, val confidence: Float)

/** A paragraph-like group of lines that is translated and redrawn as one unit. */
data class TextRegion(val id: Int, val box: Rect, val lines: List<OcrLine>, val text: String)

/** On-device OCR with the bundled ML Kit models (works without Google Play services). */
object OcrEngine {
    private val chinese: TextRecognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }
    private val japanese: TextRecognizer by lazy {
        TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
    }
    private val korean: TextRecognizer by lazy {
        TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
    }

    /**
     * @param source a code from [yyzs.lqkj.uk.Languages.sources]. "auto" runs every model and keeps
     *   the result with the highest confidence-weighted character count; every model also reads Latin text.
     * @param skipTop lines that end above this y (the status bar) are dropped.
     */
    suspend fun recognize(bitmap: Bitmap, source: String, skipTop: Int): List<TextRegion> = coroutineScope {
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizers = when (source) {
            "ja" -> listOf(japanese)
            "ko" -> listOf(korean)
            "zh", "latin" -> listOf(chinese)
            else -> listOf(chinese, japanese, korean)
        }
        val results = recognizers
            .map { recognizer -> async { runCatching { recognizer.process(image).await() }.getOrNull() } }
            .awaitAll()
            .filterNotNull()
        if (results.isEmpty()) throw IllegalStateException("文字识别失败")
        buildRegions(results.maxBy(::score), skipTop)
    }

    private fun score(text: Text): Double = text.textBlocks.sumOf { block ->
        block.lines.sumOf { line -> line.confidence.toDouble() * line.text.count { !it.isWhitespace() } }
    }

    private fun buildRegions(text: Text, skipTop: Int): List<TextRegion> {
        val regions = ArrayList<TextRegion>()
        for (block in text.textBlocks) {
            val lines = block.lines.mapNotNull { line ->
                val box = line.boundingBox ?: return@mapNotNull null
                val content = line.text.trim()
                if (content.isEmpty() || box.bottom <= skipTop || box.width() <= 0 || box.height() <= 0) null
                else OcrLine(content, box, line.confidence)
            }
            for (group in splitBlock(lines)) {
                val joined = joinLines(group.map { it.text })
                // Times, counters and icons-as-text have nothing to translate.
                if (joined.none { it.isLetter() }) continue
                val box = Rect(group[0].box).apply { group.forEach { union(it.box) } }
                regions += TextRegion(regions.size, box, group, joined)
            }
        }
        return regions
    }

    /** ML Kit sometimes merges a heading with its body, or two columns; split where size or spacing jumps. */
    private fun splitBlock(lines: List<OcrLine>): List<List<OcrLine>> {
        if (lines.isEmpty()) return emptyList()
        val groups = mutableListOf(mutableListOf(lines[0]))
        for (i in 1 until lines.size) {
            val prev = lines[i - 1].box
            val cur = lines[i].box
            val small = minOf(prev.height(), cur.height())
            val large = maxOf(prev.height(), cur.height())
            val sizeJump = large > small * 1.45f
            val bigGap = cur.top - prev.bottom > large * 0.9f
            val sameRow = cur.top < prev.bottom - large * 0.5f
            if (sizeJump || bigGap || sameRow) groups += mutableListOf(lines[i]) else groups.last() += lines[i]
        }
        return groups
    }

    private fun joinLines(texts: List<String>): String {
        val sb = StringBuilder()
        for (t in texts) {
            if (sb.isNotEmpty() && !isCjk(sb.last()) && !isCjk(t.first())) sb.append(' ')
            sb.append(t)
        }
        return sb.toString()
    }

    private fun isCjk(c: Char): Boolean = when (Character.UnicodeScript.of(c.code)) {
        Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA -> true
        // CJK punctuation and full-width forms.
        else -> c in '\u3000'..'\u303F' || c in '\uFF00'..'\uFFEF'
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
