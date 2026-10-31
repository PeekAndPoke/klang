/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_engine

/**
 * Cycles to playback seconds, in linear pieces: one piece per tempo (KLANG-00399, step 1).
 *
 * Second 0 is the playback's start, and it never moves: the backend counts every voice from it (its epoch). A tempo
 * change adds a piece where it takes effect, so every cycle before that keeps its second and the voices already sent
 * stay right. Before the map, a change moved the start instead, and the backend, whose epoch stays, played every later
 * voice displaced by the move.
 *
 * A map never changes: [changed] makes a new one, so a reader on another thread holds a whole map or the next one.
 */
internal class TempoMap private constructor(private val pieces: List<Piece>) {

    /** A tempo from [startCycle], which plays at [startSec], up to the next piece. */
    private class Piece(val startCycle: Double, val startSec: Double, val secPerCycle: Double)

    /** One tempo from second 0 on. */
    constructor(cyclesPerSecond: Double) : this(
        listOf(Piece(startCycle = 0.0, startSec = 0.0, secPerCycle = 1.0 / cyclesPerSecond))
    )

    /** How many pieces the map keeps. */
    val size: Int get() = pieces.size

    /** The second [cycle] plays at. */
    fun secondsAt(cycle: Double): Double {
        val piece = pieceAtCycle(cycle)

        return piece.startSec + (cycle - piece.startCycle) * piece.secPerCycle
    }

    /** The cycle that plays at [sec]. */
    fun cycleAt(sec: Double): Double {
        val piece = pieceAtSec(sec)

        return piece.startCycle + (sec - piece.startSec) / piece.secPerCycle
    }

    /** The tempo at [cycle], in cycles per second. */
    fun cyclesPerSecondAt(cycle: Double): Double = 1.0 / pieceAtCycle(cycle).secPerCycle

    /** The first cycle after [cycle] where the tempo changes; null when it stays. */
    fun nextChangeAfter(cycle: Double): Double? = pieces.firstOrNull { it.startCycle > cycle }?.startCycle

    /**
     * This map with the tempo [cyclesPerSecond] from [atCycle] on; a change already in it at or after [atCycle] gives
     * way. The pieces that end at or before [foldBefore] go: only a catch-up may still ask for those cycles, for state,
     * and gets them on the first kept piece's tempo.
     */
    fun changed(atCycle: Double, cyclesPerSecond: Double, foldBefore: Double): TempoMap {
        val kept = pieces.filter { it.startCycle < atCycle }
        val added = Piece(startCycle = atCycle, startSec = secondsAt(atCycle), secPerCycle = 1.0 / cyclesPerSecond)
        val all = kept + added
        // The last piece starting at or before the fold starts the map
        val first = all.indexOfLast { it.startCycle <= foldBefore }.coerceAtLeast(0)

        return TempoMap(all.drop(first))
    }

    /** The last piece starting at or before [cycle]; the first piece for a cycle before it. */
    private fun pieceAtCycle(cycle: Double): Piece = pieces.lastOrNull { it.startCycle <= cycle } ?: pieces[0]

    /** The last piece starting at or before [sec]; the first piece for a second before it. */
    private fun pieceAtSec(sec: Double): Piece = pieces.lastOrNull { it.startSec <= sec } ?: pieces[0]
}
