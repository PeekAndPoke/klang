/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.constants.VOICE_CULL_NEVER
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

class LangCullSpec : StringSpec({

    "cull dsl interface" {
        val pat = "a b"
        val ctrl = "0.1 0.2"
        dslInterfaceTests(
            "pattern.cull(ctrl)" to seq(pat).cull(ctrl),
            "script pattern.cull(ctrl)" to SprudelPattern.compile("""seq("$pat").cull("$ctrl")"""),
            "string.cull(ctrl)" to pat.cull(ctrl),
            "script string.cull(ctrl)" to SprudelPattern.compile(""""$pat".cull("$ctrl")"""),
            "cull(ctrl)" to seq(pat).apply(cull(ctrl)),
            "script cull(ctrl)" to SprudelPattern.compile("""seq("$pat").apply(cull("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.cull shouldBe 0.1
            events[1].data.cull shouldBe 0.2
        }
    }

    "noCull dsl interface" {
        val pat = "a b"
        dslInterfaceTests(
            "pattern.noCull()" to seq(pat).noCull(),
            "script pattern.noCull()" to SprudelPattern.compile("""seq("$pat").noCull()"""),
            "string.noCull()" to pat.noCull(),
            "script string.noCull()" to SprudelPattern.compile(""""$pat".noCull()"""),
            "mapper noCull()" to seq(pat).apply(cull(0.3).noCull()),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.cull shouldBe VOICE_CULL_NEVER
            events[1].data.cull shouldBe VOICE_CULL_NEVER
        }
    }

    "cull reaches the wire, and an unset cull stays null (the engine default)" {
        val set = seq("a").cull(0.2).queryArc(0.0, 1.0)
        set[0].data.toVoiceData().cull shouldBe 0.2

        val unset = seq("a").queryArc(0.0, 1.0)
        unset[0].data.toVoiceData().cull shouldBe null
    }

    "the cull accessor reads the field for another setter" {
        val events = seq("a").cull(0.25).gain(cull).queryArc(0.0, 1.0)
        events[0].data.gain shouldBe 0.25
    }

    "a mapper argument applies to the field" {
        val events = seq("a b").cull(0.05).cull(mul("1 4")).queryArc(0.0, 1.0)
        events[0].data.cull shouldBe 0.05
        events[1].data.cull shouldBe 0.2
    }
})
