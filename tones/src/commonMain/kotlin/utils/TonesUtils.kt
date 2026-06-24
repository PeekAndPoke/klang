/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * Portions derived from tonal.js — Copyright (c) 2015 danigb.
 * SPDX-License-Identifier: MIT
 * Full license: tones/LICENSE
 */

package io.peekandpoke.klang.tones.utils

import kotlin.math.abs

/**
 * General utility functions for the tones library.
 */
object TonesUtils {
    /**
     * Repeats a string n times.
     *
     * @param s The string to repeat.
     * @param n The number of times to repeat.
     * @return The repeated string.
     */
    fun fillStr(s: String, n: Int): String {
        return s.repeat(abs(n))
    }
}
