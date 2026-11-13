package yyzs.lqkj.uk

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import yyzs.lqkj.uk.translate.DeepSeekClient
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Checks the latest GitHub release. Inactive until [REPO] is set. */
object UpdateChecker {
    /** "owner/repo" on GitHub; empty until releases are published there. */
    private const val REPO = ""

    sealed interface Result {
        data object NotConfigured : Result
        data object UpToDate : Result
        data class Available(val version: String, val url: String, val notes: String) : Result
    }

    private val http by lazy { DeepSeekClient.http.newBuilder().callTimeout(15, TimeUnit.SECONDS).build() }

    suspend fun check(currentVersion: String): Result = withContext(Dispatchers.IO) {
        if (REPO.isBlank()) return@withContext Result.NotConfigured
        val request = Request.Builder()
            .url("https://api.github.com/repos/$REPO/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .build()
        val json = http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("GitHub 返回 ${response.code}")
            JSONObject(response.body?.string().orEmpty())
        }
        val latest = json.getString("tag_name")
        if (isNewer(latest, currentVersion)) {
            Result.Available(latest.removePrefix("v"), json.optString("html_url"), json.optString("body"))
        } else {
            Result.UpToDate
        }
    }

    /** Compares dotted numbers only; a leading "v" and build suffixes such as "-ocr-preview" are ignored. */
    internal fun isNewer(latest: String, current: String): Boolean {
        fun parts(v: String) = v.trim().removePrefix("v").substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val a = parts(latest)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
