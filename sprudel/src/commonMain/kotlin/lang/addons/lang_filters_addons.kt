@file:Suppress("DuplicatedCode", "ObjectPropertyName")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang.addons

import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel._applyControlFromParams
import io.peekandpoke.klang.sprudel._liftOrReinterpretNumericalField
import io.peekandpoke.klang.sprudel.lang.PatternLike
import io.peekandpoke.klang.sprudel.lang.PatternMapperFn
import io.peekandpoke.klang.sprudel.lang.SprudelDsl
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.lang.asDoubleOrNull
import io.peekandpoke.klang.sprudel.lang.chain
import io.peekandpoke.klang.sprudel.lang.toVoiceValuePattern
import io.peekandpoke.klang.sprudel.lang.voiceModifier

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in SprudelRegistry.
 */
var sprudelLangFiltersAddonsInit = false

// -- notchf() ---------------------------------------------------------------------------------------------------------

private val notchfMutation = voiceModifier {
    val str = it?.toString() ?: return@voiceModifier this
    if (":" in str) {
        val parts = str.split(":").map { d -> d.trim().toDoubleOrNull() }
        copy(
            notchf = parts.getOrNull(0) ?: notchf,
            nresonance = parts.getOrNull(1) ?: nresonance,
            nfenv = parts.getOrNull(2) ?: nfenv,
        )
    } else {
        copy(notchf = str.toDoubleOrNull())
    }
}

private fun applyNotchf(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val str = args.firstOrNull()?.value?.toString() ?: ""
    return if (":" in str) {
        source._applyControlFromParams(args, notchfMutation) { src, ctrl ->
            src.copy(
                notchf = ctrl.notchf ?: src.notchf,
                nresonance = ctrl.nresonance ?: src.nresonance,
                nfenv = ctrl.nfenv ?: src.nfenv,
            )
        }
    } else {
        source._liftOrReinterpretNumericalField(args, notchfMutation)
    }
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
 * @param-tool freq SprudelNotchFilterSequenceEditor
 * @category effects
 * @tags notchf, notch filter, filter, frequency
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.notchf(freq: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyNotchf(this, listOfNotNull(freq).asSprudelDslArgs(callInfo))

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
@SprudelDsl
@KlangScript.Function
fun String.notchf(freq: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).notchf(freq, callInfo)

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
 *
 * @category effects
 * @tags notchf, notch filter, filter, frequency
 */
@SprudelDsl
@KlangScript.Function
fun notchf(freq: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.notchf(freq, callInfo) }

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
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.notchf(freq: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.notchf(freq, callInfo) }

// -- nresonance() / nres() - Notch Filter resonance ------------------------------------------------------------------

private val nresonanceMutation = voiceModifier { copy(nresonance = it?.asDoubleOrNull()) }

private fun applyNresonance(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
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
 * @param-tool q SprudelNResonanceSequenceEditor
 * @alias nres
 * @category effects
 * @tags nresonance, nres, notch filter, Q, resonance
 */
@SprudelDsl
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
@SprudelDsl
@KlangScript.Function
fun String.nresonance(q: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).nresonance(q, callInfo)

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
 *
 * @alias nres
 * @category effects
 * @tags nresonance, nres, notch filter, Q, resonance
 */
@SprudelDsl
@KlangScript.Function
fun nresonance(q: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.nresonance(q, callInfo) }

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
@SprudelDsl
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
 * note("c4").notchf(500).nres(10)   // alias for nresonance
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4").nres("<5 20>")         // sweeping notch Q
 * ```
 *
 * @param-tool q SprudelNotchQSequenceEditor
 * @alias nresonance
 * @category effects
 * @tags nres, nresonance, notch filter, Q
 */
@SprudelDsl
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
 * "c4".notchf(500).nres(10)         // alias for String.nresonance
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun String.notchq(q: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).notchq(q, callInfo)

/**
 * Alias for [nresonance]. Returns a [PatternMapperFn] that sets notch filter resonance.
 *
 * @param q The Q factor.
 * @return A [PatternMapperFn] that applies notch resonance.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").apply(notchf(1000).nres(10))  // alias for nresonance()
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").firstOf(4, nres(15))           // narrow notch on first cycle
 * ```
 *
 * @alias nresonance
 * @category effects
 * @tags nres, nresonance, notch filter, Q
 */
@SprudelDsl
@KlangScript.Function
fun notchq(q: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    nresonance(q, callInfo)

/**
 * Creates a chained [PatternMapperFn] that sets notch resonance (alias for [nresonance]) after the previous mapper.
 *
 * @param q The Q factor.
 * @return A new [PatternMapperFn] chaining notch resonance after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").apply(notchf(500).nres(10))   // notchf then nres
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").firstOf(4, notchf(1000).nres(15))  // chain
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.notchq(q: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.nresonance(q, callInfo)

// -- nfattack() / nfa() - Notch Filter Envelope Attack -----------------------------------------------------------------

private val nfattackMutation = voiceModifier { copy(nfattack = it?.asDoubleOrNull()) }

private fun applyNfattack(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, nfattackMutation)
}

/**
 * Sets the notch filter envelope attack time in seconds.
 *
 * Controls how quickly the notch filter centre frequency sweeps from its baseline to the
 * peak at note onset. Use with [nfenv], [nfdecay], [nfsustain], [nfrelease].
 *
 * ```KlangScript(Playable)
 * note("c4").notchf(1000).nfenv(3000).nfattack(0.1)   // notch sweeps open over 100 ms
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd").nfattack("<0.01 0.5>")                        // fast vs slow attack per cycle
 * ```
 *
 * @param seconds Attack time in seconds; omit to reinterpret the pattern's own values.
 * @return A [PatternMapperFn] that sets the notch filter attack time, or [SprudelPattern] when called on a pattern.
 * @param-tool seconds SprudelNfAttackSequenceEditor
 * @alias nfa
 * @category effects
 * @tags nfattack, nfa, notch filter, envelope, attack
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.nfattack(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyNfattack(this, listOfNotNull(seconds).asSprudelDslArgs(callInfo))

/** Sets the notch filter envelope attack time on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.nfattack(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).nfattack(seconds, callInfo)

/** Creates a [PatternMapperFn] that sets the notch filter envelope attack time. */
@SprudelDsl
@KlangScript.Function
fun nfattack(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.nfattack(seconds, callInfo) }

/** Creates a chained [PatternMapperFn] that sets the notch filter envelope attack time after the previous mapper. */
@SprudelDsl
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
 * @param-tool seconds SprudelNfAttackSequenceEditor
 * @alias nfattack
 * @category effects
 * @tags nfa, nfattack, notch filter, envelope, attack
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.nfa(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.nfattack(seconds, callInfo)

/** Alias for [nfattack] on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.nfa(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).nfa(seconds, callInfo)

/** Creates a [PatternMapperFn] that sets the notch filter envelope attack time (alias for [nfattack]). */
@SprudelDsl
@KlangScript.Function
fun nfa(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    nfattack(seconds, callInfo)

/** Creates a chained [PatternMapperFn] that sets notch filter attack (alias for [nfattack]) after the previous mapper. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.nfa(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.nfattack(seconds, callInfo)

// -- nfdecay() / nfd() - Notch Filter Envelope Decay -------------------------------------------------------------------

private val nfdecayMutation = voiceModifier { copy(nfdecay = it?.asDoubleOrNull()) }

private fun applyNfdecay(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, nfdecayMutation)
}

/**
 * Sets the notch filter envelope decay time in seconds.
 *
 * Controls how quickly the notch filter centre frequency moves from peak to sustain level
 * after the attack. Use with [nfattack], [nfsustain], [nfrelease], [nfenv].
 *
 * ```KlangScript(Playable)
 * note("c4").notchf(1000).nfenv(3000).nfdecay(0.2)   // notch decays over 200 ms
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd").nfdecay("<0.05 0.5>")                        // short vs long decay per cycle
 * ```
 *
 * @param seconds Decay time in seconds; omit to reinterpret the pattern's own values.
 * @return A [PatternMapperFn] that sets the notch filter decay time, or [SprudelPattern] when called on a pattern.
 * @param-tool seconds SprudelNfDecaySequenceEditor
 * @alias nfd
 * @category effects
 * @tags nfdecay, nfd, notch filter, envelope, decay
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.nfdecay(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyNfdecay(this, listOfNotNull(seconds).asSprudelDslArgs(callInfo))

/** Sets the notch filter envelope decay time on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.nfdecay(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).nfdecay(seconds, callInfo)

/** Creates a [PatternMapperFn] that sets the notch filter envelope decay time. */
@SprudelDsl
@KlangScript.Function
fun nfdecay(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.nfdecay(seconds, callInfo) }

/** Creates a chained [PatternMapperFn] that sets the notch filter envelope decay time after the previous mapper. */
@SprudelDsl
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
 * @param-tool seconds SprudelNfDecaySequenceEditor
 * @alias nfdecay
 * @category effects
 * @tags nfd, nfdecay, notch filter, envelope, decay
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.nfd(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.nfdecay(seconds, callInfo)

/** Alias for [nfdecay] on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.nfd(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).nfd(seconds, callInfo)

/** Creates a [PatternMapperFn] that sets the notch filter envelope decay time (alias for [nfdecay]). */
@SprudelDsl
@KlangScript.Function
fun nfd(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    nfdecay(seconds, callInfo)

/** Creates a chained [PatternMapperFn] that sets notch filter decay (alias for [nfdecay]) after the previous mapper. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.nfd(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.nfdecay(seconds, callInfo)

// -- nfsustain() / nfs() - Notch Filter Envelope Sustain ---------------------------------------------------------------

private val nfsustainMutation = voiceModifier { copy(nfsustain = it?.asDoubleOrNull()) }

private fun applyNfsustain(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
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
 * note("c4").notchf(1000).nfenv(3000).nfsustain(0.5)  // sustain at half depth
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4").nfsustain("<0 1>")                         // closed vs fully open sustain
 * ```
 *
 * @param level Sustain level (0–1); omit to reinterpret the pattern's own values.
 * @return A [PatternMapperFn] that sets the notch filter sustain level, or [SprudelPattern] when called on a pattern.
 * @param-tool level SprudelNfSustainSequenceEditor
 * @alias nfs
 * @category effects
 * @tags nfsustain, nfs, notch filter, envelope, sustain
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.nfsustain(level: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyNfsustain(this, listOfNotNull(level).asSprudelDslArgs(callInfo))

/** Sets the notch filter envelope sustain level on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.nfsustain(level: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).nfsustain(level, callInfo)

/** Creates a [PatternMapperFn] that sets the notch filter envelope sustain level. */
@SprudelDsl
@KlangScript.Function
fun nfsustain(level: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.nfsustain(level, callInfo) }

/** Creates a chained [PatternMapperFn] that sets the notch filter sustain level after the previous mapper. */
@SprudelDsl
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
 * @param-tool level SprudelNfSustainSequenceEditor
 * @alias nfsustain
 * @category effects
 * @tags nfs, nfsustain, notch filter, envelope, sustain
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.nfs(level: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.nfsustain(level, callInfo)

/** Alias for [nfsustain] on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.nfs(level: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).nfs(level, callInfo)

/** Creates a [PatternMapperFn] that sets the notch filter envelope sustain level (alias for [nfsustain]). */
@SprudelDsl
@KlangScript.Function
fun nfs(level: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    nfsustain(level, callInfo)

/** Creates a chained [PatternMapperFn] that sets notch filter sustain (alias for [nfsustain]) after the previous mapper. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.nfs(level: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.nfsustain(level, callInfo)

// -- nfrelease() / nfr() - Notch Filter Envelope Release ---------------------------------------------------------------

private val nfreleaseMutation = voiceModifier { copy(nfrelease = it?.asDoubleOrNull()) }

private fun applyNfrelease(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, nfreleaseMutation)
}

/**
 * Sets the notch filter envelope release time in seconds.
 *
 * Controls how quickly the notch filter centre frequency returns to baseline after the
 * note ends. Use with [nfattack], [nfdecay], [nfsustain], [nfenv].
 *
 * ```KlangScript(Playable)
 * note("c4").notchf(1000).nfenv(3000).nfrelease(0.4)   // notch closes slowly after note
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd").nfrelease("<0.05 1.0>")                        // short vs long release per cycle
 * ```
 *
 * @param seconds Release time in seconds; omit to reinterpret the pattern's own values.
 * @return A [PatternMapperFn] that sets the notch filter release time, or [SprudelPattern] when called on a pattern.
 * @param-tool seconds SprudelNfReleaseSequenceEditor
 * @alias nfr
 * @category effects
 * @tags nfrelease, nfr, notch filter, envelope, release
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.nfrelease(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyNfrelease(this, listOfNotNull(seconds).asSprudelDslArgs(callInfo))

/** Sets the notch filter envelope release time on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.nfrelease(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).nfrelease(seconds, callInfo)

/** Creates a [PatternMapperFn] that sets the notch filter envelope release time. */
@SprudelDsl
@KlangScript.Function
fun nfrelease(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.nfrelease(seconds, callInfo) }

/** Creates a chained [PatternMapperFn] that sets the notch filter envelope release time after the previous mapper. */
@SprudelDsl
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
 * @param-tool seconds SprudelNfReleaseSequenceEditor
 * @alias nfrelease
 * @category effects
 * @tags nfr, nfrelease, notch filter, envelope, release
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.nfr(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.nfrelease(seconds, callInfo)

/** Alias for [nfrelease] on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.nfr(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).nfr(seconds, callInfo)

/** Creates a [PatternMapperFn] that sets the notch filter envelope release time (alias for [nfrelease]). */
@SprudelDsl
@KlangScript.Function
fun nfr(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    nfrelease(seconds, callInfo)

/** Creates a chained [PatternMapperFn] that sets notch filter release (alias for [nfrelease]) after the previous mapper. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.nfr(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.nfrelease(seconds, callInfo)

// -- nfenv() / nfe() - Notch Filter Envelope Depth ---------------------------------------------------------------------

private val nfenvMutation = voiceModifier { copy(nfenv = it?.asDoubleOrNull()) }

private fun applyNfenv(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, nfenvMutation)
}

/**
 * Sets the notch filter envelope depth (modulation amount).
 *
 * Controls how far above the base [notchf] centre frequency the notch sweeps when the ADSR envelope
 * is fully open. The depth is a multiplier applied to the base cutoff:
 *
 * ```
 * newCutoff = baseCutoff × (1 + depth × envelopeValue)
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
 * Example with `notchf(500).nfenv(3.0).nfattack(0.01).nfdecay(0.5).nfsustain(0.2).nfrelease(0.3)`:
 *
 * | Phase | envValue | Centre freq |
 * |-------|----------|-------------|
 * | Note start | 0.0 | 500 Hz |
 * | Attack peak | 1.0 | 500 × (1 + 3 × 1) = **2000 Hz** |
 * | Sustain | 0.2 | 500 × (1 + 3 × 0.2) = **800 Hz** |
 * | Release end | 0.0 | 500 Hz |
 *
 * ```KlangScript(Playable)
 * note("c4").notchf(1000).nfenv(3.0)              // notch sweeps up to 4000 Hz at peak
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd").notchf(500).nfenv("<1.0 5.0>")           // subtle vs dramatic sweep per cycle
 * ```
 *
 * @param depth Envelope depth as a ratio (e.g. 1.0 = one octave sweep); omit to reinterpret the pattern's own values.
 * @return A [PatternMapperFn] that sets the notch filter envelope depth, or [SprudelPattern] when called on a pattern.
 * @param-tool depth SprudelNfEnvSequenceEditor
 * @alias nfe
 * @category effects
 * @tags nfenv, nfe, notch filter, envelope, depth, modulation
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.nfenv(depth: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyNfenv(this, listOfNotNull(depth).asSprudelDslArgs(callInfo))

/** Sets the notch filter envelope depth/amount on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.nfenv(depth: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).nfenv(depth, callInfo)

/** Creates a [PatternMapperFn] that sets the notch filter envelope depth. */
@SprudelDsl
@KlangScript.Function
fun nfenv(depth: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.nfenv(depth, callInfo) }

/** Creates a chained [PatternMapperFn] that sets the notch filter envelope depth after the previous mapper. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.nfenv(depth: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.nfenv(depth, callInfo) }

/**
 * Alias for [nfenv]. Sets the notch filter envelope depth.
 *
 * ```KlangScript(Playable)
 * note("c4").notchf(500).nfe(3.0)   // alias for nfenv()
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4").apply(notchf(500).nfe(3.0))   // chained PatternMapperFn
 * ```
 *
 * @param depth Envelope depth as a ratio (e.g. 1.0 = one octave sweep); omit to reinterpret the pattern's own values.
 * @return A [PatternMapperFn] that sets the notch filter envelope depth, or [SprudelPattern] when called on a pattern.
 * @param-tool depth SprudelNfEnvSequenceEditor
 * @alias nfenv
 * @category effects
 * @tags nfe, nfenv, notch filter, envelope, depth, modulation
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.nfe(depth: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.nfenv(depth, callInfo)

/** Alias for [nfenv] on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.nfe(depth: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).nfe(depth, callInfo)

/** Creates a [PatternMapperFn] that sets the notch filter envelope depth (alias for [nfenv]). */
@SprudelDsl
@KlangScript.Function
fun nfe(depth: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    nfenv(depth, callInfo)

/** Creates a chained [PatternMapperFn] that sets notch filter envelope depth (alias for [nfenv]) after the previous mapper. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.nfe(depth: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.nfenv(depth, callInfo)
