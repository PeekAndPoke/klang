@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions")

package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.strudel.*
import io.peekandpoke.klang.strudel.lang.StrudelDslArg.Companion.asStrudelDslArgs

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in StrudelRegistry.
 */
var strudelLangDynamicsInit = false

// -- gain() -----------------------------------------------------------------------------------------------------------

private val gainMutation = voiceModifier { copy(gain = it?.asDoubleOrNull()) }

fun applyGain(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftOrReinterpretNumericalField(args, gainMutation)
}

internal val StrudelPattern._gain by dslPatternExtension { p, args, /* callInfo */ _ -> applyGain(p, args) }

internal val String._gain by dslStringExtension { p, args, callInfo -> p._gain(args, callInfo) }

internal val _gain by dslPatternMapper { args, callInfo -> { p -> p._gain(args, callInfo) } }

internal val PatternMapperFn._gain by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_gain(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the gain (volume multiplier) for each event in the pattern.
 *
 * Values below 1 reduce volume; above 1 amplify. Accepts control patterns for per-event modulation.
 *
 * ```KlangScript
 * s("bd sd hh cp").gain(0.5)              // all hits at half volume
 * ```
 *
 * ```KlangScript
 * s("bd*4").gain("<0.2 0.5 0.8 1.0>")    // different gain each cycle
 * ```
 *
 * @param amount The control value to use for gain.
 * @param-tool amount StrudelGainSequenceEditor
 *
 * @category dynamics
 * @tags gain, volume, amplitude, dynamics
 */
@StrudelDsl
fun StrudelPattern.gain(amount: PatternLike? = null): StrudelPattern =
    this._gain(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Parses this string as a pattern and sets the gain for each event.
 *
 * ```KlangScript
 * "bd*4".gain("0.2 0.5 0.8 1.0").s()    // different gain each beat
 * ```
 *
 * @param amount The control value to use for gain.
 */
@StrudelDsl
fun String.gain(amount: PatternLike? = null): StrudelPattern =
    this._gain(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Creates a [PatternMapperFn] that sets the gain for each event in a pattern.
 *
 * ```KlangScript
 * s("hh hh hh hh").apply(gain("1.0 0.75 0.5 0.25"))
 * ```
 *
 * @param amount The control value to use for gain.
 */
@StrudelDsl
fun gain(amount: PatternLike? = null): PatternMapperFn =
    _gain(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets the gain after the previous mapper.
 *
 * ```KlangScript
 * s("hh*4").apply(gain("1.0 0.5").gain(0.8))  // chain gain modifiers
 * ```
 *
 * @param amount The control value to use for gain.
 */
@StrudelDsl
fun PatternMapperFn.gain(amount: PatternLike? = null): PatternMapperFn =
    _gain(listOfNotNull(amount).asStrudelDslArgs())

// -- pan() ------------------------------------------------------------------------------------------------------------

private val panMutation = voiceModifier { copy(pan = it?.asDoubleOrNull()) }

fun applyPan(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftOrReinterpretNumericalField(args, panMutation)
}

internal val StrudelPattern._pan by dslPatternExtension { p, args, /* callInfo */ _ -> applyPan(p, args) }

internal val String._pan by dslStringExtension { p, args, callInfo -> p._pan(args, callInfo) }

internal val _pan by dslPatternMapper { args, callInfo -> { p -> p._pan(args, callInfo) } }

internal val PatternMapperFn._pan by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_pan(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the stereo panning position for each event (0 = full left, 0.5 = centre, 1 = full right).
 *
 * Accepts control patterns or continuous patterns for animated panning effects.
 *
 * ```KlangScript
 * s("bd sd").pan(0.25)                   // slightly left
 * ```
 *
 * ```KlangScript
 * s("bd hh sd cp").pan("0 0.33 0.66 1")  // left to right
 * ```
 *
 * ```KlangScript
 * s("hh*8").pan(sine.range(0, 1))        // smooth left-right sweep
 * ```
 *
 * @param amount The panning position for each event, ranging from 0 (full left) to 1 (full right).
 * @param-tool amount StrudelPanSequenceEditor
 *
 * @category dynamics
 * @tags pan, stereo, panning, position
 */
@StrudelDsl
fun StrudelPattern.pan(amount: PatternLike? = null): StrudelPattern =
    this._pan(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Parses this string as a pattern and sets the stereo panning position.
 *
 * ```KlangScript
 * "bd hh sd cp".pan("0 0.33 0.66 1").s()  // left to right
 * ```
 *
 * @param amount The panning position for each event, ranging from 0 (full left) to 1 (full right).
 */
@StrudelDsl
fun String.pan(amount: PatternLike? = null): StrudelPattern =
    this._pan(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Creates a [PatternMapperFn] that sets the pan for each event in a pattern.
 *
 * ```KlangScript
 * s("bd hh sd cp").apply(pan("0 0.33 0.66 1"))  // left to right
 * ```
 */
@StrudelDsl
fun pan(amount: PatternLike? = null): PatternMapperFn =
    _pan(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets the pan after the previous mapper.
 *
 * ```KlangScript
 * s("bd hh sd cp").apply(pan("0 0.33 0.66 1").gain(0.8))  // pan + gain chained
 * ```
 *
 * @param amount The panning position for each event, ranging from 0 (full left) to 1 (full right).
 */
@StrudelDsl
fun PatternMapperFn.pan(amount: PatternLike? = null): PatternMapperFn =
    _pan(listOfNotNull(amount).asStrudelDslArgs())

// -- velocity() / vel() -----------------------------------------------------------------------------------------------

private val velocityMutation = voiceModifier { copy(velocity = it?.asDoubleOrNull()) }

fun applyVelocity(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftOrReinterpretNumericalField(args, velocityMutation)
}

internal val StrudelPattern._velocity by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyVelocity(p, args)
}

internal val String._velocity by dslStringExtension { p, args, callInfo -> p._velocity(args, callInfo) }

internal val _velocity by dslPatternMapper { args, callInfo -> { p -> p._velocity(args, callInfo) } }

internal val PatternMapperFn._velocity by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_velocity(args, callInfo))
}

internal val StrudelPattern._vel by dslPatternExtension { p, args, /* callInfo */ _ -> applyVelocity(p, args) }

internal val String._vel by dslStringExtension { p, args, callInfo -> p._velocity(args, callInfo) }

internal val _vel by dslPatternMapper { args, callInfo -> { p -> p._velocity(args, callInfo) } }

internal val PatternMapperFn._vel by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_vel(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the gain 'velocity'. It is multiplied with the gain of the events.
 *
 * ```KlangScript
 * note("c d e f").gain(0.5).velocity("0.5 2.0")  // gain is multiplied by velocity
 * ```
 *
 * ```KlangScript
 * note("c*4").velocity("<0.3 0.6 0.9 1.0>")  // crescendo pattern
 * ```
 *
 * ```KlangScript
 * note("c*4").velocity(saw.range(0.25, 1.0).slow(4))  // crescendo pattern over 4 cycles
 * ```
 *
 * @param amount The velocity value or pattern to apply to the events.
 *
 * @alias vel
 * @category dynamics
 * @tags velocity, vel, volume, midi, dynamics
 */
@StrudelDsl
fun StrudelPattern.velocity(amount: PatternLike? = null): StrudelPattern =
    this._velocity(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Parses this string as a pattern and sets the velocity (gain multiplier) for each event.
 *
 * ```KlangScript
 * "c*4".velocity("<0.3 0.6 0.9 1.0>").note()  // crescendo pattern
 * ```
 *
 * @param amount The velocity value or pattern to apply to the events.
 */
@StrudelDsl
fun String.velocity(amount: PatternLike? = null): StrudelPattern =
    this._velocity(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Create a [PatternMapperFn] that sets the velocity (gain multiplier) for each event in a pattern.
 *
 * ```KlangScript
 * note("c*4").apply(velocity("<0.3 0.6 0.9 1.0>"))  // crescendo pattern
 * ```
 *
 * @param amount The velocity value or pattern to apply to the events.
 */
@StrudelDsl
fun velocity(amount: PatternLike? = null): PatternMapperFn =
    _velocity(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets the velocity after the previous mapper.
 *
 * ```KlangScript
 * note("c*4").apply(velocity("<0.3 0.6 0.9>").gain(0.8))  // velocity + gain chained
 * ```
 *
 * @param amount The velocity value or pattern to apply to the events.
 */
@StrudelDsl
fun PatternMapperFn.velocity(amount: PatternLike? = null): PatternMapperFn =
    _velocity(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Alias for [velocity]. Sets the gain 'velocity'. It is multiplied with the gain of the events.
 *
 * ```KlangScript
 * note("c d e f").gain(0.5).vel("0.5 2.0")   // gain is multiplied by velocity
 * ```
 *
 * ```KlangScript
 * note("c*4").vel(saw.range(0.25, 1.0).slow(4))   // crescendo pattern over 4 cycles
 * ```
 *
 * @param amount The velocity value or pattern to apply to the events.
 *
 * @alias velocity
 * @category dynamics
 * @tags vel, velocity, volume, midi, dynamics
 */
@StrudelDsl
fun StrudelPattern.vel(amount: PatternLike? = null): StrudelPattern =
    this._vel(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Alias for [velocity]. Sets the velocity (gain multiplier) for each event in this pattern.
 *
 * ```KlangScript
 * "c*4".vel("<0.3 0.6 0.9 1.0>").note()  // crescendo pattern
 * ```
 *
 * @param amount The velocity value or pattern to apply to the events.
 */
@StrudelDsl
fun String.vel(amount: PatternLike? = null): StrudelPattern =
    this._vel(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Alias for [velocity]. Create a [PatternMapperFn] that sets the velocity (gain multiplier) for each event in a pattern.
 *
 * ```KlangScript
 * note("c*4").apply(vel("<0.3 0.6 0.9 1.0>"))  // crescendo pattern
 * ```
 *
 * @param amount The velocity value or pattern to apply to the events.
 */
@StrudelDsl
fun vel(amount: PatternLike? = null): PatternMapperFn =
    _vel(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Alias for [velocity]. Creates a chained [PatternMapperFn] that sets the velocity after the previous mapper.
 *
 * ```KlangScript
 * note("c*4").apply(vel("<0.3 0.6 0.9>").gain(0.8))  // velocity + gain chained
 * ```
 *
 * @param amount The velocity value or pattern to apply to the events.
 */
@StrudelDsl
fun PatternMapperFn.vel(amount: PatternLike? = null): PatternMapperFn =
    _vel(listOfNotNull(amount).asStrudelDslArgs())

// -- postgain() -------------------------------------------------------------------------------------------------------

private val postgainMutation = voiceModifier { copy(postGain = it?.asDoubleOrNull()) }

fun applyPostgain(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftOrReinterpretNumericalField(args, postgainMutation)
}

internal val StrudelPattern._postgain by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyPostgain(p, args)
}

internal val String._postgain by dslStringExtension { p, args, callInfo -> p._postgain(args, callInfo) }

internal val _postgain by dslPatternMapper { args, callInfo -> { p -> p._postgain(args, callInfo) } }

internal val PatternMapperFn._postgain by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_postgain(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the post-gain (applied after voice processing) for each event in the pattern.
 *
 * Unlike `gain` which is applied before synthesis, `postgain` is a final output multiplier.
 *
 * ```KlangScript
 * s("bd sd").postgain(1.5)                    // amplify after processing
 * ```
 *
 * ```KlangScript
 * s("hh*8").postgain(rand.range(0.1, 1.0))   // random post-gain per hit
 * ```
 *
 * @param amount The post-gain value or pattern to apply to the events.
 *
 * @category dynamics
 * @tags postgain, gain, volume, post-processing
 */
@StrudelDsl
fun StrudelPattern.postgain(amount: PatternLike? = null): StrudelPattern =
    this._postgain(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Parses this string as a pattern and sets the post-gain for each event.
 *
 * ```KlangScript
 * "hh*8".postgain(perlin.range(0.1, 1.0).slow(4)).s()   // perlin noised post-gain
 * ```
 *
 * @param amount The post-gain value or pattern to apply to the events.
 */
@StrudelDsl
fun String.postgain(amount: PatternLike? = null): StrudelPattern =
    this._postgain(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Create a [PatternMapperFn] that sets the post-gain for each event in a pattern.
 *
 * ```KlangScript
 * "hh*8".apply(postgain(sine.range(0.1, 1.0).slow(2))).s()   // sine post-gain over two cycles
 * ```
 *
 * @param amount The post-gain value or pattern to apply to the events.
 */
@StrudelDsl
fun postgain(amount: PatternLike? = null): PatternMapperFn =
    _postgain(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets the post-gain after the previous mapper.
 *
 * ```KlangScript
 * s("hh*4").apply(postgain(0.8).gain(0.5))  // postgain + gain chained
 * ```
 *
 * @param amount The post-gain value or pattern to apply to the events.
 */
@StrudelDsl
fun PatternMapperFn.postgain(amount: PatternLike? = null): PatternMapperFn =
    _postgain(listOfNotNull(amount).asStrudelDslArgs())

// -- compressor() / comp() --------------------------------------------------------------------------------------------

private val compressorMutation = voiceModifier { copy(compressor = it?.toString()) }

fun applyCompressor(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftOrReinterpretStringField(args, compressorMutation)
}

internal val StrudelPattern._compressor by dslPatternExtension { p, args, /* callInfo */ _ -> applyCompressor(p, args) }
internal val String._compressor by dslStringExtension { p, args, callInfo -> p._compressor(args, callInfo) }
internal val _compressor by dslPatternMapper { args, callInfo -> { p -> p._compressor(args, callInfo) } }

internal val PatternMapperFn._compressor by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_compressor(args, callInfo))
}

internal val StrudelPattern._comp by dslPatternExtension { p, args, /* callInfo */ _ -> applyCompressor(p, args) }
internal val String._comp by dslStringExtension { p, args, callInfo -> p._comp(args, callInfo) }
internal val _comp by dslPatternMapper { args, callInfo -> { p -> p._comp(args, callInfo) } }

internal val PatternMapperFn._comp by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_comp(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets dynamic range compression parameters as a colon-separated string
 * `"threshold:ratio:knee:attack:release"`.
 *
 * **Threshold:** The volume level (in decibels) at which compression starts.
 * - Logic: Signals above this level are attenuated.
 * - Range: Usually -60.0 to 0.0.
 *
 * **Ratio:** How much the signal is reduced once it exceeds the threshold.
 * - Logic: A ratio of 4.0 (4:1) means that for every 4dB the input goes over the threshold,
 *   the output only increases by 1dB.
 * - Range: 1.0 (no compression) and up. 20.0 or higher acts as a limiter.
 *
 * **Knee:** The "smoothness" of the transition into compression.
 * - Logic: A value of 0 is a "hard knee" (instant compression at threshold). Higher values (e.g., 6.0) create a
 *   "soft knee" where compression is applied gradually as the signal approaches the threshold.
 *
 * **Attack:** How quickly the compressor reacts to signals exceeding the threshold.
 * - Logic: Measured in seconds. Fast attacks (e.g., 0.003) catch peaks immediately; slow attacks let the
 *   initial "click" or transient through.
 *
 * **Release:** How quickly the compressor stops attenuating after the signal falls back below the threshold.
 * - Logic: Measured in seconds. Short release times (e.g., 0.1) return to normal quickly; long release times
 *   create a smoother, more "levelled" sound.
 *
 * **Common Configurations:**
 *
 * | Use Case          | Configuration        | Description                                                                              |
 * | ----------------- | -------------------- | ---------------------------------------------------------------------------------------- |
 * | Gentle Leveling   | `-15:2:6:0.01:0.2`   | Low ratio and soft knee to subtly even out a melody or pad.                              |
 * | Punchy Drums      | `-20:4:3:0.03:0.1`   | Slightly slower attack to let the drum "hit" (transient) pass before squeezing the tail. |
 * | Brickwall Limiter | `-2:40:0:0.001:0.05` | High ratio and instant attack to prevent any signal from clipping above -2dB.            |
 * | Heavy Squeeze     | `-30:8:2:0.005:0.1`  | Low threshold and high ratio for that "pumping" aggressive sound.                        |
 *
 * ```KlangScript
 * s("bd sd").compressor("-20:4:3:0.03:0.1")  // standard compression
 * ```
 *
 * ```KlangScript
 * s("bd*4").compressor("<-10:2:1:0.01:0.1 -30:8:5:0.005:0.5>")   // alternate settings
 * ```
 *
 * ```KlangScript
 * // Shorthand: only threshold and ratio (defaults: knee=6.0, attack=0.003, release=0.1)
 * s("hh*8").compressor("-15:4")
 * ```
 *
 * @param params The compression parameters as a colon-separated string.
 *
 * @param-tool params StrudelCompressorSequenceEditor
 * @alias comp
 * @category dynamics
 * @tags compressor, comp, compression, threshold, ratio, dynamics
 */
@StrudelDsl
fun StrudelPattern.compressor(params: PatternLike? = null): StrudelPattern =
    this._compressor(listOfNotNull(params).asStrudelDslArgs())

/**
 * Parses this string as a pattern and sets dynamic range compression parameters.
 *
 * ```KlangScript
 * s("bd*4").compressor("<-10:2:1:0.01:0.1 -30:8:5:0.005:0.5>")   // alternate settings
 * ```
 *
 * @param params The compression parameters as a colon-separated string.
 */
@StrudelDsl
fun String.compressor(params: PatternLike? = null): StrudelPattern =
    this._compressor(listOfNotNull(params).asStrudelDslArgs())

/**
 * Create a [PatternMapperFn] that sets dynamic range compression parameters for a pattern.
 *
 * ```KlangScript
 * s("bd*4").apply(compressor("<-10:2:1:0.01:0.1 -30:8:5:0.005:0.5>"))   // alternate settings
 * ```

 */
@StrudelDsl
fun compressor(params: PatternLike? = null): PatternMapperFn =
    _compressor(listOfNotNull(params).asStrudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets compressor parameters after the previous mapper.
 *
 * ```KlangScript
 * s("bd*4").apply(compressor("-20:4:3:0.03:0.1").gain(0.8))  // compress + gain chained
 * ```
 *
 * @param params The compression parameters as a colon-separated string.
 */
@StrudelDsl
fun PatternMapperFn.compressor(params: PatternLike? = null): PatternMapperFn =
    _compressor(listOfNotNull(params).asStrudelDslArgs())

/**
 * Alias for [compressor]. Sets dynamic range compression parameters as a colon-separated string
 * `"threshold:ratio:knee:attack:release"`.
 *
 * ```KlangScript
 * s("bd sd").comp("-20:4:3:0.01:0.3")                        // standard compression
 * ```
 *
 * ```KlangScript
 * s("bd*4").comp("<-10:2:1:0.01:0.1 -30:8:5:0.005:0.5>")   // alternate settings
 * ```
 *
 * @param params The compression parameters as a colon-separated string.
 *
 * @param-tool params StrudelCompressorSequenceEditor
 * @alias compressor
 * @category dynamics
 * @tags comp, compressor, compression, threshold, ratio, dynamics
 */
@StrudelDsl
fun StrudelPattern.comp(params: PatternLike? = null): StrudelPattern =
    this._comp(listOfNotNull(params).asStrudelDslArgs())

/**
 * Alias for [compressor]. Parses this string as a pattern and sets compression parameters.
 *
 * ```KlangScript
 * s("bd*4").comp("<-10:2:1:0.01:0.1 -30:8:5:0.005:0.5>")   // alternate settings
 * ```
 *
 * @param params The compression parameters as a colon-separated string.
 */
@StrudelDsl
fun String.comp(params: PatternLike? = null): StrudelPattern =
    this._comp(listOfNotNull(params).asStrudelDslArgs())

/**
 * Alias for [compressor]. Parses this string as a pattern and sets compression parameters.
 *
 * ```KlangScript
 * s("bd*4").apply(comp("<-10:2:1:0.01:0.1 -30:8:5:0.005:0.5>"))   // alternate settings
 * ```
 *
 * @param params The compression parameters as a colon-separated string.
 */
@StrudelDsl
fun comp(params: PatternLike? = null): PatternMapperFn =
    _comp(listOfNotNull(params).asStrudelDslArgs())

/**
 * Alias for [compressor]. Creates a chained [PatternMapperFn] that sets compressor parameters after the previous
 * mapper.
 *
 * ```KlangScript
 * s("bd*4").apply(comp("-20:4:3:0.03:0.1").gain(0.8))  // compress + gain chained
 * ```
 *
 * @param params The compression parameters as a colon-separated string.
 */
@StrudelDsl
fun PatternMapperFn.comp(params: PatternLike? = null): PatternMapperFn =
    _comp(listOfNotNull(params).asStrudelDslArgs())

// -- unison() / uni() -------------------------------------------------------------------------------------------------

private val unisonMutation = voiceModifier { copy(voices = it?.asDoubleOrNull()) }

private fun applyUnison(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftOrReinterpretStringField(args, unisonMutation)
}

internal val StrudelPattern._unison by dslPatternExtension { p, args, /* callInfo */ _ -> applyUnison(p, args) }
internal val String._unison by dslStringExtension { p, args, callInfo -> p._unison(args, callInfo) }
internal val _unison by dslPatternMapper { args, callInfo -> { p -> p._unison(args, callInfo) } }

internal val PatternMapperFn._unison by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_unison(args, callInfo))
}

internal val StrudelPattern._uni by dslPatternExtension { p, args, /* callInfo */ _ -> applyUnison(p, args) }
internal val String._uni by dslStringExtension { p, args, callInfo -> p._uni(args, callInfo) }
internal val _uni by dslPatternMapper { args, callInfo -> { p -> p._uni(args, callInfo) } }

internal val PatternMapperFn._uni by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_uni(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the number of unison voices for oscillator stacking effects (e.g. supersaw).
 *
 * Higher values produce a thicker, chorus-like sound. Use with `detune` and `spread`
 * to control the detuning and panning spread of the voices.
 *
 * ```KlangScript
 * note("c3").s("supersaw").unison(5)               // 5 stacked sawtooth oscillators
 * ```
 *
 * ```KlangScript
 * note("c3 e3 g3").s("supersaw").unison("<3 6 10 16>").detune(0.3)  // unison pattern
 * ```
 *
 * @param voices The number of unison voices.
 *
 * @alias uni
 * @category dynamics
 * @tags unison, uni, voices, stacking, supersaw
 */
@StrudelDsl
fun StrudelPattern.unison(voices: PatternLike? = null): StrudelPattern =
    this._unison(listOfNotNull(voices).asStrudelDslArgs())

/**
 * Parses this string as a pattern and sets the number of unison voices.
 *
 * ```KlangScript
 * "c3 e3 g3".s("supersaw").unison("<1 5 10 16>").detune(0.3).note()  // unison pattern
 * ```
 *
 * @param voices The number of unison voices.
 */
@StrudelDsl
fun String.unison(voices: PatternLike? = null): StrudelPattern =
    this._unison(listOfNotNull(voices).asStrudelDslArgs())

/**
 * Create a [PatternMapperFn] that sets the number of unison voices for a pattern.
 *
 * ```KlangScript
 * "c3 e3 g3".s("supersaw").apply(unison("<1 5 10 16>")).detune(0.3).note()  // unison pattern
 * ```
 *
 * @param voices The number of unison voices.
 */
@StrudelDsl
fun unison(voices: PatternLike? = null): PatternMapperFn =
    _unison(listOfNotNull(voices).asStrudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets the number of unison voices after the previous mapper.
 *
 * ```KlangScript
 * note("c3").s("supersaw").apply(unison(5).detune(0.3))  // unison + detune chained
 * ```
 *
 * @param voices The number of unison voices.
 */
@StrudelDsl
fun PatternMapperFn.unison(voices: PatternLike? = null): PatternMapperFn =
    _unison(listOfNotNull(voices).asStrudelDslArgs())

/**
 * Alias for [unison]. Sets the number of unison voices for oscillator stacking effects.
 *
 * ```KlangScript
 * note("c3").s("supersaw").uni(5)               // 5 stacked sawtooth oscillators
 * ```
 *
 * ```KlangScript
 * note("c3 e3 g3").s("supersaw").uni("<1 5 10 16>").detune(0.3)  // unison pattern
 * ```
 *
 * @param voices The number of unison voices.
 *
 * @alias unison
 * @category dynamics
 * @tags uni, unison, voices, stacking, supersaw
 */
@StrudelDsl
fun StrudelPattern.uni(voices: PatternLike? = null): StrudelPattern =
    this._uni(listOfNotNull(voices).asStrudelDslArgs())

/**
 * Alias for [unison]. Parses this string as a pattern and sets the number of unison voices.
 *
 * ```KlangScript
 * "c3 e3 g3".s("supersaw").uni("<1 5 10 16>").detune(0.3).note()  // unison pattern
 * ```
 */
@StrudelDsl
fun String.uni(voices: PatternLike? = null): StrudelPattern =
    this._uni(listOfNotNull(voices).asStrudelDslArgs())

/**
 * Alias for [unison]. Creates a [PatternMapperFn] that sets the number of unison voices for a pattern.
 *
 * ```KlangScript
 * "c3 e3 g3".s("supersaw").apply(unison("<1 5 10 16>")).detune(0.3).note()  // unison pattern
 * ```
 *
 * @param voices The number of unison voices.
 */
@StrudelDsl
fun uni(voices: PatternLike? = null): PatternMapperFn =
    _uni(listOfNotNull(voices).asStrudelDslArgs())

/**
 * Alias for [unison]. Creates a chained [PatternMapperFn] that sets the number of unison voices after the previous
 * mapper.
 *
 * ```KlangScript
 * note("c3").s("supersaw").apply(uni(5).detune(0.3))  // unison + detune chained
 * ```
 *
 * @param voices The number of unison voices.
 */
@StrudelDsl
fun PatternMapperFn.uni(voices: PatternLike? = null): PatternMapperFn =
    _uni(listOfNotNull(voices).asStrudelDslArgs())

// -- detune() ---------------------------------------------------------------------------------------------------------

private val detuneMutation = voiceModifier { copy(freqSpread = it?.asDoubleOrNull()) }

private fun applyDetune(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftOrReinterpretStringField(args, detuneMutation)
}

internal val StrudelPattern._detune by dslPatternExtension { p, args, /* callInfo */ _ -> applyDetune(p, args) }
internal val String._detune by dslStringExtension { p, args, callInfo -> p._detune(args, callInfo) }
internal val _detune by dslPatternMapper { args, callInfo -> { p -> p._detune(args, callInfo) } }

internal val PatternMapperFn._detune by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_detune(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the oscillator frequency spread in cents for unison/supersaw effects.
 *
 * Controls how much each unison voice is detuned from the base pitch. Use with `unison`
 * to set the number of voices. Higher values produce a wider, more detuned sound.
 *
 * ```KlangScript
 * note("c3").s("supersaw").unison(5).detune(0.5)   // 5 voices spread 0.5 half tones
 * ```
 *
 * ```KlangScript
 * note("c3*4").s("supersaw").detune("<0.05 0.10 0.20 0.40>")  // escalating detune each beat
 * ```
 *
 * @param amount The detuning in cents.
 *
 * @category dynamics
 * @tags detune, spread, unison, cents, supersaw
 */
@StrudelDsl
fun StrudelPattern.detune(amount: PatternLike? = null): StrudelPattern =
    this._detune(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Parses this string as a pattern and sets the oscillator frequency spread.
 *
 * ```KlangScript
 * "c3*4".detune("<0.05 0.10 0.20 0.40>").s("supersaw").note() // escalating detune each beat
 * ```
 *
 * @param amount The detuning in cents.
 */
@StrudelDsl
fun String.detune(amount: PatternLike? = null): StrudelPattern =
    this._detune(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Creates a [PatternMapperFn] that sets the oscillator frequency spread for a pattern.
 *
 * ```KlangScript
 * note("c3*4").s("supersaw").apply(detune("<0.05 0.10 0.20 0.40>"))  // escalating detune each beat
 * ```
 * @param amount The detuning in cents.
 */
@StrudelDsl
fun detune(amount: PatternLike? = null): PatternMapperFn =
    _detune(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets the oscillator frequency spread after the previous mapper.
 *
 * ```KlangScript
 * note("c3").s("supersaw").apply(unison(5).detune(0.3))  // unison + detune chained
 * ```
 *
 * @param amount The detuning in cents.
 */
@StrudelDsl
fun PatternMapperFn.detune(amount: PatternLike? = null): PatternMapperFn =
    _detune(listOfNotNull(amount).asStrudelDslArgs())

// -- spread() ---------------------------------------------------------------------------------------------------------

private val spreadMutation = voiceModifier { copy(panSpread = it?.asDoubleOrNull()) }

private fun applySpread(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftOrReinterpretStringField(args, spreadMutation)
}

internal val StrudelPattern._spread by dslPatternExtension { p, args, /* callInfo */ _ -> applySpread(p, args) }
internal val String._spread by dslStringExtension { p, args, callInfo -> p._spread(args, callInfo) }
internal val _spread by dslPatternMapper { args, callInfo -> { p -> p._spread(args, callInfo) } }

internal val PatternMapperFn._spread by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_spread(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the stereo pan spread for unison/supersaw voices (0 = mono, 1 = full stereo spread).
 *
 * Controls how widely the unison voices are spread across the stereo field. Use with
 * `unison` to set the number of voices.
 *
 * ```KlangScript
 * note("c3").s("supersaw").unison(5).spread(0.8)   // wide stereo spread
 * ```
 *
 * ```KlangScript
 * note("c3*4").s("supersaw").spread("<0.2 0.5 0.8 1.0>")         // gradually widen each beat
 * ```
 *
 * @param amount The stereo pan spread, between 0 and 1.
 *
 * @category dynamics
 * @tags spread, pan, stereo, unison, supersaw
 */
@StrudelDsl
fun StrudelPattern.spread(amount: PatternLike? = null): StrudelPattern =
    this._spread(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Parses this string as a pattern and sets the stereo pan spread for unison voices.
 *
 * ```KlangScript
 * "c3*4".spread("<0.2 0.5 0.8 1.0>").s("supersaw").note()         // gradually widen each beat
 * ```
 *
 * @param amount The stereo pan spread, between 0 and 1.
 */
@StrudelDsl
fun String.spread(amount: PatternLike? = null): StrudelPattern =
    this._spread(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Creates a [PatternMapperFn] that sets the stereo pan spread for unison voices.
 *
 * ```KlangScript
 * "c3*4".apply(spread("<0.2 0.5 0.8 1.0>")).s("supersaw").note()         // gradually widen each beat
 * ```
 *
 * @param amount The stereo pan spread, between 0 and 1.
 */
@StrudelDsl
fun spread(amount: PatternLike? = null): PatternMapperFn =
    _spread(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets the stereo pan spread after the previous mapper.
 *
 * ```KlangScript
 * note("c3").s("supersaw").apply(unison(5).spread(0.8))  // unison + spread chained
 * ```
 *
 * @param amount The stereo pan spread, between 0 and 1.
 */
@StrudelDsl
fun PatternMapperFn.spread(amount: PatternLike? = null): PatternMapperFn =
    _spread(listOfNotNull(amount).asStrudelDslArgs())

// -- density() / d() --------------------------------------------------------------------------------------------------

private val densityMutation = voiceModifier { copy(density = it?.asDoubleOrNull()) }

private fun applyDensity(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftOrReinterpretStringField(args, densityMutation)
}

internal val StrudelPattern._density by dslPatternExtension { p, args, /* callInfo */ _ -> applyDensity(p, args) }
internal val String._density by dslStringExtension { p, args, callInfo -> p._density(args, callInfo) }
internal val _density by dslPatternMapper { args, callInfo -> { p -> p._density(args, callInfo) } }

internal val PatternMapperFn._density by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_density(args, callInfo))
}

internal val StrudelPattern._d by dslPatternExtension { p, args, /* callInfo */ _ -> applyDensity(p, args) }
internal val String._d by dslStringExtension { p, args, callInfo -> p._d(args, callInfo) }
internal val _d by dslPatternMapper { args, callInfo -> { p -> p._d(args, callInfo) } }

internal val PatternMapperFn._d by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_d(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the oscillator density for supersaw or noise density for dust/crackle generators.
 *
 * For supersaw: controls how tightly packed the oscillators are.
 * For noise generators (e.g. `dust`): controls the number of events per second.
 *
 * ```KlangScript
 * note("a").s("dust").density(40)   // 40 noise events per second
 * ```
 *
 * ```KlangScript
 * note("c3").s("supersaw").unison(7).density("<0 0.5 1 2>")  // tight supersaw
 * ```
 *
 * @param amount The oscillator density.
 *
 * @alias d
 * @category dynamics
 * @tags density, d, supersaw, dust, noise
 */
@StrudelDsl
fun StrudelPattern.density(amount: PatternLike? = null): StrudelPattern =
    this._density(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Parses this string as a pattern and sets the oscillator or noise density.
 *
 * ```KlangScript
 * "a".density(40).s("dust").note()   // 40 noise events per second
 * ```
 *
 * @param amount The oscillator density.
 */
@StrudelDsl
fun String.density(amount: PatternLike? = null): StrudelPattern =
    this._density(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Parses this string as a pattern and sets the oscillator or noise density.
 *
 * ```KlangScript
 * "a".apply(density(40)).s("dust").note()   // 40 noise events per second
 * ```
 * @param amount The oscillator density.
 */
@StrudelDsl
fun density(amount: PatternLike? = null): PatternMapperFn =
    _density(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets the oscillator or noise density after the previous mapper.
 *
 * ```KlangScript
 * note("c3").s("supersaw").apply(unison(7).density(0.5))  // unison + density chained
 * ```
 *
 * @param amount The oscillator density.
 */
@StrudelDsl
fun PatternMapperFn.density(amount: PatternLike? = null): PatternMapperFn =
    _density(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Alias for [density]. Sets the oscillator density for supersaw or noise density for dust/crackle.
 *
 * ```KlangScript
 * note("a").s("dust").d(40)   // 40 noise events per second
 * ```
 *
 * ```KlangScript
 * note("c3").s("supersaw").unison(7).d(0.5)   // tight supersaw
 * ```
 *
 * @alias density
 * @category dynamics
 * @tags d, density, supersaw, dust, noise
 */
@StrudelDsl
fun StrudelPattern.d(amount: PatternLike? = null): StrudelPattern =
    this._d(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Alias for [density]. Parses this string as a pattern and sets the oscillator or noise density.
 *
 * ```KlangScript
 * "a".d(40).s("dust").note()   // 40 noise events per second
 * ```
 *
 * @param amount The oscillator density.
 */
@StrudelDsl
fun String.d(amount: PatternLike? = null): StrudelPattern =
    this._d(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Alias for [density]. Creates a [PatternMapperFn] that sets the oscillator or noise density.
 *
 * ```KlangScript
 * note("a").apply(d(40)).s("dust")   // 40 noise events per second
 * ```
 *
 * @param amount The oscillator density.
 */
@StrudelDsl
fun d(amount: PatternLike? = null): PatternMapperFn =
    _d(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Alias for [density]. Creates a chained [PatternMapperFn] that sets the oscillator or noise density after the
 * previous mapper.
 *
 * ```KlangScript
 * note("c3").s("supersaw").apply(unison(7).d(0.5))  // unison + density chained
 * ```
 *
 * @param amount The oscillator density.
 */
@StrudelDsl
fun PatternMapperFn.d(amount: PatternLike? = null): PatternMapperFn =
    _d(listOfNotNull(amount).asStrudelDslArgs())

// -- ADSR attack() ----------------------------------------------------------------------------------------------------

private val attackMutation = voiceModifier { copy(attack = it?.asDoubleOrNull()) }

private fun applyAttack(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftOrReinterpretStringField(args, attackMutation)
}

internal val StrudelPattern._attack by dslPatternExtension { p, args, /* callInfo */ _ -> applyAttack(p, args) }

internal val String._attack by dslStringExtension { p, args, callInfo -> p._attack(args, callInfo) }

internal val _attack by dslPatternMapper { args, callInfo -> { p -> p._attack(args, callInfo) } }

internal val PatternMapperFn._attack by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_attack(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the ADSR envelope attack time in seconds for synthesised notes.
 *
 * Controls how quickly the note rises from silence to full volume at the start.
 * Short values produce a sharp, percussive onset; longer values create a gradual fade-in.
 *
 * ```KlangScript
 * note("c3 e3 g3").s("sine").attack(0.01)     // sharp attack
 * ```
 *
 * ```KlangScript
 * note("c3*4").attack("<0.01 0.1 0.5 1.0>")   // varying attacks
 * ```
 *
 * @param time The attack time in seconds.
 *
 * @category dynamics
 * @tags attack, adsr, envelope, fade-in
 */
@StrudelDsl
fun StrudelPattern.attack(time: PatternLike? = null): StrudelPattern =
    this._attack(listOfNotNull(time).asStrudelDslArgs())

/**
 * Parses this string as a pattern and sets the ADSR envelope attack time.
 *
 * ```KlangScript
 * "c3*4".attack("<0.01 0.1 0.5 1.0>").note()  // varying attacks
 * ```
 *
 * @param time The attack time in seconds.
 */
@StrudelDsl
fun String.attack(time: PatternLike? = null): StrudelPattern =
    this._attack(listOfNotNull(time).asStrudelDslArgs())

/**
 * Creates a [PatternMapperFn] that sets the ADSR envelope attack time for each event.
 *
 * ```KlangScript
 * note("c3*4").s("sine").apply(attack("<0.01 0.1 0.5 1.0>"))  // varying attacks
 * ```
 *
 * @param time The attack time in seconds.
 */
@StrudelDsl
fun attack(time: PatternLike? = null): PatternMapperFn = _attack(listOfNotNull(time).asStrudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets the ADSR attack time after the previous mapper.
 *
 * ```KlangScript
 * note("c3*4").s("sine").apply(attack(0.1).decay(0.2))  // attack + decay chained
 * ```
 *
 * @param time The attack time in seconds.
 */
@StrudelDsl
fun PatternMapperFn.attack(time: PatternLike? = null): PatternMapperFn =
    _attack(listOfNotNull(time).asStrudelDslArgs())

// -- ADSR decay() -----------------------------------------------------------------------------------------------------

private val decayMutation = voiceModifier { copy(decay = it?.asDoubleOrNull()) }

private fun applyDecay(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftOrReinterpretStringField(args, decayMutation)
}

internal val StrudelPattern._decay by dslPatternExtension { p, args, /* callInfo */ _ -> applyDecay(p, args) }

internal val String._decay by dslStringExtension { p, args, callInfo -> p._decay(args, callInfo) }

internal val _decay by dslPatternMapper { args, callInfo -> { p -> p._decay(args, callInfo) } }

internal val PatternMapperFn._decay by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_decay(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the ADSR envelope decay time in seconds for synthesised notes.
 *
 * Controls how quickly the volume falls from its peak to the sustain level after the attack phase.
 *
 * ```KlangScript
 * note("c3 e3").s("sawtooth").decay(0.2)       // short decay
 * ```
 *
 * ```KlangScript
 * note("c3*4").decay("<0.05 0.2 0.5 1.0>")    // varying decays
 * ```
 *
 * @param time The decay time in seconds.
 *
 * @category dynamics
 * @tags decay, adsr, envelope
 */
@StrudelDsl
fun StrudelPattern.decay(time: PatternLike? = null): StrudelPattern =
    this._decay(listOfNotNull(time).asStrudelDslArgs())

/**
 * Parses this string as a pattern and sets the ADSR envelope decay time.
 *
 * ```KlangScript
 * "c3*4".decay("<0.05 0.2 0.5 1.0>").note()   // varying decays
 * ```
 *
 * @param time The decay time in seconds.
 */
@StrudelDsl
fun String.decay(time: PatternLike? = null): StrudelPattern =
    this._decay(listOfNotNull(time).asStrudelDslArgs())

/**
 * Creates a [PatternMapperFn] that sets the ADSR envelope decay time for each event.
 *
 * ```KlangScript
 * note("c3*4").s("sawtooth").apply(decay("<0.05 0.2 0.5 1.0>"))  // varying decays
 * ```
 *
 * @param time The decay time in seconds.
 */
@StrudelDsl
fun decay(time: PatternLike? = null): PatternMapperFn = _decay(listOfNotNull(time).asStrudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets the ADSR decay time after the previous mapper.
 *
 * ```KlangScript
 * note("c3*4").s("sine").apply(attack(0.01).decay(0.2))  // attack + decay chained
 * ```
 *
 * @param time The decay time in seconds.
 */
@StrudelDsl
fun PatternMapperFn.decay(time: PatternLike? = null): PatternMapperFn =
    _decay(listOfNotNull(time).asStrudelDslArgs())

// -- ADSR sustain() ---------------------------------------------------------------------------------------------------

private val sustainMutation = voiceModifier { copy(sustain = it?.asDoubleOrNull()) }

private fun applySustain(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftOrReinterpretStringField(args, sustainMutation)
}

internal val StrudelPattern._sustain by dslPatternExtension { p, args, /* callInfo */ _ -> applySustain(p, args) }

internal val String._sustain by dslStringExtension { p, args, callInfo -> p._sustain(args, callInfo) }

internal val _sustain by dslPatternMapper { args, callInfo -> { p -> p._sustain(args, callInfo) } }

internal val PatternMapperFn._sustain by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_sustain(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the ADSR envelope sustain level (0–1) for synthesised notes.
 *
 * The sustain level is held while the note is pressed, after the attack and decay phases.
 * `0` = silence after decay; `1` = hold at full peak level.
 *
 * ```KlangScript
 * note("c3 e3").s("sine").sustain(0.7)        // 70% sustain level
 * ```
 *
 * ```KlangScript
 * note("c3*4").sustain("<0 0.3 0.7 1.0>")    // varying sustain
 * ```
 *
 * @param level The sustain level between 0 and 1.
 *
 * @category dynamics
 * @tags sustain, adsr, envelope, hold
 */
@StrudelDsl
fun StrudelPattern.sustain(level: PatternLike? = null): StrudelPattern =
    this._sustain(listOfNotNull(level).asStrudelDslArgs())

/**
 * Parses this string as a pattern and sets the ADSR envelope sustain level.
 *
 * ```KlangScript
 * "c3*4".sustain("<0 0.3 0.7 1.0>").note()   // varying sustain
 * ```
 *
 * @param level The sustain level between 0 and 1.
 */
@StrudelDsl
fun String.sustain(level: PatternLike? = null): StrudelPattern =
    this._sustain(listOfNotNull(level).asStrudelDslArgs())

/**
 * Creates a [PatternMapperFn] that sets the ADSR envelope sustain level for each event.
 *
 * ```KlangScript
 * note("c3*4").s("sine").apply(sustain("<0 0.3 0.7 1.0>"))  // varying sustain
 * ```
 *
 * @param level The sustain level between 0 and 1.
 */
@StrudelDsl
fun sustain(level: PatternLike? = null): PatternMapperFn = _sustain(listOfNotNull(level).asStrudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets the ADSR sustain level after the previous mapper.
 *
 * ```KlangScript
 * note("c3*4").s("sine").apply(attack(0.01).sustain(0.7))  // attack + sustain chained
 * ```
 *
 * @param level The sustain level between 0 and 1.
 */
@StrudelDsl
fun PatternMapperFn.sustain(level: PatternLike? = null): PatternMapperFn =
    _sustain(listOfNotNull(level).asStrudelDslArgs())

// -- ADSR release() ---------------------------------------------------------------------------------------------------

private val releaseMutation = voiceModifier { copy(release = it?.asDoubleOrNull()) }

private fun applyRelease(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftOrReinterpretStringField(args, releaseMutation)
}

internal val StrudelPattern._release by dslPatternExtension { p, args, /* callInfo */ _ -> applyRelease(p, args) }

internal val String._release by dslStringExtension { p, args, callInfo -> p._release(args, callInfo) }

internal val _release by dslPatternMapper { args, callInfo -> { p -> p._release(args, callInfo) } }

internal val PatternMapperFn._release by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_release(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the ADSR envelope release time in seconds for synthesised notes.
 *
 * Controls how long the note takes to fade to silence after a note-off event.
 * Short values produce an abrupt cut; longer values create a smooth fade-out.
 *
 * ```KlangScript
 * note("c3 e3 g3").s("sine").release(0.5)     // half-second release
 * ```
 *
 * ```KlangScript
 * note("c3*4").release("<0.1 0.3 0.8 2.0>")  // varying releases
 * ```
 *
 * @param time The release time in seconds.
 *
 * @category dynamics
 * @tags release, adsr, envelope, fade-out
 */
@StrudelDsl
fun StrudelPattern.release(time: PatternLike? = null): StrudelPattern =
    this._release(listOfNotNull(time).asStrudelDslArgs())

/**
 * Parses this string as a pattern and sets the ADSR envelope release time.
 *
 * ```KlangScript
 * "c3*4".release("<0.1 0.3 0.8 2.0>").note()  // varying releases
 * ```
 *
 * @param time The release time in seconds.
 */
@StrudelDsl
fun String.release(time: PatternLike? = null): StrudelPattern =
    this._release(listOfNotNull(time).asStrudelDslArgs())

/**
 * Creates a [PatternMapperFn] that sets the ADSR envelope release time for each event.
 *
 * ```KlangScript
 * note("c3*4").s("sine").apply(release("<0.1 0.3 0.8 2.0>"))  // varying releases
 * ```
 *
 * @param time The release time in seconds.
 */
@StrudelDsl
fun release(time: PatternLike? = null): PatternMapperFn = _release(listOfNotNull(time).asStrudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets the ADSR release time after the previous mapper.
 *
 * ```KlangScript
 * note("c3*4").s("sine").apply(attack(0.01).release(0.5))  // attack + release chained
 * ```
 *
 * @param time The release time in seconds.
 */
@StrudelDsl
fun PatternMapperFn.release(time: PatternLike? = null): PatternMapperFn =
    _release(listOfNotNull(time).asStrudelDslArgs())

// -- ADSR adsr() ------------------------------------------------------------------------------------------------------

private val adsrMutation = voiceModifier {
    val parts = it?.toString()?.split(":")
        ?.mapNotNull { d -> d.toDoubleOrNull() } ?: emptyList()

    copy(
        attack = parts.getOrNull(0) ?: attack,
        decay = parts.getOrNull(1) ?: decay,
        sustain = parts.getOrNull(2) ?: sustain,
        release = parts.getOrNull(3) ?: release,
    )
}

private fun applyAdsr(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._applyControlFromParams(args, adsrMutation) { src, ctrl ->
        src.copy(
            attack = ctrl.attack ?: src.attack,
            decay = ctrl.decay ?: src.decay,
            sustain = ctrl.sustain ?: src.sustain,
            release = ctrl.release ?: src.release,
        )
    }
}

internal val StrudelPattern._adsr by dslPatternExtension { p, args, /* callInfo */ _ -> applyAdsr(p, args) }

internal val String._adsr by dslStringExtension { p, args, callInfo -> p._adsr(args, callInfo) }

internal val _adsr by dslPatternMapper { args, callInfo -> { p -> p._adsr(args, callInfo) } }

internal val PatternMapperFn._adsr by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_adsr(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets all four ADSR envelope parameters at once via a colon-separated string
 * `"attack:decay:sustain:release"`.
 *
 * Each field is a number: attack/decay/release in seconds, sustain in 0–1 range.
 * Missing trailing fields keep their previous values.
 *
 * ```KlangScript
 * note("c3 e3 g3").s("sine").adsr("0.01:0.2:0.7:0.5")          // standard ADSR
 * ```
 *
 * ```KlangScript
 * note("c3*4").adsr("<0.01:0.1:0.5:0.2 0.5:0.5:0.8:1.0>")     // alternate envelopes
 * ```
 *
 * @param params The ADSR parameters as a colon-separated string `"attack:decay:sustain:release"`.
 * @param-tool params StrudelAdsrSequenceEditor
 *
 * @category dynamics
 * @tags adsr, attack, decay, sustain, release, envelope
 */
@StrudelDsl
fun StrudelPattern.adsr(params: PatternLike? = null): StrudelPattern =
    this._adsr(listOfNotNull(params).asStrudelDslArgs())

/**
 * Parses this string as a pattern and sets all ADSR envelope parameters.
 *
 * ```KlangScript
 * "c3*4".adsr("<0.01:0.1:0.5:0.2 0.5:0.5:0.8:1.0>").note()    // alternate envelopes
 * ```
 *
 * @param params The ADSR parameters as a colon-separated string `"attack:decay:sustain:release"`.
 */
@StrudelDsl
fun String.adsr(params: PatternLike? = null): StrudelPattern =
    this._adsr(listOfNotNull(params).asStrudelDslArgs())

/**
 * Creates a [PatternMapperFn] that sets all ADSR envelope parameters for each event.
 *
 * ```KlangScript
 * note("c3*4").s("sine").apply(adsr("<0.01:0.1:0.5:0.2 0.5:0.5:0.8:1.0>"))  // alternate envelopes
 * ```
 *
 * @param params The ADSR parameters as a colon-separated string `"attack:decay:sustain:release"`.
 */
@StrudelDsl
fun adsr(params: PatternLike? = null): PatternMapperFn = _adsr(listOfNotNull(params).asStrudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets all ADSR parameters after the previous mapper.
 *
 * ```KlangScript
 * note("c3*4").s("sine").apply(gain(0.8).adsr("0.01:0.2:0.7:0.5"))  // gain + adsr chained
 * ```
 *
 * @param params The ADSR parameters as a colon-separated string `"attack:decay:sustain:release"`.
 */
@StrudelDsl
fun PatternMapperFn.adsr(params: PatternLike? = null): PatternMapperFn =
    _adsr(listOfNotNull(params).asStrudelDslArgs())

// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Routing
// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

// -- orbit() / o() ----------------------------------------------------------------------------------------------------

private val orbitMutation = voiceModifier {
    copy(orbit = it?.asIntOrNull())
}

private fun applyOrbit(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftOrReinterpretStringField(args, orbitMutation)
}

internal val StrudelPattern._orbit by dslPatternExtension { p, args, /* callInfo */ _ -> applyOrbit(p, args) }
internal val String._orbit by dslStringExtension { p, args, callInfo -> p._orbit(args, callInfo) }
internal val _orbit by dslPatternMapper { args, callInfo -> { p -> p._orbit(args, callInfo) } }

internal val PatternMapperFn._orbit by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_orbit(args, callInfo))
}

internal val StrudelPattern._o by dslPatternExtension { p, args, /* callInfo */ _ -> applyOrbit(p, args) }
internal val String._o by dslStringExtension { p, args, callInfo -> p._o(args, callInfo) }
internal val _o by dslPatternMapper { args, callInfo -> { p -> p._o(args, callInfo) } }

internal val PatternMapperFn._o by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_o(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Routes the pattern to an audio output orbit (channel group) for independent effect processing.
 *
 * Each orbit can have its own reverb, delay, and other effects applied independently.
 * Use different orbit numbers to send patterns to different effect buses.
 *
 * ```KlangScript
 * s("bd sd").orbit(1)                           // send drums to orbit 1
 * ```
 *
 * ```KlangScript
 * note("c3 e3").orbit(2).room(0.8).roomsize(4)  // melodic line on orbit 2 with reverb
 * ```
 *
 * @param index The orbit index to route events to.
 *
 * @alias o
 * @category dynamics
 * @tags orbit, o, routing, effects, bus, channel
 */
@StrudelDsl
fun StrudelPattern.orbit(index: PatternLike? = null): StrudelPattern =
    this._orbit(listOfNotNull(index).asStrudelDslArgs())

/**
 * Parses this string as a pattern and routes it to the given audio output orbit.
 *
 * ```KlangScript
 * "bd sd".orbit(1).s()   // send drums to orbit 1
 * ```
 *
 * @param index The orbit index to route events to.
 */
@StrudelDsl
fun String.orbit(index: PatternLike? = null): StrudelPattern =
    this._orbit(listOfNotNull(index).asStrudelDslArgs())

/**
 * Creates a [PatternMapperFn] that routes events to the given audio output orbit.
 *
 * ```KlangScript
 * s("bd sd").apply(orbit(1))   // send drums to orbit 1
 * ```
 *
 * @param index The orbit index to route events to.
 */
@StrudelDsl
fun orbit(index: PatternLike? = null): PatternMapperFn =
    _orbit(listOfNotNull(index).asStrudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that routes events to the given orbit after the previous mapper.
 *
 * ```KlangScript
 * s("bd sd").apply(gain(0.8).orbit(1))  // gain + orbit chained
 * ```
 *
 * @param index The orbit index to route events to.
 */
@StrudelDsl
fun PatternMapperFn.orbit(index: PatternLike? = null): PatternMapperFn =
    _orbit(listOfNotNull(index).asStrudelDslArgs())

/**
 * Alias for [orbit]. Routes the pattern to an audio output orbit for independent effect processing.
 *
 * ```KlangScript
 * s("bd sd").o(1)                       // send drums to orbit 1
 * ```
 *
 * ```KlangScript
 * note("c3 e3").o(2).room(0.8)          // melodic line on orbit 2 with reverb
 * ```
 *
 * @param index The orbit index to route events to.
 *
 * @alias orbit
 * @category dynamics
 * @tags o, orbit, routing, effects, bus, channel
 */
@StrudelDsl
fun StrudelPattern.o(index: PatternLike? = null): StrudelPattern =
    this._o(listOfNotNull(index).asStrudelDslArgs())

/**
 * Alias for [orbit]. Parses this string as a pattern and routes it to the given orbit.
 *
 * ```KlangScript
 * "bd sd".o(1).s()   // send drums to orbit 1
 * ```
 *
 * @param index The orbit index to route events to.
 */
@StrudelDsl
fun String.o(index: PatternLike? = null): StrudelPattern =
    this._o(listOfNotNull(index).asStrudelDslArgs())

/**
 * Alias for [orbit]. Creates a [PatternMapperFn] that routes events to the given audio output orbit.
 *
 * ```KlangScript
 * s("bd sd").apply(o(1))   // send drums to orbit 1
 * ```
 *
 * @param index The orbit index to route events to.
 */
@StrudelDsl
fun o(index: PatternLike? = null): PatternMapperFn =
    _o(listOfNotNull(index).asStrudelDslArgs())

// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Ducking / Sidechain
// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

// -- duckorbit() / duck() ---------------------------------------------------------------------------------------------

private val duckOrbitMutation = voiceModifier {
    copy(duckOrbit = it?.asIntOrNull())
}

private fun applyDuckOrbit(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, duckOrbitMutation)
}

internal val StrudelPattern._duckorbit by dslPatternExtension { p, args, /* callInfo */ _ -> applyDuckOrbit(p, args) }
internal val String._duckorbit by dslStringExtension { p, args, callInfo -> p._duckorbit(args, callInfo) }
internal val _duckorbit by dslPatternMapper { args, callInfo -> { p -> p._duckorbit(args, callInfo) } }

internal val PatternMapperFn._duckorbit by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_duckorbit(args, callInfo))
}

internal val StrudelPattern._duck by dslPatternExtension { p, args, /* callInfo */ _ -> applyDuckOrbit(p, args) }
internal val String._duck by dslStringExtension { p, args, callInfo -> p._duck(args, callInfo) }
internal val _duck by dslPatternMapper { args, callInfo -> { p -> p._duck(args, callInfo) } }

internal val PatternMapperFn._duck by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_duck(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the target orbit to listen to for sidechain ducking.
 *
 * The pattern's volume is reduced when audio is detected on the specified orbit.
 * Use with `duckdepth` to set the attenuation amount and `duckattack` for the recovery time.
 *
 * ```KlangScript
 * s("bd*4").orbit(1)                              // kick drum on orbit 1
 * ```
 *
 * ```KlangScript
 * note("c3 e3 g3").duckorbit(1).duckdepth(0.8)   // duck when kick plays on orbit 1
 * ```
 *
 * @param orbitIndex The orbit index to listen to for the sidechain trigger.
 *
 * @alias duck
 * @category dynamics
 * @tags duckorbit, duck, sidechain, ducking, dynamics
 */
@StrudelDsl
fun StrudelPattern.duckorbit(orbitIndex: PatternLike? = null): StrudelPattern =
    this._duckorbit(listOfNotNull(orbitIndex).asStrudelDslArgs())

/**
 * Parses this string as a pattern and sets the sidechain source orbit for ducking.
 *
 * ```KlangScript
 * "c3 e3".duckorbit(1).duckdepth(0.8).note()   // duck when orbit 1 plays
 * ```
 *
 * @param orbitIndex The orbit index to listen to for the sidechain trigger.
 */
@StrudelDsl
fun String.duckorbit(orbitIndex: PatternLike? = null): StrudelPattern =
    this._duckorbit(listOfNotNull(orbitIndex).asStrudelDslArgs())

/**
 * Creates a [PatternMapperFn] that sets the sidechain source orbit for ducking.
 *
 * ```KlangScript
 * note("c3 e3 g3").apply(duckorbit(1)).duckdepth(0.8)   // duck when orbit 1 plays
 * ```
 *
 * @param orbitIndex The orbit index to listen to for the sidechain trigger.
 */
@StrudelDsl
fun duckorbit(orbitIndex: PatternLike? = null): PatternMapperFn =
    _duckorbit(listOfNotNull(orbitIndex).asStrudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets the sidechain source orbit after the previous mapper.
 *
 * ```KlangScript
 * note("c3 e3").apply(gain(0.8).duckorbit(1))  // gain + duckorbit chained
 * ```
 *
 * @param orbitIndex The orbit index to listen to for the sidechain trigger.
 */
@StrudelDsl
fun PatternMapperFn.duckorbit(orbitIndex: PatternLike? = null): PatternMapperFn =
    _duckorbit(listOfNotNull(orbitIndex).asStrudelDslArgs())

/**
 * Alias for [duckorbit]. Sets the target orbit to listen to for sidechain ducking.
 *
 * ```KlangScript
 * stack(
 *   s("bd*4").orbit(0),                               // kick drum on orbit 0
 *   note("c3 e3").orbit(1).duck(0).duckdepth(1.0),    // duck when kick plays on orbit 0
 * )
 * ```
 *
 * @param orbitIndex The orbit index to listen to for the sidechain trigger.
 *
 * @alias duckorbit
 * @category dynamics
 * @tags duck, duckorbit, sidechain, ducking, dynamics
 */
@StrudelDsl
fun StrudelPattern.duck(orbitIndex: PatternLike? = null): StrudelPattern =
    this._duck(listOfNotNull(orbitIndex).asStrudelDslArgs())

/**
 * Alias for [duckorbit]. Parses this string as a pattern and sets the sidechain source orbit.
 *
 * ```KlangScript
 * "c3 e3".duck(0).duckdepth(0.8).note()   // duck when orbit 0 plays
 * ```
 *
 * @param orbitIndex The orbit index to listen to for the sidechain trigger.
 */
@StrudelDsl
fun String.duck(orbitIndex: PatternLike? = null): StrudelPattern =
    this._duck(listOfNotNull(orbitIndex).asStrudelDslArgs())

/**
 * Alias for [duckorbit]. Creates a [PatternMapperFn] that sets the sidechain source orbit for ducking.
 *
 * ```KlangScript
 * note("c3 e3").apply(duck(0)).duckdepth(0.8)   // duck when orbit 0 plays
 * ```
 *
 * @param orbitIndex The orbit index to listen to for the sidechain trigger.
 */
@StrudelDsl
fun duck(orbitIndex: PatternLike? = null): PatternMapperFn =
    _duck(listOfNotNull(orbitIndex).asStrudelDslArgs())

/**
 * Alias for [duckorbit]. Creates a chained [PatternMapperFn] that sets the sidechain source orbit after the
 * previous mapper.
 *
 * ```KlangScript
 * note("c3 e3").apply(gain(0.8).duck(0))  // gain + duck chained
 * ```
 *
 * @param orbitIndex The orbit index to listen to for the sidechain trigger.
 */
@StrudelDsl
fun PatternMapperFn.duck(orbitIndex: PatternLike? = null): PatternMapperFn =
    _duck(listOfNotNull(orbitIndex).asStrudelDslArgs())

// -- duckattack() / duckatt() -----------------------------------------------------------------------------------------

private val duckAttackMutation = voiceModifier { copy(duckAttack = it?.asDoubleOrNull()) }

private fun applyDuckAttack(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, duckAttackMutation)
}

internal val StrudelPattern._duckattack by dslPatternExtension { p, args, /* callInfo */ _ -> applyDuckAttack(p, args) }
internal val String._duckattack by dslStringExtension { p, args, callInfo -> p._duckattack(args, callInfo) }
internal val _duckattack by dslPatternMapper { args, callInfo -> { p -> p._duckattack(args, callInfo) } }

internal val PatternMapperFn._duckattack by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_duckattack(args, callInfo))
}

internal val StrudelPattern._duckatt by dslPatternExtension { p, args, /* callInfo */ _ -> applyDuckAttack(p, args) }
internal val String._duckatt by dslStringExtension { p, args, callInfo -> p._duckatt(args, callInfo) }
internal val _duckatt by dslPatternMapper { args, callInfo -> { p -> p._duckatt(args, callInfo) } }

internal val PatternMapperFn._duckatt by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_duckatt(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the duck release (return-to-normal) time in seconds for sidechain ducking.
 *
 * Controls how quickly the ducked pattern returns to its full volume after the sidechain
 * trigger stops. Shorter values snap back quickly; longer values create a pumping effect.
 *
 * ```KlangScript
 * note("c3 e3").duck(1).duckdepth(0.8).duckattack(0.2)   // 200 ms recovery
 * ```
 *
 * ```KlangScript
 * note("c3*4").duckattack("<0.05 0.1 0.3 0.5>")          // varying recovery times
 * ```
 *
 * @param time The recovery time in seconds.
 *
 * @alias duckatt
 * @category dynamics
 * @tags duckattack, duckatt, sidechain, ducking, release, dynamics
 */
@StrudelDsl
fun StrudelPattern.duckattack(time: PatternLike? = null): StrudelPattern =
    this._duckattack(listOfNotNull(time).asStrudelDslArgs())

/**
 * Parses this string as a pattern and sets the duck release time.
 *
 * ```KlangScript
 * "c3*4".duckattack("<0.05 0.1 0.3 0.5>").note()   // varying recovery times
 * ```
 *
 * @param time The recovery time in seconds.
 */
@StrudelDsl
fun String.duckattack(time: PatternLike? = null): StrudelPattern =
    this._duckattack(listOfNotNull(time).asStrudelDslArgs())

/**
 * Creates a [PatternMapperFn] that sets the duck release time.
 *
 * ```KlangScript
 * note("c3 e3").apply(duckattack(0.2)).duck(1).duckdepth(0.8)   // 200 ms recovery
 * ```
 *
 * @param time The recovery time in seconds.
 */
@StrudelDsl
fun duckattack(time: PatternLike? = null): PatternMapperFn =
    _duckattack(listOfNotNull(time).asStrudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets the duck recovery time after the previous mapper.
 *
 * ```KlangScript
 * note("c3 e3").apply(duck(1).duckattack(0.2))  // duck + duckattack chained
 * ```
 *
 * @param time The recovery time in seconds.
 */
@StrudelDsl
fun PatternMapperFn.duckattack(time: PatternLike? = null): PatternMapperFn =
    _duckattack(listOfNotNull(time).asStrudelDslArgs())

/**
 * Alias for [duckattack]. Sets the duck release (return-to-normal) time in seconds.
 *
 * ```KlangScript
 * note("c3 e3").duck(1).duckdepth(0.8).duckatt(0.2)   // 200 ms recovery
 * ```
 *
 * ```KlangScript
 * note("c3*4").duckatt("<0.05 0.1 0.3 0.5>")          // varying recovery times
 * ```
 *
 * @param time The recovery time in seconds.
 *
 * @alias duckattack
 * @category dynamics
 * @tags duckatt, duckattack, sidechain, ducking, release, dynamics
 */
@StrudelDsl
fun StrudelPattern.duckatt(time: PatternLike? = null): StrudelPattern =
    this._duckatt(listOfNotNull(time).asStrudelDslArgs())

/**
 * Alias for [duckattack]. Parses this string as a pattern and sets the duck release time.
 *
 * ```KlangScript
 * "c3*4".duckatt("<0.05 0.1 0.3 0.5>").note()   // varying recovery times
 * ```
 *
 * @param time The recovery time in seconds.
 */
@StrudelDsl
fun String.duckatt(time: PatternLike? = null): StrudelPattern =
    this._duckatt(listOfNotNull(time).asStrudelDslArgs())

/**
 * Alias for [duckattack]. Creates a [PatternMapperFn] that sets the duck release time.
 *
 * ```KlangScript
 * note("c3 e3").apply(duckatt(0.2)).duck(1).duckdepth(0.8)   // 200 ms recovery
 * ```
 *
 * @param time The recovery time in seconds.
 */
@StrudelDsl
fun duckatt(time: PatternLike? = null): PatternMapperFn =
    _duckatt(listOfNotNull(time).asStrudelDslArgs())

/**
 * Alias for [duckattack]. Creates a chained [PatternMapperFn] that sets the duck recovery time after the previous
 * mapper.
 *
 * ```KlangScript
 * note("c3 e3").apply(duck(1).duckatt(0.2))  // duck + duckatt chained
 * ```
 *
 * @param time The recovery time in seconds.
 */
@StrudelDsl
fun PatternMapperFn.duckatt(time: PatternLike? = null): PatternMapperFn =
    _duckatt(listOfNotNull(time).asStrudelDslArgs())

// -- duckdepth() ------------------------------------------------------------------------------------------------------

private val duckDepthMutation = voiceModifier { copy(duckDepth = it?.asDoubleOrNull()) }

private fun applyDuckDepth(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, duckDepthMutation)
}

internal val StrudelPattern._duckdepth by dslPatternExtension { p, args, /* callInfo */ _ -> applyDuckDepth(p, args) }
internal val String._duckdepth by dslStringExtension { p, args, callInfo -> p._duckdepth(args, callInfo) }
internal val _duckdepth by dslPatternMapper { args, callInfo -> { p -> p._duckdepth(args, callInfo) } }

internal val PatternMapperFn._duckdepth by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_duckdepth(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the ducking depth (0.0 = no ducking, 1.0 = full silence) for sidechain ducking.
 *
 * Controls how much the pattern is attenuated when the sidechain trigger fires.
 * Use with `duckorbit` to set the sidechain source and `duckattack` for recovery time.
 *
 * ```KlangScript
 * note("c3 e3").duck(1).duckdepth(0.8)           // 80% attenuation on sidechain
 * ```
 *
 * ```KlangScript
 * note("c3*4").duckdepth("<0.3 0.6 0.9 1.0>")   // escalating ducking depth
 * ```
 *
 * @param amount The ducking depth between 0.0 (no ducking) and 1.0 (full silence).
 *
 * @category dynamics
 * @tags duckdepth, sidechain, ducking, attenuation, dynamics
 */
@StrudelDsl
fun StrudelPattern.duckdepth(amount: PatternLike? = null): StrudelPattern =
    this._duckdepth(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Parses this string as a pattern and sets the ducking depth.
 *
 * ```KlangScript
 * "c3*4".duckdepth("<0.3 0.6 0.9 1.0>").note()   // escalating ducking depth
 * ```
 *
 * @param amount The ducking depth between 0.0 (no ducking) and 1.0 (full silence).
 */
@StrudelDsl
fun String.duckdepth(amount: PatternLike? = null): StrudelPattern =
    this._duckdepth(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Creates a [PatternMapperFn] that sets the ducking depth.
 *
 * ```KlangScript
 * note("c3*4").apply(duckdepth("<0.3 0.6 0.9 1.0>"))   // escalating ducking depth
 * ```
 *
 * @param amount The ducking depth between 0.0 (no ducking) and 1.0 (full silence).
 */
@StrudelDsl
fun duckdepth(amount: PatternLike? = null): PatternMapperFn =
    _duckdepth(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets the ducking depth after the previous mapper.
 *
 * ```KlangScript
 * note("c3*4").apply(duck(1).duckdepth(0.8))  // duck + duckdepth chained
 * ```
 *
 * @param amount The ducking depth between 0.0 (no ducking) and 1.0 (full silence).
 */
@StrudelDsl
fun PatternMapperFn.duckdepth(amount: PatternLike? = null): PatternMapperFn =
    _duckdepth(listOfNotNull(amount).asStrudelDslArgs())
