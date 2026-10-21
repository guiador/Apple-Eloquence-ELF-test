// SPDX-License-Identifier: MIT
package com.eloquence.tts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.io.File

/** Controlled resource-sample synthesis for adb tests, protected by DUMP. */
class DeviceProbeReceiver : BroadcastReceiver() {
    companion object { private var reader: TextToSpeech? = null }
    override fun onReceive(context: Context, intent: Intent) {
        val tag = intent.getStringExtra("language") ?: "es-ES"
        val profile = intent.getIntExtra("profile", 0)
        if (tag !in VoiceCatalog.tags || profile !in VoiceCatalog.presets.indices) return
        val pending = goAsync()
        val main = Handler(Looper.getMainLooper())
        var finished = false
        fun finish(result: String) {
            main.post {
                if (!finished) {
                    finished = true
                    Log.i("EloquenceProbe", result)
                    reader?.shutdown()
                    reader = null
                    pending.finish()
                }
            }
        }
        reader?.shutdown()
        val output = File(context.cacheDir, "probe-$tag-$profile.wav")
        reader = TextToSpeech(context, { result ->
            if (result != TextToSpeech.SUCCESS) { finish("FAIL init"); return@TextToSpeech }
            val engine = reader ?: return@TextToSpeech
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) { Log.i("EloquenceProbe", "START $tag profile=$profile") }
                override fun onDone(id: String?) { finish("PASS $tag profile=$profile bytes=${output.length()}") }
                @Deprecated("Android legacy callback")
                override fun onError(id: String?) { finish("FAIL synthesis $tag profile=$profile") }
            })
            val voice = engine.voices?.firstOrNull { it.name == VoiceCatalog.id(tag, profile) }
            Log.i("EloquenceProbe", "voices=${engine.voices?.size}")
            if (voice == null || engine.setVoice(voice) < 0) { finish("FAIL voice"); return@TextToSpeech }
            engine.setSpeechRate(1f)
            engine.setPitch(1f)
            if (engine.synthesizeToFile(LocalizedSamples.forTag(context, tag), Bundle(), output, "probe") < 0)
                finish("FAIL request")
        }, context.packageName)
        main.postDelayed({ finish("FAIL timeout") }, 25000)
    }
}
