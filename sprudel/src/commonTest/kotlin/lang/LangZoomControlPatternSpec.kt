package io.peekandpoke.klang.strudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON

class LangZoomControlPatternSpec : StringSpec({

    "zoom with static values works" {
        val subject = s("bd sd ht lt").zoom(0.0, 0.5)

        assertSoftly {
            repeat(3) { cycle ->
                withClue("Cycle $cycle") {
                    val cycleDbl = cycle.toDouble()
                    val events = subject.queryArc(cycleDbl, cycleDbl + 1).filter { it.isOnset }

                    println("Cycle $cycle:")
                    events.forEachIndexed { i, e ->
                        println("  Event $i: sound=${e.data.sound}, part=[${e.part.begin}, ${e.part.end}]")
                    }

                    // zoom(0, 0.5) takes first half and stretches to full cycle
                    events.size shouldBe 2
                    events[0].data.sound shouldBe "bd"
                    events[1].data.sound shouldBe "sd"

                    events[0].part.begin.toDouble() shouldBe ((cycleDbl + 0.0) plusOrMinus EPSILON)
                    events[0].part.end.toDouble() shouldBe ((cycleDbl + 0.5) plusOrMinus EPSILON)

                    events[1].part.begin.toDouble() shouldBe ((cycleDbl + 0.5) plusOrMinus EPSILON)
                    events[1].part.end.toDouble() shouldBe ((cycleDbl + 1.0) plusOrMinus EPSILON)
                }
            }
        }
    }

    "zoom with control pattern for start parameter" {
        val subject = s("bd sd ht lt").zoom("<0 0.25>", 0.75)

        assertSoftly {
            repeat(6) { cycle ->
                withClue("Cycle $cycle") {
                    val cycleDbl = cycle.toDouble()
                    val events = subject.queryArc(cycleDbl, cycleDbl + 1).filter { it.isOnset }

                    println("Cycle $cycle (start=${if (cycle % 2 == 0) "0" else "0.25"}):")
                    events.forEachIndexed { i, e ->
                        println("  Event $i: sound=${e.data.sound}, part=[${e.part.begin}, ${e.part.end}]")
                    }

                    when (cycle % 2) {
                        0 -> {
                            // zoom(0, 0.75) - takes first 75%
                            events.size shouldBe 3
                            events[0].data.sound shouldBe "bd"
                            events[1].data.sound shouldBe "sd"
                            events[2].data.sound shouldBe "ht"
                        }

                        1 -> {
                            // zoom(0.25, 0.75) - takes middle 50%
                            events.size shouldBe 2
                            events[0].data.sound shouldBe "sd"
                            events[1].data.sound shouldBe "ht"
                        }
                    }
                }
            }
        }
    }

    "zoom with control pattern for end parameter" {
        val subject = s("bd sd ht lt").zoom(0.0, "<0.5 0.75>")

        assertSoftly {
            repeat(6) { cycle ->
                withClue("Cycle $cycle") {
                    val cycleDbl = cycle.toDouble()
                    val events = subject.queryArc(cycleDbl, cycleDbl + 1).filter { it.isOnset }

                    println("Cycle $cycle (end=${if (cycle % 2 == 0) "0.5" else "0.75"}):")
                    events.forEachIndexed { i, e ->
                        println("  Event $i: sound=${e.data.sound}, part=[${e.part.begin}, ${e.part.end}]")
                    }

                    when (cycle % 2) {
                        0 -> {
                            // zoom(0, 0.5) - takes first 50%
                            events.size shouldBe 2
                            events[0].data.sound shouldBe "bd"
                            events[1].data.sound shouldBe "sd"
                        }

                        1 -> {
                            // zoom(0, 0.75) - takes first 75%
                            events.size shouldBe 3
                            events[0].data.sound shouldBe "bd"
                            events[1].data.sound shouldBe "sd"
                            events[2].data.sound shouldBe "ht"
                        }
                    }
                }
            }
        }
    }

    "zoom with control patterns for both parameters" {
        val subject = s("bd sd ht lt").zoom("<0 0.25>", "<0.5 0.75>")

        assertSoftly {
            repeat(6) { cycle ->
                withClue("Cycle $cycle") {
                    val cycleDbl = cycle.toDouble()
                    val events = subject.queryArc(cycleDbl, cycleDbl + 1).filter { it.isOnset }

                    println("Cycle $cycle (${if (cycle % 2 == 0) "0-0.5" else "0.25-0.75"}):")
                    events.forEachIndexed { i, e ->
                        println("  Event $i: sound=${e.data.sound}, part=[${e.part.begin}, ${e.part.end}]")
                    }

                    when (cycle % 2) {
                        0 -> {
                            // zoom(0, 0.5) - takes first 50%
                            events.size shouldBe 2
                            events[0].data.sound shouldBe "bd"
                            events[1].data.sound shouldBe "sd"
                        }

                        1 -> {
                            // zoom(0.25, 0.75) - takes middle 50%
                            events.size shouldBe 2
                            events[0].data.sound shouldBe "sd"
                            events[1].data.sound shouldBe "ht"
                        }
                    }
                }
            }
        }
    }
})
