/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.IgnitorDsl

/**
 * Guard: the `IgnitorDsl.SuperSaw` character-field defaults (in `audio_bridge`) are hand-copies of the
 * `SUPERSAW_*` tuning constants (in `audio_be/.../OscillatorTuning.kt`) — `audio_bridge` can't see the
 * `internal` consts, so the two are duplicated with a "keep in sync" comment. This spec lives in `audio_be`
 * (which can see BOTH) and turns any silent drift between them into a red build. If you tune a `SUPERSAW_*`
 * const by ear, update the matching `IgnitorDsl.SuperSaw` default too.
 */
class SuperSawDefaultsSyncSpec : StringSpec({

    "IgnitorDsl.SuperSaw character defaults match the SUPERSAW_* engine constants" {
        val d = IgnitorDsl.SuperSaw()
        d.spreadPower shouldBe SUPERSAW_SPREAD_POWER
        d.sideAtten shouldBe SUPERSAW_SIDE_ATTEN
        d.gainJitter shouldBe SUPERSAW_GAIN_JITTER
        d.centerJitterScale shouldBe SUPERSAW_CENTER_JITTER_SCALE
    }
})
