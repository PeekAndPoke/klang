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
import io.peekandpoke.klang.audio_be.warehouse.SizedBuffers

/**
 * The lifecycle of [KatalystDelayEffect] is a state machine whose transitions are POINTER SWAPS
 * between three objects created with the effect. Nothing on the audio thread may allocate, and a
 * transition is on the audio thread.
 *
 * **What this spec guards is "the state machine adds no allocation"** (section 1 of
 * `docs/plans/effect-state-machines.md` and its identity-spec bullet), and only that. Rule 3's
 * "`enter` initialises the target's own data, so a state never carries a previous life's values" is
 * behaviour and is guarded in `KatalystDelayEffectSpec`: replacing `draining.enter(remaining)`
 * with a bare `state = draining` leaves every row HERE green and turns the drain rows there red,
 * and the row "the countdown a drain runs on is its own, never the previous life's remains" is the
 * one written for it.
 *
 * It is deliberately NOT an allocation profiler: the only objects a transition could allocate are
 * the states themselves, so driving the table and counting the DISTINCT state objects that ever
 * appear says the same thing. That final count is a SUMMARY, not an independent guard: it cannot
 * go red before one of the `shouldBeSameInstanceAs` assertions above it does. Its job is to say
 * "and no fourth object appeared anywhere along the way" in one line.
 *
 * **What the main row drives:** every cell of the table on `KatalystDelayEffect.State` except the
 * two marked "(never)", which are unreachable by construction, and each of `configure`'s three
 * arms separately, because a cell of that table can be reached more than one way: an off-config
 * with no ring at all (which returns before the state is consulted), an off-config with a ring,
 * and the ON-config refusal the table's Off row spells out.
 *
 * This is the shape a conversion of the reverb, the filter swap or the compressor copies. The seam
 * is [KatalystDelayEffect.currentState], which hands out the state object and nothing else.
 */
class KatalystDelayStateIdentitySpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128

    /**
     * A ring shelf whose allocator can be told to refuse, the same double `LazyRingSpec` uses (it
     * holds the lambda rather than implementing the function type, which Kotlin/JS forbids).
     */
    class Recording(var failing: Boolean = false) {
        val allocate: (Int) -> StereoBuffer? = { frames ->
            if (failing) null else StereoBuffer(frames)
        }
    }

    fun createCtx() = KatalystContext(
        blockFrames = blockFrames,
        mixBuffer = StereoBuffer(blockFrames),
        delaySendBuffer = StereoBuffer(blockFrames),
        reverbSendBuffer = StereoBuffer(blockFrames),
    )

    /**
     * The PRODUCTION constructor, so the effect starts with no ring at all and rents one on its
     * first activating config. The test-seam constructor would install a `DelayLine` up front and
     * the "no ring" arm of `configure` would never be walked.
     */
    fun createEffect(rings: SizedBuffers) = KatalystDelayEffect(
        rings = rings,
        sampleRate = sampleRate,
        blockFrames = blockFrames,
    )

    /** One block with a full-scale impulse in the send, so the ring is charged and has a tail. */
    fun charge(effect: KatalystDelayEffect, ctx: KatalystContext) {
        ctx.delaySendBuffer.clear()
        ctx.mixBuffer.clear()
        ctx.delaySendBuffer.left[0] = 1.0
        ctx.delaySendBuffer.right[0] = 1.0
        effect.process(ctx)
    }

    /** Silent blocks until the effect reports no tail, i.e. until the countdown has run out. */
    fun drainToOff(effect: KatalystDelayEffect, ctx: KatalystContext) {
        var blocks = 0

        while (effect.hasTail() && blocks < 100_000) {
            ctx.delaySendBuffer.clear()
            ctx.mixBuffer.clear()
            effect.process(ctx)
            blocks++
        }

        withClue("the drain must end inside the countdown") { effect.hasTail() shouldBe false }
    }

    "every reachable cell of the table points at the three states created with the effect" {
        val rings = SizedBuffers.forRings(sampleRate)
        val effect = createEffect(rings)
        val ctx = createCtx()

        // An IDENTITY collection, deliberately, and spelled out because common Kotlin has no
        // identity set: a `Set` compares with `equals`, so a state written one day as a `data
        // class` would make two FRESH instances equal and keep this whole row green while every
        // transition allocated. Everything here compares with `===` for the same reason.
        val seen = mutableListOf<Any>()

        fun see(state: Any) {
            if (seen.none { it === state }) {
                seen += state
            }
        }

        // Off, before any owner has spoken, and with no ring: this effect was built the way a
        // cylinder builds one.
        val off = effect.currentState
        see(off)
        effect.delayLine shouldBe null

        // Off + an off-config with NO ring: `configure` returns at its null check, before the
        // state is consulted at all.
        effect.configure(time = 0.0, feedback = 0.0, cap = 1.0)
        effect.currentState shouldBeSameInstanceAs off

        // Off -> Active, renting the ring on the way.
        effect.configure(time = 0.05, feedback = 0.6, cap = 2.0)
        val active = effect.currentState
        see(active)
        active shouldNotBeSameInstanceAs off

        // Active -> Active: the knob-rewrite self-edge, every block of a running delay.
        effect.configure(time = 0.06, feedback = 0.5, cap = 1.5)
        withClue("a knob rewrite is a self-edge, not a new state") {
            effect.currentState shouldBeSameInstanceAs active
        }
        see(effect.currentState)

        charge(effect, ctx)

        // Active -> Draining.
        effect.configure(time = 0.0, feedback = 0.6, cap = 2.0)
        val draining = effect.currentState
        see(draining)
        withClue("Draining is its own state") {
            draining shouldNotBeSameInstanceAs active
            draining shouldNotBeSameInstanceAs off
        }

        // Draining -> Draining: a second off-config changes nothing (the countdown keeps running,
        // which is the half `KatalystDelayEffectSpec` owns).
        effect.configure(time = 0.0, feedback = 0.6, cap = 2.0)
        withClue("an off-config repeated while draining is a self-edge") {
            effect.currentState shouldBeSameInstanceAs draining
        }
        see(effect.currentState)

        // The shortcut: Draining -> Active before the countdown ends, and straight back.
        effect.configure(time = 0.08, feedback = 0.4, cap = 1.0)
        withClue("Draining -> Active reuses the ONE Active instance") {
            effect.currentState shouldBeSameInstanceAs active
        }
        see(effect.currentState)

        effect.configure(time = 0.0, feedback = 0.4, cap = 1.0)
        withClue("Active -> Draining reuses the ONE Draining instance") {
            effect.currentState shouldBeSameInstanceAs draining
        }
        see(effect.currentState)

        // Draining -> Off, by the countdown running out.
        drainToOff(effect, ctx)
        withClue("the countdown lands in the ONE Off instance") {
            effect.currentState shouldBeSameInstanceAs off
        }
        see(effect.currentState)

        // Off + an off-config WITH a ring present (the terminal reset keeps the ring): this time
        // `configure` does reach the state, and Off has nothing to do.
        effect.delayLine.shouldNotBeNull()
        effect.configure(time = 0.0, feedback = 0.0, cap = 1.0)
        withClue("an off-config while Off with a ring is a self-edge") {
            effect.currentState shouldBeSameInstanceAs off
        }
        see(effect.currentState)

        // Off + reset, and Off + retire: the two lifecycle doors, walked from Off.
        effect.reset()
        withClue("reset while Off is a self-edge") {
            effect.currentState shouldBeSameInstanceAs off
        }
        see(effect.currentState)

        // Active -> Off through the cylinder-deactivation door, from a live tail.
        effect.configure(time = 0.05, feedback = 0.6, cap = 2.0)
        effect.currentState shouldBeSameInstanceAs active
        charge(effect, ctx)
        effect.reset()
        withClue("reset lands in the ONE Off instance") {
            effect.currentState shouldBeSameInstanceAs off
        }
        see(effect.currentState)

        // Draining -> Off through the same door: `reset` from mid-drain.
        effect.configure(time = 0.05, feedback = 0.6, cap = 2.0)
        charge(effect, ctx)
        effect.configure(time = 0.0, feedback = 0.6, cap = 2.0)
        effect.currentState shouldBeSameInstanceAs draining
        effect.reset()
        withClue("reset from mid-drain lands in the ONE Off instance") {
            effect.currentState shouldBeSameInstanceAs off
        }
        see(effect.currentState)

        // Draining -> Off through the RETIRE door, then a whole new life on a ring the warehouse
        // hands out zeroed: an off-config on that silent ring must land in Off directly. (A
        // countdown left over from the retired life would NOT show here: this path reaches Off
        // through a local in `Active.deactivate` and never reads `Draining.remaining`. Where it
        // shows is `KatalystDelayEffectSpec`'s "the countdown a drain runs on is its own".)
        effect.configure(time = 0.05, feedback = 0.6, cap = 2.0)
        charge(effect, ctx)
        effect.configure(time = 0.0, feedback = 0.6, cap = 2.0)
        effect.currentState shouldBeSameInstanceAs draining
        effect.retire()
        withClue("retire from mid-drain lands in the ONE Off instance") {
            effect.currentState shouldBeSameInstanceAs off
        }
        see(effect.currentState)

        effect.configure(time = 0.05, feedback = 0.6, cap = 2.0)
        withClue("the new life rents a ring and activates") {
            effect.currentState shouldBeSameInstanceAs active
        }
        effect.configure(time = 0.0, feedback = 0.0, cap = 1.0)
        withClue("a silent ring goes straight to Off, with no drain of the retired life's making") {
            effect.currentState shouldBeSameInstanceAs off
        }
        see(effect.currentState)

        // Active -> Off through retire, from a live tail; then retire AGAIN, from Off. The second
        // one is the double-release question: `release` gives the ring back and drops it, so the
        // second call finds nothing to hand over. `SizedBuffers` counts a buffer returned twice,
        // which is what makes that observable from here.
        effect.configure(time = 0.05, feedback = 0.6, cap = 2.0)
        charge(effect, ctx)
        effect.retire()
        withClue("retire lands in the ONE Off instance") {
            effect.currentState shouldBeSameInstanceAs off
        }
        see(effect.currentState)

        effect.retire()
        withClue("retire while Off is a self-edge") {
            effect.currentState shouldBeSameInstanceAs off
        }
        withClue("a second retire must not hand the same ring back twice") {
            rings.doubleReturns shouldBe 0
        }
        see(effect.currentState)

        // Off + an ON-config the warehouse REFUSES, with no ring to fall back on: the effect
        // degrades rather than dying, and degrading means staying Off. (`LazyRingSpec` owns the
        // rest of that contract: the denied count, the dry render, the latch.)
        val refusing = Recording(failing = true)
        val starved = createEffect(SizedBuffers.forRings(sampleRate, allocate = refusing.allocate))
        val starvedOff = starved.currentState

        starved.configure(time = 0.3, feedback = 0.4, cap = 1.0)
        withClue("a refused first rent leaves the effect in its own Off instance") {
            starved.currentState shouldBeSameInstanceAs starvedOff
        }
        starved.deniedRents shouldBe 1

        // The whole point, as a summary: driving the table produced no fourth object. The clue
        // prints CLASS names, so a failure reads "Off, Active, Draining, Draining" instead of four
        // identity hashes.
        withClue("a transition that allocates shows up here: ${seen.map { it::class.simpleName }}") {
            seen.size shouldBe 3
        }
    }

    "an already-silent tap window goes straight to the same Off instance, without a Draining detour" {
        // The other way into Off from Active (an empty or inaudible ring at the off-config), and
        // the one that must not invent a state either.
        val effect = createEffect(SizedBuffers.forRings(sampleRate))
        val off = effect.currentState

        effect.configure(time = 0.05, feedback = 1.2, cap = 2.0)
        val active = effect.currentState

        // Never charged: the ring is all zeros, so the off-config lands in Off directly.
        effect.configure(time = 0.0, feedback = 0.0, cap = 1.0)

        withClue("the silent-ring arm reuses the ONE Off instance") {
            effect.currentState shouldBeSameInstanceAs off
        }
        active shouldNotBeSameInstanceAs off
    }
})
