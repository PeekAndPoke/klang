/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.EPSILON
import io.peekandpoke.klang.sprudel.SprudelPattern

class LangDelayFeedbackSpec : StringSpec({

    "delay(feedback = ...) sets VoiceData.delayFeedback" {
        val p = note("a b").apply(delay(feedback = "0.5 0.7"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.delayFeedback } shouldBe listOf(0.5, 0.7)
    }

    "delay(feedback = ...) works as pattern extension" {
        val p = note("c").delay(feedback = "0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.delayFeedback shouldBe 0.5
    }

    "delay(feedback = ...) works as string extension" {
        val p = "c".delay(feedback = "0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.delayFeedback shouldBe 0.5
    }

    "delay(feedback = ...) works in compiled code" {
        val p = SprudelPattern.compile("""note("c").delay(feedback = "0.5")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.delayFeedback shouldBe 0.5
    }

    "delay(feedback = ...) with continuous pattern sets delayFeedback correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").delay(feedback = sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        // t=0.0: sine(0) = 0.5
        events[0].data.delayFeedback shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.25: sine(0.25) = 1.0
        events[1].data.delayFeedback shouldBe (1.0 plusOrMinus EPSILON)
        // t=0.5: sine(0.5) = 0.5
        events[2].data.delayFeedback shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.75: sine(0.75) = 0.0
        events[3].data.delayFeedback shouldBe (0.0 plusOrMinus EPSILON)
    }

})
