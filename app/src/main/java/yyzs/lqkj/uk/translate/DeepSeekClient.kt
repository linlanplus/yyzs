package yyzs.lqkj.uk.translate

import yyzs.lqkj.uk.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapNotNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiException(message: String) : IOException(message)

data class ToolCall(val id: String, val name: String, val arguments: String)

sealed interface ChatEvent {
    data class Text(val delta: String) : ChatEvent
    /** Thinking-mode reasoning, streamed before the answer. */
    data class Reasoning(val delta: String) : ChatEvent
    /** Emitted once per call, after its arguments have fully streamed in. */
    data class Call(val call: ToolCall) : ChatEvent
}

/** Minimal OpenAI-compatible chat client for the DeepSeek API (streaming only). */
object DeepSeekClient {
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()

    fun textPart(text: String) = JSONObject().put("type", "text").put("text", text)

    fun imagePart(dataUrl: String) = JSONObject()
        .put("type", "image_url")
        .put("image_url", JSONObject().put("url", dataUrl).put("detail", "high"))

    fun message(role: String, content: Any) = JSONObject().put("role", role).put("content", content)

    /** Streams `delta.content` chunks. */
    fun stream(messages: List<JSONObject>, maxTokens: Int = 8192): Flow<String> =
        chat(messages, tools = null, maxTokens = maxTokens).mapNotNull { (it as? ChatEvent.Text)?.delta }

    /**
     * Streams text and, when [tools] are given, completed tool calls.
     * Thinking is off unless asked for: translation and phone commands need speed, not reasoning.
     */
    fun chat(
        messages: List<JSONObject>,
        tools: JSONArray?,
        maxTokens: Int = 8192,
        thinking: Boolean = false,
        /** False sends the tools with `tool_choice: none`, forcing a text answer. */
        allowTools: Boolean = true,
    ): Flow<ChatEvent> = flow {
        val key = Prefs.apiKey
        if (key.isBlank()) throw ApiException("请先在设置里填写对话模型的 API Key")

        val body = JSONObject()
            .put("model", Prefs.model)
            .put("messages", JSONArray(messages))
            .put("stream", true)
            .put("max_tokens", maxTokens)
            .put("thinking", JSONObject().put("type", if (thinking) "enabled" else "disabled"))
        // Temperature has no effect in thinking mode.
        if (thinking) body.put("reasoning_effort", "high") else body.put("temperature", 0.3)
        if (tools != null) {
            body.put("tools", tools)
            if (!allowTools) body.put("tool_choice", "none")
        }

        val request = Request.Builder()
            .url(Prefs.baseUrl.trimEnd('/') + "/chat/completions")
            .header("Authorization", "Bearer $key")
            .header("Accept", "text/event-stream")
            .post(body.toString().toRequestBody(JSON))
            .build()

        // Tool calls stream in pieces keyed by index: id and name first, then argument fragments.
        val ids = HashMap<Int, String>()
        val names = HashMap<Int, String>()
        val args = HashMap<Int, StringBuilder>()

        val call = http.newCall(request)
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) throw ApiException(describeError(response.code, response.body?.string()))
                val source = response.body!!.source()
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.substring(5).trim()
                    if (data == "[DONE]") break
                    val choice = JSONObject(data).optJSONArray("choices")?.optJSONObject(0) ?: continue
                    val delta = choice.optJSONObject("delta")
                    val reasoning = delta?.optString("reasoning_content").orEmpty()
                    if (reasoning.isNotEmpty() && reasoning != "null") emit(ChatEvent.Reasoning(reasoning))
                    val content = delta?.optString("content").orEmpty()
                    if (content.isNotEmpty() && content != "null") emit(ChatEvent.Text(content))
                    delta?.optJSONArray("tool_calls")?.let { calls ->
                        for (i in 0 until calls.length()) {
                            val c = calls.getJSONObject(i)
                            val index = c.optInt("index", i)
                            c.optString("id").takeIf { it.isNotEmpty() }?.let { ids[index] = it }
                            val fn = c.optJSONObject("function") ?: continue
                            fn.optString("name").takeIf { it.isNotEmpty() }?.let { names[index] = it }
                            args.getOrPut(index) { StringBuilder() }.append(fn.optString("arguments"))
                        }
                    }
                    when (choice.optString("finish_reason")) {
                        "length" -> throw ApiException("回复过长被截断")
                        "tool_calls" -> Unit
                    }
                }
            }
        } finally {
            call.cancel()
        }
        for (index in names.keys.sorted()) {
            emit(ChatEvent.Call(ToolCall(ids[index] ?: "call_$index", names[index]!!, args[index]?.toString().orEmpty())))
        }
    }.flowOn(Dispatchers.IO)

    private fun describeError(code: Int, body: String?): String {
        val detail = runCatching { JSONObject(body!!).getJSONObject("error").getString("message") }.getOrNull()
        val hint = when (code) {
            401 -> "API Key 无效"
            402 -> "账户余额不足"
            422 -> "请求参数错误"
            429 -> "请求太频繁，请稍后再试"
            in 500..599 -> "模型服务繁忙"
            else -> "请求失败"
        }
        return if (detail.isNullOrBlank()) "$hint（$code）" else "$hint（$code）：$detail"
    }
}
