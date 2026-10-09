/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_engine.KlangOfflineRenderer
import io.peekandpoke.klang.script.stdlib.KlangScriptIgnitor
import io.peekandpoke.klang.sprudel.lang.note
import io.peekandpoke.klang.sprudel.lang.sound

/**
 * A number child of `Ignitor.variants` is a constant (Q23, maintainer 2026-10-09), so `variants(400, 1200)` as a
 * sine's frequency picks 400 Hz or 1200 Hz per note by the note's `:n`, on both doors.
 *
 * Each row renders one cycle of two notes through the real voice path (`KlangOfflineRenderer`) and reads each note's
 * frequency from its rising zero crossings in the middle of the note, away from the onset and the gate end.
 */
class VariantsNumberChildRenderSpec : StringSpec({

    val sampleRate = 48_000

    suspend fun render(pattern: SprudelPattern): DoubleArray {
        val out = ArrayList<Double>()

        KlangOfflineRenderer(sampleRate = sampleRate).render(
            pattern = pattern,
            cycles = 1,
            cyclesPerSecond = 1.0,
            tailSec = 0.0,
            onBlock = { block, n ->
                for (i in 0 until n) {
                    out.add(block.left[i])
                }
            },
        )

        return out.toDoubleArray()
    }

    /** The frequency in Hz between [fromSec] and [toSec], from the rising zero crossings. */
    fun frequency(samples: DoubleArray, fromSec: Double, toSec: Double): Double {
        val from = (fromSec * sampleRate).toInt()
        val to = (toSec * sampleRate).toInt()
        var crossings = 0

        for (i in from + 1 until to) {
            if (samples[i - 1] < 0.0 && samples[i] >= 0.0) {
                crossings++
            }
        }

        return crossings / (toSec - fromSec)
    }

    fun script(code: String): SprudelPattern = SprudelPattern.compile(code) ?: error("the song did not compile: $code")

    // the first note (no `:n`) is variant 0, the second (`:1`) variant 1; each lasts half a second
    listOf(
        "KlangScript door" to { script("""note("a4 a4:1").sound(Ignitor.sine(Ignitor.variants(400, 1200)))""") },
        "Kotlin door" to { note("a4 a4:1").sound(KlangScriptIgnitor.sine(KlangScriptIgnitor.variants(400, 1200))) },
    ).forEach { (door, pattern) ->
        "$door: Ignitor.sine(Ignitor.variants(400, 1200)) plays 400 Hz, then 1200 Hz" {
            val samples = render(pattern())

            withClue("first note, variant 0") { frequency(samples, fromSec = 0.1, toSec = 0.4) shouldBe (400.0 plusOrMinus 8.0) }
            withClue("second note, variant 1") { frequency(samples, fromSec = 0.6, toSec = 0.9) shouldBe (1200.0 plusOrMinus 8.0) }
        }
    }
})
