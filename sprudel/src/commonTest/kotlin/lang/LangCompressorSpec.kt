/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

class LangCompressorSpec : StringSpec({

    "compressor dsl interface" {
        val pat = "0 1"

        dslInterfaceTests(
            "pattern.compressor(t, r)" to
                    seq(pat).compressor("-10 -30", "2 4"),
            "script pattern.compressor(t, r)" to
                    SprudelPattern.compile("""seq("$pat").compressor("-10 -30", "2 4")"""),
            "string.compressor(t, r)" to
                    pat.compressor("-10 -30", "2 4"),
            "script string.compressor(t, r)" to
                    SprudelPattern.compile(""""$pat".compressor("-10 -30", "2 4")"""),
            "compressor(t, r)" to
                    seq(pat).apply(compressor("-10 -30", "2 4")),
            "script compressor(t, r)" to
                    SprudelPattern.compile("""seq("$pat").apply(compressor("-10 -30", "2 4"))"""),
            // comp alias
            "pattern.comp(t, r)" to
                    seq(pat).comp("-10 -30", "2 4"),
            "script pattern.comp(t, r)" to
                    SprudelPattern.compile("""seq("$pat").comp("-10 -30", "2 4")"""),
            "string.comp(t, r)" to
                    pat.comp("-10 -30", "2 4"),
            "script string.comp(t, r)" to
                    SprudelPattern.compile(""""$pat".comp("-10 -30", "2 4")"""),
            "comp(t, r)" to
                    seq(pat).apply(comp("-10 -30", "2 4")),
            "script comp(t, r)" to
                    SprudelPattern.compile("""seq("$pat").apply(comp("-10 -30", "2 4"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.compressorThreshold shouldBe -10.0
            events[0].data.compressorRatio shouldBe 2.0
            events[1].data.compressorThreshold shouldBe -30.0
            events[1].data.compressorRatio shouldBe 4.0
        }
    }

    "compressor() sets all five wire fields" {
        val p = note("c").compressor(-20, 4, 6, 0.003, 0.1)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0].data) {
            compressorThreshold shouldBe -20.0
            compressorRatio shouldBe 4.0
            compressorKnee shouldBe 6.0
            compressorAttack shouldBe 0.003
            compressorRelease shouldBe 0.1
        }
    }

    "compressor() with leading params only leaves the rest unset" {
        val p = note("c").compressor(-15, 3)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0].data) {
            compressorThreshold shouldBe -15.0
            compressorRatio shouldBe 3.0
            compressorKnee shouldBe null
            compressorAttack shouldBe null
            compressorRelease shouldBe null
        }
    }

    "compressor() with named params skips unset slots" {
        val p = SprudelPattern.compile("""note("c").compressor(knee = 2, release = 0.5)""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 1
        with(events[0].data) {
            compressorThreshold shouldBe null
            compressorRatio shouldBe null
            compressorKnee shouldBe 2.0
            compressorRelease shouldBe 0.5
        }
    }

    "compressor() params are independently patternable" {
        val p = note("c d").compressor("-10 -30", ratio = 4)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.compressorThreshold shouldBe -10.0
        events[1].data.compressorThreshold shouldBe -30.0
        events[0].data.compressorRatio shouldBe 4.0
        events[1].data.compressorRatio shouldBe 4.0
    }

    "compressor() alternation form selects per cycle" {
        val p = note("c").compressor("<-10 -30>", "<2 8>")
        val c0 = p.queryArc(0.0, 1.0)
        val c1 = p.queryArc(1.0, 2.0)

        assertSoftly {
            c0.size shouldBe 1
            c1.size shouldBe 1
            c0[0].data.compressorThreshold shouldBe -10.0
            c0[0].data.compressorRatio shouldBe 2.0
            c1[0].data.compressorThreshold shouldBe -30.0
            c1[0].data.compressorRatio shouldBe 8.0
        }
    }

    "bare compressor() reinterprets the pattern's values as the threshold, like every compound head (2026-09-07)" {
        val p = seq("3 4").compressor()
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.compressorThreshold } shouldBe listOf(3.0, 4.0)
        events[0].data.compressorRatio shouldBe null
    }

    "comp() alias reaches the same fields" {
        val p = SprudelPattern.compile("""note("c").comp(-20, 4, 3, 0.01, 0.3)""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 1
        with(events[0].data) {
            compressorThreshold shouldBe -20.0
            compressorRatio shouldBe 4.0
            compressorKnee shouldBe 3.0
            compressorAttack shouldBe 0.01
            compressorRelease shouldBe 0.3
        }
    }

    "compressor() keeps a prior value on unparseable input" {
        val p = note("c").compressor(-20).compressor("oops")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.compressorThreshold shouldBe -20.0
    }
})
