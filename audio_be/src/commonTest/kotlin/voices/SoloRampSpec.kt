/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.ultra.maths.Ease

/**
 * [SoloRamp]: the background level of a playback while a source is soloed, moving over a fixed duration on the
 * in-out cubic. The oracle replays, step by step, the law of the `ValueRamp` it replaced, with the library curve
 * that ramp called (audit item B4.17): start plus span times the eased progress, ending exactly on the target.
 */
class SoloRampSpec : StringSpec({

    val dt = 128.0 / 48_000.0

    "a ramp walks the cubic from the start to the target, bit for bit, and ends exactly on it" {
        val ramp = SoloRamp(initialValue = 1.0, durationSec = 1.5)
        var progress = 0.0
        var steps = 0

        while (progress < 1.0) {
            progress += dt / 1.5
            steps++

            val want = if (progress >= 1.0) 0.0 else 1.0 + (0.0 - 1.0) * Ease.InOut.cubic(progress)

            ramp.step(target = 0.0, dt = dt).toRawBits() shouldBe want.toRawBits()
        }

        withClue("1.5 s of 128-frame blocks") { steps shouldBe 563 }
        ramp.current shouldBe 0.0
        ramp.step(target = 0.0, dt = dt) shouldBe 0.0
    }

    "a new target starts a new transition from where the value stands" {
        val ramp = SoloRamp(initialValue = 1.0, durationSec = 1.0)

        repeat(100) { ramp.step(target = 0.0, dt = dt) }

        val from = ramp.current
        val next = ramp.step(target = 0.5, dt = dt)

        next.toRawBits() shouldBe (from + (0.5 - from) * Ease.InOut.cubic(dt / 1.0)).toRawBits()
    }

    "a target within 1e-6 of the last one is no new transition" {
        val ramp = SoloRamp(initialValue = 1.0, durationSec = 1.0)

        repeat(10) { ramp.step(target = 0.0, dt = dt) }

        val held = SoloRamp(initialValue = 1.0, durationSec = 1.0)

        repeat(10) { held.step(target = 0.0, dt = dt) }

        ramp.step(target = 0.0, dt = dt) shouldBe held.step(target = 9e-7, dt = dt)

        withClue("positive control: 2e-6 is one") {
            val moved = SoloRamp(initialValue = 1.0, durationSec = 1.0)

            repeat(10) { moved.step(target = 0.0, dt = dt) }

            val before = moved.current

            moved.step(target = 2e-6, dt = dt).toRawBits() shouldBe
                (before + (2e-6 - before) * Ease.InOut.cubic(dt)).toRawBits()
        }
    }

    "a duration of 0 or less jumps to the target" {
        SoloRamp(initialValue = 1.0, durationSec = 0.0).step(target = 0.25, dt = dt) shouldBe 0.25
        SoloRamp(initialValue = 1.0, durationSec = -1.0).step(target = 0.25, dt = dt) shouldBe 0.25
    }

    "it starts at its initial value and holds it while the target is that value" {
        val ramp = SoloRamp(initialValue = 0.75, durationSec = 1.0)

        ramp.current shouldBe 0.75
        ramp.step(target = 0.75, dt = dt) shouldBe 0.75
    }
})
