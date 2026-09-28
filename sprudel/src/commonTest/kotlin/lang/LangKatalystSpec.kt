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
 * the mapper forms stamp the reference onto sounding events. Like `master(…)`, the door REPLACES
 * (decided 2026-09-18): a chain is one instrument, and `k.classic()` inside the builder is how you
 * start from the familiar one.
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

    // ── the door replaces, like sound() and master() (decided 2026-09-18) ─────

    "two doors REPLACE: x.katalyst(A).katalyst(B) is B, and nothing of A survives" {
        val events = note("c3").katalyst(chainA).katalyst(chainB).queryArc(0.0, 1.0)

        events[0].data.katalyst shouldBe KatalystValue.Dsl(chainB)
        // Spelled out as the stage list too: the append rule would have left A's Eq in front of
        // B's Reverb, and two chains that both declare a reverb read the same slot names.
        (events[0].data.katalyst as KatalystValue.Dsl).katalyst.stages.map { it::class.simpleName } shouldBe
                listOf("Reverb")
    }

    "the replacing door carries the plain chain's own unique id, not a composition's" {
        val events = note("c3").katalyst(chainA).katalyst(chainB).queryArc(0.0, 1.0)

        events[0].data.toVoiceData().katalyst shouldBe chainB.uniqueId()
    }

    "every one of the four door forms replaces, on every event" {
        // All four, because the rule lives in four places (the carrier, the pattern door, the
        // string door, the chained mapper) and a door that still appended would be invisible in a
        // spec that only exercised one of them.
        listOf(
            "pattern.katalyst(A).katalyst(B)" to note("c3 e3").katalyst(chainA).katalyst(chainB),
            "string.katalyst(A).katalyst(B)" to "c3 e3".katalyst(chainA).katalyst(chainB),
            "chained mapper .katalyst(A).katalyst(B)" to
                    note("c3 e3").apply(gain(0.5).katalyst(chainA).katalyst(chainB)),
            "carrier under a pattern door" to katalyst(chainA).katalyst(chainB),
        ).forEach { (label, pattern) ->
            withClue(label) {
                val events = pattern.queryArc(0.0, 1.0)

                events.shouldNotBeEmpty()
                events.forEach { it.data.katalyst shouldBe KatalystValue.Dsl(chainB) }
            }
        }
    }

    "one door, one KatalystValue instance: every event of a pattern gets the SAME one" {
        // Reference equality, not structural: the door stamps a value it allocated once, so a
        // pattern of four notes costs one instance and one `uniqueId()` hash, not four. The
        // per-event cost is what the retired append memo existed for; replacing gets it for free.
        val events = note("c3 e3 g3 a3").katalyst(chainA).queryArc(0.0, 1.0)

        events.size shouldBe 4
        val first = events[0].data.katalyst

        events.forEach { it.data.katalyst shouldBeSameInstance first }

        // The carrier and the chained mapper hand out one instance each as well.
        val carrier = katalyst(chainA).queryArc(0.0, 4.0)

        carrier.size shouldBe 4
        carrier.forEach { it.data.katalyst shouldBeSameInstance carrier[0].data.katalyst }

        val mapped = note("c3 e3 g3").apply(gain(0.5).katalyst(chainB)).queryArc(0.0, 1.0)

        mapped.size shouldBe 3
        mapped.forEach { it.data.katalyst shouldBeSameInstance mapped[0].data.katalyst }
    }

    "an outer door replaces each stacked pattern's own chain" {
        // stack(a.katalyst(A), b).katalyst(B): both get B. The chain is a property of the ORBIT,
        // and an outer door is the author saying what that orbit runs.
        val pattern = stack(note("c3").katalyst(chainA), note("e3")).katalyst(chainB)
        val events = pattern.queryArc(0.0, 1.0)

        events.size shouldBe 2
        val byNote = events.associate { it.data.note to (it.data.katalyst as KatalystValue.Dsl).katalyst }

        byNote["c3"] shouldBe chainB
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

    "the script door replaces too" {
        val script = SprudelPattern
            .compile("""note("c3").katalyst(Katalyst(k => k.reverb())).katalyst(Katalyst(k => k.gain(1.4)))""")!!
            .queryArc(0.0, 1.0)

        script[0].data.katalyst shouldBe KatalystValue.Dsl(
            KatalystDsl.of(KatalystStageDsl.Gain(IgnitorDsl.Constant(1.4)))
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
