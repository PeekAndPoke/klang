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
 * per door (`reverb(wet)`, `delay(wet)`, `phaser(wet)`, `body(wet)`, `vowel(wet)`). Each old name must
 * fail script dispatch as a member call and as a free mapper-factory call; if one resolves
 * again, an alias crept back in (docs/plans/filter-unification.md, chunk C4).
 *
 * C6 finished the job: `phd`/`phasdp` are deleted, and so are `reverb` (a full-signature
 * duplicate of the reverb send; it returned 2026-09-16 as the compound object that replaced
 * `room`, guarded in [LangRetiredDoorsSpec]) and `vibmod` (of `vibratoMod`).
 * The `roomWet`/`delayWet`/`phaserWet` spellings themselves retired 2026-09-07 into the compound
 * objects; [LangRetiredDoorsSpec] guards those.
 *
 * The wire fields (`delay`, `phaserDepth`, `bodyMix`, `vowelMix`) keep their old spelling on
 * purpose; only the DSL surface renamed. The reverb's wire fields follow its door word since
 * 2026-09-16 (`reverb`, `reverbSize`, `reverbLowpass`).
 */
class LangDeletedWetNamesSpec : StringSpec({

    val deleted = listOf(
        // "room" and "delay" returned 2026-09-07 as the compound objects room(...) and delay(...);
        // "reverb" returned 2026-09-16 as the compound object reverb(...), replacing room(...).
        "phaserdepth", "bodyMix", "vowelMix", "phd", "phasdp", "vibmod",
    )

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
