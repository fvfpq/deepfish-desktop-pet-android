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
    val behaviorIntensity: Float = 1f
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
    private const val KEY_AUTO_START = "auto_start"

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
            behaviorIntensity = getFloat(KEY_BEHAVIOR_INTENSITY, 1f)
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
            .apply()
    }

    fun autoStart(context: Context): Boolean = sp(context).getBoolean(KEY_AUTO_START, false)

    fun setAutoStart(context: Context, value: Boolean) {
        sp(context).edit().putBoolean(KEY_AUTO_START, value).apply()
    }
}
