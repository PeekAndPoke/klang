/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.Kat
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystKnobDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import io.peekandpoke.klang.audio_bridge.abs
import io.peekandpoke.klang.audio_bridge.add
import io.peekandpoke.klang.audio_bridge.div
import io.peekandpoke.klang.audio_bridge.kat
import io.peekandpoke.klang.audio_bridge.max
import io.peekandpoke.klang.audio_bridge.min
import io.peekandpoke.klang.audio_bridge.minus
import io.peekandpoke.klang.audio_bridge.mul
import io.peekandpoke.klang.audio_bridge.neg
import io.peekandpoke.klang.audio_bridge.negate
import io.peekandpoke.klang.audio_bridge.plus
import io.peekandpoke.klang.audio_bridge.pow
import io.peekandpoke.klang.audio_bridge.power
import io.peekandpoke.klang.audio_bridge.range
import io.peekandpoke.klang.audio_bridge.sub
import io.peekandpoke.klang.audio_bridge.times
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.KlangScriptTypeError
import io.peekandpoke.klang.script.runtime.NativeObjectValue

/**
 * The Katalyst knob expression doors (step 4 of KLANG-00302 (katalyst knob expressions), D4): every
 * door and alias, positional and named, on the script door and the Kotlin door, against the [KatalystKnobDsl] node
 * written out by hand, so a door wired to the wrong node cannot look right on both sides. The `min`/`max` crossing is
 * part of the table: `max` ("at most", a cap) builds [KatalystKnobDsl.Min]. The refusals: an Ignitor node as an
 * operand, an expression on an `eq` section (Q2, E2). `katp` refusing an expression is in sprudel's
 * `LangSlotSetterSpec`, where `katp` lives.
 */
class KlangScriptKatalystKnobExpressionDoorParitySpec : StringSpec({

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

    val fader = KatalystKnobDsl.Param("fader", 1.0)
    val faderScript = """Kat.param("fader", 1)"""

    /**
     * One door with an operand: its script name, the parameter's name, the typed Kotlin function (`audio_bridge`, the
     * main word's for an alias), the script variant called from Kotlin, and the node it must build.
     */
    data class Door(
        val name: String,
        val param: String,
        val kotlin: (KatalystKnobDsl, KatalystKnobDsl) -> KatalystKnobDsl,
        val script: (KatalystKnobDsl, Any) -> KatalystKnobDsl,
        val node: (KatalystKnobDsl, KatalystKnobDsl) -> KatalystKnobDsl,
    )

    val doors = listOf(
        Door("plus", "other", { k, v -> k.plus(v) }, { k, v -> k.scriptPlus(v) }, { a, b -> KatalystKnobDsl.Plus(left = a, right = b) }),
        Door("add", "other", { k, v -> k.add(v) }, { k, v -> k.scriptAdd(v) }, { a, b -> KatalystKnobDsl.Plus(left = a, right = b) }),
        Door("minus", "other", { k, v -> k.minus(v) }, { k, v -> k.scriptMinus(v) }, { a, b -> KatalystKnobDsl.Minus(left = a, right = b) }),
        Door("sub", "other", { k, v -> k.sub(v) }, { k, v -> k.scriptSub(v) }, { a, b -> KatalystKnobDsl.Minus(left = a, right = b) }),
        Door("times", "other", { k, v -> k.times(v) }, { k, v -> k.scriptTimes(v) }, { a, b -> KatalystKnobDsl.Times(left = a, right = b) }),
        Door("mul", "other", { k, v -> k.mul(v) }, { k, v -> k.scriptMul(v) }, { a, b -> KatalystKnobDsl.Times(left = a, right = b) }),
        Door("div", "other", { k, v -> k.div(v) }, { k, v -> k.scriptDiv(v) }, { a, b -> KatalystKnobDsl.Div(left = a, right = b) }),
        Door("pow", "exp", { k, v -> k.pow(v) }, { k, v -> k.scriptPow(v) }, { a, b -> KatalystKnobDsl.Pow(base = a, exp = b) }),
        Door("power", "exp", { k, v -> k.power(v) }, { k, v -> k.scriptPower(v) }, { a, b -> KatalystKnobDsl.Pow(base = a, exp = b) }),
        // The crossing: the doors are clamps, the nodes are the mathematical min and max.
        Door("min", "other", { k, v -> k.min(v) }, { k, v -> k.scriptMin(v) }, { a, b -> KatalystKnobDsl.Max(left = a, right = b) }),
        Door("max", "other", { k, v -> k.max(v) }, { k, v -> k.scriptMax(v) }, { a, b -> KatalystKnobDsl.Min(left = a, right = b) }),
    )

    "every operand door builds its own node, a number operand a Constant, positionally and named, on both doors" {
        for (door in doors) {
            val expected = door.node(fader, c(0.375))

            withClue("${door.name}, positional") { kx("$faderScript.${door.name}(0.375)") shouldBe expected }
            withClue("${door.name}, named") { kx("$faderScript.${door.name}(${door.param} = 0.375)") shouldBe expected }
            withClue("${door.name}, Kotlin") { door.kotlin(fader, 0.375.kat) shouldBe expected }
            withClue("${door.name}, the script variant from Kotlin") { door.script(fader, 0.375) shouldBe expected }
        }
    }

    "an operand may be a Katalyst param or another expression, on every operand door and both doors" {
        val trim = KatalystKnobDsl.Param("trim", 0.8)
        val nested = KatalystKnobDsl.Neg(inner = trim)

        for (door in doors) {
            withClue("${door.name}, a param operand") {
                kx("""$faderScript.${door.name}(Kat.param("trim", 0.8))""") shouldBe door.node(fader, trim)
                door.kotlin(fader, Kat.param("trim", 0.8)) shouldBe door.node(fader, trim)
                door.script(fader, Kat.param("trim", 0.8)) shouldBe door.node(fader, trim)
            }
            withClue("${door.name}, an expression operand") {
                kx("""$faderScript.${door.name}(Kat.param("trim", 0.8).neg())""") shouldBe door.node(fader, nested)
                door.kotlin(fader, Kat.param("trim", 0.8).neg()) shouldBe door.node(fader, nested)
                door.script(fader, Kat.param("trim", 0.8).neg()) shouldBe door.node(fader, nested)
            }
        }
    }

    "the min and max doors are clamps: min builds the mathematical Max, max the Min" {
        kx("$faderScript.min(0.2)") shouldBe KatalystKnobDsl.Max(left = fader, right = c(0.2))
        kx("$faderScript.max(0.9)") shouldBe KatalystKnobDsl.Min(left = fader, right = c(0.9))
        fader.min(0.2.kat) shouldBe KatalystKnobDsl.Max(left = fader, right = c(0.2))
        fader.max(0.9.kat) shouldBe KatalystKnobDsl.Min(left = fader, right = c(0.9))
        fader.scriptMin(0.2) shouldBe KatalystKnobDsl.Max(left = fader, right = c(0.2))
        fader.scriptMax(0.9) shouldBe KatalystKnobDsl.Min(left = fader, right = c(0.9))
    }

    "neg, negate and abs wrap the knob, on both doors" {
        listOf(
            "neg" to KatalystKnobDsl.Neg(inner = fader),
            "negate" to KatalystKnobDsl.Neg(inner = fader),
            "abs" to KatalystKnobDsl.Abs(inner = fader),
        ).forEach { (name, expected) ->
            withClue(name) { kx("$faderScript.$name()") shouldBe expected }
        }

        fader.neg() shouldBe KatalystKnobDsl.Neg(inner = fader)
        fader.negate() shouldBe KatalystKnobDsl.Neg(inner = fader)
        fader.abs() shouldBe KatalystKnobDsl.Abs(inner = fader)
        fader.scriptNeg() shouldBe KatalystKnobDsl.Neg(inner = fader)
        fader.scriptNegate() shouldBe KatalystKnobDsl.Neg(inner = fader)
        fader.scriptAbs() shouldBe KatalystKnobDsl.Abs(inner = fader)
    }

    "range(from, to), positional and named, numbers and params, on both doors" {
        val expected = KatalystKnobDsl.Range(inner = fader, from = c(400.0), to = c(1600.0))

        kx("$faderScript.range(400, 1600)") shouldBe expected
        kx("$faderScript.range(from = 400, to = 1600)") shouldBe expected
        kx("$faderScript.range(to = 1600, from = 400)") shouldBe expected
        fader.range(from = 400.kat, to = 1600.kat) shouldBe expected
        fader.range(to = 1600.0.kat, from = 400.0.kat) shouldBe expected
        fader.scriptRange(400, 1600) shouldBe expected
        fader.scriptRange(to = 1600.0, from = 400.0) shouldBe expected

        val withParams = KatalystKnobDsl.Range(inner = fader, from = KatalystKnobDsl.Param("lo", 200.0), to = c(800.0))

        kx("""$faderScript.range(Kat.param("lo", 200), 800)""") shouldBe withParams
        fader.range(from = Kat.param("lo", 200.0), to = 800.kat) shouldBe withParams
        fader.scriptRange(Kat.param("lo", 200.0), 800) shouldBe withParams
    }

    "an expression is a stage knob like a param, on both doors" {
        val gain = KatalystKnobDsl.Times(left = fader, right = c(0.5))

        ks("""Katalyst(k => k.gain(Kat.param("fader", 1).times(0.5)))""") shouldBe
                KatalystDsl.of(KatalystStageDsl.Gain(gain = gain))
        Kat.build { it.gain(Kat.param("fader", 1.0).times(0.5.kat)) } shouldBe
                KatalystDsl.of(KatalystStageDsl.Gain(gain = gain))

        // A door parameter, a builder knob and an arithmetic stage's operand.
        val beat = KatalystKnobDsl.Div(left = KatalystKnobDsl.Param("beat", 0.5), right = c(4.0))

        val cap = KatalystKnobDsl.Abs(inner = KatalystKnobDsl.Param("c", 2.0))
        val delay = "Katalyst(k => k.delay(time = Kat.param(\"beat\", 0.5).div(4), " +
                "configure = d => d.cap(Kat.param(\"c\", 2).abs())))"

        ks(delay) shouldBe KatalystDsl.of(KatalystStageDsl.Delay(time = beat, cap = cap))
        ks("""Katalyst(k => k.times(Kat.slot.gain.gain.neg()))""") shouldBe
                KatalystDsl.of(KatalystStageDsl.Times(other = KatalystKnobDsl.Neg(inner = KatalystDsl.Slots.gain.gain)))
    }

    "an Ignitor node as an operand is the stage knob's type error, on both doors" {
        val sound = "a Katalyst knob takes a number, a Kat.param or a Kat expression; got a sound"
        val param = "an Ignitor param in a Katalyst chain; use Kat.param"

        listOf(
            "$faderScript.times(Ignitor.sine(1))" to sound,
            "$faderScript.range(Ignitor.sine(1), 2)" to sound,
            """$faderScript.plus(Ignitor.param("a", 1))""" to param,
            """$faderScript.min(Ign.slot.pregain)""" to param,
        ).forEach { (script, message) ->
            withClue(script) { shouldThrow<KlangScriptTypeError> { engine().execute(script) }.message shouldBe message }
        }

        // The typed functions refuse an Ignitor node at compile time; the script variants convert and refuse.
        shouldThrow<KlangScriptTypeError> { fader.scriptTimes(IgnitorDsl.Sine()) }.message shouldBe sound
        shouldThrow<KlangScriptTypeError> { fader.scriptPow(IgnitorDsl.Param("a", 1.0)) }.message shouldBe param
        shouldThrow<KlangScriptTypeError> { fader.scriptPlus("x") }.message shouldBe
                "a Katalyst knob takes a number, a Kat.param or a Kat expression; got a string"
    }

    "an eq section takes a number or a Kat.param only: an expression there is a type error (Q2, E2)" {
        val message = "a Katalyst eq section takes a number or a Kat.param; got a Kat expression"

        listOf(
            """Katalyst(k => k.eq(e => e.band(freq = Kat.param("f", 300).times(2))))""",
            """Katalyst(k => k.eq(e => e.band(freq = 300, db = Kat.param("d", 1).neg())))""",
            """Katalyst(k => k.eq(e => e.tap(freq = 300, gain = Kat.param("g", 1).range(0, 2))))""",
        ).forEach { script ->
            withClue(script) { shouldThrow<KlangScriptTypeError> { ks(script) }.message shouldBe message }
        }

        // The Kotlin door's typed eq builder refuses it at build time (Q49, the typed-doors rollout S3a).
        shouldThrow<IllegalArgumentException> {
            Kat.build { it.eq { e -> e.band(freq = Kat.param("f", 300.0).times(2.kat)) } }
        }.message shouldBe "Katalyst eq band(freq = ...) takes a number or a Kat.param; got a Kat expression"
    }
})
