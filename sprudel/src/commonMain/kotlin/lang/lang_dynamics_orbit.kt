/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions", "ClassName")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel._liftOrReinterpretNumericalField
import io.peekandpoke.klang.sprudel._liftOrReinterpretStringField
import io.peekandpoke.klang.sprudel._mapNumericField
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs

// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Routing
// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

// -- orbit() / o() ----------------------------------------------------------------------------------------------------

private val orbitMutation = voiceSetter {
    cylinder = it?.asIntOrNull()
}

private fun applyOrbit(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.cylinder?.toDouble() }, update = orbitMutation)
    }

    return source._liftOrReinterpretStringField(args, orbitMutation)
}

/**
 * Routes the pattern to an audio output orbit (channel group) for independent effect processing.
 *
 * Each orbit can have its own reverb, delay, and other effects applied independently.
 * Use different orbit numbers to send patterns to different effect buses.
 *
 * ```KlangScript(Playable)
 * s("bd sd").orbit(1)                           // send drums to orbit 1
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3").orbit(2).room(wet = 0.8, size = 4)  // melodic line on orbit 2 with reverb
 * ```
 *
 * @param index The orbit index to route events to.
 *
 * @alias o
 * @category dynamics
 * @tags orbit, o, routing, effects, bus, channel
 */
@KlangScript.Function
fun SprudelPattern.orbit(index: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyOrbit(this, listOfNotNull(index).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and routes it to the given audio output orbit.
 *
 * ```KlangScript(Playable)
 * "bd sd".orbit(1).s()   // send drums to orbit 1
 * ```
 *
 * @param index The orbit index to route events to.
 */
@KlangScript.Function
fun String.orbit(index: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).orbit(index, callInfo)

/**
 * The orbit (effect bus) of each event, as a value other setters can read.
 *
 * Bare `orbit` reads what the chain has set so far, so it comes after whatever set the field
 * (`orbit(...)` or an alias). Call it, `orbit(...)`, to set the field; a mapper argument applies
 * to the field.
 *
 * Aliases: `o`.
 *
 * ```KlangScript(Playable)
 * s("bd sd").orbit(0).orbit(add("0 1"))                                    // the snare on orbit 1
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd").orbit("0 1").room(orbit.mul(0.3))                         // more reverb on the higher orbit
 * ```
 *
 * @category dynamics
 * @tags orbit, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("orbit")
object orbit : FieldAccessor({ it.cylinder?.toDouble() }) {

    /**
     * Creates a [PatternMapperFn] that routes events to the given audio output orbit.
     *
     * ```KlangScript(Playable)
     * s("bd sd").apply(orbit(1))   // send drums to orbit 1
     * ```
     *
     * @param index The orbit index to route events to.
     */
    @KlangScript.Invoke
    operator fun invoke(index: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        { p -> p.orbit(index, callInfo) }
}

/**
 * Creates a chained [PatternMapperFn] that routes events to the given orbit after the previous mapper.
 *
 * ```KlangScript(Playable)
 * s("bd sd").apply(gain(0.8).orbit(1))  // gain + orbit chained
 * ```
 *
 * @param index The orbit index to route events to.
 */
@KlangScript.Function
fun PatternMapperFn.orbit(index: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.orbit(index, callInfo) }

/**
 * Alias for [orbit]. Routes the pattern to an audio output orbit for independent effect processing.
 *
 * ```KlangScript(Playable)
 * s("bd sd").o(1)                       // send drums to orbit 1
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3").o(2).room(0.8)          // melodic line on orbit 2 with reverb
 * ```
 *
 * @param index The orbit index to route events to.
 *
 * @alias orbit
 * @category dynamics
 * @tags o, orbit, routing, effects, bus, channel
 */
@KlangScript.Function
fun SprudelPattern.o(index: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.orbit(index, callInfo)

/**
 * Alias for [orbit]. Parses this string as a pattern and routes it to the given orbit.
 *
 * ```KlangScript(Playable)
 * "bd sd".o(1).s()   // send drums to orbit 1
 * ```
 *
 * @param index The orbit index to route events to.
 */
@KlangScript.Function
fun String.o(index: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).orbit(index, callInfo)

/**
 * Alias of [orbit]: the same accessor under another name.
 *
 * @category dynamics
 * @tags o, orbit, accessor
 */
@KlangScript.Constant
val o: orbit = orbit

// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Ducking / Sidechain
// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

// -- duck ------------------------------------------------------------------------------------------------------------

private val duckOrbitMutation = voiceSetter { duckCylinder = it?.asIntOrNull() ?: duckCylinder }

private fun applyDuckOrbit(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.duckCylinder?.toDouble() }, update = duckOrbitMutation)
    }

    return source._liftOrReinterpretNumericalField(args, duckOrbitMutation)
}

private val duckDepthMutation = voiceSetter { duckDepth = it?.asDoubleOrNull() }

private fun applyDuckDepth(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.duckDepth }, update = duckDepthMutation)
    }

    return source._liftOrReinterpretNumericalField(args, duckDepthMutation)
}

private val duckAttackMutation = voiceSetter { duckAttack = it?.asDoubleOrNull() }

private fun applyDuckAttack(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.duckAttack }, update = duckAttackMutation)
    }

    return source._liftOrReinterpretNumericalField(args, duckAttackMutation)
}

/**
 * Sidechain ducking: the orbit that triggers it, the depth and the recovery time.
 *
 * The pattern carrying `duck(...)` is ducked whenever the named orbit plays; the duck-down is instant, `attack` is the recovery.
 *
 * Every slot is independent and patternable; an omitted slot keeps its value, a named slot takes
 * a mapper (`duck(depth = mul(2))`), and the numeric slots read back as `duck.orbit`, `duck.depth`, `duck.attack`.
 * With no argument at all, the pattern's own values are reinterpreted as `orbit`.
 *
 * ```KlangScript(Playable)
 * stack(s("bd*4").orbit(1), note("c2*8").s("saw").duck(1, 0.8, 0.2))          // the bass ducks under the kick
 * ```
 *
 * ```KlangScript(Playable)
 * stack(s("bd*4").orbit(1), note("c2*8").s("saw").duck(1, 0.8).duck(depth = mul("<1 0.5>")))   // half as deep every other bar
 * ```
 *
 * ```KlangScript(Playable)
 * stack(s("bd*4").orbit(1), note("c2*8").s("saw").duck(1, "0.3 0.9").gain(duck.depth))   // deeper duck, louder bass
 * ```
 *
 * @param orbit Orbit index whose voices trigger the duck.
 * @param depth Depth, 0 (no ducking) to 1 (full silence).
 * @param attack Recovery time in seconds after the trigger stops.

 *
 * @category dynamics
 * @tags duck, orbit, depth, attack
 */
@KlangScript.Function
fun SprudelPattern.duck(orbit: PatternLike? = null, depth: PatternLike? = null, attack: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern {
    // A tail-only call must not touch orbit: reinterpret runs only on a fully bare call.
    var p = if (orbit != null || !(depth != null || attack != null)) {
        applyDuckOrbit(this, listOfNotNull(orbit).asSprudelDslArgs(callInfo))
    } else {
        this
    }
    if (depth != null) p = applyDuckDepth(p, listOf<Any?>(depth).asSprudelDslArgs(callInfo?.forParam(1)))
    if (attack != null) p = applyDuckAttack(p, listOf<Any?>(attack).asSprudelDslArgs(callInfo?.forParam(2)))
    return p
}

/** Parses this string as a pattern, then applies [duck]. */
@KlangScript.Function
fun String.duck(orbit: PatternLike? = null, depth: PatternLike? = null, attack: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).duck(orbit, depth, attack, callInfo)

/** Chains a [duck] step onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.duck(orbit: PatternLike? = null, depth: PatternLike? = null, attack: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.duck(orbit, depth, attack, callInfo) }

/**
 * The `duck` object: `duck(...)` sets the slots, and each numeric slot reads back as a child,
 * `duck.orbit`, `duck.depth`, `duck.attack`.
 *
 * @category dynamics
 * @tags duck, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("duck")
object duck {

    /** The orbit slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val orbit: FieldAccessor = FieldAccessor { it.duckCylinder?.toDouble() }

    /** The depth slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val depth: FieldAccessor = FieldAccessor { it.duckDepth }

    /** The attack slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val attack: FieldAccessor = FieldAccessor { it.duckAttack }

    /** The setter, see [SprudelPattern.duck]. */
    @KlangScript.Invoke
    operator fun invoke(orbit: PatternLike? = null, depth: PatternLike? = null, attack: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        { p -> p.duck(orbit, depth, attack, callInfo) }
}

// -- cylinder() — alias for orbit() ----------------------------------------------------------------------------------

/**
 * Routes events to a specific audio output cylinder (alias for [orbit]).
 *
 * In the Klangmotor, a Cylinder is an independent parallel audio channel
 * (called "orbit" in Strudel/Tidal Cycles). Each cylinder has its own effect pipeline (Katalyst).
 *
 * ```KlangScript(Playable)
 * note("c3 e3").cylinder(2).room(wet = 0.8, size = 4)  // melodic line on cylinder 2 with reverb
 * ```
 *
 * @param index The cylinder index to route events to.
 *
 * @category dynamics
 * @tags cylinder, orbit, routing, effects, bus, channel
 */
@KlangScript.Function
fun SprudelPattern.cylinder(index: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.orbit(index, callInfo)

/**
 * Parses this string as a pattern and routes it to the given audio output cylinder (alias for [orbit]).
 *
 * ```KlangScript(Playable)
 * "bd sd".cylinder(1).s()   // send drums to cylinder 1
 * ```
 *
 * @param index The cylinder index to route events to.
 *
 * @category dynamics
 * @tags cylinder, orbit, routing, effects, bus, channel
 */
@KlangScript.Function
fun String.cylinder(index: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).orbit(index, callInfo)

/**
 * Creates a [PatternMapperFn] that routes events to the given audio output cylinder (alias for [orbit]).
 *
 * ```KlangScript(Playable)
 * s("bd sd").apply(cylinder(1))   // send drums to cylinder 1
 * ```
 *
 * @param index The cylinder index to route events to.
 *
 * @category dynamics
 * @tags cylinder, orbit, routing, effects, bus, channel
 */
@KlangScript.Function
fun cylinder(index: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.orbit(index, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that routes events to the given cylinder after the previous mapper
 * (alias for [orbit]).
 *
 * ```KlangScript(Playable)
 * s("bd sd").apply(gain(0.8).cylinder(1))  // gain + cylinder chained
 * ```
 *
 * @param index The cylinder index to route events to.
 *
 * @category dynamics
 * @tags cylinder, orbit, routing, effects, bus, channel
 */
@KlangScript.Function
fun PatternMapperFn.cylinder(index: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.orbit(index, callInfo) }
