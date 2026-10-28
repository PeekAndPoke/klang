/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.feel

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/** A look as text (KLANG-00382): the round trip, and pasted text checked role by role. */
class KlangLookTextSpec : StringSpec({

    "a look copied as text and pasted comes back whole, under the receiver's id" {
        val look = KlangLookPreset(
            id = "own-3",
            name = "Rust",
            values = mapOf(KlangRole.Accent to "#b04fd6", KlangRole.Radius to "4px", KlangRole.Grain to "0.32"),
            author = "Someone",
            own = true,
        )

        val pasted = KlangLookText.decode(KlangLookText.encode(look), id = "own-9").shouldNotBeNull()

        pasted shouldBe look.copy(id = "own-9")
    }

    "the store's JSON keeps the id" {
        val look = KlangLookPreset(id = "own-1", name = "Mine", values = emptyMap(), own = true)

        KlangLookText.fromJson(KlangLookText.toJson(look, withId = true)) shouldBe look
    }

    "a value that does not fit its role and a name no role has are dropped; the name is cut" {
        val text = """
            {"klang-look": 1, "name": "A rather long name", "author": "x",
             "values": {"--kl-accent": "url(evil)", "--kl-focus": "#123456", "--kl-nope": "#ffffff",
                        "--kl-grain": 2, "--kl-radius": "3px; color: red"}}
        """

        val look = KlangLookText.decode(text, id = "own-1").shouldNotBeNull()

        look.values shouldBe mapOf(KlangRole.Focus to "#123456")
        look.name shouldBe "A rather l"
        look.own shouldBe true
    }

    "text that is not a look is none" {
        KlangLookText.decode("hello", id = "own-1").shouldBeNull()
        KlangLookText.decode("""{"name": "no mark"}""", id = "own-1").shouldBeNull()
        KlangLookText.decode("[1, 2]", id = "own-1").shouldBeNull()
    }

    "a name of only control characters or spaces becomes Mine" {
        KlangLookText.cleanName(" \u0007 ") shouldBe "Mine"
        KlangLookText.cleanName("  Rust  ") shouldBe "Rust"
    }

    "every role is in exactly one of the editor's groups" {
        KlangRoleGroup.entries.flatMap { it.roles }.sortedBy { it.ordinal } shouldContainExactly KlangRole.entries
    }
})
