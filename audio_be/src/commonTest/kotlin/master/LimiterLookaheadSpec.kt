/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.master

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.MasterStage
import io.peekandpoke.klang.audio_be.effects.Compressor
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.tanh

/**
 * **The guard for the transient "knock".**
 *
 * The master limiter is feed-forward with no lookahead, so it cannot reduce a peak it has not seen
 * yet. A kick-shaped transient driven over the ceiling escapes at very nearly its input level and is
 * dealt with by the hard clip in [MasterStage] instead — 2–6 ms of hard clipping per transient,
 * widening with the amount of limiting. That is what is audible as a knock.
 *
 * Two things about how this is measured, both load-bearing:
 *
 * 1. **It asserts on [Compressor] output directly, NOT on [MasterStage] output.** `MasterStage`
 *    emits a `ShortArray` through a hard clip, so "no sample exceeds 1.0" measured there is
 *    trivially true *with the bug present* — a textbook toothless guard.
 * 2. **Drive is bounded at +18 dB over threshold.** At `ratio = 20` the best achievable output is
 *    `threshold + overshoot/ratio`, so ≥ +20 dB over legitimately exceeds 0 dBFS no matter how good
 *    the anticipation is. That bound is a property of the ratio, not a defect — see
 *    `docs/tasks/master-limiter-lookahead.md` §2.4.
 *
 * **Known unguarded details** — recorded rather than covered by tests that would only look like
 * guards:
 *
 * - `MIN_BOX_TAPS` (the floor that stops `.attack(0.0)` collapsing the two box filters into an
 *   identity, i.e. a one-sample gain step at ~595 dB/ms). Removing the floor is not detectable here:
 *   recovering the applied gain from output/input has to skip samples near zero crossings, so
 *   consecutive measurements are not adjacent and the per-sample step gets diluted below any
 *   threshold that a clean run also passes. Catching it wants a gain-trajectory probe on the
 *   `Compressor` itself, which does not exist yet.
 * - The min-hold window is `delayFrames + 1`, and shrinking it to
 * `delayFrames` is NOT detectable through this spec — three attempts (house config, fast release,
 * short smoothing) all stayed green. The reason is structural: the box filters average *held* values
 * from earlier samples, and those windows still contain the peak, so the gain stays down even when
 * the peak's own sample has fallen out of its own window. The `+ 1` is correct and should stay, but
 * with any smoothing present it is not observable in the output. Recorded rather than guarded with a
 * test that would only look like a guard.
 *
 * This spec was written BEFORE the fix and watched failing — `expected:<0> but was:<176>` at +6 dB
 * over, i.e. 176 samples (3.99 ms) escaping to the clip. That is the evidence it can fail; a guard
 * that has only ever been green proves nothing.
 */
class LimiterLookaheadSpec : StringSpec({

    val sampleRate = 44100

    /** The house safety limiter, exactly as [MasterStage] configures it. */
    fun houseLimiter() = Compressor(
        sampleRate = sampleRate,
        thresholdDb = MasterStage.LIMITER_THRESHOLD_DB,
        ratio = MasterStage.LIMITER_RATIO,
        kneeDb = MasterStage.LIMITER_KNEE_DB,
        attackSeconds = MasterStage.LIMITER_ATTACK_SECONDS,
        releaseSeconds = MasterStage.LIMITER_RELEASE_SECONDS,
        lookaheadSeconds = MasterStage.LIMITER_LOOKAHEAD_SECONDS,
    )

    /**
     * A kick-shaped transient: a fast-decaying low sine with a downward pitch sweep. This is the
     * shape that generated the report — low enough that the carrier cannot mask a clipped edge.
     */
    fun kick(frames: Int, peak: Double): DoubleArray {
        val out = DoubleArray(frames)
        var phase = 0.0
        for (i in 0 until frames) {
            val t = i.toDouble() / sampleRate
            val freq = 55.0 * (1.0 + 3.0 * exp(-t / 0.02))
            phase += 2.0 * PI * freq / sampleRate
            out[i] = peak * exp(-t / 0.030) * sin(phase)
        }
        return out
    }

    /** Peak of |x| over the buffer, in dBFS. */
    fun peakDbfs(buffer: DoubleArray): Double {
        var peak = 0.0
        for (v in buffer) {
            val a = abs(v)
            if (a > peak) peak = a
        }
        return 20.0 * log10(if (peak > 1e-12) peak else 1e-12)
    }

    /** How many samples the final clip would have to flatten. */
    fun samplesOverCeiling(buffer: DoubleArray): Int = buffer.count { abs(it) > 1.0 }

    fun renderKick(overshootDb: Double): DoubleArray {
        val frames = sampleRate / 4
        val signal = kick(frames, peak = exp(overshootDb / 20.0 * 2.302585092994046))
        val left = signal.copyOf()
        val right = signal.copyOf()
        houseLimiter().process(left, right, frames)
        return left
    }

    "the limiter holds the ceiling on a transient — nothing reaches the final clip" {
        // The invariant that matters: the LIMITER does the limiting. Any sample above 1.0 here is a
        // sample the hard clip has to flatten, and a run of them is the knock.
        listOf(6.0, 12.0, 18.0).forEach { overshootDb ->
            val out = renderKick(overshootDb)

            withClue(overshootDb) {
                samplesOverCeiling(out) shouldBe 0
            }
        }
    }

    "a transient does not escape at its input level" {
        // Restates the same defect as a level rather than a count, so a failure reports HOW far the
        // transient escaped. Today a +12 dB kick exits at ~+11.7 dBFS — the limiter contributes
        // essentially nothing during the transient.
        val out = renderKick(12.0)

        (peakDbfs(out) < 0.0) shouldBe true
    }

    "the gain minimum lands exactly on the peak it is protecting" {
        // Added after mutation-checking: shrinking the min-hold window from D+1 to D sailed through
        // every peak-level assertion above, because a slow release still catches the tail and the
        // peak number stays defensible. Only the TIMING of the gain minimum exposes it.
        val delayFrames = (MasterStage.LIMITER_LOOKAHEAD_SECONDS * sampleRate).toInt()
        val frames = 4000
        val impulseAt = 500

        val signal = DoubleArray(frames)
        signal[impulseAt] = 4.0                    // ~+12 dB over the ceiling, one sample wide
        val left = signal.copyOf()
        val right = signal.copyOf()
        houseLimiter().process(left, right, frames)

        // The impulse emerges delayed by exactly the lookahead...
        val loudest = (0 until frames).maxByOrNull { abs(left[it]) } ?: -1
        loudest shouldBe impulseAt + delayFrames

        // ...and it must not have escaped, which is only true if the gain reached its minimum by
        // the time this sample arrived — i.e. if the hold window really spans the whole delay.
        (abs(left[loudest]) <= 1.0) shouldBe true
    }

    "a sustained over-ceiling signal recovers after it stops" {
        // Added after mutation-checking: deleting the release stage entirely (gain latches at its
        // minimum forever) was invisible to every other test here, because they all end while the
        // limiter is still working. This is the guard that the gain comes back.
        val frames = sampleRate / 2
        val loudFor = sampleRate / 10
        val signal = DoubleArray(frames) { i ->
            val amp = if (i < loudFor) 4.0 else 0.05      // loud burst, then very quiet
            amp * sin(2.0 * PI * 220.0 * i / sampleRate)
        }
        val left = signal.copyOf()
        val right = signal.copyOf()
        houseLimiter().process(left, right, frames)

        // Long after the burst the quiet tail must be passing at essentially unity again. With the
        // release removed it stays ducked by ~12 dB forever.
        val tail = left.copyOfRange(frames - sampleRate / 20, frames)
        val tailPeak = tail.maxOf { abs(it) }

        (tailPeak > 0.04) shouldBe true
    }

    "a quiet transient passes untouched — the fix must not cost transparency" {
        // Below the threshold the limiter must stay out of the way. This one passes today and must
        // keep passing: it is the guard against "fixed the knock by squashing everything".
        // NB: asserting "nothing over 1.0" here would be decoration — the gain path can only ever
        // return <= 1.0, so a quiet input cannot exceed the ceiling however broken the limiter is.
        // The level assertion is what actually carries the transparency claim.
        val out = renderKick(-6.0)

        (peakDbfs(out) > -7.0) shouldBe true
    }

    "the ceiling holds at EVERY smoothing length — the shape-bug guard" {
        // The plan's headline test, and the one that would have caught the construction bug found
        // during design (an earlier draft partitioned the window between min-hold and smoothing;
        // it measured +0.14 / +1.10 / +2.74 dBFS as the smoothing widened).
        //
        // With a correct construction the min-hold does the anticipating, so the peak result must be
        // INVARIANT to how long the smoother is. At the house setting (smoothing = the full window)
        // a partition bug is invisible — only sweeping the smoothing exposes it.
        val peaks = listOf(0.0005, 0.001, 0.0025, 0.005).map { attack ->
            val limiter = Compressor(
                sampleRate = sampleRate,
                thresholdDb = MasterStage.LIMITER_THRESHOLD_DB,
                ratio = MasterStage.LIMITER_RATIO,
                kneeDb = MasterStage.LIMITER_KNEE_DB,
                attackSeconds = attack,
                releaseSeconds = MasterStage.LIMITER_RELEASE_SECONDS,
                lookaheadSeconds = MasterStage.LIMITER_LOOKAHEAD_SECONDS,
            )
            val frames = sampleRate / 4
            val signal = kick(frames, peak = exp(12.0 / 20.0 * 2.302585092994046))
            val left = signal.copyOf()
            val right = signal.copyOf()
            limiter.process(left, right, frames)
            samplesOverCeiling(left)
        }

        peaks.forEach { it shouldBe 0 }
    }

    "two peaks inside one lookahead window — the case that defeated the naive design" {
        // A second, larger peak arriving while the gain is already moving. A ramp-based design
        // restarts from the current gain and cannot get there in time; the min-hold handles it with
        // no special case, because both peaks are inside the same hold window.
        val frames = sampleRate / 4
        val signal = DoubleArray(frames)
        // Amplitudes chosen so the SUM stays inside the +18 dB drive bound this spec documents —
        // past ~+20 dB over threshold a 20:1 curve legitimately exceeds the ceiling (§2.4), and that
        // residual would be measured here as a construction failure when it is arithmetic.
        val small = kick(frames, peak = 1.5)
        val big = kick(frames, peak = 4.0)
        val offset = (0.0005 * sampleRate).toInt()
        for (i in 0 until frames) {
            signal[i] = small[i] + if (i >= offset) big[i - offset] else 0.0
        }
        val left = signal.copyOf()
        val right = signal.copyOf()
        houseLimiter().process(left, right, frames)

        samplesOverCeiling(left) shouldBe 0
    }

    "reset() clears the delay ring — no stale audio into the first block after warmup" {
        // WarmupRunner -> dispatcher.resetPostChain() -> MasterStage.reset(), and MasterBus.beginFade
        // resets chains ON THE AUDIO THREAD. Without clearing the ring, whatever was mid-flight
        // replays into the next block.
        val limiter = houseLimiter()
        val frames = 512

        val loud = DoubleArray(frames) { 0.9 }
        limiter.process(loud, loud.copyOf(), frames)   // fill the ring with signal

        limiter.reset()

        val silence = DoubleArray(frames)
        val right = DoubleArray(frames)
        limiter.process(silence, right, frames)

        // Every sample must be silence. Without the reset the ring still holds the 0.9 block.
        silence.forEach { abs(it) shouldBe 0.0 }
    }


    "a monotonically-falling loud tone does not defeat the hold window" {
        // The min-hold deque fills only when `required` rises for the whole window — i.e. when the
        // level FALLS monotonically for 5 ms while above the knee. That is ordinary saturated
        // sub-bass, and at the wrong ring size the deque overflows into a false "empty", discards
        // the window, and leaks straight past the ceiling into the hard clip.
        //
        // The kick shape used elsewhere in this spec misses this by luck: a 55 Hz sweep's falling
        // quarter-cycle is ~200 samples, just under the 221-sample window.
        val frames = sampleRate / 2
        // A tanh-SATURATED 20 Hz tone at +18 dB, and the saturation is the point: it flattens the
        // peak while leaving long, gently-falling shoulders, so |x| decreases monotonically for far
        // longer than the 221-sample window. A plain sine does not trigger it (its flank falls too
        // fast) and a hard-clipped square has no falling flank at all — both were tried and both
        // missed the bug.
        val signal = DoubleArray(frames) { i ->
            tanh(8.0 * sin(2.0 * PI * 20.0 * i / sampleRate)) * 8.0
        }
        val left = signal.copyOf()
        val right = signal.copyOf()
        houseLimiter().process(left, right, frames)

        samplesOverCeiling(left) shouldBe 0
    }

    "the bed recovers between kicks — the dual release, not one long one" {
        // The pump: with a single 100 ms release the gain is still climbing back when the next kick
        // lands, so a sustained bed audibly swells between hits. The dual release lets the fast
        // branch clear each hit while the slow branch holds a steady floor.
        //
        // Measured here as the gain applied to the BED three-quarters of the way through the gap,
        // where the kick is long gone. Single release: 0.926. Dual: 0.986.
        val sampleRateLocal = sampleRate
        val frames = sampleRateLocal * 2
        val period = sampleRateLocal / 4                       // 120 BPM eighths
        val signal = DoubleArray(frames) { i ->
            val t = i.toDouble() / sampleRateLocal
            val phase = i % period
            val bed = 0.5 * sin(2.0 * PI * 220.0 * t)
            val kick = if (phase < sampleRateLocal / 20) {
                1.41 * exp(-phase.toDouble() / (0.03 * sampleRateLocal)) *
                        sin(2.0 * PI * 55.0 * phase / sampleRateLocal)
            } else 0.0
            bed + kick
        }

        val left = signal.copyOf()
        val right = signal.copyOf()
        houseLimiter().process(left, right, frames)

        val delayFrames = (MasterStage.LIMITER_LOOKAHEAD_SECONDS * sampleRateLocal).toInt()
        val probe = sampleRateLocal + period * 3 / 4           // settled, mid-gap
        val recovered = left[probe] / signal[probe - delayFrames]

        (recovered > 0.96) shouldBe true
    }
})

/** Local `withClue` shim — keeps the failure message pointing at the drive level that failed. */
private inline fun <T> withClue(clue: Any?, thunk: () -> T): T {
    try {
        return thunk()
    } catch (e: AssertionError) {
        throw AssertionError("at overshoot ${clue} dB over threshold: ${e.message}", e)
    }
}
