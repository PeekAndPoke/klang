/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.effects.DelayLine
import io.peekandpoke.klang.audio_bridge.constants.DELAY_CAP
import io.peekandpoke.klang.audio_bridge.constants.DELAY_FEEDBACK
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
            configure(time = delayTime, feedback = feedback, cap = 1.0)
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

        effect.delayLine!!.time shouldBe 0.5
        effect.delayLine!!.feedback shouldBe 0.3

        effect.delayLine!!.time = 1.0
        effect.delayLine!!.time shouldBe 1.0
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

        drained.configure(time = 0.0, feedback = 0.0, cap = 1.0)

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

    "a delay that returns mid-drain keeps the ring AND the tail ceiling" {
        // The Draining -> Active edge, and the one non-obvious decision of this state machine:
        // coming back re-arms NOTHING. The ring keeps its content (the echo train continues under
        // the new tap, send-delay semantics) and the tail ceiling keeps its value, so the orbit
        // still reports a tail while the ring is audibly ringing.
        //
        // What a converter who reads "enter resets the target's fields" as a rule would write is
        // `activeTail.reset()` in the Active entry, and the cost is a cut echo train: a new owner
        // claims the orbit while its own voice is still in its attack and sends nothing, the
        // ceiling reads empty, `Cylinder.tryDeactivate` resets the chain and the tail stops dead.
        //
        // The oracle for the audio half is the same shape as the drain row above: an effect whose
        // owner never left, fed silent sends. That comparison is BIT-exact by construction, and
        // the reason is worth stating, because it is what makes the row an identity and not an
        // approximation: `DelayLine.process` reads its input samples, the three knobs and its own
        // ring, and nothing else. Draining feeds it `silentInput` while Active feeds it the send
        // buffer, but with a silent send both are all-zero arrays of the same length, both sides
        // run the same frame count, and the re-`configure` writes the SAME three numbers into
        // plain field setters, so neither ring can diverge by a bit.
        val ref = createEffect(delayTime = 0.4, feedback = 0.7)
        val refCtx = createCtx()
        val returning = createEffect(delayTime = 0.4, feedback = 0.7)
        val returningCtx = createCtx()

        // One hot block charges both rings identically.
        refCtx.delaySendBuffer.left[0] = 1.0
        refCtx.delaySendBuffer.right[0] = 1.0
        returningCtx.delaySendBuffer.left[0] = 1.0
        returningCtx.delaySendBuffer.right[0] = 1.0
        ref.process(refCtx)
        returning.process(returningCtx)

        // The owner leaves for about 170 ms, well before the first echo is due at 0.4 s.
        returning.configure(time = 0.0, feedback = 0.0, cap = 1.0)

        repeat(60) {
            refCtx.delaySendBuffer.clear()
            refCtx.mixBuffer.clear()
            ref.process(refCtx)

            returningCtx.delaySendBuffer.clear()
            returningCtx.mixBuffer.clear()
            returning.process(returningCtx)
        }

        // A new owner with the same knobs claims the orbit while the ring is still charged.
        returning.configure(time = 0.4, feedback = 0.7, cap = 1.0)

        // Long enough that the first echo (at 0.4 s, block 138) is inside the window: a row that
        // stopped before it would compare two silences and prove nothing.
        var maxDiff = 0.0
        var tailAlways = true
        var loudest = 0.0

        repeat(200) {
            refCtx.delaySendBuffer.clear()
            refCtx.mixBuffer.clear()
            ref.process(refCtx)

            returningCtx.delaySendBuffer.clear()
            returningCtx.mixBuffer.clear()
            returning.process(returningCtx)

            tailAlways = tailAlways && returning.hasTail()

            for (i in 0 until blockFrames) {
                maxDiff = maxOf(maxDiff, abs(refCtx.mixBuffer.left[i] - returningCtx.mixBuffer.left[i]))
                maxDiff = maxOf(maxDiff, abs(refCtx.mixBuffer.right[i] - returningCtx.mixBuffer.right[i]))
                loudest = maxOf(
                    loudest,
                    abs(returningCtx.mixBuffer.left[i]),
                    abs(returningCtx.mixBuffer.right[i]),
                )
            }
        }

        // (a) the ceiling survived the drain: the orbit is never told the ring is empty.
        withClue("the tail ceiling must survive Draining -> Active") { tailAlways shouldBe true }
        // (b) the ring survived it too, sample for sample.
        withClue("the ring must survive Draining -> Active") { maxDiff shouldBe 0.0 }
        // Not two silences: the echo really came out inside the window.
        withClue("the echo must be inside the compared window") { (loudest > 0.5) shouldBe true }
    }

    "a life that ended in Off starts the next one with an empty ceiling" {
        // `Off.enter()` forgets the tail ceiling, and that one line is load-bearing with nothing
        // else behind it: the Off arm of `hasTail` is a hardcoded false, so a stale ceiling is
        // invisible for as long as the effect stays Off and only surfaces in the NEXT life, where
        // `Active.hasTail()` reads it. A converter who writes `Off.enter() { state = this }` keeps
        // every other row green and hands the next owner a tail that is not there: at a big room's
        // comb feedback that holds the orbit about 20 s past its due, and at a delay feedback of
        // 1 or more it pins the orbit for ever.
        //
        // The numbers, verified against `TailCeiling.observe` rather than assumed. One window here
        // is `delaySamples + 1` = 2206 samples and `lapsPerWindow` is 2, so four blocks of 0.8
        // leave `current = 0.8 * (1 + 0.6) = 1.28` with only 4 x 128 = 512 samples elapsed: no
        // window boundary is crossed, nothing decays, and 1.28 is far above the 1e-5 silence
        // threshold. So with the reset deleted the fresh life reports a tail, and with it in place
        // the ceiling is 0.
        val effect = createEffect(delayTime = 0.05, feedback = 0.6)
        val ctx = createCtx()

        repeat(4) {
            ctx.delaySendBuffer.fill(0.8)
            ctx.mixBuffer.clear()
            effect.process(ctx)
        }

        effect.configure(time = 0.0, feedback = 0.0, cap = 1.0)

        var blocks = 0

        while (effect.hasTail() && blocks < 100_000) {
            ctx.delaySendBuffer.clear()
            ctx.mixBuffer.clear()
            effect.process(ctx)
            blocks++
        }

        withClue("the drain must reach Off, or the row never tests the entry into Off") {
            effect.hasTail() shouldBe false
        }

        // A new owner, a new life, and the question is asked BEFORE any block is processed
        // because that is the strictest moment. The REASON is NOT that a block would hide the bug:
        // against `TailCeiling.observe` that is false, because `inputPeakInWindow` is a running max
        // within the window, so a silent block on a stale ceiling still recomputes
        // `fresh(0.8, fb, 2)` and answers true. Simulated against a line-for-line port of
        // `observe`: with the reset deleted the stale answer survives 31 silent blocks at the
        // feedback 0.0 used here, about 427 at 0.6, and for ever at 1.2. That is exactly why a leak
        // of this shape is long, and why the row asks twice.
        effect.configure(time = 0.05, feedback = 0.0, cap = 1.0)

        withClue("a fresh life must not inherit the previous life's tail ceiling") {
            effect.hasTail() shouldBe false
        }

        // Ten silent blocks, because ten is when production asks: `Cylinder` polls the chain's
        // tail only after `silentBlocksBeforeTailCheck` silent blocks, and its default is 10.
        repeat(10) {
            ctx.delaySendBuffer.clear()
            ctx.mixBuffer.clear()
            effect.process(ctx)
        }

        withClue("and still empty ten silent blocks later, which is when the cylinder asks") {
            effect.hasTail() shouldBe false
        }

        // Positive control: the fresh life really is Active and really does answer this question,
        // so the two `false`s above are an empty ceiling and not a dead effect.
        ctx.delaySendBuffer.fill(0.8)
        ctx.mixBuffer.clear()
        effect.process(ctx)

        withClue("one loud block must make the fresh life report a tail") {
            effect.hasTail() shouldBe true
        }
    }

    "the countdown a drain runs on is its own, never the previous life's remains" {
        // The countdown belongs to the Draining state: its `enter` is the only thing that
        // INITIALISES it (its `process` advances it, nothing else touches it at all), so a life
        // that ends mid-drain cannot lend its leftover to the next. That is what made THREE writes
        // of the old flag version dead code: `drainRemaining = 0.0` in `reset` and in `release`,
        // and the field write on the arm that went straight to Off.
        val effect = createEffect(delayTime = 0.02, feedback = 0.2)
        val ctx = createCtx()

        // A SHORT first drain: a quiet ring at a low feedback is inaudible within a few periods.
        ctx.delaySendBuffer.left[0] = 0.001
        ctx.delaySendBuffer.right[0] = 0.001
        effect.process(ctx)

        effect.configure(time = 0.0, feedback = 0.0, cap = 1.0)

        // How long that first drain would have been. Read it as a LOWER BOUND for the wait below,
        // not as an expected value: it comes from the production helpers, so it cannot be their
        // oracle, and the `1..200` clue is what bounds it. By hand, so the number is checkable
        // without running anything: the ring holds one 0.001 impulse, the feedback is 0.2 and one
        // period is 0.02 s = 882 samples, so
        //   periods = ceil(ln(1e-5 / 1e-3) / ln(0.2)) = ceil(2.861) = 3, plus one slack period,
        //   4 x 882 = 3528 samples = 28 blocks of 128.
        // The second life's own countdown is ceil(ln(1e-5 / 0.989) / ln(0.9)) + 1 = 111 periods of
        // 0.5 s, about 2.4 million samples: three orders of magnitude apart, which is the gap the
        // row measures.
        val shortDrainBlocks = ceil(
            effect.delayLine!!.drainSamplesUntilSilent(peak = effect.delayLine!!.tapWindowPeakAbs()) / blockFrames
        ).toInt()
        withClue("the first drain must be short, or the row proves nothing") {
            (shortDrainBlocks in 1..200) shouldBe true
        }

        // Spend one block of it, then throw the whole life away mid-drain.
        ctx.delaySendBuffer.clear()
        ctx.mixBuffer.clear()
        effect.process(ctx)
        effect.hasTail() shouldBe true

        effect.reset()
        effect.hasTail() shouldBe false

        // The next life: a long, loud tail whose honest countdown is far longer than what the
        // previous life had left.
        effect.configure(time = 0.5, feedback = 0.9, cap = 1.0)
        ctx.delaySendBuffer.fill(1.0)
        ctx.mixBuffer.clear()
        effect.process(ctx)
        effect.configure(time = 0.0, feedback = 0.0, cap = 1.0)

        // A countdown carried over from the previous life would be spent within these blocks.
        repeat(shortDrainBlocks + 8) {
            ctx.delaySendBuffer.clear()
            ctx.mixBuffer.clear()
            effect.process(ctx)
        }

        withClue("the second drain must still be running on its OWN countdown") {
            effect.hasTail() shouldBe true
        }
    }

    "the countdown ends: ring literally empty, processing short-circuits" {
        val effect = createEffect(delayTime = 0.05, feedback = 0.5)
        val ctx = createCtx()

        ctx.delaySendBuffer.left[0] = 1.0
        ctx.delaySendBuffer.right[0] = 1.0
        effect.process(ctx)

        effect.configure(time = 0.0, feedback = 0.0, cap = 1.0)

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

        effect.configure(time = 0.0, feedback = 0.0, cap = 1.0)

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
        effect.configure(time = 8.0, feedback = 0.3, cap = 1.0)

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

        effect.configure(time = 0.0, feedback = 0.0, cap = 1.0)

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
            configure(time = 0.05, feedback = 0.5, cap = 1.0)
        }
        val ctx = createCtx()

        ctx.delaySendBuffer.left[0] = 1.0
        effect.process(ctx)
        effect.configure(time = 0.0, feedback = 0.0, cap = 1.0)

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

    "a non-finite feedback or cap is the shared default, never the previous owner's value" {
        // DelayLine's setters DROP non-finite writes, so passing a NaN through would leave the last
        // owner's feedback (here a self-oscillating 1.2) and cap in force.
        val effect = createEffect(delayTime = 0.3, feedback = 1.2)
        effect.configure(time = 0.3, feedback = 1.2, cap = 3.0)

        effect.configure(time = 0.3, feedback = Double.NaN, cap = Double.POSITIVE_INFINITY)

        effect.delayLine!!.feedback shouldBe DELAY_FEEDBACK
        effect.delayLine!!.cap shouldBe DELAY_CAP
    }

    "a non-finite time reads as off, never as the previous owner's delay" {
        // +Inf is the sharp half: `Inf >= 0.01` is TRUE, so without the isFinite test it is an
        // active config whose ring is refused, and the orbit keeps sounding the previous time.
        for (bad in listOf(Double.POSITIVE_INFINITY, Double.NaN)) {
            val poisoned = createEffect(delayTime = 0.05, feedback = 0.5)
            val off = createEffect(delayTime = 0.05, feedback = 0.5)
            val poisonedCtx = createCtx()
            val offCtx = createCtx()

            poisoned.configure(time = bad, feedback = 0.5, cap = 1.0)
            off.configure(time = 0.0, feedback = 0.5, cap = 1.0)

            var maxDiff = 0.0

            repeat(40) {
                poisonedCtx.delaySendBuffer.fill(0.6)
                poisonedCtx.mixBuffer.clear()
                poisoned.process(poisonedCtx)

                offCtx.delaySendBuffer.fill(0.6)
                offCtx.mixBuffer.clear()
                off.process(offCtx)

                for (i in 0 until blockFrames) {
                    maxDiff = maxOf(maxDiff, abs(poisonedCtx.mixBuffer.left[i] - offCtx.mixBuffer.left[i]))
                }
            }

            withClue("time = $bad") { maxDiff shouldBe 0.0 }
        }
    }

    "reset restores the factory params — the next orbit life cannot inherit them" {
        // DelayLine setters DROP non-finite writes, so a NaN param from the next life would
        // otherwise keep THIS life's value (e.g. a dead owner's self-oscillating feedback).
        val effect = createEffect(delayTime = 0.5, feedback = 1.2)

        effect.reset()

        effect.delayLine!!.time shouldBe 0.0
        effect.delayLine!!.feedback shouldBe 0.0
        effect.delayLine!!.cap shouldBe DELAY_CAP
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

        effect.configure(time = 0.0, feedback = 0.0, cap = 1.0)

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
        empty.configure(time = 0.0, feedback = 0.0, cap = 1.0)
        empty.hasTail() shouldBe false

        // A charged ring, same infinite drain: the tail is real and must be reported.
        val charged = createEffect(delayTime = 0.05, feedback = 1.2)
        val ctx = createCtx()
        ctx.delaySendBuffer.left[0] = 1.0
        ctx.delaySendBuffer.right[0] = 1.0
        charged.process(ctx)
        charged.configure(time = 0.0, feedback = 0.0, cap = 1.0)
        charged.hasTail() shouldBe true
    }

    "the drain is sample-counted: block size changes neither the audio nor the off point" {
        fun runDrain(bf: Int): Pair<DoubleArray, Int> {
            val effect = KatalystDelayEffect(
                delayLine = DelayLine(maxDelaySeconds = 10.0, sampleRate = sampleRate),
                blockFrames = bf,
            ).apply {
                configure(time = 0.05, feedback = 0.5, cap = 1.0)
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
                    effect.configure(time = 0.0, feedback = 0.0, cap = 1.0)
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
