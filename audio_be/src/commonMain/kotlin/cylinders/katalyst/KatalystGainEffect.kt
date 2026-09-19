/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.peekandpoke.klang.audio_be.KnobGlide

/**
 * The orbit's **group fader**: one multiply of the summed mix, at the stage's list position.
 *
 * The structural fix for mixing an orbit deliberately low, to keep its compressor out of plop
 * territory, and bringing the level back at the end of its chain (the signal-flow plan's D5:
 * `gain` means the tone-neutral level on every surface).
 *
 * **Raw.** A negative factor (a polarity flip) and one far above unity are the author's business;
 * nothing here clamps. The two guards are reads of "unset" rather than clamps, and they answer it
 * differently on purpose: the WRITER reads a non-finite slot as unity and hands that on (see
 * [KatalystGainWriter], the same thing `MasterChain.buildGain` does with this knob on the master
 * bus), while this stage's DOOR ignores the call entirely and leaves the current target standing
 * (see [configure], which spells out when the difference shows).
 *
 * **Unity is bit-transparent**: the multiply is skipped entirely, so a chain that declares
 * `gain(1.0)` cannot change one sample. There is no `-0.0` hazard in that skip, unlike the
 * additive identities elsewhere in the engine, because this is a multiply by exactly 1.0, which
 * returns `-0.0` for `-0.0` anyway; skipping it is a saving, not a different answer.
 *
 * **A change GLIDES, per sample, over `KNOB_GLIDE_SECONDS`** (`docs/plans/knob-glide.md`, the LEVEL
 * rule; Katalyst 5c-8). The master snaps its gain instead: its factor is resolved at chain build and
 * a new number is a new chain, so the 60 ms bus crossfade covers it. Here the factor is a slot, so
 * `.katp("gain.gain", "<0.5 1.0>")` moves it while the stage stands. The glide is [KnobGlide]'s LEVEL
 * half: linear over whole blocks (17 at 44.1 kHz, 19 at 48 kHz), per sample within a block, written
 * from the block's END so it lands on the target bit for bit, and a new target mid-glide starts from
 * where the fader stands. Until 5c-8 a change ramped across ONE block from its start, which measured
 * -58 to -79 dB above 8 kHz relative to the signal on a full-span jump (saw, chord and bass, all
 * band-limited to 3 kHz) and landed one rounding off the target.
 *
 * **Three situations, no state classes.** The effect-state-machine plan names them: FRESH (nothing
 * multiplied since construction or [reset], so every [configure] snaps, see below), RAMPING (a glide
 * runs) and SETTLED; the level in force outlives all three. They are exactly [KnobGlide]'s snap flag,
 * its block countdown and its rest, the helper every LEVEL knob of the orbit already shares, so
 * spelling them as inner classes here would copy the helper for an identical result (the plan's
 * complexity rule: no identity-only states where the lifecycle is trivial). What made the lifecycle
 * look less trivial, a host reset landing under sounding notes, is gone since 5c-8: that was the
 * host's precondition to fix, not a state of this stage (answer 3 below).
 *
 * **The plan's four questions, for this stage** (`docs/plans/effect-state-machines.md` section 2):
 *
 *  1. *What outlives its states*: the level in force, [gain]. Every glide starts from it, a retarget
 *     mid-glide included, so nothing that begins a glide may touch it. Row: "a new target mid-glide
 *     turns from where the fader stands, never from where the old glide was going".
 *  2. *What record of a finished life is forgotten, and where*: the fader has no terminal state; the
 *     host's [reset] (a deactivated orbit) and [retire] forget the level and re-arm the snap, so the
 *     next life starts at unity and its first factor arrives at once. Row: "a reset mid-glide: the
 *     next life snaps, it does not glide from the old life's level".
 *  3. *Its Off precondition*: it has no Off and no tail ([hasTail] is a constant false, so a row on it
 *     would be vacuous). The one precondition that matters is the HOST's: `Cylinder.tryDeactivate`
 *     resets the chain only once no voice plays on the orbit (its lease has lapsed), so a reset never
 *     lands under a sounding note, and a fader at exactly 0 keeps its orbit alive. Row:
 *     `CylinderFaderThroughZeroSpec`.
 *  4. *Which state data are references*: none. The whole state is [KnobGlide]'s numbers and flag, so
 *     [reset] completes synchronously without dispatching anywhere. Row: "reset puts the fader back
 *     to unity" (its FIRST sample, which a reset that left a glide running would miss).
 *
 * **Why an ARRIVING factor snaps** (FRESH): the glide exists to make a MOVE continuous, and there is
 * nothing to be continuous with before the first sample or after a reset (the orbit has been silent
 * for its grace, and no voice is on it). `k.classic().gain(0)` is the mute idiom: a
 * glide from the unset unity would open every life of a muted orbit with 50 ms of its notes at full
 * level. A chain arriving through a crossfade can see two configures before its first block, and
 * both snap, because it is a processed BLOCK that ends the fresh situation.
 */
class KatalystGainEffect(
    sampleRate: Int,
    /** The frames of one render block, pinned to 128 in the engine. */
    blockFrames: Int,
) : KatalystEffect {

    /** The fader: the level in force, where it is going, and the snap of a fresh stage. */
    private val glide = KnobGlide(sampleRate = sampleRate, blockFrames = blockFrames)

    init {
        // Unset is unity, the identity element (the helper itself starts at zero, which would mute).
        glide.retarget(1.0)
    }

    /** Test seam: the factor in force right now, which is the target once a glide has landed. */
    internal val gain: Double get() = glide.value

    /**
     * Test seam for the one OUTPUT-INVISIBLE property of this stage: how many blocks it spent
     * gliding. A steady fader must never glide (it arrives by a snap and then multiplies by a
     * constant); a stage that glided every block would sound the same and do the work forever.
     */
    internal var ramps: Int = 0
        private set

    /**
     * Sets the fader. Takes effect by a glide from where the fader stands, or at once while the stage
     * is fresh (see the class KDoc); the same number again is free.
     *
     * A non-finite factor is UNSET, and the call is ignored so the current target stands (the
     * [KnobGlide] door's NaN guard). That is unity only BEFORE the first set; after a factor has been
     * set, ignoring a NaN keeps THAT factor, not unity.
     *
     * In production the difference never shows, because the guard never fires: [KatalystGainWriter]
     * substitutes 1.0 for a non-finite slot before it calls this, so an unset slot arrives here as
     * unity and a cleared one takes the fader back to unity by a glide. The guard stays at the door
     * anyway, because the cost of a NaN that does get through is unbounded (the whole orbit would be
     * NaN for good) and because a second caller must not have to rediscover the rule.
     */
    fun configure(gain: Double) {
        glide.retarget(gain)
    }

    override fun reset() {
        glide.reset()
        glide.retarget(1.0)
    }

    /** Nothing time-based at all: one multiply of the mix, no memory of the block before. */
    override fun hasTail(): Boolean = false

    /** Rents nothing, so retiring is the clean slate. */
    override fun retire() {
        reset()
    }

    override fun process(ctx: KatalystContext) {
        if (!glide.isGliding && glide.value == 1.0) {
            // Unity: bit-transparent, see the class KDoc. The advance still ends the fresh
            // situation, because leaving the buffer alone is producing a block at unity.
            glide.advance()

            return
        }

        if (glide.isGliding) {
            ramps++
        }

        glide.advanceScaled(ctx.mixBuffer, ctx.mixBuffer, ctx.blockFrames)
    }
}
