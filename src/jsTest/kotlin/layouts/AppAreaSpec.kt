/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.layouts

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.Nav
import io.peekandpoke.kraft.routing.Route1
import io.peekandpoke.kraft.routing.Static

/**
 * `AppArea.of` (KLANG-00341): its rules, on routes of the spec's own, not the app's route table, which moves with the
 * layout work (the maintainer, 2026-10-26: "please do not do it too tightly at the moment").
 */
class AppAreaSpec : StringSpec({

    "a route under the tutorials' base is Learn, though that base lies under the manuals' base" {
        AppArea.of(Route1("${Nav.tutorialsBase}/{slug}")) shouldBe AppArea.Learn
        AppArea.of(Static("${Nav.manualsBase}/some-page")) shouldBe AppArea.Docs
    }

    "the Dev Tools' base and every route under it are the Dev Tools; a name that only starts the same is not" {
        AppArea.of(Static(Nav.devToolsBase)) shouldBe AppArea.DevTools
        AppArea.of(Static("${Nav.devToolsBase}/some-tool")) shouldBe AppArea.DevTools
        AppArea.of(Static("${Nav.devToolsBase}-else")) shouldBe AppArea.Other
    }

    "a route of no area is Other" {
        AppArea.of(Static("/no-such-page")) shouldBe AppArea.Other
    }
})
