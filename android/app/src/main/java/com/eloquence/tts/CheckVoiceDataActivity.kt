// SPDX-License-Identifier: MIT
// Adapted from Free Eloquence TTS CheckVoiceDataActivity; see TEST-BUILD-NOTICES.md.
package com.eloquence.tts

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import java.io.File

class CheckVoiceDataActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val folder = File(applicationInfo.nativeLibraryDir)
        val modules = listOf("eng-USA" to "enu", "eng-GBR" to "eng", "spa-ESP" to "esp",
            "spa-MEX" to "esm", "fra-FRA" to "fra", "fra-CAN" to "frc", "deu-DEU" to "deu",
            "ita-ITA" to "ita", "por-BRA" to "ptb", "fin-FIN" to "fin")
        val enginePresent = File(folder, "libeci.so").isFile
        val available = ArrayList(modules.filter { enginePresent && File(folder, "lib${it.second}.so").isFile }.map { it.first })
        val unavailable = ArrayList(modules.map { it.first }.filterNot { it in available })
        val result = Intent().apply {
            putStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES, available)
            putStringArrayListExtra(TextToSpeech.Engine.EXTRA_UNAVAILABLE_VOICES, unavailable)
        }
        setResult(if (available.isEmpty()) TextToSpeech.Engine.CHECK_VOICE_DATA_FAIL else TextToSpeech.Engine.CHECK_VOICE_DATA_PASS, result)
        finish()
    }
}
