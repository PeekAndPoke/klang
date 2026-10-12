/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.peekandpoke.klang.audio_bridge.KatalystKnobDsl
import io.peekandpoke.klang.common.infra.KlangMap

/*
 * Reads the knobs of a Katalyst chain: one [KatalystKnobDsl] in, one `Double` out ([KatalystKnob]). The composite
 * values a stage wants instead of a number (the body and vowel `ResonatorConfig`s, the [CompressorSettings], the
 * [DuckSettings]) are filled by that stage's writer (`KatalystSlotWriters.kt`), each the one caller of its rule.
 *
 * Katalyst step 3a (2026-09-17). The per-stage contract is `docs/tasks-archive/2026-09/20260928-katalyst-dsl.md` §7 and
 * the value rule is the signal-flow plan §7 (D4): **a chain's stage knobs come from its slots only**, on every chain
 * since step 5b-1, the one a cylinder is born with included. The owner voice is a knob source only through the map it
 * carries; it has no bus fields since 5b-3.
 *
 * The param state is the owner voice's `katalystParams` map, which `.katp` and the bus doors write: a
 * [KatalystKnobDsl.Param] reads `state[name]` and falls back to its authored default. Only a `Param` moves; a
 * [KatalystKnobDsl.Constant] stays as it was built.
 */

/**
 * One knob of a DECLARED stage: its authored value, plus the slot name the orbit's param state may
 * move it with.
 *
 * Built ONCE per knob when the chain is built: a [KatalystKnobDsl.Constant] is its number, a [KatalystKnobDsl.Param]
 * its default and its name, and a missing knob (null, the reverb's omitted `lowpass` or an EQ section knob the engine
 * cannot read) is [fallback]. From then on this knob is two fields, and [resolve] is one map lookup for a slot and
 * nothing at all for a constant. That is the cost rule of the param state: one lookup per SLOT per CHANGED map, and
 * zero per block, because the writers read [value] and never the map.
 *
 * **Resolution happens ONCE, when the chain is built**, and again only when the orbit's param state CHANGES (Katalyst
 * step 5a): every answer here is block-constant, so the writer [KatalystChainBuilder] installs holds the resolved
 * numbers here and re-applies them on every owner claim. That is what keeps a writer allocation-free per block.
 *
 * "Not in the map" and "no map at all" are the same answer, the authored default, so a pattern that
 * writes nothing hears exactly what the chain says.
 *
 * A knob reads a constant or a slot and nothing else: the Katalyst's knob language has no other node yet (steps 4
 * and 5 of `docs/tasks/katalyst-knob-expressions.md` add expressions and modulators), and an Ignitor node never
 * reaches a stage knob (the wire type is [KatalystKnobDsl], decision D1). Until 2026-10-12 a knob was an `IgnitorDsl`,
 * and anything that was neither a constant nor a slot was probed at two note frequencies and folded to one number
 * here; that coercion is gone with the type.
 */
internal class KatalystKnob(node: KatalystKnobDsl?, fallback: Double) {

    /** The slot name when the knob is a [KatalystKnobDsl.Param], null for a constant or a missing knob. */
    private val slot: String?

    /** What the chain itself says: a `Param`'s default, a constant's number, or the fallback for a missing knob. */
    private val authored: Double

    init {
        when (node) {
            null -> {
                slot = null
                authored = fallback
            }

            is KatalystKnobDsl.Constant -> {
                slot = null
                authored = node.value
            }

            is KatalystKnobDsl.Param -> {
                slot = node.name
                authored = node.default
            }
        }
    }

    /** The number the stage is configured with right now. */
    var value: Double = authored
        private set

    /**
     * True when [value] came OUT of the orbit's param state as a finite number, rather than from
     * what the chain authored. "The pattern wrote this knob", which is the slot twin of the wire's
     * old "the voice TOUCHED this effect" (`VoiceFactory`'s `reverbTouched` / `delayTouched`), and
     * the delay and reverb stages need it to keep that rule (see [KatalystReverbWriter]).
     *
     * FINITE on purpose: a non-finite slot is the wire's "never set" (`/dsl-design` §4), so a
     * cleared knob reads as untouched, exactly as a null field did.
     *
     * Always false for a knob that is not a slot: a chain that AUTHORED its wet as a number said
     * what it wanted, and no pattern touched it.
     */
    var written: Boolean = false
        private set

    /** Re-reads a slot from the orbit's param state; null (no owner, no slots) is the default. */
    fun resolve(params: KlangMap<Double>?) {
        val name = slot ?: return
        val fromState = params?.get(name)

        // NaN-guard on a value the author can write: a non-finite slot was never set, so it is not
        // a write either.
        // Two branches and not `fromState ?: authored`: on V8 that merge of the map's tagged value with the double
        // field boxed an absent knob's non-integral default. Measured on the classic chain: about 180 of the about
        // 1,310 bytes a new param map cost, on both bundles, pinned to one core and not (the V8 rule in
        // `audio/ref/performance.md`, V8 allocation pass).
        if (fromState != null) {
            written = fromState.isFinite()
            value = fromState
        } else {
            written = false
            value = authored
        }
    }
}
