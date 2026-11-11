/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.peekandpoke.klang.audio_be.AudioBuffer
import kotlin.math.abs
import kotlin.random.Random

/** This file's one seeded stream: every run draws the same, and successive builds still draw
 *  differently (as they did from the process-wide stream these calls used before). */
private val testRandom = Random(0x5EED)

/**
 * C3 guard, IGNITOR path (KLANG-00227 (filter unification)): envelope depth is SEMITONES
 * in the svf kernel: `cutoff = base * 2^(depth/12 * env)`. (The strip-pipeline path had its own
 * rows in `FilterEnvSemitoneSpec` until the voice strip retired, phase 3 step 9.)
 *
 * Method: with attack 0 / sustain 1 the envelope is exactly 1.0, so an env-modulated filter
 * must produce the SAME output as a static filter at `base * 2^(depth/12)`. +7 carries the law (a
 * fractional exponent), -12 the sign; the formula itself is `SvfCoeffSweepSpec`'s, and depth 0 as
 * the switch is `IgnitorFilterKnobsSpec`'s "env = 0 written EXPLICITLY" row.
 */
class IgnitorFilterEnvSemitoneSpec : StringSpec({

    val sr = 48000
    val blockFrames = 128
    val blocks = 24

    fun ctx() = IgniteContext(
        sampleRate = sr,
        voiceDurationFrames = blocks * blockFrames * 2,
        gateEndFrame = blocks * blockFrames * 2,
        scratchBuffers = ScratchBuffers(blockFrames = blockFrames),
        voiceElapsedFrames = 0,
        random = testRandom,
    )

    val noise = DoubleArray(blocks * blockFrames).also {
        val rng = Random(4242)
        for (i in it.indices) it[i] = rng.nextDouble() * 2.0 - 1.0
    }

    fun noiseSource(): Ignitor = object : Ignitor {
        var pos = 0
        override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
            for (i in ctx.offset until ctx.windowEnd) {
                buffer[i] = noise[pos++]
            }
        }
    }

    fun render(chain: Ignitor): DoubleArray {
        val c = ctx()
        c.updateOffsetAndLength(offset = 0, length = blockFrames)
        val buf = AudioBuffer(blockFrames)
        val out = DoubleArray(blocks * blockFrames)
        repeat(blocks) { blk ->
            chain.generate(buf, 220.0, c)
            buf.copyInto(destination = out, destinationOffset = blk * blockFrames, startIndex = 0, endIndex = blockFrames)
            c.voiceElapsedFrames += blockFrames
        }
        // vacuous-pass tripwire: a mis-set context renders silence, and two silent
        // renders would "match" any formula
        var peak = 0.0
        for (v in out) if (abs(v) > peak) peak = abs(v)
        peak shouldBeGreaterThan 1e-6
        return out
    }

    fun envEqualsStatic(depth: Double, expectedMul: Double, base: Double = 800.0, highpass: Boolean = false) {
        val env = FilterEnvDef(depth = depth, attack = 0.0, decay = 0.0, sustain = 1.0, release = 0.0)
        val modded = if (highpass) {
            render(noiseSource().highpass(cutoffHz = base, q = 0.707, env = env))
        } else {
            render(noiseSource().lowpass(cutoffHz = base, q = 0.707, env = env))
        }
        val static = if (highpass) {
            render(noiseSource().highpass(cutoffHz = base * expectedMul, q = 0.707))
        } else {
            render(noiseSource().lowpass(cutoffHz = base * expectedMul, q = 0.707))
        }
        var maxDiff = 0.0
        for (i in modded.indices) {
            val d = abs(modded[i] - static[i])
            if (d > maxDiff) maxDiff = d
        }
        maxDiff shouldBeLessThan 1e-12
    }

    "ignitor path: -12 semitones equals a static filter at 0.5x base" {
        envEqualsStatic(depth = -12.0, expectedMul = 0.5)
    }

    "ignitor path: +7 semitones equals a static filter at 2^(7/12)x (fractional exponent)" {
        envEqualsStatic(depth = +7.0, expectedMul = 1.4983070768766815)
    }

    "clamp saturation: a deep positive depth on a high base drives the HIGHPASS to the shared clamp" {
        // base 15000 * 2^(48/12) = 240 kHz, clamped to the shared cutoff clamp, 0.49 * fs since KLANG-00432 (audit
        // L4-10; Nyquist - 1 Hz before, where this row read the output as near silence; at 0.49 fs the highpass passes
        // the top 2 percent of the band). The row pins that the semitone law goes THROUGH the shared clamp rather
        // than around it: bit for bit the static highpass asked for a cutoff far past Nyquist, which the clamp holds.
        envEqualsStatic(depth = +48.0, expectedMul = 1e9 / 15000.0, base = 15000.0, highpass = true)

        // control: the SAME filter without the envelope passes plenty, the clamped one far less
        val env = FilterEnvDef(depth = +48.0, attack = 0.0, decay = 0.0, sustain = 1.0, release = 0.0)
        val out = render(noiseSource().highpass(cutoffHz = 15000.0, q = 0.707, env = env))
        val ref = render(noiseSource().highpass(cutoffHz = 15000.0, q = 0.707))
        var peakTail = 0.0
        var peakRef = 0.0
        for (i in ref.size / 2 until ref.size) {
            if (abs(out[i]) > peakTail) peakTail = abs(out[i])
            if (abs(ref[i]) > peakRef) peakRef = abs(ref[i])
        }
        peakRef shouldBeGreaterThan 0.1
        peakTail shouldBeLessThan 0.5 * peakRef
    }
})
