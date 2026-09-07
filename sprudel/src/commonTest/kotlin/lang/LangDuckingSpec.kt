/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

class LangDuckingSpec : StringSpec({

    "duckorbit dsl interface" {
        val pat = "0 1"
        val ctrl = "1 2"

        dslInterfaceTests(
            "pattern.duck(ctrl)" to
                    seq(pat).duck(ctrl),
            "script pattern.duck(ctrl)" to
                    SprudelPattern.compile("""seq("$pat").duck("$ctrl")"""),
            "string.duck(ctrl)" to
                    pat.duck(ctrl),
            "script string.duck(ctrl)" to
                    SprudelPattern.compile(""""$pat".duck("$ctrl")"""),
            "duck(ctrl)" to
                    seq(pat).apply(duck(ctrl)),
            "script duck(ctrl)" to
                    SprudelPattern.compile("""seq("$pat").apply(duck("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.duckCylinder shouldBe 1
            events[1].data.duckCylinder shouldBe 2
        }
    }

    "duckattack dsl interface" {
        val pat = "0 1"
        val ctrl = "0.1 0.5"

        dslInterfaceTests(
            "pattern.duck(attack = ctrl)" to
                    seq(pat).duck(attack = ctrl),
            "script pattern.duck(attack = ctrl)" to
                    SprudelPattern.compile("""seq("$pat").duck(attack = "$ctrl")"""),
            "string.duck(attack = ctrl)" to
                    pat.duck(attack = ctrl),
            "script string.duck(attack = ctrl)" to
                    SprudelPattern.compile(""""$pat".duck(attack = "$ctrl")"""),
            "duck(attack = ctrl)" to
                    seq(pat).apply(duck(attack = ctrl)),
            "script duck(attack = ctrl)" to
                    SprudelPattern.compile("""seq("$pat").apply(duck(attack = "$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.duckAttack shouldBe 0.1
            events[1].data.duckAttack shouldBe 0.5
        }
    }

    "duckdepth dsl interface" {
        val pat = "0 1"
        val ctrl = "0.1 0.5"

        dslInterfaceTests(
            "pattern.duck(depth = ctrl)" to
                    seq(pat).duck(depth = ctrl),
            "script pattern.duck(depth = ctrl)" to
                    SprudelPattern.compile("""seq("$pat").duck(depth = "$ctrl")"""),
            "string.duck(depth = ctrl)" to
                    pat.duck(depth = ctrl),
            "script string.duck(depth = ctrl)" to
                    SprudelPattern.compile(""""$pat".duck(depth = "$ctrl")"""),
            "duck(depth = ctrl)" to
                    seq(pat).apply(duck(depth = ctrl)),
            "script duck(depth = ctrl)" to
                    SprudelPattern.compile("""seq("$pat").apply(duck(depth = "$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.duckDepth shouldBe 0.1
            events[1].data.duckDepth shouldBe 0.5
        }
    }

    "duck() sets duckOrbit" {
        val p = note("c3").duck(1)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.duckCylinder shouldBe 1
    }

    "duck() can be used as PatternMapper" {
        val p = note("c3").apply(duck(1))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.duckCylinder shouldBe 1
    }

    "duck(attack = ...) sets duckAttack" {
        val p = note("c3").duck(attack = 0.15)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.duckAttack shouldBe 0.15
    }

    "duck(attack = ...) can be used as PatternMapper" {
        val p = note("c3").apply(duck(attack = 0.25))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.duckAttack shouldBe 0.25
    }

    "duck(depth = ...) sets duckDepth" {
        val p = note("c3").duck(depth = 0.7)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.duckDepth shouldBe 0.7
    }

    "duck(depth = ...) can be used as PatternMapper" {
        val p = note("c3").apply(duck(depth = 0.9))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.duckDepth shouldBe 0.9
    }

    "ducking parameters merge correctly" {
        val p = note("c3")
            .duck(0)
            .duck(attack = 0.1)
            .duck(depth = 0.8)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.duckCylinder shouldBe 0
        events[0].data.duckAttack shouldBe 0.1
        events[0].data.duckDepth shouldBe 0.8
    }

    "ducking parameters transfer to VoiceData" {
        val p = note("c3")
            .duck(0)
            .duck(attack = 0.15)
            .duck(depth = 0.6)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        val voiceData = events[0].data.toVoiceData()

        voiceData.duckCylinder shouldBe 0
        voiceData.duckAttack shouldBe 0.15
        voiceData.duckDepth shouldBe 0.6
    }

    "ducking parameters work with pattern control" {
        val p = note("c3 d3")
            .duck(2)
            .duck(attack = 0.1)
            .duck(depth = 0.5)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.duckCylinder shouldBe 2
        events[0].data.duckAttack shouldBe 0.1
        events[0].data.duckDepth shouldBe 0.5
    }
})
