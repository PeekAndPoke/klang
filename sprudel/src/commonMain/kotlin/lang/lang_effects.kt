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

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in SprudelRegistry.
 */
var sprudelLangEffectsInit = false

// -- distort() / dist() -----------------------------------------------------------------------------------------------

private val distortMutation = voiceModifier {
    val str = it?.toString() ?: return@voiceModifier this
    if (":" in str) {
        val parts = str.split(":")
        copy(
            distort = parts.getOrNull(0)?.trim()?.toDoubleOrNull() ?: distort,
            distortShape = parts.getOrNull(1)?.trim()?.takeIf { s -> s.isNotEmpty() } ?: distortShape,
            distortOversample = parts.getOrNull(2)?.trim()?.toIntOrNull() ?: distortOversample,
        )
    } else {
        copy(distort = str.toDoubleOrNull() ?: distort)
    }
}

private fun applyDistort(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val str = args.firstOrNull()?.value?.toString() ?: ""
    return if (":" in str) {
        source._applyControlFromParams(args, distortMutation) { src, ctrl ->
            src.copy(
                distort = ctrl.distort ?: src.distort,
                distortShape = ctrl.distortShape ?: src.distortShape,
                distortOversample = ctrl.distortOversample ?: src.distortOversample,
            )
        }
    } else {
        source._liftOrReinterpretNumericalField(args, distortMutation)
    }
}

/**
 * Applies waveshaper distortion to this pattern.
 *
 * Higher values produce more harmonic saturation and clipping. Works well on synth
 * bass lines and leads; combine with `lpf` to tame harsh high frequencies.
 *
 * Accepts either a single numeric value or a colon-separated `"amount:shape"` string
 * to set both distortion amount and waveshaper shape at once. Available shapes:
 * `soft`, `hard`, `gentle`, `cubic`, `diode`, `fold`, `chebyshev`, `rectify`, `exp`.
 *
 * When [amount] is omitted, the pattern's own numeric values are reinterpreted as distortion amounts.
 *
 * @param amount The distortion amount, or `"amount:shape"` compound string.
 *   Omit to reinterpret the pattern's values as distortion.
 * @param-tool amount SprudelDistortSequenceEditor
 * @param-sub amount amount Distortion drive level (0 = clean, 2 = extreme)
 * @param-sub amount shape Waveshaper curve: soft, hard, gentle, cubic, diode, fold, chebyshev, rectify, exp
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
 * note("c2 eb2 g2").s("sawtooth").distort("0.5:soft")       // warm tanh saturation
 * ```
 *
 * ```KlangScript(Playable)
 * note("c2 eb2 g2").s("sawtooth").distort("0.7:hard")       // aggressive hard clipping
 * ```
 *
 * ```KlangScript(Playable)
 * note("c2 eb2 g2").s("sawtooth").distort("0.4:gentle")     // smooth x/(1+|x|) saturation
 * ```
 *
 * ```KlangScript(Playable)
 * note("c2 eb2 g2").s("sawtooth").distort("0.6:cubic")      // tube-like, 3rd harmonic
 * ```
 *
 * ```KlangScript(Playable)
 * note("c2 eb2 g2").s("sawtooth").distort("0.5:diode")      // asymmetric, even harmonics
 * ```
 *
 * ```KlangScript(Playable)
 * note("c2 eb2 g2").s("sawtooth").distort("0.8:fold")       // sine wavefolding, metallic
 * ```
 *
 * ```KlangScript(Playable)
 * note("c2 eb2 g2").s("sawtooth").distort("0.5:chebyshev")  // T3 polynomial, tape saturation
 * ```
 *
 * ```KlangScript(Playable)
 * note("c2 eb2 g2").s("sawtooth").distort("0.7:rectify")    // full-wave rectification, octave-up
 * ```
 *
 * ```KlangScript(Playable)
 * note("c2 eb2 g2").s("sawtooth").distort("0.6:exp")        // exponential, transistor-style
 * ```
 * @alias dist
 * @category effects
 * @tags distort, dist, distortion, waveshaper, overdrive
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.distort(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyDistort(this, listOfNotNull(amount).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern, then applies waveshaper distortion.
 *
 * When [amount] is omitted, the pattern's own numeric values are reinterpreted as distortion amounts.
 *
 * @param amount The distortion amount. Higher values produce more saturation and clipping.
 *   Omit to reinterpret the pattern's values as distortion.
 * @return A new pattern with distortion applied.
 *
 * ```KlangScript(Playable)
 * "c2 eb2 g2".distort(0.5).note().s("sawtooth")  // moderate distortion on bass notes
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun String.distort(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).distort(amount, callInfo)

/**
 * Returns a [PatternMapperFn] that applies waveshaper distortion.
 *
 * Use the returned mapper as a transform argument or apply it to a pattern via `.apply(...)`.
 * When [amount] is omitted, the pattern's own numeric values are reinterpreted as distortion amounts.
 *
 * @param amount The distortion amount or `"amount:shape"` compound string.
 *   Omit to reinterpret the pattern's values as distortion.
 * @param-tool amount SprudelDistortSequenceEditor
 * @param-sub amount amount Distortion drive level (0 = clean, 2 = extreme)
 * @param-sub amount shape Waveshaper curve: soft, hard, gentle, cubic, diode, fold, chebyshev, rectify, exp
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
@SprudelDsl
@KlangScript.Function
fun distort(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.distort(amount, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that applies waveshaper distortion after the previous mapper.
 *
 * @param amount The distortion amount. Omit to reinterpret the pattern's values as distortion.
 * @return A new [PatternMapperFn] chaining this distortion after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c2 eb2 g2").s("sawtooth").apply(crush(4).distort(0.5))  // bit-crush then distort
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").firstOf(4, room(0.3).distort(0.8))  // room + distortion every 4th cycle
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.distort(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.distort(amount, callInfo) }

/**
 * Alias for [distort]. Applies waveshaper distortion to this pattern.
 *
 * When [amount] is omitted, the pattern's own numeric values are reinterpreted as distortion amounts.
 *
 * @param amount The distortion amount or `"amount:shape"` compound string.
 *   Omit to reinterpret the pattern's values as distortion.
 * @param-tool amount SprudelDistortSequenceEditor
 * @param-sub amount amount Distortion drive level (0 = clean, 2 = extreme)
 * @param-sub amount shape Waveshaper curve: soft, hard, gentle, cubic, diode, fold, chebyshev, rectify, exp
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
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.dist(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.distort(amount, callInfo)

/**
 * Alias for [distort]. Parses this string as a pattern, then applies waveshaper distortion.
 *
 * When [amount] is omitted, the pattern's own numeric values are reinterpreted as distortion amounts.
 *
 * @param amount The distortion amount. Higher values produce more saturation and clipping.
 *   Omit to reinterpret the pattern's values as distortion.
 * @return A new pattern with distortion applied.
 *
 * ```KlangScript(Playable)
 * "c2 eb2 g2".dist(0.5).note().s("sawtooth")  // moderate distortion on bass notes
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun String.dist(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).distort(amount, callInfo)

/**
 * Returns a [PatternMapperFn] that applies waveshaper distortion. Alias for [distort].
 *
 * Use the returned mapper as a transform argument or apply it to a pattern via `.apply(...)`.
 * When [amount] is omitted, the pattern's own numeric values are reinterpreted as distortion amounts.
 *
 * @param amount The distortion amount or `"amount:shape"` compound string.
 *   Omit to reinterpret the pattern's values as distortion.
 * @param-tool amount SprudelDistortSequenceEditor
 * @param-sub amount amount Distortion drive level (0 = clean, 2 = extreme)
 * @param-sub amount shape Waveshaper curve: soft, hard, gentle, cubic, diode, fold, chebyshev, rectify, exp
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
@SprudelDsl
@KlangScript.Function
fun dist(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.distort(amount, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that applies waveshaper distortion (alias for [distort]) after the previous mapper.
 *
 * @param amount The distortion amount. Omit to reinterpret the pattern's values as distortion.
 * @return A new [PatternMapperFn] chaining this distortion after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c2 eb2 g2").s("sawtooth").apply(crush(4).dist(0.5))  // bit-crush then distort
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").firstOf(4, room(0.3).dist(0.8))  // room + distortion every 4th cycle
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.dist(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.distort(amount, callInfo) }

// -- distos() / distortoversampling() ---------------------------------------------------------------------------------

private val distortOversampleMutation = voiceModifier { copy(distortOversample = it?.toString()?.toIntOrNull()) }

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
 * note("c3*4").distort("0.8:exp").distos(4)                 // 4x oversampled exp distortion
 * ```
 * @alias distortOversampling
 * @category effects
 * @tags distos, distort, oversampling, aliasing, quality
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.distos(factor: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyDistortOversample(this, listOfNotNull(factor).asSprudelDslArgs(callInfo))

@SprudelDsl
@KlangScript.Function
fun String.distos(factor: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).distos(factor, callInfo)

@SprudelDsl
@KlangScript.Function
fun distos(factor: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.distos(factor, callInfo) }

@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.distos(factor: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.distos(factor, callInfo) }

@SprudelDsl
@KlangScript.Function
fun SprudelPattern.distortOversampling(factor: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyDistortOversample(this, listOfNotNull(factor).asSprudelDslArgs(callInfo))

@SprudelDsl
@KlangScript.Function
fun String.distortOversampling(factor: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).distortOversampling(factor, callInfo)

@SprudelDsl
@KlangScript.Function
fun distortOversampling(factor: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.distortOversampling(factor, callInfo) }

@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.distortOversampling(factor: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.distortOversampling(factor, callInfo) }

// -- distortshape() / distshape() / dshape() --------------------------------------------------------------------------

private val distortShapeMutation = voiceModifier { shape -> copy(distortShape = shape?.toString()?.lowercase()) }

private fun applyDistortShape(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._applyControlFromParams(args, distortShapeMutation) { src, ctrl ->
        src.copy(distortShape = ctrl.distortShape)
    }
}

/**
 * Sets the distortion waveshaper shape for this pattern.
 *
 * Controls which waveshaping algorithm is used for distortion. Available shapes:
 * `soft`, `hard`, `gentle`, `cubic`, `diode`, `fold`, `chebyshev`, `rectify`, `exp`.
 *
 * @param shape The waveshaper shape name.
 * @param-tool shape SprudelDistortShapeSequenceEditor
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
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.distortshape(shape: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyDistortShape(this, listOf(shape).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets the distortion waveshaper shape.
 *
 * @param shape The waveshaper shape name.
 *
 * ```KlangScript(Playable)
 * "c2 eb2 g2".distortshape("fold").distort(0.5).note().s("sawtooth")
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun String.distortshape(shape: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).distortshape(shape, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the distortion waveshaper shape.
 *
 * @param shape The waveshaper shape name.
 * @param-tool shape SprudelDistortShapeSequenceEditor
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
@SprudelDsl
@KlangScript.Function
fun distortshape(shape: PatternLike, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.distortshape(shape, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the distortion waveshaper shape after the previous mapper.
 *
 * @param shape The waveshaper shape name.
 * @return A new [PatternMapperFn] chaining this distortion shape after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c2 eb2 g2").apply(distort(0.5).distortshape("fold"))   // amount then shape
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.distortshape(shape: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.distortshape(shape, callInfo) }

/**
 * Alias for [distortshape]. Sets the distortion waveshaper shape for this pattern.
 *
 * @param shape The waveshaper shape name.
 * @param-tool shape SprudelDistortShapeSequenceEditor
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
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.distshape(shape: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.distortshape(shape, callInfo)

/**
 * Alias for [distortshape]. Parses this string as a pattern and sets the distortion waveshaper shape.
 *
 * @param shape The waveshaper shape name.
 *
 * ```KlangScript(Playable)
 * "c2 eb2 g2".distshape("fold").distort(0.5).note().s("sawtooth")
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun String.distshape(shape: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).distortshape(shape, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the distortion waveshaper shape. Alias for [distortshape].
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
@SprudelDsl
@KlangScript.Function
fun distshape(shape: PatternLike, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.distortshape(shape, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the distortion waveshaper shape (alias for [distortshape])
 * after the previous mapper.
 *
 * @param shape The waveshaper shape name.
 * @return A new [PatternMapperFn] chaining this distortion shape after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c2 eb2 g2").apply(distort(0.5).distshape("fold"))   // amount then shape
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.distshape(shape: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.distortshape(shape, callInfo) }

/**
 * Alias for [distortshape]. Sets the distortion waveshaper shape for this pattern.
 *
 * @param shape The waveshaper shape name.
 * @param-tool shape SprudelDistortShapeSequenceEditor
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
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.dshape(shape: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.distortshape(shape, callInfo)

/**
 * Alias for [distortshape]. Parses this string as a pattern and sets the distortion waveshaper shape.
 *
 * @param shape The waveshaper shape name.
 *
 * ```KlangScript(Playable)
 * "c2 eb2 g2".dshape("fold").distort(0.5).note().s("sawtooth")
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun String.dshape(shape: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).distortshape(shape, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the distortion waveshaper shape. Alias for [distortshape].
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
@SprudelDsl
@KlangScript.Function
fun dshape(shape: PatternLike, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.distortshape(shape, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the distortion waveshaper shape (alias for [distortshape])
 * after the previous mapper.
 *
 * @param shape The waveshaper shape name.
 * @return A new [PatternMapperFn] chaining this distortion shape after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c2 eb2 g2").apply(distort(0.5).dshape("fold"))   // amount then shape
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.dshape(shape: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.distortshape(shape, callInfo) }

// -- Named distortion shapes ------------------------------------------------------------------------------------------
// -- crush() ----------------------------------------------------------------------------------------------------------

private val crushMutation = voiceModifier { copy(crush = it?.asDoubleOrNull()) }

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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.crush(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.crush(amount, callInfo) }

// -- crushos() / crushoversampling() ----------------------------------------------------------------------------------

private val crushOversampleMutation = voiceModifier { copy(crushOversample = it?.toString()?.toIntOrNull()) }

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
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.crushos(factor: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyCrushOversample(this, listOfNotNull(factor).asSprudelDslArgs(callInfo))

@SprudelDsl
@KlangScript.Function
fun String.crushos(factor: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).crushos(factor, callInfo)

@SprudelDsl
@KlangScript.Function
fun crushos(factor: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.crushos(factor, callInfo) }

@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.crushos(factor: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.crushos(factor, callInfo) }

@SprudelDsl
@KlangScript.Function
fun SprudelPattern.crushOversampling(factor: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyCrushOversample(this, listOfNotNull(factor).asSprudelDslArgs(callInfo))

@SprudelDsl
@KlangScript.Function
fun String.crushOversampling(factor: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).crushOversampling(factor, callInfo)

@SprudelDsl
@KlangScript.Function
fun crushOversampling(factor: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.crushOversampling(factor, callInfo) }

@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.crushOversampling(factor: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.crushOversampling(factor, callInfo) }

// -- coarse() ---------------------------------------------------------------------------------------------------------

private val coarseMutation = voiceModifier { copy(coarse = it?.asDoubleOrNull()) }

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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.coarse(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.coarse(amount, callInfo) }

// -- coarseos() / coarseoversampling() --------------------------------------------------------------------------------

private val coarseOversampleMutation = voiceModifier { copy(coarseOversample = it?.toString()?.toIntOrNull()) }

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
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.coarseos(factor: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyCoarseOversample(this, listOfNotNull(factor).asSprudelDslArgs(callInfo))

@SprudelDsl
@KlangScript.Function
fun String.coarseos(factor: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).coarseos(factor, callInfo)

@SprudelDsl
@KlangScript.Function
fun coarseos(factor: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.coarseos(factor, callInfo) }

@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.coarseos(factor: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.coarseos(factor, callInfo) }

@SprudelDsl
@KlangScript.Function
fun SprudelPattern.coarseOversampling(factor: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyCoarseOversample(this, listOfNotNull(factor).asSprudelDslArgs(callInfo))

@SprudelDsl
@KlangScript.Function
fun String.coarseOversampling(factor: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).coarseOversampling(factor, callInfo)

@SprudelDsl
@KlangScript.Function
fun coarseOversampling(factor: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.coarseOversampling(factor, callInfo) }

@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.coarseOversampling(factor: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.coarseOversampling(factor, callInfo) }

// -- room() -----------------------------------------------------------------------------------------------------------

private val roomMutation = voiceModifier {
    val parts = it?.toString()?.split(":")
        ?.map { d -> d.trim().toDoubleOrNull() } ?: emptyList()

    copy(
        room = parts.getOrNull(0) ?: room,
        roomSize = parts.getOrNull(1) ?: roomSize,
        roomFade = parts.getOrNull(2) ?: roomFade,
        roomLp = parts.getOrNull(3) ?: roomLp,
        roomDim = parts.getOrNull(4) ?: roomDim,
    )
}

private fun applyRoom(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    // No args: reinterpret pattern's own values as room mix (backward compat)
    if (args.isEmpty()) {
        return source.reinterpretVoice {
            it.copy(room = it.value?.asDouble)
        }
    }

    return source._applyControlFromParams(args, roomMutation) { src, ctrl ->
        src.copy(
            room = ctrl.room ?: src.room,
            roomSize = ctrl.roomSize ?: src.roomSize,
            roomFade = ctrl.roomFade ?: src.roomFade,
            roomLp = ctrl.roomLp ?: src.roomLp,
            roomDim = ctrl.roomDim ?: src.roomDim,
        )
    }
}

/**
 * Sets the reverb wet/dry mix for this pattern (0 = dry, 1 = full wet).
 *
 * Use with `roomsize` to control the reverb tail length, and `orbit` to send
 * multiple patterns to separate reverb buses.
 * When [amount] is omitted, the pattern's own numeric values are reinterpreted as room mix.
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").clip(0.5).s("sine").room(0.5)   // 50% reverb
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").clip(0.5).room("<0 0.3 0.6 0.9>").roomsize(4)   // increasing wet mix
 * ```
 *
 * ```KlangScript(Playable)
 * seq("0 0.5 1.0").room()   // reinterpret values as room mix
 * ```
 *
 * @param amount The wet/dry mix (0–1). Omit to reinterpret the pattern's values as room mix.
 * @param-tool amount SprudelReverbSequenceEditor
 * @return A new pattern with reverb wet/dry mix applied.
 * @category effects
 * @tags room, reverb, wet, mix, space
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.room(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyRoom(this, listOfNotNull(amount).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets the reverb wet/dry mix.
 *
 * When [amount] is omitted, the string's numeric values are reinterpreted as room mix.
 *
 * @param amount The wet/dry mix (0–1). Omit to reinterpret the pattern's values as room mix.
 * @return A new pattern with reverb wet/dry mix applied.
 *
 * ```KlangScript(Playable)
 * "c3 e3 g3".room(0.5).note().clip(0.5)    // 50% reverb on bass notes
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun String.room(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).room(amount, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the reverb wet/dry mix.
 *
 * Use the returned mapper as a transform argument or apply it to a pattern via `.apply(...)`.
 * When [amount] is omitted, the pattern's own numeric values are reinterpreted as room mix.
 *
 * @param amount The wet/dry mix (0–1). Omit to reinterpret the pattern's values as room mix.
 * @return A [PatternMapperFn] that sets the reverb wet/dry mix.
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").apply(room(0.5)).clip(0.5)     // 50% reverb via mapper
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, room(0.9)).clip(0.5)      // heavy reverb every 4th cycle
 * ```
 *
 * @category effects
 * @tags room, reverb, wet, mix, space
 */
@SprudelDsl
@KlangScript.Function
fun room(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.room(amount, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the reverb wet/dry mix after the previous mapper.
 *
 * @param amount The wet/dry mix (0–1). Omit to reinterpret the pattern's values as room mix.
 * @return A new [PatternMapperFn] chaining this reverb mix after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(roomsize(4).room(0.5))     // set room size then wet mix
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, coarse(4).room(0.8))     // lo-fi with heavy reverb every 4th cycle
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.room(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.room(amount, callInfo) }

// -- roomsize() / rsize() / sz() / size() -----------------------------------------------------------------------------

private val roomSizeMutation = voiceModifier { copy(roomSize = it?.asDoubleOrNull()) }

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
 * note("c3 e3").clip(0.5).room(0.5).roomsize(4)   // long reverb tail
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
 * @param-tool amount SprudelRoomSizeSequenceEditor
 * @alias rsize, sz, size
 * @category effects
 * @tags roomsize, rsize, sz, size, reverb, room, tail
 */
@SprudelDsl
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
 * "c3 e3".roomsize(4).room(0.5).note().clip(0.5)    // long reverb tail on bass notes
 * ```
 */
@SprudelDsl
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
@SprudelDsl
@KlangScript.Function
fun roomsize(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.roomsize(amount, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the reverb room size after the previous mapper.
 *
 * @param amount The room size. Omit to reinterpret the pattern's values as room size.
 * @return A new [PatternMapperFn] chaining this room size after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(room(0.5).roomsize(4))   // set wet mix then room size
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, room(0.8).roomsize(8))  // big reverb every 4th cycle
 * ```
 */
@SprudelDsl
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
 * note("c3 e3").clip(0.5).room(0.5).rsize(4)   // long reverb tail
 * ```
 *
 * @param-tool amount SprudelRoomSizeSequenceEditor
 * @alias roomsize, sz, size
 * @category effects
 * @tags rsize, roomsize, sz, size, reverb, room, tail
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.rsize(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.roomsize(amount, callInfo)

/**
 * Alias for [roomsize]. Parses this string as a pattern and sets the reverb room size.
 *
 * @param amount The room size. Omit to reinterpret the pattern's values as room size.
 *
 * ```KlangScript(Playable)
 * "c3 e3".rsize(4).room(0.5).note().clip(0.5)    // long reverb tail on bass notes
 * ```
 */
@SprudelDsl
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
@SprudelDsl
@KlangScript.Function
fun rsize(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.roomsize(amount, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the reverb room size (alias for roomsize) after the previous mapper.
 *
 * @param amount The room size. Omit to reinterpret the pattern's values as room size.
 * @return A new [PatternMapperFn] chaining this room size after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(room(0.5).rsize(4))   // set wet mix then room size
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, room(0.8).rsize(8))  // big reverb every 4th cycle
 * ```
 */
@SprudelDsl
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
 * note("c3 e3").clip(0.5).room(0.5).sz(4)   // long reverb tail
 * ```
 *
 * @param-tool amount SprudelRoomSizeSequenceEditor
 * @alias roomsize, rsize, size
 * @category effects
 * @tags sz, roomsize, rsize, size, reverb, room, tail
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.sz(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.roomsize(amount, callInfo)

/**
 * Alias for [roomsize]. Parses this string as a pattern and sets the reverb room size.
 *
 * @param amount The room size. Omit to reinterpret the pattern's values as room size.
 *
 * ```KlangScript(Playable)
 * "c3 e3".sz(4).room(0.5).note().clip(0.5)    // long reverb tail on bass notes
 * ```
 */
@SprudelDsl
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
@SprudelDsl
@KlangScript.Function
fun sz(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.roomsize(amount, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the reverb room size (alias for roomsize) after the previous mapper.
 *
 * @param amount The room size. Omit to reinterpret the pattern's values as room size.
 * @return A new [PatternMapperFn] chaining this room size after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(room(0.5).sz(4))   // set wet mix then room size
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, room(0.8).sz(8))  // big reverb every 4th cycle
 * ```
 */
@SprudelDsl
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
 * note("c3 e3").clip(0.5).room(0.5).size(4)   // long reverb tail
 * ```
 *
 * @alias roomsize, rsize, sz
 * @category effects
 * @tags size, roomsize, rsize, sz, reverb, room, tail
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.size(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.roomsize(amount, callInfo)

/**
 * Alias for [roomsize]. Parses this string as a pattern and sets the reverb room size.
 *
 * @param amount The room size. Omit to reinterpret the pattern's values as room size.
 *
 * ```KlangScript(Playable)
 * "c3 e3".size(4).room(0.5).note().clip(0.5)    // long reverb tail on bass notes
 * ```
 */
@SprudelDsl
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
@SprudelDsl
@KlangScript.Function
fun size(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.roomsize(amount, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the reverb room size (alias for roomsize) after the previous mapper.
 *
 * @param amount The room size. Omit to reinterpret the pattern's values as room size.
 * @return A new [PatternMapperFn] chaining this room size after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(room(0.5).size(4))   // set wet mix then room size
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, room(0.8).size(8))  // big reverb every 4th cycle
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.size(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.roomsize(amount, callInfo) }

// -- roomfade() / rfade() ---------------------------------------------------------------------------------------------

private val roomFadeMutation = voiceModifier { copy(roomFade = it?.asDoubleOrNull()) }

private fun applyRoomFade(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, roomFadeMutation)
}

/**
 * Sets the reverb fade time in seconds for this pattern.
 *
 * Controls how long the reverb tail takes to fade out. Longer values create more sustained
 * tails that persist after the dry signal ends.
 * When [time] is omitted, the pattern's own numeric values are reinterpreted as fade time.
 *
 * @param time The fade time in seconds. Omit to reinterpret the pattern's values as fade time.
 * @return A new pattern with the reverb fade time applied.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").room(0.6).roomfade(2.0)    // 2-second reverb fade
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").roomfade("<0.5 1 2 4>")     // increasing fade time each beat
 * ```
 *
 * ```KlangScript(Playable)
 * seq("0.5 1 2 4").roomfade()              // reinterpret values as fade time
 * ```
 *
 * @alias rfade
 * @category effects
 * @tags roomfade, rfade, reverb, fade, tail
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.roomfade(time: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyRoomFade(this, listOfNotNull(time).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets the reverb fade time.
 *
 * When [time] is omitted, the string's numeric values are reinterpreted as fade time.
 *
 * @param time The fade time in seconds. Omit to reinterpret the pattern's values as fade time.
 *
 * ```KlangScript(Playable)
 * "c3 e3".roomfade(2.0).room(0.6).note()   // 2-second fade on string pattern
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun String.roomfade(time: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).roomfade(time, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the reverb fade time.
 *
 * Use the returned mapper as a transform argument or apply it via `.apply(...)`.
 * When [time] is omitted, the pattern's own numeric values are reinterpreted as fade time.
 *
 * @param time The fade time in seconds. Omit to reinterpret the pattern's values as fade time.
 * @return A [PatternMapperFn] that sets the reverb fade time.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(roomfade(2.0)).room(0.6)   // 2-second fade via mapper
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, roomfade(4.0))            // long fade every 4th cycle
 * ```
 *
 * @alias rfade
 * @category effects
 * @tags roomfade, rfade, reverb, fade, tail
 */
@SprudelDsl
@KlangScript.Function
fun roomfade(time: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.roomfade(time, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the reverb fade time after the previous mapper.
 *
 * @param time The fade time in seconds. Omit to reinterpret the pattern's values as fade time.
 * @return A new [PatternMapperFn] chaining this fade time after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(room(0.6).roomfade(2.0))   // wet mix then fade time
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, room(0.8).roomfade(4.0))  // big reverb every 4th cycle
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.roomfade(time: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.roomfade(time, callInfo) }

/**
 * Alias for [roomfade]. Sets the reverb fade time in seconds for this pattern.
 *
 * When [time] is omitted, the pattern's own numeric values are reinterpreted as fade time.
 *
 * @param time The fade time in seconds. Omit to reinterpret the pattern's values as fade time.
 * @return A new pattern with the reverb fade time applied.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").room(0.6).rfade(2.0)    // 2-second reverb fade
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").rfade("<0.5 1 2 4>")     // increasing fade time each beat
 * ```
 *
 * @alias roomfade
 * @category effects
 * @tags rfade, roomfade, reverb, fade, tail
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.rfade(time: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.roomfade(time, callInfo)

/**
 * Alias for [roomfade]. Parses this string as a pattern and sets the reverb fade time.
 *
 * When [time] is omitted, the string's numeric values are reinterpreted as fade time.
 *
 * @param time The fade time in seconds. Omit to reinterpret the pattern's values as fade time.
 *
 * ```KlangScript(Playable)
 * "c3 e3".rfade(2.0).room(0.6).note()   // 2-second fade on string pattern
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun String.rfade(time: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).roomfade(time, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the reverb fade time. Alias for [roomfade].
 *
 * @param time The fade time in seconds.
 * @return A [PatternMapperFn] that sets the reverb fade time.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(rfade(2.0)).room(0.6)   // 2-second fade via mapper
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, rfade(4.0))            // long fade every 4th cycle
 * ```
 *
 * @alias roomfade
 * @category effects
 * @tags rfade, roomfade, reverb, fade, tail
 */
@SprudelDsl
@KlangScript.Function
fun rfade(time: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.roomfade(time, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the reverb fade time (alias for roomfade) after the previous mapper.
 *
 * @param time The fade time in seconds. Omit to reinterpret the pattern's values as fade time.
 * @return A new [PatternMapperFn] chaining this fade time after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(room(0.6).rfade(2.0))   // wet mix then fade time
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, room(0.8).rfade(4.0))  // big reverb every 4th cycle
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.rfade(time: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.roomfade(time, callInfo) }

// -- roomlp() / rlp() -------------------------------------------------------------------------------------------------

private val roomLpMutation = voiceModifier { copy(roomLp = it?.asDoubleOrNull()) }

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
 * note("c3 e3").room(0.6).roomlp(4000)           // dark reverb tail
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
@SprudelDsl
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
 * "c3 e3".roomlp(4000).room(0.6).note()   // dark reverb on string pattern
 * ```
 */
@SprudelDsl
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
 * note("c3 e3").apply(roomlp(4000)).room(0.6)   // dark reverb via mapper
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
@SprudelDsl
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
 * note("c3 e3").apply(room(0.6).roomlp(4000))   // wet mix then dark reverb
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, room(0.8).roomlp(1000))  // very dark reverb every 4th cycle
 * ```
 */
@SprudelDsl
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
 * note("c3 e3").room(0.6).rlp(4000)           // dark reverb tail
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
@SprudelDsl
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
 * "c3 e3".rlp(4000).room(0.6).note()   // dark reverb on string pattern
 * ```
 */
@SprudelDsl
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
 * note("c3 e3").apply(rlp(4000)).room(0.6)   // dark reverb via mapper
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
@SprudelDsl
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
 * note("c3 e3").apply(room(0.6).rlp(4000))   // wet mix then dark reverb
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, room(0.8).rlp(1000))  // very dark reverb every 4th cycle
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.rlp(freq: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.roomlp(freq, callInfo) }

// -- roomdim() / rdim() -----------------------------------------------------------------------------------------------

private val roomDimMutation = voiceModifier { copy(roomDim = it?.asDoubleOrNull()) }

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
 * note("c3 e3").room(0.6).roomdim(500)   // very dark, dim reverb
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
@SprudelDsl
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
 * "c3 e3".roomdim(500).room(0.6).note()   // very dark reverb on string pattern
 * ```
 */
@SprudelDsl
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
 * note("c3 e3").apply(roomdim(500)).room(0.6)   // very dark reverb via mapper
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
@SprudelDsl
@KlangScript.Function
fun roomdim(freq: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.roomdim(freq, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the reverb lowpass frequency at -60 dB after the previous mapper.
 *
 * @param freq The -60 dB lowpass frequency in Hz. Omit to reinterpret the pattern's values as frequency.
 * @return A new [PatternMapperFn] chaining this frequency after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(room(0.6).roomdim(500))   // wet mix then dim frequency
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, room(0.8).roomdim(200))  // very dim reverb every 4th cycle
 * ```
 */
@SprudelDsl
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
 * note("c3 e3").room(0.6).rdim(500)   // very dark, dim reverb
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
@SprudelDsl
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
 * "c3 e3".rdim(500).room(0.6).note()   // very dark reverb on string pattern
 * ```
 */
@SprudelDsl
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
 * note("c3 e3").apply(rdim(500)).room(0.6)   // very dark reverb via mapper
 * ```
 *
 * @alias roomdim
 * @category effects
 * @tags rdim, roomdim, reverb, lowpass, darkness
 */
@SprudelDsl
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
 * note("c3 e3").apply(room(0.6).rdim(500))   // wet mix then dim frequency
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, room(0.8).rdim(200))  // very dim reverb every 4th cycle
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.rdim(freq: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.roomdim(freq, callInfo) }

// -- iresponse() / ir() -----------------------------------------------------------------------------------------------

private val iResponseMutation = voiceModifier { response -> copy(iResponse = response?.toString()) }

private fun applyIResponse(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._applyControlFromParams(args, iResponseMutation) { src, ctrl ->
        src.copy(iResponse = ctrl.iResponse)
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
@KlangScript.Function
fun iresponse(name: PatternLike, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.iresponse(name, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the impulse response after the previous mapper.
 *
 * @param name The name of the impulse response sample.
 * @return A new [PatternMapperFn] chaining this impulse response after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(room(0.5).iresponse("church"))   // room then IR reverb
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, room(0.8).iresponse("hall"))   // hall IR every 4th cycle
 * ```
 */
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
@KlangScript.Function
fun ir(name: PatternLike, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.iresponse(name, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the impulse response (alias for iresponse) after the previous mapper.
 *
 * @param name The name of the impulse response sample.
 * @return A new [PatternMapperFn] chaining this impulse response after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(room(0.5).ir("church"))   // room then IR reverb
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, room(0.8).ir("hall"))   // hall IR every 4th cycle
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.ir(name: PatternLike, callInfo: CallInfo? = null): PatternMapperFn = this.chain { p -> p.iresponse(name, callInfo) }

// -- delay() ----------------------------------------------------------------------------------------------------------

// Supports both single value (wet/dry mix) and combined "wet:time:feedback" format.
private val delayMutation = voiceModifier {
    val str = it?.toString() ?: return@voiceModifier this
    if (":" in str) {
        val parts = str.split(":").map { d -> d.trim().toDoubleOrNull() }
        copy(
            delay = parts.getOrNull(0) ?: delay,
            delayTime = parts.getOrNull(1) ?: delayTime,
            delayFeedback = parts.getOrNull(2) ?: delayFeedback,
        )
    } else {
        copy(delay = str.toDoubleOrNull() ?: delay)
    }
}

private fun applyDelay(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val str = args.firstOrNull()?.value?.toString() ?: ""
    return if (":" in str) {
        source._applyControlFromParams(args, delayMutation) { src, ctrl ->
            src.copy(
                delay = ctrl.delay ?: src.delay,
                delayTime = ctrl.delayTime ?: src.delayTime,
                delayFeedback = ctrl.delayFeedback ?: src.delayFeedback,
            )
        }
    } else {
        source._liftOrReinterpretNumericalField(args, delayMutation)
    }
}

/**
 * Sets the delay effect for this pattern.
 *
 * Accepts either a single value (wet/dry mix 0–1) or a colon-separated string
 * `"wet:time:feedback"` to set all delay parameters at once.
 * Trailing fields can be omitted.
 *
 * - **wet**: wet/dry mix (0–1)
 * - **time**: delay interval in seconds
 * - **feedback**: feedback amount (0–1), higher = more repeats
 *
 * When [amount] is omitted, the pattern's own numeric values are reinterpreted as delay mix.
 *
 * @param amount The delay parameters: a single value (wet/dry mix) or `"wet:time:feedback"`.
 * @return A new pattern with the delay applied.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").delay(0.4)                         // 40% delay mix
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").delay("0.5:0.25:0.6")               // wet=0.5, time=0.25s, feedback=0.6
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").delay("<0.3:0.125 0.6:0.25:0.8>")   // alternating delay per cycle
 * ```
 *
 * @param-tool amount SprudelDelaySequenceEditor
 * @param-sub amount wet Wet/dry mix (0 = fully dry, 1 = fully wet)
 * @param-sub amount time Delay interval in seconds
 * @param-sub amount feedback Feedback amount (0–1), higher values produce more repeats
 * @category effects
 * @tags delay, echo, wet, mix, delaytime, delayfeedback
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.delay(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyDelay(this, listOfNotNull(amount).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets the delay wet/dry mix.
 *
 * When [amount] is omitted, the string's numeric values are reinterpreted as delay mix.
 *
 * @param amount The wet/dry mix (0–1). Omit to reinterpret the pattern's values as delay mix.
 *
 * ```KlangScript(Playable)
 * "c3 e3".delay(0.4).note()   // 40% delay mix on string pattern
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun String.delay(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).delay(amount, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the delay wet/dry mix.
 *
 * Use the returned mapper as a transform argument or apply it via `.apply(...)`.
 * When [amount] is omitted, the pattern's own numeric values are reinterpreted as delay mix.
 *
 * @param amount The wet/dry mix (0–1). Omit to reinterpret the pattern's values as delay mix.
 * @return A [PatternMapperFn] that sets the delay mix.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(delay(0.4))   // 40% delay mix via mapper
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, delay(0.8))   // heavy delay every 4th cycle
 * ```
 *
 * @category effects
 * @tags delay, echo, wet, mix
 */
@SprudelDsl
@KlangScript.Function
fun delay(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.delay(amount, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the delay wet/dry mix after the previous mapper.
 *
 * @param amount The wet/dry mix (0–1). Omit to reinterpret the pattern's values as delay mix.
 * @return A new [PatternMapperFn] chaining this delay mix after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(delaytime(0.25).delay(0.5))   // set delay time then mix
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, delaytime(0.25).delay(0.8))   // delay every 4th cycle
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.delay(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.delay(amount, callInfo) }

// -- delaytime() ------------------------------------------------------------------------------------------------------

private val delayTimeMutation = voiceModifier { copy(delayTime = it?.asDoubleOrNull()) }

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
 * note("c3").delay(0.5).delaytime(0.375)   // dotted-eighth delay
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
 * @param-tool time SprudelDelayTimeSequenceEditor
 * @category effects
 * @tags delaytime, delay, echo, time, interval
 */
@SprudelDsl
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
 * "c3 e3".delaytime(0.25).delay(0.5).note()   // quarter-note delay on string pattern
 * ```
 */
@SprudelDsl
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
@SprudelDsl
@KlangScript.Function
fun delaytime(time: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.delaytime(time, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the delay time after the previous mapper.
 *
 * @param time The delay interval in seconds. Omit to reinterpret the pattern's values as delay time.
 * @return A new [PatternMapperFn] chaining this delay time after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(delay(0.5).delaytime(0.25))   // delay mix then time
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, delay(0.8).delaytime(0.125))   // delay every 4th cycle
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.delaytime(time: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.delaytime(time, callInfo) }

// -- delayfeedback() / delayfb() / dfb() ------------------------------------------------------------------------------

private val delayFeedbackMutation = voiceModifier { copy(delayFeedback = it?.asDoubleOrNull()) }

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
 * note("c3").delay(0.4).delaytime(0.25).delayfeedback(0.6)   // 3-4 echoes
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
 * @param-tool amount SprudelDelayFeedbackSequenceEditor
 * @alias delayfb, dfb
 * @category effects
 * @tags delayfeedback, delayfb, dfb, delay, echo, feedback, repeats
 */
@SprudelDsl
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
 * "c3 e3".delayfeedback(0.6).delay(0.4).note()   // echoing string pattern
 * ```
 */
@SprudelDsl
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
@SprudelDsl
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
 * note("c3 e3").apply(delay(0.5).delayfeedback(0.6))   // mix then feedback
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, delay(0.8).delayfeedback(0.9))   // echoing delay every 4th cycle
 * ```
 */
@SprudelDsl
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
 * note("c3").delay(0.4).delaytime(0.25).delayfb(0.6)   // 3-4 echoes
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
 * @param-tool amount SprudelDelayFeedbackSequenceEditor
 * @alias delayfeedback, dfb
 * @category effects
 * @tags delayfb, delayfeedback, dfb, delay, echo, feedback, repeats
 */
@SprudelDsl
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
 * "c3 e3".delayfb(0.6).delay(0.4).note()   // echoing string pattern
 * ```
 */
@SprudelDsl
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
@SprudelDsl
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
 * note("c3 e3").apply(delay(0.5).delayfb(0.6))   // mix then feedback
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, delay(0.8).delayfb(0.9))   // echoing delay every 4th cycle
 * ```
 */
@SprudelDsl
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
 * note("c3").delay(0.4).delaytime(0.25).dfb(0.6)   // 3-4 echoes
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
@SprudelDsl
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
 * "c3 e3".dfb(0.6).delay(0.4).note()   // echoing string pattern
 * ```
 */
@SprudelDsl
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
@SprudelDsl
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
 * note("c3 e3").apply(delay(0.5).dfb(0.6))   // mix then feedback
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, delay(0.8).dfb(0.9))   // echoing delay every 4th cycle
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.dfb(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.delayfeedback(amount, callInfo) }

// -- phaser() / ph() --------------------------------------------------------------------------------------------------

private val phaserMutation = voiceModifier {
    val str = it?.toString() ?: return@voiceModifier this
    if (":" in str) {
        val parts = str.split(":").map { d -> d.trim().toDoubleOrNull() }
        copy(
            phaserRate = parts.getOrNull(0) ?: phaserRate,
            phaserDepth = parts.getOrNull(1) ?: phaserDepth,
            phaserCenter = parts.getOrNull(2) ?: phaserCenter,
            phaserSweep = parts.getOrNull(3) ?: phaserSweep,
        )
    } else {
        copy(phaserRate = str.toDoubleOrNull() ?: phaserRate)
    }
}

private fun applyPhaser(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val str = args.firstOrNull()?.value?.toString() ?: ""
    return if (":" in str) {
        source._applyControlFromParams(args, phaserMutation) { src, ctrl ->
            src.copy(
                phaserRate = ctrl.phaserRate ?: src.phaserRate,
                phaserDepth = ctrl.phaserDepth ?: src.phaserDepth,
                phaserCenter = ctrl.phaserCenter ?: src.phaserCenter,
                phaserSweep = ctrl.phaserSweep ?: src.phaserSweep,
            )
        }
    } else {
        source._liftOrReinterpretNumericalField(args, phaserMutation)
    }
}

/**
 * Sets the phaser LFO rate in Hz for this pattern.
 *
 * A phaser creates a sweeping comb-filter effect by modulating a series of all-pass filters.
 * Higher rate values produce faster sweeping. Use with `phaserdepth` and `phasercenter`.
 *
 * Accepts either a single numeric value (rate) or a colon-separated `"rate:depth:center:sweep"`
 * string to set all phaser parameters at once. Trailing fields can be omitted.
 *
 * When [rate] is omitted, the pattern's own numeric values are reinterpreted as the phaser rate.
 *
 * @param rate The phaser LFO rate in Hz, or `"rate:depth:center:sweep"` compound string.
 *   Omit to reinterpret the pattern's values as phaser rate.
 * @param-tool rate SprudelPhaserSequenceEditor
 * @param-sub rate rate LFO speed in Hz controlling the sweep rate
 * @param-sub rate depth Modulation depth (0–1), how far the filters sweep
 * @param-sub rate center Center frequency in Hz for the all-pass filter bank
 * @param-sub rate sweep Sweep range in Hz around the center frequency
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
 * note("c3 e3 g3").s("sawtooth").phaser("0.5:0.8:500:1000")   // full compound phaser
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").s("sawtooth").phaser("2.0:0.6")   // rate + depth only
 * ```
 *
 * @alias ph
 * @category effects
 * @tags phaser, ph, phase, sweep, modulation
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.phaser(rate: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyPhaser(this, listOfNotNull(rate).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets the phaser LFO rate.
 *
 * When [rate] is omitted, the string's numeric values are reinterpreted as the phaser rate.
 *
 * @param rate The phaser LFO rate in Hz. Omit to reinterpret the pattern's values as phaser rate.
 *
 * ```KlangScript(Playable)
 * "c3 e3 g3".phaser(0.5).note().s("sawtooth")   // slow phaser on string pattern
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun String.phaser(rate: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).phaser(rate, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the phaser LFO rate.
 *
 * Use the returned mapper as a transform argument or apply it via `.apply(...)`.
 * When [rate] is omitted, the pattern's own numeric values are reinterpreted as the phaser rate.
 *
 * @param rate The phaser LFO rate in Hz, or `"rate:depth:center:sweep"` compound string.
 *   Omit to reinterpret the pattern's values as phaser rate.
 * @param-tool rate SprudelPhaserSequenceEditor
 * @param-sub rate rate LFO speed in Hz controlling the sweep rate
 * @param-sub rate depth Modulation depth (0–1), how far the filters sweep
 * @param-sub rate center Center frequency in Hz for the all-pass filter bank
 * @param-sub rate sweep Sweep range in Hz around the center frequency
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
@SprudelDsl
@KlangScript.Function
fun phaser(rate: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.phaser(rate, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the phaser LFO rate after the previous mapper.
 *
 * @param rate The phaser LFO rate in Hz. Omit to reinterpret the pattern's values as phaser rate.
 * @return A new [PatternMapperFn] chaining this phaser rate after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(phaserdepth(0.8).phaser(0.5))   // depth then rate
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, phaserdepth(1.0).phaser(4.0))   // full-depth fast phaser
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.phaser(rate: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.phaser(rate, callInfo) }

/**
 * Alias for [phaser]. Sets the phaser LFO rate in Hz for this pattern.
 *
 * When [rate] is omitted, the pattern's own numeric values are reinterpreted as the phaser rate.
 *
 * @param rate The phaser LFO rate in Hz, or `"rate:depth:center:sweep"` compound string.
 *   Omit to reinterpret the pattern's values as phaser rate.
 * @param-tool rate SprudelPhaserSequenceEditor
 * @param-sub rate rate LFO speed in Hz controlling the sweep rate
 * @param-sub rate depth Modulation depth (0–1), how far the filters sweep
 * @param-sub rate center Center frequency in Hz for the all-pass filter bank
 * @param-sub rate sweep Sweep range in Hz around the center frequency
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
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.ph(rate: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.phaser(rate, callInfo)

/**
 * Alias for [phaser]. Parses this string as a pattern and sets the phaser LFO rate.
 *
 * When [rate] is omitted, the string's numeric values are reinterpreted as the phaser rate.
 *
 * @param rate The phaser LFO rate in Hz. Omit to reinterpret the pattern's values as phaser rate.
 *
 * ```KlangScript(Playable)
 * "c3 e3 g3".ph(0.5).note().s("sawtooth")   // slow phaser on string pattern
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun String.ph(rate: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).phaser(rate, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the phaser LFO rate. Alias for [phaser].
 *
 * @param rate The phaser LFO rate in Hz, or `"rate:depth:center:sweep"` compound string.
 *   Omit to reinterpret the pattern's values as phaser rate.
 * @param-tool rate SprudelPhaserSequenceEditor
 * @param-sub rate rate LFO speed in Hz controlling the sweep rate
 * @param-sub rate depth Modulation depth (0–1), how far the filters sweep
 * @param-sub rate center Center frequency in Hz for the all-pass filter bank
 * @param-sub rate sweep Sweep range in Hz around the center frequency
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
@SprudelDsl
@KlangScript.Function
fun ph(rate: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.phaser(rate, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the phaser LFO rate (alias for phaser) after the previous mapper.
 *
 * @param rate The phaser LFO rate in Hz. Omit to reinterpret the pattern's values as phaser rate.
 * @return A new [PatternMapperFn] chaining this phaser rate after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(phaserdepth(0.8).ph(0.5))   // depth then rate
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, phaserdepth(1.0).ph(4.0))   // full-depth fast phaser
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.ph(rate: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.phaser(rate, callInfo) }

// -- phaserdepth() / phd() / phasdp() ---------------------------------------------------------------------------------

private val phaserDepthMutation = voiceModifier { copy(phaserDepth = it?.asDoubleOrNull()) }

private fun applyPhaserDepth(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, phaserDepthMutation)
}

/**
 * Sets the phaser depth (modulation intensity) for this pattern.
 *
 * Controls how strongly the phaser sweeps across frequencies. Higher values produce
 * a more pronounced notch-filter sweep effect.
 * When [amount] is omitted, the pattern's own numeric values are reinterpreted as phaser depth.
 *
 * @param amount The modulation intensity (0–1). Omit to reinterpret the pattern's values as phaser depth.
 * @return A new pattern with the phaser depth applied.
 *
 * ```KlangScript(Playable)
 * note("c3*4").phaser(0.5).phaserdepth(0.8)   // deep phaser sweep
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").phaserdepth("<0.2 0.5 0.8 1.0>")   // increasing phaser depth
 * ```
 *
 * ```KlangScript(Playable)
 * seq("0.2 0.5 0.8 1.0").phaserdepth()   // reinterpret values as phaser depth
 * ```
 *
 * @alias phd, phasdp
 * @category effects
 * @tags phaserdepth, phd, phasdp, phaser, depth, modulation
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.phaserdepth(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyPhaserDepth(this, listOfNotNull(amount).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets the phaser depth.
 *
 * When [amount] is omitted, the string's numeric values are reinterpreted as phaser depth.
 *
 * @param amount The modulation intensity (0–1). Omit to reinterpret the pattern's values as phaser depth.
 *
 * ```KlangScript(Playable)
 * "c3*4".phaserdepth(0.8).phaser(0.5).note()   // deep phaser on string pattern
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun String.phaserdepth(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).phaserdepth(amount, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the phaser depth.
 *
 * Use the returned mapper as a transform argument or apply it via `.apply(...)`.
 * When [amount] is omitted, the pattern's own numeric values are reinterpreted as phaser depth.
 *
 * @param amount The modulation intensity (0–1). Omit to reinterpret the pattern's values as phaser depth.
 * @return A [PatternMapperFn] that sets the phaser depth.
 *
 * ```KlangScript(Playable)
 * note("c3*4").apply(phaserdepth(0.8))   // deep phaser via mapper
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, phaserdepth(1.0))   // max depth every 4th cycle
 * ```
 *
 * @alias phd, phasdp
 * @category effects
 * @tags phaserdepth, phd, phasdp, phaser, depth, modulation
 */
@SprudelDsl
@KlangScript.Function
fun phaserdepth(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.phaserdepth(amount, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the phaser depth after the previous mapper.
 *
 * @param amount The modulation intensity (0–1). Omit to reinterpret the pattern's values as phaser depth.
 * @return A new [PatternMapperFn] chaining this phaser depth after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(phaser(0.5).phaserdepth(0.8))   // rate then depth
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, phaser(4.0).phaserdepth(1.0))   // full-depth fast phaser
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.phaserdepth(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.phaserdepth(amount, callInfo) }

/**
 * Alias for [phaserdepth]. Sets the phaser depth (modulation intensity) for this pattern.
 *
 * When [amount] is omitted, the pattern's own numeric values are reinterpreted as phaser depth.
 *
 * @param amount The modulation intensity (0–1). Omit to reinterpret the pattern's values as phaser depth.
 * @return A new pattern with the phaser depth applied.
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
 * @alias phaserdepth, phasdp
 * @category effects
 * @tags phd, phaserdepth, phasdp, phaser, depth, modulation
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.phd(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.phaserdepth(amount, callInfo)

/**
 * Alias for [phaserdepth]. Parses this string as a pattern and sets the phaser depth.
 *
 * When [amount] is omitted, the string's numeric values are reinterpreted as phaser depth.
 *
 * @param amount The modulation intensity (0–1). Omit to reinterpret the pattern's values as phaser depth.
 *
 * ```KlangScript(Playable)
 * "c3*4".phd(0.8).phaser(0.5).note()   // deep phaser on string pattern
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun String.phd(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).phaserdepth(amount, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the phaser depth. Alias for [phaserdepth].
 *
 * @param amount The modulation intensity (0–1). Omit to reinterpret the pattern's values as phaser depth.
 * @return A [PatternMapperFn] that sets the phaser depth.
 *
 * ```KlangScript(Playable)
 * note("c3*4").apply(phd(0.8))   // deep phaser via mapper
 * ```
 *
 * @alias phaserdepth, phasdp
 * @category effects
 * @tags phd, phaserdepth, phasdp, phaser, depth, modulation
 */
@SprudelDsl
@KlangScript.Function
fun phd(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.phaserdepth(amount, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the phaser depth (alias for phaserdepth) after the previous mapper.
 *
 * @param amount The modulation intensity (0–1). Omit to reinterpret the pattern's values as phaser depth.
 * @return A new [PatternMapperFn] chaining this phaser depth after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(phaser(0.5).phd(0.8))   // rate then depth
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, phaser(4.0).phd(1.0))   // full-depth fast phaser
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.phd(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.phaserdepth(amount, callInfo) }

/**
 * Alias for [phaserdepth]. Sets the phaser depth (modulation intensity) for this pattern.
 *
 * When [amount] is omitted, the pattern's own numeric values are reinterpreted as phaser depth.
 *
 * @param amount The modulation intensity (0–1). Omit to reinterpret the pattern's values as phaser depth.
 * @return A new pattern with the phaser depth applied.
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
 * @alias phaserdepth, phd
 * @category effects
 * @tags phasdp, phaserdepth, phd, phaser, depth, modulation
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.phasdp(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.phaserdepth(amount, callInfo)

/**
 * Alias for [phaserdepth]. Parses this string as a pattern and sets the phaser depth.
 *
 * When [amount] is omitted, the string's numeric values are reinterpreted as phaser depth.
 *
 * @param amount The modulation intensity (0–1). Omit to reinterpret the pattern's values as phaser depth.
 *
 * ```KlangScript(Playable)
 * "c3*4".phasdp(0.8).phaser(0.5).note()   // deep phaser on string pattern
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun String.phasdp(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).phaserdepth(amount, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the phaser depth. Alias for [phaserdepth].
 *
 * @param amount The modulation intensity (0–1). Omit to reinterpret the pattern's values as phaser depth.
 * @return A [PatternMapperFn] that sets the phaser depth.
 *
 * ```KlangScript(Playable)
 * note("c3*4").apply(phasdp(0.8))   // deep phaser via mapper
 * ```
 *
 * @alias phaserdepth, phd
 * @category effects
 * @tags phasdp, phaserdepth, phd, phaser, depth, modulation
 */
@SprudelDsl
@KlangScript.Function
fun phasdp(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.phaserdepth(amount, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the phaser depth (alias for phaserdepth) after the previous mapper.
 *
 * @param amount The modulation intensity (0–1). Omit to reinterpret the pattern's values as phaser depth.
 * @return A new [PatternMapperFn] chaining this phaser depth after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(phaser(0.5).phasdp(0.8))   // rate then depth
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, phaser(4.0).phasdp(1.0))   // full-depth fast phaser
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.phasdp(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.phaserdepth(amount, callInfo) }

// -- phasercenter() / phc() -------------------------------------------------------------------------------------------

private val phaserCenterMutation = voiceModifier { copy(phaserCenter = it?.asDoubleOrNull()) }

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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.phc(freq: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.phasercenter(freq, callInfo) }

// -- phasersweep() / phs() --------------------------------------------------------------------------------------------

private val phaserSweepMutation = voiceModifier { copy(phaserSweep = it?.asDoubleOrNull()) }

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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.phs(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.phasersweep(amount, callInfo) }

// -- tremolosync() / tremsync() ---------------------------------------------------------------------------------------

private val tremoloSyncMutation = voiceModifier { copy(tremoloSync = it?.asDoubleOrNull()) }

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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.tremsync(rate: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.tremolosync(rate, callInfo) }

// -- tremolodepth() / tremdepth() -------------------------------------------------------------------------------------

private val tremoloDepthMutation = voiceModifier { copy(tremoloDepth = it?.asDoubleOrNull()) }

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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.tremdepth(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.tremolodepth(amount, callInfo) }

// -- tremoloskew() / tremskew() ---------------------------------------------------------------------------------------

private val tremoloSkewMutation = voiceModifier { copy(tremoloSkew = it?.asDoubleOrNull()) }

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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.tremskew(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.tremoloskew(amount, callInfo) }

// -- tremolophase() / tremphase() -------------------------------------------------------------------------------------

private val tremoloPhaseMutation = voiceModifier { copy(tremoloPhase = it?.asDoubleOrNull()) }

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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.tremphase(phase: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.tremolophase(phase, callInfo) }

// -- tremoloshape() / tremshape() -------------------------------------------------------------------------------------

private val tremoloShapeMutation = voiceModifier { shape -> copy(tremoloShape = shape?.toString()?.lowercase()) }

private fun applyTremoloShape(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._applyControlFromParams(args, tremoloShapeMutation) { src, ctrl ->
        src.copy(tremoloShape = ctrl.tremoloShape)
    }
}

/**
 * Sets the tremolo LFO waveform shape for this pattern.
 *
 * Accepted values: `"sine"`, `"triangle"`, `"square"`, `"sawtooth"`, `"rampup"`, `"rampdown"`.
 * Different shapes produce different tremolo characters — sine is smooth, square is choppy.
 *
 * @param shape The LFO waveform shape name.
 * @param-tool shape SprudelWaveformSequenceEditor
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.tremoloshape(shape: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.tremoloshape(shape, callInfo) }

/**
 * Alias for [tremoloshape]. Sets the tremolo LFO waveform shape for this pattern.
 *
 * @param shape The LFO waveform shape name.
 * @param-tool shape SprudelWaveformSequenceEditor
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.tremshape(shape: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.tremoloshape(shape, callInfo) }
