/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.clamp
import io.peekandpoke.klang.audio_bridge.div
import io.peekandpoke.klang.audio_bridge.lerp
import io.peekandpoke.klang.audio_bridge.max
import io.peekandpoke.klang.audio_bridge.min
import io.peekandpoke.klang.audio_bridge.minus
import io.peekandpoke.klang.audio_bridge.mod
import io.peekandpoke.klang.audio_bridge.mul
import io.peekandpoke.klang.audio_bridge.plus
import io.peekandpoke.klang.audio_bridge.pow
import io.peekandpoke.klang.audio_bridge.range
import io.peekandpoke.klang.audio_bridge.rangex
import io.peekandpoke.klang.audio_bridge.select
import io.peekandpoke.klang.audio_bridge.times
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.toObjectOrNull

/**
 * The Ignitor arithmetic with a plain number, on both doors (engine tidy-up step 13, D10, maintainer 2026-10-08: "a
 * plain number should be possible everywhere a constant value is accepted"). The script door takes a number through
 * `IgnitorDslLike`; the Kotlin door has a `Double` overload that delegates to the `IgnitorDsl` door with a `Constant`.
 * Both build the same tree, the script's aliases included.
 *
 * `min` and `max` are clamps on every door (`CLAUDE.md`, guardrail): `x.max(1)` is "x, at most 1" and builds the
 * `Min` node, on both doors. The rows name the node, so a `Double` overload that built the node itself, or crossed
 * the wrong way, goes red here.
 */
class KlangScriptIgnitorArithmeticDoorParitySpec : StringSpec({

    fun ignitor(code: String): IgnitorDsl {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        return engine.execute(code).toObjectOrNull<IgnitorDsl>()!!
    }

    fun c(v: Double) = IgnitorDsl.Constant(v)

    val sine = ignitor("Ign.sine(2)")

    "plus: x.plus(0.5) and x.add(0.5) in script, x + 0.5 in Kotlin" {
        val kotlin = sine + 0.5

        kotlin shouldBe IgnitorDsl.Plus(left = sine, right = c(0.5))
        ignitor("Ign.sine(2).plus(0.5)") shouldBe kotlin
        ignitor("Ign.sine(2).add(0.5)") shouldBe kotlin
    }

    "times: x.times(0.5) in script, x * 0.5 in Kotlin" {
        val kotlin = sine * 0.5

        kotlin shouldBe IgnitorDsl.Times(left = sine, right = c(0.5))
        ignitor("Ign.sine(2).times(0.5)") shouldBe kotlin
    }

    "mul: x.mul(0.5) on both doors" {
        val kotlin = sine.mul(0.5)

        kotlin shouldBe IgnitorDsl.Times(left = sine, right = c(0.5))
        ignitor("Ign.sine(2).mul(0.5)") shouldBe kotlin
    }

    "div: x.div(4) on both doors" {
        val kotlin = sine.div(4.0)

        kotlin shouldBe IgnitorDsl.Div(left = sine, right = c(4.0))
        ignitor("Ign.sine(2).div(4)") shouldBe kotlin
    }

    "minus: x.minus(0.5) and x.sub(0.5) in script, x.minus(0.5) in Kotlin" {
        val kotlin = sine.minus(0.5)

        kotlin shouldBe IgnitorDsl.Minus(left = sine, right = c(0.5))
        ignitor("Ign.sine(2).minus(0.5)") shouldBe kotlin
        ignitor("Ign.sine(2).sub(0.5)") shouldBe kotlin
    }

    "pow: x.pow(3) and x.power(3) in script, x.pow(3.0) in Kotlin" {
        val kotlin = sine.pow(3.0)

        kotlin shouldBe IgnitorDsl.Pow(base = sine, exp = c(3.0))
        ignitor("Ign.sine(2).pow(3)") shouldBe kotlin
        ignitor("Ign.sine(2).power(3)") shouldBe kotlin
    }

    "min: x.min(0) is a floor on both doors and builds the Max node" {
        val kotlin = sine.min(0.0)

        kotlin shouldBe IgnitorDsl.Max(left = sine, right = c(0.0))
        ignitor("Ign.sine(2).min(0)") shouldBe kotlin
    }

    "max: x.max(0.5) is a cap on both doors and builds the Min node" {
        val kotlin = sine.max(0.5)

        kotlin shouldBe IgnitorDsl.Min(left = sine, right = c(0.5))
        ignitor("Ign.sine(2).max(0.5)") shouldBe kotlin
    }

    "clamp: x.clamp(-0.5, 0.75) on both doors, by name too" {
        val kotlin = sine.clamp(lo = -0.5, hi = 0.75)

        kotlin shouldBe IgnitorDsl.Clamp(inner = sine, lo = c(-0.5), hi = c(0.75))
        ignitor("Ign.sine(2).clamp(-0.5, 0.75)") shouldBe kotlin
        ignitor("Ign.sine(2).clamp(hi = 0.75, lo = -0.5)") shouldBe kotlin
    }

    "lerp: toward a signal at a constant weight, and toward a constant, on both doors (mix is the script alias)" {
        val saw = ignitor("Ign.saw(3)")
        val towardSignal = sine.lerp(other = saw, t = 0.3)
        val towardConstant = sine.lerp(other = 0.5, t = 0.3)

        towardSignal shouldBe IgnitorDsl.Lerp(left = sine, right = saw, t = c(0.3))
        towardConstant shouldBe IgnitorDsl.Lerp(left = sine, right = c(0.5), t = c(0.3))
        ignitor("Ign.sine(2).lerp(Ign.saw(3), 0.3)") shouldBe towardSignal
        ignitor("Ign.sine(2).mix(Ign.saw(3), 0.3)") shouldBe towardSignal
        ignitor("Ign.sine(2).lerp(0.5, 0.3)") shouldBe towardConstant
        ignitor("Ign.sine(2).lerp(t = 0.3, other = 0.5)") shouldBe towardConstant
    }

    "range: x.range(200, 800) on both doors" {
        val kotlin = sine.range(from = 200.0, to = 800.0)

        kotlin shouldBe IgnitorDsl.Range(inner = sine, from = c(200.0), to = c(800.0))
        ignitor("Ign.sine(2).range(200, 800)") shouldBe kotlin
    }

    "rangex: x.rangex(200, 3200) on both doors, the same composed tree as the IgnitorDsl door" {
        val kotlin = sine.rangex(from = 200.0, to = 3200.0)

        kotlin shouldBe sine.rangex(from = c(200.0), to = c(3200.0))
        ignitor("Ign.sine(2).rangex(200, 3200)") shouldBe kotlin
    }

    "mod: x.mod(0.4) and x.rem(0.4) in script, x.mod(0.4) in Kotlin" {
        val kotlin = sine.mod(0.4)

        kotlin shouldBe IgnitorDsl.Mod(left = sine, right = c(0.4))
        ignitor("Ign.sine(2).mod(0.4)") shouldBe kotlin
        ignitor("Ign.sine(2).rem(0.4)") shouldBe kotlin
    }

    "select: x.select(1, -1) on both doors" {
        val kotlin = sine.select(whenTrue = 1.0, whenFalse = -1.0)

        kotlin shouldBe IgnitorDsl.Select(cond = sine, whenTrue = c(1.0), whenFalse = c(-1.0))
        ignitor("Ign.sine(2).select(1, -1)") shouldBe kotlin
    }
})
