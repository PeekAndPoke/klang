package io.peekandpoke

import kotlin.math.sin
import kotlin.reflect.KFunction

typealias OscFn = (phase: Double) -> Double

fun oscillators(build: Oscillators.Builder.() -> Unit) =
    Oscillators.Builder().apply(build).build()

class Oscillators private constructor(
    val sawtooth: OscFn = ::sawtooth,
    val square: OscFn = ::square,
    val triangle: OscFn = ::triangle,
    val sine: OscFn = ::sine,
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun OscFn.name() = try {
            (this as? KFunction<Double>)?.name ?: this.toString()
        } catch (_: Exception) {
            this.toString()
        }

        fun sawtooth(phase: Double): Double {
            val gain = 0.6
            val x = phase / (2.0 * Math.PI)
            return gain * 2.0 * (x - kotlin.math.floor(x + 0.5))
        }

        fun square(phase: Double): Double {
            val gain = 0.5
            return gain * if (sin(phase) >= 0.0) 1.0 else -1.0
        }

        fun triangle(phase: Double): Double {
            val gain = 0.7
            // Triangle via asin(sin) normalized to [-1,1]
            return gain * (2.0 / Math.PI) * kotlin.math.asin(sin(phase))
        }

        fun sine(phase: Double): Double = sin(phase)
    }

    class Builder {
        private var sawtooth: OscFn = ::sawtooth
        private var square: OscFn = ::square
        private var triangle: OscFn = ::triangle
        private var sine: OscFn = ::sin

        fun sawtooth(osc: OscFn) = apply { sawtooth = osc }
        fun square(osc: OscFn) = apply { square = osc }
        fun triangle(osc: OscFn) = apply { triangle = osc }
        fun sine(osc: OscFn) = apply { sine = osc }

        internal fun build() = Oscillators(sawtooth, square, triangle, sine)
    }

    fun getByName(name: String?): OscFn = when (name) {
        "saw", "sawtooth" -> sawtooth
        "sqr", "square" -> square
        "tri" ,"triangle" -> triangle
        "sin", "sine" -> sine
        else -> sine
    }
}
