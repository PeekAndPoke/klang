/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.master

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.MasterStage
import io.peekandpoke.klang.audio_be.effects.DelayLine
import io.peekandpoke.klang.audio_be.effects.Reverb
import io.peekandpoke.klang.audio_bridge.MasterDsl
import io.peekandpoke.klang.audio_bridge.MasterStageDsl

/**
 * Keeps the authoring-side master defaults in sync with the engine — the `*DefaultsSyncSpec` family.
 *
 * Two independent guarantees, both load-bearing for "a song without `master(…)` sounds exactly like
 * it did before the master DSL existed".
 */
class MasterDefaultsSyncSpec : StringSpec({

    "MasterDsl.default is EMPTY — the final safety chain is the only thing on the mix" {
        // If this ever gains a stage, every existing song changes: the safety limiter in
        // MasterStage still runs on the summed mix, so a default limiter here would put two
        // limiters in series. See MasterDsl.default's KDoc.
        MasterDsl.default.stages shouldBe emptyList()
    }

    "the opt-in Limiter stage defaults match the house safety limiter" {
        val limiter = MasterStageDsl.Limiter()

        limiter.thresholdDb shouldBe MasterStage.LIMITER_THRESHOLD_DB
        limiter.ratio shouldBe MasterStage.LIMITER_RATIO
        limiter.kneeDb shouldBe MasterStage.LIMITER_KNEE_DB
        limiter.attackSeconds shouldBe MasterStage.LIMITER_ATTACK_SECONDS
        limiter.releaseSeconds shouldBe MasterStage.LIMITER_RELEASE_SECONDS
    }

    "the master reverb default is the authored twin of the Freeverb default" {
        // 5.0 authored / 10 == 0.5, the DSP's own default — so changing the literal from 0.5 to 5.0
        // was behaviour-preserving. If either side moves without the other, the master's default
        // reverb silently changes length.
        Reverb.normalizeRoomSize(MasterStageDsl.Reverb().roomSize) shouldBe Reverb(44100).roomSize
        MasterStageDsl.Reverb().damp shouldBe Reverb(44100).damp
    }

    "the feedback ceilings default to the DSP's own" {
        MasterStageDsl.Delay().cap shouldBe DelayLine(maxDelaySeconds = 1.0, sampleRate = 44100).feedbackCap
    }

    "the new reverb overrides default to absent" {
        MasterStageDsl.Reverb().roomFade shouldBe null
        MasterStageDsl.Reverb().roomLp shouldBe null
    }
})
