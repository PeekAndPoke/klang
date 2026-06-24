/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.voices.strip.calculateControlRateEnvelope
import io.peekandpoke.klang.audio_be.voices.strip.envelopeLevelAtPosition
import io.peekandpoke.klang.audio_bridge.AdsrCurve

/**
 * Golden-value tests for the per-stage envelope shape curves
 * (Linear / Square / Cube) at the midpoint of each stage.
 *
 * Math:
 * - Attack:  level = shape(p)
 * - Decay:   level = sustain + (1 - sustain) * shape(1 - p)
 * - Release: level = startLevel * shape(1 - p)
 *
 * shape(p) per curve:
 * - Linear: p
 * - Square: p * p
 * - Cube:   p * p * p
 */
class EnvelopeShapeTest : StringSpec({

    fun env(
        attackFrames: Double = 100.0,
        decayFrames: Double = 100.0,
        sustainLevel: Double = 0.0,
        releaseFrames: Double = 100.0,
        attackCurve: AdsrCurve = AdsrCurve.Square,
        decayCurve: AdsrCurve = AdsrCurve.Square,
        releaseCurve: AdsrCurve = AdsrCurve.Square,
    ) = Voice.Envelope(
        attackFrames, decayFrames, sustainLevel, releaseFrames,
        attackCurve, decayCurve, releaseCurve,
    )

    // ── Attack midpoint ────────────────────────────────────────────────────────

    "attack midpoint — Linear = 0.5" {
        envelopeLevelAtPosition(env(attackCurve = AdsrCurve.Linear), 50) shouldBe (0.5 plusOrMinus 0.001)
    }
    "attack midpoint — Square = 0.25" {
        envelopeLevelAtPosition(env(attackCurve = AdsrCurve.Square), 50) shouldBe (0.25 plusOrMinus 0.001)
    }
    "attack midpoint — Cube = 0.125" {
        envelopeLevelAtPosition(env(attackCurve = AdsrCurve.Cube), 50) shouldBe (0.125 plusOrMinus 0.001)
    }

    // ── Decay midpoint with sustain = 0 isolates the curve shape ──────────────

    "decay midpoint, sustain=0 — Linear = 0.5" {
        // absPos = 150 = attackFrames(100) + decayFrames/2(50)
        envelopeLevelAtPosition(env(decayCurve = AdsrCurve.Linear), 150) shouldBe (0.5 plusOrMinus 0.001)
    }
    "decay midpoint, sustain=0 — Square = 0.25" {
        envelopeLevelAtPosition(env(decayCurve = AdsrCurve.Square), 150) shouldBe (0.25 plusOrMinus 0.001)
    }
    "decay midpoint, sustain=0 — Cube = 0.125" {
        envelopeLevelAtPosition(env(decayCurve = AdsrCurve.Cube), 150) shouldBe (0.125 plusOrMinus 0.001)
    }

    // ── Decay midpoint with non-zero sustain ──────────────────────────────────

    "decay midpoint with sustain=0.5 — Square gives sustain + (1-sustain)*0.25 = 0.625" {
        envelopeLevelAtPosition(env(sustainLevel = 0.5, decayCurve = AdsrCurve.Square), 150) shouldBe (0.625 plusOrMinus 0.001)
    }

    // ── Stage endpoints ────────────────────────────────────────────────────────

    "decay/sustain boundary — all curves arrive at sustain" {
        // absPos = 200 = attackFrames + decayFrames → start of sustain
        for (curve in AdsrCurve.entries) {
            envelopeLevelAtPosition(env(sustainLevel = 0.3, decayCurve = curve), 200) shouldBe (0.3 plusOrMinus 0.001)
        }
    }

    // ── Release via calculateControlRateEnvelope ──────────────────────────────

    "release midpoint via calculateControlRateEnvelope — Square = 0.25 of startLevel" {
        // gateEnd = 0; at blockStart = 50, p = 50/100 = 0.5; Square gives 0.25.
        val e = env(
            attackFrames = 0.0,
            decayFrames = 0.0,
            sustainLevel = 1.0,
            releaseFrames = 100.0,
            releaseCurve = AdsrCurve.Square,
        )
        calculateControlRateEnvelope(e, blockStart = 50, startFrame = 0, gateEndFrame = 0) shouldBe
                (0.25 plusOrMinus 0.001)
    }

    "release endpoint reaches 0 for all curves" {
        for (curve in AdsrCurve.entries) {
            val e = env(
                attackFrames = 0.0,
                decayFrames = 0.0,
                sustainLevel = 1.0,
                releaseFrames = 100.0,
                releaseCurve = curve,
            )
            calculateControlRateEnvelope(e, blockStart = 100, startFrame = 0, gateEndFrame = 0) shouldBe
                    (0.0 plusOrMinus 0.001)
        }
    }
})
