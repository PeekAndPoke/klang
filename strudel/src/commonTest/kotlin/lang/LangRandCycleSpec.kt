package io.peekandpoke.klang.strudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class LangRandCycleSpec : StringSpec({

    "randCycle produces values between 0 and 1" {
        val p = randCycle.seed(42)
        val value1 = p.queryArc(0.0, 0.01).firstOrNull()?.data?.value?.asDouble ?: 0.0
        val value2 = p.queryArc(1.0, 1.01).firstOrNull()?.data?.value?.asDouble ?: 0.0

        assertSoftly {
            withClue("value at cycle 0") {
                value1 shouldBe (value1 plusOrMinus 1.0) // Between 0 and 1
            }
            withClue("value at cycle 1") {
                value2 shouldBe (value2 plusOrMinus 1.0) // Between 0 and 1
            }
        }
    }

    "randCycle is constant within a cycle" {
        val p = randCycle.seed(42)

        // Query multiple times within cycle 0
        val value1 = p.queryArc(0.0, 0.01).firstOrNull()?.data?.value?.asDouble
        val value2 = p.queryArc(0.25, 0.26).firstOrNull()?.data?.value?.asDouble
        val value3 = p.queryArc(0.5, 0.51).firstOrNull()?.data?.value?.asDouble
        val value4 = p.queryArc(0.99, 1.0).firstOrNull()?.data?.value?.asDouble

        assertSoftly {
            withClue("All values within cycle 0 should be equal") {
                value2 shouldBe value1
                value3 shouldBe value1
                value4 shouldBe value1
            }
        }
    }

    "randCycle changes between cycles" {
        val p = randCycle.seed(42)

        // Query at the start of different cycles
        val valueCycle0 = p.queryArc(0.0, 0.01).firstOrNull()?.data?.value?.asDouble
        val valueCycle1 = p.queryArc(1.0, 1.01).firstOrNull()?.data?.value?.asDouble
        val valueCycle2 = p.queryArc(2.0, 2.01).firstOrNull()?.data?.value?.asDouble
        val valueCycle3 = p.queryArc(3.0, 3.01).firstOrNull()?.data?.value?.asDouble

        assertSoftly {
            withClue("Values should differ between cycles") {
                valueCycle1 shouldNotBe valueCycle0
                valueCycle2 shouldNotBe valueCycle0
                valueCycle2 shouldNotBe valueCycle1
                valueCycle3 shouldNotBe valueCycle0
                valueCycle3 shouldNotBe valueCycle1
                valueCycle3 shouldNotBe valueCycle2
            }
        }
    }

    "randCycle is reproducible with same seed" {
        val p1 = randCycle.seed(42)
        val p2 = randCycle.seed(42)

        val value1Cycle0 = p1.queryArc(0.0, 0.01).firstOrNull()?.data?.value?.asDouble
        val value2Cycle0 = p2.queryArc(0.0, 0.01).firstOrNull()?.data?.value?.asDouble

        val value1Cycle5 = p1.queryArc(5.0, 5.01).firstOrNull()?.data?.value?.asDouble
        val value2Cycle5 = p2.queryArc(5.0, 5.01).firstOrNull()?.data?.value?.asDouble

        assertSoftly {
            withClue("Cycle 0 should match") {
                value2Cycle0 shouldBe value1Cycle0
            }
            withClue("Cycle 5 should match") {
                value2Cycle5 shouldBe value1Cycle5
            }
        }
    }

    "randCycle with degradeByWith removes events per cycle" {
        // When using randCycle, all events in a cycle are either kept or removed together
        val p = note("a b c d").degradeByWith(randCycle, 0.5).seed(42)

        // Count events across multiple cycles
        val cycleEventCounts = (0..20).map { cycle ->
            p.queryArc(cycle.toDouble(), cycle + 1.0).size
        }

        assertSoftly {
            withClue("Each cycle should have either 0 or 4 events (all or nothing)") {
                cycleEventCounts.all { it == 0 || it == 4 } shouldBe true
            }
            withClue("Some cycles should have events") {
                cycleEventCounts.any { it == 4 } shouldBe true
            }
            withClue("Some cycles should have no events") {
                cycleEventCounts.any { it == 0 } shouldBe true
            }
        }
    }

    "randCycle vs rand behavior difference" {
        // rand: each event gets independent random decision
        // randCycle: all events in a cycle get the same random decision
        val pRand = note("a b c d").degradeByWith(rand, 0.5).seed(42)
        val pRandCycle = note("a b c d").degradeByWith(randCycle, 0.5).seed(42)

        // For randCycle, each cycle should have 0 or 4 events
        val randCycleCounts = (0..20).map { cycle ->
            pRandCycle.queryArc(cycle.toDouble(), cycle + 1.0).size
        }

        // For rand, each cycle can have 0, 1, 2, 3, or 4 events (variable)
        val randCounts = (0..20).map { cycle ->
            pRand.queryArc(cycle.toDouble(), cycle + 1.0).size
        }

        assertSoftly {
            withClue("randCycle should only have 0 or 4 events per cycle") {
                randCycleCounts.all { it == 0 || it == 4 } shouldBe true
            }
            withClue("rand should have variable event counts (not all 0 or 4)") {
                val hasVariableCounts = randCounts.any { it != 0 && it != 4 }
                hasVariableCounts shouldBe true
            }
        }
    }

    "randCycle works in compiled code" {
        val p = io.peekandpoke.klang.strudel.StrudelPattern.compile(
            """note("a").degradeByWith(randCycle, 0.5).seed(42)"""
        )

        val events = p?.queryArc(0.0, 10.0) ?: emptyList()

        events.size shouldBeInRange 3..7
    }
})
