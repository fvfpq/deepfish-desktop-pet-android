package com.deepfish.pet

import android.content.Context
import android.content.SharedPreferences

data class PetSettings(
    val provider: String = "pollinations",
    val endpoint: String = Prefs.DEFAULT_ENDPOINT,
    val model: String = Prefs.DEFAULT_MODEL,
    val alwaysOnTop: Boolean = true,
    val scale: Float = 0.9f,
    val sound: Boolean = true,
    val freeWalk: Boolean = true,
    val interactiveZones: Boolean = true,
    val spicyLines: Boolean = true,
    val behaviorIntensity: Float = 1f,
    val touchThrough: Boolean = false
)

object Prefs {

    const val DEFAULT_ENDPOINT = "https://text.pollinations.ai/openai"
    const val DEFAULT_MODEL = "openai-fast"

    private const val FILE = "deepfish_pet"
    private const val KEY_PROVIDER = "provider"
    private const val KEY_ENDPOINT = "endpoint"
    private const val KEY_MODEL = "model"
    private const val KEY_ALWAYS_ON_TOP = "always_on_top"
    private const val KEY_SCALE = "scale"
    private const val KEY_SOUND = "sound"
    private const val KEY_FREE_WALK = "free_walk"
    private const val KEY_INTERACTIVE_ZONES = "interactive_zones"
    private const val KEY_SPICY_LINES = "spicy_lines"
    private const val KEY_BEHAVIOR_INTENSITY = "behavior_intensity"
    private const val KEY_TOUCH_THROUGH = "touch_through"
    private const val KEY_AUTO_START = "auto_start"
    private const val KEY_GATEWAY_ENABLED = "gateway_enabled"
    private const val KEY_GATEWAY_HOST = "gateway_host"
    private const val KEY_GATEWAY_PORT = "gateway_port"
    private const val KEY_GATEWAY_TOKEN = "gateway_token"
    private const val KEY_GATEWAY_EMBEDDED = "gateway_embedded"
    const val DEFAULT_GATEWAY_HOST = "127.0.0.1"
    const val DEFAULT_GATEWAY_PORT = 18789

    private fun sp(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun settings(context: Context): PetSettings = with(sp(context)) {
        PetSettings(
            provider = getString(KEY_PROVIDER, "pollinations") ?: "pollinations",
            endpoint = getString(KEY_ENDPOINT, DEFAULT_ENDPOINT) ?: DEFAULT_ENDPOINT,
            model = getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL,
            alwaysOnTop = getBoolean(KEY_ALWAYS_ON_TOP, true),
            scale = getFloat(KEY_SCALE, 0.9f),
            sound = getBoolean(KEY_SOUND, true),
            freeWalk = getBoolean(KEY_FREE_WALK, true),
            interactiveZones = getBoolean(KEY_INTERACTIVE_ZONES, true),
            spicyLines = getBoolean(KEY_SPICY_LINES, true),
            behaviorIntensity = getFloat(KEY_BEHAVIOR_INTENSITY, 1f),
            touchThrough = getBoolean(KEY_TOUCH_THROUGH, false)
        )
    }

    fun saveSettings(context: Context, s: PetSettings) {
        sp(context).edit()
            .putString(KEY_PROVIDER, s.provider)
            .putString(KEY_ENDPOINT, s.endpoint)
            .putString(KEY_MODEL, s.model)
            .putBoolean(KEY_ALWAYS_ON_TOP, s.alwaysOnTop)
            .putFloat(KEY_SCALE, s.scale)
            .putBoolean(KEY_SOUND, s.sound)
            .putBoolean(KEY_FREE_WALK, s.freeWalk)
            .putBoolean(KEY_INTERACTIVE_ZONES, s.interactiveZones)
            .putBoolean(KEY_SPICY_LINES, s.spicyLines)
            .putFloat(KEY_BEHAVIOR_INTENSITY, s.behaviorIntensity)
            .putBoolean(KEY_TOUCH_THROUGH, s.touchThrough)
            .apply()
    }

    fun autoStart(context: Context): Boolean = sp(context).getBoolean(KEY_AUTO_START, false)

    fun setAutoStart(context: Context, value: Boolean) {
        sp(context).edit().putBoolean(KEY_AUTO_START, value).apply()
    }

    fun gatewayEnabled(context: Context): Boolean = sp(context).getBoolean(KEY_GATEWAY_ENABLED, false)

    fun setGatewayEnabled(context: Context, value: Boolean) {
        sp(context).edit().putBoolean(KEY_GATEWAY_ENABLED, value).apply()
    }

    fun gatewayHost(context: Context): String =
        sp(context).getString(KEY_GATEWAY_HOST, DEFAULT_GATEWAY_HOST) ?: DEFAULT_GATEWAY_HOST

    fun gatewayPort(context: Context): Int = sp(context).getInt(KEY_GATEWAY_PORT, DEFAULT_GATEWAY_PORT)

    fun gatewayToken(context: Context): String? = sp(context).getString(KEY_GATEWAY_TOKEN, null)

    /** 是否使用内置 Gateway（随 APK 自带 rootfs，无需 Termux）。默认开启。 */
    fun gatewayEmbedded(context: Context): Boolean = sp(context).getBoolean(KEY_GATEWAY_EMBEDDED, true)

    fun setGatewayEmbedded(context: Context, value: Boolean) {
        sp(context).edit().putBoolean(KEY_GATEWAY_EMBEDDED, value).apply()
    }

    fun saveGatewayConfig(context: Context, host: String, port: Int, token: String?) {
        sp(context).edit()
            .putString(KEY_GATEWAY_HOST, host.trim())
            .putInt(KEY_GATEWAY_PORT, port)
            .putString(KEY_GATEWAY_TOKEN, token?.trim()?.takeIf { it.isNotEmpty() })
            .apply()
    }
}
