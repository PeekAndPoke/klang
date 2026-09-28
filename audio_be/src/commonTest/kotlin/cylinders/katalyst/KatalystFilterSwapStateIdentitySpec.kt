/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.filters.AudioFilter

/**
 * The lifecycle of [KatalystFilterSwap] is a state machine whose transitions are POINTER SWAPS
 * between three objects created with the swap. The shape is `KatalystDelayStateIdentitySpec`'s.
 *
 * **What this spec guards is "the state machine adds no allocation"** (section 1 of
 * `docs/plans/effect-state-machines.md`), and only that. What the edges DO (the fade position a fade
 * starts from, the references a finished life drops, the cut and the instant install at Off) is
 * guarded in `KatalystFilterSwapSpec`.
 *
 * **What the main row drives:** every cell of the table on `KatalystFilterSwap.State` except the one
 * marked "(never)", plus a `process` in each state that ends nothing, which must be a self-edge,
 * and both "fresh" arms (the first initialisation). The swap is built the way its three hosts
 * build it, `KatalystFilterSwap(sampleRate)`, so the fade is the production `BANK_CROSSFADE_SECONDS`.
 * The seam is [KatalystFilterSwap.currentState].
 */
class KatalystFilterSwapStateIdentitySpec : StringSpec({

    val sampleRate = 44100.0
    val n = 128

    fun gain(g: Double) = object : AudioFilter {
        override fun process(buffer: AudioBuffer, offset: Int, length: Int) {
            for (i in offset until offset + length) {
                buffer[i] *= g
            }
        }
    }

    "every reachable cell of the table points at the three states created with the swap" {
        val swap = KatalystFilterSwap(sampleRate)
        val mix = StereoBuffer(n)

        // An IDENTITY collection, deliberately: a `Set` compares with `equals`, so a state written
        // one day as a `data class` would make two FRESH instances equal and keep this row green
        // while every transition allocated. Everything here compares with `===`.
        val seen = mutableListOf<Any>()

        fun see(state: Any) {
            if (seen.none { it === state }) {
                seen += state
            }
        }

        // 6.9 blocks at 44.1 kHz; this many blocks lands any fade.
        val land = 9

        // Off, as the hosts build it.
        val off = swap.currentState
        see(off)

        // Off + clear, Off + resume, Off + reset: self-edges.
        swap.clear()
        swap.resume(gain(1.0)) shouldBe false
        swap.reset()
        swap.currentState shouldBeSameInstanceAs off

        // Off -> Engaged while fresh: installed at once. Engaged + set while fresh: replaced, a self-edge.
        swap.set(gain(1.0), gain(1.0))
        val engaged = swap.currentState
        see(engaged)
        engaged shouldNotBeSameInstanceAs off
        swap.set(gain(0.75), gain(0.75))
        swap.currentState shouldBeSameInstanceAs engaged

        // Engaged + clear while fresh: Off at once.
        swap.clear()
        swap.currentState shouldBeSameInstanceAs off

        // Off + a block (the snap is spent): a self-edge.
        swap.process(mix, n)
        swap.currentState shouldBeSameInstanceAs off

        // Off -> Crossfading: a set fades in from dry.
        val first = gain(1.0)

        swap.set(first, gain(1.0))

        val crossfading = swap.currentState

        see(crossfading)
        withClue("Crossfading is its own state") {
            crossfading shouldNotBeSameInstanceAs engaged
            crossfading shouldNotBeSameInstanceAs off
        }

        // Crossfading + a block that ends nothing, + a REFUSED set, + a REFUSED clear, + resume of
        // the target: self-edges, every one of them.
        swap.process(mix, n)
        swap.currentState shouldBeSameInstanceAs crossfading
        swap.set(gain(0.5), gain(0.5))
        swap.currentState shouldBeSameInstanceAs crossfading
        swap.clear()
        swap.currentState shouldBeSameInstanceAs crossfading
        swap.resume(first) shouldBe true
        withClue("a refused change, a refused clear and a return mid-fade reuse the ONE Crossfading instance") {
            swap.currentState shouldBeSameInstanceAs crossfading
        }

        // Crossfading -> Engaged, by the fade landing on a pair.
        repeat(land) { swap.process(mix, n) }
        withClue("the fade's end lands in the ONE Engaged instance") {
            swap.currentState shouldBeSameInstanceAs engaged
        }

        // Engaged + a block, + resume of the pair in service: self-edges.
        swap.process(mix, n)
        swap.resume(first) shouldBe true
        swap.currentState shouldBeSameInstanceAs engaged

        // Engaged -> Crossfading by set; the RETURN to the pair that is now fading out is a
        // self-edge too, and then that turned-around fade lands back in Engaged.
        swap.set(gain(0.25), gain(0.25))
        swap.currentState shouldBeSameInstanceAs crossfading
        swap.resume(first) shouldBe true
        swap.currentState shouldBeSameInstanceAs crossfading
        repeat(land) { swap.process(mix, n) }
        swap.currentState shouldBeSameInstanceAs engaged

        // Engaged -> Crossfading by clear, then Crossfading -> Off by the fade landing on dry.
        swap.clear()
        swap.currentState shouldBeSameInstanceAs crossfading
        repeat(land) { swap.process(mix, n) }
        withClue("a fade to dry lands in the ONE Off instance") {
            swap.currentState shouldBeSameInstanceAs off
        }

        // Crossfading -> Off and Engaged -> Off by reset.
        swap.set(gain(1.0), gain(1.0))
        swap.currentState shouldBeSameInstanceAs crossfading
        swap.reset()
        withClue("reset mid-fade lands in the ONE Off instance") {
            swap.currentState shouldBeSameInstanceAs off
        }
        swap.set(gain(1.0), gain(1.0))
        swap.currentState shouldBeSameInstanceAs engaged
        swap.reset()
        swap.currentState shouldBeSameInstanceAs off

        // The summary: driving the table produced no fourth object. The clue prints CLASS names.
        withClue("a transition that allocates shows up here: ${seen.map { it::class.simpleName }}") {
            seen.size shouldBe 3
        }
    }
})
