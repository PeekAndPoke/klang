/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.peekandpoke.klang.audio_be.ChainSwap
import io.peekandpoke.klang.audio_be.cylinders.CylinderSwapRig.Companion.dryChain
import io.peekandpoke.klang.audio_be.cylinders.CylinderSwapRig.Companion.duckedRoomChain
import io.peekandpoke.klang.audio_be.cylinders.CylinderSwapRig.Companion.roomChain
import io.peekandpoke.klang.audio_be.cylinders.CylinderSwapRig.Companion.roomState
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystChain
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers

/**
 * The contracts of [ChainSwap] that its conversion from five `Cylinder` fields worked out for itself
 * (phase 3 step 12 C1; `docs/plans/effect-state-machines.md` section 2): the REFUSAL of a begin while
 * the one leaving slot is taken, the RE-ENTRY of a request parked behind a drain, and "a finished
 * life leaves no record". What a swap SOUNDS like is `CylinderChainCrossfadeSpec`'s; what a request
 * does is `CylinderChainSwapSpec`'s.
 *
 * The oracles are TWIN rigs: two cylinders given the same script produce the same bits, so "this
 * event changed nothing" and "this is the same as doing it at that moment" are sample-for-sample
 * comparisons, not thresholds.
 */
class ChainSwapSpec : StringSpec({

    /** Two rigs with the same chains and the same owner, both past [warm] sounding blocks. */
    fun twins(warm: Int = 60): Pair<CylinderSwapRig, CylinderSwapRig> {
        val a = CylinderSwapRig()
        val b = CylinderSwapRig()

        for (rig in listOf(a, b)) {
            rig.registry.register("dry", dryChain(0.9))
            rig.registry.register("room", roomChain(3.0))
            rig.voice = VoiceTestHelpers.createSynthVoice(katalystParams = roomState)
            rig.render(blocks = warm, level = 0.5)
        }

        return a to b
    }

    /**
     * A chain offered to a busy swap that has RENTED a room (its first configure rents one), so
     * whether the refusal retired it shows on the shelf.
     */
    fun CylinderSwapRig.offeredRoom(): KatalystChain {
        val offered = buildChain(roomChain(3.0))

        offered.applyParams(null)

        return offered
    }

    /** Renders [blocks] on both rigs and asserts every sample is the same. */
    fun sameFrom(a: CylinderSwapRig, b: CylinderSwapRig, blocks: Int, level: Double) {
        for (i in 0 until blocks) {
            val left = a.block(level = level)
            val right = b.block(level = level)

            withClue("block $i after the event") {
                left.contentEquals(right) shouldBe true
            }
        }
    }

    // ── The refusal ──────────────────────────────────────────────────────────────────────────────

    "a begin while the swap FADES is refused: the leaving chain and the ramp run on untouched" {
        val (a, b) = twins()

        a.cylinder.requestChain("dry")
        b.cylinder.requestChain("dry")
        a.render(blocks = 3, level = 0.5)
        b.render(blocks = 3, level = 0.5)

        val leaving = a.swap.leaving.shouldNotBeNull()
        val shelved = a.reverbs.idleCount

        a.swap.begin(leaving = a.offeredRoom(), duckingOut = null, duckFadingIn = false)

        withClue("the refused begin kept the one leaving chain") {
            a.swap.isFading shouldBe true
            a.swap.leaving shouldBeSameInstanceAs leaving
        }

        withClue("and retired the chain it refused: its room is back on the shelf, nothing stranded") {
            a.reverbs.idleCount shouldBe shelved + 1
        }

        withClue("and the output is the twin's, which was offered nothing, through the drain") {
            sameFrom(a, b, blocks = a.fadeBlocks + 40, level = 0.5)
        }
    }

    "a begin while the swap DRAINS is refused: the ring-out runs on untouched" {
        val (a, b) = twins()

        a.cylinder.requestChain("dry")
        b.cylinder.requestChain("dry")
        a.render(blocks = a.fadeBlocks, level = 0.0)
        b.render(blocks = b.fadeBlocks, level = 0.0)

        a.swap.isDraining shouldBe true

        val leaving = a.swap.leaving.shouldNotBeNull()
        val shelved = a.reverbs.idleCount

        a.swap.begin(leaving = a.offeredRoom(), duckingOut = null, duckFadingIn = false)

        withClue("the refused begin kept the draining chain") {
            a.swap.isDraining shouldBe true
            a.swap.leaving shouldBeSameInstanceAs leaving
        }

        withClue("and retired the chain it refused: its room is back on the shelf, nothing stranded") {
            a.reverbs.idleCount shouldBe shelved + 1
        }

        sameFrom(a, b, blocks = 60, level = 0.0)
    }

    // ── The re-entry ─────────────────────────────────────────────────────────────────────────────

    "a request during the drain waits for it and lands exactly as a request made the block it frees" {
        // A: the request arrives early in the drain and is parked by the host (the swap would
        // refuse it). B: the same request is made in the block the slot frees, at the point of the
        // block where the poll would land it. The drain must not be cut, the parked key must not
        // land late, and the two must then be the same bits: parking is invisible.
        val (a, b) = twins()

        a.cylinder.requestChain("dry")
        b.cylinder.requestChain("dry")
        a.render(blocks = a.fadeBlocks, level = 0.3)
        b.render(blocks = b.fadeBlocks, level = 0.3)

        a.swap.isDraining shouldBe true

        a.cylinder.requestChain("room")

        withClue("the parked request did not cut the drain") {
            a.swap.isDraining shouldBe true
        }

        var drained = 0

        while (a.cylinder.isDraining && drained < 20000) {
            val left = a.block(level = 0.3)
            val right = b.block(level = 0.3)

            withClue("drain block $drained: the parked key changes nothing while it waits") {
                left.contentEquals(right) shouldBe true
            }

            drained++
        }

        withClue("the drain ran a real tail before the parked request could land") {
            drained shouldBeGreaterThan 20
        }

        b.swap.settled shouldBe true

        val left = a.block(level = 0.3)
        val right = b.block(level = 0.3) { b.cylinder.requestChain("room") }

        withClue("the parked request landed in the block the slot freed, through a fade") {
            a.swap.isFading shouldBe true
            left.contentEquals(right) shouldBe true
        }

        sameFrom(a, b, blocks = a.fadeBlocks + 20, level = 0.3)
    }

    // ── A finished life leaves no record ─────────────────────────────────────────────────────────

    "a fade that ends in the drain and a drain that ends keep no reference to the chain or its duck" {
        val rig = CylinderSwapRig()
        rig.registry.register("ducked", duckedRoomChain())
        rig.registry.register("dry", dryChain(1.0))

        rig.cylinder.requestChain("ducked")
        rig.render(blocks = 200, level = 0.5, sidechainLevel = 0.5)

        rig.cylinder.requestChain("dry")

        val leaving = rig.swap.leaving.shouldNotBeNull()
        val duck = rig.swap.duckingOut.shouldNotBeNull()

        // No duck pass during the fade (the sidechain orbit disappeared), so the only thing that
        // can drop the duck is the fade's own exit edge.
        rig.render(blocks = rig.fadeBlocks, level = 0.5, duckPass = false)

        rig.swap.isDraining shouldBe true

        withClue("the fade's duck handover died with the fade") {
            rig.swap.holdsDuck(duck) shouldBe false
        }

        withClue("positive control: the draining chain is referenced while it drains") {
            rig.swap.holds(leaving) shouldBe true
        }

        rig.drainOut(level = 0.0)

        withClue("the drain ended, and no state still references the chain it retired") {
            rig.swap.settled shouldBe true
            rig.swap.holds(leaving) shouldBe false
        }
    }

    "a fade that retires its chain at once keeps no reference to it" {
        val rig = CylinderSwapRig()
        rig.registry.register("dry", dryChain(0.9))
        rig.registry.register("dry2", dryChain(1.1))

        rig.cylinder.requestChain("dry")
        rig.render(blocks = rig.fadeBlocks + 5, level = 0.5)

        rig.cylinder.requestChain("dry2")

        val leaving = rig.swap.leaving.shouldNotBeNull()

        rig.render(blocks = rig.fadeBlocks, level = 0.5)

        withClue("a chain with no tail retires at the fade's end and is not remembered") {
            rig.swap.settled shouldBe true
            rig.swap.holds(leaving) shouldBe false
        }
    }

    "the hard cut keeps no reference to the chain it cut, from a fade and from a drain" {
        val fadingRig = CylinderSwapRig()
        fadingRig.registry.register("ducked", duckedRoomChain())
        fadingRig.registry.register("dry", dryChain(1.0))
        fadingRig.cylinder.requestChain("ducked")
        fadingRig.render(blocks = 200, level = 0.5, sidechainLevel = 0.5)
        fadingRig.cylinder.requestChain("dry")
        fadingRig.render(blocks = 2, level = 0.5, sidechainLevel = 0.5)

        val cutWhileFading = fadingRig.swap.leaving.shouldNotBeNull()
        val duck = fadingRig.swap.duckingOut.shouldNotBeNull()

        fadingRig.cylinder.retire()

        withClue("retire from mid-fade") {
            fadingRig.swap.holds(cutWhileFading) shouldBe false
            fadingRig.swap.holdsDuck(duck) shouldBe false
        }

        val drainingRig = CylinderSwapRig()
        drainingRig.registry.register("dry", dryChain(1.0))
        drainingRig.voice = VoiceTestHelpers.createSynthVoice(katalystParams = roomState)
        drainingRig.render(blocks = 60, level = 0.5)
        drainingRig.cylinder.requestChain("dry")
        drainingRig.render(blocks = drainingRig.fadeBlocks, level = 0.0)

        drainingRig.swap.isDraining shouldBe true

        val cutWhileDraining = drainingRig.swap.leaving.shouldNotBeNull()

        drainingRig.cylinder.retire()

        withClue("retire from mid-drain") {
            drainingRig.swap.holds(cutWhileDraining) shouldBe false
        }
    }
})
