package io.peekandpoke.klang.strudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.strudel.StrudelPattern.Companion.compile
import io.peekandpoke.klang.strudel.StrudelVoiceValue

class LangChunkSpec : FunSpec({

    test("chunk(4) should transform each quarter sequentially") {
        val pat = seq("0 0 0 0").chunk(4) { it.add(1) }

        assertSoftly {
            repeat(16) { cycle ->
                withClue("Cycle $cycle") {
                    val events = pat.queryArc(cycle.toDouble(), cycle.toDouble() + 1.0)
                    events.size shouldBe 4

                    events.forEachIndexed { index, value ->
                        withClue("Event $index should have a num voice value") {
                            value.data.value?.shouldBeInstanceOf<StrudelVoiceValue.Num>()
                        }
                    }

                    val values = events.map { it.data.value?.asInt }

                    val expected = when (cycle % 4) {
                        0 -> listOf(1, 0, 0, 0)
                        1 -> listOf(0, 1, 0, 0)
                        2 -> listOf(0, 0, 1, 0)
                        else -> listOf(0, 0, 0, 1)
                    }

                    values shouldBe expected
                }
            }
        }
    }

    test("chunk(4) should transform each quarter sequentially - compiled") {
        val pat = compile("""seq("0 0 0 0").chunk(4, x => x.add(1))""")!!

        assertSoftly {
            repeat(16) { cycle ->
                withClue("Cycle $cycle") {
                    val events = pat.queryArc(cycle.toDouble(), cycle.toDouble() + 1.0)
                    events.size shouldBe 4

                    val values = events.map { it.data.value?.asInt }

                    val expected = when (cycle % 4) {
                        0 -> listOf(1, 0, 0, 0)
                        1 -> listOf(0, 1, 0, 0)
                        2 -> listOf(0, 0, 1, 0)
                        else -> listOf(0, 0, 0, 1)
                    }

                    values shouldBe expected
                }
            }
        }
    }

    test("chunk(2) should transform each half sequentially") {
        val pat = compile("""s("bd hh sd oh").chunk(2, x => x.fast(2))""")!!

        assertSoftly {
            // Cycle 0: First half doubled
            val events0 = pat.queryArc(0.0, 1.0)
            events0.size shouldBe 6  // bd bd hh hh sd oh

            // Cycle 1: Second half doubled
            val events1 = pat.queryArc(1.0, 2.0)
            events1.size shouldBe 6  // bd hh sd sd oh hh
        }
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
