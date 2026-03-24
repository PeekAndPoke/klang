package io.peekandpoke.klang.audio_be.filters

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.tan

private const val DENORMAL_THRESHOLD = 1e-15

@Suppress("NOTHING_TO_INLINE")
private inline fun flushDenormal(v: Double): Double = if (abs(v) < DENORMAL_THRESHOLD) 0.0 else v

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

    fun createBPF(cutoffHz: Double, q: Double?, sampleRate: Double): AudioFilter =
        SvfBPF(cutoffHz, q ?: 1.0, sampleRate)

    fun createNotch(cutoffHz: Double, q: Double?, sampleRate: Double): AudioFilter =
        SvfNotch(cutoffHz, q ?: 1.0, sampleRate)

    // --- Implementations ---

    class OnePoleLPF(cutoffHz: Double, private val sampleRate: Double) : AudioFilter, AudioFilter.Tunable {
        private var y = 0.0
        private var lowPass: Double = 0.0

        init {
            setCutoff(cutoffHz)
        }

        override fun setCutoff(cutoffHz: Double) {
            val nyquist = 0.5 * sampleRate
            val cutoff = cutoffHz.coerceIn(5.0, nyquist - 1.0)
            lowPass = 1.0 - exp(-2.0 * PI * cutoff / sampleRate)
        }

        override fun process(buffer: FloatArray, offset: Int, length: Int) {
            val end = offset + length
            for (i in offset until end) {
                val x = buffer[i].toDouble()
                y += lowPass * (x - y)
                y = flushDenormal(y)
                buffer[i] = y.toFloat()
            }
        }
    }

    class OnePoleHPF(cutoffHz: Double, private val sampleRate: Double) : AudioFilter, AudioFilter.Tunable {
        private var y = 0.0
        private var xPrev = 0.0
        private var a: Double = 0.0

        init {
            setCutoff(cutoffHz)
        }

        override fun setCutoff(cutoffHz: Double) {
            val nyquist = 0.5 * sampleRate
            val cutoff = cutoffHz.coerceIn(5.0, nyquist - 1.0)
            a = exp(-2.0 * PI * cutoff / sampleRate)
        }

        override fun process(buffer: FloatArray, offset: Int, length: Int) {
            val end = offset + length
            for (i in offset until end) {
                val x = buffer[i].toDouble()
                y = a * (y + x - xPrev)
                y = flushDenormal(y)
                xPrev = x
                buffer[i] = y.toFloat()
            }
        }
    }

    // State Variable Filter (Shared logic)
    abstract class BaseSvf(cutoffHz: Double, q: Double, private val sampleRate: Double) : AudioFilter,
        AudioFilter.Tunable {
        protected var ic1eq = 0.0
        protected var ic2eq = 0.0
        protected var a1: Double = 0.0
        protected var a2: Double = 0.0
        protected var a3: Double = 0.0
        protected var k: Double = 0.0
        private val q: Double = q

        init {
            setCutoff(cutoffHz)
        }

        override fun setCutoff(cutoffHz: Double) {
            val nyquist = 0.5 * sampleRate
            val fc = cutoffHz.coerceIn(5.0, nyquist - 1.0)
            val Q = q.coerceIn(0.1, 50.0)

            val g = tan(PI * fc / sampleRate)
            k = 1.0 / Q
            a1 = 1.0 / (1.0 + g * (g + k))
            a2 = g * a1
            a3 = g * a2
        }
    }

    class SvfLPF(cutoffHz: Double, q: Double, sampleRate: Double) : BaseSvf(cutoffHz, q, sampleRate) {
        override fun process(buffer: FloatArray, offset: Int, length: Int) {
            val end = offset + length
            for (i in offset until end) {
                val v0 = buffer[i].toDouble()
                val v3 = v0 - ic2eq
                val v1 = a1 * ic1eq + a2 * v3
                val v2 = ic2eq + a2 * ic1eq + a3 * v3
                ic1eq = flushDenormal(2.0 * v1 - ic1eq)
                ic2eq = flushDenormal(2.0 * v2 - ic2eq)
                buffer[i] = v2.toFloat()
            }
        }
    }

    class SvfHPF(cutoffHz: Double, q: Double, sampleRate: Double) : BaseSvf(cutoffHz, q, sampleRate) {
        override fun process(buffer: FloatArray, offset: Int, length: Int) {
            val end = offset + length
            for (i in offset until end) {
                val v0 = buffer[i].toDouble()
                val v3 = v0 - ic2eq
                val v1 = a1 * ic1eq + a2 * v3
                val v2 = ic2eq + a2 * ic1eq + a3 * v3
                ic1eq = flushDenormal(2.0 * v1 - ic1eq)
                ic2eq = flushDenormal(2.0 * v2 - ic2eq)
                buffer[i] = (v0 - k * v1 - v2).toFloat()
            }
        }
    }

    class SvfBPF(cutoffHz: Double, q: Double, sampleRate: Double) : BaseSvf(cutoffHz, q, sampleRate) {
        override fun process(buffer: FloatArray, offset: Int, length: Int) {
            val end = offset + length
            for (i in offset until end) {
                val v0 = buffer[i].toDouble()
                val v3 = v0 - ic2eq
                val v1 = a1 * ic1eq + a2 * v3
                val v2 = ic2eq + a2 * ic1eq + a3 * v3
                ic1eq = flushDenormal(2.0 * v1 - ic1eq)
                ic2eq = flushDenormal(2.0 * v2 - ic2eq)
                buffer[i] = v1.toFloat()
            }
        }
    }

    class SvfNotch(cutoffHz: Double, q: Double, sampleRate: Double) : BaseSvf(cutoffHz, q, sampleRate) {
        override fun process(buffer: FloatArray, offset: Int, length: Int) {
            val end = offset + length
            for (i in offset until end) {
                val v0 = buffer[i].toDouble()
                val v3 = v0 - ic2eq
                val v1 = a1 * ic1eq + a2 * v3
                val v2 = ic2eq + a2 * ic1eq + a3 * v3
                ic1eq = flushDenormal(2.0 * v1 - ic1eq)
                ic2eq = flushDenormal(2.0 * v2 - ic2eq)
                buffer[i] = (v0 - k * v1).toFloat()
            }
        }
    }
}
