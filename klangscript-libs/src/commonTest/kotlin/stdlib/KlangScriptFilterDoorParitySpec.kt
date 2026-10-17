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
import io.peekandpoke.klang.audio_bridge.BandFilterBuilder
import io.peekandpoke.klang.audio_bridge.FilterBuilder
import io.peekandpoke.klang.audio_bridge.ModAdsrBuilder
import io.peekandpoke.klang.audio_bridge.bandpass
import io.peekandpoke.klang.audio_bridge.constants.FILTER_ENV_ATTACK_SEC
import io.peekandpoke.klang.audio_bridge.constants.FILTER_ENV_DECAY_SEC
import io.peekandpoke.klang.audio_bridge.constants.FILTER_ENV_DEPTH_SEMITONES
import io.peekandpoke.klang.audio_bridge.constants.FILTER_ENV_RELEASE_SEC
import io.peekandpoke.klang.audio_bridge.constants.FILTER_ENV_SUSTAIN_LEVEL
import io.peekandpoke.klang.audio_bridge.constants.MOD_ENV_CURVE
import io.peekandpoke.klang.audio_bridge.highpass
import io.peekandpoke.klang.audio_bridge.ign
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
    is IgnitorDsl.Lowpass -> fields(freq, q, analog, passes, env, attack, decay, sustain, release, attackCurve, decayCurve, releaseCurve, humanize)
    is IgnitorDsl.Highpass -> fields(freq, q, analog, passes, env, attack, decay, sustain, release, attackCurve, decayCurve, releaseCurve, humanize)
    is IgnitorDsl.Bandpass -> fields(freq, q, analog, IgnitorDsl.Constant(1.0), env, attack, decay, sustain, release, attackCurve, decayCurve, releaseCurve, humanize)
    is IgnitorDsl.Notch -> fields(freq, q, analog, IgnitorDsl.Constant(1.0), env, attack, decay, sustain, release, attackCurve, decayCurve, releaseCurve, humanize)
    else -> error("not a filter node: ${this::class.simpleName}")
}

private fun fields(
    freq: IgnitorDsl, q: IgnitorDsl, analog: IgnitorDsl, passes: IgnitorDsl, env: IgnitorDsl,
    a: IgnitorDsl, d: IgnitorDsl, s: IgnitorDsl, r: IgnitorDsl,
    ac: IgnitorDsl, dc: IgnitorDsl, rc: IgnitorDsl, humanize: Boolean,
): Map<String, Any?> = linkedMapOf(
    "freq" to freq, "q" to q, "analog" to analog, "passes" to passes, "env" to env,
    "attack" to a, "decay" to d, "sustain" to s, "release" to r,
    "attackCurve" to ac, "decayCurve" to dc, "releaseCurve" to rc, "humanize" to humanize,
)

/** The five cutoff-envelope values of a filter node after the fill, in node order. */
private fun IgnitorDsl.envKnobs(): List<Any?> = filterFields().let { f ->
    listOf(f["env"], f["attack"], f["decay"], f["sustain"], f["release"])
}

private fun c(v: Double) = IgnitorDsl.Constant(v)

/**
 * The two doors of the four filters, compared node for node (the filter node's own fields, never
 * whole trees, so how either side builds the upstream oscillator cannot mask or fake a
 * difference). Since the typed-doors rollout S3a both doors have ONE shape, `door(freq, q, configure)`,
 * on ONE set of builders (`FilterBuilder`, `BandFilterBuilder` in `audio_bridge`); the script door
 * converts and calls the Kotlin door, where the compound fill runs once. Parity rows cannot see a
 * builder member that writes the wrong field (both doors share it), so every member knob also has
 * a row against the node WRITTEN OUT with `.copy()`, from both doors, and every Kotlin door a
 * default-node row. Every row LOOPS over the family, and each row names the node type it expects,
 * so a door that stopped handling a knob goes red on its own name.
 */
class KlangScriptFilterDoorParitySpec : StringSpec({

    fun ks(code: String): IgnitorDsl {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        return engine.execute(code).toObjectOrNull<IgnitorDsl>()!!
    }

    val saw = IgnitorDsl.Saw()
    val modulated = IgnitorDsl.Param("cut", 800.0)

    /**
     * The Kotlin door, by name: [configure] runs on the [FilterBuilder] of lowpass and highpass, and its
     * band-filter half [band] on the [BandFilterBuilder] of bandpass and notch (the two builders differ by `passes`).
     */
    fun kotlinDoor(
        door: String,
        freq: IgnitorDsl = c(800.0),
        q: IgnitorDsl? = null,
        configure: (FilterBuilder) -> FilterBuilder = { it },
        band: (BandFilterBuilder) -> BandFilterBuilder = { it },
    ): IgnitorDsl = when (door) {
        "lowpass" -> if (q == null) saw.lowpass(freq = freq, configure = configure) else saw.lowpass(freq = freq, q = q, configure = configure)
        "highpass" -> if (q == null) saw.highpass(freq = freq, configure = configure) else saw.highpass(freq = freq, q = q, configure = configure)
        "bandpass" -> if (q == null) saw.bandpass(freq = freq, configure = band) else saw.bandpass(freq = freq, q = q, configure = band)
        "notch" -> if (q == null) saw.notch(freq = freq, configure = band) else saw.notch(freq = freq, q = q, configure = band)
        else -> error(door)
    }

    /** The same knob on both builders: one call site per row for all four filters. */
    fun kotlinKnob(door: String, knob: (FilterKnobsCall) -> Unit): IgnitorDsl {
        val call = FilterKnobsCall()
        knob(call)

        return kotlinDoor(door, configure = { call.full(it) }, band = { call.band(it) })
    }

    /**
     * Each Kotlin door's bare node, WRITTEN OUT: the cutoff and the default q (`0.707`), every other field the
     * node's own default (no analog, one pass, no envelope, the constant stages, the unshaped curves, no lane).
     */
    fun bare(door: String): IgnitorDsl = when (door) {
        "lowpass" -> IgnitorDsl.Lowpass(inner = saw, freq = c(800.0), q = c(0.707))
        "highpass" -> IgnitorDsl.Highpass(inner = saw, freq = c(800.0), q = c(0.707))
        "bandpass" -> IgnitorDsl.Bandpass(inner = saw, freq = c(800.0), q = c(0.707))
        "notch" -> IgnitorDsl.Notch(inner = saw, freq = c(800.0), q = c(0.707))
        else -> error(door)
    }

    /** [bare] with the envelope and lane fields written out, through each node's own `copy`. */
    fun written(
        door: String,
        analog: IgnitorDsl? = null,
        passes: IgnitorDsl? = null,
        env: IgnitorDsl? = null,
        stages: List<IgnitorDsl>? = null,
        curves: List<IgnitorDsl>? = null,
        humanize: Boolean? = null,
    ): IgnitorDsl {
        val n = bare(door)
        val f = n.filterFields()
        val a = analog ?: f["analog"] as IgnitorDsl
        val e = env ?: f["env"] as IgnitorDsl
        val st = stages ?: listOf(f["attack"], f["decay"], f["sustain"], f["release"]).map { it as IgnitorDsl }
        val cv = curves ?: listOf(f["attackCurve"], f["decayCurve"], f["releaseCurve"]).map { it as IgnitorDsl }
        val h = humanize ?: (f["humanize"] as Boolean)

        return when (n) {
            is IgnitorDsl.Lowpass -> n.copy(
                analog = a, passes = passes ?: n.passes, env = e, attack = st[0], decay = st[1], sustain = st[2], release = st[3],
                attackCurve = cv[0], decayCurve = cv[1], releaseCurve = cv[2], humanize = h,
            )

            is IgnitorDsl.Highpass -> n.copy(
                analog = a, passes = passes ?: n.passes, env = e, attack = st[0], decay = st[1], sustain = st[2], release = st[3],
                attackCurve = cv[0], decayCurve = cv[1], releaseCurve = cv[2], humanize = h,
            )

            is IgnitorDsl.Bandpass -> n.copy(
                analog = a, env = e, attack = st[0], decay = st[1], sustain = st[2], release = st[3],
                attackCurve = cv[0], decayCurve = cv[1], releaseCurve = cv[2], humanize = h,
            )

            is IgnitorDsl.Notch -> n.copy(
                analog = a, env = e, attack = st[0], decay = st[1], sustain = st[2], release = st[3],
                attackCurve = cv[0], decayCurve = cv[1], releaseCurve = cv[2], humanize = h,
            )

            else -> error(door)
        }
    }

    val nodeType = mapOf(
        "lowpass" to IgnitorDsl.Lowpass::class,
        "highpass" to IgnitorDsl.Highpass::class,
        "bandpass" to IgnitorDsl.Bandpass::class,
        "notch" to IgnitorDsl.Notch::class,
    )
    val doors = nodeType.keys.toList()
    val constantStages = listOf(c(FILTER_ENV_ATTACK_SEC), c(FILTER_ENV_DECAY_SEC), c(FILTER_ENV_SUSTAIN_LEVEL), c(FILTER_ENV_RELEASE_SEC))

    fun script(door: String, args: String): IgnitorDsl = ks("Ignitor.saw().$door($args)").also {
        withClue("$door builds its own node") { nodeType.getValue(door).isInstance(it) shouldBe true }
    }

    "every knob of the builder reaches the node identically on both doors, all four filters" {
        for (door in doors) {
            val passes = if (door == "lowpass" || door == "highpass") ".passes(2)" else ""
            val s = script(
                door,
                """Ignitor.param("cut", 800), 1.2, x => x$passes.analog(4).humanize(1).env(24)""" +
                    """.adsr(0.005, 0.3, 0.2, 0.05, e => e.curves("exp", "square", "cube"))""",
            )
            val k = kotlinDoor(
                door, modulated, c(1.2),
                configure = {
                    it.passes(2.0.ign).analog(4.ign).humanize().env(24.ign)
                        .adsr(0.005.ign, 0.3.ign, 0.2.ign, 0.05.ign) { e -> e.curves("exp", "square", "cube") }
                },
                band = {
                    it.analog(4.ign).humanize().env(24.ign)
                        .adsr(0.005.ign, 0.3.ign, 0.2.ign, 0.05.ign) { e -> e.curves("exp", "square", "cube") }
                },
            )

            withClue(door) { s.filterFields() shouldBe k.filterFields() }
        }
    }

    "the default node of each Kotlin door, written out; the script door's bare call is the same node" {
        for (door in doors) {
            withClue("$door kotlin bare") { kotlinDoor(door) shouldBe bare(door) }
            withClue("$door kotlin x => x") { kotlinDoor(door, configure = { it }, band = { it }) shouldBe bare(door) }
            withClue("$door script bare") { script(door, "800").filterFields() shouldBe bare(door).filterFields() }
            withClue("$door script x => x") { script(door, "800, x => x").filterFields() shouldBe bare(door).filterFields() }
            withClue("$door envelope off") { bare(door).envKnobs() shouldBe listOf(c(0.0)) + constantStages }
            withClue("$door one pass, the literal") { bare(door).filterFields()["passes"] shouldBe c(1.0) }
            withClue("$door unshaped curves: MOD_ENV_CURVE's knob") {
                bare(door).filterFields()["attackCurve"] shouldBe AdsrCurves.knob(MOD_ENV_CURVE)
            }
        }
    }

    "each member knob writes its own field, against the node written out, from both doors" {
        // Distinct values off every default (stages 0.02, 0.3, 0.4, 0.5 against the constants 0.01, 0.1, 1, 0.1).
        val stages = listOf(c(0.02), c(0.3), c(0.4), c(0.5))
        val shaped = listOf(AdsrCurves.knob(AdsrCurve.Linear), AdsrCurves.knob(AdsrCurve.Cube), AdsrCurves.knob(AdsrCurve.SCurve))
        val unshaped = List(3) { AdsrCurves.knob(MOD_ENV_CURVE) }

        for (door in doors) {
            fun row(knob: String, scriptKnob: String, kotlin: (FilterKnobsCall) -> Unit, expected: IgnitorDsl) {
                withClue("$door $knob from Kotlin") { kotlinKnob(door, kotlin) shouldBe expected }
                withClue("$door $knob from the script") { script(door, "800, x => x.$scriptKnob").filterFields() shouldBe expected.filterFields() }
                withClue("$door $knob moves the node off its default") { (expected == bare(door)) shouldBe false }
            }

            row("analog", "analog(4)", { it.analog = 4.ign }, written(door, analog = c(4.0)))
            row("humanize", "humanize()", { it.humanize = true }, written(door, humanize = true))
            row("env", "env(24)", { it.env = 24.ign }, written(door, env = c(24.0)))
            row(
                "adsr", "adsr(0.02, 0.3, 0.4, 0.5)", { it.adsr = stages },
                written(door, env = c(FILTER_ENV_DEPTH_SEMITONES), stages = stages, curves = unshaped),
            )
            row(
                "adsr curves (index)", "adsr(0.02, 0.3, 0.4, 0.5, e => e.curves(0, 2, 3))", { it.adsr = stages; it.curves = shaped },
                written(door, env = c(FILTER_ENV_DEPTH_SEMITONES), stages = stages, curves = shaped),
            )
            row(
                "adsr curves (names)", """adsr(0.02, 0.3, 0.4, 0.5, e => e.curves("lin", "cube", "scurve"))""",
                { it.adsr = stages; it.curveNames = listOf("lin", "cube", "scurve") },
                written(door, env = c(FILTER_ENV_DEPTH_SEMITONES), stages = stages, curves = shaped),
            )

            if (door == "lowpass" || door == "highpass") {
                row("passes", "passes(3)", { it.passes = c(3.0) }, written(door, passes = c(3.0)))
                row(
                    "passes, a slot", """passes(Ignitor.param("np", 2))""", { it.passes = IgnitorDsl.Param("np", 2.0) },
                    written(door, passes = IgnitorDsl.Param("np", 2.0)),
                )
            }
        }
    }

    "curves: an omitted argument is MOD_ENV_CURVE, on both Kotlin forms (index and name) and on the script" {
        val stages = listOf(c(0.02), c(0.3), c(0.4), c(0.5))
        val mod = AdsrCurves.knob(MOD_ENV_CURVE)
        val linear = AdsrCurves.knob(AdsrCurve.Linear)

        for (door in listOf("lowpass", "notch")) {
            val expected = written(door, env = c(FILTER_ENV_DEPTH_SEMITONES), stages = stages, curves = listOf(linear, mod, mod))
            val (a, d, s, r) = stages

            withClue("$door script curves(\"lin\")") {
                script(door, """800, x => x.adsr(0.02, 0.3, 0.4, 0.5, e => e.curves("lin"))""").filterFields() shouldBe
                    expected.filterFields()
            }
            withClue("$door kotlin curves(\"lin\")") {
                kotlinDoor(
                    door,
                    configure = { it.adsr(attack = a, decay = d, sustain = s, release = r) { e -> e.curves("lin") } },
                    band = { it.adsr(attack = a, decay = d, sustain = s, release = r) { e -> e.curves("lin") } },
                ) shouldBe expected
            }
            withClue("$door kotlin curves(attack = knob(Linear))") {
                kotlinDoor(
                    door,
                    configure = { it.adsr(attack = a, decay = d, sustain = s, release = r) { e -> e.curves(attack = linear) } },
                    band = { it.adsr(attack = a, decay = d, sustain = s, release = r) { e -> e.curves(attack = linear) } },
                ) shouldBe expected
            }
        }
    }

    "THE COMPOUND FILL at the end of the lambda: env alone gets the constant stages, on both doors" {
        for (door in doors) {
            val expected = written(door, env = c(24.0), stages = constantStages)

            withClue("$door script") { script(door, "800, x => x.env(24)").filterFields() shouldBe expected.filterFields() }
            withClue("$door kotlin") { kotlinKnob(door) { it.env = 24.ign } shouldBe expected }
        }
    }

    "THE COMPOUND FILL: adsr alone switches the envelope on at the constant depth, on both doors" {
        val stages = listOf(c(0.005), c(0.3), c(0.2), c(0.05))

        for (door in doors) {
            val expected = written(door, env = c(FILTER_ENV_DEPTH_SEMITONES), stages = stages)

            withClue("$door script") { script(door, "800, x => x.adsr(0.005, 0.3, 0.2, 0.05)").filterFields() shouldBe expected.filterFields() }
            withClue("$door kotlin") { kotlinKnob(door) { it.adsr = stages } shouldBe expected }
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
        // exists on either door (the Kotlin door's flat `attackCurve` left with the typed-doors rollout S3a).
        for (door in doors) {
            withClue(door) {
                shouldThrowAny { ks("""Ignitor.saw().$door(800, x => x.adsrCurves("exp", "exp", "exp"))""") }
                    .message shouldContain "has no method 'adsrCurves'"
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

        // The Kotlin member is a typed Boolean, on by default.
        saw.lowpass(800.ign) { it.humanize(false) } shouldBe bare("lowpass")
        saw.notch(800.ign) { it.humanize(on = false) } shouldBe bare("notch")
    }

    "passes is a knob of lowpass and highpass only, coerced like before" {
        for (door in listOf("lowpass", "highpass")) {
            withClue("$door passes(3)") { script(door, "800, x => x.passes(3)").filterFields()["passes"] shouldBe IgnitorDsl.Constant(3.0) }
            withClue("$door passes(0) coerces to 1") { script(door, "800, x => x.passes(0)").filterFields()["passes"] shouldBe IgnitorDsl.Constant(1.0) }
        }

        // The coercion is the Kotlin door's (the script door calls it): rounded, 1..16.
        saw.lowpass(800.ign) { it.passes(0.0.ign) } shouldBe bare("lowpass")
        (saw.highpass(800.ign) { it.passes(2.6.ign) }).passes shouldBe IgnitorDsl.Constant(3.0)
        (saw.lowpass(800.ign) { it.passes(99.0.ign) }).passes shouldBe IgnitorDsl.Constant(16.0)

        for (door in listOf("bandpass", "notch")) {
            withClue("$door offers no passes") { shouldThrowAny { ks("Ignitor.saw().$door(800, x => x.passes(2))") } }
        }
    }

    "the third positional argument is the lambda: the old lowpass/lpf positional trap is gone" {
        for (door in doors) {
            withClue(door) { shouldThrowAny { ks("Ignitor.saw().$door(800, 1.2, 2)") } }
        }
    }

    "onepole: one door, the script converts and calls it; its default node written out" {
        val script = ks("""Ignitor.saw().onepole(Ignitor.param("cut", 800))""")
        script shouldBe IgnitorDsl.Saw().onepole(modulated)
        IgnitorDsl.Saw().onepole(800.ign) shouldBe IgnitorDsl.OnePoleLowpass(inner = saw, freq = c(800.0))
        ks("Ignitor.saw().onepole(800)") shouldBe IgnitorDsl.OnePoleLowpass(inner = saw, freq = c(800.0))
    }
})

/**
 * One knob call per row for the two filter builders: the Kotlin rows set a field here, and [full] / [band] replay it
 * on the builder of the door (only `passes` is lowpass and highpass only).
 */
private class FilterKnobsCall {
    var passes: IgnitorDsl? = null
    var analog: IgnitorDsl? = null
    var humanize: Boolean = false
    var env: IgnitorDsl? = null
    var adsr: List<IgnitorDsl>? = null
    var curves: List<IgnitorDsl>? = null
    var curveNames: List<String>? = null

    private fun shape(e: ModAdsrBuilder): ModAdsrBuilder {
        val byIndex = curves
        val byName = curveNames

        return when {
            byIndex != null -> e.curves(attack = byIndex[0], decay = byIndex[1], release = byIndex[2])
            byName != null -> e.curves(attack = byName[0], decay = byName[1], release = byName[2])
            else -> e
        }
    }

    fun full(b: FilterBuilder): FilterBuilder {
        var x = b
        passes?.let { x = x.passes(n = it) }
        analog?.let { x = x.analog(character = it) }
        if (humanize) {
            x = x.humanize()
        }
        env?.let { x = x.env(semitones = it) }
        adsr?.let { st -> x = x.adsr(attack = st[0], decay = st[1], sustain = st[2], release = st[3]) { e -> shape(e) } }

        return x
    }

    fun band(b: BandFilterBuilder): BandFilterBuilder {
        var x = b
        analog?.let { x = x.analog(character = it) }
        if (humanize) {
            x = x.humanize()
        }
        env?.let { x = x.env(semitones = it) }
        adsr?.let { st -> x = x.adsr(attack = st[0], decay = st[1], sustain = st[2], release = st[3]) { e -> shape(e) } }

        return x
    }
}
