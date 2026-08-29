/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import kotlin.math.abs
import kotlin.random.Random

/**
 * C3 guard, IGNITOR path (docs/plans/filter-unification.md): envelope depth is SEMITONES
 * in the svf kernel too — `cutoff = base * 2^(depth/12 * env)`. The strip-pipeline path has
 * its own rows in `FilterEnvSemitoneSpec`; both exist because the law is applied in two
 * independent places (the `drivePerAnalog` lesson).
 *
 * Method: with attack 0 / sustain 1 the envelope is exactly 1.0, so an env-modulated filter
 * must produce the SAME output as a static filter at `base * 2^(depth/12)`.
 */
class IgnitorFilterEnvSemitoneSpec : StringSpec({

    val sr = 48000
    val blockFrames = 128
    val blocks = 24

    fun ctx() = IgniteContext(
        sampleRate = sr,
        voiceDurationFrames = blocks * blockFrames * 2,
        gateEndFrame = blocks * blockFrames * 2,
        releaseFrames = 0,
        scratchBuffers = ScratchBuffers(blockFrames = blockFrames),
        voiceElapsedFrames = 0,
    )

    val noise = DoubleArray(blocks * blockFrames).also {
        val rng = Random(4242)
        for (i in it.indices) it[i] = rng.nextDouble() * 2.0 - 1.0
    }

    fun noiseSource(): Ignitor = object : Ignitor {
        var pos = 0
        override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
            for (i in ctx.offset until ctx.offset + ctx.length) {
                buffer[i] = noise[pos++]
            }
        }
    }

    fun render(chain: Ignitor): DoubleArray {
        val c = ctx()
        c.offset = 0
        c.length = blockFrames
        val buf = AudioBuffer(blockFrames)
        val out = DoubleArray(blocks * blockFrames)
        repeat(blocks) { blk ->
            chain.generate(buf, 220.0, c)
            buf.copyInto(out, blk * blockFrames, 0, blockFrames)
            c.voiceElapsedFrames += blockFrames
        }
        // vacuous-pass tripwire: a mis-set context renders silence, and two silent
        // renders would "match" any formula
        var peak = 0.0
        for (v in out) if (abs(v) > peak) peak = abs(v)
        peak shouldBeGreaterThan 1e-6
        return out
    }

    fun envEqualsStatic(depth: Double, expectedMul: Double, base: Double = 800.0) {
        val env = FilterEnvDef(depth = depth, attackSec = 0.0, decaySec = 0.0, sustainLevel = 1.0, releaseSec = 0.0)
        val modded = render(noiseSource().lowpass(base, 0.707, env))
        val static = render(noiseSource().lowpass(base * expectedMul, 0.707))
        var maxDiff = 0.0
        for (i in modded.indices) {
            val d = abs(modded[i] - static[i])
            if (d > maxDiff) maxDiff = d
        }
        maxDiff shouldBeLessThan 1e-12
    }

    "ignitor path: +12 semitones equals a static filter at 2x base" {
        envEqualsStatic(+12.0, 2.0)
    }

    "ignitor path: -12 semitones equals a static filter at 0.5x base" {
        envEqualsStatic(-12.0, 0.5)
    }

    "ignitor path: depth 0 equals the unmodulated filter" {
        envEqualsStatic(0.0, 1.0)
    }

    "ignitor path: +7 semitones equals a static filter at 2^(7/12)x (fractional exponent)" {
        envEqualsStatic(+7.0, 1.4983070768766815)
    }

    "clamp saturation: a deep positive depth on a high base drives HIGHPASS silent" {
        // base 15000 * 2^(48/12) = 240 kHz, clamped to Nyquist-1: a highpass at Nyquist
        // passes (near) nothing. The row pins that the semitone law goes THROUGH the
        // shared clamp rather than around it.
        val env = FilterEnvDef(depth = +48.0, attackSec = 0.0, decaySec = 0.0, sustainLevel = 1.0, releaseSec = 0.0)
        val out = render(noiseSource().highpass(15000.0, 0.707, env))
        var peakTail = 0.0
        for (i in out.size / 2 until out.size) {
            if (abs(out[i]) > peakTail) peakTail = abs(out[i])
        }
        peakTail shouldBeLessThan 0.02

        // control: the SAME filter without the envelope passes plenty
        val ref = render(noiseSource().highpass(15000.0, 0.707))
        var peakRef = 0.0
        for (i in ref.size / 2 until ref.size) {
            if (abs(ref[i]) > peakRef) peakRef = abs(ref[i])
        }
        peakRef shouldBeGreaterThan 0.1
    }
})
