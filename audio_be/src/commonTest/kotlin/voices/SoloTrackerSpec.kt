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
 * their edges: the soloed window, the newest event per source (Q28, engine follow-up 43), the capacity.
 */
class SoloTrackerSpec : StringSpec({

    "a source is soloed until its event's end plus the grace, and not one moment longer (no hold)" {
        val tracker = SoloTracker(graceSec = 0.01)
        tracker.record(sourceId = "a", amount = 0.5, startSec = 0.0, untilSec = 1.0, control = true)

        tracker.advance(nowSec = 1.005)
        withClue("inside the grace: the others at 1 - 0.5") { tracker.targetGain() shouldBe 0.5 }
        withClue("inside the grace: soloed") { tracker.isSoloed("a") shouldBe true }

        tracker.advance(nowSec = 1.02)
        withClue("past the grace: the others back") { tracker.targetGain() shouldBe 1.0 }
        withClue("past the grace: no longer soloed") { tracker.isSoloed("a") shouldBe false }
        withClue("past the grace: released (its next notes do not dip), not held") { tracker.isReleased("a") shouldBe true }

        tracker.clearReleased()
        tracker.advance(nowSec = 1.03)
        tracker.size shouldBe 0
    }

    "with the SAME start the control event's end decides, in either pop order; a note's longer gate never extends it" {
        for (noteFirst in listOf(true, false)) {
            val tracker = SoloTracker(graceSec = 0.01)

            if (noteFirst) {
                tracker.record(sourceId = "a", amount = 0.5, startSec = 6.0, untilSec = 9.0, control = false)
                tracker.record(sourceId = "a", amount = 0.5, startSec = 6.0, untilSec = 7.0, control = true)
            } else {
                tracker.record(sourceId = "a", amount = 0.5, startSec = 6.0, untilSec = 7.0, control = true)
                tracker.record(sourceId = "a", amount = 0.5, startSec = 6.0, untilSec = 9.0, control = false)
            }

            tracker.advance(nowSec = 6.9)
            withClue("note first = $noteFirst: soloed inside the control event") { tracker.isSoloed("a") shouldBe true }

            tracker.advance(nowSec = 7.5)
            withClue("note first = $noteFirst: ended with the control event, not the note's gate") { tracker.isSoloed("a") shouldBe false }
        }
    }

    "a note with a LATER start never moves a control event's end either, but takes its amount" {
        val tracker = SoloTracker(graceSec = 0.01)
        tracker.record(sourceId = "a", amount = 0.5, startSec = 6.0, untilSec = 7.0, control = true)
        tracker.record(sourceId = "a", amount = 0.8, startSec = 6.5, untilSec = 30.0, control = false)

        tracker.advance(nowSec = 6.9)
        withClue("the note's amount") { tracker.targetGain() shouldBe (0.2 plusOrMinus 1e-12) }

        tracker.advance(nowSec = 7.5)
        withClue("the control event's end") { tracker.isSoloed("a") shouldBe false }
    }

    "a source with notes only takes its ends from them: the later end at the same start, the newer one after" {
        val tracker = SoloTracker(graceSec = 0.01)
        tracker.record(sourceId = "a", amount = 0.5, startSec = 6.0, untilSec = 8.0, control = false)
        tracker.record(sourceId = "a", amount = 0.5, startSec = 6.0, untilSec = 7.0, control = false)

        tracker.advance(nowSec = 7.5)
        withClue("the later of two notes at the same start") { tracker.isSoloed("a") shouldBe true }

        tracker.record(sourceId = "a", amount = 0.5, startSec = 7.6, untilSec = 9.0, control = false)
        tracker.advance(nowSec = 8.5)
        withClue("a newer note's end") { tracker.isSoloed("a") shouldBe true }
    }

    "an event with a LATER start replaces the amount and the end at once: the latest value wins" {
        val tracker = SoloTracker(graceSec = 0.01)
        tracker.record(sourceId = "a", amount = 0.9, startSec = 0.0, untilSec = 8.0, control = true)
        tracker.record(sourceId = "a", amount = 0.3, startSec = 1.0, untilSec = 2.0, control = true)
        tracker.advance(nowSec = 1.5)

        withClue("the newer 0.3, not the older and stronger 0.9") { tracker.targetGain() shouldBe (0.7 plusOrMinus 1e-12) }

        tracker.advance(nowSec = 3.0)

        withClue("the newer event's end, not the older and later one") { tracker.isSoloed("a") shouldBe false }
    }

    "a newer event with amount 0, NaN or an infinity ends the source's solo; one with an earlier start changes nothing" {
        for ((name, amount) in listOf("0" to 0.0, "NaN" to Double.NaN, "+Inf" to Double.POSITIVE_INFINITY)) {
            val tracker = SoloTracker(graceSec = 0.01)
            tracker.record(sourceId = "a", amount = 1.0, startSec = 0.0, untilSec = 8.0, control = true)
            tracker.record(sourceId = "a", amount = amount, startSec = 1.0, untilSec = 8.0, control = true)
            tracker.advance(nowSec = 1.5)

            withClue("$name: not soloed") { tracker.isSoloed("a") shouldBe false }
            withClue("$name: the others at full level") { tracker.targetGain() shouldBe 1.0 }

            tracker.record(sourceId = "a", amount = 1.0, startSec = 0.5, untilSec = 8.0, control = true)
            tracker.advance(nowSec = 1.5)

            withClue("$name: an older event does not solo it again") { tracker.isSoloed("a") shouldBe false }
        }
    }

    "a source that is not soloed takes no slot" {
        val tracker = SoloTracker(graceSec = 0.01)
        tracker.record(sourceId = "a", amount = 0.0, startSec = 0.0, untilSec = 8.0, control = true)
        tracker.advance(nowSec = 0.0)

        tracker.size shouldBe 0
    }

    "the strongest soloed source wins, and a re-recorded source takes the new amount" {
        val tracker = SoloTracker(graceSec = 0.01)
        tracker.record(sourceId = "a", amount = 0.5, startSec = 0.0, untilSec = 10.0, control = true)
        tracker.record(sourceId = "b", amount = 0.9, startSec = 0.0, untilSec = 10.0, control = true)
        tracker.advance(nowSec = 0.0)

        withClue("max of 0.5 and 0.9") { tracker.targetGain() shouldBe (0.1 plusOrMinus 1e-12) }

        tracker.record(sourceId = "b", amount = 0.3, startSec = 0.0, untilSec = 10.0, control = true)
        tracker.advance(nowSec = 0.0)

        withClue("b's 0.9 is replaced, not kept beside its 0.3") { tracker.targetGain() shouldBe 0.5 }
        tracker.size shouldBe 2
    }

    "beyond its capacity a new source takes the slot of the entry that ends first" {
        val tracker = SoloTracker(graceSec = 0.01, capacity = 2)
        tracker.record(sourceId = "a", amount = 0.5, startSec = 0.0, untilSec = 5.0, control = true)
        tracker.record(sourceId = "b", amount = 0.5, startSec = 0.0, untilSec = 1.0, control = true)
        tracker.record(sourceId = "c", amount = 0.5, startSec = 0.0, untilSec = 3.0, control = true)
        tracker.advance(nowSec = 0.0)

        withClue("a kept") { tracker.isSoloed("a") shouldBe true }
        withClue("b, which ended first, evicted") { tracker.isSoloed("b") shouldBe false }
        withClue("c took its slot") { tracker.isSoloed("c") shouldBe true }
    }
    "when full, a new source takes the slot of an entry that solos nothing (amount 0) before a soloed one" {
        val tracker = SoloTracker(graceSec = 0.01, capacity = 2)
        tracker.record(sourceId = "a", amount = 0.5, startSec = 0.0, untilSec = 1.0, control = true)
        tracker.record(sourceId = "b", amount = 0.5, startSec = 0.0, untilSec = 5.0, control = true)
        tracker.record(sourceId = "b", amount = 0.0, startSec = 0.5, untilSec = 5.0, control = true)
        tracker.record(sourceId = "c", amount = 0.5, startSec = 0.5, untilSec = 3.0, control = true)
        tracker.advance(nowSec = 0.6)

        withClue("a, soloed and ending first, kept") { tracker.isSoloed("a") shouldBe true }
        withClue("c took b's slot") { tracker.isSoloed("c") shouldBe true }
    }
    "a soloed source that is told 0, or whose solo runs out, is released until clearReleased; soloed again, it is not" {
        for (case in listOf("told 0", "ran out")) {
            val tracker = SoloTracker(graceSec = 0.01)
            tracker.record(sourceId = "a", amount = 0.9, startSec = 0.0, untilSec = 1.0, control = true)

            if (case == "told 0") {
                tracker.record(sourceId = "a", amount = 0.0, startSec = 0.5, untilSec = 1.0, control = true)
            }

            tracker.advance(nowSec = if (case == "told 0") 0.6 else 1.5)

            withClue("$case: not soloed") { tracker.isSoloed("a") shouldBe false }
            withClue("$case: released") { tracker.isReleased("a") shouldBe true }
            withClue("$case: the others at full level") { tracker.targetGain() shouldBe 1.0 }

            tracker.advance(nowSec = 5.0)
            withClue("$case: still released after its event ended") { tracker.isReleased("a") shouldBe true }

            tracker.clearReleased()
            tracker.advance(nowSec = 5.0)
            withClue("$case: cleared and dropped") { tracker.isReleased("a") shouldBe false }
            tracker.size shouldBe 0
        }

        val again = SoloTracker(graceSec = 0.01)
        again.record(sourceId = "a", amount = 0.9, startSec = 0.0, untilSec = 1.0, control = true)
        again.record(sourceId = "a", amount = 0.0, startSec = 0.5, untilSec = 1.0, control = true)
        again.record(sourceId = "a", amount = 0.9, startSec = 0.7, untilSec = 1.0, control = true)
        again.advance(nowSec = 0.8)
        withClue("soloed again: not released") { again.isReleased("a") shouldBe false }
    }

    "a source that was never soloed is never released" {
        val tracker = SoloTracker(graceSec = 0.01)
        tracker.record(sourceId = "a", amount = 0.9, startSec = 0.0, untilSec = 1.0, control = true)
        tracker.advance(nowSec = 0.5)

        withClue("soloed, not released") { tracker.isReleased("a") shouldBe false }
        withClue("an unknown source") { tracker.isReleased("b") shouldBe false }
    }
    "a source whose solo ran out starts afresh: its next note sets the end again, a control event before it or not" {
        val tracker = SoloTracker(graceSec = 0.01)
        tracker.record(sourceId = "a", amount = 0.9, startSec = 0.0, untilSec = 1.0, control = true)
        tracker.advance(nowSec = 1.5)
        withClue("released") { tracker.isReleased("a") shouldBe true }

        tracker.record(sourceId = "a", amount = 0.9, startSec = 2.0, untilSec = 3.0, control = false)
        tracker.advance(nowSec = 2.5)

        withClue("soloed by the note until its end") { tracker.isSoloed("a") shouldBe true }
    }

    "a source told 0 twice is released once: a release cleared is not set again by the next 0" {
        val tracker = SoloTracker(graceSec = 0.01)
        tracker.record(sourceId = "a", amount = 0.9, startSec = 0.0, untilSec = 1.0, control = true)
        tracker.record(sourceId = "a", amount = 0.0, startSec = 0.5, untilSec = 1.0, control = true)
        tracker.advance(nowSec = 0.6)
        withClue("released by the first 0") { tracker.isReleased("a") shouldBe true }

        tracker.clearReleased()
        tracker.record(sourceId = "a", amount = 0.0, startSec = 0.7, untilSec = 1.0, control = true)
        tracker.advance(nowSec = 0.8)

        withClue("not released again") { tracker.isReleased("a") shouldBe false }
    }
})
