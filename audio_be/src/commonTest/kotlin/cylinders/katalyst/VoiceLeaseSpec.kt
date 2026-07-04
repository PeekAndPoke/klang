/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * First-writer-wins lease: the owner holds it while it checks in every block; on a missed block (any death
 * mode) it lapses after a one-block grace and the next voice takes over.
 */
class VoiceLeaseSpec : StringSpec({

    val bf = 128

    "the first voice to claim becomes the owner" {
        val lease = VoiceLease()
        lease.claim(voiceId = 1, blockStart = 0, blockFrames = bf) shouldBe true
        lease.hasOwner shouldBe true
    }

    "while the owner renews each block, other voices are denied" {
        val lease = VoiceLease()
        lease.claim(1, 0, bf) shouldBe true        // voice 1 claims block 0
        lease.claim(2, 0, bf) shouldBe false       // voice 2 same block → denied
        lease.claim(1, bf, bf) shouldBe true       // owner renews at block 1
        lease.claim(2, bf, bf) shouldBe false      // still denied
        lease.claim(1, 2 * bf, bf) shouldBe true   // owner renews at block 2
    }

    "when the owner misses a block, the lease lapses after one grace block and the next voice takes over" {
        val lease = VoiceLease()
        lease.claim(1, 0, bf) shouldBe true        // voice 1 owns at block 0, then dies (stops checking in)
        lease.claim(2, bf, bf) shouldBe false      // block 1 = grace: owner still counted alive
        lease.claim(2, 2 * bf, bf) shouldBe true   // block 2: owner lapsed → voice 2 takes over
        lease.claim(2, 3 * bf, bf) shouldBe true   // voice 2 now owns and renews
    }

    "an owner that keeps checking in is never displaced by a later voice" {
        val lease = VoiceLease()
        // interleave both voices every block; voice 1 claimed first and keeps checking in
        for (b in 0..5) {
            lease.claim(1, b * bf, bf) shouldBe true
            lease.claim(2, b * bf, bf) shouldBe false
        }
    }

    "reset releases the lease so a fresh voice can own it" {
        val lease = VoiceLease()
        lease.claim(1, 0, bf) shouldBe true
        lease.reset()
        lease.hasOwner shouldBe false
        lease.claim(2, bf, bf) shouldBe true       // brand-new owner right after reset
    }

    "a voice with id -1 (a wrapped counter) can still own the lease" {
        // ownership is tracked by a flag, not a sentinel id, so any Int value works as an owner.
        val lease = VoiceLease()
        lease.claim(voiceId = -1, blockStart = 0, blockFrames = bf) shouldBe true
        lease.hasOwner shouldBe true
        lease.claim(voiceId = 2, blockStart = 0, blockFrames = bf) shouldBe false  // -1 owns; others denied
        lease.claim(voiceId = -1, blockStart = bf, blockFrames = bf) shouldBe true // -1 renews
    }
})
