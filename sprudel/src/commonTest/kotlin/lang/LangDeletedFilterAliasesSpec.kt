/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldContain
import io.peekandpoke.klang.sprudel.SprudelPattern

/**
 * C6a guard: the strudel-heritage filter aliases are DELETED, not deprecated.
 *
 * Each name below must fail script dispatch — as a member call and as a free
 * mapper-factory call. If one of these starts resolving again, an alias crept
 * back in (docs/plans/filter-unification.md, chunk C6a).
 *
 * The notch family (notchf/notchq/nresonance/nf*) is deliberately absent: it is
 * carved out until C6.
 */
class LangDeletedFilterAliasesSpec : StringSpec({

    val deleted = listOf(
        // filter freq
        "cutoff", "ctf", "lp", "hp", "hcutoff", "bp", "bandf",
        // filter q
        "res", "resonance", "hres", "hresonance", "bandq",
        // envelope depth
        "lpenv", "hpenv", "bpenv",
        // envelope shape (lpadsr/hpadsr/bpadsr survive instead)
        "lpattack", "lpa", "lpdecay", "lpd", "lpsustain", "lps", "lprelease", "lpr",
        "hpattack", "hpa", "hpdecay", "hpd", "hpsustain", "hps", "hprelease", "hpr",
        "bpattack", "bpa", "bpdecay", "bpd", "bpsustain", "bps", "bprelease", "bpr",
    )

    deleted.forEach { name ->
        "deleted alias '$name' fails as a member call" {
            val error = shouldThrowAny {
                SprudelPattern.compile("""note("c4").$name(0.5)""")
            }
            withClue("error should name the missing method") {
                (error.message ?: "") shouldContain name
            }
        }

        "deleted alias '$name' fails as a free mapper call" {
            val error = shouldThrowAny {
                SprudelPattern.compile("""note("c4").apply($name(0.5))""")
            }
            withClue("error should name the missing function") {
                (error.message ?: "") shouldContain name
            }
        }
    }
})
