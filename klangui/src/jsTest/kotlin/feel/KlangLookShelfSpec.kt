/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.feel

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/** The shelf's ids for new looks (KLANG-00422): never given twice, on this shelf or another. */
class KlangLookShelfSpec : StringSpec({

    "a new look's id is own- and a UUID, another each time, also after a look was deleted" {
        val uuid = Regex("own-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        val ids = List(50) { KlangLookShelf.newId() }

        withClue("the form: $ids") { ids.all { uuid.matches(it) } shouldBe true }
        withClue("each another") { ids.toSet().size shouldBe 50 }

        try {
            val id = KlangLookShelf.newId()
            KlangLookShelf.keep(KlangLookPreset(id = id, name = "Mine", values = emptyMap()))
            KlangLookShelf.delete(id)

            withClue("a deleted look's id is not given again") { (KlangLookShelf.newId() == id) shouldBe false }
        } finally {
            KlangLookShelf.restore(own = emptyList(), slots = null)
        }
    }
})
