package io.peekandpoke

import kotlin.math.*
import kotlin.random.Random
import kotlin.reflect.KFunction

typealias OscFn = (frequency: Double) -> Double
typealias NoiseFactory = (rng: Random) -> OscFn
typealias DustFactory = (sampleRate: Int, density: Double, rng: Random) -> OscFn
typealias SupersawFactory = (
    sampleRate: Int,
    baseFreqHz: Double,
    voices: Int,
    detuneSemitones: Double,
    panspread: Double,
    rng: Random,
) -> OscFn

fun oscFn(block: OscFn) = block

fun oscillators(sampleRate: Int, build: Oscillators.Builder.() -> Unit = {}) =
    Oscillators.Builder(sampleRate).apply(build).build()

class Oscillators private constructor(
    val sampleRate: Int,
    val rng: Random,
    // Oscillators
    val sine: OscFn,
    val sawtooth: OscFn,
    val square: OscFn,
    val triangle: OscFn,
    val zawtooth: OscFn,
    val supersaw: SupersawFactory,
    val pulze: OscFn,
    val impulse: () -> OscFn,
    // Noises
    val whiteNoise: NoiseFactory,
    val brownNoise: NoiseFactory,
    val pinkNoise: NoiseFactory,
    val dust: DustFactory,
    val crackle: DustFactory,
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
            gain * 2.0 * (x - floor(x + 0.5))
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
            val frac = x - floor(x)
            gain * (frac * 2.0 - 1.0)
        }

        fun supersaw(
            sampleRate: Int,
            baseFreqHz: Double,
            voices: Int = 5,
            detuneSemitones: Double = 0.2,
            panspread: Double = 0.4,
            rng: Random,
            gain: Double = 0.6,
        ): OscFn {
            val v = voices.coerceIn(1, 16)
            val sr = sampleRate.toDouble()

            // JS stores per-voice phase in cycles [0,1)
            val phase = DoubleArray(v) { rng.nextDouble() }

            val g1 = sqrt(1.0 - panspread.coerceIn(0.0, 1.0))
            val g2 = sqrt(panspread.coerceIn(0.0, 1.0))

            return { _ ->
                var sl = 0.0
                var srSum = 0.0

                for (n in 0 until v) {
                    val det = getUnisonDetune(v, detuneSemitones, n)
                    val freqAdjusted = applySemitoneDetuneToFrequency(baseFreqHz, det)
                    val dt = freqAdjusted / sr

                    val isOdd = (n and 1) == 1
                    val gainL = if (isOdd) g2 else g1
                    val gainR = if (isOdd) g1 else g2

                    val p = polyBlep(phase[n], dt)
                    val s = 2.0 * phase[n] - 1.0 - p

                    sl += s * gainL
                    srSum += s * gainR

                    phase[n] += dt
                    if (phase[n] > 1.0) phase[n] -= 1.0
                }

                // Mono output for now, like your JS "return sl + sr"
                gain * (sl + srSum) / v.toDouble()
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

        fun impulse(): OscFn {
            var lastPhase = Double.POSITIVE_INFINITY

            @Suppress("AssignedValueIsNeverRead")
            return { phase ->
                val v = if (phase < lastPhase) 1.0 else 0.0
                lastPhase = phase
                v
            }
        }

        // Noise //////////////////////////////////////////////////////////////////////

        fun whiteNoise(
            rng: Random,
            gain: Double = 1.0,
        ): OscFn = { _ ->
            gain * (rng.nextDouble() * 2.0 - 1.0)
        }

        fun brownNoise(
            rng: Random,
            gain: Double = 1.0,
        ): OscFn {
            var out = 0.0
            return { _ ->
                val white = rng.nextDouble() * 2.0 - 1.0
                out = (out + 0.02 * white) / 1.02
                gain * out
            }
        }

        fun pinkNoise(
            rng: Random,
            gain: Double = 1.0,
        ): OscFn {
            var b0 = 0.0
            var b1 = 0.0
            var b2 = 0.0
            var b3 = 0.0
            var b4 = 0.0
            var b5 = 0.0
            var b6 = 0.0

            @Suppress("AssignedValueIsNeverRead")
            return { _ ->
                val white = rng.nextDouble() * 2.0 - 1.0

                b0 = 0.99886 * b0 + white * 0.0555179
                b1 = 0.99332 * b1 + white * 0.0750759
                b2 = 0.96900 * b2 + white * 0.1538520
                b3 = 0.86650 * b3 + white * 0.3104856
                b4 = 0.55000 * b4 + white * 0.5329522
                b5 = -0.7616 * b5 - white * 0.0168980

                val pink = b0 + b1 + b2 + b3 + b4 + b5 + b6 + white * 0.5362
                b6 = white * 0.115926

                gain * (pink * 0.11)
            }
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

        private fun polyBlep(tIn: Double, dt: Double): Double {
            var t = tIn
            // 0 <= t < 1
            if (t < dt) {
                t /= dt
                // 2 * (t - t^2/2 - 0.5)  ==  t + t - t*t - 1
                return t + t - t * t - 1.0
            }
            // -1 < t < 0
            if (t > 1.0 - dt) {
                t = (t - 1.0) / dt
                // 2 * (t^2/2 + t + 0.5) == t*t + t + t + 1
                return t * t + t + t + 1.0
            }
            // 0 otherwise
            return 0.0
        }

        private fun applySemitoneDetuneToFrequency(frequency: Double, detuneSemitones: Double): Double =
            frequency * 2.0.pow(detuneSemitones / 12.0)

        private fun getUnisonDetune(unison: Int, detune: Double, voiceIndex: Int): Double {
            if (unison < 2) return 0.0
            val a = -detune * 0.5
            val b = detune * 0.5
            val n = voiceIndex.toDouble() / (unison - 1).toDouble()
            return n * (b - a) + a
        }

        private fun parseNameMul(name: String): Pair<String, Int> {
            // Accept "crackle*8" / "dust*2" / "crackle" (defaults to 1)
            val parts = name.split('*', limit = 2)
            val base = parts[0].lowercase()
            val mul = parts.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
            return base to mul
        }
    }

    class Builder(val sampleRate: Int) {
        private var rng: Random = Random

        // Oscillators
        private var sine: OscFn = sine()
        private var sawtooth: OscFn = sawtooth()
        private var square: OscFn = square()
        private var triangle: OscFn = triangle()
        private var zawtooth: OscFn = zawtooth()
        private var supersaw: SupersawFactory = { sampleRate, baseFreqHz, voices, detuneSemitones, panspread, rng ->
            supersaw(
                sampleRate = sampleRate,
                baseFreqHz = baseFreqHz,
                voices = voices,
                detuneSemitones = detuneSemitones,
                panspread = panspread,
                rng = rng,
            )
        }
        private var pulze: OscFn = pulze()
        private var impulse: () -> OscFn = { impulse() }

        // Noises
        private var whiteNoise: NoiseFactory = { rng -> whiteNoise(rng = rng) }
        private var brownNoise: NoiseFactory = { rng -> brownNoise(rng = rng) }
        private var pinkNoise: NoiseFactory = { rng -> pinkNoise(rng = rng) }
        private var dust: DustFactory = { sampleRate, density, rng ->
            dust(sampleRate, density, 200.0, rng)
        }
        private var crackle: DustFactory = { sampleRate, density, rng ->
            dust(sampleRate, density, 800.0, rng)
        }

        // Random
        fun rng(rng: Random) = apply { this.rng = rng }

        // Oscillators
        fun sine(osc: OscFn) = apply { sine = osc }
        fun sawtooth(osc: OscFn) = apply { sawtooth = osc }
        fun square(osc: OscFn) = apply { square = osc }
        fun triangle(osc: OscFn) = apply { triangle = osc }
        fun zawtooth(osc: OscFn) = apply { zawtooth = osc }
        fun supersaw(osc: SupersawFactory) = apply { supersaw = osc }
        fun pulze(osc: OscFn) = apply { pulze = osc }
        fun impulse(osc: () -> OscFn) = apply { impulse = osc }

        // Noises
        fun whiteNoise(osc: NoiseFactory) = apply { whiteNoise = osc }
        fun brownNoise(osc: NoiseFactory) = apply { brownNoise = osc }
        fun dust(osc: DustFactory) = apply { dust = osc }
        fun crackle(osc: DustFactory) = apply { crackle = osc }

        internal fun build() = Oscillators(
            sampleRate = sampleRate,
            rng = rng,
            // Oscillators
            sine = sine,
            sawtooth = sawtooth,
            square = square,
            triangle = triangle,
            zawtooth = zawtooth,
            supersaw = supersaw,
            pulze = pulze,
            impulse = impulse,
            // Noises
            whiteNoise = whiteNoise,
            brownNoise = brownNoise,
            pinkNoise = pinkNoise,
            dust = dust,
            crackle = crackle,
        )
    }

    val silence: OscFn = { 0.0 }

    fun get(e: StrudelEvent, freqHz: Double?): OscFn = get(
        name = e.osc,
        freqHz = freqHz,
        density = e.density,
        unison = e.unison,
        detune = e.detune,
        spread = e.spread,
    )

    fun get(
        name: String?,
        freqHz: Double?,
        density: Double?,
        unison: Double?,
        detune: Double?,
        spread: Double?,
    ): OscFn {
        if (name == null) return sine

        val density = (density ?: 0.1)
        val unison = (unison ?: 5.0)
        val detune = (detune ?: 0.2)
        val panSpread = (spread ?: 0.4)

        return parseNameMul(name).let { (base, mul) ->
            when (base) {
                // Oscillators
                "saw", "sawtooth" -> sawtooth
                "sqr", "square", "pulse" -> square
                "tri", "triangle" -> triangle
                "sin", "sine" -> sine
                "zaw", "zawtooth", "z_zawtooth" -> zawtooth
                "supersaw", "z_supersaw" -> if (freqHz != null) {
                    this.supersaw(
                        /* sampleRate */ sampleRate,
                        /* baseFreqHz */  freqHz,
                        /* voices */  (unison * mul).toInt(),
                        /* detuneSemitones */  detune,
                        /* panspread */  panSpread,
                        /* rng */  rng,
                    )
                } else {
                    silence
                }
                "pulze", "z_pulze" -> pulze // TODO: parameterize
                "impulse" -> this.impulse()

                // Noises
                "white", "whitenoise" -> this.whiteNoise(rng)
                "brown", "brownnoise" -> this.brownNoise(rng)
                "pink", "pinknoise" -> this.pinkNoise(rng)
                "dust" -> this.dust(sampleRate, density, rng)
                "crackle" -> this.crackle(sampleRate, density, rng)

                else -> silence
            }
        }
    }
}
