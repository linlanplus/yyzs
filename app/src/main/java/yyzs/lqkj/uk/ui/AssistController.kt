package yyzs.lqkj.uk.ui

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import yyzs.lqkj.uk.Prefs
import yyzs.lqkj.uk.agent.AgentEvent
import yyzs.lqkj.uk.agent.AssistantAgent
import yyzs.lqkj.uk.ocr.OcrEngine
import yyzs.lqkj.uk.ocr.TextRegion
import yyzs.lqkj.uk.render.TranslationRenderer
import yyzs.lqkj.uk.translate.Translator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatMessage(val fromUser: Boolean, text: String = "", loading: Boolean = !fromUser) {
    val id = nextId++
    var text by mutableStateOf(text)
    var loading by mutableStateOf(loading)
    var error by mutableStateOf<String?>(null)
    /** Phone actions the assistant performed for this reply, shown as small chips. */
    val actions = mutableStateListOf<String>()
    /** Research steps (searches, pages read), shown as grey lines above the answer. */
    val steps = mutableStateListOf<String>()
    /** The tool currently running, e.g. “正在搜索：…”. */
    var status by mutableStateOf<String?>(null)
    var reasoning by mutableStateOf("")
    /** How long deep thinking took, once the answer starts. */
    var thoughtMillis by mutableStateOf<Long?>(null)

    private companion object { var nextId = 0L }
}

class ScreenResult(val original: Bitmap, val deep: Boolean) {
    var renderer by mutableStateOf<TranslationRenderer?>(null)
    /** Bumped after every paint so the preview redraws the mutated bitmap. */
    var version by mutableIntStateOf(0)
    var status by mutableStateOf<String?>("识别文字…")
    var error by mutableStateOf<String?>(null)
    var showOriginal by mutableStateOf(false)
    var regions: List<TextRegion> = emptyList()
    val translations = sortedMapOf<Int, String>()

    fun translatedText(): String = regions
        .map { region -> translations[region.id]?.ifBlank { region.text } ?: region.text }
        .joinToString("\n")

    /** Buffer both the text and drawing in one-shot mode. Failure/cancellation never publishes a partial result. */
    internal suspend fun renderTranslations(streaming: Boolean, incoming: Flow<Pair<Int, String>>): TranslationRenderer {
        val target = TranslationRenderer(original, regions)
        val byId = regions.associateBy { it.id }
        val received = sortedMapOf<Int, String>()
        val pending = mutableListOf<TranslationRenderer.Prepared>()
        if (streaming) renderer = target
        status = (if (deep) "深度翻译中 " else "翻译中 ") + "0/${regions.size}"
        incoming.collect { (id, translation) ->
            val region = byId[id] ?: return@collect
            val prepared = withContext(Dispatchers.Default) { target.prepare(region, translation) }
            received[id] = translation
            if (streaming) {
                target.apply(prepared)
                translations[id] = translation
                version++
            } else {
                pending += prepared
            }
            status = (if (deep) "深度翻译中 " else "翻译中 ") + "${received.size}/${regions.size}"
        }
        if (!streaming && received.isNotEmpty()) {
            target.applyBatch(pending)
            translations.putAll(received)
            renderer = target
            version++
        }
        return target
    }
}

/** Everything [AssistActivity] shows; lives as long as the activity. */
class AssistController(
    private val scope: CoroutineScope,
    private val statusBarHeight: Int,
    private val agent: AssistantAgent,
    /** Called after a reply finishes; spoken requests get spoken replies. */
    private val onReply: (text: String, spoken: Boolean) -> Unit,
    private val store: ChatStore,
) {
    var screenshot by mutableStateOf<Bitmap?>(null)
    var screenshotMissing by mutableStateOf(false)
    val messages = mutableStateListOf<ChatMessage>()
    val busy: Boolean get() = messages.lastOrNull()?.loading == true
    var screen by mutableStateOf<ScreenResult?>(null)
    var showingScreen by mutableStateOf(false)
    var source by mutableStateOf(Prefs.sourceLang)
    var target by mutableStateOf(Prefs.targetLang)
    var message by mutableStateOf<String?>(null)
    var thinking by mutableStateOf(Prefs.deepThinking)
        private set
    /** Saved conversations for the history drawer, newest first. */
    val history = mutableStateListOf<ChatSummary>()
    /** The conversation on screen; null until its first message. */
    var conversationId by mutableStateOf<String?>(null)
        private set

    private var textJob: Job? = null
    private var screenJob: Job? = null

    fun reset() {
        textJob?.cancel()
        screenJob?.cancel()
        messages.clear()
        agent.reset()
        conversationId = null
        screen = null
        showingScreen = false
        message = null
    }

    fun toggleThinking() {
        thinking = !thinking
        Prefs.deepThinking = thinking
    }

    /** Stops the reply being generated; what arrived so far stays. */
    fun stop() {
        textJob?.cancel()
        val last = messages.lastOrNull()
        if (last != null && last.loading) {
            last.loading = false
            last.status = null
        }
        save()
    }

    fun refreshHistory() {
        scope.launch {
            val list = withContext(Dispatchers.IO) { store.list() }
            history.clear()
            history += list
        }
    }

    fun newConversation() {
        textJob?.cancel()
        messages.clear()
        agent.reset()
        conversationId = null
        message = null
    }

    /** Shows a saved conversation so it can be continued. */
    fun open(id: String) {
        textJob?.cancel()
        scope.launch {
            val stored = runCatching { withContext(Dispatchers.IO) { store.load(id) } }.getOrElse {
                message = "这条对话记录已损坏"
                return@launch
            }
            messages.clear()
            for (m in stored) {
                messages += ChatMessage(m.fromUser, m.text, loading = false).apply {
                    actions += m.actions
                    steps += m.steps
                }
            }
            agent.restore(stored.map { it.fromUser to it.text })
            conversationId = id
            message = null
        }
    }

    fun delete(id: String) {
        history.removeAll { it.id == id }
        if (conversationId == id) newConversation()
        scope.launch(Dispatchers.IO) { store.delete(id) }
    }

    private fun save() {
        val id = conversationId ?: return
        val snapshot = messages.filter { it.fromUser || it.text.isNotEmpty() || it.actions.isNotEmpty() }.map {
            StoredMessage(it.fromUser, it.text, it.actions.toList(), it.steps.toList())
        }
        scope.launch(Dispatchers.IO) { store.save(id, snapshot) }
    }

    fun setLanguages(newSource: String = source, newTarget: String = target) {
        val changed = newSource != source || newTarget != target
        source = newSource
        target = newTarget
        Prefs.sourceLang = newSource
        Prefs.targetLang = newTarget
        if (!changed) return
        screen?.let { if (showingScreen) translateScreen(it.deep) }
    }

    /** Sends typed or spoken input to the assistant (translation, phone actions or a short answer). */
    fun send(input: String, spoken: Boolean = false) {
        val content = input.trim()
        if (content.isEmpty()) return
        textJob?.cancel()
        messages.lastOrNull()?.loading = false
        messages += ChatMessage(fromUser = true, text = content)
        val reply = ChatMessage(fromUser = false)
        messages += reply
        message = null
        if (conversationId == null) conversationId = System.currentTimeMillis().toString()
        val started = System.currentTimeMillis()
        textJob = scope.launch {
            try {
                agent.send(content, spoken, thinking && !spoken).collect { e ->
                    when (e) {
                        is AgentEvent.Reasoning -> reply.reasoning += e.delta
                        is AgentEvent.Text -> {
                            if (reply.reasoning.isNotEmpty() && reply.thoughtMillis == null) {
                                reply.thoughtMillis = System.currentTimeMillis() - started
                            }
                            reply.text += e.delta
                        }
                        is AgentEvent.Started -> {
                            reply.status = e.label
                            // The next round thinks again; keep its reasoning apart from this one.
                            if (reply.reasoning.isNotEmpty() && !reply.reasoning.endsWith("\n\n")) reply.reasoning = reply.reasoning.trimEnd() + "\n\n"
                        }
                        is AgentEvent.Action -> {
                            reply.status = null
                            val step = e.result.step
                            if (step != null) reply.steps += step else reply.actions += e.result.output
                        }
                    }
                }
                if (reply.text.isBlank() && reply.actions.isEmpty()) reply.text = "（没有回复）"
                onReply(reply.text.ifBlank { reply.actions.joinToString("，") }, spoken)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reply.error = e.message ?: "请求失败"
            } finally {
                reply.loading = false
                reply.status = null
                if (reply.reasoning.isNotEmpty() && reply.thoughtMillis == null) {
                    reply.thoughtMillis = System.currentTimeMillis() - started
                }
                save()
            }
        }
    }

    fun translateScreen(deep: Boolean) {
        val bitmap = screenshot
        if (bitmap == null) {
            message = "拿不到屏幕截图：当前应用禁止截屏，或系统设置里关闭了“使用屏幕截图”"
            return
        }
        screenJob?.cancel()
        val result = ScreenResult(bitmap, deep)
        val streaming = Prefs.streamScreenTranslation
        screen = result
        showingScreen = true
        screenJob = scope.launch {
            try {
                val regions = OcrEngine.recognize(bitmap, source, statusBarHeight)
                result.regions = regions
                if (regions.isEmpty()) {
                    result.error = "屏幕上没有识别到文字"
                    return@launch
                }
                val renderer = result.renderTranslations(streaming,
                    Translator.translateRegions(regions, target, if (deep) bitmap else null))
                if (result.translations.isEmpty()) result.error = "模型没有返回译文，请重试"
                else if (renderer.truncatedIds.isNotEmpty()) result.error = "部分译文较长，画面中已省略；复制译文可查看全文。"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                result.error = e.message ?: "翻译失败"
            } finally {
                result.status = null
            }
        }
    }
}
