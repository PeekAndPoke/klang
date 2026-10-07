/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.filters

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.ignitor.IgniteContext
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.ignitor.toExciter
import io.peekandpoke.klang.audio_bridge.FILTER_MAX_PASSES
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.highpass
import io.peekandpoke.klang.audio_bridge.lowpass
import kotlin.math.log10
import kotlin.math.sqrt
import kotlin.random.Random

/** This file's one seeded stream: every run draws the same, and successive builds still draw
 *  differently (as they did from the process-wide stream these calls used before). */
private val testRandom = Random(0x5EED)

/**
 * C5 guards: `passes` cascades the SVF stage with the STAGGERED Butterworth q ladder
 * (maintainer decision: the cascade stays -3 dB AT fc — `lpf(800, passes = 2)` still means
 * 800; a plain per-stage-q cascade would be -6 dB at fc with the knee at ~0.64·fc).
 */
class PassesCascadeSpec : StringSpec({

    val sr = 48000.0
    val frames = 48000

    fun db(x: Double): Double = 20.0 * log10(x)

    /** Steady-state RMS of a [freq] sine rendered THROUGH the ignitor door (no optimizer). */
    fun rmsIgnitor(dsl: IgnitorDsl, freq: Double): Double {
        val chain = dsl.toExciter(random = testRandom)
        val block = 128
        val ctx = IgniteContext(
            sampleRate = sr.toInt(),
            voiceDurationFrames = frames,
            gateEndFrame = frames,
            scratchBuffers = ScratchBuffers(blockFrames = block),
            random = testRandom,
        )
        ctx.updateOffsetAndLength(offset = 0, length = block)
        val buf = AudioBuffer(block)
        var sum = 0.0
        var n = 0
        var t = 0
        while (t < frames / 4) {
            chain.generate(buf, freq, ctx)
            if (t >= frames / 8) {
                for (i in 0 until block) {
                    sum += buf[i] * buf[i]
                    n++
                }
            }
            ctx.voiceElapsedFrames += block
            t += block
        }
        return sqrt(sum / n)
    }

    fun rmsIgnitorLp(passes: Int, freq: Double): Double =
        rmsIgnitor(IgnitorDsl.Sine().lowpass(freq = 1000.0, q = 0.707, passes = passes), freq)

    fun rmsIgnitorHp(passes: Int, freq: Double): Double =
        rmsIgnitor(IgnitorDsl.Sine().highpass(freq = 1000.0, q = 0.707, passes = passes), freq)

    "the q ladder: passes = 1 is the user q VERBATIM; passes = 2 is the 4th-order Butterworth pair" {
        butterworthQLadder(1, 1.2).toList() shouldBe listOf(1.2)
        val two = butterworthQLadder(2, 0.707)
        two.size shouldBe 2
        // classic 4th-order Butterworth stage Qs, scaled by 0.707/0.7071 ≈ 1
        two[0] shouldBe (0.5412 plusOrMinus 0.001)
        two[1] shouldBe (1.3065 plusOrMinus 0.002)
        // 0 and negative coerce to a single stage
        butterworthQLadder(0, 0.9).toList() shouldBe listOf(0.9)
        butterworthQLadder(-3, 0.9).toList() shouldBe listOf(0.9)
        // ...and the resource ceiling holds: a live-typed `lpf(passes = 1e9)` must not allocate a
        // billion stages inside a note-on. FILTER_MAX_PASSES is the ONE bound (coercePasses).
        butterworthQLadder(1_000_000, 0.707).size shouldBe FILTER_MAX_PASSES
    }

    // The absolute C5 laws, on the tree's door (phase 3 step 9, commit a2: the strip's cascade class that held them
    // retired). The dry sine through the same harness is the reference. The absolute rows also pin what the
    // relative ones (passes = 2 against passes = 1) used to: a dropped cascade fold reads -12 where -24 is due.

    "STAGGERED q on the ignitor door: passes = 1 and the passes = 2 cascade are both ~-3 dB AT the cutoff, lowpass and highpass (the C5 decision)" {
        // The discriminating row for the C5 decision: at fc a staggered pair multiplies to
        // 0.5412 * 1.3065 = 0.707 (-3.01 dB), a plain q-per-stage pair to 0.707^2 = 0.5 (-6.02 dB).
        // Dropping `.scaledBy(rel[k])` in IgnitorDslRuntime reads -6 here; the lowpass and the
        // highpass arm each have their own cascade fold, so both are pinned.
        val dry = rmsIgnitor(IgnitorDsl.Sine(), 1000.0)

        db(rmsIgnitorLp(1, 1000.0) / dry) shouldBe (-3.0 plusOrMinus 0.5)
        db(rmsIgnitorLp(2, 1000.0) / dry) shouldBe (-3.0 plusOrMinus 0.5)
        db(rmsIgnitorHp(1, 1000.0) / dry) shouldBe (-3.0 plusOrMinus 0.5)
        db(rmsIgnitorHp(2, 1000.0) / dry) shouldBe (-3.0 plusOrMinus 0.5)
    }

    "slope on the ignitor door: passes = 2 measures ~-24 dB one octave above fc, passes = 1 ~-12 (lowpass)" {
        val dry = rmsIgnitor(IgnitorDsl.Sine(), 2000.0)
        db(rmsIgnitorLp(1, 2000.0) / dry) shouldBe (-12.3 plusOrMinus 1.0)
        db(rmsIgnitorLp(2, 2000.0) / dry) shouldBe (-24.1 plusOrMinus 1.5)
    }

    "slope mirror on the ignitor door: highpass passes = 2 measures ~-24 dB one octave BELOW fc" {
        val dry = rmsIgnitor(IgnitorDsl.Sine(), 500.0)
        db(rmsIgnitorHp(1, 500.0) / dry) shouldBe (-12.3 plusOrMinus 1.0)
        db(rmsIgnitorHp(2, 500.0) / dry) shouldBe (-24.1 plusOrMinus 1.5)
    }
})
