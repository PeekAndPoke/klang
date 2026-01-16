package io.peekandpoke.klang.strudel.lang

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.shouldBe

class LangSomeCyclesBySpec : StringSpec({

    "someCyclesBy(0.5, fn) behavior" {
        // someCyclesBy decides PER CYCLE.
        // So for "a*4", all 4 notes in a cycle are EITHER 'a' OR 'A'.
        // They should never be mixed within a single cycle.

        val p = note("a*4").someCyclesBy(0.5) { it.scale("C4") }.seed(100)

        var mixedCycles = 0
        var allModifiedCycles = 0
        var allUnmodifiedCycles = 0
        val totalCycles = 100

        for (i in 0 until totalCycles) {
            val events = p.queryArc(i.toDouble(), i + 1.0)
            val notes = events.map { it.data.scale }

            if (notes.all { it == null }) {
                allModifiedCycles++
            } else if (notes.all { it == "C4" }) {
                allUnmodifiedCycles++
            } else {
                mixedCycles++
            }
        }

        withClue("Mixed cycles (should be 0)") {
            mixedCycles shouldBe 0
        }
        withClue("Modified vs Unmodified split (approx 50/50)") {
            allModifiedCycles shouldBeInRange 30..70
            allUnmodifiedCycles shouldBeInRange 30..70
        }
    }

    "someCyclesBy with control pattern string" {
        // "0.1 0.9" -> alternates prob per cycle? No, per step.
        // But someCyclesBy samples prob at EVENT start, but uses CYCLE SEED for randomness.
        // If prob pattern is "0.1 0.9", it changes over the cycle.
        // If "a*4" (events at 0, 0.25, 0.5, 0.75).
        // Event at 0: p=0.1. Seed from cycle start. r is fixed for cycle.
        // Event at 0.5: p=0.9. Seed from cycle start. r is SAME.
        // So it is possible to have mixed results if P changes but R is constant!
        // Because "r < p" might change if p changes.

        // Let's test with steady probability first to ensure cycle locking works.
        // "0.5" constant.

        val p = note("a*4").someCyclesBy("0.5") { it.note() }.seed(200)
        var mixed = 0
        for (i in 0 until 100) {
            val events = p.queryArc(i.toDouble(), i + 1.0)
            val notes = events.map { it.data.note }
            if (notes.any { it == "a" } && notes.any { it == "A" }) mixed++
        }
        mixed shouldBe 0
    }

    "someCycles() alias" {
        val p = note("a*2").someCycles { it.note() }.seed(300)
        var mixed = 0
        for (i in 0 until 100) {
            val events = p.queryArc(i.toDouble(), i + 1.0)
            val notes = events.map { it.data.note }
            if (notes.any { it == "a" } && notes.any { it == "A" }) mixed++
        }
        mixed shouldBe 0
    }
})
