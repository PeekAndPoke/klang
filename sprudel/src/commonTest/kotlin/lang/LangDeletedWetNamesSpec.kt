/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldContain
import io.peekandpoke.klang.sprudel.SprudelPattern

/**
 * C4.2 guard: the pre-C4 wet-knob names are DELETED, not deprecated — the knob has ONE name
 * per door (`roomWet`, `delayWet`, `phaserWet`, `bodyWet`, `vowelWet`). Each old name must
 * fail script dispatch as a member call and as a free mapper-factory call; if one resolves
 * again, an alias crept back in (docs/plans/filter-unification.md, chunk C4).
 *
 * C6 finished the job: `phd`/`phasdp` are now deleted too and join the list below. The wire
 * fields (`room`, `delay`, `phaserDepth`, `bodyMix`, `vowelMix`) keep their old
 * spelling on purpose; only the DSL surface renamed.
 */
class LangDeletedWetNamesSpec : StringSpec({

    val deleted = listOf("room", "delay", "phaserdepth", "bodyMix", "vowelMix", "phd", "phasdp")

    deleted.forEach { name ->
        "deleted wet name '$name' fails as a member call" {
            val error = shouldThrowAny {
                SprudelPattern.compile("""note("c4").$name(0.5)""")
            }
            withClue("error should name the missing method") {
                (error.message ?: "") shouldContain name
            }
        }

        "deleted wet name '$name' fails as a free mapper call" {
            val error = shouldThrowAny {
                SprudelPattern.compile("""note("c4").apply($name(0.5))""")
            }
            withClue("error should name the missing function") {
                (error.message ?: "") shouldContain name
            }
        }
    }
})
