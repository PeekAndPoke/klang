/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.effects.DelayLine
import kotlin.math.abs
import kotlin.math.ceil

class KatalystDelayEffectSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128

    fun createCtx() = KatalystContext(
        blockFrames = blockFrames,
        mixBuffer = StereoBuffer(blockFrames),
        delaySendBuffer = StereoBuffer(blockFrames),
        reverbSendBuffer = StereoBuffer(blockFrames),
    )

    fun createEffect(delayTime: Double = 0.5, feedback: Double = 0.0): KatalystDelayEffect {
        val dl = DelayLine(maxDelaySeconds = 10.0, sampleRate = sampleRate)

        return KatalystDelayEffect(delayLine = dl, blockFrames = blockFrames).apply {
            configure(timeSeconds = delayTime, feedback = feedback, cap = 1.0)
        }
    }

    "an off-config (time < 0.01s) on a fresh line stays off — nothing is processed" {
        val effect = createEffect(delayTime = 0.005)
        val ctx = createCtx()

        // Put signal in send buffer
        ctx.delaySendBuffer.left[0] = 0.5

        effect.process(ctx)

        // Mix buffer should be untouched
        ctx.mixBuffer.left[0] shouldBe 0.0
    }

    "processes delay when delay time is above threshold" {
        val effect = createEffect(delayTime = 0.05)
        val ctx = createCtx()

        // Put signal in send buffer
        for (i in 0 until blockFrames) {
            ctx.delaySendBuffer.left[i] = 0.5
            ctx.delaySendBuffer.right[i] = 0.3
        }

        // Process multiple blocks to allow delay to fill
        repeat(50) {
            ctx.delaySendBuffer.left.fill(0.5)
            ctx.delaySendBuffer.right.fill(0.3)
            ctx.mixBuffer.clear()
            effect.process(ctx)
        }

        // After enough blocks, delayed signal should appear in mix buffer
        val hasSignalL = ctx.mixBuffer.left.any { it != 0.0 }
        val hasSignalR = ctx.mixBuffer.right.any { it != 0.0 }

        hasSignalL shouldBe true
        hasSignalR shouldBe true
    }

    "delay line parameters are accessible and writable" {
        val effect = createEffect(delayTime = 0.5, feedback = 0.3)

        effect.delayLine!!.delayTimeSeconds shouldBe 0.5
        effect.delayLine!!.feedback shouldBe 0.3

        effect.delayLine!!.delayTimeSeconds = 1.0
        effect.delayLine!!.delayTimeSeconds shouldBe 1.0
    }

    // ── The drain lifecycle (block-framing ledger D3) ─────────────────────────

    "an off-config drains the tail on schedule — identical to staying active with silent sends" {
        // Reference: the owner keeps the delay and the send simply goes silent.
        val ref = createEffect(delayTime = 0.05, feedback = 0.5)
        val refCtx = createCtx()

        // Drained: a no-delay owner takes over after the hot block.
        val drained = createEffect(delayTime = 0.05, feedback = 0.5)
        val drainedCtx = createCtx()

        refCtx.delaySendBuffer.left[0] = 1.0
        refCtx.delaySendBuffer.right[0] = 1.0
        drainedCtx.delaySendBuffer.left[0] = 1.0
        drainedCtx.delaySendBuffer.right[0] = 1.0
        ref.process(refCtx)
        drained.process(drainedCtx)

        drained.configure(timeSeconds = 0.0, feedback = 0.0, cap = 1.0)

        // 40 blocks ≈ 2.3 echo periods at 0.05 s — well inside the countdown, so the two runs
        // must be BIT-identical: the drain is by construction "active with silent input".
        var maxDiff = 0.0

        repeat(40) {
            refCtx.delaySendBuffer.clear()
            refCtx.mixBuffer.clear()
            ref.process(refCtx)

            // The draining side gets GARBAGE sends — a drain must discard them (the owner said off).
            drainedCtx.delaySendBuffer.fill(0.7)
            drainedCtx.mixBuffer.clear()
            drained.process(drainedCtx)

            for (i in 0 until blockFrames) {
                maxDiff = maxOf(maxDiff, abs(refCtx.mixBuffer.left[i] - drainedCtx.mixBuffer.left[i]))
                maxDiff = maxOf(maxDiff, abs(refCtx.mixBuffer.right[i] - drainedCtx.mixBuffer.right[i]))
            }
        }

        maxDiff shouldBe 0.0
    }

    "the countdown ends: ring literally empty, processing short-circuits" {
        val effect = createEffect(delayTime = 0.05, feedback = 0.5)
        val ctx = createCtx()

        ctx.delaySendBuffer.left[0] = 1.0
        ctx.delaySendBuffer.right[0] = 1.0
        effect.process(ctx)

        effect.configure(timeSeconds = 0.0, feedback = 0.0, cap = 1.0)

        // The retained (last-active) params + the measured ring peak drive the countdown, so this
        // recomputes the same value the effect captured at the off-transition.
        val drainSamples = effect.delayLine!!.drainSamplesUntilSilent(peak = effect.delayLine!!.tapWindowPeakAbs())
        drainSamples.isFinite() shouldBe true
        val drainBlocks = ceil(drainSamples / blockFrames).toInt()

        // Halfway through, the tail must still be draining (guards a grossly short countdown).
        repeat(drainBlocks / 2) {
            ctx.delaySendBuffer.clear()
            ctx.mixBuffer.clear()
            effect.process(ctx)
        }
        effect.hasTail() shouldBe true

        repeat(drainBlocks / 2 + 2) {
            ctx.delaySendBuffer.clear()
            ctx.mixBuffer.clear()
            effect.process(ctx)
        }

        effect.hasTail() shouldBe false
        // Literally zero: hasTail(0.0) is a strict > comparison, ANY residue would trip it.
        effect.delayLine!!.hasTail(0.0) shouldBe false

        // And Off is a true short-circuit: a hot send no longer reaches the mix.
        ctx.delaySendBuffer.fill(0.9)
        ctx.mixBuffer.clear()
        effect.process(ctx)
        ctx.mixBuffer.left.all { it == 0.0 } shouldBe true
    }

    "re-enabling after off resurrects nothing, even with a far longer delay time" {
        val effect = createEffect(delayTime = 0.05, feedback = 0.6)
        val ctx = createCtx()

        repeat(4) {
            ctx.delaySendBuffer.fill(0.8)
            ctx.mixBuffer.clear()
            effect.process(ctx)
        }

        effect.configure(timeSeconds = 0.0, feedback = 0.0, cap = 1.0)

        val drainBlocks = ceil(
            effect.delayLine!!.drainSamplesUntilSilent(peak = effect.delayLine!!.tapWindowPeakAbs()) / blockFrames
        ).toInt() + 2

        repeat(drainBlocks) {
            ctx.delaySendBuffer.clear()
            ctx.mixBuffer.clear()
            effect.process(ctx)
        }
        effect.hasTail() shouldBe false

        // New owner with a delay long enough that its tap sweeps the ENTIRE region written above.
        // Without the terminal reset, the pre-drain tail would re-emerge somewhere in this sweep.
        effect.configure(timeSeconds = 8.0, feedback = 0.3, cap = 1.0)

        var residue = 0.0
        val sweepBlocks = (8.5 * sampleRate / blockFrames).toInt()

        repeat(sweepBlocks) {
            ctx.delaySendBuffer.clear()
            ctx.mixBuffer.clear()
            effect.process(ctx)

            for (i in 0 until blockFrames) {
                residue = maxOf(residue, abs(ctx.mixBuffer.left[i]), abs(ctx.mixBuffer.right[i]))
            }
        }

        residue shouldBe 0.0
    }

    "feedback >= 1.0 never auto-drains — the self-oscillating drone keeps sounding" {
        val effect = createEffect(delayTime = 0.02, feedback = 1.2)
        val ctx = createCtx()

        ctx.delaySendBuffer.left[0] = 0.5
        ctx.delaySendBuffer.right[0] = 0.5
        effect.process(ctx)

        effect.configure(timeSeconds = 0.0, feedback = 0.0, cap = 1.0)

        // 2000 blocks ≈ 290 delay periods — far beyond any finite countdown for a 0.02 s line.
        // The drone is an impulse train with 882-sample spacing, so a single 128-frame block can
        // legitimately contain no echo — measure the peak over the last 10 blocks (> one period).
        var tailWindowPeak = 0.0

        repeat(2000) { idx ->
            ctx.delaySendBuffer.clear()
            ctx.mixBuffer.clear()
            effect.process(ctx)

            if (idx >= 1990) {
                for (i in 0 until blockFrames) {
                    tailWindowPeak = maxOf(tailWindowPeak, abs(ctx.mixBuffer.left[i]))
                }
            }
        }

        effect.hasTail() shouldBe true
        (tailWindowPeak > 0.1) shouldBe true
    }

    "a mismatched context cannot make the countdown outrun the ring" {
        // The effect is built for 64-frame blocks but driven with a 128-frame context: the drain
        // clamps its processing to 64, so the countdown must tick by 64 too (review round 2 — a
        // countdown outrunning the ring would fire the terminal reset at ~-50 dBFS).
        val effect = KatalystDelayEffect(
            delayLine = DelayLine(maxDelaySeconds = 10.0, sampleRate = sampleRate),
            blockFrames = 64,
        ).apply {
            configure(timeSeconds = 0.05, feedback = 0.5, cap = 1.0)
        }
        val ctx = createCtx()

        ctx.delaySendBuffer.left[0] = 1.0
        effect.process(ctx)
        effect.configure(timeSeconds = 0.0, feedback = 0.0, cap = 1.0)

        val drainSamples = effect.delayLine!!.drainSamplesUntilSilent(peak = effect.delayLine!!.tapWindowPeakAbs())
        // Enough 128-frame calls that a countdown ticking by ctx.blockFrames would have flipped
        // Off — but the ring has only processed HALF that many samples and is still audible.
        val callsForBuggyFlip = ceil(drainSamples / blockFrames).toInt() + 2

        repeat(callsForBuggyFlip) {
            ctx.delaySendBuffer.clear()
            ctx.mixBuffer.clear()
            effect.process(ctx)
        }
        effect.hasTail() shouldBe true

        // With the honest 64-sample tick it completes in twice the calls.
        repeat(callsForBuggyFlip + 4) {
            ctx.delaySendBuffer.clear()
            ctx.mixBuffer.clear()
            effect.process(ctx)
        }
        effect.hasTail() shouldBe false
    }

    "reset restores the factory params — the next orbit life cannot inherit them" {
        // DelayLine setters DROP non-finite writes, so a NaN param from the next life would
        // otherwise keep THIS life's value (e.g. a dead owner's self-oscillating feedback).
        val effect = createEffect(delayTime = 0.5, feedback = 1.2)

        effect.reset()

        effect.delayLine!!.delayTimeSeconds shouldBe 0.0
        effect.delayLine!!.feedback shouldBe 0.0
        effect.delayLine!!.feedbackCap shouldBe 1.0
    }

    "a quiet ring drains in proportion to its content, not the saturated worst case" {
        // Review round 3: the static bound made every drain pay the full theoretical countdown —
        // a stopped playback's engine stayed alive for a minute-plus of silent processing. The
        // countdown now starts from the measured ring peak.
        val effect = createEffect(delayTime = 0.02, feedback = 0.9)
        val ctx = createCtx()

        // Charge QUIETLY: peak ~1e-3.
        ctx.delaySendBuffer.left[0] = 0.001
        ctx.delaySendBuffer.right[0] = 0.001
        effect.process(ctx)

        effect.configure(timeSeconds = 0.0, feedback = 0.0, cap = 1.0)

        // From 1e-3 at fb 0.9: ceil(ln(1e-5/1e-3)/ln(0.9)) + 1 = 45 periods. The worst-case bound
        // (from 1.0) would be 111 periods — assert we are done well before THAT.
        val peakBlocks = ceil(46.0 * 0.02 * sampleRate / blockFrames).toInt() + 2

        repeat(peakBlocks) {
            ctx.delaySendBuffer.clear()
            ctx.mixBuffer.clear()
            effect.process(ctx)
        }

        effect.hasTail() shouldBe false
    }

    "an empty self-oscillating ring frees the orbit; a charged one reports its tail" {
        // The round-1 engine-leak shape (|fb| >= 1 + empty ring = pinned forever) is closed at
        // the configure door since round 3: a silent tap window goes STRAIGHT to Off, so the
        // infinite countdown never even starts.
        val empty = createEffect(delayTime = 0.5, feedback = 1.2)
        // Never processed a send: the ring is all zeros — the off-config lands in Off directly.
        empty.configure(timeSeconds = 0.0, feedback = 0.0, cap = 1.0)
        empty.hasTail() shouldBe false

        // A charged ring, same infinite drain: the tail is real and must be reported.
        val charged = createEffect(delayTime = 0.05, feedback = 1.2)
        val ctx = createCtx()
        ctx.delaySendBuffer.left[0] = 1.0
        ctx.delaySendBuffer.right[0] = 1.0
        charged.process(ctx)
        charged.configure(timeSeconds = 0.0, feedback = 0.0, cap = 1.0)
        charged.hasTail() shouldBe true
    }

    "the drain is sample-counted: block size changes neither the audio nor the off point" {
        fun runDrain(bf: Int): Pair<DoubleArray, Int> {
            val effect = KatalystDelayEffect(
                delayLine = DelayLine(maxDelaySeconds = 10.0, sampleRate = sampleRate),
                blockFrames = bf,
            ).apply {
                configure(timeSeconds = 0.05, feedback = 0.5, cap = 1.0)
            }
            val ctx = KatalystContext(
                blockFrames = bf,
                mixBuffer = StereoBuffer(bf),
                delaySendBuffer = StereoBuffer(bf),
                reverbSendBuffer = StereoBuffer(bf),
            )

            // 18 periods x 2205 samples: the pinned countdown for this run's full-scale impulse at
            // fb 0.5 / 0.05 s (see DelayLineSpec) — a constant here because the ring is still
            // EMPTY at this point (the peak-based query would answer 0).
            val drainSamples = 18.0 * 2205.0
            val totalSamples = 128 + (ceil(drainSamples / 128.0).toInt() + 4) * 128
            val out = DoubleArray(totalSamples)
            var offAtSample = -1
            var absSample = 0

            while (absSample < totalSamples) {
                ctx.delaySendBuffer.clear()
                ctx.mixBuffer.clear()

                if (absSample == 0) {
                    ctx.delaySendBuffer.left[0] = 1.0
                }

                // The takeover lands at the SAME absolute sample for every block size.
                if (absSample == 128) {
                    effect.configure(timeSeconds = 0.0, feedback = 0.0, cap = 1.0)
                }

                effect.process(ctx)

                for (i in 0 until bf) {
                    out[absSample + i] = ctx.mixBuffer.left[i]
                }
                absSample += bf

                if (offAtSample < 0 && !effect.hasTail()) {
                    offAtSample = absSample
                }
            }

            return out to offAtSample
        }

        val (out128, off128) = runDrain(128)
        val (out64, off64) = runDrain(64)

        var maxDiff = 0.0
        for (i in out128.indices) {
            maxDiff = maxOf(maxDiff, abs(out128[i] - out64[i]))
        }

        // Identical until the (block-quantised) off flip; after either flips, both sides are below
        // the drain silence threshold, so the streams may differ by at most that threshold.
        (maxDiff <= 0.00001) shouldBe true

        // Both must actually reach Off, within one 128-frame block of each other.
        (off128 > 0) shouldBe true
        (off64 > 0) shouldBe true
        (abs(off128 - off64) <= 128) shouldBe true
    }
})
