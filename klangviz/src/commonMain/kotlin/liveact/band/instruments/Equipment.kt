/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.instruments

// The instruments, a folder each. drums/, marimba/ and trumpet/ hold the instrument itself, built (its meshes, its
// parts that light up), and its player (who poses the figure). guitar/ and bass/ hold their design (shape, strings
// and tuning, how each is played and hangs); the built strung instrument and its player, which the two share, are
// in strings/. Each folder also says what its musician needs to equip (its `_<instrument>_equipment.kt`), and what
// every player with sticks or mallets does with them is here (Strike.kt).

/**
 * What a musician needs to equip to play an instrument, as plain data any scene can read (for a member shown alone,
 * with or without what they play). Each instrument says its own (`DRUMS_EQUIPMENT`, `MARIMBA_EQUIPMENT`,
 * `GUITAR_EQUIPMENT`, `BASS_EQUIPMENT`, `TRUMPET_EQUIPMENT`). Lengths are in the figure's own measure (times its
 * scale gives metres on the stage).
 */
sealed interface Equipment {
    /** Drumsticks, [perHand] in each hand, each [length] long. The instrument makes them; its player holds them. */
    data class Sticks(val perHand: Int, val length: Double) : Equipment

    /** Mallets, [perHand] in each hand, each [length] long. The instrument makes them; its player holds them. */
    data class Mallets(val perHand: Int, val length: Double) : Equipment

    /** The instrument itself, hung on a strap from the trunk: a guitar, a bass. */
    data object OnStrap : Equipment

    /** The instrument itself, held in the hands: the trumpet. */
    data object InHands : Equipment
}
