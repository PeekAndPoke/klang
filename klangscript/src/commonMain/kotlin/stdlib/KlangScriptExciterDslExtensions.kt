package io.peekandpoke.klang.script.stdlib

import io.peekandpoke.klang.audio_bridge.*
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.annotations.KlangScriptLibraries

/**
 * Accepts [ExciterDsl] or [Number]. Numbers are converted to [ExciterDsl.Param] automatically.
 */
typealias ExciterDslLike = Any

/** Converts an [ExciterDslLike] value to [ExciterDsl]. Numbers become [ExciterDsl.Param]. */
private fun ExciterDslLike.toExciterDsl(name: String = ""): ExciterDsl = when (this) {
    is ExciterDsl -> this
    is Number -> ExciterDsl.Param(name, this.toDouble())
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
    fun lowpass(self: ExciterDsl, cutoffHz: Any, q: Any = 0.707): ExciterDsl =
        ExciterDsl.Lowpass(self, cutoffHz.toExciterDsl("cutoffHz"), q.toExciterDsl("q"))

    /** Applies a resonant highpass filter. Cutoff and Q accept Number or ExciterDsl. */
    @KlangScript.Method
    fun highpass(self: ExciterDsl, cutoffHz: Any, q: Any = 0.707): ExciterDsl =
        ExciterDsl.Highpass(self, cutoffHz.toExciterDsl("cutoffHz"), q.toExciterDsl("q"))

    /** Applies a one-pole lowpass filter (gentle rolloff). Alias: onePoleLowpass. */
    @KlangScript.Method
    fun warmth(self: ExciterDsl, cutoffHz: Any): ExciterDsl =
        ExciterDsl.OnePoleLowpass(self, cutoffHz.toExciterDsl("cutoffHz"))

    /** Applies a one-pole lowpass filter (gentle rolloff). Alias for warmth. */
    @KlangScript.Method
    fun onePoleLowpass(self: ExciterDsl, cutoffHz: Any): ExciterDsl =
        ExciterDsl.OnePoleLowpass(self, cutoffHz.toExciterDsl("cutoffHz"))

    // ── Envelope ─────────────────────────────────────────────────────────────

    /** Applies an ADSR amplitude envelope. All times accept Number or ExciterDsl. */
    @KlangScript.Method
    fun adsr(self: ExciterDsl, attackSec: Any, decaySec: Any, sustainLevel: Any, releaseSec: Any): ExciterDsl =
        ExciterDsl.Adsr(
            self,
            attackSec.toExciterDsl("attackSec"),
            decaySec.toExciterDsl("decaySec"),
            sustainLevel.toExciterDsl("sustainLevel"),
            releaseSec.toExciterDsl("releaseSec"),
        )

    // ── Effects ──────────────────────────────────────────────────────────────

    /** Applies waveshaping distortion. */
    @KlangScript.Method
    fun distort(self: ExciterDsl, amount: Any, shape: String = "soft"): ExciterDsl =
        ExciterDsl.Distort(self, amount.toExciterDsl("amount"), shape)

    /** Applies bit-depth reduction (bitcrusher). */
    @KlangScript.Method
    fun crush(self: ExciterDsl, amount: Any): ExciterDsl =
        ExciterDsl.Crush(self, amount.toExciterDsl("amount"))

    /** Applies sample-rate reduction. */
    @KlangScript.Method
    fun coarse(self: ExciterDsl, amount: Any): ExciterDsl =
        ExciterDsl.Coarse(self, amount.toExciterDsl("amount"))

    /** Applies a multi-stage phaser effect. */
    @KlangScript.Method
    fun phaser(self: ExciterDsl, rate: Any, depth: Any, center: Any = 1000.0, sweep: Any = 1000.0): ExciterDsl =
        ExciterDsl.Phaser(
            self,
            rate.toExciterDsl("rate"),
            depth.toExciterDsl("depth"),
            center.toExciterDsl("center"),
            sweep.toExciterDsl("sweep")
        )

    /** Applies amplitude tremolo. */
    @KlangScript.Method
    fun tremolo(self: ExciterDsl, rate: Any, depth: Any): ExciterDsl =
        ExciterDsl.Tremolo(self, rate.toExciterDsl("rate"), depth.toExciterDsl("depth"))

    // ── FM Synthesis ─────────────────────────────────────────────────────────

    /** Applies FM synthesis with a modulator exciter. */
    @KlangScript.Method
    fun fm(self: ExciterDsl, modulator: ExciterDsl, ratio: Any, depth: Any): ExciterDsl =
        ExciterDsl.Fm(
            carrier = self,
            modulator = modulator,
            ratio = ratio.toExciterDsl("ratio"),
            depth = depth.toExciterDsl("depth"),
        )

    // ── Pitch Modulation ─────────────────────────────────────────────────────

    /** Shifts pitch by semitones. */
    @KlangScript.Method
    fun detune(self: ExciterDsl, semitones: Any): ExciterDsl =
        ExciterDsl.Detune(self, semitones.toExciterDsl("semitones"))

    /** Shifts pitch up one octave. */
    @KlangScript.Method
    fun octaveUp(self: ExciterDsl): ExciterDsl =
        self.octaveUp()

    /** Shifts pitch down one octave. */
    @KlangScript.Method
    fun octaveDown(self: ExciterDsl): ExciterDsl =
        self.octaveDown()

    /** Applies pitch vibrato. */
    @KlangScript.Method
    fun vibrato(self: ExciterDsl, rate: Any, depth: Any): ExciterDsl =
        ExciterDsl.Vibrato(self, rate.toExciterDsl("rate"), depth.toExciterDsl("depth"))

    /** Applies continuous pitch acceleration over the voice duration. */
    @KlangScript.Method
    fun accelerate(self: ExciterDsl, amount: Any): ExciterDsl =
        ExciterDsl.Accelerate(self, amount.toExciterDsl("amount"))

    // ── Arithmetic ───────────────────────────────────────────────────────────

    /** Adds two exciter signals together (summing). */
    @KlangScript.Method
    fun plus(self: ExciterDsl, other: ExciterDsl): ExciterDsl =
        self + other

    /** Multiplies two exciter signals (ring modulation / amplitude modulation). */
    @KlangScript.Method
    fun times(self: ExciterDsl, other: ExciterDsl): ExciterDsl =
        self * other

    /** Scales the signal by a factor (ExciterDsl or Number). */
    @KlangScript.Method
    fun mul(self: ExciterDsl, other: Any): ExciterDsl =
        self.mul(other.toExciterDsl("factor"))

    /** Divides the signal by a divisor (ExciterDsl or Number). */
    @KlangScript.Method
    fun div(self: ExciterDsl, other: Any): ExciterDsl =
        self.div(other.toExciterDsl("divisor"))
}
