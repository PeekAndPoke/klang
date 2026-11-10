/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_be.SampleStore
import io.peekandpoke.klang.audio_be.cylinders.offerAndCommit
import io.peekandpoke.klang.audio_be.ignitor.IgnitorRegistry
import io.peekandpoke.klang.audio_be.ignitor.PhasePools
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.ignitor.registerDefaults
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.createContext
import io.peekandpoke.klang.audio_bridge.SampleRequest
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import kotlin.math.abs
import kotlin.random.Random

/**
 * `pan` and `freqHz`, the two typed wire numbers the factory read raw until the engine audit (audit L1-01, L6-02,
 * L1-02). The rule is the one every wire number follows (`/dsl-design` section 4, `audio/ref/numerical-safety.md`,
 * "Non-finite inputs"): a non-finite value reads as UNSET at its one reader, `VoiceFactory`. Every finite value passes
 * raw (the Motor stays raw).
 *
 * What each did raw, measured by the audit: a NaN `pan` survived `coerceIn`, the pan law made both channel gains NaN,
 * and the voice wrote NaN into its cylinder for its whole life, which silenced the whole output while it sounded. A
 * NaN `freqHz` made a sample voice's playhead NaN from its second frame, and the Karplus family (`pluck`, `string`,
 * `superpluck`, `ks`) NaN from its first. A sample whose own pitch is 0 played at 0 Hz (0/0) did the same without any
 * NaN on the wire.
 */
class VoicePanPitchWireSpec : StringSpec({

    val sampleRate = 48000
    val blockFrames = 128
    val blocks = 20

    val sampleSound = "panpitch-sample"

    fun sampleEntry(pitchHz: Double) = SampleStore.SampleEntry.Complete(
        req = SampleRequest(bank = null, sound = sampleSound, index = null, note = null),
        note = null,
        pitchHz = pitchHz,
        sample = TestSamples.sine(size = blocks * blockFrames * 2, sampleRate = sampleRate),
    )

    /** A fresh registry and playback per voice, so two renders of equal data draw the same random values. */
    fun voiceOf(data: VoiceData, getSample: (SampleRequest) -> SampleStore.SampleEntry.Complete? = { null }): Voice {
        val registry = IgnitorRegistry().apply { registerDefaults() }
        val factory = VoiceFactory(sampleRate = sampleRate, blockFrames = blockFrames, scratchBuffers = ScratchBuffers(blockFrames))

        return factory.makeVoice(
            scheduled = ScheduledVoice(playbackId = "test", data = data, startTime = 0.0, gateEndTime = 0.2, playbackStartTime = 0.0),
            backendStartTimeSec = 0.0,
            playbackCtx = PlaybackCtx(playbackId = "test", ignitorRegistry = registry, phasePools = PhasePools(Random(1))),
            getSample = getSample,
        ) ?: error("makeVoice returned null")
    }

    /** Both channels of the voice's cylinder mix over [blocks] blocks, left then right per block. */
    fun render(data: VoiceData, getSample: (SampleRequest) -> SampleStore.SampleEntry.Complete? = { null }): DoubleArray {
        val voice = voiceOf(data, getSample)
        val ctx = createContext(blockStart = 0.0, blockFrames = blockFrames, sampleRate = sampleRate)
        val out = DoubleArray(blocks * blockFrames * 2)

        for (block in 0 until blocks) {
            ctx.blockStart = (block * blockFrames).toDouble()
            voice.render(ctx)

            val mix = ctx.cylinders.offerAndCommit(voice.cylinderId, voice, 0.0).mixBuffer

            mix.left.copyInto(destination = out, destinationOffset = block * blockFrames * 2)
            mix.right.copyInto(destination = out, destinationOffset = block * blockFrames * 2 + blockFrames)
            mix.left.fill(0.0)
            mix.right.fill(0.0)
        }

        return out
    }

    fun assertSameBits(clue: String, expected: DoubleArray, actual: DoubleArray) {
        for (i in expected.indices) {
            withClue("$clue, sample $i") {
                actual[i].toRawBits() shouldBe expected[i].toRawBits()
            }
        }
    }

    fun peakOf(buf: DoubleArray): Double = buf.maxOf { abs(it) }

    // ── pan ──────────────────────────────────────────────────────────────────────────────────────────────────────────

    fun wirePan(pan: Double?): Double = voiceOf(VoiceData.empty.copy(freqHz = 440.0, sound = "sine", pan = pan)).pan

    "an unset pan reads as the centre" {
        wirePan(null) shouldBe 0.5
    }

    "a non-finite pan reads as unset" {
        wirePan(Double.NaN) shouldBe 0.5
        wirePan(Double.POSITIVE_INFINITY) shouldBe 0.5
        wirePan(Double.NEGATIVE_INFINITY) shouldBe 0.5
    }

    "a finite pan passes raw, outside 0 to 1 included" {
        wirePan(0.0) shouldBe 0.0
        wirePan(1.0) shouldBe 1.0
        wirePan(-0.3) shouldBe -0.3
        wirePan(1.7) shouldBe 1.7
    }

    "a NaN pan from the wire renders exactly what an unset pan renders" {
        val sine = VoiceData.empty.copy(freqHz = 440.0, sound = "sine")
        val reference = render(sine)
        val poisoned = render(sine.copy(pan = Double.NaN))

        withClue("not-silence floor") {
            peakOf(reference) shouldBeGreaterThan 0.01
        }

        withClue("engagement: a hard-left pan renders otherwise") {
            render(sine.copy(pan = 0.0)).toList() shouldNotBe reference.toList()
        }

        assertSameBits("NaN pan", expected = reference, actual = poisoned)
    }

    // ── freqHz on the Karplus family ─────────────────────────────────────────────────────────────────────────────────

    "a NaN pitch on the Karplus family renders what an unset pitch renders, finite" {
        for (sound in listOf("pluck", "string", "superpluck", "ks")) {
            val unset = render(VoiceData.empty.copy(sound = sound))
            val poisoned = render(VoiceData.empty.copy(sound = sound, freqHz = Double.NaN))

            withClue("$sound: engagement, a real pitch sounds") {
                peakOf(render(VoiceData.empty.copy(sound = sound, freqHz = 220.0))) shouldBeGreaterThan 0.01
            }

            withClue("$sound: every sample finite") {
                poisoned.all { it.isFinite() } shouldBe true
            }

            assertSameBits("$sound, NaN pitch", expected = unset, actual = poisoned)
        }
    }

    // ── freqHz and the sample's own pitch on a sample voice ─────────────────────────────────────────────────────────

    "a non-finite pitch on a sample voice plays the sample at its own pitch, as an unset one does" {
        val entry = sampleEntry(pitchHz = 440.0)
        val unset = render(VoiceData.empty.copy(sound = sampleSound)) { entry }

        withClue("not-silence floor") {
            peakOf(unset) shouldBeGreaterThan 0.01
        }

        withClue("engagement: another pitch plays otherwise") {
            render(VoiceData.empty.copy(sound = sampleSound, freqHz = 220.0)) { entry }.toList() shouldNotBe unset.toList()
        }

        for (pitch in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertSameBits("freqHz $pitch", expected = unset, actual = render(VoiceData.empty.copy(sound = sampleSound, freqHz = pitch)) { entry })
        }
    }

    "a sample whose own pitch is 0, played at 0 Hz, plays at its own rate instead of NaN" {
        val zeroPitched = sampleEntry(pitchHz = 0.0)
        val zeroByZero = render(VoiceData.empty.copy(sound = sampleSound, freqHz = 0.0)) { zeroPitched }
        val unsetOnZero = render(VoiceData.empty.copy(sound = sampleSound)) { zeroPitched }

        withClue("0 Hz on a 0 Hz sample: every sample finite, and the sample sounds") {
            zeroByZero.all { it.isFinite() } shouldBe true
            peakOf(zeroByZero) shouldBeGreaterThan 0.01
        }

        withClue("an unset pitch on a 0 Hz sample: the same") {
            unsetOnZero.all { it.isFinite() } shouldBe true
            peakOf(unsetOnZero) shouldBeGreaterThan 0.01
        }
    }
})
