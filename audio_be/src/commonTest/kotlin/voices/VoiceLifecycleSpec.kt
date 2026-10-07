/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.ignitor.IgniteContext
import io.peekandpoke.klang.audio_be.ignitor.Ignitor
import io.peekandpoke.klang.audio_be.voices.Voice.State
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.createContext
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.createVoice
import io.peekandpoke.klang.audio_bridge.AdsrCurve
import io.peekandpoke.klang.audio_bridge.constants.VOICE_CULL_NEVER
import kotlin.math.abs

/**
 * The voice's lifecycle state machine (`Voice.state`, step 1 of `docs/tasks/voice-lifecycle-state-machine.md`):
 * which state a real `Voice` is in on which block, and that the terminal states stay terminal.
 *
 * The state describes a whole block: `Pending` until the block that holds the onset, `Sounding` from there,
 * `Releasing` from the first block that STARTS at or after the gate end, `Zombie` at the end of the release block
 * that completes the cull window of silence, `Done` from the first block that starts at or after `endFrame`.
 *
 * The voices are the helper's constant 1.0 source (no envelope unless a row says so), 48 kHz, 128-frame blocks.
 * Every frame below is a multiple of 128 unless the row is about a frame inside a block.
 */
class VoiceLifecycleSpec : StringSpec({

    val sampleRate = 48000
    val blockFrames = 128
    val sentinel = 7.0

    fun voice(
        start: Double,
        gate: Double,
        end: Double,
        cull: Double? = VOICE_CULL_NEVER,
        envelope: Voice.Envelope? = null,
    ): Voice = createVoice(
        startFrame = start, gateEndFrame = gate, endFrame = end,
        sampleRate = sampleRate, blockFrames = blockFrames, cull = cull, envelope = envelope,
    )

    // A fresh context (and with it fresh orbits) per row.
    var ctx = createContext(blockStart = 0.0, blockFrames = blockFrames, sampleRate = sampleRate)

    beforeTest {
        ctx = createContext(blockStart = 0.0, blockFrames = blockFrames, sampleRate = sampleRate)
    }

    /** Renders the block starting at [blockStart] over a sentinel-filled voice buffer; returns render's answer. */
    fun block(v: Voice, blockStart: Double): Boolean {
        ctx.voiceBuffer.fill(sentinel)
        ctx.blockStart = blockStart

        return v.render(ctx)
    }

    fun untouched(): Boolean = ctx.voiceBuffer.all { it == sentinel }

    "Pending until the block that holds the onset, a block ending exactly on it included" {
        val v = voice(start = 1024.0, gate = 4096.0, end = 8192.0)

        withClue("before the first render") { v.state shouldBe State.Pending }

        block(v, 0.0) shouldBe true
        withClue("first block") { v.state shouldBe State.Pending }
        withClue("a pending block renders nothing") { untouched() shouldBe true }

        block(v, 896.0) shouldBe true
        withClue("the block [896, 1024) ends ON the onset: still pending") { v.state shouldBe State.Pending }
        withClue("nothing rendered") { untouched() shouldBe true }
        withClue("a pending voice does not touch its orbit (no lease claim)") { ctx.cylinders.cylindersIds.isEmpty() shouldBe true }

        block(v, 1024.0) shouldBe true
        withClue("the block that starts on the onset") { v.state shouldBe State.Sounding }
        withClue("the onset block renders") { ctx.voiceBuffer[0] shouldBe 1.0 }
        withClue("and sends to its orbit") { ctx.cylinders.cylindersIds shouldBe setOf(v.cylinderId) }
    }

    "Sounding through the gate, Releasing from the first block that starts at or after the gate end" {
        // Gate on a block boundary: the block [1920, 2048) is the last sounding one.
        val onBoundary = voice(start = 0.0, gate = 2048.0, end = 8192.0)

        block(onBoundary, 0.0)
        withClue("the onset block") { onBoundary.state shouldBe State.Sounding }
        block(onBoundary, 1920.0)
        withClue("the last block of the gate") { onBoundary.state shouldBe State.Sounding }
        block(onBoundary, 2048.0)
        withClue("the block that starts ON the gate end") { onBoundary.state shouldBe State.Releasing }
        withClue("a releasing voice renders") { ctx.voiceBuffer[0] shouldBe 1.0 }

        // Gate inside a block: the block holding it is still Sounding, the next one is Releasing.
        val inside = voice(start = 0.0, gate = 2100.0, end = 8192.0)

        block(inside, 0.0)
        block(inside, 2048.0)
        withClue("the block [2048, 2176) holds the gate end: sounding") { inside.state shouldBe State.Sounding }
        block(inside, 2176.0)
        withClue("the next block") { inside.state shouldBe State.Releasing }
    }

    "Done from the first block that starts at or after endFrame, the block before it renders its frames" {
        val v = voice(start = 0.0, gate = 1024.0, end = 2100.0)

        block(v, 0.0)
        block(v, 1024.0)
        block(v, 2048.0) shouldBe true
        withClue("the block [2048, 2176) holds the end: releasing") { v.state shouldBe State.Releasing }
        withClue("its frames up to the end render") { ctx.voiceBuffer[51] shouldBe 1.0 }
        withClue("past the end nothing renders") { ctx.voiceBuffer[52] shouldBe sentinel }

        withClue("the block starting after the end") { block(v, 2176.0) shouldBe false }
        v.state shouldBe State.Done
        withClue("a done block renders nothing") { untouched() shouldBe true }
    }

    "Zombie at the end of the release block that completes the cull window, never inside the gate" {
        // Silent 10 ms (480 frames) into a 1280-frame gate; the cull window is 0.008 s = 384 frames = 3 blocks.
        val percussive = Voice.Envelope(attackFrames = 0.0, decayFrames = 480.0, sustainLevel = 0.0, releaseFrames = 4096.0)
        val v = voice(start = 0.0, gate = 1280.0, end = 5376.0, cull = 0.008, envelope = percussive)

        var start = 0.0

        while (start < 1280.0) {
            block(v, start)
            withClue("block $start: silent but inside the gate, never culled") { v.state shouldBe State.Sounding }
            start += blockFrames
        }

        block(v, 1280.0)
        withClue("first silent release block") { v.state shouldBe State.Releasing }
        block(v, 1408.0)
        withClue("second silent release block, 256 of 384 frames") { v.state shouldBe State.Releasing }
        block(v, 1536.0)
        withClue("third silent release block completes the window") { v.state shouldBe State.Zombie }
        withClue("a zombie is still alive") { v.culled shouldBe true }
    }

    "a zombie never renders again, ignores a note-off, and turns Done exactly at its endFrame" {
        val percussive = Voice.Envelope(attackFrames = 0.0, decayFrames = 480.0, sustainLevel = 0.0, releaseFrames = 4096.0)
        val v = voice(start = 0.0, gate = 1280.0, end = 5376.0, cull = 0.0, envelope = percussive)
        var start = 0.0

        while (v.state != State.Zombie) {
            withClue("must turn zombie before its end") { (start < 5376.0) shouldBe true }
            block(v, start)
            start += blockFrames
        }

        // Through the scheduler a zombie's note-off is a no-op anyway; called directly with a frame before its gate
        // end it would move the end earlier, and the zombie must ignore it.
        v.releaseGate(1000.0)
        withClue("the note-off left the end where it was") { v.endFrame shouldBe 5376.0 }

        while (start < 5376.0) {
            withClue("block $start: a zombie stays alive") { block(v, start) shouldBe true }
            withClue("block $start: zombie") { v.state shouldBe State.Zombie }
            withClue("block $start: a zombie renders nothing") { untouched() shouldBe true }
            start += blockFrames
        }

        withClue("the block starting on the end") { block(v, 5376.0) shouldBe false }
        v.state shouldBe State.Done
        withClue("done, the zombie reading is off") { v.culled shouldBe false }
    }

    "Done is never left: not by an earlier block, not by a note-off that would move the end later" {
        // A raw-Motor negative release: the end (1536) lies before the gate (2048). A note-off at 1800 would move
        // the end LATER, to 1800, on a voice that is not done.
        val v = voice(start = 0.0, gate = 2048.0, end = 1536.0)

        block(v, 1536.0) shouldBe false
        v.state shouldBe State.Done

        v.releaseGate(1800.0)
        withClue("a done voice ignores the note-off") { v.endFrame shouldBe 1536.0 }

        withClue("an earlier block does not wake it") { block(v, 0.0) shouldBe false }
        v.state shouldBe State.Done
        withClue("and it renders nothing") { untouched() shouldBe true }
    }

    "onset, gate end and end in one block: Sounding for that block, its frames only, Done at the next" {
        val v = voice(start = 20.0, gate = 50.0, end = 90.0)

        block(v, 0.0) shouldBe true
        withClue("the state describes the block's start, which is before the gate end") { v.state shouldBe State.Sounding }
        withClue("frame 19 is before the onset") { ctx.voiceBuffer[19] shouldBe sentinel }
        withClue("frame 20 is the onset") { ctx.voiceBuffer[20] shouldBe 1.0 }
        withClue("frame 89 is the last frame") { ctx.voiceBuffer[89] shouldBe 1.0 }
        withClue("frame 90 is the end") { ctx.voiceBuffer[90] shouldBe sentinel }

        block(v, 128.0) shouldBe false
        v.state shouldBe State.Done
    }

    "a first render after the gate end passes from Pending through Sounding to Releasing in one call" {
        // A voice first rendered late (its onset and its gate end both lie before the block): the release state
        // applies to that very block.
        val v = voice(start = 0.0, gate = 128.0, end = 8192.0)

        block(v, 256.0) shouldBe true
        v.state shouldBe State.Releasing
        withClue("and it renders") { ctx.voiceBuffer[0] shouldBe 1.0 }
    }

    "a realtime note-off moves a Sounding voice to Releasing at the next block" {
        // A held realtime voice: its gate is a far horizon.
        val v = voice(start = 0.0, gate = 1_000_000.0, end = 1_004_800.0)

        block(v, 0.0)
        block(v, 128.0)
        withClue("held") { v.state shouldBe State.Sounding }

        // The scheduler releases at its cursor, the next block's first frame.
        v.releaseGate(256.0)
        withClue("the release span is kept") { v.endFrame shouldBe 256.0 + 4800.0 }
        withClue("the state moves with the next block, not with the call") { v.state shouldBe State.Sounding }

        block(v, 256.0)
        v.state shouldBe State.Releasing

        // A note-off inside a block: that block is still sounding.
        val w = voice(start = 0.0, gate = 1_000_000.0, end = 1_004_800.0)

        block(w, 0.0)
        w.releaseGate(200.0)
        block(w, 128.0)
        withClue("the block [128, 256) holds the new gate end") { w.state shouldBe State.Sounding }
        block(w, 256.0)
        w.state shouldBe State.Releasing
        withClue("still rendering") { ctx.voiceBuffer[0] shouldNotBe sentinel }
    }

    "a realtime note-off reaches every gate consumer through the voice's limits: it renders what a voice scheduled with that gate renders" {
        // Every gate consumer at once (step 2, "amendment A1"): the ignitor door's own envelope (it reads the
        // voice-relative gate the ignite stage derives per block), the pitch envelope and the FM envelope (they read
        // the limits through the block context), the state (Releasing) and the end (the render window). The source
        // echoes the pitch modulation (the product of the pitch envelope and the FM multiplier), and the tree's own
        // linear envelope scales it, so the output carries all three gate readers.
        // The voice-relative gate the ignitors saw on the last generate call (both voices share the echo; the
        // released voice renders second, so after its render this is its value).
        var seenGate = -1

        val echo = object : Ignitor {
            override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
                seenGate = ctx.gateEndFrame

                val mod = ctx.phaseMod

                for (i in ctx.offset until ctx.windowEnd) {
                    buffer[i] = if (mod == null) 1.0 else mod[i]
                }
            }
        }
        val lin = AdsrCurve.Linear
        val span = 2048.0
        val gate = 1024.0

        fun withGate(scheduledGate: Double): Voice = createVoice(
            startFrame = 0.0, gateEndFrame = scheduledGate, endFrame = scheduledGate + span,
            sampleRate = sampleRate, blockFrames = blockFrames, cull = VOICE_CULL_NEVER, signal = echo,
            envelope = Voice.Envelope(0.0, 0.0, 1.0, span, lin, lin, lin),
            pitchEnvelope = Voice.PitchEnvelope(semitones = 12.0, envelope = Voice.Envelope(0.0, 0.0, 1.0, 1024.0, lin, lin, lin)),
            // A fresh FM per voice: the modulator phase lives on it.
            fm = Voice.Fm(ratio = 1.0, depth = 100.0, envelope = Voice.Envelope(0.0, 0.0, 1.0, 0.0)),
        )

        val reference = withGate(gate)
        val released = withGate(1_000_000.0)
        val refCtx = createContext(blockStart = 0.0, blockFrames = blockFrames, sampleRate = sampleRate)
        val relCtx = createContext(blockStart = 0.0, blockFrames = blockFrames, sampleRate = sampleRate)
        var start = 0.0
        var heldPeak = 0.0
        var midReleasePeak = 0.0

        while (start < gate + span + 2 * blockFrames) {
            if (start == gate) {
                // The scheduler releases at its cursor, the first frame of the next block.
                released.releaseGate(gate)
            }

            refCtx.blockStart = start
            relCtx.blockStart = start
            refCtx.voiceBuffer.fill(0.0)
            relCtx.voiceBuffer.fill(0.0)

            val refAlive = reference.render(refCtx)
            val relAlive = released.render(relCtx)

            // The derived gate itself, as literals: both voices derive it the same way, so the comparison below
            // alone could not see a wrong derivation.
            if (start == 0.0) {
                withClue("the held gate, voice-relative") { seenGate shouldBe 1_000_000 }
            }

            if (start == gate) {
                withClue("the moved gate, voice-relative") { seenGate shouldBe 1024 }
            }

            withClue("block $start: alive alike") { relAlive shouldBe refAlive }
            withClue("block $start: same state") { released.state shouldBe reference.state }

            for (i in 0 until blockFrames) {
                withClue("frame ${start + i}") { relCtx.voiceBuffer[i].toRawBits() shouldBe refCtx.voiceBuffer[i].toRawBits() }
            }

            val peak = refCtx.voiceBuffer.maxOf { abs(it) }

            if (start < gate) {
                heldPeak = maxOf(heldPeak, peak)
            }

            if (start == gate + span / 2) {
                midReleasePeak = peak
            }

            start += blockFrames
        }

        withClue("the reference really releases (or the comparison proves nothing)") {
            (midReleasePeak < heldPeak * 0.6) shouldBe true
        }
        withClue("and ends at gate + span") { released.state shouldBe State.Done }
    }
})
