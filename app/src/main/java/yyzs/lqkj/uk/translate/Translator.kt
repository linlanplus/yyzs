package yyzs.lqkj.uk.translate

import android.graphics.Bitmap
import android.util.Base64
import androidx.core.graphics.scale
import yyzs.lqkj.uk.Languages
import yyzs.lqkj.uk.Prefs
import yyzs.lqkj.uk.ocr.TextRegion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

object Translator {
    /** Free-form text from the input box or speech, streamed as it is generated. */
    fun translateText(text: String, targetCode: String): Flow<String> {
        val target = Languages.target(targetCode).promptName
        val secondary = Languages.target(Prefs.secondaryLang).promptName
        val system = """
            你是翻译助手。把用户发来的文字翻译成$target；如果原文已经是$target，就翻译成$secondary。
            如果用户明确要求翻译成某种语言（例如“用日语怎么说……”），按用户要求的语言翻译其中的内容。
            只输出译文，不要解释，不要加引号。
        """.trimIndent()
        return DeepSeekClient.stream(
            listOf(DeepSeekClient.message("system", system), DeepSeekClient.message("user", text)),
        )
    }

    /**
     * Translates OCR regions and emits `(regionId, translation)` as each line arrives.
     * With [screenshot] set (deep mode) the model also sees the image and every region's position,
     * so it can fix OCR mistakes and pick wording that fits the screen.
     * An empty translation means "leave the original as it is".
     */
    fun translateRegions(regions: List<TextRegion>, targetCode: String, screenshot: Bitmap?): Flow<Pair<Int, String>> = flow {
        val target = Languages.target(targetCode).promptName
        val source = Languages.source(Prefs.sourceLang).promptName
        val deep = screenshot != null
        val scale = if (deep) minOf(1f, IMAGE_WIDTH.toFloat() / screenshot.width) else 1f

        val items = JSONArray()
        for (r in regions) {
            val item = JSONObject().put("id", r.id).put("text", r.text)
            if (deep) item.put(
                "box",
                JSONArray(listOf(r.box.left, r.box.top, r.box.right, r.box.bottom).map { (it * scale).roundToInt() }),
            )
            items.put(item)
        }

        val system = buildString {
            appendLine("你是专业的手机屏幕翻译引擎。用户给出截图上 OCR 识别出的文字片段（JSON 数组，每项有 id 和 text），请把每个片段翻译成$target。")
            if (source.isNotEmpty()) appendLine("原文主要是$source。")
            appendLine("规则：")
            appendLine("1. 每个片段输出一行，格式为 id|译文。按 id 顺序逐个输出，不要遗漏，不要输出任何其他内容（不要解释，不要代码块）。")
            appendLine("2. 译文内不要换行。")
            appendLine("3. 已经是$target 的片段，以及品牌名、应用名、Logo 文字、用户名、@账号、#话题、网址、代码、数字等不需要翻译的内容，原样输出。")
            appendLine("4. OCR 可能有错字、漏字或断词，按上下文理解后再翻译。所有片段来自同一个界面，术语前后保持一致。")
            appendLine("5. 按钮、菜单、标签等短文本使用简短的常见译法。")
            if (deep) {
                appendLine("6. 附带了截图。box 是片段在这张截图中的像素坐标 [左, 上, 右, 下]。以图片中的实际文字为准纠正 OCR 错误，并根据画面语境（社交媒体帖子、评论、按钮、邮件等）选择译法。")
                appendLine("7. 如果某个片段其实是图标、装饰图案或识别噪声，输出“id|”（译文留空）。")
            }
        }

        val userContent: Any = if (deep) {
            JSONArray()
                .put(DeepSeekClient.imagePart(encodeJpeg(screenshot, scale)))
                .put(DeepSeekClient.textPart(items.toString()))
        } else {
            items.toString()
        }

        val pending = StringBuilder()
        DeepSeekClient.stream(
            listOf(DeepSeekClient.message("system", system), DeepSeekClient.message("user", userContent)),
        ).collect { chunk ->
            pending.append(chunk)
            var newline = pending.indexOf("\n")
            while (newline >= 0) {
                parseLine(pending.substring(0, newline))?.let { emit(it) }
                pending.delete(0, newline + 1)
                newline = pending.indexOf("\n")
            }
        }
        parseLine(pending.toString())?.let { emit(it) }
    }

    private val LINE = Regex("""^\s*(\d+)\s*[|｜]\s?(.*)$""")

    private fun parseLine(line: String): Pair<Int, String>? {
        val match = LINE.find(line) ?: return null
        return match.groupValues[1].toInt() to match.groupValues[2].trim()
    }

    private const val IMAGE_WIDTH = 1080

    private fun encodeJpeg(bitmap: Bitmap, scale: Float): String {
        val scaled = if (scale < 1f) {
            bitmap.scale((bitmap.width * scale).roundToInt(), (bitmap.height * scale).roundToInt())
        } else bitmap
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
        if (scaled !== bitmap) scaled.recycle()
        return "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}
