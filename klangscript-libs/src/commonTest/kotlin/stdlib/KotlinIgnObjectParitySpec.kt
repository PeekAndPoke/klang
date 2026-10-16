/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.peekandpoke.klang.audio_bridge.Ign
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.ign
import io.peekandpoke.klang.audio_bridge.unaryMinus
import io.peekandpoke.klang.audio_bridge.vibrato
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.toObjectOrNull

/**
 * The typed Kotlin `Ign` object (`audio_bridge`, step 1b of `docs/tasks/kotlin-typed-doors.md`, Q46) against the
 * KlangScript `Ign`: for every function, the script call and the Kotlin call build equal nodes, with the defaults, with
 * numbers, with a signal, and with every builder knob (the script's `configure` lambda, the Kotlin door's named
 * parameters, or, for `saw` and `vibrato` since S1, the same builder in a Kotlin `configure` lambda). The values in a
 * row are distinct, so a swapped parameter or a missed knob shows.
 *
 * Noise sources carry a fresh `uid` per call; a row compares them with the `uid` zeroed.
 */
class KotlinIgnObjectParitySpec : StringSpec({

    fun ks(code: String): IgnitorDsl {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        return engine.execute(code).toObjectOrNull<IgnitorDsl>()!!
    }

    /** Zeroes the per-call uid of a noise source, so two calls compare by their knobs. */
    fun IgnitorDsl.withoutUid(): IgnitorDsl = when (this) {
        is IgnitorDsl.WhiteNoise -> copy(uid = 0)
        is IgnitorDsl.BrownNoise -> copy(uid = 0)
        is IgnitorDsl.PinkNoise -> copy(uid = 0)
        else -> this
    }

    val lfo = "Ign.perlin(0.5)"
    val lfoK = Ign.perlin(0.5.ign)

    val rows: List<Pair<String, () -> IgnitorDsl>> = listOf(
        // ── basics
        "Ign.freq()" to { Ign.freq() },
        "Ign.silence()" to { Ign.silence() },
        "Ign.constant(0.3)" to { Ign.constant(0.3) },
        "Ign.param(\"cut\", 800)" to { Ign.param("cut", 800.0) },
        "Ign.param(\"cut\", 800, \"the cutoff\")" to { Ign.param("cut", 800.0, "the cutoff") },
        "Ign.variants()" to { Ign.variants() },
        "Ign.variants(400, 1200)" to { Ign.variants(400.ign, 1200.ign) },
        "Ign.variants(Ign.sine(), Ign.saw())" to { Ign.variants(Ign.sine(), Ign.saw()) },

        // ── sine
        "Ign.sine()" to { Ign.sine() },
        "Ign.sine(5)" to { Ign.sine(5.ign) },
        "Ign.sine($lfo)" to { Ign.sine(lfoK) },
        "Ign.sine(220, x => x.analog(3).fundamental(0.7).analogSpread(0.4).phase(0.25))" to {
            Ign.sine(freq = 220.ign, analog = 3.ign, fundamental = 0.7.ign, analogSpread = 0.4.ign, phase = 0.25.ign)
        },
        "Ign.sine(x => x.analog($lfo))" to { Ign.sine(analog = lfoK) },

        // ── saw, ramp
        "Ign.saw()" to { Ign.saw() },
        "Ign.saw(110)" to { Ign.saw(110.ign) },
        "Ign.saw($lfo)" to { Ign.saw(lfoK) },
        "Ign.saw(110, x => x.analog(5).resetSamples(4).shapeMax(0.3).phase(0.5))" to {
            Ign.saw(110.ign) { it.analog(5.ign).resetSamples(4.0).shapeMax(0.3).phase(0.5.ign) }
        },
        "Ign.saw(configure = x => x.phase(0.5).shapeMax(0.3).resetSamples(4).analog(5))" to {
            Ign.saw(configure = { it.phase(0.5.ign).shapeMax(0.3).resetSamples(4.0).analog(5.ign) })
        },
        "Ign.saw(x => x.analog($lfo).phase($lfo))" to { Ign.saw { it.analog(lfoK).phase(lfoK) } },

        // ── vibrato, the builder door on both sides (typed doors S1)
        "Ign.saw().vibrato(5, 0.3)" to { Ign.saw().vibrato(5.ign, 0.3.ign) },
        "Ign.saw().vibrato(5, 0.3, v => v.range(0, 1))" to { Ign.saw().vibrato(5.ign, 0.3.ign) { it.range(0.ign, 1.ign) } },
        "Ign.saw().vibrato(5, 0.3, v => v.range(-0.5, 2).phase(0.75))" to {
            Ign.saw().vibrato(5.ign, 0.3.ign) { it.range(-0.5.ign, 2.ign).phase(0.75.ign) }
        },
        "Ign.saw().vibrato($lfo, Ign.sine(0.3), v => v.range($lfo, 1).phase($lfo))" to {
            Ign.saw().vibrato(rate = lfoK, semitones = Ign.sine(0.3.ign)) { it.range(from = lfoK, to = 1.ign).phase(lfoK) }
        },
        "Ign.ramp()" to { Ign.ramp() },
        "Ign.ramp(110, x => x.analog(5).resetSamples(4).shapeMax(0.3).phase(0.5))" to {
            Ign.ramp(freq = 110.ign, analog = 5.ign, resetSamples = 4.0, shapeMax = 0.3, phase = 0.5.ign)
        },
        "Ign.ramp(x => x.analog($lfo))" to { Ign.ramp(analog = lfoK) },

        // ── square, pulze
        "Ign.square()" to { Ign.square() },
        "Ign.square(55)" to { Ign.square(55.ign) },
        "Ign.square(55, x => x.duty(0.3).analog(2).flankSamples(4).riseFlank(0.2).fallFlank(0.6).phase(0.1))" to {
            Ign.square(
                freq = 55.ign,
                duty = 0.3.ign,
                analog = 2.ign,
                flankSamples = 4.0,
                riseFlank = 0.2,
                fallFlank = 0.6,
                phase = 0.1.ign,
            )
        },
        "Ign.square(x => x.duty($lfo))" to { Ign.square(duty = lfoK) },
        "Ign.pulze()" to { Ign.pulze() },
        "Ign.pulze(55, x => x.duty(0.3).analog(2).phase(0.1))" to {
            Ign.pulze(freq = 55.ign, duty = 0.3.ign, analog = 2.ign, phase = 0.1.ign)
        },
        "Ign.pulze($lfo, x => x.duty($lfo))" to { Ign.pulze(freq = lfoK, duty = lfoK) },

        // ── tri, zawtooth, zamp, impulse
        "Ign.tri()" to { Ign.tri() },
        "Ign.tri(330, x => x.analog(3).phase(0.75))" to { Ign.tri(freq = 330.ign, analog = 3.ign, phase = 0.75.ign) },
        "Ign.tri($lfo)" to { Ign.tri(lfoK) },
        "Ign.zawtooth()" to { Ign.zawtooth() },
        "Ign.zawtooth(330, x => x.analog(3).phase(0.75))" to { Ign.zawtooth(freq = 330.ign, analog = 3.ign, phase = 0.75.ign) },
        "Ign.zamp()" to { Ign.zamp() },
        "Ign.zamp(330, x => x.analog(3).phase(0.75))" to { Ign.zamp(freq = 330.ign, analog = 3.ign, phase = 0.75.ign) },
        "Ign.impulse()" to { Ign.impulse() },
        "Ign.impulse(330, x => x.analog(3).phase(0.75))" to { Ign.impulse(freq = 330.ign, analog = 3.ign, phase = 0.75.ign) },

        // ── noise
        "Ign.whitenoise()" to { Ign.whitenoise() },
        "Ign.whitenoise(-0.5)" to { Ign.whitenoise((-0.5).ign) },
        "Ign.whitenoise($lfo)" to { Ign.whitenoise(lfoK) },
        "Ign.brownnoise()" to { Ign.brownnoise() },
        "Ign.brownnoise(0.3)" to { Ign.brownnoise(0.3.ign) },
        "Ign.pinknoise()" to { Ign.pinknoise() },
        "Ign.perlin()" to { Ign.perlin() },
        "Ign.perlin(3, 4, 0.7)" to { Ign.perlin(3.ign, 4.ign, 0.7.ign) },
        "Ign.perlin(persistence = $lfo)" to { Ign.perlin(persistence = lfoK) },
        "Ign.berlin()" to { Ign.berlin() },
        "Ign.berlin(3, 4, 0.7)" to { Ign.berlin(3.ign, 4.ign, 0.7.ign) },
        "Ign.dust()" to { Ign.dust() },
        "Ign.dust(0.4, 2, 1)" to { Ign.dust(0.4.ign, 2.ign, 1.ign) },
        "Ign.dust(tail = $lfo)" to { Ign.dust(tail = lfoK) },
        "Ign.crackle()" to { Ign.crackle() },
        "Ign.crackle(1.9)" to { Ign.crackle(1.9.ign) },

        // ── super oscillators
        "Ign.supersaw()" to { Ign.supersaw() },
        "Ign.supersaw(110)" to { Ign.supersaw(110.ign) },
        "Ign.supersaw(110, x => x.voices(9).spread(0.1).analog(5).analogSpread(0.3).spreadPower(1.5).sideAtten(0.2).gainJitter(0.05).centerJitter(0.6).phase(0.4))" to {
            Ign.supersaw(
                freq = 110.ign,
                voices = 9.ign,
                spread = 0.1.ign,
                analog = 5.ign,
                analogSpread = 0.3.ign,
                spreadPower = 1.5,
                sideAtten = 0.2,
                gainJitter = 0.05,
                centerJitter = 0.6,
                phase = 0.4.ign,
            )
        },
        "Ign.supersaw(x => x.spread($lfo))" to { Ign.supersaw(spread = lfoK) },
        "Ign.supersine()" to { Ign.supersine() },
        "Ign.supersine(110, x => x.voices(9).spread(0.1).analog(5).analogSpread(0.3).spreadPower(1.5).sideAtten(0.2).gainJitter(0.05).centerJitter(0.6).phase(0.4))" to {
            Ign.supersine(
                freq = 110.ign,
                voices = 9.ign,
                spread = 0.1.ign,
                analog = 5.ign,
                analogSpread = 0.3.ign,
                spreadPower = 1.5,
                sideAtten = 0.2,
                gainJitter = 0.05,
                centerJitter = 0.6,
                phase = 0.4.ign,
            )
        },
        "Ign.supersquare()" to { Ign.supersquare() },
        "Ign.supersquare(110, x => x.voices(9).spread(0.1).analog(5).analogSpread(0.3).spreadPower(1.5).sideAtten(0.2).gainJitter(0.05).centerJitter(0.6).phase(0.4))" to {
            Ign.supersquare(
                freq = 110.ign,
                voices = 9.ign,
                spread = 0.1.ign,
                analog = 5.ign,
                analogSpread = 0.3.ign,
                spreadPower = 1.5,
                sideAtten = 0.2,
                gainJitter = 0.05,
                centerJitter = 0.6,
                phase = 0.4.ign,
            )
        },
        "Ign.supertri()" to { Ign.supertri() },
        "Ign.supertri(110, x => x.voices(9).spread(0.1).analog(5).analogSpread(0.3).spreadPower(1.5).sideAtten(0.2).gainJitter(0.05).centerJitter(0.6).phase(0.4))" to {
            Ign.supertri(
                freq = 110.ign,
                voices = 9.ign,
                spread = 0.1.ign,
                analog = 5.ign,
                analogSpread = 0.3.ign,
                spreadPower = 1.5,
                sideAtten = 0.2,
                gainJitter = 0.05,
                centerJitter = 0.6,
                phase = 0.4.ign,
            )
        },
        "Ign.superramp()" to { Ign.superramp() },
        "Ign.superramp(110, x => x.voices(9).spread(0.1).analog(5).analogSpread(0.3).spreadPower(1.5).sideAtten(0.2).gainJitter(0.05).centerJitter(0.6).phase(0.4))" to {
            Ign.superramp(
                freq = 110.ign,
                voices = 9.ign,
                spread = 0.1.ign,
                analog = 5.ign,
                analogSpread = 0.3.ign,
                spreadPower = 1.5,
                sideAtten = 0.2,
                gainJitter = 0.05,
                centerJitter = 0.6,
                phase = 0.4.ign,
            )
        },

        // ── physical models
        "Ign.pluck()" to { Ign.pluck() },
        "Ign.pluck(220)" to { Ign.pluck(220.ign) },
        "Ign.pluck(220, x => x.feedback(0.99).brightness(0.45).pickPosition(0.3).stiffness(0.1).analog(2))" to {
            Ign.pluck(
                freq = 220.ign,
                feedback = 0.99.ign,
                brightness = 0.45.ign,
                pickPosition = 0.3.ign,
                stiffness = 0.1.ign,
                analog = 2.ign,
            )
        },
        "Ign.pluck(x => x.brightness($lfo))" to { Ign.pluck(brightness = lfoK) },
        "Ign.superpluck()" to { Ign.superpluck() },
        "Ign.superpluck(220, x => x.voices(6).spread(0.15).feedback(0.99).brightness(0.45).pickPosition(0.3).stiffness(0.1).analog(2).analogSpread(0.7))" to {
            Ign.superpluck(
                freq = 220.ign,
                voices = 6.ign,
                spread = 0.15.ign,
                feedback = 0.99.ign,
                brightness = 0.45.ign,
                pickPosition = 0.3.ign,
                stiffness = 0.1.ign,
                analog = 2.ign,
                analogSpread = 0.7.ign,
            )
        },
    )

    rows.forEach { (code, kotlin) ->
        "$code equals the Kotlin Ign call" {
            withClue(code) {
                ks(code).withoutUid() shouldBe kotlin().withoutUid()
            }
        }
    }

    // The script saw and vibrato doors build on the Kotlin ones (typed doors S1), so a parity row cannot see a wrong
    // Kotlin default either: pin the nodes, written out.
    "Ign.saw() and .vibrato(rate, semitones) build the nodes at their defaults" {
        Ign.saw() shouldBe IgnitorDsl.Saw(freq = IgnitorDsl.Freq)
        Ign.saw().vibrato(5.ign, 0.3.ign) shouldBe IgnitorDsl.Vibrato(
            inner = IgnitorDsl.Saw(),
            rate = IgnitorDsl.Constant(5.0),
            semitones = IgnitorDsl.Constant(0.3),
            rangeFrom = IgnitorDsl.Constant(-1.0),
            rangeTo = IgnitorDsl.Constant(1.0),
            phase = IgnitorDsl.Constant(0.0),
        )
    }

    // Both doors share the builder members, so a parity row cannot see a member that writes the wrong field: one row per
    // member knob against the node written out with `.copy()`, never through a builder, each value distinct and off
    // its default (typed doors S1).
    "every typed saw and vibrato member writes its own field" {
        val saw = IgnitorDsl.Saw()
        val vib = IgnitorDsl.Vibrato(inner = saw, rate = 5.ign, semitones = 0.3.ign)

        Ign.saw { it.analog(7.ign) } shouldBe saw.copy(analog = 7.ign)
        Ign.saw { it.resetSamples(4.0) } shouldBe saw.copy(resetSamples = 4.0)
        Ign.saw { it.shapeMax(0.3) } shouldBe saw.copy(shapeMax = 0.3)
        Ign.saw { it.phase(0.25.ign) } shouldBe saw.copy(phase = 0.25.ign)
        Ign.saw().vibrato(5.ign, 0.3.ign) { it.range(0.2.ign, 0.9.ign) } shouldBe vib.copy(rangeFrom = 0.2.ign, rangeTo = 0.9.ign)
        Ign.saw().vibrato(5.ign, 0.3.ign) { it.phase(0.75.ign) } shouldBe vib.copy(phase = 0.75.ign)

        // the same members through the script variants
        ks("Ign.saw(x => x.analog(7))") shouldBe saw.copy(analog = 7.ign)
        ks("Ign.saw(x => x.resetSamples(4))") shouldBe saw.copy(resetSamples = 4.0)
        ks("Ign.saw(x => x.shapeMax(0.3))") shouldBe saw.copy(shapeMax = 0.3)
        ks("Ign.saw(x => x.phase(0.25))") shouldBe saw.copy(phase = 0.25.ign)
        ks("Ign.saw().vibrato(5, 0.3, v => v.range(0.2, 0.9))") shouldBe vib.copy(rangeFrom = 0.2.ign, rangeTo = 0.9.ign)
        ks("Ign.saw().vibrato(5, 0.3, v => v.phase(0.75))") shouldBe vib.copy(phase = 0.75.ign)
    }

    // The script pluck doors build on the Kotlin ones, so a parity row cannot see a wrong sealed default: pin the node.
    "Ign.pluck() and Ign.superpluck() seal their string knobs to constants" {
        Ign.pluck() shouldBe IgnitorDsl.Pluck(
            feedback = IgnitorDsl.Constant(0.996),
            brightness = IgnitorDsl.Constant(0.5),
            pickPosition = IgnitorDsl.Constant(0.5),
            stiffness = IgnitorDsl.Constant(0.0),
        )
        Ign.superpluck() shouldBe IgnitorDsl.SuperPluck(
            voices = IgnitorDsl.Constant(8.0),
            spread = IgnitorDsl.Constant(0.2),
            feedback = IgnitorDsl.Constant(0.996),
            brightness = IgnitorDsl.Constant(0.5),
            pickPosition = IgnitorDsl.Constant(0.5),
            stiffness = IgnitorDsl.Constant(0.0),
        )
    }

    "Ign.slot is the script's Ign.slot, leaf for leaf" {
        Ign.slot shouldBeSameInstanceAs IgnitorDsl.Slots
        ks("Ign.slot.analog") shouldBeSameInstanceAs Ign.slot.analog
        ks("Ign.slot.lpf.freq") shouldBeSameInstanceAs Ign.slot.lpf.freq
    }
})
