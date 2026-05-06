package io.peekandpoke.klang.audio_be.cylinders

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers

/**
 * Tests for Cylinder cleanup functionality (tryDeactivate).
 * Verifies that silent cylinders are properly deactivated to reduce processing overhead.
 */
class OrbitCleanupTest : StringSpec({

    val blockFrames = 128
    val sampleRate = 44100

    fun createTestOrbit(): Cylinder {
        return Cylinder(id = 0, blockFrames = blockFrames, sampleRate = sampleRate, silentBlocksBeforeTailCheck = 0)
    }

    fun makeOrbitActive(cylinder: Cylinder) {
        // Create a minimal voice just to activate the cylinder
        val voice = VoiceTestHelpers.createSynthVoice(
            startFrame = 0,
            endFrame = 1000
        )
        cylinder.updateFromVoice(voice)
    }

    "tryDeactivate() deactivates a completely silent cylinder" {
        val cylinder = createTestOrbit()
        makeOrbitActive(cylinder)

        // Verify it's active
        cylinder.isActive shouldBe true

        // Fill buffer with zeros (silent)
        cylinder.mixBuffer.clear()

        // Try to deactivate
        cylinder.tryDeactivate()

        // Should now be inactive
        cylinder.isActive shouldBe false
    }

    "tryDeactivate() does NOT deactivate an cylinder with signal" {
        val cylinder = createTestOrbit()
        makeOrbitActive(cylinder)

        // Verify it's active
        cylinder.isActive shouldBe true

        // Add some signal to the buffer
        cylinder.mixBuffer.left[0] = 0.5
        cylinder.mixBuffer.right[0] = 0.5

        // Try to deactivate
        cylinder.tryDeactivate()

        // Should still be active
        cylinder.isActive shouldBe true
    }

    "tryDeactivate() respects threshold of 0.0001" {
        val cylinder = createTestOrbit()
        makeOrbitActive(cylinder)

        // Fill with values just below threshold
        for (i in 0 until blockFrames) {
            cylinder.mixBuffer.left[i] = 0.000005
            cylinder.mixBuffer.right[i] = 0.000005
        }

        cylinder.tryDeactivate()

        // Should be deactivated (below threshold)
        cylinder.isActive shouldBe false
    }

    "tryDeactivate() keeps cylinder active when signal exceeds threshold" {
        val cylinder = createTestOrbit()
        makeOrbitActive(cylinder)

        // Fill with values just above threshold
        for (i in 0 until blockFrames) {
            cylinder.mixBuffer.left[i] = 0.0002
            cylinder.mixBuffer.right[i] = 0.0002
        }

        cylinder.tryDeactivate()

        // Should remain active (above threshold)
        cylinder.isActive shouldBe true
    }

    "tryDeactivate() checks both channels - left channel has signal" {
        val cylinder = createTestOrbit()
        makeOrbitActive(cylinder)

        // Left channel has signal, right is silent
        cylinder.mixBuffer.left[64] = 0.1
        cylinder.mixBuffer.right.fill(0.0)

        cylinder.tryDeactivate()

        // Should remain active
        cylinder.isActive shouldBe true
    }

    "tryDeactivate() checks both channels - right channel has signal" {
        val cylinder = createTestOrbit()
        makeOrbitActive(cylinder)

        // Right channel has signal, left is silent
        cylinder.mixBuffer.left.fill(0.0)
        cylinder.mixBuffer.right[64] = 0.1

        cylinder.tryDeactivate()

        // Should remain active
        cylinder.isActive shouldBe true
    }

    "tryDeactivate() handles negative values correctly" {
        val cylinder = createTestOrbit()
        makeOrbitActive(cylinder)

        // Fill with negative values above threshold
        cylinder.mixBuffer.left[0] = -0.5
        cylinder.mixBuffer.right[0] = -0.3

        cylinder.tryDeactivate()

        // Should remain active (abs value matters)
        cylinder.isActive shouldBe true
    }

    "tryDeactivate() on already inactive cylinder does nothing" {
        val cylinder = createTestOrbit()
        makeOrbitActive(cylinder)

        // Deactivate it first
        cylinder.mixBuffer.clear()
        cylinder.tryDeactivate()
        cylinder.isActive shouldBe false

        // Add signal to buffer
        cylinder.mixBuffer.left[0] = 0.5

        // Try to deactivate again - should exit early
        cylinder.tryDeactivate()

        // Should still be inactive (didn't check buffer)
        cylinder.isActive shouldBe false
    }

    "cylinder reactivates when updateFromVoice is called" {
        val cylinder = createTestOrbit()
        makeOrbitActive(cylinder)

        // Deactivate it
        cylinder.mixBuffer.clear()
        cylinder.tryDeactivate()
        cylinder.isActive shouldBe false

        // Reactivate by calling updateFromVoice
        makeOrbitActive(cylinder)

        // Should be active again
        cylinder.isActive shouldBe true
    }

    "tryDeactivate() with signal only in middle of buffer" {
        val cylinder = createTestOrbit()
        makeOrbitActive(cylinder)

        // Silent except for one sample in the middle
        cylinder.mixBuffer.clear()
        cylinder.mixBuffer.left[64] = 0.01

        cylinder.tryDeactivate()

        // Should remain active
        cylinder.isActive shouldBe true
    }

    "tryDeactivate() with signal only at end of buffer" {
        val cylinder = createTestOrbit()
        makeOrbitActive(cylinder)

        // Silent except for last sample
        cylinder.mixBuffer.clear()
        cylinder.mixBuffer.right[blockFrames - 1] = 0.001

        cylinder.tryDeactivate()

        // Should remain active
        cylinder.isActive shouldBe true
    }
})
