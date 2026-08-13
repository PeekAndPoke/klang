/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_bridge.FilterDef

/**
 * Contract for the orbit-level vowel/formant resonator — the twin of [KatalystBodyEffectSpec].
 * Exists because the formant effect diverged from the body effect (it was missing the `floor`
 * plumbing that `vowelFloor()` relies on); the floor test below guards that parity.
 */
class KatalystFormantEffectSpec : StringSpec({

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

    val vowelish = FilterDef.Formant(
        bands = listOf(FilterDef.Formant.Band(freq = 700.0, db = 0.0, q = 10.0)),
        mix = 1.0,
    )

    "inactive vowel is a no-op on the mix" {
        val (ctx, mix) = contextWithConstantMix(1.0)
        KatalystFormantEffect(sampleRate).process(ctx)
        mix.left[n - 1] shouldBe 1.0
        mix.right[n - 1] shouldBe 1.0
    }

    "a configured vowel colours the mix" {
        val (ctx, mix) = contextWithConstantMix(1.0)
        KatalystFormantEffect(sampleRate).apply { configure(vowelish) }.process(ctx)
        (mix.left[n - 1] != 1.0) shouldBe true
    }

    "configure(null) turns the vowel off" {
        val (ctx, mix) = contextWithConstantMix(1.0)
        val fx = KatalystFormantEffect(sampleRate)
        fx.configure(vowelish)
        fx.configure(null)
        fx.process(ctx)
        mix.left[n - 1] shouldBe 1.0
    }

    "vowel floor is honored — a lower floor passes less dry (guards vowelFloor() plumbing)" {
        fun outAt(floor: Double): Double {
            val (ctx, mix) = contextWithConstantMix(1.0)
            KatalystFormantEffect(sampleRate).apply { configure(vowelish.copy(floor = floor)) }.process(ctx)
            return mix.left[n - 1]
        }
        // At mix=1 the dry is held at `floor`; the wet is floor-independent, so it cancels. This is the
        // exact test that was missing when the formant effect dropped the floor arg.
        outAt(0.2) shouldBeLessThan outAt(0.8)
    }
})
