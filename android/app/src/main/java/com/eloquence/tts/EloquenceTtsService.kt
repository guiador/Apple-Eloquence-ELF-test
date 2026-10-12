/*
 * SPDX-License-Identifier: MIT
 *
 * EloquenceTtsService -- an Android TextToSpeechService backed by the converted
 * Apple Eloquence engine. Synthesis is delegated to libeloquence_jni.so, which
 * dlopens eci.so + the language modules (shipped in the app's native lib dir)
 * and returns 11025 Hz mono S16 PCM that we hand to the platform callback.
 *
 * Engine data layout at runtime:
 *   - eci.so + lib<lang>.so live in applicationInfo.nativeLibraryDir (installed
 *     read-only from app/src/main/jniLibs/arm64-v8a/, where execution is
 *     allowed -- you cannot dlopen executable code from writable app storage on
 *     modern Android).
 *   - eci.ini is generated into filesDir at first run with absolute Path=
 *     entries pointing into nativeLibraryDir; the engine reads it from cwd, so
 *     the JNI layer chdir()s to filesDir before eciNew.
 */
package com.eloquence.tts

import android.media.AudioFormat
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import android.util.Log
import android.system.Os
import android.system.ErrnoException
import java.io.File
import java.util.Locale
import android.speech.tts.Voice as AndroidVoice

private const val TAG = "EloquenceTts"
private const val SAMPLE_RATE = 11025

/** One shipped voice: ISO3 language/country -> ECI dialect + module file(s). */
private data class Voice(
    val iso3Lang: String,
    val iso3Country: String,
    val dialect: Int,
    val module: String,            // lib<module>.so in nativeLibraryDir
    val romanizer: String? = null, // CJK only
)

// Mirrors release.yml's eci.ini table. Modules are installed as lib<name>.so.
private val VOICES = listOf(
    Voice("eng", "USA", 0x00010000, "enu"),
    Voice("eng", "GBR", 0x00010001, "eng"),
    Voice("spa", "ESP", 0x00020000, "esp"),
    Voice("spa", "MEX", 0x00020001, "esm"),
    Voice("fra", "FRA", 0x00030000, "fra"),
    Voice("fra", "CAN", 0x00030001, "frc"),
    Voice("deu", "DEU", 0x00040000, "deu"),
    Voice("ita", "ITA", 0x00050000, "ita"),
    Voice("por", "BRA", 0x00070000, "ptb"),
    Voice("fin", "FIN", 0x00090000, "fin"),
    Voice("zho", "CHN", 0x00060000, "chs", "chsrom"),
    Voice("zho", "TWN", 0x00060001, "cht", "chtrom"),
    Voice("jpn", "JPN", 0x00080000, "jpn", "jpnrom"),
    Voice("kor", "KOR", 0x000A0000, "kor", "korrom"),
)

class EloquenceTtsService : TextToSpeechService() {

    private var handle: Long = 0
    private var currentDialect: Int = 0
    private val lock = Any()
    private lateinit var settings: VoiceSettings

    override fun onCreate() {
        // Engine + eci.ini must exist before the base class wires up languages.
        writeEciIni()
        settings = VoiceSettings(this)
        ensureEngine(VOICES.first().dialect)
        super.onCreate()
    }

    override fun onDestroy() {
        synchronized(lock) {
            if (handle != 0L) { EloquenceNative.nativeShutdown(handle); handle = 0 }
        }
        super.onDestroy()
    }

    // --- language plumbing -------------------------------------------------

    private fun match(lang: String?, country: String?): Voice? {
        if (lang == null) return null
        VOICES.firstOrNull { it.romanizer == null && it.iso3Lang == lang && it.iso3Country == country }?.let { return it }
        return VOICES.firstOrNull { it.romanizer == null && it.iso3Lang == lang } // Western modules only: cp1252
    }

    override fun onIsLanguageAvailable(lang: String?, country: String?, variant: String?): Int {
        val v = match(lang, country) ?: return TextToSpeech.LANG_NOT_SUPPORTED
        return when {
            v.iso3Lang == lang && v.iso3Country == country -> TextToSpeech.LANG_COUNTRY_AVAILABLE
            else -> TextToSpeech.LANG_AVAILABLE
        }
    }

    override fun onGetLanguage(): Array<String> {
        val v = VOICES.firstOrNull { it.dialect == currentDialect } ?: VOICES.first()
        return arrayOf(v.iso3Lang, v.iso3Country, "")
    }

    override fun onLoadLanguage(lang: String?, country: String?, variant: String?): Int {
        val v = match(lang, country) ?: return TextToSpeech.LANG_NOT_SUPPORTED
        synchronized(lock) { if (!ensureEngine(v.dialect)) return TextToSpeech.LANG_NOT_SUPPORTED }
        return onIsLanguageAvailable(lang, country, variant)
    }

    private fun tagFor(v: Voice): String = VoiceCatalog.tags.first {
        val locale = Locale.forLanguageTag(it)
        locale.isO3Language == v.iso3Lang && locale.isO3Country == v.iso3Country
    }

    private fun namedVoice(name: String?): Pair<String, Int>? {
        VoiceCatalog.parse(name)?.let { return it }
        // Accept the old locale-only names for clients retaining the previous APK's voices.
        if (name in VoiceCatalog.tags) return name!! to settings.profile(name)
        return null
    }

    override fun onGetVoices(): MutableList<AndroidVoice> = VoiceCatalog.tags.flatMap { tag ->
        VoiceCatalog.presets.indices.map { profile ->
            AndroidVoice(VoiceCatalog.id(tag, profile), Locale.forLanguageTag(tag),
                AndroidVoice.QUALITY_NORMAL, AndroidVoice.LATENCY_NORMAL, false, emptySet())
        }
    }.toMutableList()

    override fun onIsValidVoiceName(voiceName: String?): Int =
        if (namedVoice(voiceName) != null) TextToSpeech.SUCCESS else TextToSpeech.ERROR

    override fun onGetDefaultVoiceNameFor(lang: String?, country: String?, variant: String?): String? {
        val tag = match(lang, country)?.let { tagFor(it) } ?: return null
        return VoiceCatalog.id(tag, settings.profile(tag))
    }

    override fun onLoadVoice(voiceName: String?): Int {
        val (tag, _) = namedVoice(voiceName) ?: return TextToSpeech.ERROR
        val locale = Locale.forLanguageTag(tag)
        val v = match(locale.isO3Language, locale.isO3Country) ?: return TextToSpeech.ERROR
        return synchronized(lock) { if (ensureEngine(v.dialect)) TextToSpeech.SUCCESS else TextToSpeech.ERROR }
    }

    // --- synthesis ---------------------------------------------------------

    override fun onSynthesizeText(request: SynthesisRequest, callback: SynthesisCallback) {
        val selected = namedVoice(request.voiceName)
        if (!request.voiceName.isNullOrEmpty() && selected == null) { callback.error(); return }
        val requestedLocale = selected?.first?.let { Locale.forLanguageTag(it) }
        val v = match(requestedLocale?.isO3Language ?: request.language,
            requestedLocale?.isO3Country ?: request.country)
        if (v == null) { callback.error(); return }

        val text = request.charSequenceText?.toString().orEmpty()
        synchronized(lock) {
            if (!ensureEngine(v.dialect)) { callback.error(); return }
            val tag = tagFor(v)
            val profile = selected?.second ?: settings.profile(tag)
            // A client's 100% rate/pitch preserves the chosen profile/custom defaults.
            val parameters = VoiceCatalog.prosody(settings.parameters(tag, profile),
                request.speechRate, request.pitch)
            if (!EloquenceNative.nativeSetVoice(handle, parameters)) {
                Log.e(TAG, "Engine rejected voice parameters")
                callback.error(); return
            }

            val pcm = EloquenceNative.nativeSynthesize(handle, TextCodec.encode(text))
            if (pcm == null) { callback.error(); return }
            deliver(pcm, callback)
        }
    }

    override fun onStop() {
        synchronized(lock) { if (handle != 0L) EloquenceNative.nativeStop(handle) }
    }

    private fun deliver(pcm: ShortArray, callback: SynthesisCallback) {
        if (callback.start(SAMPLE_RATE, AudioFormat.ENCODING_PCM_16BIT, 1) != TextToSpeech.SUCCESS) return
        val maxBytes = callback.maxBufferSize.coerceAtLeast(2).let { it - it % 2 }
        val bytes = ByteArray(pcm.size * 2)
        for (i in pcm.indices) {
            bytes[i * 2]     = (pcm[i].toInt() and 0xFF).toByte()
            bytes[i * 2 + 1] = ((pcm[i].toInt() shr 8) and 0xFF).toByte()
        }
        var off = 0
        while (off < bytes.size) {
            val n = minOf(maxBytes, bytes.size - off)
            if (callback.audioAvailable(bytes, off, n) != TextToSpeech.SUCCESS) { callback.error(); return }
            off += n
        }
        callback.done()
    }

    // --- engine lifecycle --------------------------------------------------

    private fun ensureEngine(dialect: Int): Boolean {
        if (handle != 0L && dialect == currentDialect) return true
        if (handle != 0L) { EloquenceNative.nativeShutdown(handle); handle = 0 }
        handle = EloquenceNative.nativeInit(filesDir.absolutePath, dialect)
        if (handle == 0L) { Log.e(TAG, "nativeInit failed for dialect 0x%08x".format(dialect)); return false }
        currentDialect = dialect
        return true
    }

    private fun writeEciIni() {
        val libDir = applicationInfo.nativeLibraryDir
        // The Apple language modules have fixed-size path buffers. Mirror the
        // upstream 1.3.0 relative-path fix instead of passing Android's long,
        // randomized /data/app/... installation prefix to those modules.
        val engineLink = File(filesDir, "engine")
        try {
            val existingTarget = try { Os.readlink(engineLink.absolutePath) } catch (_: ErrnoException) { null }
            if (existingTarget != null && existingTarget != libDir) {
                if (!engineLink.delete()) error("Cannot update engine directory link")
            }
            if (!engineLink.exists()) Os.symlink(libDir, engineLink.absolutePath)
        } catch (failure: ErrnoException) {
            throw IllegalStateException("Cannot link installed engine libraries", failure)
        }
        val sb = StringBuilder()
        sb.append("# Generated by EloquenceTtsService. Path= entries point at the\n")
        sb.append("# app's read-only native lib dir (execute-allowed).\n\n")
        for (v in VOICES) {
            val hi = (v.dialect ushr 16) and 0xFFFF
            val lo = v.dialect and 0xFFFF
            sb.append("[$hi.$lo]\n")
            sb.append("Path=./engine/lib${v.module}.so\n")
            v.romanizer?.let { sb.append("Path_Rom=./engine/lib$it.so\n") }
            sb.append("Version=6.1\n\n")
        }
        File(filesDir, "eci.ini").writeText(sb.toString())
    }
}
