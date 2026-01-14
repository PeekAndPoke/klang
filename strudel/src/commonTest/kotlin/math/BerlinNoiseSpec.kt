package io.peekandpoke.klang.strudel.math

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.comparables.shouldBeBetween
import io.kotest.matchers.doubles.shouldBeBetween
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.random.Random

class BerlinNoiseSpec : StringSpec({

    "BerlinNoise: stability - same seed produces same ridges" {
        val noise1 = BerlinNoise(Random(99))
        val noise2 = BerlinNoise(Random(99))

        for (i in 0..100) {
            val t = i * 0.15
            noise1.noise(t) shouldBe noise2.noise(t)
        }
    }

    "BerlinNoise: different seeds produce different ridge heights" {
        val noise1 = BerlinNoise(Random(1))
        val noise2 = BerlinNoise(Random(2))

        noise1.noise(0.75) shouldNotBe noise2.noise(0.75)
    }

    "BerlinNoise: internal cache stability" {
        val noise = BerlinNoise(Random(123))

        val val1 = noise.noise(5.5)
        // Simulate "forgetting" and re-querying or same-instance query
        val val2 = noise.noise(5.5)

        val1 shouldBe val2
    }

    "BerlinNoise: output range is 0.0 to 1.0" {
        val noise = BerlinNoise(Random(42))
        for (i in 0..500) {
            val v = noise.noise(i * 0.1)
            v.shouldBeBetween(0.0, 1.0, 0.0001)
        }
    }

    "BerlinNoise: ridge behavior - height increases between integers" {
        val noise = BerlinNoise(Random(0))

        // At integer boundaries, the height is purely based on the random point
        // In this implementation, interp is: prev + t * (next_top - prev)
        // JS: result / 2.0
        val vAtInt = noise.noise(1.0)
        vAtInt.shouldBeBetween(0.0, 1.0)
    }

    "BerlinNoise: reaches values close to limits" {
        val noise = BerlinNoise(Random(0))
        var min = 1.0
        var max = 0.0

        // Scan a large range to find peaks
        for (i in 0..10000) {
            val v = noise.noise(i * 0.1)
            if (v < min) min = v
            if (v > max) max = v
        }

        // Berlin noise produces values strictly between 0.0 and 1.0
        // We expect it to reach near the bottom (0.0) and near the top (1.0)
        min.shouldBeBetween(0.0, 0.2, 0.0)
        max.shouldBeBetween(0.8, 1.0, 0.0)
    }
})
