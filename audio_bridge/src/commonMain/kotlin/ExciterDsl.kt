package io.peekandpoke.klang.audio_bridge

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Serializable sealed DSL for describing exciter signal graphs.
 *
 * Each subtype represents a primitive oscillator, noise source, effect, filter, envelope,
 * or arithmetic combinator. Subtrees are composed declaratively and serialized across the
 * audio bridge boundary for rendering in the audio worklet.
 */
@Serializable
sealed interface ExciterDsl {

    /** Whether this exciter requires a frequency to produce sound. Noise sources override to false. */
    val needsFreq: Boolean get() = true

    /** Recursively collects all [Param] leaf nodes in this DSL subtree into [out]. */
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

    /** Sine wave oscillator. */
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

    /** Sawtooth wave oscillator (rising ramp). */
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

    /** Square wave oscillator. */
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

    /** Triangle wave oscillator. */
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

    /** White noise generator. Produces uniformly distributed random samples. */
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

    /** Zawtooth wave oscillator (falling ramp, inverse of sawtooth). */
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

    /** Impulse oscillator. Emits a single-sample spike per cycle. */
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

    /** Pulse wave oscillator with variable duty cycle. */
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

    /** Brown (Brownian/red) noise generator. Random-walk filtered noise with -6 dB/oct slope. */
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

    /** Pink noise generator. Equal energy per octave with -3 dB/oct slope. */
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

    /** Perlin noise generator. Smooth, continuous random signal useful for organic modulation. */
    @Serializable
    @SerialName("perlin-noise")
    data class PerlinNoise(
        val rate: ExciterDsl = Param("rate", 1.0, "Speed of noise evolution. Higher = faster changes."),
        val gain: ExciterDsl = Param("gain", 1.0, "Output amplitude."),
    ) : ExciterDsl {
        override val needsFreq: Boolean get() = false
        override fun collectParams(out: MutableList<Param>) {
            rate.collectParams(out); gain.collectParams(out)
        }
    }

    /** Berlin noise generator. Bipolar variant of Perlin noise (output ranges -1..+1). */
    @Serializable
    @SerialName("berlin-noise")
    data class BerlinNoise(
        val rate: ExciterDsl = Param("rate", 1.0, "Speed of noise evolution. Higher = faster changes."),
        val gain: ExciterDsl = Param("gain", 1.0, "Output amplitude."),
    ) : ExciterDsl {
        override val needsFreq: Boolean get() = false
        override fun collectParams(out: MutableList<Param>) {
            rate.collectParams(out); gain.collectParams(out)
        }
    }

    /** Dust noise generator. Emits sparse random impulses at a controllable density. */
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

    /** Crackle noise generator. Similar to [Dust] but with bipolar impulses for a crackle texture. */
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

    /** Ramp oscillator. Alias for a falling sawtooth shape. */
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

    /** Unison supersaw oscillator. Stacks multiple detuned sawtooth voices for a thick sound. */
    @Serializable
    @SerialName("supersaw")
    data class SuperSaw(
        val voices: ExciterDsl = Param("voices", 8.0, "Number of unison voices."),
        val freqSpread: ExciterDsl = Param("freqSpread", 0.2, "Detune spread in semitones between voices."),
        val gain: ExciterDsl = Param("gain", 0.6, "Output amplitude."),
        val analog: ExciterDsl = Param("analog", 0.0, "Perlin noise pitch drift amount."),
    ) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {
            voices.collectParams(out); freqSpread.collectParams(out); gain.collectParams(out); analog.collectParams(out)
        }
    }

    /** Unison supersine oscillator. Stacks multiple detuned sine voices. */
    @Serializable
    @SerialName("supersine")
    data class SuperSine(
        val voices: ExciterDsl = Param("voices", 8.0, "Number of unison voices."),
        val freqSpread: ExciterDsl = Param("freqSpread", 0.2, "Detune spread in semitones between voices."),
        val gain: ExciterDsl = Param("gain", 1.0, "Output amplitude."),
        val analog: ExciterDsl = Param("analog", 0.0, "Perlin noise pitch drift amount."),
    ) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {
            voices.collectParams(out); freqSpread.collectParams(out); gain.collectParams(out); analog.collectParams(out)
        }
    }

    /** Unison supersquare oscillator. Stacks multiple detuned square voices. */
    @Serializable
    @SerialName("supersquare")
    data class SuperSquare(
        val voices: ExciterDsl = Param("voices", 8.0, "Number of unison voices."),
        val freqSpread: ExciterDsl = Param("freqSpread", 0.2, "Detune spread in semitones between voices."),
        val gain: ExciterDsl = Param("gain", 0.5, "Output amplitude."),
        val analog: ExciterDsl = Param("analog", 0.0, "Perlin noise pitch drift amount."),
    ) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {
            voices.collectParams(out); freqSpread.collectParams(out); gain.collectParams(out); analog.collectParams(out)
        }
    }

    /** Unison supertriangle oscillator. Stacks multiple detuned triangle voices. */
    @Serializable
    @SerialName("supertri")
    data class SuperTri(
        val voices: ExciterDsl = Param("voices", 8.0, "Number of unison voices."),
        val freqSpread: ExciterDsl = Param("freqSpread", 0.2, "Detune spread in semitones between voices."),
        val gain: ExciterDsl = Param("gain", 0.7, "Output amplitude."),
        val analog: ExciterDsl = Param("analog", 0.0, "Perlin noise pitch drift amount."),
    ) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {
            voices.collectParams(out); freqSpread.collectParams(out); gain.collectParams(out); analog.collectParams(out)
        }
    }

    /** Unison superramp oscillator. Stacks multiple detuned ramp voices. */
    @Serializable
    @SerialName("superramp")
    data class SuperRamp(
        val voices: ExciterDsl = Param("voices", 8.0, "Number of unison voices."),
        val freqSpread: ExciterDsl = Param("freqSpread", 0.2, "Detune spread in semitones between voices."),
        val gain: ExciterDsl = Param("gain", 0.6, "Output amplitude."),
        val analog: ExciterDsl = Param("analog", 0.0, "Perlin noise pitch drift amount."),
    ) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {
            voices.collectParams(out); freqSpread.collectParams(out); gain.collectParams(out); analog.collectParams(out)
        }
    }

    /** Silent exciter. Outputs a zero-filled buffer. */
    @Serializable
    @SerialName("silence")
    data object Silence : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {}
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Physical Models
    // ═════════════════════════════════════════════════════════════════════════════

    /** Karplus-Strong plucked string physical model. */
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

    /** Unison superpluck. Stacks multiple detuned Karplus-Strong voices for a chorus-like string sound. */
    @Serializable
    @SerialName("superpluck")
    data class SuperPluck(
        val voices: ExciterDsl = Param("voices", 8.0, "Number of unison voices."),
        val freqSpread: ExciterDsl = Param("freqSpread", 0.2, "Detune spread in semitones between voices."),
        val decay: ExciterDsl = Param("decay", 0.996, "Feedback decay factor. Lower = shorter sustain."),
        val brightness: ExciterDsl = Param("brightness", 0.5, "Lowpass cutoff in feedback loop. 0=dark, 1=bright."),
        val pickPosition: ExciterDsl = Param("pickPosition", 0.5, "Pluck position along string. Affects harmonic content."),
        val stiffness: ExciterDsl = Param("stiffness", 0.0, "Higher harmonics decay faster. 0=nylon, 1=piano wire."),
        val gain: ExciterDsl = Param("gain", 0.7, "Output amplitude."),
        val analog: ExciterDsl = Param("analog", 0.0, "Perlin noise pitch drift amount."),
    ) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {
            voices.collectParams(out); freqSpread.collectParams(out); decay.collectParams(out); brightness.collectParams(out)
            pickPosition.collectParams(out); stiffness.collectParams(out); gain.collectParams(out); analog.collectParams(out)
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Arithmetic Composition
    // ═════════════════════════════════════════════════════════════════════════════

    /** Additive combinator. Sums two exciter signals sample-by-sample. */
    @Serializable
    @SerialName("plus")
    data class Plus(val left: ExciterDsl, val right: ExciterDsl) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {
            left.collectParams(out); right.collectParams(out)
        }
    }

    /** Multiplicative combinator. Multiplies two exciter signals sample-by-sample (ring modulation). */
    @Serializable
    @SerialName("times")
    data class Times(val left: ExciterDsl, val right: ExciterDsl) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {
            left.collectParams(out); right.collectParams(out)
        }
    }

    /** Scales the inner signal by a constant or modulatable factor. */
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

    /** Divides the inner signal by a constant or modulatable divisor. */
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

    /** Shifts the pitch of the inner exciter by a number of semitones. */
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

    /** Biquad lowpass filter. Attenuates frequencies above the cutoff. */
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

    /** Biquad highpass filter. Attenuates frequencies below the cutoff. */
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

    /** Simple one-pole lowpass filter. Lightweight with -6 dB/oct rolloff and no resonance. */
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

    /** ADSR amplitude envelope. Shapes the inner signal's volume over the note lifecycle. */
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

    /**
     * Frequency modulation synthesis. The modulator's output shifts the carrier's frequency
     * at audio rate, with an optional ADSR envelope controlling modulation depth over time.
     */
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

    /** Waveshaping distortion effect. Applies a nonlinear transfer function to the signal. */
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

    /** Bit-crush effect. Reduces amplitude resolution to create quantization noise. */
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

    /** Sample-rate reduction effect. Holds samples to create aliasing artifacts. */
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

    /** Phaser effect. Sweeps a series of allpass filters to create notch comb filtering. */
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

    /** Tremolo effect. Modulates amplitude with an LFO for a pulsing volume change. */
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

    /** Vibrato effect. Modulates pitch with an LFO for a wavering frequency change. */
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

    /** Pitch acceleration. Continuously shifts pitch over the voice's duration using an exponential curve. */
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

    /**
     * Pitch envelope. Applies an attack-decay-release envelope to pitch, useful for
     * kick drum sweeps, laser effects, and other transient pitch gestures.
     */
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

/** Adds two exciter signals together (sample-by-sample sum). */
operator fun ExciterDsl.plus(other: ExciterDsl) = ExciterDsl.Plus(left = this, right = other)

/** Multiplies two exciter signals together (ring modulation). */
operator fun ExciterDsl.times(other: ExciterDsl) = ExciterDsl.Times(left = this, right = other)

/** Scales this signal by a modulatable [other] factor. */
fun ExciterDsl.mul(other: ExciterDsl) = ExciterDsl.Mul(inner = this, factor = other)

/** Divides this signal by a modulatable [other] divisor. */
fun ExciterDsl.div(other: ExciterDsl) = ExciterDsl.Div(inner = this, divisor = other)

// Frequency

/** Shifts pitch by the given number of [semitones]. */
fun ExciterDsl.detune(semitones: Double) = ExciterDsl.Detune(
    inner = this,
    semitones = ExciterDsl.Param(name = "semitones", default = semitones, description = "Pitch shift in semitones.")
)

/** Shifts pitch up by one octave (+12 semitones). */
fun ExciterDsl.octaveUp() = detune(semitones = 12.0)

/** Shifts pitch down by one octave (-12 semitones). */
fun ExciterDsl.octaveDown() = detune(semitones = -12.0)

// Filters

/** Applies a biquad lowpass filter at [cutoffHz] with resonance [q]. */
fun ExciterDsl.lowpass(cutoffHz: Double, q: Double = 0.707) = ExciterDsl.Lowpass(
    inner = this,
    cutoffHz = ExciterDsl.Param(name = "cutoffHz", default = cutoffHz, description = "Filter cutoff frequency in Hz."),
    q = ExciterDsl.Param(name = "q", default = q, description = "Filter resonance (Q factor)."),
)

/** Applies a biquad highpass filter at [cutoffHz] with resonance [q]. */
fun ExciterDsl.highpass(cutoffHz: Double, q: Double = 0.707) = ExciterDsl.Highpass(
    inner = this,
    cutoffHz = ExciterDsl.Param(name = "cutoffHz", default = cutoffHz, description = "Filter cutoff frequency in Hz."),
    q = ExciterDsl.Param(name = "q", default = q, description = "Filter resonance (Q factor)."),
)

/** Applies a lightweight one-pole lowpass filter at [cutoffHz]. */
fun ExciterDsl.onePoleLowpass(cutoffHz: Double) = ExciterDsl.OnePoleLowpass(
    inner = this,
    cutoffHz = ExciterDsl.Param(name = "cutoffHz", default = cutoffHz, description = "Filter cutoff frequency in Hz."),
)

// Envelope

/** Wraps this signal in an ADSR amplitude envelope. */
fun ExciterDsl.adsr(attackSec: Double, decaySec: Double, sustainLevel: Double, releaseSec: Double) = ExciterDsl.Adsr(
    inner = this,
    attackSec = ExciterDsl.Param(name = "attackSec", default = attackSec, description = "Attack time in seconds."),
    decaySec = ExciterDsl.Param(name = "decaySec", default = decaySec, description = "Decay time in seconds."),
    sustainLevel = ExciterDsl.Param(name = "sustainLevel", default = sustainLevel, description = "Sustain level 0.0..1.0."),
    releaseSec = ExciterDsl.Param(name = "releaseSec", default = releaseSec, description = "Release time in seconds."),
)

// FM

/** Applies FM synthesis to this carrier using the given [modulator], [ratio], and [depth]. */
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

/** Applies waveshaping distortion with the given [amount] and clipping [shape]. */
fun ExciterDsl.distort(amount: Double, shape: String = "soft") = ExciterDsl.Distort(
    inner = this,
    amount = ExciterDsl.Param(name = "amount", default = amount, description = "Distortion drive amount."), shape = shape
)

/** Applies bit-crush quantization at the given bit [amount]. */
fun ExciterDsl.crush(amount: Double) = ExciterDsl.Crush(
    inner = this,
    amount = ExciterDsl.Param(name = "amount", default = amount, description = "Bit depth for quantization.")
)

/** Applies sample-rate reduction by the given [amount] factor. */
fun ExciterDsl.coarse(amount: Double) = ExciterDsl.Coarse(
    inner = this,
    amount = ExciterDsl.Param(name = "amount", default = amount, description = "Sample rate reduction factor.")
)

/** Applies a phaser effect with the given LFO [rate], [depth], [center] frequency, and [sweep] range. */
fun ExciterDsl.phaser(rate: Double, depth: Double, center: Double = 1000.0, sweep: Double = 1000.0) = ExciterDsl.Phaser(
    inner = this,
    rate = ExciterDsl.Param(name = "rate", default = rate, description = "LFO rate in Hz."),
    depth = ExciterDsl.Param(name = "depth", default = depth, description = "Wet/dry mix depth."),
    center = ExciterDsl.Param(name = "center", default = center, description = "Center frequency in Hz."),
    sweep = ExciterDsl.Param(name = "sweep", default = sweep, description = "Frequency sweep range in Hz."),
)

/** Applies a tremolo (amplitude modulation) at the given LFO [rate] and [depth]. */
fun ExciterDsl.tremolo(rate: Double, depth: Double) = ExciterDsl.Tremolo(
    inner = this,
    rate = ExciterDsl.Param(name = "rate", default = rate, description = "LFO rate in Hz."),
    depth = ExciterDsl.Param(name = "depth", default = depth, description = "Modulation depth 0.0..1.0."),
)

// Pitch modulation

/** Applies vibrato (pitch modulation) at the given LFO [rate] and [depth]. */
fun ExciterDsl.vibrato(rate: Double, depth: Double) = ExciterDsl.Vibrato(
    inner = this,
    rate = ExciterDsl.Param(name = "rate", default = rate, description = "LFO rate in Hz."),
    depth = ExciterDsl.Param(name = "depth", default = depth, description = "Frequency deviation."),
)

/** Applies continuous pitch acceleration over the voice's duration. */
fun ExciterDsl.accelerate(amount: Double) = ExciterDsl.Accelerate(
    inner = this,
    amount = ExciterDsl.Param(name = "amount", default = amount, description = "Pitch change exponent over voice duration.")
)

// ═════════════════════════════════════════════════════════════════════════════════
// Discovery
// ═════════════════════════════════════════════════════════════════════════════════

/** Walks the DSL tree and collects all [ExciterDsl.Param] leaf nodes. */
fun ExciterDsl.getParamSlots(): List<ExciterDsl.Param> {
    val result = mutableListOf<ExciterDsl.Param>()
    collectParams(result)
    return result
}
