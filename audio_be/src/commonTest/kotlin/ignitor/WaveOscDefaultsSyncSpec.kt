/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.IgnitorDsl

/**
 * Guard (single-osc shape): the `IgnitorDsl` shape-field defaults for the single WaveIgnitor-backed
 * oscillators (in `audio_bridge`) are hand-copies of the `SAW_*` / `RAMP_*` / `PULSE_*` tuning constants
 * (in `audio_be/.../OscillatorTuning.kt`) — `audio_bridge` can't see the `internal` consts, so the two are
 * duplicated. This spec lives in `audio_be` (sees BOTH) and turns any silent drift into a red build.
 * Sibling of [SuperOscDefaultsSyncSpec] (which guards the unison `Super*` character defaults).
 */
class WaveOscDefaultsSyncSpec : StringSpec({

    "IgnitorDsl.Sawtooth shape defaults match the SAW_* engine constants" {
        val d = IgnitorDsl.Sawtooth()
        d.resetSamples shouldBe SAW_RESET_SAMPLES
        d.shapeMax shouldBe SAW_SHAPE_MAX
    }

    "IgnitorDsl.Ramp shape defaults match the RAMP_* engine constants" {
        val d = IgnitorDsl.Ramp()
        d.resetSamples shouldBe RAMP_RESET_SAMPLES
        d.shapeMax shouldBe RAMP_SHAPE_MAX
    }

    "IgnitorDsl.Pulze shape defaults match the PULSE_* engine constants" {
        val d = IgnitorDsl.Pulze()
        d.flankSamples shouldBe PULSE_MIN_FLANK_SAMPLES
        d.riseFlank shouldBe PULSE_RISE_FLANK
        d.fallFlank shouldBe PULSE_FALL_FLANK
    }
})
