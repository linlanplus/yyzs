package yyzs.lqkj.uk

import android.app.Activity
import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import yyzs.lqkj.uk.ocr.OcrEngine
import yyzs.lqkj.uk.ocr.OcrLine
import yyzs.lqkj.uk.ocr.ParagraphAssembler
import yyzs.lqkj.uk.ocr.TextRegion
import yyzs.lqkj.uk.render.TextInkMask
import yyzs.lqkj.uk.render.TranslationRenderer
import yyzs.lqkj.uk.translate.RegionTranslationProtocol
import yyzs.lqkj.uk.ui.ScreenResult

/** Runs on the Android OCR/render stack without model API calls or user credentials. SDK diagnostics are not disabled here. */
class TranslationRegression : Instrumentation() {
    private var phase = "after"
    private var fixture = "original"
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        phase = arguments?.getString("phase") ?: "after"
        fixture = arguments?.getString("fixture") ?: "original"
        start()
    }

    override fun onStart() {
        val result = Bundle()
        try {
            runBlocking {
                val started = System.currentTimeMillis()
                testOutputModes()
                testGrouping()
                testProtocol()
                testInkAndOverflow()
                testDetachedGlyphAndLayout()
                if (fixture == "list") {
                    runListFixture(result)
                    return@runBlocking
                }
                val bitmap = context.assets.open("translation-regression.jpg").use { BitmapFactory.decodeStream(it) }
                val regions = OcrEngine.recognize(bitmap, "auto", 120)
                val paragraph = regions.single { it.text.contains("models were expected", true) }
                check(paragraph.text.endsWith("turned out.")) { "Tail word still detached: ${paragraph.text}" }
                check(regions.none { it.text.trim() == "out." })
                val report = JSONObject().put("width", bitmap.width).put("height", bitmap.height)
                val items = JSONArray()
                val renderer = TranslationRenderer(bitmap, regions)
                for (region in regions) {
                    val text = region.text
                    items.put(JSONObject().put("id", region.id).put("text", text)
                        .put("box", region.box.toShortString())
                        .put("lines", JSONArray(region.lines.map { JSONObject().put("text", it.text).put("box", it.box.toShortString()) })))
                    fixtureTranslation(text)?.let { renderer.paint(region, it) }
                }
                // These non-text areas must remain pixel-identical to the decoded original.
                assertSamePixels(bitmap, renderer.output, Rect(0, 320, 175, 530), "avatar")
                assertSamePixels(bitmap, renderer.output, Rect(20, 630, 80, 720), "emoji")
                assertSamePixels(bitmap, renderer.output, Rect(20, 1600, 1240, 2300), "hero image")
                val reverse = TranslationRenderer(bitmap, regions)
                for (region in regions.reversed()) fixtureTranslation(region.text)?.let { reverse.paint(region, it) }
                fixtureTranslation(paragraph.text)?.let { reverse.paint(paragraph, it) }
                assertSamePixels(renderer.output, reverse.output, Rect(0, 0, bitmap.width, bitmap.height), "stream order / duplicate paint")
                reverse.output.recycle()
                report.put("regions", items)
                report.put("checks", JSONArray(listOf("real-image-tail", "paragraph-boundaries", "columns", "hyphens",
                    "missing-id-retry", "duplicates-and-unknown-ids", "empty-translation", "retry-exhaustion", "cancellation",
                    "partial-glyph-mask", "icon-preservation", "overflow-boundary", "avatar-emoji-hero-preservation", "stream-order",
                    "streaming-vs-one-shot", "default-streaming-enabled", "one-shot-error", "one-shot-cancellation")))
                report.put("truncatedIds", JSONArray(renderer.truncatedIds.toList()))
                report.put("elapsedMs", System.currentTimeMillis() - started)
                File(targetContext.filesDir, "regression-$phase.json").writeText(report.toString(2))
                File(targetContext.filesDir, "regression-$phase.png").outputStream().use { renderer.output.compress(Bitmap.CompressFormat.PNG, 100, it) }
                result.putString("stream", "PASS: 18 regression checks; ${regions.size} OCR regions; phase=$phase; ${System.currentTimeMillis() - started} ms\n")
            }
            finish(Activity.RESULT_OK, result)
        } catch (error: Throwable) {
            result.putString("stream", "FAILED: ${error.javaClass.simpleName}: ${error.message}\n${error.stackTraceToString()}")
            finish(Activity.RESULT_CANCELED, result)
        }
    }

    private suspend fun testOutputModes() {
        // The test package has its own preferences, separate from the user's installed release.
        Prefs.init(targetContext)
        val settings = targetContext.getSharedPreferences("settings", 0)
        val key = "stream_screen_translation"
        val hadSetting = settings.contains(key)
        val previous = Prefs.streamScreenTranslation
        try {
            check(settings.edit().remove(key).commit())
            check(Prefs.streamScreenTranslation) { "Existing users must default to streaming" }
            Prefs.streamScreenTranslation = false
            Prefs.init(targetContext)
            check(!Prefs.streamScreenTranslation) { "Output preference not retained" }
        } finally {
            if (hadSetting) settings.edit().putBoolean(key, previous).commit()
            else settings.edit().remove(key).commit()
        }

        val bitmap = Bitmap.createBitmap(420, 180, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(Color.WHITE)
        val regions = listOf(region(2, "Hello there", Rect(20, 20, 360, 65)),
            region(9, "How are you", Rect(20, 100, 360, 145)))
        val results = mutableListOf<ScreenResult>()
        for (streaming in listOf(true, false)) {
            val state = ScreenResult(bitmap, deep = !streaming).apply { this.regions = regions }
            state.renderTranslations(streaming, flow {
                emit(2 to "你好")
                if (streaming) {
                    check(state.renderer != null && state.translations == mapOf(2 to "你好") && state.version == 1)
                } else {
                    check(state.renderer == null && state.translations.isEmpty() && state.version == 0) { "Partial batch leaked" }
                }
                emit(9 to "你好吗")
                if (!streaming) check(state.renderer == null && state.translations.isEmpty())
            })
            check(state.translations == mapOf(2 to "你好", 9 to "你好吗"))
            check(state.version == if (streaming) 2 else 1)
            results += state
        }
        assertSamePixels(results[0].renderer!!.output, results[1].renderer!!.output,
            Rect(0, 0, bitmap.width, bitmap.height), "output mode image equivalence")
        check(results[0].translatedText() == results[1].translatedText())
        for (failure in listOf(IllegalStateException("network failed"), CancellationException("user cancelled"))) {
            val state = ScreenResult(bitmap, false).apply { this.regions = regions }
            val thrown = runCatching {
                state.renderTranslations(false, flow { emit(2 to "你好"); throw failure })
            }.exceptionOrNull()
            check(thrown === failure)
            check(state.renderer == null && state.translations.isEmpty() && state.version == 0)
        }
        results.forEach { it.renderer!!.output.recycle() }
        bitmap.recycle()
    }

    private suspend fun runListFixture(result: Bundle) {
        val bitmap = context.assets.open("translation-list.jpg").use { BitmapFactory.decodeStream(it) }
        val regions = OcrEngine.recognize(bitmap, "auto", 120)
        val renderer = TranslationRenderer(bitmap, regions)
        val items = JSONArray()
        val listFonts = mutableListOf<Float>()
        for (region in regions) {
            val item = JSONObject().put("id", region.id).put("text", region.text).put("box", region.box.toShortString())
                .put("lines", JSONArray(region.lines.map { JSONObject().put("text", it.text).put("box", it.box.toShortString()) }))
            listTranslation(region.text)?.let { text ->
                val prepared = renderer.prepare(region, text)
                prepared.drawing?.let {
                    item.put("fontSize", it.layout.paint.textSize).put("drawBox", it.box.toShortString())
                    if (region.text.startsWith("→")) listFonts += it.layout.paint.textSize
                }
                renderer.apply(prepared)
            }
            items.put(item)
        }
        if (phase != "before") {
            assertSamePixels(bitmap, renderer.output, Rect(20, 630, 78, 710), "heading emoji")
            assertSamePixels(bitmap, renderer.output, Rect(0, 320, 175, 530), "list avatar")
            assertSamePixels(bitmap, renderer.output, Rect(0, 2240, bitmap.width, bitmap.height), "bottom image and reply bar")
            check(listFonts.size == 5 && listFonts.max() / listFonts.min() < 1.07f) { "List font sizes: $listFonts" }
        }
        File(targetContext.filesDir, "regression-list-$phase.json").writeText(JSONObject().put("regions", items).toString(2))
        File(targetContext.filesDir, "regression-list-$phase.png").outputStream().use { renderer.output.compress(Bitmap.CompressFormat.PNG, 100, it) }
        result.putString("stream", "List fixture: ${regions.size} OCR regions; phase=$phase\n")
    }

    private fun testDetachedGlyphAndLayout() {
        val bitmap = Bitmap.createBitmap(360, 240, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 48f }
        canvas.drawText("Astra", 45f, 70f, paint)
        val bounds = Rect().also { paint.getTextBounds("Astra", 0, 5, it); it.offset(45, 70) }
        val cut = region(0, "Astra", Rect(bounds).apply { left = 45 + paint.measureText("A").toInt() + 2 })
        val mask = TextInkMask.find(bitmap, cut, Color.WHITE, listOf(cut))
        val erased = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val eraseCanvas = Canvas(erased)
        paint.color = Color.WHITE
        mask.patches.forEach { eraseCanvas.drawRect(it, paint) }
        for (y in bounds.top until bounds.bottom) for (x in bounds.left until cut.box.left) {
            check(Color.red(erased.getPixel(x, y)) > 245) { "Detached A remains at $x,$y" }
        }
        erased.recycle()

        canvas.drawColor(Color.WHITE)
        paint.color = Color.BLACK; paint.textSize = 44f
        canvas.drawText("Heading that", 85f, 70f, paint)
        canvas.drawText("wraps", 25f, 124f, paint)
        paint.color = Color.BLUE
        canvas.drawRect(25f, 35f, 65f, 80f, paint)
        val lines = listOf(OcrLine("Heading that", Rect(85, 35, 340, 75), 1f), OcrLine("wraps", Rect(25, 90, 160, 130), 1f))
        val heading = TextRegion(0, Rect(25, 35, 340, 130), lines, "Heading that wraps")
        val headingRenderer = TranslationRenderer(bitmap, listOf(heading))
        headingRenderer.paint(heading, "带有图标且需要换行的标题".repeat(2))
        assertSamePixels(bitmap, headingRenderer.output, Rect(25, 35, 65, 80), "wrapped heading icon")
        headingRenderer.output.recycle()

        canvas.drawColor(Color.WHITE)
        canvas.drawRect(160f, 80f, 190f, 125f, paint)
        val neighbors = listOf(region(0, "An ordinary paragraph above", Rect(20, 20, 320, 55)),
            region(1, "Same font text in this row", Rect(20, 85, 140, 120)),
            region(2, "An ordinary paragraph below", Rect(20, 150, 320, 185)))
        val renderer = TranslationRenderer(bitmap, neighbors)
        val prepared = renderer.prepare(neighbors[1], "这是一段较长的译文，右边有图标")
        check(prepared.drawing!!.box.right < 160)
        renderer.apply(prepared)
        assertSamePixels(bitmap, renderer.output, Rect(160, 80, 190, 125), "right-hand icon")
        renderer.output.recycle(); bitmap.recycle()
    }

    private fun listTranslation(text: String): String? {
        val pairs = listOf(
            "COULD FINALLY BE" to "GPT-6.2 ASTRA 和 SOL 可能终于要在下周发布了。",
            "latest rumors" to "最新传闻称，OpenAI 在看到最新前沿模型的竞争激烈程度后，可能推迟了 Astra 以进行进一步改进。",
            "makes this interesting" to "以下是值得关注的地方：",
            "could launch together" to "→ GPT-6.2 Astra 和 Sol 可能一同发布。",
            "Both are expected" to "→ 两者预计都将比上一版本带来重大改进。",
            "reportedly getting stronger" to "→ 据报道，Astra 在编程、视觉生成和通过代码创建视频方面变得更强。",
            "Early testers claim" to "→ 早期测试者称，新的 Astra 检查点已经令人印象深刻。",
            "next big challenge" to "→ Fable 5.5 可能是 Anthropic 对 OpenAI 的下一个重大挑战。",
            "One interesting detail" to "早期测试中有一个有趣的细节：据报道，Astra 通过代码生成的视频看起来就像真实视频，不过 Fable 整体上似乎仍占优势。",
            "If these reports" to "如果这些报道准确，OpenAI 可能正在准备一次比常规模型更新大得多的升级。",
            "Next week could" to "下周可能会很疯狂。你在等 GPT-6.2 Astra 还是 GPT-6.2 Sol？",
        )
        return pairs.filter { text.contains(it.first, true) }.map { it.second }.takeIf { it.isNotEmpty() }?.joinToString("\n")
    }

    private fun region(id: Int, text: String, box: Rect, extraLine: Boolean = false): TextRegion {
        val lines = if (extraLine) listOf(
            OcrLine(text.substringBeforeLast(' '), Rect(box.left, box.top, box.right, box.top + 40), 1f),
            OcrLine(text.substringAfterLast(' '), Rect(box.left, box.bottom - 40, box.right, box.bottom), 1f),
        ) else listOf(OcrLine(text, box, 1f))
        return TextRegion(id, box, lines, text)
    }

    private fun testGrouping() {
        val body = region(80, "This is a wrapped paragraph that turned", Rect(24, 100, 650, 190), true)
        val tail = region(1, "out.", Rect(24, 205, 100, 236))
        check(ParagraphAssembler.assemble(listOf(tail, body)).single().text.endsWith("turned out."))
        fun separate(a: TextRegion, b: TextRegion) = check(ParagraphAssembler.assemble(listOf(a, b)).size == 2)
        separate(region(1, "Heading", Rect(24, 25, 650, 88)), body)
        separate(body.copy(text = body.text + "."), tail)
        separate(body, tail.copy(box = Rect(24, 260, 100, 291)))
        separate(region(1, "Share", Rect(24, 100, 120, 140)), region(2, "Save", Rect(24, 155, 120, 195)))
        separate(body, region(2, "Save", Rect(24, 205, 120, 245)))
        separate(body, tail.copy(text = "> A new paragraph"))
        separate(body, tail.copy(text = "→ Next list item"))
        separate(body, tail.copy(text = "2. Next list item"))
        val left = region(0, "A paragraph in the left column ends", Rect(20, 300, 280, 390), true)
        val right = region(1, "Another paragraph in the right column ends", Rect(330, 300, 590, 390), true)
        val columns = ParagraphAssembler.assemble(listOf(right,
            region(2, "here.", Rect(20, 400, 90, 431)), left, region(3, "there.", Rect(330, 400, 420, 431))))
        check(columns.size == 2 && columns[0].text.endsWith("ends here.") && columns[1].text.endsWith("ends there."))
        check(ParagraphAssembler.joinLines(listOf("video-", "like output")) == "video-like output")
    }

    private suspend fun testProtocol() {
        val requests = mutableListOf<List<Int>>()
        val output = RegionTranslationProtocol.translate(listOf(5, 7, 9)) { missing, retry ->
            requests += missing
            if (!retry) flowOf("5|译", "文\r\n999|unknown\n5|duplicate\n999999999999999999999999|overflow\n7|")
            else flowOf("9｜补译")
        }.toList()
        check(requests == listOf(listOf(5, 7, 9), listOf(9)))
        check(output == listOf(5 to "译文", 7 to "", 9 to "补译"))
        var attempts = 0
        val failure = runCatching {
            RegionTranslationProtocol.translate(listOf(2, 8)) { _, _ -> attempts++; flowOf("2|完成\nmalformed") }.toList()
        }.exceptionOrNull()
        check(attempts == 2 && failure is IllegalStateException && failure.message!!.contains("1 处"))
        attempts = 0
        val cancelled = runCatching {
            RegionTranslationProtocol.translate(listOf(3)) { _, _ ->
                attempts++
                flow { throw CancellationException("cancel test") }
            }.toList()
        }.exceptionOrNull()
        check(cancelled is CancellationException && attempts == 1)
        check(RegionTranslationProtocol.translate(emptyList()) { _, _ -> error("Empty input must not request") }.toList().isEmpty())
    }

    private fun testInkAndOverflow() {
        val bitmap = Bitmap.createBitmap(340, 180, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 48f }
        canvas.drawText("Open", 45f, 70f, paint)
        val bounds = Rect().also { paint.getTextBounds("Open", 0, 4, it); it.offset(45, 70) }
        paint.color = Color.RED
        canvas.drawRect(6f, 30f, 22f, 73f, paint)
        val cut = region(0, "Open", Rect(bounds).apply { left += 12 })
        val mask = TextInkMask.find(bitmap, cut, Color.WHITE, listOf(cut))
        check(mask.inkBounds.left <= bounds.left + 1) { "Cut-off leading glyph not recovered" }
        val erased = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        paint.color = Color.WHITE
        val erasedCanvas = Canvas(erased)
        mask.patches.forEach { erasedCanvas.drawRect(it, paint) }
        for (y in bounds.top until bounds.bottom) for (x in bounds.left until bounds.right) {
            check(Color.red(erased.getPixel(x, y)) > 245) { "Old glyph remains at $x,$y" }
        }
        assertSamePixels(bitmap, erased, Rect(0, 20, 25, 80), "adjacent icon")
        val renderer = TranslationRenderer(bitmap, listOf(cut))
        renderer.paint(cut, "这是一段很长的译文".repeat(35))
        check(renderer.truncatedIds == setOf(0))
        assertSamePixels(bitmap, renderer.output, Rect(0, 100, 340, 180), "overflow into next content")
        erased.recycle(); renderer.output.recycle(); bitmap.recycle()
    }

    private fun assertSamePixels(a: Bitmap, b: Bitmap, box: Rect, label: String) {
        val first = IntArray(box.width())
        val second = IntArray(box.width())
        for (y in box.top until box.bottom) {
            a.getPixels(first, 0, box.width(), box.left, y, box.width(), 1)
            b.getPixels(second, 0, box.width(), box.left, y, box.width(), 1)
            check(first.contentEquals(second)) { "$label changed at row $y" }
        }
    }

    private fun fixtureTranslation(text: String): String? = when {
        text.contains("models were expected", true) -> "这些模型原定于本周发布，但 OpenAI 看到 Fable 5.2 的强劲表现后，可能推迟了发布，以便进一步改进。"
        text.contains("checkpoint", true) -> "Astra 6.2 检查点的表现确实令人印象深刻。它甚至能通过代码生成类似视频的输出，不过 Fable 整体上仍有优势。"
        text.contains("launch together next week", true) -> "我预计 Astra 和 Sol 将于下周一起发布。OpenAI 需要重大升级才能与 Anthropic 的最新模型竞争，尤其是 Fable 5.5 也可能即将问世。"
        text.contains("needs to deliver", true) -> "OpenAI 需要拿出成果。"
        text.contains("Astra and Sol", true) -> "GPT-6.2 Astra 和 Sol：更新"
        else -> null
    }
}
