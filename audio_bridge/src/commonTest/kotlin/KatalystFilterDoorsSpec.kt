/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * The chain's filter, shape and drive doors (KLANG-00467, cylinder amp step 1), the typed Kotlin door in this module.
 * Every expected value is the stage WRITTEN OUT from its data classes and literal numbers, never through a builder: a
 * door that writes the wrong section kind, field or default goes red here, which a script-against-Kotlin parity row
 * (both doors share these members) cannot see.
 *
 * The cascade's q values come from an oracle written in this spec (the Butterworth pole ladder, `sqrt(2) / (2 cos((2k
 * + 1) pi / 4N))`) and two literals, never from `passesLadderRel`; one row compares the chain door with the voice's
 * optimizer, which expands a voice filter with that function, so a chain cascade that drifts from the voice's goes red
 * there too.
 */
class KatalystFilterDoorsSpec : StringSpec({

    fun c(value: Double): IgnitorDsl.Constant = IgnitorDsl.Constant(value)

    fun eqOf(vararg sections: IgnitorDsl.EqSection): KatalystDsl = KatalystDsl.of(KatalystStageDsl.Eq(sections = sections.toList()))

    /** The Butterworth ladder factor of stage [k] of [n], written here as the oracle. */
    fun ladder(k: Int, n: Int): Double = sqrt(2.0) / (2.0 * cos((2.0 * k + 1.0) * PI / (4.0 * n)))

    /** The sections' q values as numbers (every q of a cascade is a constant). */
    fun qs(dsl: KatalystDsl): List<Double> =
        (dsl.stages.single() as KatalystStageDsl.Eq).sections.map { section ->
            val q = when (section) {
                is IgnitorDsl.EqSection.Lowpass -> section.q
                is IgnitorDsl.EqSection.Highpass -> section.q
                else -> error("not a cascade section: $section")
            }

            q.shouldBeInstanceOf<IgnitorDsl.Constant>().value
        }

    "each filter door writes ONE eq stage holding its own section kind, freq and q (values off their defaults)" {
        Kat { it.lowpass(freq = 1234.kat, q = 1.7.kat) } shouldBe
                eqOf(IgnitorDsl.EqSection.Lowpass(freq = c(1234.0), q = c(1.7)))
        Kat { it.highpass(freq = 87.kat, q = 0.9.kat) } shouldBe
                eqOf(IgnitorDsl.EqSection.Highpass(freq = c(87.0), q = c(0.9)))
        Kat { it.bandpass(freq = 640.kat, q = 2.5.kat) } shouldBe
                eqOf(IgnitorDsl.EqSection.Bandpass(freq = c(640.0), q = c(2.5)))
        Kat { it.notch(freq = 3100.kat, q = 4.kat) } shouldBe
                eqOf(IgnitorDsl.EqSection.Notch(freq = c(3100.0), q = c(4.0)))
    }

    "a door without q writes the voice's default q, 0.707 (FILTER_Q), on all four" {
        Kat { it.lowpass(800.kat) } shouldBe eqOf(IgnitorDsl.EqSection.Lowpass(freq = c(800.0), q = c(0.707)))
        Kat { it.highpass(800.kat) } shouldBe eqOf(IgnitorDsl.EqSection.Highpass(freq = c(800.0), q = c(0.707)))
        Kat { it.bandpass(800.kat) } shouldBe eqOf(IgnitorDsl.EqSection.Bandpass(freq = c(800.0), q = c(0.707)))
        Kat { it.notch(800.kat) } shouldBe eqOf(IgnitorDsl.EqSection.Notch(freq = c(800.0), q = c(0.707)))
    }

    "passes 2: two sections at the cutoff, q on the Butterworth ladder (literals)" {
        val chain = Kat { it.lowpass(freq = 900.kat, q = 2.kat) { f -> f.passes(2.0) } }
        val sections = (chain.stages.single() as KatalystStageDsl.Eq).sections

        sections.size shouldBe 2
        sections.forEach { (it as IgnitorDsl.EqSection.Lowpass).freq shouldBe c(900.0) }
        // 2 * sqrt(2) / (2 cos(pi / 8)) and 2 * sqrt(2) / (2 cos(3 pi / 8))
        qs(chain)[0] shouldBe (1.5307337294603591 plusOrMinus 1e-12)
        qs(chain)[1] shouldBe (3.6955181300451470 plusOrMinus 1e-12)
    }

    "passes 2, 3 and 4 on lowpass and highpass: N sections, each q on the ladder; the middle of 3 is q itself" {
        val q = 1.3

        for (n in 2..4) {
            val low = Kat { it.lowpass(freq = 700.kat, q = q.kat) { f -> f.passes(n.toDouble()) } }
            val high = Kat { it.highpass(freq = 70.kat, q = q.kat) { f -> f.passes(n.toDouble()) } }

            withClue("passes $n") {
                (low.stages.single() as KatalystStageDsl.Eq).sections.forEach {
                    it.shouldBeInstanceOf<IgnitorDsl.EqSection.Lowpass>().freq shouldBe c(700.0)
                }
                (high.stages.single() as KatalystStageDsl.Eq).sections.forEach {
                    it.shouldBeInstanceOf<IgnitorDsl.EqSection.Highpass>().freq shouldBe c(70.0)
                }

                val expected = List(n) { k -> q * ladder(k = k, n = n) }

                qs(low).size shouldBe n
                qs(high).size shouldBe n
                qs(low).zip(expected).forEach { (got, want) -> got shouldBe (want plusOrMinus 1e-12) }
                qs(high).zip(expected).forEach { (got, want) -> got shouldBe (want plusOrMinus 1e-12) }
            }
        }

        // The middle stage of an odd cascade has a factor of exactly 1.0: its q is the author's, bit for bit.
        qs(Kat { it.lowpass(freq = 700.kat, q = q.kat) { f -> f.passes(3.0) } })[1] shouldBe q
        qs(Kat { it.highpass(freq = 70.kat, q = q.kat) { f -> f.passes(3.0) } })[1] shouldBe q
    }

    "the chain's cascade is the voice's: the optimizer fuses the voice filter into the same sections" {
        for (n in 1..4) {
            withClue("passes $n") {
                val voiceLow = Ign.saw().lowpass(freq = 800.ign, q = 2.ign) { it.passes(n.ign) }.optimize()
                val voiceHigh = Ign.saw().highpass(freq = 120.ign, q = 0.9.ign) { it.passes(n.ign) }.optimize()
                val chainLow = Kat { it.lowpass(freq = 800.kat, q = 2.kat) { f -> f.passes(n.toDouble()) } }
                val chainHigh = Kat { it.highpass(freq = 120.kat, q = 0.9.kat) { f -> f.passes(n.toDouble()) } }

                (chainLow.stages.single() as KatalystStageDsl.Eq).sections shouldBe
                        voiceLow.shouldBeInstanceOf<IgnitorDsl.Eq>().sections
                (chainHigh.stages.single() as KatalystStageDsl.Eq).sections shouldBe
                        voiceHigh.shouldBeInstanceOf<IgnitorDsl.Eq>().sections
            }
        }

        // The two section filters, one each, are the voice's too.
        (Kat { it.bandpass(freq = 640.kat, q = 2.5.kat) }.stages.single() as KatalystStageDsl.Eq).sections shouldBe
                Ign.saw().bandpass(freq = 640.ign, q = 2.5.ign).optimize().shouldBeInstanceOf<IgnitorDsl.Eq>().sections
        (Kat { it.notch(freq = 3100.kat, q = 4.kat) }.stages.single() as KatalystStageDsl.Eq).sections shouldBe
                Ign.saw().notch(freq = 3100.ign, q = 4.ign).optimize().shouldBeInstanceOf<IgnitorDsl.Eq>().sections
    }

    "passes is coerced as coercePasses reads it: 0 is 1, 99 is 16, 2.6 rounds to 3, NaN is 1" {
        listOf(0.0 to 1, 99.0 to 16, 2.6 to 3, 2.4 to 2, Double.NaN to 1, Double.POSITIVE_INFINITY to 1, -3.0 to 1)
            .forEach { (passes, sections) ->
                withClue("passes $passes") {
                    qs(Kat { it.lowpass(freq = 500.kat) { f -> f.passes(passes) } }).size shouldBe sections
                    qs(Kat { it.highpass(freq = 500.kat) { f -> f.passes(passes) } }).size shouldBe sections
                }
            }
    }

    "a Kat.param as freq and as q with one pass is the section's Ignitor slot of the same name and default" {
        val f = Kat.param("amp.cut", 1800.0, "the cut")
        val q = Kat.param("amp.q", 0.9)
        val cut = IgnitorDsl.Param("amp.cut", 1800.0, "the cut")
        val slotQ = IgnitorDsl.Param("amp.q", 0.9)

        Kat { it.lowpass(freq = f, q = q) } shouldBe
                eqOf(IgnitorDsl.EqSection.Lowpass(freq = cut, q = slotQ))
        Kat { it.highpass(freq = f, q = q) { x -> x.passes(1.0) } } shouldBe
                eqOf(IgnitorDsl.EqSection.Highpass(freq = cut, q = slotQ))
        Kat { it.bandpass(freq = f, q = q) } shouldBe
                eqOf(IgnitorDsl.EqSection.Bandpass(freq = cut, q = slotQ))
        Kat { it.notch(freq = f, q = q) } shouldBe
                eqOf(IgnitorDsl.EqSection.Notch(freq = cut, q = slotQ))

        // A slot cutoff in a cascade is fine: every section reads the same slot.
        val cascade = Kat { it.lowpass(freq = f, q = 0.707.kat) { x -> x.passes(2.0) } }
        (cascade.stages.single() as KatalystStageDsl.Eq).sections.forEach {
            (it as IgnitorDsl.EqSection.Lowpass).freq shouldBe cut
        }
    }

    "a Kat expression as freq or q is refused, naming the door and the parameter (Q49)" {
        val lfo = Kat.sine(0.5.kat)
        val sum = Kat.param("f", 300.0).times(2.kat)

        shouldThrow<IllegalArgumentException> { Kat { it.lowpass(freq = lfo) } }.message shouldBe
                "Katalyst eq lowpass(freq = ...) takes a number or a Kat.param; got a Kat expression"
        shouldThrow<IllegalArgumentException> { Kat { it.highpass(freq = 100.kat, q = sum) } }.message shouldBe
                "Katalyst eq highpass(q = ...) takes a number or a Kat.param; got a Kat expression"
        shouldThrow<IllegalArgumentException> { Kat { it.bandpass(freq = sum) } }.message shouldBe
                "Katalyst eq bandpass(freq = ...) takes a number or a Kat.param; got a Kat expression"
        shouldThrow<IllegalArgumentException> { Kat { it.notch(freq = 100.kat, q = lfo) } }.message shouldBe
                "Katalyst eq notch(q = ...) takes a number or a Kat.param; got a Kat expression"
    }

    "a Kat.param as q with more than one pass is refused (D3); with one pass it is the slot" {
        val q = Kat.param("amp.q", 0.9)

        shouldThrow<IllegalArgumentException> { Kat { it.lowpass(freq = 800.kat, q = q) { f -> f.passes(2.0) } } }.message shouldBe
                "Katalyst eq lowpass(q = ...) with more than one pass takes a number; got a Kat.param"
        shouldThrow<IllegalArgumentException> { Kat { it.highpass(freq = 80.kat, q = q) { f -> f.passes(4.0) } } }.message shouldBe
                "Katalyst eq highpass(q = ...) with more than one pass takes a number; got a Kat.param"

        // passes that coerce to one pass are one pass: the slot stands.
        Kat { it.lowpass(freq = 800.kat, q = q) { f -> f.passes(0.0) } } shouldBe
                eqOf(IgnitorDsl.EqSection.Lowpass(freq = c(800.0), q = IgnitorDsl.Param("amp.q", 0.9)))
    }

    "shape() is soft, shape(\"tube\") the tube index, an unknown name soft: the stage written out" {
        Kat { it.shape() } shouldBe KatalystDsl.of(KatalystStageDsl.Shape(shape = 0))
        Kat { it.shape("tube") } shouldBe KatalystDsl.of(KatalystStageDsl.Shape(shape = 10))
        Kat { it.shape("asym") } shouldBe KatalystDsl.of(KatalystStageDsl.Shape(shape = 14))
        Kat { it.shape("no-such-shape") } shouldBe KatalystDsl.of(KatalystStageDsl.Shape(shape = 0))
    }

    "two filter doors in a row are two eq stages in written order (merging is the optimizer's, KLANG-00468)" {
        Kat { it.highpass(freq = 100.kat).lowpass(freq = 5000.kat, q = 0.8.kat) } shouldBe KatalystDsl.of(
            KatalystStageDsl.Eq(sections = listOf(IgnitorDsl.EqSection.Highpass(freq = c(100.0), q = c(0.707)))),
            KatalystStageDsl.Eq(sections = listOf(IgnitorDsl.EqSection.Lowpass(freq = c(5000.0), q = c(0.8)))),
        )
    }

    "drive(amount) is a times stage by the voice's drive law: 10^(amount * 1.2), written out" {
        // The oracle is the law written here, not `driveGain`.
        Kat { it.drive(0.3.kat) } shouldBe KatalystDsl.of(KatalystStageDsl.Times(other = KatalystKnobDsl.Constant(10.0.pow(0.3 * 1.2))))
        Kat { it.drive(1.kat) } shouldBe KatalystDsl.of(KatalystStageDsl.Times(other = KatalystKnobDsl.Constant(10.0.pow(1.2))))
        (Kat { it.drive(0.25.kat) }.stages.single() as KatalystStageDsl.Times).other shouldBe
                KatalystKnobDsl.Constant(10.0.pow(0.3))

        // `powerStock`'s drive(0.3) is about mul(2.29), the parent ticket's reading of D6.
        ((Kat { it.drive(0.3.kat) }.stages.single() as KatalystStageDsl.Times).other as KatalystKnobDsl.Constant).value shouldBe
                (2.2908676527677737 plusOrMinus 1e-12)
    }

    "drive at or below 0, or not finite, is unity, as the voice's drive passes its input through" {
        listOf(0.0, -0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach { amount ->
            withClue("amount $amount") {
                Kat { it.drive(amount.kat) } shouldBe KatalystDsl.of(KatalystStageDsl.Times(other = KatalystKnobDsl.Constant(1.0)))
            }
        }
    }

    "drive with a knob that is not a number multiplies by the law as a knob: 10^(max(amount, 0) * 1.2)" {
        val amount = Kat.param("amp.drive", 0.3)

        Kat { it.drive(amount) } shouldBe KatalystDsl.of(
            KatalystStageDsl.Times(
                other = KatalystKnobDsl.Pow(
                    base = KatalystKnobDsl.Constant(10.0),
                    exp = KatalystKnobDsl.Times(
                        left = KatalystKnobDsl.Max(left = amount, right = KatalystKnobDsl.Constant(0.0)),
                        right = KatalystKnobDsl.Constant(1.2),
                    ),
                ),
            ),
        )
    }

    "Kat.fn returns the function itself, typed" {
        val part: (KatalystBuilder) -> KatalystBuilder = { it.lowpass(800.kat) }

        Kat.fn(part) shouldBeSameInstanceAs part
        Kat { it.serial(Kat.fn { k -> k.drive(0.3.kat).shape("tube") }) } shouldBe Kat { it.drive(0.3.kat).shape("tube") }
    }
})
