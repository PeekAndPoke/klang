@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
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

private fun applyLpf(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
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
 * @param-tool freq SprudelLpFilterSequenceEditor
 * @alias cutoff, ctf, lp
 * @category effects
 * @tags lpf, cutoff, low pass filter, filter, frequency
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.lpf(freq: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyLpf(this, listOfNotNull(freq).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern, then applies a Low Pass Filter.
 *
 * When [freq] is omitted, the string pattern's values are reinterpreted as cutoff frequencies.
 *
 * @param freq The cutoff frequency in Hz. Omit to reinterpret the pattern's values as cutoff.
 * @return A new pattern with LPF applied.
 *
 * ```KlangScript(Playable)
 * "c4 e4".lpf(500).note()           // LPF on string pattern
 * ```
 *
 * @alias cutoff, ctf, lp
 * @category effects
 * @tags lpf, cutoff, low pass filter, filter, frequency
 */
@SprudelDsl
@KlangScript.Function
fun String.lpf(freq: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).lpf(freq, callInfo)

/**
 * Returns a [PatternMapperFn] that applies a Low Pass Filter.
 *
 * Use the returned mapper as a transform argument or apply it to a pattern via `.apply(...)`.
 * When [freq] is omitted, the pattern's own numeric values are reinterpreted as cutoff frequencies.
 *
 * @param freq The cutoff frequency in Hz. Omit to reinterpret the pattern's values as cutoff.
 * @return A [PatternMapperFn] that applies LPF.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").apply(lpf(500))                     // apply LPF via mapper
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4*4").firstOf(4, lpf(200).resonance(20))   // resonant LPF on first cycle
 * ```
 *
 * @alias cutoff, ctf, lp
 * @category effects
 * @tags lpf, cutoff, low pass filter, filter, frequency
 */
@SprudelDsl
@KlangScript.Function
fun lpf(freq: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.lpf(freq, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that applies a Low Pass Filter after the previous mapper.
 *
 * @param freq The cutoff frequency in Hz. Omit to reinterpret the pattern's values as cutoff.
 * @return A new [PatternMapperFn] chaining LPF after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").apply(gain(0.8).lpf(500))           // gain then LPF
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").firstOf(4, lpf(300).resonance(15))   // resonant LPF chain
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.lpf(freq: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.lpf(freq, callInfo) }

/**
 * Alias for [lpf]. Applies a Low Pass Filter with the given cutoff frequency.
 *
 * When [freq] is omitted, the pattern's own numeric values are reinterpreted as cutoff frequencies.
 *
 * @param freq The cutoff frequency in Hz. Omit to reinterpret the pattern's values as cutoff.
 * @return A new pattern with LPF applied.
 *
 * ```KlangScript(Playable)
 * note("c4").cutoff(800)            // alias for lpf
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4").cutoff("<200 2000>")   // alternating cutoff per cycle
 * ```
 *
 * @param-tool freq SprudelLpFilterSequenceEditor
 * @alias lpf, ctf, lp
 * @category effects
 * @tags cutoff, lpf, low pass filter, filter, frequency
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.cutoff(freq: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.lpf(freq, callInfo)

/**
 * Alias for [lpf] on a string pattern.
 *
 * @param freq The cutoff frequency in Hz.
 * @return A new pattern with LPF applied.
 *
 * ```KlangScript(Playable)
 * "c4 e4".cutoff(500).note()        // alias for String.lpf
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun String.cutoff(freq: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).cutoff(freq, callInfo)

/**
 * Alias for [lpf]. Returns a [PatternMapperFn] that applies a Low Pass Filter.
 *
 * @param freq The cutoff frequency in Hz. Omit to reinterpret the pattern's values as cutoff.
 * @return A [PatternMapperFn] that applies LPF.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").apply(cutoff(500))      // alias for lpf()
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").firstOf(4, cutoff(200))  // LPF on first cycle
 * ```
 *
 * @alias lpf, ctf, lp
 * @category effects
 * @tags cutoff, lpf, low pass filter, filter, frequency
 */
@SprudelDsl
@KlangScript.Function
fun cutoff(freq: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    lpf(freq, callInfo)

/**
 * Creates a chained [PatternMapperFn] that applies LPF (alias for [lpf]) after the previous mapper.
 *
 * @param freq The cutoff frequency in Hz. Omit to reinterpret the pattern's values as cutoff.
 * @return A new [PatternMapperFn] chaining LPF after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c4").apply(gain(0.8).cutoff(500))           // gain then cutoff
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").firstOf(4, cutoff(300).resonance(10))  // chain cutoff + resonance
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.cutoff(freq: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.lpf(freq, callInfo)

/**
 * Alias for [lpf]. Applies a Low Pass Filter with the given cutoff frequency.
 *
 * @param freq The cutoff frequency in Hz. Omit to reinterpret the pattern's values as cutoff.
 * @return A new pattern with LPF applied.
 *
 * ```KlangScript(Playable)
 * note("c4").ctf(600)               // short alias for lpf
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4").ctf("<100 5000>")      // sweeping cutoff
 * ```
 *
 * @param-tool freq SprudelLpFilterSequenceEditor
 * @alias lpf, cutoff, lp
 * @category effects
 * @tags ctf, lpf, low pass filter, filter, frequency
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.ctf(freq: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.lpf(freq, callInfo)

/**
 * Alias for [lpf] on a string pattern.
 *
 * @param freq The cutoff frequency in Hz.
 * @return A new pattern with LPF applied.
 *
 * ```KlangScript(Playable)
 * "c4 e4".ctf(500).note()           // short alias
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun String.ctf(freq: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).ctf(freq, callInfo)

/**
 * Alias for [lpf]. Returns a [PatternMapperFn] that applies a Low Pass Filter.
 *
 * @param freq The cutoff frequency in Hz.
 * @return A [PatternMapperFn] that applies LPF.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").apply(ctf(500))     // short alias for lpf()
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").firstOf(4, ctf(200)) // LPF on first cycle
 * ```
 *
 * @alias lpf, cutoff, lp
 * @category effects
 * @tags ctf, lpf, low pass filter, filter, frequency
 */
@SprudelDsl
@KlangScript.Function
fun ctf(freq: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    lpf(freq, callInfo)

/**
 * Creates a chained [PatternMapperFn] that applies LPF (alias for [lpf]) after the previous mapper.
 *
 * @param freq The cutoff frequency in Hz.
 * @return A new [PatternMapperFn] chaining LPF after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c4").apply(gain(0.8).ctf(500))              // gain then ctf
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").firstOf(4, ctf(300).resonance(10))   // chain
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.ctf(freq: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.lpf(freq, callInfo)

/**
 * Alias for [lpf]. Applies a Low Pass Filter with the given cutoff frequency.
 *
 * @param freq The cutoff frequency in Hz. Omit to reinterpret the pattern's values as cutoff.
 * @return A new pattern with LPF applied.
 *
 * ```KlangScript(Playable)
 * note("c4").lp(400)                // short alias for lpf
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4").lp("<200 3000>")       // sweeping cutoff
 * ```
 *
 * @param-tool freq SprudelLpFilterSequenceEditor
 * @alias lpf, cutoff, ctf
 * @category effects
 * @tags lp, lpf, low pass filter, filter, frequency
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.lp(freq: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.lpf(freq, callInfo)

/**
 * Alias for [lpf] on a string pattern.
 *
 * @param freq The cutoff frequency in Hz.
 * @return A new pattern with LPF applied.
 *
 * ```KlangScript(Playable)
 * "c4 e4".lp(500).note()            // alias for String.lpf
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun String.lp(freq: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).lp(freq, callInfo)

/**
 * Alias for [lpf]. Returns a [PatternMapperFn] that applies a Low Pass Filter.
 *
 * @param freq The cutoff frequency in Hz.
 * @return A [PatternMapperFn] that applies LPF.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").apply(lp(500))      // alias for lpf()
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").firstOf(4, lp(200))  // LPF on first cycle
 * ```
 *
 * @alias lpf, cutoff, ctf
 * @category effects
 * @tags lp, lpf, low pass filter, filter, frequency
 */
@SprudelDsl
@KlangScript.Function
fun lp(freq: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    lpf(freq, callInfo)

/**
 * Creates a chained [PatternMapperFn] that applies LPF (alias for [lpf]) after the previous mapper.
 *
 * @param freq The cutoff frequency in Hz.
 * @return A new [PatternMapperFn] chaining LPF after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c4").apply(gain(0.8).lp(500))               // gain then lp filter
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").firstOf(4, lp(300).resonance(10))    // chain
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.lp(freq: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.lpf(freq, callInfo)

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

private fun applyHpf(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
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
 * @param-tool freq SprudelHpFilterSequenceEditor
 * @alias hp, hcutoff
 * @category effects
 * @tags hpf, hcutoff, high pass filter, filter, frequency
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.hpf(freq: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyHpf(this, listOfNotNull(freq).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern, then applies a High Pass Filter.
 *
 * @param freq The cutoff frequency in Hz. Omit to reinterpret the pattern's values as cutoff.
 * @return A new pattern with HPF applied.
 *
 * ```KlangScript(Playable)
 * "c4 e4".hpf(300).note()          // HPF on string pattern
 * ```
 *
 * @alias hp, hcutoff
 * @category effects
 * @tags hpf, hcutoff, high pass filter, filter, frequency
 */
@SprudelDsl
@KlangScript.Function
fun String.hpf(freq: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).hpf(freq, callInfo)

/**
 * Returns a [PatternMapperFn] that applies a High Pass Filter.
 *
 * @param freq The cutoff frequency in Hz. Omit to reinterpret the pattern's values as cutoff.
 * @return A [PatternMapperFn] that applies HPF.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").apply(hpf(300))                     // apply HPF via mapper
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4*4").firstOf(4, hpf(200).hresonance(10))  // resonant HPF on first cycle
 * ```
 *
 * @alias hp, hcutoff
 * @category effects
 * @tags hpf, hcutoff, high pass filter, filter, frequency
 */
@SprudelDsl
@KlangScript.Function
fun hpf(freq: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.hpf(freq, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that applies a High Pass Filter after the previous mapper.
 *
 * @param freq The cutoff frequency in Hz. Omit to reinterpret the pattern's values as cutoff.
 * @return A new [PatternMapperFn] chaining HPF after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").apply(gain(0.8).hpf(300))           // gain then HPF
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").firstOf(4, hpf(200).hresonance(15))  // resonant HPF chain
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.hpf(freq: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.hpf(freq, callInfo) }

/**
 * Alias for [hpf]. Applies a High Pass Filter with the given cutoff frequency.
 *
 * @param freq The cutoff frequency in Hz. Omit to reinterpret the pattern's values as cutoff.
 * @return A new pattern with HPF applied.
 *
 * ```KlangScript(Playable)
 * note("c4").hp(300)               // alias for hpf
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4").hp("<100 800>")       // alternating HPF cutoff
 * ```
 *
 * @param-tool freq SprudelHpFilterSequenceEditor
 * @alias hpf, hcutoff
 * @category effects
 * @tags hp, hpf, high pass filter, filter, frequency
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.hp(freq: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.hpf(freq, callInfo)

/**
 * Alias for [hpf] on a string pattern.
 *
 * @param freq The cutoff frequency in Hz.
 * @return A new pattern with HPF applied.
 *
 * ```KlangScript(Playable)
 * "c4 e4".hp(300).note()           // alias for String.hpf
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun String.hp(freq: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).hp(freq, callInfo)

/**
 * Alias for [hpf]. Returns a [PatternMapperFn] that applies a High Pass Filter.
 *
 * @param freq The cutoff frequency in Hz.
 * @return A [PatternMapperFn] that applies HPF.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").apply(hp(300))      // alias for hpf()
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").firstOf(4, hp(200))  // HPF on first cycle
 * ```
 *
 * @alias hpf, hcutoff
 * @category effects
 * @tags hp, hpf, high pass filter, filter, frequency
 */
@SprudelDsl
@KlangScript.Function
fun hp(freq: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    hpf(freq, callInfo)

/**
 * Creates a chained [PatternMapperFn] that applies HPF (alias for [hpf]) after the previous mapper.
 *
 * @param freq The cutoff frequency in Hz.
 * @return A new [PatternMapperFn] chaining HPF after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c4").apply(gain(0.8).hp(300))               // gain then hp filter
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").firstOf(4, hp(200).hresonance(10))   // chain
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.hp(freq: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.hpf(freq, callInfo)

/**
 * Alias for [hpf]. Applies a High Pass Filter with the given cutoff frequency.
 *
 * @param freq The cutoff frequency in Hz. Omit to reinterpret the pattern's values as cutoff.
 * @return A new pattern with HPF applied.
 *
 * ```KlangScript(Playable)
 * note("c4").hcutoff(300)          // alias for hpf
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4").hcutoff("<100 800>")  // alternating HPF cutoff
 * ```
 *
 * @param-tool freq SrudelHpFilterSequenceEditor
 * @alias hpf, hp
 * @category effects
 * @tags hcutoff, hpf, high pass filter, filter, frequency
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.hcutoff(freq: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.hpf(freq, callInfo)

/**
 * Alias for [hpf] on a string pattern.
 *
 * @param freq The cutoff frequency in Hz.
 * @return A new pattern with HPF applied.
 *
 * ```KlangScript(Playable)
 * "c4 e4".hcutoff(300).note()      // alias for String.hpf
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun String.hcutoff(freq: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).hcutoff(freq, callInfo)

/**
 * Alias for [hpf]. Returns a [PatternMapperFn] that applies a High Pass Filter.
 *
 * @param freq The cutoff frequency in Hz.
 * @return A [PatternMapperFn] that applies HPF.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").apply(hcutoff(300))      // alias for hpf()
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").firstOf(4, hcutoff(200))  // HPF on first cycle
 * ```
 *
 * @alias hpf, hp
 * @category effects
 * @tags hcutoff, hpf, high pass filter, filter, frequency
 */
@SprudelDsl
@KlangScript.Function
fun hcutoff(freq: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    hpf(freq, callInfo)

/**
 * Creates a chained [PatternMapperFn] that applies HPF (alias for [hpf]) after the previous mapper.
 *
 * @param freq The cutoff frequency in Hz.
 * @return A new [PatternMapperFn] chaining HPF after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c4").apply(gain(0.8).hcutoff(300))           // gain then hcutoff
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").firstOf(4, hcutoff(200).hresonance(10))  // chain
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.hcutoff(freq: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.hpf(freq, callInfo)

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

private fun applyBandf(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
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
 * ```KlangScript(Playable)
 * s("sd").bandf(1000)               // emphasise mid-range around 1 kHz
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4").bandf("<500 2000>")    // alternating centre per cycle
 * ```
 *
 * ```KlangScript(Playable)
 * seq("500 1000 2000").bandf()      // reinterpret values as BPF centre
 * ```
 *
 * @param-tool freq SprudelBpFilterSequenceEditor
 * @alias bpf, bp
 * @category effects
 * @tags bandf, bpf, band pass filter, filter, frequency
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.bandf(freq: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyBandf(this, listOfNotNull(freq).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern, then applies a Band Pass Filter.
 *
 * @param freq The centre frequency in Hz. Omit to reinterpret the pattern's values as centre frequency.
 * @return A new pattern with BPF applied.
 *
 * ```KlangScript(Playable)
 * "c4 e4".bandf(1000).note()        // BPF on string pattern
 * ```
 *
 * @alias bpf, bp
 * @category effects
 * @tags bandf, bpf, band pass filter, filter, frequency
 */
@SprudelDsl
@KlangScript.Function
fun String.bandf(freq: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).bandf(freq, callInfo)

/**
 * Returns a [PatternMapperFn] that applies a Band Pass Filter.
 *
 * @param freq The centre frequency in Hz. Omit to reinterpret the pattern's values as centre frequency.
 * @return A [PatternMapperFn] that applies BPF.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").apply(bandf(1000))                    // apply BPF via mapper
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4*4").firstOf(4, bandf(800).bandq(5))        // narrow BPF on first cycle
 * ```
 *
 * @alias bpf, bp
 * @category effects
 * @tags bandf, bpf, band pass filter, filter, frequency
 */
@SprudelDsl
@KlangScript.Function
fun bandf(freq: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.bandf(freq, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that applies a Band Pass Filter after the previous mapper.
 *
 * @param freq The centre frequency in Hz. Omit to reinterpret the pattern's values as centre frequency.
 * @return A new [PatternMapperFn] chaining BPF after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").apply(gain(0.8).bandf(1000))          // gain then BPF
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").firstOf(4, bandf(800).bandq(8))        // narrow BPF chain
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.bandf(freq: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.bandf(freq, callInfo) }

/**
 * Alias for [bandf]. Applies a Band Pass Filter with the given centre frequency.
 *
 * @param freq The centre frequency in Hz. Omit to reinterpret the pattern's values as centre frequency.
 * @return A new pattern with BPF applied.
 *
 * ```KlangScript(Playable)
 * note("c4").bpf(1000)              // alias for bandf
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4").bpf("<500 2000>")      // alternating centre per cycle
 * ```
 *
 * @param-tool freq SprudelBpFilterSequenceEditor
 * @alias bandf, bp
 * @category effects
 * @tags bpf, bandf, band pass filter, filter, frequency
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.bpf(freq: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.bandf(freq, callInfo)

/**
 * Alias for [bandf] on a string pattern.
 *
 * @param freq The centre frequency in Hz.
 * @return A new pattern with BPF applied.
 *
 * ```KlangScript(Playable)
 * "c4 e4".bpf(1000).note()          // alias for String.bandf
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun String.bpf(freq: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).bpf(freq, callInfo)

/**
 * Alias for [bandf]. Returns a [PatternMapperFn] that applies a Band Pass Filter.
 *
 * @param freq The centre frequency in Hz.
 * @return A [PatternMapperFn] that applies BPF.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").apply(bpf(1000))    // alias for bandf()
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").firstOf(4, bpf(800)) // BPF on first cycle
 * ```
 *
 * @alias bandf, bp
 * @category effects
 * @tags bpf, bandf, band pass filter, filter, frequency
 */
@SprudelDsl
@KlangScript.Function
fun bpf(freq: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    bandf(freq, callInfo)

/**
 * Creates a chained [PatternMapperFn] that applies BPF (alias for [bandf]) after the previous mapper.
 *
 * @param freq The centre frequency in Hz.
 * @return A new [PatternMapperFn] chaining BPF after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c4").apply(gain(0.8).bpf(1000))              // gain then BPF
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").firstOf(4, bpf(800).bandq(5))         // chain
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.bpf(freq: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.bandf(freq, callInfo)

/**
 * Alias for [bandf]. Applies a Band Pass Filter with the given centre frequency.
 *
 * @param freq The centre frequency in Hz. Omit to reinterpret the pattern's values as centre frequency.
 * @return A new pattern with BPF applied.
 *
 * ```KlangScript(Playable)
 * note("c4").bp(1000)               // short alias for bandf
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4").bp("<500 2000>")       // alternating centre per cycle
 * ```
 *
 * @param-tool freq SprudelBpFilterSequenceEditor
 * @alias bandf, bpf
 * @category effects
 * @tags bp, bandf, band pass filter, filter, frequency
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.bp(freq: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.bandf(freq, callInfo)

/**
 * Alias for [bandf] on a string pattern.
 *
 * @param freq The centre frequency in Hz.
 * @return A new pattern with BPF applied.
 *
 * ```KlangScript(Playable)
 * "c4 e4".bp(1000).note()           // alias for String.bandf
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun String.bp(freq: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).bp(freq, callInfo)

/**
 * Alias for [bandf]. Returns a [PatternMapperFn] that applies a Band Pass Filter.
 *
 * @param freq The centre frequency in Hz.
 * @return A [PatternMapperFn] that applies BPF.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").apply(bp(1000))     // alias for bandf()
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").firstOf(4, bp(800))  // BPF on first cycle
 * ```
 *
 * @alias bandf, bpf
 * @category effects
 * @tags bp, bandf, band pass filter, filter, frequency
 */
@SprudelDsl
@KlangScript.Function
fun bp(freq: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    bandf(freq, callInfo)

/**
 * Creates a chained [PatternMapperFn] that applies BPF (alias for [bandf]) after the previous mapper.
 *
 * @param freq The centre frequency in Hz.
 * @return A new [PatternMapperFn] chaining BPF after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c4").apply(gain(0.8).bp(1000))               // gain then bp filter
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").firstOf(4, bp(800).bandq(5))          // chain
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.bp(freq: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.bandf(freq, callInfo)

// -- resonance() / res() / lpq() - Low Pass Filter resonance ---------------------------------------------------------

private val resonanceMutation = voiceModifier { copy(resonance = it?.asDoubleOrNull()) }

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
 * note("c4 e4").lpf(800).resonance(15)    // LPF with high resonance peak
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd").lpf(500).resonance("<0 20>")    // resonance sweeps from flat to peaked
 * ```
 *
 * ```KlangScript(Playable)
 * seq("0 5 10 20").resonance()            // reinterpret values as resonance Q
 * ```
 *
 * @param-tool q SprudelLpResonanceSequenceEditor
 * @alias res, lpq
 * @category effects
 * @tags resonance, res, lpq, low pass filter, Q
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.resonance(q: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyResonance(this, listOfNotNull(q).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern, then sets LPF resonance.
 *
 * @param q The Q factor. Omit to reinterpret the pattern's values as Q.
 * @return A new pattern with LPF resonance applied.
 *
 * ```KlangScript(Playable)
 * "c4 e4".lpf(800).resonance(15)    // resonance on string pattern
 * ```
 *
 * @alias res, lpq
 * @category effects
 * @tags resonance, res, lpq, low pass filter, Q
 */
@SprudelDsl
@KlangScript.Function
fun String.resonance(q: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).resonance(q, callInfo)

/**
 * Returns a [PatternMapperFn] that sets LPF resonance.
 *
 * @param q The Q factor. Omit to reinterpret the pattern's values as Q.
 * @return A [PatternMapperFn] that applies LPF resonance.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").apply(lpf(800).resonance(15))        // LPF + resonance chain
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4*4").firstOf(4, resonance(20))             // high resonance on first cycle
 * ```
 *
 * @alias res, lpq
 * @category effects
 * @tags resonance, res, lpq, low pass filter, Q
 */
@SprudelDsl
@KlangScript.Function
fun resonance(q: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.resonance(q, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets LPF resonance after the previous mapper.
 *
 * @param q The Q factor. Omit to reinterpret the pattern's values as Q.
 * @return A new [PatternMapperFn] chaining resonance after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").apply(lpf(500).resonance(15))        // LPF then resonance
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").firstOf(4, lpf(300).resonance(20))    // resonant LPF chain
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.resonance(q: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.resonance(q, callInfo) }

/**
 * Alias for [resonance]. Sets the LPF resonance/Q.
 *
 * @param q The Q factor. Omit to reinterpret the pattern's values as Q.
 * @return A new pattern with LPF resonance applied.
 *
 * ```KlangScript(Playable)
 * note("c4").lpf(500).res(10)       // alias for resonance
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4").res("<0 20>")          // sweeping resonance
 * ```
 *
 * @param-tool q SprudelLpResonanceSequenceEditor
 * @alias resonance, lpq
 * @category effects
 * @tags res, resonance, lpq, low pass filter, Q
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.res(q: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.resonance(q, callInfo)

/**
 * Alias for [resonance] on a string pattern.
 *
 * @param q The Q factor.
 * @return A new pattern with LPF resonance applied.
 *
 * ```KlangScript(Playable)
 * "c4".lpf(500).res(10)             // alias for String.resonance
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun String.res(q: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).res(q, callInfo)

/**
 * Alias for [resonance]. Returns a [PatternMapperFn] that sets LPF resonance.
 *
 * @param q The Q factor.
 * @return A [PatternMapperFn] that applies LPF resonance.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").apply(lpf(500).res(10))  // alias for resonance()
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").firstOf(4, res(20))       // high resonance on first cycle
 * ```
 *
 * @alias resonance, lpq
 * @category effects
 * @tags res, resonance, lpq, low pass filter, Q
 */
@SprudelDsl
@KlangScript.Function
fun res(q: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    resonance(q, callInfo)

/**
 * Creates a chained [PatternMapperFn] that sets LPF resonance (alias for [resonance]) after the previous mapper.
 *
 * @param q The Q factor.
 * @return A new [PatternMapperFn] chaining resonance after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").apply(lpf(500).res(10))  // LPF then res
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").firstOf(4, lpf(300).res(20))  // chain
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.res(q: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.resonance(q, callInfo)

/**
 * Alias for [resonance]. Sets the LPF resonance/Q.
 *
 * @param q The Q factor. Omit to reinterpret the pattern's values as Q.
 * @return A new pattern with LPF resonance applied.
 *
 * ```KlangScript(Playable)
 * note("c4").lpf(500).lpq(10)       // alias for resonance
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4").lpq("<0 20>")          // sweeping Q
 * ```
 *
 * @param-tool q SprudelLpResonanceSequenceEditor
 * @alias resonance, res
 * @category effects
 * @tags lpq, resonance, res, low pass filter, Q
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.lpq(q: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.resonance(q, callInfo)

/**
 * Alias for [resonance] on a string pattern.
 *
 * @param q The Q factor.
 * @return A new pattern with LPF resonance applied.
 *
 * ```KlangScript(Playable)
 * "c4".lpf(500).lpq(10)             // alias for String.resonance
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun String.lpq(q: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).lpq(q, callInfo)

/**
 * Alias for [resonance]. Returns a [PatternMapperFn] that sets LPF resonance.
 *
 * @param q The Q factor.
 * @return A [PatternMapperFn] that applies LPF resonance.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").apply(lpf(500).lpq(10))  // alias for resonance()
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").firstOf(4, lpq(20))       // high Q on first cycle
 * ```
 *
 * @alias resonance, res
 * @category effects
 * @tags lpq, resonance, res, low pass filter, Q
 */
@SprudelDsl
@KlangScript.Function
fun lpq(q: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    resonance(q, callInfo)

/**
 * Creates a chained [PatternMapperFn] that sets LPF resonance (alias for [resonance]) after the previous mapper.
 *
 * @param q The Q factor.
 * @return A new [PatternMapperFn] chaining resonance after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").apply(lpf(500).lpq(10))  // LPF then lpq
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").firstOf(4, lpf(300).lpq(20))  // chain
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.lpq(q: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.resonance(q, callInfo)

// -- hresonance() / hres() / hpq() - High Pass Filter resonance ------------------------------------------------------

private val hresonanceMutation = voiceModifier { copy(hresonance = it?.asDoubleOrNull()) }

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
 * note("c4").hpf(300).hresonance(15)        // HPF with strong resonance peak
 * ```
 *
 * ```KlangScript(Playable)
 * s("sd").hpf(200).hresonance("<0 20>")     // resonance sweeps per cycle
 * ```
 *
 * ```KlangScript(Playable)
 * seq("0 5 15").hresonance()                // reinterpret values as HPF Q
 * ```
 *
 * @param-tool q SprudelHpResonanceSequenceEditor
 * @alias hres, hpq
 * @category effects
 * @tags hresonance, hres, hpq, high pass filter, Q, resonance
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.hresonance(q: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyHresonance(this, listOfNotNull(q).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern, then sets HPF resonance.
 *
 * @param q The Q factor. Omit to reinterpret the pattern's values as Q.
 * @return A new pattern with HPF resonance applied.
 *
 * ```KlangScript(Playable)
 * "c4".hpf(300).hresonance(15)      // resonance on string pattern
 * ```
 *
 * @alias hres, hpq
 * @category effects
 * @tags hresonance, hres, hpq, high pass filter, Q, resonance
 */
@SprudelDsl
@KlangScript.Function
fun String.hresonance(q: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).hresonance(q, callInfo)

/**
 * Returns a [PatternMapperFn] that sets HPF resonance.
 *
 * @param q The Q factor. Omit to reinterpret the pattern's values as Q.
 * @return A [PatternMapperFn] that applies HPF resonance.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").apply(hpf(300).hresonance(15))       // HPF + resonance chain
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4*4").firstOf(4, hresonance(20))            // high HPF resonance on first cycle
 * ```
 *
 * @alias hres, hpq
 * @category effects
 * @tags hresonance, hres, hpq, high pass filter, Q, resonance
 */
@SprudelDsl
@KlangScript.Function
fun hresonance(q: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.hresonance(q, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets HPF resonance after the previous mapper.
 *
 * @param q The Q factor. Omit to reinterpret the pattern's values as Q.
 * @return A new [PatternMapperFn] chaining HPF resonance after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").apply(hpf(200).hresonance(15))       // HPF then resonance
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").firstOf(4, hpf(300).hresonance(20))   // resonant HPF chain
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.hresonance(q: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.hresonance(q, callInfo) }

/**
 * Alias for [hresonance]. Sets the HPF resonance/Q.
 *
 * @param q The Q factor. Omit to reinterpret the pattern's values as Q.
 * @return A new pattern with HPF resonance applied.
 *
 * ```KlangScript(Playable)
 * note("c4").hpf(300).hres(10)      // alias for hresonance
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4").hres("<0 20>")         // sweeping HPF Q
 * ```
 *
 * @param-tool q SprudelHpResonanceSequenceEditor
 * @alias hresonance, hpq
 * @category effects
 * @tags hres, hresonance, hpq, high pass filter, Q
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.hres(q: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.hresonance(q, callInfo)

/**
 * Alias for [hresonance] on a string pattern.
 *
 * @param q The Q factor.
 * @return A new pattern with HPF resonance applied.
 *
 * ```KlangScript(Playable)
 * "c4".hpf(300).hres(10)            // alias for String.hresonance
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun String.hres(q: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).hres(q, callInfo)

/**
 * Alias for [hresonance]. Returns a [PatternMapperFn] that sets HPF resonance.
 *
 * @param q The Q factor.
 * @return A [PatternMapperFn] that applies HPF resonance.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").apply(hpf(300).hres(10))  // alias for hresonance()
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").firstOf(4, hres(20))       // high Q on first cycle
 * ```
 *
 * @alias hresonance, hpq
 * @category effects
 * @tags hres, hresonance, hpq, high pass filter, Q
 */
@SprudelDsl
@KlangScript.Function
fun hres(q: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    hresonance(q, callInfo)

/**
 * Creates a chained [PatternMapperFn] that sets HPF resonance (alias for [hresonance]) after the previous mapper.
 *
 * @param q The Q factor.
 * @return A new [PatternMapperFn] chaining HPF resonance after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").apply(hpf(300).hres(10))  // HPF then hres
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").firstOf(4, hpf(200).hres(20))  // chain
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.hres(q: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.hresonance(q, callInfo)

/**
 * Alias for [hresonance]. Sets the HPF resonance/Q.
 *
 * @param q The Q factor. Omit to reinterpret the pattern's values as Q.
 * @return A new pattern with HPF resonance applied.
 *
 * ```KlangScript(Playable)
 * note("c4").hpf(300).hpq(10)       // alias for hresonance
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4").hpq("<0 20>")          // sweeping HPF Q
 * ```
 *
 * @param-tool q SprudelHpResonanceSequenceEditor
 * @alias hresonance, hres
 * @category effects
 * @tags hpq, hresonance, hres, high pass filter, Q
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.hpq(q: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.hresonance(q, callInfo)

/**
 * Alias for [hresonance] on a string pattern.
 *
 * @param q The Q factor.
 * @return A new pattern with HPF resonance applied.
 *
 * ```KlangScript(Playable)
 * "c4".hpf(300).hpq(10)             // alias for String.hresonance
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun String.hpq(q: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).hpq(q, callInfo)

/**
 * Alias for [hresonance]. Returns a [PatternMapperFn] that sets HPF resonance.
 *
 * @param q The Q factor.
 * @return A [PatternMapperFn] that applies HPF resonance.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").apply(hpf(300).hpq(10))  // alias for hresonance()
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").firstOf(4, hpq(20))       // high Q on first cycle
 * ```
 *
 * @alias hresonance, hres
 * @category effects
 * @tags hpq, hresonance, hres, high pass filter, Q
 */
@SprudelDsl
@KlangScript.Function
fun hpq(q: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    hresonance(q, callInfo)

/**
 * Creates a chained [PatternMapperFn] that sets HPF resonance (alias for [hresonance]) after the previous mapper.
 *
 * @param q The Q factor.
 * @return A new [PatternMapperFn] chaining HPF resonance after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").apply(hpf(300).hpq(10))  // HPF then hpq
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").firstOf(4, hpf(200).hpq(20))  // chain
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.hpq(q: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.hresonance(q, callInfo)

// -- bandq() / bpq() - Band Pass Filter resonance --------------------------------------------------------------------

private val bandqMutation = voiceModifier { copy(bandq = it?.asDoubleOrNull()) }

private fun applyBandq(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, bandqMutation)
}


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
 * ```KlangScript(Playable)
 * note("c4").bandf(1000).bandq(5)         // narrow band pass at 1 kHz
 * ```
 *
 * ```KlangScript(Playable)
 * s("sd").bandf(800).bandq("<1 20>")      // Q sweeps from wide to narrow
 * ```
 *
 * ```KlangScript(Playable)
 * seq("1 5 10 20").bandq()                // reinterpret values as BPF Q
 * ```
 *
 * @param-tool q SprudelBpQSequenceEditor
 * @alias bpq
 * @category effects
 * @tags bandq, bpq, band pass filter, Q, bandwidth
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.bandq(q: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyBandq(this, listOfNotNull(q).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern, then sets BPF Q.
 *
 * @param q The Q factor. Omit to reinterpret the pattern's values as Q.
 * @return A new pattern with BPF Q applied.
 *
 * ```KlangScript(Playable)
 * "c4".bandf(800).bandq(5)          // BPF Q on string pattern
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun String.bandq(q: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).bandq(q, callInfo)

/**
 * Returns a [PatternMapperFn] that sets BPF Q.
 *
 * @param q The Q factor. Omit to reinterpret the pattern's values as Q.
 * @return A [PatternMapperFn] that applies BPF Q.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").apply(bandf(1000).bandq(5))          // BPF + Q chain
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4*4").firstOf(4, bandq(10))                 // narrow BPF on first cycle
 * ```
 *
 * @alias bpq
 * @category effects
 * @tags bandq, bpq, band pass filter, Q, bandwidth
 */
@SprudelDsl
@KlangScript.Function
fun bandq(q: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.bandq(q, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets BPF Q after the previous mapper.
 *
 * @param q The Q factor. Omit to reinterpret the pattern's values as Q.
 * @return A new [PatternMapperFn] chaining BPF Q after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").apply(bandf(800).bandq(5))           // bandf then bandq
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").firstOf(4, bandf(1000).bandq(8))      // narrow BPF chain
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.bandq(q: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.bandq(q, callInfo) }

/**
 * Alias for [bandq]. Sets the BPF Q (bandwidth).
 *
 * @param q The Q factor. Omit to reinterpret the pattern's values as Q.
 * @return A new pattern with BPF Q applied.
 *
 * ```KlangScript(Playable)
 * note("c4").bandf(800).bpq(5)      // alias for bandq
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4").bpq("<1 20>")          // sweeping BPF Q
 * ```
 *
 * @param-tool q SprudelBpQSequenceEditor
 * @alias bandq
 * @category effects
 * @tags bpq, bandq, band pass filter, Q, bandwidth
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.bpq(q: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.bandq(q, callInfo)

/**
 * Alias for [bandq] on a string pattern.
 *
 * @param q The Q factor.
 * @return A new pattern with BPF Q applied.
 *
 * ```KlangScript(Playable)
 * "c4".bandf(800).bpq(5)            // alias for String.bandq
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun String.bpq(q: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).bpq(q, callInfo)

/**
 * Alias for [bandq]. Returns a [PatternMapperFn] that sets BPF Q.
 *
 * @param q The Q factor.
 * @return A [PatternMapperFn] that applies BPF Q.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").apply(bandf(800).bpq(5))  // alias for bandq()
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").firstOf(4, bpq(10))        // narrow BPF on first cycle
 * ```
 *
 * @alias bandq
 * @category effects
 * @tags bpq, bandq, band pass filter, Q, bandwidth
 */
@SprudelDsl
@KlangScript.Function
fun bpq(q: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    bandq(q, callInfo)

/**
 * Creates a chained [PatternMapperFn] that sets BPF Q (alias for [bandq]) after the previous mapper.
 *
 * @param q The Q factor.
 * @return A new [PatternMapperFn] chaining BPF Q after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").apply(bandf(800).bpq(5))  // bandf then bpq
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").firstOf(4, bandf(1000).bpq(8))  // chain
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.bpq(q: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.bandq(q, callInfo)

// -- lpattack() / lpa() - Low Pass Filter Envelope Attack ---------------------------------------------------------------

private val lpattackMutation = voiceModifier { copy(lpattack = it?.asDoubleOrNull()) }

private fun applyLpattack(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, lpattackMutation)
}

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
 * ```KlangScript(Playable)
 * s("bd").lpf(200).lpenv(5000).lpattack(0.1)   // filter opens over 100 ms
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4").lpattack("<0.01 0.5>")              // fast vs slow filter attack per cycle
 * ```
 *
 * @param-tool seconds SprudelLpAttackSequenceEditor
 * @alias lpa
 * @category effects
 * @tags lpattack, lpa, low pass filter, envelope, attack
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.lpattack(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyLpattack(this, listOfNotNull(seconds).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern, then sets LPF envelope attack time.
 *
 * @param seconds The attack time in seconds.
 * @return A new pattern with LPF attack applied.
 *
 * ```KlangScript(Playable)
 * "c4".lpattack(0.1)                 // LPF attack on string pattern
 * ```
 *
 * @alias lpa
 * @category effects
 * @tags lpattack, lpa, low pass filter, envelope, attack
 */
@SprudelDsl
@KlangScript.Function
fun String.lpattack(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).lpattack(seconds, callInfo)

/**
 * Returns a [PatternMapperFn] that sets LPF envelope attack time.
 *
 * @param seconds The attack time in seconds. Omit to reinterpret the pattern's values as attack time.
 * @return A [PatternMapperFn] that applies LPF attack.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").apply(lpf(200).lpenv(4000).lpattack(0.1))  // LPF attack chain
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4*4").firstOf(4, lpattack(0.5))   // slow attack on first cycle
 * ```
 *
 * @alias lpa
 * @category effects
 * @tags lpattack, lpa, low pass filter, envelope, attack
 */
@SprudelDsl
@KlangScript.Function
fun lpattack(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.lpattack(seconds, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets LPF envelope attack after the previous mapper.
 *
 * @param seconds The attack time in seconds.
 * @return A new [PatternMapperFn] chaining LPF attack after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").apply(lpf(200).lpattack(0.1))  // lpf then attack
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").firstOf(4, lpf(300).lpattack(0.2))  // chain
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.lpattack(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.lpattack(seconds, callInfo) }

/**
 * Alias for [lpattack]. Sets the LPF envelope attack time.
 *
 * @param seconds The attack time in seconds. Omit to reinterpret the pattern's values as attack time.
 * @return A new pattern with LPF attack applied.
 *
 * ```KlangScript(Playable)
 * s("bd").lpf(200).lpenv(5000).lpa(0.1)   // filter opens over 100 ms
 * ```
 *
 * @param-tool seconds SprudelLpAttackSequenceEditor
 * @alias lpattack
 * @category effects
 * @tags lpa, lpattack, low pass filter, envelope, attack
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.lpa(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.lpattack(seconds, callInfo)

/** Alias for [lpattack] on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.lpa(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).lpa(seconds, callInfo)

/**
 * Alias for [lpattack]. Returns a [PatternMapperFn] that sets LPF envelope attack.
 *
 * @param seconds The attack time in seconds.
 * @return A [PatternMapperFn] that applies LPF attack.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").apply(lpf(200).lpa(0.1))   // alias for lpattack()
 * ```
 *
 * @alias lpattack
 * @category effects
 * @tags lpa, lpattack, low pass filter, envelope, attack
 */
@SprudelDsl
@KlangScript.Function
fun lpa(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    lpattack(seconds, callInfo)

/** Creates a chained [PatternMapperFn] that sets LPF attack (alias for [lpattack]) after the previous mapper. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.lpa(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.lpattack(seconds, callInfo)

// -- lpdecay() / lpd() - Low Pass Filter Envelope Decay ----------------------------------------------------------------

private val lpdecayMutation = voiceModifier { copy(lpdecay = it?.asDoubleOrNull()) }

private fun applyLpdecay(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, lpdecayMutation)
}

/**
 * Sets the LPF envelope decay time in seconds.
 *
 * Controls how quickly the filter cutoff moves from peak to sustain level after the attack.
 * Use with [lpattack], [lpsustain], [lprelease], [lpenv].
 *
 * @param seconds The decay time in seconds. Omit to reinterpret the pattern's values as decay time.
 * @return A new pattern with LPF decay applied.
 *
 * ```KlangScript(Playable)
 * s("bd").lpf(200).lpenv(5000).lpdecay(0.2)   // filter decays over 200 ms
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4").lpdecay("<0.05 0.5>")              // short vs long filter decay per cycle
 * ```
 *
 * @param-tool seconds SprudelLpDecaySequenceEditor
 * @alias lpd
 * @category effects
 * @tags lpdecay, lpd, low pass filter, envelope, decay
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.lpdecay(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyLpdecay(this, listOfNotNull(seconds).asSprudelDslArgs(callInfo))

/** Parses this string as a pattern, then sets LPF envelope decay time. */
@SprudelDsl
@KlangScript.Function
fun String.lpdecay(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).lpdecay(seconds, callInfo)

/**
 * Returns a [PatternMapperFn] that sets LPF envelope decay time.
 *
 * @param seconds The decay time in seconds.
 * @return A [PatternMapperFn] that applies LPF decay.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").apply(lpf(200).lpenv(4000).lpdecay(0.2))  // LPF decay chain
 * ```
 *
 * @alias lpd
 * @category effects
 * @tags lpdecay, lpd, low pass filter, envelope, decay
 */
@SprudelDsl
@KlangScript.Function
fun lpdecay(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.lpdecay(seconds, callInfo) }

/** Creates a chained [PatternMapperFn] that sets LPF decay after the previous mapper. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.lpdecay(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.lpdecay(seconds, callInfo) }

/**
 * Alias for [lpdecay]. Sets the LPF envelope decay time.
 *
 * @param seconds The decay time in seconds.
 * @return A new pattern with LPF decay applied.
 *
 * ```KlangScript(Playable)
 * s("bd").lpf(200).lpenv(5000).lpd(0.2)   // alias for lpdecay
 * ```
 *
 * @param-tool seconds SprudelLpDecaySequenceEditor
 * @alias lpdecay
 * @category effects
 * @tags lpd, lpdecay, low pass filter, envelope, decay
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.lpd(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.lpdecay(seconds, callInfo)

/** Alias for [lpdecay] on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.lpd(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).lpd(seconds, callInfo)

/**
 * Alias for [lpdecay]. Returns a [PatternMapperFn] that sets LPF envelope decay.
 *
 * @param seconds The decay time in seconds.
 * @return A [PatternMapperFn] that applies LPF decay.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").apply(lpf(200).lpd(0.2))   // alias for lpdecay()
 * ```
 *
 * @alias lpdecay
 * @category effects
 * @tags lpd, lpdecay, low pass filter, envelope, decay
 */
@SprudelDsl
@KlangScript.Function
fun lpd(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    lpdecay(seconds, callInfo)

/** Creates a chained [PatternMapperFn] that sets LPF decay (alias for [lpdecay]) after the previous mapper. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.lpd(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.lpdecay(seconds, callInfo)

// -- lpsustain() / lps() - Low Pass Filter Envelope Sustain ------------------------------------------------------------

private val lpsustainMutation = voiceModifier { copy(lpsustain = it?.asDoubleOrNull()) }

private fun applyLpsustain(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, lpsustainMutation)
}

/**
 * Sets the LPF envelope sustain level (0–1).
 *
 * Controls the filter cutoff level during the sustained portion of the note. `1` holds
 * the filter open; `0` closes it back to baseline. Use with the lpattack/lpdecay/lprelease.
 *
 * ```KlangScript(Playable)
 * note("c4").lpf(200).lpenv(4000).lpsustain(0.5)  // sustain at half depth
 * ```
 *
 * ```KlangScript(Playable)
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
@KlangScript.Function
fun SprudelPattern.lpsustain(level: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyLpsustain(this, listOfNotNull(level).asSprudelDslArgs(callInfo))

/** Sets the LPF envelope sustain level on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.lpsustain(level: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).lpsustain(level, callInfo)

/** Creates a [PatternMapperFn] that sets the LPF envelope sustain level. */
@SprudelDsl
@KlangScript.Function
fun lpsustain(level: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.lpsustain(level, callInfo) }

/** Creates a chained [PatternMapperFn] that sets the LPF envelope sustain level after the previous mapper. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.lpsustain(level: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.lpsustain(level, callInfo) }

/**
 * Alias for [lpsustain]. Sets the LPF envelope sustain level.
 *
 * ```KlangScript(Playable)
 * note("c4").lpf(200).lps(0.5)   // alias for lpsustain()
 * ```
 *
 * ```KlangScript(Playable)
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
@KlangScript.Function
fun SprudelPattern.lps(level: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.lpsustain(level, callInfo)

/** Alias for [lpsustain] on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.lps(level: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).lps(level, callInfo)

/** Creates a [PatternMapperFn] that sets the LPF envelope sustain level (alias for [lpsustain]). */
@SprudelDsl
@KlangScript.Function
fun lps(level: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    lpsustain(level, callInfo)

/** Creates a chained [PatternMapperFn] that sets LPF sustain (alias for [lpsustain]) after the previous mapper. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.lps(level: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.lpsustain(level, callInfo)

// -- lprelease() / lpr() - Low Pass Filter Envelope Release ------------------------------------------------------------

private val lpreleaseMutation = voiceModifier { copy(lprelease = it?.asDoubleOrNull()) }

private fun applyLprelease(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, lpreleaseMutation)
}

/**
 * Sets the LPF envelope release time in seconds.
 *
 * Controls how quickly the low pass filter cutoff returns to baseline after the note ends.
 * Use with [lpattack], [lpdecay], [lpsustain], [lpenv].
 *
 * ```KlangScript(Playable)
 * s("bd").lpf(200).lpenv(5000).lprelease(0.4)   // filter closes slowly after note
 * ```
 *
 * ```KlangScript(Playable)
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
@KlangScript.Function
fun SprudelPattern.lprelease(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyLprelease(this, listOfNotNull(seconds).asSprudelDslArgs(callInfo))

/** Sets the LPF envelope release time on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.lprelease(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).lprelease(seconds, callInfo)

/** Creates a [PatternMapperFn] that sets the LPF envelope release time. */
@SprudelDsl
@KlangScript.Function
fun lprelease(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.lprelease(seconds, callInfo) }

/** Creates a chained [PatternMapperFn] that sets the LPF envelope release time after the previous mapper. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.lprelease(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.lprelease(seconds, callInfo) }

/**
 * Alias for [lprelease]. Sets the LPF envelope release time.
 *
 * ```KlangScript(Playable)
 * note("c4").lpf(200).lpr(0.4)   // alias for lprelease()
 * ```
 *
 * ```KlangScript(Playable)
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
@KlangScript.Function
fun SprudelPattern.lpr(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.lprelease(seconds, callInfo)

/** Alias for [lprelease] on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.lpr(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).lpr(seconds, callInfo)

/** Creates a [PatternMapperFn] that sets the LPF envelope release time (alias for [lprelease]). */
@SprudelDsl
@KlangScript.Function
fun lpr(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    lprelease(seconds, callInfo)

/** Creates a chained [PatternMapperFn] that sets LPF release (alias for [lprelease]) after the previous mapper. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.lpr(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.lprelease(seconds, callInfo)

// -- lpenv() / lpe() - Low Pass Filter Envelope Depth ------------------------------------------------------------------

private val lpenvMutation = voiceModifier { copy(lpenv = it?.asDoubleOrNull()) }

private fun applyLpenv(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, lpenvMutation)
}

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
 * ```KlangScript(Playable)
 * s("bd").lpf(200).lpenv(3.0)                // sweeps up to 800 Hz at peak
 * ```
 *
 * ```KlangScript(Playable)
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
@KlangScript.Function
fun SprudelPattern.lpenv(depth: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyLpenv(this, listOfNotNull(depth).asSprudelDslArgs(callInfo))

/** Sets the LPF envelope depth/amount on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.lpenv(depth: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).lpenv(depth, callInfo)

/** Creates a [PatternMapperFn] that sets the LPF envelope depth. */
@SprudelDsl
@KlangScript.Function
fun lpenv(depth: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.lpenv(depth, callInfo) }

/** Creates a chained [PatternMapperFn] that sets the LPF envelope depth after the previous mapper. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.lpenv(depth: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.lpenv(depth, callInfo) }

/**
 * Alias for [lpenv]. Sets the LPF envelope depth.
 *
 * ```KlangScript(Playable)
 * note("c4").lpf(200).lpe(4000)   // alias for lpenv()
 * ```
 *
 * ```KlangScript(Playable)
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
@KlangScript.Function
fun SprudelPattern.lpe(depth: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.lpenv(depth, callInfo)

/** Alias for [lpenv] on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.lpe(depth: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).lpe(depth, callInfo)

/** Creates a [PatternMapperFn] that sets the LPF envelope depth (alias for [lpenv]). */
@SprudelDsl
@KlangScript.Function
fun lpe(depth: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    lpenv(depth, callInfo)

/** Creates a chained [PatternMapperFn] that sets LPF envelope depth (alias for [lpenv]) after the previous mapper. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.lpe(depth: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.lpenv(depth, callInfo)

// -- hpattack() / hpa() - High Pass Filter Envelope Attack -------------------------------------------------------------

private val hpattackMutation = voiceModifier { copy(hpattack = it?.asDoubleOrNull()) }

private fun applyHpattack(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, hpattackMutation)
}

/**
 * Sets the HPF envelope attack time in seconds.
 *
 * Controls how quickly the high pass filter cutoff sweeps from its baseline to the peak
 * at note onset. Use with [hpenv], [hpdecay], [hpsustain], [hprelease].
 *
 * ```KlangScript(Playable)
 * s("sd").hpf(100).hpenv(2000).hpattack(0.1)   // filter opens over 100 ms
 * ```
 *
 * ```KlangScript(Playable)
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
@KlangScript.Function
fun SprudelPattern.hpattack(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyHpattack(this, listOfNotNull(seconds).asSprudelDslArgs(callInfo))

/** Sets the HPF envelope attack time on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.hpattack(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).hpattack(seconds, callInfo)

/** Creates a [PatternMapperFn] that sets the HPF envelope attack time. */
@SprudelDsl
@KlangScript.Function
fun hpattack(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.hpattack(seconds, callInfo) }

/** Creates a chained [PatternMapperFn] that sets the HPF envelope attack time after the previous mapper. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.hpattack(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.hpattack(seconds, callInfo) }

/**
 * Alias for [hpattack]. Sets the HPF envelope attack time.
 *
 * ```KlangScript(Playable)
 * note("c4").hpf(100).hpa(0.1)   // alias for hpattack()
 * ```
 *
 * ```KlangScript(Playable)
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
@KlangScript.Function
fun SprudelPattern.hpa(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.hpattack(seconds, callInfo)

/** Alias for [hpattack] on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.hpa(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).hpa(seconds, callInfo)

/** Creates a [PatternMapperFn] that sets the HPF envelope attack time (alias for [hpattack]). */
@SprudelDsl
@KlangScript.Function
fun hpa(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    hpattack(seconds, callInfo)

/** Creates a chained [PatternMapperFn] that sets HPF attack (alias for [hpattack]) after the previous mapper. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.hpa(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.hpattack(seconds, callInfo)

// -- hpdecay() / hpd() - High Pass Filter Envelope Decay ---------------------------------------------------------------

private val hpdecayMutation = voiceModifier { copy(hpdecay = it?.asDoubleOrNull()) }

private fun applyHpdecay(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, hpdecayMutation)
}

/**
 * Sets the HPF envelope decay time in seconds.
 *
 * Controls how quickly the filter cutoff moves from peak to sustain level after the attack.
 * Use with [hpattack], [hpsustain], [hprelease], [hpenv].
 *
 * ```KlangScript(Playable)
 * s("sd").hpf(100).hpenv(2000).hpdecay(0.2)   // filter decays over 200 ms
 * ```
 *
 * ```KlangScript(Playable)
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
@KlangScript.Function
fun SprudelPattern.hpdecay(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyHpdecay(this, listOfNotNull(seconds).asSprudelDslArgs(callInfo))

/** Sets the HPF envelope decay time on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.hpdecay(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).hpdecay(seconds, callInfo)

/** Creates a [PatternMapperFn] that sets the HPF envelope decay time. */
@SprudelDsl
@KlangScript.Function
fun hpdecay(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.hpdecay(seconds, callInfo) }

/** Creates a chained [PatternMapperFn] that sets the HPF envelope decay time after the previous mapper. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.hpdecay(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.hpdecay(seconds, callInfo) }

/**
 * Alias for [hpdecay]. Sets the HPF envelope decay time.
 *
 * ```KlangScript(Playable)
 * note("c4").hpf(100).hpd(0.2)   // alias for hpdecay()
 * ```
 *
 * ```KlangScript(Playable)
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
@KlangScript.Function
fun SprudelPattern.hpd(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.hpdecay(seconds, callInfo)

/** Alias for [hpdecay] on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.hpd(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).hpd(seconds, callInfo)

/** Creates a [PatternMapperFn] that sets the HPF envelope decay time (alias for [hpdecay]). */
@SprudelDsl
@KlangScript.Function
fun hpd(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    hpdecay(seconds, callInfo)

/** Creates a chained [PatternMapperFn] that sets HPF decay (alias for [hpdecay]) after the previous mapper. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.hpd(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.hpdecay(seconds, callInfo)

// -- hpsustain() / hps() - High Pass Filter Envelope Sustain -----------------------------------------------------------

private val hpsustainMutation = voiceModifier { copy(hpsustain = it?.asDoubleOrNull()) }

private fun applyHpsustain(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, hpsustainMutation)
}

/**
 * Sets the HPF envelope sustain level (0–1).
 *
 * Controls the filter cutoff level during the sustained portion of the note. `1` holds the
 * filter open at the envelope peak; `0` closes it back to baseline. Use with
 * [hpattack]/[hpdecay]/[hprelease].
 *
 * ```KlangScript(Playable)
 * note("c4").hpf(100).hpenv(3000).hpsustain(0.5)  // sustain at half depth
 * ```
 *
 * ```KlangScript(Playable)
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
@KlangScript.Function
fun SprudelPattern.hpsustain(level: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyHpsustain(this, listOfNotNull(level).asSprudelDslArgs(callInfo))

/** Sets the HPF envelope sustain level on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.hpsustain(level: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).hpsustain(level, callInfo)

/** Creates a [PatternMapperFn] that sets the HPF envelope sustain level. */
@SprudelDsl
@KlangScript.Function
fun hpsustain(level: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.hpsustain(level, callInfo) }

/** Creates a chained [PatternMapperFn] that sets the HPF envelope sustain level after the previous mapper. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.hpsustain(level: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.hpsustain(level, callInfo) }

/**
 * Alias for [hpsustain]. Sets the HPF envelope sustain level.
 *
 * ```KlangScript(Playable)
 * note("c4").hpf(100).hps(0.5)   // alias for hpsustain()
 * ```
 *
 * ```KlangScript(Playable)
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
@KlangScript.Function
fun SprudelPattern.hps(level: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.hpsustain(level, callInfo)

/** Alias for [hpsustain] on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.hps(level: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).hps(level, callInfo)

/** Creates a [PatternMapperFn] that sets the HPF envelope sustain level (alias for [hpsustain]). */
@SprudelDsl
@KlangScript.Function
fun hps(level: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    hpsustain(level, callInfo)

/** Creates a chained [PatternMapperFn] that sets HPF sustain (alias for [hpsustain]) after the previous mapper. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.hps(level: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.hpsustain(level, callInfo)

// -- hprelease() / hpr() - High Pass Filter Envelope Release -----------------------------------------------------------

private val hpreleaseMutation = voiceModifier { copy(hprelease = it?.asDoubleOrNull()) }

private fun applyHprelease(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, hpreleaseMutation)
}

/**
 * Sets the HPF envelope release time in seconds.
 *
 * Controls how quickly the high pass filter cutoff returns to baseline after the note ends.
 * Use with [hpattack], [hpdecay], [hpsustain], [hpenv].
 *
 * ```KlangScript(Playable)
 * s("sd").hpf(100).hpenv(2000).hprelease(0.4)   // filter closes slowly after note
 * ```
 *
 * ```KlangScript(Playable)
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
@KlangScript.Function
fun SprudelPattern.hprelease(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyHprelease(this, listOfNotNull(seconds).asSprudelDslArgs(callInfo))

/** Sets the HPF envelope release time on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.hprelease(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).hprelease(seconds, callInfo)

/** Creates a [PatternMapperFn] that sets the HPF envelope release time. */
@SprudelDsl
@KlangScript.Function
fun hprelease(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.hprelease(seconds, callInfo) }

/** Creates a chained [PatternMapperFn] that sets the HPF envelope release time after the previous mapper. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.hprelease(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.hprelease(seconds, callInfo) }

/**
 * Alias for [hprelease]. Sets the HPF envelope release time.
 *
 * ```KlangScript(Playable)
 * note("c4").hpf(100).hpr(0.4)   // alias for hprelease()
 * ```
 *
 * ```KlangScript(Playable)
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
@KlangScript.Function
fun SprudelPattern.hpr(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.hprelease(seconds, callInfo)

/** Alias for [hprelease] on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.hpr(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).hpr(seconds, callInfo)

/** Creates a [PatternMapperFn] that sets the HPF envelope release time (alias for [hprelease]). */
@SprudelDsl
@KlangScript.Function
fun hpr(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    hprelease(seconds, callInfo)

/** Creates a chained [PatternMapperFn] that sets HPF release (alias for [hprelease]) after the previous mapper. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.hpr(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.hprelease(seconds, callInfo)

// -- hpenv() / hpe() - High Pass Filter Envelope Depth -----------------------------------------------------------------

private val hpenvMutation = voiceModifier { copy(hpenv = it?.asDoubleOrNull()) }

private fun applyHpenv(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, hpenvMutation)
}

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
 * ```KlangScript(Playable)
 * s("sd").hpf(100).hpenv(3.0)                // sweeps up to 400 Hz at peak
 * ```
 *
 * ```KlangScript(Playable)
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
@KlangScript.Function
fun SprudelPattern.hpenv(depth: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyHpenv(this, listOfNotNull(depth).asSprudelDslArgs(callInfo))

/** Sets the HPF envelope depth/amount on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.hpenv(depth: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).hpenv(depth, callInfo)

/** Creates a [PatternMapperFn] that sets the HPF envelope depth. */
@SprudelDsl
@KlangScript.Function
fun hpenv(depth: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.hpenv(depth, callInfo) }

/** Creates a chained [PatternMapperFn] that sets the HPF envelope depth after the previous mapper. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.hpenv(depth: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.hpenv(depth, callInfo) }

/**
 * Alias for [hpenv]. Sets the HPF envelope depth.
 *
 * ```KlangScript(Playable)
 * note("c4").hpf(200).hpe(3000)   // alias for hpenv()
 * ```
 *
 * ```KlangScript(Playable)
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
@KlangScript.Function
fun SprudelPattern.hpe(depth: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.hpenv(depth, callInfo)

/** Alias for [hpenv] on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.hpe(depth: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).hpe(depth, callInfo)

/** Creates a [PatternMapperFn] that sets the HPF envelope depth (alias for [hpenv]). */
@SprudelDsl
@KlangScript.Function
fun hpe(depth: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    hpenv(depth, callInfo)

/** Creates a chained [PatternMapperFn] that sets HPF envelope depth (alias for [hpenv]) after the previous mapper. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.hpe(depth: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.hpenv(depth, callInfo)

// -- bpattack() / bpa() - Band Pass Filter Envelope Attack -------------------------------------------------------------

private val bpattackMutation = voiceModifier { copy(bpattack = it?.asDoubleOrNull()) }

private fun applyBpattack(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, bpattackMutation)
}

/**
 * Sets the BPF envelope attack time in seconds.
 *
 * Controls how quickly the band pass filter centre frequency sweeps from its baseline to
 * the peak at note onset. Use with [bpenv], [bpdecay], [bpsustain], [bprelease].
 *
 * ```KlangScript(Playable)
 * s("sd").bandf(500).bpenv(2000).bpattack(0.1)   // filter opens over 100 ms
 * ```
 *
 * ```KlangScript(Playable)
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
@KlangScript.Function
fun SprudelPattern.bpattack(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyBpattack(this, listOfNotNull(seconds).asSprudelDslArgs(callInfo))

/** Sets the BPF envelope attack time on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.bpattack(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).bpattack(seconds, callInfo)

/** Creates a [PatternMapperFn] that sets the BPF envelope attack time. */
@SprudelDsl
@KlangScript.Function
fun bpattack(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.bpattack(seconds, callInfo) }

/** Creates a chained [PatternMapperFn] that sets the BPF envelope attack time after the previous mapper. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.bpattack(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.bpattack(seconds, callInfo) }

/**
 * Alias for [bpattack]. Sets the BPF envelope attack time.
 *
 * ```KlangScript(Playable)
 * note("c4").bandf(500).bpa(0.1)   // alias for bpattack()
 * ```
 *
 * ```KlangScript(Playable)
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
@KlangScript.Function
fun SprudelPattern.bpa(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.bpattack(seconds, callInfo)

/** Alias for [bpattack] on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.bpa(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).bpa(seconds, callInfo)

/** Creates a [PatternMapperFn] that sets the BPF envelope attack time (alias for [bpattack]). */
@SprudelDsl
@KlangScript.Function
fun bpa(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    bpattack(seconds, callInfo)

/** Creates a chained [PatternMapperFn] that sets BPF attack (alias for [bpattack]) after the previous mapper. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.bpa(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.bpattack(seconds, callInfo)

// -- bpdecay() / bpd() - Band Pass Filter Envelope Decay ---------------------------------------------------------------

private val bpdecayMutation = voiceModifier { copy(bpdecay = it?.asDoubleOrNull()) }

private fun applyBpdecay(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, bpdecayMutation)
}

/**
 * Sets the BPF envelope decay time in seconds.
 *
 * Controls how quickly the filter centre frequency moves from peak to sustain level after
 * the attack. Use with [bpattack], [bpsustain], [bprelease], [bpenv].
 *
 * ```KlangScript(Playable)
 * s("sd").bandf(500).bpenv(2000).bpdecay(0.2)   // filter decays over 200 ms
 * ```
 *
 * ```KlangScript(Playable)
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
@KlangScript.Function
fun SprudelPattern.bpdecay(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyBpdecay(this, listOfNotNull(seconds).asSprudelDslArgs(callInfo))

/** Sets the BPF envelope decay time on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.bpdecay(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).bpdecay(seconds, callInfo)

/** Creates a [PatternMapperFn] that sets the BPF envelope decay time. */
@SprudelDsl
@KlangScript.Function
fun bpdecay(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.bpdecay(seconds, callInfo) }

/** Creates a chained [PatternMapperFn] that sets the BPF envelope decay time after the previous mapper. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.bpdecay(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.bpdecay(seconds, callInfo) }

/**
 * Alias for [bpdecay]. Sets the BPF envelope decay time.
 *
 * ```KlangScript(Playable)
 * note("c4").bandf(500).bpd(0.2)   // alias for bpdecay()
 * ```
 *
 * ```KlangScript(Playable)
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
@KlangScript.Function
fun SprudelPattern.bpd(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.bpdecay(seconds, callInfo)

/** Alias for [bpdecay] on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.bpd(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).bpd(seconds, callInfo)

/** Creates a [PatternMapperFn] that sets the BPF envelope decay time (alias for [bpdecay]). */
@SprudelDsl
@KlangScript.Function
fun bpd(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    bpdecay(seconds, callInfo)

/** Creates a chained [PatternMapperFn] that sets BPF decay (alias for [bpdecay]) after the previous mapper. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.bpd(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.bpdecay(seconds, callInfo)

// -- bpsustain() / bps() - Band Pass Filter Envelope Sustain -----------------------------------------------------------

private val bpsustainMutation = voiceModifier { copy(bpsustain = it?.asDoubleOrNull()) }

private fun applyBpsustain(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, bpsustainMutation)
}

/**
 * Sets the BPF envelope sustain level (0–1).
 *
 * Controls the filter centre frequency level during the sustained portion of the note.
 * `1` holds the filter at the envelope peak; `0` returns to baseline. Use with
 * [bpattack]/[bpdecay]/[bprelease].
 *
 * ```KlangScript(Playable)
 * note("c4").bandf(500).bpenv(3000).bpsustain(0.5)  // sustain at half depth
 * ```
 *
 * ```KlangScript(Playable)
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
@KlangScript.Function
fun SprudelPattern.bpsustain(level: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyBpsustain(this, listOfNotNull(level).asSprudelDslArgs(callInfo))

/** Sets the BPF envelope sustain level on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.bpsustain(level: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).bpsustain(level, callInfo)

/** Creates a [PatternMapperFn] that sets the BPF envelope sustain level. */
@SprudelDsl
@KlangScript.Function
fun bpsustain(level: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.bpsustain(level, callInfo) }

/** Creates a chained [PatternMapperFn] that sets the BPF envelope sustain level after the previous mapper. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.bpsustain(level: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.bpsustain(level, callInfo) }

/**
 * Alias for [bpsustain]. Sets the BPF envelope sustain level.
 *
 * ```KlangScript(Playable)
 * note("c4").bandf(500).bps(0.5)   // alias for bpsustain()
 * ```
 *
 * ```KlangScript(Playable)
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
@KlangScript.Function
fun SprudelPattern.bps(level: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.bpsustain(level, callInfo)

/** Alias for [bpsustain] on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.bps(level: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).bps(level, callInfo)

/** Creates a [PatternMapperFn] that sets the BPF envelope sustain level (alias for [bpsustain]). */
@SprudelDsl
@KlangScript.Function
fun bps(level: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    bpsustain(level, callInfo)

/** Creates a chained [PatternMapperFn] that sets BPF sustain (alias for [bpsustain]) after the previous mapper. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.bps(level: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.bpsustain(level, callInfo)

// -- bprelease() / bpr() - Band Pass Filter Envelope Release -----------------------------------------------------------

private val bpreleaseMutation = voiceModifier { copy(bprelease = it?.asDoubleOrNull()) }

private fun applyBprelease(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, bpreleaseMutation)
}

/**
 * Sets the BPF envelope release time in seconds.
 *
 * Controls how quickly the band pass filter centre frequency returns to baseline after the
 * note ends. Use with [bpattack], [bpdecay], [bpsustain], [bpenv].
 *
 * ```KlangScript(Playable)
 * s("sd").bandf(500).bpenv(2000).bprelease(0.4)   // filter closes slowly after note
 * ```
 *
 * ```KlangScript(Playable)
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
@KlangScript.Function
fun SprudelPattern.bprelease(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyBprelease(this, listOfNotNull(seconds).asSprudelDslArgs(callInfo))

/** Sets the BPF envelope release time on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.bprelease(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).bprelease(seconds, callInfo)

/** Creates a [PatternMapperFn] that sets the BPF envelope release time. */
@SprudelDsl
@KlangScript.Function
fun bprelease(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.bprelease(seconds, callInfo) }

/** Creates a chained [PatternMapperFn] that sets the BPF envelope release time after the previous mapper. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.bprelease(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.bprelease(seconds, callInfo) }

/**
 * Alias for [bprelease]. Sets the BPF envelope release time.
 *
 * ```KlangScript(Playable)
 * note("c4").bandf(500).bpr(0.4)   // alias for bprelease()
 * ```
 *
 * ```KlangScript(Playable)
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
@KlangScript.Function
fun SprudelPattern.bpr(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.bprelease(seconds, callInfo)

/** Alias for [bprelease] on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.bpr(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).bpr(seconds, callInfo)

/** Creates a [PatternMapperFn] that sets the BPF envelope release time (alias for [bprelease]). */
@SprudelDsl
@KlangScript.Function
fun bpr(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    bprelease(seconds, callInfo)

/** Creates a chained [PatternMapperFn] that sets BPF release (alias for [bprelease]) after the previous mapper. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.bpr(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.bprelease(seconds, callInfo)

// -- bpenv() / bpe() - Band Pass Filter Envelope Depth -----------------------------------------------------------------

private val bpenvMutation = voiceModifier { copy(bpenv = it?.asDoubleOrNull()) }

private fun applyBpenv(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, bpenvMutation)
}

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
 * ```KlangScript(Playable)
 * s("sd").bandf(500).bpenv(3.0)                // sweeps up to 2000 Hz at peak
 * ```
 *
 * ```KlangScript(Playable)
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
@KlangScript.Function
fun SprudelPattern.bpenv(depth: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyBpenv(this, listOfNotNull(depth).asSprudelDslArgs(callInfo))

/** Sets the BPF envelope depth/amount on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.bpenv(depth: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).bpenv(depth, callInfo)

/** Creates a [PatternMapperFn] that sets the BPF envelope depth. */
@SprudelDsl
@KlangScript.Function
fun bpenv(depth: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.bpenv(depth, callInfo) }

/** Creates a chained [PatternMapperFn] that sets the BPF envelope depth after the previous mapper. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.bpenv(depth: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.bpenv(depth, callInfo) }

/**
 * Alias for [bpenv]. Sets the BPF envelope depth.
 *
 * ```KlangScript(Playable)
 * note("c4").bandf(200).bpe(3000)   // alias for bpenv()
 * ```
 *
 * ```KlangScript(Playable)
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
@KlangScript.Function
fun SprudelPattern.bpe(depth: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.bpenv(depth, callInfo)

/** Alias for [bpenv] on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.bpe(depth: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).bpe(depth, callInfo)

/** Creates a [PatternMapperFn] that sets the BPF envelope depth (alias for [bpenv]). */
@SprudelDsl
@KlangScript.Function
fun bpe(depth: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    bpenv(depth, callInfo)

/** Creates a chained [PatternMapperFn] that sets BPF envelope depth (alias for [bpenv]) after the previous mapper. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.bpe(depth: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.bpenv(depth, callInfo)
