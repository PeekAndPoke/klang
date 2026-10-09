/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.utils

import io.peekandpoke.ultra.common.toFixed
import kotlin.math.pow
import kotlin.math.round

/**
 * Rounds to [decimals] decimal places: `1.23456.roundTo(2)` is `1.23`.
 *
 * Rounds with [round], which takes a half to the even neighbour (`0.125.roundTo(2)` is `0.12`).
 */
internal fun Double.roundTo(decimals: Int): Double {
    val factor = 10.0.pow(decimals)

    // round() instead of roundToLong(): Long is boxed in Kotlin/JS (emulated via wrapper object),
    // causing unnecessary heap allocation. round() returns Double directly, no boxing.
    return round(this * factor) / factor
}

/**
 * [roundTo] [decimals] places, printed without trailing zeros: `2.5.roundToString(3)` is `"2.5"`,
 * `100.0.roundToString(3)` is `"100"`.
 *
 * Prints with [Double.toString], so the platform's own form shows through (Kotlin/JS writes `100.0` as `"100"`).
 * An exponent form (`"1.5e+30"`) is returned as printed, never trimmed.
 * Compare [toFixedTrimmed], which formats with JS `toFixed` and rounds on the binary value instead.
 */
internal fun Double.roundToString(decimals: Int): String {
    val s = roundTo(decimals).toString()
    val dotIdx = s.indexOf('.')

    return if (dotIdx < 0 || 'e' in s) s else s.trimEnd('0').trimEnd('.')
}

/**
 * [toFixed] with [decimals] places, then trailing zeros and a bare dot trimmed: `0.25.toFixedTrimmed(3)` is
 * `"0.25"`, `1000.0.toFixedTrimmed(3)` is `"1000"`.
 *
 * Only a string with a dot and no exponent is trimmed, so `decimals = 0` keeps `"1000"` intact and JS's exponent
 * form for magnitudes from 1e21 up (`"1.5e+30"`) is returned as printed.
 */
internal fun Double.toFixedTrimmed(decimals: Int): String {
    val s = toFixed(decimals)

    return if ('.' in s && 'e' !in s) s.trimEnd('0').trimEnd('.') else s
}

/**
 * The number of decimal places [Double.toString] prints, trailing zeros not counted: `0.25` has 2, `3.0` has 0.
 */
internal fun Double.decimalPlaces(): Int {
    val s = toString()
    val dot = s.indexOf('.')

    return if (dot < 0) 0 else s.substring(dot + 1).trimEnd('0').length
}
