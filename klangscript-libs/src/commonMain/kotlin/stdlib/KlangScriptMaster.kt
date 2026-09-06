/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.peekandpoke.klang.audio_bridge.MasterDsl
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.annotations.KlangScriptLibraries

/**
 * `Master` for KlangScript: builds [MasterDsl] master-bus chains.
 *
 * The master is the last stage of a playback's signal path: everything it plays runs through the
 * chain before joining the mix. Author one by CALLING `Master` with a configure lambda and hand
 * it to the `master(...)` pattern; the lambda receives a [MasterBuilder] whose knobs append stages
 * in written order:
 *
 * ```
 * let loud = Master(m => m.gain(2.5).limiter())
 * stack(
 *   note("c2 g2").s("supersaw"),
 *   master(loud),
 * )
 * ```
 *
 * `Master()` with no lambda is the unity chain (the same as [default]); `Master(m => ...)` is the
 * same as [build]. The method forms exist so the callable form can be tested against them.
 *
 * Sibling of `Pipeline` (per-voice signal path) and `Osc` (per-voice exciter): same shape,
 * different host.
 */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.Object("Master")
object KlangScriptMaster {
    override fun toString(): String = "[Master object]"

    /**
     * Builds a master chain: the lambda receives a [MasterBuilder] and appends stages in order
     * (`gain`, `limiter`, `reverb`, `delay`). No lambda, or an empty one, is the unity chain.
     *
     * ```
     * master(Master.build(m => m.reverb(r => r.wet(0.05)).gain(2.5)))
     * ```
     *
     * @param configure receives the [MasterBuilder] and returns it.
     */
    @KlangScript.Method
    fun build(configure: ((MasterBuilder) -> MasterBuilder)? = null): MasterDsl =
        MasterBuilder(MasterDsl.default).configuredBy("Master", configure).node

    /**
     * The unity master, the chain a playback runs with when no `master(...)` is present.
     *
     * Use it to switch a master back OFF while playing:
     *
     * ```
     * master(Master.default())
     * ```
     *
     * This matters because a master reference means "change to this", not "this is the master from
     * now on": deleting a `master(...)` line while live coding leaves the last chain in place, since
     * no event is emitted to say otherwise. `Master.default()` (or `Master()`) says it explicitly.
     */
    @KlangScript.Method
    fun default(): MasterDsl = MasterDsl.default

    /**
     * `Master(m => ...)`: the callable form of [build]. `Master()` is [default].
     *
     * ```
     * master(Master(m => m.reverb(r => r.wet(0.05).roomSize(9)).gain(2.5).limiter(l => l.thresholdDb(-3))))
     * ```
     */
    @KlangScript.Method(name = "invoke")
    fun invoke(configure: ((MasterBuilder) -> MasterBuilder)? = null): MasterDsl = build(configure)
}
