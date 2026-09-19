/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.peekandpoke.klang.audio_bridge.KatalystDsl

/**
 * Writes a chain's own slots into ONE built stage, with no voice in sight: the chain is the
 * instrument (the signal-flow plan's D4).
 *
 * **The ONLY kind of writer there is** since Katalyst step 5b-1 (2026-09-19): the chain a cylinder
 * is born with reads the orbit's param state exactly as a declared one does, and the owner-voice
 * writers that read the bus FIELDS are gone. A chain can therefore be configured with NO OWNER
 * ALIVE (`KatalystChain.applyParams(null)`), which is what a chain faded in from `Cylinders`'
 * pending poll needs: the block's voices have already offered themselves by then, and without this
 * the chain would run its first blocks at the settings [KatalystChain.reset] left.
 *
 * **Two methods, because the orbit's param state changes far more rarely than a block goes by**
 * (Katalyst step 5a). [resolve] re-reads this stage's [KatalystKnob]s from the state and rebuilds
 * whatever composite the stage wants (a `FilterDef`, a `Voice.Compressor`); [apply] writes what is
 * already resolved into the stage and does no lookup and no allocation, so it can run on every
 * block. [KatalystChain.applyParams] is the one place that decides
 * which of the two a block needs.
 *
 * Stateful, and that IS the design: the resolved numbers have to live somewhere between the map
 * that produced them and the block that writes them, and the stage they belong to takes several of
 * them at once.
 */
internal interface KatalystSlotWriter {
    /** Re-read this stage's slots from the orbit's param state. Null = the authored defaults. */
    fun resolve(params: Map<String, Double>?)

    /** Write the resolved values into the stage. No map, no allocation, idempotent. */
    fun apply()
}

/**
 * A built orbit chain: the stage instances for one [KatalystDsl], in the DSL's order, plus the
 * lifecycle the hosting cylinder needs.
 *
 * Instances are **stateful** (delay ring, reverb network, compressor envelope, phaser sweep
 * clock), so a chain belongs to exactly one cylinder and is never shared. Building can rent from
 * the warehouse, so a chain is built ONCE per cylinder, never per block and never per voice (see
 * `Cylinder`). Mirror of [io.peekandpoke.klang.audio_be.master.MasterChain] on the orbit bus.
 *
 * **The duck is not in [pipeline].** It needs every orbit processed first, so `Cylinders` runs it
 * after the chains, which is what it has always done; the duck stage's position in the DSL list is
 * therefore ignored, and a chain that declares two keeps the last (decided 2026-09-17).
 *
 * Every knob comes from the orbit's param state through [applyParams], the chain a cylinder is
 * born with included (step 5b-1). What the DSL moved is WHERE the stage list lives: it is data
 * now, not a hardcoded `listOf(...)`.
 */
class KatalystChain internal constructor(
    /**
     * The stages that run on the orbit mix per block, in DSL order, as the array [process] walks.
     * The duck is deliberately absent (see the class KDoc); [stages] is the whole set the chain
     * owns.
     *
     * Array, not List, like `MasterChain`'s: iterated once per block per orbit, so no iterator
     * allocation. Private, because an array hands every reader a write: what leaves the chain is
     * the read-only [pipeline] view.
     */
    private val serial: Array<KatalystEffect>,
    /** One writer per declared stage, in DSL order, the duck's last. See [KatalystSlotWriter]. */
    private val statics: Array<KatalystSlotWriter>,
    /** The duck stage, or null when the chain declares none. The LAST declared duck wins. */
    val duck: KatalystDuckEffect?,
    /**
     * The writer of this chain's [duck] stage, or null when it declares none (see [ducksWith], the
     * one reader). Its `declared` flag is whether the stage currently resolves to settings, which
     * `.katp` can change while the chain runs.
     */
    private val duckWriter: KatalystDuckWriter?,
) {
    /**
     * The processing order as a read-only view, for the hosts and the specs: `List`, not the
     * array, so no caller can swap a stage out from under the render path. Created once here.
     */
    val pipeline: List<KatalystEffect> = serial.asList()

    /**
     * Every stage this chain owns, the duck included: the set the lifecycle covers. Built once
     * here, never on a render path, and always a COPY, so the lifecycle array and the processing
     * array can never alias (a chain without a duck would otherwise share one array with two
     * meanings, and the next reader to append to "the lifecycle set" would change the pipeline).
     */
    private val stages: Array<KatalystEffect> = if (duck == null) serial.copyOf() else serial + duck

    /**
     * Test seam: how many writers the build installed. The one way a spec can tell "the dropped
     * duplicate has no writer" from "it has one that nothing runs", which is what the last-duck
     * rule turns on.
     */
    internal val writerCount: Int get() = statics.size

    /**
     * The param state [statics] last resolved from, by REFERENCE: the gate of [applyParams]. Null
     * both before anything resolved (see [everResolved]) and after a resolve from no owner.
     */
    private var resolvedFrom: Map<String, Double>? = null

    /** False until the first [applyParams], so that resolving from a null state still happens. */
    private var everResolved: Boolean = false

    /** Test seam: how many times [applyParams] actually re-read the state. */
    internal var resolveCount: Int = 0
        private set

    // ════════════════════════════════════════════════════════════════════════════
    // Typed accessors
    // ════════════════════════════════════════════════════════════════════════════

    // The seven classic EFFECTS by name (the fader has no accessor: nothing asks a chain for its
    // gain stage), for the hosts and the specs that ask about ONE of them
    // (the warehouse specs about the rented ring and network, the diagnostics about denied rents).
    // Null when the chain declares no such stage: "the cylinder's delay" is a property of the
    // chain, not of the cylinder, which is exactly what this step makes true. A chain that
    // declares a kind twice reports the LAST one here, the same rule the duck follows, while BOTH
    // run and both are covered by the lifecycle (which goes through [stages], not through these).

    val body: KatalystBodyEffect? = serial.filterIsInstance<KatalystBodyEffect>().lastOrNull()

    val vowel: KatalystFormantEffect? = serial.filterIsInstance<KatalystFormantEffect>().lastOrNull()

    val delay: KatalystDelayEffect? = serial.filterIsInstance<KatalystDelayEffect>().lastOrNull()

    val reverb: KatalystReverbEffect? = serial.filterIsInstance<KatalystReverbEffect>().lastOrNull()

    val phaser: KatalystPhaserEffect? = serial.filterIsInstance<KatalystPhaserEffect>().lastOrNull()

    val compressor: KatalystCompressorEffect? = serial.filterIsInstance<KatalystCompressorEffect>().lastOrNull()

    /** Rents the warehouse refused any stage of this chain, for the diagnostics feedback. */
    val deniedRents: Int
        get() {
            var total = 0

            for (i in stages.indices) {
                total += stages[i].deniedRents
            }

            return total
        }

    // ════════════════════════════════════════════════════════════════════════════
    // Per block
    // ════════════════════════════════════════════════════════════════════════════

    /**
     * Apply EVERY stage of this chain from the orbit's param state ([params], the owner voice's
     * `katalystParams`). **Null is the no-owner door**: no state, so every slot resolves to what
     * the chain itself authored, which is what a chain entering service needs before any voice has
     * claimed the orbit's lease, and what an orbit whose owner has died falls back to.
     *
     * **This is the ONE way a bus knob reaches a stage** since step 5b-1, on a declared chain and
     * on the chain a cylinder is born with alike. The rule and its one home are the `katp` door's
     * KDoc in `sprudel/lang/lang_katalyst.kt`.
     *
     * Absent stages are turned OFF, so the state fully determines the orbit and nothing leaks from
     * a previous owner. The values are re-written every block and that is idempotent (body and
     * vowel short-circuit an unchanged config). The compressor and ducking INSTANCES are reused,
     * so their envelope followers survive across notes as long as consecutive owners keep the
     * effect: a takeover by a voice that asks for neither clears it (the compressor by gliding its
     * gain reduction to 0 dB first, see [KatalystCompressorEffect]), and the next owner that
     * re-adds it after that starts a fresh envelope.
     *
     * Stage by stage, in DSL order. The order is unobservable (each stage writes only its own
     * instance, and no stage reads another's params), which is why the historical
     * `applyBusEffects` could configure ducking before the compressor while the classic chain
     * declares them the other way round.
     *
     * **The re-resolve is gated on the map's IDENTITY, the apply is not** (Katalyst step 5a). A
     * live owner hands over the same map instance every block, so the lookups run once per owner
     * and the blocks in between cost exactly what step 3a's fixed writers cost: one virtual call
     * per stage, writing numbers that are already in hand. The values are re-WRITTEN every block
     * regardless, because that is what makes a writer idempotent after a [reset] the orbit reached
     * while the same voice was still holding the lease.
     *
     * A map is immutable by contract (`VoiceData.katalystParams`), so identity is a sound test for
     * "these are the same values"; a producer that mutated one in place would be breaking that
     * contract, not this gate. [everResolved] is what makes the FIRST call resolve even when there
     * is nothing to compare against.
     *
     * The reference is dropped by [reset] and [retire], so an idle chain pins no voice's map (the
     * allocation-cleanup rule).
     */
    fun applyParams(params: Map<String, Double>?) {
        resolveParams(params)

        for (i in statics.indices) {
            statics[i].apply()
        }
    }

    /**
     * The RESOLVE half of [applyParams], without the write: what a host needs when it has to ASK
     * this chain something that depends on its slots before its writers may run.
     *
     * The callers are the two swap paths (`Cylinder.beginFade` and the late-duck correction in
     * `Cylinder.updateFromVoice`), which ask [ducksWith] before a live envelope is handed over and
     * must NOT write the stages first: the carried envelope is updated in place by the arriving
     * chain's own writer AFTER the handover (see `writeDuck` and `KatalystDuckEffect.takeOver`).
     * Without this, a chain whose duck is named by `.katp("duck.orbit", n)` still reads as "no
     * duck" at the moment the question is asked, and the swap ramps the reduction out and then
     * drops a fresh one on the orbit a block later.
     *
     * Idempotent and gated exactly like [applyParams], so the [applyParams] that follows on the
     * same map instance costs nothing extra.
     */
    fun resolveParams(params: Map<String, Double>?) {
        if (everResolved && params === resolvedFrom) {
            return
        }

        everResolved = true
        resolvedFrom = params
        resolveCount++

        for (i in statics.indices) {
            statics[i].resolve(params)
        }
    }

    /** Runs the serial chain on the orbit's buffers, in DSL order. */
    fun process(ctx: KatalystContext) {
        for (i in serial.indices) {
            serial[i].process(ctx)
        }
    }

    /**
     * Runs the duck, which the host calls AFTER every orbit has been processed (the sidechain
     * source is another cylinder's mix). A chain without a duck stage does nothing here.
     */
    fun processDuck(ctx: KatalystContext) {
        duck?.process(ctx)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Lifecycle
    // ════════════════════════════════════════════════════════════════════════════

    /**
     * Turn every stage off AND clear its internal state: the clean slate a cylinder reaches when
     * its orbit deactivates (the lease is freed), so a reused orbit never replays a previous
     * owner's tail.
     */
    fun reset() {
        forgetParams()

        for (i in stages.indices) {
            stages[i].reset()
        }
    }

    /**
     * Hand this chain to the shelf: the rented units (delay ring, reverb network) go back to THEIR
     * shelves and the small stages clear.
     *
     * NOT [reset]: that would zero the ring and the network here, on the audio thread, and the
     * shelves zero them again on return (`Cylinder.retire`'s review round 3: sixteen warmup
     * cylinders retired in one block were ~19 MB of stores, twice). The units go back DIRTY and
     * the warehouse's housekeeping zeroes them a block at a time.
     */
    fun retire() {
        forgetParams()

        for (i in stages.indices) {
            stages[i].retire()
        }
    }

    /**
     * True when this chain's [duck] will be CONFIGURED the next time its writers run, rather than
     * cleared or left alone.
     *
     * A chain that declares a Duck stage does not necessarily duck: one whose `orbit` slot is unset
     * resolves to no settings at all. The host asks this before it hands a live envelope to an
     * ARRIVING chain (`Cylinder.handOverDuck`): handing one to a stage that is about to be cleared
     * releases the whole reduction in one sample.
     *
     * The answer is its duck writer's, on every chain.
     */
    fun ducksWith(): Boolean = duckWriter?.declared == true

    /**
     * Drops the param state this chain resolved from and puts its writers back to what the chain
     * itself authored: a chain that is not in service holds no voice's map (the allocation-cleanup
     * rule), and it must not REMEMBER one either.
     *
     * The remembering is the sharp edge. A cached chain comes back into service through
     * `Cylinder.beginFade`, which asks [ducksWith] BEFORE the arriving chain's writers have run:
     * a duck writer still holding a previous owner's `duck.orbit` would answer "this chain ducks",
     * be handed the live envelope, and then never be configured, so the orbit would keep ducking
     * off a stage its chain no longer declares.
     *
     * Free in the common case: a chain that resolved from no state has nothing to undo.
     */
    private fun forgetParams() {
        if (resolvedFrom == null) {
            return
        }

        resolvedFrom = null
        resolveCount++

        for (i in statics.indices) {
            statics[i].resolve(null)
        }
    }

    /**
     * True while ANY stage still holds audible energy, asked before an orbit is deactivated so no
     * delay echo or reverb tail is cut. A compare, not a scan (see `TailCeiling`).
     */
    fun hasTail(): Boolean {
        for (i in stages.indices) {
            if (stages[i].hasTail()) {
                return true
            }
        }

        return false
    }
}
