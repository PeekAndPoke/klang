/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.camera

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlin.math.abs

/** The largest step of the share between two moments a hundredth of a bar apart, from bar [from] to [to]. */
private fun largestStep(way: FlightWay, from: Double, to: Double): Double {
    val steps = ((to - from) * 100).toInt()

    return (0 until steps).maxOf { i ->
        abs(Flight.share(way, from + (i + 1) / 100.0) - Flight.share(way, from + i / 100.0))
    }
}

class FlightSpec : StringSpec({

    "every way is on the total for the round's first twenty bars and again at its end" {
        for (way in FlightWay.entries) {
            Flight.share(way, 0.0) shouldBe 0.0
            Flight.share(way, 19.99) shouldBe 0.0
            Flight.share(way, 48.0) shouldBe 0.0
        }
    }

    "every way is at the desk from bar 30 to bar 38" {
        for (way in FlightWay.entries) {
            Flight.share(way, 30.0) shouldBe 1.0
            Flight.share(way, 34.0) shouldBe 1.0
            Flight.share(way, 37.99) shouldBe 1.0
        }
    }

    "out and back flies both ways and never jumps" {
        Flight.share(FlightWay.OutAndBack, 25.0) shouldBe (0.5 plusOrMinus 1e-12)
        Flight.share(FlightWay.OutAndBack, 43.0) shouldBe (0.5 plusOrMinus 1e-12)
        // softly away and softly there: a tenth of the way through, it has gone under a hundredth of it
        Flight.share(FlightWay.OutAndBack, 21.0) shouldBeLessThan 0.01
        Flight.share(FlightWay.OutAndBack, 21.0) shouldBe (0.00856 plusOrMinus 1e-5)
        Flight.share(FlightWay.OutAndBack, 39.0) shouldBe (1.0 - 0.00856 plusOrMinus 1e-5)
        Flight.share(FlightWay.CutThenIn, 39.0) shouldBe (1.0 - 0.00856 plusOrMinus 1e-5)
        largestStep(FlightWay.OutAndBack, from = 0.0, to = 48.0) shouldBeLessThan 0.002
    }

    "out then cut flies out and jumps back to the total at bar 38" {
        Flight.share(FlightWay.OutThenCut, 25.0) shouldBe (0.5 plusOrMinus 1e-12)
        largestStep(FlightWay.OutThenCut, from = 0.0, to = 37.99) shouldBeLessThan 0.002
        Flight.share(FlightWay.OutThenCut, 38.0) shouldBe 0.0
        Flight.share(FlightWay.OutThenCut, 43.0) shouldBe 0.0
    }

    "cut then in jumps to the desk at bar 28 and flies back in" {
        Flight.share(FlightWay.CutThenIn, 27.99) shouldBe 0.0
        Flight.share(FlightWay.CutThenIn, 28.0) shouldBe 1.0
        Flight.share(FlightWay.CutThenIn, 43.0) shouldBe (0.5 plusOrMinus 1e-12)
        largestStep(FlightWay.CutThenIn, from = 28.0, to = 48.0) shouldBeLessThan 0.002
    }

    "a third of the rounds go each way" {
        Flight.way(0.0) shouldBe FlightWay.OutAndBack
        Flight.way(0.33) shouldBe FlightWay.OutAndBack
        Flight.way(0.34) shouldBe FlightWay.OutThenCut
        Flight.way(0.66) shouldBe FlightWay.OutThenCut
        Flight.way(0.67) shouldBe FlightWay.CutThenIn
        Flight.way(0.999) shouldBe FlightWay.CutThenIn
    }
})
