/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.warehouse.NoteRings
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import kotlin.random.Random

/**
 * A node that rents its per-note ring from the warehouse's note-ring shelf plays bit for bit what it played with a ring
 * of its own (KLANG-00429, audit finding L5-03). A rented ring is longer than asked (its class: 4096 frames for a
 * Karplus line of 2,500, 16,384 for the default shimmer ring of 14,402), so each node runs on its own length, never the
 * array's size: the Karplus string's wrap and the clamp of its read delay (a low note bent below the line reaches it),
 * the shimmer's write head and its grains' wrap. And a ring that came back, dirty, and was zeroed by housekeeping plays
 * the next note exactly as a fresh one.
 */
class NoteRingIdentitySpec : StringSpec({

    val sampleRate = 48000
    val blockFrames = 128
    val blocks = 300

    class Rendered(val out: DoubleArray, val built: BuiltIgnitor)

    /** [dsl] built at [freqHz] (renting from [shelf] when given) and rendered for [blocks] blocks, the seeds fixed. */
    fun render(dsl: IgnitorDsl, freqHz: Double, shelf: NoteRings?): Rendered {
        val built = dsl.buildExciter(random = Random(7), freqHz = freqHz, sampleRate = sampleRate, noteRings = shelf)
        val total = blocks * blockFrames
        val ctx = IgniteContext(
            sampleRate = sampleRate,
            voiceDurationFrames = total,
            gateEndFrame = total,
            scratchBuffers = ScratchBuffers(blockFrames),
            random = Random(9),
        )
        val out = DoubleArray(total)
        val buffer = AudioBuffer(blockFrames)

        for (b in 0 until blocks) {
            ctx.voiceElapsedFrames = b * blockFrames
            ctx.updateOffsetAndLength(offset = 0, length = blockFrames)
            built.ignitor.generate(buffer, freqHz, ctx)
            buffer.copyInto(destination = out, destinationOffset = b * blockFrames)
        }

        return Rendered(out = out, built = built)
    }

    fun DoubleArray.sameBits(other: DoubleArray): Boolean = size == other.size && indices.all { this[it].toRawBits() == other[it].toRawBits() }

    val cases: List<Triple<String, IgnitorDsl, Double>> = listOf(
        Triple("pluck at 220 Hz", IgnitorDsl.Pluck(), 220.0),
        // 1600 samples a period; the pitch an octave down reads 3200 back, past the line: the string's own length clamps it
        Triple("pluck at 30 Hz bent an octave down, past its line", IgnitorDsl.PitchModSemitones(inner = IgnitorDsl.Pluck(), mod = IgnitorDsl.Constant(-12.0)), 30.0),
        Triple("superpluck of eight strings at 110 Hz", IgnitorDsl.SuperPluck(voices = IgnitorDsl.Constant(8.0)), 110.0),
        // A count that reads the note is not read at the build: its six strings are made at the first block, in render
        Triple(
            "superpluck whose count reads the note, its strings grown at its first block",
            IgnitorDsl.SuperPluck(voices = IgnitorDsl.Plus(IgnitorDsl.Times(IgnitorDsl.Freq, IgnitorDsl.Constant(0.0)), IgnitorDsl.Constant(6.0))),
            110.0,
        ),
        Triple("shimmer over a saw at its default pitches", IgnitorDsl.Shimmer(inner = IgnitorDsl.Saw(freq = IgnitorDsl.Freq), wet = IgnitorDsl.Constant(0.5)), 220.0),
    )

    for ((title, dsl, freqHz) in cases) {
        "$title: a rented ring plays bit for bit what an own ring played, and a ring zeroed after its return plays it again" {
            val own = render(dsl = dsl, freqHz = freqHz, shelf = null)

            withClue("without a shelf the build makes no lease") { own.built.noteRings.shouldBeNull() }

            val shelf = NoteRings()
            val rented = render(dsl = dsl, freqHz = freqHz, shelf = shelf)
            val lease = rented.built.noteRings.shouldNotBeNull()

            withClue("the node rented (an engagement control: its rings are their class, longer than asked)") {
                (lease.count >= 1) shouldBe true
                shelf.allocations shouldBe lease.count
            }
            withClue("the output is not silence") { own.out.any { it != 0.0 } shouldBe true }
            withClue("rented ring against own ring") { rented.out.sameBits(own.out) shouldBe true }

            // The rings go back with real content in them, are zeroed by housekeeping and rented by the next note
            val count = lease.count

            lease.giveBackAll()
            shelf.housekeep(maxFrames = Int.MAX_VALUE)

            val again = render(dsl = dsl, freqHz = freqHz, shelf = shelf)

            withClue("the next note rents the returned rings") {
                shelf.hits shouldBe count
                shelf.allocations shouldBe count
            }
            withClue("a returned, zeroed ring against own ring") { again.out.sameBits(own.out) shouldBe true }
        }
    }
})
