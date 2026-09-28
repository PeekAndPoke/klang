/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.constants.SLOT_UNSET

/**
 * The envelope curve catalogue (phase 3 step 3c): the one home of the curve NAMES, which the Ignitor
 * doors and sprudel's `adsrCurves` parse through, and of the index rule the backend reads a curve
 * knob with. Its two jobs, pinned separately: the names still mean what the two parsers it replaced
 * meant, and a knob value resolves to its position or to the READER's fallback.
 */
class AdsrCurvesSpec : StringSpec({

    /**
     * A VERBATIM copy of the table both retired parsers carried (klangscript-libs'
     * `parseAdsrCurveName` and sprudel's private twin, identical `when` arms, 2026-09-25). It is the
     * independent oracle: the catalogue must answer exactly this for every name and alias, and
     * nothing for anything else.
     */
    fun oldTable(name: String?): AdsrCurve? = when (name?.trim()?.lowercase()) {
        "linear", "lin" -> AdsrCurve.Linear
        "square", "sq", "quad", "quadratic" -> AdsrCurve.Square
        "cube", "cb", "cubic" -> AdsrCurve.Cube
        "scurve", "s", "smooth", "sigmoid" -> AdsrCurve.SCurve
        "invsquare", "inv", "isquare", "concave" -> AdsrCurve.InvSquare
        "exponential", "exp", "expo" -> AdsrCurve.Exponential
        else -> null
    }

    val oldNames = listOf(
        "linear", "lin", "square", "sq", "quad", "quadratic", "cube", "cb", "cubic",
        "scurve", "s", "smooth", "sigmoid", "invsquare", "inv", "isquare", "concave",
        "exponential", "exp", "expo",
    )

    "the catalogue's names are the enum's entries, position by position" {
        // The position IS the wire encoding, and the backend turns it back through
        // `AdsrCurve.entries`, so the two orders must agree exactly.
        AdsrCurves.names.size shouldBe AdsrCurve.entries.size

        AdsrCurve.entries.forEachIndexed { index, curve ->
            withClue("$curve at $index") {
                AdsrCurves.names[index] shouldBe curve.name.lowercase()
                AdsrCurves.indexOf(curve) shouldBe index.toDouble()
            }
        }
    }

    "every name the old parsers knew means the same curve, and the catalogue knows no other" {
        oldNames.forEach { name ->
            withClue(name) {
                AdsrCurves.curveOf(name) shouldBe oldTable(name)
                // The old table had no null for a known name, so the row above is not passing on nulls.
                (oldTable(name) != null) shouldBe true
            }
        }

        // The other direction: every name and alias the catalogue holds is one the old table knew.
        (AdsrCurves.names + AdsrCurves.aliases.keys).toSet() shouldBe oldNames.toSet()
    }

    "trimming and case, as the old parsers did; an unknown name and none at all are null" {
        listOf("  Exp ", "LINEAR", "SCurve", "\tinv").forEach { name ->
            withClue(name) { AdsrCurves.curveOf(name) shouldBe oldTable(name) }
        }

        listOf("sqare", "", "exp2", "log").forEach { name ->
            withClue(name) {
                AdsrCurves.curveOf(name) shouldBe null
                oldTable(name) shouldBe null
            }
        }

        AdsrCurves.curveOf(null) shouldBe null
    }

    "a name's index takes the reader's fallback for an unknown name, and only then" {
        AdsrCurves.indexOf("square", AdsrCurve.Linear) shouldBe AdsrCurve.Square.ordinal.toDouble()
        AdsrCurves.indexOf("sqare", AdsrCurve.Linear) shouldBe AdsrCurve.Linear.ordinal.toDouble()
        AdsrCurves.indexOf("sqare", AdsrCurve.Exponential) shouldBe AdsrCurve.Exponential.ordinal.toDouble()
    }

    "a knob value selects its position, and the fallback outside the table" {
        AdsrCurve.entries.forEach { curve ->
            withClue(curve) { AdsrCurves.curveAt(curve.ordinal.toDouble(), AdsrCurve.Linear) shouldBe curve }
        }

        // Each edge its own row, and each with a fallback that is NOT index 0, so a resolver that
        // returned index 0 instead of the reader's fallback is red.
        withClue("unset") { AdsrCurves.curveAt(SLOT_UNSET, AdsrCurve.Cube) shouldBe AdsrCurve.Cube }
        withClue("+Inf") { AdsrCurves.curveAt(Double.POSITIVE_INFINITY, AdsrCurve.Cube) shouldBe AdsrCurve.Cube }
        withClue("negative") { AdsrCurves.curveAt(-1.0, AdsrCurve.Cube) shouldBe AdsrCurve.Cube }
        withClue("past the end") {
            AdsrCurves.curveAt(AdsrCurves.names.size.toDouble(), AdsrCurve.Cube) shouldBe AdsrCurve.Cube
        }

        // The rounding rule, `catalogueIndexAt`'s: nearest, and a tie to the even index.
        withClue("1.4 is square") { AdsrCurves.curveAt(1.4, AdsrCurve.Cube) shouldBe AdsrCurve.Square }
        withClue("4.6 is exponential") { AdsrCurves.curveAt(4.6, AdsrCurve.Cube) shouldBe AdsrCurve.Exponential }
        withClue("2.5 ties to 2, cube, not scurve") { AdsrCurves.curveAt(2.5, AdsrCurve.Linear) shouldBe AdsrCurve.Cube }
    }

    "knob is a Constant of the index" {
        AdsrCurves.knob(AdsrCurve.SCurve) shouldBe IgnitorDsl.Constant(3.0)
    }
})
