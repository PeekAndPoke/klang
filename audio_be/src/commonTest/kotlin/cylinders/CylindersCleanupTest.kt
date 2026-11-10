/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers

/**
 * Tests for Cylinders round-robin cleanup functionality.
 * Verifies that inactive cylinders are skipped during mixing and that
 * cleanup happens in round-robin fashion.
 */
class CylindersCleanupTest : StringSpec({

    val blockFrames = 128
    val sampleRate = 44100

    // A block long after every claim below: no voice plays any more, so no check-in holds the cylinder
    // and the round-robin cleanup is decided by the silence gate alone.
    val afterLastVoice = 100.0 * blockFrames

    fun createTestVoice(cylinderId: Int): Voice {
        return VoiceTestHelpers.createSynthVoice(
            startFrame = 0.0,
            endFrame = 1000.0,
            cylinderId = cylinderId
        )
    }

    "a cylinder the cleanup has deactivated is not mixed, whatever its buffer holds; an active one is" {
        val cylinders = Cylinders(maxCylinders = 4, blockFrames = blockFrames, sampleRate = sampleRate, silentBlocksBeforeTailCheck = 0)
        val fusionMix = StereoBuffer(blockFrames)

        // Cylinder 0 silent, cylinder 1 sounding. The first block's cleanup visits cylinder 0 and frees it.
        val cylinder0 = cylinders.offerAndCommit(0, createTestVoice(0), blockStart = 0.0)
        val cylinder1 = cylinders.offerAndCommit(1, createTestVoice(1), blockStart = 0.0)

        cylinder0.mixBuffer.clear()
        cylinder1.mixBuffer.left[0] = 0.5
        cylinder1.mixBuffer.right[0] = 0.5

        cylinders.processAndMix(fusionMix, afterLastVoice)

        cylinder0.isActive shouldBe false
        cylinder1.isActive shouldBe true

        // Signal written into the freed cylinder AFTER deactivation must not reach the mix; the
        // sounding cylinder's must, so a mix that skipped every cylinder would go red here too.
        cylinder0.mixBuffer.left[0] = 1.0
        cylinder0.mixBuffer.right[0] = 1.0
        cylinder1.mixBuffer.left[0] = 0.5
        cylinder1.mixBuffer.right[0] = 0.5
        fusionMix.clear()

        cylinders.processAndMix(fusionMix, afterLastVoice)

        fusionMix.left[0] shouldBe 0.5
        fusionMix.right[0] shouldBe 0.5
    }

    "round-robin cleanup checks one cylinder per block" {
        val cylinders = Cylinders(maxCylinders = 4, blockFrames = blockFrames, sampleRate = sampleRate, silentBlocksBeforeTailCheck = 0)
        val fusionMix = StereoBuffer(blockFrames)

        // Create 3 cylinders, all active but silent
        val cylinder0 = cylinders.offerAndCommit(0, createTestVoice(0), blockStart = 0.0)
        val cylinder1 = cylinders.offerAndCommit(1, createTestVoice(1), blockStart = 0.0)
        val cylinder2 = cylinders.offerAndCommit(2, createTestVoice(2), blockStart = 0.0)

        cylinder0.mixBuffer.clear()
        cylinder1.mixBuffer.clear()
        cylinder2.mixBuffer.clear()

        // All should be active initially
        cylinder0.isActive shouldBe true
        cylinder1.isActive shouldBe true
        cylinder2.isActive shouldBe true

        // Process multiple times - each time one cylinder should be checked
        assertSoftly {
            // Block 1: Should check cylinder 0
            cylinders.processAndMix(fusionMix, afterLastVoice)
            cylinder0.isActive shouldBe false  // Deactivated
            cylinder1.isActive shouldBe true   // Not checked yet
            cylinder2.isActive shouldBe true   // Not checked yet

            // Block 2: Should check cylinder 1
            cylinders.processAndMix(fusionMix, afterLastVoice)
            cylinder0.isActive shouldBe false
            cylinder1.isActive shouldBe false  // Deactivated
            cylinder2.isActive shouldBe true   // Not checked yet

            // Block 3: Should check cylinder 2
            cylinders.processAndMix(fusionMix, afterLastVoice)
            cylinder0.isActive shouldBe false
            cylinder1.isActive shouldBe false
            cylinder2.isActive shouldBe false  // Deactivated
        }
    }

    "round-robin wraps around after maxCylinders" {
        val cylinders = Cylinders(maxCylinders = 2, blockFrames = blockFrames, sampleRate = sampleRate, silentBlocksBeforeTailCheck = 0)
        val fusionMix = StereoBuffer(blockFrames)

        // Create 2 cylinders
        val cylinder0 = cylinders.offerAndCommit(0, createTestVoice(0), blockStart = 0.0)
        val cylinder1 = cylinders.offerAndCommit(1, createTestVoice(1), blockStart = 0.0)

        cylinder0.mixBuffer.clear()
        cylinder1.mixBuffer.clear()

        assertSoftly {
            // First cycle
            cylinders.processAndMix(fusionMix, afterLastVoice)
            cylinder0.isActive shouldBe false
            cylinder1.isActive shouldBe true

            cylinders.processAndMix(fusionMix, afterLastVoice)
            cylinder0.isActive shouldBe false
            cylinder1.isActive shouldBe false

            // Reactivate both
            cylinders.offerAndCommit(0, createTestVoice(0), blockStart = 0.0)
            cylinders.offerAndCommit(1, createTestVoice(1), blockStart = 0.0)
            cylinder0.mixBuffer.clear()
            cylinder1.mixBuffer.clear()

            // Should wrap around and check cylinder 0 again
            cylinders.processAndMix(fusionMix, afterLastVoice)
            cylinder0.isActive shouldBe false
            cylinder1.isActive shouldBe true
        }
    }

    "with more cylinders rented than maxCylinders, every one of them is cleaned up, negative ids included" {
        // KLANG-00428, audit finding L6-05: a folded id keeps its sign, so up to `2 * maxCylinders - 1` cylinders can be
        // rented, and the round-robin index wrapped at maxCylinders: the ones past it were never visited, stayed active
        // for good and kept a stopped engine alive (256 cylinders: 1 never cleaned; 509: 254).
        for ((maxCylinders, ids) in listOf(2 to listOf(-1, 0, 1), Cylinders.MAX_CYLINDERS to (-254..254).toList())) {
            val cylinders = Cylinders(
                blockFrames = blockFrames,
                sampleRate = sampleRate,
                silentBlocksBeforeTailCheck = 0,
                maxCylinders = maxCylinders,
            )
            val mix = StereoBuffer(blockFrames)

            for (id in ids) {
                cylinders.checkIn(id = id, blockStart = 0.0)
            }

            withClue("maxCylinders $maxCylinders: all ${ids.size} rented and active") {
                cylinders.cylinders.size shouldBe ids.size
                cylinders.cylinders.all { it.isActive } shouldBe true
            }

            // No grace: a silent cylinder goes at its first visit, one cylinder visited per block. Two laps.
            for (b in 1..(2 * ids.size)) {
                cylinders.clearAll()
                mix.clear()
                cylinders.processAndMix(fusionMix = mix, blockStart = b * blockFrames.toDouble() + afterLastVoice)
            }

            withClue("maxCylinders $maxCylinders: rent indices still active ${cylinders.cylinders.indices.filter { cylinders.cylinders[it].isActive }}") {
                cylinders.anyActive() shouldBe false
            }
        }
    }

    "cleanup handles empty cylinders map gracefully" {
        val cylinders = Cylinders(maxCylinders = 4, blockFrames = blockFrames, sampleRate = sampleRate, silentBlocksBeforeTailCheck = 0)
        val fusionMix = StereoBuffer(blockFrames)

        // No cylinders created - should not crash
        cylinders.processAndMix(fusionMix, afterLastVoice)

        // Should complete without error
        fusionMix.left[0] shouldBe 0.0
        fusionMix.right[0] shouldBe 0.0
    }

    "cleanup only checks existing cylinders" {
        val cylinders = Cylinders(maxCylinders = 8, blockFrames = blockFrames, sampleRate = sampleRate, silentBlocksBeforeTailCheck = 0)
        val fusionMix = StereoBuffer(blockFrames)

        // Create only cylinder 0 (sparse map)
        val cylinder0 = cylinders.offerAndCommit(0, createTestVoice(0), blockStart = 0.0)
        cylinder0.mixBuffer.clear()

        // Process 8 times (full round-robin cycle)
        // Should only check cylinder 0 when its index comes up
        repeat(8) {
            cylinders.processAndMix(fusionMix, afterLastVoice)
        }

        // Cylinder 0 should have been checked and deactivated
        cylinder0.isActive shouldBe false
    }

    "multiple active cylinders mix correctly" {
        val cylinders = Cylinders(maxCylinders = 4, blockFrames = blockFrames, sampleRate = sampleRate, silentBlocksBeforeTailCheck = 0)
        val fusionMix = StereoBuffer(blockFrames)

        // Create 3 active cylinders with different signals
        val cylinder0 = cylinders.offerAndCommit(0, createTestVoice(0), blockStart = 0.0)
        val cylinder1 = cylinders.offerAndCommit(1, createTestVoice(1), blockStart = 0.0)
        val cylinder2 = cylinders.offerAndCommit(2, createTestVoice(2), blockStart = 0.0)

        cylinder0.mixBuffer.left[0] = 0.1
        cylinder1.mixBuffer.left[0] = 0.2
        cylinder2.mixBuffer.left[0] = 0.3

        cylinder0.mixBuffer.right[0] = 0.1
        cylinder1.mixBuffer.right[0] = 0.2
        cylinder2.mixBuffer.right[0] = 0.3

        fusionMix.clear()

        // Process and mix
        cylinders.processAndMix(fusionMix, afterLastVoice)

        // All three should be summed
        fusionMix.left[0] shouldBe (0.6 plusOrMinus 0.0001)
        fusionMix.right[0] shouldBe (0.6 plusOrMinus 0.0001)
    }
})
