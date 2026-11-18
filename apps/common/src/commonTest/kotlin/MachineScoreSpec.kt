/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.apps.common

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class MachineScoreSpec : StringSpec({

    "the full voices and the full frames give 100 each and 100 combined; more gives no more" {
        MachineScore.of(safeVoices = 250, fps = 60.0) shouldBe MachineScore(sound = 100.0, picture = 100.0, combined = 100)
        MachineScore.of(safeVoices = 400, fps = 144.0) shouldBe MachineScore(sound = 100.0, picture = 100.0, combined = 100)
    }

    "each half is its share of the full mark" {
        val score = MachineScore.of(safeVoices = 125, fps = 15.0)

        score.sound shouldBe 50.0
        score.picture shouldBe 25.0
    }

    "the combined score is the geometric mean, rounded: one weak half pulls it down" {
        // sqrt(100 * 25) = 50, where the plain mean would say 62.5
        MachineScore.of(safeVoices = 250, fps = 15.0).combined shouldBe 50
        // sqrt(64 * 100) = 80
        MachineScore.of(safeVoices = 160, fps = 60.0).combined shouldBe 80
        // sqrt(100 * 0.81) = 9
        MachineScore.of(safeVoices = 250, fps = 0.486).combined shouldBe 9
    }

    "nothing in one half is nothing combined" {
        MachineScore.of(safeVoices = 250, fps = 0.0).combined shouldBe 0
        MachineScore.of(safeVoices = 0, fps = 60.0).combined shouldBe 0
    }

    "a frame rate that is no number counts as none; negative voices as none" {
        MachineScore.of(safeVoices = 250, fps = Double.NaN).picture shouldBe 0.0
        MachineScore.of(safeVoices = 250, fps = Double.POSITIVE_INFINITY).picture shouldBe 0.0
        MachineScore.of(safeVoices = -5, fps = 60.0).sound shouldBe 0.0
    }

    "the sentence changes at 80 and at 50" {
        MachineScore(sound = 0.0, picture = 0.0, combined = 80).verdict shouldBe "This machine is ready for the show."
        MachineScore(sound = 0.0, picture = 0.0, combined = 79).verdict shouldBe "The show should run; it may stutter now and then."
        MachineScore(sound = 0.0, picture = 0.0, combined = 50).verdict shouldBe "The show should run; it may stutter now and then."
        MachineScore(sound = 0.0, picture = 0.0, combined = 49).verdict shouldBe
            "This machine will struggle with the show. You can start it anyway."
    }
})
