// SPDX-License-Identifier: MIT
package com.eloquence.tts

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.widget.*
import java.util.Locale

/** Accessible native controls for testing and saving per-language voice settings. */
class TestActivity : Activity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private lateinit var settings: VoiceSettings
    private lateinit var status: TextView
    private lateinit var text: EditText
    private lateinit var language: Spinner
    private lateinit var profile: Spinner
    private lateinit var gender: Spinner
    private lateinit var speak: Button
    private val bars = mutableMapOf<Int, SeekBar>()
    private val values = mutableMapOf<Int, TextView>()
    private var languageTag = "es-ES"
    private var selectedProfile = 0
    private var loading = false
    private var lastSample = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.activity_title)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        settings = VoiceSettings(this)
        languageTag = settings.language()
        selectedProfile = settings.profile(languageTag)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        fun label(caption: String, target: View) {
            target.id = View.generateViewId()
            column.addView(TextView(this).apply { setText(caption); labelFor = target.id })
            column.addView(target, LinearLayout.LayoutParams(-1, -2))
        }
        column.addView(TextView(this).apply {
            setText(R.string.introduction)
        })
        language = Spinner(this).apply {
            adapter = ArrayAdapter(this@TestActivity, android.R.layout.simple_spinner_dropdown_item,
                VoiceCatalog.tags.map { Locale.forLanguageTag(it).getDisplayName(resources.configuration.locales[0]) })
        }
        label(getString(R.string.language), language)
        profile = Spinner(this)
        label(getString(R.string.voice_profile), profile)
        text = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
            lastSample = LocalizedSamples.forTag(this@TestActivity, languageTag)
            setText(lastSample)
        }
        label(getString(R.string.test_text), text)
        speak = Button(this).apply {
            setText(R.string.read_text)
            isEnabled = false
            setOnClickListener { readText() }
        }
        column.addView(speak)
        column.addView(Button(this).apply {
            setText(R.string.stop_reading)
            setOnClickListener { tts?.stop(); status.setText(R.string.reading_stopped) }
        })
        status = TextView(this).apply {
            setText(R.string.initializing)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            isFocusable = true
        }
        column.addView(status)
        column.addView(Button(this).apply {
            setText(R.string.reset_profile)
            setOnClickListener { settings.reset(languageTag, selectedProfile); loadControls(); status.setText(R.string.profile_reset) }
        })
        column.addView(Button(this).apply {
            setText(R.string.open_system_settings)
            setOnClickListener { openSpeechSettings() }
        })
        gender = Spinner(this).apply {
            adapter = ArrayAdapter(this@TestActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf(getString(R.string.male), getString(R.string.female)))
        }
        label(getString(R.string.voice_gender), gender)
        val captions = linkedMapOf(6 to R.string.speed, 2 to R.string.pitch, 7 to R.string.voice_volume,
            1 to R.string.head_size, 3 to R.string.pitch_variation, 4 to R.string.roughness, 5 to R.string.breathiness)
        for ((parameter, captionResource) in captions) {
            val caption = getString(captionResource)
            val value = TextView(this)
            values[parameter] = value
            column.addView(value)
            val bar = SeekBar(this).apply {
                id = View.generateViewId()
                max = VoiceCatalog.maximum(parameter)
                contentDescription = caption
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        value.text = getString(R.string.parameter_value, caption, progress, max)
                        if (!loading) settings.set(languageTag, selectedProfile, parameter, progress)
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
            }
            value.labelFor = bar.id
            bars[parameter] = bar
            column.addView(bar, LinearLayout.LayoutParams(-1, -2))
            column.addView(LinearLayout(this).apply {
                for (delta in listOf(-1, 1)) {
                    addView(Button(this@TestActivity).apply {
                        setText(if (delta < 0) R.string.decrease_symbol else R.string.increase_symbol)
                        contentDescription = getString(if (delta < 0) R.string.decrease_parameter else R.string.increase_parameter, caption)
                        setOnClickListener {
                            bar.progress = (bar.progress + delta).coerceIn(0, bar.max)
                            bar.announceForAccessibility(getString(R.string.parameter_announcement, caption, bar.progress))
                        }
                    }, LinearLayout.LayoutParams(0, -2, 1f))
                }
            })
        }
        column.addView(TextView(this).apply {
            setText(R.string.parameter_limits)
        })
        setContentView(ScrollView(this).apply { addView(column) })
        loading = true
        language.setSelection(VoiceCatalog.tags.indexOf(languageTag))
        updateProfileNames()
        loading = false
        loadControls()
        language.onItemSelectedListener = selection { position ->
            val chosen = VoiceCatalog.tags[position]
            if (!loading && chosen != languageTag) {
                tts?.stop()
                languageTag = chosen
                val sample = LocalizedSamples.forTag(this@TestActivity, languageTag)
                if (text.text.toString() == lastSample) text.setText(sample)
                lastSample = sample
                selectedProfile = settings.profile(languageTag)
                settings.selectLanguage(languageTag)
                loading = true
                updateProfileNames()
                loading = false
                loadControls()
            }
        }
        profile.onItemSelectedListener = selection { position ->
            if (!loading && position != selectedProfile) {
                tts?.stop()
                selectedProfile = position
                settings.selectProfile(languageTag, position)
                loadControls()
            }
        }
        gender.onItemSelectedListener = selection { position ->
            if (!loading && position != settings.parameters(languageTag, selectedProfile)[0])
                settings.set(languageTag, selectedProfile, 0, position)
        }
        tts = TextToSpeech(this, this, packageName)
    }

    private fun selection(action: (Int) -> Unit) = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { action(position) }
        override fun onNothingSelected(parent: AdapterView<*>?) {}
    }

    private fun updateProfileNames() {
        profile.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            VoiceCatalog.presets.map { it.displayName(languageTag) })
        profile.setSelection(selectedProfile)
    }

    private fun loadControls() {
        loading = true
        val parameters = settings.parameters(languageTag, selectedProfile)
        gender.setSelection(parameters[0])
        for ((parameter, bar) in bars) {
            bar.progress = parameters[parameter]
            // A same-value setProgress doesn't dispatch a callback on Android.
            val caption = bar.contentDescription.toString()
            values[parameter]?.text = getString(R.string.parameter_value, caption, parameters[parameter], bar.max)
        }
        loading = false
    }

    override fun onInit(result: Int) {
        if (result != TextToSpeech.SUCCESS) { status.setText(R.string.engine_failed); return }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) { runOnUiThread { status.setText(R.string.reading) } }
            override fun onDone(id: String?) { runOnUiThread { status.setText(R.string.reading_finished) } }
            @Deprecated("Android legacy callback")
            override fun onError(id: String?) { runOnUiThread { status.setText(R.string.synthesis_failed) } }
        })
        speak.isEnabled = true
        status.setText(R.string.engine_ready)
    }

    private fun readText() {
        val engine = tts ?: return
        val message = text.text.toString()
        if (message.isBlank()) { text.error = getString(R.string.enter_text); text.requestFocus(); return }
        val voice = engine.voices?.firstOrNull { it.name == VoiceCatalog.id(languageTag, selectedProfile) }
        if (voice == null || engine.setVoice(voice) < 0) { status.setText(R.string.profile_unavailable); return }
        engine.setSpeechRate(1f)
        engine.setPitch(1f)
        if (engine.speak(message, TextToSpeech.QUEUE_FLUSH, Bundle(), "prueba") == TextToSpeech.ERROR)
            status.setText(R.string.reading_failed)
    }

    private fun openSpeechSettings() {
        for (action in listOf("com.android.settings.TTS_SETTINGS", Settings.ACTION_ACCESSIBILITY_SETTINGS, Settings.ACTION_SETTINGS)) {
            try { startActivity(Intent(action)); return } catch (_: ActivityNotFoundException) { }
        }
        status.setText(R.string.settings_failed)
    }
    override fun onStop() { tts?.stop(); super.onStop() }
    override fun onDestroy() { tts?.shutdown(); tts = null; super.onDestroy() }
}
