/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.Crossfade
import io.peekandpoke.klang.audio_be.Oversampler
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.cylinders.Cylinder
import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.ignitor.IgnitorRegistry
import io.peekandpoke.klang.audio_be.ignitor.PhasePools
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.ignitor.registerDefaults
import io.peekandpoke.klang.audio_be.katalyst.KatalystChainBuilder
import io.peekandpoke.klang.audio_be.katalyst.KatalystContext
import io.peekandpoke.klang.audio_be.katalyst.KatalystRegistry
import io.peekandpoke.klang.audio_be.warehouse.ReverbUnits
import io.peekandpoke.klang.audio_be.warehouse.SizedBuffers
import io.peekandpoke.klang.audio_bridge.Ign
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystKnobDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.distort
import io.peekandpoke.klang.audio_bridge.ign
import io.peekandpoke.klang.audio_bridge.mul
import io.peekandpoke.klang.audio_bridge.oversampleBy
import kotlin.math.abs
import kotlin.random.Random

/**
 * The hand-over (KLANG-00214 (oversampling regions) step 3b): a voice whose tree ends in an oversampling region lands
 * on its orbit's bus at the region's rate, and the orbit's chain brings every bus to its own rate once, whatever the
 * voices on it ([io.peekandpoke.klang.audio_be.katalyst.KatalystBusLadder], the head region of a chain).
 *
 * The oracles: a voice that hands over against the same voice converting down itself (its gain written after the
 * region instead of inside it, so its root is no region): equal to rounding while gain and pan are constant, since the
 * orbit steps down with the voice's own stages; within a bound where the fades and the solo ramp move the gain, which
 * now applies before the step down. The buses at an orbit's rate against bare [Oversampler]s fed with the buses
 * captured in the same run.
 */
class VoiceHandOverSpec : StringSpec({

    val sampleRate = 48000
    val blockFrames = 128
    val gateSec = 0.1

    // The voice's end: no envelope, so the tree reports no tail and the voice lives 0.05 s past its gate, the last
    // 192 frames under the teardown fade.
    val endFrame = ((gateSec + 0.05) * sampleRate).toInt()

    fun c(value: Double) = KatalystKnobDsl.Constant(value)

    val unityChain = KatalystDsl.of(KatalystStageDsl.Gain(gain = c(1.0)))

    fun regionChain(factor: Int) = KatalystDsl.of(KatalystStageDsl.OversampleBy(factor = factor, body = unityChain))

    /** A tree that ENDS in a region of [steps]: its gain inside the body, so it hands over. */
    fun handing(steps: Int): IgnitorDsl = Ign.saw().oversampleBy((1 shl steps).ign) { it.distort(0.6.ign, "tube").mul(0.5.ign) }

    /** The same sound, its gain after the region: the root is no region, so the voice converts down itself. */
    fun converting(steps: Int): IgnitorDsl = Ign.saw().oversampleBy((1 shl steps).ign) { it.distort(0.6.ign, "tube") }.mul(0.5.ign)

    fun voice(tree: IgnitorDsl, startFrame: Int = 1000, freqHz: Double = 220.0): Voice {
        val registry = IgnitorRegistry().apply {
            registerDefaults()
            register("tree", tree)
        }
        val factory = VoiceFactory(
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            scratchBuffers = ScratchBuffers(blockFrames),
        )

        return factory.makeVoice(
            scheduled = ScheduledVoice(
                playbackId = "test",
                data = VoiceData.empty.copy(sound = "tree", freqHz = freqHz),
                startTime = startFrame.toDouble() / sampleRate,
                gateEndTime = gateSec,
                playbackStartTime = 0.0,
            ),
            backendStartTimeSec = 0.0,
            playbackCtx = PlaybackCtx(playbackId = "test", ignitorRegistry = registry, phasePools = PhasePools(Random(1))),
            getSample = { null },
        ) ?: error("makeVoice returned null")
    }

    /** One orbit (0) running [chain], rendered in the engine's block order: clear, the voices, the chains and the mix. */
    class Rig(chain: KatalystDsl, silentBlocksBeforeTailCheck: Int = 10) {
        val katalysts = KatalystRegistry()
        val cylinders = Cylinders(
            blockFrames = blockFrames,
            sampleRate = sampleRate,
            silentBlocksBeforeTailCheck = silentBlocksBeforeTailCheck,
            katalysts = katalysts,
        )
        val ctx = Voice.RenderContext(
            cylinders = cylinders,
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            voiceBuffer = AudioBuffer(blockFrames),
            scratchBuffers = ScratchBuffers(blockFrames),
        )
        val fusion = StereoBuffer(blockFrames)

        /** The orbit's rate buses each block, left channel, captured after the voices and before the chain. */
        val captured = mutableMapOf<Int, MutableList<DoubleArray>>()

        /** The active-bus bits each block, as the voices left them. */
        val masks = mutableListOf<Int>()

        init {
            request(name = "start", chain = chain)
        }

        val orbit: Cylinder get() = cylinders.cylinders.single()

        fun request(name: String, chain: KatalystDsl) {
            katalysts.register(name, chain)
            cylinders.requestChain(orbit = 0, name = name)
        }

        fun render(voices: List<Voice>, blocks: Int, before: (Int) -> Unit = {}): DoubleArray {
            val out = DoubleArray(blocks * blockFrames)

            repeat(blocks) { block ->
                before(block)
                ctx.blockStart = (block * blockFrames).toDouble()
                cylinders.clearAll()
                voices.forEach { it.render(ctx) }

                val context = orbit.katalystContext

                masks.add(context.activeBuses)

                for (steps in 1..4) {
                    val left = context.buses[steps]?.left?.copyOf() ?: DoubleArray(blockFrames shl steps)

                    captured.getOrPut(steps) { mutableListOf() }.add(left)
                }

                fusion.clear()
                cylinders.processAndMix(fusion, ctx.blockStart)
                fusion.left.copyInto(destination = out, destinationOffset = block * blockFrames)
            }

            return out
        }
    }

    fun maxDiff(a: DoubleArray, b: DoubleArray, from: Int = 0, until: Int = a.size): Double =
        (from until until).maxOf { abs(a[it] - b[it]) }

    fun peak(a: DoubleArray): Double = a.maxOf { abs(it) }

    val blocks = endFrame / blockFrames + 4

    "a voice whose tree ends in a region lands on its orbit's bus at the region's rate, and a base-rate orbit steps it down as the voice would have" {
        val handed = Rig(unityChain)
        val converted = Rig(unityChain)
        // The onset mid-block (1000 is 104 frames into block 7): the voice's window lands at its rate.
        val outH = handed.render(listOf(voice(handing(2))), blocks)
        val outC = converted.render(listOf(voice(converting(2))), blocks)

        withClue("the voice wrote the 4x bus, and only that") { handed.masks[10] shouldBe (1 shl 2) }
        withClue("the converting voice wrote none") { converted.masks.all { it == 0 } shouldBe true }
        withClue("there is sound") { peak(outC) shouldBeGreaterThan 0.1 }
        withClue("equal to rounding up to the teardown fade") {
            maxDiff(outH, outC, until = endFrame - 200) shouldBeLessThan 1e-12
        }
    }

    "two voices at one rate meet on one bus and leave the orbit as they would have one by one" {
        val handed = Rig(unityChain)
        val converted = Rig(unityChain)
        val outH = handed.render(listOf(voice(handing(2)), voice(handing(2), startFrame = 2000, freqHz = 330.0)), blocks)
        val outC = converted.render(listOf(voice(converting(2)), voice(converting(2), startFrame = 2000, freqHz = 330.0)), blocks)

        maxDiff(outH, outC, until = endFrame - 200) shouldBeLessThan 1e-12
    }

    "the teardown fade runs at the voice's rate: the same line between the frames, silence after the last frame" {
        val outH = Rig(unityChain).render(listOf(voice(handing(2))), blocks)
        val outC = Rig(unityChain).render(listOf(voice(converting(2))), blocks)

        withClue("the fade, applied before the step down instead of after it") {
            maxDiff(outH, outC) shouldBeLessThan 0.02 * peak(outC)
        }
        withClue("after the voice: only the step down's own ring") {
            maxDiff(outH, DoubleArray(outH.size), from = endFrame + 32) shouldBeLessThan 1e-4
        }
    }

    "a cut fades the voice at its rate" {
        val cutAt = 20
        // Mid-block, while the voice sounds.
        val cutFrame = cutAt * blockFrames + 30.0
        val handing = voice(handing(2))
        val converting = voice(converting(2))
        val outH = Rig(unityChain).render(listOf(handing), blocks) { if (it == cutAt) handing.cutOff(cutFrame) }
        val outC = Rig(unityChain).render(listOf(converting), blocks) { if (it == cutAt) converting.cutOff(cutFrame) }

        maxDiff(outH, outC) shouldBeLessThan 0.02 * peak(outC)
        withClue("the cut has ended the voice") {
            maxDiff(outH, DoubleArray(outH.size), from = (cutAt + 4) * blockFrames) shouldBeLessThan 1e-4
        }
    }

    "a solo's gain ramp runs at the voice's rate, the same line between the frames" {
        val handing = voice(handing(2))
        val converting = voice(converting(2))
        val soloAt = 20
        // Set every block, as the scheduler does: 1 before the solo, 0.25 from it on, so one block ramps.
        val outH = Rig(unityChain).render(listOf(handing), blocks) { handing.setGainMultiplier(if (it < soloAt) 1.0 else 0.25) }
        val outC = Rig(unityChain).render(listOf(converting), blocks) { converting.setGainMultiplier(if (it < soloAt) 1.0 else 0.25) }

        withClue("the ramp's block and the one after it") {
            maxDiff(outH, outC, from = soloAt * blockFrames, until = (soloAt + 2) * blockFrames) shouldBeLessThan 0.02 * peak(outC)
        }
        withClue("past the ramp the gain is constant again, the step down's memory of it fading") {
            maxDiff(outH, outC, from = (soloAt + 4) * blockFrames, until = endFrame - 200) shouldBeLessThan 1e-9
        }
    }

    "a 4x orbit takes the 4x bus straight in: what a base-rate orbit makes of it, with no trip up and down between" {
        val atRate = Rig(regionChain(4)).render(listOf(voice(handing(2))), blocks)
        val atBase = Rig(unityChain).render(listOf(voice(handing(2))), blocks)

        // The base-rate orbit's steps down ring on for their hold after the voice (`Oversampler.tailFrames`), then stop;
        // the 4x orbit's region runs on.
        val ringEnd = endFrame + Oversampler.tailFrames(4)

        withClue("there is sound") { peak(atRate) shouldBeGreaterThan 0.1 }
        withClue("the same samples, the steps' ring after the voice included") { maxDiff(atRate, atBase, until = ringEnd) shouldBe 0.0 }
        withClue("past the hold, what is left of the ring") { maxDiff(atRate, atBase, from = ringEnd) shouldBeLessThan 1e-9 }
    }

    "on a 4x orbit a 2x bus continues its cascade up from stage 1 and an 8x bus steps down through stage 2" {
        val rig = Rig(regionChain(4))
        val out = rig.render(listOf(voice(handing(1)), voice(handing(3), freqHz = 330.0)), blocks)

        // The orbit's region by hand: the 2x bus up through stage 1, the 8x bus down through stage 2, summed at 4x,
        // the unity body, and down to the base rate through stages 1 and 0.
        val up = Oversampler(stages = 1, firstStage = 1)
        val down = Oversampler(stages = 1, firstStage = 2)
        val toBase = Oversampler(stages = 2, firstStage = 0)
        val expected = DoubleArray(out.size)
        val twoX = rig.captured.getValue(1)
        val eightX = rig.captured.getValue(3)

        for (block in 0 until blocks) {
            val inner = AudioBuffer(4 * blockFrames)
            val stepped = eightX[block].copyOf()

            up.upsample(source = twoX[block], offset = 0, length = 2 * blockFrames, work = inner)
            down.decimate(work = stepped, target = stepped, offset = 0, length = 4 * blockFrames)

            for (i in 0 until 4 * blockFrames) {
                inner[i] = inner[i] + stepped[i]
            }

            toBase.decimate(work = inner, target = expected, offset = block * blockFrames, length = blockFrames)
        }

        withClue("both buses were written") { rig.masks[10] shouldBe ((1 shl 1) or (1 shl 3)) }
        withClue("there is sound") { peak(out) shouldBeGreaterThan 0.1 }
        maxDiff(out, expected) shouldBe 0.0
    }

    "on a base-rate orbit buses at two rates share the steps down: a 2x bus joins the 8x one at stage 1" {
        val handed = Rig(unityChain)
        val converted = Rig(unityChain)
        val outH = handed.render(listOf(voice(handing(1)), voice(handing(3), freqHz = 330.0)), blocks)
        val outC = converted.render(listOf(voice(converting(1)), voice(converting(3), freqHz = 330.0)), blocks)

        withClue("both buses were written") { handed.masks[10] shouldBe ((1 shl 1) or (1 shl 3)) }
        maxDiff(outH, outC, until = endFrame - 200) shouldBeLessThan 1e-12
    }

    "a swap between chains of different rates fades every rate: a 4x chain to a base-rate one, both unity, the orbit goes on unbroken" {
        val swapAt = 12
        val steady = Rig(regionChain(4)).render(listOf(voice(handing(2))), blocks)
        val swapping = Rig(regionChain(4))
        val out = swapping.render(listOf(voice(handing(2))), blocks) {
            if (it == swapAt) {
                swapping.request(name = "base", chain = unityChain)
            }
        }
        val fadeBlocks = (Crossfade.XFADE_SECONDS * sampleRate / blockFrames).toInt() + 2

        withClue("the swap ran") { (swapping.orbit.isFading || swapping.orbit.chainSwap.settled) shouldBe true }
        withClue("through the fade and the leaving chain's drain, against the orbit that never swapped") {
            maxDiff(out, steady, from = swapAt * blockFrames, until = (swapAt + fadeBlocks + 12) * blockFrames) shouldBeLessThan 0.005 * peak(steady)
        }
    }

    "a leaving chain below the voice's rate rings its steps down out on a silent bus: a 2x chain to a 4x one" {
        // The 2x chain steps the 4x bus down one stage, and its region holds a tail, so it drains after the fade and its
        // step rings on its own bus. Both chains are unity and convert with the same stages: the orbit goes on unbroken.
        val swapAt = 12
        val steady = Rig(regionChain(4)).render(listOf(voice(handing(2))), blocks)
        val swapping = Rig(regionChain(2))
        val out = swapping.render(listOf(voice(handing(2))), blocks) {
            if (it == swapAt) {
                swapping.request(name = "four", chain = regionChain(4))
            }
        }
        val fadeBlocks = (Crossfade.XFADE_SECONDS * sampleRate / blockFrames).toInt() + 2

        withClue("through the fade and the drain") {
            maxDiff(out, steady, from = swapAt * blockFrames, until = (swapAt + fadeBlocks + 12) * blockFrames) shouldBeLessThan 0.005 * peak(steady)
        }
    }

    "a chain's reset clears its converters: a reset chain run on silent buses stays silent" {
        listOf(unityChain, regionChain(4)).forEach { dsl ->
            val chain = KatalystChainBuilder.build(
                dsl = dsl,
                sampleRate = sampleRate,
                blockFrames = blockFrames,
                rings = SizedBuffers.forRings(sampleRate),
                reverbs = ReverbUnits(sampleRate),
            )
            val ctx = KatalystContext(blockFrames = blockFrames, mixBuffer = StereoBuffer(blockFrames))

            // The mix too: a head region's steps up hold state of their own.
            ctx.mixBuffer.left.fill(0.5)
            ctx.busAt(3).left.fill(0.5)
            chain.process(ctx)
            chain.reset()
            ctx.clearBuses()
            ctx.mixBuffer.clear()
            // A bus that holds this block's audio, all of it silent.
            ctx.busAt(3)
            chain.process(ctx)

            withClue(dsl.stages.first()::class.simpleName) { peak(ctx.mixBuffer.left) shouldBe 0.0 }
        }
    }

    "an orbit that deactivates while its steps ring out zeroes its buses: the next voice starts clean" {
        // No grace: the orbit goes two blocks after the voice's last, while its steps down still ring on a bus.
        val laterStart = endFrame + 40 * blockFrames
        val rig = Rig(unityChain, silentBlocksBeforeTailCheck = 0)
        val total = laterStart / blockFrames + 20
        val out = rig.render(listOf(voice(handing(2)), voice(handing(2), startFrame = laterStart)), total)
        val alone = Rig(unityChain, silentBlocksBeforeTailCheck = 0).render(listOf(voice(handing(2), startFrame = laterStart)), total)

        withClue("the orbit went quiet between the voices") { rig.masks[laterStart / blockFrames - 2] shouldBe 0 }
        maxDiff(out, alone, from = laterStart - blockFrames) shouldBe 0.0
    }

    "a retired orbit holds no bus audio: the next life starts silent" {
        val cylinder = Cylinder(id = 0, blockFrames = blockFrames, sampleRate = sampleRate)
        val bus = cylinder.mixBufferAt(2)

        bus.left.fill(0.5)
        cylinder.retire()

        cylinder.katalystContext.activeBuses shouldBe 0
        peak(bus.left) shouldBe 0.0
    }
})
