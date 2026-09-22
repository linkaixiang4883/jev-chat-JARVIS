package com.jev.probe.core

import android.content.Context

import com.jev.probe.jev.JevEndpoints

/**
 * App-private config store. Holds the OpenCode/TypeSafe keys, model choices, the
 * relationship description used in Jev's state, and the conversation whitelist.
 *
 * Key handling: stored in app-private SharedPreferences (not world-readable,
 * never logged, never in code/git). Hardening to EncryptedSharedPreferences is
 * a follow-up; on the user's own device app-private storage is the MVP bar.
 */
class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("jev_assistant", Context.MODE_PRIVATE)

    /** OpenCode API Key（sk- 开头）：Zen 的 Jev + Go 的起草共用。 */
    var opencodeKey: String
        get() = sp.getString(K_OPENCODE_KEY, "") ?: ""
        set(v) = sp.edit().putString(K_OPENCODE_KEY, v.trim()).apply()

    /** TypeSafe 官方 key：仅在 jevProvider = typesafe 时使用。 */
    var typesafeKey: String
        get() = sp.getString(K_TYPESAFE_KEY, "") ?: ""
        set(v) = sp.edit().putString(K_TYPESAFE_KEY, v.trim()).apply()

    /** Jev 判断后端：JevEndpoints.PROVIDER_ZEN（默认，免费 jev-1.13-free）或 PROVIDER_TYPESAFE。 */
    var jevProvider: String
        get() = sp.getString(K_JEV_PROVIDER, JevEndpoints.PROVIDER_ZEN) ?: JevEndpoints.PROVIDER_ZEN
        set(v) = sp.edit().putString(K_JEV_PROVIDER, v).apply()

    /** Jev 模型：留空 = 按后端取默认（zen → jev-1.13-free；typesafe → jev-latest）。 */
    var jevModel: String
        get() = sp.getString(K_JEV_MODEL, "") ?: ""
        set(v) = sp.edit().putString(K_JEV_MODEL, v.trim()).apply()

    /** OpenCode Go 的会话 id：首次读取即生成并落盘，所有 Go 请求共用（缺它 → 400 MissingSessionID）。 */
    val opencodeSession: String
        get() {
            val cur = sp.getString(K_SESSION, "").orEmpty()
            if (cur.isNotBlank()) return cur
            val gen = java.util.UUID.randomUUID().toString()
            sp.edit().putString(K_SESSION, gen).apply()
            return gen
        }

    /** Generative model for drafting the 3 candidate replies (OpenCode Go). */
    var replyModel: String
        get() = sp.getString(K_REPLY_MODEL, DEFAULT_REPLY_MODEL) ?: DEFAULT_REPLY_MODEL
        set(v) = sp.edit().putString(K_REPLY_MODEL, v.trim()).apply()

    /** Free-text describing who the other person is; goes into Jev's state. */
    var relationship: String
        get() = sp.getString(K_REL, DEFAULT_REL) ?: DEFAULT_REL
        set(v) = sp.edit().putString(K_REL, v).apply()

    /** Master on/off for showing the overlay + running analysis. */
    var enabled: Boolean
        get() = sp.getBoolean(K_ENABLED, true)
        set(v) = sp.edit().putBoolean(K_ENABLED, v).apply()

    /**
     * Conversation whitelist: titles the assistant is allowed to act on. Empty
     * set means "all conversations". Stored as a plain string set.
     */
    var whitelist: Set<String>
        get() = sp.getStringSet(K_WHITELIST, emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet(K_WHITELIST, v).apply()

    /** Overlay panel opacity, 60..100 (%). Lower lets the chat show through. */
    var overlayOpacity: Int
        get() = sp.getInt(K_OPACITY, 92).coerceIn(60, 100)
        set(v) = sp.edit().putInt(K_OPACITY, v.coerceIn(60, 100)).apply()

    /** Remembered vertical position of the bubble (px); -1 = default. */
    var bubbleY: Int
        get() = sp.getInt(K_BUBBLE_Y, -1)
        set(v) = sp.edit().putInt(K_BUBBLE_Y, v).apply()

    /** Remembered horizontal position of the bubble (px); -1 = default. */
    var bubbleX: Int
        get() = sp.getInt(K_BUBBLE_X, -1)
        set(v) = sp.edit().putInt(K_BUBBLE_X, v).apply()

    /** Auto-analyze on every incoming message; if false, user taps to analyze. */
    var autoAnalyze: Boolean
        get() = sp.getBoolean(K_AUTO, true)
        set(v) = sp.edit().putBoolean(K_AUTO, v).apply()

    fun isAllowed(title: String?): Boolean {
        val wl = whitelist
        if (wl.isEmpty()) return true
        if (title == null) return false
        return wl.any { title.contains(it) }
    }

    /** 判断后端所需的 key 是否就位。 */
    fun hasJevKey(): Boolean =
        if (jevProvider == JevEndpoints.PROVIDER_TYPESAFE) typesafeKey.isNotBlank() else opencodeKey.isNotBlank()

    /** 起草永远走 OpenCode Go → OpenCode key 必备。 */
    fun hasKeys(): Boolean = opencodeKey.isNotBlank() && hasJevKey()

    companion object {
        private const val K_OPENCODE_KEY = "opencode_key"
        private const val K_TYPESAFE_KEY = "typesafe_key"
        private const val K_JEV_PROVIDER = "jev_provider"
        private const val K_JEV_MODEL = "jev_model"
        private const val K_SESSION = "opencode_session"
        private const val K_REPLY_MODEL = "reply_model"
        private const val K_REL = "relationship"
        private const val K_ENABLED = "enabled"
        private const val K_WHITELIST = "whitelist"
        private const val K_OPACITY = "overlay_opacity"
        private const val K_BUBBLE_Y = "bubble_y"
        private const val K_BUBBLE_X = "bubble_x"
        private const val K_AUTO = "auto_analyze"

        // Reply drafting model on OpenCode Go（chat/completions 族；便宜、中文自然）.
        const val DEFAULT_REPLY_MODEL = JevEndpoints.DEFAULT_REPLY_MODEL
        const val DEFAULT_REL = "对方是我的伴侣；from=me 的是我发的，from=other 的是对方发的"
    }
}
