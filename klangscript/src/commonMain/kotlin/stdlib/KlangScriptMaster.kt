/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.peekandpoke.klang.audio_bridge.MasterDsl
import io.peekandpoke.klang.audio_bridge.MasterStageDsl
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.annotations.KlangScriptLibraries

/**
 * `Master` object for KlangScript — builds [MasterDsl] master-bus chains.
 *
 * The master is the last stage of a playback's signal path: everything it plays runs through the
 * chain before joining the mix. Author one with `Master.of(MasterFx.…)` and hand it to the
 * `master(…)` pattern:
 *
 * ```
 * let loud = Master.of(MasterFx.gain(2.5), MasterFx.limiter())
 * stack(
 *   note("c2 g2").s("supersaw"),
 *   master(loud),
 * )
 * ```
 *
 * Sibling of `Pipeline` (per-voice signal path) and `Osc` (per-voice exciter) — same shape, same
 * chaining, different host.
 */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.Object("Master")
object KlangScriptMaster {

    override fun toString(): String = "[Master object]"

    /**
     * Builds a master chain from an ordered list of stages.
     *
     * An empty chain is unity — exactly what a playback uses when no `master(…)` is present.
     *
     * @param stages the chain, in order (e.g. `MasterFx.gain(2.0), MasterFx.limiter()`)
     */
    @KlangScript.Method
    fun of(vararg stages: MasterStageDsl): MasterDsl = MasterDsl(stages.toList())

    /**
     * The unity master — the chain a playback runs with when no `master(…)` is present.
     *
     * Use it to switch a master back **off** while playing:
     *
     * ```
     * master(Master.default())
     * ```
     *
     * This matters because a master reference means "change to this", not "this is the master from
     * now on": *deleting* a `master(…)` line while live coding leaves the last chain in place, since
     * no event is emitted to say otherwise. `Master.default()` is the way to say it explicitly.
     */
    @KlangScript.Method
    fun default(): MasterDsl = MasterDsl.default
}

/**
 * `MasterFx` object for KlangScript — builds the [MasterStageDsl] stages of a [MasterDsl] chain.
 *
 * Every stage returns a *configurable* value: chain its tuning right after, before adding the next
 * stage — `MasterFx.limiter().thresholdDb(-0.5)`, `MasterFx.reverb().wet(0.4).roomSize(8)`.
 *
 * The counterpart of `Stage` (voice pipeline) for the master bus. Effects are the same DSP the
 * per-orbit Katalyst effects use — only the host differs, and **the parameters use the same names
 * and the same scales as their sprudel twins**, so a number means the same thing on either bus.
 */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.Object("MasterFx")
object KlangScriptMasterFx {

    override fun toString(): String = "[MasterFx object]"

    /**
     * Make-up gain on the master bus — the honest way to mix a song low and bring it back up.
     *
     * @param gain linear gain factor (1.0 = unity, 2.0 ≈ +6 dB).
     */
    @KlangScript.Method
    fun gain(gain: Double = 1.0): MasterStageDsl.Gain = MasterStageDsl.Gain(gain = gain)

    /**
     * Musical limiter on this playback's master bus.
     *
     * Threshold, ratio, knee and release match the always-on safety limiter that already runs on the
     * summed mix — but this one has **no lookahead by default**, so it shapes level rather than
     * anticipating transients, and adds no latency. The safety limiter is the actual brick wall.
     *
     * Chain to tune: `thresholdDb` / `ratio` / `kneeDb` / `attack` / `release` / `lookahead`.
     *
     * `attack` means two things: a one-pole time constant with no lookahead, the gain-smoothing
     * length with it. `lookahead` is opt-in because it costs exactly that much latency and stages
     * stack — three authored limiters with lookahead are three delay lines.
     */
    @KlangScript.Method
    fun limiter(): MasterStageDsl.Limiter = MasterStageDsl.Limiter()

    /**
     * Master reverb (shared Freeverb, used as an insert).
     *
     * Chain `wet` / `roomSize` (sprudel `roomsize` scale, ~0..10) / `damp` / `roomFade` / `roomLp`.
     */
    @KlangScript.Method
    fun reverb(): MasterStageDsl.Reverb = MasterStageDsl.Reverb()

    /** Master delay (shared delay line, used as an insert). Chain `wet` / `time` / `feedback` / `cap`. */
    @KlangScript.Method
    fun delay(): MasterStageDsl.Delay = MasterStageDsl.Delay()
}
