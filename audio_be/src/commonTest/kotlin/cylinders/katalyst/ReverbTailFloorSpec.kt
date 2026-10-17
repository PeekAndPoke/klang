/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.doubles.shouldBeLessThanOrEqual
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.effects.Reverb
import io.peekandpoke.klang.audio_be.warehouse.ReverbUnits
import io.peekandpoke.klang.audio_bridge.constants.SILENCE_FLOOR
import kotlin.math.abs
import kotlin.math.log10

/**
 * The reverb's two tail floors (maintainer, 2026-10-17). Ordinary playback holds a room to the engine's
 * [SILENCE_FLOOR] on its comb cells, bit for bit as before; only a room whose chain DRAINS after a swap
 * (`KatalystChain.measureTails`, `ChainSwap.TAIL_MEASURE_SECONDS`) answers against `Reverb.TAIL_FLOOR`, whose
 * output is about -90 dBFS. The drain's window is a literal, read from no constant: a floor moved by 10 dB either
 * way (-80 or -100 dBFS at the output) moves the end out of it. Broadband charges on purpose: a pure tone partly
 * cancels in the comb sum (output about 0.02 x the comb peak, `ChainSwapCapSpec`'s rig), which would hide a floor
 * set too high.
 */
class ReverbTailFloorSpec : StringSpec({

    val sampleRate = 48000
    val blockFrames = 128
    val measureEvery = (0.5 * sampleRate / blockFrames).toInt()

    class End(val levelDb: Double, val combPeak: Double)

    /** [size] on, then 2 s of fixed-seed noise (the same bits on every platform). */
    fun charge(fx: KatalystReverbEffect, ctx: KatalystContext, size: Double) {
        var seed = 12345

        fx.configure(size = size, lowpass = null, wet = 0.5)

        repeat(2 * sampleRate / blockFrames) {
            for (i in 0 until blockFrames) {
                seed = seed * 1103515245 + 12345
                val v = 0.3 * (((seed ushr 8) and 0xFFFF) / 32768.0 - 1.0)

                ctx.mixBuffer.left[i] = v
                ctx.mixBuffer.right[i] = v
            }

            fx.process(ctx)
        }
    }

    /**
     * Silence until the stage reports no tail; with [drain] it is measured every 0.5 s, as a swap's drain does.
     * Returns the output peak of that last block and what the combs still held.
     */
    fun runOut(fx: KatalystReverbEffect, ctx: KatalystContext, drain: Boolean): End {
        val mix = ctx.mixBuffer
        var k = 0

        while (k < 60 * sampleRate / blockFrames) {
            mix.clear()
            fx.process(ctx)

            var last = 0.0
            for (i in 0 until blockFrames) {
                last = maxOf(last, abs(mix.left[i]), abs(mix.right[i]))
            }

            if (drain && k % measureEvery == 0) {
                fx.measureTail()
            }

            if (!fx.hasTail()) {
                return End(levelDb = 20.0 * log10(last), combPeak = fx.reverb!!.combPeakAbs())
            }

            k++
        }

        error("the room never reported the end of its tail")
    }

    fun ctx() = KatalystContext(blockFrames = blockFrames, mixBuffer = StereoBuffer(blockFrames))

    "a broadband room drained like a swap's ends with its output near -90 dBFS: between -101 and -88, at three sizes" {
        // Measured 2026-10-17: -93.0 (size 0.3), -96.9 (0.6), -92.9 (1.0) dBFS.
        for (size in listOf(0.3, 0.6, 1.0)) {
            val fx = KatalystReverbEffect(reverb = Reverb(sampleRate), blockFrames = blockFrames)
            val c = ctx()

            charge(fx, c, size)
            val level = runOut(fx, c, drain = true).levelDb

            withClue("size $size ended at $level dBFS") {
                level shouldBeLessThan -88.0
                level shouldBeGreaterThan -101.0
            }
        }
    }

    "an ordinary room, not draining, still holds its tail until its combs are under SILENCE_FLOOR" {
        for (size in listOf(0.3, 1.0)) {
            val fx = KatalystReverbEffect(reverb = Reverb(sampleRate), blockFrames = blockFrames)
            val c = ctx()

            charge(fx, c, size)
            val end = runOut(fx, c, drain = false)

            withClue("size $size: combs ${end.combPeak} at the end, output ${end.levelDb} dBFS") {
                end.combPeak shouldBeLessThanOrEqual SILENCE_FLOOR
                end.levelDb shouldBeLessThan -110.0
            }
        }
    }

    "a stage re-used after a drain is back on the ordinary floor, through retire and through reset" {
        for (way in listOf("retire", "reset")) {
            val fx = KatalystReverbEffect(units = ReverbUnits(sampleRate), blockFrames = blockFrames)
            val c = ctx()

            // A first life that drained after a swap: measured, so on the drain's floor.
            charge(fx, c, 1.0)
            val drained = runOut(fx, c, drain = true)

            withClue("positive control: the drain ended on its own floor, combs well above SILENCE_FLOOR") {
                drained.combPeak shouldBeGreaterThan 10 * SILENCE_FLOOR
            }

            when (way) {
                "retire" -> fx.retire()
                else -> fx.reset()
            }

            // The next life, an ordinary one: never measured.
            charge(fx, c, 0.6)
            val end = runOut(fx, c, drain = false)

            withClue("after $way: combs ${end.combPeak} at the end") {
                end.combPeak shouldBeLessThanOrEqual SILENCE_FLOOR
            }
        }
    }
})
