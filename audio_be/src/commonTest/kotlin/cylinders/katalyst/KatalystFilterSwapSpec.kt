/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.filters.AudioFilter

/**
 * [KatalystFilterSwap] is the declick crossfade behind `body(...)` and `vowel(...)`: swapping a
 * resonant bank outright would jump from a mid-ring state to a zero state in one sample, so [set]
 * keeps the old pair alive and ramps old→new over `fadeSeconds`.
 *
 * `KatalystBodyEffectSpec`'s *"a live material change does not step the output"* already guards the
 * **start** of that fade, and does it well. What it cannot see is everything after: it inspects one
 * sample, and at 44100 Hz a 12 ms fade is **529 frames — 4.1 blocks of 128**.
 *
 * That gap has a silent failure mode. If the ramp never advanced (`t` stuck at 0), the swap boundary
 * would be *perfectly* continuous, because the output would simply still be the old bank — the
 * existing assertion passes. The damage would land 4 blocks later, when the fade position finally crosses
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
 * clamped t = 1 and sounds identical), so the references row below asks the swap through its
 * `holds` seam instead.
 *
 * The last four rows are the answers of Katalyst step 5c-4 (the lifecycle as a state machine) to
 * the four questions of `docs/plans/effect-state-machines.md`, one row each.
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
        // Sampling all five blocks is the point: the fade position accumulates ACROSS process() calls, so a
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

    /**
     * A "filter" that ignores its input and counts the samples it has processed, from [start], so a
     * pair's own life is visible. Each channel gets its own start, so a crossed L/R wiring shows.
     */
    class Clock(start: Int) : AudioFilter {
        private var count = start

        override fun process(buffer: AudioBuffer, offset: Int, length: Int) {
            for (i in offset until offset + length) {
                buffer[i] = count.toDouble()
                count++
            }
        }
    }

    "a pair's own state runs on across every transition: in service, outgoing, and alone again" {
        // Question 1, what outlives the states: the pairs. The swap never builds, resets or re-runs
        // one; it only moves a reference from "in service" to "outgoing". A clock shows it: each
        // pair must see every sample of its life exactly once, whatever state the swap is in.
        val swap = KatalystFilterSwap(sampleRate, fadeSeconds)

        // Distinct right-channel starts, so a right pair run twice, skipped or wired to the left shows.
        val aRight = 5000
        val bRight = 7000
        val cRight = 9000

        swap.set(Clock(0), Clock(aRight))
        swap.process(dcBlock(), n)

        swap.set(Clock(0), Clock(bRight))

        // A was in service for one block, so it enters the fade at 128; B starts at its own start.
        for (block in 0 until 5) {
            val mix = dcBlock()
            swap.process(mix, n)

            for (i in 0 until n) {
                val kk = block * n + i
                val t = minOf(kk.toDouble() / fadeLen, 1.0)
                val left = if (kk < fadeLen) (n + kk) * (1.0 - t) + kk * t else kk.toDouble()
                val right = if (kk < fadeLen) {
                    (aRight + n + kk) * (1.0 - t) + (bRight + kk) * t
                } else {
                    (bRight + kk).toDouble()
                }

                withClue("A to B, left sample $kk") { mix.left[i] shouldBe left.plusOrMinus(1e-9) }
                withClue("A to B, right sample $kk") { mix.right[i] shouldBe right.plusOrMinus(1e-9) }
            }
        }

        // B, in service for 5 blocks, becomes the outgoing pair and runs on from 640.
        swap.set(Clock(0), Clock(cRight))

        for (block in 0 until 5) {
            val mix = dcBlock()
            swap.process(mix, n)

            for (i in 0 until n) {
                val kk = block * n + i
                val t = minOf(kk.toDouble() / fadeLen, 1.0)
                val left = if (kk < fadeLen) (5 * n + kk) * (1.0 - t) + kk * t else kk.toDouble()
                val right = if (kk < fadeLen) {
                    (bRight + 5 * n + kk) * (1.0 - t) + (cRight + kk) * t
                } else {
                    (cRight + kk).toDouble()
                }

                withClue("B to C, left sample $kk") { mix.left[i] shouldBe left.plusOrMinus(1e-9) }
                withClue("B to C, right sample $kk") { mix.right[i] shouldBe right.plusOrMinus(1e-9) }
            }
        }
    }

    "every fade starts at t = 0: a restart mid-fade, and the first fade of a life after clear()" {
        // Question 2, what record of a finished life is forgotten: the fade position. It belongs to
        // Crossfading and `enter` is its only initialiser, so neither a restart nor a new life can
        // start a fade where an earlier one stopped.
        fun ramp(swap: KatalystFilterSwap, from: Double, label: String) {
            for (block in 0 until 5) {
                val mix = dcBlock()
                swap.process(mix, n)

                for (i in 0 until n) {
                    val k = block * n + i
                    val t = minOf(k.toDouble() / fadeLen, 1.0)

                    withClue("$label, sample $k") { mix.left[i] shouldBe (from * (1.0 - t)).plusOrMinus(1e-12) }
                }
            }
        }

        // A restart two blocks into a fade: x2 is in service, so the new fade runs from 2 to 0.
        val restart = KatalystFilterSwap(sampleRate, fadeSeconds)
        restart.set(gain(1.0), gain(1.0))
        restart.process(dcBlock(), n)
        restart.set(gain(2.0), gain(2.0))
        restart.process(dcBlock(), n)
        restart.process(dcBlock(), n)
        restart.set(gain(0.0), gain(0.0))
        ramp(restart, from = 2.0, label = "restart")

        // clear() two blocks into a fade, then a new life: installed at once, then its first fade.
        val relive = KatalystFilterSwap(sampleRate, fadeSeconds)
        relive.set(gain(1.0), gain(1.0))
        relive.process(dcBlock(), n)
        relive.set(gain(2.0), gain(2.0))
        relive.process(dcBlock(), n)
        relive.process(dcBlock(), n)
        relive.clear()
        relive.set(gain(3.0), gain(3.0))
        relive.process(dcBlock(), n)
        relive.set(gain(0.0), gain(0.0))
        ramp(relive, from = 3.0, label = "new life")
    }

    "Off is a pass-through from every way in, and the first pair after it is installed at once" {
        // Question 3, the Off precondition. TODAY there is none: entering Off from a sounding pair
        // is a hard cut to dry, and leaving it installs without a fade. That is the click the
        // decided switch-off fade removes (`docs/tasks/katalyst-dsl.md`), and this row turns red
        // with that sound change, on purpose: it pins today's lifecycle, not the future one.
        fun fromOff(swap: KatalystFilterSwap, label: String) {
            val dry = dcBlock()
            swap.process(dry, n)

            withClue("$label: Off leaves the mix untouched") {
                (0 until n).all { dry.left[it] == 1.0 && dry.right[it] == 1.0 } shouldBe true
            }

            swap.set(gain(0.25), gain(0.25))
            val on = dcBlock()
            swap.process(on, n)

            withClue("$label: the first pair after Off is alone from its first sample") {
                (0 until n).all { on.left[it] == 0.25 && on.right[it] == 0.25 } shouldBe true
            }
        }

        val fromEngaged = KatalystFilterSwap(sampleRate, fadeSeconds)
        fromEngaged.set(gain(0.0), gain(0.0))
        fromEngaged.process(dcBlock(), n)
        fromEngaged.clear()
        fromOff(fromEngaged, "clear from Engaged")

        val fromCrossfading = KatalystFilterSwap(sampleRate, fadeSeconds)
        fromCrossfading.set(gain(0.0), gain(0.0))
        fromCrossfading.process(dcBlock(), n)
        fromCrossfading.set(gain(0.5), gain(0.5))
        fromCrossfading.process(dcBlock(), n)
        fromCrossfading.process(dcBlock(), n)
        fromCrossfading.clear()
        fromOff(fromCrossfading, "clear mid-fade")
    }

    "a finished fade, a restart and a clear() leave no reference to a dead pair" {
        // Question 4, the REFERENCES. The outgoing pair dies with Crossfading, so each event that
        // leaves that state drops it: the fade's end, a restart (which drops the OLDEST pair) and
        // clear(). The pair in service is dropped on entering Off. A retained pair is inaudible (see
        // the class KDoc), so the swap is asked directly through its `holds` seam; the positive
        // controls show the seam does see a live reference.
        val swap = KatalystFilterSwap(sampleRate, fadeSeconds)
        val aL = gain(1.0)
        val aR = gain(1.0)
        val bL = gain(0.5)
        val bR = gain(0.5)

        swap.set(aL, aR)
        swap.process(dcBlock(), n)
        swap.set(bL, bR)
        swap.process(dcBlock(), n)

        withClue("mid-fade the outgoing pair is held") {
            swap.holds(aL) shouldBe true
            swap.holds(aR) shouldBe true
        }

        repeat(4) { swap.process(dcBlock(), n) }

        withClue("after the fade's end the outgoing pair is dropped, the pair in service kept") {
            swap.holds(aL) shouldBe false
            swap.holds(aR) shouldBe false
            swap.holds(bL) shouldBe true
            swap.holds(bR) shouldBe true
        }

        // A restart mid-fade drops the oldest pair: B is outgoing, C comes in, D restarts from C.
        val cL = gain(0.25)
        val cR = gain(0.25)
        val dL = gain(0.0)
        val dR = gain(0.0)

        swap.set(cL, cR)
        swap.process(dcBlock(), n)
        swap.set(dL, dR)

        withClue("a restart drops the oldest pair and keeps the one it fades from") {
            swap.holds(bL) shouldBe false
            swap.holds(bR) shouldBe false
            swap.holds(cL) shouldBe true
            swap.holds(cR) shouldBe true
        }

        swap.process(dcBlock(), n)
        swap.clear()

        withClue("clear() mid-fade drops both pairs") {
            listOf(cL, cR, dL, dR).none { swap.holds(it) } shouldBe true
        }
    }
})
