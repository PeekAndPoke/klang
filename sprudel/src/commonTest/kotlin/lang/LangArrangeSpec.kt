/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.EPSILON
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.soundName

class LangArrangeSpec : StringSpec({

    "arrange() with simple patterns defaults to 1 cycle each" {
        // Given two patterns without duration specification
        val p = arrange(sound("bd"), sound("hh"))

        // When querying two cycles
        val events = p.queryArc(0.0, 2.0).sortedBy { it.part.begin }

        // Then each pattern takes 1 cycle
        events.size shouldBe 2
        events[0].data.soundName shouldBe "bd"
        events[0].part.begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].part.end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)

        events[1].data.soundName shouldBe "hh"
        events[1].part.begin.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
        events[1].part.end.toDouble() shouldBe (2.0 plusOrMinus EPSILON)
    }

    "arrange() with duration specification [2, pattern]" {
        // Given a pattern that should play for 2 cycles
        val p = arrange(listOf(2, sound("bd")), sound("hh"))

        // When querying three cycles
        val events = p.queryArc(0.0, 3.0).sortedBy { it.part.begin }

        // Then bd plays for 2 cycles, hh for 1
        events.size shouldBe 3

        // First two cycles: bd
        events[0].data.soundName shouldBe "bd"
        events[0].part.begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].part.end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
        events[1].data.soundName shouldBe "bd"
        events[1].part.begin.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
        events[1].part.end.toDouble() shouldBe (2.0 plusOrMinus EPSILON)

        // Third cycle: hh
        events[2].data.soundName shouldBe "hh"
        events[2].part.begin.toDouble() shouldBe (2.0 plusOrMinus EPSILON)
        events[2].part.end.toDouble() shouldBe (3.0 plusOrMinus EPSILON)
    }

    "arrange() works as method on SprudelPattern" {
        // sound("bd").arrange(sound("hh")) -> arrange(sound("bd"), sound("hh"))
        val p = sound("bd").arrange(sound("hh"))

        val events = p.queryArc(0.0, 2.0).sortedBy { it.part.begin }

        events.size shouldBe 2
        events[0].data.soundName shouldBe "bd"
        events[1].data.soundName shouldBe "hh"
    }

    "arrange() works as extension on String" {
        // "bd".arrange("hh") -> arrange(sound("bd"), sound("hh"))?
        // Wait, "bd" as pattern via defaultModifier goes to 'note' or 'value'.
        // But `sound("bd")` puts it in `sound`.
        // If we use `"bd".arrange("hh")`, "bd" becomes a pattern via defaultModifier (note/value).
        // So we check for note/value.

        val p = "bd".arrange("hh")

        val events = p.queryArc(0.0, 2.0).sortedBy { it.part.begin }

        events.size shouldBe 2
        // "bd" -> value="bd" (as VoiceValue.Text)
        // "hh" -> value="hh"
        // Note: this assumes "bd" isn't parsed as sound("bd") automatically unless we use sound("bd").

        // Let's check what defaultModifier does. It sets note and value.
        // So we check note.
        events[0].data.value?.asString shouldBe "bd"
        events[1].data.value?.asString shouldBe "bd"
    }

    "arrange() works in compiled code" {
        val p = SprudelPattern.compile("""arrange(sound("bd"), sound("hh"))""")
        val events = p?.queryArc(0.0, 2.0)?.sortedBy { it.part.begin } ?: emptyList()

        events.size shouldBe 2
        events[0].data.soundName shouldBe "bd"
        events[1].data.soundName shouldBe "hh"
    }

    "arrange() works as method in compiled code" {
        val p = SprudelPattern.compile("""sound("bd").arrange(sound("hh"))""")
        val events = p?.queryArc(0.0, 2.0)?.sortedBy { it.part.begin } ?: emptyList()

        events.size shouldBe 2
        events[0].data.soundName shouldBe "bd"
        events[1].data.soundName shouldBe "hh"
    }

    "arrange() drops no onsets across a section whose total does not divide the tick grid" {
        // Regression for "irishLamentTechno" bassline aussetzer: a bass with 8 onsets/cycle
        // placed in a 16-cycle arrange segment, with a total arrangement length (212) that does
        // NOT divide CycleTime.T. The old fast(dur).withWeight(dur)+slow(total) formulation
        // scaled query time through three rounded steps, nudging some per-cycle boundaries a tick
        // past n*T and silently dropping that cycle's downbeat across the half-open query seam.
        val bass = note("a1!8") // 8 eighth-note onsets per cycle

        val arranged = arrange(
            listOf(16, bass),
            listOf(196, silence), // pad total to 212 (= 4*53, does not divide T)
        )

        for (n in 0 until 16) {
            val direct = bass.queryArc(n.toDouble(), n + 1.0).count { it.isOnset }
            val viaArrange = arranged.queryArc(n.toDouble(), n + 1.0).count { it.isOnset }

            withClue("cycle $n: arrange must preserve every onset the bass emits standalone") {
                viaArrange shouldBe direct
            }
        }
    }
})
