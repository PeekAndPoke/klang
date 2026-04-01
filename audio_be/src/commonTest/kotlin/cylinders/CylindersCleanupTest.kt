package io.peekandpoke.klang.audio_be.cylinders

import io.kotest.assertions.assertSoftly
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
class OrbitsCleanupTest : StringSpec({

    val blockFrames = 128
    val sampleRate = 44100

    fun createTestVoice(cylinderId: Int): Voice {
        return VoiceTestHelpers.createSynthVoice(
            startFrame = 0,
            endFrame = 1000,
            cylinderId = cylinderId
        )
    }

    "inactive cylinders are skipped during mixing" {
        val cylinders = Cylinders(maxCylinders = 4, blockFrames = blockFrames, sampleRate = sampleRate, silentBlocksBeforeTailCheck = 0)
        val fusionMix = StereoBuffer(blockFrames)

        // Create cylinder 0 and make it inactive
        val voice0 = createTestVoice(cylinderId = 0)
        val orbit0 = cylinders.getOrInit(0, voice0)
        orbit0.mixBuffer.clear()
        orbit0.tryDeactivate()
        orbit0.isActive shouldBe false

        // Create cylinder 1 with signal
        val voice1 = createTestVoice(cylinderId = 1)
        val orbit1 = cylinders.getOrInit(1, voice1)
        orbit1.mixBuffer.left[0] = 0.5f
        orbit1.mixBuffer.right[0] = 0.5f

        // Clear fusion mix
        fusionMix.clear()

        // Process and mix
        cylinders.processAndMix(fusionMix)

        // Master should only have cylinder 1's signal (cylinder 0 was skipped)
        fusionMix.left[0] shouldBe 0.5f
        fusionMix.right[0] shouldBe 0.5f
    }

    "round-robin cleanup checks one cylinder per block" {
        val cylinders = Cylinders(maxCylinders = 4, blockFrames = blockFrames, sampleRate = sampleRate, silentBlocksBeforeTailCheck = 0)
        val fusionMix = StereoBuffer(blockFrames)

        // Create 3 cylinders, all active but silent
        val orbit0 = cylinders.getOrInit(0, createTestVoice(0))
        val orbit1 = cylinders.getOrInit(1, createTestVoice(1))
        val orbit2 = cylinders.getOrInit(2, createTestVoice(2))

        orbit0.mixBuffer.clear()
        orbit1.mixBuffer.clear()
        orbit2.mixBuffer.clear()

        // All should be active initially
        orbit0.isActive shouldBe true
        orbit1.isActive shouldBe true
        orbit2.isActive shouldBe true

        // Process multiple times - each time one cylinder should be checked
        assertSoftly {
            // Block 1: Should check cylinder 0
            cylinders.processAndMix(fusionMix)
            orbit0.isActive shouldBe false  // Deactivated
            orbit1.isActive shouldBe true   // Not checked yet
            orbit2.isActive shouldBe true   // Not checked yet

            // Block 2: Should check cylinder 1
            cylinders.processAndMix(fusionMix)
            orbit0.isActive shouldBe false
            orbit1.isActive shouldBe false  // Deactivated
            orbit2.isActive shouldBe true   // Not checked yet

            // Block 3: Should check cylinder 2
            cylinders.processAndMix(fusionMix)
            orbit0.isActive shouldBe false
            orbit1.isActive shouldBe false
            orbit2.isActive shouldBe false  // Deactivated
        }
    }

    "round-robin wraps around after maxCylinders" {
        val cylinders = Cylinders(maxCylinders = 2, blockFrames = blockFrames, sampleRate = sampleRate, silentBlocksBeforeTailCheck = 0)
        val fusionMix = StereoBuffer(blockFrames)

        // Create 2 cylinders
        val orbit0 = cylinders.getOrInit(0, createTestVoice(0))
        val orbit1 = cylinders.getOrInit(1, createTestVoice(1))

        orbit0.mixBuffer.clear()
        orbit1.mixBuffer.clear()

        assertSoftly {
            // First cycle
            cylinders.processAndMix(fusionMix)
            orbit0.isActive shouldBe false
            orbit1.isActive shouldBe true

            cylinders.processAndMix(fusionMix)
            orbit0.isActive shouldBe false
            orbit1.isActive shouldBe false

            // Reactivate both
            cylinders.getOrInit(0, createTestVoice(0))
            cylinders.getOrInit(1, createTestVoice(1))
            orbit0.mixBuffer.clear()
            orbit1.mixBuffer.clear()

            // Should wrap around and check cylinder 0 again
            cylinders.processAndMix(fusionMix)
            orbit0.isActive shouldBe false
            orbit1.isActive shouldBe true
        }
    }

    "cleanup handles empty cylinders map gracefully" {
        val cylinders = Cylinders(maxCylinders = 4, blockFrames = blockFrames, sampleRate = sampleRate, silentBlocksBeforeTailCheck = 0)
        val fusionMix = StereoBuffer(blockFrames)

        // No cylinders created - should not crash
        cylinders.processAndMix(fusionMix)

        // Should complete without error
        fusionMix.left[0] shouldBe 0.0f
        fusionMix.right[0] shouldBe 0.0f
    }

    "inactive cylinder with signal is not mixed" {
        val cylinders = Cylinders(maxCylinders = 4, blockFrames = blockFrames, sampleRate = sampleRate, silentBlocksBeforeTailCheck = 0)
        val fusionMix = StereoBuffer(blockFrames)

        // Create cylinder and deactivate it
        val voice = createTestVoice(cylinderId = 0)
        val cylinder = cylinders.getOrInit(0, voice)
        cylinder.mixBuffer.clear()
        cylinder.tryDeactivate()

        // Add signal AFTER deactivation
        cylinder.mixBuffer.left[0] = 1.0f
        cylinder.mixBuffer.right[0] = 1.0f

        // Clear fusion
        fusionMix.clear()

        // Process and mix
        cylinders.processAndMix(fusionMix)

        // Master should be silent (inactive cylinder was skipped)
        fusionMix.left[0] shouldBe 0.0f
        fusionMix.right[0] shouldBe 0.0f
    }

    "cleanup only checks existing cylinders" {
        val cylinders = Cylinders(maxCylinders = 8, blockFrames = blockFrames, sampleRate = sampleRate, silentBlocksBeforeTailCheck = 0)
        val fusionMix = StereoBuffer(blockFrames)

        // Create only cylinder 0 (sparse map)
        val orbit0 = cylinders.getOrInit(0, createTestVoice(0))
        orbit0.mixBuffer.clear()

        // Process 8 times (full round-robin cycle)
        // Should only check cylinder 0 when its index comes up
        repeat(8) {
            cylinders.processAndMix(fusionMix)
        }

        // Cylinder 0 should have been checked and deactivated
        orbit0.isActive shouldBe false
    }

    "multiple active cylinders mix correctly" {
        val cylinders = Cylinders(maxCylinders = 4, blockFrames = blockFrames, sampleRate = sampleRate, silentBlocksBeforeTailCheck = 0)
        val fusionMix = StereoBuffer(blockFrames)

        // Create 3 active cylinders with different signals
        val orbit0 = cylinders.getOrInit(0, createTestVoice(0))
        val orbit1 = cylinders.getOrInit(1, createTestVoice(1))
        val orbit2 = cylinders.getOrInit(2, createTestVoice(2))

        orbit0.mixBuffer.left[0] = 0.1f
        orbit1.mixBuffer.left[0] = 0.2f
        orbit2.mixBuffer.left[0] = 0.3f

        orbit0.mixBuffer.right[0] = 0.1f
        orbit1.mixBuffer.right[0] = 0.2f
        orbit2.mixBuffer.right[0] = 0.3f

        fusionMix.clear()

        // Process and mix
        cylinders.processAndMix(fusionMix)

        // All three should be summed
        fusionMix.left[0].toDouble() shouldBe (0.6 plusOrMinus 0.0001)
        fusionMix.right[0].toDouble() shouldBe (0.6 plusOrMinus 0.0001)
    }

    "cylinder deactivated by cleanup is not mixed on next block" {
        val cylinders = Cylinders(maxCylinders = 4, blockFrames = blockFrames, sampleRate = sampleRate, silentBlocksBeforeTailCheck = 0)
        val fusionMix = StereoBuffer(blockFrames)

        // Create silent cylinder
        val orbit0 = cylinders.getOrInit(0, createTestVoice(0))
        orbit0.mixBuffer.clear()

        // First block: cleanup deactivates cylinder 0
        cylinders.processAndMix(fusionMix)
        orbit0.isActive shouldBe false

        // Add signal to the now-inactive cylinder
        orbit0.mixBuffer.left[0] = 1.0f
        orbit0.mixBuffer.right[0] = 1.0f

        fusionMix.clear()

        // Second block: should NOT mix the inactive cylinder
        cylinders.processAndMix(fusionMix)
        fusionMix.left[0] shouldBe 0.0f
        fusionMix.right[0] shouldBe 0.0f
    }
})
