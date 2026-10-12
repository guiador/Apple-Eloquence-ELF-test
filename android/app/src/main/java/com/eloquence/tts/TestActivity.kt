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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Apple Eloquence: voces y ajustes"
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
            setText("Elige una voz, ajusta sus parámetros y prueba la lectura. Los ajustes se guardan para cada idioma y perfil.")
        })
        language = Spinner(this).apply {
            adapter = ArrayAdapter(this@TestActivity, android.R.layout.simple_spinner_dropdown_item, VoiceCatalog.languageNames)
        }
        label("Idioma", language)
        profile = Spinner(this)
        label("Perfil de voz", profile)
        text = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
            setText("El niño pidió café. Mañana hablará español. Pingüino, canción, árbol: á, é, í, ó, ú, ñ y ü.")
        }
        label("Texto de prueba", text)
        speak = Button(this).apply {
            setText("Leer texto")
            isEnabled = false
            setOnClickListener { readText() }
        }
        column.addView(speak)
        column.addView(Button(this).apply {
            setText("Detener lectura")
            setOnClickListener { tts?.stop(); status.text = "Lectura detenida" }
        })
        status = TextView(this).apply {
            text = "Inicializando el motor…"
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            isFocusable = true
        }
        column.addView(status)
        column.addView(Button(this).apply {
            setText("Restablecer valores del perfil")
            setOnClickListener { settings.reset(languageTag, selectedProfile); loadControls(); status.text = "Valores originales del perfil restaurados" }
        })
        column.addView(Button(this).apply {
            setText("Abrir ajustes de texto a voz del sistema")
            setOnClickListener { openSpeechSettings() }
        })
        gender = Spinner(this).apply {
            adapter = ArrayAdapter(this@TestActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Masculino", "Femenino"))
        }
        label("Género de la voz", gender)
        val captions = linkedMapOf(6 to "Velocidad", 2 to "Tono", 7 to "Volumen de la voz",
            1 to "Tamaño de cabeza (timbre)", 3 to "Variación tonal", 4 to "Aspereza", 5 to "Aire en la voz")
        for ((parameter, caption) in captions) {
            val value = TextView(this)
            values[parameter] = value
            column.addView(value)
            val bar = SeekBar(this).apply {
                id = View.generateViewId()
                max = VoiceCatalog.maximum(parameter)
                contentDescription = caption
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        value.text = "$caption: $progress de $max"
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
                for ((delta, action) in listOf(-1 to "Disminuir", 1 to "Aumentar")) {
                    addView(Button(this@TestActivity).apply {
                        setText(if (delta < 0) "−" else "+")
                        contentDescription = "$action $caption"
                        setOnClickListener {
                            bar.progress = (bar.progress + delta).coerceIn(0, bar.max)
                            bar.announceForAccessibility("$caption: ${bar.progress}")
                        }
                    }, LinearLayout.LayoutParams(0, -2, 1f))
                }
            })
        }
        column.addView(TextView(this).apply {
            setText("Velocidad: 0 a 250. Otros parámetros: 0 a 100. Los valores extremos pueden sonar poco naturales. Las funciones avanzadas y los textos largos siguen en pruebas.")
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
            values[parameter]?.text = "$caption: ${parameters[parameter]} de ${bar.max}"
        }
        loading = false
    }

    override fun onInit(result: Int) {
        if (result != TextToSpeech.SUCCESS) { status.text = "No se pudo iniciar el motor"; return }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) { runOnUiThread { status.text = "Leyendo" } }
            override fun onDone(id: String?) { runOnUiThread { status.text = "Lectura terminada" } }
            @Deprecated("Android legacy callback")
            override fun onError(id: String?) { runOnUiThread { status.text = "El motor no pudo sintetizar el texto o rechazó un parámetro" } }
        })
        speak.isEnabled = true
        status.text = "Motor conectado. Elige una voz y pulsa Leer texto."
    }

    private fun readText() {
        val engine = tts ?: return
        val message = text.text.toString()
        if (message.isBlank()) { text.error = "Escribe un texto"; text.requestFocus(); return }
        val voice = engine.voices?.firstOrNull { it.name == VoiceCatalog.id(languageTag, selectedProfile) }
        if (voice == null || engine.setVoice(voice) < 0) { status.text = "Perfil no disponible"; return }
        engine.setSpeechRate(1f)
        engine.setPitch(1f)
        if (engine.speak(message, TextToSpeech.QUEUE_FLUSH, Bundle(), "prueba") == TextToSpeech.ERROR)
            status.text = "No se pudo iniciar la lectura"
    }

    private fun openSpeechSettings() {
        for (action in listOf("com.android.settings.TTS_SETTINGS", Settings.ACTION_ACCESSIBILITY_SETTINGS, Settings.ACTION_SETTINGS)) {
            try { startActivity(Intent(action)); return } catch (_: ActivityNotFoundException) { }
        }
        status.text = "No se pudieron abrir los ajustes del sistema"
    }
    override fun onStop() { tts?.stop(); super.onStop() }
    override fun onDestroy() { tts?.shutdown(); tts = null; super.onDestroy() }
}
