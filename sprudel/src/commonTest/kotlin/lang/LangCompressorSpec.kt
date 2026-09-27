/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_RATIO

class LangCompressorSpec : StringSpec({

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

    "compressor() keeps a prior value on unparseable input" {
        val p = note("c").compressor(-20).compressor("oops")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.katalystParams?.get("compressor.threshold") shouldBe -20.0
    }
})
