/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
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
import io.peekandpoke.klang.sprudel.lang.voiceSetter
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
 * note("c3").notchf(200).nfenv(24).nfadsr(0.01, 0.3, 0.5, 0.5)
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
