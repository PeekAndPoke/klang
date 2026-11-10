/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers
import io.peekandpoke.klang.common.infra.KlangMap
import io.peekandpoke.klang.common.infra.klangMapOf
import kotlin.math.PI
import kotlin.math.sin

/**
 * Every duck's sidechain reads its source cylinder's mix BEFORE any duck writes (maintainer, 2026-11-10; audit findings
 * L6-04, L7b-08, KLANG-00428). Before, the duck pass walked the cylinders in rent order and ducked each mix in place,
 * so a duck read its source ducked or not by which cylinder had been used first, and two cylinders that duck each other
 * did not duck alike.
 */
class CylindersDuckOrderSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128
    val blocks = 40

    /** One cylinder of a run: its id, the slots its owner carries (its duck, or none), and its signal's level and rate. */
    class Part(val id: Int, val slots: KlangMap<Double>, val level: Double, val hz: Double)

    fun duckFrom(source: Int): KlangMap<Double> = klangMapOf("duck.cylinder" to source.toDouble(), "duck.depth" to 0.8, "duck.release" to 0.03)

    val noDuck: KlangMap<Double> = klangMapOf()

    /**
     * Renders [parts], rented in the order given, for [blocks] blocks: each block every part's owner offers itself and
     * its mix is a slow sine burst at its level (so the duck envelopes move). Returns each cylinder's mix after the duck
     * pass, by id, all blocks in a row.
     */
    fun render(parts: List<Part>): Map<Int, DoubleArray> {
        val c = Cylinders(blockFrames = blockFrames, sampleRate = sampleRate)
        val voices = parts.associate { p ->
            p.id to VoiceTestHelpers.createSynthVoice(sampleRate = sampleRate, blockFrames = blockFrames, katalystParams = p.slots)
        }
        val out = parts.associate { it.id to DoubleArray(blocks * blockFrames) }

        for (b in 0 until blocks) {
            val blockStart = (b * blockFrames).toDouble()

            c.clearAll()

            for (p in parts) {
                val mix = c.offer(id = p.id, voice = voices.getValue(p.id), blockStart = blockStart).mixBuffer

                for (i in 0 until blockFrames) {
                    val t = (b * blockFrames + i).toDouble() / sampleRate
                    val v = p.level * (0.5 + 0.5 * sin(2.0 * PI * p.hz * t))

                    mix.left[i] = v
                    mix.right[i] = v
                }
            }

            c.processAndMix(fusionMix = StereoBuffer(blockFrames), blockStart = blockStart)

            for (cylinder in c.cylinders) {
                cylinder.mixBuffer.left.copyInto(destination = out.getValue(cylinder.id), destinationOffset = b * blockFrames, startIndex = 0, endIndex = blockFrames)
            }
        }

        return out
    }

    fun firstDifference(a: DoubleArray, b: DoubleArray): Int? = a.indices.firstOrNull { a[it].toRawBits() != b[it].toRawBits() }

    "two cylinders that duck each other render the same, bit for bit, whichever was rented first" {
        val one = Part(id = 1, slots = duckFrom(2), level = 0.9, hz = 7.0)
        val two = Part(id = 2, slots = duckFrom(1), level = 0.7, hz = 11.0)

        val oneFirst = render(listOf(one, two))
        val twoFirst = render(listOf(two, one))

        withClue("positive control: the ducks duck (cylinder 1's last frame is under its dry level)") {
            oneFirst.getValue(1).last() shouldBeLessThan 0.9 * (0.5 + 0.5 * sin(2.0 * PI * 7.0 * (blocks * blockFrames - 1).toDouble() / sampleRate))
        }

        for (id in listOf(1, 2)) {
            withClue("cylinder $id: the first frame that differs between the two rent orders") {
                firstDifference(oneFirst.getValue(id), twoFirst.getValue(id)) shouldBe null
            }
        }
    }

    "a source that is itself ducked is read before its own duck, in every rent order" {
        // Kick on 1, bass on 2 ducked by the kick, pad on 3 ducked by the bass: the pad hears the bass as the bass's
        // chain left it, which is exactly the pad of a run where the bass is not ducked at all.
        val kick = Part(id = 1, slots = noDuck, level = 0.9, hz = 5.0)
        val bass = Part(id = 2, slots = duckFrom(1), level = 0.8, hz = 3.0)
        val pad = Part(id = 3, slots = duckFrom(2), level = 0.5, hz = 13.0)
        val reference = render(listOf(kick, bass.let { Part(id = it.id, slots = noDuck, level = it.level, hz = it.hz) }, pad)).getValue(3)

        withClue("positive control: the bass's own duck changes the bass") {
            (firstDifference(render(listOf(kick, bass, pad)).getValue(2), render(listOf(kick, Part(id = 2, slots = noDuck, level = 0.8, hz = 3.0), pad)).getValue(2)) != null) shouldBe true
        }

        for (order in listOf(listOf(kick, bass, pad), listOf(pad, bass, kick), listOf(bass, pad, kick))) {
            withClue("rented ${order.map { it.id }}: the pad's first frame that differs from the pad over an unducked bass") {
                firstDifference(render(order).getValue(3), reference) shouldBe null
            }
        }
    }
})
