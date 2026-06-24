/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import kotlin.math.abs
import kotlin.math.sin

/**
 * Collection of optimised shaping functions for distortion and saturation.
 *
 * Each function applies a different transfer curve (waveshaper) to an input sample `x`.
 * These are used to create various "colors" of distortion, from warm saturation to
 * harsh digital clipping.
 *
 * **NaN / Inf policy:** these are raw math primitives. NaN/Inf input is **not**
 * sterilised here — callers in IIR contexts (e.g. anything feeding a DcBlocker or
 * the Oversampler FIR delay line) must guard before invocation. See the `// NaN-guard`
 * idiom used by [Oversampler.process] and the strip-filter direct paths. This
 * convention matches the engine's "raw Motör" philosophy: don't pay the cost of
 * defensive checks in the inner math; defend at the integration points.
 */
@Suppress("NOTHING_TO_INLINE", "unused")
object ClippingFuncs {

    /**
     * Fast approximation of `tanh` using a Padé approximant.
     *
     * **Sonic Character:** Warm, analog-style saturation. Smoothly rounds off peaks.
     * **Use Case:** General purpose overdrive, guitar-pedal style distortion.
     * **Performance:** Significantly faster than `kotlin.math.tanh` (~5x) with negligible error for audio.
     * **Range:** Accurate within `[-3, 3]`, clamped outside.
     *
     * **C¹ at the ±3 boundary:** the Padé form `x(27+x²) / (27+9x²)` evaluates to
     * exactly `1.0` at `x = 3` AND its derivative is `0` at `x = 3` — so the
     * constant clamp `return 1.0` is both value- AND slope-continuous with the
     * curve. No cliff at the clamp.
     */
    inline fun fastTanh(x: Double): Double {
        if (x < -3.0) return -1.0
        if (x > 3.0) return 1.0
        val x2 = x * x
        return x * (27.0 + x2) / (27.0 + 9.0 * x2)
    }

    /**
     * Hard Clipping (Digital Clip).
     *
     * **Sonic Character:** Harsh, aggressive, "buzzy". Introduces significant odd harmonics and aliasing at high drive.
     * **Use Case:** Industrial sounds, "bit-crush" adjacent effects, or as a safety brick-wall limiter.
     * **Math:** Simply clamps the signal between -1.0 and 1.0.
     */
    inline fun hardClip(x: Double): Double {
        return x.coerceIn(-1.0, 1.0)
    }

    /**
     * Rational Soft Clipper (Algebraic Sigmoid).
     *
     * **Sonic Character:** Very similar to `tanh` but slightly "softer" knee.
     * **Use Case:** A cheaper alternative to `fastTanh` if CPU is extremely tight,
     * though the difference is minimal on modern CPUs.
     * **Math:** `x / (1 + |x|)`
     *
     * **Inf input → NaN.** `±Inf / (1 + Inf) = NaN`. Per the engine convention
     * (see file-level KDoc), this function does not sterilise; callers in IIR
     * contexts must guard. Finite input is always well-behaved.
     */
    inline fun softClip(x: Double): Double {
        return x / (1.0 + abs(x))
    }

    /**
     * Cubic Soft Clipper (Tube Simulation).
     *
     * **Sonic Character:** Emulates the saturation of a vacuum tube amplifier.
     * Compresses dynamic range gently before clipping. Emphasises the 3rd harmonic.
     * **Use Case:** Warmth, boosting perceived loudness without obvious distortion.
     * **Math:** `1.5·x − 0.5·x³` clamped to `[-1, 1]`. At `x = ±1` the curve hits
     * `±1` with zero derivative, so the clamp is value- AND slope-continuous.
     */
    inline fun cubicClip(x: Double): Double {
        val xc = x.coerceIn(-1.0, 1.0)
        return 1.5 * xc - 0.5 * xc * xc * xc
    }

    /**
     * Sine Wavefolder.
     *
     * **Sonic Character:** Metallic, sci-fi, "FM-like".
     * Instead of clipping peaks, it maps them back into the range using a sine function.
     * **Use Case:** West-coast synthesis, experimental sounds, metallic basses.
     * **Math:** sin(x)
     */
    inline fun sineFold(x: Double): Double {
        return sin(x)
    }

    /**
     * Standard Hyperbolic Tangent.
     *
     * **Sonic Character:** The "gold standard" for soft clipping math.
     * **Use Case:** Reference implementation. Use fastTanh for production code.
     */
    inline fun nativeTanh(x: Double): Double {
        return kotlin.math.tanh(x)
    }

    /**
     * Asymmetric Diode Clipping.
     *
     * **Sonic Character:** Thicker, warmer than symmetric clipping.
     * Positive peaks compress via tanh, negative peaks compress less aggressively.
     * **Use Case:** Tube amp simulation, adding "body" to bass lines.
     * **Math:** tanh(x) for positive, tanh(x * 0.75) for negative.
     * **Note:** Generates DC offset — always use with a DC blocking filter.
     */
    inline fun diodeClip(x: Double): Double {
        return if (x >= 0.0) {
            fastTanh(x)
        } else {
            fastTanh(x * 0.75)
        }
    }

    /**
     * Chebyshev T3 Polynomial (3rd harmonic generator).
     *
     * **Sonic Character:** Pure 3rd harmonic addition, tape-saturation feel.
     * **Use Case:** Subtle harmonic enhancement, "tape warmth".
     * **Math:** T3(x) = 4x^3 - 3x, with input clamped to [-1, 1].
     */
    inline fun chebyshevT3(x: Double): Double {
        val xc = x.coerceIn(-1.0, 1.0)
        return 4.0 * xc * xc * xc - 3.0 * xc
    }

    /**
     * Full-Wave Rectifier.
     *
     * **Sonic Character:** Octave-up effect, buzzy, gnarly.
     * **Use Case:** Octave effects, aggressive bass sounds.
     * **Math:** `|x|`, then **hard-clipped** at `1.0`. The hard clip is intentional —
     * it's the aliased flat-top above unity that gives this shape its harsh,
     * buzzy character. Pre-amplified input (via `drive`) is expected.
     * **Note:** Generates DC offset — always use with a DC blocking filter.
     */
    inline fun rectify(x: Double): Double {
        return abs(x).coerceAtMost(1.0)
    }

    /**
     * Exponential Soft Clip (Transistor-style).
     *
     * **Sonic Character:** Tighter knee than tanh, more "transistor" than "tube".
     * **Use Case:** Transistor amp simulation, punchy drums.
     * **Math:** sign(x) * (1 - exp(-|x|))
     */
    inline fun expClip(x: Double): Double {
        return if (x >= 0.0) {
            1.0 - kotlin.math.exp(-x)
        } else {
            -(1.0 - kotlin.math.exp(x))
        }
    }

    /**
     * Soft Saturation (algebraic, gentler than `softClip`).
     *
     * **Sonic Character:** Very subtle, "almost identity" at low levels — the gentlest
     * saturation in the family. Adds barely-perceptible warmth that opens up under heavy drive.
     * **Use Case:** "Pre-clip" warming stage, transparent saturation on mix bus, vocal smoothing.
     * **Math:** `x / √(1 + x²)`
     *
     * Compared to `softClip` (`x / (1 + |x|)`): the square-root denominator is closer to 1
     * for small `|x|`, so the slope at the origin is steeper (= more identity) and the
     * approach to ±1 is gentler.
     *
     * **Inf input → NaN** (Inf² = Inf, Inf/Inf = NaN). Per the engine convention
     * (see file-level KDoc), this function does not sterilise; callers in IIR
     * contexts must guard. Finite input is always well-behaved.
     */
    inline fun softSat(x: Double): Double {
        return x / kotlin.math.sqrt(1.0 + x * x)
    }

    /**
     * Tube Saturation (shifted-tanh, asymmetric).
     *
     * **Sonic Character:** Warm, "vintage triode" — the asymmetric operating point
     * generates even harmonics in addition to odd, giving a fuller, "fatter" tone than
     * symmetric clippers. The positive side compresses harder; the negative side reaches
     * the rail. Mathematically: a Class A triode biased off the linear point produces
     * exactly this transfer curve.
     * **Use Case:** Warm bass, vocal body, "vintage" character, anything that wants
     * the fundamental thickened without obvious distortion.
     * **Math:** `(tanh(x + bias) − tanh(bias)) / (1 + tanh(bias))` with `bias = 0.5`,
     * normalised so the negative rail hits exactly `−1` (the positive saturates around
     * `+0.37`). DC offset is generated by construction — relied upon for the harmonic
     * content; downstream DC blocker removes the offset itself.
     * **Note:** Generates DC offset — always use with a DC blocking filter.
     */
    inline fun tube(x: Double): Double {
        // Constants match the Padé fastTanh, not the real tanh — so tube(0) is
        // exactly 0 (the subtracted bias equals fastTanh(0.5)). With the same
        // form, tube(-∞) is exactly -1 since `(1 + fastTanh(0.5)) × norm = 1`.
        //   fastTanh(0.5)         = 13.625 / 29.25 ≈ 0.46581196581196581
        //   1 / (1 + fastTanh(.5)) = 29.25 / 42.875 ≈ 0.68221574344023324
        return (fastTanh(x + 0.5) - 0.46581196581196581) * 0.68221574344023324
    }

    /**
     * Linear Wavefolder (triangular wrap).
     *
     * **Sonic Character:** Metallic, sci-fi — sharper and "creased" compared to `sineFold`'s
     * smooth folds. The hard reflection points produce strong inharmonic content above the
     * fundamental, especially at high drive. Identity in the linear region `|x| ≤ 1`.
     * **Use Case:** West-coast synthesis, "Buchla-style" timbres, percussive folds, metallic basses.
     * **Math:** Triangle wave with period 4, amplitude `±1`, passing through the origin.
     * Equivalent to `1 − |((x + 1) mod 4) − 2|`.
     *
     * **Aliasing:** the sharp creases at `x = ±1, ±3, …` introduce harmonics with slow
     * spectral decay — anti-aliasing (oversampling or ADAA) is *strongly* recommended for
     * audio-rate signals.
     */
    inline fun linearFold(x: Double): Double {
        val shifted = x + 1.0
        val phase = shifted - 4.0 * kotlin.math.floor(shifted * 0.25)
        return 1.0 - abs(phase - 2.0)
    }

    /**
     * Zero-Square (high-gain tanh — pushes signal toward square).
     *
     * **Sonic Character:** At low input the signal is amplified linearly; above a threshold
     * it slams into the rails like a hard clipper, producing a near-square waveform. The
     * narrow crossover region is where the timbre lives — fundamental + strong odd harmonics
     * (3rd, 5th, 7th, …) with the steep slope giving "loud-and-buzzy" character.
     * **Use Case:** Aggressive leads, "fuzz pedal" emulation, anything that wants
     * square-from-anything behaviour.
     * **Math:** `tanh(8·x)` — gain factor `8` chosen so |x| ≈ 0.3 already saturates.
     * Output bounded by ±1 (fastTanh clamps).
     */
    inline fun zeroSquare(x: Double): Double {
        return fastTanh(x * 8.0)
    }

    /**
     * Sine Shaper (normalised sine fold).
     *
     * **Sonic Character:** Smooth wavefolding with a "musical" peak — the curve hits `±1`
     * exactly at the unit boundaries, then folds back through zero and continues. Unlike
     * `sineFold` (raw `sin(x)`, peak at `x = π/2 ≈ 1.57`), this one is normalised so the
     * linear region matches `[-1, +1]`. Gentler entry than `linearFold`.
     * **Use Case:** Subtle wavefolding, "FM-light" harmonic enhancement, west-coast timbres
     * where the peak-at-unity behaviour matters.
     * **Math:** `sin(π·x / 2)`. Identity-like for small `|x|` (Taylor expansion is `x + O(x³)·π²/24`).
     * Output bounded by ±1.
     */
    inline fun sineShaper(x: Double): Double {
        return sin(x * kotlin.math.PI * 0.5)
    }

    /**
     * Asymmetric Polynomial Shaper.
     *
     * **Sonic Character:** Strong asymmetry — positive side compresses gradually (cubic taper,
     * generating the soft-clipper's 3rd harmonic), negative side saturates aggressively
     * (square-root knee, fast approach to `−1`). The polarity-dependent curve adds even
     * harmonics + DC offset on top of the odd-harmonic content.
     * **Use Case:** "Tube-like" body without the textbook shifted-tanh feel, percussive
     * shapes where attack transients sit asymmetrically, dial-in-able warmth.
     * **Math:**
     *   - Positive: `1.5x − 0.5x³` (cubic clip), input clamped to `[0, 1]`.
     *   - Negative: `−√(−x)`, input clamped to `[−1, 0]`.
     * **Note:** Generates DC offset — always use with a DC blocking filter.
     */
    inline fun asym(x: Double): Double {
        return if (x >= 0.0) {
            val xc = if (x > 1.0) 1.0 else x
            1.5 * xc - 0.5 * xc * xc * xc
        } else {
            val xc = if (x < -1.0) 1.0 else -x
            -kotlin.math.sqrt(xc)
        }
    }

    /**
     * Stomp-Box (asymmetric diode-pedal model).
     *
     * **Sonic Character:** Classic guitar-pedal grit — exponential soft clipping with
     * different "diode count" per polarity. Positive side uses one forward diode (gentler
     * curve, `k = 1.5`), negative side uses two anti-parallel diodes (sharper cutoff,
     * `k = 3.0`). The asymmetry adds even harmonics; the exponential knee gives the
     * characteristic "fuzzy compression" of analog clippers.
     * **Use Case:** Guitar emulation, lo-fi grit on drums, bass distortion with body.
     * **Math:**
     *   - Positive: `1 − e^(−1.5·x)`.
     *   - Negative: `−(1 − e^(3·x))`.
     * Output bounded by ±1. Continuous at `x = 0` (`1 − e⁰ = 0`).
     * **Note:** Generates DC offset — always use with a DC blocking filter.
     */
    inline fun stompBox(x: Double): Double {
        return if (x >= 0.0) {
            1.0 - kotlin.math.exp(-x * 1.5)
        } else {
            -(1.0 - kotlin.math.exp(x * 3.0))
        }
    }

    /**
     * Soft brick-wall limiter — identity in the linear region, smooth saturation above.
     *
     * **Shape:** C¹-continuous piecewise.
     *  - For `|x| ≤ threshold`: identity (no compression).
     *  - For `|x| > threshold`: `sign(x) · (T + (1−T) · fastTanh((|x|−T)/(1−T)))`.
     *
     * Both value and slope match at the threshold (no cliff click), output asymptotes
     * to ±1.
     *
     * **Use Case:** Bounding the output of a distortion / DC-blocker chain to ±1
     * without compressing the bulk of the signal — typical [threshold] of 0.95
     * leaves clean audio untouched and only acts on rail-edge transients above ±1
     * (e.g. the 2× DC-blocker overshoot on heavily-saturated inputs).
     *
     * @param x input sample.
     * @param threshold linear-region cutoff in [0, 1). Default 0.95. Lower = more
     *   saturation character on bulk peaks; higher = more identity, hard-clip-ish.
     */
    inline fun softCap(x: Double, threshold: Double = 0.95): Double {
        val absX = if (x < 0.0) -x else x
        if (absX <= threshold) return x
        val headroom = 1.0 - threshold
        val saturated = threshold + headroom * fastTanh((absX - threshold) / headroom)
        return if (x < 0.0) -saturated else saturated
    }
}
