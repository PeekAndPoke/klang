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

// -- lpadsr() ---------------------------------------------------------------------------------------------------------

private val lpadsrMutation = voiceModifier {
    val parts = it?.toString()?.split(":")
        ?.map { d -> d.trim().toDoubleOrNull() } ?: emptyList()

    copy(
        lpattack = parts.getOrNull(0) ?: lpattack,
        lpdecay = parts.getOrNull(1) ?: lpdecay,
        lpsustain = parts.getOrNull(2) ?: lpsustain,
        lprelease = parts.getOrNull(3) ?: lprelease,
    )
}

private fun applyLpadsr(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._applyControlFromParams(args, lpadsrMutation) { src, ctrl ->
        src.copy(
            lpattack = ctrl.lpattack ?: src.lpattack,
            lpdecay = ctrl.lpdecay ?: src.lpdecay,
            lpsustain = ctrl.lpsustain ?: src.lpsustain,
            lprelease = ctrl.lprelease ?: src.lprelease,
        )
    }
}

internal val _lpadsr by dslPatternMapper { args, callInfo -> { p -> p._lpadsr(args, callInfo) } }
internal val StrudelPattern._lpadsr by dslPatternExtension { p, args, /* callInfo */ _ -> applyLpadsr(p, args) }
internal val String._lpadsr by dslStringExtension { p, args, callInfo -> p._lpadsr(args, callInfo) }
internal val PatternMapperFn._lpadsr by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_lpadsr(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the LPF envelope shape via a colon-separated string `"attack:decay:sustain:release"`.
 *
 * Controls how the low-pass filter cutoff sweeps over time (used with [lpf] and [lpenv]).
 * - **attack**: time in seconds for the filter to open fully
 * - **decay**: time in seconds to fall from peak to sustain level
 * - **sustain**: sustain level (0–1)
 * - **release**: time in seconds for the filter to close after note release
 *
 * ```KlangScript
 * note("c3").lpf(200).lpenv(4000).lpadsr("0.01:0.3:0.5:0.5")
 * ```
 *
 * @param params The ADSR parameters as `"attack:decay:sustain:release"`.
 * @param-tool params StrudelLpAdsrSequenceEditor
 * @return A new pattern with all specified LPF envelope parameters applied.
 * @category effects
 * @tags lpadsr, low pass filter, envelope, adsr, attack, decay, sustain, release, addon
 */
@StrudelDsl
fun StrudelPattern.lpadsr(params: PatternLike? = null): StrudelPattern =
    this._lpadsr(listOfNotNull(params).asStrudelDslArgs())

/**
 * Parses this string as a pattern and sets all LPF envelope parameters.
 *
 * ```KlangScript
 * "c3*4".lpadsr("0.01:0.3:0.5:0.5").note()
 * ```
 *
 * @param params The ADSR parameters as `"attack:decay:sustain:release"`.
 * @return A new pattern with all specified LPF envelope parameters applied.
 * @category effects
 * @tags lpadsr, low pass filter, envelope, adsr, attack, decay, sustain, release, addon
 */
@StrudelDsl
fun String.lpadsr(params: PatternLike? = null): StrudelPattern =
    this._lpadsr(listOfNotNull(params).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets all LPF envelope parameters.
 *
 * ```KlangScript
 * note("c3 e3 g3").apply(lpadsr("0.01:0.3:0.5:0.5"))
 * ```
 *
 * @param params The ADSR parameters as `"attack:decay:sustain:release"`.
 * @return A [PatternMapperFn] that sets LPF envelope parameters.
 * @category effects
 * @tags lpadsr, low pass filter, envelope, adsr, attack, decay, sustain, release, addon
 */
@StrudelDsl
fun lpadsr(params: PatternLike? = null): PatternMapperFn = _lpadsr(listOfNotNull(params).asStrudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets all LPF envelope parameters after the previous mapper.
 *
 * ```KlangScript
 * note("c3 e3").apply(gain(0.8).lpadsr("0.01:0.3:0.5:0.5"))
 * ```
 */
@StrudelDsl
fun PatternMapperFn.lpadsr(params: PatternLike? = null): PatternMapperFn =
    _lpadsr(listOfNotNull(params).asStrudelDslArgs())

// -- hpadsr() ---------------------------------------------------------------------------------------------------------

private val hpadsrMutation = voiceModifier {
    val parts = it?.toString()?.split(":")
        ?.map { d -> d.trim().toDoubleOrNull() } ?: emptyList()

    copy(
        hpattack = parts.getOrNull(0) ?: hpattack,
        hpdecay = parts.getOrNull(1) ?: hpdecay,
        hpsustain = parts.getOrNull(2) ?: hpsustain,
        hprelease = parts.getOrNull(3) ?: hprelease,
    )
}

private fun applyHpadsr(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._applyControlFromParams(args, hpadsrMutation) { src, ctrl ->
        src.copy(
            hpattack = ctrl.hpattack ?: src.hpattack,
            hpdecay = ctrl.hpdecay ?: src.hpdecay,
            hpsustain = ctrl.hpsustain ?: src.hpsustain,
            hprelease = ctrl.hprelease ?: src.hprelease,
        )
    }
}

internal val _hpadsr by dslPatternMapper { args, callInfo -> { p -> p._hpadsr(args, callInfo) } }
internal val StrudelPattern._hpadsr by dslPatternExtension { p, args, /* callInfo */ _ -> applyHpadsr(p, args) }
internal val String._hpadsr by dslStringExtension { p, args, callInfo -> p._hpadsr(args, callInfo) }
internal val PatternMapperFn._hpadsr by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_hpadsr(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the HPF envelope shape via a colon-separated string `"attack:decay:sustain:release"`.
 *
 * Controls how the high-pass filter cutoff sweeps over time (used with [hpf] and [hpenv]).
 * - **attack**: time in seconds for the filter to open fully
 * - **decay**: time in seconds to fall from peak to sustain level
 * - **sustain**: sustain level (0–1)
 * - **release**: time in seconds for the filter to close after note release
 *
 * ```KlangScript
 * note("c3").hpf(200).hpenv(4000).hpadsr("0.01:0.3:0.5:0.5")
 * ```
 *
 * @param params The ADSR parameters as `"attack:decay:sustain:release"`.
 * @param-tool params StrudelHpAdsrSequenceEditor
 * @return A new pattern with all specified HPF envelope parameters applied.
 * @category effects
 * @tags hpadsr, high pass filter, envelope, adsr, attack, decay, sustain, release, addon
 */
@StrudelDsl
fun StrudelPattern.hpadsr(params: PatternLike? = null): StrudelPattern =
    this._hpadsr(listOfNotNull(params).asStrudelDslArgs())

/**
 * Parses this string as a pattern and sets all HPF envelope parameters.
 *
 * ```KlangScript
 * "c3*4".hpadsr("0.01:0.3:0.5:0.5").note()
 * ```
 *
 * @param params The ADSR parameters as `"attack:decay:sustain:release"`.
 * @return A new pattern with all specified HPF envelope parameters applied.
 * @category effects
 * @tags hpadsr, high pass filter, envelope, adsr, attack, decay, sustain, release, addon
 */
@StrudelDsl
fun String.hpadsr(params: PatternLike? = null): StrudelPattern =
    this._hpadsr(listOfNotNull(params).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets all HPF envelope parameters.
 *
 * ```KlangScript
 * note("c3 e3 g3").apply(hpadsr("0.01:0.3:0.5:0.5"))
 * ```
 *
 * @param params The ADSR parameters as `"attack:decay:sustain:release"`.
 * @return A [PatternMapperFn] that sets HPF envelope parameters.
 * @category effects
 * @tags hpadsr, high pass filter, envelope, adsr, attack, decay, sustain, release, addon
 */
@StrudelDsl
fun hpadsr(params: PatternLike? = null): PatternMapperFn = _hpadsr(listOfNotNull(params).asStrudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets all HPF envelope parameters after the previous mapper.
 *
 * ```KlangScript
 * note("c3 e3").apply(gain(0.8).hpadsr("0.01:0.3:0.5:0.5"))
 * ```
 */
@StrudelDsl
fun PatternMapperFn.hpadsr(params: PatternLike? = null): PatternMapperFn =
    _hpadsr(listOfNotNull(params).asStrudelDslArgs())

// -- bpadsr() ---------------------------------------------------------------------------------------------------------

private val bpadsrMutation = voiceModifier {
    val parts = it?.toString()?.split(":")
        ?.map { d -> d.trim().toDoubleOrNull() } ?: emptyList()

    copy(
        bpattack = parts.getOrNull(0) ?: bpattack,
        bpdecay = parts.getOrNull(1) ?: bpdecay,
        bpsustain = parts.getOrNull(2) ?: bpsustain,
        bprelease = parts.getOrNull(3) ?: bprelease,
    )
}

private fun applyBpadsr(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._applyControlFromParams(args, bpadsrMutation) { src, ctrl ->
        src.copy(
            bpattack = ctrl.bpattack ?: src.bpattack,
            bpdecay = ctrl.bpdecay ?: src.bpdecay,
            bpsustain = ctrl.bpsustain ?: src.bpsustain,
            bprelease = ctrl.bprelease ?: src.bprelease,
        )
    }
}

internal val _bpadsr by dslPatternMapper { args, callInfo -> { p -> p._bpadsr(args, callInfo) } }
internal val StrudelPattern._bpadsr by dslPatternExtension { p, args, /* callInfo */ _ -> applyBpadsr(p, args) }
internal val String._bpadsr by dslStringExtension { p, args, callInfo -> p._bpadsr(args, callInfo) }
internal val PatternMapperFn._bpadsr by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_bpadsr(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the BPF envelope shape via a colon-separated string `"attack:decay:sustain:release"`.
 *
 * Controls how the band-pass filter cutoff sweeps over time (used with [bpf] and [bpenv]).
 * - **attack**: time in seconds for the filter to open fully
 * - **decay**: time in seconds to fall from peak to sustain level
 * - **sustain**: sustain level (0–1)
 * - **release**: time in seconds for the filter to close after note release
 *
 * ```KlangScript
 * note("c3").bpf(200).bpenv(4000).bpadsr("0.01:0.3:0.5:0.5")
 * ```
 *
 * @param params The ADSR parameters as `"attack:decay:sustain:release"`.
 * @param-tool params StrudelBpAdsrSequenceEditor
 * @return A new pattern with all specified BPF envelope parameters applied.
 * @category effects
 * @tags bpadsr, band pass filter, envelope, adsr, attack, decay, sustain, release, addon
 */
@StrudelDsl
fun StrudelPattern.bpadsr(params: PatternLike? = null): StrudelPattern =
    this._bpadsr(listOfNotNull(params).asStrudelDslArgs())

/**
 * Parses this string as a pattern and sets all BPF envelope parameters.
 *
 * ```KlangScript
 * "c3*4".bpadsr("0.01:0.3:0.5:0.5").note()
 * ```
 *
 * @param params The ADSR parameters as `"attack:decay:sustain:release"`.
 * @return A new pattern with all specified BPF envelope parameters applied.
 * @category effects
 * @tags bpadsr, band pass filter, envelope, adsr, attack, decay, sustain, release, addon
 */
@StrudelDsl
fun String.bpadsr(params: PatternLike? = null): StrudelPattern =
    this._bpadsr(listOfNotNull(params).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets all BPF envelope parameters.
 *
 * ```KlangScript
 * note("c3 e3 g3").apply(bpadsr("0.01:0.3:0.5:0.5"))
 * ```
 *
 * @param params The ADSR parameters as `"attack:decay:sustain:release"`.
 * @return A [PatternMapperFn] that sets BPF envelope parameters.
 * @category effects
 * @tags bpadsr, band pass filter, envelope, adsr, attack, decay, sustain, release, addon
 */
@StrudelDsl
fun bpadsr(params: PatternLike? = null): PatternMapperFn = _bpadsr(listOfNotNull(params).asStrudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets all BPF envelope parameters after the previous mapper.
 *
 * ```KlangScript
 * note("c3 e3").apply(gain(0.8).bpadsr("0.01:0.3:0.5:0.5"))
 * ```
 */
@StrudelDsl
fun PatternMapperFn.bpadsr(params: PatternLike? = null): PatternMapperFn =
    _bpadsr(listOfNotNull(params).asStrudelDslArgs())

// -- nfadsr() ---------------------------------------------------------------------------------------------------------

private val nfadsrMutation = voiceModifier {
    val parts = it?.toString()?.split(":")
        ?.map { d -> d.trim().toDoubleOrNull() } ?: emptyList()

    copy(
        nfattack = parts.getOrNull(0) ?: nfattack,
        nfdecay = parts.getOrNull(1) ?: nfdecay,
        nfsustain = parts.getOrNull(2) ?: nfsustain,
        nfrelease = parts.getOrNull(3) ?: nfrelease,
    )
}

private fun applyNfadsr(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._applyControlFromParams(args, nfadsrMutation) { src, ctrl ->
        src.copy(
            nfattack = ctrl.nfattack ?: src.nfattack,
            nfdecay = ctrl.nfdecay ?: src.nfdecay,
            nfsustain = ctrl.nfsustain ?: src.nfsustain,
            nfrelease = ctrl.nfrelease ?: src.nfrelease,
        )
    }
}

internal val _nfadsr by dslPatternMapper { args, callInfo -> { p -> p._nfadsr(args, callInfo) } }
internal val StrudelPattern._nfadsr by dslPatternExtension { p, args, /* callInfo */ _ -> applyNfadsr(p, args) }
internal val String._nfadsr by dslStringExtension { p, args, callInfo -> p._nfadsr(args, callInfo) }
internal val PatternMapperFn._nfadsr by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_nfadsr(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the notch filter envelope shape via a colon-separated string `"attack:decay:sustain:release"`.
 *
 * Controls how the notch filter cutoff sweeps over time (used with [nf] and [nfenv]).
 * - **attack**: time in seconds for the filter to open fully
 * - **decay**: time in seconds to fall from peak to sustain level
 * - **sustain**: sustain level (0–1)
 * - **release**: time in seconds for the filter to close after note release
 *
 * ```KlangScript
 * note("c3").nf(200).nfenv(4000).nfadsr("0.01:0.3:0.5:0.5")
 * ```
 *
 * @param params The ADSR parameters as `"attack:decay:sustain:release"`.
 * @param-tool params StrudelNfAdsrSequenceEditor
 * @return A new pattern with all specified notch filter envelope parameters applied.
 * @category effects
 * @tags nfadsr, notch filter, envelope, adsr, attack, decay, sustain, release, addon
 */
@StrudelDsl
fun StrudelPattern.nfadsr(params: PatternLike? = null): StrudelPattern =
    this._nfadsr(listOfNotNull(params).asStrudelDslArgs())

/**
 * Parses this string as a pattern and sets all notch filter envelope parameters.
 *
 * ```KlangScript
 * "c3*4".nfadsr("0.01:0.3:0.5:0.5").note()
 * ```
 *
 * @param params The ADSR parameters as `"attack:decay:sustain:release"`.
 * @return A new pattern with all specified notch filter envelope parameters applied.
 * @category effects
 * @tags nfadsr, notch filter, envelope, adsr, attack, decay, sustain, release, addon
 */
@StrudelDsl
fun String.nfadsr(params: PatternLike? = null): StrudelPattern =
    this._nfadsr(listOfNotNull(params).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets all notch filter envelope parameters.
 *
 * ```KlangScript
 * note("c3 e3 g3").apply(nfadsr("0.01:0.3:0.5:0.5"))
 * ```
 *
 * @param params The ADSR parameters as `"attack:decay:sustain:release"`.
 * @return A [PatternMapperFn] that sets notch filter envelope parameters.
 * @category effects
 * @tags nfadsr, notch filter, envelope, adsr, attack, decay, sustain, release, addon
 */
@StrudelDsl
fun nfadsr(params: PatternLike? = null): PatternMapperFn = _nfadsr(listOfNotNull(params).asStrudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets all notch filter envelope parameters after the previous mapper.
 *
 * ```KlangScript
 * note("c3 e3").apply(gain(0.8).nfadsr("0.01:0.3:0.5:0.5"))
 * ```
 */
@StrudelDsl
fun PatternMapperFn.nfadsr(params: PatternLike? = null): PatternMapperFn =
    _nfadsr(listOfNotNull(params).asStrudelDslArgs())