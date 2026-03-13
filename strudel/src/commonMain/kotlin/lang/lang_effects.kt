@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions")

package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel._applyControlFromParams
import io.peekandpoke.klang.strudel._liftOrReinterpretNumericalField
import io.peekandpoke.klang.strudel.lang.StrudelDslArg.Companion.asStrudelDslArgs

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in StrudelRegistry.
 */
var strudelLangEffectsInit = false

// -- distort() / dist() -----------------------------------------------------------------------------------------------

private val distortMutation = voiceModifier { copy(distort = it?.asDoubleOrNull()) }

fun applyDistort(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftOrReinterpretNumericalField(args, distortMutation)
}

internal val _distort by dslPatternMapper { args, callInfo -> { p -> p._distort(args, callInfo) } }
internal val StrudelPattern._distort by dslPatternExtension { p, args, /* callInfo */ _ -> applyDistort(p, args) }
internal val String._distort by dslStringExtension { p, args, callInfo -> p._distort(args, callInfo) }
internal val PatternMapperFn._distort by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_distort(args, callInfo))
}

internal val _dist by dslPatternMapper { args, callInfo -> { p -> p._dist(args, callInfo) } }
internal val StrudelPattern._dist by dslPatternExtension { p, args, /* callInfo */ _ -> applyDistort(p, args) }
internal val String._dist by dslStringExtension { p, args, callInfo -> p._dist(args, callInfo) }
internal val PatternMapperFn._dist by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_dist(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Applies waveshaper distortion to this pattern.
 *
 * Higher values produce more harmonic saturation and clipping. Works well on synth
 * bass lines and leads; combine with `lpf` to tame harsh high frequencies.
 *
 * When [amount] is omitted, the pattern's own numeric values are reinterpreted as distortion amounts.
 *
 * @param amount The distortion amount. Higher values produce more saturation and clipping.
 *   Omit to reinterpret the pattern's values as distortion.
 * @return A new pattern with distortion applied.
 *
 * ```KlangScript
 * note("c2 eb2 g2").s("sawtooth").distort(0.5)   // moderate distortion
 * ```
 *
 * ```KlangScript
 * note("c3*4").distort("<0 0.3 0.6 1.0>")        // escalating distortion each beat
 * ```
 *
 * ```KlangScript
 * seq("0 0.5 1.0").distort()                     // reinterpret values as distortion
 * ```
 * @alias dist
 * @category effects
 * @tags distort, dist, distortion, waveshaper, overdrive
 */
@StrudelDsl
fun StrudelPattern.distort(amount: PatternLike? = null): StrudelPattern =
    this._distort(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Parses this string as a pattern, then applies waveshaper distortion.
 *
 * When [amount] is omitted, the pattern's own numeric values are reinterpreted as distortion amounts.
 *
 * @param amount The distortion amount. Higher values produce more saturation and clipping.
 *   Omit to reinterpret the pattern's values as distortion.
 * @return A new pattern with distortion applied.
 *
 * ```KlangScript
 * "c2 eb2 g2".distort(0.5).note().s("sawtooth")  // moderate distortion on bass notes
 * ```
 */
@StrudelDsl
fun String.distort(amount: PatternLike? = null): StrudelPattern =
    this._distort(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that applies waveshaper distortion.
 *
 * Use the returned mapper as a transform argument or apply it to a pattern via `.apply(...)`.
 * When [amount] is omitted, the pattern's own numeric values are reinterpreted as distortion amounts.
 *
 * @param amount The distortion amount. Higher values produce more saturation and clipping.
 *   Omit to reinterpret the pattern's values as distortion.
 * @return A [PatternMapperFn] that applies waveshaper distortion.
 *
 * ```KlangScript
 * note("c2 eb2 g2").s("sawtooth").apply(distort(0.5))  // moderate distortion
 * ```
 *
 * ```KlangScript
 * note("c3*4").firstOf(4, distort(0.8))  // heavy distortion on every 4th cycle
 * ```
 * @alias dist
 * @category effects
 * @tags distort, dist, distortion, waveshaper, overdrive
 */
@StrudelDsl
fun distort(amount: PatternLike? = null): PatternMapperFn = _distort(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that applies waveshaper distortion after the previous mapper.
 *
 * @param amount The distortion amount. Omit to reinterpret the pattern's values as distortion.
 * @return A new [PatternMapperFn] chaining this distortion after the previous mapper.
 *
 * ```KlangScript
 * note("c2 eb2 g2").s("sawtooth").apply(crush(4).distort(0.5))  // bit-crush then distort
 * ```
 *
 * ```KlangScript
 * note("c3*4").firstOf(4, room(0.3).distort(0.8))  // room + distortion every 4th cycle
 * ```
 */
@StrudelDsl
fun PatternMapperFn.distort(amount: PatternLike? = null): PatternMapperFn =
    _distort(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Alias for [distort]. Applies waveshaper distortion to this pattern.
 *
 * When [amount] is omitted, the pattern's own numeric values are reinterpreted as distortion amounts.
 *
 * @param amount The distortion amount. Higher values produce more saturation and clipping.
 *   Omit to reinterpret the pattern's values as distortion.
 * @return A new pattern with distortion applied.
 *
 * ```KlangScript
 * note("c2 eb2 g2").s("sawtooth").dist(0.5)   // moderate distortion
 * ```
 *
 * ```KlangScript
 * note("c3*4").dist("<0 0.3 0.6 1.0>")        // escalating distortion each beat
 * ```
 *
 * ```KlangScript
 * seq("0 0.5 1.0").dist()                     // reinterpret values as distortion
 * ```
 * @alias distort
 * @category effects
 * @tags dist, distort, distortion, waveshaper, overdrive
 */
@StrudelDsl
fun StrudelPattern.dist(amount: PatternLike? = null): StrudelPattern =
    this._dist(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Alias for [distort]. Parses this string as a pattern, then applies waveshaper distortion.
 *
 * When [amount] is omitted, the pattern's own numeric values are reinterpreted as distortion amounts.
 *
 * @param amount The distortion amount. Higher values produce more saturation and clipping.
 *   Omit to reinterpret the pattern's values as distortion.
 * @return A new pattern with distortion applied.
 *
 * ```KlangScript
 * "c2 eb2 g2".dist(0.5).note().s("sawtooth")  // moderate distortion on bass notes
 * ```
 */
@StrudelDsl
fun String.dist(amount: PatternLike? = null): StrudelPattern =
    this._dist(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that applies waveshaper distortion. Alias for [distort].
 *
 * Use the returned mapper as a transform argument or apply it to a pattern via `.apply(...)`.
 * When [amount] is omitted, the pattern's own numeric values are reinterpreted as distortion amounts.
 *
 * @param amount The distortion amount. Higher values produce more saturation and clipping.
 *   Omit to reinterpret the pattern's values as distortion.
 * @return A [PatternMapperFn] that applies waveshaper distortion.
 *
 * ```KlangScript
 * note("c2 eb2 g2").s("sawtooth").apply(dist(0.5))  // moderate distortion
 * ```
 *
 * ```KlangScript
 * note("c3*4").firstOf(4, dist(0.8))  // heavy distortion on every 4th cycle
 * ```
 * @alias distort
 * @category effects
 * @tags dist, distort, distortion, waveshaper, overdrive
 */
@StrudelDsl
fun dist(amount: PatternLike? = null): PatternMapperFn = _dist(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that applies waveshaper distortion (alias for [distort]) after the previous mapper.
 *
 * @param amount The distortion amount. Omit to reinterpret the pattern's values as distortion.
 * @return A new [PatternMapperFn] chaining this distortion after the previous mapper.
 *
 * ```KlangScript
 * note("c2 eb2 g2").s("sawtooth").apply(crush(4).dist(0.5))  // bit-crush then distort
 * ```
 *
 * ```KlangScript
 * note("c3*4").firstOf(4, room(0.3).dist(0.8))  // room + distortion every 4th cycle
 * ```
 */
@StrudelDsl
fun PatternMapperFn.dist(amount: PatternLike? = null): PatternMapperFn =
    _dist(listOfNotNull(amount).asStrudelDslArgs())

// -- crush() ----------------------------------------------------------------------------------------------------------

private val crushMutation = voiceModifier { copy(crush = it?.asDoubleOrNull()) }

fun applyCrush(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftOrReinterpretNumericalField(args, crushMutation)
}

internal val _crush by dslPatternMapper { args, callInfo -> { p -> p._crush(args, callInfo) } }
internal val StrudelPattern._crush by dslPatternExtension { p, args, /* callInfo */ _ -> applyCrush(p, args) }
internal val String._crush by dslStringExtension { p, args, callInfo -> p._crush(args, callInfo) }
internal val PatternMapperFn._crush by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_crush(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

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
 * ```KlangScript
 * s("bd sd hh").crush(4)              // 4-bit lo-fi crunch
 * ```
 *
 * ```KlangScript
 * note("c3*4").crush("<16 8 4 2>")    // decreasing bit depth each beat
 * ```
 *
 * ```KlangScript
 * seq("16 8 4 2").crush()             // reinterpret values as crush
 * ```
 *
 * @category effects
 * @tags crush, bitcrush, lofi, bitdepth, distortion
 */
@StrudelDsl
fun StrudelPattern.crush(amount: PatternLike? = null): StrudelPattern =
    this._crush(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Parses this string as a pattern and applies bit-crushing.
 *
 * When [amount] is omitted, the string's numeric values are reinterpreted as crush amounts.
 *
 * @param amount The bit-depth reduction amount. Lower values produce more lo-fi character.
 *   Omit to reinterpret the pattern's values as crush.
 * @return A new pattern with bit-crushing applied.
 *
 * ```KlangScript
 * "bd sd hh".crush(4).s()            // 4-bit lo-fi crunch on samples
 * ```
 */
@StrudelDsl
fun String.crush(amount: PatternLike? = null): StrudelPattern =
    this._crush(listOfNotNull(amount).asStrudelDslArgs())

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
 * ```KlangScript
 * s("bd sd hh").apply(crush(4))              // 4-bit crunch via mapper
 * ```
 *
 * ```KlangScript
 * note("c3*4").every(4, crush(2))            // maximum crush every 4th cycle
 * ```
 *
 * @category effects
 * @tags crush, bitcrush, lofi, bitdepth, distortion
 */
@StrudelDsl
fun crush(amount: PatternLike? = null): PatternMapperFn = _crush(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that applies bit-crushing after the previous mapper.
 *
 * @param amount The bit-depth reduction amount. Lower values produce more lo-fi character.
 *   Omit to reinterpret the pattern's values as crush.
 * @return A new [PatternMapperFn] chaining this bit-crushing after the previous mapper.
 *
 * ```KlangScript
 * note("c3*4").apply(coarse(4).crush(4))   // sample-rate reduction then bit-crush
 * ```
 *
 * ```KlangScript
 * note("c3*4").every(4, distort(0.5).crush(2))   // distort + max crush every 4th cycle
 * ```
 */
@StrudelDsl
fun PatternMapperFn.crush(amount: PatternLike? = null): PatternMapperFn =
    _crush(listOfNotNull(amount).asStrudelDslArgs())

// -- coarse() ---------------------------------------------------------------------------------------------------------

private val coarseMutation = voiceModifier { copy(coarse = it?.asDoubleOrNull()) }

fun applyCoarse(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftOrReinterpretNumericalField(args, coarseMutation)
}

internal val _coarse by dslPatternMapper { args, callInfo -> { p -> p._coarse(args, callInfo) } }
internal val StrudelPattern._coarse by dslPatternExtension { p, args, /* callInfo */ _ -> applyCoarse(p, args) }
internal val String._coarse by dslStringExtension { p, args, callInfo -> p._coarse(args, callInfo) }
internal val PatternMapperFn._coarse by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_coarse(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

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
 * ```KlangScript
 * s("bd sd").coarse(4)               // moderate sample-rate reduction
 * ```
 *
 * ```KlangScript
 * note("c3*8").coarse("<1 2 4 8>")   // escalating downsampling
 * ```
 *
 * ```KlangScript
 * seq("1 2 4 8").coarse()            // reinterpret values as coarse
 * ```
 *
 * @category effects
 * @tags coarse, samplerate, lofi, aliasing, downsample
 */
@StrudelDsl
fun StrudelPattern.coarse(amount: PatternLike? = null): StrudelPattern =
    this._coarse(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Parses this string as a pattern and applies sample-rate reduction.
 *
 * When [amount] is omitted, the string's numeric values are reinterpreted as coarse amounts.
 *
 * @param amount The downsampling amount. Higher values produce more aliasing and lo-fi character.
 *   Omit to reinterpret the pattern's values as coarse.
 * @return A new pattern with sample-rate reduction applied.
 *
 * ```KlangScript
 * "bd sd".coarse(4).s()              // lo-fi samples
 * ```
 */
@StrudelDsl
fun String.coarse(amount: PatternLike? = null): StrudelPattern =
    this._coarse(listOfNotNull(amount).asStrudelDslArgs())

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
 * ```KlangScript
 * s("bd sd").apply(coarse(4))        // lo-fi via mapper
 * ```
 *
 * ```KlangScript
 * note("c3*4").every(4, coarse(8))   // heavy downsampling every 4th cycle
 * ```
 *
 * @category effects
 * @tags coarse, samplerate, lofi, aliasing, downsample
 */
@StrudelDsl
fun coarse(amount: PatternLike? = null): PatternMapperFn = _coarse(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that applies sample-rate reduction after the previous mapper.
 *
 * @param amount The downsampling amount. Higher values produce more aliasing and lo-fi character.
 *   Omit to reinterpret the pattern's values as coarse.
 * @return A new [PatternMapperFn] chaining this sample-rate reduction after the previous mapper.
 *
 * ```KlangScript
 * note("c3*4").apply(crush(4).coarse(4))    // bit-crush then downsample
 * ```
 *
 * ```KlangScript
 * s("bd sd").every(4, coarse(8).distort(0.5))  // heavy lo-fi every 4th cycle
 * ```
 */
@StrudelDsl
fun PatternMapperFn.coarse(amount: PatternLike? = null): PatternMapperFn =
    _coarse(listOfNotNull(amount).asStrudelDslArgs())

// -- room() -----------------------------------------------------------------------------------------------------------

private val roomMutation = voiceModifier { copy(room = it?.asDoubleOrNull()) }

fun applyRoom(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftOrReinterpretNumericalField(args, roomMutation)
}

internal val _room by dslPatternMapper { args, callInfo -> { p -> p._room(args, callInfo) } }
internal val StrudelPattern._room by dslPatternExtension { p, args, /* callInfo */ _ -> applyRoom(p, args) }
internal val String._room by dslStringExtension { p, args, callInfo -> p._room(args, callInfo) }
internal val PatternMapperFn._room by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_room(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the reverb wet/dry mix for this pattern (0 = dry, 1 = full wet).
 *
 * Use with `roomsize` to control the reverb tail length, and `orbit` to send
 * multiple patterns to separate reverb buses.
 * When [amount] is omitted, the pattern's own numeric values are reinterpreted as room mix.
 *
 * @param amount The wet/dry mix (0–1). Omit to reinterpret the pattern's values as room mix.
 * @return A new pattern with reverb wet/dry mix applied.
 *
 * ```KlangScript
 * note("c3 e3 g3").clip(0.5).s("sine").room(0.5)              // 50% reverb
 * ```
 *
 * ```KlangScript
 * note("c3*4").clip(0.5).room("<0 0.3 0.6 0.9>").roomsize(4)  // increasing wet mix
 * ```
 *
 * ```KlangScript
 * seq("0 0.5 1.0").room()                           // reinterpret values as room mix
 * ```
 *
 * @param-tool amount StrudelGainSequenceEditor
 * @category effects
 * @tags room, reverb, wet, mix, space
 */
@StrudelDsl
fun StrudelPattern.room(amount: PatternLike? = null): StrudelPattern =
    this._room(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Parses this string as a pattern and sets the reverb wet/dry mix.
 *
 * When [amount] is omitted, the string's numeric values are reinterpreted as room mix.
 *
 * @param amount The wet/dry mix (0–1). Omit to reinterpret the pattern's values as room mix.
 * @return A new pattern with reverb wet/dry mix applied.
 *
 * ```KlangScript
 * "c3 e3 g3".room(0.5).note().clip(0.5)    // 50% reverb on bass notes
 * ```
 */
@StrudelDsl
fun String.room(amount: PatternLike? = null): StrudelPattern =
    this._room(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets the reverb wet/dry mix.
 *
 * Use the returned mapper as a transform argument or apply it to a pattern via `.apply(...)`.
 * When [amount] is omitted, the pattern's own numeric values are reinterpreted as room mix.
 *
 * @param amount The wet/dry mix (0–1). Omit to reinterpret the pattern's values as room mix.
 * @return A [PatternMapperFn] that sets the reverb wet/dry mix.
 *
 * ```KlangScript
 * note("c3 e3 g3").apply(room(0.5)).clip(0.5)     // 50% reverb via mapper
 * ```
 *
 * ```KlangScript
 * note("c3*4").every(4, room(0.9)).clip(0.5)      // heavy reverb every 4th cycle
 * ```
 *
 * @category effects
 * @tags room, reverb, wet, mix, space
 */
@StrudelDsl
fun room(amount: PatternLike? = null): PatternMapperFn = _room(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets the reverb wet/dry mix after the previous mapper.
 *
 * @param amount The wet/dry mix (0–1). Omit to reinterpret the pattern's values as room mix.
 * @return A new [PatternMapperFn] chaining this reverb mix after the previous mapper.
 *
 * ```KlangScript
 * note("c3 e3").apply(roomsize(4).room(0.5))     // set room size then wet mix
 * ```
 *
 * ```KlangScript
 * note("c3*4").every(4, coarse(4).room(0.8))     // lo-fi with heavy reverb every 4th cycle
 * ```
 */
@StrudelDsl
fun PatternMapperFn.room(amount: PatternLike? = null): PatternMapperFn =
    _room(listOfNotNull(amount).asStrudelDslArgs())

// -- roomsize() / rsize() / sz() / size() -----------------------------------------------------------------------------

private val roomSizeMutation = voiceModifier { copy(roomSize = it?.asDoubleOrNull()) }

fun applyRoomSize(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftOrReinterpretNumericalField(args, roomSizeMutation)
}

internal val _roomsize by dslPatternMapper { args, callInfo -> { p -> p._roomsize(args, callInfo) } }
internal val StrudelPattern._roomsize by dslPatternExtension { p, args, /* callInfo */ _ -> applyRoomSize(p, args) }
internal val String._roomsize by dslStringExtension { p, args, callInfo -> p._roomsize(args, callInfo) }
internal val PatternMapperFn._roomsize by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_roomsize(args, callInfo))
}

internal val _rsize by dslPatternMapper { args, callInfo -> { p -> p._rsize(args, callInfo) } }
internal val StrudelPattern._rsize by dslPatternExtension { p, args, /* callInfo */ _ -> applyRoomSize(p, args) }
internal val String._rsize by dslStringExtension { p, args, callInfo -> p._rsize(args, callInfo) }
internal val PatternMapperFn._rsize by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_rsize(args, callInfo))
}

internal val _sz by dslPatternMapper { args, callInfo -> { p -> p._sz(args, callInfo) } }
internal val StrudelPattern._sz by dslPatternExtension { p, args, /* callInfo */ _ -> applyRoomSize(p, args) }
internal val String._sz by dslStringExtension { p, args, callInfo -> p._sz(args, callInfo) }
internal val PatternMapperFn._sz by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_sz(args, callInfo))
}

internal val _size by dslPatternMapper { args, callInfo -> { p -> p._size(args, callInfo) } }
internal val StrudelPattern._size by dslPatternExtension { p, args, /* callInfo */ _ -> applyRoomSize(p, args) }
internal val String._size by dslStringExtension { p, args, callInfo -> p._size(args, callInfo) }
internal val PatternMapperFn._size by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_size(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

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
 * ```KlangScript
 * note("c3 e3").clip(0.5).room(0.5).roomsize(4)   // long reverb tail
 * ```
 *
 * ```KlangScript
 * note("c3*4").clip(0.5).roomsize("<1 2 4 8>")              // growing room size
 * ```
 *
 * ```KlangScript
 * seq("1 2 4 8").roomsize()                       // reinterpret values as room size
 * ```
 *
 * @param-tool amount StrudelGainSequenceEditor
 * @alias rsize, sz, size
 * @category effects
 * @tags roomsize, rsize, sz, size, reverb, room, tail
 */
@StrudelDsl
fun StrudelPattern.roomsize(amount: PatternLike? = null): StrudelPattern =
    this._roomsize(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Parses this string as a pattern and sets the reverb room size.
 *
 * When [amount] is omitted, the string's numeric values are reinterpreted as room size.
 *
 * @param amount The room size. Larger values produce longer reverb tails.
 *   Omit to reinterpret the pattern's values as room size.
 * @return A new pattern with room size applied.
 *
 * ```KlangScript
 * "c3 e3".roomsize(4).room(0.5).note().clip(0.5)    // long reverb tail on bass notes
 * ```
 */
@StrudelDsl
fun String.roomsize(amount: PatternLike? = null): StrudelPattern =
    this._roomsize(listOfNotNull(amount).asStrudelDslArgs())

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
 * ```KlangScript
 * note("c3 e3").apply(roomsize(4)).clip(0.5)        // long reverb tail via mapper
 * ```
 *
 * ```KlangScript
 * note("c3*4").every(4, roomsize(8)).clip(0.5)      // huge room every 4th cycle
 * ```
 *
 * @alias rsize, sz, size
 * @category effects
 * @tags roomsize, rsize, sz, size, reverb, room, tail
 */
@StrudelDsl
fun roomsize(amount: PatternLike? = null): PatternMapperFn = _roomsize(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets the reverb room size after the previous mapper.
 *
 * @param amount The room size. Omit to reinterpret the pattern's values as room size.
 * @return A new [PatternMapperFn] chaining this room size after the previous mapper.
 *
 * ```KlangScript
 * note("c3 e3").apply(room(0.5).roomsize(4))   // set wet mix then room size
 * ```
 *
 * ```KlangScript
 * note("c3*4").every(4, room(0.8).roomsize(8))  // big reverb every 4th cycle
 * ```
 */
@StrudelDsl
fun PatternMapperFn.roomsize(amount: PatternLike? = null): PatternMapperFn =
    _roomsize(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Alias for [roomsize]. Sets the reverb room size for this pattern.
 *
 * @param amount The room size. Omit to reinterpret the pattern's values as room size.
 * @return A new pattern with room size applied.
 *
 * ```KlangScript
 * note("c3 e3").clip(0.5).room(0.5).rsize(4)   // long reverb tail
 * ```
 *
 * @param-tool amount StrudelGainSequenceEditor
 * @alias roomsize, sz, size
 * @category effects
 * @tags rsize, roomsize, sz, size, reverb, room, tail
 */
@StrudelDsl
fun StrudelPattern.rsize(amount: PatternLike? = null): StrudelPattern =
    this._rsize(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Alias for [roomsize]. Parses this string as a pattern and sets the reverb room size.
 *
 * @param amount The room size. Omit to reinterpret the pattern's values as room size.
 *
 * ```KlangScript
 * "c3 e3".rsize(4).room(0.5).note().clip(0.5)    // long reverb tail on bass notes
 * ```
 */
@StrudelDsl
fun String.rsize(amount: PatternLike? = null): StrudelPattern =
    this._rsize(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets the reverb room size. Alias for [roomsize].
 *
 * @param amount The room size. Omit to reinterpret the pattern's values as room size.
 * @return A [PatternMapperFn] that sets the reverb room size.
 *
 * ```KlangScript
 * note("c3 e3").apply(rsize(4)).clip(0.5)   // long reverb tail via mapper
 * ```
 *
 * @alias roomsize, sz, size
 * @category effects
 * @tags rsize, roomsize, sz, size, reverb, room, tail
 */
@StrudelDsl
fun rsize(amount: PatternLike? = null): PatternMapperFn = _rsize(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets the reverb room size (alias for roomsize) after the previous mapper.
 *
 * @param amount The room size. Omit to reinterpret the pattern's values as room size.
 * @return A new [PatternMapperFn] chaining this room size after the previous mapper.
 *
 * ```KlangScript
 * note("c3 e3").apply(room(0.5).rsize(4))   // set wet mix then room size
 * ```
 *
 * ```KlangScript
 * note("c3*4").every(4, room(0.8).rsize(8))  // big reverb every 4th cycle
 * ```
 */
@StrudelDsl
fun PatternMapperFn.rsize(amount: PatternLike? = null): PatternMapperFn =
    _rsize(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Alias for [roomsize]. Sets the reverb room size for this pattern.
 *
 * @param amount The room size. Omit to reinterpret the pattern's values as room size.
 * @return A new pattern with room size applied.
 *
 * ```KlangScript
 * note("c3 e3").clip(0.5).room(0.5).sz(4)   // long reverb tail
 * ```
 *
 * @param-tool amount StrudelGainSequenceEditor
 * @alias roomsize, rsize, size
 * @category effects
 * @tags sz, roomsize, rsize, size, reverb, room, tail
 */
@StrudelDsl
fun StrudelPattern.sz(amount: PatternLike? = null): StrudelPattern =
    this._sz(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Alias for [roomsize]. Parses this string as a pattern and sets the reverb room size.
 *
 * @param amount The room size. Omit to reinterpret the pattern's values as room size.
 *
 * ```KlangScript
 * "c3 e3".sz(4).room(0.5).note().clip(0.5)    // long reverb tail on bass notes
 * ```
 */
@StrudelDsl
fun String.sz(amount: PatternLike? = null): StrudelPattern =
    this._sz(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets the reverb room size. Alias for [roomsize].
 *
 * @param amount The room size. Omit to reinterpret the pattern's values as room size.
 * @return A [PatternMapperFn] that sets the reverb room size.
 *
 * ```KlangScript
 * note("c3 e3").apply(sz(4)).clip(0.5)   // long reverb tail via mapper
 * ```
 *
 * @alias roomsize, rsize, size
 * @category effects
 * @tags sz, roomsize, rsize, size, reverb, room, tail
 */
@StrudelDsl
fun sz(amount: PatternLike? = null): PatternMapperFn = _sz(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets the reverb room size (alias for roomsize) after the previous mapper.
 *
 * @param amount The room size. Omit to reinterpret the pattern's values as room size.
 * @return A new [PatternMapperFn] chaining this room size after the previous mapper.
 *
 * ```KlangScript
 * note("c3 e3").apply(room(0.5).sz(4))   // set wet mix then room size
 * ```
 *
 * ```KlangScript
 * note("c3*4").every(4, room(0.8).sz(8))  // big reverb every 4th cycle
 * ```
 */
@StrudelDsl
fun PatternMapperFn.sz(amount: PatternLike? = null): PatternMapperFn =
    _sz(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Alias for [roomsize]. Sets the reverb room size for this pattern.
 *
 * @param amount The room size. Omit to reinterpret the pattern's values as room size.
 * @return A new pattern with room size applied.
 *
 * ```KlangScript
 * note("c3 e3").clip(0.5).room(0.5).size(4)   // long reverb tail
 * ```
 *
 * @alias roomsize, rsize, sz
 * @category effects
 * @tags size, roomsize, rsize, sz, reverb, room, tail
 */
@StrudelDsl
fun StrudelPattern.size(amount: PatternLike? = null): StrudelPattern =
    this._size(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Alias for [roomsize]. Parses this string as a pattern and sets the reverb room size.
 *
 * @param amount The room size. Omit to reinterpret the pattern's values as room size.
 *
 * ```KlangScript
 * "c3 e3".size(4).room(0.5).note().clip(0.5)    // long reverb tail on bass notes
 * ```
 */
@StrudelDsl
fun String.size(amount: PatternLike? = null): StrudelPattern =
    this._size(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets the reverb room size. Alias for [roomsize].
 *
 * @param amount The room size. Omit to reinterpret the pattern's values as room size.
 * @return A [PatternMapperFn] that sets the reverb room size.
 *
 * ```KlangScript
 * note("c3 e3").apply(size(4)).clip(0.5)   // long reverb tail via mapper
 * ```
 *
 * @alias roomsize, rsize, sz
 * @category effects
 * @tags size, roomsize, rsize, sz, reverb, room, tail
 */
@StrudelDsl
fun size(amount: PatternLike? = null): PatternMapperFn = _size(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets the reverb room size (alias for roomsize) after the previous mapper.
 *
 * @param amount The room size. Omit to reinterpret the pattern's values as room size.
 * @return A new [PatternMapperFn] chaining this room size after the previous mapper.
 *
 * ```KlangScript
 * note("c3 e3").apply(room(0.5).size(4))   // set wet mix then room size
 * ```
 *
 * ```KlangScript
 * note("c3*4").every(4, room(0.8).size(8))  // big reverb every 4th cycle
 * ```
 */
@StrudelDsl
fun PatternMapperFn.size(amount: PatternLike? = null): PatternMapperFn =
    _size(listOfNotNull(amount).asStrudelDslArgs())

// -- roomfade() / rfade() ---------------------------------------------------------------------------------------------

private val roomFadeMutation = voiceModifier { copy(roomFade = it?.asDoubleOrNull()) }

fun applyRoomFade(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftOrReinterpretNumericalField(args, roomFadeMutation)
}

internal val _roomfade by dslPatternMapper { args, callInfo -> { p -> p._roomfade(args, callInfo) } }
internal val StrudelPattern._roomfade by dslPatternExtension { p, args, /* callInfo */ _ -> applyRoomFade(p, args) }
internal val String._roomfade by dslStringExtension { p, args, callInfo -> p._roomfade(args, callInfo) }
internal val PatternMapperFn._roomfade by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_roomfade(args, callInfo))
}

internal val _rfade by dslPatternMapper { args, callInfo -> { p -> p._rfade(args, callInfo) } }
internal val StrudelPattern._rfade by dslPatternExtension { p, args, /* callInfo */ _ -> applyRoomFade(p, args) }
internal val String._rfade by dslStringExtension { p, args, callInfo -> p._rfade(args, callInfo) }
internal val PatternMapperFn._rfade by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_rfade(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

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
 * ```KlangScript
 * note("c3 e3").room(0.6).roomfade(2.0)    // 2-second reverb fade
 * ```
 *
 * ```KlangScript
 * note("c3*4").roomfade("<0.5 1 2 4>")     // increasing fade time each beat
 * ```
 *
 * ```KlangScript
 * seq("0.5 1 2 4").roomfade()              // reinterpret values as fade time
 * ```
 *
 * @alias rfade
 * @category effects
 * @tags roomfade, rfade, reverb, fade, tail
 */
@StrudelDsl
fun StrudelPattern.roomfade(time: PatternLike? = null): StrudelPattern =
    this._roomfade(listOfNotNull(time).asStrudelDslArgs())

/**
 * Parses this string as a pattern and sets the reverb fade time.
 *
 * When [time] is omitted, the string's numeric values are reinterpreted as fade time.
 *
 * @param time The fade time in seconds. Omit to reinterpret the pattern's values as fade time.
 *
 * ```KlangScript
 * "c3 e3".roomfade(2.0).room(0.6).note()   // 2-second fade on string pattern
 * ```
 */
@StrudelDsl
fun String.roomfade(time: PatternLike? = null): StrudelPattern =
    this._roomfade(listOfNotNull(time).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets the reverb fade time.
 *
 * Use the returned mapper as a transform argument or apply it via `.apply(...)`.
 * When [time] is omitted, the pattern's own numeric values are reinterpreted as fade time.
 *
 * @param time The fade time in seconds. Omit to reinterpret the pattern's values as fade time.
 * @return A [PatternMapperFn] that sets the reverb fade time.
 *
 * ```KlangScript
 * note("c3 e3").apply(roomfade(2.0)).room(0.6)   // 2-second fade via mapper
 * ```
 *
 * ```KlangScript
 * note("c3*4").every(4, roomfade(4.0))            // long fade every 4th cycle
 * ```
 *
 * @alias rfade
 * @category effects
 * @tags roomfade, rfade, reverb, fade, tail
 */
@StrudelDsl
fun roomfade(time: PatternLike? = null): PatternMapperFn = _roomfade(listOfNotNull(time).asStrudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets the reverb fade time after the previous mapper.
 *
 * @param time The fade time in seconds. Omit to reinterpret the pattern's values as fade time.
 * @return A new [PatternMapperFn] chaining this fade time after the previous mapper.
 *
 * ```KlangScript
 * note("c3 e3").apply(room(0.6).roomfade(2.0))   // wet mix then fade time
 * ```
 *
 * ```KlangScript
 * note("c3*4").every(4, room(0.8).roomfade(4.0))  // big reverb every 4th cycle
 * ```
 */
@StrudelDsl
fun PatternMapperFn.roomfade(time: PatternLike? = null): PatternMapperFn =
    _roomfade(listOfNotNull(time).asStrudelDslArgs())

/**
 * Alias for [roomfade]. Sets the reverb fade time in seconds for this pattern.
 *
 * When [time] is omitted, the pattern's own numeric values are reinterpreted as fade time.
 *
 * @param time The fade time in seconds. Omit to reinterpret the pattern's values as fade time.
 * @return A new pattern with the reverb fade time applied.
 *
 * ```KlangScript
 * note("c3 e3").room(0.6).rfade(2.0)    // 2-second reverb fade
 * ```
 *
 * ```KlangScript
 * note("c3*4").rfade("<0.5 1 2 4>")     // increasing fade time each beat
 * ```
 *
 * @alias roomfade
 * @category effects
 * @tags rfade, roomfade, reverb, fade, tail
 */
@StrudelDsl
fun StrudelPattern.rfade(time: PatternLike? = null): StrudelPattern =
    this._rfade(listOfNotNull(time).asStrudelDslArgs())

/**
 * Alias for [roomfade]. Parses this string as a pattern and sets the reverb fade time.
 *
 * When [time] is omitted, the string's numeric values are reinterpreted as fade time.
 *
 * @param time The fade time in seconds. Omit to reinterpret the pattern's values as fade time.
 *
 * ```KlangScript
 * "c3 e3".rfade(2.0).room(0.6).note()   // 2-second fade on string pattern
 * ```
 */
@StrudelDsl
fun String.rfade(time: PatternLike? = null): StrudelPattern =
    this._rfade(listOfNotNull(time).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets the reverb fade time. Alias for [roomfade].
 *
 * @param time The fade time in seconds.
 * @return A [PatternMapperFn] that sets the reverb fade time.
 *
 * ```KlangScript
 * note("c3 e3").apply(rfade(2.0)).room(0.6)   // 2-second fade via mapper
 * ```
 *
 * ```KlangScript
 * note("c3*4").every(4, rfade(4.0))            // long fade every 4th cycle
 * ```
 *
 * @alias roomfade
 * @category effects
 * @tags rfade, roomfade, reverb, fade, tail
 */
@StrudelDsl
fun rfade(time: PatternLike? = null): PatternMapperFn = _rfade(listOfNotNull(time).asStrudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets the reverb fade time (alias for roomfade) after the previous mapper.
 *
 * @param time The fade time in seconds. Omit to reinterpret the pattern's values as fade time.
 * @return A new [PatternMapperFn] chaining this fade time after the previous mapper.
 *
 * ```KlangScript
 * note("c3 e3").apply(room(0.6).rfade(2.0))   // wet mix then fade time
 * ```
 *
 * ```KlangScript
 * note("c3*4").every(4, room(0.8).rfade(4.0))  // big reverb every 4th cycle
 * ```
 */
@StrudelDsl
fun PatternMapperFn.rfade(time: PatternLike? = null): PatternMapperFn =
    _rfade(listOfNotNull(time).asStrudelDslArgs())

// -- roomlp() / rlp() -------------------------------------------------------------------------------------------------

private val roomLpMutation = voiceModifier { copy(roomLp = it?.asDoubleOrNull()) }

fun applyRoomLp(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftOrReinterpretNumericalField(args, roomLpMutation)
}

internal val _roomlp by dslPatternMapper { args, callInfo -> { p -> p._roomlp(args, callInfo) } }
internal val StrudelPattern._roomlp by dslPatternExtension { p, args, /* callInfo */ _ -> applyRoomLp(p, args) }
internal val String._roomlp by dslStringExtension { p, args, callInfo -> p._roomlp(args, callInfo) }
internal val PatternMapperFn._roomlp by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_roomlp(args, callInfo))
}

internal val _rlp by dslPatternMapper { args, callInfo -> { p -> p._rlp(args, callInfo) } }
internal val StrudelPattern._rlp by dslPatternExtension { p, args, /* callInfo */ _ -> applyRoomLp(p, args) }
internal val String._rlp by dslStringExtension { p, args, callInfo -> p._rlp(args, callInfo) }
internal val PatternMapperFn._rlp by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_rlp(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

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
 * ```KlangScript
 * note("c3 e3").room(0.6).roomlp(4000)           // dark reverb tail
 * ```
 *
 * ```KlangScript
 * note("c3*4").roomlp("<8000 4000 2000 1000>")   // increasingly dark reverb
 * ```
 *
 * ```KlangScript
 * seq("8000 4000 2000 1000").roomlp()             // reinterpret values as lowpass frequency
 * ```
 *
 * @alias rlp
 * @category effects
 * @tags roomlp, rlp, reverb, lowpass, filter
 */
@StrudelDsl
fun StrudelPattern.roomlp(freq: PatternLike? = null): StrudelPattern =
    this._roomlp(listOfNotNull(freq).asStrudelDslArgs())

/**
 * Parses this string as a pattern and sets the reverb lowpass start frequency.
 *
 * When [freq] is omitted, the string's numeric values are reinterpreted as the frequency.
 *
 * @param freq The lowpass filter start frequency in Hz.
 *   Omit to reinterpret the pattern's values as lowpass frequency.
 *
 * ```KlangScript
 * "c3 e3".roomlp(4000).room(0.6).note()   // dark reverb on string pattern
 * ```
 */
@StrudelDsl
fun String.roomlp(freq: PatternLike? = null): StrudelPattern =
    this._roomlp(listOfNotNull(freq).asStrudelDslArgs())

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
 * ```KlangScript
 * note("c3 e3").apply(roomlp(4000)).room(0.6)   // dark reverb via mapper
 * ```
 *
 * ```KlangScript
 * note("c3*4").every(4, roomlp(1000))            // very dark reverb every 4th cycle
 * ```
 *
 * @alias rlp
 * @category effects
 * @tags roomlp, rlp, reverb, lowpass, filter
 */
@StrudelDsl
fun roomlp(freq: PatternLike? = null): PatternMapperFn = _roomlp(listOfNotNull(freq).asStrudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets the reverb lowpass frequency after the previous mapper.
 *
 * @param freq The lowpass filter start frequency in Hz.
 *   Omit to reinterpret the pattern's values as lowpass frequency.
 * @return A new [PatternMapperFn] chaining this lowpass frequency after the previous mapper.
 *
 * ```KlangScript
 * note("c3 e3").apply(room(0.6).roomlp(4000))   // wet mix then dark reverb
 * ```
 *
 * ```KlangScript
 * note("c3*4").every(4, room(0.8).roomlp(1000))  // very dark reverb every 4th cycle
 * ```
 */
@StrudelDsl
fun PatternMapperFn.roomlp(freq: PatternLike? = null): PatternMapperFn =
    _roomlp(listOfNotNull(freq).asStrudelDslArgs())

/**
 * Alias for [roomlp]. Sets the reverb lowpass start frequency in Hz for this pattern.
 *
 * When [freq] is omitted, the pattern's own numeric values are reinterpreted as the frequency.
 *
 * @param freq The lowpass filter start frequency in Hz.
 *   Omit to reinterpret the pattern's values as lowpass frequency.
 * @return A new pattern with the reverb lowpass frequency applied.
 *
 * ```KlangScript
 * note("c3 e3").room(0.6).rlp(4000)           // dark reverb tail
 * ```
 *
 * ```KlangScript
 * note("c3*4").rlp("<8000 4000 2000 1000>")   // increasingly dark reverb
 * ```
 *
 * @alias roomlp
 * @category effects
 * @tags rlp, roomlp, reverb, lowpass, filter
 */
@StrudelDsl
fun StrudelPattern.rlp(freq: PatternLike? = null): StrudelPattern =
    this._rlp(listOfNotNull(freq).asStrudelDslArgs())

/**
 * Alias for [roomlp]. Parses this string as a pattern and sets the reverb lowpass start frequency.
 *
 * When [freq] is omitted, the string's numeric values are reinterpreted as the frequency.
 *
 * @param freq The lowpass filter start frequency in Hz.
 *   Omit to reinterpret the pattern's values as lowpass frequency.
 *
 * ```KlangScript
 * "c3 e3".rlp(4000).room(0.6).note()   // dark reverb on string pattern
 * ```
 */
@StrudelDsl
fun String.rlp(freq: PatternLike? = null): StrudelPattern =
    this._rlp(listOfNotNull(freq).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets the reverb lowpass start frequency. Alias for [roomlp].
 *
 * When [freq] is omitted, the pattern's own numeric values are reinterpreted as the frequency.
 *
 * @param freq The lowpass filter start frequency in Hz.
 *   Omit to reinterpret the pattern's values as lowpass frequency.
 * @return A [PatternMapperFn] that sets the reverb lowpass frequency.
 *
 * ```KlangScript
 * note("c3 e3").apply(rlp(4000)).room(0.6)   // dark reverb via mapper
 * ```
 *
 * ```KlangScript
 * note("c3*4").every(4, rlp(1000))            // very dark reverb every 4th cycle
 * ```
 *
 * @alias roomlp
 * @category effects
 * @tags rlp, roomlp, reverb, lowpass, filter
 */
@StrudelDsl
fun rlp(freq: PatternLike? = null): PatternMapperFn = _rlp(listOfNotNull(freq).asStrudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets the reverb lowpass frequency (alias for roomlp) after the previous mapper.
 *
 * @param freq The lowpass filter start frequency in Hz.
 *   Omit to reinterpret the pattern's values as lowpass frequency.
 * @return A new [PatternMapperFn] chaining this lowpass frequency after the previous mapper.
 *
 * ```KlangScript
 * note("c3 e3").apply(room(0.6).rlp(4000))   // wet mix then dark reverb
 * ```
 *
 * ```KlangScript
 * note("c3*4").every(4, room(0.8).rlp(1000))  // very dark reverb every 4th cycle
 * ```
 */
@StrudelDsl
fun PatternMapperFn.rlp(freq: PatternLike? = null): PatternMapperFn =
    _rlp(listOfNotNull(freq).asStrudelDslArgs())

// -- roomdim() / rdim() -----------------------------------------------------------------------------------------------

private val roomDimMutation = voiceModifier { copy(roomDim = it?.asDoubleOrNull()) }

fun applyRoomDim(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftOrReinterpretNumericalField(args, roomDimMutation)
}

internal val _roomdim by dslPatternMapper { args, callInfo -> { p -> p._roomdim(args, callInfo) } }
internal val StrudelPattern._roomdim by dslPatternExtension { p, args, /* callInfo */ _ -> applyRoomDim(p, args) }
internal val String._roomdim by dslStringExtension { p, args, callInfo -> p._roomdim(args, callInfo) }
internal val PatternMapperFn._roomdim by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_roomdim(args, callInfo))
}

internal val _rdim by dslPatternMapper { args, callInfo -> { p -> p._rdim(args, callInfo) } }
internal val StrudelPattern._rdim by dslPatternExtension { p, args, /* callInfo */ _ -> applyRoomDim(p, args) }
internal val String._rdim by dslStringExtension { p, args, callInfo -> p._rdim(args, callInfo) }
internal val PatternMapperFn._rdim by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_rdim(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

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
 * ```KlangScript
 * note("c3 e3").room(0.6).roomdim(500)   // very dark, dim reverb
 * ```
 *
 * ```KlangScript
 * note("c3*4").roomdim("<2000 1000 500 200>")   // progressively dimmer reverb
 * ```
 *
 * ```KlangScript
 * seq("2000 1000 500 200").roomdim()   // reinterpret values as -60 dB frequency
 * ```
 *
 * @alias rdim
 * @category effects
 * @tags roomdim, rdim, reverb, lowpass, darkness
 */
@StrudelDsl
fun StrudelPattern.roomdim(freq: PatternLike? = null): StrudelPattern =
    this._roomdim(listOfNotNull(freq).asStrudelDslArgs())

/**
 * Parses this string as a pattern and sets the reverb lowpass frequency at -60 dB.
 *
 * When [freq] is omitted, the string's numeric values are reinterpreted as the frequency.
 *
 * @param freq The -60 dB lowpass frequency in Hz. Omit to reinterpret the pattern's values as frequency.
 *
 * ```KlangScript
 * "c3 e3".roomdim(500).room(0.6).note()   // very dark reverb on string pattern
 * ```
 */
@StrudelDsl
fun String.roomdim(freq: PatternLike? = null): StrudelPattern =
    this._roomdim(listOfNotNull(freq).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets the reverb lowpass frequency at -60 dB.
 *
 * Use the returned mapper as a transform argument or apply it via `.apply(...)`.
 * When [freq] is omitted, the pattern's own numeric values are reinterpreted as the frequency.
 *
 * @param freq The -60 dB lowpass frequency in Hz. Omit to reinterpret the pattern's values as frequency.
 * @return A [PatternMapperFn] that sets the reverb -60 dB frequency.
 *
 * ```KlangScript
 * note("c3 e3").apply(roomdim(500)).room(0.6)   // very dark reverb via mapper
 * ```
 *
 * ```KlangScript
 * note("c3*4").every(4, roomdim(200))            // very dim reverb every 4th cycle
 * ```
 *
 * @alias rdim
 * @category effects
 * @tags roomdim, rdim, reverb, lowpass, darkness
 */
@StrudelDsl
fun roomdim(freq: PatternLike? = null): PatternMapperFn = _roomdim(listOfNotNull(freq).asStrudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets the reverb lowpass frequency at -60 dB after the previous mapper.
 *
 * @param freq The -60 dB lowpass frequency in Hz. Omit to reinterpret the pattern's values as frequency.
 * @return A new [PatternMapperFn] chaining this frequency after the previous mapper.
 *
 * ```KlangScript
 * note("c3 e3").apply(room(0.6).roomdim(500))   // wet mix then dim frequency
 * ```
 *
 * ```KlangScript
 * note("c3*4").every(4, room(0.8).roomdim(200))  // very dim reverb every 4th cycle
 * ```
 */
@StrudelDsl
fun PatternMapperFn.roomdim(freq: PatternLike? = null): PatternMapperFn =
    _roomdim(listOfNotNull(freq).asStrudelDslArgs())

/**
 * Alias for [roomdim]. Sets the reverb lowpass frequency at -60 dB for this pattern.
 *
 * When [freq] is omitted, the pattern's own numeric values are reinterpreted as the frequency.
 *
 * @param freq The -60 dB lowpass frequency in Hz. Omit to reinterpret the pattern's values as frequency.
 * @return A new pattern with the reverb -60 dB frequency applied.
 *
 * ```KlangScript
 * note("c3 e3").room(0.6).rdim(500)   // very dark, dim reverb
 * ```
 *
 * ```KlangScript
 * note("c3*4").rdim("<2000 1000 500 200>")   // progressively dimmer reverb
 * ```
 *
 * ```KlangScript
 * seq("2000 1000 500 200").rdim()   // reinterpret values as -60 dB frequency
 * ```
 *
 * @alias roomdim
 * @category effects
 * @tags rdim, roomdim, reverb, lowpass, darkness
 */
@StrudelDsl
fun StrudelPattern.rdim(freq: PatternLike? = null): StrudelPattern =
    this._rdim(listOfNotNull(freq).asStrudelDslArgs())

/**
 * Alias for [roomdim]. Parses this string as a pattern and sets the reverb lowpass frequency at -60 dB.
 *
 * When [freq] is omitted, the string's numeric values are reinterpreted as the frequency.
 *
 * @param freq The -60 dB lowpass frequency in Hz. Omit to reinterpret the pattern's values as frequency.
 *
 * ```KlangScript
 * "c3 e3".rdim(500).room(0.6).note()   // very dark reverb on string pattern
 * ```
 */
@StrudelDsl
fun String.rdim(freq: PatternLike? = null): StrudelPattern =
    this._rdim(listOfNotNull(freq).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets the reverb lowpass frequency at -60 dB. Alias for [roomdim].
 *
 * @param freq The -60 dB lowpass frequency in Hz. Omit to reinterpret the pattern's values as frequency.
 * @return A [PatternMapperFn] that sets the reverb -60 dB frequency.
 *
 * ```KlangScript
 * note("c3 e3").apply(rdim(500)).room(0.6)   // very dark reverb via mapper
 * ```
 *
 * @alias roomdim
 * @category effects
 * @tags rdim, roomdim, reverb, lowpass, darkness
 */
@StrudelDsl
fun rdim(freq: PatternLike? = null): PatternMapperFn = _rdim(listOfNotNull(freq).asStrudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets the reverb -60 dB frequency (alias for roomdim) after the previous
 * mapper.
 *
 * @param freq The -60 dB lowpass frequency in Hz. Omit to reinterpret the pattern's values as frequency.
 * @return A new [PatternMapperFn] chaining this frequency after the previous mapper.
 *
 * ```KlangScript
 * note("c3 e3").apply(room(0.6).rdim(500))   // wet mix then dim frequency
 * ```
 *
 * ```KlangScript
 * note("c3*4").every(4, room(0.8).rdim(200))  // very dim reverb every 4th cycle
 * ```
 */
@StrudelDsl
fun PatternMapperFn.rdim(freq: PatternLike? = null): PatternMapperFn =
    _rdim(listOfNotNull(freq).asStrudelDslArgs())

// -- iresponse() / ir() -----------------------------------------------------------------------------------------------

private val iResponseMutation = voiceModifier { response -> copy(iResponse = response?.toString()) }

fun applyIResponse(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._applyControlFromParams(args, iResponseMutation) { src, ctrl ->
        src.copy(iResponse = ctrl.iResponse)
    }
}

internal val _iresponse by dslPatternMapper { args, callInfo -> { p -> p._iresponse(args, callInfo) } }
internal val StrudelPattern._iresponse by dslPatternExtension { p, args, /* callInfo */ _ -> applyIResponse(p, args) }
internal val String._iresponse by dslStringExtension { p, args, callInfo -> p._iresponse(args, callInfo) }
internal val PatternMapperFn._iresponse by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_iresponse(args, callInfo))
}

internal val _ir by dslPatternMapper { args, callInfo -> { p -> p._ir(args, callInfo) } }
internal val StrudelPattern._ir by dslPatternExtension { p, args, /* callInfo */ _ -> applyIResponse(p, args) }
internal val String._ir by dslStringExtension { p, args, callInfo -> p._ir(args, callInfo) }
internal val PatternMapperFn._ir by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_ir(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the impulse response sample name for convolution reverb on this pattern.
 *
 * Uses a recorded impulse response to simulate the acoustics of a real space. The value
 * is the name of an IR sample loaded in the audio engine.
 *
 * @param name The name of the impulse response sample.
 * @return A new pattern with the impulse response applied.
 *
 * ```KlangScript
 * note("c3 e3 g3").iresponse("church")     // church reverb IR
 * ```
 *
 * ```KlangScript
 * note("c3*4").iresponse("<room hall plate>")  // cycle through IR types
 * ```
 *
 * @alias ir
 * @category effects
 * @tags iresponse, ir, impulse, convolution, reverb
 */
@StrudelDsl
fun StrudelPattern.iresponse(name: PatternLike): StrudelPattern = this._iresponse(listOf(name).asStrudelDslArgs())

/**
 * Parses this string as a pattern and sets the impulse response sample for convolution reverb.
 *
 * @param name The name of the impulse response sample.
 *
 * ```KlangScript
 * "c3 e3 g3".iresponse("church").note()   // church reverb IR on string pattern
 * ```
 */
@StrudelDsl
fun String.iresponse(name: PatternLike): StrudelPattern = this._iresponse(listOf(name).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets the impulse response sample for convolution reverb.
 *
 * Use the returned mapper as a transform argument or apply it via `.apply(...)`.
 *
 * @param name The name of the impulse response sample.
 * @return A [PatternMapperFn] that sets the impulse response.
 *
 * ```KlangScript
 * note("c3 e3 g3").apply(iresponse("church"))   // church reverb via mapper
 * ```
 *
 * ```KlangScript
 * note("c3*4").every(4, iresponse("hall"))   // hall reverb every 4th cycle
 * ```
 *
 * @alias ir
 * @category effects
 * @tags iresponse, ir, impulse, convolution, reverb
 */
@StrudelDsl
fun iresponse(name: PatternLike): PatternMapperFn = _iresponse(listOf(name).asStrudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets the impulse response after the previous mapper.
 *
 * @param name The name of the impulse response sample.
 * @return A new [PatternMapperFn] chaining this impulse response after the previous mapper.
 *
 * ```KlangScript
 * note("c3 e3").apply(room(0.5).iresponse("church"))   // room then IR reverb
 * ```
 *
 * ```KlangScript
 * note("c3*4").every(4, room(0.8).iresponse("hall"))   // hall IR every 4th cycle
 * ```
 */
@StrudelDsl
fun PatternMapperFn.iresponse(name: PatternLike): PatternMapperFn = _iresponse(listOf(name).asStrudelDslArgs())

/**
 * Alias for [iresponse]. Sets the impulse response sample name for convolution reverb on this pattern.
 *
 * @param name The name of the impulse response sample.
 * @return A new pattern with the impulse response applied.
 *
 * ```KlangScript
 * note("c3 e3 g3").ir("church")     // church reverb IR
 * ```
 *
 * ```KlangScript
 * note("c3*4").ir("<room hall plate>")  // cycle through IR types
 * ```
 *
 * @alias iresponse
 * @category effects
 * @tags ir, iresponse, impulse, convolution, reverb
 */
@StrudelDsl
fun StrudelPattern.ir(name: PatternLike): StrudelPattern = this._ir(listOf(name).asStrudelDslArgs())

/**
 * Alias for [iresponse]. Parses this string as a pattern and sets the impulse response sample.
 *
 * @param name The name of the impulse response sample.
 *
 * ```KlangScript
 * "c3 e3 g3".ir("church").note()   // church reverb IR on string pattern
 * ```
 */
@StrudelDsl
fun String.ir(name: PatternLike): StrudelPattern = this._ir(listOf(name).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets the impulse response sample. Alias for [iresponse].
 *
 * @param name The name of the impulse response sample.
 * @return A [PatternMapperFn] that sets the impulse response.
 *
 * ```KlangScript
 * note("c3 e3 g3").apply(ir("church"))   // church reverb via mapper
 * ```
 *
 * @alias iresponse
 * @category effects
 * @tags ir, iresponse, impulse, convolution, reverb
 */
@StrudelDsl
fun ir(name: PatternLike): PatternMapperFn = _ir(listOf(name).asStrudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets the impulse response (alias for iresponse) after the previous mapper.
 *
 * @param name The name of the impulse response sample.
 * @return A new [PatternMapperFn] chaining this impulse response after the previous mapper.
 *
 * ```KlangScript
 * note("c3 e3").apply(room(0.5).ir("church"))   // room then IR reverb
 * ```
 *
 * ```KlangScript
 * note("c3*4").every(4, room(0.8).ir("hall"))   // hall IR every 4th cycle
 * ```
 */
@StrudelDsl
fun PatternMapperFn.ir(name: PatternLike): PatternMapperFn = _ir(listOf(name).asStrudelDslArgs())

// -- delay() ----------------------------------------------------------------------------------------------------------

private val delayMutation = voiceModifier { copy(delay = it?.asDoubleOrNull()) }

fun applyDelay(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftOrReinterpretNumericalField(args, delayMutation)
}

internal val _delay by dslPatternMapper { args, callInfo -> { p -> p._delay(args, callInfo) } }
internal val StrudelPattern._delay by dslPatternExtension { p, args, /* callInfo */ _ -> applyDelay(p, args) }
internal val String._delay by dslStringExtension { p, args, callInfo -> p._delay(args, callInfo) }
internal val PatternMapperFn._delay by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_delay(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the delay wet/dry mix for this pattern (0 = dry, 1 = full wet).
 *
 * Use with `delaytime` to set the delay interval and `delayfeedback` to control
 * the number of repeats.
 * When [amount] is omitted, the pattern's own numeric values are reinterpreted as delay mix.
 *
 * @param amount The wet/dry mix (0–1). Omit to reinterpret the pattern's values as delay mix.
 * @return A new pattern with the delay mix applied.
 *
 * ```KlangScript
 * note("c3 e3").delay(0.4)                         // 40% delay mix
 * ```
 *
 * ```KlangScript
 * note("c3*4").delay(0.5).delaytime(0.25).delayfeedback(0.6)  // dotted-eighth delay
 * ```
 *
 * ```KlangScript
 * seq("0 0.25 0.5 1.0").delay()   // reinterpret values as delay mix
 * ```
 *
 * @param-tool amount StrudelGainSequenceEditor
 * @category effects
 * @tags delay, echo, wet, mix
 */
@StrudelDsl
fun StrudelPattern.delay(amount: PatternLike? = null): StrudelPattern =
    this._delay(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Parses this string as a pattern and sets the delay wet/dry mix.
 *
 * When [amount] is omitted, the string's numeric values are reinterpreted as delay mix.
 *
 * @param amount The wet/dry mix (0–1). Omit to reinterpret the pattern's values as delay mix.
 *
 * ```KlangScript
 * "c3 e3".delay(0.4).note()   // 40% delay mix on string pattern
 * ```
 */
@StrudelDsl
fun String.delay(amount: PatternLike? = null): StrudelPattern =
    this._delay(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets the delay wet/dry mix.
 *
 * Use the returned mapper as a transform argument or apply it via `.apply(...)`.
 * When [amount] is omitted, the pattern's own numeric values are reinterpreted as delay mix.
 *
 * @param amount The wet/dry mix (0–1). Omit to reinterpret the pattern's values as delay mix.
 * @return A [PatternMapperFn] that sets the delay mix.
 *
 * ```KlangScript
 * note("c3 e3").apply(delay(0.4))   // 40% delay mix via mapper
 * ```
 *
 * ```KlangScript
 * note("c3*4").every(4, delay(0.8))   // heavy delay every 4th cycle
 * ```
 *
 * @category effects
 * @tags delay, echo, wet, mix
 */
@StrudelDsl
fun delay(amount: PatternLike? = null): PatternMapperFn = _delay(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets the delay wet/dry mix after the previous mapper.
 *
 * @param amount The wet/dry mix (0–1). Omit to reinterpret the pattern's values as delay mix.
 * @return A new [PatternMapperFn] chaining this delay mix after the previous mapper.
 *
 * ```KlangScript
 * note("c3 e3").apply(delaytime(0.25).delay(0.5))   // set delay time then mix
 * ```
 *
 * ```KlangScript
 * note("c3*4").every(4, delaytime(0.25).delay(0.8))   // delay every 4th cycle
 * ```
 */
@StrudelDsl
fun PatternMapperFn.delay(amount: PatternLike? = null): PatternMapperFn =
    _delay(listOfNotNull(amount).asStrudelDslArgs())

// -- delaytime() ------------------------------------------------------------------------------------------------------

private val delayTimeMutation = voiceModifier { copy(delayTime = it?.asDoubleOrNull()) }

fun applyDelayTime(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftOrReinterpretNumericalField(args, delayTimeMutation)
}

internal val _delaytime by dslPatternMapper { args, callInfo -> { p -> p._delaytime(args, callInfo) } }
internal val StrudelPattern._delaytime by dslPatternExtension { p, args, /* callInfo */ _ -> applyDelayTime(p, args) }
internal val String._delaytime by dslStringExtension { p, args, callInfo -> p._delaytime(args, callInfo) }
internal val PatternMapperFn._delaytime by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_delaytime(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

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
 * ```KlangScript
 * note("c3").delay(0.5).delaytime(0.375)   // dotted-eighth delay
 * ```
 *
 * ```KlangScript
 * note("c3*4").delaytime("<0.125 0.25 0.5>")   // varying delay times
 * ```
 *
 * ```KlangScript
 * seq("0.125 0.25 0.5").delaytime()   // reinterpret values as delay time
 * ```
 *
 * @param-tool time StrudelGainSequenceEditor
 * @category effects
 * @tags delaytime, delay, echo, time, interval
 */
@StrudelDsl
fun StrudelPattern.delaytime(time: PatternLike? = null): StrudelPattern =
    this._delaytime(listOfNotNull(time).asStrudelDslArgs())

/**
 * Parses this string as a pattern and sets the delay time.
 *
 * When [time] is omitted, the string's numeric values are reinterpreted as delay time.
 *
 * @param time The delay interval in seconds. Omit to reinterpret the pattern's values as delay time.
 *
 * ```KlangScript
 * "c3 e3".delaytime(0.25).delay(0.5).note()   // quarter-note delay on string pattern
 * ```
 */
@StrudelDsl
fun String.delaytime(time: PatternLike? = null): StrudelPattern =
    this._delaytime(listOfNotNull(time).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets the delay time in seconds.
 *
 * Use the returned mapper as a transform argument or apply it via `.apply(...)`.
 * When [time] is omitted, the pattern's own numeric values are reinterpreted as delay time.
 *
 * @param time The delay interval in seconds. Omit to reinterpret the pattern's values as delay time.
 * @return A [PatternMapperFn] that sets the delay time.
 *
 * ```KlangScript
 * note("c3 e3").apply(delaytime(0.25))   // quarter-note delay via mapper
 * ```
 *
 * ```KlangScript
 * note("c3*4").every(4, delaytime(0.125))   // eighth-note delay every 4th cycle
 * ```
 *
 * @category effects
 * @tags delaytime, delay, echo, time, interval
 */
@StrudelDsl
fun delaytime(time: PatternLike? = null): PatternMapperFn = _delaytime(listOfNotNull(time).asStrudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets the delay time after the previous mapper.
 *
 * @param time The delay interval in seconds. Omit to reinterpret the pattern's values as delay time.
 * @return A new [PatternMapperFn] chaining this delay time after the previous mapper.
 *
 * ```KlangScript
 * note("c3 e3").apply(delay(0.5).delaytime(0.25))   // delay mix then time
 * ```
 *
 * ```KlangScript
 * note("c3*4").every(4, delay(0.8).delaytime(0.125))   // delay every 4th cycle
 * ```
 */
@StrudelDsl
fun PatternMapperFn.delaytime(time: PatternLike? = null): PatternMapperFn =
    _delaytime(listOfNotNull(time).asStrudelDslArgs())

// -- delayfeedback() / delayfb() / dfb() ------------------------------------------------------------------------------

private val delayFeedbackMutation = voiceModifier { copy(delayFeedback = it?.asDoubleOrNull()) }

fun applyDelayFeedback(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftOrReinterpretNumericalField(args, delayFeedbackMutation)
}

internal val _delayfeedback by dslPatternMapper { args, callInfo -> { p -> p._delayfeedback(args, callInfo) } }
internal val StrudelPattern._delayfeedback by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyDelayFeedback(p, args)
}
internal val String._delayfeedback by dslStringExtension { p, args, callInfo -> p._delayfeedback(args, callInfo) }
internal val PatternMapperFn._delayfeedback by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_delayfeedback(args, callInfo))
}

internal val _delayfb by dslPatternMapper { args, callInfo -> { p -> p._delayfb(args, callInfo) } }
internal val StrudelPattern._delayfb by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyDelayFeedback(p, args)
}
internal val String._delayfb by dslStringExtension { p, args, callInfo -> p._delayfb(args, callInfo) }
internal val PatternMapperFn._delayfb by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_delayfb(args, callInfo))
}

internal val _dfb by dslPatternMapper { args, callInfo -> { p -> p._dfb(args, callInfo) } }
internal val StrudelPattern._dfb by dslPatternExtension { p, args, /* callInfo */ _ -> applyDelayFeedback(p, args) }
internal val String._dfb by dslStringExtension { p, args, callInfo -> p._dfb(args, callInfo) }
internal val PatternMapperFn._dfb by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_dfb(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

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
 * ```KlangScript
 * note("c3").delay(0.4).delaytime(0.25).delayfeedback(0.6)   // 3-4 echoes
 * ```
 *
 * ```KlangScript
 * note("c3*4").delayfeedback("<0.2 0.4 0.6 0.8>")            // increasing echoes
 * ```
 *
 * ```KlangScript
 * seq("0.2 0.4 0.6 0.8").delayfeedback()   // reinterpret values as feedback
 * ```
 *
 * @param-tool amount StrudelGainSequenceEditor
 * @alias delayfb, dfb
 * @category effects
 * @tags delayfeedback, delayfb, dfb, delay, echo, feedback, repeats
 */
@StrudelDsl
fun StrudelPattern.delayfeedback(amount: PatternLike? = null): StrudelPattern =
    this._delayfeedback(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Parses this string as a pattern and sets the delay feedback amount.
 *
 * When [amount] is omitted, the string's numeric values are reinterpreted as feedback.
 *
 * @param amount The feedback amount (0–1). Omit to reinterpret the pattern's values as feedback.
 *
 * ```KlangScript
 * "c3 e3".delayfeedback(0.6).delay(0.4).note()   // echoing string pattern
 * ```
 */
@StrudelDsl
fun String.delayfeedback(amount: PatternLike? = null): StrudelPattern =
    this._delayfeedback(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets the delay feedback amount.
 *
 * Use the returned mapper as a transform argument or apply it via `.apply(...)`.
 * When [amount] is omitted, the pattern's own numeric values are reinterpreted as feedback.
 *
 * @param amount The feedback amount (0–1). Omit to reinterpret the pattern's values as feedback.
 * @return A [PatternMapperFn] that sets the delay feedback.
 *
 * ```KlangScript
 * note("c3 e3").apply(delayfeedback(0.6))   // feedback via mapper
 * ```
 *
 * ```KlangScript
 * note("c3*4").every(4, delayfeedback(0.8))   // lots of echoes every 4th cycle
 * ```
 *
 * @alias delayfb, dfb
 * @category effects
 * @tags delayfeedback, delayfb, dfb, delay, echo, feedback, repeats
 */
@StrudelDsl
fun delayfeedback(amount: PatternLike? = null): PatternMapperFn =
    _delayfeedback(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets the delay feedback amount after the previous mapper.
 *
 * @param amount The feedback amount (0–1). Omit to reinterpret the pattern's values as feedback.
 * @return A new [PatternMapperFn] chaining this feedback after the previous mapper.
 *
 * ```KlangScript
 * note("c3 e3").apply(delay(0.5).delayfeedback(0.6))   // mix then feedback
 * ```
 *
 * ```KlangScript
 * note("c3*4").every(4, delay(0.8).delayfeedback(0.9))   // echoing delay every 4th cycle
 * ```
 */
@StrudelDsl
fun PatternMapperFn.delayfeedback(amount: PatternLike? = null): PatternMapperFn =
    _delayfeedback(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Alias for [delayfeedback]. Sets the delay feedback amount (0–1) for this pattern.
 *
 * When [amount] is omitted, the pattern's own numeric values are reinterpreted as feedback.
 *
 * @param amount The feedback amount (0–1). Omit to reinterpret the pattern's values as feedback.
 * @return A new pattern with the delay feedback applied.
 *
 * ```KlangScript
 * note("c3").delay(0.4).delaytime(0.25).delayfb(0.6)   // 3-4 echoes
 * ```
 *
 * ```KlangScript
 * note("c3*4").delayfb("<0.2 0.4 0.6 0.8>")            // increasing echoes
 * ```
 *
 * ```KlangScript
 * seq("0.2 0.4 0.6 0.8").delayfb()   // reinterpret values as feedback
 * ```
 *
 * @param-tool amount StrudelGainSequenceEditor
 * @alias delayfeedback, dfb
 * @category effects
 * @tags delayfb, delayfeedback, dfb, delay, echo, feedback, repeats
 */
@StrudelDsl
fun StrudelPattern.delayfb(amount: PatternLike? = null): StrudelPattern =
    this._delayfb(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Alias for [delayfeedback]. Parses this string as a pattern and sets the delay feedback amount.
 *
 * When [amount] is omitted, the string's numeric values are reinterpreted as feedback.
 *
 * @param amount The feedback amount (0–1). Omit to reinterpret the pattern's values as feedback.
 *
 * ```KlangScript
 * "c3 e3".delayfb(0.6).delay(0.4).note()   // echoing string pattern
 * ```
 */
@StrudelDsl
fun String.delayfb(amount: PatternLike? = null): StrudelPattern =
    this._delayfb(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets the delay feedback amount. Alias for [delayfeedback].
 *
 * @param amount The feedback amount (0–1). Omit to reinterpret the pattern's values as feedback.
 * @return A [PatternMapperFn] that sets the delay feedback.
 *
 * ```KlangScript
 * note("c3 e3").apply(delayfb(0.6))   // feedback via mapper
 * ```
 *
 * @alias delayfeedback, dfb
 * @category effects
 * @tags delayfb, delayfeedback, dfb, delay, echo, feedback, repeats
 */
@StrudelDsl
fun delayfb(amount: PatternLike? = null): PatternMapperFn = _delayfb(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets the delay feedback (alias for delayfeedback) after the previous
 * mapper.
 *
 * @param amount The feedback amount (0–1). Omit to reinterpret the pattern's values as feedback.
 * @return A new [PatternMapperFn] chaining this feedback after the previous mapper.
 *
 * ```KlangScript
 * note("c3 e3").apply(delay(0.5).delayfb(0.6))   // mix then feedback
 * ```
 *
 * ```KlangScript
 * note("c3*4").every(4, delay(0.8).delayfb(0.9))   // echoing delay every 4th cycle
 * ```
 */
@StrudelDsl
fun PatternMapperFn.delayfb(amount: PatternLike? = null): PatternMapperFn =
    _delayfb(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Alias for [delayfeedback]. Sets the delay feedback amount (0–1) for this pattern.
 *
 * When [amount] is omitted, the pattern's own numeric values are reinterpreted as feedback.
 *
 * @param amount The feedback amount (0–1). Omit to reinterpret the pattern's values as feedback.
 * @return A new pattern with the delay feedback applied.
 *
 * ```KlangScript
 * note("c3").delay(0.4).delaytime(0.25).dfb(0.6)   // 3-4 echoes
 * ```
 *
 * ```KlangScript
 * note("c3*4").dfb("<0.2 0.4 0.6 0.8>")            // increasing echoes
 * ```
 *
 * ```KlangScript
 * seq("0.2 0.4 0.6 0.8").dfb()   // reinterpret values as feedback
 * ```
 *
 * @alias delayfeedback, delayfb
 * @category effects
 * @tags dfb, delayfeedback, delayfb, delay, echo, feedback, repeats
 */
@StrudelDsl
fun StrudelPattern.dfb(amount: PatternLike? = null): StrudelPattern =
    this._dfb(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Alias for [delayfeedback]. Parses this string as a pattern and sets the delay feedback amount.
 *
 * When [amount] is omitted, the string's numeric values are reinterpreted as feedback.
 *
 * @param amount The feedback amount (0–1). Omit to reinterpret the pattern's values as feedback.
 *
 * ```KlangScript
 * "c3 e3".dfb(0.6).delay(0.4).note()   // echoing string pattern
 * ```
 */
@StrudelDsl
fun String.dfb(amount: PatternLike? = null): StrudelPattern =
    this._dfb(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets the delay feedback amount. Alias for [delayfeedback].
 *
 * @param amount The feedback amount (0–1). Omit to reinterpret the pattern's values as feedback.
 * @return A [PatternMapperFn] that sets the delay feedback.
 *
 * ```KlangScript
 * note("c3 e3").apply(dfb(0.6))   // feedback via mapper
 * ```
 *
 * @alias delayfeedback, delayfb
 * @category effects
 * @tags dfb, delayfeedback, delayfb, delay, echo, feedback, repeats
 */
@StrudelDsl
fun dfb(amount: PatternLike? = null): PatternMapperFn = _dfb(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets the delay feedback (alias for delayfeedback) after the previous
 * mapper.
 *
 * @param amount The feedback amount (0–1). Omit to reinterpret the pattern's values as feedback.
 * @return A new [PatternMapperFn] chaining this feedback after the previous mapper.
 *
 * ```KlangScript
 * note("c3 e3").apply(delay(0.5).dfb(0.6))   // mix then feedback
 * ```
 *
 * ```KlangScript
 * note("c3*4").every(4, delay(0.8).dfb(0.9))   // echoing delay every 4th cycle
 * ```
 */
@StrudelDsl
fun PatternMapperFn.dfb(amount: PatternLike? = null): PatternMapperFn =
    _dfb(listOfNotNull(amount).asStrudelDslArgs())

// -- phaser() / ph() --------------------------------------------------------------------------------------------------

private val phaserMutation = voiceModifier { copy(phaserRate = it?.asDoubleOrNull()) }

fun applyPhaser(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftOrReinterpretNumericalField(args, phaserMutation)
}

internal val _phaser by dslPatternMapper { args, callInfo -> { p -> p._phaser(args, callInfo) } }
internal val StrudelPattern._phaser by dslPatternExtension { p, args, /* callInfo */ _ -> applyPhaser(p, args) }
internal val String._phaser by dslStringExtension { p, args, callInfo -> p._phaser(args, callInfo) }
internal val PatternMapperFn._phaser by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_phaser(args, callInfo))
}

internal val _ph by dslPatternMapper { args, callInfo -> { p -> p._ph(args, callInfo) } }
internal val StrudelPattern._ph by dslPatternExtension { p, args, /* callInfo */ _ -> applyPhaser(p, args) }
internal val String._ph by dslStringExtension { p, args, callInfo -> p._ph(args, callInfo) }
internal val PatternMapperFn._ph by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_ph(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the phaser LFO rate in Hz for this pattern.
 *
 * A phaser creates a sweeping comb-filter effect by modulating a series of all-pass filters.
 * Higher rate values produce faster sweeping. Use with `phaserdepth` and `phasercenter`.
 * When [rate] is omitted, the pattern's own numeric values are reinterpreted as the phaser rate.
 *
 * @param rate The phaser LFO rate in Hz. Omit to reinterpret the pattern's values as phaser rate.
 * @return A new pattern with the phaser rate applied.
 *
 * ```KlangScript
 * note("c3 e3 g3").s("sawtooth").phaser(0.5)   // slow phaser sweep
 * ```
 *
 * ```KlangScript
 * note("c3*4").phaser("<0.1 0.5 1 4>")         // accelerating phaser rate
 * ```
 *
 * ```KlangScript
 * seq("0.1 0.5 1 4").phaser()   // reinterpret values as phaser rate
 * ```
 *
 * @alias ph
 * @category effects
 * @tags phaser, ph, phase, sweep, modulation
 */
@StrudelDsl
fun StrudelPattern.phaser(rate: PatternLike? = null): StrudelPattern =
    this._phaser(listOfNotNull(rate).asStrudelDslArgs())

/**
 * Parses this string as a pattern and sets the phaser LFO rate.
 *
 * When [rate] is omitted, the string's numeric values are reinterpreted as the phaser rate.
 *
 * @param rate The phaser LFO rate in Hz. Omit to reinterpret the pattern's values as phaser rate.
 *
 * ```KlangScript
 * "c3 e3 g3".phaser(0.5).note().s("sawtooth")   // slow phaser on string pattern
 * ```
 */
@StrudelDsl
fun String.phaser(rate: PatternLike? = null): StrudelPattern =
    this._phaser(listOfNotNull(rate).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets the phaser LFO rate.
 *
 * Use the returned mapper as a transform argument or apply it via `.apply(...)`.
 * When [rate] is omitted, the pattern's own numeric values are reinterpreted as the phaser rate.
 *
 * @param rate The phaser LFO rate in Hz. Omit to reinterpret the pattern's values as phaser rate.
 * @return A [PatternMapperFn] that sets the phaser rate.
 *
 * ```KlangScript
 * note("c3 e3 g3").apply(phaser(0.5))   // slow phaser via mapper
 * ```
 *
 * ```KlangScript
 * note("c3*4").every(4, phaser(4.0))   // fast phaser every 4th cycle
 * ```
 *
 * @alias ph
 * @category effects
 * @tags phaser, ph, phase, sweep, modulation
 */
@StrudelDsl
fun phaser(rate: PatternLike? = null): PatternMapperFn = _phaser(listOfNotNull(rate).asStrudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets the phaser LFO rate after the previous mapper.
 *
 * @param rate The phaser LFO rate in Hz. Omit to reinterpret the pattern's values as phaser rate.
 * @return A new [PatternMapperFn] chaining this phaser rate after the previous mapper.
 *
 * ```KlangScript
 * note("c3 e3").apply(phaserdepth(0.8).phaser(0.5))   // depth then rate
 * ```
 *
 * ```KlangScript
 * note("c3*4").every(4, phaserdepth(1.0).phaser(4.0))   // full-depth fast phaser
 * ```
 */
@StrudelDsl
fun PatternMapperFn.phaser(rate: PatternLike? = null): PatternMapperFn =
    _phaser(listOfNotNull(rate).asStrudelDslArgs())

/**
 * Alias for [phaser]. Sets the phaser LFO rate in Hz for this pattern.
 *
 * When [rate] is omitted, the pattern's own numeric values are reinterpreted as the phaser rate.
 *
 * @param rate The phaser LFO rate in Hz. Omit to reinterpret the pattern's values as phaser rate.
 * @return A new pattern with the phaser rate applied.
 *
 * ```KlangScript
 * note("c3 e3 g3").s("sawtooth").ph(0.5)   // slow phaser sweep
 * ```
 *
 * ```KlangScript
 * note("c3*4").ph("<0.1 0.5 1 4>")         // accelerating phaser rate
 * ```
 *
 * ```KlangScript
 * seq("0.1 0.5 1 4").ph()   // reinterpret values as phaser rate
 * ```
 *
 * @alias phaser
 * @category effects
 * @tags ph, phaser, phase, sweep, modulation
 */
@StrudelDsl
fun StrudelPattern.ph(rate: PatternLike? = null): StrudelPattern =
    this._ph(listOfNotNull(rate).asStrudelDslArgs())

/**
 * Alias for [phaser]. Parses this string as a pattern and sets the phaser LFO rate.
 *
 * When [rate] is omitted, the string's numeric values are reinterpreted as the phaser rate.
 *
 * @param rate The phaser LFO rate in Hz. Omit to reinterpret the pattern's values as phaser rate.
 *
 * ```KlangScript
 * "c3 e3 g3".ph(0.5).note().s("sawtooth")   // slow phaser on string pattern
 * ```
 */
@StrudelDsl
fun String.ph(rate: PatternLike? = null): StrudelPattern =
    this._ph(listOfNotNull(rate).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets the phaser LFO rate. Alias for [phaser].
 *
 * @param rate The phaser LFO rate in Hz. Omit to reinterpret the pattern's values as phaser rate.
 * @return A [PatternMapperFn] that sets the phaser rate.
 *
 * ```KlangScript
 * note("c3 e3 g3").apply(ph(0.5))   // slow phaser via mapper
 * ```
 *
 * @alias phaser
 * @category effects
 * @tags ph, phaser, phase, sweep, modulation
 */
@StrudelDsl
fun ph(rate: PatternLike? = null): PatternMapperFn = _ph(listOfNotNull(rate).asStrudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets the phaser LFO rate (alias for phaser) after the previous mapper.
 *
 * @param rate The phaser LFO rate in Hz. Omit to reinterpret the pattern's values as phaser rate.
 * @return A new [PatternMapperFn] chaining this phaser rate after the previous mapper.
 *
 * ```KlangScript
 * note("c3 e3").apply(phaserdepth(0.8).ph(0.5))   // depth then rate
 * ```
 *
 * ```KlangScript
 * note("c3*4").every(4, phaserdepth(1.0).ph(4.0))   // full-depth fast phaser
 * ```
 */
@StrudelDsl
fun PatternMapperFn.ph(rate: PatternLike? = null): PatternMapperFn =
    _ph(listOfNotNull(rate).asStrudelDslArgs())

// -- phaserdepth() / phd() / phasdp() ---------------------------------------------------------------------------------

private val phaserDepthMutation = voiceModifier { copy(phaserDepth = it?.asDoubleOrNull()) }

fun applyPhaserDepth(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftOrReinterpretNumericalField(args, phaserDepthMutation)
}

internal val _phaserdepth by dslPatternMapper { args, callInfo -> { p -> p._phaserdepth(args, callInfo) } }
internal val StrudelPattern._phaserdepth by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyPhaserDepth(p, args)
}
internal val String._phaserdepth by dslStringExtension { p, args, callInfo -> p._phaserdepth(args, callInfo) }
internal val PatternMapperFn._phaserdepth by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_phaserdepth(args, callInfo))
}

internal val _phd by dslPatternMapper { args, callInfo -> { p -> p._phd(args, callInfo) } }
internal val StrudelPattern._phd by dslPatternExtension { p, args, /* callInfo */ _ -> applyPhaserDepth(p, args) }
internal val String._phd by dslStringExtension { p, args, callInfo -> p._phd(args, callInfo) }
internal val PatternMapperFn._phd by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_phd(args, callInfo))
}

internal val _phasdp by dslPatternMapper { args, callInfo -> { p -> p._phasdp(args, callInfo) } }
internal val StrudelPattern._phasdp by dslPatternExtension { p, args, /* callInfo */ _ -> applyPhaserDepth(p, args) }
internal val String._phasdp by dslStringExtension { p, args, callInfo -> p._phasdp(args, callInfo) }
internal val PatternMapperFn._phasdp by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_phasdp(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

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
 * ```KlangScript
 * note("c3*4").phaser(0.5).phaserdepth(0.8)   // deep phaser sweep
 * ```
 *
 * ```KlangScript
 * note("c3*4").phaserdepth("<0.2 0.5 0.8 1.0>")   // increasing phaser depth
 * ```
 *
 * ```KlangScript
 * seq("0.2 0.5 0.8 1.0").phaserdepth()   // reinterpret values as phaser depth
 * ```
 *
 * @alias phd, phasdp
 * @category effects
 * @tags phaserdepth, phd, phasdp, phaser, depth, modulation
 */
@StrudelDsl
fun StrudelPattern.phaserdepth(amount: PatternLike? = null): StrudelPattern =
    this._phaserdepth(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Parses this string as a pattern and sets the phaser depth.
 *
 * When [amount] is omitted, the string's numeric values are reinterpreted as phaser depth.
 *
 * @param amount The modulation intensity (0–1). Omit to reinterpret the pattern's values as phaser depth.
 *
 * ```KlangScript
 * "c3*4".phaserdepth(0.8).phaser(0.5).note()   // deep phaser on string pattern
 * ```
 */
@StrudelDsl
fun String.phaserdepth(amount: PatternLike? = null): StrudelPattern =
    this._phaserdepth(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets the phaser depth.
 *
 * Use the returned mapper as a transform argument or apply it via `.apply(...)`.
 * When [amount] is omitted, the pattern's own numeric values are reinterpreted as phaser depth.
 *
 * @param amount The modulation intensity (0–1). Omit to reinterpret the pattern's values as phaser depth.
 * @return A [PatternMapperFn] that sets the phaser depth.
 *
 * ```KlangScript
 * note("c3*4").apply(phaserdepth(0.8))   // deep phaser via mapper
 * ```
 *
 * ```KlangScript
 * note("c3*4").every(4, phaserdepth(1.0))   // max depth every 4th cycle
 * ```
 *
 * @alias phd, phasdp
 * @category effects
 * @tags phaserdepth, phd, phasdp, phaser, depth, modulation
 */
@StrudelDsl
fun phaserdepth(amount: PatternLike? = null): PatternMapperFn = _phaserdepth(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets the phaser depth after the previous mapper.
 *
 * @param amount The modulation intensity (0–1). Omit to reinterpret the pattern's values as phaser depth.
 * @return A new [PatternMapperFn] chaining this phaser depth after the previous mapper.
 *
 * ```KlangScript
 * note("c3 e3").apply(phaser(0.5).phaserdepth(0.8))   // rate then depth
 * ```
 *
 * ```KlangScript
 * note("c3*4").every(4, phaser(4.0).phaserdepth(1.0))   // full-depth fast phaser
 * ```
 */
@StrudelDsl
fun PatternMapperFn.phaserdepth(amount: PatternLike? = null): PatternMapperFn =
    _phaserdepth(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Alias for [phaserdepth]. Sets the phaser depth (modulation intensity) for this pattern.
 *
 * When [amount] is omitted, the pattern's own numeric values are reinterpreted as phaser depth.
 *
 * @param amount The modulation intensity (0–1). Omit to reinterpret the pattern's values as phaser depth.
 * @return A new pattern with the phaser depth applied.
 *
 * ```KlangScript
 * note("c3*4").phaser(0.5).phd(0.8)   // deep phaser sweep
 * ```
 *
 * ```KlangScript
 * note("c3*4").phd("<0.2 0.5 0.8 1.0>")   // increasing phaser depth
 * ```
 *
 * ```KlangScript
 * seq("0.2 0.5 0.8 1.0").phd()   // reinterpret values as phaser depth
 * ```
 *
 * @alias phaserdepth, phasdp
 * @category effects
 * @tags phd, phaserdepth, phasdp, phaser, depth, modulation
 */
@StrudelDsl
fun StrudelPattern.phd(amount: PatternLike? = null): StrudelPattern =
    this._phd(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Alias for [phaserdepth]. Parses this string as a pattern and sets the phaser depth.
 *
 * When [amount] is omitted, the string's numeric values are reinterpreted as phaser depth.
 *
 * @param amount The modulation intensity (0–1). Omit to reinterpret the pattern's values as phaser depth.
 *
 * ```KlangScript
 * "c3*4".phd(0.8).phaser(0.5).note()   // deep phaser on string pattern
 * ```
 */
@StrudelDsl
fun String.phd(amount: PatternLike? = null): StrudelPattern =
    this._phd(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets the phaser depth. Alias for [phaserdepth].
 *
 * @param amount The modulation intensity (0–1). Omit to reinterpret the pattern's values as phaser depth.
 * @return A [PatternMapperFn] that sets the phaser depth.
 *
 * ```KlangScript
 * note("c3*4").apply(phd(0.8))   // deep phaser via mapper
 * ```
 *
 * @alias phaserdepth, phasdp
 * @category effects
 * @tags phd, phaserdepth, phasdp, phaser, depth, modulation
 */
@StrudelDsl
fun phd(amount: PatternLike? = null): PatternMapperFn = _phd(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets the phaser depth (alias for phaserdepth) after the previous mapper.
 *
 * @param amount The modulation intensity (0–1). Omit to reinterpret the pattern's values as phaser depth.
 * @return A new [PatternMapperFn] chaining this phaser depth after the previous mapper.
 *
 * ```KlangScript
 * note("c3 e3").apply(phaser(0.5).phd(0.8))   // rate then depth
 * ```
 *
 * ```KlangScript
 * note("c3*4").every(4, phaser(4.0).phd(1.0))   // full-depth fast phaser
 * ```
 */
@StrudelDsl
fun PatternMapperFn.phd(amount: PatternLike? = null): PatternMapperFn =
    _phd(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Alias for [phaserdepth]. Sets the phaser depth (modulation intensity) for this pattern.
 *
 * When [amount] is omitted, the pattern's own numeric values are reinterpreted as phaser depth.
 *
 * @param amount The modulation intensity (0–1). Omit to reinterpret the pattern's values as phaser depth.
 * @return A new pattern with the phaser depth applied.
 *
 * ```KlangScript
 * note("c3*4").phaser(0.5).phasdp(0.8)   // deep phaser sweep
 * ```
 *
 * ```KlangScript
 * note("c3*4").phasdp("<0.2 0.5 0.8 1.0>")   // increasing phaser depth
 * ```
 *
 * ```KlangScript
 * seq("0.2 0.5 0.8 1.0").phasdp()   // reinterpret values as phaser depth
 * ```
 *
 * @alias phaserdepth, phd
 * @category effects
 * @tags phasdp, phaserdepth, phd, phaser, depth, modulation
 */
@StrudelDsl
fun StrudelPattern.phasdp(amount: PatternLike? = null): StrudelPattern =
    this._phasdp(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Alias for [phaserdepth]. Parses this string as a pattern and sets the phaser depth.
 *
 * When [amount] is omitted, the string's numeric values are reinterpreted as phaser depth.
 *
 * @param amount The modulation intensity (0–1). Omit to reinterpret the pattern's values as phaser depth.
 *
 * ```KlangScript
 * "c3*4".phasdp(0.8).phaser(0.5).note()   // deep phaser on string pattern
 * ```
 */
@StrudelDsl
fun String.phasdp(amount: PatternLike? = null): StrudelPattern =
    this._phasdp(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets the phaser depth. Alias for [phaserdepth].
 *
 * @param amount The modulation intensity (0–1). Omit to reinterpret the pattern's values as phaser depth.
 * @return A [PatternMapperFn] that sets the phaser depth.
 *
 * ```KlangScript
 * note("c3*4").apply(phasdp(0.8))   // deep phaser via mapper
 * ```
 *
 * @alias phaserdepth, phd
 * @category effects
 * @tags phasdp, phaserdepth, phd, phaser, depth, modulation
 */
@StrudelDsl
fun phasdp(amount: PatternLike? = null): PatternMapperFn = _phasdp(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets the phaser depth (alias for phaserdepth) after the previous mapper.
 *
 * @param amount The modulation intensity (0–1). Omit to reinterpret the pattern's values as phaser depth.
 * @return A new [PatternMapperFn] chaining this phaser depth after the previous mapper.
 *
 * ```KlangScript
 * note("c3 e3").apply(phaser(0.5).phasdp(0.8))   // rate then depth
 * ```
 *
 * ```KlangScript
 * note("c3*4").every(4, phaser(4.0).phasdp(1.0))   // full-depth fast phaser
 * ```
 */
@StrudelDsl
fun PatternMapperFn.phasdp(amount: PatternLike? = null): PatternMapperFn =
    _phasdp(listOfNotNull(amount).asStrudelDslArgs())

// -- phasercenter() / phc() -------------------------------------------------------------------------------------------

private val phaserCenterMutation = voiceModifier { copy(phaserCenter = it?.asDoubleOrNull()) }

fun applyPhaserCenter(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftOrReinterpretNumericalField(args, phaserCenterMutation)
}

internal val _phasercenter by dslPatternMapper { args, callInfo -> { p -> p._phasercenter(args, callInfo) } }
internal val StrudelPattern._phasercenter by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyPhaserCenter(p, args)
}
internal val String._phasercenter by dslStringExtension { p, args, callInfo -> p._phasercenter(args, callInfo) }
internal val PatternMapperFn._phasercenter by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_phasercenter(args, callInfo))
}

internal val _phc by dslPatternMapper { args, callInfo -> { p -> p._phc(args, callInfo) } }
internal val StrudelPattern._phc by dslPatternExtension { p, args, /* callInfo */ _ -> applyPhaserCenter(p, args) }
internal val String._phc by dslStringExtension { p, args, callInfo -> p._phc(args, callInfo) }
internal val PatternMapperFn._phc by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_phc(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

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
 * ```KlangScript
 * note("c3*4").phaser(0.5).phasercenter(1000)   // centered around 1 kHz
 * ```
 *
 * ```KlangScript
 * note("c3*4").phasercenter("<500 1000 2000 4000>")   // sweeping center frequency
 * ```
 *
 * ```KlangScript
 * seq("500 1000 2000 4000").phasercenter()   // reinterpret values as center frequency
 * ```
 *
 * @alias phc
 * @category effects
 * @tags phasercenter, phc, phaser, frequency, center
 */
@StrudelDsl
fun StrudelPattern.phasercenter(freq: PatternLike? = null): StrudelPattern =
    this._phasercenter(listOfNotNull(freq).asStrudelDslArgs())

/**
 * Parses this string as a pattern and sets the phaser center frequency.
 *
 * When [freq] is omitted, the string's numeric values are reinterpreted as the center frequency.
 *
 * @param freq The center frequency in Hz. Omit to reinterpret the pattern's values as center frequency.
 *
 * ```KlangScript
 * "c3*4".phasercenter(1000).phaser(0.5).note()   // centered phaser on string pattern
 * ```
 */
@StrudelDsl
fun String.phasercenter(freq: PatternLike? = null): StrudelPattern =
    this._phasercenter(listOfNotNull(freq).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets the phaser center frequency.
 *
 * Use the returned mapper as a transform argument or apply it via `.apply(...)`.
 * When [freq] is omitted, the pattern's own numeric values are reinterpreted as the center frequency.
 *
 * @param freq The center frequency in Hz. Omit to reinterpret the pattern's values as center frequency.
 * @return A [PatternMapperFn] that sets the phaser center frequency.
 *
 * ```KlangScript
 * note("c3*4").apply(phasercenter(1000))   // 1 kHz center via mapper
 * ```
 *
 * ```KlangScript
 * note("c3*4").every(4, phasercenter(4000))   // high-frequency center every 4th cycle
 * ```
 *
 * @alias phc
 * @category effects
 * @tags phasercenter, phc, phaser, frequency, center
 */
@StrudelDsl
fun phasercenter(freq: PatternLike? = null): PatternMapperFn = _phasercenter(listOfNotNull(freq).asStrudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets the phaser center frequency after the previous mapper.
 *
 * @param freq The center frequency in Hz. Omit to reinterpret the pattern's values as center frequency.
 * @return A new [PatternMapperFn] chaining this center frequency after the previous mapper.
 *
 * ```KlangScript
 * note("c3 e3").apply(phaser(0.5).phasercenter(1000))   // rate then center frequency
 * ```
 *
 * ```KlangScript
 * note("c3*4").every(4, phaser(2.0).phasercenter(2000))   // centered fast phaser
 * ```
 */
@StrudelDsl
fun PatternMapperFn.phasercenter(freq: PatternLike? = null): PatternMapperFn =
    _phasercenter(listOfNotNull(freq).asStrudelDslArgs())

/**
 * Alias for [phasercenter]. Sets the phaser center frequency in Hz for this pattern.
 *
 * When [freq] is omitted, the pattern's own numeric values are reinterpreted as the center frequency.
 *
 * @param freq The center frequency in Hz. Omit to reinterpret the pattern's values as center frequency.
 * @return A new pattern with the phaser center frequency applied.
 *
 * ```KlangScript
 * note("c3*4").phaser(0.5).phc(1000)   // centered around 1 kHz
 * ```
 *
 * ```KlangScript
 * note("c3*4").phc("<500 1000 2000 4000>")   // sweeping center frequency
 * ```
 *
 * ```KlangScript
 * seq("500 1000 2000 4000").phc()   // reinterpret values as center frequency
 * ```
 *
 * @alias phasercenter
 * @category effects
 * @tags phc, phasercenter, phaser, frequency, center
 */
@StrudelDsl
fun StrudelPattern.phc(freq: PatternLike? = null): StrudelPattern =
    this._phc(listOfNotNull(freq).asStrudelDslArgs())

/**
 * Alias for [phasercenter]. Parses this string as a pattern and sets the phaser center frequency.
 *
 * When [freq] is omitted, the string's numeric values are reinterpreted as the center frequency.
 *
 * @param freq The center frequency in Hz. Omit to reinterpret the pattern's values as center frequency.
 *
 * ```KlangScript
 * "c3*4".phc(1000).phaser(0.5).note()   // centered phaser on string pattern
 * ```
 */
@StrudelDsl
fun String.phc(freq: PatternLike? = null): StrudelPattern =
    this._phc(listOfNotNull(freq).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets the phaser center frequency. Alias for [phasercenter].
 *
 * @param freq The center frequency in Hz. Omit to reinterpret the pattern's values as center frequency.
 * @return A [PatternMapperFn] that sets the phaser center frequency.
 *
 * ```KlangScript
 * note("c3*4").apply(phc(1000))   // 1 kHz center via mapper
 * ```
 *
 * @alias phasercenter
 * @category effects
 * @tags phc, phasercenter, phaser, frequency, center
 */
@StrudelDsl
fun phc(freq: PatternLike? = null): PatternMapperFn = _phc(listOfNotNull(freq).asStrudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets the phaser center frequency (alias for phasercenter) after the
 * previous mapper.
 *
 * @param freq The center frequency in Hz. Omit to reinterpret the pattern's values as center frequency.
 * @return A new [PatternMapperFn] chaining this center frequency after the previous mapper.
 *
 * ```KlangScript
 * note("c3 e3").apply(phaser(0.5).phc(1000))   // rate then center frequency
 * ```
 *
 * ```KlangScript
 * note("c3*4").every(4, phaser(2.0).phc(2000))   // centered fast phaser
 * ```
 */
@StrudelDsl
fun PatternMapperFn.phc(freq: PatternLike? = null): PatternMapperFn =
    _phc(listOfNotNull(freq).asStrudelDslArgs())

// -- phasersweep() / phs() --------------------------------------------------------------------------------------------

private val phaserSweepMutation = voiceModifier { copy(phaserSweep = it?.asDoubleOrNull()) }

fun applyPhaserSweep(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftOrReinterpretNumericalField(args, phaserSweepMutation)
}

internal val _phasersweep by dslPatternMapper { args, callInfo -> { p -> p._phasersweep(args, callInfo) } }
internal val StrudelPattern._phasersweep by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyPhaserSweep(p, args)
}
internal val String._phasersweep by dslStringExtension { p, args, callInfo -> p._phasersweep(args, callInfo) }
internal val PatternMapperFn._phasersweep by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_phasersweep(args, callInfo))
}

internal val _phs by dslPatternMapper { args, callInfo -> { p -> p._phs(args, callInfo) } }
internal val StrudelPattern._phs by dslPatternExtension { p, args, /* callInfo */ _ -> applyPhaserSweep(p, args) }
internal val String._phs by dslStringExtension { p, args, callInfo -> p._phs(args, callInfo) }
internal val PatternMapperFn._phs by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_phs(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

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
 * ```KlangScript
 * note("c3*4").phaser(0.5).phasersweep(2000)   // ±2000 Hz sweep
 * ```
 *
 * ```KlangScript
 * note("c3*4").phasersweep("<500 1000 2000 4000>")   // increasing sweep width
 * ```
 *
 * ```KlangScript
 * seq("500 1000 2000 4000").phasersweep()   // reinterpret values as sweep range
 * ```
 *
 * @alias phs
 * @category effects
 * @tags phasersweep, phs, phaser, sweep, width
 */
@StrudelDsl
fun StrudelPattern.phasersweep(amount: PatternLike? = null): StrudelPattern =
    this._phasersweep(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Parses this string as a pattern and sets the phaser sweep range.
 *
 * When [amount] is omitted, the string's numeric values are reinterpreted as the sweep range.
 *
 * @param amount The sweep range in Hz. Omit to reinterpret the pattern's values as sweep range.
 *
 * ```KlangScript
 * "c3*4".phasersweep(2000).phaser(0.5).note()   // wide sweep on string pattern
 * ```
 */
@StrudelDsl
fun String.phasersweep(amount: PatternLike? = null): StrudelPattern =
    this._phasersweep(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets the phaser sweep range.
 *
 * Use the returned mapper as a transform argument or apply it via `.apply(...)`.
 * When [amount] is omitted, the pattern's own numeric values are reinterpreted as the sweep range.
 *
 * @param amount The sweep range in Hz. Omit to reinterpret the pattern's values as sweep range.
 * @return A [PatternMapperFn] that sets the phaser sweep range.
 *
 * ```KlangScript
 * note("c3*4").apply(phasersweep(2000))   // ±2000 Hz sweep via mapper
 * ```
 *
 * ```KlangScript
 * note("c3*4").every(4, phasersweep(4000))   // wide sweep every 4th cycle
 * ```
 *
 * @alias phs
 * @category effects
 * @tags phasersweep, phs, phaser, sweep, width
 */
@StrudelDsl
fun phasersweep(amount: PatternLike? = null): PatternMapperFn = _phasersweep(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets the phaser sweep range after the previous mapper.
 *
 * @param amount The sweep range in Hz. Omit to reinterpret the pattern's values as sweep range.
 * @return A new [PatternMapperFn] chaining this sweep range after the previous mapper.
 *
 * ```KlangScript
 * note("c3 e3").apply(phaser(0.5).phasersweep(2000))   // rate then sweep range
 * ```
 *
 * ```KlangScript
 * note("c3*4").every(4, phaser(2.0).phasersweep(4000))   // wide sweep fast phaser
 * ```
 */
@StrudelDsl
fun PatternMapperFn.phasersweep(amount: PatternLike? = null): PatternMapperFn =
    _phasersweep(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Alias for [phasersweep]. Sets the phaser sweep range in Hz for this pattern.
 *
 * When [amount] is omitted, the pattern's own numeric values are reinterpreted as the sweep range.
 *
 * @param amount The sweep range in Hz. Omit to reinterpret the pattern's values as sweep range.
 * @return A new pattern with the phaser sweep range applied.
 *
 * ```KlangScript
 * note("c3*4").phaser(0.5).phs(2000)   // ±2000 Hz sweep
 * ```
 *
 * ```KlangScript
 * note("c3*4").phs("<500 1000 2000 4000>")   // increasing sweep width
 * ```
 *
 * ```KlangScript
 * seq("500 1000 2000 4000").phs()   // reinterpret values as sweep range
 * ```
 *
 * @alias phasersweep
 * @category effects
 * @tags phs, phasersweep, phaser, sweep, width
 */
@StrudelDsl
fun StrudelPattern.phs(amount: PatternLike? = null): StrudelPattern =
    this._phs(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Alias for [phasersweep]. Parses this string as a pattern and sets the phaser sweep range.
 *
 * When [amount] is omitted, the string's numeric values are reinterpreted as the sweep range.
 *
 * @param amount The sweep range in Hz. Omit to reinterpret the pattern's values as sweep range.
 *
 * ```KlangScript
 * "c3*4".phs(2000).phaser(0.5).note()   // wide sweep on string pattern
 * ```
 */
@StrudelDsl
fun String.phs(amount: PatternLike? = null): StrudelPattern =
    this._phs(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets the phaser sweep range. Alias for [phasersweep].
 *
 * @param amount The sweep range in Hz. Omit to reinterpret the pattern's values as sweep range.
 * @return A [PatternMapperFn] that sets the phaser sweep range.
 *
 * ```KlangScript
 * note("c3*4").apply(phs(2000))   // ±2000 Hz sweep via mapper
 * ```
 *
 * @alias phasersweep
 * @category effects
 * @tags phs, phasersweep, phaser, sweep, width
 */
@StrudelDsl
fun phs(amount: PatternLike? = null): PatternMapperFn = _phs(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets the phaser sweep range (alias for phasersweep) after the previous
 * mapper.
 *
 * @param amount The sweep range in Hz. Omit to reinterpret the pattern's values as sweep range.
 * @return A new [PatternMapperFn] chaining this sweep range after the previous mapper.
 *
 * ```KlangScript
 * note("c3 e3").apply(phaser(0.5).phs(2000))   // rate then sweep range
 * ```
 *
 * ```KlangScript
 * note("c3*4").every(4, phaser(2.0).phs(4000))   // wide sweep fast phaser
 * ```
 */
@StrudelDsl
fun PatternMapperFn.phs(amount: PatternLike? = null): PatternMapperFn =
    _phs(listOfNotNull(amount).asStrudelDslArgs())

// -- tremolosync() / tremsync() ---------------------------------------------------------------------------------------

private val tremoloSyncMutation = voiceModifier { copy(tremoloSync = it?.asDoubleOrNull()) }

fun applyTremoloSync(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftOrReinterpretNumericalField(args, tremoloSyncMutation)
}

internal val _tremolosync by dslPatternMapper { args, callInfo -> { p -> p._tremolosync(args, callInfo) } }
internal val StrudelPattern._tremolosync by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyTremoloSync(p, args)
}
internal val String._tremolosync by dslStringExtension { p, args, callInfo -> p._tremolosync(args, callInfo) }
internal val PatternMapperFn._tremolosync by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_tremolosync(args, callInfo))
}

internal val _tremsync by dslPatternMapper { args, callInfo -> { p -> p._tremsync(args, callInfo) } }
internal val StrudelPattern._tremsync by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyTremoloSync(p, args)
}
internal val String._tremsync by dslStringExtension { p, args, callInfo -> p._tremsync(args, callInfo) }
internal val PatternMapperFn._tremsync by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_tremsync(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

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
 * ```KlangScript
 * note("c3 e3").s("sine").tremolosync(4)   // 4 Hz tremolo
 * ```
 *
 * ```KlangScript
 * note("c3*4").tremolosync("<1 2 4 8>")   // accelerating tremolo rate
 * ```
 *
 * ```KlangScript
 * seq("1 2 4 8").tremolosync()   // reinterpret values as tremolo rate
 * ```
 *
 * @alias tremsync
 * @category effects
 * @tags tremolosync, tremsync, tremolo, rate, modulation
 */
@StrudelDsl
fun StrudelPattern.tremolosync(rate: PatternLike? = null): StrudelPattern =
    this._tremolosync(listOfNotNull(rate).asStrudelDslArgs())

/**
 * Parses this string as a pattern and sets the tremolo LFO rate.
 *
 * When [rate] is omitted, the string's numeric values are reinterpreted as the tremolo rate.
 *
 * @param rate The tremolo LFO rate in Hz. Omit to reinterpret the pattern's values as tremolo rate.
 *
 * ```KlangScript
 * "c3 e3".tremolosync(4).note().s("sine")   // 4 Hz tremolo on string pattern
 * ```
 */
@StrudelDsl
fun String.tremolosync(rate: PatternLike? = null): StrudelPattern =
    this._tremolosync(listOfNotNull(rate).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets the tremolo LFO rate.
 *
 * Use the returned mapper as a transform argument or apply it via `.apply(...)`.
 * When [rate] is omitted, the pattern's own numeric values are reinterpreted as the tremolo rate.
 *
 * @param rate The tremolo LFO rate in Hz. Omit to reinterpret the pattern's values as tremolo rate.
 * @return A [PatternMapperFn] that sets the tremolo rate.
 *
 * ```KlangScript
 * note("c3 e3").apply(tremolosync(4))   // 4 Hz tremolo via mapper
 * ```
 *
 * ```KlangScript
 * note("c3*4").every(4, tremolosync(8))   // fast tremolo every 4th cycle
 * ```
 *
 * @alias tremsync
 * @category effects
 * @tags tremolosync, tremsync, tremolo, rate, modulation
 */
@StrudelDsl
fun tremolosync(rate: PatternLike? = null): PatternMapperFn = _tremolosync(listOfNotNull(rate).asStrudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets the tremolo LFO rate after the previous mapper.
 *
 * @param rate The tremolo LFO rate in Hz. Omit to reinterpret the pattern's values as tremolo rate.
 * @return A new [PatternMapperFn] chaining this tremolo rate after the previous mapper.
 *
 * ```KlangScript
 * note("c3 e3").apply(tremolodepth(0.8).tremolosync(4))   // depth then rate
 * ```
 *
 * ```KlangScript
 * note("c3*4").every(4, tremolodepth(1.0).tremolosync(8))   // full-depth fast tremolo
 * ```
 */
@StrudelDsl
fun PatternMapperFn.tremolosync(rate: PatternLike? = null): PatternMapperFn =
    _tremolosync(listOfNotNull(rate).asStrudelDslArgs())

/**
 * Alias for [tremolosync]. Sets the tremolo LFO rate in Hz for this pattern.
 *
 * When [rate] is omitted, the pattern's own numeric values are reinterpreted as the tremolo rate.
 *
 * @param rate The tremolo LFO rate in Hz. Omit to reinterpret the pattern's values as tremolo rate.
 * @return A new pattern with the tremolo rate applied.
 *
 * ```KlangScript
 * note("c3 e3").s("sine").tremsync(4)   // 4 Hz tremolo
 * ```
 *
 * ```KlangScript
 * note("c3*4").tremsync("<1 2 4 8>")   // accelerating tremolo rate
 * ```
 *
 * ```KlangScript
 * seq("1 2 4 8").tremsync()   // reinterpret values as tremolo rate
 * ```
 *
 * @alias tremolosync
 * @category effects
 * @tags tremsync, tremolosync, tremolo, rate, modulation
 */
@StrudelDsl
fun StrudelPattern.tremsync(rate: PatternLike? = null): StrudelPattern =
    this._tremsync(listOfNotNull(rate).asStrudelDslArgs())

/**
 * Alias for [tremolosync]. Parses this string as a pattern and sets the tremolo LFO rate.
 *
 * When [rate] is omitted, the string's numeric values are reinterpreted as the tremolo rate.
 *
 * @param rate The tremolo LFO rate in Hz. Omit to reinterpret the pattern's values as tremolo rate.
 *
 * ```KlangScript
 * "c3 e3".tremsync(4).note().s("sine")   // 4 Hz tremolo on string pattern
 * ```
 */
@StrudelDsl
fun String.tremsync(rate: PatternLike? = null): StrudelPattern =
    this._tremsync(listOfNotNull(rate).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets the tremolo LFO rate. Alias for [tremolosync].
 *
 * @param rate The tremolo LFO rate in Hz. Omit to reinterpret the pattern's values as tremolo rate.
 * @return A [PatternMapperFn] that sets the tremolo rate.
 *
 * ```KlangScript
 * note("c3 e3").apply(tremsync(4))   // 4 Hz tremolo via mapper
 * ```
 *
 * @alias tremolosync
 * @category effects
 * @tags tremsync, tremolosync, tremolo, rate, modulation
 */
@StrudelDsl
fun tremsync(rate: PatternLike? = null): PatternMapperFn = _tremsync(listOfNotNull(rate).asStrudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets the tremolo LFO rate (alias for tremolosync) after the previous
 * mapper.
 *
 * @param rate The tremolo LFO rate in Hz. Omit to reinterpret the pattern's values as tremolo rate.
 * @return A new [PatternMapperFn] chaining this tremolo rate after the previous mapper.
 *
 * ```KlangScript
 * note("c3 e3").apply(tremolodepth(0.8).tremsync(4))   // depth then rate
 * ```
 *
 * ```KlangScript
 * note("c3*4").every(4, tremolodepth(1.0).tremsync(8))   // full-depth fast tremolo
 * ```
 */
@StrudelDsl
fun PatternMapperFn.tremsync(rate: PatternLike? = null): PatternMapperFn =
    _tremsync(listOfNotNull(rate).asStrudelDslArgs())

// -- tremolodepth() / tremdepth() -------------------------------------------------------------------------------------

private val tremoloDepthMutation = voiceModifier { copy(tremoloDepth = it?.asDoubleOrNull()) }

fun applyTremoloDepth(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftOrReinterpretNumericalField(args, tremoloDepthMutation)
}

internal val _tremolodepth by dslPatternMapper { args, callInfo -> { p -> p._tremolodepth(args, callInfo) } }
internal val StrudelPattern._tremolodepth by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyTremoloDepth(p, args)
}
internal val String._tremolodepth by dslStringExtension { p, args, callInfo -> p._tremolodepth(args, callInfo) }
internal val PatternMapperFn._tremolodepth by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_tremolodepth(args, callInfo))
}

internal val _tremdepth by dslPatternMapper { args, callInfo -> { p -> p._tremdepth(args, callInfo) } }
internal val StrudelPattern._tremdepth by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyTremoloDepth(p, args)
}
internal val String._tremdepth by dslStringExtension { p, args, callInfo -> p._tremdepth(args, callInfo) }
internal val PatternMapperFn._tremdepth by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_tremdepth(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

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
 * ```KlangScript
 * note("c3 e3").s("sine").tremolosync(4).tremolodepth(0.8)   // strong tremolo
 * ```
 *
 * ```KlangScript
 * note("c3*4").tremolodepth("<0.2 0.5 0.8 1.0>")             // increasing depth
 * ```
 *
 * ```KlangScript
 * seq("0.2 0.5 0.8 1.0").tremolodepth()   // reinterpret values as tremolo depth
 * ```
 *
 * @alias tremdepth
 * @category effects
 * @tags tremolodepth, tremdepth, tremolo, depth, modulation
 */
@StrudelDsl
fun StrudelPattern.tremolodepth(amount: PatternLike? = null): StrudelPattern =
    this._tremolodepth(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Parses this string as a pattern and sets the tremolo depth.
 *
 * When [amount] is omitted, the string's numeric values are reinterpreted as tremolo depth.
 *
 * @param amount The modulation intensity (0–1). Omit to reinterpret the pattern's values as tremolo depth.
 *
 * ```KlangScript
 * "c3 e3".tremolodepth(0.8).tremolosync(4).note()   // strong tremolo on string pattern
 * ```
 */
@StrudelDsl
fun String.tremolodepth(amount: PatternLike? = null): StrudelPattern =
    this._tremolodepth(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets the tremolo depth.
 *
 * Use the returned mapper as a transform argument or apply it via `.apply(...)`.
 * When [amount] is omitted, the pattern's own numeric values are reinterpreted as tremolo depth.
 *
 * @param amount The modulation intensity (0–1). Omit to reinterpret the pattern's values as tremolo depth.
 * @return A [PatternMapperFn] that sets the tremolo depth.
 *
 * ```KlangScript
 * note("c3 e3").apply(tremolodepth(0.8))   // strong tremolo via mapper
 * ```
 *
 * ```KlangScript
 * note("c3*4").every(4, tremolodepth(1.0))   // max depth every 4th cycle
 * ```
 *
 * @alias tremdepth
 * @category effects
 * @tags tremolodepth, tremdepth, tremolo, depth, modulation
 */
@StrudelDsl
fun tremolodepth(amount: PatternLike? = null): PatternMapperFn = _tremolodepth(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets the tremolo depth after the previous mapper.
 *
 * @param amount The modulation intensity (0–1). Omit to reinterpret the pattern's values as tremolo depth.
 * @return A new [PatternMapperFn] chaining this tremolo depth after the previous mapper.
 *
 * ```KlangScript
 * note("c3 e3").apply(tremolosync(4).tremolodepth(0.8))   // rate then depth
 * ```
 *
 * ```KlangScript
 * note("c3*4").every(4, tremolosync(8).tremolodepth(1.0))   // full-depth fast tremolo
 * ```
 */
@StrudelDsl
fun PatternMapperFn.tremolodepth(amount: PatternLike? = null): PatternMapperFn =
    _tremolodepth(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Alias for [tremolodepth]. Sets the tremolo depth (modulation intensity, 0–1) for this pattern.
 *
 * When [amount] is omitted, the pattern's own numeric values are reinterpreted as tremolo depth.
 *
 * @param amount The modulation intensity (0–1). Omit to reinterpret the pattern's values as tremolo depth.
 * @return A new pattern with the tremolo depth applied.
 *
 * ```KlangScript
 * note("c3 e3").s("sine").tremolosync(4).tremdepth(0.8)   // strong tremolo
 * ```
 *
 * ```KlangScript
 * note("c3*4").tremdepth("<0.2 0.5 0.8 1.0>")             // increasing depth
 * ```
 *
 * ```KlangScript
 * seq("0.2 0.5 0.8 1.0").tremdepth()   // reinterpret values as tremolo depth
 * ```
 *
 * @alias tremolodepth
 * @category effects
 * @tags tremdepth, tremolodepth, tremolo, depth, modulation
 */
@StrudelDsl
fun StrudelPattern.tremdepth(amount: PatternLike? = null): StrudelPattern =
    this._tremdepth(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Alias for [tremolodepth]. Parses this string as a pattern and sets the tremolo depth.
 *
 * When [amount] is omitted, the string's numeric values are reinterpreted as tremolo depth.
 *
 * @param amount The modulation intensity (0–1). Omit to reinterpret the pattern's values as tremolo depth.
 *
 * ```KlangScript
 * "c3 e3".tremdepth(0.8).tremolosync(4).note()   // strong tremolo on string pattern
 * ```
 */
@StrudelDsl
fun String.tremdepth(amount: PatternLike? = null): StrudelPattern =
    this._tremdepth(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets the tremolo depth. Alias for [tremolodepth].
 *
 * @param amount The modulation intensity (0–1). Omit to reinterpret the pattern's values as tremolo depth.
 * @return A [PatternMapperFn] that sets the tremolo depth.
 *
 * ```KlangScript
 * note("c3 e3").apply(tremdepth(0.8))   // strong tremolo via mapper
 * ```
 *
 * @alias tremolodepth
 * @category effects
 * @tags tremdepth, tremolodepth, tremolo, depth, modulation
 */
@StrudelDsl
fun tremdepth(amount: PatternLike? = null): PatternMapperFn = _tremdepth(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets the tremolo depth (alias for tremolodepth) after the previous mapper.
 *
 * @param amount The modulation intensity (0–1). Omit to reinterpret the pattern's values as tremolo depth.
 * @return A new [PatternMapperFn] chaining this tremolo depth after the previous mapper.
 *
 * ```KlangScript
 * note("c3 e3").apply(tremolosync(4).tremdepth(0.8))   // rate then depth
 * ```
 *
 * ```KlangScript
 * note("c3*4").every(4, tremolosync(8).tremdepth(1.0))   // full-depth fast tremolo
 * ```
 */
@StrudelDsl
fun PatternMapperFn.tremdepth(amount: PatternLike? = null): PatternMapperFn =
    _tremdepth(listOfNotNull(amount).asStrudelDslArgs())

// -- tremoloskew() / tremskew() ---------------------------------------------------------------------------------------

private val tremoloSkewMutation = voiceModifier { copy(tremoloSkew = it?.asDoubleOrNull()) }

fun applyTremoloSkew(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftOrReinterpretNumericalField(args, tremoloSkewMutation)
}

internal val _tremoloskew by dslPatternMapper { args, callInfo -> { p -> p._tremoloskew(args, callInfo) } }
internal val StrudelPattern._tremoloskew by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyTremoloSkew(p, args)
}
internal val String._tremoloskew by dslStringExtension { p, args, callInfo -> p._tremoloskew(args, callInfo) }
internal val PatternMapperFn._tremoloskew by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_tremoloskew(args, callInfo))
}

internal val _tremskew by dslPatternMapper { args, callInfo -> { p -> p._tremskew(args, callInfo) } }
internal val StrudelPattern._tremskew by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyTremoloSkew(p, args)
}
internal val String._tremskew by dslStringExtension { p, args, callInfo -> p._tremskew(args, callInfo) }
internal val PatternMapperFn._tremskew by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_tremskew(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

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
 * ```KlangScript
 * note("c3*4").tremolosync(2).tremoloskew(0.8)   // skewed toward peak
 * ```
 *
 * ```KlangScript
 * note("c3*4").tremoloskew("<0.2 0.5 0.8>")      // varying asymmetry
 * ```
 *
 * ```KlangScript
 * seq("0.2 0.5 0.8").tremoloskew()   // reinterpret values as tremolo skew
 * ```
 *
 * @alias tremskew
 * @category effects
 * @tags tremoloskew, tremskew, tremolo, skew, asymmetry
 */
@StrudelDsl
fun StrudelPattern.tremoloskew(amount: PatternLike? = null): StrudelPattern =
    this._tremoloskew(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Parses this string as a pattern and sets the tremolo LFO skew.
 *
 * When [amount] is omitted, the string's numeric values are reinterpreted as the skew.
 *
 * @param amount The skew value. Omit to reinterpret the pattern's values as tremolo skew.
 *
 * ```KlangScript
 * "c3*4".tremoloskew(0.8).tremolosync(2).note()   // skewed tremolo on string pattern
 * ```
 */
@StrudelDsl
fun String.tremoloskew(amount: PatternLike? = null): StrudelPattern =
    this._tremoloskew(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets the tremolo LFO skew.
 *
 * Use the returned mapper as a transform argument or apply it via `.apply(...)`.
 * When [amount] is omitted, the pattern's own numeric values are reinterpreted as the skew.
 *
 * @param amount The skew value. Omit to reinterpret the pattern's values as tremolo skew.
 * @return A [PatternMapperFn] that sets the tremolo skew.
 *
 * ```KlangScript
 * note("c3*4").apply(tremoloskew(0.8))   // skewed tremolo via mapper
 * ```
 *
 * ```KlangScript
 * note("c3*4").every(4, tremoloskew(0.2))   // inverted skew every 4th cycle
 * ```
 *
 * @alias tremskew
 * @category effects
 * @tags tremoloskew, tremskew, tremolo, skew, asymmetry
 */
@StrudelDsl
fun tremoloskew(amount: PatternLike? = null): PatternMapperFn = _tremoloskew(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets the tremolo LFO skew after the previous mapper.
 *
 * @param amount The skew value. Omit to reinterpret the pattern's values as tremolo skew.
 * @return A new [PatternMapperFn] chaining this tremolo skew after the previous mapper.
 *
 * ```KlangScript
 * note("c3 e3").apply(tremolosync(2).tremoloskew(0.8))   // rate then skew
 * ```
 *
 * ```KlangScript
 * note("c3*4").every(4, tremolosync(4).tremoloskew(0.2))   // inverted skew fast tremolo
 * ```
 */
@StrudelDsl
fun PatternMapperFn.tremoloskew(amount: PatternLike? = null): PatternMapperFn =
    _tremoloskew(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Alias for [tremoloskew]. Sets the tremolo LFO skew (asymmetry) value for this pattern.
 *
 * When [amount] is omitted, the pattern's own numeric values are reinterpreted as the skew.
 *
 * @param amount The skew value. Omit to reinterpret the pattern's values as tremolo skew.
 * @return A new pattern with the tremolo skew applied.
 *
 * ```KlangScript
 * note("c3*4").tremolosync(2).tremskew(0.8)   // skewed toward peak
 * ```
 *
 * ```KlangScript
 * note("c3*4").tremskew("<0.2 0.5 0.8>")      // varying asymmetry
 * ```
 *
 * ```KlangScript
 * seq("0.2 0.5 0.8").tremskew()   // reinterpret values as tremolo skew
 * ```
 *
 * @alias tremoloskew
 * @category effects
 * @tags tremskew, tremoloskew, tremolo, skew, asymmetry
 */
@StrudelDsl
fun StrudelPattern.tremskew(amount: PatternLike? = null): StrudelPattern =
    this._tremskew(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Alias for [tremoloskew]. Parses this string as a pattern and sets the tremolo LFO skew.
 *
 * When [amount] is omitted, the string's numeric values are reinterpreted as the skew.
 *
 * @param amount The skew value. Omit to reinterpret the pattern's values as tremolo skew.
 *
 * ```KlangScript
 * "c3*4".tremskew(0.8).tremolosync(2).note()   // skewed tremolo on string pattern
 * ```
 */
@StrudelDsl
fun String.tremskew(amount: PatternLike? = null): StrudelPattern =
    this._tremskew(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets the tremolo LFO skew. Alias for [tremoloskew].
 *
 * @param amount The skew value. Omit to reinterpret the pattern's values as tremolo skew.
 * @return A [PatternMapperFn] that sets the tremolo skew.
 *
 * ```KlangScript
 * note("c3*4").apply(tremskew(0.8))   // skewed tremolo via mapper
 * ```
 *
 * @alias tremoloskew
 * @category effects
 * @tags tremskew, tremoloskew, tremolo, skew, asymmetry
 */
@StrudelDsl
fun tremskew(amount: PatternLike? = null): PatternMapperFn = _tremskew(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets the tremolo LFO skew (alias for tremoloskew) after the previous
 * mapper.
 *
 * @param amount The skew value. Omit to reinterpret the pattern's values as tremolo skew.
 * @return A new [PatternMapperFn] chaining this tremolo skew after the previous mapper.
 *
 * ```KlangScript
 * note("c3 e3").apply(tremolosync(2).tremskew(0.8))   // rate then skew
 * ```
 *
 * ```KlangScript
 * note("c3*4").every(4, tremolosync(4).tremskew(0.2))   // inverted skew fast tremolo
 * ```
 */
@StrudelDsl
fun PatternMapperFn.tremskew(amount: PatternLike? = null): PatternMapperFn =
    _tremskew(listOfNotNull(amount).asStrudelDslArgs())

// -- tremolophase() / tremphase() -------------------------------------------------------------------------------------

private val tremoloPhaseMutation = voiceModifier { copy(tremoloPhase = it?.asDoubleOrNull()) }

fun applyTremoloPhase(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftOrReinterpretNumericalField(args, tremoloPhaseMutation)
}

internal val _tremolophase by dslPatternMapper { args, callInfo -> { p -> p._tremolophase(args, callInfo) } }
internal val StrudelPattern._tremolophase by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyTremoloPhase(p, args)
}
internal val String._tremolophase by dslStringExtension { p, args, callInfo -> p._tremolophase(args, callInfo) }
internal val PatternMapperFn._tremolophase by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_tremolophase(args, callInfo))
}

internal val _tremphase by dslPatternMapper { args, callInfo -> { p -> p._tremphase(args, callInfo) } }
internal val StrudelPattern._tremphase by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyTremoloPhase(p, args)
}
internal val String._tremphase by dslStringExtension { p, args, callInfo -> p._tremphase(args, callInfo) }
internal val PatternMapperFn._tremphase by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_tremphase(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

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
 * ```KlangScript
 * note("c3 e3").tremolosync(2).tremolophase(1.57)   // start at 90°
 * ```
 *
 * ```KlangScript
 * stack(
 *   note("c3").tremolosync(2).tremolophase(0),
 *   note("e3").tremolosync(2).tremolophase(3.14),   // 180° offset
 * )
 * ```
 *
 * ```KlangScript
 * seq("0 1.57 3.14 4.71").tremolophase()   // reinterpret values as tremolo phase
 * ```
 *
 * @alias tremphase
 * @category effects
 * @tags tremolophase, tremphase, tremolo, phase, offset
 */
@StrudelDsl
fun StrudelPattern.tremolophase(phase: PatternLike? = null): StrudelPattern =
    this._tremolophase(listOfNotNull(phase).asStrudelDslArgs())

/**
 * Parses this string as a pattern and sets the tremolo LFO starting phase.
 *
 * When [phase] is omitted, the string's numeric values are reinterpreted as the phase.
 *
 * @param phase The starting phase in radians. Omit to reinterpret the pattern's values as tremolo phase.
 *
 * ```KlangScript
 * "c3 e3".tremolophase(1.57).tremolosync(2).note()   // 90° tremolo on string pattern
 * ```
 */
@StrudelDsl
fun String.tremolophase(phase: PatternLike? = null): StrudelPattern =
    this._tremolophase(listOfNotNull(phase).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets the tremolo LFO starting phase.
 *
 * Use the returned mapper as a transform argument or apply it via `.apply(...)`.
 * When [phase] is omitted, the pattern's own numeric values are reinterpreted as the phase.
 *
 * @param phase The starting phase in radians. Omit to reinterpret the pattern's values as tremolo phase.
 * @return A [PatternMapperFn] that sets the tremolo phase.
 *
 * ```KlangScript
 * note("c3 e3").apply(tremolophase(1.57))   // 90° start via mapper
 * ```
 *
 * ```KlangScript
 * note("c3*4").every(4, tremolophase(3.14))   // 180° start every 4th cycle
 * ```
 *
 * @alias tremphase
 * @category effects
 * @tags tremolophase, tremphase, tremolo, phase, offset
 */
@StrudelDsl
fun tremolophase(phase: PatternLike? = null): PatternMapperFn = _tremolophase(listOfNotNull(phase).asStrudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets the tremolo LFO starting phase after the previous mapper.
 *
 * @param phase The starting phase in radians. Omit to reinterpret the pattern's values as tremolo phase.
 * @return A new [PatternMapperFn] chaining this tremolo phase after the previous mapper.
 *
 * ```KlangScript
 * note("c3 e3").apply(tremolosync(2).tremolophase(1.57))   // rate then phase
 * ```
 *
 * ```KlangScript
 * note("c3*4").every(4, tremolosync(4).tremolophase(3.14))   // fast tremolo at 180°
 * ```
 */
@StrudelDsl
fun PatternMapperFn.tremolophase(phase: PatternLike? = null): PatternMapperFn =
    _tremolophase(listOfNotNull(phase).asStrudelDslArgs())

/**
 * Alias for [tremolophase]. Sets the tremolo LFO starting phase in radians for this pattern.
 *
 * When [phase] is omitted, the pattern's own numeric values are reinterpreted as the phase.
 *
 * @param phase The starting phase in radians. Omit to reinterpret the pattern's values as tremolo phase.
 * @return A new pattern with the tremolo phase applied.
 *
 * ```KlangScript
 * note("c3 e3").tremolosync(2).tremphase(1.57)   // start at 90°
 * ```
 *
 * ```KlangScript
 * note("c3*4").tremphase("<0 1.57 3.14 4.71>")   // quarter-turn offsets
 * ```
 *
 * ```KlangScript
 * seq("0 1.57 3.14 4.71").tremphase()   // reinterpret values as tremolo phase
 * ```
 *
 * @alias tremolophase
 * @category effects
 * @tags tremphase, tremolophase, tremolo, phase, offset
 */
@StrudelDsl
fun StrudelPattern.tremphase(phase: PatternLike? = null): StrudelPattern =
    this._tremphase(listOfNotNull(phase).asStrudelDslArgs())

/**
 * Alias for [tremolophase]. Parses this string as a pattern and sets the tremolo LFO starting phase.
 *
 * When [phase] is omitted, the string's numeric values are reinterpreted as the phase.
 *
 * @param phase The starting phase in radians. Omit to reinterpret the pattern's values as tremolo phase.
 *
 * ```KlangScript
 * "c3 e3".tremphase(1.57).tremolosync(2).note()   // 90° tremolo on string pattern
 * ```
 */
@StrudelDsl
fun String.tremphase(phase: PatternLike? = null): StrudelPattern =
    this._tremphase(listOfNotNull(phase).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets the tremolo LFO starting phase. Alias for [tremolophase].
 *
 * @param phase The starting phase in radians. Omit to reinterpret the pattern's values as tremolo phase.
 * @return A [PatternMapperFn] that sets the tremolo phase.
 *
 * ```KlangScript
 * note("c3 e3").apply(tremphase(1.57))   // 90° start via mapper
 * ```
 *
 * @alias tremolophase
 * @category effects
 * @tags tremphase, tremolophase, tremolo, phase, offset
 */
@StrudelDsl
fun tremphase(phase: PatternLike? = null): PatternMapperFn = _tremphase(listOfNotNull(phase).asStrudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets the tremolo LFO starting phase (alias for tremolophase) after the
 * previous mapper.
 *
 * @param phase The starting phase in radians. Omit to reinterpret the pattern's values as tremolo phase.
 * @return A new [PatternMapperFn] chaining this tremolo phase after the previous mapper.
 *
 * ```KlangScript
 * note("c3 e3").apply(tremolosync(2).tremphase(1.57))   // rate then phase
 * ```
 *
 * ```KlangScript
 * note("c3*4").every(4, tremolosync(4).tremphase(3.14))   // fast tremolo at 180°
 * ```
 */
@StrudelDsl
fun PatternMapperFn.tremphase(phase: PatternLike? = null): PatternMapperFn =
    _tremphase(listOfNotNull(phase).asStrudelDslArgs())

// -- tremoloshape() / tremshape() -------------------------------------------------------------------------------------

private val tremoloShapeMutation = voiceModifier { shape -> copy(tremoloShape = shape?.toString()?.lowercase()) }

fun applyTremoloShape(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._applyControlFromParams(args, tremoloShapeMutation) { src, ctrl ->
        src.copy(tremoloShape = ctrl.tremoloShape)
    }
}

internal val _tremoloshape by dslPatternMapper { args, callInfo -> { p -> p._tremoloshape(args, callInfo) } }
internal val StrudelPattern._tremoloshape by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyTremoloShape(p, args)
}
internal val String._tremoloshape by dslStringExtension { p, args, callInfo -> p._tremoloshape(args, callInfo) }
internal val PatternMapperFn._tremoloshape by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_tremoloshape(args, callInfo))
}

internal val _tremshape by dslPatternMapper { args, callInfo -> { p -> p._tremshape(args, callInfo) } }
internal val StrudelPattern._tremshape by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyTremoloShape(p, args)
}
internal val String._tremshape by dslStringExtension { p, args, callInfo -> p._tremshape(args, callInfo) }
internal val PatternMapperFn._tremshape by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_tremshape(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the tremolo LFO waveform shape for this pattern.
 *
 * Accepted values: `"sine"`, `"triangle"`, `"square"`, `"sawtooth"`, `"rampup"`, `"rampdown"`.
 * Different shapes produce different tremolo characters — sine is smooth, square is choppy.
 *
 * @param shape The LFO waveform shape name.
 * @return A new pattern with the tremolo shape applied.
 *
 * ```KlangScript
 * note("c3 e3").tremolosync(4).tremoloshape("square")   // choppy on/off tremolo
 * ```
 *
 * ```KlangScript
 * note("c3*4").tremoloshape("<sine triangle square>")   // cycle through shapes
 * ```
 *
 * @alias tremshape
 * @category effects
 * @tags tremoloshape, tremshape, tremolo, shape, waveform
 */
@StrudelDsl
fun StrudelPattern.tremoloshape(shape: PatternLike): StrudelPattern =
    this._tremoloshape(listOf(shape).asStrudelDslArgs())

/**
 * Parses this string as a pattern and sets the tremolo LFO waveform shape.
 *
 * @param shape The LFO waveform shape name.
 *
 * ```KlangScript
 * "c3 e3".tremoloshape("square").tremolosync(4).note()   // choppy tremolo on string pattern
 * ```
 */
@StrudelDsl
fun String.tremoloshape(shape: PatternLike): StrudelPattern = this._tremoloshape(listOf(shape).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets the tremolo LFO waveform shape.
 *
 * Use the returned mapper as a transform argument or apply it via `.apply(...)`.
 *
 * @param shape The LFO waveform shape name.
 * @return A [PatternMapperFn] that sets the tremolo shape.
 *
 * ```KlangScript
 * note("c3 e3").apply(tremoloshape("square"))   // choppy tremolo via mapper
 * ```
 *
 * ```KlangScript
 * note("c3*4").every(4, tremoloshape("triangle"))   // triangle tremolo every 4th cycle
 * ```
 *
 * @alias tremshape
 * @category effects
 * @tags tremoloshape, tremshape, tremolo, shape, waveform
 */
@StrudelDsl
fun tremoloshape(shape: PatternLike): PatternMapperFn = _tremoloshape(listOf(shape).asStrudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets the tremolo LFO waveform shape after the previous mapper.
 *
 * @param shape The LFO waveform shape name.
 * @return A new [PatternMapperFn] chaining this tremolo shape after the previous mapper.
 *
 * ```KlangScript
 * note("c3 e3").apply(tremolosync(4).tremoloshape("square"))   // rate then shape
 * ```
 *
 * ```KlangScript
 * note("c3*4").every(4, tremolosync(8).tremoloshape("triangle"))   // shaped fast tremolo
 * ```
 */
@StrudelDsl
fun PatternMapperFn.tremoloshape(shape: PatternLike): PatternMapperFn = _tremoloshape(listOf(shape).asStrudelDslArgs())

/**
 * Alias for [tremoloshape]. Sets the tremolo LFO waveform shape for this pattern.
 *
 * @param shape The LFO waveform shape name.
 * @return A new pattern with the tremolo shape applied.
 *
 * ```KlangScript
 * note("c3 e3").tremolosync(4).tremshape("square")   // choppy on/off tremolo
 * ```
 *
 * ```KlangScript
 * note("c3*4").tremshape("<sine triangle square>")   // cycle through shapes
 * ```
 *
 * @alias tremoloshape
 * @category effects
 * @tags tremshape, tremoloshape, tremolo, shape, waveform
 */
@StrudelDsl
fun StrudelPattern.tremshape(shape: PatternLike): StrudelPattern = this._tremshape(listOf(shape).asStrudelDslArgs())

/**
 * Alias for [tremoloshape]. Parses this string as a pattern and sets the tremolo LFO waveform shape.
 *
 * @param shape The LFO waveform shape name.
 *
 * ```KlangScript
 * "c3 e3".tremshape("square").tremolosync(4).note()   // choppy tremolo on string pattern
 * ```
 */
@StrudelDsl
fun String.tremshape(shape: PatternLike): StrudelPattern = this._tremshape(listOf(shape).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets the tremolo LFO waveform shape. Alias for [tremoloshape].
 *
 * @param shape The LFO waveform shape name.
 * @return A [PatternMapperFn] that sets the tremolo shape.
 *
 * ```KlangScript
 * note("c3 e3").apply(tremshape("square"))   // choppy tremolo via mapper
 * ```
 *
 * @alias tremoloshape
 * @category effects
 * @tags tremshape, tremoloshape, tremolo, shape, waveform
 */
@StrudelDsl
fun tremshape(shape: PatternLike): PatternMapperFn = _tremshape(listOf(shape).asStrudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets the tremolo LFO waveform shape (alias for tremoloshape) after the
 * previous mapper.
 *
 * @param shape The LFO waveform shape name.
 * @return A new [PatternMapperFn] chaining this tremolo shape after the previous mapper.
 *
 * ```KlangScript
 * note("c3 e3").apply(tremolosync(4).tremshape("square"))   // rate then shape
 * ```
 *
 * ```KlangScript
 * note("c3*4").every(4, tremolosync(8).tremshape("triangle"))   // shaped fast tremolo
 * ```
 */
@StrudelDsl
fun PatternMapperFn.tremshape(shape: PatternLike): PatternMapperFn = _tremshape(listOf(shape).asStrudelDslArgs())
