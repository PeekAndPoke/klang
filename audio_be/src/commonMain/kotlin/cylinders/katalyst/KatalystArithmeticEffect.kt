/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.KnobGlide
import io.peekandpoke.klang.audio_be.ignitor.absLaw
import io.peekandpoke.klang.audio_be.ignitor.divLaw
import io.peekandpoke.klang.audio_be.ignitor.divisorOf
import io.peekandpoke.klang.audio_be.ignitor.maxLaw
import io.peekandpoke.klang.audio_be.ignitor.minLaw
import io.peekandpoke.klang.audio_be.ignitor.minusLaw
import io.peekandpoke.klang.audio_be.ignitor.plusLaw
import io.peekandpoke.klang.audio_be.ignitor.powLaw
import io.peekandpoke.klang.audio_be.ignitor.timesLaw

/** Which law a [KatalystArithmeticEffect] applies; one per arithmetic stage of `KatalystStageDsl`. */
internal enum class KatalystArithmeticOp {
    PLUS,
    MINUS,
    TIMES,
    DIV,
    POW,
    MIN,
    MAX,
    ABS,
}

/**
 * The arithmetic stages on the bus (`docs/tasks/katalyst-arithmetic.md`): every sample of both channels goes through
 * the SAME per-sample law as the Ignitor's node of that operator (`_arithmetic_laws.kt`), so a settled operand gives
 * the sample the authored Ignitor node gives, bit for bit, clamps included. (A registered voice runs its optimized
 * tree, where a constant divide is a multiply by the reciprocal: one rounding apart, within `OPTIMIZER_PARITY`.)
 * Memoryless: no latency, no tail.
 *
 * **The operand glides**, per sample, over `KNOB_GLIDE_SECONDS` ([KnobGlide], the ramp the fader uses): a `Param`
 * operand moved by `katp` would otherwise step once per block and zip. Each sample takes the law with the operand at
 * that sample, written from the block's END so the block lands on its value bit for bit. A constant operand never
 * moves (a new number is a new chain), so it is the plain law on every sample. The first value snaps, as on the fader.
 * For every operator but [KatalystArithmeticOp.DIV] the output at an operand between two values lies between the
 * outputs at those values, so a linear glide invents nothing (a -0.0 sample under a negative power excepted: it
 * flips between the two signed `safeOut` bounds, which both endpoints already put on the bus).
 *
 * **[KatalystArithmeticOp.DIV] glides its RECIPROCAL**, the level `1 / divisor` (0 for a divisor of 0, the house rule),
 * and multiplies by it while it moves. A linear glide of the divisor itself would pass through divisors near zero
 * that the author never wrote: from `div(0)` to `div(1)` it opened at about +67 dB, and from `div(-1)` to `div(1)` at
 * 48 kHz one sample landed on the `safeDiv` floor, a gain of about +300 dB (round 1 review). Gliding the level, a move from 1 to -1 passes through silence and one to 0 is a
 * fade. Once settled it is the true division again, [divLaw] with the divisor as written.
 *
 * **A non-finite operand is unset** (the bus's NaN rule, `/dsl-design` §4; the Ignitor would pass the NaN on, which on
 * a bus would take the whole orbit). Unset is the operator's identity, 0 for [KatalystArithmeticOp.PLUS] and
 * [KatalystArithmeticOp.MINUS], 1 for [KatalystArithmeticOp.TIMES], [KatalystArithmeticOp.DIV] and
 * [KatalystArithmeticOp.POW], and the operand glides there and back like any other move. A cap or a floor has no
 * identity, so an unset [KatalystArithmeticOp.MIN] or [KatalystArithmeticOp.MAX] passes the bus untouched: the bound
 * lets go at the next block, and its first operand after that arrives at once (a bound cannot glide from "no
 * bound"). Both are a step on a signal past the bound, accepted: reachable only by writing a non-finite value into the
 * slot while the orbit sounds.
 *
 * **Raw.** No clamp beyond the laws' own (`safeOut` on times, div and pow); a negative factor, an offset that keeps
 * the orbit from ever going silent, a power that folds the signal: the author's business.
 */
internal class KatalystArithmeticEffect(
    private val op: KatalystArithmeticOp,
    sampleRate: Int,
    /** The frames of one render block, pinned to 128 in the engine. */
    blockFrames: Int,
) : KatalystEffect {

    /** The operand (for DIV its reciprocal): where it stands, where it is going, the snap of a fresh stage. */
    private val glide = KnobGlide(sampleRate = sampleRate, blockFrames = blockFrames)

    /** The value an unset operand reads as; NaN for MIN and MAX, which have none. The one table of identities. */
    private val identity: Double = when (op) {
        KatalystArithmeticOp.PLUS, KatalystArithmeticOp.MINUS -> 0.0
        KatalystArithmeticOp.TIMES, KatalystArithmeticOp.DIV, KatalystArithmeticOp.POW -> 1.0
        KatalystArithmeticOp.MIN, KatalystArithmeticOp.MAX, KatalystArithmeticOp.ABS -> Double.NaN
    }

    /** True while a MIN or MAX has no finite operand: the stage passes the bus. */
    private var unset: Boolean = !identity.isFinite()

    /** DIV only: the divisor as written (finite), which the settled blocks divide by. */
    private var divisor: Double = 1.0

    init {
        if (identity.isFinite()) {
            glide.retarget(identity)
        }
    }

    /** Sets the operand; see the class KDoc for the glide and for what a non-finite one means. */
    fun configure(operand: Double) {
        // NaN-guard on a value the author can write: a non-finite operand is unset (class KDoc).
        val written = if (operand.isFinite()) operand else identity

        if (!written.isFinite()) {
            unset = true

            return
        }

        // DIV glides the level, so what the glide holds is the reciprocal (class KDoc).
        val target = if (op == KatalystArithmeticOp.DIV) {
            divisor = written
            if (written == 0.0) 0.0 else 1.0 / divisorOf(written)
        } else {
            written
        }

        if (unset) {
            unset = false
            glide.settleAt(target)

            return
        }

        glide.retarget(target)
    }

    override fun reset() {
        glide.reset()
        divisor = 1.0

        if (identity.isFinite()) {
            glide.retarget(identity)
        } else {
            unset = true
        }
    }

    /** Memoryless: nothing of one block reaches the next. */
    override fun hasTail(): Boolean = false

    /** Rents nothing, so retiring is the clean slate. */
    override fun retire() {
        reset()
    }

    override fun process(ctx: KatalystContext) {
        val left = ctx.mixBuffer.left
        val right = ctx.mixBuffer.right
        val frames = ctx.blockFrames

        if (op == KatalystArithmeticOp.ABS) {
            for (i in 0 until frames) {
                left[i] = absLaw(left[i])
                right[i] = absLaw(right[i])
            }

            return
        }

        val from = glide.value
        val end = glide.advance()

        if (unset) {
            return
        }

        when (op) {
            KatalystArithmeticOp.PLUS ->
                applyLaw(left = left, right = right, frames = frames, from = from, end = end) { x, v -> plusLaw(a = x, b = v) }

            KatalystArithmeticOp.MINUS ->
                applyLaw(left = left, right = right, frames = frames, from = from, end = end) { x, v -> minusLaw(a = x, b = v) }

            KatalystArithmeticOp.TIMES ->
                applyLaw(left = left, right = right, frames = frames, from = from, end = end) { x, v -> timesLaw(a = x, b = v) }

            KatalystArithmeticOp.DIV -> if (from == end) {
                val d = divisor

                applyLaw(left = left, right = right, frames = frames, from = d, end = d) { x, v -> divLaw(a = x, b = v) }
            } else {
                // Moving: the glide holds the level, so this block multiplies (class KDoc).
                applyLaw(left = left, right = right, frames = frames, from = from, end = end) { x, v -> timesLaw(a = x, b = v) }
            }

            KatalystArithmeticOp.POW ->
                applyLaw(left = left, right = right, frames = frames, from = from, end = end) { x, v -> powLaw(a = x, b = v) }

            KatalystArithmeticOp.MIN ->
                applyLaw(left = left, right = right, frames = frames, from = from, end = end) { x, v -> minLaw(a = x, b = v) }

            KatalystArithmeticOp.MAX ->
                applyLaw(left = left, right = right, frames = frames, from = from, end = end) { x, v -> maxLaw(a = x, b = v) }

            KatalystArithmeticOp.ABS -> Unit
        }
    }

    /**
     * Applies [law] in place with the operand ramping from [from] to [end] across the block, the bus sample FIRST
     * (the laws are not value-commutative under NaN, see `minLaw`). Inline, so each arm is its own loop with its law
     * inlined: no lambda object, no boxing.
     */
    private inline fun applyLaw(
        left: AudioBuffer,
        right: AudioBuffer,
        frames: Int,
        from: Double,
        end: Double,
        law: (Double, Double) -> Double,
    ) {
        if (end == from) {
            for (i in 0 until frames) {
                left[i] = law(left[i], end)
                right[i] = law(right[i], end)
            }

            return
        }

        val step = (end - from) / frames
        val last = frames - 1

        for (i in 0 until frames) {
            val v = end - step * (last - i)

            left[i] = law(left[i], v)
            right[i] = law(right[i], v)
        }
    }
}
