// SPDX-License-Identifier: GPL-2.0-or-later
// Preset data adapted from Mudb0y / Stas Przecinek's sd_eloquence/src/eci/voices.c.
package com.eloquence.tts

import java.nio.charset.Charset
import java.text.Normalizer
import java.util.Locale

data class VoicePreset(val name: String, val parameters: List<Int>) {
    fun displayName(tag: String) = if (name == "Reed" && tag.startsWith("fr")) "Jacques" else name
}

object VoiceCatalog {
    // ECI ordering: gender, head size, pitch, pitch variation, roughness,
    // breathiness, speed, volume. Apply all fields to active slot 0.
    val presets = listOf(
        VoicePreset("Reed", listOf(0, 50, 65, 30, 0, 0, 50, 92)),
        VoicePreset("Shelley", listOf(1, 50, 81, 30, 0, 50, 50, 95)),
        VoicePreset("Sandy", listOf(1, 22, 93, 30, 0, 50, 50, 95)),
        VoicePreset("Rocko", listOf(0, 86, 56, 47, 0, 0, 50, 93)),
        VoicePreset("Flo", listOf(1, 56, 89, 35, 0, 40, 50, 95)),
        VoicePreset("Grandma", listOf(1, 45, 68, 30, 3, 40, 50, 90)),
        VoicePreset("Grandpa", listOf(0, 30, 61, 44, 18, 20, 50, 90)),
        VoicePreset("Eddy", listOf(0, 50, 69, 34, 0, 0, 50, 92)),
    )
    val tags = listOf("es-ES", "es-MX", "en-US", "en-GB", "fr-FR", "fr-CA", "de-DE", "it-IT", "pt-BR", "fi-FI")
    fun id(tag: String, profile: Int) = "apple:$tag:${presets[profile].name}"
    fun parse(voiceId: String?): Pair<String, Int>? {
        if (voiceId == null) return null
        for (tag in tags) for (profile in presets.indices)
            if (voiceId == id(tag, profile)) return tag to profile
        return null
    }
    fun maximum(parameter: Int) = when (parameter) { 0 -> 1; 6 -> 250; else -> 100 }
    fun legacyTag(name: String?): String? {
        if (name.isNullOrEmpty() || name.startsWith("apple:")) return null
        val locale = Locale.forLanguageTag(name)
        tags.firstOrNull { it.equals(locale.toLanguageTag(), ignoreCase = true) }?.let { return it }
        // The old Android default enumerator could create es, es-PE, etc.
        // Retain its language fallback without advertising invented new profiles.
        return tags.firstOrNull { Locale.forLanguageTag(it).language == locale.language }
    }
    fun prosody(parameters: IntArray, ratePercent: Int, pitchPercent: Int): IntArray =
        parameters.copyOf().apply {
            this[6] = (this[6].toLong() * ratePercent / 100).coerceIn(0, 250).toInt()
            this[2] = (this[2].toLong() * pitchPercent / 100).coerceIn(0, 100).toInt()
        }
}

object TextCodec {
    private val western = Charset.forName("windows-1252")
    // Western modules take legacy bytes, not JNI's modified UTF-8. NFC merges
    // decomposed accents; unrepresentable code points become charset '?' bytes.
    fun encode(text: String): ByteArray = Normalizer.normalize(text, Normalizer.Form.NFC)
        .replace('\u0000', ' ').replace('`', ' ').toByteArray(western)
}
