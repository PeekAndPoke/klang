/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.peekandpoke.klang.audio_bridge.AdsrCurve
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.coercePasses
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
     * `passes` is the THIRD slot on every door (`lpf(freq, q, passes)` in sprudel,
     * `lowpass(freq, q, passes)` from Kotlin), so the same positional call means the same
     * filter everywhere. [analog] is fourth, and exists on this door only. KlangScript does
     * not allow MIXING positional and named arguments, so reach for [analog] either fully
     * positionally, `lowpass(800, 1.8, 1, 3)`, or all-named:
     * `lowpass(freq = 800, q = 1.8, analog = 3)`.
     *
     * The cascade's per-stage q is STAGGERED (Butterworth ladder scaled by `q/0.707`), so at
     * the DEFAULT q it stays -3 dB AT the cutoff: `lowpass(800, 0.707, 2)` still means 800.
     * A resonant q keeps its character but COMPOUNDS across stages — gain at the cutoff is
     * `(q*sqrt(2))^passes / sqrt(2)`, so `q = 1.0, passes = 2` sits +3 dB there, not -3. So
     * does [analog]: every stage gets the full drive.
     *
     * @param passes Cascade count: `2` = 24 dB/oct, `3` = 36. Rounded, coerced to 1..16.
     * @param analog Analog character amount, 0..10. `0` = clean linear filter
     * (default — bit-identical to pre-analog behaviour). Higher values engage
     * OB-X-style state-dependent damping that compresses the resonance peak.
     * 1–3 gives Diva-default warmth; higher = stronger "diode bite".
     */
    @KlangScript.Method
    fun lowpass(
        self: IgnitorDsl,
        freq: IgnitorDslLike,
        q: IgnitorDslLike = 0.707,
        passes: Double = 1.0,
        analog: IgnitorDslLike = 0.0,
    ): IgnitorDsl = IgnitorDsl.Lowpass(
        inner = self, freq = freq.toIgnitorDsl(), q = q.toIgnitorDsl(),
        analog = analog.toIgnitorDsl(), passes = coercePasses(passes),
    )

    /** Applies a resonant highpass filter. See [lowpass] for `passes` and `analog` semantics. */
    @KlangScript.Method
    fun highpass(
        self: IgnitorDsl,
        freq: IgnitorDslLike,
        q: IgnitorDslLike = 0.707,
        passes: Double = 1.0,
        analog: IgnitorDslLike = 0.0,
    ): IgnitorDsl = IgnitorDsl.Highpass(
        inner = self, freq = freq.toIgnitorDsl(), q = q.toIgnitorDsl(),
        analog = analog.toIgnitorDsl(), passes = coercePasses(passes),
    )

    /**
     * Applies a one-pole lowpass at [freq] Hz — the gentlest filter there is (6 dB/oct, no
     * resonance); musically a warmth/tone control. ONE name on every door (formerly
     * `warmth` / `onePoleLowpass`).
     */
    @KlangScript.Method
    fun onepole(self: IgnitorDsl, freq: IgnitorDslLike): IgnitorDsl =
        IgnitorDsl.OnePoleLowpass(inner = self, freq = freq.toIgnitorDsl())

    /**
     * SVF bandpass filter. Passes frequencies near the cutoff, attenuates others.
     *
     * @param analog Reserved — accepted for API consistency but currently a no-op
     * (BP saturation not yet implemented). Same range as [lowpass.analog] when implemented.
     */
    @KlangScript.Method
    fun bandpass(
        self: IgnitorDsl,
        freq: IgnitorDslLike,
        q: IgnitorDslLike = 0.707,
        analog: IgnitorDslLike = 0.0,
    ): IgnitorDsl = IgnitorDsl.Bandpass(
        inner = self, freq = freq.toIgnitorDsl(), q = q.toIgnitorDsl(),
        analog = analog.toIgnitorDsl(),
    )

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
     * Opens an equalizer on [self]. Everything added to it runs in ONE pass instead of one node
     * each, which drops the scratch buffer, the extra buffer traffic and the virtual call for
     * every section after the first (the per-sample filter loop per section stays, by design). The saving grows with the section count and is much larger in the browser
     * than on desktop JVM.
     *
     * Two kinds of section, and the difference is audible:
     * - `.band(freq, q, db)` is an ordinary EQ band. Bands apply one after another, so two
     *   overlapping boosts compound.
     * - `.tap(freq, q, gain)` takes the sound going INTO the equalizer, filters that, and mixes
     *   it back in. Taps mix with the original rather than stacking on each other.
     *
     * Both exist only on an equalizer, so `.eq()` comes first: `Osc.saw().eq().band(1200, 1.0, 6)`.
     * Calling `.eq()` again right away changes nothing, but an `.eq()` written after other
     * filters opens a SECOND equalizer. Plain filters written after it (`.lowpass()`,
     * `.notch()`, ...) are folded into the same single pass automatically, so you do not have to
     * write them as bands to get the saving — but only NEIGHBOURING filters merge: anything
     * else in between (`.distort()`, `.mul()`, `.tremolo()`, ...) is a wall and starts a new
     * pass. Use `.optimizer(0)` to render a sound exactly as written and compare by ear.
     *
     * ⚠ Careful when adding a `.tap()` onto a sound someone ELSE built: if that sound already
     * ends in an equalizer, your `.eq()` continues theirs, and your tap then reads THEIR input
     * rather than their output. Bands are unaffected.
     */
    @KlangScript.Method
    fun eq(self: IgnitorDsl): IgnitorDsl.Eq = when (self) {
        is IgnitorDsl.Eq -> self
        else -> IgnitorDsl.Eq(inner = self)
    }

    /** SVF notch (band-reject) filter. See [bandpass] for `analog` semantics (currently a no-op). */
    @KlangScript.Method
    fun notch(
        self: IgnitorDsl,
        freq: IgnitorDslLike,
        q: IgnitorDslLike = 0.707,
        analog: IgnitorDslLike = 0.0,
    ): IgnitorDsl = IgnitorDsl.Notch(
        inner = self, freq = freq.toIgnitorDsl(), q = q.toIgnitorDsl(),
        analog = analog.toIgnitorDsl(),
    )

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

    /** Applies the same ADSR shape curve to all three stages — same names as [adsrCurves] (`"exp"` default). */
    @KlangScript.Method
    fun adsrCurve(self: IgnitorDsl, curve: String = "exp"): IgnitorDsl =
        adsrCurves(self, curve, curve, curve)

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

    private fun parseAdsrCurveName(name: String): AdsrCurve? = when (name.trim().lowercase()) {
        "linear", "lin" -> AdsrCurve.Linear
        "square", "sq", "quad", "quadratic" -> AdsrCurve.Square
        "cube", "cb", "cubic" -> AdsrCurve.Cube
        "scurve", "s", "smooth", "sigmoid" -> AdsrCurve.SCurve
        "invsquare", "inv", "isquare", "concave" -> AdsrCurve.InvSquare
        "exponential", "exp", "expo" -> AdsrCurve.Exponential
        else -> null
    }

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
     * Applies a multi-stage phaser effect. The wet/dry balance is not a parameter here — it is
     * the shared wet knob, typed onto the node: `.phaser(rate).wet(0.3).dryFloor(0.2)`
     * (defaults 0.5 / 0.0).
     */
    @KlangScript.Method
    fun phaser(
        self: IgnitorDsl,
        rate: IgnitorDslLike,
        center: IgnitorDslLike = 1000.0,
        sweep: IgnitorDslLike = 1000.0,
    ): IgnitorDsl.Phaser = IgnitorDsl.Phaser(
        inner = self,
        rate = rate.toIgnitorDsl(),
        center = center.toIgnitorDsl(),
        sweep = sweep.toIgnitorDsl(),
    )

    /** Applies amplitude tremolo. */
    @KlangScript.Method
    fun tremolo(self: IgnitorDsl, rate: IgnitorDslLike, depth: IgnitorDslLike): IgnitorDsl =
        IgnitorDsl.Tremolo(inner = self, rate = rate.toIgnitorDsl(), depth = depth.toIgnitorDsl())

    /**
     * Applies a granular shimmer cloud with configurable pitch transpositions and feedback.
     * The wet/dry balance is the shared wet knob, typed onto the node:
     * `.shimmer().wet(0.4).dryFloor(0.2)` (defaults 0.5 / 0.0).
     *
     * @param feedback Cascade feedback (0..0.95). Default 0.5.
     * @param tone Feedback-path LPF cutoff in Hz. Default 4000.
     * @param pitches Array of semitone transpositions. Default [0, 7, 12]. Example: [0, 4, 7, 11] for maj7.
     */
    @KlangScript.Method
    fun shimmer(
        self: IgnitorDsl,
        feedback: IgnitorDslLike = 0.5,
        tone: IgnitorDslLike = 4000.0,
        pitches: Any = listOf(0.0, 7.0, 12.0),
    ): IgnitorDsl.Shimmer {
        @Suppress("UNCHECKED_CAST")
        val pitchList = when (pitches) {
            is List<*> -> pitches.map { (it as Number).toDouble() }
            else -> listOf(0.0, 7.0, 12.0)
        }
        return IgnitorDsl.Shimmer(
            inner = self,
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
     * Applies a pitch envelope (pitch sweep over time). [semitones] is the shift at the
     * envelope peak: `pitchEnvelope(semitones = 24, decaySec = 0.05)` sweeps a kick from
     * two octaves up down to the note.
     */
    @KlangScript.Method
    fun pitchEnvelope(
        self: IgnitorDsl,
        semitones: IgnitorDslLike,
        attackSec: IgnitorDslLike = 0.01,
        decaySec: IgnitorDslLike = 0.1,
        releaseSec: IgnitorDslLike = 0.0,
    ): IgnitorDsl = IgnitorDsl.PitchEnvelope(
        inner = self,
        semitones = semitones.toIgnitorDsl(),
        attackSec = attackSec.toIgnitorDsl(),
        decaySec = decaySec.toIgnitorDsl(),
        releaseSec = releaseSec.toIgnitorDsl(),
    )

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
