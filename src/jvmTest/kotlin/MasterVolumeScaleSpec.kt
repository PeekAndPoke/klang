/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe

/**
 * The master volume's loudness scale (KLANG-00330): silence at the bottom, full at the top, decibels in between, and
 * never above the song's own level; and the way back, from a gain to the knob's position.
 */
class MasterVolumeScaleSpec : StringSpec({

    "the ends: silence at the bottom, the song's own level at the top" {
        withClue("the bottom end") {
            MasterVolumeScale.gainOf(0.0) shouldBe 0.0
        }

        withClue("the top end") {
            MasterVolumeScale.gainOf(1.0) shouldBe 1.0
        }
    }

    "in between, the position runs in decibels" {
        withClue("half way is half the floor: -30 dB") {
            MasterVolumeScale.dbOf(0.5) shouldBe -30.0
            MasterVolumeScale.gainOf(0.5) shouldBe (0.0316227766 plusOrMinus 1e-9)
        }

        withClue("one step above the bottom is near the floor, not silence") {
            MasterVolumeScale.gainOf(0.01) shouldBe (0.0010715193 plusOrMinus 1e-9)
        }

        withClue("three quarters: -15 dB") {
            MasterVolumeScale.gainOf(0.75) shouldBe (0.1778279410 plusOrMinus 1e-9)
        }
    }

    "never above the song's level, never below silence, and NaN is silence" {
        withClue("above the top") {
            MasterVolumeScale.gainOf(1.5) shouldBe 1.0
        }

        withClue("below the bottom") {
            MasterVolumeScale.gainOf(-0.5) shouldBe 0.0
        }

        withClue("NaN") {
            MasterVolumeScale.gainOf(Double.NaN) shouldBe 0.0
        }
    }

    "the way back: a gain to the knob's position" {
        withClue("silence and full") {
            MasterVolumeScale.positionOf(0.0) shouldBe 0.0
            MasterVolumeScale.positionOf(1.0) shouldBe 1.0
        }

        withClue("-30 dB is half way, -15 dB three quarters") {
            MasterVolumeScale.positionOf(0.0316227766) shouldBe (0.5 plusOrMinus 1e-9)
            MasterVolumeScale.positionOf(0.1778279410) shouldBe (0.75 plusOrMinus 1e-9)
        }

        withClue("below the floor is the bottom, above full is the top") {
            MasterVolumeScale.positionOf(0.0001) shouldBe 0.0
            MasterVolumeScale.positionOf(2.0) shouldBe 1.0
        }

        withClue("a negative gain and NaN are silence") {
            MasterVolumeScale.positionOf(-0.5) shouldBe 0.0
            MasterVolumeScale.positionOf(Double.NaN) shouldBe 0.0
        }

        withClue("there and back") {
            listOf(0.01, 0.2, 0.5, 0.9).forEach { position ->
                MasterVolumeScale.positionOf(MasterVolumeScale.gainOf(position)) shouldBe (position plusOrMinus 1e-9)
            }
        }
    }
})
