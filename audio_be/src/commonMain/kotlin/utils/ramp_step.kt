/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.utils

/**
 * The per-frame step of a linear ramp from [from] to [to] across [frames] frames: `(to - from) / frames`, with
 * [frames] counted as at least 1 (an empty window steps the whole way at once and divides by nothing smaller).
 *
 * The analog drift's per-block ramp: a drifting oscillator reads its drift multiplier at the block's start and
 * end and walks between them sample by sample (`m += step`).
 */
@Suppress("NOTHING_TO_INLINE")
inline fun rampStep(from: Double, to: Double, frames: Int): Double = (to - from) / frames.coerceAtLeast(1)
