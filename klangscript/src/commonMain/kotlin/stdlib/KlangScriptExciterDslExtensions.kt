package io.peekandpoke.klang.script.stdlib

import io.peekandpoke.klang.audio_bridge.*
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
object KlangScriptExciterDslExtensions {

    // ── Filters ──────────────────────────────────────────────────────────────

    /** Applies a resonant lowpass filter. Cutoff and Q accept Number or ExciterDsl. */
    @KlangScript.Method
    fun lowpass(self: ExciterDsl, cutoffHz: ExciterDslLike, q: ExciterDslLike = 0.707): ExciterDsl =
        ExciterDsl.Lowpass(self, cutoffHz.toExciterDsl(), q.toExciterDsl())

    /** Applies a resonant highpass filter. Cutoff and Q accept Number or ExciterDsl. */
    @KlangScript.Method
    fun highpass(self: ExciterDsl, cutoffHz: ExciterDslLike, q: ExciterDslLike = 0.707): ExciterDsl =
        ExciterDsl.Highpass(self, cutoffHz.toExciterDsl(), q.toExciterDsl())

    /** Applies a one-pole lowpass filter (gentle rolloff). Alias: onePoleLowpass. */
    @KlangScript.Method
    fun warmth(self: ExciterDsl, cutoffHz: ExciterDslLike): ExciterDsl =
        ExciterDsl.OnePoleLowpass(self, cutoffHz.toExciterDsl())

    /** Applies a one-pole lowpass filter (gentle rolloff). Alias for warmth. */
    @KlangScript.Method
    fun onePoleLowpass(self: ExciterDsl, cutoffHz: ExciterDslLike): ExciterDsl =
        ExciterDsl.OnePoleLowpass(self, cutoffHz.toExciterDsl())

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
        self,
        attackSec.toExciterDsl(),
        decaySec.toExciterDsl(),
        sustainLevel.toExciterDsl(),
        releaseSec.toExciterDsl(),
    )

    // ── Effects ──────────────────────────────────────────────────────────────

    /** Applies waveshaping distortion. */
    @KlangScript.Method
    fun distort(self: ExciterDsl, amount: ExciterDslLike, shape: String = "soft"): ExciterDsl =
        ExciterDsl.Distort(self, amount.toExciterDsl(), shape)

    /** Applies bit-depth reduction (bitcrusher). */
    @KlangScript.Method
    fun crush(self: ExciterDsl, amount: ExciterDslLike): ExciterDsl =
        ExciterDsl.Crush(self, amount.toExciterDsl())

    /** Applies sample-rate reduction. */
    @KlangScript.Method
    fun coarse(self: ExciterDsl, amount: ExciterDslLike): ExciterDsl =
        ExciterDsl.Coarse(self, amount.toExciterDsl())

    /** Applies a multi-stage phaser effect. */
    @KlangScript.Method
    fun phaser(
        self: ExciterDsl,
        rate: ExciterDslLike,
        depth: ExciterDslLike,
        center: ExciterDslLike = 1000.0,
        sweep: ExciterDslLike = 1000.0,
    ): ExciterDsl = ExciterDsl.Phaser(
        self,
        rate.toExciterDsl(),
        depth.toExciterDsl(),
        center.toExciterDsl(),
        sweep.toExciterDsl(),
    )

    /** Applies amplitude tremolo. */
    @KlangScript.Method
    fun tremolo(self: ExciterDsl, rate: ExciterDslLike, depth: ExciterDslLike): ExciterDsl =
        ExciterDsl.Tremolo(self, rate.toExciterDsl(), depth.toExciterDsl())

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
        ExciterDsl.Detune(self, semitones.toExciterDsl())

    /** Shifts pitch up one octave (+12 semitones). */
    @KlangScript.Method
    fun octaveUp(self: ExciterDsl): ExciterDsl =
        ExciterDsl.Detune(self, ExciterDsl.Constant(12.0))

    /** Shifts pitch down one octave (-12 semitones). */
    @KlangScript.Method
    fun octaveDown(self: ExciterDsl): ExciterDsl =
        ExciterDsl.Detune(self, ExciterDsl.Constant(-12.0))

    /** Applies pitch vibrato. */
    @KlangScript.Method
    fun vibrato(self: ExciterDsl, rate: ExciterDslLike, depth: ExciterDslLike): ExciterDsl =
        ExciterDsl.Vibrato(self, rate.toExciterDsl(), depth.toExciterDsl())

    /** Applies continuous pitch acceleration over the voice duration. */
    @KlangScript.Method
    fun accelerate(self: ExciterDsl, amount: ExciterDslLike): ExciterDsl =
        ExciterDsl.Accelerate(self, amount.toExciterDsl())

    // ── Arithmetic ───────────────────────────────────────────────────────────

    /** Adds two exciter signals together (summing). */
    @KlangScript.Method
    fun plus(self: ExciterDsl, other: ExciterDslLike): ExciterDsl =
        self + other.toExciterDsl()

    /** Multiplies two exciter signals (ring modulation / amplitude modulation). */
    @KlangScript.Method
    fun times(self: ExciterDsl, other: ExciterDslLike): ExciterDsl =
        self * other.toExciterDsl()

    /** Scales the signal by a factor (ExciterDsl or Number). */
    @KlangScript.Method
    fun mul(self: ExciterDsl, other: ExciterDslLike): ExciterDsl =
        self.mul(other.toExciterDsl())

    /** Divides the signal by a divisor (ExciterDsl or Number). */
    @KlangScript.Method
    fun div(self: ExciterDsl, other: ExciterDslLike): ExciterDsl =
        self.div(other.toExciterDsl())
}