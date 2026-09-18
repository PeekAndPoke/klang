/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import kotlin.math.abs

/**
 * The dual-chain crossfade: the ramp two effect chains are blended over while both process the
 * same input.
 *
 * Owned by the two hosts that swap a whole chain under live audio, the master bus
 * (`MasterBus`) and every orbit bus (`Cylinder`). One instance per host, created once. Katalyst
 * step 3b, 2026-09-17 (`docs/tasks/katalyst-dsl.md` §D3) extracted it from `MasterBus`, which had
 * carried it alone since the master DSL shipped; the extraction is byte-identical, measured on a
 * master swap rendered before and after.
 *
 * **The blend is LINEAR, not equal-power.** Both chains process the *same* input, so their outputs
 * are highly correlated; amplitude-complementary weights sum correctly, while an equal-power
 * (sin/cos) law would push correlated material up to +3 dB mid-fade. Equal-power is for
 * *uncorrelated* sources.
 *
 * **The start is block-quantized, the ramp is per-sample.** The per-sample ramp is what prevents
 * zipper noise; the start of the fade rounds to the current block (at most ~2.7 ms at 128 frames /
 * 48 kHz) because the shared DSP processes buffers from index 0. Inaudible against a 60 ms fade,
 * and it keeps the effect classes untouched.
 *
 * **Two shapes, one ramp.** The master blends the two chains' OUTPUTS ([blend]); a cylinder ramps
 * the outgoing chain's INPUT down instead and adds its output at full weight ([rampDown] +
 * [rampUpAndAdd]). The reason is the orbit's drain: a cylinder does not retire the outgoing chain
 * at the end of the fade, it lets the echoes and the room it had already scheduled ring out. A
 * chain whose OUTPUT has just been ramped to zero and is then re-added at full weight steps by the
 * whole level of that tail, measured at ~0.045 rms on a loud chord with a wet room, which is the
 * click the fade exists to prevent. Ramping the INPUT reaches zero input exactly where the drain
 * takes over, so the boundary is continuous by construction and the tail is never scaled. Decided
 * 2026-09-17 (step 3b); the master keeps the blend it shipped with, where the outgoing chain IS cut
 * and a ramped output is therefore the right shape.
 *
 * Three properties of that shape a reader should not have to rediscover:
 *  - The dry path crossfades linearly through both chains' inserts as long as those inserts are
 *    LINEAR. A compressor is not: fed a shrinking input it releases and holds its output up, so the
 *    sum can bulge mid-fade by up to the gain reduction it gives back. Bounded by the fade and by
 *    the compressor's own release, and the same class of inaccuracy the master's output blend has
 *    (there the outgoing compressor sees full input to the last sample and is then cut).
 *  - A chain with no send stage retires one block after the ramp ends, so whatever its INSERTS
 *    still hold is dropped at that moment: a resonator's ring, a `KatalystFilterSwap` crossfade in
 *    flight. Silent input has just reached it, so the residue is small; if it is ever heard, the
 *    fix is `hasTail` on those stages, not a longer fade.
 *  - The drained tail bypasses the INCOMING chain's INSERTS: it is added to the mix after them, so
 *    the new chain's compressor and phaser do not govern the old chain's ring-out. Its DUCK does:
 *    the duck pass runs on the whole orbit mix, after the tail has been added
 *    (`Cylinder.processDuck`). Whether a new chain's duck should pump the previous chain's ring-out
 *    is a taste call, not an oversight; it is recorded here rather than decided.
 *
 * **What is NOT here: the retarget queue.** Both hosts keep at most one queued request and let the
 * last intent win, but the two queues are not one object: the master queues a registered master's
 * name, while a cylinder's queue also holds a name whose registration has not arrived yet and is
 * polled on every idle block, and each host's eviction consults its own. Moving them here would
 * buy indirection, not sharing (decided 2026-09-17, step 3b).
 */
internal class Crossfade(sampleRate: Int) {

    /** Frames the ramp spans. At least one, so a sample rate of 0 cannot make it divide by zero. */
    private val totalFrames: Int = (XFADE_SECONDS * sampleRate).toInt().coerceAtLeast(1)

    /** Frames already elapsed in the running fade. */
    private var pos: Int = 0

    /**
     * Where [pos] stood when the current block's [rampUpAndAdd] started, so a SECOND per-sample
     * pass over the same block can use the SAME weights ([blendHeld]). The duck runs after the
     * orbit's mix is formed, in another pass, and its weights have to line up with the mix's.
     */
    private var blockFrom: Int = 0

    /** True once the incoming chain has reached full weight. */
    val isComplete: Boolean get() = pos >= totalFrames

    /**
     * True while the ramp has not moved yet: the block the swap was decided in, where the two
     * chains still stand at their starting weights and a host may still change what it hands them
     * without a step (`Cylinder.updateFromVoice`'s late duck takeover).
     */
    val isAtStart: Boolean get() = pos == 0

    /** Starts (or restarts) the ramp at weight 0 for the incoming chain. */
    fun restart() {
        pos = 0
        blockFrom = 0
    }

    /**
     * Blends [frames] of [outgoing] and [incoming] into [target] and advances the ramp.
     *
     * [target] normally aliases one of the two sources, whichever buffer the host must end up
     * with; both samples are read before either is written, so the aliasing is safe. The incoming
     * weight rises from 0 to 1 across [totalFrames], the outgoing weight is its complement.
     */
    fun blend(target: StereoBuffer, incoming: StereoBuffer, outgoing: StereoBuffer, frames: Int) {
        pos = blendFrom(target, incoming, outgoing, frames, pos)
    }

    /**
     * [blend] with the weights of the block [rampUpAndAdd] has just written, and without advancing
     * the ramp: a second per-sample pass over the same block.
     *
     * What the duck pass needs (`Cylinder.processDuck`): a duck entering or leaving service has to
     * be crossfaded into or out of the orbit's gain, and its weights must be the ones the two
     * chains were blended with, not the next block's.
     *
     * Valid ONLY inside the block [rampUpAndAdd] ran: afterwards it replays that block's weights,
     * so a host that stops calling it has to stop for good (`Cylinder.beginDrain` clears both duck
     * states for exactly that reason).
     */
    fun blendHeld(target: StereoBuffer, incoming: StereoBuffer, outgoing: StereoBuffer, frames: Int) {
        blendFrom(target, incoming, outgoing, frames, blockFrom)
    }

    /** The blend loop, from ramp position [from]; returns where it ended. */
    private fun blendFrom(
        target: StereoBuffer,
        incoming: StereoBuffer,
        outgoing: StereoBuffer,
        frames: Int,
        from: Int,
    ): Int {
        val targetLeft = target.left
        val targetRight = target.right
        val inLeft = incoming.left
        val inRight = incoming.right
        val outLeft = outgoing.left
        val outRight = outgoing.right
        val total = totalFrames.toDouble()
        var at = from

        for (i in 0 until frames) {
            val t = if (at >= totalFrames) 1.0 else at / total
            val u = 1.0 - t

            // Sterilised taps: at the fade's endpoints one weight is exactly 0.0, and
            // `Inf * 0.0` is NaN, so a chain contributing NOTHING yet could still inject
            // NaN into the bus, which used to latch the master DC blocker downstream. A
            // large-but-finite chain output is untouched (that is the raw engine's business).
            val outL = outLeft[i]
            val outR = outRight[i]
            val inL = inLeft[i]
            val inR = inRight[i]

            targetLeft[i] = (if (abs(outL) <= Double.MAX_VALUE) outL else 0.0) * u +
                (if (abs(inL) <= Double.MAX_VALUE) inL else 0.0) * t
            targetRight[i] = (if (abs(outR) <= Double.MAX_VALUE) outR else 0.0) * u +
                (if (abs(inR) <= Double.MAX_VALUE) inR else 0.0) * t

            at++
        }

        return at
    }

    /**
     * Writes [frames] of [source] scaled by the OUTGOING weight (1 down to 0) into [target], the
     * input the chain leaving service is handed while it fades.
     *
     * Does NOT advance the ramp: this and [rampUpAndAdd] are one block's two halves, with the two
     * chains processed in between, and they must see the same weight for the same sample.
     */
    fun rampDown(target: StereoBuffer, source: StereoBuffer, frames: Int) {
        val targetLeft = target.left
        val targetRight = target.right
        val sourceLeft = source.left
        val sourceRight = source.right
        val total = totalFrames.toDouble()
        var at = pos

        for (i in 0 until frames) {
            val t = if (at >= totalFrames) 1.0 else at / total
            val u = 1.0 - t

            // Sterilised tap, the reason [blend] gives: `Inf * 0.0` is NaN. Not a NaN shield for the
            // chain, which reads the same sample unsterilised through the live send buffers; just
            // this multiplication not inventing one.
            val left = sourceLeft[i]
            val right = sourceRight[i]

            targetLeft[i] = (if (abs(left) <= Double.MAX_VALUE) left else 0.0) * u
            targetRight[i] = (if (abs(right) <= Double.MAX_VALUE) right else 0.0) * u

            at++
        }
    }

    /**
     * Scales [frames] of [target] by the INCOMING weight (0 up to 1), adds [outgoing] at FULL
     * weight, and advances the ramp.
     *
     * [target] holds the incoming chain's output and is what the host keeps; [outgoing] holds the
     * output of the chain that is leaving, which was already attenuated through its own input
     * ([rampDown]) and must not be scaled a second time.
     */
    fun rampUpAndAdd(target: StereoBuffer, outgoing: StereoBuffer, frames: Int) {
        val targetLeft = target.left
        val targetRight = target.right
        val outLeft = outgoing.left
        val outRight = outgoing.right
        val total = totalFrames.toDouble()
        var at = pos

        blockFrom = pos

        for (i in 0 until frames) {
            val t = if (at >= totalFrames) 1.0 else at / total

            val inL = targetLeft[i]
            val inR = targetRight[i]
            val outL = outLeft[i]
            val outR = outRight[i]

            targetLeft[i] = (if (abs(inL) <= Double.MAX_VALUE) inL else 0.0) * t +
                (if (abs(outL) <= Double.MAX_VALUE) outL else 0.0)
            targetRight[i] = (if (abs(inR) <= Double.MAX_VALUE) inR else 0.0) * t +
                (if (abs(outR) <= Double.MAX_VALUE) outR else 0.0)

            at++
        }

        pos = at
    }

    companion object {
        /** Crossfade length for a chain swap, on every bus. Tune by ear. */
        const val XFADE_SECONDS: Double = 0.06
    }
}
