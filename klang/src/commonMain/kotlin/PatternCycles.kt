/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_engine

import io.peekandpoke.klang.audio_bridge.KlangPattern
import io.peekandpoke.klang.audio_bridge.KlangPatternEvent
import io.peekandpoke.klang.common.infra.KlangLock
import io.peekandpoke.klang.common.infra.withLock
import kotlin.math.floor

/**
 * How a playback asks its pattern (the maintainer, 2026-11-16, KLANG-00450: "we always query one full cycle at a time
 * internally"): whatever window is wanted, the pattern is asked one whole cycle at a time, and a window's edge that
 * is not on a cycle is cut afterwards, by each event's start. So the fetch, the sample look-ahead, the catch-up and
 * the signal re-emit all get the same events for the same cycle.
 *
 * The last [capacity] answers stored are kept, so a cycle asked again is not computed again, and a pattern that reads
 * something that moves (the wall clock) still gives one answer per cycle. An answer belongs to its pattern, its cycle
 * and the tempo it was asked at; the oldest goes first. The playback owns this object and empties it when its pattern
 * changes and when it stops.
 */
internal class PatternCycles(private val capacity: Int = DEFAULT_CAPACITY) {

    companion object {
        /** The sample look-ahead (8 cycles) and the fetch's look-ahead fit, with room to spare. */
        const val DEFAULT_CAPACITY = 32
    }

    private class Answer(
        val pattern: KlangPattern,
        val cycle: Double,
        val cps: Double,
        val events: List<KlangPatternEvent>,
    )

    private val lock = KlangLock()
    private val answers = ArrayDeque<Answer>()

    /** Counts the [clear]s: an asking that was running through one does not store its answer. */
    private var clears = 0

    /**
     * The events of [pattern] that start in [from] until [to] (cycles). Where the tempo changes inside a cycle, that
     * cycle is asked once per tempo, whole each time, and each answer gives the events that start in its tempo's part:
     * the pattern reads the tempo it is asked at (sprudel's `cps`, `beats`).
     */
    fun events(pattern: KlangPattern, tempo: TempoMap, from: Double, to: Double): List<KlangPatternEvent> = buildList {
        // No window, no events: and a cycle counted up from an infinite start would never arrive
        if (!from.isFinite() || !to.isFinite()) {
            return@buildList
        }

        var at = from

        while (at < to) {
            val until = minOf(to, tempo.nextChangeAfter(at) ?: to)
            val cps = tempo.cyclesPerSecondAt(at)
            var cycle = floor(at)

            while (cycle < until) {
                // An edge on the cycle's own border cuts nothing: the pattern's answer for the cycle stands as it is
                val cutsStart = at > cycle
                val cutsEnd = until < cycle + 1.0

                for (event in cycleEvents(pattern = pattern, cycle = cycle, cps = cps)) {
                    val start = event.startCycles

                    if ((!cutsStart || start >= at) && (!cutsEnd || start < until)) {
                        add(event)
                    }
                }

                cycle += 1.0
            }

            at = until
        }
    }

    /** Forgets every answer. */
    fun clear() {
        lock.withLock {
            answers.clear()
            clears++
        }
    }

    private fun cycleEvents(pattern: KlangPattern, cycle: Double, cps: Double): List<KlangPatternEvent> {
        val clearsBefore = lock.withLock {
            find(pattern = pattern, cycle = cycle, cps = cps)?.let { return it.events }

            clears
        }

        // Asked outside the lock: a query can take long, and another thread's asking must not wait for it
        val asked = pattern.queryEvents(fromCycles = cycle, toCycles = cycle + 1.0, cps = cps)

        return lock.withLock {
            // Another asking was first: its answer is the cycle's
            val first = find(pattern = pattern, cycle = cycle, cps = cps)

            if (first != null) {
                return@withLock first.events
            }

            // Cleared while the pattern was asked: the answer is given, and not kept (it would outlive the clear)
            if (clears != clearsBefore) {
                return@withLock asked
            }

            answers.addLast(Answer(pattern = pattern, cycle = cycle, cps = cps, events = asked))

            while (answers.size > capacity) {
                answers.removeFirst()
            }

            asked
        }
    }

    /** Under the [lock]. */
    private fun find(pattern: KlangPattern, cycle: Double, cps: Double): Answer? =
        answers.firstOrNull { it.pattern === pattern && it.cycle == cycle && it.cps == cps }
}
