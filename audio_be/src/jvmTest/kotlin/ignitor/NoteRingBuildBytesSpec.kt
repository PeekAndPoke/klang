/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.probes.allocatedBytesNow
import io.peekandpoke.klang.audio_be.probes.median
import io.peekandpoke.klang.audio_be.warehouse.NoteRings
import io.peekandpoke.klang.audio_bridge.VoiceData
import kotlin.random.Random

/**
 * What a pluck's and a superpluck's build allocate once the warehouse's note-ring shelf is warm (KLANG-00429, audit
 * finding L5-03): their Karplus lines are rented, so the build pays the tree around them, like any other voice. Before,
 * every build allocated its lines, 2,500 doubles (20 KB) each: 22,760 B per pluck build and 163,584 B per superpluck
 * build of eight strings through `VoiceFactory` (the closing run's `AuditRealtimeRepro`, 2026-11-11). Each build here
 * is the production build of the built-in sound (`IgnitorRegistry.createExciter`), its rings given back and zeroed
 * after it, as the voice's end and the warehouse's housekeeping do; HotSpot's per-thread count ([allocatedBytesNow]).
 * The bounds sit well under one line (20 KB): a line allocated per build fails them.
 */
class NoteRingBuildBytesSpec : StringSpec({

    val registry = IgnitorRegistry().apply { registerDefaults() }

    fun bytesPerBuild(sound: String, shelf: NoteRings): Double {
        val data = VoiceData.empty.copy(sound = sound, freqHz = 220.0)
        val perRound = 20
        val rounds = 11
        val samples = DoubleArray(rounds)
        var sink = 0

        fun build(seed: Int) {
            val built = registry.createExciter(name = sound, data = data, freqHz = 220.0, random = Random(seed), sampleRate = 48000, noteRings = shelf)

            if (built != null) {
                sink += built.ignitor.hashCode()
                // The voice's end and the warehouse's housekeeping: allocation free
                built.noteRings?.giveBackAll()
                shelf.housekeep(maxFrames = Int.MAX_VALUE)
            }
        }

        repeat(300) { build(it) }

        for (r in 0 until rounds) {
            val b0 = allocatedBytesNow()

            repeat(perRound) { build(it) }

            samples[r] = (allocatedBytesNow() - b0) / perRound
        }

        // keep the builds observable to the JIT
        if (sink == Int.MIN_VALUE) {
            println(sink)
        }

        return median(samples)
    }

    "audit L5-03: a pluck's build rents its line from a warm shelf and allocates no line" {
        val shelf = NoteRings()
        val bytes = bytesPerBuild(sound = "pluck", shelf = shelf)

        println("NoteRingBuildBytesSpec: pluck $bytes B per build")

        withClue("the line was rented, never allocated after the first build") { shelf.allocations shouldBe 1 }
        withClue("$bytes B per pluck build") { bytes shouldBeLessThan 8_000.0 }
    }

    "audit L5-03: a superpluck's build rents its eight lines from a warm shelf and allocates none" {
        val shelf = NoteRings()
        val bytes = bytesPerBuild(sound = "superpluck", shelf = shelf)

        println("NoteRingBuildBytesSpec: superpluck $bytes B per build")

        withClue("the eight lines were rented, never allocated after the first build") { shelf.allocations shouldBe 8 }
        withClue("$bytes B per superpluck build") { bytes shouldBeLessThan 12_000.0 }
    }
})
