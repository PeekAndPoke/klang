/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel._applyControlFromParams
import io.peekandpoke.klang.sprudel._liftOrReinterpretNumericalField
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
// -- lpf() -------------------------------------------------------------------------------------------------------------

private val lpfMutation = voiceSetter {
    val str = it?.toString() ?: return@voiceSetter
    cutoff = str.toDoubleOrNull()
}

private fun applyLpf(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, lpfMutation)
}

/**
 * Applies a Low Pass Filter (LPF) with the given cutoff frequency in Hz.
 *
 * Only frequencies below the cutoff pass through. Lower values produce a darker, more
 * muffled sound; higher values let more signal through. Use [lpq] to add emphasis
 * at the cutoff frequency.
 *
 * When [freq] is omitted, the pattern's own numeric values are reinterpreted as cutoff frequencies.
 *
 * @param freq The cutoff frequency in Hz. Omit to reinterpret the pattern's values as cutoff.
 * @param q The filter Q factor (resonance). Omit to leave it unchanged.
 * @param passes The cascade count (C5): `2` = 24 dB/oct, `3` = 36. Omit for a single 12 dB/oct stage.
 * @return A new pattern with LPF applied.
 *
 * ```KlangScript(Playable)
 * s("bd sd hh").lpf(500)             // dark, muffled sound
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4 e4").lpf("<200 2000>")    // alternating cutoff per cycle
 * ```
 *
 * ```KlangScript(Playable)
 * seq("200 500 1000").lpf()          // reinterpret values as cutoff
 * ```
 *
 * @param-tool freq SprudelLpFilterEditor, SprudelLpFilterSequenceEditor
 * @param-tool q SprudelLpResonanceEditor, SprudelLpResonanceSequenceEditor
 * @category effects
 * @tags lpf, cutoff, low pass filter, filter, frequency
 */
@KlangScript.Function
fun SprudelPattern.lpf(freq: PatternLike? = null, q: PatternLike? = null, passes: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern {
    // A tail-only call must not touch freq: reinterpret runs only on a fully bare call.
    var p = if (freq != null || (q == null && passes == null)) {
        applyLpf(this, listOfNotNull(freq).asSprudelDslArgs(callInfo))
    } else {
        this
    }
    if (q != null) {
        p = p.lpq(q, callInfo?.forParam(1))
    }
    if (passes != null) {
        p = p.lpx(passes, callInfo?.forParam(2))
    }
    return p
}

/**
 * Parses this string as a pattern, then applies a Low Pass Filter.
 *
 * When [freq] is omitted, the string pattern's values are reinterpreted as cutoff frequencies.
 *
 * @param freq The cutoff frequency in Hz. Omit to reinterpret the pattern's values as cutoff.
 * @param q The filter Q factor (resonance). Omit to leave it unchanged.
 * @param passes The cascade count (C5): `2` = 24 dB/oct, `3` = 36. Omit for a single 12 dB/oct stage.
 * @return A new pattern with LPF applied.
 *
 * ```KlangScript(Playable)
 * "c4 e4".lpf(500).note()           // LPF on string pattern
 * ```
 *
 * @category effects
 * @tags lpf, cutoff, low pass filter, filter, frequency
 */
@KlangScript.Function
fun String.lpf(freq: PatternLike? = null, q: PatternLike? = null, passes: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).lpf(freq, q, passes, callInfo)

/**
 * Returns a [PatternMapperFn] that applies a Low Pass Filter.
 *
 * Use the returned mapper as a transform argument or apply it to a pattern via `.apply(...)`.
 * When [freq] is omitted, the pattern's own numeric values are reinterpreted as cutoff frequencies.
 *
 * @param freq The cutoff frequency in Hz. Omit to reinterpret the pattern's values as cutoff.
 * @param q The filter Q factor (resonance). Omit to leave it unchanged.
 * @param passes The cascade count (C5): `2` = 24 dB/oct, `3` = 36. Omit for a single 12 dB/oct stage.
 * @return A [PatternMapperFn] that applies LPF.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").apply(lpf(500))                     // apply LPF via mapper
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4*4").firstOf(4, lpf(200).lpq(20))   // resonant LPF on first cycle
 * ```
 *
 * @category effects
 * @tags lpf, cutoff, low pass filter, filter, frequency
 */
@KlangScript.Function
fun lpf(freq: PatternLike? = null, q: PatternLike? = null, passes: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.lpf(freq, q, passes, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that applies a Low Pass Filter after the previous mapper.
 *
 * @param freq The cutoff frequency in Hz. Omit to reinterpret the pattern's values as cutoff.
 * @param q The filter Q factor (resonance). Omit to leave it unchanged.
 * @param passes The cascade count (C5): `2` = 24 dB/oct, `3` = 36. Omit for a single 12 dB/oct stage.
 * @return A new [PatternMapperFn] chaining LPF after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").apply(gain(0.8).lpf(500))           // gain then LPF
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").firstOf(4, lpf(300).lpq(15))   // resonant LPF chain
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.lpf(freq: PatternLike? = null, q: PatternLike? = null, passes: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.lpf(freq, q, passes, callInfo) }

// -- hpf() -------------------------------------------------------------------------------------------------------------

private val hpfMutation = voiceSetter {
    val str = it?.toString() ?: return@voiceSetter
    hcutoff = str.toDoubleOrNull()
}

private fun applyHpf(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, hpfMutation)
}

/**
 * Applies a High Pass Filter (HPF) with the given cutoff frequency in Hz.
 *
 * Only frequencies above the cutoff pass through. Higher values produce a thinner, brighter
 * sound by removing low-frequency content. Use [hpq] to add emphasis at the cutoff.
 *
 * When [freq] is omitted, the pattern's own numeric values are reinterpreted as cutoff frequencies.
 *
 * @param freq The cutoff frequency in Hz. Omit to reinterpret the pattern's values as cutoff.
 * @param q The filter Q factor (resonance). Omit to leave it unchanged.
 * @param passes The cascade count (C5): `2` = 24 dB/oct, `3` = 36. Omit for a single 12 dB/oct stage.
 * @return A new pattern with HPF applied.
 *
 * ```KlangScript(Playable)
 * s("bd sd").hpf(300)              // removes bass, thin sound
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4 e4").hpf("<100 800>")   // alternating HPF cutoff per cycle
 * ```
 *
 * ```KlangScript(Playable)
 * seq("100 300 800").hpf()         // reinterpret values as HPF cutoff
 * ```
 *
 * @param-tool freq SprudelHpFilterEditor, SprudelHpFilterSequenceEditor
 * @param-tool q SprudelHpResonanceEditor, SprudelHpResonanceSequenceEditor
 * @category effects
 * @tags hpf, hcutoff, high pass filter, filter, frequency
 */
@KlangScript.Function
fun SprudelPattern.hpf(freq: PatternLike? = null, q: PatternLike? = null, passes: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern {
    // A tail-only call must not touch freq: reinterpret runs only on a fully bare call.
    var p = if (freq != null || (q == null && passes == null)) {
        applyHpf(this, listOfNotNull(freq).asSprudelDslArgs(callInfo))
    } else {
        this
    }
    if (q != null) {
        p = p.hpq(q, callInfo?.forParam(1))
    }
    if (passes != null) {
        p = p.hpx(passes, callInfo?.forParam(2))
    }
    return p
}

/**
 * Parses this string as a pattern, then applies a High Pass Filter.
 *
 * @param freq The cutoff frequency in Hz. Omit to reinterpret the pattern's values as cutoff.
 * @param q The filter Q factor (resonance). Omit to leave it unchanged.
 * @param passes The cascade count (C5): `2` = 24 dB/oct, `3` = 36. Omit for a single 12 dB/oct stage.
 * @return A new pattern with HPF applied.
 *
 * ```KlangScript(Playable)
 * "c4 e4".hpf(300).note()          // HPF on string pattern
 * ```
 *
 * @category effects
 * @tags hpf, hcutoff, high pass filter, filter, frequency
 */
@KlangScript.Function
fun String.hpf(freq: PatternLike? = null, q: PatternLike? = null, passes: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).hpf(freq, q, passes, callInfo)

/**
 * Returns a [PatternMapperFn] that applies a High Pass Filter.
 *
 * @param freq The cutoff frequency in Hz. Omit to reinterpret the pattern's values as cutoff.
 * @param q The filter Q factor (resonance). Omit to leave it unchanged.
 * @param passes The cascade count (C5): `2` = 24 dB/oct, `3` = 36. Omit for a single 12 dB/oct stage.
 * @return A [PatternMapperFn] that applies HPF.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").apply(hpf(300))                     // apply HPF via mapper
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4*4").firstOf(4, hpf(200).hpq(10))  // resonant HPF on first cycle
 * ```
 *
 * @category effects
 * @tags hpf, hcutoff, high pass filter, filter, frequency
 */
@KlangScript.Function
fun hpf(freq: PatternLike? = null, q: PatternLike? = null, passes: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.hpf(freq, q, passes, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that applies a High Pass Filter after the previous mapper.
 *
 * @param freq The cutoff frequency in Hz. Omit to reinterpret the pattern's values as cutoff.
 * @param q The filter Q factor (resonance). Omit to leave it unchanged.
 * @param passes The cascade count (C5): `2` = 24 dB/oct, `3` = 36. Omit for a single 12 dB/oct stage.
 * @return A new [PatternMapperFn] chaining HPF after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").apply(gain(0.8).hpf(300))           // gain then HPF
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").firstOf(4, hpf(200).hpq(15))  // resonant HPF chain
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.hpf(freq: PatternLike? = null, q: PatternLike? = null, passes: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.hpf(freq, q, passes, callInfo) }

// -- bpf() -------------------------------------------------------------------------------------------------------------

private val bpfMutation = voiceSetter {
    val str = it?.toString() ?: return@voiceSetter
    bandf = str.toDoubleOrNull()
}

private fun applyBpf(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, bpfMutation)
}

/**
 * Applies a Band Pass Filter (BPF) with the given centre frequency in Hz.
 *
 * Only a band of frequencies around the centre frequency passes through. Use [bpq] to
 * control the bandwidth (Q factor); higher Q values create a narrower band.
 *
 * When [freq] is omitted, the pattern's own numeric values are reinterpreted as centre frequencies.
 *
 * @param freq The centre frequency in Hz. Omit to reinterpret the pattern's values as centre frequency.
 * @param q The filter Q factor (bandwidth). Omit to leave it unchanged.
 * @return A new pattern with BPF applied.
 *
 * ```KlangScript(Playable)
 * s("sd").bpf(1000)               // emphasise mid-range around 1 kHz
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4").bpf("<500 2000>")    // alternating centre per cycle
 * ```
 *
 * ```KlangScript(Playable)
 * seq("500 1000 2000").bpf()      // reinterpret values as BPF centre
 * ```
 *
 * @param-tool freq SprudelBpFilterEditor, SprudelBpFilterSequenceEditor
 * @param-tool q SprudelBpQEditor, SprudelBpQSequenceEditor
 * @category effects
 * @tags bandf, bpf, band pass filter, filter, frequency
 */
@KlangScript.Function
fun SprudelPattern.bpf(freq: PatternLike? = null, q: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern {
    // A tail-only call must not touch freq: reinterpret runs only on a fully bare call.
    val withFreq = if (freq != null || q == null) {
        applyBpf(this, listOfNotNull(freq).asSprudelDslArgs(callInfo))
    } else {
        this
    }
    return if (q != null) withFreq.bpq(q, callInfo?.forParam(1)) else withFreq
}

/**
 * Parses this string as a pattern, then applies a Band Pass Filter.
 *
 * @param freq The centre frequency in Hz. Omit to reinterpret the pattern's values as centre frequency.
 * @param q The filter Q factor (bandwidth). Omit to leave it unchanged.
 * @return A new pattern with BPF applied.
 *
 * ```KlangScript(Playable)
 * "c4 e4".bpf(1000).note()        // BPF on string pattern
 * ```
 *
 * @category effects
 * @tags bandf, bpf, band pass filter, filter, frequency
 */
@KlangScript.Function
fun String.bpf(freq: PatternLike? = null, q: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).bpf(freq, q, callInfo)

/**
 * Creates a chained [PatternMapperFn] that applies a Band Pass Filter after the previous mapper.
 *
 * @param freq The centre frequency in Hz. Omit to reinterpret the pattern's values as centre frequency.
 * @param q The filter Q factor (bandwidth). Omit to leave it unchanged.
 * @return A new [PatternMapperFn] chaining BPF after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").apply(gain(0.8).bpf(1000))          // gain then BPF
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").firstOf(4, bpf(800).bpq(8))        // narrow BPF chain
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.bpf(freq: PatternLike? = null, q: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.bpf(freq, q, callInfo) }

/**
 * Returns a [PatternMapperFn] that applies a Band Pass Filter.
 *
 * @param freq The centre frequency in Hz.
 * @param q The filter Q factor (bandwidth). Omit to leave it unchanged.
 * @return A [PatternMapperFn] that applies BPF.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").apply(bpf(1000))    // same as chained form
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").firstOf(4, bpf(800)) // BPF on first cycle
 * ```
 *
 * @category effects
 * @tags bpf, bandf, band pass filter, filter, frequency
 */
@KlangScript.Function
fun bpf(freq: PatternLike? = null, q: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.bpf(freq, q, callInfo) }

// -- lpq() - Low Pass Filter resonance ---------------------------------------------------------------------------------

private val resonanceMutation = voiceSetter { resonance = it?.asDoubleOrNull() }

private fun applyResonance(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, resonanceMutation)
}

/**
 * Sets the resonance (Q factor) of the Low Pass Filter.
 *
 * Resonance adds emphasis (a peak) at the filter's cutoff frequency. Higher values create a
 * more pronounced ringing effect. Use with [lpf] to set the cutoff frequency.
 *
 * When [q] is omitted, the pattern's own numeric values are reinterpreted as Q values.
 *
 * @param q The Q factor. Higher values produce more resonance. Omit to reinterpret pattern values.
 * @return A new pattern with LPF resonance applied.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").lpf(800).lpq(15)    // LPF with high resonance peak
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd").lpf(500).lpq("<0 20>")    // resonance sweeps from flat to peaked
 * ```
 *
 * ```KlangScript(Playable)
 * seq("0 5 10 20").lpq()            // reinterpret values as resonance Q
 * ```
 *
 * @param-tool q SprudelLpResonanceEditor, SprudelLpResonanceSequenceEditor
 * @category effects
 * @tags resonance, res, lpq, low pass filter, Q
 */
@KlangScript.Function
fun SprudelPattern.lpq(q: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyResonance(this, listOfNotNull(q).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern, then sets LPF resonance.
 *
 * @param q The Q factor. Omit to reinterpret the pattern's values as Q.
 * @return A new pattern with LPF resonance applied.
 *
 * ```KlangScript(Playable)
 * "c4 e4".lpf(800).lpq(15)    // resonance on string pattern
 * ```
 *
 * @category effects
 * @tags resonance, res, lpq, low pass filter, Q
 */
@KlangScript.Function
fun String.lpq(q: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).lpq(q, callInfo)

/**
 * Creates a chained [PatternMapperFn] that sets LPF resonance after the previous mapper.
 *
 * @param q The Q factor. Omit to reinterpret the pattern's values as Q.
 * @return A new [PatternMapperFn] chaining resonance after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").apply(lpf(500).lpq(15))        // LPF then resonance
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").firstOf(4, lpf(300).lpq(20))    // resonant LPF chain
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.lpq(q: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.lpq(q, callInfo) }

/**
 * Returns a [PatternMapperFn] that sets LPF resonance.
 *
 * @param q The Q factor.
 * @return A [PatternMapperFn] that applies LPF resonance.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").apply(lpf(500).lpq(10))  // same as chained form
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").firstOf(4, lpq(20))       // high Q on first cycle
 * ```
 *
 * @category effects
 * @tags lpq, resonance, res, low pass filter, Q
 */
@KlangScript.Function
fun lpq(q: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.lpq(q, callInfo) }

// -- hpq() - High Pass Filter resonance --------------------------------------------------------------------------------

private val hresonanceMutation = voiceSetter { hresonance = it?.asDoubleOrNull() }

private fun applyHresonance(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, hresonanceMutation)
}

/**
 * Sets the resonance (Q factor) of the High Pass Filter.
 *
 * Resonance adds emphasis at the HPF's cutoff frequency, creating a peak effect.
 * Higher values make the resonance more pronounced. Use with [hpf] to set the cutoff.
 *
 * When [q] is omitted, the pattern's own numeric values are reinterpreted as Q values.
 *
 * @param q The Q factor. Higher values produce more resonance. Omit to reinterpret pattern values.
 * @return A new pattern with HPF resonance applied.
 *
 * ```KlangScript(Playable)
 * note("c4").hpf(300).hpq(15)        // HPF with strong resonance peak
 * ```
 *
 * ```KlangScript(Playable)
 * s("sd").hpf(200).hpq("<0 20>")     // resonance sweeps per cycle
 * ```
 *
 * ```KlangScript(Playable)
 * seq("0 5 15").hpq()                // reinterpret values as HPF Q
 * ```
 *
 * @param-tool q SprudelHpResonanceEditor, SprudelHpResonanceSequenceEditor
 * @category effects
 * @tags hresonance, hres, hpq, high pass filter, Q, resonance
 */
@KlangScript.Function
fun SprudelPattern.hpq(q: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyHresonance(this, listOfNotNull(q).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern, then sets HPF resonance.
 *
 * @param q The Q factor. Omit to reinterpret the pattern's values as Q.
 * @return A new pattern with HPF resonance applied.
 *
 * ```KlangScript(Playable)
 * "c4".hpf(300).hpq(15)      // resonance on string pattern
 * ```
 *
 * @category effects
 * @tags hresonance, hres, hpq, high pass filter, Q, resonance
 */
@KlangScript.Function
fun String.hpq(q: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).hpq(q, callInfo)

/**
 * Creates a chained [PatternMapperFn] that sets HPF resonance after the previous mapper.
 *
 * @param q The Q factor. Omit to reinterpret the pattern's values as Q.
 * @return A new [PatternMapperFn] chaining HPF resonance after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").apply(hpf(200).hpq(15))       // HPF then resonance
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").firstOf(4, hpf(300).hpq(20))   // resonant HPF chain
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.hpq(q: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.hpq(q, callInfo) }

/**
 * Returns a [PatternMapperFn] that sets HPF resonance.
 *
 * @param q The Q factor.
 * @return A [PatternMapperFn] that applies HPF resonance.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").apply(hpf(300).hpq(10))  // same as chained form
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").firstOf(4, hpq(20))       // high Q on first cycle
 * ```
 *
 * @category effects
 * @tags hpq, hresonance, hres, high pass filter, Q
 */
@KlangScript.Function
fun hpq(q: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.hpq(q, callInfo) }

// -- bpq() - Band Pass Filter resonance --------------------------------------------------------------------------------

private val bandqMutation = voiceSetter { bandq = it?.asDoubleOrNull() }

private fun applyBandq(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, bandqMutation)
}

/**
 * Sets the Q factor (bandwidth) of the Band Pass Filter.
 *
 * Higher Q values create a narrower, more selective frequency band. Lower values let a
 * wider range through. Use with [bpf] to set the centre frequency.
 *
 * When [q] is omitted, the pattern's own numeric values are reinterpreted as Q values.
 *
 * @param q The Q factor. Higher values create a narrower band. Omit to reinterpret pattern values.
 * @return A new pattern with BPF Q applied.
 *
 * ```KlangScript(Playable)
 * note("c4").bpf(1000).bpq(5)         // narrow band pass at 1 kHz
 * ```
 *
 * ```KlangScript(Playable)
 * s("sd").bpf(800).bpq("<1 20>")      // Q sweeps from wide to narrow
 * ```
 *
 * ```KlangScript(Playable)
 * seq("1 5 10 20").bpq()                // reinterpret values as BPF Q
 * ```
 *
 * @param-tool q SprudelBpQEditor, SprudelBpQSequenceEditor
 * @category effects
 * @tags bandq, bpq, band pass filter, Q, bandwidth
 */
@KlangScript.Function
fun SprudelPattern.bpq(q: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyBandq(this, listOfNotNull(q).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern, then sets BPF Q.
 *
 * @param q The Q factor. Omit to reinterpret the pattern's values as Q.
 * @return A new pattern with BPF Q applied.
 *
 * ```KlangScript(Playable)
 * "c4".bpf(800).bpq(5)          // BPF Q on string pattern
 * ```
 *
 * @category effects
 * @tags bandq, bpq, band pass filter, Q
 */
@KlangScript.Function
fun String.bpq(q: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).bpq(q, callInfo)

/**
 * Creates a chained [PatternMapperFn] that sets BPF Q after the previous mapper.
 *
 * @param q The Q factor. Omit to reinterpret the pattern's values as Q.
 * @return A new [PatternMapperFn] chaining BPF Q after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").apply(bpf(800).bpq(5))           // bpf then bpq
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").firstOf(4, bpf(1000).bpq(8))      // narrow BPF chain
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.bpq(q: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.bpq(q, callInfo) }

/**
 * Returns a [PatternMapperFn] that sets BPF Q.
 *
 * @param q The Q factor.
 * @return A [PatternMapperFn] that applies BPF Q.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").apply(bpf(800).bpq(5))  // same as chained form
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").firstOf(4, bpq(10))        // narrow BPF on first cycle
 * ```
 *
 * @category effects
 * @tags bpq, bandq, band pass filter, Q, bandwidth
 */
@KlangScript.Function
fun bpq(q: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.bpq(q, callInfo) }


// -- lpe() - Low Pass Filter Envelope Depth ----------------------------------------------------------------------------

private val lpenvMutation = voiceSetter { lpenv = it?.asDoubleOrNull() }

private fun applyLpenv(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, lpenvMutation)
}

/**
 * Sets the LPF envelope depth (modulation amount).
 *
 * Controls how far above the base [lpf] cutoff the filter sweeps when the ADSR envelope
 * is fully open. The depth is in SEMITONES: the sweep is pitch-linear, the way DAW
 * filter envelopes work: +12 doubles the cutoff at full envelope, -12 halves it, and
 * negative depths are first-class (no dead zone).
 *
 * ```
 * newCutoff = baseCutoff × 2^(semitones/12 × envelopeValue)
 * ```
 *
 * ### How cutoff, ADSR, and depth work together
 *
 * | Component | Role |
 * |-----------|------|
 * | `lpf(freq)` | Sets the **resting** cutoff — where the filter sits with no envelope |
 * | `lpadsr` | Shapes the **envelope curve** over time (0→1→sustain→0) |
 * | `lpe(semitones)` | Scales **how far** the envelope moves the cutoff |
 *
 * Example with `lpf(500).lpe(24).lpadsr(0.01, 0.5, 0.2, 0.3)`:
 *
 * | Phase | envValue | Cutoff |
 * |-------|----------|--------|
 * | Note start | 0.0 | 500 Hz |
 * | Attack peak | 1.0 | 500 × 2^(24/12 × 1.0) = **2000 Hz** (2 octaves up) |
 * | Sustain | 0.2 | 500 × 2^(24/12 × 0.2) = **660 Hz** |
 * | Release end | 0.0 | 500 Hz |
 *
 * ```KlangScript(Playable)
 * s("bd").lpf(200).lpe(24)                 // sweeps up 2 octaves, to 800 Hz at peak
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4").lpf(300).lpe("<7 36>")        // subtle (a fifth) vs dramatic (3 octaves) per cycle
 * ```
 *
 * @param semitones Envelope depth in semitones (+12 = one octave up at full envelope); omit to reinterpret the pattern's own values.
 * @return A [PatternMapperFn] that sets the LPF envelope depth, or [SprudelPattern] when called on a pattern.
 * @param-tool semitones SprudelLpEnvEditor, SprudelLpEnvSequenceEditor
 * @category effects
 * @tags lpenv, lpe, low pass filter, envelope, depth, modulation
 */
@KlangScript.Function
fun SprudelPattern.lpe(semitones: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyLpenv(this, listOfNotNull(semitones).asSprudelDslArgs(callInfo))

/** Sets the LPF envelope depth/amount on a string pattern. */
@KlangScript.Function
fun String.lpe(semitones: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).lpe(semitones, callInfo)

/** Creates a chained [PatternMapperFn] that sets the LPF envelope depth after the previous mapper. */
@KlangScript.Function
fun PatternMapperFn.lpe(semitones: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.lpe(semitones, callInfo) }

/** Creates a [PatternMapperFn] that sets the LPF envelope depth. */
@KlangScript.Function
fun lpe(semitones: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.lpe(semitones, callInfo) }


// -- lpx() -----------------------------------------------------------------------------------------------------------

private val lpxMutation = voiceSetter { lpPasses = it?.asDoubleOrNull() }

private fun applyLpx(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, lpxMutation)
}

/**
 * Sets the lowpass CASCADE count (C5): run the 12 dB/oct filter stage that many times —
 * `2` = 24 dB/oct, `3` = 36. The per-stage q is STAGGERED (Butterworth ladder scaled by
 * `q/0.707`) so at the DEFAULT q the cascade is exactly Butterworth: -3 dB AT the cutoff,
 * `lpf(800, 0.707, 2)` still means 800, no darker-with-a-drifting-knee. A resonant q keeps
 * its character but COMPOUNDS across stages: the gain AT the cutoff is `(q*sqrt(2))^N / sqrt(2)`,
 * so `q = 1.0, passes = 2` sits +3 dB there, and `lpf(800, 10, 4)` peaks near +89 dB. That is
 * the raw engine doing what it was told, not a bug — but it is one digit away from a surprise.
 * `analog` compounds the same way: every stage gets the full drive. Values are rounded and
 * coerced to 1..16 (a resource count, not a tone knob); when omitted, the pattern's own
 * numeric values are reinterpreted as the count.
 *
 * ```KlangScript(Playable)
 * note("c2*4").s("sawtooth").lpf(900, 0.707, 2)   // 24 dB/oct - steeper, same knee
 * ```
 *
 * ```KlangScript(Playable)
 * note("c2*4").s("sawtooth").lpf(900).lpx("<1 2 3>")   // the tail form: slope per cycle
 * ```
 *
 * @param passes The cascade count (1, 2, 3, ...). Omit to reinterpret the pattern's values.
 * @return A new pattern with the lowpass cascade count applied.
 * @category effects
 * @tags lpx, lpf, lowpass, passes, cascade, slope, butterworth
 */
@KlangScript.Function
fun SprudelPattern.lpx(passes: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyLpx(this, listOfNotNull(passes).asSprudelDslArgs(callInfo))

/** Sets the lowpass cascade count on a string pattern (see [SprudelPattern.lpx]). */
@KlangScript.Function
fun String.lpx(passes: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).lpx(passes, callInfo)

/** Returns a [PatternMapperFn] that sets the lowpass cascade count (see [SprudelPattern.lpx]). */
@KlangScript.Function
fun lpx(passes: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.lpx(passes, callInfo) }

/** Chains a lpx step onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.lpx(passes: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.lpx(passes, callInfo) }

// -- hpe() - High Pass Filter Envelope Depth ---------------------------------------------------------------------------

private val hpenvMutation = voiceSetter { hpenv = it?.asDoubleOrNull() }

private fun applyHpenv(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, hpenvMutation)
}

/**
 * Sets the HPF envelope depth (modulation amount).
 *
 * Controls how far above the base [hpf] cutoff the filter sweeps when the ADSR envelope
 * is fully open. The depth is in SEMITONES: the sweep is pitch-linear, the way DAW
 * filter envelopes work: +12 doubles the cutoff at full envelope, -12 halves it, and
 * negative depths are first-class (no dead zone).
 *
 * ```
 * newCutoff = baseCutoff × 2^(semitones/12 × envelopeValue)
 * ```
 *
 * ### How cutoff, ADSR, and depth work together
 *
 * | Component | Role |
 * |-----------|------|
 * | `hpf(freq)` | Sets the **resting** cutoff — where the filter sits with no envelope |
 * | `hpadsr` | Shapes the **envelope curve** over time (0→1→sustain→0) |
 * | `hpe(semitones)` | Scales **how far** the envelope moves the cutoff |
 *
 * Example with `hpf(500).hpe(24).hpadsr(0.01, 0.5, 0.2, 0.3)`:
 *
 * | Phase | envValue | Cutoff |
 * |-------|----------|--------|
 * | Note start | 0.0 | 500 Hz |
 * | Attack peak | 1.0 | 500 × 2^(24/12 × 1.0) = **2000 Hz** (2 octaves up) |
 * | Sustain | 0.2 | 500 × 2^(24/12 × 0.2) = **660 Hz** |
 * | Release end | 0.0 | 500 Hz |
 *
 * ```KlangScript(Playable)
 * s("sd").hpf(100).hpe(24)                // sweeps up to 400 Hz at peak
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4").hpf(200).hpe("<7 36>")        // subtle (a fifth) vs dramatic (3 octaves) per cycle
 * ```
 *
 * @param semitones Envelope depth in semitones (+12 = one octave up at full envelope); omit to reinterpret the pattern's own values.
 * @return A [PatternMapperFn] that sets the HPF envelope depth, or [SprudelPattern] when called on a pattern.
 * @param-tool semitones SprudelHpEnvEditor, SprudelHpEnvSequenceEditor
 * @category effects
 * @tags hpenv, hpe, high pass filter, envelope, depth, modulation
 */
@KlangScript.Function
fun SprudelPattern.hpe(semitones: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyHpenv(this, listOfNotNull(semitones).asSprudelDslArgs(callInfo))

/** Sets the HPF envelope depth/amount on a string pattern. */
@KlangScript.Function
fun String.hpe(semitones: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).hpe(semitones, callInfo)

/** Creates a chained [PatternMapperFn] that sets the HPF envelope depth after the previous mapper. */
@KlangScript.Function
fun PatternMapperFn.hpe(semitones: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.hpe(semitones, callInfo) }

/** Creates a [PatternMapperFn] that sets the HPF envelope depth. */
@KlangScript.Function
fun hpe(semitones: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.hpe(semitones, callInfo) }


// -- hpx() -----------------------------------------------------------------------------------------------------------

private val hpxMutation = voiceSetter { hpPasses = it?.asDoubleOrNull() }

private fun applyHpx(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, hpxMutation)
}

/**
 * Sets the highpass CASCADE count — the highpass twin of [lpx]: `2` = 24 dB/oct with the
 * staggered q ladder keeping -3 dB AT the cutoff at the DEFAULT q (a resonant q compounds,
 * see [lpx]). Coerced to 1..16; when omitted, the pattern's own numeric values are
 * reinterpreted as the count.
 *
 * ```KlangScript(Playable)
 * note("c4*4").s("sawtooth").hpf(400, 0.707, 2)   // 24 dB/oct highpass, same knee
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4*4").s("sawtooth").hpf(400).hpx(2)   // the tail form
 * ```
 *
 * @param passes The cascade count (1, 2, 3, ...). Omit to reinterpret the pattern's values.
 * @return A new pattern with the highpass cascade count applied.
 * @category effects
 * @tags hpx, hpf, highpass, passes, cascade, slope, butterworth
 */
@KlangScript.Function
fun SprudelPattern.hpx(passes: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyHpx(this, listOfNotNull(passes).asSprudelDslArgs(callInfo))

/** Sets the highpass cascade count on a string pattern (see [SprudelPattern.hpx]). */
@KlangScript.Function
fun String.hpx(passes: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).hpx(passes, callInfo)

/** Returns a [PatternMapperFn] that sets the highpass cascade count (see [SprudelPattern.hpx]). */
@KlangScript.Function
fun hpx(passes: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.hpx(passes, callInfo) }

/** Chains a hpx step onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.hpx(passes: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.hpx(passes, callInfo) }

// -- bpe() - Band Pass Filter Envelope Depth ---------------------------------------------------------------------------

private val bpenvMutation = voiceSetter { bpenv = it?.asDoubleOrNull() }

private fun applyBpenv(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, bpenvMutation)
}

/**
 * Sets the BPF envelope depth (modulation amount).
 *
 * Controls how far above the base [bpf] centre frequency the filter sweeps when the ADSR envelope
 * is fully open. The depth is in SEMITONES: the sweep is pitch-linear, the way DAW
 * filter envelopes work: +12 doubles the cutoff at full envelope, -12 halves it, and
 * negative depths are first-class (no dead zone).
 *
 * ```
 * newCutoff = baseCutoff × 2^(semitones/12 × envelopeValue)
 * ```
 *
 * ### How cutoff, ADSR, and depth work together
 *
 * | Component | Role |
 * |-----------|------|
 * | `bpf(freq)` | Sets the **resting** centre frequency — where the filter sits with no envelope |
 * | `bpadsr` | Shapes the **envelope curve** over time (0→1→sustain→0) |
 * | `bpe(semitones)` | Scales **how far** the envelope moves the centre frequency |
 *
 * Example with `bpf(500).bpe(24).bpadsr(0.01, 0.5, 0.2, 0.3)`:
 *
 * | Phase | envValue | Centre freq |
 * |-------|----------|-------------|
 * | Note start | 0.0 | 500 Hz |
 * | Attack peak | 1.0 | 500 × 2^(24/12 × 1.0) = **2000 Hz** (2 octaves up) |
 * | Sustain | 0.2 | 500 × 2^(24/12 × 0.2) = **660 Hz** |
 * | Release end | 0.0 | 500 Hz |
 *
 * ```KlangScript(Playable)
 * s("sd").bpf(500).bpe(24)                // sweeps up to 2000 Hz at peak
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4").bpf(300).bpe("<7 36>")        // subtle (a fifth) vs dramatic (3 octaves) per cycle
 * ```
 *
 * @param semitones Envelope depth in semitones (+12 = one octave up at full envelope); omit to reinterpret the pattern's own values.
 * @return A [PatternMapperFn] that sets the BPF envelope depth, or [SprudelPattern] when called on a pattern.
 * @param-tool semitones SprudelBpEnvEditor, SprudelBpEnvSequenceEditor
 * @category effects
 * @tags bpenv, bpe, band pass filter, envelope, depth, modulation
 */
@KlangScript.Function
fun SprudelPattern.bpe(semitones: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyBpenv(this, listOfNotNull(semitones).asSprudelDslArgs(callInfo))

/** Sets the BPF envelope depth/amount on a string pattern. */
@KlangScript.Function
fun String.bpe(semitones: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).bpe(semitones, callInfo)

/** Creates a chained [PatternMapperFn] that sets the BPF envelope depth after the previous mapper. */
@KlangScript.Function
fun PatternMapperFn.bpe(semitones: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.bpe(semitones, callInfo) }

/** Creates a [PatternMapperFn] that sets the BPF envelope depth. */
@KlangScript.Function
fun bpe(semitones: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.bpe(semitones, callInfo) }

