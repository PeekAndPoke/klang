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
import io.peekandpoke.klang.audio_bridge.bandpass
import io.peekandpoke.klang.audio_bridge.constants.FILTER_ENV_ATTACK_SEC
import io.peekandpoke.klang.audio_bridge.constants.FILTER_ENV_DECAY_SEC
import io.peekandpoke.klang.audio_bridge.constants.FILTER_ENV_DEPTH_SEMITONES
import io.peekandpoke.klang.audio_bridge.constants.FILTER_ENV_RELEASE_SEC
import io.peekandpoke.klang.audio_bridge.constants.FILTER_ENV_SUSTAIN_LEVEL
import io.peekandpoke.klang.audio_bridge.constants.MOD_ENV_CURVE
import io.peekandpoke.klang.audio_bridge.highpass
import io.peekandpoke.klang.audio_bridge.lowpass
import io.peekandpoke.klang.audio_bridge.notch
import io.peekandpoke.klang.audio_bridge.onepole
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.toObjectOrNull

/**
 * Every field of a filter node except its input, by name: the door's inputs, the builder's knobs
 * and the envelope after the fill. `passes` is a constant 1 on the two band filters, which have no such knob.
 */
private fun IgnitorDsl.filterFields(): Map<String, Any?> = when (this) {
    is IgnitorDsl.Lowpass -> fields(freq, q, analog, passes, env, attackSec, decaySec, sustainLevel, releaseSec, attackCurve, decayCurve, releaseCurve, humanize)
    is IgnitorDsl.Highpass -> fields(freq, q, analog, passes, env, attackSec, decaySec, sustainLevel, releaseSec, attackCurve, decayCurve, releaseCurve, humanize)
    is IgnitorDsl.Bandpass -> fields(freq, q, analog, IgnitorDsl.Constant(1.0), env, attackSec, decaySec, sustainLevel, releaseSec, attackCurve, decayCurve, releaseCurve, humanize)
    is IgnitorDsl.Notch -> fields(freq, q, analog, IgnitorDsl.Constant(1.0), env, attackSec, decaySec, sustainLevel, releaseSec, attackCurve, decayCurve, releaseCurve, humanize)
    else -> error("not a filter node: ${this::class.simpleName}")
}

private fun fields(
    freq: IgnitorDsl, q: IgnitorDsl, analog: IgnitorDsl, passes: IgnitorDsl, env: IgnitorDsl,
    a: IgnitorDsl, d: IgnitorDsl, s: IgnitorDsl, r: IgnitorDsl,
    ac: IgnitorDsl, dc: IgnitorDsl, rc: IgnitorDsl, humanize: Boolean,
): Map<String, Any?> = linkedMapOf(
    "freq" to freq, "q" to q, "analog" to analog, "passes" to passes, "env" to env,
    "attackSec" to a, "decaySec" to d, "sustainLevel" to s, "releaseSec" to r,
    "attackCurve" to ac, "decayCurve" to dc, "releaseCurve" to rc, "humanize" to humanize,
)

/** The five cutoff-envelope values of a filter node after the fill, in node order. */
private fun IgnitorDsl.envKnobs(): List<Any?> = filterFields().let { f ->
    listOf(f["env"], f["attackSec"], f["decaySec"], f["sustainLevel"], f["releaseSec"])
}

private fun c(v: Double) = IgnitorDsl.Constant(v)

/**
 * The two doors of the four filters, compared node for node (the filter node's own fields, never
 * whole trees, so how either side builds the upstream oscillator cannot mask or fake a
 * difference). Phase 3 step 3d(i): the script door is `door(freq, q = 0.707, configure)` with the
 * knobs on a builder; the Kotlin door is the flat engine-level function the script door calls
 * after the lambda, so the compound fill runs once, in one place. Every row LOOPS over the
 * family, and each row names the node type it expects, so a door that stopped handling a knob
 * goes red on its own name.
 */
class KlangScriptFilterDoorParitySpec : StringSpec({

    fun ks(code: String): IgnitorDsl {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        return engine.execute(code).toObjectOrNull<IgnitorDsl>()!!
    }

    val saw = IgnitorDsl.Sawtooth()
    val modulated = IgnitorDsl.Param("cut", 800.0)

    /** The Kotlin door, by name, with everything named; `passes` is ignored by the band filters. */
    fun kotlinDoor(
        door: String,
        freq: IgnitorDsl = c(800.0),
        q: IgnitorDsl = c(0.707),
        passes: Int = 1,
        analog: IgnitorDsl = c(0.0),
        env: IgnitorDsl? = null,
        a: IgnitorDsl? = null,
        d: IgnitorDsl? = null,
        s: IgnitorDsl? = null,
        r: IgnitorDsl? = null,
        ac: IgnitorDsl? = null,
        dc: IgnitorDsl? = null,
        rc: IgnitorDsl? = null,
        humanize: Boolean = false,
    ): IgnitorDsl = when (door) {
        "lowpass" -> saw.lowpass(freq, q, passes, analog, env, a, d, s, r, ac, dc, rc, humanize)
        "highpass" -> saw.highpass(freq, q, passes, analog, env, a, d, s, r, ac, dc, rc, humanize)
        "bandpass" -> saw.bandpass(freq, q, analog, env, a, d, s, r, ac, dc, rc, humanize)
        "notch" -> saw.notch(freq, q, analog, env, a, d, s, r, ac, dc, rc, humanize)
        else -> error(door)
    }

    val nodeType = mapOf(
        "lowpass" to IgnitorDsl.Lowpass::class,
        "highpass" to IgnitorDsl.Highpass::class,
        "bandpass" to IgnitorDsl.Bandpass::class,
        "notch" to IgnitorDsl.Notch::class,
    )
    val doors = nodeType.keys.toList()

    fun script(door: String, args: String): IgnitorDsl = ks("Osc.saw().$door($args)").also {
        withClue("$door builds its own node") { nodeType.getValue(door).isInstance(it) shouldBe true }
    }

    "every knob of the builder reaches the node identically on both doors, all four filters" {
        for (door in doors) {
            val passes = if (door == "lowpass" || door == "highpass") ".passes(2)" else ""
            val s = script(
                door,
                """Osc.param("cut", 800), 1.2, x => x$passes.analog(4).humanize(1).env(24)""" +
                    """.adsr(0.005, 0.3, 0.2, 0.05, e => e.curves("exp", "square", "cube"))""",
            )
            val k = kotlinDoor(
                door, modulated, c(1.2), passes = 2, analog = c(4.0), env = c(24.0),
                a = c(0.005), d = c(0.3), s = c(0.2), r = c(0.05),
                ac = AdsrCurves.knob(AdsrCurve.Exponential), dc = AdsrCurves.knob(AdsrCurve.Square),
                rc = AdsrCurves.knob(AdsrCurve.Cube), humanize = true,
            )

            withClue(door) { s.filterFields() shouldBe k.filterFields() }
        }
    }

    "a bare call and a call with an empty lambda are the filter without envelope, lane or curves" {
        val defaults = listOf(c(0.0), c(FILTER_ENV_ATTACK_SEC), c(FILTER_ENV_DECAY_SEC), c(FILTER_ENV_SUSTAIN_LEVEL), c(FILTER_ENV_RELEASE_SEC))

        for (door in doors) {
            val k = kotlinDoor(door)

            withClue("$door bare") { script(door, "800").filterFields() shouldBe k.filterFields() }
            withClue("$door x => x") { script(door, "800, x => x").filterFields() shouldBe k.filterFields() }
            withClue("$door envelope off") { k.envKnobs() shouldBe defaults }
            withClue("$door unshaped curves: MOD_ENV_CURVE's knob") {
                k.filterFields()["attackCurve"] shouldBe AdsrCurves.knob(MOD_ENV_CURVE)
                k.filterFields()["decayCurve"] shouldBe AdsrCurves.knob(MOD_ENV_CURVE)
                k.filterFields()["releaseCurve"] shouldBe AdsrCurves.knob(MOD_ENV_CURVE)
            }
        }
    }

    "THE COMPOUND FILL at the end of the lambda: env alone gets the constant stages" {
        val expected = listOf(c(24.0), c(FILTER_ENV_ATTACK_SEC), c(FILTER_ENV_DECAY_SEC), c(FILTER_ENV_SUSTAIN_LEVEL), c(FILTER_ENV_RELEASE_SEC))

        for (door in doors) {
            withClue("$door script") { script(door, "800, x => x.env(24)").envKnobs() shouldBe expected }
            withClue("$door kotlin") { kotlinDoor(door, env = c(24.0)).envKnobs() shouldBe expected }
        }
    }

    "THE COMPOUND FILL: adsr alone switches the envelope on at the constant depth" {
        val expected = listOf(c(FILTER_ENV_DEPTH_SEMITONES), c(0.005), c(0.3), c(0.2), c(0.05))

        for (door in doors) {
            withClue("$door script") { script(door, "800, x => x.adsr(0.005, 0.3, 0.2, 0.05)").envKnobs() shouldBe expected }
            withClue("$door kotlin") {
                kotlinDoor(door, a = c(0.005), d = c(0.3), s = c(0.2), r = c(0.05)).envKnobs() shouldBe expected
            }
        }
    }

    "an explicit env is never overwritten by the fill, in either order inside the lambda" {
        val expected = listOf(c(12.0), c(0.005), c(0.3), c(0.2), c(0.05))

        for (door in doors) {
            withClue("$door env first") { script(door, "800, x => x.env(12).adsr(0.005, 0.3, 0.2, 0.05)").envKnobs() shouldBe expected }
            withClue("$door adsr first") { script(door, "800, x => x.adsr(0.005, 0.3, 0.2, 0.05).env(12)").envKnobs() shouldBe expected }
            withClue("$door env 0 stays off") { script(door, "800, x => x.env(0).adsr(0.005, 0.3, 0.2, 0.05)").envKnobs()[0] shouldBe c(0.0) }
        }
    }

    "the curves live INSIDE adsr's own lambda since step 3c: the builder knob adsrCurves is gone" {
        // One `adsr(a, d, s, r, configure)` shape everywhere (maintainer, 2026-09-25). A curve can
        // therefore only be written by a call that also names the stage, so "a curve alone" no longer
        // exists on this door; the Kotlin door keeps it (a curve with no stage knob leaves the
        // envelope off, the rule of section 4 of the dsl-design skill).
        for (door in doors) {
            withClue(door) {
                shouldThrowAny { ks("""Osc.saw().$door(800, x => x.adsrCurves("exp", "exp", "exp"))""") }
                    .message shouldContain "has no method 'adsrCurves'"
            }
        }

        for (door in doors) {
            withClue("$door kotlin: a curve alone leaves the envelope off") {
                kotlinDoor(door, dc = AdsrCurves.knob(AdsrCurve.Exponential)).envKnobs()[0] shouldBe c(0.0)
            }
        }
    }

    "curves: an omitted argument and an unknown name both mean the default, and an adsr without a lambda is unshaped" {
        val default = AdsrCurves.knob(MOD_ENV_CURVE)

        for (door in doors) {
            // The second call names only the decay: it REPLACES the first call, it does not merge.
            val later = script(
                door, """800, x => x.adsr(0.01, 0.1, 0.5, 0.1, e => e.curves("square", "cube", "scurve").curves(decay = "exp"))""",
            ).filterFields()

            withClue("$door omitted attack is the default") { later["attackCurve"] shouldBe default }
            withClue("$door named stage set") { later["decayCurve"] shouldBe AdsrCurves.knob(AdsrCurve.Exponential) }
            withClue("$door omitted release is the default") { later["releaseCurve"] shouldBe default }

            val unknown = script(door, """800, x => x.adsr(0.01, 0.1, 0.5, 0.1, e => e.curves("bogus", "cube", "square"))""").filterFields()

            withClue("$door unknown name is the default") { unknown["attackCurve"] shouldBe default }
            withClue("$door known names beside it") { unknown["decayCurve"] shouldBe AdsrCurves.knob(AdsrCurve.Cube) }
            withClue("$door known names beside it") { unknown["releaseCurve"] shouldBe AdsrCurves.knob(AdsrCurve.Square) }

            // One adsr call is the whole envelope: a later call without a lambda leaves it unshaped.
            val replaced = script(
                door, """800, x => x.adsr(0.01, 0.1, 0.5, 0.1, e => e.curves("square", "cube", "scurve")).adsr(0.02, 0.2, 0.4, 0.2)""",
            ).filterFields()

            withClue("$door a later adsr replaces the curves") {
                listOf(replaced["attackCurve"], replaced["decayCurve"], replaced["releaseCurve"]) shouldBe listOf(default, default, default)
            }
        }
    }

    "humanize: a flag, a truthy number, and on when called with no argument" {
        for (door in doors) {
            withClue("$door humanize()") { script(door, "800, x => x.humanize()").filterFields()["humanize"] shouldBe true }
            withClue("$door humanize(1)") { script(door, "800, x => x.humanize(1)").filterFields()["humanize"] shouldBe true }
            withClue("$door humanize(0)") { script(door, "800, x => x.humanize(0)").filterFields()["humanize"] shouldBe false }
            withClue("$door humanize(true)") { script(door, "800, x => x.humanize(true)").filterFields()["humanize"] shouldBe true }
        }
    }

    "passes is a knob of lowpass and highpass only, coerced like before" {
        for (door in listOf("lowpass", "highpass")) {
            withClue("$door passes(3)") { script(door, "800, x => x.passes(3)").filterFields()["passes"] shouldBe IgnitorDsl.Constant(3.0) }
            withClue("$door passes(0) coerces to 1") { script(door, "800, x => x.passes(0)").filterFields()["passes"] shouldBe IgnitorDsl.Constant(1.0) }
        }

        for (door in listOf("bandpass", "notch")) {
            withClue("$door offers no passes") { shouldThrowAny { ks("Osc.saw().$door(800, x => x.passes(2))") } }
        }
    }

    "the third positional argument is the lambda: the old lowpass/lpf positional trap is gone" {
        for (door in doors) {
            withClue(door) { shouldThrowAny { ks("Osc.saw().$door(800, 1.2, 2)") } }
        }
    }

    "onepole: a modulated freq works from Kotlin (it was Double-only)" {
        val script = ks("""Osc.saw().onepole(Osc.param("cut", 800))""") as IgnitorDsl.OnePoleLowpass
        val kotlin = IgnitorDsl.Sawtooth().onepole(modulated)
        script.freq shouldBe kotlin.freq
    }

    "the Kotlin scalar overloads wrap in Constant and default the same as the node overloads" {
        val f = c(800.0)

        withClue("lowpass") { IgnitorDsl.Sawtooth().lowpass(800.0).filterFields() shouldBe IgnitorDsl.Sawtooth().lowpass(f).filterFields() }
        withClue("highpass") { IgnitorDsl.Sawtooth().highpass(800.0).filterFields() shouldBe IgnitorDsl.Sawtooth().highpass(f).filterFields() }
        withClue("bandpass") { IgnitorDsl.Sawtooth().bandpass(800.0).filterFields() shouldBe IgnitorDsl.Sawtooth().bandpass(f).filterFields() }
        withClue("notch") { IgnitorDsl.Sawtooth().notch(800.0).filterFields() shouldBe IgnitorDsl.Sawtooth().notch(f).filterFields() }

        withClue("the scalar overload carries the curves and the fill too") {
            IgnitorDsl.Sawtooth().lowpass(800.0, decaySec = 0.3, releaseCurve = AdsrCurve.Cube).filterFields() shouldBe
                IgnitorDsl.Sawtooth().lowpass(f, decaySec = c(0.3), releaseCurve = AdsrCurves.knob(AdsrCurve.Cube)).filterFields()
        }
    }
})
