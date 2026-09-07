/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.SprudelPattern

class LangEffectsRoutingSpec : StringSpec({

    // distort
    "top-level distort() sets VoiceData.distort correctly" {
        val p = note("a b").apply(distort("0.0 2.5"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events.map { it.data.distort } shouldBe listOf(0.0, 2.5)
    }

    "control pattern distort() sets VoiceData.distort on existing pattern" {
        val base = note("c3 e3")
        val p = base.distort("1.0 3.0")
        val events = p.queryArc(0.0, 2.0)
        events.size shouldBe 4
        events.map { it.data.distort } shouldBe listOf(1.0, 3.0, 1.0, 3.0)
    }

    // crush
    "top-level crush() sets VoiceData.crush correctly" {
        val p = note("a b").apply(crush("8 4"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events.map { it.data.crush } shouldBe listOf(8.0, 4.0)
    }

    "control pattern crush() sets VoiceData.crush on existing pattern" {
        val base = note("c3 e3")
        val p = base.crush("12 6")
        val events = p.queryArc(0.0, 2.0)
        events.size shouldBe 4
        events.map { it.data.crush } shouldBe listOf(12.0, 6.0, 12.0, 6.0)
    }

    // coarse
    "top-level coarse() sets VoiceData.coarse correctly" {
        val p = note("a b").apply(coarse("1 2"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events.map { it.data.coarse } shouldBe listOf(1.0, 2.0)
    }

    "control pattern coarse() sets VoiceData.coarse on existing pattern" {
        val base = note("c3 e3")
        val p = base.coarse("3 4")
        val events = p.queryArc(0.0, 2.0)
        events.size shouldBe 4
        events.map { it.data.coarse } shouldBe listOf(3.0, 4.0, 3.0, 4.0)
    }

    // room
    "top-level room() sets VoiceData.room correctly" {
        val p = note("a b").apply(room("0.1 0.9"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events.map { it.data.room } shouldBe listOf(0.1, 0.9)
    }

    "control pattern room() sets VoiceData.room on existing pattern" {
        val base = note("c3 e3")
        val p = base.room("0.3 0.6")
        val events = p.queryArc(0.0, 2.0)
        events.size shouldBe 4
        events.map { it.data.room } shouldBe listOf(0.3, 0.6, 0.3, 0.6)
    }

    // room(size = ...)
    "top-level room(size = ...) sets VoiceData.roomSize correctly" {
        val p1 = note("a b").apply(room(size = "0.2 0.8"))
        val e1 = p1.queryArc(0.0, 1.0)
        e1.size shouldBe 2
        e1.map { it.data.roomSize } shouldBe listOf(0.2, 0.8)
    }

    "control pattern room(size = ...) sets VoiceData.roomSize on existing pattern" {
        val base = note("c3 e3")
        val p = base.room(size = "0.1 0.3")
        val events = p.queryArc(0.0, 2.0)
        events.size shouldBe 4
        events.map { it.data.roomSize } shouldBe listOf(0.1, 0.3, 0.1, 0.3)
    }

    // delay
    "top-level delay() sets VoiceData.delay correctly" {
        val p = note("a b").apply(delay("0.0 1.0"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events.map { it.data.delay } shouldBe listOf(0.0, 1.0)
    }

    "control pattern delay() sets VoiceData.delay on existing pattern" {
        val base = note("c3 e3")
        val p = base.delay("0.25 0.5")
        val events = p.queryArc(0.0, 2.0)
        events.size shouldBe 4
        events.map { it.data.delay } shouldBe listOf(0.25, 0.5, 0.25, 0.5)
    }

    // delay(time = ...)
    "top-level delay(time = ...) sets VoiceData.delayTime correctly" {
        val p = note("a b").apply(delay(time = "0.125 0.25"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events.map { it.data.delayTime } shouldBe listOf(0.125, 0.25)
    }

    "control pattern delay(time = ...) sets VoiceData.delayTime on existing pattern" {
        val base = note("c3 e3")
        val p = base.delay(time = "0.0625 0.5")
        val events = p.queryArc(0.0, 2.0)
        events.size shouldBe 4
        events.map { it.data.delayTime } shouldBe listOf(0.0625, 0.5, 0.0625, 0.5)
    }

    // delay(feedback = ...)
    "top-level delay(feedback = ...) sets VoiceData.delayFeedback correctly" {
        val p = note("a b").apply(delay(feedback = "0.25 0.75"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events.map { it.data.delayFeedback } shouldBe listOf(0.25, 0.75)
    }

    "control pattern delay(feedback = ...) sets VoiceData.delayFeedback on existing pattern" {
        val base = note("c3 e3")
        val p = base.delay(feedback = "0.1 0.9")
        val events = p.queryArc(0.0, 2.0)
        events.size shouldBe 4
        events.map { it.data.delayFeedback } shouldBe listOf(0.1, 0.9, 0.1, 0.9)
    }

    // orbit
    "top-level orbit() sets VoiceData.orbit correctly" {
        val p = note("a b").apply(orbit("0 2"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events.map { it.data.cylinder } shouldBe listOf(0, 2)
    }

    "control pattern orbit() sets VoiceData.orbit on existing pattern" {
        val base = note("c3 e3")
        val p = base.orbit("1 3")
        val events = p.queryArc(0.0, 2.0)
        events.size shouldBe 4
        events.map { it.data.cylinder } shouldBe listOf(1, 3, 1, 3)
    }

    "distort() works within compiled code as top-level PatternMapper" {
        val p = SprudelPattern.compile("""note("a b").apply(distort("0 1"))""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.distort } shouldBe listOf(0.0, 1.0)
    }

    "distort() works within compiled code as chained-level function" {
        val p = SprudelPattern.compile("""note("a b").distort("0 1")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.distort } shouldBe listOf(0.0, 1.0)
    }

    "crush() works within compiled code as top-level PatternMapper" {
        val p = SprudelPattern.compile("""note("a b").apply(crush("8 4"))""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.crush } shouldBe listOf(8.0, 4.0)
    }

    "crush() works within compiled code as chained-level function" {
        val p = SprudelPattern.compile("""note("a b").crush("8 4")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.crush } shouldBe listOf(8.0, 4.0)
    }

    "coarse() works within compiled code as top-level PatternMapper" {
        val p = SprudelPattern.compile("""note("a b").apply(coarse("1 2"))""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.coarse } shouldBe listOf(1.0, 2.0)
    }

    "coarse() works within compiled code as chained-level function" {
        val p = SprudelPattern.compile("""note("a b").coarse("1 2")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.coarse } shouldBe listOf(1.0, 2.0)
    }

    "room() works within compiled code as top-level PatternMapper" {
        val p = SprudelPattern.compile("""note("a b").apply(room("0.1 0.9"))""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.room } shouldBe listOf(0.1, 0.9)
    }

    "room() works within compiled code as chained-level function" {
        val p = SprudelPattern.compile("""note("a b").room("0.1 0.9")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.room } shouldBe listOf(0.1, 0.9)
    }

    "room(size = ...) works within compiled code as top-level PatternMapper" {
        val p = SprudelPattern.compile("""note("a b").apply(room(size = "0.2 0.8"))""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.roomSize } shouldBe listOf(0.2, 0.8)
    }

    "room(size = ...) works within compiled code as chained-level function" {
        val p = SprudelPattern.compile("""note("a b").room(size = "0.2 0.8")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.roomSize } shouldBe listOf(0.2, 0.8)
    }

    "delay() works within compiled code as top-level function" {
        val p = SprudelPattern.compile("""note("a b").apply(delay("0.0 1.0"))""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.delay } shouldBe listOf(0.0, 1.0)
    }

    "delay() works within compiled code as chained-level function" {
        val p = SprudelPattern.compile("""note("a b").delay("0.0 1.0")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.delay } shouldBe listOf(0.0, 1.0)
    }

    "delay(time = ...) works within compiled code as top-level function" {
        val p = SprudelPattern.compile("""note("a b").apply(delay(time = "0.125 0.25"))""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.delayTime } shouldBe listOf(0.125, 0.25)
    }

    "delay(time = ...) works within compiled code as chained-level function" {
        val p = SprudelPattern.compile("""note("a b").delay(time = "0.125 0.25")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.delayTime } shouldBe listOf(0.125, 0.25)
    }

    "delay(feedback = ...) works within compiled code as top-level function" {
        val p = SprudelPattern.compile("""note("a b").apply(delay(feedback = "0.25 0.75"))""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.delayFeedback } shouldBe listOf(0.25, 0.75)
    }

    "delay(feedback = ...) works within compiled code as chained-level function" {
        val p = SprudelPattern.compile("""note("a b").delay(feedback = "0.25 0.75")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.delayFeedback } shouldBe listOf(0.25, 0.75)
    }

    "orbit() works within compiled code as top-level function" {
        val p = SprudelPattern.compile("""note("a b").apply(orbit("0 2"))""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.cylinder } shouldBe listOf(0, 2)
    }

    "orbit() works within compiled code as chained-level function" {
        val p = SprudelPattern.compile("""note("a b").orbit("0 2")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.cylinder } shouldBe listOf(0, 2)
    }

    // PatternMapperFn chaining
    "delay().delay(time = ..., feedback = ...) can be chained as PatternMapperFn" {
        val p = note("c3 e3").apply(delay(0.5).delay(time = 0.25, feedback = 0.6))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.delay shouldBe 0.5
        events[0].data.delayTime shouldBe 0.25
        events[0].data.delayFeedback shouldBe 0.6
    }

    "room().room(size = ...) can be chained as PatternMapperFn" {
        val p = note("c3 e3").apply(room(0.5).room(size = 4.0))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.room shouldBe 0.5
        events[0].data.roomSize shouldBe 4.0
    }

    "phaser().phaser(wet = ...) can be chained as PatternMapperFn" {
        val p = note("c3 e3").apply(phaser(0.5).phaser(wet = 0.8))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.phaserRate shouldBe 0.5
        events[0].data.phaserDepth shouldBe 0.8
    }

    "tremolo(sync = ...).tremolo(depth = ...) can be chained as PatternMapperFn" {
        val p = note("c3 e3").apply(tremolo(sync = 4.0).tremolo(depth = 0.8))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.tremoloSync shouldBe 4.0
        events[0].data.tremoloDepth shouldBe 0.8
    }
})
