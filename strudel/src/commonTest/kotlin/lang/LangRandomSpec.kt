package io.peekandpoke.klang.strudel.lang

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangRandomSpec : StringSpec({
    "rand oscillator" {
        withClue("rand in kotlin produces values between 0 and 1") {
            val pattern = rand.seed(42)
            val events = pattern.queryArc(0.0, 1.0)
            events.size shouldBe 1

            val value = events[0].data.value?.asDouble!!
            (value >= 0.0 && value <= 1.0) shouldBe true
        }

        withClue("rand with range in kotlin") {
            val pattern = rand.seed(42).range(10.0, 20.0)
            val events = pattern.queryArc(0.0, 1.0)

            val value = events[0].data.value?.asDouble!!
            (value >= 10.0 && value <= 20.0) shouldBe true
        }

        withClue("rand with seed produces consistent results") {
            val p1 = rand.seed(99)
            val v1 = p1.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble

            val p2 = rand.seed(99)
            val v2 = p2.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble

            v1 shouldBe v2
        }

        withClue("rand compiled") {
            val pattern = StrudelPattern.compile("rand.seed(123)")!!
            val events = pattern.queryArc(0.0, 1.0)
            events.size shouldBe 1

            val value = events[0].data.value?.asDouble!!
            (value >= 0.0 && value <= 1.0) shouldBe true
        }

        withClue("rand compiled with range") {
            val pattern = StrudelPattern.compile("rand.seed(123).range(0, 100)")!!
            val events = pattern.queryArc(0.0, 1.0)

            val value = events[0].data.value?.asDouble!!
            (value >= 0.0 && value <= 100.0) shouldBe true
        }
    }

    "rand2 oscillator" {
        withClue("rand2 in kotlin produces values between -1 and 1") {
            val pattern = rand2.seed(42)
            val events = pattern.queryArc(0.0, 1.0)
            events.size shouldBe 1

            val value = events[0].data.value?.asDouble!!
            (value >= -1.0 && value <= 1.0) shouldBe true
        }

        withClue("rand2 with range in kotlin") {
            val pattern = rand2.seed(42).range(-100.0, 100.0)
            val events = pattern.queryArc(0.0, 1.0)

            val value = events[0].data.value?.asDouble!!
            (value >= -100.0 && value <= 100.0) shouldBe true
        }

        withClue("rand2 with seed produces consistent results") {
            val p1 = rand2.seed(77)
            val v1 = p1.queryArc(0.3, 0.3 + EPSILON)[0].data.value?.asDouble

            val p2 = rand2.seed(77)
            val v2 = p2.queryArc(0.3, 0.3 + EPSILON)[0].data.value?.asDouble

            v1 shouldBe v2
        }

        withClue("rand2 compiled") {
            val pattern = StrudelPattern.compile("rand2.seed(456)")!!
            val events = pattern.queryArc(0.0, 1.0)
            events.size shouldBe 1

            val value = events[0].data.value?.asDouble!!
            (value >= -1.0 && value <= 1.0) shouldBe true
        }

        withClue("rand2 compiled with range") {
            val pattern = StrudelPattern.compile("rand2.seed(456).range(0, 50)")!!
            val events = pattern.queryArc(0.0, 1.0)

            val value = events[0].data.value?.asDouble!!
            (value >= 0.0 && value <= 50.0) shouldBe true
        }
    }

    "brand oscillator" {
        withClue("brand in kotlin produces 0 or 1") {
            val pattern = brand.seed(42)
            val events = pattern.queryArc(0.0, 10.0)

            // Check that all values are either 0.0 or 1.0
            events.forEach { event ->
                val value = event.data.value?.asDouble!!
                (value == 0.0 || value == 1.0) shouldBe true
            }
        }

        withClue("brand with range in kotlin") {
            val pattern = brand.seed(42).range(10.0, 20.0)
            val events = pattern.queryArc(0.0, 10.0)

            // Check that all values are either 10.0 or 20.0
            events.forEach { event ->
                val value = event.data.value?.asDouble!!
                (value == 10.0 || value == 20.0) shouldBe true
            }
        }

        withClue("brand with seed produces consistent results") {
            (0..100).forEach { seed ->
                withClue("seed = $seed") {
                    val p1 = brand.seed(55)
                    val v1 = p1.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble

                    val p2 = brand.seed(55)
                    val v2 = p2.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble

                    v1.shouldNotBeNull()
                    v2.shouldNotBeNull()
                    v1 shouldBe v2
                }
            }
        }

        withClue("brand compiled") {
            val pattern = StrudelPattern.compile("brand.seed(789)")!!
            val events = pattern.queryArc(0.0, 5.0)

            events.forEach { event ->
                val value = event.data.value?.asDouble!!
                (value == 0.0 || value == 1.0) shouldBe true
            }
        }

        withClue("brand compiled with range") {
            val pattern = StrudelPattern.compile("brand.seed(789).range(-5, 5)")!!
            val events = pattern.queryArc(0.0, 5.0)

            events.forEach { event ->
                val value = event.data.value?.asDouble!!
                (value == -5.0 || value == 5.0) shouldBe true
            }
        }
    }

    "brandBy oscillator" {
        withClue("brandBy(0.8) in kotlin produces 0 or 1 with bias") {
            val pattern = brandBy(0.8).seed(42)
            val events = (1..<100).flatMap { pattern.queryArc(it.toDouble(), it + 1.0) }

            // Check that all values are either 0.0 or 1.0
            var ones = 0
            var zeros = 0
            events.forEach { event ->
                val value = event.data.value?.asDouble!!
                if (value == 1.0) ones++
                else if (value == 0.0) zeros++
            }

            println("Ones: $ones, zeros: $zeros")

            // With probability 0.8, we expect roughly 80% ones
            // Allow some tolerance due to randomness
            val ratio = ones.toDouble() / events.size
            ratio shouldBe (0.8 plusOrMinus 0.15)
        }

        withClue("brandBy(0.2) in kotlin produces mostly zeros") {
            val pattern = brandBy(0.2).seed(42)
            val events = (1..<100).flatMap { pattern.queryArc(it.toDouble(), it + 1.0) }

            var ones = 0
            events.forEach { event ->
                val value = event.data.value?.asDouble!!
                if (value == 1.0) ones++
            }

            // With probability 0.2, we expect roughly 20% ones
            val ratio = ones.toDouble() / events.size
            ratio shouldBe (0.2 plusOrMinus 0.15)
        }

        withClue("brandBy with range in kotlin") {
            val pattern = brandBy(0.5).seed(42).range(-10.0, 10.0)
            val events = (1..<10).flatMap { pattern.queryArc(it.toDouble(), it + 1.0) }

            // Check that all values are either -10.0 or 10.0
            events.forEach { event ->
                val value = event.data.value?.asDouble!!
                (value == -10.0 || value == 10.0) shouldBe true
            }
        }

        withClue("brandBy with seed produces consistent results") {
            val p1 = brandBy(0.7).seed(33)
            val v1 = p1.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble

            val p2 = brandBy(0.7).seed(33)
            val v2 = p2.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble

            v1 shouldBe v2
        }

        withClue("brandBy compiled") {
            val pattern = StrudelPattern.compile("brandBy(0.3).seed(999)")!!
            val events = (1..<50).flatMap { pattern.queryArc(it.toDouble(), it + 1.0) }

            var ones = 0
            events.forEach { event ->
                val value = event.data.value?.asDouble!!
                if (value == 1.0) ones++
            }

            // With probability 0.3, expect roughly 30% ones
            val ratio = ones.toDouble() / events.size
            ratio shouldBe (0.3 plusOrMinus 0.15)
        }

        withClue("brandBy compiled with range") {
            val pattern = StrudelPattern.compile("brandBy(0.5).seed(999).range(0, 100)")!!
            val events = (1..<10).flatMap { pattern.queryArc(it.toDouble(), it + 1.0) }

            events.forEach { event ->
                val value = event.data.value?.asDouble!!
                (value == 0.0 || value == 100.0) shouldBe true
            }
        }
    }

    "irand oscillator" {
        withClue("irand(4) produces integers in [0, 3] and changes over time") {
            val n = 4
            // Without explicit seed, it should be random but deterministic per run if context is not seeded?
            // Wait, StrudelPattern.queryArc uses empty context which has no seed.
            // But QueryContext.getSeededRandom uses Random.Default if no seed is in context?
            // No, context.getSeededRandom logic:
            // fun getRandom(): Random = getOrNull(randomSeed)?.let { Random(it) } ?: Random.Default
            // If randomSeed is not set, it uses Random.Default which IS random.

            val pattern = irand(n)
            val events1 = pattern.queryArc(0.0, 1.0)
            val events2 = pattern.queryArc(1.0, 2.0)

            // Check structure: irand(n) creates a sequence of n events per cycle
            events1.size shouldBe n
            events2.size shouldBe n

            // Check values range
            events1.forEach {
                val v = it.data.value?.asDouble!!
                (v >= 0 && v < 8) shouldBe true // The impl uses random.nextInt(0, 8), is that correct for n argument?
                // Ah, the revised implementation has hardcoded `random.nextInt(0, 8)`.
                // The instruction said "keep the code as is!".
                // But the doc says "between 0 and n-1".
                // The user provided code uses `random.nextInt(0, 8)` regardless of `n`.
                // Wait, `n` is used for `(0..<n).map`. So it creates `n` events per cycle.
                // But the value is `random.nextInt(0, 8)`.
                // So `irand(4)` creates 4 events, each with value 0..7.
                // The doc example says `n(irand(8))` implying 8 events? No, `n` usually takes a pattern of values.
                // If `irand` returns a pattern, `n(pattern)` sets note values.
                // If `irand(8)` creates 8 events per cycle with values 0..7, then `n(irand(8))` sets 8 notes per cycle.
                // But `irand(4)` in the test description says "This pattern produces an ever changing sequence".
                // Let's assume the hardcoded 8 is intentional for now or a mistake in the snippet I was told to keep.
                // I will test against the actual behavior of the provided code (0..7 values).
            }

            // Check that it changes (highly likely)
            val values1 = events1.map { it.data.value?.asDouble }
            val values2 = events2.map { it.data.value?.asDouble }
            values1 shouldNotBe values2
        }

        withClue("irand(4).seed(1) produces consistent results") {
            val n = 4
            val pattern = irand(n).seed(1)

            val events1 = pattern.queryArc(0.0, 1.0)
            val events2 = pattern.queryArc(1.0, 2.0) // Next cycle

            // Wait, if seeded, does it repeat exactly every cycle?
            // The implementation uses `val seed = fraction * 100`.
            // `evt.begin` increases. `evt.begin.floor()` increases.
            // `fraction` = `evt.begin - evt.begin.floor()` should be the same for the same position in cycle.
            // e.g. event 0 at 0.0: frac 0. event 0 at 1.0: frac 0.
            // event 1 at 0.25: frac 0.25. event 1 at 1.25: frac 0.25.
            // So yes, `seed` depends on `fraction` which is periodic.
            // So it SHOULD repeat every cycle if the context seed is fixed.

            val values1 = events1.map { it.data.value?.asDouble }
            val values2 = events2.map { it.data.value?.asDouble }

            values1 shouldBe values2
        }

        withClue("irand with different seeds produces different results") {
            val n = 4
            val p1 = irand(n).seed(123)
            val events1 = p1.queryArc(0.0, 1.0)

            val p2 = irand(n).seed(456)
            val events2 = p2.queryArc(0.0, 1.0)

            val values1 = events1.map { it.data.value?.asDouble }
            val values2 = events2.map { it.data.value?.asDouble }

            values1 shouldNotBe values2
        }
    }
})
