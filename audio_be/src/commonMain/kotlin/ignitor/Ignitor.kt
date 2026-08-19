/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.AudioSample

import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Core signal generator interface.
 *
 * An Ignitor is a composable unit that writes audio samples into a buffer.
 * Each instance is per-voice and owns its own mutable state (phase, filter memory, etc.).
 * Ignitors are composed via extension functions (filters, envelopes, arithmetic, pitch modulation).
 *
 * **NOT a `fun interface`** by design — see `audio/ref/performance.md`. SAM-constructor
 * usage (`Ignitor { … }`) silently turns mutable closure-captured `var`s into Kotlin/JS
 * `ObjectRef` wrappers, with per-sample property-load cost. Stateful implementations
 * must be regular classes with explicit fields; stateless ones can use anonymous
 * `object : Ignitor { override fun generate(…) { … } }` if a class is overkill.
 */
interface Ignitor {
    /**
     * @param buffer where to write output samples
     * @param freqHz base frequency in Hz (used by oscillators; effects may ignore it)
     * @param ctx per-voice rendering context (timing, block params, scratch buffers)
     */
    fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext)

    /**
     * The block-constant scalar value of this signal, or `null` if it varies within the block.
     *
     * Block-constant leaves ([ConstantIgnitor], [ParamIgnitor], [FreqIgnitor]) and pure pointwise
     * combinators (`plus`/`times`/… folding block-constant children) return their value here; everything
     * else (oscillators, filters, envelopes, LFOs) returns `null` (the default). Lets control-rate readers
     * take the scalar directly instead of rendering a scratch buffer, and lets the pulse `duty` path pick
     * the bake-once render over per-sample PWM.
     *
     * CONTRACT (load-bearing since the constant-fold in `plus`/`times` consumes this on the
     * AUDIO path, not just for control-rate reads): an override MUST
     *  1. be pure — no state advanced, no side effects, no `ctx` mutation; callable any number
     *     of times per block, including zero;
     *  2. be bit-identical to the node's own [generate] output for every sample in
     *     `[offset, offset+length)` of the same block.
     * A node that is merely *slowly varying* must return `null` — a non-null value here turns
     * `x * node` into a stepped per-block multiply with no spec failing loudly.
     */
    fun controlRateValueOrNull(freqHz: Double, ctx: IgniteContext): Double? = null

    /**
     * Structural block-constancy: `true` iff [controlRateValueOrNull] returns non-null for every
     * block (the VALUE may still change between blocks, e.g. [FreqIgnitor] under detune). Purely
     * structural, so implementations compute it ONCE at construction — letting hot paths gate
     * their fold branches without paying a per-block subtree walk and a boxed `Double?` per query
     * on the non-folding side (JVM boxes the nullable return; JS does not).
     * Must agree with [controlRateValueOrNull]'s nullability — the scalar-parity specs pin both.
     */
    val isBlockConstant: Boolean get() = false

    /**
     * The signal's value at block start ([IgniteContext.offset]) — for callers that need a single
     * control-rate value (oscillator freq / analog / duty params, detune amount, unison spread).
     *
     * Uses [controlRateValueOrNull] when available; otherwise renders a scratch buffer and reads one
     * sample, which for stateful nodes advances their phase by one block (the original `readParam`
     * fallback). Not meant to be overridden.
     */
    fun blockStartValue(freqHz: Double, ctx: IgniteContext): Double =
        controlRateValueOrNull(freqHz, ctx)
            ?: ctx.scratchBuffers.use { tmp -> generate(tmp, freqHz, ctx); tmp[ctx.offset] }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Arithmetic Composition
// ═══════════════════════════════════════════════════════════════════════════════
//
// All combinators implement `Ignitor` as dedicated `private class` types, not SAM
// lambdas. See `audio/ref/performance.md` for the rationale (Rule 1).
//
// CONSTANT-FOLD POLICY: binary ops (and the const-heavy ternary slots — Clamp/Range bounds,
// Lerp t) carry fold branches in `generate()`. UNARY ops (Neg, Abs, Sqrt, …) deliberately do
// NOT: they override `controlRateValueOrNull`/`isBlockConstant`, so a constant unary subtree
// folds AT ITS PARENT, which never calls the unary's `generate` at all — an internal
// fill-branch would be near-dead code that still costs a parity case, a liveness probe and a
// mutation check each. Residual cost: in the few non-folding parent slots (Lerp a/b under a
// varying t, Select branches) a constant unary pays one redundant in-place loop per block —
// accepted (no scratch, no alloc).

/**
 * Adds block-constant [k] onto the `[offset, offset+length)` window of [buffer] in place.
 * Fold arm of [plus] — bit-identical to the scratch loop (`tmp[i] == k`); bare add, no clamp
 * (Plus is a naturally bounded op, see the safety table below).
 */
private fun addConstInPlace(buffer: AudioBuffer, ctx: IgniteContext, k: Double) {
    val end = ctx.offset + ctx.length
    for (i in ctx.offset until end) {
        buffer[i] = buffer[i] + k
    }
}

/**
 * Multiplies the `[offset, offset+length)` window of [buffer] by block-constant [k] in place,
 * with the [safeOut] output clamp. Fold arm of [times] AND the [MulConstIgnitor] body — one
 * shared loop keeps `.mul(2.0)` and the Times folds bit-consistent by construction.
 * (The op is NOT a lambda parameter on purpose: an indirect call per sample is exactly what
 * `audio/ref/performance.md` bans — one concrete helper per op.)
 */
private fun mulConstInPlace(buffer: AudioBuffer, ctx: IgniteContext, k: Double) {
    val end = ctx.offset + ctx.length
    for (i in ctx.offset until end) {
        buffer[i] = safeOut(buffer[i] * k)
    }
}

/**
 * Mix two signals additively per-sample. A block-constant operand folds as a scalar (no scratch
 * render); otherwise the second signal renders into a scratch buffer.
 */
operator fun Ignitor.plus(other: Ignitor): Ignitor = PlusIgnitor(this, other)

private class PlusIgnitor(private val a: Ignitor, private val b: Ignitor) : Ignitor {
    // Structural — computed once so the non-folding hot path pays no per-block subtree walk.
    private val aConst = a.isBlockConstant
    private val bConst = b.isBlockConstant

    override val isBlockConstant: Boolean = aConst && bConst

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        // Constant-fold: a block-constant operand needs no scratch render. Bit-identical to the
        // scratch path — `v + k` operates on the same IEEE operands the scratch loop would read
        // (`tmp[i] == k` for a block-constant operand), and for non-NaN operands IEEE `+` is
        // bitwise commutative (incl. signed zeros), so folding the LEFT operand onto a
        // right-rendered buffer is equally exact (NaN operands are scrubbed downstream either
        // way). Block-constant implies stateless, so skipping the render advances no state.
        // A null scalar despite a true flag is a contract breach: fall through to the scratch
        // path below, which is correct for every operand — degrade, never throw on the render
        // thread (an exception here kills the whole worklet processor, not one voice).
        if (aConst && bConst) {
            val ka = a.controlRateValueOrNull(freqHz, ctx)
            val kb = b.controlRateValueOrNull(freqHz, ctx)
            if (ka != null && kb != null) {
                // Both constant: one add, one fill — bit-identical to the per-sample paths,
                // which compute the same op on the same operands at every index.
                // DELIBERATELY no safeOut (unlike the Times fill): Plus's per-op contract is
                // clamp-free (safety table below), and clamping ONLY here would break
                // bit-identity with the scratch loop — 1e15 + 1e15 is 2e15 bare but 1e15
                // clamped. Clamp Plus everywhere or nowhere; the contract says nowhere.
                buffer.fill(ka + kb, ctx.offset, ctx.offset + ctx.length)
                return
            }
        }

        if (bConst) {
            val kb = b.controlRateValueOrNull(freqHz, ctx)
            if (kb != null) {
                a.generate(buffer, freqHz, ctx)
                addConstInPlace(buffer, ctx, kb)
                return
            }
        }

        if (aConst) {
            val ka = a.controlRateValueOrNull(freqHz, ctx)
            if (ka != null) {
                b.generate(buffer, freqHz, ctx)
                addConstInPlace(buffer, ctx, ka)
                return
            }
        }

        a.generate(buffer, freqHz, ctx)

        ctx.scratchBuffers.use { tmp ->
            b.generate(tmp, freqHz, ctx)
            val end = ctx.offset + ctx.length
            for (i in ctx.offset until end) {
                buffer[i] = buffer[i] + tmp[i]
            }
        }
    }

    override fun controlRateValueOrNull(freqHz: Double, ctx: IgniteContext): Double? {
        val x = a.controlRateValueOrNull(freqHz, ctx) ?: return null
        val y = b.controlRateValueOrNull(freqHz, ctx) ?: return null
        return x + y
    }
}

/**
 * Ring-modulate two signals by per-sample multiplication. A block-constant operand folds as a
 * scalar (no scratch render); otherwise the second signal renders into a scratch buffer.
 *
 * Output magnitude is clamped to `±SAFE_MAX` and `NaN` is scrubbed to `0` per sample.
 * See `audio/ref/numerical-safety.md` for the safety contract.
 */
operator fun Ignitor.times(other: Ignitor): Ignitor = TimesIgnitor(this, other)

private class TimesIgnitor(private val a: Ignitor, private val b: Ignitor) : Ignitor {
    private val aConst = a.isBlockConstant
    private val bConst = b.isBlockConstant

    override val isBlockConstant: Boolean = aConst && bConst

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        // Constant-fold — same contract and breach policy as PlusIgnitor above: `safeOut(v * k)`
        // is bit-identical to `safeOut(v * tmp[i])` with `tmp[i] == k`, IEEE `*` is bitwise
        // commutative for non-NaN operands, and a block-constant operand is stateless.
        if (aConst && bConst) {
            val ka = a.controlRateValueOrNull(freqHz, ctx)
            val kb = b.controlRateValueOrNull(freqHz, ctx)
            if (ka != null && kb != null) {
                // Both constant: one multiply, one safeOut, one fill — bit-identical to the
                // per-sample paths, which compute the same op on the same operands per index.
                buffer.fill(safeOut(ka * kb), ctx.offset, ctx.offset + ctx.length)
                return
            }
        }

        if (bConst) {
            val kb = b.controlRateValueOrNull(freqHz, ctx)
            if (kb != null) {
                a.generate(buffer, freqHz, ctx)
                mulConstInPlace(buffer, ctx, kb)
                return
            }
        }

        if (aConst) {
            val ka = a.controlRateValueOrNull(freqHz, ctx)
            if (ka != null) {
                b.generate(buffer, freqHz, ctx)
                mulConstInPlace(buffer, ctx, ka)
                return
            }
        }

        a.generate(buffer, freqHz, ctx)

        ctx.scratchBuffers.use { tmp ->
            b.generate(tmp, freqHz, ctx)
            val end = ctx.offset + ctx.length
            for (i in ctx.offset until end) {
                buffer[i] = safeOut(buffer[i] * tmp[i])
            }
        }
    }

    override fun controlRateValueOrNull(freqHz: Double, ctx: IgniteContext): Double? {
        val x = a.controlRateValueOrNull(freqHz, ctx) ?: return null
        val y = b.controlRateValueOrNull(freqHz, ctx) ?: return null
        return safeOut(x * y)
    }
}

/** Scale signal amplitude per-sample by an audio-rate [factor]. Delegates to [times]. */
fun Ignitor.mul(factor: Ignitor): Ignitor = this * factor

/** Scale signal amplitude per-sample by a constant [factor]. Short-circuits when factor is 1.0. */
fun Ignitor.mul(factor: Double): Ignitor {
    if (factor == 1.0) return this
    return MulConstIgnitor(this, factor)
}

private class MulConstIgnitor(private val upstream: Ignitor, private val factor: Double) : Ignitor {
    override val isBlockConstant: Boolean = upstream.isBlockConstant

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        upstream.generate(buffer, freqHz, ctx)
        mulConstInPlace(buffer, ctx, factor)
    }

    override fun controlRateValueOrNull(freqHz: Double, ctx: IgniteContext): Double? {
        val x = upstream.controlRateValueOrNull(freqHz, ctx) ?: return null
        return safeOut(x * factor)
    }
}

/**
 * Divide signal amplitude per-sample by an audio-rate [divisor].
 *
 * Divisor magnitudes below `SAFE_MIN` are clamped (sign preserved) so the engine
 * never produces `NaN`/`Inf`. The output is also clamped to `±SAFE_MAX`.
 * See `audio/ref/numerical-safety.md`.
 */
fun Ignitor.div(divisor: Ignitor): Ignitor = DivIgnitor(this, divisor)

private class DivIgnitor(private val a: Ignitor, private val b: Ignitor) : Ignitor {
    private val aConst = a.isBlockConstant
    private val bConst = b.isBlockConstant

    override val isBlockConstant: Boolean = aConst && bConst

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        // Constant-fold ladder — same contract and breach policy as PlusIgnitor. Div keeps TRUE
        // division (never reciprocal-multiply: different rounding) and the per-op guards exactly:
        // safeDiv on the divisor (hoisted once when the divisor is the constant side), safeOut
        // on the output.
        if (aConst && bConst) {
            val ka = a.controlRateValueOrNull(freqHz, ctx)
            val kb = b.controlRateValueOrNull(freqHz, ctx)
            if (ka != null && kb != null) {
                buffer.fill(safeOut(ka / safeDiv(kb)), ctx.offset, ctx.offset + ctx.length)
                return
            }
        }

        if (bConst) {
            val kb = b.controlRateValueOrNull(freqHz, ctx)
            if (kb != null) {
                val d = safeDiv(kb)
                a.generate(buffer, freqHz, ctx)
                val end = ctx.offset + ctx.length
                for (i in ctx.offset until end) {
                    buffer[i] = safeOut(buffer[i] / d)
                }
                return
            }
        }

        if (aConst) {
            val ka = a.controlRateValueOrNull(freqHz, ctx)
            if (ka != null) {
                b.generate(buffer, freqHz, ctx)
                val end = ctx.offset + ctx.length
                for (i in ctx.offset until end) {
                    buffer[i] = safeOut(ka / safeDiv(buffer[i]))
                }
                return
            }
        }

        a.generate(buffer, freqHz, ctx)

        ctx.scratchBuffers.use { tmp ->
            b.generate(tmp, freqHz, ctx)
            val end = ctx.offset + ctx.length
            for (i in ctx.offset until end) {
                buffer[i] = safeOut(buffer[i] / safeDiv(tmp[i]))
            }
        }
    }

    override fun controlRateValueOrNull(freqHz: Double, ctx: IgniteContext): Double? {
        val x = a.controlRateValueOrNull(freqHz, ctx) ?: return null
        val y = b.controlRateValueOrNull(freqHz, ctx) ?: return null
        return safeOut(x / safeDiv(y))
    }
}

/**
 * Divide signal amplitude by a constant [divisor].
 * Zero divisor is substituted with `±SAFE_MIN`; output clamped to `±SAFE_MAX`.
 */
fun Ignitor.div(divisor: Double): Ignitor {
    val safeFactor = 1.0 / safeDiv(divisor)
    return mul(safeFactor)
}

/** Subtract another signal from this one (per-sample). Uses a scratch buffer for the second signal. */
fun Ignitor.minus(other: Ignitor): Ignitor = MinusIgnitor(this, other)

private class MinusIgnitor(private val a: Ignitor, private val b: Ignitor) : Ignitor {
    private val aConst = a.isBlockConstant
    private val bConst = b.isBlockConstant

    override val isBlockConstant: Boolean = aConst && bConst

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        // Constant-fold ladder — same contract and breach policy as PlusIgnitor. Minus is
        // NON-commutative: each arm keeps its side of the subtraction. Bare op (safety table).
        if (aConst && bConst) {
            val ka = a.controlRateValueOrNull(freqHz, ctx)
            val kb = b.controlRateValueOrNull(freqHz, ctx)
            if (ka != null && kb != null) {
                buffer.fill(ka - kb, ctx.offset, ctx.offset + ctx.length)
                return
            }
        }

        if (bConst) {
            val kb = b.controlRateValueOrNull(freqHz, ctx)
            if (kb != null) {
                a.generate(buffer, freqHz, ctx)
                val end = ctx.offset + ctx.length
                for (i in ctx.offset until end) {
                    buffer[i] = buffer[i] - kb
                }
                return
            }
        }

        if (aConst) {
            val ka = a.controlRateValueOrNull(freqHz, ctx)
            if (ka != null) {
                b.generate(buffer, freqHz, ctx)
                val end = ctx.offset + ctx.length
                for (i in ctx.offset until end) {
                    buffer[i] = ka - buffer[i]
                }
                return
            }
        }

        a.generate(buffer, freqHz, ctx)

        ctx.scratchBuffers.use { tmp ->
            b.generate(tmp, freqHz, ctx)
            val end = ctx.offset + ctx.length
            for (i in ctx.offset until end) {
                buffer[i] = buffer[i] - tmp[i]
            }
        }
    }

    override fun controlRateValueOrNull(freqHz: Double, ctx: IgniteContext): Double? {
        val x = a.controlRateValueOrNull(freqHz, ctx) ?: return null
        val y = b.controlRateValueOrNull(freqHz, ctx) ?: return null
        return x - y
    }
}

/** Negate this signal (flip polarity, per-sample). */
fun Ignitor.neg(): Ignitor = NegIgnitor(this)

private class NegIgnitor(private val upstream: Ignitor) : Ignitor {
    override val isBlockConstant: Boolean = upstream.isBlockConstant

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        upstream.generate(buffer, freqHz, ctx)
        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            buffer[i] = -buffer[i]
        }
    }

    override fun controlRateValueOrNull(freqHz: Double, ctx: IgniteContext): Double? {
        val x = upstream.controlRateValueOrNull(freqHz, ctx) ?: return null
        return -x
    }
}

/** Absolute value of this signal (per-sample, full-wave rectification). */
fun Ignitor.abs(): Ignitor = AbsIgnitor(this)

private class AbsIgnitor(private val upstream: Ignitor) : Ignitor {
    override val isBlockConstant: Boolean = upstream.isBlockConstant

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        upstream.generate(buffer, freqHz, ctx)
        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            val v = buffer[i]
            buffer[i] = if (v < 0.0) -v else v
        }
    }

    override fun controlRateValueOrNull(freqHz: Double, ctx: IgniteContext): Double? {
        val v = upstream.controlRateValueOrNull(freqHz, ctx) ?: return null
        return if (v < 0.0) -v else v
    }
}

/**
 * Raise this signal to the power of [exp] (per-sample).
 *
 * Signed-magnitude: negative bases produce `-(|base|^exp)` to avoid `NaN`.
 * `0^negative = +Inf` is caught by the output clamp (`±SAFE_MAX`).
 */
fun Ignitor.pow(exp: Ignitor): Ignitor = PowIgnitor(this, exp)

private class PowIgnitor(private val base: Ignitor, private val exp: Ignitor) : Ignitor {
    private val baseConst = base.isBlockConstant
    private val expConst = exp.isBlockConstant

    override val isBlockConstant: Boolean = baseConst && expConst

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        // Constant-fold ladder — same contract and breach policy as PlusIgnitor. NON-commutative:
        // each arm keeps base/exp in their roles, signed-magnitude expr and safeOut verbatim.
        if (baseConst && expConst) {
            val kb = base.controlRateValueOrNull(freqHz, ctx)
            val ke = exp.controlRateValueOrNull(freqHz, ctx)
            if (kb != null && ke != null) {
                val raw = if (kb >= 0.0) kb.pow(ke) else -((-kb).pow(ke))
                buffer.fill(safeOut(raw), ctx.offset, ctx.offset + ctx.length)
                return
            }
        }

        if (expConst) {
            val ke = exp.controlRateValueOrNull(freqHz, ctx)
            if (ke != null) {
                base.generate(buffer, freqHz, ctx)
                val end = ctx.offset + ctx.length
                for (i in ctx.offset until end) {
                    val b = buffer[i]
                    val raw = if (b >= 0.0) b.pow(ke) else -((-b).pow(ke))
                    buffer[i] = safeOut(raw)
                }
                return
            }
        }

        if (baseConst) {
            val kb = base.controlRateValueOrNull(freqHz, ctx)
            if (kb != null) {
                exp.generate(buffer, freqHz, ctx)
                val end = ctx.offset + ctx.length
                for (i in ctx.offset until end) {
                    val e = buffer[i]
                    val raw = if (kb >= 0.0) kb.pow(e) else -((-kb).pow(e))
                    buffer[i] = safeOut(raw)
                }
                return
            }
        }

        base.generate(buffer, freqHz, ctx)

        ctx.scratchBuffers.use { tmp ->
            exp.generate(tmp, freqHz, ctx)
            val end = ctx.offset + ctx.length
            for (i in ctx.offset until end) {
                val b = buffer[i]
                val e = tmp[i]
                val raw = if (b >= 0.0) b.pow(e) else -((-b).pow(e))
                buffer[i] = safeOut(raw)
            }
        }
    }

    override fun controlRateValueOrNull(freqHz: Double, ctx: IgniteContext): Double? {
        val b = base.controlRateValueOrNull(freqHz, ctx) ?: return null
        val e = exp.controlRateValueOrNull(freqHz, ctx) ?: return null
        val raw = if (b >= 0.0) b.pow(e) else -((-b).pow(e))
        return safeOut(raw)
    }
}

/** Per-sample minimum of this signal and [other]. Uses a scratch buffer for [other]. */
fun Ignitor.min(other: Ignitor): Ignitor = MinIgnitor(this, other)

private class MinIgnitor(private val a: Ignitor, private val b: Ignitor) : Ignitor {
    private val aConst = a.isBlockConstant
    private val bConst = b.isBlockConstant

    override val isBlockConstant: Boolean = aConst && bConst

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        // Constant-fold ladder — same contract and breach policy as PlusIgnitor. Min is NOT
        // value-commutative under NaN (`if (x < y) x else y` returns y when x is NaN), so both
        // arms keep `a` as the FIRST comparison operand exactly like the scratch loop.
        if (aConst && bConst) {
            val ka = a.controlRateValueOrNull(freqHz, ctx)
            val kb = b.controlRateValueOrNull(freqHz, ctx)
            if (ka != null && kb != null) {
                buffer.fill(if (ka < kb) ka else kb, ctx.offset, ctx.offset + ctx.length)
                return
            }
        }

        if (bConst) {
            val kb = b.controlRateValueOrNull(freqHz, ctx)
            if (kb != null) {
                a.generate(buffer, freqHz, ctx)
                val end = ctx.offset + ctx.length
                for (i in ctx.offset until end) {
                    val x = buffer[i]
                    buffer[i] = if (x < kb) x else kb
                }
                return
            }
        }

        if (aConst) {
            val ka = a.controlRateValueOrNull(freqHz, ctx)
            if (ka != null) {
                b.generate(buffer, freqHz, ctx)
                val end = ctx.offset + ctx.length
                for (i in ctx.offset until end) {
                    val y = buffer[i]
                    buffer[i] = if (ka < y) ka else y
                }
                return
            }
        }

        a.generate(buffer, freqHz, ctx)
        ctx.scratchBuffers.use { tmp ->
            b.generate(tmp, freqHz, ctx)
            val end = ctx.offset + ctx.length
            for (i in ctx.offset until end) {
                val x = buffer[i]
                val y = tmp[i]
                buffer[i] = if (x < y) x else y
            }
        }
    }

    override fun controlRateValueOrNull(freqHz: Double, ctx: IgniteContext): Double? {
        val x = a.controlRateValueOrNull(freqHz, ctx) ?: return null
        val y = b.controlRateValueOrNull(freqHz, ctx) ?: return null
        return if (x < y) x else y
    }
}

/** Per-sample maximum of this signal and [other]. Uses a scratch buffer for [other]. */
fun Ignitor.max(other: Ignitor): Ignitor = MaxIgnitor(this, other)

private class MaxIgnitor(private val a: Ignitor, private val b: Ignitor) : Ignitor {
    private val aConst = a.isBlockConstant
    private val bConst = b.isBlockConstant

    override val isBlockConstant: Boolean = aConst && bConst

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        // See MinIgnitor — same ladder, same NaN-ordering care, with `>`.
        if (aConst && bConst) {
            val ka = a.controlRateValueOrNull(freqHz, ctx)
            val kb = b.controlRateValueOrNull(freqHz, ctx)
            if (ka != null && kb != null) {
                buffer.fill(if (ka > kb) ka else kb, ctx.offset, ctx.offset + ctx.length)
                return
            }
        }

        if (bConst) {
            val kb = b.controlRateValueOrNull(freqHz, ctx)
            if (kb != null) {
                a.generate(buffer, freqHz, ctx)
                val end = ctx.offset + ctx.length
                for (i in ctx.offset until end) {
                    val x = buffer[i]
                    buffer[i] = if (x > kb) x else kb
                }
                return
            }
        }

        if (aConst) {
            val ka = a.controlRateValueOrNull(freqHz, ctx)
            if (ka != null) {
                b.generate(buffer, freqHz, ctx)
                val end = ctx.offset + ctx.length
                for (i in ctx.offset until end) {
                    val y = buffer[i]
                    buffer[i] = if (ka > y) ka else y
                }
                return
            }
        }

        a.generate(buffer, freqHz, ctx)
        ctx.scratchBuffers.use { tmp ->
            b.generate(tmp, freqHz, ctx)
            val end = ctx.offset + ctx.length
            for (i in ctx.offset until end) {
                val x = buffer[i]
                val y = tmp[i]
                buffer[i] = if (x > y) x else y
            }
        }
    }

    override fun controlRateValueOrNull(freqHz: Double, ctx: IgniteContext): Double? {
        val x = a.controlRateValueOrNull(freqHz, ctx) ?: return null
        val y = b.controlRateValueOrNull(freqHz, ctx) ?: return null
        return if (x > y) x else y
    }
}

/** Bound this signal to `[lo, hi]` per sample. Uses two scratch buffers. */
fun Ignitor.clamp(lo: Ignitor, hi: Ignitor): Ignitor = ClampIgnitor(this, lo, hi)

private class ClampIgnitor(
    private val upstream: Ignitor,
    private val lo: Ignitor,
    private val hi: Ignitor,
) : Ignitor {
    private val boundsConst = lo.isBlockConstant && hi.isBlockConstant

    override val isBlockConstant: Boolean = upstream.isBlockConstant && boundsConst

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        // Partial fold: block-constant bounds (the dominant `clamp(-1, 1)` shape) skip BOTH
        // scratch renders. Same breach policy as PlusIgnitor (null despite flag -> scratch path).
        if (boundsConst) {
            val kl = lo.controlRateValueOrNull(freqHz, ctx)
            val kh = hi.controlRateValueOrNull(freqHz, ctx)
            if (kl != null && kh != null) {
                upstream.generate(buffer, freqHz, ctx)
                val end = ctx.offset + ctx.length
                for (i in ctx.offset until end) {
                    val v = buffer[i]
                    buffer[i] = when {
                        v < kl -> kl
                        v > kh -> kh
                        else -> v
                    }
                }
                return
            }
        }

        upstream.generate(buffer, freqHz, ctx)
        ctx.scratchBuffers.use { loBuf ->
            lo.generate(loBuf, freqHz, ctx)
            ctx.scratchBuffers.use { hiBuf ->
                hi.generate(hiBuf, freqHz, ctx)
                val end = ctx.offset + ctx.length
                for (i in ctx.offset until end) {
                    val v = buffer[i]
                    val l = loBuf[i]
                    val h = hiBuf[i]
                    buffer[i] = when {
                        v < l -> l
                        v > h -> h
                        else -> v
                    }
                }
            }
        }
    }

    override fun controlRateValueOrNull(freqHz: Double, ctx: IgniteContext): Double? {
        val v = upstream.controlRateValueOrNull(freqHz, ctx) ?: return null
        val l = lo.controlRateValueOrNull(freqHz, ctx) ?: return null
        val h = hi.controlRateValueOrNull(freqHz, ctx) ?: return null
        return when {
            v < l -> l
            v > h -> h
            else -> v
        }
    }
}

/** `e^x` per sample. Output clamped to `±SAFE_MAX` (caught for large `x`, e.g. `exp(40) ≈ 2.4e17`). */
fun Ignitor.exp(): Ignitor = ExpIgnitor(this)

private class ExpIgnitor(private val upstream: Ignitor) : Ignitor {
    override val isBlockConstant: Boolean = upstream.isBlockConstant

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        upstream.generate(buffer, freqHz, ctx)
        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            buffer[i] = safeOut(exp(buffer[i]))
        }
    }

    override fun controlRateValueOrNull(freqHz: Double, ctx: IgniteContext): Double? {
        val v = upstream.controlRateValueOrNull(freqHz, ctx) ?: return null
        return safeOut(exp(v))
    }
}

/**
 * Natural logarithm per sample.
 *
 * Signed-magnitude: `x > 0` → `ln(x)`, `x < 0` → `−ln(−x)`, `x == 0` → `0`.
 * Avoids `-Inf` / `NaN` poisoning the audio path.
 */
fun Ignitor.log(): Ignitor = LogIgnitor(this)

private class LogIgnitor(private val upstream: Ignitor) : Ignitor {
    override val isBlockConstant: Boolean = upstream.isBlockConstant

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        upstream.generate(buffer, freqHz, ctx)
        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            val v = buffer[i]
            buffer[i] = when {
                v > 0.0 -> ln(v)
                v < 0.0 -> -ln((-v))
                else -> 0.0
            }
        }
    }

    override fun controlRateValueOrNull(freqHz: Double, ctx: IgniteContext): Double? {
        val v = upstream.controlRateValueOrNull(freqHz, ctx) ?: return null
        return when {
            v > 0.0 -> ln(v)
            v < 0.0 -> -ln((-v))
            else -> 0.0
        }
    }
}

/** Square root per sample. Signed-magnitude: `x < 0` → `−√(−x)`. */
fun Ignitor.sqrt(): Ignitor = SqrtIgnitor(this)

private class SqrtIgnitor(private val upstream: Ignitor) : Ignitor {
    override val isBlockConstant: Boolean = upstream.isBlockConstant

    override fun controlRateValueOrNull(freqHz: Double, ctx: IgniteContext): Double? {
        val v = upstream.controlRateValueOrNull(freqHz, ctx) ?: return null
        return if (v >= 0.0) sqrt(v) else -sqrt((-v))
    }

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        upstream.generate(buffer, freqHz, ctx)
        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            val v = buffer[i]
            buffer[i] = if (v >= 0.0) sqrt(v) else -sqrt((-v))
        }
    }
}

/** Sign of the inner signal: `-1`, `0`, or `+1`. */
fun Ignitor.sign(): Ignitor = SignIgnitor(this)

private class SignIgnitor(private val upstream: Ignitor) : Ignitor {
    override val isBlockConstant: Boolean = upstream.isBlockConstant

    override fun controlRateValueOrNull(freqHz: Double, ctx: IgniteContext): Double? {
        val v = upstream.controlRateValueOrNull(freqHz, ctx) ?: return null
        return when {
            v > 0.0 -> 1.0
            v < 0.0 -> -1.0
            else -> 0.0
        }
    }

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        upstream.generate(buffer, freqHz, ctx)
        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            val v = buffer[i]
            buffer[i] = when {
                v > 0.0 -> 1.0
                v < 0.0 -> -1.0
                else -> 0.0
            }
        }
    }
}

/** `tanh(x)` per sample. */
fun Ignitor.tanh(): Ignitor = TanhIgnitor(this)

private class TanhIgnitor(private val upstream: Ignitor) : Ignitor {
    override val isBlockConstant: Boolean = upstream.isBlockConstant

    override fun controlRateValueOrNull(freqHz: Double, ctx: IgniteContext): Double? {
        val v = upstream.controlRateValueOrNull(freqHz, ctx) ?: return null
        return tanh(v)
    }

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        upstream.generate(buffer, freqHz, ctx)
        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            buffer[i] = tanh(buffer[i])
        }
    }
}

/** Linear interpolation: `this·(1−t) + other·t`. Two scratch buffers. */
fun Ignitor.lerp(other: Ignitor, t: Ignitor): Ignitor = LerpIgnitor(this, other, t)

private class LerpIgnitor(
    private val a: Ignitor,
    private val b: Ignitor,
    private val t: Ignitor,
) : Ignitor {
    private val tConst = t.isBlockConstant

    override val isBlockConstant: Boolean =
        a.isBlockConstant && b.isBlockConstant && tConst

    override fun controlRateValueOrNull(freqHz: Double, ctx: IgniteContext): Double? {
        val x = a.controlRateValueOrNull(freqHz, ctx) ?: return null
        val y = b.controlRateValueOrNull(freqHz, ctx) ?: return null
        val tv = t.controlRateValueOrNull(freqHz, ctx) ?: return null
        return x * (1.0 - tv) + y * tv
    }

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        // Partial fold: a block-constant `t` (the common `lerp(x, y, 0.3)` shape) skips one
        // scratch render. Folding constant a/b too would be combinatorial for a rare shape —
        // deliberately not done; a fully-constant Lerp folds at a FOLDING parent via the
        // overrides above (non-folding slots still run this loop — reachable, just rare).
        // Same breach policy as PlusIgnitor (null despite flag -> scratch path below).
        if (tConst) {
            val kt = t.controlRateValueOrNull(freqHz, ctx)
            if (kt != null) {
                a.generate(buffer, freqHz, ctx)
                ctx.scratchBuffers.use { otherBuf ->
                    b.generate(otherBuf, freqHz, ctx)
                    val end = ctx.offset + ctx.length
                    for (i in ctx.offset until end) {
                        buffer[i] = buffer[i] * (1.0 - kt) + otherBuf[i] * kt
                    }
                }
                return
            }
        }

        a.generate(buffer, freqHz, ctx)
        ctx.scratchBuffers.use { otherBuf ->
            b.generate(otherBuf, freqHz, ctx)
            ctx.scratchBuffers.use { tBuf ->
                t.generate(tBuf, freqHz, ctx)
                val end = ctx.offset + ctx.length
                for (i in ctx.offset until end) {
                    val tv = tBuf[i]
                    buffer[i] = buffer[i] * (1.0 - tv) + otherBuf[i] * tv
                }
            }
        }
    }
}

/** Maps `[-1, 1]` → `[lo, hi]` per sample: `lo + (x + 1)·0.5·(hi − lo)`. */
fun Ignitor.range(lo: Ignitor, hi: Ignitor): Ignitor = RangeIgnitor(this, lo, hi)

private class RangeIgnitor(
    private val upstream: Ignitor,
    private val lo: Ignitor,
    private val hi: Ignitor,
) : Ignitor {
    private val boundsConst = lo.isBlockConstant && hi.isBlockConstant

    override val isBlockConstant: Boolean = upstream.isBlockConstant && boundsConst

    override fun controlRateValueOrNull(freqHz: Double, ctx: IgniteContext): Double? {
        val v = upstream.controlRateValueOrNull(freqHz, ctx) ?: return null
        val l = lo.controlRateValueOrNull(freqHz, ctx) ?: return null
        val h = hi.controlRateValueOrNull(freqHz, ctx) ?: return null
        return l + (v + 1.0) * 0.5 * (h - l)
    }

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        // Partial fold: block-constant bounds (the dominant `range(200, 4000)` shape) skip BOTH
        // scratch renders. Single-const-bound combinatorics deliberately not done — rare shape.
        // Same breach policy as PlusIgnitor (null despite flag -> scratch path below).
        if (boundsConst) {
            val kl = lo.controlRateValueOrNull(freqHz, ctx)
            val kh = hi.controlRateValueOrNull(freqHz, ctx)
            if (kl != null && kh != null) {
                upstream.generate(buffer, freqHz, ctx)
                val end = ctx.offset + ctx.length
                for (i in ctx.offset until end) {
                    buffer[i] = kl + (buffer[i] + 1.0) * 0.5 * (kh - kl)
                }
                return
            }
        }

        upstream.generate(buffer, freqHz, ctx)
        ctx.scratchBuffers.use { loBuf ->
            lo.generate(loBuf, freqHz, ctx)
            ctx.scratchBuffers.use { hiBuf ->
                hi.generate(hiBuf, freqHz, ctx)
                val end = ctx.offset + ctx.length
                for (i in ctx.offset until end) {
                    val l = loBuf[i]
                    val h = hiBuf[i]
                    buffer[i] = l + (buffer[i] + 1.0) * 0.5 * (h - l)
                }
            }
        }
    }
}

/** Maps `[0, 1]` → `[-1, 1]` per sample: `x·2 − 1`. */
fun Ignitor.bipolar(): Ignitor = BipolarIgnitor(this)

private class BipolarIgnitor(private val upstream: Ignitor) : Ignitor {
    override val isBlockConstant: Boolean = upstream.isBlockConstant

    override fun controlRateValueOrNull(freqHz: Double, ctx: IgniteContext): Double? {
        val v = upstream.controlRateValueOrNull(freqHz, ctx) ?: return null
        return v * 2.0 - 1.0
    }

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        upstream.generate(buffer, freqHz, ctx)
        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            buffer[i] = buffer[i] * 2.0 - 1.0
        }
    }
}

/** Maps `[-1, 1]` → `[0, 1]` per sample: `(x + 1)·0.5`. */
fun Ignitor.unipolar(): Ignitor = UnipolarIgnitor(this)

private class UnipolarIgnitor(private val upstream: Ignitor) : Ignitor {
    override val isBlockConstant: Boolean = upstream.isBlockConstant

    override fun controlRateValueOrNull(freqHz: Double, ctx: IgniteContext): Double? {
        val v = upstream.controlRateValueOrNull(freqHz, ctx) ?: return null
        return (v + 1.0) * 0.5
    }

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        upstream.generate(buffer, freqHz, ctx)
        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            buffer[i] = (buffer[i] + 1.0) * 0.5
        }
    }
}

/** Per-sample floor. */
fun Ignitor.floor(): Ignitor = FloorIgnitor(this)

private class FloorIgnitor(private val upstream: Ignitor) : Ignitor {
    override val isBlockConstant: Boolean = upstream.isBlockConstant

    override fun controlRateValueOrNull(freqHz: Double, ctx: IgniteContext): Double? {
        val v = upstream.controlRateValueOrNull(freqHz, ctx) ?: return null
        return floor(v)
    }

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        upstream.generate(buffer, freqHz, ctx)
        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            buffer[i] = floor(buffer[i])
        }
    }
}

/** Per-sample ceiling. */
fun Ignitor.ceil(): Ignitor = CeilIgnitor(this)

private class CeilIgnitor(private val upstream: Ignitor) : Ignitor {
    override val isBlockConstant: Boolean = upstream.isBlockConstant

    override fun controlRateValueOrNull(freqHz: Double, ctx: IgniteContext): Double? {
        val v = upstream.controlRateValueOrNull(freqHz, ctx) ?: return null
        return ceil(v)
    }

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        upstream.generate(buffer, freqHz, ctx)
        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            buffer[i] = ceil(buffer[i])
        }
    }
}

/** Per-sample round to nearest integer. */
fun Ignitor.round(): Ignitor = RoundIgnitor(this)

private class RoundIgnitor(private val upstream: Ignitor) : Ignitor {
    override val isBlockConstant: Boolean = upstream.isBlockConstant

    override fun controlRateValueOrNull(freqHz: Double, ctx: IgniteContext): Double? {
        val v = upstream.controlRateValueOrNull(freqHz, ctx) ?: return null
        return round(v)
    }

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        upstream.generate(buffer, freqHz, ctx)
        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            buffer[i] = round(buffer[i])
        }
    }
}

/** Per-sample fractional part: `x − floor(x)`. */
fun Ignitor.frac(): Ignitor = FracIgnitor(this)

private class FracIgnitor(private val upstream: Ignitor) : Ignitor {
    override val isBlockConstant: Boolean = upstream.isBlockConstant

    override fun controlRateValueOrNull(freqHz: Double, ctx: IgniteContext): Double? {
        val v = upstream.controlRateValueOrNull(freqHz, ctx) ?: return null
        return (v - floor(v))
    }

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        upstream.generate(buffer, freqHz, ctx)
        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            val v = buffer[i]
            buffer[i] = (v - floor(v))
        }
    }
}

/**
 * Per-sample modulo (Kotlin `rem` semantics — sign follows the dividend).
 *
 * Divisor magnitudes below `SAFE_MIN` are clamped (sign preserved). Uses one scratch buffer.
 * See `audio/ref/numerical-safety.md`.
 */
fun Ignitor.mod(other: Ignitor): Ignitor = ModIgnitor(this, other)

private class ModIgnitor(private val a: Ignitor, private val b: Ignitor) : Ignitor {
    private val aConst = a.isBlockConstant
    private val bConst = b.isBlockConstant

    override val isBlockConstant: Boolean = aConst && bConst

    override fun controlRateValueOrNull(freqHz: Double, ctx: IgniteContext): Double? {
        val x = a.controlRateValueOrNull(freqHz, ctx) ?: return null
        val y = b.controlRateValueOrNull(freqHz, ctx) ?: return null
        return x % safeDiv(y)
    }

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        // Constant-fold ladder — same contract and breach policy as PlusIgnitor. Mod is
        // NON-commutative; safeDiv on the divisor, NO safeOut (matches the scratch loop:
        // rem's magnitude is bounded by the divisor's).
        if (aConst && bConst) {
            val ka = a.controlRateValueOrNull(freqHz, ctx)
            val kb = b.controlRateValueOrNull(freqHz, ctx)
            if (ka != null && kb != null) {
                buffer.fill(ka % safeDiv(kb), ctx.offset, ctx.offset + ctx.length)
                return
            }
        }

        if (bConst) {
            val kb = b.controlRateValueOrNull(freqHz, ctx)
            if (kb != null) {
                val d = safeDiv(kb)
                a.generate(buffer, freqHz, ctx)
                val end = ctx.offset + ctx.length
                for (i in ctx.offset until end) {
                    buffer[i] = buffer[i] % d
                }
                return
            }
        }

        if (aConst) {
            val ka = a.controlRateValueOrNull(freqHz, ctx)
            if (ka != null) {
                b.generate(buffer, freqHz, ctx)
                val end = ctx.offset + ctx.length
                for (i in ctx.offset until end) {
                    buffer[i] = ka % safeDiv(buffer[i])
                }
                return
            }
        }

        a.generate(buffer, freqHz, ctx)
        ctx.scratchBuffers.use { tmp ->
            b.generate(tmp, freqHz, ctx)
            val end = ctx.offset + ctx.length
            for (i in ctx.offset until end) {
                buffer[i] = buffer[i] % safeDiv(tmp[i])
            }
        }
    }
}

/**
 * Per-sample reciprocal `1/x`.
 *
 * Input magnitudes below `SAFE_MIN` are clamped (sign preserved) so the output
 * stays at `±SAFE_MAX` rather than overflowing to `±Inf`.
 */
fun Ignitor.recip(): Ignitor = RecipIgnitor(this)

private class RecipIgnitor(private val upstream: Ignitor) : Ignitor {
    override val isBlockConstant: Boolean = upstream.isBlockConstant

    override fun controlRateValueOrNull(freqHz: Double, ctx: IgniteContext): Double? {
        val v = upstream.controlRateValueOrNull(freqHz, ctx) ?: return null
        return safeOut(1.0 / safeDiv(v))
    }

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        upstream.generate(buffer, freqHz, ctx)
        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            buffer[i] = safeOut(1.0 / safeDiv(buffer[i]))
        }
    }
}

/** Per-sample square: `x · x`. Output clamped to `±SAFE_MAX` (caught for `|x| > ~3.16e7`). */
fun Ignitor.sq(): Ignitor = SqIgnitor(this)

private class SqIgnitor(private val upstream: Ignitor) : Ignitor {
    override val isBlockConstant: Boolean = upstream.isBlockConstant

    override fun controlRateValueOrNull(freqHz: Double, ctx: IgniteContext): Double? {
        val v = upstream.controlRateValueOrNull(freqHz, ctx) ?: return null
        return safeOut(v * v)
    }

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        upstream.generate(buffer, freqHz, ctx)
        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            val v = buffer[i]
            buffer[i] = safeOut(v * v)
        }
    }
}

/**
 * Per-sample conditional: when this signal `> 0`, use [whenTrue]; else [whenFalse].
 *
 * Both branches are evaluated at audio rate — no short-circuit — so any stateful
 * sources advance regardless of which branch is selected.
 */
fun Ignitor.select(whenTrue: Ignitor, whenFalse: Ignitor): Ignitor =
    SelectIgnitor(this, whenTrue, whenFalse)

private class SelectIgnitor(
    private val cond: Ignitor,
    private val whenTrue: Ignitor,
    private val whenFalse: Ignitor,
) : Ignitor {
    override val isBlockConstant: Boolean =
        cond.isBlockConstant && whenTrue.isBlockConstant && whenFalse.isBlockConstant

    override fun controlRateValueOrNull(freqHz: Double, ctx: IgniteContext): Double? {
        // Resolve ALL THREE children before returning (the MinIgnitor pattern) — generate
        // renders both branches unconditionally so their state advances; a short-circuit on
        // the condition would let an untaken stateful branch fall behind the render path.
        val c = cond.controlRateValueOrNull(freqHz, ctx) ?: return null
        val tv = whenTrue.controlRateValueOrNull(freqHz, ctx) ?: return null
        val fv = whenFalse.controlRateValueOrNull(freqHz, ctx) ?: return null
        return if (c > 0.0) tv else fv
    }

    // NO fold branch on generate: both branches MUST render every block regardless of the
    // condition (stateful sources advance either way — the documented Select contract), so a
    // constant condition saves almost nothing here. A fully-constant Select folds at a FOLDING
    // parent; in non-folding slots (another Select's branch, Clamp/Range upstream, graph root)
    // this full loop still runs — reachable, just rare.
    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        cond.generate(buffer, freqHz, ctx)
        ctx.scratchBuffers.use { tBuf ->
            whenTrue.generate(tBuf, freqHz, ctx)
            ctx.scratchBuffers.use { fBuf ->
                whenFalse.generate(fBuf, freqHz, ctx)
                val end = ctx.offset + ctx.length
                for (i in ctx.offset until end) {
                    buffer[i] = if (buffer[i] > 0.0) tBuf[i] else fBuf[i]
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Numerical Safety Bounds
// ═══════════════════════════════════════════════════════════════════════════════
//
// Klang's safety contract — see `audio/ref/numerical-safety.md` for the full story.
//
// Every arithmetic operator that can produce `NaN`/`Inf` clamps either its inputs
// (divisor-class ops: Div, Mod, Recip) or its output (output-clamp ops: Times,
// Pow, Exp, Sq, Mul-by-constant). Naturally bounded ops (Plus, Minus, Lerp,
// Range, Clamp, Min, Max, Abs, Neg, Sign, Floor, Ceil, Round, Frac, Tanh, Sqrt,
// Log) need no extra guard.
//
// Values match the SuperCollider / ChucK / STK convention (`zapgremlins`,
// `CK_DDN_*`): ±300 dBFS, well below any audible signal, well above subnormal.
// `1 / SAFE_MIN = SAFE_MAX` ensures a reciprocal of the smallest allowed
// divisor lands at the largest allowed output — round-trip safe. (Design intent, not an FP
// bit-fact: 1.0 / 1e-15 evaluates to 9.999999999999999e14, just BELOW SAFE_MAX — so a
// reciprocal of a safeDiv'd value can never actually engage safeOut.)

/**
 * Smallest allowed magnitude for a divisor (or reciprocal input) in audio arithmetic.
 *
 * Values closer to zero are clamped to `±SAFE_MIN` (sign preserved) to prevent
 * `1/x` from overflowing. ≈ -300 dBFS — well below any audible signal.
 */
const val SAFE_MIN: AudioSample = 1e-15

/**
 * Largest allowed output magnitude for ops that can grow values (`Times`, `Pow`,
 * `Exp`, `Sq`, `Mul-by-constant`).
 *
 * Outputs above this are clamped to `±SAFE_MAX`. ≈ +300 dBFS — vastly above any
 * musical signal, well below `Double.MAX_VALUE` (`≈ 1.8e308`). Squaring two
 * `SAFE_MAX` values gives `1e30`, still finite Double.
 */
const val SAFE_MAX: AudioSample = 1e15

/**
 * Clamp a divisor's magnitude to `≥ SAFE_MIN`, preserving sign.
 *
 * Substitutes `0.0` and `NaN` with `+SAFE_MIN`. `±Inf` passes through unchanged
 * (since `±Inf` is already a valid divisor — `a / ±Inf = ±0`); the resulting
 * `0` or any `NaN` from `Inf - Inf` patterns is scrubbed downstream by [safeOut].
 */
@Suppress("NOTHING_TO_INLINE")
internal inline fun safeDiv(d: AudioSample): AudioSample = when {
    d.isNaN() -> SAFE_MIN
    d > SAFE_MIN -> d
    d < -SAFE_MIN -> d
    d < 0.0 -> -SAFE_MIN
    else -> SAFE_MIN
}

/** Clamp an output value to `[-SAFE_MAX, +SAFE_MAX]`. Scrubs `NaN` to `0`. */
@Suppress("NOTHING_TO_INLINE")
internal inline fun safeOut(v: AudioSample): AudioSample = when {
    v.isNaN() -> 0.0
    v > SAFE_MAX -> SAFE_MAX
    v < -SAFE_MAX -> -SAFE_MAX
    else -> v
}

// ═══════════════════════════════════════════════════════════════════════════════
// Frequency Modifiers
// ═══════════════════════════════════════════════════════════════════════════════

/** Shift frequency by [semitones] from an audio-rate exciter. Reads the first sample per block for the detune value. */
fun Ignitor.detune(semitones: Ignitor): Ignitor = DetuneIgnitor(this, semitones)

// Detune (both forms) deliberately has NO controlRateValueOrNull/isBlockConstant override:
// it is a PITCH node — it changes the freqHz its upstream sees, not a pointwise value — so
// "block-constant" is not a meaningful property of its output. Do not "complete" the set.
private class DetuneIgnitor(
    private val upstream: Ignitor,
    private val semitones: Ignitor,
) : Ignitor {
    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        val s = semitones.blockStartValue(freqHz, ctx)
        val ratio = 2.0.pow(s / 12.0)
        upstream.generate(buffer, freqHz * ratio, ctx)
    }
}

/** Shift frequency by a constant number of [semitones]. Short-circuits when semitones is 0.0. */
fun Ignitor.detune(semitones: Double): Ignitor {
    if (semitones == 0.0) return this
    return DetuneConstIgnitor(this, 2.0.pow(semitones / 12.0))
}

private class DetuneConstIgnitor(
    private val upstream: Ignitor,
    private val ratio: Double,
) : Ignitor {
    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        upstream.generate(buffer, freqHz * ratio, ctx)
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Gain from Ignitor (param slot)
// ═══════════════════════════════════════════════════════════════════════════════

/** Apply audio-rate gain from another exciter. Short-circuits when [gain] is a constant 1.0. */
fun Ignitor.withGain(gain: Ignitor): Ignitor {
    if (gain is ConstantIgnitor && gain.value == 1.0) return this
    if (gain is ParamIgnitor && gain.default == 1.0) return this
    return this * gain
}

/** Shift frequency up one octave. */
fun Ignitor.octaveUp(): Ignitor = detune(12.0)

/** Shift frequency down one octave. */
fun Ignitor.octaveDown(): Ignitor = detune(-12.0)
