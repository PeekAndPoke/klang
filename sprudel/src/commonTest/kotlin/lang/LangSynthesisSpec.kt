/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.SprudelPattern.Companion.compile

class LangSynthesisSpec : FunSpec({

    // /////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // FM Synthesis
    // /////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    test("fm(h = ...) should set harmonicity ratio") {
        val pat = compile("""note("c3").fm(h = 2)""")!!
        val events = pat.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events.first().data.fmh shouldBe 2.0
    }

    test("fm(h = ...) should work with decimal ratios") {
        val pat = compile("""note("c3").fm(h = 1.5)""")!!
        val events = pat.queryArc(0.0, 1.0)

        events.first().data.fmh shouldBe 1.5
    }

    test("fm(h = ...) should work as standalone function") {
        val pat = compile("""note("a").apply(fm(h = 2.5))""")!!
        val events = pat.queryArc(0.0, 1.0)

        events.first().data.fmh shouldBe 2.5
    }

    test("fm(attack = ...) should set FM attack time") {
        val pat = compile("""note("c3").fm(attack = 0.1)""")!!
        val events = pat.queryArc(0.0, 1.0)

        events.first().data.fmAttack shouldBe 0.1
    }

    test("fm(decay = ...) should set FM decay time") {
        val pat = compile("""note("c3").fm(decay = 0.2)""")!!
        val events = pat.queryArc(0.0, 1.0)

        events.first().data.fmDecay shouldBe 0.2
    }

    test("fm(sustain = ...) should set FM sustain level") {
        val pat = compile("""note("c3").fm(sustain = 0.7)""")!!
        val events = pat.queryArc(0.0, 1.0)

        events.first().data.fmSustain shouldBe 0.7
    }

    test("fm() should set FM modulation depth") {
        val pat = compile("""note("c3").fm(100)""")!!
        val events = pat.queryArc(0.0, 1.0)

        events.first().data.fmEnv shouldBe 100.0
    }

    test("fm() should work with high modulation values") {
        val pat = compile("""note("c3").fm(500)""")!!
        val events = pat.queryArc(0.0, 1.0)

        events.first().data.fmEnv shouldBe 500.0
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Integration Tests
    // /////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    test("FM envelope chain should set all FM parameters together") {
        val pat = compile(
            """
            note("c3").fm(h = 2, attack = 0.01, decay = 0.1, sustain = 0.5, env = 100)
        """.trimIndent()
        )!!
        val events = pat.queryArc(0.0, 1.0)

        val data = events.first().data
        data.fmh shouldBe 2.0
        data.fmAttack shouldBe 0.01
        data.fmDecay shouldBe 0.1
        data.fmSustain shouldBe 0.5
        data.fmEnv shouldBe 100.0
    }

})
