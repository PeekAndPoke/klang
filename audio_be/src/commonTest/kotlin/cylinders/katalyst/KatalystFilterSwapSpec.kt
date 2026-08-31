/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.filters.AudioFilter

/**
 * [KatalystFilterSwap] is the declick crossfade behind `body(...)` and `vowel(...)`: swapping a
 * resonant bank outright would jump from a mid-ring state to a zero state in one sample, so [set]
 * keeps the old pair alive and ramps old→new over [KatalystFilterSwap.fadeSeconds].
 *
 * `KatalystBodyEffectSpec`'s *"a live material change does not step the output"* already guards the
 * **start** of that fade, and does it well. What it cannot see is everything after: it inspects one
 * sample, and at 44100 Hz a 12 ms fade is **529 frames — 4.1 blocks of 128**.
 *
 * That gap has a silent failure mode. If the ramp never advanced (`t` stuck at 0), the swap boundary
 * would be *perfectly* continuous, because the output would simply still be the old bank — the
 * existing assertion passes. The damage would land 4 blocks later, when `fadePos` finally crosses
 * `fadeLen`, the old pair is dropped, and the output snaps to the new bank in one sample. A click,
 * moved to where nothing was looking.
 *
 * So this spec pins the ramp itself. The two "filters" are plain gains — old ×1.0, new ×0.0 — which
 * makes the expected output exactly `1 - t`, and every constant in the crossfade arithmetic
 * checkable against a number derived from the class's own definition rather than from a recorded run.
 *
 * Deliberately NOT a row here: "the fade end is continuous when the old pair is dropped". It was
 * written, and no mutation could kill it — the linear-ramp row already pins every sample to 1e-12,
 * including the two either side of that release, so it added a name and no kill power. Whether the
 * old pair is actually released is invisible from the output (a retained pair keeps blending at a
 * clamped t = 1 and sounds identical); it is a cost property, not a behaviour.
 */
class KatalystFilterSwapSpec : StringSpec({

    val sampleRate = 44100.0
    val fadeSeconds = 0.012
    val n = 128

    // KatalystFilterSwap computes this itself as (sampleRate * fadeSeconds).toInt() — recomputed
    // here from the same definition so the spec does not simply agree with whatever the class did.
    val fadeLen = (sampleRate * fadeSeconds).toInt() // 529

    /** A "filter" that multiplies by a constant, so the crossfade output is exactly predictable. */
    fun gain(g: Double) = object : AudioFilter {
        override fun process(buffer: AudioBuffer, offset: Int, length: Int) {
            for (i in offset until offset + length) {
                buffer[i] *= g
            }
        }
    }

    fun dcBlock(): StereoBuffer = StereoBuffer(n).apply {
        for (i in 0 until n) {
            left[i] = 1.0
            right[i] = 1.0
        }
    }

    /** Installs ×1.0, swaps to ×0.0, then renders [blocks] blocks of DC and concatenates the left channel. */
    fun rampAfterSwap(blocks: Int): DoubleArray {
        val swap = KatalystFilterSwap(sampleRate, fadeSeconds)
        swap.set(gain(1.0), gain(1.0))

        val settle = dcBlock()
        swap.process(settle, n)

        swap.set(gain(0.0), gain(0.0))

        val out = DoubleArray(blocks * n)
        for (b in 0 until blocks) {
            val mix = dcBlock()
            swap.process(mix, n)
            for (i in 0 until n) {
                out[b * n + i] = mix.left[i]
            }
        }

        return out
    }

    "the ramp is exactly linear from the old pair to the new one, across block boundaries" {
        val out = rampAfterSwap(blocks = 5)

        // out[k] = old*(1-t) + new*t with old = 1.0, new = 0.0  =>  1 - t, t = min(k/fadeLen, 1).
        // Sampling all five blocks is the point: fadePos accumulates ACROSS process() calls, so a
        // per-block reset would still look right inside block 0.
        for (k in 0 until 5 * n) {
            val t = minOf(k.toDouble() / fadeLen, 1.0)
            out[k] shouldBe (1.0 - t).plusOrMinus(1e-12)
        }
    }

    "the fade starts at the OLD pair — the swap sample is continuous" {
        val out = rampAfterSwap(blocks = 1)

        // t = 0 at the first sample after the swap, so the blend is entirely the old bank. This is
        // the property KatalystBodyEffectSpec measures as "no step"; pinned here as an exact value.
        out[0] shouldBe 1.0.plusOrMinus(1e-12)
    }

    "the fade COMPLETES — after fadeLen the output is the new pair alone" {
        val out = rampAfterSwap(blocks = 5)

        // 5 blocks = 640 frames > fadeLen = 529, so the tail is past the end of the ramp. A fade
        // that never advanced would sit at 1.0 here; a fade that overran would go negative.
        for (k in fadeLen until 5 * n) {
            out[k] shouldBe 0.0.plusOrMinus(1e-12)
        }
    }

    "with no pair installed the mix passes through untouched" {
        val swap = KatalystFilterSwap(sampleRate, fadeSeconds)
        val mix = dcBlock()

        swap.process(mix, n)

        swap.active shouldBe false
        (0 until n).all { mix.left[it] == 1.0 && mix.right[it] == 1.0 } shouldBe true
    }

    "clear() drops the pair and stops filtering" {
        val swap = KatalystFilterSwap(sampleRate, fadeSeconds)
        swap.set(gain(0.0), gain(0.0))
        swap.active shouldBe true

        swap.clear()
        swap.active shouldBe false

        val mix = dcBlock()
        swap.process(mix, n)

        // Still 1.0: with the pair dropped, the silencing gain is no longer in the path at all.
        (0 until n).all { mix.left[it] == 1.0 } shouldBe true
    }
})
