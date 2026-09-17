/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import io.peekandpoke.klang.audio_bridge.KatalystValue
import io.peekandpoke.klang.audio_bridge.uniqueId
import io.peekandpoke.klang.sprudel.SprudelPattern

/**
 * The `katalyst(…)` authoring surface, see `docs/tasks/katalyst-dsl.md` §6.
 *
 * The top-level form is a **control carrier** (one silent event per cycle, routed with `.orbit(n)`);
 * the mapper forms stamp the reference onto sounding events. Unlike `master(…)`, the door APPENDS:
 * the pattern text is the stage order.
 */
class LangKatalystSpec : StringSpec({

    val chainA = KatalystDsl.of(KatalystStageDsl.Eq(sections = listOf(IgnitorDsl.EqSection.Lowpass())))
    val chainB = KatalystDsl.of(KatalystStageDsl.Reverb(wet = IgnitorDsl.Constant(0.3)))

    "katalyst() emits one control event per cycle carrying only the chain" {
        val events = katalyst(chainA).queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.katalyst shouldBe KatalystValue.Dsl(chainA)
        events[0].data.control shouldBe true
        // Nothing else: the carrier must not accidentally sound.
        events[0].data.sound.shouldBeNull()
        events[0].data.freqHz.shouldBeNull()
    }

    "the carrier is an ordinary pattern, so .orbit(n) routes it" {
        val events = katalyst(chainA).orbit(5).queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.cylinder shouldBe 5
        events[0].data.control shouldBe true
        events[0].data.katalyst shouldBe KatalystValue.Dsl(chainA)
    }

    "katalyst() keeps emitting across cycles" {
        val events = katalyst(chainA).queryArc(0.0, 4.0)

        events.size shouldBe 4
        events.forEach { it.data.control shouldBe true }
    }

    "pattern.katalyst() stamps sounding events, and they still sound" {
        val events = note("c3 e3").katalyst(chainA).queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.forEach {
            it.data.katalyst shouldBe KatalystValue.Dsl(chainA)
            // NOT a control event: the note plays, the chain declaration just rides along.
            it.data.control.shouldBeNull()
        }
    }

    "katalyst dsl interface: pattern / string / chained-mapper forms agree" {
        val pat = "c3"

        listOf(
            "pattern.katalyst(v)" to note(pat).katalyst(chainA),
            "string.katalyst(v)" to pat.katalyst(chainA),
            "chained mapper .katalyst(v)" to note(pat).apply(gain(0.5).katalyst(chainA)),
        ).forEach { (label, pattern) ->
            withClue(label) {
                val events = pattern.queryArc(0.0, 1.0)
                events.shouldNotBeEmpty()
                events[0].data.katalyst shouldBe KatalystValue.Dsl(chainA)
            }
        }
    }

    // ── composition: the one place a Katalyst differs from a master ───────────

    "two doors APPEND: x.katalyst(A).katalyst(B) is the concatenation, in that order" {
        val events = note("c3").katalyst(chainA).katalyst(chainB).queryArc(0.0, 1.0)
        val expected = KatalystDsl(chainA.stages + chainB.stages)

        events[0].data.katalyst shouldBe KatalystValue.Dsl(expected)
        // ...and A really is first: the order is the signal order, so a reversed concatenation is
        // a different mix, not a different spelling of the same one.
        (events[0].data.katalyst as KatalystValue.Dsl).katalyst.stages.map { it::class.simpleName } shouldBe
                listOf("Eq", "Reverb")
    }

    "a composed chain has the same unique id as the hand-built concatenation" {
        val events = note("c3").katalyst(chainA).katalyst(chainB).queryArc(0.0, 1.0)

        events[0].data.toVoiceData().katalyst shouldBe KatalystDsl(chainA.stages + chainB.stages).uniqueId()
    }

    "the door's memo hands every event of one pattern the SAME composed instance" {
        // Reference equality, not structural: this is what pins the memo. Without it the door
        // allocates a fresh stage list, a fresh data class and a structural hash per event.
        val events = note("c3 e3 g3 a3").katalyst(chainA).katalyst(chainB).queryArc(0.0, 1.0)

        events.size shouldBe 4
        val first = (events[0].data.katalyst as KatalystValue.Dsl).katalyst
        events.forEach { (it.data.katalyst as KatalystValue.Dsl).katalyst shouldBeSameInstance first }
    }

    "the memo is capped: past LIMIT distinct incoming chains it composes without remembering" {
        // The memo exists for the steady state, where one door sees one or a handful of incoming
        // chains. A pathological source must not turn it into an unbounded map, so past the cap it
        // keeps composing and stops storing. Correctness never depends on the memo, which is what
        // this pins: the (LIMIT + 1)th chain composes exactly like the first.
        KatalystAppend.LIMIT shouldBe 8

        val inners = (0 until KatalystAppend.LIMIT + 1).map { i ->
            KatalystDsl.of(KatalystStageDsl.Gain(gain = IgnitorDsl.Constant(i.toDouble())))
        }
        val stacked = stack(
            *inners.mapIndexed { i, chain -> note("c$i").katalyst(chain) }.toTypedArray()
        ).katalyst(chainB)

        val events = stacked.queryArc(0.0, 1.0)

        events.size shouldBe KatalystAppend.LIMIT + 1

        // Every one of them, memoized or not, carries its own chain plus the outer one.
        val byNote = events.associate { it.data.note to (it.data.katalyst as KatalystValue.Dsl).katalyst }

        inners.forEachIndexed { i, inner ->
            withClue("note c$i (index $i, cap is ${KatalystAppend.LIMIT})") {
                byNote["c$i"] shouldBe KatalystDsl(inner.stages + chainB.stages)
            }
        }
    }

    "a chain referenced by NAME is replaced, not appended: named chains do not compose yet" {
        // The stages behind a name live in the backend registry, so this door cannot concatenate
        // them. Replacing is the honest answer, and it is the door's rule, so it is pinned on the
        // door's rule object rather than through a pattern that cannot produce a Named value yet
        // (no sprudel surface mints one today; the wire and a future `katalyst("name")` will).
        val door = KatalystAppend(chainB)

        door.onto(KatalystValue.Named("guitarBus")) shouldBe KatalystValue.Dsl(chainB)

        // The three branches of the rule, side by side: nothing, a name, and an inline chain.
        door.onto(null) shouldBe KatalystValue.Dsl(chainB)
        door.onto(KatalystValue.Dsl(chainA)) shouldBe KatalystValue.Dsl(KatalystDsl(chainA.stages + chainB.stages))

        // ...and the two replacing branches hand back the SAME instance, which is what makes the
        // carrier free per event.
        door.onto(null) shouldBeSameInstance door.onto(KatalystValue.Named("other"))
    }

    "an outer door appends to each stacked pattern's own chain" {
        // stack(a.katalyst(A), b).katalyst(B): a's events get A+B, b's events get B alone.
        val pattern = stack(note("c3").katalyst(chainA), note("e3")).katalyst(chainB)
        val events = pattern.queryArc(0.0, 1.0)

        events.size shouldBe 2
        val byNote = events.associate { it.data.note to (it.data.katalyst as KatalystValue.Dsl).katalyst }

        byNote["c3"] shouldBe KatalystDsl(chainA.stages + chainB.stages)
        byNote["e3"] shouldBe chainB
    }

    "inline katalyst denormalizes to its synthetic name on the wire" {
        val events = katalyst(chainA).queryArc(0.0, 1.0)
        val voiceData = events[0].data.toVoiceData()

        voiceData.katalyst shouldBe chainA.uniqueId()
        voiceData.control shouldBe true
    }

    "a pattern without katalyst() carries none" {
        val events = note("c3").queryArc(0.0, 1.0)

        events[0].data.katalyst.shouldBeNull()
        events[0].data.toVoiceData().katalyst.shouldBeNull()
    }

    "the field merges last-writer-wins: composing is the door's job, not the merge's" {
        val carrier = katalyst(chainB).queryArc(0.0, 1.0)[0].data
        val note = note("c3").katalyst(chainA).queryArc(0.0, 1.0)[0].data

        val merged = note.merge(carrier)
        merged.katalyst shouldBe KatalystValue.Dsl(chainB)

        // ...and the in-place mirror must agree (guarded against drift by SprudelVoiceDataSpec).
        val inPlace = note("c3").katalyst(chainA).queryArc(0.0, 1.0)[0].data
        inPlace.mergeFrom(carrier)
        inPlace.katalyst shouldBe KatalystValue.Dsl(chainB)
    }

    "control does not merge: a katalyst carrier cannot silence real notes" {
        val carrier = katalyst(chainA).queryArc(0.0, 1.0)[0].data
        val note = note("c3").queryArc(0.0, 1.0)[0].data

        carrier.control shouldBe true

        val merged = note.merge(carrier)
        merged.control.shouldBeNull()
        merged.katalyst shouldBe KatalystValue.Dsl(chainA)
    }

    // ── the script door ──────────────────────────────────────────────────────

    "katalyst() is available from KlangScript with the same result" {
        val kotlinEvents = katalyst(KatalystDsl.of(KatalystStageDsl.Gain(IgnitorDsl.Constant(1.4))))
            .queryArc(0.0, 1.0)
        val scriptEvents = SprudelPattern
            .compile("""katalyst(Katalyst(k => k.gain(1.4)))""")!!
            .queryArc(0.0, 1.0)

        scriptEvents.size shouldBe kotlinEvents.size
        scriptEvents[0].data.katalyst shouldBe kotlinEvents[0].data.katalyst
        scriptEvents[0].data.control shouldBe true
    }

    "the script door appends too" {
        val script = SprudelPattern
            .compile("""note("c3").katalyst(Katalyst(k => k.reverb())).katalyst(Katalyst(k => k.gain(1.4)))""")!!
            .queryArc(0.0, 1.0)

        script[0].data.katalyst shouldBe KatalystValue.Dsl(
            KatalystDsl.of(KatalystStageDsl.Reverb(), KatalystStageDsl.Gain(IgnitorDsl.Constant(1.4)))
        )
    }

    "KatalystValue.Dsl.name is the chain's synthetic name, computed on READ not on construction" {
        // Parity first: `name` must be the same door the rest of the code uses.
        val chain = KatalystDsl.of(KatalystStageDsl.Gain(gain = IgnitorDsl.Constant(4321.0)))
        val value = KatalystValue.Dsl(chain)

        value.name shouldBe chain.uniqueId()
        value.name shouldBe value.name

        // Laziness, observably: the identity map hands out monotonic `katalyst-N` names, so if the
        // name were computed in the constructor the numbers would follow CONSTRUCTION order. Build
        // two never-before-seen chains in one order and read them in the other; the numbers must
        // follow the reads.
        val first = KatalystValue.Dsl(KatalystDsl.of(KatalystStageDsl.Gain(gain = IgnitorDsl.Constant(9911.0))))
        val second = KatalystValue.Dsl(KatalystDsl.of(KatalystStageDsl.Gain(gain = IgnitorDsl.Constant(9912.0))))

        val secondNumber = second.name.substringAfterLast('-').toInt()
        val firstNumber = first.name.substringAfterLast('-').toInt()

        withClue("read order was second (${second.name}) then first (${first.name})") {
            (secondNumber < firstNumber) shouldBe true
        }
    }

    "Katalyst.classic() is the historical chain, from both languages" {
        val kotlinEvents = katalyst(KatalystDsl.classic).queryArc(0.0, 1.0)
        val scriptEvents = SprudelPattern.compile("""katalyst(Katalyst.classic())""")!!.queryArc(0.0, 1.0)

        scriptEvents[0].data.katalyst shouldBe kotlinEvents[0].data.katalyst
        scriptEvents[0].data.katalyst shouldBe KatalystValue.Dsl(KatalystDsl.classic)
    }
})

/** Reference equality, spelled out so the failure message says what it means. */
private infix fun Any?.shouldBeSameInstance(other: Any?) {
    (this === other) shouldBe true
}
