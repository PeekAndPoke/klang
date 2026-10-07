/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe

/**
 * The solo state of one playback, the rules [VoiceScheduler] reads per block. The scheduler-level rows (what a
 * voice hears) are in [VoiceSchedulerSoloCutSpec]; these pin the bookkeeping the scheduler rows reach only at
 * their edges: the live and protected windows, the last writer per source, the capacity.
 */
class SoloTrackerSpec : StringSpec({

    "an entry is live until its end plus the grace, and protects until its end plus the hold" {
        val tracker = SoloTracker(holdSec = 2.0, graceSec = 0.01)
        tracker.record(sourceId = "a", amount = 0.5, untilSec = 1.0)

        tracker.advance(nowSec = 1.005)
        withClue("inside the grace: live") { tracker.targetGain() shouldBe 0.5 }
        withClue("inside the grace: protected") { tracker.isProtected("a") shouldBe true }

        tracker.advance(nowSec = 1.02)
        withClue("past the grace: no longer live") { tracker.targetGain() shouldBe 1.0 }
        withClue("past the grace: still protected (the hold)") { tracker.isProtected("a") shouldBe true }

        tracker.advance(nowSec = 3.0)
        withClue("at the end of the hold: gone") { tracker.isProtected("a") shouldBe false }
        tracker.size shouldBe 0
    }

    "a re-recorded source keeps the LATER end: a note and its control event start together, in no fixed order" {
        val tracker = SoloTracker(holdSec = 2.0, graceSec = 0.01)
        tracker.record(sourceId = "a", amount = 0.5, untilSec = 8.0)  // the control event over the cycle
        tracker.record(sourceId = "a", amount = 0.5, untilSec = 7.0)  // the note, popped after it
        tracker.advance(nowSec = 7.5)

        withClue("still live at 7.5 s") { tracker.targetGain() shouldBe 0.5 }
    }

    "the strongest live entry wins, and a re-recorded source takes the new amount" {
        val tracker = SoloTracker(holdSec = 2.0, graceSec = 0.01)
        tracker.record(sourceId = "a", amount = 0.5, untilSec = 10.0)
        tracker.record(sourceId = "b", amount = 0.9, untilSec = 10.0)
        tracker.advance(nowSec = 0.0)

        withClue("max of 0.5 and 0.9") { tracker.targetGain() shouldBe (0.1 plusOrMinus 1e-12) }

        tracker.record(sourceId = "b", amount = 0.3, untilSec = 10.0)
        tracker.advance(nowSec = 0.0)

        withClue("b's 0.9 is replaced, not kept beside its 0.3") { tracker.targetGain() shouldBe 0.5 }
        tracker.size shouldBe 2
    }

    "beyond its capacity a new source takes the slot of the entry that ends first" {
        val tracker = SoloTracker(holdSec = 2.0, graceSec = 0.01, capacity = 2)
        tracker.record(sourceId = "a", amount = 0.5, untilSec = 5.0)
        tracker.record(sourceId = "b", amount = 0.5, untilSec = 1.0)
        tracker.record(sourceId = "c", amount = 0.5, untilSec = 3.0)
        tracker.advance(nowSec = 0.0)

        withClue("a kept") { tracker.isProtected("a") shouldBe true }
        withClue("b, which ended first, evicted") { tracker.isProtected("b") shouldBe false }
        withClue("c took its slot") { tracker.isProtected("c") shouldBe true }
    }
})
