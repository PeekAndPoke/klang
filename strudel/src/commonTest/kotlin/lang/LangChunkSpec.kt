package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern.Companion.compile

class LangChunkSpec : FunSpec({

    test("chunk(4) should transform each quarter sequentially") {
        val pat = compile("""seq("0 1 2 3").chunk(4, x => x.add(12))""")!!

        // Cycle 0: First quarter transformed (0 -> 12)
        val events0 = pat.queryArc(0.0, 1.0)
        events0.map { it.data.value?.asInt } shouldBe listOf(12, 1, 2, 3)

        // Cycle 1: Second quarter transformed (1 -> 13)
        val events1 = pat.queryArc(1.0, 2.0)
        events1.map { it.data.value?.asInt } shouldBe listOf(0, 1, 2, 15)

        // Cycle 2: Third quarter transformed (2 -> 14)
        val events2 = pat.queryArc(2.0, 3.0)
        events2.map { it.data.value?.asInt } shouldBe listOf(0, 1, 14, 3)

        // Cycle 3: Fourth quarter transformed (3 -> 15)
        val events3 = pat.queryArc(3.0, 4.0)
        events3.map { it.data.value?.asInt } shouldBe listOf(0, 13, 2, 3)

        // Cycle 4: Fourth quarter transformed (0 -> 12)
        val events4 = pat.queryArc(4.0, 5.0)
        events4.map { it.data.value?.asInt } shouldBe listOf(12, 1, 2, 3)
    }

    test("chunk(2) should transform each half sequentially") {
        val pat = compile("""s("bd hh sd oh").chunk(2, x => x.fast(2))""")!!

        // Cycle 0: First half doubled
        val events0 = pat.queryArc(0.0, 1.0)
        events0.size shouldBe 6  // bd bd hh sd oh (bd doubled)

        // Cycle 1: Second half doubled
        val events1 = pat.queryArc(1.0, 2.0)
        events1.size shouldBe 6  // bd hh sd sd oh (sd doubled)
    }

    test("chunk() aliases should work") {
        val pat1 = compile("""note("c d e f").slowchunk(4, x => x.transpose(12))""")!!
        val pat2 = compile("""note("c d e f").slowChunk(4, x => x.transpose(12))""")!!
        val pat3 = compile("""note("c d e f").chunk(4, x => x.transpose(12))""")!!

        val events1 = pat1.queryArc(0.0, 1.0)
        val events2 = pat2.queryArc(0.0, 1.0)
        val events3 = pat3.queryArc(0.0, 1.0)

        events1.map { it.data.note } shouldBe events2.map { it.data.note }
        events2.map { it.data.note } shouldBe events3.map { it.data.note }
    }

    test("chunk() should work with Kotlin function call syntax") {
        val pat = seq("0 1 2 3").chunk(4) { it.add(12) }

        val events0 = pat.queryArc(0.0, 1.0)
        events0.size shouldBe 4
        events0[0].data.value?.asInt shouldBe 12  // 0 + 12
    }

    test("chunk(n) should cycle after n cycles") {
        val pat = compile("""seq("0 1 2 3").chunk(4, x => x.add(12))""")!!

        // Cycle 4 should be the same as cycle 0
        val events0 = pat.queryArc(0.0, 1.0)
        val events4 = pat.queryArc(4.0, 5.0)

        events0.map { it.data.value } shouldBe events4.map { it.data.value }
    }
})
