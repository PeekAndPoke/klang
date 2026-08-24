/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("DuplicatedCode", "ObjectPropertyName")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang.addons

import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel._applyControlFromParams
import io.peekandpoke.klang.sprudel.lang.PatternLike
import io.peekandpoke.klang.sprudel.lang.PatternMapperFn
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.lang.chain
import io.peekandpoke.klang.sprudel.lang.toVoiceValuePattern
import io.peekandpoke.klang.sprudel.lang.roomsize
import io.peekandpoke.klang.sprudel.lang.roomfade
import io.peekandpoke.klang.sprudel.lang.roomlp
import io.peekandpoke.klang.sprudel.lang.roomdim
import io.peekandpoke.klang.sprudel.lang.tremolosync
import io.peekandpoke.klang.sprudel.lang.tremoloshape
import io.peekandpoke.klang.sprudel.lang.tremoloskew
import io.peekandpoke.klang.sprudel.lang.tremolophase
import io.peekandpoke.klang.sprudel.lang.voiceSetter
// -- reverb() ---------------------------------------------------------------------------------------------------------

private val reverbMutation = voiceSetter {
    room = it?.toString()?.toDoubleOrNull() ?: room
}

private fun applyReverb(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._applyControlFromParams(args, reverbMutation) { src, ctrl ->
        src.room = ctrl.room ?: src.room
        src
    }
}

/**
 * Sets the reverb parameters — a convenience compound over the `roomWet`/`roomsize`/... family
 * (alias until C6; see [io.peekandpoke.klang.sprudel.lang.roomWet]). Reverb is a SEND, not a
 * crossfade: the dry signal reaches the mix untouched, and [wet] only scales how much of the
 * voice feeds the orbit's shared reverb return. Each parameter is independent and patternable;
 * omitted parameters keep their previous values.
 *
 * Each field is optional — trailing fields can be omitted.
 * - **wet**: send amount (0–1)
 * - **size**: room size, **~0..10** (larger = longer tail; 3 ≈ 1 s, 5 ≈ 1.4 s, 10 ≈ 12.5 s)
 * - **fade**: tail override, **0..1** (NOT seconds, and NOT the *size* scale) — when present it
 *   wins over *size*, which is then inert
 * - **lowpass**: lowpass filter frequency on reverb in Hz
 * - **dim**: currently unused by the engine
 *
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").reverb(0.8, 2)   // room=0.8, size=2
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").reverb(0.5, 4, 0.5, 8000, 6000)   // all five reverb params
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").reverb("<0.3 0.8>", "<1 4>")   // alternating reverb per cycle
 * ```
 *
 * @param wet The reverb send amount (0–1).
 * @param dim Currently unused by the engine.
 * @param-tool wet SprudelReverbEditor, SprudelReverbSequenceEditor
 * @param size Room size — larger values produce longer reverb tails
 * @param fade Tail override, 0..1 (not seconds). Overrides size.
 * @param lowpass Lowpass filter frequency on reverb output in Hz
 * @return A new pattern with all specified reverb parameters applied.
 * @category effects
 * @tags reverb, room, roomsize, roomfade, roomlp, roomdim, addon
 */
@KlangScript.Function
fun SprudelPattern.reverb(wet: PatternLike? = null, size: PatternLike? = null, fade: PatternLike? = null, lowpass: PatternLike? = null, dim: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern {
    // A tail-only call must not touch wet: reinterpret runs only on a fully bare call.
    var p = if (wet != null || !(size != null || fade != null || lowpass != null || dim != null)) {
        applyReverb(this, listOfNotNull(wet).asSprudelDslArgs(callInfo))
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
 * Parses this string as a pattern and sets all reverb parameters.
 *
 * ```KlangScript(Playable)
 * "c3*4".reverb(0.5, 2, 0.3).note()   // reverb on string pattern
 * ```
 *
 * @param wet The reverb send amount (0–1).
 * @param size Room size (~0–10).
 * @param fade Tail override (0–1), wins over size.
 * @param lowpass Lowpass on the reverb tail in Hz.
 * @param dim Currently unused by the engine.
 * @return A new pattern with all specified reverb parameters applied.
 * @category effects
 * @tags reverb, room, roomsize, roomfade, roomlp, roomdim, addon
 */
@KlangScript.Function
fun String.reverb(wet: PatternLike? = null, size: PatternLike? = null, fade: PatternLike? = null, lowpass: PatternLike? = null, dim: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).reverb(wet, size, fade, lowpass, dim, callInfo)

/**
 * Returns a [PatternMapperFn] that sets all reverb parameters.
 *
 * Use the returned mapper as a transform argument or apply it to a pattern via `.apply(...)`.
 *
 * @param wet The reverb send amount (0–1).
 * @param size Room size (~0–10).
 * @param fade Tail override (0–1), wins over size.
 * @param lowpass Lowpass on the reverb tail in Hz.
 * @param dim Currently unused by the engine.
 * @return A [PatternMapperFn] that sets reverb parameters.
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").apply(reverb(0.5, 2))   // reverb via mapper
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, reverb(0.9, 8))   // heavy reverb every 4th cycle
 * ```
 *
 * @category effects
 * @tags reverb, room, roomsize, roomfade, roomlp, roomdim, addon
 */
@KlangScript.Function
fun reverb(wet: PatternLike? = null, size: PatternLike? = null, fade: PatternLike? = null, lowpass: PatternLike? = null, dim: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.reverb(wet, size, fade, lowpass, dim, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets all reverb parameters after the previous mapper.
 *
 * @param wet The reverb send amount (0–1).
 * @param size Room size (~0–10).
 * @param fade Tail override (0–1), wins over size.
 * @param lowpass Lowpass on the reverb tail in Hz.
 * @param dim Currently unused by the engine.
 * @return A new [PatternMapperFn] chaining reverb after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(gain(0.8).reverb(0.5, 2))   // gain then reverb
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, delayWet(0.5).reverb(0.9, 4))   // delay + reverb every 4th cycle
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.reverb(wet: PatternLike? = null, size: PatternLike? = null, fade: PatternLike? = null, lowpass: PatternLike? = null, dim: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.reverb(wet, size, fade, lowpass, dim, callInfo) }

// -- lpadsr() ---------------------------------------------------------------------------------------------------------

private val lpattackMutation = voiceSetter {
    lpattack = it?.toString()?.toDoubleOrNull() ?: lpattack
}

private val lpdecayMutation = voiceSetter {
    lpdecay = it?.toString()?.toDoubleOrNull() ?: lpdecay
}

private val lpsustainMutation = voiceSetter {
    lpsustain = it?.toString()?.toDoubleOrNull() ?: lpsustain
}

private val lpreleaseMutation = voiceSetter {
    lprelease = it?.toString()?.toDoubleOrNull() ?: lprelease
}

private fun applyLpAttack(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._applyControlFromParams(args, lpattackMutation) { src, ctrl ->
        src.lpattack = ctrl.lpattack ?: src.lpattack
        src
    }
}

private fun applyLpDecay(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._applyControlFromParams(args, lpdecayMutation) { src, ctrl ->
        src.lpdecay = ctrl.lpdecay ?: src.lpdecay
        src
    }
}

private fun applyLpSustain(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._applyControlFromParams(args, lpsustainMutation) { src, ctrl ->
        src.lpsustain = ctrl.lpsustain ?: src.lpsustain
        src
    }
}

private fun applyLpRelease(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._applyControlFromParams(args, lpreleaseMutation) { src, ctrl ->
        src.lprelease = ctrl.lprelease ?: src.lprelease
        src
    }
}

/**
 * Sets the LPF envelope shape. Each stage is an independent, patternable parameter.
 *
 * Controls how the low-pass filter cutoff sweeps over time (used with [ lpf ] and [ lpenv ]).
 * - **attack**: time in seconds for the filter to open fully
 * - **decay**: time in seconds to fall from peak to sustain level
 * - **sustain**: sustain level (0–1)
 * - **release**: time in seconds for the filter to close after note release
 *
 * ```KlangScript(Playable)
 * note("c3").lpf(200).lpe(24).lpadsr(0.01, 0.3, 0.5, 0.5)
 * ```
 *
 * @param attack Attack time in seconds.
 * @param decay Decay time in seconds.
 * @param sustain Sustain level (0–1).
 * @param release Release time in seconds.
 * @param-tool attack SprudelLpAdsrEditor, SprudelLpAdsrSequenceEditor
 * @return A new pattern with all specified LPF envelope parameters applied.
 * @category effects
 * @tags lpadsr, low pass filter, envelope, adsr, attack, decay, sustain, release, addon
 */
@KlangScript.Function
fun SprudelPattern.lpadsr(attack: PatternLike? = null, decay: PatternLike? = null, sustain: PatternLike? = null, release: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern {
    var p = this
    if (attack != null) p = applyLpAttack(p, listOf<Any?>(attack).asSprudelDslArgs(callInfo?.forParam(0)))
    if (decay != null) p = applyLpDecay(p, listOf<Any?>(decay).asSprudelDslArgs(callInfo?.forParam(1)))
    if (sustain != null) p = applyLpSustain(p, listOf<Any?>(sustain).asSprudelDslArgs(callInfo?.forParam(2)))
    if (release != null) p = applyLpRelease(p, listOf<Any?>(release).asSprudelDslArgs(callInfo?.forParam(3)))
    return p
}

/**
 * Parses this string as a pattern and sets all LPF envelope parameters.
 *
 * ```KlangScript(Playable)
 * "c3*4".lpadsr(0.01, 0.3, 0.5, 0.5).note()
 * ```
 *
 * @param attack Attack time in seconds.
 * @param decay Decay time in seconds.
 * @param sustain Sustain level (0–1).
 * @param release Release time in seconds.
 * @return A new pattern with all specified LPF envelope parameters applied.
 * @category effects
 * @tags lpadsr, low pass filter, envelope, adsr, attack, decay, sustain, release, addon
 */
@KlangScript.Function
fun String.lpadsr(attack: PatternLike? = null, decay: PatternLike? = null, sustain: PatternLike? = null, release: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).lpadsr(attack, decay, sustain, release, callInfo)

/**
 * Returns a [PatternMapperFn] that sets all LPF envelope parameters.
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").apply(lpadsr(0.01, 0.3, 0.5, 0.5))
 * ```
 *
 * @param attack Attack time in seconds.
 * @param decay Decay time in seconds.
 * @param sustain Sustain level (0–1).
 * @param release Release time in seconds.
 * @return A [PatternMapperFn] that sets LPF envelope parameters.
 * @category effects
 * @tags lpadsr, low pass filter, envelope, adsr, attack, decay, sustain, release, addon
 */
@KlangScript.Function
fun lpadsr(attack: PatternLike? = null, decay: PatternLike? = null, sustain: PatternLike? = null, release: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.lpadsr(attack, decay, sustain, release, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets all LPF envelope parameters after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(gain(0.8).lpadsr(0.01, 0.3, 0.5, 0.5))
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.lpadsr(attack: PatternLike? = null, decay: PatternLike? = null, sustain: PatternLike? = null, release: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.lpadsr(attack, decay, sustain, release, callInfo) }

// -- hpadsr() ---------------------------------------------------------------------------------------------------------

private val hpattackMutation = voiceSetter {
    hpattack = it?.toString()?.toDoubleOrNull() ?: hpattack
}

private val hpdecayMutation = voiceSetter {
    hpdecay = it?.toString()?.toDoubleOrNull() ?: hpdecay
}

private val hpsustainMutation = voiceSetter {
    hpsustain = it?.toString()?.toDoubleOrNull() ?: hpsustain
}

private val hpreleaseMutation = voiceSetter {
    hprelease = it?.toString()?.toDoubleOrNull() ?: hprelease
}

private fun applyHpAttack(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._applyControlFromParams(args, hpattackMutation) { src, ctrl ->
        src.hpattack = ctrl.hpattack ?: src.hpattack
        src
    }
}

private fun applyHpDecay(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._applyControlFromParams(args, hpdecayMutation) { src, ctrl ->
        src.hpdecay = ctrl.hpdecay ?: src.hpdecay
        src
    }
}

private fun applyHpSustain(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._applyControlFromParams(args, hpsustainMutation) { src, ctrl ->
        src.hpsustain = ctrl.hpsustain ?: src.hpsustain
        src
    }
}

private fun applyHpRelease(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._applyControlFromParams(args, hpreleaseMutation) { src, ctrl ->
        src.hprelease = ctrl.hprelease ?: src.hprelease
        src
    }
}

/**
 * Sets the HPF envelope shape. Each stage is an independent, patternable parameter.
 *
 * Controls how the high-pass filter cutoff sweeps over time (used with [ hpf ] and [ hpenv ]).
 * - **attack**: time in seconds for the filter to open fully
 * - **decay**: time in seconds to fall from peak to sustain level
 * - **sustain**: sustain level (0–1)
 * - **release**: time in seconds for the filter to close after note release
 *
 * ```KlangScript(Playable)
 * note("c3").hpf(200).hpe(24).hpadsr(0.01, 0.3, 0.5, 0.5)
 * ```
 *
 * @param attack Attack time in seconds.
 * @param decay Decay time in seconds.
 * @param sustain Sustain level (0–1).
 * @param release Release time in seconds.
 * @param-tool attack SprudelHpAdsrEditor, SprudelHpAdsrSequenceEditor
 * @return A new pattern with all specified HPF envelope parameters applied.
 * @category effects
 * @tags hpadsr, high pass filter, envelope, adsr, attack, decay, sustain, release, addon
 */
@KlangScript.Function
fun SprudelPattern.hpadsr(attack: PatternLike? = null, decay: PatternLike? = null, sustain: PatternLike? = null, release: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern {
    var p = this
    if (attack != null) p = applyHpAttack(p, listOf<Any?>(attack).asSprudelDslArgs(callInfo?.forParam(0)))
    if (decay != null) p = applyHpDecay(p, listOf<Any?>(decay).asSprudelDslArgs(callInfo?.forParam(1)))
    if (sustain != null) p = applyHpSustain(p, listOf<Any?>(sustain).asSprudelDslArgs(callInfo?.forParam(2)))
    if (release != null) p = applyHpRelease(p, listOf<Any?>(release).asSprudelDslArgs(callInfo?.forParam(3)))
    return p
}

/**
 * Parses this string as a pattern and sets all HPF envelope parameters.
 *
 * ```KlangScript(Playable)
 * "c3*4".hpadsr(0.01, 0.3, 0.5, 0.5).note()
 * ```
 *
 * @param attack Attack time in seconds.
 * @param decay Decay time in seconds.
 * @param sustain Sustain level (0–1).
 * @param release Release time in seconds.
 * @return A new pattern with all specified HPF envelope parameters applied.
 * @category effects
 * @tags hpadsr, high pass filter, envelope, adsr, attack, decay, sustain, release, addon
 */
@KlangScript.Function
fun String.hpadsr(attack: PatternLike? = null, decay: PatternLike? = null, sustain: PatternLike? = null, release: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).hpadsr(attack, decay, sustain, release, callInfo)

/**
 * Returns a [PatternMapperFn] that sets all HPF envelope parameters.
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").apply(hpadsr(0.01, 0.3, 0.5, 0.5))
 * ```
 *
 * @param attack Attack time in seconds.
 * @param decay Decay time in seconds.
 * @param sustain Sustain level (0–1).
 * @param release Release time in seconds.
 * @return A [PatternMapperFn] that sets HPF envelope parameters.
 * @category effects
 * @tags hpadsr, high pass filter, envelope, adsr, attack, decay, sustain, release, addon
 */
@KlangScript.Function
fun hpadsr(attack: PatternLike? = null, decay: PatternLike? = null, sustain: PatternLike? = null, release: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.hpadsr(attack, decay, sustain, release, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets all HPF envelope parameters after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(gain(0.8).hpadsr(0.01, 0.3, 0.5, 0.5))
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.hpadsr(attack: PatternLike? = null, decay: PatternLike? = null, sustain: PatternLike? = null, release: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.hpadsr(attack, decay, sustain, release, callInfo) }

// -- bpadsr() ---------------------------------------------------------------------------------------------------------

private val bpattackMutation = voiceSetter {
    bpattack = it?.toString()?.toDoubleOrNull() ?: bpattack
}

private val bpdecayMutation = voiceSetter {
    bpdecay = it?.toString()?.toDoubleOrNull() ?: bpdecay
}

private val bpsustainMutation = voiceSetter {
    bpsustain = it?.toString()?.toDoubleOrNull() ?: bpsustain
}

private val bpreleaseMutation = voiceSetter {
    bprelease = it?.toString()?.toDoubleOrNull() ?: bprelease
}

private fun applyBpAttack(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._applyControlFromParams(args, bpattackMutation) { src, ctrl ->
        src.bpattack = ctrl.bpattack ?: src.bpattack
        src
    }
}

private fun applyBpDecay(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._applyControlFromParams(args, bpdecayMutation) { src, ctrl ->
        src.bpdecay = ctrl.bpdecay ?: src.bpdecay
        src
    }
}

private fun applyBpSustain(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._applyControlFromParams(args, bpsustainMutation) { src, ctrl ->
        src.bpsustain = ctrl.bpsustain ?: src.bpsustain
        src
    }
}

private fun applyBpRelease(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._applyControlFromParams(args, bpreleaseMutation) { src, ctrl ->
        src.bprelease = ctrl.bprelease ?: src.bprelease
        src
    }
}

/**
 * Sets the BPF envelope shape. Each stage is an independent, patternable parameter.
 *
 * Controls how the band-pass filter cutoff sweeps over time (used with [ bpf ] and [ bpenv ]).
 * - **attack**: time in seconds for the filter to open fully
 * - **decay**: time in seconds to fall from peak to sustain level
 * - **sustain**: sustain level (0–1)
 * - **release**: time in seconds for the filter to close after note release
 *
 * ```KlangScript(Playable)
 * note("c3").bpf(200).bpe(24).bpadsr(0.01, 0.3, 0.5, 0.5)
 * ```
 *
 * @param attack Attack time in seconds.
 * @param decay Decay time in seconds.
 * @param sustain Sustain level (0–1).
 * @param release Release time in seconds.
 * @param-tool attack SprudelBpAdsrEditor, SprudelBpAdsrSequenceEditor
 * @return A new pattern with all specified BPF envelope parameters applied.
 * @category effects
 * @tags bpadsr, band pass filter, envelope, adsr, attack, decay, sustain, release, addon
 */
@KlangScript.Function
fun SprudelPattern.bpadsr(attack: PatternLike? = null, decay: PatternLike? = null, sustain: PatternLike? = null, release: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern {
    var p = this
    if (attack != null) p = applyBpAttack(p, listOf<Any?>(attack).asSprudelDslArgs(callInfo?.forParam(0)))
    if (decay != null) p = applyBpDecay(p, listOf<Any?>(decay).asSprudelDslArgs(callInfo?.forParam(1)))
    if (sustain != null) p = applyBpSustain(p, listOf<Any?>(sustain).asSprudelDslArgs(callInfo?.forParam(2)))
    if (release != null) p = applyBpRelease(p, listOf<Any?>(release).asSprudelDslArgs(callInfo?.forParam(3)))
    return p
}

/**
 * Parses this string as a pattern and sets all BPF envelope parameters.
 *
 * ```KlangScript(Playable)
 * "c3*4".bpadsr(0.01, 0.3, 0.5, 0.5).note()
 * ```
 *
 * @param attack Attack time in seconds.
 * @param decay Decay time in seconds.
 * @param sustain Sustain level (0–1).
 * @param release Release time in seconds.
 * @return A new pattern with all specified BPF envelope parameters applied.
 * @category effects
 * @tags bpadsr, band pass filter, envelope, adsr, attack, decay, sustain, release, addon
 */
@KlangScript.Function
fun String.bpadsr(attack: PatternLike? = null, decay: PatternLike? = null, sustain: PatternLike? = null, release: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).bpadsr(attack, decay, sustain, release, callInfo)

/**
 * Returns a [PatternMapperFn] that sets all BPF envelope parameters.
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").apply(bpadsr(0.01, 0.3, 0.5, 0.5))
 * ```
 *
 * @param attack Attack time in seconds.
 * @param decay Decay time in seconds.
 * @param sustain Sustain level (0–1).
 * @param release Release time in seconds.
 * @return A [PatternMapperFn] that sets BPF envelope parameters.
 * @category effects
 * @tags bpadsr, band pass filter, envelope, adsr, attack, decay, sustain, release, addon
 */
@KlangScript.Function
fun bpadsr(attack: PatternLike? = null, decay: PatternLike? = null, sustain: PatternLike? = null, release: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.bpadsr(attack, decay, sustain, release, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets all BPF envelope parameters after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(gain(0.8).bpadsr(0.01, 0.3, 0.5, 0.5))
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.bpadsr(attack: PatternLike? = null, decay: PatternLike? = null, sustain: PatternLike? = null, release: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.bpadsr(attack, decay, sustain, release, callInfo) }

// -- tremolo() --------------------------------------------------------------------------------------------------------

private val tremoloMutation = voiceSetter {
    tremoloDepth = it?.toString()?.trim()?.toDoubleOrNull() ?: tremoloDepth
}

private fun applyTremolo(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._applyControlFromParams(args, tremoloMutation) { src, ctrl ->
        src.tremoloDepth = ctrl.tremoloDepth ?: src.tremoloDepth
        src
    }
}

/**
 * Sets the tremolo parameters. Each parameter is independent and patternable;
 * omitted parameters keep their previous values.
 *
 * Each field is optional — trailing fields can be omitted.
 * - **depth**: modulation intensity (0–1)
 * - **rate**: LFO speed in cycles per pattern cycle
 * - **shape**: LFO waveform (`sine`, `triangle`, `square`, `saw`)
 * - **skew**: waveform skew (0–1)
 * - **phase**: LFO start phase offset in cycles
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").tremolo(0.5, 4)   // depth=0.5, rate=4
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").tremolo(0.8, 8, "square")   // choppy tremolo
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").tremolo("<0.3 0.8>", "<2 8>")   // alternating tremolo per cycle
 * ```
 *
 * @param sync Rate in cycles per cycle.
 * @param-tool depth SprudelTremoloEditor, SprudelTremoloSequenceEditor
 * @param depth Modulation intensity (0 = no effect, 1 = full tremolo)
 * @param shape LFO waveform: sine, triangle, square, saw
 * @param skew Waveform skew (0–1)
 * @param phase LFO start phase offset in cycles
 * @return A new pattern with all specified tremolo parameters applied.
 * @category effects
 * @tags tremolo, depth, rate, shape, skew, phase, modulation, addon
 */
@KlangScript.Function
fun SprudelPattern.tremolo(depth: PatternLike? = null, sync: PatternLike? = null, shape: PatternLike? = null, skew: PatternLike? = null, phase: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern {
    // A tail-only call must not touch depth: reinterpret runs only on a fully bare call.
    var p = if (depth != null || !(sync != null || shape != null || skew != null || phase != null)) {
        applyTremolo(this, listOfNotNull(depth).asSprudelDslArgs(callInfo))
    } else {
        this
    }
    if (sync != null) p = p.tremolosync(sync, callInfo?.forParam(1))
    if (shape != null) p = p.tremoloshape(shape, callInfo?.forParam(2))
    if (skew != null) p = p.tremoloskew(skew, callInfo?.forParam(3))
    if (phase != null) p = p.tremolophase(phase, callInfo?.forParam(4))
    return p
}

/**
 * Parses this string as a pattern and sets all tremolo parameters.
 *
 * ```KlangScript(Playable)
 * "c3*4".tremolo(0.5, 4, "sine").note()   // tremolo on string pattern
 * ```
 *
 * @param depth Depth (0–1).
 * @param sync Rate in cycles per cycle.
 * @param shape Shape name.
 * @param skew Skew (0–1).
 * @param phase Phase offset in cycles.
 * @return A new pattern with all specified tremolo parameters applied.
 * @category effects
 * @tags tremolo, depth, rate, shape, skew, phase, modulation, addon
 */
@KlangScript.Function
fun String.tremolo(depth: PatternLike? = null, sync: PatternLike? = null, shape: PatternLike? = null, skew: PatternLike? = null, phase: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).tremolo(depth, sync, shape, skew, phase, callInfo)

/**
 * Returns a [PatternMapperFn] that sets all tremolo parameters.
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").apply(tremolo(0.5, 4))   // tremolo via mapper
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, tremolo(0.8, 8, "square"))   // choppy tremolo every 4th cycle
 * ```
 *
 * @param depth Depth (0–1).
 * @param sync Rate in cycles per cycle.
 * @param shape Shape name.
 * @param skew Skew (0–1).
 * @param phase Phase offset in cycles.
 * @return A [PatternMapperFn] that sets tremolo parameters.
 * @category effects
 * @tags tremolo, depth, rate, shape, skew, phase, modulation, addon
 */
@KlangScript.Function
fun tremolo(depth: PatternLike? = null, sync: PatternLike? = null, shape: PatternLike? = null, skew: PatternLike? = null, phase: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.tremolo(depth, sync, shape, skew, phase, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets all tremolo parameters after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(gain(0.8).tremolo(0.5, 4))   // gain then tremolo
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, delayWet(0.5).tremolo(0.8, 8))   // delay + tremolo every 4th cycle
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.tremolo(depth: PatternLike? = null, sync: PatternLike? = null, shape: PatternLike? = null, skew: PatternLike? = null, phase: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.tremolo(depth, sync, shape, skew, phase, callInfo) }

// -- nfadsr() ---------------------------------------------------------------------------------------------------------

private val nfattackMutation = voiceSetter {
    nfattack = it?.toString()?.toDoubleOrNull() ?: nfattack
}

private val nfdecayMutation = voiceSetter {
    nfdecay = it?.toString()?.toDoubleOrNull() ?: nfdecay
}

private val nfsustainMutation = voiceSetter {
    nfsustain = it?.toString()?.toDoubleOrNull() ?: nfsustain
}

private val nfreleaseMutation = voiceSetter {
    nfrelease = it?.toString()?.toDoubleOrNull() ?: nfrelease
}

private fun applyNfAttack(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._applyControlFromParams(args, nfattackMutation) { src, ctrl ->
        src.nfattack = ctrl.nfattack ?: src.nfattack
        src
    }
}

private fun applyNfDecay(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._applyControlFromParams(args, nfdecayMutation) { src, ctrl ->
        src.nfdecay = ctrl.nfdecay ?: src.nfdecay
        src
    }
}

private fun applyNfSustain(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._applyControlFromParams(args, nfsustainMutation) { src, ctrl ->
        src.nfsustain = ctrl.nfsustain ?: src.nfsustain
        src
    }
}

private fun applyNfRelease(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._applyControlFromParams(args, nfreleaseMutation) { src, ctrl ->
        src.nfrelease = ctrl.nfrelease ?: src.nfrelease
        src
    }
}

/**
 * Sets the notch filter envelope shape. Each stage is an independent, patternable parameter.
 *
 * Controls how the notch filter cutoff sweeps over time (used with [ nf ] and [ nfenv ]).
 * - **attack**: time in seconds for the filter to open fully
 * - **decay**: time in seconds to fall from peak to sustain level
 * - **sustain**: sustain level (0–1)
 * - **release**: time in seconds for the filter to close after note release
 *
 * ```KlangScript(Playable)
 * note("c3").nf(200).nfenv(24).nfadsr(0.01, 0.3, 0.5, 0.5)
 * ```
 *
 * @param attack Attack time in seconds.
 * @param decay Decay time in seconds.
 * @param sustain Sustain level (0–1).
 * @param release Release time in seconds.
 * @param-tool attack SprudelNfAdsrEditor, SprudelNfAdsrSequenceEditor
 * @return A new pattern with all specified notch filter envelope parameters applied.
 * @category effects
 * @tags nfadsr, notch filter, envelope, adsr, attack, decay, sustain, release, addon
 */
@KlangScript.Function
fun SprudelPattern.nfadsr(attack: PatternLike? = null, decay: PatternLike? = null, sustain: PatternLike? = null, release: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern {
    var p = this
    if (attack != null) p = applyNfAttack(p, listOf<Any?>(attack).asSprudelDslArgs(callInfo?.forParam(0)))
    if (decay != null) p = applyNfDecay(p, listOf<Any?>(decay).asSprudelDslArgs(callInfo?.forParam(1)))
    if (sustain != null) p = applyNfSustain(p, listOf<Any?>(sustain).asSprudelDslArgs(callInfo?.forParam(2)))
    if (release != null) p = applyNfRelease(p, listOf<Any?>(release).asSprudelDslArgs(callInfo?.forParam(3)))
    return p
}

/**
 * Parses this string as a pattern and sets all notch filter envelope parameters.
 *
 * ```KlangScript(Playable)
 * "c3*4".nfadsr(0.01, 0.3, 0.5, 0.5).note()
 * ```
 *
 * @param attack Attack time in seconds.
 * @param decay Decay time in seconds.
 * @param sustain Sustain level (0–1).
 * @param release Release time in seconds.
 * @return A new pattern with all specified notch filter envelope parameters applied.
 * @category effects
 * @tags nfadsr, notch filter, envelope, adsr, attack, decay, sustain, release, addon
 */
@KlangScript.Function
fun String.nfadsr(attack: PatternLike? = null, decay: PatternLike? = null, sustain: PatternLike? = null, release: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).nfadsr(attack, decay, sustain, release, callInfo)

/**
 * Returns a [PatternMapperFn] that sets all notch filter envelope parameters.
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").apply(nfadsr(0.01, 0.3, 0.5, 0.5))
 * ```
 *
 * @param attack Attack time in seconds.
 * @param decay Decay time in seconds.
 * @param sustain Sustain level (0–1).
 * @param release Release time in seconds.
 * @return A [PatternMapperFn] that sets notch filter envelope parameters.
 * @category effects
 * @tags nfadsr, notch filter, envelope, adsr, attack, decay, sustain, release, addon
 */
@KlangScript.Function
fun nfadsr(attack: PatternLike? = null, decay: PatternLike? = null, sustain: PatternLike? = null, release: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.nfadsr(attack, decay, sustain, release, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets all notch filter envelope parameters after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(gain(0.8).nfadsr(0.01, 0.3, 0.5, 0.5))
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.nfadsr(attack: PatternLike? = null, decay: PatternLike? = null, sustain: PatternLike? = null, release: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.nfadsr(attack, decay, sustain, release, callInfo) }
