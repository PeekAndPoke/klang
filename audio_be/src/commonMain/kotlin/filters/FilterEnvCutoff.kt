/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.filters

import io.peekandpoke.klang.audio_be.EnvelopeCore
import kotlin.math.pow

/**
 * The cutoff a filter envelope asks for at the voice-relative frame [pos]:
 * `baseCutoff * 2^(depthSemitones / 12 * level)`, with the envelope's level clamped to [0, 1] (depth is
 * SEMITONES, C3 of the filter unification). This [EnvelopeCore] must be prepared for the block. The filter node
 * (`SvfIgnitor`) reads it at its knots through [filterEnvCutoffAtLevel] and multiplies its own drift and tolerance.
 */
@Suppress("NOTHING_TO_INLINE")
internal inline fun EnvelopeCore.filterEnvCutoff(pos: Int, baseCutoff: Double, depthSemitones: Double): Double =
    filterEnvCutoffAtLevel(level = at(pos).coerceIn(0.0, 1.0), baseCutoff = baseCutoff, depthSemitones = depthSemitones)

/** [filterEnvCutoff]'s law at an envelope [level] the caller has already clamped to [0, 1]. */
@Suppress("NOTHING_TO_INLINE")
internal inline fun filterEnvCutoffAtLevel(level: Double, baseCutoff: Double, depthSemitones: Double): Double =
    baseCutoff * 2.0.pow(depthSemitones / 12.0 * level)
