/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.doubles.shouldBeLessThan
import io.peekandpoke.klang.audio_bridge.BodyMaterials
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystKnobDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import kotlin.math.abs

/**
 * **A chain swap drains a leaving chain that still rings, tail or not** (KLANG-00293 (chain swap cuts ringing
 * inserts), 2026-11-04). The insert stages (body, vowel, phaser, eq) report no tail, and the swap used to retire such a
 * chain where its fade ended, cutting the ring in one sample: measured, a 220 Hz sine at 0.5 through the `bell` body
 * left a step of 0.28, a +24 dB eq bell at the sine's own frequency about 4, a phaser centred at 200 Hz 0.026. Now the
 * fade's end reads what the leaving chain put out after the fade had ended, and drains it (or cuts it, when a request
 * waits) while that rings.
 *
 * The swap goes to a chain of gain 0, so after the fade the orbit's output is the leaving chain alone. The step is the
 * last sample before that output falls to exact zeros: where the leaving chain was retired.
 */
class ChainSwapRingingInsertSpec : StringSpec({

    fun k(v: Double) = KatalystKnobDsl.Constant(v)

    val bellBody = KatalystStageDsl.Body(material = k(BodyMaterials.names.indexOf("bell").toDouble()), wet = k(1.0))
    val eqBell = KatalystStageDsl.Eq(
        sections = listOf(
            IgnitorDsl.EqSection.Bell(freq = IgnitorDsl.Constant(220.0), q = IgnitorDsl.Constant(30.0), db = IgnitorDsl.Constant(24.0)),
        ),
    )
    val lowPhaser = KatalystStageDsl.Phaser(wet = k(1.0), center = k(200.0), sweep = k(100.0))
    val dry = KatalystStageDsl.Gain(gain = k(0.9))

    /** Dry too, but late: a 5 ms lookahead delays its output, so its fade comes out of it that much after the ramp. */
    val lateDry = KatalystStageDsl.Compressor(lookahead = 0.005)

    class Swapped(val step: Double, val drained: Boolean, val cut: Boolean)

    /**
     * Plays a 220 Hz sine through [leaving] for 200 blocks, swaps to a silent chain and renders until the swap settles.
     * With [parkBehind] a third (silent) chain is requested in the fade's first block, so it waits for the fade.
     */
    fun swapAway(leaving: KatalystStageDsl, sampleRate: Int = 44100, parkBehind: Boolean = false): Swapped {
        val rig = CylinderSwapRig(sampleRate = sampleRate)

        rig.registry.register("leaving", KatalystDsl.of(leaving))
        rig.registry.register("mute", KatalystDsl.of(KatalystStageDsl.Gain(gain = k(0.0))))
        rig.registry.register("mute2", KatalystDsl.of(KatalystStageDsl.Gain(gain = k(0.0)), KatalystStageDsl.Gain(gain = k(0.0))))

        rig.cylinder.requestChain("leaving")
        rig.render(blocks = 200, level = 0.5)
        rig.cylinder.requestChain("mute")

        val out = ArrayList<Double>()
        var drained = false
        var cut = false
        var blocks = 0
        var settledBlocks = 0

        // Until the swap has stayed settled for four blocks: a parked request lands at the next poll and starts a fade.
        do {
            out.addAll(
                rig.block(level = 0.5) {
                    if (parkBehind && blocks == 1) {
                        rig.cylinder.requestChain("mute2")
                    }
                }.toList()
            )
            drained = drained || rig.cylinder.isDraining
            cut = cut || rig.swap.isCutting
            settledBlocks = if (rig.swap.settled) settledBlocks + 1 else 0
            blocks++
        } while (settledBlocks < 4 && blocks < 20000)

        var last = out.size - 1

        while (last > 0 && out[last] == 0.0) {
            last--
        }

        return Swapped(step = abs(out[last]), drained = drained, cut = cut)
    }

    "a ringing insert drains where the fade ends, and is retired only once it has rung out" {
        for ((name, stage) in listOf("bell body" to bellBody, "eq bell" to eqBell, "low phaser" to lowPhaser)) {
            withClue(name) {
                val swapped = swapAway(stage)

                swapped.drained.shouldBeTrue()
                swapped.step shouldBeLessThan 1e-4
            }
        }
    }

    "a chain that does not ring retires where the fade ends, as before: no drain, and a parked request is not cut" {
        for ((name, stage) in listOf("dry" to dry, "dry with a lookahead" to lateDry)) {
            for (park in listOf(false, true)) {
                withClue("$name, parked: $park") {
                    val swapped = swapAway(stage, parkBehind = park)

                    swapped.drained.shouldBeFalse()
                    swapped.cut.shouldBeFalse()
                }
            }
        }
    }

    "a ringing insert with a request parked behind its fade is cut, not retired" {
        val swapped = swapAway(bellBody, parkBehind = true)

        swapped.cut.shouldBeTrue()
        swapped.step shouldBeLessThan 1e-4
    }

    "where the fade ends on a block's end (96 kHz), the end is decided one block later, on the ring alone" {
        // 0.06 s at 96 kHz is 5760 frames, 45 blocks exactly: no frame of the fade's last block shows the ring alone.
        withClue("bell body") {
            val swapped = swapAway(bellBody, sampleRate = 96000)

            swapped.drained.shouldBeTrue()
            swapped.step shouldBeLessThan 1e-4
        }

        withClue("dry") {
            val swapped = swapAway(dry, sampleRate = 96000, parkBehind = true)

            swapped.drained.shouldBeFalse()
            swapped.cut.shouldBeFalse()
        }
    }
})
