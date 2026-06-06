package io.peekandpoke.klang.sprudel.lang.helpers

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.SprudelVoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.sprudel.createSprudelVoiceData
import io.peekandpoke.klang.sprudel.lang.resolveNote

class VoiceDataSpec : StringSpec({

    "resolveNote() with scale and index should resolve note and frequency" {
        val voice = createSprudelVoiceData(scale = "C major")

        val result = voice.resolveNote(newIndex = 0)

        result.note shouldBe "C3"
        result.freqHz.let {
            it.shouldNotBeNull()
            it shouldBeGreaterThan 100.0
        }
        result.soundIndex shouldBe null
        result.value shouldBe null
    }

    "resolveNote() with scale containing colon should clean it" {
        val voice = createSprudelVoiceData(scale = "C3:major")

        val result = voice.resolveNote(newIndex = 2)

        result.note shouldBe "E3"
        result.freqHz.let {
            it.shouldNotBeNull()
            it shouldBe (164.8 plusOrMinus 0.1)
        }
    }

    "resolveNote() with soundIndex and scale should use soundIndex" {
        val voice = createSprudelVoiceData(
            scale = "C major",
            soundIndex = 4
        )

        val result = voice.resolveNote()

        result.note shouldBe "G3"
        result.soundIndex shouldBe null
    }

    "resolveNote() with value and scale should interpret value as index" {
        val voice = createSprudelVoiceData(
            scale = "C4 major",
            value = 2.asVoiceValue()
        )

        val result = voice.resolveNote()

        result.note shouldBe "E4"
        result.value shouldBe null
    }

    "resolveNote() with newIndex but no scale should set soundIndex" {
        val voice = createSprudelVoiceData()

        val result = voice.resolveNote(newIndex = 5)

        result.soundIndex shouldBe 5
        result.note shouldBe null
        result.freqHz shouldBe null
    }

    "resolveNote() with note but no scale should preserve note and compute frequency" {
        val voice = createSprudelVoiceData(note = "A4")

        val result = voice.resolveNote()

        result.note shouldBe "A4"
        result.freqHz shouldBe 440.0
    }

    "resolveNote() with value as note string should use it as fallback" {
        val voice = createSprudelVoiceData(
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
        val voice = createSprudelVoiceData(
            scale = "C3:major",
            soundIndex = 0
        )

        val result = voice.resolveNote(newIndex = 4)

        result.note shouldBe "G3"
    }

    "resolveNote() priority: value over soundIndex (new contract)" {
        // value is the scale-step input; soundIndex carries variant override.
        // With value=4 + soundIndex=2 + scale, value wins for note resolution
        // and soundIndex survives as the variant.
        val voice = createSprudelVoiceData(
            scale = "C major",
            soundIndex = 2,
            value = 4.asVoiceValue()
        )

        val result = voice.resolveNote()

        result.note shouldBe "G3"
        result.soundIndex shouldBe 2 // preserved — not the step source
        result.value shouldBe null
    }

    "resolveNote() parses value 'step:variant' into note + soundIndex override" {
        val voice = createSprudelVoiceData(
            scale = "C major",
            value = "0:1".asVoiceValue()
        )

        val result = voice.resolveNote()

        result.note shouldBe "C3"
        result.soundIndex shouldBe 1 // parsed variant override
        result.value shouldBe null
    }

    "resolveNote() parses value 'step:variant:gain' into all three" {
        val voice = createSprudelVoiceData(
            scale = "C major",
            value = "0:1:0.5".asVoiceValue()
        )

        val result = voice.resolveNote()

        result.note shouldBe "C3"
        result.soundIndex shouldBe 1
        result.gain shouldBe 0.5
        result.value shouldBe null
    }

    "resolveNote() leaves soundIndex untouched when value has no :variant" {
        // seq("1").scale(...) — only step parsed, no variant override.
        // Existing soundIndex on the voice should NOT be overwritten with null.
        val voice = createSprudelVoiceData(
            scale = "C major",
            value = "1".asVoiceValue(),
            soundIndex = 7
        )

        val result = voice.resolveNote()

        result.note shouldBe "D3"
        result.soundIndex shouldBe 7 // preserved
    }

    "resolveNote() leaves gain untouched when value has no :gain" {
        val voice = createSprudelVoiceData(
            scale = "C major",
            value = "0:1".asVoiceValue(),
            gain = 0.42
        )

        val result = voice.resolveNote()

        result.gain shouldBe 0.42 // preserved (only :variant parsed, not :gain)
    }

    "resolveNote() value :variant OVERWRITES an existing soundIndex" {
        // The "only update when parsed is non-null" rule cuts the other way too:
        // when the parse DOES yield a variant, it must replace whatever soundIndex
        // was sitting on the voice (e.g. from a prior `.n(...)`).
        val voice = createSprudelVoiceData(
            scale = "C major",
            value = "0:1".asVoiceValue(),
            soundIndex = 7
        )

        val result = voice.resolveNote()

        result.note shouldBe "C3"
        result.soundIndex shouldBe 1 // parsed variant wins
    }

    "resolveNote() value :gain OVERWRITES an existing gain" {
        val voice = createSprudelVoiceData(
            scale = "C major",
            value = "0:1:0.7".asVoiceValue(),
            gain = 0.42
        )

        val result = voice.resolveNote()

        result.gain shouldBe 0.7 // parsed gain wins
    }

    "resolveNote() numeric value still resolves as scale step" {
        // seq(2) (numeric) wraps as Num(2.0) — its asString is "2.0", which
        // does NOT parse as Int. Falls back to value.asInt for the step.
        val voice = createSprudelVoiceData(
            scale = "C major",
            value = 2.asVoiceValue()
        )

        val result = voice.resolveNote()

        result.note shouldBe "E3"
        result.soundIndex shouldBe null
        result.value shouldBe null
    }

    "resolveNote() with empty voice should return empty voice" {
        val voice = createSprudelVoiceData()

        val result = voice.resolveNote()

        result.note shouldBe null
        result.freqHz shouldBe 440.0
        result.soundIndex shouldBe null
    }
})
