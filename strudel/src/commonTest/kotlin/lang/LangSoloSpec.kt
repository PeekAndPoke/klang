package io.peekandpoke.klang.strudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEqualIgnoringCase
import io.peekandpoke.klang.strudel.lang.addons.solo
import io.peekandpoke.klang.strudel.lang.addons.strudelLangStructuralAddonsInit

class LangSoloSpec : StringSpec({

    // Force initialization of addons
    strudelLangStructuralAddonsInit = true

    "s(\"bd\").solo() sets solo flag to true" {
        val subject = s("bd").solo()

        assertSoftly {
            repeat(12) { cycle ->
                withClue("Cycle $cycle") {
                    val cycleDbl = cycle.toDouble()
                    val events = subject.queryArc(cycleDbl, cycleDbl + 1)
                        .filter { it.isOnset }

                    events.size shouldBe 1
                    events[0].data.sound shouldBeEqualIgnoringCase "bd"
                    events[0].data.solo shouldBe true
                }
            }
        }
    }

    "s(\"bd\").solo(1) sets solo flag to true" {
        val subject = s("bd").solo(1)

        assertSoftly {
            repeat(12) { cycle ->
                withClue("Cycle $cycle") {
                    val cycleDbl = cycle.toDouble()
                    val events = subject.queryArc(cycleDbl, cycleDbl + 1)
                        .filter { it.isOnset }

                    events.size shouldBe 1
                    events[0].data.solo shouldBe true
                }
            }
        }
    }

    "s(\"bd\").solo(0) sets solo flag to false" {
        val subject = s("bd").solo(0)

        assertSoftly {
            repeat(12) { cycle ->
                withClue("Cycle $cycle") {
                    val cycleDbl = cycle.toDouble()
                    val events = subject.queryArc(cycleDbl, cycleDbl + 1)
                        .filter { it.isOnset }

                    events.size shouldBe 1
                    events[0].data.solo shouldBe false
                }
            }
        }
    }

    "s(\"bd sd\").solo(\"<1 0>\") alternates solo flag" {
        val subject = s("bd sd").solo("<1 0>")

        assertSoftly {
            repeat(12) { cycle ->
                withClue("Cycle $cycle") {
                    val cycleDbl = cycle.toDouble()
                    val events = subject.queryArc(cycleDbl, cycleDbl + 1)
                        .filter { it.isOnset }

                    events.size shouldBe 2

                    // Pattern alternates: cycle 0 → solo=true, cycle 1 → solo=false
                    val expectedSolo = (cycle % 2) == 0

                    events[0].data.sound shouldBeEqualIgnoringCase "bd"
                    events[0].data.solo shouldBe expectedSolo

                    events[1].data.sound shouldBeEqualIgnoringCase "sd"
                    events[1].data.solo shouldBe expectedSolo
                }
            }
        }
    }

    "s(\"bd sd\").solo(\"<1 0 1 0>\") alternates per half cycle" {
        val subject = s("bd sd").solo("1 0")

        assertSoftly {
            repeat(12) { cycle ->
                withClue("Cycle $cycle") {
                    val cycleDbl = cycle.toDouble()
                    val events = subject.queryArc(cycleDbl, cycleDbl + 1)
                        .filter { it.isOnset }

                    events.size shouldBe 2

                    events[0].data.sound shouldBeEqualIgnoringCase "bd"
                    events[0].data.solo shouldBe true

                    events[1].data.sound shouldBeEqualIgnoringCase "sd"
                    events[1].data.solo shouldBe false
                }
            }
        }
    }

    "s(\"bd\").solo(true) sets solo flag using boolean" {
        val subject = s("bd").solo(true)

        assertSoftly {
            repeat(12) { cycle ->
                withClue("Cycle $cycle") {
                    val cycleDbl = cycle.toDouble()
                    val events = subject.queryArc(cycleDbl, cycleDbl + 1)
                        .filter { it.isOnset }

                    events.size shouldBe 1
                    events[0].data.solo shouldBe true
                }
            }
        }
    }

    "s(\"bd\").solo(false) sets solo flag to false using boolean" {
        val subject = s("bd").solo(false)

        assertSoftly {
            repeat(12) { cycle ->
                withClue("Cycle $cycle") {
                    val cycleDbl = cycle.toDouble()
                    val events = subject.queryArc(cycleDbl, cycleDbl + 1)
                        .filter { it.isOnset }

                    events.size shouldBe 1
                    events[0].data.solo shouldBe false
                }
            }
        }
    }

    "string pattern \"bd\".solo() works" {
        val subject = "bd".solo()

        assertSoftly {
            repeat(12) { cycle ->
                withClue("Cycle $cycle") {
                    val cycleDbl = cycle.toDouble()
                    val events = subject.queryArc(cycleDbl, cycleDbl + 1)
                        .filter { it.isOnset }

                    events.size shouldBe 1
                    events[0].data.value?.asString shouldBe "bd"
                    events[0].data.solo shouldBe true
                }
            }
        }
    }

    "s(\"bd\").solo().fast(2) preserves solo flag after transformation" {
        val subject = s("bd").solo().fast(2)

        assertSoftly {
            repeat(12) { cycle ->
                withClue("Cycle $cycle") {
                    val cycleDbl = cycle.toDouble()
                    val events = subject.queryArc(cycleDbl, cycleDbl + 1)
                        .filter { it.isOnset }

                    events.size shouldBe 2
                    events[0].data.solo shouldBe true
                    events[1].data.solo shouldBe true
                }
            }
        }
    }

    "s(\"bd\").gain(0.5).solo() chains with other modifiers" {
        val subject = s("bd").gain(0.5).solo()

        assertSoftly {
            repeat(12) { cycle ->
                withClue("Cycle $cycle") {
                    val cycleDbl = cycle.toDouble()
                    val events = subject.queryArc(cycleDbl, cycleDbl + 1)
                        .filter { it.isOnset }

                    events.size shouldBe 1
                    events[0].data.gain shouldBe 0.5
                    events[0].data.solo shouldBe true
                }
            }
        }
    }
})
