/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.utils.safeDiv
import io.peekandpoke.klang.audio_be.utils.safeOut
import io.peekandpoke.klang.common.math.semitones

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
     * NO RENDER CONTEXT ON PURPOSE — the query is a pure function of the graph and [freqHz]. Every
     * implementation either ignores the block or forwards to its children, so the [IgniteContext]
     * parameter this used to carry was dead weight on all 34 overrides (audited 2026-08-27).
     * Dropping it is what lets the ignitor BUILD ask a node its value at note-on, where no context
     * exists yet (voice-lifetime resolution — see
     * `docs/tasks-archive/2026-08/20260831-ignitor-envelope-ownership.md`). The
     * per-block variation this interface does support arrives through [freqHz] (e.g. [FreqIgnitor]
     * under detune), not through the block. If a node ever needs the block itself, add the
     * parameter back as REQUIRED: that breaks every override and forces a decision at each one,
     * which is the safe direction — a nullable one would fail silently.
     *
     * CONTRACT (load-bearing since the constant-fold in `plus`/`times` consumes this on the
     * AUDIO path, not just for control-rate reads): an override MUST
     *  1. be pure — no state advanced, no side effects; callable any number of times per block,
     *     including zero;
     *  2. be bit-identical to the node's own [generate] output for every sample in
     *     `[offset, offset+length)` of the same block.
     * A node that is merely *slowly varying* must return `null` — a non-null value here turns
     * `x * node` into a stepped per-block multiply with no spec failing loudly.
     */
    fun controlRateValueOrNull(freqHz: Double): Double? = null

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
     *
     * A ZERO-LENGTH window (reachable: `legato` can clip a gate to 0 frames and `Voice.render`
     * still runs the pipeline) returns 0.0 deterministically: `generate` writes nothing there, so
     * the scratch read would otherwise hand back whatever a previous node left in the pool — an
     * arbitrary, run-to-run nondeterministic value (block-framing ledger E5).
     */
    fun blockStartValue(freqHz: Double, ctx: IgniteContext): Double =
        controlRateValueOrNull(freqHz)
            ?: if (ctx.length == 0) {
                0.0
            } else {
                ctx.scratchBuffers.use { tmp -> generate(tmp, freqHz, ctx); tmp[ctx.offset] }
            }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Arithmetic Composition
// ═══════════════════════════════════════════════════════════════════════════════
//
// All combinators implement `Ignitor` as dedicated `private class` types, not SAM
// lambdas. See `audio/ref/performance.md` for the rationale (Rule 1).
//
// The pointwise binary and unary ops (Plus to Mod, Abs to Sq) write no law of their own: each
// law is one inline function in `_arithmetic_laws.kt` (engine tidy-up step 11), which every node
// calls on both its paths, through the shared inline `binaryLadder` or `unaryMap` when it
// renders. One class per op on purpose, so each scalar path stays a small method the JIT inlines.
//
// CONSTANT-FOLD POLICY: binary ops (and the const-heavy ternary slots, Clamp/Range bounds and
// Lerp t) carry fold branches in `generate()` (the ladder). UNARY ops (`neg()` is a multiply by
// -1 since 2026-09-15, so it is not one) deliberately do NOT: they override
// `controlRateValueOrNull`/`isBlockConstant`, so a constant unary subtree folds AT ITS PARENT,
// which never calls the unary's `generate` at all. An internal fill branch would be near-dead
// code that still costs a parity case, a liveness probe and a mutation check each. Residual
// cost: in the few non-folding parent slots (Lerp a/b under a varying t, Select branches) a
// constant unary pays one redundant in-place loop per block, accepted (no scratch, no alloc).

/**
 * Mix two signals additively per-sample. A block-constant operand folds as a scalar (no scratch
 * render); otherwise the second signal renders into a scratch buffer.
 */
operator fun Ignitor.plus(other: Ignitor): Ignitor = PlusIgnitor(a = this, b = other)

private class PlusIgnitor(private val a: Ignitor, private val b: Ignitor) : Ignitor {
    // Structural, computed once, so the non-folding hot path pays no per-block subtree walk.
    private val aConst = a.isBlockConstant
    private val bConst = b.isBlockConstant

    override val isBlockConstant: Boolean = aConst && bConst

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) = binaryLadder(
        a = a,
        b = b,
        aConst = aConst,
        bConst = bConst,
        buffer = buffer,
        freqHz = freqHz,
        ctx = ctx,
        deadOnRight = { false },
        deadOnLeft = { false },
        prepareRight = { k -> k },
        lawRight = { x, k -> plusLaw(a = x, b = k) },
        law = { x, y -> plusLaw(a = x, b = y) },
    )

    override fun controlRateValueOrNull(freqHz: Double): Double? {
        val x = a.controlRateValueOrNull(freqHz) ?: return null
        val y = b.controlRateValueOrNull(freqHz) ?: return null

        return plusLaw(a = x, b = y)
    }
}

/**
 * Ring-modulate two signals by per-sample multiplication. A block-constant operand folds as a
 * scalar (no scratch render); otherwise the second signal renders into a scratch buffer.
 *
 * Output magnitude is clamped to `±SAFE_MAX` and `NaN` is scrubbed to `0` per sample.
 * See `audio/ref/numerical-safety.md` for the safety contract.
 */
operator fun Ignitor.times(other: Ignitor): Ignitor = TimesIgnitor(a = this, b = other)

// `internal` with exposed operands so EqIgnitor can see through a scaled voice-constant
// (a `passes` cascade stage's staggered q) instead of demoting the whole section to
// per-block reconfigure.
internal class TimesIgnitor(internal val a: Ignitor, internal val b: Ignitor) : Ignitor {
    // Structural, computed once, so the non-folding hot path pays no per-block subtree walk.
    private val aConst = a.isBlockConstant
    private val bConst = b.isBlockConstant

    override val isBlockConstant: Boolean = aConst && bConst

    // A block-constant factor of exactly zero on either side is a dead branch (maintainer, 2026-09-15).
    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) = binaryLadder(
        a = a,
        b = b,
        aConst = aConst,
        bConst = bConst,
        buffer = buffer,
        freqHz = freqHz,
        ctx = ctx,
        deadOnRight = { k -> k == 0.0 },
        deadOnLeft = { k -> k == 0.0 },
        prepareRight = { k -> k },
        lawRight = { x, k -> timesLaw(a = x, b = k) },
        law = { x, y -> timesLaw(a = x, b = y) },
    )

    override fun controlRateValueOrNull(freqHz: Double): Double? {
        val x = a.controlRateValueOrNull(freqHz) ?: return null
        val y = b.controlRateValueOrNull(freqHz) ?: return null

        return timesLaw(a = x, b = y)
    }
}

/**
 * `mul · (x + pre) + add` in one pass: the runtime of `IgnitorDsl.Affine`, the optimizer's fold of
 * block-constant arithmetic. Sanitised exactly like the `Plus`/`Times`/`Plus` chain it replaces:
 * `safeOut(mul · (x + pre)) + add` (the Times contract scrubs per op, the Plus contract
 * deliberately does not). An absent pre-add or add arrives as `-0.0`, which the two adds pass
 * through bit for bit (`v + (-0.0)` is `v` for every `v`), so there is no branch for it.
 *
 * Cost, with all three coefficients block-constant (the only shape the optimizer builds): three
 * scalar reads per block and, per sample, two adds, one multiply and one clamp, in one pass
 * where the chain took two or three. With ANY modulated coefficient every coefficient renders
 * per sample through scratch, three buffers held at once where the chain holds one: correct, and
 * slower than the chain; a node for the optimizer, not a door.
 */
fun Ignitor.affine(pre: Ignitor, mul: Ignitor, add: Ignitor): Ignitor = AffineIgnitor(inner = this, pre = pre, mul = mul, add = add)

/**
 * `internal` with `internal` operands, like [TimesIgnitor]: `EqIgnitor` looks through it to tell a
 * voice-constant section coefficient (the C5 passes cascade, once the optimizer folds its
 * `Times(q, Constant)` into this), and the spec asserts the DSL node lowers to this class.
 */
internal class AffineIgnitor(
    internal val inner: Ignitor,
    internal val pre: Ignitor,
    internal val mul: Ignitor,
    internal val add: Ignitor,
) : Ignitor {
    private val innerConst = inner.isBlockConstant
    private val coefficientsConst = pre.isBlockConstant && mul.isBlockConstant && add.isBlockConstant

    override val isBlockConstant: Boolean = innerConst && coefficientsConst

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        if (coefficientsConst) {
            val kp = pre.controlRateValueOrNull(freqHz)
            val km = mul.controlRateValueOrNull(freqHz)
            val ka = add.controlRateValueOrNull(freqHz)

            if (kp != null && km != null && ka != null) {
                if (innerConst) {
                    val kx = inner.controlRateValueOrNull(freqHz)

                    if (kx != null) {
                        buffer.fill(safeOut(km * (kx + kp)) + ka, ctx.offset, ctx.windowEnd)

                        return
                    }
                }

                // a multiplier of exactly zero is a dead branch, as in TimesIgnitor: the inner
                // renders nothing and the block is the add alone
                if (km == 0.0) {
                    buffer.fill(0.0 + ka, ctx.offset, ctx.windowEnd)

                    return
                }

                inner.generate(buffer, freqHz, ctx)

                // the window is read AFTER the child render, like every sibling combinator
                val end = ctx.windowEnd

                for (i in ctx.offset until end) {
                    buffer[i] = safeOut(km * (buffer[i] + kp)) + ka
                }

                return
            }

            // A block-constant coefficient whose scalar came back null is a contract breach; the
            // scratch path below is correct for any child: degrade, never throw on the render
            // thread (the Plus policy).
        }

        // A modulated coefficient: per sample, all three through scratch.
        inner.generate(buffer, freqHz, ctx)

        ctx.scratchBuffers.use { p ->
            pre.generate(p, freqHz, ctx)

            ctx.scratchBuffers.use { m ->
                mul.generate(m, freqHz, ctx)

                ctx.scratchBuffers.use { a ->
                    add.generate(a, freqHz, ctx)

                    val end = ctx.windowEnd

                    for (i in ctx.offset until end) {
                        buffer[i] = safeOut(m[i] * (buffer[i] + p[i])) + a[i]
                    }
                }
            }
        }
    }

    override fun controlRateValueOrNull(freqHz: Double): Double? {
        val x = inner.controlRateValueOrNull(freqHz) ?: return null
        val p = pre.controlRateValueOrNull(freqHz) ?: return null
        val m = mul.controlRateValueOrNull(freqHz) ?: return null
        val a = add.controlRateValueOrNull(freqHz) ?: return null

        return safeOut(m * (x + p)) + a
    }
}

/** Scale signal amplitude per-sample by an audio-rate [factor]. Delegates to [times]. */
fun Ignitor.mul(factor: Ignitor): Ignitor = this * factor

/** Scale signal amplitude per-sample by a constant [factor]. Short-circuits when factor is 1.0. */
fun Ignitor.mul(factor: Double): Ignitor {
    if (factor == 1.0) return this

    // a factor of exactly zero is a dead branch: nothing upstream renders (see TimesIgnitor)
    if (factor == 0.0) {
        return ConstantIgnitor(0.0)
    }

    return MulConstIgnitor(this, factor)
}

private class MulConstIgnitor(private val upstream: Ignitor, private val factor: Double) : Ignitor {
    override val isBlockConstant: Boolean = upstream.isBlockConstant

    // The Times law (`timesLaw`) on both paths, so `.mul(2.0)` and a Times fold agree by construction.
    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        upstream.generate(buffer, freqHz, ctx)

        val end = ctx.windowEnd
        val k = factor

        for (i in ctx.offset until end) {
            buffer[i] = timesLaw(a = buffer[i], b = k)
        }
    }

    override fun controlRateValueOrNull(freqHz: Double): Double? {
        val x = upstream.controlRateValueOrNull(freqHz) ?: return null

        return timesLaw(a = x, b = factor)
    }
}

/**
 * Divide signal amplitude per-sample by an audio-rate [divisor].
 *
 * A divisor of exactly zero yields zero (a block-constant zero or infinity is a dead branch:
 * nothing upstream renders). Other divisor magnitudes below `SAFE_MIN` are clamped (sign
 * preserved) so the engine never produces `NaN`/`Inf`. The output is also clamped to
 * `±SAFE_MAX`. See `audio/ref/numerical-safety.md`.
 */
fun Ignitor.div(divisor: Ignitor): Ignitor = DivIgnitor(a = this, b = divisor)

private class DivIgnitor(private val a: Ignitor, private val b: Ignitor) : Ignitor {
    // Structural, computed once, so the non-folding hot path pays no per-block subtree walk.
    private val aConst = a.isBlockConstant
    private val bConst = b.isBlockConstant

    override val isBlockConstant: Boolean = aConst && bConst

    // A block-constant divisor of exactly zero or an infinity is a dead branch (maintainer, 2026-09-15): its quotient
    // is a zero, and the optimizer's reciprocal of it is a zero multiplier. Any other constant divisor is guarded once
    // per block. A constant zero on the LEFT is not dead.
    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) = binaryLadder(
        a = a,
        b = b,
        aConst = aConst,
        bConst = bConst,
        buffer = buffer,
        freqHz = freqHz,
        ctx = ctx,
        deadOnRight = { k -> k == 0.0 || k.isInfinite() },
        deadOnLeft = { false },
        prepareRight = { k -> divisorOf(k) },
        lawRight = { x, d -> divQuotient(a = x, d = d) },
        law = { x, y -> divLaw(a = x, b = y) },
    )

    override fun controlRateValueOrNull(freqHz: Double): Double? {
        val x = a.controlRateValueOrNull(freqHz) ?: return null
        val y = b.controlRateValueOrNull(freqHz) ?: return null

        return divLaw(a = x, b = y)
    }
}

/**
 * Divide signal amplitude by a constant [divisor].
 * A zero divisor is a dead branch (silence, nothing upstream renders); any other divisor is
 * clamped to `±SAFE_MIN` and the output to `±SAFE_MAX`.
 */
fun Ignitor.div(divisor: Double): Ignitor {
    if (divisor == 0.0) {
        return ConstantIgnitor(0.0)
    }

    val safeFactor = 1.0 / safeDiv(divisor)

    return mul(safeFactor)
}

/** Subtract another signal from this one (per-sample). Uses a scratch buffer for the second signal. */
fun Ignitor.minus(other: Ignitor): Ignitor = MinusIgnitor(a = this, b = other)

private class MinusIgnitor(private val a: Ignitor, private val b: Ignitor) : Ignitor {
    // Structural, computed once, so the non-folding hot path pays no per-block subtree walk.
    private val aConst = a.isBlockConstant
    private val bConst = b.isBlockConstant

    override val isBlockConstant: Boolean = aConst && bConst

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) = binaryLadder(
        a = a,
        b = b,
        aConst = aConst,
        bConst = bConst,
        buffer = buffer,
        freqHz = freqHz,
        ctx = ctx,
        deadOnRight = { false },
        deadOnLeft = { false },
        prepareRight = { k -> k },
        lawRight = { x, k -> minusLaw(a = x, b = k) },
        law = { x, y -> minusLaw(a = x, b = y) },
    )

    override fun controlRateValueOrNull(freqHz: Double): Double? {
        val x = a.controlRateValueOrNull(freqHz) ?: return null
        val y = b.controlRateValueOrNull(freqHz) ?: return null

        return minusLaw(a = x, b = y)
    }
}

/**
 * Negate this signal (flip polarity, per-sample). A multiply by `-1`, clamp included: there is
 * no dedicated negation (maintainer, 2026-09-15), so the optimizer folds it like any level.
 */
fun Ignitor.neg(): Ignitor = mul(-1.0)

/** Absolute value of this signal (per-sample, full-wave rectification). */
fun Ignitor.abs(): Ignitor = AbsIgnitor(this)

private class AbsIgnitor(private val upstream: Ignitor) : Ignitor {
    override val isBlockConstant: Boolean = upstream.isBlockConstant

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) =
        unaryMap(upstream = upstream, buffer = buffer, freqHz = freqHz, ctx = ctx) { v -> absLaw(v) }

    override fun controlRateValueOrNull(freqHz: Double): Double? {
        val v = upstream.controlRateValueOrNull(freqHz) ?: return null

        return absLaw(v)
    }
}

/**
 * Raise this signal to the power of [exp] (per-sample).
 *
 * Signed-magnitude: negative bases produce `-(|base|^exp)` to avoid `NaN`.
 * `0^negative = +Inf` is caught by the output clamp (`±SAFE_MAX`).
 */
fun Ignitor.pow(exp: Ignitor): Ignitor = PowIgnitor(base = this, exp = exp)

private class PowIgnitor(private val base: Ignitor, private val exp: Ignitor) : Ignitor {
    // Structural, computed once, so the non-folding hot path pays no per-block subtree walk.
    private val baseConst = base.isBlockConstant
    private val expConst = exp.isBlockConstant

    override val isBlockConstant: Boolean = baseConst && expConst

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) = binaryLadder(
        a = base,
        b = exp,
        aConst = baseConst,
        bConst = expConst,
        buffer = buffer,
        freqHz = freqHz,
        ctx = ctx,
        deadOnRight = { false },
        deadOnLeft = { false },
        prepareRight = { k -> k },
        lawRight = { x, k -> powLaw(a = x, b = k) },
        law = { x, y -> powLaw(a = x, b = y) },
    )

    override fun controlRateValueOrNull(freqHz: Double): Double? {
        val x = base.controlRateValueOrNull(freqHz) ?: return null
        val y = exp.controlRateValueOrNull(freqHz) ?: return null

        return powLaw(a = x, b = y)
    }
}

/** Per-sample minimum of this signal and [other]. Uses a scratch buffer for [other]. */
fun Ignitor.min(other: Ignitor): Ignitor = MinIgnitor(a = this, b = other)

private class MinIgnitor(private val a: Ignitor, private val b: Ignitor) : Ignitor {
    // Structural, computed once, so the non-folding hot path pays no per-block subtree walk.
    private val aConst = a.isBlockConstant
    private val bConst = b.isBlockConstant

    override val isBlockConstant: Boolean = aConst && bConst

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) = binaryLadder(
        a = a,
        b = b,
        aConst = aConst,
        bConst = bConst,
        buffer = buffer,
        freqHz = freqHz,
        ctx = ctx,
        deadOnRight = { false },
        deadOnLeft = { false },
        prepareRight = { k -> k },
        lawRight = { x, k -> minLaw(a = x, b = k) },
        law = { x, y -> minLaw(a = x, b = y) },
    )

    override fun controlRateValueOrNull(freqHz: Double): Double? {
        val x = a.controlRateValueOrNull(freqHz) ?: return null
        val y = b.controlRateValueOrNull(freqHz) ?: return null

        return minLaw(a = x, b = y)
    }
}

/** Per-sample maximum of this signal and [other]. Uses a scratch buffer for [other]. */
fun Ignitor.max(other: Ignitor): Ignitor = MaxIgnitor(a = this, b = other)

private class MaxIgnitor(private val a: Ignitor, private val b: Ignitor) : Ignitor {
    // Structural, computed once, so the non-folding hot path pays no per-block subtree walk.
    private val aConst = a.isBlockConstant
    private val bConst = b.isBlockConstant

    override val isBlockConstant: Boolean = aConst && bConst

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) = binaryLadder(
        a = a,
        b = b,
        aConst = aConst,
        bConst = bConst,
        buffer = buffer,
        freqHz = freqHz,
        ctx = ctx,
        deadOnRight = { false },
        deadOnLeft = { false },
        prepareRight = { k -> k },
        lawRight = { x, k -> maxLaw(a = x, b = k) },
        law = { x, y -> maxLaw(a = x, b = y) },
    )

    override fun controlRateValueOrNull(freqHz: Double): Double? {
        val x = a.controlRateValueOrNull(freqHz) ?: return null
        val y = b.controlRateValueOrNull(freqHz) ?: return null

        return maxLaw(a = x, b = y)
    }
}

/** Bound this signal to `[lo, hi]` per sample. Uses two scratch buffers. */
fun Ignitor.clamp(lo: Ignitor, hi: Ignitor): Ignitor = ClampIgnitor(upstream = this, lo = lo, hi = hi)

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
            val kl = lo.controlRateValueOrNull(freqHz)
            val kh = hi.controlRateValueOrNull(freqHz)
            if (kl != null && kh != null) {
                upstream.generate(buffer, freqHz, ctx)
                val end = ctx.windowEnd
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
                val end = ctx.windowEnd
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

    override fun controlRateValueOrNull(freqHz: Double): Double? {
        val v = upstream.controlRateValueOrNull(freqHz) ?: return null
        val l = lo.controlRateValueOrNull(freqHz) ?: return null
        val h = hi.controlRateValueOrNull(freqHz) ?: return null

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

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) =
        unaryMap(upstream = upstream, buffer = buffer, freqHz = freqHz, ctx = ctx) { v -> expLaw(v) }

    override fun controlRateValueOrNull(freqHz: Double): Double? {
        val v = upstream.controlRateValueOrNull(freqHz) ?: return null

        return expLaw(v)
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

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) =
        unaryMap(upstream = upstream, buffer = buffer, freqHz = freqHz, ctx = ctx) { v -> logLaw(v) }

    override fun controlRateValueOrNull(freqHz: Double): Double? {
        val v = upstream.controlRateValueOrNull(freqHz) ?: return null

        return logLaw(v)
    }
}

/** Square root per sample. Signed-magnitude: `x < 0` → `−√(−x)`. */
fun Ignitor.sqrt(): Ignitor = SqrtIgnitor(this)

private class SqrtIgnitor(private val upstream: Ignitor) : Ignitor {
    override val isBlockConstant: Boolean = upstream.isBlockConstant

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) =
        unaryMap(upstream = upstream, buffer = buffer, freqHz = freqHz, ctx = ctx) { v -> sqrtLaw(v) }

    override fun controlRateValueOrNull(freqHz: Double): Double? {
        val v = upstream.controlRateValueOrNull(freqHz) ?: return null

        return sqrtLaw(v)
    }
}

/** Sign of the inner signal: `-1`, `0`, or `+1`. */
fun Ignitor.sign(): Ignitor = SignIgnitor(this)

private class SignIgnitor(private val upstream: Ignitor) : Ignitor {
    override val isBlockConstant: Boolean = upstream.isBlockConstant

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) =
        unaryMap(upstream = upstream, buffer = buffer, freqHz = freqHz, ctx = ctx) { v -> signLaw(v) }

    override fun controlRateValueOrNull(freqHz: Double): Double? {
        val v = upstream.controlRateValueOrNull(freqHz) ?: return null

        return signLaw(v)
    }
}

/** `tanh(x)` per sample. */
fun Ignitor.tanh(): Ignitor = TanhIgnitor(this)

private class TanhIgnitor(private val upstream: Ignitor) : Ignitor {
    override val isBlockConstant: Boolean = upstream.isBlockConstant

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) =
        unaryMap(upstream = upstream, buffer = buffer, freqHz = freqHz, ctx = ctx) { v -> tanhLaw(v) }

    override fun controlRateValueOrNull(freqHz: Double): Double? {
        val v = upstream.controlRateValueOrNull(freqHz) ?: return null

        return tanhLaw(v)
    }
}

/**
 * Linear interpolation: `this·(1−t) + other·t`, i.e. [t] is how much of [other] is heard.
 *
 * `t = 0` is this signal alone, `t = 1` is [other] alone; values outside `[0, 1]` extrapolate and
 * are deliberately NOT clamped (see `audio/ref/numerical-safety.md`). One scratch buffer for a
 * block-constant [t], two when [t] is audio-rate.
 */
fun Ignitor.lerp(other: Ignitor, t: Ignitor): Ignitor = LerpIgnitor(from = this, to = other, weight = t)

/**
 * @param from The signal heard at `weight = 0` — `IgnitorDsl.Lerp.left`, the chain the DSL call
 *   hangs off (`x.lerp(y, t)` puts `x` here). Rendered straight into the output buffer.
 * @param to The signal heard at `weight = 1` — `IgnitorDsl.Lerp.right`, the DSL's `other`.
 *   Rendered into scratch, then mixed in.
 * @param weight The per-sample crossfade position — `IgnitorDsl.Lerp.t`. Block-constant here is
 *   the common case (a literal `0.3`) and takes the cheaper path in [generate].
 */
private class LerpIgnitor(
    private val from: Ignitor,
    private val to: Ignitor,
    private val weight: Ignitor,
) : Ignitor {
    private val weightConst = weight.isBlockConstant

    override val isBlockConstant: Boolean =
        from.isBlockConstant && to.isBlockConstant && weightConst

    override fun controlRateValueOrNull(freqHz: Double): Double? {
        val x = from.controlRateValueOrNull(freqHz) ?: return null
        val y = to.controlRateValueOrNull(freqHz) ?: return null
        val w = weight.controlRateValueOrNull(freqHz) ?: return null

        return x * (1.0 - w) + y * w
    }

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        // Partial fold: a block-constant `weight` (the common `lerp(x, y, 0.3)` shape) skips one
        // scratch render. Folding constant from/to too would be combinatorial for a rare shape —
        // deliberately not done; a fully-constant Lerp folds at a FOLDING parent via the
        // overrides above (non-folding slots still run this loop — reachable, just rare).
        // Same breach policy as PlusIgnitor (null despite flag -> scratch path below).
        if (weightConst) {
            val kw = weight.controlRateValueOrNull(freqHz)

            if (kw != null) {
                // `1 − kw` is loop-invariant, hoisted NOT because the JIT would miss it (C2 and
                // TurboFan both hoist a loop-invariant on a local) but because the baseline tiers
                // run first and a short voice can be gone before the loop ever tiers up.
                // Bit-identical either way.
                val end = ctx.windowEnd
                val kwInv = 1.0 - kw

                from.generate(buffer, freqHz, ctx)

                ctx.scratchBuffers.use { toBuf ->
                    to.generate(toBuf, freqHz, ctx)

                    for (i in ctx.offset until end) {
                        buffer[i] = buffer[i] * kwInv + toBuf[i] * kw
                    }
                }

                return
            }
        }

        from.generate(buffer, freqHz, ctx)

        ctx.scratchBuffers.use { toBuf ->
            to.generate(toBuf, freqHz, ctx)

            ctx.scratchBuffers.use { weightBuf ->
                val end = ctx.windowEnd

                weight.generate(weightBuf, freqHz, ctx)

                // NOTHING to hoist here: `w` changes every sample, so `1 − w` does too. The
                // cheaper algebraic form `from + (to − from)·w` is deliberately NOT used — it
                // would break bit-parity with the constant path above (ConstantFoldParitySpec)
                // and lose the exact endpoints (`w = 1` must return `to` bit-exactly).
                for (i in ctx.offset until end) {
                    val w = weightBuf[i]

                    buffer[i] = buffer[i] * (1.0 - w) + toBuf[i] * w
                }
            }
        }
    }
}

/** Maps `[-1, 1]` → `[from, to]` per sample: `from + (x + 1)·0.5·(to − from)`. */
fun Ignitor.range(from: Ignitor, to: Ignitor): Ignitor = RangeIgnitor(upstream = this, from = from, to = to)

private class RangeIgnitor(
    private val upstream: Ignitor,
    private val from: Ignitor,
    private val to: Ignitor,
) : Ignitor {
    private val boundsConst = from.isBlockConstant && to.isBlockConstant

    override val isBlockConstant: Boolean = upstream.isBlockConstant && boundsConst

    override fun controlRateValueOrNull(freqHz: Double): Double? {
        val v = upstream.controlRateValueOrNull(freqHz) ?: return null
        val f = from.controlRateValueOrNull(freqHz) ?: return null
        val t = to.controlRateValueOrNull(freqHz) ?: return null

        return f + (v + 1.0) * 0.5 * (t - f)
    }

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        // Partial fold: block-constant bounds (the dominant `range(200, 4000)` shape) skip BOTH
        // scratch renders. Single-const-bound combinatorics deliberately not done — rare shape.
        // Same breach policy as PlusIgnitor (null despite flag -> scratch path below).
        if (boundsConst) {
            val kFrom = from.controlRateValueOrNull(freqHz)
            val kTo = to.controlRateValueOrNull(freqHz)

            if (kFrom != null && kTo != null) {
                // Half the span, hoisted for the same reason as `LerpIgnitor`'s `kwInv`. The
                // re-association is safe: scaling by 0.5 is exact, so `((x+1)·0.5)·span` and
                // `(x+1)·(span·0.5)` are both ONE rounding of the same real product, and
                // `ConstantFoldParitySpec` pins this loop against the audio-rate one below.
                val end = ctx.windowEnd
                val halfSpan = 0.5 * (kTo - kFrom)

                upstream.generate(buffer, freqHz, ctx)

                for (i in ctx.offset until end) {
                    buffer[i] = kFrom + (buffer[i] + 1.0) * halfSpan
                }

                return
            }
        }

        upstream.generate(buffer, freqHz, ctx)

        ctx.scratchBuffers.use { fromBuf ->
            from.generate(fromBuf, freqHz, ctx)

            ctx.scratchBuffers.use { toBuf ->
                val end = ctx.windowEnd

                to.generate(toBuf, freqHz, ctx)

                // `t − f` is per-sample here, not invariant: both bounds are audio-rate signals.
                // The form stays unfactored to match `controlRateValueOrNull` bit-for-bit
                // (ControlRateScalarParitySpec renders this path as the scalar's oracle).
                for (i in ctx.offset until end) {
                    val f = fromBuf[i]
                    val t = toBuf[i]

                    buffer[i] = f + (buffer[i] + 1.0) * 0.5 * (t - f)
                }
            }
        }
    }
}

/** Per-sample floor. */
fun Ignitor.floor(): Ignitor = FloorIgnitor(this)

private class FloorIgnitor(private val upstream: Ignitor) : Ignitor {
    override val isBlockConstant: Boolean = upstream.isBlockConstant

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) =
        unaryMap(upstream = upstream, buffer = buffer, freqHz = freqHz, ctx = ctx) { v -> floorLaw(v) }

    override fun controlRateValueOrNull(freqHz: Double): Double? {
        val v = upstream.controlRateValueOrNull(freqHz) ?: return null

        return floorLaw(v)
    }
}

/** Per-sample ceiling. */
fun Ignitor.ceil(): Ignitor = CeilIgnitor(this)

private class CeilIgnitor(private val upstream: Ignitor) : Ignitor {
    override val isBlockConstant: Boolean = upstream.isBlockConstant

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) =
        unaryMap(upstream = upstream, buffer = buffer, freqHz = freqHz, ctx = ctx) { v -> ceilLaw(v) }

    override fun controlRateValueOrNull(freqHz: Double): Double? {
        val v = upstream.controlRateValueOrNull(freqHz) ?: return null

        return ceilLaw(v)
    }
}

/** Per-sample round to nearest integer. */
fun Ignitor.round(): Ignitor = RoundIgnitor(this)

private class RoundIgnitor(private val upstream: Ignitor) : Ignitor {
    override val isBlockConstant: Boolean = upstream.isBlockConstant

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) =
        unaryMap(upstream = upstream, buffer = buffer, freqHz = freqHz, ctx = ctx) { v -> roundLaw(v) }

    override fun controlRateValueOrNull(freqHz: Double): Double? {
        val v = upstream.controlRateValueOrNull(freqHz) ?: return null

        return roundLaw(v)
    }
}

/** Per-sample fractional part: `x − floor(x)`. */
fun Ignitor.frac(): Ignitor = FracIgnitor(this)

private class FracIgnitor(private val upstream: Ignitor) : Ignitor {
    override val isBlockConstant: Boolean = upstream.isBlockConstant

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) =
        unaryMap(upstream = upstream, buffer = buffer, freqHz = freqHz, ctx = ctx) { v -> fracLaw(v) }

    override fun controlRateValueOrNull(freqHz: Double): Double? {
        val v = upstream.controlRateValueOrNull(freqHz) ?: return null

        return fracLaw(v)
    }
}

/**
 * Per-sample modulo (Kotlin `rem` semantics — sign follows the dividend).
 *
 * Divisor magnitudes below `SAFE_MIN` are clamped (sign preserved). Uses one scratch buffer.
 * See `audio/ref/numerical-safety.md`.
 */
fun Ignitor.mod(other: Ignitor): Ignitor = ModIgnitor(a = this, b = other)

private class ModIgnitor(private val a: Ignitor, private val b: Ignitor) : Ignitor {
    // Structural, computed once, so the non-folding hot path pays no per-block subtree walk.
    private val aConst = a.isBlockConstant
    private val bConst = b.isBlockConstant

    override val isBlockConstant: Boolean = aConst && bConst

    // A constant divisor is guarded once per block.
    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) = binaryLadder(
        a = a,
        b = b,
        aConst = aConst,
        bConst = bConst,
        buffer = buffer,
        freqHz = freqHz,
        ctx = ctx,
        deadOnRight = { false },
        deadOnLeft = { false },
        prepareRight = { k -> divisorOf(k) },
        lawRight = { x, d -> modRemainder(a = x, d = d) },
        law = { x, y -> modLaw(a = x, b = y) },
    )

    override fun controlRateValueOrNull(freqHz: Double): Double? {
        val x = a.controlRateValueOrNull(freqHz) ?: return null
        val y = b.controlRateValueOrNull(freqHz) ?: return null

        return modLaw(a = x, b = y)
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

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) =
        unaryMap(upstream = upstream, buffer = buffer, freqHz = freqHz, ctx = ctx) { v -> recipLaw(v) }

    override fun controlRateValueOrNull(freqHz: Double): Double? {
        val v = upstream.controlRateValueOrNull(freqHz) ?: return null

        return recipLaw(v)
    }
}

/** Per-sample square: `x · x`. Output clamped to `±SAFE_MAX` (caught for `|x| > ~3.16e7`). */
fun Ignitor.sq(): Ignitor = SqIgnitor(this)

private class SqIgnitor(private val upstream: Ignitor) : Ignitor {
    override val isBlockConstant: Boolean = upstream.isBlockConstant

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) =
        unaryMap(upstream = upstream, buffer = buffer, freqHz = freqHz, ctx = ctx) { v -> sqLaw(v) }

    override fun controlRateValueOrNull(freqHz: Double): Double? {
        val v = upstream.controlRateValueOrNull(freqHz) ?: return null

        return sqLaw(v)
    }
}

/**
 * Per-sample conditional: when this signal `> 0`, use [whenTrue]; else [whenFalse].
 *
 * Both branches are evaluated at audio rate — no short-circuit — so any stateful
 * sources advance regardless of which branch is selected.
 */
fun Ignitor.select(whenTrue: Ignitor, whenFalse: Ignitor): Ignitor =
    SelectIgnitor(cond = this, whenTrue = whenTrue, whenFalse = whenFalse)

private class SelectIgnitor(
    private val cond: Ignitor,
    private val whenTrue: Ignitor,
    private val whenFalse: Ignitor,
) : Ignitor {
    override val isBlockConstant: Boolean =
        cond.isBlockConstant && whenTrue.isBlockConstant && whenFalse.isBlockConstant

    override fun controlRateValueOrNull(freqHz: Double): Double? {
        // Resolve ALL THREE children before returning (the MinIgnitor pattern): generate
        // renders both branches unconditionally so their state advances; a short-circuit on
        // the condition would let an untaken stateful branch fall behind the render path.
        val c = cond.controlRateValueOrNull(freqHz) ?: return null
        val tv = whenTrue.controlRateValueOrNull(freqHz) ?: return null
        val fv = whenFalse.controlRateValueOrNull(freqHz) ?: return null

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
                val end = ctx.windowEnd

                whenFalse.generate(fBuf, freqHz, ctx)

                for (i in ctx.offset until end) {
                    buffer[i] = if (buffer[i] > 0.0) tBuf[i] else fBuf[i]
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Frequency Modifiers
// ═══════════════════════════════════════════════════════════════════════════════

/** Shift frequency by [semitones] from an audio-rate exciter. Reads the first sample per block for the detune value. */
fun Ignitor.detune(semitones: Ignitor): Ignitor = DetuneIgnitor(upstream = this, semitones = semitones)

// Detune (both forms) deliberately has NO controlRateValueOrNull/isBlockConstant override:
// it is a PITCH node — it changes the freqHz its upstream sees, not a pointwise value — so
// "block-constant" is not a meaningful property of its output. Do not "complete" the set.
private class DetuneIgnitor(
    private val upstream: Ignitor,
    private val semitones: Ignitor,
) : Ignitor {
    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        val s = semitones.blockStartValue(freqHz, ctx)
        val ratio = s.semitones()
        upstream.generate(buffer, freqHz * ratio, ctx)
    }
}

/** Shift frequency by a constant number of [semitones]. Short-circuits when semitones is 0.0. */
fun Ignitor.detune(semitones: Double): Ignitor {
    if (semitones == 0.0) return this

    return DetuneConstIgnitor(this, semitones.semitones())
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
