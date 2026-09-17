/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.annotations.KlangScriptLibraries

/**
 * `Katalyst` for KlangScript: builds [KatalystDsl] orbit chains.
 *
 * A Katalyst is the instrument an orbit plays through: the effects its summed voices run, in the
 * order written, before the playback's master bus. Author one by CALLING `Katalyst` with a
 * configure lambda and hand it to the `katalyst(...)` pattern door; the lambda receives a
 * [KatalystBuilder] whose knobs append stages in written order:
 *
 * ```
 * let guitarBus = Katalyst(k => k
 *   .eq(e => e.band(freq = 300, q = 0.8, db = 2.0))
 *   .reverb(r => r.wet(0.15).size(3))
 *   .compressor(c => c.threshold(-21).ratio(3))
 * )
 * stack(guitar1, guitar2, guitar3).katalyst(guitarBus)
 * ```
 *
 * `Katalyst()` with no lambda is the empty chain; `Katalyst(k => ...)` is the same as [build]. The
 * method forms exist so the callable form can be tested against them.
 *
 * Sibling of `Master` (per playback), `Pipeline` (per-voice signal path) and `Osc` (per-voice
 * exciter): same shape, different host.
 *
 * **In this step a chain is declared and registered, nothing more.** The backend starts applying
 * declared chains from step 2 of the Katalyst work; until then an orbit keeps running its fixed
 * historical chain, so writing one changes what the song SAYS, not yet what it sounds like.
 */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.Object("Katalyst")
object KlangScriptKatalyst {
    override fun toString(): String = "[Katalyst object]"

    /**
     * Builds an orbit chain: the lambda receives a [KatalystBuilder] and appends stages in order
     * (`body`, `vowel`, `delay`, `reverb`, `phaser`, `compressor`, `duck`, `eq`, `gain`, and
     * `classic` for all seven historical ones at once). No lambda, or an empty one, is the empty
     * chain.
     *
     * ```
     * katalyst(Katalyst.build(k => k.reverb(r => r.wet(0.2)).gain(1.4)))
     * ```
     *
     * @param configure receives the [KatalystBuilder] and returns it.
     */
    @KlangScript.Method
    fun build(configure: ((KatalystBuilder) -> KatalystBuilder)? = null): KatalystDsl =
        KatalystBuilder(KatalystDsl(emptyList())).configuredBy("Katalyst", configure).node

    /**
     * The historical chain every orbit has always run: body, vowel, delay, reverb, phaser,
     * compressor and the duck, seven stages in that order, with every knob a named slot that the
     * pattern doors will write once the cylinder reads the chain, from step 2 on.
     *
     * Write it to say "the familiar orbit", or use it as the base of a chain that adds to it. Until
     * step 2 an orbit runs that chain anyway, so today this declares it rather than restores it:
     *
     * ```
     * katalyst(Katalyst.classic())
     * katalyst(Katalyst(k => k.classic().eq(e => e.band(freq = 160, q = 1.0, db = -3.0))))
     * katalyst(Katalyst(k => k.classic().duck(d => d.orbit(1).depth(0.8))))
     * ```
     *
     * Its duck names no source, so nothing ducks until one is written. The third line above is how:
     * a cylinder runs exactly one ducking effect, so the LAST duck in the chain wins and appending
     * one replaces the unset duck `classic` brought.
     */
    @KlangScript.Method
    fun classic(): KatalystDsl = KatalystDsl.classic

    /**
     * `Katalyst(k => ...)`: the callable form of [build]. `Katalyst()` is the empty chain.
     *
     * ```
     * katalyst(Katalyst(k => k.reverb(r => r.wet(0.2).size(4)).compressor(c => c.threshold(-18))))
     * ```
     */
    @KlangScript.Invoke
    operator fun invoke(configure: ((KatalystBuilder) -> KatalystBuilder)? = null): KatalystDsl = build(configure)
}
