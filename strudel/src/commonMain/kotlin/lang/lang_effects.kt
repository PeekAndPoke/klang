@file:Suppress("DuplicatedCode", "ObjectPropertyName")

package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel._applyControlFromParams
import io.peekandpoke.klang.strudel._liftNumericField
import io.peekandpoke.klang.strudel.lang.StrudelDslArg.Companion.asStrudelDslArgs

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in StrudelRegistry.
 */
var strudelLangEffectsInit = false

// -- distort() / dist() -----------------------------------------------------------------------------------------------

private val distortMutation = voiceModifier { copy(distort = it?.asDoubleOrNull()) }

fun applyDistort(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, distortMutation)
}

internal val _distort by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(distortMutation) }
internal val StrudelPattern._distort by dslPatternExtension { p, args, /* callInfo */ _ -> applyDistort(p, args) }
internal val String._distort by dslStringExtension { p, args, callInfo -> p._distort(args, callInfo) }

internal val _dist by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(distortMutation) }
internal val StrudelPattern._dist by dslPatternExtension { p, args, /* callInfo */ _ -> applyDistort(p, args) }
internal val String._dist by dslStringExtension { p, args, callInfo -> p._dist(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Applies waveshaper distortion to the pattern.
 *
 * Higher values produce more harmonic saturation and clipping. Works well on synth
 * bass lines and leads; combine with `lpf` to tame harsh high frequencies.
 *
 * ```KlangScript
 * note("c2 eb2 g2").s("sawtooth").distort(0.5)   // moderate distortion
 * ```
 *
 * ```KlangScript
 * note("c3*4").distort("<0 0.3 0.6 1.0>")        // escalating distortion
 * ```
 *
 * @alias dist
 * @category effects
 * @tags distort, dist, distortion, waveshaper, overdrive
 */
@StrudelDsl
fun distort(amount: PatternLike): StrudelPattern = _distort(listOf(amount).asStrudelDslArgs())

/** Applies waveshaper distortion to this pattern. */
@StrudelDsl
fun StrudelPattern.distort(amount: PatternLike): StrudelPattern = this._distort(listOf(amount).asStrudelDslArgs())

/** Parses this string as a pattern and applies waveshaper distortion. */
@StrudelDsl
fun String.distort(amount: PatternLike): StrudelPattern = this._distort(listOf(amount).asStrudelDslArgs())

/**
 * Alias for [distort]. Applies waveshaper distortion to the pattern.
 *
 * ```KlangScript
 * note("c2 eb2 g2").s("sawtooth").dist(0.5)   // moderate distortion
 * ```
 *
 * ```KlangScript
 * note("c3*4").dist("<0 0.3 0.6 1.0>")        // escalating distortion
 * ```
 *
 * @alias distort
 * @category effects
 * @tags dist, distort, distortion, waveshaper, overdrive
 */
@StrudelDsl
fun dist(amount: PatternLike): StrudelPattern = _dist(listOf(amount).asStrudelDslArgs())

/** Alias for [distort]. Applies waveshaper distortion to this pattern. */
@StrudelDsl
fun StrudelPattern.dist(amount: PatternLike): StrudelPattern = this._dist(listOf(amount).asStrudelDslArgs())

/** Alias for [distort]. Parses this string as a pattern and applies waveshaper distortion. */
@StrudelDsl
fun String.dist(amount: PatternLike): StrudelPattern = this._dist(listOf(amount).asStrudelDslArgs())

// -- crush() ----------------------------------------------------------------------------------------------------------

private val crushMutation = voiceModifier { copy(crush = it?.asDoubleOrNull()) }

fun applyCrush(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, crushMutation)
}

internal val _crush by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(crushMutation) }
internal val StrudelPattern._crush by dslPatternExtension { p, args, /* callInfo */ _ -> applyCrush(p, args) }
internal val String._crush by dslStringExtension { p, args, callInfo -> p._crush(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Applies bit-crushing (bit-depth reduction) to the pattern.
 *
 * Lower values reduce the bit depth, producing a lo-fi, crunchy digital sound.
 * A value of 1 is maximum crush; higher values approach the original sound.
 *
 * ```KlangScript
 * s("bd sd hh").crush(4)              // 4-bit lo-fi crunch
 * ```
 *
 * ```KlangScript
 * note("c3*4").crush("<16 8 4 2>")    // decreasing bit depth each beat
 * ```
 *
 * @category effects
 * @tags crush, bitcrush, lofi, bitdepth, distortion
 */
@StrudelDsl
fun crush(amount: PatternLike): StrudelPattern = _crush(listOf(amount).asStrudelDslArgs())

/** Applies bit-crushing to this pattern. */
@StrudelDsl
fun StrudelPattern.crush(amount: PatternLike): StrudelPattern = this._crush(listOf(amount).asStrudelDslArgs())

/** Parses this string as a pattern and applies bit-crushing. */
@StrudelDsl
fun String.crush(amount: PatternLike): StrudelPattern = this._crush(listOf(amount).asStrudelDslArgs())

// -- coarse() ---------------------------------------------------------------------------------------------------------

private val coarseMutation = voiceModifier { copy(coarse = it?.asDoubleOrNull()) }

fun applyCoarse(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, coarseMutation)
}

internal val _coarse by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(coarseMutation) }
internal val StrudelPattern._coarse by dslPatternExtension { p, args, /* callInfo */ _ -> applyCoarse(p, args) }
internal val String._coarse by dslStringExtension { p, args, callInfo -> p._coarse(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Applies sample-rate reduction (downsampling) to the pattern.
 *
 * Reduces the effective sample rate of the audio, producing a gritty lo-fi effect.
 * Higher values cause more aliasing; combine with `crush` for classic lo-fi aesthetics.
 *
 * ```KlangScript
 * s("bd sd").coarse(4)               // moderate sample-rate reduction
 * ```
 *
 * ```KlangScript
 * note("c3*8").coarse("<1 2 4 8>")   // escalating downsampling
 * ```
 *
 * @category effects
 * @tags coarse, samplerate, lofi, aliasing, downsample
 */
@StrudelDsl
fun coarse(amount: PatternLike): StrudelPattern = _coarse(listOf(amount).asStrudelDslArgs())

/** Applies sample-rate reduction to this pattern. */
@StrudelDsl
fun StrudelPattern.coarse(amount: PatternLike): StrudelPattern = this._coarse(listOf(amount).asStrudelDslArgs())

/** Parses this string as a pattern and applies sample-rate reduction. */
@StrudelDsl
fun String.coarse(amount: PatternLike): StrudelPattern = this._coarse(listOf(amount).asStrudelDslArgs())

// -- room() -----------------------------------------------------------------------------------------------------------

private val roomMutation = voiceModifier { copy(room = it?.asDoubleOrNull()) }

fun applyRoom(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, roomMutation)
}

internal val _room by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(roomMutation) }
internal val StrudelPattern._room by dslPatternExtension { p, args, /* callInfo */ _ -> applyRoom(p, args) }
internal val String._room by dslStringExtension { p, args, callInfo -> p._room(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the reverb wet/dry mix for the pattern (0 = dry, 1 = full wet).
 *
 * Use with `roomsize` to control the reverb tail length, and `orbit` to send
 * multiple patterns to separate reverb buses.
 *
 * ```KlangScript
 * note("c3 e3 g3").s("sine").room(0.5)              // 50% reverb
 * ```
 *
 * ```KlangScript
 * note("c3*4").room("<0 0.3 0.6 0.9>").roomsize(4)  // increasing wet mix
 * ```
 *
 * @category effects
 * @tags room, reverb, wet, mix, space
 */
@StrudelDsl
fun room(amount: PatternLike): StrudelPattern = _room(listOf(amount).asStrudelDslArgs())

/** Sets the reverb wet/dry mix for this pattern. */
@StrudelDsl
fun StrudelPattern.room(amount: PatternLike): StrudelPattern = this._room(listOf(amount).asStrudelDslArgs())

/** Parses this string as a pattern and sets the reverb wet/dry mix. */
@StrudelDsl
fun String.room(amount: PatternLike): StrudelPattern = this._room(listOf(amount).asStrudelDslArgs())

// -- roomsize() / rsize() / sz() / size() -----------------------------------------------------------------------------

private val roomSizeMutation = voiceModifier { copy(roomSize = it?.asDoubleOrNull()) }

fun applyRoomSize(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, roomSizeMutation)
}

internal val _roomsize by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(roomSizeMutation) }
internal val StrudelPattern._roomsize by dslPatternExtension { p, args, /* callInfo */ _ -> applyRoomSize(p, args) }
internal val String._roomsize by dslStringExtension { p, args, callInfo -> p._roomsize(args, callInfo) }

internal val _rsize by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(roomSizeMutation) }
internal val StrudelPattern._rsize by dslPatternExtension { p, args, /* callInfo */ _ -> applyRoomSize(p, args) }
internal val String._rsize by dslStringExtension { p, args, callInfo -> p._rsize(args, callInfo) }

internal val _sz by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(roomSizeMutation) }
internal val StrudelPattern._sz by dslPatternExtension { p, args, /* callInfo */ _ -> applyRoomSize(p, args) }
internal val String._sz by dslStringExtension { p, args, callInfo -> p._sz(args, callInfo) }

internal val _size by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(roomSizeMutation) }
internal val StrudelPattern._size by dslPatternExtension { p, args, /* callInfo */ _ -> applyRoomSize(p, args) }
internal val String._size by dslStringExtension { p, args, callInfo -> p._size(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the reverb room size (tail length) for the pattern.
 *
 * Larger values produce a longer, more spacious reverb tail. Use with `room` to control
 * the wet/dry mix.
 *
 * ```KlangScript
 * note("c3 e3").s("sine").room(0.5).roomsize(4)   // long reverb tail
 * ```
 *
 * ```KlangScript
 * note("c3*4").roomsize("<1 2 4 8>")              // growing room size
 * ```
 *
 * @alias rsize, sz, size
 * @category effects
 * @tags roomsize, rsize, sz, size, reverb, room, tail
 */
@StrudelDsl
fun roomsize(amount: PatternLike): StrudelPattern = _roomsize(listOf(amount).asStrudelDslArgs())

/** Sets the reverb room size for this pattern. */
@StrudelDsl
fun StrudelPattern.roomsize(amount: PatternLike): StrudelPattern = this._roomsize(listOf(amount).asStrudelDslArgs())

/** Parses this string as a pattern and sets the reverb room size. */
@StrudelDsl
fun String.roomsize(amount: PatternLike): StrudelPattern = this._roomsize(listOf(amount).asStrudelDslArgs())

/**
 * Alias for [roomsize]. Sets the reverb room size (tail length) for the pattern.
 *
 * ```KlangScript
 * note("c3 e3").s("sine").room(0.5).rsize(4)   // long reverb tail
 * ```
 *
 * ```KlangScript
 * note("c3*4").rsize("<1 2 4 8>")              // growing room size
 * ```
 *
 * @alias roomsize, sz, size
 * @category effects
 * @tags rsize, roomsize, sz, size, reverb, room, tail
 */
@StrudelDsl
fun rsize(amount: PatternLike): StrudelPattern = _rsize(listOf(amount).asStrudelDslArgs())

/** Alias for [roomsize]. Sets the reverb room size for this pattern. */
@StrudelDsl
fun StrudelPattern.rsize(amount: PatternLike): StrudelPattern = this._rsize(listOf(amount).asStrudelDslArgs())

/** Alias for [roomsize]. Parses this string as a pattern and sets the reverb room size. */
@StrudelDsl
fun String.rsize(amount: PatternLike): StrudelPattern = this._rsize(listOf(amount).asStrudelDslArgs())

/**
 * Alias for [roomsize]. Sets the reverb room size (tail length) for the pattern.
 *
 * ```KlangScript
 * note("c3 e3").s("sine").room(0.5).sz(4)   // long reverb tail
 * ```
 *
 * ```KlangScript
 * note("c3*4").sz("<1 2 4 8>")              // growing room size
 * ```
 *
 * @alias roomsize, rsize, size
 * @category effects
 * @tags sz, roomsize, rsize, size, reverb, room, tail
 */
@StrudelDsl
fun sz(amount: PatternLike): StrudelPattern = _sz(listOf(amount).asStrudelDslArgs())

/** Alias for [roomsize]. Sets the reverb room size for this pattern. */
@StrudelDsl
fun StrudelPattern.sz(amount: PatternLike): StrudelPattern = this._sz(listOf(amount).asStrudelDslArgs())

/** Alias for [roomsize]. Parses this string as a pattern and sets the reverb room size. */
@StrudelDsl
fun String.sz(amount: PatternLike): StrudelPattern = this._sz(listOf(amount).asStrudelDslArgs())

/**
 * Alias for [roomsize]. Sets the reverb room size (tail length) for the pattern.
 *
 * ```KlangScript
 * note("c3 e3").s("sine").room(0.5).size(4)   // long reverb tail
 * ```
 *
 * ```KlangScript
 * note("c3*4").size("<1 2 4 8>")              // growing room size
 * ```
 *
 * @alias roomsize, rsize, sz
 * @category effects
 * @tags size, roomsize, rsize, sz, reverb, room, tail
 */
@StrudelDsl
fun size(amount: PatternLike): StrudelPattern = _size(listOf(amount).asStrudelDslArgs())

/** Alias for [roomsize]. Sets the reverb room size for this pattern. */
@StrudelDsl
fun StrudelPattern.size(amount: PatternLike): StrudelPattern = this._size(listOf(amount).asStrudelDslArgs())

/** Alias for [roomsize]. Parses this string as a pattern and sets the reverb room size. */
@StrudelDsl
fun String.size(amount: PatternLike): StrudelPattern = this._size(listOf(amount).asStrudelDslArgs())

// -- roomfade() / rfade() ---------------------------------------------------------------------------------------------

private val roomFadeMutation = voiceModifier { copy(roomFade = it?.asDoubleOrNull()) }

fun applyRoomFade(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, roomFadeMutation)
}

internal val _roomfade by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(roomFadeMutation) }
internal val StrudelPattern._roomfade by dslPatternExtension { p, args, /* callInfo */ _ -> applyRoomFade(p, args) }
internal val String._roomfade by dslStringExtension { p, args, callInfo -> p._roomfade(args, callInfo) }

internal val _rfade by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(roomFadeMutation) }
internal val StrudelPattern._rfade by dslPatternExtension { p, args, /* callInfo */ _ -> applyRoomFade(p, args) }
internal val String._rfade by dslStringExtension { p, args, callInfo -> p._rfade(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the reverb fade time in seconds.
 *
 * Controls how long the reverb tail takes to fade out. Longer values create more sustained
 * tails that persist after the dry signal ends.
 *
 * ```KlangScript
 * note("c3 e3").room(0.6).roomfade(2.0)    // 2-second fade
 * ```
 *
 * ```KlangScript
 * note("c3*4").roomfade("<0.5 1 2 4>")     // increasing fade time
 * ```
 *
 * @alias rfade
 * @category effects
 * @tags roomfade, rfade, reverb, fade, tail
 */
@StrudelDsl
fun roomfade(time: PatternLike): StrudelPattern = _roomfade(listOf(time).asStrudelDslArgs())

/** Sets the reverb fade time for this pattern. */
@StrudelDsl
fun StrudelPattern.roomfade(time: PatternLike): StrudelPattern = this._roomfade(listOf(time).asStrudelDslArgs())

/** Parses this string as a pattern and sets the reverb fade time. */
@StrudelDsl
fun String.roomfade(time: PatternLike): StrudelPattern = this._roomfade(listOf(time).asStrudelDslArgs())

/**
 * Alias for [roomfade]. Sets the reverb fade time in seconds.
 *
 * ```KlangScript
 * note("c3 e3").room(0.6).rfade(2.0)    // 2-second fade
 * ```
 *
 * ```KlangScript
 * note("c3*4").rfade("<0.5 1 2 4>")     // increasing fade time
 * ```
 *
 * @alias roomfade
 * @category effects
 * @tags rfade, roomfade, reverb, fade, tail
 */
@StrudelDsl
fun rfade(time: PatternLike): StrudelPattern = _rfade(listOf(time).asStrudelDslArgs())

/** Alias for [roomfade]. Sets the reverb fade time for this pattern. */
@StrudelDsl
fun StrudelPattern.rfade(time: PatternLike): StrudelPattern = this._rfade(listOf(time).asStrudelDslArgs())

/** Alias for [roomfade]. Parses this string as a pattern and sets the reverb fade time. */
@StrudelDsl
fun String.rfade(time: PatternLike): StrudelPattern = this._rfade(listOf(time).asStrudelDslArgs())

// -- roomlp() / rlp() -------------------------------------------------------------------------------------------------

private val roomLpMutation = voiceModifier { copy(roomLp = it?.asDoubleOrNull()) }

fun applyRoomLp(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, roomLpMutation)
}

internal val _roomlp by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(roomLpMutation) }
internal val StrudelPattern._roomlp by dslPatternExtension { p, args, /* callInfo */ _ -> applyRoomLp(p, args) }
internal val String._roomlp by dslStringExtension { p, args, callInfo -> p._roomlp(args, callInfo) }

internal val _rlp by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(roomLpMutation) }
internal val StrudelPattern._rlp by dslPatternExtension { p, args, /* callInfo */ _ -> applyRoomLp(p, args) }
internal val String._rlp by dslStringExtension { p, args, callInfo -> p._rlp(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the reverb lowpass start frequency in Hz.
 *
 * Applies a lowpass filter to the reverb tail starting at the specified frequency,
 * making the reverb darker and less bright.
 *
 * ```KlangScript
 * note("c3 e3").room(0.6).roomlp(4000)   // dark reverb tail
 * ```
 *
 * ```KlangScript
 * note("c3*4").roomlp("<8000 4000 2000 1000>")   // increasingly dark reverb
 * ```
 *
 * @alias rlp
 * @category effects
 * @tags roomlp, rlp, reverb, lowpass, filter
 */
@StrudelDsl
fun roomlp(freq: PatternLike): StrudelPattern = _roomlp(listOf(freq).asStrudelDslArgs())

/** Sets the reverb lowpass start frequency for this pattern. */
@StrudelDsl
fun StrudelPattern.roomlp(freq: PatternLike): StrudelPattern = this._roomlp(listOf(freq).asStrudelDslArgs())

/** Parses this string as a pattern and sets the reverb lowpass start frequency. */
@StrudelDsl
fun String.roomlp(freq: PatternLike): StrudelPattern = this._roomlp(listOf(freq).asStrudelDslArgs())

/**
 * Alias for [roomlp]. Sets the reverb lowpass start frequency in Hz.
 *
 * ```KlangScript
 * note("c3 e3").room(0.6).rlp(4000)   // dark reverb tail
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
fun rlp(freq: PatternLike): StrudelPattern = _rlp(listOf(freq).asStrudelDslArgs())

/** Alias for [roomlp]. Sets the reverb lowpass start frequency for this pattern. */
@StrudelDsl
fun StrudelPattern.rlp(freq: PatternLike): StrudelPattern = this._rlp(listOf(freq).asStrudelDslArgs())

/** Alias for [roomlp]. Parses this string as a pattern and sets the reverb lowpass start frequency. */
@StrudelDsl
fun String.rlp(freq: PatternLike): StrudelPattern = this._rlp(listOf(freq).asStrudelDslArgs())

// -- roomdim() / rdim() -----------------------------------------------------------------------------------------------

private val roomDimMutation = voiceModifier { copy(roomDim = it?.asDoubleOrNull()) }

fun applyRoomDim(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, roomDimMutation)
}

internal val _roomdim by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(roomDimMutation) }
internal val StrudelPattern._roomdim by dslPatternExtension { p, args, /* callInfo */ _ -> applyRoomDim(p, args) }
internal val String._roomdim by dslStringExtension { p, args, callInfo -> p._roomdim(args, callInfo) }

internal val _rdim by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(roomDimMutation) }
internal val StrudelPattern._rdim by dslPatternExtension { p, args, /* callInfo */ _ -> applyRoomDim(p, args) }
internal val String._rdim by dslStringExtension { p, args, callInfo -> p._rdim(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the reverb lowpass frequency at -60 dB.
 *
 * Determines the frequency at which the reverb tail has decayed to -60 dB, controlling
 * the overall brightness of the reverb at full decay.
 *
 * ```KlangScript
 * note("c3 e3").room(0.6).roomdim(500)   // very dark, dim reverb
 * ```
 *
 * ```KlangScript
 * note("c3*4").roomdim("<2000 1000 500 200>")   // progressively dimmer reverb
 * ```
 *
 * @alias rdim
 * @category effects
 * @tags roomdim, rdim, reverb, lowpass, darkness
 */
@StrudelDsl
fun roomdim(freq: PatternLike): StrudelPattern = _roomdim(listOf(freq).asStrudelDslArgs())

/** Sets the reverb lowpass frequency at -60 dB for this pattern. */
@StrudelDsl
fun StrudelPattern.roomdim(freq: PatternLike): StrudelPattern = this._roomdim(listOf(freq).asStrudelDslArgs())

/** Parses this string as a pattern and sets the reverb lowpass frequency at -60 dB. */
@StrudelDsl
fun String.roomdim(freq: PatternLike): StrudelPattern = this._roomdim(listOf(freq).asStrudelDslArgs())

/**
 * Alias for [roomdim]. Sets the reverb lowpass frequency at -60 dB.
 *
 * ```KlangScript
 * note("c3 e3").room(0.6).rdim(500)   // very dark, dim reverb
 * ```
 *
 * ```KlangScript
 * note("c3*4").rdim("<2000 1000 500 200>")   // progressively dimmer reverb
 * ```
 *
 * @alias roomdim
 * @category effects
 * @tags rdim, roomdim, reverb, lowpass, darkness
 */
@StrudelDsl
fun rdim(freq: PatternLike): StrudelPattern = _rdim(listOf(freq).asStrudelDslArgs())

/** Alias for [roomdim]. Sets the reverb lowpass frequency at -60 dB for this pattern. */
@StrudelDsl
fun StrudelPattern.rdim(freq: PatternLike): StrudelPattern = this._rdim(listOf(freq).asStrudelDslArgs())

/** Alias for [roomdim]. Parses this string as a pattern and sets the reverb lowpass frequency at -60 dB. */
@StrudelDsl
fun String.rdim(freq: PatternLike): StrudelPattern = this._rdim(listOf(freq).asStrudelDslArgs())

// -- iresponse() / ir() -----------------------------------------------------------------------------------------------

private val iResponseMutation = voiceModifier { response -> copy(iResponse = response?.toString()) }

fun applyIResponse(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._applyControlFromParams(args, iResponseMutation) { src, ctrl ->
        src.copy(iResponse = ctrl.iResponse)
    }
}

internal val _iresponse by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(iResponseMutation) }
internal val StrudelPattern._iresponse by dslPatternExtension { p, args, /* callInfo */ _ -> applyIResponse(p, args) }
internal val String._iresponse by dslStringExtension { p, args, callInfo -> p._iresponse(args, callInfo) }

internal val _ir by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(iResponseMutation) }
internal val StrudelPattern._ir by dslPatternExtension { p, args, /* callInfo */ _ -> applyIResponse(p, args) }
internal val String._ir by dslStringExtension { p, args, callInfo -> p._ir(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the impulse response sample name for convolution reverb.
 *
 * Uses a recorded impulse response to simulate the acoustics of a real space. The value
 * is the name of an IR sample loaded in the audio engine.
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
fun iresponse(name: PatternLike): StrudelPattern = _iresponse(listOf(name).asStrudelDslArgs())

/** Sets the impulse response sample for convolution reverb on this pattern. */
@StrudelDsl
fun StrudelPattern.iresponse(name: PatternLike): StrudelPattern = this._iresponse(listOf(name).asStrudelDslArgs())

/** Parses this string as a pattern and sets the impulse response sample for convolution reverb. */
@StrudelDsl
fun String.iresponse(name: PatternLike): StrudelPattern = this._iresponse(listOf(name).asStrudelDslArgs())

/**
 * Alias for [iresponse]. Sets the impulse response sample name for convolution reverb.
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
fun ir(name: PatternLike): StrudelPattern = _ir(listOf(name).asStrudelDslArgs())

/** Alias for [iresponse]. Sets the impulse response sample for convolution reverb on this pattern. */
@StrudelDsl
fun StrudelPattern.ir(name: PatternLike): StrudelPattern = this._ir(listOf(name).asStrudelDslArgs())

/** Alias for [iresponse]. Parses this string as a pattern and sets the impulse response sample. */
@StrudelDsl
fun String.ir(name: PatternLike): StrudelPattern = this._ir(listOf(name).asStrudelDslArgs())

// -- delay() ----------------------------------------------------------------------------------------------------------

private val delayMutation = voiceModifier { copy(delay = it?.asDoubleOrNull()) }

fun applyDelay(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, delayMutation)
}

internal val _delay by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(delayMutation) }
internal val StrudelPattern._delay by dslPatternExtension { p, args, /* callInfo */ _ -> applyDelay(p, args) }
internal val String._delay by dslStringExtension { p, args, callInfo -> p._delay(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the delay wet/dry mix for the pattern (0 = dry, 1 = full wet).
 *
 * Use with `delaytime` to set the delay interval and `delayfeedback` to control
 * the number of repeats.
 *
 * ```KlangScript
 * note("c3 e3").delay(0.4)                         // 40% delay mix
 * ```
 *
 * ```KlangScript
 * note("c3*4").delay(0.5).delaytime(0.25).delayfeedback(0.6)  // dotted-eighth delay
 * ```
 *
 * @category effects
 * @tags delay, echo, wet, mix
 */
@StrudelDsl
fun delay(amount: PatternLike): StrudelPattern = _delay(listOf(amount).asStrudelDslArgs())

/** Sets the delay wet/dry mix for this pattern. */
@StrudelDsl
fun StrudelPattern.delay(amount: PatternLike): StrudelPattern = this._delay(listOf(amount).asStrudelDslArgs())

/** Parses this string as a pattern and sets the delay wet/dry mix. */
@StrudelDsl
fun String.delay(amount: PatternLike): StrudelPattern = this._delay(listOf(amount).asStrudelDslArgs())

// -- delaytime() ------------------------------------------------------------------------------------------------------

private val delayTimeMutation = voiceModifier { copy(delayTime = it?.asDoubleOrNull()) }

fun applyDelayTime(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, delayTimeMutation)
}

internal val _delaytime by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(delayTimeMutation) }
internal val StrudelPattern._delaytime by dslPatternExtension { p, args, /* callInfo */ _ -> applyDelayTime(p, args) }
internal val String._delaytime by dslStringExtension { p, args, callInfo -> p._delaytime(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the delay time in seconds (the interval between repeats).
 *
 * Use with `delay` for wet/dry mix and `delayfeedback` for the number of repeats.
 * Musical values: `0.25` = quarter note at 60 BPM, `0.5` = half note.
 *
 * ```KlangScript
 * note("c3").delay(0.5).delaytime(0.375)   // dotted-eighth delay
 * ```
 *
 * ```KlangScript
 * note("c3*4").delaytime("<0.125 0.25 0.5>")   // varying delay times
 * ```
 *
 * @category effects
 * @tags delaytime, delay, echo, time, interval
 */
@StrudelDsl
fun delaytime(time: PatternLike): StrudelPattern = _delaytime(listOf(time).asStrudelDslArgs())

/** Sets the delay time for this pattern. */
@StrudelDsl
fun StrudelPattern.delaytime(time: PatternLike): StrudelPattern = this._delaytime(listOf(time).asStrudelDslArgs())

/** Parses this string as a pattern and sets the delay time. */
@StrudelDsl
fun String.delaytime(time: PatternLike): StrudelPattern = this._delaytime(listOf(time).asStrudelDslArgs())

// -- delayfeedback() / delayfb() / dfb() ------------------------------------------------------------------------------

private val delayFeedbackMutation = voiceModifier { copy(delayFeedback = it?.asDoubleOrNull()) }

fun applyDelayFeedback(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, delayFeedbackMutation)
}

internal val _delayfeedback by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(delayFeedbackMutation) }
internal val StrudelPattern._delayfeedback by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyDelayFeedback(p, args)
}
internal val String._delayfeedback by dslStringExtension { p, args, callInfo -> p._delayfeedback(args, callInfo) }

internal val _delayfb by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(delayFeedbackMutation) }
internal val StrudelPattern._delayfb by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyDelayFeedback(p, args)
}
internal val String._delayfb by dslStringExtension { p, args, callInfo -> p._delayfb(args, callInfo) }

internal val _dfb by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(delayFeedbackMutation) }
internal val StrudelPattern._dfb by dslPatternExtension { p, args, /* callInfo */ _ -> applyDelayFeedback(p, args) }
internal val String._dfb by dslStringExtension { p, args, callInfo -> p._dfb(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the delay feedback amount (0–1), controlling the number of echoes.
 *
 * Higher values produce more repeats. Values near 1 create infinite repeats.
 * Use with `delay` and `delaytime` to set up the full delay effect.
 *
 * ```KlangScript
 * note("c3").delay(0.4).delaytime(0.25).delayfeedback(0.6)   // 3-4 echoes
 * ```
 *
 * ```KlangScript
 * note("c3*4").delayfeedback("<0.2 0.4 0.6 0.8>")            // increasing echoes
 * ```
 *
 * @alias delayfb, dfb
 * @category effects
 * @tags delayfeedback, delayfb, dfb, delay, echo, feedback, repeats
 */
@StrudelDsl
fun delayfeedback(amount: PatternLike): StrudelPattern = _delayfeedback(listOf(amount).asStrudelDslArgs())

/** Sets the delay feedback amount for this pattern. */
@StrudelDsl
fun StrudelPattern.delayfeedback(amount: PatternLike): StrudelPattern =
    this._delayfeedback(listOf(amount).asStrudelDslArgs())

/** Parses this string as a pattern and sets the delay feedback amount. */
@StrudelDsl
fun String.delayfeedback(amount: PatternLike): StrudelPattern =
    this._delayfeedback(listOf(amount).asStrudelDslArgs())

/**
 * Alias for [delayfeedback]. Sets the delay feedback amount (0–1).
 *
 * ```KlangScript
 * note("c3").delay(0.4).delaytime(0.25).delayfb(0.6)   // 3-4 echoes
 * ```
 *
 * ```KlangScript
 * note("c3*4").delayfb("<0.2 0.4 0.6 0.8>")            // increasing echoes
 * ```
 *
 * @alias delayfeedback, dfb
 * @category effects
 * @tags delayfb, delayfeedback, dfb, delay, echo, feedback, repeats
 */
@StrudelDsl
fun delayfb(amount: PatternLike): StrudelPattern = _delayfb(listOf(amount).asStrudelDslArgs())

/** Alias for [delayfeedback]. Sets the delay feedback amount for this pattern. */
@StrudelDsl
fun StrudelPattern.delayfb(amount: PatternLike): StrudelPattern = this._delayfb(listOf(amount).asStrudelDslArgs())

/** Alias for [delayfeedback]. Parses this string as a pattern and sets the delay feedback amount. */
@StrudelDsl
fun String.delayfb(amount: PatternLike): StrudelPattern = this._delayfb(listOf(amount).asStrudelDslArgs())

/**
 * Alias for [delayfeedback]. Sets the delay feedback amount (0–1).
 *
 * ```KlangScript
 * note("c3").delay(0.4).delaytime(0.25).dfb(0.6)   // 3-4 echoes
 * ```
 *
 * ```KlangScript
 * note("c3*4").dfb("<0.2 0.4 0.6 0.8>")            // increasing echoes
 * ```
 *
 * @alias delayfeedback, delayfb
 * @category effects
 * @tags dfb, delayfeedback, delayfb, delay, echo, feedback, repeats
 */
@StrudelDsl
fun dfb(amount: PatternLike): StrudelPattern = _dfb(listOf(amount).asStrudelDslArgs())

/** Alias for [delayfeedback]. Sets the delay feedback amount for this pattern. */
@StrudelDsl
fun StrudelPattern.dfb(amount: PatternLike): StrudelPattern = this._dfb(listOf(amount).asStrudelDslArgs())

/** Alias for [delayfeedback]. Parses this string as a pattern and sets the delay feedback amount. */
@StrudelDsl
fun String.dfb(amount: PatternLike): StrudelPattern = this._dfb(listOf(amount).asStrudelDslArgs())

// -- phaser() / ph() --------------------------------------------------------------------------------------------------

private val phaserMutation = voiceModifier { copy(phaserRate = it?.asDoubleOrNull()) }

fun applyPhaser(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, phaserMutation)
}

internal val _phaser by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(phaserMutation) }
internal val StrudelPattern._phaser by dslPatternExtension { p, args, /* callInfo */ _ -> applyPhaser(p, args) }
internal val String._phaser by dslStringExtension { p, args, callInfo -> p._phaser(args, callInfo) }

internal val _ph by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(phaserMutation) }
internal val StrudelPattern._ph by dslPatternExtension { p, args, /* callInfo */ _ -> applyPhaser(p, args) }
internal val String._ph by dslStringExtension { p, args, callInfo -> p._ph(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the phaser LFO rate in Hz.
 *
 * A phaser creates a sweeping comb-filter effect by modulating a series of all-pass filters.
 * Higher rate values produce faster sweeping. Use with `phaserdepth` and `phasercenter`.
 *
 * ```KlangScript
 * note("c3 e3 g3").s("sawtooth").phaser(0.5)   // slow phaser sweep
 * ```
 *
 * ```KlangScript
 * note("c3*4").phaser("<0.1 0.5 1 4>")         // accelerating phaser rate
 * ```
 *
 * @alias ph
 * @category effects
 * @tags phaser, ph, phase, sweep, modulation
 */
@StrudelDsl
fun phaser(rate: PatternLike): StrudelPattern = _phaser(listOf(rate).asStrudelDslArgs())

/** Sets the phaser LFO rate for this pattern. */
@StrudelDsl
fun StrudelPattern.phaser(rate: PatternLike): StrudelPattern = this._phaser(listOf(rate).asStrudelDslArgs())

/** Parses this string as a pattern and sets the phaser LFO rate. */
@StrudelDsl
fun String.phaser(rate: PatternLike): StrudelPattern = this._phaser(listOf(rate).asStrudelDslArgs())

/**
 * Alias for [phaser]. Sets the phaser LFO rate in Hz.
 *
 * ```KlangScript
 * note("c3 e3 g3").s("sawtooth").ph(0.5)   // slow phaser sweep
 * ```
 *
 * ```KlangScript
 * note("c3*4").ph("<0.1 0.5 1 4>")         // accelerating phaser rate
 * ```
 *
 * @alias phaser
 * @category effects
 * @tags ph, phaser, phase, sweep, modulation
 */
@StrudelDsl
fun ph(rate: PatternLike): StrudelPattern = _ph(listOf(rate).asStrudelDslArgs())

/** Alias for [phaser]. Sets the phaser LFO rate for this pattern. */
@StrudelDsl
fun StrudelPattern.ph(rate: PatternLike): StrudelPattern = this._ph(listOf(rate).asStrudelDslArgs())

/** Alias for [phaser]. Parses this string as a pattern and sets the phaser LFO rate. */
@StrudelDsl
fun String.ph(rate: PatternLike): StrudelPattern = this._ph(listOf(rate).asStrudelDslArgs())

// -- phaserdepth() / phd() / phasdp() ---------------------------------------------------------------------------------

private val phaserDepthMutation = voiceModifier { copy(phaserDepth = it?.asDoubleOrNull()) }

fun applyPhaserDepth(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, phaserDepthMutation)
}

internal val _phaserdepth by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(phaserDepthMutation) }
internal val StrudelPattern._phaserdepth by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyPhaserDepth(p, args)
}
internal val String._phaserdepth by dslStringExtension { p, args, callInfo -> p._phaserdepth(args, callInfo) }

internal val _phd by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(phaserDepthMutation) }
internal val StrudelPattern._phd by dslPatternExtension { p, args, /* callInfo */ _ -> applyPhaserDepth(p, args) }
internal val String._phd by dslStringExtension { p, args, callInfo -> p._phd(args, callInfo) }

internal val _phasdp by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(phaserDepthMutation) }
internal val StrudelPattern._phasdp by dslPatternExtension { p, args, /* callInfo */ _ -> applyPhaserDepth(p, args) }
internal val String._phasdp by dslStringExtension { p, args, callInfo -> p._phasdp(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the phaser depth (modulation intensity).
 *
 * Controls how strongly the phaser sweeps across frequencies. Higher values produce
 * a more pronounced notch-filter sweep effect.
 *
 * ```KlangScript
 * note("c3*4").phaser(0.5).phaserdepth(0.8)   // deep phaser sweep
 * ```
 *
 * ```KlangScript
 * note("c3*4").phaserdepth("<0.2 0.5 0.8 1.0>")   // increasing phaser depth
 * ```
 *
 * @alias phd, phasdp
 * @category effects
 * @tags phaserdepth, phd, phasdp, phaser, depth, modulation
 */
@StrudelDsl
fun phaserdepth(amount: PatternLike): StrudelPattern = _phaserdepth(listOf(amount).asStrudelDslArgs())

/** Sets the phaser depth for this pattern. */
@StrudelDsl
fun StrudelPattern.phaserdepth(amount: PatternLike): StrudelPattern =
    this._phaserdepth(listOf(amount).asStrudelDslArgs())

/** Parses this string as a pattern and sets the phaser depth. */
@StrudelDsl
fun String.phaserdepth(amount: PatternLike): StrudelPattern = this._phaserdepth(listOf(amount).asStrudelDslArgs())

/**
 * Alias for [phaserdepth]. Sets the phaser depth (modulation intensity).
 *
 * ```KlangScript
 * note("c3*4").phaser(0.5).phd(0.8)   // deep phaser sweep
 * ```
 *
 * ```KlangScript
 * note("c3*4").phd("<0.2 0.5 0.8 1.0>")   // increasing phaser depth
 * ```
 *
 * @alias phaserdepth, phasdp
 * @category effects
 * @tags phd, phaserdepth, phasdp, phaser, depth, modulation
 */
@StrudelDsl
fun phd(amount: PatternLike): StrudelPattern = _phd(listOf(amount).asStrudelDslArgs())

/** Alias for [phaserdepth]. Sets the phaser depth for this pattern. */
@StrudelDsl
fun StrudelPattern.phd(amount: PatternLike): StrudelPattern = this._phd(listOf(amount).asStrudelDslArgs())

/** Alias for [phaserdepth]. Parses this string as a pattern and sets the phaser depth. */
@StrudelDsl
fun String.phd(amount: PatternLike): StrudelPattern = this._phd(listOf(amount).asStrudelDslArgs())

/**
 * Alias for [phaserdepth]. Sets the phaser depth (modulation intensity).
 *
 * ```KlangScript
 * note("c3*4").phaser(0.5).phasdp(0.8)   // deep phaser sweep
 * ```
 *
 * ```KlangScript
 * note("c3*4").phasdp("<0.2 0.5 0.8 1.0>")   // increasing phaser depth
 * ```
 *
 * @alias phaserdepth, phd
 * @category effects
 * @tags phasdp, phaserdepth, phd, phaser, depth, modulation
 */
@StrudelDsl
fun phasdp(amount: PatternLike): StrudelPattern = _phasdp(listOf(amount).asStrudelDslArgs())

/** Alias for [phaserdepth]. Sets the phaser depth for this pattern. */
@StrudelDsl
fun StrudelPattern.phasdp(amount: PatternLike): StrudelPattern = this._phasdp(listOf(amount).asStrudelDslArgs())

/** Alias for [phaserdepth]. Parses this string as a pattern and sets the phaser depth. */
@StrudelDsl
fun String.phasdp(amount: PatternLike): StrudelPattern = this._phasdp(listOf(amount).asStrudelDslArgs())

// -- phasercenter() / phc() -------------------------------------------------------------------------------------------

private val phaserCenterMutation = voiceModifier { copy(phaserCenter = it?.asDoubleOrNull()) }

fun applyPhaserCenter(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, phaserCenterMutation)
}

internal val _phasercenter by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(phaserCenterMutation) }
internal val StrudelPattern._phasercenter by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyPhaserCenter(p, args)
}
internal val String._phasercenter by dslStringExtension { p, args, callInfo -> p._phasercenter(args, callInfo) }

internal val _phc by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(phaserCenterMutation) }
internal val StrudelPattern._phc by dslPatternExtension { p, args, /* callInfo */ _ -> applyPhaserCenter(p, args) }
internal val String._phc by dslStringExtension { p, args, callInfo -> p._phc(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the phaser center frequency in Hz.
 *
 * The center frequency is the midpoint of the phaser's sweep range. Adjusting it shifts
 * where the notch-filter effect is focused in the frequency spectrum.
 *
 * ```KlangScript
 * note("c3*4").phaser(0.5).phasercenter(1000)   // centered around 1 kHz
 * ```
 *
 * ```KlangScript
 * note("c3*4").phasercenter("<500 1000 2000 4000>")   // sweeping center frequency
 * ```
 *
 * @alias phc
 * @category effects
 * @tags phasercenter, phc, phaser, frequency, center
 */
@StrudelDsl
fun phasercenter(freq: PatternLike): StrudelPattern = _phasercenter(listOf(freq).asStrudelDslArgs())

/** Sets the phaser center frequency for this pattern. */
@StrudelDsl
fun StrudelPattern.phasercenter(freq: PatternLike): StrudelPattern =
    this._phasercenter(listOf(freq).asStrudelDslArgs())

/** Parses this string as a pattern and sets the phaser center frequency. */
@StrudelDsl
fun String.phasercenter(freq: PatternLike): StrudelPattern = this._phasercenter(listOf(freq).asStrudelDslArgs())

/**
 * Alias for [phasercenter]. Sets the phaser center frequency in Hz.
 *
 * ```KlangScript
 * note("c3*4").phaser(0.5).phc(1000)   // centered around 1 kHz
 * ```
 *
 * ```KlangScript
 * note("c3*4").phc("<500 1000 2000 4000>")   // sweeping center frequency
 * ```
 *
 * @alias phasercenter
 * @category effects
 * @tags phc, phasercenter, phaser, frequency, center
 */
@StrudelDsl
fun phc(freq: PatternLike): StrudelPattern = _phc(listOf(freq).asStrudelDslArgs())

/** Alias for [phasercenter]. Sets the phaser center frequency for this pattern. */
@StrudelDsl
fun StrudelPattern.phc(freq: PatternLike): StrudelPattern = this._phc(listOf(freq).asStrudelDslArgs())

/** Alias for [phasercenter]. Parses this string as a pattern and sets the phaser center frequency. */
@StrudelDsl
fun String.phc(freq: PatternLike): StrudelPattern = this._phc(listOf(freq).asStrudelDslArgs())

// -- phasersweep() / phs() --------------------------------------------------------------------------------------------

private val phaserSweepMutation = voiceModifier { copy(phaserSweep = it?.asDoubleOrNull()) }

fun applyPhaserSweep(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, phaserSweepMutation)
}

internal val _phasersweep by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(phaserSweepMutation) }
internal val StrudelPattern._phasersweep by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyPhaserSweep(p, args)
}
internal val String._phasersweep by dslStringExtension { p, args, callInfo -> p._phasersweep(args, callInfo) }

internal val _phs by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(phaserSweepMutation) }
internal val StrudelPattern._phs by dslPatternExtension { p, args, /* callInfo */ _ -> applyPhaserSweep(p, args) }
internal val String._phs by dslStringExtension { p, args, callInfo -> p._phs(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the phaser sweep range in Hz (half the total sweep width).
 *
 * Controls how wide the phaser's frequency sweep is around the center frequency.
 * Larger values produce a more dramatic, wider sweep effect.
 *
 * ```KlangScript
 * note("c3*4").phaser(0.5).phasersweep(2000)   // ±2000 Hz sweep
 * ```
 *
 * ```KlangScript
 * note("c3*4").phasersweep("<500 1000 2000 4000>")   // increasing sweep width
 * ```
 *
 * @alias phs
 * @category effects
 * @tags phasersweep, phs, phaser, sweep, width
 */
@StrudelDsl
fun phasersweep(amount: PatternLike): StrudelPattern = _phasersweep(listOf(amount).asStrudelDslArgs())

/** Sets the phaser sweep range for this pattern. */
@StrudelDsl
fun StrudelPattern.phasersweep(amount: PatternLike): StrudelPattern =
    this._phasersweep(listOf(amount).asStrudelDslArgs())

/** Parses this string as a pattern and sets the phaser sweep range. */
@StrudelDsl
fun String.phasersweep(amount: PatternLike): StrudelPattern = this._phasersweep(listOf(amount).asStrudelDslArgs())

/**
 * Alias for [phasersweep]. Sets the phaser sweep range in Hz.
 *
 * ```KlangScript
 * note("c3*4").phaser(0.5).phs(2000)   // ±2000 Hz sweep
 * ```
 *
 * ```KlangScript
 * note("c3*4").phs("<500 1000 2000 4000>")   // increasing sweep width
 * ```
 *
 * @alias phasersweep
 * @category effects
 * @tags phs, phasersweep, phaser, sweep, width
 */
@StrudelDsl
fun phs(amount: PatternLike): StrudelPattern = _phs(listOf(amount).asStrudelDslArgs())

/** Alias for [phasersweep]. Sets the phaser sweep range for this pattern. */
@StrudelDsl
fun StrudelPattern.phs(amount: PatternLike): StrudelPattern = this._phs(listOf(amount).asStrudelDslArgs())

/** Alias for [phasersweep]. Parses this string as a pattern and sets the phaser sweep range. */
@StrudelDsl
fun String.phs(amount: PatternLike): StrudelPattern = this._phs(listOf(amount).asStrudelDslArgs())

// -- tremolosync() / tremsync() ---------------------------------------------------------------------------------------

private val tremoloSyncMutation = voiceModifier { copy(tremoloSync = it?.asDoubleOrNull()) }

fun applyTremoloSync(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, tremoloSyncMutation)
}

internal val _tremolosync by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(tremoloSyncMutation) }
internal val StrudelPattern._tremolosync by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyTremoloSync(p, args)
}
internal val String._tremolosync by dslStringExtension { p, args, callInfo -> p._tremolosync(args, callInfo) }

internal val _tremsync by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(tremoloSyncMutation) }
internal val StrudelPattern._tremsync by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyTremoloSync(p, args)
}
internal val String._tremsync by dslStringExtension { p, args, callInfo -> p._tremsync(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the tremolo LFO rate in Hz (sync mode).
 *
 * Controls the speed of the tremolo amplitude modulation effect. Use with `tremolodepth`
 * to set the modulation intensity and `tremoloshape` to choose the LFO waveform.
 *
 * ```KlangScript
 * note("c3 e3").s("sine").tremolosync(4)   // 4 Hz tremolo
 * ```
 *
 * ```KlangScript
 * note("c3*4").tremolosync("<1 2 4 8>")   // accelerating tremolo rate
 * ```
 *
 * @alias tremsync
 * @category effects
 * @tags tremolosync, tremsync, tremolo, rate, modulation
 */
@StrudelDsl
fun tremolosync(rate: PatternLike): StrudelPattern = _tremolosync(listOf(rate).asStrudelDslArgs())

/** Sets the tremolo LFO rate for this pattern. */
@StrudelDsl
fun StrudelPattern.tremolosync(rate: PatternLike): StrudelPattern = this._tremolosync(listOf(rate).asStrudelDslArgs())

/** Parses this string as a pattern and sets the tremolo LFO rate. */
@StrudelDsl
fun String.tremolosync(rate: PatternLike): StrudelPattern = this._tremolosync(listOf(rate).asStrudelDslArgs())

/**
 * Alias for [tremolosync]. Sets the tremolo LFO rate in Hz.
 *
 * ```KlangScript
 * note("c3 e3").s("sine").tremsync(4)   // 4 Hz tremolo
 * ```
 *
 * ```KlangScript
 * note("c3*4").tremsync("<1 2 4 8>")   // accelerating tremolo rate
 * ```
 *
 * @alias tremolosync
 * @category effects
 * @tags tremsync, tremolosync, tremolo, rate, modulation
 */
@StrudelDsl
fun tremsync(rate: PatternLike): StrudelPattern = _tremsync(listOf(rate).asStrudelDslArgs())

/** Alias for [tremolosync]. Sets the tremolo LFO rate for this pattern. */
@StrudelDsl
fun StrudelPattern.tremsync(rate: PatternLike): StrudelPattern = this._tremsync(listOf(rate).asStrudelDslArgs())

/** Alias for [tremolosync]. Parses this string as a pattern and sets the tremolo LFO rate. */
@StrudelDsl
fun String.tremsync(rate: PatternLike): StrudelPattern = this._tremsync(listOf(rate).asStrudelDslArgs())

// -- tremolodepth() / tremdepth() -------------------------------------------------------------------------------------

private val tremoloDepthMutation = voiceModifier { copy(tremoloDepth = it?.asDoubleOrNull()) }

fun applyTremoloDepth(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, tremoloDepthMutation)
}

internal val _tremolodepth by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(tremoloDepthMutation) }
internal val StrudelPattern._tremolodepth by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyTremoloDepth(p, args)
}
internal val String._tremolodepth by dslStringExtension { p, args, callInfo -> p._tremolodepth(args, callInfo) }

internal val _tremdepth by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(tremoloDepthMutation) }
internal val StrudelPattern._tremdepth by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyTremoloDepth(p, args)
}
internal val String._tremdepth by dslStringExtension { p, args, callInfo -> p._tremdepth(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the tremolo depth (modulation intensity, 0–1).
 *
 * Controls how much the amplitude is modulated by the tremolo LFO. `0` = no effect;
 * `1` = full amplitude modulation (silence to full volume).
 *
 * ```KlangScript
 * note("c3 e3").s("sine").tremolosync(4).tremolodepth(0.8)   // strong tremolo
 * ```
 *
 * ```KlangScript
 * note("c3*4").tremolodepth("<0.2 0.5 0.8 1.0>")             // increasing depth
 * ```
 *
 * @alias tremdepth
 * @category effects
 * @tags tremolodepth, tremdepth, tremolo, depth, modulation
 */
@StrudelDsl
fun tremolodepth(amount: PatternLike): StrudelPattern = _tremolodepth(listOf(amount).asStrudelDslArgs())

/** Sets the tremolo depth for this pattern. */
@StrudelDsl
fun StrudelPattern.tremolodepth(amount: PatternLike): StrudelPattern =
    this._tremolodepth(listOf(amount).asStrudelDslArgs())

/** Parses this string as a pattern and sets the tremolo depth. */
@StrudelDsl
fun String.tremolodepth(amount: PatternLike): StrudelPattern = this._tremolodepth(listOf(amount).asStrudelDslArgs())

/**
 * Alias for [tremolodepth]. Sets the tremolo depth (modulation intensity, 0–1).
 *
 * ```KlangScript
 * note("c3 e3").s("sine").tremolosync(4).tremdepth(0.8)   // strong tremolo
 * ```
 *
 * ```KlangScript
 * note("c3*4").tremdepth("<0.2 0.5 0.8 1.0>")             // increasing depth
 * ```
 *
 * @alias tremolodepth
 * @category effects
 * @tags tremdepth, tremolodepth, tremolo, depth, modulation
 */
@StrudelDsl
fun tremdepth(amount: PatternLike): StrudelPattern = _tremdepth(listOf(amount).asStrudelDslArgs())

/** Alias for [tremolodepth]. Sets the tremolo depth for this pattern. */
@StrudelDsl
fun StrudelPattern.tremdepth(amount: PatternLike): StrudelPattern = this._tremdepth(listOf(amount).asStrudelDslArgs())

/** Alias for [tremolodepth]. Parses this string as a pattern and sets the tremolo depth. */
@StrudelDsl
fun String.tremdepth(amount: PatternLike): StrudelPattern = this._tremdepth(listOf(amount).asStrudelDslArgs())

// -- tremoloskew() / tremskew() ---------------------------------------------------------------------------------------

private val tremoloSkewMutation = voiceModifier { copy(tremoloSkew = it?.asDoubleOrNull()) }

fun applyTremoloSkew(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, tremoloSkewMutation)
}

internal val _tremoloskew by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(tremoloSkewMutation) }
internal val StrudelPattern._tremoloskew by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyTremoloSkew(p, args)
}
internal val String._tremoloskew by dslStringExtension { p, args, callInfo -> p._tremoloskew(args, callInfo) }

internal val _tremskew by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(tremoloSkewMutation) }
internal val StrudelPattern._tremskew by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyTremoloSkew(p, args)
}
internal val String._tremskew by dslStringExtension { p, args, callInfo -> p._tremskew(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the tremolo LFO skew (asymmetry) value.
 *
 * Adjusts the asymmetry of the tremolo waveform. A value of `0.5` is symmetric;
 * values above or below shift the waveform to spend more time at the top or bottom.
 *
 * ```KlangScript
 * note("c3*4").tremolosync(2).tremoloskew(0.8)   // skewed toward peak
 * ```
 *
 * ```KlangScript
 * note("c3*4").tremoloskew("<0.2 0.5 0.8>")      // varying asymmetry
 * ```
 *
 * @alias tremskew
 * @category effects
 * @tags tremoloskew, tremskew, tremolo, skew, asymmetry
 */
@StrudelDsl
fun tremoloskew(amount: PatternLike): StrudelPattern = _tremoloskew(listOf(amount).asStrudelDslArgs())

/** Sets the tremolo LFO skew for this pattern. */
@StrudelDsl
fun StrudelPattern.tremoloskew(amount: PatternLike): StrudelPattern =
    this._tremoloskew(listOf(amount).asStrudelDslArgs())

/** Parses this string as a pattern and sets the tremolo LFO skew. */
@StrudelDsl
fun String.tremoloskew(amount: PatternLike): StrudelPattern = this._tremoloskew(listOf(amount).asStrudelDslArgs())

/**
 * Alias for [tremoloskew]. Sets the tremolo LFO skew (asymmetry) value.
 *
 * ```KlangScript
 * note("c3*4").tremolosync(2).tremskew(0.8)   // skewed toward peak
 * ```
 *
 * ```KlangScript
 * note("c3*4").tremskew("<0.2 0.5 0.8>")      // varying asymmetry
 * ```
 *
 * @alias tremoloskew
 * @category effects
 * @tags tremskew, tremoloskew, tremolo, skew, asymmetry
 */
@StrudelDsl
fun tremskew(amount: PatternLike): StrudelPattern = _tremskew(listOf(amount).asStrudelDslArgs())

/** Alias for [tremoloskew]. Sets the tremolo LFO skew for this pattern. */
@StrudelDsl
fun StrudelPattern.tremskew(amount: PatternLike): StrudelPattern = this._tremskew(listOf(amount).asStrudelDslArgs())

/** Alias for [tremoloskew]. Parses this string as a pattern and sets the tremolo LFO skew. */
@StrudelDsl
fun String.tremskew(amount: PatternLike): StrudelPattern = this._tremskew(listOf(amount).asStrudelDslArgs())

// -- tremolophase() / tremphase() -------------------------------------------------------------------------------------

private val tremoloPhaseMutation = voiceModifier { copy(tremoloPhase = it?.asDoubleOrNull()) }

fun applyTremoloPhase(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, tremoloPhaseMutation)
}

internal val _tremolophase by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(tremoloPhaseMutation) }
internal val StrudelPattern._tremolophase by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyTremoloPhase(p, args)
}
internal val String._tremolophase by dslStringExtension { p, args, callInfo -> p._tremolophase(args, callInfo) }

internal val _tremphase by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(tremoloPhaseMutation) }
internal val StrudelPattern._tremphase by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyTremoloPhase(p, args)
}
internal val String._tremphase by dslStringExtension { p, args, callInfo -> p._tremphase(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the tremolo LFO starting phase in radians.
 *
 * Controls where in its cycle the tremolo LFO begins. Use to offset the tremolo
 * relative to the beat or other patterns playing simultaneously.
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
 * @alias tremphase
 * @category effects
 * @tags tremolophase, tremphase, tremolo, phase, offset
 */
@StrudelDsl
fun tremolophase(phase: PatternLike): StrudelPattern = _tremolophase(listOf(phase).asStrudelDslArgs())

/** Sets the tremolo LFO starting phase for this pattern. */
@StrudelDsl
fun StrudelPattern.tremolophase(phase: PatternLike): StrudelPattern =
    this._tremolophase(listOf(phase).asStrudelDslArgs())

/** Parses this string as a pattern and sets the tremolo LFO starting phase. */
@StrudelDsl
fun String.tremolophase(phase: PatternLike): StrudelPattern = this._tremolophase(listOf(phase).asStrudelDslArgs())

/**
 * Alias for [tremolophase]. Sets the tremolo LFO starting phase in radians.
 *
 * ```KlangScript
 * note("c3 e3").tremolosync(2).tremphase(1.57)   // start at 90°
 * ```
 *
 * ```KlangScript
 * note("c3*4").tremphase("<0 1.57 3.14 4.71>")   // quarter-turn offsets
 * ```
 *
 * @alias tremolophase
 * @category effects
 * @tags tremphase, tremolophase, tremolo, phase, offset
 */
@StrudelDsl
fun tremphase(phase: PatternLike): StrudelPattern = _tremphase(listOf(phase).asStrudelDslArgs())

/** Alias for [tremolophase]. Sets the tremolo LFO starting phase for this pattern. */
@StrudelDsl
fun StrudelPattern.tremphase(phase: PatternLike): StrudelPattern = this._tremphase(listOf(phase).asStrudelDslArgs())

/** Alias for [tremolophase]. Parses this string as a pattern and sets the tremolo LFO starting phase. */
@StrudelDsl
fun String.tremphase(phase: PatternLike): StrudelPattern = this._tremphase(listOf(phase).asStrudelDslArgs())

// -- tremoloshape() / tremshape() -------------------------------------------------------------------------------------

private val tremoloShapeMutation = voiceModifier { shape -> copy(tremoloShape = shape?.toString()?.lowercase()) }

fun applyTremoloShape(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._applyControlFromParams(args, tremoloShapeMutation) { src, ctrl ->
        src.copy(tremoloShape = ctrl.tremoloShape)
    }
}

internal val _tremoloshape by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(tremoloShapeMutation) }
internal val StrudelPattern._tremoloshape by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyTremoloShape(p, args)
}
internal val String._tremoloshape by dslStringExtension { p, args, callInfo -> p._tremoloshape(args, callInfo) }

internal val _tremshape by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(tremoloShapeMutation) }
internal val StrudelPattern._tremshape by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyTremoloShape(p, args)
}
internal val String._tremshape by dslStringExtension { p, args, callInfo -> p._tremshape(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the tremolo LFO waveform shape.
 *
 * Accepted values: `"sine"`, `"triangle"`, `"square"`, `"sawtooth"`, `"rampup"`, `"rampdown"`.
 * Different shapes produce different tremolo characters — sine is smooth, square is choppy.
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
fun tremoloshape(shape: PatternLike): StrudelPattern = _tremoloshape(listOf(shape).asStrudelDslArgs())

/** Sets the tremolo LFO waveform shape for this pattern. */
@StrudelDsl
fun StrudelPattern.tremoloshape(shape: PatternLike): StrudelPattern =
    this._tremoloshape(listOf(shape).asStrudelDslArgs())

/** Parses this string as a pattern and sets the tremolo LFO waveform shape. */
@StrudelDsl
fun String.tremoloshape(shape: PatternLike): StrudelPattern = this._tremoloshape(listOf(shape).asStrudelDslArgs())

/**
 * Alias for [tremoloshape]. Sets the tremolo LFO waveform shape.
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
fun tremshape(shape: PatternLike): StrudelPattern = _tremshape(listOf(shape).asStrudelDslArgs())

/** Alias for [tremoloshape]. Sets the tremolo LFO waveform shape for this pattern. */
@StrudelDsl
fun StrudelPattern.tremshape(shape: PatternLike): StrudelPattern = this._tremshape(listOf(shape).asStrudelDslArgs())

/** Alias for [tremoloshape]. Parses this string as a pattern and sets the tremolo LFO waveform shape. */
@StrudelDsl
fun String.tremshape(shape: PatternLike): StrudelPattern = this._tremshape(listOf(shape).asStrudelDslArgs())
