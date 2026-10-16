/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_bridge.AdsrBuilder
import io.peekandpoke.klang.audio_bridge.FmBuilder
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.PhaserBuilder
import io.peekandpoke.klang.audio_bridge.PitchEnvelopeBuilder
import io.peekandpoke.klang.audio_bridge.TremoloBuilder
import io.peekandpoke.klang.audio_bridge.accelerate
import io.peekandpoke.klang.audio_bridge.adsr
import io.peekandpoke.klang.audio_bridge.coarse
import io.peekandpoke.klang.audio_bridge.crush
import io.peekandpoke.klang.audio_bridge.detune
import io.peekandpoke.klang.audio_bridge.distort
import io.peekandpoke.klang.audio_bridge.drive
import io.peekandpoke.klang.audio_bridge.fm
import io.peekandpoke.klang.audio_bridge.ign
import io.peekandpoke.klang.audio_bridge.octaveDown
import io.peekandpoke.klang.audio_bridge.octaveUp
import io.peekandpoke.klang.audio_bridge.phaser
import io.peekandpoke.klang.audio_bridge.pitchEnvelope
import io.peekandpoke.klang.audio_bridge.pitchMod
import io.peekandpoke.klang.audio_bridge.pitchModSemitones
import io.peekandpoke.klang.audio_bridge.shape
import io.peekandpoke.klang.audio_bridge.shimmer
import io.peekandpoke.klang.audio_bridge.tremolo
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.toObjectOrNull

private fun c(v: Double) = IgnitorDsl.Constant(v)

/**
 * One row: the Kotlin door's call, the script door's call (after `Ignitor.saw()`), and the node it must build,
 * WRITTEN OUT with the node's own `.copy()` from [base], never through a builder.
 */
private class Row(
    val name: String,
    val kotlin: () -> IgnitorDsl,
    val script: String,
    val expected: IgnitorDsl,
    val base: IgnitorDsl? = null,
)

/**
 * The two doors of the effect family of the typed-doors rollout S3b and S3c (`docs/tasks/kotlin-typed-doors.md`):
 * `adsr`, `fm`, `pitchEnvelope`, `phaser`, `shimmer` and `tremolo` on their shared builders in `audio_bridge`
 * (`AdsrBuilder`, `FmBuilder`, `PitchEnvelopeBuilder`, `PhaserBuilder`, `ShimmerBuilder`, `TremoloBuilder`), and the
 * retyped doors (`detune`, `octaveUp`, `octaveDown`, `drive`, `shape` and `distort` with their name forms, `crush`, `coarse`, `accelerate`,
 * `pitchMod`, `pitchModSemitones`). The script door converts and calls the Kotlin door, so a parity row alone cannot
 * see a member that writes the wrong field or a wrong default symbol both doors share: every Kotlin door has a
 * default-node row and every member knob a row against the node written out with LITERAL numbers (never a constant),
 * each run from both doors, with a guard that the row moves its knob off the default. The pitch envelope's default
 * fill (`PitchEnvelopeBuilder.adsr`, its one home) has rows of its own through both doors.
 */
class KlangScriptEffectDoorParitySpec : StringSpec({

    fun ks(code: String): IgnitorDsl {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        return engine.execute(code).toObjectOrNull<IgnitorDsl>()!!
    }

    val saw = IgnitorDsl.Saw()
    val sine = IgnitorDsl.Sine()

    // ── The default nodes, written out with literals ─────────────────────────────────────────────────────────────

    // The amplitude envelope: the four stages as given, the curves exponential (index 5), the declick slot.
    val adsrBase = IgnitorDsl.Adsr(
        inner = saw, attack = c(0.01), decay = c(0.2), sustain = c(0.5), release = c(0.3),
        attackCurve = c(5.0), decayCurve = c(5.0), releaseCurve = c(5.0), declick = IgnitorDsl.Slots.declick,
    )
    val adsrK = { cfg: ((AdsrBuilder) -> AdsrBuilder)? ->
        saw.adsr(attack = 0.01.ign, decay = 0.2.ign, sustain = 0.5.ign, release = 0.3.ign, configure = cfg)
    }
    val adsrS = "adsr(0.01, 0.2, 0.5, 0.3"

    // The FM: a constant depth (attack 0, decay 0, sustain 1, release 0).
    val fmBase = IgnitorDsl.Fm(
        carrier = saw, modulator = sine, ratio = c(2.0), depth = c(300.0),
        attack = c(0.0), decay = c(0.0), sustain = c(1.0), release = c(0.0),
    )
    val fmK = { cfg: ((FmBuilder) -> FmBuilder)? ->
        saw.fm(modulator = sine, ratio = 2.ign, depth = 300.ign, configure = cfg)
    }
    val fmS = "fm(Ignitor.sine(), 2, 300"

    // The pitch envelope: stages 0.01, 0.1, 0, 0, the curves exponential (index 5, MOD_ENV_CURVE).
    val penvBase = IgnitorDsl.PitchEnvelope(
        inner = saw, semitones = c(12.0), attack = c(0.01), decay = c(0.1), sustain = c(0.0), release = c(0.0),
        attackCurve = c(5.0), decayCurve = c(5.0), releaseCurve = c(5.0),
    )
    val penvK = { cfg: ((PitchEnvelopeBuilder) -> PitchEnvelopeBuilder)? ->
        saw.pitchEnvelope(semitones = 12.ign, configure = cfg)
    }
    val penvS = "pitchEnvelope(12"
    val penvStages = penvBase.copy(attack = c(0.002), decay = c(0.05), sustain = c(0.25), release = c(0.1))

    // The phaser: center and sweep 1000 Hz, no floor.
    val phaserBase = IgnitorDsl.Phaser(inner = saw, rate = c(0.7), wet = c(0.3), center = c(1000.0), sweep = c(1000.0), floor = c(0.0))
    val phaserK = { cfg: ((PhaserBuilder) -> PhaserBuilder)? ->
        saw.phaser(wet = 0.3.ign, rate = 0.7.ign, configure = cfg)
    }
    val phaserS = "phaser(0.3, 0.7"

    // The shimmer: wet 0.5, feedback 0.5, tone 4000 Hz, root, fifth and octave, no floor.
    val shimmerBase = IgnitorDsl.Shimmer(
        inner = saw, wet = c(0.5), feedback = c(0.5), pitches = listOf(0.0, 7.0, 12.0), tone = c(4000.0), floor = c(0.0),
    )

    // The tremolo: a sine LFO (index 0), the classic dip (-1, 0).
    val tremoloBase = IgnitorDsl.Tremolo(inner = saw, rate = c(4.0), depth = c(0.3), shape = c(0.0), rangeFrom = c(-1.0), rangeTo = c(0.0))
    val tremoloK = { cfg: ((TremoloBuilder) -> TremoloBuilder)? ->
        saw.tremolo(rate = 4.ign, depth = 0.3.ign, configure = cfg)
    }
    val tremoloS = "tremolo(4, 0.3"

    // ── Default-node rows: every Kotlin door's bare call, and the script's, against the written-out node ───────────

    val defaults = listOf(
        Row("adsr", { adsrK(null) }, "$adsrS)", adsrBase),
        Row("fm", { fmK(null) }, "$fmS)", fmBase),
        Row("pitchEnvelope", { penvK(null) }, "$penvS)", penvBase),
        Row("phaser", { phaserK(null) }, "$phaserS)", phaserBase),
        Row("shimmer", { saw.shimmer() }, "shimmer()", shimmerBase),
        Row("tremolo", { tremoloK(null) }, "$tremoloS)", tremoloBase),
        Row("detune", { saw.detune(7.ign) }, "detune(7)", IgnitorDsl.Detune(inner = saw, semitones = c(7.0))),
        Row("octaveUp", { saw.octaveUp() }, "octaveUp()", IgnitorDsl.Detune(inner = saw, semitones = c(12.0))),
        Row("octaveDown", { saw.octaveDown() }, "octaveDown()", IgnitorDsl.Detune(inner = saw, semitones = c(-12.0))),
        Row("drive", { saw.drive(0.4.ign) }, "drive(0.4)", IgnitorDsl.Drive(inner = saw, amount = c(0.4))),
        Row(
            "distort", { saw.distort(amount = 0.4.ign) }, "distort(0.4)",
            IgnitorDsl.Shape(inner = IgnitorDsl.Drive(inner = saw, amount = c(0.4)), shape = c(0.0), oversample = c(0.0)),
        ),
        Row("shape", { saw.shape() }, "shape()", IgnitorDsl.Shape(inner = saw, shape = c(0.0), oversample = c(0.0))),
        Row("crush", { saw.crush(6.ign) }, "crush(6)", IgnitorDsl.Crush(inner = saw, bits = c(6.0))),
        Row("coarse", { saw.coarse(3.ign) }, "coarse(3)", IgnitorDsl.Coarse(inner = saw, factor = c(3.0))),
        Row("accelerate", { saw.accelerate(5.ign) }, "accelerate(5)", IgnitorDsl.Accelerate(inner = saw, semitones = c(5.0))),
        Row("pitchMod", { saw.pitchMod(0.25.ign) }, "pitchMod(0.25)", IgnitorDsl.PitchMod(inner = saw, mod = c(0.25))),
        Row(
            "pitchModSemitones", { saw.pitchModSemitones(7.ign) }, "pitchModSemitones(7)",
            IgnitorDsl.PitchModSemitones(inner = saw, mod = c(7.0)),
        ),
    )

    for (row in defaults) {
        "default node: ${row.name}, from the Kotlin door and the script door" {
            withClue("Kotlin") { row.kotlin() shouldBe row.expected }
            withClue("script") { ks("Ignitor.saw().${row.script}") shouldBe row.expected }
        }
    }

    // ── Door inputs: every argument off its default, a number and a signal, from both doors ──────────────────────

    val p = IgnitorDsl.Param("p", 0.5)
    val driven = IgnitorDsl.Drive(inner = saw, amount = c(0.4))
    fun shapeOf(inner: IgnitorDsl, shape: IgnitorDsl, oversample: IgnitorDsl) = IgnitorDsl.Shape(inner = inner, shape = shape, oversample = oversample)
    val inputs = listOf(
        Row(
            "phaser center and sweep",
            { saw.phaser(wet = 0.3.ign, rate = 0.7.ign, center = 800.ign, sweep = 600.ign) }, "phaser(0.3, 0.7, 800, 600)",
            phaserBase.copy(center = c(800.0), sweep = c(600.0)),
        ),
        Row(
            "shimmer wet, feedback, tone and pitches",
            { saw.shimmer(wet = 0.3.ign, feedback = 0.4.ign, tone = 3000.ign, pitches = listOf(0.0, 4.0, 7.0)) },
            "shimmer(0.3, 0.4, 3000, [0, 4, 7])",
            shimmerBase.copy(wet = c(0.3), feedback = c(0.4), tone = c(3000.0), pitches = listOf(0.0, 4.0, 7.0)),
        ),
        Row(
            "distort shape and oversample",
            { saw.distort(amount = 0.4.ign, shape = "hard", oversample = 4.ign) }, "distort(0.4, \"hard\", 4)",
            IgnitorDsl.Shape(inner = IgnitorDsl.Drive(inner = saw, amount = c(0.4)), shape = c(1.0), oversample = c(4.0)),
        ),
        // shape and distort: the index form and the name form, each with a slot for `shape` and for `oversample`
        Row(
            "shape, the index form, a slot oversample", { saw.shape(shape = 6.ign, oversample = IgnitorDsl.Param("os", 4.0)) },
            "shape(6, Ignitor.param(\"os\", 4))", shapeOf(saw, c(6.0), IgnitorDsl.Param("os", 4.0)),
        ),
        Row(
            "shape, the index form, a slot shape", { saw.shape(shape = IgnitorDsl.Param("sh", 3.0)) },
            "shape(Ignitor.param(\"sh\", 3))", shapeOf(saw, IgnitorDsl.Param("sh", 3.0), c(0.0)),
        ),
        Row("shape, the name form", { saw.shape(shape = "tube", oversample = 2.ign) }, "shape(\"tube\", 2)", shapeOf(saw, c(10.0), c(2.0))),
        Row("shape, the name form, its default oversample", { saw.shape(shape = "hard") }, "shape(\"hard\")", shapeOf(saw, c(1.0), c(0.0))),
        Row(
            "shape, the name form, a slot oversample", { saw.shape(shape = "tube", oversample = IgnitorDsl.Param("os", 4.0)) },
            "shape(\"tube\", Ignitor.param(\"os\", 4))", shapeOf(saw, c(10.0), IgnitorDsl.Param("os", 4.0)),
        ),
        Row("shape, an unknown name is soft", { saw.shape(shape = "wobble", oversample = 2.ign) }, "shape(\"wobble\", 2)", shapeOf(saw, c(0.0), c(2.0))),
        Row(
            "distort, the index form, a slot shape", { saw.distort(amount = 0.4.ign, shape = IgnitorDsl.Param("sh", 3.0), oversample = 8.ign) },
            "distort(0.4, Ignitor.param(\"sh\", 3), 8)", shapeOf(driven, IgnitorDsl.Param("sh", 3.0), c(8.0)),
        ),
        Row(
            "distort, the index form, a slot oversample", { saw.distort(amount = 0.4.ign, shape = 5.ign, oversample = IgnitorDsl.Param("os", 4.0)) },
            "distort(0.4, 5, Ignitor.param(\"os\", 4))", shapeOf(driven, c(5.0), IgnitorDsl.Param("os", 4.0)),
        ),
        Row(
            "distort, the name form, a slot oversample", { saw.distort(amount = 0.4.ign, shape = "tube", oversample = IgnitorDsl.Param("os", 4.0)) },
            "distort(0.4, \"tube\", Ignitor.param(\"os\", 4))", shapeOf(driven, c(10.0), IgnitorDsl.Param("os", 4.0)),
        ),
        Row("distort, the name form, its default oversample", { saw.distort(amount = 0.4.ign, shape = "hard") }, "distort(0.4, \"hard\")", shapeOf(driven, c(1.0), c(0.0))),
        Row("adsr, a signal stage", { saw.adsr(attack = p, decay = 0.2.ign, sustain = 0.5.ign, release = 0.3.ign) }, "adsr(Ignitor.param(\"p\", 0.5), 0.2, 0.5, 0.3)", adsrBase.copy(attack = p)),
        Row("fm, a signal depth", { saw.fm(modulator = sine, ratio = 2.ign, depth = p) }, "fm(Ignitor.sine(), 2, Ignitor.param(\"p\", 0.5))", fmBase.copy(depth = p)),
        Row("pitchEnvelope, a signal depth", { saw.pitchEnvelope(p) }, "pitchEnvelope(Ignitor.param(\"p\", 0.5))", penvBase.copy(semitones = p)),
        Row("phaser, a signal wet", { saw.phaser(wet = p, rate = 0.7.ign) }, "phaser(Ignitor.param(\"p\", 0.5), 0.7)", phaserBase.copy(wet = p)),
        Row("shimmer, a signal tone", { saw.shimmer(tone = p) }, "shimmer(0.5, 0.5, Ignitor.param(\"p\", 0.5))", shimmerBase.copy(tone = p)),
        Row("tremolo, a signal depth", { saw.tremolo(rate = 4.ign, depth = p) }, "tremolo(4, Ignitor.param(\"p\", 0.5))", tremoloBase.copy(depth = p)),
        Row("detune, a signal", { saw.detune(p) }, "detune(Ignitor.param(\"p\", 0.5))", IgnitorDsl.Detune(inner = saw, semitones = p)),
        Row("drive, a signal", { saw.drive(p) }, "drive(Ignitor.param(\"p\", 0.5))", IgnitorDsl.Drive(inner = saw, amount = p)),
        Row("crush, a signal", { saw.crush(p) }, "crush(Ignitor.param(\"p\", 0.5))", IgnitorDsl.Crush(inner = saw, bits = p)),
        Row("coarse, a signal", { saw.coarse(p) }, "coarse(Ignitor.param(\"p\", 0.5))", IgnitorDsl.Coarse(inner = saw, factor = p)),
        Row("accelerate, a signal", { saw.accelerate(p) }, "accelerate(Ignitor.param(\"p\", 0.5))", IgnitorDsl.Accelerate(inner = saw, semitones = p)),
        Row("pitchMod, a signal", { saw.pitchMod(p) }, "pitchMod(Ignitor.param(\"p\", 0.5))", IgnitorDsl.PitchMod(inner = saw, mod = p)),
        Row(
            "pitchModSemitones, a signal", { saw.pitchModSemitones(p) }, "pitchModSemitones(Ignitor.param(\"p\", 0.5))",
            IgnitorDsl.PitchModSemitones(inner = saw, mod = p),
        ),
    )

    for (row in inputs) {
        "door inputs: ${row.name}, from the Kotlin door and the script door" {
            withClue("Kotlin") { row.kotlin() shouldBe row.expected }
            withClue("script") { ks("Ignitor.saw().${row.script}") shouldBe row.expected }
        }
    }

    // ── Member knobs: one row each, against the node written out with .copy() ─────────────────────────────────────

    val members = listOf(
        // AdsrBuilder
        Row(
            "AdsrBuilder.curves, the index form", { adsrK { it.curves(attack = 0.ign, decay = 1.ign, release = 2.ign) } },
            "$adsrS, e => e.curves(0, 1, 2))", adsrBase.copy(attackCurve = c(0.0), decayCurve = c(1.0), releaseCurve = c(2.0)), adsrBase,
        ),
        Row(
            "AdsrBuilder.curves, the name form", { adsrK { it.curves("lin", "sq", "cube") } },
            "$adsrS, e => e.curves(\"lin\", \"sq\", \"cube\"))", adsrBase.copy(attackCurve = c(0.0), decayCurve = c(1.0), releaseCurve = c(2.0)), adsrBase,
        ),
        Row(
            "AdsrBuilder.curves, the name form's omitted stages are exponential", { adsrK { it.curves("lin") } },
            "$adsrS, e => e.curves(\"lin\"))", adsrBase.copy(attackCurve = c(0.0)), adsrBase,
        ),
        Row(
            "AdsrBuilder.curves, the index form's omitted stages are exponential", { adsrK { it.curves(attack = 3.ign) } },
            "$adsrS, e => e.curves(3))", adsrBase.copy(attackCurve = c(3.0)), adsrBase,
        ),
        Row(
            "AdsrBuilder.curves, a slot", { adsrK { it.curves(decay = IgnitorDsl.Param("dc", 4.0)) } },
            "$adsrS, e => e.curves(null, Ignitor.param(\"dc\", 4)))", adsrBase.copy(decayCurve = IgnitorDsl.Param("dc", 4.0)), adsrBase,
        ),
        Row(
            "AdsrBuilder.declick", { adsrK { it.declick(0.002.ign) } },
            "$adsrS, e => e.declick(0.002))", adsrBase.copy(declick = c(0.002)), adsrBase,
        ),
        // FmBuilder
        Row(
            "FmBuilder.adsr", { fmK { it.adsr(attack = 0.01.ign, decay = 0.2.ign, sustain = 0.3.ign, release = 0.4.ign) } },
            "$fmS, x => x.adsr(0.01, 0.2, 0.3, 0.4))", fmBase.copy(attack = c(0.01), decay = c(0.2), sustain = c(0.3), release = c(0.4)), fmBase,
        ),
        // PitchEnvelopeBuilder
        Row(
            "PitchEnvelopeBuilder.adsr, the stages", { penvK { it.adsr(attack = 0.002.ign, decay = 0.05.ign, sustain = 0.25.ign, release = 0.1.ign) } },
            "$penvS, x => x.adsr(0.002, 0.05, 0.25, 0.1))", penvStages, penvBase,
        ),
        Row(
            "PitchEnvelopeBuilder.adsr, its curves", {
                penvK { it.adsr(attack = 0.002.ign, decay = 0.05.ign, sustain = 0.25.ign, release = 0.1.ign) { e -> e.curves("lin", "sq", "cube") } }
            },
            "$penvS, x => x.adsr(0.002, 0.05, 0.25, 0.1, e => e.curves(\"lin\", \"sq\", \"cube\")))",
            penvStages.copy(attackCurve = c(0.0), decayCurve = c(1.0), releaseCurve = c(2.0)), penvBase,
        ),
        // PhaserBuilder, ShimmerBuilder
        Row("PhaserBuilder.floor", { phaserK { it.floor(0.2.ign) } }, "$phaserS, x => x.floor(0.2))", phaserBase.copy(floor = c(0.2)), phaserBase),
        Row(
            "ShimmerBuilder.floor", { saw.shimmer { it.floor(0.2.ign) } }, "shimmer(0.5, 0.5, 4000, [0, 7, 12], x => x.floor(0.2))",
            shimmerBase.copy(floor = c(0.2)), shimmerBase,
        ),
        // TremoloBuilder
        Row(
            "TremoloBuilder.range", { tremoloK { it.range(from = 0.ign, to = 1.ign) } }, "$tremoloS, x => x.range(0, 1))",
            tremoloBase.copy(rangeFrom = c(0.0), rangeTo = c(1.0)), tremoloBase,
        ),
        Row("TremoloBuilder.shape, the index form", { tremoloK { it.shape(index = 2.ign) } }, "$tremoloS, x => x.shape(2))", tremoloBase.copy(shape = c(2.0)), tremoloBase),
        Row("TremoloBuilder.shape, the name form", { tremoloK { it.shape("square") } }, "$tremoloS, x => x.shape(\"square\"))", tremoloBase.copy(shape = c(2.0)), tremoloBase),
        Row("TremoloBuilder.shape, an alias", { tremoloK { it.shape("saw") } }, "$tremoloS, x => x.shape(\"saw\"))", tremoloBase.copy(shape = c(3.0)), tremoloBase),
        Row(
            "TremoloBuilder.shape, a slot", { tremoloK { it.shape(index = IgnitorDsl.Param("ts", 1.0)) } },
            "$tremoloS, x => x.shape(Ignitor.param(\"ts\", 1)))", tremoloBase.copy(shape = IgnitorDsl.Param("ts", 1.0)), tremoloBase,
        ),
    )

    for (row in members) {
        "member knob: ${row.name}, from the Kotlin door and the script door, against the written-out node" {
            withClue("the row moves its knob off the default") { row.expected shouldNotBe row.base }
            withClue("Kotlin") { row.kotlin() shouldBe row.expected }
            withClue("script") { ks("Ignitor.saw().${row.script}") shouldBe row.expected }
        }
    }

    "TremoloBuilder.shape: an unknown name is the sine, coerced, on both doors" {
        val shaped = tremoloBase.copy(shape = c(4.0))

        tremoloK { it.shape("ramp").shape("wobble") } shouldBe tremoloBase
        ks("Ignitor.saw().$tremoloS, x => x.shape(\"ramp\").shape(\"wobble\"))") shouldBe tremoloBase
        tremoloK { it.shape("ramp") } shouldBe shaped
    }

    // ── The pitch envelope's default fill, through both doors ─────────────────────────────────────────────────────

    "pitch envelope fill: a curve the lambda left out is MOD_ENV_CURVE (exponential), on both doors" {
        val expected = penvStages.copy(attackCurve = c(0.0))

        penvK { it.adsr(attack = 0.002.ign, decay = 0.05.ign, sustain = 0.25.ign, release = 0.1.ign) { e -> e.curves("lin") } } shouldBe expected
        ks("Ignitor.saw().$penvS, x => x.adsr(0.002, 0.05, 0.25, 0.1, e => e.curves(\"lin\")))") shouldBe expected
    }

    "pitch envelope fill: a later adsr replaces an earlier one completely, its unshaped curves back to the default, on both doors" {
        penvK {
            it.adsr(attack = 0.3.ign, decay = 0.3.ign, sustain = 0.3.ign, release = 0.3.ign) { e -> e.curves("lin", "sq", "cube") }
                .adsr(attack = 0.002.ign, decay = 0.05.ign, sustain = 0.25.ign, release = 0.1.ign)
        } shouldBe penvStages

        ks(
            "Ignitor.saw().$penvS, x => x.adsr(0.3, 0.3, 0.3, 0.3, e => e.curves(\"lin\", \"sq\", \"cube\")).adsr(0.002, 0.05, 0.25, 0.1))",
        ) shouldBe penvStages
    }

    "pitch envelope fill: the fill reads the node's default, not an earlier curve, also on a pitch envelope over a slot" {
        val slotted = IgnitorDsl.PitchEnvelope(inner = saw, semitones = IgnitorDsl.Slots.penv.semitones)
            .copy(attack = c(0.002), decay = c(0.05), sustain = c(0.25), release = c(0.1), attackCurve = c(5.0), decayCurve = c(5.0), releaseCurve = c(5.0))

        saw.pitchEnvelope(IgnitorDsl.Slots.penv.semitones) {
            it.adsr(attack = 0.002.ign, decay = 0.05.ign, sustain = 0.25.ign, release = 0.1.ign)
        } shouldBe slotted
        ks("Ignitor.saw().pitchEnvelope(Ignitor.slot.penv.semitones, x => x.adsr(0.002, 0.05, 0.25, 0.1))") shouldBe slotted
    }
})
