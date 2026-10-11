/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

/**
 * What a declared compressor stage is configured with: the five knobs, every one resolved to a
 * number. [KatalystCompressorEffect.configure] compares the VALUES with the ones it applied last, so
 * an unchanged owner costs five compares per block and writes nothing.
 *
 * **Mutable and reused, on purpose** (katalyst knob expressions step 2, 2026-10-11): [KatalystCompressorWriter]
 * owns one and refills it in place at every resolve. It built a new one per resolve before, which a
 * knob resolved every block would turn into an allocation per block. The stage reads the values and
 * keeps no reference.
 *
 * Lived under `Voice` until 2026-10-07 (audit A1.7), though only the chain ever used it.
 */
class CompressorSettings(
    var thresholdDb: Double = Double.NaN,
    var ratio: Double = Double.NaN,
    var kneeDb: Double = Double.NaN,
    var attackSeconds: Double = Double.NaN,
    var releaseSeconds: Double = Double.NaN,
)
