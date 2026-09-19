/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_ATTACK_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_KNEE_DB
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_RATIO
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_RELEASE_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_THRESHOLD_DB
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
            events[0].data.katalystParams?.get("compressor.threshold") shouldBe -10.0
            events[0].data.katalystParams?.get("compressor.ratio") shouldBe 2.0
            events[1].data.katalystParams?.get("compressor.threshold") shouldBe -30.0
            events[1].data.katalystParams?.get("compressor.ratio") shouldBe 4.0
        }
    }

    "compressor() sets all five slots" {
        val p = note("c").compressor(-20, 4, 6, 0.003, 0.1)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0].data) {
            katalystParams?.get("compressor.threshold") shouldBe -20.0
            katalystParams?.get("compressor.ratio") shouldBe 4.0
            katalystParams?.get("compressor.knee") shouldBe 6.0
            katalystParams?.get("compressor.attack") shouldBe 0.003
            katalystParams?.get("compressor.release") shouldBe 0.1
        }
    }

    "compressor() with leading params only fills the rest with the shared constants" {
        // Katalyst step 5a-3: the compound-door fill rule. Byte-identical to what the engine did,
        // because `Voice.Compressor.fromParams` substituted exactly these three for a null field.
        val p = note("c").compressor(-15, 3)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0].data) {
            katalystParams?.get("compressor.threshold") shouldBe -15.0
            katalystParams?.get("compressor.ratio") shouldBe 3.0
            katalystParams?.get("compressor.knee") shouldBe COMPRESSOR_KNEE_DB
            katalystParams?.get("compressor.attack") shouldBe COMPRESSOR_ATTACK_SECONDS
            katalystParams?.get("compressor.release") shouldBe COMPRESSOR_RELEASE_SECONDS
        }
    }

    "compressor() with named params fills the ones it skipped" {
        val p = SprudelPattern.compile("""note("c").compressor(knee = 2, release = 0.5)""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 1
        with(events[0].data) {
            katalystParams?.get("compressor.threshold") shouldBe COMPRESSOR_THRESHOLD_DB
            katalystParams?.get("compressor.ratio") shouldBe COMPRESSOR_RATIO
            katalystParams?.get("compressor.knee") shouldBe 2.0
            katalystParams?.get("compressor.release") shouldBe 0.5
        }
    }

    "compressor() params are independently patternable" {
        val p = note("c d").compressor("-10 -30", ratio = 4)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.katalystParams?.get("compressor.threshold") shouldBe -10.0
        events[1].data.katalystParams?.get("compressor.threshold") shouldBe -30.0
        events[0].data.katalystParams?.get("compressor.ratio") shouldBe 4.0
        events[1].data.katalystParams?.get("compressor.ratio") shouldBe 4.0
    }

    "compressor() alternation form selects per cycle" {
        val p = note("c").compressor("<-10 -30>", "<2 8>")
        val c0 = p.queryArc(0.0, 1.0)
        val c1 = p.queryArc(1.0, 2.0)

        assertSoftly {
            c0.size shouldBe 1
            c1.size shouldBe 1
            c0[0].data.katalystParams?.get("compressor.threshold") shouldBe -10.0
            c0[0].data.katalystParams?.get("compressor.ratio") shouldBe 2.0
            c1[0].data.katalystParams?.get("compressor.threshold") shouldBe -30.0
            c1[0].data.katalystParams?.get("compressor.ratio") shouldBe 8.0
        }
    }

    "bare compressor() reinterprets the pattern's values as the threshold, like every compound head (2026-09-07)" {
        val p = seq("3 4").compressor()
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.katalystParams?.get("compressor.threshold") } shouldBe listOf(3.0, 4.0)
        events[0].data.katalystParams?.get("compressor.ratio") shouldBe COMPRESSOR_RATIO
    }

    "comp() alias reaches the same slots" {
        val p = SprudelPattern.compile("""note("c").comp(-20, 4, 3, 0.01, 0.3)""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 1
        with(events[0].data) {
            katalystParams?.get("compressor.threshold") shouldBe -20.0
            katalystParams?.get("compressor.ratio") shouldBe 4.0
            katalystParams?.get("compressor.knee") shouldBe 3.0
            katalystParams?.get("compressor.attack") shouldBe 0.01
            katalystParams?.get("compressor.release") shouldBe 0.3
        }
    }

    "compressor() keeps a prior value on unparseable input" {
        val p = note("c").compressor(-20).compressor("oops")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.katalystParams?.get("compressor.threshold") shouldBe -20.0
    }
})
