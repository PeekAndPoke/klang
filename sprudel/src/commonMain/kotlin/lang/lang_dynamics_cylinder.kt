/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions", "ClassName")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.audio_bridge.constants.DUCK_ATTACK_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.DUCK_DEPTH
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.ParamBag
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelVoiceData
import io.peekandpoke.klang.sprudel._liftOrReinterpretNumericalField
import io.peekandpoke.klang.sprudel._liftOrReinterpretStringField
import io.peekandpoke.klang.sprudel._mapNumericField
import io.peekandpoke.klang.sprudel.katalystParamsOrNew
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.putKatalystParam

// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Routing
// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

// -- cylinder() / orbit() / o() --------------------------------------------------------------------------------------

private val cylinderMutation = voiceSetter {
    cylinder = it?.asIntOrNull()
}

private fun applyCylinder(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.cylinder?.toDouble() }, update = cylinderMutation)
    }

    return source._liftOrReinterpretStringField(args, cylinderMutation)
}

/**
 * Routes the pattern to a cylinder, its own channel of shared effects.
 *
 * This is the answer whenever two patterns fight over a bus effect. Reverb, delay, phaser, compressor, body, vowel
 * and ducking exist once per cylinder and take their settings from the newest note held on it, so a second pattern
 * asking for a different reverb size is overruled. Move it to another cylinder and it gets its own.
 *
 * "Cylinder" is the Klangmotor's word for it (KLANG-00418, decision D1); `orbit` and `o`, the words of sprudel's
 * Strudel origin, are its aliases.
 *
 * ```KlangScript(Playable)
 * s("bd sd").cylinder(1)                           // send drums to cylinder 1
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3").cylinder(2).reverb(wet = 0.8, size = 4)  // melodic line on cylinder 2 with reverb
 * ```
 *
 * @param index The cylinder index to route events to.
 *
 * @alias orbit, o
 * @category dynamics
 * @tags cylinder, orbit, o, routing, effects, bus, channel
 */
@KlangScript.Function
fun SprudelPattern.cylinder(index: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyCylinder(this, listOfNotNull(index).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and routes it to the given cylinder.
 *
 * ```KlangScript(Playable)
 * "bd sd".cylinder(1).s()   // send drums to cylinder 1
 * ```
 *
 * @param index The cylinder index to route events to.
 */
@KlangScript.Function
fun String.cylinder(index: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).cylinder(index, callInfo)

/**
 * The cylinder (effect bus) of each event, as a value other setters can read.
 *
 * Bare `cylinder` reads what the chain has set so far, so it comes after whatever set the field
 * (`cylinder(...)` or an alias). Call it, `cylinder(...)`, to set the field; a mapper argument applies
 * to the field.
 *
 * Aliases: `orbit`, `o`.
 *
 * ```KlangScript(Playable)
 * s("bd sd").cylinder(0).cylinder(add("0 1"))                       // the snare on cylinder 1
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd").cylinder("0 1").reverb(cylinder.mul(0.3), 4)         // more reverb on the higher cylinder
 * ```
 *
 * @category dynamics
 * @tags cylinder, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("cylinder")
object cylinder : FieldAccessor({ it.cylinder?.toDouble() }) {

    /**
     * Creates a [PatternMapperFn] that routes events to the given cylinder.
     *
     * ```KlangScript(Playable)
     * s("bd sd").apply(cylinder(1))   // send drums to cylinder 1
     * ```
     *
     * @param index The cylinder index to route events to.
     */
    @KlangScript.Invoke
    operator fun invoke(index: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        { p -> p.cylinder(index, callInfo) }
}

/**
 * Creates a chained [PatternMapperFn] that routes events to the given cylinder after the previous mapper.
 *
 * ```KlangScript(Playable)
 * s("bd sd").apply(gain(0.8).cylinder(1))  // gain + cylinder chained
 * ```
 *
 * @param index The cylinder index to route events to.
 */
@KlangScript.Function
fun PatternMapperFn.cylinder(index: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.cylinder(index, callInfo) }

/**
 * Alias for [cylinder], the word of sprudel's Strudel origin. Routes the pattern to a cylinder, its own channel of
 * shared effects.
 *
 * ```KlangScript(Playable)
 * s("bd sd").orbit(1)                       // send drums to cylinder 1
 * ```
 *
 * @param index The cylinder index to route events to.
 *
 * @alias cylinder, o
 * @category dynamics
 * @tags orbit, cylinder, routing, effects, bus, channel
 */
@KlangScript.Function
fun SprudelPattern.orbit(index: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.cylinder(index, callInfo)

/**
 * Alias for [cylinder]. Parses this string as a pattern and routes it to the given cylinder.
 *
 * ```KlangScript(Playable)
 * "bd sd".orbit(1).s()   // send drums to cylinder 1
 * ```
 *
 * @param index The cylinder index to route events to.
 */
@KlangScript.Function
fun String.orbit(index: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).cylinder(index, callInfo)

/**
 * Alias for [cylinder]: a chained [PatternMapperFn] that routes events to the given cylinder after the previous mapper.
 *
 * ```KlangScript(Playable)
 * s("bd sd").apply(gain(0.8).orbit(1))  // gain + cylinder chained
 * ```
 *
 * @param index The cylinder index to route events to.
 */
@KlangScript.Function
fun PatternMapperFn.orbit(index: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.cylinder(index, callInfo) }

/**
 * Alias of [cylinder]: the same accessor under the word of sprudel's Strudel origin.
 *
 * @category dynamics
 * @tags orbit, cylinder, accessor
 */
@KlangScript.Constant
val orbit: cylinder = cylinder

/**
 * Alias for [cylinder]. Routes the pattern to a cylinder for independent effect processing.
 *
 * ```KlangScript(Playable)
 * s("bd sd").o(1)                       // send drums to cylinder 1
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3").o(2).reverb(0.8, 4)     // melodic line on cylinder 2 with reverb
 * ```
 *
 * @param index The cylinder index to route events to.
 *
 * @alias cylinder, orbit
 * @category dynamics
 * @tags o, cylinder, orbit, routing, effects, bus, channel
 */
@KlangScript.Function
fun SprudelPattern.o(index: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.cylinder(index, callInfo)

/**
 * Alias for [cylinder]. Parses this string as a pattern and routes it to the given cylinder.
 *
 * ```KlangScript(Playable)
 * "bd sd".o(1).s()   // send drums to cylinder 1
 * ```
 *
 * @param index The cylinder index to route events to.
 */
@KlangScript.Function
fun String.o(index: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).cylinder(index, callInfo)

/**
 * Alias of [cylinder]: the same accessor under another name.
 *
 * @category dynamics
 * @tags o, cylinder, accessor
 */
@KlangScript.Constant
val o: cylinder = cylinder

// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Ducking / Sidechain
// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

// -- duck ------------------------------------------------------------------------------------------------------------

/**
 * Fills the duck stage's companions, `duck.depth` and `duck.attack`, from
 * `constants/BusEffectDefaults.kt` (`/dsl-design` §4 is the rule; this is only what THIS door
 * does). Called from the CYLINDER setter alone, and only when that call named a cylinder: the cylinder is
 * this stage's name knob, so a tail-only `duck(depth = 0.5)` or `duck(attack = 0.3)` writes its own
 * slot and nothing else, and a bare `duck()` that names nothing fills nothing.
 *
 * The filled depth is [DUCK_DEPTH], which is 0, so `duck(1)` alone names a source and still ducks
 * nothing: it waits for a depth, the way the phaser waits for a wet. 0 is also what
 * `KatalystDsl.classic` carries for that slot, so filling it changes nothing on the historical
 * chain either.
 *
 * The slots are the duck's only storage since Katalyst step 5b-3 (the voice fields left the wire
 * and `SprudelVoiceData`), and the `duck.*` accessors read them: `duck.attack` reads
 * [DUCK_ATTACK_SECONDS] after `duck(1)`, the value the cylinder runs. Until 5b-3 the accessors read
 * the fields, which this fill never wrote, so the same read came back empty.
 *
 * Two different things keep a fill off a value it must not touch, and they are worth telling apart.
 * The `value = null` plus the absence test is what lets a `katp("duck.depth", 0.7)` from an earlier
 * call survive: that value IS in the event's bag, so the fill leaves it. A CHAIN-authored depth is
 * not in the bag at all, it is the chain's own `Param` default, so nothing in [ParamBag] could
 * protect it; what protects it is calling this fill from the CYLINDER setter alone, so a tail-only
 * `duck(attack = 0.3)` never writes `duck.depth` and the chain's 0.8 is never asked about. Until
 * round 3 of step 5a-3 every duck knob filled, and that call silenced the ducking on a declared
 * chain.
 */
private fun SprudelVoiceData.fillDuckDefaults() {
    val slots = katalystParamsOrNew()

    slots.setOrDefault("duck.depth", value = null, default = DUCK_DEPTH)
    slots.setOrDefault("duck.attack", value = null, default = DUCK_ATTACK_SECONDS)
}

// The cylinder is the NAME KNOB. It is also the one duck setter a bare call can reach with a null (the
// bare-call reinterpret of `_liftOrReinterpretNumericalField`); naming nothing must write nothing,
// slot included, or `duck(2).katp("duck.cylinder", 5).duck()` would stamp the stale field back over
// the 5 (checklist 12).
private val duckCylinderMutation = voiceSetter {
    val named = it?.asIntOrNull()

    if (named != null) {
        katalystParamsOrNew().set("duck.cylinder", named.toDouble())
        fillDuckDefaults()
    }
}

private fun applyDuckCylinder(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.katalystParams?.get("duck.cylinder") }, update = duckCylinderMutation)
    }

    return source._liftOrReinterpretNumericalField(args, duckCylinderMutation)
}

// The two tail setters write their own slot and nothing else. Not filling here is the whole of the
// round 3 fix: a fill from a tail call would write `duck.depth` with DUCK_DEPTH, and on a chain that
// authored its own depth as a `Param` default that 0 is what the stage then resolves to
// (`/dsl-design` §4, the name-knob half).
private val duckDepthMutation = voiceSetter {
    putKatalystParam("duck.depth", it?.asDoubleOrNull())
}

private fun applyDuckDepth(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.katalystParams?.get("duck.depth") }, update = duckDepthMutation)
    }

    return source._liftOrReinterpretNumericalField(args, duckDepthMutation)
}

private val duckAttackMutation = voiceSetter {
    putKatalystParam("duck.attack", it?.asDoubleOrNull())
}

private fun applyDuckAttack(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.katalystParams?.get("duck.attack") }, update = duckAttackMutation)
    }

    return source._liftOrReinterpretNumericalField(args, duckAttackMutation)
}

/**
 * Sidechain ducking: the cylinder that triggers it, the depth and the recovery time.
 *
 * The pattern carrying `duck(...)` is ducked whenever the named cylinder plays. The duck-down is
 * instant, `attack` is the recovery.
 *
 * Ducking belongs to the cylinder's bus and runs after every cylinder has been
 * processed, so it ducks the whole cylinder, set by its owning voice, not one note at a time.
 *
 * Every slot is independent and patternable; an omitted slot keeps its value, a named slot takes
 * a mapper (`duck(depth = mul(2))`), and the numeric slots read back as `duck.cylinder`, `duck.depth`, `duck.attack`.
 * With no argument at all, the pattern's own values are reinterpreted as `cylinder`.
 *
 * ```KlangScript(Playable)
 * stack(s("bd*4").cylinder(1), note("c2*8").s("saw").duck(1, 0.8, 0.2))          // the bass ducks under the kick
 * ```
 *
 * ```KlangScript(Playable)
 * stack(s("bd*4").cylinder(1), note("c2*8").s("saw").duck(1, 0.8).duck(depth = mul("<1 0.5>")))   // half as deep every other bar
 * ```
 *
 * ```KlangScript(Playable)
 * stack(s("bd*4").cylinder(1), note("c2*8").s("saw").duck(1, "0.3 0.9").gain(duck.depth))   // deeper duck, louder bass
 * ```
 *
 * @param cylinder Cylinder index whose voices trigger the duck.
 * @param depth Depth, 0 for none, 1 for full silence.
 * @param attack Recovery time in seconds after the trigger stops.
 *
 * @scope orbit
 * @category dynamics
 * @tags duck, cylinder, orbit, depth, attack
 */
@KlangScript.Function
fun SprudelPattern.duck(cylinder: PatternLike? = null, depth: PatternLike? = null, attack: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern {
    // A tail-only call must not touch the cylinder: reinterpret runs only on a fully bare call.
    var p = if (cylinder != null || !(depth != null || attack != null)) {
        applyDuckCylinder(this, listOfNotNull(cylinder).asSprudelDslArgs(callInfo))
    } else {
        this
    }
    if (depth != null) p = applyDuckDepth(p, listOf<Any?>(depth).asSprudelDslArgs(callInfo?.forParam(1)))
    if (attack != null) p = applyDuckAttack(p, listOf<Any?>(attack).asSprudelDslArgs(callInfo?.forParam(2)))
    return p
}

/** Parses this string as a pattern, then applies [duck]. */
@KlangScript.Function
fun String.duck(cylinder: PatternLike? = null, depth: PatternLike? = null, attack: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).duck(cylinder, depth, attack, callInfo)

/** Chains a [duck] step onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.duck(cylinder: PatternLike? = null, depth: PatternLike? = null, attack: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.duck(cylinder, depth, attack, callInfo) }

/**
 * The `duck` object: `duck(...)` sets the slots, and each numeric slot reads back as a child,
 * `duck.cylinder`, `duck.depth`, `duck.attack`.
 *
 * @scope orbit
 * @category dynamics
 * @tags duck, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("duck")
object duck {

    /** The cylinder slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val cylinder: FieldAccessor = FieldAccessor { it.katalystParams?.get("duck.cylinder") }

    /** The depth slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val depth: FieldAccessor = FieldAccessor { it.katalystParams?.get("duck.depth") }

    /** The attack slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val attack: FieldAccessor = FieldAccessor { it.katalystParams?.get("duck.attack") }

    /**
     * The setter, see [SprudelPattern.duck].
     *
     * @param cylinder Cylinder index whose voices trigger the duck.
     * @param depth Depth, 0 for none, 1 for full silence.
     * @param attack Recovery time in seconds after the trigger stops.
     */
    @KlangScript.Invoke
    operator fun invoke(cylinder: PatternLike? = null, depth: PatternLike? = null, attack: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        { p -> p.duck(cylinder, depth, attack, callInfo) }
}
