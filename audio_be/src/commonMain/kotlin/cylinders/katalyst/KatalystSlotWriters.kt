/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.peekandpoke.klang.audio_be.effects.Reverb
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_bridge.BodyMaterials
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.audio_bridge.VowelBands
import io.peekandpoke.klang.audio_bridge.constants.PHASER_FLOOR
import io.peekandpoke.klang.audio_bridge.constants.PHASER_WET
import io.peekandpoke.klang.audio_bridge.constants.SLOT_UNSET

// The KatalystSlotWriter of every DECLARED stage kind: one class per stage, holding that stage's
// KatalystKnobs and whatever composite its effect wants written.
//
// The split the interface asks for, stage by stage. `resolve` runs when the orbit's param state
// changes and is the only place a map is read and a composite allocated; `apply` runs every block
// and writes numbers that are already in hand. A chain with no `.katp` and no bus door on its orbit
// therefore resolves exactly once, when it is built, and every block after that costs what step 3a
// cost.
//
// Nothing here decides a gate of its own: each writer reproduces the per-stage contract of
// `docs/tasks/katalyst-dsl.md` §7 through the same KatalystSlots functions the build used, so a
// value that arrives from a slot and one that was authored as a constant take the identical path.

/**
 * Body: all three knobs are slots, the material as the INDEX of a name in the shared catalogue
 * (Katalyst step 5a-2). The index-to-bands lookup lives in [resolve] with the rest of the
 * composite, never in [apply]: `apply` writes a `FilterDef` that is already in hand.
 */
internal class KatalystBodyWriter(
    private val fx: KatalystBodyEffect,
    private val material: KatalystKnob,
    private val wet: KatalystKnob,
    private val floor: KatalystKnob,
) : KatalystSlotWriter {

    private var def: FilterDef.Body? = buildDef()

    override fun resolve(params: Map<String, Double>?) {
        material.resolve(params)
        wet.resolve(params)
        floor.resolve(params)
        def = buildDef()
    }

    override fun apply() {
        // null (the chain names no material, or an index out of range) turns the resonator off, and
        // the effect short-circuits an unchanged def, so this is free on an unchanged block.
        fx.configure(def)
    }

    private fun buildDef(): FilterDef.Body? =
        KatalystSlots.bodyDef(BodyMaterials.modesAt(material.value), wet.value, floor.value)
}

/** Vowel: the twin of [KatalystBodyWriter], with the formant bank. */
internal class KatalystVowelWriter(
    private val fx: KatalystFormantEffect,
    private val vowel: KatalystKnob,
    private val wet: KatalystKnob,
    private val floor: KatalystKnob,
) : KatalystSlotWriter {

    private var def: FilterDef.Formant? = buildDef()

    override fun resolve(params: Map<String, Double>?) {
        vowel.resolve(params)
        wet.resolve(params)
        floor.resolve(params)
        def = buildDef()
    }

    override fun apply() {
        fx.configure(def)
    }

    private fun buildDef(): FilterDef.Formant? =
        KatalystSlots.vowelDef(VowelBands.bandsAt(vowel.value), wet.value, floor.value)
}

/**
 * Delay: an off stage is expressed by handing the line a non-finite TIME, which is also what makes
 * a live tail drain instead of freeze (see [KatalystDelayEffect]). `wet` is the amount of the orbit
 * mix the line is fed; whether the stage runs at all is [sendStageRuns].
 */
internal class KatalystDelayWriter(
    private val fx: KatalystDelayEffect,
    private val wet: KatalystKnob,
    private val time: KatalystKnob,
    private val feedback: KatalystKnob,
    private val cap: KatalystKnob,
) : KatalystSlotWriter {

    private var gatedTime: Double = gate()

    override fun resolve(params: Map<String, Double>?) {
        wet.resolve(params)
        time.resolve(params)
        feedback.resolve(params)
        cap.resolve(params)
        gatedTime = gate()
    }

    override fun apply() {
        fx.configure(time = gatedTime, feedback = feedback.value, cap = cap.value, wet = wet.value)
    }

    private fun gate(): Double = if (sendStageRuns(wet)) time.value else SLOT_UNSET
}

/**
 * Reverb: the slot carries the AUTHORED 0 to 10 size, so it passes through the one shared
 * conversion ([Reverb.normalizeSize]) here. The stage's gate is [sendStageRuns] and `wet` is the
 * amount of the orbit mix the room is fed, as on the delay above.
 */
internal class KatalystReverbWriter(
    private val fx: KatalystReverbEffect,
    private val wet: KatalystKnob,
    private val size: KatalystKnob,
    private val lowpass: KatalystKnob,
) : KatalystSlotWriter {

    private var gatedSize: Double = gate()
    private var damping: Double? = damping()

    override fun resolve(params: Map<String, Double>?) {
        wet.resolve(params)
        size.resolve(params)
        lowpass.resolve(params)
        gatedSize = gate()
        damping = damping()
    }

    override fun apply() {
        fx.configure(size = gatedSize, lowpass = damping, wet = wet.value)
    }

    private fun gate(): Double =
        if (sendStageRuns(wet)) Reverb.normalizeSize(size.value) else SLOT_UNSET

    // NaN-guard on a value the author can write: non-finite is "unset", which is the engine's own
    // fixed damping.
    private fun damping(): Double? = lowpass.value.takeIf { it.isFinite() }
}

/**
 * Phaser: the five knobs, through the one gate and kernel-param rule in
 * [KatalystPhaserEffect.configure].
 *
 * `wet` and `floor` are NaN-guarded here, the way the reverb writer guards its `lowpass`, because
 * the two knobs behind them read a non-finite value as something other than unset:
 * `Phaser.depth`'s setter DROPS it and keeps the depth it had (so a cleared `phaser.wet` would
 * leave the phaser engaged at the previous amount), and `Phaser.floor` stores it RAW, where
 * `WetDryMix.dryCoeff` coerces it to 0.0 and the additive law (floor 1.0, the dry signal passing
 * at full level next to the wet) becomes the CROSSFADE law, `cos(depth*pi/2)^2`: about unity just
 * above the engage threshold, -1.4 dB at depth 0.25, -6 dB at 0.5, -20 dB at 0.8 and silent only
 * at 1.0. So it is audible at large depths and nearly inaudible at small ones, not a full notch.
 * `rate`, `center` and `sweep` need no guard: they are only written while the phaser is engaged,
 * and `center`/`sweep` already fall back on `> 0` inside [KatalystPhaserEffect.configure].
 */
internal class KatalystPhaserWriter(
    private val fx: KatalystPhaserEffect,
    private val wet: KatalystKnob,
    private val rate: KatalystKnob,
    private val center: KatalystKnob,
    private val sweep: KatalystKnob,
    private val floor: KatalystKnob,
) : KatalystSlotWriter {

    private var depth: Double = guardedDepth()
    private var dryFloor: Double = guardedFloor()

    override fun resolve(params: Map<String, Double>?) {
        wet.resolve(params)
        rate.resolve(params)
        center.resolve(params)
        sweep.resolve(params)
        floor.resolve(params)
        depth = guardedDepth()
        dryFloor = guardedFloor()
    }

    override fun apply() {
        fx.configure(
            depth = depth,
            rate = rate.value,
            center = center.value,
            sweep = sweep.value,
            floor = dryFloor,
        )
    }

    // NaN-guards on values the author can write: an unset slot is OFF for the amount and the
    // engine's own dry coefficient for the floor, not whatever the setter last held.
    private fun guardedDepth(): Double = if (wet.value.isFinite()) wet.value else PHASER_WET

    private fun guardedFloor(): Double = if (floor.value.isFinite()) floor.value else PHASER_FLOOR
}

/** Compressor: on iff ANY of the five slots is finite, the voice path's own rule. */
internal class KatalystCompressorWriter(
    private val fx: KatalystCompressorEffect,
    private val threshold: KatalystKnob,
    private val ratio: KatalystKnob,
    private val knee: KatalystKnob,
    private val attack: KatalystKnob,
    private val release: KatalystKnob,
) : KatalystSlotWriter {

    private var settings: Voice.Compressor? = settings()

    override fun resolve(params: Map<String, Double>?) {
        threshold.resolve(params)
        ratio.resolve(params)
        knee.resolve(params)
        attack.resolve(params)
        release.resolve(params)
        settings = settings()
    }

    override fun apply() {
        fx.configure(settings)
    }

    private fun settings(): Voice.Compressor? = KatalystSlots.compressorSettings(
        threshold = threshold.value,
        ratio = ratio.value,
        knee = knee.value,
        attack = attack.value,
        release = release.value,
    )
}

/**
 * Eq: every section's knobs, resolved into the flat scalar array the stage configures from
 * ([KatalystEqEffect.KNOBS_PER_SECTION] per section, in list order).
 *
 * A null knob is a param the section's TYPE does not have (only a bell has `db`, only a tap has
 * `gain`), and it resolves to the 0.0 the per-voice adapter passes for the same absent param, so
 * both adapters hand [io.peekandpoke.klang.audio_be.filters.EqCore] the identical seven arguments.
 *
 * No NaN guard, deliberately, unlike every other writer here: the coefficient helpers own the
 * fallbacks for a non-finite freq, q, db and tap gain, and adding one at this surface would make
 * the same number sound different on a bus than on a voice (see [KatalystEqEffect]).
 */
internal class KatalystEqWriter(
    private val fx: KatalystEqEffect,
    private val knobs: Array<KatalystKnob?>,
) : KatalystSlotWriter {

    /** The resolved scalars, filled in place: `apply` writes numbers already in hand. */
    private val values = DoubleArray(knobs.size)

    init {
        fill()
    }

    override fun resolve(params: Map<String, Double>?) {
        for (i in knobs.indices) {
            knobs[i]?.resolve(params)
        }

        fill()
    }

    override fun apply() {
        // The stage short-circuits an unchanged curve, so this is one compare pass on a block that
        // moved no knob, which is every block of a chain without `.katp` on its EQ.
        fx.configure(values)
    }

    private fun fill() {
        for (i in knobs.indices) {
            // `knobs[i]?.value ?: 0.0` boxes the nullable Double per knob on the JVM; the explicit
            // branch reads the primitive field (`audio/ref/performance.md`).
            val knob = knobs[i]

            values[i] = if (knob != null) knob.value else 0.0
        }
    }
}

/** Gain: the one knob, guarded, into the fader. */
internal class KatalystGainWriter(
    private val fx: KatalystGainEffect,
    private val gain: KatalystKnob,
) : KatalystSlotWriter {

    private var factor: Double = guarded()

    override fun resolve(params: Map<String, Double>?) {
        gain.resolve(params)
        factor = guarded()
    }

    override fun apply() {
        fx.configure(factor)
    }

    /**
     * NaN-guard on a value the author can write: an unset fader is unity, the identity element of
     * the stage and the master's own fallback for the same knob (`MasterChain.buildGain`). NOT a
     * magnitude clamp: a negative factor and one above unity pass through untouched.
     */
    private fun guarded(): Double = if (gain.value.isFinite()) gain.value else 1.0
}

/**
 * Duck: on iff the stage names a source orbit AND asks for depth. The instance is reused so the
 * envelope follower survives, and the writer holds the settings the arriving chain applies after a
 * handover (see [KatalystDuckEffect.configure]).
 */
internal class KatalystDuckWriter(
    private val fx: KatalystDuckEffect,
    private val orbit: KatalystKnob,
    private val depth: KatalystKnob,
    private val attack: KatalystKnob,
) : KatalystSlotWriter {

    private var settings: Voice.Ducking? = settings()

    /**
     * Whether this stage will be CONFIGURED rather than cleared, which the host asks BEFORE it
     * hands a live envelope over (`KatalystChain.ducksWith`). Kept in step with the settings, and
     * a `var` because `.katp("duck.orbit", n)` can turn a declared duck on after the chain was
     * built.
     */
    var declared: Boolean = settings != null
        private set

    override fun resolve(params: Map<String, Double>?) {
        orbit.resolve(params)
        depth.resolve(params)
        attack.resolve(params)
        settings = settings()
        declared = settings != null
    }

    override fun apply() {
        fx.configure(settings)
    }

    private fun settings(): Voice.Ducking? = KatalystSlots.duckSettings(
        orbit = orbit.value,
        depth = depth.value,
        attack = attack.value,
    )
}
