/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.ignitor.SampleIgnitor
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.createContext
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.createVoice
import io.peekandpoke.klang.audio_bridge.AdsrCurve

/**
 * Tests specific to sample playback via SampleIgnitor.
 * Verifies sample playback, looping, and interpolation.
 */
class SampleVoiceSpecificTest : StringSpec({

    "SampleVoice plays back sample data correctly" {
        val sample = TestSamples.constant(size = 200, value = 0.5) // Constant 0.5, longer than block

        val voice = createVoice(
            signal = SampleIgnitor(
                pcm = sample.pcm,
                rate = 1.0,
                playhead = 0.0,
                loopStart = -1.0,
                loopEnd = -1.0,
                isLooping = false,
                stopFrame = Double.MAX_VALUE,
                sampleRate = 48000,
            ),
            freqHz = 440.0,
        )

        val ctx = createContext(blockFrames = 100)
        voice.render(ctx)

        // With "always on" envelope, first 100 samples should be 0.5
        ctx.voiceBuffer.all { it == 0.5 } shouldBe true
    }

    "SampleVoice with rate > 1 plays faster" {
        val sample = TestSamples.ramp(size = 100)

        val voice = createVoice(
            signal = SampleIgnitor(
                pcm = sample.pcm,
                rate = 2.0, // Double speed
                playhead = 0.0,
                loopStart = -1.0,
                loopEnd = -1.0,
                isLooping = false,
                stopFrame = Double.MAX_VALUE,
                sampleRate = 48000,
            ),
            freqHz = 440.0,
        )

        val ctx = createContext(blockFrames = 50)
        voice.render(ctx)

        // Should cover full sample in 50 frames
        ctx.voiceBuffer[0] shouldBe (0.0 plusOrMinus 0.01)
        ctx.voiceBuffer[49] shouldBe (0.98 plusOrMinus 0.03)
    }

    "SampleVoice with rate < 1 plays slower" {
        val sample = TestSamples.ramp(size = 100)

        val voice = createVoice(
            signal = SampleIgnitor(
                pcm = sample.pcm,
                rate = 0.5, // Half speed
                playhead = 0.0,
                loopStart = -1.0,
                loopEnd = -1.0,
                isLooping = false,
                stopFrame = Double.MAX_VALUE,
                sampleRate = 48000,
            ),
            freqHz = 440.0,
        )

        val ctx = createContext(blockFrames = 100)
        voice.render(ctx)

        // Should only cover half the sample in 100 frames
        ctx.voiceBuffer[0] shouldBe (0.0 plusOrMinus 0.01)
        ctx.voiceBuffer[99] shouldBe (0.50 plusOrMinus 0.02)
    }

    "SampleVoice performs linear interpolation" {
        val sample = TestSamples.ramp(size = 10) // 0.0, 0.111, 0.222, ..., 1.0

        val voice = createVoice(
            signal = SampleIgnitor(
                pcm = sample.pcm,
                rate = 1.5, // Non-integer playback rate
                playhead = 0.0,
                loopStart = -1.0,
                loopEnd = -1.0,
                isLooping = false,
                stopFrame = Double.MAX_VALUE,
                sampleRate = 48000,
            ),
            freqHz = 440.0,
        )

        val ctx = createContext(blockFrames = 5)
        voice.render(ctx)

        // ramp(10) is pcm[i] = i/9. At rate 1.5, frame k reads playhead 1.5k, and interpolating a
        // STRAIGHT line linearly has to reproduce the line: out[k] = 1.5k/9. Derived from the
        // definition of lerp, not read off a recorded run.
        for (k in 0 until 5) {
            ctx.voiceBuffer[k] shouldBe (1.5 * k / 9.0).plusOrMinus(1e-9)
        }

        // The row that separates linear from truncation or nearest-neighbour: frame 1 sits exactly
        // half way between pcm[1] and pcm[2]. Truncating would return pcm[1] = 0.1111... instead.
        ctx.voiceBuffer[1] shouldBe ((1.0 / 9.0 + 2.0 / 9.0) / 2.0).plusOrMinus(1e-9)
    }

    "SampleVoice without looping stops at end" {
        val sample = TestSamples.ramp(size = 50)

        val voice = createVoice(
            signal = SampleIgnitor(
                pcm = sample.pcm,
                rate = 1.0,
                playhead = 0.0,
                loopStart = -1.0,
                loopEnd = -1.0,
                isLooping = false,
                stopFrame = Double.MAX_VALUE,
                sampleRate = 48000,
            ),
            freqHz = 440.0,
        )

        val ctx = createContext(blockFrames = 100)
        voice.render(ctx)

        // First 50 samples should have audio
        (ctx.voiceBuffer[25] > 0.0) shouldBe true

        // After sample ends, should be silent
        ctx.voiceBuffer[75] shouldBe 0.0
    }

    "SampleVoice with explicit looping wraps correctly" {
        val sample = TestSamples.ramp(size = 100)

        val voice = createVoice(
            signal = SampleIgnitor(
                pcm = sample.pcm,
                rate = 1.0,
                playhead = 0.0,
                loopStart = 0.0,
                loopEnd = 50.0, // Loop first half
                isLooping = true,
                stopFrame = Double.MAX_VALUE,
                sampleRate = 48000,
            ),
            freqHz = 440.0,
        )

        val ctx = createContext(blockFrames = 100)
        voice.render(ctx)

        // First 50 frames should play 0.0 to 0.5
        ctx.voiceBuffer[0] shouldBe (0.0 plusOrMinus 0.01)
        ctx.voiceBuffer[49] shouldBe (0.49 plusOrMinus 0.02)

        // Next 50 frames should loop back and play 0.0 to 0.5 again
        ctx.voiceBuffer[50] shouldBe (0.0 plusOrMinus 0.02)
        ctx.voiceBuffer[99] shouldBe (0.49 plusOrMinus 0.02)
    }

    "SampleVoice with stopFrame ends early" {
        val sample = TestSamples.ramp(size = 100)

        val voice = createVoice(
            signal = SampleIgnitor(
                pcm = sample.pcm,
                rate = 1.0,
                playhead = 0.0,
                loopStart = -1.0,
                loopEnd = -1.0,
                isLooping = false,
                stopFrame = 50.0, // Stop at frame 50
                sampleRate = 48000,
            ),
            freqHz = 440.0,
        )

        val ctx = createContext(blockFrames = 100)
        voice.render(ctx)

        // First 50 frames should have audio
        (ctx.voiceBuffer[25] > 0.0) shouldBe true

        // After stopFrame, should be silent
        ctx.voiceBuffer[75] shouldBe 0.0
    }

    "SampleVoice playhead advances correctly" {
        // A ramp, NOT a constant: the old fixture was a constant 1.0, which is why this test could
        // only say "can't directly verify playhead without access to private field". A ramp makes
        // the playhead position readable straight off the output value.
        val sample = TestSamples.ramp(size = 100)

        // Create voice with initial playhead
        val voice = createVoice(
            signal = SampleIgnitor(
                pcm = sample.pcm,
                rate = 1.0,
                playhead = 10.0, // Start at sample 10
                loopStart = -1.0,
                loopEnd = -1.0,
                isLooping = false,
                stopFrame = Double.MAX_VALUE,
                sampleRate = 48000,
            ),
            freqHz = 440.0,
        )

        val ctx = createContext(blockFrames = 10)
        voice.render(ctx)

        // ramp(100) is pcm[i] = i/99. Starting at playhead 10 and advancing by rate 1.0 per frame,
        // frame k must read pcm[10 + k] — which pins BOTH the start offset and the step size.
        for (k in 0 until 10) {
            ctx.voiceBuffer[k] shouldBe ((10.0 + k) / 99.0).plusOrMinus(1e-9)
        }
    }

    "SampleVoice with vibrato modulates playback rate" {
        // Long enough for a 5 Hz LFO to actually swing: 4000 frames at 44100 is ~91 ms, so the
        // vibrato covers ~0.45 of a cycle. The original 100-frame block was 2.3 ms, over which a
        // 5 Hz LFO barely leaves zero — the test could not have seen its own subject.
        fun render(vibrato: Voice.Vibrato): AudioBuffer {
            val sample = TestSamples.sine(size = 8000)
            val voice = createVoice(
                endFrame = 4000.0,
                gateEndFrame = 4000.0,
                blockFrames = 4000,
            signal = SampleIgnitor(
                pcm = sample.pcm,
                rate = 1.0,
                playhead = 0.0,
                loopStart = -1.0,
                loopEnd = -1.0,
                isLooping = false,
                stopFrame = Double.MAX_VALUE,
                sampleRate = 48000,
            ),
                freqHz = 440.0,
                vibrato = vibrato,
            )
            val ctx = createContext(blockFrames = 4000)
            voice.render(ctx)

            return ctx.voiceBuffer
        }

        val dry = render(Voice.Vibrato(0.0, 0.0))
        val wet = render(Voice.Vibrato(rate = 5.0, semitones = 0.25))

        // Positive control first: without it, two silent buffers would also "differ by nothing" and
        // a broken fixture would read as a passing dry render.
        dry.any { it != 0.0 } shouldBe true

        // Vibrato bends the playback rate, so the wet render must drift away from the dry one.
        (0 until 4000).any { kotlin.math.abs(wet[it] - dry[it]) > 1e-9 } shouldBe true
    }

    "SampleVoice with FM modulates playback rate" {
        fun render(fm: Voice.Fm?): AudioBuffer {
            val sample = TestSamples.sine(size = 8000)
            val voice = createVoice(
                endFrame = 4000.0,
                gateEndFrame = 4000.0,
                blockFrames = 4000,
            signal = SampleIgnitor(
                pcm = sample.pcm,
                rate = 1.0,
                playhead = 0.0,
                loopStart = -1.0,
                loopEnd = -1.0,
                isLooping = false,
                stopFrame = Double.MAX_VALUE,
                sampleRate = 48000,
            ),
                freqHz = 440.0,
                fm = fm,
            )
            val ctx = createContext(blockFrames = 4000)
            voice.render(ctx)

            return ctx.voiceBuffer
        }

        val dry = render(null)
        val wet = render(Voice.Fm(ratio = 2.0, depth = 50.0, envelope = Voice.Envelope(0.0, 0.0, 1.0, 0.0)))

        dry.any { it != 0.0 } shouldBe true
        (0 until 4000).any { kotlin.math.abs(wet[it] - dry[it]) > 1e-9 } shouldBe true
    }

    // RENAMED 2026-08-31. The old name was "SampleVoice getBaseFrequency returns sample base pitch"
    // and there is no `getBaseFrequency` anywhere in the codebase — the test was named after a
    // symbol that does not exist, so nothing could have guarded it. What its comment described
    // ("base frequency is used for FM calculation") IS real and observable, so the test now guards
    // that instead of being deleted.
    "SampleVoice freqHz shapes the FM modulation" {
        fun render(freqHz: Double): AudioBuffer {
            val sample = TestSamples.sine(size = 8000)
            val voice = createVoice(
                endFrame = 4000.0,
                gateEndFrame = 4000.0,
                blockFrames = 4000,
            signal = SampleIgnitor(
                pcm = sample.pcm,
                rate = 1.0,
                playhead = 0.0,
                loopStart = -1.0,
                loopEnd = -1.0,
                isLooping = false,
                stopFrame = Double.MAX_VALUE,
                sampleRate = 48000,
            ),
                freqHz = freqHz,
                fm = Voice.Fm(ratio = 1.0, depth = 50.0, envelope = Voice.Envelope(0.0, 0.0, 1.0, 0.0)),
            )
            val ctx = createContext(blockFrames = 4000)
            voice.render(ctx)

            return ctx.voiceBuffer
        }

        // FmRenderer uses freqHz TWICE: `modFreq = freqHz * ratio` sets the modulator's speed, and
        // `fmMult = 1 + modSignal / freqHz` normalises its depth. At a fixed ratio, two different
        // pitches must therefore produce two different modulations.
        //
        // ⚠️ This row deliberately does NOT claim to pin the modulator FREQUENCY specifically.
        // Mutation Md (`modFreq = freqHz * ratio` -> `modFreq = ratio`) SURVIVES it, because role 2
        // still separates the two renders on its own. Nothing in the suite currently distinguishes
        // the two roles; see audit finding F6's notes. The earlier name for this row claimed the
        // narrower guard and would have been the same kind of overstatement this file is fixing.
        val low = render(440.0)
        val high = render(880.0)

        low.any { it != 0.0 } shouldBe true
        (0 until 4000).any { kotlin.math.abs(high[it] - low[it]) > 1e-9 } shouldBe true
    }

    "SampleVoice with envelope modulates sample output" {
        val sample = TestSamples.constant(size = 200, value = 1.0) // Longer sample

        val voice = createVoice(
            signal = SampleIgnitor(
                pcm = sample.pcm,
                rate = 1.0,
                playhead = 0.0,
                loopStart = -1.0,
                loopEnd = -1.0,
                isLooping = false,
                stopFrame = Double.MAX_VALUE,
                sampleRate = 48000,
            ),
            freqHz = 440.0,
            envelope = Voice.Envelope(
                attackFrames = 100.0,
                decayFrames = 0.0,
                sustainLevel = 1.0,
                releaseFrames = 0.0,
                attackCurve = AdsrCurve.Linear,
                decayCurve = AdsrCurve.Linear,
                releaseCurve = AdsrCurve.Linear,
            ),
        )

        val ctx = createContext(blockFrames = 100)
        voice.render(ctx)

        // Envelope should modulate sample amplitude. VCA gain is de-clicked, so the
        // linear attack ramp lags slightly; verify it rises from ~0 monotonically
        // (exact shape: EnvelopeShapeTest).
        ctx.voiceBuffer[0] shouldBe (0.0 plusOrMinus 0.02)  // Start of attack
        (ctx.voiceBuffer[50] > ctx.voiceBuffer[0]) shouldBe true
        (ctx.voiceBuffer[99] > ctx.voiceBuffer[50]) shouldBe true
        (ctx.voiceBuffer[99] > 0.6) shouldBe true
    }

    "SampleVoice handles sample end boundary" {
        val sample = TestSamples.ramp(size = 50)

        val voice = createVoice(
            signal = SampleIgnitor(
                pcm = sample.pcm,
                rate = 1.0,
                playhead = 45.0, // Near end
                loopStart = -1.0,
                loopEnd = -1.0,
                isLooping = false,
                stopFrame = Double.MAX_VALUE,
                sampleRate = 48000,
            ),
            freqHz = 440.0,
        )

        val ctx = createContext(blockFrames = 10)
        voice.render(ctx)

        // First 5 samples should have audio
        (ctx.voiceBuffer[2] > 0.0) shouldBe true

        // After sample ends, should be silent
        ctx.voiceBuffer[7] shouldBe 0.0
    }

    "SampleVoice with negative playhead is handled" {
        val sample = TestSamples.ramp(size = 100)

        val voice = createVoice(
            signal = SampleIgnitor(
                pcm = sample.pcm,
                rate = 1.0,
                playhead = -10.0, // Negative playhead
                loopStart = -1.0,
                loopEnd = -1.0,
                isLooping = false,
                stopFrame = Double.MAX_VALUE,
                sampleRate = 48000,
            ),
            freqHz = 440.0,
        )

        val ctx = createContext(blockFrames = 20)
        voice.render(ctx)

        // Negative playhead samples should be 0
        ctx.voiceBuffer[0] shouldBe 0.0
        ctx.voiceBuffer[9] shouldBe 0.0

        // After playhead reaches 0, should have audio
        (ctx.voiceBuffer[15] >= 0.0) shouldBe true
    }

    "SampleVoice preserves playhead across renders" {
        val sample = TestSamples.ramp(size = 200)

        val voice = createVoice(
            signal = SampleIgnitor(
                pcm = sample.pcm,
                rate = 1.0,
                playhead = 0.0,
                loopStart = -1.0,
                loopEnd = -1.0,
                isLooping = false,
                stopFrame = Double.MAX_VALUE,
                sampleRate = 48000,
            ),
            freqHz = 440.0,
        )

        // First render: frames 0-100
        val ctx1 = createContext(blockStart = 0.0, blockFrames = 100)
        voice.render(ctx1)
        val firstValue = ctx1.voiceBuffer[99]

        // Second render: frames 100-200
        val ctx2 = createContext(blockStart = 100.0, blockFrames = 100)
        voice.render(ctx2)
        val secondValue = ctx2.voiceBuffer[0]

        // Second render should continue where first left off
        (secondValue > firstValue) shouldBe true
    }

    "SampleVoice with all modulations renders correctly" {
        val sample = TestSamples.sine(size = 100)

        val voice = createVoice(
            signal = SampleIgnitor(
                pcm = sample.pcm,
                rate = 1.0,
                playhead = 0.0,
                loopStart = 0.0,
                loopEnd = 50.0,
                isLooping = true,
                stopFrame = Double.MAX_VALUE,
                sampleRate = 48000,
            ),
            freqHz = 440.0,
            vibrato = Voice.Vibrato(rate = 5.0, semitones = 0.25),
            accelerate = Voice.Accelerate(semitones = 1.0),
            fm = Voice.Fm(ratio = 2.0, depth = 50.0, envelope = Voice.Envelope(0.0, 0.0, 1.0, 0.0)),
            envelope = Voice.Envelope(100.0, 0.0, 1.0, 0.0),
        )

        val ctx = createContext()
        voice.render(ctx)

        // Stacking vibrato + accelerate + FM + a looping sample is the combination most likely to
        // produce a non-finite playhead, and a NaN here would propagate into the cylinder and kill
        // the orbit silently. "Renders successfully" is now a claim with teeth: audible, and finite.
        ctx.voiceBuffer.any { it != 0.0 } shouldBe true
        ctx.voiceBuffer.all { it == it && kotlin.math.abs(it) <= 1.0e6 } shouldBe true
    }
})
