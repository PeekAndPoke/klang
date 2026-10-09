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
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import kotlin.math.abs

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
            wet = IgnitorDsl.Constant(0.5),
            time = IgnitorDsl.Constant(0.1),
            feedback = IgnitorDsl.Constant(1.0),
        )
    )

    "a self-sustaining chain swapped away is released at the cap: exp(-k / tau) from exactly 1, then 0, and the parked request lands" {
        fun rig() = CylinderSwapRig().apply {
            registry.register("room", roomChain(3.0))
            registry.register("loop", loop)
            registry.register("dry", dryChain(0.9))
            registry.register("late", dryChain(1.1))
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
        var parkedAt = -1

        for (b in 0 until releaseAt + releaseBlocks + 30) {
            if (b == 100) {
                // Parked behind the drain; latest wins, and it is all that is ever asked for.
                swapped.cylinder.requestChain("late")
                parkedAt = b
            }

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

                b == releaseAt + releaseBlocks - 1 -> withClue("block $b: the release's last block retired the chain and forgot it") {
                    swapped.swap.settled shouldBe true
                    swapped.swap.holds(leaving) shouldBe false
                }

                b == releaseAt + releaseBlocks -> withClue("block $b: the parked request landed on the next block's poll") {
                    swapped.swap.isFading shouldBe true
                }
            }
        }

        withClue("the request really was parked (the loop was draining then)") {
            parkedAt shouldBe 100
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

    /** What a room of [size] did when it was swapped away after 400 blocks at 0.5, against a control that kept it. */
    class RoomDrain(val blocks: Int, val capBlocks: Int, val released: Boolean, val settled: Boolean, val worst: Double)

    fun drainRoom(size: Double): RoomDrain {
        fun rig() = CylinderSwapRig().apply {
            registry.register("hall", roomChain(size))
            registry.register("dry", dryChain(0.9))
        }

        val swapped = rig()
        val control = rig()
        val capBlocks = (ChainSwap.MAX_DRAIN_SECONDS * swapped.sampleRate).toInt() / swapped.blockFrames

        for (rig in listOf(swapped, control)) {
            rig.cylinder.requestChain("hall")
            rig.render(blocks = 400, level = 0.5)
        }

        swapped.cylinder.requestChain("dry")

        var blocks = 0
        var released = false
        var worst = 0.0

        while (!swapped.swap.settled && blocks < capBlocks * 2) {
            val got = swapped.block(level = 0.0)
            val want = control.block(level = 0.0)

            for (i in got.indices) {
                worst = maxOf(worst, abs(got[i] - want[i]))
            }

            released = released || swapped.swap.isReleasing
            blocks++
        }

        return RoomDrain(blocks = blocks, capBlocks = capBlocks, released = released, settled = swapped.swap.settled, worst = worst)
    }

    "a room swapped away drains to its own end: the cap never touches it" {
        // Size 9 rings out on its own in about 10 s here (its tail ceiling reaching -100 dBFS).
        val drain = drainRoom(9.0)

        withClue("the room drained and settled on its own, never released") {
            drain.released shouldBe false
            drain.settled shouldBe true
            drain.blocks shouldBeLessThan drain.capBlocks
            drain.blocks shouldBeGreaterThan drain.capBlocks / 3
        }

        withClue("and the swapped rig was the room's own ring-out, sample for sample") {
            drain.worst shouldBe 0.0
        }
    }

    "the largest room (size 10) can outlast the cap, and loses nothing above -100 dBFS to it" {
        // Size 10's comb feedback is 0.98, and its tail CEILING (conservative by up to 2x, see
        // `TailCeiling`) can take longer than the cap to cross -100 dBFS: measured, the drain of
        // this charge reached the cap and the release differed from the room's own ring-out by
        // under 1e-6, below half a 16-bit step. The row pins the level, not whether the cap fires.
        val drain = drainRoom(10.0)

        withClue("the swap settled, released or not") {
            drain.settled shouldBe true
        }

        withClue("the largest difference from the room's own ring-out is below -100 dBFS") {
            drain.worst shouldBeLessThan 1e-5
        }
    }
})
