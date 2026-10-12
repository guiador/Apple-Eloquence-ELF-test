// SPDX-License-Identifier: MIT
// Adapted from Free Eloquence TTS GetSampleTextActivity; see TEST-BUILD-NOTICES.md.
package com.eloquence.tts

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech

class GetSampleTextActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val requested = intent.getStringExtra("language")
        val sample = if (requested == "spa" || requested == "es")
            "Esta es una prueba de Apple Eloquence en Android." else "This is a test of Apple Eloquence on Android."
        setResult(TextToSpeech.LANG_AVAILABLE, Intent().putExtra(TextToSpeech.Engine.EXTRA_SAMPLE_TEXT, sample))
        finish()
    }
}
