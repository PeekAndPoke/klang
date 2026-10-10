/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.ignitor.toExciter
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.classic
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.createContext
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.createSynthVoice
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Sprudel's FM through a real voice: `classic()`'s FM stage, filled through the `fm.*` slots (pitch pipeline step 4;
 * the strip's `FmRenderer` and `Voice.Fm` retired), and when it is built. Its law (the ratio per sample against the
 * modulator's phase, the depth and the envelope level, the release tail) is the oracle in `ClassicFmSpec`, the node's
 * phase wrap in `ModulatorPhaseWrapSpec`, the envelope in `EnvelopeLawSpec`.
 */
class FmSynthesisTest : StringSpec({

    val bf = 512

    /** Compute RMS of a float array slice. */
    fun rms(buf: AudioBuffer, from: Int = 0, to: Int = buf.size): Double {
        var sum = 0.0
        for (i in from until to) {
            sum += buf[i] * buf[i]
        }
        return sqrt(sum / (to - from))
    }

    /** Compute RMS of the element-wise difference of two buffers. */
    fun diffRms(a: AudioBuffer, b: AudioBuffer, from: Int = 0, to: Int = a.size): Double {
        var sum = 0.0
        for (i in from until to) {
            val d = a[i] - b[i]
            sum += d * d
        }
        return sqrt(sum / (to - from))
    }

    "FM at depth 0 is the unmodulated carrier, and a real depth is not" {
        // Audit F12. The three-way is what has teeth: unwritten and depth 0 must agree (either one applying
        // modulation breaks it), and a real depth must NOT agree with them (which is what makes the first half mean
        // something). A ratio alone switches nothing on: `fm.depth` is the stage's switch.
        fun render(bag: Map<String, Double>): AudioBuffer {
            val instrument = IgnitorDsl.Sine(analog = IgnitorDsl.Constant(0.0)).classic()
            val voice = createSynthVoice(blockFrames = bf, freqHz = 440.0, signal = instrument.toExciter(ignitorParams = bag, random = Random(1)))
            val ctx = createContext(blockFrames = bf)
            voice.render(ctx)
            return ctx.voiceBuffer
        }

        val none = render(emptyMap())
        val zeroDepth = render(mapOf("fm.ratio" to 2.0, "fm.depth" to 0.0))
        val ratioOnly = render(mapOf("fm.ratio" to 2.0))
        val realDepth = render(mapOf("fm.ratio" to 2.0, "fm.depth" to 100.0))
        val negativeDepth = render(mapOf("fm.ratio" to 2.0, "fm.depth" to -100.0))

        diffRms(a = zeroDepth, b = none) shouldBeLessThan 1e-6
        diffRms(a = ratioOnly, b = none) shouldBeLessThan 1e-6
        diffRms(a = realDepth, b = none) shouldBeGreaterThan 1e-3
        // A negative depth is a raw value like any other: the stage is built for any finite depth but 0.
        diffRms(a = negativeDepth, b = none) shouldBeGreaterThan 1e-3
        // and the carrier is actually sounding, so the comparisons are not all-silence
        rms(none) shouldBeGreaterThan 0.0
    }
})
