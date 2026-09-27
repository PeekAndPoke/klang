/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_be.SampleStore
import io.peekandpoke.klang.audio_be.cylinders.Cylinders
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
 * **An AUTHORED instrument on `classic()`** (phase 3 step 10, `docs/tasks/builtin-instruments.md`), on trees a bare
 * saw cannot stand for (an instrument with its OWN envelope):
 *
 *  - the doors reach it, and `adsrOff` ends it on the teardown fade (also for an own root envelope whose release
 *    is modulated, which cannot promise to reach zero by the voice's end);
 *  - `classic()`'s envelope releases over its own slot, so an instrument's longer own tail is CUT there, while the
 *    voice still LIVES as long as the tree's tail. The strip stretched its release to the tail (the envelope
 *    ownership fix of 2026-08-27); the maintainer's decision (2026-09-26, F1): no engine stretch, a song writes
 *    `adsr(release = <tail>)`;
 *  - `classic()` places no `pregain`: an authored tree that does not place the slot ignores `pregain(x)`.
 *
 * Until the voice strip retired (phase 3 step 9) every row also rendered the tree without `classic()` on the
 * strip and pinned the two bit for bit. Mid-block onset (frame 37), the gate at a quarter second, the whole
 * release inside the render.
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
        register("longclassic", longTail.classic())
        register("shortclassic", shortTail.classic())
        register("levelclassic", sustainedLevel.classic())
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
            cylinders = Cylinders(blockFrames = blockFrames, sampleRate = sampleRate),
            voiceBuffer = DoubleArray(blockFrames),
            freqModBuffer = DoubleArray(blockFrames),
            scratchBuffers = ScratchBuffers(blockFrames),
        )
        val noSamples: (SampleRequest) -> SampleStore.SampleEntry.Complete? = { null }
        val voice = factory.makeVoice(
            scheduled = ScheduledVoice(
                playbackId = "test",
                data = data.withClassicSlots(),
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

    "the F1 law: classic() releases over its own 0.05 s and cuts the instrument's 0.5 s tail, and the voice lives the tail" {
        val classic = render(base.copy(sound = "longclassic"))
        // 0.1 s after the gate: classic()'s release (0.05) and its 1 ms de-click are long over, the instrument's
        // own 0.5 s release is at its start.
        val from = gateFrame + (0.1 * sampleRate).toInt()
        val until = gateFrame + (0.3 * sampleRate).toInt()

        withClue("the voice sounds up to the gate") { maxAbs(classic.out, gateFrame - 600, gateFrame) shouldBeGreaterThan 0.05 }
        withClue("silent 0.1 to 0.3 s after the gate: its release is its own slot, the tail is cut") {
            maxAbs(classic.out, from, until) shouldBeLessThan 1e-9
        }
        withClue("...but the voice LIVES as long as the tree's 0.5 s tail") {
            classic.endFrame shouldBe (gateFrame + 0.5 * sampleRate plusOrMinus 1.0)
        }
    }

    "the F1 remedy: with adsr(release = 0.5) written, the instrument's own tail sounds" {
        val classic = render(base.copy(sound = "longclassic", adsr = AdsrDef.Std(release = 0.5)))
        val from = gateFrame + (0.1 * sampleRate).toInt()
        val until = gateFrame + (0.3 * sampleRate).toInt()

        withClue("the tail sounds 0.1 to 0.3 s after the gate") { maxAbs(classic.out, from, until) shouldBeGreaterThan 0.05 }
        withClue("and the lifetime is the tail's") { classic.endFrame shouldBe (gateFrame + 0.5 * sampleRate plusOrMinus 1.0) }
    }

    "the doors reach an authored classic() tree: filters, filter envelope, crush, tremolo and envelope" {
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
        val classic = render(doors.copy(sound = "shortclassic"))

        withClue("engaged: the doors change the voice") {
            firstMismatch(render(base.copy(sound = "shortclassic")).out, classic.out) shouldNotBe -1
        }
    }

    "adsrOff on an authored classic() tree whose root is not an envelope ends the voice on the teardown fade" {
        val off = base.copy(adsr = AdsrDef.Std(on = false))
        val classic = render(off.copy(sound = "levelclassic"))

        withClue("the fade is inside the render: the voice sounds just before its end and is an exact zero at its last frame") {
            val last = classic.endFrame.toInt() - 1

            maxAbs(classic.out, last - 600, last - 300) shouldBeGreaterThan 0.1
            classic.out[last] shouldBe 0.0
        }
        withClue("engaged: adsrOff changes the voice") {
            firstMismatch(render(base.copy(sound = "levelclassic")).out, classic.out) shouldNotBe -1
        }
    }

    "adsrOff on an authored classic() tree whose own root envelope has a MODULATED release still gets the teardown fade" {
        // Such an envelope has no static length, so it cannot promise to reach zero by the voice's end: the voice
        // fades it, as the strip always faded an `adsrOff` voice. Without the fade the voice ends on a hard cut.
        val off = base.copy(adsr = AdsrDef.Std(on = false))
        val classic = render(off.copy(sound = "modrelclassic"))
        val last = classic.endFrame.toInt() - 1

        withClue("the voice sounds just before its end, inside its own release") {
            maxAbs(classic.out, last - 600, last - 300) shouldBeGreaterThan 0.1
        }
        withClue("and ends on an exact zero: the fade ran") { classic.out[last] shouldBe 0.0 }
    }

    "classic() places no pregain: an authored tree that does not place the slot ignores pregain(2)" {
        val plain = render(base.copy(sound = "shortclassic"))
        val pushed = render(base.copy(sound = "shortclassic", oscParams = mapOf("pregain" to 2.0)))

        withClue("classic(), first mismatch") { firstMismatch(plain.out, pushed.out) shouldBe -1 }
        withClue("the harness hears the voice") { maxAbs(plain.out, onsetFrame, gateFrame) shouldBeGreaterThan 0.1 }
    }
})
