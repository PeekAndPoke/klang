package io.peekandpoke.klang.audio_be.orbits

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers

/**
 * Tests for Orbit cleanup functionality (tryDeactivate).
 * Verifies that silent orbits are properly deactivated to reduce processing overhead.
 */
class OrbitCleanupTest : StringSpec({

    val blockFrames = 128
    val sampleRate = 44100

    fun createTestOrbit(): Orbit {
        return Orbit(id = 0, blockFrames = blockFrames, sampleRate = sampleRate)
    }

    fun makeOrbitActive(orbit: Orbit) {
        // Create a minimal voice just to activate the orbit
        val voice = VoiceTestHelpers.createSynthVoice(
            startFrame = 0,
            endFrame = 1000
        )
        orbit.updateFromVoice(voice)
    }

    "tryDeactivate() deactivates a completely silent orbit" {
        val orbit = createTestOrbit()
        makeOrbitActive(orbit)

        // Verify it's active
        orbit.isActive shouldBe true

        // Fill buffer with zeros (silent)
        orbit.mixBuffer.clear()

        // Try to deactivate
        orbit.tryDeactivate()

        // Should now be inactive
        orbit.isActive shouldBe false
    }

    "tryDeactivate() does NOT deactivate an orbit with signal" {
        val orbit = createTestOrbit()
        makeOrbitActive(orbit)

        // Verify it's active
        orbit.isActive shouldBe true

        // Add some signal to the buffer
        orbit.mixBuffer.left[0] = 0.5
        orbit.mixBuffer.right[0] = 0.5

        // Try to deactivate
        orbit.tryDeactivate()

        // Should still be active
        orbit.isActive shouldBe true
    }

    "tryDeactivate() respects threshold of 0.0001" {
        val orbit = createTestOrbit()
        makeOrbitActive(orbit)

        // Fill with values just below threshold
        for (i in 0 until blockFrames) {
            orbit.mixBuffer.left[i] = 0.00005
            orbit.mixBuffer.right[i] = 0.00005
        }

        orbit.tryDeactivate()

        // Should be deactivated (below threshold)
        orbit.isActive shouldBe false
    }

    "tryDeactivate() keeps orbit active when signal exceeds threshold" {
        val orbit = createTestOrbit()
        makeOrbitActive(orbit)

        // Fill with values just above threshold
        for (i in 0 until blockFrames) {
            orbit.mixBuffer.left[i] = 0.0002
            orbit.mixBuffer.right[i] = 0.0002
        }

        orbit.tryDeactivate()

        // Should remain active (above threshold)
        orbit.isActive shouldBe true
    }

    "tryDeactivate() checks both channels - left channel has signal" {
        val orbit = createTestOrbit()
        makeOrbitActive(orbit)

        // Left channel has signal, right is silent
        orbit.mixBuffer.left[64] = 0.1
        orbit.mixBuffer.right.fill(0.0)

        orbit.tryDeactivate()

        // Should remain active
        orbit.isActive shouldBe true
    }

    "tryDeactivate() checks both channels - right channel has signal" {
        val orbit = createTestOrbit()
        makeOrbitActive(orbit)

        // Right channel has signal, left is silent
        orbit.mixBuffer.left.fill(0.0)
        orbit.mixBuffer.right[64] = 0.1

        orbit.tryDeactivate()

        // Should remain active
        orbit.isActive shouldBe true
    }

    "tryDeactivate() handles negative values correctly" {
        val orbit = createTestOrbit()
        makeOrbitActive(orbit)

        // Fill with negative values above threshold
        orbit.mixBuffer.left[0] = -0.5
        orbit.mixBuffer.right[0] = -0.3

        orbit.tryDeactivate()

        // Should remain active (abs value matters)
        orbit.isActive shouldBe true
    }

    "tryDeactivate() on already inactive orbit does nothing" {
        val orbit = createTestOrbit()
        makeOrbitActive(orbit)

        // Deactivate it first
        orbit.mixBuffer.clear()
        orbit.tryDeactivate()
        orbit.isActive shouldBe false

        // Add signal to buffer
        orbit.mixBuffer.left[0] = 0.5

        // Try to deactivate again - should exit early
        orbit.tryDeactivate()

        // Should still be inactive (didn't check buffer)
        orbit.isActive shouldBe false
    }

    "orbit reactivates when updateFromVoice is called" {
        val orbit = createTestOrbit()
        makeOrbitActive(orbit)

        // Deactivate it
        orbit.mixBuffer.clear()
        orbit.tryDeactivate()
        orbit.isActive shouldBe false

        // Reactivate by calling updateFromVoice
        makeOrbitActive(orbit)

        // Should be active again
        orbit.isActive shouldBe true
    }

    "tryDeactivate() with signal only in middle of buffer" {
        val orbit = createTestOrbit()
        makeOrbitActive(orbit)

        // Silent except for one sample in the middle
        orbit.mixBuffer.clear()
        orbit.mixBuffer.left[64] = 0.01

        orbit.tryDeactivate()

        // Should remain active
        orbit.isActive shouldBe true
    }

    "tryDeactivate() with signal only at end of buffer" {
        val orbit = createTestOrbit()
        makeOrbitActive(orbit)

        // Silent except for last sample
        orbit.mixBuffer.clear()
        orbit.mixBuffer.right[blockFrames - 1] = 0.001

        orbit.tryDeactivate()

        // Should remain active
        orbit.isActive shouldBe true
    }
})
