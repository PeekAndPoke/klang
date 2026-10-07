/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.utils

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * The numeric guards of `numerical_safety.kt`, pinned directly: the two state-hygiene helpers, the tap guard
 * [finiteOrZero], and the divisor and output clamps of the arithmetic nodes.
 *
 * [flushState] is called at ~58 IIR carry sites by house rule (`/code-style` #8), so the engine's
 * whole "a filter cannot latch" property rests on this one function. Filter-level specs can only
 * see the half of it that changes OUTPUT: dropping the denormal flush is a CPU property with no
 * audible signature, so it survives every filter oracle in the suite. This spec is that
 * mutation's designated killer, and the reason the helper is tested at all rather than only
 * through its callers.
 */
class NumericalSafetySpec : StringSpec({

    "flushState rejects a denormal carry" {
        // The original job: denormals cost 10-100x on some platforms. No filter oracle can see
        // this (the value is ~1e-320, inaudible), so it is pinned here or nowhere.
        (1e-320).flushState() shouldBe 0.0
        (-1e-320).flushState() shouldBe 0.0
        (DENORMAL_THRESHOLD / 2.0).flushState() shouldBe 0.0
    }

    "flushState rejects a non-finite carry" {
        // The half added in the master round: an IIR whose state goes non-finite can never
        // recover, and ONE Inf is enough: the next sample computes `-Inf + Inf`.
        Double.NaN.flushState() shouldBe 0.0
        Double.POSITIVE_INFINITY.flushState() shouldBe 0.0
        Double.NEGATIVE_INFINITY.flushState() shouldBe 0.0
        Double.MAX_VALUE.flushState() shouldBe Double.MAX_VALUE
    }

    "flushState passes normal finite values through untouched" {
        // Bit-identity for real audio is what makes the widening a no-op for shipped sound.
        for (v in listOf(0.5, -0.5, 1.0, -1.0, 1e-14, -1e-14, DENORMAL_THRESHOLD, 1e300)) {
            v.flushState().toRawBits() shouldBe v.toRawBits()
        }
    }

    "nanGuard catches NaN and deliberately does NOT catch Inf" {
        // The division of labour: nanGuard sterilises a SAMPLE against the FIR smear; an Inf
        // travelling a sample path is the raw engine behaving as designed. The permanent-latch
        // class belongs to flushState.
        Double.NaN.nanGuard() shouldBe 0.0
        Double.POSITIVE_INFINITY.nanGuard() shouldBe Double.POSITIVE_INFINITY
        (0.5).nanGuard() shouldBe 0.5
    }

    "finiteOrZero reads NaN and both infinities as 0" {
        Double.NaN.finiteOrZero() shouldBe 0.0
        Double.POSITIVE_INFINITY.finiteOrZero() shouldBe 0.0
        Double.NEGATIVE_INFINITY.finiteOrZero() shouldBe 0.0
    }

    "finiteOrZero passes every finite value bit for bit, the extremes, -0.0 and denormals included" {
        for (v in listOf(0.5, -0.5, 0.0, -0.0, 1e-320, -1e-320, 1e300, Double.MAX_VALUE, -Double.MAX_VALUE)) {
            v.finiteOrZero().toRawBits() shouldBe v.toRawBits()
        }
    }

    "safeDiv keeps a divisor at least 1e-15 from zero, sign kept, and passes the infinities" {
        safeDiv(2.0) shouldBe 2.0
        safeDiv(-2.0) shouldBe -2.0
        safeDiv(1e-20) shouldBe 1e-15
        safeDiv(-1e-20) shouldBe -1e-15
        safeDiv(0.0) shouldBe 1e-15
        safeDiv(-0.0) shouldBe 1e-15
        safeDiv(Double.NaN) shouldBe 1e-15
        safeDiv(Double.POSITIVE_INFINITY) shouldBe Double.POSITIVE_INFINITY
        safeDiv(Double.NEGATIVE_INFINITY) shouldBe Double.NEGATIVE_INFINITY
    }

    "safeOut clamps to plus or minus 1e15 and turns NaN into 0" {
        safeOut(3.0) shouldBe 3.0
        safeOut(-3.0) shouldBe -3.0
        safeOut(1e20) shouldBe 1e15
        safeOut(-1e20) shouldBe -1e15
        safeOut(Double.POSITIVE_INFINITY) shouldBe 1e15
        safeOut(Double.NEGATIVE_INFINITY) shouldBe -1e15
        safeOut(Double.NaN) shouldBe 0.0
    }
})
