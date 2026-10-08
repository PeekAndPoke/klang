/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.cylinders.offerAndCommit
import io.peekandpoke.klang.audio_be.ignitor.IgniteContext
import io.peekandpoke.klang.audio_be.ignitor.Ignitor
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.createContext
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.createVoice
import io.peekandpoke.klang.audio_bridge.BodyMaterials
import io.peekandpoke.klang.audio_bridge.constants.VOICE_CULL_NEVER
import io.peekandpoke.klang.audio_bridge.constants.VOICE_CULL_SECONDS
import kotlin.math.abs

/**
 * Silence culling: a voice whose release has stayed under the audibility floor for the cull
 * window ends there (`Voice.culled`, `Done` at the end of that block): it renders nothing more and
 * leaves the scheduler's list at once, long before its scheduled end (lifecycle step 5 retired the zombie). The gate is never culled; an audible
 * release is never culled; the solo/mute fade is not silence.
 *
 * The voices here are a constant signal through the amp VCA. A `sustain = 0` envelope with a
 * short decay goes silent 10 ms into a 100 ms gate, then carries a one-second release: the shape
 * of every percussive note in the songs, with the tail exaggerated.
 */
class VoiceCullingSpec : StringSpec({

    val sampleRate = 48000
    val gateEndFrame = 4800.0                           // 100 ms gate
    val releaseFrames = 48000.0                         // 1 s scheduled tail
    val endFrame = gateEndFrame + releaseFrames
    val defaultWindowFrames = VOICE_CULL_SECONDS * sampleRate

    /** Decays to silence 10 ms in, stays silent: the percussive shape. */
    fun percussive(releaseFrames: Double) = Voice.Envelope(
        attackFrames = 0.0, decayFrames = 480.0, sustainLevel = 0.0, releaseFrames = releaseFrames,
    )

    /** Holds full level through the gate, then releases: audible until the release ends. */
    fun held(releaseFrames: Double) = Voice.Envelope(
        attackFrames = 0.0, decayFrames = 0.0, sustainLevel = 1.0, releaseFrames = releaseFrames,
    )

    fun voice(envelope: Voice.Envelope, cull: Double?, blockFrames: Int = 128, end: Double = endFrame) = createVoice(
        startFrame = 0.0, gateEndFrame = gateEndFrame, endFrame = end,
        sampleRate = sampleRate, blockFrames = blockFrames, envelope = envelope, cull = cull,
    )

    /** How a voice ended: the frame (see [cullFrame]) and whether it was culled on the way. */
    data class Ending(val frame: Double, val culled: Boolean)

    /**
     * Renders block after block until the voice reports itself finished. Returns the start frame of
     * that block, the block the cull completed on ([Voice.culled]) or the one it expired on, and
     * [Ending.culled] tells the two apart. Also asserts: a voice that was not culled never ends before
     * its scheduled end, and a culled one ends before it.
     */
    fun cullFrame(voice: Voice, blockFrames: Int = 128, end: Double = endFrame): Ending {
        val ctx = createContext(blockStart = 0.0, blockFrames = blockFrames, sampleRate = sampleRate)
        var start = 0.0

        while (start < end + 10 * blockFrames) {
            ctx.blockStart = start
            val alive = voice.render(ctx)

            if (!alive) {
                if (voice.culled) {
                    withClue("a culled voice ends before its scheduled end") { (start < end) shouldBe true }
                } else {
                    withClue("a voice that was not culled expires at its scheduled end") { start shouldBeGreaterThanOrEqualTo end }
                }

                return Ending(frame = start, culled = voice.culled)
            }

            start += blockFrames
        }

        error("the voice never finished")
    }

    "a silent release is culled once the default window has elapsed, never inside the gate" {
        val v = voice(percussive(releaseFrames), cull = null)
        val (death, culled) = cullFrame(v)

        withClue("culled") { culled shouldBe true }
        // Silent from 10 ms on, but the gate lasts 100 ms: the window only starts counting there.
        // The voice ends ON the block that completes the window, so its start is up to one block early.
        withClue("never inside the gate") { death shouldBeGreaterThanOrEqualTo gateEndFrame + defaultWindowFrames - 128 }
        withClue("as soon as the window has elapsed") { death shouldBeLessThanOrEqualTo gateEndFrame + defaultWindowFrames + 128 }
    }

    "cull(seconds) sets the window" {
        val death = cullFrame(voice(percussive(releaseFrames), cull = 0.2)).frame

        death shouldBeGreaterThanOrEqualTo gateEndFrame + 0.2 * sampleRate - 128
        death shouldBeLessThanOrEqualTo gateEndFrame + 0.2 * sampleRate + 128
    }

    "cull(0) ends the voice on the first silent block of the release" {
        val death = cullFrame(voice(percussive(releaseFrames), cull = 0.0)).frame

        death shouldBeGreaterThanOrEqualTo gateEndFrame
        death shouldBeLessThanOrEqualTo gateEndFrame + 2 * 128
    }

    "noCull (a negative window) renders the whole scheduled tail" {
        val v = voice(percussive(releaseFrames), cull = VOICE_CULL_NEVER)
        val (death, culled) = cullFrame(v)

        withClue("expired, not culled") { culled shouldBe false }
        death shouldBeGreaterThanOrEqualTo endFrame
    }

    "a NaN window falls back to the default" {
        val death = cullFrame(voice(percussive(releaseFrames), cull = Double.NaN)).frame

        death shouldBeGreaterThanOrEqualTo gateEndFrame + defaultWindowFrames - 128
        death shouldBeLessThanOrEqualTo gateEndFrame + defaultWindowFrames + 128
    }

    "an audible release is not culled" {
        // Full level through the gate, then a 50 ms ramp to zero: never silent for a whole window
        // before the scheduled end.
        val shortRelease = 2400.0
        val v = voice(held(shortRelease), cull = null, end = gateEndFrame + shortRelease)
        val (death, culled) = cullFrame(v, end = gateEndFrame + shortRelease)

        withClue("expired, not culled") { culled shouldBe false }
        death shouldBeGreaterThanOrEqualTo gateEndFrame + shortRelease
    }

    "the solo/mute fade is not silence" {
        // A loud voice faded to nothing by the scheduler's solo multiplier keeps its tail: the
        // peak is measured before the multiplier, so un-soloing later still finds it playing.
        val v = voice(held(releaseFrames), cull = null)
        v.setGainMultiplier(0.0)
        val (death, culled) = cullFrame(v)

        withClue("expired, not culled") { culled shouldBe false }
        death shouldBeGreaterThanOrEqualTo endFrame
    }

    "the window has the same length at any block size: the cut lands within one block of the same frame" {
        val death128 = cullFrame(voice(percussive(releaseFrames), cull = null, blockFrames = 128), blockFrames = 128).frame
        val death64 = cullFrame(voice(percussive(releaseFrames), cull = null, blockFrames = 64), blockFrames = 64).frame

        // The window is counted in frames, so the two can only differ by the block granularity.
        abs(death128 - death64) shouldBeLessThanOrEqualTo 128.0
    }

    "a phase-inverted voice (raw-Motor gain -1) is as audible as an upright one" {
        val v = createVoice(
            startFrame = 0.0, gateEndFrame = gateEndFrame, endFrame = endFrame,
            sampleRate = sampleRate, blockFrames = 128, envelope = held(releaseFrames), cull = null,
            gain = -1.0,
        )
        val (death, culled) = cullFrame(v)

        withClue("expired, not culled") { culled shouldBe false }
        death shouldBeGreaterThanOrEqualTo endFrame
    }

    "an audible block inside the release restarts the window" {
        // Audible in its first block (so the `heard` latch is set and the release counts from the gate end),
        // silent from there on, one 256-frame burst 2000 frames into the release (inside the 2400-frame window),
        // silent again: the window must start over after the burst. Without the first block the voice would be
        // unheard until the burst and the count would start there anyway, reset or not (lifecycle step 5b).
        val burstStart = gateEndFrame.toInt() + 2000
        val burstEnd = burstStart + 256
        val burst = object : Ignitor {
            override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
                val end = ctx.windowEnd

                for (i in ctx.offset until end) {
                    val frame = ctx.voiceElapsedFrames + (i - ctx.offset)
                    buffer[i] = if (frame < 128 || frame in burstStart until burstEnd) 1.0 else 0.0
                }
            }
        }
        val v = createVoice(
            startFrame = 0.0, gateEndFrame = gateEndFrame, endFrame = endFrame,
            sampleRate = sampleRate, blockFrames = 128, envelope = held(releaseFrames), cull = null,
            signal = burst,
        )
        val (death, culled) = cullFrame(v)

        withClue("culled, after the burst") { culled shouldBe true }
        death shouldBeGreaterThanOrEqualTo burstEnd + defaultWindowFrames - 128
        death shouldBeLessThanOrEqualTo burstEnd + defaultWindowFrames + 128
    }
    "a culled voice ends at the block that completes the window: Done, and nothing renders after it" {
        val v = voice(percussive(releaseFrames), cull = 0.0)
        val ctx = createContext(blockStart = 0.0, blockFrames = 128, sampleRate = sampleRate)
        var start = 0.0

        while (v.render(ctx.also { it.blockStart = start })) {
            withClue("must be culled before its scheduled end") { (start < endFrame) shouldBe true }
            start += 128
        }

        withClue("culled, not expired") { v.culled shouldBe true }
        v.state shouldBe Voice.State.Done
        withClue("long before its scheduled end") { (start < endFrame - 10_000) shouldBe true }

        ctx.voiceBuffer.fill(0.5)
        ctx.blockStart = start + 128
        withClue("stays done") { v.render(ctx) shouldBe false }
        withClue("and renders nothing") { ctx.voiceBuffer.all { it == 0.5 } shouldBe true }
    }

    "a voice that has not sounded yet is never culled, however long past its gate" {
        // Silent through the whole gate and well past the default window, then audible: a sample
        // with leading silence pitched down, or an ignitor attack outliving a short gate.
        val onset = gateEndFrame.toInt() + 4000
        val late = object : Ignitor {
            override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
                val end = ctx.windowEnd

                for (i in ctx.offset until end) {
                    val frame = ctx.voiceElapsedFrames + (i - ctx.offset)
                    buffer[i] = if (frame >= onset) 1.0 else 0.0
                }
            }
        }
        val v = createVoice(
            startFrame = 0.0, gateEndFrame = gateEndFrame, endFrame = endFrame,
            sampleRate = sampleRate, blockFrames = 128, envelope = held(releaseFrames), cull = null,
            signal = late,
        )
        val (death, culled) = cullFrame(v)

        withClue("audible from its late onset to its scheduled end: expired, not culled") { culled shouldBe false }
        death shouldBeGreaterThanOrEqualTo endFrame
    }

    "a culled voice leaves its orbit: a later voice with its own bus config owns it at once" {
        val culledVoice = voice(percussive(releaseFrames), cull = 0.0)
        val challenger = createVoice(
            startFrame = 0.0, gateEndFrame = gateEndFrame, endFrame = endFrame,
            sampleRate = sampleRate, blockFrames = 128, envelope = held(releaseFrames), cull = null,
            // The orbit's body reads the SLOT state (Katalyst step 5b-1): a material INDEX from
            // the shared catalogue plus its mix, which is what `.body(material = "wood", wet = 1)` writes.
            katalystParams = mapOf("body.material" to BodyMaterials.indexOf("wood"), "body.wet" to 1.0),
        )
        val ctx = createContext(blockStart = 0.0, blockFrames = 128, sampleRate = sampleRate)
        var start = 0.0

        while (culledVoice.render(ctx.also { it.blockStart = start })) {
            start += 128
        }

        val cylinder = ctx.cylinders.cylinders.first()

        withClue("nobody owns the orbit any more") { cylinder.body!!.isEngaged shouldBe false }

        ctx.cylinders.offerAndCommit(challenger.cylinderId, challenger, start + 128)
        withClue("the challenger owns at once") { cylinder.body!!.isEngaged shouldBe true }
    }

    "a hand-muted voice (gain 0) can never be heard and is culled like any silent tail" {
        val v = createVoice(
            startFrame = 0.0, gateEndFrame = gateEndFrame, endFrame = endFrame,
            sampleRate = sampleRate, blockFrames = 128, envelope = held(releaseFrames), cull = null,
            gain = 0.0,
        )
        val (death, culled) = cullFrame(v)

        withClue("culled") { culled shouldBe true }
        death shouldBeLessThanOrEqualTo gateEndFrame + defaultWindowFrames + 128
    }
})
