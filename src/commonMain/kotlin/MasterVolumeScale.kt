/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang

import kotlin.math.log10
import kotlin.math.pow

/**
 * The master volume knob's loudness scale (KLANG-00330): between the knob's position, 0.0 to 1.0, and the gain on the
 * browser's output. The gain is the truth (`Player.outputVolume`); the knob derives its position with [positionOf]
 * and turns the gain with [gainOf].
 *
 * The position runs in decibels (maintainer, 2026-10-25: "Loudness-scale as more natural"), from [FLOOR_DB] just
 * above the bottom to 0 dB at the top. The bottom end itself is silence, and the gain never goes above 1.0: the
 * knob turns the song down, never up.
 */
object MasterVolumeScale {

    /** The quietest step above silence, in decibels. */
    const val FLOOR_DB = -60.0

    /** The linear gain for [position]; 0.0 at and below the bottom end, 1.0 at and above the top. */
    fun gainOf(position: Double): Double {
        // NaN-guard: an unreadable position is silence, never a blast
        if (position != position) {
            return 0.0
        }

        if (position <= 0.0) {
            return 0.0
        }

        return 10.0.pow(dbOf(position) / 20.0)
    }

    /**
     * The knob's position for [gain], the inverse of [gainOf]: 0.0 for silence and for any gain below [FLOOR_DB], 1.0
     * at and above 1.0.
     */
    fun positionOf(gain: Double): Double {
        // NaN-guard: an unreadable gain shows as silence, as [gainOf] treats an unreadable position
        if (gain != gain) {
            return 0.0
        }

        // Silence, and a gain below it, which has no decibels at all
        if (gain <= 0.0) {
            return 0.0
        }

        val db = 20.0 * log10(gain)

        return (1.0 - db / FLOOR_DB).coerceIn(0.0, 1.0)
    }

    /** The decibels for [position], [FLOOR_DB] at the bottom end and 0.0 at the top; for the knob's tooltip. */
    fun dbOf(position: Double): Double = FLOOR_DB * (1.0 - position.coerceIn(0.0, 1.0))
}
