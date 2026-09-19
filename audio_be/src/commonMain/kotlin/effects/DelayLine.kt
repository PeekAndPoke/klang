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
import io.peekandpoke.klang.audio_bridge.constants.DELAY_CAP
import io.peekandpoke.klang.audio_bridge.constants.KNOB_GLIDE_SECONDS
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

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
 *   filter, Karplus-Strong). The tap position is resolved once per [process]
 *   call. Interpolation direction: `alpha=0` reads `s1` (newer sample, at
 *   `pos - delayInt`); `alpha=1` reads `s2` (one sample older).
 * - **A `time` change CROSSFADES from the old tap to the new one** over
 *   [KNOB_GLIDE_SECONDS], a linear per-sample ramp that feeds the output and
 *   the feedback path alike (`docs/plans/knob-glide.md`: the time never glides,
 *   a glide would bend the echoes' pitch). Measured in Katalyst step 5b-2: the
 *   jump it replaces sat at -20 to -29 dB above 8 kHz on a band-limited pad,
 *   the class of a hard cut; with the crossfade it sits at the steady-state
 *   floor (-91 to -94 dB). A change that arrives while a crossfade runs is
 *   PARKED: the running fade finishes, and the next block fades to whatever the
 *   time is then (the latest wins, so two taps are always enough). The first
 *   block after construction or [reset] SNAPS to the time, because an empty or
 *   fresh ring has nothing to be continuous with. A line whose time never
 *   changes (the master's) never takes the crossfade path.
 * - **A [feedback] change ramps per sample** across the block that sees it,
 *   from the value the block before ended on (see [process]); a settled
 *   line adds exactly 0.0 per sample and runs as before.
 * - **Short-delay support** down to [MIN_DELAY_SECONDS] (~0.1 ms), enabling
 *   flanger/comb regimes. Note: linear interpolation introduces a mild HF
 *   roll-off (~−3 dB at Nyquist) that's only audible in short-delay use cases.
 * - **Feedback** path with smooth saturation safety: rather than a hard clip,
 *   the recirculated sample is passed through [ShapingFuncs.softCap] to
 *   prevent runaway accumulation when `feedback ≥ 1.0` while keeping the
 *   character musical (smooth tanh-style knee). The ring buffer is therefore
 *   always bounded to ±1, which together with the non-finite-rejecting
 *   [time] / [feedback] setters means a finite input always
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
    time: Double = 0.5,
    feedback: Double = 0.0,
) {
    /** Allocates its own ring of [maxDelaySeconds]. Master chains and specs; cylinders rent instead. */
    constructor(
        maxDelaySeconds: Double,
        sampleRate: Int,
        time: Double = 0.5,
        feedback: Double = 0.0,
    ) : this(StereoBuffer((maxDelaySeconds * sampleRate).toInt()), sampleRate, time, feedback)

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

        // The tap in force and a crossfade in flight move with the history: a grow happens because
        // a LONGER time arrived, and that change must crossfade on the new ring like any other.
        tapSamples = from.tapSamples
        feedbackInForce = from.feedbackInForce
        snapTap = from.snapTap
        fading = from.fading
        fadeFromSamples = from.fadeFromSamples
        fadeDone = from.fadeDone
    }
    private var writePos = 0

    /** Samples one tap crossfade spans ([KNOB_GLIDE_SECONDS]), at least one. */
    private val fadeTotal: Int = round(KNOB_GLIDE_SECONDS * sampleRate).toInt().coerceAtLeast(1)

    private val invFadeTotal: Double = 1.0 / fadeTotal

    /** The tap distance in force, in samples: what the last block read, and where a crossfade goes. */
    private var tapSamples: Double = 0.0

    /** True until a block has read the tap: the first block after construction or [reset] snaps. */
    private var snapTap: Boolean = true

    /** True while the output crossfades from [fadeFromSamples] to [tapSamples]. */
    private var fading: Boolean = false

    /** The tap a running crossfade is leaving. */
    private var fadeFromSamples: Double = 0.0

    /** Samples of the running crossfade already rendered. */
    private var fadeDone: Int = 0

    /** The feedback the last block ended on: where the next block's per-sample ramp starts. */
    private var feedbackInForce: Double = 0.0

    /** Test seam: true while a tap crossfade runs. */
    internal val isCrossfading: Boolean get() = fading

    /** Test seam for the migration rows: where the next sample will be written. */
    internal val writePosForTest: Int get() = writePos

    /** Delay time in seconds. Setter silently ignores non-finite values. */
    var time: Double = time
        set(value) {
            if (!value.isFinite()) return
            field = value
        }

    /** Feedback amount. Setter silently ignores non-finite values. Values ≥ 1.0
     *  self-oscillate, bounded by [cap] in the feedback path. */
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
    var cap: Double = DELAY_CAP
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
        // Test/diagnostic only since the content-ceiling tail (`TailCeiling`): no production caller,
        // and none should return — this is O(ring) with no ceiling on the ring.
        for (i in 0 until bufferSize) {
            if (abs(buffer.left[i]) > threshold || abs(buffer.right[i]) > threshold) {
                return true
            }
        }
        return false
    }

    /**
     * The loudest sample the drain's tap can still REACH: the samples immediately behind the write
     * head, one reach ([reachSamples], the longest tap still sounding) plus the interpolation
     * neighbour. Everything older
     * is overwritten by the head before the tap arrives, so it can never be emitted — scanning
     * the whole ring (review round 4) both cost most of a block budget on the audio thread at
     * every off-transition and over-estimated the drain by whatever was loud up to
     * [bufferSize] samples ago. O(delayInt), transition use only. Always finite:
     * [ShapingFuncs.softCap] bounds every store.
     */
    fun tapWindowPeakAbs(): Double {
        val window = reachSamples().toInt() + 2
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
     *
     * [feedback] is the line's own unless the caller knows better: an orbit delay whose feedback
     * still glides passes the larger magnitude of where it stands and where it goes
     * (`KatalystDelayEffect`).
     */
    fun drainSamplesUntilSilent(
        peak: Double,
        feedback: Double = this.feedback,
        threshold: Double = 0.00001,
    ): Double {
        if (peak <= threshold) {
            return 0.0
        }

        val fbAbs = abs(feedback)

        if (fbAbs >= 1.0) {
            return Double.POSITIVE_INFINITY
        }

        val delaySamples = reachSamples()

        if (fbAbs <= 0.0) {
            // One period overwrites the entire tap window with exact zeros; keep the slack period.
            return 2.0 * delaySamples
        }

        val periods = ceil(ln(threshold / peak) / ln(fbAbs))

        return (periods + 1.0) * delaySamples
    }

    /**
     * The window for [TailCeiling]: one recirculation (the effective tap distance) plus the
     * interpolation neighbour, so a read never reaches further back than the previous window.
     */
    val tailWindowSamples: Double get() = reachSamples() + 1.0

    /**
     * The ring's laps per [tailWindowSamples]: computed like the reverb's, `ceil(window / period)`,
     * with the SHORTEST tap still sounding as the period, because the window is the longest one
     * (see [reachSamples]): during a crossfade from a long tap to a short one the short tap comes
     * round many times inside the long window, and counting it once would let the ceiling
     * under-state. On a settled line it is `ceil((time + 1) / time)`, 2 for every reachable time.
     */
    val tailLapsPerWindow: Int
        get() = ceil(tailWindowSamples / shortestSamples()).toInt()

    /** The effective tap distance in samples — [time] under the same coercion [process] applies. */
    private fun currentDelaySamples(): Double =
        (time * sampleRate).coerceIn(MIN_DELAY_SECONDS * sampleRate, bufferSize - 2.0)

    /** The shortest tap that can sound within the next crossfade: [reachSamples]' counterpart. */
    private fun shortestSamples(): Double {
        val target = currentDelaySamples()

        if (snapTap) {
            return target
        }

        if (fading) {
            return min(target, min(tapSamples, fadeFromSamples))
        }

        return min(target, tapSamples)
    }

    /**
     * The furthest back any tap can read within the next crossfade: the time, and while a tap is
     * in force also that tap and a tap a crossfade is leaving. The tail bounds above take this
     * rather than the time, so a countdown or a ceiling taken mid-crossfade (or before a parked
     * change has started its fade) never under-reaches the tap that is still sounding. Equal to the
     * time whenever the line is settled.
     */
    private fun reachSamples(): Double {
        val target = currentDelaySamples()

        if (snapTap) {
            return target
        }

        if (fading) {
            return max(target, max(tapSamples, fadeFromSamples))
        }

        return max(target, tapSamples)
    }

    /**
     * The delay actually being rendered, in seconds — [time] after the physical bound of
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
        // An empty ring has nothing to crossfade from: the next block snaps to the time.
        snapTap = true
        fading = false
    }

    fun process(input: StereoBuffer, output: StereoBuffer, length: Int) {
        val target = currentDelaySamples()

        // The feedback in force RAMPS per sample across the block, from the value the last block
        // ended on to [feedback]: a gain on the recirculating audio that stepped once per block
        // would write a staircase into the ring that comes back every period (step 5b-2 review).
        // A settled line has a step of exactly 0.0, and `fb + 0.0` is `fb`, so it runs bit for bit
        // as before; the first block after construction or [reset] snaps, like the tap.
        val fbEnd = feedback
        val fbFrom = if (snapTap) fbEnd else feedbackInForce
        val fbStep = (fbEnd - fbFrom) / length

        feedbackInForce = fbEnd

        if (snapTap) {
            tapSamples = target
            snapTap = false
        } else if (!fading && target != tapSamples) {
            fadeFromSamples = tapSamples
            tapSamples = target
            fadeDone = 0
            fading = true
        }

        if (fading) {
            processCrossfade(input, output, length, fbEnd, fbStep)

            return
        }

        // Per-block constants — channel- and chunk-independent. Hoisted out of
        // the inner loop so they're computed once per process() call rather
        // than once per channel × chunk (4×).
        val delaySamples = tapSamples
        val delayInt = delaySamples.toInt()
        val alpha = delaySamples - delayInt
        val last = length - 1
        // Sanitised once per block, not per sample. `softCapTo`'s branches are loop-invariant here,
        // and this file's own PERF note records that a previously added per-sample check cost
        // ~+33% JVM / +30% JS at the rate this path runs.
        val rawCap = cap
        val safeCap = if (rawCap.isFinite() && rawCap > 0.0) rawCap else 1.0

        // Split the block at ring-buffer wrap boundaries so the inner loop has no
        // 'if (pos >= bufferSize)' check. A while loop rather than a single split:
        // block size is a tone parameter, so 'length > bufferSize' must wrap more
        // than once instead of writing past the ring (the smallest master ring is
        // only ~0.06 s).
        var done = 0
        var pos = writePos

        while (done < length) {
            val chunk = min(length - done, bufferSize - pos)

            processInternal(
                buffer.left, input.left, output.left, done, chunk, pos, delayInt, alpha, fbEnd, fbStep, last, safeCap,
            )
            processInternal(
                buffer.right, input.right, output.right, done, chunk, pos, delayInt, alpha, fbEnd, fbStep, last, safeCap,
            )

            done += chunk
            pos = (pos + chunk) % bufferSize
        }

        writePos = pos
    }

    /**
     * [process] while a tap crossfade runs: the block is read from BOTH taps and blended with the
     * crossfade's per-sample weight, which ends at exactly 1.0 on the fade's last sample and holds
     * there for the rest of the block, where `old * 0.0 + new * 1.0` is the single-tap read bit for
     * bit. Everything else (the feedback, the soft cap, the NaN guard, the wrap split) is the plain
     * path's.
     */
    private fun processCrossfade(input: StereoBuffer, output: StereoBuffer, length: Int, fbEnd: Double, fbStep: Double) {
        val fromSamples = fadeFromSamples
        val fromInt = fromSamples.toInt()
        val fromAlpha = fromSamples - fromInt
        val toSamples = tapSamples
        val toInt = toSamples.toInt()
        val toAlpha = toSamples - toInt
        val last = length - 1
        val rawCap = cap
        val safeCap = if (rawCap.isFinite() && rawCap > 0.0) rawCap else 1.0
        val fadeStart = fadeDone

        var done = 0
        var pos = writePos

        while (done < length) {
            val chunk = min(length - done, bufferSize - pos)

            processInternalCrossfade(
                buffer.left, input.left, output.left, done, chunk, pos,
                fromInt, fromAlpha, toInt, toAlpha, fbEnd, fbStep, last, safeCap, fadeStart,
            )
            processInternalCrossfade(
                buffer.right, input.right, output.right, done, chunk, pos,
                fromInt, fromAlpha, toInt, toAlpha, fbEnd, fbStep, last, safeCap, fadeStart,
            )

            done += chunk
            pos = (pos + chunk) % bufferSize
        }

        writePos = pos

        val elapsed = fadeStart + length
        fadeDone = elapsed

        if (elapsed >= fadeTotal) {
            fading = false
        }
    }

    /** The crossfade twin of [processInternal]; see [processCrossfade]. */
    private fun processInternalCrossfade(
        buffer: AudioBuffer,
        input: AudioBuffer,
        output: AudioBuffer,
        offset: Int,
        length: Int,
        startWritePos: Int,
        fromInt: Int,
        fromAlpha: Double,
        toInt: Int,
        toAlpha: Double,
        fbEnd: Double,
        fbStep: Double,
        last: Int,
        cap: Double,
        fadeStart: Int,
    ) {
        val total = fadeTotal
        val inv = invFadeTotal
        var pos = startWritePos
        // The feedback ramp, entered at this chunk's first sample (see [process]).
        var fb = fbEnd - fbStep * (last - offset)

        for (i in 0 until length) {
            val inputIndex = offset + i

            var old1 = pos - fromInt
            if (old1 < 0) {
                old1 += bufferSize
            }
            var old2 = old1 - 1
            if (old2 < 0) {
                old2 += bufferSize
            }
            var new1 = pos - toInt
            if (new1 < 0) {
                new1 += bufferSize
            }
            var new2 = new1 - 1
            if (new2 < 0) {
                new2 += bufferSize
            }

            val o1 = buffer[old1]
            val n1 = buffer[new1]
            val oldTap = o1 + fromAlpha * (buffer[old2] - o1)
            val newTap = n1 + toAlpha * (buffer[new2] - n1)

            val k = fadeStart + inputIndex + 1
            val g = if (k >= total) 1.0 else k * inv
            val delayedSignal = oldTap * (1.0 - g) + newTap * g

            val newSample = input[inputIndex] + (delayedSignal * fb)
            // NaN-guard on the ring store, for the reason [processInternal] gives.
            val stored = if (cap == 1.0) {
                ShapingFuncs.softCap(newSample)
            } else {
                cap * ShapingFuncs.softCap(newSample / cap)
            }
            buffer[pos] = stored.nanGuard()

            output[inputIndex] = output[inputIndex] + delayedSignal

            fb += fbStep
            pos++
        }
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
        fbEnd: Double,
        fbStep: Double,
        last: Int,
        cap: Double,
    ) {
        var pos = startWritePos
        // The feedback ramp, entered at this chunk's first sample (see [process]). One add per
        // sample, of exactly 0.0 on a settled line.
        var fb = fbEnd - fbStep * (last - offset)

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

            fb += fbStep
            pos++
        }
    }

    companion object {
        /**
         * Lower bound for [time] in seconds. ~0.1 ms — short enough
         * for flanger/comb regimes, long enough that linear interpolation
         * doesn't blow up at boundary conditions.
         */
        private const val MIN_DELAY_SECONDS: Double = 0.0001
    }
}
