/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("ClassName")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.audio_bridge.constants.VOICE_CULL_NEVER
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel._liftOrReinterpretNumericalField
import io.peekandpoke.klang.sprudel._mapNumericField
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs

// -- cull() -----------------------------------------------------------------------------------------------------------

private val cullMutation = voiceSetter { cull = it?.asDoubleOrNull() }

private fun applyCull(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.cull }, update = cullMutation)
    }

    return source._liftOrReinterpretNumericalField(args, cullMutation)
}

/**
 * Sets the silence-culling window of each voice, in seconds.
 *
 * A voice normally renders until its scheduled release tail has run out, even after it has
 * decayed to silence. With culling, once a voice is in its release and its output has stayed
 * inaudible (under -100 dBFS) for [seconds], it ends there instead. The held part of the note
 * is never culled, and neither is a voice that has not sounded yet, so a slow attack, a silent
 * lead-in or a sample with leading silence is safe; reverb and delay tails live on
 * the orbit and keep ringing. A RELEASE that goes silent and comes back is cut at its first
 * gap: a voice with a `tremolo` is therefore never culled unless you set this door, and a sparse
 * source inside an ignitor (`Osc.dust`, `Osc.crackle`) ringing through its release wants a wider
 * window or [noCull].
 *
 * Every voice is culled with a 50 ms window by default; this door changes the window, [noCull]
 * turns culling off for a voice. Accepts control patterns.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").sound("supersaw").adsr(0.01, 0.2, 0, 2).cull(0.2)   // a wider window before a tail is cut
 * ```
 *
 * ```KlangScript(Playable)
 * s("hh*8").adsr(0.001, 0.1, 0.7, 2.0).cull(0.02)                    // short samples, long release: cull fast
 * ```
 *
 * @param seconds The window in seconds; the release must stay silent this long before the voice ends.
 * @return A pattern with the cull window assigned.
 *
 * @scope voice
 * @category dynamics
 * @tags cull, voice, silence, release, cpu
 */
@KlangScript.Function
fun SprudelPattern.cull(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyCull(this, listOfNotNull(seconds).asSprudelDslArgs(callInfo))

/** Parses this string as a pattern and sets its silence-culling window (see [cull]). */
@KlangScript.Function
fun String.cull(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).cull(seconds, callInfo)

/**
 * The silence-culling window of each event, in seconds, as a value other setters can read.
 *
 * Bare `cull` reads what the chain has set so far, so it comes after whatever set the field.
 * Call it, `cull(...)`, to set the field; a mapper argument applies to the field.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").cull(0.05).cull(mul("1 4"))          // the second note gets a four times wider window
 * ```
 *
 * @category dynamics
 * @tags cull, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("cull")
object cull : FieldAccessor({ it.cull }) {

    /**
     * Returns a [PatternMapperFn] that sets the silence-culling window.
     *
     * ```KlangScript(Playable)
     * note("c3 e3").apply(cull(0.2))                    // set the window via mapper
     * ```
     */
    @KlangScript.Invoke
    operator fun invoke(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        { p -> p.cull(seconds, callInfo) }
}

/** Chains a cull window onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.cull(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.cull(seconds, callInfo) }

// -- noCull() ---------------------------------------------------------------------------------------------------------

/**
 * Turns silence culling OFF for each voice: the voice renders its whole scheduled release tail,
 * silent or not, the way every voice did before culling existed.
 *
 * The one reason to reach for it is a sound whose release goes silent and then comes back (a
 * sparse `Osc.dust` still ringing, a gate inside the ignitor), which the 50 ms window would cut.
 * `cull(seconds)` widens the window instead of removing it. A voice with a `tremolo` gets this
 * automatically.
 *
 * ```KlangScript(Playable)
 * note("c2").sound("supersaw").adsr(0.01, 0.1, 1, 3).noCull()   // the full three-second tail, always
 * ```
 *
 * @scope voice
 * @category dynamics
 * @tags cull, voice, release
 */
@KlangScript.Function
fun SprudelPattern.noCull(callInfo: CallInfo? = null): SprudelPattern = this.cull(VOICE_CULL_NEVER, callInfo)

/** Parses this string as a pattern and turns silence culling off for it (see [noCull]). */
@KlangScript.Function
fun String.noCull(callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).noCull(callInfo)

/** Chains `noCull` onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.noCull(callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.noCull(callInfo) }
