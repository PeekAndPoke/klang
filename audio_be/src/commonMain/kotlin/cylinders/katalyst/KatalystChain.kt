/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.constants.SLOT_UNSET

/**
 * Writes the orbit's OWNER voice into ONE built stage.
 *
 * Transitional by design. Step 2 of the Katalyst work (2026-09-17) builds the chain from the DSL
 * but still reads every knob off the owner voice, exactly as `Cylinder.applyBusEffects` did, so
 * the engine stays byte-identical. Step 3 replaces these with the per-stage slot resolver of
 * `docs/tasks/katalyst-dsl.md` §7, at which point the owner voice stops being a knob source and
 * this interface goes with it.
 *
 * Stateless on purpose: the instance [KatalystChainBuilder] installs here captures the stage and
 * the sample rate, both `val`, and every bit of mutable state stays inside the effect class (the
 * house rule on SAM lambdas, `audio/ref/performance.md`). One instance per stage, built once, so
 * the per-block cost is one virtual call per stage and no dispatch and no allocation.
 */
internal fun interface KatalystOwnerApply {
    fun apply(voice: Voice)
}

/**
 * Writes a DECLARED chain's own slots into ONE built stage, with no voice in sight: the chain is
 * the instrument (the signal-flow plan's D4).
 *
 * Separate from [KatalystOwnerApply] so that a chain can be configured with NO OWNER ALIVE
 * ([KatalystChain.applyStatic]), which is what a chain faded in from `Cylinders`' pending poll
 * needs: the block's voices have already offered themselves by then, and without this the declared
 * chain would run its first blocks at the settings [KatalystChain.reset] left. Transitional with
 * its sibling: step 5 takes the bus fields off the wire, and then every writer is one of these.
 */
internal fun interface KatalystStaticApply {
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
 * Step 2 is the mirror: the chain is built from [KatalystDsl.classic] and every knob still comes
 * from the owner voice through [applyOwner], so nothing sounds different. What moved is WHERE the
 * stage list lives: it is data now, not a hardcoded `listOf(...)`.
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
    /**
     * One writer per declared stage whose knobs a stage CONSUMES, in DSL order, the duck's last.
     * `eq` and `gain` do have knobs, but no stage reads them yet (they build a pass-through), so
     * they get no writer and this array is shorter than [stages] for a chain that declares them.
     *
     * The VOICE-driven half, so a chain is either all of these (the classic chain) or all
     * [statics] (a declared chain); the builder's `voiceDriven` flag decides which.
     */
    private val owners: Array<KatalystOwnerApply>,
    /** The SLOT-driven half, same rule, per declared stage. See [KatalystStaticApply]. */
    private val statics: Array<KatalystStaticApply>,
    /** The duck stage, or null when the chain declares none. The LAST declared duck wins. */
    val duck: KatalystDuckEffect?,
    /**
     * True when this chain's writers read the owner voice (the classic chain), false when they
     * resolve the chain's own slots. See [KatalystStaticApply] and [ducksWith].
     */
    private val voiceDriven: Boolean,
    /**
     * For a SLOT-driven chain: whether its [duck] stage resolved to settings at build time. False
     * for a chain that declares no duck, and for one whose `orbit` or `depth` slots say "off"
     * (`KatalystSlots.duckSettings` returns null). Meaningless for a voice-driven chain, whose
     * owner decides; [ducksWith] is the one reader.
     */
    private val duckDeclared: Boolean,
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
     * Test seam: how many writers the build installed, voice-driven and slot-driven together. The
     * one way a spec can tell "the dropped duplicate has no writer" from "it has one that nothing
     * runs", which is what the last-duck rule turns on.
     */
    internal val writerCount: Int get() = owners.size + statics.size

    // ════════════════════════════════════════════════════════════════════════════
    // Typed accessors
    // ════════════════════════════════════════════════════════════════════════════

    // The seven classic effects by name, for the hosts and the specs that ask about ONE of them
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
     * Apply ALL of this chain's stages from the orbit's owning [voice]. Absent effects are turned
     * off, so the owner's config fully determines the orbit and nothing leaks from a previous
     * owner. The owner re-applies every block; this is idempotent (body/vowel short-circuit on an
     * unchanged config). The compressor/ducking instances are reused, so their envelope followers
     * survive across notes AS LONG AS consecutive owners keep the effect: a takeover by a voice
     * that has no compressor/ducking clears it, and the next owner that re-adds it starts a fresh
     * envelope.
     *
     * Stage by stage, in DSL order. The order is unobservable (each stage writes only its own
     * instance, and no stage reads another's params), which is why the historical
     * `applyBusEffects` could configure ducking before the compressor while the classic chain
     * declares them the other way round.
     */
    fun applyOwner(voice: Voice) {
        for (i in owners.indices) {
            owners[i].apply(voice)
        }

        applyStatic()
    }

    /**
     * Apply every SLOT-driven stage of this chain, with no owner voice: what a declared chain needs
     * the moment it enters service, because nothing else will configure it until a voice claims the
     * orbit's lease (see [KatalystStaticApply]).
     *
     * A no-op on the classic chain, whose writers all need the voice. Idempotent, like
     * [applyOwner], so the hosts may call it on any entry path.
     */
    fun applyStatic() {
        for (i in statics.indices) {
            statics[i].apply()
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
        for (i in stages.indices) {
            stages[i].retire()
        }
    }

    /**
     * True when this chain's [duck] will be CONFIGURED the next time its writers run, rather than
     * cleared or left alone.
     *
     * A chain that declares a Duck stage does not necessarily duck: the classic chain declares one
     * on every cylinder and its writer CLEARS it when the orbit's owner carries no ducking, and a
     * declared chain whose `orbit` slot is unset resolves to no settings at all. The host asks this
     * before it hands a live envelope to an arriving chain (`Cylinder.handOverDuck`): handing one
     * to a stage that is about to be cleared releases the whole reduction in one sample.
     *
     * [ownerDucks] is the answer for a voice-driven chain, which the host knows and this chain does
     * not: does the voice holding the orbit's lease carry ducking (and is one alive at all)?
     */
    fun ducksWith(ownerDucks: Boolean): Boolean {
        if (duck == null) {
            return false
        }

        return if (voiceDriven) ownerDucks else duckDeclared
    }

    /**
     * Hands every send stage its off-config, which is how this chain is asked to RING OUT: the
     * delay and the reverb enter their existing Draining state and run their already-scheduled
     * echoes and room out under the parameters they last had, on whatever input they are then
     * given (see `KatalystDelayEffect`, `KatalystReverbEffect`).
     *
     * Called by the host when a crossfade to another chain has completed and this one leaves
     * service: `Cylinder` then keeps processing it on SILENT input until [hasTail] is false.
     * Nothing here invents a decay curve; the two effects already own one, which is the whole
     * reason the orbit bus drains where the master bus cuts.
     *
     * The off-config is ALL that happens: the insert stages keep whatever filter memory they
     * hold, because the signal that charged it has just reached weight zero.
     */
    fun drainSends() {
        for (i in stages.indices) {
            when (val stage = stages[i]) {
                // A non-finite time / size is the off switch; the drain runs on the retained
                // last-active parameters, so the other arguments are unset rather than invented.
                is KatalystDelayEffect -> stage.configure(
                    time = SLOT_UNSET,
                    feedback = SLOT_UNSET,
                    cap = SLOT_UNSET,
                )

                is KatalystReverbEffect -> stage.configure(size = SLOT_UNSET, lowpass = null)

                else -> {}
            }
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
