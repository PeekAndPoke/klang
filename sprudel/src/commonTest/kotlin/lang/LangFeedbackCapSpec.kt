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
import io.peekandpoke.klang.sprudel.createSprudelVoiceData
import io.peekandpoke.klang.sprudel.paramBagOf

/**
 * `delay(cap = ...)`, the ceiling a runaway delay saturates toward.
 *
 * The delay is where self-oscillation is genuinely musical: `delay(feedback)` at or above 1.0
 * recirculates without loss, and this is how the author says how loud that sits. (The reverb has no
 * twin: a Freeverb comb past unity latches to DC rather than ringing, so its range is bounded
 * instead — see `Reverb.normalizeSize`.) The master bus has the same knob as
 * `Master(m => m.delay(configure = d => d.cap(3.0)))`.
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
            events[0].data.katalystParams?.get("delay.cap") shouldBe 3.0
        }
    }

    "the cap reaches the wire and defaults to absent" {
        val withCaps = note("c3").delay(cap = 3.0).queryArc(0.0, 1.0)[0].data.toVoiceData()
        withCaps.katalystParams?.get("delay.cap") shouldBe 3.0

        // Unset means "the engine's own default" (1.0), not a value written on every voice.
        note("c3").queryArc(0.0, 1.0)[0].data.toVoiceData().katalystParams?.get("delay.cap").shouldBeNull()
    }

    "the cap survives a merge, per slot name" {
        // The cap lives in the orbit slot bag since Katalyst step 5b-3 (it used to be a field of a
        // delay group, and this row guarded that group's merge helper). `plain` HAS a slot bag with
        // a delay slot but no cap, built directly: a `delay(...)` call would fill its cap with the
        // default (2026-09-16), and a voice without a bag would take merge's copy branch and never
        // reach the per-name merge this row exists to guard.
        val withCap = note("c3").delay(cap = 2.5).queryArc(0.0, 1.0)[0].data
        val plain = createSprudelVoiceData { katalystParams = paramBagOf("delay.wet" to 0.4) }

        plain.merge(withCap).katalystParams?.get("delay.cap") shouldBe 2.5
        withCap.merge(plain).katalystParams?.get("delay.cap") shouldBe 2.5

        val inPlace = createSprudelVoiceData { katalystParams = paramBagOf("delay.wet" to 0.4) }
        inPlace.mergeFrom(withCap)
        inPlace.katalystParams?.get("delay.cap") shouldBe 2.5
    }
})
