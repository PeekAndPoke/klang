/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.peekandpoke.klang.audio_be.effects.Compressor
import io.peekandpoke.klang.audio_be.effects.Ducking
import io.peekandpoke.klang.audio_be.effects.Phaser
import io.peekandpoke.klang.audio_be.effects.Reverb
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.warehouse.ReverbUnits
import io.peekandpoke.klang.audio_be.warehouse.SizedBuffers
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import io.peekandpoke.klang.audio_bridge.constants.DELAY_CAP
import io.peekandpoke.klang.audio_bridge.constants.DELAY_FEEDBACK
import io.peekandpoke.klang.audio_bridge.constants.DELAY_TIME_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.DELAY_WET
import io.peekandpoke.klang.audio_bridge.constants.PHASER_CENTER_HZ
import io.peekandpoke.klang.audio_bridge.constants.PHASER_FLOOR
import io.peekandpoke.klang.audio_bridge.constants.PHASER_RATE_HZ
import io.peekandpoke.klang.audio_bridge.constants.PHASER_SWEEP_HZ
import io.peekandpoke.klang.audio_bridge.constants.PHASER_WET
import io.peekandpoke.klang.audio_bridge.constants.REVERB_SIZE
import io.peekandpoke.klang.audio_bridge.constants.REVERB_WET
import io.peekandpoke.klang.audio_bridge.constants.SLOT_UNSET

/**
 * Builds the [KatalystChain] for one [KatalystDsl]: one exhaustive `when` over
 * [KatalystStageDsl], one stage instance per declared stage, in the declared order.
 *
 * The seven classic effects are the stage instances for their variants, **unchanged** (the shells
 * over the shared DSP in `audio_be/effects/`), with the constructor arguments the cylinder used to
 * pass them: the delay rents its ring from [SizedBuffers] and the reverb its network from
 * [ReverbUnits], both on first activation and never here, so building a chain allocates no ring
 * and no network. [KatalystStageDsl.Eq] and [KatalystStageDsl.Gain] build a
 * [KatalystPassThroughStage] until step 4 gives them their DSP.
 *
 * **Two kinds of writer, chosen by [build]'s `voiceDriven` flag** (Katalyst step 3a, 2026-09-17),
 * one interface each, so a declared chain can be configured with no owner voice alive
 * ([KatalystChain.applyStatic]):
 *
 *  - `voiceDriven = true`, the CLASSIC chain: every knob comes from the orbit's owner voice, which
 *    is `Cylinder.applyBusEffects` line for line, so a song that declares no chain is
 *    byte-identical to the pre-DSL engine.
 *  - `voiceDriven = false`, a DECLARED chain: every knob comes from the chain's own slots
 *    ([KatalystSlots]), resolved once here, and the voice is ignored (the signal-flow plan §7,
 *    D4: the chain is the instrument).
 *
 * The distinction exists only until step 5 takes the bus fields off the wire and makes the doors
 * `katp` writers on the orbit's chain. Then every chain is slot-driven and the flag goes.
 *
 * **A chain that declares two ducks keeps the LAST one** (decided with the maintainer,
 * 2026-09-17): the cylinder runs exactly one ducking effect, so the earlier declarations are
 * silently dropped rather than summed. Every other kind may appear more than once and every
 * instance runs, at its own position.
 */
object KatalystChainBuilder {

    /**
     * Builds the chain for [dsl]. Call once per cylinder and chain (see `Cylinder`), never per
     * block: the stage instances own the orbit's DSP state, and building them is where the
     * allocation lives.
     */
    fun build(
        dsl: KatalystDsl,
        sampleRate: Int,
        blockFrames: Int,
        /** The ring shelf a delay stage rents from. Required: a private shelf hidden in a default
         *  would let a caller build a chain that rents outside the engine's one warehouse. */
        rings: SizedBuffers,
        /** The unit shelf a reverb stage rents from. Required, same reason. */
        reverbs: ReverbUnits,
        /**
         * True ONLY for the classic chain, whose knobs still come from the owner voice (see the
         * class KDoc). Required, and not derived from `dsl == KatalystDsl.classic` here: the
         * decision belongs to the host that installs the chain, which is the one place that knows
         * whether it is serving the historical default or an author's declaration.
         */
        voiceDriven: Boolean,
    ): KatalystChain {
        val pipeline = mutableListOf<KatalystEffect>()
        val owners = mutableListOf<KatalystOwnerApply>()
        val statics = mutableListOf<KatalystStaticApply>()
        var duck: KatalystDuckEffect? = null
        var duckStage: KatalystStageDsl.Duck? = null

        for (stage in dsl.stages) {
            when (stage) {
                // Body / vowel resonators, timbre shapers of the whole orbit (moved off the
                // per-voice chain). In the classic chain they run first, so they color the dry
                // mix before the time/dynamics effects.
                is KatalystStageDsl.Body -> {
                    val fx = KatalystBodyEffect(sampleRate.toDouble())
                    pipeline.add(fx)

                    if (voiceDriven) {
                        // null (the owner has no body) turns the resonator off, not a no-op.
                        owners.add(KatalystOwnerApply { voice -> fx.configure(voice.body) })
                    } else {
                        val def = KatalystSlots.bodyDef(stage, KatalystSlots.bodyModes(stage.material))
                        statics.add(KatalystStaticApply { fx.configure(def) })
                    }
                }

                is KatalystStageDsl.Vowel -> {
                    val fx = KatalystFormantEffect(sampleRate.toDouble())
                    pipeline.add(fx)

                    if (voiceDriven) {
                        owners.add(KatalystOwnerApply { voice -> fx.configure(voice.vowel) })
                    } else {
                        val def = KatalystSlots.vowelDef(stage, KatalystSlots.vowelBands(stage.vowel))
                        statics.add(KatalystStaticApply { fx.configure(def) })
                    }
                }

                // No ring until a voice asks for one (resource warehouse, 2b). This used to
                // construct a 10-second DelayLine per orbit (7.68 MB, 97 % of the cylinder)
                // delay or not.
                is KatalystStageDsl.Delay -> {
                    val fx = KatalystDelayEffect(
                        rings = rings,
                        sampleRate = sampleRate,
                        blockFrames = blockFrames,
                    )
                    pipeline.add(fx)

                    // Routed through the effect's lifecycle: an off-config drains the tail out on
                    // its own timeline instead of freezing the ring (see KatalystDelayEffect).
                    if (voiceDriven) {
                        owners.add(
                            KatalystOwnerApply { voice ->
                                fx.configure(
                                    time = voice.delay.time,
                                    feedback = voice.delay.feedback,
                                    cap = voice.delay.cap,
                                )
                            }
                        )
                    } else {
                        // In THIS step `delay.wet` is the stage's ON SWITCH, not yet its amount
                        // (decided with the maintainer, 2026-09-17): the send amount is still the
                        // per-voice `voice.delay.amount` that `SendRenderer` writes into the send
                        // buffer, so a chain cannot say HOW MUCH yet, but it must be able to say
                        // NOTHING. `wet(0.0)` therefore rents no ring, consistent with the phaser
                        // (gated on depth) and the duck (gated on orbit). Step 5 makes the sends
                        // insert-style and `wet` becomes the amount it reads like.
                        val time = KatalystSlots.resolve(stage.time, DELAY_TIME_SECONDS)
                        val feedback = KatalystSlots.resolve(stage.feedback, DELAY_FEEDBACK)
                        val cap = KatalystSlots.resolve(stage.cap, DELAY_CAP)
                        val gatedTime = if (sendIsOn(stage.wet, DELAY_WET)) time else SLOT_UNSET

                        statics.add(
                            KatalystStaticApply { fx.configure(time = gatedTime, feedback = feedback, cap = cap) }
                        )
                    }
                }

                // No network until a voice asks for room (resource warehouse, 2d): ~200 KB per
                // orbit otherwise.
                is KatalystStageDsl.Reverb -> {
                    val fx = KatalystReverbEffect(
                        units = reverbs,
                        blockFrames = blockFrames,
                    )
                    pipeline.add(fx)

                    // reverb.amount is used by SendRenderer for the send amount. Routed through
                    // the effect's lifecycle like the delay: an off-config drains the tail out on
                    // its own timeline instead of freezing the combs (see KatalystReverbEffect).
                    // size is already normalized (and bounded) by `Reverb.normalizeSize` in
                    // VoiceFactory, and configure bounds it again at the door, so every caller
                    // shares one conversion.
                    if (voiceDriven) {
                        owners.add(
                            KatalystOwnerApply { voice ->
                                fx.configure(
                                    size = voice.reverb.size,
                                    lowpass = voice.reverb.lowpass,
                                )
                            }
                        )
                    } else {
                        // The slot carries the AUTHORED 0..10 size, so it passes through the one
                        // shared conversion here, where VoiceFactory does it for a voice.
                        // `reverb.wet` is the stage's ON SWITCH in this step, as on the delay above.
                        val size = Reverb.normalizeSize(KatalystSlots.resolve(stage.size, REVERB_SIZE))
                        val gatedSize = if (sendIsOn(stage.wet, REVERB_WET)) size else SLOT_UNSET
                        val lowpass = KatalystSlots.resolve(stage.lowpass, SLOT_UNSET)
                            // NaN-guard on a value the author can write: non-finite is "unset",
                            // which is the engine's own fixed damping.
                            .takeIf { it.isFinite() }

                        statics.add(KatalystStaticApply { fx.configure(size = gatedSize, lowpass = lowpass) })
                    }
                }

                is KatalystStageDsl.Phaser -> {
                    val fx = KatalystPhaserEffect(
                        phaser = Phaser(sampleRate),
                    )
                    pipeline.add(fx)

                    if (voiceDriven) {
                        owners.add(
                            KatalystOwnerApply { voice ->
                                writePhaser(
                                    fx = fx,
                                    depth = voice.phaser.depth,
                                    rate = voice.phaser.rate,
                                    center = voice.phaser.center,
                                    sweep = voice.phaser.sweep,
                                    floor = voice.phaser.floor,
                                )
                            }
                        )
                    } else {
                        val depth = KatalystSlots.resolve(stage.wet, PHASER_WET)
                        val rate = KatalystSlots.resolve(stage.rate, PHASER_RATE_HZ)
                        val center = KatalystSlots.resolve(stage.center, PHASER_CENTER_HZ)
                        val sweep = KatalystSlots.resolve(stage.sweep, PHASER_SWEEP_HZ)
                        val floor = KatalystSlots.resolve(stage.floor, PHASER_FLOOR)

                        statics.add(
                            KatalystStaticApply {
                                writePhaser(fx, depth = depth, rate = rate, center = center, sweep = sweep, floor = floor)
                            }
                        )
                    }
                }

                is KatalystStageDsl.Compressor -> {
                    val fx = KatalystCompressorEffect()
                    pipeline.add(fx)

                    if (voiceDriven) {
                        owners.add(KatalystOwnerApply { voice -> writeCompressor(fx, voice.compressor, sampleRate) })
                    } else {
                        val settings = KatalystSlots.compressorSettings(stage)

                        statics.add(KatalystStaticApply { writeCompressor(fx, settings, sampleRate) })
                    }
                }

                // Declared in the list, run outside it: `Cylinders` applies it after every orbit,
                // because it needs cross-orbit access to the sidechain source. The last
                // declaration wins, so a second Duck stage replaces the first, instance and all.
                is KatalystStageDsl.Duck -> {
                    duck = KatalystDuckEffect()
                    duckStage = stage
                }

                // No DSP yet: the position is kept and the buffers are untouched (step 4).
                is KatalystStageDsl.Eq,
                is KatalystStageDsl.Gain,
                    -> pipeline.add(KatalystPassThroughStage())
            }
        }

        // The duck's writer is installed after the loop so that only the winning instance is ever
        // configured; its position among the writers is unobservable (every writer touches exactly
        // one stage), and the duck's list position is documented as ignored anyway.
        val theDuck = duck

        if (theDuck != null) {
            if (voiceDriven) {
                owners.add(KatalystOwnerApply { voice -> writeDuck(theDuck, voice.ducking, sampleRate) })
            } else {
                // The winning stage's slots, for the same reason: the dropped duplicate's are never read.
                val settings = duckStage?.let { KatalystSlots.duckSettings(it) }

                statics.add(KatalystStaticApply { writeDuck(theDuck, settings, sampleRate) })
            }
        }

        return KatalystChain(
            serial = pipeline.toTypedArray(),
            owners = owners.toTypedArray(),
            statics = statics.toTypedArray(),
            duck = theDuck,
            voiceDriven = voiceDriven,
            // The winning stage's slots, resolved once here: a declared chain that names no orbit
            // (or a depth of zero) declares a duck stage that will never be configured, and the
            // host has to be able to tell that from one that will (see [KatalystChain.ducksWith]).
            duckDeclared = !voiceDriven && duckStage?.let { KatalystSlots.duckSettings(it) } != null,
        )
    }

    /**
     * A declared send stage is on when its `wet` slot is a positive number: finite, above zero.
     * Off is expressed by handing the effect a non-finite time respectively size, so the ONE gate
     * stays inside the effect (where it also drains a live tail instead of freezing it) and this
     * function only decides whether the author asked for the stage at all.
     */
    private fun sendIsOn(wet: IgnitorDsl?, fallback: Double): Boolean {
        val value = KatalystSlots.resolve(wet, fallback)

        // NaN-guard on a value the author can write: a non-finite wet was never set, and an unset
        // send stage is off, the same reading the voice path gives an untouched effect.
        return value.isFinite() && value > 0.0
    }

    /**
     * Phaser: depth (the on/off + amount knob) is always written; the KERNEL params are written
     * only when the phaser is engaged. A no-phaser source must not zero the sweep CLOCK (ledger
     * D2, completed in review round 1): VoiceFactory defaults rate to 0.0, and a rate of 0 freezes
     * the LFO as surely as a skipped prepareBlock: the retained rate is what keeps the sweep on its
     * own timeline across owner handoffs, mirroring the delay's retained drain config. A source
     * that EXPLICITLY sets rate 0 with an engaged depth still gets its static notch: depth >= the
     * gate means its kernel params are written.
     *
     * Gate on the STORED depth, not the raw input: the setter silently rejects non-finite input,
     * and the two gates (this one and Phaser.process's) must never disagree about whether the
     * phaser is engaged (review round 2).
     *
     * One writer for both knob sources (the owner voice, a declared chain's slots), so the gate
     * cannot drift between them.
     */
    private fun writePhaser(
        fx: KatalystPhaserEffect,
        depth: Double,
        rate: Double,
        center: Double,
        sweep: Double,
        floor: Double,
    ) {
        fx.phaser.depth = depth

        if (fx.phaser.depth >= Phaser.MIN_ACTIVE_DEPTH) {
            fx.phaser.rate = rate
            fx.phaser.center = if (center > 0) center else PHASER_CENTER_HZ
            fx.phaser.sweep = if (sweep > 0) sweep else PHASER_SWEEP_HZ
            fx.phaser.floor = floor
            fx.phaser.feedback = 0.5
        }
    }

    /**
     * Compressor: reuse the instance to preserve the envelope follower across notes; clear it when
     * [settings] is null (nobody asks for a compressor), so it does not linger from a previous
     * owner or a previous chain.
     */
    private fun writeCompressor(fx: KatalystCompressorEffect, settings: Voice.Compressor?, sampleRate: Int) {
        if (settings != null) {
            val existing = fx.compressor

            if (existing == null) {
                fx.compressor = Compressor(
                    sampleRate = sampleRate,
                    thresholdDb = settings.thresholdDb,
                    ratio = settings.ratio,
                    kneeDb = settings.kneeDb,
                    attackSeconds = settings.attackSeconds,
                    releaseSeconds = settings.releaseSeconds,
                )
            } else {
                existing.thresholdDb = settings.thresholdDb
                existing.ratio = settings.ratio
                existing.kneeDb = settings.kneeDb
                existing.attackSeconds = settings.attackSeconds
                existing.releaseSeconds = settings.releaseSeconds
            }
        } else {
            fx.compressor = null
        }
    }

    /**
     * Duck / Sidechain: reuse the instance to preserve envelope state; clear when [settings] is
     * null (nobody asks for ducking).
     */
    private fun writeDuck(fx: KatalystDuckEffect, settings: Voice.Ducking?, sampleRate: Int) {
        if (fx.handedOver) {
            // The incoming chain of a running crossfade owns this envelope now
            // ([KatalystDuckEffect.takeOver]); writing here would build a second `Ducking` nobody
            // runs, and `reset()` below would undo the handover.
            return
        }

        if (settings != null) {
            fx.duckCylinderId = settings.cylinderId
            val existing = fx.ducking

            if (existing == null) {
                fx.ducking = Ducking(
                    sampleRate = sampleRate,
                    attackSeconds = settings.attackSeconds,
                    depth = settings.depth,
                )
            } else {
                existing.attackSeconds = settings.attackSeconds
                existing.depth = settings.depth
            }
        } else {
            fx.reset()
        }
    }
}
