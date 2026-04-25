@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang.addons

// ADDONS: functions that are NOT available in the original strudel impl

import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.lang.PatternLike
import io.peekandpoke.klang.sprudel.lang.PatternMapperFn
import io.peekandpoke.klang.sprudel.lang.SprudelDsl
import io.peekandpoke.klang.sprudel.lang.chain
import io.peekandpoke.klang.sprudel.lang.orbit
import io.peekandpoke.klang.sprudel.lang.toVoiceValuePattern

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in SprudelRegistry.
 */
var sprudelLangDynamicsAddonsInit = false

// -- cylinder() — alias for orbit() ----------------------------------------------------------------------------------

/**
 * Routes events to a specific audio output cylinder (alias for [orbit]).
 *
 * In the Klang Audio Motör, a Cylinder is an independent parallel audio channel
 * (called "orbit" in Strudel/Tidal Cycles). Each cylinder has its own effect pipeline (Katalyst).
 *
 * ```KlangScript(Playable)
 * note("c3 e3").cylinder(2).room(0.8).roomsize(4)  // melodic line on cylinder 2 with reverb
 * ```
 *
 * @param index The cylinder index to route events to.
 *
 * @category dynamics
 * @tags cylinder, orbit, routing, effects, bus, channel, addon
 */
@SprudelDsl
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
 * @tags cylinder, orbit, routing, effects, bus, channel, addon
 */
@SprudelDsl
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
 * @tags cylinder, orbit, routing, effects, bus, channel, addon
 */
@SprudelDsl
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
 * @tags cylinder, orbit, routing, effects, bus, channel, addon
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.cylinder(index: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.orbit(index, callInfo) }
