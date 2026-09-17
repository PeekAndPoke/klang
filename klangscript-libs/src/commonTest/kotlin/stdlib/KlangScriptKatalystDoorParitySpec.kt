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
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.KlangScriptTypeError
import io.peekandpoke.klang.script.runtime.NativeObjectValue

/**
 * Dual-surface rule: every stage and every knob of an orbit chain must be reachable from
 * KlangScript AND from Kotlin, with the same name meaning the same thing. This spec compares the
 * two doors stage for stage and knob for knob.
 *
 * It compares against the STAGE DATA CLASSES rather than against the Kotlin builder calling the
 * same lambda, so a knob wired to the wrong field (`sweep` writing `center`) cannot look identical
 * on both sides. The one case that does go through the Kotlin builder is the last, which pins that
 * the two builders are the same object with two front doors.
 */
class KlangScriptKatalystDoorParitySpec : StringSpec({

    fun ks(code: String): KatalystDsl {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        val result = engine.execute(code)
        result.shouldBeInstanceOf<NativeObjectValue<*>>()
        return result.value.shouldBeInstanceOf<KatalystDsl>()
    }

    fun c(value: Double): IgnitorDsl = IgnitorDsl.Constant(value)

    "Katalyst() is the empty chain, and Katalyst(k => ...) == Katalyst.build(k => ...)" {
        ks("Katalyst()") shouldBe KatalystDsl(emptyList())

        val code = "k => k.reverb(r => r.wet(0.2)).gain(1.4)"
        ks("Katalyst($code)") shouldBe ks("Katalyst.build($code)")
    }

    "Katalyst.classic() == KatalystDsl.classic, and classic() inside a chain appends its stages" {
        ks("Katalyst.classic()") shouldBe KatalystDsl.classic

        ks("Katalyst(k => k.classic().eq(e => e.band(freq = 160, q = 1.0, db = -3.0)))") shouldBe
                KatalystDsl(
                    KatalystDsl.classic.stages + KatalystStageDsl.Eq(
                        sections = listOf(
                            IgnitorDsl.EqSection.Bell(freq = c(160.0), q = c(1.0), db = c(-3.0))
                        )
                    )
                )
    }

    "stages append in written order, script == Kotlin data classes" {
        ks("Katalyst(k => k.reverb(r => r.wet(0.05).size(9)).gain(2.5).compressor())") shouldBe KatalystDsl.of(
            KatalystStageDsl.Reverb(wet = c(0.05), size = c(9.0)),
            KatalystStageDsl.Gain(gain = c(2.5)),
            KatalystStageDsl.Compressor(),
        )
    }

    "every knob of every stage reaches its own field" {
        listOf(
            "body" to (
                    """Katalyst(k => k.body("wood", b => b.wet(0.7).floor(0.3)))""" to
                            KatalystDsl.of(KatalystStageDsl.Body(material = "wood", wet = c(0.7), floor = c(0.3)))
                    ),
            "vowel" to (
                    """Katalyst(k => k.vowel("soprano:a", v => v.wet(0.6).floor(0.1)))""" to
                            KatalystDsl.of(KatalystStageDsl.Vowel(vowel = "soprano:a", wet = c(0.6), floor = c(0.1)))
                    ),
            "delay" to (
                    "Katalyst(k => k.delay(d => d.wet(0.2).time(0.5).feedback(1.0).cap(3.0)))" to
                            KatalystDsl.of(
                                KatalystStageDsl.Delay(wet = c(0.2), time = c(0.5), feedback = c(1.0), cap = c(3.0))
                            )
                    ),
            "reverb" to (
                    "Katalyst(k => k.reverb(r => r.wet(0.3).size(8).lowpass(6000)))" to
                            KatalystDsl.of(KatalystStageDsl.Reverb(wet = c(0.3), size = c(8.0), lowpass = c(6000.0)))
                    ),
            "phaser" to (
                    "Katalyst(k => k.phaser(p => p.rate(0.3).wet(0.5).center(800).sweep(1200).floor(0.2)))" to
                            KatalystDsl.of(
                                KatalystStageDsl.Phaser(
                                    rate = c(0.3), wet = c(0.5), center = c(800.0), sweep = c(1200.0), floor = c(0.2),
                                )
                            )
                    ),
            "compressor" to (
                    "Katalyst(k => k.compressor(c => c.threshold(-21).ratio(3).knee(6).attack(0.005).release(0.12)))" to
                            KatalystDsl.of(
                                KatalystStageDsl.Compressor(
                                    threshold = c(-21.0), ratio = c(3.0), knee = c(6.0),
                                    attack = c(0.005), release = c(0.12),
                                )
                            )
                    ),
            "duck" to (
                    "Katalyst(k => k.duck(d => d.orbit(2).depth(0.8).attack(0.05)))" to
                            KatalystDsl.of(KatalystStageDsl.Duck(orbit = c(2.0), depth = c(0.8), attack = c(0.05)))
                    ),
            "eq" to (
                    "Katalyst(k => k.eq(e => e.band(freq = 300, q = 0.8, db = 2.0).tap(850, 0.707, 1.7)))" to
                            KatalystDsl.of(
                                KatalystStageDsl.Eq(
                                    sections = listOf(
                                        IgnitorDsl.EqSection.Bell(freq = c(300.0), q = c(0.8), db = c(2.0)),
                                        IgnitorDsl.EqSection.RawTap(freq = c(850.0), q = c(0.707), gain = c(1.7)),
                                    )
                                )
                            )
                    ),
            "gain" to ("Katalyst(k => k.gain(1.45))" to KatalystDsl.of(KatalystStageDsl.Gain(gain = c(1.45)))),
        ).forEach { (stage, case) ->
            val (script, kotlin) = case
            withClue(stage) { ks(script) shouldBe kotlin }
        }
    }

    "a bare stage from the script door is the bare data class: same defaults on both surfaces" {
        ks("Katalyst(k => k.body().vowel().delay().reverb().phaser().compressor().duck().eq().gain())") shouldBe
                KatalystDsl.of(
                    KatalystStageDsl.Body(),
                    KatalystStageDsl.Vowel(),
                    KatalystStageDsl.Delay(),
                    KatalystStageDsl.Reverb(),
                    KatalystStageDsl.Phaser(),
                    KatalystStageDsl.Compressor(),
                    KatalystStageDsl.Duck(),
                    KatalystStageDsl.Eq(),
                    KatalystStageDsl.Gain(),
                )
    }

    "a knob takes an Osc.param slot as readily as a number, on both doors" {
        ks("""Katalyst(k => k.reverb(r => r.wet(Osc.param("room", 0.2))))""") shouldBe
                KatalystDsl.of(KatalystStageDsl.Reverb(wet = IgnitorDsl.Param("room", 0.2)))
    }

    "the Kotlin door takes the same lambda" {
        ks("Katalyst(k => k.reverb(r => r.wet(0.2)).gain(1.4))") shouldBe
                KlangScriptKatalyst.build { it.reverb { r -> r.wet(0.2) }.gain(1.4) }
    }

    "Katalyst is still a value: stored, then called" {
        ks("let K = Katalyst\nK(k => k.gain(2))") shouldBe KatalystDsl.of(KatalystStageDsl.Gain(c(2.0)))
    }

    "a lambda that returns nothing is a script-level type error naming the door" {
        val err = shouldThrow<KlangScriptTypeError> { ks("Katalyst(k => { k.gain(2) })") }
        err.message shouldBe
                "the configure lambda of Katalyst returned nothing; return the builder it received (`x => x.analog(3)`)"
    }

    "a stage lambda that returns nothing names its stage" {
        val err = shouldThrow<KlangScriptTypeError> { ks("Katalyst(k => k.reverb(r => { r.wet(0.2) }))") }
        err.message shouldBe
                "the configure lambda of Katalyst reverb returned nothing; return the builder it received (`x => x.analog(3)`)"
    }
})
