package io.peekandpoke.klang.audio_bridge


/**
 * Process-local counter used to stamp each noise-source DSL instance with a unique
 * [IgnitorDsl.WhiteNoise.uid] / [IgnitorDsl.BrownNoise.uid] / [IgnitorDsl.PinkNoise.uid].
 *
 * Two calls to `Osc.whitenoise()` produce two different DSL objects with different uids —
 * and therefore two independent Ignitors under the identity-caching `toExciter`. Binding to
 * a variable and re-using it keeps a single instance, so `let s = Osc.whitenoise(); s + s`
 * still collapses to one shared stream summed with itself.
 *
 * DSL construction is single-threaded in practice (the audio bridge is built from the UI
 * thread before being serialised to the worklet), so a plain counter is sufficient.
 */
private var noiseUidCounter: Int = 0

internal fun nextNoiseUid(): Int = noiseUidCounter++

/**
 * Serializable sealed DSL for describing exciter signal graphs.
 *
 * Each subtype represents a primitive oscillator, noise source, effect, filter, envelope,
 * or arithmetic combinator. Subtrees are composed declaratively and serialized across the
 * audio bridge boundary for rendering in the audio worklet.
 */
@WireFormat
sealed interface IgnitorDsl {

    /** Recursively collects all [Param] leaf nodes in this DSL subtree into [out]. */
    fun collectParams(out: MutableList<Param>)

    // ═════════════════════════════════════════════════════════════════════════════
    // Parameter Slot
    // ═════════════════════════════════════════════════════════════════════════════

    /**
     * A named parameter slot that produces a constant signal by default.
     *
     * At runtime, fills a buffer with [default]. Can be replaced with any [IgnitorDsl]
     * subtree to modulate the parameter at audio rate.
     *
     * @param name parameter name — used for discovery, UI display, and oscParams override matching
     * @param default constant value when no modulator is wired in
     * @param description human-readable description for UI tooltips and auto-generated docs
     */
    @WireName("param")
    data class Param(
        val name: String,
        val default: Double,
        val description: String = "",
    ) : IgnitorDsl {
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
    @WireName("const")
    data class Constant(
        val value: Double,
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {}
    }

    /**
     * The voice's note frequency (e.g. 440 Hz for A4).
     * Use anywhere a frequency value is needed to track the played note.
     */
    @WireName("freq")
    data object Freq : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {}
    }

    /**
     * Canonical open parameter slots that mirror sprudel's `withOscParam(name)` calls.
     *
     * Use these when defining a custom sound that should respond to sprudel modulation
     * (e.g. `note("c").analog(0.3)`). Each slot is a `Param(name, default)` singleton with
     * the same name + default value that sprudel addons expect.
     *
     * Builtin sounds (`IgnitorDefaults.kt`) wire these in automatically; custom sounds
     * opt in explicitly:
     * ```
     * let mypad = Osc.sine().analog(OscSlot.analog())
     * note("c").sound(mypad)
     * ```
     */
    object Slots {
        val analog: IgnitorDsl = Param(name = "analog", default = 0.0)
        val voices: IgnitorDsl = Param(name = "voices", default = 8.0)
        val freqSpread: IgnitorDsl = Param(name = "freqSpread", default = 0.2)
        val duty: IgnitorDsl = Param(name = "duty", default = 0.5)
        val density: IgnitorDsl = Param(name = "density", default = 0.2)
        val decay: IgnitorDsl = Param(name = "decay", default = 0.996)
        val brightness: IgnitorDsl = Param(name = "brightness", default = 0.5)
        val pickPosition: IgnitorDsl = Param(name = "pickPosition", default = 0.5)
        val stiffness: IgnitorDsl = Param(name = "stiffness", default = 0.0)
        val rate: IgnitorDsl = Param(name = "rate", default = 1.0)
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Oscillator Primitives
    //
    // freq default: Freq = use the voice's note frequency (e.g. 440 Hz for A4).
    // Constant(n) = fixed frequency in Hz (e.g. Constant(5.0) for a 5 Hz LFO).
    // ═════════════════════════════════════════════════════════════════════════════

    /** Sine wave oscillator. */
    @WireName("sine")
    data class Sine(
        val freq: IgnitorDsl = Freq,
        val analog: IgnitorDsl = Constant(0.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); analog.collectParams(out)
        }
    }

    /** Sawtooth wave oscillator (rising ramp). */
    @WireName("sawtooth")
    data class Sawtooth(
        val freq: IgnitorDsl = Freq,
        val analog: IgnitorDsl = Constant(0.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); analog.collectParams(out)
        }
    }

    /**
     * Square wave oscillator (fixed 50% duty). NOTE: the `square` / `sqr` / `pulse` *sound names* are
     * registered to [Pulze] (one pulse oscillator with a `duty` osc-param); this type is retained as a
     * plain 50%-duty pulse used internally (presets, generic test fixtures).
     */
    @WireName("square")
    data class Square(
        val freq: IgnitorDsl = Freq,
        val analog: IgnitorDsl = Constant(0.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); analog.collectParams(out)
        }
    }

    /** Triangle wave oscillator. */
    @WireName("triangle")
    data class Triangle(
        val freq: IgnitorDsl = Freq,
        val analog: IgnitorDsl = Constant(0.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); analog.collectParams(out)
        }
    }

    /**
     * White noise generator. Produces uniformly distributed random samples.
     *
     * [uid] is an instance-discriminator used by the runtime identity cache: each call to
     * `Osc.whitenoise()` creates a fresh [WhiteNoise] with a unique uid, so two such calls
     * yield two independent noise streams when composed (`a + b`). Binding to a variable and
     * re-using it (`let s = Osc.whitenoise(); s + s`) keeps a single instance and sums the
     * same stream twice.
     */
    @WireName("white-noise")
    data class WhiteNoise(val uid: Int = nextNoiseUid()) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {}
    }

    /** Zawtooth wave oscillator. Naive sawtooth without PolyBLEP anti-aliasing (brighter/harsher). */
    @WireName("zawtooth")
    data class Zawtooth(
        val freq: IgnitorDsl = Freq,
        val analog: IgnitorDsl = Constant(0.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); analog.collectParams(out)
        }
    }

    /** Zamp wave oscillator ("zamp"). Naive reverse sawtooth without anti-aliasing — the raw [Ramp]. */
    @WireName("zamp")
    data class Zamp(
        val freq: IgnitorDsl = Freq,
        val analog: IgnitorDsl = Constant(0.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); analog.collectParams(out)
        }
    }

    /** Impulse oscillator. Emits a single-sample spike per cycle. */
    @WireName("impulse")
    data class Impulse(
        val freq: IgnitorDsl = Freq,
        val analog: IgnitorDsl = Constant(0.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); analog.collectParams(out)
        }
    }

    /** Pulse wave oscillator with variable duty cycle. */
    @WireName("pulze")
    data class Pulze(
        val freq: IgnitorDsl = Freq,
        val duty: IgnitorDsl = Constant(0.5),
        val analog: IgnitorDsl = Constant(0.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); duty.collectParams(out); analog.collectParams(out)
        }
    }

    /**
     * Raw pulse oscillator — naive (no anti-aliasing), the raw counterpart of the rounded pulse
     * (sound names `square`/`pulse`, which use [Pulze]). This type backs the `pulze` sound name.
     */
    @WireName("raw-pulze")
    data class RawPulze(
        val freq: IgnitorDsl = Freq,
        val duty: IgnitorDsl = Constant(0.5),
        val analog: IgnitorDsl = Constant(0.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); duty.collectParams(out); analog.collectParams(out)
        }
    }

    /**
     * Brown (Brownian/red) noise generator. Random-walk filtered noise with -6 dB/oct slope.
     * See [WhiteNoise] for the [uid] instance-discriminator story.
     */
    @WireName("brown-noise")
    data class BrownNoise(val uid: Int = nextNoiseUid()) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {}
    }

    /**
     * Pink noise generator. Equal energy per octave with -3 dB/oct slope.
     * See [WhiteNoise] for the [uid] instance-discriminator story.
     */
    @WireName("pink-noise")
    data class PinkNoise(val uid: Int = nextNoiseUid()) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {}
    }

    /** Perlin noise generator. Smooth, continuous random signal useful for organic modulation. */
    @WireName("perlin-noise")
    data class PerlinNoise(
        val rate: IgnitorDsl = Constant(1.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            rate.collectParams(out)
        }
    }

    /** Berlin noise generator. Bipolar variant of Perlin noise (output ranges -1..+1). */
    @WireName("berlin-noise")
    data class BerlinNoise(
        val rate: IgnitorDsl = Constant(1.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            rate.collectParams(out)
        }
    }

    /** Dust noise generator. Emits sparse random impulses at a controllable density. */
    @WireName("dust")
    data class Dust(
        val density: IgnitorDsl = Constant(0.2),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            density.collectParams(out)
        }
    }

    /** Crackle noise generator. Similar to [Dust] but with bipolar impulses for a crackle texture. */
    @WireName("crackle")
    data class Crackle(
        val density: IgnitorDsl = Constant(0.2),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            density.collectParams(out)
        }
    }

    /** Ramp oscillator. Reverse sawtooth (ramp up, opposite slope of [Sawtooth]). */
    @WireName("ramp")
    data class Ramp(
        val freq: IgnitorDsl = Freq,
        val analog: IgnitorDsl = Constant(0.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); analog.collectParams(out)
        }
    }

    /** Unison supersaw oscillator. Stacks multiple detuned sawtooth voices for a thick sound. */
    @WireName("supersaw")
    data class SuperSaw(
        val freq: IgnitorDsl = Freq,
        val voices: IgnitorDsl = Constant(8.0),
        val freqSpread: IgnitorDsl = Constant(0.2),
        val analog: IgnitorDsl = Constant(0.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); voices.collectParams(out); freqSpread.collectParams(out); analog.collectParams(out)
        }
    }

    /** Unison supersine oscillator. Stacks multiple detuned sine voices. */
    @WireName("supersine")
    data class SuperSine(
        val freq: IgnitorDsl = Freq,
        val voices: IgnitorDsl = Constant(8.0),
        val freqSpread: IgnitorDsl = Constant(0.2),
        val analog: IgnitorDsl = Constant(0.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); voices.collectParams(out); freqSpread.collectParams(out); analog.collectParams(out)
        }
    }

    /** Unison supersquare oscillator. Stacks multiple detuned square voices. */
    @WireName("supersquare")
    data class SuperSquare(
        val freq: IgnitorDsl = Freq,
        val voices: IgnitorDsl = Constant(8.0),
        val freqSpread: IgnitorDsl = Constant(0.2),
        val analog: IgnitorDsl = Constant(0.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); voices.collectParams(out); freqSpread.collectParams(out); analog.collectParams(out)
        }
    }

    /** Unison supertriangle oscillator. Stacks multiple detuned triangle voices. */
    @WireName("supertri")
    data class SuperTri(
        val freq: IgnitorDsl = Freq,
        val voices: IgnitorDsl = Constant(8.0),
        val freqSpread: IgnitorDsl = Constant(0.2),
        val analog: IgnitorDsl = Constant(0.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); voices.collectParams(out); freqSpread.collectParams(out); analog.collectParams(out)
        }
    }

    /** Unison superramp oscillator. Stacks multiple detuned ramp voices. */
    @WireName("superramp")
    data class SuperRamp(
        val freq: IgnitorDsl = Freq,
        val voices: IgnitorDsl = Constant(8.0),
        val freqSpread: IgnitorDsl = Constant(0.2),
        val analog: IgnitorDsl = Constant(0.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); voices.collectParams(out); freqSpread.collectParams(out); analog.collectParams(out)
        }
    }

    /** Silent exciter. Outputs a zero-filled buffer. */
    @WireName("silence")
    data object Silence : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {}
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Physical Models
    // ═════════════════════════════════════════════════════════════════════════════

    /** Karplus-Strong plucked string physical model. */
    @WireName("pluck")
    data class Pluck(
        val freq: IgnitorDsl = Freq,
        val decay: IgnitorDsl = Constant(0.996),
        val brightness: IgnitorDsl = Constant(0.5),
        val pickPosition: IgnitorDsl = Constant(0.5),
        val stiffness: IgnitorDsl = Constant(0.0),
        val analog: IgnitorDsl = Constant(0.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); decay.collectParams(out); brightness.collectParams(out); pickPosition.collectParams(out)
            stiffness.collectParams(out); analog.collectParams(out)
        }
    }

    /** Unison superpluck. Stacks multiple detuned Karplus-Strong voices for a chorus-like string sound. */
    @WireName("superpluck")
    data class SuperPluck(
        val freq: IgnitorDsl = Freq,
        val voices: IgnitorDsl = Constant(8.0),
        val freqSpread: IgnitorDsl = Constant(0.2),
        val decay: IgnitorDsl = Constant(0.996),
        val brightness: IgnitorDsl = Constant(0.5),
        val pickPosition: IgnitorDsl = Constant(0.5),
        val stiffness: IgnitorDsl = Constant(0.0),
        val analog: IgnitorDsl = Constant(0.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            freq.collectParams(out); voices.collectParams(out); freqSpread.collectParams(out); decay.collectParams(out); brightness.collectParams(
                out
            )
            pickPosition.collectParams(out); stiffness.collectParams(out); analog.collectParams(out)
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Dispatch / Selection
    // ═════════════════════════════════════════════════════════════════════════════

    /**
     * Selects one of several child ignitors based on the voice's `soundIndex`.
     *
     * Lets a single sound expose multiple variants, addressable per note via the
     * `name:n` mini-notation or `.n(...)` pattern — the same mechanism that picks
     * variants in a sample bank (`bd:0`, `bd:1`, ...).
     *
     * Index wraps with floor-mod semantics: with N children, index `k` selects
     * `children[k.mod(N)]`, so negative indices wrap from the end and overflow
     * wraps to zero. Missing `:n` defaults to index 0 at the registry boundary.
     *
     * Nested variants all dispatch on the same `soundIndex` — this is intentional,
     * so a single switching axis can drive correlated changes deep in the tree.
     */
    @WireName("variants")
    data class Variants(val children: List<IgnitorDsl>) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            children.forEach { it.collectParams(out) }
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Arithmetic Composition
    // ═════════════════════════════════════════════════════════════════════════════

    /** Additive combinator. Sums two ignitor signals sample-by-sample. */
    @WireName("plus")
    data class Plus(val left: IgnitorDsl, val right: IgnitorDsl) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            left.collectParams(out); right.collectParams(out)
        }
    }

    /** Multiplicative combinator. Multiplies two ignitor signals sample-by-sample (ring modulation). */
    @WireName("times")
    data class Times(val left: IgnitorDsl, val right: IgnitorDsl) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            left.collectParams(out); right.collectParams(out)
        }
    }

    /** Divides the left signal by the right signal (per-sample division). */
    @WireName("div")
    data class Div(
        val left: IgnitorDsl,
        val right: IgnitorDsl = Constant(1.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            left.collectParams(out); right.collectParams(out)
        }
    }

    /** Subtractive combinator. Subtracts the right signal from the left sample-by-sample. */
    @WireName("minus")
    data class Minus(val left: IgnitorDsl, val right: IgnitorDsl) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            left.collectParams(out); right.collectParams(out)
        }
    }

    /** Negates the inner signal (flips polarity). */
    @WireName("neg")
    data class Neg(val inner: IgnitorDsl) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out)
        }
    }

    /** Absolute value of the inner signal (full-wave rectification). */
    @WireName("abs")
    data class Abs(val inner: IgnitorDsl) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out)
        }
    }

    /**
     * Raises [base] to the power of [exp] sample-by-sample.
     *
     * Uses signed-magnitude semantics: for negative [base], computes
     * `-(|base|^exp)`. Preserves sign and avoids `NaN` for any real
     * exponent — keeps the engine numerically stable at audio rate.
     */
    @WireName("pow")
    data class Pow(val base: IgnitorDsl, val exp: IgnitorDsl) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            base.collectParams(out); exp.collectParams(out)
        }
    }

    /** Per-sample minimum of two signals. */
    @WireName("min")
    data class Min(val left: IgnitorDsl, val right: IgnitorDsl) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            left.collectParams(out); right.collectParams(out)
        }
    }

    /** Per-sample maximum of two signals. */
    @WireName("max")
    data class Max(val left: IgnitorDsl, val right: IgnitorDsl) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            left.collectParams(out); right.collectParams(out)
        }
    }

    /** Bounds the inner signal to `[lo, hi]` per sample. */
    @WireName("clamp")
    data class Clamp(
        val inner: IgnitorDsl,
        val lo: IgnitorDsl = Constant(-1.0),
        val hi: IgnitorDsl = Constant(1.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); lo.collectParams(out); hi.collectParams(out)
        }
    }

    /** `e^x` per sample. */
    @WireName("exp")
    data class Exp(val inner: IgnitorDsl) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out)
        }
    }

    /**
     * Natural logarithm per sample.
     *
     * Signed-magnitude: for negative inputs computes `-ln(|x|)`.
     * `log(0)` is treated as `0` to avoid `-Inf` poisoning the audio path.
     */
    @WireName("log")
    data class Log(val inner: IgnitorDsl) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out)
        }
    }

    /**
     * Square root per sample.
     *
     * Signed-magnitude: for negative inputs computes `-√|x|` to keep the engine `NaN`-free.
     */
    @WireName("sqrt")
    data class Sqrt(val inner: IgnitorDsl) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out)
        }
    }

    /** Sign of the inner signal (`-1`, `0`, or `+1`). */
    @WireName("sign")
    data class Sign(val inner: IgnitorDsl) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out)
        }
    }

    /** Hyperbolic tangent per sample (smooth saturation curve). */
    @WireName("tanh")
    data class Tanh(val inner: IgnitorDsl) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out)
        }
    }

    /**
     * Linear interpolation per sample: `left·(1−t) + right·t`.
     *
     * Crossfades between [left] and [right] under per-sample weight [t] (typically `[0, 1]`).
     */
    @WireName("lerp")
    data class Lerp(
        val left: IgnitorDsl,
        val right: IgnitorDsl,
        val t: IgnitorDsl,
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            left.collectParams(out); right.collectParams(out); t.collectParams(out)
        }
    }

    /**
     * Maps the inner signal from `[-1, 1]` to `[lo, hi]` per sample.
     *
     * Standard LFO scaler. Output = `lo + (inner + 1)·0.5·(hi − lo)`.
     */
    @WireName("range")
    data class Range(
        val inner: IgnitorDsl,
        val lo: IgnitorDsl,
        val hi: IgnitorDsl,
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); lo.collectParams(out); hi.collectParams(out)
        }
    }

    /** Maps the inner signal from `[0, 1]` to `[-1, 1]` per sample. */
    @WireName("bipolar")
    data class Bipolar(val inner: IgnitorDsl) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out)
        }
    }

    /** Maps the inner signal from `[-1, 1]` to `[0, 1]` per sample. */
    @WireName("unipolar")
    data class Unipolar(val inner: IgnitorDsl) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out)
        }
    }

    /** Per-sample floor: largest integer `≤ x`. */
    @WireName("floor")
    data class Floor(val inner: IgnitorDsl) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out)
        }
    }

    /** Per-sample ceiling: smallest integer `≥ x`. */
    @WireName("ceil")
    data class Ceil(val inner: IgnitorDsl) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out)
        }
    }

    /** Per-sample round to nearest integer (banker's rounding ties to even). */
    @WireName("round")
    data class Round(val inner: IgnitorDsl) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out)
        }
    }

    /** Per-sample fractional part: `x − floor(x)`. */
    @WireName("frac")
    data class Frac(val inner: IgnitorDsl) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out)
        }
    }

    /**
     * Per-sample modulo: `left mod right`.
     *
     * Zero divisors are substituted with a tiny epsilon (1e-30) so the engine
     * never produces `NaN`. The master limiter handles the resulting spike.
     */
    @WireName("mod")
    data class Mod(val left: IgnitorDsl, val right: IgnitorDsl) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            left.collectParams(out); right.collectParams(out)
        }
    }

    /**
     * Per-sample reciprocal: `1 / x`.
     *
     * Zero inputs are substituted with a tiny epsilon (1e-30) so the engine
     * never produces `NaN`. The master limiter handles the resulting spike.
     */
    @WireName("recip")
    data class Recip(val inner: IgnitorDsl) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out)
        }
    }

    /** Per-sample square: `x · x`. */
    @WireName("sq")
    data class Sq(val inner: IgnitorDsl) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out)
        }
    }

    /**
     * Per-sample conditional: returns [whenTrue] when [cond] `> 0`, else [whenFalse].
     *
     * Both branches are evaluated at audio rate (no short-circuit), so the gate is
     * deterministic and stateful sources advance regardless of which branch is selected.
     */
    @WireName("select")
    data class Select(
        val cond: IgnitorDsl,
        val whenTrue: IgnitorDsl,
        val whenFalse: IgnitorDsl,
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            cond.collectParams(out); whenTrue.collectParams(out); whenFalse.collectParams(out)
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Frequency Modifiers
    // ═════════════════════════════════════════════════════════════════════════════

    /** Shifts the pitch of the inner exciter by a number of semitones. */
    @WireName("detune")
    data class Detune(
        val inner: IgnitorDsl,
        val semitones: IgnitorDsl = Constant(0.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); semitones.collectParams(out)
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Filters
    // ═════════════════════════════════════════════════════════════════════════════

    /** Biquad lowpass filter. Attenuates frequencies above the cutoff. */
    @WireName("lowpass")
    data class Lowpass(
        val inner: IgnitorDsl,
        val cutoffHz: IgnitorDsl = Constant(2000.0),
        val q: IgnitorDsl = Constant(0.707),
        /**
         * Analog character amount. `0` = clean linear filter (default — bit-identical
         * to pre-saturation behaviour). Higher values engage the OB-X-style state-dependent
         * damping in the SVF resonance feedback, compressing the resonance peak.
         * Typical range 0..10; values around 1–3 give Diva-default warmth.
         */
        val analog: IgnitorDsl = Constant(0.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); cutoffHz.collectParams(out); q.collectParams(out); analog.collectParams(out)
        }
    }

    /** Biquad highpass filter. Attenuates frequencies below the cutoff. */
    @WireName("highpass")
    data class Highpass(
        val inner: IgnitorDsl,
        val cutoffHz: IgnitorDsl = Constant(200.0),
        val q: IgnitorDsl = Constant(0.707),
        /** See [Lowpass.analog] — same semantics for the HP tap. */
        val analog: IgnitorDsl = Constant(0.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); cutoffHz.collectParams(out); q.collectParams(out); analog.collectParams(out)
        }
    }

    /** Simple one-pole lowpass filter. Lightweight with -6 dB/oct rolloff and no resonance. */
    @WireName("one-pole-lowpass")
    data class OnePoleLowpass(
        val inner: IgnitorDsl,
        val cutoffHz: IgnitorDsl = Constant(2000.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); cutoffHz.collectParams(out)
        }
    }

    /** SVF bandpass filter. Passes frequencies near the cutoff, attenuates others. */
    @WireName("bandpass")
    data class Bandpass(
        val inner: IgnitorDsl,
        val cutoffHz: IgnitorDsl = Constant(1000.0),
        val q: IgnitorDsl = Constant(1.0),
        /**
         * Reserved for forward-compat — accepted but currently a no-op (BP saturation
         * not yet implemented; same pattern as the voice-strip `SvfBPF`).
         * See [Lowpass.analog] for semantics when implemented.
         */
        val analog: IgnitorDsl = Constant(0.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); cutoffHz.collectParams(out); q.collectParams(out); analog.collectParams(out)
        }
    }

    /** SVF notch (band-reject) filter. Removes frequencies near the cutoff, passes others. */
    @WireName("notch")
    data class Notch(
        val inner: IgnitorDsl,
        val cutoffHz: IgnitorDsl = Constant(1000.0),
        val q: IgnitorDsl = Constant(1.0),
        /** Reserved for forward-compat — accepted but currently a no-op (see [Bandpass.analog]). */
        val analog: IgnitorDsl = Constant(0.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); cutoffHz.collectParams(out); q.collectParams(out); analog.collectParams(out)
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Envelope
    // ═════════════════════════════════════════════════════════════════════════════

    /** ADSR amplitude envelope. Shapes the inner signal's volume over the note lifecycle. */
    @WireName("adsr")
    data class Adsr(
        val inner: IgnitorDsl,
        val attackSec: IgnitorDsl = Constant(0.01),
        val decaySec: IgnitorDsl = Constant(0.1),
        val sustainLevel: IgnitorDsl = Constant(0.7),
        val releaseSec: IgnitorDsl = Constant(0.3),
        val attackCurve: AdsrCurve? = null,
        val decayCurve: AdsrCurve? = null,
        val releaseCurve: AdsrCurve? = null,
    ) : IgnitorDsl {
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
    @WireName("fm")
    data class Fm(
        val carrier: IgnitorDsl,
        val modulator: IgnitorDsl,
        val ratio: IgnitorDsl = Constant(1.0),
        val depth: IgnitorDsl = Constant(0.0),
        val envAttackSec: IgnitorDsl = Constant(0.0),
        val envDecaySec: IgnitorDsl = Constant(0.0),
        val envSustainLevel: IgnitorDsl = Constant(1.0),
        val envReleaseSec: IgnitorDsl = Constant(0.0),
    ) : IgnitorDsl {
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
    @WireName("drive")
    data class Drive(
        val inner: IgnitorDsl,
        val amount: IgnitorDsl = Constant(0.5),
        val driveType: String = "linear",
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); amount.collectParams(out)
        }
    }

    /**
     * Pure waveshaping without drive. Applies a nonlinear transfer function per sample.
     * Includes DC blocker for asymmetric shapes.
     *
     * Shapes:
     *  - **Symmetric soft:** "soft" (tanh), "gentle" (soft clip, 2× gain), "softsat"
     *    (algebraic, gentler), "cubic", "exp" (transistor), "sineshaper" (peak-at-unity fold).
     *  - **Symmetric hard / harsh:** "hard" (clip), "zerosquare" (→ square), "chebyshev"
     *    (3rd-harmonic), "fold" (sin wavefold), "linearfold" (triangle wavefold).
     *  - **Asymmetric (even harmonics, DC):** "diode", "tube" (shifted-tanh), "asym" (poly),
     *    "stompbox" (diode pedal), "rectify" (full-wave).
     */
    @WireName("clip")
    data class Clip(
        val inner: IgnitorDsl,
        val shape: String = "soft",
        val oversample: Int = 0,
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out)
        }
    }

    /**
     * Legacy distortion node. Kept for backward compatibility with serialized trees.
     * New code should use [Drive] + [Clip] instead. The builder extension [IgnitorDsl.distort]
     * creates a Clip(Drive(...)) chain.
     */
    @WireName("distort")
    data class Distort(
        val inner: IgnitorDsl,
        val amount: IgnitorDsl = Constant(0.5),
        val shape: String = "soft",
        val oversample: Int = 0,
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); amount.collectParams(out)
        }
    }

    /** Bit-crush effect. Reduces amplitude resolution to create quantization noise. */
    @WireName("crush")
    data class Crush(
        val inner: IgnitorDsl,
        val amount: IgnitorDsl = Constant(8.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); amount.collectParams(out)
        }
    }

    /** Sample-rate reduction effect. Holds samples to create aliasing artifacts. */
    @WireName("coarse")
    data class Coarse(
        val inner: IgnitorDsl,
        val amount: IgnitorDsl = Constant(4.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); amount.collectParams(out)
        }
    }

    /**
     * Phaser effect. Sweeps a series of allpass filters to create notch comb filtering.
     *
     * @param blend Crossfade between dry and wet. 0.0 = 100% dry (bypass), 1.0 = 100% wet (effect only).
     *   Formula: `out = dry · (1 − blend) + wet · blend`. Default: 0.5 (equal mix).
     */
    @WireName("phaser")
    data class Phaser(
        val inner: IgnitorDsl,
        val rate: IgnitorDsl = Constant(0.5),
        val blend: IgnitorDsl = Constant(0.5),
        val center: IgnitorDsl = Constant(1000.0),
        val sweep: IgnitorDsl = Constant(1000.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); rate.collectParams(out); blend.collectParams(out)
            center.collectParams(out); sweep.collectParams(out)
        }
    }

    /** Tremolo effect. Modulates amplitude with an LFO for a pulsing volume change. */
    @WireName("tremolo")
    data class Tremolo(
        val inner: IgnitorDsl,
        val rate: IgnitorDsl = Constant(5.0),
        val depth: IgnitorDsl = Constant(0.5),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); rate.collectParams(out); depth.collectParams(out)
        }
    }

    /**
     * Shimmer effect. Granular pitch-shift cloud with feedback — Aetherizer-style.
     *
     * Chops the input into short overlapping grains, replays them at the pitch intervals
     * specified by [pitches] (in semitones), and feeds the wet output back into the grain buffer
     * through a one-pole lowpass at [tone] Hz.
     *
     * @param blend Crossfade between dry and wet. 0.0 = 100% dry (bypass), 1.0 = 100% wet (effect only).
     *   Formula: `out = dry · (1 − blend) + wet · blend`. Default: 0.5 (equal mix).
     * @param feedback Wet → grain-buffer feedback. 0.0 = no cascade, 0.9 = long tails.
     *   Hard-clamped to 0.95 internally for stability.
     * @param pitches Semitone transpositions for grains. Default: `[0, 7, 12]` (root + fifth + octave).
     *   Example: `[0, 4, 7, 11]` for a major 7th chord shimmer.
     * @param tone One-pole lowpass cutoff in Hz applied in the feedback path.
     *   Lower = darker, more ghostly tails. Typical: 2000–6000. Default: 4000.
     */
    @WireName("shimmer")
    data class Shimmer(
        val inner: IgnitorDsl,
        val blend: IgnitorDsl = Constant(0.5),
        val feedback: IgnitorDsl = Constant(0.5),
        val pitches: List<Double> = listOf(0.0, 7.0, 12.0),
        val tone: IgnitorDsl = Constant(4000.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); blend.collectParams(out); feedback.collectParams(out); tone.collectParams(out)
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Pitch Modulation
    // ═════════════════════════════════════════════════════════════════════════════

    /**
     * Vibrato effect. Modulates pitch with a sinusoidal LFO.
     *
     * @param rate LFO frequency in Hz (default 5.0)
     * @param depth modulation depth in semitones (default 0.25 ≈ quarter-semitone wobble).
     *   Matches the sprudel `vibratoMod()` unit: both specify depth in semitones.
     */
    @WireName("vibrato")
    data class Vibrato(
        val inner: IgnitorDsl,
        val rate: IgnitorDsl = Constant(5.0),
        val depth: IgnitorDsl = Constant(0.25),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); rate.collectParams(out); depth.collectParams(out)
        }
    }

    /** Pitch acceleration. Continuously shifts pitch over the voice's duration using an exponential curve. */
    @WireName("accelerate")
    data class Accelerate(
        val inner: IgnitorDsl,
        val amount: IgnitorDsl = Constant(0.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); amount.collectParams(out)
        }
    }

    /**
     * Pitch envelope. Applies an attack-decay-release envelope to pitch, useful for
     * kick drum sweeps, laser effects, and other transient pitch gestures.
     */
    @WireName("pitch-envelope")
    data class PitchEnvelope(
        val inner: IgnitorDsl,
        val amount: IgnitorDsl = Constant(0.0),
        val attackSec: IgnitorDsl = Constant(0.01),
        val decaySec: IgnitorDsl = Constant(0.1),
        val releaseSec: IgnitorDsl = Constant(0.0),
        val curve: IgnitorDsl = Constant(0.0),
        val anchor: IgnitorDsl = Constant(0.0),
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); amount.collectParams(out); attackSec.collectParams(out)
            decaySec.collectParams(out); releaseSec.collectParams(out); curve.collectParams(out); anchor.collectParams(out)
        }
    }

    /**
     * General-purpose pitch modulation. The [mod] Ignitor produces per-sample phase-deviation
     * values (0.0 = no change, positive = higher pitch, negative = lower). At runtime, the
     * build-time walker converts to ratio space (`value + 1.0`) and bubbles the mod to the
     * source oscillator.
     *
     * This is the general primitive underlying `.vibrato()`, `.accelerate()`, `.fm()`, and
     * `.pitchEnvelope()`. Use it for custom pitch modulation from any Ignitor source.
     */
    @WireName("pitch-mod")
    data class PitchMod(
        val inner: IgnitorDsl,
        val mod: IgnitorDsl,
    ) : IgnitorDsl {
        override fun collectParams(out: MutableList<Param>) {
            inner.collectParams(out); mod.collectParams(out)
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════════
// Builder Extensions
// ═════════════════════════════════════════════════════════════════════════════════

// Arithmetic

/** Adds two ignitor signals together (sample-by-sample sum). */
operator fun IgnitorDsl.plus(other: IgnitorDsl) = IgnitorDsl.Plus(left = this, right = other)

/** Multiplies two ignitor signals together (ring modulation). */
operator fun IgnitorDsl.times(other: IgnitorDsl) = IgnitorDsl.Times(left = this, right = other)

/** Scales this signal by a modulatable [other] factor. Alias for [times]. */
fun IgnitorDsl.mul(other: IgnitorDsl) = IgnitorDsl.Times(left = this, right = other)

/** Divides this signal by a modulatable [other] divisor. */
fun IgnitorDsl.div(other: IgnitorDsl) = IgnitorDsl.Div(left = this, right = other)

/** Subtracts [other] from this signal sample-by-sample. */
fun IgnitorDsl.minus(other: IgnitorDsl) = IgnitorDsl.Minus(left = this, right = other)

/** Negates this signal (flips polarity). */
fun IgnitorDsl.neg() = IgnitorDsl.Neg(inner = this)

/** Absolute value of this signal (full-wave rectification). */
fun IgnitorDsl.abs() = IgnitorDsl.Abs(inner = this)

/** Raises this signal to the power of [exp]. Signed-magnitude (no NaN for negative bases). */
fun IgnitorDsl.pow(exp: IgnitorDsl) = IgnitorDsl.Pow(base = this, exp = exp)

/** Per-sample minimum of this signal and [other]. */
fun IgnitorDsl.min(other: IgnitorDsl) = IgnitorDsl.Min(left = this, right = other)

/** Per-sample maximum of this signal and [other]. */
fun IgnitorDsl.max(other: IgnitorDsl) = IgnitorDsl.Max(left = this, right = other)

/** Bounds this signal to the range `[lo, hi]` per sample. */
fun IgnitorDsl.clamp(lo: IgnitorDsl, hi: IgnitorDsl) = IgnitorDsl.Clamp(inner = this, lo = lo, hi = hi)

/** `e^x` per sample. */
fun IgnitorDsl.exp() = IgnitorDsl.Exp(inner = this)

/** Natural logarithm per sample. Signed-magnitude; `log(0) = 0` (no `-Inf`). */
fun IgnitorDsl.log() = IgnitorDsl.Log(inner = this)

/** Square root per sample. Signed-magnitude (no `NaN` for negatives). */
fun IgnitorDsl.sqrt() = IgnitorDsl.Sqrt(inner = this)

/** Sign of this signal: `-1`, `0`, or `+1`. */
fun IgnitorDsl.sign() = IgnitorDsl.Sign(inner = this)

/** `tanh(x)` per sample (smooth saturation curve). */
fun IgnitorDsl.tanh() = IgnitorDsl.Tanh(inner = this)

/** Linear interpolation: `this·(1−t) + other·t`. */
fun IgnitorDsl.lerp(other: IgnitorDsl, t: IgnitorDsl) = IgnitorDsl.Lerp(left = this, right = other, t = t)

/** Maps this signal from `[-1, 1]` to `[lo, hi]` per sample. */
fun IgnitorDsl.range(lo: IgnitorDsl, hi: IgnitorDsl) = IgnitorDsl.Range(inner = this, lo = lo, hi = hi)

/** Maps this signal from `[0, 1]` to `[-1, 1]` per sample. */
fun IgnitorDsl.bipolar() = IgnitorDsl.Bipolar(inner = this)

/** Maps this signal from `[-1, 1]` to `[0, 1]` per sample. */
fun IgnitorDsl.unipolar() = IgnitorDsl.Unipolar(inner = this)

/** Per-sample floor. */
fun IgnitorDsl.floor() = IgnitorDsl.Floor(inner = this)

/** Per-sample ceiling. */
fun IgnitorDsl.ceil() = IgnitorDsl.Ceil(inner = this)

/** Per-sample round to nearest integer. */
fun IgnitorDsl.round() = IgnitorDsl.Round(inner = this)

/** Per-sample fractional part: `x − floor(x)`. */
fun IgnitorDsl.frac() = IgnitorDsl.Frac(inner = this)

/** Per-sample modulo. Zero divisors substituted with `1e-30` to avoid `NaN`. */
fun IgnitorDsl.mod(other: IgnitorDsl) = IgnitorDsl.Mod(left = this, right = other)

/** Per-sample reciprocal: `1 / x`. Zero inputs substituted with `1e-30` to avoid `NaN`. */
fun IgnitorDsl.recip() = IgnitorDsl.Recip(inner = this)

/** Per-sample square: `x · x`. */
fun IgnitorDsl.sq() = IgnitorDsl.Sq(inner = this)

/** Per-sample conditional: when this signal `> 0` use [whenTrue], else [whenFalse]. */
fun IgnitorDsl.select(whenTrue: IgnitorDsl, whenFalse: IgnitorDsl) =
    IgnitorDsl.Select(cond = this, whenTrue = whenTrue, whenFalse = whenFalse)

// Frequency

/** Shifts pitch by the given number of [semitones]. */
fun IgnitorDsl.detune(semitones: Double) = IgnitorDsl.Detune(
    inner = this,
    semitones = IgnitorDsl.Constant(semitones),
)

// Filters

/** Applies a biquad lowpass filter at [cutoffHz] with resonance [q]. */
fun IgnitorDsl.lowpass(cutoffHz: Double, q: Double = 0.707) = IgnitorDsl.Lowpass(
    inner = this,
    cutoffHz = IgnitorDsl.Constant(cutoffHz),
    q = IgnitorDsl.Constant(q),
)

/** Applies a biquad highpass filter at [cutoffHz] with resonance [q]. */
fun IgnitorDsl.highpass(cutoffHz: Double, q: Double = 0.707) = IgnitorDsl.Highpass(
    inner = this,
    cutoffHz = IgnitorDsl.Constant(cutoffHz),
    q = IgnitorDsl.Constant(q),
)

/** Applies a lightweight one-pole lowpass filter at [cutoffHz]. */
fun IgnitorDsl.onePoleLowpass(cutoffHz: Double) = IgnitorDsl.OnePoleLowpass(
    inner = this,
    cutoffHz = IgnitorDsl.Constant(cutoffHz),
)

fun IgnitorDsl.bandpass(cutoffHz: Double, q: Double = 1.0) = IgnitorDsl.Bandpass(
    this, IgnitorDsl.Constant(cutoffHz), IgnitorDsl.Constant(q),
)

fun IgnitorDsl.notch(cutoffHz: Double, q: Double = 1.0) = IgnitorDsl.Notch(
    this, IgnitorDsl.Constant(cutoffHz), IgnitorDsl.Constant(q),
)

fun IgnitorDsl.drive(amount: Double, driveType: String = "linear") =
    IgnitorDsl.Drive(this, IgnitorDsl.Constant(amount), driveType)

/**
 * Pure waveshaping without drive. See [IgnitorDsl.Clip] for the full list of supported [shape] values.
 *
 * Quick reference:
 *  - soft / gentle / softsat / cubic / exp / sineshaper — symmetric soft
 *  - hard / zerosquare / chebyshev / fold / linearfold — symmetric hard / wavefolding
 *  - diode / tube / asym / stompbox / rectify — asymmetric (even harmonics, DC offset)
 */
fun IgnitorDsl.clip(shape: String = "soft", oversample: Int = 0) = IgnitorDsl.Clip(this, shape, oversample)

// Envelope

/** Wraps this signal in an ADSR amplitude envelope. */
fun IgnitorDsl.adsr(attackSec: Double, decaySec: Double, sustainLevel: Double, releaseSec: Double) = IgnitorDsl.Adsr(
    inner = this,
    attackSec = IgnitorDsl.Constant(attackSec),
    decaySec = IgnitorDsl.Constant(decaySec),
    sustainLevel = IgnitorDsl.Constant(sustainLevel),
    releaseSec = IgnitorDsl.Constant(releaseSec),
)

// FM

/** Applies FM synthesis to this carrier using the given [modulator], [ratio], and [depth]. */
fun IgnitorDsl.fm(
    modulator: IgnitorDsl,
    ratio: Double,
    depth: Double,
    envAttackSec: Double = 0.0,
    envDecaySec: Double = 0.0,
    envSustainLevel: Double = 1.0,
    envReleaseSec: Double = 0.0,
) = IgnitorDsl.Fm(
    carrier = this,
    modulator = modulator,
    ratio = IgnitorDsl.Constant(ratio),
    depth = IgnitorDsl.Constant(depth),
    envAttackSec = IgnitorDsl.Constant(envAttackSec),
    envDecaySec = IgnitorDsl.Constant(envDecaySec),
    envSustainLevel = IgnitorDsl.Constant(envSustainLevel),
    envReleaseSec = IgnitorDsl.Constant(envReleaseSec),
)

// Effects

/**
 * Applies waveshaping distortion with the given [amount] and clipping [shape].
 *
 * Equivalent to `this.drive(amount).clip(shape, oversample)`. See [IgnitorDsl.Clip] for the
 * full list of supported [shape] values.
 *
 * Quick reference:
 *  - soft / gentle / softsat / cubic / exp / sineshaper — symmetric soft
 *  - hard / zerosquare / chebyshev / fold / linearfold — symmetric hard / wavefolding
 *  - diode / tube / asym / stompbox / rectify — asymmetric (even harmonics, DC offset)
 */
fun IgnitorDsl.distort(amount: Double, shape: String = "soft", oversample: Int = 0) =
    IgnitorDsl.Clip(inner = IgnitorDsl.Drive(inner = this, amount = IgnitorDsl.Constant(amount)), shape = shape, oversample = oversample)

/** Applies bit-crush quantization at the given bit [amount]. */
fun IgnitorDsl.crush(amount: Double) = IgnitorDsl.Crush(
    inner = this,
    amount = IgnitorDsl.Constant(amount),
)

/** Applies sample-rate reduction by the given [amount] factor. */
fun IgnitorDsl.coarse(amount: Double) = IgnitorDsl.Coarse(
    inner = this,
    amount = IgnitorDsl.Constant(amount),
)

/** Applies a phaser effect. [blend]: 0.0 = dry only, 1.0 = wet only (crossfade). */
fun IgnitorDsl.phaser(rate: Double, blend: Double = 0.5, center: Double = 1000.0, sweep: Double = 1000.0) = IgnitorDsl.Phaser(
    inner = this,
    rate = IgnitorDsl.Constant(rate),
    blend = IgnitorDsl.Constant(blend),
    center = IgnitorDsl.Constant(center),
    sweep = IgnitorDsl.Constant(sweep),
)

/** Applies a tremolo (amplitude modulation) at the given LFO [rate] and [depth]. */
fun IgnitorDsl.tremolo(rate: Double, depth: Double) = IgnitorDsl.Tremolo(
    inner = this,
    rate = IgnitorDsl.Constant(rate),
    depth = IgnitorDsl.Constant(depth),
)

/**
 * Applies a granular shimmer (pitch-shift cloud with feedback).
 * [blend]: 0.0 = dry only, 1.0 = wet only (crossfade). [pitches]: semitone transpositions.
 * [tone]: feedback-path LPF cutoff in Hz.
 */
fun IgnitorDsl.shimmer(
    blend: Double = 0.5,
    feedback: Double = 0.5,
    pitches: List<Double> = listOf(0.0, 7.0, 12.0),
    tone: Double = 4000.0,
) = IgnitorDsl.Shimmer(
    inner = this,
    blend = IgnitorDsl.Constant(blend),
    feedback = IgnitorDsl.Constant(feedback),
    pitches = pitches,
    tone = IgnitorDsl.Constant(tone),
)

// Pitch modulation

/** Applies vibrato (pitch modulation) at the given LFO [rate] and [depth]. */
fun IgnitorDsl.vibrato(rate: Double, depth: Double) = IgnitorDsl.Vibrato(
    inner = this,
    rate = IgnitorDsl.Constant(rate),
    depth = IgnitorDsl.Constant(depth),
)

/** Applies continuous pitch acceleration over the voice's duration. */
fun IgnitorDsl.accelerate(amount: Double) = IgnitorDsl.Accelerate(
    inner = this,
    amount = IgnitorDsl.Constant(amount),
)

/**
 * Applies a custom pitch modulation from any Ignitor signal.
 *
 * The [mod] signal uses deviation space: 0.0 = no change, positive = higher, negative = lower.
 * At build time, the runtime converts to ratio space and bubbles the mod to the source oscillator.
 */
fun IgnitorDsl.pitchMod(mod: IgnitorDsl) = IgnitorDsl.PitchMod(inner = this, mod = mod)

// ═════════════════════════════════════════════════════════════════════════════════
// Discovery
// ═════════════════════════════════════════════════════════════════════════════════

/** Walks the DSL tree and collects all [IgnitorDsl.Param] leaf nodes. */
fun IgnitorDsl.getParamSlots(): List<IgnitorDsl.Param> {
    val result = mutableListOf<IgnitorDsl.Param>()
    collectParams(result)
    return result
}

/**
 * Walks the DSL tree and returns the maximum ADSR release time (in seconds)
 * found in any [IgnitorDsl.Adsr] node.
 *
 * Returns 0.0 if no Adsr nodes exist in the tree.
 * Only reads [IgnitorDsl.Constant] values and [IgnitorDsl.Param] defaults —
 * dynamically modulated release times use the static default.
 */
fun IgnitorDsl.maxReleaseSec(): Double = when (this) {
    // Adsr: extract this node's release and recurse into inner
    is IgnitorDsl.Adsr -> {
        val thisRelease = when (releaseSec) {
            is IgnitorDsl.Constant -> releaseSec.value
            is IgnitorDsl.Param -> releaseSec.default
            else -> 0.3
        }
        maxOf(thisRelease, inner.maxReleaseSec())
    }
    // Wrapper nodes with `inner`
    is IgnitorDsl.Lowpass -> inner.maxReleaseSec()
    is IgnitorDsl.Highpass -> inner.maxReleaseSec()
    is IgnitorDsl.Bandpass -> inner.maxReleaseSec()
    is IgnitorDsl.Notch -> inner.maxReleaseSec()
    is IgnitorDsl.OnePoleLowpass -> inner.maxReleaseSec()
    is IgnitorDsl.Distort -> inner.maxReleaseSec()
    is IgnitorDsl.Drive -> inner.maxReleaseSec()
    is IgnitorDsl.Clip -> inner.maxReleaseSec()
    is IgnitorDsl.Crush -> inner.maxReleaseSec()
    is IgnitorDsl.Coarse -> inner.maxReleaseSec()
    is IgnitorDsl.Phaser -> inner.maxReleaseSec()
    is IgnitorDsl.Tremolo -> inner.maxReleaseSec()
    is IgnitorDsl.Shimmer -> inner.maxReleaseSec()
    is IgnitorDsl.PitchMod -> inner.maxReleaseSec()
    is IgnitorDsl.Vibrato -> inner.maxReleaseSec()
    is IgnitorDsl.Accelerate -> inner.maxReleaseSec()
    is IgnitorDsl.PitchEnvelope -> inner.maxReleaseSec()
    is IgnitorDsl.Detune -> inner.maxReleaseSec()
    // Binary nodes
    is IgnitorDsl.Plus -> maxOf(left.maxReleaseSec(), right.maxReleaseSec())
    is IgnitorDsl.Times -> maxOf(left.maxReleaseSec(), right.maxReleaseSec())
    is IgnitorDsl.Div -> maxOf(left.maxReleaseSec(), right.maxReleaseSec())
    is IgnitorDsl.Minus -> maxOf(left.maxReleaseSec(), right.maxReleaseSec())
    is IgnitorDsl.Pow -> maxOf(base.maxReleaseSec(), exp.maxReleaseSec())
    is IgnitorDsl.Min -> maxOf(left.maxReleaseSec(), right.maxReleaseSec())
    is IgnitorDsl.Max -> maxOf(left.maxReleaseSec(), right.maxReleaseSec())
    is IgnitorDsl.Neg -> inner.maxReleaseSec()
    is IgnitorDsl.Abs -> inner.maxReleaseSec()
    is IgnitorDsl.Clamp -> maxOf(inner.maxReleaseSec(), lo.maxReleaseSec(), hi.maxReleaseSec())
    is IgnitorDsl.Exp -> inner.maxReleaseSec()
    is IgnitorDsl.Log -> inner.maxReleaseSec()
    is IgnitorDsl.Sqrt -> inner.maxReleaseSec()
    is IgnitorDsl.Sign -> inner.maxReleaseSec()
    is IgnitorDsl.Tanh -> inner.maxReleaseSec()
    is IgnitorDsl.Lerp -> maxOf(left.maxReleaseSec(), right.maxReleaseSec(), t.maxReleaseSec())
    is IgnitorDsl.Range -> maxOf(inner.maxReleaseSec(), lo.maxReleaseSec(), hi.maxReleaseSec())
    is IgnitorDsl.Bipolar -> inner.maxReleaseSec()
    is IgnitorDsl.Unipolar -> inner.maxReleaseSec()
    is IgnitorDsl.Floor -> inner.maxReleaseSec()
    is IgnitorDsl.Ceil -> inner.maxReleaseSec()
    is IgnitorDsl.Round -> inner.maxReleaseSec()
    is IgnitorDsl.Frac -> inner.maxReleaseSec()
    is IgnitorDsl.Mod -> maxOf(left.maxReleaseSec(), right.maxReleaseSec())
    is IgnitorDsl.Recip -> inner.maxReleaseSec()
    is IgnitorDsl.Sq -> inner.maxReleaseSec()
    is IgnitorDsl.Select -> maxOf(cond.maxReleaseSec(), whenTrue.maxReleaseSec(), whenFalse.maxReleaseSec())
    is IgnitorDsl.Fm -> maxOf(carrier.maxReleaseSec(), modulator.maxReleaseSec())
    // Variants: voice lifetime must cover whichever child gets picked, so take the max.
    is IgnitorDsl.Variants -> children.maxOfOrNull { it.maxReleaseSec() } ?: 0.0
    // Leaf nodes — no release info
    is IgnitorDsl.Freq -> 0.0
    is IgnitorDsl.Param -> 0.0
    is IgnitorDsl.Constant -> 0.0
    is IgnitorDsl.Silence -> 0.0
    is IgnitorDsl.WhiteNoise -> 0.0
    is IgnitorDsl.BrownNoise -> 0.0
    is IgnitorDsl.PinkNoise -> 0.0
    is IgnitorDsl.PerlinNoise -> 0.0
    is IgnitorDsl.BerlinNoise -> 0.0
    is IgnitorDsl.Dust -> 0.0
    is IgnitorDsl.Crackle -> 0.0
    is IgnitorDsl.Sine -> 0.0
    is IgnitorDsl.Sawtooth -> 0.0
    is IgnitorDsl.Square -> 0.0
    is IgnitorDsl.Triangle -> 0.0
    is IgnitorDsl.Ramp -> 0.0
    is IgnitorDsl.Zawtooth -> 0.0
    is IgnitorDsl.Zamp -> 0.0
    is IgnitorDsl.Impulse -> 0.0
    is IgnitorDsl.Pulze -> 0.0
    is IgnitorDsl.RawPulze -> 0.0
    is IgnitorDsl.SuperSaw -> 0.0
    is IgnitorDsl.SuperSine -> 0.0
    is IgnitorDsl.SuperSquare -> 0.0
    is IgnitorDsl.SuperTri -> 0.0
    is IgnitorDsl.SuperRamp -> 0.0
    is IgnitorDsl.Pluck -> 0.0
    is IgnitorDsl.SuperPluck -> 0.0
}
