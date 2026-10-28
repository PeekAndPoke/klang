/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.feel

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

/** The equip grid's slots (KLANG-00382): place, swap, unequip, and remembered slots cleaned on the way in. */
class KlangLoadoutSpec : StringSpec({

    val start = KlangLoadout(listOf("a", "b", "c", null, null, null, null, null))

    "the default is the looks equipped by default, in their order, the rest empty; Army waits on the bench" {
        KlangLoadout.default.slots shouldContainExactly
            listOf("default", "copper", "cream", "forest", "sun", "asphalt", "neon", null)
    }

    "a look from the bench on a slot sends the look there to the bench" {
        start.place("x", 1).slots shouldContainExactly listOf("a", "x", "c", null, null, null, null, null)
    }

    "a look from a slot on another slot swaps with the look there" {
        start.place("a", 2).slots shouldContainExactly listOf("c", "b", "a", null, null, null, null, null)
    }

    "a look from a slot on an empty slot leaves its own slot empty" {
        start.place("b", 5).slots shouldContainExactly listOf("a", null, "c", null, null, "b", null, null)
    }

    "a slot out of range changes nothing" {
        start.place("x", KlangLoadout.SIZE) shouldBe start
        start.place("x", -1) shouldBe start
    }

    "unequip empties the slot, without takes a look off" {
        start.unequip(0).slots shouldContainExactly listOf(null, "b", "c", null, null, null, null, null)
        start.without("c").slots shouldContainExactly listOf("a", "b", null, null, null, null, null, null)
        start.slotOf("c") shouldBe 2
        start.slotOf("x") shouldBe null
    }

    "normalized keeps eight slots of known looks, each in one slot only" {
        val odd = KlangLoadout(listOf("a", "gone", "a", "b", null, "c", "d", "e", "f", "g"))

        odd.normalized(known = listOf("a", "b", "c", "d", "e", "f", "g")).slots shouldContainExactly
            listOf("a", null, null, "b", null, "c", "d", "e")
    }
})
