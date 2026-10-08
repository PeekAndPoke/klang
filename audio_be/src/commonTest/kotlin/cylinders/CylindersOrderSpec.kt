/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers

/**
 * [Cylinders] keeps its orbits in the order they were rented and finds one by its id without a map (tidy-up step 7,
 * audit item B4.3): the per-block walks are index loops, and the order they walk is the order the orbits were
 * rented in, which is the mix's summation order and the round-robin cleanup's. An orbit id folds by the cylinder
 * count and keeps its sign; the duck finds its sidechain by the raw id.
 */
class CylindersOrderSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128

    fun cylinders(silentBlocksBeforeTailCheck: Int = 10) = Cylinders(
        blockFrames = blockFrames,
        sampleRate = sampleRate,
        silentBlocksBeforeTailCheck = silentBlocksBeforeTailCheck,
    )

    "the orbits sum into the mix in the order they were rented, not by id" {
        val c = cylinders()
        // Rented 7, 3, 5. Summed in that order, (1e16 - 1e16) + 1 is 1. By id (3, 5, 7), -1e16 + 1 rounds back to
        // -1e16 and the sum is 0; in reverse rent order (5, 3, 7) likewise 0.
        val values = listOf(7 to 1e16, 3 to -1e16, 5 to 1.0)

        for ((id, value) in values) {
            c.checkIn(id = id, blockStart = 0.0).mixBuffer.left[0] = value
        }

        val mix = StereoBuffer(blockFrames)

        c.processAndMix(fusionMix = mix, blockStart = 0.0)

        withClue("the classic chain at its defaults leaves each orbit's sample as it was") {
            c.cylinders.map { it.mixBuffer.left[0] } shouldBe values.map { it.second }
        }

        mix.left[0] shouldBe 1.0
    }

    "the round-robin cleanup visits the orbits in rent order" {
        val c = cylinders(silentBlocksBeforeTailCheck = 1)
        val mix = StereoBuffer(blockFrames)

        // Rented 7, 3, 5 in block 0, silent, and never checked in again: an orbit goes at its first visit two blocks
        // after its last check-in. Visits run 7, 3, 5, 7, 3: 7 and 3 are still held in blocks 0 and 1, so 5 goes
        // in block 2, 7 in block 3, 3 in block 4. By id (3, 5, 7) the visit of block 2 would take 7.
        for (id in listOf(7, 3, 5)) {
            c.checkIn(id = id, blockStart = 0.0)
        }

        val wentInBlock = mutableMapOf<Int, Int>()

        for (block in 0 until 6) {
            mix.clear()
            c.clearAll()
            c.processAndMix(fusionMix = mix, blockStart = block * blockFrames.toDouble())

            for (cylinder in c.cylinders) {
                if (!cylinder.isActive && cylinder.id !in wentInBlock) {
                    wentInBlock[cylinder.id] = block
                }
            }
        }

        wentInBlock shouldBe mapOf(5 to 2, 7 to 3, 3 to 4)
    }

    "an orbit id folds by the cylinder count and keeps its sign" {
        val c = cylinders()
        val max = Cylinders.MAX_CYLINDERS

        val three = c.checkIn(id = 3, blockStart = 0.0)
        val minusThree = c.checkIn(id = -3, blockStart = 0.0)
        val top = c.checkIn(id = max - 1, blockStart = 0.0)
        val bottom = c.checkIn(id = -(max - 1), blockStart = 0.0)

        withClue("3 + max folds onto 3, -3 - max onto -3") {
            c.checkIn(id = 3 + max, blockStart = 0.0) shouldBeSameInstanceAs three
            c.checkIn(id = -3 - max, blockStart = 0.0) shouldBeSameInstanceAs minusThree
        }

        withClue("-3 is an orbit of its own, and so are both ends of the folded range") {
            minusThree shouldNotBeSameInstanceAs three
            c.checkIn(id = max, blockStart = 0.0) shouldNotBeSameInstanceAs top
            c.checkIn(id = -max, blockStart = 0.0) shouldNotBeSameInstanceAs bottom
        }

        withClue("ids are the folded ones, listed in rent order") {
            c.cylindersIds.toList() shouldBe listOf(3, -3, max - 1, -(max - 1), 0)
        }
    }

    "the duck finds its sidechain by the raw orbit id, never by a folded twin" {
        val max = Cylinders.MAX_CYLINDERS

        /** Orbit 3 ducked by the orbit named [duckOrbit] in its slots, while orbit [loudOrbit] is loud. */
        fun duckedLevel(duckOrbit: Int, loudOrbit: Int?): Double {
            val c = cylinders()
            val voice = VoiceTestHelpers.createSynthVoice(
                sampleRate = sampleRate,
                blockFrames = blockFrames,
                katalystParams = mapOf("duck.orbit" to duckOrbit.toDouble(), "duck.attack" to 0.001, "duck.depth" to 1.0),
            )

            c.offer(id = 3, voice = voice, blockStart = 0.0).mixBuffer.left.fill(0.5)

            if (loudOrbit != null) {
                c.checkIn(id = loudOrbit, blockStart = 0.0).mixBuffer.left.fill(0.9)
            }

            c.processAndMix(fusionMix = StereoBuffer(blockFrames), blockStart = 0.0)

            return c.cylinders.first { it.id == 3 }.mixBuffer.left[blockFrames - 1]
        }

        val undisturbed = duckedLevel(duckOrbit = 1, loudOrbit = null)

        withClue("positive control: a loud orbit 1 ducks orbit 3") {
            duckedLevel(duckOrbit = 1, loudOrbit = 1) shouldBeLessThan undisturbed
        }

        withClue("a negative orbit is found by its own id") {
            duckedLevel(duckOrbit = -2, loudOrbit = -2) shouldBeLessThan undisturbed
        }

        withClue("both ends of the folded range are found") {
            duckedLevel(duckOrbit = max - 1, loudOrbit = max - 1) shouldBeLessThan undisturbed
            duckedLevel(duckOrbit = -(max - 1), loudOrbit = -(max - 1)) shouldBeLessThan undisturbed
        }

        withClue("a duck naming orbit 1 + max does not find orbit 1, which that id folds onto") {
            duckedLevel(duckOrbit = 1 + max, loudOrbit = 1) shouldBe undisturbed
        }

        withClue("nor does max find orbit 0, nor -max find orbit 0") {
            duckedLevel(duckOrbit = max, loudOrbit = 0) shouldBe duckedLevel(duckOrbit = max, loudOrbit = null)
            duckedLevel(duckOrbit = -max, loudOrbit = 0) shouldBe duckedLevel(duckOrbit = -max, loudOrbit = null)
        }
    }

    "releaseAll returns every orbit and forgets them: the next use of an id rents again, in a new order" {
        val c = cylinders()

        val five = c.checkIn(id = 5, blockStart = 0.0)
        val two = c.checkIn(id = 2, blockStart = 0.0)

        c.releaseAll()

        c.cylinders.size shouldBe 0
        c.cylindersIds shouldBe emptySet()

        c.checkIn(id = 7, blockStart = 0.0)
        c.checkIn(id = 5, blockStart = 0.0)

        c.cylindersIds.toList() shouldBe listOf(7, 5)

        withClue("returned in rent order (5, then 2) to a last-in, first-out shelf: the first rent takes 2's") {
            c.cylinders[0] shouldBeSameInstanceAs two
            c.cylinders[1] shouldBeSameInstanceAs five
        }
    }
})
