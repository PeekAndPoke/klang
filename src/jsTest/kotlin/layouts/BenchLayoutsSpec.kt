/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.layouts

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.state.BenchLayout
import io.peekandpoke.klang.ui.comp.KlangWorkbench

/** The bench's layout to and from the one the Spine keeps (KLANG-00422): every part carried over, each to its own. */
class BenchLayoutsSpec : StringSpec({

    "every part of the layout goes to the bench and comes back as it was" {
        val kept = BenchLayout(
            pinned = setOf("help", "tools"),
            open = "tools",
            bottomOpen = false,
            bottomPinned = false,
            bottomHeight = 412.5,
            bottomTab = "console",
        )

        benchLayoutOf(kept = kept) shouldBe KlangWorkbench.Layout(
            pinned = setOf("help", "tools"),
            open = "tools",
            bottomOpen = false,
            bottomPinned = false,
            bottomHeight = 412.5,
            bottomTab = "console",
        )
        keptLayoutOf(layout = benchLayoutOf(kept = kept)) shouldBe kept

        withClue("the two switches of the bottom panel, each to its own") {
            keptLayoutOf(layout = KlangWorkbench.Layout(bottomOpen = true, bottomPinned = false)) shouldBe
                BenchLayout(bottomOpen = true, bottomPinned = false, bottomHeight = null)
            benchLayoutOf(kept = BenchLayout(bottomOpen = false, bottomPinned = true)).let { it.bottomOpen to it.bottomPinned } shouldBe
                (false to true)
        }
    }

    "the bench's own height is no choice: it is not kept; any other is" {
        keptLayoutOf(layout = KlangWorkbench.Layout()).bottomHeight shouldBe null
        keptLayoutOf(layout = KlangWorkbench.Layout(bottomHeight = KlangWorkbench.Layout().bottomHeight + 1.0)).bottomHeight shouldBe
            KlangWorkbench.Layout().bottomHeight + 1.0
    }

    "a first visit: the bottom panel open and docked, at the bench's own height" {
        benchLayoutOf(kept = BenchLayout()) shouldBe
            KlangWorkbench.Layout(bottomOpen = true, bottomPinned = true, bottomHeight = KlangWorkbench.Layout().bottomHeight)
    }
})
