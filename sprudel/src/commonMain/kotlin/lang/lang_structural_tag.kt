/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("ObjectPropertyName")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.script.runtime.FunctionValue
import io.peekandpoke.klang.script.runtime.ObjectValue
import io.peekandpoke.klang.script.runtime.convertFunctionToKotlin
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.pattern.ReinterpretPattern.Companion.reinterpretVoice
import io.peekandpoke.klang.sprudel.pattern.TweaksPattern
import io.peekandpoke.klang.sprudel.withTag
import io.peekandpoke.klang.sprudel.withTweaks

// -- tag() ------------------------------------------------------------------------------------------------------------

// The tag name is a LITERAL — deliberately NOT routed through the lift helpers, which would parse
// it as mini-notation (`.tag("guitar 1")` must stay ONE tag, not two events).
private fun applyTag(source: SprudelPattern, name: String): SprudelPattern =
    source.reinterpretVoice { vd -> vd.withTag(name) }

/**
 * Tags every event of this pattern with a semantic name, e.g. for visualizations to know which
 * instrument an event belongs to.
 *
 * Tags accumulate as a set: chaining adds (`.tag("a").tag("b")` → both), duplicates are ignored,
 * and there is NO ordering guarantee. Outer tags join inner ones, so parts keep their identity
 * inside a tagged group. Tags ride on the scheduled voice data (UI signal stream and wire) and
 * never change the sound.
 *
 * ```KlangScript
 * note("c3 e3 g3").tag("guitar1")   // every event carries "guitar1"
 * ```
 *
 * ```KlangScript
 * stack(
 *   s("bd*4").tag("drums"),
 *   note("c2 g2").tag("bass")
 * ).tag("band")                     // events carry {"drums","band"} resp. {"bass","band"}
 * ```
 *
 * @param name The tag to add. Taken literally — not parsed as mini-notation.
 * @return A new pattern whose events carry the tag.
 * @category structural
 * @tags tag, tags, visualization, metadata
 */
@KlangScript.Function
fun SprudelPattern.tag(name: String, callInfo: CallInfo? = null): SprudelPattern =
    applyTag(this, name)

/**
 * Parses this string as a pattern and tags every event with a semantic name.
 *
 * ```KlangScript
 * "c3 e3 g3".tag("lead").note()     // tag the string pattern's events
 * ```
 *
 * @param name The tag to add. Taken literally — not parsed as mini-notation.
 * @return A new pattern whose events carry the tag.
 */
@KlangScript.Function
fun String.tag(name: String, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).tag(name, callInfo)

/**
 * Creates a [PatternMapperFn] that tags every event of the input pattern.
 *
 * ```KlangScript
 * s("bd*4").apply(tag("drums"))     // tag via a mapper
 * ```
 *
 * @param name The tag to add. Taken literally — not parsed as mini-notation.
 * @return A mapper that tags the pattern it is applied to.
 * @category structural
 * @tags tag, tags, visualization, metadata
 */
@KlangScript.Function
fun tag(name: String, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.tag(name, callInfo) }

/**
 * Chains a tag operation onto this [PatternMapperFn].
 *
 * ```KlangScript
 * s("bd*4").apply(tag("drums").tag("kit"))   // both tags accumulate
 * ```
 *
 * @param name The tag to add. Taken literally — not parsed as mini-notation.
 * @return A mapper that additionally tags the pattern it is applied to.
 */
@KlangScript.Function
fun PatternMapperFn.tag(name: String, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.tag(name, callInfo) }

// -- tweak() ----------------------------------------------------------------------------------------------------------

// Like tag(), the name is a LITERAL — never routed through the lift helpers, which would parse it
// as mini-notation.
internal fun applyTweaks(source: SprudelPattern, names: List<String>): SprudelPattern = when {
    names.isEmpty() -> source
    else -> source.reinterpretVoice { vd -> vd.withTweaks(names) }
}

/**
 * Attaches a tweak name to every event of this pattern. It only marks: the transform the name
 * refers to is bound later by `tweaks(...)`, and a name nothing binds stays inert.
 *
 * Tweaks accumulate as a LIST, unlike [tag]: they apply in the order attached and a repeated name
 * applies twice. The usual place to attach one is mini-notation (`note("c3 e3{swell}")`); this
 * function is for marking a whole pattern at once.
 *
 * ```KlangScript
 * note("c3 e3 g3").tweak("swell")   // every event carries "swell"
 * ```
 *
 * @param name The tweak to attach. Taken literally — not parsed as mini-notation.
 * @return A new pattern whose events carry the tweak name.
 * @category structural
 * @tags tweak, tweaks, modifier, metadata
 */
@KlangScript.Function
fun SprudelPattern.tweak(name: String, @Suppress("unused") callInfo: CallInfo? = null): SprudelPattern =
    applyTweaks(this, listOf(name))

/**
 * Parses this string as a pattern and attaches a tweak name to every event.
 *
 * ```KlangScript
 * "c3 e3 g3".tweak("swell").note()
 * ```
 *
 * @param name The tweak to attach. Taken literally — not parsed as mini-notation.
 * @return A new pattern whose events carry the tweak name.
 */
@KlangScript.Function
fun String.tweak(name: String, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).tweak(name, callInfo)

/**
 * Creates a [PatternMapperFn] that attaches a tweak name to every event of the input pattern.
 *
 * ```KlangScript
 * note("c3 e3").apply(tweak("swell"))
 * ```
 *
 * @param name The tweak to attach. Taken literally — not parsed as mini-notation.
 * @return A mapper that marks the pattern it is applied to.
 * @category structural
 * @tags tweak, tweaks, modifier, metadata
 */
@KlangScript.Function
fun tweak(name: String, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.tweak(name, callInfo) }

/**
 * Chains a tweak marking onto this [PatternMapperFn].
 *
 * ```KlangScript
 * note("c3 e3").apply(tweak("swell").tweak("bend"))   // both names accumulate, in this order
 * ```
 *
 * @param name The tweak to attach. Taken literally — not parsed as mini-notation.
 * @return A mapper that additionally marks the pattern it is applied to.
 */
@KlangScript.Function
fun PatternMapperFn.tweak(name: String, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.tweak(name, callInfo) }

// -- tweaks() ---------------------------------------------------------------------------------------------------------

/** Turns a script object literal of `name: x => …` into the Kotlin binding map. Non-function values are ignored. */
private fun ObjectValue.toTweakDefs(): Map<String, PatternMapperFn> = buildMap {
    for ((name, value) in properties) {
        val fn = (value as? FunctionValue)?.convertFunctionToKotlin<Function1<Any?, Any?>>() ?: continue
        put(name) { pattern -> fn(pattern) as SprudelPattern }
    }
}

/**
 * Binds tweak names to transforms and applies them to the events carrying those names.
 *
 * Names are attached upstream, in mini-notation or via [tweak]; this is where they finally mean
 * something. Tweaks apply **in the order written on the event**, not in the order this map declares
 * them, and a name repeated on an event applies twice.
 *
 * A name nothing binds passes through **untouched and unstripped**, so an inner `tweaks(...)` and an
 * outer one compose without either knowing about the other. Applying does not consume the name
 * either: two calls binding the same name both apply.
 *
 * Placement matters. A tweak applies where this call sits in the chain, so anything after it still
 * overrides: put it last when the tweak should win.
 *
 * ```KlangScript
 * note("c3 e3{swell} g3{swell bend}").tweaks({
 *     swell: x => x.adsr(attack = 0.3).gain(1.1),
 *     bend:  x => x.transpose(-2).accelerate(0.5),
 * })
 * ```
 *
 * @param defs Object literal mapping each tweak name to a transform, e.g. `{ swell: x => x.gain(1.1) }`.
 * @return A new pattern with the bound tweaks applied.
 * @category structural
 * @tags tweak, tweaks, modifier
 */
@KlangScript.Function
fun SprudelPattern.tweaks(defs: ObjectValue, @Suppress("unused") callInfo: CallInfo? = null): SprudelPattern =
    tweaks(defs.toTweakDefs())

/**
 * Kotlin door for [tweaks]: binds tweak names to transforms directly.
 *
 * ```kotlin
 * note("c3 e3{swell}").tweaks(mapOf("swell" to { p: SprudelPattern -> p.gain(1.1) }))
 * ```
 *
 * @param defs Map from tweak name to the transform it stands for.
 * @return A new pattern with the bound tweaks applied.
 */
fun SprudelPattern.tweaks(defs: Map<String, PatternMapperFn>): SprudelPattern = when {
    defs.isEmpty() -> this
    else -> TweaksPattern(inner = this, defs = defs)
}

/**
 * Kotlin door for [tweaks] taking pairs, so no map literal is needed.
 *
 * ```kotlin
 * note("c3 e3{swell}").tweaks("swell" to { p: SprudelPattern -> p.gain(1.1) })
 * ```
 *
 * @param defs Name-to-transform pairs. A repeated name keeps the last binding.
 * @return A new pattern with the bound tweaks applied.
 */
fun SprudelPattern.tweaks(vararg defs: Pair<String, PatternMapperFn>): SprudelPattern =
    tweaks(defs.toMap())

/**
 * Parses this string as a pattern, then binds and applies tweaks.
 *
 * ```KlangScript
 * "c3 e3{swell}".tweaks({ swell: x => x.gain(1.1) }).note()
 * ```
 *
 * @param defs Object literal mapping each tweak name to a transform.
 * @return A new pattern with the bound tweaks applied.
 */
@KlangScript.Function
fun String.tweaks(defs: ObjectValue, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).tweaks(defs, callInfo)

/**
 * Creates a [PatternMapperFn] that binds and applies tweaks to the input pattern.
 *
 * ```KlangScript
 * note("c3 e3{swell}").apply(tweaks({ swell: x => x.gain(1.1) }))
 * ```
 *
 * @param defs Object literal mapping each tweak name to a transform.
 * @return A mapper that applies the bound tweaks.
 * @category structural
 * @tags tweak, tweaks, modifier
 */
@KlangScript.Function
fun tweaks(defs: ObjectValue, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.tweaks(defs, callInfo) }

/**
 * Chains a tweak binding onto this [PatternMapperFn].
 *
 * @param defs Object literal mapping each tweak name to a transform.
 * @return A mapper that additionally applies the bound tweaks.
 */
@KlangScript.Function
fun PatternMapperFn.tweaks(defs: ObjectValue, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.tweaks(defs, callInfo) }
