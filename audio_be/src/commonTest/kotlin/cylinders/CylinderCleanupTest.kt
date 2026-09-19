/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers

/**
 * Tests for Cylinder cleanup functionality (tryDeactivate).
 * Verifies that silent cylinders are properly deactivated to reduce processing overhead.
 */
class OrbitCleanupTest : StringSpec({

    val blockFrames = 128
    val sampleRate = 44100

    // A block long after every claim below: no voice plays any more, so the orbit lease has lapsed
    // and what each row tests (the silence gate, the tails, the clean slate) is all that decides.
    val afterLastVoice = 100.0 * blockFrames

    fun createTestOrbit(): Cylinder {
        return Cylinder(id = 0, blockFrames = blockFrames, sampleRate = sampleRate, silentBlocksBeforeTailCheck = 0)
    }

    fun makeOrbitActive(cylinder: Cylinder) {
        // Create a minimal voice just to activate the cylinder
        val voice = VoiceTestHelpers.createSynthVoice(
            startFrame = 0.0,
            endFrame = 1000.0
        )
        cylinder.updateFromVoice(voice, blockStart = 0.0)
    }

    "tryDeactivate() deactivates a completely silent cylinder" {
        val cylinder = createTestOrbit()
        makeOrbitActive(cylinder)

        // Verify it's active
        cylinder.isActive shouldBe true

        // Fill buffer with zeros (silent)
        cylinder.mixBuffer.clear()

        // Try to deactivate
        cylinder.tryDeactivate(afterLastVoice)

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
        cylinder.tryDeactivate(afterLastVoice)

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

        cylinder.tryDeactivate(afterLastVoice)

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

        cylinder.tryDeactivate(afterLastVoice)

        // Should remain active (above threshold)
        cylinder.isActive shouldBe true
    }

    "tryDeactivate() checks both channels - left channel has signal" {
        val cylinder = createTestOrbit()
        makeOrbitActive(cylinder)

        // Left channel has signal, right is silent
        cylinder.mixBuffer.left[64] = 0.1
        cylinder.mixBuffer.right.fill(0.0)

        cylinder.tryDeactivate(afterLastVoice)

        // Should remain active
        cylinder.isActive shouldBe true
    }

    "tryDeactivate() checks both channels - right channel has signal" {
        val cylinder = createTestOrbit()
        makeOrbitActive(cylinder)

        // Right channel has signal, left is silent
        cylinder.mixBuffer.left.fill(0.0)
        cylinder.mixBuffer.right[64] = 0.1

        cylinder.tryDeactivate(afterLastVoice)

        // Should remain active
        cylinder.isActive shouldBe true
    }

    "tryDeactivate() handles negative values correctly" {
        val cylinder = createTestOrbit()
        makeOrbitActive(cylinder)

        // Fill with negative values above threshold
        cylinder.mixBuffer.left[0] = -0.5
        cylinder.mixBuffer.right[0] = -0.3

        cylinder.tryDeactivate(afterLastVoice)

        // Should remain active (abs value matters)
        cylinder.isActive shouldBe true
    }

    "tryDeactivate() on already inactive cylinder does nothing" {
        val cylinder = createTestOrbit()
        makeOrbitActive(cylinder)

        // Deactivate it first
        cylinder.mixBuffer.clear()
        cylinder.tryDeactivate(afterLastVoice)
        cylinder.isActive shouldBe false

        // Add signal to buffer
        cylinder.mixBuffer.left[0] = 0.5

        // Try to deactivate again - should exit early
        cylinder.tryDeactivate(afterLastVoice)

        // Should still be inactive (didn't check buffer)
        cylinder.isActive shouldBe false
    }

    "cylinder reactivates when updateFromVoice is called" {
        val cylinder = createTestOrbit()
        makeOrbitActive(cylinder)

        // Deactivate it
        cylinder.mixBuffer.clear()
        cylinder.tryDeactivate(afterLastVoice)
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

        cylinder.tryDeactivate(afterLastVoice)

        // Should remain active
        cylinder.isActive shouldBe true
    }

    "tryDeactivate() with signal only at end of buffer" {
        val cylinder = createTestOrbit()
        makeOrbitActive(cylinder)

        // Silent except for last sample
        cylinder.mixBuffer.clear()
        cylinder.mixBuffer.right[blockFrames - 1] = 0.001

        cylinder.tryDeactivate(afterLastVoice)

        // Should remain active
        cylinder.isActive shouldBe true
    }

    "an inaudibly-charged delay ring is cleared LITERALLY on deactivation" {
        // hasTail() scans the WHOLE ring, so a charged drain keeps the orbit alive until the
        // countdown\'s own terminal reset (old copies sit in the ring at full amplitude until the
        // head wraps). The reachable case for the resetBusEffects wiring is therefore an ACTIVE
        // delay whose ring only ever held sub-threshold content: the scan frees the orbit while
        // literal nonzeros remain, and only resetBusEffects cleans those. Review round 2 found
        // that wiring line unguarded.
        val cylinder = createTestOrbit()

        cylinder.updateFromVoice(
            VoiceTestHelpers.createSynthVoice(
                katalystParams = mapOf("delay.wet" to 1.0, "delay.time" to 0.02, "delay.feedback" to 0.4),
            ),
            blockStart = 0.0,
        )

        // Charge QUIETLY: everything in the ring stays below the 1e-5 audibility scan, yet > 0.
        repeat(4) {
            cylinder.clear()
            cylinder.mixBuffer.fill(0.000005)
            cylinder.processEffects()
        }

        cylinder.clear()
        cylinder.tryDeactivate(afterLastVoice)

        cylinder.isActive shouldBe false
        // Literally zero: any residue trips the strict > comparison.
        cylinder.delay!!.delayLine!!.hasTail(0.0) shouldBe false
        // Factory params too, not just the ring: a core-only reset would leave the dead owner's
        // time for the next life's first non-finite param to inherit (round-2 retrofit).
        cylinder.delay!!.delayLine!!.time shouldBe 0.0
    }

    "an inaudibly-charged reverb network is cleared LITERALLY on deactivation" {
        // The reverb sibling of the delay row above (reverb drain adoption, review round 1): an
        // ACTIVE reverb whose combs only ever held sub-threshold content — the audibility scan
        // frees the orbit while literal nonzeros remain, and only the resetBusEffects wiring
        // cleans those (the anti-denormal bias guarantees such nonzeros on EVERY reverb orbit,
        // so this is the normal state, not a corner). Also the row where an Active-state
        // hasTail must genuinely answer false: a hard-true mutation makes every orbit that ever
        // had reverb immortal, leaking one PlaybackEngine per stop.
        val cylinder = createTestOrbit()

        cylinder.updateFromVoice(
            VoiceTestHelpers.createSynthVoice(
                // lowpass set so the reset's `lowpass = null` line has something to clear —
                // a fresh-default null would make that assert vacuous (review round 3).
                katalystParams = mapOf(
                    "reverb.wet" to 1.0, "reverb.size" to 5.0, "reverb.lowpass" to 3000.0,
                ),
            ),
            blockStart = 0.0,
        )

        // Charge QUIETLY: 4 blocks never wrap a comb (>= 1116 samples), so every written cell
        // is exactly the feed level, below the 1e-5 audibility scan, yet > 0.
        repeat(4) {
            cylinder.clear()
            cylinder.mixBuffer.fill(0.000005)
            cylinder.processEffects()
        }

        cylinder.clear()
        cylinder.tryDeactivate(afterLastVoice)

        cylinder.isActive shouldBe false
        // Literally zero: any residue trips the strict > comparison.
        cylinder.reverb!!.reverb!!.hasTail(0.0) shouldBe false
        // Factory params too, not just the buffers: a network-only `reverb.reverb!!.reset()`
        // passes the buffer assert while the dead owner's room survives into the next life
        // (review round 2).
        cylinder.reverb!!.reverb!!.size shouldBe 0.0
        cylinder.reverb!!.reverb!!.lowpass shouldBe null
    }

    "a draining self-oscillating delay with an EMPTY ring does not pin the orbit" {
        val cylinder = createTestOrbit()

        // Owner A configures a self-oscillating delay; nothing is ever sent into it, so the ring
        // never holds anything: the orbit mix stays silent, so the line is fed nothing. The
        // `delay.wet` is WRITTEN, so the line runs, and the ring stays empty.)
        cylinder.updateFromVoice(
            VoiceTestHelpers.createSynthVoice(
                katalystParams = mapOf("delay.wet" to 1.0, "delay.time" to 0.5, "delay.feedback" to 1.2),
            ),
            blockStart = 0.0,
        )

        // A lapses; a no-delay owner takes over. The ring never held anything, so the off-config
        // lands in Off directly (the silent-window check outranks the |fb| >= 1 sentinel).
        cylinder.updateFromVoice(
            VoiceTestHelpers.createSynthVoice(),
            blockStart = 2.0 * blockFrames,
        )

        // Ring empty, mix silent: the orbit must free itself. Review round 1 found this shape
        // pinning the cylinder forever (and through anyActive -> hasOwnSound -> isIdle leaking
        // one whole PlaybackEngine per stop); the configure-door peak check is what closes it.
        cylinder.tryDeactivate(afterLastVoice)

        cylinder.isActive shouldBe false
    }

    "the duck is in the lifecycle set: deactivation clears it" {
        val cylinder = createTestOrbit()

        cylinder.updateFromVoice(
            VoiceTestHelpers.createSynthVoice(
                katalystParams = mapOf("duck.orbit" to 3.0, "duck.attack" to 0.02, "duck.depth" to 0.8),
            ),
            blockStart = 0.0,
        )
        cylinder.duck!!.duckCylinderId shouldBe 3
        cylinder.duck!!.ducking shouldNotBe null

        cylinder.mixBuffer.clear()
        cylinder.tryDeactivate(afterLastVoice)

        // The duck runs OUTSIDE the serial pipeline, so a lifecycle that walks only the pipeline
        // would leave this orbit ducking to a dead owner's source for its whole next life.
        cylinder.isActive shouldBe false
        cylinder.duck!!.duckCylinderId shouldBe null
        cylinder.duck!!.ducking shouldBe null
    }

    "the duck is in the lifecycle set: retiring to the shelf clears it" {
        val cylinder = createTestOrbit()

        cylinder.updateFromVoice(
            VoiceTestHelpers.createSynthVoice(
                katalystParams = mapOf("duck.orbit" to 4.0, "duck.attack" to 0.02, "duck.depth" to 0.8),
            ),
            blockStart = 0.0,
        )
        cylinder.duck!!.duckCylinderId shouldBe 4

        cylinder.retire()

        // A shelved cylinder holds nothing: the next engine to rent it must not inherit a duck.
        cylinder.duck!!.duckCylinderId shouldBe null
        cylinder.duck!!.ducking shouldBe null
    }

    "deactivation clears the mix, so a reactivating voice is not summed onto the last block's residue" {
        // Found in the step 5b-2 review (Synthris Echo): `clear()` skips an inactive orbit, so the
        // sub-floor output of the orbit's last block stayed in its mix buffer until a voice woke the
        // orbit, was summed under that voice and, since the delay and the reverb are fed from the
        // mix, fed into the room.
        val cylinder = createTestOrbit()
        makeOrbitActive(cylinder)

        // Below the silence floor, so the orbit deactivates with it in the buffer.
        cylinder.mixBuffer.fill(0.000001)
        cylinder.tryDeactivate(afterLastVoice)

        cylinder.isActive shouldBe false
        cylinder.mixBuffer.left.all { it == 0.0 } shouldBe true
        cylinder.mixBuffer.right.all { it == 0.0 } shouldBe true
    }
})
