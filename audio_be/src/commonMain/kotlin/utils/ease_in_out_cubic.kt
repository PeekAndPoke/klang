/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.utils

import kotlin.math.pow

/**
 * The in-out cubic easing curve over a progress [x] from 0 to 1: `4 x^3` below the middle, `1 - (2 - 2 x)^3 / 2`
 * from it on. 0 at 0, 0.5 at 0.5, 1 at 1, flat at both ends.
 *
 * The expression, and so every bit, of the library curve the solo ramp used before (`Ease.InOut.cubic` of
 * `io.peekandpoke.ultra.maths`, whose `pow(3.0)` form has `2^(3 - 1) = 4` as its factor), written here so the render
 * path calls no library function through an interface (audit item B4.17). Outside 0 to 1 it extends the two
 * polynomials; NaN gives NaN.
 */
@Suppress("NOTHING_TO_INLINE")
inline fun easeInOutCubic(x: Double): Double =
    if (x < 0.5) 4.0 * x.pow(3.0) else 1.0 - (-2.0 * x + 2.0).pow(3.0) / 2.0
