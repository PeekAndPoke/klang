/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_engine

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe

/** [TempoMap]: cycles to playback seconds, a piece per tempo (KLANG-00399). */
class TempoMapSpec : StringSpec({

    val eps = 1e-9

    "one tempo is one straight line" {
        val map = TempoMap(cyclesPerSecond = 0.5)

        map.secondsAt(3.0) shouldBe (6.0 plusOrMinus eps)
        map.cycleAt(6.0) shouldBe (3.0 plusOrMinus eps)
        map.cyclesPerSecondAt(3.0) shouldBe (0.5 plusOrMinus eps)
    }

    "a change keeps every second before it and goes on from there at the new tempo" {
        var map = TempoMap(cyclesPerSecond = 0.5)

        map = map.changed(atCycle = 10.1, cyclesPerSecond = 1.0, foldBefore = 0.0)

        map.secondsAt(10.0) shouldBe (20.0 plusOrMinus eps)
        map.secondsAt(10.1) shouldBe (20.2 plusOrMinus eps)
        map.secondsAt(11.0) shouldBe (21.1 plusOrMinus eps)
        map.cycleAt(20.0) shouldBe (10.0 plusOrMinus eps)
        map.cycleAt(21.1) shouldBe (11.0 plusOrMinus eps)
        map.cyclesPerSecondAt(10.0) shouldBe (0.5 plusOrMinus eps)
        map.cyclesPerSecondAt(11.0) shouldBe (1.0 plusOrMinus eps)
    }

    "a second change at the same cycle replaces the first" {
        var map = TempoMap(cyclesPerSecond = 0.5)

        map = map.changed(atCycle = 10.1, cyclesPerSecond = 1.0, foldBefore = 0.0)
        map = map.changed(atCycle = 10.1, cyclesPerSecond = 2.0, foldBefore = 0.0)

        map.size shouldBe 2
        map.secondsAt(11.0) shouldBe (20.65 plusOrMinus eps)
    }

    "a change before a later one drops the later one" {
        var map = TempoMap(cyclesPerSecond = 0.5)

        map = map.changed(atCycle = 12.0, cyclesPerSecond = 1.0, foldBefore = 0.0)
        map = map.changed(atCycle = 11.0, cyclesPerSecond = 2.0, foldBefore = 0.0)

        map.size shouldBe 2
        map.secondsAt(12.0) shouldBe (22.5 plusOrMinus eps)
    }

    "the pieces that end before the fold go, and the seconds after it stay" {
        var map = TempoMap(cyclesPerSecond = 0.5)

        map = map.changed(atCycle = 2.0, cyclesPerSecond = 1.0, foldBefore = 0.0)
        map = map.changed(atCycle = 4.0, cyclesPerSecond = 2.0, foldBefore = 0.0)
        map.size shouldBe 3

        map = map.changed(atCycle = 6.0, cyclesPerSecond = 1.0, foldBefore = 5.0)

        map.size shouldBe 2
        map.secondsAt(5.0) shouldBe (6.5 plusOrMinus eps)
        map.secondsAt(7.0) shouldBe (8.0 plusOrMinus eps)
        map.cycleAt(6.5) shouldBe (5.0 plusOrMinus eps)
    }

    "the tempo comes back as given, not as the inverse of its inverse" {
        // 1 / (1 / x) is not x for this one; a pattern reads the tempo it is asked at (sprudel's cps)
        val cps = 0.4395792082655184

        TempoMap(cyclesPerSecond = cps).cyclesPerSecondAt(2.0) shouldBe cps
    }

    "a length within one tempo is its cycles times the seconds per cycle, to the last bit; across a change, end less start" {
        val cps = 34.5 / 60.0
        val map = TempoMap(cyclesPerSecond = cps)

        // (3.1 - 3.0) cycles: the end less the start differs from 0.1 times the seconds per cycle in the last bit
        map.durationSec(startCycle = 3.0, durationCycles = 0.1) shouldBe 0.1 * (1.0 / cps)

        val changed = map.changed(atCycle = 4.0, cyclesPerSecond = 1.0, foldBefore = 0.0)
        changed.durationSec(startCycle = 3.5, durationCycles = 1.0) shouldBe (0.5 / cps + 0.5 plusOrMinus eps)
    }

    "a change makes a new map and leaves the old one as it was" {
        val old = TempoMap(cyclesPerSecond = 0.5)

        val new = old.changed(atCycle = 10.1, cyclesPerSecond = 1.0, foldBefore = 0.0)

        old.size shouldBe 1
        old.secondsAt(11.0) shouldBe (22.0 plusOrMinus eps)
        new.secondsAt(11.0) shouldBe (21.1 plusOrMinus eps)
    }

    "the next change after a cycle is where the next piece starts" {
        val map = TempoMap(cyclesPerSecond = 0.5)
            .changed(atCycle = 10.1, cyclesPerSecond = 1.0, foldBefore = 0.0)

        map.nextChangeAfter(10.0) shouldBe 10.1
        map.nextChangeAfter(10.1) shouldBe null
        TempoMap(cyclesPerSecond = 0.5).nextChangeAfter(0.0) shouldBe null
    }
})
