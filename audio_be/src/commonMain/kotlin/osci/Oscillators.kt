package io.peekandpoke.klang.audio_be.osci

import io.peekandpoke.klang.audio_be.TWO_PI
import io.peekandpoke.klang.audio_be.safeEnumOrNull
import kotlin.jvm.JvmStatic
import kotlin.math.*
import kotlin.random.Random

fun interface OscFn {
    /**
     * Fills [buffer] starting at [offset] for [length] samples.
     * Uses [phase] as the starting phase and increments it by [phaseInc] per sample.
     *
     * [phaseMod] is an optional array of modulation factors (1.0 = no change).
     * If provided, it must be at least as large as [buffer] (or valid at [offset]).
     *
     * Returns the next phase value.
     */
    fun process(
        buffer: DoubleArray,
        offset: Int,
        length: Int,
        phase: Double,
        phaseInc: Double,
        phaseMod: DoubleArray?,
    ): Double
}

/**
 * Wraps an [OscFn] with a one-pole low-pass filter to tame harsh harmonics.
 * @param warmthFactor Amount of filtering (0.0 = none, 1.0 = full).
 */
fun OscFn.withWarmth(warmthFactor: Double): OscFn {
    if (warmthFactor <= 0.0) return this // Optimization: skip if no warmth

    // State is captured here, unique to this wrapper instance
    var lastSample = 0.0
    val alpha = warmthFactor.coerceIn(0.0, 0.99)

    return OscFn { buffer, offset, length, phase, phaseInc, phaseMod ->
        // 1. Generate raw waveform
        val nextPhase = this.process(buffer, offset, length, phase, phaseInc, phaseMod)

        // 2. Apply One-Pole LPF
        val end = offset + length
        for (i in offset until end) {
            val raw = buffer[i]
            val smoothed = raw + alpha * (lastSample - raw)
            buffer[i] = smoothed
            lastSample = smoothed
        }

        nextPhase
    }
}

typealias NoiseFactory = (rng: Random) -> OscFn
typealias DustFactory = (sampleRate: Int, density: Double, rng: Random) -> OscFn
typealias SupersawFactory = (
    sampleRate: Int,
    baseFreqHz: Double,
    voices: Int,
    freqSpread: Double,
    panSpread: Double,
    rng: Random,
) -> OscFn

fun oscillators(sampleRate: Int, build: Oscillators.Builder.() -> Unit = {}) =
    Oscillators.Builder(sampleRate).apply(build).build()

class Oscillators private constructor(
    val sampleRate: Int,
    val rng: Random,
    // Oscillators
    val silence: OscFn,
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
        private val namesSet = Names.entries.map { it.name }.toSet()

        val silenceFn = OscFn { buffer, offset, length, _, _, _ ->
            buffer.fill(0.0, offset, offset + length)
            0.0
        }

        fun sineFn(gain: Double = 1.0) = OscFn { buffer, offset, length, startPhase, phaseInc, phaseMod ->
            var p = startPhase
            val end = offset + length

            if (phaseMod == null) {
                for (i in offset until end) {
                    buffer[i] = gain * sin(p)
                    p += phaseInc
                    if (p >= TWO_PI) p -= TWO_PI
                }
            } else {
                for (i in offset until end) {
                    buffer[i] = gain * sin(p)
                    p += phaseInc * phaseMod[i]
                    if (p >= TWO_PI) p -= TWO_PI
                }
            }
            p
        }

        fun sawtoothFn(gain: Double = 0.6) = OscFn { buffer, offset, length, startPhase, phaseInc, phaseMod ->
            var p = startPhase / TWO_PI // Normalize to 0..1 for PolyBLEP
            val inc = phaseInc / TWO_PI
            val end = offset + length

            if (phaseMod == null) {
                for (i in offset until end) {
                    // Naive Saw: 2 * p - 1
                    var out = 2.0 * p - 1.0

                    // PolyBLEP Correction (Anti-Aliasing)
                    out -= polyBlep(p, inc)

                    // Apply Gain
                    buffer[i] = gain * out

                    p += inc
                    if (p >= 1.0) p -= 1.0
                }
            } else {
                for (i in offset until end) {
                    val dt = inc * phaseMod[i]

                    var out = 2.0 * p - 1.0
                    out -= polyBlep(p, dt)

                    buffer[i] = gain * out

                    p += dt
                    if (p >= 1.0) p -= 1.0
                }
            }

            p * TWO_PI // Denormalize back to radians
        }

        fun squareFn(gain: Double = 0.5) = OscFn { buffer, offset, length, startPhase, phaseInc, phaseMod ->
            var p = startPhase
            val end = offset + length

            if (phaseMod == null) {
                for (i in offset until end) {
                    buffer[i] = gain * if (sin(p) >= 0.0) 1.0 else -1.0
                    p += phaseInc
                    if (p >= TWO_PI) p -= TWO_PI
                }
            } else {
                for (i in offset until end) {
                    buffer[i] = gain * if (sin(p) >= 0.0) 1.0 else -1.0
                    p += phaseInc * phaseMod[i]
                    if (p >= TWO_PI) p -= TWO_PI
                }
            }
            p
        }

        fun triangleFn(gain: Double = 0.7) = OscFn { buffer, offset, length, startPhase, phaseInc, phaseMod ->
            var p = startPhase
            val end = offset + length
            val norm = 2.0 / PI

            if (phaseMod == null) {
                for (i in offset until end) {
                    buffer[i] = gain * norm * asin(sin(p))
                    p += phaseInc
                    if (p >= TWO_PI) p -= TWO_PI
                }
            } else {
                for (i in offset until end) {
                    buffer[i] = gain * norm * asin(sin(p))
                    p += phaseInc * phaseMod[i]
                    if (p >= TWO_PI) p -= TWO_PI
                }
            }
            p
        }

        fun zawtoothFn(gain: Double = 1.0) = OscFn { buffer, offset, length, startPhase, phaseInc, phaseMod ->
            var p = startPhase
            val end = offset + length
            val invTwoPi = 1.0 / TWO_PI

            if (phaseMod == null) {
                for (i in offset until end) {
                    val x = p * invTwoPi
                    val frac = x - floor(x)
                    buffer[i] = gain * (frac * 2.0 - 1.0)
                    p += phaseInc
                    if (p >= TWO_PI) p -= TWO_PI
                }
            } else {
                for (i in offset until end) {
                    val x = p * invTwoPi
                    val frac = x - floor(x)
                    buffer[i] = gain * (frac * 2.0 - 1.0)
                    p += phaseInc * phaseMod[i]
                    if (p >= TWO_PI) p -= TWO_PI
                }
            }
            p
        }

        fun supersawFn(
            sampleRate: Int,
            baseFreqHz: Double,
            voices: Int,
            freqSpread: Double,
            panSpread: Double,
            rng: Random,
            gain: Double = 0.6,
        ): OscFn {
            val v = voices.coerceIn(1, 16)
            val sr = sampleRate.toDouble()
            val blepMinDt = 1.0e-5

            // Internal state for supersaw phases (independent of Voice phase)
            val phases = DoubleArray(v) { rng.nextDouble() }

            // MONO OPTIMIZATION:
            // In stereo, panSpread splits energy between L/R.
            // In mono, summing L+R of a coherent signal (same phase) just sums amplitudes.
            // To prevent volume swelling at center pan (where sqrt(0.5)+sqrt(0.5) > 1),
            // we ignore panSpread for mono output and just normalize by voice count.
            val voiceGain = gain / v.toDouble()

            // Pre-calculate detunes per voice
            val detunes = DoubleArray(v) { n ->
                val det = getUnisonDetune(v, freqSpread, n)
                val freqAdjusted = applySemitoneDetuneToFrequency(baseFreqHz, det)
                freqAdjusted / sr
            }

            return OscFn { buffer, offset, length, _, _, phaseMod ->
                val end = offset + length

                // 1. GENERATE WAVEFORM
                if (phaseMod == null) {
                    // OPTIMIZED PATH: No Phase Modulation

                    // --- Voice 0 (Write / Overwrite Buffer) ---
                    // We unroll the first voice to initialize the buffer instead of filling with 0.0 first
                    run {
                        var p = phases[0]
                        val dt = detunes[0]

                        if (dt <= blepMinDt) {
                            // DC / Low freq path (no blep)
                            for (i in offset until end) {
                                buffer[i] = (2.0 * p - 1.0) * voiceGain
                                p += dt
                                if (p >= 1.0) p -= 1.0
                            }
                        } else {
                            // Audio rate path (polyBlep)
                            for (i in offset until end) {
                                val blep = polyBlep(p, dt)
                                buffer[i] = (2.0 * p - 1.0 - blep) * voiceGain
                                p += dt
                                if (p >= 1.0) p -= 1.0
                            }
                        }
                        phases[0] = p
                    }

                    // --- Voices 1..v (Accumulate) ---
                    for (n in 1 until v) {
                        var p = phases[n]
                        val dt = detunes[n]

                        if (dt <= blepMinDt) {
                            for (i in offset until end) {
                                buffer[i] += (2.0 * p - 1.0) * voiceGain
                                p += dt
                                if (p >= 1.0) p -= 1.0
                            }
                        } else {
                            for (i in offset until end) {
                                val blep = polyBlep(p, dt)
                                buffer[i] += (2.0 * p - 1.0 - blep) * voiceGain
                                p += dt
                                if (p >= 1.0) p -= 1.0
                            }
                        }
                        phases[n] = p
                    }
                } else {
                    // MODULATED PATH (Slower, per-sample inner loop)
                    for (i in offset until end) {
                        val mod = phaseMod[i]
                        var sum = 0.0

                        for (n in 0 until v) {
                            var p = phases[n]
                            // Linear FM: mod is a frequency multiplier (1.0 = no change)
                            val dt = detunes[n] * mod

                            val s = if (dt <= blepMinDt) {
                                (2.0 * p - 1.0)
                            } else {
                                val blep = polyBlep(p, dt)
                                (2.0 * p - 1.0 - blep)
                            }
                            sum += s

                            p += dt
                            if (p >= 1.0) p -= 1.0
                            phases[n] = p
                        }
                        buffer[i] = sum * voiceGain
                    }
                }

                0.0 // Return value unused for this oscillator type
            }
        }

        fun pulzeFn(
            duty: Double = 0.5,
            gain: Double = 1.0,
        ) = OscFn { buffer, offset, length, startPhase, phaseInc, phaseMod ->
            var p = startPhase
            val end = offset + length
            val d = duty.coerceIn(0.0, 1.0)
            val invTwoPi = 1.0 / TWO_PI

            if (phaseMod == null) {
                for (i in offset until end) {
                    val x = p * invTwoPi
                    val cyclePos = x - floor(x)
                    buffer[i] = gain * if (cyclePos < d) 1.0 else -1.0
                    p += phaseInc
                    if (p >= TWO_PI) p -= TWO_PI
                }
            } else {
                for (i in offset until end) {
                    val x = p * invTwoPi
                    val cyclePos = x - floor(x)
                    buffer[i] = gain * if (cyclePos < d) 1.0 else -1.0
                    p += phaseInc * phaseMod[i]
                    if (p >= TWO_PI) p -= TWO_PI
                }
            }
            p
        }

        fun impulseFn(): OscFn {
            var lastPhase = Double.POSITIVE_INFINITY

            return OscFn { buffer, offset, length, startPhase, phaseInc, phaseMod ->
                var p = startPhase
                val end = offset + length

                if (phaseMod == null) {
                    for (i in offset until end) {
                        buffer[i] = if (p < lastPhase) 1.0 else 0.0
                        lastPhase = p
                        p += phaseInc
                        if (p >= TWO_PI) p -= TWO_PI
                    }
                } else {
                    for (i in offset until end) {
                        buffer[i] = if (p < lastPhase) 1.0 else 0.0
                        lastPhase = p
                        p += phaseInc * phaseMod[i]
                        if (p >= TWO_PI) p -= TWO_PI
                    }
                }
                p
            }
        }

        // Noise //////////////////////////////////////////////////////////////////////
        // Note: Noises ignore phase/phaseInc, they just fill random

        fun whiteNoiseFn(rng: Random, gain: Double = 1.0) = OscFn { buffer, offset, length, _, _, _ ->
            val end = offset + length
            for (i in offset until end) {
                buffer[i] = gain * (rng.nextDouble() * 2.0 - 1.0)
            }
            0.0
        }

        fun brownNoiseFn(rng: Random, gain: Double = 1.0): OscFn {
            var out = 0.0

            return OscFn { buffer, offset, length, _, _, _ ->
                val end = offset + length
                for (i in offset until end) {
                    val white = rng.nextDouble() * 2.0 - 1.0
                    out = (out + 0.02 * white) / 1.02
                    buffer[i] = gain * out
                }
                0.0
            }
        }

        fun pinkNoiseFn(rng: Random, gain: Double = 1.0): OscFn {
            var b0 = 0.0
            var b1 = 0.0
            var b2 = 0.0
            var b3 = 0.0
            var b4 = 0.0
            var b5 = 0.0
            var b6 = 0.0

            return OscFn { buffer, offset, length, _, _, _ ->
                val end = offset + length
                for (i in offset until end) {
                    val white = rng.nextDouble() * 2.0 - 1.0
                    b0 = 0.99886 * b0 + white * 0.0555179
                    b1 = 0.99332 * b1 + white * 0.0750759
                    b2 = 0.96900 * b2 + white * 0.1538520
                    b3 = 0.86650 * b3 + white * 0.3104856
                    b4 = 0.55000 * b4 + white * 0.5329522
                    b5 = -0.7616 * b5 - white * 0.0168980
                    val pink = b0 + b1 + b2 + b3 + b4 + b5 + b6 + white * 0.5362
                    b6 = white * 0.115926
                    buffer[i] = gain * (pink * 0.11)
                }
                0.0
            }
        }

        fun dustFn(
            sampleRate: Int,
            density: Double,
            maxRateHz: Double,
            rng: Random,
        ) = OscFn { buffer, offset, length, _, _, _ ->
            val d = density.coerceIn(0.0, 1.0)
            val rateHz = d * maxRateHz
            val p = (rateHz / sampleRate.toDouble()).coerceIn(0.0, 1.0)
            val end = offset + length

            for (i in offset until end) {
                buffer[i] = if (rng.nextDouble() < p) rng.nextDouble() else 0.0
            }
            0.0
        }

        @Suppress("NOTHING_TO_INLINE")
        @JvmStatic
        fun polyBlep(t: Double, dt: Double): Double {
            // t = phase (0..1), dt = increment per sample
            return when {
                t < dt -> {
                    val r = t / dt
                    r + r - r * r - 1.0
                }

                t > 1.0 - dt -> {
                    val r = (t - 1.0) / dt
                    r * r + r + r + 1.0
                }

                else -> 0.0
            }
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

        /**
         * parses something like "crackle*8" / "dust*2" / "crackle" (defaults to 1)
         *
         * return the sound name in lowercase and the multiplier as a Pair
         */
        private fun parseNameMul(name: String): Pair<String, Int> {
            // Accept "crackle*8" / "dust*2" / "crackle" (defaults to 1)
            val parts = name.split('*', limit = 2)
            val base = parts[0].lowercase()
            val mul = parts.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 1

            return base to mul
        }
    }

    @Suppress("EnumEntryName")
    enum class Names {
        // Silence
        silence,

        // Sine
        sin, sine,

        // Square
        sqr, square, pulse,

        // Triangle
        tri, triangle,

        // Sawtooth
        saw, sawtooth,

        // Zawtooth
        zaw, zawtooth, z_zawtooth,

        // Supersaw
        supersaw, z_supersaw,

        // Pulze
        pulze, z_pulze,

        // Impulse
        impulse,

        // White Noise
        white, whitenoise,

        // Brown noise
        brown, brownnoise,

        // Pink noise
        pink, pinknoise,

        // Dust
        dust,

        // Crackle
        crackle,
    }

    @Suppress("unused")
    class Builder(val sampleRate: Int) {
        private var rng: Random = Random

        // Oscillators
        private var silence: OscFn = silenceFn
        private var sine: OscFn = sineFn()
        private var sawtooth: OscFn = sawtoothFn()
        private var square: OscFn = squareFn()
        private var triangle: OscFn = triangleFn()
        private var zawtooth: OscFn = zawtoothFn()
        private var supersaw: SupersawFactory = { sampleRate, baseFreqHz, voices, detuneSemitones, panspread, rng ->
            supersawFn(
                sampleRate = sampleRate,
                baseFreqHz = baseFreqHz,
                voices = voices,
                freqSpread = detuneSemitones,
                panSpread = panspread,
                rng = rng,
            )
        }
        private var pulze: OscFn = pulzeFn()
        private var impulse: () -> OscFn = { impulseFn() }

        // Noises
        private var whiteNoise: NoiseFactory = { rng -> whiteNoiseFn(rng = rng) }
        private var brownNoise: NoiseFactory = { rng -> brownNoiseFn(rng = rng) }
        private var pinkNoise: NoiseFactory = { rng -> pinkNoiseFn(rng = rng) }
        private var dust: DustFactory = { sampleRate, density, rng ->
            dustFn(sampleRate, density, 200.0, rng)
        }
        private var crackle: DustFactory = { sampleRate, density, rng ->
            dustFn(sampleRate, density, 800.0, rng)
        }

        // Random
        fun rng(rng: Random) = apply { this.rng = rng }

        // Oscillators
        fun silence(osc: OscFn) = apply { silence = osc }
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
            silence = silence,
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

    fun isOsc(sound: String?): Boolean {
        // No sound mean sine wave oscillator
        if (sound == null) return true

        val s = parseNameMul(sound).first

        return namesSet.contains(s)
    }

    fun get(
        name: String?,
        freqHz: Double?,
        density: Double?,
        voices: Double?,
        panSpread: Double?,
        freqSpread: Double?,
    ): OscFn {
        if (name == null) return triangle

        val density = (density ?: 0.2)
        val unison = (voices ?: 5.0)
        val freqSpread = (freqSpread ?: 0.2)
        val panSpread = (panSpread ?: 0.4)

        return parseNameMul(name).let { (base, mul) ->
            val baseEnum = safeEnumOrNull<Names>(base)

            when (baseEnum) {
                // Oscillators
                Names.silence -> silence
                Names.saw, Names.sawtooth -> sawtooth
                Names.sqr, Names.square, Names.pulse -> square
                Names.tri, Names.triangle -> triangle
                Names.sin, Names.sine -> sine
                Names.zaw, Names.zawtooth, Names.z_zawtooth -> zawtooth
                Names.supersaw, Names.z_supersaw -> if (freqHz != null) {
                    this.supersaw(
                        /* sampleRate */ sampleRate,
                        /* baseFreqHz */  freqHz,
                        /* voices */  (unison * mul).toInt(),
                        /* detuneSemitones */  freqSpread,
                        /* panspread */  panSpread,
                        /* rng */  rng,
                    )
                } else {
                    silence
                }

                Names.pulze, Names.z_pulze -> pulze // TODO: parameterize
                Names.impulse -> this.impulse()

                // Noises
                Names.white, Names.whitenoise -> this.whiteNoise(rng)
                Names.brown, Names.brownnoise -> this.brownNoise(rng)
                Names.pink, Names.pinknoise -> this.pinkNoise(rng)
                Names.dust -> this.dust(sampleRate, density, rng)
                Names.crackle -> this.crackle(sampleRate, density, rng)

                else -> silence
            }
        }
    }
}
