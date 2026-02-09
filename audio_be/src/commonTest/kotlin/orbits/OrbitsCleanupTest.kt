package io.peekandpoke.klang.audio_be.orbits

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers

/**
 * Tests for Orbits round-robin cleanup functionality.
 * Verifies that inactive orbits are skipped during mixing and that
 * cleanup happens in round-robin fashion.
 */
class OrbitsCleanupTest : StringSpec({

    val blockFrames = 128
    val sampleRate = 44100

    fun createTestVoice(orbitId: Int): Voice {
        return VoiceTestHelpers.createSynthVoice(
            startFrame = 0,
            endFrame = 1000,
            orbitId = orbitId
        )
    }

    "inactive orbits are skipped during mixing" {
        val orbits = Orbits(maxOrbits = 4, blockFrames = blockFrames, sampleRate = sampleRate)
        val masterMix = StereoBuffer(blockFrames)

        // Create orbit 0 and make it inactive
        val voice0 = createTestVoice(orbitId = 0)
        val orbit0 = orbits.getOrInit(0, voice0)
        orbit0.mixBuffer.clear()
        orbit0.tryDeactivate()
        orbit0.isActive shouldBe false

        // Create orbit 1 with signal
        val voice1 = createTestVoice(orbitId = 1)
        val orbit1 = orbits.getOrInit(1, voice1)
        orbit1.mixBuffer.left[0] = 0.5
        orbit1.mixBuffer.right[0] = 0.5

        // Clear master mix
        masterMix.clear()

        // Process and mix
        orbits.processAndMix(masterMix)

        // Master should only have orbit 1's signal (orbit 0 was skipped)
        masterMix.left[0] shouldBe 0.5
        masterMix.right[0] shouldBe 0.5
    }

    "round-robin cleanup checks one orbit per block" {
        val orbits = Orbits(maxOrbits = 4, blockFrames = blockFrames, sampleRate = sampleRate)
        val masterMix = StereoBuffer(blockFrames)

        // Create 3 orbits, all active but silent
        val orbit0 = orbits.getOrInit(0, createTestVoice(0))
        val orbit1 = orbits.getOrInit(1, createTestVoice(1))
        val orbit2 = orbits.getOrInit(2, createTestVoice(2))

        orbit0.mixBuffer.clear()
        orbit1.mixBuffer.clear()
        orbit2.mixBuffer.clear()

        // All should be active initially
        orbit0.isActive shouldBe true
        orbit1.isActive shouldBe true
        orbit2.isActive shouldBe true

        // Process multiple times - each time one orbit should be checked
        assertSoftly {
            // Block 1: Should check orbit 0
            orbits.processAndMix(masterMix)
            orbit0.isActive shouldBe false  // Deactivated
            orbit1.isActive shouldBe true   // Not checked yet
            orbit2.isActive shouldBe true   // Not checked yet

            // Block 2: Should check orbit 1
            orbits.processAndMix(masterMix)
            orbit0.isActive shouldBe false
            orbit1.isActive shouldBe false  // Deactivated
            orbit2.isActive shouldBe true   // Not checked yet

            // Block 3: Should check orbit 2
            orbits.processAndMix(masterMix)
            orbit0.isActive shouldBe false
            orbit1.isActive shouldBe false
            orbit2.isActive shouldBe false  // Deactivated
        }
    }

    "round-robin wraps around after maxOrbits" {
        val orbits = Orbits(maxOrbits = 2, blockFrames = blockFrames, sampleRate = sampleRate)
        val masterMix = StereoBuffer(blockFrames)

        // Create 2 orbits
        val orbit0 = orbits.getOrInit(0, createTestVoice(0))
        val orbit1 = orbits.getOrInit(1, createTestVoice(1))

        orbit0.mixBuffer.clear()
        orbit1.mixBuffer.clear()

        assertSoftly {
            // First cycle
            orbits.processAndMix(masterMix)
            orbit0.isActive shouldBe false
            orbit1.isActive shouldBe true

            orbits.processAndMix(masterMix)
            orbit0.isActive shouldBe false
            orbit1.isActive shouldBe false

            // Reactivate both
            orbits.getOrInit(0, createTestVoice(0))
            orbits.getOrInit(1, createTestVoice(1))
            orbit0.mixBuffer.clear()
            orbit1.mixBuffer.clear()

            // Should wrap around and check orbit 0 again
            orbits.processAndMix(masterMix)
            orbit0.isActive shouldBe false
            orbit1.isActive shouldBe true
        }
    }

    "cleanup handles empty orbits map gracefully" {
        val orbits = Orbits(maxOrbits = 4, blockFrames = blockFrames, sampleRate = sampleRate)
        val masterMix = StereoBuffer(blockFrames)

        // No orbits created - should not crash
        orbits.processAndMix(masterMix)

        // Should complete without error
        masterMix.left[0] shouldBe 0.0
        masterMix.right[0] shouldBe 0.0
    }

    "inactive orbit with signal is not mixed" {
        val orbits = Orbits(maxOrbits = 4, blockFrames = blockFrames, sampleRate = sampleRate)
        val masterMix = StereoBuffer(blockFrames)

        // Create orbit and deactivate it
        val voice = createTestVoice(orbitId = 0)
        val orbit = orbits.getOrInit(0, voice)
        orbit.mixBuffer.clear()
        orbit.tryDeactivate()

        // Add signal AFTER deactivation
        orbit.mixBuffer.left[0] = 1.0
        orbit.mixBuffer.right[0] = 1.0

        // Clear master
        masterMix.clear()

        // Process and mix
        orbits.processAndMix(masterMix)

        // Master should be silent (inactive orbit was skipped)
        masterMix.left[0] shouldBe 0.0
        masterMix.right[0] shouldBe 0.0
    }

    "cleanup only checks existing orbits" {
        val orbits = Orbits(maxOrbits = 8, blockFrames = blockFrames, sampleRate = sampleRate)
        val masterMix = StereoBuffer(blockFrames)

        // Create only orbit 0 (sparse map)
        val orbit0 = orbits.getOrInit(0, createTestVoice(0))
        orbit0.mixBuffer.clear()

        // Process 8 times (full round-robin cycle)
        // Should only check orbit 0 when its index comes up
        repeat(8) {
            orbits.processAndMix(masterMix)
        }

        // Orbit 0 should have been checked and deactivated
        orbit0.isActive shouldBe false
    }

    "multiple active orbits mix correctly" {
        val orbits = Orbits(maxOrbits = 4, blockFrames = blockFrames, sampleRate = sampleRate)
        val masterMix = StereoBuffer(blockFrames)

        // Create 3 active orbits with different signals
        val orbit0 = orbits.getOrInit(0, createTestVoice(0))
        val orbit1 = orbits.getOrInit(1, createTestVoice(1))
        val orbit2 = orbits.getOrInit(2, createTestVoice(2))

        orbit0.mixBuffer.left[0] = 0.1
        orbit1.mixBuffer.left[0] = 0.2
        orbit2.mixBuffer.left[0] = 0.3

        orbit0.mixBuffer.right[0] = 0.1
        orbit1.mixBuffer.right[0] = 0.2
        orbit2.mixBuffer.right[0] = 0.3

        masterMix.clear()

        // Process and mix
        orbits.processAndMix(masterMix)

        // All three should be summed
        masterMix.left[0] shouldBe (0.6 plusOrMinus 0.0001)
        masterMix.right[0] shouldBe (0.6 plusOrMinus 0.0001)
    }

    "orbit deactivated by cleanup is not mixed on next block" {
        val orbits = Orbits(maxOrbits = 4, blockFrames = blockFrames, sampleRate = sampleRate)
        val masterMix = StereoBuffer(blockFrames)

        // Create silent orbit
        val orbit0 = orbits.getOrInit(0, createTestVoice(0))
        orbit0.mixBuffer.clear()

        // First block: cleanup deactivates orbit 0
        orbits.processAndMix(masterMix)
        orbit0.isActive shouldBe false

        // Add signal to the now-inactive orbit
        orbit0.mixBuffer.left[0] = 1.0
        orbit0.mixBuffer.right[0] = 1.0

        masterMix.clear()

        // Second block: should NOT mix the inactive orbit
        orbits.processAndMix(masterMix)
        masterMix.left[0] shouldBe 0.0
        masterMix.right[0] shouldBe 0.0
    }
})
