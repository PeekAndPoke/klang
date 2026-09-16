/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.constants.DELAY_CAP
import io.peekandpoke.klang.audio_bridge.constants.DELAY_FEEDBACK
import io.peekandpoke.klang.audio_bridge.constants.DELAY_TIME_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.DELAY_WET
import io.peekandpoke.klang.sprudel.EPSILON
import io.peekandpoke.klang.sprudel.SprudelPattern

class LangDelaySpec : StringSpec({

    "delay() sets VoiceData.delay" {
        val p = note("a b").apply(delay("0.5 0.8"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.delay } shouldBe listOf(0.5, 0.8)
    }

    "delay() works as pattern extension" {
        val p = note("c").delay("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.delay shouldBe 0.5
    }

    "delay() works as string extension" {
        val p = "c".delay("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.delay shouldBe 0.5
    }

    "delay() works in compiled code" {
        val p = SprudelPattern.compile("""note("c").delay("0.5")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.delay shouldBe 0.5
    }

    "delay() with continuous pattern sets delay correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").delay(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        // t=0.0: sine(0) = 0.5
        events[0].data.delay shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.25: sine(0.25) = 1.0
        events[1].data.delay shouldBe (1.0 plusOrMinus EPSILON)
        // t=0.5: sine(0.5) = 0.5
        events[2].data.delay shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.75: sine(0.75) = 0.0
        events[3].data.delay shouldBe (0.0 plusOrMinus EPSILON)
    }

    // -- per-param (amount, time, feedback) --------------------------------------------

    "delay() per-param sets all three VoiceData fields" {
        val p = note("c").delay(0.5, 0.25, 0.6)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0].data) {
            delay shouldBe 0.5
            delayTime shouldBe 0.25
            delayFeedback shouldBe 0.6
        }
    }

    "delay() per-param with partial params sets the given fields, the rest take their defaults" {
        val p = note("c").delay(0.8, 0.125)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0].data) {
            delay shouldBe 0.8
            delayTime shouldBe 0.125
            delayFeedback shouldBe DELAY_FEEDBACK
            delayCap shouldBe DELAY_CAP
        }
    }

    "delay() per-param works as string extension" {
        val p = "c".delay(0.5, 0.25, 0.6)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0].data) {
            delay shouldBe 0.5
            delayTime shouldBe 0.25
            delayFeedback shouldBe 0.6
        }
    }

    "delay() per-param works in compiled code" {
        val p = SprudelPattern.compile("""note("c").delay(0.5, 0.25, 0.6)""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        with(events[0].data) {
            delay shouldBe 0.5
            delayTime shouldBe 0.25
            delayFeedback shouldBe 0.6
        }
    }

    "delay() per-param mini-notation patterns" {
        val p = note("c3 e3").delay("<0.3 0.6>", "<0.125 0.25>", "<~ 0.8>")
        val cycle0 = p.queryArc(0.0, 1.0)
        val cycle1 = p.queryArc(1.0, 2.0)

        assertSoftly {
            cycle0.size shouldBe 2
            cycle0[0].data.delay shouldBe 0.3
            cycle0[0].data.delayTime shouldBe 0.125
            cycle0[0].data.delayFeedback shouldBe DELAY_FEEDBACK // the rest leaves the default the send write filled

            cycle1.size shouldBe 2
            cycle1[0].data.delay shouldBe 0.6
            cycle1[0].data.delayTime shouldBe 0.25
            cycle1[0].data.delayFeedback shouldBe 0.8
        }
    }

    "delay() per-param works chained with other effects" {
        val p = note("c").apply(gain(0.8).delay(0.5, 0.25, 0.6))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0].data) {
            gain shouldBe 0.8
            delay shouldBe 0.5
            delayTime shouldBe 0.25
            delayFeedback shouldBe 0.6
        }
    }

    "delay() single value sets the send, and the other slots take their defaults" {
        val p = note("c").delay(0.7)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.delay shouldBe 0.7
        events[0].data.delayTime shouldBe DELAY_TIME_SECONDS
        events[0].data.delayFeedback shouldBe DELAY_FEEDBACK
        events[0].data.delayCap shouldBe DELAY_CAP
    }

    "delay(tail-only) does not reinterpret into the head field" {
        // numeric receiver: without the tail-only guard the head apply would REINTERPRET
        // the values ("3"/"4") into the delay field; the send takes its default instead
        val p = SprudelPattern.compile("""seq("3 4").delay(time = 0.25)""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events[0].data.delay shouldBe DELAY_WET
        events[0].data.delayTime shouldBe 0.25
    }

    // -- the call sets every slot ----------------------------------------------------------------------------------------

    "each slot alone fills the others, in both doors" {
        listOf(
            "time" to "delay(time = 0.5)",
            "feedback" to "delay(feedback = 0.6)",
            "cap" to "delay(cap = 2)",
        ).forEach { (slot, call) ->
            listOf(
                "kotlin" to when (slot) {
                    "time" -> note("c").delay(time = 0.5)
                    "feedback" -> note("c").delay(feedback = 0.6)
                    else -> note("c").delay(cap = 2)
                },
                "script" to SprudelPattern.compile("""note("c").$call""")!!,
            ).forEach { (door, p) ->
                with(p.queryArc(0.0, 1.0)[0].data) {
                    withClue("$slot, $door") {
                        delay shouldBe DELAY_WET
                        delayTime shouldBe (if (slot == "time") 0.5 else DELAY_TIME_SECONDS)
                        delayFeedback shouldBe (if (slot == "feedback") 0.6 else DELAY_FEEDBACK)
                        delayCap shouldBe (if (slot == "cap") 2.0 else DELAY_CAP)
                    }
                }
            }
        }
    }

    "a zero send is a written slot too" {
        with(note("c").delay(0).queryArc(0.0, 1.0)[0].data) {
            delay shouldBe 0.0
            delayTime shouldBe DELAY_TIME_SECONDS
            delayFeedback shouldBe DELAY_FEEDBACK
        }
    }

    "a slot an earlier call set keeps its value" {
        val data = note("c").delay(0.4, 0.5).delay(feedback = 0.7).queryArc(0.0, 1.0)[0].data

        data.delay shouldBe 0.4
        data.delayTime shouldBe 0.5
        data.delayFeedback shouldBe 0.7
    }

    "a rest in a control pattern sets nothing on that event" {
        val p = s("hh").delay("<0.5 ~>")

        p.queryArc(0.0, 1.0)[0].data.delayTime shouldBe DELAY_TIME_SECONDS
        with(p.queryArc(1.0, 2.0)[0].data) {
            delay.shouldBeNull()
            delayTime.shouldBeNull()
        }
    }

    "a mapper on a slot that was never set sets nothing" {
        with(s("hh").delay(time = mul(2)).queryArc(0.0, 1.0)[0].data) {
            delay.shouldBeNull()
            delayTime.shouldBeNull()
        }
    }
})
