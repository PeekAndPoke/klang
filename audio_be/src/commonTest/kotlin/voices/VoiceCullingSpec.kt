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
import io.peekandpoke.klang.audio_be.ignitor.IgniteContext
import io.peekandpoke.klang.audio_be.ignitor.Ignitor
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.createContext
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.createVoice
import io.peekandpoke.klang.audio_bridge.BodyMaterials
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.audio_bridge.constants.VOICE_CULL_FLOOR
import io.peekandpoke.klang.audio_bridge.constants.VOICE_CULL_NEVER
import io.peekandpoke.klang.audio_bridge.constants.VOICE_CULL_SECONDS
import kotlin.math.abs

/**
 * Silence culling: a voice whose release has stayed under the audibility floor for the cull
 * window turns into a zombie (`Voice.culled`): it renders nothing more, keeps its active-list slot
 * and its orbit lease, and expires at its scheduled end. The gate is never culled; an audible
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
        attackFrames = 0.0, decayFrames = 480.0, sustainLevel = 0.0, releaseFrames = releaseFrames, level = 1.0,
    )

    /** Holds full level through the gate, then releases: audible until the release ends. */
    fun held(releaseFrames: Double) = Voice.Envelope(
        attackFrames = 0.0, decayFrames = 0.0, sustainLevel = 1.0, releaseFrames = releaseFrames, level = 1.0,
    )

    fun voice(envelope: Voice.Envelope, cull: Double?, blockFrames: Int = 128, end: Double = endFrame) = createVoice(
        startFrame = 0.0, gateEndFrame = gateEndFrame, endFrame = end,
        sampleRate = sampleRate, blockFrames = blockFrames, envelope = envelope, cull = cull,
    )

    /**
     * Renders block after block until the voice reports itself finished. Returns the start frame of
     * the block on which it turned into a zombie ([Voice.culled]), or the frame it expired on when it
     * never did; [Voice.culled] tells the two apart. Also asserts the zombie contract: a culled voice
     * stays alive (render returns true) until its scheduled end, and the voice never expires early.
     */
    fun cullFrame(voice: Voice, blockFrames: Int = 128, end: Double = endFrame): Double {
        val ctx = createContext(blockStart = 0.0, blockFrames = blockFrames, sampleRate = sampleRate)
        var start = 0.0
        var culledAt = -1.0

        while (start < end + 10 * blockFrames) {
            ctx.blockStart = start
            val alive = voice.render(ctx)

            if (!alive) {
                withClue("a voice expires at its scheduled end, culled or not") { start shouldBeGreaterThanOrEqualTo end }

                return if (culledAt >= 0.0) culledAt else start
            }

            if (voice.culled && culledAt < 0.0) {
                culledAt = start
            }

            start += blockFrames
        }

        error("the voice never finished")
    }

    "a silent release is culled once the default window has elapsed, never inside the gate" {
        val v = voice(percussive(releaseFrames), cull = null)
        val death = cullFrame(v)

        withClue("culled") { v.culled shouldBe true }
        // Silent from 10 ms on, but the gate lasts 100 ms: the window only starts counting there.
        // The voice ends ON the block that completes the window, so its start is up to one block early.
        withClue("never inside the gate") { death shouldBeGreaterThanOrEqualTo gateEndFrame + defaultWindowFrames - 128 }
        withClue("as soon as the window has elapsed") { death shouldBeLessThanOrEqualTo gateEndFrame + defaultWindowFrames + 128 }
    }

    "cull(seconds) sets the window" {
        val death = cullFrame(voice(percussive(releaseFrames), cull = 0.2))

        death shouldBeGreaterThanOrEqualTo gateEndFrame + 0.2 * sampleRate - 128
        death shouldBeLessThanOrEqualTo gateEndFrame + 0.2 * sampleRate + 128
    }

    "cull(0) ends the voice on the first silent block of the release" {
        val death = cullFrame(voice(percussive(releaseFrames), cull = 0.0))

        death shouldBeGreaterThanOrEqualTo gateEndFrame
        death shouldBeLessThanOrEqualTo gateEndFrame + 2 * 128
    }

    "noCull (a negative window) renders the whole scheduled tail" {
        val v = voice(percussive(releaseFrames), cull = VOICE_CULL_NEVER)
        val death = cullFrame(v)

        withClue("expired, not culled") { v.culled shouldBe false }
        death shouldBeGreaterThanOrEqualTo endFrame
    }

    "a NaN window falls back to the default" {
        val death = cullFrame(voice(percussive(releaseFrames), cull = Double.NaN))

        death shouldBeGreaterThanOrEqualTo gateEndFrame + defaultWindowFrames - 128
        death shouldBeLessThanOrEqualTo gateEndFrame + defaultWindowFrames + 128
    }

    "an audible release is not culled" {
        // Full level through the gate, then a 50 ms ramp to zero: never silent for a whole window
        // before the scheduled end.
        val shortRelease = 2400.0
        val v = voice(held(shortRelease), cull = null, end = gateEndFrame + shortRelease)
        val death = cullFrame(v, end = gateEndFrame + shortRelease)

        withClue("expired, not culled") { v.culled shouldBe false }
        death shouldBeGreaterThanOrEqualTo gateEndFrame + shortRelease
    }

    "the solo/mute fade is not silence" {
        // A loud voice faded to nothing by the scheduler's solo multiplier keeps its tail: the
        // peak is measured before the multiplier, so un-soloing later still finds it playing.
        val v = voice(held(releaseFrames), cull = null)
        v.setGainMultiplier(0.0)
        val death = cullFrame(v)

        withClue("expired, not culled") { v.culled shouldBe false }
        death shouldBeGreaterThanOrEqualTo endFrame
    }

    "the window has the same length at any block size: the cut lands within one block of the same frame" {
        val death128 = cullFrame(voice(percussive(releaseFrames), cull = null, blockFrames = 128), blockFrames = 128)
        val death64 = cullFrame(voice(percussive(releaseFrames), cull = null, blockFrames = 64), blockFrames = 64)

        // The window is counted in frames, so the two can only differ by the block granularity.
        abs(death128 - death64) shouldBeLessThanOrEqualTo 128.0
    }

    "a phase-inverted voice (raw-Motor gain -1) is as audible as an upright one" {
        val v = createVoice(
            startFrame = 0.0, gateEndFrame = gateEndFrame, endFrame = endFrame,
            sampleRate = sampleRate, blockFrames = 128, envelope = held(releaseFrames), cull = null,
            gain = -1.0,
        )
        val death = cullFrame(v)

        withClue("expired, not culled") { v.culled shouldBe false }
        death shouldBeGreaterThanOrEqualTo endFrame
    }

    "an audible block inside the release restarts the window" {
        // Silent from the gate on, one 256-frame burst 2000 frames into the release (inside the
        // 2400-frame window), silent again: the window must start over after the burst.
        val burstStart = gateEndFrame.toInt() + 2000
        val burstEnd = burstStart + 256
        val burst = object : Ignitor {
            override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
                val end = ctx.windowEnd

                for (i in ctx.offset until end) {
                    val frame = ctx.voiceElapsedFrames + (i - ctx.offset)
                    buffer[i] = if (frame in burstStart until burstEnd) 1.0 else 0.0
                }
            }
        }
        val v = createVoice(
            startFrame = 0.0, gateEndFrame = gateEndFrame, endFrame = endFrame,
            sampleRate = sampleRate, blockFrames = 128, envelope = held(releaseFrames), cull = null,
            signal = burst,
        )
        val death = cullFrame(v)

        withClue("culled, after the burst") { v.culled shouldBe true }
        death shouldBeGreaterThanOrEqualTo burstEnd + defaultWindowFrames - 128
        death shouldBeLessThanOrEqualTo burstEnd + defaultWindowFrames + 128
    }
    "a zombie renders nothing and renews its orbit lease until its scheduled end" {
        val v = voice(percussive(releaseFrames), cull = 0.0)
        val ctx = createContext(blockStart = 0.0, blockFrames = 128, sampleRate = sampleRate)
        var start = 0.0

        while (!v.culled) {
            withClue("must be culled before its scheduled end") { start shouldBeLessThanOrEqualTo endFrame }
            ctx.blockStart = start
            v.render(ctx) shouldBe true
            start += 128
        }

        // Zombie blocks: the voice buffer stays untouched (no strip ran) and the voice stays alive.
        ctx.voiceBuffer.fill(0.5)
        ctx.blockStart = start
        v.render(ctx) shouldBe true
        ctx.voiceBuffer.all { it == 0.5 } shouldBe true

        ctx.blockStart = endFrame
        withClue("expires at the scheduled end") { v.render(ctx) shouldBe false }
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
        val death = cullFrame(v)

        withClue("audible from its late onset to its scheduled end: expired, not culled") { v.culled shouldBe false }
        death shouldBeGreaterThanOrEqualTo endFrame
    }

    "the measured peak bounds the send buses, not only the mix" {
        // Loud through the gate (so the voice counts as heard), then a level just under the floor:
        // silent on the mix bus, but a x3 delay send carries it above the floor until the VCA's
        // release ramp has taken two thirds off. The send must delay the cull by far more than a window.
        val quiet = 0.9 * VOICE_CULL_FLOOR
        fun fading() = object : Ignitor {
            override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
                val end = ctx.windowEnd

                for (i in ctx.offset until end) {
                    val frame = ctx.voiceElapsedFrames + (i - ctx.offset)
                    buffer[i] = if (frame < gateEndFrame) 1.0 else quiet
                }
            }
        }
        fun voiceWith(delay: Voice.Delay) = createVoice(
            startFrame = 0.0, gateEndFrame = gateEndFrame, endFrame = endFrame,
            sampleRate = sampleRate, blockFrames = 128, envelope = held(releaseFrames), cull = null,
            signal = fading(), delay = delay,
        )
        val plain = voiceWith(Voice.Delay(amount = 0.0, time = 0.1, feedback = 0.0))
        val sent = voiceWith(Voice.Delay(amount = 3.0, time = 0.1, feedback = 0.0))
        val plainCull = cullFrame(plain)
        val sentCull = cullFrame(sent)

        withClue("both end up culled: the release ramp takes the send under the floor too") {
            plain.culled shouldBe true
            sent.culled shouldBe true
        }
        withClue("the plain voice is culled as soon as the window elapses") {
            plainCull shouldBeLessThanOrEqualTo gateEndFrame + defaultWindowFrames + 128
        }
        withClue("the delay send keeps the voice audible for a good part of the release") {
            sentCull - plainCull shouldBeGreaterThanOrEqualTo 4800.0
        }
    }

    "a zombie keeps its orbit lease: a later voice with its own bus config is refused" {
        // The zombie has no body; the challenger brings one. While the zombie renews its lease every
        // block the challenger's claim is denied and the orbit's body stays off. Two blocks without
        // the zombie and the lease lapses: the challenger takes over and the body engages.
        val zombie = voice(percussive(releaseFrames), cull = 0.0)
        val challenger = createVoice(
            startFrame = 0.0, gateEndFrame = gateEndFrame, endFrame = endFrame,
            sampleRate = sampleRate, blockFrames = 128, envelope = held(releaseFrames), cull = null,
            // The orbit's body reads the SLOT state (Katalyst step 5b-1): a material INDEX from
            // the shared catalogue plus its mix, which is what `.body("wood", wet = 1)` writes.
            katalystParams = mapOf("body.material" to BodyMaterials.indexOf("wood"), "body.wet" to 1.0),
        )
        val ctx = createContext(blockStart = 0.0, blockFrames = 128, sampleRate = sampleRate)
        var start = 0.0

        while (!zombie.culled) {
            withClue("must be culled before its scheduled end") { start shouldBeLessThanOrEqualTo endFrame }
            ctx.blockStart = start
            zombie.render(ctx)
            start += 128
        }

        val cylinder = ctx.cylinders.getOrInit(zombie.cylinderId, zombie, start - 128)

        repeat(4) {
            ctx.blockStart = start
            zombie.render(ctx)                                          // renews first, like the active list
            ctx.cylinders.getOrInit(challenger.cylinderId, challenger, start)
            withClue("block $it: the zombie holds the lease") { cylinder.body!!.isEngaged shouldBe false }
            start += 128
        }

        start += 2 * 128                                                // the zombie missed two blocks
        ctx.cylinders.getOrInit(challenger.cylinderId, challenger, start)
        withClue("the lease lapsed without the zombie") { cylinder.body!!.isEngaged shouldBe true }
    }
    "a hand-muted voice (gain 0) can never be heard and is culled like any silent tail" {
        val v = createVoice(
            startFrame = 0.0, gateEndFrame = gateEndFrame, endFrame = endFrame,
            sampleRate = sampleRate, blockFrames = 128, envelope = held(releaseFrames), cull = null,
            gain = 0.0,
        )
        val death = cullFrame(v)

        withClue("culled") { v.culled shouldBe true }
        death shouldBeLessThanOrEqualTo gateEndFrame + defaultWindowFrames + 128
    }
})
