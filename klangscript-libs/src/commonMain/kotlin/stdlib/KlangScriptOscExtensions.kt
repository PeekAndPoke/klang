/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.peekandpoke.klang.audio_bridge.AdsrCurve
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.coercePasses
import io.peekandpoke.klang.audio_bridge.bandpass
import io.peekandpoke.klang.audio_bridge.highpass
import io.peekandpoke.klang.audio_bridge.lowpass
import io.peekandpoke.klang.audio_bridge.notch
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.annotations.KlangScriptLibraries
import io.peekandpoke.klang.script.runtime.KlangScriptTypeError

/**
 * Accepts [IgnitorDsl] or [Number]. Numbers are converted to [IgnitorDsl.Constant] automatically.
 */
typealias IgnitorDslLike = Any

/**
 * Converts an [IgnitorDslLike] value to [IgnitorDsl]. Numbers become [IgnitorDsl.Constant] (not
 * overridable by oscParams). Anything else is a script-level type error naming what arrived, so a
 * lambda that landed on a sound slot (`Osc.whitenoise(x => ...)`, which has no `configure`) reads
 * as "got a function", not as an internal error.
 */
fun IgnitorDslLike.toIgnitorDsl(): IgnitorDsl = when (this) {
    is IgnitorDsl -> this
    is Number -> IgnitorDsl.Constant(this.toDouble())
    is Function<*> -> throw KlangScriptTypeError("expected a sound or a number, got a function", operation = "sound parameter")
    else -> throw KlangScriptTypeError("expected a sound or a number, got ${this::class.simpleName}", operation = "sound parameter")
}

/**
 * An `adsrCurves` name as a curve, or `null` when the name is unknown. The chain's door coerces
 * `null` to its default ("exp"); the filter and pitch builders to theirs (`toModEnvCurve`).
 */
internal fun parseAdsrCurveName(name: String): AdsrCurve? = when (name.trim().lowercase()) {
    "linear", "lin" -> AdsrCurve.Linear
    "square", "sq", "quad", "quadratic" -> AdsrCurve.Square
    "cube", "cb", "cubic" -> AdsrCurve.Cube
    "scurve", "s", "smooth", "sigmoid" -> AdsrCurve.SCurve
    "invsquare", "inv", "isquare", "concave" -> AdsrCurve.InvSquare
    "exponential", "exp", "expo" -> AdsrCurve.Exponential
    else -> null
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

    /**
     * Applies a resonant lowpass filter. Cutoff and Q accept Number or IgnitorDsl.
     *
     * The door carries the filter's musical inputs, [freq] and [q]; everything else is a knob on
     * the [FilterBuilder] the lambda receives: `passes`, `analog`, `humanize`, and the cutoff
     * envelope as `env(semitones)` plus ONE `adsr(attackSec, decaySec, sustainLevel, releaseSec)`
     * call, the chain `adsr`'s pattern, shaped by `adsrCurves`. `env` and `adsr` are a compound
     * pair: naming either switches the envelope on and the other fills from the constants
     * (`fillFilterEnvelope`, after the lambda). A curve alone does not switch it on.
     *
     * ```KlangScript
     * Osc.saw().lowpass(800, 1.2, x => x.passes(2).analog(3))
     * Osc.saw().lowpass(800, x => x.env(24).adsr(0.005, 0.3, 0.2, 0.2))   // a pluck; q stays 0.707
     * ```
     *
     * The cutoff-envelope LAW is not the same as sprudel's `lpf(env = ...)` yet: see
     * [IgnitorDsl.Lowpass.env] and decision D3.
     *
     * @param configure receives the [FilterBuilder] (knobs: `passes`, `analog`, `humanize`, `env`,
     * `adsr`, `adsrCurves`) and returns it.
     */
    @KlangScript.Method
    fun lowpass(
        self: IgnitorDsl,
        freq: IgnitorDslLike,
        q: IgnitorDslLike = 0.707,
        configure: ((FilterBuilder) -> FilterBuilder)? = null,
    ): IgnitorDsl {
        val k = FilterBuilder().configuredBy("lowpass", configure).knobs

        return self.lowpass(
            freq.toIgnitorDsl(), q.toIgnitorDsl(), coercePasses(k.passes), k.analog,
            k.env, k.attackSec, k.decaySec, k.sustainLevel, k.releaseSec,
            k.attackCurve, k.decayCurve, k.releaseCurve, k.humanize,
        )
    }

    /**
     * Applies a resonant highpass filter. The knobs are [lowpass]'s, on the same [FilterBuilder].
     *
     * @param configure receives the [FilterBuilder] and returns it.
     */
    @KlangScript.Method
    fun highpass(
        self: IgnitorDsl,
        freq: IgnitorDslLike,
        q: IgnitorDslLike = 0.707,
        configure: ((FilterBuilder) -> FilterBuilder)? = null,
    ): IgnitorDsl {
        val k = FilterBuilder().configuredBy("highpass", configure).knobs

        return self.highpass(
            freq.toIgnitorDsl(), q.toIgnitorDsl(), coercePasses(k.passes), k.analog,
            k.env, k.attackSec, k.decaySec, k.sustainLevel, k.releaseSec,
            k.attackCurve, k.decayCurve, k.releaseCurve, k.humanize,
        )
    }

    /**
     * Applies a one-pole lowpass at [freq] Hz — the gentlest filter there is (6 dB/oct, no
     * resonance); musically a warmth/tone control. ONE name on every door (formerly
     * `warmth` / `onePoleLowpass`).
     */
    @KlangScript.Method
    fun onepole(self: IgnitorDsl, freq: IgnitorDslLike): IgnitorDsl =
        IgnitorDsl.OnePoleLowpass(inner = self, freq = freq.toIgnitorDsl())

    /**
     * SVF bandpass filter. Passes frequencies near the cutoff, attenuates others. The knobs are
     * [lowpass]'s without `passes`, on a [BandFilterBuilder]; its `analog` scales `humanize` only
     * (the saturation is not implemented for this tap).
     *
     * @param configure receives the [BandFilterBuilder] (knobs: `analog`, `humanize`, `env`,
     * `adsr`, `adsrCurves`) and returns it.
     */
    @KlangScript.Method
    fun bandpass(
        self: IgnitorDsl,
        freq: IgnitorDslLike,
        q: IgnitorDslLike = 0.707,
        configure: ((BandFilterBuilder) -> BandFilterBuilder)? = null,
    ): IgnitorDsl {
        val k = BandFilterBuilder().configuredBy("bandpass", configure).knobs

        return self.bandpass(
            freq.toIgnitorDsl(), q.toIgnitorDsl(), k.analog,
            k.env, k.attackSec, k.decaySec, k.sustainLevel, k.releaseSec,
            k.attackCurve, k.decayCurve, k.releaseCurve, k.humanize,
        )
    }

    /**
     * Turns the graph optimizer off for this sound (`optimizer(0)`), so it renders exactly as
     * written instead of having its filter chain fused.
     *
     * Fusing is meant to be inaudible, so this is a listening tool: put `.optimizer(0)` on a
     * sound, compare by ear, and take it off again. Anything but 0 leaves the optimizer on.
     */
    @KlangScript.Method
    fun optimizer(self: IgnitorDsl, on: Int = 1): IgnitorDsl =
        IgnitorDsl.OptimizerHint(inner = self, on = on)

    /**
     * Opens an equalizer on [self] and configures its sections in the lambda. Everything in one
     * equalizer runs in ONE pass instead of one node each, which drops the scratch buffer, the
     * extra buffer traffic and the virtual call for every section after the first (the per-sample
     * filter loop per section stays, by design). The saving grows with the section count and is
     * much larger in the browser than on desktop JVM.
     *
     * Two kinds of section on the [EqBuilder], and the difference is audible:
     * - `band(freq, q, db)` is an ordinary EQ band. Bands apply one after another, so two
     *   overlapping boosts compound.
     * - `tap(freq, q, gain)` takes the sound going INTO the equalizer, filters that, and mixes
     *   it back in. Taps mix with the original rather than stacking on each other.
     *
     * Plain filters written after it (`.lowpass()`, `.notch()`, ...) are folded into the same
     * single pass automatically, so you do not have to write them as bands to get the saving,
     * but only NEIGHBOURING filters merge: anything else in between (`.distort()`, `.mul()`,
     * `.tremolo()`, ...) is a wall and starts a new pass. Use `.optimizer(0)` to render a sound
     * exactly as written and compare by ear.
     *
     * An `.eq()` directly on an equalizer continues it (the lambda appends to its sections); an
     * `.eq()` written after other filters opens a SECOND equalizer. Careful when adding a `tap`
     * onto a sound someone ELSE built: if that sound already ends in an equalizer, your `.eq()`
     * continues theirs, and your tap then reads THEIR input rather than their output. Bands are
     * unaffected.
     *
     * @param configure receives the [EqBuilder] (knobs: `band`, `tap`) and returns it.
     *
     * ```KlangScript
     * Osc.saw().eq(e => e.band(300, 1.0, -4).tap(850, 0.707, 1.7)).lowpass(5000)
     * ```
     */
    @KlangScript.Method
    fun eq(self: IgnitorDsl, configure: ((EqBuilder) -> EqBuilder)? = null): IgnitorDsl {
        val opened = when (self) {
            is IgnitorDsl.Eq -> self
            else -> IgnitorDsl.Eq(inner = self)
        }
        return EqBuilder(opened).configuredBy("eq", configure).node
    }

    /**
     * SVF notch (band-reject) filter. The knobs are [bandpass]'s, on a [BandFilterBuilder].
     *
     * @param configure receives the [BandFilterBuilder] and returns it.
     */
    @KlangScript.Method
    fun notch(
        self: IgnitorDsl,
        freq: IgnitorDslLike,
        q: IgnitorDslLike = 0.707,
        configure: ((BandFilterBuilder) -> BandFilterBuilder)? = null,
    ): IgnitorDsl {
        val k = BandFilterBuilder().configuredBy("notch", configure).knobs

        return self.notch(
            freq.toIgnitorDsl(), q.toIgnitorDsl(), k.analog,
            k.env, k.attackSec, k.decaySec, k.sustainLevel, k.releaseSec,
            k.attackCurve, k.decayCurve, k.releaseCurve, k.humanize,
        )
    }

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

    /**
     * Sets per-stage ADSR shape curves. Each stage takes `"exp"` (the DEFAULT everywhere —
     * analog-style curvature, see `expK`), `"linear"` (`lin`), `"square"` (`sq`/`quad`),
     * `"cube"` (`cb`), `"scurve"` (`s`/`smooth`/`sigmoid`) or `"invsquare"` (`inv`/`concave`).
     * An unrecognized name coerces to `"exp"`, the same as not setting it.
     *
     * ⚠ A PARTIAL call RESETS the omitted stages to `"exp"` (every param defaults to it) —
     * unlike the sprudel door's `adsrCurves`, whose omitted stages keep their current curve
     * (per-event control-pattern semantics). Set all three when you mean all three.
     *
     * If [self] is already an [IgnitorDsl.Adsr], the curves are set on it via copy.
     * Otherwise, a new [IgnitorDsl.Adsr] is wrapped around [self] with default times.
     */
    @KlangScript.Method
    fun adsrCurves(
        self: IgnitorDsl,
        attackCurve: String = "exp",
        decayCurve: String = "exp",
        releaseCurve: String = "exp",
    ): IgnitorDsl {
        val a = parseAdsrCurveName(attackCurve) ?: AdsrCurve.Default
        val d = parseAdsrCurveName(decayCurve) ?: AdsrCurve.Default
        val r = parseAdsrCurveName(releaseCurve) ?: AdsrCurve.Default
        return when (self) {
            is IgnitorDsl.Adsr -> self.copy(
                attackCurve = a, decayCurve = d, releaseCurve = r,
            )

            else -> IgnitorDsl.Adsr(
                inner = self,
                attackCurve = a, decayCurve = d, releaseCurve = r,
            )
        }
    }

    /**
     * De-click the ADSR gain by [seconds] — a one-pole low-pass that rounds the corners at segment
     * joins (attack→decay peak, gate-off, cutoff), removing the low-note "plop". `0` = off; a gentle
     * value is ~0.0005–0.001. This per-ignitor envelope is not de-clicked by default.
     *
     * If [self] is already an [IgnitorDsl.Adsr], the value is set on it via copy; otherwise a new
     * [IgnitorDsl.Adsr] is wrapped around [self] with default times.
     */
    @KlangScript.Method
    fun declickSeconds(self: IgnitorDsl, seconds: IgnitorDslLike): IgnitorDsl = when (self) {
        is IgnitorDsl.Adsr -> self.copy(declickSeconds = seconds.toIgnitorDsl())
        else -> IgnitorDsl.Adsr(inner = self, declickSeconds = seconds.toIgnitorDsl())
    }

    /**
     * Sets the curvature [k] of the ADSR `"exponential"` shape (larger = steeper initial change,
     * faster decay drop / sharper attack finish). Only affects stages using the exponential curve.
     * Omit for the engine default (3.0).
     *
     * If [self] is already an [IgnitorDsl.Adsr], the value is set on it via copy; otherwise a new
     * [IgnitorDsl.Adsr] is wrapped around [self] with default times.
     */
    @KlangScript.Method
    fun expK(self: IgnitorDsl, k: IgnitorDslLike): IgnitorDsl = when (self) {
        is IgnitorDsl.Adsr -> self.copy(expK = k.toIgnitorDsl())
        else -> IgnitorDsl.Adsr(inner = self, expK = k.toIgnitorDsl())
    }

    // ── Effects ──────────────────────────────────────────────────────────────

    /**
     * Pre-amplification stage. Boosts signal level before clipping: gain without a curve, every
     * colour belongs to `shape`.
     */
    @KlangScript.Method
    fun drive(self: IgnitorDsl, amount: IgnitorDslLike): IgnitorDsl =
        IgnitorDsl.Drive(inner = self, amount = amount.toIgnitorDsl())

    /**
     * Pure waveshaping without drive. Applies a nonlinear transfer function per sample.
     *
     * Shapes:
     *  - **Symmetric soft:** "soft" (tanh), "gentle", "softsat", "cubic", "exp", "sineshaper".
     *  - **Symmetric hard / wavefolding:** "hard", "zerosquare", "chebyshev", "fold", "linearfold".
     *  - **Asymmetric (even harmonics):** "diode", "tube", "asym", "stompbox", "rectify".
     *
     * Oversample: user-facing factor (2 = 2x, 4 = 4x, 8 = 8x). 0/1 = off. Non-power-of-2 floored.
     */
    @KlangScript.Method
    fun shape(self: IgnitorDsl, shape: String = "soft", oversample: Int = 0): IgnitorDsl =
        IgnitorDsl.Shape(inner = self, shape = shape, oversample = oversample)

    /**
     * Waveshaping distortion. Convenience for drive(amount) + shape(shape).
     *
     * Shapes:
     *  - **Symmetric soft:** "soft" (tanh), "gentle", "softsat", "cubic", "exp", "sineshaper".
     *  - **Symmetric hard / wavefolding:** "hard", "zerosquare", "chebyshev", "fold", "linearfold".
     *  - **Asymmetric (even harmonics):** "diode", "tube", "asym", "stompbox", "rectify".
     *
     * Oversample: user-facing factor (2 = 2x, 4 = 4x, 8 = 8x). 0/1 = off. Non-power-of-2 floored.
     */
    @KlangScript.Method
    fun distort(self: IgnitorDsl, amount: IgnitorDslLike, shape: String = "soft", oversample: Int = 0): IgnitorDsl =
        IgnitorDsl.Shape(
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

    /**
     * Applies a multi-stage phaser effect. [wet] comes FIRST, as on every door that has one, and
     * both [wet] and [rate] are required, because a positional rate follows the wet. The dry floor
     * is a knob on the [PhaserBuilder]: `.phaser(0.3, 0.5, x => x.floor(0.2))`.
     *
     * @param wet wet/dry balance, 0..1 (0 is a bit-exact bypass).
     * @param rate sweep rate in Hz.
     * @param center sweep center frequency in Hz (default 1000).
     * @param sweep sweep width in Hz (default 1000).
     * @param configure receives the [PhaserBuilder] (knob: `floor`) and returns it.
     */
    @KlangScript.Method
    fun phaser(
        self: IgnitorDsl,
        wet: IgnitorDslLike,
        rate: IgnitorDslLike,
        center: IgnitorDslLike = 1000.0,
        sweep: IgnitorDslLike = 1000.0,
        configure: ((PhaserBuilder) -> PhaserBuilder)? = null,
    ): IgnitorDsl = PhaserBuilder(
        IgnitorDsl.Phaser(
            inner = self,
            rate = rate.toIgnitorDsl(),
            wet = wet.toIgnitorDsl(),
            center = center.toIgnitorDsl(),
            sweep = sweep.toIgnitorDsl(),
        ),
    ).configuredBy("phaser", configure).node

    /** Applies amplitude tremolo. */
    @KlangScript.Method
    fun tremolo(self: IgnitorDsl, rate: IgnitorDslLike, depth: IgnitorDslLike): IgnitorDsl =
        IgnitorDsl.Tremolo(inner = self, rate = rate.toIgnitorDsl(), depth = depth.toIgnitorDsl())

    /**
     * Applies a granular shimmer cloud with configurable pitch transpositions and feedback. [wet]
     * comes FIRST, as on every door that has one; the dry floor is a knob on the [ShimmerBuilder]:
     * `.shimmer(0.4, 0.5, 4000, [0, 7, 12], x => x.floor(0.2))`.
     *
     * @param wet Wet/dry balance, 0..1. Default 0.5. 0 is a bit-exact bypass.
     * @param feedback Cascade feedback (0..0.95). Default 0.5.
     * @param tone Feedback-path LPF cutoff in Hz. Default 4000.
     * @param pitches Array of semitone transpositions. Default [0, 7, 12]. Example: [0, 4, 7, 11] for maj7.
     * @param configure receives the [ShimmerBuilder] (knob: `floor`) and returns it.
     */
    @KlangScript.Method
    fun shimmer(
        self: IgnitorDsl,
        wet: IgnitorDslLike = 0.5,
        feedback: IgnitorDslLike = 0.5,
        tone: IgnitorDslLike = 4000.0,
        pitches: Any? = null,
        configure: ((ShimmerBuilder) -> ShimmerBuilder)? = null,
    ): IgnitorDsl {
        val pitchList = when (pitches) {
            is List<*> -> pitches.map { (it as Number).toDouble() }
            else -> listOf(0.0, 7.0, 12.0)
        }
        return ShimmerBuilder(
            IgnitorDsl.Shimmer(
                inner = self,
                wet = wet.toIgnitorDsl(),
                feedback = feedback.toIgnitorDsl(),
                pitches = pitchList,
                tone = tone.toIgnitorDsl(),
            ),
        ).configuredBy("shimmer", configure).node
    }

    // ── FM Synthesis ─────────────────────────────────────────────────────────

    /**
     * Applies FM synthesis with a modulator ignitor. The modulation index envelope is a knob on the
     * [FmBuilder]: `.fm(Osc.sine(), 1.4, 300, x => x.adsr(0.001, 0.5, 0, 0.05))`, the built-in `sgbell`.
     *
     * @param configure receives the [FmBuilder] (knob: `adsr`) and returns it.
     */
    @KlangScript.Method
    fun fm(
        self: IgnitorDsl,
        modulator: IgnitorDslLike,
        ratio: IgnitorDslLike,
        depth: IgnitorDslLike,
        configure: ((FmBuilder) -> FmBuilder)? = null,
    ): IgnitorDsl = FmBuilder(
        IgnitorDsl.Fm(
            carrier = self,
            modulator = modulator.toIgnitorDsl(),
            ratio = ratio.toIgnitorDsl(),
            depth = depth.toIgnitorDsl(),
        ),
    ).configuredBy("fm", configure).node

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

    /** Applies pitch vibrato: [rate] Hz LFO, [semitones] deep. */
    @KlangScript.Method
    fun vibrato(self: IgnitorDsl, rate: IgnitorDslLike, semitones: IgnitorDslLike): IgnitorDsl =
        IgnitorDsl.Vibrato(inner = self, rate = rate.toIgnitorDsl(), semitones = semitones.toIgnitorDsl())

    /** Applies continuous pitch acceleration over the voice duration, by [semitones] total. */
    @KlangScript.Method
    fun accelerate(self: IgnitorDsl, semitones: IgnitorDslLike): IgnitorDsl =
        IgnitorDsl.Accelerate(inner = self, semitones = semitones.toIgnitorDsl())

    /**
     * Applies a custom pitch modulation from any Ignitor signal.
     * The mod signal uses deviation space: 0.0 = no change, positive = higher, negative = lower.
     */
    @KlangScript.Method
    fun pitchMod(self: IgnitorDsl, mod: IgnitorDslLike): IgnitorDsl =
        IgnitorDsl.PitchMod(inner = self, mod = mod.toIgnitorDsl())

    /**
     * Applies a pitch envelope (pitch sweep over time). [semitones] is the shift at the envelope
     * peak; the stages are ONE `adsr` call on the [PitchEnvelopeBuilder], the chain `adsr`'s
     * pattern: `pitchEnvelope(24, x => x.adsr(0.001, 0.05, 0, 0))` sweeps a kick from two octaves
     * up down to the note. Without the lambda the stages are `adsr(0.01, 0.1, 0, 0)`. The release
     * returns the pitch to the note from the gate's end and does not extend the voice's life.
     *
     * @param configure receives the [PitchEnvelopeBuilder] (knobs: `adsr`, `adsrCurves`) and returns it.
     */
    @KlangScript.Method
    fun pitchEnvelope(
        self: IgnitorDsl,
        semitones: IgnitorDslLike,
        configure: ((PitchEnvelopeBuilder) -> PitchEnvelopeBuilder)? = null,
    ): IgnitorDsl = PitchEnvelopeBuilder(
        IgnitorDsl.PitchEnvelope(inner = self, semitones = semitones.toIgnitorDsl()),
    ).configuredBy("pitchEnvelope", configure).node

    // ── Analog Drift ────────────────────────────────────────────────────────


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
     * Places the `pregain` slot here: how hard the pattern plays INTO whatever follows.
     *
     * Exactly `mul(OscSlot.pregain)`, and written as a call to [mul] so the two spellings cannot
     * drift into two operand orders: one tree, one content id. It takes no argument on purpose:
     * the slot IS the parameter, and the pattern moves it with `pregain(x)`.
     *
     * Put it in front of the nonlinearity it should drive, once. It changes TIMBRE only because
     * of what follows it; with nothing nonlinear after it, it is a plain level, and `gain` is
     * the tone-neutral level word.
     *
     * ```KlangScript
     * Osc.saw().pregain().distort(0.5)
     * ```
     */
    @KlangScript.Method
    fun pregain(self: IgnitorDsl): IgnitorDsl =
        mul(self, IgnitorDsl.Slots.pregain)

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
     */
    @KlangScript.Method
    fun pow(self: IgnitorDsl, exp: IgnitorDslLike): IgnitorDsl =
        IgnitorDsl.Pow(base = self, exp = exp.toIgnitorDsl())

    /**
     * Raises this signal to the power of [exp]. Alias for [pow].
     *
     */
    @KlangScript.Method
    fun power(self: IgnitorDsl, exp: IgnitorDslLike): IgnitorDsl =
        IgnitorDsl.Pow(base = self, exp = exp.toIgnitorDsl())

    /**
     * Enforces a minimum allowed value per sample: this signal, but at least [other].
     *
     * Reads as "take self, at a minimum other", the same meaning `min` carries on a number
     * and on a pattern. Anything below [other] is raised to it.
     *
     * The node crossing is deliberate: a floor is the per-sample *maximum* of the two
     * signals, so the `min` door builds [IgnitorDsl.Max]. Do not "correct" it.
     *
     * ```KlangScript(Playable)
     * sine.range(-1, 1).min(0)   // half-wave rectify
     * ```
     */
    @KlangScript.Method
    fun min(self: IgnitorDsl, other: IgnitorDslLike): IgnitorDsl =
        IgnitorDsl.Max(left = self, right = other.toIgnitorDsl())

    /**
     * Enforces a maximum allowed value per sample: this signal, but at most [other].
     *
     * Reads as "take self, at a maximum other", the same meaning `max` carries on a number
     * and on a pattern. Anything above [other] is lowered to it.
     *
     * The node crossing is deliberate: a cap is the per-sample *minimum* of the two
     * signals, so the `max` door builds [IgnitorDsl.Min]. Do not "correct" it.
     *
     * ```KlangScript(Playable)
     * sine.range(0, 2).max(0.8)   // cap the signal at 0.8
     * ```
     */
    @KlangScript.Method
    fun max(self: IgnitorDsl, other: IgnitorDslLike): IgnitorDsl =
        IgnitorDsl.Min(left = self, right = other.toIgnitorDsl())

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
     * On a signal `mod` and `rem` are the same operation, the engine's `%`: the sign follows the LEFT
     * operand, so a negative value stays negative. This differs from `mod` on a number, which is the
     * floor modulo (`(-1).mod(12)` is 11); to wrap a negative signal into a range, add the range and
     * take `mod` again.
     */
    @KlangScript.Method
    fun mod(self: IgnitorDsl, other: IgnitorDslLike): IgnitorDsl =
        IgnitorDsl.Mod(left = self, right = other.toIgnitorDsl())

    /**
     * Per-sample modulo. Alias for [mod] (matches Kotlin's `rem`): on a signal the two are the same
     * operation, unlike `mod` and `rem` on a number.
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
