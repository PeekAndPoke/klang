package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlin.math.abs

/**
 * Guards for the super-saw analog flyback shape ([SawVoiceState.sampleAt]) and its tuning anchor
 * ([Ignitors.getUnisonDetune]). See the SuperSaw rewrite plan.
 */
class AnalogSawSpec : StringSpec({

    // Shape sample at phase [p] for a voice configured with the given flyback fraction.
    fun analogSaw(p: Double, rf: Double): Double =
        SawVoiceState().apply { setShape(rf) }.sampleAt(p)

    "analogSaw - pure ramp (rf=0) spans -1..+1" {
        analogSaw(0.0, 0.0) shouldBe (-1.0 plusOrMinus 1e-9)
        analogSaw(0.5, 0.0) shouldBe (0.0 plusOrMinus 1e-9)
        analogSaw(0.999999, 0.0) shouldBe (1.0 plusOrMinus 1e-3)
    }

    "analogSaw - is zero-mean across flyback fractions" {
        val n = 100_000
        for (rf in listOf(0.0, 0.1, 0.2, 0.4, 0.49)) {
            var sum = 0.0
            for (i in 0 until n) sum += analogSaw(i.toDouble() / n, rf)
            (sum / n) shouldBe (0.0 plusOrMinus 1e-3)
        }
    }

    "analogSaw - rises to the peak then flies back down" {
        val rf = 0.2
        val n = 4000
        val vals = DoubleArray(n) { analogSaw(it.toDouble() / n, rf) }
        val peakIdx = vals.indices.maxByOrNull { vals[it] }!!
        // monotone non-decreasing through the rise up to the peak
        for (i in 1..peakIdx) (vals[i] >= vals[i - 1] - 1e-9) shouldBe true
        // monotone non-increasing through the flyback to the cycle end
        val flybackStart = ((1.0 - rf) * n).toInt() + 1
        for (i in flybackStart until n) (vals[i] <= vals[i - 1] + 1e-9) shouldBe true
    }

    "analogSaw - higher pitch (larger flyback fraction) softens: smaller max slope" {
        val n = 100_000
        fun maxAbsSlope(rf: Double): Double {
            var prev = analogSaw(0.0, rf)
            var m = 0.0
            for (i in 1 until n) {
                val cur = analogSaw(i.toDouble() / n, rf)
                val s = abs(cur - prev); if (s > m) m = s
                prev = cur
            }
            return m
        }
        // a wide flyback (high note → near-triangle) has a gentler reset than a narrow one (low note)
        maxAbsSlope(0.45) shouldBeLessThan maxAbsSlope(0.02)
    }

    "getUnisonDetune - symmetric spread sums to zero (in-tune centroid)" {
        for (u in listOf(2, 3, 5, 7, 8, 12)) {
            var sum = 0.0
            for (n in 0 until u) sum += Ignitors.getUnisonDetune(u, 0.2, n)
            sum shouldBe (0.0 plusOrMinus 1e-9)
        }
    }
})
