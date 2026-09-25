/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.string.shouldContain
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.AdsrCurve
import io.peekandpoke.klang.audio_bridge.AdsrCurves
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.adsr
import io.peekandpoke.klang.audio_bridge.bandpass
import io.peekandpoke.klang.audio_bridge.constants.MOD_ENV_CURVE
import io.peekandpoke.klang.audio_bridge.fm
import io.peekandpoke.klang.audio_bridge.highpass
import io.peekandpoke.klang.audio_bridge.lowpass
import io.peekandpoke.klang.audio_bridge.notch
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.toObjectOrNull

/**
 * ONE `adsr(attackSec, decaySec, sustainLevel, releaseSec, configure)` shape on every envelope
 * (phase 3 step 3c, maintainer, 2026-09-25), door against door, looping over the family: the chain
 * `adsr`, the four filters' cutoff envelope, the pitch envelope, and fm's index envelope.
 *
 * Each envelope's script form is compared with its Kotlin form: the flat audio_bridge door for the
 * chain and the filters, the node constructor for the pitch envelope (there is no Kotlin
 * `pitchEnvelope` door, recorded in 3d(i)), the flat `fm` door for fm. What an envelope's lambda
 * offers is what THAT envelope can do: the chain `curves` and `declick`, the modulation envelopes
 * `curves`, fm nothing yet.
 */
class KlangScriptEnvelopeDoorParitySpec : StringSpec({

    fun ks(code: String): IgnitorDsl {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        return engine.execute(code).toObjectOrNull<IgnitorDsl>()!!
    }

    fun c(v: Double) = IgnitorDsl.Constant(v)

    val saw = IgnitorDsl.Sawtooth()

    /**
     * One envelope of the family: how the script writes it with a given `adsr(...)` call, how the
     * Kotlin door writes it with the same stages and curves, and where its three curves live on the
     * node. [default] is that envelope's own unshaped curve.
     */
    class Envelope(
        val name: String,
        val default: AdsrCurve,
        val script: (adsrCall: String) -> String,
        val kotlin: (a: AdsrCurve?, d: AdsrCurve?, r: AdsrCurve?) -> IgnitorDsl,
        val curves: (IgnitorDsl) -> List<IgnitorDsl>,
    )

    // The stages every row uses: attack, decay, sustain, release.
    val stages = "0.005, 0.3, 0.2, 0.05"

    fun filterCurves(node: IgnitorDsl): List<IgnitorDsl> = when (node) {
        is IgnitorDsl.Lowpass -> listOf(node.attackCurve, node.decayCurve, node.releaseCurve)
        is IgnitorDsl.Highpass -> listOf(node.attackCurve, node.decayCurve, node.releaseCurve)
        is IgnitorDsl.Bandpass -> listOf(node.attackCurve, node.decayCurve, node.releaseCurve)
        is IgnitorDsl.Notch -> listOf(node.attackCurve, node.decayCurve, node.releaseCurve)
        else -> error("not a filter: ${node::class.simpleName}")
    }

    val family = listOf(
        Envelope(
            "chain adsr", AdsrCurve.Default,
            script = { call -> "Osc.saw().$call" },
            kotlin = { a, d, r -> saw.adsr(0.005, 0.3, 0.2, 0.05, a, d, r) },
            curves = { (it as IgnitorDsl.Adsr).let { n -> listOf(n.attackCurve, n.decayCurve, n.releaseCurve) } },
        ),
        Envelope(
            "lowpass", MOD_ENV_CURVE,
            script = { call -> "Osc.saw().lowpass(800, x => x.$call)" },
            kotlin = { a, d, r -> saw.lowpass(800.0, attackSec = 0.005, decaySec = 0.3, sustainLevel = 0.2, releaseSec = 0.05, attackCurve = a, decayCurve = d, releaseCurve = r) },
            curves = ::filterCurves,
        ),
        Envelope(
            "highpass", MOD_ENV_CURVE,
            script = { call -> "Osc.saw().highpass(800, x => x.$call)" },
            kotlin = { a, d, r -> saw.highpass(800.0, attackSec = 0.005, decaySec = 0.3, sustainLevel = 0.2, releaseSec = 0.05, attackCurve = a, decayCurve = d, releaseCurve = r) },
            curves = ::filterCurves,
        ),
        Envelope(
            "bandpass", MOD_ENV_CURVE,
            script = { call -> "Osc.saw().bandpass(800, x => x.$call)" },
            kotlin = { a, d, r -> saw.bandpass(800.0, attackSec = 0.005, decaySec = 0.3, sustainLevel = 0.2, releaseSec = 0.05, attackCurve = a, decayCurve = d, releaseCurve = r) },
            curves = ::filterCurves,
        ),
        Envelope(
            "notch", MOD_ENV_CURVE,
            script = { call -> "Osc.saw().notch(800, x => x.$call)" },
            kotlin = { a, d, r -> saw.notch(800.0, attackSec = 0.005, decaySec = 0.3, sustainLevel = 0.2, releaseSec = 0.05, attackCurve = a, decayCurve = d, releaseCurve = r) },
            curves = ::filterCurves,
        ),
        Envelope(
            "pitch", MOD_ENV_CURVE,
            script = { call -> "Osc.saw().pitchEnvelope(12, x => x.$call)" },
            kotlin = { a, d, r ->
                val base = IgnitorDsl.PitchEnvelope(
                    inner = saw, semitones = c(12.0),
                    attackSec = c(0.005), decaySec = c(0.3), sustainLevel = c(0.2), releaseSec = c(0.05),
                )

                base.copy(
                    attackCurve = a?.let(AdsrCurves::knob) ?: base.attackCurve,
                    decayCurve = d?.let(AdsrCurves::knob) ?: base.decayCurve,
                    releaseCurve = r?.let(AdsrCurves::knob) ?: base.releaseCurve,
                )
            },
            curves = { (it as IgnitorDsl.PitchEnvelope).let { n -> listOf(n.attackCurve, n.decayCurve, n.releaseCurve) } },
        ),
    )

    "every envelope: the same adsr call builds the same node on both doors, with and without curves" {
        for (env in family) {
            withClue("${env.name} without a lambda") {
                ks(env.script("adsr($stages)")) shouldBe env.kotlin(null, null, null)
            }
            withClue("${env.name} with curves") {
                ks(env.script("""adsr($stages, e => e.curves("square", "cube", "scurve"))""")) shouldBe
                        env.kotlin(AdsrCurve.Square, AdsrCurve.Cube, AdsrCurve.SCurve)
            }
        }
    }

    "every envelope: each curve argument reaches ITS stage and no other" {
        // One named stage at a time, so a builder that wired `attack` into the decay (or dropped
        // one) is red on its own envelope's name.
        for (env in family) {
            val default = AdsrCurves.knob(env.default)
            val named = listOf("attack", "decay", "release")

            named.forEachIndexed { index, stage ->
                val node = ks(env.script("""adsr($stages, e => e.curves($stage = "cube"))"""))
                val expected = List(3) { if (it == index) AdsrCurves.knob(AdsrCurve.Cube) else default }

                withClue("${env.name} $stage") { env.curves(node) shouldBe expected }
            }
        }
    }

    "every envelope: an omitted or unknown curve is THAT envelope's default, and a name, a number and a slot all reach it" {
        for (env in family) {
            val default = AdsrCurves.knob(env.default)

            withClue("${env.name} bare curves()") {
                env.curves(ks(env.script("adsr($stages, e => e.curves())"))) shouldBe List(3) { default }
            }
            withClue("${env.name} unknown names") {
                env.curves(ks(env.script("""adsr($stages, e => e.curves("bogus", "sqare", ""))"""))) shouldBe List(3) { default }
            }
            withClue("${env.name} a number is the index, a slot passes through") {
                env.curves(ks(env.script("""adsr($stages, e => e.curves(1, Osc.param("c", 2), "exp"))"""))) shouldBe listOf(
                    c(1.0), IgnitorDsl.Param("c", 2.0), AdsrCurves.knob(AdsrCurve.Exponential),
                )
            }
        }
    }

    "the chain's lambda also de-clicks; the modulation envelopes' lambdas do not offer it" {
        ks("Osc.saw().adsr($stages, e => e.declick(0.0005))") shouldBe
                saw.adsr(0.005, 0.3, 0.2, 0.05, declickSeconds = 0.0005)

        ks("""Osc.saw().adsr($stages, e => e.curves("lin", "exp", "exp").declick(0.001))""") shouldBe
                saw.adsr(0.005, 0.3, 0.2, 0.05, AdsrCurve.Linear, AdsrCurve.Exponential, AdsrCurve.Exponential, declickSeconds = 0.001)

        for (env in family.filter { it.name != "chain adsr" }) {
            withClue("${env.name} has no declick") {
                shouldThrowAny { ks(env.script("adsr($stages, e => e.declick(0.001))")) }
                    .message shouldContain "has no method 'declick'"
            }
        }
    }

    "the Kotlin chain door: null keeps the node's defaults, so a bare call is today's node" {
        saw.adsr(0.01, 0.1, 0.7, 0.3) shouldBe IgnitorDsl.Adsr(
            inner = saw, attackSec = c(0.01), decaySec = c(0.1), sustainLevel = c(0.7), releaseSec = c(0.3),
        )
    }

    "fm: the index envelope takes the same four stages and NO lambda yet (no curve support)" {
        ks("Osc.saw().fm(Osc.sine(), 1.4, 300, x => x.adsr($stages))") shouldBe
                saw.fm(IgnitorDsl.Sine(), 1.4, 300.0, envAttackSec = 0.005, envDecaySec = 0.3, envSustainLevel = 0.2, envReleaseSec = 0.05)

        shouldThrowAny { ks("""Osc.saw().fm(Osc.sine(), 1.4, 300, x => x.adsr($stages, e => e.curves("lin")))""") }
            .message shouldContain "too many arguments (5, expected"
    }
})
