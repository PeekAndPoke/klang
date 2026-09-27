/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_bridge.AdsrCurves
import kotlin.math.abs

/**
 * **The built-in `saw` on `classic()`, one voice per slot row: the CONTRACT half** (phase 3 step 9, 2026-09-27, when the
 * voice strip retired). Every row renders the same tree registered by an AUTHOR (`authoredsaw`, its last call
 * `.classic()`), which must be the built-in bit for bit, and checks that its slots are ENGAGED (the row differs from
 * the untouched voice). The rows at the end compare `classic()` against Ignitor nodes built by hand: the default filter
 * curve, the named filter curves, the filter envelope's slot-layer fill, and where the built-in shape puts `pregain`.
 *
 * Until step 9 these rows were `ClassicStripParitySpec`, which also rendered each row through the old voice strip. The
 * rows' raw-bits fingerprints, frozen from the tree that still had the strip, are `ClassicVoiceBaselineSpec`'s
 * (JVM only: Kotlin/JS math rounds differently, and a whole-number double prints differently in a row's title).
 * The rows and the render are [ClassicVoiceRig]'s.
 */
class ClassicVoiceContractSpec : StringSpec({

    with(ClassicVoiceRig) {
        "the harness sees sound: the untouched voice is not silence" {
            for (rate in rates) {
                withClue("$rate Hz") { untouched.getValue(rate).maxOf { abs(it) } shouldBeGreaterThan 0.1 }
            }
        }

        for (rate in rates) {
            for (row in rows) {
                "[$rate Hz] ${row.title}" {
                    val bag = classic(row.bag, rate, voice = row.voice)
                    val authored = classic(row.bag, rate, sound = "authoredsaw", voice = row.voice)

                    withClue("AUTHORED: an authored tree that ends in classic() IS the built-in, first mismatch") {
                        firstMismatch(bag, authored) shouldBe -1
                    }

                    if (row.bag.isNotEmpty()) {
                        withClue("engagement: the slots render something other than the untouched voice") {
                            firstMismatch(bag, untouched.getValue(rate)) shouldNotBe -1
                        }
                    }
                }
            }

            "[$rate Hz] the unwritten filter curve IS Exponential (D3 b): `lpf env 24` renders the door's lowpass with its curves named Exponential" {
                val c = classic(mapOf("lpf.freq" to 600.0, "lpf.env" to 24.0), rate)

                withClue("first mismatching frame against the Exponential-named lowpass") {
                    firstMismatch(classic(emptyMap(), rate, sound = "expfilter"), c) shouldBe -1
                }
                withClue("anti-vacuous: the Linear-named lowpass is not it") {
                    firstMismatch(classic(emptyMap(), rate, sound = "linfilter"), c) shouldNotBe -1
                }
            }

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

            "[$rate Hz] the D3 fill row IS the door's sweep: `lpf.attack` alone renders `lowpass(600, attackSec = 0.05)`" {
                val c = classic(mapOf("lpf.freq" to 600.0, "lpf.attack" to 0.05), rate)
                val door = classic(emptyMap(), rate, sound = "doorfill")

                withClue("first mismatching frame against the door-built filter") { firstMismatch(door, c) shouldBe -1 }
                withClue("and that is not the static filter") {
                    firstMismatch(classic(mapOf("lpf.freq" to 600.0), rate), c) shouldNotBe -1
                }
            }

            // ── pregain: placed on every built-in's source, in front of every classic() stage ──

            "[$rate Hz] [pregain] a built-in's pregain 2 doubles every sample exactly: nothing nonlinear is written" {
                val unity = classic(emptyMap(), rate)
                val doubled = classic(mapOf("pregain" to 2.0), rate)

                withClue("engaged, the unity voice sounds") { unity.any { it != 0.0 } shouldBe true }
                withClue("first frame that is not exactly twice the unity voice") {
                    (unity.indices.firstOrNull { doubled[it].toRawBits() != (2.0 * unity[it]).toRawBits() } ?: -1) shouldBe -1
                }
            }

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
