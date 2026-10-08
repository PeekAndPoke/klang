/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.filters.ResonatorConfig
import io.peekandpoke.klang.audio_bridge.BodyMaterials
import io.peekandpoke.klang.audio_bridge.VowelBands
import java.lang.management.ManagementFactory

/**
 * Engine tidy-up step 12 (a): a material or vowel change allocates NOTHING once the stage's pool exists. Before the
 * step every change built two new filter banks on the audio thread (about 100 objects for eight bands); now an install
 * reconfigures the pair nobody hears. Measured by the bytes this thread allocates (`com.sun.management.ThreadMXBean`),
 * the [io.peekandpoke.klang.audio_be.ignitor.FirstBlockAllocationSpec] way.
 *
 * Each run drives one stage through changes every few blocks (so fades land and installs happen), bursts of a change
 * every block (so changes park and the parking is offered on the landing), offs and returns. The first run builds the
 * pool and warms the JIT; over the later runs the fewest bytes any run took must be 0. The fewest, because a one-off
 * (a JIT recompilation in the middle of a run) lands in one run, while a change that allocates does so in every run.
 */
class KatalystResonatorAllocationSpec : StringSpec({

    val mx = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
    val blockFrames = 128

    fun bytes(): Double = mx.currentThreadAllocatedBytes.toDouble()

    fun changesAllocateNothing(kind: ResonatorKind, indices: DoubleArray) {
        val fx = KatalystResonatorEffect(kind = kind, sampleRate = 48000.0, blockFrames = blockFrames)
        val ctx = KatalystContext(blockFrames = blockFrames, mixBuffer = StereoBuffer(blockFrames))
        // The configs a writer would hold, built up front: the measured loop must allocate only what the stage does.
        val configs = Array(indices.size) {
            ResonatorConfig(table = ResonatorTables.at(kind = kind, slotValue = indices[it]), mix = 0.6)
        }
        val off = ResonatorConfig(table = null)
        var fewest = Double.MAX_VALUE
        var installs = 0

        for (run in 0 until 10) {
            val b0 = bytes()

            for (block in 0 until 400) {
                // A change every 9 blocks, a burst of one per block in every fourth stretch, an off now and then.
                val step = if ((block / 36) % 4 == 3) block else block / 9
                val config = if (step % 7 == 6) off else configs[step % configs.size]

                fx.configure(config)

                for (i in 0 until blockFrames) {
                    ctx.mixBuffer.left[i] = ((i * 37 + block) % 101) / 101.0 - 0.5
                    ctx.mixBuffer.right[i] = ((i * 53 + block) % 89) / 89.0 - 0.5
                }

                fx.process(ctx)
            }

            val b1 = bytes()

            if (run >= 4) {
                fewest = minOf(fewest, b1 - b0)
            }

            installs = fx.installs
        }

        withClue("$kind: $installs installs over the runs; the fewest bytes a run of 400 blocks took was $fewest") {
            installs shouldBeGreaterThan 100
            fewest shouldBe 0.0
        }
    }

    "a body material change allocates nothing once the pool exists" {
        changesAllocateNothing(
            kind = ResonatorKind.BODY,
            indices = doubleArrayOf(
                BodyMaterials.indexOf("wood"),
                BodyMaterials.indexOf("glass"),
                BodyMaterials.indexOf("brass"),
                BodyMaterials.indexOf("bell"),
            ),
        )
    }

    "a vowel change allocates nothing once the pool exists" {
        changesAllocateNothing(
            kind = ResonatorKind.VOWEL,
            indices = doubleArrayOf(
                VowelBands.indexOf("bass:a"),
                VowelBands.indexOf("soprano:i"),
                VowelBands.indexOf("tenor:o"),
                VowelBands.indexOf("alto:u"),
            ),
        )
    }
})
