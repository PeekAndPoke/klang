/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.apps.common

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class PictureQualitySpec : StringSpec({

    "a machine that holds its rate at 2x, on a plain display, starts at 2x" {
        PictureQuality.oversampleFor(fps1x = 60.0, fps2x = 60.0, density = 1.0) shouldBe 2
        // 0.9 of 60 is 54, but 55 must be held: 55 is enough
        PictureQuality.oversampleFor(fps1x = 60.0, fps2x = 55.0, density = 1.0) shouldBe 2
    }

    "the 2x part must hold 55 frames and 0.9 of the 1x part" {
        PictureQuality.oversampleFor(fps1x = 60.0, fps2x = 54.9, density = 1.0) shouldBe 1
        PictureQuality.oversampleFor(fps1x = 100.0, fps2x = 89.0, density = 1.0) shouldBe 1
        PictureQuality.oversampleFor(fps1x = 100.0, fps2x = 90.0, density = 1.0) shouldBe 2
    }

    "above 120 at 1x, the 2x part only has to hold 55" {
        PictureQuality.oversampleFor(fps1x = 144.0, fps2x = 60.0, density = 1.0) shouldBe 2
        PictureQuality.oversampleFor(fps1x = 120.0, fps2x = 60.0, density = 1.0) shouldBe 1
    }

    "without a 2x part there is no 2x" {
        PictureQuality.oversampleFor(fps1x = 144.0, fps2x = null, density = 1.0) shouldBe 1
        PictureQuality.oversampleFor(fps1x = 144.0, fps2x = Double.NaN, density = 1.0) shouldBe 1
    }

    "a dense display starts at 1x whatever the rates; just under the mark it is a plain one" {
        PictureQuality.oversampleFor(fps1x = 60.0, fps2x = 60.0, density = 2.0) shouldBe 1
        PictureQuality.oversampleFor(fps1x = 144.0, fps2x = 144.0, density = 3.0) shouldBe 1
        PictureQuality.oversampleFor(fps1x = 60.0, fps2x = 60.0, density = 1.99) shouldBe 2
    }

    "the 2x part is run only on a plain display and where 1x is fast enough" {
        PictureQuality.measuresTwice(fps1x = 60.0, density = 1.0) shouldBe true
        PictureQuality.measuresTwice(fps1x = 55.0, density = 1.99) shouldBe true
        PictureQuality.measuresTwice(fps1x = 54.9, density = 1.0) shouldBe false
        PictureQuality.measuresTwice(fps1x = 60.0, density = 2.0) shouldBe false
    }

    // The fog: two choices of sheets, 4 and 8 (notstrom's low and high until 2026-11-26; 2 and 4 since, KLANG-00484)

    "where the first part holds 55 frames, the fog stays high" {
        fog(fpsFull = 60.0, fpsFew = null) shouldBe 8
        fog(fpsFull = 55.0, fpsFew = null) shouldBe 8
        PictureQuality.measuresFog(fpsFull = 55.0) shouldBe false
    }

    "where the first part does not hold 55, the fog part is run" {
        PictureQuality.measuresFog(fpsFull = 54.9) shouldBe true
        PictureQuality.measuresFog(fpsFull = 20.0) shouldBe true
        PictureQuality.measuresFog(fpsFull = Double.NaN) shouldBe true
    }

    "a Fairphone: 20 frames with the fog high, 34 with it low, gives low" {
        fog(fpsFull = 20.0, fpsFew = 34.0) shouldBe 4
    }

    "high is never foreseen where it was measured below 55, however fast low is" {
        fog(fpsFull = 54.9, fpsFew = 200.0) shouldBe 4
        fog(fpsFull = 54.0, fpsFew = 60.0) shouldBe 4
        fog(fpsFull = 40.0, fpsFew = 144.0) shouldBe 4
    }

    "with a choice between the two parts, the most sheets the line foresees at 55 or more" {
        // taken at the 60 measured: 54 at 12 sheets foresees 56.8 for 8, 52 foresees 55.7, 50 foresees 54.5
        fog(fpsFull = 54.0, fpsFew = 60.0, choices = listOf(4, 8, 12)) shouldBe 8
        fog(fpsFull = 52.0, fpsFew = 60.0, choices = listOf(4, 8, 12)) shouldBe 8
        fog(fpsFull = 50.0, fpsFew = 60.0, choices = listOf(4, 8, 12)) shouldBe 4
        // 200 at 4 and 54.9 at 12: 8 foreseen at 86, 12 at the 54.9 measured
        fog(fpsFull = 54.9, fpsFew = 200.0, choices = listOf(4, 8, 12)) shouldBe 8
    }

    "where the line says nothing, the plain answer is low" {
        // no fog part
        fog(fpsFull = 30.0, fpsFew = null) shouldBe 4
        // rates that are no number, not finite, or not above 0
        fog(fpsFull = 30.0, fpsFew = Double.NaN) shouldBe 4
        fog(fpsFull = Double.NaN, fpsFew = 200.0) shouldBe 4
        fog(fpsFull = 30.0, fpsFew = Double.POSITIVE_INFINITY) shouldBe 4
        fog(fpsFull = 0.0, fpsFew = 200.0) shouldBe 4
        fog(fpsFull = 30.0, fpsFew = -60.0) shouldBe 4
    }

    "a fog part not clearly faster than the first: the fog is not what limits, high stays" {
        // a display of 30 pictures a second caps both parts alike
        fog(fpsFull = 30.0, fpsFew = 30.0) shouldBe 8
        // 31 is less than 5 percent faster than 30
        fog(fpsFull = 30.0, fpsFew = 31.0) shouldBe 8
        // slower with fewer sheets: noise, not the fog
        fog(fpsFull = 30.0, fpsFew = 25.0) shouldBe 8
        // 31.5 is 5 percent faster: the line is asked, and foresees no 55
        fog(fpsFull = 30.0, fpsFew = 31.5) shouldBe 4
        // clearly faster: the line as before
        fog(fpsFull = 20.0, fpsFew = 34.0) shouldBe 4
    }
})

/** The fog's sheets [PictureQuality.fogFor] chooses from [choices]: 4 and 8 unless a case says. */
private fun fog(fpsFull: Double, fpsFew: Double?, choices: List<Int> = listOf(4, 8)): Int = PictureQuality.fogFor(
    fpsFull = fpsFull,
    fpsFew = fpsFew,
    choices = choices,
)
