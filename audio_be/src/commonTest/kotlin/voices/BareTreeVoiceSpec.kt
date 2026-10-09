/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_be.cylinders.offerAndCommit
import io.peekandpoke.klang.audio_be.ignitor.IgnitorRegistry
import io.peekandpoke.klang.audio_be.ignitor.PhasePools
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.ignitor.registerDefaults
import io.peekandpoke.klang.audio_bridge.AdsrCurve
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.adsr
import io.peekandpoke.klang.audio_bridge.classic
import io.peekandpoke.klang.audio_bridge.plus
import kotlin.random.Random

/**
 * **An authored instrument that does not end in `classic()` is played as its BARE TREE** (phase 3 step 9, the voice
 * strip retired): no voice envelope, no doors, no engine `onepole`. What the voice still adds around the tree is its
 * pitch pipeline, the channel (gain, pan, orbit), and, when the tree's root is not a built envelope with a static
 * release, the teardown fade over its last frames. Its lifetime is the tree's own release tail, or
 * `VOICE_ADSR_RELEASE_SEC` (0.05 s) past the gate when the tree reports none.
 *
 * The source is a CONSTANT 0.5, so every expectation below is arithmetic written here: the level the channel
 * leaves ([level], measured on a frame the rule does not touch), the teardown fade's linear ramp over its 192
 * frames at 48 kHz (`TEARDOWN_FADE_SECONDS`), and a linear envelope's release (`EnvelopeCore`: `floor(N)`
 * frames over `floor(N) - 1`, an exact 0.0 on the last one).
 */
class BareTreeVoiceSpec : StringSpec({

    val sampleRate = 48000
    val blockFrames = 128
    val gateSec = 0.25
    val gateFrame = (gateSec * sampleRate).toInt()

    val dc = IgnitorDsl.Constant(0.5)
    val linear = AdsrCurve.Linear

    val registry = IgnitorRegistry().apply {
        registerDefaults()
        // No envelope, no stage: the bare tree.
        register("bare", dc)
        // Its own envelope at the ROOT, a static release of 0.2 s, linear stages so the release is arithmetic.
        register("enveloped", dc.adsr(attack = 0.0, decay = 0.0, sustain = 1.0, release = 0.2, attackCurve = linear, decayCurve = linear, releaseCurve = linear))
        // The same with an exponential release of a FRACTIONAL frame count: 0.00501 s is 240.48 frames.
        register("envelopedexp", dc.adsr(attack = 0.0, decay = 0.0, sustain = 1.0, release = 0.00501, attackCurve = AdsrCurve.Exponential, decayCurve = AdsrCurve.Exponential, releaseCurve = AdsrCurve.Exponential))
        // classic() below the root: each branch has the voice chain, the root is a sum.
        register("branches", dc.classic().plus(dc.classic()))
    }

    fun render(data: VoiceData, blocks: Int = 140): DoubleArray {
        val factory = VoiceFactory(
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            voiceBuffer = DoubleArray(blockFrames),
            freqModBuffer = DoubleArray(blockFrames),
            scratchBuffers = ScratchBuffers(blockFrames),
        )
        val voice = factory.makeVoice(
            scheduled = ScheduledVoice(
                playbackId = "test",
                data = data,
                startTime = 0.0,
                gateEndTime = gateSec,
                playbackStartTime = 0.0,
            ),
            backendStartTimeSec = 0.0,
            playbackCtx = PlaybackCtx(playbackId = "test", ignitorRegistry = registry, phasePools = PhasePools(Random(1))),
            getSample = { null },
        ) ?: error("makeVoice returned null for ${data.sound}")

        val ctx = VoiceTestHelpers.createContext(blockStart = 0.0, blockFrames = blockFrames, sampleRate = sampleRate)
        val out = DoubleArray(blocks * blockFrames)

        repeat(blocks) { block ->
            ctx.blockStart = (block * blockFrames).toDouble()
            voice.render(ctx)

            val cylinder = ctx.cylinders.offerAndCommit(voice.cylinderId, voice, 0.0)

            cylinder.mixBuffer.left.copyInto(destination = out, destinationOffset = block * blockFrames, startIndex = 0, endIndex = blockFrames)
            cylinder.mixBuffer.left.fill(0.0)
            cylinder.mixBuffer.right.fill(0.0)
        }

        return out
    }

    val base = VoiceData.empty.copy(freqHz = 220.0)

    /** The level the constant leaves the channel at, read on a frame no rule of this spec touches (the body). */
    fun level(out: DoubleArray): Double = out[gateFrame / 2]

    fun firstMismatch(a: DoubleArray, b: DoubleArray): Int = a.indices.firstOrNull { a[it].toRawBits() != b[it].toRawBits() } ?: -1

    "the harness sees sound: the bare constant leaves the channel at a level" {
        (level(render(base.copy(sound = "bare"))) > 0.1) shouldBe true
    }

    "no doors: a pattern's filter, envelope and onepole slots change nothing on a bare tree" {
        val plain = render(base.copy(sound = "bare"))
        val doors = render(
            base.copy(
                sound = "bare",
                ignitorParams = mapOf("lpf.freq" to 300.0, "adsr.attack" to 0.1, "adsr.release" to 0.5, "onepole" to 200.0),
            ),
        )

        withClue("first mismatching frame") { firstMismatch(a = plain, b = doors) shouldBe -1 }
    }

    "no voice envelope: full level from the first frame to the fade, straight through the gate" {
        val out = render(base.copy(sound = "bare"))
        val l = level(out)

        withClue("the onset has no attack ramp") { out[0] shouldBe l }
        withClue("the gate has no release ramp") { out[gateFrame + 100] shouldBe l }
    }

    "the lifetime: 0.05 s past the gate, and the teardown fade takes the last 192 frames linearly to an exact 0.0" {
        val out = render(base.copy(sound = "bare"))
        val l = level(out)
        val lastFrame = gateFrame + (0.05 * sampleRate).toInt() - 1

        withClue("the last rendered frame is an exact zero") { out[lastFrame] shouldBe 0.0 }
        withClue("and the voice is gone after it") { (lastFrame + 1 until lastFrame + 2000).all { out[it] == 0.0 } shouldBe true }
        withClue("full level just before the fade window") { out[lastFrame - 200] shouldBe l }

        for (k in listOf(1, 48, 95, 150, 191)) {
            withClue("the fade, $k frames before the end: linear over 191 steps") {
                out[lastFrame - k] shouldBe (l * k / 191.0 plusOrMinus 1e-12)
            }
        }
    }

    "a root envelope with a static release ends the voice itself: its release, no teardown fade on top" {
        val out = render(base.copy(sound = "enveloped"), blocks = 200)
        val l = level(out)
        val releaseFrames = (0.2 * sampleRate).toInt()
        val lastFrame = gateFrame + releaseFrames - 1

        withClue("the last rendered frame is the envelope's exact zero") { out[lastFrame] shouldBe 0.0 }
        withClue("and the voice is gone after it") { (lastFrame + 1 until lastFrame + 2000).all { out[it] == 0.0 } shouldBe true }

        // The linear release is `1 - pos / (N - 1)`: at pos N - 1 - k it is k / (N - 1). A teardown fade on top would
        // scale the last 192 frames by up to another k / 191.
        for (k in listOf(1, 48, 96, 191)) {
            withClue("the release, $k frames before the end, is the envelope's alone") {
                out[lastFrame - k] shouldBe (l * k / (releaseFrames - 1).toDouble() plusOrMinus 1e-12)
            }
        }

        // An exponential release of 240.48 frames counts 240 and lands on the same exact 0.0 on the voice's last
        // rendered frame (folded here from `ReleaseEndsAtZeroSpec`, 2026-09-27: a release whose endpoint fell one
        // frame past the voice's end once left an audible residual, stepped to zero by the teardown). 240 frames
        // divide by 239, where a hoisted reciprocal is not exact (`AdsrCurveMath`, the note on the divide).
        val exp = render(base.copy(sound = "envelopedexp"))
        val expLast = gateFrame + 240 - 1

        withClue("exponential, fractional N: still sounding one frame before the end") { (exp[expLast - 1] > 0.0) shouldBe true }
        withClue("exponential, fractional N: the last rendered frame is an exact zero") { exp[expLast] shouldBe 0.0 }
        withClue("exponential, fractional N: the voice is gone after it") {
            (expLast + 1 until expLast + 2000).all { exp[it] == 0.0 } shouldBe true
        }
    }

    "classic() below the root: the doors reach each branch, and the sum still ends on the teardown fade" {
        val plain = render(base.copy(sound = "branches"))
        val doors = render(base.copy(sound = "branches", ignitorParams = mapOf("adsr.attack" to 0.05)))
        val lastFrame = gateFrame + (0.05 * sampleRate).toInt() - 1

        withClue("engaged: the envelope slot reaches the branches") { firstMismatch(a = plain, b = doors) shouldNotBe -1 }
        withClue("the last rendered frame is an exact zero") { plain[lastFrame] shouldBe 0.0 }
    }
})
