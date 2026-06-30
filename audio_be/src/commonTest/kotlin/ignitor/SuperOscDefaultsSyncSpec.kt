/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.IgnitorDsl

/**
 * Guard: the `IgnitorDsl.Super*` character-field defaults (in `audio_bridge`) are hand-copies of the
 * `SUPER*_*` tuning constants (in `audio_be/.../OscillatorTuning.kt`) — `audio_bridge` can't see the
 * `internal` consts, so the two are duplicated with a "keep in sync" comment. This spec lives in `audio_be`
 * (which can see BOTH) and turns any silent drift between them into a red build. If you tune a `SUPER*_*`
 * const by ear, update the matching `IgnitorDsl.Super*` default too.
 */
class SuperOscDefaultsSyncSpec : StringSpec({

    "IgnitorDsl.SuperSaw character defaults match the SUPERSAW_* engine constants" {
        val d = IgnitorDsl.SuperSaw()
        d.spreadPower shouldBe SUPERSAW_SPREAD_POWER
        d.sideAtten shouldBe SUPERSAW_SIDE_ATTEN
        d.gainJitter shouldBe SUPERSAW_GAIN_JITTER
        d.centerJitterScale shouldBe SUPERSAW_CENTER_JITTER_SCALE
    }

    "IgnitorDsl.SuperRamp character defaults match the SUPERRAMP_* engine constants" {
        val d = IgnitorDsl.SuperRamp()
        d.spreadPower shouldBe SUPERRAMP_SPREAD_POWER
        d.sideAtten shouldBe SUPERRAMP_SIDE_ATTEN
        d.gainJitter shouldBe SUPERRAMP_GAIN_JITTER
        d.centerJitterScale shouldBe SUPERRAMP_CENTER_JITTER_SCALE
    }

    "IgnitorDsl.SuperSine character defaults match the SUPERSINE_* engine constants" {
        val d = IgnitorDsl.SuperSine()
        d.spreadPower shouldBe SUPERSINE_SPREAD_POWER
        d.sideAtten shouldBe SUPERSINE_SIDE_ATTEN
        d.gainJitter shouldBe SUPERSINE_GAIN_JITTER
        d.centerJitterScale shouldBe SUPERSINE_CENTER_JITTER_SCALE
    }

    "IgnitorDsl.SuperSquare character defaults match the SUPERSQUARE_* engine constants" {
        val d = IgnitorDsl.SuperSquare()
        d.spreadPower shouldBe SUPERSQUARE_SPREAD_POWER
        d.sideAtten shouldBe SUPERSQUARE_SIDE_ATTEN
        d.gainJitter shouldBe SUPERSQUARE_GAIN_JITTER
        d.centerJitterScale shouldBe SUPERSQUARE_CENTER_JITTER_SCALE
    }

    "IgnitorDsl.SuperTri character defaults match the SUPERTRI_* engine constants" {
        val d = IgnitorDsl.SuperTri()
        d.spreadPower shouldBe SUPERTRI_SPREAD_POWER
        d.sideAtten shouldBe SUPERTRI_SIDE_ATTEN
        d.gainJitter shouldBe SUPERTRI_GAIN_JITTER
        d.centerJitterScale shouldBe SUPERTRI_CENTER_JITTER_SCALE
    }
})
