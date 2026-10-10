/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.ignitor.IgniteContext
import io.peekandpoke.klang.audio_be.ignitor.Ignitor
import io.peekandpoke.klang.audio_be.ignitor.Ignitors
import io.peekandpoke.klang.audio_be.ignitor.ParamIgnitor
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.ignitor.fmModIgnitor
import io.peekandpoke.klang.audio_be.ignitor.vibratoModIgnitor
import io.peekandpoke.klang.audio_be.utils.TWO_PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** This file's one seeded stream: every run draws the same, and successive builds still draw
 *  differently (as they did from the process-wide stream these calls used before). */
private val testRandom = Random(0x5EED)

/**
 * The FM modulator and the vibrato LFOs evaluate the polynomial sine, whose fold is exact only a
 * quarter period past `[0, 2π)`: each of them now wraps its phase per sample, and takes the
 * full wrap when one increment is a whole period or more (a rate or a modulator past the sample
 * rate, raw-Motor, either sign). Before, the FM modulator wrapped at block end only, and the
 * vibrato LFOs never wrapped a negative rate at all, which the library sine tolerated.
 *
 * The FM rows render the Ignitor `fm` node over a sine modulator at `analog` 0, the shape `classic()`'s FM stage
 * places for sprudel's `fm` since pitch pipeline step 4 (they rendered the voice strip's `FmRenderer` until then).
 */
class ModulatorPhaseWrapSpec : StringSpec({

    val sampleRate = 48000
    val blockFrames = 128
    val freqHz = 440.0

    /** Renders [blocks] blocks of the pitch mod [mod] into one array of ratios, the gate far past the render. */
    fun renderMod(mod: Ignitor, blocks: Int): DoubleArray {
        val ctx = IgniteContext(
            sampleRate = sampleRate, voiceDurationFrames = 500_000, gateEndFrame = 500_000,
            scratchBuffers = ScratchBuffers(blockFrames),
            random = testRandom,
        )
        val buf = AudioBuffer(blockFrames)
        val out = DoubleArray(blocks * blockFrames)

        repeat(blocks) { b ->
            ctx.updateOffsetAndLength(offset = 0, length = blockFrames)
            ctx.voiceElapsedFrames = b * blockFrames
            mod.generate(buf, freqHz, ctx)

            for (i in 0 until blockFrames) {
                out[b * blockFrames + i] = buf[i]
            }
        }

        return out
    }

    /** The fm node as `classic()` places it: a sine modulator at `analog` 0. */
    fun fm(ratio: Double, depth: Double, decay: Double = 0.0, sustain: Double = 1.0): Ignitor = fmModIgnitor(
        modulator = Ignitors.sine(analog = ParamIgnitor("analog", 0.0)),
        ratio = ParamIgnitor("ratio", ratio),
        depth = ParamIgnitor("depth", depth),
        decay = ParamIgnitor("decay", decay),
        sustain = ParamIgnitor("sustain", sustain),
    )

    /**
     * Every sample finite and inside `[lo, hi]`, and the run actually MODULATES: a modulator
     * that silently stopped (a zero depth, a sine stuck at 0) would sit at 1.0, inside any bound.
     * [spans] asks for both ends of the bound to be reached (an LFO over whole cycles), otherwise
     * only that the run is not constant (an aliasing rate). One scan, the clue built on failure.
     */
    fun assertModulatesWithin(out: DoubleArray, lo: Double, hi: Double, spans: Boolean, clue: String) {
        var bad = -1

        for (i in out.indices) {
            val v = out[i]

            if (!(v >= lo && v <= hi)) { // also catches NaN
                bad = i
                break
            }
        }

        withClue("$clue: sample $bad = ${if (bad >= 0) out[bad] else 0.0} outside [$lo, $hi]") { bad shouldBe -1 }

        val min = out.min()
        val max = out.max()

        if (spans) {
            withClue("$clue: reaches the top of its bound, max $max") { (max > hi - 1e-6) shouldBe true }
            withClue("$clue: reaches the bottom of its bound, min $min") { (min < lo + 1e-6) shouldBe true }
        } else {
            withClue("$clue: not a constant, spread ${max - min}") { (max - min > 0.1 * (hi - lo)) shouldBe true }
        }
    }

    "the FM modulator matches a library-sine accumulator across many blocks" {
        // A bright ratio (2 : 1 at 440 Hz gives 0.115 rad per sample, 14.7 rad per block, well
        // past the fold): without a per-sample wrap the polynomial would leave its fold inside
        // the first block. The reference is the FM law with kotlin.math.sin.
        //
        // The envelope decays from 1 to a sustain of 0.4 over the first 40 blocks, so the row also pins that the
        // envelope's level scales the depth PER SAMPLE (the strip held it per block until pitch pipeline step 4,
        // ledger E11), on the exponential curve every modulation envelope has unwritten (decision D3).
        val ratio = 2.0
        val depth = 200.0
        val decayFrames = 40.0 * blockFrames
        val sustain = 0.4
        val blocks = 200
        val out = renderMod(fm(ratio = ratio, depth = depth, decay = decayFrames / sampleRate, sustain = sustain), blocks)
        val modInc = TWO_PI * freqHz * ratio / sampleRate

        fun expShape(x: Double): Double = (exp(3.0 * x) - 1.0) / (exp(3.0) - 1.0)

        var phase = 0.0
        var worst = 0.0

        for (p in out.indices) {
            val level = if (p < decayFrames) sustain + (1.0 - sustain) * expShape(1.0 - p / decayFrames) else sustain
            val expected = 1.0 + sin(phase) * depth * level / freqHz

            worst = maxOf(worst, abs(out[p] - expected))
            phase += modInc
        }

        withClue("worst deviation from the library-sine FM ratio") { worst shouldBeLessThan 1e-9 }
    }

    "an FM modulator past the sample rate stays a bounded multiplier, in either sign" {
        for (ratio in listOf(200.0, -200.0)) {
            val depth = 200.0
            val out = renderMod(fm(ratio = ratio, depth = depth), 100)
            val swing = depth / freqHz

            assertModulatesWithin(out = out, lo = 1.0 - swing - 1e-9, hi = 1.0 + swing + 1e-9, spans = false, clue = "fm ratio $ratio")
        }
    }

    "the vibrato ignitor stays within its depth for a negative rate and for a rate past the sample rate" {
        for (rate in listOf(-5.0, 2.37 * sampleRate, -2.37 * sampleRate)) {
            val mod = vibratoModIgnitor(rate = rate, semitones = 1.0)
            val ctx = IgniteContext(
                sampleRate = sampleRate, voiceDurationFrames = 500_000, gateEndFrame = 500_000,
                scratchBuffers = ScratchBuffers(blockFrames),
                random = testRandom,
            )
            val buf = AudioBuffer(blockFrames)
            val blocks = if (rate == -5.0) 1500 else 100
            val out = DoubleArray(blocks * blockFrames)

            repeat(blocks) { b ->
                ctx.updateOffsetAndLength(offset = 0, length = blockFrames)
                ctx.voiceElapsedFrames = b * blockFrames
                mod.generate(buf, freqHz, ctx)

                for (i in 0 until blockFrames) {
                    out[b * blockFrames + i] = buf[i]
                }
            }

            val bound = 2.0.pow(1.0 / 12.0)

            assertModulatesWithin(out = out, lo = 1.0 / bound - 1e-9, hi = bound + 1e-9, spans = rate == -5.0, clue = "vibrato ignitor rate $rate")
        }
    }
})
