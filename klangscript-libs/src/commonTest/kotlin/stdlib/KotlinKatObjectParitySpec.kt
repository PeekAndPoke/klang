/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.Kat
import io.peekandpoke.klang.audio_bridge.KatalystBuilder
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystKnobDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import io.peekandpoke.klang.audio_bridge.kat
import io.peekandpoke.klang.audio_bridge.range
import io.peekandpoke.klang.audio_bridge.unaryMinus
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.KlangScriptTypeError
import io.peekandpoke.klang.script.runtime.toObjectOrNull

/**
 * The typed Kotlin `Kat` object (`audio_bridge`, Q46 of `docs/tasks/kotlin-typed-doors.md`, on its builders since
 * rollout S5) against the KlangScript `Kat`: for every function the typed object mirrors, the script call and the
 * Kotlin call build equal values, with the defaults, with numbers, with a signal, and with every builder knob (the same
 * `configure` lambda and builder on both doors). The chain door (`Kat.build`, `Kat { ... }`) and every member of its
 * builders have a row against the node WRITTEN OUT (`.copy()` on the bare stage, never through a builder), from Kotlin
 * and from the script, and every stage door has a default-node row written out with literal numbers, never a constant
 * (`/dsl-design` section 3, the typed-door pattern).
 */
class KotlinKatObjectParitySpec : StringSpec({

    fun ks(code: String): Any {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        return engine.execute(code).toObjectOrNull<Any>()!!
    }

    fun k(value: Double): KatalystKnobDsl.Constant = KatalystKnobDsl.Constant(value)

    val lfo = "Kat.perlin(0.5)"
    val lfoK = Kat.perlin(0.5.kat)

    val rows: List<Pair<String, () -> Any>> = listOf(
        "Kat.classic()" to { Kat.classic() },
        "Kat.param(\"depth\", 0.4)" to { Kat.param("depth", 0.4) },
        "Kat.param(\"depth\", 0.4, \"how deep\")" to { Kat.param("depth", 0.4, "how deep") },

        "Kat.sine(0.7)" to { Kat.sine(0.7.kat) },
        "Kat.sine($lfo)" to { Kat.sine(lfoK) },
        "Kat.sine(0.7, x => x.phase(0.25))" to { Kat.sine(0.7.kat) { it.phase(0.25.kat) } },
        "Kat.sine(0.7, x => x.phase($lfo))" to { Kat.sine(0.7.kat) { it.phase(lfoK) } },
        "Kat.saw(0.7)" to { Kat.saw(0.7.kat) },
        "Kat.saw(0.7, x => x.phase(0.25))" to { Kat.saw(0.7.kat) { it.phase(0.25.kat) } },
        "Kat.saw($lfo)" to { Kat.saw(lfoK) },
        "Kat.ramp(0.7)" to { Kat.ramp(0.7.kat) },
        "Kat.ramp(0.7, x => x.phase(0.25))" to { Kat.ramp(0.7.kat) { it.phase(0.25.kat) } },
        "Kat.tri(0.7)" to { Kat.tri(0.7.kat) },
        "Kat.tri(0.7, x => x.phase(0.25))" to { Kat.tri(0.7.kat) { it.phase(0.25.kat) } },
        "Kat.square(0.7)" to { Kat.square(0.7.kat) },
        "Kat.square(0.7, x => x.duty(0.3).phase(0.25))" to { Kat.square(0.7.kat) { it.duty(0.3.kat).phase(0.25.kat) } },
        "Kat.square($lfo, x => x.duty($lfo))" to { Kat.square(lfoK) { it.duty(lfoK) } },

        "Kat.perlin(0.5)" to { Kat.perlin(0.5.kat) },
        "Kat.perlin(0.5, 4, 0.7)" to { Kat.perlin(rate = 0.5.kat, octaves = 4.kat, persistence = 0.7.kat) },
        "Kat.perlin(rate = Kat.sine(0.1), persistence = 0.7)" to { Kat.perlin(rate = Kat.sine(0.1.kat), persistence = 0.7.kat) },
        "Kat.berlin(0.5)" to { Kat.berlin(0.5.kat) },
        "Kat.berlin(0.5, 4, 0.7)" to { Kat.berlin(rate = 0.5.kat, octaves = 4.kat, persistence = 0.7.kat) },
        "Kat.berlin(rate = 0.5, octaves = $lfo)" to { Kat.berlin(rate = 0.5.kat, octaves = lfoK) },

        // The chain door: build and its call form, the empty chain, and a chain through every builder shape.
        "Kat()" to { Kat() },
        "Kat.build()" to { Kat.build() },
        "Kat(k => k.reverb(0.2, 4).gain(1.2))" to { Kat { it.reverb(wet = 0.2.kat, size = 4.kat).gain(1.2.kat) } },
        "Kat.build(k => k.reverb(0.2, 4).gain(1.2))" to { Kat.build { it.reverb(wet = 0.2.kat, size = 4.kat).gain(1.2.kat) } },
        "Kat(k => k.classic().delay(0.3, 0.25, 0.4, d => d.cap(0.8)).phaser(0.5, 0.2, p => p.floor(0.3)))" to {
            Kat {
                it.classic()
                    .delay(wet = 0.3.kat, time = 0.25.kat, feedback = 0.4.kat) { d -> d.cap(0.8.kat) }
                    .phaser(wet = 0.5.kat, rate = 0.2.kat) { p -> p.floor(0.3.kat) }
            }
        },
        "Kat(k => k.body(0.4, \"wood\", b => b.floor(0.3)).vowel(0.6, \"o\", v => v.floor(0.1)))" to {
            Kat {
                it.body(wet = 0.4.kat, material = "wood") { b -> b.floor(0.3.kat) }
                    .vowel(wet = 0.6.kat, vowel = "o") { v -> v.floor(0.1.kat) }
            }
        },
        "Kat(k => k.gain(Kat.sine(0.5, x => x.phase(0.25)).range(0.5, 1)).times(Kat.param(\"t\", 1)))" to {
            Kat {
                it.gain(Kat.sine(0.5.kat) { x -> x.phase(0.25.kat) }.range(from = 0.5.kat, to = 1.kat))
                    .times(Kat.param("t", 1.0))
            }
        },
        "Kat(k => k.eq(e => e.band(300, 0.8, 2).tap(850)).limiter(-3))" to {
            Kat {
                it.eq { e -> e.band(freq = 300.kat, q = 0.8.kat, db = 2.kat).tap(freq = 850.kat) }
                    .limiter(threshold = -3.kat)
            }
        },
    )

    rows.forEach { (code, kotlin) ->
        "$code equals the Kotlin Kat call" {
            withClue(code) {
                ks(code) shouldBe kotlin()
            }
        }
    }

    "Kat.slot is the script's Kat.slot, leaf for leaf" {
        Kat.slot shouldBeSameInstanceAs KatalystDsl.Slots
        ks("Kat.slot.reverb.wet") shouldBeSameInstanceAs Kat.slot.reverb.wet
    }

    // The script stage doors build on the typed members (rollout S5), so a parity row cannot see a wrong Kotlin default
    // symbol, nor a script `null` substituted with another one: every stage door's bare node, written out with
    // literal numbers, from both doors.
    "every stage door builds its bare stage at the default symbols, written out, from both doors" {
        val unset = Double.NaN

        listOf(
            Triple(
                "body()",
                { b: KatalystBuilder -> b.body() },
                KatalystStageDsl.Body(material = k(unset), wet = k(0.5), floor = k(0.4)),
            ),
            Triple(
                "vowel()",
                { b: KatalystBuilder -> b.vowel() },
                KatalystStageDsl.Vowel(vowel = k(unset), wet = k(0.5), floor = k(0.2)),
            ),
            Triple(
                "delay()",
                { b: KatalystBuilder -> b.delay() },
                KatalystStageDsl.Delay(wet = k(0.25), time = k(0.25), feedback = k(0.3), cap = k(1.0)),
            ),
            Triple(
                "reverb()",
                { b: KatalystBuilder -> b.reverb() },
                KatalystStageDsl.Reverb(wet = k(0.25), size = k(5.0), lowpass = null),
            ),
            Triple(
                "phaser()",
                { b: KatalystBuilder -> b.phaser() },
                KatalystStageDsl.Phaser(rate = k(0.0), wet = k(0.0), center = k(1000.0), sweep = k(1000.0), floor = k(1.0)),
            ),
            Triple(
                "compressor()",
                { b: KatalystBuilder -> b.compressor() },
                KatalystStageDsl.Compressor(
                    threshold = k(-20.0), ratio = k(4.0), knee = k(6.0), attack = k(0.003), release = k(0.1),
                    lookahead = 0.0,
                ),
            ),
            // The limiter preset: the compressor stage with the limiter numbers, which live once, in the typed door.
            Triple(
                "limiter()",
                { b: KatalystBuilder -> b.limiter() },
                KatalystStageDsl.Compressor(
                    threshold = k(-1.0), ratio = k(20.0), knee = k(2.0), attack = k(0.001), release = k(0.1),
                    lookahead = 0.0,
                ),
            ),
            Triple(
                "distort()",
                { b: KatalystBuilder -> b.distort() },
                KatalystStageDsl.Distort(amount = k(0.5), shape = 0, oversample = 2),
            ),
            Triple(
                "duck()",
                { b: KatalystBuilder -> b.duck() },
                KatalystStageDsl.Duck(orbit = k(unset), depth = k(0.0), attack = k(0.1)),
            ),
            Triple("eq()", { b: KatalystBuilder -> b.eq() }, KatalystStageDsl.Eq(sections = emptyList())),
            Triple("gain()", { b: KatalystBuilder -> b.gain() }, KatalystStageDsl.Gain(gain = k(1.0))),
            // The name forms' own defaults: everything but the name. The indices are literals (wood is 1, soprano:o
            // 66, hard 1), never the lookup under test.
            Triple(
                "body(material = \"wood\")",
                { b: KatalystBuilder -> b.body(material = "wood") },
                KatalystStageDsl.Body(material = k(1.0), wet = k(0.5), floor = k(0.4)),
            ),
            Triple(
                "vowel(vowel = \"o\")",
                { b: KatalystBuilder -> b.vowel(vowel = "o") },
                KatalystStageDsl.Vowel(vowel = k(66.0), wet = k(0.5), floor = k(0.2)),
            ),
            Triple(
                "distort(shape = \"hard\")",
                { b: KatalystBuilder -> b.distort(shape = "hard") },
                KatalystStageDsl.Distort(amount = k(0.5), shape = 1, oversample = 2),
            ),
        ).forEach { (call, kotlin, stage) ->
            withClue("Kotlin $call") { Kat { kotlin(it) } shouldBe KatalystDsl.of(stage) }
            withClue("script $call") { ks("Kat(k => k.$call)") shouldBe KatalystDsl.of(stage) }
        }
    }

    // Both doors share the builder members, so a parity row cannot see a member that writes the wrong field: one row
    // per member knob (and per stage-door parameter) against the stage written out with `.copy()` on the bare data
    // class, never through a builder, each value distinct and off its default, from Kotlin and through the script
    // variant.
    "every typed Katalyst member writes its own field, from Kotlin and from the script" {
        val body = KatalystStageDsl.Body()
        val vowel = KatalystStageDsl.Vowel()
        val delay = KatalystStageDsl.Delay()
        val reverb = KatalystStageDsl.Reverb()
        val phaser = KatalystStageDsl.Phaser()
        val comp = KatalystStageDsl.Compressor()
        val limiter = KatalystStageDsl.Compressor(
            threshold = k(-1.0), ratio = k(20.0), knee = k(2.0), attack = k(0.001), release = k(0.1), lookahead = 0.0,
        )
        val distort = KatalystStageDsl.Distort()
        val duck = KatalystStageDsl.Duck()
        val gain = KatalystStageDsl.Gain()
        val tube = 10

        // (script call, Kotlin call, the stage written out, the bare stage it must move off)
        val rows: List<Triple<String, (KatalystBuilder) -> KatalystBuilder, Pair<KatalystStageDsl, KatalystStageDsl>>> = listOf(
            Triple("body(wet = 0.7)", { b -> b.body(wet = 0.7.kat) }, body.copy(wet = k(0.7)) to body),
            Triple("body(material = 3)", { b -> b.body(material = 3.kat) }, body.copy(material = k(3.0)) to body),
            Triple("body(material = \"glass\")", { b -> b.body(material = "glass") }, body.copy(material = k(11.0)) to body),
            Triple("body(configure = x => x.floor(0.3))", { b -> b.body { x -> x.floor(0.3.kat) } }, body.copy(floor = k(0.3)) to body),
            Triple("vowel(wet = 0.6)", { b -> b.vowel(wet = 0.6.kat) }, vowel.copy(wet = k(0.6)) to vowel),
            Triple("vowel(vowel = 4)", { b -> b.vowel(vowel = 4.kat) }, vowel.copy(vowel = k(4.0)) to vowel),
            Triple("vowel(vowel = \"bass:a\")", { b -> b.vowel(vowel = "bass:a") }, vowel.copy(vowel = k(1.0)) to vowel),
            Triple("vowel(configure = x => x.floor(0.1))", { b -> b.vowel { x -> x.floor(0.1.kat) } }, vowel.copy(floor = k(0.1)) to vowel),
            Triple("delay(wet = 0.2)", { b -> b.delay(wet = 0.2.kat) }, delay.copy(wet = k(0.2)) to delay),
            Triple("delay(time = 0.5)", { b -> b.delay(time = 0.5.kat) }, delay.copy(time = k(0.5)) to delay),
            Triple("delay(feedback = 0.9)", { b -> b.delay(feedback = 0.9.kat) }, delay.copy(feedback = k(0.9)) to delay),
            Triple("delay(configure = x => x.cap(3))", { b -> b.delay { x -> x.cap(3.kat) } }, delay.copy(cap = k(3.0)) to delay),
            Triple("reverb(wet = 0.3)", { b -> b.reverb(wet = 0.3.kat) }, reverb.copy(wet = k(0.3)) to reverb),
            Triple("reverb(size = 8)", { b -> b.reverb(size = 8.kat) }, reverb.copy(size = k(8.0)) to reverb),
            Triple("reverb(lowpass = 6000)", { b -> b.reverb(lowpass = 6000.kat) }, reverb.copy(lowpass = k(6000.0)) to reverb),
            Triple("phaser(wet = 0.5)", { b -> b.phaser(wet = 0.5.kat) }, phaser.copy(wet = k(0.5)) to phaser),
            Triple("phaser(rate = 0.3)", { b -> b.phaser(rate = 0.3.kat) }, phaser.copy(rate = k(0.3)) to phaser),
            Triple("phaser(center = 800)", { b -> b.phaser(center = 800.kat) }, phaser.copy(center = k(800.0)) to phaser),
            Triple("phaser(sweep = 1200)", { b -> b.phaser(sweep = 1200.kat) }, phaser.copy(sweep = k(1200.0)) to phaser),
            Triple("phaser(configure = x => x.floor(0.2))", { b -> b.phaser { x -> x.floor(0.2.kat) } }, phaser.copy(floor = k(0.2)) to phaser),
            Triple("compressor(threshold = -21)", { b -> b.compressor(threshold = -21.kat) }, comp.copy(threshold = k(-21.0)) to comp),
            Triple("compressor(ratio = 3)", { b -> b.compressor(ratio = 3.kat) }, comp.copy(ratio = k(3.0)) to comp),
            Triple("compressor(knee = 5)", { b -> b.compressor(knee = 5.kat) }, comp.copy(knee = k(5.0)) to comp),
            Triple("compressor(attack = 0.005)", { b -> b.compressor(attack = 0.005.kat) }, comp.copy(attack = k(0.005)) to comp),
            Triple("compressor(release = 0.12)", { b -> b.compressor(release = 0.12.kat) }, comp.copy(release = k(0.12)) to comp),
            Triple("compressor(lookahead = 0.004)", { b -> b.compressor(lookahead = 0.004) }, comp.copy(lookahead = 0.004) to comp),
            Triple("limiter(threshold = -3)", { b -> b.limiter(threshold = -3.kat) }, limiter.copy(threshold = k(-3.0)) to limiter),
            Triple("limiter(ratio = 12)", { b -> b.limiter(ratio = 12.kat) }, limiter.copy(ratio = k(12.0)) to limiter),
            Triple("limiter(knee = 1)", { b -> b.limiter(knee = 1.kat) }, limiter.copy(knee = k(1.0)) to limiter),
            Triple("limiter(attack = 0.004)", { b -> b.limiter(attack = 0.004.kat) }, limiter.copy(attack = k(0.004)) to limiter),
            Triple("limiter(release = 0.2)", { b -> b.limiter(release = 0.2.kat) }, limiter.copy(release = k(0.2)) to limiter),
            Triple("limiter(lookahead = 0.003)", { b -> b.limiter(lookahead = 0.003) }, limiter.copy(lookahead = 0.003) to limiter),
            Triple("distort(amount = 0.15)", { b -> b.distort(amount = 0.15.kat) }, distort.copy(amount = k(0.15)) to distort),
            Triple("distort(shape = \"tube\")", { b -> b.distort(shape = "tube") }, distort.copy(shape = tube) to distort),
            Triple("distort(oversample = 8)", { b -> b.distort(oversample = 8) }, distort.copy(oversample = 8) to distort),
            Triple("duck(orbit = 2)", { b -> b.duck(orbit = 2.kat) }, duck.copy(orbit = k(2.0)) to duck),
            Triple("duck(depth = 0.8)", { b -> b.duck(depth = 0.8.kat) }, duck.copy(depth = k(0.8)) to duck),
            Triple("duck(attack = 0.05)", { b -> b.duck(attack = 0.05.kat) }, duck.copy(attack = k(0.05)) to duck),
            Triple(
                "eq(e => e.band(300, 0.8, 2))",
                { b -> b.eq { e -> e.band(freq = 300.kat, q = 0.8.kat, db = 2.kat) } },
                KatalystStageDsl.Eq(
                    sections = listOf(
                        IgnitorDsl.EqSection.Bell(freq = IgnitorDsl.Constant(300.0), q = IgnitorDsl.Constant(0.8), db = IgnitorDsl.Constant(2.0)),
                    ),
                ) to KatalystStageDsl.Eq(),
            ),
            Triple("gain(0.8)", { b -> b.gain(0.8.kat) }, gain.copy(gain = k(0.8)) to gain),
            // The arithmetic stages: one typed member per word (an alias calls its main word's member).
            Triple("plus(0.375)", { b -> b.plus(0.375.kat) }, KatalystStageDsl.Plus(other = k(0.375)) to KatalystStageDsl.Abs),
            Triple("minus(0.375)", { b -> b.minus(0.375.kat) }, KatalystStageDsl.Minus(other = k(0.375)) to KatalystStageDsl.Abs),
            Triple("times(0.375)", { b -> b.times(0.375.kat) }, KatalystStageDsl.Times(other = k(0.375)) to KatalystStageDsl.Abs),
            Triple("div(0.375)", { b -> b.div(0.375.kat) }, KatalystStageDsl.Div(other = k(0.375)) to KatalystStageDsl.Abs),
            Triple("pow(0.375)", { b -> b.pow(0.375.kat) }, KatalystStageDsl.Pow(exp = k(0.375)) to KatalystStageDsl.Abs),
            Triple("min(0.375)", { b -> b.min(0.375.kat) }, KatalystStageDsl.Max(other = k(0.375)) to KatalystStageDsl.Abs),
            Triple("max(0.375)", { b -> b.max(0.375.kat) }, KatalystStageDsl.Min(other = k(0.375)) to KatalystStageDsl.Abs),
            Triple("neg()", { b -> b.neg() }, KatalystStageDsl.Times(other = k(-1.0)) to KatalystStageDsl.Abs),
            // the script's aliases, one-line typed delegates to their main word
            Triple("add(0.375)", { b -> b.add(0.375.kat) }, KatalystStageDsl.Plus(other = k(0.375)) to KatalystStageDsl.Abs),
            Triple("sub(0.375)", { b -> b.sub(0.375.kat) }, KatalystStageDsl.Minus(other = k(0.375)) to KatalystStageDsl.Abs),
            Triple("mul(0.375)", { b -> b.mul(0.375.kat) }, KatalystStageDsl.Times(other = k(0.375)) to KatalystStageDsl.Abs),
            Triple("power(0.375)", { b -> b.power(0.375.kat) }, KatalystStageDsl.Pow(exp = k(0.375)) to KatalystStageDsl.Abs),
            Triple("negate()", { b -> b.negate() }, KatalystStageDsl.Times(other = k(-1.0)) to KatalystStageDsl.Abs),
            Triple("abs()", { b -> b.abs() }, KatalystStageDsl.Abs to KatalystStageDsl.Times(other = k(-1.0))),
            Triple("append", { b -> b.append(KatalystStageDsl.Abs) }, KatalystStageDsl.Abs to KatalystStageDsl.Times(other = k(-1.0))),
        )

        rows.forEach { (call, kotlin, pair) ->
            val (expected, bare) = pair

            withClue("guard: $call moves off the bare stage") { expected shouldNotBe bare }
            withClue("Kotlin $call") { Kat { kotlin(it) } shouldBe KatalystDsl.of(expected) }

            if (call != "append") {
                withClue("script $call") { ks("Kat(k => k.$call)") shouldBe KatalystDsl.of(expected) }
            }
        }
    }

    "every typed modulator builder member writes its own field, from Kotlin and from the script" {
        val f = 0.7.kat

        listOf(
            Triple("Kat.sine(0.7, x => x.phase(0.25))", Kat.sine(f) { it.phase(0.25.kat) }, KatalystKnobDsl.Sine(freq = f).copy(phase = k(0.25))),
            Triple("Kat.saw(0.7, x => x.phase(0.25))", Kat.saw(f) { it.phase(0.25.kat) }, KatalystKnobDsl.Saw(freq = f).copy(phase = k(0.25))),
            Triple("Kat.ramp(0.7, x => x.phase(0.25))", Kat.ramp(f) { it.phase(0.25.kat) }, KatalystKnobDsl.Ramp(freq = f).copy(phase = k(0.25))),
            Triple("Kat.tri(0.7, x => x.phase(0.25))", Kat.tri(f) { it.phase(0.25.kat) }, KatalystKnobDsl.Tri(freq = f).copy(phase = k(0.25))),
            Triple("Kat.square(0.7, x => x.phase(0.25))", Kat.square(f) { it.phase(0.25.kat) }, KatalystKnobDsl.Square(freq = f).copy(phase = k(0.25))),
            Triple("Kat.square(0.7, x => x.duty(0.3))", Kat.square(f) { it.duty(0.3.kat) }, KatalystKnobDsl.Square(freq = f).copy(duty = k(0.3))),
        ).forEach { (code, kotlin, expected) ->
            withClue(code) {
                kotlin shouldBe expected
                ks(code) shouldBe expected
            }
        }

        // the modulators' default nodes, written out
        Kat.sine(f) shouldBe KatalystKnobDsl.Sine(freq = k(0.7), phase = k(0.0))
        Kat.saw(f) shouldBe KatalystKnobDsl.Saw(freq = k(0.7), phase = k(0.0))
        Kat.ramp(f) shouldBe KatalystKnobDsl.Ramp(freq = k(0.7), phase = k(0.0))
        Kat.tri(f) shouldBe KatalystKnobDsl.Tri(freq = k(0.7), phase = k(0.0))
        Kat.square(f) shouldBe KatalystKnobDsl.Square(freq = k(0.7), duty = k(0.5), phase = k(0.0))
        Kat.perlin(f) shouldBe KatalystKnobDsl.PerlinNoise(rate = k(0.7), octaves = k(1.0), persistence = k(0.5))
        Kat.berlin(f) shouldBe KatalystKnobDsl.BerlinNoise(rate = k(0.7), octaves = k(1.0), persistence = k(0.5))
    }

    "classic() lands at most once per builder, through both doors" {
        val gain = KatalystStageDsl.Gain(gain = k(0.5))

        Kat { it.classic().classic() } shouldBe KatalystDsl.classic
        ks("Kat(k => k.classic().classic())") shouldBe KatalystDsl.classic
        Kat { it.classic().gain(0.5.kat).classic() } shouldBe KatalystDsl(KatalystDsl.classic.stages + gain)
        ks("Kat(k => k.classic().gain(0.5).classic())") shouldBe KatalystDsl(KatalystDsl.classic.stages + gain)
    }

    "an eq section given a Kat expression is refused through both doors (Q49)" {
        shouldThrow<IllegalArgumentException> {
            Kat { it.eq { e -> e.band(freq = 300.kat, db = Kat.sine(1.kat)) } }
        }.message shouldBe "Katalyst eq band(db = ...) takes a number or a Kat.param; got a Kat expression"

        shouldThrow<KlangScriptTypeError> {
            ks("Kat(k => k.eq(e => e.band(freq = 300, db = Kat.sine(1))))")
        }.message shouldBe "a Katalyst eq section takes a number or a Kat.param; got a Kat expression"
    }
})
