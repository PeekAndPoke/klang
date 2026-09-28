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
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.voices.Voice

/**
 * The lifecycle of [KatalystCompressorEffect] is a state machine whose transitions are POINTER SWAPS
 * between three objects created with the effect. The shape is `KatalystDelayStateIdentitySpec`'s.
 *
 * **What this spec guards is "the state machine adds no allocation"** (section 1 of
 * `docs/plans/effect-state-machines.md`), and only that. What the edges DO (the fade law, the
 * turn-around, the release on landing, the cut and the snap) is guarded in
 * `KatalystCompressorEffectSpec`.
 *
 * **What the main row drives:** every cell of the table on `KatalystCompressorEffect.State` except
 * the ones marked "(never)", plus a `process` in each state that ends nothing, which must be a
 * self-edge, and both "fresh" arms (the first initialisation). The seam is
 * [KatalystCompressorEffect.currentState].
 */
class KatalystCompressorStateIdentitySpec : StringSpec({

    val sampleRate = 44100
    val n = 128

    val settings = Voice.Compressor(thresholdDb = -20.0, ratio = 4.0, kneeDb = 6.0, attackSeconds = 0.005, releaseSeconds = 0.1)

    "every reachable cell of the table points at the three states created with the effect" {
        val fx = KatalystCompressorEffect(sampleRate, n)
        val ctx = KatalystContext(blockFrames = n, mixBuffer = StereoBuffer(n))

        // An IDENTITY collection, deliberately: a `Set` compares with `equals`, so a state written
        // one day as a `data class` would make two FRESH instances equal and keep this row green
        // while every transition allocated. Everything here compares with `===`.
        val seen = mutableListOf<Any>()

        fun see(state: Any) {
            if (seen.none { it === state }) {
                seen += state
            }
        }

        // 17.2 blocks at 44.1 kHz; this many blocks lands any fade.
        val land = 19

        fun block() {
            ctx.mixBuffer.left.fill(0.5)
            ctx.mixBuffer.right.fill(0.5)
            fx.process(ctx)
        }

        // Off, as the builder makes it.
        val off = fx.currentState
        see(off)

        // Off + OFF, Off + reset, Off + retire: self-edges.
        fx.configure(null)
        fx.reset()
        fx.retire()
        fx.currentState shouldBeSameInstanceAs off

        // Off -> Engaged while fresh: at once. Engaged + ON: a self-edge.
        fx.configure(settings)
        val engaged = fx.currentState
        see(engaged)
        engaged shouldNotBeSameInstanceAs off
        fx.configure(settings)
        fx.currentState shouldBeSameInstanceAs engaged

        // Engaged + OFF while fresh: Off at once.
        fx.configure(null)
        fx.currentState shouldBeSameInstanceAs off

        // Off + a block (the snap is spent): a self-edge.
        block()
        fx.currentState shouldBeSameInstanceAs off

        // Off -> Fading: an ON fades in.
        fx.configure(settings)
        val fading = fx.currentState
        see(fading)
        withClue("Fading is its own state") {
            fading shouldNotBeSameInstanceAs engaged
            fading shouldNotBeSameInstanceAs off
        }

        // Fading (in) + a block that ends nothing, + ON, + OFF (turned around), + OFF again, + ON
        // (turned around again): self-edges.
        block()
        fx.currentState shouldBeSameInstanceAs fading
        fx.configure(settings)
        fx.currentState shouldBeSameInstanceAs fading
        fx.configure(null)
        fx.currentState shouldBeSameInstanceAs fading
        fx.configure(null)
        fx.currentState shouldBeSameInstanceAs fading
        fx.configure(settings)
        withClue("a turn-around either way reuses the ONE Fading instance") {
            fx.currentState shouldBeSameInstanceAs fading
        }

        // Fading -> Engaged, by the fade landing on full weight.
        repeat(land) { block() }
        withClue("the fade-in lands in the ONE Engaged instance") {
            fx.currentState shouldBeSameInstanceAs engaged
        }

        // Engaged + a block: a self-edge. Engaged + OFF: Fading (out).
        block()
        fx.currentState shouldBeSameInstanceAs engaged
        fx.configure(null)
        fx.currentState shouldBeSameInstanceAs fading

        // Fading -> Off, by the fade landing on dry.
        repeat(land) { block() }
        withClue("the fade-out lands in the ONE Off instance") {
            fx.currentState shouldBeSameInstanceAs off
        }

        // Engaged + reset, Fading + reset, Fading + retire: Off.
        fx.configure(settings)
        repeat(land) { block() }
        fx.currentState shouldBeSameInstanceAs engaged
        fx.reset()
        fx.currentState shouldBeSameInstanceAs off

        block()
        fx.configure(settings)
        fx.currentState shouldBeSameInstanceAs fading
        fx.reset()
        fx.currentState shouldBeSameInstanceAs off

        block()
        fx.configure(settings)
        fx.currentState shouldBeSameInstanceAs fading
        fx.retire()
        fx.currentState shouldBeSameInstanceAs off

        withClue("exactly three state objects, ever") {
            seen.size shouldBe 3
        }
    }
})
