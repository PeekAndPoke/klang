/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

/**
 * `delay(cap = ...)`, the ceiling a runaway delay saturates toward.
 *
 * The delay is where self-oscillation is genuinely musical: `delay(feedback)` at or above 1.0
 * recirculates without loss, and this is how the author says how loud that sits. (The reverb has no
 * twin: a Freeverb comb past unity latches to DC rather than ringing, so its range is bounded
 * instead — see `Reverb.normalizeRoomSize`.) The master bus has the same knob as
 * `Master(m => m.delay(d => d.cap()))`.
 */
class LangFeedbackCapSpec : StringSpec({

    "delay(cap = ...) dsl interface" {
        val pat = "c3"
        val value = "3.0"

        dslInterfaceTests(
            "pattern.delay(cap = v)" to note(pat).delay(cap = value),
            "script pattern.delay(cap = v)" to SprudelPattern.compile("""note("$pat").delay(cap = "$value")"""),
            "string.delay(cap = v)" to pat.delay(cap = value),
            "script string.delay(cap = v)" to SprudelPattern.compile(""""$pat".delay(cap = "$value")"""),
            "delay(cap = v)" to note(pat).apply(delay(cap = value)),
            "script delay(cap = v)" to SprudelPattern.compile("""note("$pat").apply(delay(cap = "$value"))"""),
            "chained delay(cap = v)" to note(pat).apply(delay(cap = value).delay(cap = value)),
            "script chained delay(cap = v)" to
                    SprudelPattern.compile("""note("$pat").apply(delay(cap = "$value").delay(cap = "$value"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.delayCap shouldBe 3.0
        }
    }

    "the cap reaches the wire and defaults to absent" {
        val withCaps = note("c3").delay(cap = 3.0).queryArc(0.0, 1.0)[0].data.toVoiceData()
        withCaps.delayCap shouldBe 3.0

        // Unset means "the engine's own default" (1.0), not a value written on every voice.
        note("c3").queryArc(0.0, 1.0)[0].data.toVoiceData().delayCap.shouldBeNull()
    }

    "the cap survives a merge — it is a normal voice field, not a special case" {
        // The merge helpers are the easy place to forget a new field: `clone()` uses copy() so it is
        // safe automatically, and the "mergeFrom matches merge" oracle compares two paths that call
        // the SAME helper, so neither notices an omission. This asserts the value directly.
        val withCap = note("c3").delay(cap = 2.5).queryArc(0.0, 1.0)[0].data
        val plain = note("c3").delay(0.4).queryArc(0.0, 1.0)[0].data

        plain.merge(withCap).delayCap shouldBe 2.5
        withCap.merge(plain).delayCap shouldBe 2.5

        val inPlace = note("c3").delay(0.4).queryArc(0.0, 1.0)[0].data
        inPlace.mergeFrom(withCap)
        inPlace.delayCap shouldBe 2.5
    }
})
