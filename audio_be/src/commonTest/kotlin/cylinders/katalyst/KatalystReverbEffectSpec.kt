/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.effects.Reverb
import kotlin.math.abs
import kotlin.math.ceil

class KatalystReverbEffectSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128

    fun createCtx() = KatalystContext(
        blockFrames = blockFrames,
        mixBuffer = StereoBuffer(blockFrames),
        delaySendBuffer = StereoBuffer(blockFrames),
        reverbSendBuffer = StereoBuffer(blockFrames),
    )

    // The production door takes every param explicitly (no defaults, mirroring the delay);
    // this trims the boilerplate for rows that only care about the lifecycle-deciding size.
    fun KatalystReverbEffect.configureSize(size: Double) {
        configure(size = size, lowpass = null)
    }

    fun createEffect(size: Double = 0.5): KatalystReverbEffect {
        return KatalystReverbEffect(reverb = Reverb(sampleRate), blockFrames = blockFrames).apply {
            configureSize(size = size)
        }
    }

    "an off-config (size < 0.01) on a fresh network stays off — nothing is processed" {
        val effect = createEffect(size = 0.005)
        val ctx = createCtx()

        // 25 hot blocks: an ACTIVE reverb is audibly wet from ~block 9 (shortest comb 1116
        // samples), so a single-block probe of sample 0 could never fail — review round 1.
        var anySignal = false

        repeat(25) {
            ctx.reverbSendBuffer.fill(0.5)
            ctx.mixBuffer.clear()
            effect.process(ctx)

            if (ctx.mixBuffer.left.any { it != 0.0 } || ctx.mixBuffer.right.any { it != 0.0 }) {
                anySignal = true
            }
        }

        anySignal shouldBe false
    }

    "processes reverb when size is above threshold" {
        val effect = createEffect(size = 0.5)
        val ctx = createCtx()

        // Feed signal through multiple blocks — reverb comb filters need time to build up
        repeat(20) {
            ctx.reverbSendBuffer.left.fill(0.5)
            ctx.reverbSendBuffer.right.fill(0.5)
            ctx.mixBuffer.clear()
            effect.process(ctx)
        }

        // Audible wet, not the ~1e-20 anti-denormal residue any processed block carries (round 2).
        val hasSignal = ctx.mixBuffer.left.any { it > 1e-6 || it < -1e-6 } ||
                ctx.mixBuffer.right.any { it > 1e-6 || it < -1e-6 }
        hasSignal shouldBe true
    }

    "reverb parameters are accessible" {
        val effect = createEffect(size = 0.7)

        effect.reverb!!.size shouldBe 0.7
        effect.reverb!!.sampleRate shouldBe sampleRate
    }

    // ── The drain lifecycle (block-framing ledger D3, adopted for the reverb) ─────────────────

    "an off-config drains the tail on schedule — identical to staying active with silent sends" {
        // Reference: the owner keeps the reverb and the send simply goes silent.
        val ref = createEffect(size = 0.05)
        val refCtx = createCtx()

        // Drained: a no-reverb owner takes over after the hot block.
        val drained = createEffect(size = 0.05)
        val drainedCtx = createCtx()

        refCtx.reverbSendBuffer.left[0] = 1.0
        refCtx.reverbSendBuffer.right[0] = 1.0
        drainedCtx.reverbSendBuffer.left[0] = 1.0
        drainedCtx.reverbSendBuffer.right[0] = 1.0
        ref.process(refCtx)
        drained.process(drainedCtx)

        drained.configureSize(size = 0.0)

        // The off-config must not have reached the DSP: the drain decays at the RETAINED room.
        drained.reverb!!.size shouldBe 0.05

        // 40 blocks ≈ 3 revolutions of the longest comb — well inside the countdown, so the two
        // runs must be BIT-identical: the drain is by construction "active with silent input".
        var maxDiff = 0.0

        repeat(40) {
            refCtx.reverbSendBuffer.clear()
            refCtx.mixBuffer.clear()
            ref.process(refCtx)

            // The draining side gets GARBAGE sends — a drain must discard them (the owner said off).
            drainedCtx.reverbSendBuffer.fill(0.7)
            drainedCtx.mixBuffer.clear()
            drained.process(drainedCtx)

            for (i in 0 until blockFrames) {
                maxDiff = maxOf(maxDiff, abs(refCtx.mixBuffer.left[i] - drainedCtx.mixBuffer.left[i]))
                maxDiff = maxOf(maxDiff, abs(refCtx.mixBuffer.right[i] - drainedCtx.mixBuffer.right[i]))
            }
        }

        maxDiff shouldBe 0.0
    }

    "the countdown ends: comb network literally empty, processing short-circuits" {
        val effect = createEffect(size = 0.05)
        val ctx = createCtx()

        ctx.reverbSendBuffer.left[0] = 1.0
        ctx.reverbSendBuffer.right[0] = 1.0
        effect.process(ctx)

        effect.configureSize(size = 0.0)

        // The retained (last-active) params + the measured comb peak drive the countdown, so this
        // recomputes the same value the effect captured at the off-transition. Pinned: peak 1.0
        // (the impulse cell) at fb 0.714 (size 0.05) is 36 revolutions of the 1640-sample
        // longest comb (1617 + 23 stereo spread at 44.1 kHz).
        val drainSamples = effect.reverb!!.drainSamplesUntilSilent(peak = effect.reverb!!.combPeakAbs())
        drainSamples shouldBe (36.0 * 1640.0)
        val drainBlocks = ceil(drainSamples / blockFrames).toInt()

        // Halfway through, the tail must still be draining (guards a grossly short countdown).
        repeat(drainBlocks / 2) {
            ctx.reverbSendBuffer.clear()
            ctx.mixBuffer.clear()
            effect.process(ctx)
        }
        effect.hasTail() shouldBe true

        repeat(drainBlocks / 2 + 2) {
            ctx.reverbSendBuffer.clear()
            ctx.mixBuffer.clear()
            effect.process(ctx)
        }

        effect.hasTail() shouldBe false
        // Literally zero: hasTail(0.0) is a strict > comparison, ANY residue would trip it.
        effect.reverb!!.hasTail(0.0) shouldBe false

        // And Off is a true short-circuit: a hot send no longer reaches the mix.
        ctx.reverbSendBuffer.fill(0.9)
        ctx.mixBuffer.clear()
        effect.process(ctx)
        ctx.mixBuffer.left.all { it == 0.0 } shouldBe true
    }

    "re-enabling after off resurrects nothing" {
        val effect = createEffect(size = 0.05)
        val ctx = createCtx()

        repeat(4) {
            ctx.reverbSendBuffer.fill(0.8)
            ctx.mixBuffer.clear()
            effect.process(ctx)
        }

        effect.configureSize(size = 0.0)

        val drainBlocks = ceil(
            effect.reverb!!.drainSamplesUntilSilent(peak = effect.reverb!!.combPeakAbs()) / blockFrames
        ).toInt() + 2

        repeat(drainBlocks) {
            ctx.reverbSendBuffer.clear()
            ctx.mixBuffer.clear()
            effect.process(ctx)
        }
        effect.hasTail() shouldBe false

        // New owner with the LONGEST room (fb 0.98): any surviving cell would recirculate loudly
        // and near-indefinitely. Sweep for well over a full revolution of every buffer.
        effect.configureSize(size = 1.0)

        var residue = 0.0

        repeat(30) {
            ctx.reverbSendBuffer.clear()
            ctx.mixBuffer.clear()
            effect.process(ctx)

            for (i in 0 until blockFrames) {
                residue = maxOf(residue, abs(ctx.mixBuffer.left[i]), abs(ctx.mixBuffer.right[i]))
            }
        }

        // Not an exact 0.0: the reverb's canonical ANTI_DENORMAL bias (1e-18 per IIR store) keeps
        // the state epsilon-alive by design. Any REAL resurrection is >= ~1e-5 wet.
        (residue < 1.0e-12) shouldBe true
    }

    listOf(Double.POSITIVE_INFINITY, Double.NaN).forEach { bad ->
        "a non-finite size ($bad) reads as off, never as the previous owner's room" {
            // Reverb's setters DROP non-finite writes, so before the lifecycle a non-finite param
            // left the DSP on whatever the previous owner set (the phaser's D2 leak shape). +Inf is
            // the sharp half (review round 1): `Inf >= 0.01` is TRUE, so without the isFinite test
            // it reads as ACTIVE while the dropped write keeps the old room. Production cannot
            // reach this (normalizeSize bounds it in VoiceFactory) — this is the DOOR's own
            // contract for future callers. Both non-finite kinds must behave exactly like a clean
            // off-config: drain, discard sends.
            val poisoned = createEffect(size = 0.05)
            val poisonedCtx = createCtx()
            val off = createEffect(size = 0.05)
            val offCtx = createCtx()

            poisonedCtx.reverbSendBuffer.left[0] = 1.0
            offCtx.reverbSendBuffer.left[0] = 1.0
            poisoned.process(poisonedCtx)
            off.process(offCtx)

            poisoned.configureSize(size = bad)
            off.configureSize(size = 0.0)

            poisoned.reverb!!.size shouldBe 0.05 // retained — the non-finite config never reached the DSP
            poisoned.hasTail() shouldBe true // draining

            var maxDiff = 0.0

            repeat(30) {
                poisonedCtx.reverbSendBuffer.fill(0.6)
                poisonedCtx.mixBuffer.clear()
                poisoned.process(poisonedCtx)

                offCtx.reverbSendBuffer.fill(0.6)
                offCtx.mixBuffer.clear()
                off.process(offCtx)

                for (i in 0 until blockFrames) {
                    maxDiff = maxOf(maxDiff, abs(poisonedCtx.mixBuffer.left[i] - offCtx.mixBuffer.left[i]))
                }
            }

            maxDiff shouldBe 0.0
        }
    }

    "a NaN lowpass reads as unset, never as the previous owner's damping" {
        val effect = createEffect(size = 0.5)
        effect.configure(size = 0.5, lowpass = 500.0)
        effect.reverb!!.lowpass shouldBe 500.0

        effect.configure(size = 0.5, lowpass = Double.NaN)
        effect.reverb!!.lowpass shouldBe null
    }

    "size is bounded to 0..1 at the door too" {
        // Production pre-normalizes in VoiceFactory, so this pins the door contract for the
        // future direct caller the KDoc cites (review round 3: the clamp had no guard).
        val effect = createEffect(size = 5.0)
        effect.reverb!!.size shouldBe 1.0
    }

    "a non-finite SEND can no longer poison the network at all" {
        // Master round: `Reverb.process` sterilises its two input taps, so the route this
        // spec used to poison through is closed. The guard is on the INPUT and not on the 24
        // state stores because converting those to flushState was measured at ~+11%/sample
        // and reverted (2026-05-19) — and it is equivalent, since the comb/allpass network is
        // a stable linear system: a finite input can never drive the state non-finite.
        // Before it, one Inf send latched every comb for the life of the orbit.
        for (hostile in listOf(Double.POSITIVE_INFINITY, Double.NaN)) {
            val effect = createEffect(size = 0.5)
            val ctx = createCtx()

            ctx.reverbSendBuffer.fill(0.8)
            effect.process(ctx)

            ctx.reverbSendBuffer.clear()
            ctx.reverbSendBuffer.left[0] = hostile
            effect.process(ctx)

            effect.reverb!!.combPeakAbs().isFinite() shouldBe true
        }
    }

    "an overflow-poisoned network resets instead of draining forever" {
        // The route that REMAINS open after the input guard, and the realistic one: a runaway
        // finite gain. A sustained MAX_VALUE send overflows a comb cell to non-finite after
        // one delay revolution (~1116 samples at 44.1k) — no non-finite guard can prevent
        // that, which is exactly why the drain-heal is still load-bearing rather than dead
        // code. `Inf x fb` never decays, so the countdown is infinite and the off-transition
        // must heal (reset + Off) — without it the orbit and its PlaybackEngine are pinned
        // forever, feeding Inf/NaN into the mix (review round 1).
        val effect = createEffect(size = 0.5)
        val ctx = createCtx()

        repeat(16) {
            ctx.reverbSendBuffer.fill(Double.MAX_VALUE)
            ctx.mixBuffer.clear()
            effect.process(ctx)
        }

        effect.reverb!!.drainSamplesUntilSilent(peak = effect.reverb!!.combPeakAbs()) shouldBe Double.POSITIVE_INFINITY

        effect.configureSize(size = 0.0)

        effect.hasTail() shouldBe false
        // hasTail(0.0) is itself NaN-blind (NaN > 0.0 is false), so the heal is pinned with the
        // NaN-hardened scan instead (review round 3): zero means reset() really cleared them.
        effect.reverb!!.combPeakAbs() shouldBe 0.0
    }

    "a reverb owner arriving MID-drain goes straight to Active with the network kept" {
        val effect = createEffect(size = 0.05)
        val ctx = createCtx()

        repeat(4) {
            ctx.reverbSendBuffer.fill(0.8)
            ctx.mixBuffer.clear()
            effect.process(ctx)
        }

        effect.configureSize(size = 0.0)
        // The original countdown: a mutant that stays Draining would terminally reset by then.
        val originalDrainBlocks = ceil(
            effect.reverb!!.drainSamplesUntilSilent(peak = effect.reverb!!.combPeakAbs()) / blockFrames
        ).toInt() + 2

        repeat(10) {
            ctx.reverbSendBuffer.clear()
            ctx.mixBuffer.clear()
            effect.process(ctx)
        }
        effect.hasTail() shouldBe true // mid-drain

        // New owner takes the lease with the LONGEST room: network kept, params written, sends live.
        effect.configureSize(size = 1.0)

        effect.reverb!!.size shouldBe 1.0 // the new owner's params reached the DSP
        effect.hasTail() shouldBe true // the tail was NOT cut

        repeat(originalDrainBlocks) {
            ctx.reverbSendBuffer.fill(0.3)
            ctx.mixBuffer.clear()
            effect.process(ctx)
        }

        // Long past the abandoned countdown the effect is still Active: the live sends were
        // processed and keep the network hot (a stuck-Draining mutant discarded them, ran the
        // countdown out and terminally reset to silence).
        effect.hasTail() shouldBe true
        ctx.reverbSendBuffer.clear()
        ctx.mixBuffer.clear()
        effect.process(ctx)
        ctx.mixBuffer.left.any { it > 1e-6 || it < -1e-6 } shouldBe true
    }

    "a mismatched context cannot make the countdown outrun the network" {
        // The effect is built for 64-frame blocks but driven with a 128-frame context: the drain
        // clamps its processing to 64, so the countdown must tick by 64 too (a countdown
        // outrunning the DSP would fire the terminal reset while the tail is still audible).
        val effect = KatalystReverbEffect(reverb = Reverb(sampleRate), blockFrames = 64).apply {
            configureSize(size = 0.05)
        }
        val ctx = createCtx()

        ctx.reverbSendBuffer.left[0] = 1.0
        effect.process(ctx)
        effect.configureSize(size = 0.0)

        val drainSamples = effect.reverb!!.drainSamplesUntilSilent(peak = effect.reverb!!.combPeakAbs())
        // Enough 128-frame calls that a countdown ticking by ctx.blockFrames would have flipped
        // Off — but the network has only processed HALF that many samples.
        val callsForBuggyFlip = ceil(drainSamples / blockFrames).toInt() + 2

        repeat(callsForBuggyFlip) {
            ctx.reverbSendBuffer.clear()
            ctx.mixBuffer.clear()
            effect.process(ctx)
        }
        effect.hasTail() shouldBe true

        // With the honest 64-sample tick it completes in twice the calls.
        repeat(callsForBuggyFlip + 4) {
            ctx.reverbSendBuffer.clear()
            ctx.mixBuffer.clear()
            effect.process(ctx)
        }
        effect.hasTail() shouldBe false
    }

    "reset restores the factory params — the next orbit life cannot inherit them" {
        // Reverb setters DROP non-finite writes, so a NaN param from the next life would
        // otherwise keep THIS life's value.
        val effect = createEffect(size = 0.8)
        effect.configure(size = 0.8, lowpass = 5000.0)

        effect.reset()

        effect.reverb!!.size shouldBe 0.0
        effect.reverb!!.lowpass shouldBe null
    }

    "a quiet network drains in proportion to its content, not the saturated worst case" {
        val effect = createEffect(size = 0.5)
        val ctx = createCtx()

        // Charge QUIETLY: peak ~1e-3.
        ctx.reverbSendBuffer.left[0] = 0.001
        ctx.reverbSendBuffer.right[0] = 0.001
        effect.process(ctx)

        effect.configureSize(size = 0.0)

        // From 1e-3 at fb 0.84: ceil(ln(1e-5/1e-3)/ln(0.84)) + 1 = 28 revolutions. The worst-case
        // bound (from peak 1.0) would be 68 — assert we are done well before THAT.
        val peakBlocks = ceil(29.0 * 1640.0 / blockFrames).toInt() + 2

        repeat(peakBlocks) {
            ctx.reverbSendBuffer.clear()
            ctx.mixBuffer.clear()
            effect.process(ctx)
        }

        effect.hasTail() shouldBe false
        // Literally zero: proves the TERMINAL RESET fired inside the proportional window — a
        // worst-case countdown (peak 1.0 -> 68 revolutions = 872 blocks) would still be Draining.
        effect.reverb!!.hasTail(0.0) shouldBe false
    }

    "an empty network goes straight to Off — no drain hold on a silent orbit; a charged one reports its tail" {
        // The delay round's engine-leak shape: a drain entered with nothing to drain would hold
        // the orbit (and its PlaybackEngine) alive for the whole countdown.
        val empty = createEffect(size = 0.5)
        // Never processed a send: the combs are all zeros — the off-config lands in Off directly.
        empty.configureSize(size = 0.0)
        empty.hasTail() shouldBe false

        val charged = createEffect(size = 0.5)
        val ctx = createCtx()
        ctx.reverbSendBuffer.left[0] = 1.0
        ctx.reverbSendBuffer.right[0] = 1.0
        charged.process(ctx)
        charged.configureSize(size = 0.0)
        charged.hasTail() shouldBe true
    }

    "the drain is sample-counted: block size changes neither the audio nor the off point" {
        fun runDrain(bf: Int): Pair<DoubleArray, Int> {
            val effect = KatalystReverbEffect(reverb = Reverb(sampleRate), blockFrames = bf).apply {
                configureSize(size = 0.05)
            }
            val ctx = KatalystContext(
                blockFrames = bf,
                mixBuffer = StereoBuffer(bf),
                delaySendBuffer = StereoBuffer(bf),
                reverbSendBuffer = StereoBuffer(bf),
            )

            // 36 revolutions x 1640 samples: the pinned countdown for this run's full-scale
            // impulse at fb 0.714 (see the countdown row) — a constant here because the network
            // is still EMPTY at this point (the peak-based query would answer 0).
            val drainSamples = 36.0 * 1640.0
            val totalSamples = 128 + (ceil(drainSamples / 128.0).toInt() + 4) * 128
            val out = DoubleArray(totalSamples)
            var offAtSample = -1
            var absSample = 0

            while (absSample < totalSamples) {
                ctx.reverbSendBuffer.clear()
                ctx.mixBuffer.clear()

                if (absSample == 0) {
                    ctx.reverbSendBuffer.left[0] = 1.0
                }

                // The takeover lands at the SAME absolute sample for every block size.
                if (absSample == 128) {
                    effect.configureSize(size = 0.0)
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
