/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_be.utils.finiteOrZero
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow

/**
 * The release of a tail that will not end on its own: its output dies away EXPONENTIALLY, per
 * sample, from exactly 1, 60 dB every [RT60_SECONDS], and stops at [FLOOR_DB], so it sounds like a
 * tail that was always going to end rather than like an edit (maintainer's option A, 2026-09-28).
 * The one home of that law, for its two users:
 *  - [ChainSwap], a chain swapped away whose drain outlived `ChainSwap.MAX_DRAIN_SECONDS` (phase 3
 *    step 12 decision (i));
 *  - [PlaybackEngine], a STOPPED playback still ringing with a tail that can never end when its
 *    hold ends (decision (j): after a stop, no hard cut, ever; finite tails ring out).
 *
 * The gain is `g(k) = exp(-k / tau)`, k the frames since [restart], computed as the recurrence
 * `g(k + 1) = g(k) * exp(-1 / tau)` from exactly 1: one multiply per sample instead of one `exp`
 * (KLANG-00290 (engine follow ups) item 25, maintainer 2026-11-06). Over the whole release (4.5 s,
 * about 216,000 steps at 48 kHz) the product stays within about 1e-11 of the closed form, relative (under
 * 1e-10 at 96 kHz, `TailReleaseLawSpec`),
 * far under anything audible at a gain that ends at -90 dB. g(0) is exactly 1, so the release meets
 * the full-weight audio before it without a step, and it moves per sample, so there is no per-block
 * zipper. From the first sample under the floor it is 0, and [addReleased] says so: the owner stops
 * the source at the end of that block.
 *
 * One instance per owner, created with it; its position OUTLIVES the owner's states (the
 * [Crossfade] precedent), and [restart] is the one initialiser of that position. Not thread-safe;
 * nothing allocates after construction.
 */
internal class TailRelease(sampleRate: Int, private val blockFrames: Int) {

    /** The time constant in frames: 60 dB (a factor 1000) every [RT60_SECONDS]. */
    private val tauFrames: Double = RT60_SECONDS * sampleRate.coerceAtLeast(1) / ln(1000.0)

    /** [FLOOR_DB] as a gain. */
    private val floorGain: Double = 10.0.pow(FLOOR_DB / 20.0)

    /** The step of the recurrence: the gain one frame later is the gain times this. */
    private val ratio: Double = exp(-1.0 / tauFrames)

    /** The gain of the next sample: exactly 1 at [restart], then times [ratio] per frame, 0 once under the floor. */
    private var gain: Double = 1.0

    /** Starts the release over at gain exactly 1. */
    fun restart() {
        gain = 1.0
    }

    /**
     * The gain the next sample would be added under: where a quicker fade that takes over from this
     * release starts (`ChainSwap`'s cut), so the two meet without a step. 0 once under the floor.
     * Called once per takeover, never per block.
     */
    fun nextGain(): Double {
        if (gain < floorGain) {
            return 0.0
        }

        return gain
    }

    /**
     * Adds one block of [source] to [target] under the release gain and advances it. Returns true
     * when the gain has fallen under the floor within this block (it is 0 from there on), which is
     * where the owner may retire the source: nothing it plays is heard any more.
     */
    fun addReleased(target: StereoBuffer, source: StereoBuffer): Boolean {
        val targetLeft = target.left
        val targetRight = target.right
        val sourceLeft = source.left
        val sourceRight = source.right
        val step = ratio
        val floor = floorGain
        val frames = blockFrames
        var next = gain
        var silent = false

        for (i in 0 until frames) {
            var g = next

            if (g < floor) {
                // 0 from here on, and it stays 0: no subnormal multiplies after the floor
                g = 0.0
                silent = true
            }

            next = g * step

            // Sterilised tap: at g = 0 an infinite sample would make `Inf * 0.0` NaN.
            val left = sourceLeft[i]
            val right = sourceRight[i]

            targetLeft[i] = targetLeft[i] + left.finiteOrZero() * g
            targetRight[i] = targetRight[i] + right.finiteOrZero() * g
        }

        gain = next

        return silent
    }

    companion object {
        /**
         * How fast the release dies away: 60 dB every this many seconds. 3 s is the slope of a long
         * room (about a size 8 reverb), so an echo still audible when the release begins fades like
         * a tail that was always going to end, not like an edit; shorter starts to sound like a
         * fade-out, longer only keeps a parked request or a stopped engine waiting.
         */
        const val RT60_SECONDS: Double = 3.0

        /**
         * Where the release stops: the gain under -90 dB, reached [RT60_SECONDS] x 1.5 = 4.5 s
         * after it began. Not -60 dB: a feedback-1 loop sits near the delay's soft cap (1.0,
         * 0 dBFS), so -60 dB of it would end on a -60 dBFS step, a tick a quiet passage can show.
         * At -90 dB the step is at most about one 16-bit step (3e-5) for a full-scale ring (at the
         * delay's default cap, with nothing after it in the chain), and -108 dBFS for an echo
         * already 18 dB down.
         */
        const val FLOOR_DB: Double = -90.0
    }
}
