package io.peekandpoke.klang.strudel.lang.helpers

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.VoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.strudel.lang.resolveNote

class VoiceDataSpec : StringSpec({

    "resolveNote() with scale and index should resolve note and frequency" {
        val voice = VoiceData.empty.copy(scale = "C major")

        val result = voice.resolveNote(newIndex = 0)

        result.note shouldBe "C"
        result.freqHz.let {
            it.shouldNotBeNull()
            it shouldBeGreaterThan 100.0
        }
        result.soundIndex shouldBe null
        result.value shouldBe null
    }

    "resolveNote() with scale containing colon should clean it" {
        val voice = VoiceData.empty.copy(scale = "C3:major")

        val result = voice.resolveNote(newIndex = 2)

        result.note shouldBe "E3"
        result.freqHz.let {
            it.shouldNotBeNull()
            it shouldBe (164.8 plusOrMinus 0.1)
        }
    }

    "resolveNote() with soundIndex and scale should use soundIndex" {
        val voice = VoiceData.empty.copy(
            scale = "C major",
            soundIndex = 4
        )

        val result = voice.resolveNote()

        result.note shouldBe "G"
        result.soundIndex shouldBe null
    }

    "resolveNote() with value and scale should interpret value as index" {
        val voice = VoiceData.empty.copy(
            scale = "C4 major",
            value = 2.asVoiceValue()
        )

        val result = voice.resolveNote()

        result.note shouldBe "E4"
        result.value shouldBe null
    }

    "resolveNote() with newIndex but no scale should set soundIndex" {
        val voice = VoiceData.empty

        val result = voice.resolveNote(newIndex = 5)

        result.soundIndex shouldBe 5
        result.note shouldBe null
        result.freqHz shouldBe null
    }

    "resolveNote() with note but no scale should preserve note and compute frequency" {
        val voice = VoiceData.empty.copy(note = "A4")

        val result = voice.resolveNote()

        result.note shouldBe "A4"
        result.freqHz shouldBe 440.0
    }

    "resolveNote() with value as note string should use it as fallback" {
        val voice = VoiceData.empty.copy(
            value = "C4".asVoiceValue()
        )

        val result = voice.resolveNote()

        result.note shouldBe "C4"
        result.freqHz.let {
            it.shouldNotBeNull()
            it shouldBeGreaterThan 200.0
        }
    }

    "resolveNote() priority: newIndex over soundIndex" {
        val voice = VoiceData.empty.copy(
            scale = "C3:major",
            soundIndex = 0
        )

        val result = voice.resolveNote(newIndex = 4)

        result.note shouldBe "G3"
    }

    "resolveNote() priority: soundIndex over value" {
        val voice = VoiceData.empty.copy(
            scale = "C major",
            soundIndex = 2,
            value = 4.asVoiceValue()
        )

        val result = voice.resolveNote()

        result.note shouldBe "E"
    }

    "resolveNote() with empty voice should return empty voice" {
        val voice = VoiceData.empty

        val result = voice.resolveNote()

        result.note shouldBe null
        result.freqHz shouldBe 440.0
        result.soundIndex shouldBe null
    }
})
