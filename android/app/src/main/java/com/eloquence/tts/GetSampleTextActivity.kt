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
            "El niño pidió café. Mañana hablará español: á, é, í, ó, ú, ñ y ü." else "This is a test of Apple Eloquence on Android."
        setResult(TextToSpeech.LANG_AVAILABLE, Intent().putExtra(TextToSpeech.Engine.EXTRA_SAMPLE_TEXT, sample))
        finish()
    }
}
