/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.Kat
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystKnobDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import io.peekandpoke.klang.audio_bridge.VowelBands
import io.peekandpoke.klang.audio_bridge.div
import io.peekandpoke.klang.audio_bridge.kat
import io.peekandpoke.klang.audio_bridge.range
import io.peekandpoke.klang.audio_bridge.times
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.KlangScriptTypeError
import io.peekandpoke.klang.script.runtime.NativeObjectValue

/**
 * The Katalyst modulator doors (step 5b of `docs/tasks-archive/2026-10/20261012-katalyst-knob-expressions.md`, D3): `Kat.sine`,
 * `Kat.saw`, `Kat.ramp`, `Kat.tri`, `Kat.square`, `Kat.perlin`, `Kat.berlin`, on the script door and the Kotlin door
 * (the same object, `KlangScriptKatalyst`), positional and named, against the [KatalystKnobDsl] node written out by
 * hand. The Ignitor's door shape, with the recorded differences: `freq` and the noises' `rate` required (Q10), the
 * builders keep only `phase` and the square's `duty` (Q11). Then a modulator inside the knob arithmetic and on a stage,
 * and the refusals: an Ignitor node as an operand and a modulator on an `eq` section (Q2). A `body`'s or `vowel`'s
 * `wet` and `floor` take a modulator since step 5c (the in-place blend, Q8 (a)); 5b refused it there.
 */
class KlangScriptKatalystModulatorDoorParitySpec : StringSpec({

    fun engine() = klangScript().also { it.execute("""import * from "stdlib"""") }

    /** The knob a script expression evaluates to. */
    fun kx(code: String): KatalystKnobDsl {
        val result = engine().execute(code)
        result.shouldBeInstanceOf<NativeObjectValue<*>>()

        return result.value.shouldBeInstanceOf<KatalystKnobDsl>()
    }

    fun ks(code: String): KatalystDsl {
        val result = engine().execute(code)
        result.shouldBeInstanceOf<NativeObjectValue<*>>()

        return result.value.shouldBeInstanceOf<KatalystDsl>()
    }

    fun c(value: Double): KatalystKnobDsl = KatalystKnobDsl.Constant(value)

    /**
     * One periodic door: its name, the typed Kotlin door (`Kat` in `audio_bridge`) with and without a phase, the script
     * object called from Kotlin with its script phase variant, and the node it builds.
     */
    data class Periodic(
        val name: String,
        val kotlin: (KatalystKnobDsl) -> KatalystKnobDsl,
        val kotlinPhased: (KatalystKnobDsl, KatalystKnobDsl) -> KatalystKnobDsl,
        val scriptPhased: (Any, Any) -> KatalystKnobDsl,
        val node: (KatalystKnobDsl, KatalystKnobDsl) -> KatalystKnobDsl,
    )

    val periodic = listOf(
        Periodic(
            name = "sine",
            kotlin = { f -> Kat.sine(f) },
            kotlinPhased = { f, p -> Kat.sine(f) { it.phase(p) } },
            scriptPhased = { f, p -> KlangScriptKatalyst.sine(f) { it.scriptPhase(p) } },
            node = { f, p -> KatalystKnobDsl.Sine(freq = f, phase = p) },
        ),
        Periodic(
            name = "saw",
            kotlin = { f -> Kat.saw(f) },
            kotlinPhased = { f, p -> Kat.saw(f) { it.phase(p) } },
            scriptPhased = { f, p -> KlangScriptKatalyst.saw(f) { it.scriptPhase(p) } },
            node = { f, p -> KatalystKnobDsl.Saw(freq = f, phase = p) },
        ),
        Periodic(
            name = "ramp",
            kotlin = { f -> Kat.ramp(f) },
            kotlinPhased = { f, p -> Kat.ramp(f) { it.phase(p) } },
            scriptPhased = { f, p -> KlangScriptKatalyst.ramp(f) { it.scriptPhase(p) } },
            node = { f, p -> KatalystKnobDsl.Ramp(freq = f, phase = p) },
        ),
        Periodic(
            name = "tri",
            kotlin = { f -> Kat.tri(f) },
            kotlinPhased = { f, p -> Kat.tri(f) { it.phase(p) } },
            scriptPhased = { f, p -> KlangScriptKatalyst.tri(f) { it.scriptPhase(p) } },
            node = { f, p -> KatalystKnobDsl.Tri(freq = f, phase = p) },
        ),
        Periodic(
            name = "square",
            kotlin = { f -> Kat.square(f) },
            kotlinPhased = { f, p -> Kat.square(f) { it.phase(p) } },
            scriptPhased = { f, p -> KlangScriptKatalyst.square(f) { it.scriptPhase(p) } },
            node = { f, p -> KatalystKnobDsl.Square(freq = f, phase = p) },
        ),
    )

    "every periodic door builds its own node: freq positional and named, phase on the builder, on both doors" {
        for (door in periodic) {
            val bare = door.node(c(2.5), c(0.0))
            val phased = door.node(c(2.5), c(0.25))

            withClue("${door.name}, positional") { kx("Kat.${door.name}(2.5)") shouldBe bare }
            withClue("${door.name}, named") { kx("Kat.${door.name}(freq = 2.5)") shouldBe bare }
            withClue("${door.name}, the long name") { kx("Katalyst.${door.name}(2.5)") shouldBe bare }
            withClue("${door.name}, phase") { kx("Kat.${door.name}(2.5, x => x.phase(0.25))") shouldBe phased }
            withClue("${door.name}, named configure") {
                kx("Kat.${door.name}(freq = 2.5, configure = x => x.phase(0.25))") shouldBe phased
            }
            withClue("${door.name}, Kotlin") {
                door.kotlin(2.5.kat) shouldBe bare
                door.kotlinPhased(2.5.kat, 0.25.kat) shouldBe phased
                door.scriptPhased(2.5, 0.25) shouldBe phased
            }
        }
    }

    "the square's builder also takes duty, and every builder knob a param or an expression, on both doors" {
        val duty = KatalystKnobDsl.Param("pw", 0.3)
        val phase = KatalystKnobDsl.Neg(inner = KatalystKnobDsl.Param("q", 0.0))

        kx("""Kat.square(1, x => x.duty(Kat.param("pw", 0.3)).phase(Kat.param("q", 0).neg()))""") shouldBe
                KatalystKnobDsl.Square(freq = c(1.0), duty = duty, phase = phase)
        kx("Kat.square(1, x => x.duty(0.25))") shouldBe KatalystKnobDsl.Square(freq = c(1.0), duty = c(0.25))
        kx("Kat.square(1, x => x.duty(width = 0.25))") shouldBe KatalystKnobDsl.Square(freq = c(1.0), duty = c(0.25))
        Kat.square(1.kat) { it.duty(width = 0.25.kat) } shouldBe KatalystKnobDsl.Square(freq = c(1.0), duty = c(0.25))
        Kat.square(1.kat) { it.duty(Kat.param("pw", 0.3)) } shouldBe KatalystKnobDsl.Square(freq = c(1.0), duty = duty)
        KlangScriptKatalyst.square(1) { it.scriptDuty(width = 0.25) } shouldBe
                KatalystKnobDsl.Square(freq = c(1.0), duty = c(0.25))
        kx("""Kat.sine(1, x => x.phase(Kat.param("q", 0).neg()))""") shouldBe
                KatalystKnobDsl.Sine(freq = c(1.0), phase = phase)
    }

    "freq takes a Kat.param or a Kat expression, and so does the noises' rate" {
        val beat = KatalystKnobDsl.Div(left = KatalystKnobDsl.Param("bpm", 120.0), right = c(60.0))

        kx("""Kat.sine(Kat.param("bpm", 120).div(60))""") shouldBe KatalystKnobDsl.Sine(freq = beat)
        Kat.sine(Kat.param("bpm", 120.0).div(60.kat)) shouldBe KatalystKnobDsl.Sine(freq = beat)
        KlangScriptKatalyst.sine(Kat.param("bpm", 120.0).div(60.kat)) shouldBe KatalystKnobDsl.Sine(freq = beat)
        kx("""Kat.perlin(Kat.param("drift", 0.002))""") shouldBe
                KatalystKnobDsl.PerlinNoise(rate = KatalystKnobDsl.Param("drift", 0.002))
    }

    "perlin and berlin: rate required, octaves and persistence defaulted, positional and named, on both doors" {
        val noises = listOf(
            Triple(
                "perlin",
                { r: KatalystKnobDsl, o: KatalystKnobDsl, p: KatalystKnobDsl -> Kat.perlin(rate = r, octaves = o, persistence = p) },
                { r: KatalystKnobDsl, o: KatalystKnobDsl, p: KatalystKnobDsl ->
                    KatalystKnobDsl.PerlinNoise(rate = r, octaves = o, persistence = p)
                },
            ),
            Triple(
                "berlin",
                { r: KatalystKnobDsl, o: KatalystKnobDsl, p: KatalystKnobDsl -> Kat.berlin(rate = r, octaves = o, persistence = p) },
                { r: KatalystKnobDsl, o: KatalystKnobDsl, p: KatalystKnobDsl ->
                    KatalystKnobDsl.BerlinNoise(rate = r, octaves = o, persistence = p)
                },
            ),
        )

        for ((name, kotlin, node) in noises) {
            val defaults = node(c(0.004), c(1.0), c(0.5))
            val full = node(c(0.004), c(3.0), c(0.6))

            withClue("$name, rate only") { kx("Kat.$name(0.004)") shouldBe defaults }
            withClue("$name, positional") { kx("Kat.$name(0.004, 3, 0.6)") shouldBe full }
            withClue("$name, named") { kx("Kat.$name(persistence = 0.6, rate = 0.004, octaves = 3)") shouldBe full }
            withClue("$name, Kotlin") { kotlin(0.004.kat, 3.kat, 0.6.kat) shouldBe full }
        }

        Kat.perlin(0.004.kat) shouldBe KatalystKnobDsl.PerlinNoise(rate = c(0.004))
        Kat.berlin(0.004.kat) shouldBe KatalystKnobDsl.BerlinNoise(rate = c(0.004))
        KlangScriptKatalyst.perlin(0.004) shouldBe KatalystKnobDsl.PerlinNoise(rate = c(0.004))
        KlangScriptKatalyst.berlin(0.004) shouldBe KatalystKnobDsl.BerlinNoise(rate = c(0.004))
    }

    "freq and rate are required: a call without one is a script error" {
        for (door in listOf("sine", "saw", "ramp", "tri", "square", "perlin", "berlin")) {
            withClue("Kat.$door()") { shouldThrowAny { engine().execute("Kat.$door()") } }
        }
    }

    "a modulator inside the knob arithmetic and on a stage, on both doors" {
        val sweep = KatalystKnobDsl.Range(inner = KatalystKnobDsl.Sine(freq = c(0.1)), from = c(400.0), to = c(1600.0))

        kx("Kat.sine(0.1).range(400, 1600)") shouldBe sweep
        Kat.sine(0.1.kat).range(from = 400.kat, to = 1600.kat) shouldBe sweep

        val phaser = KatalystDsl.of(KatalystStageDsl.Phaser(wet = c(0.6), center = sweep))

        ks("Katalyst(k => k.phaser(wet = 0.6, center = Kat.sine(0.1).range(400, 1600)))") shouldBe phaser
        Kat.build {
            it.phaser(wet = 0.6.kat, center = Kat.sine(0.1.kat).range(from = 400.kat, to = 1600.kat))
        } shouldBe phaser

        // A slot times a modulator: the depth from the pattern, the motion from the chain.
        val tremolo = KatalystKnobDsl.Times(
            left = KatalystKnobDsl.Param("depth", 1.0),
            right = KatalystKnobDsl.Square(freq = c(2.0)),
        )

        kx("""Kat.param("depth", 1).times(Kat.square(2))""") shouldBe tremolo
        Kat.param("depth", 1.0).times(Kat.square(2.kat)) shouldBe tremolo

        // An arithmetic stage's operand, and the body's index (which a modulator may move: a bank swap per change).
        ks("Katalyst(k => k.div(Kat.tri(0.5).range(1, 2)))") shouldBe KatalystDsl.of(
            KatalystStageDsl.Div(
                other = KatalystKnobDsl.Range(inner = KatalystKnobDsl.Tri(freq = c(0.5)), from = c(1.0), to = c(2.0)),
            ),
        )
        ks("Katalyst(k => k.body(0.5, Kat.square(0.25).range(1, 2)))") shouldBe KatalystDsl.of(
            KatalystStageDsl.Body(
                wet = c(0.5),
                material = KatalystKnobDsl.Range(
                    inner = KatalystKnobDsl.Square(freq = c(0.25)),
                    from = c(1.0),
                    to = c(2.0),
                ),
            ),
        )
    }

    "an Ignitor node as freq or rate is the stage knob's type error, and a modulator is no Ignitor operand" {
        val sound = "a Katalyst knob takes a number, a Kat.param or a Kat expression; got a sound"

        shouldThrow<KlangScriptTypeError> { engine().execute("Kat.sine(Ignitor.sine(1))") }.message shouldBe sound
        shouldThrow<KlangScriptTypeError> { KlangScriptKatalyst.perlin(IgnitorDsl.Sine()) }.message shouldBe sound
        shouldThrow<KlangScriptTypeError> {
            KlangScriptKatalyst.square(1) { it.scriptDuty(IgnitorDsl.Sine()) }
        }.message shouldBe sound
        // The other way round: a Katalyst modulator on an Ignitor is refused there.
        shouldThrowAny { engine().execute("Ignitor.sine(440).mul(Kat.sine(1))") }
    }

    "an eq section refuses a modulator, as it refuses every expression (Q2, E2)" {
        val message = "a Katalyst eq section takes a number or a Kat.param; got a Kat expression"

        shouldThrow<KlangScriptTypeError> {
            ks("Katalyst(k => k.eq(e => e.band(freq = Kat.sine(0.2).range(200, 800))))")
        }.message shouldBe message
        // The Kotlin door's typed eq builder refuses it at build time (Q49, the typed-doors rollout S3a).
        shouldThrow<IllegalArgumentException> {
            Kat.build { it.eq { e -> e.band(freq = Kat.sine(0.2.kat)) } }
        }.message shouldBe "Katalyst eq band(freq = ...) takes a number or a Kat.param; got a Kat expression"
    }

    "a body's and a vowel's wet and floor take a modulator, alone and inside an expression, on both doors (5c)" {
        // 5b refused all four (a moving wet re-installed the bank); step 5c's in-place blend lifted the refusal.
        val sine = KatalystKnobDsl.Sine(freq = c(1.0))
        val tri = KatalystKnobDsl.Range(inner = KatalystKnobDsl.Tri(freq = c(0.5)), from = c(0.2), to = c(0.4))
        val product = KatalystKnobDsl.Times(
            left = KatalystKnobDsl.Param(name = "w", default = 0.5),
            right = KatalystKnobDsl.PerlinNoise(rate = c(0.01)),
        )
        val bareBody = KatalystStageDsl.Body()
        val bareVowel = KatalystStageDsl.Vowel()

        withClue("body wet, script") {
            ks("Katalyst(k => k.body(Kat.sine(1)))") shouldBe KatalystDsl.of(bareBody.copy(wet = sine))
        }
        withClue("body wet inside an expression, script") {
            ks("""Katalyst(k => k.body(Kat.param("w", 0.5).times(Kat.perlin(0.01))))""") shouldBe
                    KatalystDsl.of(bareBody.copy(wet = product))
        }
        withClue("body floor, script") {
            ks("Katalyst(k => k.body(wet = 0.5, configure = x => x.floor(Kat.tri(0.5).range(0.2, 0.4))))") shouldBe
                    KatalystDsl.of(bareBody.copy(wet = c(0.5), floor = tri))
        }
        withClue("vowel wet, script") {
            ks("Katalyst(k => k.vowel(Kat.sine(1)))") shouldBe KatalystDsl.of(bareVowel.copy(wet = sine))
        }
        withClue("vowel wet inside an expression, script") {
            ks("""Katalyst(k => k.vowel(Kat.param("w", 0.5).times(Kat.perlin(0.01))))""") shouldBe
                    KatalystDsl.of(bareVowel.copy(wet = product))
        }
        withClue("vowel floor, script") {
            ks("Katalyst(k => k.vowel(wet = 0.5, configure = x => x.floor(Kat.tri(0.5).range(0.2, 0.4))))") shouldBe
                    KatalystDsl.of(bareVowel.copy(wet = c(0.5), floor = tri))
        }

        val kotlinTri = Kat.tri(0.5.kat).range(from = 0.2.kat, to = 0.4.kat)

        withClue("body wet and floor, Kotlin") {
            Kat.build {
                it.body(wet = Kat.sine(1.kat)) { b -> b.floor(kotlinTri) }
            } shouldBe KatalystDsl.of(bareBody.copy(wet = sine, floor = tri))
        }
        withClue("vowel wet and floor, Kotlin") {
            Kat.build {
                it.vowel(wet = Kat.sine(1.kat), vowel = "a") { v -> v.floor(kotlinTri) }
            } shouldBe KatalystDsl.of(
                KatalystStageDsl.Vowel(vowel = c(VowelBands.indexOf("a")), wet = sine, floor = tri)
            )
        }
    }

    "a configure lambda that returns the wrong thing names the door" {
        shouldThrow<KlangScriptTypeError> {
            engine().execute("Kat.sine(1, x => 3)")
        }.message shouldContain "Katalyst.sine"
    }
})
