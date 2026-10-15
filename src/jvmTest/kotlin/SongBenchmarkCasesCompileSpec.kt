/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.lang.sprudelLib

/**
 * Every song benchmark case, the frozen reference songs included, compiles to a pattern.
 *
 * The benchmark corpus lives in `jvmMain` and no other suite compiles it, so a DSL surface change
 * (a retired door, a renamed knob) used to break it silently until someone benchmarked. Found
 * 2026-09-07 when the envelope doors were removed: `FrozenSongs` and the LEAD ladder still called
 * `.release(...)`.
 *
 * Kind: for the future: every text it builds is frozen or transcribed, so it goes red on a DSL surface change and
 * never on a song edit (`CLAUDE.md`, "Two kinds of check").
 */
class SongBenchmarkCasesCompileSpec : StringSpec({
    fun engine() = klangScript {
        registerLibrary(sprudelLib)
        registerBuiltInSongsAsModules()
    }

    "every song benchmark case compiles to a SprudelPattern" {
        val cases = SongBenchmarkCases.all()
        cases.isNotEmpty() shouldBe true

        for (case in cases) {
            withClue(case.name) { SprudelPattern.compile(engine(), case.code).shouldNotBeNull() }
        }
    }

    "the rig ablation and the frozen ledger build and compile: every text anchor matches the frozen song it edits" {
        // `rig()` rewrites the FROZEN Der Schmetterling through text anchors that fail loudly when the text does not
        // contain them, and `all()` does not build it. Building the cases here is what runs the anchors. The ledger's
        // live rows read the live song and are left out: songs move freely.
        val cases = SongBenchmarkCases.rig() + SongBenchmarkCases.ledgerFrozen()
        cases.isNotEmpty() shouldBe true

        for (case in cases) {
            withClue(case.name) { SprudelPattern.compile(engine(), case.code).shouldNotBeNull() }
        }
    }
})
