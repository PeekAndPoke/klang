/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.sound

import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * How much energy the sound has at one moment, each from 0 to about 1: its [level]; the level over the last second
 * and a half ([slow]); how far this moment is above that ([burst]); and the level of its [low], [mid] and [high]
 * bands.
 */
data class Energy(
    val slow: Double,
    val burst: Double,
    val level: Double,
    val low: Double,
    val mid: Double,
    val high: Double,
) {
    companion object {
        /** A band playing at an even, middling level: what is assumed where nothing was measured. */
        val even = Energy(slow = 0.7, burst = 0.0, level = 0.7, low = 0.5, mid = 0.5, high = 0.5)
    }
}

/**
 * A recording's loudness over time, measured once from its audio: a value every [step] seconds, the first at
 * [offset].
 */
@Serializable
data class Loudness(
    val step: Double,
    val offset: Double,
    val level: List<Double>,
    val slow: List<Double>,
    val low: List<Double>,
    val mid: List<Double>,
    val high: List<Double>,
) {
    /** The sound's energy at [t] seconds. */
    fun at(t: Double): Energy {
        val i = max(0, min(level.size - 1, ((t - offset) / step).roundToInt()))
        var burst = 0.0

        for (k in 0 until 6) {
            val j = max(0, i - k)
            burst += max(0.0, level[j] - slow[j]) / 6
        }

        return Energy(slow = slow[i], burst = burst, level = level[i], low = low[i], mid = mid[i], high = high[i])
    }
}
