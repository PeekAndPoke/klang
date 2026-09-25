/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.DistortionShapes
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.LfoShapes
import io.peekandpoke.klang.audio_bridge.distort
import io.peekandpoke.klang.audio_bridge.shape
import io.peekandpoke.klang.audio_bridge.tremolo
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.toObjectOrNull

private fun c(v: Double) = IgnitorDsl.Constant(v)

/** Every field of a tremolo, shape or distort node except its input, by name. */
private fun IgnitorDsl.knobs(): Map<String, Any?> = when (this) {
    is IgnitorDsl.Tremolo -> linkedMapOf("rate" to rate, "depth" to depth, "shape" to shape, "skew" to skew, "phase" to phase)
    is IgnitorDsl.Shape -> linkedMapOf("shape" to shape, "oversample" to oversample)
    else -> error("not a tremolo or shape node: ${this::class.simpleName}")
}

/** A distort as both doors spell it, `Shape(Drive(...))`: the shape's knobs plus the drive's amount. */
private fun IgnitorDsl.distortKnobs(): Map<String, Any?> {
    val shape = this as IgnitorDsl.Shape
    val drive = shape.inner as IgnitorDsl.Drive

    return shape.knobs() + ("amount" to drive.amount)
}

/**
 * The two doors of the waveshaper and the tremolo, compared node for node (the node's own fields,
 * never its input, so how either side builds the oscillator cannot mask a difference). Phase 3 step
 * 3b (2026-09-25):
 *
 *  - `tremolo(rate, depth, configure)` on the script door, with `shape`, `skew` and `phase` on a
 *    builder; the Kotlin door is FLAT, `tremolo(rate, depth, shape, skew, phase)`, a recorded
 *    asymmetry (the filter doors' precedent);
 *  - `shape(shape, oversample)` and `distort(amount, shape, oversample)` flat on both, the shape a
 *    NAME converted to its index in `DistortionShapes`. The script door also takes a number or a
 *    slot, and a slot is written on the Kotlin side through the node (a recorded asymmetry).
 *
 * Every row loops over its family (every name and alias of the catalogue, plus unknown ones), so a
 * door that mishandles one spelling goes red on its own name.
 */
class KlangScriptWaveshaperDoorParitySpec : StringSpec({

    fun ks(code: String): IgnitorDsl {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        return engine.execute(code).toObjectOrNull<IgnitorDsl>()!!
    }

    val saw = IgnitorDsl.Sawtooth()

    // ── tremolo ──────────────────────────────────────────────────────────────────────────────────

    "tremolo: every LFO shape name, alias and unknown name is the same node on both doors" {
        val spellings = LfoShapes.names + LfoShapes.aliases.keys + listOf("SQUARE", "rampup", "")

        for (name in spellings) {
            withClue(name) {
                ks("""Osc.saw().tremolo(4, 0.5, x => x.shape("$name").skew(0.3).phase(0.25))""").knobs() shouldBe
                        saw.tremolo(4.0, 0.5, shape = name, skew = 0.3, phase = 0.25).knobs()
            }
        }
    }

    "tremolo: the bare door is the sine at skew 0 and phase 0 on both doors, and the node's own default" {
        val script = ks("Osc.saw().tremolo(4, 0.5)").knobs()

        script shouldBe saw.tremolo(4.0, 0.5).knobs()
        script shouldBe IgnitorDsl.Tremolo(inner = saw, rate = c(4.0), depth = c(0.5)).knobs()
        script["shape"] shouldBe c(LfoShapes.SINE_INDEX.toDouble())
    }

    "tremolo: each builder knob writes its own field and nothing else" {
        val bare = saw.tremolo(4.0, 0.5).knobs()

        for ((knob, call, value) in listOf(
            Triple("shape", """x.shape("ramp")""", c(LfoShapes.indexOf("ramp"))),
            Triple("skew", "x.skew(-0.6)", c(-0.6)),
            Triple("phase", "x.phase(0.7)", c(0.7)),
        )) {
            withClue(knob) {
                ks("Osc.saw().tremolo(4, 0.5, x => $call)").knobs() shouldBe bare + (knob to value)
            }
        }
    }

    "tremolo: the shape knob takes an index as a number and a slot as itself; skew and phase take slots" {
        ks("Osc.saw().tremolo(4, 0.5, x => x.shape(2))").knobs()["shape"] shouldBe c(2.0)
        ks("""Osc.saw().tremolo(4, 0.5, x => x.shape(Osc.param("ts", 3)))""").knobs()["shape"] shouldBe IgnitorDsl.Param("ts", 3.0)

        val slotted = ks("""Osc.saw().tremolo(4, 0.5, x => x.skew(Osc.param("tk", 0.1)).phase(Osc.param("tp", 0.2)))""").knobs()

        slotted["skew"] shouldBe IgnitorDsl.Param("tk", 0.1)
        slotted["phase"] shouldBe IgnitorDsl.Param("tp", 0.2)
    }

    "tremolo: named door arguments, and a configure lambda that returns nothing is refused" {
        ks("""Osc.saw().tremolo(rate = 4, depth = 0.5, configure = x => x.shape("tri"))""").knobs() shouldBe
                saw.tremolo(4.0, 0.5, shape = "tri").knobs()

        shouldThrowAny { ks("""Osc.saw().tremolo(4, 0.5, x => { x.shape("tri") })""") }
    }

    // ── shape ────────────────────────────────────────────────────────────────────────────────────

    "shape: every waveshaper name, alias and unknown name is the same node on both doors, with and without a factor" {
        val spellings = DistortionShapes.names + DistortionShapes.aliases.keys + listOf("TUBE", "nonexistent", "")

        for (name in spellings) {
            withClue(name) {
                ks("""Osc.saw().shape("$name")""").knobs() shouldBe saw.shape(name).knobs()
                ks("""Osc.saw().shape("$name", 4)""").knobs() shouldBe saw.shape(name, 4).knobs()
            }
        }
    }

    "shape: the bare door, and a named factor with the shape omitted, default to soft on both doors" {
        // The named call skips `shape`, so it only works if the script door's default is a safe
        // literal the KSP turns into a thunk (the Slots-default guardrail).
        ks("Osc.saw().shape()").knobs() shouldBe saw.shape().knobs()
        ks("Osc.saw().shape(oversample = 4)").knobs() shouldBe saw.shape(oversample = 4).knobs()
        ks("Osc.saw().shape()").knobs() shouldBe IgnitorDsl.Shape(inner = saw).knobs()
    }

    "shape: the script door takes the index as a number and either knob as a slot" {
        ks("Osc.saw().shape(10, 2)").knobs() shouldBe mapOf("shape" to c(10.0), "oversample" to c(2.0))
        ks("""Osc.saw().shape(Osc.param("sh", 1), Osc.param("os", 4))""").knobs() shouldBe
                mapOf("shape" to IgnitorDsl.Param("sh", 1.0), "oversample" to IgnitorDsl.Param("os", 4.0))
    }

    // ── distort ──────────────────────────────────────────────────────────────────────────────────

    "distort: every waveshaper name, alias and unknown name is the same Shape(Drive) on both doors" {
        val spellings = DistortionShapes.names + DistortionShapes.aliases.keys + listOf("Tube", "nonexistent")

        for (name in spellings) {
            withClue(name) {
                ks("""Osc.saw().distort(0.5, "$name", 2)""").distortKnobs() shouldBe saw.distort(0.5, name, 2).distortKnobs()
                ks("""Osc.saw().distort(0.5, "$name")""").distortKnobs() shouldBe saw.distort(0.5, name).distortKnobs()
            }
        }
    }

    "distort: the bare door and a named factor default to soft on both doors; the number and slot forms reach the node" {
        ks("Osc.saw().distort(0.5)").distortKnobs() shouldBe saw.distort(0.5).distortKnobs()
        ks("Osc.saw().distort(amount = 0.5, oversample = 4)").distortKnobs() shouldBe saw.distort(0.5, oversample = 4).distortKnobs()

        ks("""Osc.saw().distort(0.5, 13, Osc.param("os", 2))""").distortKnobs() shouldBe
                mapOf("shape" to c(13.0), "oversample" to IgnitorDsl.Param("os", 2.0), "amount" to c(0.5))
    }
})
