/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import io.peekandpoke.klang.audio_bridge.constants.BODY_WET
import io.peekandpoke.klang.audio_bridge.constants.SLOT_UNSET

/**
 * The slot storage of one event, and the one METHOD the compound-door fill rule is expressed
 * through (Katalyst step 5a-3; the rule itself lives in `/dsl-design` §4).
 *
 * The rows the doors depend on are the two halves of [ParamBag.setOrDefault]: a value the call gave
 * is written, and a default is written ONLY where nobody has written yet. Everything else here is
 * the single-owner contract the mutable bag rests on: [ParamBag.copy] hands out no shared state and
 * [ParamBag.toMap] hands the wire no live reference.
 */
class ParamBagSpec : StringSpec({

    "set writes, and overwrites: what the author named always wins" {
        val bag = ParamBag()

        bag.set("reverb.wet", 0.3)
        bag["reverb.wet"] shouldBe 0.3

        bag.set("reverb.wet", 0.7)
        bag["reverb.wet"] shouldBe 0.7

        withClue("a name nobody wrote reads as null, which is what the fill branch tests for") {
            bag["reverb.size"].shouldBeNull()
        }
    }

    "setOrDefault with a value writes that value, present or absent" {
        val bag = ParamBag()

        bag.setOrDefault("body.wet", 0.3, 0.5)
        bag["body.wet"] shouldBe 0.3

        // ...and it overwrites, because a value the call gave is an explicit write.
        bag.setOrDefault("body.wet", 0.9, 0.5)
        bag["body.wet"] shouldBe 0.9
    }

    "setOrDefault without a value writes the default ONLY where the name is absent" {
        val bag = ParamBag()

        bag.setOrDefault("body.floor", null, 0.4)
        bag["body.floor"] shouldBe 0.4

        // A second fill must not move it, even to a different constant: absence is the whole test.
        bag.setOrDefault("body.floor", null, 0.9)
        bag["body.floor"] shouldBe 0.4
    }

    "a CLEARED slot stays cleared: a non-finite value is a value, not an empty name" {
        // A unit property of the bag, not of a door: whatever put a non-finite number in a slot,
        // a later fill has to leave it alone, or it would quietly un-clear what the author cleared.
        // Testing the VALUE instead of the name would do exactly that, because SLOT_UNSET is NaN,
        // and that mutant is what this row kills.
        //
        // The producer a reader should picture is a raw `katp("body.wet", "NaN")`: `katp` writes one
        // slot and no voice field, and the fill that follows must not stamp BODY_WET over it. The
        // NAME knobs are not the example here, because a fill may never write one at all.
        val bag = ParamBag()

        bag.set("body.wet", SLOT_UNSET)
        bag.setOrDefault("body.wet", null, BODY_WET)

        withClue("the cleared slot is still cleared, not BODY_WET") {
            bag["body.wet"].shouldNotBeNull().isFinite() shouldBe false
        }
    }

    "an explicit value survives a later fill: the never-overwrite half of the rule" {
        // This is `body(wet = 0.3).body(material = "wood")`: the second call names the stage and fills the
        // companions, and the 0.3 the author asked for has to come through untouched.
        val bag = ParamBag()

        bag.set("body.wet", 0.3)
        bag.setOrDefault("body.wet", null, 0.5)

        bag["body.wet"] shouldBe 0.3

        withClue("and a 0.0 is a value like any other, not an empty slot") {
            val zeroed = ParamBag()

            zeroed.set("duck.depth", 0.0)
            zeroed.setOrDefault("duck.depth", null, 0.75)

            zeroed["duck.depth"] shouldBe 0.0
        }
    }

    "copy is independent: a write to the copy does not reach the original" {
        val original = ParamBag()

        original.set("reverb.wet", 0.3)
        original.set("reverb.size", 6.0)

        val copy = original.copy()

        copy shouldNotBeSameInstanceAs original
        copy["reverb.wet"] shouldBe 0.3

        copy.set("reverb.wet", 0.9)
        copy.set("room", 2.0)

        withClue("the clone owns its bag, which is what makes one event's write stay on that event") {
            original["reverb.wet"] shouldBe 0.3
            original["room"].shouldBeNull()
            original.size shouldBe 2
        }
    }

    "mergeFrom is last writer wins per name, in place" {
        val target = ParamBag()

        target.set("reverb.size", 3.0)
        target.set("room", 1.0)

        val other = ParamBag()

        other.set("room", 9.0)
        other.set("delay.time", 0.5)

        target.mergeFrom(other)

        withClue("other's name wins, the receiver's own names stay") {
            target["room"] shouldBe 9.0
            target["reverb.size"] shouldBe 3.0
            target["delay.time"] shouldBe 0.5
        }

        withClue("the source is untouched: a merge reads it, it does not adopt it") {
            other["reverb.size"].shouldBeNull()
        }
    }

    "toMap is a copy, so the wire value cannot move under the backend" {
        val bag = ParamBag()

        bag.set("room", 2.0)

        val wire = bag.toMap()

        wire shouldBe mapOf("room" to 2.0)

        bag.set("room", 9.0)
        bag.set("reverb.wet", 0.5)

        wire shouldBe mapOf("room" to 2.0)
    }

    "an empty bag is empty, and two bags with the same names are equal" {
        val bag = ParamBag()

        bag.isEmpty() shouldBe true
        bag.size shouldBe 0

        bag.set("room", 2.0)

        bag.isEmpty() shouldBe false
        bag.size shouldBe 1

        // Structural equality, because `SprudelVoiceData` is a data class and its `clone()` guard
        // compares two whole instances.
        bag shouldBe paramBagOf("room" to 2.0)
        bag shouldNotBe paramBagOf("room" to 3.0)
    }
})
