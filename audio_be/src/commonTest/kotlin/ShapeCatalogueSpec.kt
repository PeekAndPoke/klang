/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.DistortionShapes
import io.peekandpoke.klang.audio_bridge.LfoShapes

/**
 * The two shape catalogues (`DistortionShapes`, `LfoShapes` in `audio_bridge`) against the backend
 * (phase 3 step 3b, 2026-09-25). Since 3b a shape NAME no longer reaches the backend directly:
 * `parseDistortionShape` goes through the catalogue's index, the Ignitor nodes carry that index as a
 * knob, and the tremolo's LFO is the oscillator its index names (since 2026-09-29, when the backend's
 * own LFO enum retired). Two things can therefore go silently wrong, and each has its rows:
 *
 *  - **the NAME TABLE moved**: a name or alias now reaches a different shape than it did before 3b.
 *    The expected tables below are the old `when` arms copied verbatim, the independent oracle; they do
 *    not route through the catalogue.
 *  - **the ORDER drifted**: the catalogue's position `i` and the shape an index `i` selected before are
 *    different shapes, so an index knob (a stored `tremolo.shape` slot) selects the wrong one while every
 *    name still parses right (a name goes through the index, so it would move with the drift and hide it).
 *    Pinned position by position.
 */
class ShapeCatalogueSpec : StringSpec({

    // The old parseDistortionShape, arm by arm (HEAD 0303c330).
    val distortionBefore: Map<String, DistortionShape> = mapOf(
        "hard" to DistortionShape.HARD,
        "gentle" to DistortionShape.GENTLE,
        "cubic" to DistortionShape.CUBIC,
        "diode" to DistortionShape.DIODE,
        "fold" to DistortionShape.FOLD,
        "chebyshev" to DistortionShape.CHEBYSHEV,
        "rectify" to DistortionShape.RECTIFY,
        "exp" to DistortionShape.EXP,
        "softsat" to DistortionShape.SOFT_SAT, "soft_sat" to DistortionShape.SOFT_SAT,
        "tube" to DistortionShape.TUBE,
        "linearfold" to DistortionShape.LINEAR_FOLD, "linear_fold" to DistortionShape.LINEAR_FOLD, "lfold" to DistortionShape.LINEAR_FOLD,
        "zerosquare" to DistortionShape.ZERO_SQUARE, "zero_square" to DistortionShape.ZERO_SQUARE, "square" to DistortionShape.ZERO_SQUARE,
        "sineshaper" to DistortionShape.SINE_SHAPER, "sine_shaper" to DistortionShape.SINE_SHAPER, "sshape" to DistortionShape.SINE_SHAPER,
        "asym" to DistortionShape.ASYM,
        "stompbox" to DistortionShape.STOMP_BOX, "stomp_box" to DistortionShape.STOMP_BOX, "stomp" to DistortionShape.STOMP_BOX,
        "soft" to DistortionShape.SOFT,
    )

    // The old parseLfoShape, arm by arm (HEAD 0303c330), onto the canonical name of the shape it reached.
    val lfoBefore: Map<String, String> = mapOf(
        "triangle" to "triangle", "tri" to "triangle",
        "square" to "square", "sqr" to "square", "pulse" to "square",
        "sawtooth" to "sawtooth", "saw" to "sawtooth",
        "ramp" to "ramp",
        "sine" to "sine", "sin" to "sine",
    )

    // The old backend enum's entry order (SINE, TRIANGLE, SQUARE, SAWTOOTH, RAMP): what index i selected.
    val lfoOrderBefore: List<String> = listOf("sine", "triangle", "square", "sawtooth", "ramp")

    /** A shape name through the catalogue's index, as the tremolo's knob carries it, back to its canonical name. */
    fun parseLfoShape(name: String?): String = LfoShapes.names[LfoShapes.indexAt(LfoShapes.indexOf(name))]

    // ── The name tables did not move ─────────────────────────────────────────────────────────────

    "every distortion name and alias reaches the shape it reached before step 3b, in any case" {
        for ((name, shape) in distortionBefore) {
            withClue(name) {
                parseDistortionShape(name) shouldBe shape
                parseDistortionShape(name.uppercase()) shouldBe shape
            }
        }
    }

    "the distortion catalogue names exactly the old table's words, no more and no fewer" {
        (DistortionShapes.names + DistortionShapes.aliases.keys).toSet() shouldBe distortionBefore.keys
    }

    "an unknown distortion name is soft, as it always was" {
        for (name in listOf("nonexistent", "", "tanh", "square wave")) {
            withClue(name) { parseDistortionShape(name) shouldBe DistortionShape.SOFT }
        }
    }

    "every LFO name and alias reaches the shape it reached before step 3b, in any case" {
        for ((name, shape) in lfoBefore) {
            withClue(name) {
                parseLfoShape(name) shouldBe shape
                parseLfoShape(name.uppercase()) shouldBe shape
            }
        }
    }

    "the LFO catalogue names exactly the old table's words, no more and no fewer" {
        (LfoShapes.names + LfoShapes.aliases.keys).toSet() shouldBe lfoBefore.keys
    }

    "an unknown LFO name, and none at all, is the sine, as it always was" {
        parseLfoShape(null) shouldBe "sine"

        for (name in listOf("rampup", "rampdown", "", "noise")) {
            withClue(name) { parseLfoShape(name) shouldBe "sine" }
        }
    }

    // ── The order did not drift ──────────────────────────────────────────────────────────────────

    "position i of the distortion catalogue is entry i of the backend enum" {
        DistortionShape.entries.size shouldBe DistortionShapes.names.size

        DistortionShapes.names.forEachIndexed { i, name ->
            withClue("$i $name") {
                // Through the INDEX path the node takes, checked against the oracle table.
                distortionShapeAt(i.toDouble()) shouldBe distortionBefore.getValue(name)
            }
        }
    }

    "position i of the LFO catalogue is the shape index i selected before" {
        LfoShapes.names.size shouldBe lfoOrderBefore.size

        LfoShapes.names.forEachIndexed { i, name ->
            withClue("$i $name") { LfoShapes.names[LfoShapes.indexAt(i.toDouble())] shouldBe lfoOrderBefore[i] }
        }
    }

    // ── A bad index is a defined shape, never a throw ────────────────────────────────────────────

    "a non-finite, negative or past-the-end index is soft and the sine; a fractional one rounds" {
        val bad = listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1.0, -0.6, 16.0, 99.0, 1e300)

        for (index in bad) {
            withClue("distort $index") { distortionShapeAt(index) shouldBe DistortionShape.SOFT }
        }

        for (index in bad + listOf(5.0, 4.5000001)) {
            withClue("lfo $index") { LfoShapes.indexAt(index) shouldBe LfoShapes.SINE_INDEX }
        }

        // Nearest position, ties to EVEN (the BodyMaterials rule). A tie is only a test where half-up
        // would land elsewhere: 2.5 is 2 half-even and 3 half-up.
        distortionShapeAt(1.6) shouldBe DistortionShape.GENTLE
        distortionShapeAt(2.5) shouldBe DistortionShape.GENTLE
        distortionShapeAt(-0.4) shouldBe DistortionShape.SOFT
        distortionShapeAt(15.4) shouldBe DistortionShape.STOMP_BOX
        LfoShapes.names[LfoShapes.indexAt(4.4)] shouldBe "ramp"
        LfoShapes.names[LfoShapes.indexAt(2.5)] shouldBe "square"
    }
})
