/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_be.SampleStore
import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.engines.PipelineRegistry
import io.peekandpoke.klang.audio_be.ignitor.IgnitorRegistry
import io.peekandpoke.klang.audio_be.ignitor.PhasePools
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_bridge.AdsrDef
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.audio_bridge.FilterDefs
import io.peekandpoke.klang.audio_bridge.FilterEnvDef
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.SampleRequest
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.adsr
import io.peekandpoke.klang.audio_bridge.classic
import io.peekandpoke.klang.audio_bridge.mul
import io.peekandpoke.klang.audio_bridge.plus
import kotlin.math.abs
import kotlin.random.Random

/**
 * **An AUTHORED instrument on `classic()` against the same instrument on the voice strip** (phase 3 step 10,
 * `docs/tasks/builtin-instruments.md`). The songs move their instruments from the strip onto `.classic()`, so
 * this spec pins, on trees a bare saw cannot stand for (an instrument with its OWN envelope), what that move
 * keeps and the one thing it does not:
 *
 *  - the doors, the envelope and `adsrOff`'s teardown fade are the strip's, bit for bit;
 *  - the strip STRETCHED its envelope's release to the instrument's own release tail (the envelope ownership
 *    fix of 2026-08-27: the voice release becomes the tail when the tail is longer). `classic()`'s envelope
 *    releases over its own slot, so the instrument's tail is cut there. The voice still LIVES as long (the
 *    lifetime is the tree's tail). The maintainer's decision (2026-09-26, F1): no engine stretch; a song writes
 *    `adsr(release = <tail>)`, which renders the strip's voice bit for bit (the row after it);
 *  - `classic()` places no `pregain`: an authored tree that does not place the slot ignores `pregain(x)`.
 *
 * Each row renders two voices of one note through the real [VoiceFactory]: STRIP, the tree registered as it
 * is (the strip runs after it), and CLASSIC, the same tree with `.classic()` appended. Mid-block onset
 * (frame 37), the gate at a quarter second, the whole release inside the render.
 */
class AuthoredClassicSpec : StringSpec({

    val sampleRate = 48000
    val blockFrames = 128
    val blocks = 300
    val gateSec = 0.25
    val onsetFrame = 37

    /** An instrument with its own envelope and a release tail (0.5 s) ten times the voice envelope's 0.05. */
    val longTail: IgnitorDsl = IgnitorDsl.Sine().adsr(0.005, 0.1, 0.8, 0.5)

    /** An instrument whose own tail (0.02 s) is inside the voice envelope's release: nothing is stretched. */
    val shortTail: IgnitorDsl = IgnitorDsl.Sawtooth().adsr(0.005, 0.1, 0.8, 0.02)

    /**
     * No envelope of its own and a level at its root: under `adsrOff` it sounds until the voice ends, so the
     * teardown fade over the voice's last frames is what takes it to zero (an instrument with its own short
     * release would be silent there already, and no row could see the fade).
     */
    val sustainedLevel: IgnitorDsl = IgnitorDsl.Sawtooth().mul(IgnitorDsl.Constant(0.5))

    /**
     * Its own ROOT envelope with a MODULATED release (a perlin-driven 0.3 s, give or take 10 ms), which no build can
     * give a static length. Under `adsrOff` the voice lives as long as `classic()`'s switched-off envelope says
     * (0.05 s past the gate) while this envelope is still releasing, so only the teardown fade takes it to zero.
     */
    val modulatedRelease: IgnitorDsl = IgnitorDsl.Adsr(
        inner = IgnitorDsl.Sawtooth(),
        attackSec = IgnitorDsl.Constant(0.005),
        decaySec = IgnitorDsl.Constant(0.1),
        sustainLevel = IgnitorDsl.Constant(0.8),
        releaseSec = IgnitorDsl.Constant(0.3).plus(IgnitorDsl.PerlinNoise().mul(IgnitorDsl.Constant(0.01))),
    )

    val registry = IgnitorRegistry().apply {
        register("long", longTail)
        register("longclassic", longTail.classic())
        register("short", shortTail)
        register("shortclassic", shortTail.classic())
        register("level", sustainedLevel)
        register("levelclassic", sustainedLevel.classic())
        register("modrel", modulatedRelease)
        register("modrelclassic", modulatedRelease.classic())
    }

    class Rendered(val out: DoubleArray, val endFrame: Double)

    fun render(data: VoiceData): Rendered {
        val onsetSec = onsetFrame.toDouble() / sampleRate
        val factory = VoiceFactory(
            sampleRate = sampleRate,
            sampleRateDouble = sampleRate.toDouble(),
            blockFrames = blockFrames,
            ignitorRegistry = registry,
            pipelineRegistry = PipelineRegistry(),
            cylinders = Cylinders(blockFrames = blockFrames, sampleRate = sampleRate),
            voiceBuffer = DoubleArray(blockFrames),
            freqModBuffer = DoubleArray(blockFrames),
            scratchBuffers = ScratchBuffers(blockFrames),
        )
        val noSamples: (SampleRequest) -> SampleStore.SampleEntry.Complete? = { null }
        val voice = factory.makeVoice(
            scheduled = ScheduledVoice(
                playbackId = "test",
                data = data,
                startTime = onsetSec,
                gateEndTime = onsetSec + gateSec,
                playbackStartTime = 0.0,
            ),
            backendStartTimeSec = 0.0,
            playbackCtx = PlaybackCtx(playbackId = "test", ignitorRegistry = registry, phasePools = PhasePools(Random(1))),
            getSample = noSamples,
        ) ?: error("makeVoice returned null for ${data.sound}")

        val endFrame = voice.endFrame
        val ctx = VoiceTestHelpers.createContext(blockStart = 0.0, blockFrames = blockFrames, sampleRate = sampleRate)
        val out = DoubleArray(blocks * blockFrames)

        repeat(blocks) { block ->
            ctx.blockStart = (block * blockFrames).toDouble()
            voice.render(ctx)

            val cylinder = ctx.cylinders.getOrInit(voice.cylinderId, voice, 0.0)

            cylinder.mixBuffer.left.copyInto(out, block * blockFrames, 0, blockFrames)
            cylinder.mixBuffer.left.fill(0.0)
            cylinder.mixBuffer.right.fill(0.0)
        }

        return Rendered(out, endFrame)
    }

    fun firstMismatch(a: DoubleArray, b: DoubleArray, until: Int = a.size): Int =
        (0 until until).firstOrNull { a[it].toRawBits() != b[it].toRawBits() } ?: -1

    fun maxAbs(a: DoubleArray, from: Int, until: Int): Double = (from until until).maxOf { abs(a[it]) }

    val base = VoiceData.empty.copy(freqHz = 220.0)
    val gateFrame = onsetFrame + (gateSec * sampleRate).toInt()

    "DIVERGENT, the F1 law: the strip stretched its release to the instrument's 0.5 s tail, classic() releases over its own 0.05 and cuts the tail" {
        val strip = render(base.copy(sound = "long"))
        val classic = render(base.copy(sound = "longclassic"))
        // 0.1 s after the gate: classic()'s release (0.05) and its 1 ms de-click are long over, the strip's 0.5 s
        // release is at its start; the window ends before the strip's release does.
        val from = gateFrame + (0.1 * sampleRate).toInt()
        val until = gateFrame + (0.3 * sampleRate).toInt()

        withClue("up to the gate the two voices are one voice, first mismatch") {
            firstMismatch(strip.out, classic.out, until = gateFrame) shouldBe -1
        }
        withClue("the strip's voice still sounds 0.1 to 0.3 s after the gate: its release is the instrument's tail") {
            maxAbs(strip.out, from, until) shouldBeGreaterThan 0.05
        }
        withClue("classic()'s voice is silent there: its release is its own slot, the tail is cut") {
            maxAbs(classic.out, from, until) shouldBeLessThan 1e-9
        }
        withClue("...but the voice LIVES as long: the lifetime is the tree's tail on both paths") {
            classic.endFrame shouldBe strip.endFrame
        }
    }

    "IDENTICAL, the F1 remedy: with adsr(release = 0.5) written, the classic() voice IS the strip's voice" {
        val written = base.copy(adsr = AdsrDef.Std(release = 0.5))
        val strip = render(written.copy(sound = "long"))
        val classic = render(written.copy(sound = "longclassic"))

        withClue("first mismatching frame over the whole release") { firstMismatch(strip.out, classic.out) shouldBe -1 }
        withClue("and the lifetime") { classic.endFrame shouldBe strip.endFrame }
        withClue("engaged: without the written release the classic() voice is a different one") {
            firstMismatch(render(base.copy(sound = "longclassic")).out, classic.out) shouldNotBe -1
        }
    }

    "IDENTICAL: the doors on an authored classic() tree are the strip's, filters, filter envelope, crush, tremolo and envelope" {
        val doors = base.copy(
            filters = FilterDefs(
                listOf(
                    FilterDef.HighPass(150.0, 0.707),
                    FilterDef.LowPass(2400.0, 1.5, envelope = FilterEnvDef(depth = 12.0, decay = 0.2)),
                ),
            ),
            crush = 6.0,
            tremoloDepth = 0.4,
            tremoloSync = 5.0,
            adsr = AdsrDef.Std(attack = 0.01, decay = 0.2, sustain = 0.6, release = 0.1),
        )
        val strip = render(doors.copy(sound = "short"))
        val classic = render(doors.copy(sound = "shortclassic"))

        withClue("first mismatching frame") { firstMismatch(strip.out, classic.out) shouldBe -1 }
        withClue("engaged: the doors change the voice") {
            firstMismatch(render(base.copy(sound = "shortclassic")).out, classic.out) shouldNotBe -1
        }
    }

    "IDENTICAL: adsrOff on an authored classic() tree whose root is not an envelope fades the voice as the strip did" {
        val off = base.copy(adsr = AdsrDef.Std(on = false))
        val strip = render(off.copy(sound = "level"))
        val classic = render(off.copy(sound = "levelclassic"))

        withClue("first mismatching frame, the teardown fade included") { firstMismatch(strip.out, classic.out) shouldBe -1 }
        withClue("the fade is inside the render: the voice sounds just before its end and is an exact zero at its last frame") {
            val last = classic.endFrame.toInt() - 1

            maxAbs(classic.out, last - 600, last - 300) shouldBeGreaterThan 0.1
            classic.out[last] shouldBe 0.0
        }
        withClue("engaged: adsrOff changes the voice") {
            firstMismatch(render(base.copy(sound = "levelclassic")).out, classic.out) shouldNotBe -1
        }
    }

    "IDENTICAL: adsrOff on an authored classic() tree whose own root envelope has a MODULATED release still gets the teardown fade" {
        // Such an envelope has no static length, so it cannot promise to reach zero by the voice's end: the voice
        // fades it, as the strip always faded an `adsrOff` voice. Without the fade the voice ends on a hard cut.
        val off = base.copy(adsr = AdsrDef.Std(on = false))
        val strip = render(off.copy(sound = "modrel"))
        val classic = render(off.copy(sound = "modrelclassic"))
        val last = classic.endFrame.toInt() - 1

        withClue("first mismatching frame against the strip, the fade included") { firstMismatch(strip.out, classic.out) shouldBe -1 }
        withClue("the voice sounds just before its end, inside its own release") {
            maxAbs(classic.out, last - 600, last - 300) shouldBeGreaterThan 0.1
        }
        withClue("and ends on an exact zero: the fade ran") { classic.out[last] shouldBe 0.0 }
    }

    "classic() places no pregain: an authored tree that does not place the slot ignores pregain(2), on both paths" {
        val plain = render(base.copy(sound = "shortclassic"))
        val pushed = render(base.copy(sound = "shortclassic", oscParams = mapOf("pregain" to 2.0)))

        withClue("classic(), first mismatch") { firstMismatch(plain.out, pushed.out) shouldBe -1 }
        withClue("the strip, first mismatch") {
            firstMismatch(render(base.copy(sound = "short")).out, render(base.copy(sound = "short", oscParams = mapOf("pregain" to 2.0))).out) shouldBe -1
        }
        withClue("the harness hears the voice") { maxAbs(plain.out, onsetFrame, gateFrame) shouldBeGreaterThan 0.1 }
    }
})
