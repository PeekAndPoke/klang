/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.lights

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe

/** The mood [index] of [LightPlan.MOODS], held. */
private fun held(index: Int): MoodMix = MoodMix(from = LightPlan.MOODS[index], to = LightPlan.MOODS[index], share = 0.0)

class SunSpec : StringSpec({

    "the sun is an ember in the warm moods and in cold against hot, cold only in the cold mood" {
        // cold against hot, violet and amber, hot all over, the first swapped
        for (index in listOf(0, 1, 4, 5)) {
            Sun.color(mood = held(index), front = FrontColors.AMBER, frontLevel = 0.0) shouldBe Sun.EMBER
        }

        // cold all over
        Sun.color(mood = held(3), front = FrontColors.AMBER, frontLevel = 0.0) shouldBe Sun.COLD
    }

    "teal and magenta is a little cooler than an ember" {
        Sun.color(mood = held(2), front = FrontColors.AMBER, frontLevel = 0.0) shouldBe 0xc08e7a
    }

    "between two moods the sun goes over as the lamps do" {
        fun hotToCold(share: Double) = MoodMix(from = LightPlan.MOODS[4], to = LightPlan.MOODS[3], share = share)

        // hot all over going into cold all over: half way the lamps' mean is neutral and the sun still an ember
        Sun.color(mood = hotToCold(0.5), front = FrontColors.AMBER, frontLevel = 0.0) shouldBe Sun.EMBER
        Sun.color(mood = hotToCold(0.8), front = FrontColors.AMBER, frontLevel = 0.0) shouldBe 0x9586b5
        Sun.color(mood = hotToCold(1.0), front = FrontColors.AMBER, frontLevel = 0.0) shouldBe Sun.COLD
    }

    "the front lamp pulls the sun toward its colour by up to 0.7; at no number it leaves it alone" {
        Sun.color(mood = held(0), front = FrontColors.BLUE, frontLevel = 1.0) shouldBe 0xa5adce
        Sun.color(mood = held(0), front = FrontColors.BLUE, frontLevel = 0.5) shouldBe 0xbe9f94
        Sun.color(mood = held(0), front = FrontColors.BLUE, frontLevel = 3.0) shouldBe 0xa5adce
        Sun.color(mood = held(0), front = FrontColors.BLUE, frontLevel = Double.NaN) shouldBe Sun.EMBER
    }

    "it dims to 0.3 under the full front lamp, to the strobe's rest while it flashes, and not at no number" {
        Sun.share(front = 0.0, strobing = false) shouldBe 1.0
        Sun.share(front = 1.0, strobing = false) shouldBe (0.3 plusOrMinus 1e-12)
        Sun.share(front = 0.5, strobing = false) shouldBe (0.65 plusOrMinus 1e-12)
        Sun.share(front = 1.0, strobing = true) shouldBe Strobe.REST
        Sun.share(front = Double.NaN, strobing = false) shouldBe 1.0
    }

    "the lamps behind light the stage fully with all three, about three quarters with the outer two, a third with the inner" {
        val lamps = listOf(BackPlace.OUTER to 260.0, BackPlace.OUTER to 260.0, BackPlace.INNER to 180.0)

        fun held(rig: BackRig) = BackRigMix(from = rig, to = rig, share = 0.0)

        Sun.lit(rig = held(BackRig.ALL), lamps = lamps) shouldBe (1.0 plusOrMinus 1e-12)
        Sun.lit(rig = held(BackRig.OUTER), lamps = lamps) shouldBe (520.0 / 700.0 plusOrMinus 1e-12)
        Sun.lit(rig = held(BackRig.INNER), lamps = lamps) shouldBe (180.0 * BackRig.INNER_ALONE / 700.0 plusOrMinus 1e-12)
        // half way from all three to the inner one alone
        Sun.lit(rig = BackRigMix(from = BackRig.ALL, to = BackRig.INNER, share = 0.5), lamps = lamps) shouldBe
            ((1.0 + 252.0 / 700.0) / 2 plusOrMinus 1e-12)
        Sun.lit(rig = held(BackRig.ALL), lamps = emptyList()) shouldBe 0.0
    }

    "the sun follows the stage's light, squared: full with all lamps, low in the dark, none at no number" {
        Sun.follow(1.0) shouldBe 1.0
        Sun.follow(0.5) shouldBe (0.25 plusOrMinus 1e-12)
        Sun.follow(0.36) shouldBe (0.1296 plusOrMinus 1e-12)
        Sun.follow(0.0) shouldBe 0.0
        Sun.follow(1.4) shouldBe 1.0
        Sun.follow(-0.2) shouldBe 0.0
        Sun.follow(Double.NaN) shouldBe 0.0
    }
})
