/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("DuplicatedCode", "ObjectPropertyName")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystValue
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.pattern.AtomicPattern
import io.peekandpoke.klang.sprudel.pattern.ReinterpretPattern.Companion.reinterpretVoice

// -- master() ---------------------------------------------------------------------------------------------------------

/**
 * Stamps a master reference onto every event of [source], **replacing** the one they carry.
 *
 * The reference rides the event stream: the backend swaps this playback's output chain when it
 * consumes the event, at its start time. Mirrors the inline-instrument path of `sound(...)`.
 *
 * The master is a [KatalystDsl], the same chain type an orbit runs (phase 3 step 12): one DSL, two
 * positions. [value] is ONE [KatalystValue.Dsl] per door, allocated when the door is written and
 * handed to every event, like `katalyst(...)`, so stamping costs nothing per event and the chain's
 * name is hashed once.
 */
private fun applyMaster(source: SprudelPattern, value: KatalystValue.Dsl): SprudelPattern =
    source.reinterpretVoice { vd -> vd.copy(master = value) }

/**
 * Creates a pattern that sets the **master chain** for the whole playback — one silent control event
 * per cycle that carries nothing but the master reference.
 *
 * The master is the last stage of the signal path: everything this playback plays (all orbits, after
 * their Katalyst effects) runs through it before being mixed with other playbacks. Use it to bring a
 * deliberately quiet mix back up and to hold the ceiling:
 *
 * ```KlangScript(Playable)
 * stack(
 *   note("c2 g2").s("supersaw"),
 *   s("bd*4"),
 *   master(Katalyst(k => k.gain(2.5).limiter())),
 * )
 * ```
 *
 * The chain is a `Katalyst`, the same chain an orbit runs, so every stage the builder offers works
 * here. Two differences follow from the position: nothing fills a `Katalyst.param(...)` slot at the
 * output (it stays at its default, `katp` reaches orbits only), and a `duck` stage is inert (there is
 * no other orbit to listen to). `master(Katalyst())` switches the master back off.
 *
 * The event is a **control event**: it never sounds. Because a master reference can ride any event,
 * `note("c3").master(…)` swaps the master at that note's onset *and* still plays the note — which is
 * how you automate a master over musical time (see [SprudelPattern.master]).
 *
 * @param master The master chain to apply.
 * @return A pattern emitting one control event per cycle.
 *
 * @scope master
 * @category effects
 * @tags master, loudness, gain, limiter, bus, motor
 */
@KlangScript.Function
fun master(master: KatalystDsl, @Suppress("unused") callInfo: CallInfo? = null): SprudelPattern {
    val value = KatalystValue.Dsl(master)

    return AtomicPattern.pure.reinterpretVoice { vd ->
        vd.copy(master = value, control = true)
    }
}

/**
 * Sets the master chain from this pattern's events onward.
 *
 * Unlike the top-level [master] carrier, these events still sound — the master swap simply rides
 * them. That makes the master patternable: put it on a slow rest-like pattern to stage a change,
 * or on real notes to align it with the music.
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").s("supersaw").master(Katalyst(k => k.gain(1.8)))
 * ```
 *
 * @param master The master chain to apply.
 * @return A new pattern whose events carry the master reference.
 *
 * @scope master
 * @category effects
 * @tags master, loudness, gain, limiter, bus, motor
 */
@KlangScript.Function
fun SprudelPattern.master(master: KatalystDsl, @Suppress("unused") callInfo: CallInfo? = null): SprudelPattern =
    applyMaster(this, KatalystValue.Dsl(master))

/**
 * Parses this string as a pattern and sets the master chain from its events onward.
 *
 * ```KlangScript(Playable)
 * "c3 e3 g3".master(Katalyst(k => k.gain(1.8))).s("supersaw")
 * ```
 *
 * @param master The master chain to apply.
 * @return A new pattern whose events carry the master reference.
 */
@KlangScript.Function
fun String.master(master: KatalystDsl, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).master(master, callInfo)

// NOTE — no bare `master(dsl): PatternMapperFn` factory (the usual form (c) of a sprudel op).
// The top-level `master(dsl)` name is taken by the *carrier* above, which is the headline surface
// (`stack(lead, bass, master(...))`), and a second overload with the same parameters could only
// differ by return type. Use `.master(dsl)` on the pattern instead — the mapper-chaining form (d)
// below still exists for `.apply(gain(0.5).master(...))`.

/**
 * Creates a chained [PatternMapperFn] that sets the master chain after the previous mapper.
 *
 * @param master The master chain to apply.
 */
@KlangScript.Function
fun PatternMapperFn.master(master: KatalystDsl, callInfo: CallInfo? = null): PatternMapperFn {
    // Built ONCE, outside the lambda: a mapper is invoked per pattern it is applied to, and one
    // value per door is the budget, not one per application.
    val value = KatalystValue.Dsl(master)

    return this.chain { p -> applyMaster(p, value) }
}
