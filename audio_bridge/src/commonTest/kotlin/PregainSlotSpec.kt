/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * `pregain` is an ORDINARY slot (the signal-flow plan, section 6): a `Param` with a name and a
 * default, placed by the instrument, doing nothing anywhere else. This spec pins the declaration
 * and the one structural promise the helper makes.
 *
 * **Why the helper needs a spec at all.** `x.pregain()` is offered as a shorter spelling of
 * `x.mul(OscSlot.pregain)`, and "shorter spelling" is a claim about the TREE, not about the
 * sound: the two must be the same node with the same operand order, or the build cache, the
 * optimizer's refcounts and the process-wide identity map (`uniqueId`) would see two instruments
 * where the author wrote one, register both and crossfade one against the other. Swapping the
 * operands would even render the same samples (a multiply commutes), which is exactly why a
 * render test cannot stand in for this one.
 */
class PregainSlotSpec : StringSpec({

    "the slot is a Param named pregain, defaulting to unity" {
        // Unity and not zero: an instrument that places the slot must sound like itself until a
        // pattern says otherwise. A zero default would silence every driven instrument.
        val slot = IgnitorDsl.Slots.pregain

        slot shouldBe IgnitorDsl.Param(name = "pregain", default = 1.0)
    }

    "the helper is the mul, operand for operand, so both spellings are ONE tree" {
        val source = IgnitorDsl.Sawtooth()

        val helper = source.pregain()
        val spelledOut = source.mul(IgnitorDsl.Slots.pregain)

        helper shouldBe spelledOut
        helper shouldBe IgnitorDsl.Times(left = source, right = IgnitorDsl.Slots.pregain)

        // The signal is on the LEFT. A multiply commutes, so the swap renders identically and
        // only the tree can tell: this is the assertion a render test cannot replace.
        (helper as IgnitorDsl.Times).left shouldBe source
        helper.right shouldBe IgnitorDsl.Slots.pregain
        helper shouldNotBe IgnitorDsl.Times(left = IgnitorDsl.Slots.pregain, right = source)
    }

    "both spellings get the SAME content id, which is what the registries key on" {
        val source = IgnitorDsl.Sine()

        source.pregain().uniqueId() shouldBe source.mul(IgnitorDsl.Slots.pregain).uniqueId()

        // ...and it is not the id of the bare source, so the row above is not vacuous.
        source.pregain().uniqueId() shouldNotBe source.uniqueId()
    }

    "a tree that places the slot discovers it; one that does not has no pregain to discover" {
        // `0.5` and not `2.0`: at 2.0 the shaper is in hard saturation and the slot is
        // inaudible, and a spec is the first place anybody copies an instrument out of.
        val driven = IgnitorDsl.Sawtooth().pregain().distort(0.5)
        val plain = IgnitorDsl.Sawtooth().distort(0.5)

        withClue("the driven instrument offers the slot") {
            driven.getParamSlots().map { it.name } shouldBe listOf("analog", "pregain")
        }

        withClue("the plain one does not, which is why .pregain(x) is inert on it") {
            plain.getParamSlots().map { it.name } shouldNotContain "pregain"
        }
    }
})
