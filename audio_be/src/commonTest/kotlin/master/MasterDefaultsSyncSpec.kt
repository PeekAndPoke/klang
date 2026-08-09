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

    "the opt-in Limiter stage shares the house CHARACTER — threshold, ratio, knee, release" {
        val limiter = MasterStageDsl.Limiter()

        limiter.thresholdDb shouldBe MasterStage.LIMITER_THRESHOLD_DB
        limiter.ratio shouldBe MasterStage.LIMITER_RATIO
        limiter.kneeDb shouldBe MasterStage.LIMITER_KNEE_DB
        limiter.releaseSeconds shouldBe MasterStage.LIMITER_RELEASE_SECONDS
    }

    "the opt-in Limiter's TIMING deliberately differs — and the difference is asserted, not assumed" {
        // The house limiter runs once on the summed mix, so its lookahead delays everything
        // uniformly and nothing can desync. The authored one runs per playback, where the same
        // delay WOULD desync it against other playbacks. So the two legitimately diverge, and this
        // is where that is written down — a `shouldNotBe` would prove nothing.
        MasterStageDsl.Limiter().attackSeconds shouldBe MasterStage.AUTHORED_LIMITER_ATTACK_SECONDS

        // ...and the authored default really is "no added latency", on the wire model itself.
        MasterStageDsl.Limiter().lookaheadSeconds shouldBe MasterStage.AUTHORED_LIMITER_LOOKAHEAD_SECONDS
        MasterStage.AUTHORED_LIMITER_LOOKAHEAD_SECONDS shouldBe 0.0
    }

    "the house limiter smooths across its whole lookahead window" {
        // Peak performance is invariant to the smoothing length (the min-hold does the
        // anticipating), while LF cleanliness tracks it — so at a fixed latency, maximum smoothing
        // is the right default. This encodes that intent as a relation rather than two loose numbers.
        MasterStage.LIMITER_ATTACK_SECONDS shouldBe MasterStage.LIMITER_LOOKAHEAD_SECONDS
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
