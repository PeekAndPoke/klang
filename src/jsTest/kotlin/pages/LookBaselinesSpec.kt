/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.ui.feel.KlangLookPreset
import io.peekandpoke.klang.ui.feel.KlangLooks
import io.peekandpoke.klang.ui.feel.KlangRole

/**
 * What the look editor's Reset goes back to (KLANG-00444): a look of the user's own as it was when the page first
 * showed it, however often it was changed and kept since.
 */
class LookBaselinesSpec : StringSpec({

    val mine = KlangLookPreset(
        id = "own-1",
        name = "Mine",
        values = mapOf(KlangRole.Accent to "#111111", KlangRole.Glow to "0.2"),
        own = true,
    )

    "a look first seen is remembered as it is; changed and kept since, it is still that" {
        val baselines = LookBaselines()

        baselines.seen(mine)

        withClue("nothing changed yet: Reset has nothing to do") {
            baselines.changed(mine) shouldBe false
            baselines.changes(mine) shouldBe 0
        }

        val edited = mine.copy(values = mine.values + (KlangRole.Accent to "#222222"))

        // The editor shows the look again after every change it kept
        baselines.seen(edited)

        baselines.of(edited) shouldBe mine
        baselines.changed(edited) shouldBe true
        baselines.changes(edited) shouldBe 1

        withClue("changed back by hand: nothing to reset") {
            baselines.changed(edited.copy(values = mine.values)) shouldBe false
        }
    }

    "a change counts once per role: one changed, one taken off, one added, and the name" {
        val baselines = LookBaselines()

        baselines.seen(mine)

        val edited = mine.copy(
            name = "Mine 2",
            values = mapOf(KlangRole.Accent to "#222222", KlangRole.Focus to "#333333"),
        )

        baselines.changes(edited) shouldBe 4

        withClue("the name alone") { baselines.changes(mine.copy(name = "Other")) shouldBe 1 }
    }

    "each look has its own; a look never seen is its own baseline" {
        val baselines = LookBaselines()
        val other = mine.copy(id = "own-2", name = "Other")

        baselines.seen(mine)

        baselines.of(other) shouldBe other
        baselines.changed(other) shouldBe false

        baselines.seen(other)
        baselines.of(other.copy(name = "Renamed")) shouldBe other
        baselines.of(mine.copy(name = "Renamed")) shouldBe mine
    }

    "a built-in look is read only and never remembered" {
        val baselines = LookBaselines()

        baselines.seen(KlangLooks.Copper)

        // Were it remembered, a look of the user's with its id would count against it
        baselines.of(KlangLooks.Copper.copy(name = "Changed")).name shouldBe "Changed"
    }

    "a deleted look is forgotten: one made under its id later starts anew" {
        val baselines = LookBaselines()

        baselines.seen(mine)
        baselines.forget(mine.id)

        val again = mine.copy(name = "Again")

        baselines.seen(again)
        baselines.of(again) shouldBe again
    }
})
