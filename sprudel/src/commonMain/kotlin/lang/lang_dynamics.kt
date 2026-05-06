@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel._applyControlFromParams
import io.peekandpoke.klang.sprudel._liftNumericField
import io.peekandpoke.klang.sprudel._liftOrReinterpretNumericalField
import io.peekandpoke.klang.sprudel._liftOrReinterpretStringField
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.withOscParam
// -- gain() -----------------------------------------------------------------------------------------------------------

private val gainMutation = voiceModifier { copy(gain = it?.asDoubleOrNull()) }

private fun applyGain(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, gainMutation)
}

/**
 * Sets the gain (volume multiplier) for each event in the pattern.
 *
 * Values below 1 reduce volume; above 1 amplify. Accepts control patterns for per-event modulation.
 *
 * ```KlangScript(Playable)
 * s("bd sd hh cp").gain(0.5)              // all hits at half volume
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd*4").gain("<0.2 0.5 0.8 1.0>")    // different gain each cycle
 * ```
 *
 * @param amount The control value to use for gain.
 * @param-tool amount SprudelGainSequenceEditor
 *
 * @category dynamics
 * @tags gain, volume, amplitude, dynamics
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.gain(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyGain(this, listOfNotNull(amount).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets the gain for each event.
 *
 * ```KlangScript(Playable)
 * "bd*4".gain("0.2 0.5 0.8 1.0").s()    // different gain each beat
 * ```
 *
 * @param amount The control value to use for gain.
 */
@SprudelDsl
@KlangScript.Function
fun String.gain(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).gain(amount, callInfo)

/**
 * Creates a [PatternMapperFn] that sets the gain for each event in a pattern.
 *
 * ```KlangScript(Playable)
 * s("hh hh hh hh").apply(gain("1.0 0.75 0.5 0.25"))
 * ```
 *
 * @param amount The control value to use for gain.
 */
@SprudelDsl
@KlangScript.Function
fun gain(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.gain(amount, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the gain after the previous mapper.
 *
 * ```KlangScript(Playable)
 * s("hh*4").apply(gain("1.0 0.5").gain(0.8))  // chain gain modifiers
 * ```
 *
 * @param amount The control value to use for gain.
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.gain(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.gain(amount, callInfo) }

// -- pan() ------------------------------------------------------------------------------------------------------------

private val panMutation = voiceModifier { copy(pan = it?.asDoubleOrNull()) }

private fun applyPan(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, panMutation)
}

/**
 * Sets the stereo panning position for each event (0 = full left, 0.5 = centre, 1 = full right).
 *
 * Accepts control patterns or continuous patterns for animated panning effects.
 *
 * ```KlangScript(Playable)
 * s("bd sd").pan(0.25)                   // slightly left
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd hh sd cp").pan("0 0.33 0.66 1")  // left to right
 * ```
 *
 * ```KlangScript(Playable)
 * s("hh*8").pan(sine.range(0, 1))        // smooth left-right sweep
 * ```
 *
 * @param amount The panning position for each event, ranging from 0 (full left) to 1 (full right).
 * @param-tool amount SprudelPanSequenceEditor
 *
 * @category dynamics
 * @tags pan, stereo, panning, position
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.pan(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyPan(this, listOfNotNull(amount).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets the stereo panning position.
 *
 * ```KlangScript(Playable)
 * "bd hh sd cp".pan("0 0.33 0.66 1").s()  // left to right
 * ```
 *
 * @param amount The panning position for each event, ranging from 0 (full left) to 1 (full right).
 */
@SprudelDsl
@KlangScript.Function
fun String.pan(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).pan(amount, callInfo)

/**
 * Creates a [PatternMapperFn] that sets the pan for each event in a pattern.
 *
 * ```KlangScript(Playable)
 * s("bd hh sd cp").apply(pan("0 0.33 0.66 1"))  // left to right
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun pan(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.pan(amount, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the pan after the previous mapper.
 *
 * ```KlangScript(Playable)
 * s("bd hh sd cp").apply(pan("0 0.33 0.66 1").gain(0.8))  // pan + gain chained
 * ```
 *
 * @param amount The panning position for each event, ranging from 0 (full left) to 1 (full right).
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.pan(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.pan(amount, callInfo) }

// -- velocity() / vel() -----------------------------------------------------------------------------------------------

private val velocityMutation = voiceModifier { copy(velocity = it?.asDoubleOrNull()) }

private fun applyVelocity(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, velocityMutation)
}

/**
 * Sets the gain 'velocity'. It is multiplied with the gain of the events.
 *
 * ```KlangScript(Playable)
 * note("c d e f").gain(0.5).velocity("0.5 2.0")  // gain is multiplied by velocity
 * ```
 *
 * ```KlangScript(Playable)
 * note("c*4").velocity("<0.3 0.6 0.9 1.0>")  // crescendo pattern
 * ```
 *
 * ```KlangScript(Playable)
 * note("c*4").velocity(saw.range(0.25, 1.0).slow(4))  // crescendo pattern over 4 cycles
 * ```
 *
 * @param amount The velocity value or pattern to apply to the events.
 *
 * @alias vel
 * @category dynamics
 * @tags velocity, vel, volume, midi, dynamics
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.velocity(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyVelocity(this, listOfNotNull(amount).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets the velocity (gain multiplier) for each event.
 *
 * ```KlangScript(Playable)
 * "c*4".velocity("<0.3 0.6 0.9 1.0>").note()  // crescendo pattern
 * ```
 *
 * @param amount The velocity value or pattern to apply to the events.
 */
@SprudelDsl
@KlangScript.Function
fun String.velocity(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).velocity(amount, callInfo)

/**
 * Create a [PatternMapperFn] that sets the velocity (gain multiplier) for each event in a pattern.
 *
 * ```KlangScript(Playable)
 * note("c*4").apply(velocity("<0.3 0.6 0.9 1.0>"))  // crescendo pattern
 * ```
 *
 * @param amount The velocity value or pattern to apply to the events.
 */
@SprudelDsl
@KlangScript.Function
fun velocity(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.velocity(amount, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the velocity after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c*4").apply(velocity("<0.3 0.6 0.9>").gain(0.8))  // velocity + gain chained
 * ```
 *
 * @param amount The velocity value or pattern to apply to the events.
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.velocity(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.velocity(amount, callInfo) }

/**
 * Alias for [velocity]. Sets the gain 'velocity'. It is multiplied with the gain of the events.
 *
 * ```KlangScript(Playable)
 * note("c d e f").gain(0.5).vel("0.5 2.0")   // gain is multiplied by velocity
 * ```
 *
 * ```KlangScript(Playable)
 * note("c*4").vel(saw.range(0.25, 1.0).slow(4))   // crescendo pattern over 4 cycles
 * ```
 *
 * @param amount The velocity value or pattern to apply to the events.
 *
 * @alias velocity
 * @category dynamics
 * @tags vel, velocity, volume, midi, dynamics
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.vel(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.velocity(amount, callInfo)

/**
 * Alias for [velocity]. Sets the velocity (gain multiplier) for each event in this pattern.
 *
 * ```KlangScript(Playable)
 * "c*4".vel("<0.3 0.6 0.9 1.0>").note()  // crescendo pattern
 * ```
 *
 * @param amount The velocity value or pattern to apply to the events.
 */
@SprudelDsl
@KlangScript.Function
fun String.vel(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).velocity(amount, callInfo)

/**
 * Alias for [velocity]. Create a [PatternMapperFn] that sets the velocity (gain multiplier) for each event in a pattern.
 *
 * ```KlangScript(Playable)
 * note("c*4").apply(vel("<0.3 0.6 0.9 1.0>"))  // crescendo pattern
 * ```
 *
 * @param amount The velocity value or pattern to apply to the events.
 */
@SprudelDsl
@KlangScript.Function
fun vel(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.velocity(amount, callInfo) }

/**
 * Alias for [velocity]. Creates a chained [PatternMapperFn] that sets the velocity after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c*4").apply(vel("<0.3 0.6 0.9>").gain(0.8))  // velocity + gain chained
 * ```
 *
 * @param amount The velocity value or pattern to apply to the events.
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.vel(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.velocity(amount, callInfo) }

// -- postgain() -------------------------------------------------------------------------------------------------------

private val postgainMutation = voiceModifier { copy(postGain = it?.asDoubleOrNull()) }

private fun applyPostgain(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, postgainMutation)
}

/**
 * Sets the post-gain (applied after voice processing) for each event in the pattern.
 *
 * Unlike `gain` which is applied before synthesis, `postgain` is a final output multiplier.
 *
 * ```KlangScript(Playable)
 * s("bd sd").postgain(1.5)                    // amplify after processing
 * ```
 *
 * ```KlangScript(Playable)
 * s("hh*8").postgain(rand.range(0.1, 1.0))   // random post-gain per hit
 * ```
 *
 * @param amount The post-gain value or pattern to apply to the events.
 *
 * @category dynamics
 * @tags postgain, gain, volume, post-processing
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.postgain(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyPostgain(this, listOfNotNull(amount).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets the post-gain for each event.
 *
 * ```KlangScript(Playable)
 * "hh*8".postgain(perlin.range(0.1, 1.0).slow(4)).s()   // perlin noised post-gain
 * ```
 *
 * @param amount The post-gain value or pattern to apply to the events.
 */
@SprudelDsl
@KlangScript.Function
fun String.postgain(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).postgain(amount, callInfo)

/**
 * Create a [PatternMapperFn] that sets the post-gain for each event in a pattern.
 *
 * ```KlangScript(Playable)
 * "hh*8".apply(postgain(sine.range(0.1, 1.0).slow(2))).s()   // sine post-gain over two cycles
 * ```
 *
 * @param amount The post-gain value or pattern to apply to the events.
 */
@SprudelDsl
@KlangScript.Function
fun postgain(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.postgain(amount, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the post-gain after the previous mapper.
 *
 * ```KlangScript(Playable)
 * s("hh*4").apply(postgain(0.8).gain(0.5))  // postgain + gain chained
 * ```
 *
 * @param amount The post-gain value or pattern to apply to the events.
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.postgain(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.postgain(amount, callInfo) }

// -- compressor() / comp() --------------------------------------------------------------------------------------------

private val compressorMutation = voiceModifier { copy(compressor = it?.toString()) }

private fun applyCompressor(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretStringField(args, compressorMutation)
}

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
 * ```KlangScript(Playable)
 * s("bd sd").compressor("-20:4:3:0.03:0.1")  // standard compression
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd*4").compressor("<-10:2:1:0.01:0.1 -30:8:5:0.005:0.5>")   // alternate settings
 * ```
 *
 * ```KlangScript(Playable)
 * // Shorthand: only threshold and ratio (defaults: knee=6.0, attack=0.003, release=0.1)
 * s("hh*8").compressor("-15:4")
 * ```
 *
 * @param params The compression parameters as a colon-separated string.
 *
 * @param-tool params SprudelCompressorSequenceEditor
 * @param-sub params threshold Level in dB above which compression starts (e.g. -20)
 * @param-sub params ratio Compression ratio (e.g. 4 means 4:1 reduction above threshold)
 * @param-sub params knee Smoothness of compression onset in dB (0 = hard knee, 6+ = soft)
 * @param-sub params attack How quickly compression engages, in seconds (e.g. 0.003)
 * @param-sub params release How quickly compression releases, in seconds (e.g. 0.1)
 * @alias comp
 * @category dynamics
 * @tags compressor, comp, compression, threshold, ratio, dynamics
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.compressor(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyCompressor(this, listOfNotNull(params).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets dynamic range compression parameters.
 *
 * ```KlangScript(Playable)
 * s("bd*4").compressor("<-10:2:1:0.01:0.1 -30:8:5:0.005:0.5>")   // alternate settings
 * ```
 *
 * @param params The compression parameters as a colon-separated string.
 */
@SprudelDsl
@KlangScript.Function
fun String.compressor(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).compressor(params, callInfo)

/**
 * Create a [PatternMapperFn] that sets dynamic range compression parameters for a pattern.
 *
 * ```KlangScript(Playable)
 * s("bd*4").apply(compressor("<-10:2:1:0.01:0.1 -30:8:5:0.005:0.5>"))   // alternate settings
 * ```

 */
@SprudelDsl
@KlangScript.Function
fun compressor(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.compressor(params, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets compressor parameters after the previous mapper.
 *
 * ```KlangScript(Playable)
 * s("bd*4").apply(compressor("-20:4:3:0.03:0.1").gain(0.8))  // compress + gain chained
 * ```
 *
 * @param params The compression parameters as a colon-separated string.
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.compressor(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.compressor(params, callInfo) }

/**
 * Alias for [compressor]. Sets dynamic range compression parameters as a colon-separated string
 * `"threshold:ratio:knee:attack:release"`.
 *
 * ```KlangScript(Playable)
 * s("bd sd").comp("-20:4:3:0.01:0.3")                        // standard compression
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd*4").comp("<-10:2:1:0.01:0.1 -30:8:5:0.005:0.5>")   // alternate settings
 * ```
 *
 * @param params The compression parameters as a colon-separated string.
 *
 * @param-tool params SprudelCompressorSequenceEditor
 * @alias compressor
 * @category dynamics
 * @tags comp, compressor, compression, threshold, ratio, dynamics
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.comp(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.compressor(params, callInfo)

/**
 * Alias for [compressor]. Parses this string as a pattern and sets compression parameters.
 *
 * ```KlangScript(Playable)
 * s("bd*4").comp("<-10:2:1:0.01:0.1 -30:8:5:0.005:0.5>")   // alternate settings
 * ```
 *
 * @param params The compression parameters as a colon-separated string.
 */
@SprudelDsl
@KlangScript.Function
fun String.comp(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).compressor(params, callInfo)

/**
 * Alias for [compressor]. Parses this string as a pattern and sets compression parameters.
 *
 * ```KlangScript(Playable)
 * s("bd*4").apply(comp("<-10:2:1:0.01:0.1 -30:8:5:0.005:0.5>"))   // alternate settings
 * ```
 *
 * @param params The compression parameters as a colon-separated string.
 */
@SprudelDsl
@KlangScript.Function
fun comp(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.compressor(params, callInfo) }

/**
 * Alias for [compressor]. Creates a chained [PatternMapperFn] that sets compressor parameters after the previous
 * mapper.
 *
 * ```KlangScript(Playable)
 * s("bd*4").apply(comp("-20:4:3:0.03:0.1").gain(0.8))  // compress + gain chained
 * ```
 *
 * @param params The compression parameters as a colon-separated string.
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.comp(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.compressor(params, callInfo) }

// -- unison() / uni() -------------------------------------------------------------------------------------------------

private val unisonMutation = voiceModifier { withOscParam("voices", it?.asDoubleOrNull()) }

private fun applyUnison(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretStringField(args, unisonMutation)
}

/**
 * Sets the number of unison voices for oscillator stacking effects (e.g. supersaw).
 *
 * Higher values produce a thicker, chorus-like sound. Use with `detune` and `spread`
 * to control the detuning and panning spread of the voices.
 *
 * ```KlangScript(Playable)
 * note("c3").s("supersaw").unison(5)               // 5 stacked sawtooth oscillators
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").s("supersaw").unison("<3 6 10 16>").detune(0.3)  // unison pattern
 * ```
 *
 * @param voices The number of unison voices.
 *
 * @alias uni
 * @category dynamics
 * @tags unison, uni, voices, stacking, supersaw
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.unison(voices: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyUnison(this, listOfNotNull(voices).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets the number of unison voices.
 *
 * ```KlangScript(Playable)
 * "c3 e3 g3".s("supersaw").unison("<1 5 10 16>").detune(0.3).note()  // unison pattern
 * ```
 *
 * @param voices The number of unison voices.
 */
@SprudelDsl
@KlangScript.Function
fun String.unison(voices: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).unison(voices, callInfo)

/**
 * Create a [PatternMapperFn] that sets the number of unison voices for a pattern.
 *
 * ```KlangScript(Playable)
 * "c3 e3 g3".s("supersaw").apply(unison("<1 5 10 16>")).detune(0.3).note()  // unison pattern
 * ```
 *
 * @param voices The number of unison voices.
 */
@SprudelDsl
@KlangScript.Function
fun unison(voices: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.unison(voices, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the number of unison voices after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3").s("supersaw").apply(unison(5).detune(0.3))  // unison + detune chained
 * ```
 *
 * @param voices The number of unison voices.
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.unison(voices: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.unison(voices, callInfo) }

/**
 * Alias for [unison]. Sets the number of unison voices for oscillator stacking effects.
 *
 * ```KlangScript(Playable)
 * note("c3").s("supersaw").uni(5)               // 5 stacked sawtooth oscillators
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").s("supersaw").uni("<1 5 10 16>").detune(0.3)  // unison pattern
 * ```
 *
 * @param voices The number of unison voices.
 *
 * @alias unison
 * @category dynamics
 * @tags uni, unison, voices, stacking, supersaw
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.uni(voices: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.unison(voices, callInfo)

/**
 * Alias for [unison]. Parses this string as a pattern and sets the number of unison voices.
 *
 * ```KlangScript(Playable)
 * "c3 e3 g3".s("supersaw").uni("<1 5 10 16>").detune(0.3).note()  // unison pattern
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun String.uni(voices: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).unison(voices, callInfo)

/**
 * Alias for [unison]. Creates a [PatternMapperFn] that sets the number of unison voices for a pattern.
 *
 * ```KlangScript(Playable)
 * "c3 e3 g3".s("supersaw").apply(unison("<1 5 10 16>")).detune(0.3).note()  // unison pattern
 * ```
 *
 * @param voices The number of unison voices.
 */
@SprudelDsl
@KlangScript.Function
fun uni(voices: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.unison(voices, callInfo) }

/**
 * Alias for [unison]. Creates a chained [PatternMapperFn] that sets the number of unison voices after the previous
 * mapper.
 *
 * ```KlangScript(Playable)
 * note("c3").s("supersaw").apply(uni(5).detune(0.3))  // unison + detune chained
 * ```
 *
 * @param voices The number of unison voices.
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.uni(voices: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.unison(voices, callInfo) }

// -- detune() ---------------------------------------------------------------------------------------------------------

private val detuneMutation = voiceModifier { withOscParam("freqSpread", it?.asDoubleOrNull()) }

private fun applyDetune(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretStringField(args, detuneMutation)
}

/**
 * Sets the oscillator frequency spread in cents for unison/supersaw effects.
 *
 * Controls how much each unison voice is detuned from the base pitch. Use with `unison`
 * to set the number of voices. Higher values produce a wider, more detuned sound.
 *
 * ```KlangScript(Playable)
 * note("c3").s("supersaw").unison(5).detune(0.5)   // 5 voices spread 0.5 half tones
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").s("supersaw").detune("<0.05 0.10 0.20 0.40>")  // escalating detune each beat
 * ```
 *
 * @param amount The detuning in cents.
 *
 * @category dynamics
 * @tags detune, spread, unison, cents, supersaw
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.detune(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyDetune(this, listOfNotNull(amount).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets the oscillator frequency spread.
 *
 * ```KlangScript(Playable)
 * "c3*4".detune("<0.05 0.10 0.20 0.40>").s("supersaw").note() // escalating detune each beat
 * ```
 *
 * @param amount The detuning in cents.
 */
@SprudelDsl
@KlangScript.Function
fun String.detune(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).detune(amount, callInfo)

/**
 * Creates a [PatternMapperFn] that sets the oscillator frequency spread for a pattern.
 *
 * ```KlangScript(Playable)
 * note("c3*4").s("supersaw").apply(detune("<0.05 0.10 0.20 0.40>"))  // escalating detune each beat
 * ```
 * @param amount The detuning in cents.
 */
@SprudelDsl
@KlangScript.Function
fun detune(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.detune(amount, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the oscillator frequency spread after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3").s("supersaw").apply(unison(5).detune(0.3))  // unison + detune chained
 * ```
 *
 * @param amount The detuning in cents.
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.detune(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.detune(amount, callInfo) }

// -- spread() ---------------------------------------------------------------------------------------------------------

private val spreadMutation = voiceModifier { withOscParam("panSpread", it?.asDoubleOrNull()) }

private fun applySpread(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretStringField(args, spreadMutation)
}

/**
 * Sets the stereo pan spread for unison/supersaw voices (0 = mono, 1 = full stereo spread).
 *
 * Controls how widely the unison voices are spread across the stereo field. Use with
 * `unison` to set the number of voices.
 *
 * ```KlangScript(Playable)
 * note("c3").s("supersaw").unison(5).spread(0.8)   // wide stereo spread
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").s("supersaw").spread("<0.2 0.5 0.8 1.0>")         // gradually widen each beat
 * ```
 *
 * @param amount The stereo pan spread, between 0 and 1.
 *
 * @category dynamics
 * @tags spread, pan, stereo, unison, supersaw
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.spread(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applySpread(this, listOfNotNull(amount).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets the stereo pan spread for unison voices.
 *
 * ```KlangScript(Playable)
 * "c3*4".spread("<0.2 0.5 0.8 1.0>").s("supersaw").note()         // gradually widen each beat
 * ```
 *
 * @param amount The stereo pan spread, between 0 and 1.
 */
@SprudelDsl
@KlangScript.Function
fun String.spread(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).spread(amount, callInfo)

/**
 * Creates a [PatternMapperFn] that sets the stereo pan spread for unison voices.
 *
 * ```KlangScript(Playable)
 * "c3*4".apply(spread("<0.2 0.5 0.8 1.0>")).s("supersaw").note()         // gradually widen each beat
 * ```
 *
 * @param amount The stereo pan spread, between 0 and 1.
 */
@SprudelDsl
@KlangScript.Function
fun spread(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.spread(amount, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the stereo pan spread after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3").s("supersaw").apply(unison(5).spread(0.8))  // unison + spread chained
 * ```
 *
 * @param amount The stereo pan spread, between 0 and 1.
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.spread(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.spread(amount, callInfo) }

// -- density() / d() --------------------------------------------------------------------------------------------------

private val densityMutation = voiceModifier { withOscParam("density", it?.asDoubleOrNull()) }

private fun applyDensity(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretStringField(args, densityMutation)
}

/**
 * Sets the oscillator density for supersaw or noise density for dust/crackle generators.
 *
 * For supersaw: controls how tightly packed the oscillators are.
 * For noise generators (e.g. `dust`): controls the number of events per second.
 *
 * ```KlangScript(Playable)
 * note("a").s("dust").density(40)   // 40 noise events per second
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3").s("supersaw").unison(7).density("<0 0.5 1 2>")  // tight supersaw
 * ```
 *
 * @param amount The oscillator density.
 *
 * @alias d
 * @category dynamics
 * @tags density, d, supersaw, dust, noise
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.density(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyDensity(this, listOfNotNull(amount).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets the oscillator or noise density.
 *
 * ```KlangScript(Playable)
 * "a".density(40).s("dust").note()   // 40 noise events per second
 * ```
 *
 * @param amount The oscillator density.
 */
@SprudelDsl
@KlangScript.Function
fun String.density(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).density(amount, callInfo)

/**
 * Parses this string as a pattern and sets the oscillator or noise density.
 *
 * ```KlangScript(Playable)
 * "a".apply(density(40)).s("dust").note()   // 40 noise events per second
 * ```
 * @param amount The oscillator density.
 */
@SprudelDsl
@KlangScript.Function
fun density(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.density(amount, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the oscillator or noise density after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3").s("supersaw").apply(unison(7).density(0.5))  // unison + density chained
 * ```
 *
 * @param amount The oscillator density.
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.density(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.density(amount, callInfo) }

/**
 * Alias for [density]. Sets the oscillator density for supersaw or noise density for dust/crackle.
 *
 * ```KlangScript(Playable)
 * note("a").s("dust").d(40)   // 40 noise events per second
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3").s("supersaw").unison(7).d(0.5)   // tight supersaw
 * ```
 *
 * @param amount The oscillator density. Integer, typically 1-16. Higher values produce denser sound.
 *
 * @alias density
 * @category dynamics
 * @tags d, density, supersaw, dust, noise
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.d(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.density(amount, callInfo)

/**
 * Alias for [density]. Parses this string as a pattern and sets the oscillator or noise density.
 *
 * ```KlangScript(Playable)
 * "a".d(40).s("dust").note()   // 40 noise events per second
 * ```
 *
 * @param amount The oscillator density.
 */
@SprudelDsl
@KlangScript.Function
fun String.d(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).density(amount, callInfo)

/**
 * Alias for [density]. Creates a [PatternMapperFn] that sets the oscillator or noise density.
 *
 * ```KlangScript(Playable)
 * note("a").apply(d(40)).s("dust")   // 40 noise events per second
 * ```
 *
 * @param amount The oscillator density.
 */
@SprudelDsl
@KlangScript.Function
fun d(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.density(amount, callInfo) }

/**
 * Alias for [density]. Creates a chained [PatternMapperFn] that sets the oscillator or noise density after the
 * previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3").s("supersaw").apply(unison(7).d(0.5))  // unison + density chained
 * ```
 *
 * @param amount The oscillator density.
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.d(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.density(amount, callInfo) }

// -- ADSR attack() ----------------------------------------------------------------------------------------------------

private val attackMutation = voiceModifier { copy(attack = it?.asDoubleOrNull()) }

private fun applyAttack(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretStringField(args, attackMutation)
}

/**
 * Sets the ADSR envelope attack time in seconds for synthesised notes.
 *
 * Controls how quickly the note rises from silence to full volume at the start.
 * Short values produce a sharp, percussive onset; longer values create a gradual fade-in.
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").s("sine").attack(0.01)     // sharp attack
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").attack("<0.01 0.1 0.5 1.0>")   // varying attacks
 * ```
 *
 * @param time The attack time in seconds.
 *
 * @category dynamics
 * @tags attack, adsr, envelope, fade-in
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.attack(time: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyAttack(this, listOfNotNull(time).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets the ADSR envelope attack time.
 *
 * ```KlangScript(Playable)
 * "c3*4".attack("<0.01 0.1 0.5 1.0>").note()  // varying attacks
 * ```
 *
 * @param time The attack time in seconds.
 */
@SprudelDsl
@KlangScript.Function
fun String.attack(time: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).attack(time, callInfo)

/**
 * Creates a [PatternMapperFn] that sets the ADSR envelope attack time for each event.
 *
 * ```KlangScript(Playable)
 * note("c3*4").s("sine").apply(attack("<0.01 0.1 0.5 1.0>"))  // varying attacks
 * ```
 *
 * @param time The attack time in seconds.
 */
@SprudelDsl
@KlangScript.Function
fun attack(time: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.attack(time, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the ADSR attack time after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3*4").s("sine").apply(attack(0.1).decay(0.2))  // attack + decay chained
 * ```
 *
 * @param time The attack time in seconds.
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.attack(time: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.attack(time, callInfo) }

// -- ADSR decay() -----------------------------------------------------------------------------------------------------

private val decayMutation = voiceModifier { copy(decay = it?.asDoubleOrNull()) }

private fun applyDecay(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretStringField(args, decayMutation)
}

/**
 * Sets the ADSR envelope decay time in seconds for synthesised notes.
 *
 * Controls how quickly the volume falls from its peak to the sustain level after the attack phase.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("sawtooth").decay(0.2)       // short decay
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").decay("<0.05 0.2 0.5 1.0>")    // varying decays
 * ```
 *
 * @param time The decay time in seconds.
 *
 * @category dynamics
 * @tags decay, adsr, envelope
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.decay(time: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyDecay(this, listOfNotNull(time).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets the ADSR envelope decay time.
 *
 * ```KlangScript(Playable)
 * "c3*4".decay("<0.05 0.2 0.5 1.0>").note()   // varying decays
 * ```
 *
 * @param time The decay time in seconds.
 */
@SprudelDsl
@KlangScript.Function
fun String.decay(time: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).decay(time, callInfo)

/**
 * Creates a [PatternMapperFn] that sets the ADSR envelope decay time for each event.
 *
 * ```KlangScript(Playable)
 * note("c3*4").s("sawtooth").apply(decay("<0.05 0.2 0.5 1.0>"))  // varying decays
 * ```
 *
 * @param time The decay time in seconds.
 */
@SprudelDsl
@KlangScript.Function
fun decay(time: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.decay(time, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the ADSR decay time after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3*4").s("sine").apply(attack(0.01).decay(0.2))  // attack + decay chained
 * ```
 *
 * @param time The decay time in seconds.
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.decay(time: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.decay(time, callInfo) }

// -- ADSR sustain() ---------------------------------------------------------------------------------------------------

private val sustainMutation = voiceModifier { copy(sustain = it?.asDoubleOrNull()) }

private fun applySustain(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretStringField(args, sustainMutation)
}

/**
 * Sets the ADSR envelope sustain level (0–1) for synthesised notes.
 *
 * The sustain level is held while the note is pressed, after the attack and decay phases.
 * `0` = silence after decay; `1` = hold at full peak level.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("sine").sustain(0.7)        // 70% sustain level
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").sustain("<0 0.3 0.7 1.0>")    // varying sustain
 * ```
 *
 * @param level The sustain level between 0 and 1.
 *
 * @category dynamics
 * @tags sustain, adsr, envelope, hold
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.sustain(level: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applySustain(this, listOfNotNull(level).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets the ADSR envelope sustain level.
 *
 * ```KlangScript(Playable)
 * "c3*4".sustain("<0 0.3 0.7 1.0>").note()   // varying sustain
 * ```
 *
 * @param level The sustain level between 0 and 1.
 */
@SprudelDsl
@KlangScript.Function
fun String.sustain(level: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).sustain(level, callInfo)

/**
 * Creates a [PatternMapperFn] that sets the ADSR envelope sustain level for each event.
 *
 * ```KlangScript(Playable)
 * note("c3*4").s("sine").apply(sustain("<0 0.3 0.7 1.0>"))  // varying sustain
 * ```
 *
 * @param level The sustain level between 0 and 1.
 */
@SprudelDsl
@KlangScript.Function
fun sustain(level: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.sustain(level, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the ADSR sustain level after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3*4").s("sine").apply(attack(0.01).sustain(0.7))  // attack + sustain chained
 * ```
 *
 * @param level The sustain level between 0 and 1.
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.sustain(level: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.sustain(level, callInfo) }

// -- ADSR release() ---------------------------------------------------------------------------------------------------

private val releaseMutation = voiceModifier { copy(release = it?.asDoubleOrNull()) }

private fun applyRelease(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretStringField(args, releaseMutation)
}

/**
 * Sets the ADSR envelope release time in seconds for synthesised notes.
 *
 * Controls how long the note takes to fade to silence after a note-off event.
 * Short values produce an abrupt cut; longer values create a smooth fade-out.
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").s("sine").release(0.5)     // half-second release
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").release("<0.1 0.3 0.8 2.0>")  // varying releases
 * ```
 *
 * @param time The release time in seconds.
 *
 * @category dynamics
 * @tags release, adsr, envelope, fade-out
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.release(time: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyRelease(this, listOfNotNull(time).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets the ADSR envelope release time.
 *
 * ```KlangScript(Playable)
 * "c3*4".release("<0.1 0.3 0.8 2.0>").note()  // varying releases
 * ```
 *
 * @param time The release time in seconds.
 */
@SprudelDsl
@KlangScript.Function
fun String.release(time: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).release(time, callInfo)

/**
 * Creates a [PatternMapperFn] that sets the ADSR envelope release time for each event.
 *
 * ```KlangScript(Playable)
 * note("c3*4").s("sine").apply(release("<0.1 0.3 0.8 2.0>"))  // varying releases
 * ```
 *
 * @param time The release time in seconds.
 */
@SprudelDsl
@KlangScript.Function
fun release(time: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.release(time, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the ADSR release time after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3*4").s("sine").apply(attack(0.01).release(0.5))  // attack + release chained
 * ```
 *
 * @param time The release time in seconds.
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.release(time: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.release(time, callInfo) }

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

private fun applyAdsr(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._applyControlFromParams(args, adsrMutation) { src, ctrl ->
        src.copy(
            attack = ctrl.attack ?: src.attack,
            decay = ctrl.decay ?: src.decay,
            sustain = ctrl.sustain ?: src.sustain,
            release = ctrl.release ?: src.release,
        )
    }
}

/**
 * Sets all four ADSR envelope parameters at once via a colon-separated string
 * `"attack:decay:sustain:release"`.
 *
 * Each field is a number: attack/decay/release in seconds, sustain in 0–1 range.
 * Missing trailing fields keep their previous values.
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").s("sine").adsr("0.01:0.2:0.7:0.5")          // standard ADSR
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").adsr("<0.01:0.1:0.5:0.2 0.5:0.5:0.8:1.0>")     // alternate envelopes
 * ```
 *
 * @param params The ADSR parameters as a colon-separated string `"attack:decay:sustain:release"`.
 * @param-tool params SprudelAdsrSequenceEditor
 * @param-sub params attack Attack time in seconds — how quickly the note rises from silence to full volume
 * @param-sub params decay Decay time in seconds — how quickly the volume falls from peak to sustain level
 * @param-sub params sustain Sustain level (0–1) — the volume held while the note is pressed
 * @param-sub params release Release time in seconds — how long the note takes to fade to silence after note-off
 *
 * @category dynamics
 * @tags adsr, attack, decay, sustain, release, envelope
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.adsr(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyAdsr(this, listOfNotNull(params).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets all ADSR envelope parameters.
 *
 * ```KlangScript(Playable)
 * "c3*4".adsr("<0.01:0.1:0.5:0.2 0.5:0.5:0.8:1.0>").note()    // alternate envelopes
 * ```
 *
 * @param params The ADSR parameters as a colon-separated string `"attack:decay:sustain:release"`.
 */
@SprudelDsl
@KlangScript.Function
fun String.adsr(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).adsr(params, callInfo)

/**
 * Creates a [PatternMapperFn] that sets all ADSR envelope parameters for each event.
 *
 * ```KlangScript(Playable)
 * note("c3*4").s("sine").apply(adsr("<0.01:0.1:0.5:0.2 0.5:0.5:0.8:1.0>"))  // alternate envelopes
 * ```
 *
 * @param params The ADSR parameters as a colon-separated string `"attack:decay:sustain:release"`.
 */
@SprudelDsl
@KlangScript.Function
fun adsr(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.adsr(params, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets all ADSR parameters after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3*4").s("sine").apply(gain(0.8).adsr("0.01:0.2:0.7:0.5"))  // gain + adsr chained
 * ```
 *
 * @param params The ADSR parameters as a colon-separated string `"attack:decay:sustain:release"`.
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.adsr(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.adsr(params, callInfo) }

// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Routing
// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

// -- orbit() / o() ----------------------------------------------------------------------------------------------------

private val orbitMutation = voiceModifier {
    copy(cylinder = it?.asIntOrNull())
}

private fun applyOrbit(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretStringField(args, orbitMutation)
}

/**
 * Routes the pattern to an audio output orbit (channel group) for independent effect processing.
 *
 * Each orbit can have its own reverb, delay, and other effects applied independently.
 * Use different orbit numbers to send patterns to different effect buses.
 *
 * ```KlangScript(Playable)
 * s("bd sd").orbit(1)                           // send drums to orbit 1
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3").orbit(2).room(0.8).roomsize(4)  // melodic line on orbit 2 with reverb
 * ```
 *
 * @param index The orbit index to route events to.
 *
 * @alias o
 * @category dynamics
 * @tags orbit, o, routing, effects, bus, channel
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.orbit(index: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyOrbit(this, listOfNotNull(index).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and routes it to the given audio output orbit.
 *
 * ```KlangScript(Playable)
 * "bd sd".orbit(1).s()   // send drums to orbit 1
 * ```
 *
 * @param index The orbit index to route events to.
 */
@SprudelDsl
@KlangScript.Function
fun String.orbit(index: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).orbit(index, callInfo)

/**
 * Creates a [PatternMapperFn] that routes events to the given audio output orbit.
 *
 * ```KlangScript(Playable)
 * s("bd sd").apply(orbit(1))   // send drums to orbit 1
 * ```
 *
 * @param index The orbit index to route events to.
 */
@SprudelDsl
@KlangScript.Function
fun orbit(index: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.orbit(index, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that routes events to the given orbit after the previous mapper.
 *
 * ```KlangScript(Playable)
 * s("bd sd").apply(gain(0.8).orbit(1))  // gain + orbit chained
 * ```
 *
 * @param index The orbit index to route events to.
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.orbit(index: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.orbit(index, callInfo) }

/**
 * Alias for [orbit]. Routes the pattern to an audio output orbit for independent effect processing.
 *
 * ```KlangScript(Playable)
 * s("bd sd").o(1)                       // send drums to orbit 1
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3").o(2).room(0.8)          // melodic line on orbit 2 with reverb
 * ```
 *
 * @param index The orbit index to route events to.
 *
 * @alias orbit
 * @category dynamics
 * @tags o, orbit, routing, effects, bus, channel
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.o(index: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.orbit(index, callInfo)

/**
 * Alias for [orbit]. Parses this string as a pattern and routes it to the given orbit.
 *
 * ```KlangScript(Playable)
 * "bd sd".o(1).s()   // send drums to orbit 1
 * ```
 *
 * @param index The orbit index to route events to.
 */
@SprudelDsl
@KlangScript.Function
fun String.o(index: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).orbit(index, callInfo)

/**
 * Alias for [orbit]. Creates a [PatternMapperFn] that routes events to the given audio output orbit.
 *
 * ```KlangScript(Playable)
 * s("bd sd").apply(o(1))   // send drums to orbit 1
 * ```
 *
 * @param index The orbit index to route events to.
 */
@SprudelDsl
@KlangScript.Function
fun o(index: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.orbit(index, callInfo) }

// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Ducking / Sidechain
// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

// -- duckorbit() / duck() ---------------------------------------------------------------------------------------------

private val duckOrbitMutation = voiceModifier {
    copy(duckCylinder = it?.asIntOrNull())
}

private fun applyDuckOrbit(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftNumericField(args, duckOrbitMutation)
}

/**
 * Sets the target orbit to listen to for sidechain ducking.
 *
 * The pattern's volume is reduced when audio is detected on the specified orbit.
 * Use with `duckdepth` to set the attenuation amount and `duckattack` for the recovery time.
 *
 * ```KlangScript(Playable)
 * s("bd*4").orbit(1)                              // kick drum on orbit 1
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").duckorbit(1).duckdepth(0.8)   // duck when kick plays on orbit 1
 * ```
 *
 * @param orbitIndex The orbit index to listen to for the sidechain trigger.
 *
 * @alias duck
 * @category dynamics
 * @tags duckorbit, duck, sidechain, ducking, dynamics
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.duckorbit(orbitIndex: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyDuckOrbit(this, listOfNotNull(orbitIndex).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets the sidechain source orbit for ducking.
 *
 * ```KlangScript(Playable)
 * "c3 e3".duckorbit(1).duckdepth(0.8).note()   // duck when orbit 1 plays
 * ```
 *
 * @param orbitIndex The orbit index to listen to for the sidechain trigger.
 */
@SprudelDsl
@KlangScript.Function
fun String.duckorbit(orbitIndex: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).duckorbit(orbitIndex, callInfo)

/**
 * Creates a [PatternMapperFn] that sets the sidechain source orbit for ducking.
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").apply(duckorbit(1)).duckdepth(0.8)   // duck when orbit 1 plays
 * ```
 *
 * @param orbitIndex The orbit index to listen to for the sidechain trigger.
 */
@SprudelDsl
@KlangScript.Function
fun duckorbit(orbitIndex: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.duckorbit(orbitIndex, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the sidechain source orbit after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(gain(0.8).duckorbit(1))  // gain + duckorbit chained
 * ```
 *
 * @param orbitIndex The orbit index to listen to for the sidechain trigger.
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.duckorbit(orbitIndex: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.duckorbit(orbitIndex, callInfo) }

/**
 * Alias for [duckorbit]. Sets the target orbit to listen to for sidechain ducking.
 *
 * ```KlangScript(Playable)
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
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.duck(orbitIndex: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.duckorbit(orbitIndex, callInfo)

/**
 * Alias for [duckorbit]. Parses this string as a pattern and sets the sidechain source orbit.
 *
 * ```KlangScript(Playable)
 * "c3 e3".duck(0).duckdepth(0.8).note()   // duck when orbit 0 plays
 * ```
 *
 * @param orbitIndex The orbit index to listen to for the sidechain trigger.
 */
@SprudelDsl
@KlangScript.Function
fun String.duck(orbitIndex: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).duckorbit(orbitIndex, callInfo)

/**
 * Alias for [duckorbit]. Creates a [PatternMapperFn] that sets the sidechain source orbit for ducking.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(duck(0)).duckdepth(0.8)   // duck when orbit 0 plays
 * ```
 *
 * @param orbitIndex The orbit index to listen to for the sidechain trigger.
 */
@SprudelDsl
@KlangScript.Function
fun duck(orbitIndex: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.duckorbit(orbitIndex, callInfo) }

/**
 * Alias for [duckorbit]. Creates a chained [PatternMapperFn] that sets the sidechain source orbit after the
 * previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(gain(0.8).duck(0))  // gain + duck chained
 * ```
 *
 * @param orbitIndex The orbit index to listen to for the sidechain trigger.
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.duck(orbitIndex: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.duckorbit(orbitIndex, callInfo) }

// -- duckattack() / duckatt() -----------------------------------------------------------------------------------------

private val duckAttackMutation = voiceModifier { copy(duckAttack = it?.asDoubleOrNull()) }

private fun applyDuckAttack(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftNumericField(args, duckAttackMutation)
}

/**
 * Sets the duck release (return-to-normal) time in seconds for sidechain ducking.
 *
 * Controls how quickly the ducked pattern returns to its full volume after the sidechain
 * trigger stops. Shorter values snap back quickly; longer values create a pumping effect.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").duck(1).duckdepth(0.8).duckattack(0.2)   // 200 ms recovery
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").duckattack("<0.05 0.1 0.3 0.5>")          // varying recovery times
 * ```
 *
 * @param time The recovery time in seconds.
 *
 * @alias duckatt
 * @category dynamics
 * @tags duckattack, duckatt, sidechain, ducking, release, dynamics
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.duckattack(time: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyDuckAttack(this, listOfNotNull(time).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets the duck release time.
 *
 * ```KlangScript(Playable)
 * "c3*4".duckattack("<0.05 0.1 0.3 0.5>").note()   // varying recovery times
 * ```
 *
 * @param time The recovery time in seconds.
 */
@SprudelDsl
@KlangScript.Function
fun String.duckattack(time: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).duckattack(time, callInfo)

/**
 * Creates a [PatternMapperFn] that sets the duck release time.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(duckattack(0.2)).duck(1).duckdepth(0.8)   // 200 ms recovery
 * ```
 *
 * @param time The recovery time in seconds.
 */
@SprudelDsl
@KlangScript.Function
fun duckattack(time: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.duckattack(time, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the duck recovery time after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(duck(1).duckattack(0.2))  // duck + duckattack chained
 * ```
 *
 * @param time The recovery time in seconds.
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.duckattack(time: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.duckattack(time, callInfo) }

/**
 * Alias for [duckattack]. Sets the duck release (return-to-normal) time in seconds.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").duck(1).duckdepth(0.8).duckatt(0.2)   // 200 ms recovery
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").duckatt("<0.05 0.1 0.3 0.5>")          // varying recovery times
 * ```
 *
 * @param time The recovery time in seconds.
 *
 * @alias duckattack
 * @category dynamics
 * @tags duckatt, duckattack, sidechain, ducking, release, dynamics
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.duckatt(time: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.duckattack(time, callInfo)

/**
 * Alias for [duckattack]. Parses this string as a pattern and sets the duck release time.
 *
 * ```KlangScript(Playable)
 * "c3*4".duckatt("<0.05 0.1 0.3 0.5>").note()   // varying recovery times
 * ```
 *
 * @param time The recovery time in seconds.
 */
@SprudelDsl
@KlangScript.Function
fun String.duckatt(time: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).duckattack(time, callInfo)

/**
 * Alias for [duckattack]. Creates a [PatternMapperFn] that sets the duck release time.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(duckatt(0.2)).duck(1).duckdepth(0.8)   // 200 ms recovery
 * ```
 *
 * @param time The recovery time in seconds.
 */
@SprudelDsl
@KlangScript.Function
fun duckatt(time: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.duckattack(time, callInfo) }

/**
 * Alias for [duckattack]. Creates a chained [PatternMapperFn] that sets the duck recovery time after the previous
 * mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(duck(1).duckatt(0.2))  // duck + duckatt chained
 * ```
 *
 * @param time The recovery time in seconds.
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.duckatt(time: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.duckattack(time, callInfo) }

// -- duckdepth() ------------------------------------------------------------------------------------------------------

private val duckDepthMutation = voiceModifier { copy(duckDepth = it?.asDoubleOrNull()) }

private fun applyDuckDepth(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftNumericField(args, duckDepthMutation)
}

/**
 * Sets the ducking depth (0.0 = no ducking, 1.0 = full silence) for sidechain ducking.
 *
 * Controls how much the pattern is attenuated when the sidechain trigger fires.
 * Use with `duckorbit` to set the sidechain source and `duckattack` for recovery time.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").duck(1).duckdepth(0.8)           // 80% attenuation on sidechain
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").duckdepth("<0.3 0.6 0.9 1.0>")   // escalating ducking depth
 * ```
 *
 * @param amount The ducking depth between 0.0 (no ducking) and 1.0 (full silence).
 *
 * @category dynamics
 * @tags duckdepth, sidechain, ducking, attenuation, dynamics
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.duckdepth(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyDuckDepth(this, listOfNotNull(amount).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets the ducking depth.
 *
 * ```KlangScript(Playable)
 * "c3*4".duckdepth("<0.3 0.6 0.9 1.0>").note()   // escalating ducking depth
 * ```
 *
 * @param amount The ducking depth between 0.0 (no ducking) and 1.0 (full silence).
 */
@SprudelDsl
@KlangScript.Function
fun String.duckdepth(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).duckdepth(amount, callInfo)

/**
 * Creates a [PatternMapperFn] that sets the ducking depth.
 *
 * ```KlangScript(Playable)
 * note("c3*4").apply(duckdepth("<0.3 0.6 0.9 1.0>"))   // escalating ducking depth
 * ```
 *
 * @param amount The ducking depth between 0.0 (no ducking) and 1.0 (full silence).
 */
@SprudelDsl
@KlangScript.Function
fun duckdepth(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.duckdepth(amount, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the ducking depth after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3*4").apply(duck(1).duckdepth(0.8))  // duck + duckdepth chained
 * ```
 *
 * @param amount The ducking depth between 0.0 (no ducking) and 1.0 (full silence).
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.duckdepth(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.duckdepth(amount, callInfo) }
