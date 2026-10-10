/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.KlangScriptTypeError
import io.peekandpoke.klang.script.runtime.NativeObjectValue

/**
 * Door parity for the sine partial banks (`docs/plans/sine-partial-banks.md`): the script door
 * `Ignitor.sine(freq, configure)` and the Kotlin door (`OscSineBuilder` on `IgnitorDsl.Sine`) build equal
 * nodes for every knob, `freq` stays the door's first parameter, and a sine without bank knobs is the
 * plain sine the engine short-circuits on.
 */
class KlangScriptSineSpec : StringSpec({
    fun ks(code: String): IgnitorDsl {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        val result = engine.execute(code)
        result.shouldBeInstanceOf<NativeObjectValue<*>>()
        return result.value.shouldBeInstanceOf<IgnitorDsl>()
    }

    fun node() = KlangScriptIgnitor.sine() as IgnitorDsl.Sine
    fun c(v: Double) = IgnitorDsl.Constant(v)

    "Ignitor.sine(): script == Kotlin door, all defaults, and it is the plain sine" {
        ks("Ignitor.sine()") shouldBe node()
        node() shouldBe IgnitorDsl.Sine()
        node().isPlainSine() shouldBe true
    }

    "freq is the door's first parameter, not a knob: Ignitor.sine(220)" {
        ks("Ignitor.sine(220)") shouldBe node().copy(freq = c(220.0))
    }

    "analog alone keeps the plain sine" {
        val dsl = ks("Ignitor.sine(x => x.analog(3))") as IgnitorDsl.Sine
        dsl shouldBe node().copy(analog = c(3.0))
        dsl.isPlainSine() shouldBe true
    }

    "harmonics(7): count, rolloff defaults to 1" {
        ks("Ignitor.sine(x => x.harmonics(7))") shouldBe node().copy(harmonics = c(7.0), harmonicsRolloff = c(1.0))
    }

    "harmonics(1, 0.5): count and rolloff positional" {
        ks("Ignitor.sine(x => x.harmonics(1, 0.5))") shouldBe node().copy(harmonics = c(1.0), harmonicsRolloff = c(0.5))
    }

    "harmonics(count = 3, rolloff = 2): named" {
        ks("Ignitor.sine(x => x.harmonics(count = 3, rolloff = 2))") shouldBe node().copy(harmonics = c(3.0), harmonicsRolloff = c(2.0))
    }

    "rolloff accepts an Ignitor graph (control-rate)" {
        ks("Ignitor.sine(x => x.harmonics(8, Ignitor.sine(0.2)))") shouldBe
            node().copy(harmonics = c(8.0), harmonicsRolloff = IgnitorDsl.Sine(freq = c(0.2)))
    }

    "octaves(5) and octaves(3, 0)" {
        ks("Ignitor.sine(x => x.octaves(5))") shouldBe node().copy(octaves = c(5.0), octavesRolloff = c(1.0))
        ks("Ignitor.sine(x => x.octaves(3, 0))") shouldBe node().copy(octaves = c(3.0), octavesRolloff = c(0.0))
    }

    "suboctaves(1) and suboctaves(1, 0)" {
        ks("Ignitor.sine(x => x.suboctaves(1))") shouldBe node().copy(suboctaves = c(1.0), suboctavesRolloff = c(1.0))
        ks("Ignitor.sine(x => x.suboctaves(1, 0))") shouldBe node().copy(suboctaves = c(1.0), suboctavesRolloff = c(0.0))
    }

    "fundamental(0) and fundamental as a graph" {
        ks("Ignitor.sine(x => x.fundamental(0))") shouldBe node().copy(fundamental = c(0.0))
        ks("Ignitor.sine(x => x.fundamental(Ignitor.sine(0.5)))") shouldBe node().copy(fundamental = IgnitorDsl.Sine(freq = c(0.5)))
    }

    "analogSpread(0)" {
        ks("Ignitor.sine(x => x.analogSpread(0))") shouldBe node().copy(analogSpread = c(0.0))
    }

    "every bank knob flips isPlainSine, a Param at its default included" {
        (ks("Ignitor.sine(x => x.harmonics(1))") as IgnitorDsl.Sine).isPlainSine() shouldBe false
        (ks("Ignitor.sine(x => x.octaves(1))") as IgnitorDsl.Sine).isPlainSine() shouldBe false
        (ks("Ignitor.sine(x => x.suboctaves(1))") as IgnitorDsl.Sine).isPlainSine() shouldBe false
        (ks("Ignitor.sine(x => x.fundamental(0.5))") as IgnitorDsl.Sine).isPlainSine() shouldBe false
        (ks("""Ignitor.sine(x => x.harmonics(Ignitor.param("h", 0)))""") as IgnitorDsl.Sine).isPlainSine() shouldBe false
    }

    "every knob in one lambda, freq on the door" {
        val code = "Ignitor.sine(110, x => x.analog(4).fundamental(0.5).harmonics(7, 1.5).octaves(2, 0.5)" +
            ".suboctaves(1, 0).analogSpread(0.25))"
        ks(code) shouldBe IgnitorDsl.Sine(
            freq = c(110.0),
            analog = c(4.0),
            fundamental = c(0.5),
            harmonics = c(7.0), harmonicsRolloff = c(1.5),
            octaves = c(2.0), octavesRolloff = c(0.5),
            suboctaves = c(1.0), suboctavesRolloff = c(0.0),
            analogSpread = c(0.25),
        )
    }

    "the Kotlin door takes the same lambda" {
        ks("Ignitor.sine(x => x.harmonics(7, 0.5).fundamental(0))") shouldBe
            KlangScriptIgnitor.sine(configure = { it.harmonics(7.0, 0.5).fundamental(0.0) })
    }

    // ── the explicit partials (Q26): one partial per call, in the order written ──

    fun p(ratio: Double, gain: Double = 1.0, phase: Double = 0.0) = IgnitorDsl.Sine.Partial(ratio = c(ratio), gain = c(gain), phase = c(phase))

    "partial(ratio): gain defaults to 1, phase to 0" {
        ks("Ignitor.sine(x => x.partial(1.5))") shouldBe node().copy(partials = listOf(p(1.5)))
    }

    "partial(ratio, gain, phase): positional and named" {
        ks("Ignitor.sine(x => x.partial(0.8714, 0.652, 0.5))") shouldBe node().copy(partials = listOf(p(0.8714, 0.652, 0.5)))
        ks("Ignitor.sine(x => x.partial(ratio = 0.8714, gain = 0.652, phase = 0.5))") shouldBe node().copy(partials = listOf(p(0.8714, 0.652, 0.5)))
        ks("Ignitor.sine(x => x.partial(ratio = 2, phase = 0.25))") shouldBe node().copy(partials = listOf(p(2.0, 1.0, 0.25)))
    }

    "each call adds one partial, kept in the order written, next to the other knobs" {
        ks("Ignitor.sine(x => x.fundamental(0).partial(0.6571, 0.52).harmonics(2).partial(0.8714, 0.652, 0.5))") shouldBe
            node().copy(
                fundamental = c(0.0),
                harmonics = c(2.0), harmonicsRolloff = c(1.0),
                partials = listOf(p(0.6571, 0.52), p(0.8714, 0.652, 0.5)),
            )
    }

    "every partial knob accepts a signal" {
        ks("""Ignitor.sine(x => x.partial(Ignitor.param("r", 1.5), Ignitor.sine(0.5), Ignitor.sine(2)))""") shouldBe
            node().copy(
                partials = listOf(
                    IgnitorDsl.Sine.Partial(ratio = IgnitorDsl.Param("r", 1.5), gain = IgnitorDsl.Sine(freq = c(0.5)), phase = IgnitorDsl.Sine(freq = c(2.0))),
                ),
            )
    }

    "a partial flips isPlainSine" {
        (ks("Ignitor.sine(x => x.partial(1))") as IgnitorDsl.Sine).isPlainSine() shouldBe false
    }

    "the Kotlin door: the same chain builds the same partials, and the builder is immutable" {
        ks("Ignitor.sine(x => x.fundamental(0).partial(0.6571, 0.52).partial(0.8714, 0.652, 0.5))") shouldBe
            KlangScriptIgnitor.sine(configure = {
                it.fundamental(0.0)
                    .partial(ratio = 0.6571, gain = 0.52)
                    .partial(ratio = 0.8714, gain = 0.652, phase = 0.5)
            })

        val base = OscSineBuilder(node())
        val one = base.partial(ratio = 1.5)

        base.node.partials shouldBe emptyList()
        one.node.partials shouldBe listOf(p(1.5))
        one.partial(ratio = 2.0).node.partials shouldBe listOf(p(1.5), p(2.0))
        one.node.partials shouldBe listOf(p(1.5))
    }

    "the even series: multiples follow the door's freq" {
        ks("Ignitor.sine(Ignitor.freq().mul(2), x => x.harmonics(3))") shouldBe
            node().copy(freq = IgnitorDsl.Times(IgnitorDsl.Freq, c(2.0)), harmonics = c(3.0), harmonicsRolloff = c(1.0))
    }

    "processing goes OUTSIDE the lambda: the wrapper sees the configured node" {
        val dsl = ks("Ignitor.sine(x => x.harmonics(7)).lowpass(2000)")
        dsl.shouldBeInstanceOf<IgnitorDsl.Lowpass>()
        val inner = dsl.inner
        inner.shouldBeInstanceOf<IgnitorDsl.Sine>()
        inner.harmonics shouldBe c(7.0)
    }
})
