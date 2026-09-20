/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.peekandpoke.klang.audio_be.AudioBackendContext
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.filters.AudioFilter
import io.peekandpoke.klang.audio_be.filters.EqCore

/**
 * Orbit-level **mix equalizer**: the section list of a declared `eq` stage, run once on the summed
 * stereo mix instead of once per voice.
 *
 * The DSP is the shared [EqCore], the same core the per-voice `EqIgnitor` drives, and the section
 * types come from the same `eqSectionSpec` mapping, so `band(freq = 300, q = 0.8, db = 2)` means
 * one curve on a voice and on a bus. The core is MONO, so this stage keeps one instance per
 * channel with independent integrator state.
 *
 * **Where it sits is what it does.** The stage processes `ctx.mixBuffer` at its list position and
 * nothing else. The delay and the reverb are fed from that mix at THEIR position (Katalyst step
 * 5b-2), so an `eq` written BEFORE `reverb` shapes the dry mix and what the room hears, and the
 * same `eq` written AFTER it shapes dry and tail together. Both are legitimate mixes and the list
 * order is how they are told apart.
 *
 * **A configuration change does not click.** [EqCore] is SNAP-only by contract (its own KDoc: a
 * per-block coefficient change on a bus is a click), so the smoothing is built here, and it is the
 * house precedent rather than a new one: [KatalystFilterSwap], the same switch `body(...)` and
 * `vowel(...)` go through, crossfades from what sounds now to the new bank over
 * `BANK_CROSSFADE_SECONDS`, and a change arriving mid-fade waits instead of dropping one.
 * The core's `process(buffer, offset, length)` IS the [AudioFilter] signature the swap wants, so
 * the adaptation is one delegating call per channel per block and nothing at all per sample. The
 * alternative, interpolating the five coefficients per sample inside the stage, would have been a
 * second smoothing policy in the engine for a stage that had a working one available. There is no
 * off door: the stage exists only on a declared chain, and a chain swap already crossfades.
 *
 * **TWO pre-built banks and one parking slot** (Katalyst step 5c-11, 2026-09-20). The swap carries
 * two banks and no more, so the arriving configuration takes the one the swap does not hold
 * ([KatalystFilterSwap.holds]), which is by definition not heard, and zeroes it before it is
 * reconfigured, which makes it exactly the fresh instance the body and the vowel build on every
 * change. A change that arrives while a fade runs is not given a bank at all: its SCALARS wait in
 * [parkedValues] until the fade lands, and a further change overwrites them (latest wins), so a
 * `.katp` burst costs one `install` and not one per event. The banks can be pre-built because an
 * EQ's section count and types are fixed by the DECLARATION (structure is declared once, the
 * signal-flow plan's rule 2), where a material decides how many bands a body has. That matters
 * because a `.katp` on an EQ knob is a change per event: the cost of one is a `reset` plus one
 * coefficient computation per section per channel (a `tan` each) plus one fade time of one more
 * bank running, and it is bounded at one per BLOCK, because the orbit's param state is re-read
 * once per block at most. Nothing is allocated and nothing is rebuilt on a block that changes no
 * knob, which is the normal case and the one the `installs` counter pins. A change allocates
 * nothing at all: the banks are built with the stage, and [KatalystFilterSwap] sizes its scratch
 * buffers for `blockFrames` at construction.
 *
 * A [EqCore.BELL] section at `db == 0.0` stays BIT-TRANSPARENT through the core's explicit
 * passthrough branch, and this stage deliberately does NOT take the per-voice adapter's
 * `disableSection` shortcut for it: that zeroes the section's state, and on a bus a `db` can be
 * moved off zero by `.katp` between two blocks, where the running A = 1 recurrence is what keeps
 * the move click-free.
 *
 * Every knob reaches the core RAW. The coefficient helpers own the clamps (`bilinearK` takes a
 * non-finite cutoff to 1000 Hz, `computeSvfCoeffs` a non-finite q to 0.707, the bell a non-finite
 * db to a transparent 0 dB, and a non-finite tap gain leaves `safeOut` adding nothing), and the
 * parity contract is that the surface adds none of its own: a guard here would make the same
 * number sound different on a bus than on a voice.
 */
class KatalystEqEffect(
    private val sampleRate: Double,
    /**
     * The [EqCore] section type per declared section, in list order. Structure, so it is never a
     * slot: a section's KIND is the declaration's, only its coefficient scalars can move.
     */
    private val types: IntArray,
    /** The frames of one render block; sizes the swap's scratch at construction (see [KatalystFilterSwap]). */
    blockFrames: Int = AudioBackendContext.RENDER_QUANTUM_FRAMES,
) : KatalystEffect {

    companion object {
        /**
         * The scalars one section is configured from, in the order [KNOB_FREQ], [KNOB_Q],
         * [KNOB_DB], [KNOB_GAIN]. [configure] takes them flat, `KNOBS_PER_SECTION` per section, in
         * list order, because the writer that fills them holds a flat array of knobs and a block
         * that changes nothing then costs one pass over a `DoubleArray`.
         */
        const val KNOBS_PER_SECTION = 4

        const val KNOB_FREQ = 0
        const val KNOB_Q = 1
        const val KNOB_DB = 2
        const val KNOB_GAIN = 3
    }

    /** Holds the bank in service and, while a fade runs, the bank fading out. */
    private val swap = KatalystFilterSwap(sampleRate, blockFrames)

    /**
     * The TWO pre-built banks (see the class KDoc): one sounds, one is the fade partner, and an
     * install takes the one the swap does not hold. Two is the whole capacity of the swap, so a
     * third could never be heard.
     */
    private val banks = Array(2) { EqBank(types.size) }

    /** The scalars the installed bank was configured from, [KNOBS_PER_SECTION] per section. */
    private val current = DoubleArray(types.size * KNOBS_PER_SECTION)

    /**
     * The ONE parking slot: the scalars of a change that arrived while a fade was running,
     * preallocated with the stage, so parking writes numbers and allocates nothing.
     */
    private val parkedValues = DoubleArray(types.size * KNOBS_PER_SECTION)

    private var hasParked: Boolean = false

    /** Test seam: true while a bank is installed, so the stage is filtering rather than absent. */
    internal val isEngaged: Boolean get() = swap.active

    /** Test seam: whether a curve is waiting for the fade in flight to land. */
    internal val isParked: Boolean get() = hasParked

    /**
     * Test seam: WHICH of the banks the last install took, or -1 before the first. The stage keeps
     * two (Katalyst 5c-11, the swap's whole capacity) and an install takes the one the swap does
     * not hold, so consecutive installs ALTERNATE. That is the property, and it is invisible from
     * the output: a third bank would simply never be reached, and a bank reconfigured while it
     * still sounds is a thump the crossfade row would catch only with the right curve.
     */
    internal var lastInstalledBank: Int = -1
        private set

    /**
     * Test seam for the one OUTPUT-INVISIBLE property of this stage (the `hasRawTap` /
     * `staticConfigureSkips` precedent): how many banks have been installed. A block that
     * re-applies the values it applied last must not install one, or every block would recompute
     * every section's coefficients and start a crossfade nobody asked for.
     */
    internal var installs: Int = 0
        private set

    /**
     * Configures the curve from the resolved scalars, [KNOBS_PER_SECTION] per section in list
     * order (see the companion). A call that carries the installed values is FREE: one compare
     * pass, no coefficients, no swap.
     *
     * An array of the wrong SHAPE is IGNORED and the installed curve stands, the same house
     * fall-through [EqCore] states for its own index and window guards: this is a control-rate
     * door on the audio thread, where an escaped exception takes the whole worklet, and a
     * mismatched length would otherwise throw inside `copyInto` or the compare pass.
     */
    fun configure(values: DoubleArray) {
        if (values.size != current.size) {
            return // wrong shape: ignore, keep the curve (KDoc)
        }

        if (swap.active && unchanged(values)) {
            // The latest word is the curve already installed: anything parked behind it is overtaken.
            hasParked = false

            return
        }

        if (!swap.settled) {
            // A fade is running and a third bank could not be heard: the SCALARS wait, latest wins.
            values.copyInto(parkedValues)
            hasParked = true

            return
        }

        values.copyInto(current)

        val index = freeBank()
        val bank = banks[index]

        bank.install(types, current, sampleRate)
        swap.set(bank.left, bank.right)
        lastInstalledBank = index
        installs++
    }

    /**
     * The index of a bank nothing can hear: one the swap holds no reference to. An install only
     * ever runs while the swap is settled, where it holds at most ONE bank, so the other is always
     * free; the last index is the fallback that keeps this total when the swap holds none at all.
     */
    private fun freeBank(): Int {
        for (i in banks.indices) {
            if (!swap.holds(banks[i].left)) {
                return i
            }
        }

        return banks.size - 1
    }

    /**
     * Turns the stage off, a HARD cut (the orbit's deactivation, a chain swap, the shelf): the swap
     * holds no pair, so nothing this stage owns can reach the mix.
     *
     * The banks are deliberately NOT zeroed here. An idle pool bank is unreachable while the swap is
     * empty, and every path back into service goes through [EqBank.install], which zeroes it, so
     * the flush has ONE place and one failing row instead of two guards that mask each other
     * (`KatalystEqEffectSpec`, "the bank that comes back from the pool is silent on silence").
     */
    override fun reset() {
        hasParked = false
        swap.reset()
    }

    /**
     * False, and NOT because this stage is stateless: the SVF integrators and the swap's
     * crossfade hold state. It is because all of it lands in `ctx.mixBuffer`, which
     * `Cylinder.isMixBufferSilent()` scans AFTER the chain has run, so the deactivation gate
     * already sees this stage's residue without having to ask it. See [KatalystBodyEffect.hasTail].
     */
    override fun hasTail(): Boolean = false

    /** Rents nothing (the whole pool of banks is built with the stage), so retiring is the clean slate. */
    override fun retire() {
        reset()
    }

    /** The block, then the parked curve if the fade landed inside it; see [KatalystBodyEffect.process]. */
    override fun process(ctx: KatalystContext) {
        swap.process(ctx.mixBuffer, ctx.blockFrames)

        if (hasParked && swap.settled) {
            hasParked = false
            configure(parkedValues)
        }
    }

    private fun unchanged(values: DoubleArray): Boolean {
        for (i in current.indices) {
            val installed = current[i]
            val arriving = values[i]

            // NaN-guard on a self-comparison: a non-finite knob is a value like any other to the
            // coefficient helpers (see the class KDoc), but `NaN != NaN` would reinstall the bank
            // on every block for a curve that never moved.
            if (installed != arriving && !(installed.isNaN() && arriving.isNaN())) {
                return false
            }
        }

        return true
    }
}

/**
 * One [EqCore] behind the [AudioFilter] door [KatalystFilterSwap] blends: the core's `process`
 * already has that exact signature, so this is a delegation and no per-sample cost.
 */
private class EqCoreFilter(val core: EqCore) : AudioFilter {
    override fun process(buffer: AudioBuffer, offset: Int, length: Int) {
        core.process(buffer, offset, length)
    }
}

/** One stereo pair of [EqCore]s: the unit [KatalystEqEffect] installs from its pool (see its KDoc). */
private class EqBank(sections: Int) {

    val left = EqCoreFilter(EqCore(sections))
    val right = EqCoreFilter(EqCore(sections))

    /** Makes this bank a fresh one on the given curve: state zeroed, every section reconfigured. */
    fun install(types: IntArray, values: DoubleArray, sampleRate: Double) {
        configure(left.core, types, values, sampleRate)
        configure(right.core, types, values, sampleRate)
    }

    private fun configure(core: EqCore, types: IntArray, values: DoubleArray, sampleRate: Double) {
        // Zeroed FIRST: the bank coming back into service is one that faded OUT earlier, and its
        // integrators still hold that curve's energy. `configureSection` keeps state by design (a
        // live tweak on ONE core must not click), so a bank that is about to be blended in from
        // zero weight has to start from zero state, or a high-Q section releases seconds-old
        // energy as a thump (the warning in `EqCore.disableSection`'s KDoc).
        core.reset()

        for (i in types.indices) {
            val base = i * KatalystEqEffect.KNOBS_PER_SECTION

            core.configureSection(
                index = i,
                type = types[i],
                freq = values[base + KatalystEqEffect.KNOB_FREQ],
                q = values[base + KatalystEqEffect.KNOB_Q],
                db = values[base + KatalystEqEffect.KNOB_DB],
                gain = values[base + KatalystEqEffect.KNOB_GAIN],
                sampleRate = sampleRate,
            )
        }
    }
}
