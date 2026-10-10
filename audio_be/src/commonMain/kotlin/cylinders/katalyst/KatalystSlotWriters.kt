/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.peekandpoke.klang.audio_be.effects.Reverb
import io.peekandpoke.klang.audio_be.filters.ResonatorConfig
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_ATTACK_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_KNEE_DB
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_RATIO
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_RELEASE_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_THRESHOLD_DB
import io.peekandpoke.klang.audio_bridge.constants.DUCK_ATTACK_SECONDS
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
// Each writer holds the per-stage contract of `docs/tasks-archive/2026-09/20260928-katalyst-dsl.md`
// §7 for its stage (the gate, and the rule for an unset slot), so a value that arrives from a slot
// and one that was authored as a constant take the identical path. A NaN rule lives in ONE place per
// knob: here, or in the stage's `configure` when that stage compares and caches what it is handed
// (body, vowel, the reverb's lowpass), never in both (audit B2.11, 2026-10-07).

/**
 * Body and vowel (engine tidy-up step 12 (a): one writer, as there is one stage class): all three knobs are slots, the
 * material or the vowel as the INDEX of a name in its shared catalogue (Katalyst step 5a-2). The index-to-table lookup
 * lives in [resolve], never in [apply]: `apply` hands over a config that is already in hand.
 *
 * The table comes from [ResonatorTables.at] for the stage's kind, by the catalogue's own index rule
 * (`BodyMaterials.slotIndexAt`, `VowelBands.slotIndexAt`). An unset slot (non-finite), an index of 0 (`none`) and an
 * index out of range are the same answer, null, which is the stage off whatever `wet` says: the rule
 * `SprudelVoiceData.toVoiceData` follows for an unknown NAME on the voice path; the name-to-index half is
 * `BodyMaterials.indexOf` / `VowelBands.indexOf`, and both doors call it.
 *
 * `wet` and `floor` pass through RAW: a non-finite one is unset, and [KatalystResonatorEffect.configure] substitutes
 * the kind's constant for it before its compare (a raw `katp` write is the only way one arrives: the `body(...)` and
 * `vowel(...)` doors fill their own companions, `/dsl-design` §4, checklist 11).
 */
internal class KatalystResonatorWriter(
    private val fx: KatalystResonatorEffect,
    /** The catalogue index: `body.material` or `vowel.vowel`. */
    private val index: KatalystKnob,
    private val wet: KatalystKnob,
    private val floor: KatalystKnob,
) : KatalystSlotWriter {

    /** This writer's own config, rewritten at [resolve] and handed over by reference ([ResonatorConfig] says why). */
    private val config = ResonatorConfig()

    init {
        fill()
    }

    override fun resolve(params: Map<String, Double>?) {
        index.resolve(params)
        wet.resolve(params)
        floor.resolve(params)
        fill()
    }

    override fun apply() {
        // A null table (the chain names no material or vowel, or an index out of range) turns the resonator off, and
        // the effect short-circuits an unchanged config, so this is free on an unchanged block.
        fx.configure(config)
    }

    private fun fill() {
        config.table = ResonatorTables.at(kind = fx.kind, slotValue = index.value)
        config.mix = wet.value
        config.floor = floor.value
    }
}

/**
 * Delay: an off stage is expressed by handing the line a non-finite TIME, which is also what makes
 * a live tail drain instead of freeze (see [KatalystDelayEffect]). `wet` is the amount of the orbit
 * mix the line is fed; whether the stage runs at all is [stageAskedFor].
 */
internal class KatalystDelayWriter(
    private val fx: KatalystDelayEffect,
    private val wet: KatalystKnob,
    private val time: KatalystKnob,
    private val feedback: KatalystKnob,
    private val cap: KatalystKnob,
) : KatalystSlotWriter {

    /** This writer's own config, rewritten at [resolve] and handed over by reference ([DelayConfig] says why). */
    private val config = DelayConfig()

    init {
        fill()
    }

    override fun resolve(params: Map<String, Double>?) {
        wet.resolve(params)
        time.resolve(params)
        feedback.resolve(params)
        cap.resolve(params)
        fill()
    }

    override fun apply() {
        fx.configure(config)
    }

    private fun fill() {
        config.time = gate()
        config.feedback = feedback.value
        config.cap = cap.value
        config.wet = wet.value
    }

    private fun gate(): Double = if (stageAskedFor(wet)) time.value else SLOT_UNSET
}

/**
 * Reverb: the slot carries the AUTHORED 0 to 10 size, so it passes through the one shared
 * conversion ([Reverb.normalizeSize]) here. The stage's gate is [stageAskedFor] and `wet` is the
 * amount of the orbit mix the room is fed, as on the delay above.
 */
internal class KatalystReverbWriter(
    private val fx: KatalystReverbEffect,
    private val wet: KatalystKnob,
    private val size: KatalystKnob,
    private val lowpass: KatalystKnob,
) : KatalystSlotWriter {

    /** This writer's own config, rewritten at [resolve] and handed over by reference ([ReverbConfig] says why). */
    private val config = ReverbConfig()

    init {
        fill()
    }

    override fun resolve(params: Map<String, Double>?) {
        wet.resolve(params)
        size.resolve(params)
        lowpass.resolve(params)
        fill()
    }

    override fun apply() {
        fx.configure(config)
    }

    private fun fill() {
        config.size = gate()
        // The lowpass slot, RAW: a non-finite one is unset, which [KatalystReverbEffect.configure] turns into the
        // engine's own fixed damping. Boxed here, once per resolve, and not in [apply].
        config.lowpass = lowpass.value
        config.wet = wet.value
    }

    private fun gate(): Double =
        if (stageAskedFor(wet)) Reverb.normalizeSize(size.value) else SLOT_UNSET
}

/**
 * Phaser: the five knobs, through the one gate and kernel-param rule in
 * [KatalystPhaserEffect.configure].
 *
 * `wet` and `floor` are NaN-guarded here, and here only, because
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

    /** This writer's own config, rewritten at [resolve] and handed over by reference ([PhaserConfig] says why). */
    private val config = PhaserConfig()

    init {
        fill()
    }

    override fun resolve(params: Map<String, Double>?) {
        wet.resolve(params)
        rate.resolve(params)
        center.resolve(params)
        sweep.resolve(params)
        floor.resolve(params)
        fill()
    }

    override fun apply() {
        fx.configure(config)
    }

    private fun fill() {
        config.depth = guardedDepth()
        config.rate = rate.value
        config.center = center.value
        config.sweep = sweep.value
        config.floor = guardedFloor()
    }

    // NaN-guards on values the author can write: an unset slot is OFF for the amount and the
    // engine's own dry coefficient for the floor, not whatever the setter last held.
    private fun guardedDepth(): Double = if (wet.value.isFinite()) wet.value else PHASER_WET

    private fun guardedFloor(): Double = if (floor.value.isFinite()) floor.value else PHASER_FLOOR
}

/**
 * Compressor: on iff ANY of the five slots is finite, and then every unset (non-finite) one takes
 * its `COMPRESSOR_*` constant. Null (the stage off) when none is set.
 *
 * The substitution is the NaN rule for a raw `katp` write, not a second fill: since Katalyst step
 * 5a-3 the `compressor(...)` door fills the other four itself, whichever of the five the call
 * named, so the values it writes are already the ones this writer would supply.
 */
internal class KatalystCompressorWriter(
    private val fx: KatalystCompressorEffect,
    private val threshold: KatalystKnob,
    private val ratio: KatalystKnob,
    private val knee: KatalystKnob,
    private val attack: KatalystKnob,
    private val release: KatalystKnob,
) : KatalystSlotWriter {

    private var settings: CompressorSettings? = settings()

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

    private fun settings(): CompressorSettings? {
        val t = threshold.value
        val r = ratio.value
        val k = knee.value
        val a = attack.value
        val rel = release.value

        // NaN-guards on values the author can write: a non-finite slot was never set.
        if (!t.isFinite() && !r.isFinite() && !k.isFinite() && !a.isFinite() && !rel.isFinite()) {
            return null
        }

        return CompressorSettings(
            thresholdDb = if (t.isFinite()) t else COMPRESSOR_THRESHOLD_DB,
            ratio = if (r.isFinite()) r else COMPRESSOR_RATIO,
            kneeDb = if (k.isFinite()) k else COMPRESSOR_KNEE_DB,
            attackSeconds = if (a.isFinite()) a else COMPRESSOR_ATTACK_SECONDS,
            releaseSeconds = if (rel.isFinite()) rel else COMPRESSOR_RELEASE_SECONDS,
        )
    }
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
     * the stage. NOT a
     * magnitude clamp: a negative factor and one above unity pass through untouched.
     */
    private fun guarded(): Double = if (gain.value.isFinite()) gain.value else 1.0
}

/**
 * Distort: on iff the amount is finite and above 0 ([KatalystDistortEffect.configure] decides). An unset (non-finite)
 * slot is handed over as it is, which the stage reads as OFF: the stage's identity is "no distortion", so an amount
 * nobody set must not invent one. The holder is this writer's own, filled at [resolve] and handed over by reference
 * ([DistortConfig] says why).
 */
internal class KatalystDistortWriter(
    private val fx: KatalystDistortEffect,
    private val amount: KatalystKnob,
) : KatalystSlotWriter {

    private val config = DistortConfig()

    init {
        fill()
    }

    override fun resolve(params: Map<String, Double>?) {
        amount.resolve(params)
        fill()
    }

    override fun apply() {
        fx.configure(config)
    }

    private fun fill() {
        config.amount = amount.value
    }
}

/**
 * Duck: on iff the stage names a source orbit AND asks for depth. The instance is reused so the
 * envelope follower survives, and the writer holds the settings the arriving chain applies after a
 * handover (see [KatalystDuckEffect.configure]).
 *
 * `orbit` is a number the runtime coerces to an Int, exactly as the sprudel door does; a finite
 * negative is a request like any other, not an off switch (the off switch is the non-finite
 * default). A non-finite attack takes `DUCK_ATTACK_SECONDS`.
 */
internal class KatalystDuckWriter(
    private val fx: KatalystDuckEffect,
    private val orbit: KatalystKnob,
    private val depth: KatalystKnob,
    private val attack: KatalystKnob,
) : KatalystSlotWriter {

    private var settings: DuckSettings? = settings()

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

    private fun settings(): DuckSettings? {
        val source = orbit.value
        val amount = depth.value
        val attackSeconds = attack.value

        // NaN-guard on values the author can write: a non-finite orbit is "no source named".
        if (!source.isFinite() || !(amount > 0.0)) {
            return null
        }

        return DuckSettings(
            cylinderId = source.toInt(),
            // NaN-guard on a value the author can write: a non-finite attack is unset.
            attackSeconds = if (attackSeconds.isFinite()) attackSeconds else DUCK_ATTACK_SECONDS,
            depth = amount,
        )
    }
}

/**
 * Parallel: no knob of its own; it hands the orbit's param state to every branch chain, so a branch's stages resolve
 * and apply exactly as the chain's own do (each branch keeps its own identity gate, [KatalystChain.resolveParams]).
 * Holds the state between the two halves and drops it on a resolve from null, as every chain does
 * ([KatalystChain.reset] forgets through that path).
 */
internal class KatalystParallelWriter(
    private val fx: KatalystParallelEffect,
) : KatalystSlotWriter {

    private var params: Map<String, Double>? = null

    override fun resolve(params: Map<String, Double>?) {
        this.params = params

        for (i in fx.branches.indices) {
            fx.branches[i].resolveParams(params)
        }
    }

    override fun apply() {
        for (i in fx.branches.indices) {
            fx.branches[i].applyParams(params)
        }
    }
}
