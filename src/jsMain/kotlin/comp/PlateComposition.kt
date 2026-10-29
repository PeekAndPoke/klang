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
     * With [bareOutside], whether its tiles show: off, the parts float on black, only the title cut into the dark (the
     * maintainer, 2026-10-29: "without the tiles was also nice... can we have a switch somewhere? Maybe the
     * Motorbutton").
     */
    val platesShown: Boolean = true,
) {
    companion object {
        /** The ids of the tiles the compositions here place. */
        const val GAUGES = "gauges"
        const val BOARD = "board"
        const val AMP = "amp"
        const val SCOPE_SPECTRUM = "scope-spectrum"
        const val PLAYLIST = "playlist"

        /**
         * The room the start page keeps under its gauges for its panel: the composition stands as before, its title
         * where it always was (the maintainer, 2026-10-26).
         */
        const val START_PANEL_PX = 250.0

        /** The start page: KLANGMOTOR on its plate, the gauges on the tiles under it, the panel's room below. */
        val start: PlateComposition by lazy {
            PlateComposition(
                tiles = listOf(
                    PlacedTile.title("KLANGMOTOR"),
                    PlacedTile.centred(id = GAUGES, row = 1, columns = 5, raised = false),
                ),
                rows = 2,
                roomBelowPx = START_PANEL_PX,
            )
        }

        /**
         * The Motor's plate: the start page's, its title where the start page's stands, and under the gauges one plate
         * for the scope and the spectrum (the maintainer, 2026-10-29: "The osci and spectrum must share one big tile").
         */
        val motorBoard: PlateComposition by lazy {
            start.copy(tiles = start.tiles + PlacedTile.centred(id = BOARD, row = 2, columns = 3, rows = 2))
        }

        /**
         * Klangamp under glass: the desktop player as a part of its own, laid on three by three tiles under the title
         * (the maintainer, 2026-10-29: "Hide this winamp behind glass and aligned to the tiles as if it was its own
         * element that can be placed in the board of tiles").
         */
        val klangampGlass: PlateComposition by lazy {
            PlateComposition(
                tiles = listOf(
                    PlacedTile.title("KLANGMOTOR"),
                    PlacedTile.centred(id = AMP, row = 1, columns = 3, rows = 3),
                ),
                rows = 4,
            )
        }

        /**
         * Klangamp made of tiles (the maintainer, 2026-10-29: "a player composed of our tile. Header tile "Klangamp".
         * Gauges tile. Osci & Spectrum tile. Playlist tile"): five tiles wide, one under the other.
         */
        val klangampTiles: PlateComposition by lazy {
            PlateComposition(
                tiles = listOf(
                    PlacedTile.title("KLANG AMP", columns = 5),
                    PlacedTile.centred(id = GAUGES, row = 1, columns = 5),
                    PlacedTile.centred(id = SCOPE_SPECTRUM, row = 2, columns = 5),
                    PlacedTile.centred(id = PLAYLIST, row = 3, columns = 5, rows = 2),
                ),
                rows = 5,
                bareOutside = true,
            )
        }
    }
}
