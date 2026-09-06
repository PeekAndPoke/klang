/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelVoiceData

/**
 * One row per field accessor, each run through BOTH doors: the Kotlin pattern and the same text
 * compiled as KlangScript must agree with each other and with the expected field value, over 12
 * cycles. Two rows per accessor: a mapper applied to its own field (`gain(mul(0.5))`) and the
 * bare accessor read into another field (`pan(gain)`).
 *
 * `freq` and the mechanism itself are covered by [LangFreqAccessorSpec].
 */
class LangFieldAccessorsSpec : StringSpec({

    class Row(
        val accessor: String,
        val kotlin: SprudelPattern,
        val script: String,
        val field: (SprudelVoiceData) -> Double?,
        val expected: Double,
    )

    fun row(accessor: String, script: String, field: (SprudelVoiceData) -> Double?, expected: Double, kotlin: SprudelPattern) =
        Row(accessor, kotlin, script, field, expected)

    val mapped = listOf(
        row("gain", """note("c e").gain(0.8).gain(mul(0.5))""", { it.gain }, 0.4, note("c e").gain(0.8).gain(mul(0.5))),
        row("velocity", """note("c e").velocity(0.8).velocity(mul(0.5))""", { it.velocity }, 0.4, note("c e").velocity(0.8).velocity(mul(0.5))),
        row("pan", """note("c e").pan(0.3).pan(add(0.2))""", { it.pan }, 0.5, note("c e").pan(0.3).pan(add(0.2))),
        row("postgain", """note("c e").postgain(0.5).postgain(mul(2))""", { it.postGain }, 1.0, note("c e").postgain(0.5).postgain(mul(2))),
        row("lpf", """note("c e").lpf(800).lpf(mul(2))""", { it.cutoff }, 1600.0, note("c e").lpf(800).lpf(mul(2))),
        row("hpf", """note("c e").hpf(200).hpf(add(50))""", { it.hcutoff }, 250.0, note("c e").hpf(200).hpf(add(50))),
        row("bpf", """note("c e").bpf(500).bpf(mul(2))""", { it.bandf }, 1000.0, note("c e").bpf(500).bpf(mul(2))),
        row("lpq", """note("c e").lpq(4).lpq(mul(2))""", { it.resonance }, 8.0, note("c e").lpq(4).lpq(mul(2))),
        row("hpq", """note("c e").hpq(3).hpq(add(1))""", { it.hresonance }, 4.0, note("c e").hpq(3).hpq(add(1))),
        row("bpq", """note("c e").bpq(5).bpq(mul(2))""", { it.bandq }, 10.0, note("c e").bpq(5).bpq(mul(2))),
        row("attack", """note("c e").attack(0.1).attack(mul(2))""", { it.attack }, 0.2, note("c e").attack(0.1).attack(mul(2))),
        row("decay", """note("c e").decay(0.2).decay(add(0.1))""", { it.decay }, 0.3, note("c e").decay(0.2).decay(add(0.1))),
        row("sustain", """note("c e").sustain(0.5).sustain(mul(0.5))""", { it.sustain }, 0.25, note("c e").sustain(0.5).sustain(mul(0.5))),
        row("release", """note("c e").release(0.4).release(mul(0.5))""", { it.release }, 0.2, note("c e").release(0.4).release(mul(0.5))),
    )

    val read = listOf(
        row("gain", """note("c e").gain(0.8).pan(gain)""", { it.pan }, 0.8, note("c e").gain(0.8).pan(gain)),
        row("velocity", """note("c e").velocity(0.7).pan(velocity)""", { it.pan }, 0.7, note("c e").velocity(0.7).pan(velocity)),
        row("pan", """note("c e").pan(0.3).gain(pan)""", { it.gain }, 0.3, note("c e").pan(0.3).gain(pan)),
        row("postgain", """note("c e").postgain(0.6).pan(postgain)""", { it.pan }, 0.6, note("c e").postgain(0.6).pan(postgain)),
        row("lpf", """note("c e").lpf(800).hpf(lpf)""", { it.hcutoff }, 800.0, note("c e").lpf(800).hpf(lpf)),
        row("hpf", """note("c e").hpf(200).lpf(hpf)""", { it.cutoff }, 200.0, note("c e").hpf(200).lpf(hpf)),
        row("bpf", """note("c e").bpf(500).lpf(bpf)""", { it.cutoff }, 500.0, note("c e").bpf(500).lpf(bpf)),
        row("lpq", """note("c e").lpq(4).hpq(lpq)""", { it.hresonance }, 4.0, note("c e").lpq(4).hpq(lpq)),
        row("hpq", """note("c e").hpq(3).bpq(hpq)""", { it.bandq }, 3.0, note("c e").hpq(3).bpq(hpq)),
        row("bpq", """note("c e").bpq(5).lpq(bpq)""", { it.resonance }, 5.0, note("c e").bpq(5).lpq(bpq)),
        row("attack", """note("c e").attack(0.1).decay(attack)""", { it.decay }, 0.1, note("c e").attack(0.1).decay(attack)),
        row("decay", """note("c e").decay(0.2).release(decay)""", { it.release }, 0.2, note("c e").decay(0.2).release(decay)),
        row("sustain", """note("c e").sustain(0.5).pan(sustain)""", { it.pan }, 0.5, note("c e").sustain(0.5).pan(sustain)),
        row("release", """note("c e").release(0.4).attack(release)""", { it.attack }, 0.4, note("c e").release(0.4).attack(release)),
    )

    fun SprudelPattern.cycles() = (0 until 12).map { c -> queryArc(c.toDouble(), c + 1.0) }

    fun check(rows: List<Row>) {
        rows.forEach { r ->
            withClue("${r.accessor} | ${r.script}") {
                val kotlin = r.kotlin.cycles()
                val script = SprudelPattern.compile(r.script).shouldNotBeNull().cycles()

                listOf("kotlin" to kotlin, "script" to script).forEach { (door, cycles) ->
                    withClue(door) {
                        cycles.forEach { events ->
                            events shouldHaveSize 2
                            events.forEach { r.field(it.data).shouldNotBeNull() shouldBe (r.expected plusOrMinus 1e-9) }
                        }
                    }
                }

                // Door parity: same events, same values, in the same places.
                kotlin.map { events -> events.map { it.whole to r.field(it.data) } } shouldBe
                        script.map { events -> events.map { it.whole to r.field(it.data) } }
            }
        }
    }

    "a mapper argument applies to the setter's own field, in both doors" {
        check(mapped)
    }

    "the bare accessor reads its field into another setter, in both doors" {
        check(read)
    }

    "multi-parameter setters dispatch through the accessor's invoke in both doors" {
        fun both(kotlin: SprudelPattern, script: String, check: (SprudelVoiceData) -> Unit) {
            withClue(script) {
                listOf("kotlin" to kotlin, "script" to SprudelPattern.compile(script).shouldNotBeNull()).forEach { (door, p) ->
                    withClue(door) { p.cycles().forEach { events -> events shouldHaveSize 2; events.forEach { check(it.data) } } }
                }
            }
        }

        both(note("c e").apply(lpf(500, 8, 2)), """note("c e").apply(lpf(500, 8, 2))""") {
            it.cutoff shouldBe 500.0
            it.resonance shouldBe 8.0
            it.lpPasses shouldBe 2.0
        }
        both(note("c e").apply(hpf(300, 4)), """note("c e").apply(hpf(300, 4))""") {
            it.hcutoff shouldBe 300.0
            it.hresonance shouldBe 4.0
        }
        both(note("c e").apply(bpf(1000, 3)), """note("c e").apply(bpf(1000, 3))""") {
            it.bandf shouldBe 1000.0
            it.bandq shouldBe 3.0
        }
        // a mapper in the first slot next to a literal tail: only the first parameter takes a mapper
        both(note("c e").lpf(400).lpf(mul(2), 8), """note("c e").lpf(400).lpf(mul(2), 8)""") {
            it.cutoff shouldBe 800.0
            it.resonance shouldBe 8.0
        }
        both(note("c e").lpf(400).hpf(lpf.div(2), 4), """note("c e").lpf(400).hpf(lpf.div(2), 4)""") {
            it.hcutoff shouldBe 200.0
            it.hresonance shouldBe 4.0
        }
        both(note("c e").bpf(freq.mul(2), 3), """note("c e").bpf(freq.mul(2), 3)""") {
            it.bandf shouldBe (it.freqHz.shouldNotBeNull() * 2 plusOrMinus 1e-9)
            it.bandq shouldBe 3.0
        }
        // a named argument that skips the first parameter
        both(note("c e").lpf(700).apply(lpf(q = 6)), """note("c e").lpf(700).apply(lpf(q = 6))""") {
            it.cutoff shouldBe 700.0
            it.resonance shouldBe 6.0
        }
    }

    "an unset source field leaves the target unchanged | note(\"c\").gain(0.8).gain(velocity)" {
        note("c").gain(0.8).gain(velocity).cycles().forEach { events ->
            events shouldHaveSize 1
            events.single().data.gain shouldBe 0.8
        }
        SprudelPattern.compile("""note("c").gain(0.8).gain(velocity)""").shouldNotBeNull().cycles().forEach { events ->
            events.single().data.gain shouldBe 0.8
        }
    }
})
