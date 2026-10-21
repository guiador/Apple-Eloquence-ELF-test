// SPDX-License-Identifier: MIT
package com.eloquence.tts

import android.content.Context

class VoiceSettings(context: Context) {
    private val preferences = context.getSharedPreferences("apple_voice_settings", Context.MODE_PRIVATE)
    fun language(): String = preferences.getString("language", "es-ES")
        ?.takeIf { it in VoiceCatalog.tags } ?: "es-ES"
    fun selectLanguage(tag: String) { preferences.edit().putString("language", tag).apply() }
    fun profile(tag: String) = preferences.getInt("profile:$tag", 0).coerceIn(0, 7)
    fun selectProfile(tag: String, profile: Int) { preferences.edit().putInt("profile:$tag", profile).apply() }
    fun parameters(tag: String, profile: Int): IntArray = IntArray(8) { parameter ->
        preferences.getInt("$tag:$profile:$parameter", VoiceCatalog.presets[profile].parameters[parameter])
            .coerceIn(0, VoiceCatalog.maximum(parameter))
    }
    fun set(tag: String, profile: Int, parameter: Int, value: Int) {
        preferences.edit().putInt("$tag:$profile:$parameter", value.coerceIn(0, VoiceCatalog.maximum(parameter))).apply()
    }
    fun reset(tag: String, profile: Int) {
        preferences.edit().apply { for (parameter in 0..7) remove("$tag:$profile:$parameter") }.apply()
    }
}
