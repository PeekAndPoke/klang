package io.peekandpoke.klang.script.stdlib

import io.peekandpoke.klang.audio_bridge.ExciterDsl
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.annotations.KlangScriptLibraries

/**
 * Accepts [ExciterDsl] or [Number]. Numbers are converted to [ExciterDsl.Constant] automatically.
 */
typealias ExciterDslLike = Any

/** Converts an [ExciterDslLike] value to [ExciterDsl]. Numbers become [ExciterDsl.Constant] (not overridable by oscParams). */
fun ExciterDslLike.toExciterDsl(): ExciterDsl = when (this) {
    is ExciterDsl -> this
    is Number -> ExciterDsl.Constant(this.toDouble())
    else -> error("Expected ExciterDsl or Number, got ${this::class.simpleName}")
}

/**
 * Extension methods on [ExciterDsl] for KlangScript.
 *
 * Enables chaining: `Osc.sine().lowpass(1000).adsr(0.01, 0.1, 0.5, 0.3)`
 * Any numeric parameter also accepts an ExciterDsl for audio-rate modulation:
 * `Osc.sine().lowpass(Osc.perlin())` — modulated cutoff.
 */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.TypeExtensions(ExciterDsl::class)
object KlangScriptOscExtensions {

    // ── Filters ──────────────────────────────────────────────────────────────

    /** Applies a resonant lowpass filter. Cutoff and Q accept Number or ExciterDsl. */
    @KlangScript.Method
    fun lowpass(self: ExciterDsl, cutoffHz: ExciterDslLike, q: ExciterDslLike = 0.707): ExciterDsl =
        ExciterDsl.Lowpass(inner = self, cutoffHz = cutoffHz.toExciterDsl(), q = q.toExciterDsl())

    /** Applies a resonant highpass filter. Cutoff and Q accept Number or ExciterDsl. */
    @KlangScript.Method
    fun highpass(self: ExciterDsl, cutoffHz: ExciterDslLike, q: ExciterDslLike = 0.707): ExciterDsl =
        ExciterDsl.Highpass(inner = self, cutoffHz = cutoffHz.toExciterDsl(), q = q.toExciterDsl())

    /** Applies a one-pole lowpass filter (gentle rolloff). Alias: onePoleLowpass. */
    @KlangScript.Method
    fun warmth(self: ExciterDsl, cutoffHz: ExciterDslLike): ExciterDsl =
        ExciterDsl.OnePoleLowpass(inner = self, cutoffHz = cutoffHz.toExciterDsl())

    /** Applies a one-pole lowpass filter (gentle rolloff). Alias for warmth. */
    @KlangScript.Method
    fun onePoleLowpass(self: ExciterDsl, cutoffHz: ExciterDslLike): ExciterDsl =
        ExciterDsl.OnePoleLowpass(inner = self, cutoffHz = cutoffHz.toExciterDsl())

    /** SVF bandpass filter. Passes frequencies near the cutoff, attenuates others. */
    @KlangScript.Method
    fun bandpass(self: ExciterDsl, cutoffHz: ExciterDslLike, q: ExciterDslLike = 1.0): ExciterDsl =
        ExciterDsl.Bandpass(inner = self, cutoffHz = cutoffHz.toExciterDsl(), q = q.toExciterDsl())

    /** SVF notch (band-reject) filter. Removes frequencies near the cutoff, passes others. */
    @KlangScript.Method
    fun notch(self: ExciterDsl, cutoffHz: ExciterDslLike, q: ExciterDslLike = 1.0): ExciterDsl =
        ExciterDsl.Notch(inner = self, cutoffHz = cutoffHz.toExciterDsl(), q = q.toExciterDsl())

    // ── Envelope ─────────────────────────────────────────────────────────────

    /** Applies an ADSR amplitude envelope. All times accept Number or ExciterDsl. */
    @KlangScript.Method
    fun adsr(
        self: ExciterDsl,
        attackSec: ExciterDslLike,
        decaySec: ExciterDslLike,
        sustainLevel: ExciterDslLike,
        releaseSec: ExciterDslLike,
    ): ExciterDsl = ExciterDsl.Adsr(
        inner = self,
        attackSec = attackSec.toExciterDsl(),
        decaySec = decaySec.toExciterDsl(),
        sustainLevel = sustainLevel.toExciterDsl(),
        releaseSec = releaseSec.toExciterDsl(),
    )

    // ── Effects ──────────────────────────────────────────────────────────────

    /**
     * Pre-amplification stage. Boosts signal level before clipping.
     * Types: "linear".
     */
    @KlangScript.Method
    fun drive(self: ExciterDsl, amount: ExciterDslLike, driveType: String = "linear"): ExciterDsl =
        ExciterDsl.Drive(inner = self, amount = amount.toExciterDsl(), driveType = driveType)

    /**
     * Pure waveshaping without drive. Applies a nonlinear transfer function per sample.
     * Shapes: "soft" (tanh), "hard", "gentle", "cubic", "diode", "fold", "chebyshev", "rectify", "exp".
     */
    @KlangScript.Method
    fun clip(self: ExciterDsl, shape: String = "soft"): ExciterDsl =
        ExciterDsl.Clip(inner = self, shape = shape)

    /**
     * Waveshaping distortion. Convenience for drive(amount) + clip(shape).
     * Shapes: "soft" (tanh), "hard", "gentle", "cubic", "diode", "fold", "chebyshev", "rectify", "exp".
     */
    @KlangScript.Method
    fun distort(self: ExciterDsl, amount: ExciterDslLike, shape: String = "soft"): ExciterDsl =
        ExciterDsl.Clip(inner = ExciterDsl.Drive(inner = self, amount = amount.toExciterDsl()), shape = shape)

    /** Applies bit-depth reduction (bitcrusher). */
    @KlangScript.Method
    fun crush(self: ExciterDsl, amount: ExciterDslLike): ExciterDsl =
        ExciterDsl.Crush(inner = self, amount = amount.toExciterDsl())

    /** Applies sample-rate reduction. */
    @KlangScript.Method
    fun coarse(self: ExciterDsl, amount: ExciterDslLike): ExciterDsl =
        ExciterDsl.Coarse(inner = self, amount = amount.toExciterDsl())

    /** Applies a multi-stage phaser effect. */
    @KlangScript.Method
    fun phaser(
        self: ExciterDsl,
        rate: ExciterDslLike,
        depth: ExciterDslLike,
        center: ExciterDslLike = 1000.0,
        sweep: ExciterDslLike = 1000.0,
    ): ExciterDsl = ExciterDsl.Phaser(
        inner = self,
        rate = rate.toExciterDsl(),
        depth = depth.toExciterDsl(),
        center = center.toExciterDsl(),
        sweep = sweep.toExciterDsl(),
    )

    /** Applies amplitude tremolo. */
    @KlangScript.Method
    fun tremolo(self: ExciterDsl, rate: ExciterDslLike, depth: ExciterDslLike): ExciterDsl =
        ExciterDsl.Tremolo(inner = self, rate = rate.toExciterDsl(), depth = depth.toExciterDsl())

    // ── FM Synthesis ─────────────────────────────────────────────────────────

    /** Applies FM synthesis with a modulator exciter. */
    @KlangScript.Method
    fun fm(self: ExciterDsl, modulator: ExciterDslLike, ratio: ExciterDslLike, depth: ExciterDslLike): ExciterDsl =
        ExciterDsl.Fm(
            carrier = self,
            modulator = modulator.toExciterDsl(),
            ratio = ratio.toExciterDsl(),
            depth = depth.toExciterDsl(),
        )

    // ── Pitch Modulation ─────────────────────────────────────────────────────

    /** Shifts pitch by semitones. */
    @KlangScript.Method
    fun detune(self: ExciterDsl, semitones: ExciterDslLike): ExciterDsl =
        ExciterDsl.Detune(inner = self, semitones = semitones.toExciterDsl())

    /** Shifts pitch up one octave (+12 semitones). */
    @KlangScript.Method
    fun octaveUp(self: ExciterDsl): ExciterDsl =
        ExciterDsl.Detune(inner = self, semitones = ExciterDsl.Constant(12.0))

    /** Shifts pitch down one octave (-12 semitones). */
    @KlangScript.Method
    fun octaveDown(self: ExciterDsl): ExciterDsl =
        ExciterDsl.Detune(inner = self, semitones = ExciterDsl.Constant(-12.0))

    /** Applies pitch vibrato. */
    @KlangScript.Method
    fun vibrato(self: ExciterDsl, rate: ExciterDslLike, depth: ExciterDslLike): ExciterDsl =
        ExciterDsl.Vibrato(inner = self, rate = rate.toExciterDsl(), depth = depth.toExciterDsl())

    /** Applies continuous pitch acceleration over the voice duration. */
    @KlangScript.Method
    fun accelerate(self: ExciterDsl, amount: ExciterDslLike): ExciterDsl =
        ExciterDsl.Accelerate(inner = self, amount = amount.toExciterDsl())

    /** Applies a pitch envelope (pitch sweep over time). */
    @KlangScript.Method
    fun pitchEnvelope(
        self: ExciterDsl,
        amount: ExciterDslLike,
        attackSec: ExciterDslLike = 0.01,
        decaySec: ExciterDslLike = 0.1,
        releaseSec: ExciterDslLike = 0.0,
    ): ExciterDsl = ExciterDsl.PitchEnvelope(
        inner = self,
        amount = amount.toExciterDsl(),
        attackSec = attackSec.toExciterDsl(),
        decaySec = decaySec.toExciterDsl(),
        releaseSec = releaseSec.toExciterDsl(),
    )

    // ── Analog Drift ────────────────────────────────────────────────────────

    /** Sets the analog drift amount (Perlin noise pitch jitter). */
    @KlangScript.Method
    fun analog(self: ExciterDsl, amount: ExciterDslLike): ExciterDsl = when (self) {
        is ExciterDsl.Sine -> self.copy(analog = amount.toExciterDsl())
        is ExciterDsl.Sawtooth -> self.copy(analog = amount.toExciterDsl())
        is ExciterDsl.Square -> self.copy(analog = amount.toExciterDsl())
        is ExciterDsl.Triangle -> self.copy(analog = amount.toExciterDsl())
        is ExciterDsl.Ramp -> self.copy(analog = amount.toExciterDsl())
        is ExciterDsl.Zawtooth -> self.copy(analog = amount.toExciterDsl())
        is ExciterDsl.Impulse -> self.copy(analog = amount.toExciterDsl())
        is ExciterDsl.Pulze -> self.copy(analog = amount.toExciterDsl())
        is ExciterDsl.SuperSaw -> self.copy(analog = amount.toExciterDsl())
        is ExciterDsl.SuperSine -> self.copy(analog = amount.toExciterDsl())
        is ExciterDsl.SuperSquare -> self.copy(analog = amount.toExciterDsl())
        is ExciterDsl.SuperTri -> self.copy(analog = amount.toExciterDsl())
        is ExciterDsl.SuperRamp -> self.copy(analog = amount.toExciterDsl())
        is ExciterDsl.Pluck -> self.copy(analog = amount.toExciterDsl())
        is ExciterDsl.SuperPluck -> self.copy(analog = amount.toExciterDsl())
        else -> self // no-op for types without analog drift (noise sources, wrappers, etc.)
    }

    // ── Arithmetic ───────────────────────────────────────────────────────────

    /** Adds two exciter signals together (summing). */
    @KlangScript.Method
    fun plus(self: ExciterDsl, other: ExciterDslLike): ExciterDsl =
        ExciterDsl.Plus(left = self, right = other.toExciterDsl())

    /** Subtracts another signal from this one. */
    @KlangScript.Method
    fun minus(self: ExciterDsl, other: ExciterDslLike): ExciterDsl =
        ExciterDsl.Plus(left = self, right = ExciterDsl.Mul(left = other.toExciterDsl(), right = ExciterDsl.Constant(-1.0)))

    /** Multiplies two exciter signals (ring modulation / amplitude modulation). */
    @KlangScript.Method
    fun times(self: ExciterDsl, other: ExciterDslLike): ExciterDsl =
        ExciterDsl.Times(left = self, right = other.toExciterDsl())

    /** Scales the signal by a factor (ExciterDsl or Number). */
    @KlangScript.Method
    fun mul(self: ExciterDsl, other: ExciterDslLike): ExciterDsl =
        ExciterDsl.Mul(left = self, right = other.toExciterDsl())

    /** Divides the signal by a divisor (ExciterDsl or Number). */
    @KlangScript.Method
    fun div(self: ExciterDsl, other: ExciterDslLike): ExciterDsl =
        ExciterDsl.Div(left = self, right = other.toExciterDsl())
}
