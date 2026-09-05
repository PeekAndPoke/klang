/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.AdsrDef
import io.peekandpoke.klang.audio_bridge.MonoSamplePcm
import io.peekandpoke.klang.audio_bridge.SampleMetadata
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

    // ── meta must round-trip too — it did not, and no soundfont had ever looped in the browser ──
    //
    // Every chunk carries the sample's meta (toChunks puts it on each one), and the receiver's
    // reassembly used to construct `MonoSamplePcm(sampleRate, pcm)` and let meta default to
    // {loop = null, adsr = null, anchor = 0}. JsAudioBackend chunks EVERY Complete before the
    // worklet boundary regardless of size, so in the browser every sample arrived stripped: no
    // soundfont ever looped there, while the JVM path — which passes Complete in-process — always
    // did. The rows above checked that the PCM survived and never asked about meta. Both ends
    // covered, the join empty.

    val richMeta = SampleMetadata(
        anchor = 0.75,
        loop = SampleMetadata.LoopRange(startSec = 0.123, endSec = 0.456),
        adsr = AdsrDef.Std(attack = 0.0, decay = 0.0, sustain = 1.0, release = 0.05),
    )

    listOf(1, chunk + 1, 3 * chunk + 3).forEach { size ->

        "meta survives reassembly — loop, adsr and anchor intact after $size frames in chunks" {
            val r = req("meta-$size")
            val store = newStore()
            val original = KlangCommLink.Cmd.Sample.Complete(
                req = r,
                note = "c4",
                pitchHz = 261.6,
                sample = MonoSamplePcm(sampleRate = 44100, pcm = pcmOf(size), meta = richMeta),
            )

            original.toChunks(chunk).forEach { store.addSample(it) }

            val arrived = store.getComplete(r).shouldNotBeNull().sample.meta
            arrived.loop shouldBe richMeta.loop
            arrived.adsr shouldBe richMeta.adsr
            arrived.anchor shouldBe richMeta.anchor
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
