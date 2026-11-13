package yyzs.lqkj.uk.agent

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import java.util.Locale

data class AppEntry(val label: String, val packageName: String, val system: Boolean)

/**
 * Launchable apps, so the assistant can open them by a vague name.
 * Some Chinese ROMs (MagicOS, MIUI…) guard the app list behind a runtime permission;
 * without it they may hide user apps from the query.
 */
object InstalledApps {
    /** The ROM's app-list permission (defined by MagicOS, MIUI, ColorOS…); absent on stock Android. */
    const val PERMISSION = "com.android.permission.GET_INSTALLED_APPS"

    private var cache: List<AppEntry>? = null
    private var cachedAt = 0L

    /** True when the ROM defines [PERMISSION]; then it has to be requested at runtime. */
    fun permissionDefined(context: Context): Boolean =
        runCatching { context.packageManager.getPermissionInfo(PERMISSION, 0) }.isSuccess

    fun permissionGranted(context: Context): Boolean =
        !permissionDefined(context) || context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    fun all(context: Context): List<AppEntry> {
        val now = System.currentTimeMillis()
        cache?.let { if (now - cachedAt < 60_000) return it }
        val pm = context.packageManager
        val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .distinctBy { it.activityInfo.packageName }
            .filter { it.activityInfo.packageName != context.packageName }
            .map {
                val flags = it.activityInfo.applicationInfo.flags
                AppEntry(
                    label = it.loadLabel(pm).toString().trim(),
                    packageName = it.activityInfo.packageName,
                    system = flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
                )
            }
        cache = apps
        cachedAt = now
        return apps
    }

    /** Apps the user installed, for the model's context; system apps are left out to keep it short. */
    fun userApps(context: Context): List<AppEntry> = all(context).filterNot { it.system }.sortedBy { it.label }

    /**
     * Best match for [query]: exact label, then label or package containment, then the label sharing the
     * most characters in order. Nicknames (“B站” for 哔哩哔哩) are left to the model, which sees the app list.
     */
    fun find(context: Context, query: String): AppEntry? {
        val q = normalize(query)
        if (q.isEmpty()) return null
        val apps = all(context)
        apps.firstOrNull { normalize(it.label) == q }?.let { return it }
        apps.filter { normalize(it.label).contains(q) || q.contains(normalize(it.label)) }
            .minByOrNull { it.label.length }?.let { return it }
        apps.firstOrNull { it.packageName.lowercase(Locale.ROOT).contains(q) }?.let { return it }
        val scored = apps.map { it to similarity(q, normalize(it.label)) }.maxByOrNull { it.second }
        return scored?.takeIf { it.second >= 0.6f }?.first
    }

    fun suggestions(context: Context, query: String, limit: Int = 8): List<AppEntry> {
        val q = normalize(query)
        return all(context).sortedByDescending { similarity(q, normalize(it.label)) }.take(limit)
    }

    private fun normalize(s: String) = s.lowercase(Locale.ROOT).filterNot { it.isWhitespace() || it == '·' || it == '-' }

    /** Longest common subsequence relative to the shorter string. */
    private fun similarity(a: String, b: String): Float {
        if (a.isEmpty() || b.isEmpty()) return 0f
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 1..a.length) for (j in 1..b.length) {
            dp[i][j] = if (a[i - 1] == b[j - 1]) dp[i - 1][j - 1] + 1 else maxOf(dp[i - 1][j], dp[i][j - 1])
        }
        return dp[a.length][b.length].toFloat() / minOf(a.length, b.length)
    }
}
