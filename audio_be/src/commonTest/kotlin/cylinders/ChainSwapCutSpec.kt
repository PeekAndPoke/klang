/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.ChainSwap
import io.peekandpoke.klang.audio_be.ReleaseLaw
import io.peekandpoke.klang.audio_be.cylinders.CylinderSwapRig.Companion.dryChain
import io.peekandpoke.klang.audio_be.cylinders.CylinderSwapRig.Companion.roomChain
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystKnobDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

/**
 * The decided law of the cut (maintainer, 2026-10-17), written here from the constant and nothing
 * else, for the oracles of `ChainSwapCutSpec` and `MasterBusTest`: [k] frames into the cut, the
 * leaving chain's output is weighted by `start * 0.5 * (1 + cos(pi * k / n))`, n the cut's length,
 * and by 0 from n on; the chain retires at the end of the block that reaches n.
 */
internal class CutLaw(sampleRate: Int, private val blockFrames: Int) {
    /** The cut's length in frames. */
    val frames: Int = (ChainSwap.CUT_SECONDS * sampleRate).toInt()

    /** Blocks the cut runs, the last one retiring the chain. */
    val blocks: Int = (frames + blockFrames - 1) / blockFrames

    /** The weight [k] frames into a cut that started at [start]. */
    fun weight(k: Int, start: Double = 1.0): Double = if (k >= frames) 0.0 else start * 0.5 * (1.0 + cos(PI * k / frames))
}

/**
 * The cut (maintainer, 2026-10-17, "cut the drain" in KLANG-00318 (chain swap request during drain)):
 * a request that waits behind a ring-out no longer waits for it. The leaving chain's output is faded
 * out over [ChainSwap.CUT_SECONDS] and the request lands on the next poll; a request during the fade
 * cuts where the fade ends; with no request the drain rings out in full. The master's rows are
 * `MasterBusTest`'s.
 *
 * The oracle is a CONTROL rig given the same script without the request: on silent input its output
 * is the ring the swapped rig's leaving chain plays, sample for sample, and the swapped rig must be
 * that ring times [CutLaw.weight].
 */
class ChainSwapCutSpec : StringSpec({

    /** A delay that sustains itself: feedback 1 on silence never decays. */
    val loop = KatalystDsl.of(
        KatalystStageDsl.Delay(
            wet = KatalystKnobDsl.Constant(0.5),
            time = KatalystKnobDsl.Constant(0.1),
            feedback = KatalystKnobDsl.Constant(1.0),
        )
    )

    /** A rig with a long room in service, charged loud, then swapped to a dry chain and draining. */
    fun drainingRoom(sampleRate: Int = 44100): CylinderSwapRig = CylinderSwapRig(sampleRate = sampleRate).apply {
        registry.register("room", roomChain(8.0))
        registry.register("dry", dryChain(0.9))
        registry.register("late", dryChain(1.1))
        cylinder.requestChain("room")
        render(blocks = 300, level = 0.5)
        cylinder.requestChain("dry")
        render(blocks = fadeBlocks + 20, level = 0.0)
        swap.isDraining shouldBe true
    }

    /**
     * Runs [swapped] against [control] on silence for [blocks] blocks and asserts the swapped rig is
     * the control's ring times [weight] (the frame index counted from the first block), sample for
     * sample. Returns the largest ring the control played over those blocks.
     */
    fun followsLaw(
        swapped: CylinderSwapRig,
        control: CylinderSwapRig,
        blocks: Int,
        tolerance: Double = 0.0,
        afterBlock: (Int) -> Unit = {},
        weight: (Int) -> Double,
    ): Double {
        var ring = 0.0

        for (b in 0 until blocks) {
            val got = swapped.block(level = 0.0)
            val want = control.block(level = 0.0)

            for (i in got.indices) {
                val expected = want[i] * weight(b * swapped.blockFrames + i)

                if (abs(got[i] - expected) > tolerance) {
                    withClue("block $b sample $i: got ${got[i]}, want ${want[i]} x ${weight(b * swapped.blockFrames + i)}") {
                        got[i] shouldBe (expected plusOrMinus tolerance)
                    }
                }

                ring = maxOf(ring, abs(want[i]))
            }

            afterBlock(b)
        }

        return ring
    }

    "a request during the drain cuts it: the ring times a raised cosine from 1 to 0, and the request lands on the next poll" {
        val swapped = drainingRoom()
        val control = drainingRoom()
        val law = CutLaw(sampleRate = swapped.sampleRate, blockFrames = swapped.blockFrames)
        var landedAt = -1

        swapped.cylinder.requestChain("late")

        withClue("the request cut the drain at once") {
            swapped.swap.isCutting shouldBe true
        }

        val ring = followsLaw(
            swapped = swapped,
            control = control,
            blocks = law.blocks + 5,
            afterBlock = { b ->
                when {
                    b < law.blocks - 1 -> withClue("block $b: the cut runs") { swapped.swap.isCutting shouldBe true }
                    b == law.blocks - 1 -> withClue("block $b: the cut's last block retired the chain") {
                        swapped.swap.settled shouldBe true
                    }
                }

                if (landedAt < 0 && swapped.swap.isFading) {
                    landedAt = b
                }
            },
            weight = { k -> law.weight(k) },
        )

        withClue("the request landed on the block after the cut, a fifth of a second at most after it was made") {
            landedAt shouldBe law.blocks
            (landedAt * swapped.blockFrames.toDouble() / swapped.sampleRate) shouldBeLessThan 0.25
        }

        withClue("positive control: the room rang loud when it was cut, and rings on in the control") {
            ring shouldBeGreaterThan 0.01
            control.block(level = 0.0).maxOf { abs(it) } shouldBeGreaterThan 0.001
        }
    }

    "the cut is click-free and short: 100 to 250 ms, no step in the output beyond the ring's own" {
        // Behaviour, not the constant: this row reads none of them, so a cut made abrupt (a click)
        // or long (latency) fails here even when the law above moves with it.
        val swapped = drainingRoom(sampleRate = 48000)
        val control = drainingRoom(sampleRate = 48000)
        var previousGot = 0.0
        var previousWant = 0.0
        var worstExtraStep = 0.0
        var cutFrames = 0
        var guard = 0

        swapped.cylinder.requestChain("late")

        while (!swapped.swap.settled && guard < 1000) {
            val got = swapped.block(level = 0.0)
            val want = control.block(level = 0.0)

            for (i in got.indices) {
                val step = abs(got[i] - previousGot)
                val ownStep = abs(want[i] - previousWant)

                worstExtraStep = maxOf(worstExtraStep, step - ownStep)

                previousGot = got[i]
                previousWant = want[i]
            }

            cutFrames += got.size
            guard++
        }

        // The first block after the cut: the leaving chain is gone, and silence is all that is left.
        val after = swapped.block(level = 0.0)

        withClue("the cut ended") {
            swapped.swap.isCutting shouldBe false
        }

        withClue("the cut lasted 100 to 250 ms") {
            cutFrames shouldBeGreaterThan 4800
            cutFrames shouldBeLessThan 12000
        }

        withClue("no sample moved by more than 1e-4 beyond the ring's own step, the last one to silence included") {
            worstExtraStep shouldBeLessThan 1e-4
            abs(after[0] - previousGot) shouldBeLessThan 1e-4
        }

        withClue("positive control: the ring was loud enough that a hard cut would have been a click") {
            control.block(level = 0.0).maxOf { abs(it) } shouldBeGreaterThan 0.001
        }
    }

    "a request during the release cuts it too, from the release's gain" {
        fun rig() = CylinderSwapRig().apply {
            registry.register("loop", loop)
            registry.register("dry", dryChain(0.9))
            registry.register("late", dryChain(1.1))
            cylinder.requestChain("loop")
            render(blocks = fadeBlocks + 40, level = 0.5)
        }

        val swapped = rig()
        val control = rig()
        val law = CutLaw(sampleRate = swapped.sampleRate, blockFrames = swapped.blockFrames)
        val release = ReleaseLaw(swapped.sampleRate)

        swapped.cylinder.requestChain("dry")

        // Into the release, and 200 blocks into it.
        var releasedBlocks = 0
        var guard = 0

        while (releasedBlocks < 200 && guard < 10000) {
            val releasing = swapped.swap.isReleasing

            swapped.block(level = 0.0)
            control.block(level = 0.0)

            if (releasing) {
                releasedBlocks++
            }

            guard++
        }

        swapped.swap.isReleasing shouldBe true

        val start = release.gain(releasedBlocks * swapped.blockFrames)

        swapped.cylinder.requestChain("late")

        withClue("the request cut the release") {
            swapped.swap.isCutting shouldBe true
        }

        val ring = followsLaw(
            swapped = swapped,
            control = control,
            blocks = law.blocks + 3,
            tolerance = 1e-12,
            weight = { k -> law.weight(k = k, start = start) },
        )

        withClue("the cut ended in its last block, and the request landed on the next poll") {
            swapped.swap.isFading shouldBe true
        }

        withClue("positive control: the release was still well above its floor, and the loop rang loud") {
            start shouldBeGreaterThan 0.1
            ring shouldBeGreaterThan 0.05
        }
    }

    "a request during the fade waits for the fade, then the leaving tail is cut, not drained" {
        fun rig() = CylinderSwapRig().apply {
            registry.register("room", roomChain(8.0))
            registry.register("dry", dryChain(0.9))
            registry.register("late", dryChain(1.1))
            cylinder.requestChain("room")
            render(blocks = 300, level = 0.5)
            cylinder.requestChain("dry")
            render(blocks = 3, level = 0.0)
            swap.isFading shouldBe true
        }

        val swapped = rig()
        val control = rig()
        val law = CutLaw(sampleRate = swapped.sampleRate, blockFrames = swapped.blockFrames)

        swapped.cylinder.requestChain("late")

        withClue("the fade runs on") {
            swapped.swap.isFading shouldBe true
        }

        // The fade, unchanged: the swapped rig is the control, sample for sample. The block that
        // ends it runs the entered state's block too: the cut's first block for the swapped rig,
        // the drain's for the control.
        var fadeBlocks = 0
        var ended = false

        while (!ended && fadeBlocks < 100) {
            val got = swapped.block(level = 0.0)
            val want = control.block(level = 0.0)

            if (swapped.swap.isFading) {
                withClue("fade block $fadeBlocks") {
                    got.contentEquals(want) shouldBe true
                }
            } else {
                for (i in got.indices) {
                    withClue("the block that ended the fade, sample $i: the cut's first block") {
                        got[i] shouldBe want[i] * law.weight(i)
                    }
                }

                ended = true
            }

            fadeBlocks++
        }

        withClue("where the fade ended the tail went into the cut, not the drain (the control drains)") {
            swapped.swap.isCutting shouldBe true
            control.swap.isDraining shouldBe true
            fadeBlocks shouldBeLessThan 100
        }

        var landedAt = -1

        followsLaw(
            swapped = swapped,
            control = control,
            blocks = law.blocks + 3,
            afterBlock = { b ->
                if (landedAt < 0 && swapped.swap.isFading) {
                    landedAt = b
                }
            },
            weight = { k -> law.weight(k + swapped.blockFrames) },
        )

        withClue("the request landed one cut after the fade") {
            landedAt shouldBe law.blocks - 1
        }
    }

    "with no request waiting the drain rings out in full, sample for sample" {
        fun rig() = CylinderSwapRig().apply {
            registry.register("room", roomChain(6.0))
            registry.register("dry", dryChain(0.9))
            cylinder.requestChain("room")
            render(blocks = 300, level = 0.5)
        }

        val swapped = rig()
        val control = rig()
        var blocks = 0
        var cut = false

        swapped.cylinder.requestChain("dry")

        while (!swapped.swap.settled && blocks < 20000) {
            val got = swapped.block(level = 0.0)
            val want = control.block(level = 0.0)

            withClue("block $blocks") {
                got.contentEquals(want) shouldBe true
            }

            cut = cut || swapped.swap.isCutting
            blocks++
        }

        withClue("the drain ran a real ring-out of its own, over a second, and was never cut") {
            cut shouldBe false
            swapped.swap.settled shouldBe true
            blocks shouldBeGreaterThan 345
        }
    }

    "a name registered while its request waits behind a drain cuts the drain on the next poll" {
        val rig = drainingRoom()

        rig.cylinder.requestChain("unknown")

        withClue("an unknown name cannot land, so it cuts nothing") {
            rig.swap.isDraining shouldBe true
        }

        rig.render(blocks = 3, level = 0.0)
        rig.swap.isDraining shouldBe true

        rig.registry.register("unknown", dryChain(0.7))
        rig.block(level = 0.0)

        withClue("the poll found it registered and the swap heard that a request waits") {
            rig.swap.isCutting shouldBe true
        }
    }

    "a chain that sustains itself no longer counts as endless once it is being cut" {
        val rig = CylinderSwapRig().apply {
            registry.register("loop", loop)
            registry.register("dry", dryChain(0.9))
            registry.register("late", dryChain(1.1))
            cylinder.requestChain("loop")
            render(blocks = fadeBlocks + 40, level = 0.5)
            cylinder.requestChain("dry")
            render(blocks = fadeBlocks + 10, level = 0.0)
        }

        withClue("positive control: the loop drains and the orbit holds an endless tail") {
            rig.swap.isDraining shouldBe true
            rig.cylinder.sustainsItself() shouldBe true
        }

        rig.cylinder.requestChain("late")

        withClue("cut, it ends within the cut by construction") {
            rig.swap.isCutting shouldBe true
            rig.cylinder.sustainsItself() shouldBe false
        }
    }
})
