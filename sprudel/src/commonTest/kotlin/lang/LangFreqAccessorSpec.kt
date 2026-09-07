/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests
import io.peekandpoke.klang.tones.Tones

/**
 * The `freq` field accessor and the mapper-argument rule (`docs/tasks-archive/2026-09/20260907-sprudel-field-accessors.md`).
 *
 * A mapper handed to a setter applies to the setter's own field; bare `freq` is the mapper that
 * reads the frequency into the value register. Timing core: every case runs over 12 cycles.
 */
class LangFreqAccessorSpec : StringSpec({

    fun hz(note: String): Double = Tones.noteToFreq(note)

    fun SprudelPattern.cycles(n: Int = 12) = (0 until n).map { c -> queryArc(c.toDouble(), c + 1.0) }

    "a novice violin player: freq(mul(perlin.seg(4).range(0.95, 1.05))) wobbles every note within 5 percent" {
        val ratios = mutableSetOf<Double>()

        // mul() takes its structure from the control, so the segmented perlin fragments the notes;
        // only the onset fragments are scheduled and only they are judged here.
        note("a c e").freq(mul(perlin.seg(4).range(0.95, 1.05))).cycles().forEach { all ->
            val events = all.filter { it.isOnset }
            events shouldHaveSize 3

            val cycleRatios = events.map { e ->
                val ratio = e.data.freqHz.shouldNotBeNull() / hz(e.data.note.shouldNotBeNull())
                (ratio in 0.95 - 1e-9..1.05 + 1e-9) shouldBe true
                ratio
            }

            // The wobble is per note, not per cycle: the three onsets of one cycle must not agree.
            (cycleRatios.toSet().size > 1) shouldBe true
            ratios.addAll(cycleRatios)
        }

        (ratios.size > 3) shouldBe true
    }

    "chords: every note maps its own frequency | note(\"[a,c,e]\").freq(mul(2))" {
        note("[a,c,e]").freq(mul(2)).cycles().forEach { events ->
            events shouldHaveSize 3
            events.forEach { it.data.freqHz shouldBe (hz(it.data.note.shouldNotBeNull()) * 2 plusOrMinus 1e-9) }
        }
    }

    "regression guard: a mapper argument no longer clears the field | note(\"c e\").freq(add(50))" {
        note("c e").freq(add(50)).cycles().forEach { events ->
            events.map { it.data.freqHz } shouldBe listOf(hz("c") + 50, hz("e") + 50)
        }
    }

    "reading a field into another | note(\"c e g a\").bpf(freq)" {
        note("c e g a").bpf(freq).cycles().forEach { events ->
            events shouldHaveSize 4
            events.forEach {
                it.data.freqHz.shouldNotBeNull()
                it.data.bandf shouldBe it.data.freqHz
            }
        }
    }

    "first step of a mapper chain on the accessor | bpf(freq.mul(2))" {
        note("c e g a").bpf(freq.mul(2)).cycles().forEach { events ->
            events shouldHaveSize 4
            events.forEach { it.data.bandf shouldBe (it.data.freqHz.shouldNotBeNull() * 2 plusOrMinus 1e-9) }
        }
    }

    "a mapper on bpf's own field | note(\"c\").bpf(800).bpf(mul(2))" {
        note("c").bpf(800).bpf(mul(2)).cycles().forEach { events ->
            events shouldHaveSize 1
            events.single().data.bandf shouldBe 1600.0
        }
    }

    "self reference: freq(freq.add(50)) equals freq(add(50))" {
        val viaAccessor = note("c e").freq(freq.add(50)).cycles().map { events -> events.map { it.data.freqHz } }
        val viaMapper = note("c e").freq(add(50)).cycles().map { events -> events.map { it.data.freqHz } }

        viaAccessor shouldBe viaMapper
    }

    "order matters: an accessor reads what the chain has set so far | s(\"saw\").bpf(freq)" {
        s("saw").bpf(freq).cycles().forEach { events ->
            events shouldHaveSize 1
            events.single().data.freqHz.shouldBeNull()
            events.single().data.bandf.shouldBeNull()
        }
    }

    "order matters: the accessor reads before a later note() writes | s(\"saw\").bpf(freq).note(\"c e\")" {
        s("saw").bpf(freq).note("c e").cycles().forEach { events ->
            events shouldHaveSize 1
            events.forEach {
                it.data.freqHz.shouldNotBeNull()
                it.data.bandf.shouldBeNull()
            }
        }
    }

    "the value register is drained after the write | note(\"c e\").freq(mul(2))" {
        note("c e").freq(mul(2)).cycles().forEach { events ->
            events shouldHaveSize 2
            events.forEach {
                it.data.value.shouldBeNull()
                it.data.freqHz shouldBe (hz(it.data.note.shouldNotBeNull()) * 2 plusOrMinus 1e-9)
            }
        }
    }

    "a script arrow function in the mapper slot is ignored, never thrown, and leaves the field alone" {
        val p = SprudelPattern.compile("""note("c e").freq(x => 42)""").shouldNotBeNull()

        p.cycles().forEach { events ->
            events.map { it.data.freqHz } shouldBe listOf(hz("c"), hz("e"))
        }
    }

    "the setter is unchanged in both doors" {
        dslInterfaceTests(
            "freq(432)" to note("a c e").freq(432),
            "script freq(432)" to SprudelPattern.compile("""note("a c e").freq(432)"""),
            "freq(\"440 880\")" to note("a c e").freq("440 880"),
            "script freq(\"440 880\")" to SprudelPattern.compile("""note("a c e").freq("440 880")"""),
            "apply(freq(432))" to note("a c e").apply(freq(432)),
            "script apply(freq(432))" to SprudelPattern.compile("""note("a c e").apply(freq(432))"""),
        ) { _, events ->
            events shouldHaveSize 3
            events.forEach { it.data.freqHz.shouldNotBeNull() }
        }
    }

    "door parity: the script text and the Kotlin text produce equal events" {
        fun fingerprint(p: SprudelPattern?) = p.shouldNotBeNull().cycles().flatMap { events ->
            events.map { Triple(it.whole, it.data.freqHz, it.data.bandf) }
        }

        fingerprint(note("a c e").freq(mul(perlin.seg(4).range(0.95, 1.05)))) shouldBe
                fingerprint(SprudelPattern.compile("""note("a c e").freq(mul(perlin.seg(4).range(0.95, 1.05)))"""))

        fingerprint(note("c e g a").bpf(freq)) shouldBe
                fingerprint(SprudelPattern.compile("""note("c e g a").bpf(freq)"""))

        fingerprint(note("c e g a").bpf(freq.mul(2))) shouldBe
                fingerprint(SprudelPattern.compile("""note("c e g a").bpf(freq.mul(2))"""))

        fingerprint(note("c e").freq(freq.add(50))) shouldBe
                fingerprint(SprudelPattern.compile("""note("c e").freq(freq.add(50))"""))
    }
})
