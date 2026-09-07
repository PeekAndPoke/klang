/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelDiagnostics
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelVoiceData
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.pattern.MergePattern
import io.peekandpoke.klang.sprudel.pattern.StackPattern

// -- jux() ------------------------------------------------------------------------------------------------------------

private fun applyJux(source: SprudelPattern, transform: PatternMapperFn): SprudelPattern {
    // Pan is unipolar (0.0 to 1.0).
    // jux pans original hard left (0.0) and transformed hard right (1.0).
    val left = source.pan(0.0)
    val right = transform(source).pan(1.0)
    return StackPattern(listOf(left, right))
}

/**
 * Pans this pattern hard left and a transformed version hard right.
 *
 * Creates a stereo image by stacking the original panned to 0.0 (left) with `transform(this)` panned
 * to 1.0 (right). Useful for stereo width or call-and-response effects.
 *
 * @param transform Function applied to the right-channel copy of the pattern.
 * @return A stereo pattern with the original on the left and the transformed copy on the right.
 *
 * ```KlangScript(Playable)
 * s("bd sd").jux(x => x.rev())       // reversed pattern panned right
 * ```
 *
 * ```KlangScript(Playable)
 * note("c e g").jux(x => x.fast(2))  // double-speed version panned right
 * ```
 *
 * @category structural
 * @tags jux, pan, stereo, spatial, transform
 */
@KlangScript.Function
fun SprudelPattern.jux(transform: PatternMapperFn, @Suppress("unused") callInfo: CallInfo? = null): SprudelPattern =
    applyJux(this, transform)

/** Pans this string pattern hard left and a transformed version hard right. */
@KlangScript.Function
fun String.jux(transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).jux(transform, callInfo)

/**
 * Returns a [PatternMapperFn] that pans the source hard left and a transformed version hard right.
 *
 * @param transform Function applied to the right-channel copy of the source pattern.
 * @return A [PatternMapperFn] producing a stereo pattern.
 *
 * ```KlangScript(Playable)
 * s("bd sd").apply(jux(x => x.rev()))   // via mapper
 * ```
 *
 * @category structural
 * @tags jux, pan, stereo, spatial, transform
 */
@KlangScript.Function
fun jux(transform: PatternMapperFn, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.jux(transform, callInfo) }

/** Chains a jux onto this [PatternMapperFn]; pans left and transformed-right. */
@KlangScript.Function
fun PatternMapperFn.jux(transform: PatternMapperFn, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.jux(transform, callInfo) }

// -- juxBy() ----------------------------------------------------------------------------------------------------------

private fun applyJuxBy(source: SprudelPattern, amount: Double, transform: PatternMapperFn): SprudelPattern {
    // Unipolar pan: 0.0 is left, 1.0 is right, 0.5 is center.
    // amount=1.0 -> 0.0 (L) & 1.0 (R) - full stereo
    // amount=0.5 -> 0.25 (L) & 0.75 (R) - half stereo width
    // amount=0.0 -> 0.5 (L) & 0.5 (R) - mono/center
    val panLeft = 0.5 * (1.0 - amount)
    val panRight = 0.5 * (1.0 + amount)

    val left = source.pan(panLeft)
    val right = transform(source).pan(panRight)

    return StackPattern(listOf(left, right))
}

/**
 * Like [jux], but with adjustable stereo width.
 *
 * Pans the original left by `0.5 * (1 - amount)` and the transformed copy right by `0.5 * (1 + amount)`.
 * At `amount = 1.0` (full stereo) this is equivalent to [jux]. At `amount = 0.0` both copies are centred.
 *
 * @param amount Stereo width from 0.0 (mono) to 1.0 (full hard pan).
 * @param transform Function applied to the right-channel copy.
 * @return A stereo pattern with width controlled by `amount`.
 *
 * ```KlangScript(Playable)
 * s("bd sd").juxBy(0.5, x => x.rev())        // half stereo width, reversed on right
 * ```
 *
 * ```KlangScript(Playable)
 * note("c e g").juxBy(0.75, x => x.fast(2))  // 75% stereo, faster on right
 * ```
 *
 * @category structural
 * @tags juxBy, jux, pan, stereo, spatial, width
 */
@KlangScript.Function
fun SprudelPattern.juxBy(amount: Double, transform: PatternMapperFn, @Suppress("unused") callInfo: CallInfo? = null): SprudelPattern =
    applyJuxBy(this, amount, transform)

/** Like [jux] with adjustable stereo width on a string pattern. */
@KlangScript.Function
fun String.juxBy(amount: Double, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).juxBy(amount, transform, callInfo)

/**
 * Returns a [PatternMapperFn] that pans the source with adjustable stereo width.
 *
 * @param amount Stereo width from 0.0 (mono) to 1.0 (full hard pan).
 * @param transform Function applied to the right-channel copy.
 * @return A [PatternMapperFn] producing a stereo pattern with the given width.
 *
 * ```KlangScript(Playable)
 * s("bd sd").apply(juxBy(0.5, x => x.rev()))  // via mapper
 * ```
 *
 * @category structural
 * @tags juxBy, jux, pan, stereo, spatial, width
 */
@KlangScript.Function
fun juxBy(amount: Double, transform: PatternMapperFn, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.juxBy(amount, transform, callInfo) }

/** Chains a juxBy onto this [PatternMapperFn]; pans with adjustable stereo width. */
@KlangScript.Function
fun PatternMapperFn.juxBy(amount: Double, transform: PatternMapperFn, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.juxBy(amount, transform, callInfo) }

// -- off() ------------------------------------------------------------------------------------------------------------

private fun applyOff(source: SprudelPattern, time: Double, transform: PatternMapperFn): SprudelPattern {
    return source.stack(transform(source).late(time))
}

/**
 * Layers a time-shifted, transformed copy of this pattern on top of itself.
 *
 * Stacks the original with a delayed copy produced by applying [transform]. Useful for creating rhythmic
 * echoes, counterpoint, or call-and-response effects.
 *
 * @param time Time offset in cycles for the delayed copy. Default is 0.25 (quarter cycle).
 * @param transform Function applied to the delayed copy.
 * @return The original pattern stacked with a time-shifted, transformed copy.
 *
 * ```KlangScript(Playable)
 * s("bd sd").off(0.125, x => x.gain(0.2))       // quiet echo 1/8 cycle behind
 * ```
 *
 * ```KlangScript(Playable)
 * note("c e g").off(0.25, x => x.transpose(12)) // octave-up copy a quarter cycle behind
 * ```
 *
 * @category structural
 * @tags off, delay, echo, layer, stack, time
 */
@KlangScript.Function
fun SprudelPattern.off(time: Double, transform: PatternMapperFn, @Suppress("unused") callInfo: CallInfo? = null): SprudelPattern =
    applyOff(this, time, transform)

/** Layers a time-shifted, transformed copy of this string pattern on top of itself. */
@KlangScript.Function
fun String.off(time: Double, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).off(time, transform, callInfo)

/**
 * Returns a [PatternMapperFn] that layers a time-shifted, transformed copy on top of the source.
 *
 * @param time Time offset in cycles for the delayed copy.
 * @param transform Function applied to the delayed copy.
 * @return A [PatternMapperFn] that stacks the source with a late, transformed copy.
 *
 * ```KlangScript(Playable)
 * s("bd sd").apply(off(0.125, x => x.gain(0.2)))   // via mapper
 * ```
 *
 * @category structural
 * @tags off, delay, echo, layer, stack, time
 */
@KlangScript.Function
fun off(time: Double, transform: PatternMapperFn, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.off(time, transform, callInfo) }

/** Chains an off onto this [PatternMapperFn]; layers a time-shifted, transformed copy. */
@KlangScript.Function
fun PatternMapperFn.off(time: Double, transform: PatternMapperFn, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.off(time, transform, callInfo) }

// -- superimpose() ----------------------------------------------------------------------------------------------------

private fun applySuperimpose(source: SprudelPattern, transforms: Array<out PatternMapperFn>): SprudelPattern {
    val transformed = transforms.map { it(source) }
    return source.stack(*transformed.toTypedArray())
}

/**
 * Layers one or more transformed copies of this pattern on top of itself.
 *
 * Stacks the original pattern with the result of applying each [transforms] to it.
 * Unlike [off], the copies are not time-shifted — all layers start at the same position.
 *
 * @param transforms Functions applied to produce the additional layers.
 * @return The original pattern stacked with its transformed copy.
 *
 * ```KlangScript(Playable)
 * s("bd sd").superimpose(x => x.fast(2))                         // double-speed layer on top
 * ```
 *
 * ```KlangScript(Playable)
 * note("c e g").superimpose(x => x.transpose(7), x => x.transpose(12))  // fifth and octave stacked
 * ```
 *
 * @category structural
 * @tags superimpose, layer, stack, transform
 */
@KlangScript.Function
fun SprudelPattern.superimpose(vararg transforms: PatternMapperFn, @Suppress("unused") callInfo: CallInfo? = null): SprudelPattern =
    applySuperimpose(this, transforms)

/** Layers a transformed copy of this string pattern on top of itself. */
@KlangScript.Function
fun String.superimpose(vararg transforms: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).superimpose(*transforms, callInfo = callInfo)

/**
 * Returns a [PatternMapperFn] that layers a transformed copy of the source on top of itself.
 *
 * @param transforms Functions applied to produce the additional layers.
 * @return A [PatternMapperFn] that stacks the source with its transformed copy.
 *
 * ```KlangScript(Playable)
 * s("bd sd").apply(superimpose(x => x.fast(2)))   // via mapper
 * ```
 *
 * @category structural
 * @tags superimpose, layer, stack, transform
 */
@KlangScript.Function
fun superimpose(vararg transforms: PatternMapperFn, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.superimpose(*transforms, callInfo = callInfo) }

/** Chains a superimpose onto this [PatternMapperFn]; layers a transformed copy on top. */
@KlangScript.Function
fun PatternMapperFn.superimpose(vararg transforms: PatternMapperFn, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.superimpose(*transforms, callInfo = callInfo) }

// -- layer() ----------------------------------------------------------------------------------------------------------

private fun applyLayer(source: SprudelPattern, transforms: Array<out PatternMapperFn>): SprudelPattern {
    if (transforms.isEmpty()) {
        return source // we keep the pattern as is
    }

    val patterns = transforms.map { transform ->
        try {
            transform(source)
        } catch (e: Exception) {
            SprudelDiagnostics.report("layer transform", e)
            source
        }
    }

    return if (patterns.size == 1) {
        patterns.first()
    } else {
        StackPattern(patterns)
    }
}

/**
 * Applies one or more transformation functions to this pattern and stacks the results.
 *
 * Each function in [transforms] is applied to the original pattern independently, and all
 * results are stacked together. Useful for building complex textures from a single source.
 *
 * @param transforms One or more functions to apply; each result is stacked with the others.
 * @return All transformed copies stacked as a single pattern.
 *
 * ```KlangScript(Playable)
 * s("bd hh sd oh").layer(x => x.fast(2), x => x.rev())              // two transformed layers stacked
 * ```
 *
 * ```KlangScript(Playable)
 * note("c e g").layer(x => x.transpose(7), x => x.transpose(12))    // fifth and octave stacked
 * ```
 *
 * @alias apply
 * @category structural
 * @tags layer, stack, transform, superimpose
 */
@KlangScript.Function
fun SprudelPattern.layer(vararg transforms: PatternMapperFn, @Suppress("unused") callInfo: CallInfo? = null): SprudelPattern =
    applyLayer(this, transforms)

/** Applies transformations to this string pattern and stacks the results. */
@KlangScript.Function
fun String.layer(vararg transforms: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).layer(*transforms, callInfo = callInfo)

/**
 * Returns a [PatternMapperFn] that applies the given transforms to the source and stacks results.
 *
 * @param transforms One or more functions; each is applied to the source and results are stacked.
 * @return A [PatternMapperFn] that stacks the transformed copies.
 *
 * ```KlangScript(Playable)
 * s("bd sd").apply(layer(x => x.fast(2), x => x.rev()))   // via mapper
 * ```
 *
 * @alias apply
 * @category structural
 * @tags layer, stack, transform, superimpose
 */
@KlangScript.Function
fun layer(vararg transforms: PatternMapperFn, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.layer(*transforms, callInfo = callInfo) }

/** Chains a layer onto this [PatternMapperFn]; stacks the transformed copies. */
@KlangScript.Function
fun PatternMapperFn.layer(vararg transforms: PatternMapperFn, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.layer(*transforms, callInfo = callInfo) }

/**
 * Alias for [layer] — applies multiple transformation functions and stacks the results.
 *
 * @param transforms One or more functions to apply; results are stacked.
 * @return All transformed copies stacked as a single pattern.
 *
 * ```KlangScript(Playable)
 * s("bd hh sd oh").apply(x => x.fast(2), x => x.rev())   // two layers stacked
 * ```
 *
 * ```KlangScript(Playable)
 * note("c e").apply(x => x.transpose(7))                  // fifth layer stacked
 * ```
 *
 * @alias layer
 * @category structural
 * @tags layer, stack, transform, apply
 */
@KlangScript.Function
fun SprudelPattern.apply(vararg transforms: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    this.layer(*transforms, callInfo = callInfo)

/** Alias for [layer] on a string pattern. */
@KlangScript.Function
fun String.apply(vararg transforms: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).apply(*transforms, callInfo = callInfo)

/**
 * Returns a [PatternMapperFn] — alias for [layer] — that applies transforms and stacks results.
 *
 * @param transforms One or more functions; results are stacked.
 * @return A [PatternMapperFn] that stacks the transformed copies.
 *
 * ```KlangScript(Playable)
 * s("bd sd").apply(layer(x => x.fast(2), x => x.rev()))   // apply the layer mapper
 * ```
 *
 * @alias layer
 * @category structural
 * @tags layer, stack, transform, apply
 */
@KlangScript.Function
fun apply(vararg transforms: PatternMapperFn, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.apply(*transforms, callInfo = callInfo) }

/** Chains an apply (alias for [layer]) onto this [PatternMapperFn]; stacks the transformed copies. */
@KlangScript.Function
fun PatternMapperFn.apply(vararg transforms: PatternMapperFn, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.apply(*transforms, callInfo = callInfo) }

// -- merge() ----------------------------------------------------------------------------------------------------------

private fun applyMerge(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val ctrl = args.toPattern()
    return MergePattern(source = pattern, control = ctrl)
}

/**
 * Overlays voice properties from a control pattern onto this pattern's events.
 *
 * For each source event the control is sampled at the event's onset time. Non-null fields
 * from the control's [SprudelVoiceData] override the corresponding fields in the source event.
 * Source fields that the control leaves `null` are kept unchanged.
 *
 * ```KlangScript(Playable)
 * s("hh hh hh hh").merge(note("c3 d3 e3 f3"))   // high-hat gains note values from the note pattern
 * ```
 *
 * ```KlangScript(Playable)
 * s("hh!8").merge(freq("100 200 300 400"))   // high-hat gains frequencies
 * ```
 *
 * ```KlangScript(Playable)
 * s("hh!8").merge("x!8".freq(sine.range(100, 1000)))   // high-hat gains frequencies
 * ```
 *
 * ```KlangScript(Playable)
 * note("<[c3 d3] [e3 f3]>").merge(seq("<12000 8000 4000 2000>").onepole())   // notes darken per event (one-pole Hz)
 * ```
 *
 * @param ctrl The pattern (or mini-notation string) whose voice data is merged in.
 *
 * @category structural
 * @tags merge, overlay, combine, voice, data
 */
@KlangScript.Function
fun SprudelPattern.merge(ctrl: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyMerge(this, listOf(ctrl).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and overlays voice properties from the control pattern.
 *
 * ```KlangScript(Playable)
 * "1 2 3 4".merge("<12000 8000 4000 2000>".onepole()).scale("c3:major").n()   // value sequence gains warmth from control
 * ```
 *
 * @param ctrl The pattern (or mini-notation string) whose voice data is merged in.
 */
@KlangScript.Function
fun String.merge(ctrl: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).merge(ctrl, callInfo)

/**
 * Creates a [PatternMapperFn] that overlays voice properties from the control pattern.
 *
 * ```KlangScript(Playable)
 * seq("1 2").apply(merge(seq("8000 3000").onepole())).scale("c3:major").n()   // apply warmth overlay as a mapper
 * ```
 *
 * @param ctrl The pattern (or mini-notation string) whose voice data is merged in.
 */
@KlangScript.Function
fun merge(ctrl: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.merge(ctrl, callInfo) }

/**
 * Chains a voice-data overlay onto this [PatternMapperFn].
 *
 * @param ctrl The pattern (or mini-notation string) whose voice data is merged in.
 */
@KlangScript.Function
fun PatternMapperFn.merge(ctrl: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.merge(ctrl, callInfo) }

