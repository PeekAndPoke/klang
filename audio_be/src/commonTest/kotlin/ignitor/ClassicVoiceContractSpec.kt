/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_bridge.AdsrCurves

/**
 * **The built-in `saw` on `classic()`, against Ignitor nodes built by hand: the CONTRACT half** (phase 3 step 9,
 * 2026-09-27, when the voice strip retired): the named filter curves reach the filter node as their enum literals,
 * and the built-in shape puts `pregain` in front of every `classic()` stage.
 *
 * Until step 9 these rows were `ClassicStripParitySpec`, which also rendered each slot row through the old voice
 * strip. Its per-row loop went with the test consolidation (2026-09-27): the authored-equals-built-in compare could
 * not fail once both were one tree on one path, and each slot's engagement is `ClassicDoorRenderParitySpec`'s (sprudel).
 * The rows' raw-bits fingerprints (`ClassicVoiceBaselineSpec`, JVM only) were retired at the phase 3 end checkpoint
 * (2026-10-02): the hand-built oracles here hold the laws. The render is [ClassicVoiceRig]'s.
 */
class ClassicVoiceContractSpec : StringSpec({

    with(ClassicVoiceRig) {
        for (rate in rates) {
            for ((door, freq) in curveFilters) {
                "[$rate Hz] the named $door curves ARE the node's curves named as enum literals, and they move the sweep" {
                    val env = mapOf("$door.freq" to freq, "$door.env" to 24.0, "$door.attack" to 0.01, "$door.decay" to 0.15, "$door.sustain" to 0.3, "$door.release" to 0.1)
                    val curves = mapOf(
                        "${door}Curves.attack" to AdsrCurves.indexOf(namedCurves.first),
                        "${door}Curves.decay" to AdsrCurves.indexOf(namedCurves.second),
                        "${door}Curves.release" to AdsrCurves.indexOf(namedCurves.third),
                    )
                    val named = classic(env + curves, rate)

                    withClue("first mismatching frame against the node with Linear, SCurve, InvSquare named") {
                        firstMismatch(classic(emptyMap(), rate, sound = "curved$door"), named) shouldBe -1
                    }
                    withClue("anti-vacuous: the named curves change the sweep") {
                        firstMismatch(classic(env, rate), named) shouldNotBe -1
                    }
                }
            }

            // ── pregain: placed on every built-in's source, in front of every classic() stage ──

            "[$rate Hz] [pregain] a built-in's pregain sits IN FRONT of its nonlinear stages: distort and crush see the doubled source" {
                for ((label, bag) in listOf(
                    "distort 0.5" to mapOf("distort.amount" to 0.5),
                    "crush 5 with onepole 900" to mapOf("crush.amount" to 5.0, "onepole" to 900.0),
                )) {
                    val builtIn = classic(bag + ("pregain" to 2.0), rate)
                    val oracle = classic(bag, rate, sound = "saw2x")
                    val afterTheStage = classic(bag, rate).map { 2.0 * it }.toDoubleArray()

                    withClue("$label: first mismatch against the doubled source through classic()") {
                        firstMismatch(oracle, builtIn) shouldBe -1
                    }
                    withClue("$label: anti-vacuous, doubling AFTER the stage is a different signal") {
                        firstMismatch(afterTheStage, builtIn) shouldNotBe -1
                    }
                }
            }

            "[$rate Hz] [pregain] ...and in front of the onepole: the source, then pregain, then onepole (a 1.7 gain, so the order shows in the bits)" {
                // Scaling by 2 commutes with the linear one-pole bit for bit; a gain of 1.7 rounds differently on
                // either side of it, so this row tells `source.pregain().onepole()` from `source.onepole().pregain()`.
                val builtIn = classic(mapOf("onepole" to 900.0, "pregain" to 1.7), rate)
                val oracle = classic(mapOf("onepole" to 900.0), rate, sound = "saw1p7x")

                withClue("first mismatch against the 1.7x source through classic()'s onepole") {
                    firstMismatch(oracle, builtIn) shouldBe -1
                }
                withClue("anti-vacuous, the onepole-then-gain order is a different signal") {
                    firstMismatch(classic(emptyMap(), rate, sound = "onepolethen1p7x"), builtIn) shouldNotBe -1
                }
            }
        }
    }
})
