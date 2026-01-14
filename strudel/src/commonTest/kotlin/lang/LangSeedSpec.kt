package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class LangSeedSpec : StringSpec({
    "seed() sets random seed for pattern method" {
        // Query the same pattern twice with the same seed
        val p1 = rand.seed(42)
        val events1 = p1.queryArc(0.0, 1.0)

        val p2 = rand.seed(42)
        val events2 = p2.queryArc(0.0, 1.0)

        // With the same seed, random values should be identical
        events1.size shouldBe events2.size
        events1.zip(events2).forEach { (e1, e2) ->
            e1.data.value?.asDouble shouldBe e2.data.value?.asDouble
        }
    }

    "seed() with different seeds produces different results" {
        val p1 = rand.seed(42)
        val events1 = (0..100).flatMap {
            p1.queryArc(it.toDouble(), (it + 1).toDouble())
        }

        val p2 = rand.seed(123)
        val events2 = (0..100).flatMap {
            p2.queryArc(it.toDouble(), (it + 1).toDouble())
        }

        // With different seeds, at least some random values should differ
        val allSame = events1.zip(events2).all { (e1, e2) ->
            e1.data.value?.asDouble == e2.data.value?.asDouble
        }

        allSame shouldBe false
    }

    "seed() works as string extension" {
        val p1 = "0 1 2 3".degradeBy(0.5).seed(42)
        val events1 = p1.queryArc(0.0, 1.0)

        val p2 = "0 1 2 3".degradeBy(0.5).seed(42)
        val events2 = p2.queryArc(0.0, 1.0)

        // Same seed should produce identical degradation patterns
        events1.size shouldBe events2.size
        events1.zip(events2).forEach { (e1, e2) ->
            e1.data.value shouldBe e2.data.value
        }
    }

    "seed() with brand produces deterministic binary random" {
        val p1 = brand.seed(42)
        val events1 = (0..100).flatMap {
            p1.queryArc(it.toDouble(), (it + 1).toDouble())
        }

        val p2 = brand.seed(42)
        val events2 = (0..100).flatMap {
            p2.queryArc(it.toDouble(), (it + 1).toDouble())
        }

        // Same seed should produce identical binary random patterns
        events1.size shouldBe events2.size
        events1.zip(events2).forEach { (e1, e2) ->
            e1.data.value?.asDouble shouldBe e2.data.value?.asDouble
        }
    }

    "seed() propagates to nested patterns" {
        // Test that seed affects choice patterns
        val p1 = "bd|hh|sd".seed(42)
        val events1 = p1.queryArc(0.0, 4.0)

        val p2 = "bd|hh|sd".seed(42)
        val events2 = p2.queryArc(0.0, 4.0)

        // Same seed should produce identical choices
        events1.size shouldBe events2.size
        events1.zip(events2).forEach { (e1, e2) ->
            e1.data.sound shouldBe e2.data.sound
        }
    }

    "seed() with degradeBy produces reproducible pattern removal" {
        val p1 = "bd hh sd cp".degradeBy(0.3).seed(99)
        val events1 = p1.queryArc(0.0, 2.0)

        val p2 = "bd hh sd cp".degradeBy(0.3).seed(99)
        val events2 = p2.queryArc(0.0, 2.0)

        // Same seed and probability should produce identical event filtering
        events1.size shouldBe events2.size
        events1.zip(events2).forEach { (e1, e2) ->
            e1.data.sound shouldBe e2.data.sound
            e1.begin shouldBe e2.begin
        }
    }

    "seed() default value is 0" {
        // Calling seed() without arguments should use seed 0
        val p1 = rand.seed()
        val events1 = p1.queryArc(0.0, 1.0)

        val p2 = rand.seed(0)
        val events2 = p2.queryArc(0.0, 1.0)

        // Default seed (0) should match explicit seed(0)
        events1.size shouldBe events2.size
        events1.zip(events2).forEach { (e1, e2) ->
            e1.data.value?.asDouble shouldBe e2.data.value?.asDouble
        }
    }
})
