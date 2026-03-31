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
        override fun collectParams(out: MutableList<Param>) {
            out.add(this)
        }
    }

    /**
     * A fixed constant value that cannot be overridden by oscParams.
     *
     * Use when the user explicitly sets a value (e.g. `Osc.sine(5)` = 5 Hz).
     * Unlike [Param], this is not discoverable and not overridable at play time.
     */
    @Serializable
    @SerialName("const")
    data class Constant(
        val value: Double,
    ) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {}
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Oscillator Primitives
    //
    // freq convention: 0 = use the note's frequency from the voice (e.g. 440 Hz for A4).
    // Non-zero = fixed frequency in Hz (e.g. 5.0 for a 5 Hz LFO).
    // ═════════════════════════════════════════════════════════════════════════════

    /** Sine wave oscillator. @param freq 0 = voice frequency, non-zero = fixed Hz. */
    @Serializable
    @SerialName("sine")
    data class Sine(
        val freq: ExciterDsl = Constant(0.0),
        val analog: ExciterDsl = Constant(0.0),
    ) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); analog.collectParams(out)
        }
    }

    /** Sawtooth wave oscillator (rising ramp). */
    @Serializable
    @SerialName("sawtooth")
    data class Sawtooth(
        val freq: ExciterDsl = Constant(0.0),
        val analog: ExciterDsl = Constant(0.0),
    ) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); analog.collectParams(out)
        }
    }

    /** Square wave oscillator. */
    @Serializable
    @SerialName("square")
    data class Square(
        val freq: ExciterDsl = Constant(0.0),
        val analog: ExciterDsl = Constant(0.0),
    ) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); analog.collectParams(out)
        }
    }

    /** Triangle wave oscillator. */
    @Serializable
    @SerialName("triangle")
    data class Triangle(
        val freq: ExciterDsl = Constant(0.0),
        val analog: ExciterDsl = Constant(0.0),
    ) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); analog.collectParams(out)
        }
    }

    /** White noise generator. Produces uniformly distributed random samples. */
    @Serializable
    @SerialName("white-noise")
    data object WhiteNoise : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {}
    }

    /** Zawtooth wave oscillator. Naive sawtooth without PolyBLEP anti-aliasing (brighter/harsher). */
    @Serializable
    @SerialName("zawtooth")
    data class Zawtooth(
        val freq: ExciterDsl = Constant(0.0),
        val analog: ExciterDsl = Constant(0.0),
    ) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); analog.collectParams(out)
        }
    }

    /** Impulse oscillator. Emits a single-sample spike per cycle. */
    @Serializable
    @SerialName("impulse")
    data class Impulse(
        val freq: ExciterDsl = Constant(0.0),
        val analog: ExciterDsl = Constant(0.0),
    ) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); analog.collectParams(out)
        }
    }

    /** Pulse wave oscillator with variable duty cycle. */
    @Serializable
    @SerialName("pulze")
    data class Pulze(
        val freq: ExciterDsl = Constant(0.0),
        val duty: ExciterDsl = Constant(0.5),
        val analog: ExciterDsl = Constant(0.0),
    ) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); duty.collectParams(out); analog.collectParams(out)
        }
    }

    /** Brown (Brownian/red) noise generator. Random-walk filtered noise with -6 dB/oct slope. */
    @Serializable
    @SerialName("brown-noise")
    data object BrownNoise : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {}
    }

    /** Pink noise generator. Equal energy per octave with -3 dB/oct slope. */
    @Serializable
    @SerialName("pink-noise")
    data object PinkNoise : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {}
    }

    /** Perlin noise generator. Smooth, continuous random signal useful for organic modulation. */
    @Serializable
    @SerialName("perlin-noise")
    data class PerlinNoise(
        val rate: ExciterDsl = Constant(1.0),
    ) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {
            rate.collectParams(out)
        }
    }

    /** Berlin noise generator. Bipolar variant of Perlin noise (output ranges -1..+1). */
    @Serializable
    @SerialName("berlin-noise")
    data class BerlinNoise(
        val rate: ExciterDsl = Constant(1.0),
    ) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {
            rate.collectParams(out)
        }
    }

    /** Dust noise generator. Emits sparse random impulses at a controllable density. */
    @Serializable
    @SerialName("dust")
    data class Dust(
        val density: ExciterDsl = Constant(0.2),
    ) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {
            density.collectParams(out)
        }
    }

    /** Crackle noise generator. Similar to [Dust] but with bipolar impulses for a crackle texture. */
    @Serializable
    @SerialName("crackle")
    data class Crackle(
        val density: ExciterDsl = Constant(0.2),
    ) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {
            density.collectParams(out)
        }
    }

    /** Ramp oscillator. Reverse sawtooth (ramp up, opposite slope of [Sawtooth]). */
    @Serializable
    @SerialName("ramp")
    data class Ramp(
        val freq: ExciterDsl = Constant(0.0),
        val analog: ExciterDsl = Constant(0.0),
    ) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); analog.collectParams(out)
        }
    }

    /** Unison supersaw oscillator. Stacks multiple detuned sawtooth voices for a thick sound. */
    @Serializable
    @SerialName("supersaw")
    data class SuperSaw(
        val freq: ExciterDsl = Constant(0.0),
        val voices: ExciterDsl = Constant(8.0),
        val freqSpread: ExciterDsl = Constant(0.2),
        val analog: ExciterDsl = Constant(0.0),
    ) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); voices.collectParams(out); freqSpread.collectParams(out); analog.collectParams(out)
        }
    }

    /** Unison supersine oscillator. Stacks multiple detuned sine voices. */
    @Serializable
    @SerialName("supersine")
    data class SuperSine(
        val freq: ExciterDsl = Constant(0.0),
        val voices: ExciterDsl = Constant(8.0),
        val freqSpread: ExciterDsl = Constant(0.2),
        val analog: ExciterDsl = Constant(0.0),
    ) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); voices.collectParams(out); freqSpread.collectParams(out); analog.collectParams(out)
        }
    }

    /** Unison supersquare oscillator. Stacks multiple detuned square voices. */
    @Serializable
    @SerialName("supersquare")
    data class SuperSquare(
        val freq: ExciterDsl = Constant(0.0),
        val voices: ExciterDsl = Constant(8.0),
        val freqSpread: ExciterDsl = Constant(0.2),
        val analog: ExciterDsl = Constant(0.0),
    ) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); voices.collectParams(out); freqSpread.collectParams(out); analog.collectParams(out)
        }
    }

    /** Unison supertriangle oscillator. Stacks multiple detuned triangle voices. */
    @Serializable
    @SerialName("supertri")
    data class SuperTri(
        val freq: ExciterDsl = Constant(0.0),
        val voices: ExciterDsl = Constant(8.0),
        val freqSpread: ExciterDsl = Constant(0.2),
        val analog: ExciterDsl = Constant(0.0),
    ) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); voices.collectParams(out); freqSpread.collectParams(out); analog.collectParams(out)
        }
    }

    /** Unison superramp oscillator. Stacks multiple detuned ramp voices. */
    @Serializable
    @SerialName("superramp")
    data class SuperRamp(
        val freq: ExciterDsl = Constant(0.0),
        val voices: ExciterDsl = Constant(8.0),
        val freqSpread: ExciterDsl = Constant(0.2),
        val analog: ExciterDsl = Constant(0.0),
    ) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); voices.collectParams(out); freqSpread.collectParams(out); analog.collectParams(out)
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
        val freq: ExciterDsl = Constant(0.0),
        val decay: ExciterDsl = Constant(0.996),
        val brightness: ExciterDsl = Constant(0.5),
        val pickPosition: ExciterDsl = Constant(0.5),
        val stiffness: ExciterDsl = Constant(0.0),
        val analog: ExciterDsl = Constant(0.0),
    ) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); decay.collectParams(out); brightness.collectParams(out); pickPosition.collectParams(out)
            stiffness.collectParams(out); analog.collectParams(out)
        }
    }

    /** Unison superpluck. Stacks multiple detuned Karplus-Strong voices for a chorus-like string sound. */
    @Serializable
    @SerialName("superpluck")
    data class SuperPluck(
        val freq: ExciterDsl = Constant(0.0),
        val voices: ExciterDsl = Constant(8.0),
        val freqSpread: ExciterDsl = Constant(0.2),
        val decay: ExciterDsl = Constant(0.996),
        val brightness: ExciterDsl = Constant(0.5),
        val pickPosition: ExciterDsl = Constant(0.5),
        val stiffness: ExciterDsl = Constant(0.0),
        val analog: ExciterDsl = Constant(0.0),
    ) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); voices.collectParams(out); freqSpread.collectParams(out); decay.collectParams(out); brightness.collectParams(
                out
            )
            pickPosition.collectParams(out); stiffness.collectParams(out); analog.collectParams(out)
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

    /** Scales the left signal by the right signal (per-sample multiplication). */
    @Serializable
    @SerialName("mul")
    data class Mul(
        val left: ExciterDsl,
        val right: ExciterDsl = Constant(1.0),
    ) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {
            left.collectParams(out); right.collectParams(out)
        }
    }

    /** Divides the left signal by the right signal (per-sample division). */
    @Serializable
    @SerialName("div")
    data class Div(
        val left: ExciterDsl,
        val right: ExciterDsl = Constant(1.0),
    ) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {
            left.collectParams(out); right.collectParams(out)
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
        val semitones: ExciterDsl = Constant(0.0),
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
        val cutoffHz: ExciterDsl = Constant(2000.0),
        val q: ExciterDsl = Constant(0.707),
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
        val cutoffHz: ExciterDsl = Constant(200.0),
        val q: ExciterDsl = Constant(0.707),
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
        val cutoffHz: ExciterDsl = Constant(2000.0),
    ) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); cutoffHz.collectParams(out)
        }
    }

    /** SVF bandpass filter. Passes frequencies near the cutoff, attenuates others. */
    @Serializable
    @SerialName("bandpass")
    data class Bandpass(
        val inner: ExciterDsl,
        val cutoffHz: ExciterDsl = Constant(1000.0),
        val q: ExciterDsl = Constant(1.0),
    ) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); cutoffHz.collectParams(out); q.collectParams(out)
        }
    }

    /** SVF notch (band-reject) filter. Removes frequencies near the cutoff, passes others. */
    @Serializable
    @SerialName("notch")
    data class Notch(
        val inner: ExciterDsl,
        val cutoffHz: ExciterDsl = Constant(1000.0),
        val q: ExciterDsl = Constant(1.0),
    ) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); cutoffHz.collectParams(out); q.collectParams(out)
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
        val attackSec: ExciterDsl = Constant(0.01),
        val decaySec: ExciterDsl = Constant(0.1),
        val sustainLevel: ExciterDsl = Constant(0.7),
        val releaseSec: ExciterDsl = Constant(0.3),
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
        val ratio: ExciterDsl = Constant(1.0),
        val depth: ExciterDsl = Constant(0.0),
        val envAttackSec: ExciterDsl = Constant(0.0),
        val envDecaySec: ExciterDsl = Constant(0.0),
        val envSustainLevel: ExciterDsl = Constant(1.0),
        val envReleaseSec: ExciterDsl = Constant(0.0),
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

    /**
     * Pre-amplification stage. Boosts signal level before clipping.
     * Types: "linear" (current gain curve).
     */
    @Serializable
    @SerialName("drive")
    data class Drive(
        val inner: ExciterDsl,
        val amount: ExciterDsl = Constant(0.5),
        val driveType: String = "linear",
    ) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); amount.collectParams(out)
        }
    }

    /**
     * Pure waveshaping without drive. Applies a nonlinear transfer function per sample.
     * Includes DC blocker for asymmetric shapes.
     * Shapes: "soft" (tanh), "hard", "gentle", "cubic", "diode", "fold", "chebyshev", "rectify", "exp".
     */
    @Serializable
    @SerialName("clip")
    data class Clip(
        val inner: ExciterDsl,
        val shape: String = "soft",
    ) : ExciterDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out)
        }
    }

    /**
     * Legacy distortion node. Kept for backward compatibility with serialized trees.
     * New code should use [Drive] + [Clip] instead. The builder extension [ExciterDsl.distort]
     * creates a Clip(Drive(...)) chain.
     */
    @Serializable
    @SerialName("distort")
    data class Distort(
        val inner: ExciterDsl,
        val amount: ExciterDsl = Constant(0.5),
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
        val amount: ExciterDsl = Constant(8.0),
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
        val amount: ExciterDsl = Constant(4.0),
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
        val rate: ExciterDsl = Constant(0.5),
        val depth: ExciterDsl = Constant(0.5),
        val center: ExciterDsl = Constant(1000.0),
        val sweep: ExciterDsl = Constant(1000.0),
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
        val rate: ExciterDsl = Constant(5.0),
        val depth: ExciterDsl = Constant(0.5),
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
        val rate: ExciterDsl = Constant(5.0),
        val depth: ExciterDsl = Constant(0.02),
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
        val amount: ExciterDsl = Constant(0.0),
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
        val amount: ExciterDsl = Constant(0.0),
        val attackSec: ExciterDsl = Constant(0.01),
        val decaySec: ExciterDsl = Constant(0.1),
        val releaseSec: ExciterDsl = Constant(0.0),
        val curve: ExciterDsl = Constant(0.0),
        val anchor: ExciterDsl = Constant(0.0),
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
fun ExciterDsl.mul(other: ExciterDsl) = ExciterDsl.Mul(left = this, right = other)

/** Divides this signal by a modulatable [other] divisor. */
fun ExciterDsl.div(other: ExciterDsl) = ExciterDsl.Div(left = this, right = other)

// Frequency

/** Shifts pitch by the given number of [semitones]. */
fun ExciterDsl.detune(semitones: Double) = ExciterDsl.Detune(
    inner = this,
    semitones = ExciterDsl.Constant(semitones),
)

// Filters

/** Applies a biquad lowpass filter at [cutoffHz] with resonance [q]. */
fun ExciterDsl.lowpass(cutoffHz: Double, q: Double = 0.707) = ExciterDsl.Lowpass(
    inner = this,
    cutoffHz = ExciterDsl.Constant(cutoffHz),
    q = ExciterDsl.Constant(q),
)

/** Applies a biquad highpass filter at [cutoffHz] with resonance [q]. */
fun ExciterDsl.highpass(cutoffHz: Double, q: Double = 0.707) = ExciterDsl.Highpass(
    inner = this,
    cutoffHz = ExciterDsl.Constant(cutoffHz),
    q = ExciterDsl.Constant(q),
)

/** Applies a lightweight one-pole lowpass filter at [cutoffHz]. */
fun ExciterDsl.onePoleLowpass(cutoffHz: Double) = ExciterDsl.OnePoleLowpass(
    inner = this,
    cutoffHz = ExciterDsl.Constant(cutoffHz),
)

fun ExciterDsl.bandpass(cutoffHz: Double, q: Double = 1.0) = ExciterDsl.Bandpass(
    this, ExciterDsl.Constant(cutoffHz), ExciterDsl.Constant(q),
)

fun ExciterDsl.notch(cutoffHz: Double, q: Double = 1.0) = ExciterDsl.Notch(
    this, ExciterDsl.Constant(cutoffHz), ExciterDsl.Constant(q),
)

fun ExciterDsl.drive(amount: Double, driveType: String = "linear") =
    ExciterDsl.Drive(this, ExciterDsl.Constant(amount), driveType)

fun ExciterDsl.clip(shape: String = "soft") = ExciterDsl.Clip(this, shape)

// Envelope

/** Wraps this signal in an ADSR amplitude envelope. */
fun ExciterDsl.adsr(attackSec: Double, decaySec: Double, sustainLevel: Double, releaseSec: Double) = ExciterDsl.Adsr(
    inner = this,
    attackSec = ExciterDsl.Constant(attackSec),
    decaySec = ExciterDsl.Constant(decaySec),
    sustainLevel = ExciterDsl.Constant(sustainLevel),
    releaseSec = ExciterDsl.Constant(releaseSec),
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
    ratio = ExciterDsl.Constant(ratio),
    depth = ExciterDsl.Constant(depth),
    envAttackSec = ExciterDsl.Constant(envAttackSec),
    envDecaySec = ExciterDsl.Constant(envDecaySec),
    envSustainLevel = ExciterDsl.Constant(envSustainLevel),
    envReleaseSec = ExciterDsl.Constant(envReleaseSec),
)

// Effects

/** Applies waveshaping distortion with the given [amount] and clipping [shape]. */
fun ExciterDsl.distort(amount: Double, shape: String = "soft") =
    ExciterDsl.Clip(inner = ExciterDsl.Drive(inner = this, amount = ExciterDsl.Constant(amount)), shape = shape)

/** Applies bit-crush quantization at the given bit [amount]. */
fun ExciterDsl.crush(amount: Double) = ExciterDsl.Crush(
    inner = this,
    amount = ExciterDsl.Constant(amount),
)

/** Applies sample-rate reduction by the given [amount] factor. */
fun ExciterDsl.coarse(amount: Double) = ExciterDsl.Coarse(
    inner = this,
    amount = ExciterDsl.Constant(amount),
)

/** Applies a phaser effect with the given LFO [rate], [depth], [center] frequency, and [sweep] range. */
fun ExciterDsl.phaser(rate: Double, depth: Double, center: Double = 1000.0, sweep: Double = 1000.0) = ExciterDsl.Phaser(
    inner = this,
    rate = ExciterDsl.Constant(rate),
    depth = ExciterDsl.Constant(depth),
    center = ExciterDsl.Constant(center),
    sweep = ExciterDsl.Constant(sweep),
)

/** Applies a tremolo (amplitude modulation) at the given LFO [rate] and [depth]. */
fun ExciterDsl.tremolo(rate: Double, depth: Double) = ExciterDsl.Tremolo(
    inner = this,
    rate = ExciterDsl.Constant(rate),
    depth = ExciterDsl.Constant(depth),
)

// Pitch modulation

/** Applies vibrato (pitch modulation) at the given LFO [rate] and [depth]. */
fun ExciterDsl.vibrato(rate: Double, depth: Double) = ExciterDsl.Vibrato(
    inner = this,
    rate = ExciterDsl.Constant(rate),
    depth = ExciterDsl.Constant(depth),
)

/** Applies continuous pitch acceleration over the voice's duration. */
fun ExciterDsl.accelerate(amount: Double) = ExciterDsl.Accelerate(
    inner = this,
    amount = ExciterDsl.Constant(amount),
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
