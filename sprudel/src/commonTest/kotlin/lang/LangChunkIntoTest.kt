package io.peekandpoke.klang.strudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEqualIgnoringCase
import io.peekandpoke.klang.strudel.EPSILON

class LangChunkIntoSpec : StringSpec({

    "seq(\"0 1 2 3\").chunkInto(4) { it.add(10) } doesn't repeat pattern" {
        val subject = seq("0 1 2 3").chunkInto(4) { it.add(10) }

        assertSoftly {
            repeat(12) { cycle ->
                withClue("Cycle $cycle") {
                    val cycleDbl = cycle.toDouble()
                    val events = subject.queryArc(cycleDbl, cycleDbl + 1).filter { it.isOnset }

                    // chunkInto (like fastChunk) doesn't repeat the pattern, so we get 4 events in cycle 0.
                    // The pattern has 4 elements total, so it fits perfectly.
                    events.size shouldBe 4

                    // chunkInto applies transformation to chunks in forward order
                    // without repeating the source pattern structure itself (unlike chunk)
                    when (cycle % 4) {
                        0 -> {
                            // Chunk 0: first element transformed (0 + 10 = 10)
                            events[0].data.value?.asInt shouldBe 10
                            events[1].data.value?.asInt shouldBe 1
                            events[2].data.value?.asInt shouldBe 2
                            events[3].data.value?.asInt shouldBe 3
                        }

                        1 -> {
                            // Chunk 1: second element transformed (1 + 10 = 11)
                            events[0].data.value?.asInt shouldBe 0
                            events[1].data.value?.asInt shouldBe 11
                            events[2].data.value?.asInt shouldBe 2
                            events[3].data.value?.asInt shouldBe 3
                        }

                        2 -> {
                            // Chunk 2: third element transformed (2 + 10 = 12)
                            events[0].data.value?.asInt shouldBe 0
                            events[1].data.value?.asInt shouldBe 1
                            events[2].data.value?.asInt shouldBe 12
                            events[3].data.value?.asInt shouldBe 3
                        }

                        3 -> {
                            // Chunk 3: fourth element transformed (3 + 10 = 13)
                            events[0].data.value?.asInt shouldBe 0
                            events[1].data.value?.asInt shouldBe 1
                            events[2].data.value?.asInt shouldBe 2
                            events[3].data.value?.asInt shouldBe 13
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

    "s(\"bd sd\").chunkInto(2) { it.gain(0.8) } works with sound patterns" {
        val subject = s("bd sd").chunkInto(2) { it.gain(0.8) }

        assertSoftly {
            repeat(6) { cycle ->
                withClue("Cycle $cycle") {
                    val cycleDbl = cycle.toDouble()
                    val events = subject.queryArc(cycleDbl, cycleDbl + 1).filter { it.isOnset }

                    events.size shouldBe 2

                    // Verify sounds are present
                    val sounds = events.map { it.data.sound }
                    sounds.filterNotNull().size shouldBe 2

                    // Verify gain is applied to the correct chunk
                    when (cycle % 2) {
                        0 -> {
                            // Chunk 0: first element gets gain
                            events[0].data.gain shouldBe 0.8
                            events[1].data.gain shouldBe null
                            events[0].data.sound shouldBeEqualIgnoringCase "bd"
                            events[1].data.sound shouldBeEqualIgnoringCase "sd"
                        }

                        1 -> {
                            // Chunk 1: second element gets gain
                            events[0].data.gain shouldBe null
                            events[1].data.gain shouldBe 0.8
                            events[0].data.sound shouldBeEqualIgnoringCase "bd"
                            events[1].data.sound shouldBeEqualIgnoringCase "sd"
                        }
                    }
                }
            }
        }
    }

    "note(\"c4 d4 e4 f4\").chunkinto(4) { it.transpose(12) } lowercase alias works" {
        val subject = note("c4 d4 e4 f4").chunkinto(4) { it.transpose(12) }

        assertSoftly {
            repeat(4) { cycle ->
                withClue("Cycle $cycle") {
                    val cycleDbl = cycle.toDouble()
                    val events = subject.queryArc(cycleDbl, cycleDbl + 1).filter { it.isOnset }

                    events.size shouldBe 4

                    when (cycle % 4) {
                        0 -> events[0].data.note shouldBeEqualIgnoringCase "c5"
                        1 -> events[1].data.note shouldBeEqualIgnoringCase "d5"
                        2 -> events[2].data.note shouldBeEqualIgnoringCase "e5"
                        3 -> events[3].data.note shouldBeEqualIgnoringCase "f5"
                    }
                }
            }
        }
    }

    "seq(\"<0 5> 1 2\").chunkInto(3) { it.add(10) } with varying pattern" {
        val subject = seq("<0 5> 1 2").chunkInto(3) { it.add(10) }

        assertSoftly {
            repeat(6) { cycle ->
                withClue("Cycle $cycle") {
                    val cycleDbl = cycle.toDouble()
                    val events = subject.queryArc(cycleDbl, cycleDbl + 1).filter { it.isOnset }

                    // Pattern "<0 5> 1 2" has 3 events per cycle.
                    // The <0 5> alternates between 0 (even cycles) and 5 (odd cycles).
                    // chunkInto(3) cycles through which element to transform.
                    events.size shouldBe 3

                    when (cycle % 3) {
                        0 -> {
                            // Chunk 0 transformed: first element gets +10
                            val expectedFirst = if (cycle % 2 == 0) 10 else 15
                            events[0].data.value?.asInt shouldBe expectedFirst
                            events[1].data.value?.asInt shouldBe 1
                            events[2].data.value?.asInt shouldBe 2
                        }

                        1 -> {
                            // Chunk 1 transformed: second element gets +10
                            val expectedFirst = if (cycle % 2 == 0) 0 else 5
                            events[0].data.value?.asInt shouldBe expectedFirst
                            events[1].data.value?.asInt shouldBe 11
                            events[2].data.value?.asInt shouldBe 2
                        }

                        2 -> {
                            // Chunk 2 transformed: third element gets +10
                            val expectedFirst = if (cycle % 2 == 0) 0 else 5
                            events[0].data.value?.asInt shouldBe expectedFirst
                            events[1].data.value?.asInt shouldBe 1
                            events[2].data.value?.asInt shouldBe 12
                        }
                    }
                }
            }
        }
    }
})
