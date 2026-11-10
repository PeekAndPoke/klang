/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeLessThan
import io.peekandpoke.klang.audio_be.probes.allocatedBytesNow
import io.peekandpoke.klang.audio_be.probes.median
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.classic
import io.peekandpoke.klang.audio_bridge.optimize
import io.peekandpoke.klang.common.infra.KlangMap
import io.peekandpoke.klang.common.infra.klangMapOf
import kotlin.random.Random

/**
 * What a stage the gate switches off costs at the build (audit L5-04): its leaf query, nothing else. The gate is asked
 * in front of the build cache and reads the DSL leaf, so a gated stage takes no cache entry, no `buildRaw`, no copy of
 * its inner's answer and builds no leaf to ask it. Measured with the stage cost probe's byte counter
 * ([allocatedBytesNow], HotSpot's per-thread count) over the build of `classic()` with every stage off, against the
 * same source bare.
 *
 * Before the fix the off tail of a saw voice cost 3.6 KB and 11 us of its 12 us build (`disproof-realtime.md`, L5-04;
 * through `VoiceFactory`), and asking a leaf built a `BuiltIgnitor`, a `ParamIgnitor` and a boxed `Double` about 25
 * times per voice. After it, about 250 B through `VoiceFactory` and about 110 B here. The bound below sits well
 * between the two.
 */
class GatedStageBuildCostSpec : StringSpec({

    val saw = IgnitorDsl.Saw(freq = IgnitorDsl.Freq, analog = IgnitorDsl.Slots.analog)
    val bare = saw.optimize()
    val everyStageOff = saw.classic().optimize()
    val offParams: KlangMap<Double> = klangMapOf("adsr.on" to 0.0)

    /** The median bytes one build of [dsl] allocates, over rounds of builds, after a warm-up. */
    fun bytesPerBuild(dsl: IgnitorDsl, params: KlangMap<Double>?): Double {
        val perRound = 50
        val rounds = 21
        val samples = DoubleArray(rounds)
        var sink = 0

        repeat(3000) {
            sink += dsl.buildExciter(ignitorParams = params, random = Random(it), freqHz = 220.0).ignitor.hashCode()
        }

        for (r in 0 until rounds) {
            val b0 = allocatedBytesNow()

            repeat(perRound) {
                sink += dsl.buildExciter(ignitorParams = params, random = Random(it), freqHz = 220.0).ignitor.hashCode()
            }

            samples[r] = (allocatedBytesNow() - b0) / perRound
        }

        // keep the builds observable to the JIT
        if (sink == Int.MIN_VALUE) {
            println(sink)
        }

        return median(samples)
    }

    "audit L5-04: classic() with every stage off costs its gate queries over the bare source, not a build per stage" {
        val bareBytes = bytesPerBuild(dsl = bare, params = null)
        val offBytes = bytesPerBuild(dsl = everyStageOff, params = offParams)
        val offTail = offBytes - bareBytes

        println("GatedStageBuildCostSpec: bare $bareBytes B, every stage off $offBytes B, the off tail $offTail B per build")

        withClue("bare $bareBytes B, every stage off $offBytes B: the off tail $offTail B per build") {
            offTail shouldBeLessThan 400.0
        }
    }
})
