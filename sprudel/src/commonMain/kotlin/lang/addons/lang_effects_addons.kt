@file:Suppress("DuplicatedCode", "ObjectPropertyName")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang.addons

import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel._applyControlFromParams
import io.peekandpoke.klang.sprudel.lang.PatternLike
import io.peekandpoke.klang.sprudel.lang.PatternMapperFn
import io.peekandpoke.klang.sprudel.lang.SprudelDsl
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.lang.chain
import io.peekandpoke.klang.sprudel.lang.toVoiceValuePattern
import io.peekandpoke.klang.sprudel.lang.voiceModifier

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in SprudelRegistry.
 */
var sprudelLangEffectsAddonsInit = false

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

private fun applyReverb(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
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
 * ```KlangScript(Playable)
 * note("c3 e3 g3").reverb("0.8:2")   // room=0.8, size=2
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").reverb("0.5:4:0.5:8000:6000")   // all five reverb params
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").reverb("<0.3:1 0.8:4>")   // alternating reverb per cycle
 * ```
 *
 * @param params The reverb parameters as a colon-separated string `"room:size:fade:lowpass:dim"`.
 * @param-tool params SprudelReverbSequenceEditor
 * @param-sub params room Wet/dry mix (0 = fully dry, 1 = fully wet)
 * @param-sub params size Room size — larger values produce longer reverb tails
 * @param-sub params fade Reverb tail fade time in seconds
 * @param-sub params lowpass Lowpass filter frequency on reverb output in Hz
 * @param-sub params dim High-frequency damping frequency in Hz
 * @return A new pattern with all specified reverb parameters applied.
 * @category effects
 * @tags reverb, room, roomsize, roomfade, roomlp, roomdim, addon
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.reverb(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyReverb(this, listOfNotNull(params).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets all reverb parameters.
 *
 * ```KlangScript(Playable)
 * "c3*4".reverb("0.5:2:0.3").note()   // reverb on string pattern
 * ```
 *
 * @param params The reverb parameters as a colon-separated string `"room:size:fade:lowpass:dim"`.
 * @return A new pattern with all specified reverb parameters applied.
 * @category effects
 * @tags reverb, room, roomsize, roomfade, roomlp, roomdim, addon
 */
@SprudelDsl
@KlangScript.Function
fun String.reverb(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).reverb(params, callInfo)

/**
 * Returns a [PatternMapperFn] that sets all reverb parameters.
 *
 * Use the returned mapper as a transform argument or apply it to a pattern via `.apply(...)`.
 *
 * @param params The reverb parameters as a colon-separated string `"room:size:fade:lowpass:dim"`.
 * @return A [PatternMapperFn] that sets reverb parameters.
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").apply(reverb("0.5:2"))   // reverb via mapper
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, reverb("0.9:8"))   // heavy reverb every 4th cycle
 * ```
 *
 * @category effects
 * @tags reverb, room, roomsize, roomfade, roomlp, roomdim, addon
 */
@SprudelDsl
@KlangScript.Function
fun reverb(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.reverb(params, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets all reverb parameters after the previous mapper.
 *
 * @param params The reverb parameters as a colon-separated string `"room:size:fade:lowpass:dim"`.
 * @return A new [PatternMapperFn] chaining reverb after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(gain(0.8).reverb("0.5:2"))   // gain then reverb
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, delay(0.5).reverb("0.9:4"))   // delay + reverb every 4th cycle
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.reverb(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.reverb(params, callInfo) }

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

private fun applyLpadsr(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._applyControlFromParams(args, lpadsrMutation) { src, ctrl ->
        src.copy(
            lpattack = ctrl.lpattack ?: src.lpattack,
            lpdecay = ctrl.lpdecay ?: src.lpdecay,
            lpsustain = ctrl.lpsustain ?: src.lpsustain,
            lprelease = ctrl.lprelease ?: src.lprelease,
        )
    }
}

/**
 * Sets the LPF envelope shape via a colon-separated string `"attack:decay:sustain:release"`.
 *
 * Controls how the low-pass filter cutoff sweeps over time (used with [lpf] and [lpenv]).
 * - **attack**: time in seconds for the filter to open fully
 * - **decay**: time in seconds to fall from peak to sustain level
 * - **sustain**: sustain level (0–1)
 * - **release**: time in seconds for the filter to close after note release
 *
 * ```KlangScript(Playable)
 * note("c3").lpf(200).lpenv(4000).lpadsr("0.01:0.3:0.5:0.5")
 * ```
 *
 * @param params The ADSR parameters as `"attack:decay:sustain:release"`.
 * @param-tool params SprudelLpAdsrSequenceEditor
 * @return A new pattern with all specified LPF envelope parameters applied.
 * @category effects
 * @tags lpadsr, low pass filter, envelope, adsr, attack, decay, sustain, release, addon
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.lpadsr(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyLpadsr(this, listOfNotNull(params).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets all LPF envelope parameters.
 *
 * ```KlangScript(Playable)
 * "c3*4".lpadsr("0.01:0.3:0.5:0.5").note()
 * ```
 *
 * @param params The ADSR parameters as `"attack:decay:sustain:release"`.
 * @return A new pattern with all specified LPF envelope parameters applied.
 * @category effects
 * @tags lpadsr, low pass filter, envelope, adsr, attack, decay, sustain, release, addon
 */
@SprudelDsl
@KlangScript.Function
fun String.lpadsr(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).lpadsr(params, callInfo)

/**
 * Returns a [PatternMapperFn] that sets all LPF envelope parameters.
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").apply(lpadsr("0.01:0.3:0.5:0.5"))
 * ```
 *
 * @param params The ADSR parameters as `"attack:decay:sustain:release"`.
 * @return A [PatternMapperFn] that sets LPF envelope parameters.
 * @category effects
 * @tags lpadsr, low pass filter, envelope, adsr, attack, decay, sustain, release, addon
 */
@SprudelDsl
@KlangScript.Function
fun lpadsr(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.lpadsr(params, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets all LPF envelope parameters after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(gain(0.8).lpadsr("0.01:0.3:0.5:0.5"))
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.lpadsr(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.lpadsr(params, callInfo) }

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

private fun applyHpadsr(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._applyControlFromParams(args, hpadsrMutation) { src, ctrl ->
        src.copy(
            hpattack = ctrl.hpattack ?: src.hpattack,
            hpdecay = ctrl.hpdecay ?: src.hpdecay,
            hpsustain = ctrl.hpsustain ?: src.hpsustain,
            hprelease = ctrl.hprelease ?: src.hprelease,
        )
    }
}

/**
 * Sets the HPF envelope shape via a colon-separated string `"attack:decay:sustain:release"`.
 *
 * Controls how the high-pass filter cutoff sweeps over time (used with [hpf] and [hpenv]).
 * - **attack**: time in seconds for the filter to open fully
 * - **decay**: time in seconds to fall from peak to sustain level
 * - **sustain**: sustain level (0–1)
 * - **release**: time in seconds for the filter to close after note release
 *
 * ```KlangScript(Playable)
 * note("c3").hpf(200).hpenv(4000).hpadsr("0.01:0.3:0.5:0.5")
 * ```
 *
 * @param params The ADSR parameters as `"attack:decay:sustain:release"`.
 * @param-tool params SprudelHpAdsrSequenceEditor
 * @return A new pattern with all specified HPF envelope parameters applied.
 * @category effects
 * @tags hpadsr, high pass filter, envelope, adsr, attack, decay, sustain, release, addon
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.hpadsr(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyHpadsr(this, listOfNotNull(params).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets all HPF envelope parameters.
 *
 * ```KlangScript(Playable)
 * "c3*4".hpadsr("0.01:0.3:0.5:0.5").note()
 * ```
 *
 * @param params The ADSR parameters as `"attack:decay:sustain:release"`.
 * @return A new pattern with all specified HPF envelope parameters applied.
 * @category effects
 * @tags hpadsr, high pass filter, envelope, adsr, attack, decay, sustain, release, addon
 */
@SprudelDsl
@KlangScript.Function
fun String.hpadsr(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).hpadsr(params, callInfo)

/**
 * Returns a [PatternMapperFn] that sets all HPF envelope parameters.
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").apply(hpadsr("0.01:0.3:0.5:0.5"))
 * ```
 *
 * @param params The ADSR parameters as `"attack:decay:sustain:release"`.
 * @return A [PatternMapperFn] that sets HPF envelope parameters.
 * @category effects
 * @tags hpadsr, high pass filter, envelope, adsr, attack, decay, sustain, release, addon
 */
@SprudelDsl
@KlangScript.Function
fun hpadsr(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.hpadsr(params, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets all HPF envelope parameters after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(gain(0.8).hpadsr("0.01:0.3:0.5:0.5"))
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.hpadsr(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.hpadsr(params, callInfo) }

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

private fun applyBpadsr(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._applyControlFromParams(args, bpadsrMutation) { src, ctrl ->
        src.copy(
            bpattack = ctrl.bpattack ?: src.bpattack,
            bpdecay = ctrl.bpdecay ?: src.bpdecay,
            bpsustain = ctrl.bpsustain ?: src.bpsustain,
            bprelease = ctrl.bprelease ?: src.bprelease,
        )
    }
}

/**
 * Sets the BPF envelope shape via a colon-separated string `"attack:decay:sustain:release"`.
 *
 * Controls how the band-pass filter cutoff sweeps over time (used with [bpf] and [bpenv]).
 * - **attack**: time in seconds for the filter to open fully
 * - **decay**: time in seconds to fall from peak to sustain level
 * - **sustain**: sustain level (0–1)
 * - **release**: time in seconds for the filter to close after note release
 *
 * ```KlangScript(Playable)
 * note("c3").bpf(200).bpenv(4000).bpadsr("0.01:0.3:0.5:0.5")
 * ```
 *
 * @param params The ADSR parameters as `"attack:decay:sustain:release"`.
 * @param-tool params SprudelBpAdsrSequenceEditor
 * @return A new pattern with all specified BPF envelope parameters applied.
 * @category effects
 * @tags bpadsr, band pass filter, envelope, adsr, attack, decay, sustain, release, addon
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.bpadsr(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyBpadsr(this, listOfNotNull(params).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets all BPF envelope parameters.
 *
 * ```KlangScript(Playable)
 * "c3*4".bpadsr("0.01:0.3:0.5:0.5").note()
 * ```
 *
 * @param params The ADSR parameters as `"attack:decay:sustain:release"`.
 * @return A new pattern with all specified BPF envelope parameters applied.
 * @category effects
 * @tags bpadsr, band pass filter, envelope, adsr, attack, decay, sustain, release, addon
 */
@SprudelDsl
@KlangScript.Function
fun String.bpadsr(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).bpadsr(params, callInfo)

/**
 * Returns a [PatternMapperFn] that sets all BPF envelope parameters.
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").apply(bpadsr("0.01:0.3:0.5:0.5"))
 * ```
 *
 * @param params The ADSR parameters as `"attack:decay:sustain:release"`.
 * @return A [PatternMapperFn] that sets BPF envelope parameters.
 * @category effects
 * @tags bpadsr, band pass filter, envelope, adsr, attack, decay, sustain, release, addon
 */
@SprudelDsl
@KlangScript.Function
fun bpadsr(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.bpadsr(params, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets all BPF envelope parameters after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(gain(0.8).bpadsr("0.01:0.3:0.5:0.5"))
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.bpadsr(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.bpadsr(params, callInfo) }

// -- tremolo() --------------------------------------------------------------------------------------------------------

private val tremoloMutation = voiceModifier {
    val parts = it?.toString()?.split(":") ?: emptyList()

    copy(
        tremoloDepth = parts.getOrNull(0)?.trim()?.toDoubleOrNull() ?: tremoloDepth,
        tremoloSync = parts.getOrNull(1)?.trim()?.toDoubleOrNull() ?: tremoloSync,
        tremoloShape = parts.getOrNull(2)?.trim()?.takeIf { s -> s.isNotEmpty() } ?: tremoloShape,
        tremoloSkew = parts.getOrNull(3)?.trim()?.toDoubleOrNull() ?: tremoloSkew,
        tremoloPhase = parts.getOrNull(4)?.trim()?.toDoubleOrNull() ?: tremoloPhase,
    )
}

private fun applyTremolo(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._applyControlFromParams(args, tremoloMutation) { src, ctrl ->
        src.copy(
            tremoloDepth = ctrl.tremoloDepth ?: src.tremoloDepth,
            tremoloSync = ctrl.tremoloSync ?: src.tremoloSync,
            tremoloShape = ctrl.tremoloShape ?: src.tremoloShape,
            tremoloSkew = ctrl.tremoloSkew ?: src.tremoloSkew,
            tremoloPhase = ctrl.tremoloPhase ?: src.tremoloPhase,
        )
    }
}

/**
 * Sets all tremolo parameters at once via a colon-separated string
 * `"depth:rate:shape:skew:phase"`.
 *
 * Each field is optional — trailing fields can be omitted.
 * - **depth**: modulation intensity (0–1)
 * - **rate**: LFO speed in cycles per pattern cycle
 * - **shape**: LFO waveform (`sine`, `triangle`, `square`, `saw`)
 * - **skew**: waveform skew (0–1)
 * - **phase**: LFO start phase offset in cycles
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").tremolo("0.5:4")   // depth=0.5, rate=4
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").tremolo("0.8:8:square")   // choppy tremolo
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").tremolo("<0.3:2 0.8:8>")   // alternating tremolo per cycle
 * ```
 *
 * @param params The tremolo parameters as a colon-separated string `"depth:rate:shape:skew:phase"`.
 * @param-tool params SprudelTremoloSequenceEditor
 * @param-sub params depth Modulation intensity (0 = no effect, 1 = full tremolo)
 * @param-sub params rate LFO speed in cycles per pattern cycle
 * @param-sub params shape LFO waveform: sine, triangle, square, saw
 * @param-sub params skew Waveform skew (0–1)
 * @param-sub params phase LFO start phase offset in cycles
 * @return A new pattern with all specified tremolo parameters applied.
 * @category effects
 * @tags tremolo, depth, rate, shape, skew, phase, modulation, addon
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.tremolo(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyTremolo(this, listOfNotNull(params).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets all tremolo parameters.
 *
 * ```KlangScript(Playable)
 * "c3*4".tremolo("0.5:4:sine").note()   // tremolo on string pattern
 * ```
 *
 * @param params The tremolo parameters as a colon-separated string `"depth:rate:shape:skew:phase"`.
 * @return A new pattern with all specified tremolo parameters applied.
 * @category effects
 * @tags tremolo, depth, rate, shape, skew, phase, modulation, addon
 */
@SprudelDsl
@KlangScript.Function
fun String.tremolo(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).tremolo(params, callInfo)

/**
 * Returns a [PatternMapperFn] that sets all tremolo parameters.
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").apply(tremolo("0.5:4"))   // tremolo via mapper
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, tremolo("0.8:8:square"))   // choppy tremolo every 4th cycle
 * ```
 *
 * @param params The tremolo parameters as a colon-separated string `"depth:rate:shape:skew:phase"`.
 * @return A [PatternMapperFn] that sets tremolo parameters.
 * @category effects
 * @tags tremolo, depth, rate, shape, skew, phase, modulation, addon
 */
@SprudelDsl
@KlangScript.Function
fun tremolo(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.tremolo(params, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets all tremolo parameters after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(gain(0.8).tremolo("0.5:4"))   // gain then tremolo
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, delay(0.5).tremolo("0.8:8"))   // delay + tremolo every 4th cycle
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.tremolo(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.tremolo(params, callInfo) }

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

private fun applyNfadsr(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._applyControlFromParams(args, nfadsrMutation) { src, ctrl ->
        src.copy(
            nfattack = ctrl.nfattack ?: src.nfattack,
            nfdecay = ctrl.nfdecay ?: src.nfdecay,
            nfsustain = ctrl.nfsustain ?: src.nfsustain,
            nfrelease = ctrl.nfrelease ?: src.nfrelease,
        )
    }
}

/**
 * Sets the notch filter envelope shape via a colon-separated string `"attack:decay:sustain:release"`.
 *
 * Controls how the notch filter cutoff sweeps over time (used with [nf] and [nfenv]).
 * - **attack**: time in seconds for the filter to open fully
 * - **decay**: time in seconds to fall from peak to sustain level
 * - **sustain**: sustain level (0–1)
 * - **release**: time in seconds for the filter to close after note release
 *
 * ```KlangScript(Playable)
 * note("c3").nf(200).nfenv(4000).nfadsr("0.01:0.3:0.5:0.5")
 * ```
 *
 * @param params The ADSR parameters as `"attack:decay:sustain:release"`.
 * @param-tool params SprudelNfAdsrSequenceEditor
 * @return A new pattern with all specified notch filter envelope parameters applied.
 * @category effects
 * @tags nfadsr, notch filter, envelope, adsr, attack, decay, sustain, release, addon
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.nfadsr(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyNfadsr(this, listOfNotNull(params).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets all notch filter envelope parameters.
 *
 * ```KlangScript(Playable)
 * "c3*4".nfadsr("0.01:0.3:0.5:0.5").note()
 * ```
 *
 * @param params The ADSR parameters as `"attack:decay:sustain:release"`.
 * @return A new pattern with all specified notch filter envelope parameters applied.
 * @category effects
 * @tags nfadsr, notch filter, envelope, adsr, attack, decay, sustain, release, addon
 */
@SprudelDsl
@KlangScript.Function
fun String.nfadsr(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).nfadsr(params, callInfo)

/**
 * Returns a [PatternMapperFn] that sets all notch filter envelope parameters.
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").apply(nfadsr("0.01:0.3:0.5:0.5"))
 * ```
 *
 * @param params The ADSR parameters as `"attack:decay:sustain:release"`.
 * @return A [PatternMapperFn] that sets notch filter envelope parameters.
 * @category effects
 * @tags nfadsr, notch filter, envelope, adsr, attack, decay, sustain, release, addon
 */
@SprudelDsl
@KlangScript.Function
fun nfadsr(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.nfadsr(params, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets all notch filter envelope parameters after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(gain(0.8).nfadsr("0.01:0.3:0.5:0.5"))
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.nfadsr(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.nfadsr(params, callInfo) }
