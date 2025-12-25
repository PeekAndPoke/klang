package io.peekandpoke.klang.dsp

import kotlin.math.exp
import kotlin.math.tan

object LowPassHighPassFilters {

    fun createLPF(cutoffHz: Double, q: Double?, sampleRate: Double): AudioFilter =
        when (q) {
            null -> OnePoleLPF(cutoffHz, sampleRate)
            else -> SvfLPF(cutoffHz, q, sampleRate)
        }

    fun createHPF(cutoffHz: Double, q: Double?, sampleRate: Double): AudioFilter =
        when (q) {
            null -> OnePoleHPF(cutoffHz, sampleRate)
            else -> SvfHPF(cutoffHz, q, sampleRate)
        }

    // --- Implementations ---

    class OnePoleLPF(cutoffHz: Double, sampleRate: Double) : AudioFilter {
        private var y = 0.0
        private val lowPass: Double

        init {
            val nyquist = 0.5 * sampleRate
            val cutoff = cutoffHz.coerceIn(5.0, nyquist - 1.0)
            lowPass = 1.0 - exp(-2.0 * Math.PI * cutoff / sampleRate)
        }

        override fun process(buffer: DoubleArray, offset: Int, length: Int) {
            val end = offset + length
            // Tight loop: very fast!
            for (i in offset until end) {
                val x = buffer[i]
                y += lowPass * (x - y)
                buffer[i] = y
            }
        }
    }

    class OnePoleHPF(cutoffHz: Double, sampleRate: Double) : AudioFilter {
        private var y = 0.0
        private var xPrev = 0.0
        private val a: Double

        init {
            val nyquist = 0.5 * sampleRate
            val cutoff = cutoffHz.coerceIn(5.0, nyquist - 1.0)
            a = exp(-2.0 * Math.PI * cutoff / sampleRate)
        }

        override fun process(buffer: DoubleArray, offset: Int, length: Int) {
            val end = offset + length
            for (i in offset until end) {
                val x = buffer[i]
                y = a * (y + x - xPrev)
                xPrev = x
                buffer[i] = y
            }
        }
    }

    // State Variable Filter (Shared logic)
    abstract class BaseSvf(cutoffHz: Double, q: Double, sampleRate: Double) : AudioFilter {
        protected var ic1eq = 0.0
        protected var ic2eq = 0.0
        protected val a1: Double
        protected val a2: Double
        protected val a3: Double
        protected val k: Double

        init {
            val nyquist = 0.5 * sampleRate
            val fc = cutoffHz.coerceIn(5.0, nyquist - 1.0)
            val Q = q.coerceIn(0.1, 50.0)

            val g = tan(Math.PI * fc / sampleRate)
            k = 1.0 / Q
            a1 = 1.0 / (1.0 + g * (g + k))
            a2 = g * a1
            a3 = g * a2
        }

        protected fun runSvf(buffer: DoubleArray, offset: Int, length: Int, outputSelector: (v0: Double, v1: Double, v2: Double) -> Double) {
            val end = offset + length
            for (i in offset until end) {
                val v0 = buffer[i]
                val v3 = v0 - ic2eq
                val v1 = a1 * ic1eq + a2 * v3
                val v2 = ic2eq + a2 * ic1eq + a3 * v3

                ic1eq = 2.0 * v1 - ic1eq
                ic2eq = 2.0 * v2 - ic2eq

                buffer[i] = outputSelector(v0, v1, v2)
            }
        }
    }

    class SvfLPF(cutoffHz: Double, q: Double, sampleRate: Double) : BaseSvf(cutoffHz, q, sampleRate) {
        override fun process(buffer: DoubleArray, offset: Int, length: Int) {
            // Inline loop for LPF specific output (v2)
            val end = offset + length
            for (i in offset until end) {
                val v0 = buffer[i]
                val v3 = v0 - ic2eq
                val v1 = a1 * ic1eq + a2 * v3
                val v2 = ic2eq + a2 * ic1eq + a3 * v3

                ic1eq = 2.0 * v1 - ic1eq
                ic2eq = 2.0 * v2 - ic2eq

                buffer[i] = v2 // Low pass output
            }
        }
    }

    class SvfHPF(cutoffHz: Double, q: Double, sampleRate: Double) : BaseSvf(cutoffHz, q, sampleRate) {
        override fun process(buffer: DoubleArray, offset: Int, length: Int) {
            val end = offset + length
            for (i in offset until end) {
                val v0 = buffer[i]
                val v3 = v0 - ic2eq
                val v1 = a1 * ic1eq + a2 * v3
                val v2 = ic2eq + a2 * ic1eq + a3 * v3

                ic1eq = 2.0 * v1 - ic1eq
                ic2eq = 2.0 * v2 - ic2eq

                // High pass output: v0 - k*v1 - v2
                buffer[i] = v0 - k * v1 - v2
            }
        }
    }
}
