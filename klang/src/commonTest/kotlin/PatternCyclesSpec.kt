/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_engine

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.KlangPattern
import io.peekandpoke.klang.audio_bridge.KlangPatternEvent
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.common.SourceLocationChain

/**
 * [PatternCycles] (KLANG-00450): a playback asks its pattern one whole cycle at a time, cuts a window's edge
 * afterwards, and keeps a cycle's answer.
 */
class PatternCyclesSpec : StringSpec({

    val oneTempo = TempoMap(cyclesPerSecond = 0.5)

    "a window that cuts cycles asks whole cycles and keeps the events that start inside it" {
        val pattern = Quarters()
        val events = PatternCycles().events(pattern = pattern, tempo = oneTempo, from = 0.5, to = 2.5)

        pattern.asked shouldBe listOf(0.0 to 1.0, 1.0 to 2.0, 2.0 to 3.0)
        events.map { it.startCycles } shouldBe listOf(0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0, 2.25)
    }

    "a window on cycle borders keeps a cycle's answer as it is, also an event that starts outside the cycle" {
        // The pattern's word stands: what the fetch gets for a cycle is what every other asking gets for it
        val pattern = Quarters(stray = -0.01)
        val events = PatternCycles().events(pattern = pattern, tempo = oneTempo, from = 1.0, to = 2.0)

        events.map { it.startCycles } shouldBe listOf(0.99, 1.0, 1.25, 1.5, 1.75)

        val late = PatternCycles().events(pattern = Quarters(stray = 1.0), tempo = oneTempo, from = 1.0, to = 2.0)

        late.map { it.startCycles } shouldBe listOf(2.0, 1.0, 1.25, 1.5, 1.75)
    }

    "an asking that comes while the same cycle is being asked: the first answer stored is the cycle's" {
        val cycles = PatternCycles()
        var inner: List<KlangPatternEvent>? = null
        val quarters = Quarters()

        // The pattern is asked outside the lock, so a second asking can come in the middle of the first: here from
        // inside it, the way another thread would
        val pattern = object : KlangPattern {
            override fun queryEvents(fromCycles: Double, toCycles: Double, cps: Double): List<KlangPatternEvent> {
                if (inner == null) {
                    inner = emptyList()
                    inner = cycles.events(pattern = this, tempo = oneTempo, from = 0.0, to = 1.0)
                }

                return quarters.queryEvents(fromCycles = fromCycles, toCycles = toCycles, cps = cps)
            }
        }

        val outer = cycles.events(pattern = pattern, tempo = oneTempo, from = 0.0, to = 1.0)

        quarters.asked.size shouldBe 2
        outer.size shouldBe 4
        outer.zip(inner!!).all { (a, b) -> a === b } shouldBe true
    }

    "an answer that arrives after a clear is given and not kept" {
        val cycles = PatternCycles()
        val quarters = Quarters()
        var clearNext = true

        val pattern = object : KlangPattern {
            override fun queryEvents(fromCycles: Double, toCycles: Double, cps: Double): List<KlangPatternEvent> {
                if (clearNext) {
                    clearNext = false
                    cycles.clear()
                }

                return quarters.queryEvents(fromCycles = fromCycles, toCycles = toCycles, cps = cps)
            }
        }

        cycles.events(pattern = pattern, tempo = oneTempo, from = 0.0, to = 1.0).size shouldBe 4
        cycles.events(pattern = pattern, tempo = oneTempo, from = 0.0, to = 1.0)
        quarters.asked.size shouldBe 2
        // And from then on it is kept again
        cycles.events(pattern = pattern, tempo = oneTempo, from = 0.0, to = 1.0)
        quarters.asked.size shouldBe 2
    }

    "a window without an end or a start asks nothing" {
        val pattern = Quarters()
        val cycles = PatternCycles()

        cycles.events(pattern = pattern, tempo = oneTempo, from = Double.NEGATIVE_INFINITY, to = 1.0) shouldBe emptyList()
        cycles.events(pattern = pattern, tempo = oneTempo, from = 0.0, to = Double.NaN) shouldBe emptyList()
        pattern.asked shouldBe emptyList()
    }

    "a cycle asked again is not computed again, in whatever window it is asked" {
        val pattern = Quarters()
        val cycles = PatternCycles()

        val one = cycles.events(pattern = pattern, tempo = oneTempo, from = 3.0, to = 4.0)
        val wide = cycles.events(pattern = pattern, tempo = oneTempo, from = 2.0, to = 5.0)

        pattern.asked shouldBe listOf(3.0 to 4.0, 2.0 to 3.0, 4.0 to 5.0)
        // The very same events, not equal ones: a pattern that reads the clock gives one answer per cycle
        wide.filter { it.startCycles >= 3.0 && it.startCycles < 4.0 }.zip(one).all { (a, b) -> a === b } shouldBe true
        wide.size shouldBe 12
    }

    "another pattern is asked anew" {
        val first = Quarters()
        val second = Quarters()
        val cycles = PatternCycles()

        cycles.events(pattern = first, tempo = oneTempo, from = 0.0, to = 1.0)
        cycles.events(pattern = second, tempo = oneTempo, from = 0.0, to = 1.0)

        second.asked shouldBe listOf(0.0 to 1.0)
    }

    "clear forgets the answers" {
        val pattern = Quarters()
        val cycles = PatternCycles()

        cycles.events(pattern = pattern, tempo = oneTempo, from = 0.0, to = 1.0)
        cycles.clear()
        cycles.events(pattern = pattern, tempo = oneTempo, from = 0.0, to = 1.0)

        pattern.asked.size shouldBe 2
    }

    "only the last askings are kept: the oldest goes first" {
        val pattern = Quarters()
        val cycles = PatternCycles(capacity = 2)

        cycles.events(pattern = pattern, tempo = oneTempo, from = 0.0, to = 3.0)
        // Cycles 1 and 2 are kept, cycle 0 is not
        cycles.events(pattern = pattern, tempo = oneTempo, from = 1.0, to = 3.0)
        pattern.asked.size shouldBe 3
        cycles.events(pattern = pattern, tempo = oneTempo, from = 0.0, to = 1.0)
        pattern.asked.size shouldBe 4
    }

    "a tempo change inside a cycle asks that cycle whole at each tempo, and each event comes once, at its tempo" {
        val pattern = Quarters()
        // From cycle 1.5 on, one cycle per second
        val tempo = oneTempo.changed(atCycle = 1.5, cyclesPerSecond = 1.0, foldBefore = 0.0)
        val events = PatternCycles().events(pattern = pattern, tempo = tempo, from = 1.0, to = 3.0)

        pattern.askedAt shouldBe listOf(Triple(1.0, 2.0, 0.5), Triple(1.0, 2.0, 1.0), Triple(2.0, 3.0, 1.0))
        events.map { it.startCycles to it.toVoiceData().freqHz } shouldBe listOf(
            1.0 to 0.5, 1.25 to 0.5,
            1.5 to 1.0, 1.75 to 1.0,
            2.0 to 1.0, 2.25 to 1.0, 2.5 to 1.0, 2.75 to 1.0,
        )
    }

    "the same cycle at another tempo is asked anew" {
        val pattern = Quarters()
        val cycles = PatternCycles()

        cycles.events(pattern = pattern, tempo = oneTempo, from = 0.0, to = 1.0)
        cycles.events(pattern = pattern, tempo = TempoMap(cyclesPerSecond = 1.0), from = 0.0, to = 1.0)

        pattern.askedAt shouldBe listOf(Triple(0.0, 1.0, 0.5), Triple(0.0, 1.0, 1.0))
    }

    "an empty window asks nothing" {
        val pattern = Quarters()

        PatternCycles().events(pattern = pattern, tempo = oneTempo, from = 2.0, to = 2.0) shouldBe emptyList()
        pattern.asked shouldBe emptyList()
    }
}) {
    /**
     * Four events per cycle, on the quarters, each carrying the tempo it was asked at as its frequency; it notes every
     * asking. With [stray], the first event of a cycle starts that much off its cycle's start.
     */
    private class Quarters(private val stray: Double = 0.0) : KlangPattern {
        val askedAt = mutableListOf<Triple<Double, Double, Double>>()
        val asked get() = askedAt.map { it.first to it.second }

        override fun queryEvents(fromCycles: Double, toCycles: Double, cps: Double): List<KlangPatternEvent> {
            askedAt += Triple(fromCycles, toCycles, cps)

            val first = if (stray != 0.0) listOf(Event(start = fromCycles + stray, cps = cps)) else emptyList()

            return first + (0 until 4).map { Event(start = fromCycles + it * 0.25, cps = cps) }
        }
    }

    private class Event(start: Double, cps: Double) : KlangPatternEvent {
        override val startCycles = start
        override val durationCycles = 0.25
        override val sourceLocations: SourceLocationChain? = null
        private val data = VoiceData.empty.copy(freqHz = cps)

        override fun toVoiceData() = data

        override fun gateSeconds(durationSec: Double): Double = durationSec
    }
}
