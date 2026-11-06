/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.Ign
import io.peekandpoke.klang.audio_bridge.IgnitorDsl

/**
 * Guard (single sources): the open knobs of the single oscillators and the pluck have ONE home, their
 * `IgnitorDsl.Slots` param (KLANG-00417 A2). Checked against it: the node a door builds (`Ign.square()`,
 * `Ign.pluck()`, which the script doors build on) and the engine's own fallbacks in [Ignitors]. The shape constants
 * (`SAW_*`, `RAMP_*`, `PULSE_*`) are read directly by both the node and the engine and need no row. Sibling of
 * [SuperOscDefaultsSyncSpec] (the unison sources) and [NoiseDefaultsSyncSpec].
 */
class WaveOscDefaultsSyncSpec : StringSpec({

    val slots = IgnitorDsl.Slots

    fun defaultOf(slot: IgnitorDsl): Double = (slot as IgnitorDsl.Param).default

    "both pulses a door builds answer the duty and analog slots" {
        Ign.square().duty shouldBe slots.duty
        Ign.square().analog shouldBe slots.analog
        Ign.pulze().duty shouldBe slots.duty
        Ign.pulze().analog shouldBe slots.analog
    }

    "the other single oscillators a door builds answer the analog slot" {
        listOf(
            Ign.sine().analog,
            Ign.saw().analog,
            Ign.tri().analog,
            Ign.ramp().analog,
            Ign.zawtooth().analog,
            Ign.zamp().analog,
            Ign.impulse().analog,
        ).forEach { it shouldBe slots.analog }
    }

    "the pluck a door builds answers every string slot" {
        val node = Ign.pluck()

        node.feedback shouldBe slots.feedback
        node.brightness shouldBe slots.brightness
        node.pickPosition shouldBe slots.pickPosition
        node.stiffness shouldBe slots.stiffness
        node.analog shouldBe slots.analog
    }

    "the engine's single-source fallbacks are the slot defaults" {
        Ignitors.dutyDefault.value shouldBe defaultOf(slots.duty)
        Ignitors.analogDefault.value shouldBe defaultOf(slots.analog)
        Ignitors.feedbackDefault.value shouldBe defaultOf(slots.feedback)
        Ignitors.brightnessDefault.value shouldBe defaultOf(slots.brightness)
        Ignitors.pickPositionDefault.value shouldBe defaultOf(slots.pickPosition)
        Ignitors.stiffnessDefault.value shouldBe defaultOf(slots.stiffness)
    }
})
