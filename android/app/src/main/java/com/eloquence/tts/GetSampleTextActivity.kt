// SPDX-License-Identifier: MIT
// Adapted from Free Eloquence TTS GetSampleTextActivity; see TEST-BUILD-NOTICES.md.
package com.eloquence.tts

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import java.util.Locale

class GetSampleTextActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val requested = intent.getStringExtra("language")
        val country = intent.getStringExtra("country").orEmpty()
        val candidates = VoiceCatalog.tags.filter {
            val locale = Locale.forLanguageTag(it)
            requested == locale.language || requested == locale.isO3Language
        }
        val tag = candidates.firstOrNull {
            val locale = Locale.forLanguageTag(it)
            country.equals(locale.country, true) || country.equals(locale.isO3Country, true)
        } ?: candidates.firstOrNull() ?: "en-US"
        val sample = LocalizedSamples.forTag(this, tag)
        setResult(if (requested != null && candidates.isEmpty()) TextToSpeech.LANG_NOT_SUPPORTED
            else TextToSpeech.LANG_AVAILABLE, Intent().putExtra(TextToSpeech.Engine.EXTRA_SAMPLE_TEXT, sample))
        finish()
    }
}
