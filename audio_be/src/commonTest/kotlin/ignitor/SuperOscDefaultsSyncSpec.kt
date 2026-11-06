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
 * Guard (unison sources): every unison source's open knobs have ONE home, their `IgnitorDsl.Slots` param
 * (KLANG-00417 A2). The places a literal could creep back in are each checked against it: the node a door builds
 * (`Ign.supersaw()`, which the script door `Ignitor.supersaw()` builds on) and the engine's own fallbacks in
 * [Ignitors], which a Kotlin caller of a factory meets. The engine's unset voice count was 7 against 8 everywhere
 * else until this guard; the character constants (`SUPERSAW_*`, ...) are read directly by both the node and the
 * engine and need no row. Sibling of [WaveOscDefaultsSyncSpec] (the single sources) and [NoiseDefaultsSyncSpec].
 */
class SuperOscDefaultsSyncSpec : StringSpec({

    val slots = IgnitorDsl.Slots

    fun defaultOf(slot: IgnitorDsl): Double = (slot as IgnitorDsl.Param).default

    "the super oscillators a door builds answer the voices, spread and analog slots" {
        listOf(
            Triple(Ign.supersaw().voices, Ign.supersaw().spread, Ign.supersaw().analog),
            Triple(Ign.supersine().voices, Ign.supersine().spread, Ign.supersine().analog),
            Triple(Ign.supersquare().voices, Ign.supersquare().spread, Ign.supersquare().analog),
            Triple(Ign.supertri().voices, Ign.supertri().spread, Ign.supertri().analog),
            Triple(Ign.superramp().voices, Ign.superramp().spread, Ign.superramp().analog),
        ).forEach { (voices, spread, analog) ->
            voices shouldBe slots.voices
            spread shouldBe slots.spread
            analog shouldBe slots.analog
        }
    }

    "the superpluck a door builds answers every string and unison slot" {
        val node = Ign.superpluck()

        node.voices shouldBe slots.voices
        node.spread shouldBe slots.spread
        node.feedback shouldBe slots.feedback
        node.brightness shouldBe slots.brightness
        node.pickPosition shouldBe slots.pickPosition
        node.stiffness shouldBe slots.stiffness
        node.analog shouldBe slots.analog
    }

    "the engine's unison fallbacks are the slot defaults" {
        Ignitors.voicesDefault.value shouldBe defaultOf(slots.voices)
        Ignitors.detuneDefault.value shouldBe defaultOf(slots.spread)
        Ignitors.analogDefault.value shouldBe defaultOf(slots.analog)
    }
})
