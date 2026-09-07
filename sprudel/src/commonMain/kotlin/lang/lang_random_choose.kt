/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.pattern.AtomicPattern
import io.peekandpoke.klang.sprudel.pattern.ChoicePattern
import io.peekandpoke.klang.sprudel.pattern.StructurePattern

// -- chooseWith() -----------------------------------------------------------------------------------------------------

private fun applyChooseWith(p: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val xs = args.extractChoiceArgs()
    return ChoicePattern.createFromRaw(p, xs, mode = StructurePattern.Mode.Out)
}

/**
 * Chooses from the given list of values using a selector pattern (values 0–1).
 *
 * The selector pattern controls which value is chosen at each point in time. A value of 0
 * picks the first element, 1 picks the last, and values in between interpolate as an index.
 * Structure comes from the selector pattern (`chooseOut` mode).
 *
 * ```KlangScript(Playable)
 * note("c2 g2 d2 f1").s(chooseWith(sine.fast(2), "sawtooth", "triangle", "bd:6"))
 * ```
 *
 * ```KlangScript(Playable)
 * s(chooseWith(rand, "bd", "sd", "hh"))    // random instrument selection per event
 * ```
 *
 * @param args First argument is the selector pattern (values 0–1); remaining arguments are the values to choose from.
 * @category random
 * @tags chooseWith, choose, selector, random, values
 */
@KlangScript.Function
fun chooseWith(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern {
    val argList = args.toList().asSprudelDslArgs(callInfo)
    val firstArg = argList.firstOrNull()
    return when (val firstVal = firstArg?.value) {
        is SprudelPattern -> applyChooseWith(firstVal, argList.drop(1))
        else -> applyChooseWith(AtomicPattern.pure, argList)
    }
}

/** Uses this pattern (range 0–1) as a selector to choose from the given list. */
@KlangScript.Function
fun SprudelPattern.chooseWith(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyChooseWith(this, args.toList().asSprudelDslArgs(callInfo))

/** Uses this string pattern as a selector to choose from the given list. */
@KlangScript.Function
fun String.chooseWith(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).chooseWith(*args, callInfo = callInfo)

// -- chooseInWith() ---------------------------------------------------------------------------------------------------

private fun applyChooseInWith(p: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val xs = args.extractChoiceArgs()
    return ChoicePattern.createFromRaw(p, xs, mode = StructurePattern.Mode.In)
}

/**
 * Like `chooseWith` but structure comes from the chosen values, not the selector pattern.
 *
 * The selector pattern (values 0–1) controls which value is chosen. The timing structure is
 * taken from the selected value pattern (`chooseIn` mode).
 *
 * ```KlangScript(Playable)
 * chooseInWith(sine, "c d", "e f g", "a b c d")   // selector picks pattern; its timing wins
 * ```
 *
 * @param args First argument is the selector pattern (values 0–1); remaining arguments are the values to choose from.
 * @category random
 * @tags chooseInWith, chooseWith, selector, random, structure
 */
@KlangScript.Function
fun chooseInWith(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern {
    val argList = args.toList().asSprudelDslArgs(callInfo)
    val firstArg = argList.firstOrNull()
    return when (val firstVal = firstArg?.value) {
        is SprudelPattern -> applyChooseInWith(firstVal, argList.drop(1))
        else -> applyChooseInWith(AtomicPattern.pure, argList)
    }
}

/** Uses this pattern as a selector to choose from the given list (structure from chosen). */
@KlangScript.Function
fun SprudelPattern.chooseInWith(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyChooseInWith(this, args.toList().asSprudelDslArgs(callInfo))

/** Uses this string pattern as a selector to choose from the given list (structure from chosen). */
@KlangScript.Function
fun String.chooseInWith(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).chooseInWith(*args, callInfo = callInfo)

// -- choose() ---------------------------------------------------------------------------------------------------------

private fun applyChoose(p: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val xs = args.extractChoiceArgs()
    return ChoicePattern.createFromRaw(p, xs, mode = StructurePattern.Mode.Out)
}

/**
 * Chooses randomly from the given values or patterns at each event.
 *
 * Uses `rand` as the selector, so the choice varies continuously in time. Structure comes
 * from the selector (`chooseOut` mode). For cycle-level choices use `chooseCycles`.
 *
 * ```KlangScript(Playable)
 * note("c2 g2 d2 f1").s(choose("sine", "triangle", "bd:6"))   // random synth each note
 * ```
 *
 * ```KlangScript(Playable)
 * s(choose("bd", "sd", "hh")).fast(8)    // random drum sound every eighth-note
 * ```
 *
 * @param args Values or patterns to randomly choose from at each event.
 * @alias chooseOut
 * @category random
 * @tags choose, random, selection, values, chooseOut
 */
@KlangScript.Function
fun choose(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern {
    val argList = args.toList().asSprudelDslArgs(callInfo)
    val firstArg = argList.firstOrNull()
    return when (val firstVal = firstArg?.value) {
        is SprudelPattern -> applyChoose(firstVal, argList.drop(1))
        else -> applyChoose(AtomicPattern.pure, argList)
    }
}

/**
 * Uses this pattern (range 0–1) as a selector to choose from the given list.
 *
 * The receiver pattern controls the index into the list. Structure comes from the
 * selector pattern (`chooseOut` mode).
 *
 * ```KlangScript(Playable)
 * sine.choose("c", "e", "g", "b")     // sine wave selects among chord tones
 * ```
 *
 * @param args Values or patterns to choose from using the receiver as selector.
 * @alias chooseOut
 * @category random
 * @tags choose, selector, random, chooseOut
 */
@KlangScript.Function
fun SprudelPattern.choose(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyChoose(this, args.toList().asSprudelDslArgs(callInfo))

/** Uses this string pattern as a selector to choose from the given list. */
@KlangScript.Function
fun String.choose(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).choose(*args, callInfo = callInfo)

// -- chooseOut() ------------------------------------------------------------------------------------------------------

/**
 * Alias for [choose].
 *
 * @param args Values or patterns to randomly choose from at each event.
 * @alias choose
 * @category random
 * @tags chooseOut, choose, random, selection
 */
@KlangScript.Function
fun chooseOut(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    choose(*args, callInfo = callInfo)

/** Alias for [choose]. */
@KlangScript.Function
fun SprudelPattern.chooseOut(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.choose(*args, callInfo = callInfo)

/** Alias for [choose]. */
@KlangScript.Function
fun String.chooseOut(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).chooseOut(*args, callInfo = callInfo)

// -- chooseIn() -------------------------------------------------------------------------------------------------------

private fun applyChooseIn(p: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val xs = args.extractChoiceArgs()
    return ChoicePattern.createFromRaw(p, xs, mode = StructurePattern.Mode.In)
}

/**
 * Like `choose`, but structure comes from the chosen values rather than the selector.
 *
 * Uses `rand` as the selector. The timing structure is inherited from the selected value
 * pattern (`chooseIn` mode), so the chosen pattern's own rhythm determines the events.
 *
 * ```KlangScript(Playable)
 * chooseIn("c d", "e f g", "a b c d")   // random pattern; its timing determines structure
 * ```
 *
 * @param args Values or patterns to randomly choose from; structure comes from the chosen value.
 * @category random
 * @tags chooseIn, random, selection, structure
 */
@KlangScript.Function
fun chooseIn(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern {
    val argList = args.toList().asSprudelDslArgs(callInfo)
    val firstArg = argList.firstOrNull()
    return when (val firstVal = firstArg?.value) {
        is SprudelPattern -> applyChooseIn(firstVal, argList.drop(1))
        else -> applyChooseIn(AtomicPattern.pure, argList)
    }
}

/** Uses this pattern as a selector; structure comes from the chosen value. */
@KlangScript.Function
fun SprudelPattern.chooseIn(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyChooseIn(this, args.toList().asSprudelDslArgs(callInfo))

/** Uses this string pattern as a selector; structure comes from the chosen value. */
@KlangScript.Function
fun String.chooseIn(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).chooseIn(*args, callInfo = callInfo)

// -- choose2() --------------------------------------------------------------------------------------------------------

private fun applyChoose2(p: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val xs = args.extractChoiceArgs()
    return ChoicePattern.createFromRaw(p.fromBipolar(), xs, mode = StructurePattern.Mode.Out)
}

/**
 * Like `choose`, but the selector pattern should be in the range -1 to 1 (bipolar).
 *
 * The receiver bipolar pattern is converted to 0–1 before indexing into the list, so
 * `rand2` and LFOs centred at zero can be used directly as selectors.
 *
 * ```KlangScript(Playable)
 * rand2.choose2("c", "e", "g", "b")    // bipolar rand selects among chord tones
 * ```
 *
 * @param args Values or patterns to choose from using the bipolar receiver (-1 to 1) as selector.
 * @category random
 * @tags choose2, bipolar, selector, random
 */
@KlangScript.Function
fun SprudelPattern.choose2(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyChoose2(this, args.toList().asSprudelDslArgs(callInfo))

/** Like `choose`, but the selector pattern should be in the range -1 to 1 (bipolar). */
@KlangScript.Function
fun String.choose2(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).choose2(*args, callInfo = callInfo)

// -- chooseCycles() ---------------------------------------------------------------------------------------------------

private fun applyChooseCyclesPattern(p: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val xs = (listOf(p) + args.map { it.value }).asSprudelDslArgs()
    return ChoicePattern.createFromRaw(rand.segment(1), xs, mode = StructurePattern.Mode.In)
}

/**
 * Picks one of the given values or patterns at random, changing once per cycle.
 *
 * The entire cycle plays the same chosen pattern. Unlike `choose`, which can vary within a
 * cycle, `chooseCycles` makes a fresh random choice only at cycle boundaries.
 *
 * ```KlangScript(Playable)
 * chooseCycles("bd", "hh", "sd").s().fast(8)   // entire cycle uses one random drum sound
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd | hh | sd").fast(8)                    // mini-notation equivalent using |
 * ```
 *
 * @param args Values or patterns to randomly pick from; one is chosen per cycle.
 * @alias randcat
 * @category random
 * @tags chooseCycles, random, cycle, randcat, selection
 */
@KlangScript.Function
fun chooseCycles(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern {
    val argList = args.toList().asSprudelDslArgs(callInfo)
    val firstArg = argList.firstOrNull()
    return when (val firstVal = firstArg?.value) {
        is SprudelPattern -> applyChooseCyclesPattern(firstVal, argList.drop(1))
        else -> applyChooseCyclesPattern(AtomicPattern.pure, argList)
    }
}

/** Picks one of the given values at random, changing once per cycle. */
@KlangScript.Function
fun SprudelPattern.chooseCycles(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyChooseCyclesPattern(this, args.toList().asSprudelDslArgs(callInfo))

/** Picks one of the given values at random, changing once per cycle. */
@KlangScript.Function
fun String.chooseCycles(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).chooseCycles(*args, callInfo = callInfo)

// -- randcat() --------------------------------------------------------------------------------------------------------

/**
 * Alias for [chooseCycles].
 *
 * @param args Values or patterns to randomly pick from; one is chosen per cycle.
 * @alias chooseCycles
 * @category random
 * @tags randcat, chooseCycles, random, cycle
 */
@KlangScript.Function
fun randcat(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    chooseCycles(*args, callInfo = callInfo)

/** Alias for [chooseCycles]. */
@KlangScript.Function
fun SprudelPattern.randcat(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.chooseCycles(*args, callInfo = callInfo)

/** Alias for [chooseCycles]. */
@KlangScript.Function
fun String.randcat(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).randcat(*args, callInfo = callInfo)

// -- wchoose() --------------------------------------------------------------------------------------------------------

private fun applyWchoose(p: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val (items, weights) = args.extractWeightedPairs()
    return ChoicePattern.createFromRaw(
        selector = p,
        choices = items,
        weights = weights,
        mode = StructurePattern.Mode.Out,
    )
}

/**
 * Chooses randomly from the given values according to relative weights.
 *
 * Each choice is a `[value, weight]` pair. Higher weights make that value more likely.
 * Uses `rand` as the selector so choices vary within a cycle.
 *
 * ```KlangScript(Playable)
 * note("c2 g2 d2 f1").s(wchoose(["sine", 10], ["triangle", 1]))
 * ```
 *
 * ```KlangScript(Playable)
 * s(wchoose(["bd", 8], ["sd", 2], ["hh", 5])).fast(8)
 * ```
 *
 * @param args `[value, weight]` pairs — higher weight means more likely selection.
 * @category random
 * @tags wchoose, weighted, random, probability, selection
 */
@KlangScript.Function
fun wchoose(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern {
    val argList = args.toList().asSprudelDslArgs(callInfo)
    val firstArg = argList.firstOrNull()
    return when (val firstVal = firstArg?.value) {
        is SprudelPattern -> applyWchoose(firstVal, argList.drop(1))
        else -> applyWchoose(AtomicPattern.pure, argList)
    }
}

/** Uses this pattern (range 0–1) as a weighted selector over the given value/weight pairs. */
@KlangScript.Function
fun SprudelPattern.wchoose(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyWchoose(this, args.toList().asSprudelDslArgs(callInfo))

/** Uses this string pattern as a weighted selector over the given value/weight pairs. */
@KlangScript.Function
fun String.wchoose(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).wchoose(*args, callInfo = callInfo)

// -- wchooseCycles() --------------------------------------------------------------------------------------------------

private fun applyWchooseCyclesPattern(p: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val (items, weights) = args.extractWeightedPairs()
    val allItems = (listOf(p) + items.map { it.value }).asSprudelDslArgs()
    val allWeights = (listOf(1.0) + weights.map { it.value }).asSprudelDslArgs()

    return ChoicePattern.createFromRaw(
        selector = rand.segment(1),
        choices = allItems,
        weights = allWeights,
        mode = StructurePattern.Mode.In,
    )
}

/**
 * Picks one of the given values at random each cycle according to relative weights.
 *
 * Like `chooseCycles` but each choice can have a different probability. The entire cycle
 * plays the same chosen value. Each choice is a `[value, weight]` pair.
 *
 * ```KlangScript(Playable)
 * wchooseCycles(["bd", 10], ["hh", 1]).s().fast(8)   // bd much more likely
 * ```
 *
 * @param args `[value, weight]` pairs — higher weight means more likely selection. One is chosen per cycle.
 * @alias wrandcat
 * @category random
 * @tags wchooseCycles, weighted, random, cycle, wrandcat
 */
@KlangScript.Function
fun wchooseCycles(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern {
    val argList = args.toList().asSprudelDslArgs(callInfo)
    val firstArg = argList.firstOrNull()
    return when (val firstVal = firstArg?.value) {
        is SprudelPattern -> applyWchooseCyclesPattern(firstVal, argList.drop(1))
        else -> applyWchooseCyclesPattern(AtomicPattern.pure, argList)
    }
}

/** Picks a weighted random value once per cycle. */
@KlangScript.Function
fun SprudelPattern.wchooseCycles(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyWchooseCyclesPattern(this, args.toList().asSprudelDslArgs(callInfo))

/** Picks a weighted random value once per cycle. */
@KlangScript.Function
fun String.wchooseCycles(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).wchooseCycles(*args, callInfo = callInfo)

// -- wrandcat() -------------------------------------------------------------------------------------------------------

/**
 * Alias for [wchooseCycles].
 *
 * @param args `[value, weight]` pairs — higher weight means more likely selection. One is chosen per cycle.
 * @alias wchooseCycles
 * @category random
 * @tags wrandcat, wchooseCycles, weighted, random, cycle
 */
@KlangScript.Function
fun wrandcat(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    wchooseCycles(*args, callInfo = callInfo)

/** Alias for [wchooseCycles]. */
@KlangScript.Function
fun SprudelPattern.wrandcat(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.wchooseCycles(*args, callInfo = callInfo)

/** Alias for [wchooseCycles]. */
@KlangScript.Function
fun String.wrandcat(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).wrandcat(*args, callInfo = callInfo)
