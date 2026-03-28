package io.peekandpoke.klang.audio_bridge

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface ExciterDsl {

    /** Whether this exciter requires a frequency to produce sound. Noise sources override to false. */
    val needsFreq: Boolean get() = true

    /** Collects all [Param] leaf nodes in this DSL subtree. */
    fun collectParams(out: MutableList<Param>)

    // ═════════════════════════════════════════════════════════════════════════════
    // Parameter Slot
    // ═════════════════════════════════════════════════════════════════════════════

    /**
     * A named parameter slot that produces a constant signal by default.
     *
     * At runtime, fills a buffer with [default]. Can be replaced with any [ExciterDsl]
     * subtree to modulate the parameter at audio rate.
     *
     * @param name parameter name — used for discovery, UI display, and oscParams override matching
     * @param default constant value when no modulator is wired in
     * @param description human-readable description for UI tooltips and auto-generated docs
     */
    @Serializable
    @SerialName("param")
    data class Param(
        val name: String,
        val default: Double,
        val description: String = "",
    ) : ExciterDsl {
        override val needsFreq: Boolean get() = false
        override fun collectParams(out: MutableList<Param>) {
            out.add(this)
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Oscillator Primitives
    // ═════════════════════════════════════════════════════════════════════════════

    @Serializable
    @SerialName("sine")
    data class Sine(
        val gain: ExciterDsl = Param("gain", 1.0, "Output amplitude."),
        val analog: ExciterDsl = Param("analog", 0.0, "Perlin noise pitch drift amount."),
    ) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {
            gain.collectParams(out); analog.collectParams(out)
        }
    }

    @Serializable
    @SerialName("sawtooth")
    data class Sawtooth(
        val gain: ExciterDsl = Param("gain", 0.6, "Output amplitude."),
        val analog: ExciterDsl = Param("analog", 0.0, "Perlin noise pitch drift amount."),
    ) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {
            gain.collectParams(out); analog.collectParams(out)
        }
    }

    @Serializable
    @SerialName("square")
    data class Square(
        val gain: ExciterDsl = Param("gain", 0.5, "Output amplitude."),
        val analog: ExciterDsl = Param("analog", 0.0, "Perlin noise pitch drift amount."),
    ) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {
            gain.collectParams(out); analog.collectParams(out)
        }
    }

    @Serializable
    @SerialName("triangle")
    data class Triangle(
        val gain: ExciterDsl = Param("gain", 0.7, "Output amplitude."),
        val analog: ExciterDsl = Param("analog", 0.0, "Perlin noise pitch drift amount."),
    ) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {
            gain.collectParams(out); analog.collectParams(out)
        }
    }

    @Serializable
    @SerialName("white-noise")
    data class WhiteNoise(
        val gain: ExciterDsl = Param("gain", 1.0, "Output amplitude."),
    ) : ExciterDsl {
        override val needsFreq: Boolean get() = false
        override fun collectParams(out: MutableList<Param>) {
            gain.collectParams(out)
        }
    }

    @Serializable
    @SerialName("zawtooth")
    data class Zawtooth(
        val gain: ExciterDsl = Param("gain", 1.0, "Output amplitude."),
        val analog: ExciterDsl = Param("analog", 0.0, "Perlin noise pitch drift amount."),
    ) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {
            gain.collectParams(out); analog.collectParams(out)
        }
    }

    @Serializable
    @SerialName("impulse")
    data class Impulse(
        val gain: ExciterDsl = Param("gain", 1.0, "Output amplitude."),
        val analog: ExciterDsl = Param("analog", 0.0, "Perlin noise pitch drift amount."),
    ) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {
            gain.collectParams(out); analog.collectParams(out)
        }
    }

    @Serializable
    @SerialName("pulze")
    data class Pulze(
        val duty: ExciterDsl = Param("duty", 0.5, "Duty cycle 0.0..1.0. Controls high/low ratio."),
        val gain: ExciterDsl = Param("gain", 1.0, "Output amplitude."),
        val analog: ExciterDsl = Param("analog", 0.0, "Perlin noise pitch drift amount."),
    ) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {
            duty.collectParams(out); gain.collectParams(out); analog.collectParams(out)
        }
    }

    @Serializable
    @SerialName("brown-noise")
    data class BrownNoise(
        val gain: ExciterDsl = Param("gain", 1.0, "Output amplitude."),
    ) : ExciterDsl {
        override val needsFreq: Boolean get() = false
        override fun collectParams(out: MutableList<Param>) {
            gain.collectParams(out)
        }
    }

    @Serializable
    @SerialName("pink-noise")
    data class PinkNoise(
        val gain: ExciterDsl = Param("gain", 1.0, "Output amplitude."),
    ) : ExciterDsl {
        override val needsFreq: Boolean get() = false
        override fun collectParams(out: MutableList<Param>) {
            gain.collectParams(out)
        }
    }

    @Serializable
    @SerialName("dust")
    data class Dust(
        val density: ExciterDsl = Param("density", 0.2, "Impulse rate 0.0..1.0."),
        val gain: ExciterDsl = Param("gain", 1.0, "Output amplitude."),
    ) : ExciterDsl {
        override val needsFreq: Boolean get() = false
        override fun collectParams(out: MutableList<Param>) {
            density.collectParams(out); gain.collectParams(out)
        }
    }

    @Serializable
    @SerialName("crackle")
    data class Crackle(
        val density: ExciterDsl = Param("density", 0.2, "Impulse rate 0.0..1.0."),
        val gain: ExciterDsl = Param("gain", 1.0, "Output amplitude."),
    ) : ExciterDsl {
        override val needsFreq: Boolean get() = false
        override fun collectParams(out: MutableList<Param>) {
            density.collectParams(out); gain.collectParams(out)
        }
    }

    @Serializable
    @SerialName("ramp")
    data class Ramp(
        val gain: ExciterDsl = Param("gain", 0.6, "Output amplitude."),
        val analog: ExciterDsl = Param("analog", 0.0, "Perlin noise pitch drift amount."),
    ) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {
            gain.collectParams(out); analog.collectParams(out)
        }
    }

    @Serializable
    @SerialName("supersaw")
    data class SuperSaw(
        val voices: Int = 8,
        val freqSpread: ExciterDsl = Param("freqSpread", 0.2, "Detune spread in semitones between voices."),
        val gain: ExciterDsl = Param("gain", 0.6, "Output amplitude."),
        val analog: ExciterDsl = Param("analog", 0.0, "Perlin noise pitch drift amount."),
    ) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {
            freqSpread.collectParams(out); gain.collectParams(out); analog.collectParams(out)
        }
    }

    @Serializable
    @SerialName("supersine")
    data class SuperSine(
        val voices: Int = 8,
        val freqSpread: ExciterDsl = Param("freqSpread", 0.2, "Detune spread in semitones between voices."),
        val gain: ExciterDsl = Param("gain", 1.0, "Output amplitude."),
        val analog: ExciterDsl = Param("analog", 0.0, "Perlin noise pitch drift amount."),
    ) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {
            freqSpread.collectParams(out); gain.collectParams(out); analog.collectParams(out)
        }
    }

    @Serializable
    @SerialName("supersquare")
    data class SuperSquare(
        val voices: Int = 8,
        val freqSpread: ExciterDsl = Param("freqSpread", 0.2, "Detune spread in semitones between voices."),
        val gain: ExciterDsl = Param("gain", 0.5, "Output amplitude."),
        val analog: ExciterDsl = Param("analog", 0.0, "Perlin noise pitch drift amount."),
    ) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {
            freqSpread.collectParams(out); gain.collectParams(out); analog.collectParams(out)
        }
    }

    @Serializable
    @SerialName("supertri")
    data class SuperTri(
        val voices: Int = 8,
        val freqSpread: ExciterDsl = Param("freqSpread", 0.2, "Detune spread in semitones between voices."),
        val gain: ExciterDsl = Param("gain", 0.7, "Output amplitude."),
        val analog: ExciterDsl = Param("analog", 0.0, "Perlin noise pitch drift amount."),
    ) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {
            freqSpread.collectParams(out); gain.collectParams(out); analog.collectParams(out)
        }
    }

    @Serializable
    @SerialName("superramp")
    data class SuperRamp(
        val voices: Int = 8,
        val freqSpread: ExciterDsl = Param("freqSpread", 0.2, "Detune spread in semitones between voices."),
        val gain: ExciterDsl = Param("gain", 0.6, "Output amplitude."),
        val analog: ExciterDsl = Param("analog", 0.0, "Perlin noise pitch drift amount."),
    ) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {
            freqSpread.collectParams(out); gain.collectParams(out); analog.collectParams(out)
        }
    }

    @Serializable
    @SerialName("silence")
    data object Silence : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {}
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Physical Models
    // ═════════════════════════════════════════════════════════════════════════════

    @Serializable
    @SerialName("pluck")
    data class Pluck(
        val decay: ExciterDsl = Param("decay", 0.996, "Feedback decay factor. Lower = shorter sustain."),
        val brightness: ExciterDsl = Param("brightness", 0.5, "Lowpass cutoff in feedback loop. 0=dark, 1=bright."),
        val pickPosition: ExciterDsl = Param("pickPosition", 0.5, "Pluck position along string. Affects harmonic content."),
        val stiffness: ExciterDsl = Param("stiffness", 0.0, "Higher harmonics decay faster. 0=nylon, 1=piano wire."),
        val gain: ExciterDsl = Param("gain", 0.7, "Output amplitude."),
        val analog: ExciterDsl = Param("analog", 0.0, "Perlin noise pitch drift amount."),
    ) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {
            decay.collectParams(out); brightness.collectParams(out); pickPosition.collectParams(out)
            stiffness.collectParams(out); gain.collectParams(out); analog.collectParams(out)
        }
    }

    @Serializable
    @SerialName("superpluck")
    data class SuperPluck(
        val voices: Int = 8,
        val freqSpread: ExciterDsl = Param("freqSpread", 0.2, "Detune spread in semitones between voices."),
        val decay: ExciterDsl = Param("decay", 0.996, "Feedback decay factor. Lower = shorter sustain."),
        val brightness: ExciterDsl = Param("brightness", 0.5, "Lowpass cutoff in feedback loop. 0=dark, 1=bright."),
        val pickPosition: ExciterDsl = Param("pickPosition", 0.5, "Pluck position along string. Affects harmonic content."),
        val stiffness: ExciterDsl = Param("stiffness", 0.0, "Higher harmonics decay faster. 0=nylon, 1=piano wire."),
        val gain: ExciterDsl = Param("gain", 0.7, "Output amplitude."),
        val analog: ExciterDsl = Param("analog", 0.0, "Perlin noise pitch drift amount."),
    ) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {
            freqSpread.collectParams(out); decay.collectParams(out); brightness.collectParams(out)
            pickPosition.collectParams(out); stiffness.collectParams(out); gain.collectParams(out); analog.collectParams(out)
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Arithmetic Composition
    // ═════════════════════════════════════════════════════════════════════════════

    @Serializable
    @SerialName("plus")
    data class Plus(val left: ExciterDsl, val right: ExciterDsl) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {
            left.collectParams(out); right.collectParams(out)
        }
    }

    @Serializable
    @SerialName("times")
    data class Times(val left: ExciterDsl, val right: ExciterDsl) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {
            left.collectParams(out); right.collectParams(out)
        }
    }

    @Serializable
    @SerialName("mul")
    data class Mul(
        val inner: ExciterDsl,
        val factor: ExciterDsl = Param("factor", 1.0, "Scale factor."),
    ) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); factor.collectParams(out)
        }
    }

    @Serializable
    @SerialName("div")
    data class Div(
        val inner: ExciterDsl,
        val divisor: ExciterDsl = Param("divisor", 1.0, "Divisor."),
    ) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); divisor.collectParams(out)
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Frequency Modifiers
    // ═════════════════════════════════════════════════════════════════════════════

    @Serializable
    @SerialName("detune")
    data class Detune(
        val inner: ExciterDsl,
        val semitones: ExciterDsl = Param("semitones", 0.0, "Pitch shift in semitones."),
    ) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); semitones.collectParams(out)
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Filters
    // ═════════════════════════════════════════════════════════════════════════════

    @Serializable
    @SerialName("lowpass")
    data class Lowpass(
        val inner: ExciterDsl,
        val cutoffHz: ExciterDsl = Param("cutoffHz", 2000.0, "Filter cutoff frequency in Hz."),
        val q: ExciterDsl = Param("q", 0.707, "Filter resonance (Q factor)."),
    ) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); cutoffHz.collectParams(out); q.collectParams(out)
        }
    }

    @Serializable
    @SerialName("highpass")
    data class Highpass(
        val inner: ExciterDsl,
        val cutoffHz: ExciterDsl = Param("cutoffHz", 200.0, "Filter cutoff frequency in Hz."),
        val q: ExciterDsl = Param("q", 0.707, "Filter resonance (Q factor)."),
    ) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); cutoffHz.collectParams(out); q.collectParams(out)
        }
    }

    @Serializable
    @SerialName("one-pole-lowpass")
    data class OnePoleLowpass(
        val inner: ExciterDsl,
        val cutoffHz: ExciterDsl = Param("cutoffHz", 2000.0, "Filter cutoff frequency in Hz."),
    ) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); cutoffHz.collectParams(out)
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Envelope
    // ═════════════════════════════════════════════════════════════════════════════

    @Serializable
    @SerialName("adsr")
    data class Adsr(
        val inner: ExciterDsl,
        val attackSec: ExciterDsl = Param("attackSec", 0.01, "Attack time in seconds."),
        val decaySec: ExciterDsl = Param("decaySec", 0.1, "Decay time in seconds."),
        val sustainLevel: ExciterDsl = Param("sustainLevel", 0.7, "Sustain level 0.0..1.0."),
        val releaseSec: ExciterDsl = Param("releaseSec", 0.3, "Release time in seconds."),
    ) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); attackSec.collectParams(out); decaySec.collectParams(out)
            sustainLevel.collectParams(out); releaseSec.collectParams(out)
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // FM Synthesis
    // ═════════════════════════════════════════════════════════════════════════════

    @Serializable
    @SerialName("fm")
    data class Fm(
        val carrier: ExciterDsl,
        val modulator: ExciterDsl,
        val ratio: ExciterDsl = Param("ratio", 1.0, "Frequency ratio between modulator and carrier."),
        val depth: ExciterDsl = Param("depth", 0.0, "Modulation depth in Hz."),
        val envAttackSec: ExciterDsl = Param("envAttackSec", 0.0, "FM envelope attack time in seconds."),
        val envDecaySec: ExciterDsl = Param("envDecaySec", 0.0, "FM envelope decay time in seconds."),
        val envSustainLevel: ExciterDsl = Param("envSustainLevel", 1.0, "FM envelope sustain level."),
        val envReleaseSec: ExciterDsl = Param("envReleaseSec", 0.0, "FM envelope release time in seconds."),
    ) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {
            carrier.collectParams(out); modulator.collectParams(out); ratio.collectParams(out); depth.collectParams(out)
            envAttackSec.collectParams(out); envDecaySec.collectParams(out); envSustainLevel.collectParams(out); envReleaseSec.collectParams(
                out
            )
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Effects
    // ═════════════════════════════════════════════════════════════════════════════

    @Serializable
    @SerialName("distort")
    data class Distort(
        val inner: ExciterDsl,
        val amount: ExciterDsl = Param("amount", 0.5, "Distortion drive amount."),
        val shape: String = "soft",
    ) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); amount.collectParams(out)
        }
    }

    @Serializable
    @SerialName("crush")
    data class Crush(
        val inner: ExciterDsl,
        val amount: ExciterDsl = Param("amount", 8.0, "Bit depth for quantization."),
    ) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); amount.collectParams(out)
        }
    }

    @Serializable
    @SerialName("coarse")
    data class Coarse(
        val inner: ExciterDsl,
        val amount: ExciterDsl = Param("amount", 4.0, "Sample rate reduction factor."),
    ) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); amount.collectParams(out)
        }
    }

    @Serializable
    @SerialName("phaser")
    data class Phaser(
        val inner: ExciterDsl,
        val rate: ExciterDsl = Param("rate", 0.5, "LFO rate in Hz."),
        val depth: ExciterDsl = Param("depth", 0.5, "Wet/dry mix depth."),
        val center: ExciterDsl = Param("center", 1000.0, "Center frequency in Hz."),
        val sweep: ExciterDsl = Param("sweep", 1000.0, "Frequency sweep range in Hz."),
    ) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); rate.collectParams(out); depth.collectParams(out)
            center.collectParams(out); sweep.collectParams(out)
        }
    }

    @Serializable
    @SerialName("tremolo")
    data class Tremolo(
        val inner: ExciterDsl,
        val rate: ExciterDsl = Param("rate", 5.0, "LFO rate in Hz."),
        val depth: ExciterDsl = Param("depth", 0.5, "Modulation depth 0.0..1.0."),
    ) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); rate.collectParams(out); depth.collectParams(out)
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Pitch Modulation
    // ═════════════════════════════════════════════════════════════════════════════

    @Serializable
    @SerialName("vibrato")
    data class Vibrato(
        val inner: ExciterDsl,
        val rate: ExciterDsl = Param("rate", 5.0, "LFO rate in Hz."),
        val depth: ExciterDsl = Param("depth", 0.02, "Frequency deviation (e.g. 0.02 = ±2%)."),
    ) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); rate.collectParams(out); depth.collectParams(out)
        }
    }

    @Serializable
    @SerialName("accelerate")
    data class Accelerate(
        val inner: ExciterDsl,
        val amount: ExciterDsl = Param("amount", 0.0, "Pitch change exponent over voice duration."),
    ) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); amount.collectParams(out)
        }
    }

    @Serializable
    @SerialName("pitch-envelope")
    data class PitchEnvelope(
        val inner: ExciterDsl,
        val amount: ExciterDsl = Param("amount", 0.0, "Semitones of pitch shift at peak."),
        val attackSec: ExciterDsl = Param("attackSec", 0.01, "Attack time in seconds."),
        val decaySec: ExciterDsl = Param("decaySec", 0.1, "Decay time in seconds."),
        val releaseSec: ExciterDsl = Param("releaseSec", 0.0, "Release time in seconds."),
        val curve: ExciterDsl = Param("curve", 0.0, "Envelope curve shape."),
        val anchor: ExciterDsl = Param("anchor", 0.0, "Starting envelope level. 0=start shifted, 1=start normal."),
    ) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); amount.collectParams(out); attackSec.collectParams(out)
            decaySec.collectParams(out); releaseSec.collectParams(out); curve.collectParams(out); anchor.collectParams(out)
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════════
// Builder Extensions
// ═════════════════════════════════════════════════════════════════════════════════

// Arithmetic
operator fun ExciterDsl.plus(other: ExciterDsl) = ExciterDsl.Plus(this, other)
operator fun ExciterDsl.times(other: ExciterDsl) = ExciterDsl.Times(this, other)
fun ExciterDsl.mul(factor: Double) = ExciterDsl.Mul(this, ExciterDsl.Param("factor", factor, "Scale factor."))
fun ExciterDsl.div(divisor: Double) = ExciterDsl.Div(this, ExciterDsl.Param("divisor", divisor, "Divisor."))

// Frequency
fun ExciterDsl.detune(semitones: Double) = ExciterDsl.Detune(this, ExciterDsl.Param("semitones", semitones, "Pitch shift in semitones."))
fun ExciterDsl.octaveUp() = detune(12.0)
fun ExciterDsl.octaveDown() = detune(-12.0)

// Filters
fun ExciterDsl.lowpass(cutoffHz: Double, q: Double = 0.707) = ExciterDsl.Lowpass(
    this,
    ExciterDsl.Param("cutoffHz", cutoffHz, "Filter cutoff frequency in Hz."),
    ExciterDsl.Param("q", q, "Filter resonance (Q factor)."),
)

fun ExciterDsl.highpass(cutoffHz: Double, q: Double = 0.707) = ExciterDsl.Highpass(
    this,
    ExciterDsl.Param("cutoffHz", cutoffHz, "Filter cutoff frequency in Hz."),
    ExciterDsl.Param("q", q, "Filter resonance (Q factor)."),
)

fun ExciterDsl.onePoleLowpass(cutoffHz: Double) = ExciterDsl.OnePoleLowpass(
    this,
    ExciterDsl.Param("cutoffHz", cutoffHz, "Filter cutoff frequency in Hz."),
)

// Envelope
fun ExciterDsl.adsr(attackSec: Double, decaySec: Double, sustainLevel: Double, releaseSec: Double) =
    ExciterDsl.Adsr(
        this,
        ExciterDsl.Param("attackSec", attackSec, "Attack time in seconds."),
        ExciterDsl.Param("decaySec", decaySec, "Decay time in seconds."),
        ExciterDsl.Param("sustainLevel", sustainLevel, "Sustain level 0.0..1.0."),
        ExciterDsl.Param("releaseSec", releaseSec, "Release time in seconds."),
    )

// FM
fun ExciterDsl.fm(
    modulator: ExciterDsl,
    ratio: Double,
    depth: Double,
    envAttackSec: Double = 0.0,
    envDecaySec: Double = 0.0,
    envSustainLevel: Double = 1.0,
    envReleaseSec: Double = 0.0,
) = ExciterDsl.Fm(
    carrier = this,
    modulator = modulator,
    ratio = ExciterDsl.Param("ratio", ratio, "Frequency ratio between modulator and carrier."),
    depth = ExciterDsl.Param("depth", depth, "Modulation depth in Hz."),
    envAttackSec = ExciterDsl.Param("envAttackSec", envAttackSec, "FM envelope attack time in seconds."),
    envDecaySec = ExciterDsl.Param("envDecaySec", envDecaySec, "FM envelope decay time in seconds."),
    envSustainLevel = ExciterDsl.Param("envSustainLevel", envSustainLevel, "FM envelope sustain level."),
    envReleaseSec = ExciterDsl.Param("envReleaseSec", envReleaseSec, "FM envelope release time in seconds."),
)

// Effects
fun ExciterDsl.distort(amount: Double, shape: String = "soft") =
    ExciterDsl.Distort(this, ExciterDsl.Param("amount", amount, "Distortion drive amount."), shape)

fun ExciterDsl.crush(amount: Double) = ExciterDsl.Crush(this, ExciterDsl.Param("amount", amount, "Bit depth for quantization."))
fun ExciterDsl.coarse(amount: Double) = ExciterDsl.Coarse(this, ExciterDsl.Param("amount", amount, "Sample rate reduction factor."))
fun ExciterDsl.phaser(rate: Double, depth: Double, center: Double = 1000.0, sweep: Double = 1000.0) = ExciterDsl.Phaser(
    this,
    ExciterDsl.Param("rate", rate, "LFO rate in Hz."),
    ExciterDsl.Param("depth", depth, "Wet/dry mix depth."),
    ExciterDsl.Param("center", center, "Center frequency in Hz."),
    ExciterDsl.Param("sweep", sweep, "Frequency sweep range in Hz."),
)

fun ExciterDsl.tremolo(rate: Double, depth: Double) = ExciterDsl.Tremolo(
    this,
    ExciterDsl.Param("rate", rate, "LFO rate in Hz."),
    ExciterDsl.Param("depth", depth, "Modulation depth 0.0..1.0."),
)

// Pitch modulation
fun ExciterDsl.vibrato(rate: Double, depth: Double) = ExciterDsl.Vibrato(
    this,
    ExciterDsl.Param("rate", rate, "LFO rate in Hz."),
    ExciterDsl.Param("depth", depth, "Frequency deviation."),
)

fun ExciterDsl.accelerate(amount: Double) =
    ExciterDsl.Accelerate(this, ExciterDsl.Param("amount", amount, "Pitch change exponent over voice duration."))

// ═════════════════════════════════════════════════════════════════════════════════
// Discovery
// ═════════════════════════════════════════════════════════════════════════════════

/** Walks the DSL tree and collects all [ExciterDsl.Param] leaf nodes. */
fun ExciterDsl.getParamSlots(): List<ExciterDsl.Param> {
    val result = mutableListOf<ExciterDsl.Param>()
    collectParams(result)
    return result
}
