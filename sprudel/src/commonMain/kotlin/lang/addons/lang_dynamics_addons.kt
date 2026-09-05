/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang.addons

// ADDONS: functions that are NOT available in the original strudel impl

import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelVoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.sprudel._liftData
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.lang.toPattern
import io.peekandpoke.klang.sprudel.lang.voiceSetter
import io.peekandpoke.klang.sprudel.lang.PatternLike
import io.peekandpoke.klang.sprudel.lang.PatternMapperFn
import io.peekandpoke.klang.sprudel.lang.chain
import io.peekandpoke.klang.sprudel.lang.orbit
import io.peekandpoke.klang.sprudel.lang.toVoiceValuePattern
// -- cylinder() — alias for orbit() ----------------------------------------------------------------------------------

/**
 * Routes events to a specific audio output cylinder (alias for [orbit]).
 *
 * In the Klangmotor, a Cylinder is an independent parallel audio channel
 * (called "orbit" in Strudel/Tidal Cycles). Each cylinder has its own effect pipeline (Katalyst).
 *
 * ```KlangScript(Playable)
 * note("c3 e3").cylinder(2).roomWet(0.8).roomsize(4)  // melodic line on cylinder 2 with reverb
 * ```
 *
 * @param index The cylinder index to route events to.
 *
 * @category dynamics
 * @tags cylinder, orbit, routing, effects, bus, channel, addon
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
 * @tags cylinder, orbit, routing, effects, bus, channel, addon
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
 * @tags cylinder, orbit, routing, effects, bus, channel, addon
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
 * @tags cylinder, orbit, routing, effects, bus, channel, addon
 */
@KlangScript.Function
fun PatternMapperFn.cylinder(index: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.orbit(index, callInfo) }

// -- ADSR adsrOn() / adsrOff() ----------------------------------------------------------------------------------------

private val adsrOnMutation = voiceSetter { adsrOn = it?.asVoiceValue()?.asBoolean }

private fun applyAdsrOn(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val effectiveArgs = args.ifEmpty { listOf(SprudelDslArg.of(1.0)) }
    val control = effectiveArgs.toPattern(adsrOnMutation)
    return source._liftData(control)
}

/**
 * Switches the voice's own amplitude envelope (the VCA) ON or OFF.
 *
 * Use [adsrOff] when the instrument already carries its own envelope: an ignitor built with
 * `.adsr(...)` shapes amplitude itself, and without this the voice envelope applies on top, so the
 * two multiply and every curve comes out twice as steep in dB.
 *
 * Switching it off does NOT throw the numbers away — `.adsr(0.005, 1.0, 1.0, 0.05).adsrOff()` keeps
 * them, so you can flip back with [adsrOn] and compare. Note lifetime is unaffected either way: the
 * engine covers an ignitor's release tail — UNLESS the ignitor's release time is itself modulated,
 * which has no single static value, in which case the voice's own `release` still governs and is
 * worth setting even with the envelope off.
 *
 * When unset, the engine's `Vca` stage decides (the built-in engines leave it on).
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").s("supersaw").adsrOn()   // shape the note here, whatever the engine defaults to
 * ```
 *
 * The built-in sounds carry no envelope of their own, so they WANT the voice envelope — `adsrOff`
 * is for instruments you build with their own `.adsr(...)`.
 *
 * @param flag Truthy = the voice envelope shapes the note. Defaults to `true`.
 * @return A pattern with the VCA switched on.
 *
 * @category dynamics
 * @tags adsr, envelope, vca, gate, addon
 */
@KlangScript.Function
fun SprudelPattern.adsrOn(flag: PatternLike = true, callInfo: CallInfo? = null): SprudelPattern =
    applyAdsrOn(this, listOf(flag).asSprudelDslArgs(callInfo))

/** Parses this string as a pattern and switches the voice's amplitude envelope on (see [adsrOn]). */
@KlangScript.Function
fun String.adsrOn(flag: PatternLike = true, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).adsrOn(flag, callInfo)

/** Creates a [PatternMapperFn] that switches the voice's amplitude envelope on (see [adsrOn]). */
@KlangScript.Function
fun adsrOn(flag: PatternLike = true, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.adsrOn(flag, callInfo) }

/** Chains onto an existing mapper, switching the voice's amplitude envelope on (see [adsrOn]). */
@KlangScript.Function
fun PatternMapperFn.adsrOn(flag: PatternLike = true, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.adsrOn(flag, callInfo) }

/**
 * Switches the voice's own amplitude envelope (the VCA) OFF, so the instrument shapes its own
 * amplitude. The counterpart of [adsrOn]; see there for the full story.
 *
 * With the voice envelope off, the voice's `release` window becomes a full-level HOLD rather than
 * a decay, so the instrument really does have to shape its own tail. And with a release under about
 * 4 ms the engine's teardown guard, not the instrument, owns the note-off.
 *
 * ```KlangScript(Playable)
 * let pluck = Osc.saw().lowpass(2500).adsr(0.005, 0.35, 0.0, 0.08)
 * note("c3 e3 g3 c4").sound(pluck).adsrOff().gain(0.4)
 * ```
 *
 * @return A pattern with the VCA switched off.
 *
 * @category dynamics
 * @tags adsr, envelope, vca, gate, addon
 */
@KlangScript.Function
fun SprudelPattern.adsrOff(callInfo: CallInfo? = null): SprudelPattern = this.adsrOn(false, callInfo)

/** Parses this string as a pattern and switches the voice's amplitude envelope off (see [adsrOff]). */
@KlangScript.Function
fun String.adsrOff(callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).adsrOff(callInfo)

/** Creates a [PatternMapperFn] that switches the voice's amplitude envelope off (see [adsrOff]). */
@KlangScript.Function
fun adsrOff(callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.adsrOff(callInfo) }

/** Chains onto an existing mapper, switching the voice's amplitude envelope off (see [adsrOff]). */
@KlangScript.Function
fun PatternMapperFn.adsrOff(callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.adsrOff(callInfo) }
