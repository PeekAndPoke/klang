/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelVoiceData

/**
 * The filter doors `lpf`, `hpf`, `bpf` and `notch`, one table over the four: what a call does to the
 * knobs it does NOT name. The calling forms are `LangDoorFormsSpec`'s; the mapper, the gap, the
 * control pattern per slot and the tail-only guard are `LangFieldAccessorsSpec`'s.
 */
class LangLpfSpec : StringSpec({

    class Filter(
        val name: String,
        val freq: (SprudelVoiceData) -> Double?,
        val q: (SprudelVoiceData) -> Double?,
        val stages: (SprudelVoiceData) -> List<Double?>,
        val freqQ: (SprudelPattern, PatternLike, PatternLike) -> SprudelPattern,
        val attackDecay: (SprudelPattern, PatternLike, PatternLike) -> SprudelPattern,
    )

    val filters = listOf(
        Filter("lpf", { it.cutoff }, { it.resonance }, { listOf(it.lpattack, it.lpdecay, it.lpsustain, it.lprelease) },
            { p, f, q -> p.lpf(f, q) }, { p, a, d -> p.lpf(attack = a, decay = d) }),
        Filter("hpf", { it.hcutoff }, { it.hresonance }, { listOf(it.hpattack, it.hpdecay, it.hpsustain, it.hprelease) },
            { p, f, q -> p.hpf(f, q) }, { p, a, d -> p.hpf(attack = a, decay = d) }),
        Filter("bpf", { it.bandf }, { it.bandq }, { listOf(it.bpattack, it.bpdecay, it.bpsustain, it.bprelease) },
            { p, f, q -> p.bpf(f, q) }, { p, a, d -> p.bpf(attack = a, decay = d) }),
        Filter("notch", { it.notchf }, { it.nresonance }, { listOf(it.nfattack, it.nfdecay, it.nfsustain, it.nfrelease) },
            { p, f, q -> p.notch(f, q) }, { p, a, d -> p.notch(attack = a, decay = d) }),
    )

    // ---- C0 guard: per-param flow (docs/tasks-archive/2026-09/20260927-filter-unification.md, C0) ----

    "x(freq-sequence, q) gives per-event freq and constant q, on all four filters" {
        filters.forEach { f ->
            withClue(f.name) {
                val events = f.freqQ(note("c e"), "200 800", 1.5).queryArc(0.0, 1.0)

                events.size shouldBe 2
                events.map { f.freq(it.data) } shouldBe listOf(200.0, 800.0)
                events.map { f.q(it.data) } shouldBe listOf(1.5, 1.5)
            }
        }
    }

    "lpf(freq-alternation, q) selects freq per cycle, q stays" {
        val p = note("c").lpf("<200 800>", 1.5)
        val c0 = p.queryArc(0.0, 1.0)
        val c1 = p.queryArc(1.0, 2.0)

        c0.size shouldBe 1
        c1.size shouldBe 1
        c0[0].data.cutoff shouldBe 200.0
        c1[0].data.cutoff shouldBe 800.0
        c0[0].data.resonance shouldBe 1.5
        c1[0].data.resonance shouldBe 1.5
    }

    "lpf(q = ...) alone sets only q, cutoff stays untouched" {
        val p = SprudelPattern.compile("""seq("300 600").lpf(q = 2)""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events[0].data.cutoff shouldBe null
        events[1].data.cutoff shouldBe null
        events[0].data.resonance shouldBe 2.0
    }

    "x(q = ...) does not clear a previously set freq, on all four filters, in compiled code" {
        filters.forEach { f ->
            withClue(f.name) {
                val events = SprudelPattern.compile("""note("c3").${f.name}(800).${f.name}(q = 12)""").shouldNotBeNull().queryArc(0.0, 1.0)

                events.size shouldBe 1
                f.freq(events[0].data) shouldBe 800.0
                f.q(events[0].data) shouldBe 12.0
            }
        }
    }

    "a filter call writes only the envelope stages it names: the others stay unset, on all four filters" {
        // A filter has no fill rule (that is the compound Katalyst doors', `LangKatalystParamSpec`): the
        // stages a call leaves out stay unset, and the classic() slot defaults decide them on the wire.
        filters.forEach { f ->
            withClue(f.name) {
                val events = f.attackDecay(note("c3"), 0.01, 0.3).queryArc(0.0, 1.0)

                events.size shouldBe 1
                f.stages(events[0].data) shouldBe listOf(0.01, 0.3, null, null)
            }
        }
    }
})
