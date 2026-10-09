/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.master

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystChain
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystChainBuilder
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystContext
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystRegistry
import io.peekandpoke.klang.audio_be.effects.Reverb
import io.peekandpoke.klang.audio_be.warehouse.ResourceWarehouse
import io.peekandpoke.klang.audio_be.warehouse.ReverbUnits
import io.peekandpoke.klang.audio_be.warehouse.SizedBuffers
import io.peekandpoke.klang.audio_bridge.DistortionShapes
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * The output runs a Katalyst chain (phase 3 step 12 C3): what a master declares is the chain an orbit
 * would run, and the output host adds nothing to its sound. The oracle is that chain built by hand
 * from the literal Katalyst twins and run standalone, never the bus's own build.
 */
class MasterOutputChainSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128

    fun c(value: Double) = IgnitorDsl.Constant(value)

    /** Loud, silent, loud: the silent stretch is where a host that resets on silence would show. */
    fun input(block: Int, i: Int): Double {
        if (block in 60 until 120) {
            return 0.0
        }

        val n = (block * blockFrames + i).toDouble()

        return 0.6 * sin(2.0 * PI * 220.0 * n / sampleRate) + 0.3 * sin(2.0 * PI * 97.0 * n / sampleRate)
    }

    "a chain at the output runs bit for bit as the same Katalyst chain standalone, across a silent gap" {
        // Every stage kind the master has, with numbers unlike any default: gain drives the limiter
        // into real reduction, the delay's echoes and the room ring into the gap.
        val registry = KatalystRegistry()
        registry.register(
            "m",
            KatalystDsl.of(
                KatalystStageDsl.Delay(wet = c(0.35), time = c(0.07), feedback = c(0.45), cap = c(1.3)),
                KatalystStageDsl.Reverb(wet = c(0.3), size = c(6.5), lowpass = c(5200.0)),
                KatalystStageDsl.Gain(gain = c(1.8)),
                KatalystStageDsl.Compressor(threshold = c(-2.0), ratio = c(15.0), knee = c(1.5), attack = c(0.002), release = c(0.15)),
            ),
        )
        val bus = MasterBus(sampleRate = sampleRate, blockFrames = blockFrames, registry = registry)

        val standalone = KatalystChainBuilder.build(
            dsl = KatalystDsl.of(
                KatalystStageDsl.Delay(wet = c(0.35), time = c(0.07), feedback = c(0.45), cap = c(1.3)),
                KatalystStageDsl.Reverb(wet = c(0.3), size = c(6.5), lowpass = c(5200.0)),
                KatalystStageDsl.Gain(gain = c(1.8)),
                KatalystStageDsl.Compressor(
                    threshold = c(-2.0),
                    ratio = c(15.0),
                    knee = c(1.5),
                    attack = c(0.002),
                    release = c(0.15),
                    lookahead = 0.0,
                ),
            ),
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            rings = SizedBuffers.forRings(sampleRate),
            reverbs = ReverbUnits(sampleRate),
        ).also { it.applyParams(null) }

        // Before the engine's first block, as a top-level `master(...)` arrives: adopted at once.
        bus.requestSwap("m")

        val onBus = StereoBuffer(blockFrames)
        val ctx = KatalystContext(blockFrames, StereoBuffer(blockFrames))
        var gapPeak = 0.0

        for (block in 0 until 180) {
            for (i in 0 until blockFrames) {
                val x = input(block = block, i = i)

                onBus.left[i] = x
                onBus.right[i] = x * 0.5
                ctx.mixBuffer.left[i] = x
                ctx.mixBuffer.right[i] = x * 0.5
            }

            bus.process(onBus, blockFrames)
            bus.markRendered()
            standalone.process(ctx)

            for (i in 0 until blockFrames) {
                withClue("block $block frame $i") {
                    onBus.left[i].toRawBits() shouldBe ctx.mixBuffer.left[i].toRawBits()
                    onBus.right[i].toRawBits() shouldBe ctx.mixBuffer.right[i].toRawBits()
                }

                if (block in 60 until 120) {
                    gapPeak = maxOf(gapPeak, abs(onBus.left[i]))
                }
            }
        }

        // Positive control: the gap is not two silences agreeing; the tails rang into it.
        gapPeak shouldBeGreaterThan 1e-3
    }

    // ── A refused unit degrades and recovers, the orbit's rule (maintainer, 2026-09-27) ─────────

    /** A plain sine, loud enough for a room or an echo to show, never silent for a whole block. */
    fun sine(block: Int, i: Int): Double = 0.4 * sin(2.0 * PI * 330.0 * (block * blockFrames + i) / sampleRate)

    fun fill(into: StereoBuffer, block: Int) {
        for (i in 0 until blockFrames) {
            into.left[i] = sine(block = block, i = i)
            into.right[i] = sine(block = block, i = i) * 0.5
        }
    }

    /** A bus whose one master is [dsl], adopted before the first block as a top-level `master(...)` is. */
    fun adopted(dsl: KatalystDsl, rings: SizedBuffers, reverbs: ReverbUnits): MasterBus {
        val registry = KatalystRegistry().apply { register("m", dsl) }

        return MasterBus(sampleRate = sampleRate, blockFrames = blockFrames, registry = registry, rings = rings, reverbs = reverbs)
            .also { it.requestSwap("m") }
    }

    /** The refused blocks: the output is the input, bit for bit, and the bus stays on the bus path. */
    fun runRefused(bus: MasterBus, onBus: StereoBuffer, blocks: Int) {
        for (b in 0 until blocks) {
            fill(onBus, b)
            bus.process(onBus, blockFrames)
            bus.markRendered()

            for (i in 0 until blockFrames) {
                withClue("refused block $b frame $i") {
                    onBus.left[i].toRawBits() shouldBe sine(block = b, i = i).toRawBits()
                }
            }
        }

        bus.isActive shouldBe true
        // It declares its tail: the stage is there, only its unit is not.
        bus.isRinging shouldBe true
    }

    /**
     * From [from] on, the bus against the same stage built standalone on a working shelf and started
     * at that block: equal bits mean the stage engaged at that very block, from an empty unit.
     * Returns the largest wet sample, so a caller can see the stage really sounded.
     */
    fun runRecovered(bus: MasterBus, onBus: StereoBuffer, standalone: KatalystChain, from: Int, blocks: Int): Double {
        val ctx = KatalystContext(blockFrames, StereoBuffer(blockFrames))
        var wet = 0.0

        for (b in from until from + blocks) {
            fill(onBus, b)
            fill(ctx.mixBuffer, b)
            bus.process(onBus, blockFrames)
            bus.markRendered()
            standalone.process(ctx)

            for (i in 0 until blockFrames) {
                withClue("recovered block $b frame $i") {
                    onBus.left[i].toRawBits() shouldBe ctx.mixBuffer.left[i].toRawBits()
                    onBus.right[i].toRawBits() shouldBe ctx.mixBuffer.right[i].toRawBits()
                }

                wet = maxOf(wet, abs(onBus.left[i] - sine(block = b, i = i)))
            }
        }

        return wet
    }

    fun standalone(stage: KatalystStageDsl) = KatalystChainBuilder.build(
        dsl = KatalystDsl.of(stage),
        sampleRate = sampleRate,
        blockFrames = blockFrames,
        rings = SizedBuffers.forRings(sampleRate),
        reverbs = ReverbUnits(sampleRate),
    ).also { it.applyParams(null) }

    "a refused master room stays dry and active, and engages from the block the shelf gets a unit back, from an empty unit" {
        var asked = 0
        val units = ReverbUnits(sampleRate, allocate = { _ ->
            asked++
            null
        })
        val bus = adopted(KatalystDsl.of(KatalystStageDsl.Reverb(wet = c(0.4), size = c(6.0))), SizedBuffers.forRings(sampleRate), units)
        val onBus = StereoBuffer(blockFrames)

        runRefused(bus, onBus, blocks = 20)
        // Asked at the build (the first request) and once more after the adoption's reset re-armed
        // the retry; the latched blocks only consult the shelf.
        asked shouldBe 2

        // An orbit hands its room back DIRTY, still charged: the shelf must zero it on the way out.
        val returned = Reverb(sampleRate).apply { size = 0.9 }
        val charge = StereoBuffer(blockFrames).apply {
            left.fill(0.5)
            right.fill(0.5)
        }
        repeat(40) { returned.process(input = charge, output = StereoBuffer(blockFrames), length = blockFrames) }
        returned.hasTail() shouldBe true
        units.giveBack(returned)

        val wet = runRecovered(
            bus = bus,
            onBus = onBus,
            standalone = standalone(KatalystStageDsl.Reverb(wet = c(0.4), size = c(6.0))),
            from = 20,
            blocks = 60,
        )

        units.hits shouldBe 1
        asked shouldBe 2
        wet shouldBeGreaterThan 1e-3
    }

    "a refused master echo stays dry and active, and engages from the block the shelf gets a ring back, from an empty ring" {
        var asked = 0
        val rings = SizedBuffers.forRings(sampleRate, allocate = { _ ->
            asked++
            null
        })
        val bus = adopted(KatalystDsl.of(KatalystStageDsl.Delay(wet = c(0.5), time = c(0.05), feedback = c(0.4))), rings, ReverbUnits(sampleRate))
        val onBus = StereoBuffer(blockFrames)

        runRefused(bus, onBus, blocks = 20)
        asked shouldBe 2

        // A ring comes back DIRTY, full of an old echo.
        val frames = (sampleRate * ResourceWarehouse.MIN_RING_SECONDS).toInt() + ResourceWarehouse.RING_MARGIN_FRAMES
        rings.giveBack(
            StereoBuffer(frames).apply {
                left.fill(0.7)
                right.fill(0.7)
            }
        )

        val wet = runRecovered(
            bus = bus,
            onBus = onBus,
            standalone = standalone(KatalystStageDsl.Delay(wet = c(0.5), time = c(0.05), feedback = c(0.4))),
            from = 20,
            blocks = 60,
        )

        rings.hits shouldBe 1
        asked shouldBe 2
        wet shouldBeGreaterThan 1e-3
    }

    "a reset re-arms the allocating retry: the fade into a refused master allocates on its next block" {
        var failing = true
        var asked = 0
        val units = ReverbUnits(sampleRate, allocate = { sr ->
            asked++
            if (failing) null else Reverb(sr)
        })
        val registry = KatalystRegistry().apply {
            register("room", KatalystDsl.of(KatalystStageDsl.Reverb(wet = c(0.4), size = c(6.0))))
            register("loud", KatalystDsl.of(KatalystStageDsl.Gain(gain = c(2.0))))
        }
        val bus = MasterBus(sampleRate = sampleRate, blockFrames = blockFrames, registry = registry, reverbs = units)
        val onBus = StereoBuffer(blockFrames)
        var block = 0

        fun run(blocks: Int) {
            repeat(blocks) {
                fill(onBus, block++)
                bus.process(onBus, blockFrames)
                bus.markRendered()
            }
        }

        bus.requestSwap("room")
        run(10)
        asked shouldBe 2

        // The memory is there again, but a latched stage does not allocate: it only asks the shelf.
        failing = false
        run(10)
        asked shouldBe 2

        // Away and back: the fade into "room" resets it, and the reset clears the latch.
        bus.requestSwap("loud")
        run(40)
        asked shouldBe 2
        bus.requestSwap("room")
        run(1)
        asked shouldBe 3
        units.allocations shouldBe 1
    }

    "a master distort with an asymmetric shape holds the engine while its DC decay rings, then lets go" {
        // Review round 2 of the Katalyst distort stage: `tube` makes DC while signal flows, and after the input stops
        // the stage's DC blocker decays from that offset for a few hundred milliseconds. The master asked a chain for
        // its tail only when it declared a reverb or a delay, so a stopped playback disposed the engine and cut the
        // decay in one sample. The step is measured in `KatalystDistortEffectSpec`; here, the host asks.
        val bus = adopted(
            KatalystDsl.of(KatalystStageDsl.Distort(amount = c(0.5), shape = DistortionShapes.indexOf("tube").toInt())),
            SizedBuffers.forRings(sampleRate),
            ReverbUnits(sampleRate),
        )
        val onBus = StereoBuffer(blockFrames)

        fun block(loud: Boolean, b: Int) {
            if (loud) {
                fill(onBus, b)
            } else {
                onBus.left.fill(0.0)
                onBus.right.fill(0.0)
            }

            bus.process(onBus, blockFrames)
            bus.markRendered()
        }

        for (b in 0 until 20) {
            block(loud = true, b = b)
        }

        // Past the master's own silent grace: the decay is still there, so the bus still rings.
        for (b in 0 until 15) {
            block(loud = false, b = b)
        }

        bus.isRinging shouldBe true

        // ...and once it has decayed (a few hundred milliseconds), it lets the engine go.
        for (b in 0 until 400) {
            block(loud = false, b = b)
        }

        bus.isRinging shouldBe false
    }

    "a limiter-only master never holds the engine: it has nothing that rings" {
        // What `k.limiter()` appends: the compressor stage with the limiter's numbers.
        val limiter = KatalystStageDsl.Compressor(
            threshold = c(-1.0), ratio = c(20.0), knee = c(2.0), attack = c(0.001), release = c(0.1),
        )
        val bus = adopted(KatalystDsl.of(limiter), SizedBuffers.forRings(sampleRate), ReverbUnits(sampleRate))
        val onBus = StereoBuffer(blockFrames)

        for (b in 0 until 20) {
            fill(onBus, b)
            bus.process(onBus, blockFrames)
            bus.markRendered()
        }

        bus.isActive shouldBe true
        bus.isRinging shouldBe false
    }
})
