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
 * **What the main row drives:** every cell of the table on `KatalystFilterSwap.State` except the two
 * marked "(never)", plus a `process` in each state that ends nothing, which must be a self-edge. The
 * swap is built the way its three hosts build it, `KatalystFilterSwap(sampleRate)`, so the fade is
 * the production 12 ms. The seam is [KatalystFilterSwap.currentState].
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

        // Off, as the hosts build it.
        val off = swap.currentState
        see(off)

        // Off + clear, and Off + a block: self-edges.
        swap.clear()
        swap.process(mix, n)
        withClue("clear and process while Off are self-edges") {
            swap.currentState shouldBeSameInstanceAs off
        }

        // Off -> Engaged: the first pair is installed at once.
        swap.set(gain(1.0), gain(1.0))
        val engaged = swap.currentState
        see(engaged)
        engaged shouldNotBeSameInstanceAs off

        // Engaged + a block: a self-edge.
        swap.process(mix, n)
        swap.currentState shouldBeSameInstanceAs engaged

        // Engaged -> Crossfading.
        swap.set(gain(0.5), gain(0.5))
        val crossfading = swap.currentState
        see(crossfading)
        withClue("Crossfading is its own state") {
            crossfading shouldNotBeSameInstanceAs engaged
            crossfading shouldNotBeSameInstanceAs off
        }

        // Crossfading + a block that does not end the fade: a self-edge (128 of 529 frames).
        swap.process(mix, n)
        swap.currentState shouldBeSameInstanceAs crossfading

        // Crossfading + set: the restart is a self-edge on the ONE Crossfading instance.
        swap.set(gain(0.25), gain(0.25))
        withClue("a restart mid-fade reuses the ONE Crossfading instance") {
            swap.currentState shouldBeSameInstanceAs crossfading
        }
        see(swap.currentState)

        // Crossfading -> Engaged, by the fade running out (five blocks cover the 529 frames).
        repeat(5) { swap.process(mix, n) }
        withClue("the fade's end lands in the ONE Engaged instance") {
            swap.currentState shouldBeSameInstanceAs engaged
        }
        see(swap.currentState)

        // Engaged -> Off.
        swap.clear()
        withClue("clear from Engaged lands in the ONE Off instance") {
            swap.currentState shouldBeSameInstanceAs off
        }
        see(swap.currentState)

        // Off -> Engaged -> Crossfading -> Off: clear mid-fade.
        swap.set(gain(1.0), gain(1.0))
        swap.currentState shouldBeSameInstanceAs engaged
        swap.set(gain(0.0), gain(0.0))
        swap.process(mix, n)
        swap.currentState shouldBeSameInstanceAs crossfading
        swap.clear()
        withClue("clear mid-fade lands in the ONE Off instance") {
            swap.currentState shouldBeSameInstanceAs off
        }
        see(swap.currentState)

        // The summary: driving the table produced no fourth object. The clue prints CLASS names.
        withClue("a transition that allocates shows up here: ${seen.map { it::class.simpleName }}") {
            seen.size shouldBe 3
        }
    }
})
