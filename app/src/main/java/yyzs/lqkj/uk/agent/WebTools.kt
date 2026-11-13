package yyzs.lqkj.uk.agent

import android.net.Uri
import android.text.Html
import okhttp3.Request
import yyzs.lqkj.uk.translate.DeepSeekClient
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

data class SearchHit(val title: String, val url: String, val snippet: String)

/** Web access for the assistant: a search results page and a page reader. Blocking; call off the main thread. */
object WebTools {
    private const val UA = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"
    private const val MAX_PAGE_CHARS = 6000

    private val http = DeepSeekClient.http.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /** Parses Bing's results page (cn.bing.com works without a key, also inside mainland China). */
    fun search(query: String, limit: Int = 6): List<SearchHit> {
        val html = get("https://cn.bing.com/search?q=" + Uri.encode(query) + "&ensearch=0")
        return html.split("<li class=\"b_algo\"").drop(1).mapNotNull { block ->
            val item = block.substringBefore("<li class=\"b_")
            val title = H2.find(item)?.groupValues?.get(1)?.let(::plain).orEmpty()
            val url = HREF.findAll(item).map { it.groupValues[1] }.firstOrNull { !it.contains("bing.com") }
            val snippet = P.find(item)?.groupValues?.get(1)?.let(::plain).orEmpty()
            if (title.isBlank() || url == null) null else SearchHit(title, Html.fromHtml(url, 0).toString(), snippet)
        }.take(limit)
    }

    /** The readable text of a page, trimmed to a size the model can take in. */
    fun read(url: String): String {
        val text = runCatching { extract(get(url)) }.getOrDefault("")
        if (text.length >= 300) return text.take(MAX_PAGE_CHARS)
        // Script-rendered pages come back nearly empty; a reader service renders them.
        val rendered = runCatching { get("https://r.jina.ai/$url") }.getOrDefault("")
        return rendered.ifBlank { text }.take(MAX_PAGE_CHARS)
    }

    private fun get(url: String): String {
        val request = Request.Builder().url(url)
            .header("User-Agent", UA)
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.6")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw java.io.IOException("网页返回 ${response.code}")
            val body = response.body ?: return ""
            val bytes = body.bytes()
            val charset = body.contentType()?.charset()
                ?: META_CHARSET.find(String(bytes, 0, minOf(bytes.size, 2048), Charsets.ISO_8859_1))?.groupValues?.get(1)
                    ?.let { runCatching { Charset.forName(it) }.getOrNull() }
                ?: Charsets.UTF_8
            return String(bytes, charset)
        }
    }

    private fun extract(html: String): String {
        val title = TITLE.find(html)?.groupValues?.get(1)?.let(::plain).orEmpty()
        // fromHtml folds newlines into spaces, so mark block ends with a control character instead.
        val body = html
            .replace(DROP, " ")
            .replace(BLOCK_END, "\u0001")
        val text = plain(body).split('\u0001').map { it.trim() }.filter { it.length > 1 }.joinToString("\n")
        return if (title.isBlank()) text else "$title\n$text"
    }

    private fun plain(html: String) = Html.fromHtml(html.replace(TAG, " "), 0).toString()
        .replace(SPACES, " ").trim()

    private val H2 = Regex("<h2[^>]*>(.*?)</h2>", RegexOption.DOT_MATCHES_ALL)
    private val HREF = Regex("<a [^>]*href=\"(https?://[^\"]+)\"")
    private val P = Regex("<p[^>]*>(.*?)</p>", RegexOption.DOT_MATCHES_ALL)
    private val TITLE = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val META_CHARSET = Regex("charset=[\"']?([\\w-]+)", RegexOption.IGNORE_CASE)
    private val DROP = Regex(
        "<(script|style|noscript|svg|nav|footer|header|iframe|form)[^>]*>.*?</\\1>|<!--.*?-->",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    )
    private val BLOCK_END = Regex("</(p|div|li|h[1-6]|tr|section|article)>|<br\\s*/?>", RegexOption.IGNORE_CASE)
    private val TAG = Regex("<[^>]+>")
    private val SPACES = Regex("[ \\t\\u00A0\\u3000]+")
}
