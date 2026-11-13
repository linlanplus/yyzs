package yyzs.lqkj.uk.agent

import android.net.Uri
import yyzs.lqkj.uk.Languages
import yyzs.lqkj.uk.Prefs
import yyzs.lqkj.uk.translate.ChatEvent
import yyzs.lqkj.uk.translate.DeepSeekClient
import yyzs.lqkj.uk.translate.ToolCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.json.JSONArray
import org.json.JSONObject

sealed interface AgentEvent {
    data class Text(val delta: String) : AgentEvent
    /** Deep-thinking reasoning, streamed before the answer of each round. */
    data class Reasoning(val delta: String) : AgentEvent
    /** A tool is about to run; [label] is a short description for the UI. */
    data class Started(val name: String, val label: String) : AgentEvent
    data class Action(val name: String, val result: ActionResult) : AgentEvent
}

/**
 * A multi-turn conversation with DeepSeek that can call [PhoneActions], search the web and think.
 * One conversation per panel; earlier ones can be resumed with [restore].
 */
class AssistantAgent(private val actions: PhoneActions) {
    private val history = mutableListOf<JSONObject>()

    fun reset() = history.clear()

    /** Continues a saved conversation: user/assistant text only, tool details are not kept. */
    fun restore(turns: List<Pair<Boolean, String>>) {
        history.clear()
        for ((fromUser, text) in turns) {
            if (text.isNotBlank()) history += DeepSeekClient.message(if (fromUser) "user" else "assistant", text)
        }
        trim()
    }

    fun send(userText: String, spoken: Boolean = false, thinking: Boolean = false): Flow<AgentEvent> = flow {
        compactOldTools()
        history += DeepSeekClient.message("user", userText)
        trim()

        repeat(MAX_ROUNDS) { round ->
            val reply = StringBuilder()
            val reasoning = StringBuilder()
            val calls = mutableListOf<ToolCall>()
            // In the last round tools are switched off, so the model has to answer with what it found.
            DeepSeekClient.chat(
                listOf(system(spoken)) + history, actions.tools,
                maxTokens = if (thinking) 8192 else 3072,
                thinking = thinking,
                allowTools = round < MAX_ROUNDS - 1,
            ).collect { e ->
                when (e) {
                    is ChatEvent.Text -> { reply.append(e.delta); emit(AgentEvent.Text(e.delta)) }
                    is ChatEvent.Reasoning -> { reasoning.append(e.delta); emit(AgentEvent.Reasoning(e.delta)) }
                    is ChatEvent.Call -> calls += e.call
                }
            }
            val assistant = JSONObject().put("role", "assistant").put("content", reply.toString())
            // With tools in the request, thinking mode needs earlier reasoning sent back.
            if (reasoning.isNotEmpty()) assistant.put("reasoning_content", reasoning.toString())
            if (calls.isEmpty()) {
                history += assistant
                return@flow
            }
            assistant.put("tool_calls", JSONArray(calls.map { c ->
                JSONObject().put("id", c.id).put("type", "function")
                    .put("function", JSONObject().put("name", c.name).put("arguments", c.arguments))
            }))
            history += assistant
            for (c in calls) {
                emit(AgentEvent.Started(c.name, describe(c)))
                val result = actions.execute(c.name, c.arguments)
                emit(AgentEvent.Action(c.name, result))
                history += JSONObject().put("role", "tool").put("tool_call_id", c.id).put("content", result.output)
            }
        }
    }

    private fun describe(call: ToolCall): String {
        val args = runCatching { JSONObject(call.arguments) }.getOrDefault(JSONObject())
        return when (call.name) {
            "search_web" -> "正在搜索：" + args.optString("query")
            "read_webpage" -> "正在阅读：" + Uri.parse(args.optString("url")).host.orEmpty().removePrefix("www.")
            "open_app" -> "正在打开" + args.optString("name")
            else -> "正在执行…"
        }
    }

    private fun trim() {
        while (history.size > MAX_HISTORY) history.removeAt(0)
        // Never start with a dangling assistant or tool message.
        while (history.isNotEmpty() && history[0].optString("role") != "user") history.removeAt(0)
    }

    /** Web pages from earlier questions are rarely needed again; keep only their beginning. */
    private fun compactOldTools() {
        for (m in history) {
            if (m.optString("role") != "tool") continue
            val content = m.optString("content")
            if (content.length > OLD_TOOL_CHARS) m.put("content", content.take(OLD_TOOL_CHARS) + "…（已省略）")
        }
    }

    private fun system(spoken: Boolean): JSONObject {
        val target = Languages.target(Prefs.targetLang).promptName
        val secondary = Languages.target(Prefs.secondaryLang).promptName
        val apps = actions.appList()
        // Stable parts first so the API's prefix cache keeps working; the clock changes every minute.
        val text = buildString {
            appendLine("你是手机上的智能助手，名字叫“翻译助手”。")
            appendLine("1. 用户只发来一段外语、没有其他指令时，直接给出$target 译文；原文已经是$target 时译成$secondary。只输出译文。")
            appendLine("2. 用户要求翻译时，按要求的语言翻译，只输出译文。")
            appendLine("3. 用户要求操作手机（打开应用、闹钟、计时、手电筒、导航、调音量、打开设置、翻译屏幕等）时调用工具，完成后用一句话说明结果。工具返回失败时如实告诉用户。")
            appendLine("4. 打开应用时，从下面的已安装应用列表里找出用户指的那个（可能是简称、别名、拼音或英文名，例如“B站”是哔哩哔哩），用列表里的原名调用 open_app；系统自带应用（相机、设置、时钟等）不在列表里，直接用常见名称。")
            appendLine("5. 涉及新闻、价格、天气、版本、近期事件，或你不确定的事实时，先用 search_web 搜索；摘要不够就用 read_webpage 读一两个最相关的网页，再综合回答，并在末尾用“来源：网站名”注明。不要编造。")
            appendLine("6. 闲聊和简单问题简短回答；用户要资料、解释、比较、建议时可以展开，条理清楚。")
            if (spoken) appendLine("7. 回答会被朗读出来：不要用 Markdown、列表符号或表情，数字和单位写成口语，尽量控制在五句话以内。")
            else appendLine("7. 不要用 Markdown 符号（如 **、#、表格）；需要分条时用 1. 2. 3. 编号。")
            if (apps.isNotBlank()) appendLine("已安装应用：$apps")
            appendLine(actions.context())
        }
        return DeepSeekClient.message("system", text)
    }

    private companion object {
        const val MAX_ROUNDS = 6
        const val MAX_HISTORY = 40
        const val OLD_TOOL_CHARS = 600
    }
}
