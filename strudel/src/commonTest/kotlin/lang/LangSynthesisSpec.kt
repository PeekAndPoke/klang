package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern.Companion.compile

class LangSynthesisSpec : FunSpec({

    // /////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // FM Synthesis
    // /////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    test("fmh() should set harmonicity ratio") {
        val pat = compile("""note("c3").fmh(2)""")!!
        val events = pat.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events.first().data.fmh shouldBe 2.0
    }

    test("fmh() should work with decimal ratios") {
        val pat = compile("""note("c3").fmh(1.5)""")!!
        val events = pat.queryArc(0.0, 1.0)

        events.first().data.fmh shouldBe 1.5
    }

    test("fmh() should work as standalone function") {
        val pat = compile("""fmh(2.5)""")!!
        val events = pat.queryArc(0.0, 1.0)

        events.first().data.fmh shouldBe 2.5
    }

    test("fmattack() should set FM attack time") {
        val pat = compile("""note("c3").fmattack(0.1)""")!!
        val events = pat.queryArc(0.0, 1.0)

        events.first().data.fmAttack shouldBe 0.1
    }

    test("fmatt() alias should work") {
        val pat = compile("""note("c3").fmatt(0.05)""")!!
        val events = pat.queryArc(0.0, 1.0)

        events.first().data.fmAttack shouldBe 0.05
    }

    test("fmdecay() should set FM decay time") {
        val pat = compile("""note("c3").fmdecay(0.2)""")!!
        val events = pat.queryArc(0.0, 1.0)

        events.first().data.fmDecay shouldBe 0.2
    }

    test("fmdec() alias should work") {
        val pat = compile("""note("c3").fmdec(0.15)""")!!
        val events = pat.queryArc(0.0, 1.0)

        events.first().data.fmDecay shouldBe 0.15
    }

    test("fmsustain() should set FM sustain level") {
        val pat = compile("""note("c3").fmsustain(0.7)""")!!
        val events = pat.queryArc(0.0, 1.0)

        events.first().data.fmSustain shouldBe 0.7
    }

    test("fmsus() alias should work") {
        val pat = compile("""note("c3").fmsus(0.5)""")!!
        val events = pat.queryArc(0.0, 1.0)

        events.first().data.fmSustain shouldBe 0.5
    }

    test("fmenv() should set FM modulation depth") {
        val pat = compile("""note("c3").fmenv(100)""")!!
        val events = pat.queryArc(0.0, 1.0)

        events.first().data.fmEnv shouldBe 100.0
    }

    test("fmmod() alias should work") {
        val pat = compile("""note("c3").fmmod(200)""")!!
        val events = pat.queryArc(0.0, 1.0)

        events.first().data.fmEnv shouldBe 200.0
    }

    test("fmenv() should work with high modulation values") {
        val pat = compile("""note("c3").fmenv(500)""")!!
        val events = pat.queryArc(0.0, 1.0)

        events.first().data.fmEnv shouldBe 500.0
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Integration Tests
    // /////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    test("FM envelope chain should set all FM parameters together") {
        val pat = compile(
            """
            note("c3").fmh(2).fmattack(0.01).fmdecay(0.1).fmsustain(0.5).fmenv(100)
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

    test("FM envelope chain should work with abbreviated aliases") {
        val pat = compile(
            """
            note("c3").fmh(1.5).fmatt(0.02).fmdec(0.2).fmsus(0.7).fmmod(150)
        """.trimIndent()
        )!!
        val events = pat.queryArc(0.0, 1.0)

        val data = events.first().data
        data.fmh shouldBe 1.5
        data.fmAttack shouldBe 0.02
        data.fmDecay shouldBe 0.2
        data.fmSustain shouldBe 0.7
        data.fmEnv shouldBe 150.0
    }
})
