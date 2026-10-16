/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * `.ign` and `.kat`, the way a plain number enters the typed Kotlin door (task `kotlin-typed-doors`, step 1,
 * 2026-10-16). What is pinned: each builds the precise `Constant` the script door builds from a bare number, an
 * [Int] and a [Double] give the same tree, and a leading minus stays inside the constant (`-1.ign` is
 * `Constant(-1.0)`, as the script's `-1`), never a negation node around it.
 */
class NumberExtensionsSpec : StringSpec({

    // ── Ignitor ──────────────────────────────────────────────────────────────────────────────────

    "ign builds the Ignitor constant of the number" {
        1.ign shouldBe IgnitorDsl.Constant(1.0)
        0.5.ign shouldBe IgnitorDsl.Constant(0.5)
    }

    "an Int and a Double give the same Ignitor tree" {
        5.ign shouldBe 5.0.ign
    }

    "a leading minus stays inside the Ignitor constant, no Neg node" {
        val negated: IgnitorDsl = -1.ign

        negated shouldBe IgnitorDsl.Constant(-1.0)
        -0.25.ign shouldBe IgnitorDsl.Constant(-0.25)
        -1.0.ign shouldBe IgnitorDsl.Constant(-1.0)
    }

    // ── Katalyst ─────────────────────────────────────────────────────────────────────────────────

    "kat builds the Katalyst constant of the number" {
        0.5.kat shouldBe KatalystKnobDsl.Constant(0.5)
        2.kat shouldBe KatalystKnobDsl.Constant(2.0)
    }

    "an Int and a Double give the same Katalyst tree" {
        3.kat shouldBe 3.0.kat
    }

    "a leading minus stays inside the Katalyst constant, no Neg node" {
        val negated: KatalystKnobDsl = -1.kat

        negated shouldBe KatalystKnobDsl.Constant(-1.0)
        -0.25.kat shouldBe KatalystKnobDsl.Constant(-0.25)
        -1.0.kat shouldBe KatalystKnobDsl.Constant(-1.0)
    }
})
