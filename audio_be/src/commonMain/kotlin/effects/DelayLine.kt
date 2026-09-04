/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.effects

import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.ShapingFuncs
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.nanGuard
import io.peekandpoke.klang.audio_be.effects.DelayLine.Companion.MIN_DELAY_SECONDS
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.min

/**
 * A modulated stereo delay line with linear-interpolation fractional read and
 * feedback control.
 *
 * **How it works:**
 * Stores incoming audio samples in a circular ring buffer and plays them back
 * after a configurable duration. Stereo operation = two independent mono lines
 * (no cross-feedback / ping-pong).
 *
 * **Key features:**
 * - **Fractional read** via linear interpolation between adjacent ring-buffer
 *   samples. Gives sub-sample tap accuracy (kills the integer-quantisation
 *   zipper) and accurate tuning for pitch-based effects (flanger, chorus, comb
 *   filter, Karplus-Strong). NOTE: the tap position is resolved once per
 *   [process] call — a `delayTimeSeconds` change moves the read tap
 *   instantaneously at the next block boundary, with no crossfade/ramp.
 *   Interpolation direction: `alpha=0` reads `s1` (newer
 *   sample, at `pos - delayInt`); `alpha=1` reads `s2` (one sample older).
 * - **Short-delay support** down to [MIN_DELAY_SECONDS] (~0.1 ms), enabling
 *   flanger/comb regimes. Note: linear interpolation introduces a mild HF
 *   roll-off (~−3 dB at Nyquist) that's only audible in short-delay use cases.
 * - **Feedback** path with smooth saturation safety: rather than a hard clip,
 *   the recirculated sample is passed through [ShapingFuncs.softCap] to
 *   prevent runaway accumulation when `feedback ≥ 1.0` while keeping the
 *   character musical (smooth tanh-style knee). The ring buffer is therefore
 *   always bounded to ±1, which together with the non-finite-rejecting
 *   [delayTimeSeconds] / [feedback] setters means a finite input always
 *   produces a finite output — no per-sample scrub needed. Callers are
 *   expected to feed finite samples (consistent with the engine's raw style).
 *
 * **Output semantic (caller contract):** [process] writes the **wet (delayed)
 * signal additively** into `output`. The dry signal is NOT mixed in by this
 * class. Callers using DelayLine as a send/return effect should clear or
 * pre-fill `output` before calling. See `KatalystDelayEffect` for the canonical
 * usage pattern.
 *
 * **Performance:**
 * - Block processing: the inner sample loop has no ring-buffer wrap check.
 *   `process()` splits the block at wrap boundaries and feeds the inner loop
 *   contiguous chunks instead (a loop, so any `length` wraps correctly).
 * - Per-block constants (`delayInt`, `alpha`, etc.) are computed once per
 *   `process()`, not per channel/chunk.
 */
class DelayLine(
    /** The ring. Rented from the resource warehouse in production; the secondary constructor allocates one. */
    ring: StereoBuffer,
    val sampleRate: Int,
    delayTimeSeconds: Double = 0.5,
    feedback: Double = 0.0,
) {
    /** Allocates its own ring of [maxDelaySeconds]. Master chains and specs; cylinders rent instead. */
    constructor(
        maxDelaySeconds: Double,
        sampleRate: Int,
        delayTimeSeconds: Double = 0.5,
        feedback: Double = 0.0,
    ) : this(StereoBuffer((maxDelaySeconds * sampleRate).toInt()), sampleRate, delayTimeSeconds, feedback)

    /** The ring itself, so an owner can give it back to the warehouse. */
    internal val ring: StereoBuffer = ring

    private val bufferSize = ring.left.size
    private val buffer = ring

    /**
     * How many frames this ring holds, raw. The longest delay it can SERVE is two frames less
     * (`currentDelaySamples` clamps to `size - 2` for the interpolation neighbour); callers that
     * size a ring add their margin on the request side (`KatalystDelayEffect.framesFor`), never
     * subtract it here.
     */
    val capacityFrames: Int get() = bufferSize

    /**
     * Seeds this ring with [from]'s history so that **"n samples ago" reads the same sample here as
     * it did there** — which is exactly what makes the seam of a grow inaudible (resource warehouse,
     * step 2c). A delay that is ringing when a longer time arrives keeps ringing.
     *
     * [from]'s oldest sample sits at its `writePos` (the slot about to be overwritten) and its newest
     * one just before; they are copied oldest-first into `[0, n)` here and the cursor is left at `n`,
     * so `writePos - d` lands on the same sample for every `d` the old ring could serve. Anything
     * older than the old ring's span was never recorded and reads as the zeros this ring came with —
     * the honest answer for a tap that now reaches further back than the delay ever recorded.
     *
     * Copies the NEWEST `min(from.size, size)` samples, so it is correct in both directions: a
     * larger target takes all of [from]'s history; a smaller one keeps the most recent slice and
     * drops the oldest, which is the only truncation that leaves every reachable tap aligned.
     * (Review round 1: a first cut started at [from]'s oldest sample, which for a smaller target
     * discarded the NEWEST and shifted every tap — wrong, not truncated. Shrinks never happen
     * today, so the function is now simply right rather than "right for grows".)
     */
    internal fun adoptHistory(from: DelayLine) {
        val n = minOf(from.bufferSize, bufferSize)
        val src = from.buffer
        // The newest n samples end just before from.writePos; their oldest one is n back from it.
        var start = from.writePos - n
        if (start < 0) {
            start += from.bufferSize
        }

        for (i in 0 until n) {
            var s = start + i
            if (s >= from.bufferSize) {
                s -= from.bufferSize
            }
            buffer.left[i] = src.left[s]
            buffer.right[i] = src.right[s]
        }

        writePos = if (n == bufferSize) 0 else n
    }
    private var writePos = 0

    /** Test seam for the migration rows: where the next sample will be written. */
    internal val writePosForTest: Int get() = writePos

    /** Delay time in seconds. Setter silently ignores non-finite values. */
    var delayTimeSeconds: Double = delayTimeSeconds
        set(value) {
            if (!value.isFinite()) return
            field = value
        }

    /** Feedback amount. Setter silently ignores non-finite values. Values ≥ 1.0
     *  self-oscillate, bounded by [feedbackCap] in the feedback path. */
    var feedback: Double = feedback
        set(value) {
            if (!value.isFinite()) return
            field = value
        }

    /**
     * Ceiling the feedback path saturates toward ([ShapingFuncs.softCapTo]).
     *
     * The engine is raw: any [feedback] is allowed, including ≥ 1.0, which self-oscillates. This
     * decides *how loud* that runaway settles rather than whether it is permitted — and keeps the
     * ring bounded so a finite input can never produce a non-finite output. Default 1.0 is
     * bit-identical to the previous fixed behaviour. Setter silently ignores non-finite values.
     */
    var feedbackCap: Double = 1.0
        set(value) {
            if (!value.isFinite()) return
            field = value
        }

    /**
     * Returns true if the internal ring buffer still contains audio above
     * [threshold]. Used by cylinder-cleanup logic to detect tails that should
     * keep the cylinder alive.
     *
     * Cost: O(bufferSize × 2) — scans both channels linearly. Not intended for
     * per-block use; called from cleanup polling.
     *
     * Conservative under stable feedback (≤ 1.0): if every sample in the buffer
     * is below threshold, no future feedback iteration can bring the output
     * back above threshold, so a `false` return is safe.
     */
    fun hasTail(threshold: Double = 0.00001): Boolean {
        // Test/diagnostic only since the closed-form tail (`TailCountdown`): no production caller,
        // and none should return — this is O(ring) with no ceiling on the ring.
        for (i in 0 until bufferSize) {
            if (abs(buffer.left[i]) > threshold || abs(buffer.right[i]) > threshold) {
                return true
            }
        }
        return false
    }

    /**
     * The loudest sample the drain's tap can still REACH: the `delayInt + 2` samples immediately
     * behind the write head (one delay period plus the interpolation neighbor). Everything older
     * is overwritten by the head before the tap arrives, so it can never be emitted — scanning
     * the whole ring (review round 4) both cost most of a block budget on the audio thread at
     * every off-transition and over-estimated the drain by whatever was loud up to
     * [bufferSize] samples ago. O(delayInt), transition use only. Always finite:
     * [ShapingFuncs.softCap] bounds every store.
     */
    fun tapWindowPeakAbs(): Double {
        val window = currentDelaySamples().toInt() + 2
        var peak = 0.0
        var pos = writePos - 1

        for (i in 0 until window) {
            if (pos < 0) {
                pos += bufferSize
            }

            val l = abs(buffer.left[pos])
            val r = abs(buffer.right[pos])

            if (l > peak) {
                peak = l
            }

            if (r > peak) {
                peak = r
            }

            pos--
        }

        return peak
    }

    /**
     * How many samples of zero-input processing until everything the tap can reach is provably
     * below [threshold] — the closed form of the [hasTail] argument, run forward in time, from
     * the MEASURED content [peak] (usually [tapWindowPeakAbs] at the off-transition; review
     * round 3 replaced the static worst-case cap bound with this, so a barely-used delay drains
     * in proportion to what it actually holds instead of paying the saturated-ring worst case).
     *
     * With silent input each delay period multiplies the content ceiling by |[feedback]| (the
     * interpolated tap is a convex combination, [ShapingFuncs.softCap] never expands). After
     * `k = ceil(ln(threshold / peak) / ln(|fb|))` periods the tap window is below [threshold]
     * and (for |fb| < 1, the same argument [hasTail] makes) can never come back up. One slack
     * period is added on top — it also covers the tap's extra interpolation sample.
     *
     * A [peak] at or below [threshold] returns 0.0: already silent, nothing to drain — this
     * outranks the self-oscillation sentinel on purpose (an EMPTY self-osc ring must not drain
     * forever). Otherwise `|feedback| >= 1.0` returns [Double.POSITIVE_INFINITY]: the line
     * self-oscillates by design (raw engine) and never drains on its own.
     */
    fun drainSamplesUntilSilent(peak: Double, threshold: Double = 0.00001): Double {
        if (peak <= threshold) {
            return 0.0
        }

        val fbAbs = abs(feedback)

        if (fbAbs >= 1.0) {
            return Double.POSITIVE_INFINITY
        }

        val delaySamples = currentDelaySamples()

        if (fbAbs <= 0.0) {
            // One period overwrites the entire tap window with exact zeros; keep the slack period.
            return 2.0 * delaySamples
        }

        val periods = ceil(ln(threshold / peak) / ln(fbAbs))

        return (periods + 1.0) * delaySamples
    }

    /** The effective tap distance in samples — [delayTimeSeconds] under the same coercion [process] applies. */
    private fun currentDelaySamples(): Double =
        (delayTimeSeconds * sampleRate).coerceIn(MIN_DELAY_SECONDS * sampleRate, bufferSize - 2.0)

    /**
     * The delay actually being rendered, in seconds — [delayTimeSeconds] after the physical bound of
     * this ring. They differ only when the requested time exceeds the ring, which since the resource
     * warehouse means "a longer ring was refused (out of memory) and this one is doing its best".
     * That gap is what the frontend should show as "delay time reduced".
     */
    val effectiveDelaySeconds: Double get() = currentDelaySamples() / sampleRate

    /**
     * Clears the ring buffer and resets the write head so a reused delay line does not replay a previous
     * owner's tail. Parameter values (delay time / feedback) are preserved. Used by cylinder cleanup.
     */
    fun reset() {
        buffer.clear()
        writePos = 0
    }

    fun process(input: StereoBuffer, output: StereoBuffer, length: Int) {
        // Per-block constants — channel- and chunk-independent. Hoisted out of
        // the inner loop so they're computed once per process() call rather
        // than once per channel × chunk (4×).
        val delaySamples = currentDelaySamples()
        val delayInt = delaySamples.toInt()
        val alpha = delaySamples - delayInt
        val fb = feedback
        // Sanitised once per block, not per sample. `softCapTo`'s branches are loop-invariant here,
        // and this file's own PERF note records that a previously added per-sample check cost
        // ~+33% JVM / +30% JS at the rate this path runs.
        val rawCap = feedbackCap
        val cap = if (rawCap.isFinite() && rawCap > 0.0) rawCap else 1.0

        // Split the block at ring-buffer wrap boundaries so the inner loop has no
        // 'if (pos >= bufferSize)' check. A while loop rather than a single split:
        // block size is a tone parameter, so 'length > bufferSize' must wrap more
        // than once instead of writing past the ring (the smallest master ring is
        // only ~0.06 s).
        var done = 0
        var pos = writePos

        while (done < length) {
            val chunk = min(length - done, bufferSize - pos)

            processInternal(buffer.left, input.left, output.left, done, chunk, pos, delayInt, alpha, fb, cap)
            processInternal(buffer.right, input.right, output.right, done, chunk, pos, delayInt, alpha, fb, cap)

            done += chunk
            pos = (pos + chunk) % bufferSize
        }

        writePos = pos
    }

    private fun processInternal(
        buffer: AudioBuffer,
        input: AudioBuffer,
        output: AudioBuffer,
        offset: Int,
        length: Int,
        startWritePos: Int,
        delayInt: Int,
        alpha: Double,
        fb: Double,
        cap: Double,
    ) {
        var pos = startWritePos

        for (i in 0 until length) {
            val inputIndex = offset + i

            // --- 1. Fractional read: linear interpolation between two ring
            //         positions. `s1` is newer (at pos - delayInt), `s2` is one
            //         sample older. alpha=0 → s1, alpha=1 → s2.
            var readIndex1 = pos - delayInt
            if (readIndex1 < 0) {
                readIndex1 += bufferSize
            }
            var readIndex2 = readIndex1 - 1
            if (readIndex2 < 0) {
                readIndex2 += bufferSize
            }

            val s1 = buffer[readIndex1]
            val s2 = buffer[readIndex2]
            val delayedSignal = s1 + alpha * (s2 - s1)

            // --- 2. Feedback + smooth saturation around ±1 (instead of a hard
            //         clip — softer, less brittle on runaway feedback or hot inputs).
            //         softCap also keeps every ring-buffer store within [-1, 1], so
            //         given finite input + finite feedback (enforced by setters) the
            //         output stays finite without a per-sample isFinite() scrub.
            //         Previous revision (Round 7) did a per-sample isFinite check
            //         here; that cost ~+33% JVM / +30% JS at the rate this path
            //         runs (every delay sample × stereo). Removed 2026-05-22.
            val newSample = input[inputIndex] + (delayedSignal * fb)
            // cap is pre-sanitised (finite, > 0) so this reduces to the scaled softCap; at the
            // default 1.0 it is the exact pre-change `softCap(newSample)`.
            // NaN-guard on the ring store. `softCap` already sterilises +/-Inf (it saturates to
            // +/-1), but `softCap(NaN)` is NaN — both its branch tests are false — and this ring
            // RECIRCULATES, so a single NaN never scrolls out: the orbit's delay is dead for the
            // rest of its life. This is the delay's half of the master round's "no state may
            // latch" pass; `flushState` could not reach it because the ring is FIR-shaped state,
            // not an IIR carry.
            //
            // NOT the `isFinite` check that lived here until 2026-05-22 and was removed at a
            // measured +33% JVM / +30% JS: that cost was the non-inlined stdlib call, not the
            // test. `nanGuard` is one inline self-compare (`x != x`), and re-measured on a
            // purpose-added delay rung it is indistinguishable from zero (interleaved A/B).
            val stored = if (cap == 1.0) {
                ShapingFuncs.softCap(newSample)
            } else {
                cap * ShapingFuncs.softCap(newSample / cap)
            }
            buffer[pos] = stored.nanGuard()

            // --- 3. Wet output, additive. Caller owns the dry mix.
            output[inputIndex] = output[inputIndex] + delayedSignal

            pos++
        }
    }

    companion object {
        /**
         * Lower bound for [delayTimeSeconds] in seconds. ~0.1 ms — short enough
         * for flanger/comb regimes, long enough that linear interpolation
         * doesn't blow up at boundary conditions.
         */
        private const val MIN_DELAY_SECONDS: Double = 0.0001
    }
}
