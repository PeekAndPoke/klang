/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.master

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import io.peekandpoke.klang.audio_bridge.MasterDsl
import io.peekandpoke.klang.audio_bridge.MasterStageDsl

/**
 * The C3 scaffolding, `MasterDslShim` (goes with the Master DSL in phase 3 step 12 C5): what each
 * wire stage of a `MasterDsl` becomes in the `KatalystDsl` the output runs, and the build-time rules
 * of the retired master chain it keeps so C3 is identity.
 *
 * The oracles are LITERALS, never the shim's own constants or the wire's defaults, and every number
 * in the table row is distinct, so a knob that lands in a neighbour's field or takes its fallback
 * shows by name.
 */
class MasterDslShimSpec : StringSpec({

    fun c(value: Double) = IgnitorDsl.Constant(value)

    fun shim(vararg stages: MasterStageDsl): KatalystDsl = MasterDslShim.toKatalyst(MasterDsl.of(*stages))

    "every stage maps to its Katalyst twin, in order, every knob a constant of the same number" {
        shim(
            MasterStageDsl.Reverb(wet = 0.31, size = 6.2, lowpass = 4321.0),
            MasterStageDsl.Gain(gain = 1.7),
            MasterStageDsl.Delay(wet = 0.27, time = 0.41, feedback = 0.53, cap = 1.9),
            MasterStageDsl.Limiter(
                threshold = -3.5,
                ratio = 11.0,
                knee = 1.25,
                attackSeconds = 0.004,
                releaseSeconds = 0.37,
                lookaheadSeconds = 0.003,
            ),
        ) shouldBe KatalystDsl.of(
            KatalystStageDsl.Reverb(wet = c(0.31), size = c(6.2), lowpass = c(4321.0)),
            KatalystStageDsl.Gain(gain = c(1.7)),
            KatalystStageDsl.Delay(wet = c(0.27), time = c(0.41), feedback = c(0.53), cap = c(1.9)),
            KatalystStageDsl.Compressor(
                threshold = c(-3.5),
                ratio = c(11.0),
                knee = c(1.25),
                attack = c(0.004),
                release = c(0.37),
                lookahead = 0.003,
            ),
        )
    }

    "the empty master is the empty chain, and a reverb without a lowpass has none" {
        MasterDslShim.toKatalyst(MasterDsl.default) shouldBe KatalystDsl(emptyList())

        shim(MasterStageDsl.Reverb(wet = 0.2, size = 7.0, lowpass = null)) shouldBe
            KatalystDsl.of(KatalystStageDsl.Reverb(wet = c(0.2), size = c(7.0), lowpass = null))
    }

    "a stage that cannot be heard is left out, so a chain of them stays empty (the engine's fast path)" {
        withClue("unity gain") { shim(MasterStageDsl.Gain(gain = 1.0)).stages shouldBe emptyList() }
        withClue("reverb wet at the floor") { shim(MasterStageDsl.Reverb(wet = 0.0001, size = 5.0)).stages shouldBe emptyList() }
        withClue("reverb authored size 0.05, normalized under 0.01") {
            shim(MasterStageDsl.Reverb(wet = 0.5, size = 0.05)).stages shouldBe emptyList()
        }
        withClue("delay wet at the floor") { shim(MasterStageDsl.Delay(wet = 0.0001, time = 0.3)).stages shouldBe emptyList() }
        withClue("delay time under 10 ms") { shim(MasterStageDsl.Delay(wet = 0.5, time = 0.0099)).stages shouldBe emptyList() }
    }

    "just above each floor the stage is kept" {
        withClue("gain") { shim(MasterStageDsl.Gain(gain = 1.0001)).stages.size shouldBe 1 }
        withClue("reverb wet") { shim(MasterStageDsl.Reverb(wet = 0.00011, size = 5.0)).stages.size shouldBe 1 }
        withClue("reverb authored size 0.1, normalized exactly 0.01") {
            shim(MasterStageDsl.Reverb(wet = 0.5, size = 0.1)).stages.size shouldBe 1
        }
        withClue("delay wet") { shim(MasterStageDsl.Delay(wet = 0.00011, time = 0.3)).stages.size shouldBe 1 }
        withClue("delay time 10 ms") { shim(MasterStageDsl.Delay(wet = 0.5, time = 0.01)).stages.size shouldBe 1 }
    }

    "a non-finite knob takes the old master's fallback, never the Katalyst's unset" {
        // A slot reads non-finite as UNSET, which for a send stage is OFF; the old master substituted
        // the shared default instead, and C3 keeps that. Literals: 0.25 / 5.0 are REVERB_WET /
        // REVERB_SIZE, 0.25 / 0.25 / 0.3 / 1.0 the delay's, -1 / 20 / 2 / 0.001 / 0.1 the limiter's.
        val nan = Double.NaN
        val inf = Double.POSITIVE_INFINITY

        shim(MasterStageDsl.Gain(gain = nan)).stages shouldBe emptyList() // unity, then dropped

        shim(MasterStageDsl.Reverb(wet = nan, size = inf, lowpass = nan)) shouldBe
            KatalystDsl.of(KatalystStageDsl.Reverb(wet = c(0.25), size = c(5.0), lowpass = null))

        // The delay's wet and time fallbacks are the SAME number (0.25), so each is probed alone, next
        // to finite, distinct siblings: a knob that read its neighbour's field would land on the
        // neighbour's number instead. (Swapping the two constants themselves cannot be seen while
        // they are equal.)
        shim(MasterStageDsl.Delay(wet = inf, time = 0.41, feedback = 0.53, cap = 1.9)) shouldBe
            KatalystDsl.of(KatalystStageDsl.Delay(wet = c(0.25), time = c(0.41), feedback = c(0.53), cap = c(1.9)))

        shim(MasterStageDsl.Delay(wet = 0.27, time = nan, feedback = 0.53, cap = 1.9)) shouldBe
            KatalystDsl.of(KatalystStageDsl.Delay(wet = c(0.27), time = c(0.25), feedback = c(0.53), cap = c(1.9)))

        shim(MasterStageDsl.Delay(wet = 0.27, time = 0.41, feedback = nan, cap = -inf)) shouldBe
            KatalystDsl.of(KatalystStageDsl.Delay(wet = c(0.27), time = c(0.41), feedback = c(0.3), cap = c(1.0)))

        shim(
            MasterStageDsl.Limiter(
                threshold = nan,
                ratio = inf,
                knee = nan,
                attackSeconds = nan,
                releaseSeconds = -inf,
                lookaheadSeconds = 0.002,
            ),
        ) shouldBe KatalystDsl.of(
            KatalystStageDsl.Compressor(
                threshold = c(-1.0),
                ratio = c(20.0),
                knee = c(2.0),
                attack = c(0.001),
                release = c(0.1),
                lookahead = 0.002,
            ),
        )
    }

    "the lookahead passes through raw: the compressor stage bounds it, as the old build did" {
        // The one bound on what a lookahead allocates lives in `Compressor.coerceLookaheadSeconds`,
        // which `KatalystCompressorEffect` calls; a second bound here would be a second home.
        shim(MasterStageDsl.Limiter(lookaheadSeconds = 10.0)).stages.single().let {
            (it as KatalystStageDsl.Compressor).lookahead shouldBe 10.0
        }
    }
})
