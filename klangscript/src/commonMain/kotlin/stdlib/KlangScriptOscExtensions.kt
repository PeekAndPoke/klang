package io.peekandpoke.klang.script.stdlib

import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.annotations.KlangScriptLibraries
import io.peekandpoke.klang.script.stdlib.KlangScriptOscExtensions.lerp
import io.peekandpoke.klang.script.stdlib.KlangScriptOscExtensions.minus
import io.peekandpoke.klang.script.stdlib.KlangScriptOscExtensions.mod
import io.peekandpoke.klang.script.stdlib.KlangScriptOscExtensions.neg
import io.peekandpoke.klang.script.stdlib.KlangScriptOscExtensions.plus
import io.peekandpoke.klang.script.stdlib.KlangScriptOscExtensions.pow
import io.peekandpoke.klang.script.stdlib.KlangScriptOscExtensions.recip
import io.peekandpoke.klang.script.stdlib.KlangScriptOscExtensions.times

/**
 * Accepts [IgnitorDsl] or [Number]. Numbers are converted to [IgnitorDsl.Constant] automatically.
 */
typealias IgnitorDslLike = Any

/** Converts an [IgnitorDslLike] value to [IgnitorDsl]. Numbers become [IgnitorDsl.Constant] (not overridable by oscParams). */
fun IgnitorDslLike.toIgnitorDsl(): IgnitorDsl = when (this) {
    is IgnitorDsl -> this
    is Number -> IgnitorDsl.Constant(this.toDouble())
    else -> error("Expected IgnitorDsl or Number, got ${this::class.simpleName}")
}

/**
 * Extension methods on [IgnitorDsl] for KlangScript.
 *
 * Enables chaining: `Osc.sine().lowpass(1000).adsr(0.01, 0.1, 0.5, 0.3)`
 * Any numeric parameter also accepts an IgnitorDsl for audio-rate modulation:
 * `Osc.sine().lowpass(Osc.perlin())` — modulated cutoff.
 */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.TypeExtensions(IgnitorDsl::class)
object KlangScriptOscExtensions {

    // ── Filters ──────────────────────────────────────────────────────────────

    /** Applies a resonant lowpass filter. Cutoff and Q accept Number or IgnitorDsl. */
    @KlangScript.Method
    fun lowpass(self: IgnitorDsl, cutoffHz: IgnitorDslLike, q: IgnitorDslLike = 0.707): IgnitorDsl =
        IgnitorDsl.Lowpass(inner = self, cutoffHz = cutoffHz.toIgnitorDsl(), q = q.toIgnitorDsl())

    /** Applies a resonant highpass filter. Cutoff and Q accept Number or IgnitorDsl. */
    @KlangScript.Method
    fun highpass(self: IgnitorDsl, cutoffHz: IgnitorDslLike, q: IgnitorDslLike = 0.707): IgnitorDsl =
        IgnitorDsl.Highpass(inner = self, cutoffHz = cutoffHz.toIgnitorDsl(), q = q.toIgnitorDsl())

    /** Applies a one-pole lowpass filter (gentle rolloff). Alias: onePoleLowpass. */
    @KlangScript.Method
    fun warmth(self: IgnitorDsl, cutoffHz: IgnitorDslLike): IgnitorDsl =
        IgnitorDsl.OnePoleLowpass(inner = self, cutoffHz = cutoffHz.toIgnitorDsl())

    /** Applies a one-pole lowpass filter (gentle rolloff). Alias for warmth. */
    @KlangScript.Method
    fun onePoleLowpass(self: IgnitorDsl, cutoffHz: IgnitorDslLike): IgnitorDsl =
        IgnitorDsl.OnePoleLowpass(inner = self, cutoffHz = cutoffHz.toIgnitorDsl())

    /** SVF bandpass filter. Passes frequencies near the cutoff, attenuates others. */
    @KlangScript.Method
    fun bandpass(self: IgnitorDsl, cutoffHz: IgnitorDslLike, q: IgnitorDslLike = 1.0): IgnitorDsl =
        IgnitorDsl.Bandpass(inner = self, cutoffHz = cutoffHz.toIgnitorDsl(), q = q.toIgnitorDsl())

    /** SVF notch (band-reject) filter. Removes frequencies near the cutoff, passes others. */
    @KlangScript.Method
    fun notch(self: IgnitorDsl, cutoffHz: IgnitorDslLike, q: IgnitorDslLike = 1.0): IgnitorDsl =
        IgnitorDsl.Notch(inner = self, cutoffHz = cutoffHz.toIgnitorDsl(), q = q.toIgnitorDsl())

    // ── Envelope ─────────────────────────────────────────────────────────────

    /** Applies an ADSR amplitude envelope. All times accept Number or IgnitorDsl. */
    @KlangScript.Method
    fun adsr(
        self: IgnitorDsl,
        attackSec: IgnitorDslLike,
        decaySec: IgnitorDslLike,
        sustainLevel: IgnitorDslLike,
        releaseSec: IgnitorDslLike,
    ): IgnitorDsl = IgnitorDsl.Adsr(
        inner = self,
        attackSec = attackSec.toIgnitorDsl(),
        decaySec = decaySec.toIgnitorDsl(),
        sustainLevel = sustainLevel.toIgnitorDsl(),
        releaseSec = releaseSec.toIgnitorDsl(),
    )

    // ── Effects ──────────────────────────────────────────────────────────────

    /**
     * Pre-amplification stage. Boosts signal level before clipping.
     * Types: "linear".
     */
    @KlangScript.Method
    fun drive(self: IgnitorDsl, amount: IgnitorDslLike, driveType: String = "linear"): IgnitorDsl =
        IgnitorDsl.Drive(inner = self, amount = amount.toIgnitorDsl(), driveType = driveType)

    /**
     * Pure waveshaping without drive. Applies a nonlinear transfer function per sample.
     * Shapes: "soft" (tanh), "hard", "gentle", "cubic", "diode", "fold", "chebyshev", "rectify", "exp".
     * Oversample: user-facing factor (2 = 2x, 4 = 4x, 8 = 8x). 0/1 = off. Non-power-of-2 floored.
     */
    @KlangScript.Method
    fun clip(self: IgnitorDsl, shape: String = "soft", oversample: Int = 0): IgnitorDsl =
        IgnitorDsl.Clip(inner = self, shape = shape, oversample = oversample)

    /**
     * Waveshaping distortion. Convenience for drive(amount) + clip(shape).
     * Shapes: "soft" (tanh), "hard", "gentle", "cubic", "diode", "fold", "chebyshev", "rectify", "exp".
     * Oversample: user-facing factor (2 = 2x, 4 = 4x, 8 = 8x). 0/1 = off. Non-power-of-2 floored.
     */
    @KlangScript.Method
    fun distort(self: IgnitorDsl, amount: IgnitorDslLike, shape: String = "soft", oversample: Int = 0): IgnitorDsl =
        IgnitorDsl.Clip(
            inner = IgnitorDsl.Drive(inner = self, amount = amount.toIgnitorDsl()),
            shape = shape,
            oversample = oversample,
        )

    /** Applies bit-depth reduction (bitcrusher). */
    @KlangScript.Method
    fun crush(self: IgnitorDsl, amount: IgnitorDslLike): IgnitorDsl =
        IgnitorDsl.Crush(inner = self, amount = amount.toIgnitorDsl())

    /** Applies sample-rate reduction. */
    @KlangScript.Method
    fun coarse(self: IgnitorDsl, amount: IgnitorDslLike): IgnitorDsl =
        IgnitorDsl.Coarse(inner = self, amount = amount.toIgnitorDsl())

    /** Applies a multi-stage phaser effect. Blend: 0.0 = 100% dry (bypass), 1.0 = 100% wet (crossfade). */
    @KlangScript.Method
    fun phaser(
        self: IgnitorDsl,
        rate: IgnitorDslLike,
        blend: IgnitorDslLike = 0.5,
        center: IgnitorDslLike = 1000.0,
        sweep: IgnitorDslLike = 1000.0,
    ): IgnitorDsl = IgnitorDsl.Phaser(
        inner = self,
        rate = rate.toIgnitorDsl(),
        blend = blend.toIgnitorDsl(),
        center = center.toIgnitorDsl(),
        sweep = sweep.toIgnitorDsl(),
    )

    /** Applies amplitude tremolo. */
    @KlangScript.Method
    fun tremolo(self: IgnitorDsl, rate: IgnitorDslLike, depth: IgnitorDslLike): IgnitorDsl =
        IgnitorDsl.Tremolo(inner = self, rate = rate.toIgnitorDsl(), depth = depth.toIgnitorDsl())

    /**
     * Applies a granular shimmer cloud with configurable pitch transpositions and feedback.
     *
     * @param blend Crossfade: 0.0 = 100% dry (bypass), 1.0 = 100% wet (effect only). Default 0.5.
     * @param feedback Cascade feedback (0..0.95). Default 0.5.
     * @param tone Feedback-path LPF cutoff in Hz. Default 4000.
     * @param pitches Array of semitone transpositions. Default [0, 7, 12]. Example: [0, 4, 7, 11] for maj7.
     */
    @KlangScript.Method
    fun shimmer(
        self: IgnitorDsl,
        blend: IgnitorDslLike = 0.5,
        feedback: IgnitorDslLike = 0.5,
        tone: IgnitorDslLike = 4000.0,
        pitches: Any = listOf(0.0, 7.0, 12.0),
    ): IgnitorDsl {
        @Suppress("UNCHECKED_CAST")
        val pitchList = when (pitches) {
            is List<*> -> pitches.map { (it as Number).toDouble() }
            else -> listOf(0.0, 7.0, 12.0)
        }
        return IgnitorDsl.Shimmer(
            inner = self,
            blend = blend.toIgnitorDsl(),
            feedback = feedback.toIgnitorDsl(),
            pitches = pitchList,
            tone = tone.toIgnitorDsl(),
        )
    }

    // ── FM Synthesis ─────────────────────────────────────────────────────────

    /** Applies FM synthesis with a modulator ignitor. */
    @KlangScript.Method
    fun fm(self: IgnitorDsl, modulator: IgnitorDslLike, ratio: IgnitorDslLike, depth: IgnitorDslLike): IgnitorDsl =
        IgnitorDsl.Fm(
            carrier = self,
            modulator = modulator.toIgnitorDsl(),
            ratio = ratio.toIgnitorDsl(),
            depth = depth.toIgnitorDsl(),
        )

    // ── Pitch Modulation ─────────────────────────────────────────────────────

    /** Shifts pitch by semitones. */
    @KlangScript.Method
    fun detune(self: IgnitorDsl, semitones: IgnitorDslLike): IgnitorDsl =
        IgnitorDsl.Detune(inner = self, semitones = semitones.toIgnitorDsl())

    /** Shifts pitch up one octave (+12 semitones). */
    @KlangScript.Method
    fun octaveUp(self: IgnitorDsl): IgnitorDsl =
        IgnitorDsl.Detune(inner = self, semitones = IgnitorDsl.Constant(12.0))

    /** Shifts pitch down one octave (-12 semitones). */
    @KlangScript.Method
    fun octaveDown(self: IgnitorDsl): IgnitorDsl =
        IgnitorDsl.Detune(inner = self, semitones = IgnitorDsl.Constant(-12.0))

    /** Applies pitch vibrato. */
    @KlangScript.Method
    fun vibrato(self: IgnitorDsl, rate: IgnitorDslLike, depth: IgnitorDslLike): IgnitorDsl =
        IgnitorDsl.Vibrato(inner = self, rate = rate.toIgnitorDsl(), depth = depth.toIgnitorDsl())

    /** Applies continuous pitch acceleration over the voice duration. */
    @KlangScript.Method
    fun accelerate(self: IgnitorDsl, amount: IgnitorDslLike): IgnitorDsl =
        IgnitorDsl.Accelerate(inner = self, amount = amount.toIgnitorDsl())

    /**
     * Applies a custom pitch modulation from any Ignitor signal.
     * The mod signal uses deviation space: 0.0 = no change, positive = higher, negative = lower.
     */
    @KlangScript.Method
    fun pitchMod(self: IgnitorDsl, mod: IgnitorDslLike): IgnitorDsl =
        IgnitorDsl.PitchMod(inner = self, mod = mod.toIgnitorDsl())

    /** Applies a pitch envelope (pitch sweep over time). */
    @KlangScript.Method
    fun pitchEnvelope(
        self: IgnitorDsl,
        amount: IgnitorDslLike,
        attackSec: IgnitorDslLike = 0.01,
        decaySec: IgnitorDslLike = 0.1,
        releaseSec: IgnitorDslLike = 0.0,
    ): IgnitorDsl = IgnitorDsl.PitchEnvelope(
        inner = self,
        amount = amount.toIgnitorDsl(),
        attackSec = attackSec.toIgnitorDsl(),
        decaySec = decaySec.toIgnitorDsl(),
        releaseSec = releaseSec.toIgnitorDsl(),
    )

    // ── Analog Drift ────────────────────────────────────────────────────────

    /** Sets the analog drift amount (Perlin noise pitch jitter). */
    @KlangScript.Method
    fun analog(self: IgnitorDsl, amount: IgnitorDslLike): IgnitorDsl = when (self) {
        is IgnitorDsl.Sine -> self.copy(analog = amount.toIgnitorDsl())
        is IgnitorDsl.Sawtooth -> self.copy(analog = amount.toIgnitorDsl())
        is IgnitorDsl.Square -> self.copy(analog = amount.toIgnitorDsl())
        is IgnitorDsl.Triangle -> self.copy(analog = amount.toIgnitorDsl())
        is IgnitorDsl.Ramp -> self.copy(analog = amount.toIgnitorDsl())
        is IgnitorDsl.Zawtooth -> self.copy(analog = amount.toIgnitorDsl())
        is IgnitorDsl.Impulse -> self.copy(analog = amount.toIgnitorDsl())
        is IgnitorDsl.Pulze -> self.copy(analog = amount.toIgnitorDsl())
        is IgnitorDsl.SuperSaw -> self.copy(analog = amount.toIgnitorDsl())
        is IgnitorDsl.SuperSine -> self.copy(analog = amount.toIgnitorDsl())
        is IgnitorDsl.SuperSquare -> self.copy(analog = amount.toIgnitorDsl())
        is IgnitorDsl.SuperTri -> self.copy(analog = amount.toIgnitorDsl())
        is IgnitorDsl.SuperRamp -> self.copy(analog = amount.toIgnitorDsl())
        is IgnitorDsl.Pluck -> self.copy(analog = amount.toIgnitorDsl())
        is IgnitorDsl.SuperPluck -> self.copy(analog = amount.toIgnitorDsl())
        else -> self // no-op for types without analog drift (noise sources, wrappers, etc.)
    }

    // ── Arithmetic ───────────────────────────────────────────────────────────

    /**
     * Adds two ignitor signals together (summing).
     *
     * @alias add
     */
    @KlangScript.Method
    fun plus(self: IgnitorDsl, other: IgnitorDslLike): IgnitorDsl =
        IgnitorDsl.Plus(left = self, right = other.toIgnitorDsl())

    /**
     * Adds two ignitor signals together (summing). Alias for [plus].
     *
     * @alias plus
     */
    @KlangScript.Method
    fun add(self: IgnitorDsl, other: IgnitorDslLike): IgnitorDsl =
        IgnitorDsl.Plus(left = self, right = other.toIgnitorDsl())

    /**
     * Subtracts another signal from this one.
     *
     * @alias sub
     */
    @KlangScript.Method
    fun minus(self: IgnitorDsl, other: IgnitorDslLike): IgnitorDsl =
        IgnitorDsl.Minus(left = self, right = other.toIgnitorDsl())

    /**
     * Subtracts another signal from this one. Alias for [minus].
     *
     * @alias minus
     */
    @KlangScript.Method
    fun sub(self: IgnitorDsl, other: IgnitorDslLike): IgnitorDsl =
        IgnitorDsl.Minus(left = self, right = other.toIgnitorDsl())

    /**
     * Multiplies two ignitor signals (ring modulation / amplitude modulation).
     *
     * @alias mul
     */
    @KlangScript.Method
    fun times(self: IgnitorDsl, other: IgnitorDslLike): IgnitorDsl =
        IgnitorDsl.Times(left = self, right = other.toIgnitorDsl())

    /**
     * Scales the signal by a factor (IgnitorDsl or Number). Alias for [times].
     *
     * @alias times
     */
    @KlangScript.Method
    fun mul(self: IgnitorDsl, other: IgnitorDslLike): IgnitorDsl =
        IgnitorDsl.Times(left = self, right = other.toIgnitorDsl())

    /**
     * Divides the signal by a divisor (IgnitorDsl or Number).
     *
     * Zero divisors are substituted with `1e-30` to keep the engine `NaN`-free.
     */
    @KlangScript.Method
    fun div(self: IgnitorDsl, other: IgnitorDslLike): IgnitorDsl =
        IgnitorDsl.Div(left = self, right = other.toIgnitorDsl())

    /**
     * Negates this signal (flips polarity).
     *
     * @alias negate
     */
    @KlangScript.Method
    fun neg(self: IgnitorDsl): IgnitorDsl =
        IgnitorDsl.Neg(inner = self)

    /**
     * Negates this signal (flips polarity). Alias for [neg].
     *
     * @alias neg
     */
    @KlangScript.Method
    fun negate(self: IgnitorDsl): IgnitorDsl =
        IgnitorDsl.Neg(inner = self)

    /** Absolute value of this signal (full-wave rectification). */
    @KlangScript.Method
    fun abs(self: IgnitorDsl): IgnitorDsl =
        IgnitorDsl.Abs(inner = self)

    /**
     * Raises this signal to the power of [exp] (per-sample).
     * Signed-magnitude: negative bases produce `-(|base|^exp)` to avoid `NaN`.
     *
     * @alias power
     */
    @KlangScript.Method
    fun pow(self: IgnitorDsl, exp: IgnitorDslLike): IgnitorDsl =
        IgnitorDsl.Pow(base = self, exp = exp.toIgnitorDsl())

    /**
     * Raises this signal to the power of [exp]. Alias for [pow].
     *
     * @alias pow
     */
    @KlangScript.Method
    fun power(self: IgnitorDsl, exp: IgnitorDslLike): IgnitorDsl =
        IgnitorDsl.Pow(base = self, exp = exp.toIgnitorDsl())

    /** Per-sample minimum of this signal and [other]. */
    @KlangScript.Method
    fun min(self: IgnitorDsl, other: IgnitorDslLike): IgnitorDsl =
        IgnitorDsl.Min(left = self, right = other.toIgnitorDsl())

    /** Per-sample maximum of this signal and [other]. */
    @KlangScript.Method
    fun max(self: IgnitorDsl, other: IgnitorDslLike): IgnitorDsl =
        IgnitorDsl.Max(left = self, right = other.toIgnitorDsl())

    /** Bounds this signal to the range `[lo, hi]` per sample. */
    @KlangScript.Method
    fun clamp(self: IgnitorDsl, lo: IgnitorDslLike, hi: IgnitorDslLike): IgnitorDsl =
        IgnitorDsl.Clamp(inner = self, lo = lo.toIgnitorDsl(), hi = hi.toIgnitorDsl())

    /** `e^x` per sample. */
    @KlangScript.Method
    fun exp(self: IgnitorDsl): IgnitorDsl = IgnitorDsl.Exp(inner = self)

    /** Natural logarithm per sample. Signed-magnitude; `log(0) = 0` (no `-Inf`). */
    @KlangScript.Method
    fun log(self: IgnitorDsl): IgnitorDsl = IgnitorDsl.Log(inner = self)

    /** Square root per sample. Signed-magnitude (no `NaN` for negatives). */
    @KlangScript.Method
    fun sqrt(self: IgnitorDsl): IgnitorDsl = IgnitorDsl.Sqrt(inner = self)

    /** Sign of this signal: `-1`, `0`, or `+1`. */
    @KlangScript.Method
    fun sign(self: IgnitorDsl): IgnitorDsl = IgnitorDsl.Sign(inner = self)

    /** `tanh(x)` per sample (smooth saturation curve). */
    @KlangScript.Method
    fun tanh(self: IgnitorDsl): IgnitorDsl = IgnitorDsl.Tanh(inner = self)

    /**
     * Linear interpolation: `this·(1−t) + other·t`.
     *
     * @alias mix
     */
    @KlangScript.Method
    fun lerp(self: IgnitorDsl, other: IgnitorDslLike, t: IgnitorDslLike): IgnitorDsl =
        IgnitorDsl.Lerp(left = self, right = other.toIgnitorDsl(), t = t.toIgnitorDsl())

    /**
     * Crossfade: `this·(1−t) + other·t`. Alias for [lerp].
     *
     * @alias lerp
     */
    @KlangScript.Method
    fun mix(self: IgnitorDsl, other: IgnitorDslLike, t: IgnitorDslLike): IgnitorDsl =
        IgnitorDsl.Lerp(left = self, right = other.toIgnitorDsl(), t = t.toIgnitorDsl())

    /** Maps this signal from `[-1, 1]` to `[lo, hi]` per sample. Standard LFO scaler. */
    @KlangScript.Method
    fun range(self: IgnitorDsl, lo: IgnitorDslLike, hi: IgnitorDslLike): IgnitorDsl =
        IgnitorDsl.Range(inner = self, lo = lo.toIgnitorDsl(), hi = hi.toIgnitorDsl())

    /** Maps this signal from `[0, 1]` to `[-1, 1]` per sample. */
    @KlangScript.Method
    fun bipolar(self: IgnitorDsl): IgnitorDsl = IgnitorDsl.Bipolar(inner = self)

    /** Maps this signal from `[-1, 1]` to `[0, 1]` per sample. */
    @KlangScript.Method
    fun unipolar(self: IgnitorDsl): IgnitorDsl = IgnitorDsl.Unipolar(inner = self)

    /** Per-sample floor. */
    @KlangScript.Method
    fun floor(self: IgnitorDsl): IgnitorDsl = IgnitorDsl.Floor(inner = self)

    /** Per-sample ceiling. */
    @KlangScript.Method
    fun ceil(self: IgnitorDsl): IgnitorDsl = IgnitorDsl.Ceil(inner = self)

    /** Per-sample round to nearest integer. */
    @KlangScript.Method
    fun round(self: IgnitorDsl): IgnitorDsl = IgnitorDsl.Round(inner = self)

    /** Per-sample fractional part: `x − floor(x)`. */
    @KlangScript.Method
    fun frac(self: IgnitorDsl): IgnitorDsl = IgnitorDsl.Frac(inner = self)

    /**
     * Per-sample modulo. Zero divisors substituted with `1e-30` to avoid `NaN`.
     *
     * @alias rem
     */
    @KlangScript.Method
    fun mod(self: IgnitorDsl, other: IgnitorDslLike): IgnitorDsl =
        IgnitorDsl.Mod(left = self, right = other.toIgnitorDsl())

    /**
     * Per-sample modulo. Alias for [mod] (matches Kotlin's `rem`).
     *
     * @alias mod
     */
    @KlangScript.Method
    fun rem(self: IgnitorDsl, other: IgnitorDslLike): IgnitorDsl =
        IgnitorDsl.Mod(left = self, right = other.toIgnitorDsl())

    /**
     * Per-sample reciprocal: `1 / x`. Zero inputs substituted with `1e-30` to avoid `NaN`.
     *
     * @alias reciprocal
     */
    @KlangScript.Method
    fun recip(self: IgnitorDsl): IgnitorDsl = IgnitorDsl.Recip(inner = self)

    /**
     * Per-sample reciprocal: `1 / x`. Alias for [recip].
     *
     * @alias recip
     */
    @KlangScript.Method
    fun reciprocal(self: IgnitorDsl): IgnitorDsl = IgnitorDsl.Recip(inner = self)

    /** Per-sample square: `x · x`. */
    @KlangScript.Method
    fun sq(self: IgnitorDsl): IgnitorDsl = IgnitorDsl.Sq(inner = self)

    /** Per-sample conditional: when this signal `> 0` use [whenTrue], else [whenFalse]. */
    @KlangScript.Method
    fun select(self: IgnitorDsl, whenTrue: IgnitorDslLike, whenFalse: IgnitorDslLike): IgnitorDsl =
        IgnitorDsl.Select(cond = self, whenTrue = whenTrue.toIgnitorDsl(), whenFalse = whenFalse.toIgnitorDsl())
}
