/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.IgnitorDsl

/**
 * Guard (noise generators): the `IgnitorDsl` noise-knob default `Param` values (in `audio_bridge`) are
 * hand-copies of the noise tuning constants (in `audio_be/.../OscillatorTuning.kt`) — `audio_bridge`
 * can't see the `internal` consts, so the two are duplicated. This spec lives in `audio_be` (sees BOTH)
 * and turns any silent drift into a red build. Sibling of [WaveOscDefaultsSyncSpec].
 */
class NoiseDefaultsSyncSpec : StringSpec({

    "IgnitorDsl.Crackle chaos default matches the CRACKLE_CHAOS_DEFAULT engine constant" {
        val chaos = IgnitorDsl.Crackle().chaos
        (chaos as IgnitorDsl.Param).default shouldBe CRACKLE_CHAOS_DEFAULT
    }

    "IgnitorDsl.WhiteNoise color default matches the NOISE_TILT_DEFAULT engine constant" {
        val color = IgnitorDsl.WhiteNoise().color
        (color as IgnitorDsl.Param).default shouldBe NOISE_TILT_DEFAULT
    }

    "IgnitorDsl.BrownNoise depth default matches the BROWN_LEAK_DEFAULT engine constant" {
        val depth = IgnitorDsl.BrownNoise().depth
        (depth as IgnitorDsl.Param).default shouldBe BROWN_LEAK_DEFAULT
    }

    "IgnitorDsl.Dust tail/bipolar defaults match the DUST_* engine constants" {
        val dust = IgnitorDsl.Dust()
        (dust.tail as IgnitorDsl.Param).default shouldBe DUST_TAIL_DEFAULT
        (dust.bipolar as IgnitorDsl.Param).default shouldBe DUST_BIPOLAR_DEFAULT
    }
})
