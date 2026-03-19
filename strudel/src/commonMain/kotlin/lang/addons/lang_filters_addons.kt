@file:Suppress("DuplicatedCode", "ObjectPropertyName")

package io.peekandpoke.klang.strudel.lang.addons

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel._applyControlFromParams
import io.peekandpoke.klang.strudel._liftOrReinterpretNumericalField
import io.peekandpoke.klang.strudel.lang.*
import io.peekandpoke.klang.strudel.lang.StrudelDslArg.Companion.asStrudelDslArgs

/**
 * ADDONS: Filter functions that are NOT available in the original strudel impl
 */

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in StrudelRegistry.
 */
var strudelLangFiltersAddonsInit = false

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

fun applyNotchf(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
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

internal val _notchf by dslPatternMapper { args, callInfo -> { p -> p._notchf(args, callInfo) } }
internal val StrudelPattern._notchf by dslPatternExtension { p, args, _ -> applyNotchf(p, args) }
internal val String._notchf by dslStringExtension { p, args, callInfo -> p._notchf(args, callInfo) }
internal val PatternMapperFn._notchf by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_notchf(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

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
 * ```KlangScript
 * note("c4 e4").notchf(1000)           // notch out 1 kHz
 * ```
 *
 * ```KlangScript
 * s("bd sd").notchf("<500 2000>")      // alternating notch centre per cycle
 * ```
 *
 * ```KlangScript
 * seq("500 1000 2000").notchf()        // reinterpret values as notch centre
 * ```
 *
 * @param-tool freq StrudelNotchFilterSequenceEditor
 * @category effects
 * @tags notchf, notch filter, filter, frequency
 */
@StrudelDsl
fun StrudelPattern.notchf(freq: PatternLike? = null): StrudelPattern =
    this._notchf(listOfNotNull(freq).asStrudelDslArgs())

/**
 * Parses this string as a pattern, then applies a Notch Filter.
 *
 * @param freq The centre frequency in Hz. Omit to reinterpret the pattern's values as centre frequency.
 * @return A new pattern with notch filter applied.
 *
 * ```KlangScript
 * "c4 e4".notchf(1000).note()          // notch filter on string pattern
 * ```
 *
 * @category effects
 * @tags notchf, notch filter, filter, frequency
 */
@StrudelDsl
fun String.notchf(freq: PatternLike? = null): StrudelPattern =
    this._notchf(listOfNotNull(freq).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that applies a Notch Filter.
 *
 * @param freq The centre frequency in Hz. Omit to reinterpret the pattern's values as centre frequency.
 * @return A [PatternMapperFn] that applies a notch filter.
 *
 * ```KlangScript
 * note("c4 e4").apply(notchf(1000))                   // apply notch via mapper
 * ```
 *
 * ```KlangScript
 * note("c4*4").firstOf(4, notchf(500).nresonance(10)) // resonant notch on first cycle
 * ```
 *
 * @category effects
 * @tags notchf, notch filter, filter, frequency
 */
@StrudelDsl
fun notchf(freq: PatternLike? = null): PatternMapperFn = _notchf(listOfNotNull(freq).asStrudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that applies a Notch Filter after the previous mapper.
 *
 * @param freq The centre frequency in Hz. Omit to reinterpret the pattern's values as centre frequency.
 * @return A new [PatternMapperFn] chaining the notch filter after the previous mapper.
 *
 * ```KlangScript
 * note("c4 e4").apply(gain(0.8).notchf(1000))         // gain then notch filter
 * ```
 *
 * ```KlangScript
 * note("c3*4").firstOf(4, notchf(500).nresonance(10)) // notch chain
 * ```
 */
@StrudelDsl
fun PatternMapperFn.notchf(freq: PatternLike? = null): PatternMapperFn =
    _notchf(listOfNotNull(freq).asStrudelDslArgs())

// -- nresonance() / nres() - Notch Filter resonance ------------------------------------------------------------------

private val nresonanceMutation = voiceModifier { copy(nresonance = it?.asDoubleOrNull()) }

fun applyNresonance(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftOrReinterpretNumericalField(args, nresonanceMutation)
}

internal val _nresonance by dslPatternMapper { args, callInfo -> { p -> p._nresonance(args, callInfo) } }
internal val StrudelPattern._nresonance by dslPatternExtension { p, args, _ -> applyNresonance(p, args) }
internal val String._nresonance by dslStringExtension { p, args, callInfo -> p._nresonance(args, callInfo) }
internal val PatternMapperFn._nresonance by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_nresonance(args, callInfo))
}

internal val _notchq by dslPatternMapper { args, callInfo -> { p -> p._notchq(args, callInfo) } }
internal val StrudelPattern._notchq by dslPatternExtension { p, args, _ -> applyNresonance(p, args) }
internal val String._notchq by dslStringExtension { p, args, callInfo -> p._notchq(args, callInfo) }
internal val PatternMapperFn._notchq by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_notchq(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

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
 * ```KlangScript
 * note("c4").notchf(1000).nresonance(10)   // narrow deep notch at 1 kHz
 * ```
 *
 * ```KlangScript
 * s("bd").notchf(500).nresonance("<5 20>") // Q sweeps from wide to narrow per cycle
 * ```
 *
 * ```KlangScript
 * seq("1 5 15").nresonance()               // reinterpret values as notch Q
 * ```
 *
 * @param-tool q StrudelNResonanceSequenceEditor
 * @alias nres
 * @category effects
 * @tags nresonance, nres, notch filter, Q, resonance
 */
@StrudelDsl
fun StrudelPattern.nresonance(q: PatternLike? = null): StrudelPattern =
    this._nresonance(listOfNotNull(q).asStrudelDslArgs())

/**
 * Parses this string as a pattern, then sets notch filter resonance.
 *
 * @param q The Q factor. Omit to reinterpret the pattern's values as Q.
 * @return A new pattern with notch resonance applied.
 *
 * ```KlangScript
 * "c4".notchf(1000).nresonance(10)  // notch Q on string pattern
 * ```
 *
 * @alias nres
 * @category effects
 * @tags nresonance, nres, notch filter, Q, resonance
 */
@StrudelDsl
fun String.nresonance(q: PatternLike? = null): StrudelPattern =
    this._nresonance(listOfNotNull(q).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets notch filter resonance.
 *
 * @param q The Q factor. Omit to reinterpret the pattern's values as Q.
 * @return A [PatternMapperFn] that applies notch resonance.
 *
 * ```KlangScript
 * note("c4 e4").apply(notchf(1000).nresonance(10))   // notch + Q chain
 * ```
 *
 * ```KlangScript
 * note("c4*4").firstOf(4, nresonance(15))            // narrow notch on first cycle
 * ```
 *
 * @alias nres
 * @category effects
 * @tags nresonance, nres, notch filter, Q, resonance
 */
@StrudelDsl
fun nresonance(q: PatternLike? = null): PatternMapperFn = _nresonance(listOfNotNull(q).asStrudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets notch resonance after the previous mapper.
 *
 * @param q The Q factor. Omit to reinterpret the pattern's values as Q.
 * @return A new [PatternMapperFn] chaining notch resonance after the previous mapper.
 *
 * ```KlangScript
 * note("c4 e4").apply(notchf(500).nresonance(10))    // notchf then resonance
 * ```
 *
 * ```KlangScript
 * note("c3*4").firstOf(4, notchf(1000).nresonance(15))  // narrow notch chain
 * ```
 */
@StrudelDsl
fun PatternMapperFn.nresonance(q: PatternLike? = null): PatternMapperFn =
    _nresonance(listOfNotNull(q).asStrudelDslArgs())

/**
 * Alias for [nresonance]. Sets the notch filter Q.
 *
 * @param q The Q factor. Omit to reinterpret the pattern's values as Q.
 * @return A new pattern with notch resonance applied.
 *
 * ```KlangScript
 * note("c4").notchf(500).nres(10)   // alias for nresonance
 * ```
 *
 * ```KlangScript
 * note("c4").nres("<5 20>")         // sweeping notch Q
 * ```
 *
 * @param-tool q StrudelNotchQSequenceEditor
 * @alias nresonance
 * @category effects
 * @tags nres, nresonance, notch filter, Q
 */
@StrudelDsl
fun StrudelPattern.notchq(q: PatternLike? = null): StrudelPattern =
    this._notchq(listOfNotNull(q).asStrudelDslArgs())

/**
 * Alias for [nresonance] on a string pattern.
 *
 * @param q The Q factor.
 * @return A new pattern with notch resonance applied.
 *
 * ```KlangScript
 * "c4".notchf(500).nres(10)         // alias for String.nresonance
 * ```
 */
@StrudelDsl
fun String.notchq(q: PatternLike? = null): StrudelPattern =
    this._notchq(listOfNotNull(q).asStrudelDslArgs())

/**
 * Alias for [nresonance]. Returns a [PatternMapperFn] that sets notch filter resonance.
 *
 * @param q The Q factor.
 * @return A [PatternMapperFn] that applies notch resonance.
 *
 * ```KlangScript
 * note("c4 e4").apply(notchf(1000).nres(10))  // alias for nresonance()
 * ```
 *
 * ```KlangScript
 * note("c3*4").firstOf(4, nres(15))           // narrow notch on first cycle
 * ```
 *
 * @alias nresonance
 * @category effects
 * @tags nres, nresonance, notch filter, Q
 */
@StrudelDsl
fun notchq(q: PatternLike? = null): PatternMapperFn = _notchq(listOfNotNull(q).asStrudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets notch resonance (alias for [nresonance]) after the previous mapper.
 *
 * @param q The Q factor.
 * @return A new [PatternMapperFn] chaining notch resonance after the previous mapper.
 *
 * ```KlangScript
 * note("c4 e4").apply(notchf(500).nres(10))   // notchf then nres
 * ```
 *
 * ```KlangScript
 * note("c3*4").firstOf(4, notchf(1000).nres(15))  // chain
 * ```
 */
@StrudelDsl
fun PatternMapperFn.notchq(q: PatternLike? = null): PatternMapperFn =
    _notchq(listOfNotNull(q).asStrudelDslArgs())

// -- nfattack() / nfa() - Notch Filter Envelope Attack -----------------------------------------------------------------

private val nfattackMutation = voiceModifier { copy(nfattack = it?.asDoubleOrNull()) }

fun applyNfattack(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftOrReinterpretNumericalField(args, nfattackMutation)
}

internal val _nfattack by dslPatternMapper { args, callInfo -> { p -> p._nfattack(args, callInfo) } }
internal val StrudelPattern._nfattack by dslPatternExtension { p, args, _ -> applyNfattack(p, args) }
internal val String._nfattack by dslStringExtension { p, args, callInfo -> p._nfattack(args, callInfo) }
internal val PatternMapperFn._nfattack by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_nfattack(args, callInfo))
}

internal val _nfa by dslPatternMapper { args, callInfo -> { p -> p._nfa(args, callInfo) } }
internal val StrudelPattern._nfa by dslPatternExtension { p, args, _ -> applyNfattack(p, args) }
internal val String._nfa by dslStringExtension { p, args, callInfo -> p._nfa(args, callInfo) }
internal val PatternMapperFn._nfa by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_nfa(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the notch filter envelope attack time in seconds.
 *
 * Controls how quickly the notch filter centre frequency sweeps from its baseline to the
 * peak at note onset. Use with [nfenv], [nfdecay], [nfsustain], [nfrelease].
 *
 * ```KlangScript
 * note("c4").notchf(1000).nfenv(3000).nfattack(0.1)   // notch sweeps open over 100 ms
 * ```
 *
 * ```KlangScript
 * s("bd").nfattack("<0.01 0.5>")                        // fast vs slow attack per cycle
 * ```
 *
 * @param seconds Attack time in seconds; omit to reinterpret the pattern's own values.
 * @return A [PatternMapperFn] that sets the notch filter attack time, or [StrudelPattern] when called on a pattern.
 * @param-tool seconds StrudelNfAttackSequenceEditor
 * @alias nfa
 * @category effects
 * @tags nfattack, nfa, notch filter, envelope, attack
 */
@StrudelDsl
fun StrudelPattern.nfattack(seconds: PatternLike? = null): StrudelPattern =
    this._nfattack(listOfNotNull(seconds).asStrudelDslArgs())

/** Sets the notch filter envelope attack time on a string pattern. */
@StrudelDsl
fun String.nfattack(seconds: PatternLike? = null): StrudelPattern =
    this._nfattack(listOfNotNull(seconds).asStrudelDslArgs())

/** Creates a [PatternMapperFn] that sets the notch filter envelope attack time. */
@StrudelDsl
fun nfattack(seconds: PatternLike? = null): PatternMapperFn = _nfattack(listOfNotNull(seconds).asStrudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets the notch filter envelope attack time after the previous mapper. */
@StrudelDsl
fun PatternMapperFn.nfattack(seconds: PatternLike? = null): PatternMapperFn =
    _nfattack(listOfNotNull(seconds).asStrudelDslArgs())

/**
 * Alias for [nfattack]. Sets the notch filter envelope attack time.
 *
 * ```KlangScript
 * note("c4").notchf(1000).nfa(0.1)   // alias for nfattack()
 * ```
 *
 * ```KlangScript
 * note("c4").apply(notchf(1000).nfa(0.1))   // chained PatternMapperFn
 * ```
 *
 * @param seconds Attack time in seconds; omit to reinterpret the pattern's own values.
 * @return A [PatternMapperFn] that sets the notch filter attack time, or [StrudelPattern] when called on a pattern.
 * @param-tool seconds StrudelNfAttackSequenceEditor
 * @alias nfattack
 * @category effects
 * @tags nfa, nfattack, notch filter, envelope, attack
 */
@StrudelDsl
fun StrudelPattern.nfa(seconds: PatternLike? = null): StrudelPattern =
    this._nfa(listOfNotNull(seconds).asStrudelDslArgs())

/** Alias for [nfattack] on a string pattern. */
@StrudelDsl
fun String.nfa(seconds: PatternLike? = null): StrudelPattern =
    this._nfa(listOfNotNull(seconds).asStrudelDslArgs())

/** Creates a [PatternMapperFn] that sets the notch filter envelope attack time (alias for [nfattack]). */
@StrudelDsl
fun nfa(seconds: PatternLike? = null): PatternMapperFn = _nfa(listOfNotNull(seconds).asStrudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets notch filter attack (alias for [nfattack]) after the previous mapper. */
@StrudelDsl
fun PatternMapperFn.nfa(seconds: PatternLike? = null): PatternMapperFn =
    _nfa(listOfNotNull(seconds).asStrudelDslArgs())

// -- nfdecay() / nfd() - Notch Filter Envelope Decay -------------------------------------------------------------------

private val nfdecayMutation = voiceModifier { copy(nfdecay = it?.asDoubleOrNull()) }

fun applyNfdecay(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftOrReinterpretNumericalField(args, nfdecayMutation)
}

internal val _nfdecay by dslPatternMapper { args, callInfo -> { p -> p._nfdecay(args, callInfo) } }
internal val StrudelPattern._nfdecay by dslPatternExtension { p, args, _ -> applyNfdecay(p, args) }
internal val String._nfdecay by dslStringExtension { p, args, callInfo -> p._nfdecay(args, callInfo) }
internal val PatternMapperFn._nfdecay by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_nfdecay(args, callInfo))
}

internal val _nfd by dslPatternMapper { args, callInfo -> { p -> p._nfd(args, callInfo) } }
internal val StrudelPattern._nfd by dslPatternExtension { p, args, _ -> applyNfdecay(p, args) }
internal val String._nfd by dslStringExtension { p, args, callInfo -> p._nfd(args, callInfo) }
internal val PatternMapperFn._nfd by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_nfd(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the notch filter envelope decay time in seconds.
 *
 * Controls how quickly the notch filter centre frequency moves from peak to sustain level
 * after the attack. Use with [nfattack], [nfsustain], [nfrelease], [nfenv].
 *
 * ```KlangScript
 * note("c4").notchf(1000).nfenv(3000).nfdecay(0.2)   // notch decays over 200 ms
 * ```
 *
 * ```KlangScript
 * s("bd").nfdecay("<0.05 0.5>")                        // short vs long decay per cycle
 * ```
 *
 * @param seconds Decay time in seconds; omit to reinterpret the pattern's own values.
 * @return A [PatternMapperFn] that sets the notch filter decay time, or [StrudelPattern] when called on a pattern.
 * @param-tool seconds StrudelNfDecaySequenceEditor
 * @alias nfd
 * @category effects
 * @tags nfdecay, nfd, notch filter, envelope, decay
 */
@StrudelDsl
fun StrudelPattern.nfdecay(seconds: PatternLike? = null): StrudelPattern =
    this._nfdecay(listOfNotNull(seconds).asStrudelDslArgs())

/** Sets the notch filter envelope decay time on a string pattern. */
@StrudelDsl
fun String.nfdecay(seconds: PatternLike? = null): StrudelPattern =
    this._nfdecay(listOfNotNull(seconds).asStrudelDslArgs())

/** Creates a [PatternMapperFn] that sets the notch filter envelope decay time. */
@StrudelDsl
fun nfdecay(seconds: PatternLike? = null): PatternMapperFn = _nfdecay(listOfNotNull(seconds).asStrudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets the notch filter envelope decay time after the previous mapper. */
@StrudelDsl
fun PatternMapperFn.nfdecay(seconds: PatternLike? = null): PatternMapperFn =
    _nfdecay(listOfNotNull(seconds).asStrudelDslArgs())

/**
 * Alias for [nfdecay]. Sets the notch filter envelope decay time.
 *
 * ```KlangScript
 * note("c4").notchf(1000).nfd(0.2)   // alias for nfdecay()
 * ```
 *
 * ```KlangScript
 * note("c4").apply(notchf(1000).nfd(0.2))   // chained PatternMapperFn
 * ```
 *
 * @param seconds Decay time in seconds; omit to reinterpret the pattern's own values.
 * @return A [PatternMapperFn] that sets the notch filter decay time, or [StrudelPattern] when called on a pattern.
 * @param-tool seconds StrudelNfDecaySequenceEditor
 * @alias nfdecay
 * @category effects
 * @tags nfd, nfdecay, notch filter, envelope, decay
 */
@StrudelDsl
fun StrudelPattern.nfd(seconds: PatternLike? = null): StrudelPattern =
    this._nfd(listOfNotNull(seconds).asStrudelDslArgs())

/** Alias for [nfdecay] on a string pattern. */
@StrudelDsl
fun String.nfd(seconds: PatternLike? = null): StrudelPattern =
    this._nfd(listOfNotNull(seconds).asStrudelDslArgs())

/** Creates a [PatternMapperFn] that sets the notch filter envelope decay time (alias for [nfdecay]). */
@StrudelDsl
fun nfd(seconds: PatternLike? = null): PatternMapperFn = _nfd(listOfNotNull(seconds).asStrudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets notch filter decay (alias for [nfdecay]) after the previous mapper. */
@StrudelDsl
fun PatternMapperFn.nfd(seconds: PatternLike? = null): PatternMapperFn =
    _nfd(listOfNotNull(seconds).asStrudelDslArgs())

// -- nfsustain() / nfs() - Notch Filter Envelope Sustain ---------------------------------------------------------------

private val nfsustainMutation = voiceModifier { copy(nfsustain = it?.asDoubleOrNull()) }

fun applyNfsustain(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftOrReinterpretNumericalField(args, nfsustainMutation)
}

internal val _nfsustain by dslPatternMapper { args, callInfo -> { p -> p._nfsustain(args, callInfo) } }
internal val StrudelPattern._nfsustain by dslPatternExtension { p, args, _ -> applyNfsustain(p, args) }
internal val String._nfsustain by dslStringExtension { p, args, callInfo -> p._nfsustain(args, callInfo) }
internal val PatternMapperFn._nfsustain by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_nfsustain(args, callInfo))
}

internal val _nfs by dslPatternMapper { args, callInfo -> { p -> p._nfs(args, callInfo) } }
internal val StrudelPattern._nfs by dslPatternExtension { p, args, _ -> applyNfsustain(p, args) }
internal val String._nfs by dslStringExtension { p, args, callInfo -> p._nfs(args, callInfo) }
internal val PatternMapperFn._nfs by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_nfs(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the notch filter envelope sustain level (0–1).
 *
 * Controls the notch centre frequency level during the sustained portion of the note.
 * `1` holds the notch at the envelope peak; `0` returns to baseline. Use with
 * [nfattack]/[nfdecay]/[nfrelease].
 *
 * ```KlangScript
 * note("c4").notchf(1000).nfenv(3000).nfsustain(0.5)  // sustain at half depth
 * ```
 *
 * ```KlangScript
 * note("c4").nfsustain("<0 1>")                         // closed vs fully open sustain
 * ```
 *
 * @param level Sustain level (0–1); omit to reinterpret the pattern's own values.
 * @return A [PatternMapperFn] that sets the notch filter sustain level, or [StrudelPattern] when called on a pattern.
 * @param-tool level StrudelNfSustainSequenceEditor
 * @alias nfs
 * @category effects
 * @tags nfsustain, nfs, notch filter, envelope, sustain
 */
@StrudelDsl
fun StrudelPattern.nfsustain(level: PatternLike? = null): StrudelPattern =
    this._nfsustain(listOfNotNull(level).asStrudelDslArgs())

/** Sets the notch filter envelope sustain level on a string pattern. */
@StrudelDsl
fun String.nfsustain(level: PatternLike? = null): StrudelPattern =
    this._nfsustain(listOfNotNull(level).asStrudelDslArgs())

/** Creates a [PatternMapperFn] that sets the notch filter envelope sustain level. */
@StrudelDsl
fun nfsustain(level: PatternLike? = null): PatternMapperFn = _nfsustain(listOfNotNull(level).asStrudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets the notch filter sustain level after the previous mapper. */
@StrudelDsl
fun PatternMapperFn.nfsustain(level: PatternLike? = null): PatternMapperFn =
    _nfsustain(listOfNotNull(level).asStrudelDslArgs())

/**
 * Alias for [nfsustain]. Sets the notch filter envelope sustain level.
 *
 * ```KlangScript
 * note("c4").notchf(1000).nfs(0.5)   // alias for nfsustain()
 * ```
 *
 * ```KlangScript
 * note("c4").apply(notchf(1000).nfs(0.5))   // chained PatternMapperFn
 * ```
 *
 * @param level Sustain level (0–1); omit to reinterpret the pattern's own values.
 * @return A [PatternMapperFn] that sets the notch filter sustain level, or [StrudelPattern] when called on a pattern.
 * @param-tool level StrudelNfSustainSequenceEditor
 * @alias nfsustain
 * @category effects
 * @tags nfs, nfsustain, notch filter, envelope, sustain
 */
@StrudelDsl
fun StrudelPattern.nfs(level: PatternLike? = null): StrudelPattern =
    this._nfs(listOfNotNull(level).asStrudelDslArgs())

/** Alias for [nfsustain] on a string pattern. */
@StrudelDsl
fun String.nfs(level: PatternLike? = null): StrudelPattern =
    this._nfs(listOfNotNull(level).asStrudelDslArgs())

/** Creates a [PatternMapperFn] that sets the notch filter envelope sustain level (alias for [nfsustain]). */
@StrudelDsl
fun nfs(level: PatternLike? = null): PatternMapperFn = _nfs(listOfNotNull(level).asStrudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets notch filter sustain (alias for [nfsustain]) after the previous mapper. */
@StrudelDsl
fun PatternMapperFn.nfs(level: PatternLike? = null): PatternMapperFn =
    _nfs(listOfNotNull(level).asStrudelDslArgs())

// -- nfrelease() / nfr() - Notch Filter Envelope Release ---------------------------------------------------------------

private val nfreleaseMutation = voiceModifier { copy(nfrelease = it?.asDoubleOrNull()) }

fun applyNfrelease(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftOrReinterpretNumericalField(args, nfreleaseMutation)
}

internal val _nfrelease by dslPatternMapper { args, callInfo -> { p -> p._nfrelease(args, callInfo) } }
internal val StrudelPattern._nfrelease by dslPatternExtension { p, args, _ -> applyNfrelease(p, args) }
internal val String._nfrelease by dslStringExtension { p, args, callInfo -> p._nfrelease(args, callInfo) }
internal val PatternMapperFn._nfrelease by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_nfrelease(args, callInfo))
}

internal val _nfr by dslPatternMapper { args, callInfo -> { p -> p._nfr(args, callInfo) } }
internal val StrudelPattern._nfr by dslPatternExtension { p, args, _ -> applyNfrelease(p, args) }
internal val String._nfr by dslStringExtension { p, args, callInfo -> p._nfr(args, callInfo) }
internal val PatternMapperFn._nfr by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_nfr(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the notch filter envelope release time in seconds.
 *
 * Controls how quickly the notch filter centre frequency returns to baseline after the
 * note ends. Use with [nfattack], [nfdecay], [nfsustain], [nfenv].
 *
 * ```KlangScript
 * note("c4").notchf(1000).nfenv(3000).nfrelease(0.4)   // notch closes slowly after note
 * ```
 *
 * ```KlangScript
 * s("bd").nfrelease("<0.05 1.0>")                        // short vs long release per cycle
 * ```
 *
 * @param seconds Release time in seconds; omit to reinterpret the pattern's own values.
 * @return A [PatternMapperFn] that sets the notch filter release time, or [StrudelPattern] when called on a pattern.
 * @param-tool seconds StrudelNfReleaseSequenceEditor
 * @alias nfr
 * @category effects
 * @tags nfrelease, nfr, notch filter, envelope, release
 */
@StrudelDsl
fun StrudelPattern.nfrelease(seconds: PatternLike? = null): StrudelPattern =
    this._nfrelease(listOfNotNull(seconds).asStrudelDslArgs())

/** Sets the notch filter envelope release time on a string pattern. */
@StrudelDsl
fun String.nfrelease(seconds: PatternLike? = null): StrudelPattern =
    this._nfrelease(listOfNotNull(seconds).asStrudelDslArgs())

/** Creates a [PatternMapperFn] that sets the notch filter envelope release time. */
@StrudelDsl
fun nfrelease(seconds: PatternLike? = null): PatternMapperFn = _nfrelease(listOfNotNull(seconds).asStrudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets the notch filter envelope release time after the previous mapper. */
@StrudelDsl
fun PatternMapperFn.nfrelease(seconds: PatternLike? = null): PatternMapperFn =
    _nfrelease(listOfNotNull(seconds).asStrudelDslArgs())

/**
 * Alias for [nfrelease]. Sets the notch filter envelope release time.
 *
 * ```KlangScript
 * note("c4").notchf(1000).nfr(0.4)   // alias for nfrelease()
 * ```
 *
 * ```KlangScript
 * note("c4").apply(notchf(1000).nfr(0.4))   // chained PatternMapperFn
 * ```
 *
 * @param seconds Release time in seconds; omit to reinterpret the pattern's own values.
 * @return A [PatternMapperFn] that sets the notch filter release time, or [StrudelPattern] when called on a pattern.
 * @param-tool seconds StrudelNfReleaseSequenceEditor
 * @alias nfrelease
 * @category effects
 * @tags nfr, nfrelease, notch filter, envelope, release
 */
@StrudelDsl
fun StrudelPattern.nfr(seconds: PatternLike? = null): StrudelPattern =
    this._nfr(listOfNotNull(seconds).asStrudelDslArgs())

/** Alias for [nfrelease] on a string pattern. */
@StrudelDsl
fun String.nfr(seconds: PatternLike? = null): StrudelPattern =
    this._nfr(listOfNotNull(seconds).asStrudelDslArgs())

/** Creates a [PatternMapperFn] that sets the notch filter envelope release time (alias for [nfrelease]). */
@StrudelDsl
fun nfr(seconds: PatternLike? = null): PatternMapperFn = _nfr(listOfNotNull(seconds).asStrudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets notch filter release (alias for [nfrelease]) after the previous mapper. */
@StrudelDsl
fun PatternMapperFn.nfr(seconds: PatternLike? = null): PatternMapperFn =
    _nfr(listOfNotNull(seconds).asStrudelDslArgs())

// -- nfenv() / nfe() - Notch Filter Envelope Depth ---------------------------------------------------------------------

private val nfenvMutation = voiceModifier { copy(nfenv = it?.asDoubleOrNull()) }

fun applyNfenv(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftOrReinterpretNumericalField(args, nfenvMutation)
}

internal val _nfenv by dslPatternMapper { args, callInfo -> { p -> p._nfenv(args, callInfo) } }
internal val StrudelPattern._nfenv by dslPatternExtension { p, args, _ -> applyNfenv(p, args) }
internal val String._nfenv by dslStringExtension { p, args, callInfo -> p._nfenv(args, callInfo) }
internal val PatternMapperFn._nfenv by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_nfenv(args, callInfo))
}

internal val _nfe by dslPatternMapper { args, callInfo -> { p -> p._nfe(args, callInfo) } }
internal val StrudelPattern._nfe by dslPatternExtension { p, args, _ -> applyNfenv(p, args) }
internal val String._nfe by dslStringExtension { p, args, callInfo -> p._nfe(args, callInfo) }
internal val PatternMapperFn._nfe by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_nfe(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

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
 * ```KlangScript
 * note("c4").notchf(1000).nfenv(3.0)              // notch sweeps up to 4000 Hz at peak
 * ```
 *
 * ```KlangScript
 * s("bd").notchf(500).nfenv("<1.0 5.0>")           // subtle vs dramatic sweep per cycle
 * ```
 *
 * @param depth Envelope depth as a ratio (e.g. 1.0 = one octave sweep); omit to reinterpret the pattern's own values.
 * @return A [PatternMapperFn] that sets the notch filter envelope depth, or [StrudelPattern] when called on a pattern.
 * @param-tool depth StrudelNfEnvSequenceEditor
 * @alias nfe
 * @category effects
 * @tags nfenv, nfe, notch filter, envelope, depth, modulation
 */
@StrudelDsl
fun StrudelPattern.nfenv(depth: PatternLike? = null): StrudelPattern =
    this._nfenv(listOfNotNull(depth).asStrudelDslArgs())

/** Sets the notch filter envelope depth/amount on a string pattern. */
@StrudelDsl
fun String.nfenv(depth: PatternLike? = null): StrudelPattern =
    this._nfenv(listOfNotNull(depth).asStrudelDslArgs())

/** Creates a [PatternMapperFn] that sets the notch filter envelope depth. */
@StrudelDsl
fun nfenv(depth: PatternLike? = null): PatternMapperFn = _nfenv(listOfNotNull(depth).asStrudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets the notch filter envelope depth after the previous mapper. */
@StrudelDsl
fun PatternMapperFn.nfenv(depth: PatternLike? = null): PatternMapperFn =
    _nfenv(listOfNotNull(depth).asStrudelDslArgs())

/**
 * Alias for [nfenv]. Sets the notch filter envelope depth.
 *
 * ```KlangScript
 * note("c4").notchf(500).nfe(3.0)   // alias for nfenv()
 * ```
 *
 * ```KlangScript
 * note("c4").apply(notchf(500).nfe(3.0))   // chained PatternMapperFn
 * ```
 *
 * @param depth Envelope depth as a ratio (e.g. 1.0 = one octave sweep); omit to reinterpret the pattern's own values.
 * @return A [PatternMapperFn] that sets the notch filter envelope depth, or [StrudelPattern] when called on a pattern.
 * @param-tool depth StrudelNfEnvSequenceEditor
 * @alias nfenv
 * @category effects
 * @tags nfe, nfenv, notch filter, envelope, depth, modulation
 */
@StrudelDsl
fun StrudelPattern.nfe(depth: PatternLike? = null): StrudelPattern =
    this._nfe(listOfNotNull(depth).asStrudelDslArgs())

/** Alias for [nfenv] on a string pattern. */
@StrudelDsl
fun String.nfe(depth: PatternLike? = null): StrudelPattern =
    this._nfe(listOfNotNull(depth).asStrudelDslArgs())

/** Creates a [PatternMapperFn] that sets the notch filter envelope depth (alias for [nfenv]). */
@StrudelDsl
fun nfe(depth: PatternLike? = null): PatternMapperFn = _nfe(listOfNotNull(depth).asStrudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets notch filter envelope depth (alias for [nfenv]) after the previous mapper. */
@StrudelDsl
fun PatternMapperFn.nfe(depth: PatternLike? = null): PatternMapperFn =
    _nfe(listOfNotNull(depth).asStrudelDslArgs())
