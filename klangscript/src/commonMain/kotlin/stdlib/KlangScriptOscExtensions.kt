package io.peekandpoke.klang.script.stdlib

import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.annotations.KlangScriptLibraries

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

    /** Applies a multi-stage phaser effect. */
    @KlangScript.Method
    fun phaser(
        self: IgnitorDsl,
        rate: IgnitorDslLike,
        depth: IgnitorDslLike,
        center: IgnitorDslLike = 1000.0,
        sweep: IgnitorDslLike = 1000.0,
    ): IgnitorDsl = IgnitorDsl.Phaser(
        inner = self,
        rate = rate.toIgnitorDsl(),
        depth = depth.toIgnitorDsl(),
        center = center.toIgnitorDsl(),
        sweep = sweep.toIgnitorDsl(),
    )

    /** Applies amplitude tremolo. */
    @KlangScript.Method
    fun tremolo(self: IgnitorDsl, rate: IgnitorDslLike, depth: IgnitorDslLike): IgnitorDsl =
        IgnitorDsl.Tremolo(inner = self, rate = rate.toIgnitorDsl(), depth = depth.toIgnitorDsl())

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

    /** Adds two ignitor signals together (summing). */
    @KlangScript.Method
    fun plus(self: IgnitorDsl, other: IgnitorDslLike): IgnitorDsl =
        IgnitorDsl.Plus(left = self, right = other.toIgnitorDsl())

    /** Subtracts another signal from this one. */
    @KlangScript.Method
    fun minus(self: IgnitorDsl, other: IgnitorDslLike): IgnitorDsl =
        IgnitorDsl.Plus(left = self, right = IgnitorDsl.Mul(left = other.toIgnitorDsl(), right = IgnitorDsl.Constant(-1.0)))

    /** Multiplies two ignitor signals (ring modulation / amplitude modulation). */
    @KlangScript.Method
    fun times(self: IgnitorDsl, other: IgnitorDslLike): IgnitorDsl =
        IgnitorDsl.Times(left = self, right = other.toIgnitorDsl())

    /** Scales the signal by a factor (IgnitorDsl or Number). */
    @KlangScript.Method
    fun mul(self: IgnitorDsl, other: IgnitorDslLike): IgnitorDsl =
        IgnitorDsl.Mul(left = self, right = other.toIgnitorDsl())

    /** Divides the signal by a divisor (IgnitorDsl or Number). */
    @KlangScript.Method
    fun div(self: IgnitorDsl, other: IgnitorDslLike): IgnitorDsl =
        IgnitorDsl.Div(left = self, right = other.toIgnitorDsl())
}
