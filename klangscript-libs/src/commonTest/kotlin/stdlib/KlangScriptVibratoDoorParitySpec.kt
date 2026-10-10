/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.childNodes
import io.peekandpoke.klang.audio_bridge.classic
import io.peekandpoke.klang.audio_bridge.vibrato
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.KlangScriptTypeError
import io.peekandpoke.klang.script.runtime.toObjectOrNull

/**
 * The vibrato's doors (pitch pipeline 7c): the script door `vibrato(rate, semitones, configure)` with `range(from, to)`
 * and `phase` on the [VibratoBuilder], the builder door from Kotlin (`KlangScriptIgnitorExtensions.vibrato(...) { ... }`),
 * and the flat Kotlin door in `audio_bridge`, `vibrato(rate, semitones, rangeFrom, rangeTo, phase)`, in its Double and
 * its [IgnitorDsl] overload (review 7b, A8: a signal depth was script-only). Every form builds the same node, and the
 * expectation is the node written out (it never goes through a door). The flat Kotlin door is the tremolo's recorded
 * two-door asymmetry.
 */
class KlangScriptVibratoDoorParitySpec : StringSpec({

    fun ks(code: String): IgnitorDsl {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        return engine.execute(code).toObjectOrNull<IgnitorDsl>()!!
    }

    fun c(v: Double) = IgnitorDsl.Constant(v)

    val saw = IgnitorDsl.Saw()

    "no configure: every door builds the node at its defaults, range (-1, 1) and phase 0" {
        val expected = IgnitorDsl.Vibrato(inner = saw, rate = c(5.0), semitones = c(0.5), rangeFrom = c(-1.0), rangeTo = c(1.0), phase = c(0.0))

        ks("Ignitor.saw().vibrato(5, 0.5)") shouldBe expected
        ks("Ignitor.saw().vibrato(rate = 5, semitones = 0.5)") shouldBe expected
        ks("Ign.saw().vibrato(5, 0.5, v => v)") shouldBe expected
        KlangScriptIgnitorExtensions.vibrato(saw, 5.0, 0.5) shouldBe expected
        saw.vibrato(5.0, 0.5) shouldBe expected
        saw.vibrato(c(5.0), c(0.5)) shouldBe expected
    }

    "range(from, to): positional, named in either order, the builder from Kotlin and both flat overloads" {
        val expected = IgnitorDsl.Vibrato(inner = saw, rate = c(5.0), semitones = c(0.5), rangeFrom = c(0.0), rangeTo = c(1.0))

        ks("Ignitor.saw().vibrato(5, 0.5, v => v.range(0, 1))") shouldBe expected
        ks("Ignitor.saw().vibrato(5, 0.5, v => v.range(from = 0, to = 1))") shouldBe expected
        ks("Ignitor.saw().vibrato(5, 0.5, v => v.range(to = 1, from = 0))") shouldBe expected
        ks("Ignitor.saw().vibrato(rate = 5, semitones = 0.5, configure = v => v.range(0, 1))") shouldBe expected
        KlangScriptIgnitorExtensions.vibrato(saw, 5.0, 0.5) { it.range(0.0, 1.0) } shouldBe expected
        saw.vibrato(5.0, 0.5, rangeFrom = 0.0, rangeTo = 1.0) shouldBe expected
        saw.vibrato(rate = c(5.0), semitones = c(0.5), rangeFrom = c(0.0), rangeTo = c(1.0)) shouldBe expected

        // not vacuous: the knob wrote fields the default does not have
        expected shouldNotBe ks("Ignitor.saw().vibrato(5, 0.5)")
    }

    "phase(x): the builder knob and the flat door's phase, not wrapped at the door (the engine wraps)" {
        val expected = IgnitorDsl.Vibrato(inner = saw, rate = c(5.0), semitones = c(0.5), phase = c(0.25))

        ks("Ignitor.saw().vibrato(5, 0.5, v => v.phase(0.25))") shouldBe expected
        ks("Ignitor.saw().vibrato(5, 0.5, v => v.phase(phase = 0.25))") shouldBe expected
        KlangScriptIgnitorExtensions.vibrato(saw, 5.0, 0.5) { it.phase(0.25) } shouldBe expected
        saw.vibrato(5.0, 0.5, phase = 0.25) shouldBe expected
        saw.vibrato(rate = c(5.0), semitones = c(0.5), phase = c(0.25)) shouldBe expected

        ks("Ignitor.saw().vibrato(5, 0.5, v => v.phase(1.25))") shouldBe saw.vibrato(5.0, 0.5, phase = 1.25)
        expected shouldNotBe ks("Ignitor.saw().vibrato(5, 0.5)")
    }

    "range and phase on one builder, in either order" {
        val expected = saw.vibrato(6.0, 0.4, rangeFrom = -1.0, rangeTo = 0.0, phase = 0.5)

        ks("Ignitor.saw().vibrato(6, 0.4, v => v.range(-1, 0).phase(0.5))") shouldBe expected
        ks("Ignitor.saw().vibrato(6, 0.4, v => v.phase(0.5).range(-1, 0))") shouldBe expected
    }

    "every knob takes a signal on both doors (the IgnitorDsl overload, review 7b A8)" {
        val rate = ks("Ign.sine(0.2).mul(2).plus(5)")
        val depth = ks("Ign.sine(0.5).mul(0.3).plus(0.3)")
        val from = ks("Ign.sine(1).mul(0.5)")
        val phase = ks("Ign.sine(3).mul(0.1)")
        val expected = IgnitorDsl.Vibrato(inner = saw, rate = rate, semitones = depth, rangeFrom = from, rangeTo = c(1.0), phase = phase)

        ks(
            "Ignitor.saw().vibrato(Ign.sine(0.2).mul(2).plus(5), Ign.sine(0.5).mul(0.3).plus(0.3), " +
                "v => v.range(Ign.sine(1).mul(0.5), 1).phase(Ign.sine(3).mul(0.1)))"
        ) shouldBe expected
        saw.vibrato(rate = rate, semitones = depth, rangeFrom = from, rangeTo = c(1.0), phase = phase) shouldBe expected
        KlangScriptIgnitorExtensions.vibrato(saw, rate, depth) { it.range(from, 1.0).phase(phase) } shouldBe expected
    }

    "the slots on both doors: an own tail from Ignitor.slot.vibrato.* is classic()'s vibrato node" {
        val s = IgnitorDsl.Slots.vibrato
        val classicVibrato = generateSequence(IgnitorDsl.Saw().classic()) { it.childNodes().firstOrNull() }
            .filterIsInstance<IgnitorDsl.Vibrato>()
            .first()
        val script = ks(
            "Ignitor.saw().vibrato(Ignitor.slot.vibrato.rate, Ignitor.slot.vibrato.semitones, " +
                "v => v.range(Ignitor.slot.vibrato.rangeFrom, Ignitor.slot.vibrato.rangeTo).phase(Ignitor.slot.vibrato.phase))"
        ) as IgnitorDsl.Vibrato
        val kotlin = saw.vibrato(rate = s.rate, semitones = s.semitones, rangeFrom = s.rangeFrom, rangeTo = s.rangeTo, phase = s.phase)

        script shouldBe kotlin
        kotlin.copy(inner = classicVibrato.inner) shouldBe classicVibrato
    }

    "a configure lambda that returns nothing is a script error naming the door (checklist 13)" {
        val err = shouldThrow<KlangScriptTypeError> { ks("Ignitor.saw().vibrato(5, 0.5, v => { v.range(0, 1) })") }

        err.message shouldBe "the configure lambda of vibrato returned nothing; return the builder it received (`x => x.analog(3)`)"
    }
})
