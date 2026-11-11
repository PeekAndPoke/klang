/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_be.AudioBackendContext
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.utils.rampStep
import kotlin.math.round
import kotlin.random.Random

/**
 * Ignitor that plays back pre-recorded PCM audio samples.
 *
 * Wraps sample playback (interpolation, looping, stop frame) as a composable
 * [Ignitor] source, enabling samples to flow through the same signal chain
 * as synthesized oscillators.
 *
 * When [analog] > 0, applies the [AnalogDrift] lane (Ornstein-Uhlenbeck) to the playhead advance rate,
 * simulating wow & flutter from vinyl records and tape machines.
 *
 * Pitch is controlled via [rate] and
 * [IgniteContext.phaseMod], consistent with how noise generators ignore freqHz.
 *
 * **The read** (KLANG-00430, audit findings L3-05 and L3-14): a four-point cubic Lagrange interpolation ([lagrange4])
 * between the frames around the playhead, where it was two-point linear: 11 to 47 dB less imaging on a transposed
 * tone above 1 kHz, and less of the linear read's treble droop. The PCM is silence before frame 0 and after its last
 * frame, so a one-shot plays its last frame and then falls to 0 within one frame, up to [stopFrame] (the read used to
 * write 0 from the last frame on, so the last frame was never heard). While looping, the frames past the loop's end
 * are the loop's start again, and once the playhead has wrapped, the frames before the loop's start are its end
 * ([edgeFrame]): a loop over the whole sample no longer writes a zero at every pass, and a loop's seam is read
 * across the wrap. Before the first wrap the frames before the loop's start are the sample's own (its attack).
 */
class SampleIgnitor(
    private val pcm: DoubleArray,
    private val rate: Double,
    private var playhead: Double,
    private val loopStart: Double,
    private val loopEnd: Double,
    private val isLooping: Boolean,
    private val stopFrame: Double,
    analog: Double = 0.0,
    sampleRate: Int,
    /** The engine's block size: the drift lane steps once per block (see [AnalogDrift]). */
    blockFrames: Int = AudioBackendContext.RENDER_QUANTUM_FRAMES,
    /** The voice's random stream (seeded-voice-rng) — wow/flutter drift seeds from it. */
    rng: Random,
) : Ignitor {

    private val drift = AnalogDrift(analog, analogDriftStepRate(sampleRate = sampleRate, blockFrames = blockFrames), rng)
    private val loopLength = if (isLooping && loopEnd > loopStart) loopEnd - loopStart else 0.0

    /** Whether the playhead wraps: a loop of some length. */
    private val looping: Boolean = loopLength > 0.0

    /**
     * The loop in whole frames for the read's neighbours ([edgeFrame]): its first frame, its length (at least 1) and the
     * first frame past it. A frame at or past [loopAfter] is moved back by [loopFrames], and once the playhead has
     * wrapped, a frame before [loopFirst] forward by it. Rounded, so a loop on whole frames (a soundfont loop, `loop(1)`
     * over a whole sample) maps exactly whatever the rounding of its seconds, and a loop whose ends are not on frames (a
     * `begin` or `end` ratio) has its seam read up to half a frame off; the playhead itself wraps by the exact length.
     */
    private val loopFirst: Int = if (looping) round(loopStart).toInt() else 0
    private val loopFrames: Int = if (looping) maxOf(1, round(loopLength).toInt()) else 1
    private val loopAfter: Int = loopFirst + loopFrames

    /**
     * The frames from [directStart] up to before this one are read straight from [pcm]; the rest go through
     * [edgeFrame]. The sample's end, or the loop's end when that comes first.
     */
    private val directEnd: Int = if (looping) minOf(pcm.size, loopAfter) else pcm.size

    /** The first frame read straight from [pcm]: 0 (frame -1 is silence) until the playhead first wraps, then the loop's first frame. */
    private var directStart: Int = 0

    /** Whether the playhead has wrapped once: from then on a frame before the loop's start is the loop's end ([edgeFrame]). */
    private var wrapped: Boolean = false

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        val phaseMod = ctx.phaseMod

        // `* 1.0`: a seed from a field must pass an arithmetic op on V8, or the loop can box the playhead (audio/ref/performance.md).
        var ph = playhead * 1.0

        if (drift.active) {
            // Analog drift path: wow & flutter on playback rate, one lane step per block, ramped
            drift.beginBlock()

            // `* 1.0`: a seed from a call or a field must pass an arithmetic op on V8, or `m += dm` boxes (audio/ref/performance.md).
            var m = drift.blockStart * 1.0
            val dm = rampStep(from = m, to = drift.blockEnd, frames = ctx.length)

            for (i in 0 until ctx.length) {
                val idxOut = ctx.offset + i

                if (looping && ph >= loopEnd) {
                    ph = wrapIntoLoop(ph)
                }

                buffer[idxOut] = if (ph < 0.0 || ph >= stopFrame) 0.0 else readAt(ph)

                ph += if (phaseMod != null) rate * phaseMod[idxOut] * m else rate * m
                m += dm
            }
        } else {
            // Clean digital path
            for (i in 0 until ctx.length) {
                val idxOut = ctx.offset + i

                if (looping && ph >= loopEnd) {
                    ph = wrapIntoLoop(ph)
                }

                buffer[idxOut] = if (ph < 0.0 || ph >= stopFrame) 0.0 else readAt(ph)

                ph += if (phaseMod != null) rate * phaseMod[idxOut] else rate
            }
        }

        playhead = ph
    }

    /** [ph] at or past the loop's end, wrapped into `[loopStart, loopEnd)`; the first wrap lets the read cross the seam backwards too. */
    private fun wrapIntoLoop(ph: Double): Double {
        val offset = (ph - loopStart) % loopLength

        if (!wrapped) {
            wrapped = true
            directStart = loopFirst
        }

        return loopStart + if (offset < 0.0) offset + loopLength else offset
    }

    /**
     * The cubic read at a playhead [ph] at or above 0: the frames `base - 1` to `base + 2` around it, straight from [pcm]
     * where all four are in `[directStart, directEnd)`, else through [edgeFrame]. `base` is at most `Int.MAX_VALUE` (a
     * one-shot whose playhead ran far past its end), so the range check subtracts instead of adding.
     */
    @Suppress("NOTHING_TO_INLINE")
    private inline fun readAt(ph: Double): Double {
        val base = ph.toInt()
        val t = ph - base

        return if (base > directStart && base < directEnd - 2) {
            lagrange4(t = t, xm1 = pcm[base - 1], x0 = pcm[base], x1 = pcm[base + 1], x2 = pcm[base + 2])
        } else {
            lagrange4(t = t, xm1 = edgeFrame(base - 1), x0 = edgeFrame(base), x1 = edgeFrame(base + 1), x2 = edgeFrame(base + 2))
        }
    }

    /**
     * Frame [k] as the read sees it: while looping, a frame at or past the loop's end ([loopAfter]) is moved back by
     * whole [loopFrames] into the loop (the loop's start again), and after the first wrap a frame before the loop's start
     * ([loopFirst]) is moved forward into it (the loop's end); a frame outside the PCM after that is silence. Each move is
     * one integer division, whatever the distance: a playhead that a negative phaseMod carries far below a soundfont
     * loop's start reads frames thousands of loops away (a step per loop length would be hundreds of steps per frame on
     * the audio thread). The divisions are of non-negative numbers only, so they mean the same on the JVM and on JS.
     *
     * The read's lowest frame is `base - 1` with `base` at or above 0, so a frame below -1 is an index that overflowed (a
     * one-shot playhead near `Int.MAX_VALUE`, `base + 1` or `base + 2`): it reads as silence before any arithmetic, which
     * keeps the backward distance below from overflowing.
     */
    private fun edgeFrame(k: Int): Double {
        if (k < -1) {
            return 0.0
        }

        var j = k

        if (looping) {
            if (j >= loopAfter) {
                // (j - loopAfter) >= 0, and the loop count times loopFrames stays below j: no overflow.
                j -= ((j - loopAfter) / loopFrames + 1) * loopFrames
            } else if (wrapped && j < loopFirst) {
                // (loopFirst - 1 - j) >= 0 and at most loopFirst: j >= -1 here.
                j += ((loopFirst - 1 - j) / loopFrames + 1) * loopFrames
            }
        }

        return if (j >= 0 && j < pcm.size) pcm[j] else 0.0
    }
}

/**
 * The four-point, third-order Lagrange interpolation at [t] in `[0, 1)` between [x0] (at 0) and [x1] (at 1), with
 * their outer neighbours [xm1] (at -1) and [x2] (at 2): the cubic through the four points, written as a polynomial in
 * [t] (Horner form; the weights are `-t (t - 1) (t - 2) / 6`, `(t + 1) (t - 1) (t - 2) / 2`, `-(t + 1) t (t - 2) / 2`,
 * `(t + 1) t (t - 1) / 6`). At `t = 0` it is [x0] exactly. Shared by the sample read ([SampleIgnitor]) and the
 * string's fractional delay ([KarplusString]); Lagrange interpolation is the classic maximally flat fractional delay
 * (Laakso, Välimäki, Karjalainen and Laine, "Splitting the Unit Delay", IEEE Signal Processing Magazine, 1996).
 */
@Suppress("NOTHING_TO_INLINE")
internal inline fun lagrange4(t: Double, xm1: Double, x0: Double, x1: Double, x2: Double): Double {
    val c1 = x1 - (1.0 / 3.0) * xm1 - 0.5 * x0 - (1.0 / 6.0) * x2
    val c2 = 0.5 * (xm1 + x1) - x0
    val c3 = (1.0 / 6.0) * (x2 - xm1) + 0.5 * (x0 - x1)

    return ((c3 * t + c2) * t + c1) * t + x0
}
