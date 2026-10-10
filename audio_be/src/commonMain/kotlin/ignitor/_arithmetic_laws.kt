/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("NOTHING_TO_INLINE")

package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.utils.fastExp
import io.peekandpoke.klang.audio_be.utils.safeDiv
import io.peekandpoke.klang.audio_be.utils.safeOut
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sqrt
import kotlin.math.tanh

// ═══════════════════════════════════════════════════════════════════════════════
// The pointwise arithmetic laws, each written once, and the ladders that run them
// ═══════════════════════════════════════════════════════════════════════════════
//
// Engine tidy-up step 11 (d1), audit B4.8 and B2.15. The 8 binary and 12 unary arithmetic nodes (`PlusIgnitor` to
// `SqIgnitor`, in `Ignitor.kt`) each wrote their law twice, in `generate` and in their scalar, and the
// signed pow five times. Each law is now one inline function here, and every node calls it on both paths: through
// [binaryLadder] or [unaryMap] when it renders, directly for its scalar.
//
// One class per op stays on purpose (review round 1 of step 11): a single shared node class made the scalar path one
// recursive method that neither the JVM nor V8 inlines through, so a block-constant subtree boxed its scalars
// (960 bytes per block on the JVM for 20 nodes of the optimizer's `x.affine(mul = 1.div(param))`, 0 before). With a
// class per op every scalar (`controlRateValue`) is a small method of its own, as before the step.
//
// The clamps per op, on purpose and each one load-bearing (`audio/ref/numerical-safety.md`):
//  - `Plus` and `Minus` are bare: clamp-free by contract. Clamping one arm only would break bit-identity between the
//    arms (1e15 + 1e15 is 2e15 bare but 1e15 clamped), so it is everywhere or nowhere, and the contract says nowhere;
//  - `Times`, `Div`, `Pow`, `Recip`, `Sq` and `Exp` apply `safeOut` to their output;
//  - `Div`, `Mod` and `Recip` apply `safeDiv` to the divisor; `Mod` has NO `safeOut` (rem's magnitude is bounded by
//    the divisor's); `Div` alone maps a divisor of exactly zero to zero (maintainer, 2026-09-15), `Recip` keeps the
//    `safeDiv` substitution at zero.

// ── The binary laws ──────────────────────────────────────────────────────────────

internal inline fun plusLaw(a: Double, b: Double): Double = a + b

internal inline fun minusLaw(a: Double, b: Double): Double = a - b

/** The Times law, also the body of the scalar `mul(k)` door (`MulConstIgnitor`), so the two agree by construction. */
internal inline fun timesLaw(a: Double, b: Double): Double = safeOut(a * b)

/**
 * The divisor half of the guarded laws (Div, Mod): `safeDiv`. Split from the quotient so a constant divisor is
 * guarded once per block ([binaryLadder]'s `prepareRight`), not once per sample.
 */
internal inline fun divisorOf(b: Double): Double = safeDiv(b)

/** The quotient half of [divLaw], over a divisor [divisorOf] already guarded. */
internal inline fun divQuotient(a: Double, d: Double): Double = safeOut(a / d)

/** TRUE division (never a reciprocal multiply: it rounds differently); a divisor of exactly zero yields zero. */
internal inline fun divLaw(a: Double, b: Double): Double = if (b == 0.0) 0.0 else divQuotient(a = a, d = divisorOf(b))

/** The remainder half of [modLaw], over a divisor [divisorOf] already guarded. */
internal inline fun modRemainder(a: Double, d: Double): Double = a % d

/** Kotlin `rem` (the sign follows the dividend), `safeDiv` on the divisor, no `safeOut`. */
internal inline fun modLaw(a: Double, b: Double): Double = modRemainder(a = a, d = divisorOf(b))

/** Signed magnitude: a negative base gives `-(|base|^exp)` instead of NaN. A NaN base takes the negative branch. */
private inline fun signedPow(base: Double, exp: Double): Double = if (base >= 0.0) base.pow(exp) else -((-base).pow(exp))

internal inline fun powLaw(a: Double, b: Double): Double = safeOut(signedPow(base = a, exp = b))

/** Not value-commutative under NaN (`a` NaN gives `b`, `b` NaN gives `b`): every arm keeps `a` the FIRST operand. */
internal inline fun minLaw(a: Double, b: Double): Double = if (a < b) a else b

/** See [minLaw], with `>`. */
internal inline fun maxLaw(a: Double, b: Double): Double = if (a > b) a else b

// ── The unary laws ───────────────────────────────────────────────────────────────

/** `-0.0` stays `-0.0` (it is not below 0), NaN stays NaN. */
internal inline fun absLaw(v: Double): Double = if (v < 0.0) -v else v

internal inline fun expLaw(v: Double): Double = safeOut(fastExp(v))

/** Signed magnitude: `ln(v)` above 0, `-ln(-v)` below; 0, -0.0 and NaN give 0 (no `-Inf` or NaN onto the path). */
internal inline fun logLaw(v: Double): Double = when {
    v > 0.0 -> ln(v)
    v < 0.0 -> -ln(-v)
    else -> 0.0
}

/** Signed magnitude: `-sqrt(-v)` below 0. A NaN takes the negative branch and stays NaN. */
internal inline fun sqrtLaw(v: Double): Double = if (v >= 0.0) sqrt(v) else -sqrt(-v)

/** 1, -1, or 0 for 0, -0.0 and NaN. */
internal inline fun signLaw(v: Double): Double = when {
    v > 0.0 -> 1.0
    v < 0.0 -> -1.0
    else -> 0.0
}

internal inline fun tanhLaw(v: Double): Double = tanh(v)

internal inline fun floorLaw(v: Double): Double = floor(v)

internal inline fun ceilLaw(v: Double): Double = ceil(v)

/** `kotlin.math.round`: ties go to the even neighbour (0.5 to 0, 1.5 to 2, -0.5 to -0). */
internal inline fun roundLaw(v: Double): Double = round(v)

internal inline fun fracLaw(v: Double): Double = v - floor(v)

/** `safeDiv` on the input: at zero this is the `SAFE_MIN` substitution (about `SAFE_MAX`), not Div's zero. */
internal inline fun recipLaw(v: Double): Double = safeOut(1.0 / safeDiv(v))

internal inline fun sqLaw(v: Double): Double = safeOut(v * v)

// ── The ladders ──────────────────────────────────────────────────────────────────

/**
 * The constant-fold ladder of a binary node `a op b`, four arms, the first that applies wins:
 *  1. both operands block-constant: one [law], one fill;
 *  2. the right operand block-constant: [a] renders into [buffer], and [lawRight] runs against the constant as
 *     [prepareRight] made it, once per block (Div and Mod guard their divisor there; every other op passes it as is);
 *  3. the left operand block-constant: [b] renders, and [law] runs with the constant FIRST (`law(ka, y)`);
 *  4. neither: [a] renders into the buffer, [b] into scratch.
 *
 * Every arm computes the same law on the same IEEE operands as the scratch arm would (`tmp[i] == k` for a
 * block-constant operand), so the arms are bit-identical. A block-constant operand is stateless, so skipping its
 * render advances nothing. A true [Ignitor.isBlockConstant] ([aConst], [bConst]) requires a real scalar by contract (the
 * non-null [Ignitor.controlRateValue], engine follow-ups 8 and 12, step 2), so a constant arm always applies; the
 * "null despite the flag" breach and its fall-through are gone with the nullable return (maintainer, 2026-10-10).
 *
 * **Dead branches** ([deadOnRight], [deadOnLeft]; maintainer, 2026-09-15): an op whose constant side makes the block
 * zero fills `+0.0`, and the other side renders nothing and draws nothing (a noise node takes nothing from the
 * voice's stream). What that costs is the sign of the zeros. Only arms 2 and 3 have them: arm 1 runs the law, so
 * `0.0 * -5.0` both constant is `-0.0`.
 *
 * Until step 11 the left-constant arm of `Plus` and `Times` computed `y + ka` and `safeOut(y * ka)`; the uniform
 * `law(ka, y)` differs only in which payload a NaN + NaN sum carries, which nothing observes (`Times` scrubs NaN to 0).
 *
 * Inline, with every lambda inline: each node gets its own copy with its law in the loops, no closure and no indirect
 * call per sample.
 */
internal inline fun binaryLadder(
    a: Ignitor,
    b: Ignitor,
    aConst: Boolean,
    bConst: Boolean,
    buffer: AudioBuffer,
    freqHz: Double,
    ctx: IgniteContext,
    deadOnRight: (k: Double) -> Boolean,
    deadOnLeft: (k: Double) -> Boolean,
    prepareRight: (k: Double) -> Double,
    lawRight: (x: Double, prepared: Double) -> Double,
    law: (x: Double, y: Double) -> Double,
) {
    if (aConst && bConst) {
        val ka = a.controlRateValue(freqHz)
        val kb = b.controlRateValue(freqHz)

        buffer.fill(law(ka, kb), ctx.offset, ctx.windowEnd)

        return
    }

    if (bConst) {
        val kb = b.controlRateValue(freqHz)

        if (deadOnRight(kb)) {
            buffer.fill(0.0, ctx.offset, ctx.windowEnd)

            return
        }

        val k: Double = prepareRight(kb)

        a.generate(buffer, freqHz, ctx)

        val end = ctx.windowEnd

        for (i in ctx.offset until end) {
            buffer[i] = lawRight(buffer[i], k)
        }

        return
    }

    if (aConst) {
        val ka = a.controlRateValue(freqHz)

        if (deadOnLeft(ka)) {
            buffer.fill(0.0, ctx.offset, ctx.windowEnd)

            return
        }

        val k: Double = ka

        b.generate(buffer, freqHz, ctx)

        val end = ctx.windowEnd

        for (i in ctx.offset until end) {
            buffer[i] = law(k, buffer[i])
        }

        return
    }

    a.generate(buffer, freqHz, ctx)

    ctx.scratchBuffers.use { tmp ->
        b.generate(tmp, freqHz, ctx)

        val end = ctx.windowEnd

        for (i in ctx.offset until end) {
            buffer[i] = law(buffer[i], tmp[i])
        }
    }
}

/**
 * A unary node's render: [upstream] into [buffer], then [law] in place over the window, the window end read after the
 * render. No fold branch on purpose: a constant unary subtree answers `controlRateValue` and folds AT ITS
 * PARENT, which never calls the unary's `generate` (see the policy at the top of `Ignitor.kt`'s arithmetic section).
 */
internal inline fun unaryMap(upstream: Ignitor, buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext, law: (v: Double) -> Double) {
    upstream.generate(buffer, freqHz, ctx)

    val end = ctx.windowEnd

    for (i in ctx.offset until end) {
        buffer[i] = law(buffer[i])
    }
}
