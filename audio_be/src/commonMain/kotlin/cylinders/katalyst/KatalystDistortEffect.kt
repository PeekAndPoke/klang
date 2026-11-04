/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.peekandpoke.klang.audio_be.AudioBackendContext
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.DistortionCore
import io.peekandpoke.klang.audio_be.KnobGlide
import io.peekandpoke.klang.audio_be.distortionShapeAt
import io.peekandpoke.klang.audio_be.filters.HOUSE_DC_BLOCK_COEFF
import io.peekandpoke.klang.audio_be.utils.copyRangeInto
import io.peekandpoke.klang.audio_be.utils.crossfadeLinear
import io.peekandpoke.klang.audio_be.utils.isFiniteInline
import io.peekandpoke.klang.audio_be.utils.linearFadeWeight
import io.peekandpoke.klang.audio_bridge.DistortionShapes
import io.peekandpoke.klang.audio_bridge.constants.KNOB_GLIDE_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.SILENCE_FLOOR
import kotlin.math.min

/**
 * The bus distortion, an insert: the voice's distort at the bus position (`KatalystStageDsl.Distort`,
 * KLANG-00292 (katalyst distort stage)). It bends the orbit mix in place, left and right each through a core
 * of their own, so the notes of the bus meet inside one curve: the glue of a master saturator, the clipper before
 * a limiter, the amp fed by a whole chord.
 *
 * **The law** is the voice's fused distort, [DistortionCore] at the drive `10^(amount * 1.2)`
 * ([DistortionCore.drive]): the drive and the shaper, the DC blocker after, no soft cap, at the rate of the bus it
 * is in (inside an oversampling region, the region's; its own oversampling factor retired with KLANG-00214
 * (oversampling regions) step 4, and with it its latency and phase twins). One
 * deliberate difference: the DC blocker's pole is the house stage's ([HOUSE_DC_BLOCK_COEFF], a knee near 7 Hz)
 * instead of the voice's (near 35 Hz), because a bus carries the whole low end of a mix (measured 2026-10-09: the
 * voice's pole takes about 2.5 dB off 40 Hz).
 *
 * **How it switches**, the compressor's law ([KatalystCompressorEffect]): every edge fades over
 * [KNOB_GLIDE_SECONDS], linearly, between the distorted mix and the DRY mix, `out = dry + w * (distorted - dry)`.
 * A switch is needed because `amount` 0 is not the identity (the curve bends at unity drive too), so the stage
 * cannot switch by gliding the amount to 0.
 * - **OFF** fades `w` from 1 to 0 while the cores keep running, and enters [Off] only once `w` is exactly 0, so the
 *   output IS the dry mix.
 * - **ON** starts a new life from reset cores and fades it in from dry.
 * - **A return** turns a running fade around where it stands.
 * - **The first initialisation is instant**: until a block has been processed since construction or [reset], a
 *   switch acts at once ([KnobGlide]'s snap rule). A stage set on an orbit's first block renders at full weight from
 *   its first sample.
 * - **[reset] and [retire] are a HARD cut** to [Off], for a silent orbit and the shelf.
 *
 * **The amount glides** over [KNOB_GLIDE_SECONDS] ([KnobGlide], linear in the AMOUNT, so exponential in the drive,
 * as the knob is perceived). While it moves, the cores ramp the drive per sample of the stream they shape, from the
 * value the last block ended on to this block's ([DistortionCore.processRamped]); a settled amount hands the drive
 * to the cores. The shape is fixed with the chain.
 *
 * The lifecycle is a state machine (`audio/ref/effect-state-machines.md`), the shape of
 * [KatalystCompressorEffect]; the table on [State] is authoritative for its edges.
 *
 * **The four questions of the plan:**
 * 1. *What outlives its states:* the two cores, the amount glide, the dry scratch buffers and the snap flag [fresh].
 * 2. *The record of a finished life:* the cores' filter memories and the amount glide, forgotten in [Off.enter].
 *    The next life starts from reset cores and its amount snaps.
 * 3. *The Off precondition:* the output is the dry mix. [Off] is entered
 *    only from a fade whose weight has landed on exactly 0, by [reset] (the host guarantees silence), or while
 *    [fresh].
 * 4. *References and who drops them:* none. The cores are built once with the effect.
 */
class KatalystDistortEffect(
    private val sampleRate: Int,
    /** The frames of one render block; it sizes the scratch buffers and counts the glide's blocks. */
    blockFrames: Int = AudioBackendContext.RENDER_QUANTUM_FRAMES,
    /** The shape as its index in [DistortionShapes.names]; out of range is `soft` ([distortionShapeAt]). */
    shapeIndex: Int = DistortionShapes.SOFT_INDEX,
    /**
     * True when the amount's knob holds a modulator (knob expressions step 5b, D2): it glides over ONE block, the
     * drive ramped per sample from the last block's amount to this one's. The ON/OFF gate reads the block's value
     * (Q7): an amount that swings through 0 switches the stage off and on through its own blend over
     * [KNOB_GLIDE_SECONDS], turned around where it stands; accepted, a modulated amount is the author's to keep
     * above 0.
     */
    amountModulated: Boolean = false,
    /**
     * The 2x steps above the base rate the chain runs at, 0 on an orbit; inside an oversampling region the region's
     * (KLANG-00214 (oversampling regions)): the DC blocker's pole is mapped to stay where it is in Hz.
     */
    firstStage: Int = 0,
) : KatalystEffect {

    private val fadeLen: Int = (sampleRate * KNOB_GLIDE_SECONDS).toInt().coerceAtLeast(1)

    private val invFadeLen: Double = 1.0 / fadeLen

    private val coreL = DistortionCore(
        shape = distortionShapeAt(shapeIndex.toDouble()),
        dcBlockCoefficient = DistortionCore.dcBlockCoefficientAt(coefficient = HOUSE_DC_BLOCK_COEFF, stages = firstStage),
    )

    private val coreR = DistortionCore(
        shape = distortionShapeAt(shapeIndex.toDouble()),
        dcBlockCoefficient = DistortionCore.dcBlockCoefficientAt(coefficient = HOUSE_DC_BLOCK_COEFF, stages = firstStage),
    )

    private val amountGlide = KnobGlide(sampleRate = sampleRate, blockFrames = blockFrames, modulated = amountModulated)

    /** True from construction and from [reset] until a block has been processed: a switch then acts at once. */
    private var fresh: Boolean = true

    /** The dry mix of the current block, captured while a fade reads it. Engine blocks are [blockFrames] long. */
    private var dryL: AudioBuffer = AudioBuffer(blockFrames)
    private var dryR: AudioBuffer = AudioBuffer(blockFrames)

    /**
     * The lifecycle, one class per state, one instance each, created with the effect.
     *
     * | state \ event | `configure`, ON | `configure`, OFF | `process`, the fade lands | `reset` / `retire` |
     * |---|---|---|---|---|
     * | **Off** | fresh: **Engaged** at once, else **Fading** in from dry | Off | (never) | Off |
     * | **Engaged** | Engaged (a changed amount glides) | fresh: **Off** at once; else **Fading** out | (never) | **Off** |
     * | **Fading** | fading in: Fading; fading out: Fading, turned around | fading out: Fading; fading in: Fading, turned around | **Engaged** (in) or **Off** (out, the output exactly dry) | **Off** |
     */
    private sealed class State {
        abstract fun switchOn()

        abstract fun switchOff()

        abstract fun process(n: Int, left: AudioBuffer, right: AudioBuffer)
    }

    /** No distortion: the mix passes untouched. */
    private inner class Off : State() {
        /**
         * Forgets the finished life: the cores' filter memories and the amount glide (the next ON snaps).
         *
         * PRECONDITION: the output is already the dry mix (a fade out has landed on exactly 0, or [reset], or
         * nothing has sounded yet).
         */
        fun enter() {
            coreL.reset()
            coreR.reset()
            amountGlide.reset()
            state = this
        }

        override fun switchOn() {
            if (fresh) {
                engaged.enter()
            } else {
                fading.enter(from = 0.0, to = 1.0)
            }
        }

        override fun switchOff() {}

        override fun process(n: Int, left: AudioBuffer, right: AudioBuffer) {}
    }

    /** The cores at full weight, in place. */
    private inner class Engaged : State() {
        fun enter() {
            state = this
        }

        override fun switchOn() {}

        override fun switchOff() {
            if (fresh) {
                off.enter()
            } else {
                fading.enter(from = 1.0, to = 0.0)
            }
        }

        override fun process(n: Int, left: AudioBuffer, right: AudioBuffer) {
            distort(left = left, right = right, n = n)
        }
    }

    /**
     * The weight `w` of the distorted mix against the dry one moves linearly from [from] to [to] over one fade; the
     * first sample carries [from] and the sample at `fadeLen` is [to] exactly. Never entered while [fresh].
     */
    private inner class Fading : State() {
        private var from: Double = 0.0
        private var to: Double = 0.0
        private var pos: Int = 0

        fun enter(from: Double, to: Double) {
            this.from = from
            this.to = to
            pos = 0
            state = this
        }

        private fun weightNow(): Double =
            linearFadeWeight(weightFrom = from, weightTo = to, remaining = fadeLen - pos, invLength = invFadeLen)

        override fun switchOn() {
            if (to == 0.0) {
                enter(from = weightNow(), to = 1.0)
            }
        }

        override fun switchOff() {
            if (to == 1.0) {
                enter(from = weightNow(), to = 0.0)
            }
        }

        override fun process(n: Int, left: AudioBuffer, right: AudioBuffer) {
            val dL = dryL
            val dR = dryR
            val f = from
            val t = to
            val p0 = pos
            val len = fadeLen
            val inv = invFadeLen

            distort(left = left, right = right, n = n)

            val end = min(n, len - p0)

            // out = dry + w * (distorted - dry), the compressor's law (`utils/linear_crossfade.kt`).
            crossfadeLinear(
                target = left,
                base = dL,
                other = left,
                count = end,
                weightFrom = f,
                weightTo = t,
                remaining = len - p0,
                invLength = inv,
            )
            crossfadeLinear(
                target = right,
                base = dR,
                other = right,
                count = end,
                weightFrom = f,
                weightTo = t,
                remaining = len - p0,
                invLength = inv,
            )

            if (p0 + n < len) {
                pos = p0 + n

                return
            }

            if (t == 0.0) {
                // Landed on dry: the rest of the block IS the dry mix, and the life ends.
                dL.copyRangeInto(destination = left, destinationOffset = end, startIndex = end, endIndex = n)
                dR.copyRangeInto(destination = right, destinationOffset = end, startIndex = end, endIndex = n)
                off.enter()
            } else {
                engaged.enter()
            }
        }
    }

    private val off = Off()
    private val engaged = Engaged()
    private val fading = Fading()

    /** The current state; this initializer is the one entry into Off that does not run [Off.enter]. */
    private var state: State = off

    /** Test seam: the current state OBJECT, for the state identity rows. Production never reads it. */
    internal val currentState: Any get() = state

    /** True while the stage distorts (Engaged or Fading), for the specs. */
    internal val isOn: Boolean get() = state !== off

    /**
     * Applies the orbit's distort settings ([DistortConfig], filled by the writer). An amount that is not finite or
     * is at or below 0 is OFF. Called on every block the orbit has an owner, so an unchanged amount is free (a
     * same-value retarget of [KnobGlide] does nothing).
     */
    fun configure(config: DistortConfig) {
        val amount = config.amount

        // NaN-guard on a value the author can write: a non-finite amount was never set, and an unset stage is off.
        // `isFiniteInline`, the stdlib `isFinite()`'s exact twin without the call (engine follow-up 45): this runs on
        // every block the orbit has an owner. Measured, it did not box here in any profile tried (single-case and
        // mixed, V8's own inlining budget and a squeezed one); the swap is the house rule for a per-block path.
        if (!amount.isFiniteInline() || amount <= 0.0) {
            state.switchOff()

            return
        }

        amountGlide.retarget(amount)
        state.switchOn()
    }

    /**
     * One block of the cores, in place. A settled amount hands its drive to the cores; a moving one has the cores
     * ramp the drive from the value the last block ended on to this block's ([DistortionCore.processRamped]), where the
     * settled drive is applied too, so the two kinds of block meet without a seam.
     */
    private fun distort(left: AudioBuffer, right: AudioBuffer, n: Int) {
        val amountFrom = amountGlide.value
        val amountTo = amountGlide.advance()
        val driveTo = DistortionCore.drive(amountTo)

        if (amountFrom == amountTo) {
            coreL.process(buffer = left, offset = 0, length = n, drive = driveTo)
            coreR.process(buffer = right, offset = 0, length = n, drive = driveTo)

            return
        }

        val driveFrom = DistortionCore.drive(amountFrom)

        coreL.processRamped(buffer = left, offset = 0, length = n, driveFrom = driveFrom, driveTo = driveTo)
        coreR.processRamped(buffer = right, offset = 0, length = n, driveFrom = driveFrom, driveTo = driveTo)
    }

    override fun process(ctx: KatalystContext) {
        fresh = false

        val n = ctx.blockFrames
        val left = ctx.mixBuffer.left
        val right = ctx.mixBuffer.right

        if (state === fading) {
            // The dry is only read by a fade.
            ensureDry(n)
            left.copyRangeInto(destination = dryL, destinationOffset = 0, startIndex = 0, endIndex = n)
            right.copyRangeInto(destination = dryR, destinationOffset = 0, startIndex = 0, endIndex = n)
        }

        state.process(n = n, left = left, right = right)
    }

    /** Grows the dry scratch buffers to [n] frames once, for a direct caller's longer block. */
    private fun ensureDry(n: Int) {
        if (dryL.size < n) {
            dryL = AudioBuffer(n)
            dryR = AudioBuffer(n)
        }
    }

    /**
     * True while the stage can still put audio into a silent input: while it distorts, while a core's DC blocker still
     * holds the offset it was removing. An asymmetric shape
     * (`tube`, `rectify`, ...) makes DC while signal flows, and after the input stops the blocker's output decays
     * from that offset over tens of milliseconds. The cylinder's deactivation would see it in the mix anyway, but
     * a chain swap retires the leaving chain at the end of its input ramp unless it reports a tail (`ChainSwap`),
     * and that cut the decay to 0 in one sample (round 1: up to 0.05 on `tube`, 0.37 on `rectify`). In Off the
     * cores are reset and hold nothing.
     */
    override fun hasTail(): Boolean =
        state !== off && (coreL.dcHoldsEnergy(SILENCE_FLOOR) || coreR.dcHoldsEnergy(SILENCE_FLOOR))

    /** A HARD cut to [Off]: the cores cleared, and the next switch snaps again. */
    override fun reset() {
        off.enter()
        fresh = true
    }

    /** Rents nothing, so retiring is the clean slate. */
    override fun retire() {
        reset()
    }
}

/**
 * What a distort stage is configured with on every block the orbit has an owner ([KatalystDistortEffect.configure]):
 * the [amount]. Mutable and reused, as `PhaserConfig` is (the V8 allocation pass, `audio/ref/performance.md`): the
 * writer owns one, fills it at resolve and hands it over by reference, so no double crosses a call per block.
 */
class DistortConfig {
    var amount: Double = Double.NaN
}
