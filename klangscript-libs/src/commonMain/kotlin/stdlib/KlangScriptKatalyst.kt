/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.peekandpoke.klang.audio_bridge.IgnitorDsl
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
 * **The chain is the instrument.** A stage the chain does not declare does not run, however loudly
 * a voice asks for it, so `Katalyst(k => k.eq(...))` is honestly "an EQ and nothing else". Start
 * from the familiar orbit with [classic], which brings the historical stages plus the group fader
 * at unity, as named slots the bus doors and `katp` can drive.
 */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.Object("Katalyst")
object KlangScriptKatalyst {
    override fun toString(): String = "[Katalyst object]"

    /**
     * Builds an orbit chain: the lambda receives a [KatalystBuilder] and appends stages in order
     * (`body`, `vowel`, `delay`, `reverb`, `phaser`, `compressor`, `duck`, `eq`, `gain`, and
     * `classic` for the whole familiar block at once, at most once per builder). No lambda, or an
     * empty one, is the empty chain.
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
     * The chain every orbit has always run: body, vowel, delay, reverb, phaser, compressor and the
     * duck, in that order, plus the group fader at unity (`gain.gain`) between the compressor and
     * the duck, which is bit-transparent and is there so a pattern can reach a group fader. Every
     * knob is a named slot the bus doors and `katp` write.
     *
     * Write it to say "the familiar orbit", or use it as the base of a chain that adds to it:
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
     *
     * **Writing it is not the same as writing nothing** (decided 2026-09-18). An orbit that declares
     * no chain runs the historical one from the voice's own effect fields; declaring THIS one names
     * the same stages as slots, so `katp` and the bus doors drive them, which is what makes
     * `Katalyst(k => k.classic())` the one line to add before automating a classic knob. The sound
     * is the same either way; what changes is who the knobs listen to. Its `body` material and its
     * `vowel` are slots as well, carrying the INDEX of a name in the shared catalogues, so a
     * pattern's `body("wood")` still reaches it.
     */
    @KlangScript.Method
    fun classic(): KatalystDsl = KatalystDsl.classic

    /**
     * Creates a named **chain slot** with a default value: the knob a pattern can then move with
     * `.katp("<name>", value)`.
     *
     * A slot is read per block from the voice that holds the orbit's lease, so it is orbit state and
     * not a per-note snapshot. When nothing has written the name, the knob is [default]. Write a
     * plain number instead of a slot where the chain should stay fixed.
     *
     * ```
     * let bus = Katalyst(k => k.reverb(r => r.wet(0.5).size(Katalyst.param("room", 5.0))))
     * note("c3 e3 g3").s("supersaw").katalyst(bus).katp("room", "<2 9>")
     * ```
     *
     * **A slot listens only when it IS the knob.** `Katalyst.param("room", 5).mul(2)` is an
     * expression over a slot, not a slot: the bus reads a knob that is neither a constant nor a slot
     * ONCE, when the chain is built, and folds it to a number, so `katp("room", x)` never reaches
     * it. Hand the knob the slot itself and do the arithmetic on the pattern side.
     *
     * The twin of `Osc.param` on the other host: that one fills the voice's own instrument from
     * `oscp`, this one the orbit's chain from `katp`. The two namespaces never cross.
     *
     * @param name slot name, `<stage>.<knob>` for a classic knob or any word for an authored one
     * @param default the value the knob has while nothing writes the name
     * @param description human-readable description for documentation
     */
    @KlangScript.Method
    fun param(name: String, default: Double, description: String = ""): IgnitorDsl =
        IgnitorDsl.Param(name, default, description)

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
