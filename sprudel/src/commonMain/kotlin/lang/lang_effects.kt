/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel._applyControlFromParams
import io.peekandpoke.klang.sprudel._liftOrReinterpretNumericalField
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.pattern.ReinterpretPattern.Companion.reinterpretVoice
// -- distort() / dist() -----------------------------------------------------------------------------------------------

private val distortMutation = voiceSetter {
    val str = it?.toString() ?: return@voiceSetter
    distort = str.toDoubleOrNull() ?: distort
}

private fun applyDistort(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, distortMutation)
}

/**
 * Applies waveshaper distortion to this pattern.
 *
 * Higher values produce more harmonic saturation and clipping. Works well on synth
 * bass lines and leads; combine with `lpf` to tame harsh high frequencies.
 *
 * Takes the drive amount plus optional shape and oversampling parameters
 * to set both distortion amount and waveshaper shape at once. Available shapes:
 *  - **Symmetric soft:** `soft` (tanh), `gentle`, `softsat`, `cubic`, `exp`, `sineshaper`.
 *  - **Symmetric hard / wavefolding:** `hard`, `zerosquare`, `chebyshev`, `fold`, `linearfold`.
 *  - **Asymmetric (even harmonics):** `diode`, `tube`, `asym`, `stompbox`, `rectify`.
 *
 * When [amount] is omitted, the pattern's own numeric values are reinterpreted as distortion amounts.
 *
 * @param amount The distortion amount.
 * @param shape Waveshaper curve name: soft, hard, gentle, softsat, cubic, exp, sineshaper,
 *   zerosquare, chebyshev, fold, linearfold, diode, tube, asym, stompbox, rectify. Omit to leave it unchanged.
 * @param oversample Oversampling factor (2/4/8). Omit to leave it unchanged.
 *   Omit to reinterpret the pattern's values as distortion.
 * @param-tool amount SprudelDistortEditor, SprudelDistortSequenceEditor
 * @return A new pattern with distortion applied.
 *
 * ```KlangScript(Playable)
 * note("c2 eb2 g2").s("sawtooth").distort(0.5)   // moderate distortion (default shape)
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").distort("<0 0.3 0.6 1.0>")        // escalating distortion each beat
 * ```
 *
 * ```KlangScript(Playable)
 * note("c2 eb2 g2").s("sawtooth").distort(0.5, "soft")       // warm tanh saturation
 * ```
 *
 * ```KlangScript(Playable)
 * note("c2 eb2 g2").s("sawtooth").distort(0.7, "hard")       // aggressive hard clipping
 * ```
 *
 * ```KlangScript(Playable)
 * note("c2 eb2 g2").s("sawtooth").distort(0.4, "gentle")     // smooth x/(1+|x|) saturation
 * ```
 *
 * ```KlangScript(Playable)
 * note("c2 eb2 g2").s("sawtooth").distort(0.6, "cubic")      // tube-like, 3rd harmonic
 * ```
 *
 * ```KlangScript(Playable)
 * note("c2 eb2 g2").s("sawtooth").distort(0.5, "diode")      // asymmetric, even harmonics
 * ```
 *
 * ```KlangScript(Playable)
 * note("c2 eb2 g2").s("sawtooth").distort(0.8, "fold")       // sine wavefolding, metallic
 * ```
 *
 * ```KlangScript(Playable)
 * note("c2 eb2 g2").s("sawtooth").distort(0.5, "chebyshev")  // T3 polynomial, tape saturation
 * ```
 *
 * ```KlangScript(Playable)
 * note("c2 eb2 g2").s("sawtooth").distort(0.7, "rectify")    // full-wave rectification, octave-up
 * ```
 *
 * ```KlangScript(Playable)
 * note("c2 eb2 g2").s("sawtooth").distort(0.6, "exp")        // exponential, transistor-style
 * ```
 *
 * ```KlangScript(Playable)
 * note("c2 eb2 g2").s("sawtooth").distort(0.5, "softsat")    // gentlest soft saturation
 * ```
 *
 * ```KlangScript(Playable)
 * note("c2 eb2 g2").s("sawtooth").distort(0.7, "tube")       // shifted-tanh tube, warm + asymmetric
 * ```
 *
 * ```KlangScript(Playable)
 * note("c2 eb2 g2").s("sawtooth").distort(0.8, "linearfold") // triangle wavefolding, sharper than fold
 * ```
 *
 * ```KlangScript(Playable)
 * note("c2 eb2 g2").s("sawtooth").distort(0.6, "zerosquare") // pushes signal toward square
 * ```
 *
 * ```KlangScript(Playable)
 * note("c2 eb2 g2").s("sawtooth").distort(0.7, "stompbox")   // asymmetric diode pedal grit
 * ```
 *
 * ```KlangScript(Playable)
 * note("c2 eb2 g2").s("sawtooth").distort(0.6, "asym")       // polynomial asymmetry, even+odd
 * ```
 *
 * ```KlangScript(Playable)
 * note("c2 eb2 g2").s("sawtooth").distort(0.5, "sineshaper") // normalised sine fold (peak at unity)
 * ```
 * @alias dist
 * @category effects
 * @tags distort, dist, distortion, waveshaper, overdrive
 */
@KlangScript.Function
fun SprudelPattern.distort(amount: PatternLike? = null, shape: PatternLike? = null, oversample: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern {
    // A tail-only call must not touch amount: reinterpret runs only on a fully bare call.
    var p = if (amount != null || !(shape != null || oversample != null)) {
        applyDistort(this, listOfNotNull(amount).asSprudelDslArgs(callInfo))
    } else {
        this
    }
    if (shape != null) p = p.distortshape(shape, callInfo?.forParam(1))
    if (oversample != null) p = p.distos(oversample, callInfo?.forParam(2))
    return p
}

/**
 * Parses this string as a pattern, then applies waveshaper distortion.
 *
 * When [amount] is omitted, the pattern's own numeric values are reinterpreted as distortion amounts.
 *
 * @param amount The distortion amount. Higher values produce more saturation and clipping.
 * @param shape Waveshaper curve name: soft, hard, gentle, softsat, cubic, exp, sineshaper,
 *   zerosquare, chebyshev, fold, linearfold, diode, tube, asym, stompbox, rectify. Omit to leave it unchanged.
 * @param oversample Oversampling factor (2/4/8). Omit to leave it unchanged.
 *   Omit to reinterpret the pattern's values as distortion.
 * @return A new pattern with distortion applied.
 *
 * ```KlangScript(Playable)
 * "c2 eb2 g2".distort(0.5).note().s("sawtooth")  // moderate distortion on bass notes
 * ```
 */
@KlangScript.Function
fun String.distort(amount: PatternLike? = null, shape: PatternLike? = null, oversample: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).distort(amount, shape, oversample, callInfo)

/**
 * Returns a [PatternMapperFn] that applies waveshaper distortion.
 *
 * Use the returned mapper as a transform argument or apply it to a pattern via `.apply(...)`.
 * When [amount] is omitted, the pattern's own numeric values are reinterpreted as distortion amounts.
 *
 * @param amount The distortion amount.
 * @param shape Waveshaper curve name: soft, hard, gentle, softsat, cubic, exp, sineshaper,
 *   zerosquare, chebyshev, fold, linearfold, diode, tube, asym, stompbox, rectify. Omit to leave it unchanged.
 * @param oversample Oversampling factor (2/4/8). Omit to leave it unchanged.
 *   Omit to reinterpret the pattern's values as distortion.
 * @param-tool amount SprudelDistortEditor, SprudelDistortSequenceEditor
 * @return A [PatternMapperFn] that applies waveshaper distortion.
 *
 * ```KlangScript(Playable)
 * note("c2 eb2 g2").s("sawtooth").apply(distort(0.5))  // moderate distortion
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").firstOf(4, distort(0.8))  // heavy distortion on every 4th cycle
 * ```
 * @alias dist
 * @category effects
 * @tags distort, dist, distortion, waveshaper, overdrive
 */
@KlangScript.Function
fun distort(amount: PatternLike? = null, shape: PatternLike? = null, oversample: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.distort(amount, shape, oversample, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that applies waveshaper distortion after the previous mapper.
 *
 * @param amount The distortion amount. Omit to reinterpret the pattern's values as distortion.
 * @param shape Waveshaper curve name: soft, hard, gentle, softsat, cubic, exp, sineshaper,
 *   zerosquare, chebyshev, fold, linearfold, diode, tube, asym, stompbox, rectify. Omit to leave it unchanged.
 * @param oversample Oversampling factor (2/4/8). Omit to leave it unchanged.
 * @return A new [PatternMapperFn] chaining this distortion after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c2 eb2 g2").s("sawtooth").apply(crush(4).distort(0.5))  // bit-crush then distort
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").firstOf(4, roomWet(0.3).distort(0.8))  // room + distortion every 4th cycle
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.distort(amount: PatternLike? = null, shape: PatternLike? = null, oversample: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.distort(amount, shape, oversample, callInfo) }

/**
 * Alias for [distort]. Applies waveshaper distortion to this pattern.
 *
 * When [amount] is omitted, the pattern's own numeric values are reinterpreted as distortion amounts.
 *
 * @param amount The distortion amount.
 * @param shape Waveshaper curve name: soft, hard, gentle, softsat, cubic, exp, sineshaper,
 *   zerosquare, chebyshev, fold, linearfold, diode, tube, asym, stompbox, rectify. Omit to leave it unchanged.
 * @param oversample Oversampling factor (2/4/8). Omit to leave it unchanged.
 *   Omit to reinterpret the pattern's values as distortion.
 * @param-tool amount SprudelDistortEditor, SprudelDistortSequenceEditor
 * @return A new pattern with distortion applied.
 *
 * ```KlangScript(Playable)
 * note("c2 eb2 g2").s("sawtooth").dist(0.5)   // moderate distortion
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").dist("<0 0.3 0.6 1.0>")        // escalating distortion each beat
 * ```
 *
 * ```KlangScript(Playable)
 * seq("0 0.5 1.0").dist()                     // reinterpret values as distortion
 * ```
 * @alias distort
 * @category effects
 * @tags dist, distort, distortion, waveshaper, overdrive
 */
@KlangScript.Function
fun SprudelPattern.dist(amount: PatternLike? = null, shape: PatternLike? = null, oversample: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.distort(amount, shape, oversample, callInfo)

/**
 * Alias for [distort]. Parses this string as a pattern, then applies waveshaper distortion.
 *
 * When [amount] is omitted, the pattern's own numeric values are reinterpreted as distortion amounts.
 *
 * @param amount The distortion amount. Higher values produce more saturation and clipping.
 * @param shape Waveshaper curve name: soft, hard, gentle, softsat, cubic, exp, sineshaper,
 *   zerosquare, chebyshev, fold, linearfold, diode, tube, asym, stompbox, rectify. Omit to leave it unchanged.
 * @param oversample Oversampling factor (2/4/8). Omit to leave it unchanged.
 *   Omit to reinterpret the pattern's values as distortion.
 * @return A new pattern with distortion applied.
 *
 * ```KlangScript(Playable)
 * "c2 eb2 g2".dist(0.5).note().s("sawtooth")  // moderate distortion on bass notes
 * ```
 */
@KlangScript.Function
fun String.dist(amount: PatternLike? = null, shape: PatternLike? = null, oversample: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).distort(amount, shape, oversample, callInfo)

/**
 * Returns a [PatternMapperFn] that applies waveshaper distortion. Alias for [distort].
 *
 * Use the returned mapper as a transform argument or apply it to a pattern via `.apply(...)`.
 * When [amount] is omitted, the pattern's own numeric values are reinterpreted as distortion amounts.
 *
 * @param amount The distortion amount.
 * @param shape Waveshaper curve name: soft, hard, gentle, softsat, cubic, exp, sineshaper,
 *   zerosquare, chebyshev, fold, linearfold, diode, tube, asym, stompbox, rectify. Omit to leave it unchanged.
 * @param oversample Oversampling factor (2/4/8). Omit to leave it unchanged.
 *   Omit to reinterpret the pattern's values as distortion.
 * @param-tool amount SprudelDistortEditor, SprudelDistortSequenceEditor
 * @return A [PatternMapperFn] that applies waveshaper distortion.
 *
 * ```KlangScript(Playable)
 * note("c2 eb2 g2").s("sawtooth").apply(dist(0.5))  // moderate distortion
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").firstOf(4, dist(0.8))  // heavy distortion on every 4th cycle
 * ```
 * @alias distort
 * @category effects
 * @tags dist, distort, distortion, waveshaper, overdrive
 */
@KlangScript.Function
fun dist(amount: PatternLike? = null, shape: PatternLike? = null, oversample: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.distort(amount, shape, oversample, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that applies waveshaper distortion (alias for [distort]) after the previous mapper.
 *
 * @param amount The distortion amount. Omit to reinterpret the pattern's values as distortion.
 * @param shape Waveshaper curve name: soft, hard, gentle, softsat, cubic, exp, sineshaper,
 *   zerosquare, chebyshev, fold, linearfold, diode, tube, asym, stompbox, rectify. Omit to leave it unchanged.
 * @param oversample Oversampling factor (2/4/8). Omit to leave it unchanged.
 * @return A new [PatternMapperFn] chaining this distortion after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c2 eb2 g2").s("sawtooth").apply(crush(4).dist(0.5))  // bit-crush then distort
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").firstOf(4, roomWet(0.3).dist(0.8))  // room + distortion every 4th cycle
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.dist(amount: PatternLike? = null, shape: PatternLike? = null, oversample: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.distort(amount, shape, oversample, callInfo) }

// -- distos() / distortoversampling() ---------------------------------------------------------------------------------

private val distortOversampleMutation = voiceSetter { distortOversample = it?.toString()?.toDoubleOrNull()?.toInt() }

private fun applyDistortOversample(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, distortOversampleMutation)
}

/**
 * Sets the distortion oversampling factor.
 *
 * Higher values reduce aliasing from distortion at the cost of more CPU.
 * The factor is floored to the nearest power of 2. Values <= 1 disable oversampling.
 *
 * @param factor The oversampling factor (2=2x, 4=4x, 8=8x). Non-power-of-2 floored.
 * @return A new pattern with distortion oversampling applied.
 *
 * ```KlangScript(Playable)
 * note("c2 eb2 g2").s("sawtooth").distort(0.8).distos(2)   // 2x oversampled distortion
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").distort(0.8, "exp").distos(4)                 // 4x oversampled exp distortion
 * ```
 * @alias distortOversampling
 * @category effects
 * @tags distos, distort, oversampling, aliasing, quality
 */
@KlangScript.Function
fun SprudelPattern.distos(factor: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyDistortOversample(this, listOfNotNull(factor).asSprudelDslArgs(callInfo))

@KlangScript.Function
fun String.distos(factor: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).distos(factor, callInfo)

@KlangScript.Function
fun distos(factor: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.distos(factor, callInfo) }

@KlangScript.Function
fun PatternMapperFn.distos(factor: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.distos(factor, callInfo) }

@KlangScript.Function
fun SprudelPattern.distortOversampling(factor: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyDistortOversample(this, listOfNotNull(factor).asSprudelDslArgs(callInfo))

@KlangScript.Function
fun String.distortOversampling(factor: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).distortOversampling(factor, callInfo)

@KlangScript.Function
fun distortOversampling(factor: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.distortOversampling(factor, callInfo) }

@KlangScript.Function
fun PatternMapperFn.distortOversampling(factor: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.distortOversampling(factor, callInfo) }

// -- distortshape() / distshape() / dshape() --------------------------------------------------------------------------

private val distortShapeMutation = voiceSetter { shape -> distortShape = shape?.toString()?.lowercase() }

private fun applyDistortShape(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._applyControlFromParams(args, distortShapeMutation) { src, ctrl ->
        src.distortShape = ctrl.distortShape
        src
    }
}

/**
 * Sets the distortion waveshaper shape for this pattern.
 *
 * Controls which waveshaping algorithm is used for distortion. Available shapes:
 *  - **Symmetric soft:** `soft` (tanh), `gentle`, `softsat`, `cubic`, `exp`, `sineshaper`.
 *  - **Symmetric hard / wavefolding:** `hard`, `zerosquare`, `chebyshev`, `fold`, `linearfold`.
 *  - **Asymmetric (even harmonics):** `diode`, `tube`, `asym`, `stompbox`, `rectify`.
 *
 * @param shape The waveshaper shape name. One of: soft, hard, gentle, softsat, cubic, exp, sineshaper, zerosquare, chebyshev, fold, linearfold, diode, tube, asym, stompbox, rectify.
 * @param-tool shape SprudelDistortShapeEditor, SprudelDistortShapeSequenceEditor
 * @return A new pattern with the distortion shape applied.
 *
 * ```KlangScript(Playable)
 * note("c2 eb2 g2").s("sawtooth").distort(0.5).distortshape("fold")   // wavefolding distortion
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").distort(0.7).distortshape("<soft hard fold exp>")   // cycle through shapes
 * ```
 *
 * @alias distshape, dshape
 * @category effects
 * @tags distortshape, distshape, dshape, distort, shape, waveshaper
 */
@KlangScript.Function
fun SprudelPattern.distortshape(shape: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyDistortShape(this, listOf(shape).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets the distortion waveshaper shape.
 *
 * See [SprudelPattern.distortshape] for the full list of available shapes.
 *
 * @param shape The waveshaper shape name.
 *
 * ```KlangScript(Playable)
 * "c2 eb2 g2".distortshape("fold").distort(0.5).note().s("sawtooth")
 * ```
 */
@KlangScript.Function
fun String.distortshape(shape: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).distortshape(shape, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the distortion waveshaper shape.
 *
 * Available shapes: soft, hard, gentle, softsat, cubic, exp, sineshaper, zerosquare,
 * chebyshev, fold, linearfold, diode, tube, asym, stompbox, rectify. See [SprudelPattern.distortshape]
 * for grouping (symmetric soft / symmetric hard / asymmetric).
 *
 * @param shape The waveshaper shape name.
 * @param-tool shape SprudelDistortShapeEditor, SprudelDistortShapeSequenceEditor
 * @return A [PatternMapperFn] that sets the distortion shape.
 *
 * ```KlangScript(Playable)
 * note("c2 eb2 g2").apply(distortshape("fold"))   // wavefolding via mapper
 * ```
 *
 * @alias distshape, dshape
 * @category effects
 * @tags distortshape, distshape, dshape, distort, shape, waveshaper
 */
@KlangScript.Function
fun distortshape(shape: PatternLike, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.distortshape(shape, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the distortion waveshaper shape after the previous mapper.
 *
 * See [SprudelPattern.distortshape] for the full list of available shapes.
 *
 * @param shape The waveshaper shape name.
 * @return A new [PatternMapperFn] chaining this distortion shape after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c2 eb2 g2").apply(distort(0.5).distortshape("fold"))   // amount then shape
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.distortshape(shape: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.distortshape(shape, callInfo) }

/**
 * Alias for [distortshape]. Sets the distortion waveshaper shape for this pattern.
 *
 * Available shapes: soft, hard, gentle, softsat, cubic, exp, sineshaper, zerosquare,
 * chebyshev, fold, linearfold, diode, tube, asym, stompbox, rectify. See [SprudelPattern.distortshape]
 * for grouping.
 *
 * @param shape The waveshaper shape name.
 * @param-tool shape SprudelDistortShapeEditor, SprudelDistortShapeSequenceEditor
 * @return A new pattern with the distortion shape applied.
 *
 * ```KlangScript(Playable)
 * note("c2 eb2 g2").s("sawtooth").distort(0.5).distshape("hard")   // hard clipping
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").distort(0.7).distshape("<soft hard fold exp>")   // cycle through shapes
 * ```
 *
 * @alias distortshape, dshape
 * @category effects
 * @tags distshape, distortshape, dshape, distort, shape, waveshaper
 */
@KlangScript.Function
fun SprudelPattern.distshape(shape: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.distortshape(shape, callInfo)

/**
 * Alias for [distortshape]. Parses this string as a pattern and sets the distortion waveshaper shape.
 *
 * See [SprudelPattern.distortshape] for the full list of available shapes.
 *
 * @param shape The waveshaper shape name.
 *
 * ```KlangScript(Playable)
 * "c2 eb2 g2".distshape("fold").distort(0.5).note().s("sawtooth")
 * ```
 */
@KlangScript.Function
fun String.distshape(shape: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).distortshape(shape, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the distortion waveshaper shape. Alias for [distortshape].
 *
 * Available shapes: soft, hard, gentle, softsat, cubic, exp, sineshaper, zerosquare,
 * chebyshev, fold, linearfold, diode, tube, asym, stompbox, rectify.
 *
 * @param shape The waveshaper shape name.
 * @return A [PatternMapperFn] that sets the distortion shape.
 *
 * ```KlangScript(Playable)
 * note("c2 eb2 g2").apply(distshape("fold"))   // wavefolding via mapper
 * ```
 *
 * @alias distortshape, dshape
 * @category effects
 * @tags distshape, distortshape, dshape, distort, shape, waveshaper
 */
@KlangScript.Function
fun distshape(shape: PatternLike, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.distortshape(shape, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the distortion waveshaper shape (alias for [distortshape])
 * after the previous mapper.
 *
 * See [SprudelPattern.distortshape] for the full list of available shapes.
 *
 * @param shape The waveshaper shape name.
 * @return A new [PatternMapperFn] chaining this distortion shape after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c2 eb2 g2").apply(distort(0.5).distshape("fold"))   // amount then shape
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.distshape(shape: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.distortshape(shape, callInfo) }

/**
 * Alias for [distortshape]. Sets the distortion waveshaper shape for this pattern.
 *
 * Available shapes: soft, hard, gentle, softsat, cubic, exp, sineshaper, zerosquare,
 * chebyshev, fold, linearfold, diode, tube, asym, stompbox, rectify. See [SprudelPattern.distortshape]
 * for grouping.
 *
 * @param shape The waveshaper shape name.
 * @param-tool shape SprudelDistortShapeEditor, SprudelDistortShapeSequenceEditor
 * @return A new pattern with the distortion shape applied.
 *
 * ```KlangScript(Playable)
 * note("c2 eb2 g2").s("sawtooth").distort(0.5).dshape("hard")   // hard clipping
 * ```
 *
 * @alias distortshape, distshape
 * @category effects
 * @tags dshape, distortshape, distshape, distort, shape, waveshaper
 */
@KlangScript.Function
fun SprudelPattern.dshape(shape: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.distortshape(shape, callInfo)

/**
 * Alias for [distortshape]. Parses this string as a pattern and sets the distortion waveshaper shape.
 *
 * See [SprudelPattern.distortshape] for the full list of available shapes.
 *
 * @param shape The waveshaper shape name.
 *
 * ```KlangScript(Playable)
 * "c2 eb2 g2".dshape("fold").distort(0.5).note().s("sawtooth")
 * ```
 */
@KlangScript.Function
fun String.dshape(shape: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).distortshape(shape, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the distortion waveshaper shape. Alias for [distortshape].
 *
 * Available shapes: soft, hard, gentle, softsat, cubic, exp, sineshaper, zerosquare,
 * chebyshev, fold, linearfold, diode, tube, asym, stompbox, rectify.
 *
 * @param shape The waveshaper shape name.
 * @return A [PatternMapperFn] that sets the distortion shape.
 *
 * ```KlangScript(Playable)
 * note("c2 eb2 g2").apply(dshape("fold"))   // wavefolding via mapper
 * ```
 *
 * @alias distortshape, distshape
 * @category effects
 * @tags dshape, distortshape, distshape, distort, shape, waveshaper
 */
@KlangScript.Function
fun dshape(shape: PatternLike, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.distortshape(shape, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the distortion waveshaper shape (alias for [distortshape])
 * after the previous mapper.
 *
 * See [SprudelPattern.distortshape] for the full list of available shapes.
 *
 * @param shape The waveshaper shape name.
 * @return A new [PatternMapperFn] chaining this distortion shape after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c2 eb2 g2").apply(distort(0.5).dshape("fold"))   // amount then shape
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.dshape(shape: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.distortshape(shape, callInfo) }

// -- Named distortion shapes ------------------------------------------------------------------------------------------
// -- crush() ----------------------------------------------------------------------------------------------------------

private val crushMutation = voiceSetter { crush = it?.asDoubleOrNull() }

private fun applyCrush(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, crushMutation)
}

/**
 * Applies bit-crushing (bit-depth reduction) to this pattern.
 *
 * Lower values reduce the bit depth, producing a lo-fi, crunchy digital sound.
 * A value of 1 is maximum crush; higher values approach the original sound.
 * When [amount] is omitted, the pattern's own numeric values are reinterpreted as crush amounts.
 *
 * @param amount The bit-depth reduction amount. Lower values produce more lo-fi character.
 *   Omit to reinterpret the pattern's values as crush.
 * @return A new pattern with bit-crushing applied.
 *
 * ```KlangScript(Playable)
 * s("bd sd hh").crush(4)              // 4-bit lo-fi crunch
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").crush("<16 8 4 2>")    // decreasing bit depth each beat
 * ```
 *
 * ```KlangScript(Playable)
 * seq("16 8 4 2").crush()             // reinterpret values as crush
 * ```
 *
 * @category effects
 * @tags crush, bitcrush, lofi, bitdepth, distortion
 */
@KlangScript.Function
fun SprudelPattern.crush(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyCrush(this, listOfNotNull(amount).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and applies bit-crushing.
 *
 * When [amount] is omitted, the string's numeric values are reinterpreted as crush amounts.
 *
 * @param amount The bit-depth reduction amount. Lower values produce more lo-fi character.
 *   Omit to reinterpret the pattern's values as crush.
 * @return A new pattern with bit-crushing applied.
 *
 * ```KlangScript(Playable)
 * "bd sd hh".crush(4).s()            // 4-bit lo-fi crunch on samples
 * ```
 */
@KlangScript.Function
fun String.crush(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).crush(amount, callInfo)

/**
 * Returns a [PatternMapperFn] that applies bit-crushing.
 *
 * Use the returned mapper as a transform argument or apply it to a pattern via `.apply(...)`.
 * When [amount] is omitted, the pattern's own numeric values are reinterpreted as crush amounts.
 *
 * @param amount The bit-depth reduction amount. Lower values produce more lo-fi character.
 *   Omit to reinterpret the pattern's values as crush.
 * @return A [PatternMapperFn] that applies bit-crushing.
 *
 * ```KlangScript(Playable)
 * s("bd sd hh").apply(crush(4))              // 4-bit crunch via mapper
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, crush(2))            // maximum crush every 4th cycle
 * ```
 *
 * @category effects
 * @tags crush, bitcrush, lofi, bitdepth, distortion
 */
@KlangScript.Function
fun crush(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.crush(amount, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that applies bit-crushing after the previous mapper.
 *
 * @param amount The bit-depth reduction amount. Lower values produce more lo-fi character.
 *   Omit to reinterpret the pattern's values as crush.
 * @return A new [PatternMapperFn] chaining this bit-crushing after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3*4").apply(coarse(4).crush(4))   // sample-rate reduction then bit-crush
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, distort(0.5).crush(2))   // distort + max crush every 4th cycle
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.crush(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.crush(amount, callInfo) }

// -- crushos() / crushoversampling() ----------------------------------------------------------------------------------

private val crushOversampleMutation = voiceSetter { crushOversample = it?.toString()?.toIntOrNull() }

private fun applyCrushOversample(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, crushOversampleMutation)
}

/**
 * Sets the bit-crush oversampling factor.
 *
 * Higher values reduce aliasing from the staircase quantization at the cost of more CPU.
 * The factor is floored to the nearest power of 2. Values <= 1 disable oversampling.
 *
 * @param factor The oversampling factor (2=2x, 4=4x, 8=8x). Non-power-of-2 floored.
 * @return A new pattern with crush oversampling applied.
 *
 * ```KlangScript(Playable)
 * note("c2 eb2 g2").s("supersaw").crush(2).crushos(4)   // 4x oversampled bit-crush
 * ```
 *
 * @alias crushOversampling
 * @category effects
 * @tags crushos, crush, oversampling, aliasing, quality
 */
@KlangScript.Function
fun SprudelPattern.crushos(factor: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyCrushOversample(this, listOfNotNull(factor).asSprudelDslArgs(callInfo))

@KlangScript.Function
fun String.crushos(factor: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).crushos(factor, callInfo)

@KlangScript.Function
fun crushos(factor: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.crushos(factor, callInfo) }

@KlangScript.Function
fun PatternMapperFn.crushos(factor: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.crushos(factor, callInfo) }

@KlangScript.Function
fun SprudelPattern.crushOversampling(factor: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyCrushOversample(this, listOfNotNull(factor).asSprudelDslArgs(callInfo))

@KlangScript.Function
fun String.crushOversampling(factor: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).crushOversampling(factor, callInfo)

@KlangScript.Function
fun crushOversampling(factor: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.crushOversampling(factor, callInfo) }

@KlangScript.Function
fun PatternMapperFn.crushOversampling(factor: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.crushOversampling(factor, callInfo) }

// -- coarse() ---------------------------------------------------------------------------------------------------------

private val coarseMutation = voiceSetter { coarse = it?.asDoubleOrNull() }

private fun applyCoarse(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, coarseMutation)
}

/**
 * Applies sample-rate reduction (downsampling) to this pattern.
 *
 * Reduces the effective sample rate of the audio, producing a gritty lo-fi effect.
 * Higher values cause more aliasing; combine with `crush` for classic lo-fi aesthetics.
 * When [amount] is omitted, the pattern's own numeric values are reinterpreted as coarse amounts.
 *
 * @param amount The downsampling amount. Higher values produce more aliasing and lo-fi character.
 *   Omit to reinterpret the pattern's values as coarse.
 * @return A new pattern with sample-rate reduction applied.
 *
 * ```KlangScript(Playable)
 * s("bd sd").coarse(4)               // moderate sample-rate reduction
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*8").coarse("<1 2 4 8>")   // escalating downsampling
 * ```
 *
 * ```KlangScript(Playable)
 * seq("1 2 4 8").coarse()            // reinterpret values as coarse
 * ```
 *
 * @category effects
 * @tags coarse, samplerate, lofi, aliasing, downsample
 */
@KlangScript.Function
fun SprudelPattern.coarse(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyCoarse(this, listOfNotNull(amount).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and applies sample-rate reduction.
 *
 * When [amount] is omitted, the string's numeric values are reinterpreted as coarse amounts.
 *
 * @param amount The downsampling amount. Higher values produce more aliasing and lo-fi character.
 *   Omit to reinterpret the pattern's values as coarse.
 * @return A new pattern with sample-rate reduction applied.
 *
 * ```KlangScript(Playable)
 * "bd sd".coarse(4).s()              // lo-fi samples
 * ```
 */
@KlangScript.Function
fun String.coarse(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).coarse(amount, callInfo)

/**
 * Returns a [PatternMapperFn] that applies sample-rate reduction.
 *
 * Use the returned mapper as a transform argument or apply it to a pattern via `.apply(...)`.
 * When [amount] is omitted, the pattern's own numeric values are reinterpreted as coarse amounts.
 *
 * @param amount The downsampling amount. Higher values produce more aliasing and lo-fi character.
 *   Omit to reinterpret the pattern's values as coarse.
 * @return A [PatternMapperFn] that applies sample-rate reduction.
 *
 * ```KlangScript(Playable)
 * s("bd sd").apply(coarse(4))        // lo-fi via mapper
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, coarse(8))   // heavy downsampling every 4th cycle
 * ```
 *
 * @category effects
 * @tags coarse, samplerate, lofi, aliasing, downsample
 */
@KlangScript.Function
fun coarse(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.coarse(amount, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that applies sample-rate reduction after the previous mapper.
 *
 * @param amount The downsampling amount. Higher values produce more aliasing and lo-fi character.
 *   Omit to reinterpret the pattern's values as coarse.
 * @return A new [PatternMapperFn] chaining this sample-rate reduction after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3*4").apply(crush(4).coarse(4))    // bit-crush then downsample
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd").every(4, coarse(8).distort(0.5))  // heavy lo-fi every 4th cycle
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.coarse(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.coarse(amount, callInfo) }

// -- coarseos() / coarseoversampling() --------------------------------------------------------------------------------

private val coarseOversampleMutation = voiceSetter { coarseOversample = it?.toString()?.toIntOrNull() }

private fun applyCoarseOversample(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, coarseOversampleMutation)
}

/**
 * Sets the coarse (sample-rate reducer) oversampling factor.
 *
 * Higher values reduce aliasing from the sample-hold step edges at the cost of more CPU.
 * The factor is floored to the nearest power of 2. Values <= 1 disable oversampling.
 *
 * Note: coarse is often used to *produce* aliased metallic character intentionally —
 * only reach for `coarseos` when you want the downsampling feel without the aliasing hash.
 *
 * @param factor The oversampling factor (2=2x, 4=4x, 8=8x). Non-power-of-2 floored.
 * @return A new pattern with coarse oversampling applied.
 *
 * ```KlangScript(Playable)
 * note("c3*4").s("sawtooth").coarse(4).coarseos(4)   // 4x oversampled downsample
 * ```
 *
 * @alias coarseOversampling
 * @category effects
 * @tags coarseos, coarse, oversampling, aliasing, quality
 */
@KlangScript.Function
fun SprudelPattern.coarseos(factor: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyCoarseOversample(this, listOfNotNull(factor).asSprudelDslArgs(callInfo))

@KlangScript.Function
fun String.coarseos(factor: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).coarseos(factor, callInfo)

@KlangScript.Function
fun coarseos(factor: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.coarseos(factor, callInfo) }

@KlangScript.Function
fun PatternMapperFn.coarseos(factor: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.coarseos(factor, callInfo) }

@KlangScript.Function
fun SprudelPattern.coarseOversampling(factor: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyCoarseOversample(this, listOfNotNull(factor).asSprudelDslArgs(callInfo))

@KlangScript.Function
fun String.coarseOversampling(factor: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).coarseOversampling(factor, callInfo)

@KlangScript.Function
fun coarseOversampling(factor: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.coarseOversampling(factor, callInfo) }

@KlangScript.Function
fun PatternMapperFn.coarseOversampling(factor: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.coarseOversampling(factor, callInfo) }

// -- roomWet() -----------------------------------------------------------------------------------------------------------

private val roomMutation = voiceSetter {
    room = it?.toString()?.toDoubleOrNull() ?: room
}

private fun applyRoom(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    // No args: reinterpret pattern's own values as room mix (backward compat)
    if (args.isEmpty()) {
        return source.reinterpretVoice {
            it.clone().apply { room = value?.asDouble }
        }
    }

    return source._applyControlFromParams(args, roomMutation) { src, ctrl ->
        src.room = ctrl.room ?: src.room
        src
    }
}

/**
 * Sets the reverb SEND amount for this pattern — reverb is a send, not a crossfade: the dry
 * signal reaches the mix untouched at full level, and [wet] only scales how much of the voice
 * feeds the orbit's shared reverb return (0 = no reverb, 1 = full send). That is why there is
 * no `roomFloor`: a send has nothing to floor.
 *
 * Use with `roomsize` to control the reverb tail length, and `orbit` to send
 * multiple patterns to separate reverb buses.
 * When [wet] is omitted, the pattern's own numeric values are reinterpreted as the send amount.
 *
 * **Compound form** — `roomWet(wet, size, fade, lowpass, dim)` sets several at once. Mind the
 * scales, they are not the same:
 *
 * | slot | param | scale |
 * |---|---|---|
 * | 1 | wet | 0..1 |
 * | 2 | `roomsize` | **~0..10** (3 ≈ 1 s tail, 5 ≈ 1.4 s, 10 ≈ 12.5 s) |
 * | 3 | `roomfade` | **0..1** — *overrides* slot 2; not a time |
 * | 4 | `roomlp` | Hz |
 * | 5 | `roomdim` | currently unused by the engine |
 *
 * So `roomWet(0.3, 5, 0.1)` is send 0.3 with a **0.1** tail — the `5` is inert, because slot 3
 * wins. The master bus takes the same values as `MasterFx.reverb().wet(0.3).roomFade(0.1)`.
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").clip(0.5).s("sine").roomWet(0.5)   // 50% reverb send
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").clip(0.5).roomWet("<0 0.3 0.6 0.9>").roomsize(4)   // increasing send
 * ```
 *
 * ```KlangScript(Playable)
 * seq("0 0.5 1.0").roomWet()   // reinterpret values as the reverb send
 * ```
 *
 * @param wet The reverb send amount (0–1). Omit to reinterpret the pattern's values.
 * @param size Room size (~0–10). Omit to leave it unchanged.
 * @param fade Tail override (0–1), wins over size. Omit to leave it unchanged.
 * @param lowpass Lowpass on the reverb tail in Hz. Omit to leave it unchanged.
 * @param dim Currently unused by the engine.
 * @param-tool wet SprudelReverbEditor, SprudelReverbSequenceEditor
 * @return A new pattern with the reverb send applied.
 * @category effects
 * @tags room, reverb, wet, mix, space
 */
@KlangScript.Function
fun SprudelPattern.roomWet(wet: PatternLike? = null, size: PatternLike? = null, fade: PatternLike? = null, lowpass: PatternLike? = null, dim: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern {
    // A tail-only call must not touch wet: reinterpret runs only on a fully bare call.
    var p = if (wet != null || !(size != null || fade != null || lowpass != null || dim != null)) {
        applyRoom(this, listOfNotNull(wet).asSprudelDslArgs(callInfo))
    } else {
        this
    }
    if (size != null) p = p.roomsize(size, callInfo?.forParam(1))
    if (fade != null) p = p.roomfade(fade, callInfo?.forParam(2))
    if (lowpass != null) p = p.roomlp(lowpass, callInfo?.forParam(3))
    if (dim != null) p = p.roomdim(dim, callInfo?.forParam(4))
    return p
}

/**
 * Parses this string as a pattern and sets the reverb send (see [SprudelPattern.roomWet]).
 *
 * When [wet] is omitted, the string's numeric values are reinterpreted as the send amount.
 *
 * @param wet The reverb send amount (0–1). Omit to reinterpret the pattern's values.
 * @return A new pattern with the reverb send applied.
 *
 * ```KlangScript(Playable)
 * "c3 e3 g3".roomWet(0.5).note().clip(0.5)    // 50% reverb send on bass notes
 * ```
 */
@KlangScript.Function
fun String.roomWet(wet: PatternLike? = null, size: PatternLike? = null, fade: PatternLike? = null, lowpass: PatternLike? = null, dim: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).roomWet(wet, size, fade, lowpass, dim, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the reverb send (see [SprudelPattern.roomWet]).
 *
 * Use the returned mapper as a transform argument or apply it to a pattern via `.apply(...)`.
 * When [wet] is omitted, the pattern's own numeric values are reinterpreted as the send amount.
 *
 * @param wet The reverb send amount (0–1). Omit to reinterpret the pattern's values.
 * @return A [PatternMapperFn] that sets the reverb send.
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").apply(roomWet(0.5)).clip(0.5)     // 50% reverb send via mapper
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, roomWet(0.9)).clip(0.5)      // heavy reverb every 4th cycle
 * ```
 *
 * @category effects
 * @tags room, reverb, wet, mix, space
 */
@KlangScript.Function
fun roomWet(wet: PatternLike? = null, size: PatternLike? = null, fade: PatternLike? = null, lowpass: PatternLike? = null, dim: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.roomWet(wet, size, fade, lowpass, dim, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the reverb send after the previous mapper.
 *
 * @param wet The reverb send amount (0–1). Omit to reinterpret the pattern's values.
 * @return A new [PatternMapperFn] chaining this reverb send after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(roomsize(4).roomWet(0.5))     // set room size then the send
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, coarse(4).roomWet(0.8))     // lo-fi with heavy reverb every 4th cycle
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.roomWet(wet: PatternLike? = null, size: PatternLike? = null, fade: PatternLike? = null, lowpass: PatternLike? = null, dim: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.roomWet(wet, size, fade, lowpass, dim, callInfo) }

// -- roomsize() / rsize() / sz() / size() -----------------------------------------------------------------------------

private val roomSizeMutation = voiceSetter { roomSize = it?.asDoubleOrNull() }

private fun applyRoomSize(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, roomSizeMutation)
}

/**
 * Sets the reverb room size (tail length) for this pattern.
 *
 * Larger values produce a longer, more spacious reverb tail. Use with `room` to control
 * the wet/dry mix.
 * When [amount] is omitted, the pattern's own numeric values are reinterpreted as room size.
 *
 * @param amount The room size. Larger values produce longer reverb tails.
 *   Omit to reinterpret the pattern's values as room size.
 * @return A new pattern with room size applied.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").clip(0.5).roomWet(0.5).roomsize(4)   // long reverb tail
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").clip(0.5).roomsize("<1 2 4 8>")              // growing room size
 * ```
 *
 * ```KlangScript(Playable)
 * seq("1 2 4 8").roomsize()                       // reinterpret values as room size
 * ```
 *
 * @param-tool amount SprudelRoomSizeEditor, SprudelRoomSizeSequenceEditor
 * @alias rsize, sz, size
 * @category effects
 * @tags roomsize, rsize, sz, size, reverb, room, tail
 */
@KlangScript.Function
fun SprudelPattern.roomsize(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyRoomSize(this, listOfNotNull(amount).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets the reverb room size.
 *
 * When [amount] is omitted, the string's numeric values are reinterpreted as room size.
 *
 * @param amount The room size. Larger values produce longer reverb tails.
 *   Omit to reinterpret the pattern's values as room size.
 * @return A new pattern with room size applied.
 *
 * ```KlangScript(Playable)
 * "c3 e3".roomsize(4).roomWet(0.5).note().clip(0.5)    // long reverb tail on bass notes
 * ```
 */
@KlangScript.Function
fun String.roomsize(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).roomsize(amount, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the reverb room size.
 *
 * Use the returned mapper as a transform argument or apply it to a pattern via `.apply(...)`.
 * When [amount] is omitted, the pattern's own numeric values are reinterpreted as room size.
 *
 * @param amount The room size. Larger values produce longer reverb tails.
 *   Omit to reinterpret the pattern's values as room size.
 * @return A [PatternMapperFn] that sets the reverb room size.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(roomsize(4)).clip(0.5)        // long reverb tail via mapper
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, roomsize(8)).clip(0.5)      // huge room every 4th cycle
 * ```
 *
 * @alias rsize, sz, size
 * @category effects
 * @tags roomsize, rsize, sz, size, reverb, room, tail
 */
@KlangScript.Function
fun roomsize(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.roomsize(amount, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the reverb room size after the previous mapper.
 *
 * @param amount The room size. Omit to reinterpret the pattern's values as room size.
 * @return A new [PatternMapperFn] chaining this room size after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(roomWet(0.5).roomsize(4))   // set wet mix then room size
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, roomWet(0.8).roomsize(8))  // big reverb every 4th cycle
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.roomsize(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.roomsize(amount, callInfo) }

/**
 * Alias for [roomsize]. Sets the reverb room size for this pattern.
 *
 * @param amount The room size. Omit to reinterpret the pattern's values as room size.
 * @return A new pattern with room size applied.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").clip(0.5).roomWet(0.5).rsize(4)   // long reverb tail
 * ```
 *
 * @param-tool amount SprudelRoomSizeEditor, SprudelRoomSizeSequenceEditor
 * @alias roomsize, sz, size
 * @category effects
 * @tags rsize, roomsize, sz, size, reverb, room, tail
 */
@KlangScript.Function
fun SprudelPattern.rsize(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.roomsize(amount, callInfo)

/**
 * Alias for [roomsize]. Parses this string as a pattern and sets the reverb room size.
 *
 * @param amount The room size. Omit to reinterpret the pattern's values as room size.
 *
 * ```KlangScript(Playable)
 * "c3 e3".rsize(4).roomWet(0.5).note().clip(0.5)    // long reverb tail on bass notes
 * ```
 */
@KlangScript.Function
fun String.rsize(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).roomsize(amount, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the reverb room size. Alias for [roomsize].
 *
 * @param amount The room size. Omit to reinterpret the pattern's values as room size.
 * @return A [PatternMapperFn] that sets the reverb room size.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(rsize(4)).clip(0.5)   // long reverb tail via mapper
 * ```
 *
 * @alias roomsize, sz, size
 * @category effects
 * @tags rsize, roomsize, sz, size, reverb, room, tail
 */
@KlangScript.Function
fun rsize(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.roomsize(amount, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the reverb room size (alias for roomsize) after the previous mapper.
 *
 * @param amount The room size. Omit to reinterpret the pattern's values as room size.
 * @return A new [PatternMapperFn] chaining this room size after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(roomWet(0.5).rsize(4))   // set wet mix then room size
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, roomWet(0.8).rsize(8))  // big reverb every 4th cycle
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.rsize(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.roomsize(amount, callInfo) }

/**
 * Alias for [roomsize]. Sets the reverb room size for this pattern.
 *
 * @param amount The room size. Omit to reinterpret the pattern's values as room size.
 * @return A new pattern with room size applied.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").clip(0.5).roomWet(0.5).sz(4)   // long reverb tail
 * ```
 *
 * @param-tool amount SprudelRoomSizeEditor, SprudelRoomSizeSequenceEditor
 * @alias roomsize, rsize, size
 * @category effects
 * @tags sz, roomsize, rsize, size, reverb, room, tail
 */
@KlangScript.Function
fun SprudelPattern.sz(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.roomsize(amount, callInfo)

/**
 * Alias for [roomsize]. Parses this string as a pattern and sets the reverb room size.
 *
 * @param amount The room size. Omit to reinterpret the pattern's values as room size.
 *
 * ```KlangScript(Playable)
 * "c3 e3".sz(4).roomWet(0.5).note().clip(0.5)    // long reverb tail on bass notes
 * ```
 */
@KlangScript.Function
fun String.sz(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).roomsize(amount, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the reverb room size. Alias for [roomsize].
 *
 * @param amount The room size. Omit to reinterpret the pattern's values as room size.
 * @return A [PatternMapperFn] that sets the reverb room size.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(sz(4)).clip(0.5)   // long reverb tail via mapper
 * ```
 *
 * @alias roomsize, rsize, size
 * @category effects
 * @tags sz, roomsize, rsize, size, reverb, room, tail
 */
@KlangScript.Function
fun sz(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.roomsize(amount, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the reverb room size (alias for roomsize) after the previous mapper.
 *
 * @param amount The room size. Omit to reinterpret the pattern's values as room size.
 * @return A new [PatternMapperFn] chaining this room size after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(roomWet(0.5).sz(4))   // set wet mix then room size
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, roomWet(0.8).sz(8))  // big reverb every 4th cycle
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.sz(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.roomsize(amount, callInfo) }

/**
 * Alias for [roomsize]. Sets the reverb room size for this pattern.
 *
 * @param amount The room size. Omit to reinterpret the pattern's values as room size.
 * @return A new pattern with room size applied.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").clip(0.5).roomWet(0.5).size(4)   // long reverb tail
 * ```
 *
 * @alias roomsize, rsize, sz
 * @category effects
 * @tags size, roomsize, rsize, sz, reverb, room, tail
 */
@KlangScript.Function
fun SprudelPattern.size(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.roomsize(amount, callInfo)

/**
 * Alias for [roomsize]. Parses this string as a pattern and sets the reverb room size.
 *
 * @param amount The room size. Omit to reinterpret the pattern's values as room size.
 *
 * ```KlangScript(Playable)
 * "c3 e3".size(4).roomWet(0.5).note().clip(0.5)    // long reverb tail on bass notes
 * ```
 */
@KlangScript.Function
fun String.size(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).roomsize(amount, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the reverb room size. Alias for [roomsize].
 *
 * @param amount The room size. Omit to reinterpret the pattern's values as room size.
 * @return A [PatternMapperFn] that sets the reverb room size.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(size(4)).clip(0.5)   // long reverb tail via mapper
 * ```
 *
 * @alias roomsize, rsize, sz
 * @category effects
 * @tags size, roomsize, rsize, sz, reverb, room, tail
 */
@KlangScript.Function
fun size(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.roomsize(amount, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the reverb room size (alias for roomsize) after the previous mapper.
 *
 * @param amount The room size. Omit to reinterpret the pattern's values as room size.
 * @return A new [PatternMapperFn] chaining this room size after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(roomWet(0.5).size(4))   // set wet mix then room size
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, roomWet(0.8).size(8))  // big reverb every 4th cycle
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.size(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.roomsize(amount, callInfo) }

// -- roomfade() / rfade() ---------------------------------------------------------------------------------------------

private val roomFadeMutation = voiceSetter { roomFade = it?.asDoubleOrNull() }

private fun applyRoomFade(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, roomFadeMutation)
}

/**
 * Overrides `roomsize` to set the reverb tail for this pattern.
 *
 * **0..1, not seconds** despite the name (0 = ~0.7 s, 1 = ~12.5 s), and a different scale from
 * `roomsize` (~0..10). Whenever this is set it wins over `roomsize`.
 *
 * Controls how long the reverb tail takes to fade out. Longer values create more sustained
 * tails that persist after the dry signal ends.
 * When [time] is omitted, the pattern's own numeric values are reinterpreted as fade time.
 *
 * @param time Tail override, **0..1** (0 = ~0.7 s, 1 = ~12.5 s) — NOT seconds, and a different
 *   scale from `roomsize` (~0..10). Overrides `roomsize`. Values outside 0..1 are bounded —
 *   past unity the comb network runs away rather than ringing longer. Omit to reinterpret the pattern's values as the override.
 * @return A new pattern with the reverb fade time applied.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").roomWet(0.6).roomsize(8).roomfade(0.1)   // roomfade wins: a short tail
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").roomfade("<0.05 0.1 0.2 0.4>")  // tail grows each beat
 * ```
 *
 * ```KlangScript(Playable)
 * seq("0.05 0.1 0.2 0.4").roomfade()       // reinterpret values as the tail override
 * ```
 *
 * @alias rfade
 * @category effects
 * @tags roomfade, rfade, reverb, fade, tail
 */
@KlangScript.Function
fun SprudelPattern.roomfade(time: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyRoomFade(this, listOfNotNull(time).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets the reverb tail override (0..1).
 *
 * When [time] is omitted, the string's numeric values are reinterpreted as fade time.
 *
 * @param time Tail override, **0..1** (0 = ~0.7 s, 1 = ~12.5 s) — NOT seconds, and a different
 *   scale from `roomsize` (~0..10). Overrides `roomsize`. Values outside 0..1 are bounded —
 *   past unity the comb network runs away rather than ringing longer. Omit to reinterpret the pattern's values as the override.
 *
 * ```KlangScript(Playable)
 * "c3 e3".roomfade(0.1).roomWet(0.6).note()   // short tail on string pattern
 * ```
 */
@KlangScript.Function
fun String.roomfade(time: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).roomfade(time, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the reverb tail override (0..1).
 *
 * Use the returned mapper as a transform argument or apply it via `.apply(...)`.
 * When [time] is omitted, the pattern's own numeric values are reinterpreted as fade time.
 *
 * @param time Tail override, **0..1** (0 = ~0.7 s, 1 = ~12.5 s) — NOT seconds, and a different
 *   scale from `roomsize` (~0..10). Overrides `roomsize`. Values outside 0..1 are bounded —
 *   past unity the comb network runs away rather than ringing longer. Omit to reinterpret the pattern's values as the override.
 * @return A [PatternMapperFn] that sets the reverb tail override (0..1).
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(roomfade(0.1)).roomWet(0.6)   // short tail via mapper
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, roomfade(0.6))            // long tail every 4th cycle
 * ```
 *
 * @alias rfade
 * @category effects
 * @tags roomfade, rfade, reverb, fade, tail
 */
@KlangScript.Function
fun roomfade(time: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.roomfade(time, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the reverb tail override (0..1) after the previous mapper.
 *
 * @param time Tail override, **0..1** (0 = ~0.7 s, 1 = ~12.5 s) — NOT seconds, and a different
 *   scale from `roomsize` (~0..10). Overrides `roomsize`. Values outside 0..1 are bounded —
 *   past unity the comb network runs away rather than ringing longer. Omit to reinterpret the pattern's values as the override.
 * @return A new [PatternMapperFn] chaining this tail override after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(roomWet(0.6).roomfade(0.1))   // wet mix then tail
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, roomWet(0.8).roomfade(0.6))  // big reverb every 4th cycle
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.roomfade(time: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.roomfade(time, callInfo) }

/**
 * Alias for [roomfade]. Overrides `roomsize` to set the reverb tail for this pattern.
 *
 * **0..1, not seconds** despite the name (0 = ~0.7 s, 1 = ~12.5 s), and a different scale from
 * `roomsize` (~0..10). Whenever this is set it wins over `roomsize`.
 *
 * When [time] is omitted, the pattern's own numeric values are reinterpreted as fade time.
 *
 * @param time Tail override, **0..1** (0 = ~0.7 s, 1 = ~12.5 s) — NOT seconds, and a different
 *   scale from `roomsize` (~0..10). Overrides `roomsize`. Values outside 0..1 are bounded —
 *   past unity the comb network runs away rather than ringing longer. Omit to reinterpret the pattern's values as the override.
 * @return A new pattern with the reverb fade time applied.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").roomWet(0.6).rsize(8).rfade(0.1)   // rfade wins: a short tail
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").rfade("<0.05 0.1 0.2 0.4>")  // tail grows each beat
 * ```
 *
 * @alias roomfade
 * @category effects
 * @tags rfade, roomfade, reverb, fade, tail
 */
@KlangScript.Function
fun SprudelPattern.rfade(time: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.roomfade(time, callInfo)

/**
 * Alias for [roomfade]. Parses this string as a pattern and sets the reverb tail override (0..1).
 *
 * When [time] is omitted, the string's numeric values are reinterpreted as fade time.
 *
 * @param time Tail override, **0..1** (0 = ~0.7 s, 1 = ~12.5 s) — NOT seconds, and a different
 *   scale from `roomsize` (~0..10). Overrides `roomsize`. Values outside 0..1 are bounded —
 *   past unity the comb network runs away rather than ringing longer. Omit to reinterpret the pattern's values as the override.
 *
 * ```KlangScript(Playable)
 * "c3 e3".rfade(0.1).roomWet(0.6).note()   // short tail on string pattern
 * ```
 */
@KlangScript.Function
fun String.rfade(time: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).roomfade(time, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the reverb tail override (0..1). Alias for [roomfade].
 *
 * @param time Tail override, **0..1** (not seconds). Overrides `roomsize`.
 * @return A [PatternMapperFn] that sets the reverb tail override (0..1).
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(rfade(0.1)).roomWet(0.6)   // 2-second fade via mapper
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, rfade(0.1))            // long fade every 4th cycle
 * ```
 *
 * @alias roomfade
 * @category effects
 * @tags rfade, roomfade, reverb, fade, tail
 */
@KlangScript.Function
fun rfade(time: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.roomfade(time, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the reverb tail override (0..1) (alias for roomfade) after the previous mapper.
 *
 * @param time Tail override, **0..1** (0 = ~0.7 s, 1 = ~12.5 s) — NOT seconds, and a different
 *   scale from `roomsize` (~0..10). Overrides `roomsize`. Values outside 0..1 are bounded —
 *   past unity the comb network runs away rather than ringing longer. Omit to reinterpret the pattern's values as the override.
 * @return A new [PatternMapperFn] chaining this tail override after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(roomWet(0.6).rfade(0.1))   // wet mix then fade time
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, roomWet(0.8).rfade(0.1))  // big reverb every 4th cycle
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.rfade(time: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.roomfade(time, callInfo) }

// -- roomlp() / rlp() -------------------------------------------------------------------------------------------------

private val roomLpMutation = voiceSetter { roomLp = it?.asDoubleOrNull() }

private fun applyRoomLp(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, roomLpMutation)
}

/**
 * Sets the reverb lowpass start frequency in Hz for this pattern.
 *
 * Applies a lowpass filter to the reverb tail starting at the specified frequency,
 * making the reverb darker and less bright.
 * When [freq] is omitted, the pattern's own numeric values are reinterpreted as the frequency.
 *
 * @param freq The lowpass filter start frequency in Hz.
 *   Omit to reinterpret the pattern's values as lowpass frequency.
 * @return A new pattern with the reverb lowpass frequency applied.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").roomWet(0.6).roomlp(4000)           // dark reverb tail
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").roomlp("<8000 4000 2000 1000>")   // increasingly dark reverb
 * ```
 *
 * ```KlangScript(Playable)
 * seq("8000 4000 2000 1000").roomlp()             // reinterpret values as lowpass frequency
 * ```
 *
 * @alias rlp
 * @category effects
 * @tags roomlp, rlp, reverb, lowpass, filter
 */
@KlangScript.Function
fun SprudelPattern.roomlp(freq: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyRoomLp(this, listOfNotNull(freq).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets the reverb lowpass start frequency.
 *
 * When [freq] is omitted, the string's numeric values are reinterpreted as the frequency.
 *
 * @param freq The lowpass filter start frequency in Hz.
 *   Omit to reinterpret the pattern's values as lowpass frequency.
 *
 * ```KlangScript(Playable)
 * "c3 e3".roomlp(4000).roomWet(0.6).note()   // dark reverb on string pattern
 * ```
 */
@KlangScript.Function
fun String.roomlp(freq: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).roomlp(freq, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the reverb lowpass start frequency.
 *
 * Use the returned mapper as a transform argument or apply it via `.apply(...)`.
 * When [freq] is omitted, the pattern's own numeric values are reinterpreted as the frequency.
 *
 * @param freq The lowpass filter start frequency in Hz.
 *   Omit to reinterpret the pattern's values as lowpass frequency.
 * @return A [PatternMapperFn] that sets the reverb lowpass frequency.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(roomlp(4000)).roomWet(0.6)   // dark reverb via mapper
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, roomlp(1000))            // very dark reverb every 4th cycle
 * ```
 *
 * @alias rlp
 * @category effects
 * @tags roomlp, rlp, reverb, lowpass, filter
 */
@KlangScript.Function
fun roomlp(freq: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.roomlp(freq, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the reverb lowpass frequency after the previous mapper.
 *
 * @param freq The lowpass filter start frequency in Hz.
 *   Omit to reinterpret the pattern's values as lowpass frequency.
 * @return A new [PatternMapperFn] chaining this lowpass frequency after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(roomWet(0.6).roomlp(4000))   // wet mix then dark reverb
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, roomWet(0.8).roomlp(1000))  // very dark reverb every 4th cycle
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.roomlp(freq: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.roomlp(freq, callInfo) }

/**
 * Alias for [roomlp]. Sets the reverb lowpass start frequency in Hz for this pattern.
 *
 * When [freq] is omitted, the pattern's own numeric values are reinterpreted as the frequency.
 *
 * @param freq The lowpass filter start frequency in Hz.
 *   Omit to reinterpret the pattern's values as lowpass frequency.
 * @return A new pattern with the reverb lowpass frequency applied.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").roomWet(0.6).rlp(4000)           // dark reverb tail
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").rlp("<8000 4000 2000 1000>")   // increasingly dark reverb
 * ```
 *
 * @alias roomlp
 * @category effects
 * @tags rlp, roomlp, reverb, lowpass, filter
 */
@KlangScript.Function
fun SprudelPattern.rlp(freq: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.roomlp(freq, callInfo)

/**
 * Alias for [roomlp]. Parses this string as a pattern and sets the reverb lowpass start frequency.
 *
 * When [freq] is omitted, the string's numeric values are reinterpreted as the frequency.
 *
 * @param freq The lowpass filter start frequency in Hz.
 *   Omit to reinterpret the pattern's values as lowpass frequency.
 *
 * ```KlangScript(Playable)
 * "c3 e3".rlp(4000).roomWet(0.6).note()   // dark reverb on string pattern
 * ```
 */
@KlangScript.Function
fun String.rlp(freq: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).roomlp(freq, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the reverb lowpass start frequency. Alias for [roomlp].
 *
 * When [freq] is omitted, the pattern's own numeric values are reinterpreted as the frequency.
 *
 * @param freq The lowpass filter start frequency in Hz.
 *   Omit to reinterpret the pattern's values as lowpass frequency.
 * @return A [PatternMapperFn] that sets the reverb lowpass frequency.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(rlp(4000)).roomWet(0.6)   // dark reverb via mapper
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, rlp(1000))            // very dark reverb every 4th cycle
 * ```
 *
 * @alias roomlp
 * @category effects
 * @tags rlp, roomlp, reverb, lowpass, filter
 */
@KlangScript.Function
fun rlp(freq: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.roomlp(freq, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the reverb lowpass frequency (alias for roomlp) after the previous mapper.
 *
 * @param freq The lowpass filter start frequency in Hz.
 *   Omit to reinterpret the pattern's values as lowpass frequency.
 * @return A new [PatternMapperFn] chaining this lowpass frequency after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(roomWet(0.6).rlp(4000))   // wet mix then dark reverb
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, roomWet(0.8).rlp(1000))  // very dark reverb every 4th cycle
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.rlp(freq: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.roomlp(freq, callInfo) }

// -- roomdim() / rdim() -----------------------------------------------------------------------------------------------

private val roomDimMutation = voiceSetter { roomDim = it?.asDoubleOrNull() }

private fun applyRoomDim(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, roomDimMutation)
}

/**
 * Sets the reverb lowpass frequency at -60 dB for this pattern.
 *
 * Determines the frequency at which the reverb tail has decayed to -60 dB, controlling
 * the overall brightness of the reverb at full decay.
 * When [freq] is omitted, the pattern's own numeric values are reinterpreted as the frequency.
 *
 * @param freq The -60 dB lowpass frequency in Hz. Omit to reinterpret the pattern's values as frequency.
 * @return A new pattern with the reverb -60 dB frequency applied.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").roomWet(0.6).roomdim(500)   // very dark, dim reverb
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").roomdim("<2000 1000 500 200>")   // progressively dimmer reverb
 * ```
 *
 * ```KlangScript(Playable)
 * seq("2000 1000 500 200").roomdim()   // reinterpret values as -60 dB frequency
 * ```
 *
 * @alias rdim
 * @category effects
 * @tags roomdim, rdim, reverb, lowpass, darkness
 */
@KlangScript.Function
fun SprudelPattern.roomdim(freq: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyRoomDim(this, listOfNotNull(freq).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets the reverb lowpass frequency at -60 dB.
 *
 * When [freq] is omitted, the string's numeric values are reinterpreted as the frequency.
 *
 * @param freq The -60 dB lowpass frequency in Hz. Omit to reinterpret the pattern's values as frequency.
 *
 * ```KlangScript(Playable)
 * "c3 e3".roomdim(500).roomWet(0.6).note()   // very dark reverb on string pattern
 * ```
 */
@KlangScript.Function
fun String.roomdim(freq: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).roomdim(freq, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the reverb lowpass frequency at -60 dB.
 *
 * Use the returned mapper as a transform argument or apply it via `.apply(...)`.
 * When [freq] is omitted, the pattern's own numeric values are reinterpreted as the frequency.
 *
 * @param freq The -60 dB lowpass frequency in Hz. Omit to reinterpret the pattern's values as frequency.
 * @return A [PatternMapperFn] that sets the reverb -60 dB frequency.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(roomdim(500)).roomWet(0.6)   // very dark reverb via mapper
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, roomdim(200))            // very dim reverb every 4th cycle
 * ```
 *
 * @alias rdim
 * @category effects
 * @tags roomdim, rdim, reverb, lowpass, darkness
 */
@KlangScript.Function
fun roomdim(freq: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.roomdim(freq, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the reverb lowpass frequency at -60 dB after the previous mapper.
 *
 * @param freq The -60 dB lowpass frequency in Hz. Omit to reinterpret the pattern's values as frequency.
 * @return A new [PatternMapperFn] chaining this frequency after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(roomWet(0.6).roomdim(500))   // wet mix then dim frequency
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, roomWet(0.8).roomdim(200))  // very dim reverb every 4th cycle
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.roomdim(freq: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.roomdim(freq, callInfo) }

/**
 * Alias for [roomdim]. Sets the reverb lowpass frequency at -60 dB for this pattern.
 *
 * When [freq] is omitted, the pattern's own numeric values are reinterpreted as the frequency.
 *
 * @param freq The -60 dB lowpass frequency in Hz. Omit to reinterpret the pattern's values as frequency.
 * @return A new pattern with the reverb -60 dB frequency applied.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").roomWet(0.6).rdim(500)   // very dark, dim reverb
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").rdim("<2000 1000 500 200>")   // progressively dimmer reverb
 * ```
 *
 * ```KlangScript(Playable)
 * seq("2000 1000 500 200").rdim()   // reinterpret values as -60 dB frequency
 * ```
 *
 * @alias roomdim
 * @category effects
 * @tags rdim, roomdim, reverb, lowpass, darkness
 */
@KlangScript.Function
fun SprudelPattern.rdim(freq: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.roomdim(freq, callInfo)

/**
 * Alias for [roomdim]. Parses this string as a pattern and sets the reverb lowpass frequency at -60 dB.
 *
 * When [freq] is omitted, the string's numeric values are reinterpreted as the frequency.
 *
 * @param freq The -60 dB lowpass frequency in Hz. Omit to reinterpret the pattern's values as frequency.
 *
 * ```KlangScript(Playable)
 * "c3 e3".rdim(500).roomWet(0.6).note()   // very dark reverb on string pattern
 * ```
 */
@KlangScript.Function
fun String.rdim(freq: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).roomdim(freq, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the reverb lowpass frequency at -60 dB. Alias for [roomdim].
 *
 * @param freq The -60 dB lowpass frequency in Hz. Omit to reinterpret the pattern's values as frequency.
 * @return A [PatternMapperFn] that sets the reverb -60 dB frequency.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(rdim(500)).roomWet(0.6)   // very dark reverb via mapper
 * ```
 *
 * @alias roomdim
 * @category effects
 * @tags rdim, roomdim, reverb, lowpass, darkness
 */
@KlangScript.Function
fun rdim(freq: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.roomdim(freq, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the reverb -60 dB frequency (alias for roomdim) after the previous
 * mapper.
 *
 * @param freq The -60 dB lowpass frequency in Hz. Omit to reinterpret the pattern's values as frequency.
 * @return A new [PatternMapperFn] chaining this frequency after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(roomWet(0.6).rdim(500))   // wet mix then dim frequency
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, roomWet(0.8).rdim(200))  // very dim reverb every 4th cycle
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.rdim(freq: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.roomdim(freq, callInfo) }

// -- iresponse() / ir() -----------------------------------------------------------------------------------------------

private val iResponseMutation = voiceSetter { response -> iResponse = response?.toString() }

private fun applyIResponse(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._applyControlFromParams(args, iResponseMutation) { src, ctrl ->
        src.iResponse = ctrl.iResponse
        src
    }
}

/**
 * Sets the impulse response sample name for convolution reverb on this pattern.
 *
 * Uses a recorded impulse response to simulate the acoustics of a real space. The value
 * is the name of an IR sample loaded in the audio engine.
 *
 * @param name The name of the impulse response sample.
 * @return A new pattern with the impulse response applied.
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").iresponse("church")     // church reverb IR
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").iresponse("<room hall plate>")  // cycle through IR types
 * ```
 *
 * @alias ir
 * @category effects
 * @tags iresponse, ir, impulse, convolution, reverb
 */
@KlangScript.Function
fun SprudelPattern.iresponse(name: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyIResponse(this, listOf(name).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets the impulse response sample for convolution reverb.
 *
 * @param name The name of the impulse response sample.
 *
 * ```KlangScript(Playable)
 * "c3 e3 g3".iresponse("church").note()   // church reverb IR on string pattern
 * ```
 */
@KlangScript.Function
fun String.iresponse(name: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).iresponse(name, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the impulse response sample for convolution reverb.
 *
 * Use the returned mapper as a transform argument or apply it via `.apply(...)`.
 *
 * @param name The name of the impulse response sample.
 * @return A [PatternMapperFn] that sets the impulse response.
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").apply(iresponse("church"))   // church reverb via mapper
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, iresponse("hall"))   // hall reverb every 4th cycle
 * ```
 *
 * @alias ir
 * @category effects
 * @tags iresponse, ir, impulse, convolution, reverb
 */
@KlangScript.Function
fun iresponse(name: PatternLike, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.iresponse(name, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the impulse response after the previous mapper.
 *
 * @param name The name of the impulse response sample.
 * @return A new [PatternMapperFn] chaining this impulse response after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(roomWet(0.5).iresponse("church"))   // room then IR reverb
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, roomWet(0.8).iresponse("hall"))   // hall IR every 4th cycle
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.iresponse(name: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.iresponse(name, callInfo) }

/**
 * Alias for [iresponse]. Sets the impulse response sample name for convolution reverb on this pattern.
 *
 * @param name The name of the impulse response sample.
 * @return A new pattern with the impulse response applied.
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").ir("church")     // church reverb IR
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").ir("<room hall plate>")  // cycle through IR types
 * ```
 *
 * @alias iresponse
 * @category effects
 * @tags ir, iresponse, impulse, convolution, reverb
 */
@KlangScript.Function
fun SprudelPattern.ir(name: PatternLike, callInfo: CallInfo? = null): SprudelPattern = this.iresponse(name, callInfo)

/**
 * Alias for [iresponse]. Parses this string as a pattern and sets the impulse response sample.
 *
 * @param name The name of the impulse response sample.
 *
 * ```KlangScript(Playable)
 * "c3 e3 g3".ir("church").note()   // church reverb IR on string pattern
 * ```
 */
@KlangScript.Function
fun String.ir(name: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).iresponse(name, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the impulse response sample. Alias for [iresponse].
 *
 * @param name The name of the impulse response sample.
 * @return A [PatternMapperFn] that sets the impulse response.
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").apply(ir("church"))   // church reverb via mapper
 * ```
 *
 * @alias iresponse
 * @category effects
 * @tags ir, iresponse, impulse, convolution, reverb
 */
@KlangScript.Function
fun ir(name: PatternLike, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.iresponse(name, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the impulse response (alias for iresponse) after the previous mapper.
 *
 * @param name The name of the impulse response sample.
 * @return A new [PatternMapperFn] chaining this impulse response after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(roomWet(0.5).ir("church"))   // room then IR reverb
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, roomWet(0.8).ir("hall"))   // hall IR every 4th cycle
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.ir(name: PatternLike, callInfo: CallInfo? = null): PatternMapperFn = this.chain { p -> p.iresponse(name, callInfo) }

// -- delayWet() ----------------------------------------------------------------------------------------------------------

private val delayMutation = voiceSetter {
    val str = it?.toString() ?: return@voiceSetter
    delay = str.toDoubleOrNull() ?: delay
}

private fun applyDelay(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, delayMutation)
}

/**
 * Sets the delay SEND amount for this pattern — delay, like reverb, is a send, not a
 * crossfade: the dry signal reaches the mix untouched at full level, and [wet] only scales
 * how much of the voice feeds the orbit's shared delay line (0 = no delay, 1 = full send).
 * That is why there is no `delayFloor`: a send has nothing to floor.
 *
 * Takes the send amount plus optional time and feedback parameters.
 * Each parameter is independent and patternable; omitted parameters keep their previous values.
 *
 * - **wet**: send amount (0–1)
 * - **time**: delay interval in seconds
 * - **feedback**: feedback amount (0–1), higher = more repeats
 *
 * When [wet] is omitted, the pattern's own numeric values are reinterpreted as the send amount.
 *
 * @param wet The delay send amount (0–1).
 * @param time Delay time in seconds. Omit to leave it unchanged.
 * @param feedback Feedback amount (0–1). Omit to leave it unchanged.
 * @return A new pattern with the delay applied.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").delayWet(0.4)                         // 40% delay send
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").delayWet(0.5, 0.25, 0.6)               // wet=0.5, time=0.25s, feedback=0.6
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").delayWet("<0.3 0.6>", "<0.125 0.25>", "<~ 0.8>")   // alternating delay per cycle
 * ```
 *
 * @param-tool wet SprudelDelayEditor, SprudelDelaySequenceEditor
 * @category effects
 * @tags delay, echo, wet, mix, delaytime, delayfeedback
 */
@KlangScript.Function
fun SprudelPattern.delayWet(wet: PatternLike? = null, time: PatternLike? = null, feedback: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern {
    // A tail-only call must not touch wet: reinterpret runs only on a fully bare call.
    var p = if (wet != null || !(time != null || feedback != null)) {
        applyDelay(this, listOfNotNull(wet).asSprudelDslArgs(callInfo))
    } else {
        this
    }
    if (time != null) p = p.delaytime(time, callInfo?.forParam(1))
    if (feedback != null) p = p.delayfeedback(feedback, callInfo?.forParam(2))
    return p
}

/**
 * Parses this string as a pattern and sets the delay send (see [SprudelPattern.delayWet]).
 *
 * When [wet] is omitted, the string's numeric values are reinterpreted as the send amount.
 *
 * @param wet The delay send amount (0–1). Omit to reinterpret the pattern's values.
 * @param time Delay time in seconds. Omit to leave it unchanged.
 * @param feedback Feedback amount (0–1). Omit to leave it unchanged.
 *
 * ```KlangScript(Playable)
 * "c3 e3".delayWet(0.4).note()   // 40% delay send on string pattern
 * ```
 */
@KlangScript.Function
fun String.delayWet(wet: PatternLike? = null, time: PatternLike? = null, feedback: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).delayWet(wet, time, feedback, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the delay send (see [SprudelPattern.delayWet]).
 *
 * Use the returned mapper as a transform argument or apply it via `.apply(...)`.
 * When [wet] is omitted, the pattern's own numeric values are reinterpreted as the send amount.
 *
 * @param wet The delay send amount (0–1). Omit to reinterpret the pattern's values.
 * @param time Delay time in seconds. Omit to leave it unchanged.
 * @param feedback Feedback amount (0–1). Omit to leave it unchanged.
 * @return A [PatternMapperFn] that sets the delay send.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(delayWet(0.4))   // 40% delay send via mapper
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, delayWet(0.8))   // heavy delay every 4th cycle
 * ```
 *
 * @category effects
 * @tags delay, echo, wet, mix
 */
@KlangScript.Function
fun delayWet(wet: PatternLike? = null, time: PatternLike? = null, feedback: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.delayWet(wet, time, feedback, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the delay send after the previous mapper.
 *
 * @param wet The delay send amount (0–1). Omit to reinterpret the pattern's values.
 * @param time Delay time in seconds. Omit to leave it unchanged.
 * @param feedback Feedback amount (0–1). Omit to leave it unchanged.
 * @return A new [PatternMapperFn] chaining this delay send after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(delaytime(0.25).delayWet(0.5))   // set delay time then the send
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, delaytime(0.25).delayWet(0.8))   // delay every 4th cycle
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.delayWet(wet: PatternLike? = null, time: PatternLike? = null, feedback: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.delayWet(wet, time, feedback, callInfo) }

// -- delaytime() ------------------------------------------------------------------------------------------------------

private val delayTimeMutation = voiceSetter { delayTime = it?.asDoubleOrNull() }

private fun applyDelayTime(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, delayTimeMutation)
}

/**
 * Sets the delay time in seconds (the interval between repeats) for this pattern.
 *
 * Use with `delay` for wet/dry mix and `delayfeedback` for the number of repeats.
 * Musical values: `0.25` = quarter note at 60 BPM, `0.5` = half note.
 * When [time] is omitted, the pattern's own numeric values are reinterpreted as delay time.
 *
 * @param time The delay interval in seconds. Omit to reinterpret the pattern's values as delay time.
 * @return A new pattern with the delay time applied.
 *
 * ```KlangScript(Playable)
 * note("c3").delayWet(0.5).delaytime(0.375)   // dotted-eighth delay
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").delaytime("<0.125 0.25 0.5>")   // varying delay times
 * ```
 *
 * ```KlangScript(Playable)
 * seq("0.125 0.25 0.5").delaytime()   // reinterpret values as delay time
 * ```
 *
 * @param-tool time SprudelDelayTimeEditor, SprudelDelayTimeSequenceEditor
 * @category effects
 * @tags delaytime, delay, echo, time, interval
 */
@KlangScript.Function
fun SprudelPattern.delaytime(time: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyDelayTime(this, listOfNotNull(time).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets the delay time.
 *
 * When [time] is omitted, the string's numeric values are reinterpreted as delay time.
 *
 * @param time The delay interval in seconds. Omit to reinterpret the pattern's values as delay time.
 *
 * ```KlangScript(Playable)
 * "c3 e3".delaytime(0.25).delayWet(0.5).note()   // quarter-note delay on string pattern
 * ```
 */
@KlangScript.Function
fun String.delaytime(time: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).delaytime(time, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the delay time in seconds.
 *
 * Use the returned mapper as a transform argument or apply it via `.apply(...)`.
 * When [time] is omitted, the pattern's own numeric values are reinterpreted as delay time.
 *
 * @param time The delay interval in seconds. Omit to reinterpret the pattern's values as delay time.
 * @return A [PatternMapperFn] that sets the delay time.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(delaytime(0.25))   // quarter-note delay via mapper
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, delaytime(0.125))   // eighth-note delay every 4th cycle
 * ```
 *
 * @category effects
 * @tags delaytime, delay, echo, time, interval
 */
@KlangScript.Function
fun delaytime(time: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.delaytime(time, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the delay time after the previous mapper.
 *
 * @param time The delay interval in seconds. Omit to reinterpret the pattern's values as delay time.
 * @return A new [PatternMapperFn] chaining this delay time after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(delayWet(0.5).delaytime(0.25))   // delay mix then time
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, delayWet(0.8).delaytime(0.125))   // delay every 4th cycle
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.delaytime(time: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.delaytime(time, callInfo) }

// -- delayfeedback() / delayfb() / dfb() ------------------------------------------------------------------------------

private val delayFeedbackMutation = voiceSetter { delayFeedback = it?.asDoubleOrNull() }

private fun applyDelayFeedback(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, delayFeedbackMutation)
}

/**
 * Sets the delay feedback amount (0–1) for this pattern, controlling the number of echoes.
 *
 * Higher values produce more repeats. Values near 1 create infinite repeats.
 * Use with `delay` and `delaytime` to set up the full delay effect.
 * When [amount] is omitted, the pattern's own numeric values are reinterpreted as feedback.
 *
 * @param amount The feedback amount (0–1). Omit to reinterpret the pattern's values as feedback.
 * @return A new pattern with the delay feedback applied.
 *
 * ```KlangScript(Playable)
 * note("c3").delayWet(0.4).delaytime(0.25).delayfeedback(0.6)   // 3-4 echoes
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").delayfeedback("<0.2 0.4 0.6 0.8>")            // increasing echoes
 * ```
 *
 * ```KlangScript(Playable)
 * seq("0.2 0.4 0.6 0.8").delayfeedback()   // reinterpret values as feedback
 * ```
 *
 * @param-tool amount SprudelDelayFeedbackEditor, SprudelDelayFeedbackSequenceEditor
 * @alias delayfb, dfb
 * @category effects
 * @tags delayfeedback, delayfb, dfb, delay, echo, feedback, repeats
 */
@KlangScript.Function
fun SprudelPattern.delayfeedback(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyDelayFeedback(this, listOfNotNull(amount).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets the delay feedback amount.
 *
 * When [amount] is omitted, the string's numeric values are reinterpreted as feedback.
 *
 * @param amount The feedback amount (0–1). Omit to reinterpret the pattern's values as feedback.
 *
 * ```KlangScript(Playable)
 * "c3 e3".delayfeedback(0.6).delayWet(0.4).note()   // echoing string pattern
 * ```
 */
@KlangScript.Function
fun String.delayfeedback(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).delayfeedback(amount, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the delay feedback amount.
 *
 * Use the returned mapper as a transform argument or apply it via `.apply(...)`.
 * When [amount] is omitted, the pattern's own numeric values are reinterpreted as feedback.
 *
 * @param amount The feedback amount (0–1). Omit to reinterpret the pattern's values as feedback.
 * @return A [PatternMapperFn] that sets the delay feedback.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(delayfeedback(0.6))   // feedback via mapper
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, delayfeedback(0.8))   // lots of echoes every 4th cycle
 * ```
 *
 * @alias delayfb, dfb
 * @category effects
 * @tags delayfeedback, delayfb, dfb, delay, echo, feedback, repeats
 */
@KlangScript.Function
fun delayfeedback(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.delayfeedback(amount, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the delay feedback amount after the previous mapper.
 *
 * @param amount The feedback amount (0–1). Omit to reinterpret the pattern's values as feedback.
 * @return A new [PatternMapperFn] chaining this feedback after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(delayWet(0.5).delayfeedback(0.6))   // mix then feedback
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, delayWet(0.8).delayfeedback(0.9))   // echoing delay every 4th cycle
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.delayfeedback(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.delayfeedback(amount, callInfo) }

/**
 * Alias for [delayfeedback]. Sets the delay feedback amount (0–1) for this pattern.
 *
 * When [amount] is omitted, the pattern's own numeric values are reinterpreted as feedback.
 *
 * @param amount The feedback amount (0–1). Omit to reinterpret the pattern's values as feedback.
 * @return A new pattern with the delay feedback applied.
 *
 * ```KlangScript(Playable)
 * note("c3").delayWet(0.4).delaytime(0.25).delayfb(0.6)   // 3-4 echoes
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").delayfb("<0.2 0.4 0.6 0.8>")            // increasing echoes
 * ```
 *
 * ```KlangScript(Playable)
 * seq("0.2 0.4 0.6 0.8").delayfb()   // reinterpret values as feedback
 * ```
 *
 * @param-tool amount SprudelDelayFeedbackEditor, SprudelDelayFeedbackSequenceEditor
 * @alias delayfeedback, dfb
 * @category effects
 * @tags delayfb, delayfeedback, dfb, delay, echo, feedback, repeats
 */
@KlangScript.Function
fun SprudelPattern.delayfb(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.delayfeedback(amount, callInfo)

/**
 * Alias for [delayfeedback]. Parses this string as a pattern and sets the delay feedback amount.
 *
 * When [amount] is omitted, the string's numeric values are reinterpreted as feedback.
 *
 * @param amount The feedback amount (0–1). Omit to reinterpret the pattern's values as feedback.
 *
 * ```KlangScript(Playable)
 * "c3 e3".delayfb(0.6).delayWet(0.4).note()   // echoing string pattern
 * ```
 */
@KlangScript.Function
fun String.delayfb(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).delayfeedback(amount, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the delay feedback amount. Alias for [delayfeedback].
 *
 * @param amount The feedback amount (0–1). Omit to reinterpret the pattern's values as feedback.
 * @return A [PatternMapperFn] that sets the delay feedback.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(delayfb(0.6))   // feedback via mapper
 * ```
 *
 * @alias delayfeedback, dfb
 * @category effects
 * @tags delayfb, delayfeedback, dfb, delay, echo, feedback, repeats
 */
@KlangScript.Function
fun delayfb(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.delayfeedback(amount, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the delay feedback (alias for delayfeedback) after the previous
 * mapper.
 *
 * @param amount The feedback amount (0–1). Omit to reinterpret the pattern's values as feedback.
 * @return A new [PatternMapperFn] chaining this feedback after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(delayWet(0.5).delayfb(0.6))   // mix then feedback
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, delayWet(0.8).delayfb(0.9))   // echoing delay every 4th cycle
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.delayfb(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.delayfeedback(amount, callInfo) }

/**
 * Alias for [delayfeedback]. Sets the delay feedback amount (0–1) for this pattern.
 *
 * When [amount] is omitted, the pattern's own numeric values are reinterpreted as feedback.
 *
 * @param amount The feedback amount (0–1). Omit to reinterpret the pattern's values as feedback.
 * @return A new pattern with the delay feedback applied.
 *
 * ```KlangScript(Playable)
 * note("c3").delayWet(0.4).delaytime(0.25).dfb(0.6)   // 3-4 echoes
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").dfb("<0.2 0.4 0.6 0.8>")            // increasing echoes
 * ```
 *
 * ```KlangScript(Playable)
 * seq("0.2 0.4 0.6 0.8").dfb()   // reinterpret values as feedback
 * ```
 *
 * @alias delayfeedback, delayfb
 * @category effects
 * @tags dfb, delayfeedback, delayfb, delay, echo, feedback, repeats
 */
@KlangScript.Function
fun SprudelPattern.dfb(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.delayfeedback(amount, callInfo)

/**
 * Alias for [delayfeedback]. Parses this string as a pattern and sets the delay feedback amount.
 *
 * When [amount] is omitted, the string's numeric values are reinterpreted as feedback.
 *
 * @param amount The feedback amount (0–1). Omit to reinterpret the pattern's values as feedback.
 *
 * ```KlangScript(Playable)
 * "c3 e3".dfb(0.6).delayWet(0.4).note()   // echoing string pattern
 * ```
 */
@KlangScript.Function
fun String.dfb(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).delayfeedback(amount, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the delay feedback amount. Alias for [delayfeedback].
 *
 * @param amount The feedback amount (0–1). Omit to reinterpret the pattern's values as feedback.
 * @return A [PatternMapperFn] that sets the delay feedback.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(dfb(0.6))   // feedback via mapper
 * ```
 *
 * @alias delayfeedback, delayfb
 * @category effects
 * @tags dfb, delayfeedback, delayfb, delay, echo, feedback, repeats
 */
@KlangScript.Function
fun dfb(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.delayfeedback(amount, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the delay feedback (alias for delayfeedback) after the previous
 * mapper.
 *
 * @param amount The feedback amount (0–1). Omit to reinterpret the pattern's values as feedback.
 * @return A new [PatternMapperFn] chaining this feedback after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(delayWet(0.5).dfb(0.6))   // mix then feedback
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, delayWet(0.8).dfb(0.9))   // echoing delay every 4th cycle
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.dfb(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.delayfeedback(amount, callInfo) }

// -- phaser() / ph() --------------------------------------------------------------------------------------------------

private val phaserMutation = voiceSetter {
    val str = it?.toString() ?: return@voiceSetter
    phaserRate = str.toDoubleOrNull() ?: phaserRate
}

private fun applyPhaser(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, phaserMutation)
}

/**
 * Sets the phaser LFO rate in Hz for this pattern.
 *
 * A phaser creates a sweeping comb-filter effect by modulating a series of all-pass filters.
 * Higher rate values produce faster sweeping. Use with `phaserWet` and `phasercenter`.
 *
 * Takes the LFO rate plus optional wet, center, and sweep parameters.
 * Each parameter is independent and patternable; omitted parameters keep their previous values.
 *
 * When [rate] is omitted, the pattern's own numeric values are reinterpreted as the phaser rate.
 *
 * @param rate The phaser LFO rate in Hz.
 * @param wet Wet amount (0–1) — the shared wet knob; on the orbit phaser it is additive
 *   by default (`phaserFloor` = 1). Omit to leave it unchanged.
 * @param center Centre frequency in Hz. Omit to leave it unchanged.
 * @param sweep Sweep range in Hz. Omit to leave it unchanged.
 *   Omit to reinterpret the pattern's values as phaser rate.
 * @param-tool rate SprudelPhaserEditor, SprudelPhaserSequenceEditor
 * @return A new pattern with the phaser rate applied.
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").s("sawtooth").phaser(0.5)   // slow phaser sweep
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").phaser("<0.1 0.5 1 4>")         // accelerating phaser rate
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").s("sawtooth").phaser(0.5, 0.8, 500, 1000)   // full compound phaser
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").s("sawtooth").phaser(2.0, 0.6)   // rate + wet only
 * ```
 *
 * @alias ph
 * @category effects
 * @tags phaser, ph, phase, sweep, modulation
 */
@KlangScript.Function
fun SprudelPattern.phaser(rate: PatternLike? = null, wet: PatternLike? = null, center: PatternLike? = null, sweep: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern {
    // A tail-only call must not touch rate: reinterpret runs only on a fully bare call.
    var p = if (rate != null || !(wet != null || center != null || sweep != null)) {
        applyPhaser(this, listOfNotNull(rate).asSprudelDslArgs(callInfo))
    } else {
        this
    }
    if (wet != null) p = p.phaserWet(wet, callInfo?.forParam(1))
    if (center != null) p = p.phasercenter(center, callInfo?.forParam(2))
    if (sweep != null) p = p.phasersweep(sweep, callInfo?.forParam(3))
    return p
}

/**
 * Parses this string as a pattern and sets the phaser LFO rate.
 *
 * When [rate] is omitted, the string's numeric values are reinterpreted as the phaser rate.
 *
 * @param rate The phaser LFO rate in Hz. Omit to reinterpret the pattern's values as phaser rate.
 * @param wet Wet amount (0–1) — the shared wet knob; on the orbit phaser it is additive
 *   by default (`phaserFloor` = 1). Omit to leave it unchanged.
 * @param center Centre frequency in Hz. Omit to leave it unchanged.
 * @param sweep Sweep range in Hz. Omit to leave it unchanged.
 *
 * ```KlangScript(Playable)
 * "c3 e3 g3".phaser(0.5).note().s("sawtooth")   // slow phaser on string pattern
 * ```
 */
@KlangScript.Function
fun String.phaser(rate: PatternLike? = null, wet: PatternLike? = null, center: PatternLike? = null, sweep: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).phaser(rate, wet, center, sweep, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the phaser LFO rate.
 *
 * Use the returned mapper as a transform argument or apply it via `.apply(...)`.
 * When [rate] is omitted, the pattern's own numeric values are reinterpreted as the phaser rate.
 *
 * @param rate The phaser LFO rate in Hz.
 * @param wet Wet amount (0–1) — the shared wet knob; on the orbit phaser it is additive
 *   by default (`phaserFloor` = 1). Omit to leave it unchanged.
 * @param center Centre frequency in Hz. Omit to leave it unchanged.
 * @param sweep Sweep range in Hz. Omit to leave it unchanged.
 *   Omit to reinterpret the pattern's values as phaser rate.
 * @param-tool rate SprudelPhaserEditor, SprudelPhaserSequenceEditor
 * @return A [PatternMapperFn] that sets the phaser rate.
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").apply(phaser(0.5))   // slow phaser via mapper
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, phaser(4.0))   // fast phaser every 4th cycle
 * ```
 *
 * @alias ph
 * @category effects
 * @tags phaser, ph, phase, sweep, modulation
 */
@KlangScript.Function
fun phaser(rate: PatternLike? = null, wet: PatternLike? = null, center: PatternLike? = null, sweep: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.phaser(rate, wet, center, sweep, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the phaser LFO rate after the previous mapper.
 *
 * @param rate The phaser LFO rate in Hz. Omit to reinterpret the pattern's values as phaser rate.
 * @param wet Wet amount (0–1) — the shared wet knob; on the orbit phaser it is additive
 *   by default (`phaserFloor` = 1). Omit to leave it unchanged.
 * @param center Centre frequency in Hz. Omit to leave it unchanged.
 * @param sweep Sweep range in Hz. Omit to leave it unchanged.
 * @return A new [PatternMapperFn] chaining this phaser rate after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(phaserWet(0.8).phaser(0.5))   // wet then rate
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, phaserWet(1.0).phaser(4.0))   // full-wet fast phaser
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.phaser(rate: PatternLike? = null, wet: PatternLike? = null, center: PatternLike? = null, sweep: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.phaser(rate, wet, center, sweep, callInfo) }

/**
 * Alias for [phaser]. Sets the phaser LFO rate in Hz for this pattern.
 *
 * When [rate] is omitted, the pattern's own numeric values are reinterpreted as the phaser rate.
 *
 * @param rate The phaser LFO rate in Hz.
 * @param wet Wet amount (0–1) — the shared wet knob; on the orbit phaser it is additive
 *   by default (`phaserFloor` = 1). Omit to leave it unchanged.
 * @param center Centre frequency in Hz. Omit to leave it unchanged.
 * @param sweep Sweep range in Hz. Omit to leave it unchanged.
 *   Omit to reinterpret the pattern's values as phaser rate.
 * @param-tool rate SprudelPhaserEditor, SprudelPhaserSequenceEditor
 * @return A new pattern with the phaser rate applied.
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").s("sawtooth").ph(0.5)   // slow phaser sweep
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").ph("<0.1 0.5 1 4>")         // accelerating phaser rate
 * ```
 *
 * ```KlangScript(Playable)
 * seq("0.1 0.5 1 4").ph()   // reinterpret values as phaser rate
 * ```
 *
 * @alias phaser
 * @category effects
 * @tags ph, phaser, phase, sweep, modulation
 */
@KlangScript.Function
fun SprudelPattern.ph(rate: PatternLike? = null, wet: PatternLike? = null, center: PatternLike? = null, sweep: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.phaser(rate, wet, center, sweep, callInfo)

/**
 * Alias for [phaser]. Parses this string as a pattern and sets the phaser LFO rate.
 *
 * When [rate] is omitted, the string's numeric values are reinterpreted as the phaser rate.
 *
 * @param rate The phaser LFO rate in Hz. Omit to reinterpret the pattern's values as phaser rate.
 * @param wet Wet amount (0–1) — the shared wet knob; on the orbit phaser it is additive
 *   by default (`phaserFloor` = 1). Omit to leave it unchanged.
 * @param center Centre frequency in Hz. Omit to leave it unchanged.
 * @param sweep Sweep range in Hz. Omit to leave it unchanged.
 *
 * ```KlangScript(Playable)
 * "c3 e3 g3".ph(0.5).note().s("sawtooth")   // slow phaser on string pattern
 * ```
 */
@KlangScript.Function
fun String.ph(rate: PatternLike? = null, wet: PatternLike? = null, center: PatternLike? = null, sweep: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).phaser(rate, wet, center, sweep, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the phaser LFO rate. Alias for [phaser].
 *
 * @param rate The phaser LFO rate in Hz.
 * @param wet Wet amount (0–1) — the shared wet knob; on the orbit phaser it is additive
 *   by default (`phaserFloor` = 1). Omit to leave it unchanged.
 * @param center Centre frequency in Hz. Omit to leave it unchanged.
 * @param sweep Sweep range in Hz. Omit to leave it unchanged.
 *   Omit to reinterpret the pattern's values as phaser rate.
 * @param-tool rate SprudelPhaserEditor, SprudelPhaserSequenceEditor
 * @return A [PatternMapperFn] that sets the phaser rate.
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").apply(ph(0.5))   // slow phaser via mapper
 * ```
 *
 * @alias phaser
 * @category effects
 * @tags ph, phaser, phase, sweep, modulation
 */
@KlangScript.Function
fun ph(rate: PatternLike? = null, wet: PatternLike? = null, center: PatternLike? = null, sweep: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.phaser(rate, wet, center, sweep, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the phaser LFO rate (alias for phaser) after the previous mapper.
 *
 * @param rate The phaser LFO rate in Hz. Omit to reinterpret the pattern's values as phaser rate.
 * @param wet Wet amount (0–1) — the shared wet knob; on the orbit phaser it is additive
 *   by default (`phaserFloor` = 1). Omit to leave it unchanged.
 * @param center Centre frequency in Hz. Omit to leave it unchanged.
 * @param sweep Sweep range in Hz. Omit to leave it unchanged.
 * @return A new [PatternMapperFn] chaining this phaser rate after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(phaserWet(0.8).ph(0.5))   // wet then rate
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, phaserWet(1.0).ph(4.0))   // full-wet fast phaser
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.ph(rate: PatternLike? = null, wet: PatternLike? = null, center: PatternLike? = null, sweep: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.phaser(rate, wet, center, sweep, callInfo) }

// -- phaserWet() / phd() / phasdp() ------------------------------------------------------------------------------------

private val phaserWetMutation = voiceSetter { phaserDepth = it?.asDoubleOrNull() }

private fun applyPhaserWet(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, phaserWetMutation)
}

/**
 * Sets the phaser wet amount for this pattern — the shared wet knob (C4). On the orbit
 * phaser the dry stays untouched by default (`phaserFloor` = 1), so the wet ADDS the swept
 * notch signal on top: higher values produce a more pronounced sweep. Lower [phaserFloor]
 * to turn the same knob into a crossfade.
 *
 * The phaser is an ORBIT (bus) effect (2026-08-24: one sweep over the summed orbit, the
 * DAW-insert model) and orbit knobs are first-writer-wins: a voice that sets phaser knobs
 * but does not own its orbit's lease is not phased. Route to its own orbit for its own
 * phaser.
 *
 * When [wet] is omitted, the pattern's own numeric values are reinterpreted as the wet amount.
 *
 * @param wet The wet amount (0–1). Omit to reinterpret the pattern's values.
 * @return A new pattern with the phaser wet applied.
 *
 * ```KlangScript(Playable)
 * note("c3*4").phaser(0.5).phaserWet(0.8)   // deep phaser sweep
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").phaserWet("<0.2 0.5 0.8 1.0>")   // increasing phaser wet
 * ```
 *
 * ```KlangScript(Playable)
 * seq("0.2 0.5 0.8 1.0").phaserWet()   // reinterpret values as the phaser wet
 * ```
 *
 * @alias phd, phasdp
 * @category effects
 * @tags phaserwet, phaserdepth, phd, phasdp, phaser, depth, modulation, wet
 */
@KlangScript.Function
fun SprudelPattern.phaserWet(wet: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyPhaserWet(this, listOfNotNull(wet).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets the phaser wet amount.
 *
 * When [wet] is omitted, the string's numeric values are reinterpreted as the wet amount.
 *
 * @param wet The wet amount (0–1). Omit to reinterpret the pattern's values.
 *
 * ```KlangScript(Playable)
 * "c3*4".phaserWet(0.8).phaser(0.5).note()   // deep phaser on string pattern
 * ```
 */
@KlangScript.Function
fun String.phaserWet(wet: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).phaserWet(wet, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the phaser wet amount.
 *
 * Use the returned mapper as a transform argument or apply it via `.apply(...)`.
 * When [wet] is omitted, the pattern's own numeric values are reinterpreted as the wet amount.
 *
 * @param wet The wet amount (0–1). Omit to reinterpret the pattern's values.
 * @return A [PatternMapperFn] that sets the phaser wet.
 *
 * ```KlangScript(Playable)
 * note("c3*4").apply(phaserWet(0.8))   // deep phaser via mapper
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, phaserWet(1.0))   // full wet every 4th cycle
 * ```
 *
 * @alias phd, phasdp
 * @category effects
 * @tags phaserwet, phaserdepth, phd, phasdp, phaser, depth, modulation, wet
 */
@KlangScript.Function
fun phaserWet(wet: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.phaserWet(wet, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the phaser wet after the previous mapper.
 *
 * @param wet The wet amount (0–1). Omit to reinterpret the pattern's values.
 * @return A new [PatternMapperFn] chaining this phaser wet after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(phaser(0.5).phaserWet(0.8))   // rate then wet
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, phaser(4.0).phaserWet(1.0))   // full-wet fast phaser
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.phaserWet(wet: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.phaserWet(wet, callInfo) }

/**
 * Alias for [phaserWet]. Sets the phaser wet amount for this pattern.
 *
 * When [amount] is omitted, the pattern's own numeric values are reinterpreted as the phaser wet.
 *
 * @param amount The wet amount (0–1). Omit to reinterpret the pattern's values as the phaser wet.
 * @return A new pattern with the phaser wet applied.
 *
 * ```KlangScript(Playable)
 * note("c3*4").phaser(0.5).phd(0.8)   // deep phaser sweep
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").phd("<0.2 0.5 0.8 1.0>")   // increasing phaser depth
 * ```
 *
 * ```KlangScript(Playable)
 * seq("0.2 0.5 0.8 1.0").phd()   // reinterpret values as phaser depth
 * ```
 *
 * @alias phaserWet, phasdp
 * @category effects
 * @tags phd, phaserdepth, phasdp, phaser, depth, modulation
 */
@KlangScript.Function
fun SprudelPattern.phd(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.phaserWet(amount, callInfo)

/**
 * Alias for [phaserWet]. Parses this string as a pattern and sets the phaser wet.
 *
 * When [amount] is omitted, the string's numeric values are reinterpreted as the phaser wet.
 *
 * @param amount The wet amount (0–1). Omit to reinterpret the pattern's values as the phaser wet.
 *
 * ```KlangScript(Playable)
 * "c3*4".phd(0.8).phaser(0.5).note()   // deep phaser on string pattern
 * ```
 */
@KlangScript.Function
fun String.phd(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).phaserWet(amount, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the phaser depth. Alias for [phaserWet].
 *
 * @param amount The wet amount (0–1). Omit to reinterpret the pattern's values as the phaser wet.
 * @return A [PatternMapperFn] that sets the phaser wet.
 *
 * ```KlangScript(Playable)
 * note("c3*4").apply(phd(0.8))   // deep phaser via mapper
 * ```
 *
 * @alias phaserWet, phasdp
 * @category effects
 * @tags phd, phaserdepth, phasdp, phaser, depth, modulation
 */
@KlangScript.Function
fun phd(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.phaserWet(amount, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the phaser wet (alias for phaserWet) after the previous mapper.
 *
 * @param amount The wet amount (0–1). Omit to reinterpret the pattern's values as the phaser wet.
 * @return A new [PatternMapperFn] chaining this phaser wet after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(phaser(0.5).phd(0.8))   // rate then depth
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, phaser(4.0).phd(1.0))   // full-depth fast phaser
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.phd(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.phaserWet(amount, callInfo) }

/**
 * Alias for [phaserWet]. Sets the phaser wet amount for this pattern.
 *
 * When [amount] is omitted, the pattern's own numeric values are reinterpreted as the phaser wet.
 *
 * @param amount The wet amount (0–1). Omit to reinterpret the pattern's values as the phaser wet.
 * @return A new pattern with the phaser wet applied.
 *
 * ```KlangScript(Playable)
 * note("c3*4").phaser(0.5).phasdp(0.8)   // deep phaser sweep
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").phasdp("<0.2 0.5 0.8 1.0>")   // increasing phaser depth
 * ```
 *
 * ```KlangScript(Playable)
 * seq("0.2 0.5 0.8 1.0").phasdp()   // reinterpret values as phaser depth
 * ```
 *
 * @alias phaserWet, phd
 * @category effects
 * @tags phasdp, phaserdepth, phd, phaser, depth, modulation
 */
@KlangScript.Function
fun SprudelPattern.phasdp(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.phaserWet(amount, callInfo)

/**
 * Alias for [phaserWet]. Parses this string as a pattern and sets the phaser wet.
 *
 * When [amount] is omitted, the string's numeric values are reinterpreted as the phaser wet.
 *
 * @param amount The wet amount (0–1). Omit to reinterpret the pattern's values as the phaser wet.
 *
 * ```KlangScript(Playable)
 * "c3*4".phasdp(0.8).phaser(0.5).note()   // deep phaser on string pattern
 * ```
 */
@KlangScript.Function
fun String.phasdp(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).phaserWet(amount, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the phaser depth. Alias for [phaserWet].
 *
 * @param amount The wet amount (0–1). Omit to reinterpret the pattern's values as the phaser wet.
 * @return A [PatternMapperFn] that sets the phaser wet.
 *
 * ```KlangScript(Playable)
 * note("c3*4").apply(phasdp(0.8))   // deep phaser via mapper
 * ```
 *
 * @alias phaserWet, phd
 * @category effects
 * @tags phasdp, phaserdepth, phd, phaser, depth, modulation
 */
@KlangScript.Function
fun phasdp(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.phaserWet(amount, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the phaser wet (alias for phaserWet) after the previous mapper.
 *
 * @param amount The wet amount (0–1). Omit to reinterpret the pattern's values as the phaser wet.
 * @return A new [PatternMapperFn] chaining this phaser wet after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(phaser(0.5).phasdp(0.8))   // rate then depth
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, phaser(4.0).phasdp(1.0))   // full-depth fast phaser
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.phasdp(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.phaserWet(amount, callInfo) }

// -- phaserFloor() ----------------------------------------------------------------------------------------------------

private val phaserFloorMutation = voiceSetter { phaserFloor = it?.asDoubleOrNull() }

private fun applyPhaserFloor(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, phaserFloorMutation)
}

/**
 * Sets the phaser's minimum dry coefficient — the floor of the shared wet/dry law (C4).
 *
 * The default is `1.0`: the dry passes untouched and [phaserWet] ADDS the swept signal on
 * top (the classic orbit-phaser behaviour). Lowering the floor lets the dry fade as the wet
 * rises, turning the same knob into a crossfade. Two raw-engine truths to know: (1) on the
 * default `modern` pipeline the phaser runs TWICE per note (per-voice strip pass + cylinder
 * bus) from the same knobs, so a floor below 1 floors the dry twice — the surviving dry is
 * about `floor²`, not `floor`; (2) patterning this knob steps the orbit's dry gain at block
 * boundaries with no smoothing — expect zipper on fast patterns. When [floor] is omitted,
 * the pattern's own numeric values are reinterpreted.
 *
 * ```KlangScript(Playable)
 * note("c3*4").s("sawtooth").phaser(0.5).phaserWet(0.8).phaserFloor(0.3)   // crossfading phaser
 * ```
 *
 * @param floor The minimum dry coefficient (0–1). Omit to reinterpret the pattern's values.
 * @return A new pattern with the phaser floor applied.
 * @category effects
 * @tags phaserfloor, phaser, floor, dry, wet
 */
@KlangScript.Function
fun SprudelPattern.phaserFloor(floor: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyPhaserFloor(this, listOfNotNull(floor).asSprudelDslArgs(callInfo))

/** Parses this string as a pattern and sets the phaser floor (see [SprudelPattern.phaserFloor]). */
@KlangScript.Function
fun String.phaserFloor(floor: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).phaserFloor(floor, callInfo)

/** Returns a [PatternMapperFn] that sets the phaser floor (see [SprudelPattern.phaserFloor]). */
@KlangScript.Function
fun phaserFloor(floor: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.phaserFloor(floor, callInfo) }

/** Creates a chained [PatternMapperFn] that sets the phaser floor after the previous mapper. */
@KlangScript.Function
fun PatternMapperFn.phaserFloor(floor: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.phaserFloor(floor, callInfo) }

// -- phasercenter() / phc() -------------------------------------------------------------------------------------------

private val phaserCenterMutation = voiceSetter { phaserCenter = it?.asDoubleOrNull() }

private fun applyPhaserCenter(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, phaserCenterMutation)
}

/**
 * Sets the phaser center frequency in Hz for this pattern.
 *
 * The center frequency is the midpoint of the phaser's sweep range. Adjusting it shifts
 * where the notch-filter effect is focused in the frequency spectrum.
 * When [freq] is omitted, the pattern's own numeric values are reinterpreted as the center frequency.
 *
 * @param freq The center frequency in Hz. Omit to reinterpret the pattern's values as center frequency.
 * @return A new pattern with the phaser center frequency applied.
 *
 * ```KlangScript(Playable)
 * note("c3*4").phaser(0.5).phasercenter(1000)   // centered around 1 kHz
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").phasercenter("<500 1000 2000 4000>")   // sweeping center frequency
 * ```
 *
 * ```KlangScript(Playable)
 * seq("500 1000 2000 4000").phasercenter()   // reinterpret values as center frequency
 * ```
 *
 * @alias phc
 * @category effects
 * @tags phasercenter, phc, phaser, frequency, center
 */
@KlangScript.Function
fun SprudelPattern.phasercenter(freq: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyPhaserCenter(this, listOfNotNull(freq).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets the phaser center frequency.
 *
 * When [freq] is omitted, the string's numeric values are reinterpreted as the center frequency.
 *
 * @param freq The center frequency in Hz. Omit to reinterpret the pattern's values as center frequency.
 *
 * ```KlangScript(Playable)
 * "c3*4".phasercenter(1000).phaser(0.5).note()   // centered phaser on string pattern
 * ```
 */
@KlangScript.Function
fun String.phasercenter(freq: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).phasercenter(freq, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the phaser center frequency.
 *
 * Use the returned mapper as a transform argument or apply it via `.apply(...)`.
 * When [freq] is omitted, the pattern's own numeric values are reinterpreted as the center frequency.
 *
 * @param freq The center frequency in Hz. Omit to reinterpret the pattern's values as center frequency.
 * @return A [PatternMapperFn] that sets the phaser center frequency.
 *
 * ```KlangScript(Playable)
 * note("c3*4").apply(phasercenter(1000))   // 1 kHz center via mapper
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, phasercenter(4000))   // high-frequency center every 4th cycle
 * ```
 *
 * @alias phc
 * @category effects
 * @tags phasercenter, phc, phaser, frequency, center
 */
@KlangScript.Function
fun phasercenter(freq: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.phasercenter(freq, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the phaser center frequency after the previous mapper.
 *
 * @param freq The center frequency in Hz. Omit to reinterpret the pattern's values as center frequency.
 * @return A new [PatternMapperFn] chaining this center frequency after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(phaser(0.5).phasercenter(1000))   // rate then center frequency
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, phaser(2.0).phasercenter(2000))   // centered fast phaser
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.phasercenter(freq: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.phasercenter(freq, callInfo) }

/**
 * Alias for [phasercenter]. Sets the phaser center frequency in Hz for this pattern.
 *
 * When [freq] is omitted, the pattern's own numeric values are reinterpreted as the center frequency.
 *
 * @param freq The center frequency in Hz. Omit to reinterpret the pattern's values as center frequency.
 * @return A new pattern with the phaser center frequency applied.
 *
 * ```KlangScript(Playable)
 * note("c3*4").phaser(0.5).phc(1000)   // centered around 1 kHz
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").phc("<500 1000 2000 4000>")   // sweeping center frequency
 * ```
 *
 * ```KlangScript(Playable)
 * seq("500 1000 2000 4000").phc()   // reinterpret values as center frequency
 * ```
 *
 * @alias phasercenter
 * @category effects
 * @tags phc, phasercenter, phaser, frequency, center
 */
@KlangScript.Function
fun SprudelPattern.phc(freq: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.phasercenter(freq, callInfo)

/**
 * Alias for [phasercenter]. Parses this string as a pattern and sets the phaser center frequency.
 *
 * When [freq] is omitted, the string's numeric values are reinterpreted as the center frequency.
 *
 * @param freq The center frequency in Hz. Omit to reinterpret the pattern's values as center frequency.
 *
 * ```KlangScript(Playable)
 * "c3*4".phc(1000).phaser(0.5).note()   // centered phaser on string pattern
 * ```
 */
@KlangScript.Function
fun String.phc(freq: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).phasercenter(freq, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the phaser center frequency. Alias for [phasercenter].
 *
 * @param freq The center frequency in Hz. Omit to reinterpret the pattern's values as center frequency.
 * @return A [PatternMapperFn] that sets the phaser center frequency.
 *
 * ```KlangScript(Playable)
 * note("c3*4").apply(phc(1000))   // 1 kHz center via mapper
 * ```
 *
 * @alias phasercenter
 * @category effects
 * @tags phc, phasercenter, phaser, frequency, center
 */
@KlangScript.Function
fun phc(freq: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.phasercenter(freq, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the phaser center frequency (alias for phasercenter) after the
 * previous mapper.
 *
 * @param freq The center frequency in Hz. Omit to reinterpret the pattern's values as center frequency.
 * @return A new [PatternMapperFn] chaining this center frequency after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(phaser(0.5).phc(1000))   // rate then center frequency
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, phaser(2.0).phc(2000))   // centered fast phaser
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.phc(freq: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.phasercenter(freq, callInfo) }

// -- phasersweep() / phs() --------------------------------------------------------------------------------------------

private val phaserSweepMutation = voiceSetter { phaserSweep = it?.asDoubleOrNull() }

private fun applyPhaserSweep(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, phaserSweepMutation)
}

/**
 * Sets the phaser sweep range in Hz (half the total sweep width) for this pattern.
 *
 * Controls how wide the phaser's frequency sweep is around the center frequency.
 * Larger values produce a more dramatic, wider sweep effect.
 * When [amount] is omitted, the pattern's own numeric values are reinterpreted as the sweep range.
 *
 * @param amount The sweep range in Hz. Omit to reinterpret the pattern's values as sweep range.
 * @return A new pattern with the phaser sweep range applied.
 *
 * ```KlangScript(Playable)
 * note("c3*4").phaser(0.5).phasersweep(2000)   // ±2000 Hz sweep
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").phasersweep("<500 1000 2000 4000>")   // increasing sweep width
 * ```
 *
 * ```KlangScript(Playable)
 * seq("500 1000 2000 4000").phasersweep()   // reinterpret values as sweep range
 * ```
 *
 * @alias phs
 * @category effects
 * @tags phasersweep, phs, phaser, sweep, width
 */
@KlangScript.Function
fun SprudelPattern.phasersweep(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyPhaserSweep(this, listOfNotNull(amount).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets the phaser sweep range.
 *
 * When [amount] is omitted, the string's numeric values are reinterpreted as the sweep range.
 *
 * @param amount The sweep range in Hz. Omit to reinterpret the pattern's values as sweep range.
 *
 * ```KlangScript(Playable)
 * "c3*4".phasersweep(2000).phaser(0.5).note()   // wide sweep on string pattern
 * ```
 */
@KlangScript.Function
fun String.phasersweep(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).phasersweep(amount, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the phaser sweep range.
 *
 * Use the returned mapper as a transform argument or apply it via `.apply(...)`.
 * When [amount] is omitted, the pattern's own numeric values are reinterpreted as the sweep range.
 *
 * @param amount The sweep range in Hz. Omit to reinterpret the pattern's values as sweep range.
 * @return A [PatternMapperFn] that sets the phaser sweep range.
 *
 * ```KlangScript(Playable)
 * note("c3*4").apply(phasersweep(2000))   // ±2000 Hz sweep via mapper
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, phasersweep(4000))   // wide sweep every 4th cycle
 * ```
 *
 * @alias phs
 * @category effects
 * @tags phasersweep, phs, phaser, sweep, width
 */
@KlangScript.Function
fun phasersweep(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.phasersweep(amount, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the phaser sweep range after the previous mapper.
 *
 * @param amount The sweep range in Hz. Omit to reinterpret the pattern's values as sweep range.
 * @return A new [PatternMapperFn] chaining this sweep range after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(phaser(0.5).phasersweep(2000))   // rate then sweep range
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, phaser(2.0).phasersweep(4000))   // wide sweep fast phaser
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.phasersweep(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.phasersweep(amount, callInfo) }

/**
 * Alias for [phasersweep]. Sets the phaser sweep range in Hz for this pattern.
 *
 * When [amount] is omitted, the pattern's own numeric values are reinterpreted as the sweep range.
 *
 * @param amount The sweep range in Hz. Omit to reinterpret the pattern's values as sweep range.
 * @return A new pattern with the phaser sweep range applied.
 *
 * ```KlangScript(Playable)
 * note("c3*4").phaser(0.5).phs(2000)   // ±2000 Hz sweep
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").phs("<500 1000 2000 4000>")   // increasing sweep width
 * ```
 *
 * ```KlangScript(Playable)
 * seq("500 1000 2000 4000").phs()   // reinterpret values as sweep range
 * ```
 *
 * @alias phasersweep
 * @category effects
 * @tags phs, phasersweep, phaser, sweep, width
 */
@KlangScript.Function
fun SprudelPattern.phs(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.phasersweep(amount, callInfo)

/**
 * Alias for [phasersweep]. Parses this string as a pattern and sets the phaser sweep range.
 *
 * When [amount] is omitted, the string's numeric values are reinterpreted as the sweep range.
 *
 * @param amount The sweep range in Hz. Omit to reinterpret the pattern's values as sweep range.
 *
 * ```KlangScript(Playable)
 * "c3*4".phs(2000).phaser(0.5).note()   // wide sweep on string pattern
 * ```
 */
@KlangScript.Function
fun String.phs(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).phasersweep(amount, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the phaser sweep range. Alias for [phasersweep].
 *
 * @param amount The sweep range in Hz. Omit to reinterpret the pattern's values as sweep range.
 * @return A [PatternMapperFn] that sets the phaser sweep range.
 *
 * ```KlangScript(Playable)
 * note("c3*4").apply(phs(2000))   // ±2000 Hz sweep via mapper
 * ```
 *
 * @alias phasersweep
 * @category effects
 * @tags phs, phasersweep, phaser, sweep, width
 */
@KlangScript.Function
fun phs(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.phasersweep(amount, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the phaser sweep range (alias for phasersweep) after the previous
 * mapper.
 *
 * @param amount The sweep range in Hz. Omit to reinterpret the pattern's values as sweep range.
 * @return A new [PatternMapperFn] chaining this sweep range after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(phaser(0.5).phs(2000))   // rate then sweep range
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, phaser(2.0).phs(4000))   // wide sweep fast phaser
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.phs(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.phasersweep(amount, callInfo) }

// -- tremolosync() / tremsync() ---------------------------------------------------------------------------------------

private val tremoloSyncMutation = voiceSetter { tremoloSync = it?.asDoubleOrNull() }

private fun applyTremoloSync(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, tremoloSyncMutation)
}

/**
 * Sets the tremolo LFO rate in Hz for this pattern.
 *
 * Controls the speed of the tremolo amplitude modulation effect. Use with `tremolodepth`
 * to set the modulation intensity and `tremoloshape` to choose the LFO waveform.
 * When [rate] is omitted, the pattern's own numeric values are reinterpreted as the tremolo rate.
 *
 * @param rate The tremolo LFO rate in Hz. Omit to reinterpret the pattern's values as tremolo rate.
 * @return A new pattern with the tremolo rate applied.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("sine").tremolosync(4)   // 4 Hz tremolo
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").tremolosync("<1 2 4 8>")   // accelerating tremolo rate
 * ```
 *
 * ```KlangScript(Playable)
 * seq("1 2 4 8").tremolosync()   // reinterpret values as tremolo rate
 * ```
 *
 * @alias tremsync
 * @category effects
 * @tags tremolosync, tremsync, tremolo, rate, modulation
 */
@KlangScript.Function
fun SprudelPattern.tremolosync(rate: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyTremoloSync(this, listOfNotNull(rate).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets the tremolo LFO rate.
 *
 * When [rate] is omitted, the string's numeric values are reinterpreted as the tremolo rate.
 *
 * @param rate The tremolo LFO rate in Hz. Omit to reinterpret the pattern's values as tremolo rate.
 *
 * ```KlangScript(Playable)
 * "c3 e3".tremolosync(4).note().s("sine")   // 4 Hz tremolo on string pattern
 * ```
 */
@KlangScript.Function
fun String.tremolosync(rate: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).tremolosync(rate, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the tremolo LFO rate.
 *
 * Use the returned mapper as a transform argument or apply it via `.apply(...)`.
 * When [rate] is omitted, the pattern's own numeric values are reinterpreted as the tremolo rate.
 *
 * @param rate The tremolo LFO rate in Hz. Omit to reinterpret the pattern's values as tremolo rate.
 * @return A [PatternMapperFn] that sets the tremolo rate.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(tremolosync(4))   // 4 Hz tremolo via mapper
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, tremolosync(8))   // fast tremolo every 4th cycle
 * ```
 *
 * @alias tremsync
 * @category effects
 * @tags tremolosync, tremsync, tremolo, rate, modulation
 */
@KlangScript.Function
fun tremolosync(rate: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.tremolosync(rate, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the tremolo LFO rate after the previous mapper.
 *
 * @param rate The tremolo LFO rate in Hz. Omit to reinterpret the pattern's values as tremolo rate.
 * @return A new [PatternMapperFn] chaining this tremolo rate after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(tremolodepth(0.8).tremolosync(4))   // depth then rate
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, tremolodepth(1.0).tremolosync(8))   // full-depth fast tremolo
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.tremolosync(rate: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.tremolosync(rate, callInfo) }

/**
 * Alias for [tremolosync]. Sets the tremolo LFO rate in Hz for this pattern.
 *
 * When [rate] is omitted, the pattern's own numeric values are reinterpreted as the tremolo rate.
 *
 * @param rate The tremolo LFO rate in Hz. Omit to reinterpret the pattern's values as tremolo rate.
 * @return A new pattern with the tremolo rate applied.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("sine").tremsync(4)   // 4 Hz tremolo
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").tremsync("<1 2 4 8>")   // accelerating tremolo rate
 * ```
 *
 * ```KlangScript(Playable)
 * seq("1 2 4 8").tremsync()   // reinterpret values as tremolo rate
 * ```
 *
 * @alias tremolosync
 * @category effects
 * @tags tremsync, tremolosync, tremolo, rate, modulation
 */
@KlangScript.Function
fun SprudelPattern.tremsync(rate: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.tremolosync(rate, callInfo)

/**
 * Alias for [tremolosync]. Parses this string as a pattern and sets the tremolo LFO rate.
 *
 * When [rate] is omitted, the string's numeric values are reinterpreted as the tremolo rate.
 *
 * @param rate The tremolo LFO rate in Hz. Omit to reinterpret the pattern's values as tremolo rate.
 *
 * ```KlangScript(Playable)
 * "c3 e3".tremsync(4).note().s("sine")   // 4 Hz tremolo on string pattern
 * ```
 */
@KlangScript.Function
fun String.tremsync(rate: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).tremolosync(rate, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the tremolo LFO rate. Alias for [tremolosync].
 *
 * @param rate The tremolo LFO rate in Hz. Omit to reinterpret the pattern's values as tremolo rate.
 * @return A [PatternMapperFn] that sets the tremolo rate.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(tremsync(4))   // 4 Hz tremolo via mapper
 * ```
 *
 * @alias tremolosync
 * @category effects
 * @tags tremsync, tremolosync, tremolo, rate, modulation
 */
@KlangScript.Function
fun tremsync(rate: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.tremolosync(rate, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the tremolo LFO rate (alias for tremolosync) after the previous
 * mapper.
 *
 * @param rate The tremolo LFO rate in Hz. Omit to reinterpret the pattern's values as tremolo rate.
 * @return A new [PatternMapperFn] chaining this tremolo rate after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(tremolodepth(0.8).tremsync(4))   // depth then rate
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, tremolodepth(1.0).tremsync(8))   // full-depth fast tremolo
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.tremsync(rate: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.tremolosync(rate, callInfo) }

// -- tremolodepth() / tremdepth() -------------------------------------------------------------------------------------

private val tremoloDepthMutation = voiceSetter { tremoloDepth = it?.asDoubleOrNull() }

private fun applyTremoloDepth(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, tremoloDepthMutation)
}

/**
 * Sets the tremolo depth (modulation intensity, 0–1) for this pattern.
 *
 * Controls how much the amplitude is modulated by the tremolo LFO. `0` = no effect;
 * `1` = full amplitude modulation (silence to full volume).
 * When [amount] is omitted, the pattern's own numeric values are reinterpreted as tremolo depth.
 *
 * @param amount The modulation intensity (0–1). Omit to reinterpret the pattern's values as tremolo depth.
 * @return A new pattern with the tremolo depth applied.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("sine").tremolosync(4).tremolodepth(0.8)   // strong tremolo
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").tremolodepth("<0.2 0.5 0.8 1.0>")             // increasing depth
 * ```
 *
 * ```KlangScript(Playable)
 * seq("0.2 0.5 0.8 1.0").tremolodepth()   // reinterpret values as tremolo depth
 * ```
 *
 * @alias tremdepth
 * @category effects
 * @tags tremolodepth, tremdepth, tremolo, depth, modulation
 */
@KlangScript.Function
fun SprudelPattern.tremolodepth(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyTremoloDepth(this, listOfNotNull(amount).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets the tremolo depth.
 *
 * When [amount] is omitted, the string's numeric values are reinterpreted as tremolo depth.
 *
 * @param amount The modulation intensity (0–1). Omit to reinterpret the pattern's values as tremolo depth.
 *
 * ```KlangScript(Playable)
 * "c3 e3".tremolodepth(0.8).tremolosync(4).note()   // strong tremolo on string pattern
 * ```
 */
@KlangScript.Function
fun String.tremolodepth(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).tremolodepth(amount, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the tremolo depth.
 *
 * Use the returned mapper as a transform argument or apply it via `.apply(...)`.
 * When [amount] is omitted, the pattern's own numeric values are reinterpreted as tremolo depth.
 *
 * @param amount The modulation intensity (0–1). Omit to reinterpret the pattern's values as tremolo depth.
 * @return A [PatternMapperFn] that sets the tremolo depth.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(tremolodepth(0.8))   // strong tremolo via mapper
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, tremolodepth(1.0))   // max depth every 4th cycle
 * ```
 *
 * @alias tremdepth
 * @category effects
 * @tags tremolodepth, tremdepth, tremolo, depth, modulation
 */
@KlangScript.Function
fun tremolodepth(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.tremolodepth(amount, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the tremolo depth after the previous mapper.
 *
 * @param amount The modulation intensity (0–1). Omit to reinterpret the pattern's values as tremolo depth.
 * @return A new [PatternMapperFn] chaining this tremolo depth after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(tremolosync(4).tremolodepth(0.8))   // rate then depth
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, tremolosync(8).tremolodepth(1.0))   // full-depth fast tremolo
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.tremolodepth(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.tremolodepth(amount, callInfo) }

/**
 * Alias for [tremolodepth]. Sets the tremolo depth (modulation intensity, 0–1) for this pattern.
 *
 * When [amount] is omitted, the pattern's own numeric values are reinterpreted as tremolo depth.
 *
 * @param amount The modulation intensity (0–1). Omit to reinterpret the pattern's values as tremolo depth.
 * @return A new pattern with the tremolo depth applied.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("sine").tremolosync(4).tremdepth(0.8)   // strong tremolo
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").tremdepth("<0.2 0.5 0.8 1.0>")             // increasing depth
 * ```
 *
 * ```KlangScript(Playable)
 * seq("0.2 0.5 0.8 1.0").tremdepth()   // reinterpret values as tremolo depth
 * ```
 *
 * @alias tremolodepth
 * @category effects
 * @tags tremdepth, tremolodepth, tremolo, depth, modulation
 */
@KlangScript.Function
fun SprudelPattern.tremdepth(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.tremolodepth(amount, callInfo)

/**
 * Alias for [tremolodepth]. Parses this string as a pattern and sets the tremolo depth.
 *
 * When [amount] is omitted, the string's numeric values are reinterpreted as tremolo depth.
 *
 * @param amount The modulation intensity (0–1). Omit to reinterpret the pattern's values as tremolo depth.
 *
 * ```KlangScript(Playable)
 * "c3 e3".tremdepth(0.8).tremolosync(4).note()   // strong tremolo on string pattern
 * ```
 */
@KlangScript.Function
fun String.tremdepth(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).tremolodepth(amount, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the tremolo depth. Alias for [tremolodepth].
 *
 * @param amount The modulation intensity (0–1). Omit to reinterpret the pattern's values as tremolo depth.
 * @return A [PatternMapperFn] that sets the tremolo depth.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(tremdepth(0.8))   // strong tremolo via mapper
 * ```
 *
 * @alias tremolodepth
 * @category effects
 * @tags tremdepth, tremolodepth, tremolo, depth, modulation
 */
@KlangScript.Function
fun tremdepth(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.tremolodepth(amount, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the tremolo depth (alias for tremolodepth) after the previous mapper.
 *
 * @param amount The modulation intensity (0–1). Omit to reinterpret the pattern's values as tremolo depth.
 * @return A new [PatternMapperFn] chaining this tremolo depth after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(tremolosync(4).tremdepth(0.8))   // rate then depth
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, tremolosync(8).tremdepth(1.0))   // full-depth fast tremolo
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.tremdepth(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.tremolodepth(amount, callInfo) }

// -- tremoloskew() / tremskew() ---------------------------------------------------------------------------------------

private val tremoloSkewMutation = voiceSetter { tremoloSkew = it?.asDoubleOrNull() }

private fun applyTremoloSkew(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, tremoloSkewMutation)
}

/**
 * Sets the tremolo LFO skew (asymmetry) value for this pattern.
 *
 * Adjusts the asymmetry of the tremolo waveform. A value of `0.5` is symmetric;
 * values above or below shift the waveform to spend more time at the top or bottom.
 * When [amount] is omitted, the pattern's own numeric values are reinterpreted as the skew.
 *
 * @param amount The skew value. Omit to reinterpret the pattern's values as tremolo skew.
 * @return A new pattern with the tremolo skew applied.
 *
 * ```KlangScript(Playable)
 * note("c3*4").tremolosync(2).tremoloskew(0.8)   // skewed toward peak
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").tremoloskew("<0.2 0.5 0.8>")      // varying asymmetry
 * ```
 *
 * ```KlangScript(Playable)
 * seq("0.2 0.5 0.8").tremoloskew()   // reinterpret values as tremolo skew
 * ```
 *
 * @alias tremskew
 * @category effects
 * @tags tremoloskew, tremskew, tremolo, skew, asymmetry
 */
@KlangScript.Function
fun SprudelPattern.tremoloskew(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyTremoloSkew(this, listOfNotNull(amount).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets the tremolo LFO skew.
 *
 * When [amount] is omitted, the string's numeric values are reinterpreted as the skew.
 *
 * @param amount The skew value. Omit to reinterpret the pattern's values as tremolo skew.
 *
 * ```KlangScript(Playable)
 * "c3*4".tremoloskew(0.8).tremolosync(2).note()   // skewed tremolo on string pattern
 * ```
 */
@KlangScript.Function
fun String.tremoloskew(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).tremoloskew(amount, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the tremolo LFO skew.
 *
 * Use the returned mapper as a transform argument or apply it via `.apply(...)`.
 * When [amount] is omitted, the pattern's own numeric values are reinterpreted as the skew.
 *
 * @param amount The skew value. Omit to reinterpret the pattern's values as tremolo skew.
 * @return A [PatternMapperFn] that sets the tremolo skew.
 *
 * ```KlangScript(Playable)
 * note("c3*4").apply(tremoloskew(0.8))   // skewed tremolo via mapper
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, tremoloskew(0.2))   // inverted skew every 4th cycle
 * ```
 *
 * @alias tremskew
 * @category effects
 * @tags tremoloskew, tremskew, tremolo, skew, asymmetry
 */
@KlangScript.Function
fun tremoloskew(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.tremoloskew(amount, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the tremolo LFO skew after the previous mapper.
 *
 * @param amount The skew value. Omit to reinterpret the pattern's values as tremolo skew.
 * @return A new [PatternMapperFn] chaining this tremolo skew after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(tremolosync(2).tremoloskew(0.8))   // rate then skew
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, tremolosync(4).tremoloskew(0.2))   // inverted skew fast tremolo
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.tremoloskew(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.tremoloskew(amount, callInfo) }

/**
 * Alias for [tremoloskew]. Sets the tremolo LFO skew (asymmetry) value for this pattern.
 *
 * When [amount] is omitted, the pattern's own numeric values are reinterpreted as the skew.
 *
 * @param amount The skew value. Omit to reinterpret the pattern's values as tremolo skew.
 * @return A new pattern with the tremolo skew applied.
 *
 * ```KlangScript(Playable)
 * note("c3*4").tremolosync(2).tremskew(0.8)   // skewed toward peak
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").tremskew("<0.2 0.5 0.8>")      // varying asymmetry
 * ```
 *
 * ```KlangScript(Playable)
 * seq("0.2 0.5 0.8").tremskew()   // reinterpret values as tremolo skew
 * ```
 *
 * @alias tremoloskew
 * @category effects
 * @tags tremskew, tremoloskew, tremolo, skew, asymmetry
 */
@KlangScript.Function
fun SprudelPattern.tremskew(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.tremoloskew(amount, callInfo)

/**
 * Alias for [tremoloskew]. Parses this string as a pattern and sets the tremolo LFO skew.
 *
 * When [amount] is omitted, the string's numeric values are reinterpreted as the skew.
 *
 * @param amount The skew value. Omit to reinterpret the pattern's values as tremolo skew.
 *
 * ```KlangScript(Playable)
 * "c3*4".tremskew(0.8).tremolosync(2).note()   // skewed tremolo on string pattern
 * ```
 */
@KlangScript.Function
fun String.tremskew(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).tremoloskew(amount, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the tremolo LFO skew. Alias for [tremoloskew].
 *
 * @param amount The skew value. Omit to reinterpret the pattern's values as tremolo skew.
 * @return A [PatternMapperFn] that sets the tremolo skew.
 *
 * ```KlangScript(Playable)
 * note("c3*4").apply(tremskew(0.8))   // skewed tremolo via mapper
 * ```
 *
 * @alias tremoloskew
 * @category effects
 * @tags tremskew, tremoloskew, tremolo, skew, asymmetry
 */
@KlangScript.Function
fun tremskew(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.tremoloskew(amount, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the tremolo LFO skew (alias for tremoloskew) after the previous
 * mapper.
 *
 * @param amount The skew value. Omit to reinterpret the pattern's values as tremolo skew.
 * @return A new [PatternMapperFn] chaining this tremolo skew after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(tremolosync(2).tremskew(0.8))   // rate then skew
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, tremolosync(4).tremskew(0.2))   // inverted skew fast tremolo
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.tremskew(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.tremoloskew(amount, callInfo) }

// -- tremolophase() / tremphase() -------------------------------------------------------------------------------------

private val tremoloPhaseMutation = voiceSetter { tremoloPhase = it?.asDoubleOrNull() }

private fun applyTremoloPhase(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, tremoloPhaseMutation)
}

/**
 * Sets the tremolo LFO starting phase in radians for this pattern.
 *
 * Controls where in its cycle the tremolo LFO begins. Use to offset the tremolo
 * relative to the beat or other patterns playing simultaneously.
 * When [phase] is omitted, the pattern's own numeric values are reinterpreted as the phase.
 *
 * @param phase The starting phase in radians. Omit to reinterpret the pattern's values as tremolo phase.
 * @return A new pattern with the tremolo phase applied.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").tremolosync(2).tremolophase(1.57)   // start at 90°
 * ```
 *
 * ```KlangScript(Playable)
 * stack(
 *   note("c3").tremolosync(2).tremolophase(0),
 *   note("e3").tremolosync(2).tremolophase(3.14),   // 180° offset
 * )
 * ```
 *
 * ```KlangScript(Playable)
 * seq("0 1.57 3.14 4.71").tremolophase()   // reinterpret values as tremolo phase
 * ```
 *
 * @alias tremphase
 * @category effects
 * @tags tremolophase, tremphase, tremolo, phase, offset
 */
@KlangScript.Function
fun SprudelPattern.tremolophase(phase: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyTremoloPhase(this, listOfNotNull(phase).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets the tremolo LFO starting phase.
 *
 * When [phase] is omitted, the string's numeric values are reinterpreted as the phase.
 *
 * @param phase The starting phase in radians. Omit to reinterpret the pattern's values as tremolo phase.
 *
 * ```KlangScript(Playable)
 * "c3 e3".tremolophase(1.57).tremolosync(2).note()   // 90° tremolo on string pattern
 * ```
 */
@KlangScript.Function
fun String.tremolophase(phase: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).tremolophase(phase, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the tremolo LFO starting phase.
 *
 * Use the returned mapper as a transform argument or apply it via `.apply(...)`.
 * When [phase] is omitted, the pattern's own numeric values are reinterpreted as the phase.
 *
 * @param phase The starting phase in radians. Omit to reinterpret the pattern's values as tremolo phase.
 * @return A [PatternMapperFn] that sets the tremolo phase.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(tremolophase(1.57))   // 90° start via mapper
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, tremolophase(3.14))   // 180° start every 4th cycle
 * ```
 *
 * @alias tremphase
 * @category effects
 * @tags tremolophase, tremphase, tremolo, phase, offset
 */
@KlangScript.Function
fun tremolophase(phase: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.tremolophase(phase, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the tremolo LFO starting phase after the previous mapper.
 *
 * @param phase The starting phase in radians. Omit to reinterpret the pattern's values as tremolo phase.
 * @return A new [PatternMapperFn] chaining this tremolo phase after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(tremolosync(2).tremolophase(1.57))   // rate then phase
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, tremolosync(4).tremolophase(3.14))   // fast tremolo at 180°
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.tremolophase(phase: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.tremolophase(phase, callInfo) }

/**
 * Alias for [tremolophase]. Sets the tremolo LFO starting phase in radians for this pattern.
 *
 * When [phase] is omitted, the pattern's own numeric values are reinterpreted as the phase.
 *
 * @param phase The starting phase in radians. Omit to reinterpret the pattern's values as tremolo phase.
 * @return A new pattern with the tremolo phase applied.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").tremolosync(2).tremphase(1.57)   // start at 90°
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").tremphase("<0 1.57 3.14 4.71>")   // quarter-turn offsets
 * ```
 *
 * ```KlangScript(Playable)
 * seq("0 1.57 3.14 4.71").tremphase()   // reinterpret values as tremolo phase
 * ```
 *
 * @alias tremolophase
 * @category effects
 * @tags tremphase, tremolophase, tremolo, phase, offset
 */
@KlangScript.Function
fun SprudelPattern.tremphase(phase: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.tremolophase(phase, callInfo)

/**
 * Alias for [tremolophase]. Parses this string as a pattern and sets the tremolo LFO starting phase.
 *
 * When [phase] is omitted, the string's numeric values are reinterpreted as the phase.
 *
 * @param phase The starting phase in radians. Omit to reinterpret the pattern's values as tremolo phase.
 *
 * ```KlangScript(Playable)
 * "c3 e3".tremphase(1.57).tremolosync(2).note()   // 90° tremolo on string pattern
 * ```
 */
@KlangScript.Function
fun String.tremphase(phase: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).tremolophase(phase, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the tremolo LFO starting phase. Alias for [tremolophase].
 *
 * @param phase The starting phase in radians. Omit to reinterpret the pattern's values as tremolo phase.
 * @return A [PatternMapperFn] that sets the tremolo phase.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(tremphase(1.57))   // 90° start via mapper
 * ```
 *
 * @alias tremolophase
 * @category effects
 * @tags tremphase, tremolophase, tremolo, phase, offset
 */
@KlangScript.Function
fun tremphase(phase: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.tremolophase(phase, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the tremolo LFO starting phase (alias for tremolophase) after the
 * previous mapper.
 *
 * @param phase The starting phase in radians. Omit to reinterpret the pattern's values as tremolo phase.
 * @return A new [PatternMapperFn] chaining this tremolo phase after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(tremolosync(2).tremphase(1.57))   // rate then phase
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, tremolosync(4).tremphase(3.14))   // fast tremolo at 180°
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.tremphase(phase: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.tremolophase(phase, callInfo) }

// -- tremoloshape() / tremshape() -------------------------------------------------------------------------------------

private val tremoloShapeMutation = voiceSetter { shape -> tremoloShape = shape?.toString()?.lowercase() }

private fun applyTremoloShape(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._applyControlFromParams(args, tremoloShapeMutation) { src, ctrl ->
        src.tremoloShape = ctrl.tremoloShape
        src
    }
}

/**
 * Sets the tremolo LFO waveform shape for this pattern.
 *
 * Accepted values: `"sine"`, `"triangle"`, `"square"`, `"sawtooth"`, `"rampup"`, `"rampdown"`.
 * Different shapes produce different tremolo characters — sine is smooth, square is choppy.
 *
 * @param shape The LFO waveform shape name.
 * @param-tool shape SprudelWaveformEditor, SprudelWaveformSequenceEditor
 * @return A new pattern with the tremolo shape applied.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").tremolosync(4).tremoloshape("square")   // choppy on/off tremolo
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").tremoloshape("<sine triangle square>")   // cycle through shapes
 * ```
 *
 * @alias tremshape
 * @category effects
 * @tags tremoloshape, tremshape, tremolo, shape, waveform
 */
@KlangScript.Function
fun SprudelPattern.tremoloshape(shape: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyTremoloShape(this, listOf(shape).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets the tremolo LFO waveform shape.
 *
 * @param shape The LFO waveform shape name.
 *
 * ```KlangScript(Playable)
 * "c3 e3".tremoloshape("square").tremolosync(4).note()   // choppy tremolo on string pattern
 * ```
 */
@KlangScript.Function
fun String.tremoloshape(shape: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).tremoloshape(shape, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the tremolo LFO waveform shape.
 *
 * Use the returned mapper as a transform argument or apply it via `.apply(...)`.
 *
 * @param shape The LFO waveform shape name.
 * @return A [PatternMapperFn] that sets the tremolo shape.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(tremoloshape("square"))   // choppy tremolo via mapper
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, tremoloshape("triangle"))   // triangle tremolo every 4th cycle
 * ```
 *
 * @alias tremshape
 * @category effects
 * @tags tremoloshape, tremshape, tremolo, shape, waveform
 */
@KlangScript.Function
fun tremoloshape(shape: PatternLike, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.tremoloshape(shape, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the tremolo LFO waveform shape after the previous mapper.
 *
 * @param shape The LFO waveform shape name.
 * @return A new [PatternMapperFn] chaining this tremolo shape after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(tremolosync(4).tremoloshape("square"))   // rate then shape
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, tremolosync(8).tremoloshape("triangle"))   // shaped fast tremolo
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.tremoloshape(shape: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.tremoloshape(shape, callInfo) }

/**
 * Alias for [tremoloshape]. Sets the tremolo LFO waveform shape for this pattern.
 *
 * @param shape The LFO waveform shape name.
 * @param-tool shape SprudelWaveformEditor, SprudelWaveformSequenceEditor
 * @return A new pattern with the tremolo shape applied.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").tremolosync(4).tremshape("square")   // choppy on/off tremolo
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").tremshape("<sine triangle square>")   // cycle through shapes
 * ```
 *
 * @alias tremoloshape
 * @category effects
 * @tags tremshape, tremoloshape, tremolo, shape, waveform
 */
@KlangScript.Function
fun SprudelPattern.tremshape(shape: PatternLike, callInfo: CallInfo? = null): SprudelPattern = this.tremoloshape(shape, callInfo)

/**
 * Alias for [tremoloshape]. Parses this string as a pattern and sets the tremolo LFO waveform shape.
 *
 * @param shape The LFO waveform shape name.
 *
 * ```KlangScript(Playable)
 * "c3 e3".tremshape("square").tremolosync(4).note()   // choppy tremolo on string pattern
 * ```
 */
@KlangScript.Function
fun String.tremshape(shape: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).tremoloshape(shape, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the tremolo LFO waveform shape. Alias for [tremoloshape].
 *
 * @param shape The LFO waveform shape name.
 * @return A [PatternMapperFn] that sets the tremolo shape.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(tremshape("square"))   // choppy tremolo via mapper
 * ```
 *
 * @alias tremoloshape
 * @category effects
 * @tags tremshape, tremoloshape, tremolo, shape, waveform
 */
@KlangScript.Function
fun tremshape(shape: PatternLike, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.tremoloshape(shape, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the tremolo LFO waveform shape (alias for tremoloshape) after the
 * previous mapper.
 *
 * @param shape The LFO waveform shape name.
 * @return A new [PatternMapperFn] chaining this tremolo shape after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(tremolosync(4).tremshape("square"))   // rate then shape
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, tremolosync(8).tremshape("triangle"))   // shaped fast tremolo
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.tremshape(shape: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.tremoloshape(shape, callInfo) }

// -- delaycap() / dcap() ----------------------------------------------------------------------------------------------

private val delayCapMutation = voiceSetter { delayCap = it?.asDoubleOrNull() }

private fun applyDelayCap(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, delayCapMutation)
}

/**
 * Sets the ceiling the delay's feedback saturates toward (default 1.0).
 *
 * The engine is raw: `delayfeedback` at or above 1.0 recirculates without loss and the delay
 * **self-oscillates** forever. This decides how loud that runaway sits — it does not forbid it.
 * Below the ceiling the signal is untouched, so the default changes nothing.
 *
 * The master bus has the same knob as `MasterFx.delay().cap(...)`.
 *
 * When [amount] is omitted, the pattern's own numeric values are reinterpreted as the cap.
 *
 * @param amount The ceiling (default 1.0). Omit to reinterpret the pattern's values as the cap.
 * @return A new pattern with the delay feedback ceiling applied.
 *
 * ```KlangScript(Playable)
 * note("c3 ~ ~ ~").delayWet(0.6).delayfeedback(1.0).delaycap(2.0)   // endless echo, held at 2.0
 * ```
 *
 * @alias dcap
 * @category effects
 * @tags delaycap, dcap, delay, feedback, saturation, selfoscillation
 */
@KlangScript.Function
fun SprudelPattern.delaycap(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyDelayCap(this, listOfNotNull(amount).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets the delay feedback ceiling.
 *
 * @param amount The ceiling (default 1.0). Omit to reinterpret the pattern's values as the cap.
 * @return A new pattern with the delay feedback ceiling applied.
 *
 * ```KlangScript(Playable)
 * "c3 ~ ~ ~".delaycap(2.0).delayWet(0.6).delayfeedback(1.0).note()
 * ```
 */
@KlangScript.Function
fun String.delaycap(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).delaycap(amount, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the delay feedback ceiling.
 *
 * @param amount The ceiling (default 1.0). Omit to reinterpret the pattern's values as the cap.
 * @return A [PatternMapperFn] that sets the delay feedback ceiling.
 *
 * ```KlangScript(Playable)
 * note("c3 ~ ~ ~").apply(delaycap(2.0)).delayWet(0.6).delayfeedback(1.0)
 * ```
 *
 * @alias dcap
 * @category effects
 * @tags delaycap, dcap, delay, feedback, saturation, selfoscillation
 */
@KlangScript.Function
fun delaycap(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.delaycap(amount, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the delay feedback ceiling after the previous mapper.
 *
 * @param amount The ceiling (default 1.0). Omit to reinterpret the pattern's values as the cap.
 * @return A new [PatternMapperFn] chaining the ceiling after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 ~ ~ ~").apply(delayWet(0.6).delaycap(2.0))
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.delaycap(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.delaycap(amount, callInfo) }

/**
 * Alias for [delaycap]. Sets the delay feedback ceiling.
 *
 * @param amount The ceiling (default 1.0). Omit to reinterpret the pattern's values as the cap.
 * @return A new pattern with the delay feedback ceiling applied.
 *
 * ```KlangScript(Playable)
 * note("c3 ~ ~ ~").delayWet(0.6).delayfb(1.0).dcap(2.0)
 * ```
 *
 * @alias delaycap
 * @category effects
 * @tags dcap, delaycap, delay, feedback, saturation
 */
@KlangScript.Function
fun SprudelPattern.dcap(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.delaycap(amount, callInfo)

/**
 * Parses this string as a pattern and sets the delay feedback ceiling. Alias for [delaycap].
 *
 * @param amount The ceiling (default 1.0). Omit to reinterpret the pattern's values as the cap.
 * @return A new pattern with the delay feedback ceiling applied.
 *
 * ```KlangScript(Playable)
 * "c3 ~ ~ ~".dcap(2.0).delayWet(0.6).delayfb(1.0).note()
 * ```
 */
@KlangScript.Function
fun String.dcap(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).delaycap(amount, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the delay feedback ceiling. Alias for [delaycap].
 *
 * @param amount The ceiling (default 1.0). Omit to reinterpret the pattern's values as the cap.
 * @return A [PatternMapperFn] that sets the delay feedback ceiling.
 *
 * ```KlangScript(Playable)
 * note("c3 ~ ~ ~").apply(dcap(2.0)).delayWet(0.6).delayfb(1.0)
 * ```
 */
@KlangScript.Function
fun dcap(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.delaycap(amount, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the delay feedback ceiling. Alias for [delaycap].
 *
 * @param amount The ceiling (default 1.0). Omit to reinterpret the pattern's values as the cap.
 * @return A new [PatternMapperFn] chaining the ceiling after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 ~ ~ ~").apply(delayWet(0.6).dcap(2.0))
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.dcap(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.delaycap(amount, callInfo) }
