package io.peekandpoke.klang.script.stdlib

import io.peekandpoke.klang.audio_bridge.*
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.annotations.KlangScriptLibraries

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

    /** Applies a resonant lowpass filter. */
    @KlangScript.Method
    fun lowpass(self: ExciterDsl, cutoffHz: Double, q: Double = 0.707): ExciterDsl =
        self.lowpass(cutoffHz, q)

    /** Applies a resonant lowpass filter with modulatable cutoff. */
    @KlangScript.Method(name = "lowpass")
    fun lowpassMod(self: ExciterDsl, cutoffHz: ExciterDsl): ExciterDsl =
        ExciterDsl.Lowpass(self, cutoffHz)

    /** Applies a resonant highpass filter. */
    @KlangScript.Method
    fun highpass(self: ExciterDsl, cutoffHz: Double, q: Double = 0.707): ExciterDsl =
        self.highpass(cutoffHz, q)

    /** Applies a one-pole lowpass filter (gentle rolloff). */
    @KlangScript.Method
    fun onePoleLowpass(self: ExciterDsl, cutoffHz: Double): ExciterDsl =
        self.onePoleLowpass(cutoffHz)

    // ── Envelope ─────────────────────────────────────────────────────────────

    /** Applies an ADSR amplitude envelope. */
    @KlangScript.Method
    fun adsr(self: ExciterDsl, attackSec: Double, decaySec: Double, sustainLevel: Double, releaseSec: Double): ExciterDsl =
        self.adsr(attackSec, decaySec, sustainLevel, releaseSec)

    // ── Effects ──────────────────────────────────────────────────────────────

    /** Applies waveshaping distortion. */
    @KlangScript.Method
    fun distort(self: ExciterDsl, amount: Double, shape: String = "soft"): ExciterDsl =
        self.distort(amount, shape)

    /** Applies bit-depth reduction (bitcrusher). */
    @KlangScript.Method
    fun crush(self: ExciterDsl, amount: Double): ExciterDsl =
        self.crush(amount)

    /** Applies sample-rate reduction. */
    @KlangScript.Method
    fun coarse(self: ExciterDsl, amount: Double): ExciterDsl =
        self.coarse(amount)

    /** Applies a multi-stage phaser effect. */
    @KlangScript.Method
    fun phaser(self: ExciterDsl, rate: Double, depth: Double, center: Double = 1000.0, sweep: Double = 1000.0): ExciterDsl =
        self.phaser(rate, depth, center, sweep)

    /** Applies amplitude tremolo. */
    @KlangScript.Method
    fun tremolo(self: ExciterDsl, rate: Double, depth: Double): ExciterDsl =
        self.tremolo(rate, depth)

    // ── FM Synthesis ─────────────────────────────────────────────────────────

    /** Applies FM synthesis with a modulator exciter. */
    @KlangScript.Method
    fun fm(self: ExciterDsl, modulator: ExciterDsl, ratio: Double, depth: Double): ExciterDsl =
        self.fm(modulator, ratio, depth)

    /** Applies FM synthesis with a modulator exciter and envelope control. */
    @KlangScript.Method(name = "fm")
    fun fmEnv(self: ExciterDsl, modulator: ExciterDsl, ratio: Double, depth: Double, envDecaySec: Double): ExciterDsl =
        self.fm(modulator, ratio, depth, envDecaySec = envDecaySec, envSustainLevel = 0.0)

    // ── Pitch Modulation ─────────────────────────────────────────────────────

    /** Shifts pitch by semitones. */
    @KlangScript.Method
    fun detune(self: ExciterDsl, semitones: Double): ExciterDsl =
        self.detune(semitones)

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
    fun vibrato(self: ExciterDsl, rate: Double, depth: Double): ExciterDsl =
        self.vibrato(rate, depth)

    /** Applies continuous pitch acceleration over the voice duration. */
    @KlangScript.Method
    fun accelerate(self: ExciterDsl, amount: Double): ExciterDsl =
        self.accelerate(amount)

    // ── Arithmetic ───────────────────────────────────────────────────────────

    /** Adds two exciter signals together (summing). */
    @KlangScript.Method
    fun plus(self: ExciterDsl, other: ExciterDsl): ExciterDsl =
        self + other

    /** Multiplies two exciter signals (ring modulation / amplitude modulation). */
    @KlangScript.Method
    fun times(self: ExciterDsl, other: ExciterDsl): ExciterDsl =
        self * other

    /** Scales the signal by a factor. */
    @KlangScript.Method
    fun mul(self: ExciterDsl, other: ExciterDsl): ExciterDsl =
        self.mul(other)

    /** Scales the signal by a constant factor. */
    @KlangScript.Method(name = "mul")
    fun mulConst(self: ExciterDsl, factor: Double): ExciterDsl =
        self.mul(ExciterDsl.Param("factor", factor))

    /** Divides the signal by a divisor. */
    @KlangScript.Method
    fun div(self: ExciterDsl, other: ExciterDsl): ExciterDsl =
        self.div(other)

    /** Divides the signal by a constant divisor. */
    @KlangScript.Method(name = "div")
    fun divConst(self: ExciterDsl, divisor: Double): ExciterDsl =
        self.div(ExciterDsl.Param("divisor", divisor))
}
