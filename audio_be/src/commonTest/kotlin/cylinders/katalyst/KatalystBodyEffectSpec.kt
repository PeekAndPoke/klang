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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Contract for the orbit-level body resonator: inactive until configured; since only the OWNER voice
 * configures it (via VoiceLease), `null` (owner has no body) turns it off; and `reset()` deactivates it.
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

    "configure(null) turns the body off (only the owner configures now, so null = owner has no body)" {
        val (ctx, mix) = contextWithConstantMix(1.0)
        val fx = KatalystBodyEffect(sampleRate)
        fx.configure(woodish)
        fx.configure(null) // the owning voice has no body → resonator off
        fx.process(ctx)
        mix.left[n - 1] shouldBe 1.0 // mix untouched — body is off
    }

    "reset() deactivates the body" {
        val (ctx, mix) = contextWithConstantMix(1.0)
        val fx = KatalystBodyEffect(sampleRate)
        fx.configure(woodish)
        fx.reset()
        fx.process(ctx)
        mix.left[n - 1] shouldBe 1.0
    }

    "body floor is honored — a lower floor passes less dry (guards bodyFloor() plumbing)" {
        fun outAt(floor: Double): Double {
            val (ctx, mix) = contextWithConstantMix(1.0)
            KatalystBodyEffect(sampleRate).apply { configure(woodish.copy(floor = floor)) }.process(ctx)
            return mix.left[n - 1]
        }
        // At mix=1 the dry is held at `floor`; the wet is floor-independent, so it cancels. If the
        // floor were ignored (the KatalystFormantEffect bug), these would be equal.
        outAt(0.2) shouldBeLessThan outAt(0.8)
    }

    "a live material change does not step the output (declick crossfade)" {
        val bodyA = FilterDef.Body(bands = listOf(FilterDef.Body.Mode(120.0, 9.0, 12.0)), mix = 1.0)
        val bodyB = FilterDef.Body(bands = listOf(FilterDef.Body.Mode(320.0, 9.0, 12.0)), mix = 1.0)
        val fx = KatalystBodyEffect(sampleRate)
        val freq = 110.0 // near bodyA's mode → a strong ring to swap out of

        var phase = 0
        fun runSineBlock(): DoubleArray {
            val (ctx, mix) = contextWithConstantMix(0.0)
            for (i in 0 until n) {
                val s = sin(2.0 * PI * freq * (phase + i) / sampleRate)
                mix.left[i] = s
                mix.right[i] = s
            }
            fx.process(ctx)
            phase += n
            return DoubleArray(n) { mix.left[it] }
        }

        fx.configure(bodyA)
        var block = DoubleArray(n)
        repeat(12) { block = runSineBlock() } // let the ring settle on bodyA

        val lastA = block[n - 1]
        val naturalStep = (1 until n).maxOf { abs(block[it] - block[it - 1]) }

        fx.configure(bodyB) // live material swap while the orbit is ringing
        val boundaryStep = abs(runSineBlock()[0] - lastA)

        // Continuous: the swap-boundary jump is within a few natural per-sample steps. A bare
        // filter-instance swap would jump by the whole ring amplitude (≫ naturalStep) → a click.
        boundaryStep shouldBeLessThan (naturalStep * 4.0)
    }
})
