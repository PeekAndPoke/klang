/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * `analogSpread` defaults to 1 on every node that carries it: a lane per voice, per string, per
 * partial, which is what all of them did before the knob existed.
 *
 * This is a value pin, and it earns its place the way few value pins do: the default IS the
 * "nothing changes for an existing patch" promise, and a node whose default slipped to 0 would keep
 * rendering perfectly plausible audio, just with its unison detune frozen and its stack wobbling as
 * one oscillator. Only the supersaw's render is pinned sample for sample
 * (`SuperStackDriftSpreadSpec`), so the other five nodes and the sine bank have nothing else
 * standing between a slipped default and a silently different sound.
 */
class IgnitorDslAnalogSpreadDefaultsSpec : StringSpec({
    val independent = IgnitorDsl.Constant(1.0)

    "Sine (the partial bank)" {
        IgnitorDsl.Sine().analogSpread shouldBe independent
    }

    "SuperSaw" {
        IgnitorDsl.SuperSaw().analogSpread shouldBe independent
    }

    "SuperSine" {
        IgnitorDsl.SuperSine().analogSpread shouldBe independent
    }

    "SuperSquare" {
        IgnitorDsl.SuperSquare().analogSpread shouldBe independent
    }

    "SuperTri" {
        IgnitorDsl.SuperTri().analogSpread shouldBe independent
    }

    "SuperRamp" {
        IgnitorDsl.SuperRamp().analogSpread shouldBe independent
    }

    "SuperPluck" {
        IgnitorDsl.SuperPluck().analogSpread shouldBe independent
    }
})
