/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band

import kotlin.math.abs

// Where a string player's left hand is for each note: which string, which fret, and where on the neck the hand
// sits. Plain arithmetic on the notes, so it has a spec.

/** A finger on a string: the [string] (0 is the lowest) at the [fret] (0: the open string). */
data class Stop(val string: Int, val fret: Int)

/**
 * How one pluck or strum is held: the hand's [position] (the fret the first finger lies at; the four fingers reach
 * from it to [Tuning.SPAN] frets on) and a [stops] for each note that sounds, the lowest string first.
 */
data class Grip(val position: Int, val stops: List<Stop>)

/**
 * A string instrument's strings as they are tuned: [open] is each open string's note (midi), the lowest first, and
 * [frets] how far up the neck it is played.
 */
class Tuning(val open: List<Int>, val frets: Int) {

    init {
        require(open.isNotEmpty()) { "An instrument has a string at least" }
        require(open.zipWithNext().all { (low, high) -> low < high }) { "The strings are given from the lowest up: $open" }
        require(frets >= SPAN + 1) { "The neck is too short for a hand: $frets frets" }
        require(open.last() + frets - open.first() >= 11) { "The instrument has every note: an octave at least" }
    }

    /** The lowest and the highest note the instrument has. */
    val lowest: Int = open.first()
    val highest: Int = open.last() + frets

    /** The positions a hand can take: the first finger's fret. */
    private val positions: IntRange = 1..(frets - SPAN)

    /** One way to hold some notes at a position: the [stops] and what it costs. */
    private class Held(val stops: List<Stop>, val cost: Double)

    /**
     * The note [midi] as this instrument can play it: itself, or where it lies below the lowest string or above
     * the last fret, as many octaves nearer as it takes (the maintainer, 2026-11-17: "pitch them up per octave so
     * they fit on the fretboard").
     */
    fun fitted(midi: Int): Int {
        var m = midi

        while (m < lowest) {
            m += 12
        }

        while (m > highest) {
            m -= 12
        }

        return m
    }

    /**
     * How a player holds each of [strums] (the notes of each, as midi), one after the other.
     *
     * A hand stays in one position as long as it can: every note is looked for on the strings under its four
     * fingers or on an open string, and a higher note is reached by going to a higher string. It moves along the
     * neck where the notes ask for it, and as little as the whole passage allows: of all the ways to play the
     * strums, the one with the least moving is taken, low on the neck where there is a choice (so a long passage
     * that can be played lower brings the hand down again). In a chord each note
     * has a string of its own, the lower note the lower string; a note that finds none is left out.
     *
     * [held]: how the first of the strums were held before (a song played live grows, and what was played stays as
     * it was). They are taken as they are, and the rest goes on from the last of them.
     */
    fun grips(strums: List<List<Int>>, held: List<Grip> = emptyList()): List<Grip> {
        require(held.size <= strums.size) { "More grips held (${held.size}) than strums (${strums.size})" }

        if (strums.size == held.size) {
            return held
        }

        // a song plays the same notes again and again: each set of notes is worked out once
        val known = mutableMapOf<List<Int>, List<Held>>()
        val rest = strums.drop(held.size).map { strum ->
            val notes = strum.map { fitted(it) }.distinct().sorted()

            known.getOrPut(notes) { positions.map { position -> held(notes, position) } }
        }
        val count = positions.count()
        // the least cost of playing up to each strum with the hand ending at each position, and where it came from
        val costs = Array(rest.size) { DoubleArray(count) }
        val from = Array(rest.size) { IntArray(count) }
        val start = held.lastOrNull()?.position

        for (i in rest.indices) {
            for (p in 0 until count) {
                var best = Double.POSITIVE_INFINITY
                var came = p

                if (i == 0) {
                    best = if (start != null) move(start - positions.first, p) else 0.0
                } else {
                    for (q in 0 until count) {
                        val cost = costs[i - 1][q] + move(q, p)

                        if (cost < best) {
                            best = cost
                            came = q
                        }
                    }
                }

                costs[i][p] = best + rest[i][p].cost
                from[i][p] = came
            }
        }

        var at = (0 until count).minBy { costs[rest.lastIndex][it] }
        val chosen = IntArray(rest.size)

        for (i in rest.indices.reversed()) {
            chosen[i] = at
            at = from[i][at]
        }

        return held + rest.indices.map { i -> Grip(position = positions.first + chosen[i], stops = rest[i][chosen[i]].stops) }
    }

    /** What it costs the hand to go from one position to another. */
    private fun move(from: Int, to: Int): Double = if (from == to) 0.0 else MOVE + abs(from - to)

    /**
     * The best way to hold [notes] (sorted, each within the instrument) with the hand at [position]: each on a
     * string of its own, the strings rising with the notes, at a fret under the four fingers or open.
     */
    private fun held(notes: List<Int>, position: Int): Held {
        var best: Held? = null
        val stops = mutableListOf<Stop>()

        fun search(index: Int, string: Int, cost: Double) {
            val soFar = best

            if (soFar != null && cost >= soFar.cost) {
                return
            }

            if (index == notes.size) {
                best = Held(stops = stops.toList(), cost = cost)

                return
            }

            for (s in string until open.size) {
                val fret = notes[index] - open[s]

                if (fret == 0 || fret in position..(position + SPAN)) {
                    stops.add(Stop(string = s, fret = fret))
                    search(index = index + 1, string = s + 1, cost = cost)
                    stops.removeLast()
                }
            }

            // or it is left out
            search(index = index + 1, string = string, cost = cost + LEFT_OUT)
        }

        search(index = 0, string = 0, cost = LOW * position)

        return best ?: error("No way to hold $notes at $position")
    }

    companion object {
        /** How many frets beyond the first finger's the fourth finger reaches. */
        const val SPAN = 3

        /** A six-string guitar: E A D G B E, from the E below the bass clef's lowest line. */
        val GUITAR = Tuning(open = listOf(40, 45, 50, 55, 59, 64), frets = 15)

        /** A four-string bass: E A D G, an octave below the guitar's four lowest strings. */
        val BASS = Tuning(open = listOf(28, 33, 38, 43), frets = 12)

        // What each thing costs, in frets the hand would rather move: a move as such, a note left out (never, where
        // any position holds it), and each fret up the neck for each strum (so a hand comes back down where a long
        // passage can be played low).
        private const val MOVE = 0.5
        private const val LEFT_OUT = 40.0
        private const val LOW = 0.08
    }
}
