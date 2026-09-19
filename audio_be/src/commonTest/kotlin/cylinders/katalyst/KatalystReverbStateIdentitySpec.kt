/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.warehouse.ReverbUnits

/**
 * The lifecycle of [KatalystReverbEffect] is a state machine whose transitions are POINTER SWAPS
 * between three objects created with the effect. Nothing on the audio thread may allocate, and a
 * transition is on the audio thread. The shape is `KatalystDelayStateIdentitySpec`'s.
 *
 * **What this spec guards is "the state machine adds no allocation"** (section 1 of
 * `docs/plans/effect-state-machines.md`), and only that. The behaviour of the edges (what a return
 * mid-drain keeps, what a finished life forgets, the heal of a poisoned network) is guarded in
 * `KatalystReverbEffectSpec` and `KatalystReverbGlideSpec`.
 *
 * It is deliberately NOT an allocation profiler: the only objects a transition could allocate are
 * the states themselves, so driving the table and counting the DISTINCT state objects that ever
 * appear says the same thing. The final count is a SUMMARY of the `shouldBeSameInstanceAs`
 * assertions above it, not an independent guard.
 *
 * **What the main row drives:** every cell of the table on `KatalystReverbEffect.State` except the
 * two marked "(never)", both of the Active OFF-arm's ways into Off (a silent network and a POISONED
 * one), the size glide's edges (a return mid-drain while the glide moves, reset and retire
 * mid-glide), and each of `configure`'s three arms separately: an off-config with no unit at all
 * (which returns before the state is consulted), an off-config with a unit, and the ON-config
 * refusal the table's Off row spells out. The seam is [KatalystReverbEffect.currentState].
 */
class KatalystReverbStateIdentitySpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128

    fun createCtx() = KatalystContext(
        blockFrames = blockFrames,
        mixBuffer = StereoBuffer(blockFrames),
    )

    /**
     * The PRODUCTION constructor, so the effect starts with no unit at all and rents one on its first
     * activating config. The test-seam constructor would install a `Reverb` up front and the
     * "no unit" arm of `configure` would never be walked.
     */
    fun createEffect(units: ReverbUnits) = KatalystReverbEffect(units = units, blockFrames = blockFrames)

    /** One block with a full-scale impulse in the send, so the network is charged and has a tail. */
    fun charge(effect: KatalystReverbEffect, ctx: KatalystContext) {
        ctx.mixBuffer.clear()
        ctx.mixBuffer.left[0] = 1.0
        ctx.mixBuffer.right[0] = 1.0
        effect.process(ctx)
    }

    /** Silent blocks until the effect reports no tail, i.e. until the countdown has run out. */
    fun drainToOff(effect: KatalystReverbEffect, ctx: KatalystContext) {
        var blocks = 0

        while (effect.hasTail() && blocks < 100_000) {
            ctx.mixBuffer.clear()
            effect.process(ctx)
            blocks++
        }

        withClue("the drain must end inside the countdown") { effect.hasTail() shouldBe false }
    }

    "every reachable cell of the table points at the three states created with the effect" {
        val units = ReverbUnits(sampleRate)
        val effect = createEffect(units)
        val ctx = createCtx()

        // An IDENTITY collection, deliberately: a `Set` compares with `equals`, so a state written
        // one day as a `data class` would make two FRESH instances equal and keep this row green
        // while every transition allocated. Everything here compares with `===`.
        val seen = mutableListOf<Any>()

        fun see(state: Any) {
            if (seen.none { it === state }) {
                seen += state
            }
        }

        // Off, before any owner has spoken, and with no unit: built the way a cylinder builds one.
        val off = effect.currentState
        see(off)
        effect.reverb shouldBe null

        // Off + an off-config with NO unit: `configure` returns at its null check.
        effect.configure(size = 0.0, lowpass = null, wet = 1.0)
        effect.currentState shouldBeSameInstanceAs off

        // Off -> Active, renting the unit on the way.
        effect.configure(size = 0.5, lowpass = null, wet = 1.0)
        val active = effect.currentState
        see(active)
        active shouldNotBeSameInstanceAs off

        // Active -> Active: the knob-rewrite self-edge, every block of a running reverb.
        effect.configure(size = 0.6, lowpass = 4000.0, wet = 1.0)
        withClue("a knob rewrite is a self-edge, not a new state") {
            effect.currentState shouldBeSameInstanceAs active
        }
        see(effect.currentState)

        charge(effect, ctx)

        // Active -> Draining.
        effect.configure(size = 0.0, lowpass = null, wet = 1.0)
        val draining = effect.currentState
        see(draining)
        withClue("Draining is its own state") {
            draining shouldNotBeSameInstanceAs active
            draining shouldNotBeSameInstanceAs off
        }

        // Draining -> Draining: a second off-config changes nothing.
        effect.configure(size = 0.0, lowpass = null, wet = 1.0)
        withClue("an off-config repeated while draining is a self-edge") {
            effect.currentState shouldBeSameInstanceAs draining
        }
        see(effect.currentState)

        // The shortcut: Draining -> Active before the countdown ends, and straight back.
        effect.configure(size = 0.3, lowpass = null, wet = 1.0)
        withClue("Draining -> Active reuses the ONE Active instance") {
            effect.currentState shouldBeSameInstanceAs active
        }
        see(effect.currentState)

        effect.configure(size = 0.0, lowpass = null, wet = 1.0)
        withClue("Active -> Draining reuses the ONE Draining instance") {
            effect.currentState shouldBeSameInstanceAs draining
        }
        see(effect.currentState)

        // Draining -> Active while the size glide is still MOVING, and out again mid-glide.
        effect.configure(size = 0.9, lowpass = null, wet = 1.0)
        effect.currentState shouldBeSameInstanceAs active
        charge(effect, ctx)
        effect.configure(size = 0.0, lowpass = null, wet = 1.0)
        effect.currentState shouldBeSameInstanceAs draining
        effect.configure(size = 0.2, lowpass = null, wet = 1.0)
        withClue("a return mid-drain and mid-glide reuses the ONE Active instance") {
            effect.currentState shouldBeSameInstanceAs active
        }
        see(effect.currentState)
        effect.configure(size = 0.0, lowpass = null, wet = 1.0)
        effect.currentState shouldBeSameInstanceAs draining

        // Draining -> Off, by the countdown running out.
        drainToOff(effect, ctx)
        withClue("the countdown lands in the ONE Off instance") {
            effect.currentState shouldBeSameInstanceAs off
        }
        see(effect.currentState)

        // Off + an off-config WITH a unit present (the terminal reset keeps the unit).
        effect.reverb.shouldNotBeNull()
        effect.configure(size = 0.0, lowpass = null, wet = 1.0)
        withClue("an off-config while Off with a unit is a self-edge") {
            effect.currentState shouldBeSameInstanceAs off
        }
        see(effect.currentState)

        // Off + reset.
        effect.reset()
        withClue("reset while Off is a self-edge") {
            effect.currentState shouldBeSameInstanceAs off
        }
        see(effect.currentState)

        // Active -> Off through the cylinder-deactivation door, from a live tail, mid-glide.
        effect.configure(size = 0.2, lowpass = null, wet = 1.0)
        charge(effect, ctx)
        effect.configure(size = 0.8, lowpass = null, wet = 1.0)
        charge(effect, ctx)
        effect.currentState shouldBeSameInstanceAs active
        effect.reset()
        withClue("reset mid-glide lands in the ONE Off instance") {
            effect.currentState shouldBeSameInstanceAs off
        }
        see(effect.currentState)

        // Draining -> Off through the same door: `reset` from mid-drain.
        effect.configure(size = 0.5, lowpass = null, wet = 1.0)
        charge(effect, ctx)
        effect.configure(size = 0.0, lowpass = null, wet = 1.0)
        effect.currentState shouldBeSameInstanceAs draining
        effect.reset()
        withClue("reset from mid-drain lands in the ONE Off instance") {
            effect.currentState shouldBeSameInstanceAs off
        }
        see(effect.currentState)

        // Active -> Off through the silent-network arm: never charged since the reset.
        effect.configure(size = 0.5, lowpass = null, wet = 1.0)
        effect.configure(size = 0.0, lowpass = null, wet = 1.0)
        withClue("a silent network goes straight to the ONE Off instance") {
            effect.currentState shouldBeSameInstanceAs off
        }
        see(effect.currentState)

        // Active -> Off through the POISONED-network arm: a comb overflowed to non-finite, so the
        // countdown is infinite and the reset is the heal (the reverb's own arm).
        effect.configure(size = 0.5, lowpass = null, wet = 1.0)

        repeat(16) {
            ctx.mixBuffer.fill(Double.MAX_VALUE)
            effect.process(ctx)
        }

        effect.reverb.shouldNotBeNull().combPeakAbs() shouldBe Double.POSITIVE_INFINITY
        effect.configure(size = 0.0, lowpass = null, wet = 1.0)
        withClue("a poisoned network heals into the ONE Off instance") {
            effect.currentState shouldBeSameInstanceAs off
        }
        see(effect.currentState)

        // Draining -> Off through the RETIRE door, then a new life on a unit the shelf hands out.
        effect.configure(size = 0.5, lowpass = null, wet = 1.0)
        charge(effect, ctx)
        effect.configure(size = 0.0, lowpass = null, wet = 1.0)
        effect.currentState shouldBeSameInstanceAs draining
        effect.retire()
        withClue("retire from mid-drain lands in the ONE Off instance") {
            effect.currentState shouldBeSameInstanceAs off
        }
        see(effect.currentState)

        effect.configure(size = 0.5, lowpass = null, wet = 1.0)
        withClue("the new life rents a unit and activates") {
            effect.currentState shouldBeSameInstanceAs active
        }

        // Active -> Off through retire, mid-glide from a live tail; then retire AGAIN, from Off.
        charge(effect, ctx)
        effect.configure(size = 0.9, lowpass = null, wet = 1.0)
        charge(effect, ctx)
        effect.retire()
        withClue("retire mid-glide lands in the ONE Off instance") {
            effect.currentState shouldBeSameInstanceAs off
        }
        see(effect.currentState)

        effect.retire()
        withClue("retire while Off is a self-edge") {
            effect.currentState shouldBeSameInstanceAs off
        }
        withClue("a second retire must not hand the same unit back twice") {
            units.doubleReturns shouldBe 0
        }
        see(effect.currentState)

        // Off + an ON-config the shelf REFUSES, with no unit to fall back on: the effect stays Off.
        val starved = createEffect(ReverbUnits(sampleRate, allocate = { null }))
        val starvedOff = starved.currentState

        starved.configure(size = 0.5, lowpass = null, wet = 1.0)
        withClue("a refused first rent leaves the effect in its own Off instance") {
            starved.currentState shouldBeSameInstanceAs starvedOff
        }
        starved.deniedRents shouldBe 1

        // The summary: driving the table produced no fourth object.
        withClue("a transition that allocates shows up here: ${seen.map { it::class.simpleName }}") {
            seen.size shouldBe 3
        }
    }
})
