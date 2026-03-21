package io.peekandpoke.klang.strudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEqualIgnoringCase
import io.peekandpoke.klang.strudel.EPSILON

class LangChunkBackIntoSpec : StringSpec({

    "seq(\"0 1 2 3\").chunkBackInto(4) { it.add(10) } cycles backward starting from last chunk" {
        val subject = seq("0 1 2 3").chunkBackInto(4) { it.add(10) }

        assertSoftly {
            repeat(12) { cycle ->
                withClue("Cycle $cycle") {
                    val cycleDbl = cycle.toDouble()
                    val events = subject.queryArc(cycleDbl, cycleDbl + 1).filter { it.isOnset }

                    events.size shouldBe 4

                    // chunkBackInto:
                    // 1. fast=true (no repeatCycles, pattern plays naturally)
                    // 2. back=true (iterates chunks using iter logic)
                    // 3. early(1) (shifts iteration by 1 cycle)
                    // Sequence of transformed chunks: 3 (last), 2, 1, 0

                    when (cycle % 4) {
                        0 -> {
                            // Cycle 0 -> Chunk 3 transformed (last element)
                            // [0, 1, 2, 13]
                            events[0].data.value?.asInt shouldBe 0
                            events[1].data.value?.asInt shouldBe 1
                            events[2].data.value?.asInt shouldBe 2
                            events[3].data.value?.asInt shouldBe 13
                        }

                        1 -> {
                            // Cycle 1 -> Chunk 2 transformed
                            // [0, 1, 12, 3]
                            events[0].data.value?.asInt shouldBe 0
                            events[1].data.value?.asInt shouldBe 1
                            events[2].data.value?.asInt shouldBe 12
                            events[3].data.value?.asInt shouldBe 3
                        }

                        2 -> {
                            // Cycle 2 -> Chunk 1 transformed
                            // [0, 11, 2, 3]
                            events[0].data.value?.asInt shouldBe 0
                            events[1].data.value?.asInt shouldBe 11
                            events[2].data.value?.asInt shouldBe 2
                            events[3].data.value?.asInt shouldBe 3
                        }

                        3 -> {
                            // Cycle 3 -> Chunk 0 transformed (first element)
                            // [10, 1, 2, 3]
                            events[0].data.value?.asInt shouldBe 10
                            events[1].data.value?.asInt shouldBe 1
                            events[2].data.value?.asInt shouldBe 2
                            events[3].data.value?.asInt shouldBe 3
                        }
                    }

                    // Verify timing - standard speed
                    events[0].part.begin.toDouble() shouldBe ((cycleDbl + 0.0) plusOrMinus EPSILON)
                    events[0].part.end.toDouble() shouldBe ((cycleDbl + 0.25) plusOrMinus EPSILON)
                }
            }
        }
    }

    "s(\"bd sd\").chunkBackInto(2) { it.gain(0.8) } works with sound patterns" {
        val subject = s("bd sd").chunkBackInto(2) { it.gain(0.8) }

        assertSoftly {
            repeat(6) { cycle ->
                withClue("Cycle $cycle") {
                    val cycleDbl = cycle.toDouble()
                    val events = subject.queryArc(cycleDbl, cycleDbl + 1).filter { it.isOnset }

                    events.size shouldBe 2

                    // n=2.
                    // Cycle 0: Chunk 1 (last) -> "sd" modified
                    // Cycle 1: Chunk 0 (first) -> "bd" modified

                    when (cycle % 2) {
                        0 -> {
                            // Chunk 1: second element gets gain
                            events[0].data.gain shouldBe null
                            events[1].data.gain shouldBe 0.8
                            events[1].data.sound shouldBeEqualIgnoringCase "sd"
                        }

                        1 -> {
                            // Chunk 0: first element gets gain
                            events[0].data.gain shouldBe 0.8
                            events[1].data.gain shouldBe null
                            events[0].data.sound shouldBeEqualIgnoringCase "bd"
                        }
                    }
                }
            }
        }
    }

    "note(\"c4 d4 e4 f4\").chunkbackinto(4) { it.transpose(12) } lowercase alias works" {
        val subject = note("c4 d4 e4 f4").chunkbackinto(4) { it.transpose(12) }

        assertSoftly {
            repeat(4) { cycle ->
                withClue("Cycle $cycle") {
                    val cycleDbl = cycle.toDouble()
                    val events = subject.queryArc(cycleDbl, cycleDbl + 1).filter { it.isOnset }

                    events.size shouldBe 4

                    // Sequence: 3, 2, 1, 0
                    when (cycle % 4) {
                        0 -> events[3].data.note shouldBeEqualIgnoringCase "f5"
                        1 -> events[2].data.note shouldBeEqualIgnoringCase "e5"
                        2 -> events[1].data.note shouldBeEqualIgnoringCase "d5"
                        3 -> events[0].data.note shouldBeEqualIgnoringCase "c5"
                    }
                }
            }
        }
    }
})
