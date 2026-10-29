/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.doubles.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.visuals.VisualsCompositions
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

/**
 * The start page's and the visuals' plate, without WebGL (the test browser has none): where every composition's tiles
 * lie on screens of every common size, and how the lamp sways over them.
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

    /** Every composition a screen is made of. */
    val compositions = mapOf(
        "start" to PlateComposition.start,
        "motor board" to VisualsCompositions.motorBoard,
        "klangamp glass" to VisualsCompositions.klangampGlass,
        "klangamp tiles" to VisualsCompositions.klangampTiles,
    )

    "each tile is as wide and as high as its tiles, where its place says, round the screen's middle" {
        for ((name, composition) in compositions) {
            for ((w, h) in screens) {
                val spots = MotorBackground.tilesOnScreen(screenWidth = w, screenHeight = h, composition = composition)
                val tile = MotorBackground.tileWidthPx(h)
                val top = MotorBackground.topShare(h, composition) * h

                for (placed in composition.tiles) withClue("$name, ${w}x$h, ${placed.id}") {
                    val rect = spots.getValue(placed.id)

                    rect.width shouldBe (placed.columns * tile plusOrMinus 1.0)
                    rect.height shouldBe (placed.rows * tile plusOrMinus 0.01)
                    rect.left shouldBe (w / 2 + placed.left * tile plusOrMinus 1.0)
                    rect.top shouldBe (top + placed.row * tile plusOrMinus 0.01)
                }
            }
        }
    }

    "the tiles off the title's row lie on the grid: their sides on the seams" {
        for ((name, composition) in compositions) {
            for ((w, h) in screens) {
                val tile = MotorBackground.tileWidthPx(h)
                val spots = MotorBackground.tilesOnScreen(screenWidth = w, screenHeight = h, composition = composition)

                for (placed in composition.tiles.filter { !it.offGrid }) withClue("$name, ${w}x$h, ${placed.id}") {
                    // The seams lie half a tile either side of the middle tile's middle, the screen's
                    val fromSeam = ((spots.getValue(placed.id).left - (w / 2 - tile / 2)) / tile).mod(1.0)

                    min(fromSeam, 1.0 - fromSeam) shouldBe (0.0 plusOrMinus 0.01)
                }
            }
        }
    }

    "every composition stays on the screen" {
        for ((name, composition) in compositions) {
            for ((w, h) in screens) {
                val spots = MotorBackground.tilesOnScreen(screenWidth = w, screenHeight = h, composition = composition)

                for ((id, rect) in spots) withClue("$name, ${w}x$h, $id") {
                    rect.top shouldBeGreaterThanOrEqual 0.0
                    rect.bottom shouldBeLessThanOrEqual h + 0.5
                }
            }
        }
    }

    "the start page's composition stands where its title always stood" {
        for ((_, h) in screens) {
            // Before the tiles: the title's plate, a tile row of gauges and the panel's 250 px in the middle of the
            // screen (the clamp on the title's middle, 12 % to 40 %, does not reach these screens)
            val tile = MotorBackground.tileWidthPx(h)
            val title = MotorBackground.tilesOnScreen(1920.0, h, PlateComposition.start).getValue(PlacedTile.TITLE)

            title.top shouldBe ((h - 2 * tile - 250.0) / 2 plusOrMinus 0.5)
            title.height shouldBe (tile plusOrMinus 0.01)
            MotorBackground.titlePlateBottomShare(h) * h shouldBe (title.bottom plusOrMinus 0.01)
        }
    }

    "the Motor's board lies a row under the title's plate: the gauges' row between" {
        for ((w, h) in screens) {
            val spots = MotorBackground.tilesOnScreen(w, h, VisualsCompositions.motorBoard)
            val tile = MotorBackground.tileWidthPx(h)

            spots.getValue(VisualsCompositions.BOARD).top shouldBe (spots.getValue(PlacedTile.TITLE).bottom + tile plusOrMinus 0.5)
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
