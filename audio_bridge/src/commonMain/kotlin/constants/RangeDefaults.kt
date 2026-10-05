/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge.constants

/**
 * The smallest value `rangex` takes for `from` or `to`: a value at or below it, or NaN, is coerced up to it before
 * the logarithm, so `rangex(0, 1000)` starts at almost nothing instead of reaching `ln(0)`. The same floor and the
 * same comparison (`if (v > floor) v else floor`) on every door, sprudel's `rangex` and the Ignitor's (decided
 * 2026-10-05, `docs/tasks-archive/2026-10/20261005-sprudel-signals-range-cleanup.md` decision 16).
 */
const val RANGEX_FLOOR: Double = 0.0001
