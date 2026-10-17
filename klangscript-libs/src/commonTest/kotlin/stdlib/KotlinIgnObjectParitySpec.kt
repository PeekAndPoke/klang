/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.peekandpoke.klang.audio_bridge.Ign
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.ign
import io.peekandpoke.klang.audio_bridge.unaryMinus
import io.peekandpoke.klang.audio_bridge.vibrato
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.toObjectOrNull

/**
 * The typed Kotlin `Ign` object (`audio_bridge`, step 1b of `docs/tasks-archive/2026-10/20261016-kotlin-typed-doors.md`, Q46) against the
 * KlangScript `Ign`: for every function, the script call and the Kotlin call build equal nodes, with the defaults, with
 * numbers, with a signal, and with every builder knob (the script's `configure` lambda and the same builder in a Kotlin
 * `configure` lambda: `saw` and `vibrato` since S1, every other oscillator since S2). The values in a row are distinct,
 * so a swapped parameter or a missed knob shows.
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
            Ign.sine(220.ign) { it.analog(3.ign).fundamental(0.7.ign).analogSpread(0.4.ign).phase(0.25.ign) }
        },
        "Ign.sine(x => x.harmonics(7).octaves(3, 0.5).suboctaves(1, 0).partial(2.76).partial(0.66, 0.5, 0.25))" to {
            Ign.sine {
                it.harmonics(7.ign).octaves(3.ign, 0.5.ign).suboctaves(1.ign, 0.ign)
                    .partial(2.76.ign).partial(0.66.ign, 0.5.ign, 0.25.ign)
            }
        },
        "Ign.sine(x => x.analog($lfo).partial($lfo, $lfo))" to { Ign.sine { it.analog(lfoK).partial(lfoK, lfoK) } },

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
            Ign.ramp(110.ign) { it.analog(5.ign).resetSamples(4.0).shapeMax(0.3).phase(0.5.ign) }
        },
        "Ign.ramp(x => x.analog($lfo))" to { Ign.ramp { it.analog(lfoK) } },

        // ── square, pulze
        "Ign.square()" to { Ign.square() },
        "Ign.square(55)" to { Ign.square(55.ign) },
        "Ign.square(55, x => x.duty(0.3).analog(2).flankSamples(4).riseFlank(0.2).fallFlank(0.6).phase(0.1))" to {
            Ign.square(55.ign) { it.duty(0.3.ign).analog(2.ign).flankSamples(4.0).riseFlank(0.2).fallFlank(0.6).phase(0.1.ign) }
        },
        "Ign.square(x => x.duty($lfo))" to { Ign.square { it.duty(lfoK) } },
        "Ign.pulze()" to { Ign.pulze() },
        "Ign.pulze(55, x => x.duty(0.3).analog(2).phase(0.1))" to {
            Ign.pulze(55.ign) { it.duty(0.3.ign).analog(2.ign).phase(0.1.ign) }
        },
        "Ign.pulze($lfo, x => x.duty($lfo))" to { Ign.pulze(lfoK) { it.duty(lfoK) } },

        // ── tri, zawtooth, zamp, impulse
        "Ign.tri()" to { Ign.tri() },
        "Ign.tri(330, x => x.analog(3).phase(0.75))" to { Ign.tri(330.ign) { it.analog(3.ign).phase(0.75.ign) } },
        "Ign.tri($lfo)" to { Ign.tri(lfoK) },
        "Ign.zawtooth()" to { Ign.zawtooth() },
        "Ign.zawtooth(330, x => x.analog(3).phase(0.75))" to { Ign.zawtooth(330.ign) { it.analog(3.ign).phase(0.75.ign) } },
        "Ign.zamp()" to { Ign.zamp() },
        "Ign.zamp(330, x => x.analog(3).phase(0.75))" to { Ign.zamp(330.ign) { it.analog(3.ign).phase(0.75.ign) } },
        "Ign.impulse()" to { Ign.impulse() },
        "Ign.impulse(330, x => x.analog(3).phase(0.75))" to { Ign.impulse(330.ign) { it.analog(3.ign).phase(0.75.ign) } },

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
            Ign.supersaw(110.ign) {
                it.voices(9.ign).spread(0.1.ign).analog(5.ign).analogSpread(0.3.ign).spreadPower(1.5).sideAtten(0.2)
                    .gainJitter(0.05).centerJitter(0.6).phase(0.4.ign)
            }
        },
        "Ign.supersaw(x => x.phasePool())" to { Ign.supersaw { it.phasePool() } },
        "Ign.supersaw(x => x.phasePool(0, 0.2, 0.9, 7, 64, 3, \"random\", 2))" to {
            Ign.supersaw { it.phasePool(0.0, 0.2, 0.9, 7.0, 64.0, 3.0, "random", 2.0) }
        },
        "Ign.supersaw(x => x.spread($lfo))" to { Ign.supersaw { it.spread(lfoK) } },
        "Ign.supersine()" to { Ign.supersine() },
        "Ign.supersine(110, x => x.voices(9).spread(0.1).analog(5).analogSpread(0.3).spreadPower(1.5).sideAtten(0.2).gainJitter(0.05).centerJitter(0.6).phase(0.4))" to {
            Ign.supersine(110.ign) {
                it.voices(9.ign).spread(0.1.ign).analog(5.ign).analogSpread(0.3.ign).spreadPower(1.5).sideAtten(0.2)
                    .gainJitter(0.05).centerJitter(0.6).phase(0.4.ign)
            }
        },
        "Ign.supersine(x => x.phasePool())" to { Ign.supersine { it.phasePool() } },
        "Ign.supersine(x => x.phasePool(0, 0.2, 0.9, 7, 64, 3, \"random\", 2))" to {
            Ign.supersine { it.phasePool(0.0, 0.2, 0.9, 7.0, 64.0, 3.0, "random", 2.0) }
        },
        "Ign.supersquare()" to { Ign.supersquare() },
        "Ign.supersquare(110, x => x.voices(9).spread(0.1).analog(5).analogSpread(0.3).spreadPower(1.5).sideAtten(0.2).gainJitter(0.05).centerJitter(0.6).phase(0.4))" to {
            Ign.supersquare(110.ign) {
                it.voices(9.ign).spread(0.1.ign).analog(5.ign).analogSpread(0.3.ign).spreadPower(1.5).sideAtten(0.2)
                    .gainJitter(0.05).centerJitter(0.6).phase(0.4.ign)
            }
        },
        "Ign.supersquare(x => x.phasePool())" to { Ign.supersquare { it.phasePool() } },
        "Ign.supersquare(x => x.phasePool(0, 0.2, 0.9, 7, 64, 3, \"random\", 2))" to {
            Ign.supersquare { it.phasePool(0.0, 0.2, 0.9, 7.0, 64.0, 3.0, "random", 2.0) }
        },
        "Ign.supertri()" to { Ign.supertri() },
        "Ign.supertri(110, x => x.voices(9).spread(0.1).analog(5).analogSpread(0.3).spreadPower(1.5).sideAtten(0.2).gainJitter(0.05).centerJitter(0.6).phase(0.4))" to {
            Ign.supertri(110.ign) {
                it.voices(9.ign).spread(0.1.ign).analog(5.ign).analogSpread(0.3.ign).spreadPower(1.5).sideAtten(0.2)
                    .gainJitter(0.05).centerJitter(0.6).phase(0.4.ign)
            }
        },
        "Ign.supertri(x => x.phasePool())" to { Ign.supertri { it.phasePool() } },
        "Ign.supertri(x => x.phasePool(0, 0.2, 0.9, 7, 64, 3, \"random\", 2))" to {
            Ign.supertri { it.phasePool(0.0, 0.2, 0.9, 7.0, 64.0, 3.0, "random", 2.0) }
        },
        "Ign.superramp()" to { Ign.superramp() },
        "Ign.superramp(110, x => x.voices(9).spread(0.1).analog(5).analogSpread(0.3).spreadPower(1.5).sideAtten(0.2).gainJitter(0.05).centerJitter(0.6).phase(0.4))" to {
            Ign.superramp(110.ign) {
                it.voices(9.ign).spread(0.1.ign).analog(5.ign).analogSpread(0.3.ign).spreadPower(1.5).sideAtten(0.2)
                    .gainJitter(0.05).centerJitter(0.6).phase(0.4.ign)
            }
        },
        "Ign.superramp(x => x.phasePool())" to { Ign.superramp { it.phasePool() } },
        "Ign.superramp(x => x.phasePool(0, 0.2, 0.9, 7, 64, 3, \"random\", 2))" to {
            Ign.superramp { it.phasePool(0.0, 0.2, 0.9, 7.0, 64.0, 3.0, "random", 2.0) }
        },

        // ── physical models
        "Ign.pluck()" to { Ign.pluck() },
        "Ign.pluck(220)" to { Ign.pluck(220.ign) },
        "Ign.pluck(220, x => x.feedback(0.99).brightness(0.45).pickPosition(0.3).stiffness(0.1).analog(2))" to {
            Ign.pluck(220.ign) { it.feedback(0.99.ign).brightness(0.45.ign).pickPosition(0.3.ign).stiffness(0.1.ign).analog(2.ign) }
        },
        "Ign.pluck(x => x.brightness($lfo))" to { Ign.pluck { it.brightness(lfoK) } },
        "Ign.superpluck()" to { Ign.superpluck() },
        "Ign.superpluck(220, x => x.voices(6).spread(0.15).feedback(0.99).brightness(0.45).pickPosition(0.3).stiffness(0.1).analog(2).analogSpread(0.7))" to {
            Ign.superpluck(220.ign) {
                it.voices(6.ign).spread(0.15.ign).feedback(0.99.ign).brightness(0.45.ign).pickPosition(0.3.ign)
                    .stiffness(0.1.ign).analog(2.ign).analogSpread(0.7.ign)
            }
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

    // Every script oscillator door builds on its Kotlin door (typed doors S2), so a parity row cannot see a wrong Kotlin
    // default symbol: one row per door pins its default node, written out.
    "every Kotlin oscillator door builds its node at the default symbols" {
        Ign.sine() shouldBe IgnitorDsl.Sine(freq = IgnitorDsl.Freq)
        Ign.square() shouldBe IgnitorDsl.Pulze(freq = IgnitorDsl.Freq)
        Ign.tri() shouldBe IgnitorDsl.Tri(freq = IgnitorDsl.Freq)
        Ign.ramp() shouldBe IgnitorDsl.Ramp(freq = IgnitorDsl.Freq)
        Ign.zawtooth() shouldBe IgnitorDsl.Zawtooth(freq = IgnitorDsl.Freq)
        Ign.zamp() shouldBe IgnitorDsl.Zamp(freq = IgnitorDsl.Freq)
        Ign.impulse() shouldBe IgnitorDsl.Impulse(freq = IgnitorDsl.Freq)
        Ign.pulze() shouldBe IgnitorDsl.RawPulze(freq = IgnitorDsl.Freq)
        Ign.supersaw() shouldBe IgnitorDsl.SuperSaw(freq = IgnitorDsl.Freq)
        Ign.supersine() shouldBe IgnitorDsl.SuperSine(freq = IgnitorDsl.Freq)
        Ign.supersquare() shouldBe IgnitorDsl.SuperSquare(freq = IgnitorDsl.Freq)
        Ign.supertri() shouldBe IgnitorDsl.SuperTri(freq = IgnitorDsl.Freq)
        Ign.superramp() shouldBe IgnitorDsl.SuperRamp(freq = IgnitorDsl.Freq)
        // pluck and superpluck: their sealed default nodes are pinned below
    }

    // One row per typed member knob of the S2 builders, against the node written out with `.copy()`, never through a
    // builder, each value distinct and off its default: the Kotlin member, and the same member through its script
    // variant (typed doors S2; the saw and vibrato rows above are S1's). The pluck bases are the sealed nodes pinned below.
    "every typed oscillator member writes its own field, from Kotlin and from the script" {
        val sine = IgnitorDsl.Sine()
        val tri = IgnitorDsl.Tri()
        val zaw = IgnitorDsl.Zawtooth()
        val zamp = IgnitorDsl.Zamp()
        val imp = IgnitorDsl.Impulse()
        val raw = IgnitorDsl.RawPulze()
        val sq = IgnitorDsl.Pulze()
        val ramp = IgnitorDsl.Ramp()
        val ssaw = IgnitorDsl.SuperSaw()
        val ssine = IgnitorDsl.SuperSine()
        val ssquare = IgnitorDsl.SuperSquare()
        val stri = IgnitorDsl.SuperTri()
        val sramp = IgnitorDsl.SuperRamp()
        val pluck = IgnitorDsl.Pluck(feedback = 0.996.ign, brightness = 0.5.ign, pickPosition = 0.5.ign, stiffness = 0.ign)
        val spluck = IgnitorDsl.SuperPluck(
            voices = 8.ign,
            spread = 0.2.ign,
            feedback = 0.996.ign,
            brightness = 0.5.ign,
            pickPosition = 0.5.ign,
            stiffness = 0.ign,
        )
        val defaults = mapOf(
            "sine" to sine, "tri" to tri, "zawtooth" to zaw, "zamp" to zamp, "impulse" to imp, "pulze" to raw,
            "square" to sq, "ramp" to ramp, "supersaw" to ssaw, "supersine" to ssine, "supersquare" to ssquare,
            "supertri" to stri, "superramp" to sramp, "pluck" to pluck, "superpluck" to spluck,
        )

        val rows: List<Triple<String, () -> IgnitorDsl, IgnitorDsl>> = listOf(
            // sine
            Triple("Ign.sine(x => x.phase(0.25))", { Ign.sine { it.phase(0.25.ign) } }, sine.copy(phase = 0.25.ign)),
            Triple("Ign.sine(x => x.analog(7))", { Ign.sine { it.analog(7.ign) } }, sine.copy(analog = 7.ign)),
            Triple("Ign.sine(x => x.fundamental(0.6))", { Ign.sine { it.fundamental(0.6.ign) } }, sine.copy(fundamental = 0.6.ign)),
            Triple(
                "Ign.sine(x => x.harmonics(7, 0.5))",
                { Ign.sine { it.harmonics(7.ign, 0.5.ign) } },
                sine.copy(harmonics = 7.ign, harmonicsRolloff = 0.5.ign),
            ),
            Triple("Ign.sine(x => x.harmonics(7))", { Ign.sine { it.harmonics(7.ign) } }, sine.copy(harmonics = 7.ign)),
            Triple(
                "Ign.sine(x => x.octaves(3, 0.4))",
                { Ign.sine { it.octaves(3.ign, 0.4.ign) } },
                sine.copy(octaves = 3.ign, octavesRolloff = 0.4.ign),
            ),
            Triple("Ign.sine(x => x.octaves(3))", { Ign.sine { it.octaves(3.ign) } }, sine.copy(octaves = 3.ign)),
            Triple(
                "Ign.sine(x => x.suboctaves(2, 0))",
                { Ign.sine { it.suboctaves(2.ign, 0.ign) } },
                sine.copy(suboctaves = 2.ign, suboctavesRolloff = 0.ign),
            ),
            Triple("Ign.sine(x => x.suboctaves(2))", { Ign.sine { it.suboctaves(2.ign) } }, sine.copy(suboctaves = 2.ign)),
            Triple(
                "Ign.sine(x => x.partial(2.76, 0.4, 0.25))",
                { Ign.sine { it.partial(2.76.ign, 0.4.ign, 0.25.ign) } },
                sine.copy(partials = listOf(IgnitorDsl.Sine.Partial(ratio = 2.76.ign, gain = 0.4.ign, phase = 0.25.ign))),
            ),
            Triple(
                "Ign.sine(x => x.partial(2.76))",
                { Ign.sine { it.partial(2.76.ign) } },
                sine.copy(partials = listOf(IgnitorDsl.Sine.Partial(ratio = 2.76.ign, gain = 1.ign, phase = 0.ign))),
            ),
            Triple("Ign.sine(x => x.analogSpread(0.3))", { Ign.sine { it.analogSpread(0.3.ign) } }, sine.copy(analogSpread = 0.3.ign)),
            // tri
            Triple("Ign.tri(x => x.phase(0.25))", { Ign.tri { it.phase(0.25.ign) } }, tri.copy(phase = 0.25.ign)),
            Triple("Ign.tri(x => x.analog(7))", { Ign.tri { it.analog(7.ign) } }, tri.copy(analog = 7.ign)),
            // zawtooth
            Triple("Ign.zawtooth(x => x.phase(0.25))", { Ign.zawtooth { it.phase(0.25.ign) } }, zaw.copy(phase = 0.25.ign)),
            Triple("Ign.zawtooth(x => x.analog(7))", { Ign.zawtooth { it.analog(7.ign) } }, zaw.copy(analog = 7.ign)),
            // zamp
            Triple("Ign.zamp(x => x.phase(0.25))", { Ign.zamp { it.phase(0.25.ign) } }, zamp.copy(phase = 0.25.ign)),
            Triple("Ign.zamp(x => x.analog(7))", { Ign.zamp { it.analog(7.ign) } }, zamp.copy(analog = 7.ign)),
            // impulse
            Triple("Ign.impulse(x => x.phase(0.25))", { Ign.impulse { it.phase(0.25.ign) } }, imp.copy(phase = 0.25.ign)),
            Triple("Ign.impulse(x => x.analog(7))", { Ign.impulse { it.analog(7.ign) } }, imp.copy(analog = 7.ign)),
            // pulze
            Triple("Ign.pulze(x => x.phase(0.25))", { Ign.pulze { it.phase(0.25.ign) } }, raw.copy(phase = 0.25.ign)),
            Triple("Ign.pulze(x => x.duty(0.3))", { Ign.pulze { it.duty(0.3.ign) } }, raw.copy(duty = 0.3.ign)),
            Triple("Ign.pulze(x => x.analog(7))", { Ign.pulze { it.analog(7.ign) } }, raw.copy(analog = 7.ign)),
            // square
            Triple("Ign.square(x => x.phase(0.25))", { Ign.square { it.phase(0.25.ign) } }, sq.copy(phase = 0.25.ign)),
            Triple("Ign.square(x => x.duty(0.3))", { Ign.square { it.duty(0.3.ign) } }, sq.copy(duty = 0.3.ign)),
            Triple("Ign.square(x => x.analog(7))", { Ign.square { it.analog(7.ign) } }, sq.copy(analog = 7.ign)),
            Triple("Ign.square(x => x.flankSamples(4))", { Ign.square { it.flankSamples(4.0) } }, sq.copy(flankSamples = 4.0)),
            Triple("Ign.square(x => x.riseFlank(0.2))", { Ign.square { it.riseFlank(0.2) } }, sq.copy(riseFlank = 0.2)),
            Triple("Ign.square(x => x.fallFlank(0.6))", { Ign.square { it.fallFlank(0.6) } }, sq.copy(fallFlank = 0.6)),
            // ramp
            Triple("Ign.ramp(x => x.phase(0.25))", { Ign.ramp { it.phase(0.25.ign) } }, ramp.copy(phase = 0.25.ign)),
            Triple("Ign.ramp(x => x.analog(7))", { Ign.ramp { it.analog(7.ign) } }, ramp.copy(analog = 7.ign)),
            Triple("Ign.ramp(x => x.resetSamples(4))", { Ign.ramp { it.resetSamples(4.0) } }, ramp.copy(resetSamples = 4.0)),
            Triple("Ign.ramp(x => x.shapeMax(0.3))", { Ign.ramp { it.shapeMax(0.3) } }, ramp.copy(shapeMax = 0.3)),
            // supersaw
            Triple("Ign.supersaw(x => x.phase(0.25))", { Ign.supersaw { it.phase(0.25.ign) } }, ssaw.copy(phase = 0.25.ign)),
            Triple("Ign.supersaw(x => x.voices(9))", { Ign.supersaw { it.voices(9.ign) } }, ssaw.copy(voices = 9.ign)),
            Triple("Ign.supersaw(x => x.spread(0.1))", { Ign.supersaw { it.spread(0.1.ign) } }, ssaw.copy(spread = 0.1.ign)),
            Triple("Ign.supersaw(x => x.analog(7))", { Ign.supersaw { it.analog(7.ign) } }, ssaw.copy(analog = 7.ign)),
            Triple(
                "Ign.supersaw(x => x.analogSpread(0.3))",
                { Ign.supersaw { it.analogSpread(0.3.ign) } },
                ssaw.copy(analogSpread = 0.3.ign),
            ),
            Triple("Ign.supersaw(x => x.spreadPower(1.5))", { Ign.supersaw { it.spreadPower(1.5) } }, ssaw.copy(spreadPower = 1.5)),
            Triple("Ign.supersaw(x => x.sideAtten(0.25))", { Ign.supersaw { it.sideAtten(0.25) } }, ssaw.copy(sideAtten = 0.25)),
            Triple("Ign.supersaw(x => x.gainJitter(0.05))", { Ign.supersaw { it.gainJitter(0.05) } }, ssaw.copy(gainJitter = 0.05)),
            Triple("Ign.supersaw(x => x.centerJitter(0.6))", { Ign.supersaw { it.centerJitter(0.6) } }, ssaw.copy(centerJitterScale = 0.6)),
            Triple("Ign.supersaw(x => x.phasePool())", { Ign.supersaw { it.phasePool() } }, ssaw.copy(phasePool = 1.0)),
            Triple(
                "Ign.supersaw(x => x.phasePool(0.5, 0.2, 0.9, 7, 64, 3, \"random\", 2))",
                { Ign.supersaw { it.phasePool(0.5, 0.2, 0.9, 7.0, 64.0, 3.0, "random", 2.0) } },
                ssaw.copy(phasePool = 0.5, kMin = 0.2, kMax = 0.9, drawTries = 7.0, poolSize = 64.0, refreshEvery = 3.0, selection = "random", warmup = 2.0),
            ),
            // supersine
            Triple("Ign.supersine(x => x.phase(0.25))", { Ign.supersine { it.phase(0.25.ign) } }, ssine.copy(phase = 0.25.ign)),
            Triple("Ign.supersine(x => x.voices(9))", { Ign.supersine { it.voices(9.ign) } }, ssine.copy(voices = 9.ign)),
            Triple("Ign.supersine(x => x.spread(0.1))", { Ign.supersine { it.spread(0.1.ign) } }, ssine.copy(spread = 0.1.ign)),
            Triple("Ign.supersine(x => x.analog(7))", { Ign.supersine { it.analog(7.ign) } }, ssine.copy(analog = 7.ign)),
            Triple(
                "Ign.supersine(x => x.analogSpread(0.3))",
                { Ign.supersine { it.analogSpread(0.3.ign) } },
                ssine.copy(analogSpread = 0.3.ign),
            ),
            Triple("Ign.supersine(x => x.spreadPower(1.5))", { Ign.supersine { it.spreadPower(1.5) } }, ssine.copy(spreadPower = 1.5)),
            Triple("Ign.supersine(x => x.sideAtten(0.25))", { Ign.supersine { it.sideAtten(0.25) } }, ssine.copy(sideAtten = 0.25)),
            Triple("Ign.supersine(x => x.gainJitter(0.05))", { Ign.supersine { it.gainJitter(0.05) } }, ssine.copy(gainJitter = 0.05)),
            Triple(
                "Ign.supersine(x => x.centerJitter(0.6))",
                { Ign.supersine { it.centerJitter(0.6) } },
                ssine.copy(centerJitterScale = 0.6),
            ),
            Triple("Ign.supersine(x => x.phasePool())", { Ign.supersine { it.phasePool() } }, ssine.copy(phasePool = 1.0)),
            Triple(
                "Ign.supersine(x => x.phasePool(0.5, 0.2, 0.9, 7, 64, 3, \"random\", 2))",
                { Ign.supersine { it.phasePool(0.5, 0.2, 0.9, 7.0, 64.0, 3.0, "random", 2.0) } },
                ssine.copy(phasePool = 0.5, kMin = 0.2, kMax = 0.9, drawTries = 7.0, poolSize = 64.0, refreshEvery = 3.0, selection = "random", warmup = 2.0),
            ),
            // supersquare
            Triple("Ign.supersquare(x => x.phase(0.25))", { Ign.supersquare { it.phase(0.25.ign) } }, ssquare.copy(phase = 0.25.ign)),
            Triple("Ign.supersquare(x => x.voices(9))", { Ign.supersquare { it.voices(9.ign) } }, ssquare.copy(voices = 9.ign)),
            Triple("Ign.supersquare(x => x.spread(0.1))", { Ign.supersquare { it.spread(0.1.ign) } }, ssquare.copy(spread = 0.1.ign)),
            Triple("Ign.supersquare(x => x.analog(7))", { Ign.supersquare { it.analog(7.ign) } }, ssquare.copy(analog = 7.ign)),
            Triple(
                "Ign.supersquare(x => x.analogSpread(0.3))",
                { Ign.supersquare { it.analogSpread(0.3.ign) } },
                ssquare.copy(analogSpread = 0.3.ign),
            ),
            Triple(
                "Ign.supersquare(x => x.spreadPower(1.5))",
                { Ign.supersquare { it.spreadPower(1.5) } },
                ssquare.copy(spreadPower = 1.5),
            ),
            Triple("Ign.supersquare(x => x.sideAtten(0.25))", { Ign.supersquare { it.sideAtten(0.25) } }, ssquare.copy(sideAtten = 0.25)),
            Triple(
                "Ign.supersquare(x => x.gainJitter(0.05))",
                { Ign.supersquare { it.gainJitter(0.05) } },
                ssquare.copy(gainJitter = 0.05),
            ),
            Triple(
                "Ign.supersquare(x => x.centerJitter(0.6))",
                { Ign.supersquare { it.centerJitter(0.6) } },
                ssquare.copy(centerJitterScale = 0.6),
            ),
            Triple("Ign.supersquare(x => x.phasePool())", { Ign.supersquare { it.phasePool() } }, ssquare.copy(phasePool = 1.0)),
            Triple(
                "Ign.supersquare(x => x.phasePool(0.5, 0.2, 0.9, 7, 64, 3, \"random\", 2))",
                { Ign.supersquare { it.phasePool(0.5, 0.2, 0.9, 7.0, 64.0, 3.0, "random", 2.0) } },
                ssquare.copy(phasePool = 0.5, kMin = 0.2, kMax = 0.9, drawTries = 7.0, poolSize = 64.0, refreshEvery = 3.0, selection = "random", warmup = 2.0),
            ),
            // supertri
            Triple("Ign.supertri(x => x.phase(0.25))", { Ign.supertri { it.phase(0.25.ign) } }, stri.copy(phase = 0.25.ign)),
            Triple("Ign.supertri(x => x.voices(9))", { Ign.supertri { it.voices(9.ign) } }, stri.copy(voices = 9.ign)),
            Triple("Ign.supertri(x => x.spread(0.1))", { Ign.supertri { it.spread(0.1.ign) } }, stri.copy(spread = 0.1.ign)),
            Triple("Ign.supertri(x => x.analog(7))", { Ign.supertri { it.analog(7.ign) } }, stri.copy(analog = 7.ign)),
            Triple(
                "Ign.supertri(x => x.analogSpread(0.3))",
                { Ign.supertri { it.analogSpread(0.3.ign) } },
                stri.copy(analogSpread = 0.3.ign),
            ),
            Triple("Ign.supertri(x => x.spreadPower(1.5))", { Ign.supertri { it.spreadPower(1.5) } }, stri.copy(spreadPower = 1.5)),
            Triple("Ign.supertri(x => x.sideAtten(0.25))", { Ign.supertri { it.sideAtten(0.25) } }, stri.copy(sideAtten = 0.25)),
            Triple("Ign.supertri(x => x.gainJitter(0.05))", { Ign.supertri { it.gainJitter(0.05) } }, stri.copy(gainJitter = 0.05)),
            Triple("Ign.supertri(x => x.centerJitter(0.6))", { Ign.supertri { it.centerJitter(0.6) } }, stri.copy(centerJitterScale = 0.6)),
            Triple("Ign.supertri(x => x.phasePool())", { Ign.supertri { it.phasePool() } }, stri.copy(phasePool = 1.0)),
            Triple(
                "Ign.supertri(x => x.phasePool(0.5, 0.2, 0.9, 7, 64, 3, \"random\", 2))",
                { Ign.supertri { it.phasePool(0.5, 0.2, 0.9, 7.0, 64.0, 3.0, "random", 2.0) } },
                stri.copy(phasePool = 0.5, kMin = 0.2, kMax = 0.9, drawTries = 7.0, poolSize = 64.0, refreshEvery = 3.0, selection = "random", warmup = 2.0),
            ),
            // superramp
            Triple("Ign.superramp(x => x.phase(0.25))", { Ign.superramp { it.phase(0.25.ign) } }, sramp.copy(phase = 0.25.ign)),
            Triple("Ign.superramp(x => x.voices(9))", { Ign.superramp { it.voices(9.ign) } }, sramp.copy(voices = 9.ign)),
            Triple("Ign.superramp(x => x.spread(0.1))", { Ign.superramp { it.spread(0.1.ign) } }, sramp.copy(spread = 0.1.ign)),
            Triple("Ign.superramp(x => x.analog(7))", { Ign.superramp { it.analog(7.ign) } }, sramp.copy(analog = 7.ign)),
            Triple(
                "Ign.superramp(x => x.analogSpread(0.3))",
                { Ign.superramp { it.analogSpread(0.3.ign) } },
                sramp.copy(analogSpread = 0.3.ign),
            ),
            Triple("Ign.superramp(x => x.spreadPower(1.5))", { Ign.superramp { it.spreadPower(1.5) } }, sramp.copy(spreadPower = 1.5)),
            Triple("Ign.superramp(x => x.sideAtten(0.25))", { Ign.superramp { it.sideAtten(0.25) } }, sramp.copy(sideAtten = 0.25)),
            Triple("Ign.superramp(x => x.gainJitter(0.05))", { Ign.superramp { it.gainJitter(0.05) } }, sramp.copy(gainJitter = 0.05)),
            Triple(
                "Ign.superramp(x => x.centerJitter(0.6))",
                { Ign.superramp { it.centerJitter(0.6) } },
                sramp.copy(centerJitterScale = 0.6),
            ),
            Triple("Ign.superramp(x => x.phasePool())", { Ign.superramp { it.phasePool() } }, sramp.copy(phasePool = 1.0)),
            Triple(
                "Ign.superramp(x => x.phasePool(0.5, 0.2, 0.9, 7, 64, 3, \"random\", 2))",
                { Ign.superramp { it.phasePool(0.5, 0.2, 0.9, 7.0, 64.0, 3.0, "random", 2.0) } },
                sramp.copy(phasePool = 0.5, kMin = 0.2, kMax = 0.9, drawTries = 7.0, poolSize = 64.0, refreshEvery = 3.0, selection = "random", warmup = 2.0),
            ),
            // pluck
            Triple("Ign.pluck(x => x.feedback(0.99))", { Ign.pluck { it.feedback(0.99.ign) } }, pluck.copy(feedback = 0.99.ign)),
            Triple("Ign.pluck(x => x.brightness(0.45))", { Ign.pluck { it.brightness(0.45.ign) } }, pluck.copy(brightness = 0.45.ign)),
            Triple("Ign.pluck(x => x.pickPosition(0.3))", { Ign.pluck { it.pickPosition(0.3.ign) } }, pluck.copy(pickPosition = 0.3.ign)),
            Triple("Ign.pluck(x => x.stiffness(0.1))", { Ign.pluck { it.stiffness(0.1.ign) } }, pluck.copy(stiffness = 0.1.ign)),
            Triple("Ign.pluck(x => x.analog(7))", { Ign.pluck { it.analog(7.ign) } }, pluck.copy(analog = 7.ign)),
            // superpluck
            Triple("Ign.superpluck(x => x.voices(6))", { Ign.superpluck { it.voices(6.ign) } }, spluck.copy(voices = 6.ign)),
            Triple("Ign.superpluck(x => x.spread(0.15))", { Ign.superpluck { it.spread(0.15.ign) } }, spluck.copy(spread = 0.15.ign)),
            Triple("Ign.superpluck(x => x.feedback(0.99))", { Ign.superpluck { it.feedback(0.99.ign) } }, spluck.copy(feedback = 0.99.ign)),
            Triple(
                "Ign.superpluck(x => x.brightness(0.45))",
                { Ign.superpluck { it.brightness(0.45.ign) } },
                spluck.copy(brightness = 0.45.ign),
            ),
            Triple(
                "Ign.superpluck(x => x.pickPosition(0.3))",
                { Ign.superpluck { it.pickPosition(0.3.ign) } },
                spluck.copy(pickPosition = 0.3.ign),
            ),
            Triple("Ign.superpluck(x => x.stiffness(0.1))", { Ign.superpluck { it.stiffness(0.1.ign) } }, spluck.copy(stiffness = 0.1.ign)),
            Triple("Ign.superpluck(x => x.analog(7))", { Ign.superpluck { it.analog(7.ign) } }, spluck.copy(analog = 7.ign)),
            Triple(
                "Ign.superpluck(x => x.analogSpread(0.3))",
                { Ign.superpluck { it.analogSpread(0.3.ign) } },
                spluck.copy(analogSpread = 0.3.ign),
            ),
        )

        for ((code, kotlin, expected) in rows) {
            withClue(code) {
                // every row moves its knob off the default node (`Ign.<door>(x => ...)` names the door)
                expected shouldNotBe defaults.getValue(code.substringAfter("Ign.").substringBefore("("))
                kotlin() shouldBe expected
                ks(code) shouldBe expected
            }
        }
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
