/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.peekandpoke.klang.audio_be.KnobGlide
import io.peekandpoke.klang.audio_be.effects.Phaser
import io.peekandpoke.klang.audio_bridge.constants.KNOB_GLIDE_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.PHASER_CENTER_HZ
import io.peekandpoke.klang.audio_bridge.constants.PHASER_SWEEP_HZ

/**
 * Phaser insert effect for the bus pipeline: it processes the orbit mix in place with a
 * multi-stage all-pass cascade.
 *
 * **How it switches and changes** (decided with the maintainer 2026-09-19,
 * `docs/tasks/katalyst-dsl.md` step 5c; built in Katalyst 5c-9). Every edge and every knob move
 * glides over [KNOB_GLIDE_SECONDS] from where the stage stands, and only the first initialisation
 * is instant.
 *
 * - **The C4 law's two coefficients are the stage's mix**, `out = dry·dryC + wet·wetC`. The output
 *   is LINEAR in the pair, so ramping both linearly per sample IS a linear crossfade from the
 *   phaser's old settings to its new ones. That one mechanism carries the `wet` knob, the `floor`
 *   knob, the OFF edge and the ON edge: OFF is `dryC = 1, wetC = 0`, the dry mix exactly, which is
 *   where the depth gate's target sits, so the stage has no separate fade to build.
 * - **OFF drops the cascade only once the glide has landed on identity.** While it runs, the
 *   cascade runs too, on the live signal. Entering Off early IS the click.
 * - **ON** starts the cascade from rest (cleared at the previous OFF) and ramps the pair up from
 *   identity.
 * - **A return** mid-glide is [KnobGlide]'s own rule: the new target is taken from where the
 *   coefficient stands, over a full glide.
 * - **The first initialisation is instant** ([fresh]): until a block has been processed since
 *   construction or [reset], every knob snaps, breakpoint included, so an orbit whose first
 *   rendered block already carries a phaser is exactly what it was before this step. An orbit
 *   whose first owner has none spends that window on the gated arm (the classic chain declares a
 *   phaser at `PHASER_WET = 0`), so its LATER first phaser fades in, which is the decided
 *   behaviour.
 * - **[reset] and [retire] are a HARD cut**, the full clean slate of [Phaser.resetForReuse]
 *   (cascade, latch, LFO phase and the kernel params) with every glide forgotten. Only for a
 *   silent orbit (the cylinder's deactivation) or the shelf.
 *
 * **What glides, measured first** (`docs/plans/knob-glide.md`; metric: peak 0.7 ms RMS above
 * 8 kHz and peak 20 ms RMS below 60 Hz, both against the signal, sources band-limited to 3 kHz;
 * the phaser's own steady floor is -50 to -61 dB, set by the block-rate kinks of its interpolated
 * alpha):
 * - `wet` and `floor` feed [Phaser.dryCoeff] and [Phaser.wetCoeff], a MEMORYLESS pair, so they are
 *   LEVEL knobs: jumps measured -49.8 to -14.8 dB above 8 kHz, the ON and OFF edges among them,
 *   and an owner handover (off and straight back on) -31.4 to -15.2. They ramp PER SAMPLE inside
 *   [Phaser.process], written from the block's end (exact landing), and land at -60.3 to -49.6.
 * - `center` and `sweep` move the allpass breakpoint, and the allpass multiplies its INPUT by
 *   alpha, so a step in alpha is a step in the output: the loudest jumps of the whole stage
 *   (-9.7 dB on a chord for 100 Hz to 18 kHz). They glide PER BLOCK (the coefficient rule: the
 *   per-sample math would cost a `tan`), and `PhaserCore.prepareBlock`'s three-argument form makes
 *   alpha CONTINUOUS across the block seam, so the glide arrives as a ramp and not as 17 steps.
 *   They are the one pair that does not reach the floor: -55.0 to -48.1 against -61.4 to -51.0,
 *   6 to 9 dB over it, which is the MODULATION a breakpoint crossing the spectrum in 50 ms makes
 *   and no axis removes. The alternative, a one-block alpha ramp with no glide, is worse where it
 *   matters: below 60 Hz it measured -14.8 dB against this glide's -37.5 on the same row, worse
 *   than the hard jump's own -24.4.
 * - `rate` does not glide: it only scales the LFO's phase increment, the phase itself carries on,
 *   and every jump over 0 to 20 Hz measured at or under the steady floor in both directions.
 *
 * **No state classes** (the complexity rule of `docs/plans/effect-state-machines.md`, the
 * [KatalystGainEffect] precedent). The stage's situations are FRESH, gliding and settled, which
 * are exactly [KnobGlide]'s snap flag, block countdown and rest, plus "the cascade holds state",
 * which is [Phaser]'s own `engaged` latch next to the cascade it guards. Inner classes would copy
 * both for an identical result.
 *
 * **The plan's four questions, for this stage:**
 *  1. *What outlives its states*: the [Phaser] itself (cascade, LFO phase and kernel params), the
 *     four glides and [fresh]. Nothing but [reset] touches them. The LFO phase outliving the OFF
 *     edge is the retained-kernel rule (block-framing ledger D2): a stretch without a phaser must
 *     not restart the sweep. Row: "the sweep clock keeps running while the stage is off".
 *  2. *What record of a finished life is forgotten, and where*: at the OFF edge, NOTHING but the
 *     cascade, deliberately: the kernel params and the LFO phase are what the next owner within
 *     the same orbit life inherits. The whole record goes at [reset] / [retire], where the orbit's
 *     life ends. Row: "a reset mid-glide: the next life snaps, it does not glide from the old
 *     life's coefficients".
 *  3. *Its Off precondition*: the output is the dry mix, `dryC = 1` and `wetC = 0` for a whole
 *     block. The cascade is cleared there and nowhere else (bar [reset], where the host guarantees
 *     silence). Row: "the cascade is still running one block before the glide lands".
 *  4. *Which state data are references*: none. The glides hold numbers and the cascade is the
 *     [Phaser]'s own array, so [reset] completes synchronously.
 */
class KatalystPhaserEffect(
    val phaser: Phaser,
    sampleRate: Int,
    /** The frames of one render block, pinned to 128 in the engine. */
    blockFrames: Int,
) : KatalystEffect {

    /** The C4 law's dry coefficient in force: a LEVEL knob, ramped per sample. */
    private val dryGlide = KnobGlide(sampleRate = sampleRate, blockFrames = blockFrames)

    /** The C4 law's wet coefficient in force: a LEVEL knob, ramped per sample. */
    private val wetGlide = KnobGlide(sampleRate = sampleRate, blockFrames = blockFrames)

    /** The breakpoint centre in Hz: a COEFFICIENT knob, one value per block. */
    private val centerGlide = KnobGlide(sampleRate = sampleRate, blockFrames = blockFrames)

    /** The breakpoint sweep width in Hz: a COEFFICIENT knob, one value per block. */
    private val sweepGlide = KnobGlide(sampleRate = sampleRate, blockFrames = blockFrames)

    /**
     * True from construction and from [reset] until a block has been processed: while it holds,
     * every knob acts at once (the first initialisation is instant). The breakpoint needs it
     * SPELLED OUT, unlike the two coefficients: [KnobGlide]'s own snap sets its value, but the
     * cores still hold their built-in 1 kHz breakpoint, and the first block would otherwise start
     * alpha there and ramp away from it.
     */
    private var fresh: Boolean = true

    init {
        // Unset is identity: the dry mix, which is what the depth gate's off target is too.
        dryGlide.retarget(1.0)
        wetGlide.retarget(0.0)
        // The cores' own breakpoint, so a stage configured before its first block snaps onto the
        // author's numbers with no ramp from somewhere else.
        centerGlide.retarget(PHASER_CENTER_HZ)
        sweepGlide.retarget(PHASER_SWEEP_HZ)
    }

    /** Test seam: the C4 coefficients in force right now, the targets once a glide has landed. */
    internal val dryCoeff: Double get() = dryGlide.value

    /** Test seam: see [dryCoeff]. */
    internal val wetCoeff: Double get() = wetGlide.value

    /** Test seam: the breakpoint centre in force right now, in Hz. */
    internal val center: Double get() = centerGlide.value

    /** Test seam: the breakpoint sweep width in force right now, in Hz. */
    internal val sweep: Double get() = sweepGlide.value

    /**
     * Applies the orbit owner's five phaser knobs. Called by the chain's writer on every block the
     * lease is held, so an unchanged owner must cost nothing: every write here is either a store of
     * the same number or a [KnobGlide.retarget] to the target that already stands.
     *
     * THE GATE lives here, and there is only one: [depth] BELOW [Phaser.MIN_ACTIVE_DEPTH] aims the
     * two coefficients at identity, and the KERNEL params are not written at all. A no-phaser owner
     * must not zero the sweep CLOCK (block-framing ledger D2): `VoiceFactory` defaults `rate` to
     * 0.0, and a rate of 0 freezes the LFO as surely as a skipped `prepareBlock`; the retained rate
     * and breakpoint are what keep the sweep on its own timeline across owner handoffs within one
     * orbit life, mirroring the delay's retained drain config. A source that EXPLICITLY sets rate 0
     * with an engaged depth still gets its static notch.
     *
     * Gate on the STORED depth, not the raw input: [Phaser.depth]'s setter silently rejects
     * non-finite input, and what reaches the DSP must never disagree with what the gate decided
     * (review round 2 of the stage's first version).
     */
    fun configure(depth: Double, rate: Double, center: Double, sweep: Double, floor: Double) {
        phaser.depth = depth

        if (phaser.depth >= Phaser.MIN_ACTIVE_DEPTH) {
            phaser.rate = rate
            phaser.floor = floor
            phaser.feedback = 0.5
            centerGlide.retarget(if (center > 0) center else PHASER_CENTER_HZ)
            sweepGlide.retarget(if (sweep > 0) sweep else PHASER_SWEEP_HZ)

            if (fresh) {
                // The cores start the first block at the breakpoint they HOLD, so the snapped
                // value has to be in them before that block runs (see [fresh]).
                phaser.center = centerGlide.value
                phaser.sweep = sweepGlide.value
            }

            // The glide lands on exactly the number the settled path then computes, so aim it at
            // what the law makes of the knobs the DSP STORED, never at the raw input.
            dryGlide.retarget(phaser.dryCoeff())
            wetGlide.retarget(phaser.wetCoeff())

            return
        }

        dryGlide.retarget(1.0)
        wetGlide.retarget(0.0)
    }

    override fun process(ctx: KatalystContext) {
        fresh = false

        val dryFrom = dryGlide.value
        val dryTo = dryGlide.advance()
        val wetFrom = wetGlide.value
        val wetTo = wetGlide.advance()

        phaser.process(
            buffer = ctx.mixBuffer,
            frames = ctx.blockFrames,
            centerTo = centerGlide.advance(),
            sweepTo = sweepGlide.advance(),
            dryFrom = dryFrom,
            dryTo = dryTo,
            wetFrom = wetFrom,
            wetTo = wetTo,
        )
    }

    /** Cascade + latch + LFO phase + kernel params + every glide: the full clean slate, rate included. */
    override fun reset() {
        phaser.resetForReuse()
        fresh = true

        // Forget, then aim at the identity the cleared stage stands at: the next value snaps.
        dryGlide.reset()
        dryGlide.retarget(1.0)
        wetGlide.reset()
        wetGlide.retarget(0.0)
        centerGlide.reset()
        centerGlide.retarget(PHASER_CENTER_HZ)
        sweepGlide.reset()
        sweepGlide.retarget(PHASER_SWEEP_HZ)
    }

    /**
     * False: the cascade, the latch and the LFO phase are state, but the phaser is an INSERT, so
     * whatever it still carries is in `ctx.mixBuffer` by the time
     * `Cylinder.isMixBufferSilent()` scans it. See [KatalystBodyEffect.hasTail].
     */
    override fun hasTail(): Boolean = false

    /** Rents nothing, so retiring is the clean slate. */
    override fun retire() {
        reset()
    }
}
