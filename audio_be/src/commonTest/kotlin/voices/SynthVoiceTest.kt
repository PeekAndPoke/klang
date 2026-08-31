/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.filters.AudioFilter
import io.peekandpoke.klang.audio_be.filters.LowPassHighPassFilters
import io.peekandpoke.klang.audio_be.ignitor.IgniteContext
import io.peekandpoke.klang.audio_be.ignitor.Ignitor
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.createContext
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.createSynthVoice
import io.peekandpoke.klang.audio_bridge.AdsrCurve

/**
 * Tests specific to SynthVoice implementation.
 * Verifies Ignitor integration and synthesis-specific behavior.
 */
class SynthVoiceTest : StringSpec({

    "SynthVoice with constant signal produces constant output" {
        val voice = createSynthVoice(
            signal = TestIgnitors.constant
        )

        val ctx = createContext()
        voice.render(ctx)

        ctx.voiceBuffer.all { it == 1.0 } shouldBe true
    }

    "SynthVoice with silence signal produces no output" {
        val voice = createSynthVoice(
            signal = TestIgnitors.silence
        )

        val ctx = createContext()
        voice.render(ctx)

        ctx.voiceBuffer.all { it == 0.0 } shouldBe true
    }

    "SynthVoice with ramp signal produces ramping output" {
        val voice = createSynthVoice(
            signal = TestIgnitors.ramp,
            blockFrames = 10,
        )

        val ctx = createContext(blockFrames = 10)
        voice.render(ctx)

        ctx.voiceBuffer[0] shouldBe (0.0 plusOrMinus 0.01)
        ctx.voiceBuffer[9] shouldBe (0.9 plusOrMinus 0.01)
    }

    "SynthVoice passes pitch modulation to signal" {
        var receivedPhaseMod: DoubleArray? = null

        val trackingSignal: Ignitor = object : Ignitor {
            override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
                receivedPhaseMod = ctx.phaseMod
                val end = ctx.windowEnd
                for (i in ctx.offset until end) buffer[i] = 1.0
            }
        }

        val voice = createSynthVoice(
            signal = trackingSignal,
            vibrato = Voice.Vibrato(rate = 5.0, semitones = 0.25),
        )

        val ctx = createContext()
        voice.render(ctx)

        // Signal should receive pitch modulation (non-null DoubleArray)
        receivedPhaseMod.shouldNotBeNull()
        receivedPhaseMod!!.size shouldBeGreaterThanOrEqual ctx.blockFrames
    }

    "SynthVoice without pitch modulation passes null to signal" {
        var receivedPhaseMod: DoubleArray? = null

        val trackingSignal: Ignitor = object : Ignitor {
            override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
                receivedPhaseMod = ctx.phaseMod
                val end = ctx.windowEnd
                for (i in ctx.offset until end) buffer[i] = 1.0
            }
        }

        val voice = createSynthVoice(
            signal = trackingSignal,
            vibrato = Voice.Vibrato(rate = 0.0, semitones = 0.0),
        )

        val ctx = createContext()
        voice.render(ctx)

        (receivedPhaseMod == null) shouldBe true
    }

    // RENAMED 2026-08-31. The old name was "SynthVoice getBaseFrequency returns freqHz", and there
    // is no `getBaseFrequency` anywhere in the codebase — the test was named for a symbol that does
    // not exist, and its body only called render(). The claim underneath it is real and worth
    // guarding: the voice's freqHz is what reaches the oscillator.
    "SynthVoice passes its freqHz to the ignitor" {
        fun seenBy(freqHz: Double): Double {
            var seen = -1.0
            val probe = object : Ignitor {
                override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
                    seen = freqHz
                    for (i in ctx.offset until ctx.windowEnd) {
                        buffer[i] = 1.0
                    }
                }
            }

            val voice = createSynthVoice(freqHz = freqHz, signal = probe)
            voice.render(createContext())

            return seen
        }

        // TWO frequencies, not one: asserting a single 440.0 would still pass if the pitch were
        // hard-coded, which is exactly the bug this guards against.
        seenBy(440.0) shouldBe 440.0
        seenBy(880.0) shouldBe 880.0
    }

    "SynthVoice with envelope modulates signal output" {
        val voice = createSynthVoice(
            signal = TestIgnitors.constant,
            blockFrames = 100,
            envelope = Voice.Envelope(
                attackFrames = 100.0,
                decayFrames = 0.0,
                sustainLevel = 1.0,
                releaseFrames = 0.0,
                attackCurve = AdsrCurve.Linear,
                decayCurve = AdsrCurve.Linear,
                releaseCurve = AdsrCurve.Linear,
            )
        )

        val ctx = createContext(blockFrames = 100)
        voice.render(ctx)

        // VCA gain is de-clicked, so the linear attack ramp lags slightly; verify the
        // envelope modulates the signal up from ~0 monotonically (exact shape: EnvelopeShapeTest).
        ctx.voiceBuffer[0] shouldBe (0.0 plusOrMinus 0.02)
        (ctx.voiceBuffer[50] > ctx.voiceBuffer[0]) shouldBe true
        (ctx.voiceBuffer[99] > ctx.voiceBuffer[50]) shouldBe true
        (ctx.voiceBuffer[99] > 0.6) shouldBe true
    }

    "SynthVoice with filter affects signal output" {
        // The old fixture passed VoiceTestHelpers.NoOpFilter — a filter that by definition cannot
        // affect the signal — so the test asserted its own name false and then checked nothing.
        // A one-pole highpass on a constant is the clearest possible case: DC is exactly what a
        // highpass removes, so the output has to collapse away from the unfiltered 1.0.
        fun render(filter: AudioFilter): AudioBuffer {
            val voice = createSynthVoice(signal = TestIgnitors.constant, filter = filter)
            val ctx = createContext()
            voice.render(ctx)

            return ctx.voiceBuffer
        }

        val unfiltered = render(VoiceTestHelpers.NoOpFilter)
        val highpassed = render(LowPassHighPassFilters.OnePoleHPF(cutoffHz = 5000.0, sampleRate = 44100.0))

        unfiltered.all { it == 1.0 } shouldBe true
        kotlin.math.abs(highpassed[99]) shouldBe 0.0.plusOrMinus(0.05)
    }

    "SynthVoice with all modulations renders correctly" {
        val voice = createSynthVoice(
            signal = TestIgnitors.constant,
            freqHz = 440.0,
            vibrato = Voice.Vibrato(rate = 5.0, semitones = 0.25),
            accelerate = Voice.Accelerate(semitones = 1.0),
            pitchEnvelope = Voice.PitchEnvelope(
                attackFrames = 50.0,
                decayFrames = 50.0,
                releaseFrames = 0.0,
                semitones = 1.0,
                curve = 0.0,
                anchor = 0.0
            ),
            fm = Voice.Fm(
                ratio = 2.0,
                depth = 100.0,
                envelope = Voice.Envelope(0.0, 0.0, 1.0, 0.0)
            ),
            envelope = Voice.Envelope(
                attackFrames = 100.0,
                decayFrames = 0.0,
                sustainLevel = 1.0,
                releaseFrames = 0.0
            )
        )

        val ctx = createContext()
        voice.render(ctx)

        // Vibrato + accelerate + a pitch envelope + FM all drive the same phase accumulator, which
        // is where a non-finite pitch would surface. A NaN here propagates into the cylinder and
        // kills the orbit silently, so "renders correctly" now means audible AND finite.
        ctx.voiceBuffer.any { it != 0.0 } shouldBe true
        ctx.voiceBuffer.all { it == it && kotlin.math.abs(it) <= 1.0e6 } shouldBe true
    }

    "SynthVoice signal receives correct buffer parameters" {
        var receivedOffset = -1
        var receivedLength = -1

        val trackingSignal: Ignitor = object : Ignitor {
            override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
                receivedOffset = ctx.offset
                receivedLength = ctx.length
                val end = ctx.windowEnd
                for (i in ctx.offset until end) buffer[i] = 1.0
            }
        }

        val voice = createSynthVoice(
            startFrame = 0.0,
            endFrame = 100.0,
            signal = trackingSignal,
        )

        val ctx = createContext(blockStart = 0.0, blockFrames = 100)
        voice.render(ctx)

        receivedOffset shouldBe 0
        receivedLength shouldBe 100
    }

    "SynthVoice with partial block renders correct length" {
        var receivedLength = -1

        val trackingSignal: Ignitor = object : Ignitor {
            override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
                receivedLength = ctx.length
                val end = ctx.windowEnd
                for (i in ctx.offset until end) buffer[i] = 1.0
            }
        }

        val voice = createSynthVoice(
            startFrame = 50.0,
            endFrame = 150.0,
            signal = trackingSignal,
        )

        val ctx = createContext(blockStart = 0.0, blockFrames = 100)
        voice.render(ctx)

        receivedLength shouldBe 50
    }

    "SynthVoice tracks elapsed frames across multiple renders" {
        val elapsedFrames = mutableListOf<Int>()

        val trackingSignal: Ignitor = object : Ignitor {
            override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
                elapsedFrames.add(ctx.voiceElapsedFrames)
                val end = ctx.windowEnd
                for (i in ctx.offset until end) buffer[i] = 1.0
            }
        }

        val voice = createSynthVoice(
            signal = trackingSignal,
        )

        voice.render(createContext(blockStart = 0.0, blockFrames = 100))
        voice.render(createContext(blockStart = 100.0, blockFrames = 100))
        voice.render(createContext(blockStart = 200.0, blockFrames = 100))

        elapsedFrames.size shouldBe 3
        elapsedFrames[0] shouldBe 0
        elapsedFrames[1] shouldBe 100
        elapsedFrames[2] shouldBe 200
    }
})
