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
import android.widget.*
import java.util.Locale

/** Small native-control test surface; the upstream JNI engine stays unchanged. */
class TestActivity : Activity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private lateinit var status: TextView
    private lateinit var text: EditText
    private lateinit var language: Spinner
    private lateinit var rate: EditText
    private lateinit var pitch: EditText
    private lateinit var speak: Button
    private val tags = listOf("es-ES", "es-MX", "en-US", "en-GB", "fr-FR", "fr-CA", "de-DE", "it-IT", "pt-BR", "fi-FI")
    private val names = listOf("Español de España", "Español de México", "Inglés de Estados Unidos", "Inglés británico", "Francés de Francia", "Francés canadiense", "Alemán", "Italiano", "Portugués de Brasil", "Finés")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Apple Eloquence: pruebas"
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
            setText("Motor Apple Eloquence del padre 1.3.0. APK experimental para ARM de 64 bits, Android 7 o posterior. No es compatible con ARM de 32 bits.")
        })
        language = Spinner(this).apply {
            adapter = ArrayAdapter(this@TestActivity, android.R.layout.simple_spinner_dropdown_item, names)
        }
        label("Idioma para la prueba", language)
        text = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
            setText("Hola. Esta es una prueba de Apple Eloquence en Android. Uno, dos, tres.")
        }
        label("Texto que se leerá", text)
        rate = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText("1.0")
        }
        label("Velocidad: de 0.5 a 2.0; 1.0 es normal", rate)
        pitch = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText("1.0")
        }
        label("Tono: de 0.5 a 2.0; 1.0 es normal", pitch)
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
        column.addView(Button(this).apply {
            setText("Abrir ajustes de texto a voz del sistema")
            setOnClickListener { openSpeechSettings() }
        })
        status = TextView(this).apply {
            text = "Inicializando el motor…"
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            isFocusable = true
        }
        column.addView(status)
        setContentView(ScrollView(this).apply { addView(column) })
        tts = TextToSpeech(this, this, packageName)
    }

    override fun onInit(result: Int) {
        if (result != TextToSpeech.SUCCESS) {
            status.text = "No se pudo iniciar el motor. Revisa el informe y los registros de Android antes de elegirlo como voz del lector de pantalla."
            return
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) { runOnUiThread { status.text = "Leyendo" } }
            override fun onDone(id: String?) { runOnUiThread { status.text = "Lectura terminada" } }
            @Deprecated("Android legacy callback")
            override fun onError(id: String?) { runOnUiThread { status.text = "El motor no pudo sintetizar el texto" } }
        })
        speak.isEnabled = true
        status.text = "Motor conectado. Selecciona el idioma y pulsa Leer texto."
    }

    private fun readText() {
        val engine = tts ?: return
        val message = text.text.toString()
        if (message.isBlank()) { text.error = "Escribe un texto"; text.requestFocus(); return }
        fun value(field: EditText): Float? {
            val number = field.text.toString().replace(',', '.').toFloatOrNull()
            if (number == null || !number.isFinite() || number !in 0.5f..2.0f) {
                field.error = "Introduce un número entre 0.5 y 2.0"
                field.requestFocus()
                return null
            }
            return number
        }
        val selectedRate = value(rate) ?: return
        val selectedPitch = value(pitch) ?: return
        if (engine.setLanguage(Locale.forLanguageTag(tags[language.selectedItemPosition])) < 0) {
            status.text = "El motor no tiene disponible ese idioma"
            return
        }
        engine.setSpeechRate(selectedRate)
        engine.setPitch(selectedPitch)
        val result = engine.speak(message, TextToSpeech.QUEUE_FLUSH, Bundle(), "prueba")
        if (result == TextToSpeech.ERROR) status.text = "No se pudo iniciar la lectura"
    }

    // Same fallback pattern used by Free Eloquence's SystemSpeechSettings.
    private fun openSpeechSettings() {
        for (action in listOf("com.android.settings.TTS_SETTINGS", Settings.ACTION_ACCESSIBILITY_SETTINGS, Settings.ACTION_SETTINGS)) {
            try { startActivity(Intent(action)); return } catch (_: ActivityNotFoundException) { }
        }
        status.text = "No se pudieron abrir los ajustes del sistema"
    }

    override fun onStop() { tts?.stop(); super.onStop() }
    override fun onDestroy() { tts?.shutdown(); tts = null; super.onDestroy() }
}
