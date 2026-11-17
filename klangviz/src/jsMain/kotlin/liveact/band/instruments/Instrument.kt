/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.instruments

// What every instrument says of itself, and what its musician holds for it.

/**
 * An instrument on the stage, as every instrument says of itself: what its musician needs to equip to play it. Each
 * instrument's own folder holds the instrument itself, built (its meshes, its parts that light up), and the playing
 * of it (the player who poses the figure).
 */
interface Instrument {
    /** What the musician holds for it: the same as the instrument's `EQUIPMENT`, which can be read without one. */
    val equipment: Equipment
}

/**
 * What a musician needs to equip to play an instrument, as data a scene can read (for a member shown alone, with or
 * without what they play).
 */
sealed interface Equipment {
    /**
     * Sticks or mallets, [perHand] in each hand: the instrument makes them (they stand on the stage beside the
     * figure, not in its skeleton), and its player holds them.
     */
    data class Sticks(val perHand: Int) : Equipment

    /** The instrument itself, hung on a strap from the trunk: a guitar, a bass. */
    data object OnStrap : Equipment

    /** The instrument itself, held in the hands: the trumpet. */
    data object InHands : Equipment
}
