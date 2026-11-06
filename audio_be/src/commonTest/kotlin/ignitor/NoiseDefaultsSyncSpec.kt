/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.Ign
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.constants.BROWN_LEAK_DEFAULT
import io.peekandpoke.klang.audio_bridge.constants.CRACKLE_CHAOS_DEFAULT
import io.peekandpoke.klang.audio_bridge.constants.DUST_BIPOLAR_DEFAULT
import io.peekandpoke.klang.audio_bridge.constants.DUST_TAIL_DEFAULT
import io.peekandpoke.klang.audio_bridge.constants.NOISE_TILT_DEFAULT

/**
 * Guard (noise generators): every noise knob has ONE home (KLANG-00417 A2), its `audio_bridge/constants/` value
 * where one exists (`CRACKLE_CHAOS_DEFAULT`, ...), else its `IgnitorDsl.Slots` param. Checked against it: the slot,
 * the node the Kotlin door builds (`Ign.dust()`; the script door builds its node itself, with the same slot fallbacks,
 * pinned by `KotlinIgnObjectParitySpec`) and the engine's own
 * fallbacks in [Ignitors]. Sibling of [WaveOscDefaultsSyncSpec] and [SuperOscDefaultsSyncSpec].
 */
class NoiseDefaultsSyncSpec : StringSpec({

    val slots = IgnitorDsl.Slots

    fun defaultOf(slot: IgnitorDsl): Double = (slot as IgnitorDsl.Param).default

    "the noise slots carry the noise constants" {
        defaultOf(slots.chaos) shouldBe CRACKLE_CHAOS_DEFAULT
        defaultOf(slots.color) shouldBe NOISE_TILT_DEFAULT
        defaultOf(slots.leak) shouldBe BROWN_LEAK_DEFAULT
        defaultOf(slots.tail) shouldBe DUST_TAIL_DEFAULT
        defaultOf(slots.bipolar) shouldBe DUST_BIPOLAR_DEFAULT
    }

    "the noise sources a door builds answer their slots" {
        Ign.crackle().chaos shouldBe slots.chaos
        Ign.whitenoise().color shouldBe slots.color
        Ign.brownnoise().leak shouldBe slots.leak

        Ign.dust().density shouldBe slots.density
        Ign.dust().tail shouldBe slots.tail
        Ign.dust().bipolar shouldBe slots.bipolar

        listOf(
            Triple(Ign.perlin().rate, Ign.perlin().octaves, Ign.perlin().persistence),
            Triple(Ign.berlin().rate, Ign.berlin().octaves, Ign.berlin().persistence),
        ).forEach { (rate, octaves, persistence) ->
            rate shouldBe slots.rate
            octaves shouldBe slots.octaves
            persistence shouldBe slots.persistence
        }
    }

    "the engine's noise fallbacks are the slot defaults" {
        Ignitors.chaosDefault.value shouldBe defaultOf(slots.chaos)
        Ignitors.colorDefault.value shouldBe defaultOf(slots.color)
        Ignitors.brownLeakDefault.value shouldBe defaultOf(slots.leak)
        Ignitors.densityDefault.value shouldBe defaultOf(slots.density)
        Ignitors.tailDefault.value shouldBe defaultOf(slots.tail)
        Ignitors.bipolarDefault.value shouldBe defaultOf(slots.bipolar)
        Ignitors.rateDefault.value shouldBe defaultOf(slots.rate)
        Ignitors.octavesDefault.value shouldBe defaultOf(slots.octaves)
        Ignitors.persistenceDefault.value shouldBe defaultOf(slots.persistence)
    }
})
