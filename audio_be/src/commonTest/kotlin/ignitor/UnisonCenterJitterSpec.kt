/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Serves the scripted values from [nextDouble] in order; an int draw (a drift seed) reads 0 and takes none of them. */
private class ScriptedRandom(private val values: DoubleArray) : Random() {
    private var at = 0

    override fun nextBits(bitCount: Int): Int = 0

    override fun nextDouble(): Double = values[at++]
}

/**
 * **At an even voice count both middle voices take the centre's jitter scale** (KLANG-00430, audit finding L3-12, the
 * maintainer's option 2 of 2026-11-11). An even stack has no voice at the centre: its two middle voices carry the pitch
 * together (about +-1 cent at 8 voices), and only the lower one got the reduced jitter.
 *
 * Measurement: a supersine at `spread` 0 and `analog` 0 sums its voices at one frequency, so its output is one sine of
 * amplitude `|sum g_n e^(i 2 pi phi_n)|`, the gains `g_n` and the start phases `phi_n` (the trick of `PhasePoolSpec`).
 * At 375 Hz and 48 kHz a period is 128 samples, so the RMS over whole blocks is that amplitude over sqrt(2). The voice
 * stream is scripted: the start phases first, one per voice in index order, then one jitter draw per voice (the stack's
 * draw order with the phase pool off). The oracle writes the gain law out: the triangular base profile, the jitter
 * `1 + (draw - 0.5) * 2 * gainJitter * scale` with the centre's scale on the middle voice or voices, the sum to 1.
 */
class UnisonCenterJitterSpec : StringSpec({

    val sampleRate = 48000
    val blockFrames = 128
    val freqHz = 375.0
    val sideAtten = 0.1
    val gainJitter = 0.15
    val centerScale = 0.4

    fun phases(v: Int): DoubleArray = DoubleArray(v) { n -> (n * 0.37) % 1.0 }

    // Spread over the range and never 0.5 (a draw of 0.5 is no jitter, whatever the scale): the golden ratio's fractions.
    fun draws(v: Int): DoubleArray = DoubleArray(v) { n -> 0.1 + 0.8 * ((n * 0.6180339887) % 1.0) }

    fun measured(v: Int): Double {
        val sig = Ignitors.superSine(
            voices = ParamIgnitor("voices", v.toDouble()),
            detune = ParamIgnitor("spread", 0.0),
            analog = ParamIgnitor("analog", 0.0),
            rng = ScriptedRandom(phases(v) + draws(v)),
            sideAtten = sideAtten, gainJitter = gainJitter, centerJitterScale = centerScale,
            phasePool = 0.0,
        )
        val buffer = AudioBuffer(blockFrames)
        val ctx = IgniteContext(
            sampleRate = sampleRate,
            voiceDurationFrames = sampleRate,
            gateEndFrame = sampleRate,
            scratchBuffers = ScratchBuffers(blockFrames),
            random = Random(1),
        )
        var sumSq = 0.0

        for (b in 0 until 4) {
            ctx.updateOffsetAndLength(offset = 0, length = blockFrames)
            ctx.voiceElapsedFrames = b * blockFrames
            sig.generate(buffer, freqHz, ctx)

            for (i in 0 until blockFrames) {
                sumSq += buffer[i] * buffer[i]
            }
        }

        return sqrt(sumSq / (4 * blockFrames)) * sqrt(2.0)
    }

    fun oracle(v: Int): Double {
        val c = (v - 1) * 0.5
        val p = phases(v)
        val d = draws(v)
        val g = DoubleArray(v) { n -> if (v == 1) 1.0 else 1.0 - sideAtten * abs(n - c) / c }
        val middle = setOf((v - 1) / 2, v / 2)

        for (n in 0 until v) {
            val scale = if (n in middle) centerScale else 1.0

            g[n] *= 1.0 + (d[n] - 0.5) * 2.0 * gainJitter * scale
        }

        val sum = g.sum()
        var re = 0.0
        var im = 0.0

        for (n in 0 until v) {
            re += g[n] / sum * cos(2.0 * PI * p[n])
            im += g[n] / sum * sin(2.0 * PI * p[n])
        }

        return sqrt(re * re + im * im)
    }

    "both middle voices of an even stack take the centre's jitter scale; an odd stack's one centre voice does (L3-12)" {
        for (v in 2..9) {
            withClue("$v voices") {
                measured(v) shouldBe (oracle(v) plusOrMinus 1e-6)
            }
        }
    }
})
