/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_engine

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe

/** [PlaybackClock] (KLANG-00399, step 3a): the backend's timeline on the frontend, slowing and stopping with it. */
class PlaybackClockSpec : StringSpec({

    val eps = 1e-9

    class Rig {
        var localMs = 5_000.0
        val clock = PlaybackClock(localMs = { localMs })

        /** Reports every [gapMs] from [fromSec] on, [count] times, the backend keeping up. */
        fun reports(fromSec: Double, gapMs: Double, count: Int) {
            for (i in 0 until count) {
                clock.onReport(timelineSec = fromSec + i * gapMs / 1000.0, outputLatencyMs = 0.0)
                localMs += gapMs
            }

            localMs -= gapMs
        }
    }

    "before the first report it reads the frontend's clock, a guess the first report replaces" {
        val rig = Rig()

        rig.clock.nowSec() shouldBe (5.0 plusOrMinus eps)

        // A timeline far behind the guess (an OfflineBackend starting at 0): the guess holds nothing back
        rig.clock.onReport(timelineSec = 0.02, outputLatencyMs = 0.0)
        rig.clock.nowSec() shouldBe (0.02 plusOrMinus eps)
    }

    "between reports it runs on by the frontend's time since the last one" {
        val rig = Rig()
        rig.reports(fromSec = 100.0, gapMs = 20.0, count = 5)

        rig.localMs += 15.0

        rig.clock.nowSec() shouldBe (100.095 plusOrMinus eps)
    }

    "when the reports stop it stops a few gaps past the last one" {
        val rig = Rig()
        rig.reports(fromSec = 100.0, gapMs = 20.0, count = 5)

        rig.localMs += 2_000.0

        // Three gaps of 20 ms is under the least cap, 0.1 s
        rig.clock.nowSec() shouldBe (100.08 + 0.1 plusOrMinus eps)
    }

    "reports in bursts: the cap follows the longest gap, not the average" {
        val rig = Rig()

        // Seven reports at once, then 150 ms without one (Android hands process() over in bursts), three times
        for (burst in 0 until 3) {
            for (i in 0 until 7) {
                rig.clock.onReport(timelineSec = 100.0 + burst * 0.14 + i * 0.02, outputLatencyMs = 0.0)
            }

            rig.localMs += 150.0
        }

        // The last report said 100.40; 150 ms on, the clock is still running (on the average gap it would have
        // stopped at 100.50)
        rig.clock.capSec shouldBe (0.45 plusOrMinus eps)
        rig.clock.nowSec() shouldBe (100.55 plusOrMinus eps)
    }

    "the cap follows the gaps the reports come in, up to half a second" {
        val bursts = Rig()
        bursts.reports(fromSec = 100.0, gapMs = 100.0, count = 5)
        bursts.localMs += 2_000.0

        bursts.clock.nowSec() shouldBe (100.4 + 0.3 plusOrMinus eps)

        val stalled = Rig()
        stalled.reports(fromSec = 100.0, gapMs = 1_000.0, count = 3)
        stalled.localMs += 5_000.0

        stalled.clock.nowSec() shouldBe (102.0 + 0.5 plusOrMinus eps)
    }

    "it never goes backwards: a report behind what it said holds it until the backend has caught up" {
        val rig = Rig()
        rig.reports(fromSec = 100.0, gapMs = 20.0, count = 2)
        rig.localMs += 50.0
        rig.clock.nowSec() shouldBe (100.07 plusOrMinus eps)

        // The backend was slower than the frontend thought
        rig.clock.onReport(timelineSec = 100.04, outputLatencyMs = 0.0)

        rig.clock.nowSec() shouldBe (100.07 plusOrMinus eps)
        rig.localMs += 10.0
        rig.clock.nowSec() shouldBe (100.07 plusOrMinus eps)
        rig.localMs += 40.0
        rig.clock.nowSec() shouldBe (100.09 plusOrMinus eps)
    }

    "it keeps the output latency of the last report" {
        val rig = Rig()

        rig.clock.onReport(timelineSec = 1.0, outputLatencyMs = 42.0)

        rig.clock.outputLatencySec shouldBe (0.042 plusOrMinus eps)
    }
})
