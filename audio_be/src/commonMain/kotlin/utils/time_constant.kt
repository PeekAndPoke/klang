/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.utils

import kotlin.math.exp

/**
 * The coefficient of a one-pole smoother with the time constant [timeSeconds] at [sampleRate] Hz:
 * `1 - exp(-1 / (timeSeconds * sampleRate))`. A smoother `y += c * (x - y)` with this `c` covers `1 - 1/e` (about
 * 63 percent) of a step in [timeSeconds].
 *
 * No guard of its own. The compressor and the ducker clamp their times first; the envelope de-click only checks
 * that its time is above 0, so any positive time reaches this, `+Inf` included. The edges: `+Inf` gives exactly 0
 * (the smoother never moves), a positive zero gives 1 (it follows at once), a negative time a coefficient below 0
 * (`-0.0` gives `-Inf`), NaN gives NaN.
 *
 * Callers: the amplitude envelope's de-click (`EnvelopeDeclick` runs on it), the compressor's attack and releases,
 * the ducker's release. A different law from the bilinear `onePoleLpfCoeff`, which takes a cutoff in Hz, not a time.
 */
@Suppress("NOTHING_TO_INLINE")
inline fun timeConstantCoeff(timeSeconds: Double, sampleRate: Double): Double =
    1.0 - exp(-1.0 / (timeSeconds * sampleRate))
