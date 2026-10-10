/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.peekandpoke.klang.audio_be.AudioBackendContext
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.DistortionCore
import io.peekandpoke.klang.audio_be.KnobGlide
import io.peekandpoke.klang.audio_be.Oversampler
import io.peekandpoke.klang.audio_be.distortionShapeAt
import io.peekandpoke.klang.audio_be.filters.HOUSE_DC_BLOCK_COEFF
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.utils.copyRangeInto
import io.peekandpoke.klang.audio_be.utils.crossfadeLinear
import io.peekandpoke.klang.audio_be.utils.linearFadeWeight
import io.peekandpoke.klang.audio_bridge.DistortionShapes
import io.peekandpoke.klang.audio_bridge.constants.KNOB_GLIDE_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.SILENCE_FLOOR
import kotlin.math.min

/**
 * The bus distortion, an insert: the voice's distort at the bus position (`KatalystStageDsl.Distort`,
 * `docs/tasks-archive/2026-10/20261009-katalyst-distort-stage.md`). It bends the orbit mix in place, left and right each through a core
 * of their own, so the notes of the bus meet inside one curve: the glue of a master saturator, the clipper before
 * a limiter, the amp fed by a whole chord.
 *
 * **The law** is the voice's fused distort, [DistortionCore] at the drive `10^(amount * 1.2)`
 * ([DistortionCore.drive]): the drive inside the oversampler, the DC blocker after, no soft cap. One
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
 * to the cores. Both apply it at the same place, inside the oversampler. The shape and the oversampling factor are
 * fixed with the chain.
 *
 * **Latency.** With oversampling the distorted path runs late by the oversampler's group delay
 * ([Oversampler.groupDelaySamples]: 3.07 frames at 2x, 4.40 at 4x, 5.06 at 8x, the IIR half-band's low-frequency
 * delay), and the stage delays the orbit by that in EVERY state: Off passes the dry mix through PHASE TWINS, an
 * unshaped round trip of the same kernel per channel, and a fade blends against that twin, so switching never moves
 * the orbit in time and the two paths share their phase at every frequency, the top octave included (the IIR's delay
 * rises toward the top, so an integer delay ring matched only the bass and the mids: at the middle of a fade the old
 * ring notched near 18 kHz). [latencyFrames] reports the delay rounded, for the chain (3, 4, 5 at 2x, 4x, 8x).
 * Nothing compensates the latency, as with the compressor's lookahead: the
 * orbit, and at the output the playback, runs late by it. Without oversampling (the default) there is none.
 *
 * The lifecycle is a state machine (`docs/plans/effect-state-machines.md`), the shape of
 * [KatalystCompressorEffect]; the table on [State] is authoritative for its edges.
 *
 * **The four questions of the plan:**
 * 1. *What outlives its states:* the two cores, the amount glide, the dry scratch buffers, the phase twins, the snap
 *    flag [fresh], and the tail count [quietFrames].
 * 2. *The record of a finished life:* the cores' filter memories and the amount glide, forgotten in [Off.enter].
 *    The next life starts from reset cores and its amount snaps.
 * 3. *The Off precondition:* the output is the dry mix (through the phase twins with oversampling). [Off] is entered
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
    /** The oversampling factor, the voice's knob ([Oversampler.factorToStages]): 0 or 1 is none. */
    oversampleFactor: Int = 0,
) : KatalystEffect, KatalystLatentEffect {

    private val fadeLen: Int = (sampleRate * KNOB_GLIDE_SECONDS).toInt().coerceAtLeast(1)

    private val invFadeLen: Double = 1.0 / fadeLen

    private val stages: Int = Oversampler.factorToStages(oversampleFactor)

    private val coreL = DistortionCore(
        shape = distortionShapeAt(shapeIndex.toDouble()),
        oversampleStages = stages,
        dcBlockCoefficient = HOUSE_DC_BLOCK_COEFF,
    )

    private val coreR = DistortionCore(
        shape = distortionShapeAt(shapeIndex.toDouble()),
        oversampleStages = stages,
        dcBlockCoefficient = HOUSE_DC_BLOCK_COEFF,
    )

    /**
     * The cores' oversampled work buffers, leased per block. The lease for this stage's factor is built here, with
     * the effect, so the audio thread never builds one.
     */
    private val scratch: ScratchBuffers = ScratchBuffers(blockFrames).also { it.oversample(1 shl stages) }

    /** Frames this stage delays the orbit by, in every state: the oversampler's group delay, rounded; 0 without. */
    override val latencyFrames: Int = Oversampler.latencyFrames(stages)

    /** The cores' oversampler, the whole of [latencyFrames]; empty without. */
    override val oversamplers: List<Int> = if (stages > 0) listOf(stages) else emptyList()

    /** True with oversampling: the dry mix then runs through the phase twins. */
    private val latent: Boolean = stages > 0

    /**
     * How long a quiet input may still leave audio inside the stage, in input frames: the ring-out of the IIR
     * half-band, in the distorted path and in the dry twin alike, down to 120 dB under the input
     * ([Oversampler.tailFrames], 268 frames with oversampling; the tail rule of `audio/ref/katalyst.md`: err towards
     * holding longer). 0 without oversampling.
     */
    private val holdFrames: Int = Oversampler.tailFrames(stages)

    /** Frames since the last input block with a sample above [SILENCE_FLOOR], held at [holdFrames]. */
    private var quietFrames: Int = holdFrames

    /**
     * The PHASE TWINS of the cores' oversamplers, one per channel, null without oversampling: the dry mix runs through
     * an unshaped round trip of the same kernel, so dry and distorted share their phase at every frequency and a fade
     * between them cannot notch (2026-10-10, the maintainer's choice: "IIR + phase-matched pads"; an integer delay ring
     * matched only the low-frequency delay, and the IIR's delay rises toward the top). Running in every state, as the
     * rings did, so the dry is continuous whenever a state needs it.
     */
    private val twinL: Oversampler? = if (stages > 0) Oversampler(stages) else null
    private val twinR: Oversampler? = if (stages > 0) Oversampler(stages) else null

    private val amountGlide = KnobGlide(sampleRate = sampleRate, blockFrames = blockFrames)

    /** True from construction and from [reset] until a block has been processed: a switch then acts at once. */
    private var fresh: Boolean = true

    /** The dry mix of the current block (delayed by [latencyFrames]). Engine blocks are [blockFrames] long. */
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
     *
     * With oversampling, read "dry" as "the delayed dry".
     */
    private sealed class State {
        abstract fun switchOn()

        abstract fun switchOff()

        abstract fun process(n: Int, left: AudioBuffer, right: AudioBuffer)
    }

    /** No distortion: the mix passes untouched, or with oversampling as the delayed dry mix. */
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

        override fun process(n: Int, left: AudioBuffer, right: AudioBuffer) {
            if (!latent) {
                return
            }

            dryL.copyRangeInto(destination = left, destinationOffset = 0, startIndex = 0, endIndex = n)
            dryR.copyRangeInto(destination = right, destinationOffset = 0, startIndex = 0, endIndex = n)
        }
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
        if (!amount.isFinite() || amount <= 0.0) {
            state.switchOff()

            return
        }

        amountGlide.retarget(amount)
        state.switchOn()
    }

    /**
     * One block of the cores, in place. A settled amount hands its drive to the cores; a moving one has the cores
     * ramp the drive from the value the last block ended on to this block's ([DistortionCore.processRamped]), inside
     * the oversampler, where the settled drive is applied too, so the two kinds of block meet without a seam.
     */
    private fun distort(left: AudioBuffer, right: AudioBuffer, n: Int) {
        val amountFrom = amountGlide.value
        val amountTo = amountGlide.advance()
        val driveTo = DistortionCore.drive(amountTo)

        if (amountFrom == amountTo) {
            coreL.process(buffer = left, offset = 0, length = n, drive = driveTo, scratchBuffers = scratch)
            coreR.process(buffer = right, offset = 0, length = n, drive = driveTo, scratchBuffers = scratch)

            return
        }

        val driveFrom = DistortionCore.drive(amountFrom)

        coreL.processRamped(buffer = left, offset = 0, length = n, driveFrom = driveFrom, driveTo = driveTo, scratchBuffers = scratch)
        coreR.processRamped(buffer = right, offset = 0, length = n, driveFrom = driveFrom, driveTo = driveTo, scratchBuffers = scratch)
    }

    override fun process(ctx: KatalystContext) {
        fresh = false

        val n = ctx.blockFrames
        val left = ctx.mixBuffer.left
        val right = ctx.mixBuffer.right

        ensureDry(n)
        captureDry(n = n, left = left, right = right)

        if (latent) {
            countQuiet(n = n, left = left, right = right)
        }

        state.process(n = n, left = left, right = right)
    }

    /**
     * Fills [dryL] / [dryR] with this block's dry mix: the input itself, or with oversampling the input through the
     * phase twins (an unshaped round trip of the cores' kernel). The twins run in every state, so the dry is continuous
     * whenever a state needs it.
     */
    private fun captureDry(n: Int, left: AudioBuffer, right: AudioBuffer) {
        val dL = dryL
        val dR = dryR

        if (!latent && state !== fading) {
            // Without oversampling the dry is only read by a fade.
            return
        }

        left.copyRangeInto(destination = dL, destinationOffset = 0, startIndex = 0, endIndex = n)
        right.copyRangeInto(destination = dR, destinationOffset = 0, startIndex = 0, endIndex = n)

        val tL = twinL
        val tR = twinR

        if (tL != null && tR != null) {
            scratch.oversample(tL.factor).use { work ->
                tL.upsample(source = dL, offset = 0, length = n, work = work)
                tL.decimate(work = work, target = dL, offset = 0, length = n)
                tR.upsample(source = dR, offset = 0, length = n, work = work)
                tR.decimate(work = work, target = dR, offset = 0, length = n)
            }
        }
    }

    /** Updates [quietFrames] from the block about to enter the stage. */
    private fun countQuiet(n: Int, left: AudioBuffer, right: AudioBuffer) {
        val floor = SILENCE_FLOOR

        for (i in 0 until n) {
            val l = left[i]
            val r = right[i]

            if (l > floor || l < -floor || r > floor || r < -floor) {
                quietFrames = 0

                return
            }
        }

        quietFrames = min(quietFrames + n, holdFrames)
    }

    /** Grows the dry scratch buffers to [n] frames once, for a direct caller's longer block. */
    private fun ensureDry(n: Int) {
        if (dryL.size < n) {
            dryL = AudioBuffer(n)
            dryR = AudioBuffer(n)
        }
    }

    /**
     * True while the stage can still put audio into a silent input:
     * - with oversampling, while the twins or the cores' oversamplers may still ring out ([holdFrames]);
     * - while it distorts, while a core's DC blocker still holds the offset it was removing. An asymmetric shape
     *   (`tube`, `rectify`, ...) makes DC while signal flows, and after the input stops the blocker's output decays
     *   from that offset over tens of milliseconds. The cylinder's deactivation would see it in the mix anyway, but
     *   a chain swap retires the leaving chain at the end of its input ramp unless it reports a tail (`ChainSwap`),
     *   and that cut the decay to 0 in one sample (round 1: up to 0.05 on `tube`, 0.37 on `rectify`). In Off the
     *   cores are reset and hold nothing.
     */
    override fun hasTail(): Boolean {
        if (latent && quietFrames < holdFrames) {
            return true
        }

        return state !== off && (coreL.dcHoldsEnergy(SILENCE_FLOOR) || coreR.dcHoldsEnergy(SILENCE_FLOOR))
    }

    /** A HARD cut to [Off]: the cores and the twins cleared, and the next switch snaps again. */
    override fun reset() {
        off.enter()
        twinL?.reset()
        twinR?.reset()
        quietFrames = holdFrames
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
