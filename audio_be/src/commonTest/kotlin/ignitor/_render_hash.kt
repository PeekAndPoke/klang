/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

/**
 * TEST ONLY. A fingerprint of a render in RAW BITS: two independent 32-bit lanes (FNV-1a and a polynomial) over
 * the high and low word of every sample, printed as 16 hex digits. Equal renders give equal strings; one flipped
 * bit anywhere changes both lanes. For baselines that pin a whole render without committing the samples.
 */
fun DoubleArray.rawBitsHash(): String {
    var fnv = FNV_OFFSET
    var poly = size

    for (x in this) {
        val bits = x.toRawBits()
        val hi = (bits ushr 32).toInt()
        val lo = bits.toInt()

        fnv = (fnv xor hi) * FNV_PRIME
        fnv = (fnv xor lo) * FNV_PRIME
        poly = poly * 31 + hi
        poly = poly * 31 + lo
    }

    return fnv.toUInt().toString(16).padStart(8, '0') + poly.toUInt().toString(16).padStart(8, '0')
}

private const val FNV_OFFSET: Int = -2128831035 // 0x811C9DC5
private const val FNV_PRIME: Int = 16777619
