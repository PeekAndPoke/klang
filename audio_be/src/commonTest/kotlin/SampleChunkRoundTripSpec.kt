/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.MonoSamplePcm
import io.peekandpoke.klang.audio_bridge.SampleRequest
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink

/**
 * Guards the sample upload SPLITTER (`Cmd.Sample.Complete.toChunks`) against the reassembler
 * ([SampleStore.addSample]) that consumes it. The two are the only writers of a sample's PCM
 * layout, and neither had a test: `SampleStoreSpec` hand-builds `Chunk` objects, so it exercises
 * reassembly against chunks the splitter never produced.
 *
 * Failure here is SILENT and audible: `chunkOffset` feeds `copyInto`'s `destinationOffset`, so a
 * wrong offset writes real PCM to the wrong place and the sample plays back corrupted rather than
 * throwing. That is what makes a round-trip worth more than asserting the chunk list's shape.
 *
 * The assertions deliberately avoid restating the splitter's own arithmetic (`size / n + 1` etc.).
 * They check properties that hold for ANY correct splitting: the offsets tile the array
 * contiguously from zero, they cover it exactly, and only the final chunk claims completion.
 */
class SampleChunkRoundTripSpec : StringSpec({

    // Small enough that the boundary cases below are readable, and not a power-of-two multiple of
    // anything in the production call (which passes 64 * 1024) - the logic is size-independent.
    val chunk = 8

    fun req(sound: String) = SampleRequest(bank = null, sound = sound, index = null, note = null)

    fun newStore() = SampleStore(KlangCommLink(capacity = 1024).backend)

    /**
     * A distinct value per index. With a constant fill, a wrong `chunkOffset` would still produce
     * a "correct" array; with the index as the value, a misplaced chunk shows up as a wrong VALUE
     * and not merely a wrong length.
     */
    fun pcmOf(size: Int) = DoubleArray(size) { it.toDouble() }

    fun complete(r: SampleRequest, pcm: DoubleArray) = KlangCommLink.Cmd.Sample.Complete(
        req = r,
        note = "c4",
        pitchHz = 261.6,
        sample = MonoSamplePcm(sampleRate = 44100, pcm = pcm),
    )

    // Empty, single frame, either side of one chunk, an exact multiple (the case that emits a
    // trailing EMPTY chunk), and a multi-chunk size with a partial tail.
    listOf(0, 1, chunk - 1, chunk, chunk + 1, 2 * chunk, 3 * chunk + 3).forEach { size ->

        "toChunks then addSample round-trips $size frames at chunk size $chunk" {
            val r = req("sound-$size")
            val original = pcmOf(size)
            val store = newStore()

            val chunks = complete(r, original).toChunks(chunk)

            var covered = 0
            chunks.forEachIndexed { i, c ->
                // Contiguous from zero: each chunk starts exactly where the previous one ended.
                c.chunkOffset shouldBe covered
                // The receiver allocates `DoubleArray(totalSize)` from this, so it must be the
                // WHOLE sample's length on every chunk, not the chunk's own.
                c.totalSize shouldBe size
                // Completion is claimed once, by the last chunk. A premature flag would publish a
                // half-filled sample as Complete.
                c.isLastChunk shouldBe (i == chunks.lastIndex)

                covered += c.data.size
            }

            // No frame dropped and none duplicated.
            covered shouldBe size

            chunks.forEach { store.addSample(it) }

            store.getComplete(r).shouldNotBeNull().sample.pcm.toList() shouldBe original.toList()
        }
    }

    "a chunk stream is incomplete until the last chunk arrives" {
        val r = req("partial")
        val store = newStore()
        val chunks = complete(r, pcmOf(3 * chunk)).toChunks(chunk)

        chunks.dropLast(1).forEach { store.addSample(it) }

        // Without this, `isLastChunk` could be set on every chunk and the round-trip above would
        // still pass: the store would publish Complete early and the final copyInto would fix it up.
        store.getComplete(r) shouldBe null

        store.addSample(chunks.last())
        store.getComplete(r).shouldNotBeNull()
    }
})
