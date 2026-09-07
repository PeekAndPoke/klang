/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.sprudel.SprudelPattern

/**
 * The long filter names (`lowpass`, `highpass`, `bandpass`) and the short ones (`lpf`, `hpf`,
 * `bpf`) are one object under two names, NOT a deprecation: both spellings must build exactly
 * the same wire definition. Since 2026-09-07 the notch has one name only (`notch`); its old
 * spellings are guarded by `LangRetiredDoorsSpec`.
 */
class LangCanonicalFilterNamesSpec : StringSpec({

    fun filters(code: String): List<FilterDef> =
        SprudelPattern.compile(code)!!.queryArc(0.0, 1.0).first().data.toVoiceData().filters.filters

    // canonical spelling -> short spelling, as full call snippets
    val equivalent = listOf(
        """note("c").lowpass(800, 1.2, 2)""" to """note("c").lpf(800, 1.2, 2)""",
        """note("c").highpass(200, 0.9, 3)""" to """note("c").hpf(200, 0.9, 3)""",
        """note("c").bandpass(1000, 4.0)""" to """note("c").bpf(1000, 4.0)""",
    )

    equivalent.forEach { (canonical, short) ->
        "$canonical builds exactly what $short builds" {
            withClue("$canonical vs $short") {
                filters(canonical) shouldBe filters(short)
            }
        }
    }

    "the canonical names are reachable on all four surface forms" {
        // member, string-receiver, standalone mapper, chained mapper — the shape every
        // sprudel function ships. A name registered on only some of them is half a name.
        filters("""note("c").lowpass(800)""").size shouldBe 1
        filters(""""c".lowpass(800).note()""").size shouldBe 1
        filters("""note("c").apply(lowpass(800))""").size shouldBe 1
        filters("""note("c").apply(gain(0.8).lowpass(800))""").size shouldBe 1
    }

    "the short forms are the same objects as the long ones" {
        (filters("""note("c").lpf(800)""")[0] as FilterDef.LowPass).freq shouldBe 800.0
        (filters("""note("c").hpf(200)""")[0] as FilterDef.HighPass).freq shouldBe 200.0
        (filters("""note("c").bpf(1000)""")[0] as FilterDef.BandPass).freq shouldBe 1000.0
        (filters("""note("c").notch(1500)""")[0] as FilterDef.Notch).freq shouldBe 1500.0
    }
})
