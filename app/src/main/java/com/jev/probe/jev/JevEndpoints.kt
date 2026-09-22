package com.jev.probe.jev

/**
 * 后端路由与请求头。纯 Kotlin（无 Android / 网络依赖），便于单测。
 *
 * 实测事实（2026-09-22，细节见 AGENTS.md「架构与数据流」网络侧）：
 *  1) Jev 判断 · Zen      : POST https://opencode.ai/zen/v1/systemone          model=jev-1.13-free（免费）
 *  2) Jev 判断 · 官方      : POST https://api.typesafe.ai/v1/systemone          model=jev-latest
 *  3) 候选起草 · OpenCode Go: POST https://opencode.ai/zen/go/v1/chat/completions，**必须**带 x-opencode-session
 *  4) opencode.ai 两个域都要自述 User-Agent（库默认 UA 会被 Cloudflare 1010 拦：403）
 */
object JevEndpoints {
    const val PROVIDER_ZEN = "zen"
    const val PROVIDER_TYPESAFE = "typesafe"

    const val ZEN_SYSTEMONE = "https://opencode.ai/zen/v1/systemone"
    const val TYPESAFE_SYSTEMONE = "https://api.typesafe.ai/v1/systemone"
    const val GO_CHAT = "https://opencode.ai/zen/go/v1/chat/completions"

    const val UA = "jev-assistant-android/1.3"
    const val HEADER_SESSION = "x-opencode-session"

    const val DEFAULT_JEV_MODEL_ZEN = "jev-1.13-free"
    const val DEFAULT_JEV_MODEL_TYPESAFE = "jev-latest"
    const val DEFAULT_REPLY_MODEL = "glm-5.3-flash"

    /** 去哪、带什么额外头、默认模型是谁。 */
    data class Route(val url: String, val headers: Map<String, String>, val defaultModel: String)

    fun systemOne(provider: String): Route =
        if (provider == PROVIDER_TYPESAFE) Route(TYPESAFE_SYSTEMONE, emptyMap(), DEFAULT_JEV_MODEL_TYPESAFE)
        else Route(ZEN_SYSTEMONE, emptyMap(), DEFAULT_JEV_MODEL_ZEN)

    /** Go 端缺 session 头会 400 MissingSessionID（实测）；判断端点不需要。 */
    fun replyChat(sessionId: String, model: String): Route =
        Route(GO_CHAT, mapOf(HEADER_SESSION to sessionId), model.ifBlank { DEFAULT_REPLY_MODEL })

    fun jevModel(provider: String, configured: String): String =
        configured.ifBlank {
            if (provider == PROVIDER_TYPESAFE) DEFAULT_JEV_MODEL_TYPESAFE else DEFAULT_JEV_MODEL_ZEN
        }

    fun keyFor(provider: String, opencodeKey: String, typesafeKey: String): String =
        if (provider == PROVIDER_TYPESAFE) typesafeKey else opencodeKey
}
