package yyzs.lqkj.uk.agent

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * What a tool call did. [output] goes back to the model; [launches] means another app comes to the front,
 * so the panel should close. Research tools set [step], a short line shown instead of the raw output.
 */
data class ActionResult(val output: String, val launches: Boolean = false, val step: String? = null)

/** Phone operations exposed to the model as function tools. */
class PhoneActions(private val context: Context, private val onTranslateScreen: (deep: Boolean) -> Unit) {

    val tools: JSONArray = JSONArray().apply {
        put(tool("open_app", "打开手机上已安装的应用。名称尽量用系统提示里应用列表中的原名", "name" to str("应用名称，例如 微信、抖音、设置、相机")))
        put(tool("search_web", "联网搜索，返回标题、链接和摘要。用于新闻、实时信息、你不确定的事实", "query" to str("搜索关键词，简洁具体")))
        put(tool("read_webpage", "读取网页正文。搜索摘要不够详细时，用它打开最相关的一两个链接", "url" to str("完整网址")))
        put(tool("open_browser_search", "在浏览器里打开搜索结果页给用户看（只有用户明确要求在浏览器里搜时才用）", "query" to str("搜索内容")))
        put(
            tool(
                "set_alarm", "设置闹钟",
                "hour" to int("0-23 时"), "minute" to int("0-59 分"), "label" to str("闹钟备注，可空"),
                required = listOf("hour", "minute"),
            ),
        )
        put(tool("set_timer", "设置倒计时", "seconds" to int("倒计时秒数"), "label" to str("备注，可空"), required = listOf("seconds")))
        put(tool("flashlight", "打开或关闭手电筒", "on" to bool("true 打开，false 关闭")))
        put(tool("dial", "打开拨号界面并填好号码（不会直接拨出）", "number" to str("电话号码")))
        put(tool("navigate", "用地图导航到目的地", "destination" to str("目的地名称或地址")))
        put(
            tool(
                "open_settings", "打开系统设置的某个页面",
                "page" to enumStr("页面", listOf("wifi", "bluetooth", "display", "sound", "battery", "location", "apps", "storage", "notification", "home")),
            ),
        )
        put(
            tool(
                "set_volume", "调整媒体音量",
                "percent" to int("0-100 的音量百分比"),
            ),
        )
        put(tool("translate_screen", "翻译当前屏幕上的内容", "deep" to bool("true 使用深度翻译（截图发给模型，更准但更慢）"), required = emptyList()))
    }

    /** Facts the model can't know otherwise; sent with every request. */
    fun context(): String {
        val now = SimpleDateFormat("yyyy-MM-dd EEEE HH:mm", Locale.CHINA).format(Date())
        val battery = context.getSystemService(BatteryManager::class.java)?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return "当前时间：$now；电量：${battery ?: "未知"}%"
    }

    /** Launchable apps the user installed, for the system prompt, so vague names can be resolved. */
    fun appList(): String = InstalledApps.userApps(context).joinToString("、") { it.label }

    suspend fun execute(name: String, rawArgs: String): ActionResult {
        val args = runCatching { JSONObject(rawArgs.ifBlank { "{}" }) }.getOrDefault(JSONObject())
        return try {
            when (name) {
                "open_app" -> openApp(args.optString("name"))
                "search_web" -> withContext(Dispatchers.IO) { searchWeb(args.optString("query")) }
                "read_webpage" -> withContext(Dispatchers.IO) { readWebpage(args.optString("url")) }
                "open_browser_search" -> launch(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://www.bing.com/search?q=" + Uri.encode(args.optString("query")))),
                    "已打开搜索：${args.optString("query")}",
                )
                "set_alarm" -> launch(
                    Intent(AlarmClock.ACTION_SET_ALARM)
                        .putExtra(AlarmClock.EXTRA_HOUR, args.optInt("hour"))
                        .putExtra(AlarmClock.EXTRA_MINUTES, args.optInt("minute"))
                        .putExtra(AlarmClock.EXTRA_MESSAGE, args.optString("label"))
                        .putExtra(AlarmClock.EXTRA_SKIP_UI, true),
                    "已设置 %02d:%02d 的闹钟".format(args.optInt("hour"), args.optInt("minute")),
                    launches = false,
                )
                "set_timer" -> launch(
                    Intent(AlarmClock.ACTION_SET_TIMER)
                        .putExtra(AlarmClock.EXTRA_LENGTH, args.optInt("seconds"))
                        .putExtra(AlarmClock.EXTRA_MESSAGE, args.optString("label"))
                        .putExtra(AlarmClock.EXTRA_SKIP_UI, true),
                    "已设置 ${args.optInt("seconds")} 秒倒计时",
                    launches = false,
                )
                "flashlight" -> flashlight(args.optBoolean("on", true))
                "dial" -> launch(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(args.optString("number")))), "已打开拨号界面")
                "navigate" -> navigate(args.optString("destination"))
                "open_settings" -> launch(Intent(settingsAction(args.optString("page"))), "已打开设置")
                "set_volume" -> setVolume(args.optInt("percent", 50))
                "translate_screen" -> {
                    onTranslateScreen(args.optBoolean("deep", false))
                    ActionResult("正在翻译屏幕")
                }
                else -> ActionResult("未知工具：$name")
            }
        } catch (e: ActivityNotFoundException) {
            ActionResult("失败：手机上没有能处理这个操作的应用")
        } catch (e: Exception) {
            ActionResult("失败：${e.message}")
        }
    }

    private fun openApp(query: String): ActionResult {
        if (query.isBlank()) return ActionResult("失败：没有说要打开哪个应用")
        val match = InstalledApps.find(context, query)
            ?: return ActionResult(
                "失败：没有找到“$query”。最接近的应用：" + InstalledApps.suggestions(context, query).joinToString("、") { it.label } +
                    "。如果其中有用户要的，用它的原名再调用一次",
            )
        val intent = context.packageManager.getLaunchIntentForPackage(match.packageName)
            ?: return ActionResult("失败：${match.label} 不能直接打开")
        return launch(intent, "已打开${match.label}")
    }

    private fun searchWeb(query: String): ActionResult {
        if (query.isBlank()) return ActionResult("失败：搜索词为空")
        val hits = runCatching { WebTools.search(query) }.getOrElse { return ActionResult("搜索失败：${it.message}", step = "搜索失败") }
        if (hits.isEmpty()) return ActionResult("没有搜到结果，可以换个关键词", step = "搜索：$query")
        val text = hits.mapIndexed { i, h -> "${i + 1}. ${h.title}\n${h.url}\n${h.snippet}" }.joinToString("\n\n")
        return ActionResult(text, step = "搜索：$query")
    }

    private fun readWebpage(url: String): ActionResult {
        if (!url.startsWith("http")) return ActionResult("失败：网址无效")
        val host = Uri.parse(url).host.orEmpty().removePrefix("www.")
        val text = runCatching { WebTools.read(url) }.getOrElse { return ActionResult("读取失败：${it.message}", step = "读取失败：$host") }
        return ActionResult(text.ifBlank { "这个网页没有可读的正文" }, step = "阅读：$host")
    }

    private fun navigate(destination: String): ActionResult {
        val amap = Intent(Intent.ACTION_VIEW, Uri.parse("amapuri://route/plan/?dname=${Uri.encode(destination)}&dev=0&t=0"))
        if (amap.resolveActivity(context.packageManager) != null) return launch(amap, "已用高德地图导航到 $destination")
        val baidu = Intent(Intent.ACTION_VIEW, Uri.parse("baidumap://map/direction?destination=${Uri.encode(destination)}&coord_type=gcj02&mode=driving"))
        if (baidu.resolveActivity(context.packageManager) != null) return launch(baidu, "已用百度地图导航到 $destination")
        return launch(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(destination))), "已打开地图搜索 $destination")
    }

    private fun flashlight(on: Boolean): ActionResult {
        val cm = context.getSystemService(CameraManager::class.java)
        val id = cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
            ?: return ActionResult("失败：这台手机没有闪光灯")
        cm.setTorchMode(id, on)
        return ActionResult(if (on) "手电筒已打开" else "手电筒已关闭")
    }

    private fun setVolume(percent: Int): ActionResult {
        val am = context.getSystemService(AudioManager::class.java)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, (max * percent.coerceIn(0, 100) / 100f).toInt(), AudioManager.FLAG_SHOW_UI)
        return ActionResult("媒体音量已调到 ${percent.coerceIn(0, 100)}%")
    }

    private fun settingsAction(page: String) = when (page) {
        "wifi" -> Settings.ACTION_WIFI_SETTINGS
        "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
        "display" -> Settings.ACTION_DISPLAY_SETTINGS
        "sound" -> Settings.ACTION_SOUND_SETTINGS
        "battery" -> Intent.ACTION_POWER_USAGE_SUMMARY
        "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
        "apps" -> Settings.ACTION_APPLICATION_SETTINGS
        "storage" -> Settings.ACTION_INTERNAL_STORAGE_SETTINGS
        "notification" -> Settings.ACTION_ALL_APPS_NOTIFICATION_SETTINGS
        else -> Settings.ACTION_SETTINGS
    }

    private fun launch(intent: Intent, done: String, launches: Boolean = true): ActionResult {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return ActionResult(done, launches)
    }

    private fun tool(name: String, description: String, vararg params: Pair<String, JSONObject>, required: List<String>? = null) =
        JSONObject()
            .put("type", "function")
            .put(
                "function",
                JSONObject()
                    .put("name", name)
                    .put("description", description)
                    .put(
                        "parameters",
                        JSONObject()
                            .put("type", "object")
                            .put("properties", JSONObject().apply { params.forEach { (k, v) -> put(k, v) } })
                            .put("required", JSONArray(required ?: params.map { it.first })),
                    ),
            )

    private fun str(d: String) = JSONObject().put("type", "string").put("description", d)
    private fun int(d: String) = JSONObject().put("type", "integer").put("description", d)
    private fun bool(d: String) = JSONObject().put("type", "boolean").put("description", d)
    private fun enumStr(d: String, values: List<String>) = str(d).put("enum", JSONArray(values))

}
