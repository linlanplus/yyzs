package yyzs.lqkj.uk.translate

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Every requested ID must be acknowledged, including explicit empty/unchanged translations. */
object RegionTranslationProtocol {
    private val linePattern = Regex("""^\s*(\d+)\s*[|｜]\s?(.*)$""")

    fun translate(
        ids: List<Int>,
        request: (missing: List<Int>, retry: Boolean) -> Flow<String>,
    ): Flow<Pair<Int, String>> = flow {
        require(ids.toSet().size == ids.size) { "Duplicate input region IDs" }
        val remaining = ids.toMutableSet()
        repeat(2) { attempt ->
            if (remaining.isEmpty()) return@flow
            val pending = StringBuilder()
            suspend fun accept(line: String) {
                val match = linePattern.matchEntire(line.trimEnd('\r')) ?: return
                val id = match.groupValues[1].toIntOrNull() ?: return
                // Ignore unknown/duplicate IDs so a repeated response cannot overwrite good text.
                if (remaining.remove(id)) emit(id to match.groupValues[2].trim())
            }
            request(ids.filter { it in remaining }, attempt > 0).collect { chunk ->
                pending.append(chunk)
                var newline = pending.indexOf("\n")
                while (newline >= 0) {
                    accept(pending.substring(0, newline))
                    pending.delete(0, newline + 1)
                    newline = pending.indexOf("\n")
                }
                check(pending.length <= 131_072) { "翻译响应格式异常，请重试" }
            }
            accept(pending.toString())
        }
        check(remaining.isEmpty()) { "还有 ${remaining.size} 处未收到译文（已补译一次），保留原文，请重试。" }
    }
}
