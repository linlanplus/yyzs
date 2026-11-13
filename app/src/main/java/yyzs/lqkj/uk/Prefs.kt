package yyzs.lqkj.uk

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** User settings. Stored in app-private storage; backups are disabled in the manifest. */
object Prefs {
    const val DEFAULT_BASE_URL = "https://api.deepseek.com"

    /** DeepSeek V4.1 Flash; the only DeepSeek API model that accepts images. */
    const val DEFAULT_MODEL = "deepseek-flash"

    const val DEFAULT_LIVE_URL = "wss://api.openai.com/v1/live/sessions"
    const val DEFAULT_LIVE_MODEL = "gpt-live-1"
    const val DEFAULT_LIVE_VOICE = "marin"

    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    }

    var apiKey: String
        get() = sp.getString("api_key", null).orEmpty()
        set(value) = sp.edit { putString("api_key", value.trim()) }

    var baseUrl: String
        get() = sp.getString("base_url", null)?.takeIf { it.isNotBlank() } ?: DEFAULT_BASE_URL
        set(value) = sp.edit { putString("base_url", value.trim()) }

    var model: String
        get() = sp.getString("model", null)?.takeIf { it.isNotBlank() } ?: DEFAULT_MODEL
        set(value) = sp.edit { putString("model", value.trim()) }

    var targetLang: String
        get() = sp.getString("target_lang", null) ?: "zh-Hans"
        set(value) = sp.edit { putString("target_lang", value) }

    /** Used for typed/spoken text that is already in the target language. */
    var secondaryLang: String
        get() = sp.getString("secondary_lang", null) ?: "en"
        set(value) = sp.edit { putString("secondary_lang", value) }

    /** Decides which OCR models run; see [Languages.sources]. */
    var sourceLang: String
        get() = sp.getString("source_lang", null) ?: "auto"
        set(value) = sp.edit { putString("source_lang", value) }

    var speechLang: String
        get() = sp.getString("speech_lang", null) ?: "zh-CN"
        set(value) = sp.edit { putString("speech_lang", value) }

    /** Flattened recognizer component; empty means pick automatically. */
    var speechService: String
        get() = sp.getString("speech_service", null).orEmpty()
        set(value) = sp.edit { putString("speech_service", value) }

    var autoTranslateOnWake: Boolean
        get() = sp.getBoolean("auto_translate", false)
        set(value) = sp.edit { putBoolean("auto_translate", value) }

    var deepByDefault: Boolean
        get() = sp.getBoolean("deep_default", false)
        set(value) = sp.edit { putBoolean("deep_default", value) }

    /** Show each screen-translation region as it arrives; existing installs keep this behavior. */
    var streamScreenTranslation: Boolean
        get() = sp.getBoolean("stream_screen_translation", true)
        set(value) = sp.edit { putBoolean("stream_screen_translation", value) }

    /** OpenAI key for GPT-Live voice conversations. Stored on the phone: this is a personal build without a server. */
    var openaiKey: String
        get() = sp.getString("openai_key", null).orEmpty()
        set(value) = sp.edit { putString("openai_key", value.trim()) }

    var liveUrl: String
        get() = sp.getString("live_url", null)?.takeIf { it.isNotBlank() } ?: DEFAULT_LIVE_URL
        set(value) = sp.edit { putString("live_url", value.trim()) }

    var liveModel: String
        get() = sp.getString("live_model", null)?.takeIf { it.isNotBlank() } ?: DEFAULT_LIVE_MODEL
        set(value) = sp.edit { putString("live_model", value.trim()) }

    var liveVoice: String
        get() = sp.getString("live_voice", null)?.takeIf { it.isNotBlank() } ?: DEFAULT_LIVE_VOICE
        set(value) = sp.edit { putString("live_voice", value.trim()) }

    /** Deep thinking for typed questions in the panel (slower, better for hard questions). */
    var deepThinking: Boolean
        get() = sp.getBoolean("deep_thinking", false)
        set(value) = sp.edit { putBoolean("deep_thinking", value) }

    /** Whether the ROM's app-list permission has been asked for once from the panel. */
    var askedAppList: Boolean
        get() = sp.getBoolean("asked_app_list", false)
        set(value) = sp.edit { putBoolean("asked_app_list", value) }

    /** Read replies aloud with the system TTS when the request was spoken. */
    var speakReplies: Boolean
        get() = sp.getBoolean("speak_replies", true)
        set(value) = sp.edit { putBoolean("speak_replies", value) }
}
