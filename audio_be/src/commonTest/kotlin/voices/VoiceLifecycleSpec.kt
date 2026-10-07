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
import kotlin.math.ceil

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

    "a hard kill ends a voice in Done from every state, and nothing renders after it" {
        val percussive = Voice.Envelope(attackFrames = 0.0, decayFrames = 480.0, sustainLevel = 0.0, releaseFrames = 4096.0)

        /** A voice rendered block by block from frame 0 until it is in [target]. */
        fun inState(target: State): Pair<Voice, Double> {
            val v = when (target) {
                State.Pending -> voice(start = 1024.0, gate = 4096.0, end = 8192.0)
                State.Zombie -> voice(start = 0.0, gate = 1280.0, end = 5376.0, cull = 0.0, envelope = percussive)
                else -> voice(start = 0.0, gate = 1024.0, end = 4096.0)
            }
            var start = 0.0

            block(v, start)

            if (target == State.Fading) {
                v.cutOff(blockFrames.toDouble())
            }

            while (v.state != target) {
                start += blockFrames
                withClue("reaching $target") { (start < 8192.0) shouldBe true }
                block(v, start)
            }

            return v to start + blockFrames
        }

        for (from in State.entries) {
            val (v, next) = inState(from)
            val endBefore = v.endFrame

            v.kill()
            withClue("$from: killed") { v.state shouldBe State.Done }

            withClue("$from: the next render ends it") { block(v, next) shouldBe false }
            withClue("$from: and renders nothing") { untouched() shouldBe true }
            v.state shouldBe State.Done

            v.releaseGate(next)
            withClue("$from: a note-off after the kill is ignored") { v.endFrame shouldBe endBefore }
        }
    }

    "a note-off does what the state says: Pending and Sounding take it, Releasing, Zombie and Done ignore it" {
        // Pending: the gate and the end move (the scheduler floors a note-off at onset + one block).
        val pending = voice(start = 1024.0, gate = 1_000_000.0, end = 1_004_800.0)

        block(pending, 0.0)
        pending.releaseGate(2048.0)
        withClue("Pending: the end moves with the gate") { pending.endFrame shouldBe 2048.0 + 4800.0 }
        withClue("Pending: still pending") { pending.state shouldBe State.Pending }
        block(pending, 1024.0)
        withClue("Pending: sounds at its onset") { pending.state shouldBe State.Sounding }
        block(pending, 2048.0)
        withClue("Pending: releases at the moved gate") { pending.state shouldBe State.Releasing }

        // Sounding: the gate and the end move, Releasing from the next block.
        val sounding = voice(start = 0.0, gate = 1_000_000.0, end = 1_004_800.0)

        block(sounding, 0.0)
        sounding.releaseGate(128.0)
        withClue("Sounding: the end moves") { sounding.endFrame shouldBe 128.0 + 4800.0 }
        block(sounding, 128.0)
        withClue("Sounding: releases") { sounding.state shouldBe State.Releasing }

        // Releasing: already released. Called directly with a frame before its gate it would move the end.
        val releasing = voice(start = 0.0, gate = 1024.0, end = 5824.0)

        block(releasing, 0.0)
        block(releasing, 1024.0)
        withClue("Releasing: in its release") { releasing.state shouldBe State.Releasing }
        releasing.releaseGate(512.0)
        withClue("Releasing: ignored, the end stays") { releasing.endFrame shouldBe 5824.0 }

        // Zombie and Done: terminal (their rows above cover the details).
        val percussive = Voice.Envelope(attackFrames = 0.0, decayFrames = 480.0, sustainLevel = 0.0, releaseFrames = 4096.0)
        val zombie = voice(start = 0.0, gate = 1280.0, end = 5376.0, cull = 0.0, envelope = percussive)
        var start = 0.0

        while (zombie.state != State.Zombie) {
            block(zombie, start)
            start += blockFrames
        }

        zombie.releaseGate(1000.0)
        withClue("Zombie: ignored") { zombie.endFrame shouldBe 5376.0 }

        val done = voice(start = 0.0, gate = 128.0, end = 256.0)

        block(done, 256.0) shouldBe false
        done.releaseGate(200.0)
        withClue("Done: ignored") { done.endFrame shouldBe 256.0 }
    }

    // ── The cut (lifecycle step 4): Fading ────────────────────────────────────────────────────────────

    // At 48 kHz the cut fade is 0.004 s = 192 frames. A cut whose cutting voice begins at frame 200 (inside the
    // block [128, 256)) has its fade end at 392; the ramp reaches exact zero on frame 391, the last frame before
    // the fade end (the teardown's rule: zero on the last frame that renders), so it runs 191 steps.
    val cutAt = 200.0
    val fadeEnd = 392.0
    val zeroAt = 391

    /** The cut's gain at absolute frame [f] for a block starting at [blockStart]: the law, written out here. */
    fun cutGain(f: Int, blockStart: Double, start: Double = cutAt, end: Double = fadeEnd): Double {
        val zero = ceil(end) - 1.0
        val remaining = ((zero - blockStart) - (f - blockStart.toInt())) * (1.0 / (zero - start))

        return if (remaining < 0.0) 0.0 else if (remaining > 1.0) 1.0 else remaining
    }

    /** A held constant voice (1.0, no envelope, never culled), cut after its first block. */
    fun cutVoice(): Voice {
        val v = voice(start = 0.0, gate = 1_000_000.0, end = 1_004_800.0)

        block(v, 0.0)
        v.cutOff(cutAt)

        return v
    }

    "a cut fades a sounding voice from the cutting onset, mid-block too, to exact zero, then Done" {
        val v = cutVoice()

        withClue("Fading at once") { v.state shouldBe State.Fading }

        for (start in listOf(128.0, 256.0, 384.0)) {
            withClue("block $start renders") { block(v, start) shouldBe true }
            withClue("block $start: fading") { v.state shouldBe State.Fading }

            for (i in 0 until blockFrames) {
                val f = start.toInt() + i
                val out = ctx.voiceBuffer[i]

                when {
                    f < cutAt -> withClue("frame $f: before the cutting onset, untouched") { out shouldBe 1.0 }
                    f >= zeroAt -> withClue("frame $f: exact zero from the last frame before the fade end") { (out == 0.0) shouldBe true }
                    else -> {
                        withClue("frame $f: on the linear ramp") { (abs(out - (zeroAt - f) / 191.0) < 1e-12) shouldBe true }
                        withClue("frame $f: the law, bit for bit") { out.toRawBits() shouldBe cutGain(f, start).toRawBits() }
                    }
                }
            }
        }

        withClue("Done from the first block that starts at or after the fade end") { block(v, 512.0) shouldBe false }
        v.state shouldBe State.Done
        withClue("and renders nothing") { untouched() shouldBe true }
    }

    "a fade end on a block start: the block before it renders the ramp's zero, and the voice is Done at it" {
        // Cut at 64: the fade ends exactly on 256, a block start. Frame 255 is the last frame that renders and the
        // ramp's zero; a voice that ended one block later, or a ramp aimed at 256, would miss it.
        val v = voice(start = 0.0, gate = 1_000_000.0, end = 1_004_800.0)

        block(v, 0.0)
        v.cutOff(64.0)
        block(v, 128.0) shouldBe true
        withClue("the last rendered frame is exact zero") { (ctx.voiceBuffer[127] == 0.0) shouldBe true }
        withClue("one step before it, one ramp step") { (abs(ctx.voiceBuffer[126] - 1.0 / 191.0) < 1e-12) shouldBe true }
        withClue("Done at the block that starts on the fade end") { block(v, 256.0) shouldBe false }
    }

    "a cut with a non-finite fade start ends the voice at once" {
        for (bad in listOf(Double.NaN, Double.POSITIVE_INFINITY)) {
            val v = voice(start = 0.0, gate = 1_000_000.0, end = 1_004_800.0)

            block(v, 0.0)
            v.cutOff(bad)
            withClue("$bad: Done, no NaN fade") { v.state shouldBe State.Done }
            withClue("$bad: renders nothing") { block(v, 128.0) shouldBe false }
        }
    }

    "a cut does not click: no step between frames larger than the ramp's slope" {
        val v = cutVoice()
        var prev = 1.0
        var maxStep = 0.0

        for (start in listOf(128.0, 256.0, 384.0)) {
            block(v, start)

            for (i in 0 until blockFrames) {
                maxStep = maxOf(maxStep, abs(ctx.voiceBuffer[i] - prev))
                prev = ctx.voiceBuffer[i]
            }
        }

        // A hard cut steps by the full 1.0; the ramp moves 1/191 per frame.
        (maxStep <= 1.0 / 191.0 + 1e-12) shouldBe true
        (maxStep > 0.0) shouldBe true
    }

    "a cut's sends fade too: the orbit mix carries the ramp" {
        val v = cutVoice()
        val cylinder = ctx.cylinders.getOrInit(v.cylinderId, v, 0.0)
        val held = cylinder.mixBuffer.left[100]

        withClue("the held voice reaches its orbit") { (held > 0.1) shouldBe true }

        for (start in listOf(128.0, 256.0, 384.0)) {
            cylinder.mixBuffer.left.fill(0.0)
            cylinder.mixBuffer.right.fill(0.0)
            block(v, start)

            for (i in 0 until blockFrames) {
                val f = start.toInt() + i
                val mix = cylinder.mixBuffer.left[i]

                if (f >= zeroAt) {
                    withClue("frame $f: the send is silent from the ramp's zero") { (mix == 0.0) shouldBe true }
                }

                if (f == 296) {
                    withClue("frame 296: half way down the ramp, in the send too") { (abs(mix - held * 95.0 / 191.0) < 1e-9) shouldBe true }
                }
            }
        }
    }

    "a cut on a Pending voice and on a Zombie sends it straight to Done" {
        val pending = voice(start = 1024.0, gate = 4096.0, end = 8192.0)

        block(pending, 0.0)
        pending.cutOff(512.0)
        withClue("Pending: Done") { pending.state shouldBe State.Done }
        withClue("Pending: never sounds") { block(pending, 1024.0) shouldBe false }
        withClue("Pending: renders nothing") { untouched() shouldBe true }

        val percussive = Voice.Envelope(attackFrames = 0.0, decayFrames = 480.0, sustainLevel = 0.0, releaseFrames = 4096.0)
        val zombie = voice(start = 0.0, gate = 1280.0, end = 5376.0, cull = 0.0, envelope = percussive)
        var start = 0.0

        while (zombie.state != State.Zombie) {
            block(zombie, start)
            start += blockFrames
        }

        zombie.cutOff(start)
        withClue("Zombie: Done") { zombie.state shouldBe State.Done }
        withClue("Zombie: render ends it") { block(zombie, start) shouldBe false }
    }

    "a second cut on a Fading voice changes nothing" {
        val once = cutVoice()
        val twice = cutVoice()

        twice.cutOff(300.0)
        withClue("still fading") { twice.state shouldBe State.Fading }

        for (start in listOf(128.0, 256.0, 384.0)) {
            block(once, start)

            val reference = ctx.voiceBuffer.copyOf()

            block(twice, start)

            for (i in 0 until blockFrames) {
                withClue("frame ${start + i}") { ctx.voiceBuffer[i].toRawBits() shouldBe reference[i].toRawBits() }
            }
        }
    }

    "a note-off during Fading is ignored" {
        val v = cutVoice()

        v.releaseGate(256.0)
        withClue("the end stays") { v.endFrame shouldBe 1_004_800.0 }
        withClue("still fading") { v.state shouldBe State.Fading }
        block(v, 256.0)
        withClue("the ramp goes on: frame 300 is on it") { (abs(ctx.voiceBuffer[300 - 256] - (zeroAt - 300) / 191.0) < 1e-12) shouldBe true }
    }

    "a cut on a voice whose end falls inside the fade ends at its end: its own teardown stays where it was" {
        // Constant 1.0, no envelope, the teardown fade appended (as the factory does for a tree without its own
        // envelope): its window ends on frame 1099. Cut at 1000: the cut fade would end at 1192, after the end.
        fun withTeardown() = createVoice(
            startFrame = 0.0, gateEndFrame = 1000.0, endFrame = 1100.0,
            sampleRate = sampleRate, blockFrames = blockFrames, cull = VOICE_CULL_NEVER,
            treeStages = listOf(TeardownFadeRenderer),
        )

        val reference = withTeardown()
        val cut = withTeardown()

        for (start in listOf(0.0, 128.0, 256.0, 384.0, 512.0, 640.0, 768.0, 896.0, 1024.0)) {
            if (start == 896.0) {
                cut.cutOff(1000.0)
                withClue("fading") { cut.state shouldBe State.Fading }
            }

            block(reference, start)

            val ref = ctx.voiceBuffer.copyOf()

            block(cut, start)

            for (i in 0 until blockFrames) {
                val f = start.toInt() + i

                if (f >= 1100) {
                    withClue("frame $f: past the end, nothing") { ctx.voiceBuffer[i] shouldBe sentinel }
                } else {
                    val expected = ref[i] * cutGain(f, start, start = 1000.0, end = 1192.0)

                    withClue("frame $f: the uncut voice times the cut ramp, teardown unmoved") {
                        ctx.voiceBuffer[i].toRawBits() shouldBe expected.toRawBits()
                    }
                }
            }
        }

        withClue("the end did not move") { cut.endFrame shouldBe 1100.0 }
        withClue("Done at the first block at or after its own end, not the fade end") { block(cut, 1152.0) shouldBe false }
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
