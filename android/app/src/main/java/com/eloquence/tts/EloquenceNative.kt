/*
 * SPDX-License-Identifier: MIT
 *
 * EloquenceNative -- thin Kotlin view of libeloquence_jni.so. Each method maps
 * 1:1 to a Java_com_eloquence_tts_EloquenceNative_* entry point in
 * android/jni/eloquence_jni.c.
 */
package com.eloquence.tts

object EloquenceNative {
    init { System.loadLibrary("eloquence_jni") }

    /**
     * Initialise a renderer context. Each utterance uses a fresh native process.
     * @param dataDir directory holding eci.ini (cwd for the engine).
     * @param dialect eciLanguageDialect code (e.g. 0x00010000 = US English),
     * @param libraryDir installed directory holding the native renderer and engine.
     * @return opaque native handle, or 0 on failure.
     */
    external fun nativeInit(dataDir: String, libraryDir: String, dialect: Int): Long

    /** Apply all eight ECI voice parameters to the active slot. */
    external fun nativeSetVoice(handle: Long, parameters: IntArray): Boolean

    /** Select the dialect for the next native renderer process. */
    external fun nativeSetLanguage(handle: Long, dialect: Int): Boolean

    /** Synthesize one utterance -> 11025 Hz mono S16 PCM, or null on abort. */
    external fun nativeSynthesize(handle: Long, text: ByteArray): ShortArray?

    /** Abort the in-flight utterance (called from onStop). */
    external fun nativeStop(handle: Long)

    /** Tear down the engine instance. */
    external fun nativeShutdown(handle: Long)
}
