/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.peekandpoke.klang.common.math.semitones
import io.peekandpoke.klang.tones.interval.Interval

/**
 * The frequency ratio of this interval: `2^(semitones / 12)`.
 *
 * `Interval.get("P5").ratio` is 1.4983, a perfect fifth, and `Interval.get("M3").ratio` is 1.2599.
 * Multiply a frequency by it to transpose by the interval, which is what the script door
 * `"M3".toRatio()` does at a call site like `.oscp("hptrack", "M3".toRatio())`.
 *
 * The Kotlin door for the interval vocabulary of `tones`, so it inherits that vocabulary's
 * spellings: a descending interval carries the minus in front of the NUMBER, either tonal
 * ("-5P", what `Interval.fromSemitones(-7)` returns) or shorthand ("P-5"). "-P5" is NOT a name
 * this parser knows.
 *
 * Check [Interval.empty] before reading this: an unparseable name gives `Interval.NoInterval`,
 * whose `semitones` is `Int.MIN_VALUE`, so the ratio underflows to a meaningless 0.0. The script
 * door does that check and reports the bad name instead.
 */
val Interval.ratio: Double get() = this.semitones.toDouble().semitones()
