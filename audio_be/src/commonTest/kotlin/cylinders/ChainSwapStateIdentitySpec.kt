/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import io.peekandpoke.klang.audio_be.ChainSwap
import io.peekandpoke.klang.audio_be.cylinders.CylinderSwapRig.Companion.dryChain
import io.peekandpoke.klang.audio_be.cylinders.CylinderSwapRig.Companion.duckedRoomChain
import io.peekandpoke.klang.audio_be.cylinders.CylinderSwapRig.Companion.roomChain
import io.peekandpoke.klang.audio_be.cylinders.CylinderSwapRig.Companion.roomState
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystRegistry
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl

/**
 * The chain swap of a cylinder is a state machine whose transitions are POINTER SWAPS between the
 * three objects [ChainSwap] creates with itself (Idle, Fading, Draining). A transition runs on the
 * audio thread, where nothing may allocate.
 *
 * What this spec guards is "the state machine adds no allocation" and only that
 * (`docs/plans/effect-state-machines.md`, the identity-spec bullet): it drives the cells of the
 * table on [ChainSwap] through the HOST, a real [Cylinder], and asserts after each that the state is
 * one of the three instances seen first. Every state x event cell is driven, with one arm left out:
 * the late duck TAKEOVER of Fading x `ownerClaimed`, a data arm with no transition (its own rows are
 * `CylinderChainCrossfadeSpec`'s "first claim lands in the swap block"); the arm that declines is
 * driven on every owned fade block. Behaviour (the refusal, the re-entry, the record of a finished life) is
 * `ChainSwapSpec`'s. The seam is [ChainSwap.currentState], which hands out the state object and
 * nothing else.
 */
class ChainSwapStateIdentitySpec : StringSpec({

    "every driven cell of the table points at the three states created with the swap" {
        val rig = CylinderSwapRig()
        val swap = rig.swap

        rig.registry.register("dry", dryChain(0.9))
        rig.registry.register("dry2", dryChain(1.1))
        rig.registry.register("room", roomChain(4.0))
        rig.registry.register("ducked", duckedRoomChain())
        rig.registry.register(
            "ducker",
            KatalystDsl.of(
                KatalystStageDsl.Duck(
                    orbit = IgnitorDsl.Constant(0.0),
                    depth = IgnitorDsl.Constant(0.6),
                    attack = IgnitorDsl.Constant(0.05),
                )
            ),
        )
        rig.voice = VoiceTestHelpers.createSynthVoice(katalystParams = roomState)

        // Everything here compares with `===` (`shouldBeSameInstanceAs`): a state written one day as
        // a `data class` would make two fresh instances EQUAL and keep an `equals` compare green
        // while every transition allocated.

        // Idle, and its self-edges: a claiming owner (ownerClaimed, configureLeaving), every block
        // of an orbit that swaps nothing. (The classic duck names no source, so the duck pass is
        // skipped here; Idle x processDuck is driven below, with a ducking chain in service.)
        val idle = swap.currentState
        rig.render(blocks = 50, level = 0.5)
        withClue("an owner claiming and the chain processing are self-edges of Idle") {
            swap.currentState shouldBeSameInstanceAs idle
        }

        // Idle -> Fading (begin), from the classic chain whose owner charged its room.
        rig.cylinder.requestChain("dry")
        val fading = swap.currentState
        fading shouldNotBeSameInstanceAs idle

        // Fading + begin: REFUSED, a self-edge.
        swap.begin(leaving = rig.buildChain(dryChain(0.5)), arrivingLatencyFrames = 0, duckingOut = null, duckFadingIn = false)
        withClue("a begin while fading is refused") {
            swap.currentState shouldBeSameInstanceAs fading
        }

        // Fading + a block mid-ramp (the owner still configures the leaving chain).
        rig.render(blocks = 2, level = 0.5)
        withClue("a block mid-ramp is a self-edge") {
            swap.currentState shouldBeSameInstanceAs fading
        }

        // Fading -> Draining: the ramp ran out and the classic room still rings.
        rig.render(blocks = rig.fadeBlocks, level = 0.0)
        val draining = swap.currentState
        withClue("Draining is its own state") {
            draining shouldNotBeSameInstanceAs fading
            draining shouldNotBeSameInstanceAs idle
        }

        // Draining + begin: REFUSED; Draining + a block: self-edge.
        swap.begin(leaving = rig.buildChain(dryChain(0.5)), arrivingLatencyFrames = 0, duckingOut = null, duckFadingIn = false)
        rig.block(level = 0.0)
        withClue("a begin while draining is refused, and a drain block is a self-edge") {
            swap.currentState shouldBeSameInstanceAs draining
        }

        // Draining -> Idle: the ring-out ends.
        rig.drainOut(level = 0.0)
        withClue("the drain lands in the ONE Idle instance") {
            swap.currentState shouldBeSameInstanceAs idle
        }

        // Idle -> Fading -> Idle directly: the leaving chain (dry) holds nothing that can ring.
        rig.render(blocks = 2, level = 0.5)
        rig.cylinder.requestChain("dry2")
        withClue("a second swap reuses the ONE Fading instance") {
            swap.currentState shouldBeSameInstanceAs fading
        }
        rig.render(blocks = rig.fadeBlocks, level = 0.5)
        withClue("a fade whose leaving chain has no tail lands in the ONE Idle instance") {
            swap.currentState shouldBeSameInstanceAs idle
        }

        // Fading x processDuck, the ramp-in arm: from dry2 (no duck) to the ducked chain.
        rig.cylinder.requestChain("ducked")
        rig.render(blocks = rig.fadeBlocks, level = 0.5, sidechainLevel = 0.5)
        withClue("a fade that ramped a duck in lands in the ONE Idle instance") {
            swap.currentState shouldBeSameInstanceAs idle
        }

        // Idle x processDuck: the ducked chain in service, its duck pass running.
        rig.render(blocks = 20, level = 0.5, sidechainLevel = 0.5)
        swap.currentState shouldBeSameInstanceAs idle

        // Fading x processDuck, the chain's own arm (duck to duck, the envelope taken over), and then
        // Draining x processDuck: the leaving chain's room rings out while the ARRIVING chain ducks.
        rig.cylinder.requestChain("ducker")
        rig.render(blocks = rig.fadeBlocks, level = 0.5, sidechainLevel = 0.5)
        withClue("a drain under a ducking chain is the ONE Draining instance, and its duck pass runs") {
            swap.currentState shouldBeSameInstanceAs draining
            rig.cylinder.duck?.duckCylinderId.shouldNotBeNull()
        }
        rig.render(blocks = 3, level = 0.5, sidechainLevel = 0.5)
        swap.currentState shouldBeSameInstanceAs draining
        rig.drainOut(level = 0.5)
        swap.currentState shouldBeSameInstanceAs idle

        // Fading x processDuck, the ramp-out arm: from the ducking chain to one without a duck.
        rig.render(blocks = 20, level = 0.5, sidechainLevel = 0.5)
        rig.cylinder.requestChain("dry")
        rig.render(blocks = 2, level = 0.5, sidechainLevel = 0.5)
        withClue("a fade that carries a duck is the ONE Fading instance") {
            swap.currentState shouldBeSameInstanceAs fading
        }

        // Fading -> Idle through the hard cut (retire), from mid-ramp.
        rig.cylinder.retire()
        withClue("the hard cut from mid-fade lands in the ONE Idle instance") {
            swap.currentState shouldBeSameInstanceAs idle
        }

        // Idle + hardCut: a self-edge.
        rig.cylinder.retire()
        swap.currentState shouldBeSameInstanceAs idle

        // Draining -> Idle through the hard cut (adopt), from mid-drain.
        val registry = KatalystRegistry()
        registry.register("dry", dryChain(0.9))
        rig.cylinder.adopt(id = 1, silentBlocksBeforeTailCheck = 0, katalysts = registry)
        rig.render(blocks = 50, level = 0.5)
        rig.cylinder.requestChain("dry")
        rig.render(blocks = rig.fadeBlocks, level = 0.0)
        swap.currentState shouldBeSameInstanceAs draining
        rig.cylinder.adopt(id = 1, silentBlocksBeforeTailCheck = 0, katalysts = registry)
        withClue("the hard cut from mid-drain lands in the ONE Idle instance") {
            swap.currentState shouldBeSameInstanceAs idle
        }
    }
})
