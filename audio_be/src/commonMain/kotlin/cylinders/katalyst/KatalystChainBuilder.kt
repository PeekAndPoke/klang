/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.peekandpoke.klang.audio_be.effects.Compressor
import io.peekandpoke.klang.audio_be.effects.Ducking
import io.peekandpoke.klang.audio_be.effects.Phaser
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.warehouse.ReverbUnits
import io.peekandpoke.klang.audio_be.warehouse.SizedBuffers
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import io.peekandpoke.klang.audio_bridge.constants.PHASER_CENTER_HZ
import io.peekandpoke.klang.audio_bridge.constants.PHASER_SWEEP_HZ

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
 * **Step 2 reads no knob from the DSL.** Every stage still takes its parameters from the orbit's
 * owner voice, through the [KatalystOwnerApply] this builder installs beside it, which is
 * `Cylinder.applyBusEffects` moved here line for line so the engine stays byte-identical. Step 3
 * replaces those with the per-stage slot resolver (`docs/tasks/katalyst-dsl.md` §7) and the DSL's
 * values start to matter.
 *
 * **A chain that declares two ducks keeps the LAST one** (decided with the maintainer,
 * 2026-09-17): the cylinder runs exactly one ducking effect, so the earlier declarations are
 * silently dropped rather than summed. Every other kind may appear more than once and every
 * instance runs, at its own position.
 */
object KatalystChainBuilder {

    /**
     * Builds the chain for [dsl]. Call once per cylinder (see `Cylinder`), never per block: the
     * stage instances own the orbit's DSP state, and building them is where the allocation lives.
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
    ): KatalystChain {
        val pipeline = mutableListOf<KatalystEffect>()
        val owners = mutableListOf<KatalystOwnerApply>()
        var duck: KatalystDuckEffect? = null

        for (stage in dsl.stages) {
            when (stage) {
                // Body / vowel resonators, timbre shapers of the whole orbit (moved off the
                // per-voice chain). In the classic chain they run first, so they color the dry
                // mix before the time/dynamics effects.
                is KatalystStageDsl.Body -> {
                    val fx = KatalystBodyEffect(sampleRate.toDouble())
                    pipeline.add(fx)
                    // null (the owner has no body) turns the resonator off, not a no-op.
                    owners.add(KatalystOwnerApply { voice -> fx.configure(voice.body) })
                }

                is KatalystStageDsl.Vowel -> {
                    val fx = KatalystFormantEffect(sampleRate.toDouble())
                    pipeline.add(fx)
                    owners.add(KatalystOwnerApply { voice -> fx.configure(voice.vowel) })
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
                    owners.add(
                        KatalystOwnerApply { voice ->
                            fx.configure(
                                time = voice.delay.time,
                                feedback = voice.delay.feedback,
                                cap = voice.delay.cap,
                            )
                        }
                    )
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
                    owners.add(
                        KatalystOwnerApply { voice ->
                            fx.configure(
                                size = voice.reverb.size,
                                lowpass = voice.reverb.lowpass,
                            )
                        }
                    )
                }

                is KatalystStageDsl.Phaser -> {
                    val fx = KatalystPhaserEffect(
                        phaser = Phaser(sampleRate),
                    )
                    pipeline.add(fx)
                    owners.add(KatalystOwnerApply { voice -> applyPhaser(fx, voice) })
                }

                is KatalystStageDsl.Compressor -> {
                    val fx = KatalystCompressorEffect()
                    pipeline.add(fx)
                    owners.add(KatalystOwnerApply { voice -> applyCompressor(fx, voice, sampleRate) })
                }

                // Declared in the list, run outside it: `Cylinders` applies it after every orbit,
                // because it needs cross-orbit access to the sidechain source. The last
                // declaration wins, so a second Duck stage replaces the first, instance and all.
                is KatalystStageDsl.Duck -> {
                    duck = KatalystDuckEffect()
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
            owners.add(KatalystOwnerApply { voice -> applyDuck(theDuck, voice, sampleRate) })
        }

        return KatalystChain(
            serial = pipeline.toTypedArray(),
            owners = owners.toTypedArray(),
            duck = theDuck,
        )
    }

    /**
     * Phaser: depth (the on/off + amount knob) is always the owner's; the KERNEL params are
     * written only by an owner whose phaser is engaged. A no-phaser owner must not zero the sweep
     * CLOCK (ledger D2, completed in review round 1): VoiceFactory defaults rate to 0.0, and a
     * rate of 0 freezes the LFO as surely as a skipped prepareBlock: the retained rate is what
     * keeps the sweep on its own timeline across owner handoffs, mirroring the delay's retained
     * drain config. An owner that EXPLICITLY sets rate 0 with an engaged depth still gets its
     * static notch: depth >= the gate means its kernel params are written.
     *
     * Gate on the STORED depth, not the raw voice value: the setter silently rejects non-finite
     * input, and the two gates (this one and Phaser.process's) must never disagree about whether
     * the phaser is engaged (review round 2).
     */
    private fun applyPhaser(fx: KatalystPhaserEffect, voice: Voice) {
        fx.phaser.depth = voice.phaser.depth

        if (fx.phaser.depth >= Phaser.MIN_ACTIVE_DEPTH) {
            fx.phaser.rate = voice.phaser.rate
            fx.phaser.center = if (voice.phaser.center > 0) voice.phaser.center else PHASER_CENTER_HZ
            fx.phaser.sweep = if (voice.phaser.sweep > 0) voice.phaser.sweep else PHASER_SWEEP_HZ
            fx.phaser.floor = voice.phaser.floor
            fx.phaser.feedback = 0.5
        }
    }

    /**
     * Compressor: reuse the instance to preserve the envelope follower across notes; clear it when
     * the owner has no compressor (so it doesn't linger from a previous owner).
     */
    private fun applyCompressor(fx: KatalystCompressorEffect, voice: Voice, sampleRate: Int) {
        val settings = voice.compressor

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
     * Duck / Sidechain: reuse the instance to preserve envelope state; clear when the owner has none.
     */
    private fun applyDuck(fx: KatalystDuckEffect, voice: Voice, sampleRate: Int) {
        val settings = voice.ducking

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
