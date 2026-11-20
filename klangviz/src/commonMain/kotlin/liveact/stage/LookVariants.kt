/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.stage

import io.peekandpoke.klang.viz.liveact.Switches

/**
 * The variants of the band's look chosen while the show plays (KLANG-00478): what stands behind the band ([stage])
 * and the floor ([floor]). A later variant joins them here, with its key and its line.
 *
 * They start as the address asks ([of]); a key turns one of them over ([nextStage], the key m; [nextFloor], the key
 * p), and the line at the top says the new one in a few words for a moment ([stageLine], [floorLine]).
 */
data class LookVariants(
    val stage: StageLook = StageLook.Wall,
    val floor: FloorLook = FloorLook.Plain,
) {
    /** The next of the stage's looks, after the last the first again. */
    fun nextStage(): LookVariants = copy(stage = StageLook.entries[(stage.ordinal + 1) % StageLook.entries.size])

    /** The next of the floors, after the last the first again. */
    fun nextFloor(): LookVariants = copy(floor = FloorLook.entries[(floor.ordinal + 1) % FloorLook.entries.size])

    /**
     * Whether the mirror is drawn with the fog at [fog]: where it is chosen and the fog is drawn at medium or finer.
     * The mirror draws the stage a second time, and where the fog had to go down to low or off (a slow machine, a
     * phone) it would cost the frames the fog gave back.
     */
    fun mirrors(fog: FogQuality): Boolean = floor == FloorLook.Mirror && fog.layers >= FogQuality.Medium.layers

    /** What stands behind the band, in a word: the wall, or the fence (`?stage=mesh`). */
    val stageWord: String
        get() = when (stage) {
            StageLook.Wall -> "wall"
            StageLook.Mesh -> "fence"
        }

    /** The line shown for a moment when the stage's look is turned over: `stage: fence`. */
    fun stageLine(): String = "stage: $stageWord"

    /**
     * The line shown for a moment when the floor is turned over, with the fog at [fog]: `floor: mirror`, and where the
     * mirror is chosen but not drawn at that fog, why (`floor: mirror (not drawn at fog low)`).
     */
    fun floorLine(fog: FogQuality): String {
        val held = if (floor == FloorLook.Mirror && !mirrors(fog)) " (not drawn at fog ${fog.word})" else ""

        return "floor: ${floor.word}$held"
    }

    companion object {
        /** The variants the address asks for (`?stage=mesh`, `?floor=mirror`); without them the look as it was made. */
        fun of(switches: Switches): LookVariants = LookVariants(stage = StageLook.of(switches), floor = FloorLook.of(switches))
    }
}
