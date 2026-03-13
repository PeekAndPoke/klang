@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions")

package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel._applyControlFromParams
import io.peekandpoke.klang.strudel._liftOrReinterpretNumericalField
import io.peekandpoke.klang.strudel.lang.StrudelDslArg.Companion.asStrudelDslArgs

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in StrudelRegistry.
 */
var strudelLangFiltersInit = false

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

fun applyLpf(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
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
internal val StrudelPattern._lpf by dslPatternExtension { p, args, _ -> applyLpf(p, args) }
internal val String._lpf by dslStringExtension { p, args, callInfo -> p._lpf(args, callInfo) }
internal val PatternMapperFn._lpf by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_lpf(args, callInfo))
}

internal val _cutoff by dslPatternMapper { args, callInfo -> { p -> p._cutoff(args, callInfo) } }
internal val StrudelPattern._cutoff by dslPatternExtension { p, args, _ -> applyLpf(p, args) }
internal val String._cutoff by dslStringExtension { p, args, callInfo -> p._cutoff(args, callInfo) }
internal val PatternMapperFn._cutoff by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_cutoff(args, callInfo))
}

internal val _ctf by dslPatternMapper { args, callInfo -> { p -> p._ctf(args, callInfo) } }
internal val StrudelPattern._ctf by dslPatternExtension { p, args, _ -> applyLpf(p, args) }
internal val String._ctf by dslStringExtension { p, args, callInfo -> p._ctf(args, callInfo) }
internal val PatternMapperFn._ctf by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_ctf(args, callInfo))
}

internal val _lp by dslPatternMapper { args, callInfo -> { p -> p._lp(args, callInfo) } }
internal val StrudelPattern._lp by dslPatternExtension { p, args, _ -> applyLpf(p, args) }
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
 * @param-tool freq StrudelLpFilterSequenceEditor
 * @alias cutoff, ctf, lp
 * @category effects
 * @tags lpf, cutoff, low pass filter, filter, frequency
 */
@StrudelDsl
fun StrudelPattern.lpf(freq: PatternLike? = null): StrudelPattern =
    this._lpf(listOfNotNull(freq).asStrudelDslArgs())

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
@StrudelDsl
fun String.lpf(freq: PatternLike? = null): StrudelPattern =
    this._lpf(listOfNotNull(freq).asStrudelDslArgs())

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
@StrudelDsl
fun lpf(freq: PatternLike? = null): PatternMapperFn = _lpf(listOfNotNull(freq).asStrudelDslArgs())

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
@StrudelDsl
fun PatternMapperFn.lpf(freq: PatternLike? = null): PatternMapperFn =
    _lpf(listOfNotNull(freq).asStrudelDslArgs())

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
 * @param-tool freq StrudelLpFilterSequenceEditor
 * @alias lpf, ctf, lp
 * @category effects
 * @tags cutoff, lpf, low pass filter, filter, frequency
 */
@StrudelDsl
fun StrudelPattern.cutoff(freq: PatternLike? = null): StrudelPattern =
    this._cutoff(listOfNotNull(freq).asStrudelDslArgs())

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
@StrudelDsl
fun String.cutoff(freq: PatternLike? = null): StrudelPattern =
    this._cutoff(listOfNotNull(freq).asStrudelDslArgs())

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
@StrudelDsl
fun cutoff(freq: PatternLike? = null): PatternMapperFn = _cutoff(listOfNotNull(freq).asStrudelDslArgs())

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
@StrudelDsl
fun PatternMapperFn.cutoff(freq: PatternLike? = null): PatternMapperFn =
    _cutoff(listOfNotNull(freq).asStrudelDslArgs())

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
 * @param-tool freq StrudelLpFilterSequenceEditor
 * @alias lpf, cutoff, lp
 * @category effects
 * @tags ctf, lpf, low pass filter, filter, frequency
 */
@StrudelDsl
fun StrudelPattern.ctf(freq: PatternLike? = null): StrudelPattern =
    this._ctf(listOfNotNull(freq).asStrudelDslArgs())

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
@StrudelDsl
fun String.ctf(freq: PatternLike? = null): StrudelPattern =
    this._ctf(listOfNotNull(freq).asStrudelDslArgs())

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
@StrudelDsl
fun ctf(freq: PatternLike? = null): PatternMapperFn = _ctf(listOfNotNull(freq).asStrudelDslArgs())

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
@StrudelDsl
fun PatternMapperFn.ctf(freq: PatternLike? = null): PatternMapperFn =
    _ctf(listOfNotNull(freq).asStrudelDslArgs())

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
 * @param-tool freq StrudelLpFilterSequenceEditor
 * @alias lpf, cutoff, ctf
 * @category effects
 * @tags lp, lpf, low pass filter, filter, frequency
 */
@StrudelDsl
fun StrudelPattern.lp(freq: PatternLike? = null): StrudelPattern =
    this._lp(listOfNotNull(freq).asStrudelDslArgs())

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
@StrudelDsl
fun String.lp(freq: PatternLike? = null): StrudelPattern =
    this._lp(listOfNotNull(freq).asStrudelDslArgs())

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
@StrudelDsl
fun lp(freq: PatternLike? = null): PatternMapperFn = _lp(listOfNotNull(freq).asStrudelDslArgs())

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
@StrudelDsl
fun PatternMapperFn.lp(freq: PatternLike? = null): PatternMapperFn =
    _lp(listOfNotNull(freq).asStrudelDslArgs())

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

fun applyHpf(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
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
internal val StrudelPattern._hpf by dslPatternExtension { p, args, _ -> applyHpf(p, args) }
internal val String._hpf by dslStringExtension { p, args, callInfo -> p._hpf(args, callInfo) }
internal val PatternMapperFn._hpf by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_hpf(args, callInfo))
}

internal val _hp by dslPatternMapper { args, callInfo -> { p -> p._hp(args, callInfo) } }
internal val StrudelPattern._hp by dslPatternExtension { p, args, _ -> applyHpf(p, args) }
internal val String._hp by dslStringExtension { p, args, callInfo -> p._hp(args, callInfo) }
internal val PatternMapperFn._hp by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_hp(args, callInfo))
}

internal val _hcutoff by dslPatternMapper { args, callInfo -> { p -> p._hcutoff(args, callInfo) } }
internal val StrudelPattern._hcutoff by dslPatternExtension { p, args, _ -> applyHpf(p, args) }
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
 * @param-tool freq StrudelHpFilterSequenceEditor
 * @alias hp, hcutoff
 * @category effects
 * @tags hpf, hcutoff, high pass filter, filter, frequency
 */
@StrudelDsl
fun StrudelPattern.hpf(freq: PatternLike? = null): StrudelPattern =
    this._hpf(listOfNotNull(freq).asStrudelDslArgs())

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
@StrudelDsl
fun String.hpf(freq: PatternLike? = null): StrudelPattern =
    this._hpf(listOfNotNull(freq).asStrudelDslArgs())

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
@StrudelDsl
fun hpf(freq: PatternLike? = null): PatternMapperFn = _hpf(listOfNotNull(freq).asStrudelDslArgs())

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
@StrudelDsl
fun PatternMapperFn.hpf(freq: PatternLike? = null): PatternMapperFn =
    _hpf(listOfNotNull(freq).asStrudelDslArgs())

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
 * @param-tool freq StrudelHpFilterSequenceEditor
 * @alias hpf, hcutoff
 * @category effects
 * @tags hp, hpf, high pass filter, filter, frequency
 */
@StrudelDsl
fun StrudelPattern.hp(freq: PatternLike? = null): StrudelPattern =
    this._hp(listOfNotNull(freq).asStrudelDslArgs())

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
@StrudelDsl
fun String.hp(freq: PatternLike? = null): StrudelPattern =
    this._hp(listOfNotNull(freq).asStrudelDslArgs())

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
@StrudelDsl
fun hp(freq: PatternLike? = null): PatternMapperFn = _hp(listOfNotNull(freq).asStrudelDslArgs())

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
@StrudelDsl
fun PatternMapperFn.hp(freq: PatternLike? = null): PatternMapperFn =
    _hp(listOfNotNull(freq).asStrudelDslArgs())

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
 * @param-tool freq StrudelHpFilterSequenceEditor
 * @alias hpf, hp
 * @category effects
 * @tags hcutoff, hpf, high pass filter, filter, frequency
 */
@StrudelDsl
fun StrudelPattern.hcutoff(freq: PatternLike? = null): StrudelPattern =
    this._hcutoff(listOfNotNull(freq).asStrudelDslArgs())

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
@StrudelDsl
fun String.hcutoff(freq: PatternLike? = null): StrudelPattern =
    this._hcutoff(listOfNotNull(freq).asStrudelDslArgs())

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
@StrudelDsl
fun hcutoff(freq: PatternLike? = null): PatternMapperFn = _hcutoff(listOfNotNull(freq).asStrudelDslArgs())

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
@StrudelDsl
fun PatternMapperFn.hcutoff(freq: PatternLike? = null): PatternMapperFn =
    _hcutoff(listOfNotNull(freq).asStrudelDslArgs())

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

fun applyBandf(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
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
internal val StrudelPattern._bandf by dslPatternExtension { p, args, _ -> applyBandf(p, args) }
internal val String._bandf by dslStringExtension { p, args, callInfo -> p._bandf(args, callInfo) }
internal val PatternMapperFn._bandf by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_bandf(args, callInfo))
}

internal val _bpf by dslPatternMapper { args, callInfo -> { p -> p._bpf(args, callInfo) } }
internal val StrudelPattern._bpf by dslPatternExtension { p, args, _ -> applyBandf(p, args) }
internal val String._bpf by dslStringExtension { p, args, callInfo -> p._bpf(args, callInfo) }
internal val PatternMapperFn._bpf by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_bpf(args, callInfo))
}

internal val _bp by dslPatternMapper { args, callInfo -> { p -> p._bp(args, callInfo) } }
internal val StrudelPattern._bp by dslPatternExtension { p, args, _ -> applyBandf(p, args) }
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
 * @param-tool freq StrudelBpFilterSequenceEditor
 * @alias bpf, bp
 * @category effects
 * @tags bandf, bpf, band pass filter, filter, frequency
 */
@StrudelDsl
fun StrudelPattern.bandf(freq: PatternLike? = null): StrudelPattern =
    this._bandf(listOfNotNull(freq).asStrudelDslArgs())

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
@StrudelDsl
fun String.bandf(freq: PatternLike? = null): StrudelPattern =
    this._bandf(listOfNotNull(freq).asStrudelDslArgs())

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
@StrudelDsl
fun bandf(freq: PatternLike? = null): PatternMapperFn = _bandf(listOfNotNull(freq).asStrudelDslArgs())

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
@StrudelDsl
fun PatternMapperFn.bandf(freq: PatternLike? = null): PatternMapperFn =
    _bandf(listOfNotNull(freq).asStrudelDslArgs())

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
 * @param-tool freq StrudelBpFilterSequenceEditor
 * @alias bandf, bp
 * @category effects
 * @tags bpf, bandf, band pass filter, filter, frequency
 */
@StrudelDsl
fun StrudelPattern.bpf(freq: PatternLike? = null): StrudelPattern =
    this._bpf(listOfNotNull(freq).asStrudelDslArgs())

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
@StrudelDsl
fun String.bpf(freq: PatternLike? = null): StrudelPattern =
    this._bpf(listOfNotNull(freq).asStrudelDslArgs())

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
@StrudelDsl
fun bpf(freq: PatternLike? = null): PatternMapperFn = _bpf(listOfNotNull(freq).asStrudelDslArgs())

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
@StrudelDsl
fun PatternMapperFn.bpf(freq: PatternLike? = null): PatternMapperFn =
    _bpf(listOfNotNull(freq).asStrudelDslArgs())

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
 * @param-tool freq StrudelBpFilterSequenceEditor
 * @alias bandf, bpf
 * @category effects
 * @tags bp, bandf, band pass filter, filter, frequency
 */
@StrudelDsl
fun StrudelPattern.bp(freq: PatternLike? = null): StrudelPattern =
    this._bp(listOfNotNull(freq).asStrudelDslArgs())

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
@StrudelDsl
fun String.bp(freq: PatternLike? = null): StrudelPattern =
    this._bp(listOfNotNull(freq).asStrudelDslArgs())

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
@StrudelDsl
fun bp(freq: PatternLike? = null): PatternMapperFn = _bp(listOfNotNull(freq).asStrudelDslArgs())

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
@StrudelDsl
fun PatternMapperFn.bp(freq: PatternLike? = null): PatternMapperFn =
    _bp(listOfNotNull(freq).asStrudelDslArgs())

// -- resonance() / res() / lpq() - Low Pass Filter resonance ---------------------------------------------------------

private val resonanceMutation = voiceModifier { copy(resonance = it?.asDoubleOrNull()) }

fun applyResonance(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftOrReinterpretNumericalField(args, resonanceMutation)
}

internal val _resonance by dslPatternMapper { args, callInfo -> { p -> p._resonance(args, callInfo) } }
internal val StrudelPattern._resonance by dslPatternExtension { p, args, _ -> applyResonance(p, args) }
internal val String._resonance by dslStringExtension { p, args, callInfo -> p._resonance(args, callInfo) }
internal val PatternMapperFn._resonance by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_resonance(args, callInfo))
}

internal val _res by dslPatternMapper { args, callInfo -> { p -> p._res(args, callInfo) } }
internal val StrudelPattern._res by dslPatternExtension { p, args, _ -> applyResonance(p, args) }
internal val String._res by dslStringExtension { p, args, callInfo -> p._res(args, callInfo) }
internal val PatternMapperFn._res by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_res(args, callInfo))
}

internal val _lpq by dslPatternMapper { args, callInfo -> { p -> p._lpq(args, callInfo) } }
internal val StrudelPattern._lpq by dslPatternExtension { p, args, _ -> applyResonance(p, args) }
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
 * @param-tool q StrudelLpResonanceSequenceEditor
 * @alias res, lpq
 * @category effects
 * @tags resonance, res, lpq, low pass filter, Q
 */
@StrudelDsl
fun StrudelPattern.resonance(q: PatternLike? = null): StrudelPattern =
    this._resonance(listOfNotNull(q).asStrudelDslArgs())

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
@StrudelDsl
fun String.resonance(q: PatternLike? = null): StrudelPattern =
    this._resonance(listOfNotNull(q).asStrudelDslArgs())

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
@StrudelDsl
fun resonance(q: PatternLike? = null): PatternMapperFn = _resonance(listOfNotNull(q).asStrudelDslArgs())

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
@StrudelDsl
fun PatternMapperFn.resonance(q: PatternLike? = null): PatternMapperFn =
    _resonance(listOfNotNull(q).asStrudelDslArgs())

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
 * @param-tool q StrudelLpResonanceSequenceEditor
 * @alias resonance, lpq
 * @category effects
 * @tags res, resonance, lpq, low pass filter, Q
 */
@StrudelDsl
fun StrudelPattern.res(q: PatternLike? = null): StrudelPattern =
    this._res(listOfNotNull(q).asStrudelDslArgs())

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
@StrudelDsl
fun String.res(q: PatternLike? = null): StrudelPattern =
    this._res(listOfNotNull(q).asStrudelDslArgs())

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
@StrudelDsl
fun res(q: PatternLike? = null): PatternMapperFn = _res(listOfNotNull(q).asStrudelDslArgs())

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
@StrudelDsl
fun PatternMapperFn.res(q: PatternLike? = null): PatternMapperFn =
    _res(listOfNotNull(q).asStrudelDslArgs())

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
 * @param-tool q StrudelLpResonanceSequenceEditor
 * @alias resonance, res
 * @category effects
 * @tags lpq, resonance, res, low pass filter, Q
 */
@StrudelDsl
fun StrudelPattern.lpq(q: PatternLike? = null): StrudelPattern =
    this._lpq(listOfNotNull(q).asStrudelDslArgs())

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
@StrudelDsl
fun String.lpq(q: PatternLike? = null): StrudelPattern =
    this._lpq(listOfNotNull(q).asStrudelDslArgs())

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
@StrudelDsl
fun lpq(q: PatternLike? = null): PatternMapperFn = _lpq(listOfNotNull(q).asStrudelDslArgs())

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
@StrudelDsl
fun PatternMapperFn.lpq(q: PatternLike? = null): PatternMapperFn =
    _lpq(listOfNotNull(q).asStrudelDslArgs())

// -- hresonance() / hres() / hpq() - High Pass Filter resonance ------------------------------------------------------

private val hresonanceMutation = voiceModifier { copy(hresonance = it?.asDoubleOrNull()) }

fun applyHresonance(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftOrReinterpretNumericalField(args, hresonanceMutation)
}

internal val _hresonance by dslPatternMapper { args, callInfo -> { p -> p._hresonance(args, callInfo) } }
internal val StrudelPattern._hresonance by dslPatternExtension { p, args, _ -> applyHresonance(p, args) }
internal val String._hresonance by dslStringExtension { p, args, callInfo -> p._hresonance(args, callInfo) }
internal val PatternMapperFn._hresonance by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_hresonance(args, callInfo))
}

internal val _hres by dslPatternMapper { args, callInfo -> { p -> p._hres(args, callInfo) } }
internal val StrudelPattern._hres by dslPatternExtension { p, args, _ -> applyHresonance(p, args) }
internal val String._hres by dslStringExtension { p, args, callInfo -> p._hres(args, callInfo) }
internal val PatternMapperFn._hres by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_hres(args, callInfo))
}

internal val _hpq by dslPatternMapper { args, callInfo -> { p -> p._hpq(args, callInfo) } }
internal val StrudelPattern._hpq by dslPatternExtension { p, args, _ -> applyHresonance(p, args) }
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
 * @param-tool q StrudelHpResonanceSequenceEditor
 * @alias hres, hpq
 * @category effects
 * @tags hresonance, hres, hpq, high pass filter, Q, resonance
 */
@StrudelDsl
fun StrudelPattern.hresonance(q: PatternLike? = null): StrudelPattern =
    this._hresonance(listOfNotNull(q).asStrudelDslArgs())

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
@StrudelDsl
fun String.hresonance(q: PatternLike? = null): StrudelPattern =
    this._hresonance(listOfNotNull(q).asStrudelDslArgs())

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
@StrudelDsl
fun hresonance(q: PatternLike? = null): PatternMapperFn = _hresonance(listOfNotNull(q).asStrudelDslArgs())

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
@StrudelDsl
fun PatternMapperFn.hresonance(q: PatternLike? = null): PatternMapperFn =
    _hresonance(listOfNotNull(q).asStrudelDslArgs())

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
 * @param-tool q StrudelHpResonanceSequenceEditor
 * @alias hresonance, hpq
 * @category effects
 * @tags hres, hresonance, hpq, high pass filter, Q
 */
@StrudelDsl
fun StrudelPattern.hres(q: PatternLike? = null): StrudelPattern =
    this._hres(listOfNotNull(q).asStrudelDslArgs())

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
@StrudelDsl
fun String.hres(q: PatternLike? = null): StrudelPattern =
    this._hres(listOfNotNull(q).asStrudelDslArgs())

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
@StrudelDsl
fun hres(q: PatternLike? = null): PatternMapperFn = _hres(listOfNotNull(q).asStrudelDslArgs())

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
@StrudelDsl
fun PatternMapperFn.hres(q: PatternLike? = null): PatternMapperFn =
    _hres(listOfNotNull(q).asStrudelDslArgs())

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
 * @param-tool q StrudelHpResonanceSequenceEditor
 * @alias hresonance, hres
 * @category effects
 * @tags hpq, hresonance, hres, high pass filter, Q
 */
@StrudelDsl
fun StrudelPattern.hpq(q: PatternLike? = null): StrudelPattern =
    this._hpq(listOfNotNull(q).asStrudelDslArgs())

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
@StrudelDsl
fun String.hpq(q: PatternLike? = null): StrudelPattern =
    this._hpq(listOfNotNull(q).asStrudelDslArgs())

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
@StrudelDsl
fun hpq(q: PatternLike? = null): PatternMapperFn = _hpq(listOfNotNull(q).asStrudelDslArgs())

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
@StrudelDsl
fun PatternMapperFn.hpq(q: PatternLike? = null): PatternMapperFn =
    _hpq(listOfNotNull(q).asStrudelDslArgs())

// -- bandq() / bpq() - Band Pass Filter resonance --------------------------------------------------------------------

private val bandqMutation = voiceModifier { copy(bandq = it?.asDoubleOrNull()) }

fun applyBandq(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftOrReinterpretNumericalField(args, bandqMutation)
}

internal val _bandq by dslPatternMapper { args, callInfo -> { p -> p._bandq(args, callInfo) } }
internal val StrudelPattern._bandq by dslPatternExtension { p, args, _ -> applyBandq(p, args) }
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
 * @param-tool q StrudelBpQSequenceEditor
 * @alias bpq
 * @category effects
 * @tags bandq, bpq, band pass filter, Q, bandwidth
 */
@StrudelDsl
fun StrudelPattern.bandq(q: PatternLike? = null): StrudelPattern =
    this._bandq(listOfNotNull(q).asStrudelDslArgs())

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
@StrudelDsl
fun String.bandq(q: PatternLike? = null): StrudelPattern =
    this._bandq(listOfNotNull(q).asStrudelDslArgs())

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
@StrudelDsl
fun bandq(q: PatternLike? = null): PatternMapperFn = _bandq(listOfNotNull(q).asStrudelDslArgs())

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
@StrudelDsl
fun PatternMapperFn.bandq(q: PatternLike? = null): PatternMapperFn =
    _bandq(listOfNotNull(q).asStrudelDslArgs())

internal val _bpq by dslPatternMapper { args, callInfo -> { p -> p._bpq(args, callInfo) } }
internal val StrudelPattern._bpq by dslPatternExtension { p, args, _ -> applyBandq(p, args) }
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
 * @param-tool q StrudelBpQSequenceEditor
 * @alias bandq
 * @category effects
 * @tags bpq, bandq, band pass filter, Q, bandwidth
 */
@StrudelDsl
fun StrudelPattern.bpq(q: PatternLike? = null): StrudelPattern =
    this._bpq(listOfNotNull(q).asStrudelDslArgs())

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
@StrudelDsl
fun String.bpq(q: PatternLike? = null): StrudelPattern =
    this._bpq(listOfNotNull(q).asStrudelDslArgs())

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
@StrudelDsl
fun bpq(q: PatternLike? = null): PatternMapperFn = _bpq(listOfNotNull(q).asStrudelDslArgs())

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
@StrudelDsl
fun PatternMapperFn.bpq(q: PatternLike? = null): PatternMapperFn =
    _bpq(listOfNotNull(q).asStrudelDslArgs())

// -- lpattack() / lpa() - Low Pass Filter Envelope Attack ---------------------------------------------------------------

private val lpattackMutation = voiceModifier { copy(lpattack = it?.asDoubleOrNull()) }

fun applyLpattack(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftOrReinterpretNumericalField(args, lpattackMutation)
}

internal val _lpattack by dslPatternMapper { args, callInfo -> { p -> p._lpattack(args, callInfo) } }
internal val StrudelPattern._lpattack by dslPatternExtension { p, args, _ -> applyLpattack(p, args) }
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
 * @param-tool seconds StrudelLpAttackSequenceEditor
 * @alias lpa
 * @category effects
 * @tags lpattack, lpa, low pass filter, envelope, attack
 */
@StrudelDsl
fun StrudelPattern.lpattack(seconds: PatternLike? = null): StrudelPattern =
    this._lpattack(listOfNotNull(seconds).asStrudelDslArgs())

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
@StrudelDsl
fun String.lpattack(seconds: PatternLike? = null): StrudelPattern =
    this._lpattack(listOfNotNull(seconds).asStrudelDslArgs())

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
@StrudelDsl
fun lpattack(seconds: PatternLike? = null): PatternMapperFn = _lpattack(listOfNotNull(seconds).asStrudelDslArgs())

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
@StrudelDsl
fun PatternMapperFn.lpattack(seconds: PatternLike? = null): PatternMapperFn =
    _lpattack(listOfNotNull(seconds).asStrudelDslArgs())

internal val _lpa by dslPatternMapper { args, callInfo -> { p -> p._lpa(args, callInfo) } }
internal val StrudelPattern._lpa by dslPatternExtension { p, args, _ -> applyLpattack(p, args) }
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
 * @param-tool seconds StrudelLpAttackSequenceEditor
 * @alias lpattack
 * @category effects
 * @tags lpa, lpattack, low pass filter, envelope, attack
 */
@StrudelDsl
fun StrudelPattern.lpa(seconds: PatternLike? = null): StrudelPattern =
    this._lpa(listOfNotNull(seconds).asStrudelDslArgs())

/** Alias for [lpattack] on a string pattern. */
@StrudelDsl
fun String.lpa(seconds: PatternLike? = null): StrudelPattern =
    this._lpa(listOfNotNull(seconds).asStrudelDslArgs())

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
@StrudelDsl
fun lpa(seconds: PatternLike? = null): PatternMapperFn = _lpa(listOfNotNull(seconds).asStrudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets LPF attack (alias for [lpattack]) after the previous mapper. */
@StrudelDsl
fun PatternMapperFn.lpa(seconds: PatternLike? = null): PatternMapperFn =
    _lpa(listOfNotNull(seconds).asStrudelDslArgs())

// -- lpdecay() / lpd() - Low Pass Filter Envelope Decay ----------------------------------------------------------------

private val lpdecayMutation = voiceModifier { copy(lpdecay = it?.asDoubleOrNull()) }

fun applyLpdecay(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftOrReinterpretNumericalField(args, lpdecayMutation)
}

internal val _lpdecay by dslPatternMapper { args, callInfo -> { p -> p._lpdecay(args, callInfo) } }
internal val StrudelPattern._lpdecay by dslPatternExtension { p, args, _ -> applyLpdecay(p, args) }
internal val String._lpdecay by dslStringExtension { p, args, callInfo -> p._lpdecay(args, callInfo) }
internal val PatternMapperFn._lpdecay by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_lpdecay(args, callInfo))
}

internal val _lpd by dslPatternMapper { args, callInfo -> { p -> p._lpd(args, callInfo) } }
internal val StrudelPattern._lpd by dslPatternExtension { p, args, _ -> applyLpdecay(p, args) }
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
 * @param-tool seconds StrudelLpDecaySequenceEditor
 * @alias lpd
 * @category effects
 * @tags lpdecay, lpd, low pass filter, envelope, decay
 */
@StrudelDsl
fun StrudelPattern.lpdecay(seconds: PatternLike? = null): StrudelPattern =
    this._lpdecay(listOfNotNull(seconds).asStrudelDslArgs())

/** Parses this string as a pattern, then sets LPF envelope decay time. */
@StrudelDsl
fun String.lpdecay(seconds: PatternLike? = null): StrudelPattern =
    this._lpdecay(listOfNotNull(seconds).asStrudelDslArgs())

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
@StrudelDsl
fun lpdecay(seconds: PatternLike? = null): PatternMapperFn = _lpdecay(listOfNotNull(seconds).asStrudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets LPF decay after the previous mapper. */
@StrudelDsl
fun PatternMapperFn.lpdecay(seconds: PatternLike? = null): PatternMapperFn =
    _lpdecay(listOfNotNull(seconds).asStrudelDslArgs())

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
 * @param-tool seconds StrudelLpDecaySequenceEditor
 * @alias lpdecay
 * @category effects
 * @tags lpd, lpdecay, low pass filter, envelope, decay
 */
@StrudelDsl
fun StrudelPattern.lpd(seconds: PatternLike? = null): StrudelPattern =
    this._lpd(listOfNotNull(seconds).asStrudelDslArgs())

/** Alias for [lpdecay] on a string pattern. */
@StrudelDsl
fun String.lpd(seconds: PatternLike? = null): StrudelPattern =
    this._lpd(listOfNotNull(seconds).asStrudelDslArgs())

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
@StrudelDsl
fun lpd(seconds: PatternLike? = null): PatternMapperFn = _lpd(listOfNotNull(seconds).asStrudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets LPF decay (alias for [lpdecay]) after the previous mapper. */
@StrudelDsl
fun PatternMapperFn.lpd(seconds: PatternLike? = null): PatternMapperFn =
    _lpd(listOfNotNull(seconds).asStrudelDslArgs())

// -- lpsustain() / lps() - Low Pass Filter Envelope Sustain ------------------------------------------------------------

private val lpsustainMutation = voiceModifier { copy(lpsustain = it?.asDoubleOrNull()) }

fun applyLpsustain(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftOrReinterpretNumericalField(args, lpsustainMutation)
}

internal val _lpsustain by dslPatternMapper { args, callInfo -> { p -> p._lpsustain(args, callInfo) } }
internal val StrudelPattern._lpsustain by dslPatternExtension { p, args, _ -> applyLpsustain(p, args) }
internal val String._lpsustain by dslStringExtension { p, args, callInfo -> p._lpsustain(args, callInfo) }
internal val PatternMapperFn._lpsustain by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_lpsustain(args, callInfo))
}

internal val _lps by dslPatternMapper { args, callInfo -> { p -> p._lps(args, callInfo) } }
internal val StrudelPattern._lps by dslPatternExtension { p, args, _ -> applyLpsustain(p, args) }
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
 * @return A [PatternMapperFn] that sets the LPF sustain level, or [StrudelPattern] when called on a pattern.
 * @param-tool level StrudelLpSustainSequenceEditor
 * @alias lps
 * @category effects
 * @tags lpsustain, lps, low pass filter, envelope, sustain
 */
@StrudelDsl
fun StrudelPattern.lpsustain(level: PatternLike? = null): StrudelPattern =
    this._lpsustain(listOfNotNull(level).asStrudelDslArgs())

/** Sets the LPF envelope sustain level on a string pattern. */
@StrudelDsl
fun String.lpsustain(level: PatternLike? = null): StrudelPattern =
    this._lpsustain(listOfNotNull(level).asStrudelDslArgs())

/** Creates a [PatternMapperFn] that sets the LPF envelope sustain level. */
@StrudelDsl
fun lpsustain(level: PatternLike? = null): PatternMapperFn = _lpsustain(listOfNotNull(level).asStrudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets the LPF envelope sustain level after the previous mapper. */
@StrudelDsl
fun PatternMapperFn.lpsustain(level: PatternLike? = null): PatternMapperFn =
    _lpsustain(listOfNotNull(level).asStrudelDslArgs())

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
 * @return A [PatternMapperFn] that sets the LPF sustain level, or [StrudelPattern] when called on a pattern.
 * @param-tool level StrudelLpSustainSequenceEditor
 * @alias lpsustain
 * @category effects
 * @tags lps, lpsustain, low pass filter, envelope, sustain
 */
@StrudelDsl
fun StrudelPattern.lps(level: PatternLike? = null): StrudelPattern =
    this._lps(listOfNotNull(level).asStrudelDslArgs())

/** Alias for [lpsustain] on a string pattern. */
@StrudelDsl
fun String.lps(level: PatternLike? = null): StrudelPattern =
    this._lps(listOfNotNull(level).asStrudelDslArgs())

/** Creates a [PatternMapperFn] that sets the LPF envelope sustain level (alias for [lpsustain]). */
@StrudelDsl
fun lps(level: PatternLike? = null): PatternMapperFn = _lps(listOfNotNull(level).asStrudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets LPF sustain (alias for [lpsustain]) after the previous mapper. */
@StrudelDsl
fun PatternMapperFn.lps(level: PatternLike? = null): PatternMapperFn =
    _lps(listOfNotNull(level).asStrudelDslArgs())

// -- lprelease() / lpr() - Low Pass Filter Envelope Release ------------------------------------------------------------

private val lpreleaseMutation = voiceModifier { copy(lprelease = it?.asDoubleOrNull()) }

fun applyLprelease(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftOrReinterpretNumericalField(args, lpreleaseMutation)
}

internal val _lprelease by dslPatternMapper { args, callInfo -> { p -> p._lprelease(args, callInfo) } }
internal val StrudelPattern._lprelease by dslPatternExtension { p, args, _ -> applyLprelease(p, args) }
internal val String._lprelease by dslStringExtension { p, args, callInfo -> p._lprelease(args, callInfo) }
internal val PatternMapperFn._lprelease by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_lprelease(args, callInfo))
}

internal val _lpr by dslPatternMapper { args, callInfo -> { p -> p._lpr(args, callInfo) } }
internal val StrudelPattern._lpr by dslPatternExtension { p, args, _ -> applyLprelease(p, args) }
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
 * @return A [PatternMapperFn] that sets the LPF release time, or [StrudelPattern] when called on a pattern.
 * @param-tool seconds StrudelLpReleaseSequenceEditor
 * @alias lpr
 * @category effects
 * @tags lprelease, lpr, low pass filter, envelope, release
 */
@StrudelDsl
fun StrudelPattern.lprelease(seconds: PatternLike? = null): StrudelPattern =
    this._lprelease(listOfNotNull(seconds).asStrudelDslArgs())

/** Sets the LPF envelope release time on a string pattern. */
@StrudelDsl
fun String.lprelease(seconds: PatternLike? = null): StrudelPattern =
    this._lprelease(listOfNotNull(seconds).asStrudelDslArgs())

/** Creates a [PatternMapperFn] that sets the LPF envelope release time. */
@StrudelDsl
fun lprelease(seconds: PatternLike? = null): PatternMapperFn = _lprelease(listOfNotNull(seconds).asStrudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets the LPF envelope release time after the previous mapper. */
@StrudelDsl
fun PatternMapperFn.lprelease(seconds: PatternLike? = null): PatternMapperFn =
    _lprelease(listOfNotNull(seconds).asStrudelDslArgs())

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
 * @return A [PatternMapperFn] that sets the LPF release time, or [StrudelPattern] when called on a pattern.
 * @param-tool seconds StrudelLpReleaseSequenceEditor
 * @alias lprelease
 * @category effects
 * @tags lpr, lprelease, low pass filter, envelope, release
 */
@StrudelDsl
fun StrudelPattern.lpr(seconds: PatternLike? = null): StrudelPattern =
    this._lpr(listOfNotNull(seconds).asStrudelDslArgs())

/** Alias for [lprelease] on a string pattern. */
@StrudelDsl
fun String.lpr(seconds: PatternLike? = null): StrudelPattern =
    this._lpr(listOfNotNull(seconds).asStrudelDslArgs())

/** Creates a [PatternMapperFn] that sets the LPF envelope release time (alias for [lprelease]). */
@StrudelDsl
fun lpr(seconds: PatternLike? = null): PatternMapperFn = _lpr(listOfNotNull(seconds).asStrudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets LPF release (alias for [lprelease]) after the previous mapper. */
@StrudelDsl
fun PatternMapperFn.lpr(seconds: PatternLike? = null): PatternMapperFn =
    _lpr(listOfNotNull(seconds).asStrudelDslArgs())

// -- lpenv() / lpe() - Low Pass Filter Envelope Depth ------------------------------------------------------------------

private val lpenvMutation = voiceModifier { copy(lpenv = it?.asDoubleOrNull()) }

fun applyLpenv(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftOrReinterpretNumericalField(args, lpenvMutation)
}

internal val _lpenv by dslPatternMapper { args, callInfo -> { p -> p._lpenv(args, callInfo) } }
internal val StrudelPattern._lpenv by dslPatternExtension { p, args, _ -> applyLpenv(p, args) }
internal val String._lpenv by dslStringExtension { p, args, callInfo -> p._lpenv(args, callInfo) }
internal val PatternMapperFn._lpenv by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_lpenv(args, callInfo))
}

internal val _lpe by dslPatternMapper { args, callInfo -> { p -> p._lpe(args, callInfo) } }
internal val StrudelPattern._lpe by dslPatternExtension { p, args, _ -> applyLpenv(p, args) }
internal val String._lpe by dslStringExtension { p, args, callInfo -> p._lpe(args, callInfo) }
internal val PatternMapperFn._lpe by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_lpe(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the LPF envelope depth (modulation amount) in Hz.
 *
 * Determines how far above the base [lpf] cutoff the filter sweeps when the envelope is
 * fully open. A larger value creates a more dramatic filter sweep. Use with [lpattack],
 * [lpdecay], [lpsustain], [lprelease].
 *
 * ```KlangScript
 * s("bd").lpf(200).lpenv(4000)                // sweeps up to 4.2 kHz at peak
 * ```
 *
 * ```KlangScript
 * note("c4").lpf(300).lpenv("<1000 8000>")    // subtle vs dramatic sweep per cycle
 * ```
 *
 * @param depth Envelope depth in Hz; omit to reinterpret the pattern's own values.
 * @return A [PatternMapperFn] that sets the LPF envelope depth, or [StrudelPattern] when called on a pattern.
 * @param-tool depth StrudelLpEnvSequenceEditor
 * @alias lpe
 * @category effects
 * @tags lpenv, lpe, low pass filter, envelope, depth, modulation
 */
@StrudelDsl
fun StrudelPattern.lpenv(depth: PatternLike? = null): StrudelPattern =
    this._lpenv(listOfNotNull(depth).asStrudelDslArgs())

/** Sets the LPF envelope depth/amount on a string pattern. */
@StrudelDsl
fun String.lpenv(depth: PatternLike? = null): StrudelPattern =
    this._lpenv(listOfNotNull(depth).asStrudelDslArgs())

/** Creates a [PatternMapperFn] that sets the LPF envelope depth. */
@StrudelDsl
fun lpenv(depth: PatternLike? = null): PatternMapperFn = _lpenv(listOfNotNull(depth).asStrudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets the LPF envelope depth after the previous mapper. */
@StrudelDsl
fun PatternMapperFn.lpenv(depth: PatternLike? = null): PatternMapperFn =
    _lpenv(listOfNotNull(depth).asStrudelDslArgs())

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
 * @return A [PatternMapperFn] that sets the LPF envelope depth, or [StrudelPattern] when called on a pattern.
 * @param-tool depth StrudelLpEnvSequenceEditor
 * @alias lpenv
 * @category effects
 * @tags lpe, lpenv, low pass filter, envelope, depth, modulation
 */
@StrudelDsl
fun StrudelPattern.lpe(depth: PatternLike? = null): StrudelPattern =
    this._lpe(listOfNotNull(depth).asStrudelDslArgs())

/** Alias for [lpenv] on a string pattern. */
@StrudelDsl
fun String.lpe(depth: PatternLike? = null): StrudelPattern =
    this._lpe(listOfNotNull(depth).asStrudelDslArgs())

/** Creates a [PatternMapperFn] that sets the LPF envelope depth (alias for [lpenv]). */
@StrudelDsl
fun lpe(depth: PatternLike? = null): PatternMapperFn = _lpe(listOfNotNull(depth).asStrudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets LPF envelope depth (alias for [lpenv]) after the previous mapper. */
@StrudelDsl
fun PatternMapperFn.lpe(depth: PatternLike? = null): PatternMapperFn =
    _lpe(listOfNotNull(depth).asStrudelDslArgs())

// -- hpattack() / hpa() - High Pass Filter Envelope Attack -------------------------------------------------------------

private val hpattackMutation = voiceModifier { copy(hpattack = it?.asDoubleOrNull()) }

fun applyHpattack(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftOrReinterpretNumericalField(args, hpattackMutation)
}

internal val _hpattack by dslPatternMapper { args, callInfo -> { p -> p._hpattack(args, callInfo) } }
internal val StrudelPattern._hpattack by dslPatternExtension { p, args, _ -> applyHpattack(p, args) }
internal val String._hpattack by dslStringExtension { p, args, callInfo -> p._hpattack(args, callInfo) }
internal val PatternMapperFn._hpattack by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_hpattack(args, callInfo))
}

internal val _hpa by dslPatternMapper { args, callInfo -> { p -> p._hpa(args, callInfo) } }
internal val StrudelPattern._hpa by dslPatternExtension { p, args, _ -> applyHpattack(p, args) }
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
 * @return A [PatternMapperFn] that sets the HPF attack time, or [StrudelPattern] when called on a pattern.
 * @param-tool seconds StrudelHpAttackSequenceEditor
 * @alias hpa
 * @category effects
 * @tags hpattack, hpa, high pass filter, envelope, attack
 */
@StrudelDsl
fun StrudelPattern.hpattack(seconds: PatternLike? = null): StrudelPattern =
    this._hpattack(listOfNotNull(seconds).asStrudelDslArgs())

/** Sets the HPF envelope attack time on a string pattern. */
@StrudelDsl
fun String.hpattack(seconds: PatternLike? = null): StrudelPattern =
    this._hpattack(listOfNotNull(seconds).asStrudelDslArgs())

/** Creates a [PatternMapperFn] that sets the HPF envelope attack time. */
@StrudelDsl
fun hpattack(seconds: PatternLike? = null): PatternMapperFn = _hpattack(listOfNotNull(seconds).asStrudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets the HPF envelope attack time after the previous mapper. */
@StrudelDsl
fun PatternMapperFn.hpattack(seconds: PatternLike? = null): PatternMapperFn =
    _hpattack(listOfNotNull(seconds).asStrudelDslArgs())

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
 * @return A [PatternMapperFn] that sets the HPF attack time, or [StrudelPattern] when called on a pattern.
 * @param-tool seconds StrudelHpAttackSequenceEditor
 * @alias hpattack
 * @category effects
 * @tags hpa, hpattack, high pass filter, envelope, attack
 */
@StrudelDsl
fun StrudelPattern.hpa(seconds: PatternLike? = null): StrudelPattern =
    this._hpa(listOfNotNull(seconds).asStrudelDslArgs())

/** Alias for [hpattack] on a string pattern. */
@StrudelDsl
fun String.hpa(seconds: PatternLike? = null): StrudelPattern =
    this._hpa(listOfNotNull(seconds).asStrudelDslArgs())

/** Creates a [PatternMapperFn] that sets the HPF envelope attack time (alias for [hpattack]). */
@StrudelDsl
fun hpa(seconds: PatternLike? = null): PatternMapperFn = _hpa(listOfNotNull(seconds).asStrudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets HPF attack (alias for [hpattack]) after the previous mapper. */
@StrudelDsl
fun PatternMapperFn.hpa(seconds: PatternLike? = null): PatternMapperFn =
    _hpa(listOfNotNull(seconds).asStrudelDslArgs())

// -- hpdecay() / hpd() - High Pass Filter Envelope Decay ---------------------------------------------------------------

private val hpdecayMutation = voiceModifier { copy(hpdecay = it?.asDoubleOrNull()) }

fun applyHpdecay(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftOrReinterpretNumericalField(args, hpdecayMutation)
}

internal val _hpdecay by dslPatternMapper { args, callInfo -> { p -> p._hpdecay(args, callInfo) } }
internal val StrudelPattern._hpdecay by dslPatternExtension { p, args, _ -> applyHpdecay(p, args) }
internal val String._hpdecay by dslStringExtension { p, args, callInfo -> p._hpdecay(args, callInfo) }
internal val PatternMapperFn._hpdecay by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_hpdecay(args, callInfo))
}

internal val _hpd by dslPatternMapper { args, callInfo -> { p -> p._hpd(args, callInfo) } }
internal val StrudelPattern._hpd by dslPatternExtension { p, args, _ -> applyHpdecay(p, args) }
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
 * @return A [PatternMapperFn] that sets the HPF decay time, or [StrudelPattern] when called on a pattern.
 * @param-tool seconds StrudelHpDecaySequenceEditor
 * @alias hpd
 * @category effects
 * @tags hpdecay, hpd, high pass filter, envelope, decay
 */
@StrudelDsl
fun StrudelPattern.hpdecay(seconds: PatternLike? = null): StrudelPattern =
    this._hpdecay(listOfNotNull(seconds).asStrudelDslArgs())

/** Sets the HPF envelope decay time on a string pattern. */
@StrudelDsl
fun String.hpdecay(seconds: PatternLike? = null): StrudelPattern =
    this._hpdecay(listOfNotNull(seconds).asStrudelDslArgs())

/** Creates a [PatternMapperFn] that sets the HPF envelope decay time. */
@StrudelDsl
fun hpdecay(seconds: PatternLike? = null): PatternMapperFn = _hpdecay(listOfNotNull(seconds).asStrudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets the HPF envelope decay time after the previous mapper. */
@StrudelDsl
fun PatternMapperFn.hpdecay(seconds: PatternLike? = null): PatternMapperFn =
    _hpdecay(listOfNotNull(seconds).asStrudelDslArgs())

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
 * @return A [PatternMapperFn] that sets the HPF decay time, or [StrudelPattern] when called on a pattern.
 * @param-tool seconds StrudelHpDecaySequenceEditor
 * @alias hpdecay
 * @category effects
 * @tags hpd, hpdecay, high pass filter, envelope, decay
 */
@StrudelDsl
fun StrudelPattern.hpd(seconds: PatternLike? = null): StrudelPattern =
    this._hpd(listOfNotNull(seconds).asStrudelDslArgs())

/** Alias for [hpdecay] on a string pattern. */
@StrudelDsl
fun String.hpd(seconds: PatternLike? = null): StrudelPattern =
    this._hpd(listOfNotNull(seconds).asStrudelDslArgs())

/** Creates a [PatternMapperFn] that sets the HPF envelope decay time (alias for [hpdecay]). */
@StrudelDsl
fun hpd(seconds: PatternLike? = null): PatternMapperFn = _hpd(listOfNotNull(seconds).asStrudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets HPF decay (alias for [hpdecay]) after the previous mapper. */
@StrudelDsl
fun PatternMapperFn.hpd(seconds: PatternLike? = null): PatternMapperFn =
    _hpd(listOfNotNull(seconds).asStrudelDslArgs())

// -- hpsustain() / hps() - High Pass Filter Envelope Sustain -----------------------------------------------------------

private val hpsustainMutation = voiceModifier { copy(hpsustain = it?.asDoubleOrNull()) }

fun applyHpsustain(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftOrReinterpretNumericalField(args, hpsustainMutation)
}

internal val _hpsustain by dslPatternMapper { args, callInfo -> { p -> p._hpsustain(args, callInfo) } }
internal val StrudelPattern._hpsustain by dslPatternExtension { p, args, _ -> applyHpsustain(p, args) }
internal val String._hpsustain by dslStringExtension { p, args, callInfo -> p._hpsustain(args, callInfo) }
internal val PatternMapperFn._hpsustain by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_hpsustain(args, callInfo))
}

internal val _hps by dslPatternMapper { args, callInfo -> { p -> p._hps(args, callInfo) } }
internal val StrudelPattern._hps by dslPatternExtension { p, args, _ -> applyHpsustain(p, args) }
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
 * @return A [PatternMapperFn] that sets the HPF sustain level, or [StrudelPattern] when called on a pattern.
 * @param-tool level StrudelHpSustainSequenceEditor
 * @alias hps
 * @category effects
 * @tags hpsustain, hps, high pass filter, envelope, sustain
 */
@StrudelDsl
fun StrudelPattern.hpsustain(level: PatternLike? = null): StrudelPattern =
    this._hpsustain(listOfNotNull(level).asStrudelDslArgs())

/** Sets the HPF envelope sustain level on a string pattern. */
@StrudelDsl
fun String.hpsustain(level: PatternLike? = null): StrudelPattern =
    this._hpsustain(listOfNotNull(level).asStrudelDslArgs())

/** Creates a [PatternMapperFn] that sets the HPF envelope sustain level. */
@StrudelDsl
fun hpsustain(level: PatternLike? = null): PatternMapperFn = _hpsustain(listOfNotNull(level).asStrudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets the HPF envelope sustain level after the previous mapper. */
@StrudelDsl
fun PatternMapperFn.hpsustain(level: PatternLike? = null): PatternMapperFn =
    _hpsustain(listOfNotNull(level).asStrudelDslArgs())

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
 * @return A [PatternMapperFn] that sets the HPF sustain level, or [StrudelPattern] when called on a pattern.
 * @param-tool level StrudelHpSustainSequenceEditor
 * @alias hpsustain
 * @category effects
 * @tags hps, hpsustain, high pass filter, envelope, sustain
 */
@StrudelDsl
fun StrudelPattern.hps(level: PatternLike? = null): StrudelPattern =
    this._hps(listOfNotNull(level).asStrudelDslArgs())

/** Alias for [hpsustain] on a string pattern. */
@StrudelDsl
fun String.hps(level: PatternLike? = null): StrudelPattern =
    this._hps(listOfNotNull(level).asStrudelDslArgs())

/** Creates a [PatternMapperFn] that sets the HPF envelope sustain level (alias for [hpsustain]). */
@StrudelDsl
fun hps(level: PatternLike? = null): PatternMapperFn = _hps(listOfNotNull(level).asStrudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets HPF sustain (alias for [hpsustain]) after the previous mapper. */
@StrudelDsl
fun PatternMapperFn.hps(level: PatternLike? = null): PatternMapperFn =
    _hps(listOfNotNull(level).asStrudelDslArgs())

// -- hprelease() / hpr() - High Pass Filter Envelope Release -----------------------------------------------------------

private val hpreleaseMutation = voiceModifier { copy(hprelease = it?.asDoubleOrNull()) }

fun applyHprelease(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftOrReinterpretNumericalField(args, hpreleaseMutation)
}

internal val _hprelease by dslPatternMapper { args, callInfo -> { p -> p._hprelease(args, callInfo) } }
internal val StrudelPattern._hprelease by dslPatternExtension { p, args, _ -> applyHprelease(p, args) }
internal val String._hprelease by dslStringExtension { p, args, callInfo -> p._hprelease(args, callInfo) }
internal val PatternMapperFn._hprelease by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_hprelease(args, callInfo))
}

internal val _hpr by dslPatternMapper { args, callInfo -> { p -> p._hpr(args, callInfo) } }
internal val StrudelPattern._hpr by dslPatternExtension { p, args, _ -> applyHprelease(p, args) }
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
 * @return A [PatternMapperFn] that sets the HPF release time, or [StrudelPattern] when called on a pattern.
 * @param-tool seconds StrudelHpReleaseSequenceEditor
 * @alias hpr
 * @category effects
 * @tags hprelease, hpr, high pass filter, envelope, release
 */
@StrudelDsl
fun StrudelPattern.hprelease(seconds: PatternLike? = null): StrudelPattern =
    this._hprelease(listOfNotNull(seconds).asStrudelDslArgs())

/** Sets the HPF envelope release time on a string pattern. */
@StrudelDsl
fun String.hprelease(seconds: PatternLike? = null): StrudelPattern =
    this._hprelease(listOfNotNull(seconds).asStrudelDslArgs())

/** Creates a [PatternMapperFn] that sets the HPF envelope release time. */
@StrudelDsl
fun hprelease(seconds: PatternLike? = null): PatternMapperFn = _hprelease(listOfNotNull(seconds).asStrudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets the HPF envelope release time after the previous mapper. */
@StrudelDsl
fun PatternMapperFn.hprelease(seconds: PatternLike? = null): PatternMapperFn =
    _hprelease(listOfNotNull(seconds).asStrudelDslArgs())

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
 * @return A [PatternMapperFn] that sets the HPF release time, or [StrudelPattern] when called on a pattern.
 * @param-tool seconds StrudelHpReleaseSequenceEditor
 * @alias hprelease
 * @category effects
 * @tags hpr, hprelease, high pass filter, envelope, release
 */
@StrudelDsl
fun StrudelPattern.hpr(seconds: PatternLike? = null): StrudelPattern =
    this._hpr(listOfNotNull(seconds).asStrudelDslArgs())

/** Alias for [hprelease] on a string pattern. */
@StrudelDsl
fun String.hpr(seconds: PatternLike? = null): StrudelPattern =
    this._hpr(listOfNotNull(seconds).asStrudelDslArgs())

/** Creates a [PatternMapperFn] that sets the HPF envelope release time (alias for [hprelease]). */
@StrudelDsl
fun hpr(seconds: PatternLike? = null): PatternMapperFn = _hpr(listOfNotNull(seconds).asStrudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets HPF release (alias for [hprelease]) after the previous mapper. */
@StrudelDsl
fun PatternMapperFn.hpr(seconds: PatternLike? = null): PatternMapperFn =
    _hpr(listOfNotNull(seconds).asStrudelDslArgs())

// -- hpenv() / hpe() - High Pass Filter Envelope Depth -----------------------------------------------------------------

private val hpenvMutation = voiceModifier { copy(hpenv = it?.asDoubleOrNull()) }

fun applyHpenv(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftOrReinterpretNumericalField(args, hpenvMutation)
}

internal val _hpenv by dslPatternMapper { args, callInfo -> { p -> p._hpenv(args, callInfo) } }
internal val StrudelPattern._hpenv by dslPatternExtension { p, args, _ -> applyHpenv(p, args) }
internal val String._hpenv by dslStringExtension { p, args, callInfo -> p._hpenv(args, callInfo) }
internal val PatternMapperFn._hpenv by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_hpenv(args, callInfo))
}

internal val _hpe by dslPatternMapper { args, callInfo -> { p -> p._hpe(args, callInfo) } }
internal val StrudelPattern._hpe by dslPatternExtension { p, args, _ -> applyHpenv(p, args) }
internal val String._hpe by dslStringExtension { p, args, callInfo -> p._hpe(args, callInfo) }
internal val PatternMapperFn._hpe by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_hpe(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the HPF envelope depth (modulation amount) in Hz.
 *
 * Determines how far above the base [hpf] cutoff the filter sweeps when the envelope is
 * fully open. A larger value creates a more dramatic filter sweep. Use with [hpattack],
 * [hpdecay], [hpsustain], [hprelease].
 *
 * ```KlangScript
 * s("sd").hpf(100).hpenv(3000)                // sweeps up to 3.1 kHz at peak
 * ```
 *
 * ```KlangScript
 * note("c4").hpf(200).hpenv("<500 5000>")     // subtle vs dramatic sweep per cycle
 * ```
 *
 * @param depth Envelope depth in Hz; omit to reinterpret the pattern's own values.
 * @return A [PatternMapperFn] that sets the HPF envelope depth, or [StrudelPattern] when called on a pattern.
 * @param-tool depth StrudelHpEnvSequenceEditor
 * @alias hpe
 * @category effects
 * @tags hpenv, hpe, high pass filter, envelope, depth, modulation
 */
@StrudelDsl
fun StrudelPattern.hpenv(depth: PatternLike? = null): StrudelPattern =
    this._hpenv(listOfNotNull(depth).asStrudelDslArgs())

/** Sets the HPF envelope depth/amount on a string pattern. */
@StrudelDsl
fun String.hpenv(depth: PatternLike? = null): StrudelPattern =
    this._hpenv(listOfNotNull(depth).asStrudelDslArgs())

/** Creates a [PatternMapperFn] that sets the HPF envelope depth. */
@StrudelDsl
fun hpenv(depth: PatternLike? = null): PatternMapperFn = _hpenv(listOfNotNull(depth).asStrudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets the HPF envelope depth after the previous mapper. */
@StrudelDsl
fun PatternMapperFn.hpenv(depth: PatternLike? = null): PatternMapperFn =
    _hpenv(listOfNotNull(depth).asStrudelDslArgs())

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
 * @return A [PatternMapperFn] that sets the HPF envelope depth, or [StrudelPattern] when called on a pattern.
 * @param-tool depth StrudelHpEnvSequenceEditor
 * @alias hpenv
 * @category effects
 * @tags hpe, hpenv, high pass filter, envelope, depth, modulation
 */
@StrudelDsl
fun StrudelPattern.hpe(depth: PatternLike? = null): StrudelPattern =
    this._hpe(listOfNotNull(depth).asStrudelDslArgs())

/** Alias for [hpenv] on a string pattern. */
@StrudelDsl
fun String.hpe(depth: PatternLike? = null): StrudelPattern =
    this._hpe(listOfNotNull(depth).asStrudelDslArgs())

/** Creates a [PatternMapperFn] that sets the HPF envelope depth (alias for [hpenv]). */
@StrudelDsl
fun hpe(depth: PatternLike? = null): PatternMapperFn = _hpe(listOfNotNull(depth).asStrudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets HPF envelope depth (alias for [hpenv]) after the previous mapper. */
@StrudelDsl
fun PatternMapperFn.hpe(depth: PatternLike? = null): PatternMapperFn =
    _hpe(listOfNotNull(depth).asStrudelDslArgs())

// -- bpattack() / bpa() - Band Pass Filter Envelope Attack -------------------------------------------------------------

private val bpattackMutation = voiceModifier { copy(bpattack = it?.asDoubleOrNull()) }

fun applyBpattack(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftOrReinterpretNumericalField(args, bpattackMutation)
}

internal val _bpattack by dslPatternMapper { args, callInfo -> { p -> p._bpattack(args, callInfo) } }
internal val StrudelPattern._bpattack by dslPatternExtension { p, args, _ -> applyBpattack(p, args) }
internal val String._bpattack by dslStringExtension { p, args, callInfo -> p._bpattack(args, callInfo) }
internal val PatternMapperFn._bpattack by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_bpattack(args, callInfo))
}

internal val _bpa by dslPatternMapper { args, callInfo -> { p -> p._bpa(args, callInfo) } }
internal val StrudelPattern._bpa by dslPatternExtension { p, args, _ -> applyBpattack(p, args) }
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
 * @return A [PatternMapperFn] that sets the BPF attack time, or [StrudelPattern] when called on a pattern.
 * @param-tool seconds StrudelBpAttackSequenceEditor
 * @alias bpa
 * @category effects
 * @tags bpattack, bpa, band pass filter, envelope, attack
 */
@StrudelDsl
fun StrudelPattern.bpattack(seconds: PatternLike? = null): StrudelPattern =
    this._bpattack(listOfNotNull(seconds).asStrudelDslArgs())

/** Sets the BPF envelope attack time on a string pattern. */
@StrudelDsl
fun String.bpattack(seconds: PatternLike? = null): StrudelPattern =
    this._bpattack(listOfNotNull(seconds).asStrudelDslArgs())

/** Creates a [PatternMapperFn] that sets the BPF envelope attack time. */
@StrudelDsl
fun bpattack(seconds: PatternLike? = null): PatternMapperFn = _bpattack(listOfNotNull(seconds).asStrudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets the BPF envelope attack time after the previous mapper. */
@StrudelDsl
fun PatternMapperFn.bpattack(seconds: PatternLike? = null): PatternMapperFn =
    _bpattack(listOfNotNull(seconds).asStrudelDslArgs())

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
 * @return A [PatternMapperFn] that sets the BPF attack time, or [StrudelPattern] when called on a pattern.
 * @param-tool seconds StrudelBpAttackSequenceEditor
 * @alias bpattack
 * @category effects
 * @tags bpa, bpattack, band pass filter, envelope, attack
 */
@StrudelDsl
fun StrudelPattern.bpa(seconds: PatternLike? = null): StrudelPattern =
    this._bpa(listOfNotNull(seconds).asStrudelDslArgs())

/** Alias for [bpattack] on a string pattern. */
@StrudelDsl
fun String.bpa(seconds: PatternLike? = null): StrudelPattern =
    this._bpa(listOfNotNull(seconds).asStrudelDslArgs())

/** Creates a [PatternMapperFn] that sets the BPF envelope attack time (alias for [bpattack]). */
@StrudelDsl
fun bpa(seconds: PatternLike? = null): PatternMapperFn = _bpa(listOfNotNull(seconds).asStrudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets BPF attack (alias for [bpattack]) after the previous mapper. */
@StrudelDsl
fun PatternMapperFn.bpa(seconds: PatternLike? = null): PatternMapperFn =
    _bpa(listOfNotNull(seconds).asStrudelDslArgs())

// -- bpdecay() / bpd() - Band Pass Filter Envelope Decay ---------------------------------------------------------------

private val bpdecayMutation = voiceModifier { copy(bpdecay = it?.asDoubleOrNull()) }

fun applyBpdecay(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftOrReinterpretNumericalField(args, bpdecayMutation)
}

internal val _bpdecay by dslPatternMapper { args, callInfo -> { p -> p._bpdecay(args, callInfo) } }
internal val StrudelPattern._bpdecay by dslPatternExtension { p, args, _ -> applyBpdecay(p, args) }
internal val String._bpdecay by dslStringExtension { p, args, callInfo -> p._bpdecay(args, callInfo) }
internal val PatternMapperFn._bpdecay by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_bpdecay(args, callInfo))
}

internal val _bpd by dslPatternMapper { args, callInfo -> { p -> p._bpd(args, callInfo) } }
internal val StrudelPattern._bpd by dslPatternExtension { p, args, _ -> applyBpdecay(p, args) }
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
 * @return A [PatternMapperFn] that sets the BPF decay time, or [StrudelPattern] when called on a pattern.
 * @param-tool seconds StrudelBpDecaySequenceEditor
 * @alias bpd
 * @category effects
 * @tags bpdecay, bpd, band pass filter, envelope, decay
 */
@StrudelDsl
fun StrudelPattern.bpdecay(seconds: PatternLike? = null): StrudelPattern =
    this._bpdecay(listOfNotNull(seconds).asStrudelDslArgs())

/** Sets the BPF envelope decay time on a string pattern. */
@StrudelDsl
fun String.bpdecay(seconds: PatternLike? = null): StrudelPattern =
    this._bpdecay(listOfNotNull(seconds).asStrudelDslArgs())

/** Creates a [PatternMapperFn] that sets the BPF envelope decay time. */
@StrudelDsl
fun bpdecay(seconds: PatternLike? = null): PatternMapperFn = _bpdecay(listOfNotNull(seconds).asStrudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets the BPF envelope decay time after the previous mapper. */
@StrudelDsl
fun PatternMapperFn.bpdecay(seconds: PatternLike? = null): PatternMapperFn =
    _bpdecay(listOfNotNull(seconds).asStrudelDslArgs())

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
 * @return A [PatternMapperFn] that sets the BPF decay time, or [StrudelPattern] when called on a pattern.
 * @param-tool seconds StrudelBpDecaySequenceEditor
 * @alias bpdecay
 * @category effects
 * @tags bpd, bpdecay, band pass filter, envelope, decay
 */
@StrudelDsl
fun StrudelPattern.bpd(seconds: PatternLike? = null): StrudelPattern =
    this._bpd(listOfNotNull(seconds).asStrudelDslArgs())

/** Alias for [bpdecay] on a string pattern. */
@StrudelDsl
fun String.bpd(seconds: PatternLike? = null): StrudelPattern =
    this._bpd(listOfNotNull(seconds).asStrudelDslArgs())

/** Creates a [PatternMapperFn] that sets the BPF envelope decay time (alias for [bpdecay]). */
@StrudelDsl
fun bpd(seconds: PatternLike? = null): PatternMapperFn = _bpd(listOfNotNull(seconds).asStrudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets BPF decay (alias for [bpdecay]) after the previous mapper. */
@StrudelDsl
fun PatternMapperFn.bpd(seconds: PatternLike? = null): PatternMapperFn =
    _bpd(listOfNotNull(seconds).asStrudelDslArgs())

// -- bpsustain() / bps() - Band Pass Filter Envelope Sustain -----------------------------------------------------------

private val bpsustainMutation = voiceModifier { copy(bpsustain = it?.asDoubleOrNull()) }

fun applyBpsustain(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftOrReinterpretNumericalField(args, bpsustainMutation)
}

internal val _bpsustain by dslPatternMapper { args, callInfo -> { p -> p._bpsustain(args, callInfo) } }
internal val StrudelPattern._bpsustain by dslPatternExtension { p, args, _ -> applyBpsustain(p, args) }
internal val String._bpsustain by dslStringExtension { p, args, callInfo -> p._bpsustain(args, callInfo) }
internal val PatternMapperFn._bpsustain by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_bpsustain(args, callInfo))
}

internal val _bps by dslPatternMapper { args, callInfo -> { p -> p._bps(args, callInfo) } }
internal val StrudelPattern._bps by dslPatternExtension { p, args, _ -> applyBpsustain(p, args) }
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
 * @return A [PatternMapperFn] that sets the BPF sustain level, or [StrudelPattern] when called on a pattern.
 * @param-tool level StrudelBpSustainSequenceEditor
 * @alias bps
 * @category effects
 * @tags bpsustain, bps, band pass filter, envelope, sustain
 */
@StrudelDsl
fun StrudelPattern.bpsustain(level: PatternLike? = null): StrudelPattern =
    this._bpsustain(listOfNotNull(level).asStrudelDslArgs())

/** Sets the BPF envelope sustain level on a string pattern. */
@StrudelDsl
fun String.bpsustain(level: PatternLike? = null): StrudelPattern =
    this._bpsustain(listOfNotNull(level).asStrudelDslArgs())

/** Creates a [PatternMapperFn] that sets the BPF envelope sustain level. */
@StrudelDsl
fun bpsustain(level: PatternLike? = null): PatternMapperFn = _bpsustain(listOfNotNull(level).asStrudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets the BPF envelope sustain level after the previous mapper. */
@StrudelDsl
fun PatternMapperFn.bpsustain(level: PatternLike? = null): PatternMapperFn =
    _bpsustain(listOfNotNull(level).asStrudelDslArgs())

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
 * @return A [PatternMapperFn] that sets the BPF sustain level, or [StrudelPattern] when called on a pattern.
 * @param-tool level StrudelBpSustainSequenceEditor
 * @alias bpsustain
 * @category effects
 * @tags bps, bpsustain, band pass filter, envelope, sustain
 */
@StrudelDsl
fun StrudelPattern.bps(level: PatternLike? = null): StrudelPattern =
    this._bps(listOfNotNull(level).asStrudelDslArgs())

/** Alias for [bpsustain] on a string pattern. */
@StrudelDsl
fun String.bps(level: PatternLike? = null): StrudelPattern =
    this._bps(listOfNotNull(level).asStrudelDslArgs())

/** Creates a [PatternMapperFn] that sets the BPF envelope sustain level (alias for [bpsustain]). */
@StrudelDsl
fun bps(level: PatternLike? = null): PatternMapperFn = _bps(listOfNotNull(level).asStrudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets BPF sustain (alias for [bpsustain]) after the previous mapper. */
@StrudelDsl
fun PatternMapperFn.bps(level: PatternLike? = null): PatternMapperFn =
    _bps(listOfNotNull(level).asStrudelDslArgs())

// -- bprelease() / bpr() - Band Pass Filter Envelope Release -----------------------------------------------------------

private val bpreleaseMutation = voiceModifier { copy(bprelease = it?.asDoubleOrNull()) }

fun applyBprelease(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftOrReinterpretNumericalField(args, bpreleaseMutation)
}

internal val _bprelease by dslPatternMapper { args, callInfo -> { p -> p._bprelease(args, callInfo) } }
internal val StrudelPattern._bprelease by dslPatternExtension { p, args, _ -> applyBprelease(p, args) }
internal val String._bprelease by dslStringExtension { p, args, callInfo -> p._bprelease(args, callInfo) }
internal val PatternMapperFn._bprelease by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_bprelease(args, callInfo))
}

internal val _bpr by dslPatternMapper { args, callInfo -> { p -> p._bpr(args, callInfo) } }
internal val StrudelPattern._bpr by dslPatternExtension { p, args, _ -> applyBprelease(p, args) }
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
 * @return A [PatternMapperFn] that sets the BPF release time, or [StrudelPattern] when called on a pattern.
 * @param-tool seconds StrudelBpReleaseSequenceEditor
 * @alias bpr
 * @category effects
 * @tags bprelease, bpr, band pass filter, envelope, release
 */
@StrudelDsl
fun StrudelPattern.bprelease(seconds: PatternLike? = null): StrudelPattern =
    this._bprelease(listOfNotNull(seconds).asStrudelDslArgs())

/** Sets the BPF envelope release time on a string pattern. */
@StrudelDsl
fun String.bprelease(seconds: PatternLike? = null): StrudelPattern =
    this._bprelease(listOfNotNull(seconds).asStrudelDslArgs())

/** Creates a [PatternMapperFn] that sets the BPF envelope release time. */
@StrudelDsl
fun bprelease(seconds: PatternLike? = null): PatternMapperFn = _bprelease(listOfNotNull(seconds).asStrudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets the BPF envelope release time after the previous mapper. */
@StrudelDsl
fun PatternMapperFn.bprelease(seconds: PatternLike? = null): PatternMapperFn =
    _bprelease(listOfNotNull(seconds).asStrudelDslArgs())

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
 * @return A [PatternMapperFn] that sets the BPF release time, or [StrudelPattern] when called on a pattern.
 * @param-tool seconds StrudelBpReleaseSequenceEditor
 * @alias bprelease
 * @category effects
 * @tags bpr, bprelease, band pass filter, envelope, release
 */
@StrudelDsl
fun StrudelPattern.bpr(seconds: PatternLike? = null): StrudelPattern =
    this._bpr(listOfNotNull(seconds).asStrudelDslArgs())

/** Alias for [bprelease] on a string pattern. */
@StrudelDsl
fun String.bpr(seconds: PatternLike? = null): StrudelPattern =
    this._bpr(listOfNotNull(seconds).asStrudelDslArgs())

/** Creates a [PatternMapperFn] that sets the BPF envelope release time (alias for [bprelease]). */
@StrudelDsl
fun bpr(seconds: PatternLike? = null): PatternMapperFn = _bpr(listOfNotNull(seconds).asStrudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets BPF release (alias for [bprelease]) after the previous mapper. */
@StrudelDsl
fun PatternMapperFn.bpr(seconds: PatternLike? = null): PatternMapperFn =
    _bpr(listOfNotNull(seconds).asStrudelDslArgs())

// -- bpenv() / bpe() - Band Pass Filter Envelope Depth -----------------------------------------------------------------

private val bpenvMutation = voiceModifier { copy(bpenv = it?.asDoubleOrNull()) }

fun applyBpenv(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftOrReinterpretNumericalField(args, bpenvMutation)
}

internal val _bpenv by dslPatternMapper { args, callInfo -> { p -> p._bpenv(args, callInfo) } }
internal val StrudelPattern._bpenv by dslPatternExtension { p, args, _ -> applyBpenv(p, args) }
internal val String._bpenv by dslStringExtension { p, args, callInfo -> p._bpenv(args, callInfo) }
internal val PatternMapperFn._bpenv by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_bpenv(args, callInfo))
}

internal val _bpe by dslPatternMapper { args, callInfo -> { p -> p._bpe(args, callInfo) } }
internal val StrudelPattern._bpe by dslPatternExtension { p, args, _ -> applyBpenv(p, args) }
internal val String._bpe by dslStringExtension { p, args, callInfo -> p._bpe(args, callInfo) }
internal val PatternMapperFn._bpe by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_bpe(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the BPF envelope depth (modulation amount) in Hz.
 *
 * Determines how far above the base [bandf] centre frequency the filter sweeps when the
 * envelope is fully open. A larger value creates a more dramatic sweep. Use with [bpattack],
 * [bpdecay], [bpsustain], [bprelease].
 *
 * ```KlangScript
 * s("sd").bandf(500).bpenv(3000)                // sweeps up to 3.5 kHz at peak
 * ```
 *
 * ```KlangScript
 * note("c4").bandf(300).bpenv("<500 5000>")     // subtle vs dramatic sweep per cycle
 * ```
 *
 * @param depth Envelope depth in Hz; omit to reinterpret the pattern's own values.
 * @return A [PatternMapperFn] that sets the BPF envelope depth, or [StrudelPattern] when called on a pattern.
 * @param-tool depth StrudelBpEnvSequenceEditor
 * @alias bpe
 * @category effects
 * @tags bpenv, bpe, band pass filter, envelope, depth, modulation
 */
@StrudelDsl
fun StrudelPattern.bpenv(depth: PatternLike? = null): StrudelPattern =
    this._bpenv(listOfNotNull(depth).asStrudelDslArgs())

/** Sets the BPF envelope depth/amount on a string pattern. */
@StrudelDsl
fun String.bpenv(depth: PatternLike? = null): StrudelPattern =
    this._bpenv(listOfNotNull(depth).asStrudelDslArgs())

/** Creates a [PatternMapperFn] that sets the BPF envelope depth. */
@StrudelDsl
fun bpenv(depth: PatternLike? = null): PatternMapperFn = _bpenv(listOfNotNull(depth).asStrudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets the BPF envelope depth after the previous mapper. */
@StrudelDsl
fun PatternMapperFn.bpenv(depth: PatternLike? = null): PatternMapperFn =
    _bpenv(listOfNotNull(depth).asStrudelDslArgs())

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
 * @return A [PatternMapperFn] that sets the BPF envelope depth, or [StrudelPattern] when called on a pattern.
 * @param-tool depth StrudelBpEnvSequenceEditor
 * @alias bpenv
 * @category effects
 * @tags bpe, bpenv, band pass filter, envelope, depth, modulation
 */
@StrudelDsl
fun StrudelPattern.bpe(depth: PatternLike? = null): StrudelPattern =
    this._bpe(listOfNotNull(depth).asStrudelDslArgs())

/** Alias for [bpenv] on a string pattern. */
@StrudelDsl
fun String.bpe(depth: PatternLike? = null): StrudelPattern =
    this._bpe(listOfNotNull(depth).asStrudelDslArgs())

/** Creates a [PatternMapperFn] that sets the BPF envelope depth (alias for [bpenv]). */
@StrudelDsl
fun bpe(depth: PatternLike? = null): PatternMapperFn = _bpe(listOfNotNull(depth).asStrudelDslArgs())

/** Creates a chained [PatternMapperFn] that sets BPF envelope depth (alias for [bpenv]) after the previous mapper. */
@StrudelDsl
fun PatternMapperFn.bpe(depth: PatternLike? = null): PatternMapperFn =
    _bpe(listOfNotNull(depth).asStrudelDslArgs())
