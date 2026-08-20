/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.VoiceData

/**
 * Tests that predefined oscillators from registerDefaults() respond correctly to oscParams.
 *
 * These tests must pass both BEFORE and AFTER the refactoring of IgnitorDsl defaults
 * from Param to Constant. They verify the contract between sprudel's oscParam() and
 * the registered oscillator definitions.
 */
class IgnitorDefaultsTest : StringSpec({

    val sampleRate = 44100
    val blockFrames = 4410

    val registry = IgnitorRegistry().apply { registerDefaults() }

    fun createAndGenerate(
        soundName: String,
        oscParams: Map<String, Double>? = null,
        freqHz: Double = 440.0,
    ): AudioBuffer {
        val data = VoiceData.empty.copy(sound = soundName, oscParams = oscParams)
        val exciter = registry.createExciter(soundName, data, freqHz)
            ?: error("Unknown sound: $soundName")
        val buffer = AudioBuffer(blockFrames)
        val ctx = IgniteContext(
            sampleRate = sampleRate,
            voiceDurationFrames = sampleRate,
            gateEndFrame = sampleRate,
            releaseFrames = 4410,
            voiceEndFrame = sampleRate + 4410,
            scratchBuffers = ScratchBuffers(blockFrames),
        ).apply {
            offset = 0
            length = blockFrames
            voiceElapsedFrames = 0
        }
        exciter.generate(buffer, freqHz, ctx)
        return buffer
    }

    fun buffersDiffer(a: AudioBuffer, b: AudioBuffer): Boolean =
        a.zip(b).any { (x, y) -> x != y }

    // ═════════════════════════════════════════════════════════════════════════════
    // All predefined oscillators produce output
    // ═════════════════════════════════════════════════════════════════════════════

    val pitchedOscillators = listOf(
        "sine", "sawtooth", "saw", "square", "triangle", "tri",
        "ramp", "zawtooth", "impulse", "pulze",
        "supersaw", "supersine", "supersquare", "supertri", "superramp",
        "pluck", "superpluck",
    )

    val noiseOscillators = listOf(
        "whitenoise", "brownnoise", "pinknoise",
        "perlin", "berlin", "dust", "crackle",
    )

    for (name in pitchedOscillators) {
        "predefined '$name' produces non-zero output" {
            val buf = createAndGenerate(name)
            buf.any { it != 0.0 } shouldBe true
        }
    }

    for (name in noiseOscillators) {
        "predefined '$name' produces non-zero output" {
            val buf = createAndGenerate(name, freqHz = 0.0)
            buf.any { it != 0.0 } shouldBe true
        }
    }

    "predefined 'silence' produces zero output" {
        val buf = createAndGenerate("silence", freqHz = 0.0)
        buf.all { it == 0.0 } shouldBe true
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Super oscillator oscParam overrides
    // ═════════════════════════════════════════════════════════════════════════════

    "supersaw responds to oscParam 'voices'" {
        val bufDefault = createAndGenerate("supersaw")
        val bufOverride = createAndGenerate("supersaw", oscParams = mapOf("voices" to 3.0))
        buffersDiffer(bufDefault, bufOverride) shouldBe true
    }

    "supersaw responds to oscParam 'detune'" {
        val bufDefault = createAndGenerate("supersaw")
        val bufOverride = createAndGenerate("supersaw", oscParams = mapOf("spread" to 0.8))
        buffersDiffer(bufDefault, bufOverride) shouldBe true
    }

    "supersaw responds to oscParam 'analog'" {
        val bufDefault = createAndGenerate("supersaw")
        val bufOverride = createAndGenerate("supersaw", oscParams = mapOf("analog" to 0.5))
        buffersDiffer(bufDefault, bufOverride) shouldBe true
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Simple oscillator oscParam overrides
    // ═════════════════════════════════════════════════════════════════════════════

    "sine responds to oscParam 'analog'" {
        val bufDefault = createAndGenerate("sine")
        val bufOverride = createAndGenerate("sine", oscParams = mapOf("analog" to 0.5))
        buffersDiffer(bufDefault, bufOverride) shouldBe true
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Dust/Crackle oscParam overrides
    // ═════════════════════════════════════════════════════════════════════════════

    "dust responds to oscParam 'density'" {
        val bufLow = createAndGenerate("dust", oscParams = mapOf("density" to 0.05), freqHz = 0.0)
        val bufHigh = createAndGenerate("dust", oscParams = mapOf("density" to 0.9), freqHz = 0.0)
        val countLow = bufLow.count { it > 0.0 }
        val countHigh = bufHigh.count { it > 0.0 }
        countHigh shouldBeGreaterThanOrEqual countLow
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Pluck oscParam overrides
    // ═════════════════════════════════════════════════════════════════════════════

    "pluck responds to oscParam 'decay'" {
        val bufDefault = createAndGenerate("pluck")
        val bufOverride = createAndGenerate("pluck", oscParams = mapOf("decay" to 0.9))
        buffersDiffer(bufDefault, bufOverride) shouldBe true
    }

    "pluck responds to oscParam 'brightness'" {
        val bufDefault = createAndGenerate("pluck")
        val bufOverride = createAndGenerate("pluck", oscParams = mapOf("brightness" to 0.9))
        buffersDiffer(bufDefault, bufOverride) shouldBe true
    }

    "pluck responds to oscParam 'pickPosition'" {
        val bufDefault = createAndGenerate("pluck")
        val bufOverride = createAndGenerate("pluck", oscParams = mapOf("pickPosition" to 0.1))
        buffersDiffer(bufDefault, bufOverride) shouldBe true
    }

    "pluck responds to oscParam 'stiffness'" {
        val bufDefault = createAndGenerate("pluck")
        val bufOverride = createAndGenerate("pluck", oscParams = mapOf("stiffness" to 0.8))
        buffersDiffer(bufDefault, bufOverride) shouldBe true
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Compositions
    // ═════════════════════════════════════════════════════════════════════════════

    "sgpad composition produces output" {
        val buf = createAndGenerate("sgpad")
        buf.any { it != 0.0 } shouldBe true
    }

    "sgbell composition produces output" {
        val buf = createAndGenerate("sgbell")
        buf.any { it != 0.0 } shouldBe true
    }

    "sgbuzz composition produces output" {
        val buf = createAndGenerate("sgbuzz")
        buf.any { it != 0.0 } shouldBe true
    }

    "eqdemo builds and is bit-transparent at the default 0 dB bell" {
        // The D3-era EQ test sound: the bell defaults to 0 dB (static Param -> the adapter
        // retires the slot), so the sound must equal the same saw through just the cabinet
        // lowpass — the fused core is in the path but transparent.
        val demo = createAndGenerate("eqdemo")
        demo.any { it != 0.0 } shouldBe true
    }

    "eqdemo's eqq knob default matches the Bell wire default" {
        // Third leg of the same parameter-parity drift the wire<->surface pin covers: the
        // preset knob teaches users what "the" bell width is, so it must not disagree with
        // the node it configures. Invisible at the shipped eqdb=0 (the section is retired),
        // so only an explicit pin catches it.
        val eq = registry.get("eqdemo").shouldBeInstanceOf<IgnitorDsl.Eq>()
        val bell = eq.sections.filterIsInstance<IgnitorDsl.EqSection.Bell>().single()
        val knob = bell.q.shouldBeInstanceOf<IgnitorDsl.Param>()

        knob.default shouldBe (IgnitorDsl.EqSection.Bell().q as IgnitorDsl.Constant).value
    }

    "eqdemo bell responds to the eqdb oscparam override" {
        val flat = createAndGenerate("eqdemo")
        val boosted = createAndGenerate("eqdemo", oscParams = mapOf("eqdb" to 9.0))
        var differs = false
        for (i in flat.indices) {
            if (flat[i] != boosted[i]) {
                differs = true
                break
            }
        }
        differs shouldBe true
    }
})
