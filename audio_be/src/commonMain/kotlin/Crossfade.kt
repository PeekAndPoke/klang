/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import kotlin.math.abs

/**
 * The dual-chain crossfade: the ramp two effect chains are blended over while both hear the same
 * mix.
 *
 * Owned by [ChainSwap], the one swap both hosts of a whole chain run under live audio: every orbit
 * bus (`Cylinder`, since phase 3 step 12 C1) and the master bus (`MasterBus`, since step 12 C4). One
 * instance per swap, created once. Katalyst step 3b, 2026-09-17 (`docs/tasks/katalyst-dsl.md` §D3)
 * extracted it from `MasterBus`, which had carried it alone since the master DSL shipped.
 *
 * **The blend is LINEAR, not equal-power.** Both chains process the *same* material, so their outputs
 * are highly correlated; amplitude-complementary weights sum correctly, while an equal-power
 * (sin/cos) law would push correlated material up to +3 dB mid-fade. Equal-power is for
 * *uncorrelated* sources.
 *
 * **The start is block-quantized, the ramp is per-sample.** The per-sample ramp is what prevents
 * zipper noise; the start of the fade rounds to the current block (at most ~2.7 ms at 128 frames /
 * 48 kHz) because the shared DSP processes buffers from index 0. Inaudible against a 60 ms fade,
 * and it keeps the effect classes untouched.
 *
 * **One law: the outgoing chain's INPUT ramps down, the incoming chain's OUTPUT ramps up.** The
 * outgoing chain is handed a shrinking copy of the mix ([rampDown]) and its output is added at full
 * weight, the incoming chain's output scaled by the rising weight ([rampUpAndAdd]). The reason is
 * the drain: the outgoing chain is not retired at the end of the fade, it lets the echoes and the
 * room it had already scheduled ring out ([ChainSwap]). A chain whose OUTPUT has just been ramped
 * to zero and is then re-added at full weight steps by the whole level of that tail, measured at
 * ~0.045 rms on a loud chord with a wet room, which is the click the fade exists to prevent.
 * Ramping the INPUT reaches zero input exactly where the drain takes over, so the boundary is
 * continuous by construction and the tail is never scaled. Decided 2026-09-17 (step 3b) for the
 * orbit; the master blended the two OUTPUTS and cut the outgoing chain at the fade's end until
 * step 12 C4 (decision (f)) moved it onto this law.
 *
 * Three properties of that shape a reader should not have to rediscover:
 *  - The dry path crossfades linearly through both chains' inserts as long as those inserts are
 *    LINEAR. A compressor is not: fed a shrinking input it releases and holds its output up, so the
 *    sum can bulge mid-fade by up to the gain reduction it gives back. Bounded by the fade and by
 *    the compressor's own release. At the master this is a limiter giving its reduction back
 *    across the fade.
 *  - A chain with no delay and no reverb retires one block after the ramp ends, so whatever its INSERTS
 *    still hold is dropped at that moment: a resonator's ring, a `KatalystFilterSwap` crossfade in
 *    flight. Silent input has just reached it, so the residue is small; if it is ever heard, the
 *    fix is `hasTail` on those stages, not a longer fade.
 *  - The drained tail bypasses the INCOMING chain's INSERTS: it is added to the mix after them, so
 *    the new chain's compressor and phaser do not govern the old chain's ring-out. Its DUCK does:
 *    the duck pass runs on the whole orbit mix, after the tail has been added
 *    (`Cylinder.processDuck`). Whether a new chain's duck should pump the previous chain's ring-out
 *    is a taste call, not an oversight; it is recorded here rather than decided.
 *
 * **Late chains: the two weights meet at the OUTPUT** (phase 3 step 12 C2). A chain with a
 * lookahead compressor delays its output by `KatalystChain.latencyFrames`. The leaving chain's
 * weight goes in at its INPUT ([rampDown]) and reaches the output its latency D_L later; the
 * arriving chain's weight is applied at its output ([rampUpAndAdd]). For the two to stay
 * complementary where they are heard, both must be the same ramp at the output, delayed by
 * `M = max(D_A, D_L)`: the leaving input ramp is delayed by [outgoingInputDelayFrames] `= M - D_L`,
 * the incoming weight by [incomingDelayFrames] `= M`, and the fade completes M after the ramp
 * itself. At the output the leaving chain is weighted `1 - r(t - M)` and the arriving one
 * `r(t - M)`, which sum to exactly one: below any threshold the swap is an ordinary linear
 * crossfade from `x(t - D_L)` to `x(t - D_A)`, and the jump in time between two latencies happens
 * under it (with the usual comb of two copies mid-fade when the latencies differ). Before step 12
 * C2's round 2 the leaving ramp stayed undelayed and a dry-to-late swap dipped to 1/6 of the level
 * for 10 ms, a late-to-dry one swelled by 5.3 dB. At no latency on either side both delays are 0
 * and every weight is what it always was.
 *
 * **What is NOT here: the parked request.** Each host parks at most one request as a registry key
 * (a name not registered yet, or one waiting behind a fade or a drain), latest wins, and offers it
 * again every block; it is the host's, not a state of [ChainSwap] (step 12 decision (e)).
 */
internal class Crossfade(sampleRate: Int) {

    /** Frames the ramp spans. At least one, so a sample rate of 0 cannot make it divide by zero. */
    private val totalFrames: Int = (XFADE_SECONDS * sampleRate).toInt().coerceAtLeast(1)

    /** Frames already elapsed in the running fade. */
    private var pos: Int = 0

    /**
     * How many frames late the INCOMING weight starts, set by [restart]: `max(D_A, D_L)`, the
     * later of the two chains' latencies (the class KDoc). 0 when neither chain has a lookahead.
     */
    private var incomingDelayFrames: Int = 0

    /**
     * How many frames late the OUTGOING chain's input ramp ([rampDown]) starts, set by [restart]:
     * `max(D_A, D_L) - D_L`, so its fade-out reaches the output together with the incoming ramp.
     */
    private var outgoingInputDelayFrames: Int = 0

    /**
     * Where [pos] stood when the current block's [rampUpAndAdd] started, so a SECOND per-sample
     * pass over the same block can use the SAME weights ([blendHeld]). The duck runs after the
     * orbit's mix is formed, in another pass, and its weights have to line up with the mix's.
     */
    private var blockFrom: Int = 0

    /** True once the incoming chain has reached full weight, [incomingDelayFrames] after the ramp itself. */
    val isComplete: Boolean get() = pos >= totalFrames + incomingDelayFrames

    /**
     * True while the ramp has not moved yet: the block the swap was decided in, where the two
     * chains still stand at their starting weights and a host may still change what it hands them
     * without a step (`Cylinder.updateFromVoice`'s late duck takeover).
     */
    val isAtStart: Boolean get() = pos == 0

    /**
     * Starts (or restarts) the ramp at weight 0 for the incoming chain. [incomingDelayFrames] and
     * [outgoingInputDelayFrames] place the two weights for chains with latency (the class KDoc);
     * both are coerced to at least 0, and both are 0 for chains without a lookahead.
     */
    fun restart(incomingDelayFrames: Int, outgoingInputDelayFrames: Int) {
        pos = 0
        blockFrom = 0
        this.incomingDelayFrames = incomingDelayFrames.coerceAtLeast(0)
        this.outgoingInputDelayFrames = outgoingInputDelayFrames.coerceAtLeast(0)
    }

    /**
     * The ramp at [d] frames into it: 0 before it starts, exactly 1 from its end, `d / total` in
     * between. At a delay of 0 this is the weight every fade had before step 12 C2.
     */
    @Suppress("NOTHING_TO_INLINE")
    private inline fun rampAt(d: Int, total: Double): Double =
        if (d >= totalFrames) 1.0 else if (d <= 0) 0.0 else d / total

    /**
     * Blends [frames] of [outgoing] and [incoming] into [target] with the weights of the block
     * [rampUpAndAdd] has just written (the incoming one rising, the outgoing one its complement),
     * without advancing the ramp: a second per-sample pass over the same block. [target] may alias
     * either source; both samples are read before either is written.
     *
     * What the duck pass needs (`Cylinder.processDuck`): a duck entering or leaving service has to
     * be crossfaded into or out of the orbit's gain, and its weights must be the ones the two
     * chains were blended with, not the next block's.
     *
     * Valid ONLY inside the block [rampUpAndAdd] ran: afterwards it replays that block's weights,
     * so a host that stops calling it has to stop for good (the fade's exit edge in `ChainSwap`
     * drops the duck handover for exactly that reason).
     */
    fun blendHeld(target: StereoBuffer, incoming: StereoBuffer, outgoing: StereoBuffer, frames: Int) {
        val targetLeft = target.left
        val targetRight = target.right
        val inLeft = incoming.left
        val inRight = incoming.right
        val outLeft = outgoing.left
        val outRight = outgoing.right
        val total = totalFrames.toDouble()
        var at = blockFrom

        for (i in 0 until frames) {
            val t = rampAt(at - incomingDelayFrames, total)
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
            val t = rampAt(at - outgoingInputDelayFrames, total)
            val u = 1.0 - t

            // Sterilised tap, the reason [blendHeld] gives: `Inf * 0.0` is NaN. Not a NaN shield for the
            // chain, which reads the same sample unsterilised through the live mix; just this
            // multiplication not inventing one.
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
            val t = rampAt(at - incomingDelayFrames, total)

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
