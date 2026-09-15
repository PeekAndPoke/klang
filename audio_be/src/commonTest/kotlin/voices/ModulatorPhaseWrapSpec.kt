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
import io.peekandpoke.klang.audio_be.TWO_PI
import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.ignitor.IgniteContext
import io.peekandpoke.klang.audio_be.ignitor.Ignitors
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.ignitor.vibratoModIgnitor
import io.peekandpoke.klang.audio_be.voices.strip.BlockContext
import io.peekandpoke.klang.audio_be.voices.strip.calculateControlRateEnvelope
import io.peekandpoke.klang.audio_be.voices.strip.pitch.FmRenderer
import io.peekandpoke.klang.audio_be.voices.strip.pitch.VibratoRenderer
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin

/**
 * The FM modulator and the vibrato LFOs evaluate the polynomial sine, whose fold is exact only a
 * quarter period past `[0, 2π)`: each of them now wraps its phase per sample, and takes the
 * full wrap when one increment is a whole period or more (a rate or a modulator past the sample
 * rate, raw-Motor, either sign). Before, the FM modulator wrapped at block end only, and the
 * vibrato LFOs never wrapped a negative rate at all, which the library sine tolerated.
 */
class ModulatorPhaseWrapSpec : StringSpec({

    val sampleRate = 48000
    val blockFrames = 128
    val freqHz = 440.0

    fun stripCtx(): BlockContext = BlockContext(
        audioBuffer = AudioBuffer(blockFrames),
        freqModBuffer = DoubleArray(blockFrames),
        scratchBuffers = ScratchBuffers(blockFrames),
        sampleRate = sampleRate,
        startFrame = 0.0,
        endFrame = 1_000_000.0,
        gateEndFrame = 500_000.0,
        freqHz = freqHz,
        signal = Ignitors.silence(),
        signalCtx = IgniteContext(
            sampleRate = sampleRate, voiceDurationFrames = 500_000, gateEndFrame = 500_000, releaseFrames = 100,
            scratchBuffers = ScratchBuffers(blockFrames),
        ),
        cylinders = Cylinders(blockFrames = blockFrames, sampleRate = sampleRate),
    )

    /**
     * Renders [blocks] blocks of the strip renderer into one array of pitch multipliers. With
     * [multiplyIn] the buffer arrives already written (all ones), so the renderer takes its
     * multiply-in loop instead of its write loop.
     */
    fun renderStrip(blocks: Int, multiplyIn: Boolean = false, render: (BlockContext) -> Unit): DoubleArray {
        val ctx = stripCtx()
        val out = DoubleArray(blocks * blockFrames)

        repeat(blocks) { b ->
            ctx.blockStart = (b * blockFrames).toDouble()
            ctx.updateOffsetAndLength(0, blockFrames)
            ctx.freqModBufferWritten = multiplyIn
            ctx.freqModBuffer.fill(1.0)
            render(ctx)

            for (i in 0 until blockFrames) {
                out[b * blockFrames + i] = ctx.freqModBuffer[i]
            }
        }

        return out
    }

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
        // the first block. The reference is the old formula with kotlin.math.sin.
        val fm = Voice.Fm(ratio = 2.0, depth = 200.0, envelope = Voice.Envelope(0.0, 0.0, 1.0, 0.0))
        val renderer = FmRenderer(fm, freqHz, sampleRate, startFrame = 0.0)
        val blocks = 200
        val out = renderStrip(blocks) { renderer.render(it) }
        val modInc = TWO_PI * freqHz * fm.ratio / sampleRate
        var phase = 0.0
        var worst = 0.0

        repeat(blocks) { b ->
            val level = calculateControlRateEnvelope(fm.envelope, (b * blockFrames).toDouble(), 0.0, 500_000.0)

            for (i in 0 until blockFrames) {
                val expected = 1.0 + sin(phase) * fm.depth * level / freqHz

                worst = maxOf(worst, abs(out[b * blockFrames + i] - expected))
                phase += modInc
            }
        }

        withClue("worst deviation from the library-sine FM multiplier") { worst shouldBeLessThan 1e-9 }
    }

    "an FM modulator past the sample rate stays a bounded multiplier, in either sign" {
        for (ratio in listOf(200.0, -200.0)) {
            val fm = Voice.Fm(ratio = ratio, depth = 200.0, envelope = Voice.Envelope(0.0, 0.0, 1.0, 0.0))
            val renderer = FmRenderer(fm, freqHz, sampleRate, startFrame = 0.0)
            val out = renderStrip(100) { renderer.render(it) }
            val swing = fm.depth / freqHz

            assertModulatesWithin(out, 1.0 - swing - 1e-9, 1.0 + swing + 1e-9, spans = false, clue = "fm ratio $ratio")
        }
    }

    "the strip vibrato stays within its depth for a negative rate and for a rate past the sample rate" {
        // 4 s at -5 Hz: the old block-end wrap only ever subtracted, so a negative rate walked
        // the phase to -125 rad, far outside the polynomial's fold.
        for (rate in listOf(-5.0, 2.37 * sampleRate, -2.37 * sampleRate)) {
            for (multiplyIn in listOf(false, true)) {
                val vibrato = Voice.Vibrato(rate = rate, semitones = 1.0)
                val renderer = VibratoRenderer(vibrato, sampleRate)
                val blocks = if (rate == -5.0) 1500 else 100
                val out = renderStrip(blocks, multiplyIn) { renderer.render(it) }
                val bound = 2.0.pow(vibrato.semitones / 12.0)

                assertModulatesWithin(
                    out, 1.0 / bound - 1e-9, bound + 1e-9, spans = rate == -5.0,
                    clue = "strip vibrato rate $rate, multiplyIn $multiplyIn",
                )
            }
        }
    }

    "the vibrato ignitor stays within its depth for a negative rate and for a rate past the sample rate" {
        for (rate in listOf(-5.0, 2.37 * sampleRate, -2.37 * sampleRate)) {
            val mod = vibratoModIgnitor(rate = rate, semitones = 1.0)
            val ctx = IgniteContext(
                sampleRate = sampleRate, voiceDurationFrames = 500_000, gateEndFrame = 500_000, releaseFrames = 0,
                scratchBuffers = ScratchBuffers(blockFrames),
            )
            val buf = AudioBuffer(blockFrames)
            val blocks = if (rate == -5.0) 1500 else 100
            val out = DoubleArray(blocks * blockFrames)

            repeat(blocks) { b ->
                ctx.updateOffsetAndLength(0, blockFrames)
                ctx.voiceElapsedFrames = b * blockFrames
                mod.generate(buf, freqHz, ctx)

                for (i in 0 until blockFrames) {
                    out[b * blockFrames + i] = buf[i]
                }
            }

            val bound = 2.0.pow(1.0 / 12.0)

            assertModulatesWithin(out, 1.0 / bound - 1e-9, bound + 1e-9, spans = rate == -5.0, clue = "vibrato ignitor rate $rate")
        }
    }
})
