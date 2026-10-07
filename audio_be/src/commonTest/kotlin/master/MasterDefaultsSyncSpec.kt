/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.master

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.MasterStage
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystRegistry
import io.peekandpoke.klang.audio_be.effects.Reverb
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import io.peekandpoke.klang.audio_bridge.constants.AUTHORED_LIMITER_ATTACK_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.AUTHORED_LIMITER_LOOKAHEAD_SECONDS

/**
 * Keeps the output's defaults in sync with the engine, the `*DefaultsSyncSpec` family.
 *
 * Load-bearing for "a song without `master(…)` sounds exactly like it did before the master DSL
 * existed". Since phase 3 step 12 C5 the output runs a Katalyst chain, and the authored limiter is
 * the Katalyst `limiter(...)` door, whose numbers are pinned from literals by
 * `KlangScriptKatalystDoorParitySpec` (the `limiter` row of its door table).
 */
class MasterDefaultsSyncSpec : StringSpec({

    "the output's default is the EMPTY chain: the final safety chain is the only thing on the mix" {
        // If the default ever gains a stage, every existing song changes: the safety limiter in
        // MasterStage still runs on the summed mix, so a default limiter here would put two
        // limiters in series, and any stage at all would take the engine off its fast path.
        val bus = MasterBus(sampleRate = 44100, blockFrames = 128, registry = KatalystRegistry())

        bus.isActive shouldBe false

        // ...and `master(Katalyst())`, the empty chain, is the way back to it, mid-song.
        val registry = KatalystRegistry().apply {
            register("loud", KatalystDsl.of(KatalystStageDsl.Gain(gain = IgnitorDsl.Constant(2.0))))
            register("off", KatalystDsl(emptyList()))
        }
        val switchedOff = MasterBus(sampleRate = 44100, blockFrames = 128, registry = registry)
        switchedOff.requestSwap("loud")
        switchedOff.process(StereoBuffer(128), 128)
        switchedOff.markRendered()
        switchedOff.isActive shouldBe true

        switchedOff.requestSwap("off")
        repeat(40) { // past the 60 ms crossfade (21 blocks at 44.1 kHz)
            switchedOff.process(StereoBuffer(128), 128)
        }

        switchedOff.isActive shouldBe false
    }

    // NOTE (2026-08-11): there used to be a "shares the house CHARACTER" test here, asserting
    // `MasterStageDsl.Limiter().thresholdDb shouldBe MasterStage.LIMITER_THRESHOLD_DB` and three
    // siblings. Threshold/ratio/knee/release now have ONE declaration in audio_bridge that both
    // sides read, so comparing the two sides became `X shouldBe X`, a tautology that still LOOKS
    // like a guard, which is worse than no test. The authored limiter's values are pinned against
    // literals where its door is (see the class KDoc).

    "the authored limiter's TIMING deliberately differs from the house" {
        // The house limiter runs once on the summed mix, so its lookahead delays everything
        // uniformly and nothing can desync. The authored one runs on one playback's output or one
        // orbit, where the same delay WOULD desync it against the rest. So the two legitimately
        // diverge, and this relation is where that intent is written down.
        AUTHORED_LIMITER_LOOKAHEAD_SECONDS shouldBeLessThan MasterStage.HOUSE_LIMITER_LOOKAHEAD_SECONDS
        AUTHORED_LIMITER_ATTACK_SECONDS shouldBeLessThan MasterStage.HOUSE_LIMITER_ATTACK_SECONDS
    }

    "the house limiter smooths across its whole lookahead window" {
        // Peak performance is invariant to the smoothing length (the min-hold does the
        // anticipating), while LF cleanliness tracks it, so at a fixed latency, maximum smoothing
        // is the right default. This encodes that intent as a relation rather than two loose numbers.
        MasterStage.HOUSE_LIMITER_ATTACK_SECONDS shouldBe MasterStage.HOUSE_LIMITER_LOOKAHEAD_SECONDS
    }

    // NOTE (2026-09-16): the reverb size and delay cap sync tests went the way of the limiter's: both
    // sides read one constant now (`constants/BusEffectDefaults.kt`), so they compared a value with itself. Drift
    // between the surfaces is guarded by `KatalystDefaultsSyncSpec`; the output runs the orbit's
    // stages since step 12 C3 and the orbit's chain type since C5.

    "the reverb lowpass defaults to absent on the stage and the DSP: the fixed default damping applies" {
        KatalystStageDsl.Reverb().lowpass shouldBe null
        Reverb(44100).lowpass shouldBe null
    }
})
