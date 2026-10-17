/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.ChainSwap
import io.peekandpoke.klang.audio_be.Crossfade
import io.peekandpoke.klang.audio_be.ReleaseLaw
import io.peekandpoke.klang.audio_be.cylinders.CylinderSwapRig.Companion.dryChain
import io.peekandpoke.klang.audio_be.cylinders.CylinderSwapRig.Companion.roomChain
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystKnobDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import io.peekandpoke.klang.audio_bridge.constants.SILENCE_FLOOR
import kotlin.math.abs
import kotlin.math.pow

/**
 * The decided law of the capped drain, written here from the constants and nothing else, for the
 * oracles of `ChainSwapCapSpec`, `MasterBusTest` and `ChainSwapStateIdentitySpec`: the drain starts
 * on the block after the swap's fade completes, the release on the block after the drain's age
 * first reaches [ChainSwap.MAX_DRAIN_SECONDS], and over the release the leaving chain's output is
 * weighted by [ReleaseLaw]; the chain retires at the end of the block the gain reaches 0.
 */
internal class CapLaw(sampleRate: Int, private val blockFrames: Int) {
    private val law = ReleaseLaw(sampleRate)
    private val fadeFrames = (Crossfade.XFADE_SECONDS * sampleRate).toInt()
    private val capFrames = (ChainSwap.MAX_DRAIN_SECONDS * sampleRate).toInt()

    /** Blocks after the swap block's start at which the release's first block runs. */
    val releaseStart: Int = ceilDiv(fadeFrames) + ceilDiv(capFrames)

    /** Blocks the release runs, the last one retiring the chain. */
    val releaseBlocks: Int = law.floorFrame / blockFrames + 1

    /** The leaving chain's weight at sample [i] of block [b] (counted from the swap). */
    fun weight(b: Int, i: Int): Double = law.gain((b - releaseStart) * blockFrames + i)

    private fun ceilDiv(frames: Int): Int = (frames + blockFrames - 1) / blockFrames
}

/**
 * The capped drain on the orbit (phase 3 step 12 decision (i), maintainer 2026-09-28, option A): a
 * drain older than [ChainSwap.MAX_DRAIN_SECONDS] that still reports a tail has its OUTPUT released
 * exponentially and retires under the floor, so a request parked behind it lands. A room ends on
 * its own first and the cap never touches it. The master's rows are `MasterBusTest`'s.
 *
 * The oracle is a CONTROL rig that keeps the leaving chain in service on the same silent input:
 * its output is the ring the leaving chain drains, sample for sample, and the swapped rig must be
 * that ring times [CapLaw.weight], not a number read from the code under test.
 */
class ChainSwapCapSpec : StringSpec({

    /** A delay that sustains itself: feedback 1 on silence never decays. */
    val loop = KatalystDsl.of(
        KatalystStageDsl.Delay(
            wet = KatalystKnobDsl.Constant(0.5),
            time = KatalystKnobDsl.Constant(0.1),
            feedback = KatalystKnobDsl.Constant(1.0),
        )
    )

    "a self-sustaining chain swapped away, with no request waiting, is released at the cap: exp(-k / tau) from exactly 1, then 0" {
        fun rig() = CylinderSwapRig().apply {
            registry.register("room", roomChain(3.0))
            registry.register("loop", loop)
            registry.register("dry", dryChain(0.9))
        }

        val swapped = rig()
        val control = rig()
        val frames = swapped.blockFrames
        val law = CapLaw(sampleRate = swapped.sampleRate, blockFrames = frames)
        val releaseAt = law.releaseStart
        val releaseBlocks = law.releaseBlocks

        // Both rigs drain a room first, so the loop's drain below is not the swap's first: a drain
        // that inherited this one's age would be cut early.
        for (rig in listOf(swapped, control)) {
            rig.cylinder.requestChain("room")
            rig.render(blocks = 60, level = 0.5)
            rig.cylinder.requestChain("loop")
            rig.render(blocks = rig.fadeBlocks, level = 0.5)
            rig.drainOut(level = 0.5)
            rig.swap.settled shouldBe true
            // Charged: 40 blocks of sine into the loop, which will ring on for ever.
            rig.render(blocks = 40, level = 0.5)
        }

        swapped.cylinder.requestChain("dry")

        val leaving = swapped.swap.leaving.shouldNotBeNull()
        var ring = 0.0

        // No request waits: one would cut the drain (`ChainSwapCutSpec`), and the cap is what this row is about.
        for (b in 0 until releaseAt + releaseBlocks + 30) {
            val got = swapped.block(level = 0.0)
            val want = control.block(level = 0.0)

            for (i in 0 until frames) {
                val weight = law.weight(b = b, i = i)

                if (got[i] != want[i] * weight) {
                    withClue("block $b sample $i (release starts at block $releaseAt): got ${got[i]}, want ${want[i]} x $weight") {
                        got[i] shouldBe want[i] * weight
                    }
                }
            }

            if (b in releaseAt until releaseAt + releaseBlocks) {
                ring = maxOf(ring, want.maxOf { abs(it) })
            }

            // The state after block b: the drain enters the release at the END of the block its
            // age reaches the cap, so the release's first block is the next one.
            when {
                b < releaseAt - 1 -> withClue("block $b: still fading or draining before the cap") {
                    (swapped.swap.isDraining || swapped.swap.isFading) shouldBe true
                }

                b < releaseAt + releaseBlocks - 1 -> withClue("block $b: the release runs, and holds the chain it releases") {
                    swapped.swap.isReleasing shouldBe true
                    swapped.swap.holds(leaving) shouldBe true
                }

                b >= releaseAt + releaseBlocks - 1 -> withClue("block $b: the release's last block retired the chain and forgot it") {
                    swapped.swap.settled shouldBe true
                    swapped.swap.holds(leaving) shouldBe false
                }
            }
        }

        withClue("positive control: the loop still rang at full level through the release, so the release is not vacuous") {
            ring shouldBeGreaterThan 0.05
        }

        withClue("and it rings on in the control: the release, not a decay of its own, is what silenced the swapped rig") {
            control.block(level = 0.0).maxOf { abs(it) } shouldBeGreaterThan 0.05
        }
    }

    "a second capped drain on the same swap releases from exactly 1 again, by the same law" {
        // The release position is data of the state, set by its enter: a second release on the
        // same swap must not continue where the first one stopped. A and B run the same script
        // (a first capped release included), then both put the loop back in service and charge it;
        // only A swaps it away again, so B's loop is the ring A releases, sample for sample.
        fun rig() = CylinderSwapRig().apply {
            registry.register("loop", loop)
            registry.register("dry", dryChain(0.9))
        }

        val a = rig()
        val b = rig()
        val law = CapLaw(sampleRate = a.sampleRate, blockFrames = a.blockFrames)
        val bound = law.releaseStart + law.releaseBlocks + 30

        for (rig in listOf(a, b)) {
            rig.cylinder.requestChain("loop")
            rig.render(blocks = rig.fadeBlocks + 40, level = 0.5)
            rig.cylinder.requestChain("dry")

            var guard = 0
            while (!rig.swap.settled && guard < bound) {
                rig.block(level = 0.0)
                guard++
            }

            withClue("the first capped drain released and settled") {
                rig.swap.settled shouldBe true
                guard shouldBeGreaterThan law.releaseStart
            }

            rig.cylinder.requestChain("loop")
            rig.render(blocks = rig.fadeBlocks + 40, level = 0.5)
        }

        a.cylinder.requestChain("dry")

        var ring = 0.0

        for (blk in 0 until law.releaseStart + law.releaseBlocks + 5) {
            val got = a.block(level = 0.0)
            val want = b.block(level = 0.0)

            for (i in 0 until a.blockFrames) {
                val weight = law.weight(b = blk, i = i)

                if (got[i] != want[i] * weight) {
                    withClue("second release, block $blk sample $i: got ${got[i]}, want ${want[i]} x $weight") {
                        got[i] shouldBe want[i] * weight
                    }
                }
            }

            if (blk >= law.releaseStart) {
                ring = maxOf(ring, want.maxOf { abs(it) })
            }
        }

        withClue("the second release ran to its end, and the ring it released was loud") {
            a.swap.settled shouldBe true
            ring shouldBeGreaterThan 0.05
        }
    }

    "the release at the cap is long and smooth: at least 1 s, no per-sample gain step above 1e-4" {
        // Behaviour, not the constants: this row reads none of them, so a release made short
        // (a click) or shallow (a step to 0) fails here even when the law above moves with it.
        // The gain is read back as the swapped rig over a control that kept the loop.
        fun rig() = CylinderSwapRig(sampleRate = 48000).apply {
            registry.register("loop", loop)
            registry.register("dry", dryChain(0.9))
        }

        val swapped = rig()
        val control = rig()

        for (r in listOf(swapped, control)) {
            r.cylinder.requestChain("loop")
            r.render(blocks = r.fadeBlocks + 40, level = 0.5)
        }

        swapped.cylinder.requestChain("dry")

        // 60 s at 48 kHz: far past any cap this row could be meant to find.
        val guardBlocks = 60 * 48000 / 128
        var blocks = 0
        var releaseBlocks = 0
        var lastGain = 1.0
        var lastAt = 0
        var at = 0
        var worstStep = 0.0

        while (!swapped.swap.settled && blocks < guardBlocks) {
            val got = swapped.block(level = 0.0)
            val want = control.block(level = 0.0)

            if (swapped.swap.isReleasing || releaseBlocks > 0) {
                releaseBlocks++
            }

            for (i in got.indices) {
                if (abs(want[i]) > 0.01) {
                    val gain = got[i] / want[i]
                    val step = abs(gain - lastGain) / maxOf(1, at - lastAt)

                    worstStep = maxOf(worstStep, step)
                    lastGain = gain
                    lastAt = at
                }

                at++
            }

            blocks++
        }

        withClue("the swap settled: the release ended") {
            swapped.swap.settled shouldBe true
        }

        withClue("the release lasted at least 1 s (375 blocks at 48 kHz), and at most 10 s") {
            releaseBlocks shouldBeGreaterThan 375
            releaseBlocks shouldBeLessThan 3750
        }

        withClue("the gain never moved by more than 1e-4 between two samples, its last step to 0 included") {
            worstStep shouldBeLessThan 1e-4
        }

        withClue("positive control: the gain really fell from 1 to 0 across the rig's samples") {
            lastGain shouldBe 0.0
        }
    }

    "the hard cut from mid-release keeps no reference to the chain it cut" {
        val rig = CylinderSwapRig().apply {
            registry.register("loop", loop)
            registry.register("dry", dryChain(0.9))
        }

        rig.cylinder.requestChain("loop")
        rig.render(blocks = rig.fadeBlocks + 40, level = 0.5)
        rig.cylinder.requestChain("dry")

        val leaving = rig.swap.leaving.shouldNotBeNull()
        var guard = 0

        while (!rig.swap.isReleasing && guard < 8000) {
            rig.block(level = 0.0)
            guard++
        }

        rig.swap.isReleasing shouldBe true

        rig.cylinder.retire()

        withClue("retire from mid-release") {
            rig.swap.settled shouldBe true
            rig.swap.holds(leaving) shouldBe false
        }
    }

    /**
     * What a room of [size] did when it was swapped away after 400 blocks at 0.5, against a control
     * that kept it: how long the drain ran, the block after which the control's ring stayed under
     * -90 dBFS for good ([quietFrom], its audible ring-out), and the loudest the control's ring was
     * after the drain ended ([ringAfterEnd], what the retire cut).
     */
    class RoomDrain(
        val blocks: Int,
        val capBlocks: Int,
        val released: Boolean,
        val settled: Boolean,
        val worst: Double,
        val quietFrom: Int,
        val ringAfterEnd: Double,
    )

    fun drainRoom(size: Double, hall: KatalystDsl = roomChain(size)): RoomDrain {
        fun rig() = CylinderSwapRig().apply {
            registry.register("hall", hall)
            registry.register("dry", dryChain(0.9))
        }

        val swapped = rig()
        val control = rig()
        val capBlocks = (ChainSwap.MAX_DRAIN_SECONDS * swapped.sampleRate).toInt() / swapped.blockFrames
        val inaudible = 10.0.pow(-90.0 / 20.0)

        for (rig in listOf(swapped, control)) {
            rig.cylinder.requestChain("hall")
            rig.render(blocks = 400, level = 0.5)
        }

        swapped.cylinder.requestChain("dry")

        var blocks = 0
        var released = false
        var worst = 0.0
        var quietFrom = 0

        while (!swapped.swap.settled && blocks < capBlocks * 2) {
            val got = swapped.block(level = 0.0)
            val want = control.block(level = 0.0)

            for (i in got.indices) {
                worst = maxOf(worst, abs(got[i] - want[i]))

                if (abs(want[i]) >= inaudible) {
                    quietFrom = blocks + 1
                }
            }

            released = released || swapped.swap.isReleasing
            blocks++
        }

        // The control rings on: where it falls under -90 dBFS for good, and how loud it still was.
        var ringAfterEnd = 0.0

        for (b in blocks until capBlocks * 2) {
            val want = control.block(level = 0.0)

            for (i in want.indices) {
                ringAfterEnd = maxOf(ringAfterEnd, abs(want[i]))

                if (abs(want[i]) >= inaudible) {
                    quietFrom = b + 1
                }
            }
        }

        return RoomDrain(
            blocks = blocks,
            capBlocks = capBlocks,
            released = released,
            settled = swapped.swap.settled,
            worst = worst,
            quietFrom = quietFrom,
            ringAfterEnd = ringAfterEnd,
        )
    }

    "a room swapped away drains to its own end: the cap never touches it" {
        val drain = drainRoom(9.0)

        withClue("the room drained and settled on its own, never released") {
            drain.released shouldBe false
            drain.settled shouldBe true
            drain.blocks shouldBeLessThan drain.capBlocks
            drain.blocks shouldBeGreaterThan drain.capBlocks / 4
        }

        withClue("and the swapped rig was the room's own ring-out, sample for sample") {
            drain.worst shouldBe 0.0
        }
    }

    "the largest room (size 10) drains to its own end near its audible ring-out, well before the cap" {
        // Before 2026-10-17 its tail CEILING, conservative and held to a comb-cell floor about 20 dB
        // under the room's output floor, kept this drain open to the cap (20 s) and beyond: the
        // render `tmp/listening/chain-swap-drain/second-edit-waits.wav` waited 22.6 s. The drain now
        // measures the room (`ChainSwap.TAIL_MEASURE_SECONDS`) against the drain's output-referred floor
        // (`Reverb.TAIL_FLOOR`, about -90 dBFS at the output; ordinary playback keeps SILENCE_FLOOR).
        val drain = drainRoom(10.0)
        val sampleRate = 44100.0
        val second = (sampleRate / 128).toInt()

        withClue("the room drained and settled on its own, never released, well before the cap") {
            drain.released shouldBe false
            drain.settled shouldBe true
            drain.blocks shouldBeLessThan drain.capBlocks - 2 * second
        }

        // Two answers end the drain: the room's ceiling under its floor, and the chain's output block (both
        // channels) under SILENCE_FLOOR. Here, measured: the ring (left) under -90 dBFS for good at 13.2 s, the
        // drain's end at 17.2 s; the retire cut -106 dBFS. The floor takes the comb sum as coherent, and this
        // rig's two pure tones partly cancel in it (about 0.02 x the comb peak), so it errs long.
        withClue("it ended within 5 s of its ring falling under -90 dBFS for good, and cut nothing above -90 dBFS") {
            drain.blocks shouldBeLessThan drain.quietFrom + 5 * second
            drain.ringAfterEnd shouldBeLessThan 10.0.pow(-90.0 / 20.0)
        }

        withClue("and up to its end the swapped rig was the room's own ring-out, sample for sample") {
            drain.worst shouldBe 0.0
        }

        withClue("positive control: the room really rang for many seconds") {
            drain.quietFrom shouldBeGreaterThan 8 * second
        }
    }

    "a size-10 room inside a parallel branch drains like one in the chain, well before the cap" {
        // The branch measures its own stages (`KatalystParallelEffect.measureTail`).
        val drain = drainRoom(size = 10.0, hall = KatalystDsl.of(KatalystStageDsl.Parallel(branches = listOf(roomChain(10.0)))))
        val second = 44100 / 128

        withClue("the room drained and settled on its own, never released, well before the cap") {
            drain.released shouldBe false
            drain.settled shouldBe true
            drain.blocks shouldBeLessThan drain.capBlocks - 2 * second
            drain.worst shouldBe 0.0
        }
    }

    "a gain after the room does not raise the retire's step: the drain ends only once the chain's output is under the silence floor" {
        // The stages answer for what they hold, not for what follows them: before the output gate a size-3 room
        // followed by +30 dB retired with the chain's output at -69 dBFS (review, 2026-10-17).
        val loud = KatalystDsl.of(
            KatalystStageDsl.Reverb(wet = KatalystKnobDsl.Constant(0.5), size = KatalystKnobDsl.Constant(3.0)),
            KatalystStageDsl.Gain(gain = KatalystKnobDsl.Constant(31.62)),
        )
        val rig = CylinderSwapRig().apply {
            registry.register("loud", loud)
            registry.register("dry", dryChain(0.9))
            cylinder.requestChain("loud")
            render(blocks = 300, level = 0.5)
        }

        rig.cylinder.requestChain("dry")

        var lastPeak = 0.0
        var loudest = 0.0
        var blocks = 0

        while (!rig.swap.settled && blocks < 20000) {
            lastPeak = rig.block(level = 0.0).maxOf { abs(it) }
            loudest = maxOf(loudest, lastPeak)
            blocks++
        }

        withClue("the drain ended on its own") {
            rig.swap.settled shouldBe true
            blocks shouldBeLessThan (ChainSwap.MAX_DRAIN_SECONDS * rig.sampleRate).toInt() / rig.blockFrames
        }

        withClue("its last block, the step the retire leaves, was under the silence floor: $lastPeak") {
            lastPeak shouldBeLessThan SILENCE_FLOOR
        }

        withClue("positive control: the chain rang loud") {
            loudest shouldBeGreaterThan 0.1
        }
    }
})
