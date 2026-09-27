/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders

import io.peekandpoke.klang.audio_be.ChainSwap
import io.peekandpoke.klang.audio_be.Crossfade
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystChain
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystChainBuilder
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystRegistry
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers
import io.peekandpoke.klang.audio_be.warehouse.ReverbUnits
import io.peekandpoke.klang.audio_be.warehouse.SizedBuffers
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import kotlin.math.PI
import kotlin.math.sin

/**
 * One [Cylinder] driven the way `Cylinders.processAndMix` drives it, for the [ChainSwap] specs
 * (`ChainSwapStateIdentitySpec`, `ChainSwapSpec`). The input is a sine, so two rigs given the same
 * script produce the same bits, and a row can compare them sample for sample.
 */
internal class CylinderSwapRig {

    val blockFrames = 128
    val sampleRate = 44100

    /** Blocks the ramp spans, plus one for the block it completes in and one for the edge after it. */
    val fadeBlocks = (Crossfade.XFADE_SECONDS * sampleRate / blockFrames).toInt() + 2

    val registry = KatalystRegistry()
    val rings = SizedBuffers.forRings(sampleRate)
    val reverbs = ReverbUnits(sampleRate)

    val cylinder = Cylinder(
        id = 1,
        blockFrames = blockFrames,
        sampleRate = sampleRate,
        // 0 = one silent tryDeactivate is enough.
        silentBlocksBeforeTailCheck = 0,
        rings = rings,
        reverbs = reverbs,
        katalysts = registry,
    )

    val swap: ChainSwap get() = cylinder.chainSwap

    /** The orbit's owner voice. */
    var voice: Voice = VoiceTestHelpers.createSynthVoice()

    /** Absolute backend frame of the next block. */
    var blockStart: Double = 0.0

    /** Blocks rendered so far: the sine's clock. */
    private var blocks: Int = 0

    private val sidechain = StereoBuffer(blockFrames)

    /**
     * One rendered block in the engine's order: the owner offers itself, the voices fill the mix,
     * [beforeEffects] runs (where a request consumed by the scheduler would land), the queued chain is
     * polled and the chain runs, then the duck pass. Returns the left channel the orbit hands on.
     */
    fun block(
        level: Double,
        sidechainLevel: Double = 0.0,
        owned: Boolean = true,
        duckPass: Boolean = true,
        beforeEffects: () -> Unit = {},
    ): DoubleArray {
        if (owned) {
            cylinder.updateFromVoice(voice, blockStart)
        }

        val left = cylinder.mixBuffer.left
        val right = cylinder.mixBuffer.right

        for (i in 0 until blockFrames) {
            val t = (blocks * blockFrames + i).toDouble() / sampleRate

            left[i] = level * sin(2.0 * PI * 220.0 * t)
            right[i] = level * sin(2.0 * PI * 223.0 * t)
        }

        beforeEffects()
        cylinder.pollPendingChain()
        cylinder.processEffects()

        if (duckPass && cylinder.duck?.duckCylinderId != null) {
            sidechain.fill(sidechainLevel)
            cylinder.processDuck(sidechain)
        }

        blockStart += blockFrames
        blocks++

        return left.copyOf()
    }

    fun render(blocks: Int, level: Double, sidechainLevel: Double = 0.0, duckPass: Boolean = true) {
        repeat(blocks) {
            block(level = level, sidechainLevel = sidechainLevel, duckPass = duckPass)
        }
    }

    /** Blocks until the leaving chain has rung out, bounded; returns how many it took. */
    fun drainOut(level: Double): Int {
        var count = 0

        while (cylinder.isDraining && count < 20000) {
            block(level = level)
            count++
        }

        return count
    }

    /** A chain built outside the cylinder, for a row that offers the swap a chain directly. */
    fun buildChain(dsl: KatalystDsl): KatalystChain = KatalystChainBuilder.build(
        dsl = dsl,
        sampleRate = sampleRate,
        blockFrames = blockFrames,
        rings = rings,
        reverbs = reverbs,
    )

    companion object {
        /** A chain with neither a delay nor a reverb: nothing it leaves behind can ring. */
        fun dryChain(gain: Double) = KatalystDsl.of(
            KatalystStageDsl.Gain(gain = IgnitorDsl.Constant(gain))
        )

        /** A chain with a room of its own, so it drains when it leaves. */
        fun roomChain(size: Double) = KatalystDsl.of(
            KatalystStageDsl.Reverb(wet = IgnitorDsl.Constant(0.5), size = IgnitorDsl.Constant(size))
        )

        /** A room and a duck listening to orbit 0: it drains, and its duck is handed over on a swap. */
        fun duckedRoomChain() = KatalystDsl.of(
            KatalystStageDsl.Reverb(wet = IgnitorDsl.Constant(0.5), size = IgnitorDsl.Constant(6.0)),
            KatalystStageDsl.Duck(
                orbit = IgnitorDsl.Constant(0.0),
                depth = IgnitorDsl.Constant(0.8),
                attack = IgnitorDsl.Constant(0.05),
            ),
        )

        /** What `.reverb(wet = 0.5, size = 6)` writes into the owner's slot state. */
        val roomState: Map<String, Double> = mapOf("reverb.wet" to 0.5, "reverb.size" to 6.0)
    }
}
