/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_bridge.FilterDef

/**
 * Contract for the orbit-level body resonator: inactive until a body voice configures it, `null`
 * (a non-body voice) is a no-op that does NOT switch it off, and `reset()` deactivates it.
 */
class KatalystBodyEffectSpec : StringSpec({

    val sampleRate = 44100.0
    val n = 128

    fun contextWithConstantMix(value: Double): Pair<KatalystContext, StereoBuffer> {
        val mix = StereoBuffer(n)
        mix.left.fill(value)
        mix.right.fill(value)
        val ctx = KatalystContext(
            blockFrames = n,
            mixBuffer = mix,
            delaySendBuffer = StereoBuffer(n),
            reverbSendBuffer = StereoBuffer(n),
        )
        return ctx to mix
    }

    val woodish = FilterDef.Body(
        bands = listOf(FilterDef.Body.Mode(freq = 300.0, db = 6.0, q = 8.0)),
        mix = 1.0,
    )

    "inactive body is a no-op on the mix" {
        val (ctx, mix) = contextWithConstantMix(1.0)
        KatalystBodyEffect(sampleRate).process(ctx)
        mix.left[n - 1] shouldBe 1.0
        mix.right[n - 1] shouldBe 1.0
    }

    "a configured body colours the mix" {
        val (ctx, mix) = contextWithConstantMix(1.0)
        val fx = KatalystBodyEffect(sampleRate)
        fx.configure(woodish)
        fx.process(ctx)
        // A bandpass body on a DC step blends toward BODY_FLOOR·dry — the sample must have changed.
        (mix.left[n - 1] != 1.0) shouldBe true
    }

    "configure(null) leaves an already-configured body active (non-body voice must not switch it off)" {
        val (ctx, mix) = contextWithConstantMix(1.0)
        val fx = KatalystBodyEffect(sampleRate)
        fx.configure(woodish)
        fx.configure(null) // e.g. a non-body voice on the same orbit updates the cylinder
        fx.process(ctx)
        (mix.left[n - 1] != 1.0) shouldBe true
    }

    "reset() deactivates the body" {
        val (ctx, mix) = contextWithConstantMix(1.0)
        val fx = KatalystBodyEffect(sampleRate)
        fx.configure(woodish)
        fx.reset()
        fx.process(ctx)
        mix.left[n - 1] shouldBe 1.0
    }
})
