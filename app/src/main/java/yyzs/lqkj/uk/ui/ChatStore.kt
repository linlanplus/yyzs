package yyzs.lqkj.uk.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ChatSummary(val id: String, val title: String, val updatedAt: Long)

class StoredMessage(val fromUser: Boolean, val text: String, val actions: List<String>, val steps: List<String>)

/** Past conversations, one small JSON file each in app-private storage. Blocking; call off the main thread. */
class ChatStore(context: Context) {
    private val dir = File(context.filesDir, "chats").apply { mkdirs() }

    fun list(): List<ChatSummary> = dir.listFiles { f -> f.extension == "json" }.orEmpty()
        .mapNotNull { f ->
            runCatching {
                val o = JSONObject(f.readText())
                ChatSummary(o.getString("id"), o.optString("title"), o.optLong("updatedAt"))
            }.getOrNull()
        }
        .sortedByDescending { it.updatedAt }

    fun load(id: String): List<StoredMessage> {
        val o = JSONObject(file(id).readText())
        val arr = o.getJSONArray("messages")
        return (0 until arr.length()).map { i ->
            val m = arr.getJSONObject(i)
            StoredMessage(m.optBoolean("user"), m.optString("text"), m.optJSONArray("actions").strings(), m.optJSONArray("steps").strings())
        }
    }

    fun save(id: String, messages: List<StoredMessage>) {
        if (messages.none { it.fromUser }) return
        val title = messages.first { it.fromUser }.text.replace('\n', ' ').take(40)
        val arr = JSONArray()
        for (m in messages) {
            arr.put(
                JSONObject().put("user", m.fromUser).put("text", m.text)
                    .put("actions", JSONArray(m.actions)).put("steps", JSONArray(m.steps)),
            )
        }
        val o = JSONObject().put("id", id).put("title", title).put("updatedAt", System.currentTimeMillis()).put("messages", arr)
        val tmp = File(dir, "$id.tmp")
        tmp.writeText(o.toString())
        tmp.renameTo(file(id))
        prune()
    }

    fun delete(id: String) {
        file(id).delete()
    }

    /** Keeps the most recent conversations only. */
    private fun prune() {
        list().drop(MAX_CHATS).forEach { delete(it.id) }
    }

    private fun file(id: String) = File(dir, "$id.json")

    private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else (0 until length()).map { getString(it) }

    private companion object {
        const val MAX_CHATS = 200
    }
}
