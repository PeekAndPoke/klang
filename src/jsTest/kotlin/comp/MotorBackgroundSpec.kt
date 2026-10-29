/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * The start page's and the visual's plate, without WebGL (the test browser has none): where the visual's board plate
 * lies on screens of every common size, and how the lamp sways over it.
 */
class MotorBackgroundSpec : StringSpec({

    /** Common windows, wide and tall, small and large: width to height. */
    val screens = listOf(
        1280.0 to 720.0,
        1366.0 to 768.0,
        1440.0 to 900.0,
        1920.0 to 1080.0,
        1920.0 to 1200.0,
        2560.0 to 1440.0,
        3840.0 to 2160.0,
        1024.0 to 1366.0,
    )

    "the board's plate is three tiles wide and two rows high, round the screen's middle" {
        for ((w, h) in screens) {
            val plate = MotorBackground.boardPlateOnScreen(screenWidth = w, screenHeight = h)
            val tile = MotorBackground.tileWidthPx(h)

            plate.width shouldBe (MotorBackground.BOARD_COLUMNS * tile plusOrMinus 1.0)
            plate.height shouldBe (MotorBackground.BOARD_ROWS * tile plusOrMinus 0.01)
            (plate.left + plate.right) / 2 shouldBe (w / 2 plusOrMinus 0.01)
        }
    }

    "the board's plate starts a row under the title's plate: the gauges' row lies between" {
        for ((w, h) in screens) {
            val plate = MotorBackground.boardPlateOnScreen(screenWidth = w, screenHeight = h)
            val titleBottom = MotorBackground.titlePlateBottomShare(h) * h

            plate.top shouldBe (titleBottom + MotorBackground.tileWidthPx(h) plusOrMinus 0.5)
        }
    }

    "the board's plate stays on the screen" {
        for ((w, h) in screens) {
            val plate = MotorBackground.boardPlateOnScreen(screenWidth = w, screenHeight = h)

            plate.bottom shouldBeLessThanOrEqual h
        }
    }

    "the lamp starts over the middle and is back there after half a swing" {
        val (x0, y0) = MotorBackground.lampSwayAt(0.0)
        val (xHalf, yHalf) = MotorBackground.lampSwayAt(MotorBackground.LIGHT_SWAY_PERIOD_MS / 2)

        hypot(x0, y0) shouldBe (0.0 plusOrMinus 1e-9)
        hypot(xHalf, yHalf) shouldBe (0.0 plusOrMinus 1e-9)
    }

    "the lamp swings out to its sway and never further" {
        val (xOut, yOut) = MotorBackground.lampSwayAt(MotorBackground.LIGHT_SWAY_PERIOD_MS / 4)

        hypot(xOut, yOut) shouldBe (MotorBackground.LIGHT_SWAY plusOrMinus 1e-9)

        // Five minutes, a frame at a time
        var ms = 0.0

        while (ms < 300_000.0) {
            val (x, y) = MotorBackground.lampSwayAt(ms)

            hypot(x, y) shouldBeLessThanOrEqual MotorBackground.LIGHT_SWAY + 1e-9
            ms += 16.0
        }
    }

    "the line the lamp swings along turns round" {
        /** The swing's direction at its far end, [swings] swings in, in radians. */
        fun directionAt(swings: Int): Double {
            val (x, y) = MotorBackground.lampSwayAt(MotorBackground.LIGHT_SWAY_PERIOD_MS * (swings + 0.25))

            return atan2(y, x)
        }

        val turnPerSwing = 2 * PI * MotorBackground.LIGHT_SWAY_PERIOD_MS / MotorBackground.LIGHT_SWAY_TURN_MS
        val turned = directionAt(1) - directionAt(0)

        abs(turned) shouldBe (turnPerSwing plusOrMinus 1e-6)
    }
})
