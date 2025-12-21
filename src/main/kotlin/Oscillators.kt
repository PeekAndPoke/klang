package io.peekandpoke

import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random
import kotlin.reflect.KFunction

typealias OscFn = (frequency: Double) -> Double

fun oscFn(block: OscFn) = block

fun oscillators(sampleRate: Int, build: Oscillators.Builder.() -> Unit = {}) =
    Oscillators.Builder(sampleRate).apply(build).build()

class Oscillators private constructor(
    val sampleRate: Int,
    val rng: Random,
    // Basic
    val sine: OscFn,
    val sawtooth: OscFn,
    val square: OscFn,
    val triangle: OscFn,
    // Extra
    val zawtooth: OscFn,
    val supersaw: OscFn,
    val pulze: OscFn,
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun OscFn.name() = try {
            (this as? KFunction<Double>)?.name ?: this.toString()
        } catch (_: Exception) {
            this.toString()
        }

        fun sine(gain: Double = 1.0): OscFn = { phase ->
            gain * sin(phase)
        }

        fun sawtooth(gain: Double = 0.6): OscFn = { phase ->
            val x = phase / (2.0 * Math.PI)
            gain * 2.0 * (x - kotlin.math.floor(x + 0.5))
        }

        fun square(gain: Double = 0.5): OscFn = { phase ->
            gain * if (sin(phase) >= 0.0) 1.0 else -1.0
        }

        fun triangle(gain: Double = 0.7): OscFn = { phase ->
            // Triangle via asin(sin) normalized to [-1,1]
            gain * (2.0 / Math.PI) * kotlin.math.asin(sin(phase))
        }

        fun zawtooth(gain: Double = 1.0): OscFn = { phase ->
            val x = phase / (2.0 * Math.PI)
            val frac = x - kotlin.math.floor(x)
            gain * (frac * 2.0 - 1.0)
        }

        fun supersaw(
            gain: Double = 0.6,
            voices: Int = 5,
            freqspread: Double = 0.2,
            seed: Double = 0.0,
        ): OscFn = { phase ->
            if (voices <= 1) {
                // Single voice degenerates to a plain ramp
                val x = phase / (2.0 * 2.0 * PI) // keep the same math style as below
                val frac = x - floor(x)
                gain * (frac * 2.0 - 1.0)
            } else {
                val center = (voices - 1) / 2.0
                var sum = 0.0

                for (i in 0 until voices) {
                    val norm = (i - center) / center            // [-1, 1]
                    val detune = 1.0 + freqspread * norm        // multiplicative detune
                    val phaseOffset = pseudoRandom01(i, seed) * 2.0 * PI

                    val p = phase * detune + phaseOffset
                    val x = p / (2.0 * PI)                      // cycles
                    val frac = x - floor(x)                     // [0, 1)
                    sum += (frac * 2.0 - 1.0)                   // [-1, 1)
                }

                gain * (sum / voices.toDouble())
            }
        }

        fun pulze(
            duty: Double = 0.5,
            gain: Double = 1.0,
        ): OscFn = { phase ->
            val d = duty.coerceIn(0.0, 1.0)
            val x = phase / (2.0 * PI)                  // cycles
            val cyclePos = x - floor(x)                 // [0, 1)
            gain * if (cyclePos < d) 1.0 else -1.0
        }

        fun dust(
            /** Sample rate */
            sampleRate: Int,
            /**
             * Strudel-style density in [0, 1].
             * 0.0 = no impulses, 1.0 = very crackly.
             */
            density: Double,
            /**
             * Maximum impulse rate (impulses per second) when density == 1.0.
             * Tune this to taste:
             * - ~200: "dust" / light grains
             * - ~800: "crackle" / vinyl-ish
             */
            maxRateHz: Double,
            /** Random */
            rng: Random,
        ): OscFn = { _ ->
            val d = density.coerceIn(0.0, 1.0)
            val rateHz = d * maxRateHz
            val p = (rateHz / sampleRate.toDouble()).coerceIn(0.0, 1.0)

            if (rng.nextDouble() < p) rng.nextDouble() else 0.0
        }

        private fun pseudoRandom01(voiceIndex: Int, seed: Double): Double {
            // Deterministic "random" in [0,1) from (voiceIndex, seed)
            val x = sin((voiceIndex + 1) * 12.9898 + seed * 78.233) * 43758.5453
            return x - floor(x)
        }

        private fun hash01(sampleIndex: Long, seed: Double): Double {
            // Deterministic pseudo-random in [0,1)
            val x = sin(sampleIndex.toDouble() * 12.9898 + seed * 78.233) * 43758.5453
            return x - floor(x)
        }

        private fun parseNameMul(name: String): Pair<String, Int> {
            // Accept "crackle*8" / "dust*2" / "crackle" (defaults to 1)
            val parts = name.split('*', limit = 2)
            val base = parts[0]
            val mul = parts.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
            return base to mul
        }
    }

    class Builder(val sampleRate: Int) {
        private var rng: Random = Random

        // Basic
        private var sine: OscFn = sine()
        private var sawtooth: OscFn = sawtooth()
        private var square: OscFn = square()
        private var triangle: OscFn = triangle()

        // Extra
        private var zawtooth: OscFn = zawtooth()
        private var supersaw: OscFn = supersaw()
        private var pulze: OscFn = pulze()

        // Random
        fun rng(rng: Random) = apply { this.rng = rng }

        // Basic
        fun sine(osc: OscFn) = apply { sine = osc }
        fun sawtooth(osc: OscFn) = apply { sawtooth = osc }
        fun square(osc: OscFn) = apply { square = osc }
        fun triangle(osc: OscFn) = apply { triangle = osc }

        // Extra
        fun zawtooth(osc: OscFn) = apply { zawtooth = osc }
        fun supersaw(osc: OscFn) = apply { supersaw = osc }
        fun pulze(osc: OscFn) = apply { pulze = osc }

        internal fun build() = Oscillators(
            sampleRate = sampleRate,
            rng = rng,
            // Basic
            sine = sine,
            sawtooth = sawtooth,
            square = square,
            triangle = triangle,
            // Extra
            zawtooth = zawtooth,
            supersaw = supersaw,
            pulze = pulze,
        )
    }

    val silence: OscFn = { 0.0 }

    fun get(name: String?, density: Double?): OscFn {
        if (name == null) return sine

        val density = (density ?: 0.1).coerceIn(0.0, 1.0)

        return parseNameMul(name).let { (base, mul) ->
            when (base) {
                // Basic
                "saw", "sawtooth" -> sawtooth
                "sqr", "square", "pulse" -> square
                "tri", "triangle" -> triangle
                "sin", "sine" -> sine

                // Extra
                "zaw", "zawtooth", "z_zawtooth" -> zawtooth
                "supersaw", "z_supersaw" -> supersaw
                "pulze", "z_pulze" -> pulze

                // Noise
                "dust" -> {
                    val maxHz = 200.0 * mul.toDouble()
                    dust(sampleRate = sampleRate, density = density, maxRateHz = maxHz, rng = rng)
                }

                "crackle" -> {
                    val maxHz = 800.0 * mul.toDouble()
                    dust(sampleRate = sampleRate, density = density, maxRateHz = maxHz, rng = rng)
                }

                else -> silence
            }
        }
    }
}
