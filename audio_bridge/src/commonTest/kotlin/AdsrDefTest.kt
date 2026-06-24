/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class AdsrDefTest : StringSpec({

    "AdsrDef.empty is an AdsrDef.Std with all fields null" {
        val empty = AdsrDef.empty
        (empty is AdsrDef.Std) shouldBe true
        empty as AdsrDef.Std
        empty.attack shouldBe null
        empty.decay shouldBe null
        empty.sustain shouldBe null
        empty.release shouldBe null
        empty.attackCurve shouldBe null
        empty.decayCurve shouldBe null
        empty.releaseCurve shouldBe null
    }

    "AdsrDef.defaultSynth uses Exponential curves for all three phases" {
        val def = AdsrDef.defaultSynth as AdsrDef.Std
        def.attack shouldBe 0.01
        def.decay shouldBe 0.1
        def.sustain shouldBe 1.0
        def.release shouldBe 0.05
        def.attackCurve shouldBe AdsrCurve.Exponential
        def.decayCurve shouldBe AdsrCurve.Exponential
        def.releaseCurve shouldBe AdsrCurve.Exponential
    }

    "Std.resolve() fills in defaults when fields are null" {
        val resolved = AdsrDef.Std().resolve()
        resolved.attack shouldBe 0.01
        resolved.decay shouldBe 0.1
        resolved.sustain shouldBe 1.0
        resolved.release shouldBe 0.05
        resolved.attackCurve shouldBe AdsrCurve.Exponential
        resolved.decayCurve shouldBe AdsrCurve.Exponential
        resolved.releaseCurve shouldBe AdsrCurve.Exponential
    }

    "Std.resolve() preserves explicit field values over defaults" {
        val resolved = AdsrDef.Std(
            attack = 0.5,
            decay = 1.0,
            sustain = 0.3,
            release = 2.0,
            attackCurve = AdsrCurve.Linear,
            decayCurve = AdsrCurve.Cube,
            releaseCurve = AdsrCurve.Linear,
        ).resolve()
        resolved.attack shouldBe 0.5
        resolved.decay shouldBe 1.0
        resolved.sustain shouldBe 0.3
        resolved.release shouldBe 2.0
        resolved.attackCurve shouldBe AdsrCurve.Linear
        resolved.decayCurve shouldBe AdsrCurve.Cube
        resolved.releaseCurve shouldBe AdsrCurve.Linear
    }

    "mergeWith — values in this take precedence over other" {
        val a = AdsrDef.Std(attack = 0.1, attackCurve = AdsrCurve.Cube)
        val b = AdsrDef.Std(attack = 0.5, decay = 0.2, attackCurve = AdsrCurve.Linear, decayCurve = AdsrCurve.Linear)
        val merged = a.mergeWith(b) as AdsrDef.Std
        merged.attack shouldBe 0.1                 // from a
        merged.decay shouldBe 0.2                  // from b
        merged.attackCurve shouldBe AdsrCurve.Cube // from a
        merged.decayCurve shouldBe AdsrCurve.Linear // from b
    }

    "mergeWith null returns this" {
        val a = AdsrDef.Std(attack = 0.1)
        val merged = a.mergeWith(null) as AdsrDef.Std
        merged shouldBe a
    }

    "AdsrCurve enum has Linear, Square, Cube, SCurve, InvSquare, Exponential" {
        AdsrCurve.entries.size shouldBe 6
        AdsrCurve.entries shouldBe listOf(
            AdsrCurve.Linear, AdsrCurve.Square, AdsrCurve.Cube,
            AdsrCurve.SCurve, AdsrCurve.InvSquare, AdsrCurve.Exponential,
        )
    }
})
