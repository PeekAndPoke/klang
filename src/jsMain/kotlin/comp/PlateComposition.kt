/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

/**
 * A tile placed on the plate's grid ([MotorBackground]): [columns] tiles wide from [left], in tiles from the screen's
 * middle line (the columns are centred, a tile's middle on the screen's, so a tile's edge lies on a seam at a half:
 * -2.5 for five tiles round the middle; a whole number puts its edges half way across a tile, as the title's), and
 * [rows] rows high from [row], counted from the composition's top. A [raised] tile is a plate standing on the tiles,
 * its face flat and no seam across it; one that is not only marks where its content goes. An [engraving] is cut into
 * its face, as KLANGMOTOR is.
 *
 * The maintainer, 2026-10-29: "rewrite the StartPage background into configurable tiles", "implement the tiles
 * individually and compose the screen as needed".
 */
data class PlacedTile(
    val id: String,
    val left: Double,
    val row: Int,
    val columns: Int,
    val rows: Int,
    val raised: Boolean = true,
    val engraving: String? = null,
) {
    companion object {
        /** A tile [columns] wide round the screen's middle. */
        fun centred(id: String, row: Int, columns: Int, rows: Int = 1, raised: Boolean = true) = PlacedTile(
            id = id,
            left = -columns / 2.0,
            row = row,
            columns = columns,
            rows = rows,
            raised = raised,
        )

        /** A title cut into its own plate, round the screen's middle; as wide as its letters ask unless [columns]. */
        fun title(text: String, row: Int = 0, columns: Int = MotorBackground.titleColumns(text)) =
            centred(id = TITLE, row = row, columns = columns).copy(engraving = text)

        const val TITLE = "title"
    }

    /** Whether its edges lie half way across a tile: its rows' seams lie half a tile over, to meet them. */
    val offGrid: Boolean get() = left % 1.0 == 0.0
}

/** A rectangle on the screen, in px: where a tile lies ([MotorBackground.tilesOnScreen]). */
data class ScreenRect(val left: Double, val top: Double, val right: Double, val bottom: Double) {
    val width: Double get() = right - left
    val height: Double get() = bottom - top
}

/**
 * A screen made of tiles ([PlacedTile]): its [rows] and the room it asks under them ([roomBelowPx]) stand in the middle
 * of the window, the grid's rows running from its top.
 */
data class PlateComposition(
    val tiles: List<PlacedTile>,
    val rows: Int,
    val roomBelowPx: Double = 0.0,
    /**
     * Only its own raised tiles are drawn, the window round them left black (the maintainer, 2026-10-29: "Leave the
     * outside black"); else the grid of plain tiles runs to the window's edges.
     */
    val bareOutside: Boolean = false,
    /**
     * With [bareOutside], whether its tiles show: off, the parts float on black, only an engraving cut into the dark
     * (the maintainer, 2026-10-29: "without the tiles was also nice... can we have a switch somewhere? Maybe the
     * Motorbutton"); without the engraving too, nothing at all (the visuals' Klang Amp tiles).
     */
    val platesShown: Boolean = true,
) {
    companion object {
        /** The gauges' row: the start page's, and the visuals'. */
        const val GAUGES = "gauges"

        /**
         * The three by two tile under the gauges: the start page's dyno screen (KLANG-00394), the Motor's plate's scope
         * and spectrum in the visuals.
         */
        const val SCREEN = "screen"

        /**
         * The room the start page keeps under its gauges for its panel: the composition stands as before, its title
         * where it always was (the maintainer, 2026-10-26).
         */
        const val START_PANEL_PX = 250.0

        /**
         * The start page: KLANGMOTOR on its plate, the gauges on the tiles under it, and under them in the panel's room
         * a three by two tile for the dyno's screen ([SCREEN]; the maintainer, 2026-10-30: "replace the Dynamo Test popup
         * with a 3x2 tile screen").
         */
        val start: PlateComposition by lazy {
            PlateComposition(
                tiles = listOf(
                    PlacedTile.title("KLANGMOTOR"),
                    PlacedTile.centred(id = GAUGES, row = 1, columns = 5, raised = false),
                    PlacedTile.centred(id = SCREEN, row = 2, columns = 3, rows = 2),
                ),
                rows = 2,
                roomBelowPx = START_PANEL_PX,
            )
        }
    }
}
