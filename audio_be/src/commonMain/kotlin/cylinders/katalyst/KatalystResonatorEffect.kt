/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.peekandpoke.klang.audio_be.AudioBackendContext
import io.peekandpoke.klang.audio_be.filters.ResonatorBank
import io.peekandpoke.klang.audio_be.filters.ResonatorConfig
import io.peekandpoke.klang.audio_be.filters.ResonatorTable
import io.peekandpoke.klang.audio_bridge.constants.BODY_FLOOR
import io.peekandpoke.klang.audio_bridge.constants.BODY_WET
import io.peekandpoke.klang.audio_bridge.constants.VOWEL_FLOOR
import io.peekandpoke.klang.audio_bridge.constants.VOWEL_WET

/**
 * Orbit-level **resonator**, an insert effect on the summed orbit mix: the `body(...)` stage ([ResonatorKind.BODY])
 * and the `vowel(...)` stage ([ResonatorKind.VOWEL]), one class with two kinds since engine tidy-up step 12 (a). The
 * two were near-verbatim twins (`KatalystBodyEffect`, `KatalystFormantEffect`) that differed only in their band type,
 * their gain rule and their unset constants; the gain rules now live in the tables ([ResonatorTables]), so the kind
 * is left with the two constants. A chain declares a body stage AND a vowel stage, two instances with their own slots
 * and banks, so a vowel sung through a body (`vowel("a").body(material = "wood")`) runs both.
 *
 * `body(...)` and `vowel(...)` used to be per-voice filters, one SVF bank per voice (times every `superimpose` copy,
 * times every unison note). A body or a vowel is a timbre of the instrument, not of a note, so it runs **once per
 * orbit** on the mixed stereo signal. The orbit is the grouping unit: voices needing their own body go on different
 * orbits. The DSP is a [ResonatorBank], MONO, so a pair holds one per channel (independent state).
 *
 * Ownership: `Cylinder.commitOwner` only calls [configure] for the voice that OWNS the orbit (the rule's home is
 * `Cylinder.offer`). Because only the owner configures, a null table (the owner has no body, or no vowel)
 * authoritatively turns the resonator OFF: it is NOT a no-op.
 *
 * **Every edge fades** (Katalyst step 5c-6, decided with the maintainer 2026-09-19): off fades the bank to dry over
 * `BANK_CROSSFADE_SECONDS` and only then releases it, on fades in from dry, a change crossfades from what sounds now,
 * and an owner that returns to the same config mid-fade-out takes the fading bank back ([KatalystFilterSwap.resume]).
 * [reset] and [retire] stay a HARD cut: the cylinder calls them when the orbit has gone silent or goes to the shelf,
 * and a fade that survived them would resume on the orbit's next life.
 *
 * **ONE PARKING SLOT, for a config and never a bank** (Katalyst step 5c-11, 2026-09-20). The swap carries two banks
 * and no more, so a change that arrives while a fade runs cannot start: it waits as the config it came as (a table
 * reference and two numbers, copied into the stage's own [ResonatorConfig]), a further change REPLACES it (latest
 * wins, so a burst costs one install and not one per event), and [process] offers it again on the block the fade
 * lands. Parking the config and not a bank is what makes an overtaken change free. The 10-bank pool this replaced let
 * up to four materials smear at once on a 64th-note run, which is what the maintainer heard.
 *
 * **TWO pooled pairs, built at the FIRST install** (engine tidy-up step 12 (a), the orbit EQ's model since 5c-11). An
 * install takes the pair the swap does not hold, which nobody can hear, and [ResonatorBank.install] makes it a fresh
 * bank (state zeroed), bit for bit what building a new one gave. The pairs are built when the stage first installs,
 * not with it: the classic chain declares both stages and most voices use neither, so an eager pool would hold about
 * 12 KB per classic cylinder for nothing. One allocation per stage life, never one per change.
 *
 * **Intent and sound are two things.** [isEngaged] is the intent (the owner's latest word was a table), which is the
 * parking slot's when a config waits and the swap's otherwise; the bank may still be sounding after it turned false.
 * The config cache (what [installedTable], [installedMix] and [installedFloor] read, and the left bank of the pair)
 * describes the pair the last install configured. It survives a fade-out on purpose, so a returning owner can take the
 * fading bank back, and it is never trusted on its own: an unchanged config with the intent off asks the swap whether
 * that pair still sounds, and installs afresh when it does not (question 2 of `docs/plans/effect-state-machines.md`).
 */
class KatalystResonatorEffect(
    /** Body or vowel: which unset constants [configure] substitutes, and which tables the pool is sized for. */
    val kind: ResonatorKind,
    private val sampleRate: Double,
    /** The frames of one render block; sizes the swap's and the banks' scratch (see [KatalystFilterSwap]). */
    private val blockFrames: Int = AudioBackendContext.RENDER_QUANTUM_FRAMES,
) : KatalystEffect {

    /** One stereo pair of banks: the unit the pool holds and the swap blends. */
    private class BankPair(capacity: Int, sampleRate: Double, blockFrames: Int) {
        val left = ResonatorBank(capacity = capacity, sampleRate = sampleRate, blockFrames = blockFrames)
        val right = ResonatorBank(capacity = capacity, sampleRate = sampleRate, blockFrames = blockFrames)
    }

    /** The wet an unset (non-finite) mix stands for: `BODY_WET` or `VOWEL_WET`. */
    private val unsetWet: Double = when (kind) {
        ResonatorKind.BODY -> BODY_WET
        ResonatorKind.VOWEL -> VOWEL_WET
    }

    /** The floor an unset (non-finite) floor stands for: `BODY_FLOOR` or `VOWEL_FLOOR`. */
    private val unsetFloor: Double = when (kind) {
        ResonatorKind.BODY -> BODY_FLOOR
        ResonatorKind.VOWEL -> VOWEL_FLOOR
    }

    /**
     * The config the last install configured, its knobs as substituted (see [configure]); the pair reads it at
     * install. Its own instance, never the one offered (that one is the writer's, see [ResonatorConfig]).
     */
    private val cur = ResonatorConfig()

    /** The left bank of the pair the last install configured: what [KatalystFilterSwap.resume] looks for. */
    private var curLeft: ResonatorBank? = null

    /**
     * The ONE parking slot: the config of a change that arrived while a fade was running, a null table for a parked
     * OFF. [hasParked] is what tells those two apart, so the slot holds a real word and not an absence. Its own
     * instance, written field by field, so parking allocates nothing.
     */
    private val parked = ResonatorConfig()
    private var hasParked: Boolean = false

    /** Holds the pair in service and, while a fade runs, the pair fading out; every edge fades. */
    private val swap = KatalystFilterSwap(sampleRate, blockFrames)

    /** The two pairs, null until the first install (see the class KDoc). */
    private var pool: Array<BankPair>? = null

    /**
     * Test seam: the INTENT, true while the owner asks for a resonator. A parked config is the LATER word, so it
     * answers first.
     */
    internal val isEngaged: Boolean get() = if (hasParked) parked.table != null else swap.active

    /** Test seam: the SOUND, true while any bank may still be heard, a fade-out included. */
    internal val isSounding: Boolean get() = swap.sounding

    /** Test seam: whether a config is waiting for the fade in flight to land. */
    internal val isParked: Boolean get() = hasParked

    /**
     * Test seams: WHAT is installed, not just whether anything is ([isEngaged]).
     *
     * On a declared chain all three arrive as slots, the material or vowel as an INDEX into a shared catalogue, so a
     * wrong lookup or a knob wired to the wrong argument installs a real bank of the wrong box (or sings the wrong
     * vowel) and reads as "engaged" either way. Nothing else can see which one it is.
     *
     * They read the values the pair was configured from, so an unset knob reads as its constant and never as the
     * non-finite marker that arrived, and a PARKED change is not in them yet.
     */
    internal val installedTable: ResonatorTable? get() = cur.table

    internal val installedMix: Double get() = cur.mix

    internal val installedFloor: Double get() = cur.floor

    /**
     * Test seam: WHICH pair the last install took, or -1 before the first. An install takes the pair the swap does not
     * hold, so consecutive installs while one pair sounds ALTERNATE; a pair reconfigured while it still sounds would
     * be a thump the output rows catch only with the right material.
     */
    internal var lastInstalledPair: Int = -1
        private set

    /** Test seam: how many installs ran. A block that re-offers the installed config must not add one. */
    internal var installs: Int = 0
        private set

    /** Test seam: whether the pool has been built (it is, from the first install on). */
    internal val hasPool: Boolean get() = pool != null

    /**
     * Configure from the chain's slots (`KatalystResonatorWriter`); a voice's `body(...)` or `vowel(...)` door reaches
     * here through those slots. A null table in [offer] (nobody asks for this resonator) turns it off. The stage reads
     * [offer] and keeps no reference to it.
     *
     * A non-finite mix or floor is UNSET and takes the kind's constant (`BODY_WET` / `BODY_FLOOR`, `VOWEL_WET` /
     * `VOWEL_FLOOR`). This is the ONE home of that rule: the writer hands the slots through raw, so a raw `katp` write
     * arrives here non-finite.
     *
     * A null fades the bank out (see the class KDoc); a second null while it fades is free. While a fade runs the word
     * is PARKED instead of acted on, except for the one word that needs no new bank: a return to the config that is
     * fading out, which turns that fade around.
     */
    fun configure(offer: ResonatorConfig) {
        val table = offer.table

        if (table == null) {
            if (!isEngaged) {
                return // already off, or already heading there: idempotent
            }

            if (swap.settled) {
                parked.table = null
                hasParked = false
                swap.clear() // the owner has none: fade to dry, once
            } else {
                parked.table = null
                hasParked = true // the off waits for the fade in flight (latest wins)
            }

            return
        }

        // **The ONE home of the resonator's NaN rule** (since 2026-10-07, audit B2.11): the writer hands its slots
        // through raw, so a raw `katp("body.wet", ...)` write arrives here non-finite. It lives here and not in the
        // writer because this is where the compare is, and the failure it prevents is a per-block install on the audio
        // thread rather than a wrong number.
        // NaN-guards, and they sit BEFORE the comparison on purpose: a NaN is never equal to itself, so an unguarded
        // non-finite mix made the test below true on EVERY block and reconfigured a bank per block on the audio thread,
        // restarting a crossfade that then never completed. Substituted, compared and stored, so the value the banks
        // are configured from is the value the next block compares against.
        val mix = offer.mix
        val floor = offer.floor
        val m = if (mix.isFinite()) mix else unsetWet
        val f = if (floor.isFinite()) floor else unsetFloor

        // Install only when the table, the mix or the floor actually changes: with ownership this is once per owner
        // change (a live owner re-offers the same config every block, which short-circuits here). The table compares
        // by REFERENCE: `ResonatorTables` shares one instance between indices whose rows are equal, so a switch among
        // aliases (`bass:a`, `bass:ei`) is no change.
        val unchanged = table === cur.table && m == cur.mix && f == cur.floor

        if (unchanged) {
            // The owner's latest word is the config already installed, so anything parked behind it is overtaken
            // (latest wins) whether or not a fade is still running.
            parked.table = null
            hasParked = false

            if (swap.active) {
                return
            }

            // The owner is back with the config it had: take the fading pair back where it stands. Only while it
            // still sounds; once it has faded out the cache describes a released pair, and the identical config
            // installs afresh below. A return after a DIFFERENT change (A, then B, then A again within one fade) is
            // not found here, because the cache holds B: A parks, and once B's fade lands a fresh A fades in over it.
            // Searching the outgoing pair by config is complexity the safety net does not need (decided in the 5c-6
            // review).
            val left = curLeft

            if (left != null && swap.resume(left)) {
                return
            }
        }

        if (!swap.settled) {
            // A fade is running and this change would need a third pair: park the config, latest wins.
            parked.table = table
            parked.mix = m
            parked.floor = f
            hasParked = true

            return
        }

        cur.table = table
        cur.mix = m
        cur.floor = f
        install()
    }

    /**
     * Configures the pair nobody hears from [cur] and hands it to the swap. Only while the swap is settled, where it
     * holds at most ONE pair, so the other is always free.
     */
    private fun install() {
        val pairs = pool ?: buildPool()
        val index = freePair(pairs)
        val pair = pairs[index]

        pair.left.install(cur)
        pair.right.install(cur)
        swap.set(left = pair.left, right = pair.right)
        curLeft = pair.left
        lastInstalledPair = index
        installs++
    }

    /** The pool's one allocation, at the first install of this stage's life (see the class KDoc). */
    private fun buildPool(): Array<BankPair> {
        val capacity = ResonatorTables.capacity(kind)
        val pairs = Array(2) { BankPair(capacity = capacity, sampleRate = sampleRate, blockFrames = blockFrames) }

        pool = pairs

        return pairs
    }

    /**
     * The index of a pair nothing can hear: one the swap holds no reference to. The last index is the fallback that
     * keeps this total; an install never needs it (see [install]).
     */
    private fun freePair(pairs: Array<BankPair>): Int {
        for (i in pairs.indices) {
            if (!swap.holds(pairs[i].left)) {
                return i
            }
        }

        return pairs.size - 1
    }

    /**
     * A HARD cut (orbit deactivation, chain swap, the shelf): the pair goes at once, the cache and the parking with
     * it. The pool stays: an idle pair is unreachable while the swap is empty, and every way back into service goes
     * through [ResonatorBank.install], which zeroes it.
     */
    override fun reset() {
        cur.table = null
        cur.mix = Double.NaN
        cur.floor = Double.NaN
        curLeft = null
        parked.table = null
        hasParked = false
        swap.reset()
    }

    /**
     * False, and NOT because this stage is stateless: the SVF integrators and the swap's crossfade do hold state. It
     * is because everything they hold lands in `ctx.mixBuffer`, and `Cylinder.isMixBufferSilent()` scans that buffer
     * AFTER the chain has run, so the deactivation gate already sees this stage's residue without having to ask it. A
     * future stage whose output does NOT reach the mix before that scan (a send-return, a chorus ring) must answer true
     * while it rings, as the delay and the reverb do.
     */
    override fun hasTail(): Boolean = false

    /** Rents nothing (the pool is the stage's own), so retiring is the clean slate. */
    override fun retire() {
        reset()
    }

    /**
     * The block, and then the parked config if the fade landed inside it.
     *
     * AFTER the swap and not before: the fade lands at the end of a block, so offering here starts the parked change
     * on the very next block instead of one later. It runs from `process` and not from the next [configure] because a
     * chain whose owner has died is still processed and no longer configured, and a change parked at that moment would
     * otherwise never arrive.
     */
    override fun process(ctx: KatalystContext) {
        swap.process(ctx.mixBuffer, ctx.blockFrames)

        if (hasParked && swap.settled) {
            // The slot is offered as it stands, and read before anything writes it again.
            hasParked = false
            configure(parked)
        }
    }
}
