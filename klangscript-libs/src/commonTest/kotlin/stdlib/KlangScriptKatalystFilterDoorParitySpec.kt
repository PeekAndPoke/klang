/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.Kat
import io.peekandpoke.klang.audio_bridge.KatalystBuilder
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystKnobDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import io.peekandpoke.klang.audio_bridge.kat
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.KlangScriptTypeError
import io.peekandpoke.klang.script.runtime.NativeObjectValue
import kotlin.math.pow

/**
 * The chain's filter, shape and drive doors and `Kat.fn` (KLANG-00467, cylinder amp step 1) from KlangScript and from
 * Kotlin: the script door converts its arguments, pre-checks the refusals script-side and calls the typed door in
 * `audio_bridge` with the checking wrapper for the lambda. Every row compares the script call, the Kotlin call and,
 * where a value is built, the stage WRITTEN OUT from literal numbers (`KatalystFilterDoorsSpec` in `audio_bridge` has
 * the cascade's ladder and the voice parity).
 */
class KlangScriptKatalystFilterDoorParitySpec : StringSpec({

    fun ks(code: String): KatalystDsl {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        val result = engine.execute(code)
        result.shouldBeInstanceOf<NativeObjectValue<*>>()

        return result.value.shouldBeInstanceOf<KatalystDsl>()
    }

    fun c(value: Double): IgnitorDsl.Constant = IgnitorDsl.Constant(value)

    fun eqOf(vararg sections: IgnitorDsl.EqSection): KatalystDsl = KatalystDsl.of(KatalystStageDsl.Eq(sections = sections.toList()))

    "each door from the script and from Kotlin builds the same chain, the stage written out" {
        listOf(
            Triple(
                "k.lowpass(1234, 1.7)",
                { b: KatalystBuilder -> b.lowpass(freq = 1234.kat, q = 1.7.kat) },
                eqOf(IgnitorDsl.EqSection.Lowpass(freq = c(1234.0), q = c(1.7))),
            ),
            Triple(
                "k.highpass(87, 0.9)",
                { b: KatalystBuilder -> b.highpass(freq = 87.kat, q = 0.9.kat) },
                eqOf(IgnitorDsl.EqSection.Highpass(freq = c(87.0), q = c(0.9))),
            ),
            Triple(
                "k.bandpass(640, 2.5)",
                { b: KatalystBuilder -> b.bandpass(freq = 640.kat, q = 2.5.kat) },
                eqOf(IgnitorDsl.EqSection.Bandpass(freq = c(640.0), q = c(2.5))),
            ),
            Triple(
                "k.notch(3100, 4)",
                { b: KatalystBuilder -> b.notch(freq = 3100.kat, q = 4.kat) },
                eqOf(IgnitorDsl.EqSection.Notch(freq = c(3100.0), q = c(4.0))),
            ),
            Triple(
                "k.lowpass(freq = 1234, q = 1.7)",
                { b: KatalystBuilder -> b.lowpass(freq = 1234.kat, q = 1.7.kat) },
                eqOf(IgnitorDsl.EqSection.Lowpass(freq = c(1234.0), q = c(1.7))),
            ),
            Triple(
                "k.shape(\"tube\")",
                { b: KatalystBuilder -> b.shape("tube") },
                KatalystDsl.of(KatalystStageDsl.Shape(shape = 10)),
            ),
            Triple(
                "k.shape(shape = \"asym\")",
                { b: KatalystBuilder -> b.shape(shape = "asym") },
                KatalystDsl.of(KatalystStageDsl.Shape(shape = 14)),
            ),
            Triple(
                "k.drive(1)",
                { b: KatalystBuilder -> b.drive(1.kat) },
                KatalystDsl.of(KatalystStageDsl.Times(other = KatalystKnobDsl.Constant(10.0.pow(1.2)))),
            ),
            Triple(
                "k.highpass(100).drive(0.3).shape(\"tube\").lowpass(4000)",
                { b: KatalystBuilder -> b.highpass(100.kat).drive(0.3.kat).shape("tube").lowpass(4000.kat) },
                KatalystDsl.of(
                    KatalystStageDsl.Eq(sections = listOf(IgnitorDsl.EqSection.Highpass(freq = c(100.0), q = c(0.707)))),
                    KatalystStageDsl.Times(other = KatalystKnobDsl.Constant(10.0.pow(0.3 * 1.2))),
                    KatalystStageDsl.Shape(shape = 10),
                    KatalystStageDsl.Eq(sections = listOf(IgnitorDsl.EqSection.Lowpass(freq = c(4000.0), q = c(0.707)))),
                ),
            ),
        ).forEach { (call, kotlin, expected) ->
            withClue(call) {
                val script = ks("Katalyst(k => $call)")
                val typed = Kat { kotlin(it) }

                script shouldBe typed
                script shouldBe expected
            }
        }
    }

    "the bare forms: the script's literal defaults are the Kotlin default symbols (q 0.707, shape soft)" {
        ks("Katalyst(k => k.lowpass(800))") shouldBe Kat { it.lowpass(800.kat) }
        ks("Katalyst(k => k.highpass(800))") shouldBe Kat { it.highpass(800.kat) }
        ks("Katalyst(k => k.bandpass(800))") shouldBe Kat { it.bandpass(800.kat) }
        ks("Katalyst(k => k.notch(800))") shouldBe Kat { it.notch(800.kat) }
        ks("Katalyst(k => k.shape())") shouldBe Kat { it.shape() }

        // ...and both are the stage written out with the literal numbers.
        ks("Katalyst(k => k.lowpass(800))") shouldBe eqOf(IgnitorDsl.EqSection.Lowpass(freq = c(800.0), q = c(0.707)))
        ks("Katalyst(k => k.highpass(800))") shouldBe eqOf(IgnitorDsl.EqSection.Highpass(freq = c(800.0), q = c(0.707)))
        ks("Katalyst(k => k.bandpass(800))") shouldBe eqOf(IgnitorDsl.EqSection.Bandpass(freq = c(800.0), q = c(0.707)))
        ks("Katalyst(k => k.notch(800))") shouldBe eqOf(IgnitorDsl.EqSection.Notch(freq = c(800.0), q = c(0.707)))
        ks("Katalyst(k => k.shape())") shouldBe KatalystDsl.of(KatalystStageDsl.Shape(shape = 0))
    }

    "x => x.passes(3) reaches the cascade from both doors, positional and named" {
        val typedLow = Kat { it.lowpass(freq = 900.kat, q = 1.2.kat) { f -> f.passes(3.0) } }
        val typedHigh = Kat { it.highpass(freq = 90.kat, q = 1.2.kat) { f -> f.passes(3.0) } }

        ks("Katalyst(k => k.lowpass(900, 1.2, x => x.passes(3)))") shouldBe typedLow
        ks("Katalyst(k => k.lowpass(freq = 900, q = 1.2, configure = x => x.passes(3)))") shouldBe typedLow
        ks("Katalyst(k => k.highpass(90, 1.2, x => x.passes(3)))") shouldBe typedHigh

        (typedLow.stages.single() as KatalystStageDsl.Eq).sections.size shouldBe 3
        (typedHigh.stages.single() as KatalystStageDsl.Eq).sections.size shouldBe 3

        // The script variant called from Kotlin with a Kotlin lambda builds the same.
        Kat { it.scriptLowpass(freq = 900, q = 1.2) { f -> f.scriptPasses(3.0) } } shouldBe typedLow
    }

    "a Kat.param as freq and q with one pass is the section's slot, from both doors" {
        val expected = eqOf(IgnitorDsl.EqSection.Lowpass(freq = IgnitorDsl.Param("cut", 1800.0), q = IgnitorDsl.Param("res", 0.9)))

        ks("""Katalyst(k => k.lowpass(Kat.param("cut", 1800), Kat.param("res", 0.9)))""") shouldBe expected
        Kat { it.lowpass(freq = Kat.param("cut", 1800.0), q = Kat.param("res", 0.9)) } shouldBe expected
    }

    "drive with a Kat.param is the law as a knob, from both doors" {
        ks("""Katalyst(k => k.drive(Kat.param("d", 0.3)))""") shouldBe Kat { it.drive(Kat.param("d", 0.3)) }
        (ks("""Katalyst(k => k.drive(Kat.param("d", 0.3)))""").stages.single() as KatalystStageDsl.Times).other
            .shouldBeInstanceOf<KatalystKnobDsl.Pow>()
    }

    "an expression as freq or q is a script error naming the door and the parameter" {
        listOf(
            "Katalyst(k => k.lowpass(Kat.sine(0.5)))" to ("lowpass(freq = ...)" to "Katalyst lowpass"),
            "Katalyst(k => k.highpass(200, Kat.param(\"q\", 1).times(2)))" to ("highpass(q = ...)" to "Katalyst highpass"),
            "Katalyst(k => k.bandpass(Kat.param(\"f\", 300).plus(10)))" to ("bandpass(freq = ...)" to "Katalyst bandpass"),
            "Katalyst(k => k.notch(1200, Kat.saw(1)))" to ("notch(q = ...)" to "Katalyst notch"),
        ).forEach { (script, expected) ->
            val (what, operation) = expected

            withClue(script) {
                val err = shouldThrow<KlangScriptTypeError> { ks(script) }

                err.message shouldBe "a Katalyst $what takes a number or a Kat.param; got a Kat expression"
                err.operation shouldBe operation
            }
        }
    }

    "a Kat.param as q with more than one pass is a script error naming the door (D3); one pass is fine" {
        listOf("lowpass", "highpass").forEach { door ->
            withClue(door) {
                val err = shouldThrow<KlangScriptTypeError> {
                    ks("""Katalyst(k => k.$door(800, Kat.param("res", 0.9), x => x.passes(2)))""")
                }

                err.message shouldBe "a Katalyst $door with more than one pass takes a number for q; got a Kat.param"
                err.operation shouldBe "Katalyst $door"

                // passes(1) keeps the slot.
                ks("""Katalyst(k => k.$door(800, Kat.param("res", 0.9), x => x.passes(1)))""").stages.size shouldBe 1
            }
        }
    }

    "a slot as passes is a script error: passes is a plain number fixed with the chain" {
        shouldThrow<KlangScriptTypeError> {
            ks("""Katalyst(k => k.lowpass(800, 0.707, x => x.passes(Kat.param("p", 2))))""")
        }.message shouldBe "passes on a Katalyst filter takes a number, fixed with the chain; got a Katalyst knob"

        shouldThrow<KlangScriptTypeError> {
            ks("""Katalyst(k => k.highpass(800, 0.707, x => x.passes("two")))""")
        }.message shouldBe "passes on a Katalyst filter takes a number, fixed with the chain; got a string"
    }

    "a configure lambda that returns nothing, or the wrong type, is a script error naming the door" {
        listOf("lowpass", "highpass").forEach { door ->
            withClue(door) {
                shouldThrow<KlangScriptTypeError> { ks("Katalyst(k => k.$door(800, 0.707, x => { x.passes(2) }))") }.message shouldBe
                        "the configure lambda of Katalyst $door returned nothing; return the builder it received (`x => x.passes(2)`)"

                shouldThrow<KlangScriptTypeError> { ks("Katalyst(k => k.$door(800, 0.707, x => 5))") }.message shouldContain
                        "the configure lambda of Katalyst $door must return the KatalystFilterBuilder it received"
            }
        }
    }

    "Kat.fn hands the function back as it is: a chain part that plays as the bare lambda, in serial" {
        val expected = ks("Katalyst(k => k.lowpass(800).gain(2))")

        ks("let amp = Kat.fn(k => k.lowpass(800))\nKatalyst(k => k.serial(amp).gain(2))") shouldBe expected
        ks("let amp = Katalyst.fn(k => k.lowpass(800))\nKatalyst(k => k.serial(amp).gain(2))") shouldBe expected
        ks("let amp = k => k.lowpass(800)\nKatalyst(k => k.serial(amp).gain(2))") shouldBe expected

        val part: (KatalystBuilder) -> KatalystBuilder = { it.lowpass(800.kat) }

        Kat.fn(part) shouldBeSameInstanceAs part
        KlangScriptKatalyst.fn(part) shouldBeSameInstanceAs part
    }
})
