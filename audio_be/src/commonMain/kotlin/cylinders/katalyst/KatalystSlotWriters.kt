/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.peekandpoke.klang.audio_be.effects.Reverb
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_bridge.FilterDef
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

/** Body: the material is the CHAIN's (a slot carries a number), `wet` and `floor` are slots. */
internal class KatalystBodyWriter(
    private val fx: KatalystBodyEffect,
    private val bands: List<FilterDef.Body.Mode>?,
    private val wet: KatalystKnob,
    private val floor: KatalystKnob,
) : KatalystSlotWriter {

    private var def: FilterDef.Body? = KatalystSlots.bodyDef(bands, wet.value, floor.value)

    override fun resolve(params: Map<String, Double>?) {
        wet.resolve(params)
        floor.resolve(params)
        def = KatalystSlots.bodyDef(bands, wet.value, floor.value)
    }

    override fun apply() {
        // null (the chain names no material, or an unknown one) turns the resonator off, and the
        // effect short-circuits an unchanged def, so this is free on an unchanged block.
        fx.configure(def)
    }
}

/** Vowel: the twin of [KatalystBodyWriter], with the formant bank. */
internal class KatalystVowelWriter(
    private val fx: KatalystFormantEffect,
    private val bands: List<FilterDef.Formant.Band>?,
    private val wet: KatalystKnob,
    private val floor: KatalystKnob,
) : KatalystSlotWriter {

    private var def: FilterDef.Formant? = KatalystSlots.vowelDef(bands, wet.value, floor.value)

    override fun resolve(params: Map<String, Double>?) {
        wet.resolve(params)
        floor.resolve(params)
        def = KatalystSlots.vowelDef(bands, wet.value, floor.value)
    }

    override fun apply() {
        fx.configure(def)
    }
}

/**
 * Delay: `wet` is the stage's ON SWITCH until step 5b makes it the insert amount, so an off stage
 * is expressed by handing the line a non-finite TIME, which is also what makes a live tail drain
 * instead of freeze (see [KatalystDelayEffect]).
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
        fx.configure(time = gatedTime, feedback = feedback.value, cap = cap.value)
    }

    private fun gate(): Double = if (sendIsOn(wet.value)) time.value else SLOT_UNSET
}

/**
 * Reverb: the slot carries the AUTHORED 0 to 10 size, so it passes through the one shared
 * conversion ([Reverb.normalizeSize]) here, where `VoiceFactory` does it for a voice. `wet` is the
 * on switch, as on the delay above.
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
        fx.configure(size = gatedSize, lowpass = damping)
    }

    private fun gate(): Double =
        if (sendIsOn(wet.value)) Reverb.normalizeSize(size.value) else SLOT_UNSET

    // NaN-guard on a value the author can write: non-finite is "unset", which is the engine's own
    // fixed damping.
    private fun damping(): Double? = lowpass.value.takeIf { it.isFinite() }
}

/**
 * Phaser: the five knobs, through the one gate both knob sources share ([writePhaser]).
 *
 * `wet` and `floor` are NaN-guarded here, the way the reverb writer guards its `lowpass`, because
 * the two effect setters behind them SWALLOW a non-finite value instead of reading it as unset:
 * `Phaser.depth` drops it and keeps the depth it had (so a cleared `phaser.wet` would leave the
 * phaser engaged at the previous amount), and a NaN floor reaches `WetDryMix.dryCoeff` and comes
 * out as a full notch. `rate`, `center` and `sweep` need no guard: they are only written while the
 * phaser is engaged, and `center`/`sweep` already fall back on `> 0` inside [writePhaser].
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
        writePhaser(
            fx = fx,
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
    private val sampleRate: Int,
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
        writeCompressor(fx, settings, sampleRate)
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
 * Duck: on iff the stage names a source orbit AND asks for depth. The instance is reused so the
 * envelope follower survives, and the writer holds the settings the arriving chain applies after a
 * handover (see [writeDuck]).
 */
internal class KatalystDuckWriter(
    private val fx: KatalystDuckEffect,
    private val sampleRate: Int,
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
        writeDuck(fx, settings, sampleRate)
    }

    private fun settings(): Voice.Ducking? = KatalystSlots.duckSettings(
        orbit = orbit.value,
        depth = depth.value,
        attack = attack.value,
    )
}
