/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.effects

import io.peekandpoke.klang.audio_be.filters.WetDryMix
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_bridge.constants.PHASER_CENTER_HZ
import io.peekandpoke.klang.audio_bridge.constants.PHASER_FLOOR
import io.peekandpoke.klang.audio_bridge.constants.PHASER_RATE_HZ
import io.peekandpoke.klang.audio_bridge.constants.PHASER_SWEEP_HZ
import io.peekandpoke.klang.audio_bridge.constants.PHASER_WET

/**
 * Stereo phaser — two independent [PhaserCore] instances (one per channel) sharing
 * the same parameters but maintaining independent state.
 *
 * **Output**: the shared C4 wet/dry law, correlated branch (p = 2), with a [floor]ed dry —
 * `output = max(floor, cos²(depth·π/2)) · dry + sin²(depth·π/2) · wet`. At the default
 * `floor = 1.0` this is purely ADDITIVE (`dry + wet · sin²`): at `depth = 0.5` you get the
 * dry plus half-amplitude wet (≈ +3 dB louder than dry; the allpass cascade has unit
 * magnitude so the wet sample magnitude tracks the dry, modulated by the swept notches),
 * at `depth = 1.0` dry + full wet — the cylinder-bus convention where `phaserDepth` adds
 * an effect on top of the source. Lowering the floor (`phaserFloor`) turns the same knob
 * into a crossfade; the phaser processes its cylinder mix in place, so unlike the reverb
 * SEND this is genuinely expressible per orbit. Blast radius: with `floor < 1` the knob
 * scales the dry of the WHOLE orbit mix — co-resident voices that never asked for a phaser
 * and the delay/reverb returns included (bus order: body/vowel -> delay -> reverb -> phaser;
 * first-writer-wins owns the knobs, route to another orbit for different bus settings).
 *
 * THE BUS OWNS THE PHASER (maintainer decision, 2026-08-24): the built-in pipeline presets
 * carry no per-voice phaser stage, so this bus pass is the ONE application of the knobs —
 * the DAW-insert model, one coherent sweep over the summed orbit. `phaserFloor < 1` is an
 * exact crossfade here. Only a CUSTOM pipeline that adds `StageDsl.Phaser` gets per-voice
 * phasing on top (then the same knobs drive both passes and a floor below 1 floors the dry
 * twice — the [voices.strip.filter.StripPhaserRenderer] KDoc carries that warning).
 *
 * The Ignitor-DSL phaser ([io.peekandpoke.klang.audio_be.ignitor.PhaserIgnitor]) is the
 * same law at `floor = 0.0`, applied ONCE inside the voice. They share [PhaserCore] for
 * the per-sample math; only the floor default differs.
 *
 * **Stereo image**: both channels share the same LFO phase (centred-mono image).
 * For wider stereo, use a future `stereoSpread` knob to offset the right LFO
 * (out of scope for this dedup pass).
 */
class Phaser(sampleRate: Int) {

    companion object {
        /**
         * Below this the bus phaser is a bypass: the historical katalyst `< 0.01` gate, and still
         * the ONE gate and the ONE threshold. Since Katalyst 5c-9 it is APPLIED in
         * `KatalystPhaserEffect.configure`, which is where the knobs arrive: below it the stage
         * aims its two C4 coefficients at identity and writes no kernel param. This class only
         * ever sees the coefficients a block runs at, and it reads identity, not a depth. So the
         * bare two-argument [process] does not gate at all: it renders [depth] as it stands, which
         * is what makes it the reference a spec builds an oracle from.
         *
         * The LFO advances above the gate regardless, and it keeps advancing while the stage is
         * off (block-framing ledger D2).
         */
        const val MIN_ACTIVE_DEPTH: Double = 0.01
    }

    private val coreL = PhaserCore(PhaserCore.DEFAULT_STAGES, sampleRate)
    private val coreR = PhaserCore(PhaserCore.DEFAULT_STAGES, sampleRate)

    /** LFO frequency in Hz. */
    var rate: Double
        get() = coreL.rate
        set(value) {
            coreL.rate = value
            coreR.rate = value
        }

    /** Wet/dry crossfade amount, clamped to `[0, 1]`. NaN/Inf silently ignored. */
    var depth: Double = 0.0
        set(value) {
            if (!value.isFinite()) return
            field = value.coerceIn(0.0, 1.0)
        }

    /**
     * Minimum dry coefficient of the C4 law. 1.0 (default) = purely additive. Stored RAW:
     * [WetDryMix.dryCoeff] owns the domain coercion (non-finite -> 0.0, clamp to [0, 1]),
     * so every consumer of the knob resolves a bad value the SAME way (one knob, one law).
     * NOTE: patterning this knob (or [depth] once the floor is below 1) steps the dry gain
     * of the whole cylinder mix at block boundaries with no smoothing - raw engine, no
     * hidden ramps; expect zipper on fast patterns.
     */
    var floor: Double = 1.0

    /** Center breakpoint frequency in Hz. */
    var center: Double
        get() = coreL.center
        set(value) {
            coreL.center = value
            coreR.center = value
        }

    /** LFO sweep width in Hz. */
    var sweep: Double
        get() = coreL.sweep
        set(value) {
            coreL.sweep = value
            coreR.sweep = value
        }

    /** Feedback amount, clamped to `[0, PhaserCore.MAX_FEEDBACK]`. */
    var feedback: Double
        get() = coreL.feedback
        set(value) {
            coreL.feedback = value
            coreR.feedback = value
        }

    // True while the cascades hold post-bypass state — cleared when the depth gate closes.
    private var engaged = false

    /**
     * Clears the allpass state of both channels (and the engaged latch). The LFO phase is
     * deliberately preserved — see [PhaserCore.reset] — because this also runs on BYPASS entry,
     * where the sweep clock must keep its position.
     */
    fun reset() {
        coreL.reset()
        coreR.reset()
        engaged = false
    }

    /**
     * Zeroes the LFO phase on both channels — part of [resetForReuse]; never called on the bypass
     * path, where the sweep clock must keep its position.
     */
    fun zeroLfoPhase() {
        coreL.zeroPhase()
        coreR.zeroPhase()
    }

    /**
     * Full factory state, for ORBIT TEARDOWN only (`KatalystPhaserEffect.reset`, which
     * `KatalystChain.reset` and `KatalystChain.retire` call): cascade, engaged
     * latch, LFO phase AND the kernel params. The retained-kernel rule (a no-phaser owner keeps
     * the previous owner's clock) is scoped to owner handoffs WITHIN one orbit life — across a
     * teardown there is no timeline left to preserve, and a surviving rate would free-run the
     * just-zeroed phase through the next life's phaser-less stretch (review round 3), landing the
     * next engagement mid-sweep by a cleanup-schedule-dependent offset. Zeroing the rate also
     * restores the fast path for lives that never host a phaser.
     */
    fun resetForReuse() {
        reset()
        zeroLfoPhase()
        depth = PHASER_WET
        floor = PHASER_FLOOR
        rate = PHASER_RATE_HZ
        center = PHASER_CENTER_HZ
        sweep = PHASER_SWEEP_HZ
        feedback = 0.5
    }

    /**
     * The C4 law's dry coefficient for the knobs as they stand: what a `dry` glide aims at. The
     * law lives here, in the one class that owns the two knobs, and the stage only moves between
     * the numbers it returns.
     */
    fun dryCoeff(): Double = WetDryMix.dryCoeff(depth, floor = floor, p = 2)

    /** The C4 law's wet coefficient for the knobs as they stand. See [dryCoeff]. */
    fun wetCoeff(): Double = WetDryMix.wetCoeff(depth, p = 2)

    /**
     * One block of [buffer], in place, with every knob exactly as it STANDS: no glide, no ramp.
     *
     * The bare-DSP entry. The orbit stage never calls it (it hands in what its glides hold, see
     * below), and it is what a spec builds its oracle from: a phaser whose knobs do not move
     * renders bit-identically through either entry, which is what makes "settled equals the old
     * path" true by construction rather than by a conditional.
     */
    fun process(buffer: StereoBuffer, frames: Int) {
        val dry = dryCoeff()
        val wet = wetCoeff()

        process(
            buffer = buffer,
            frames = frames,
            centerTo = center,
            sweepTo = sweep,
            dryFrom = dry,
            dryTo = dry,
            wetFrom = wet,
            wetTo = wet,
        )
    }

    /**
     * One block of [buffer], in place.
     *
     * Every knob arrives as the value the GLIDE holds for this block, never as a field read
     * (Katalyst 5c-9, `docs/plans/knob-glide.md`): `KatalystPhaserEffect` owns one
     * `KnobGlide` per knob and hands the results in.
     *
     * - [centerTo] and [sweepTo] are the breakpoint this block ENDS at; the cores start from the
     *   one they still hold and interpolate (see [PhaserCore.prepareBlock]).
     * - the C4 law's two coefficients ramp PER SAMPLE from ([dryFrom], [wetFrom]) to
     *   ([dryTo], [wetTo]), written from the END so the block's last sample is the block's value
     *   bit for bit. The output is linear in the pair, so a linear ramp of both IS a linear
     *   crossfade between the phaser's old and new settings: one mechanism for the `wet` knob, the
     *   `floor` knob and the ON and OFF edges alike.
     *
     * IDENTITY, `dry = 1` and `wet = 0` for the whole block, is the stage's OFF: the cascade is
     * cleared (once) and the block passes through untouched, while the LFO clock keeps running.
     * The glide lands on it exactly, so the cascade is only ever dropped where the output is
     * already the dry mix (entering Off early is the click this stage exists to prevent).
     */
    fun process(
        buffer: StereoBuffer,
        frames: Int,
        centerTo: Double,
        sweepTo: Double,
        dryFrom: Double,
        dryTo: Double,
        wetFrom: Double,
        wetTo: Double,
    ) {
        val identity = dryFrom == 1.0 && dryTo == 1.0 && wetFrom == 0.0 && wetTo == 0.0

        // Fast path for orbits that never had a phaser: at rate 0 the phase cannot move, alpha is
        // discarded on the gated path, and there is nothing engaged to clear — provably equivalent
        // to falling through (the next engaged block recomputes alpha from scratch anyway), and it
        // keeps the unconditional clock from taxing phaser-less orbits (review round 2).
        if (rate == 0.0 && identity && !engaged) {
            return
        }

        // Advance the LFO clock UNCONDITIONALLY (block-framing ledger D2): a depth gate must not
        // stop the sweep — the phase would otherwise resume framing-dependently after a patterned
        // depth dips through zero. Cost on the gated path: two sin + two tan per core per block
        // (alphaAt runs at both block boundaries).
        coreL.prepareBlock(frames, centerTo, sweepTo)
        coreR.prepareBlock(frames, centerTo, sweepTo)

        if (identity) {
            // Same policy as the ignitor door (ledger D5): a bypass clears the cascade instead of
            // freezing a stale feedback sample for a framing-dependent resume click.
            if (engaged) {
                reset()
            }

            return
        }

        engaged = true

        val left = buffer.left
        val right = buffer.right

        // C4 (filter unification): shared wet/dry law, correlated branch (p = 2). At the
        // default floor = 1.0 the dry coefficient is pinned at 1 (purely additive); the wet
        // follows sin^2 instead of the old linear depth (identical at 0, 0.5 and 1).
        if (dryFrom == dryTo && wetFrom == wetTo) {
            // Settled: one pair for the whole block, the path a phaser whose knobs stand has
            // always taken.
            val dryC = dryTo
            val wetC = wetTo

            for (i in 0 until frames) {
                val dryL = left[i]
                val wetL = coreL.step(dryL)
                left[i] = dryL * dryC + wetL * wetC

                val dryR = right[i]
                val wetR = coreR.step(dryR)
                right[i] = dryR * dryC + wetR * wetC
            }

            return
        }

        // A block of zero frames never enters the loop; the reciprocal keeps the division out of
        // the divide-by-zero corner anyway.
        val inv = if (frames > 0) 1.0 / frames else 0.0
        val dryStep = (dryTo - dryFrom) * inv
        val wetStep = (wetTo - wetFrom) * inv
        val last = frames - 1

        for (i in 0 until frames) {
            val back = (last - i).toDouble()
            val dryC = dryTo - dryStep * back
            val wetC = wetTo - wetStep * back

            val dryL = left[i]
            val wetL = coreL.step(dryL)
            left[i] = dryL * dryC + wetL * wetC

            val dryR = right[i]
            val wetR = coreR.step(dryR)
            right[i] = dryR * dryC + wetR * wetC
        }
    }
}
