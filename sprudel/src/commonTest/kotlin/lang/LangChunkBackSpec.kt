package io.peekandpoke.klang.strudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEqualIgnoringCase
import io.peekandpoke.klang.strudel.EPSILON

class LangChunkBackSpec : StringSpec({

    "seq(\"0 1 2 3\").chunkBack(4) { it.add(7) } cycles backward through chunks" {
        val subject = seq("0 1 2 3").chunkBack(4) { it.add(7) }

        assertSoftly {
            repeat(12) { cycle ->
                withClue("Cycle $cycle") {
                    val cycleDbl = cycle.toDouble()
                    val events = subject.queryArc(cycleDbl, cycleDbl + 1).filter { it.isOnset }

                    events.forEachIndexed { index, event ->
                        println(
                            "Cycle $cycle, Event ${index + 1}: value: ${event.data.value?.asInt} | " +
                                    "part: [${event.part.begin}, ${event.part.end}] | " +
                                    "whole: [${event.whole.begin}, ${event.whole.end}]"
                        )
                    }

                    events.size shouldBe 4

                    // chunkBack cycles backward through chunks
                    // Cycle 0 mod 4 = 0 -> chunk 0 (first element) transformed: [7, 1, 2, 3]
                    // Cycle 1 mod 4 = 1 -> chunk 3 (last element) transformed: [0, 1, 2, 10]
                    // Cycle 2 mod 4 = 2 -> chunk 2 (third element) transformed: [0, 1, 9, 3]
                    // Cycle 3 mod 4 = 3 -> chunk 1 (second element) transformed: [0, 8, 2, 3]
                    when (cycle % 4) {
                        0 -> {
                            // Chunk 0: first element transformed (0 + 7 = 7)
                            events[0].data.value?.asInt shouldBe 7
                            events[1].data.value?.asInt shouldBe 1
                            events[2].data.value?.asInt shouldBe 2
                            events[3].data.value?.asInt shouldBe 3
                        }

                        1 -> {
                            // Chunk 3: last element transformed (3 + 7 = 10)
                            events[0].data.value?.asInt shouldBe 0
                            events[1].data.value?.asInt shouldBe 1
                            events[2].data.value?.asInt shouldBe 2
                            events[3].data.value?.asInt shouldBe 10
                        }

                        2 -> {
                            // Chunk 2: third element transformed (2 + 7 = 9)
                            events[0].data.value?.asInt shouldBe 0
                            events[1].data.value?.asInt shouldBe 1
                            events[2].data.value?.asInt shouldBe 9
                            events[3].data.value?.asInt shouldBe 3
                        }

                        3 -> {
                            // Chunk 1: second element transformed (1 + 7 = 8)
                            events[0].data.value?.asInt shouldBe 0
                            events[1].data.value?.asInt shouldBe 8
                            events[2].data.value?.asInt shouldBe 2
                            events[3].data.value?.asInt shouldBe 3
                        }
                    }

                    // Verify timing - each event at 0.25 intervals
                    events[0].part.begin.toDouble() shouldBe ((cycleDbl + 0.0) plusOrMinus EPSILON)
                    events[0].part.end.toDouble() shouldBe ((cycleDbl + 0.25) plusOrMinus EPSILON)

                    events[1].part.begin.toDouble() shouldBe ((cycleDbl + 0.25) plusOrMinus EPSILON)
                    events[1].part.end.toDouble() shouldBe ((cycleDbl + 0.5) plusOrMinus EPSILON)

                    events[2].part.begin.toDouble() shouldBe ((cycleDbl + 0.5) plusOrMinus EPSILON)
                    events[2].part.end.toDouble() shouldBe ((cycleDbl + 0.75) plusOrMinus EPSILON)

                    events[3].part.begin.toDouble() shouldBe ((cycleDbl + 0.75) plusOrMinus EPSILON)
                    events[3].part.end.toDouble() shouldBe ((cycleDbl + 1.0) plusOrMinus EPSILON)
                }
            }
        }
    }

    "s(\"bd sd ht lt\").chunkBack(4) { it.gain(0.8) } works with sound patterns" {
        val subject = s("bd sd ht lt").chunkBack(4) { it.gain(0.8) }

        assertSoftly {
            repeat(12) { cycle ->
                withClue("Cycle $cycle") {
                    val cycleDbl = cycle.toDouble()
                    val events = subject.queryArc(cycleDbl, cycleDbl + 1).filter { it.isOnset }

                    events.size shouldBe 4

                    // Verify sounds are present
                    val sounds = events.map { it.data.sound }
                    sounds.filterNotNull().size shouldBe 4

                    // Verify gain is applied to the correct chunk based on backward iteration
                    when (cycle % 4) {
                        0 -> {
                            // Chunk 0: first element gets gain
                            events[0].data.gain shouldBe 0.8
                            events[1].data.gain shouldBe null
                            events[2].data.gain shouldBe null
                            events[3].data.gain shouldBe null
                            events[0].data.sound shouldBeEqualIgnoringCase "bd"
                        }

                        1 -> {
                            // Chunk 3: last element gets gain
                            events[0].data.gain shouldBe null
                            events[1].data.gain shouldBe null
                            events[2].data.gain shouldBe null
                            events[3].data.gain shouldBe 0.8
                            events[3].data.sound shouldBeEqualIgnoringCase "lt"
                        }

                        2 -> {
                            // Chunk 2: third element gets gain
                            events[0].data.gain shouldBe null
                            events[1].data.gain shouldBe null
                            events[2].data.gain shouldBe 0.8
                            events[3].data.gain shouldBe null
                            events[2].data.sound shouldBeEqualIgnoringCase "ht"
                        }

                        3 -> {
                            // Chunk 1: second element gets gain
                            events[0].data.gain shouldBe null
                            events[1].data.gain shouldBe 0.8
                            events[2].data.gain shouldBe null
                            events[3].data.gain shouldBe null
                            events[1].data.sound shouldBeEqualIgnoringCase "sd"
                        }
                    }
                }
            }
        }
    }

    "note(\"c d e f\").chunkback(4) { it.transpose(12) } lowercase alias works" {
        val subject = note("c4 d4 e4 f4").chunkback(4) { it.transpose(12) }

        assertSoftly {
            repeat(12) { cycle ->
                withClue("Cycle $cycle") {
                    val cycleDbl = cycle.toDouble()
                    val events = subject.queryArc(cycleDbl, cycleDbl + 1).filter { it.isOnset }

                    events.size shouldBe 4

                    // Verify notes are present
                    val notes = events.map { it.data.note }
                    notes.filterNotNull().size shouldBe 4

                    // chunkback should behave the same as chunkBack
                    when (cycle % 4) {
                        0 -> events[0].data.note shouldBeEqualIgnoringCase "c5"
                        1 -> events[3].data.note shouldBeEqualIgnoringCase "f5"
                        2 -> events[2].data.note shouldBeEqualIgnoringCase "e5"
                        3 -> events[1].data.note shouldBeEqualIgnoringCase "d5"
                    }
                }
            }
        }
    }

    "seq(\"0 1\").chunkBack(2) { it.add(10) } works with smaller chunk count" {
        val subject = seq("0 1").chunkBack(2) { it.add(10) }

        assertSoftly {
            repeat(12) { cycle ->
                withClue("Cycle $cycle") {
                    val cycleDbl = cycle.toDouble()
                    val events = subject.queryArc(cycleDbl, cycleDbl + 1).filter { it.isOnset }

                    events.size shouldBe 2

                    when (cycle % 2) {
                        0 -> {
                            // Chunk 0: first element transformed
                            events[0].data.value?.asInt shouldBe 10
                            events[1].data.value?.asInt shouldBe 1
                        }

                        1 -> {
                            // Chunk 1: second element transformed (backward)
                            events[0].data.value?.asInt shouldBe 0
                            events[1].data.value?.asInt shouldBe 11
                        }
                    }
                }
            }
        }
    }
})
