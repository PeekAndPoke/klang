/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.PlaybackEngineDispatcher
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.voices.TestIgnitors
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers
import io.peekandpoke.klang.audio_bridge.MasterDsl
import io.peekandpoke.klang.audio_bridge.MasterStageDsl
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.constants.KNOB_GLIDE_SECONDS
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import kotlin.math.abs
import kotlin.math.round

/**
 * The group fader patterned through exactly 0 on a dry orbit (signal-flow plan section 11, DECIDED
 * 2026-09-19, Katalyst 5c-8): **an orbit never deactivates while a voice plays on it**, so a muted
 * orbit with notes keeps running at 0 and its fader glides back up from where it stands.
 *
 * Before the decision the silence gate read the post-fader mix, so the orbit was reset every
 * tenth block while its voices played (one orbit: deactivated at the tenth silent visit,
 * reactivated by the next check-in, the count restarting), the lease was re-dealt, and the fader came back as a
 * one-sample jump or a one-block ramp mid-note depending on which voice claimed first.
 *
 * The oracles are the two decided laws, never numbers read off a run: the LEASE (every rendering
 * voice checks in each block; a lapsed owner is replaced one block after its last check-in, see
 * `VoiceLease`), and the knob-glide LEVEL law (linear over `KNOB_GLIDE_SECONDS` rounded to whole
 * blocks, per sample, landing exactly; `docs/plans/knob-glide.md`), applied to a reference render of
 * the same voices at unity.
 */
class CylinderFaderThroughZeroSpec : StringSpec({

    val blockFrames = 128
    val sampleRate = 44100
    val orbit = 1

    /** The decided glide length: the glide time rounded to whole blocks. */
    val glideBlocks = round(KNOB_GLIDE_SECONDS * sampleRate / blockFrames).toInt()

    fun voice(fromBlock: Int, toBlock: Int, fader: Double?): Voice = VoiceTestHelpers.createSynthVoice(
        startFrame = (fromBlock * blockFrames).toDouble(),
        endFrame = (toBlock * blockFrames).toDouble(),
        gateEndFrame = (toBlock * blockFrames).toDouble(),
        cylinderId = orbit,
        sampleRate = sampleRate,
        blockFrames = blockFrames,
        signal = TestIgnitors.constant,
        katalystParams = fader?.let { mapOf("gain.gain" to it) },
    )

    /** What one block of the engine leaves behind: the orbit's output and whether it is still active. */
    class Block(val left: DoubleArray, val active: Boolean)

    /**
     * Renders [blocks] blocks the way the engine does (voices offer themselves in list order, then
     * `Cylinders.processAndMix` with the same block start), with the silence grace production uses.
     */
    fun render(voices: List<Voice>, blocks: Int): List<Block> {
        val cylinders = Cylinders(blockFrames = blockFrames, sampleRate = sampleRate)
        val renderCtx = Voice.RenderContext(
            cylinders = cylinders,
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            voiceBuffer = AudioBuffer(blockFrames),
            freqModBuffer = DoubleArray(blockFrames),
            scratchBuffers = ScratchBuffers(blockFrames),
        )
        val fusion = StereoBuffer(blockFrames)
        val alive = voices.toMutableList()

        return List(blocks) { b ->
            renderCtx.blockStart = (b * blockFrames).toDouble()
            cylinders.clearAll()
            fusion.clear()
            alive.removeAll { !it.render(renderCtx) }
            cylinders.processAndMix(fusion, renderCtx.blockStart)

            Block(left = fusion.left.copyOf(), active = cylinders.cylinders.first().isActive)
        }
    }

    "a muted orbit never deactivates while its voice plays, and goes on the first block its lease has lapsed" {
        // The voice renders blocks 0..29 and is muted by the fader throughout, so the post-fader
        // mix is silent from the first block and the silence grace is served by block 9.
        val out = render(listOf(voice(fromBlock = 0, toBlock = 30, fader = 0.0)), blocks = 40)

        for (b in 0 until 40) {
            withClue("block $b") {
                // The lease law: the last check-in is block 29, the owner's grace covers block 30,
                // and the first visit after that (one cylinder: every block) deactivates.
                out[b].active shouldBe (b <= 30)
                // And nothing ever comes out: no reset, no re-dealt lease, no snap.
                out[b].left.all { it == 0.0 } shouldBe true
            }
        }
    }

    "a voice that is not the owner keeps the orbit alive after the owner has ended" {
        // A owns (it offers itself first) and ends after block 19; B, turned away while A's lease
        // holds, plays on until block 39. Both muted, so only the lease can hold the orbit.
        val out = render(
            listOf(voice(fromBlock = 0, toBlock = 20, fader = 0.0), voice(fromBlock = 5, toBlock = 40, fader = 0.0)),
            blocks = 50,
        )

        for (b in 0 until 50) {
            withClue("block $b") {
                // In block 20 nobody renews the lease (A has ended, B is turned away by A's grace)
                // and it is held all the same; B takes it in block 21, and its own grace ends it.
                out[b].active shouldBe (b <= 40)
            }
        }
    }

    "the fader comes back up from 0 by the glide, from where it stands, when the next owner takes over" {
        // A (fader 0) owns the orbit and ends after block 19; B (fader 1) has been playing, muted,
        // since block 5. The reference renders the same two voices with no fader value (unity).
        val muted = render(
            listOf(voice(fromBlock = 0, toBlock = 20, fader = 0.0), voice(fromBlock = 5, toBlock = 80, fader = 1.0)),
            blocks = 80,
        )
        val reference = render(
            listOf(voice(fromBlock = 0, toBlock = 20, fader = null), voice(fromBlock = 5, toBlock = 80, fader = null)),
            blocks = 80,
        )

        // B takes the lease in block 21 (A's last check-in is block 19, its grace covers block 20),
        // and the fader glides from 0 there, per sample, over the decided number of blocks.
        val takeover = 21

        fun level(k: Int): Double = if (k <= 0) 0.0 else if (k >= glideBlocks) 1.0 else k.toDouble() / glideBlocks

        withClue("the reference is not silence, so the rows below assert about sound") {
            (abs(reference[takeover].left[0]) > 0.1) shouldBe true
        }

        for (b in 0 until 80) {
            val from = level(b - takeover)
            val to = level(b - takeover + 1)
            val step = (to - from) / blockFrames

            for (i in 0 until blockFrames) {
                val expected = reference[b].left[i] * (to - step * (blockFrames - 1 - i))

                withClue("block $b frame $i") {
                    muted[b].active shouldBe true

                    if (b >= takeover + glideBlocks - 1 && i == blockFrames - 1 || b >= takeover + glideBlocks) {
                        // Landed: exactly the reference, bit for bit, from the glide's last sample on.
                        muted[b].left[i] shouldBe reference[b].left[i]
                    } else {
                        muted[b].left[i] shouldBe (expected plusOrMinus 1e-12)
                    }
                }
            }
        }
    }

    // ── Through the engine: the frame the cleanup reads is the one the voices claimed with ─────────

    /**
     * A muted sine on orbit 0 through a whole [PlaybackEngineDispatcher], with or without a master
     * chain (the engine renders its orbits through two different calls, one per case). Returns, per
     * block, whether the engine still holds the voice and whether any of its orbits is active.
     */
    fun engineRun(withMaster: Boolean): List<Pair<Boolean, Boolean>> {
        val d = PlaybackEngineDispatcher.create(
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            commLink = KlangCommLink(capacity = 1024).backend,
            performanceTimeMs = { 0.0 },
        ).also { it.setBackendStartTime(0.0) }

        val muted = ScheduledVoice(
            playbackId = "song",
            startTime = 0.0,
            gateEndTime = 0.3,
            data = VoiceData.empty.copy(sound = "sine", freqHz = 440.0, gain = 0.5, katalystParams = mapOf("gain.gain" to 0.0)),
            playbackStartTime = 0.0,
        )
        val master = ScheduledVoice(
            playbackId = "song",
            startTime = 0.0,
            gateEndTime = 1.0,
            data = VoiceData.empty.copy(master = "loud", control = true),
            playbackStartTime = 0.0,
        )

        if (withMaster) {
            d.handle(KlangCommLink.Cmd.RegisterMaster(playbackId = "song", name = "loud", dsl = MasterDsl.of(MasterStageDsl.Gain(gain = 2.0))))
        }

        d.handle(KlangCommLink.Cmd.ScheduleVoices(playbackId = "song", voices = if (withMaster) listOf(master, muted) else listOf(muted)))

        val out = ShortArray(blockFrames * 2)

        return List(400) { b ->
            d.renderBlock(cursorFrame = (b * blockFrames).toDouble(), out = out)
            val engine = d.engine("song")!!

            (engine.scheduler.getActiveVoiceCount() > 0) to engine.cylinders.anyActive()
        }
    }

    listOf(false, true).forEach { withMaster ->
        "through the engine${if (withMaster) " with a master chain" else ""}: a muted voice holds its orbit exactly as long as it plays" {
            val run = engineRun(withMaster)
            // The first block the engine no longer holds the voice: its last check-in was the block
            // before, the owner's grace covers this one, and the orbit goes on the next visit.
            val gone = run.indexOfFirst { (playing, _) -> !playing }

            withClue("the voice started and ended inside the run") {
                (gone > 20) shouldBe true
            }

            for ((b, state) in run.withIndex()) {
                withClue("block $b") {
                    state.second shouldBe (b <= gone)
                }
            }
        }
    }
})
