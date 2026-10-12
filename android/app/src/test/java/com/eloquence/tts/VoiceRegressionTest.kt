// SPDX-License-Identifier: GPL-2.0-or-later
package com.eloquence.tts

import org.junit.Assert.*
import org.junit.Test

class VoiceRegressionTest {
    @Test fun spanishUsesLegacyBytesAndKeepsAccents() {
        assertArrayEquals(byteArrayOf(0xe1.toByte(), 0xe9.toByte(), 0xed.toByte(), 0xf3.toByte(),
            0xfa.toByte(), 0xf1.toByte(), 0xd1.toByte(), 0xfc.toByte()), TextCodec.encode("áéíóúñÑü"))
    }
    @Test fun pastedCombiningAccentsMatchPrecomposedText() {
        assertArrayEquals(TextCodec.encode("El niño pidió café"), TextCodec.encode("El nin\u0303o pidio\u0301 cafe\u0301"))
    }
    @Test fun embeddedNullDoesNotTruncateTextAndUnsupportedCharactersAreReplaced() {
        assertArrayEquals("uno dos".toByteArray(), TextCodec.encode("uno\u0000dos"))
        assertArrayEquals("?".toByteArray(), TextCodec.encode("\uD83D\uDE00"))
    }
    @Test fun allEightyNamesRoundTripAndUnknownVoiceIsRejected() {
        val ids = VoiceCatalog.tags.flatMap { tag -> VoiceCatalog.presets.indices.map { VoiceCatalog.id(tag, it) } }
        assertEquals(80, ids.toSet().size)
        for (tag in VoiceCatalog.tags) for (profile in VoiceCatalog.presets.indices)
            assertEquals(tag to profile, VoiceCatalog.parse(VoiceCatalog.id(tag, profile)))
        assertNull(VoiceCatalog.parse("apple:es-ES:unknown"))
        assertNull(VoiceCatalog.parse("apple:ja-JP:Reed"))
    }
    @Test fun normalClientProsodyPreservesEveryPresetAndDoesNotMutateSettings() {
        for (preset in VoiceCatalog.presets) {
            val defaults = preset.parameters.toIntArray()
            assertArrayEquals(defaults, VoiceCatalog.prosody(defaults, 100, 100))
            val faster = VoiceCatalog.prosody(defaults, 200, 200)
            assertEquals(100, faster[6])
            assertEquals(100, faster[2])
            assertArrayEquals(preset.parameters.toIntArray(), defaults)
        }
        assertEquals("Jacques", VoiceCatalog.presets[0].displayName("fr-FR"))
        assertEquals("Reed", VoiceCatalog.presets[0].displayName("es-ES"))
    }
    @Test fun oldLocaleOnlyVoiceNamesKeepTheirLanguageFallback() {
        assertEquals("es-ES", VoiceCatalog.legacyTag("es"))
        assertEquals("es-ES", VoiceCatalog.legacyTag("es-PE"))
        assertEquals("es-MX", VoiceCatalog.legacyTag("es-MX"))
        assertEquals("fr-CA", VoiceCatalog.legacyTag("fr-CA"))
        assertNull(VoiceCatalog.legacyTag("apple:es-ES:unknown"))
        assertNull(VoiceCatalog.legacyTag("ja-JP"))
    }
}
