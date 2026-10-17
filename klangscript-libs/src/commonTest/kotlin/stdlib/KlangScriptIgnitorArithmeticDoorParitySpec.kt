/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.abs
import io.peekandpoke.klang.audio_bridge.add
import io.peekandpoke.klang.audio_bridge.ceil
import io.peekandpoke.klang.audio_bridge.clamp
import io.peekandpoke.klang.audio_bridge.constants.RANGEX_FLOOR
import io.peekandpoke.klang.audio_bridge.div
import io.peekandpoke.klang.audio_bridge.exp
import io.peekandpoke.klang.audio_bridge.floor
import io.peekandpoke.klang.audio_bridge.frac
import io.peekandpoke.klang.audio_bridge.ign
import io.peekandpoke.klang.audio_bridge.lerp
import io.peekandpoke.klang.audio_bridge.log
import io.peekandpoke.klang.audio_bridge.max
import io.peekandpoke.klang.audio_bridge.min
import io.peekandpoke.klang.audio_bridge.minus
import io.peekandpoke.klang.audio_bridge.mix
import io.peekandpoke.klang.audio_bridge.mod
import io.peekandpoke.klang.audio_bridge.mul
import io.peekandpoke.klang.audio_bridge.neg
import io.peekandpoke.klang.audio_bridge.negate
import io.peekandpoke.klang.audio_bridge.plus
import io.peekandpoke.klang.audio_bridge.pow
import io.peekandpoke.klang.audio_bridge.power
import io.peekandpoke.klang.audio_bridge.pregain
import io.peekandpoke.klang.audio_bridge.range
import io.peekandpoke.klang.audio_bridge.rangex
import io.peekandpoke.klang.audio_bridge.recip
import io.peekandpoke.klang.audio_bridge.reciprocal
import io.peekandpoke.klang.audio_bridge.rem
import io.peekandpoke.klang.audio_bridge.round
import io.peekandpoke.klang.audio_bridge.select
import io.peekandpoke.klang.audio_bridge.sign
import io.peekandpoke.klang.audio_bridge.sq
import io.peekandpoke.klang.audio_bridge.sqrt
import io.peekandpoke.klang.audio_bridge.sub
import io.peekandpoke.klang.audio_bridge.tanh
import io.peekandpoke.klang.audio_bridge.times
import io.peekandpoke.klang.audio_bridge.unaryMinus
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.KlangScriptTypeError
import io.peekandpoke.klang.script.runtime.toObjectOrNull

/**
 * The Ignitor arithmetic on both doors (typed-doors rollout S4, Q47): ONE typed Kotlin function per word, every
 * operand an `IgnitorDsl` (`saw * 0.5.ign`), the `Double` twins and operators gone; the script door keeps taking a
 * number or a signal (`x.mul(0.5)`), converts it and calls the typed function, its aliases included.
 *
 * Each row writes the node out by hand with distinct values, then checks the Kotlin call and every script spelling
 * against it: a typed function building the wrong node goes red here, and so does a script variant that skips the
 * conversion (it would hand a raw number to the typed function, an internal error).
 *
 * `min` and `max` are clamps on every door (`CLAUDE.md`, guardrail): `x.max(1)` is "x, at most 1" and builds the
 * `Min` node, on both doors. Do not "correct" the crossing; the rows go red if anyone does.
 */
class KlangScriptIgnitorArithmeticDoorParitySpec : StringSpec({

    fun ignitor(code: String): IgnitorDsl {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        return engine.execute(code).toObjectOrNull<IgnitorDsl>()!!
    }

    fun c(v: Double) = IgnitorDsl.Constant(v)

    val sine = ignitor("Ign.sine(2)")
    val saw = ignitor("Ign.saw(3)")

    // ── Two operands ─────────────────────────────────────────────────────────

    "plus: x + 0.5.ign in Kotlin, x.plus(0.5) and x.add(0.5) in script" {
        val written = IgnitorDsl.Plus(left = sine, right = c(0.5))

        sine + 0.5.ign shouldBe written
        sine.plus(0.5.ign) shouldBe written
        sine.add(0.5.ign) shouldBe written
        ignitor("Ign.sine(2).plus(0.5)") shouldBe written
        ignitor("Ign.sine(2).add(0.5)") shouldBe written
    }

    "times: x * 0.5.ign in Kotlin, x.times(0.5) and x.mul(0.5) in script" {
        val written = IgnitorDsl.Times(left = sine, right = c(0.25))

        sine * 0.25.ign shouldBe written
        sine.times(0.25.ign) shouldBe written
        ignitor("Ign.sine(2).times(0.25)") shouldBe written
        ignitor("Ign.sine(2).mul(0.25)") shouldBe written
    }

    "mul: x.mul(0.5.ign) in Kotlin, the same node as times" {
        val written = IgnitorDsl.Times(left = sine, right = c(0.75))

        sine.mul(0.75.ign) shouldBe written
        ignitor("Ign.sine(2).mul(0.75)") shouldBe written
    }

    "minus: x.minus(0.5.ign) in Kotlin, x.minus(0.5) and x.sub(0.5) in script" {
        val written = IgnitorDsl.Minus(left = sine, right = c(0.125))

        sine.minus(0.125.ign) shouldBe written
        sine.sub(0.125.ign) shouldBe written
        ignitor("Ign.sine(2).minus(0.125)") shouldBe written
        ignitor("Ign.sine(2).sub(0.125)") shouldBe written
    }

    "div: x.div(4.ign) in Kotlin, x.div(4) in script" {
        val written = IgnitorDsl.Div(left = sine, right = c(4.0))

        sine.div(4.ign) shouldBe written
        ignitor("Ign.sine(2).div(4)") shouldBe written
    }

    "pow: x.pow(3.ign) in Kotlin, x.pow(3) and x.power(3) in script" {
        val written = IgnitorDsl.Pow(base = sine, exp = c(3.0))

        sine.pow(3.ign) shouldBe written
        sine.power(3.ign) shouldBe written
        ignitor("Ign.sine(2).pow(3)") shouldBe written
        ignitor("Ign.sine(2).power(3)") shouldBe written
    }

    "mod: x.mod(0.4.ign) in Kotlin, x.mod(0.4) and x.rem(0.4) in script" {
        val written = IgnitorDsl.Mod(left = sine, right = c(0.4))

        sine.mod(0.4.ign) shouldBe written
        sine.rem(0.4.ign) shouldBe written
        ignitor("Ign.sine(2).mod(0.4)") shouldBe written
        ignitor("Ign.sine(2).rem(0.4)") shouldBe written
    }

    "min: x.min(0) is a floor on both doors and builds the Max node (the guardrail crossing)" {
        val written = IgnitorDsl.Max(left = sine, right = c(-0.2))

        sine.min((-0.2).ign) shouldBe written
        sine.min(-0.2.ign) shouldBe written
        ignitor("Ign.sine(2).min(-0.2)") shouldBe written
    }

    "max: x.max(0.5) is a cap on both doors and builds the Min node (the guardrail crossing)" {
        val written = IgnitorDsl.Min(left = sine, right = c(0.6))

        sine.max(0.6.ign) shouldBe written
        ignitor("Ign.sine(2).max(0.6)") shouldBe written
    }

    "min and max keep the receiver on the left, with a signal on both doors" {
        sine.min(saw) shouldBe IgnitorDsl.Max(left = sine, right = saw)
        sine.max(saw) shouldBe IgnitorDsl.Min(left = sine, right = saw)
        ignitor("Ign.sine(2).min(Ign.saw(3))") shouldBe IgnitorDsl.Max(left = sine, right = saw)
        ignitor("Ign.sine(2).max(Ign.saw(3))") shouldBe IgnitorDsl.Min(left = sine, right = saw)
    }

    // ── Three operands ───────────────────────────────────────────────────────

    "clamp: x.clamp(lo = -0.5.ign, hi = 0.75.ign) in Kotlin, positional and by name in script" {
        val written = IgnitorDsl.Clamp(inner = sine, lo = c(-0.5), hi = c(0.75))

        sine.clamp(lo = -0.5.ign, hi = 0.75.ign) shouldBe written
        ignitor("Ign.sine(2).clamp(-0.5, 0.75)") shouldBe written
        ignitor("Ign.sine(2).clamp(hi = 0.75, lo = -0.5)") shouldBe written
    }

    "lerp: toward a signal and toward a constant, x.lerp(other, t) in Kotlin, lerp and mix in script" {
        val towardSignal = IgnitorDsl.Lerp(left = sine, right = saw, t = c(0.3))
        val towardConstant = IgnitorDsl.Lerp(left = sine, right = c(0.5), t = c(0.35))

        sine.lerp(other = saw, t = 0.3.ign) shouldBe towardSignal
        sine.mix(other = saw, t = 0.3.ign) shouldBe towardSignal
        sine.lerp(other = 0.5.ign, t = 0.35.ign) shouldBe towardConstant
        ignitor("Ign.sine(2).lerp(Ign.saw(3), 0.3)") shouldBe towardSignal
        ignitor("Ign.sine(2).mix(Ign.saw(3), 0.3)") shouldBe towardSignal
        ignitor("Ign.sine(2).lerp(0.5, 0.35)") shouldBe towardConstant
        ignitor("Ign.sine(2).lerp(t = 0.35, other = 0.5)") shouldBe towardConstant
    }

    "range: x.range(from = 200.ign, to = 800.ign) in Kotlin, x.range(200, 800) in script" {
        val written = IgnitorDsl.Range(inner = sine, from = c(200.0), to = c(800.0))

        sine.range(from = 200.ign, to = 800.ign) shouldBe written
        ignitor("Ign.sine(2).range(200, 800)") shouldBe written
    }

    "rangex: the composed tree, written out, on both doors" {
        val floor = c(RANGEX_FLOOR)
        val written = IgnitorDsl.Exp(
            inner = IgnitorDsl.Range(
                inner = sine,
                from = IgnitorDsl.Log(inner = IgnitorDsl.Max(left = c(200.0), right = floor)),
                to = IgnitorDsl.Log(inner = IgnitorDsl.Max(left = c(3200.0), right = floor)),
            ),
        )

        sine.rangex(from = 200.ign, to = 3200.ign) shouldBe written
        ignitor("Ign.sine(2).rangex(200, 3200)") shouldBe written
    }

    "select: x.select(whenTrue = 1.ign, whenFalse = -1.ign) in Kotlin, x.select(1, -1) in script" {
        val written = IgnitorDsl.Select(cond = sine, whenTrue = c(1.0), whenFalse = c(-1.0))

        sine.select(whenTrue = 1.ign, whenFalse = -1.ign) shouldBe written
        ignitor("Ign.sine(2).select(1, -1)") shouldBe written
    }

    // ── One operand ──────────────────────────────────────────────────────────

    "the one-operand words build their node on both doors, aliases included" {
        val rows = listOf(
            Triple("neg", sine.neg(), IgnitorDsl.Neg(inner = sine)),
            Triple("negate", sine.negate(), IgnitorDsl.Neg(inner = sine)),
            Triple("abs", sine.abs(), IgnitorDsl.Abs(inner = sine)),
            Triple("exp", sine.exp(), IgnitorDsl.Exp(inner = sine)),
            Triple("log", sine.log(), IgnitorDsl.Log(inner = sine)),
            Triple("sqrt", sine.sqrt(), IgnitorDsl.Sqrt(inner = sine)),
            Triple("sign", sine.sign(), IgnitorDsl.Sign(inner = sine)),
            Triple("tanh", sine.tanh(), IgnitorDsl.Tanh(inner = sine)),
            Triple("floor", sine.floor(), IgnitorDsl.Floor(inner = sine)),
            Triple("ceil", sine.ceil(), IgnitorDsl.Ceil(inner = sine)),
            Triple("round", sine.round(), IgnitorDsl.Round(inner = sine)),
            Triple("frac", sine.frac(), IgnitorDsl.Frac(inner = sine)),
            Triple("recip", sine.recip(), IgnitorDsl.Recip(inner = sine)),
            Triple("reciprocal", sine.reciprocal(), IgnitorDsl.Recip(inner = sine)),
            Triple("sq", sine.sq(), IgnitorDsl.Sq(inner = sine)),
            Triple("pregain", sine.pregain(), IgnitorDsl.Times(left = sine, right = IgnitorDsl.Slots.pregain)),
        )

        rows.forEach { (word, kotlin, written) ->
            kotlin shouldBe written
            ignitor("Ign.sine(2).$word()") shouldBe written
        }
    }

    // ── Signals and the script's numbers ─────────────────────────────────────

    "every two-operand word takes a signal on both doors" {
        sine + saw shouldBe IgnitorDsl.Plus(left = sine, right = saw)
        sine * saw shouldBe IgnitorDsl.Times(left = sine, right = saw)
        ignitor("Ign.sine(2).plus(Ign.saw(3))") shouldBe sine + saw
        ignitor("Ign.sine(2).mul(Ign.saw(3))") shouldBe sine * saw
        ignitor("Ign.sine(2).div(Ign.saw(3))") shouldBe sine.div(saw)
        ignitor("Ign.sine(2).minus(Ign.saw(3))") shouldBe sine.minus(saw)
        ignitor("Ign.sine(2).pow(Ign.saw(3))") shouldBe sine.pow(saw)
        ignitor("Ign.sine(2).mod(Ign.saw(3))") shouldBe sine.mod(saw)
        ignitor("Ign.sine(2).clamp(Ign.saw(3), 1)") shouldBe sine.clamp(lo = saw, hi = 1.ign)
        ignitor("Ign.sine(2).range(Ign.saw(3), 1)") shouldBe sine.range(from = saw, to = 1.ign)
        ignitor("Ign.sine(2).select(Ign.saw(3), 0)") shouldBe sine.select(whenTrue = saw, whenFalse = 0.ign)
    }

    "-1.ign stays a Constant beside the arithmetic, the script's -1, never a Neg node" {
        (-1.ign) shouldBe c(-1.0)
        sine * -1.ign shouldBe ignitor("Ign.sine(2).mul(-1)")
        sine * -1.ign shouldBe IgnitorDsl.Times(left = sine, right = c(-1.0))
    }

    "the script's operators stay numbers only: x * 0.5 on a signal is a type error, x.mul(0.5) is the word" {
        // The interpreter has no operator overloading for native values (`Interpreter.evaluateBinaryOp`); the
        // script's arithmetic on a signal is the method. Pinned here so the S4 change is seen not to move it.
        // A recorded asymmetry: Kotlin has `+` / `*` on signals, the script is method-only until
        // `docs/tasks/klangscript-native-object-operators.md` lands (decided 2026-10-07, `__times__` / `__rtimes__`,
        // the goal `saw * 4`). This row goes red ON PURPOSE then: replace it with the operator's parity rows.
        val error = shouldThrow<KlangScriptTypeError> { ignitor("Ign.sine(2) * 0.5") }

        error.message shouldContain "requires numbers"
    }
})
