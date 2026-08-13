/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.master

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.MasterStage
import io.peekandpoke.klang.audio_be.effects.DelayLine
import io.peekandpoke.klang.audio_be.effects.Reverb
import io.peekandpoke.klang.audio_bridge.MasterDsl
import io.peekandpoke.klang.audio_bridge.MasterStageDsl
import io.peekandpoke.klang.audio_bridge.constants.AUTHORED_LIMITER_ATTACK_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.AUTHORED_LIMITER_LOOKAHEAD_SECONDS

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

    // NOTE (2026-08-11): there used to be a "shares the house CHARACTER" test here, asserting
    // `MasterStageDsl.Limiter().thresholdDb shouldBe MasterStage.LIMITER_THRESHOLD_DB` and three
    // siblings. Threshold/ratio/knee/release now have ONE declaration in audio_bridge that both
    // sides read, so comparing the two sides became `X shouldBe X` — a tautology that still LOOKS
    // like a guard, which is worse than no test.
    //
    // What replaced it is NOT nothing: the values are pinned against literals below. Sharing one
    // declaration removes the drift-apart failure mode, but not the change-it-anyway one.

    "the opt-in Limiter's defaults are pinned, and its TIMING deliberately differs from the house" {
        // The house limiter runs once on the summed mix, so its lookahead delays everything
        // uniformly and nothing can desync. The authored one runs per playback, where the same
        // delay WOULD desync it against other playbacks. So the two legitimately diverge, and the
        // relation at the bottom of this test is where that intent is written down.
        //
        // Pinned on the WIRE MODEL, not on the constants. Asserting `AUTHORED_LIMITER_ATTACK_SECONDS
        // shouldBe 0.001` would be strictly weaker: while the reference stands it duplicates the
        // line below, and once someone replaces that reference in MasterDsl with a literal — the
        // one edit that actually hurts, plausibly 0.005 copied from the house limiter while
        // "unifying" the two — it pins a value nothing reads any more. Reading through
        // `MasterStageDsl.Limiter()` catches exactly that case.
        //
        // Every field, not only the timing pair: "enforced by construction" holds exactly as long
        // as the reference survives, which is the assumption this test exists to distrust. kneeDb
        // is the sharpest example — the 2 dB soft knee is a non-obvious 2026-04-30 anti-britzel
        // fix, and a literal 0.0 there would put the C1 corner back on every authored limiter.
        val limiter = MasterStageDsl.Limiter()
        limiter.thresholdDb shouldBe -1.0
        limiter.ratio shouldBe 20.0
        limiter.kneeDb shouldBe 2.0
        limiter.releaseSeconds shouldBe 0.1
        limiter.attackSeconds shouldBe 0.001
        limiter.lookaheadSeconds shouldBe 0.0

        // The divergence itself, as a relation rather than four loose literals.
        AUTHORED_LIMITER_LOOKAHEAD_SECONDS shouldBeLessThan MasterStage.HOUSE_LIMITER_LOOKAHEAD_SECONDS
        AUTHORED_LIMITER_ATTACK_SECONDS shouldBeLessThan MasterStage.HOUSE_LIMITER_ATTACK_SECONDS
    }

    "the house limiter smooths across its whole lookahead window" {
        // Peak performance is invariant to the smoothing length (the min-hold does the
        // anticipating), while LF cleanliness tracks it — so at a fixed latency, maximum smoothing
        // is the right default. This encodes that intent as a relation rather than two loose numbers.
        MasterStage.HOUSE_LIMITER_ATTACK_SECONDS shouldBe MasterStage.HOUSE_LIMITER_LOOKAHEAD_SECONDS
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
