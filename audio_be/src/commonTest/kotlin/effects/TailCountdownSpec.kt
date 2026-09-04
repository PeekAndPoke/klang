/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.effects

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.StereoBuffer

/**
 * The tail question in closed form — the state machine on its own. The effect-level specs
 * (`ClosedFormTailSpec`) prove it against the DSP; this one pins the rules that make it cheap:
 * the peak read happens once per silence onset and never for an idle unit, an audible block
 * resets the proof, parameters changing mid-proof re-measure, self-oscillation never drains.
 */
class TailCountdownSpec : StringSpec({

    "idle until fed: a unit that never saw input has no tail, and no peak is read for it" {
        val tail = TailCountdown()
        var reads = 0
        tail.hasTail shouldBe false

        tail.observe(silent = true, frames = 128) { reads++; 1000.0 }

        tail.hasTail shouldBe false
        reads shouldBe 0
    }

    "audible input: a tail exists and nothing is measured" {
        val tail = TailCountdown()
        var reads = 0
        tail.observe(silent = false, frames = 128) { reads++; 1000.0 }
        tail.hasTail shouldBe true
        reads shouldBe 0
    }

    "silence onset reads the drain ONCE; the countdown then runs to exactly the proof's length" {
        val tail = TailCountdown()
        var reads = 0
        tail.observe(silent = false, frames = 128) { reads++; 0.0 }

        // 300 samples of proof: the onset block counts 128, the next 128, the third ends it.
        tail.observe(silent = true, frames = 128) { reads++; 300.0 }
        reads shouldBe 1
        tail.hasTail shouldBe true
        tail.observe(silent = true, frames = 128) { reads++; 999.0 }
        tail.hasTail shouldBe true
        tail.observe(silent = true, frames = 128) { reads++; 999.0 }
        tail.hasTail shouldBe false
        reads shouldBe 1 // never re-read while counting
    }

    "audible input mid-proof restarts it: the next silence onset reads again" {
        val tail = TailCountdown()
        var reads = 0
        tail.observe(silent = false, frames = 128) { 0.0 }
        tail.observe(silent = true, frames = 128) { reads++; 200.0 }
        tail.observe(silent = false, frames = 128) { reads++; 0.0 } // a note
        tail.hasTail shouldBe true
        tail.observe(silent = true, frames = 128) { reads++; 200.0 }
        reads shouldBe 2
        tail.hasTail shouldBe true
    }

    "invalidate() mid-proof re-measures on the next silent block; on an idle unit it is a no-op" {
        val tail = TailCountdown()
        var reads = 0
        tail.observe(silent = false, frames = 128) { 0.0 }
        tail.observe(silent = true, frames = 128) { reads++; 200.0 }

        tail.invalidate() // e.g. the owner raised the feedback
        tail.hasTail shouldBe true // audible until re-measured
        tail.observe(silent = true, frames = 128) { reads++; 5000.0 }
        reads shouldBe 2
        tail.hasTail shouldBe true

        val idle = TailCountdown()
        idle.invalidate()
        idle.hasTail shouldBe false
        idle.observe(silent = true, frames = 128) { reads++; 1.0 }
        reads shouldBe 2
    }

    "an infinite proof (self-oscillation) never drains" {
        val tail = TailCountdown()
        tail.observe(silent = false, frames = 128) { 0.0 }
        tail.observe(silent = true, frames = 128) { Double.POSITIVE_INFINITY }
        repeat(100_000) { tail.observe(silent = true, frames = 128) { 0.0 } }
        tail.hasTail shouldBe true
    }

    "reset() forgets everything: idle, no tail" {
        val tail = TailCountdown()
        tail.observe(silent = false, frames = 128) { 0.0 }
        tail.reset()
        tail.hasTail shouldBe false
    }

    "isSilent sees both channels and the threshold, and only the first `frames`" {
        val b = StereoBuffer(8)
        TailCountdown.isSilent(b, 8) shouldBe true
        b.right[5] = -TailCountdown.SILENCE * 2
        TailCountdown.isSilent(b, 8) shouldBe false
        TailCountdown.isSilent(b, 5) shouldBe true // index 5 is past the first five
        b.right[5] = 0.0
        b.left[0] = TailCountdown.SILENCE // AT the threshold is silent
        TailCountdown.isSilent(b, 8) shouldBe true
    }
})
