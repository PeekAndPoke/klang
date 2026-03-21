@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel._applyControlFromParams
import io.peekandpoke.klang.sprudel._liftOrReinterpretNumericalField
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in SprudelRegistry.
 */
var sprudelLangFiltersInit = false

// -- lpf() / cutoff() / ctf() / lp() ---------------------------------------------------------------------------------

private val lpfMutation = voiceModifier {
    val str = it?.toString() ?: return@voiceModifier this
    if (":" in str) {
        val parts = str.split(":").map { d -> d.trim().toDoubleOrNull() }
        copy(
            cutoff = parts.getOrNull(0) ?: cutoff,
            resonance = parts.getOrNull(1) ?: resonance,
            lpenv = parts.getOrNull(2) ?: lpenv,
        )
    } else {
        copy(cutoff = str.toDoubleOrNull())
    }
}

fun applyLpf(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val str = args.firstOrNull()?.value?.toString() ?: ""
    return if (":" in str) {
        source._applyControlFromParams(args, lpfMutation) { src, ctrl ->
            src.copy(
                cutoff = ctrl.cutoff ?: src.cutoff,
                resonance = ctrl.resonance ?: src.resonance,
                lpenv = ctrl.lpenv ?: src.lpenv,
            )
        }
    } else {
        source._liftOrReinterpretNumericalField(args, lpfMutation)
    }
}

internal val _lpf by dslPatternMapper { args, callInfo -> { p -> p._lpf(args, callInfo) } }
internal val SprudelPattern._lpf by dslPatternExtension { p, args, _ -> applyLpf(p, args) }
internal val String._lpf by dslStringExtension { p, args, callInfo -> p._lpf(args, callInfo) }
internal val PatternMapperFn._lpf by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_lpf(args, callInfo))
}

internal val _cutoff by dslPatternMapper { args, callInfo -> { p -> p._cutoff(args, callInfo) } }
internal val SprudelPattern._cutoff by dslPatternExtension { p, args, _ -> applyLpf(p, args) }
internal val String._cutoff by dslStringExtension { p, args, callInfo -> p._cutoff(args, callInfo) }
internal val PatternMapperFn._cutoff by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_cutoff(args, callInfo))
}

internal val _ctf by dslPatternMapper { args, callInfo -> { p -> p._ctf(args, callInfo) } }
internal val SprudelPattern._ctf by dslPatternExtension { p, args, _ -> applyLpf(p, args) }
internal val String._ctf by dslStringExtension { p, args, callInfo -> p._ctf(args, callInfo) }
internal val PatternMapperFn._ctf by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_ctf(args, callInfo))
}

internal val _lp by dslPatternMapper { args, callInfo -> { p -> p._lp(args, callInfo) } }
internal val SprudelPattern._lp by dslPatternExtension { p, args, _ -> applyLpf(p, args) }
internal val String._lp by dslStringExtension { p, args, callInfo -> p._lp(args, callInfo) }
internal val PatternMapperFn._lp by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_lp(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Applies a Low Pass Filter (LPF) with the given cutoff frequency in Hz.
 *
 * Only frequencies below the cutoff pass through. Lower values produce a darker, more
 * muffled sound; higher values let more signal through. Use [resonance] to add emphasis
 * at the cutoff frequency.
 *
 * When [freq] is omitted, the pattern's own numeric values are reinterpreted as cutoff frequencies.
 *
 * @param freq The cutoff frequency in Hz. Omit to reinterpret the pattern's values as cutoff.
 * @return A new pattern with LPF applied.
 *
 * ```KlangScript
 * s("bd sd hh").lpf(500)             // dark, muffled sound
 * ```
 *
 * ```KlangScript
 * note("c4 e4").lpf("<200 2000>")    // alternating cutoff per cycle
 * ```
 *
 * ```KlangScript
 * seq("200 500 1000").lpf()          // reinterpret values as cutoff
 * ```
 *
 * @param-tool freq SprudelLpFilterSequenceEditor
 * @alias cutoff, ctf, lp
 * @category effects
 * @tags lpf, cutoff, low pass filter, filter, frequency
 */
@SprudelDsl
fun SprudelPattern.lpf(freq: PatternLike? = null): SprudelPattern =
    this._lpf(listOfNotNull(freq).asSprudelDslArgs())

/**
 * Parses this string as a pattern, then applies a Low Pass Filter.
 *
 * When [freq] is omitted, the string pattern's values are reinterpreted as cutoff frequencies.
 *
 * @param freq The cutoff frequency in Hz. Omit to reinterpret the pattern's values as cutoff.
 * @return A new pattern with LPF applied.
 *
 * ```KlangScript
 * "c4 e4".lpf(500).note()           // LPF on string pattern
 * ```
 *
 * @alias cutoff, ctf, lp
 * @category effects
 * @tags lpf, cutoff, low pass filter, filter, frequency
 */
@SprudelDsl
fun String.lpf(freq: PatternLike? = null): SprudelPattern =
    this._lpf(listOfNotNull(freq).asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] that applies a Low Pass Filter.
 *
 * Use the returned mapper as a transform argument or apply it to a pattern via `.apply(...)`.
 * When [freq] is omitted, the pattern's own numeric values are reinterpreted as cutoff frequencies.
 *
 * @param freq The cutoff frequency in Hz. Omit to reinterpret the pattern's values as cutoff.
 * @return A [PatternMapperFn] that applies LPF.
 *
 * ```KlangScript
 * note("c4 e4").apply(lpf(500))                     // apply LPF via mapper
 * ```
 *
 * ```KlangScript
 * note("c4*4").firstOf(4, lpf(200).resonance(20))   // resonant LPF on first cycle
 * ```
 *
 * @alias cutoff, ctf, lp
 * @category effects
 * @tags lpf, cutoff, low pass filter, filter, frequency
 */
@SprudelDsl
fun lpf(freq: PatternLike? = null): PatternMapperFn = _lpf(listOfNotNull(freq).asSprudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that applies a Low Pass Filter after the previous mapper.
 *
 * @param freq The cutoff frequency in Hz. Omit to reinterpret the pattern's values as cutoff.
 * @return A new [PatternMapperFn] chaining LPF after the previous mapper.
 *
 * ```KlangScript
 * note("c4 e4").apply(gain(0.8).lpf(500))           // gain then LPF
 * ```
 *
 * ```KlangScript
 * note("c3*4").firstOf(4, lpf(300).resonance(15))   // resonant LPF chain
 * ```
 */
@SprudelDsl
fun PatternMapperFn.lpf(freq: PatternLike? = null): PatternMapperFn =
    _lpf(listOfNotNull(freq).asSprudelDslArgs())

/**
 * Alias for [lpf]. Applies a Low Pass Filter with the given cutoff frequency.
 *
 * When [freq] is omitted, the pattern's own numeric values are reinterpreted as cutoff frequencies.
 *
 * @param freq The cutoff frequency in Hz. Omit to reinterpret the pattern's values as cutoff.
 * @return A new pattern with LPF applied.
 *
 * ```KlangScript
 * note("c4").cutoff(800)            // alias for lpf
 * ```
 *
 * ```KlangScript
 * note("c4").cutoff("<200 2000>")   // alternating cutoff per cycle
 * ```
 *
 * @param-tool freq SprudelLpFilterSequenceEditor
 * @alias lpf, ctf, lp
 * @category effects
 * @tags cutoff, lpf, low pass filter, filter, frequency
 */
@SprudelDsl
fun SprudelPattern.cutoff(freq: PatternLike? = null): SprudelPattern =
    this._cutoff(listOfNotNull(freq).asSprudelDslArgs())

/**
 * Alias for [lpf] on a string pattern.
 *
 * @param freq The cutoff frequency in Hz.
 * @return A new pattern with LPF applied.
 *
 * ```KlangScript
 * "c4 e4".cutoff(500).note()        // alias for String.lpf
 * ```
 */
@SprudelDsl
fun String.cutoff(freq: PatternLike? = null): SprudelPattern =
    this._cutoff(listOfNotNull(freq).asSprudelDslArgs())

/**
 * Alias for [lpf]. Returns a [PatternMapperFn] that applies a Low Pass Filter.
 *
 * @param freq The cutoff frequency in Hz. Omit to reinterpret the pattern's values as cutoff.
 * @return A [PatternMapperFn] that applies LPF.
 *
 * ```KlangScript
 * note("c4 e4").apply(cutoff(500))      // alias for lpf()
 * ```
 *
 * ```KlangScript
 * note("c3*4").firstOf(4, cutoff(200))  // LPF on first cycle
 * ```
 *
 * @alias lpf, ctf, lp
 * @category effects
 * @tags cutoff, lpf, low pass filter, filter, frequency
 */
@SprudelDsl
fun cutoff(freq: PatternLike? = null): PatternMapperFn = _cutoff(listOfNotNull(freq).asSprudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that applies LPF (alias for [lpf]) after the previous mapper.
 *
 * @param freq The cutoff frequency in Hz. Omit to reinterpret the pattern's values as cutoff.
 * @return A new [PatternMapperFn] chaining LPF after the previous mapper.
 *
 * ```KlangScript
 * note("c4").apply(gain(0.8).cutoff(500))           // gain then cutoff
 * ```
 *
 * ```KlangScript
 * note("c3*4").firstOf(4, cutoff(300).resonance(10))  // chain cutoff + resonance
 * ```
 */
@SprudelDsl
fun PatternMapperFn.cutoff(freq: PatternLike? = null): PatternMapperFn =
    _cutoff(listOfNotNull(freq).asSprudelDslArgs())

/**
 * Alias for [lpf]. Applies a Low Pass Filter with the given cutoff frequency.
 *
 * @param freq The cutoff frequency in Hz. Omit to reinterpret the pattern's values as cutoff.
 * @return A new pattern with LPF applied.
 *
 * ```KlangScript
 * note("c4").ctf(600)               // short alias for lpf
 * ```
 *
 * ```KlangScript
 * note("c4").ctf("<100 5000>")      // sweeping cutoff
 * ```
 *
 * @param-tool freq SprudelLpFilterSequenceEditor
 * @alias lpf, cutoff, lp
 * @category effects
 * @tags ctf, lpf, low pass filter, filter, frequency
 */
@SprudelDsl
fun SprudelPattern.ctf(freq: PatternLike? = null): SprudelPattern =
    this._ctf(listOfNotNull(freq).asSprudelDslArgs())

/**
 * Alias for [lpf] on a string pattern.
 *
 * @param freq The cutoff frequency in Hz.
 * @return A new pattern with LPF applied.
 *
 * ```KlangScript
 * "c4 e4".ctf(500).note()           // short alias
 * ```
 */
@SprudelDsl
fun String.ctf(freq: PatternLike? = null): SprudelPattern =
    this._ctf(listOfNotNull(freq).asSprudelDslArgs())

/**
 * Alias for [lpf]. Returns a [PatternMapperFn] that applies a Low Pass Filter.
 *
 * @param freq The cutoff frequency in Hz.
 * @return A [PatternMapperFn] that applies LPF.
 *
 * ```KlangScript
 * note("c4 e4").apply(ctf(500))     // short alias for lpf()
 * ```
 *
 * ```KlangScript
 * note("c3*4").firstOf(4, ctf(200)) // LPF on first cycle
 * ```
 *
 * @alias lpf, cutoff, lp
 * @category effects
 * @tags ctf, lpf, low pass filter, filter, frequency
 */
@SprudelDsl
fun ctf(freq: PatternLike? = null): PatternMapperFn = _ctf(listOfNotNull(freq).asSprudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that applies LPF (alias for [lpf]) after the previous mapper.
 *
 * @param freq The cutoff frequency in Hz.
 * @return A new [PatternMapperFn] chaining LPF after the previous mapper.
 *
 * ```KlangScript
 * note("c4").apply(gain(0.8).ctf(500))              // gain then ctf
 * ```
 *
 * ```KlangScript
 * note("c3*4").firstOf(4, ctf(300).resonance(10))   // chain
 * ```
 */
@SprudelDsl
fun PatternMapperFn.ctf(freq: PatternLike? = null): PatternMapperFn =
    _ctf(listOfNotNull(freq).asSprudelDslArgs())

/**
 * Alias for [lpf]. Applies a Low Pass Filter with the given cutoff frequency.
 *
 * @param freq The cutoff frequency in Hz. Omit to reinterpret the pattern's values as cutoff.
 * @return A new pattern with LPF applied.
 *
 * ```KlangScript
 * note("c4").lp(400)                // short alias for lpf
 * ```
 *
 * ```KlangScript
 * note("c4").lp("<200 3000>")       // sweeping cutoff
 * ```
 *
 * @param-tool freq SprudelLpFilterSequenceEditor
 * @alias lpf, cutoff, ctf
 * @category effects
 * @tags lp, lpf, low pass filter, filter, frequency
 */
@SprudelDsl
fun SprudelPattern.lp(freq: PatternLike? = null): SprudelPattern =
    this._lp(listOfNotNull(freq).asSprudelDslArgs())

/**
 * Alias for [lpf] on a string pattern.
 *
 * @param freq The cutoff frequency in Hz.
 * @return A new pattern with LPF applied.
 *
 * ```KlangScript
 * "c4 e4".lp(500).note()            // alias for String.lpf
 * ```
 */
@SprudelDsl
fun String.lp(freq: PatternLike? = null): SprudelPattern =
    this._lp(listOfNotNull(freq).asSprudelDslArgs())

/**
 * Alias for [lpf]. Returns a [PatternMapperFn] that applies a Low Pass Filter.
 *
 * @param freq The cutoff frequency in Hz.
 * @return A [PatternMapperFn] that applies LPF.
 *
 * ```KlangScript
 * note("c4 e4").apply(lp(500))      // alias for lpf()
 * ```
 *
 * ```KlangScript
 * note("c3*4").firstOf(4, lp(200))  // LPF on first cycle
 * ```
 *
 * @alias lpf, cutoff, ctf
 * @category effects
 * @tags lp, lpf, low pass filter, filter, frequency
 */
@SprudelDsl
fun lp(freq: PatternLike? = null): PatternMapperFn = _lp(listOfNotNull(freq).asSprudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that applies LPF (alias for [lpf]) after the previous mapper.
 *
 * @param freq The cutoff frequency in Hz.
 * @return A new [PatternMapperFn] chaining LPF after the previous mapper.
 *
 * ```KlangScript
 * note("c4").apply(gain(0.8).lp(500))               // gain then lp filter
 * ```
 *
 * ```KlangScript
 * note("c3*4").firstOf(4, lp(300).resonance(10))    // chain
 * ```
 */
@SprudelDsl
fun PatternMapperFn.lp(freq: PatternLike? = null): PatternMapperFn =
    _lp(listOfNotNull(freq).asSprudelDslArgs())

// -- hpf() / hp() / hcutoff() -----------------------------------------------------------------------------------------

private val hpfMutation = voiceModifier {
    val str = it?.toString() ?: return@voiceModifier this
    if (":" in str) {
        val parts = str.split(":").map { d -> d.trim().toDoubleOrNull() }
        copy(
            hcutoff = parts.getOrNull(0) ?: hcutoff,
            hresonance = parts.getOrNull(1) ?: hresonance,
            hpenv = parts.getOrNull(2) ?: hpenv,
        )
    } else {
        copy(hcutoff = str.toDoubleOrNull())
    }
}

fun applyHpf(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val str = args.firstOrNull()?.value?.toString() ?: ""
    return if (":" in str) {
        source._applyControlFromParams(args, hpfMutation) { src, ctrl ->
            src.copy(
                hcutoff = ctrl.hcutoff ?: src.hcutoff,
                hresonance = ctrl.hresonance ?: src.hresonance,
                hpenv = ctrl.hpenv ?: src.hpenv,
            )
        }
    } else {
        source._liftOrReinterpretNumericalField(args, hpfMutation)
    }
}

internal val _hpf by dslPatternMapper { args, callInfo -> { p -> p._hpf(args, callInfo) } }
internal val SprudelPattern._hpf by dslPatternExtension { p, args, _ -> applyHpf(p, args) }
internal val String._hpf by dslStringExtension { p, args, callInfo -> p._hpf(args, callInfo) }
internal val PatternMapperFn._hpf by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_hpf(args, callInfo))
}

internal val _hp by dslPatternMapper { args, callInfo -> { p -> p._hp(args, callInfo) } }
internal val SprudelPattern._hp by dslPatternExtension { p, args, _ -> applyHpf(p, args) }
internal val String._hp by dslStringExtension { p, args, callInfo -> p._hp(args, callInfo) }
internal val PatternMapperFn._hp by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_hp(args, callInfo))
}

internal val _hcutoff by dslPatternMapper { args, callInfo -> { p -> p._hcutoff(args, callInfo) } }
internal val SprudelPattern._hcutoff by dslPatternExtension { p, args, _ -> applyHpf(p, args) }
internal val String._hcutoff by dslStringExtension { p, args, callInfo -> p._hcutoff(args, callInfo) }
internal val PatternMapperFn._hcutoff by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_hcutoff(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Applies a High Pass Filter (HPF) with the given cutoff frequency in Hz.
 *
 * Only frequencies above the cutoff pass through. Higher values produce a thinner, brighter
 * sound by removing low-frequency content. Use [hresonance] to add emphasis at the cutoff.
 *
 * When [freq] is omitted, the pattern's own numeric values are reinterpreted as cutoff frequencies.
 *
 * @param freq The cutoff frequency in Hz. Omit to reinterpret the pattern's values as cutoff.
 * @return A new pattern with HPF applied.
 *
 * ```KlangScript
 * s("bd sd").hpf(300)              // removes bass, thin sound
 * ```
 *
 * ```KlangScript
 * note("c4 e4").hpf("<100 800>")   // alternating HPF cutoff per cycle
 * ```
 *
 * ```KlangScript
 * seq("100 300 800").hpf()         // reinterpret values as HPF cutoff
 * ```
 *
 * @param-tool freq SprudelHpFilterSequenceEditor
 * @alias hp, hcutoff
 * @category effects
 * @tags hpf, hcutoff, high pass filter, filter, frequency
 */
@SprudelDsl
fun SprudelPattern.hpf(freq: PatternLike? = null): SprudelPattern =
    this._hpf(listOfNotNull(freq).asSprudelDslArgs())

/**
 * Parses this string as a pattern, then applies a High Pass Filter.
 *
 * @param freq The cutoff frequency in Hz. Omit to reinterpret the pattern's values as cutoff.
 * @return A new pattern with HPF applied.
 *
 * ```KlangScript
 * "c4 e4".hpf(300).note()          // HPF on string pattern
 * ```
 *
 * @alias hp, hcutoff
 * @category effects
 * @tags hpf, hcutoff, high pass filter, filter, frequency
 */
@SprudelDsl
fun String.hpf(freq: PatternLike? = null): SprudelPattern =
    this._hpf(listOfNotNull(freq).asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] that applies a High Pass Filter.
 *
 * @param freq The cutoff frequency in Hz. Omit to reinterpret the pattern's values as cutoff.
 * @return A [PatternMapperFn] that applies HPF.
 *
 * ```KlangScript
 * note("c4 e4").apply(hpf(300))                     // apply HPF via mapper
 * ```
 *
 * ```KlangScript
 * note("c4*4").firstOf(4, hpf(200).hresonance(10))  // resonant HPF on first cycle
 * ```
 *
 * @alias hp, hcutoff
 * @category effects
 * @tags hpf, hcutoff, high pass filter, filter, frequency
 */
@SprudelDsl
fun hpf(freq: PatternLike? = null): PatternMapperFn = _hpf(listOfNotNull(freq).asSprudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that applies a High Pass Filter after the previous mapper.
 *
 * @param freq The cutoff frequency in Hz. Omit to reinterpret the pattern's values as cutoff.
 * @return A new [PatternMapperFn] chaining HPF after the previous mapper.
 *
 * ```KlangScript
 * note("c4 e4").apply(gain(0.8).hpf(300))           // gain then HPF
 * ```
 *
 * ```KlangScript
 * note("c3*4").firstOf(4, hpf(200).hresonance(15))  // resonant HPF chain
 * ```
 */
@SprudelDsl
fun PatternMapperFn.hpf(freq: PatternLike? = null): PatternMapperFn =
    _hpf(listOfNotNull(freq).asSprudelDslArgs())

/**
 * Alias for [hpf]. Applies a High Pass Filter with the given cutoff frequency.
 *
 * @param freq The cutoff frequency in Hz. Omit to reinterpret the pattern's values as cutoff.
 * @return A new pattern with HPF applied.
 *
 * ```KlangScript
 * note("c4").hp(300)               // alias for hpf
 * ```
 *
 * ```KlangScript
 * note("c4").hp("<100 800>")       // alternating HPF cutoff
 * ```
 *
 * @param-tool freq SprudelHpFilterSequenceEditor
 * @alias hpf, hcutoff
 * @category effects
 * @tags hp, hpf, high pass filter, filter, frequency
 */
@SprudelDsl
fun SprudelPattern.hp(freq: PatternLike? = null): SprudelPattern =
    this._hp(listOfNotNull(freq).asSprudelDslArgs())

/**
 * Alias for [hpf] on a string pattern.
 *
 * @param freq The cutoff frequency in Hz.
 * @return A new pattern with HPF applied.
 *
 * ```KlangScript
 * "c4 e4".hp(300).note()           // alias for String.hpf
 * ```
 */
@SprudelDsl
fun String.hp(freq: PatternLike? = null): SprudelPattern =
    this._hp(listOfNotNull(freq).asSprudelDslArgs())

/**
 * Alias for [hpf]. Returns a [PatternMapperFn] that applies a High Pass Filter.
 *
 * @param freq The cutoff frequency in Hz.
 * @return A [PatternMapperFn] that applies HPF.
 *
 * ```KlangScript
 * note("c4 e4").apply(hp(300))      // alias for hpf()
 * ```
 *
 * ```KlangScript
 * note("c3*4").firstOf(4, hp(200))  // HPF on first cycle
 * ```
 *
 * @alias hpf, hcutoff
 * @category effects
 * @tags hp, hpf, high pass filter, filter, frequency
 */
@SprudelDsl
fun hp(freq: PatternLike? = null): PatternMapperFn = _hp(listOfNotNull(freq).asSprudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that applies HPF (alias for [hpf]) after the previous mapper.
 *
 * @param freq The cutoff frequency in Hz.
 * @return A new [PatternMapperFn] chaining HPF after the previous mapper.
 *
 * ```KlangScript
 * note("c4").apply(gain(0.8).hp(300))               // gain then hp filter
 * ```
 *
 * ```KlangScript
 * note("c3*4").firstOf(4, hp(200).hresonance(10))   // chain
 * ```
 */
@SprudelDsl
fun PatternMapperFn.hp(freq: PatternLike? = null): PatternMapperFn =
    _hp(listOfNotNull(freq).asSprudelDslArgs())

/**
 * Alias for [hpf]. Applies a High Pass Filter with the given cutoff frequency.
 *
 * @param freq The cutoff frequency in Hz. Omit to reinterpret the pattern's values as cutoff.
 * @return A new pattern with HPF applied.
 *
 * ```KlangScript
 * note("c4").hcutoff(300)          // alias for hpf
 * ```
 *
 * ```KlangScript
 * note("c4").hcutoff("<100 800>")  // alternating HPF cutoff
 * ```
 *
 * @param-tool freq SrudelHpFilterSequenceEditor
 * @alias hpf, hp
 * @category effects
 * @tags hcutoff, hpf, high pass filter, filter, frequency
 */
@SprudelDsl
fun SprudelPattern.hcutoff(freq: PatternLike? = null): SprudelPattern =
    this._hcutoff(listOfNotNull(freq).asSprudelDslArgs())

/**
 * Alias for [hpf] on a string pattern.
 *
 * @param freq The cutoff frequency in Hz.
 * @return A new pattern with HPF applied.
 *
 * ```KlangScript
 * "c4 e4".hcutoff(300).note()      // alias for String.hpf
 * ```
 */
@SprudelDsl
fun String.hcutoff(freq: PatternLike? = null): SprudelPattern =
    this._hcutoff(listOfNotNull(freq).asSprudelDslArgs())

/**
 * Alias for [hpf]. Returns a [PatternMapperFn] that applies a High Pass Filter.
 *
 * @param freq The cutoff frequency in Hz.
 * @return A [PatternMapperFn] that applies HPF.
 *
 * ```KlangScript
 * note("c4 e4").apply(hcutoff(300))      // alias for hpf()
 * ```
 *
 * ```KlangScript
 * note("c3*4").firstOf(4, hcutoff(200))  // HPF on first cycle
 * ```
 *
 * @alias hpf, hp
 * @category effects
 * @tags hcutoff, hpf, high pass filter, filter, frequency
 */
@SprudelDsl
fun hcutoff(freq: PatternLike? = null): PatternMapperFn = _hcutoff(listOfNotNull(freq).asSprudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that applies HPF (alias for [hpf]) after the previous mapper.
 *
 * @param freq The cutoff frequency in Hz.
 * @return A new [PatternMapperFn] chaining HPF after the previous mapper.
 *
 * ```KlangScript
 * note("c4").apply(gain(0.8).hcutoff(300))           // gain then hcutoff
 * ```
 *
 * ```KlangScript
 * note("c3*4").firstOf(4, hcutoff(200).hresonance(10))  // chain
 * ```
 */
@SprudelDsl
fun PatternMapperFn.hcutoff(freq: PatternLike? = null): PatternMapperFn =
    _hcutoff(listOfNotNull(freq).asSprudelDslArgs())

// -- bandf() / bpf() / bp() -------------------------------------------------------------------------------------------

private val bandfMutation = voiceModifier {
    val str = it?.toString() ?: return@voiceModifier this
    if (":" in str) {
        val parts = str.split(":").map { d -> d.trim().toDoubleOrNull() }
        copy(
            bandf = parts.getOrNull(0) ?: bandf,
            bandq = parts.getOrNull(1) ?: bandq,
            bpenv = parts.getOrNull(2) ?: bpenv,
        )
    } else {
        copy(bandf = str.toDoubleOrNull())
    }
}

fun applyBandf(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val str = args.firstOrNull()?.value?.toString() ?: ""
    return if (":" in str) {
        source._applyControlFromParams(args, bandfMutation) { src, ctrl ->
            src.copy(
                bandf = ctrl.bandf ?: src.bandf,
                bandq = ctrl.bandq ?: src.bandq,
                bpenv = ctrl.bpenv ?: src.bpenv,
            )
        }
    } else {
        source._liftOrReinterpretNumericalField(args, bandfMutation)
    }
}

internal val _bandf by dslPatternMapper { args, callInfo -> { p -> p._bandf(args, callInfo) } }
internal val SprudelPattern._bandf by dslPatternExtension { p, args, _ -> applyBandf(p, args) }
internal val String._bandf by dslStringExtension { p, args, callInfo -> p._bandf(args, callInfo) }
internal val PatternMapperFn._bandf by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_bandf(args, callInfo))
}

internal val _bpf by dslPatternMapper { args, callInfo -> { p -> p._bpf(args, callInfo) } }
internal val SprudelPattern._bpf by dslPatternExtension { p, args, _ -> applyBandf(p, args) }
internal val String._bpf by dslStringExtension { p, args, callInfo -> p._bpf(args, callInfo) }
internal val PatternMapperFn._bpf by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_bpf(args, callInfo))
}

internal val _bp by dslPatternMapper { args, callInfo -> { p -> p._bp(args, callInfo) } }
internal val SprudelPattern._bp by dslPatternExtension { p, args, _ -> applyBandf(p, args) }
internal val String._bp by dslStringExtension { p, args, callInfo -> p._bp(args, callInfo) }
internal val PatternMapperFn._bp by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_bp(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Applies a Band Pass Filter (BPF) with the given centre frequency in Hz.
 *
 * Only a band of frequencies around the centre frequency passes through. Use [bandq] to
 * control the bandwidth (Q factor); higher Q values create a narrower band.
 *
 * When [freq] is omitted, the pattern's own numeric values are reinterpreted as centre frequencies.
 *
 * @param freq The centre frequency in Hz. Omit to reinterpret the pattern's values as centre frequency.
 * @return A new pattern with BPF applied.
 *
 * ```KlangScript
 * s("sd").bandf(1000)               // emphasise mid-range around 1 kHz
 * ```
 *
 * ```KlangScript
 * note("c4").bandf("<500 2000>")    // alternating centre per cycle
 * ```
 *
 * ```KlangScript
 * seq("500 1000 2000").bandf()      // reinterpret values as BPF centre
 * ```
 *
 * @param-tool freq SprudelBpFilterSequenceEditor
 * @alias bpf, bp
 * @category effects
 * @tags bandf, bpf, band pass filter, filter, frequency
 */
@SprudelDsl
fun SprudelPattern.bandf(freq: PatternLike? = null): SprudelPattern =
    this._bandf(listOfNotNull(freq).asSprudelDslArgs())

/**
 * Parses this string as a pattern, then applies a Band Pass Filter.
 *
 * @param freq The centre frequency in Hz. Omit to reinterpret the pattern's values as centre frequency.
 * @return A new pattern with BPF applied.
 *
 * ```KlangScript
 * "c4 e4".bandf(1000).note()        // BPF on string pattern
 * ```
 *
 * @alias bpf, bp
 * @category effects
 * @tags bandf, bpf, band pass filter, filter, frequency
 */
@SprudelDsl
fun String.bandf(freq: PatternLike? = null): SprudelPattern =
    this._bandf(listOfNotNull(freq).asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] that applies a Band Pass Filter.
 *
 * @param freq The centre frequency in Hz. Omit to reinterpret the pattern's values as centre frequency.
 * @return A [PatternMapperFn] that applies BPF.
 *
 * ```KlangScript
 * note("c4 e4").apply(bandf(1000))                    // apply BPF via mapper
 * ```
 *
 * ```KlangScript
 * note("c4*4").firstOf(4, bandf(800).bandq(5))        // narrow BPF on first cycle
 * ```
 *
 * @alias bpf, bp
 * @category effects
 * @tags bandf, bpf, band pass filter, filter, frequency
 */
@SprudelDsl
fun bandf(freq: PatternLike? = null): PatternMapperFn = _bandf(listOfNotNull(freq).asSprudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that applies a Band Pass Filter after the previous mapper.
 *
 * @param freq The centre frequency in Hz. Omit to reinterpret the pattern's values as centre frequency.
 * @return A new [PatternMapperFn] chaining BPF after the previous mapper.
 *
 * ```KlangScript
 * note("c4 e4").apply(gain(0.8).bandf(1000))          // gain then BPF
 * ```
 *
 * ```KlangScript
 * note("c3*4").firstOf(4, bandf(800).bandq(8))        // narrow BPF chain
 * ```
 */
@SprudelDsl
fun PatternMapperFn.bandf(freq: PatternLike? = null): PatternMapperFn =
    _bandf(listOfNotNull(freq).asSprudelDslArgs())

/**
 * Alias for [bandf]. Applies a Band Pass Filter with the given centre frequency.
 *
 * @param freq The centre frequency in Hz. Omit to reinterpret the pattern's values as centre frequency.
 * @return A new pattern with BPF applied.
 *
 * ```KlangScript
 * note("c4").bpf(1000)              // alias for bandf
 * ```
 *
 * ```KlangScript
 * note("c4").bpf("<500 2000>")      // alternating centre per cycle
 * ```
 *
 * @param-tool freq SprudelBpFilterSequenceEditor
 * @alias bandf, bp
 * @category effects
 * @tags bpf, bandf, band pass filter, filter, frequency
 */
@SprudelDsl
fun SprudelPattern.bpf(freq: PatternLike? = null): SprudelPattern =
    this._bpf(listOfNotNull(freq).asSprudelDslArgs())

/**
 * Alias for [bandf] on a string pattern.
 *
 * @param freq The centre frequency in Hz.
 * @return A new pattern with BPF applied.
 *
 * ```KlangScript
 * "c4 e4".bpf(1000).note()          // alias for String.bandf
 * ```
 */
@SprudelDsl
fun String.bpf(freq: PatternLike? = null): SprudelPattern =
    this._bpf(listOfNotNull(freq).asSprudelDslArgs())

/**
 * Alias for [bandf]. Returns a [PatternMapperFn] that applies a Band Pass Filter.
 *
 * @param freq The centre frequency in Hz.
 * @return A [PatternMapperFn] that applies BPF.
 *
 * ```KlangScript
 * note("c4 e4").apply(bpf(1000))    // alias for bandf()
 * ```
 *
 * ```KlangScript
 * note("c3*4").firstOf(4, bpf(800)) // BPF on first cycle
 * ```
 *
 * @alias bandf, bp
 * @category effects
 * @tags bpf, bandf, band pass filter, filter, frequency
 */
@SprudelDsl
fun bpf(freq: PatternLike? = null): PatternMapperFn = _bpf(listOfNotNull(freq).asSprudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that applies BPF (alias for [bandf]) after the previous mapper.
 *
 * @param freq The centre frequency in Hz.
 * @return A new [PatternMapperFn] chaining BPF after the previous mapper.
 *
 * ```KlangScript
 * note("c4").apply(gain(0.8).bpf(1000))              // gain then BPF
 * ```
 *
 * ```KlangScript
 * note("c3*4").firstOf(4, bpf(800).bandq(5))         // chain
 * ```
 */
@SprudelDsl
fun PatternMapperFn.bpf(freq: PatternLike? = null): PatternMapperFn =
    _bpf(listOfNotNull(freq).asSprudelDslArgs())

/**
 * Alias for [bandf]. Applies a Band Pass Filter with the given centre frequency.
 *
 * @param freq The centre frequency in Hz. Omit to reinterpret the pattern's values as centre frequency.
 * @return A new pattern with BPF applied.
 *
 * ```KlangScript
 * note("c4").bp(1000)               // short alias for bandf
 * ```
 *
 * ```KlangScript
 * note("c4").bp("<500 2000>")       // alternating centre per cycle
 * ```
 *
 * @param-tool freq SprudelBpFilterSequenceEditor
 * @alias bandf, bpf
 * @category effects
 * @tags bp, bandf, band pass filter, filter, frequency
 */
@SprudelDsl
fun SprudelPattern.bp(freq: PatternLike? = null): SprudelPattern =
    this._bp(listOfNotNull(freq).asSprudelDslArgs())

/**
 * Alias for [bandf] on a string pattern.
 *
 * @param freq The centre frequency in Hz.
 * @return A new pattern with BPF applied.
 *
 * ```KlangScript
 * "c4 e4".bp(1000).note()           // alias for String.bandf
 * ```
 */
@SprudelDsl
fun String.bp(freq: PatternLike? = null): SprudelPattern =
    this._bp(listOfNotNull(freq).asSprudelDslArgs())

/**
 * Alias for [bandf]. Returns a [PatternMapperFn] that applies a Band Pass Filter.
 *
 * @param freq The centre frequency in Hz.
 * @return A [PatternMapperFn] that applies BPF.
 *
 * ```KlangScript
 * note("c4 e4").apply(bp(1000))     // alias for bandf()
 * ```
 *
 * ```KlangScript
 * note("c3*4").firstOf(4, bp(800))  // BPF on first cycle
 * ```
 *
 * @alias bandf, bpf
 * @category effects
 * @tags bp, bandf, band pass filter, filter, frequency
 */
@SprudelDsl
fun bp(freq: PatternLike? = null): PatternMapperFn = _bp(listOfNotNull(freq).asSprudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that applies BPF (alias for [bandf]) after the previous mapper.
 *
 * @param freq The centre frequency in Hz.
 * @return A new [PatternMapperFn] chaining BPF after the previous mapper.
 *
 * ```KlangScript
 * note("c4").apply(gain(0.8).bp(1000))               // gain then bp filter
 * ```
 *
 * ```KlangScript
 * note("c3*4").firstOf(4, bp(800).bandq(5))          // chain
 * ```
 */
@SprudelDsl
fun PatternMapperFn.bp(freq: PatternLike? = null): PatternMapperFn =
    _bp(listOfNotNull(freq).asSprudelDslArgs())

// -- resonance() / res() / lpq() - Low Pass Filter resonance ---------------------------------------------------------

private val resonanceMutation = voiceModifier { copy(resonance = it?.asDoubleOrNull()) }

fun applyResonance(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, resonanceMutation)
}

internal val _resonance by dslPatternMapper { args, callInfo -> { p -> p._resonance(args, callInfo) } }
internal val SprudelPattern._resonance by dslPatternExtension { p, args, _ -> applyResonance(p, args) }
internal val String._resonance by dslStringExtension { p, args, callInfo -> p._resonance(args, callInfo) }
internal val PatternMapperFn._resonance by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_resonance(args, callInfo))
}

internal val _res by dslPatternMapper { args, callInfo -> { p -> p._res(args, callInfo) } }
internal val SprudelPattern._res by dslPatternExtension { p, args, _ -> applyResonance(p, args) }
internal val String._res by dslStringExtension { p, args, callInfo -> p._res(args, callInfo) }
internal val PatternMapperFn._res by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_res(args, callInfo))
}

internal val _lpq by dslPatternMapper { args, callInfo -> { p -> p._lpq(args, callInfo) } }
internal val SprudelPattern._lpq by dslPatternExtension { p, args, _ -> applyResonance(p, args) }
internal val String._lpq by dslStringExtension { p, args, callInfo -> p._lpq(args, callInfo) }
internal val PatternMapperFn._lpq by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_lpq(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

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
 * ```KlangScript
 * note("c4 e4").lpf(800).resonance(15)    // LPF with high resonance peak
 * ```
 *
 * ```KlangScript
 * s("bd").lpf(500).resonance("<0 20>")    // resonance sweeps from flat to peaked
 * ```
 *
 * ```KlangScript
 * seq("0 5 10 20").resonance()            // reinterpret values as resonance Q
 * ```
 *
 * @param-tool q SprudelLpResonanceSequenceEditor
 * @alias res, lpq
 * @category effects
 * @tags resonance, res, lpq, low pass filter, Q
 */
@SprudelDsl
fun SprudelPattern.resonance(q: PatternLike? = null): SprudelPattern =
    this._resonance(listOfNotNull(q).asSprudelDslArgs())

/**
 * Parses this string as a pattern, then sets LPF resonance.
 *
 * @param q The Q factor. Omit to reinterpret the pattern's values as Q.
 * @return A new pattern with LPF resonance applied.
 *
 * ```KlangScript
 * "c4 e4".lpf(800).resonance(15)    // resonance on string pattern
 * ```
 *
 * @alias res, lpq
 * @category effects
 * @tags resonance, res, lpq, low pass filter, Q
 */
@SprudelDsl
fun String.resonance(q: PatternLike? = null): SprudelPattern =
    this._resonance(listOfNotNull(q).asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets LPF resonance.
 *
 * @param q The Q factor. Omit to reinterpret the pattern's values as Q.
 * @return A [PatternMapperFn] that applies LPF resonance.
 *
 * ```KlangScript
 * note("c4 e4").apply(lpf(800).resonance(15))        // LPF + resonance chain
 * ```
 *
 * ```KlangScript
 * note("c4*4").firstOf(4, resonance(20))             // high resonance on first cycle
 * ```
 *
 * @alias res, lpq
 * @category effects
 * @tags resonance, res, lpq, low pass filter, Q
 */
@SprudelDsl
fun resonance(q: PatternLike? = null): PatternMapperFn = _resonance(listOfNotNull(q).asSprudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets LPF resonance after the previous mapper.
 *
 * @param q The Q factor. Omit to reinterpret the pattern's values as Q.
 * @return A new [PatternMapperFn] chaining resonance after the previous mapper.
 *
 * ```KlangScript
 * note("c4 e4").apply(lpf(500).resonance(15))        // LPF then resonance
 * ```
 *
 * ```KlangScript
 * note("c3*4").firstOf(4, lpf(300).resonance(20))    // resonant LPF chain
 * ```
 */
@SprudelDsl
fun PatternMapperFn.resonance(q: PatternLike? = null): PatternMapperFn =
    _resonance(listOfNotNull(q).asSprudelDslArgs())

/**
 * Alias for [resonance]. Sets the LPF resonance/Q.
 *
 * @param q The Q factor. Omit to reinterpret the pattern's values as Q.
 * @return A new pattern with LPF resonance applied.
 *
 * ```KlangScript
 * note("c4").lpf(500).res(10)       // alias for resonance
 * ```
 *
 * ```KlangScript
 * note("c4").res("<0 20>")          // sweeping resonance
 * ```
 *
 * @param-tool q SprudelLpResonanceSequenceEditor
 * @alias resonance, lpq
 * @category effects
 * @tags res, resonance, lpq, low pass filter, Q
 */
@SprudelDsl
fun SprudelPattern.res(q: PatternLike? = null): SprudelPattern =
    this._res(listOfNotNull(q).asSprudelDslArgs())

/**
 * Alias for [resonance] on a string pattern.
 *
 * @param q The Q factor.
 * @return A new pattern with LPF resonance applied.
 *
 * ```KlangScript
 * "c4".lpf(500).res(10)             // alias for String.resonance
 * ```
 */
@SprudelDsl
fun String.res(q: PatternLike? = null): SprudelPattern =
    this._res(listOfNotNull(q).asSprudelDslArgs())

/**
 * Alias for [resonance]. Returns a [PatternMapperFn] that sets LPF resonance.
 *
 * @param q The Q factor.
 * @return A [PatternMapperFn] that applies LPF resonance.
 *
 * ```KlangScript
 * note("c4 e4").apply(lpf(500).res(10))  // alias for resonance()
 * ```
 *
 * ```KlangScript
 * note("c3*4").firstOf(4, res(20))       // high resonance on first cycle
 * ```
 *
 * @alias resonance, lpq
 * @category effects
 * @tags res, resonance, lpq, low pass filter, Q
 */
@SprudelDsl
fun res(q: PatternLike? = null): PatternMapperFn = _res(listOfNotNull(q).asSprudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets LPF resonance (alias for [resonance]) after the previous mapper.
 *
 * @param q The Q factor.
 * @return A new [PatternMapperFn] chaining resonance after the previous mapper.
 *
 * ```KlangScript
 * note("c4 e4").apply(lpf(500).res(10))  // LPF then res
 * ```
 *
 * ```KlangScript
 * note("c3*4").firstOf(4, lpf(300).res(20))  // chain
 * ```
 */
@SprudelDsl
fun PatternMapperFn.res(q: PatternLike? = null): PatternMapperFn =
    _res(listOfNotNull(q).asSprudelDslArgs())

/**
 * Alias for [resonance]. Sets the LPF resonance/Q.
 *
 * @param q The Q factor. Omit to reinterpret the pattern's values as Q.
 * @return A new pattern with LPF resonance applied.
 *
 * ```KlangScript
 * note("c4").lpf(500).lpq(10)       // alias for resonance
 * ```
 *
 * ```KlangScript
 * note("c4").lpq("<0 20>")          // sweeping Q
 * ```
 *
 * @param-tool q SprudelLpResonanceSequenceEditor
 * @alias resonance, res
 * @category effects
 * @tags lpq, resonance, res, low pass filter, Q
 */
@SprudelDsl
fun SprudelPattern.lpq(q: PatternLike? = null): SprudelPattern =
    this._lpq(listOfNotNull(q).asSprudelDslArgs())

/**
 * Alias for [resonance] on a string pattern.
 *
 * @param q The Q factor.
 * @return A new pattern with LPF resonance applied.
 *
 * ```KlangScript
 * "c4".lpf(500).lpq(10)             // alias for String.resonance
 * ```
 */
@SprudelDsl
fun String.lpq(q: PatternLike? = null): SprudelPattern =
    this._lpq(listOfNotNull(q).asSprudelDslArgs())

/**
 * Alias for [resonance]. Returns a [PatternMapperFn] that sets LPF resonance.
 *
 * @param q The Q factor.
 * @return A [PatternMapperFn] that applies LPF resonance.
 *
 * ```KlangScript
 * note("c4 e4").apply(lpf(500).lpq(10))  // alias for resonance()
 * ```
 *
 * ```KlangScript
 * note("c3*4").firstOf(4, lpq(20))       // high Q on first cycle
 * ```
 *
 * @alias resonance, res
 * @category effects
 * @tags lpq, resonance, res, low pass filter, Q
 */
@SprudelDsl
fun lpq(q: PatternLike? = null): PatternMapperFn = _lpq(listOfNotNull(q).asSprudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets LPF resonance (alias for [resonance]) after the previous mapper.
 *
 * @param q The Q factor.
 * @return A new [PatternMapperFn] chaining resonance after the previous mapper.
 *
 * ```KlangScript
 * note("c4 e4").apply(lpf(500).lpq(10))  // LPF then lpq
 * ```
 *
 * ```KlangScript
 * note("c3*4").firstOf(4, lpf(300).lpq(20))  // chain
 * ```
 */
@SprudelDsl
fun PatternMapperFn.lpq(q: PatternLike? = null): PatternMapperFn =
    _lpq(listOfNotNull(q).asSprudelDslArgs())

// -- hresonance() / hres() / hpq() - High Pass Filter resonance ------------------------------------------------------

private val hresonanceMutation = voiceModifier { copy(hresonance = it?.asDoubleOrNull()) }

fun applyHresonance(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, hresonanceMutation)
}

internal val _hresonance by dslPatternMapper { args, callInfo -> { p -> p._hresonance(args, callInfo) } }
internal val SprudelPattern._hresonance by dslPatternExtension { p, args, _ -> applyHresonance(p, args) }
internal val String._hresonance by dslStringExtension { p, args, callInfo -> p._hresonance(args, callInfo) }
internal val PatternMapperFn._hresonance by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_hresonance(args, callInfo))
}

internal val _hres by dslPatternMapper { args, callInfo -> { p -> p._hres(args, callInfo) } }
internal val SprudelPattern._hres by dslPatternExtension { p, args, _ -> applyHresonance(p, args) }
internal val String._hres by dslStringExtension { p, args, callInfo -> p._hres(args, callInfo) }
internal val PatternMapperFn._hres by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_hres(args, callInfo))
}

internal val _hpq by dslPatternMapper { args, callInfo -> { p -> p._hpq(args, callInfo) } }
internal val SprudelPattern._hpq by dslPatternExtension { p, args, _ -> applyHresonance(p, args) }
internal val String._hpq by dslStringExtension { p, args, callInfo -> p._hpq(args, callInfo) }
internal val PatternMapperFn._hpq by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_hpq(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

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
 * ```KlangScript
 * note("c4").hpf(300).hresonance(15)        // HPF with strong resonance peak
 * ```
 *
 * ```KlangScript
 * s("sd").hpf(200).hresonance("<0 20>")     // resonance sweeps per cycle
 * ```
 *
 * ```KlangScript
 * seq("0 5 15").hresonance()                // reinterpret values as HPF Q
 * ```
 *
 * @param-tool q SprudelHpResonanceSequenceEditor
 * @alias hres, hpq
 * @category effects
 * @tags hresonance, hres, hpq, high pass filter, Q, resonance
 */
@SprudelDsl
fun SprudelPattern.hresonance(q: PatternLike? = null): SprudelPattern =
    this._hresonance(listOfNotNull(q).asSprudelDslArgs())

/**
 * Parses this string as a pattern, then sets HPF resonance.
 *
 * @param q The Q factor. Omit to reinterpret the pattern's values as Q.
 * @return A new pattern with HPF resonance applied.
 *
 * ```KlangScript
 * "c4".hpf(300).hresonance(15)      // resonance on string pattern
 * ```
 *
 * @alias hres, hpq
 * @category effects
 * @tags hresonance, hres, hpq, high pass filter, Q, resonance
 */
@SprudelDsl
fun String.hresonance(q: PatternLike? = null): SprudelPattern =
    this._hresonance(listOfNotNull(q).asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets HPF resonance.
 *
 * @param q The Q factor. Omit to reinterpret the pattern's values as Q.
 * @return A [PatternMapperFn] that applies HPF resonance.
 *
 * ```KlangScript
 * note("c4 e4").apply(hpf(300).hresonance(15))       // HPF + resonance chain
 * ```
 *
 * ```KlangScript
 * note("c4*4").firstOf(4, hresonance(20))            // high HPF resonance on first cycle
 * ```
 *
 * @alias hres, hpq
 * @category effects
 * @tags hresonance, hres, hpq, high pass filter, Q, resonance
 */
@SprudelDsl
fun hresonance(q: PatternLike? = null): PatternMapperFn = _hresonance(listOfNotNull(q).asSprudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets HPF resonance after the previous mapper.
 *
 * @param q The Q factor. Omit to reinterpret the pattern's values as Q.
 * @return A new [PatternMapperFn] chaining HPF resonance after the previous mapper.
 *
 * ```KlangScript
 * note("c4 e4").apply(hpf(200).hresonance(15))       // HPF then resonance
 * ```
 *
 * ```KlangScript
 * note("c3*4").firstOf(4, hpf(300).hresonance(20))   // resonant HPF chain
 * ```
 */
@SprudelDsl
fun PatternMapperFn.hresonance(q: PatternLike? = null): PatternMapperFn =
    _hresonance(listOfNotNull(q).asSprudelDslArgs())

/**
 * Alias for [hresonance]. Sets the HPF resonance/Q.
 *
 * @param q The Q factor. Omit to reinterpret the pattern's values as Q.
 * @return A new pattern with HPF resonance applied.
 *
 * ```KlangScript
 * note("c4").hpf(300).hres(10)      // alias for hresonance
 * ```
 *
 * ```KlangScript
 * note("c4").hres("<0 20>")         // sweeping HPF Q
 * ```
 *
 * @param-tool q SprudelHpResonanceSequenceEditor
 * @alias hresonance, hpq
 * @category effects
 * @tags hres, hresonance, hpq, high pass filter, Q
 */
@SprudelDsl
fun SprudelPattern.hres(q: PatternLike? = null): SprudelPattern =
    this._hres(listOfNotNull(q).asSprudelDslArgs())

/**
 * Alias for [hresonance] on a string pattern.
 *
 * @param q The Q factor.
 * @return A new pattern with HPF resonance applied.
 *
 * ```KlangScript
 * "c4".hpf(300).hres(10)            // alias for String.hresonance
 * ```
 */
@SprudelDsl
fun String.hres(q: PatternLike? = null): SprudelPattern =
    this._hres(listOfNotNull(q).asSprudelDslArgs())

/**
 * Alias for [hresonance]. Returns a [PatternMapperFn] that sets HPF resonance.
 *
 * @param q The Q factor.
 * @return A [PatternMapperFn] that applies HPF resonance.
 *
 * ```KlangScript
 * note("c4 e4").apply(hpf(300).hres(10))  // alias for hresonance()
 * ```
 *
 * ```KlangScript
 * note("c3*4").firstOf(4, hres(20))       // high Q on first cycle
 * ```
 *
 * @alias hresonance, hpq
 * @category effects
 * @tags hres, hresonance, hpq, high pass filter, Q
 */
@SprudelDsl
fun hres(q: PatternLike? = null): PatternMapperFn = _hres(listOfNotNull(q).asSprudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets HPF resonance (alias for [hresonance]) after the previous mapper.
 *
 * @param q The Q factor.
 * @return A new [PatternMapperFn] chaining HPF resonance after the previous mapper.
 *
 * ```KlangScript
 * note("c4 e4").apply(hpf(300).hres(10))  // HPF then hres
 * ```
 *
 * ```KlangScript
 * note("c3*4").firstOf(4, hpf(200).hres(20))  // chain
 * ```
 */
@SprudelDsl
fun PatternMapperFn.hres(q: PatternLike? = null): PatternMapperFn =
    _hres(listOfNotNull(q).asSprudelDslArgs())

/**
 * Alias for [hresonance]. Sets the HPF resonance/Q.
 *
 * @param q The Q factor. Omit to reinterpret the pattern's values as Q.
 * @return A new pattern with HPF resonance applied.
 *
 * ```KlangScript
 * note("c4").hpf(300).hpq(10)       // alias for hresonance
 * ```
 *
 * ```KlangScript
 * note("c4").hpq("<0 20>")          // sweeping HPF Q
 * ```
 *
 * @param-tool q SprudelHpResonanceSequenceEditor
 * @alias hresonance, hres
 * @category effects
 * @tags hpq, hresonance, hres, high pass filter, Q
 */
@SprudelDsl
fun SprudelPattern.hpq(q: PatternLike? = null): SprudelPattern =
    this._hpq(listOfNotNull(q).asSprudelDslArgs())

/**
 * Alias for [hresonance] on a string pattern.
 *
 * @param q The Q factor.
 * @return A new pattern with HPF resonance applied.
 *
 * ```KlangScript
 * "c4".hpf(300).hpq(10)             // alias for String.hresonance
 * ```
 */
@SprudelDsl
fun String.hpq(q: PatternLike? = null): SprudelPattern =
    this._hpq(listOfNotNull(q).asSprudelDslArgs())

/**
 * Alias for [hresonance]. Returns a [PatternMapperFn] that sets HPF resonance.
 *
 * @param q The Q factor.
 * @return A [PatternMapperFn] that applies HPF resonance.
 *
 * ```KlangScript
 * note("c4 e4").apply(hpf(300).hpq(10))  // alias for hresonance()
 * ```
 *
 * ```KlangScript
 * note("c3*4").firstOf(4, hpq(20))       // high Q on first cycle
 * ```
 *
 * @alias hresonance, hres
 * @category effects
 * @tags hpq, hresonance, hres, high pass filter, Q
 */
@SprudelDsl
fun hpq(q: PatternLike? = null): PatternMapperFn = _hpq(listOfNotNull(q).asSprudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets HPF resonance (alias for [hresonance]) after the previous mapper.
 *
 * @param q The Q factor.
 * @return A new [PatternMapperFn] chaining HPF resonance after the previous mapper.
 *
 * ```KlangScript
 * note("c4 e4").apply(hpf(300).hpq(10))  // HPF then hpq
 * ```
 *
 * ```KlangScript
 * note("c3*4").firstOf(4, hpf(200).hpq(20))  // chain
 * ```
 */
@SprudelDsl
fun PatternMapperFn.hpq(q: PatternLike? = null): PatternMapperFn =
    _hpq(listOfNotNull(q).asSprudelDslArgs())

// -- bandq() / bpq() - Band Pass Filter resonance --------------------------------------------------------------------

private val bandqMutation = voiceModifier { copy(bandq = it?.asDoubleOrNull()) }

fun applyBandq(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, bandqMutation)
}

internal val _bandq by dslPatternMapper { args, callInfo -> { p -> p._bandq(args, callInfo) } }
internal val SprudelPattern._bandq by dslPatternExtension { p, args, _ -> applyBandq(p, args) }
internal val String._bandq by dslStringExtension { p, args, callInfo -> p._bandq(args, callInfo) }
internal val PatternMapperFn._bandq by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_bandq(args, callInfo))
}

// ===== USER-FACING OVERLOADS (bandq) =====

/**
 * Sets the Q factor (bandwidth) of the Band Pass Filter.
 *
 * Higher Q values create a narrower, more selective frequency band. Lower values let a
 * wider range through. Use with [bandf] to set the centre frequency.
 *
 * When [q] is omitted, the pattern's own numeric values are reinterpreted as Q values.
 *
 * @param q The Q factor. Higher values create a narrower band. Omit to reinterpret pattern values.
 * @return A new pattern with BPF Q applied.
 *
 * ```KlangScript
 * note("c4").bandf(1000).bandq(5)         // narrow band pass at 1 kHz
 * ```
 *
 * ```KlangScript
 * s("sd").bandf(800).bandq("<1 20>")      // Q sweeps from wide to narrow
 * ```
 *
 * ```KlangScript
 * seq("1 5 10 20").bandq()                // reinterpret values as BPF Q
 * ```
 *
 * @param-tool q SprudelBpQSequenceEditor
 * @alias bpq
 * @category effects
 * @tags bandq, bpq, band pass filter, Q, bandwidth
 */
@SprudelDsl
fun SprudelPattern.bandq(q: PatternLike? = null): SprudelPattern =
    this._bandq(listOfNotNull(q).asSprudelDslArgs())

/**
 * Parses this string as a pattern, then sets BPF Q.
 *
 * @param q The Q factor. Omit to reinterpret the pattern's values as Q.
 * @return A new pattern with BPF Q applied.
 *
 * ```KlangScript
 * "c4".bandf(800).bandq(5)          // BPF Q on string pattern
 * ```
 */
@SprudelDsl
fun String.bandq(q: PatternLike? = null): SprudelPattern =
    this._bandq(listOfNotNull(q).asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets BPF Q.
 *
 * @param q The Q factor. Omit to reinterpret the pattern's values as Q.
 * @return A [PatternMapperFn] that applies BPF Q.
 *
 * ```KlangScript
 * note("c4 e4").apply(bandf(1000).bandq(5))          // BPF + Q chain
 * ```
 *
 * ```KlangScript
 * note("c4*4").firstOf(4, bandq(10))                 // narrow BPF on first cycle
 * ```
 *
 * @alias bpq
 * @category effects
 * @tags bandq, bpq, band pass filter, Q, bandwidth
 */
@SprudelDsl
fun bandq(q: PatternLike? = null): PatternMapperFn = _bandq(listOfNotNull(q).asSprudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets BPF Q after the previous mapper.
 *
 * @param q The Q factor. Omit to reinterpret the pattern's values as Q.
 * @return A new [PatternMapperFn] chaining BPF Q after the previous mapper.
 *
 * ```KlangScript
 * note("c4 e4").apply(bandf(800).bandq(5))           // bandf then bandq
 * ```
 *
 * ```KlangScript
 * note("c3*4").firstOf(4, bandf(1000).bandq(8))      // narrow BPF chain
 * ```
 */
@SprudelDsl
fun PatternMapperFn.bandq(q: PatternLike? = null): PatternMapperFn =
    _bandq(listOfNotNull(q).asSprudelDslArgs())

internal val _bpq by dslPatternMapper { args, callInfo -> { p -> p._bpq(args, callInfo) } }
internal val SprudelPattern._bpq by dslPatternExtension { p, args, _ -> applyBandq(p, args) }
internal val String._bpq by dslStringExtension { p, args, callInfo -> p._bpq(args, callInfo) }
internal val PatternMapperFn._bpq by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_bpq(args, callInfo))
}

/**
 * Alias for [bandq]. Sets the BPF Q (bandwidth).
 *
 * @param q The Q factor. Omit to reinterpret the pattern's values as Q.
 * @return A new pattern with BPF Q applied.
 *
 * ```KlangScript
 * note("c4").bandf(800).bpq(5)      // alias for bandq
 * ```
 *
 * ```KlangScript
 * note("c4").bpq("<1 20>")          // sweeping BPF Q
 * ```
 *
 * @param-tool q SprudelBpQSequenceEditor
 * @alias bandq
 * @category effects
 * @tags bpq, bandq, band pass filter, Q, bandwidth
 */
@SprudelDsl
fun SprudelPattern.bpq(q: PatternLike? = null): SprudelPattern =
    this._bpq(listOfNotNull(q).asSprudelDslArgs())

/**
 * Alias for [bandq] on a string pattern.
 *
 * @param q The Q factor.
 * @return A new pattern with BPF Q applied.
 *
 * ```KlangScript
 * "c4".bandf(800).bpq(5)            // alias for String.bandq
 * ```
 */
@SprudelDsl
fun String.bpq(q: PatternLike? = null): SprudelPattern =
    this._bpq(listOfNotNull(q).asSprudelDslArgs())

/**
 * Alias for [bandq]. Returns a [PatternMapperFn] that sets BPF Q.
 *
 * @param q The Q factor.
 * @return A [PatternMapperFn] that applies BPF Q.
 *
 * ```KlangScript
 * note("c4 e4").apply(bandf(800).bpq(5))  // alias for bandq()
 * ```
 *
 * ```KlangScript
 * note("c3*4").firstOf(4, bpq(10))        // narrow BPF on first cycle
 * ```
 *
 * @alias bandq
 * @category effects
 * @tags bpq, bandq, band pass filter, Q, bandwidth
 */
@SprudelDsl
fun bpq(q: PatternLike? = null): PatternMapperFn = _bpq(listOfNotNull(q).asSprudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets BPF Q (alias for [bandq]) after the previous mapper.
 *
 * @param q The Q factor.
 * @return A new [PatternMapperFn] chaining BPF Q after the previous mapper.
 *
 * ```KlangScript
 * note("c4 e4").apply(bandf(800).bpq(5))  // bandf then bpq
 * ```
 *
 * ```KlangScript
 * note("c3*4").firstOf(4, bandf(1000).bpq(8))  // chain
 * ```
 */
@SprudelDsl
fun PatternMapperFn.bpq(q: PatternLike? = null): PatternMapperFn =
    _bpq(listOfNotNull(q).asSprudelDslArgs())

// -- lpattack() / lpa() - Low Pass Filter Envelope Attack ---------------------------------------------------------------

private val lpattackMutation = voiceModifier { copy(lpattack = it?.asDoubleOrNull()) }

fun applyLpattack(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, lpattackMutation)
}

internal val _lpattack by dslPatternMapper { args, callInfo -> { p -> p._lpattack(args, callInfo) } }
internal val SprudelPattern._lpattack by dslPatternExtension { p, args, _ -> applyLpattack(p, args) }
internal val String._lpattack by dslStringExtension { p, args, callInfo -> p._lpattack(args, callInfo) }
internal val PatternMapperFn._lpattack by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_lpattack(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the LPF envelope attack time in seconds.
 *
 * Controls how quickly the low pass filter cutoff sweeps from its baseline to the peak
 * at note onset. Use with [lpenv], [lpdecay], [lpsustain], [lprelease].
 *
 * When [seconds] is omitted, the pattern's own numeric values are reinterpreted as attack times.
 *
 * @param seconds The attack time in seconds. Omit to reinterpret the pattern's values as attack time.
 * @return A new pattern with LPF attack applied.
 *
 * ```KlangScript
 * s("bd").lpf(200).lpenv(5000).lpattack(0.1)   // filter opens over 100 ms
 * ```
 *
 * ```KlangScript
 * note("c4").lpattack("<0.01 0.5>")              // fast vs slow filter attack per cycle
 * ```
 *
 * @param-tool seconds SprudelLpAttackSequenceEditor
 * @alias lpa
 * @category effects
 * @tags lpattack, lpa, low pass filter, envelope, attack
 */
@SprudelDsl
fun SprudelPattern.lpattack(seconds: PatternLike? = null): SprudelPattern =
    this._lpattack(listOfNotNull(seconds).asSprudelDslArgs())

/**
 * Parses this string as a pattern, then sets LPF envelope attack time.
 *
 * @param seconds The attack time in seconds.
 * @return A new pattern with LPF attack applied.
 *
 * ```KlangScript
 * "c4".lpattack(0.1)                 // LPF attack on string pattern
 * ```
 *
 * @alias lpa
 * @category effects
 * @tags lpattack, lpa, low pass filter, envelope, attack
 */
@SprudelDsl
fun String.lpattack(seconds: PatternLike? = null): SprudelPattern =
    this._lpattack(listOfNotNull(seconds).asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets LPF envelope attack time.
 *
 * @param seconds The attack time in seconds. Omit to reinterpret the pattern's values as attack time.
 * @return A [PatternMapperFn] that applies LPF attack.
 *
 * ```KlangScript
 * note("c4 e4").apply(lpf(200).lpenv(4000).lpattack(0.1))  // LPF attack chain
 * ```
 *
 * ```KlangScript
 * note("c4*4").firstOf(4, lpattack(0.5))   // slow attack on first cycle
 * ```
 *
 * @alias lpa
 * @category effects
 * @tags lpattack, lpa, low pass filter, envelope, attack
 */
@SprudelDsl
fun lpattack(seconds: PatternLike? = null): PatternMapperFn = _lpattack(listOfNotNull(seconds).asSprudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that sets LPF envelope attack after the previous mapper.
 *
 * @param seconds The attack time in seconds.
 * @return A new [PatternMapperFn] chaining LPF attack after the previous mapper.
 *
 * ```KlangScript
 * note("c4 e4").apply(lpf(200).lpattack(0.1))  // lpf then attack
 * ```
 *
 * ```KlangScript
 * note("c3*4").firstOf(4, lpf(300).lpattack(0.2))  // chain
 * ```
 */
@SprudelDsl
fun PatternMapperFn.lpattack(seconds: PatternLike? = null): PatternMapperFn =
    _lpattack(listOfNotNull(seconds).asSprudelDslArgs())

internal val _lpa by dslPatternMapper { args, callInfo -> { p -> p._lpa(args, callInfo) } }
internal val SprudelPattern._lpa by dslPatternExtension { p, args, _ -> applyLpattack(p, args) }
internal val String._lpa by dslStringExtension { p, args, callInfo -> p._lpa(args, callInfo) }
internal val PatternMapperFn._lpa by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_lpa(args, callInfo))
}

/**
 * Alias for [lpattack]. Sets the LPF envelope attack time.
 *
 * @param seconds The attack time in seconds. Omit to reinterpret the pattern's values as attack time.
 * @return A new pattern with LPF attack applied.
 *
 * ```KlangScript
 * s("bd").lpf(200).lpenv(5000).lpa(0.1)   // filter opens over 100 ms
 * ```
 *
 * @param-tool seconds SprudelLpAttackSequenceEditor
 * @alias lpattack
 * @category effects
 * @tags lpa, lpattack, low pass filter, envelope, attack
 */
@SprudelDsl
fun SprudelPattern.lpa(seconds: PatternLike? = null): SprudelPattern =
    this._lpa(listOfNotNull(seconds).asSprudelDslArgs())

/** Alias for [lpattack] on a string pattern. */
@SprudelDsl
fun String.lpa(seconds: PatternLike? = null): SprudelPattern =
    this._lpa(listOfNotNull(seconds).asSprudelDslArgs())

/**
 * Alias for [lpattack]. Returns a [PatternMapperFn] that sets LPF envelope attack.
 *
 * @param seconds The attack time in seconds.
 * @return A [PatternMapperFn] that applies LPF attack.
 *
 * ```KlangScript
 * note("c4 e4").apply(lpf(200).lpa(0.1))   // alias for lpattack()
 * ```
 *
 * @alias lpattack
 * @category effects
 * @tags lpa, lpattack, low pass filter, envelope, attack
 */
@SprudelDsl
fun lpa(seconds: PatternLike? = null): PatternMapperFn = _lpa(listOfNotNull(seconds).asSprudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets LPF attack (alias for [lpattack]) after the previous mapper. */
@SprudelDsl
fun PatternMapperFn.lpa(seconds: PatternLike? = null): PatternMapperFn =
    _lpa(listOfNotNull(seconds).asSprudelDslArgs())

// -- lpdecay() / lpd() - Low Pass Filter Envelope Decay ----------------------------------------------------------------

private val lpdecayMutation = voiceModifier { copy(lpdecay = it?.asDoubleOrNull()) }

fun applyLpdecay(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, lpdecayMutation)
}

internal val _lpdecay by dslPatternMapper { args, callInfo -> { p -> p._lpdecay(args, callInfo) } }
internal val SprudelPattern._lpdecay by dslPatternExtension { p, args, _ -> applyLpdecay(p, args) }
internal val String._lpdecay by dslStringExtension { p, args, callInfo -> p._lpdecay(args, callInfo) }
internal val PatternMapperFn._lpdecay by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_lpdecay(args, callInfo))
}

internal val _lpd by dslPatternMapper { args, callInfo -> { p -> p._lpd(args, callInfo) } }
internal val SprudelPattern._lpd by dslPatternExtension { p, args, _ -> applyLpdecay(p, args) }
internal val String._lpd by dslStringExtension { p, args, callInfo -> p._lpd(args, callInfo) }
internal val PatternMapperFn._lpd by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_lpd(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the LPF envelope decay time in seconds.
 *
 * Controls how quickly the filter cutoff moves from peak to sustain level after the attack.
 * Use with [lpattack], [lpsustain], [lprelease], [lpenv].
 *
 * @param seconds The decay time in seconds. Omit to reinterpret the pattern's values as decay time.
 * @return A new pattern with LPF decay applied.
 *
 * ```KlangScript
 * s("bd").lpf(200).lpenv(5000).lpdecay(0.2)   // filter decays over 200 ms
 * ```
 *
 * ```KlangScript
 * note("c4").lpdecay("<0.05 0.5>")              // short vs long filter decay per cycle
 * ```
 *
 * @param-tool seconds SprudelLpDecaySequenceEditor
 * @alias lpd
 * @category effects
 * @tags lpdecay, lpd, low pass filter, envelope, decay
 */
@SprudelDsl
fun SprudelPattern.lpdecay(seconds: PatternLike? = null): SprudelPattern =
    this._lpdecay(listOfNotNull(seconds).asSprudelDslArgs())

/** Parses this string as a pattern, then sets LPF envelope decay time. */
@SprudelDsl
fun String.lpdecay(seconds: PatternLike? = null): SprudelPattern =
    this._lpdecay(listOfNotNull(seconds).asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets LPF envelope decay time.
 *
 * @param seconds The decay time in seconds.
 * @return A [PatternMapperFn] that applies LPF decay.
 *
 * ```KlangScript
 * note("c4 e4").apply(lpf(200).lpenv(4000).lpdecay(0.2))  // LPF decay chain
 * ```
 *
 * @alias lpd
 * @category effects
 * @tags lpdecay, lpd, low pass filter, envelope, decay
 */
@SprudelDsl
fun lpdecay(seconds: PatternLike? = null): PatternMapperFn = _lpdecay(listOfNotNull(seconds).asSprudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets LPF decay after the previous mapper. */
@SprudelDsl
fun PatternMapperFn.lpdecay(seconds: PatternLike? = null): PatternMapperFn =
    _lpdecay(listOfNotNull(seconds).asSprudelDslArgs())

/**
 * Alias for [lpdecay]. Sets the LPF envelope decay time.
 *
 * @param seconds The decay time in seconds.
 * @return A new pattern with LPF decay applied.
 *
 * ```KlangScript
 * s("bd").lpf(200).lpenv(5000).lpd(0.2)   // alias for lpdecay
 * ```
 *
 * @param-tool seconds SprudelLpDecaySequenceEditor
 * @alias lpdecay
 * @category effects
 * @tags lpd, lpdecay, low pass filter, envelope, decay
 */
@SprudelDsl
fun SprudelPattern.lpd(seconds: PatternLike? = null): SprudelPattern =
    this._lpd(listOfNotNull(seconds).asSprudelDslArgs())

/** Alias for [lpdecay] on a string pattern. */
@SprudelDsl
fun String.lpd(seconds: PatternLike? = null): SprudelPattern =
    this._lpd(listOfNotNull(seconds).asSprudelDslArgs())

/**
 * Alias for [lpdecay]. Returns a [PatternMapperFn] that sets LPF envelope decay.
 *
 * @param seconds The decay time in seconds.
 * @return A [PatternMapperFn] that applies LPF decay.
 *
 * ```KlangScript
 * note("c4 e4").apply(lpf(200).lpd(0.2))   // alias for lpdecay()
 * ```
 *
 * @alias lpdecay
 * @category effects
 * @tags lpd, lpdecay, low pass filter, envelope, decay
 */
@SprudelDsl
fun lpd(seconds: PatternLike? = null): PatternMapperFn = _lpd(listOfNotNull(seconds).asSprudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets LPF decay (alias for [lpdecay]) after the previous mapper. */
@SprudelDsl
fun PatternMapperFn.lpd(seconds: PatternLike? = null): PatternMapperFn =
    _lpd(listOfNotNull(seconds).asSprudelDslArgs())

// -- lpsustain() / lps() - Low Pass Filter Envelope Sustain ------------------------------------------------------------

private val lpsustainMutation = voiceModifier { copy(lpsustain = it?.asDoubleOrNull()) }

fun applyLpsustain(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, lpsustainMutation)
}

internal val _lpsustain by dslPatternMapper { args, callInfo -> { p -> p._lpsustain(args, callInfo) } }
internal val SprudelPattern._lpsustain by dslPatternExtension { p, args, _ -> applyLpsustain(p, args) }
internal val String._lpsustain by dslStringExtension { p, args, callInfo -> p._lpsustain(args, callInfo) }
internal val PatternMapperFn._lpsustain by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_lpsustain(args, callInfo))
}

internal val _lps by dslPatternMapper { args, callInfo -> { p -> p._lps(args, callInfo) } }
internal val SprudelPattern._lps by dslPatternExtension { p, args, _ -> applyLpsustain(p, args) }
internal val String._lps by dslStringExtension { p, args, callInfo -> p._lps(args, callInfo) }
internal val PatternMapperFn._lps by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_lps(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the LPF envelope sustain level (0–1).
 *
 * Controls the filter cutoff level during the sustained portion of the note. `1` holds
 * the filter open; `0` closes it back to baseline. Use with the lpattack/lpdecay/lprelease.
 *
 * ```KlangScript
 * note("c4").lpf(200).lpenv(4000).lpsustain(0.5)  // sustain at half depth
 * ```
 *
 * ```KlangScript
 * note("c4").lpsustain("<0 1>")                     // closed vs fully open sustain
 * ```
 *
 * @param level Sustain level (0–1); omit to reinterpret the pattern's own values.
 * @return A [PatternMapperFn] that sets the LPF sustain level, or [SprudelPattern] when called on a pattern.
 * @param-tool level SprudelLpSustainSequenceEditor
 * @alias lps
 * @category effects
 * @tags lpsustain, lps, low pass filter, envelope, sustain
 */
@SprudelDsl
fun SprudelPattern.lpsustain(level: PatternLike? = null): SprudelPattern =
    this._lpsustain(listOfNotNull(level).asSprudelDslArgs())

/** Sets the LPF envelope sustain level on a string pattern. */
@SprudelDsl
fun String.lpsustain(level: PatternLike? = null): SprudelPattern =
    this._lpsustain(listOfNotNull(level).asSprudelDslArgs())

/** Creates a [PatternMapperFn] that sets the LPF envelope sustain level. */
@SprudelDsl
fun lpsustain(level: PatternLike? = null): PatternMapperFn = _lpsustain(listOfNotNull(level).asSprudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets the LPF envelope sustain level after the previous mapper. */
@SprudelDsl
fun PatternMapperFn.lpsustain(level: PatternLike? = null): PatternMapperFn =
    _lpsustain(listOfNotNull(level).asSprudelDslArgs())

/**
 * Alias for [lpsustain]. Sets the LPF envelope sustain level.
 *
 * ```KlangScript
 * note("c4").lpf(200).lps(0.5)   // alias for lpsustain()
 * ```
 *
 * ```KlangScript
 * note("c4").apply(lpf(200).lps(0.5))   // chained PatternMapperFn
 * ```
 *
 * @param level Sustain level (0–1); omit to reinterpret the pattern's own values.
 * @return A [PatternMapperFn] that sets the LPF sustain level, or [SprudelPattern] when called on a pattern.
 * @param-tool level SprudelLpSustainSequenceEditor
 * @alias lpsustain
 * @category effects
 * @tags lps, lpsustain, low pass filter, envelope, sustain
 */
@SprudelDsl
fun SprudelPattern.lps(level: PatternLike? = null): SprudelPattern =
    this._lps(listOfNotNull(level).asSprudelDslArgs())

/** Alias for [lpsustain] on a string pattern. */
@SprudelDsl
fun String.lps(level: PatternLike? = null): SprudelPattern =
    this._lps(listOfNotNull(level).asSprudelDslArgs())

/** Creates a [PatternMapperFn] that sets the LPF envelope sustain level (alias for [lpsustain]). */
@SprudelDsl
fun lps(level: PatternLike? = null): PatternMapperFn = _lps(listOfNotNull(level).asSprudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets LPF sustain (alias for [lpsustain]) after the previous mapper. */
@SprudelDsl
fun PatternMapperFn.lps(level: PatternLike? = null): PatternMapperFn =
    _lps(listOfNotNull(level).asSprudelDslArgs())

// -- lprelease() / lpr() - Low Pass Filter Envelope Release ------------------------------------------------------------

private val lpreleaseMutation = voiceModifier { copy(lprelease = it?.asDoubleOrNull()) }

fun applyLprelease(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, lpreleaseMutation)
}

internal val _lprelease by dslPatternMapper { args, callInfo -> { p -> p._lprelease(args, callInfo) } }
internal val SprudelPattern._lprelease by dslPatternExtension { p, args, _ -> applyLprelease(p, args) }
internal val String._lprelease by dslStringExtension { p, args, callInfo -> p._lprelease(args, callInfo) }
internal val PatternMapperFn._lprelease by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_lprelease(args, callInfo))
}

internal val _lpr by dslPatternMapper { args, callInfo -> { p -> p._lpr(args, callInfo) } }
internal val SprudelPattern._lpr by dslPatternExtension { p, args, _ -> applyLprelease(p, args) }
internal val String._lpr by dslStringExtension { p, args, callInfo -> p._lpr(args, callInfo) }
internal val PatternMapperFn._lpr by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_lpr(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the LPF envelope release time in seconds.
 *
 * Controls how quickly the low pass filter cutoff returns to baseline after the note ends.
 * Use with [lpattack], [lpdecay], [lpsustain], [lpenv].
 *
 * ```KlangScript
 * s("bd").lpf(200).lpenv(5000).lprelease(0.4)   // filter closes slowly after note
 * ```
 *
 * ```KlangScript
 * note("c4").lprelease("<0.05 1.0>")              // short vs long filter release per cycle
 * ```
 *
 * @param seconds Release time in seconds; omit to reinterpret the pattern's own values.
 * @return A [PatternMapperFn] that sets the LPF release time, or [SprudelPattern] when called on a pattern.
 * @param-tool seconds SprudelLpReleaseSequenceEditor
 * @alias lpr
 * @category effects
 * @tags lprelease, lpr, low pass filter, envelope, release
 */
@SprudelDsl
fun SprudelPattern.lprelease(seconds: PatternLike? = null): SprudelPattern =
    this._lprelease(listOfNotNull(seconds).asSprudelDslArgs())

/** Sets the LPF envelope release time on a string pattern. */
@SprudelDsl
fun String.lprelease(seconds: PatternLike? = null): SprudelPattern =
    this._lprelease(listOfNotNull(seconds).asSprudelDslArgs())

/** Creates a [PatternMapperFn] that sets the LPF envelope release time. */
@SprudelDsl
fun lprelease(seconds: PatternLike? = null): PatternMapperFn = _lprelease(listOfNotNull(seconds).asSprudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets the LPF envelope release time after the previous mapper. */
@SprudelDsl
fun PatternMapperFn.lprelease(seconds: PatternLike? = null): PatternMapperFn =
    _lprelease(listOfNotNull(seconds).asSprudelDslArgs())

/**
 * Alias for [lprelease]. Sets the LPF envelope release time.
 *
 * ```KlangScript
 * note("c4").lpf(200).lpr(0.4)   // alias for lprelease()
 * ```
 *
 * ```KlangScript
 * note("c4").apply(lpf(200).lpr(0.4))   // chained PatternMapperFn
 * ```
 *
 * @param seconds Release time in seconds; omit to reinterpret the pattern's own values.
 * @return A [PatternMapperFn] that sets the LPF release time, or [SprudelPattern] when called on a pattern.
 * @param-tool seconds SprudelLpReleaseSequenceEditor
 * @alias lprelease
 * @category effects
 * @tags lpr, lprelease, low pass filter, envelope, release
 */
@SprudelDsl
fun SprudelPattern.lpr(seconds: PatternLike? = null): SprudelPattern =
    this._lpr(listOfNotNull(seconds).asSprudelDslArgs())

/** Alias for [lprelease] on a string pattern. */
@SprudelDsl
fun String.lpr(seconds: PatternLike? = null): SprudelPattern =
    this._lpr(listOfNotNull(seconds).asSprudelDslArgs())

/** Creates a [PatternMapperFn] that sets the LPF envelope release time (alias for [lprelease]). */
@SprudelDsl
fun lpr(seconds: PatternLike? = null): PatternMapperFn = _lpr(listOfNotNull(seconds).asSprudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets LPF release (alias for [lprelease]) after the previous mapper. */
@SprudelDsl
fun PatternMapperFn.lpr(seconds: PatternLike? = null): PatternMapperFn =
    _lpr(listOfNotNull(seconds).asSprudelDslArgs())

// -- lpenv() / lpe() - Low Pass Filter Envelope Depth ------------------------------------------------------------------

private val lpenvMutation = voiceModifier { copy(lpenv = it?.asDoubleOrNull()) }

fun applyLpenv(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, lpenvMutation)
}

internal val _lpenv by dslPatternMapper { args, callInfo -> { p -> p._lpenv(args, callInfo) } }
internal val SprudelPattern._lpenv by dslPatternExtension { p, args, _ -> applyLpenv(p, args) }
internal val String._lpenv by dslStringExtension { p, args, callInfo -> p._lpenv(args, callInfo) }
internal val PatternMapperFn._lpenv by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_lpenv(args, callInfo))
}

internal val _lpe by dslPatternMapper { args, callInfo -> { p -> p._lpe(args, callInfo) } }
internal val SprudelPattern._lpe by dslPatternExtension { p, args, _ -> applyLpenv(p, args) }
internal val String._lpe by dslStringExtension { p, args, callInfo -> p._lpe(args, callInfo) }
internal val PatternMapperFn._lpe by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_lpe(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the LPF envelope depth (modulation amount).
 *
 * Controls how far above the base [lpf] cutoff the filter sweeps when the ADSR envelope
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
 * | `lpf(freq)` | Sets the **resting** cutoff — where the filter sits with no envelope |
 * | `lpattack / lpdecay / lpsustain / lprelease` | Shapes the **envelope curve** over time (0→1→sustain→0) |
 * | `lpenv(depth)` | Scales **how far** the envelope moves the cutoff |
 *
 * Example with `lpf(500).lpenv(3.0).lpattack(0.01).lpdecay(0.5).lpsustain(0.2).lprelease(0.3)`:
 *
 * | Phase | envValue | Cutoff |
 * |-------|----------|--------|
 * | Note start | 0.0 | 500 Hz |
 * | Attack peak | 1.0 | 500 × (1 + 3 × 1) = **2000 Hz** |
 * | Sustain | 0.2 | 500 × (1 + 3 × 0.2) = **800 Hz** |
 * | Release end | 0.0 | 500 Hz |
 *
 * ```KlangScript
 * s("bd").lpf(200).lpenv(3.0)                // sweeps up to 800 Hz at peak
 * ```
 *
 * ```KlangScript
 * note("c4").lpf(300).lpenv("<1.0 5.0>")     // subtle vs dramatic sweep per cycle
 * ```
 *
 * @param depth Envelope depth as a ratio (e.g. 1.0 = one octave sweep); omit to reinterpret the pattern's own values.
 * @return A [PatternMapperFn] that sets the LPF envelope depth, or [SprudelPattern] when called on a pattern.
 * @param-tool depth SprudelLpEnvSequenceEditor
 * @alias lpe
 * @category effects
 * @tags lpenv, lpe, low pass filter, envelope, depth, modulation
 */
@SprudelDsl
fun SprudelPattern.lpenv(depth: PatternLike? = null): SprudelPattern =
    this._lpenv(listOfNotNull(depth).asSprudelDslArgs())

/** Sets the LPF envelope depth/amount on a string pattern. */
@SprudelDsl
fun String.lpenv(depth: PatternLike? = null): SprudelPattern =
    this._lpenv(listOfNotNull(depth).asSprudelDslArgs())

/** Creates a [PatternMapperFn] that sets the LPF envelope depth. */
@SprudelDsl
fun lpenv(depth: PatternLike? = null): PatternMapperFn = _lpenv(listOfNotNull(depth).asSprudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets the LPF envelope depth after the previous mapper. */
@SprudelDsl
fun PatternMapperFn.lpenv(depth: PatternLike? = null): PatternMapperFn =
    _lpenv(listOfNotNull(depth).asSprudelDslArgs())

/**
 * Alias for [lpenv]. Sets the LPF envelope depth.
 *
 * ```KlangScript
 * note("c4").lpf(200).lpe(4000)   // alias for lpenv()
 * ```
 *
 * ```KlangScript
 * note("c4").apply(lpf(200).lpe(4000))   // chained PatternMapperFn
 * ```
 *
 * @param depth Envelope depth in Hz; omit to reinterpret the pattern's own values.
 * @return A [PatternMapperFn] that sets the LPF envelope depth, or [SprudelPattern] when called on a pattern.
 * @param-tool depth SprudelLpEnvSequenceEditor
 * @alias lpenv
 * @category effects
 * @tags lpe, lpenv, low pass filter, envelope, depth, modulation
 */
@SprudelDsl
fun SprudelPattern.lpe(depth: PatternLike? = null): SprudelPattern =
    this._lpe(listOfNotNull(depth).asSprudelDslArgs())

/** Alias for [lpenv] on a string pattern. */
@SprudelDsl
fun String.lpe(depth: PatternLike? = null): SprudelPattern =
    this._lpe(listOfNotNull(depth).asSprudelDslArgs())

/** Creates a [PatternMapperFn] that sets the LPF envelope depth (alias for [lpenv]). */
@SprudelDsl
fun lpe(depth: PatternLike? = null): PatternMapperFn = _lpe(listOfNotNull(depth).asSprudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets LPF envelope depth (alias for [lpenv]) after the previous mapper. */
@SprudelDsl
fun PatternMapperFn.lpe(depth: PatternLike? = null): PatternMapperFn =
    _lpe(listOfNotNull(depth).asSprudelDslArgs())

// -- hpattack() / hpa() - High Pass Filter Envelope Attack -------------------------------------------------------------

private val hpattackMutation = voiceModifier { copy(hpattack = it?.asDoubleOrNull()) }

fun applyHpattack(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, hpattackMutation)
}

internal val _hpattack by dslPatternMapper { args, callInfo -> { p -> p._hpattack(args, callInfo) } }
internal val SprudelPattern._hpattack by dslPatternExtension { p, args, _ -> applyHpattack(p, args) }
internal val String._hpattack by dslStringExtension { p, args, callInfo -> p._hpattack(args, callInfo) }
internal val PatternMapperFn._hpattack by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_hpattack(args, callInfo))
}

internal val _hpa by dslPatternMapper { args, callInfo -> { p -> p._hpa(args, callInfo) } }
internal val SprudelPattern._hpa by dslPatternExtension { p, args, _ -> applyHpattack(p, args) }
internal val String._hpa by dslStringExtension { p, args, callInfo -> p._hpa(args, callInfo) }
internal val PatternMapperFn._hpa by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_hpa(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the HPF envelope attack time in seconds.
 *
 * Controls how quickly the high pass filter cutoff sweeps from its baseline to the peak
 * at note onset. Use with [hpenv], [hpdecay], [hpsustain], [hprelease].
 *
 * ```KlangScript
 * s("sd").hpf(100).hpenv(2000).hpattack(0.1)   // filter opens over 100 ms
 * ```
 *
 * ```KlangScript
 * note("c4").hpattack("<0.01 0.5>")              // fast vs slow filter attack per cycle
 * ```
 *
 * @param seconds Attack time in seconds; omit to reinterpret the pattern's own values.
 * @return A [PatternMapperFn] that sets the HPF attack time, or [SprudelPattern] when called on a pattern.
 * @param-tool seconds SprudelHpAttackSequenceEditor
 * @alias hpa
 * @category effects
 * @tags hpattack, hpa, high pass filter, envelope, attack
 */
@SprudelDsl
fun SprudelPattern.hpattack(seconds: PatternLike? = null): SprudelPattern =
    this._hpattack(listOfNotNull(seconds).asSprudelDslArgs())

/** Sets the HPF envelope attack time on a string pattern. */
@SprudelDsl
fun String.hpattack(seconds: PatternLike? = null): SprudelPattern =
    this._hpattack(listOfNotNull(seconds).asSprudelDslArgs())

/** Creates a [PatternMapperFn] that sets the HPF envelope attack time. */
@SprudelDsl
fun hpattack(seconds: PatternLike? = null): PatternMapperFn = _hpattack(listOfNotNull(seconds).asSprudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets the HPF envelope attack time after the previous mapper. */
@SprudelDsl
fun PatternMapperFn.hpattack(seconds: PatternLike? = null): PatternMapperFn =
    _hpattack(listOfNotNull(seconds).asSprudelDslArgs())

/**
 * Alias for [hpattack]. Sets the HPF envelope attack time.
 *
 * ```KlangScript
 * note("c4").hpf(100).hpa(0.1)   // alias for hpattack()
 * ```
 *
 * ```KlangScript
 * note("c4").apply(hpf(100).hpa(0.1))   // chained PatternMapperFn
 * ```
 *
 * @param seconds Attack time in seconds; omit to reinterpret the pattern's own values.
 * @return A [PatternMapperFn] that sets the HPF attack time, or [SprudelPattern] when called on a pattern.
 * @param-tool seconds SprudelHpAttackSequenceEditor
 * @alias hpattack
 * @category effects
 * @tags hpa, hpattack, high pass filter, envelope, attack
 */
@SprudelDsl
fun SprudelPattern.hpa(seconds: PatternLike? = null): SprudelPattern =
    this._hpa(listOfNotNull(seconds).asSprudelDslArgs())

/** Alias for [hpattack] on a string pattern. */
@SprudelDsl
fun String.hpa(seconds: PatternLike? = null): SprudelPattern =
    this._hpa(listOfNotNull(seconds).asSprudelDslArgs())

/** Creates a [PatternMapperFn] that sets the HPF envelope attack time (alias for [hpattack]). */
@SprudelDsl
fun hpa(seconds: PatternLike? = null): PatternMapperFn = _hpa(listOfNotNull(seconds).asSprudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets HPF attack (alias for [hpattack]) after the previous mapper. */
@SprudelDsl
fun PatternMapperFn.hpa(seconds: PatternLike? = null): PatternMapperFn =
    _hpa(listOfNotNull(seconds).asSprudelDslArgs())

// -- hpdecay() / hpd() - High Pass Filter Envelope Decay ---------------------------------------------------------------

private val hpdecayMutation = voiceModifier { copy(hpdecay = it?.asDoubleOrNull()) }

fun applyHpdecay(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, hpdecayMutation)
}

internal val _hpdecay by dslPatternMapper { args, callInfo -> { p -> p._hpdecay(args, callInfo) } }
internal val SprudelPattern._hpdecay by dslPatternExtension { p, args, _ -> applyHpdecay(p, args) }
internal val String._hpdecay by dslStringExtension { p, args, callInfo -> p._hpdecay(args, callInfo) }
internal val PatternMapperFn._hpdecay by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_hpdecay(args, callInfo))
}

internal val _hpd by dslPatternMapper { args, callInfo -> { p -> p._hpd(args, callInfo) } }
internal val SprudelPattern._hpd by dslPatternExtension { p, args, _ -> applyHpdecay(p, args) }
internal val String._hpd by dslStringExtension { p, args, callInfo -> p._hpd(args, callInfo) }
internal val PatternMapperFn._hpd by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_hpd(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the HPF envelope decay time in seconds.
 *
 * Controls how quickly the filter cutoff moves from peak to sustain level after the attack.
 * Use with [hpattack], [hpsustain], [hprelease], [hpenv].
 *
 * ```KlangScript
 * s("sd").hpf(100).hpenv(2000).hpdecay(0.2)   // filter decays over 200 ms
 * ```
 *
 * ```KlangScript
 * note("c4").hpdecay("<0.05 0.5>")              // short vs long filter decay per cycle
 * ```
 *
 * @param seconds Decay time in seconds; omit to reinterpret the pattern's own values.
 * @return A [PatternMapperFn] that sets the HPF decay time, or [SprudelPattern] when called on a pattern.
 * @param-tool seconds SprudelHpDecaySequenceEditor
 * @alias hpd
 * @category effects
 * @tags hpdecay, hpd, high pass filter, envelope, decay
 */
@SprudelDsl
fun SprudelPattern.hpdecay(seconds: PatternLike? = null): SprudelPattern =
    this._hpdecay(listOfNotNull(seconds).asSprudelDslArgs())

/** Sets the HPF envelope decay time on a string pattern. */
@SprudelDsl
fun String.hpdecay(seconds: PatternLike? = null): SprudelPattern =
    this._hpdecay(listOfNotNull(seconds).asSprudelDslArgs())

/** Creates a [PatternMapperFn] that sets the HPF envelope decay time. */
@SprudelDsl
fun hpdecay(seconds: PatternLike? = null): PatternMapperFn = _hpdecay(listOfNotNull(seconds).asSprudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets the HPF envelope decay time after the previous mapper. */
@SprudelDsl
fun PatternMapperFn.hpdecay(seconds: PatternLike? = null): PatternMapperFn =
    _hpdecay(listOfNotNull(seconds).asSprudelDslArgs())

/**
 * Alias for [hpdecay]. Sets the HPF envelope decay time.
 *
 * ```KlangScript
 * note("c4").hpf(100).hpd(0.2)   // alias for hpdecay()
 * ```
 *
 * ```KlangScript
 * note("c4").apply(hpf(100).hpd(0.2))   // chained PatternMapperFn
 * ```
 *
 * @param seconds Decay time in seconds; omit to reinterpret the pattern's own values.
 * @return A [PatternMapperFn] that sets the HPF decay time, or [SprudelPattern] when called on a pattern.
 * @param-tool seconds SprudelHpDecaySequenceEditor
 * @alias hpdecay
 * @category effects
 * @tags hpd, hpdecay, high pass filter, envelope, decay
 */
@SprudelDsl
fun SprudelPattern.hpd(seconds: PatternLike? = null): SprudelPattern =
    this._hpd(listOfNotNull(seconds).asSprudelDslArgs())

/** Alias for [hpdecay] on a string pattern. */
@SprudelDsl
fun String.hpd(seconds: PatternLike? = null): SprudelPattern =
    this._hpd(listOfNotNull(seconds).asSprudelDslArgs())

/** Creates a [PatternMapperFn] that sets the HPF envelope decay time (alias for [hpdecay]). */
@SprudelDsl
fun hpd(seconds: PatternLike? = null): PatternMapperFn = _hpd(listOfNotNull(seconds).asSprudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets HPF decay (alias for [hpdecay]) after the previous mapper. */
@SprudelDsl
fun PatternMapperFn.hpd(seconds: PatternLike? = null): PatternMapperFn =
    _hpd(listOfNotNull(seconds).asSprudelDslArgs())

// -- hpsustain() / hps() - High Pass Filter Envelope Sustain -----------------------------------------------------------

private val hpsustainMutation = voiceModifier { copy(hpsustain = it?.asDoubleOrNull()) }

fun applyHpsustain(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, hpsustainMutation)
}

internal val _hpsustain by dslPatternMapper { args, callInfo -> { p -> p._hpsustain(args, callInfo) } }
internal val SprudelPattern._hpsustain by dslPatternExtension { p, args, _ -> applyHpsustain(p, args) }
internal val String._hpsustain by dslStringExtension { p, args, callInfo -> p._hpsustain(args, callInfo) }
internal val PatternMapperFn._hpsustain by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_hpsustain(args, callInfo))
}

internal val _hps by dslPatternMapper { args, callInfo -> { p -> p._hps(args, callInfo) } }
internal val SprudelPattern._hps by dslPatternExtension { p, args, _ -> applyHpsustain(p, args) }
internal val String._hps by dslStringExtension { p, args, callInfo -> p._hps(args, callInfo) }
internal val PatternMapperFn._hps by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_hps(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the HPF envelope sustain level (0–1).
 *
 * Controls the filter cutoff level during the sustained portion of the note. `1` holds the
 * filter open at the envelope peak; `0` closes it back to baseline. Use with
 * [hpattack]/[hpdecay]/[hprelease].
 *
 * ```KlangScript
 * note("c4").hpf(100).hpenv(3000).hpsustain(0.5)  // sustain at half depth
 * ```
 *
 * ```KlangScript
 * note("c4").hpsustain("<0 1>")                     // closed vs fully open sustain
 * ```
 *
 * @param level Sustain level (0–1); omit to reinterpret the pattern's own values.
 * @return A [PatternMapperFn] that sets the HPF sustain level, or [SprudelPattern] when called on a pattern.
 * @param-tool level SprudelHpSustainSequenceEditor
 * @alias hps
 * @category effects
 * @tags hpsustain, hps, high pass filter, envelope, sustain
 */
@SprudelDsl
fun SprudelPattern.hpsustain(level: PatternLike? = null): SprudelPattern =
    this._hpsustain(listOfNotNull(level).asSprudelDslArgs())

/** Sets the HPF envelope sustain level on a string pattern. */
@SprudelDsl
fun String.hpsustain(level: PatternLike? = null): SprudelPattern =
    this._hpsustain(listOfNotNull(level).asSprudelDslArgs())

/** Creates a [PatternMapperFn] that sets the HPF envelope sustain level. */
@SprudelDsl
fun hpsustain(level: PatternLike? = null): PatternMapperFn = _hpsustain(listOfNotNull(level).asSprudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets the HPF envelope sustain level after the previous mapper. */
@SprudelDsl
fun PatternMapperFn.hpsustain(level: PatternLike? = null): PatternMapperFn =
    _hpsustain(listOfNotNull(level).asSprudelDslArgs())

/**
 * Alias for [hpsustain]. Sets the HPF envelope sustain level.
 *
 * ```KlangScript
 * note("c4").hpf(100).hps(0.5)   // alias for hpsustain()
 * ```
 *
 * ```KlangScript
 * note("c4").apply(hpf(100).hps(0.5))   // chained PatternMapperFn
 * ```
 *
 * @param level Sustain level (0–1); omit to reinterpret the pattern's own values.
 * @return A [PatternMapperFn] that sets the HPF sustain level, or [SprudelPattern] when called on a pattern.
 * @param-tool level SprudelHpSustainSequenceEditor
 * @alias hpsustain
 * @category effects
 * @tags hps, hpsustain, high pass filter, envelope, sustain
 */
@SprudelDsl
fun SprudelPattern.hps(level: PatternLike? = null): SprudelPattern =
    this._hps(listOfNotNull(level).asSprudelDslArgs())

/** Alias for [hpsustain] on a string pattern. */
@SprudelDsl
fun String.hps(level: PatternLike? = null): SprudelPattern =
    this._hps(listOfNotNull(level).asSprudelDslArgs())

/** Creates a [PatternMapperFn] that sets the HPF envelope sustain level (alias for [hpsustain]). */
@SprudelDsl
fun hps(level: PatternLike? = null): PatternMapperFn = _hps(listOfNotNull(level).asSprudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets HPF sustain (alias for [hpsustain]) after the previous mapper. */
@SprudelDsl
fun PatternMapperFn.hps(level: PatternLike? = null): PatternMapperFn =
    _hps(listOfNotNull(level).asSprudelDslArgs())

// -- hprelease() / hpr() - High Pass Filter Envelope Release -----------------------------------------------------------

private val hpreleaseMutation = voiceModifier { copy(hprelease = it?.asDoubleOrNull()) }

fun applyHprelease(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, hpreleaseMutation)
}

internal val _hprelease by dslPatternMapper { args, callInfo -> { p -> p._hprelease(args, callInfo) } }
internal val SprudelPattern._hprelease by dslPatternExtension { p, args, _ -> applyHprelease(p, args) }
internal val String._hprelease by dslStringExtension { p, args, callInfo -> p._hprelease(args, callInfo) }
internal val PatternMapperFn._hprelease by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_hprelease(args, callInfo))
}

internal val _hpr by dslPatternMapper { args, callInfo -> { p -> p._hpr(args, callInfo) } }
internal val SprudelPattern._hpr by dslPatternExtension { p, args, _ -> applyHprelease(p, args) }
internal val String._hpr by dslStringExtension { p, args, callInfo -> p._hpr(args, callInfo) }
internal val PatternMapperFn._hpr by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_hpr(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the HPF envelope release time in seconds.
 *
 * Controls how quickly the high pass filter cutoff returns to baseline after the note ends.
 * Use with [hpattack], [hpdecay], [hpsustain], [hpenv].
 *
 * ```KlangScript
 * s("sd").hpf(100).hpenv(2000).hprelease(0.4)   // filter closes slowly after note
 * ```
 *
 * ```KlangScript
 * note("c4").hprelease("<0.05 1.0>")              // short vs long filter release per cycle
 * ```
 *
 * @param seconds Release time in seconds; omit to reinterpret the pattern's own values.
 * @return A [PatternMapperFn] that sets the HPF release time, or [SprudelPattern] when called on a pattern.
 * @param-tool seconds SprudelHpReleaseSequenceEditor
 * @alias hpr
 * @category effects
 * @tags hprelease, hpr, high pass filter, envelope, release
 */
@SprudelDsl
fun SprudelPattern.hprelease(seconds: PatternLike? = null): SprudelPattern =
    this._hprelease(listOfNotNull(seconds).asSprudelDslArgs())

/** Sets the HPF envelope release time on a string pattern. */
@SprudelDsl
fun String.hprelease(seconds: PatternLike? = null): SprudelPattern =
    this._hprelease(listOfNotNull(seconds).asSprudelDslArgs())

/** Creates a [PatternMapperFn] that sets the HPF envelope release time. */
@SprudelDsl
fun hprelease(seconds: PatternLike? = null): PatternMapperFn = _hprelease(listOfNotNull(seconds).asSprudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets the HPF envelope release time after the previous mapper. */
@SprudelDsl
fun PatternMapperFn.hprelease(seconds: PatternLike? = null): PatternMapperFn =
    _hprelease(listOfNotNull(seconds).asSprudelDslArgs())

/**
 * Alias for [hprelease]. Sets the HPF envelope release time.
 *
 * ```KlangScript
 * note("c4").hpf(100).hpr(0.4)   // alias for hprelease()
 * ```
 *
 * ```KlangScript
 * note("c4").apply(hpf(100).hpr(0.4))   // chained PatternMapperFn
 * ```
 *
 * @param seconds Release time in seconds; omit to reinterpret the pattern's own values.
 * @return A [PatternMapperFn] that sets the HPF release time, or [SprudelPattern] when called on a pattern.
 * @param-tool seconds SprudelHpReleaseSequenceEditor
 * @alias hprelease
 * @category effects
 * @tags hpr, hprelease, high pass filter, envelope, release
 */
@SprudelDsl
fun SprudelPattern.hpr(seconds: PatternLike? = null): SprudelPattern =
    this._hpr(listOfNotNull(seconds).asSprudelDslArgs())

/** Alias for [hprelease] on a string pattern. */
@SprudelDsl
fun String.hpr(seconds: PatternLike? = null): SprudelPattern =
    this._hpr(listOfNotNull(seconds).asSprudelDslArgs())

/** Creates a [PatternMapperFn] that sets the HPF envelope release time (alias for [hprelease]). */
@SprudelDsl
fun hpr(seconds: PatternLike? = null): PatternMapperFn = _hpr(listOfNotNull(seconds).asSprudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets HPF release (alias for [hprelease]) after the previous mapper. */
@SprudelDsl
fun PatternMapperFn.hpr(seconds: PatternLike? = null): PatternMapperFn =
    _hpr(listOfNotNull(seconds).asSprudelDslArgs())

// -- hpenv() / hpe() - High Pass Filter Envelope Depth -----------------------------------------------------------------

private val hpenvMutation = voiceModifier { copy(hpenv = it?.asDoubleOrNull()) }

fun applyHpenv(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, hpenvMutation)
}

internal val _hpenv by dslPatternMapper { args, callInfo -> { p -> p._hpenv(args, callInfo) } }
internal val SprudelPattern._hpenv by dslPatternExtension { p, args, _ -> applyHpenv(p, args) }
internal val String._hpenv by dslStringExtension { p, args, callInfo -> p._hpenv(args, callInfo) }
internal val PatternMapperFn._hpenv by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_hpenv(args, callInfo))
}

internal val _hpe by dslPatternMapper { args, callInfo -> { p -> p._hpe(args, callInfo) } }
internal val SprudelPattern._hpe by dslPatternExtension { p, args, _ -> applyHpenv(p, args) }
internal val String._hpe by dslStringExtension { p, args, callInfo -> p._hpe(args, callInfo) }
internal val PatternMapperFn._hpe by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_hpe(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the HPF envelope depth (modulation amount).
 *
 * Controls how far above the base [hpf] cutoff the filter sweeps when the ADSR envelope
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
 * | `hpf(freq)` | Sets the **resting** cutoff — where the filter sits with no envelope |
 * | `hpattack / hpdecay / hpsustain / hprelease` | Shapes the **envelope curve** over time (0→1→sustain→0) |
 * | `hpenv(depth)` | Scales **how far** the envelope moves the cutoff |
 *
 * Example with `hpf(500).hpenv(3.0).hpattack(0.01).hpdecay(0.5).hpsustain(0.2).hprelease(0.3)`:
 *
 * | Phase | envValue | Cutoff |
 * |-------|----------|--------|
 * | Note start | 0.0 | 500 Hz |
 * | Attack peak | 1.0 | 500 × (1 + 3 × 1) = **2000 Hz** |
 * | Sustain | 0.2 | 500 × (1 + 3 × 0.2) = **800 Hz** |
 * | Release end | 0.0 | 500 Hz |
 *
 * ```KlangScript
 * s("sd").hpf(100).hpenv(3.0)                // sweeps up to 400 Hz at peak
 * ```
 *
 * ```KlangScript
 * note("c4").hpf(200).hpenv("<1.0 5.0>")     // subtle vs dramatic sweep per cycle
 * ```
 *
 * @param depth Envelope depth as a ratio (e.g. 1.0 = one octave sweep); omit to reinterpret the pattern's own values.
 * @return A [PatternMapperFn] that sets the HPF envelope depth, or [SprudelPattern] when called on a pattern.
 * @param-tool depth SprudelHpEnvSequenceEditor
 * @alias hpe
 * @category effects
 * @tags hpenv, hpe, high pass filter, envelope, depth, modulation
 */
@SprudelDsl
fun SprudelPattern.hpenv(depth: PatternLike? = null): SprudelPattern =
    this._hpenv(listOfNotNull(depth).asSprudelDslArgs())

/** Sets the HPF envelope depth/amount on a string pattern. */
@SprudelDsl
fun String.hpenv(depth: PatternLike? = null): SprudelPattern =
    this._hpenv(listOfNotNull(depth).asSprudelDslArgs())

/** Creates a [PatternMapperFn] that sets the HPF envelope depth. */
@SprudelDsl
fun hpenv(depth: PatternLike? = null): PatternMapperFn = _hpenv(listOfNotNull(depth).asSprudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets the HPF envelope depth after the previous mapper. */
@SprudelDsl
fun PatternMapperFn.hpenv(depth: PatternLike? = null): PatternMapperFn =
    _hpenv(listOfNotNull(depth).asSprudelDslArgs())

/**
 * Alias for [hpenv]. Sets the HPF envelope depth.
 *
 * ```KlangScript
 * note("c4").hpf(200).hpe(3000)   // alias for hpenv()
 * ```
 *
 * ```KlangScript
 * note("c4").apply(hpf(200).hpe(3000))   // chained PatternMapperFn
 * ```
 *
 * @param depth Envelope depth in Hz; omit to reinterpret the pattern's own values.
 * @return A [PatternMapperFn] that sets the HPF envelope depth, or [SprudelPattern] when called on a pattern.
 * @param-tool depth SprudelHpEnvSequenceEditor
 * @alias hpenv
 * @category effects
 * @tags hpe, hpenv, high pass filter, envelope, depth, modulation
 */
@SprudelDsl
fun SprudelPattern.hpe(depth: PatternLike? = null): SprudelPattern =
    this._hpe(listOfNotNull(depth).asSprudelDslArgs())

/** Alias for [hpenv] on a string pattern. */
@SprudelDsl
fun String.hpe(depth: PatternLike? = null): SprudelPattern =
    this._hpe(listOfNotNull(depth).asSprudelDslArgs())

/** Creates a [PatternMapperFn] that sets the HPF envelope depth (alias for [hpenv]). */
@SprudelDsl
fun hpe(depth: PatternLike? = null): PatternMapperFn = _hpe(listOfNotNull(depth).asSprudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets HPF envelope depth (alias for [hpenv]) after the previous mapper. */
@SprudelDsl
fun PatternMapperFn.hpe(depth: PatternLike? = null): PatternMapperFn =
    _hpe(listOfNotNull(depth).asSprudelDslArgs())

// -- bpattack() / bpa() - Band Pass Filter Envelope Attack -------------------------------------------------------------

private val bpattackMutation = voiceModifier { copy(bpattack = it?.asDoubleOrNull()) }

fun applyBpattack(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, bpattackMutation)
}

internal val _bpattack by dslPatternMapper { args, callInfo -> { p -> p._bpattack(args, callInfo) } }
internal val SprudelPattern._bpattack by dslPatternExtension { p, args, _ -> applyBpattack(p, args) }
internal val String._bpattack by dslStringExtension { p, args, callInfo -> p._bpattack(args, callInfo) }
internal val PatternMapperFn._bpattack by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_bpattack(args, callInfo))
}

internal val _bpa by dslPatternMapper { args, callInfo -> { p -> p._bpa(args, callInfo) } }
internal val SprudelPattern._bpa by dslPatternExtension { p, args, _ -> applyBpattack(p, args) }
internal val String._bpa by dslStringExtension { p, args, callInfo -> p._bpa(args, callInfo) }
internal val PatternMapperFn._bpa by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_bpa(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the BPF envelope attack time in seconds.
 *
 * Controls how quickly the band pass filter centre frequency sweeps from its baseline to
 * the peak at note onset. Use with [bpenv], [bpdecay], [bpsustain], [bprelease].
 *
 * ```KlangScript
 * s("sd").bandf(500).bpenv(2000).bpattack(0.1)   // filter opens over 100 ms
 * ```
 *
 * ```KlangScript
 * note("c4").bpattack("<0.01 0.5>")               // fast vs slow filter attack per cycle
 * ```
 *
 * @param seconds Attack time in seconds; omit to reinterpret the pattern's own values.
 * @return A [PatternMapperFn] that sets the BPF attack time, or [SprudelPattern] when called on a pattern.
 * @param-tool seconds SprudelBpAttackSequenceEditor
 * @alias bpa
 * @category effects
 * @tags bpattack, bpa, band pass filter, envelope, attack
 */
@SprudelDsl
fun SprudelPattern.bpattack(seconds: PatternLike? = null): SprudelPattern =
    this._bpattack(listOfNotNull(seconds).asSprudelDslArgs())

/** Sets the BPF envelope attack time on a string pattern. */
@SprudelDsl
fun String.bpattack(seconds: PatternLike? = null): SprudelPattern =
    this._bpattack(listOfNotNull(seconds).asSprudelDslArgs())

/** Creates a [PatternMapperFn] that sets the BPF envelope attack time. */
@SprudelDsl
fun bpattack(seconds: PatternLike? = null): PatternMapperFn = _bpattack(listOfNotNull(seconds).asSprudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets the BPF envelope attack time after the previous mapper. */
@SprudelDsl
fun PatternMapperFn.bpattack(seconds: PatternLike? = null): PatternMapperFn =
    _bpattack(listOfNotNull(seconds).asSprudelDslArgs())

/**
 * Alias for [bpattack]. Sets the BPF envelope attack time.
 *
 * ```KlangScript
 * note("c4").bandf(500).bpa(0.1)   // alias for bpattack()
 * ```
 *
 * ```KlangScript
 * note("c4").apply(bandf(500).bpa(0.1))   // chained PatternMapperFn
 * ```
 *
 * @param seconds Attack time in seconds; omit to reinterpret the pattern's own values.
 * @return A [PatternMapperFn] that sets the BPF attack time, or [SprudelPattern] when called on a pattern.
 * @param-tool seconds SprudelBpAttackSequenceEditor
 * @alias bpattack
 * @category effects
 * @tags bpa, bpattack, band pass filter, envelope, attack
 */
@SprudelDsl
fun SprudelPattern.bpa(seconds: PatternLike? = null): SprudelPattern =
    this._bpa(listOfNotNull(seconds).asSprudelDslArgs())

/** Alias for [bpattack] on a string pattern. */
@SprudelDsl
fun String.bpa(seconds: PatternLike? = null): SprudelPattern =
    this._bpa(listOfNotNull(seconds).asSprudelDslArgs())

/** Creates a [PatternMapperFn] that sets the BPF envelope attack time (alias for [bpattack]). */
@SprudelDsl
fun bpa(seconds: PatternLike? = null): PatternMapperFn = _bpa(listOfNotNull(seconds).asSprudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets BPF attack (alias for [bpattack]) after the previous mapper. */
@SprudelDsl
fun PatternMapperFn.bpa(seconds: PatternLike? = null): PatternMapperFn =
    _bpa(listOfNotNull(seconds).asSprudelDslArgs())

// -- bpdecay() / bpd() - Band Pass Filter Envelope Decay ---------------------------------------------------------------

private val bpdecayMutation = voiceModifier { copy(bpdecay = it?.asDoubleOrNull()) }

fun applyBpdecay(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, bpdecayMutation)
}

internal val _bpdecay by dslPatternMapper { args, callInfo -> { p -> p._bpdecay(args, callInfo) } }
internal val SprudelPattern._bpdecay by dslPatternExtension { p, args, _ -> applyBpdecay(p, args) }
internal val String._bpdecay by dslStringExtension { p, args, callInfo -> p._bpdecay(args, callInfo) }
internal val PatternMapperFn._bpdecay by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_bpdecay(args, callInfo))
}

internal val _bpd by dslPatternMapper { args, callInfo -> { p -> p._bpd(args, callInfo) } }
internal val SprudelPattern._bpd by dslPatternExtension { p, args, _ -> applyBpdecay(p, args) }
internal val String._bpd by dslStringExtension { p, args, callInfo -> p._bpd(args, callInfo) }
internal val PatternMapperFn._bpd by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_bpd(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the BPF envelope decay time in seconds.
 *
 * Controls how quickly the filter centre frequency moves from peak to sustain level after
 * the attack. Use with [bpattack], [bpsustain], [bprelease], [bpenv].
 *
 * ```KlangScript
 * s("sd").bandf(500).bpenv(2000).bpdecay(0.2)   // filter decays over 200 ms
 * ```
 *
 * ```KlangScript
 * note("c4").bpdecay("<0.05 0.5>")               // short vs long filter decay per cycle
 * ```
 *
 * @param seconds Decay time in seconds; omit to reinterpret the pattern's own values.
 * @return A [PatternMapperFn] that sets the BPF decay time, or [SprudelPattern] when called on a pattern.
 * @param-tool seconds SprudelBpDecaySequenceEditor
 * @alias bpd
 * @category effects
 * @tags bpdecay, bpd, band pass filter, envelope, decay
 */
@SprudelDsl
fun SprudelPattern.bpdecay(seconds: PatternLike? = null): SprudelPattern =
    this._bpdecay(listOfNotNull(seconds).asSprudelDslArgs())

/** Sets the BPF envelope decay time on a string pattern. */
@SprudelDsl
fun String.bpdecay(seconds: PatternLike? = null): SprudelPattern =
    this._bpdecay(listOfNotNull(seconds).asSprudelDslArgs())

/** Creates a [PatternMapperFn] that sets the BPF envelope decay time. */
@SprudelDsl
fun bpdecay(seconds: PatternLike? = null): PatternMapperFn = _bpdecay(listOfNotNull(seconds).asSprudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets the BPF envelope decay time after the previous mapper. */
@SprudelDsl
fun PatternMapperFn.bpdecay(seconds: PatternLike? = null): PatternMapperFn =
    _bpdecay(listOfNotNull(seconds).asSprudelDslArgs())

/**
 * Alias for [bpdecay]. Sets the BPF envelope decay time.
 *
 * ```KlangScript
 * note("c4").bandf(500).bpd(0.2)   // alias for bpdecay()
 * ```
 *
 * ```KlangScript
 * note("c4").apply(bandf(500).bpd(0.2))   // chained PatternMapperFn
 * ```
 *
 * @param seconds Decay time in seconds; omit to reinterpret the pattern's own values.
 * @return A [PatternMapperFn] that sets the BPF decay time, or [SprudelPattern] when called on a pattern.
 * @param-tool seconds SprudelBpDecaySequenceEditor
 * @alias bpdecay
 * @category effects
 * @tags bpd, bpdecay, band pass filter, envelope, decay
 */
@SprudelDsl
fun SprudelPattern.bpd(seconds: PatternLike? = null): SprudelPattern =
    this._bpd(listOfNotNull(seconds).asSprudelDslArgs())

/** Alias for [bpdecay] on a string pattern. */
@SprudelDsl
fun String.bpd(seconds: PatternLike? = null): SprudelPattern =
    this._bpd(listOfNotNull(seconds).asSprudelDslArgs())

/** Creates a [PatternMapperFn] that sets the BPF envelope decay time (alias for [bpdecay]). */
@SprudelDsl
fun bpd(seconds: PatternLike? = null): PatternMapperFn = _bpd(listOfNotNull(seconds).asSprudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets BPF decay (alias for [bpdecay]) after the previous mapper. */
@SprudelDsl
fun PatternMapperFn.bpd(seconds: PatternLike? = null): PatternMapperFn =
    _bpd(listOfNotNull(seconds).asSprudelDslArgs())

// -- bpsustain() / bps() - Band Pass Filter Envelope Sustain -----------------------------------------------------------

private val bpsustainMutation = voiceModifier { copy(bpsustain = it?.asDoubleOrNull()) }

fun applyBpsustain(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, bpsustainMutation)
}

internal val _bpsustain by dslPatternMapper { args, callInfo -> { p -> p._bpsustain(args, callInfo) } }
internal val SprudelPattern._bpsustain by dslPatternExtension { p, args, _ -> applyBpsustain(p, args) }
internal val String._bpsustain by dslStringExtension { p, args, callInfo -> p._bpsustain(args, callInfo) }
internal val PatternMapperFn._bpsustain by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_bpsustain(args, callInfo))
}

internal val _bps by dslPatternMapper { args, callInfo -> { p -> p._bps(args, callInfo) } }
internal val SprudelPattern._bps by dslPatternExtension { p, args, _ -> applyBpsustain(p, args) }
internal val String._bps by dslStringExtension { p, args, callInfo -> p._bps(args, callInfo) }
internal val PatternMapperFn._bps by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_bps(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the BPF envelope sustain level (0–1).
 *
 * Controls the filter centre frequency level during the sustained portion of the note.
 * `1` holds the filter at the envelope peak; `0` returns to baseline. Use with
 * [bpattack]/[bpdecay]/[bprelease].
 *
 * ```KlangScript
 * note("c4").bandf(500).bpenv(3000).bpsustain(0.5)  // sustain at half depth
 * ```
 *
 * ```KlangScript
 * note("c4").bpsustain("<0 1>")                      // closed vs fully open sustain
 * ```
 *
 * @param level Sustain level (0–1); omit to reinterpret the pattern's own values.
 * @return A [PatternMapperFn] that sets the BPF sustain level, or [SprudelPattern] when called on a pattern.
 * @param-tool level SprudelBpSustainSequenceEditor
 * @alias bps
 * @category effects
 * @tags bpsustain, bps, band pass filter, envelope, sustain
 */
@SprudelDsl
fun SprudelPattern.bpsustain(level: PatternLike? = null): SprudelPattern =
    this._bpsustain(listOfNotNull(level).asSprudelDslArgs())

/** Sets the BPF envelope sustain level on a string pattern. */
@SprudelDsl
fun String.bpsustain(level: PatternLike? = null): SprudelPattern =
    this._bpsustain(listOfNotNull(level).asSprudelDslArgs())

/** Creates a [PatternMapperFn] that sets the BPF envelope sustain level. */
@SprudelDsl
fun bpsustain(level: PatternLike? = null): PatternMapperFn = _bpsustain(listOfNotNull(level).asSprudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets the BPF envelope sustain level after the previous mapper. */
@SprudelDsl
fun PatternMapperFn.bpsustain(level: PatternLike? = null): PatternMapperFn =
    _bpsustain(listOfNotNull(level).asSprudelDslArgs())

/**
 * Alias for [bpsustain]. Sets the BPF envelope sustain level.
 *
 * ```KlangScript
 * note("c4").bandf(500).bps(0.5)   // alias for bpsustain()
 * ```
 *
 * ```KlangScript
 * note("c4").apply(bandf(500).bps(0.5))   // chained PatternMapperFn
 * ```
 *
 * @param level Sustain level (0–1); omit to reinterpret the pattern's own values.
 * @return A [PatternMapperFn] that sets the BPF sustain level, or [SprudelPattern] when called on a pattern.
 * @param-tool level SprudelBpSustainSequenceEditor
 * @alias bpsustain
 * @category effects
 * @tags bps, bpsustain, band pass filter, envelope, sustain
 */
@SprudelDsl
fun SprudelPattern.bps(level: PatternLike? = null): SprudelPattern =
    this._bps(listOfNotNull(level).asSprudelDslArgs())

/** Alias for [bpsustain] on a string pattern. */
@SprudelDsl
fun String.bps(level: PatternLike? = null): SprudelPattern =
    this._bps(listOfNotNull(level).asSprudelDslArgs())

/** Creates a [PatternMapperFn] that sets the BPF envelope sustain level (alias for [bpsustain]). */
@SprudelDsl
fun bps(level: PatternLike? = null): PatternMapperFn = _bps(listOfNotNull(level).asSprudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets BPF sustain (alias for [bpsustain]) after the previous mapper. */
@SprudelDsl
fun PatternMapperFn.bps(level: PatternLike? = null): PatternMapperFn =
    _bps(listOfNotNull(level).asSprudelDslArgs())

// -- bprelease() / bpr() - Band Pass Filter Envelope Release -----------------------------------------------------------

private val bpreleaseMutation = voiceModifier { copy(bprelease = it?.asDoubleOrNull()) }

fun applyBprelease(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, bpreleaseMutation)
}

internal val _bprelease by dslPatternMapper { args, callInfo -> { p -> p._bprelease(args, callInfo) } }
internal val SprudelPattern._bprelease by dslPatternExtension { p, args, _ -> applyBprelease(p, args) }
internal val String._bprelease by dslStringExtension { p, args, callInfo -> p._bprelease(args, callInfo) }
internal val PatternMapperFn._bprelease by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_bprelease(args, callInfo))
}

internal val _bpr by dslPatternMapper { args, callInfo -> { p -> p._bpr(args, callInfo) } }
internal val SprudelPattern._bpr by dslPatternExtension { p, args, _ -> applyBprelease(p, args) }
internal val String._bpr by dslStringExtension { p, args, callInfo -> p._bpr(args, callInfo) }
internal val PatternMapperFn._bpr by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_bpr(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the BPF envelope release time in seconds.
 *
 * Controls how quickly the band pass filter centre frequency returns to baseline after the
 * note ends. Use with [bpattack], [bpdecay], [bpsustain], [bpenv].
 *
 * ```KlangScript
 * s("sd").bandf(500).bpenv(2000).bprelease(0.4)   // filter closes slowly after note
 * ```
 *
 * ```KlangScript
 * note("c4").bprelease("<0.05 1.0>")               // short vs long filter release per cycle
 * ```
 *
 * @param seconds Release time in seconds; omit to reinterpret the pattern's own values.
 * @return A [PatternMapperFn] that sets the BPF release time, or [SprudelPattern] when called on a pattern.
 * @param-tool seconds SprudelBpReleaseSequenceEditor
 * @alias bpr
 * @category effects
 * @tags bprelease, bpr, band pass filter, envelope, release
 */
@SprudelDsl
fun SprudelPattern.bprelease(seconds: PatternLike? = null): SprudelPattern =
    this._bprelease(listOfNotNull(seconds).asSprudelDslArgs())

/** Sets the BPF envelope release time on a string pattern. */
@SprudelDsl
fun String.bprelease(seconds: PatternLike? = null): SprudelPattern =
    this._bprelease(listOfNotNull(seconds).asSprudelDslArgs())

/** Creates a [PatternMapperFn] that sets the BPF envelope release time. */
@SprudelDsl
fun bprelease(seconds: PatternLike? = null): PatternMapperFn = _bprelease(listOfNotNull(seconds).asSprudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets the BPF envelope release time after the previous mapper. */
@SprudelDsl
fun PatternMapperFn.bprelease(seconds: PatternLike? = null): PatternMapperFn =
    _bprelease(listOfNotNull(seconds).asSprudelDslArgs())

/**
 * Alias for [bprelease]. Sets the BPF envelope release time.
 *
 * ```KlangScript
 * note("c4").bandf(500).bpr(0.4)   // alias for bprelease()
 * ```
 *
 * ```KlangScript
 * note("c4").apply(bandf(500).bpr(0.4))   // chained PatternMapperFn
 * ```
 *
 * @param seconds Release time in seconds; omit to reinterpret the pattern's own values.
 * @return A [PatternMapperFn] that sets the BPF release time, or [SprudelPattern] when called on a pattern.
 * @param-tool seconds SprudelBpReleaseSequenceEditor
 * @alias bprelease
 * @category effects
 * @tags bpr, bprelease, band pass filter, envelope, release
 */
@SprudelDsl
fun SprudelPattern.bpr(seconds: PatternLike? = null): SprudelPattern =
    this._bpr(listOfNotNull(seconds).asSprudelDslArgs())

/** Alias for [bprelease] on a string pattern. */
@SprudelDsl
fun String.bpr(seconds: PatternLike? = null): SprudelPattern =
    this._bpr(listOfNotNull(seconds).asSprudelDslArgs())

/** Creates a [PatternMapperFn] that sets the BPF envelope release time (alias for [bprelease]). */
@SprudelDsl
fun bpr(seconds: PatternLike? = null): PatternMapperFn = _bpr(listOfNotNull(seconds).asSprudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets BPF release (alias for [bprelease]) after the previous mapper. */
@SprudelDsl
fun PatternMapperFn.bpr(seconds: PatternLike? = null): PatternMapperFn =
    _bpr(listOfNotNull(seconds).asSprudelDslArgs())

// -- bpenv() / bpe() - Band Pass Filter Envelope Depth -----------------------------------------------------------------

private val bpenvMutation = voiceModifier { copy(bpenv = it?.asDoubleOrNull()) }

fun applyBpenv(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, bpenvMutation)
}

internal val _bpenv by dslPatternMapper { args, callInfo -> { p -> p._bpenv(args, callInfo) } }
internal val SprudelPattern._bpenv by dslPatternExtension { p, args, _ -> applyBpenv(p, args) }
internal val String._bpenv by dslStringExtension { p, args, callInfo -> p._bpenv(args, callInfo) }
internal val PatternMapperFn._bpenv by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_bpenv(args, callInfo))
}

internal val _bpe by dslPatternMapper { args, callInfo -> { p -> p._bpe(args, callInfo) } }
internal val SprudelPattern._bpe by dslPatternExtension { p, args, _ -> applyBpenv(p, args) }
internal val String._bpe by dslStringExtension { p, args, callInfo -> p._bpe(args, callInfo) }
internal val PatternMapperFn._bpe by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_bpe(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the BPF envelope depth (modulation amount).
 *
 * Controls how far above the base [bandf] centre frequency the filter sweeps when the ADSR envelope
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
 * | `bandf(freq)` | Sets the **resting** centre frequency — where the filter sits with no envelope |
 * | `bpattack / bpdecay / bpsustain / bprelease` | Shapes the **envelope curve** over time (0→1→sustain→0) |
 * | `bpenv(depth)` | Scales **how far** the envelope moves the centre frequency |
 *
 * Example with `bandf(500).bpenv(3.0).bpattack(0.01).bpdecay(0.5).bpsustain(0.2).bprelease(0.3)`:
 *
 * | Phase | envValue | Centre freq |
 * |-------|----------|-------------|
 * | Note start | 0.0 | 500 Hz |
 * | Attack peak | 1.0 | 500 × (1 + 3 × 1) = **2000 Hz** |
 * | Sustain | 0.2 | 500 × (1 + 3 × 0.2) = **800 Hz** |
 * | Release end | 0.0 | 500 Hz |
 *
 * ```KlangScript
 * s("sd").bandf(500).bpenv(3.0)                // sweeps up to 2000 Hz at peak
 * ```
 *
 * ```KlangScript
 * note("c4").bandf(300).bpenv("<1.0 5.0>")     // subtle vs dramatic sweep per cycle
 * ```
 *
 * @param depth Envelope depth as a ratio (e.g. 1.0 = one octave sweep); omit to reinterpret the pattern's own values.
 * @return A [PatternMapperFn] that sets the BPF envelope depth, or [SprudelPattern] when called on a pattern.
 * @param-tool depth SprudelBpEnvSequenceEditor
 * @alias bpe
 * @category effects
 * @tags bpenv, bpe, band pass filter, envelope, depth, modulation
 */
@SprudelDsl
fun SprudelPattern.bpenv(depth: PatternLike? = null): SprudelPattern =
    this._bpenv(listOfNotNull(depth).asSprudelDslArgs())

/** Sets the BPF envelope depth/amount on a string pattern. */
@SprudelDsl
fun String.bpenv(depth: PatternLike? = null): SprudelPattern =
    this._bpenv(listOfNotNull(depth).asSprudelDslArgs())

/** Creates a [PatternMapperFn] that sets the BPF envelope depth. */
@SprudelDsl
fun bpenv(depth: PatternLike? = null): PatternMapperFn = _bpenv(listOfNotNull(depth).asSprudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets the BPF envelope depth after the previous mapper. */
@SprudelDsl
fun PatternMapperFn.bpenv(depth: PatternLike? = null): PatternMapperFn =
    _bpenv(listOfNotNull(depth).asSprudelDslArgs())

/**
 * Alias for [bpenv]. Sets the BPF envelope depth.
 *
 * ```KlangScript
 * note("c4").bandf(200).bpe(3000)   // alias for bpenv()
 * ```
 *
 * ```KlangScript
 * note("c4").apply(bandf(200).bpe(3000))   // chained PatternMapperFn
 * ```
 *
 * @param depth Envelope depth in Hz; omit to reinterpret the pattern's own values.
 * @return A [PatternMapperFn] that sets the BPF envelope depth, or [SprudelPattern] when called on a pattern.
 * @param-tool depth SprudelBpEnvSequenceEditor
 * @alias bpenv
 * @category effects
 * @tags bpe, bpenv, band pass filter, envelope, depth, modulation
 */
@SprudelDsl
fun SprudelPattern.bpe(depth: PatternLike? = null): SprudelPattern =
    this._bpe(listOfNotNull(depth).asSprudelDslArgs())

/** Alias for [bpenv] on a string pattern. */
@SprudelDsl
fun String.bpe(depth: PatternLike? = null): SprudelPattern =
    this._bpe(listOfNotNull(depth).asSprudelDslArgs())

/** Creates a [PatternMapperFn] that sets the BPF envelope depth (alias for [bpenv]). */
@SprudelDsl
fun bpe(depth: PatternLike? = null): PatternMapperFn = _bpe(listOfNotNull(depth).asSprudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets BPF envelope depth (alias for [bpenv]) after the previous mapper. */
@SprudelDsl
fun PatternMapperFn.bpe(depth: PatternLike? = null): PatternMapperFn =
    _bpe(listOfNotNull(depth).asSprudelDslArgs())
