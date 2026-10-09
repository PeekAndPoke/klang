/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

/**
 * What a declared compressor stage is configured with: the five knobs, every one resolved to a
 * number. [KatalystCompressorWriter] builds it when the orbit's param state changes, and
 * [KatalystCompressorEffect.configure] compares it BY REFERENCE, so an unchanged owner costs one
 * compare per block.
 *
 * Lived under `Voice` until 2026-10-07 (audit A1.7), though only the chain ever used it.
 */
class CompressorSettings(
    val thresholdDb: Double,
    val ratio: Double,
    val kneeDb: Double,
    val attackSeconds: Double,
    val releaseSeconds: Double,
)
