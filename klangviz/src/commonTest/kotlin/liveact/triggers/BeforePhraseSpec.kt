/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.triggers

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.viz.liveact.Timed

/** A note of a part: only when it begins. */
private class PhrasePlay(override val t: Double) : Timed

class BeforePhraseSpec : StringSpec({

    // a bar of two seconds: a phrase begins after a pause of four seconds or more
    val before = BeforePhrase(pause = 2.0)
    val part = listOf(10.0, 10.5, 11.0, 15.0, 15.5, 18.5).map { PhrasePlay(it) }

    fun phraseAfter(t: Double, arrives: Double = -100.0): Double? =
        before.phraseAfter(t = t, plays = part, bar = 2.0, lead = 2.0, arrives = arrives)

    "the next note, where it begins a phrase: after a pause of at least the bars asked for, the edge included" {
        withClue("before the pause of four seconds") {
            listOf(11.2, 14.9).map { phraseAfter(it) } shouldBe listOf(15.0, 15.0)
        }

        withClue("within a phrase, before a pause of three seconds, and after the last note") {
            listOf(10.2, 15.2, 16.0, 19.0).map { phraseAfter(it) } shouldBe listOf(null, null, null, null)
        }
    }

    "the first note begins a phrase where the member is there in time for what comes before it" {
        withClue("there long before") {
            phraseAfter(t = 5.0, arrives = 0.0) shouldBe 10.0
        }

        withClue("there just as the lead begins") {
            phraseAfter(t = 5.0, arrives = 8.0) shouldBe 10.0
        }

        withClue("there only within the lead") {
            phraseAfter(t = 9.0, arrives = 8.5) shouldBe null
        }
    }

    "a phrase begins after a pause" {
        shouldThrow<IllegalArgumentException> { BeforePhrase(pause = 0.0) }
    }
})
