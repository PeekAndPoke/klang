@file:Suppress("DuplicatedCode", "ObjectPropertyName")

package io.peekandpoke.klang.strudel.lang.addons

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel._applyControlFromParams
import io.peekandpoke.klang.strudel.lang.*
import io.peekandpoke.klang.strudel.lang.StrudelDslArg.Companion.asStrudelDslArgs

/**
 * ADDONS: Effect functions that are NOT available in the original strudel impl
 */

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in StrudelRegistry.
 */
var strudelLangEffectsAddonsInit = false

// -- reverb() ---------------------------------------------------------------------------------------------------------

private val reverbMutation = voiceModifier {
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

private fun applyReverb(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._applyControlFromParams(args, reverbMutation) { src, ctrl ->
        src.copy(
            room = ctrl.room ?: src.room,
            roomSize = ctrl.roomSize ?: src.roomSize,
            roomFade = ctrl.roomFade ?: src.roomFade,
            roomLp = ctrl.roomLp ?: src.roomLp,
            roomDim = ctrl.roomDim ?: src.roomDim,
        )
    }
}

internal val _reverb by dslPatternMapper { args, callInfo -> { p -> p._reverb(args, callInfo) } }
internal val StrudelPattern._reverb by dslPatternExtension { p, args, /* callInfo */ _ -> applyReverb(p, args) }
internal val String._reverb by dslStringExtension { p, args, callInfo -> p._reverb(args, callInfo) }
internal val PatternMapperFn._reverb by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_reverb(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets all reverb parameters at once via a colon-separated string
 * `"room:size:fade:lowpass:dim"`.
 *
 * Each field is optional — trailing fields can be omitted.
 * - **room**: wet/dry mix (0–1)
 * - **size**: room size (larger = longer tail)
 * - **fade**: reverb tail fade time in seconds
 * - **lowpass**: lowpass filter frequency on reverb in Hz
 * - **dim**: high-frequency damping frequency in Hz
 *
 * When [params] is omitted, the pattern's own values are reinterpreted as reverb parameters.
 *
 * ```KlangScript
 * note("c3 e3 g3").reverb("0.8:2")   // room=0.8, size=2
 * ```
 *
 * ```KlangScript
 * note("c3*4").reverb("0.5:4:0.5:8000:6000")   // all five reverb params
 * ```
 *
 * ```KlangScript
 * note("c3*4").reverb("<0.3:1 0.8:4>")   // alternating reverb per cycle
 * ```
 *
 * @param params The reverb parameters as a colon-separated string `"room:size:fade:lowpass:dim"`.
 * @param-tool params StrudelReverbSequenceEditor
 * @return A new pattern with all specified reverb parameters applied.
 * @category effects
 * @tags reverb, room, roomsize, roomfade, roomlp, roomdim, addon
 */
@StrudelDsl
fun StrudelPattern.reverb(params: PatternLike? = null): StrudelPattern =
    this._reverb(listOfNotNull(params).asStrudelDslArgs())

/**
 * Parses this string as a pattern and sets all reverb parameters.
 *
 * ```KlangScript
 * "c3*4".reverb("0.5:2:0.3").note()   // reverb on string pattern
 * ```
 *
 * @param params The reverb parameters as a colon-separated string `"room:size:fade:lowpass:dim"`.
 * @return A new pattern with all specified reverb parameters applied.
 * @category effects
 * @tags reverb, room, roomsize, roomfade, roomlp, roomdim, addon
 */
@StrudelDsl
fun String.reverb(params: PatternLike? = null): StrudelPattern =
    this._reverb(listOfNotNull(params).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets all reverb parameters.
 *
 * Use the returned mapper as a transform argument or apply it to a pattern via `.apply(...)`.
 *
 * @param params The reverb parameters as a colon-separated string `"room:size:fade:lowpass:dim"`.
 * @return A [PatternMapperFn] that sets reverb parameters.
 *
 * ```KlangScript
 * note("c3 e3 g3").apply(reverb("0.5:2"))   // reverb via mapper
 * ```
 *
 * ```KlangScript
 * note("c3*4").every(4, reverb("0.9:8"))   // heavy reverb every 4th cycle
 * ```
 *
 * @category effects
 * @tags reverb, room, roomsize, roomfade, roomlp, roomdim, addon
 */
@StrudelDsl
fun reverb(params: PatternLike? = null): PatternMapperFn = _reverb(listOfNotNull(params).asStrudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets all reverb parameters after the previous mapper.
 *
 * @param params The reverb parameters as a colon-separated string `"room:size:fade:lowpass:dim"`.
 * @return A new [PatternMapperFn] chaining reverb after the previous mapper.
 *
 * ```KlangScript
 * note("c3 e3").apply(gain(0.8).reverb("0.5:2"))   // gain then reverb
 * ```
 *
 * ```KlangScript
 * note("c3*4").every(4, delay(0.5).reverb("0.9:4"))   // delay + reverb every 4th cycle
 * ```
 */
@StrudelDsl
fun PatternMapperFn.reverb(params: PatternLike? = null): PatternMapperFn =
    _reverb(listOfNotNull(params).asStrudelDslArgs())
