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
 * **Known unguarded detail.** The min-hold window is `delayFrames + 1`, and shrinking it to
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
        val out = renderKick(-6.0)

        samplesOverCeiling(out) shouldBe 0
        (peakDbfs(out) > -7.0) shouldBe true
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
