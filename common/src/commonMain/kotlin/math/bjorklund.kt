/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.common.math

import kotlin.math.abs

/**
 * Builds the Euclidean rhythm E(pulses, steps): distributes `pulses` onsets as evenly as
 * possible across `steps` slots, returning a list of 1s (onset) and 0s (rest).
 *
 * The result is the canonical, left-justified Euclidean string (an onset falls on the first slot
 * whenever there is at least one pulse). Negative `pulses` invert the pattern (1↔0).
 */
fun bjorklund(pulses: Int, steps: Int): List<Int> {
    if (steps <= 0) return emptyList()

    val onsets = abs(pulses).coerceAtMost(steps)
    val rests = steps - onsets

    // Bjorklund grouping: start with one bucket per onset (a "1") and one per rest (a "0"), then
    // repeatedly fold the fewer trailing buckets onto the leading buckets until a single tail group
    // remains. Reading the buckets back out in order yields the even distribution.
    var primary = MutableList(onsets) { mutableListOf(1) }
    var tail = MutableList(rests) { mutableListOf(0) }

    while (minOf(primary.size, tail.size) > 1) {
        val fold = minOf(primary.size, tail.size)
        val folded = ArrayList<MutableList<Int>>(fold)
        for (i in 0 until fold) {
            primary[i].addAll(tail[i])
            folded.add(primary[i])
        }
        val leftover = if (primary.size > tail.size) primary else tail
        val remainder = ArrayList(leftover.subList(fold, leftover.size))
        primary = folded
        tail = remainder
    }

    val pattern = (primary + tail).flatten()
    return if (pulses < 0) pattern.map { 1 - it } else pattern
}
