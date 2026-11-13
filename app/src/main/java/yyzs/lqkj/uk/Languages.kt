package yyzs.lqkj.uk

/** [promptName] is how the language is named inside model prompts. */
data class Lang(val code: String, val label: String, val promptName: String)

object Languages {
    val targets = listOf(
        Lang("zh-Hans", "中文", "简体中文"),
        Lang("zh-Hant", "繁体中文", "繁体中文"),
        Lang("en", "英语", "英语"),
        Lang("ja", "日语", "日语"),
        Lang("ko", "韩语", "韩语"),
        Lang("fr", "法语", "法语"),
        Lang("de", "德语", "德语"),
        Lang("es", "西班牙语", "西班牙语"),
        Lang("ru", "俄语", "俄语"),
    )

    /** Source choices map to OCR models: Chinese also covers Latin script; "auto" runs all three. */
    val sources = listOf(
        Lang("auto", "自动检测", ""),
        Lang("latin", "英语等", "英语或其他拉丁字母语言"),
        Lang("zh", "中文", "中文"),
        Lang("ja", "日语", "日语"),
        Lang("ko", "韩语", "韩语"),
    )

    val speech = listOf(
        "zh-CN" to "普通话",
        "yue-Hant-HK" to "粤语",
        "en-US" to "英语",
        "ja-JP" to "日语",
        "ko-KR" to "韩语",
    )

    fun target(code: String) = targets.firstOrNull { it.code == code } ?: targets[0]
    fun source(code: String) = sources.firstOrNull { it.code == code } ?: sources[0]

    /** New (source, target) for the ⇄ button, or null when the source is auto-detected. */
    fun swap(source: String, target: String): Pair<String, String>? {
        if (source == "auto") return null
        val newSource = when (target) {
            "zh-Hans", "zh-Hant" -> "zh"
            "ja" -> "ja"
            "ko" -> "ko"
            else -> "latin"
        }
        val newTarget = when (source) {
            "zh" -> "zh-Hans"
            "ja" -> "ja"
            "ko" -> "ko"
            else -> "en"
        }
        return newSource to newTarget
    }
}
