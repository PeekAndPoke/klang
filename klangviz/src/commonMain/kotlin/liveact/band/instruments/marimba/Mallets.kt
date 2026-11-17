/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.instruments.marimba

// A marimba player holds two mallets in each hand. Which of the notes that sound together each hand takes is plain
// arithmetic on the bars' places, so it has a spec.

/**
 * The notes of one moment as the two hands share them: the bars for the hand on the low notes ([low]) and for the
 * hand on the high ones ([high]), each none, one or two, as indices into the bars given.
 */
data class MalletShare(val low: List<Int>, val high: List<Int>)

/**
 * How the two hands share the bars at [places] (how far along the instrument each lies, from the low end, in any
 * order), with two mallets each. A hand's two mallets open no wider than [reach].
 *
 * Two notes: one for each hand. Three: the two that lie nearer together go to one hand. Four or more: the two
 * lowest and the two highest. Where a hand's two notes lie wider apart than its mallets open, it plays the outer
 * one alone. A note that finds no mallet is not played (its bar still lights).
 */
fun malletShare(places: List<Double>, reach: Double): MalletShare {
    val order = places.indices.sortedBy { places[it] }

    fun within(bars: List<Int>, keep: Int): List<Int> = when {
        bars.size == 2 && places[bars[1]] - places[bars[0]] > reach -> listOf(bars[keep])
        else -> bars
    }

    return when (order.size) {
        0 -> MalletShare(low = emptyList(), high = emptyList())
        1 -> MalletShare(low = order, high = emptyList())
        2 -> MalletShare(low = order.take(1), high = order.drop(1))
        3 -> {
            val lowPair = places[order[1]] - places[order[0]] <= places[order[2]] - places[order[1]]

            when {
                lowPair -> MalletShare(low = within(order.take(2), keep = 0), high = order.drop(2))
                else -> MalletShare(low = order.take(1), high = within(order.drop(1), keep = 1))
            }
        }

        else -> MalletShare(low = within(order.take(2), keep = 0), high = within(order.takeLast(2), keep = 1))
    }
}
