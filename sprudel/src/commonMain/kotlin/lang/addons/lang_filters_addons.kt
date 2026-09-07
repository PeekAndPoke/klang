/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("DuplicatedCode", "ObjectPropertyName", "ClassName")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang.addons

import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel._applyControlFromParams
import io.peekandpoke.klang.sprudel._liftOrReinterpretNumericalField
import io.peekandpoke.klang.sprudel._mapNumericField
import io.peekandpoke.klang.sprudel.lang.FieldAccessor
import io.peekandpoke.klang.sprudel.lang.PatternLike
import io.peekandpoke.klang.sprudel.lang.PatternMapperFn
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.lang.asDoubleOrNull
import io.peekandpoke.klang.sprudel.lang.chain
import io.peekandpoke.klang.sprudel.lang.singleMapperOrNull
import io.peekandpoke.klang.sprudel.lang.toVoiceValuePattern
import io.peekandpoke.klang.sprudel.lang.voiceSetter
// -- notchf() ---------------------------------------------------------------------------------------------------------

private val notchfMutation = voiceSetter {
    val str = it?.toString() ?: return@voiceSetter
    notchf = str.toDoubleOrNull()
}

private fun applyNotchf(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.notchf }, update = notchfMutation)
    }

    return source._liftOrReinterpretNumericalField(args, notchfMutation)
}

/**
 * Applies a Notch Filter with the given centre frequency in Hz.
 *
 * Attenuates a narrow band of frequencies around the centre while passing everything else.
 * This is the opposite of a band pass filter. Use [nresonance] to control the notch width.
 *
 * When [freq] is omitted, the pattern's own numeric values are reinterpreted as centre frequencies.
 *
 * @param freq The centre frequency in Hz. Omit to reinterpret the pattern's values as centre frequency.
 * @return A new pattern with notch filter applied.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").notchf(1000)           // notch out 1 kHz
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd").notchf("<500 2000>")      // alternating notch centre per cycle
 * ```
 *
 * ```KlangScript(Playable)
 * seq("500 1000 2000").notchf()        // reinterpret values as notch centre
 * ```
 *
 * @param-tool freq SprudelNotchFilterEditor, SprudelNotchFilterSequenceEditor
 * @category effects
 * @tags notchf, notch filter, filter, frequency
 */
@KlangScript.Function
fun SprudelPattern.notchf(freq: PatternLike? = null, q: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern {
    // A tail-only call must not touch freq: reinterpret runs only on a fully bare call.
    val withFreq = if (freq != null || q == null) {
        applyNotchf(this, listOfNotNull(freq).asSprudelDslArgs(callInfo))
    } else {
        this
    }
    return if (q != null) withFreq.notchq(q, callInfo?.forParam(1)) else withFreq
}

/**
 * Parses this string as a pattern, then applies a Notch Filter.
 *
 * @param freq The centre frequency in Hz. Omit to reinterpret the pattern's values as centre frequency.
 * @return A new pattern with notch filter applied.
 *
 * ```KlangScript(Playable)
 * "c4 e4".notchf(1000).note()          // notch filter on string pattern
 * ```
 *
 * @category effects
 * @tags notchf, notch filter, filter, frequency
 */
@KlangScript.Function
fun String.notchf(freq: PatternLike? = null, q: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).notchf(freq, q, callInfo)

/**
 * The notch filter frequency of each event, as a value other setters can read.
 *
 * Bare `notchf` reads what the chain has set so far, so it comes after whatever set the field
 * (`notchf(...)` or an alias). Call it, `notchf(...)`, to set the field; a mapper argument applies
 * to the field.
 *
 * Aliases: `notch`, `ntf`.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").s("saw").notchf(1000).notchf(mul("1 2"))                    // the notch an octave up on the second note
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4 e4").s("saw").notchf("800 1600").lpf(notchf.mul(4))            // lowpass two octaves above the notch
 * ```
 *
 * @category effects
 * @tags notchf, accessor, addon
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("notchf")
object notchf : FieldAccessor({ it.notchf }) {

    /**
     * Returns a [PatternMapperFn] that applies a Notch Filter.
     *
     * @param freq The centre frequency in Hz. Omit to reinterpret the pattern's values as centre frequency.
     * @return A [PatternMapperFn] that applies a notch filter.
     *
     * ```KlangScript(Playable)
     * note("c4 e4").apply(notchf(1000))                   // apply notch via mapper
     * ```
     *
     * ```KlangScript(Playable)
     * note("c4*4").firstOf(4, notchf(500).nresonance(10)) // resonant notch on first cycle
     * ```
     */
    @KlangScript.Method(name = "invoke")
    operator fun invoke(freq: PatternLike? = null, q: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        { p -> p.notchf(freq, q, callInfo) }
}


/**
 * Creates a chained [PatternMapperFn] that applies a Notch Filter after the previous mapper.
 *
 * @param freq The centre frequency in Hz. Omit to reinterpret the pattern's values as centre frequency.
 * @return A new [PatternMapperFn] chaining the notch filter after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").apply(gain(0.8).notchf(1000))         // gain then notch filter
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").firstOf(4, notchf(500).nresonance(10)) // notch chain
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.notchf(freq: PatternLike? = null, q: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.notchf(freq, q, callInfo) }

// -- nresonance() / nres() - Notch Filter resonance ------------------------------------------------------------------

private val nresonanceMutation = voiceSetter { nresonance = it?.asDoubleOrNull() }

private fun applyNresonance(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.nresonance }, update = nresonanceMutation)
    }

    return source._liftOrReinterpretNumericalField(args, nresonanceMutation)
}

/**
 * Sets the resonance (Q factor) of the Notch Filter.
 *
 * Controls how narrow the notch is. Higher values create a narrower, deeper notch.
 * Use with [notchf] to set the notch frequency.
 *
 * When [q] is omitted, the pattern's own numeric values are reinterpreted as Q values.
 *
 * @param q The Q factor. Higher values create a narrower notch. Omit to reinterpret pattern values.
 * @return A new pattern with notch resonance applied.
 *
 * ```KlangScript(Playable)
 * note("c4").notchf(1000).nresonance(10)   // narrow deep notch at 1 kHz
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd").notchf(500).nresonance("<5 20>") // Q sweeps from wide to narrow per cycle
 * ```
 *
 * ```KlangScript(Playable)
 * seq("1 5 15").nresonance()               // reinterpret values as notch Q
 * ```
 *
 * @param-tool q SprudelNResonanceEditor, SprudelNResonanceSequenceEditor
 * @alias nres
 * @category effects
 * @tags nresonance, nres, notch filter, Q, resonance
 */
@KlangScript.Function
fun SprudelPattern.nresonance(q: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyNresonance(this, listOfNotNull(q).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern, then sets notch filter resonance.
 *
 * @param q The Q factor. Omit to reinterpret the pattern's values as Q.
 * @return A new pattern with notch resonance applied.
 *
 * ```KlangScript(Playable)
 * "c4".notchf(1000).nresonance(10)  // notch Q on string pattern
 * ```
 *
 * @alias nres
 * @category effects
 * @tags nresonance, nres, notch filter, Q, resonance
 */
@KlangScript.Function
fun String.nresonance(q: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).nresonance(q, callInfo)

/**
 * The notch filter resonance of each event, as a value other setters can read.
 *
 * Bare `nresonance` reads what the chain has set so far, so it comes after whatever set the field
 * (`nresonance(...)` or an alias). Call it, `nresonance(...)`, to set the field; a mapper argument applies
 * to the field.
 *
 * Aliases: `notchq`, `ntq`.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").s("saw").notchf(1000).nresonance(4).nresonance(mul("1 3"))   // a sharper notch on the second note
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4 e4").s("saw").notchf(1000).nresonance("2 8").lpf(4000).lpq(nresonance)   // same Q on the lowpass
 * ```
 *
 * @category effects
 * @tags nresonance, accessor, addon
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("nresonance")
object nresonance : FieldAccessor({ it.nresonance }) {

    /**
     * Returns a [PatternMapperFn] that sets notch filter resonance.
     *
     * @param q The Q factor. Omit to reinterpret the pattern's values as Q.
     * @return A [PatternMapperFn] that applies notch resonance.
     *
     * ```KlangScript(Playable)
     * note("c4 e4").apply(notchf(1000).nresonance(10))   // notch + Q chain
     * ```
     *
     * ```KlangScript(Playable)
     * note("c4*4").firstOf(4, nresonance(15))            // narrow notch on first cycle
     * ```
     */
    @KlangScript.Method(name = "invoke")
    operator fun invoke(q: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        { p -> p.nresonance(q, callInfo) }
}


/**
 * Creates a chained [PatternMapperFn] that sets notch resonance after the previous mapper.
 *
 * @param q The Q factor. Omit to reinterpret the pattern's values as Q.
 * @return A new [PatternMapperFn] chaining notch resonance after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").apply(notchf(500).nresonance(10))    // notchf then resonance
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").firstOf(4, notchf(1000).nresonance(15))  // narrow notch chain
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.nresonance(q: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.nresonance(q, callInfo) }

/**
 * Alias for [nresonance]. Sets the notch filter Q.
 *
 * @param q The Q factor. Omit to reinterpret the pattern's values as Q.
 * @return A new pattern with notch resonance applied.
 *
 * ```KlangScript(Playable)
 * note("c4").notchf(500).notchq(10)   // alias for nresonance
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4").notchq("<5 20>")       // sweeping notch Q
 * ```
 *
 * @param-tool q SprudelNotchQEditor, SprudelNotchQSequenceEditor
 * @alias nresonance
 * @category effects
 * @tags nres, nresonance, notch filter, Q
 */
@KlangScript.Function
fun SprudelPattern.notchq(q: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.nresonance(q, callInfo)

/**
 * Alias for [nresonance] on a string pattern.
 *
 * @param q The Q factor.
 * @return A new pattern with notch resonance applied.
 *
 * ```KlangScript(Playable)
 * "c4".notchf(500).notchq(10)       // alias for String.nresonance
 * ```
 */
@KlangScript.Function
fun String.notchq(q: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).notchq(q, callInfo)

/**
 * Alias of [nresonance]: the same accessor under another name.
 *
 * @category effects
 * @tags notchq, nresonance, accessor, addon
 */
@KlangScript.Constant
val notchq: nresonance = nresonance

/**
 * Creates a chained [PatternMapperFn] that sets notch resonance (alias for [nresonance]) after the previous mapper.
 *
 * @param q The Q factor.
 * @return A new [PatternMapperFn] chaining notch resonance after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").apply(notchf(500).notchq(10)) // notchf then notchq
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").firstOf(4, notchf(1000).notchq(15))  // chain
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.notchq(q: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.nresonance(q, callInfo)

// -- nfattack() / nfa() - Notch Filter Envelope Attack -----------------------------------------------------------------

private val nfattackMutation = voiceSetter { nfattack = it?.asDoubleOrNull() }

private fun applyNfattack(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.nfattack }, update = nfattackMutation)
    }

    return source._liftOrReinterpretNumericalField(args, nfattackMutation)
}

/**
 * Sets the notch filter envelope attack time in seconds.
 *
 * Controls how quickly the notch filter centre frequency sweeps from its baseline to the
 * peak at note onset. Use with [nfenv], [nfdecay], [nfsustain], [nfrelease].
 *
 * ```KlangScript(Playable)
 * note("c4").notchf(1000).nfenv(24).nfattack(0.1)   // notch sweeps open over 100 ms
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd").nfattack("<0.01 0.5>")                        // fast vs slow attack per cycle
 * ```
 *
 * @param seconds Attack time in seconds; omit to reinterpret the pattern's own values.
 * @return A [PatternMapperFn] that sets the notch filter attack time, or [SprudelPattern] when called on a pattern.
 * @param-tool seconds SprudelNfAttackEditor, SprudelNfAttackSequenceEditor
 * @alias nfa
 * @category effects
 * @tags nfattack, nfa, notch filter, envelope, attack
 */
@KlangScript.Function
fun SprudelPattern.nfattack(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyNfattack(this, listOfNotNull(seconds).asSprudelDslArgs(callInfo))

/** Sets the notch filter envelope attack time on a string pattern. */
@KlangScript.Function
fun String.nfattack(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).nfattack(seconds, callInfo)

/**
 * The notch envelope attack of each event, as a value other setters can read.
 *
 * Bare `nfattack` reads what the chain has set so far, so it comes after whatever set the field
 * (`nfattack(...)` or an alias). Call it, `nfattack(...)`, to set the field; a mapper argument applies
 * to the field.
 *
 * Aliases: `nfa`.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").s("saw").notchf(1000).nfenv(24).nfattack(0.1).nfattack(mul("1 3"))   // the second notch opens slower
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4 e4").s("saw").notchf(1000).nfenv(24).nfattack("0.05 0.2").nfdecay(nfattack)   // decay follows attack
 * ```
 *
 * @category effects
 * @tags nfattack, accessor, addon
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("nfattack")
object nfattack : FieldAccessor({ it.nfattack }) {

    /** Creates a [PatternMapperFn] that sets the notch filter envelope attack time. */
    @KlangScript.Method(name = "invoke")
    operator fun invoke(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        { p -> p.nfattack(seconds, callInfo) }
}


/** Creates a chained [PatternMapperFn] that sets the notch filter envelope attack time after the previous mapper. */
@KlangScript.Function
fun PatternMapperFn.nfattack(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.nfattack(seconds, callInfo) }

/**
 * Alias for [nfattack]. Sets the notch filter envelope attack time.
 *
 * ```KlangScript(Playable)
 * note("c4").notchf(1000).nfa(0.1)   // alias for nfattack()
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4").apply(notchf(1000).nfa(0.1))   // chained PatternMapperFn
 * ```
 *
 * @param seconds Attack time in seconds; omit to reinterpret the pattern's own values.
 * @return A [PatternMapperFn] that sets the notch filter attack time, or [SprudelPattern] when called on a pattern.
 * @param-tool seconds SprudelNfAttackEditor, SprudelNfAttackSequenceEditor
 * @alias nfattack
 * @category effects
 * @tags nfa, nfattack, notch filter, envelope, attack
 */
@KlangScript.Function
fun SprudelPattern.nfa(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.nfattack(seconds, callInfo)

/** Alias for [nfattack] on a string pattern. */
@KlangScript.Function
fun String.nfa(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).nfa(seconds, callInfo)

/**
 * Alias of [nfattack]: the same accessor under another name.
 *
 * @category effects
 * @tags nfa, nfattack, accessor, addon
 */
@KlangScript.Constant
val nfa: nfattack = nfattack

/** Creates a chained [PatternMapperFn] that sets notch filter attack (alias for [nfattack]) after the previous mapper. */
@KlangScript.Function
fun PatternMapperFn.nfa(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.nfattack(seconds, callInfo)

// -- nfdecay() / nfd() - Notch Filter Envelope Decay -------------------------------------------------------------------

private val nfdecayMutation = voiceSetter { nfdecay = it?.asDoubleOrNull() }

private fun applyNfdecay(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.nfdecay }, update = nfdecayMutation)
    }

    return source._liftOrReinterpretNumericalField(args, nfdecayMutation)
}

/**
 * Sets the notch filter envelope decay time in seconds.
 *
 * Controls how quickly the notch filter centre frequency moves from peak to sustain level
 * after the attack. Use with [nfattack], [nfsustain], [nfrelease], [nfenv].
 *
 * ```KlangScript(Playable)
 * note("c4").notchf(1000).nfenv(24).nfsustain(0.2).nfdecay(0.2)   // notch decays over 200 ms
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd").nfdecay("<0.05 0.5>")                        // short vs long decay per cycle
 * ```
 *
 * @param seconds Decay time in seconds; omit to reinterpret the pattern's own values.
 * @return A [PatternMapperFn] that sets the notch filter decay time, or [SprudelPattern] when called on a pattern.
 * @param-tool seconds SprudelNfDecayEditor, SprudelNfDecaySequenceEditor
 * @alias nfd
 * @category effects
 * @tags nfdecay, nfd, notch filter, envelope, decay
 */
@KlangScript.Function
fun SprudelPattern.nfdecay(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyNfdecay(this, listOfNotNull(seconds).asSprudelDslArgs(callInfo))

/** Sets the notch filter envelope decay time on a string pattern. */
@KlangScript.Function
fun String.nfdecay(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).nfdecay(seconds, callInfo)

/**
 * The notch envelope decay of each event, as a value other setters can read.
 *
 * Bare `nfdecay` reads what the chain has set so far, so it comes after whatever set the field
 * (`nfdecay(...)` or an alias). Call it, `nfdecay(...)`, to set the field; a mapper argument applies
 * to the field.
 *
 * Aliases: `nfd`.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").s("saw").notchf(1000).nfenv(24).nfsustain(0.2).nfdecay(0.2).nfdecay(mul("1 2"))   // the second notch falls slower
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4 e4").s("saw").notchf(1000).nfenv(24).nfdecay("0.1 0.4").nfrelease(nfdecay)   // release follows decay
 * ```
 *
 * @category effects
 * @tags nfdecay, accessor, addon
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("nfdecay")
object nfdecay : FieldAccessor({ it.nfdecay }) {

    /** Creates a [PatternMapperFn] that sets the notch filter envelope decay time. */
    @KlangScript.Method(name = "invoke")
    operator fun invoke(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        { p -> p.nfdecay(seconds, callInfo) }
}


/** Creates a chained [PatternMapperFn] that sets the notch filter envelope decay time after the previous mapper. */
@KlangScript.Function
fun PatternMapperFn.nfdecay(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.nfdecay(seconds, callInfo) }

/**
 * Alias for [nfdecay]. Sets the notch filter envelope decay time.
 *
 * ```KlangScript(Playable)
 * note("c4").notchf(1000).nfd(0.2)   // alias for nfdecay()
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4").apply(notchf(1000).nfd(0.2))   // chained PatternMapperFn
 * ```
 *
 * @param seconds Decay time in seconds; omit to reinterpret the pattern's own values.
 * @return A [PatternMapperFn] that sets the notch filter decay time, or [SprudelPattern] when called on a pattern.
 * @param-tool seconds SprudelNfDecayEditor, SprudelNfDecaySequenceEditor
 * @alias nfdecay
 * @category effects
 * @tags nfd, nfdecay, notch filter, envelope, decay
 */
@KlangScript.Function
fun SprudelPattern.nfd(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.nfdecay(seconds, callInfo)

/** Alias for [nfdecay] on a string pattern. */
@KlangScript.Function
fun String.nfd(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).nfd(seconds, callInfo)

/**
 * Alias of [nfdecay]: the same accessor under another name.
 *
 * @category effects
 * @tags nfd, nfdecay, accessor, addon
 */
@KlangScript.Constant
val nfd: nfdecay = nfdecay

/** Creates a chained [PatternMapperFn] that sets notch filter decay (alias for [nfdecay]) after the previous mapper. */
@KlangScript.Function
fun PatternMapperFn.nfd(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.nfdecay(seconds, callInfo)

// -- nfsustain() / nfs() - Notch Filter Envelope Sustain ---------------------------------------------------------------

private val nfsustainMutation = voiceSetter { nfsustain = it?.asDoubleOrNull() }

private fun applyNfsustain(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.nfsustain }, update = nfsustainMutation)
    }

    return source._liftOrReinterpretNumericalField(args, nfsustainMutation)
}

/**
 * Sets the notch filter envelope sustain level (0–1).
 *
 * Controls the notch centre frequency level during the sustained portion of the note.
 * `1` holds the notch at the envelope peak; `0` returns to baseline. Use with
 * [nfattack]/[nfdecay]/[nfrelease].
 *
 * ```KlangScript(Playable)
 * note("c4").notchf(1000).nfenv(24).nfsustain(0.5)  // sustain at half depth
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4").nfsustain("<0 1>")                         // closed vs fully open sustain
 * ```
 *
 * @param level Sustain level (0–1); omit to reinterpret the pattern's own values.
 * @return A [PatternMapperFn] that sets the notch filter sustain level, or [SprudelPattern] when called on a pattern.
 * @param-tool level SprudelNfSustainEditor, SprudelNfSustainSequenceEditor
 * @alias nfs
 * @category effects
 * @tags nfsustain, nfs, notch filter, envelope, sustain
 */
@KlangScript.Function
fun SprudelPattern.nfsustain(level: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyNfsustain(this, listOfNotNull(level).asSprudelDslArgs(callInfo))

/** Sets the notch filter envelope sustain level on a string pattern. */
@KlangScript.Function
fun String.nfsustain(level: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).nfsustain(level, callInfo)

/**
 * The notch envelope sustain level of each event, as a value other setters can read.
 *
 * Bare `nfsustain` reads what the chain has set so far, so it comes after whatever set the field
 * (`nfsustain(...)` or an alias). Call it, `nfsustain(...)`, to set the field; a mapper argument applies
 * to the field.
 *
 * Aliases: `nfs`.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").s("saw").notchf(1000).nfenv(24).nfsustain(0.5).nfsustain(mul("1 0"))   // the second notch percussive
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4 e4").s("saw").notchf(1000).nfenv(24).nfsustain("0.2 0.8").nfdecay(nfsustain)   // decay follows sustain
 * ```
 *
 * @category effects
 * @tags nfsustain, accessor, addon
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("nfsustain")
object nfsustain : FieldAccessor({ it.nfsustain }) {

    /** Creates a [PatternMapperFn] that sets the notch filter envelope sustain level. */
    @KlangScript.Method(name = "invoke")
    operator fun invoke(level: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        { p -> p.nfsustain(level, callInfo) }
}


/** Creates a chained [PatternMapperFn] that sets the notch filter sustain level after the previous mapper. */
@KlangScript.Function
fun PatternMapperFn.nfsustain(level: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.nfsustain(level, callInfo) }

/**
 * Alias for [nfsustain]. Sets the notch filter envelope sustain level.
 *
 * ```KlangScript(Playable)
 * note("c4").notchf(1000).nfs(0.5)   // alias for nfsustain()
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4").apply(notchf(1000).nfs(0.5))   // chained PatternMapperFn
 * ```
 *
 * @param level Sustain level (0–1); omit to reinterpret the pattern's own values.
 * @return A [PatternMapperFn] that sets the notch filter sustain level, or [SprudelPattern] when called on a pattern.
 * @param-tool level SprudelNfSustainEditor, SprudelNfSustainSequenceEditor
 * @alias nfsustain
 * @category effects
 * @tags nfs, nfsustain, notch filter, envelope, sustain
 */
@KlangScript.Function
fun SprudelPattern.nfs(level: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.nfsustain(level, callInfo)

/** Alias for [nfsustain] on a string pattern. */
@KlangScript.Function
fun String.nfs(level: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).nfs(level, callInfo)

/**
 * Alias of [nfsustain]: the same accessor under another name.
 *
 * @category effects
 * @tags nfs, nfsustain, accessor, addon
 */
@KlangScript.Constant
val nfs: nfsustain = nfsustain

/** Creates a chained [PatternMapperFn] that sets notch filter sustain (alias for [nfsustain]) after the previous mapper. */
@KlangScript.Function
fun PatternMapperFn.nfs(level: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.nfsustain(level, callInfo)

// -- nfrelease() / nfr() - Notch Filter Envelope Release ---------------------------------------------------------------

private val nfreleaseMutation = voiceSetter { nfrelease = it?.asDoubleOrNull() }

private fun applyNfrelease(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.nfrelease }, update = nfreleaseMutation)
    }

    return source._liftOrReinterpretNumericalField(args, nfreleaseMutation)
}

/**
 * Sets the notch filter envelope release time in seconds.
 *
 * Controls how quickly the notch filter centre frequency returns to baseline after the
 * note ends. Use with [nfattack], [nfdecay], [nfsustain], [nfenv].
 *
 * ```KlangScript(Playable)
 * note("c4").notchf(1000).nfenv(24).nfrelease(0.4)   // notch closes slowly after note
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd").nfrelease("<0.05 1.0>")                        // short vs long release per cycle
 * ```
 *
 * @param seconds Release time in seconds; omit to reinterpret the pattern's own values.
 * @return A [PatternMapperFn] that sets the notch filter release time, or [SprudelPattern] when called on a pattern.
 * @param-tool seconds SprudelNfReleaseEditor, SprudelNfReleaseSequenceEditor
 * @alias nfr
 * @category effects
 * @tags nfrelease, nfr, notch filter, envelope, release
 */
@KlangScript.Function
fun SprudelPattern.nfrelease(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyNfrelease(this, listOfNotNull(seconds).asSprudelDslArgs(callInfo))

/** Sets the notch filter envelope release time on a string pattern. */
@KlangScript.Function
fun String.nfrelease(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).nfrelease(seconds, callInfo)

/**
 * The notch envelope release of each event, as a value other setters can read.
 *
 * Bare `nfrelease` reads what the chain has set so far, so it comes after whatever set the field
 * (`nfrelease(...)` or an alias). Call it, `nfrelease(...)`, to set the field; a mapper argument applies
 * to the field.
 *
 * Aliases: `nfr`.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").s("saw").notchf(1000).nfenv(24).nfrelease(0.3).nfrelease(mul("1 2"))   // the second notch closes slower
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4 e4").s("saw").notchf(1000).nfenv(24).nfrelease("0.1 0.4").nfattack(nfrelease)   // attack follows release
 * ```
 *
 * @category effects
 * @tags nfrelease, accessor, addon
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("nfrelease")
object nfrelease : FieldAccessor({ it.nfrelease }) {

    /** Creates a [PatternMapperFn] that sets the notch filter envelope release time. */
    @KlangScript.Method(name = "invoke")
    operator fun invoke(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        { p -> p.nfrelease(seconds, callInfo) }
}


/** Creates a chained [PatternMapperFn] that sets the notch filter envelope release time after the previous mapper. */
@KlangScript.Function
fun PatternMapperFn.nfrelease(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.nfrelease(seconds, callInfo) }

/**
 * Alias for [nfrelease]. Sets the notch filter envelope release time.
 *
 * ```KlangScript(Playable)
 * note("c4").notchf(1000).nfr(0.4)   // alias for nfrelease()
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4").apply(notchf(1000).nfr(0.4))   // chained PatternMapperFn
 * ```
 *
 * @param seconds Release time in seconds; omit to reinterpret the pattern's own values.
 * @return A [PatternMapperFn] that sets the notch filter release time, or [SprudelPattern] when called on a pattern.
 * @param-tool seconds SprudelNfReleaseEditor, SprudelNfReleaseSequenceEditor
 * @alias nfrelease
 * @category effects
 * @tags nfr, nfrelease, notch filter, envelope, release
 */
@KlangScript.Function
fun SprudelPattern.nfr(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.nfrelease(seconds, callInfo)

/** Alias for [nfrelease] on a string pattern. */
@KlangScript.Function
fun String.nfr(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).nfr(seconds, callInfo)

/**
 * Alias of [nfrelease]: the same accessor under another name.
 *
 * @category effects
 * @tags nfr, nfrelease, accessor, addon
 */
@KlangScript.Constant
val nfr: nfrelease = nfrelease

/** Creates a chained [PatternMapperFn] that sets notch filter release (alias for [nfrelease]) after the previous mapper. */
@KlangScript.Function
fun PatternMapperFn.nfr(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.nfrelease(seconds, callInfo)

// -- nfenv() / nfe() - Notch Filter Envelope Depth ---------------------------------------------------------------------

private val nfenvMutation = voiceSetter { nfenv = it?.asDoubleOrNull() }

private fun applyNfenv(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.nfenv }, update = nfenvMutation)
    }

    return source._liftOrReinterpretNumericalField(args, nfenvMutation)
}

/**
 * Sets the notch filter envelope depth (modulation amount).
 *
 * Controls how far above the base [notchf] centre frequency the notch sweeps when the ADSR envelope
 * is fully open. The depth is in SEMITONES: the sweep is pitch-linear, the way DAW
 * filter envelopes work: +12 doubles the cutoff at full envelope, -12 halves it, and
 * negative depths are first-class (no dead zone).
 *
 * ```
 * newCutoff = baseCutoff × 2^(depth/12 × envelopeValue)
 * ```
 *
 * ### How cutoff, ADSR, and depth work together
 *
 * | Component | Role |
 * |-----------|------|
 * | `notchf(freq)` | Sets the **resting** centre frequency — where the notch sits with no envelope |
 * | `nfattack / nfdecay / nfsustain / nfrelease` | Shapes the **envelope curve** over time (0→1→sustain→0) |
 * | `nfenv(depth)` | Scales **how far** the envelope moves the centre frequency |
 *
 * Example with `notchf(500).nfenv(24).nfattack(0.01).nfdecay(0.5).nfsustain(0.2).nfrelease(0.3)`:
 *
 * | Phase | envValue | Centre freq |
 * |-------|----------|-------------|
 * | Note start | 0.0 | 500 Hz |
 * | Attack peak | 1.0 | 500 × 2^(24/12 × 1.0) = **2000 Hz** (2 octaves up) |
 * | Sustain | 0.2 | 500 × 2^(24/12 × 0.2) = **660 Hz** |
 * | Release end | 0.0 | 500 Hz |
 *
 * ```KlangScript(Playable)
 * note("c4").notchf(1000).nfenv(24)              // notch sweeps up to 4000 Hz at peak
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd").notchf(500).nfenv("<7 36>")              // subtle (a fifth) vs dramatic (3 octaves) per cycle
 * ```
 *
 * @param depth Envelope depth in semitones (+12 = one octave up at full envelope); omit to reinterpret the pattern's own values.
 * @return A [PatternMapperFn] that sets the notch filter envelope depth, or [SprudelPattern] when called on a pattern.
 * @param-tool depth SprudelNfEnvEditor, SprudelNfEnvSequenceEditor
 * @alias nfe
 * @category effects
 * @tags nfenv, nfe, notch filter, envelope, depth, modulation
 */
@KlangScript.Function
fun SprudelPattern.nfenv(depth: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyNfenv(this, listOfNotNull(depth).asSprudelDslArgs(callInfo))

/** Sets the notch filter envelope depth/amount on a string pattern. */
@KlangScript.Function
fun String.nfenv(depth: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).nfenv(depth, callInfo)

/**
 * The notch envelope depth of each event in semitones, as a value other setters can read.
 *
 * Bare `nfenv` reads what the chain has set so far, so it comes after whatever set the field
 * (`nfenv(...)` or an alias). Call it, `nfenv(...)`, to set the field; a mapper argument applies
 * to the field.
 *
 * Aliases: `nfe`.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").s("saw").notchf(500).nfattack(0.1).nfenv(24).nfenv(mul("1 -1"))   // up on the first, down on the second
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4 e4").s("saw").notchf(500).nfattack(0.1).nfenv("12 24").lpf(2000).lpe(nfenv)   // the lowpass sweeps as far
 * ```
 *
 * @category effects
 * @tags nfenv, accessor, addon
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("nfenv")
object nfenv : FieldAccessor({ it.nfenv }) {

    /** Creates a [PatternMapperFn] that sets the notch filter envelope depth. */
    @KlangScript.Method(name = "invoke")
    operator fun invoke(depth: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        { p -> p.nfenv(depth, callInfo) }
}


/** Creates a chained [PatternMapperFn] that sets the notch filter envelope depth after the previous mapper. */
@KlangScript.Function
fun PatternMapperFn.nfenv(depth: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.nfenv(depth, callInfo) }

/**
 * Alias for [nfenv]. Sets the notch filter envelope depth.
 *
 * ```KlangScript(Playable)
 * note("c4").notchf(500).nfe(24)   // alias for nfenv()
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4").apply(notchf(500).nfe(24))   // chained PatternMapperFn
 * ```
 *
 * @param depth Envelope depth in semitones (+12 = one octave up at full envelope); omit to reinterpret the pattern's own values.
 * @return A [PatternMapperFn] that sets the notch filter envelope depth, or [SprudelPattern] when called on a pattern.
 * @param-tool depth SprudelNfEnvEditor, SprudelNfEnvSequenceEditor
 * @alias nfenv
 * @category effects
 * @tags nfe, nfenv, notch filter, envelope, depth, modulation
 */
@KlangScript.Function
fun SprudelPattern.nfe(depth: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.nfenv(depth, callInfo)

/** Alias for [nfenv] on a string pattern. */
@KlangScript.Function
fun String.nfe(depth: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).nfe(depth, callInfo)

/**
 * Alias of [nfenv]: the same accessor under another name.
 *
 * @category effects
 * @tags nfe, nfenv, accessor, addon
 */
@KlangScript.Constant
val nfe: nfenv = nfenv

/** Creates a chained [PatternMapperFn] that sets notch filter envelope depth (alias for [nfenv]) after the previous mapper. */
@KlangScript.Function
fun PatternMapperFn.nfe(depth: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.nfenv(depth, callInfo)

// -- notch() / ntf() / ntq() -------------------------------------------------------------------------------------

/**
 * Applies a Notch (band-reject) filter — the CANONICAL name. `notchf` is the long-standing
 * spelling and stays first-class; both are the same function.
 *
 * @param freq The centre frequency in Hz to reject. Omit to reinterpret the pattern's values.
 * @param q The filter Q factor (notch width). Omit to leave it unchanged.
 * @return A new pattern with the notch applied.
 *
 * ```KlangScript(Playable)
 * s("sd").notch(1000)
 * ```
 *
 * @category effects
 * @tags notch, notchf, ntf, band reject, filter, frequency, addon
 */
@KlangScript.Function
fun SprudelPattern.notch(freq: PatternLike? = null, q: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    notchf(freq, q, callInfo)

/** Applies a Notch filter to a string pattern (see [SprudelPattern.notch]). */
@KlangScript.Function
fun String.notch(freq: PatternLike? = null, q: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.notchf(freq, q, callInfo)

/**
 * Alias of [notchf]: the same accessor under another name.
 *
 * @category effects
 * @tags notch, notchf, accessor, addon
 */
@KlangScript.Constant
val notch: notchf = notchf

/** Chains a notch step onto this [PatternMapperFn] (see [SprudelPattern.notch]). */
@KlangScript.Function
fun PatternMapperFn.notch(freq: PatternLike? = null, q: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.notchf(freq, q, callInfo)

/**
 * Sets the notch centre frequency — the short form, completing the `xxf`/`xxq` family that
 * `lpf`/`lpq`, `hpf`/`hpq` and `bpf`/`bpq` already had.
 *
 * @param freq The centre frequency in Hz to reject. Omit to reinterpret the pattern's values.
 * @return A new pattern with the notch frequency applied.
 *
 * ```KlangScript(Playable)
 * s("sd").ntf(1000).ntq(8)
 * ```
 *
 * @category effects
 * @tags ntf, notch, notchf, band reject, filter, frequency, addon
 */
@KlangScript.Function
fun SprudelPattern.ntf(freq: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    notchf(freq, null, callInfo)

/** Sets the notch centre frequency on a string pattern (see [SprudelPattern.ntf]). */
@KlangScript.Function
fun String.ntf(freq: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.notchf(freq, null, callInfo)

/**
 * Alias of [notchf]: the same accessor under another name.
 *
 * @category effects
 * @tags ntf, notchf, accessor, addon
 */
@KlangScript.Constant
val ntf: notchf = notchf

/** Chains an ntf step onto this [PatternMapperFn] (see [SprudelPattern.ntf]). */
@KlangScript.Function
fun PatternMapperFn.ntf(freq: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.notchf(freq, null, callInfo)

/**
 * Sets the notch Q (width) — the short form, completing the `xxf`/`xxq` family.
 *
 * @param q The filter Q factor (notch width). Omit to reinterpret the pattern's values.
 * @return A new pattern with the notch Q applied.
 *
 * ```KlangScript(Playable)
 * s("sd").ntf(1000).ntq(8)
 * ```
 *
 * @category effects
 * @tags ntq, notch, notchq, band reject, filter, resonance, addon
 */
@KlangScript.Function
fun SprudelPattern.ntq(q: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    notchq(q, callInfo)

/** Sets the notch Q on a string pattern (see [SprudelPattern.ntq]). */
@KlangScript.Function
fun String.ntq(q: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.notchq(q, callInfo)

/**
 * Alias of [nresonance]: the same accessor under another name.
 *
 * @category effects
 * @tags ntq, nresonance, accessor, addon
 */
@KlangScript.Constant
val ntq: nresonance = nresonance

/** Chains an ntq step onto this [PatternMapperFn] (see [SprudelPattern.ntq]). */
@KlangScript.Function
fun PatternMapperFn.ntq(q: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.notchq(q, callInfo)
