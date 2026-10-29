/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.visuals

import io.peekandpoke.klang.comp.PlacedTile
import io.peekandpoke.klang.comp.PlateComposition

/** The visuals' screens, each a composition of tiles on the start page's plate ([PlateComposition]). */
object VisualsCompositions {
    /** The ids of the tiles the visuals place, besides the title and the gauges' row ([PlateComposition.GAUGES]). */
    const val BOARD = "board"
    const val AMP = "amp"
    const val SCOPE_SPECTRUM = "scope-spectrum"
    const val PLAYLIST = "playlist"
    const val RAIN = "rain"

    /** Where Klang Amp's column starts when the rain's display stands beside it: the two together round the middle. */
    private const val AMP_LEFT = -4.5

    /**
     * The Motor's plate: the start page's, its title where the start page's stands, and under the gauges one plate
     * for the scope and the spectrum (the maintainer, 2026-10-29: "The osci and spectrum must share one big tile").
     */
    val motorBoard: PlateComposition by lazy {
        val start = PlateComposition.start

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
                PlacedTile.centred(id = PlateComposition.GAUGES, row = 1, columns = 5),
                PlacedTile.centred(id = SCOPE_SPECTRUM, row = 2, columns = 5),
                PlacedTile.centred(id = PLAYLIST, row = 3, columns = 5, rows = 2),
            ),
            rows = 5,
            bareOutside = true,
        )
    }

    /**
     * Klang Amp's tiles moved to the left, and on the right a display as high as they are, the voices falling down it
     * (the maintainer, 2026-10-29: "take the third (Klang amp on tiles), move it entirely to the left and add a big
     * "display" on the right. This display will show the voice played falling from the top text vertically, matrix
     * style"): nine tiles wide together, round the middle, the window round them black.
     */
    val klangampRain: PlateComposition by lazy {
        PlateComposition(
            tiles = listOf(
                PlacedTile.title("KLANG AMP", columns = 5).copy(left = AMP_LEFT),
                PlacedTile.centred(id = PlateComposition.GAUGES, row = 1, columns = 5).copy(left = AMP_LEFT),
                PlacedTile.centred(id = SCOPE_SPECTRUM, row = 2, columns = 5).copy(left = AMP_LEFT),
                PlacedTile.centred(id = PLAYLIST, row = 3, columns = 5, rows = 2).copy(left = AMP_LEFT),
                PlacedTile(id = RAIN, left = AMP_LEFT + 5, row = 0, columns = 4, rows = 5),
            ),
            rows = 5,
            bareOutside = true,
        )
    }
}
