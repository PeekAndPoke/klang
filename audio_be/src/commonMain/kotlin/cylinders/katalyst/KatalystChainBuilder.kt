/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.peekandpoke.klang.audio_be.effects.Compressor
import io.peekandpoke.klang.audio_be.effects.Ducking
import io.peekandpoke.klang.audio_be.effects.Phaser
import io.peekandpoke.klang.audio_be.filters.EqSectionSpec
import io.peekandpoke.klang.audio_be.filters.eqSectionSpec
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.warehouse.ReverbUnits
import io.peekandpoke.klang.audio_be.warehouse.SizedBuffers
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import io.peekandpoke.klang.audio_bridge.constants.BODY_FLOOR
import io.peekandpoke.klang.audio_bridge.constants.BODY_WET
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_ATTACK_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_KNEE_DB
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_RATIO
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_RELEASE_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_THRESHOLD_DB
import io.peekandpoke.klang.audio_bridge.constants.DELAY_CAP
import io.peekandpoke.klang.audio_bridge.constants.DELAY_FEEDBACK
import io.peekandpoke.klang.audio_bridge.constants.DELAY_TIME_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.DELAY_WET
import io.peekandpoke.klang.audio_bridge.constants.DUCK_ATTACK_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.DUCK_DEPTH
import io.peekandpoke.klang.audio_bridge.constants.PHASER_CENTER_HZ
import io.peekandpoke.klang.audio_bridge.constants.PHASER_FLOOR
import io.peekandpoke.klang.audio_bridge.constants.PHASER_RATE_HZ
import io.peekandpoke.klang.audio_bridge.constants.PHASER_SWEEP_HZ
import io.peekandpoke.klang.audio_bridge.constants.PHASER_WET
import io.peekandpoke.klang.audio_bridge.constants.REVERB_SIZE
import io.peekandpoke.klang.audio_bridge.constants.REVERB_WET
import io.peekandpoke.klang.audio_bridge.constants.SLOT_UNSET
import io.peekandpoke.klang.audio_bridge.constants.VOWEL_FLOOR
import io.peekandpoke.klang.audio_bridge.constants.VOWEL_WET

/**
 * Builds the [KatalystChain] for one [KatalystDsl]: one exhaustive `when` over
 * [KatalystStageDsl], one stage instance per declared stage, in the declared order.
 *
 * The seven classic effects are the stage instances for their variants, **unchanged** (the shells
 * over the shared DSP in `audio_be/effects/`), with the constructor arguments the cylinder used to
 * pass them: the delay rents its ring from [SizedBuffers] and the reverb its network from
 * [ReverbUnits], both on first activation and never here, so building a chain allocates no ring
 * and no network. Since 2026-09-19 the classic chain declares a `Gain` at unity, so every cylinder
 * has one, the born-with chain included: bit-transparent at unity and reachable with
 * `katp("gain.gain", x)` (signal-flow plan section 6, spot C).
 *
 * **One kind of writer, on every chain** (Katalyst step 5b-1, 2026-09-19): every knob of every
 * stage comes from the chain's own slots ([KatalystKnob] over [KatalystSlots]), resolved here and
 * re-resolved only when the orbit's param state changes. That state is the owner voice's
 * `katalystParams`, which `.katp` and the bus doors write (step 5a), and it is the ONE way a bus
 * knob reaches a stage, for a declared chain and for the chain a cylinder is born with alike. The
 * voice's bus FIELDS stopped being a knob source then (the signal-flow plan §7, D4: the chain is
 * the instrument) and left the wire in step 5b-3. The `voiceDriven` flag and the owner writers it
 * chose went with step 5b-1.
 *
 * A writer has TWO halves, `resolve` and `apply`, and the split is about the per-block cost: the
 * orbit's param state changes far more rarely than a block goes by, so `resolve` does the lookups
 * and allocates whatever composite a stage wants, and `apply` writes numbers that are already in
 * hand (see [KatalystSlotWriter]). `applyParams(null)` is how a chain is configured with no owner
 * voice alive, which is what a chain faded in from the pending poll needs.
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
    ): KatalystChain {
        val pipeline = mutableListOf<KatalystEffect>()
        val statics = mutableListOf<KatalystSlotWriter>()
        var duck: KatalystDuckEffect? = null
        var duckStage: KatalystStageDsl.Duck? = null

        for (stage in dsl.stages) {
            when (stage) {
                // Body / vowel resonators, timbre shapers of the whole orbit (moved off the
                // per-voice chain). In the classic chain they run first, so they color the dry
                // mix before the time/dynamics effects.
                is KatalystStageDsl.Body -> {
                    val fx = KatalystBodyEffect(sampleRate.toDouble(), blockFrames)
                    pipeline.add(fx)

                    // The material is a slot like the rest, holding the INDEX of a name in
                    // `BodyMaterials.names` (Katalyst step 5a-2). Its fallback is the wire's
                    // "never set", so a knob the bus cannot read leaves the stage off rather
                    // than picking a box nobody asked for.
                    statics.add(
                        KatalystBodyWriter(
                            fx = fx,
                            material = KatalystKnob(stage.material, SLOT_UNSET),
                            wet = KatalystKnob(stage.wet, BODY_WET),
                            floor = KatalystKnob(stage.floor, BODY_FLOOR),
                        )
                    )
                }

                is KatalystStageDsl.Vowel -> {
                    val fx = KatalystFormantEffect(sampleRate.toDouble(), blockFrames)
                    pipeline.add(fx)

                    statics.add(
                        KatalystVowelWriter(
                            fx = fx,
                            vowel = KatalystKnob(stage.vowel, SLOT_UNSET),
                            wet = KatalystKnob(stage.wet, VOWEL_WET),
                            floor = KatalystKnob(stage.floor, VOWEL_FLOOR),
                        )
                    )
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
                    //
                    // `delay.wet` is how much of the orbit mix feeds the line (step 5b-2). An
                    // AUTHORED `wet(0.0)` rents no ring (decided 2026-09-17, consistent with the
                    // phaser gated on depth and the duck on orbit), while a wet a PATTERN wrote runs
                    // the stage whatever its value. The one home of that rule is [sendStageRuns].
                    statics.add(
                        KatalystDelayWriter(
                            fx = fx,
                            wet = KatalystKnob(stage.wet, DELAY_WET),
                            time = KatalystKnob(stage.time, DELAY_TIME_SECONDS),
                            feedback = KatalystKnob(stage.feedback, DELAY_FEEDBACK),
                            cap = KatalystKnob(stage.cap, DELAY_CAP),
                        )
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

                    // Routed through the effect's lifecycle like the delay: an off-config drains
                    // the tail out on its own timeline instead of freezing the combs (see
                    // KatalystReverbEffect). The slot carries the AUTHORED 0..10 size, so the writer
                    // passes it through the one shared conversion, and configure bounds it again
                    // at the door. `reverb.wet` is how much of the orbit mix feeds the room, and
                    // whether the stage runs is [sendStageRuns], as on the delay above.
                    statics.add(
                        KatalystReverbWriter(
                            fx = fx,
                            wet = KatalystKnob(stage.wet, REVERB_WET),
                            size = KatalystKnob(stage.size, REVERB_SIZE),
                            lowpass = KatalystKnob(stage.lowpass, SLOT_UNSET),
                        )
                    )
                }

                is KatalystStageDsl.Phaser -> {
                    val fx = KatalystPhaserEffect(
                        phaser = Phaser(sampleRate),
                    )
                    pipeline.add(fx)

                    statics.add(
                        KatalystPhaserWriter(
                            fx = fx,
                            wet = KatalystKnob(stage.wet, PHASER_WET),
                            rate = KatalystKnob(stage.rate, PHASER_RATE_HZ),
                            center = KatalystKnob(stage.center, PHASER_CENTER_HZ),
                            sweep = KatalystKnob(stage.sweep, PHASER_SWEEP_HZ),
                            floor = KatalystKnob(stage.floor, PHASER_FLOOR),
                        )
                    )
                }

                is KatalystStageDsl.Compressor -> {
                    val fx = KatalystCompressorEffect()
                    pipeline.add(fx)

                    statics.add(
                        KatalystCompressorWriter(
                            fx = fx,
                            sampleRate = sampleRate,
                            threshold = KatalystKnob(stage.threshold, COMPRESSOR_THRESHOLD_DB),
                            ratio = KatalystKnob(stage.ratio, COMPRESSOR_RATIO),
                            knee = KatalystKnob(stage.knee, COMPRESSOR_KNEE_DB),
                            attack = KatalystKnob(stage.attack, COMPRESSOR_ATTACK_SECONDS),
                            release = KatalystKnob(stage.release, COMPRESSOR_RELEASE_SECONDS),
                        )
                    )
                }

                // Declared in the list, run outside it: `Cylinders` applies it after every orbit,
                // because it needs cross-orbit access to the sidechain source. The last
                // declaration wins, so a second Duck stage replaces the first, instance and all.
                is KatalystStageDsl.Duck -> {
                    duck = KatalystDuckEffect()
                    duckStage = stage
                }

                // The mix EQ: one stage, the declared section list, over two EqCore banks. Its
                // STRUCTURE (how many sections, of which type) is the declaration's; only the
                // coefficient scalars are knobs.
                is KatalystStageDsl.Eq -> {
                    val specs = stage.sections.map { eqSectionSpec(it) }
                    val fx = KatalystEqEffect(
                        sampleRate = sampleRate.toDouble(),
                        types = IntArray(specs.size) { specs[it].type },
                        blockFrames = blockFrames,
                    )
                    pipeline.add(fx)

                    // The classic chain declares no `eq`; it does declare a `gain` at unity since
                    // 2026-09-19, which is bit-transparent and is what let `katp("gain.gain", x)`
                    // reach the fader of an orbit that declares nothing one step before every
                    // other knob could.
                    statics.add(KatalystEqWriter(fx = fx, knobs = eqKnobs(specs)))
                }

                // The group fader, after the inserts (the signal-flow plan's D5).
                is KatalystStageDsl.Gain -> {
                    val fx = KatalystGainEffect()
                    pipeline.add(fx)

                    // Unity is the identity element of the stage, not a tuned value, which is why
                    // it is a literal here and in `MasterStageDsl.Gain` rather than a shared
                    // constant (the wire KDoc says so).
                    statics.add(KatalystGainWriter(fx = fx, gain = KatalystKnob(stage.gain, 1.0)))
                }
            }
        }

        // The duck's writer is installed after the loop so that only the winning instance is ever
        // configured; its position among the writers is unobservable (every writer touches exactly
        // one stage), and the duck's list position is documented as ignored anyway.
        val theDuck = duck
        var duckWriter: KatalystDuckWriter? = null

        if (theDuck != null) {
            // The winning stage's slots, for the same reason: the dropped duplicate's are never read.
            val winner = duckStage ?: KatalystStageDsl.Duck()

            duckWriter = KatalystDuckWriter(
                fx = theDuck,
                sampleRate = sampleRate,
                orbit = KatalystKnob(winner.orbit, SLOT_UNSET),
                depth = KatalystKnob(winner.depth, DUCK_DEPTH),
                attack = KatalystKnob(winner.attack, DUCK_ATTACK_SECONDS),
            )

            statics.add(duckWriter)
        }

        return KatalystChain(
            serial = pipeline.toTypedArray(),
            statics = statics.toTypedArray(),
            duck = theDuck,
            // The duck's own writer answers whether the stage will be configured: a chain that
            // names no orbit (or a depth of zero) declares a duck that will never be configured,
            // and the host has to be able to tell that from one that will (see
            // [KatalystChain.ducksWith]). A writer, not a build-time flag, because `.katp` can
            // name the orbit after the chain was built.
            duckWriter = duckWriter,
        )
    }

    /**
     * One knob per section param, flat, in the layout [KatalystEqEffect.configure] reads: four
     * slots per section, the ones the section's type does not have left null.
     *
     * The fallback is [SLOT_UNSET] rather than a number, and that is the one place this stage
     * departs from "an unreadable knob takes its shared wire constant": a section's defaults live
     * on its own wire variant (a lowpass cuts at 2000, a bell sits at 1000) and are not shared
     * constants, so copying them here would be two spellings of one default. Unset is safe: the
     * coefficient helpers take a non-finite freq to 1000 Hz, a non-finite q to 0.707 and a
     * non-finite db to a transparent bell, so nothing here can put a NaN in the mix.
     *
     * The ASYMMETRY that buys is worth stating plainly, because it is audible: a knob the bus
     * cannot read is one a VOICE can. `lowpass(freq = sine.range(400, 800))` renders around its
     * center per voice; on a bus the same section lands on the helper's fallback, 1000 Hz, which
     * is neither the author's center nor the wire default of 2000. The alternative (the wire
     * default) would only move the wrong number, and the right fix is not a better fallback but a
     * `katp` pattern, which is what a moving bus knob IS. Recorded here so nobody reads the
     * fallback as the declared default.
     */
    private fun eqKnobs(specs: List<EqSectionSpec>): Array<KatalystKnob?> {
        val knobs = arrayOfNulls<KatalystKnob>(specs.size * KatalystEqEffect.KNOBS_PER_SECTION)

        for (i in specs.indices) {
            val spec = specs[i]
            val base = i * KatalystEqEffect.KNOBS_PER_SECTION

            knobs[base + KatalystEqEffect.KNOB_FREQ] = KatalystKnob(spec.freq, SLOT_UNSET)
            knobs[base + KatalystEqEffect.KNOB_Q] = KatalystKnob(spec.q, SLOT_UNSET)
            knobs[base + KatalystEqEffect.KNOB_DB] = spec.db?.let { KatalystKnob(it, SLOT_UNSET) }
            knobs[base + KatalystEqEffect.KNOB_GAIN] = spec.gain?.let { KatalystKnob(it, SLOT_UNSET) }
        }

        return knobs
    }
}

/**
 * Whether the delay or the reverb stage RUNS, which is a different question from how much of the
 * orbit mix feeds it (that is `wet` itself, since Katalyst step 5b-2).
 *
 * The stage runs when the pattern WROTE this knob (any finite value, zero and negative included:
 * [KatalystKnob.written]) or when what the chain itself authored is a positive amount.
 *
 * **Why a written 0 keeps the stage running** (step 5b-1's "touched" rule, kept by the 5b-2
 * decision): a written wet is a LEVEL, and a level glides. A pattern that moves the wet to 0 and
 * back (`reverb(wet = "0.4 0")`, `delay(wet = "0.3 0")`) fades the feed down and up again while
 * the room or the ring keeps ringing out what it holds; switching the stage off at 0 instead would start a drain
 * and snap the feed back on at the next nonzero. A running stage fed nothing costs its network's
 * processing, which it spends anyway while it rings.
 *
 * The second half keeps the decision of 2026-09-17 for authored constants: a declared
 * `k.reverb(r => r.wet(0.0).size(6))` that no pattern touches still rents nothing, because nobody
 * asked for a room, only for a chain that has one.
 *
 * Off is expressed by handing the effect a non-finite time respectively size, so the ONE threshold
 * stays inside the effect (where it also drains a live tail instead of freezing it) and this
 * function only decides whether anybody asked for the stage at all.
 */
internal fun sendStageRuns(wet: KatalystKnob): Boolean {
    // NaN-guard on a value the author can write: a non-finite wet was never set, so it is neither
    // a write nor a positive amount, and an unset stage is off. That is the same reading the voice
    // path gave an untouched effect.
    return wet.written || (wet.value.isFinite() && wet.value > 0.0)
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
 * A free function and not a method on the effect, because the gate and the kernel-param rule belong
 * together in one readable place and the effect is a thin shell over `Phaser` (see the class KDoc).
 */
internal fun writePhaser(
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
internal fun writeCompressor(fx: KatalystCompressorEffect, settings: Voice.Compressor?, sampleRate: Int) {
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
internal fun writeDuck(fx: KatalystDuckEffect, settings: Voice.Ducking?, sampleRate: Int) {
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
